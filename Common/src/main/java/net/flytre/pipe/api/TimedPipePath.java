package net.flytre.pipe.api;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedList;

public class TimedPipePath<C> {

    private final PipePath<C> pipePath;
    private int time;
    private boolean stuck;
    private boolean anim;

    public TimedPipePath(PipePath<C> pipePath, int time) {
        this(pipePath, time, false);
    }

    public TimedPipePath(PipePath<C> pipePath, int time, boolean stuck) {
        this.pipePath = pipePath;
        this.time = time;
        this.stuck = stuck;
        this.anim = true;
    }

    public static <C> TimedPipePath<C> fromTag(NbtCompound tag, ResourceHandler<C,?> handler) {
        NbtCompound pipeTag = tag.getCompound("result");
        PipePath<C> result = PipePath.fromTag(pipeTag,handler);
        int time = tag.getInt("time");
        boolean stuck = tag.getBoolean("stuck");
        return new TimedPipePath<>(result, time, stuck);
    }

    public boolean isStuck() {
        return stuck;
    }

    public void setStuck(boolean stuck) {
        this.stuck = stuck;
    }

    public LinkedList<BlockPos> getPath() {
        return pipePath.getPath();
    }

    public BlockPos getDestination() {
        return pipePath.getDestination();
    }

    public C getResource() {
        return pipePath.getResource();
    }

    public Direction getDirection() {
        return pipePath.getDirection();
    }

    public int getLength() {
        return pipePath.getLength();
    }

    public Direction getAnim() {
        return anim ? pipePath.getAnim() : null;
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
            anim = false;
    }

    public NbtCompound toTag(NbtCompound tag, boolean client) {
        NbtCompound pipeTag = new NbtCompound();
        tag.put("result", pipePath.toTag(pipeTag, client));
        tag.putInt("time", time);
        tag.putBoolean("stuck", stuck);
        return tag;
    }

}
