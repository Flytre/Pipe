package net.flytre.pipe.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Checks whether a pipe can insert an item into a storage
 */

@FunctionalInterface
public interface InsertionChecker<C> {

    boolean canInsert(World world, C resource, BlockPos pos, Direction direction, boolean isStuck, int flowCount);
}
