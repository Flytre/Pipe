package net.flytre.pipe.impl.fluid;

import net.minecraftforge.fluids.FluidStack;

public class FluidUtils {

    public static net.flytre.flytre_lib.api.storage.fluid.core.FluidStack fromForgeStack(FluidStack stack) {
        return new net.flytre.flytre_lib.api.storage.fluid.core.FluidStack(stack.getFluid(), stack.getAmount() * 81L, stack.getTag());
    }

    public static FluidStack fromFlytreStack(net.flytre.flytre_lib.api.storage.fluid.core.FluidStack stack) {
        return new FluidStack(stack.getFluid(), (int) (stack.getAmount() / 81), stack.getNbt());
    }
}
