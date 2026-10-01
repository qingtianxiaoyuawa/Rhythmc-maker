# RhythMC Charter V2 —— 插件主导、Mod 必需的游戏内制谱器设计稿

- 状态：**设计提案（未实现）** —— 2026-09-07
- 本仓库：`RhythMC-Charter-V2`（Paper 插件，Java 21 / Spigot 1.21.11 / Maven）
- 关联仓库：`RhythMC-Reborn`（游戏运行时与谱面格式参照）、`RhythMCChartMaker`（客户端 mod 编辑器）、`RhythMC-Preview`（预览插件）
- 格式与规则基线：`RhythMC-Reborn/docs/charter/*`（song-format / note-types / animation / effects / arena-format）、`docs/chart-i18n-design.md`

---

## 目录

1. [一页摘要](#1-一页摘要)
2. [背景与定位](#2-背景与定位)
3. [设计原则](#3-设计原则)
4. [非目标与红线](#4-非目标与红线)
5. [总体架构](#5-总体架构)
6. [时间模型：BPM 决定一切](#6-时间模型bpm-决定一切)
7. [全精度数据模型](#7-全精度数据模型)
8. [编辑会话与项目结构](#8-编辑会话与项目结构)
9. [游戏内放置与可视化](#9-游戏内放置与可视化)
10. [播放引擎（Transport）](#10-播放引擎transport)
11. [Mod 契约提案：rhythmc:charter_audio](#11-mod-契约提案rhythmccharter_audio)
12. [命令、GUI 与 HUD](#12-命令-gui-与-hud)
13. [撤销、持久化与导出](#13-撤销持久化与导出)
14. [Lint 校验](#14-lint-校验)
15. [试玩联动](#15-试玩联动)
16. [性能预算](#16-性能预算)
17. [里程碑](#17-里程碑)
18. [风险与开放问题](#18-风险与开放问题)
- [附录 A：BeatClock 换算](#附录-abeatclock-换算)
- [附录 B：charter_audio opcode 载荷表](#附录-bcharter_audio-opcode-载荷表)
- [附录 C：命令全表](#附录-c命令全表)
- [附录 D：Mod-only 启动门](#附录-dmod-only-启动门)

---

## 1. 一页摘要

**Charter V2 把制谱器搬进游戏本体**：编辑、放置、预览、试听、试玩全部发生在 Minecraft 世界里，由 Paper 插件驱动，并要求谱师安装轻量 Fabric mod（`RhythMC-CharterMod`，本设计稿提案）。插件负责编辑器、世界内可视化和编译；Mod 负责本地音频时钟、断点播放、A-B 循环和音画同步。

| 需求 | 承接模块 |
|------|----------|
| 谱师友好 | 游戏内所见即所得、操作日志式撤销、热栏工具箱、向导式建谱 |
| 由 BPM 决定 | `BeatClock`：beat 为第一公民，ms 全部派生；snap 网格；BPM 变速分段积分 |
| 可视化 | 双通道：3D 世界渲染（当前预览 beat 的运行时姿态 + 播放动画）+ HUD 时间轴（bossbar 游标 / actionbar 拍号） |
| 游戏内播放 | `Transport`：服务端 tick 主时钟驱动视觉；CharterMod 本地音频时钟精确跟随（Mod 为前置条件，缺席即拒绝会话） |
| 游戏内放置 | 准星射线 × 判定平面 → 空间坐标；编辑游标 → 拍数坐标；两者解耦 |
| 格式兼容 | 编译产物遵守标准 `manifest.yml` + `world/nether/end/void.rmcc`；`.rmcd` 自己保留编辑信息，不能宣称 Reborn 运行时保留未知字段 |

---

## 2. 背景与定位

### 2.1 现有工具链

| 工具 | 形态 | 职责 | 编辑方式 |
|------|------|------|----------|
| RhythMCChartMaker | Fabric 客户端 mod | 谱面编辑 UI + 本地音频 + 经 `rhythmc:chart_preview` 通道推送预览 | 桌面式 2D 屏幕编辑器（`ChartEditorScreen`） |
| RhythMC-Preview | Paper 插件 | 纯预览：可视化回放、arena 粘贴、文件缓存 | 不可编辑 |
| RhythMC-Reborn | Paper 插件 | 完整游戏运行时（谱面格式的语义基准） | — |

### 2.2 Charter V2 的差异化定位

CharterMaker 走的是「**客户端编辑、服务器看效果**」路线；Charter V2 走「**服务器即编辑器**」路线：

- **单一受支持工况**：谱师安装并连接 CharterMod 后进入制谱会话；插件负责编辑与世界内预览，Mod 负责本地音频时钟；
- **空间直觉**：音符直接在玩家眼前放置，`pos` / `scale` / `rotation` 不再是数字输入框，而是「看到哪、放到哪」；
- **真机预览**：渲染与判定粒度就是游戏本体的 20tps / 50ms tick——预览不会比实际游玩更「顺」，所见即玩家所得；
- **Mod 是明确前置条件**：本设计不维护无 Mod 分支，避免为未验收的降级状态增加复杂度。

两条路线共用同一套谱面格式与 arena 格式，发布产物互通：ChartMaker 导出的标准谱面可以在 V2 里导入继续编辑，V2 发布的标准 `.rmcc` 也可以被 ChartMaker 打开（`.rmcd` 草稿为 V2 私有，不互通）。

### 2.3 与跨仓契约的关系（重要）

- 现有 `rhythmc:chart_preview` 通道归 `RhythMCChartMaker` + `RhythMC-Preview` 所有。**Charter V2 不复用、不重定义该通道**；若未来需要与 ChartMaker 直接互通，必须先按该契约行事并同步两仓 + 文档。
- 本设计稿**提案**一条新通道 `rhythmc:charter_audio`（Charter 插件 ↔ CharterMod，用途单一：音频传输控制）。它是全新用途的独立通道，不是 chart_preview 的第二实现；但依据本仓 AGENTS.md 的跨仓规则，**落地前需用户明确确认**，并在 Charter / CharterMod 两仓的契约文档中同步登记（见 §11）。

---

## 3. 设计原则

1. **谱师友好**
   - 所有高频操作 2 秒内可达：放/删/选一条热栏 + 准星完成；属性细节才进 GUI。
   - 破坏性操作全部可撤销（操作日志 + 反向操作，见 §13.1）。
   - 永不丢稿：自动保存 + dirty 状态显式提示 + 崩溃恢复。
   - 建谱向导：新建歌 → 填 manifest 基本信息 → 设 BPM/offset → 直接进编辑，全程一个 GUI 流。
2. **由 BPM 决定**
   - 内部一切时间量以 `BeatFraction` 存储；毫秒只在与音频、判定窗交互的边界处由 `BeatClock` 换算。
   - snap 网格（1/1~1/16 + 三连音系）是唯一推荐的拍值离散化方式；手输任意 beat 属于高级功能。
   - BPM 变速（`bpms` 事件表）从第一天就是一等公民：网格、小节线、时间轴密度图全部按事件表分段计算。
3. **可视化**
   - 双通道呈现：**3D 世界**（真实渲染管线，ItemDisplay）负责空间与演出；**HUD 时间轴**负责时间结构（小节/密度/游标）。
   - 编辑态按当前游标 beat 以运行时同一公式渲染「预览姿态」，播放态显示完整飞行动画——两个视角合起来才是完整心理模型。
4. **游戏内播放、放置**
   - 播放是「彩排」而不是「看电影」：谱师站在玩家位置、以玩家视角看谱面飞来。
   - 放置 = 空间（准星）× 时间（游标）解耦组合，两个输入通道互不干扰。

---

## 4. 非目标与红线

**非目标（V1 明确不做）**

- 不做无 Mod 降级路径：本项目唯一目标工况是 Plugin + CharterMod；缺少 Mod 时直接拒绝进入制谱会话。
- 不做多谱师实时协同编辑（架构上会话隔离已预留空间分区，但 V1 单会话单人）。
- 不做 HTTP/WebSocket：插件侧唯一外部通信是 Bukkit plugin channel。
- 不做资源包分发 / 音频上传服务器：音频永远在谱师本地（mod 端）播放，与 ChartMaker 的信任模型一致。
- 不做外部桌面编辑器或网页编辑器。

**红线（继承跨仓决定，不可违反）**

- **判定粒度保持诚实**：20tps = 50ms/tick，PERFECT ±110ms ≈ ±2 tick 是诚实分辨率极限。本插件**不得**提供任何「更精细的判定预览」「early/late PERFECT 细分」；手感调参只通过试玩（playtest-only）。
- **谱面 i18n 内嵌**：`name` / `composer` / `charters` 永不本地化；i18n 块内 `_` 前缀键是工具元数据、加载时剥离；本插件写出的谱面必须原样遵守（见 §13.3）。
- **不自造第二条 chart_preview**：见 §2.3。
- 不重新定义谱面 JSON 字段语义：字段名、类型、默认值一律以 `RhythMC-Reborn` 反序列化器实现为准。

---

## 5. 总体架构

### 5.1 组件图

```
┌─────────────────────────── Paper 服务器（本插件） ────────────────────────────┐
│                                                                              │
│  CharterSessionManager ── CharterSession（每谱师一个，状态机见 §8.2）          │
│        │                        │                                            │
│        │        ┌───────────────┼────────────────┬───────────────┐           │
│        │        ▼               ▼                ▼               ▼           │
│        │   EditorChart     BeatClock        Transport        UndoManager     │
│        │   (内存谱面模型)   (beat↔ms,        (play/pause/     (逆操作)        │
│        │   精确草稿模型)    snap,BPM段)       seek/loop/speed)                │
│        │        │               │                │                           │
│        │        ▼               └───────┬────────┘                           │
│        │   PlacementService              ▼                                    │
│        │   (准星×平面反解,ghost)   PreviewRenderer                             │
│        │                          (当前 beat 姿态 / 播放态动画, 对象池)        │
│        │                               │                                     │
│        │   PersistenceService  ◄────────┘  LintService                       │
│        │   (.rmcd 草稿+            (规则表 §14)                               │
│        │    _editor.yml 索引)                                                  │
│        │                                                                      │
│        │   Commands (/charter)   GUI 面板   HUD(actionbar/bossbar/scoreboard)  │
│        │                                                                      │
│        └────────────────► Plugin Channel: rhythmc:charter_audio（提案）        │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │ opcode 帧（附录 B）
                        ┌──────────▼──────────┐
                        │  RhythMC-CharterMod │  （必需，Fabric，新仓库提案）
                        │  AudioEngine        │  Java Sound + SPI 解码链
                        │  (seek/loop/speed)  │  （对齐 ChartMaker 的成熟方案）
                        │  CharterAudioClient │  本地音频库 .minecraft/rhythmc-audio/
                        └─────────────────────┘
```

### 5.2 模块职责表

| 模块 | 职责 | 关键依赖 |
|------|------|----------|
| CharterSessionManager | 会话生命周期、多会话空间分区、进退世界 | Bukkit |
| EditorChart | `.rmcd` 精确草稿模型；可保留导入源中的未知 JSON/YAML 键，但这只属于 Charter 自己的 round-trip 能力 | fastjson2 / snakeyaml |
| BeatClock | beat↔ms 双向换算（BPM 分段积分）、offset、snap 量化、小节线计算 | 无（纯函数） |
| PlacementService | 射线×判定平面反解 `pos`；ghost 渲染；音符 CRUD；holdGroup 维护 | Bukkit RayTrace |
| Transport | 服务端主时钟（tick 驱动）、播放/暂停/seek/loop/变速；向 mod 下发指令并做漂移校准 | Bukkit Scheduler |
| PreviewRenderer | 编辑态按当前预览 beat 计算姿态；播放态按 Reborn 的距离、轨道变换和可见区间逐 tick 计算；对象池 | ItemDisplay |
| UndoManager | 消费 `operations.ndjson`：undo/redo 追加反向领域操作（§7.6），`snapshots/` 仅用于快速恢复 | 操作日志 |
| PersistenceService | 读写 `.rmcd` 草稿与 `_editor.yml` 轻量索引；自动保存；调用 Compiler 输出 build/ 产物 | 文件 IO |
| LintService | 发布前静态检查（§14） | BeatClock |
| CharterAudioBridge | `rhythmc:charter_audio` 服务端端：握手、能力协商、指令下发、STATE 接收 | Bukkit Messenger |
| 试玩控制器 | 后置适配项；必须单独接入 Reborn 的时钟、输入与判定生命周期 | Reborn 适配层 |

### 5.3 关键数据流

```
放置:  准星视线 ──RayTrace──▶ 判定平面交点 ──▶ pos[x,y,z]
       编辑游标(beat) ──snap量化──▶ note.beat
       两者合成 ghost ──确认──▶ EditorChart.notes + 操作日志追加 ──▶ 增量重渲染

播放:  Transport 每 tick: beat += (50ms 换算) × speed
       ├──▶ PreviewRenderer: 当前轨道姿态 + speed distance + note 局部变换（与 Reborn 公式对齐）
       └──▶ 每 10 tick 对账: |插件beat换算ms − mod音频ms| > 60ms ⇒ 重 seek mod

保存:  EditorChart ──序列化──▶ <draftId>.rmcd（原子替换）+ _editor.yml 索引
编译:  .rmcd ──Compiler──▶ build/<draftId>/<revision>/（manifest.yml + .rmcc + compile-report.json）
```

---

## 6. 时间模型：BPM 决定一切

### 6.1 BeatClock

- **beat 是存储与编辑的第一公民**。内部 beat 不是 `double`，而是规范化有理数；`.rmcc` 的 `meta.bpms` 是 BPM 事件表（`{beat, bpm}`，beat 递增）；`meta.offset` 为全局毫秒偏移。
- 换算（完整公式见附录 A）：

```
ms(beat) = offset + Σᵢ (60000 / bpmᵢ) × (clamp(beat, beatᵢ, beatᵢ₊₁) − beatᵢ)
beat(ms) = 反向分段解（先扣 offset，再逐段累减）
```

- snap 量化：`q(beat) = round(beat × snap) / snap`，结果直接生成最简分数；snap ∈ {1, 2, 3, 4, 6, 8, 12, 16}（每拍分割数，含三连音系 3/6/12）。
- 小节线：默认 4/4 拍号（V1 固定；拍号事件列为开放问题 §18）。

### 6.2 全精度规则

- 编辑态、撤销栈、自动保存、工作文件全部使用 `BeatFraction`：`BigInteger numerator` + `BigInteger denominator`，分母始终为正，构造时立即约分。
- BPM 事件的 `beat`、音符 `beat`、效果 `beat`、轨道事件 `startBeat/endBeat`、游标、书签和 A-B 边界统一使用该类型。禁止 `float/double` 作为 beat 的权威存储。
- 所有排序和相等判断使用精确分数比较（交叉相乘），不得先转换为 `double`。
- GUI 可以显示小数，但显示值不是模型值；谱师可切换显示 `1/3`、`7/16` 或近似小数。
- `ms` 是派生量。内部优先使用 `BigDecimal`；只有 Bukkit tick、音频协议和兼容 `.rmcc` 导出等边界才转换为整数毫秒或有限小数。`bpm` 用十进制字符串（`BigDecimal` 语义）存储，导出转 JSON number；毫秒推导中的除法使用固定 scale（见附录 A），保证同输入同输出。

### 6.3 工作格式与发布格式

现有 `.rmcc` 的 beat 是 JSON number，无法直接表达任意分数。因此分为两层：

1. **精确工作格式**：`.rmcd` 的 `draft.json` 和操作日志保存 beat 为字符串，例如 `"1/3"`、`"7/16"`、`"12"`；所有编辑、撤销、恢复均以此文件为权威源。
2. **兼容发布格式**：导出 `world.rmcc` 等标准文件时，将分数按固定 `exportScale` 转成有限小数 JSON number。
3. **精度闸门**：如果分数无法在 `exportScale` 下无损表达，`--strict` 必须失败，不能静默四舍五入；普通导出也必须生成 warning 和误差报告。这里的“无损”只保证 Compiler 的发布小数，不代表 Reborn 运行时仍持有分数。破坏排序/分组/触发顺序的映射不属于“舍入误差”，一律按 §14 的 error 行拒绝（见 §7.3）。
4. **回读优先级**：Charter V2 生成的谱面优先读取 `.rmcd`；外部谱面没有草稿时，才从 JSON number 以 `BigDecimal` 导入并记录 `PRECISION_LOSS_RISK`。这不能恢复原作者在 JSON number 之前的真实分数。

推荐默认值：`exportScale=18`、`maxDenominator=2^20*3^12*5^8*7^4`。这只是工程保护上限，不改变游戏的 20tps 判定粒度。Reborn 的 `BPM`、`Note`、`NumEvent`、`Effect` 模型均使用 `double`；`TimingManager` 还会把 beat→毫秒结果 `Math.round` 为 `long`，所以任意分数的全精度只存在于 `.rmcd` 和 Compiler 内。

### 6.4 编辑精度 vs 判定精度（诚实声明）

- 编辑层允许任意精确分数 beat，但判定窗是硬约束：**PERFECT ±110ms、GREAT ±220ms**（继承 Reborn，不改）。
- Lint 对「同轨可交互音符间隔」按换算毫秒给出**建议级**提示（warning，不阻断），参考下表：

| BPM | 1/4 拍间隔 | 1/8 拍间隔 | 说明 |
|-----|-----------|-----------|------|
| 100 | 150ms | 75ms | 1/8 已低于 PERFECT 窗 |
| 120 | 125ms | 62.5ms | 1/8 密排需谨慎 |
| 160 | 93.75ms | 46.9ms | 1/4 已开始吃紧 |
| 200 | 75ms | 37.5ms | 1/4 密排接近不可判定 |

规则**只提示、不禁止**：密集音符是否可玩，最终由试玩决定（与「手感调参 = playtest-only」的既有决定一致）。

### 6.5 BPM 变速的编辑体验

- 时间轴 HUD 的密度条、小节线在变速段自动重排（每段独立换算宽度）。
- 变速段内 snap 网格逐拍不等宽——渲染为「每小节一个刻度块，内部按 snap 均分视觉格」，避免逐格 ms 标注的噪音。
- `bpms` 事件表本身通过 GUI 事件编辑器维护（startBeat / bpm 两列），校验 beat 严格递增。

---

## 7. 全精度数据模型

### 7.1 `BeatFraction`

```text
record BeatFraction(BigInteger numerator, BigInteger denominator)

不变量：denominator > 0；gcd(abs(numerator), denominator) == 1；zero 固定为 0/1
操作：add / subtract / multiply / divide / compareTo / floor / ceil / round（HALF_UP）
序列化："numerator/denominator"；整数可简写为 "numerator"
```

编辑态、撤销栈、自动保存和工作文件全部使用 `BeatFraction`。BPM 事件的 beat、音符 beat、效果 beat、轨道事件 `startBeat/endBeat`、游标、书签和 A-B 边界统一使用该类型。禁止 `float/double` 作为 beat 的权威存储。

snap、复制小节、移动音符、拖动 HOLD 终点、插入 BPM 事件全部调用分数运算；GUI 显示的小数只是展示值。浮点输入只允许在导入边界使用 `BigDecimal`，禁止 `new BigDecimal(double)`。

### 7.2 精确工作格式与兼容发布格式

现有 `.rmcc` 的 beat 是 JSON number，无法直接表达任意分数。因此分为两层：

1. `.rmcd` 的 `draft.json` 是 Charter V2 的权威工作文件，所有 beat 写成字符串，例如 `"1/3"`、`"7/16"`、`"12"`。
2. `world.rmcc` 等发布文件保持 Reborn 兼容格式，将分数按固定 `exportScale` 转为有限小数 JSON number。
3. 推荐 `exportScale=18`。像 `1/3` 这样的无限小数不能在现有 JSON number 中无损表达，因此必须生成 `PRECISION_LOSS`；`/publish --strict` 直接失败，不能静默四舍五入。即使是有限小数，Reborn 反序列化后也会进入 IEEE-754 `double`，因此“无损”必须定义为导出文本可表达且通过目标运行时语义校验，而不是分数对象恒等。若要求发布后仍保留任意分数，必须先升级跨仓 chart schema，不能用私有字段伪装兼容。
4. 外部导入没有 `.rmcd` 时，先以 `BigDecimal` 读取 JSON number，再构造分数并记录 `PRECISION_LOSS_RISK`。

### 7.3 最终谱发布闸门

```text
精确工作文件 → 精确模型校验 → 分数到 runtime 小数的映射预览
→ 导出误差报告 → Lint --strict → 写临时目录
→ 重新读取临时目录 → 语义哈希比对 → 原子替换最终谱目录
```

发布前后必须保持 beat 排序、holdGroup、BPM 段和效果触发顺序不变——这是硬门槛，破坏即拒绝发布（对应 §14 的 error 行）。纯数值舍入误差在普通发布中记 warning 并写入报告；`--strict` 下任何 `PRECISION_LOSS` 都拒绝发布。任何导出都不得覆盖精确工作文件。这样“最终谱”明确分为精确工作源和有限精度运行产物。

### 7.4 Compiler 设计

Compiler 是独立于编辑器 UI 的纯领域管线。它不读取玩家操作事件，只接受 `.rmcd`，因此可以在服务器主线程外执行，也可以在 CI 或命令行中复用。

```text
RmcdReader
  → DraftValidator
  → OperationReplayer
  → ExactChartNormalizer
  → RuntimeMapper
  → CompatibilityValidator
  → RmccWriter
  → ReimportVerifier
```

各阶段职责：

1. `RmcdReader`：验证 ZIP 条目、版本、UTF-8、大小上限和 SHA-256；禁止路径穿越和未知可执行条目。
2. `DraftValidator`：校验草稿结构、操作序列连续性、`beforeHash/afterHash`、实体 ID 唯一性和分数语法。
3. `OperationReplayer`：从最近快照开始按 `seq` 顺序重放操作；重放结果必须与 `draft.json.model` 的 hash 相同。
4. `ExactChartNormalizer`：排序 BPM、轨道事件、音符和效果；规范化分数；重建 HOLD 组；不改变语义。
5. `RuntimeMapper`：把精确模型映射到 Reborn 的 manifest/YAML 和 `.rmcc`/JSON 结构；只在此阶段进行 beat 编码转换；把草稿的稳定字符串实体 ID（如 `"track-0"`）映射为运行时 int track id / 数组下标，映射表写入 `compile-report.json`。
6. `CompatibilityValidator`：检查 JSON number 的导出误差、字段范围、目标运行时支持的 effect/note 类型。
7. `RmccWriter`：写入临时发布目录，并输出 `compile-report.json`。
8. `ReimportVerifier`：用与 Reborn 相同的字段语义重新读取产物，验证排序、时间顺序、holdGroup 和效果顺序。

Compiler 输出：

```text
build/<draftId>/<revision>/
├── manifest.yml
├── world.rmcc
├── nether.rmcc / end.rmcc / void.rmcc
└── compile-report.json
```

`compile-report.json` 至少包含 `draftId`、`revision`、Compiler 版本、源文件 hash、输出文件 hash、warning/error、每个 beat 的源分数、导出值、误差和是否阻断。最终发布使用目录级原子替换，不能直接覆盖工作草稿。

推荐命令语义：

```text
/charter save       # 保存 .rmcd，不生成正式发布物
/charter preview    # 编译到临时目录并加载预览，不发布
/charter compile    # 编译并生成带报告的 runtime 产物
/charter publish    # compile --strict + reimport verify + 原子发布
```

### 7.5 `.rmcd` 草稿格式

`.rmcd`（RhythMC Chart Draft）是谱师编辑过程的权威文件，不是运行时谱面。它必须记录足够的信息，使 Compiler 能够重建最终 `.rmcc`，并使编辑器能够恢复工作现场。

推荐采用 ZIP 容器格式，扩展名为 `.rmcd`，内部文件固定如下：

```text
song.rmcd
├── draft.json              # 草稿清单、版本、精确模型和当前编辑状态
├── operations.ndjson       # 不可变操作日志，一行一个操作
├── snapshots/000000.json  # 周期性完整快照，便于快速恢复
├── snapshots/000001.json
├── source/manifest.yml     # 导入时的原始 manifest 快照（从零新建的谱面可无）
├── source/world.rmcc       # 导入时的原始难度快照，可选（从零新建的谱面可无）
└── assets/index.json       # 本地音频 hash、长度、解码信息，不内嵌音频
```

`draft.json` 最小结构：

```json
{
  "format": "rmcd",
  "formatVersion": 1,
  "draftId": "uuid",
  "songFolder": "example_song",
  "activeLevel": "world",
  "revision": 1842,
  "baseSnapshot": 12,
  "model": {
    "meta": {
      "offsetMs": "0",
      "bpms": [{"beat": "0", "bpm": "120.000"}]
    },
    "tracks": [],
    "effects": []
  },
  "editorState": {
    "cursorBeat": "32/3",
    "snap": 8,
    "selectedIds": ["note-42"],
    "loop": {"a": "32", "b": "40"},
    "tool": "TAP",
    "camera": {"x": "0", "y": "2", "z": "0", "yaw": "0", "pitch": "0"}
  },
  "compiler": {
    "target": "rmcc-v1",
    "exportScale": 18,
    "rounding": "REJECT"
  }
}
```

草稿格式的硬规则：

- 所有 beat 均使用规范化字符串分数；整数用 `"12"`，非整数用 `"n/d"`，禁止 JSON number beat。
- `pos` / `scale` / `rotation` / `bpm` 等十进制量用字符串保存（`BigDecimal` 语义），禁止二进制浮点进入草稿；`holdGroup` / `noteType` / easing id 等整数用 JSON number。
- 所有实体拥有稳定 `id`，删除后不复用；操作日志通过 ID 引用实体而不是数组下标。
- `operations.ndjson` 是追加写入文件，每条操作包含 `seq`、`timestamp`、`actor`、`command`、`payload`、`beforeHash`、`afterHash`。
- `timestamp` 只用于审计和恢复排序，不参与谱面语义；谱面语义由操作顺序和 payload 决定。
- 快照每 256 条操作或文件超过配置阈值生成一次；恢复时加载最近快照，再顺序重放后续日志。
- 操作日志不保存音频二进制，只保存 `audioAssetId`、hash、解码器和时长信息。

示例操作：

```ndjson
{"seq":41,"command":"NOTE_CREATE","payload":{"id":"note-42","level":"world","trackId":"track-0","noteType":0,"beat":"32/3","pos":["0","1","0"],"scale":["1","1","1"],"rotation":["0","0","0"],"holdGroup":-1},"beforeHash":"...","afterHash":"..."}
{"seq":42,"command":"NOTE_SET_BEAT","payload":{"id":"note-42","from":"32/3","to":"11"},"beforeHash":"...","afterHash":"..."}
```

### 7.6 操作分类

Compiler 不直接依赖玩家点击事件，而只依赖规范化后的领域操作：

| 类别 | 操作 |
|------|------|
| 谱面 | `CHART_CREATE`、`CHART_IMPORT`、`CHART_SET_META`、`LEVEL_CREATE`、`LEVEL_DELETE` |
| 时间 | `BPM_ADD`、`BPM_SET`、`BPM_REMOVE`、`OFFSET_SET`、`CURSOR_SET` |
| 轨道 | `TRACK_CREATE`、`TRACK_DELETE`、`TRACK_SET_ID`、`NUM_EVENT_ADD/SET/REMOVE` |
| 音符 | `NOTE_CREATE`、`NOTE_SET_BEAT`、`NOTE_SET_TRANSFORM`、`NOTE_DELETE`、`NOTE_RETYPE` |
| 长押 | `HOLD_GROUP_CREATE`、`HOLD_GROUP_APPEND`、`HOLD_GROUP_DETACH` |
| 效果 | `EFFECT_CREATE`、`EFFECT_SET_BEAT`、`EFFECT_SET_PROPERTIES`、`EFFECT_DELETE` |
| 编辑 | `SELECTION_SET`、`BOOKMARK_SET`、`LOOP_SET`、`SNAP_SET` |

Undo/Redo 不删除日志，而是产生新的反向操作；这样任何一次编辑都可审计、重放和复现。`beforeHash/afterHash` 用于检测日志损坏或并发写入。`snapshots/` 只用于崩溃恢复与快速重放起点，不作为撤销机制。

## 8. 编辑会话与项目结构

### 8.1 谱面项目目录（服务器侧）

```
plugins/RhythMC-Charter-V2/
├── charts/                      # 谱面工作区；published/ 子目录可整体拷贝给 Reborn 的 charts 目录
│   └── <song_folder>/
│       ├── <draftId>.rmcd        # 唯一权威草稿；beat 使用 "n/d" 字符串
│       ├── _editor.yml           # 轻量索引/锁/音频提示；不作为精确模型
│       ├── build/                # Compiler 每次编译的产物与 compile-report（可清空）
│       └── published/            # /publish 原子写入的最终谱（manifest.yml + *.rmcc，§13.3 i18n 合规）
├── arenas/                      # arena 引用（symlink/拷贝 Reborn 的 arenas 或自带）
└── config.yml                   # 世界分区、自动保存间隔、性能预算
```

`_editor.yml` 内容（提案）：`editorVersion`、songFolder↔draftId 索引、文件锁、`audioHint`（建议音频文件名，供 mod 匹配本地音频库）、`lastDifficulty`。书签与循环标记以 `.rmcd` 操作日志为准，此处不重复存放。**禁止**写入任何会被游戏消费的语义字段。

### 8.2 会话状态机

```
IDLE ──/new(向导)──▶ SETUP ──完成──▶ EDIT ◄─┐
                     │                     │ /edit
                     ▼                     │
                   EDIT ──/play──▶ PREVIEW ─┤（Transport，见 §10）
                     │                     │
                    ├──/test──▶ TEST（试玩，§15）
                      │
                       └──/save /preview /compile /publish──▶ 持久化、预览与发布（仍停留 EDIT）
任何状态 ◄──崩溃恢复── 启动时扫描 dirty 会话文件 → 提示恢复
```

- 每个会话分配一个**工作区**（workspace）：自建专用世界（如 `charter_workspace`，Bukkit WorldCreator 创建；可选交由 Multiverse 接管）内按 256m 网格分配的区块区域，粘贴 `initialArena` 的 `.schem`（WorldEdit 格式，同 Reborn arena），谱师传送到玩家判定点。
- 会话与玩家 1:1；同一谱面文件夹同时只允许一个会话打开（文件锁标记于 `_editor.yml`，会话关闭时清除）。

### 8.3 自动保存与崩溃恢复

- 自动保存：dirty 后每 60s 一次 + Transport STOP 时 + `/save`；写 `.rmcd` 临时文件后原子替换。编译产物另写 `build/`，不覆盖精确草稿。
- 崩溃恢复：自动保存总是先写 `*.tmp` 再原子重命名；启动时发现带 dirty 标记的 `_editor.yml` → 下次该谱师进服提示「恢复未保存的编辑？」。

---

## 9. 游戏内放置与可视化

### 9.1 坐标模型

- **判定平面**：经过玩家判定点、法向 = 玩家→ZFar 方向（默认轨道 Z 轴向）的平面。放置时射线与该平面求交得到候选空间坐标；再依据当前轨道的缩放、旋转和平移求音符局部 `pos`。这是编辑器的放置辅助，不宣称覆盖所有动态轨道状态；正式预览必须以 Reborn 的实际组合公式验证。
- **编辑游标**：会话级状态 `cursorBeat`。移动方式：播放头回停、`/seek`、热栏步进（±1 snap / ±1 拍 / ±1 小节）、GUI 输入、点击时间轴 HUD。
- **beat 与 pos 解耦**：准星只回答「放在哪」，游标只回答「放在何时」。这是对谱师最友好的一致性模型，也天然支持纯键盘流（游标步进 + 上次放置坐标复用）。

### 9.2 热栏工具箱（固定布局，不随快捷栏保存污染背包）

| 槽位 | 物品（示意） | 功能 |
|------|------|------|
| 1 | 音符放置器（图标=当前类型） | 左键=按当前类型在 ghost 处放置；右键=循环切换 TAP/LOOK/HOLD/DODGE |
| 2 | 删除器 | 左键=删除准星指向音符 |
| 3 | 选择/移动器 | 左键=选中（高亮）；再次左键=拖动（跟随准星+游标）；右键=属性面板 |
| 4 | 属性魔棒 | 对选中音符滚轮/sneak+左键 调整 scale、rotation（LOOK 朝向手柄） |
| 5 | snap 旋转器 | 循环 1/1→1/2→1/3→…→1/16→off |
| 6 | Transport：播放/暂停 | |
| 7 | Transport：seek 回游标 / 上一小节 / 下一小节 | 左键回游标，右键按 snap 步进 |
| 8 | A-B 循环 | 左键设 A（当前 beat），右键设 B，再按清除 |
| 9 | 主菜单 GUI | 打开 §12 面板 |

- 选中音符的属性亦可全部 GUI 化编辑（复用 Reborn `gui-menu-framework.md` 的菜单模式）；热栏是快路径，GUI 是全路径。

### 9.3 各类型的编辑流

| 类型 | 放置 | 后续调整 |
|------|------|----------|
| TAP | ghost → 左键 | scale/rotation（外观/碰撞大小） |
| LOOK | 同 TAP；放置器自动带「朝向手柄」显示 | 属性魔棒调整朝向（rotation 即视角目标，语义以 Reborn `look.md` 判定实现为准） |
| HOLD | 第一次左键 = START；随后左键在后续 beat 处追加同 `holdGroup` 的节点；右键结束该组 | 拖动节点改变覆盖区间；组内节点强制同轨道、按 beat 严格递增（违反即 Lint error） |
| DODGE | ghost（初始 scale 建议 1.5×） | scale 直接决定身体碰撞体积，编辑态按 Reborn `TOLERATE_DODGE=0.1` 语义渲染碰撞箱轮廓线（`showBoundingBox` 类视觉） |

- `holdGroup` ID 自动分配（全局递增，避免谱师心智负担）；删除组内某节点自动重排 START/MIDDLE/END（与 Reborn 长押识别规则一致：同轨道、顺序、类型=2）。

### 9.4 渲染（PreviewRenderer）

**一致性是红线**：轨道姿态计算必须与 Reborn 运行时同构——`NumEvent(startBeat,endBeat,startValue,endValue,easingType)`，easing ID 0–33 对照表同 `animation.md`。Reborn 实际先由 `TrackObject` 计算当前轨道状态，再由 `NoteObject` 计算距离、缩放、旋转和世界坐标；实现策略见 §18 开放问题 2。

两种渲染态：

1. **编辑态（当前游标预览姿态）**：每个音符按当前 `cursorBeat` 计算轨道状态、speed distance 和可见位置；不能把它描述为“所有音符的命中时刻静态姿态”，因为 Reborn 的实际渲染位置由当前 beat 与音符预计算距离共同决定。选中音符可以额外显示其命中时刻的辅助标记，但那不是运行时实体位置。附加编辑视觉：
   - ghost（放置预览，半透明/发光边框）；
   - 选中高亮（Glow、粒子框）；
   - HOLD 覆盖区间：START→END 间画链式光带（TextDisplay 或粒子），并标注区间毫秒数；
   - 时间衰减着色：按「距游标的拍距」渐变（近=亮，远=暗），一眼看出时间结构。
2. **播放态（飞行动画）**：与游戏一致地逐 tick 更新当前 beat。Reborn 的音符初始距离在构造时按音符 beat 计算，当前位置为 `noteDistance - currentTrackDistance + posZ * currentTrackZScale`；ZNear/ZFar 只决定可见/隐藏，不是通用的“从 ZFar 直线移动”模型。

渲染窗口控制（防大谱面爆实体）：编辑态只渲染 `cursorBeat ± 8 拍`（可配）内的音符 + 通过 HUD 密度图感知全局；播放态渲染可见域内的音符。实体用对象池复用（池上限见 §16）。

### 9.5 HUD 时间轴

- **actionbar**（常驻）：`#<小节>.<拍> | snap 1/4 | BPM 120 | ●dirty | ▶播放中`。
- **bossbar**（播放/游标）：进度条=全曲 beat 进度，游标段名=`第 N 小节`。
- **侧栏 scoreboard**（可选开）：密度图——每小节一格，格内按音符数染色；A/B 循环区间显式标出。波形图留待 mod 能力位（未来，见 §11.2 能力位 4）。

---

## 10. 播放引擎（Transport）

### 10.1 时间源分层

| 层 | 时钟 | 用途 |
|----|------|------|
| 视觉主时钟 | 服务端 tick（50ms/tick），`beat` 按 BeatClock 每 tick 推进 | 渲染动画、HUD——**与真实游玩同粒度，这是特性**（预览诚实反映 20tps 体验） |
| 音频时钟 | mod 本地音频引擎 | 会话存在即存在（Mod 为会话前置条件）；是「音频位置」的权威。音频文件缺失时进入 §11.3 定义的静音审计态：对账暂停，HUD 常驻告警 |
| 对账 | 每 10 tick 比对 `BeatClock.ms(插件beat)` vs `mod STATE.positionMs` | 偏差 > 60ms（>1 tick 且 < PERFECT 窗）→ 以插件 beat 为准重 seek mod |

### 10.2 指令集（Transport API）

`play(fromBeat?)` / `pause()` / `seek(beat)` / `setSpeed(0.5|0.75|1.0)` / `setLoop(aBeat, bBeat|null)` / `stop()`。

- **seek（断点播放的核心）**：插件 beat → ms（含 offset、BPM 分段）→ 下发 mod `TRANSPORT_SEEK`；视觉时钟直接跳变；这是「从任意拍继续播」的服务端权威实现。
- **A-B 循环**：插件按 beat 区间循环驱动视觉；mod 收到换算后的 `[aMs, bMs)` 循环段同步循环；两端各自到达端点即回卷，对账机制纠正累积漂移。
- **变速**：视觉时钟缩放；mod 端 V1 用**重采样变速（音调随变）**——诚实标注，不做假承诺；不变调变速（SoundTouch 类）列为 V2 可选依赖（新增依赖需用户确认）。

### 10.3 Mod-only 工况

- Charter 会话建立时必须完成 Mod `HELLO`、协议版本和音频能力校验；失败则不创建编辑会话。
- 音频时钟由 Mod 本地音频引擎提供，插件时钟只负责把谱面 beat、世界渲染和游戏内试玩保持在同一 Transport 状态。
- 不实现节拍器替代、静音彩排或“无 Mod 仍可编辑”的分支；这样可以删除大量降级逻辑，减少未测试状态组合。

---

## 11. Mod 契约提案：`rhythmc:charter_audio`

> **治理声明**：本通道为设计提案。依据本仓 AGENTS.md 跨仓规则，新通道落地前需用户确认；确认后在 `RhythMC-Charter-V2` 与 `RhythMC-CharterMod`（新仓）各自 `.agent/CONTRACTS.md`（或等价契约文档）登记，并与 `rhythmc:chart_preview` 契约文档**互不重叠**地并存。编码风格刻意与 chart_preview 对齐（大端 `int` opcode 前缀；字符串 = `int byteLength + UTF-8`；分块 = `int byteLength + raw bytes`；禁用 Java `writeUTF` / MC varint），降低双契约维护心智。

### 11.1 生命周期

```
玩家进入开启会话的服务器
  → mod 发 HELLO(protocol, modVersion, capabilities)
  → 插件校验 protocol → HELLO_ACK(ok, serverVersion)
  → 会话内 Transport 指令流（PLAY/PAUSE/SEEK/SET_LOOP/SET_SPEED/STOP）
  → mod 周期 STATE 上报（playing, positionMs, speed）→ 插件对账
玩家退出 / 会话关闭 → 插件发 STOP → 双方静默
```

信任模型与 chart_preview 相同：发送者=在线 Bukkit `Player`，无 token；但**指令仅对开启 Charter 会话且 HELLO_ACK.ok 的玩家生效**（mod 无法触发无会话玩家的事务）。

### 11.2 能力协商（capabilities bitfield）

| bit | 能力 | V1 |
|-----|------|----|
| 0 | seek | ✓ |
| 1 | A-B loop | ✓ |
| 2 | speed（重采样变调） | ✓ |
| 3 | speed（不变调） | 预留 |
| 4 | waveform 数据（供未来 HUD 波形） | 预留 |

### 11.3 音频来源与匹配

- mod 本地音频库：`.minecraft/rhythmc-audio/<song_folder_name>/audio.(ogg|mp3|flac|wav|...)`。
- 会话打开谱面时，插件经 `CHART_META` 下发 `{songFolder, songName, audioHint}`（audioHint 来自 `_editor.yml`）；mod 按目录名 → audioHint → 歌名模糊 依次匹配；匹配失败则上报 `CHART_STATUS(ok=0)`，会话进入**静音审计态**：编辑与世界可视化照常进行，Transport 音频指令空转，HUD 常驻缺音频告警，有声彩排被阻止，直到谱师放置正确音频文件后重新匹配。这与已删除的“无 Mod 降级”是两回事：Mod 通道本身必须在线。
- 解码链复用 ChartMaker 的成熟经验：Java Sound + 显式 SPI 解码器（vorbis/mp3/flac/wav/aiff/au），不单赌 `AudioSystem` 服务发现。

### 11.4 Opcode 表（概要；载荷布局见附录 B）

| 方向 | opcode | 名称 | 语义 |
|------|--------|------|------|
| M→P | 1 | HELLO | 握手 + 协议版本 + 能力位 |
| P→M | 2 | CHART_META | 当前谱面标识 + audioHint |
| P→M | 3 | TRANSPORT_PLAY | 从 beat/ms 起播（含 speed） |
| P→M | 4 | TRANSPORT_PAUSE | 暂停（保位置） |
| P→M | 5 | TRANSPORT_SEEK | 跳到 ms |
| P→M | 6 | TRANSPORT_STOP | 停止 |
| P→M | 7 | SET_LOOP | 设置/清除 A-B 毫秒区间 |
| P→M | 8 | SET_SPEED | 0.5/0.75/1.0 |
| P→M | 9 | PING | RTT 测量 |
| P→M | 101 | HELLO_ACK | 协议校验结果 + 服务端版本 |
| M→P | 102 | CHART_STATUS | 音频匹配结果（ok/静音原因） |
| M→P | 103 | STATE | playing / positionMs / speed 周期上报 |
| M→P | 104 | PONG | RTT 回显 |
| 双向 | 105 | ERROR | 机器可读错误码 + 人读消息（任一方向均可发送） |

### 11.5 mod 端按键绑定（增强项）

播放/暂停、A-B 设点、±1 小节 seek 绑定客户端按键——纯客户端快捷方式，等价于把对应指令走通道发回插件（避免双时钟源分叉：**mod 按键也必须经插件 Transport 下发**，不得本地直改音频）。

---

<a id="12-命令-gui-与-hud"></a>

## 12. 命令、GUI 与 HUD

- 主命令 `/charter`（别名 `/cv2`），权限根 `rhythmc.charter.*`（`session` / `transport` / `admin` 子键）；全表见附录 C。
- GUI 面板（chest 菜单，模式对齐 Reborn `gui-menu-framework.md`）：
  1. 建谱向导（manifest 字段表单：name/composer/icon/alias/length/... + BPM/offset 初值 + 难度选择）；
  2. BPM 事件编辑器（bpms 表格增删改）；
  3. 轨道编辑器（按 track 列出十类 NumEvent 通道，事件增删改 + easing 选择器按 0–33 对照表命名展示）；
  4. 效果编辑器（17 种 effectType 模板化 properties 表单；文本字段旁提供 locale 引用切换——写 `properties.locale`，内联文本留回退，遵守 i18n 规范）；
  5. 会话/发布面板（lint 结果、compile/publish、快照）。
- 所有玩家向文本走插件的 i18n 机制（插件自身 UI 文案与谱面 i18n 是两套体系，勿混淆：前者走插件语言包，后者走谱面内嵌块）。

---

## 13. 撤销、持久化与导出

### 13.1 Undo/Redo

- 模型：**操作日志 + 反向操作**（见 §7.6）——undo/redo 不删除 `operations.ndjson`，而是追加语义相反的领域操作（如 `NOTE_SET_BEAT` 的 from/to 互换、`NOTE_CREATE` 对应 `NOTE_DELETE`）；任何一次编辑都可审计、重放和复现。`snapshots/` 周期快照只用于崩溃恢复与快速重放起点。
- 成本：单次 undo/redo = 一次领域操作应用 + 日志追加，与正常编辑同量级；`beforeHash/afterHash` 链保证重放一致性，断裂即报错并提示从最近快照恢复。

### 13.2 保存 / 导出

- `/save`：保存精确 `.rmcd` 草稿（原子替换），不生成发布物。
- `/preview`：执行 Compiler 到临时目录，并按编译产物（含 double 取整语义）加载进当前 workspace 回放，使彩排所见即发布所得；编辑态渲染仍使用精确模型（见 §9.4）。
- `/compile`：执行 §7.4 的 Compiler 管线，产物写入 `build/<draftId>/<revision>/` 并生成 `compile-report.json`，不改动草稿。
- `/publish [--strict]`：在 compile 基础上执行 §7.3 的发布闸门、Lint 与 ReimportVerifier；`--strict` 将 warning 也视为阻断；通过后原子写入 `published/`。发布产物**直接就是 Reborn 可装载的谱面文件夹**（拷贝到 Reborn charts 目录即可游玩），无需转换步骤。manifest 补全规则：`length` 默认取末音符 ms + 2s（可在向导中改，Lint 校验偏差）；`respack_sha1` 透传导入源，留空则 Lint warn（Reborn 侧资源包匹配行为以实测为准）；arena schematic 需随谱面一并部署到 Reborn。
- 未知字段保留：`.rmcd` 的源快照和 Charter 自己的领域模型可以透传未知键；但 Reborn 的反序列化器映射到固定 record 后会丢弃未知键，因此不能承诺经过 Reborn 再写回仍保留它们。跨工具 round-trip 只对 Charter 直接导入/导出路径成立。

### 13.3 i18n 合规写法（硬规则）

- `name` / `composer` / `charters` 永不写入 i18n 块；
- 编辑器产生的可翻译文本（效果文本、难度注释）一律：内联原文 + `properties.locale` 引用 / `meta.comments` 保留键；`_` 前缀键只出现在 i18n 块内且只由工具写入；
- `_editor.yml` 是**文件级**工具元数据（非 i18n 块内 `_` 键），靠「Reborn 加载器只读固定文件名」实现隔离——两者机制不同，文档中显式区分，避免后人混淆；
- `default_locale` 默认取谱师客户端语言，可改。

---

## 14. Lint 校验

| 级别 | 规则 |
|------|------|
| error | `bpms` beat 非严格递增 / 首事件 beat≠0 / bpm ≤ 0 |
| error | 精度损失破坏顺序不变性：重读后 beat 排序、BPM 段、holdGroup 或效果触发顺序与精确模型不一致 |
| warn | 纯数值舍入误差（`PRECISION_LOSS` 数值项：源分数→导出值→重读值的 epsilon 漂移，明细见 `compile-report.json`；`--strict` 下升级为阻断） |
| error | `holdGroup` 组内：跨轨道、类型≠2、beat 非严格递增 |
| error | HOLD 组区间与其他音符判定冲突（同轨同 beat 的 TAP/LOOK/HOLD-START 完全重叠） |
| error | 音符 beat < 0；`initialArena` 引用的 arena 在 workspace arena 注册表中不存在（发布时需随谱面一并部署到 Reborn） |
| error | 效果 `properties.locale` 引用的键在任何语言块中不存在（对齐 Reborn `rhymc.dev` 校验行为） |
| error | 音符数 / 轨道数 / 事件数超过 Reborn 反序列化器已知上限（以实测为准） |
| warn | 同轨可交互音符间隔 < 125ms（建议线，见 §6.4 表；不阻断，最终由试玩裁决） |
| error | 单轨道同类 NumEvent 区间重叠（Reborn `TrackDeserializer` 直接抛 `IllegalArgumentException`，谱面无法加载） |
| warn | 单轨道 `speedEvents` 留空洞（空洞段速度回退默认值的语义需谱师确认） |
| warn | DODGE 音符无配套视觉效果（孤立躲避物，体验存疑） |
| warn | manifest `length` 与谱面末音符 ms 偏差 > 5s |

---

## 15. 试玩联动

- **TEST 模式（后置适配项）**：不能仅靠复用 PreviewRenderer 实现。Reborn 的真实流程还包括 `GameInstance` 生命周期、预加载、资源包就绪、`TimingManager`、输入包/姿态样本和 `JudgeManager`。V1 先提供有声彩排；完整判定试玩必须做独立的测试适配层，并以 Reborn 真机试玩作为验收标准。
- **真机试玩（唯一正式验收路径）**：`/publish` 后把 `published/` 目录拷入 Reborn 的 charts 目录，用 Reborn 本体游玩。正式播放依赖 Reborn 的资源包音效 key（manifest 的 `respack_sha1` 与服务端资源包配置）；CharterMod 的本地音频只服务制谱彩排，不替代正式游戏音频。设计上**不**在 Charter 插件里复刻 Reborn 的结算、统计和经济系统。
- 与 ChartMaker/Preview 的互通（可选、后置）：若用户未来要求，Charter V2 可实现 chart_preview 的**服务端接收端语义子集**（把当前会话谱面按 CHUNK 协议推给 Preview 插件风格的回放）——此为跨仓契约工作，动工前必须按 §2.3 流程征得同意。

---

## 16. 性能预算

| 项 | 预算 | 手段 |
|----|------|------|
| 每会话 ItemDisplay 常驻 | ≤ 400 | 编辑态窗口渲染（cursorBeat ± 8 拍）+ 对象池 |
| 播放态同屏音符 | ≤ 128 | ZNear/ZFar 可见域裁剪（本就是游戏语义） |
| 每 tick 渲染计算 | 单会话 ≤ 1ms | 姿态计算 O(可见音符 + 活跃事件)；事件表预排序 + 指针推进 |
| Undo/Redo（反向操作追加） | ≤ 10ms / 次 | 单次领域操作应用 + 日志追加，与正常编辑同量级 |
| 自动保存 | 后台线程写盘 | 主线程仅序列化（或整步异步，序列化物为不可变副本） |
| 单服务器并发会话 | 8（默认，可配） | 256m 空间分区隔离渲染与射线范围 |

---

## 17. 里程碑

| 里程碑 | 内容 | 验收 |
|--------|------|------|
| M0 骨架 | 会话管理、BeatClock、`.rmcd` 草稿 IO 与 Compiler 管线雏形（§7.4） | 单测：BeatClock 变速换算、`.rmcd` 写入/操作日志重放、Charter 导入→导出 round-trip 不丢键（含未知键透传）；手工建谱/存谱 |
| M1 放置 | workspace + arena 粘贴、判定平面放置、ghost、四类型 CRUD、游标预览渲染 | 游戏内放满一小节谱面，视觉与 beat 对齐 |
| M2 Mod 通道 | charter_audio 契约落地（**先过用户确认关**）、CharterMod MVP（seek/loop/speed） | 握手/能力协商/STATE 对账可用，断点指令生效 |
| M3 播放 | Mod 音频 Transport + HUD 时间轴 + 播放态动画 | 有声彩排、暂停/seek/A-B 循环、对账偏差 < 60ms |
| M4 编辑完整化 | undo/redo、热栏全工具、BPM 事件/轨道/效果 GUI 编辑器 | 谱师不碰命令行完成一整段含变速谱 |
| M5 质量关 | Lint、publish --strict、i18n 合规写、自动保存/崩溃恢复 | 发布谱在 Reborn 上完整可玩；产物通过 Reborn 反序列化语义校验（排序/holdGroup/BPM 段/效果顺序） |
| M6 试玩 | 判定试玩适配层（后置适配项，接入 Reborn 时钟/输入/判定生命周期） | 以 Reborn 真机试玩为最终验收；适配层内游标起播可给出判定反馈 |

依赖排序：M0（草稿与 Compiler）→ M1（游戏内编辑）→ M2（Mod 通道）→ M3（有声彩排）→ M4（GUI 完整化）→ M5（质量关）→ M6（试玩）；所有正式验收均在 Plugin + CharterMod 工况下执行。

---

## 18. 风险与开放问题

1. **通道治理**：`rhythmc:charter_audio` 是新通道。虽然用途与 chart_preview 正交，仍需用户在 M2 前明确批准，并同步登记两仓契约文档（本设计稿仅提案）。
2. **渲染公式一致性**：Charter 插件复刻 Reborn 轨道姿态数学有漂移风险。方案 A：复制公式 + 固定输入输出样例的一致性测试（快、松耦合）；方案 B：抽 `rhythmc-chart-math` 公共 Java 库三仓共用（慢、要动 Reborn 构建与发布流程）。**V1 选 A**，B 待 Reborn 侧有意愿再议。
3. **拍号**：V1 固定 4/4；`bpms` 表是否扩展 `timeSig` 事件需 Reborn 格式先支持（格式变更=跨仓契约工作，问用户）。
4. **manifest 未知字段**：Reborn 把 manifest 与 `.rmcc` 反序列化为固定 record（`SongManifest` / `Meta` / `Note` 等），未知键不会进入运行时——是否静默忽略可实测，但「保留」绝无可能（已核实源码）。因此 Charter 的工具元数据一律走独立 `_editor.yml` 与 `.rmcd`，禁止向 manifest 添加 `_` 前缀键。
5. **音频变速变调**：V1 重采样变调是诚实折衷；不变调变速引新依赖（SoundTouch/java sound 处理链），M3 前单独问用户。
6. **Mod 是硬依赖**：这是明确的产品取舍。Mod 协议、音频文件匹配和版本检查属于启动门，不再维护无 Mod 兼容分支。
7. **大谱面性能**：千音符级谱面的窗口渲染 + 操作日志重放成本需 M1/M4 实测；预算见 §16，超限走性能优化路径。
8. **多难度编辑**：V1 单会话单难度；跨难度切换=关会话再开（保留 `_editor.lastDifficulty` 记忆）。

---

## 附录 A：BeatClock 换算

```
// ms(beat)：分段积分。bpms 按 beat 升序，最后一段延伸到 +∞。
// bpm 为 BigDecimal；除法一律 scale=12、HALF_EVEN；beat 比较一律精确比较。
BigDecimal ms(BeatFraction beat):
    t = Decimal(offsetMs)   // offset 为整数毫秒
    for i in 0 .. n-1:
        segStart = bpms[i].beat
        segEnd   = (i+1 < n) ? bpms[i+1].beat : +INF
        if beat <= segStart: break
        t += Decimal(min(beat, segEnd) - segStart) * 60000 / bpms[i].bpm
    return t

// beat(ms)：先减 offset，再逐段累减，余数按当前段 bpm 换算。
// 输入 ms 为 long（tick/音频边界精度）；输出为该 scale-12 小数的精确分数形式。
BeatFraction beat(long ms):
    r = Decimal(ms - offsetMs)
    for i in 0 .. n-1:
        segBeats = segEnd_i - segStart_i            // BeatFraction，精确
        segMs = Decimal(segBeats) * 60000 / bpm_i   // BigDecimal, scale=12
        if r < segMs or last segment: return segStart_i + Fraction(r * bpm_i / 60000)
        r -= segMs

// snap 量化
BeatFraction quantize(BeatFraction beat, int snap): return round(beat * snap) / snap
// 小节号/拍内位置（4/4）
int bar(BeatFraction beat): return floor(beat / 4)
BeatFraction inBar(BeatFraction beat): return beat - bar(beat) * 4
```

边界：`bpms[0].beat` 必须 = 0（Lint error）；`offset` 只参与 ms 域，不进入 beat 域。

## 附录 B：charter_audio opcode 载荷表

帧 = `大端 int opcode` + 载荷；字符串 = `int byteLength + UTF-8 bytes`；数值一律大端固定宽度。

| opcode | 名称 | 载荷 |
|--------|------|------|
| 1 | HELLO | `int protocol, string modVersion, int capabilities` |
| 2 | CHART_META | `string songFolder, string songName, string audioHint` |
| 3 | TRANSPORT_PLAY | `double fromMs, float speed` |
| 4 | TRANSPORT_PAUSE | — |
| 5 | TRANSPORT_SEEK | `double toMs` |
| 6 | TRANSPORT_STOP | — |
| 7 | SET_LOOP | `double aMs, double bMs`（bMs<0 = 清除） |
| 8 | SET_SPEED | `float speed` |
| 9 | PING | `long clientNonce` |
| 101 | HELLO_ACK | `byte ok, string serverVersion` |
| 102 | CHART_STATUS | `byte ok, string reason` |
| 103 | STATE | `byte playing, double positionMs, float speed` |
| 104 | PONG | `long clientNonce` |
| 105 | ERROR | `int code, string message` |

版本协商：`protocol` 不匹配 → HELLO_ACK.ok=0 + ERROR(code=PROTOCOL) → 插件拒绝创建或恢复制谱会话。

<a id="附录-c命令全表"></a>

## 附录 C：命令全表（V1）

```
/charter new <songFolder>                 # 向导建谱（GUI）
/charter open <songFolder> [difficulty]   # 打开（文件锁校验）
/charter close                            # 关闭会话（自动保存）
/charter save                             # 保存 .rmcd 草稿
/charter preview                          # 编译到临时目录并加载预览（不发布）
/charter compile                          # 编译生成 build/ 产物与 compile-report
/charter publish [--strict]               # 精度闸门 + reimport 校验 + 原子发布
/charter edit <world|nether|end|void>     # 切换编辑难度
/charter bpm <list|add|remove|set> …      # bpms 事件表维护
/charter snap <1|2|3|4|6|8|12|16|off>
/charter seek <beat|bar:N|snap±N>
/charter play [fromBeat] | pause | stop
/charter loop <aBeat> <bBeat> | loop off
/charter speed <0.5|0.75|1.0>
/charter undo | redo
/charter lint [now]
/charter hud <timeline|density> <on|off>
/charter audio hint <fileName>            # 写 _editor.yml 供 mod 匹配
/charter test [fromBeat]                  # 试玩模式（后置适配项）
/charter admin world|budget …             # 管理员（分区/预算）
```

## 附录 D：Mod-only 启动门

| 检查项 | 失败行为 |
|--------|----------|
| Mod 已连接并完成 `HELLO` | 拒绝创建或恢复 Charter 会话 |
| 协议版本匹配 | 显示升级提示，不进入编辑世界 |
| 音频文件匹配成功且 hash 可验证 | 进入静音审计态：允许编辑与可视化，不允许有声彩排，HUD 常驻告警；谱师放置正确文件后重新匹配 |
| seek / loop / speed 能力已协商 | 不允许进入正式彩排 |
| 音频时钟状态正常 | 暂停 Transport，保留草稿，不继续推进视觉播放 |
| 服务端与 Mod 的 sessionId 一致 | 丢弃控制包，要求重新握手 |

本项目没有“无 Mod 兼容模式”。这不是运行时降级，而是明确的产品前置条件；所有验收、性能指标和谱师体验均以 Plugin + CharterMod 为唯一工况。

---

*本设计稿为 RhythMC-Charter-V2 的第一份总体设计，落地前需按 §2.3 / §18 处理通道与跨仓确认事项。Compiler 与 `.rmcd` 草稿格式必须先于游戏内编辑器实现。*
