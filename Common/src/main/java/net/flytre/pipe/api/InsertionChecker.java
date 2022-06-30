package net.flytre.pipe.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Checks the amount of an item that can be inserted into a storage
 */

@FunctionalInterface
public interface InsertionChecker<C> {

    /**
     * @return The amount that can be inserted. This should include both the resource quantity AND the flow amount. The max amount
     * should be equal the quantity of the resource + the flow count, while the min amount should be equal 0
     */
    long getInsertionAmount(World world, C resource, BlockPos pos, Direction direction, boolean isStuck, long flowAmount);
}
