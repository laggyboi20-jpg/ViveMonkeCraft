package laggyboi.vivemonkecraft.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Focused debug trace for Vivecraft↔ViveMonkeCraft interactions, written to
 * {@code <gameDir>/logs/vivemonkecraft-debug.log} ONLY while
 * {@link MovementConfig#debugLogging} is on.
 *
 * <p>This exists because the mod's trickiest bugs are VR-only (teleport desync, room
 * origin lag, grip-stick) and can't be observed from a desktop — the user enables
 * logging, reproduces in VR, and shares this file. Kept separate from the main game
 * log so it's small and easy to read.
 */
public final class VmcDebugLog {

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static BufferedWriter writer;
    private static boolean failed = false;

    private VmcDebugLog() {}

    /** Tagged discrete-event line, e.g. event("NET", "→ WallSlide(true)"). */
    public static void event(String tag, String msg) {
        log("[" + tag + "] " + msg);
    }

    /** Whether logging is on — lets callers skip building expensive strings. */
    public static boolean on() {
        return MovementConfig.debugLogging && !failed;
    }

    public static synchronized void log(String msg) {
        if (!MovementConfig.debugLogging || failed) return;
        try {
            if (writer == null) {
                Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
                Files.createDirectories(dir);
                Path file = dir.resolve("vivemonkecraft-debug.log");
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                writer.write("\n==== ViveMonkeCraft debug session " + LocalDateTime.now() + " ====\n");
            }
            writer.write("[" + LocalDateTime.now().format(TF) + "] " + msg + "\n");
            writer.flush();
        } catch (Throwable t) {
            failed = true;   // never let logging break gameplay
        }
    }
}
