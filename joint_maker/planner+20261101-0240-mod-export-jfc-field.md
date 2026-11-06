# Mod 导出成品中的 JUDGE_FRAME_COLOR 字段

日期：2026-11-01

## 结论

RhythMC-MOD-0.5.1-beta.3 的 RMCC 导出序列化结构支持该效果：事件写出为 `effectType`、`beat` 和 `properties`；事件类型从 `EffectType.name()` 写出，properties 对象从导出中间模型继续写入。因此存在该事件时，其 JSON 形态应为：

```json
{
  "effectType": "JUDGE_FRAME_COLOR",
  "beat": 16.0,
  "properties": {
    "color": "red"
  }
}
```

这里 `color` 是 palette key，不是直接的 Minecraft block ID。Mod EffectFactory 读取 `properties.color` 字符串并用于运行时效果。

## 重要限定

- 本轮确认的是**导出序列化链路具备承载该字段的能力**，不是已从某个用户导出的 RMCC 文件实测到一条具体事件；提供的 Mod JAR 内没有可用于证明实际谱面含该事件的 RMCC 成品。
- Reborn ZIP 源码快照比此 Mod JAR 对应实现旧：其 `EffectType` 没有 `JUDGE_FRAME_COLOR`，`EffectDeserializer` 只允许 enum 中已定义的事件。因此旧 Reborn 源码不能读取/导出该事件；结论针对所提供的 MOD 0.5.1-beta.3 导出链路。
- 是否能通过当前编辑器 UI 创建此事件与是否能由通用 RMCC 序列化器输出，是两个问题；本次只核查导出格式与序列化器，不确认 UI 入口可用性。

## 验证依据

反汇编本机 Mod JAR 的 `ExportFrontend`、`ExportIr.IrEffect` 和 `RmccWriter`：前端从编辑器效果列表构造中间效果，保留 EffectType、beat 和复制后的 properties；writer 输出 `effectType` 字段及 properties。结合 `EffectFactory` 中读取 `color` 的字节码确定属性名。未运行游戏，不涉及源码修改或构建。
