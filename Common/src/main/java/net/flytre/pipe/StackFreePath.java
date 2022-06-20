package net.flytre.pipe;

import net.flytre.flytre_lib.api.base.util.Formatter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedList;

public class StackFreePath {
    private final LinkedList<BlockPos> path;
    private final BlockPos destination;
    private final Direction direction;
    private Direction anim;
    private int length;

    public StackFreePath(BlockPos destination, LinkedList<BlockPos> path, Direction direction, Direction anim) {
        this.path = path;
        this.destination = destination;
        this.direction = direction;
        this.length = path.size();
        this.anim = anim;
    }

    public static StackFreePath fromTag(NbtCompound tag) {
        BlockPos end = Formatter.arrToPos(tag.getIntArray("end"));

        LinkedList<BlockPos> path = new LinkedList<>();
        NbtList list = tag.getList("path", 11);
        for (int i = 0; i < list.size(); i++)
            path.add(Formatter.arrToPos(list.getIntArray(i)));

        int length = tag.getInt("length");
        Direction d = Direction.byId(tag.getInt("dir"));
        Direction anim = tag.contains("anim") ? Direction.byId(tag.getInt("anim")) : null;
        StackFreePath result = new StackFreePath(end, path, d, anim);
        result.length = length;
        return result;
    }

    public void removeAnim() {
        anim = null;
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
        tag.putInt("dir", direction.getId());

        if (anim != null)
            tag.putInt("anim", anim.getId());

        if (!client)
            tag.putInt("length", length);

        return tag;
    }

    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return "StackFreePath{" +
                "path=" + path +
                ", destination=" + destination +
                ", direction=" + direction +
                '}';
    }


    public StackFreePath copy() {
        return new StackFreePath(destination, new LinkedList<>(path), direction, anim);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StackFreePath that = (StackFreePath) o;

        if (length != that.length) return false;
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
        result = 31 * result + length;
        return result;
    }
}
