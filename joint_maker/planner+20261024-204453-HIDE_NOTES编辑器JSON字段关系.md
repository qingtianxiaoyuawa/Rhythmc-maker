# HIDE_NOTES 编辑器字段与音符匹配关系

## 结论

Maker 特效参数编辑器把类型选择写入 `properties.noteTypes`，把轨道选择写入 `properties.tracks`。这两个数组描述“筛选条件”，不是某个音符的独立 ID，也不是谱面 `tracks` 数组本身。音符对象自己的 `type` 与 `trackId` 才是实际匹配值。

但当前播放实现还要求 `hidden=true` 才会添加隐藏键；编辑器 schema 不提供 `hidden`，UI 对空数组显示“恢复显示”。因此 UI 所编辑的两个字段与 Maker 播放所需的状态动作并未闭环。单靠编辑器写入的类型/轨道参数，按当前源码会因为 hidden 缺省而执行 false 分支。

## 编辑器如何写 JSON

- 参数定义在 `EffectParameterSchema` 的 HIDE_NOTES：`noteTypes` 是 NOTE_TYPES 多选，选项 TAP/LOOK/HOLD/DODGE；`tracks` 是 INTEGER_LIST。
- `EffectParameterEditor.renderNoteTypesMenu()` 与 `renderTracksMenu()` 提供多选。勾选/取消会经 `commitValue()` 调用 `EffectParameterCodec.write()`。
- `EffectParameterCodec.write()` 把值写进事件 `properties` 对象。因此结果大致为：

```json
{
  "eventType": "HIDE_NOTES",
  "beat": 8.0,
  "properties": {
    "noteTypes": ["HOLD"],
    "tracks": [1]
  }
}
```

- 类型字段是字符串枚举数组；轨道字段是整数 ID 数组。选多个就写多个值。
- 空数组在 UI 预览中表示“恢复显示”。“全部轨道”菜单选项会写入当前所有轨道 ID，而非通配符；没有隐式的 `*`。

## 字段如何关联到音符

每个音符有 `note.type` 和 `note.trackId`。Maker 将 `noteTypes` 字符串映射到数字 type（TAP=0、LOOK=1、HOLD=2、DODGE=3；轨道保持实际整数 ID），然后构造 `(trackId,type)` 键。

当前 Maker 播放器隐藏判断：`hiddenNoteKeys.contains((note.trackId,note.type))`。因此，在 `hidden=true` 的前提下，隐藏条件为：

`note.trackId ∈ properties.tracks AND note.type ∈ properties.noteTypes`

换言之两字段是 AND，两个数组分别限定候选集合，再取笛卡尔积；同一数组内多选是 OR（命中任意选中值）。例如 `noteTypes=[HOLD]`、`tracks=[1,2]` 会隐藏轨道 1 或 2 上的 HOLD，但不隐藏这些轨道的 TAP，也不隐藏其他轨道的 HOLD。

## 空值/缺省差异

- UI 选择“恢复显示”会把对应字段写成 `[]`，不是删除字段。
- 播放解析时，字段缺失会扩展成谱面现存全部 type/track；字段显式为空数组则保持空集合。构造笛卡尔积时任一边为空，结果都是零个 key。
- 更关键的是 `updateHiddenNotes()` 把缺失的 `hidden` 解释为 false；而 HIDE_NOTES 参数 schema 没有 `hidden` 字段。标准编辑器 JSON 因而没有提供让隐藏键加入集合的标志。
- 由此，“恢复显示”空数组的 UI 标签目前只是参数层表达，无法说明播放端实际全局恢复语义；播放端只有 false 时按所选组合逐项移除，并非完全重置所有隐藏键。

## 与 Reborn 字段关系

Reborn `EffectFactory` 读取相同名字 `noteTypes` 与 `tracks`，但将其作为两个独立集合，在音符判断中用 OR：类型匹配或轨道匹配即隐藏。Reborn 不读取 `hidden`。因此字段名相同不等于含义相同：Maker 当前的 AND 组合键模型与 Reborn 的 OR 过滤模型不同；Converter 产生的显式空 tracks 也不能被 Maker 当作“类型过滤通配轨道”。

## 规划结论

应把事件规范化为明确的内部语义后再供 UI、存档、播放共用。优先决定目标是 Reborn 兼容（OR + 状态快照/空集合语义）还是 Maker 扩展（AND + 显式动作字段）；如要求 Reborn 兼容，不应让可选的 `hidden` 缺省吞掉标准事件。编辑器文案应与最终播放语义一致，并提供明确的“全局恢复”动作。
