package com.jab125.earlyloadingscreen.util;

import com.jab125.earlyloadingscreen.special.FabricLoaderImplTransformer;
import com.jab125.earlyloadingscreen.special.MixinProcessorTransformer;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.minecraftforge.unsafe.UnsafeHacks;

import java.io.InputStream;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;

public class HooksLaInit implements LanguageAdapter {
    public static boolean failed = false;
    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        throw new Error();
    }

    static {
        try {
            setup();
        } catch (Throwable t) {
            failed = true;
            t.printStackTrace();
        }
        System.out.println("ELS: " + (failed ? "Failed to setup hooks" : "Successfully setup hooks"));
    }

    private static void setup() {
        {
            //if (true) throw new RuntimeException();
            try {
                ByteBuddyAgent.install();
            } catch (IllegalStateException e) {
                // Happens on a JRE without the jdk.attach module (no compatible attachment provider).
                System.out.println("ELS: Cannot attach instrumentation agent (" + e.getMessage() + "). Use a full JDK to get detailed loading progress.");
                failed = true;
                return;
            }
            try {
                InputStream resourceAsStream = HooksLaInit.class.getClassLoader().getResourceAsStream("com/jab125/earlyloadingscreen/needed/Hooks.class");
                byte[] bytes = resourceAsStream.readAllBytes();
                final Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
                UnsafeHacks.setAccessible(defineClass);
                defineClass.invoke(
                        Knot.class.getClassLoader(),
                        "com.jab125.earlyloadingscreen.needed.Hooks",
                        bytes,
                        0,
                        bytes.length
                );
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            try {
                InputStream resourceAsStream = HooksLaInit.class.getClassLoader().getResourceAsStream("com/jab125/earlyloadingscreen/needed/Hooks$MeterDelegate.class");
                byte[] bytes = resourceAsStream.readAllBytes();
                final Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
                UnsafeHacks.setAccessible(defineClass);
                defineClass.invoke(
                        Knot.class.getClassLoader(),
                        "com.jab125.earlyloadingscreen.needed.Hooks$MeterDelegate",
                        bytes,
                        0,
                        bytes.length
                );
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            ByteBuddyAgent.getInstrumentation().addTransformer(new MixinProcessorTransformer(), true);
            ByteBuddyAgent.getInstrumentation().addTransformer(new FabricLoaderImplTransformer(), true);
            try {
                ByteBuddyAgent.getInstrumentation().retransformClasses(FabricLoaderImpl.class);
            } catch (UnmodifiableClassException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
