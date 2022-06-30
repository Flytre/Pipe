package net.flytre.pipe.impl.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.flytre.flytre_lib.api.storage.connectable.FluidPipeConnectable;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;
import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.flytre.pipe.api.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.function.ToLongFunction;


@SuppressWarnings("UnstableApiUsage")
public class FabricFluidPipeLogic implements PipeLogic<FluidStack> {

    public static final FabricFluidPipeLogic INSTANCE = new FabricFluidPipeLogic();

    public static final ClientPathValidityChecker<FluidStack> FABRIC_VALIDITY_CHECKER = (world, current, next) -> {
        Direction dir = Direction.fromVector(next.subtract(current));
        if (dir == null)
            return false;
        return world.getBlockState(next).getBlock() instanceof FluidPipeConnectable || FluidStorage.SIDED.find(world, next, dir) != null;
    };

    private static final PipeConnectable<FluidStack> FABRIC_FLUID_PIPE_CONNECTABLE = (world, pos, direction, block, entity) -> block instanceof FluidPipeConnectable || FluidStorage.SIDED.find(world, pos, direction) != null;


    private static final InsertionChecker<FluidStack> FABRIC_FLUID_INSERTION_CHECKER = new InsertionChecker<FluidStack>() {
        @Override
        public long getInsertionAmount(World world, FluidStack stack, BlockPos pos, Direction direction, boolean isStuck, long flowAmount) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pos, direction);
            if (storage == null || !storage.supportsInsertion())
                return 0;

            try (Transaction transaction = Transaction.openNested(Transaction.getCurrentUnsafe())) {
                long amountInserted = storage.insert(FluidUtils.variantOf(stack), flowAmount + stack.getAmount(), transaction);
                transaction.close();
                return amountInserted;
            }
        }
    };

    private static final StorageExtractor<FluidStack> FABRIC_FLUID_STORAGE_EXTRACTOR = new StorageExtractor<>() {
        @Override
        public boolean extract(World world, BlockPos pipePosition, Direction direction, ResourceFilter<? super FluidStack> filter, ToLongFunction<FluidStack> pipeHandler, long maxExtractionAmount) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pipePosition.offset(direction), direction.getOpposite());
            if (storage == null || !storage.supportsExtraction())
                return false;
            for (StorageView<FluidVariant> view : storage) {
                if (view.isResourceBlank())
                    continue;
                FluidVariant resource = view.getResource();
                if ((!filter.isEmpty() && !filter.passFilterTest(FluidUtils.stackOf(resource))))
                    continue;

                long extracted;
                try (Transaction transaction = Transaction.openOuter()) {
                    long amountExtracted = view.extract(resource, maxExtractionAmount, transaction);

                    if (amountExtracted == 0)
                        continue;

                    extracted = pipeHandler.applyAsLong(FluidUtils.stackOf(resource, amountExtracted));
                }
                if (extracted > 0)
                    try (Transaction transaction = Transaction.openOuter()) {
                        view.extract(resource, extracted, transaction);
                        transaction.commit();
                        return true;
                    }
            }
            return false;
        }
    };

    private static final StorageFinder<FluidStack> FABRIC_FLUID_STORAGE_FINDER = (world, pos, dir) -> {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pos, dir);
        return storage != null && storage.supportsInsertion();
    };

    private static final StorageInserter<FluidStack> FABRIC_FLUID_STORAGE_INSERTER = new StorageInserter<>() {
        @Override
        public boolean insert(World world, BlockPos pos, Direction direction, FluidStack stack) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(world, pos, direction);
            if (storage == null || !storage.supportsInsertion())
                return false;

            try (Transaction transaction = Transaction.openOuter()) {
                long amountInserted = storage.insert(FluidUtils.variantOf(stack), stack.getAmount(), transaction);
                if (amountInserted >= 1) {
                    transaction.commit();
                    stack.decrement(amountInserted);
                    return stack.isEmpty();
                }
            }
            return false;
        }
    };

    private FabricFluidPipeLogic() {

    }

    @Override
    public InsertionChecker<FluidStack> getInsertionChecker() {
        return FABRIC_FLUID_INSERTION_CHECKER;
    }

    @Override
    public PipeConnectable<FluidStack> getPipeConnectable() {
        return FABRIC_FLUID_PIPE_CONNECTABLE;
    }

    @Override
    public StorageExtractor<FluidStack> getStorageExtractor() {
        return FABRIC_FLUID_STORAGE_EXTRACTOR;
    }

    @Override
    public StorageFinder<FluidStack> getStorgeFinder() {
        return FABRIC_FLUID_STORAGE_FINDER;
    }

    @Override
    public StorageInserter<FluidStack> getStorageInserter() {
        return FABRIC_FLUID_STORAGE_INSERTER;
    }
}
