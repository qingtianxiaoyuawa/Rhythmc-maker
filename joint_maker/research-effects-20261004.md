# RhythMC 3.0 特效类型与播放语义研究

- 研究日期：2026-10-04（按任务指定文件名）
- 研究范围：`RhythMC-Reborn-master.zip`、`RhythMC-Docs-main.zip`、`RhyMC-Backend-master.zip`、`RhythMC-Frontend-main.zip`、`RhythMC-Chart-Converter-master.zip`、`RhythMC-Charter-V2-master.zip`
- 临时解压目录：`C:\Users\28585\AppData\Local\Temp\RhythMC-research-20261004`
- 结论性质：以下“可能未接入”是对主工程当前代码面的静态研究判断，不等同于运行时最终验证。
- 主工程只读对照：`F:\b晴天小雨awa\RhythMC\rhythmc maker`

## 1. 统一事件模型与播放语义

参考运行时把特效表示为三段：`effectType`、`beat`、`properties`。重制版 `Effect` record 明确字段为 `EffectType eventType`、`double beat`、`JSONObject properties`；文档规定 `.rmcc` 的 `effects` 数组使用同样结构。

- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Format/Effect.java:5-9`
- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Format/EffectType.java:3-22`
- 证据：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/effects/effects.md:7-25`
- 解析：`EffectDeserializer` 从 `effectType` 读取枚举名、从 `beat` 读取拍数、从 `properties` 读取参数；缺少类型或类型非法会告警并返回失败路径。
- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/File/Deserializers/EffectDeserializer.java:26-48`

### 时间单位

