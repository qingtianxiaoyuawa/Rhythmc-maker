package cn.frkovo.rhythmcmaker.client;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;

final class GenericFileChooser {
    private GenericFileChooser() {}
    static void choose(String filter, Consumer<Path> selected) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow().isFullscreen()) GLFW.glfwIconifyWindow(client.getWindow().getHandle());
        Thread.ofVirtual().start(() -> {
            try {
                String command = "Add-Type -AssemblyName System.Windows.Forms; $dialog = New-Object System.Windows.Forms.OpenFileDialog; $dialog.Filter = '" + filter.replace("'", "''") + "|所有文件|*.*'; $dialog.Multiselect = $false; if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write([Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($dialog.FileName))) }";
                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", command).redirectErrorStream(true).start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) output.append(line); }
                process.waitFor();
                if (process.exitValue() != 0 || output.isEmpty()) return;
                Path path = Path.of(new String(Base64.getDecoder().decode(output.toString().trim()), StandardCharsets.UTF_8));
                client.execute(() -> selected.accept(path));
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                client.execute(() -> ClientChartAccess.status("无法打开文件选择窗口：" + exception.getMessage()));
            }
        });
    }
}
