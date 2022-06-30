package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Tells pipes how to extract from storages.
 * Returns false if there is no valid storage or the pipe failed to extract resources from it.
 * See the StorageExtactor.VANILLA for an example.
 */

@FunctionalInterface
public interface StorageExtractor<C> {

    /**
     * @param filter      The extraction filter of the pipe
     * @param pipeHandler Call this upon extracting a valid resource. It will return true if the resource was able to be transferred,
     *                    and false otherwise. If it returns true, exit this method with status true. Note that this resource will be passed around,
     *                    so a smart implementation is to a copy of it and then affect the original if true.
     * @return true if a resource was extracted, false otherwise
     */
    boolean extract(World world, BlockPos pipePosition, Direction direction, ResourceFilter<? super C> filter, ToLongFunction<C> pipeHandler, long maxExtractionAmount);
}
