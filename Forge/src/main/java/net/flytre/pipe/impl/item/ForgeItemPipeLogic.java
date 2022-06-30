package net.flytre.pipe.impl.item;

import net.flytre.flytre_lib.api.storage.connectable.ItemPipeConnectable;
import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.flytre.pipe.api.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.Optional;
import java.util.function.ToLongFunction;

public class ForgeItemPipeLogic implements PipeLogic<ItemStack> {

    public static final ForgeItemPipeLogic INSTANCE = new ForgeItemPipeLogic();
    public static final ClientPathValidityChecker<ItemStack> FORGE_VALIDITY_CHECKER = new ClientPathValidityChecker<>() {
        @Override
        public boolean isValidPath(World world, BlockPos current, BlockPos next) {
            Direction dir = Direction.fromVector(next.subtract(current));
            if (dir == null)
                return false;
            BlockEntity entity = world.getBlockEntity(next);

            if (entity == null)
                return false;

            LazyOptional<IItemHandler> capability = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir);
            return capability.resolve().isPresent();
        }
    };
    private static final PipeConnectable<ItemStack> FORGE_PIPE_CONNECTABLE = (world, pos, direction, block, entity) -> {
        if (block instanceof ItemPipeConnectable)
            return true;

        if (entity == null)
            return false;

        LazyOptional<IItemHandler> capability = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction);
        return capability.resolve().isPresent();
    };

    private static final InsertionChecker<ItemStack> FORGE_INSERTION_CHECKER = new InsertionChecker<>() {
        @Override
        public long getInsertionAmount(World world, ItemStack stack, BlockPos pos, Direction direction, boolean isStuck, long flowAmount) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity == null)
                return 0;
            Optional<IItemHandler> capability = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction).resolve();
            if (capability.isEmpty())
                return 0;
            IItemHandler handler = capability.get();

            long maxAmount = flowAmount + stack.getCount();

            ItemStack copy = stack.copy();
            copy.setCount((int) flowAmount + stack.getCount());

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.isItemValid(slot, copy))
                    continue;
                copy = handler.insertItem(slot, copy, true);
                if (copy.isEmpty())
                    return maxAmount;
            }
            return maxAmount - copy.getCount();
        }
    };

    private static final StorageFinder<ItemStack> FORGE_STORAGE_FINDER = (world, pos, dir) -> {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity == null)
            return false;

        LazyOptional<IItemHandler> capability = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir.getOpposite());
        return capability.resolve().isPresent();
    };

    private static final StorageExtractor<ItemStack> FORGE_STORAGE_EXTRACTOR = new StorageExtractor<>() {
        @Override
        public boolean extract(World world, BlockPos pipePosition, Direction direction, ResourceFilter<? super ItemStack> filter, ToLongFunction<ItemStack> pipeHandler, long maxExtractionAmount) {
            BlockEntity entity = world.getBlockEntity(pipePosition.offset(direction));

            if (entity == null)
                return false;

            LazyOptional<IItemHandler> capability = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction);
            Optional<IItemHandler> optional = capability.resolve();
            if (optional.isEmpty())
                return false;
            IItemHandler handler = optional.get();

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.extractItem(slot, (int) maxExtractionAmount, true);
                if (stack.isEmpty())
                    continue;
                ItemStack copy = stack.copy();
                copy.setCount(stack.getCount());
                int extracted = (int) pipeHandler.applyAsLong(copy);
                if (extracted > 0) {
                    handler.extractItem(slot, extracted, false);
                    return true;
                }
            }

            return false;
        }
    };

    private static final StorageInserter<ItemStack> FORGE_STORAGE_INSERTER = new StorageInserter<>() {
        @Override
        public boolean insert(World world, BlockPos pos, Direction direction, ItemStack stack) {
            BlockEntity entity = world.getBlockEntity(pos);

            if (entity == null)
                return false;

            LazyOptional<IItemHandler> capability = entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction);
            Optional<IItemHandler> optional = capability.resolve();
            if (optional.isEmpty())
                return false;
            IItemHandler handler = optional.get();

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.isItemValid(slot, stack))
                    continue;
                stack = handler.insertItem(slot, stack, false);
                if (stack.isEmpty())
                    return true;
            }
            return false;
        }
    };

    private ForgeItemPipeLogic() {

    }

    @Override
    public InsertionChecker<ItemStack> getInsertionChecker() {
        return FORGE_INSERTION_CHECKER;
    }

    @Override
    public PipeConnectable<ItemStack> getPipeConnectable() {
        return FORGE_PIPE_CONNECTABLE;
    }

    @Override
    public StorageExtractor<ItemStack> getStorageExtractor() {
        return FORGE_STORAGE_EXTRACTOR;
    }

    @Override
    public StorageFinder<ItemStack> getStorgeFinder() {
        return FORGE_STORAGE_FINDER;
    }

    @Override
    public StorageInserter<ItemStack> getStorageInserter() {
        return FORGE_STORAGE_INSERTER;
    }
}
