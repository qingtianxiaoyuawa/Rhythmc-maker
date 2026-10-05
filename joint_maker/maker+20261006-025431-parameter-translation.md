# 特效参数中文翻译修正（1.10.2）

## 修改
- 特效参数说明统一使用“场景名称”，不再使用“竞技场名称”。协议字段 arena、ARENA、CHANGE_ARENA 保持不变。
- 烟花形状、天气、文本展示修改类型、发光颜色、隐藏音符类型共 33 个选项增加中文显示，同时保留括号中的原协议值供对照。
- 三维参数显示 X/Y/Z 轴，RGB/RGBA 显示红/绿/蓝/不透明度；窄面板中分量标题自动换行。
- 参数说明中的字符串数组、时间单位、旋转单位和完整参数 JSON 标题使用中文。
- 翻译仅发生在 UI 显示层；选择控件仍写入原英文枚举值，不改变参数格式、播放或场景逻辑。

## 验证
- 完整 `gradlew.bat build --offline` 成功。
- 临时校验 `.ai_generated_util_file/EffectParameterTranslationChecks.java`：33 个选项均具有中文，保存与导出仍为原协议值；未知扩展值未修改，场景参数标签和说明没有旧称。
- `.ai_generated_util_file/EffectParameterChecks.java`：110 项参数回归检查全部通过。
- 本次代码 `git diff --check` 通过。
- 本轮未执行游戏内交互实测；前轮开发客户端启动在资源下载阶段失败，不将编译成功等同于运行验证。

## 交付
- 版本：1.10.2。
- 部署：`F:\b晴天小雨awa\程序\我的世界PCL\.minecraft\versions\Rhythmc 3.0\mods\rhythmc-maker-1.10.2.jar`。
- 旧版备份：`F:\b晴天小雨awa\RhythMC\rhythmc maker\bak\parameter-translation-1.10.2`。
- 仅提交本次参数翻译、版本和此记录；保留用户已有工作区改动。
