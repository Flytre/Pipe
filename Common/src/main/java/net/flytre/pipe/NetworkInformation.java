package net.flytre.pipe;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class NetworkInformation {

    //memory usage is low for second map because its just storing references
    //trackedPaths map store where all items are going in the network
    private final Map<WrappedItemStack, Set<TimedPipePath>> trackedPaths;
    private final Map<TimedPipePath, WrappedItemStack> inverseTrackedPaths;

    //positions store all nodes in the network
    private final Set<BlockPos> positions;

    public NetworkInformation() {
        positions = new HashSet<>();
        trackedPaths = new HashMap<>();
        inverseTrackedPaths = new HashMap<>();
    }

    public void addPosition(BlockPos position) {
        positions.add(position);
    }


    //Caller may not modify contents. Performance critical so no copy is made
    public Set<BlockPos> getPositions() {
        return positions;
    }

    public void addTrackedPaths(Collection<TimedPipePath> items) {
        for (TimedPipePath path : items) {
            addTrackedPath(path);
        }
    }

    public void addTrackedPath(TimedPipePath path) {
        WrappedItemStack wrappedItemStack = new WrappedItemStack(path.getPipePath().getStack());
        trackedPaths
                .computeIfAbsent(wrappedItemStack, __ -> new HashSet<>())
                .add(path);
        inverseTrackedPaths.put(path, wrappedItemStack);
    }

    public void removeTrackedPath(TimedPipePath path) {
        WrappedItemStack stack = inverseTrackedPaths.get(path);
        inverseTrackedPaths.remove(path);
        if (trackedPaths.containsKey(stack)) {
            trackedPaths.get(stack).remove(path);
            if (trackedPaths.get(stack).isEmpty())
                trackedPaths.remove(stack);
        }
    }

    public void removeTrackedPaths(Collection<TimedPipePath> paths) {
        for(TimedPipePath path : paths)
            removeTrackedPath(path);
    }

    public Set<TimedPipePath> getTrackedPaths(ItemStack stack) {
        return trackedPaths.getOrDefault(new WrappedItemStack(stack), new HashSet<>());
    }

    public boolean isEmpty() {
        return positions.size() == 0;
    }
}
