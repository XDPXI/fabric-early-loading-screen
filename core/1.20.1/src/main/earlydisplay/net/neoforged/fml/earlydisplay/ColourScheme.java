/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

// A record, not an enum, so EarlyConfig can make custom colours
public record ColourScheme(Colour background, Colour foreground) {
    public static final ColourScheme RED = new ColourScheme(new Colour(239, 50, 61), new Colour(255, 255, 255));
    public static final ColourScheme BLACK = new ColourScheme(new Colour(0, 0, 0), new Colour(255, 255, 255));

    public record Colour(int red, int green, int blue) {
        public float redf() {
            return ((float) red) / 255f;
        }

        public float greenf() {
            return ((float) green) / 255f;
        }

        public float bluef() {
            return ((float) blue) / 255f;
        }

        public int packedint(int a) {
            return ((a & 0xff) << 24) | ((blue & 0xff) << 16) | ((green & 0xff) << 8) | (red & 0xff);
        }
    }
}
