package net.flytre.pipe.impl;

import net.flytre.flytre_lib.api.storage.inventory.filter.FilterInventory;
import net.flytre.pipe.api.ResourceHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.registry.Registry;

public class ItemResourceHandler implements ResourceHandler<ItemStack, FilterInventory> {

    public static ItemResourceHandler INSTANCE = new ItemResourceHandler();

    private ItemResourceHandler() {

    }

    @Override
    public ItemStack readNbt(NbtCompound compound) {
        return ItemStack.fromNbt(compound);
    }

    @Override
    public NbtCompound writeFilterNbt(NbtCompound compound, ItemStack resource) {
        return resource.writeNbt(compound);
    }

    @Override
    public ItemStack copy(ItemStack in) {
        return in.copy();
    }

    @Override
    public ItemStack copyWithSingleUnit(ItemStack in) {
        ItemStack copy = in.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    public boolean equals(ItemStack left, Object right) {
        if (!(right instanceof ItemStack rightStack))
            return false;
        return ItemStack.areEqual(left, rightStack);
    }

    @Override
    public int getHashCode(ItemStack in) {
        if (in == null)
            return 0;

        return Registry.ITEM.getRawId(in.getItem()) +
                in.getCount() * 31 +
                (in.getNbt() != null ? in.getNbt().hashCode() * 31 * 31 : 0);
    }

    @Override
    public FilterInventory getDefaultFilter() {
        return FilterInventory.readNbt(new NbtCompound(), 1);
    }

    @Override
    public Class<ItemStack> getResourceClass() {
        return ItemStack.class;
    }

    @Override
    public NbtCompound writeFilterNbt(FilterInventory filter) {
        return filter.writeNbt();
    }


    @Override
    public FilterInventory readFilterNbt(NbtCompound compound) {
        return FilterInventory.readNbt(compound, 1);
    }

    @Override
    public void writeFilter(PacketByteBuf packetByteBuf, FilterInventory filter) {
        packetByteBuf.writeInt(filter.getFilterType());
        packetByteBuf.writeBoolean(filter.isMatchMod());
        packetByteBuf.writeBoolean(filter.isMatchNbt());
    }
}
