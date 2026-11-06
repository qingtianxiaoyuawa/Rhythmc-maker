# RhythMC 3.0 TextDisplay 播放与编译链路

- 日期：2026-10-04（Asia/Singapore）
- 身份：Planner；本轮只读取分析并写记录，不修改工程源码、不编译、不测试、不部署。
- 工程版本：源码 `gradle.properties` 当前为 `1.6.9`；Minecraft `1.21.11`、Yarn `1.21.11+build.6`、Java 21。maker 记录中的部署版本 `1.5.39` 是历史记录，不能作为当前源码版本。

## 先区分两个“编译”

### A. Java/Gradle 编译

`TEXT_DISPLAY` 播放逻辑没有独立的“TextDisplay 编译器”。它是普通 Java 源码的一部分，主要在 `RhythmcMaker.java` 内编译为类，再打进 Fabric mod JAR。主要涉及：

- `RhythmcMaker.java`：服务端播放会话、实体创建和变换处理；
- `ChartStorage.java`：导入旧版 2.0 谱面时转换 TEXT/HOLOGRAM/TRANSFORMATIONS 事件；
- `ChartManifest.java`：保存 chart effects JSON 数据模型；
- `ChartTiming.java`：Beat 到秒的时间映射；
- `TrackPlayback.java`：轨道位姿采样。

工程使用 Fabric Loom、Minecraft 1.21.11、Yarn build.6、Java 21。`build.gradle` 的 `JavaCompile` 使用 `options.release=21`。普通 `build` 负责编译/打包；工程另定义 `deploy` 任务，会先 bump patch 再 build。Planner 不执行这些任务。

### B. 谱面特效的运行时解释/编译

谱面 effects 以 JSON 事件保存，不会在 Gradle 阶段变成独立 Java 类。正式播放开始时，`PlaybackSession`：

1. 复制 `chart.effects`；
2. 按 `effectSeconds()` 排序；
3. 使用 `ChartTiming.beatToSeconds(beat) + chart.offsetMillis/1000` 计算事件时间；
4. 每 tick 根据单调时钟得到 songTime；
5. 正式模式下，当事件时间到达，就调用 `playTrackEffect()`；
6. `playTrackEffect()` 路由 `TEXT_DISPLAY*` / `HOLOGRAM*` 类型到 `applyTextEffect()`。

这是数据驱动的运行时解释过程，不是 Java 编译阶段执行。

## TEXT_DISPLAY 生命周期

### 1. 创建会话

播放开始构造 `PlaybackSession`，预处理 ChartTiming、TrackPlayback profiles、effects 与事件时间。会话持有：

- `textDisplays: Map<String, TextDisplayEntity>`：按 id 缓存实体；
- `textDisplayAnchors`：记录显示锚点；
- `textDisplayTracks`：同步轨道的实体映射；
- `textDisplayExpiresAtNanos`：到期时间。

### 2. 事件触发

`applyTextEffect()` 按 event type 分支：

- `TEXT_DISPLAY`：取 `text` 或 `content`；
- `HOLOGRAM`：取 `location` 和 `contents`，可拼成多行文本；
- `TEXT_DISPLAY_EFFECT`：更新既有实体的文字、透明度、背景、阴影、发光或线性变换；
- `TEXT_DISPLAY_SYNC_TRACK` / `TEXT_DISPLAY_DESYNC_TRACK`：绑定/解除轨道位姿更新；
- `TEXT_DISPLAY_REMOVE` / `REMOVE_HOLOGRAM`：移除实体并清理缓存。

普通显示实体应用 text、颜色、lineWidth、opacity、background、billboard 模式、position/rotation/scale、shadow、glowing，然后设置 duration 到期时间。

### 3. 实体创建

`createTextDisplay(id)`：

1. new `DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world)`；
2. 添加 `rhythmc_effect:<chartId>` 命令标签；
3. 设置初始位置为常量 `TEXT_DISPLAY_CENTER_*`；
4. 设置默认 transformation；
5. 设置初始 displayWidth/Height `0.1` 和透明度 0；
6. 设置 FIXED billboard 与 interpolation；
7. 缓存并 `world.spawnEntity(display)`。

随后 `setTextDisplayPose/Transform()` 再应用实际事件位置。当前基准是 `(200.5,66,-1)`；实际位置计算为基准点加事件相对位置。普通 TEXT_DISPLAY 缺 position 时默认 `(0,1.5,0)`；因此实体真实位置并不等于创建时的隐藏初始位置。

### 4. 持续更新和清理

播放 tick 在计算 songTime 后调用 `session.updateTextDisplays(songTime)`：

- 到期实体会 remove 并清除 anchor/track/expiry 状态；
- 绑定轨道的实体通过 `timing.secondsToBeat(songTime - offset)` 得到当前 beat；
- `TrackPlayback.poseAt(beat)` 采样位置、旋转、scale；
- 将采样结果传给 `setTextDisplayPose()` 更新实体。

## 2.0 导入转换

`ChartStorage.convertRhythmc2Effect()` 在导入阶段把旧字段转换为 3.0 事件 properties：

- 旧 `TEXT` → `TEXT_DISPLAY`，将 `content/loc` 等转为 `text/position/rotation/scale`；
- `HOLOGRAM` 转换 `location/contents/id/duration`；
- `TRANSFORMATIONS` → `TEXT_DISPLAY_EFFECT`，将 transformation type 与参数转换；
- 删除/移除类事件映射至对应 remove 类型。

这一步是谱面数据格式转换，不是游戏播放时的 Java 编译。

## 重要注意

- 滚动播放与正式播放不是同一语义。`processPlaybackSessions()` 对 `session.formal` 才推进 effects；`ScrollJudgementSession` 侧重音乐/判定滚动，不触发谱面特效。特效编辑器预览会显式使用自己的 `playEffectPreview()` 路径，需要按该路径检查它是否属于正式特效预览，而不能笼统说所有滚动会话都执行 effects。
- `TEXT_DISPLAY_SYNC_TRACK` 只对已有 id 的显示对象存轨道绑定；事件排序和同一 beat 的顺序会影响先创建显示还是先绑定轨道。
- 当前 `displayWidth/displayHeight` 使用由最大 scale 分量推导的相同 bounds，不是由文本像素宽高或场景判定框边界编译得出；它会影响显示实体边界与文字布局的关系。
- 单纯改变中心常量会影响所有走同一基点的普通显示、全息和相关变换；需检查旧谱面兼容及轨道动画位置语义。

## 本轮结论

如果用户说“RhythMC 3.0 源码中播放 TextDisplay 是如何编译的”，准确说法是：Gradle 编译 `RhythmcMaker.java` 等实现文件；谱面 TextDisplay JSON 在游戏服务端 `PlaybackSession` 中按 beat/time 到点后由 `applyTextEffect()` 解释成 `TextDisplayEntity`，并按 duration/track 在运行中更新和清理。二者不能混称为同一类“编译”。

## 相关源码

- `src/main/java/cn/frkovo/rhythmcmaker/RhythmcMaker.java`
- `src/main/java/cn/frkovo/rhythmcmaker/chart/ChartStorage.java`
- `src/main/java/cn/frkovo/rhythmcmaker/chart/ChartManifest.java`
- `src/main/java/cn/frkovo/rhythmcmaker/chart/ChartTiming.java`
- `src/main/java/cn/frkovo/rhythmcmaker/chart/TrackPlayback.java`
- `build.gradle`
- `gradle.properties`
