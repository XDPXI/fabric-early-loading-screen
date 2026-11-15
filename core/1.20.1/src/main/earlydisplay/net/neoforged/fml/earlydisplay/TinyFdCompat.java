package net.neoforged.fml.earlydisplay;

import java.lang.reflect.Method;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

// tinyfd_messageBox uses booleans in LWJGL 3.3 and ints in 3.4, so call it reflectively
final class TinyFdCompat {
    private TinyFdCompat() {}

    static boolean messageBox(String title, String message, String dialogType, String iconType) {
        try {
            for (Method method : TinyFileDialogs.class.getMethods()) {
                var types = method.getParameterTypes();
                if (!method.getName().equals("tinyfd_messageBox") || types.length != 5 || types[0] != CharSequence.class) continue;
                Object defaultButton = types[4] == boolean.class ? (Object) Boolean.FALSE : (Object) Integer.valueOf(0);
                Object result = method.invoke(null, title, message, dialogType, iconType, defaultButton);
                return result instanceof Boolean yes ? yes : result instanceof Integer i && i != 0;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to show message box", e);
        }
        throw new IllegalStateException("No compatible tinyfd_messageBox found");
    }
}
