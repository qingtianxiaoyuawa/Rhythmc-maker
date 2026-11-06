# 2026-10-18 - Reborn 歌曲、谱面与场景存取/调用链

## 范围与来源

- 依据用户提供的 `G:\BaiduNetdiskDownload\RhythMC-Reborn-master (1).zip` 源码快照进行静态阅读。
- 重点追踪 `Main`、`ConfigManager`、`RawSongDeserializer`、`RawLevelDeserializer`、`FileUtils`、`ChartManager`、`ArenaManager`、`GameManager`、`GameInstance`、`ArenaInstance`、选歌命令/菜单及相关格式类。
- 本次只分析，不修改 Maker/Reborn 源码、不编译、不运行。
- 该 ZIP 的文件时间为 2026-10-06；以下结论针对该 ZIP，不等同于之后上游提交。

## 一句话结论

Reborn 这份源码把三类数据分开管理：

1. **歌曲（Song）**：`manifest.yml` 保存歌曲级元数据、资源包/解锁/分类信息；
2. **难度谱面（RawLevel）**：每个维度/难度一个 `.rmcc` 文件，内部是 JSON（优先按 Zstandard 解压，失败再按普通 JSON 读）；
3. **场景（Arena）**：`Data/Arenas/<arena>/metadata.yml` 描述场景信息，旁边的 `arena.schem`（或指定 schematic 文件）存 WorldEdit 剪贴板结构。

启动时它们被反序列化到内存并注册。游戏开始时选择一个 RawLevel，创建 GameInstance，读取谱面元数据中的初始 Arena 或游戏选项默认 Arena，再把 Arena schematic 粘贴到 ArenaManager 分配的运行时位置。歌曲文件的保存/编辑不是这条加载链路的一部分；在本快照中没有找到把 RawSong/RawLevel/Arena 序列化回这些本地文件的对称保存器。

---

## 一、歌曲如何组织和保存

### 1. 目录布局

服务端数据目录下的固定目录为：

```text
<插件数据目录>/Data/Charts/<歌曲目录>/
    manifest.yml
    world.rmcc     可选难度槽
    nether.rmcc    可选难度槽
    end.rmcc       可选难度槽
    void.rmcc      可选难度槽
```

`ConfigManager` 使用 `Data/Charts`，扫描它的直接子目录。只有子目录中有 `manifest.yml` 才会尝试加载歌曲。

### 2. `manifest.yml`（歌曲级数据）

`SongManifestDeserializer` 读取的核心字段：

- `name`：歌曲名称；
- `composer`：作曲者；
- `icon`：菜单图标；
- `alias`：别名；
- `length`：歌曲长度；
- `respack_sha1`：资源包 SHA-1；
- `description`：描述；
- `song_id`：歌曲 ID；
- `version`：歌曲/包版本；
- `comments`、`player-alias`、`tags`；
- `unlockSong`、`unlockWorld`、`unlockNether`、`unlockVoid`：歌曲和难度解锁定义。

代码由 `song_id` 构建声音资源 key：

```text
music:s<song_id>
```

示例：`song_id: 10001` 对应 `music:s10001`。

### 3. “保存”与“加载”边界

本地 `ConfigManager`/Deserializer 实现的是**读取**：扫描目录、读 YAML/JSON、构造不可变 record、注册到 manager。源码搜索没有发现对应的 `RawSongSerializer`、谱面写回器或 Arena metadata/schematic 保存器。

因此对这份源码而言，歌曲包通常是由外部制谱/打包/同步流程产出后放入 `Data/Charts`；Reborn 在启动时消费这些文件。`manifest.yml` 里的资源包 SHA 和歌曲 ID 是资源包关联元数据，不是把音频二进制写进 `.rmcc`。

ZIP 内的 `[level-uuid]` 只带有 `manifest.yml` 和 `end.rmcc` 的小型测试目录，不是完整真实歌曲库样本。

---

## 二、谱面文件保存结构与解析

### 1. 一个歌曲对应多个 RawLevel

`RawSong` 是聚合对象：

```text
RawSong = SongManifest
        + world RawLevel?
        + nether RawLevel?
        + end RawLevel?
        + void RawLevel?
        + song unlock method
```

`RawSongDeserializer` 固定尝试读取：

- `world.rmcc`；
- `nether.rmcc`；
- `end.rmcc`；
- `void.rmcc`。

文件缺失时对应难度为 null。若所有有效难度都未加载，则整首歌曲跳过，不注册。

### 2. `.rmcc` 编码

`FileUtils.readJson(file)` 的读取顺序：

1. 尝试用 `ZstdInputStream` 解压并解析 JSON object；
2. 若失败，再尝试把文件当普通 UTF-8 JSON 解析。

所以 `.rmcc` 是文件扩展名/容器约定，不代表其内容必然只有一种编码；该读入器明确兼容 Zstandard JSON 和普通 JSON。

### 3. `.rmcc` 的顶层结构

