# 2026-10-04 - PR4 冲突补充：initialPosition 语义确认

## 用户确认
`initialPosition` 不是 TextDisplay 的局部坐标，也不是特效自身的初始变换位置。
它表示玩家右键正式播放后，被传送到的播放平台位置，具体为玩家正式播放传送坐标的 `Y + 0.5`。

当前正式播放传送逻辑对应：
- X：`PLAYBACK_PLATFORM_X + 0.5`
- Y：`PLAYBACK_PLATFORM_Y + 1.0`
- Z：`PLAYBACK_PLATFORM_Z + 0.5`
- 玩家视角：yaw `180.0f`、pitch `0.0f`

因此，如果 PR 中的 `initialPosition` 取自玩家右键正式播放后的传送点，并在 TextDisplay 创建时使用它，它代表的是“预览场景世界坐标基点/玩家播放锚点”，不能按普通 TextDisplay 局部平移处理。

## 对之前冲突判断的修正
之前“需要确认 initialPosition 是否和矩阵局部平移重复叠加”的表述需要收窄：
- `initialPosition` 本身不是局部变换参数；
- 它应作为预览实体的世界坐标生成基点，或作为预览场景中心传入；
- TextDisplay 的 `position`、轨道位移和线性变换仍然是相对于该场景基点的局部/谱面坐标；
- 合并时真正需要防止的是：将“玩家播放传送点”再次当成局部 `position` 加入矩阵，导致 TextDisplay 世界坐标重复偏移。

## 合并建议修正
1. 保留主线的 TextDisplay 创建、复用、释放和状态重置生命周期。
2. 将 PR 的 `initialPosition` 作为预览场景世界基点使用，而不是删除。
3. 通过统一的 `EffectPreviewSceneOrigin` 或等价语义，将：
   - 玩家正式播放传送点；
   - TextDisplay 默认世界生成点；
   - 音符/轨道预览原点；
   - 特效局部坐标
   明确分层。
4. TextDisplay 创建时，世界位置应使用正式播放场景的基点；事件中的局部 `position` 只进入一次变换矩阵或世界坐标换算。
5. 关闭预览时仍需恢复玩家右键前的位置和朝向；不能把播放平台位置当作恢复位置。

## 修正后的冲突结论
截图中的冲突本质仍是两类逻辑叠加：
- PR：正式播放场景基点、预览相机/玩家跟踪和显示范围；
- 主线：TextDisplay 生命周期、对象池和矩阵状态管理。

但不应把 `initialPosition` 归类为“TextDisplay 初始局部姿态”。正确合并方向是：
- 主线负责对象生命周期和矩阵状态；
- PR 负责预览场景世界基点与实体可见范围；
- 两者通过明确的场景原点接口连接。

本补充仍只做规划记录，不修改源码、不合并、不编译、不测试。
