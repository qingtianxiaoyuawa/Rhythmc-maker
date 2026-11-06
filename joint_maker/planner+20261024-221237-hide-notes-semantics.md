# HIDE_NOTES 的 noteTypes / tracks 语义核对

## 用户问题
解释 `{"noteTypes":["TAP","LOOK","HOLD","DODGE"],"tracks":[]}` 在 Reborn 中的含义，列出筛选组合，并核对 Maker 当前行为。

## 核对范围
- Reborn：`RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/EffectFactory.java`、`Game/Effects/EffectImpl/HideNoteEffect.java`、`Game/Managers/Gameplay/EffectManager.java`。
- Maker：`src/main/java/cn/frkovo/rhythmcmaker/common/effect/parameter/EffectParameterSchema.java`、`client/render/EffectParameterEditor.java`、`RhythmcMaker.java` 的 `EffectPlaybackSession.updateHiddenNotes()`。
- 未修改 Maker 源码；未编译、未运行测试。

## Reborn 实际语义
Factory 将 `noteTypes` 解析成 NoteType 集合、`tracks` 解析成轨道 ID 集合；字段缺省或空数组都会成为空集合。EffectManager 对单个音符的判断为：

`hide = (noteTypes 非空 且音符类型在 noteTypes 中) OR (tracks 非空 且音符轨道在 tracks 中)`

因此两组筛选条件是 OR（并集），不是交集。两个集合都空时，两边条件都为 false，当前没有音符被隐藏；HIDE_NOTES 事件应用空集合也起到清空/显示全部的作用。

## 组合表
| noteTypes | tracks | Reborn 隐藏范围 | Maker 当前隐藏范围 |
|---|---|---|---|
| 空 | 空 | 不隐藏（清除隐藏状态） | 不隐藏（清除隐藏键） |
| 非空 T | 空 | 所有轨道上类型属于 T 的音符 | 所有轨道上类型属于 T 的音符；空轨道列表被视为通配 |
| 空 | 非空 R | 所有类型中轨道属于 R 的音符 | R 中所有类型的音符；空类型列表被扩展为谱面实际类型 |
| 非空 T | 非空 R | 类型属于 T **或**轨道属于 R 的音符（并集） | 类型属于 T **且**轨道属于 R 的音符（交集） |

T 是选中的音符类型集合，R 是选中的轨道 ID 集合。因而只要两个字段同时指定了有限集合，Maker 与 Reborn 的差异就会显现：Reborn 更宽地隐藏，Maker 仅隐藏交叉区域。

## 用户给定 JSON 的结论
`noteTypes` 包含全部四类，`tracks` 为空。Reborn 的类型条件匹配所有受支持类型，因此会隐藏所有轨道上的 TAP、LOOK、HOLD、DODGE。Maker 将空 `tracks` 扩展为谱面中的全部实际轨道，再与全部类型取交集，最终也会隐藏谱面中所有这些类型的音符。就这一条数据而言，两者结果一致。

字段位于 `properties` 下时语义相同；Maker 也支持兼容读取事件顶层字段。Reborn 的 Factory 只读取这两个筛选字段，`hidden` 不参与其创建逻辑；Maker 的播放代码额外兼容旧式 `hidden:false` 作为清除动作，但 Maker 参数 schema 没有暴露 `hidden` 字段。因此不要把 Reborn 文档中的 `hidden` 说明直接视为当前 Reborn 实现行为。

## Maker 核对
- Schema 将 HIDE_NOTES 的 `noteTypes`、`tracks` 默认值均设为空数组。
- 编辑器把空数组标为“不限定类型/轨道”；允许单独或同时指定类型与轨道。
- 播放端对某一维空集合进行通配扩展：空类型扩成谱面实际类型，空轨道扩成谱面实际轨道；两个维度均非空时用 `&&` 选取交叉项。
- Maker 结果不是完整 Reborn 语义兼容：四种组合中三个相同，只有“两边都非空”不同。

## 规划建议
1. 兼容 Reborn 时，播放端筛选应按两个独立命中条件做 OR；空集合继续表示该维不提供筛选条件，而两边都空表示没有隐藏目标/清除隐藏。
2. 明确 UI 文案：分别勾选类型和轨道是“命中任意条件即隐藏”，不要暗示双字段是联合筛选；如产品需要 AND，应另设明确模式字段，不能悄然偏离 Reborn。
3. 增加四象限语义测试：空/空、类型/空、空/轨道、类型/轨道，覆盖音符既同时匹配类型与轨道、只匹配一边、都不匹配的情形。
4. 对 `hidden` 字段需以实际 Reborn 版本实现为准；若需要“显示全部”事件，使用空 `noteTypes` 与空 `tracks`，并验证事件时间顺序下状态覆盖。

## 依据定位
- Reborn `EffectFactory.createHideNoteEffect`：解析两个字段；`HideNoteEffect.applyEffect` 设置当前状态、`forceRemove` 重置。
- Reborn `EffectManager.shouldHideNote`：`hideByType || hideByTrack`。
- Maker schema/editor：空数组默认及“不限定”交互。
- Maker `RhythmcMaker.EffectPlaybackSession.updateHiddenNotes`：空集合扩展为谱面可用全集，并以 `selectedTypes.contains(...) && selectedTracks.contains(...)` 形成笛卡尔交集。
