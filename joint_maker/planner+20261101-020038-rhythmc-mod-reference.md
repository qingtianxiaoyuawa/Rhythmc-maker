# RhythMC-MOD 0.5.1-beta.3 对 RhythMC Maker 的参考价值

## 任务范围
分析文件：`F:\b晴天小雨awa\程序\我的世界PCL\.minecraft\versions\RHYTHMC-MOD\mods\RhythMC-MOD-0.5.1-beta.3.jar`。

本次只读取 JAR 元数据、类结构、公开/私有方法签名和字节码结构，未修改 Maker 源码、未编译、未运行游戏验证。由于提供的是编译产物而不是源码，涉及具体实现细节的结论标记为“根据编译产物推断”。

## 项目画像
- Mod ID：`rhythmc-mod`，版本 `0.5.1-beta.3`。
- Minecraft：1.21.11；Fabric Loader 要求至少 0.19.5；客户端环境。
- 入口包括 Fabric 主入口、客户端入口和数据生成入口。
- JAR 内包含大量客户端 UI、编辑器、运行时、网络状态、音频和场景管理类，不是单纯的 Reborn 播放器。
- JAR 打包了若干依赖和 WorldEdit；许可证文件标识为 GPL-3.0。若未来复用代码而不是只参考架构，应先确认许可证义务和来源保留策略。

## 总体架构
该 Mod 可以分成六层：

1. **数据与持久化层**
   - `cv2.persistence.RmcdReader/RmcdWriter/RmcdStore`：读取、写入、列举、重命名、复制、删除 `.rmcd` 工程。
   - `ProjectService`：从编辑会话捕获工程、保存、打开、自动保存、恢复草稿、写入历史。
   - `HistoryCodec`、`ChartDiff`、`RecoveryDraft`：历史记录、差异分析和崩溃/异常恢复。

2. **统一谱面模型层**
   - `RawSong`：歌曲级对象，可以包含 World、Nether、End、Void 等多个谱面/场景等级。
   - `RawLevel`：manifest、meta、tracks、effects、noteAnimations、i18n 的组合。
   - `Track`：轨道独立保存速度、XYZ 位移、XYZ 旋转、XYZ 缩放、音符和其他轨道事件。
   - `Effect`、`InitialState`、`BPM`、`NumEvent`、`NoteAnimation`：运行时契约对象。
   - `RmcdReader` 对未知字段、非法数值、动画字段、分数 beat 和事件结构进行校验，说明其格式设计是“解析时校验”，而不是运行时到处兜底。

3. **时间与播放运行时**
   - `TimingManager` 提供 beat→毫秒、毫秒→beat、beat→tick、BPM 快照和下一 BPM 点查询。
   - `PlaybackInstance` 统一管理播放、暂停、恢复、seek、倍率、循环、结束、效果 seek、渲染帧和 tick。
   - `EffectManager` 按 beat 排队效果，维护 active persistent effects，支持初始状态、更新、seek、回退重放和清理。
   - 这一层的核心价值是：音频、音符、轨道、特效不各自维护时间，而是共享 `TimingManager/PlaybackInstance` 的时钟。

4. **实体与性能层**
   - `NoteManager`：音符对象池、预加载、可见窗口、需求曲线、池未命中统计、渐进销毁。
   - `TextDisplayManager`：TextDisplay 对象池、按效果需求构建需求曲线、预加载、容量和池未命中统计、释放和渐进销毁。
   - `GpTextDisplay`：封装 TextDisplay 的文字、透明度、背景、阴影、透视、对齐、插值、位置、旋转、缩放和矩阵更新。
   - `PlaybackInstance` 还区分 render lead ticks、frame render beat、渲染距离和音符预生成窗口，避免按完整谱面一次性生成实体。

5. **制谱器与 UI 层**
   - `cv2.editor.EditorSession` 是编辑器核心会话，持有谱面、原点、轨道编辑实例、判定线、相机跟随、播放模式、循环区间、当前光标、预览 beat、特效 ghost、历史和选区。
   - `BeatGrid`、`RulerPlanner/RulerRenderer`、`EventRenderer`、`EffectGhostPlanner/Renderer`、`OverviewSnapshotBuilder` 支持时间轴、网格、事件标记、特效幽灵预览和总览。
   - `EffectEditScreen` 使用字段规格、分类、搜索、折叠、问题提示、参数控件、预设和 lint，而不是把所有参数塞进一个拥挤面板。
   - `TimelineOverviewScreen`、`TimelineGrid`、`TimelineHud` 和 `WaveformGuiElement` 说明其时间轴是独立的编辑器子系统。
   - `UiTheme`、`UiDraw`、`UiDropdown`、`UiMultiSelect`、`UiSegmented`、`UiScrollList` 等形成统一 UI 基础层，适合作为 Maker UI 重构的架构参考。

