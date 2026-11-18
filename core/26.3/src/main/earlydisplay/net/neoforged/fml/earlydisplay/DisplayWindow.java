/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import com.mojang.blaze3d.platform.NativeLibrariesBootstrap;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforge.client.loading.NoVizFallback;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Loading Window that is opened Immediately after Forge starts.
 * It is called from the ModDirTransformerDiscoverer, the soonest method that ModLauncher calls into Forge code.
 * In this way, we can be sure that this will not run before any transformer or injection.
 *
 * Minecraft 26.3 uses SDL3 for windows and RenderPearl (Blaze3D) for drawing. This class uses the same.
 * It makes a RenderPearl OpenGL backend, a device and a window, and draws with render passes.
 * The window itself is drawn on a secondary thread, and is handed off to the main game by
 * {@link #handOffDevice(GpuBackend, GpuDebugOptions)} and {@link #setupMinecraftWindow(IntSupplier, IntSupplier, Supplier, LongSupplier)}.
 * The game then uses the same device and window, so the early display needs no second OpenGL context.
 *
 * Based on the prior ClientVisualization, with some personal touches.
 */
public class DisplayWindow implements ImmediateWindowProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("EARLYDISPLAY");
    private final AtomicBoolean animationTimerTrigger = new AtomicBoolean(true);

    private enum State {
        /** The early display draws on its own thread. */
        RUNNING,
        /** The game owns the device and the window. We only draw to the loading overlay. */
        HANDED_OFF,
        /** The early display could not start or is shut down. */
        INACTIVE
    }

    private volatile State state = State.INACTIVE;

    private ColourScheme colourScheme;
    private GpuBackend backend;
    private GpuDevice device;
    private DeviceContext deviceContext;
    private GpuSurface surface;
    private GpuSurface.Configuration surfaceConfiguration;
    private EarlyGraphics graphics;
    private EarlyGraphics.OwnedTexture framebuffer;
    private EarlyGraphics.OwnedTexture presentTarget;

    private RenderElement.DisplayContext context;
    private List<RenderElement> elements;
    private int framecount;
    private ScheduledFuture<?> windowTick;
    private ScheduledFuture<?> initializationFuture;

    private PerformanceInfo performanceInfo;
    private ScheduledFuture<?> performanceTick;
    // The SDL window. Used for all operations
    private long window;
    // The thread that contains and ticks the window while Forge is loading mods
    private ScheduledExecutorService renderScheduler;
    private int fbWidth;
    private int fbHeight;
    private int fbScale;
    private int winWidth;
    private int winHeight;
    private int winX;
    private int winY;

    private final Semaphore renderLock = new Semaphore(1);
    private boolean maximized;

    @Override
    public String name() {
        return "fmlearlywindow";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        final OptionParser parser = new OptionParser();
        var mcversionopt = parser.accepts("fml.mcVersion").withRequiredArg().ofType(String.class);
        var forgeversionopt = parser.accepts("fml.neoForgeVersion").withRequiredArg().ofType(String.class);
        var widthopt = parser.accepts("width")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightopt = parser.accepts("height")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedopt = parser.accepts("earlywindow.maximized");
        parser.allowsUnrecognizedOptions();
        var parsed = parser.parse(arguments);
        winWidth = parsed.valueOf(widthopt);
        winHeight = parsed.valueOf(heightopt);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH, winWidth);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT, winHeight);
        fbScale = FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_FBSCALE);
        if (System.getenv("FML_EARLY_WINDOW_DARK") != null) {
            this.colourScheme = ColourScheme.BLACK;
        } else {
            try {
                var optionLines = Files.readAllLines(FMLPaths.GAMEDIR.get().resolve(Paths.get("options.txt")));
                var options = optionLines.stream().map(l -> l.split(":")).filter(a -> a.length == 2).collect(Collectors.toMap(a -> a[0], a -> a[1]));
                var colourScheme = Boolean.parseBoolean(options.getOrDefault("darkMojangStudiosBackground", "false"));
                this.colourScheme = colourScheme ? ColourScheme.BLACK : ColourScheme.RED;
            } catch (IOException ioe) {
                // No options
                this.colourScheme = ColourScheme.RED; // default to red colourscheme
            }
        }
        this.colourScheme = EarlyConfig.colourScheme(this.colourScheme);
        this.maximized = parsed.has(maximizedopt) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);

        var forgeVersion = parsed.valueOf(forgeversionopt);
        StartupNotificationManager.modLoaderConsumer().ifPresent(c -> c.accept("Fabric loading " + forgeVersion));
        performanceInfo = new PerformanceInfo();
        return start(parsed.valueOf(mcversionopt), forgeVersion);
    }

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window - note animation is capped at 20FPS via the tickTimer
    private long nextFrameTime = 0;

    /**
     * The main render loop.
     * renderThread executes this.
     *
     * Ticks the screen at 20 fps. It binds the OpenGL context of the device for each frame, and releases it after.
     */
    private void renderThreadFunc() {
        if (!renderLock.tryAcquire()) {
            return;
        }
        try {
            if (state != State.RUNNING) {
                return;
            }
            long nt;
            if ((nt = System.nanoTime()) < nextFrameTime) {
                return;
            }
            nextFrameTime = nt + MINFRAMETIME;
            deviceContext.attach();
            DeviceContext.asRenderThread(() -> {
                RenderElement.globalAlpha = 0xFF;
                renderFramebuffer(1f);
                try {
                    presentToWindow();
                } catch (com.mojang.renderpearl.api.device.SurfaceException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (Throwable t) {
            LOGGER.error("BARF", t);
        } finally {
            if (state == State.RUNNING) deviceContext.detach(); // we release the gl context while we're running off the main thread
            renderLock.release();
        }
    }

    /**
     * Render initialization methods called by the Render Thread.
     * It makes the pipelines and textures, and sets up basic render framework.
     *
     * Nothing fancy, we just want to draw and render text.
     */
    private void initRender(final @Nullable String mcVersion, final String forgeVersion) {
        // This thread owns the context now. We should make a note of that.
        deviceContext.attach();
        try {
            DeviceContext.asRenderThread(() -> initGraphics(mcVersion, forgeVersion));
        } catch (Throwable t) {
            LOGGER.error("Crash during early display initialization", t);
            state = State.INACTIVE;
            crashElegantly("An error occurred initializing the early display.\n" + t);
            return;
        } finally {
            deviceContext.detach();
        }
        this.windowTick = renderScheduler.scheduleAtFixedRate(this::renderThreadFunc, 50, 50, TimeUnit.MILLISECONDS);
        this.performanceTick = renderScheduler.scheduleAtFixedRate(performanceInfo::update, 0, 500, TimeUnit.MILLISECONDS);
        // schedule a 50 ms ticker to try and smooth out the rendering
        renderScheduler.scheduleAtFixedRate(() -> animationTimerTrigger.set(true), 1, 50, TimeUnit.MILLISECONDS);
    }

    private void initGraphics(final @Nullable String mcVersion, final String forgeVersion) {
        {
            graphics = new EarlyGraphics(device);
            // we always render to an 854x480 texture and then fit that to the screen - with a scale factor
            this.context = new RenderElement.DisplayContext(854, 480, fbScale, graphics, colourScheme, performanceInfo);
            this.framebuffer = graphics.createRenderTarget("Early display framebuffer", context.scaledWidth(), context.scaledHeight());
            SimpleFont font = new SimpleFont(graphics, "Monocraft.ttf", fbScale, 200000);
            this.elements = new ArrayList<>();
            if (EarlyConfig.showFox()) this.elements.add(RenderElement.fox(graphics, font));
            if (EarlyConfig.showStatus()) this.elements.add(RenderElement.logMessageOverlay(font));
            if (EarlyConfig.showVersion()) this.elements.add(RenderElement.forgeVersionOverlay(font, mcVersion + "-" + forgeVersion.split("-")[0]));
            this.elements.add(RenderElement.performanceBar(font));
            this.elements.add(RenderElement.progressBars(font));

            var date = Calendar.getInstance();
            if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_SQUIR) || (date.get(Calendar.MONTH) == Calendar.APRIL && date.get(Calendar.DAY_OF_MONTH) == 1))
                this.elements.add(0, RenderElement.squir(graphics));

            this.surface = device.createSurface(window, () -> false);
        }
    }

    /**
     * Called every frame to draw the elements. A render pass of the framebuffer must be open.
     */
    void paintFramebuffer() {
        this.elements.removeIf(element -> !element.render(context, framecount));
        if (animationTimerTrigger.compareAndSet(true, false)) // we only increment the framecount on a periodic basis
            framecount++;
    }

    /**
     * Draw the elements to the framebuffer texture.
     */
    private void renderFramebuffer(float alpha) {
        var bg = colourScheme.background();
        graphics.beginPass("Early display", framebuffer.view(), context.scaledWidth(), context.scaledHeight(), bg.redf(), bg.greenf(), bg.bluef(), alpha, true);
        try {
            paintFramebuffer();
        } finally {
            graphics.endPass();
        }
    }

    /**
     * Draw the framebuffer texture in the middle of the window, as large as it can be without a change of its shape.
     */
    private void presentToWindow() throws com.mojang.renderpearl.api.device.SurfaceException {
        refreshWindowSize();
        final int width = this.fbWidth;
        final int height = this.fbHeight;
        if (width <= 0 || height <= 0) {
            return;
        }
        if (surfaceConfiguration == null || surfaceConfiguration.width() != width || surfaceConfiguration.height() != height) {
            var mode = GpuSurface.PresentMode.getSupportedVsyncMode(surface.supportedPresentModes(), true);
            surfaceConfiguration = new GpuSurface.Configuration(width, height, mode);
            surface.configure(surfaceConfiguration);
            if (presentTarget != null) graphics.closeTexture(presentTarget);
            presentTarget = graphics.createRenderTarget("Early display window", width, height);
        }
        if (!surface.isAcquired()) {
            surface.acquireNextTexture();
        }

        var wscale = ((float) width / this.context.width());
        var hscale = ((float) height / this.context.height());
        var scale = this.context.scale() * Math.min(wscale, hscale) / 2f;
        var wleft = RenderElement.clamp((int) (width * 0.5f - scale * this.context.width()), 0, width);
        var wtop = RenderElement.clamp((int) (height * 0.5f - scale * this.context.height()), 0, height);
        var wright = RenderElement.clamp((int) (width * 0.5f + scale * this.context.width()), 0, width);
        var wbottom = RenderElement.clamp((int) (height * 0.5f + scale * this.context.height()), 0, height);

        final var colour = this.context.colourScheme().background();
        graphics.beginPass("Early display window", presentTarget.view(), width, height, colour.redf(), colour.greenf(), colour.bluef(), 1f, false);
        try (var quad = new SimpleBufferBuilder(4 * SimpleBufferBuilder.STRIDE)) {
            quad.begin();
            QuadHelper.loadQuad(quad, wleft, wright, wtop, wbottom, 0f, 1f, 0f, 1f, 0xFFFFFFFF);
            quad.draw(graphics, ElementPipeline.RenderType.TEXTURE, framebuffer.binding());
        } finally {
            graphics.endPass();
        }
        surface.blitFromTexture(graphics.encoder(), presentTarget.view());
        surface.present();
    }

    /**
     * Draw the elements to the framebuffer texture. The loading overlay of the game calls this each frame
     * on the game thread, after the handoff. It then draws the texture.
     */
    public void render(int alpha) {
        RenderElement.globalAlpha = alpha;
        DeviceContext.asRenderThread(() -> renderFramebuffer(alpha / 255f));
    }

    /**
     * Start the window and Render Thread; we're ready to go.
     */
    public Runnable start(@Nullable String mcVersion, final String forgeVersion) {
        try {
            initWindow(mcVersion);
        } catch (Throwable t) {
            LOGGER.error("The early display could not start, so the game starts without it", t);
            shutdownAfterFailure();
            return () -> {};
        }
        renderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final var thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            return thread;
        });
        state = State.RUNNING;
        this.initializationFuture = renderScheduler.schedule(() -> initRender(mcVersion, forgeVersion), 1, TimeUnit.MILLISECONDS);
        return this::periodicTick;
    }

    private void shutdownAfterFailure() {
        state = State.INACTIVE;
        try {
            if (device != null) device.close();
            if (window != 0) SDLVideo.SDL_DestroyWindow(window);
        } catch (Throwable t) {
            LOGGER.debug("Error while the early display shuts down", t);
        }
        device = null;
        backend = null;
        window = 0;
    }

    private static final String ERROR_URL = "https://links.neoforged.net/early-display-errors";

    @Override
    public String getGLVersion() {
        return "3.3";
    }

    private void crashElegantly(String errorDetails) {
        String qrText;
        try (var is = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/glfailure.txt")))) {
            qrText = is.lines().collect(Collectors.joining("\n"));
        } catch (IOException | NullPointerException ioe) {
            qrText = "";
        }

        StringBuilder msgBuilder = new StringBuilder(2000);
        msgBuilder.append("Failed to initialize the mod loading system and display.\n");
        msgBuilder.append("\n\n");
        msgBuilder.append("Failure details:\n");
        msgBuilder.append(errorDetails);
        msgBuilder.append("\n\n");
        msgBuilder.append("If you click yes, we will try and open " + ERROR_URL + " in your default browser");
        LOGGER.error("ERROR DISPLAY\n" + msgBuilder);
        // we show the display on a new dedicated thread
        Executors.newSingleThreadExecutor().submit(() -> {
            var res = TinyFdCompat.messageBox("Minecraft: Fabric", msgBuilder.toString(), "yesno", "error");
            if (res) {
                try {
                    Desktop.getDesktop().browse(URI.create(ERROR_URL));
                } catch (IOException ioe) {
                    TinyFdCompat.messageBox("Minecraft: Fabric", "Sadly, we couldn't open your browser.\nVisit " + ERROR_URL, "ok", "error");
                }
            }
            System.exit(1);
        });
    }

    /**
     * Called to initialize SDL, the RenderPearl OpenGL backend, the device and the window.
     * This runs on the calling thread, and not on the render thread, because some platforms need
     * SDL windows to be made on the main thread. The render thread takes the OpenGL context after this.
     *
     * @param mcVersion Minecraft Version
     */
    public void initWindow(@Nullable String mcVersion) throws BackendCreationException {
        long initBegin = System.nanoTime();
        // Load the native libraries (SDL, OpenGL, shaderc and others) in the same way as the game does
        try {
            NativeLibrariesBootstrap.loadLibraries();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the native libraries", e);
        }
        // Destroy our Vulkan state, the game's VK.create fails when it already exists
        try {
            org.lwjgl.vulkan.VK.destroy();
        } catch (Throwable ignored) {
            // Vulkan was never created
        }
        SDLHints.SDL_SetHint("SDL_NO_SIGNAL_HANDLERS", "1");
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            throw new IllegalStateException("Unable to initialize SDL: " + SDLError.SDL_GetError());
        }
        long initEnd = System.nanoTime();
        if (initEnd - initBegin > 1e9) {
            LOGGER.error("WARNING : SDL took {} seconds to start.", (initEnd - initBegin) / 1.0e9);
        }

        // The early display needs OpenGL, because it must hand its device to the game.
        // The game picks the Vulkan backend only when the user chose it. Then we give up our device and window.
        DeviceContext.prepareSodium();
        backend = new GlBackend();
        backend.loadLibrary();
        var createdBackend = backend;
        device = DeviceContext.asRenderThread(() -> {
            try {
                return createdBackend.createDevice(new GpuDebugOptions(0, false, false, false));
            } catch (BackendCreationException e) {
                throw new IllegalStateException(e);
            }
        });
        deviceContext = new DeviceContext(device);
        // The window is hidden until it is in place
        window = backend.createWindow(EarlyConfig.windowTitle(), winWidth, winHeight, SDLVideo.SDL_WINDOW_RESIZABLE | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY | SDLVideo.SDL_WINDOW_HIDDEN);
        if (window == 0L) {
            throw new IllegalStateException("Unable to make a window: " + SDLError.SDL_GetError());
        }
        deviceContext.detach();

        if (this.maximized) {
            SDLVideo.SDL_MaximizeWindow(window);
        }
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            SDLVideo.SDL_GetWindowSize(window, x, y);
            this.winWidth = x.get(0);
            this.winHeight = y.get(0);
            var bounds = SDL_Rect.malloc(stack);
            var display = SDLVideo.SDL_GetPrimaryDisplay();
            if (display != 0 && SDLVideo.SDL_GetDisplayBounds(display, bounds)) {
                SDLVideo.SDL_SetWindowPosition(window, (bounds.w() - this.winWidth) / 2 + bounds.x(), (bounds.h() - this.winHeight) / 2 + bounds.y());
            }
        }
        SDLVideo.SDL_ShowWindow(window);
        SDLEvents.SDL_PumpEvents();
        refreshWindowSize();
    }

    /**
     * Read the size and position of the window. SDL has the truth, so we do not need event callbacks.
     */
    private void refreshWindowSize() {
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            if (SDLVideo.SDL_GetWindowSize(window, x, y) && x.get(0) != 0 && y.get(0) != 0) {
                this.winWidth = x.get(0);
                this.winHeight = y.get(0);
            }
            if (SDLVideo.SDL_GetWindowPosition(window, x, y)) {
                this.winX = x.get(0);
                this.winY = y.get(0);
            }
            if (SDLVideo.SDL_GetWindowSizeInPixels(window, x, y) && x.get(0) != 0 && y.get(0) != 0) {
                this.fbWidth = x.get(0);
                this.fbHeight = y.get(0);
            }
        }
    }

    /**
     * Wait for the render thread to finish its last frame, and stop it.
     * After this, the context is free and the game thread can take it.
     *
     * @return true when the early display is running and can be handed off
     */
    private boolean stopRenderThread() {
        if (state != State.RUNNING) {
            return false;
        }
        // wait for the window to actually be initialized
        try {
            this.initializationFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            Thread.dumpStack();
            crashElegantly("We seem to be having trouble initializing the window, waited for 30 seconds");
        }
        if (state != State.RUNNING) {
            return false;
        }
        // we have to spin wait for the window ticker
        ImmediateWindowHandler.updateProgress("Initializing Game Graphics");
        while (!this.windowTick.isDone()) {
            this.windowTick.cancel(false);
        }
        var tries = 0;
        var renderlockticket = false;
        do {
            try {
                renderlockticket = renderLock.tryAcquire(100, TimeUnit.MILLISECONDS);
                if (++tries > 9) {
                    Thread.dumpStack();
                    crashElegantly("We seem to be having trouble handing off the window, tried for 1 second");
                }
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
        } while (!renderlockticket);
        // we don't want the lock, just making sure it's back on the main thread
        renderLock.release();
        return true;
    }

    /**
     * Hand-off the device to the game.
     * Called on the main thread instead of the creation of the device of the game.
     *
     * @param gameBackend the backend that the game wants a device from
     * @return our device, or a new device of the game backend if we cannot give ours
     */
    public GpuDevice handOffDevice(GpuBackend gameBackend, GpuDebugOptions options) throws BackendCreationException {
        if (state == State.INACTIVE || !(gameBackend instanceof GlBackend) || !stopRenderThread()) {
            // The user picked another backend, or we did not start. The game makes its own device and window.
            closeBecauseNotUsed();
            return gameBackend.createDevice(options);
        }
        // The game thread owns the context now
        deviceContext.attach();
        DeviceContext.asRenderThread(() -> {
            if (surface != null) {
                // The game makes a surface for the window itself
                surface.close();
                surface = null;
            }
            if (presentTarget != null) {
                graphics.closeTexture(presentTarget);
                presentTarget = null;
            }
        });
        state = State.HANDED_OFF;
        return device;
    }

    /**
     * Hand-off the window to the vanilla game.
     * Called on the main thread instead of the creation of the window of the game.
     *
     * @return the Window we own.
     */
    @Override
    public long setupMinecraftWindow(final IntSupplier width, final IntSupplier height, final Supplier<String> title, final LongSupplier monitorSupplier) {
        // Set the title to what the game wants
        SDLVideo.SDL_SetWindowTitle(window, title.get());
        refreshWindowSize();
        return window;
    }

    public boolean ownsWindow() {
        return state == State.HANDED_OFF;
    }

    /**
     * The game does not use our window and device, so we remove all of them.
     */
    private void closeBecauseNotUsed() {
        if (state == State.INACTIVE && device == null) {
            return;
        }
        LOGGER.info("The game does not use the early display window, so it is closed");
        if (renderScheduler != null) renderScheduler.shutdownNow();
        try {
            if (renderScheduler != null) renderScheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
        state = State.INACTIVE;
        try {
            deviceContext.attach();
            DeviceContext.asRenderThread(() -> {
                if (surface != null) surface.close();
                if (presentTarget != null) graphics.closeTexture(presentTarget);
                if (framebuffer != null) graphics.closeTexture(framebuffer);
                if (graphics != null) graphics.close();
                device.close();
            });
            SDLVideo.SDL_DestroyWindow(window);
        } catch (Throwable t) {
            LOGGER.warn("Error while the early display shuts down", t);
        }
        surface = null;
        device = null;
        window = 0;
    }

    @Override
    public boolean positionWindow(final Optional<Object> monitor, final IntConsumer widthSetter, final IntConsumer heightSetter, final IntConsumer xSetter, final IntConsumer ySetter) {
        if (!ownsWindow()) {
            return false;
        }
        widthSetter.accept(this.winWidth);
        heightSetter.accept(this.winHeight);
        xSetter.accept(this.winX);
        ySetter.accept(this.winY);
        return true;
    }

    @Override
    public void updateFramebufferSize(final IntConsumer width, final IntConsumer height) {
        width.accept(this.fbWidth);
        height.accept(this.fbHeight);
    }

    private Method loadingOverlay;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> loadingOverlay(final Supplier<?> mc, final Supplier<?> ri, final Consumer<Optional<Throwable>> ex, final boolean fade) {
        if (!ownsWindow() || FabricLoader.getInstance().isModLoaded("drippyloadingscreen")) {
            // Use the vanilla overlay. We have no window to draw to or another mod draws the overlay.
            if (ownsWindow()) {
                close();
            }
            return (Supplier<T>) NoVizFallback.loadingOverlay((Supplier<Minecraft>) mc, (Supplier<ReloadInstance>) ri, ex, fade);
        }
        try {
            return (Supplier<T>) loadingOverlay.invoke(null, mc, ri, ex, this);
        } catch (Throwable e) {
            throw new IllegalStateException("How did you get here?", e);
        }
    }

    @Override
    public void updateModuleReads(final ModuleLayer layer) {
        Class<?> clz;
        try {
            clz = Class.forName("com.jab125.earlyloadingscreen.mc12100.NeoForgeLoadingOverlay");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        var methods = Arrays.stream(clz.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())).collect(Collectors.toMap(Method::getName, Function.identity()));
        loadingOverlay = methods.get("newInstance");
    }

    /** The texture that the early display draws to. The loading overlay of the game shows it. */
    public EarlyGraphics.OwnedTexture framebuffer() {
        return this.framebuffer;
    }

    public RenderElement.DisplayContext context() {
        return this.context;
    }

    @Override
    public void periodicTick() {
        if (state == State.RUNNING) {
            SDLEvents.SDL_PumpEvents();
        }
    }

    public void addMojangTexture(final EarlyGraphics.TextureBinding texture) {
        this.elements.add(0, RenderElement.mojang(texture, framecount));
    }

    public void close() {
        if (state == State.INACTIVE) {
            return;
        }
        state = State.INACTIVE;
        // Close the Render Scheduler thread
        renderScheduler.shutdown();
        DeviceContext.asRenderThread(() -> {
            graphics.closeTexture(framebuffer);
            graphics.close();
        });
    }

    @Override
    public void crash(final String message) {
        crashElegantly(message);
    }
}
