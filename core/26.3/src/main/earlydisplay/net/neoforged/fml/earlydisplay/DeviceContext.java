/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.sdl.SDLVideo;

/**
 * Moves the OpenGL context of a RenderPearl device from one thread to another.
 *
 * RenderPearl has no public API for this. It creates the context in the constructor of the device,
 * and the context stays current on the thread that made the device. The early display
 * draws on a thread of its own and then gives the device to the game thread. Because of this, we
 * must release and bind the context by hand, and tell the device which window is current.
 * This class is the only place where the early display does that. All drawing uses the RenderPearl API.
 */
final class DeviceContext {
    // RenderSystem checks that the OpenGL calls of RenderPearl come from its render thread. Before the game starts
    // there is none, and the early display draws on its own thread. The game also sets this field once, and
    // fails if it is set already. Because of this, we set the field for the time of our own calls and put it back.
    private static final Field RENDER_THREAD;
    // The shader compiler of RenderPearl reads the device from RenderSystem. The game sets it later.
    private static final Field RENDER_SYSTEM_DEVICE;

    static {
        try {
            RENDER_THREAD = RenderSystem.class.getDeclaredField("renderThread");
            RENDER_THREAD.setAccessible(true);
            RENDER_SYSTEM_DEVICE = RenderSystem.class.getDeclaredField("DEVICE");
            RENDER_SYSTEM_DEVICE.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RenderSystem is not what the early display expects", e);
        }
    }

    // Give Sodium default options, it throws "Config not yet available" before the game starts
    static void prepareSodium() {
        try {
            Class<?> mod = Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod", true, DeviceContext.class.getClassLoader());
            Field options = mod.getDeclaredField("OPTIONS");
            options.setAccessible(true);
            if (options.get(null) == null) {
                Class<?> optionsClass = Class.forName("net.caffeinemc.mods.sodium.client.gui.SodiumOptions", true, DeviceContext.class.getClassLoader());
                options.set(null, optionsClass.getMethod("defaults").invoke(null));
            }
        } catch (ClassNotFoundException ignored) {
            // No Sodium
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Device creation reports the real problem
        }
    }

    /** Run something that calls RenderPearl on the current thread, and treat this thread as the render thread. */
    static <T> T asRenderThread(Supplier<T> action) {
        try {
            Thread previous = (Thread) RENDER_THREAD.get(null);
            RENDER_THREAD.set(null, Thread.currentThread());
            try {
                return action.get();
            } finally {
                RENDER_THREAD.set(null, previous);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Make RenderSystem.getDevice() return the early display device for the time of the action. */
    static void withRenderSystemDevice(GpuDevice device, Runnable action) {
        try {
            Object previous = RENDER_SYSTEM_DEVICE.get(null);
            RENDER_SYSTEM_DEVICE.set(null, previous != null ? previous : device);
            try {
                action.run();
            } finally {
                RENDER_SYSTEM_DEVICE.set(null, previous);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void asRenderThread(Runnable action) {
        asRenderThread(() -> {
            action.run();
            return null;
        });
    }

    private final GpuDevice frontend;
    private final Object glDevice;
    private final Method makeCurrent;
    private final Field currentWindow;
    private final long initialWindow;
    private final GLCapabilities capabilities;

    /**
     * Call this on the thread that made the device, before another thread uses the device.
     */
    DeviceContext(GpuDevice frontend) {
        this.frontend = frontend;
        try {
            var backendField = frontend.getClass().getDeclaredField("backend");
            backendField.setAccessible(true);
            this.glDevice = backendField.get(frontend);
            var glClass = glDevice.getClass();
            this.makeCurrent = glClass.getDeclaredMethod("makeCurrent", long.class);
            this.makeCurrent.setAccessible(true);
            this.currentWindow = glClass.getDeclaredField("currentWindow");
            this.currentWindow.setAccessible(true);
            var initialWindowField = glClass.getDeclaredField("initialWindowHandle");
            initialWindowField.setAccessible(true);
            this.initialWindow = initialWindowField.getLong(glDevice);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The RenderPearl OpenGL device is not what the early display expects", e);
        }
        // The device made these capabilities for this thread. Each thread that uses the context needs them.
        this.capabilities = GL.getCapabilities();
    }

    GpuDevice device() {
        return frontend;
    }

    /** Make the context current on the calling thread. */
    void attach() {
        GL.setCapabilities(capabilities);
        try {
            // The device remembers the current window and does nothing when it is the same one.
            currentWindow.setLong(glDevice, -1L);
            makeCurrent.invoke(glDevice, initialWindow);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Release the context from the calling thread. */
    void detach() {
        SDLVideo.SDL_GL_MakeCurrent(initialWindow, 0L);
        try {
            currentWindow.setLong(glDevice, -1L);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        GL.setCapabilities(null);
    }
}
