package net.flytre.pipe.api;


import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Opposite of the extractor. Used to insert a resource into a destination
 * Return false if it fails so executing code can handle it
 */

@FunctionalInterface
public interface StorageInserter<C> {

    /**
     * Inserts a resource into a storage. This may have a quantity > 1.
     */
    boolean insert(World world, BlockPos pos, Direction direction, C resource);
}
