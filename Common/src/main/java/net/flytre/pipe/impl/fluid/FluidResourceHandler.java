package net.flytre.pipe.impl.fluid;

import net.flytre.flytre_lib.api.storage.fluid.core.FluidFilterInventory;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;
import net.flytre.pipe.api.ResourceHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public class FluidResourceHandler implements ResourceHandler<FluidStack, FluidFilterInventory> {

    public static final FluidResourceHandler INSTANCE = new FluidResourceHandler();

    private FluidResourceHandler() {

    }

    @Override
    public FluidStack readNbt(NbtCompound compound) {
        return FluidStack.readNbt(compound);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound compound, FluidStack resource) {
        return resource.writeNbt(compound);
    }

    @Override
    public boolean equals(FluidStack left, Object right) {
        if (!(right instanceof FluidStack rightStack))
            return false;
        return FluidStack.areEqual(left, rightStack);
    }

    @Override
    public int getHashCode(FluidStack stack) {
        return stack.hashCode();
    }

    @Override
    public FluidFilterInventory getDefaultFilter() {
        return FluidFilterInventory.readNbt(new NbtCompound(), 1);
    }

    @Override
    public Class<FluidStack> getResourceClass() {
        return FluidStack.class;
    }

    @Override
    public NbtCompound writeFilterNbt(FluidFilterInventory filter) {
        return filter.writeNbt();
    }

    @Override
    public FluidFilterInventory readFilterNbt(NbtCompound compound) {
        return FluidFilterInventory.readNbt(compound, 1);
    }

    @Override
    public void writeFilter(PacketByteBuf buf, FluidFilterInventory filter) {
        buf.writeInt(filter.getFilterType());
        buf.writeBoolean(filter.isMatchMod());
        buf.writeBoolean(filter.isMatchNbt());
    }

    @Override
    public long getQuantity(FluidStack stack) {
        return stack.getAmount();
    }

    @Override
    public FluidStack copyWithQuantity(FluidStack stack, long amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
