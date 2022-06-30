package net.flytre.pipe.impl.item;

import net.flytre.pipe.api.*;
import net.flytre.pipe.impl.registry.ItemRegistry;
import net.flytre.pipe.impl.registry.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ItemPipeBlock extends AbstractPipeBlock<ItemStack> {

    private static PipeLogic<ItemStack> ITEM_PIPE_LOGIC = VanillaItemPipeLogic.INSTANCE;


    public ItemPipeBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ServoItem getServoFor(BlockState state) {
        if (state.getBlock() == Registry.ITEM_PIPE.get())
            return ItemRegistry.SERVO.get();
        if (state.getBlock() == Registry.FAST_PIPE.get())
            return ItemRegistry.FAST_SERVO.get();
        if (state.getBlock() == Registry.LIGHTNING_PIPE.get())
            return ItemRegistry.LIGHTNING_SERVO.get();
        return null;
    }

    @Override
    protected ResourceHandler<ItemStack, ?> getResourceHandler() {
        return ItemResourceHandler.INSTANCE;
    }

    @Override
    protected PipeLogic<ItemStack> getPipeLogic() {
        return ITEM_PIPE_LOGIC;
    }

    @Override
    protected void scatterResources(World world, BlockPos pos) {
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof ItemPipeEntity itemPipeEntity) {
            for (ItemStack stack : itemPipeEntity.getContainedResources()) {
                ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    @Override
    protected Supplier<BlockEntityType<? extends AbstractPipeEntity<ItemStack, ?>>> getBlockEntityType() {
        return () -> Registry.ITEM_PIPE_BLOCK_ENTITY.get();
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeEntity(pos,state);
    }

    public static void setItemPipeLogic(PipeLogic<ItemStack> itemPipeLogic) {
        ITEM_PIPE_LOGIC = itemPipeLogic;
    }

}