6. **通信与客户端状态层**
   - `CharterTransport`、`CharterServer`、`CharterAudioClient` 将编辑状态、谱面状态、效果状态、轨道状态、工具栏、时间轴、场景和音频拆成独立 state snapshot。
   - 客户端发送细粒度请求，例如 transport、view seek、note edit、event update/remove、zoom、bounds，而不是每次操作都重发完整工程。
   - `CharterAudioClient` 还维护上传、异步加载、播放请求、studio 请求和重同步状态。

## 对 Maker 最有价值的参考

### 1. 统一 ChartTiming 时钟
Maker 已有 `ChartTiming`、`PlaybackTimelineClock`，但后续应进一步接近 Mod 的分层方式：

- 一个权威时钟同时服务音频位置、beat、判定线 Z、轨道变换、音符可见窗口和特效状态。
- seek 必须先把目标毫秒转换为目标 beat，再由同一时刻重建效果状态、音符窗口和轨道位置。
- 不允许 UI 播放头、音频进度、服务器/客户端判定进度各自独立推进。
- BPM 变化、offset、播放倍率和循环都应集中在 Timing/Playback 会话中，而不是散落在渲染器和屏幕类。

### 2. 音频播放与音符判定解耦
Mod 的 `CharterAudioEngine` 具备以下能力：

- 解码后的 PCM 缓冲区和音频长度。
- 专用 worker 线程写入音频设备。
- `play(startMs, speed)`、`pause`、`seek`、`stop`、`setSpeed`、循环区间。
- 独立的 `positionMs` 与 `audiblePositionMs`，能够区分逻辑位置和实际听到的位置。
- offset、命中音效预排程、音频增益、判定音增益、输出限制器、underrun/stall/最大间隔诊断。
- 波形异步生成和按窗口读取。

对 Maker 的直接启发：不要用外部播放器进程的轮询结果作为唯一时钟；至少应建立本地 PCM/音频缓冲区、单调时钟、seek generation、音频实际可听位置和诊断指标。外部播放器可以继续作为输出实现，但不应承担谱面时序权威。

### 3. 对象池与需求曲线
Mod 不是简单地“每个音符进入视野就创建实体、离开就删除”：

- 启动前根据整张谱面构建 demand profile。
- 预先准备初始对象池。
- 播放中按窗口复用对象。
- 对象池不足有 `poolMisses` 指标。
- 退出或场景切换时渐进销毁，避免单 tick 批量 remove。
- TextDisplay 与音符使用独立池，因为生命周期和更新频率不同。

Maker 当前 `RhythmcMaker.java` 中已有显示池、预加载和 TrackPlayback/PoseBuffer 相关逻辑，但主要编排仍集中在大类中。规划上应拆出：

- `NoteEntityPool`：音符实体复用和可见窗口。
- `TextDisplayPool`：特效显示实体复用。
- `PlaybackDemandProfile`：按 beat 统计同时活跃的音符、文本和动画峰值。
- `GradualEntityDisposer`：限额销毁并记录池命中/未命中。

### 4. TextDisplay 矩阵生命周期
`GpTextDisplay` 表明 Reborn 的 TextDisplay 不是简单 setPos：

- 持有独立的 `Matrix4f transformationMatrix`。
- 位置、旋转、缩放被缓存后统一应用。
- `dirty` 状态避免没有变化时重复写实体属性。
- 支持插值持续时间、frame、frame break、链式 frame 和 settle 状态。
- `applyFrame` 接受矩阵/变换对象，说明特效动画应先求解矩阵，再提交实体。
- `TextDisplayManager.rebase` 支持整体场景原点变化，而不必逐个重新解释特效数据。

Maker 当前已有 `setTextDisplayTransformation`、`setBufferedTextDisplayPose`、TextDisplay duration/seek fold 和动画求值类，但应避免将位置、旋转、缩放、插值、文本属性分别散落更新。建议形成统一 `DisplayPose`/`DisplayFrame` → `DisplayEntityApplier` 管线，并只在 dirty 时提交。

### 5. 特效 seek 与持久状态折叠
Mod 的 `EffectManager` 具备：

- 按 beat 排队事件。
- active persistent effects 集合。
- 初始状态应用。
- `update(double)` 正向推进。
- `seekTo(double)` 和 rewind，重新构建目标时刻的特效状态。
- TextDisplay rebase 和预加载批次。

Maker 已有 `PersistentEffectStateFold`、`TextDisplayEventFold`、`TextDisplayDurationAtSeek`、`PotionDurationAtSeek`，方向正确，但需要让所有特效统一遵守同一状态机：

`initialState → ordered events up to target beat → active persistent state → frame render`。

