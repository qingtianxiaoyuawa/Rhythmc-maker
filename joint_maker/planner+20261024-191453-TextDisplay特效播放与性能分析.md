# Reborn 与附属源码：TextDisplay 特效播放及性能优化

日期：2026-10-24

## 结论速览

本轮源码中，真正负责 TextDisplay 实体创建、播放、轨道同步、对象复用和清理的是 RhythMC-Reborn。Chart Converter 只负责事件类型转换/映射，Charter V2 是编辑草稿与编译器，Backend/Frontend 负责内容与资源服务；没有在这些附属工程源码中发现另一套 TextDisplay 实体运行时。

Reborn 的性能设计重点是：按谱面预估同时存活的显示数量，提前分批生成实体；以 ID 管理和复用池减少播放期反复生成/销毁；按 Beat 队列触发一次性事件；对仍需跟随轨道的显示每次游戏更新重算变换；清理和池缩减均限批处理。它降低的是实体生命周期尖峰和矩阵对象分配，不会消除“每个仍在同步的显示每游戏更新都要更新位置”的成本。

## 1. 从谱面事件到实体显示的播放链

1. Reborn `EffectFactory` 将谱面中的 `TEXT_DISPLAY`、`TEXT_DISPLAY_EFFECT`、`TEXT_DISPLAY_SYNC_TRACK`、`TEXT_DISPLAY_DESYNC_TRACK`、`TEXT_DISPLAY_REMOVE` 分派到对应 Effect 实现类。
2. `EffectManager` 将效果按规范化 Beat 放入 `TreeMap<Double, List<AbstractEffect>>`。每次 `update(beat, elapsedMillis)`：更新文本对象池、更新活跃的持久效果，再取出所有 `beat <= 当前 Beat + 精度容差` 的队列事件并执行一次。
3. `TEXT_DISPLAY` 的 `TextDisplayEffect.applyEffect()` 按文本 ID 从 `TextDisplayManager.acquire(id)` 取得对象，写入位置/旋转/缩放/文本，执行 `render()` 并通过 `player.showEntity(...)` 对该玩家显示。
4. `TEXT_DISPLAY_EFFECT` 用同一 ID 取得已有对象，按参数修改文本、阴影、透明度、背景、线性变换等属性；变换参数改完后调用 `render()`。实际实现是离散事件触发，不是每帧重复解析效果。
5. `TEXT_DISPLAY_SYNC_TRACK` 是持久效果：取得 TextDisplay 和轨道对象；在 `update(curBeat)` 中读取轨道当前平移、旋转、缩放，写入 TextDisplay 并 render。它让显示随轨道持续运动。
6. `TEXT_DISPLAY_DESYNC_TRACK` 停止该 ID 对应的轨道同步效果；`TEXT_DISPLAY_REMOVE` 将显示从活动映射移除、reset 后归还对象池。对局结束时 EffectManager 清理活跃效果，TextDisplayManager 批量销毁池内和使用中的实体。

## 2. 实体表示与变换实现

- 每个 `GpTextDisplay` 持有一个 Bukkit `TextDisplay`、所属玩家、文本状态和位置/旋转/缩放状态。
- 实体设置 `setPersistent(false)`，避免作为持久实体写入世界存档；设置 `setVisibleByDefault(false)`，随后由 `showEntity`/`hideEntity` 控制该玩家可见性；Billboard 固定为 `FIXED`，因此显示朝向由变换矩阵管理。
- 位置、旋转、缩放合成为 JOML `Matrix4f`：identity → translate → rotateXYZ → scale，再传给 `setTransformationMatrix`。
- 通过 Display 插值参数 `interpolationDelay=0` 与短 `interpolationDuration` 平滑客户端看到的变换。默认 duration 为 1 tick；reset 时将 duration 置零。
- reset 清除文本、透明度、背景色、阴影和变换，并隐藏实体；destroy 才真正调用 Bukkit `remove()`。

## 3. Reborn 的性能优化逐项

### A. 预估并预热对象池

`TextDisplayManager.buildDemandProfile(effects)` 按 Beat 排序事件，模拟 ID 的创建/移除，建立需求曲线。`getInitialPreloadTarget()` 根据开局前瞻窗口内的峰值，加上 `POOL_MARGIN`，确定初始预热数量；EffectManager 构造阶段就启动预加载，避免文本事件刚触发时集中 spawn。

