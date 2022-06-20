package net.flytre.pipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedList;

public class TimedPipePath {
    public static final TimedPipePath DEFAULT;

    static {
        PipePath result = new PipePath(BlockPos.ORIGIN, new LinkedList<>(), ItemStack.EMPTY, Direction.NORTH, Direction.NORTH);
        DEFAULT = new TimedPipePath(result, 9999);
    }

    private final PipePath pipePath;
    private int time;
    private boolean stuck;

    public TimedPipePath(PipePath pipePath, int time) {
        this(pipePath, time, false);
    }

    public TimedPipePath(PipePath pipePath, int time, boolean stuck) {
        this.pipePath = pipePath;
        this.time = time;
        this.stuck = stuck;
    }

    public static TimedPipePath fromTag(NbtCompound tag) {
        NbtCompound pipeTag = tag.getCompound("result");
        PipePath result = PipePath.fromTag(pipeTag);
        int time = tag.getInt("time");
        boolean stuck = tag.getBoolean("stuck");
        return new TimedPipePath(result, time, stuck);
    }

    public boolean isStuck() {
        return stuck;
    }

    public void setStuck(boolean stuck) {
        this.stuck = stuck;
    }

    public PipePath getPipePath() {
        return pipePath;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public void decreaseTime() {
        this.time--;
        if (time <= 0)
            getPipePath().removeAnim();
    }

    public NbtCompound toTag(NbtCompound tag, boolean client) {
        NbtCompound pipeTag = new NbtCompound();
        tag.put("result", pipePath.toTag(pipeTag, client));
        tag.putInt("time", time);
        tag.putBoolean("stuck", stuck);
        return tag;
    }

}
