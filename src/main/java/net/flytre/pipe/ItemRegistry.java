package net.flytre.pipe;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.flytre.flytre_lib.common.compat.wrench.WrenchItem;
import net.flytre.pipe.item.ServoItem;
import net.flytre.pipe.pipe.PipeBlock;
import net.flytre.pipe.pipe.PipeEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public class ItemRegistry {
    public static final Item SERVO = new ServoItem(new FabricItemSettings().group(Pipe.TAB));
    public static final Item WRENCH = new WrenchItem(new FabricItemSettings().group(Pipe.TAB).maxCount(1));

    public static void init() {
        WrenchItem.USE_ON_BLOCK_ACTIONS.add(context -> {
            if (!context.getWorld().isClient && context.getPlayer() != null && context.getPlayer().isSneaking()) {
                BlockPos pos = context.getBlockPos();
                BlockEntity entity = context.getWorld().getBlockEntity(pos);

                if ((entity instanceof PipeEntity)) {
                    ((PipeEntity) entity).setRoundRobinMode(!((PipeEntity) entity).isRoundRobinMode());
                }

            }
        });
        WrenchItem.SHIFT_TICK.add((World world, BlockHitResult hitResult, Block block, PlayerEntity player, BlockState state, BlockEntity blockEntity) -> {
            if (block instanceof PipeBlock) {
                BlockEntity entity = world.getBlockEntity(hitResult.getBlockPos());
                if (entity instanceof PipeEntity) {
                    boolean isRoundRobin = ((PipeEntity) entity).isRoundRobinMode();
                    player.sendMessage(new TranslatableText("item.pipe.wrench.2").append(": " + isRoundRobin), true);
                }
            }
        });
        WrenchItem.NO_SHIFT_TICK.add((World world, BlockHitResult hitResult, Block block, PlayerEntity player, BlockState state, BlockEntity blockEntity) -> {
            boolean wrenched = false;
            if (block instanceof PipeBlock && blockEntity instanceof PipeEntity) {
                wrenched = ((PipeEntity) blockEntity).wrenched.get(hitResult.getSide());

                player.sendMessage(new TranslatableText("item.pipe.wrench.1").append(" (" + hitResult.getSide().name() + "): " + wrenched), true);
            }
        });
        Registry.register(Registry.ITEM, new Identifier("pipe", "servo"), SERVO);
        Registry.register(Registry.ITEM, new Identifier("pipe", "wrench"), WRENCH);
    }
}
