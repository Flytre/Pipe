package net.flytre.pipe.api;


import net.flytre.flytre_lib.api.storage.connectable.ItemPipeConnectable;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Used to figure out what can connect to pipes.
 * If this returns false for a block then the algorithm will not
 * even attempt to transport items to that block.
 */
public interface PipeConnectable<C> {

    boolean isConnectable(World world, BlockPos pos, Direction direction, Block block, BlockEntity entity);
}
