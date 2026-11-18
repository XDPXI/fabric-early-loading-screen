package com.jab125.earlyloadingscreen.needed;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Hooks {
    public static Consumer<String> stringConsumer = s -> {};
    public static BiConsumer<String, MeterDelegate> onCreate = (s, b) -> {};
    public static BiConsumer<MeterDelegate, String> stringBiConsumer = (s, b) -> {};
    public static BiConsumer<MeterDelegate, String> stringBiConsumer2 = (s, b) -> {};
    private static final HashMap<Collection<EntrypointContainer<?>>, MeterDelegate> map = new HashMap<>();
    private static boolean hookSetup;
    // Early display loads game classes, so it must wait until mods add their mixin configs in preLaunch
    private static boolean preLaunchRunning;
    // Quilt calls the key pre_launch
    private static boolean isPreLaunch(String s) {
        return "preLaunch".equals(s) || "pre_launch".equals(s);
    }

    public static void entrypoint(String s) {
        if (isPreLaunch(s)) {
            preLaunchRunning = true;
        }
        setupHooks();
        stringConsumer.accept("Running entrypoint: " + s);
    }

    public static void mixinInit() {
        // Not starting the early display here, Mixin holds its lock and loaded classes would get no mixins
        stringConsumer.accept("Setting up mixin configs.");
    }

    public static void mixinPostInit() {
      //  setupHooks();
        //stringConsumer.accept("Finished setting up mixin configs.");
    }

    public static void entrypointDone(String s) {
        if (isPreLaunch(s)) {
            preLaunchRunning = false;
            setupHooks();
        }
    }

    private static void setupHooks() {
        if (hookSetup || preLaunchRunning) { return; }
        try {
            System.out.println("Setting up hooks");
            FabricLoader.getInstance().getEntrypoints("early-loading-screen-hook-provider", Runnable.class).get(0).run();
            hookSetup = true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static void setup(String s2, Collection<EntrypointContainer<?>> entrypointContainers) {
        setupHooks();
        try {
            Class<MeterDelegate> aClass = (Class<MeterDelegate>) FabricLoader.class.getClassLoader().loadClass("com.jab125.earlyloadingscreen.needed.Hooks$MeterDelegate");
            System.out.println(aClass.getClassLoader());
            MeterDelegate s = aClass.getConstructor(int.class).newInstance(entrypointContainers.size());
            map.put(entrypointContainers, s);
            if (onCreate != null) {
                onCreate.accept(s2, s);
            }
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void ran(Collection<EntrypointContainer<?>> entrypointContainers, EntrypointContainer<?> container) {
        setupHooks();
        MeterDelegate meterDelegate = map.get(entrypointContainers);
        try {
            Object getProvider = container.getClass().getMethod("getProvider").invoke(container);
            Object getMetadata = getProvider.getClass().getMethod("getMetadata").invoke(getProvider);
            Method getName = getMetadata.getClass().getDeclaredMethod("getName");
            getName.setAccessible(true);
            meterDelegate.update((String) getName.invoke(getMetadata));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
       // meterDelegate.update(container.getProvider().getMetadata().getName());
    }

    public static void ran2(Collection<EntrypointContainer<?>> entrypointContainers, EntrypointContainer<?> container) {
        setupHooks();
        MeterDelegate meterDelegate = map.get(entrypointContainers);
        try {
            Object getProvider = container.getClass().getMethod("getProvider").invoke(container);
            Object getMetadata = getProvider.getClass().getMethod("getMetadata").invoke(getProvider);
            Method getName = getMetadata.getClass().getDeclaredMethod("getName");
            getName.setAccessible(true);
            meterDelegate.finished((String) getName.invoke(getMetadata));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        // meterDelegate.update(container.getProvider().getMetadata().getName());
    }

    // Quilt containers are not Fabric types, so these take Object
    public static void ranQuilt(Collection<?> entrypointContainers, Object container) {
        setupHooks();
        MeterDelegate meterDelegate = map.get(entrypointContainers);
        try {
            meterDelegate.update(quiltModName(container));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void ran2Quilt(Collection<?> entrypointContainers, Object container) {
        setupHooks();
        MeterDelegate meterDelegate = map.get(entrypointContainers);
        try {
            meterDelegate.finished(quiltModName(container));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String quiltModName(Object container) throws ReflectiveOperationException {
        Object provider = call(container, "getProvider");
        Object metadata = call(provider, "metadata");
        return (String) call(metadata, "name");
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method;
        try {
            method = target.getClass().getMethod(name);
        } catch (NoSuchMethodException e) {
            method = target.getClass().getDeclaredMethod(name);
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    public static class MeterDelegate {

        public final int size;

        public MeterDelegate(int size) {
            this.size = size;
        }
        public void update(String modName) {
            if (stringBiConsumer != null) {
                stringBiConsumer.accept(this, modName);
            }
        }

        public void finished(String modName) {
            if (stringBiConsumer2 != null) {
                stringBiConsumer2.accept(this, modName);
            }
        }
    }
}
