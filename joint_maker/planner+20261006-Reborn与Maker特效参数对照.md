# 2026-10-06 - Reborn 与 RhythMC Maker 特效参数对照

## 结论摘要

本次对照了：

- `RhythMC-Reborn-master.zip` 中的 `EffectType`、`EffectFactory`、各类 `EffectImpl`、TextDisplay 参数类；
- RhythMC Maker 当前的 `EffectParameterSchema`、参数编辑器、RhythMC 3.0 导出编解码和服务端预览播放逻辑。

核心结论：

1. **Maker 当前参数页面已经登记 Reborn 的 17 类效果类型。**
2. **Maker 不是完全缺少 Reborn 参数，而是存在字段语义、生命周期、运行时实现和导出兼容性差异。**
3. **最需要补齐的不是继续增加效果类型，而是补齐参数的严格 schema、运行时等价行为、seek/重建行为和导出验证。**
4. Reborn 的 `GLOWING` 参数类虽然存在，但源码中 `GlowingParams` 标记为“暂未实现”；Maker 目前反而实现了 TextDisplay glowing 开关，因此不能简单认为两边行为相同。

本记录只做 Planner 分析，不修改源码、不编译、不部署。

---

## 一、Reborn 的效果类型与可修改参数

Reborn `EffectType` 共包含以下 17 类效果：

```text
HOLOGRAM
REMOVE_HOLOGRAM
TITLE
FIREWORK
TIME
EFFECT
CLEAR_EFFECT
WEATHER
ARENA
TEXT_DISPLAY
TEXT_DISPLAY_EFFECT
TEXT_DISPLAY_SYNC_TRACK
TEXT_DISPLAY_DESYNC_TRACK
HIDE_NOTES
GLOW_COLOR
MESSAGE
TEXT_DISPLAY_REMOVE
```

所有效果都由一个事件时间字段 `beat` 触发；实际可选字段位于 `properties` 对象中。

### 1. `HOLOGRAM`

Reborn `EffectFactory` 读取：

| 参数 | 类型 | 作用 |
|---|---|---|
| `location` | 数组 `[x,y,z]` | 相对游戏中心的生成位置偏移 |
| `id` | 字符串，可选 | 全息文字 ID；省略时服务端生成临时 ID |
| `contents` | 字符串数组 | 全息文字内容，每个数组项是一行/一组内容 |
| `duration` | 长整数，可选 | 持续时间，单位毫秒；默认约一年 |

Maker 页面已登记：`location`、`contents`、`id`、`duration`。

### 2. `REMOVE_HOLOGRAM`

| 参数 | 类型 | 作用 |
|---|---|---|
| `id` | 字符串 | 删除此前创建的全息文字 ID |

Maker 页面已登记。

### 3. `TITLE`

| 参数 | 类型 | 作用 |
|---|---|---|
| `title` | 字符串/文本 | 主标题 |
| `subtitle` | 字符串/文本 | 副标题 |
| `fadeIn` | 整数 | 淡入时间，毫秒 |
| `stay` | 整数 | 停留时间，毫秒 |
| `fadeOut` | 整数 | 淡出时间，毫秒 |

Maker 页面已登记。Reborn 的标题参数本身没有额外 `duration` 字段，标题生命周期由 `fadeIn + stay + fadeOut` 控制。

### 4. `FIREWORK`

| 参数 | 类型 | 作用 |
|---|---|---|
| `locations` | 数组 `[ [x,y,z], ... ]` | 多个烟花发射位置，相对游戏中心偏移 |
| `colors` | RGB 数组列表 | 烟花初始颜色列表，每个分量 0–255 |
| `fadeColors` | RGB 数组列表 | 烟花渐变颜色列表 |
| `type` | 枚举 | `BALL`、`LARGE_BALL`、`STAR`、`BURST`、`CREEPER` |
| `flicker` | 布尔 | 是否闪烁 |
| `trail` | 布尔 | 是否拖尾 |

Maker 页面已登记全部字段。

### 5. `TIME`

| 参数 | 类型 | 作用 |
|---|---|---|
| `time` | 长整数 | 玩家世界时间，单位游戏刻 |
| `duration` | 长整数，可选 | 时间效果持续时间，单位毫秒；默认约一年 |

Maker 页面已登记。

### 6. `EFFECT`

| 参数 | 类型 | 作用 |
|---|---|---|
| `effectId` | 整数 | Bukkit 药水效果数字 ID |
| `amplifier` | 整数 | 药水效果等级，0 表示一级 |
| `duration` | 长整数，可选 | 持续时间，单位毫秒；默认约一年 |

