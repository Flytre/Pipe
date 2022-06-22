package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.base.util.InventoryUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Returns whether an item has a valid path on the client side when rendering pipes.
 * uses anyMatch()
 */
public interface ClientPathValidityChecker<C> {

    boolean isValidPath(World world, BlockPos currentLocation, BlockPos nextLocation);


    ClientPathValidityChecker<ItemStack> PIPE = AbstractPipeBlock::areConnectedPipes;
    ClientPathValidityChecker<ItemStack> VANILLA = (world, current, next) -> InventoryUtils.getInventoryAt(world, next) != null;

}