### B. 活跃对象按 ID 复用

- `inUseTextDisplays: Map<ID, GpTextDisplay>` 保证同 ID 再次 acquire 时返回现有对象。
- 移除效果只 reset 并放回 `ArrayDeque`，不会每次都 remove 后重新 spawn。
- 池不足时才新增 TextDisplay 实体；池过大时再回收多余空闲对象。

### C. 按 Beat 自适应池容量

运行时用当前 Beat 前后窗口查需求曲线，目标池容量为窗口预计峰值加 margin。扩容每 tick 最多 `BATCH_SIZE` 个；缩容仅在空闲数量超过目标加阈值后发生，每 tick 最多 `SHRINK_BATCH` 个。常量为 `BATCH_SIZE=50`、`SHRINK_BATCH=50`、`SHRINK_THRESHOLD=64`、`LOOK_AHEAD_BEATS=4.0`、`POOL_MARGIN=10`。

### D. 延迟/分批清理

对局结束时把所有实体集中交给渐进销毁任务，每 tick 最多销毁 `BATCH_SIZE` 个，避免同一 tick 大量 remove 带来的尖峰。平时缩池也限量执行。

### E. 矩阵复用与 dirty 标记

- 每个 wrapper 复用一个成员 `Matrix4f`，不在每次 render 新建矩阵对象，减少短命对象/GC。
- `render()` 只有在 dirty 时才重组矩阵、写回实体变换；glowing 只在状态不同才调用 setter。
- 静态文本或只改文本/颜色属性时不会无条件重算矩阵。

### F. 只对目标玩家可见

默认不向所有玩家广播显示，使用 per-player `showEntity` / `hideEntity` 控制可见范围，降低非目标玩家看到实体的范围与无关客户端渲染负担。服务端实体仍是在世界中创建的，不应误解为客户端本地实体。

## 4. 性能边界与值得关注的代价

- **轨道同步仍是持续工作**：`TextDisplaySyncTrackEffect.update()` 每次 EffectManager 更新都读取轨道状态并调用 position/rotation/scale setter 与 render。当前 setter 会直接标记 dirty；因此同步中的显示通常每个游戏更新都会生成并下发变换状态。对象池只能避免实体反复创建，不能免去同步显示数 × 更新频率的持续成本。
- **池对象是实体，不是轻量纯数据对象**：预热会提前 spawn Bukkit 实体，虽分批降低瞬时负载，但高需求谱面会增加世界实体总量和内存占用。预热只是移峰，不是减少总实体数。
- **批量值是固定上限**：每 tick 50 个创建/销毁在高负载服务器上仍可能形成尖峰；源码内未见基于 MSPT/TPS 的动态预算或客户端视距动态裁剪机制。
- **插值是视觉平滑，不等于减少服务端更新**：设置 Display 插值参数不能证明服务端少算或少更新。应把“客户端平滑”和“服务端更新频率”分开评估。
- **静态效果与同步效果成本不同**：TEXT_DISPLAY/TEXT_DISPLAY_EFFECT 是触发时处理；长期运行的 SYNC_TRACK 才持续按更新周期处理，性能剖析应按活跃同步实体数量分组。
- **预估曲线需关注事件语义**：profile 通过 ID 集合模拟 active 数；若谱面 ID 重复复用、事件顺序异常或不成对，峰值估算可能偏离真实活动实体数。源码没有证明所有不规范输入均已由编译器 lint 拦截。
- **源码注释不完全同步**：TextDisplayEffect 类注释仍写有“Implementation pending”，但 `applyEffect()` 有完整实体获取、设置、render/show 行为；判断实现以代码为准，不能以旧注释断言占位。

## 5. 附属仓库各自提供什么

