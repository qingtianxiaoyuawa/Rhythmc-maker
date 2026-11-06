# Reborn 与 Maker 音符隐藏机制对照

## 任务边界

本轮为源码分析与规划记录；未修改 RhythMC Maker 工程源码，未编译、未运行测试。

## 溯源范围

- Reborn：`RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Managers/Gameplay/EffectManager.java`、`Effects/EffectImpl/HideNoteEffect.java`、`Chart/NoteObject.java`、`Managers/Gameplay/NoteManager.java`、`Effects/EffectFactory.java`。
- 契约/转换：`RhythMC-Docs-main/docs/charter/effects/hide-notes.md`、`RhythMC-Chart-Converter-master/src/main/java/cn/frkovo/converter/mapper/EffectTypeMapper.java`。
- Maker：`src/main/java/cn/frkovo/rhythmcmaker/RhythmcMaker.java`、`chart/ChartStorage.java`、特效参数 schema/migration。
- Charter V2 的可见性搜索命中是预览渲染默认可见属性，未发现其承担 Reborn 运行时 HIDE_NOTES 播放职责；其余前后端源码未发现独立的音符隐藏渲染链路。格式转换器主要做旧 `VISIBLE` 到新效果的数据转换。

## Reborn 实现

1. `EffectManager` 持有当前 `hideNoteTypes` 与 `hideTracks` 两个集合；默认空集合，即不隐藏。
2. `HideNoteEffect.applyEffect()` 设置状态；它是持续效果，没有独立计时结束逻辑。后续隐藏效果覆盖当前集合。`forceRemove()` 将状态清空；清理活动效果时调用移除逻辑。
3. `EffectManager.shouldHideNote(type, track)` 使用 `hideByType || hideByTrack`。即命中指定类型或指定轨道任一条件便隐藏。两个维度均空时不隐藏。
4. `NoteObject.update()` 每次更新读取隐藏状态。隐藏时不获取/不更新可见展示实体，但仍持续计算运动/位置；取消隐藏后重新 acquire 并设置显示位置。判定/音符时序数据并未被移除，因此隐藏是视觉隐藏，不等同于删除音符或取消判定。
5. `EffectManager` 将事件按 beat 排序并在时钟推进时激活；HIDE_NOTES 的状态因此跟随播放时间持续生效。

## 文档与旧格式兼容

- Docs 的 `hide-notes.md` 定义 `hidden=true/false`，类型和轨道字段分别可省略（默认全部），效果持续到后续 HIDE_NOTES 覆盖；可见性不改变判定。
- Chart Converter 将旧 `VISIBLE=false` 转成 `HIDE_NOTES hidden=true`，`VISIBLE=true` 或缺失转成显示状态。Maker 的 `ChartStorage` 也识别旧 `VISIBLE` 类型并映射为 `HIDE_NOTES`；迁移具体字段/极性应作为兼容性回归点。

## Maker 当前链路

1. 播放会话维护 `hiddenNoteKeys`，元素为 `(trackId, noteType)`；另有同 key 的 `BlockDisplayEntity` 对象池。
2. HIDE_NOTES 触发后，`updateHiddenNotes()` 解析 `hidden`、`noteTypes`、`tracks`，生成类型与轨道的笛卡尔积键集；随后 `applyVisualState()` 遍历当前活动显示实体，对已隐藏音符执行 `recycle()`，将实体设为不可见并移至池位置。恢复显示时 `restoreVisibleNotes()` 重新 acquire/布置未来可见音符。
3. 新音符预加载路径先 `shouldHide(note)`，因此隐藏状态下不会新建可见显示实体。音符仍保留在谱面/命中时间结构中；隐藏和判定音频/判定流程是分开的。
4. seek 重建先清除视觉与隐藏状态，再从时间起点重放此前效果、恢复目标时刻视觉状态；理论上保证任意 seek 的隐藏状态由同一效果时间线重算，而不是只挪 UI 指针。
5. 回收对象池减少实体反复 spawn/discard，但仍需关注每次效果切换遍历活动显示实体、恢复时遍历音符，以及对象池实体数量随峰值活动音符增长的成本。池实体应在会话结束时清理。

## 重要语义差异与风险

- **类型/轨道同时指定时存在语义差异**：Reborn 是 OR（命中类型或命中轨道即隐藏）；Maker 生成 `(track,type)` 笛卡尔积，实际是 AND（只有同时属于所选轨道和所选类型才隐藏）。若需跨端一致，应先按 Reborn 当前运行语义修正 Maker 规划，或由产品明确将契约改成 AND 并同步 Reborn/文档；不能只依据字段名假定相同。
- **空数组与缺省字段需区分**：文档把字段省略解释为全部。Maker 缺省字段会枚举全部现存轨道/类型，但显式空数组会得到空集合、形成零键，效果无作用。Reborn 由空过滤集合代表不按该维度筛选；空集合交互需做格式测试。
- **显示状态事件的作用范围**：Reborn 的 `HideNoteEffect.forceRemove()` 清空全局隐藏状态。Maker `hidden=false` 目前对计算出的键逐个移除，不是无条件清空全部键。需检查事件解析/迁移是否明确给出重置范围，尤其旧 `VISIBLE=true` 是否应全局恢复还是只恢复传入范围。
- **注释/维护风险**：Maker 的键集合本身可表示精确组合，但不能直接等价 Reborn 两个独立集合的 OR 语义。建议抽出统一的匹配规则作为跨端兼容验收基准，避免后续编辑器预览与正式播放漂移。
- 本轮只做静态源码阅读；未验证运行时实体池、seek 与具体谱面的实际效果。

## 建议交付编译合作方的任务

1. 先确定并书面冻结 HIDE_NOTES 的筛选组合规则；如以 Reborn 为权威，类型/轨道分别命中时均应隐藏（OR）。
2. 统一缺省、空数组、单字段、双字段、`hidden=false` 的行为，并补充格式兼容用例。
3. 对照 Maker 所有播放模式（正式播放、滚动播放、特效预览）逐一确认共享同一状态语义；不改变判定/播放逻辑，只让渲染可见性与 Reborn 对齐。
4. 验收场景：仅类型隐藏；仅轨道隐藏；类型+轨道同时指定且音符只命中其中一项；隐藏事件后 seek 前后；隐藏状态下新音符进入预加载区；显示恢复；旧 VISIBLE 导入；结束/退出后实体与状态清理。
5. 性能验收需分别观察活动显示实体数、对象池峰值、每次效果状态切换扫描量及 seek 重建耗时，避免以“对象未销毁”单一指标推断没有开销。
