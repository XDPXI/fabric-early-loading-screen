package com.jab125.earlyloadingscreen.mixin;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.jab125.earlyloadingscreen.Loader;

@Mixin(Main.class)
public class MainMixin {
    // ClientBootstrap needs the registries bootstrapped since 1.21.4
    @Redirect(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;bootStrap()V"))
    private static void runBootstrapWithEarlyWindow() {
        Loader.bootstrapAndTick();
    }
}