Maker 页面已登记，但目前使用数字 ID，编辑体验和跨版本稳定性较差，见缺失项。

### 7. `CLEAR_EFFECT`

| 参数 | 类型 | 作用 |
|---|---|---|
| `effects` | 整数数组，可选 | 指定要清除的药水效果 ID；省略时清除全部 |

Maker 页面已登记，并区分了“省略”和“空数组”的语义。需要注意：Reborn 源码中 `effects` 缺失时为 `null`，空数组则是不清除任何效果，不能混淆。

### 8. `WEATHER`

| 参数 | 类型 | 作用 |
|---|---|---|
| `weather` | 枚举 | `CLEAR` 或 `DOWNFALL` |

Maker 页面已登记。

### 9. `ARENA`

| 参数 | 类型 | 作用 |
|---|---|---|
| `arena` | 字符串 | 场景/竞技场名称 |

Maker 页面已登记。Maker 使用 `arenaBindings` 将名称绑定到场景文件或场景编辑器产物；这不是 Reborn 原始 Arena 对象管理的完全同一实现。

### 10. `TEXT_DISPLAY`

Reborn 创建 TextDisplay 时读取：

| 参数 | 类型 | 作用 |
|---|---|---|
| `position` | 浮点数组 `[x,y,z]` | 相对 TextDisplay 默认生成中心的变换平移 |
| `rotation` | 浮点数组 `[rx,ry,rz]` | X/Y/Z 旋转，Reborn 直接进入矩阵 |
| `scale` | 浮点数组 `[sx,sy,sz]` | 三轴缩放 |
| `text` | 字符串 | 文本内容 |
| `id` | 字符串 | 展示实体逻辑 ID |

Reborn 的 `GpTextDisplay` 还固定/内部维护：

- Billboard：`FIXED`；
- 插值延迟：0；
- 插值时长：默认 1 tick；
- 默认文本不透明度：255；
- 默认背景：无；
- 默认阴影：关闭；
- 默认 glowing：内部 `glow = true`，但不是创建事件参数；
- 生成位置：`screenCenterLoc`，不是 `position` 本身。

Maker 页面已登记 5 个业务字段，但 Maker 额外允许/读取 `lineWidth`、`opacity`、`shadowed`、`glowing`、`background` 等扩展字段，必须区分 3.0 标准字段与 Maker 扩展字段。

### 11. `TEXT_DISPLAY_EFFECT`

Reborn 使用 `type` 选择一类修改操作：

| `type` | 参数 | 作用 |
|---|---|---|
| `TEXT` | `id`、`text` | 修改文本内容 |
| `SHADOW` | `id`、`shadowed` | 开关文字阴影 |
| `OPACITY` | `id`、`targetOpacity` | 修改文字不透明度，0–255 |
| `BACKGROUND_COLOR` | `id`、`color=[r,g,b,a]` | 修改背景 RGBA |
| `LINEAR_TRANSFORMATION` | `id`、`position`、`rotation`、`scale` | 修改平移、旋转、缩放 |
| `GLOWING` | `id` | 修改发光状态的意图参数；Reborn 的 `GlowingParams` 当前标记为暂未实现 |

Maker 页面已登记以上分支字段。

### 12. `TEXT_DISPLAY_SYNC_TRACK`

| 参数 | 类型 | 作用 |
|---|---|---|
| `id` | 字符串 | 目标 TextDisplay ID |
| `track` | 整数 | 跟随的轨道 ID |
| `duration` | 长整数，可选 | 跟随持续时间，单位毫秒；默认约一年 |

Reborn 的运行时每次更新会根据当前 Beat 读取轨道对象的：

- X/Y/Z 位移；
- X/Y/Z 旋转；
- X/Y/Z 缩放。

Maker 页面已登记以上字段，Maker 运行时也维护 TextDisplay 与轨道绑定，但需要重点验证矩阵顺序和轨道时钟是否与 Reborn 相同。

### 13. `TEXT_DISPLAY_DESYNC_TRACK`

| 参数 | 类型 | 作用 |
|---|---|---|
| `id` | 字符串 | 取消该 TextDisplay 的轨道绑定 |

Maker 页面已登记。

### 14. `HIDE_NOTES`

