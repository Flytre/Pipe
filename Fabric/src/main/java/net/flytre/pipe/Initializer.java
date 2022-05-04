package net.flytre.pipe;

import net.fabricmc.api.ModInitializer;

public class Initializer implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.init();
    }
}
