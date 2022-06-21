package net.flytre.pipe;

import net.flytre.flytre_lib.api.base.compat.wrench.WrenchItem;
import net.flytre.flytre_lib.api.base.compat.wrench.WrenchObservers;
import net.flytre.flytre_lib.loader.LoaderAgnosticRegistry;
import net.flytre.pipe.item.ServoItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Supplier;

public class ItemRegistry {
    public static final Supplier<ServoItem> SERVO = register(() -> new ServoItem(new Item.Settings().group(Registry.TAB)), "servo");
    public static final Supplier<ServoItem> FAST_SERVO = register(() -> new ServoItem(new Item.Settings().group(Registry.TAB)), "fast_servo");
    public static final Supplier<ServoItem> LIGHTNING_SERVO = register(() -> new ServoItem(new Item.Settings().group(Registry.TAB)), "lightning_servo");
    public static final Supplier<Item> WRENCH = register(() -> new WrenchItem(new Item.Settings().group(Registry.TAB).maxCount(1)), "wrench");

    public static void init() {
        WrenchObservers.addUseOnBlockObserver(context -> {
            if (!context.getWorld().isClient && context.getPlayer() != null && context.getPlayer().isSneaking()) {
                BlockPos pos = context.getBlockPos();
                BlockEntity entity = context.getWorld().getBlockEntity(pos);

                if ((entity instanceof PipeEntity)) {
                    ((PipeEntity) entity).setRoundRobinMode(!((PipeEntity) entity).isRoundRobinMode());
                }

            }
        });
        WrenchObservers.addShiftTickObserver((World world, BlockHitResult hitResult, Block block, PlayerEntity player, BlockState state, BlockEntity blockEntity) -> {
            if (block instanceof PipeBlock) {
                BlockEntity entity = world.getBlockEntity(hitResult.getBlockPos());
                if (entity instanceof PipeEntity) {
                    boolean isRoundRobin = ((PipeEntity) entity).isRoundRobinMode();
                    player.sendMessage(new TranslatableText("item.pipe.wrench.2").append(": " + isRoundRobin), true);
                }
            }
        });
        WrenchObservers.addNoShiftTickObserver((World world, BlockHitResult hitResult, Block block, PlayerEntity player, BlockState state, BlockEntity blockEntity) -> {
            boolean wrenched;
            if (block instanceof PipeBlock && blockEntity instanceof PipeEntity) {
                wrenched = ((PipeEntity) blockEntity).wrenched.get(hitResult.getSide());

                player.sendMessage(new TranslatableText("item.pipe.wrench.1").append(" (" + hitResult.getSide().name() + "): " + wrenched), true);
            }
        });
    }

    private static <T extends Item> Supplier<T> register(Supplier<T> item, String id) {
        return LoaderAgnosticRegistry.registerItem(item, Constants.MOD_ID, id);
    }

}
