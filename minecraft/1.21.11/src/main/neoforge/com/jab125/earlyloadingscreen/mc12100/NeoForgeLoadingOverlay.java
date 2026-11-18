/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.jab125.earlyloadingscreen.mc12100;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * This is an implementation of the LoadingOverlay that calls back into the early window rendering, as part of the
 * game loading cycle. We completely replace the {@link #render(GuiGraphics, int, int, float)} call from the parent
 * with one of our own, that allows us to blend our early loading screen into the main window, in the same manner as
 * the Mojang screen. It also allows us to see and tick appropriately as the later stages of the loading system run.
 */
public class NeoForgeLoadingOverlay extends LoadingOverlay {
    public static final Identifier LOADING_OVERLAY_TEXTURE_ID = Identifier.parse("neoforge:loading_overlay");
    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final DisplayWindow displayWindow;
    private final ProgressMeter progressMeter;
    private float currentProgress;
    // Mirrors the private LoadingOverlay#fadeOutStart
    private long fadeOutStart = -1L;

    public NeoForgeLoadingOverlay(final Minecraft mc, final ReloadInstance reloader, final Consumer<Optional<Throwable>> errorConsumer, DisplayWindow displayWindow) {
        super(mc, reloader, errorConsumer, false);
        this.minecraft = mc;
        this.reload = reloader;
        this.displayWindow = displayWindow;
        var logoTexture = (GlTexture) mc.getTextureManager().getTexture(Identifier.withDefaultNamespace("textures/gui/title/mojangstudios.png")).getTexture();
        displayWindow.addMojangTexture(logoTexture.glId());
        this.progressMeter = StartupNotificationManager.prependProgressBar("Minecraft Progress", 1000);
        var framebuffer = new ExternalGlTexture("loading overlay framebuffer", displayWindow.getFramebufferTextureId(), displayWindow.context().width() * displayWindow.context().scale(), displayWindow.context().height() * displayWindow.context().scale());
        mc.getTextureManager().register(LOADING_OVERLAY_TEXTURE_ID, new ExternalTexture(framebuffer));
    }

    public static Supplier<LoadingOverlay> newInstance(Supplier<Minecraft> mc, Supplier<ReloadInstance> ri, Consumer<Optional<Throwable>> handler, DisplayWindow window) {
        return () -> new NeoForgeLoadingOverlay(mc.get(), ri.get(), handler, window);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fadeOutStart == -1L && this.reload.isDone()) {
            this.fadeOutStart = Util.getMillis();
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        long millis = Util.getMillis();
        float fadeouttimer = this.fadeOutStart > -1L ? (float) (millis - this.fadeOutStart) / 1000.0F : -1.0F;
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + this.reload.getActualProgress() * 0.05F, 0.0F, 1.0F);
        progressMeter.setAbsolute(Mth.ceil(this.currentProgress * 1000));
        var fade = 1.0F - Mth.clamp(fadeouttimer - 1.0F, 0.0F, 1.0F);
        var colour = this.displayWindow.context().colourScheme().background();

        if (fadeouttimer >= 1.0F && this.minecraft.screen != null && graphics.pose() != null) {
            this.minecraft.screen.render(graphics, 0, 0, partialTick);
        }

        // This updates the EarlyDisplay screen in its off-screen framebuffer
        displayWindow.render(0xFF);
        // Make sure the GlStateManager's cache is aware of the early display's GL state
        GlStateManager._activeTexture(GlConst.GL_TEXTURE0);
        GlStateManager._bindTexture(0);
        GlStateManager._enableBlend();
        GlStateManager._disableBlend();

        var fbWidth = this.minecraft.getWindow().getWidth();
        var fbHeight = this.minecraft.getWindow().getHeight();
        final var twidth = this.displayWindow.context().width();
        final var theight = this.displayWindow.context().height();
        var wscale = (float) fbWidth / twidth;
        var hscale = (float) fbHeight / theight;
        var scale = this.displayWindow.context().scale() * Math.min(wscale, hscale) / 2f;
        int wleft = (int) Mth.clamp(fbWidth * 0.5f - scale * twidth, 0, fbWidth);
        int wtop = (int) Mth.clamp(fbHeight * 0.5f - scale * theight, 0, fbHeight);
        int wright = (int) Mth.clamp(fbWidth * 0.5f + scale * twidth, 0, fbWidth);
        int wbottom = (int) Mth.clamp(fbHeight * 0.5f + scale * theight, 0, fbHeight);
        int texWidth = twidth * this.displayWindow.context().scale();
        int texHeight = theight * this.displayWindow.context().scale();

        // Draw above the screen below
        graphics.nextStratum();
        // The early display uses window pixels, not GUI-scaled units
        float guiScale = (float) this.minecraft.getWindow().getGuiScale();
        graphics.pose().pushMatrix();
        graphics.pose().scale(1f / guiScale, 1f / guiScale);
        // This is fill in around the edges - it's empty solid colour
        graphics.fill(0, 0, fbWidth, fbHeight, ARGB.colorFromFloat(fade, colour.redf(), colour.greenf(), colour.bluef()));
        // This is the actual screen data from the loading screen
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOADING_OVERLAY_TEXTURE_ID, wleft, wtop, 0f, 0f, wright - wleft, wbottom - wtop, texWidth, texHeight, texWidth, texHeight, ARGB.colorFromFloat(fade, 1, 1, 1));
        graphics.pose().popMatrix();

        if (fadeouttimer >= 2.0F) {
            progressMeter.complete();
            this.minecraft.setOverlay(null);
            this.minecraft.schedule(() -> {
                this.minecraft.getTextureManager().release(LOADING_OVERLAY_TEXTURE_ID);
                this.displayWindow.close();
            });
        }
    }

    static class ExternalTexture extends AbstractTexture {
        public ExternalTexture(GpuTexture texture) {
            this.texture = texture;
            this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
            this.textureView = RenderSystem.getDevice().createTextureView(texture);
        }
    }
}
