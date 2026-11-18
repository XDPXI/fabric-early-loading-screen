package com.jab125.earlyloadingscreen.mixin;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.earlydisplay.EarlyGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// RRLS reads Minecraft.getInstance().gui in the command encoder, which is null during early display
@Pseudo
@Mixin(targets = "org.redlance.dima_dencep.mods.rrls.utils.OverlayHelper", remap = false)
public abstract class RrlsOverlayHelperMixin {
    @Inject(method = "isCurrentRenderingState", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void noGameYet(CallbackInfoReturnable<Boolean> cir) {
        if (EarlyGraphics.isCreatingPass() || Minecraft.getInstance() == null) {
            cir.setReturnValue(false);
        }
    }
}
