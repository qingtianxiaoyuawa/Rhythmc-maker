# JUDGE_FRAME_COLOR 效果说明

日期：2026-11-01

## 定义

基于本地 `RhythMC-MOD-0.5.1-beta.3.jar` 字节码确认，`JUDGE_FRAME_COLOR` 是一个在谱面指定 Beat 触发的即时效果，用于改变当前播放场景/竞技场判定框（frame）的材质/颜色选择，不是给音符加颜色，也不是 TextDisplay 的背景色。

EffectFactory 从 `properties.color` 读取字符串，构造 `JudgeFrameColorEffect`。效果执行时调用 `PlaybackInstance.getArenaInstance().setFrameColor(color)`。ArenaInstance 会规范化颜色名，再按竞技场 frame palette 找对应方块材质；因此最终视觉通常是判定框方块/边框的材质颜色变化。若颜色无法对应有效材质，场景帧生成逻辑可能退回竞技场的 border 材质。该效果的 `forceRemove()` 为空，说明它不是有持续时间的渐变或临时染色效果；颜色会保持到后续另一个 frame-color 效果或场景生命周期改变它。

## 颜色值含义

Mod 的 ArenaFrameColors 默认色名表是 16 种染料色名：`white`、`orange`、`magenta`、`light_blue`、`yellow`、`lime`、`pink`、`gray`、`light_gray`、`cyan`、`purple`、`blue`、`brown`、`green`、`red`、`black`。内部名称会解析到默认染料方块材质；竞技场也可提供自定义 frame palette 映射，因此最终材质取决于该谱面所用竞技场配置。

字节码确认的字段是字符串 `color`，并非 RGB/RGBA 数值或十六进制颜色。效果本身不携带 duration、fade 或 tween 参数。

## 与 Maker 的关系

当前 Maker 的 `EffectParameterSchema` 没有注册 `JUDGE_FRAME_COLOR`，所以 Maker 没有对应的结构化编辑入口及已确认的预览处理。含该效果的谱面能否导入后原样保留，取决于 ChartStorage 和导出校验对未知类型的行为；本轮未完整验证这条往返链路，不能保证无损。

如果后续实现，应按竞技场色名/自定义 palette key 建模，而非复用 `GLOW_COLOR` 的 Minecraft ChatFormatting 颜色枚举，也不能按 RGB 输入。预览还必须将颜色映射到 Maker 当前竞技场的 frame 材质/颜色资源。

## 验证边界

已核对 EffectFactory、JudgeFrameColorEffect、ArenaInstance.setFrameColor 和 ArenaFrameColors 字节码。未运行游戏验证特效的精确触发时序、特定竞技场的全部自定义 palette 与无效色名表现；以上属于字节码推断。未修改、编译或测试工程。
