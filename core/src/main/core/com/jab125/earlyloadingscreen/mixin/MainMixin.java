package com.jab125.earlyloadingscreen.mixin;

import net.minecraft.client.main.Main;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static com.jab125.earlyloadingscreen.Loader.progressWindowTick;

@Mixin(Main.class)
public class MainMixin {
    // ClientBootstrap needs the registries bootstrapped since 1.21.4
    @Redirect(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;bootStrap()V"))
    private static void runBootstrapWithEarlyWindow() {
        net.neoforged.fml.loading.BackgroundWaiter.runAndTick(Bootstrap::bootStrap, progressWindowTick);
    }
}
