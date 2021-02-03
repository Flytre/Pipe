package net.flytre.pipe.pipe;

import net.flytre.flytre_lib.common.util.Formatter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedList;

public class PipeResult {
    private final LinkedList<BlockPos> path;
    private final BlockPos destination;
    private final ItemStack stack;
    private final Direction direction;

    public PipeResult(BlockPos destination, LinkedList<BlockPos> path, ItemStack stack, Direction direction) {
        this.path = path;
        this.destination = destination;
        this.stack = stack.copy();
        this.direction = direction;
    }

    public static PipeResult fromTag(CompoundTag tag) {
        BlockPos end = Formatter.arrToPos(tag.getIntArray("end"));

        LinkedList<BlockPos> path = new LinkedList<>();
        ListTag list = tag.getList("path", 11);
        for (int i = 0; i < list.size(); i++)
            path.add(Formatter.arrToPos(list.getIntArray(i)));

        CompoundTag stack = tag.getCompound("stack");
        ItemStack stack2 = ItemStack.fromTag(stack);
        Direction d = Direction.byId(tag.getInt("dir"));
        return new PipeResult(end, path, stack2, d);
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

    public CompoundTag toTag(CompoundTag tag, boolean client) {
        tag.put("end", Formatter.posToTag(destination));
        ListTag list = new ListTag();
        if (!client) {
            for (BlockPos pathPos : path)
                list.add(Formatter.posToTag(pathPos));
        } else {
            for (int i = 0; i < Math.min(path.size(), 2); i++) {
                list.add(Formatter.posToTag(path.get(i)));
            }
        }
        tag.put("path", list);

        CompoundTag stack = new CompoundTag();
        this.stack.toTag(stack);
        tag.put("stack", stack);
        tag.putInt("dir", direction.getId());
        return tag;
    }

    public ItemStack getStack() {
        return stack;
    }


    @Override
    public String toString() {
        return "PipeResult{" +
                "path=" + path +
                ", destination=" + destination +
                ", stack=" + stack +
                ", direction=" + direction +
                '}';
    }
}
