package net.flytre.pipe.impl.fluid;

import net.flytre.flytre_lib.api.storage.connectable.FluidPipeConnectable;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;
import net.flytre.pipe.api.*;
import net.flytre.pipe.impl.registry.ItemRegistry;
import net.flytre.pipe.impl.registry.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class FluidPipeBlock extends AbstractPipeBlock<FluidStack> implements FluidPipeConnectable {

    private static PipeLogic<FluidStack> FLUID_PIPE_LOGIC;

    public FluidPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ServoItem getServoFor(BlockState state) {
        if (state.getBlock() == Registry.FLUID_PIPE.get())
            return ItemRegistry.SERVO.get();
        return null;
    }

    @Override
    protected ResourceHandler<FluidStack, ?> getResourceHandler() {
        return FluidResourceHandler.INSTANCE;
    }

    @Override
    protected PipeLogic<FluidStack> getPipeLogic() {
        if(FLUID_PIPE_LOGIC == null)
            throw new AssertionError("Fluid pipe logic not set");
        return FLUID_PIPE_LOGIC;
    }

    @Override
    protected void scatterResources(World world, BlockPos pos) {

    }


    @Override
    protected Supplier<BlockEntityType<? extends AbstractPipeEntity<FluidStack, ?>>> getBlockEntityType() {
        return () -> Registry.FLUID_PIPE_BLOCK_ENTITY.get();
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeEntity(pos, state);
    }

    public static void setFluidPipeLogic(PipeLogic<FluidStack> fluidPipeLogic) {
        FLUID_PIPE_LOGIC = fluidPipeLogic;
    }
}
