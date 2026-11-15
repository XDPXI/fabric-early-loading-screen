package com.jab125.earlyloadingscreen.mixin;

import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Gives the game the device of the early display, so the early display and the game share one OpenGL context
@Mixin(Minecraft.class)
public abstract class MinecraftDeviceMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createDevice(Lcom/mojang/renderpearl/api/device/GpuDebugOptions;)Lcom/mojang/renderpearl/api/device/GpuDevice;"))
    private GpuDevice takeDevice(GpuBackend backend, GpuDebugOptions options) throws BackendCreationException {
        return ImmediateWindowHandler.takeDevice(backend, options);
    }
}
