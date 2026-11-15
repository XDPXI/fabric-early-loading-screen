package com.jab125.earlyloadingscreen.mixin.mc26;

import com.jab125.earlyloadingscreen.mixin.SplashOverlayAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

// The overlay is owned by Gui and GameLoadCookie is top-level since 26.2
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Unique
    private Overlay overlay;
    @Unique
    private GameLoadCookie context;
    @Shadow protected abstract void selfTest();
    @Shadow @Final private ResourceLoadStateTracker reloadStateTracker;
    @Shadow protected abstract void rollbackResourcePacks(Throwable throwable, @Nullable GameLoadCookie loadingContext);
    @Shadow protected abstract void onResourceLoadFinished(@Nullable GameLoadCookie loadingContext);

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(method = "<init>", at = @At("STORE"))
    private GameLoadCookie captureVar(GameLoadCookie context) {
        return this.context = context;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"))
    private void redirectSetOverlay(Gui instance, Overlay overlay) {
        ImmediateWindowHandler.acceptGameLayer(null);
        this.overlay = overlay;
        var g = ImmediateWindowHandler.<LoadingOverlay>loadingOverlay(() -> this, this::get, this::accept, false).get();
        instance.setOverlay(g);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Unique
    private void accept(Optional<Throwable> error) {
        if (error.isPresent()) {
            this.rollbackResourcePacks(error.get(), context);
        } else {
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                this.selfTest();
            }

            this.reloadStateTracker.finishReload();
            this.onResourceLoadFinished(context);
        }
    }

    @Unique
    private Object get() {
        SplashOverlayAccessor overlay2 = (SplashOverlayAccessor) overlay;
        return overlay2.getReload();
    }
}
