/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import com.mojang.renderpearl.api.device.GpuDevice;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