- `beat` 是歌曲时间轴单位，类型为 `double`；不是 tick、毫秒或秒。
- `TITLE.fadeIn/stay/fadeOut` 是 Minecraft tick；文档明确标为 tick，运行时直接传入 Bukkit title API。
- `TIME.time` 是 Minecraft 世界时间 tick，范围约 `0-24000`。
- `EFFECT.duration`、`TIME.duration`、`HOLOGRAM.duration`、`TEXT_DISPLAY_SYNC_TRACK.duration` 在重制版工厂中按 `long` 读取，并作为毫秒时长传入 timed effect；默认值来自 `GameConstants.DEFAULT_EFFECT_DURATION` 或 `DEFAULT_SYNC_TRACK_DURATION`。
- 老格式转换器的 `duration` 是旧格式 tick，转换为毫秒时统一乘 `50L`；因此导入旧谱时不能把转换后的值再次当 tick。
- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/EffectFactory.java:87-92,107-120,233-240`
- 证据：`RhythMC-Chart-Converter-master/RhythMC-Chart-Converter-master/src/main/java/cn/frkovo/converter/mapper/EffectTypeMapper.java:120-126,151-176`
- 证据：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/effects/title.md:39-45`
- 证据：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/effects/time.md:36-39`

### 激活与持续

`EffectManager`/抽象 effect 层将效果分为一次性、定时和持久效果；定时效果由持续时长控制，移除时执行清理。效果的触发点仍由 `beat` 决定，不能把 `duration` 当作触发拍数。

- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/AbstractClasses/AbstractEffect.java`
- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/AbstractClasses/AbstractTimedEffect.java`
- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/AbstractClasses/TimedEffect.java`
- 证据：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/AbstractClasses/PersistentEffect.java`

## 2. 参考运行时支持的特效类型

`EffectType` 与 `EffectFactory.createEffect` 的 switch 一一对应，当前参考运行时没有枚举了但工厂不处理的类型。可实例化类型如下：

| 类型 | 参考实现/运行时语义 | 关键字段（以工厂读取为准） | 质量要求/注意 |
|---|---|---|---|
| `HOLOGRAM` | 在相对场中心的位置创建全息文字，默认生成 ID，可持续后清理 | `location` double[3]、`id`、`contents` String[]、`duration` ms | 坐标是相对 `screenCenterLoc` 的偏移；ID 必须可被移除效果复用 |
| `REMOVE_HOLOGRAM` | 按 ID 移除全息文字 | `id` | ID 前缀/作用域需要和创建端一致 |
| `TITLE` | 向玩家发送标题、副标题 | `title`、`subtitle`、`fadeIn`/`stay`/`fadeOut` tick | 不是通用文本消息；三个时间字段必须保持 tick |
| `FIREWORK` | 在一个或多个位置生成烟花 | 运行时代码读取 `locations`、`colors`、`fadeColors`、`type`、`flicker`、`trail` | 文档总览写的是 `location`/`power`/`colors`，与工厂代码不一致，接入前应以实际 producer/consumer 统一 |
| `TIME` | 改变玩家视角世界时间，结束时恢复 | `time` 世界 tick、`duration` ms | `time` 与 `duration` 单位不同 |
| `EFFECT` | 给予药水效果 | `effectId` int、`amplifier` int、`duration` ms | `amplifier` 为 0-based；不能直接把文档中的字符串药水名当作参考运行时输入 |
| `CLEAR_EFFECT` | 清除指定药水效果，未给列表时清除全部 | `effects` int[]，可省略 | 空/省略语义是“全部清除” |
| `WEATHER` | 设置玩家视角天气 | `weather`：`CLEAR`/`RAIN`/`THUNDER` | 枚举值需大写匹配 |
| `ARENA` | 切换竞技场 | `arena` 字符串 | 工厂会转小写并从 arena manager 查找 |
| `TEXT_DISPLAY` | 创建文本显示实体 | `position` float[3]、`rotation` float[3]、`scale` float[3]、`text`、`id` | 三元数组长度和 ID 是硬前置；位置仍通过场中心偏移 |
| `TEXT_DISPLAY_EFFECT` | 修改已有文本显示实体 | `id`、`type`，以及按 type 的参数 | 支持 `TEXT`、`SHADOW`、`OPACITY`、`BACKGROUND_COLOR`、`LINEAR_TRANSFORMATION`、`GLOWING`；未知 type 直接抛异常 |
| `TEXT_DISPLAY_SYNC_TRACK` | 让文本显示跟随轨道 | `id`、`track` int、`duration` ms | 需要可解析的 track 与同步持续时间 |
| `TEXT_DISPLAY_DESYNC_TRACK` | 停止文本跟随轨道 | `id` | 必须与 sync 时使用相同 ID |
| `TEXT_DISPLAY_REMOVE` | 删除文本显示实体 | `id` | 与 desync/sync/create 共享 ID 生命周期 |
| `HIDE_NOTES` | 隐藏指定音符类型和轨道 | `noteTypes` String[]、`tracks` int[] | 参考运行时按 `NoteType.valueOf` 读取字符串；转换器旧格式默认覆盖 `TAP/LOOK/HOLD/DODGE` |
| `GLOW_COLOR` | 改变音符发光颜色 | `color` | 参考实现使用 Bukkit `ChatColor` 名称；颜色命名需统一 |
| `MESSAGE` | 发送聊天消息 | 运行时代码读取 `contents` String[] | 文档总览页写 `message`/`type`，与参考工厂冲突；应优先验证实际谱面样本与运行时代码 |

总览文档的类型列表与枚举一致，见：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/effects/effects.md:27-47`。工厂的实际分派见：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/EffectFactory.java:61-82`。

## 3. 文档字段与运行时代码冲突

这些冲突是接入时最容易造成“编辑器能保存、播放端不生效”的地方：

1. `FIREWORK`：文档总览使用 `location`、`power`、`colors`；参考 `EffectFactory` 实际读取 `locations`、`colors`、`fadeColors`、`type`、`flicker`、`trail`。两者不能直接互换。
2. `MESSAGE`：文档总览使用 `message` 字符串和 `type`；参考工厂读取 `contents` 数组，并构造 `MessageEffect`。转换器也写入 `contents` 数组。
3. `EFFECT`：文档总览写 `type` 字符串、tick `duration`，参考工厂读取 `effectId` 数字和毫秒 `duration`；转换器把旧 tick 乘 50 转成毫秒。
4. `TIME`：效果总览只列 `time`，但具体实现文档和工厂都支持可选 `duration`；若只实现总览字段，会遗漏恢复时机。
5. `TEXT_DISPLAY_EFFECT`：总览效果表只给出目标位置/旋转/缩放，但工厂的真正变换协议是 `type` + 分支参数，尤其包括背景颜色、透明度、线性变换和发光。

优先级建议：播放/导出协议应以“实际消费者代码 + 转换器输出”为准，文档字段作为编辑器提示；对有冲突的类型保留兼容读入，但写出时只选择一个规范形态。

## 4. 主工程当前静态接入情况

主工程已经具备“可识别、可保存、可编辑、可导出”的基础：

- `EffectJsonCodec` 读取 `eventType` 或兼容字段 `type`、`beat`、`properties`，并保留未知顶层字段。
- `EffectTypeRegistry` 已登记参考的 17 个类型，并额外登记 `ACTIONBAR`、`CHANGE_ARENA`。
- `EffectEditorImGuiView` 会加载效果、显示类型注册表、保存编辑结果，并调用 `EffectAnimationEvaluator` 预览动画通道。
- `ChartStorage` 读写 `chart.effects`，`Rhythmc3Exporter` 将 effects 原样复制到导出结构。
- `ChartTiming` 与 `TrackPlayback` 能按 beat/BPM 计算播放位置；beat 到秒的换算是 `60 / BPM`，不是按固定 tick 直接推进。

对应主工程证据：

- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\common\effect\EffectJsonCodec.java:15-52`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\common\effect\EffectTypeRegistry.java:38-89`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\client\render\EffectEditorImGuiView.java:47,72-73,118-119,262`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartStorage.java:142,178,221`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\Rhythmc3Exporter.java:131`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartTiming.java:37-46,98-109`

### 可能未接入或仅部分接入的类型/字段

以下不是说主工程完全不能保存这些事件，而是从当前源码可见面看，缺少与参考 Bukkit 运行时等价的通用“按 beat 触发并执行世界/玩家副作用”的连接：

- **所有非动画副作用类型的执行链**：`HOLOGRAM`、`REMOVE_HOLOGRAM`、`TITLE`、`FIREWORK`、`TIME`、`WEATHER`、`ARENA`、`MESSAGE`、`CLEAR_EFFECT`、`GLOW_COLOR`、`HIDE_NOTES`、`TEXT_DISPLAY_*` 均在注册表/JSON/编辑器层可见，但主工程未发现对应的统一 effect executor 或逐类型渲染/执行分派。
- **`TEXT_DISPLAY_EFFECT` 的分支参数**：主工程 `displayProperties()` 只登记 `text/color/x/y/z/scale/opacity`，没有参考运行时的 `type`、`shadowed`、`targetOpacity`、RGBA `color`、`position/rotation/scale` 三元数组、`GLOWING` 等协议字段。
- **`TEXT_DISPLAY`/`HOLOGRAM` 的结构字段**：主工程使用通用平铺字段 `x/y/z/scale/opacity`，而参考运行时需要 `id`、`position`、`rotation`、`scale`、`text` 或全息图的 `location`、`contents`、`duration`。
- **`TITLE`**：主工程的 `textProperties()` 仅登记 `text`、`color`，没有 `title`、`subtitle`、`fadeIn`、`stay`、`fadeOut`。
- **`MESSAGE`**：主工程登记 `text`、`color`，参考运行时工厂读取 `contents` 数组；文档又描述 `message/type`，需要明确兼容策略。
- **`FIREWORK`**：主工程登记的是 `x/y/z/color/count`，参考工厂需要多位置、颜色/渐变颜色、烟花类型、闪烁、拖尾；字段形状和语义都不等价。
- **`EFFECT`**：主工程登记 `effect` 枚举、整数 `duration`，参考重制版读 `effectId`、毫秒 `duration`；药水的 `ambient`、`particles` 也未在注册表登记。
- **`CLEAR_EFFECT`**：主工程没有 `effects` 数组字段，无法表达“只清除部分药水效果”；当前定义只能覆盖无参数清除全部的简化形态。
- **`TIME`/`WEATHER`/`ARENA`**：主工程有少量基础字段，但没有看到参考运行时的持续恢复语义、玩家视角天气执行、arena manager 切换链路。
- **`HIDE_NOTES`**：主工程登记 `noteTypes` 为整数数组，而参考运行时按字符串枚举读取；虽然主工程另有 `hidden` 字段，但需要确认导出/消费者是否做了编号到名称的映射。
- **动画播放接线**：主工程已有 `EffectAnimationEvaluator` 按 beat 采样，说明可预览的动画状态模型已存在；但当前可见调用集中在效果编辑器预览，尚未发现它把所有参考 `TEXT_DISPLAY_EFFECT` 参数绑定到真实实体或世界渲染。

## 5. 质量要求

1. 规范化事件头：写出时固定 `eventType`，`beat` 使用有限 `double`，`properties` 保持对象；不要混用 `type` 作为事件类型，除非明确是兼容读入。
2. 单位标注必须写在字段定义或 UI 中：beat、Minecraft tick、持续毫秒是三套不同单位。
3. 数组字段做长度和类型校验：位置/旋转/缩放必须是长度 3；颜色数组、音符类型数组、轨道数组要逐项校验。
4. ID 生命周期要可追踪：`TEXT_DISPLAY`/`HOLOGRAM` 创建、变换、同步、反同步、移除必须共享明确 ID 作用域；参考全息图还会加游戏实例前缀。
5. 默认值需与参考实现一致：未给持续时间时通常按一年级别的毫秒默认值；未给 `CLEAR_EFFECT.effects` 时是全部清除。
6. 对冲突协议做 golden samples：至少覆盖 `FIREWORK`、`MESSAGE`、`EFFECT`、`TEXT_DISPLAY_EFFECT`，同时验证读取、编辑后写出、RhythMC 3.0 导出和播放消费者。
7. 播放调度不能只看墙上 tick：应按 BPM 事件将 beat 转秒；主工程现有 `ChartTiming` 已采用分段 BPM 计算，可作为调度基准。
8. 未实现副作用时，编辑器应显示“可保存/不可在本地执行”的状态，避免用户将“预览动画”误认为“完整播放”。

## 6. 可直接引用的源码索引

### 参考运行时

- 类型枚举：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Format/EffectType.java:3-22`
- 事件结构：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Format/Effect.java:5-9`
- JSON 反序列化：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/File/Deserializers/EffectDeserializer.java:26-48`
- 类型分派与字段读取：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/EffectFactory.java:61-260`
- 持续/清理抽象：`RhythMC-Reborn-master/RhythMC-Reborn-master/src/main/java/cn/frkovo/rhythmcv2/Game/Effects/AbstractClasses/AbstractTimedEffect.java`、`TimedEffect.java`、`PersistentEffect.java`
- 转换器映射：`RhythMC-Chart-Converter-master/RhythMC-Chart-Converter-master/src/main/java/cn/frkovo/converter/mapper/EffectTypeMapper.java:1-240`

### 参考文档

- 总览与字段表：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/effects/effects.md:7-174`
- 总体 `.rmcc` 格式：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/song-format.md:248-286`
- 各类型单页：`RhythMC-Docs-main/RhythMC-Docs-main/docs/charter/effects/*.md`

### 主工程对照

- 注册表：`F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\common\effect\EffectTypeRegistry.java:38-89`
- JSON 持久化：`F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\common\effect\EffectJsonCodec.java:15-92`
- 动画模型：`F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\common\effect\animation\EffectAnimationJson.java:20-85`、`EffectAnimationEvaluator.java:12-19`
- 效果编辑器：`F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\client\render\EffectEditorImGuiView.java:47-262`
- beat/BPM 播放：`F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartTiming.java:37-109`、`TrackPlayback.java:43-109`
- 导入/导出保留：`F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartStorage.java:142-221`、`Rhythmc3Exporter.java:131`

## 摘要

参考 RhythMC 3.0 的核心语义是“按歌曲 beat 触发事件，properties 携带类型参数，持续型效果用毫秒生命周期，标题/世界时间仍使用 Minecraft tick”。主工程已覆盖事件保存、编辑、动画 beat 采样和导出骨架，但当前最可能未接入的是逐类型副作用执行，以及 `TITLE`、`FIREWORK`、`MESSAGE`、`EFFECT`、`TEXT_DISPLAY_EFFECT`、`CLEAR_EFFECT`、`HIDE_NOTES` 的字段/单位兼容层。优先补齐一个统一的按 beat 调度器，再为冲突字段建立规范写出与兼容读入测试。
