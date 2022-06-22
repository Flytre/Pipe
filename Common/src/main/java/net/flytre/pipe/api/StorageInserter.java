package net.flytre.pipe.api;


import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Opposite of the extractor. Used to insert an item into a destination
 * Return false if it fails so executing code can handle it
 */

@FunctionalInterface
public interface StorageInserter<C> {

    /**
     * You can set the stack directly to this one.
     * The item stack is always a single item
     */
    boolean insert(World world, BlockPos pos, Direction direction, C resource);
}
