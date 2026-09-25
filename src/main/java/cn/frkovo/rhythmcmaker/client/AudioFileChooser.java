package cn.frkovo.rhythmcmaker.client;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Path;
import java.util.function.Consumer;

final class AudioFileChooser {
    private static final String FILE_FILTER = "音频文件|*.mp3;*.flac;*.wav;*.ogg;*.m4a;*.aac|所有文件|*.*";

    private AudioFileChooser() {
    }

    static void choose(Consumer<Path> onSelected) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow().isFullscreen()) {
            GLFW.glfwIconifyWindow(client.getWindow().getHandle());
        }

        Thread.ofVirtual().start(() -> {
            try {
                String selectedPath = openWindowsFileDialog();
                if (!selectedPath.isBlank()) {
                    String decodedPath = new String(Base64.getDecoder().decode(selectedPath), StandardCharsets.UTF_8);
                    client.execute(() -> onSelected.accept(Path.of(decodedPath)));
                }
            } catch (IOException exception) {
                client.execute(() -> ClientChartAccess.status("无法打开歌曲选择窗口：" + exception.getMessage()));
            }
        });
    }

    private static String openWindowsFileDialog() throws IOException {
        String command = "Add-Type -AssemblyName System.Windows.Forms; "
                + "$dialog = New-Object System.Windows.Forms.OpenFileDialog; "
                + "$dialog.Title = '选择歌曲文件'; "
                + "$dialog.Filter = '" + FILE_FILTER + "'; "
                + "$dialog.Multiselect = $false; "
                + "if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write([Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($dialog.FileName))) }";
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", command)
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
        }
        try {
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("文件选择被中断", exception);
        }
        if (process.exitValue() != 0) throw new IOException("Windows 文件选择窗口启动失败");
        return output.toString().trim();
    }
}
