# HIDE_NOTES 定义与播放差异深度对照

## 范围与边界

本轮为 Planner 静态源码分析；未改 Maker 源码，未编译、未运行测试。对照 Reborn、Chart Converter、Docs 与当前 Maker 播放链。

## 结论摘要

目前 Maker 与 Reborn 的 HIDE_NOTES **并非仅在实体池实现不同，而是在事件数据语义、字段默认值、筛选组合、状态覆盖/恢复方面都有潜在不兼容**。最高优先级问题是：Maker `updateHiddenNotes()` 在 `hidden` 字段缺失时默认 `false`，但 Reborn 的标准 HIDE_NOTES 格式不需要 `hidden` 字段；因此从 Reborn/Converter 导出的隐藏事件导入 Maker 后可能被解释为“取消隐藏”，甚至因空轨道数组导致零个组合键而完全不生效。

## 效果定义与格式

### Reborn 播放器

- `EffectType.HIDE_NOTES` 由 `EffectFactory.createHideNoteEffect()` 解析；`noteTypes` 和 `tracks` 是可选筛选集合。字段缺省时对应空集合。
- `HideNoteEffect.applyEffect()` 不读取 `hidden`，而是把两个筛选集合交给 `EffectManager.setHideNoteState()`。
- `EffectManager.shouldHideNote()` 的实际规则是：`(类型集合非空且包含音符类型) OR (轨道集合非空且包含轨道)`。两个集合都空时显示全部；任一维度匹配即可隐藏。
- 这是当前集合状态的替换，不是与旧集合合并。后续 HIDE_NOTES 事件会整体替换之前类型/轨道过滤状态。
- Reborn `HideNoteEffect` 没有普通到期时间；它作为持久效果持续生效。`forceRemove()` 清空状态。效果管理器清理/结束时会移除活动持久效果。

### Docs 契约

- `docs/charter/effects/hide-notes.md` 描述 `hidden=true/false`、省略类型/轨道代表全部、效果持续至下一个 HIDE_NOTES 覆盖。
- 这与 Reborn 源码并不完全一致：Reborn Factory/HideNoteEffect 不解析 `hidden`，空集合的含义是“该维度不构成过滤条件”，而不是独立的全局 hidden 布尔状态。文档描述不能代替运行时事实。

### Chart Converter 旧 VISIBLE 转换

- `VISIBLE=false` 转成 `HIDE_NOTES` 并写类型列表；无类型列表时补齐 TAP/LOOK/HOLD/DODGE；同时显式写入空 `tracks` 数组。
- `VISIBLE=true` 或缺省时写空 `noteTypes` 与空 `tracks`，按 Reborn 语义表示全部显示。
- 该转换结果没有 `hidden` 字段，依赖 Reborn 的集合过滤语义。

## Maker 特效编辑定义

- `EffectParameterSchema` 给 HIDE_NOTES 提供 `noteTypes`（默认 `[]`）与 `tracks`（默认 `[]`），但 schema 未定义 `hidden` 布尔字段。
- `EffectParameterMigration` 会把数字类型列表迁移为 TAP/LOOK/HOLD/DODGE 名称；因此编辑器 schema 的可见配置本身并未表达标准 Reborn 的 `hidden` 字段。
- Maker 2.0 导入映射 `VISIBLE -> HIDE_NOTES`，复制/归一化旧 `note-types`、轨道字段；要以实际导入 JSON 检查它最终是否写 `hidden` 以及空数组如何保留。

## Maker 播放实现差异

1. `updateHiddenNotes()` 读取 `hidden`；字段缺省时 `hiddenState=false`。而 Maker schema 和 Reborn Converter 标准输出都可不含此字段。结果是：标准事件到 Maker 播放器不满足“缺省即隐藏”的直觉。
2. Maker 分别构造选中类型、轨道集合，再形成二者笛卡尔积 `(track,type)`：类型与轨道都必须匹配才隐藏（AND）。Reborn 使用 OR。两边字段同时指定时视觉结果不同。
3. Maker 的缺省 `noteTypes`/`tracks` 会枚举谱面中现存类型/轨道；显式空数组则集合为空，笛卡尔积为空，不会生成隐藏键。Reborn Converter 对 VISIBLE=false 正是发出非空类型+空 tracks；这在 Maker 里会导致 0 个键，和 Reborn “按类型隐藏”直接相反。
4. Maker `hidden=false` 只移除本次生成的键，不是 Reborn 的“把当前过滤状态替换为空”语义；Reborn 每个事件都整体设置新集合，空集合自然显示全部。
5. Maker 触发事件时调用 `applyVisualState()`：遍历当前显示实体，隐藏的实体回收至对象池；之后的预加载路径检查 `shouldHide()`，跳过隐藏音符。恢复可见时扫描未来音符并重新 acquire。Reborn 在每个 `NoteObject.update()` 查询当前效果状态，隐藏时不 acquire/更新可见实体但仍持续更新运动位置，恢复后再创建/更新显示对象。
6. Maker seek 会清除效果会话状态并按时间重放前置效果来重建隐藏状态；Reborn 当前源码所见的效果队列是前向激活模型，未在本分析中确认有等价任意 seek 回放能力。此处不能把 Maker 的 seek 重建机制误认为两端一致。
7. 隐藏不应改变判定/命中时序。Maker 的命中时间索引与隐藏显示管理分离；Reborn NoteObject 的视觉分支不删除 RawNote 或判定流程对象。此项符合跨端目标。