不要为每一种效果单独维护一套“拖动时间轴后的特殊补偿逻辑”。

### 6. 编辑器 UI 和时间轴
Mod 的 UI 参考价值高于直接复制视觉样式：

- 时间轴是独立 Overview/Timeline 模块，不和参数编辑器互相挤占空间。
- 事件按类型、轨道、时间区间形成可折叠行。
- 搜索、分类筛选、折叠 beat、匹配前后跳转和 selection bar 都有缓存版本号。
- 只在过滤条件、折叠状态、marker 数据或快照版本改变时重建 display rows/marker map。
- `WaveformGuiElement` 可作为音频波形、beat 网格、判定线和事件 marker 的统一底层。

Maker 当前 `EffectEditorImGuiView`、`EffectTrackLayoutModel` 和 `EffectPreviewViewport` 可以保留逻辑，但应补充缓存版本、分层布局、时间轴视图模型和拖动结束后的统一 seek 提交。

### 7. 场景/竞技场管理
Mod 的 `ArenaStore/ArenaManager/ArenaImportService/ArenaPreviewCache/ArenaPreviewRenderer/ArenaValidator` 形成完整场景生命周期：

- 场景元数据、来源、尺寸、边界、相机、颜色、阶段、预览和统计信息分开保存。
- 导入、校验、缓存、预览、复制和导出分离。
- 结构文件读取有摘要/头部解析，避免每次加载都完整解析大结构。
- 场景切换可以应用 stage、重建判定区域和重基 TextDisplay。

Maker 的 `SceneEditStorage` 和场景界面目前更适合扩展为 `SceneManifest + SceneRuntime + ScenePreviewCache` 三层，避免进入已有谱面世界时同步读取、解析和生成所有场景实体。

### 8. 动画编辑器与特效编辑器扩展
Mod 额外提供：

- `StudioClip`：对象、层级、长度、重复、局部 beat、世界矩阵、轨迹采样。
- `StudioCompiler`、`StudioFx`、`StudioOps`：编译、曲线、预设和操作。
- Lottie、SVG、PNG 文字、点阵文字和歌词导入。
- `EffectGhostRenderer`、`StudioGhostMath`：编辑时不立即提交正式运行实体，而是显示 ghost。
- `NoteAnimationController`：轨道动画、提前量、长度、隐藏、延迟、是否播放等参数可单独编辑。

对 Maker 的价值是把“编辑预览”和“正式播放实体”分开。编辑器拖动时间轴时优先更新 ghost/preview buffer，点击播放后才启用正式实体和音频；这符合用户此前要求的按需初始化和降低进入世界卡顿。

## Maker 当前已有能力与主要差距

### 已有、可继续沿用
- `ChartStorage`、`ChartTiming`、`TrackPlayback`、`PlaybackCoordinates`、`PlaybackDistance`。
- `PlaybackTimelineClock`、`PersistentEffectStateFold`、`TextDisplayEventFold`。
- `EffectAnimationEvaluator`、`EffectAnimationSnapshot`、动画 JSON 编解码。
- `EffectParameterSchema/Codec/Migration`，比早期直接 Map/JSON 编辑更工程化。
- `EffectPreviewViewport`、`EffectTrackLayoutModel`、ImGui 特效界面。
- Maker 已经有 TextDisplay 变换、显示池、特效参数编辑和场景存储入口。

### 主要差距
1. **播放编排过于集中**：Maker 的大量播放、实体、音频和特效逻辑仍集中在 `RhythmcMaker.java`，边界不如 Mod 的 PlaybackInstance/Manager 分层清晰。
2. **音频时钟能力不足**：Maker 仍能看到外部音频进程和进度轮询状态；缺少 Mod 那种 PCM、audible position、seek generation、underrun/stall 指标体系。
3. **实体需求控制不足**：已有池化迹象，但没有充分暴露 demand profile、pool miss、渐进销毁和对象容量指标。
4. **特效状态不够统一**：已有多个 fold 类，但需要统一到一个可 seek 的 EffectRuntimeState；否则新增 TextDisplay、轨道、隐藏音符和药水效果时容易出现不同步。
5. **UI 缓存和时间轴分层不足**：Maker 当前界面可以显示和编辑，但还需要把时间轴、事件 marker、参数面板、预览视口和列表缓存分开。
6. **场景加载职责混合**：进入谱面世界时，场景读取、谱面读取、实体生成和播放准备容易在同一链路同步发生，这是进入已有/导入谱面缓慢的重点风险。
7. **持久化和恢复能力较弱**：应评估是否需要 `.rmcd` 类工程容器、原子保存、历史快照、自动保存、恢复草稿和差异摘要，而不是只覆盖当前谱面文件。
8. **编辑预览与正式播放没有完全隔离**：Mod 有 ghost、preview、runtime 三类对象；Maker 应避免编辑器刚打开就启动完整播放链路。