| 参数 | 类型 | 作用 |
|---|---|---|
| `noteTypes` | 字符串数组，可选 | 要隐藏的音符类型：`TAP`、`LOOK`、`HOLD`、`DODGE` |
| `tracks` | 整数数组，可选 | 要隐藏的轨道 ID |

Reborn 语义：

- 缺失字段表示不指定该过滤条件；
- 空数组用于恢复/不隐藏对应集合，具体需要结合 EffectManager 状态更新；
- 强制移除时恢复显示全部。

Maker 页面已登记。

### 15. `GLOW_COLOR`

| 参数 | 类型 | 作用 |
|---|---|---|
| `color` | Minecraft 颜色枚举 | 修改音符发光颜色 |

支持：`BLACK`、`DARK_BLUE`、`DARK_GREEN`、`DARK_AQUA`、`DARK_RED`、`DARK_PURPLE`、`GOLD`、`GRAY`、`DARK_GRAY`、`BLUE`、`GREEN`、`AQUA`、`RED`、`LIGHT_PURPLE`、`YELLOW`、`WHITE`。

Maker 页面已登记全部颜色。

### 16. `MESSAGE`

| 参数 | 类型 | 作用 |
|---|---|---|
| `contents` | 字符串数组 | 向玩家发送一条或多条聊天消息 |

Maker 页面已登记。

### 17. `TEXT_DISPLAY_REMOVE`

| 参数 | 类型 | 作用 |
|---|---|---|
| `id` | 字符串 | 移除指定 TextDisplay |

Maker 页面已登记。

---

## 二、Maker 当前页面已覆盖情况

Maker `EffectParameterSchema` 当前已经登记：

- `HOLOGRAM`
- `REMOVE_HOLOGRAM`
- `TITLE`
- `FIREWORK`
- `TIME`
- `EFFECT`
- `CLEAR_EFFECT`
- `WEATHER`
- `ARENA`
- `HIDE_NOTES`
- `GLOW_COLOR`
- `MESSAGE`
- `TEXT_DISPLAY`
- `TEXT_DISPLAY_EFFECT`
- `TEXT_DISPLAY_SYNC_TRACK`
- `TEXT_DISPLAY_DESYNC_TRACK`
- `TEXT_DISPLAY_REMOVE`

因此，从“下拉框/参数字段是否登记”的角度，Maker **没有缺少 Reborn 的效果类型**。

Maker 页面已经提供以下控制类型：

- 文本和多行文本；
- 整数和长整数；
- 小数；
- 布尔值；
- 枚举；
- 三维向量；
- 三维向量列表；
- RGB 列表；
- RGBA；
- 字符串列表；
- 整数列表；
- 音符类型选择。

---

## 三、Maker 目前缺失或不完整的部分

### A. 参数页面层面的缺失/不足

#### A.1 缺少基于 Reborn 实际参数契约的严格分组

当前页面只是按照字段 schema 展示输入框，但没有把以下概念完全分开：

- Reborn 官方字段；
- Maker 扩展字段；
- 仅用于预览的字段；
- 仅用于导出的字段；
- 运行时未实现的字段。

建议每个字段显示来源标签：

```text
Reborn 官方
Maker 扩展
仅预览
导出兼容
```

#### A.2 `EFFECT` 仍使用数字药水 ID

Reborn 使用 `PotionEffectType.getById(effectId)`，Maker 页面只提供数字输入。

缺点：

- 用户难以知道数字对应的效果名称；
- 不同 Minecraft/Bukkit 版本的数字 ID 兼容性风险高；
- 输入非法数字时只能在验证阶段发现。

规划建议：

- 页面显示“效果名称 + 数字 ID”；
- 内部仍保存 Reborn 所需的 `effectId`；
- 根据目标版本生成 ID 映射；
- 对不存在的 ID 提供错误而不是静默导出。

#### A.3 `TEXT_DISPLAY_EFFECT` 的 `GLOWING` 参数语义不完整

Maker 页面当前分支登记的是 `GLOWING`，但 Reborn 的 `GlowingParams` 没有字段；Maker 运行时却读取：

```text
glowing
```

因此页面需要明确选择一种兼容策略：

1. 严格 Reborn 模式：`GLOWING` 不显示额外值，并按 Reborn 当前实现处理；
2. Maker 扩展模式：增加 `glowing=true/false`，导出时标记为 Maker 扩展；
3. 兼容模式：同时支持省略字段默认为 true，并在导出时给出提示。

不能把 Maker 的扩展布尔值直接当作 Reborn 原始字段而不提示。

