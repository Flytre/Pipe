package net.flytre.pipe.api;

import net.minecraft.util.math.BlockPos;

import java.util.*;

class NetworkInformation<C> {

    //memory usage is low for second map because its just storing references
    //trackedPaths map store where all items are going in the network
    private final ResourceHandler<C,?> resourceHandler;
    private final Map<WrappedResource<C>, Set<TimedPipePath<C>>> trackedPaths;
    private final Map<TimedPipePath<C>, WrappedResource<C>> inverseTrackedPaths;

    //positions store all nodes in the network
    private final Set<BlockPos> positions;

    public NetworkInformation(ResourceHandler<C,?> resourceHandler) {
        this.resourceHandler = resourceHandler;
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

    public void addTrackedPaths(Collection<TimedPipePath<C>> items) {
        for (TimedPipePath<C> path : items) {
            addTrackedPath(path);
        }
    }

    public void addTrackedPath(TimedPipePath<C> path) {
        WrappedResource<C> wrappedResource = new WrappedResource<>(path.getResource(), resourceHandler);
        trackedPaths
                .computeIfAbsent(wrappedResource, __ -> new HashSet<>())
                .add(path);
        inverseTrackedPaths.put(path, wrappedResource);
    }

    public void removeTrackedPath(TimedPipePath<C> path) {
        WrappedResource<C> wrappedResource = inverseTrackedPaths.get(path);
        inverseTrackedPaths.remove(path);
        if (trackedPaths.containsKey(wrappedResource)) {
            trackedPaths.get(wrappedResource).remove(path);
            if (trackedPaths.get(wrappedResource).isEmpty())
                trackedPaths.remove(wrappedResource);
        }
    }

    public void removeTrackedPaths(Collection<TimedPipePath<C>> paths) {
        for (TimedPipePath<C> path : paths)
            removeTrackedPath(path);
    }

    public Set<TimedPipePath<C>> getTrackedPaths(C resource) {
        return trackedPaths.getOrDefault(new WrappedResource<>(resource, resourceHandler), new HashSet<>());
    }

    public boolean isEmpty() {
        return positions.size() == 0;
    }


    record WrappedResource<C>(C resource, ResourceHandler<C,?> handler) {
        @Override
        public boolean equals(Object wrapped) {
            if (this == wrapped)
                return true;

            if (wrapped == null || getClass() != wrapped.getClass())
                return false;

            return handler().equals(resource(), ((WrappedResource<?>) wrapped).resource);
        }

        @Override
        public int hashCode() {
            return handler.getHashCode(resource);
        }
    }
}
