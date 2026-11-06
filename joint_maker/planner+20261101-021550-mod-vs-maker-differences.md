# RhythMC-MOD 0.5.1-beta.3 与 RhythMC Maker 定义差异分析

日期：2026-11-01

## 范围与证据

对照客户端 Mod `RhythMC-MOD-0.5.1-beta.3.jar` 的 class 元数据/字节码，以及 Maker 当前 `ChartManifest`、`ChartStorage`、`Rhythmc3Exporter`、`Rhythmc3EffectCodec`、`EffectParameterSchema` 和 `RhythmcMaker`。Mod 未提供源码，本分析中效果字段通过 JAR 字节码和类签名确认；未能从签名直接证明的细节标为待核实。此次不修改、不编译、不测试、不部署。

## 结论摘要

存在分歧，但应区分三层：

1. **基础音符/轨道格式：总体兼容。** TAP/LOOK/HOLD/DODGE 和 10 个 Track 数值通道一致，Maker 的导入/导出会把顶层音符按 `trackId` 转换到 Mod 的 `Track.notes`。Maker 额外的 UUID、音频时间缓存和源坐标属于编辑器内部数据，原则上不应形成 3.0 输出差异。
2. **高级字段与特效目录：不完整兼容。** Maker 缺少 Mod 的音符动画模型和 `JUDGE_FRAME_COLOR`；Mod 的文本展示 `FRAME` 帧动画不是 Maker 当前分支枚举提供的功能。
3. **播放语义：至少有一项明确不一致。** `HIDE_NOTES` 在类型与轨道同时指定时，Mod 按并集隐藏，Maker 预览按交集隐藏；Maker 预览会出现实际 Mod 不会出现的音符可见性。

## 差异矩阵

| 领域 | Mod | Maker | 结论/风险 |
|---|---|---|---|
| 音符类型 | TAP、LOOK、HOLD、DODGE；非法数值映射为 LOOK | 同四类型，以整数存储 | 合法值一致。需确认 Maker 对非法值是否也归一为 LOOK；未确认，不能假定一致。 |
| 音符归属 | `Track.notes` | Chart 顶层 `notes` + 每个 Note 的 `trackId` | 结构不同但转换层可兼容。需维持 Track 与 note 引用完整性；孤立 trackId 是导出风险。 |
| Note 字段 | beat、局部 pos/scale/rotation、holdGroup、动画覆盖 | 上述字段由不同字段承载，另有 UUID、time、source 坐标 | UUID/time/source 属编辑器信息；是否导出由转换负责。动画覆盖字段缺失则会丢玩法/视觉语义。 |
| Track 通道 | speed、XYZ transform/rotate/scale | 同 10 通道 | 结构一致。Mod 旋转按角度转弧度；Maker 播放器也转弧度，方向一致。仍需通过具体曲线边界样本验证插值/默认值完全相同。 |
| 轨道音符动画 | Track.noteAnimation 绑定 preset、起始偏移、持续拍数、开始前隐藏；Note 有 delay/disabled override | 未发现对应领域字段 | 明确缺失。导入后编辑/再导出无法表达完整动画语义；Mod 播放包含这些字段的谱面时，Maker 预览也无法忠实呈现。 |
| 特效类型 | 18 类，含 JUDGE_FRAME_COLOR | Maker Schema 覆盖常用类型，但无 JUDGE_FRAME_COLOR；ACTIONBAR 是 Maker 本地扩展 | Maker 无法以结构化表单编辑判定框颜色；导出兼容性取决于未知事件能否保留及校验是否阻止导出。ACTIONBAR 被明确禁止导出为官方格式。 |
| TextDisplay 创建 | `TEXT_DISPLAY` 读取位置、旋转、缩放、文本、ID、lineWidth、background、opacity、shadow、seeThrough、alignment 等 | Maker Schema 创建字段目前为位置、rotation、scale、text、id；运行时有默认样式并支持部分字段 | 字段/默认值并非一一对齐。Maker 创建的显示可能在背景、透明度、阴影、透视、对齐、行宽上与 Mod 不同。部分项需用完整 Schema 与字节码进一步逐项核实。 |
| TextDisplay 编辑 | `TEXT_DISPLAY_EFFECT` 支持 TEXT、SHADOW、OPACITY、BACKGROUND_COLOR、LINEAR_TRANSFORMATION、GLOWING | Maker 具有同名分支，字段映射基本对应 | 参数种类基本一致；旋转单位需特别注意：Maker 界面注释/编码是弧度，而创建实体旋转是角度，Mod 转换器需要保持这种分支语义。 |
| TextDisplay 帧动画 | `TEXT_DISPLAY_EFFECT` 的 `type=FRAME` 进入单独 `TextDisplayFrameEffect`；带 from/to matrix、opacity、background、snap 等帧插值状态 | Maker Schema 的 type 分支没有 FRAME；预览处理为普通静态文本变换 | 明确缺少帧插值特效。对应谱面会无法在 Maker 中编辑/预览；是否原样透传导出须核对 codec/schema 校验路径。 |
| TextDisplay 跟轨 | Mod 为持久 timed effect，按 TrackObject 更新显示 | Maker 有跟轨/取消跟轨、时长状态与逐帧 pose 更新 | 功能意图一致，但生命周期和插值更新实现不同，存在短时视觉抖动/seek 后状态差异风险；不是仅凭字段名可证明等价。 |
| 隐藏音符 | 命中所选类型 **或** 所选轨道即隐藏；空列表不匹配 | 两列表均有选项时使用类型 **且** 轨道匹配 | 明确语义分歧。只指定一个维度时结果一致；两个维度同时指定时不同。 |
| 判定框颜色 | `JUDGE_FRAME_COLOR` 只读取 `color` 字符串并作用于判定框 | Schema 未注册 | Maker 缺编辑入口和本地预览能力，且无法保证往返保留。 |
| Maker 扩展 | 不认识 ACTIONBAR | Maker 有本地预览/编辑扩展 | 若导出阻断则不会污染 Mod 文件；当前 codec 明确拒绝导出 ACTIONBAR，属安全隔离。 |

