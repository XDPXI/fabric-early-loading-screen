// SPDX-License-Identifier: LGPL-2.1-only

package net.neoforged.fml.earlydisplay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Reads config/early-loading-screen.json, written by the MidnightLib screen (ELSConfig).
// Mods are not loaded yet here, so it uses a small parser. A missing or invalid value uses the default.
public final class EarlyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("EARLYDISPLAY");
    public static final String FILE_NAME = "early-loading-screen.json";
    // Key, then a quoted string, a number, or a boolean.
    private static final Pattern ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|true|false|-?[0-9.]+)");

    public static final String DEFAULT_TITLE = "Minecraft: Fabric Loading...";

    private static Map<String, String> values;

    private EarlyConfig() {}

    private static synchronized Map<String, String> values() {
        if (values == null) {
            values = new HashMap<>();
            Path file = FMLPaths.GAMEDIR.get().resolve("config").resolve(FILE_NAME);
            if (Files.isRegularFile(file)) {
                try {
                    Matcher m = ENTRY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (m.find()) {
                        values.put(m.group(1), m.group(3) != null ? m.group(3).replace("\\\"", "\"").replace("\\\\", "\\") : m.group(2));
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("Could not read {}. Default settings are used.", file, e);
                }
            }
        }
        return values;
    }

    private static boolean bool(String key, boolean def) {
        String v = values().get(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    // "#RRGGBB" or "RRGGBB"
    private static ColourScheme.Colour colour(String key, ColourScheme.Colour def) {
        String v = values().get(key);
        if (v == null) return def;
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 6) return def;
        try {
            int rgb = Integer.parseInt(v, 16);
            return new ColourScheme.Colour((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // useGameBackground keeps the game background (red, or black in dark mode)
    public static ColourScheme colourScheme(ColourScheme gameScheme) {
        ColourScheme.Colour background = bool("useGameBackground", true)
                ? gameScheme.background()
                : colour("backgroundColor", ColourScheme.RED.background());
        return new ColourScheme(background, colour("textColor", gameScheme.foreground()));
    }

    // null: same as the text colour
    public static ColourScheme.Colour progressBarColour() {
        return colour("progressBarColor", null);
    }

    // null: green to red by memory use
    public static ColourScheme.Colour memoryBarColour() {
        if (bool("dynamicMemoryBarColor", true)) return null;
        return colour("memoryBarColor", null);
    }

    public static String windowTitle() {
        String title = values().get("windowTitle");
        return title == null || title.isBlank() ? DEFAULT_TITLE : title;
    }

    public static boolean showFox() {
        return bool("showFox", true);
    }

    public static boolean showHeap() {
        return bool("showHeap", true);
    }

    public static boolean showOffHeap() {
        return bool("showOffHeap", true);
    }

    public static boolean showCpu() {
        return bool("showCpu", true);
    }

    public static boolean showVersion() {
        return bool("showVersion", true);
    }

    public static boolean showStatus() {
        return bool("showStatus", true);
    }

    public static boolean showMemoryBar() {
        return bool("showMemoryBar", true);
    }
}
