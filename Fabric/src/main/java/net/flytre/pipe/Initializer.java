package net.flytre.pipe;

import net.fabricmc.api.ModInitializer;
import net.flytre.flytre_lib.api.config.ConfigHandler;
import net.flytre.flytre_lib.api.config.ConfigRegistry;
import net.flytre.pipe.registry.FabricPipeConfig;
import net.flytre.pipe.impl.registry.Registry;

public class Initializer implements ModInitializer {

    public static final ConfigHandler<FabricPipeConfig> CONFIG = new ConfigHandler<>(new FabricPipeConfig(), "pipe_fabric");

    static {
        CONFIG.handle();
        ConfigRegistry.registerServerConfig(CONFIG);
    }

    @Override
    public void onInitialize() {
        Registry.init();

    }
}
