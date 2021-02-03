package net.flytre.pipe.item;

import net.flytre.flytre_lib.client.util.WrenchItem;
import net.flytre.pipe.pipe.PipeBlock;
import net.flytre.pipe.pipe.PipeEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/*
 * Implements WrenchItem to be cross mod compatible when possible
 */
public class PipeWrench extends Item implements WrenchItem {
    public PipeWrench(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient && context.getPlayer() != null && context.getPlayer().isSneaking()) {
            BlockPos pos = context.getBlockPos();
            BlockEntity entity = context.getWorld().getBlockEntity(pos);

            if ((entity instanceof PipeEntity)) {
                ((PipeEntity) entity).setRoundRobinMode(!((PipeEntity) entity).isRoundRobinMode());
            }

        }
        return ActionResult.SUCCESS;
    }


    private void inventoryTickPipeNoShift(BlockHitResult hitResult, Block block, PlayerEntity player, BlockState state, BlockEntity blockEntity) {
        boolean wrenched = false;
        if (block instanceof PipeBlock && blockEntity instanceof PipeEntity)
            wrenched = ((PipeEntity) blockEntity).wrenched.get(hitResult.getSide());

        player.sendMessage(new TranslatableText("item.pipe.wrench.1").append(" (" + hitResult.getSide().name() + "): " + wrenched), true);

    }

    private void inventoryTickPipeShift(World world, BlockHitResult hitResult, Block block, PlayerEntity player, BlockState state) {
        if (block instanceof PipeBlock) {
            BlockEntity entity = world.getBlockEntity(hitResult.getBlockPos());
            if (entity instanceof PipeEntity) {
                boolean isRoundRobin = ((PipeEntity) entity).isRoundRobinMode();
                player.sendMessage(new TranslatableText("item.pipe.wrench.2").append(": " + isRoundRobin), true);
            }
        }
    }


    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!selected || !(entity instanceof PlayerEntity) || world.isClient)
            return;
        PlayerEntity player = (PlayerEntity) entity;
        BlockHitResult hitResult = Item.raycast(world, player, RaycastContext.FluidHandling.NONE);

        if (hitResult.getType() == HitResult.Type.MISS)
            return;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        Block block = state.getBlock();

        if (block instanceof PipeBlock)
            if (player.isSneaking())
                inventoryTickPipeShift(world, hitResult, block, player, state);
            else
                inventoryTickPipeNoShift(hitResult, block, player, state, blockEntity);
    }

}