## 推荐的 Maker 借鉴优先级

### P0：先修播放一致性和卡顿根因
- 建立唯一 `ChartTiming`/`PlaybackSession` 时钟。
- 将音频、判定线、轨道、音符和效果 seek 绑定到一个 `PlaybackCommand`。
- 将实体生成改为可见窗口 + 对象池 + 每 tick 预算。
- 将进入世界的初始化改成“数据索引/轻量快照优先，播放后生成实体”。
- 为 pool miss、每 tick 生成数、删除数、seek 重建耗时、音频延迟建立诊断指标。

### P1：重构特效和 TextDisplay 管线
- 统一事件排序、持久状态、seek 重建、矩阵求解和 dirty 提交。
- 建立 `TextDisplayPool`、`DisplayFrame`、`DisplayPose` 和 `DisplayEntityApplier`。
- 将显示属性更新与世界实体创建分离。
- 支持场景原点 rebase 和批量/限额销毁。

### P2：时间轴和 UI 视觉重构
- 独立时间轴模型、波形、beat/chunk 网格、marker、选择状态和拖动事务。
- 参数编辑器只负责当前事件；时间轴只负责定位、筛选和批量操作。
- 增加缓存版本号，禁止每帧重新构建所有 marker 和布局。

### P3：工程持久化和高级动画
- 评估 `.rmcd` 类容器是否适合 Maker，至少实现原子写入、历史、自动保存和恢复草稿。
- 引入轨道动画和特效动画的统一 keyframe/curve 模型。
- 再考虑 Lottie/SVG/点阵文字等高级导入，不能先于播放和编辑性能稳定。

## 不能直接照搬的部分
- Mod 的 `CharterTransport/CharterServer` 面向客户端-服务端协作；Maker 是本地客户端编辑器，不能无条件复制网络状态层。
- Mod 的 `RawSong` 多维度歌曲/场景结构可能比 Maker 当前格式更复杂，应先做领域映射，不要直接替换现有 `ChartStorage`。
- Mod 的 `CharterAudioEngine` 是专用 PCM 输出线程，接入 Maker 需要考虑 Fabric 客户端线程、设备独占、音频格式和退出释放。
- Mod 的 arena/WorldEdit/结构管理与 Maker 的场景格式不一定兼容，应只借鉴缓存、验证和生命周期分层。
- 编译产物没有源码级变量和注释，矩阵乘法顺序、线程调度细节、精确数值常量仍需从源码或运行时日志进一步验证。

## 建议交给编译合作方的任务清单
1. 先从 `RhythmcMaker.java` 抽出 `PlaybackSession`、`PlaybackClock`、`NoteEntityPool`、`TextDisplayPool`、`EffectRuntime`，保持现有播放逻辑不变。
2. 给每个播放会话增加 demand profile、pool miss、生成/删除预算、seek 重建耗时和音频位置差值指标。
3. 统一 `seek(targetBeat)`：音频、判定线 Z、轨道矩阵、音符可见窗口、特效状态和玩家/相机位置必须一次提交。
4. 将 TextDisplay 更新改为 `DisplayFrame`/矩阵快照 + dirty 提交，禁止无变化时重复写实体。
5. 将编辑器时间轴、波形、marker 和参数面板拆为独立模型，拖动使用一次性 seek 事务。
6. 增加进入谱面、播放、保存、挖掘方块、拖动时间轴等场景的性能计数和日志标签，结合 spark 做运行时验证。
7. 最后才进行 UI 视觉复刻；先保证播放时钟、实体生命周期和加载路径与实际 RhythMC 播放一致。

## 最终判断
这个 JAR 对 Maker 的最大参考价值不是某个单独 UI 控件或 TextDisplay 参数，而是完整的“谱面数据 → 统一时钟 → 需求曲线/对象池 → 特效状态机 → 矩阵帧 → 音频输出 → 编辑器状态”工程链路。

Maker 最值得优先吸收的是：
- `TimingManager + PlaybackInstance` 的单时钟模型；
- `NoteManager/TextDisplayManager` 的需求曲线、预加载和对象池；
- `EffectManager` 的事件队列、持久状态和 seek 重建；
- `GpTextDisplay` 的矩阵/dirty/interpolation 生命周期；
- `RmcdStore/ProjectService` 的原子保存、历史和恢复；
- `EditorSession + TimelineOverview + EffectEditScreen` 的编辑器分层与缓存。

这些内容可以显著帮助解决 Maker 当前的音画不同步、进入谱面缓慢、播放后操作延迟、TextDisplay 视觉差异和特效编辑器拥挤问题，但不能通过直接复制 JAR 类名来解决，必须先映射到 Maker 的客户端-only 架构。
