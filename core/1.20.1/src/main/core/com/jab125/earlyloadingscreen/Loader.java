package com.jab125.earlyloadingscreen;

//import net.fabricmc.devlaunchinjector.Main;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.BackgroundWaiter;
import net.neoforged.fml.loading.ImmediateWindowHandler;

public class Loader {
    public static Runnable progressWindowTick;

    // Not done in the mixin, some Mixin versions fail to attach lambdas in redirect handlers
    public static void bootstrapAndTick() {
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
