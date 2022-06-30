package net.flytre.pipe;

import net.flytre.pipe.impl.client.ItemPipeRenderer;
import net.flytre.pipe.impl.fluid.FluidPipeRenderer;
import net.flytre.pipe.impl.fluid.ForgeFluidPipeLogic;
import net.flytre.pipe.impl.item.ForgeItemPipeLogic;

public class ForgeClientSetup {

    public static void init() {
        ItemPipeRenderer.addValidityChecker(ForgeItemPipeLogic.FORGE_VALIDITY_CHECKER);
        FluidPipeRenderer.addValidityChecker(ForgeFluidPipeLogic.FORGE_VALIDITY_CHECKER);
    }
}
