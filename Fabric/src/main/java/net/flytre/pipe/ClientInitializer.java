package net.flytre.pipe;

import net.fabricmc.api.ClientModInitializer;
import net.flytre.pipe.impl.client.ItemPipeRenderer;
import net.flytre.pipe.api.ClientPathValidityChecker;
import net.flytre.pipe.impl.FabricItemPipeLogic;
import net.flytre.pipe.impl.registry.ClientRegistry;
import net.flytre.pipe.registry.FabricPipeConfig;

public class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientRegistry.init();
        if (Initializer.CONFIG.getConfig().pipeLogicType == FabricPipeConfig.PipeLogicType.FABRIC) {
            ItemPipeRenderer.addValidityChecker(FabricItemPipeLogic.FABRIC_VALIDITY_CHECKER);
        } else {
            ItemPipeRenderer.addValidityChecker(ClientPathValidityChecker.VANILLA);
        }
    }
}
