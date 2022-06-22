package net.flytre.pipe.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Tells a pipe whether a given position has a storage.
 */
@FunctionalInterface
public interface StorageFinder<C> {
    boolean hasStorage(World world, BlockPos pos, Direction dir);
}
