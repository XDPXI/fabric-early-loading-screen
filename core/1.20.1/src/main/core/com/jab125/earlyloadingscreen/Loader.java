package com.jab125.earlyloadingscreen;

//import net.fabricmc.devlaunchinjector.Main;
import com.jab125.earlyloadingscreen.util.HooksSetup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.BackgroundWaiter;
import net.neoforged.fml.loading.ImmediateWindowHandler;

public class Loader {
    public static Runnable progressWindowTick;

    // Not done in the mixin, some Mixin versions fail to attach lambdas in redirect handlers
    public static void bootstrapAndTick() {
        // No-op if the early display already started
        HooksSetup.init(FabricLoader.getInstance().getLaunchArguments(true));
        BackgroundWaiter.runAndTick(Bootstrap::bootStrap, progressWindowTick);
    }

//    public static void main(String[] args) throws Throwable {
//        ImmediateWindowHandler.load("forgeclient", args);
//        //while(true)
//        net.minecraftforge.fml.loading.BackgroundWaiter.runAndTick(() -> {}, progressWindowTick);
//        Main.main(args);
//        while (true) {
//            progressWindowTick.run();
//            Thread.sleep(50);
//        }
//        //ImmediateWindowHandler.loadingOverlay(() -> new Object(), () -> null, ex -> {}, true);
////        while (true) {
////            ImmediateWindowHandler.renderTick();
////            progressWindowTick.run();
////        }
//
//    }

}
