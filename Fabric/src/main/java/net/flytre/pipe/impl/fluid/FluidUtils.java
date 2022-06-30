package net.flytre.pipe.impl.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;

@SuppressWarnings("UnstableApiUsage")
public class FluidUtils {

    public static FluidVariant variantOf(FluidStack stack) {
        return FluidVariant.of(stack.getFluid(), stack.getNbt() == null ? null : stack.getNbt().copy());
    }

    public static FluidStack stackOf(FluidVariant variant) {
        return stackOf(variant, 1);
    }

    public static FluidStack stackOf(FluidVariant variant, long amount) {
        return new FluidStack(variant.getFluid(), amount, variant.getNbt() == null ? null : variant.getNbt().copy());
    }
}