#### A.4 TextDisplay 基础属性没有全部显式建模

Maker 运行时已经读取/设置部分字段，但 `TEXT_DISPLAY` 的正式 schema 主要只有：

- `position`
- `rotation`
- `scale`
- `text`
- `id`

而 Maker 运行时还处理：

- `lineWidth`
- `opacity`
- `shadowed`
- `glowing`
- `background`
- `duration`
- `displayWidth`
- `displayHeight`
- Billboard/插值相关状态

这些字段目前没有全部作为正式参数出现在基础 `TEXT_DISPLAY` 页面中，容易出现“原始 JSON 有字段，但 UI 无法回填或修改”的问题。

需要明确：

- 哪些是 Reborn 原始能力；
- 哪些是 Maker 扩展；
- 哪些只应该放在高级/兼容面板；
- 哪些字段必须参与导出校验。

#### A.5 缺少颜色输入的统一语义提示

Maker 同时存在：

- RGB 列表；
- RGBA 数组；
- Minecraft 颜色枚举；
- 数字颜色/十六进制颜色兼容读取。

Reborn 的颜色语义是严格区分的：

- 烟花颜色是 RGB 列表；
- TextDisplay 背景是 RGBA；
- 音符发光是 Minecraft `ChatColor` 枚举。

页面需要禁止把 ARGB、RGBA、RGB 和十六进制颜色混用。

#### A.6 缺少“缺失字段”和“空数组”的显式控制

至少以下字段存在缺失/空数组语义差异：

- `CLEAR_EFFECT.effects`：缺失=清除全部，空数组=不清除任何效果；
- `HIDE_NOTES.noteTypes`；
- `HIDE_NOTES.tracks`；
- `FIREWORK.fadeColors`；
- `HOLOGRAM.id`。

当前可选字段虽然能够表达部分情况，但页面需要明确提供：

```text
未设置
空数组
显式填写
```

否则 JSON 序列化时可能将三种语义压成一种。

#### A.7 缺少字段依赖和目标 ID 引用检查

以下关系必须在保存前校验：

- `TEXT_DISPLAY_EFFECT.id` 必须引用已创建或可能已创建的 TextDisplay；
- `TEXT_DISPLAY_SYNC_TRACK.id` 必须引用目标 TextDisplay；
- `TEXT_DISPLAY_DESYNC_TRACK.id` 必须引用目标 TextDisplay；
- `TEXT_DISPLAY_REMOVE.id` 必须引用目标 TextDisplay；
- `REMOVE_HOLOGRAM.id` 必须引用此前的 Hologram ID；
- `track` 必须存在于谱面轨道布局。

当前页面主要是字段级校验，缺少跨事件、跨轨道和跨生命周期校验。

### B. 运行时/播放层面的不完整或潜在不等价

#### B.1 Maker 的 Hologram 使用 TextDisplay 代理，不是 Reborn Hologram 生命周期

Maker 的 `playTrackEffect` 将：

```text
HOLOGRAM / REMOVE_HOLOGRAM
```

统一送入 `session.applyTextEffect(...)`，内部使用 Minecraft TextDisplay 对象池处理。

Reborn 则使用独立的 HologramEffect / HologramManager 生命周期。

因此可能出现：

- 默认位置偏移不同；
- 文本对齐/宽度/背景/阴影不同；
- 生成、隐藏、释放、持续时间不同；
- 删除 ID 的行为不同；
- seek 后重建结果不同。

这不是页面字段缺失，而是播放实现差异，必须单独兼容。

#### B.2 TextDisplay 默认中心和坐标语义尚未完全统一

当前 Maker 已确认默认播放/判定中心是：

```text
(200.5, 66.0, -1.0)
```

规划中不能把玩家预览锚点、TextDisplay 实体生成点、局部 `position` 和 Reborn `screenCenterLoc` 混为同一坐标。

需要固定坐标分层：

```text
最终世界坐标
= Maker 播放场景原点
+ TextDisplay/谱面局部 position
+ 轨道变换
+ 特效变换
```

并明确 Maker 当前源码中任何 `-1.5` 或其它旧 Z 值是否为实际局部偏移，避免与用户确认的 `Z=-1.0` 默认中心冲突。

#### B.3 TextDisplay 变换矩阵需要逐项对齐

Reborn `GpTextDisplay.render()` 的矩阵顺序是：

```text
translate
→ rotateXYZ
→ scale
```

