# 特效编辑器问号字形根因修复（1.10.3）

## 根因：字体存在，不等于原生图集中有字形
- 使用当前 imgui-java-binding / natives 1.86.1，在独立原生 ImGui 上复现原加载流程。
- 原 `getGlyphRangesChineseFull()` 实际仅返回 `[32,255,8192,8303,12288,12543,12784,12799]`，不包含 CJK 汉字区。
- 上游同版本 `ImFontAtlas.java` 的 `RETURN_GLYPH_2_SHORT` 使用 `sizeof(glyphs)`，但实参为字形数组指针；64 位环境得到 8，并误将其当作 short 数组元素数。
- 证据：源字体可以显示“场”（U+573A）；原范围不请求此字符；原 `findGlyphNoFallback(0x573A).isNotValidPtr()` 为 true。原字体回退字符是 U+003F，故界面出现问号。
- 原手工 EDITOR_GLYPHS 字符串只让其中列举的字能够显示，新增标签仍缺字。继续补字符串无法根治。
- 原生绑定返回的是包装对象，找不到字形也不返回 Java null，因此检测必须使用 `isValidPtr / isNotValidPtr`，不能只判断包装对象是否为 null。
- 这不是谱面 JSON 编码或中文翻译错误，没有替换玩家数据里的问号。

## 实现
- 新增 `client/render/font/EditorFontAtlas`，显式请求 Unicode BMP 非代理项范围 U+0020–U+D7FF、U+E000–U+FFFD，彻底移除有缺陷的 getGlyphRanges 接口依赖和手工补字列表。
- 同一字体文件复制为模组专用 `assets/rhythmc_maker/font/editor.ttf`，通过类路径读取，不再由 Minecraft 资源包栈覆盖编辑器字体。不影响游戏原有字体包。
- 字体配置保留至图集构建结束；源数据保留至 ImGui 上下文销毁后释放。
- 初始化检查原生字体指针、图集构建结果及必需 UI 字形；失败明确抛出诊断，不静默切换 ASCII 字体。
- 显式设置默认字体；完整图集为 2048×4096，1×1 过采样限制纹理占用。上传前检查实际显卡最大纹理尺寸。
- Java 编译明确使用 UTF-8，避免构建机器默认编码差异。
- 没有升级 ImGui 大版本，没有改参数格式、播放、场景或时间轴。

## 验证：原始红灯 → 实现绿灯 → 最终 JAR 渲染
- `.ai_generated_util_file/ImGuiFontRepro.java`：保留修改前加载器快照；原生复现明确失败，大量汉字缺失，日志 `font-repro-red.log`。
- `.ai_generated_util_file/upstream-ImFontAtlas-1.86.1.java`：上游源文件存档；宏位于 389–393 行。
- `.ai_generated_util_file/EditorFontRegression.java`：扫描当前编辑器/参数源码中的 468 个不同字符，逐字符检查原生非回退字形；连续三次创建/销毁上下文，全部通过。中文输入框 UTF-8 往返与帧中实际字体也通过。
- `.ai_generated_util_file/EditorFontOpenGlCheck.java`：在本机 NVIDIA GeForce GTX 1650 的隐藏 OpenGL 3.3 上下文中，直接调用生产 `EffectParameterEditor`，实际绘制全部 19 个参数页面。GL 错误检测和渲染像素检测通过。
- 上述两项验证再次以最终 `build/libs/rhythmc-maker-1.10.3.jar` 为代码和字体资源来源运行，全部通过，不依赖源码目录里的字体资源。
- 实际截图：`.ai_generated_util_file/font-render-FIREWORK.png`、`font-render-ARENA.png`、`font-render-TEXT_DISPLAY_EFFECT.png`、`font-render-HIDE_NOTES.png`；已检查烟花参数截图，汉字与中文选项显示完整。
- 完整 `gradlew.bat build --offline` 成功；110 项参数回归与 33 项翻译/协议值检查通过。
- 完整 Minecraft 开发客户端仍在 downloadAssets 阶段受环境资源下载失败阻挡，未做整套游戏内操作实测；不能将独立 OpenGL 页面测试描述为完整 Minecraft 会话验证。

## 适用边界与交付
- 修复覆盖现有编辑器全部 UI 字符，以及字体自身支持的 BMP 字符。旧版 ImGui 使用 16 位 ImWchar，不承诺任意 emoji / Unicode 辅助平面文字；不猜测恢复已经存为 ASCII 问号的原始文本。
- 版本：1.10.3；部署至 `F:\b晴天小雨awa\程序\我的世界PCL\.minecraft\versions\Rhythmc 3.0\mods\rhythmc-maker-1.10.3.jar`。
- 旧版备份至 `F:\b晴天小雨awa\RhythMC\rhythmc maker\bak\font-root-fix-1.10.3`。
- 启动中的游戏必须完全退出后重启，旧 Java 类与旧原生字体图集不会因为复制新 JAR 自动替换。
- 仅提交本次字体根因修复、资源、编码配置、版本和记录，保留用户已有工作区改动。
