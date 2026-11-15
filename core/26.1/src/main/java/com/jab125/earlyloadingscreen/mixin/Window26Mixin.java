package com.jab125.earlyloadingscreen.mixin;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.Window;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

// Replaces WindowMixin, the window is created in createGlfwWindow since 26.1
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
    @Shadow private int framebufferHeight;
    @Shadow private int framebufferWidth;

    @ModifyVariable(method = "<init>", ordinal = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;createWindow(Lcom/mojang/blaze3d/systems/GpuBackend;IILjava/lang/String;J)J", shift = At.Shift.AFTER))
    private Monitor monitor(Monitor monitor) {
        if (!ImmediateWindowHandler.positionWindow(Optional.ofNullable(monitor), w -> width = this.windowedWidth = w, h -> this.height = this.windowedHeight = h, x -> this.x = this.windowedX = x, y -> this.y = this.windowedY = y)) {
            return monitor;
        }
        return null;
    }

    @Redirect(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long init(int width, int height, CharSequence title, long monitor, long share) {
        return ImmediateWindowHandler.setupMinecraftWindow(() -> width, () -> height, () -> (String) title, () -> monitor);
    }

    @Inject(method = "refreshFramebufferSize", at = @At(value = "TAIL"))
    private void updateFramebufferSize(CallbackInfo ci) {
        if (this.framebufferHeight == 0 || this.framebufferWidth == 0) ImmediateWindowHandler.updateFBSize(width -> this.framebufferWidth = width, height -> this.framebufferHeight = height);
    }
}
