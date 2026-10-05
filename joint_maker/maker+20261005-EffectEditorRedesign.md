# 特效编辑器重构执行记录

日期：2026-10-05

## 已实现

- 发布版本：1.9.3。
- 特效编辑器打开时世界继续运行，玩家移动、攻击、使用物品和破坏方块输入被屏蔽。
- 移除编辑器屏幕的暂停和原生虚化，并加入 `GameRenderer.renderBlur` 保护。
- 中央区域不再绘制 ImGui 游戏画面控件，使用实际中央 Dock 区域显示保持原比例的游戏画面，必要时保留黑边。
- 特效库点击只创建空轨道；同一轨道支持多个不同时间的特效事件。
- 时间轴右键支持新增、复制、删除特效，以及复制、清空、删除轨道。
- 轨道事件强制吸附到当前分数单位，并在同轨道时间冲突时自动换用同类型轨道。
- 时间显示改为 `Chunk n + numerator/denominator`，内部播放仍使用原始 Beat；事件及动画关键帧的原始分母保存在本地。
- 编辑器轨道、事件归属及原始分母保存到本地 `effectEditorLayout`，RhythMC 3.0 导出不读取该字段。
- 时间轴垂直滚动条与轨道列表绑定，Ctrl+滚轮按鼠标位置缩放；普通滚轮仅在时间轴区域滚动轨道。
- 修改每 Chunk 分数时锁定全部编辑操作，服务端轨道重建完成后显示“同步完成”；失败时恢复并解锁。
- 特效保存改为仅更新服务端特效数据，避免覆盖制谱器音符坐标。

## 验证

- `gradlew.bat build --offline`：通过。
- `MinecraftClientMixin.java`：1.21.11 Yarn MCP 验证通过，5 个注入点有效。
- `GameRendererMixin.java`：仅保留 `renderBlur` 拦截，MCP 验证通过。
- 临时纯 Java 检查程序：11 项轨道/分数检查通过，包括 12→8 保留 5/12、同轨时间冲突不改变 Beat、保存/重进及空轨道保存。
- 特效列表和时间范围缓存仅在编辑时更新，避免每帧重建全部特效和关键帧标记。
- 客户端启动检查未完成：离线运行缺少 `org.jetbrains:annotations:13.0`；首次尝试还缺少 Loom 资产。Windows 窗口连接两次报告 `failed to write kernel assets`，因此未做游戏内截图或视觉验证，不能宣称已实测虚化消失。
- 当前项目没有自动化测试源集，Gradle 报告 `test NO-SOURCE`。

## 部署

- 产物：`build/libs/rhythmc-maker-1.9.3.jar`。
- 已部署到 `F:\b晴天小雨awa\程序\我的世界PCL\.minecraft\versions\Rhythmc 3.0\mods`。
- 6 个旧 Maker JAR 移至 `bak/effect-editor-1.9.3`，mods 中仅保留一个 Maker 版本，避免重复加载。
- 构建和部署文件 SHA-256 一致：`6FCCECD594FCD5B81E273A2C4A5694BFC490C7F9D26D8FC2AE4F81DED1A1F327`。
- 用户原有 `AGENTS.md` 修改、根目录 `fabric.mod.json` 和其他历史协作记录不包含在本次提交中。