`RawLevelDeserializer` 读取：

```json
{
  "meta": { ... },
  "tracks": [ ... ],
  "effects": [ ... ]
}
```

`meta` 缺失或无法解析时 RawLevel 加载失败；`tracks`/`effects` 缺失时记录 warning 并按空数组继续。

#### `meta`

`MetaDeserializer` 读取：

- `uid`：难度/谱面 ID；
- `initialArena`：Arena 名称，按名字向 `ArenaManager` 查找；
- `offset`：音频偏移，毫秒；
- `level`：难度数值；
- `charters`：谱师；
- `comments`：谱面备注；
- `bpms`：BPM 变化数组，每项含 `beat`、`bpm`。

难度显示字符串由 `level` 计算生成，不是单独持久化字段。

#### `tracks`

每条轨道由 `Track` record 表示，含：

- `id`；
- 速度事件 `speedEvents`；
- X/Y/Z 位移事件；
- X/Y/Z 旋转事件；
- X/Y/Z 缩放事件；
- `notes` 音符数组。

数值事件由 `NumEvent` 表示，字段为 `startBeat`、`endBeat`、`startValue`、`endValue`、`easing`。轨道反序列化还会解析其 notes。

#### `effects`

每项至少包含：

- `effectType`：必须能匹配 `EffectType` 枚举；
- `beat`：事件触发拍；
- `properties`：特效参数 object，缺失时会替换为空 object。

未知效果类型或单项解析失败会 warning 并跳过该项；不会因此必然丢弃整个谱面。

---

## 三、场景（Arena）如何保存

### 1. 目录结构

固定目录为：

```text
<插件数据目录>/Data/Arenas/<场景目录>/
    metadata.yml
    arena.schem       默认文件名，可由 metadata 的 schematic 字段覆盖
```

Arena 目录名用于缺少配置时的默认名称/错误上下文。Arena 元数据由 `ArenaDeserializer` 读取。

### 2. `metadata.yml` 字段

源码读取字段包括：

- `name`：内部名称，默认目录名并转小写；
- `display-name`：显示名称；
- `author`；
- `description`；
- `icon`：默认草方块；
- `border`：默认青色混凝土；
- `success`：默认绿色混凝土；
- `schematic`：结构文件路径，默认 `arena.schem`；
- `hide`：是否隐藏；
- `unlock-method` 或兼容旧键 `unlockMethod`：解锁规则。

WorldEdit 根据文件格式读取 schematic 成 `Clipboard`，再放到内存中的 `Arena` 对象。该 record 持有元数据、clipboard、隐藏标志和解锁方法。

### 3. Arena 模板与运行时实例不是同一件事

ArenaManager 注册的是模板 Arena（含 clipboard）。每局游戏的 `ArenaInstance` 另有：

- 由 ArenaManager 分配的 `arenaCenter`；
- WorldEdit 目标 world（ArenaManager 管理的场景世界）；
- 当前使用的 Arena；
- 最近粘贴的区域。

`ArenaInstance.generate(arena)` 会先对上次粘贴区域执行清理，再将 clipboard 原点平移到当前 `arenaCenter` 并粘贴；`destroy()` 清理该实例区域。初始场景由谱面 `meta.initialArena` 优先决定，否则用 `GameOptions.defaultArena()`。

重要：`initialArena` 存的是 Arena 名称引用，场景结构本身保存在 `Data/Arenas` 的 schematic 文件，不嵌在 `.rmcc` 中。

---

## 四、启动时如何加载并注册

`Main.onEnable()` 的关键阶段：

1. 创建 `ConfigManager`；
2. 建立 Arena 世界；
3. 创建 `ArenaManager`、`ChartManager` 等核心 manager；
4. 网络初始化；
5. 调用 `configManager.initData()`；
6. 建立 GameManager、GUI、SongSelectionManager 等运行时管理器。

`initData()` 先 `initArena()`，再 `initCharts()`：

```text
Data/Arenas/*/metadata.yml + schematic
  -> ArenaDeserializer
  -> ArenaManager.registerArena

Data/Charts/*/manifest.yml + *.rmcc
  -> RawSongDeserializer
  -> SongManifestDeserializer / RawLevelDeserializer
  -> ChartManager.registerChart
```

先加载 Arena 再加载 Charts 很关键：`MetaDeserializer` 解析 `initialArena` 时会直接调用 `ArenaManager.getArena(name)`；若场景未注册，谱面引用将解析不到对应 Arena。

若启用 `ActiveChartCatalog`，加载 `.rmcc` 前还会按谱面 `meta.uid` 过滤，非活动目录中的难度可以跳过。

---

## 五、玩家如何调用歌曲/谱面

### 1. 选歌阶段

常见入口是 `/start`：

