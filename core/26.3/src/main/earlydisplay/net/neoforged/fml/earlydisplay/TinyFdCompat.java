package net.neoforged.fml.earlydisplay;

import com.mojang.blaze3d.platform.MessageBox;

// LWJGL has no tinyfd since Minecraft 26.3, so this uses the message boxes of the game (SDL3)
final class TinyFdCompat {
    private TinyFdCompat() {}

    static boolean messageBox(String title, String message, String dialogType, String iconType) {
        if ("yesno".equals(dialogType)) {
            return MessageBox.errorWithContinue(message);
        }
        MessageBox.error(message);
        return false;
    }
}
