# 特效参数编辑完整实现（1.10.1）

## 依据与范围
- 依据 `planner+20261004-155316.md` 和本地 Reborn `EffectFactory` / `TitleEffect` / `GpTextDisplay` 源码。
- 完整覆盖 17 种官方特效：HOLOGRAM、REMOVE_HOLOGRAM、TITLE、FIREWORK、TIME、EFFECT、CLEAR_EFFECT、WEATHER、ARENA、TEXT_DISPLAY、TEXT_DISPLAY_EFFECT、TEXT_DISPLAY_SYNC_TRACK、TEXT_DISPLAY_DESYNC_TRACK、HIDE_NOTES、GLOW_COLOR、MESSAGE、TEXT_DISPLAY_REMOVE。
- ACTIONBAR 保留本地编辑和草稿保存；CHANGE_ARENA 保留旧名称，导出映射 ARENA。
- 未改正式播放、预览播放、场景重置、时间轴操作和预览中心。

## 参数控件
- 单行/多行文字、可增加删除的字符串列表（保留条目内换行）、整数列表、三维向量、可增删的烟花位置/RGB 列表、RGBA、枚举下拉、布尔开关、音符类型多选。
- 时间与持续时间使用 64 位数字文本输入；duration 单位毫秒，省略采用官方默认 31536000000 毫秒。TITLE 时间单位毫秒，并检查三项总和避免服务端 int 溢出。
- TEXT_DISPLAY 的旋转单位为弧度；位置为相对中心偏移，缩放三轴独立。新建实体生成独立 ID。
- TEXT_DISPLAY_EFFECT 按 TEXT / SHADOW / OPACITY / BACKGROUND_COLOR / LINEAR_TRANSFORMATION / GLOWING 动态显示参数；切换分支补全新分支默认值，保留其它分支已填值。
- 可选参数通过开关真正移除字段；CLEAR_EFFECT 缺省清全部，显式空数组不清除任何效果，两者不混淆。
- 数字类型、整数精度、有限值、向量长度、RGB/RGBA 整数范围、枚举和必填项均校验。无效输入留在编辑草稿中，不覆盖已应用值并阻止保存。
- 完整 properties JSON 可编辑，保留扩展字段；未应用 JSON 阻止保存，未修改的 JSON 面板自动跟随普通控件更新。

## 旧数据与导出
- 提供显式“转换旧草稿字段”按钮，不静默替换玩家已有参数，不覆盖已填官方字段。
- 转换 text→title/contents、xyz→location/position/locations、scalar scale→三轴、opacity→targetOpacity、打包颜色→RGB、数字 effect→effectId、旧音符数字→类型名称；无法明确对应的数据保持原值并显示缺项。
- 编辑器内部 eventType 保留，官方导出输出 effectType / beat / properties；兼容识别扁平字段，导出归并到 properties。
- 导出前先完整校验，失败发生在替换输出目录之前，防止无效特效覆盖上次导出。
- ACTIONBAR 无官方对应类型，明确阻止官方导出，不偷偷删除事件。CHANGE_ARENA 导出为 ARENA。
- 本地预览范围等既有专用字段不导出；未知扩展 properties 保留，编辑器布局不写入官方特效。

## 验证与交付
- `gradlew.bat build --offline`：成功。
- 临时独立 Java 校验程序 `.ai_generated_util_file/EffectParameterChecks.java`：110 项通过；覆盖全部官方类型默认值、导入导出往返、六个修改分支、64 位精度/越界、可选字段缺省/空数组、无效颜色和向量、字符串换行、旧字段迁移、扩展保留和本地字段过滤。
- `git diff --check`：本次代码与版本变更通过。
- `gradlew.bat runClient --offline`：在 downloadAssets 阶段失败，开发环境缺少资源缓存；未进入游戏，因此不宣称完成游戏内交互实测。
- 部署目标：`F:\b晴天小雨awa\程序\我的世界PCL\.minecraft\versions\Rhythmc 3.0\mods\rhythmc-maker-1.10.1.jar`。
- 旧版本备份目录：`F:\b晴天小雨awa\RhythMC\rhythmc maker\bak\effect-parameters-1.10.1`。
- 仅提交本次功能文件与此记录，不提交用户已有 AGENTS.md 改动及其它未跟踪文件。
