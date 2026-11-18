package com.jab125.earlyloadingscreen.config;

import net.fabricmc.api.ClientModInitializer;

public class ELSClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ELSConfig.init(ELSConfig.MOD_ID, ELSConfig.class);
    }
}
