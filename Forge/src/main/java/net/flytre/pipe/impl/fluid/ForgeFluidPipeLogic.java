package net.flytre.pipe.impl.fluid;

import net.flytre.flytre_lib.api.storage.connectable.FluidPipeConnectable;
import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.flytre.pipe.api.*;
import net.flytre.pipe.impl.item.ForgeItemPipeLogic;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

public class ForgeFluidPipeLogic implements PipeLogic<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> {

    public static final ForgeFluidPipeLogic INSTANCE = new ForgeFluidPipeLogic();

    public static final ClientPathValidityChecker<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> FORGE_VALIDITY_CHECKER = new ClientPathValidityChecker<>() {
        @Override
        public boolean isValidPath(World world, BlockPos current, BlockPos next) {
            if(world.getBlockState(next).getBlock() instanceof FluidPipeConnectable)
                return true;

            Direction dir = Direction.fromVector(next.subtract(current));
            if (dir == null)
                return false;
            BlockEntity entity = world.getBlockEntity(next);

            if (entity == null)
                return false;

            LazyOptional<IFluidHandler> capability = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir);
            return capability.resolve().isPresent();
        }
    };
    private static final PipeConnectable<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> FORGE_PIPE_CONNECTABLE = (world, pos, direction, block, entity) -> {
        if (block instanceof FluidPipeConnectable)
            return true;

        if (entity == null)
            return false;

        LazyOptional<IFluidHandler> capability = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction);
        return capability.resolve().isPresent();
    };

    private static final InsertionChecker<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> FORGE_INSERTION_CHECKER = new InsertionChecker<>() {
        @Override
        public long getInsertionAmount(World world, net.flytre.flytre_lib.api.storage.fluid.core.FluidStack stack, BlockPos pos, Direction direction, boolean isStuck, long flowAmount) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity == null)
                return 0;
            Optional<IFluidHandler> capability = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction).resolve();
            if (capability.isEmpty())
                return 0;

            IFluidHandler handler = capability.get();
            flowAmount = flowAmount / 81; // to forge units

            FluidStack forgeStack = FluidUtils.fromFlytreStack(stack);


            long maxAmount = flowAmount + forgeStack.getAmount();
            forgeStack.setAmount((int) maxAmount);

            int fillAmount = handler.fill(forgeStack, IFluidHandler.FluidAction.SIMULATE);

            return fillAmount * 81L;
        }
    };

    private static final StorageFinder<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> FORGE_STORAGE_FINDER = (world, pos, dir) -> {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity == null)
            return false;

        LazyOptional<IFluidHandler> capability = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir.getOpposite());
        return capability.resolve().isPresent();
    };

    private static final StorageExtractor<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> FORGE_STORAGE_EXTRACTOR = new StorageExtractor<>() {
        @Override
        public boolean extract(World world, BlockPos pipePosition, Direction direction, ResourceFilter<? super net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> filter, ToLongFunction<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> pipeHandler, long maxExtractionAmount) {
            BlockEntity entity = world.getBlockEntity(pipePosition.offset(direction));

            if (entity == null)
                return false;

            LazyOptional<IFluidHandler> capability = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction);
            Optional<IFluidHandler> optional = capability.resolve();
            if (optional.isEmpty())
                return false;
            IFluidHandler handler = optional.get();

            maxExtractionAmount /= 81; //to forge units

            List<FluidStack> drainStacks = new ArrayList<>();
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack drainStack = handler.getFluidInTank(i).copy();
                if (!drainStack.isEmpty() && filter.isEmpty() || filter.passFilterTest(FluidUtils.fromForgeStack(drainStack)))
                    drainStacks.add(drainStack);
            }

            for (FluidStack drainStack : drainStacks) {
                drainStack.setAmount(Math.min(drainStack.getAmount(), (int) maxExtractionAmount));
                FluidStack drained = handler.drain(drainStack, IFluidHandler.FluidAction.SIMULATE);

                if(drained.getAmount() == 0)
                    continue;
                long extracted = pipeHandler.applyAsLong(FluidUtils.fromForgeStack(drained));

                if(extracted > 0) {
                    handler.drain(drainStack, IFluidHandler.FluidAction.EXECUTE);
                    return true;
                }
            }

            return false;
        }
    };

    private static final StorageInserter<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> FORGE_STORAGE_INSERTER = new StorageInserter<>() {
        @Override
        public boolean insert(World world, BlockPos pos, Direction direction, net.flytre.flytre_lib.api.storage.fluid.core.FluidStack stack) {
            BlockEntity entity = world.getBlockEntity(pos);

            if (entity == null)
                return false;

            LazyOptional<IFluidHandler> capability = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction);
            Optional<IFluidHandler> optional = capability.resolve();
            if (optional.isEmpty())
                return false;
            IFluidHandler handler = optional.get();

            FluidStack forgeStack = FluidUtils.fromFlytreStack(stack);

            int filled = handler.fill(forgeStack, IFluidHandler.FluidAction.EXECUTE);
            stack.decrement(filled * 81L);
            return stack.isEmpty();
        }
    };

    private ForgeFluidPipeLogic() {

    }

    @Override
    public InsertionChecker<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> getInsertionChecker() {
        return FORGE_INSERTION_CHECKER;
    }

    @Override
    public PipeConnectable<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> getPipeConnectable() {
        return FORGE_PIPE_CONNECTABLE;
    }

    @Override
    public StorageExtractor<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> getStorageExtractor() {
        return FORGE_STORAGE_EXTRACTOR;
    }

    @Override
    public StorageFinder<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> getStorgeFinder() {
        return FORGE_STORAGE_FINDER;
    }

    @Override
    public StorageInserter<net.flytre.flytre_lib.api.storage.fluid.core.FluidStack> getStorageInserter() {
        return FORGE_STORAGE_INSERTER;
    }

}
