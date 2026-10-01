package cn.frkovo.rhythmcv2.cv2.command;

import cn.frkovo.rhythmcv2.cv2.Main;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.compile.Compiler;
import cn.frkovo.rhythmcv2.cv2.compile.LintService;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.session.CharterSession;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /charter（/cv2）命令根（附录 C MVP 子集）。 */
public final class CommandRoot implements TabExecutor {

    private final Main plugin;

    public CommandRoot(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("仅玩家可用");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "new" -> {
                try {
                    requireArg(args, 1, () -> plugin.sessions().create(player, args[1]));
                } catch (Exception e) {
                    player.sendMessage("§c建谱失败: " + e.getMessage());
                }
            }
            case "open" -> {
                try {
                    requireArg(args, 1, () -> plugin.sessions().open(player, args[1]));
                } catch (Exception e) {
                    player.sendMessage("§c打开失败: " + e.getMessage());
                }
            }
            case "close" -> plugin.sessions().close(player);
            case "save" -> withSession(player, s -> {
                try {
                    s.save();
                    player.sendMessage("§a已保存（revision " + s.chart().revision + "）");
                } catch (Exception e) {
                    player.sendMessage("§c保存失败: " + e.getMessage());
                }
            });
            case "publish" -> withSession(player, s -> publish(player, s, args));
            case "compile" -> withSession(player, s -> compile(player, s, false));
            case "bpm" -> bpm(player, args);
            case "offset" -> withSession(player, s -> {
                if (args.length < 2) {
                    player.sendMessage("§7当前 offset = " + s.chart().offsetMs + "ms");
                    return;
                }
                s.apply(new Operation.OffsetSet(s.chart().offsetMs, args[1]));
                player.sendMessage("§aoffset = " + s.chart().offsetMs + "ms");
            });
            case "snap" -> withSession(player, s -> {
                if (args.length < 2) {
                    player.sendMessage("§7snap = " + s.state().snap);
                    return;
                }
                s.state().snap = args[1].equalsIgnoreCase("off") ? -1 : Integer.parseInt(args[1]);
                player.sendMessage("§asnap = " + (s.state().snap > 0 ? "1/" + s.state().snap : "off"));
            });
            case "seek" -> withSession(player, s -> {
                if (args.length < 2) {
                    player.sendMessage("§7游标 = " + s.state().cursorBeat);
                    return;
                }
                s.transport().seek(parseBeat(s, args[1]));
            });
            case "play" -> withSession(player, s -> {
                if (args.length >= 2) {
                    s.transport().play(parseBeat(s, args[1]));
                } else {
                    s.transport().play();
                }
            });
            case "pause" -> withSession(player, s -> s.transport().pause());
            case "stop" -> withSession(player, s -> s.transport().stop());
            case "loop" -> withSession(player, s -> {
                if (args.length >= 3) {
                    BeatFraction a = parseBeat(s, args[1]);
                    BeatFraction b = parseBeat(s, args[2]);
                    s.state().loopA = a;
                    s.state().loopB = b;
                    s.transport().setLoop(a, b);
                    player.sendMessage("§aA-B 循环 [" + a + ", " + b + "]");
                } else {
                    s.state().clearLoop();
                    s.transport().clearLoop();
                    player.sendMessage("§7循环已清除");
                }
            });
            case "speed" -> withSession(player, s -> {
                if (args.length < 2) {
                    player.sendMessage("§7speed = " + s.transport().speed());
                    return;
                }
                s.transport().setSpeed(Float.parseFloat(args[1]));
                player.sendMessage("§aspeed = " + s.transport().speed() + "（mod 端重采样变调）");
            });
            case "undo" -> withSession(player, CharterSession::undo);
            case "redo" -> withSession(player, CharterSession::redo);
            case "lint" -> withSession(player, s -> {
                List<LintService.Issue> issues = LintService.lint(s.chart());
                if (issues.isEmpty()) {
                    player.sendMessage("§aLint 通过（0 问题）");
                }
                for (LintService.Issue i : issues) {
                    player.sendMessage((i.level().equals("error") ? "§c✖ " : "§e⚠ ")
                            + "§7[" + i.rule() + "] " + i.message());
                }
            });
            case "audio" -> withSession(player, s -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("hint")) {
                    s.state().audioHint = args[2];
                    plugin.bridge().sendChartMeta(s);
                    player.sendMessage("§aaudioHint = " + args[2] + "（已推送 mod 重新匹配）");
                } else {
                    player.sendMessage("§7audioStatus = " + s.audioStatus() + " " + s.audioReason());
                }
            });
            case "edit" -> withSession(player, s -> {
                if (args.length < 2) {
                    player.sendMessage("§7用法: /charter edit <world|nether|end|void>");
                    return;
                }
                String level = args[1].toLowerCase();
                s.chart().activeLevel = level;
                s.renderer().markDirty();
                player.sendMessage("§a切换难度: " + level);
            });
            default -> help(player);
        }
        return true;
    }

    private void publish(Player player, CharterSession s, String[] args) {
        boolean strict = args.length > 1 && args[1].equalsIgnoreCase("--strict");
        Compiler.CompileResult result = compile(player, s, true);
        if (!result.success()) {
            return;
        }
        if (strict) {
            boolean anyLoss = result.precision().stream()
                    .anyMatch(p -> p.kind().equals("PRECISION_LOSS"));
            if (anyLoss) {
                player.sendMessage("§c--strict：存在 PRECISION_LOSS，拒绝发布（详见 compile-report.json）");
                return;
            }
        }
        try {
            Path published = plugin.sessions().songDir(s.chart().songFolder).resolve("published");
            copyDir(result.dir(), published);
            player.sendMessage("§a已发布到 " + published + "（可直接拷贝给 Reborn charts）");
        } catch (Exception e) {
            player.sendMessage("§c发布失败: " + e.getMessage());
        }
    }

    private Compiler.CompileResult compile(Player player, CharterSession s, boolean silentOk) {
        try {
            Compiler.CompileResult result = Compiler.compile(s.chart(),
                    plugin.sessions().songDir(s.chart().songFolder));
            for (String err : result.errors()) {
                player.sendMessage("§c" + err);
            }
            int losses = result.precision().size();
            if (result.success()) {
                player.sendMessage("§a编译成功: " + result.dir()
                        + (losses > 0 ? " §e（" + losses + " 条精度损失记录）" : " §7（无精度损失）"));
            } else {
                player.sendMessage("§c编译失败（见上方错误与 compile-report.json）");
            }
            return result;
        } catch (Exception e) {
            player.sendMessage("§c编译异常: " + e.getMessage());
            return new Compiler.CompileResult(false, null, List.of(), List.of(),
                    List.of(String.valueOf(e.getMessage())), java.util.Map.of(), s.chart().revision);
        }
    }

    private static void copyDir(Path from, Path to) throws Exception {
        java.nio.file.Files.createDirectories(to);
        try (var walk = java.nio.file.Files.walk(from)) {
            for (java.nio.file.Path src : walk.toList()) {
                java.nio.file.Path dst = to.resolve(from.relativize(src).toString());
                if (java.nio.file.Files.isDirectory(src)) {
                    java.nio.file.Files.createDirectories(dst);
                } else {
                    java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void bpm(Player player, String[] args) {
        withSession(player, s -> {
            EditorChart chart = s.chart();
            if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
                player.sendMessage("§bBPM 事件表：");
                for (BeatClock.BpmEvent e : chart.bpms) {
                    player.sendMessage("§7  beat " + e.beat() + " → §f" + e.bpm().toPlainString());
                }
                return;
            }
            switch (args[1].toLowerCase()) {
                case "add" -> {
                    if (args.length < 4) {
                        player.sendMessage("§c用法: /charter bpm add <beat> <bpm>");
                        return;
                    }
                    s.apply(new Operation.BpmAdd(args[2], args[3]));
                    player.sendMessage("§aBPM 已添加");
                }
                case "remove" -> {
                    if (args.length < 3) {
                        player.sendMessage("§c用法: /charter bpm remove <beat>");
                        return;
                    }
                    s.apply(new Operation.BpmRemove(args[2], ""));
                }
                case "set" -> {
                    if (args.length < 4) {
                        player.sendMessage("§c用法: /charter bpm set <beat> <bpm>");
                        return;
                    }
                    s.apply(new Operation.BpmSet(args[2], "", args[3]));
                }
                default -> player.sendMessage("§c用法: /charter bpm <list|add|remove|set>");
            }
        });
    }

    private BeatFraction parseBeat(CharterSession s, String text) {
        if (text.startsWith("bar:")) {
            return BeatFraction.of(Long.parseLong(text.substring(4)) * 4L);
        }
        return BeatFraction.parse(text);
    }

    private interface SessionTask {
        void run(CharterSession session);
    }

    private void withSession(Player player, SessionTask task) {
        CharterSession session = plugin.sessions().get(player);
        if (session == null) {
            player.sendMessage("§c先 /charter new <songFolder> 或 /charter open <songFolder>");
            return;
        }
        task.run(session);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void requireArg(String[] args, int index, ThrowingRunnable run) throws Exception {
        run.run();
    }

    private void help(Player player) {
        player.sendMessage("""
                §b=== RhythMC Charter V2 ===
                §f/charter new <songFolder> §7| open <songFolder> §7| close §7| save
                §f/charter compile §7| publish [--strict] §7| lint
                §f/charter bpm <list|add|remove|set> §7| offset <ms> §7| snap <n|off>
                §f/charter seek <beat|bar:N> §7| play [beat] §7| pause §7| stop
                §f/charter loop <a> <b> §7| loop off §7| speed <0.5|0.75|1.0>
                §f/charter undo §7| redo §7| audio hint <file> §7| edit <level>
                §7热栏 1-8 是编辑工具，9 打开菜单。""");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("new", "open", "close", "save", "compile", "publish", "lint",
                    "bpm", "offset", "snap", "seek", "play", "pause", "stop", "loop", "speed",
                    "undo", "redo", "audio", "edit"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            out.addAll(List.of("world", "nether", "end", "void"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("publish")) {
            out.add("--strict");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
