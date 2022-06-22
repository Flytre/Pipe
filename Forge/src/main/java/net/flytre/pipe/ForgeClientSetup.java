package net.flytre.pipe;

import net.flytre.pipe.impl.client.ItemPipeRenderer;

public class ForgeClientSetup {

    public static void init() {
        ItemPipeRenderer.addValidityChecker(ForgeItemPipeLogic.FORGE_VALIDITY_CHECKER);
    }
}
