package net.flytre.pipe.impl;

import net.flytre.flytre_lib.api.base.util.InventoryUtils;
import net.flytre.flytre_lib.api.storage.connectable.ItemPipeConnectable;
import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.flytre.pipe.api.*;
import net.minecraft.block.InventoryProvider;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.function.Predicate;

public class VanillaItemPipeLogic implements PipeLogic<ItemStack> {

    public static VanillaItemPipeLogic INSTANCE = new VanillaItemPipeLogic();

    private VanillaItemPipeLogic() {

    }

    private static final InsertionChecker<ItemStack> INSERTION_CHECKER = new InsertionChecker<>() {
        @Override
        public boolean canInsert(World world, ItemStack stack, BlockPos pos, Direction direction, boolean isStuck, int flowCount) {
            Inventory destination = InventoryUtils.getInventoryAt(world, pos);
            if (destination == null)
                return false;

            if (flowCount == 0) {
                return InventoryUtils.getAvailableSlots(destination, direction.getOpposite()).anyMatch(i -> {
                    ItemStack slotStack = destination.getStack(i);
                    if (InventoryUtils.canInsert(destination, stack, i, direction.getOpposite()))
                        return slotStack.isEmpty() || (InventoryUtils.canMergeItems(slotStack, stack) && slotStack.getCount() < slotStack.getMaxCount());
                    return false;
                });
            }


            //basically, estimate how many items are going into the container and if it's going to be full, don't send the item
            int[] slots = InventoryUtils.getAvailableSlots(destination, direction.getOpposite()).toArray();
            ItemStack copy = stack.copy();
            copy.setCount(flowCount + 1);
            for (int slot : slots) {
                ItemStack slotStack = destination.getStack(slot);

                if (slotStack.getCount() >= slotStack.getMaxCount())
                    continue;

                if (!InventoryUtils.canInsert(destination, copy, slot, direction.getOpposite())) {
                    continue;
                }
                if (slotStack.isEmpty()) {
                    if (copy.getCount() < destination.getMaxCountPerStack())
                        return true;
                    else
                        copy.decrement(destination.getMaxCountPerStack());
                } else if (InventoryUtils.canMergeItems(slotStack, copy)) {
                    int slotMaxCount = slotStack.getMaxCount() == 64 ? Math.max(slotStack.getCount(), destination.getMaxCountPerStack()) : slotStack.getMaxCount();
                    int target = slotMaxCount - slotStack.getCount();

                    if (copy.getCount() <= target)
                        return true;
                    else
                        copy.decrement(target);
                }
            }
            return false;

        }
    };

    private static final PipeConnectable<ItemStack> PIPE_CONNECTABLE = (world, pos, direction, block, entity) -> block instanceof ItemPipeConnectable || block instanceof InventoryProvider || (entity instanceof Inventory && ((Inventory) entity).size() > 0);

    private static final StorageExtractor<ItemStack> STORAGE_EXTRACTOR = new StorageExtractor<>() {
        @Override
        public boolean extract(World world, BlockPos pipePosition, Direction direction, ResourceFilter<? super ItemStack> filter, Predicate<ItemStack> pipeHandler) {
            Inventory out = InventoryUtils.getInventoryAt(world, pipePosition.offset(direction));
            Direction opp = direction.getOpposite();

            if (out == null || InventoryUtils.isInventoryEmpty(out, opp))
                return false;

            int[] arr = InventoryUtils.getAvailableSlots(out, opp).toArray();
            for (int i : arr) {
                ItemStack stack = out.getStack(i);
                if (!InventoryUtils.canExtract(out, stack, i, opp) || (!filter.isEmpty() && !filter.passFilterTest(stack)))
                    continue;

                if (stack.isEmpty())
                    continue;
                if (pipeHandler.test(stack)) {
                    stack.decrement(1);
                    out.markDirty();
                    return true;
                }
            }

            return false;
        }
    };

    private static final StorageFinder<ItemStack> STORAGE_FINDER = (world, pos, dir) -> InventoryUtils.getInventoryAt(world, pos) != null;

    private static final StorageInserter<ItemStack> STORAGE_INSERTER = (world, pos, direction, stack) -> {
        Inventory inv = InventoryUtils.getInventoryAt(world, pos);
        return inv != null && InventoryUtils.putStackInInventory(stack, inv) != ItemStack.EMPTY;
    };

    @Override
    public InsertionChecker<ItemStack> getInsertionChecker() {
        return INSERTION_CHECKER;
    }

    @Override
    public PipeConnectable<ItemStack> getPipeConnectable() {
        return PIPE_CONNECTABLE;
    }

    @Override
    public StorageExtractor<ItemStack> getStorageExtractor() {
        return STORAGE_EXTRACTOR;
    }

    @Override
    public StorageFinder<ItemStack> getStorgeFinder() {
        return STORAGE_FINDER;
    }

    @Override
    public StorageInserter<ItemStack> getStorageInserter() {
        return STORAGE_INSERTER;
    }
}
