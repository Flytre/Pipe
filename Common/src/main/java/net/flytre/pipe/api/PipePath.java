package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.base.util.Formatter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedList;

class PipePath<C> {
    private final LinkedList<BlockPos> path;
    private final BlockPos destination;
    private final C resource;
    private final ResourceHandler<C,?> resourceHandler;
    private final Direction direction;
    private final Direction anim;
    private final int length;

    private PipePath(BlockPos destination, LinkedList<BlockPos> path, C resource, ResourceHandler<C,?> resourceHandler, Direction direction, Direction anim) {
        this(destination, path, resource, resourceHandler, direction, anim, path.size());
    }

    private PipePath(BlockPos destination, LinkedList<BlockPos> path, C resource, ResourceHandler<C,?> resourceHandler, Direction direction, Direction anim, int length) {
        this.path = path;
        this.destination = destination;
        this.resource = resource;
        this.resourceHandler = resourceHandler;
        this.direction = direction;
        this.length = length;
        this.anim = anim;
    }

    public static <C> PipePath<C> fromTag(NbtCompound tag, ResourceHandler<C,?> handler) {
        BlockPos end = Formatter.arrToPos(tag.getIntArray("end"));

        LinkedList<BlockPos> path = new LinkedList<>();
        NbtList list = tag.getList("path", 11);
        for (int i = 0; i < list.size(); i++)
            path.add(Formatter.arrToPos(list.getIntArray(i)));

        int length = tag.getInt("length");
        C resource = handler.readNbt(tag.getCompound("stack"));
        Direction d = Direction.byId(tag.getInt("dir"));
        Direction anim = tag.contains("anim") ? Direction.byId(tag.getInt("anim")) : null;
        return new PipePath<>(end, path, resource, handler, d, anim, length);
    }

    public static <C> PipePath<C> get(Potential<C> path, C resource) {
        return new PipePath<>(path.destination(), new LinkedList<>(path.path()), resource, path.handler(), path.direction(), path.anim());
    }

    public static <C> PipePath<C> get(PotentialQuantified<C> path, C resource) {
        var handler = path.potential().handler();
        return new PipePath<>(path.potential().destination(), new LinkedList<>(path.potential().path()), handler.copyWithQuantity(resource,path.amount()), handler, path.potential().direction(), path.potential().anim());
    }


    public Direction getAnim() {
        return anim;
    }

    public LinkedList<BlockPos> getPath() {
        return path;
    }

    public BlockPos getDestination() {
        return destination;
    }

    public Direction getDirection() {
        return direction;
    }

    public NbtCompound toTag(NbtCompound tag, boolean client) {
        tag.put("end", Formatter.writePosToNbt(destination));
        NbtList list = new NbtList();
        if (!client) {
            for (BlockPos pathPos : path)
                list.add(Formatter.writePosToNbt(pathPos));
        } else {
            for (int i = 0; i < Math.min(path.size(), 2); i++) {
                list.add(Formatter.writePosToNbt(path.get(i)));
            }
        }
        tag.put("path", list);

        NbtCompound resourceTag = new NbtCompound();
        resourceHandler.writeNbt(resourceTag, this.resource);
        tag.put("stack", resourceTag);

        tag.putInt("dir", direction.getId());

        if (anim != null)
            tag.putInt("anim", anim.getId());

        if (!client)
            tag.putInt("length", length);

        return tag;
    }

    public C getResource() {
        return resource;
    }

    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return "PipePath{" +
                "path=" + path +
                ", destination=" + destination +
                ", resource=" + resource +
                ", direction=" + direction +
                '}';
    }

    record Potential<C>(BlockPos destination,
                            LinkedList<BlockPos> path,
                            ResourceHandler<C,?> handler,
                            Direction direction, Direction anim) {

        @Override
        public String toString() {
            return "Potential{" +
                    "destination=" + destination +
                    ", path=" + path +
                    ", handler=" + handler +
                    ", direction=" + direction +
                    ", anim=" + anim +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Potential<?> that = (Potential<?>) o;

            if (!path.equals(that.path)) return false;
            if (!destination.equals(that.destination)) return false;
            if (direction != that.direction) return false;
            return anim == that.anim;
        }

        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + destination.hashCode();
            result = 31 * result + direction.hashCode();
            result = 31 * result + (anim != null ? anim.hashCode() : 0);
            return result;
        }
    }

    record PotentialQuantified<C>(Potential<C> potential, long amount) {}


}
