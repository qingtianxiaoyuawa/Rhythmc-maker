# Reborn 源码中 JUDGE_FRAME_COLOR 的表现核对

日期：2026-11-01

## 结论

用户提供的 `RhythMC-Reborn-master (1).zip` 源码快照中，找不到 `JUDGE_FRAME_COLOR`、`JudgeFrameColorEffect`、`frameColor` 等符号；该快照 `EffectType` 枚举也没有此效果。因此不能把当前 Mod beta.3 中新增效果的实现归因于这份 Reborn 源码。

当前 `RhythMC-MOD-0.5.1-beta.3.jar` 字节码显示，该效果触发时把 `properties.color` 传给 `ArenaInstance.setFrameColor`。结合 Arena frame 调色板解析链路，其实际表现是：在谱面该 beat 到达时，把当前竞技场中作为判定框/边框的结构材质切换为指定 palette 对应的方块材质。它是场景内的实体方块/结构颜色变化，不是 HUD 染色、音符染色、粒子染色或 TextDisplay 背景色。它没有自身渐变、淡入淡出或持续时间字段，切换后维持到下一次颜色切换或场景生命周期变化。

## 与 Reborn 快照的关系

该 zip 的旧 `ArenaInstance.generate(Arena)` 通过 WorldEdit 将 Arena clipboard 粘贴到 arenaCenter，记录粘贴区域，并在重建/销毁时将原区域置为空气。这个版本呈现的是由 Arena 结构定义的静态场景边框；并未实现随谱面事件按拍切换边框颜色的效果。

故可作如下对比：

- Reborn zip：竞技场 clipboard 中的边框材质决定静态外观。
- Mod beta.3：在此类竞技场边框基础上增加运行时的 frame color 事件，按 palette key 重新选择/应用边框材质。
- 具体边框形状仍由竞技场结构决定；该效果改变颜色/材质，不改变形状或位置。

“切换时具体重建哪些方块、客户端视觉是否有一帧延迟”等运行表现未通过启动游戏验证，属于根据 Mod 字节码的执行链路推断。

## Maker 启示

若 Maker 后续支持此事件，应把它作为 Beat 上的竞技场边框材质/palette 切换效果预览，而非普通颜色滤镜。预览必须有 Arena 边框几何体及 palette 到材质的映射；只有颜色字符串输入而没有当前场景 palette 时，不能准确还原实际效果。

## 验证边界

读取了用户 ZIP 中 `EffectType.java`、`ArenaInstance.java`，并用 `rg` 检索效果符号；也核对本机 Mod JAR 的效果字节码链。未运行 Reborn 或 Mod 游戏实例，未修改/编译 Maker。
