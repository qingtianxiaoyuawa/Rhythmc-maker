# JUDGE_FRAME_COLOR 是否接受直接材质输入

日期：2026-11-01

## 结论

特效本身的 `properties.color` **不直接接收任意 Minecraft 方块 ID/材质 ID**。它读取的是颜色/帧 palette 名称，例如 `red` 或 `lime`，再由当前 Arena 的 `frameColor(name)` 将名称解析为对应 `Material`。

Mod 内置名称包括 16 种染料色名：`white`、`orange`、`magenta`、`light_blue`、`yellow`、`lime`、`pink`、`gray`、`light_gray`、`cyan`、`purple`、`blue`、`brown`、`green`、`red`、`black`。`border` 表示使用竞技场本身的边框材质。

Arena 可定义自定义 frame palette：效果仍传 palette key，Arena 配置将 key 映射到材质。因此用户可以通过竞技场配置间接指定材质，但不能把任意 `minecraft:...` 材质 ID 直接写进此效果的 `color` 字段来完成选择。名称会 trim 并转小写；自定义名称校验格式为 `[a-z0-9][a-z0-9_-]{0,23}`。

示例：

```json
{
  "effectType": "JUDGE_FRAME_COLOR",
  "beat": 16.0,
  "properties": { "color": "red" }
}
```

## 验证依据

检查了本机 `RhythMC-MOD-0.5.1-beta.3.jar` 的 `EffectFactory`、`JudgeFrameColorEffect`、`ArenaInstance.setFrameColor`、`Arena.frameColor` 和 `ArenaFrameColors` 字节码：Factory 只从 properties 取字符串 `color`；运行时按竞技场 palette key 解析，解析结果才是兼容层 Material。未做运行时游戏验证。

未修改 Maker 源码、未编译或测试。
