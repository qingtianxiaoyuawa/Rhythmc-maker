# 特效编辑器首次打开崩溃修复

日期：2026-10-06

## 根因

崩溃包 `hs_err_pid6104.log` 显示原生访问冲突发生在 `ImGuiDockNode.getPosX()`，调用路径为 `EffectEditorImGuiView.drawDockspace()`，读取地址为 `0x48`。imgui-java 1.86.1 在 DockBuilder 查询缺失节点时可能返回 Java 非空包装对象，但包装对象的原生指针为零。仅判断 Java 对象是否为 `null` 会误判节点存在，导致默认布局初始化被跳过，随后对无效中央节点读取坐标并触发 JVM 原生崩溃。

同时，1.86.1 的公开 `ImGuiDockNodeFlags` 未暴露 `DockSpace` 标志。仅用 `None` 建根节点的 JNI 回归表现为根节点未成为 DockSpace、每帧重复初始化；使用该版本原生 DockSpace 位后，根节点及中央节点可正常建立。

## 修复

- 集中通过 `ImGuiDockNodeAccess` 检查包装对象的原生指针有效性。
- 只在有效 DockSpace 和中央节点都存在时读取中央视口边界。
- 首次布局用明确的 DockSpace 节点标志创建根节点，完成布局后复用，不再逐帧重建。

## 验证与交付

- JNI 回归夹具确认首次查询得到“非空包装对象、无效原生指针”，随后创建有效根节点和中央节点；连续三帧均可读取有限且非空的中央边界，且布局只初始化一次。
- `gradlew compileJava --offline --no-daemon` 通过；`gradlew build --offline --no-daemon` 通过。构建使用 Java 21.0.3。
- 成品 JAR 元数据为 `rhythmc_maker` / `1.10.4`，已部署至 PCL Rhythmc 3.0 的 `mods` 目录。
- 原 `1.10.3` JAR 已备份到 `bak/rhythmc-maker-1.10.3-bak-20261006.jar`。
- JNI 回归覆盖了导致本次崩溃的 ImGui 原生节点路径；未启动完整 Minecraft 客户端进行人工点击验证。