1. `StartCommand` 通过 `SongSelectionManager.open(...)` 打开选歌 UI；
2. 用户在 `SongSelectionMenu` 选择歌曲/难度；
3. 菜单返回 `SongSelectionResult`（包含选中的 `RawLevel` 和自动游玩选项）；
4. `StartCommand` 校验结果和难度解锁；
5. 调用 `GameManager.createGame(rhyPlayer, options, rawLevel)`。

其它入口（派对、对战、自动选歌）也会将一个已经解析好的 `RawLevel` 交给 GameManager 的相应创建方法。

### 2. 游戏实例准备

`GameManager.createGame`：

- 将玩家从大厅移除；
- 向 ArenaManager 请求空闲游戏位置；
- 创建 `GameInstance`；
- 登记玩家与实例映射。

`GameInstance.init(rawLevel, lifecycleVersion)`：

1. `new Level(rawLevel, this)` 将 RawLevel 转成运行时 Level/NoteObject/TrackObject 等；
2. 选择 `meta.initialArena` 或 `options.defaultArena`，调用 `arenaInstance.generate(...)`；
3. 创建 EffectManager、判定、统计等运行时 manager；
4. 通过 ResourcePackManager 按 `manifest.songId` 请求对应资源包；
5. 资源包处理完成后继续准备和开始游戏。

### 3. 音乐调用

`SongManifestDeserializer` 将 `song_id` 转为 NamespacedKey：

```text
music:s<song_id>
```

`GameInstance` 在预备拍/开始游戏的调度任务中调用：

```text
player.playSound(player.getLocation(), manifest.key(), playerVolume, 1.0f)
```

所以音乐文件并不从 `.rmcc` 内读取；音频资源由资源包提供，谱面 `offset`/BPM 等数据与该声音 key 对应。源码中保留一段旧的 `rhythmc:song.s<song_id>` 客户端缓存调用注释，但当前实际播放用的是 manifest 派生的 `music:s<song_id>` key。

### 4. 谱面事件调用

`Level`/`GameInstance` 的游戏循环依照 `elapsedMillis`、BPM 和谱面数据推进：

- 根据 track 数值事件计算轨道速度、位移、旋转、缩放；
- 推进并渲染轨道音符；
- 通过判定 manager 处理输入；
- EffectManager 根据 effect beat 激活效果；
- 特效可进一步修改 TextDisplay、音符显示、世界时间/天气或 Arena。

### 5. 游戏中的 Arena 切换

除了开局 `meta.initialArena`，谱面还可用 `ARENA` effect 按名称查找模板，并让当前 `ArenaInstance` 重新 `generate(arena)`。`ChangeArenaEffect` 负责调度这一调用。由于 `generate` 先清理上次粘贴区域再粘贴新 clipboard，场景切换复用同一个游戏实例的位置和 ArenaInstance。

---

## 六、最容易混淆的概念

1. `RawSong` 是一首歌曲和多难度槽的容器；`RawLevel` 才是本局实际被选中的难度谱面。
2. `manifest.yml` 是歌曲级信息；`.rmcc` 是难度级节奏数据；两者不是同一个 JSON/YAML 文件。
3. Arena 名称引用存在 `meta.initialArena` 或 ARENA effect 中；Arena 几何结构是单独的 WorldEdit schematic。
4. ArenaManager 的模板/clipboard 是共享定义；ArenaInstance 是每局分配位置后生成的临时场景副本。
5. `.rmcc` 不是音频文件；音乐由 `song_id -> music:s<song_id> -> ResourcePack` 链路提供。
6. 这份源码明确实现本地数据读取和运行时实例化；没有找到对本地 Chart/Arena 数据的对称写回器。网络目录同步和资源包下载是另一条数据分发链路，不能说成 `.rmcc` 本地保存流程。

## 七、给 Maker 对接时可借鉴的数据边界

建议 Maker 若要兼容 Reborn，保持三种实体模型分离：

```text
SongPackage
  ├─ manifest metadata + resource key/id
  ├─ DifficultyChart[] (.rmcc-compatible payload)
  └─ external asset reference (resource pack)

ArenaAsset
  ├─ metadata
  └─ schematic/scene file

ChartLevel.meta.initialArena -> ArenaAsset.name
```

不要把 Arena schematic 塞进谱面 JSON；不要把音频文件路径和谱面局部 note/effect 混为同一字段；导出时应分别校验歌曲资源 ID、难度 uid、Arena 名称引用和外部资源包是否存在。

## 八、证据边界

- **已由所给源码确认**：固定数据目录、文件名、YAML/JSON 字段、Zstandard + plain JSON 读取回退、Arena clipboard 加载与粘贴、初始 Arena 选择、游戏实例创建、manifest 音乐 key。
- **未确认**：Reborn 上游实际部署服务器中的目录完整性；远程后端如何生成/发布 `Data/Charts` 和资源包；真实音频资源包内部文件路径与 pack 构建脚本；外部制谱工具的写入格式细节。
- ZIP 自带数据样本不完整，不能凭样本证明完整歌曲文件集合；字段结构来自 Java deserializer/record。
