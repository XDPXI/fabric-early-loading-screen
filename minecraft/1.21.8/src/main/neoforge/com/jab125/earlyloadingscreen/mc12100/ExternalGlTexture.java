package com.jab125.earlyloadingscreen.mc12100;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

// Wraps the early display's framebuffer texture
public class ExternalGlTexture extends GlTexture {
    public ExternalGlTexture(String label, int glId, int width, int height) {
        super(GpuTexture.USAGE_TEXTURE_BINDING, label, TextureFormat.RGBA8, width, height, 1, 1, glId);
    }
}
