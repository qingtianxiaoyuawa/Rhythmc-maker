# 2026-10-04 - Reborn TextDisplay 生成中心确认

## 结论
Reborn 源码中没有写死一个全局固定的 TextDisplay 世界坐标。TextDisplay 的默认生成中心取决于每局 `GameInstance` 的 `gameCenterLoc`：

```text
screenCenterLoc = (gameCenterLoc.x, gameCenterLoc.y, gameCenterLoc.z - 2)
```

`TextDisplayManager` 保存该位置为 `defLoc`，创建 `GpTextDisplay` 时将 `defLoc` 传入；`GpTextDisplay` 构造函数直接在 `defLoc` 生成 Bukkit `TextDisplay`。因此，Reborn 的 TextDisplay 实体初始生成位置就是：

```text
(gameCenterLoc.x, gameCenterLoc.y, gameCenterLoc.z - 2)
```

## 重要区别
- 这是 TextDisplay 实体的初始世界生成点/锚点，不等于最终文字可见区域的几何中心。
- 后续 `GpTextDisplay.render()` 会使用矩阵：

```text
translate(posX, posY, posZ)
rotateXYZ(rotX, rotY, rotZ)
scale(scaleX, scaleY, scaleZ)
```

- 因此最终显示位置由 `screenCenterLoc + LinearTransformation position` 决定。
- Reborn 的 `screenCenterLoc` 还被 NoteObject 用作音符相对坐标基点；音符额外使用 `screenCenterLoc + (0, 0.5, 0)`，但 TextDisplay 默认没有这 `+0.5Y` 偏移。

## 对 Maker 的对应关系
Maker 当前播放中心已确认：

```text
gameCenterLoc = (200.5, 66.0, 1.0)
TEXT_DISPLAY_CENTER = (200.5, 66.0, -1.0)
```

该位置遵循 Reborn 的 `screenCenterLoc = gameCenterLoc + (0, 0, -2)`：

```text
Maker TextDisplay base = (200.5, 66.0, 1.0 - 2.0)
```

因此当前 TextDisplay 默认生成中心为 `(200.5, 66.0, -1.0)`，不再使用此前的 `(200.45, 65.8, -1.0)` 近似值。

## 证据位置
- `Game/Instances/GameInstance.java`：`screenCenterLoc = new Location(gameCenterLoc.getWorld(), gameCenterLoc.getX(), gameCenterLoc.getY(), gameCenterLoc.getZ() - 2, 0, 0)`。
- `Game/Managers/Gameplay/TextDisplayManager.java`：`defLoc = getScreenCenterLoc().clone()`，创建池对象时传入 `new GpTextDisplay(defLoc, player)`。
- `Game/Effects/TextDisplay/GpTextDisplay.java`：构造函数通过 `world.spawn(defLoc, TextDisplay.class, ...)` 生成实体。
- 同文件 `render()`：使用 `translate -> rotateXYZ -> scale` 设置变换矩阵。

## 当前工程状态
Maker 源码已按上述中心实现，并以用户确认的实际玩家游玩中心作为坐标依据；后续如果游玩中心改动，必须同步修改播放平台中心和 TextDisplay 基准，不应单独调整 TextDisplay 常量。
