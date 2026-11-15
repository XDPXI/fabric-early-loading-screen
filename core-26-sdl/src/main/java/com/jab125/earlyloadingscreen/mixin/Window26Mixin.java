package com.jab125.earlyloadingscreen.mixin;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.device.GpuBackend;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

// Gives the game the window of the early display. The game makes windows with the backend (SDL3) since 26.3.
@Mixin(Window.class)
public abstract class Window26Mixin {
    @Shadow private int width;
    @Shadow private int windowedWidth;
    @Shadow private int height;
    @Shadow private int windowedHeight;
    @Shadow private int x;
    @Shadow private int windowedX;
    @Shadow private int y;
    @Shadow private int windowedY;

    // The window keeps the size and position of the early display, so the game does not move it to a monitor
    @ModifyVariable(method = "<init>", ordinal = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;createWindow(Lcom/mojang/renderpearl/api/device/GpuBackend;IILjava/lang/String;)J", shift = At.Shift.AFTER))
    private Monitor monitor(Monitor monitor) {
        if (!ImmediateWindowHandler.positionWindow(Optional.ofNullable(monitor), w -> width = this.windowedWidth = w, h -> this.height = this.windowedHeight = h, x -> this.x = this.windowedX = x, y -> this.y = this.windowedY = y)) {
            return monitor;
        }
        return null;
    }

    @Redirect(method = "createWindow", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"))
    private long takeWindow(GpuBackend backend, String title, int width, int height, long flags) {
        return ImmediateWindowHandler.takeWindow(backend, title, width, height, flags);
    }
}