- **RhythMC-Reborn**：唯一确认含真实 TextDisplay 播放运行时的仓库，核心文件为 `TextDisplayManager`、`GpTextDisplay`、`EffectManager` 和各 `TextDisplay*Effect`。
- **RhythMC-Docs**：记录事件 JSON 格式、TEXT_DISPLAY 生命周期和同步语义，是数据契约说明；不执行实体播放。
- **RhythMC-Chart-Converter**：`EffectTypeMapper` 将旧谱面效果类型映射为新格式类型，转换器本身不创建 Minecraft TextDisplay。
- **RhythMC-Charter-V2**：提供草稿模型、编辑器与 `.rmcc` 编译/导入链；其源码搜索未发现基于 Bukkit TextDisplay 的运行时播放实现。编辑预览不能据此等价为 Reborn 服务器的实体生命周期。
- **RhyMC-Backend / RhyMC-Frontend**：处理歌曲/谱面目录、资源发布与社区展示 API；未发现 TextDisplay 生成/播放代码。

## 6. 对 Maker 预览/播放的可借鉴方案

1. 先实现按 ID 管理的实体/显示对象池及严格的 `acquire → update → release → destroy` 生命周期，确保播放停止、切歌、关闭编辑器时全部清理。
2. 事件触发采用按统一 ChartTiming/Beat 的有序队列；TEXT_DISPLAY 修改属性仅在事件点执行，避免每帧重复处理静态文本。
3. 将静态显示、有限时长变换、轨道同步三类对象分开调度；只有同步对象进入每帧/每 tick 更新集合。
4. 对同步对象做状态差分：位置/旋转/缩放实际变化超过 epsilon 才标记 dirty；同一 tick 合并多个属性变化后只写回一次矩阵。
5. 预估峰值只作为池容量建议，设置硬上限与可观察指标（活动数、池大小、每 tick 创建/销毁数、同步更新数、写回数）。
6. 对大量预热/清理引入可配置的每 tick 工作预算；预算应根据运行耗时反馈调整，不能照抄 Reborn 的固定 50 个。
7. 通过与 Reborn 相同事件样例逐拍比对文本中心、矩阵顺序、轨道变换、插值时长和显示可见范围，验证视觉一致性；性能测量另看 MSPT 与实体数量。

## 7. 源码证据路径

以下相对路径均位于本轮提供的 `RhythMC-Reborn-master` 解压目录：

- `src/main/java/cn/frkovo/rhythmcv2/Game/Managers/Gameplay/EffectManager.java`：第 19-56 行维护事件队列、池更新与播放更新；第 76-99 行按 Beat 触发事件；第 160-171 行清理。
- `src/main/java/cn/frkovo/rhythmcv2/Game/Managers/Gameplay/TextDisplayManager.java`：第 14-29 行对象池/ID 映射；第 32-45 行分批销毁；第 47-89 行需求曲线；第 91-114 行 acquire/release；第 116-169 行预热、自适应扩缩容；第 171-184 行总清理。
- `src/main/java/cn/frkovo/rhythmcv2/Game/Effects/TextDisplay/GpTextDisplay.java`：第 14-52 行实体属性；第 63-81 行变换 dirty 状态；第 84-123 行显示属性与可见性；第 126-174 行矩阵写回、reset、destroy。
- `src/main/java/cn/frkovo/rhythmcv2/Game/Effects/EffectImpl/TextDisplayEffect.java`：创建/配置/显示；`TextDisplayTransEffect.java`：离散属性修改；`TextDisplaySyncTrackEffect.java`：持久轨道同步；`TextDisplayDesyncTrackEffect.java`：停止同步；`TextDisplayRemoveEffect.java`：释放至池。
- `src/main/java/cn/frkovo/rhythmcv2/Game/GameConstants.java`：池批大小、预读 Beat 窗口、margin、缩容阈值常量。
- `src/main/java/cn/frkovo/rhythmcv2/Game/Instances/GameInstance.java`：第 413 行调用 EffectManager.update，表明效果更新接入对局更新链。
- `RhythMC-Docs-main/docs/charter/effects/text-display*.md`：文本显示、变换、同步/取消同步/移除的数据格式说明。
- `RhythMC-Chart-Converter-master/src/main/java/cn/frkovo/converter/mapper/EffectTypeMapper.java`：效果类型转换映射。

## 证据边界

分析来自用户提供的源码 ZIP 快照及其配套文档，没有运行服务器或 Spark profiler 验证实际 MSPT、包流量、显示数量/视距行为。性能影响中的持续成本是从每次 update 调用链和 setter/矩阵写回逻辑推得，需以实际运行采样验证。本轮仅新增 Planner 分析记录，不修改工程源码、不编译。
