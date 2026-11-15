/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.jab125.earlyloadingscreen.Loader;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforge.client.loading.NoVizFallback;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The SDL3 and RenderPearl version of the handler for Minecraft 26.3 and later.
 * The game makes its own backend, device and window. This class lets the game use the ones of the early display instead.
 */
public class ImmediateWindowHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static ImmediateWindowProvider provider;

    private static ProgressMeter earlyProgress;

    public static void load(final String launchTarget, final String[] arguments) {
        if (!List.of("forgeclient", "forgeclientuserdev", "forgeclientdev").contains(launchTarget)) {
            provider = new DummyProvider();
            LOGGER.info("ImmediateWindowProvider not loading because launch target is {}", launchTarget);
        } else if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            provider = new DummyProvider();
            LOGGER.info("ImmediateWindowProvider not loading because splash screen is disabled");
        } else {
            final var providername = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
            LOGGER.info("Loading ImmediateWindowProvider {}", providername);
            provider = new DisplayWindow();
        }
        // Only update config if the provider isn't the dummy provider
        if (!Objects.equals(provider.name(), "dummyprovider"))
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, provider.name());
        Loader.progressWindowTick = provider.initialize(arguments);
        earlyProgress = StartupNotificationManager.addProgressBar("EARLY", 0);
        earlyProgress.label("Bootstrapping Minecraft");
    }

    /**
     * Called instead of the creation of the device of the game.
     *
     * @return the device of the early display, or a new device of the backend when the early display cannot give its device
     */
    public static GpuDevice takeDevice(GpuBackend backend, GpuDebugOptions options) throws BackendCreationException {
        if (provider instanceof DisplayWindow displayWindow) {
            return displayWindow.handOffDevice(backend, options);
        }
        return backend.createDevice(options);
    }

    /**
     * Called instead of the creation of the window of the game.
     *
     * @return the window of the early display, or a new window of the backend when the early display cannot give its window
     */
    public static long takeWindow(GpuBackend backend, String title, int width, int height, long flags) {
        if (provider instanceof DisplayWindow displayWindow && displayWindow.ownsWindow()) {
            return displayWindow.setupMinecraftWindow(() -> width, () -> height, () -> title, () -> 0L);
        }
        return backend.createWindow(title, width, height, flags);
    }

    public static boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        return provider.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    public static void updateFBSize(IntConsumer width, IntConsumer height) {
        provider.updateFramebufferSize(width, height);
    }

    public static <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        earlyProgress.complete();
        return provider.loadingOverlay(mc, ri, ex, fade);
    }

    public static void acceptGameLayer(final ModuleLayer layer) {
        provider.updateModuleReads(layer);
    }

    public static void renderTick() {
        provider.periodicTick();
    }

    public static String getGLVersion() {
        return provider.getGLVersion();
    }

    public static void updateProgress(final String message) {
        earlyProgress.label(message);
    }

    public static void crash(final String message) {
        provider.crash(message);
    }

    /** Used when the early display is off. The game makes its own window and device. */
    private record DummyProvider() implements ImmediateWindowProvider {
        @Override
        public String name() {
            return "dummyprovider";
        }

        @Override
        public Runnable initialize(String[] args) {
            return () -> {};
        }

        @Override
        public void updateFramebufferSize(final IntConsumer width, final IntConsumer height) {}

        @Override
        public long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitor) {
            throw new IllegalStateException("The dummy provider has no window");
        }

        @Override
        public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
            return (Supplier<T>) NoVizFallback.loadingOverlay((Supplier<net.minecraft.client.Minecraft>) mc, (Supplier<net.minecraft.server.packs.resources.ReloadInstance>) ri, ex, fade);
        }

        @Override
        public String getGLVersion() {
            return "3.3";
        }

        @Override
        public void updateModuleReads(final ModuleLayer layer) {}

        @Override
        public void periodicTick() {
            // NOOP
        }

        @Override
        public void crash(final String message) {
            // NOOP for unsupported environments
        }
    }
}