Maker 也执行平移、旋转、缩放，但其服务端实体更新、轨道姿态、缓存姿态和插值状态是另一套实现。

需要验证：

- 旋转单位是否都为弧度；
- 旋转轴方向是否一致；
- 轨道变换是在局部坐标前还是后应用；
- scale 是否会影响平移；
- seek 后矩阵是否从目标 Beat 完整重算；
- 插值时长是否造成视觉滞后。

#### B.4 `TEXT_DISPLAY_SYNC_TRACK` 的持续时间和更新时钟需要统一

Reborn 通过 `update(curBeat)` 持续更新轨道姿态。

Maker 使用 `songTime → beat → TrackPlayback.Pose` 更新 TextDisplay，并用纳秒过期时间管理 duration。

这会带来：

- BPM/offset 换算差异；
- seek 后轨道姿态滞后一帧或继续沿用旧姿态；
- duration 结束时 TextDisplay 是否只解绑轨道，还是同时隐藏/释放；
- 插值造成实体位置落后于轨道逻辑位置。

这属于深层播放兼容问题，不是 UI 参数问题。

#### B.5 Reborn 的 `GLOWING` 当前也不是完整可用功能

Reborn 的 `GlowingParams` 是空 record，并明确标注“暂未实现”。因此：

- Maker UI 有 `GLOWING` 不代表 Reborn 实际游玩一定改变发光；
- Maker 如果将 `glowing` 真正应用到 TextDisplay，可能比 Reborn 多出视觉效果；
- 为保证兼容，应在导出/预览中区分“Reborn 事实行为”和“Maker 扩展行为”。

#### B.6 音符隐藏和发光颜色需要恢复状态一致

Reborn 的 `HIDE_NOTES`、`GLOW_COLOR` 通过 EffectManager 保存全局状态，并在强制清理时恢复默认。

Maker 也有对应状态，但需要验证：

- 特效结束时是否恢复原隐藏集合；
- seek 到中间 Beat 时是否从头重放所有状态型效果；
- 关闭编辑器时是否清除预览状态；
- 多个同类事件叠加时是替换、合并还是覆盖；
- 正式播放与预览是否共用同一状态容器。

### C. Maker 额外存在、Reborn 没有的内容

Maker 不是单纯缺失，也有超出 Reborn 的扩展：

- `ACTIONBAR`（Maker 明确标记为非官方 3.0 特效）；
- TextDisplay 的 `lineWidth`；
- TextDisplay 的 `opacity`、`shadowed`、`glowing` 等直接属性读取；
- 更复杂的场景编辑器绑定和 `ARENA` 文件映射；
- 编辑器布局、轨道预览、seek 和本地预览状态字段。

这些扩展不能直接写入 Reborn 官方格式后宣称完全兼容，必须在导出时：

- 删除或转换为等价官方字段；或
- 标记为 Maker 扩展并阻止导出到严格 3.0；或
- 提示用户实际游玩可能出现差异。

---

## 四、优先级排序

### P0：必须优先修复/明确

1. 统一 Reborn/Maker 的 TextDisplay 世界中心、局部坐标和轨道矩阵层级。
2. 建立统一 `ChartTiming`，让效果触发、轨道跟随、duration、seek 和音频共用同一时钟。
3. seek 时从目标 Beat 重建所有状态型效果：TextDisplay、轨道绑定、隐藏音符、发光颜色、天气、时间、药水等。
4. 关闭编辑器时完整清理 TextDisplay/Hologram 代理实体、轨道绑定和状态缓存。
5. 对 `TEXT_DISPLAY`、`TEXT_DISPLAY_EFFECT`、`TEXT_DISPLAY_SYNC_TRACK` 增加 ID/轨道引用和生命周期校验。

### P1：参数页面补齐

1. 增加“Reborn 官方 / Maker 扩展 / 仅预览”标签。
2. `EFFECT.effectId` 增加药水名称选择和数字 ID 兼容显示。
3. 显式区分“未设置、空数组、显式值”。
4. 为 TextDisplay 增加高级属性面板，并标识哪些字段不能导出到严格 Reborn 3.0。
5. 统一 RGB、RGBA、Minecraft 颜色枚举和十六进制颜色输入组件。
6. 增加跨事件 ID、轨道 ID 和场景名称检查。

### P2：一致性与体验

1. 对 `GLOWING` 按 Reborn 实际实现状态显示兼容提示。
2. 对所有效果显示默认持续时间和单位。
3. 显示标题、烟花、消息等一次性效果与持续效果的生命周期说明。
4. 增加“按 Reborn 3.0 兼容模式校验”和“Maker 扩展模式校验”。
5. 导出前生成差异报告：字段差异、运行时差异、无法转换项。

