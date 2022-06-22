package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.function.Predicate;

/**
 * Tells pipes how to extract from storages.
 * Returns false if there is no valid storage or the pipe failed to extract items from it.
 * See the StorageExtactor.VANILLA for an example.
 */

@FunctionalInterface
public interface StorageExtractor<C> {

    /**
     * @param filter      The extraction filter of the pipe
     * @param pipeHandler Call this upon extracting a valid item. It will return true if the item was able to be transferred,
     *                    and false otherwise. If it returns true, exit this method with status true.
     * @return true if an item was extracted, false otherwise
     */
    boolean extract(World world, BlockPos pipePosition, Direction direction, ResourceFilter<? super C> filter, Predicate<C> pipeHandler);
}
