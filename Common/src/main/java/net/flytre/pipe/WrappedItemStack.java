package net.flytre.pipe;

import net.minecraft.item.ItemStack;

/**
 * the WrappedItemStack class is used to give ItemStacks a hashCode and equals
 */
record WrappedItemStack(ItemStack stack) {
    @Override
    public boolean equals(Object wrapped) {
        if (this == wrapped)
            return true;

        if (wrapped == null || getClass() != wrapped.getClass())
            return false;

        return ItemStack.areEqual(this.stack, ((WrappedItemStack) wrapped).stack);
    }

    @Override
    public int hashCode() {
        if (stack == null)
            return 0;

        return net.minecraft.util.registry.Registry.ITEM.getRawId(stack.getItem()) +
                stack.getCount() * 31 +
                (stack.getNbt() != null ? stack.getNbt().hashCode() * 31 * 31 : 0);
    }
}