---

## 五、建议的参数页面结构

### 基础信息

- 事件类型；
- 事件 Beat/Chunk 位置；
- 是否一次性效果；
- 是否持续效果；
- 持续时间；
- 兼容目标：`Reborn 3.0` / `Maker 扩展`。

### 内容与目标

- 文本内容；
- 主标题/副标题；
- Hologram/TextDisplay ID；
- 轨道 ID；
- 场景名称；
- 音符类型/轨道筛选。

### 空间与变换

- 位置 `[x,y,z]`；
- 旋转 `[rx,ry,rz]`；
- 缩放 `[sx,sy,sz]`；
- 坐标参考系说明：播放中心、TextDisplay 锚点或轨道局部空间。

### 视觉属性

- 文本颜色；
- 背景 RGBA；
- 不透明度；
- 阴影；
- 发光；
- 行宽/显示宽高；
- 插值/平滑方式。

### 导出检查

- 字段是否为 Reborn 官方字段；
- 是否含 Maker 扩展；
- 是否存在未知 ID/轨道/场景；
- 是否存在缺失字段与空数组语义冲突；
- 是否可在 seek 后完整恢复。

---

## 六、最终回答用户问题

### 1）Reborn 中所有可修改的特效参数

已完整覆盖 17 类效果及其字段：

- 全息文字：位置、ID、内容、持续时间；
- 删除全息文字：ID；
- 标题：主标题、副标题、淡入、停留、淡出；
- 烟花：位置列表、初始颜色、渐变颜色、形状、闪烁、拖尾；
- 世界时间：时间、持续时间；
- 药水效果：效果 ID、等级、持续时间；
- 清除药水效果：指定效果 ID 列表；
- 天气：晴天/降雨；
- 场景：场景名称；
- 文本展示实体：位置、旋转、缩放、文本、ID；
- 文本展示实体修改：文本、阴影、不透明度、背景 RGBA、线性变换、发光意图；
- 文本展示实体跟随轨道：ID、轨道 ID、持续时间；
- 取消轨道跟随：ID；
- 隐藏音符：音符类型列表、轨道列表；
- 音符发光颜色：Minecraft 颜色枚举；
- 聊天消息：消息内容列表；
- 移除文本展示实体：ID。

### 2）Maker 当前缺失的部分

严格来说，Maker 当前页面**没有缺少 Reborn 的效果类型或基础字段**；缺失主要集中在：

- 官方字段与 Maker 扩展字段没有分层；
- TextDisplay 高级属性未全部进入正式参数 UI；
- 药水数字 ID 缺少名称映射和版本校验；
- 缺失/空数组语义没有完整可视化；
- 跨事件 ID、轨道和场景引用校验不足；
- Hologram 使用 TextDisplay 代理，生命周期不等于 Reborn；
- TextDisplay 默认中心、矩阵顺序、轨道更新和插值行为未完成逐项统一；
- seek/关闭时状态型特效的重建与清理仍需深度验证；
- `GLOWING` 的 Maker 实现可能比 Reborn 实际实现多出视觉效果；
- 缺少严格的“Reborn 3.0 兼容校验”和导出差异报告。

因此后续不建议只继续往参数页面增加输入框，应优先建立“参数 schema → 播放运行时 → seek 重建 → Reborn 导出”的单一契约。

## 七、重要坐标矛盾补充

扫描 Maker 当前源码时发现：`RhythmcMaker.java` 仍存在类似以下常量：

```text
TEXT_DISPLAY_CENTER_X = 200.5
TEXT_DISPLAY_CENTER_Y = 66.0
TEXT_DISPLAY_CENTER_Z = -1.5
```

但用户已确认 Maker 当前默认播放/判定框中心为：

```text
(200.5, 66.0, -1.0)
```

因此 `Z=-1.5` 是必须重点排查的旧值或不同语义坐标，不能直接认为它等于当前默认中心。后续应确认它究竟表示：

- TextDisplay 实体生成锚点；
- 文字几何中心修正；
- 判定框局部偏移；
- 旧版本遗留常量。

在未完成语义确认前，不得直接把 `-1.5` 改成 `-1.0`，也不得把二者同时叠加。验收时必须分别测量实体紫色边界、文本可见中心、判定框中心和正式播放视角。
