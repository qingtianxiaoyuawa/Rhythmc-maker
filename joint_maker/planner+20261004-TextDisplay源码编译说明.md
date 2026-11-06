# RhythMC 3.0 TextDisplay 源码编译说明

- 日期：2026-10-04（Asia/Singapore）
- 身份：Planner；本轮只分析源码和参考 ZIP，不修改工程源码、不执行编译、不测试、不部署。
- 用户问题：RhythMC 3.0 源码中关于播放 TextDisplay 的内容是如何编译的。

## 核心结论

需要区分两种“编译”：

1. **Java/Gradle 编译**：把 `RhythmcMaker.java`、`ChartStorage.java` 等源码编译成 `.class`，再打包到 Fabric mod JAR。
2. **TextDisplay 播放处理**：谱面中的 JSON 事件不会在 Gradle 阶段编译成实体代码，而是在游戏运行时由 `PlaybackSession` 按 Beat/秒解释，创建和更新 `TextDisplayEntity`。

因此不存在一个单独的 `compileTextDisplay` Java 编译步骤。

## 3.0 Java 编译链路

当前工程配置位于：

- `build.gradle`
- `gradle.properties`
- `src/main/resources/fabric.mod.json`

当前源码配置：

- Minecraft `1.21.11`
- Yarn `1.21.11+build.6`
- Fabric Loader `0.19.5`
- Fabric API `0.141.6+1.21.11`
- Java `21`
- 当前 `gradle.properties` 的 `mod_version=1.6.9`

`build.gradle` 使用 Fabric Loom；`JavaCompile` 设置 `options.release=21`。源代码编译后，`RhythmcMaker` 主入口及客户端入口会与资源一起进入 `rhythmc-maker-<version>.jar`。`fabric.mod.json` 声明主入口为 `cn.frkovo.rhythmcmaker.RhythmcMaker`，客户端入口为 `cn.frkovo.rhythmcmaker.client.RhythmcMakerClient`。

TextDisplay 相关逻辑没有单独模块或独立编译任务，属于主模组服务端 Java 类的一部分。

## 3.0 运行时播放链路

运行时流程：

```text
ChartManifest.effects JSON
  -> PlaybackSession 构造
  -> effects 按 effectSeconds 排序
  -> ChartTiming.beatToSeconds
  -> 服务端 tick 计算 songTime
  -> 到达事件时间
  -> playTrackEffect
  -> applyTextEffect
  -> createTextDisplay / 更新既有实体
  -> updateTextDisplays 持续更新与清理
```

关键位置在 `RhythmcMaker.java`：

- `processPlaybackSessions()`：逐 tick 处理正式播放会话；
- `PlaybackSession` 构造：准备 `chart.effects`、排序并缓存事件时间；
- `effectSeconds()`：把事件 Beat 转成带 chart offset 的秒数；
- `applyTextEffect()`：按事件类型路由 TextDisplay 行为；
- `createTextDisplay()`：创建 `DisplayEntity.TextDisplayEntity`；
- `setTextDisplayPose()` / `setTextDisplayTransform()`：设置位置、旋转、缩放和显示边界；
- `updateTextDisplays()`：处理到期删除和轨道跟随更新。

## 事件类型处理

`applyTextEffect()` 当前处理：

- `TEXT_DISPLAY`：创建/更新普通文字实体；
- `HOLOGRAM`：读取多行 `contents` 并创建文字实体；
- `TEXT_DISPLAY_EFFECT`：修改文字、背景、透明度、阴影、发光或线性变换；
- `TEXT_DISPLAY_SYNC_TRACK`：将实体绑定到动画轨道；
- `TEXT_DISPLAY_DESYNC_TRACK`：解除轨道绑定；
- `TEXT_DISPLAY_REMOVE` / `REMOVE_HOLOGRAM`：删除实体并清理缓存。

实体使用 `Map<String, TextDisplayEntity>` 按事件 `id` 复用；另有 anchor、track 和 expiry 缓存。

## 坐标、变换和边界的运行时处理

当前基准常量在 `RhythmcMaker.java`：

```text
TEXT_DISPLAY_CENTER_X = 200.5
TEXT_DISPLAY_CENTER_Y = 66.0
TEXT_DISPLAY_CENTER_Z = -1.0
```

普通事件的最终位置是：

```text
worldPosition = (200.5, 66.0, -1.0) + event.position
```

普通事件缺少位置时的默认相对位置当前约为：

```text
(0, 1.5, 0)
```

因此默认普通 TextDisplay 最终约在 `(200.5, 67.5, -1.0)`。

`setTextDisplayTransform()` 另外根据 scale 最大分量设置相同的 width/height：

```text
bounds = 2 * max(abs(scale.x), abs(scale.y), abs(scale.z))
```

这不是依据判定框或文本实际像素尺寸计算，因此实体调试框可能与场景框不重合。

## 2.0 转换与旧源码的关系

用户提供的 `RhythMC-Chart-Converter-master.zip`、`RhythMC-Reborn-master.zip` 等参考源码，不直接参与当前 3.0 Maker 的 Gradle 编译。

当前 3.0 的 `ChartStorage.java` 在导入旧 2.0 谱面时负责一次数据格式转换：

- 旧 `TEXT` → `TEXT_DISPLAY`；
- 旧 `HOLOGRAM` → `HOLOGRAM` 的新字段；
- 旧 `TRANSFORMATIONS` → `TEXT_DISPLAY_EFFECT`；
- 旧删除事件 → `TEXT_DISPLAY_REMOVE` / `REMOVE_HOLOGRAM`。

转换后的 JSON 仍然交给 3.0 运行时 `PlaybackSession` 解释，不会生成新的 Java 类。

`RhythMC-Reborn-master.zip` 中的旧 TextDisplay 管理器/效果类可作为行为参考，但不应直接当作 3.0 的编译入口；3.0 当前实现已经把相关处理集中到 `RhythmcMaker.java` 的 `PlaybackSession` 内。

## 与构建产物的关系

Gradle 编译产物包含：

- `RhythmcMaker.class` 及其内部类；
- `ChartStorage.class` 等 chart 处理类；
- Fabric 元数据和资源；
- 工程配置中 include 的依赖及相关运行库。

运行时加载 JAR 后，Fabric 通过 `fabric.mod.json` 启动入口；TextDisplay 实体是在服务器世界中由 Java 逻辑动态 `new` 并 `world.spawnEntity(display)` 生成的，不是从 JAR 中读取预生成的实体文件。

## 当前版本注意

历史 maker 记录 `maker+20261004-124500.md` 记录过 `1.5.39` 的部署，但当前工程 `gradle.properties` 已显示 `1.6.9`。分析当前源码和后续实现时，以工程当前文件为准；历史记录仅用于了解此前的转换修复。

## 相关文件

- `F:\b晴天小雨awa\RhythMC\rhythmc maker\build.gradle`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\gradle.properties`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\resources\fabric.mod.json`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\RhythmcMaker.java`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartStorage.java`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartManifest.java`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\ChartTiming.java`
- `F:\b晴天小雨awa\RhythMC\rhythmc maker\src\main\java\cn\frkovo\rhythmcmaker\chart\TrackPlayback.java`