## 具体兼容例子

- Reborn/Converter 输出 `noteTypes=[TAP,LOOK,HOLD,DODGE], tracks=[]`：Reborn 按类型隐藏全部这些类型；Maker 的轨道集合为空，笛卡尔积为空，且 `hidden` 缺失会被当成 false，隐藏状态不成立。
- 只选 `noteTypes=[HOLD]`、省略 tracks：Reborn 隐藏所有轨道 HOLD；Maker 只有在字段缺失时按谱面枚举所有轨道且同时 `hidden=true` 才能实现；若 UI 存储空数组则不会隐藏。
- 同时选 `noteTypes=[HOLD]`、`tracks=[1]`：Reborn 隐藏所有 HOLD 以及轨道 1 的所有类型；Maker 只隐藏轨道 1 上 HOLD。
- 下一事件仅要求显示轨道 1：Reborn 的语义是替换当前过滤集合为 tracks=[1]，这仍会隐藏轨道 1；Maker 必须依赖 `hidden=false` 去删局部键，事件字段语义无法直接一一映射。

## 深层原因判断

问题根源是两种不同的状态模型被同名事件混用：Reborn 是“当前过滤器快照（noteTypes 与 tracks 两个独立 OR 条件）”；Maker 是“按轨道×类型的隐藏键集合，再用布尔 hidden 增删键”。此外 Docs 把状态动作 `hidden` 与过滤器字段并列描述，但 Reborn 播放端不消费该布尔值。仅修正实体显示/池化不能解决格式层差异。

## 规划建议

1. 先指定权威契约。若 Reborn 游戏实测播放是兼容目标，Maker 应解析其标准事件，至少接受无 `hidden` 字段、空数组与类型/轨道单维过滤，并遵循 OR 和事件快照替换语义。
2. 定义 `hidden` 是否为 Maker 私有扩展。如果保留，则不可让其改变 Reborn 标准事件含义；需要规定缺省值与 false 的清除范围，并由导出器进行明确转换。
3. 统一空值规则：区分字段省略、空数组、非空数组；不能用“枚举当前所有轨道/类型”悄悄改变标准语义。建议转换到明确的规范化内部表示，再由各播放路径消费。
4. 对齐编辑器 UI：类型和轨道多选的组合规则必须展示清楚（OR/AND），并支持“全局恢复/替换过滤状态”的事件语义。
5. 复核 2.0 `VISIBLE` 迁移、Chart Converter 输出、Maker 保存后导出的 JSON 三者；至少对 Converter 的空 tracks 旧谱兼容用例回归。
6. 分别验证 Maker 正式播放、滚动播放、特效预览是否走相同的 HIDE_NOTES 解释器。当前本轮定位到的 `RhythmcMaker.java` 分支属于效果播放会话；不能据此断言其他会话全部共享。

## 验收矩阵

- 无 `hidden` + 非空 noteTypes + 空 tracks（Converter 旧谱输出）。
- hidden 缺省、true、false 三种状态。
- noteTypes 缺省/空数组/单类型/多类型；tracks 缺省/空数组/单轨/多轨。
- 仅类型、仅轨道、类型+轨道交集与差集音符，确认 OR/AND。
- 两个连续 HIDE_NOTES 事件验证是替换快照还是增量修改。
- 任意 seek 到事件前、事件拍点、事件后，再反向 seek，验证状态重建。
- 隐藏时预加载、跨事件恢复、音符已显示后隐藏，以及播放结束清理；确认无闪现、无丢判定、无残留实体。
- 2.0 VISIBLE=false/true 导入及 Reborn JSON 往返保存。
