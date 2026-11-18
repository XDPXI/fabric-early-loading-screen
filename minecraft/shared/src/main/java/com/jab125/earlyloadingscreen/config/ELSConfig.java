package com.jab125.earlyloadingscreen.config;

import eu.midnightdust.lib.config.MidnightConfig;

// Field names are the JSON keys read by EarlyConfig in the early display. Change both together.
public class ELSConfig extends MidnightConfig {
    public static final String MOD_ID = "early-loading-screen";
    public static final String APPEARANCE = "appearance";
    public static final String ELEMENTS = "elements";

    @Entry(category = APPEARANCE, width = 64)
    public static String windowTitle = "Minecraft: Fabric Loading...";
    @Entry(category = APPEARANCE)
    public static boolean useGameBackground = true;
    @Entry(category = APPEARANCE, isColor = true, width = 7, min = 7, max = 7)
    public static String backgroundColor = "#EF323D";
    @Entry(category = APPEARANCE, isColor = true, width = 7, min = 7, max = 7)
    public static String textColor = "#FFFFFF";
    @Entry(category = APPEARANCE, isColor = true, width = 7, min = 7, max = 7)
    public static String progressBarColor = "#FFFFFF";
    @Entry(category = APPEARANCE)
    public static boolean dynamicMemoryBarColor = true;
    @Entry(category = APPEARANCE, isColor = true, width = 7, min = 7, max = 7)
    public static String memoryBarColor = "#00C800";

    @Entry(category = ELEMENTS)
    public static boolean showFox = true;
    @Entry(category = ELEMENTS)
    public static boolean showStatus = true;
    @Entry(category = ELEMENTS)
    public static boolean showVersion = true;
    @Entry(category = ELEMENTS)
    public static boolean showMemoryBar = true;
    @Entry(category = ELEMENTS)
    public static boolean showHeap = true;
    @Entry(category = ELEMENTS)
    public static boolean showOffHeap = true;
    @Entry(category = ELEMENTS)
    public static boolean showCpu = true;
}
