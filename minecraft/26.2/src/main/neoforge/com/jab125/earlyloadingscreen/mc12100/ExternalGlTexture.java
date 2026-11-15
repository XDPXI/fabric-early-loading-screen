package com.jab125.earlyloadingscreen.mc12100;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

// Wraps the early display's framebuffer texture
public class ExternalGlTexture extends GlTexture {
    public ExternalGlTexture(String label, int glId, int width, int height) {
        super(GpuTexture.USAGE_TEXTURE_BINDING, label, GpuFormat.RGBA8_UNORM, width, height, 1, 1, glId, ((GlDevice) RenderSystem.getDevice().backend).frameBufferCache());
    }
}
