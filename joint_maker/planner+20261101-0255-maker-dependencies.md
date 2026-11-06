# 当前 RhythMC Maker 前置与联动 Mod 清单

日期：2026-11-01

## 核对对象与依据

核对工程 `F:\b晴天小雨awa\RhythMC\rhythmc maker` 的 `gradle.properties`、`build.gradle`、`fabric.mod.json`、WorldEdit integration 源码，以及现有成品 `build/libs/rhythmc-maker-1.13.70.jar` 的元数据和 `META-INF/jars` 嵌套内容。未执行构建或游戏验证。

## 必须满足的运行环境

- Minecraft Java Edition `1.21.11`
- Fabric Loader `>=0.19.5`
- Java `>=21`
- Fabric API（运行元数据要求存在；当前工程构建版本为 `0.141.6+1.21.11`）

## 随 Maker JAR 内置/嵌套的依赖

这些是实际成品 `rhythmc-maker-1.13.70.jar` 的 `META-INF/jars` 内容，普通安装不应再手工下载同版本重复安装；其 Fabric mod ID 同时列在 Maker 的 required depends 中，由嵌套 JAR 提供：

- Owo Lib `owo 0.13.0+1.21.11`：用于当前多个 UI Screen；源码仍有直接引用，不能认为已移除。
- Casual Arcade 模块 `0.8.1-beta.8`：`arcade-dimensions`、`arcade-event-registry`、`arcade-events-server`、`arcade-extensions`、`arcade-utils`；Maker 用其自定义编辑维度及相关事件/扩展能力。

成品还嵌套 ImGui Java binding/LWJGL3/Windows native、SnakeYAML、zstd-jni。这些属于打包库/原生组件，不是用户应单独安装的常规 Mod。

## 可选联动 Mod

- WorldEdit Fabric `7.4.2`（工程编译期 `modCompileOnly`）：可选集成，用木斧选择与 WorldEdit 方块编辑同步谱面音符/场景；源码通过 `isModLoaded("worldedit")` 检查，并在不可用时禁用该集成。普通 Maker 核心功能不要求安装它。

## 与 RhythMC 3.0 的关系

RhythMC 3.0 / RhythMC-MOD 不是 `fabric.mod.json` 声明的 Maker 前置。Maker 面向 RhythMC 3.0 `.rmcc` 和场景格式；若要在 RhythMC 游戏运行链路中实际游玩导出的谱面，需要目标游戏/模组环境，但这属于成品使用场景，不是 Maker 编辑器启动依赖。

## 平台提示

现有成品只看到 `imgui-java-natives-windows` 嵌套项，且内置音频工具位于 `native/windows-x86_64`（ffplay/ffprobe）。因此可确认发行包包含 Windows x86_64 原生资源；其它操作系统是否有可用路径需进一步核对启动选择逻辑，不应据此直接保证跨平台完整支持。

## 总结

对普通用户而言，外部核心前置是 **Minecraft 1.21.11 + Fabric Loader 0.19.5 或更高版本 + Java 21 或更高版本 + Fabric API**。Owo 和五个 Arcade 模块虽然是硬依赖 ID，但当前成品已嵌套打包；WorldEdit 是可选联动。RhythMC-MOD 是目标兼容/游玩环境，不是 Maker 安装前置。

未修改源码、未编译、未运行测试或游戏。