## 对实际游玩一致性的影响

### 高优先级：必须修正或明确限制

1. **HIDE_NOTES 并集/交集语义。** 双字段组合会使 Maker 编辑时显示与 Mod 游玩时相反的一部分音符。应以 Mod 的 OR 规则为唯一契约，并覆盖类型/轨道四种空非空组合的预览用例。
2. **NoteAnimation 导入保真。** 这是谱面运行时能力，不是纯 UI 参数。Maker 应保留 Track binding、动画 preset 数据和 Note override；即使首阶段不支持可视编辑，也至少保证导入—保存—导出不丢未知但合法数据，并对预览能力不足明确标识。
3. **TextDisplay FRAME 特效。** 将 `type=FRAME` 作为专门时间化状态过渡处理，而非普通属性更新；至少确认导出 round-trip 保留其 `from/to`、持续时间、snap 及矩阵、opacity、background。

### 中优先级：视觉差异风险

4. **TEXT_DISPLAY 创建字段与默认值对齐。** 对照 Mod 读取字段，将 `seeThrough`、`alignment`、`background`、`lineWidth`、`opacity`、`shadowed` 等逐项建立表格，并明确 Maker 默认值与 Mod 缺省值。否则新建文字在两个运行环境外观可能不同。
5. **JUDGE_FRAME_COLOR。** 增加类型定义、颜色字段校验、预览执行和导入导出支持，否则含该事件谱面无法在 Maker 完整编辑与预览。
6. **Track 曲线边界与同步生命周期。** 对非零起始值、重叠事件、事件端点、seek 前后与 duration 到期场景建立一致性校验，确认 Maker 数值采样及 TextDisplay track sync 与 Mod TrackObject 一致。

### 低优先级/内部差异

7. Maker Note UUID、`time` 缓存和 `sourceX/Y/Z` 不必与 Mod Note 同构。要求 beat 为计时权威值；导出时只序列化 Mod 契约字段，且时间缓存重算或不可参与运行时判定。
8. Maker 顶层 notes 不必为了相同格式而重构，只要转换边界保证每条 note 恰好归属一个 Track，不丢失或重复。

## 需要编译合作方后续核对的字段

- 对照 Mod `EffectFactory` 中各 create 方法，把全部 18 种效果的 `properties` 键、必填/缺省值、单位和数组结构逐项映射到 `EffectParameterSchema`；本轮已确认主要缺项，不将未完成的逐效果对照冒称为“全部字段已核实”。
- 检查 `ChartStorage` 对未识别的特效类型和多余 properties 是否保留；同时检查 `EffectParameterCodec.validateEvent` 是否会阻止含未知类型的导出。
- 用真实含 NoteAnimation、TEXT_DISPLAY_EFFECT type=FRAME、JUDGE_FRAME_COLOR 的 Mod 谱面做 import→保存→export JSON diff；比对关键 properties 是否丢失或变化。
- 选取同一谱面分别在 Maker 预览和 Mod 播放比对：音符类型/轨道、轨道曲线、隐藏组合、TextDisplay 静态样式与帧动画、seek/效果生命周期。

## 本轮边界

只分析并写入规划记录；未修改 Maker 源码，未编译、未运行测试或游戏验证，未部署。
