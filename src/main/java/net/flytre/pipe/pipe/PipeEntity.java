package net.flytre.pipe.pipe;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.flytre.flytre_lib.common.inventory.FilterInventory;
import net.flytre.flytre_lib.common.util.Formatter;
import net.flytre.flytre_lib.common.util.InventoryUtils;
import net.flytre.pipe.Config;
import net.flytre.pipe.Pipe;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PipeEntity extends BlockEntity implements Tickable, ExtendedScreenHandlerFactory, BlockEntityClientSerializable {

    public HashMap<Direction, Boolean> wrenched;
    private Set<TimedPipeResult> items;
    private int roundRobinIndex;
    private boolean roundRobinMode;
    private int cooldown;
    private FilterInventory filter;
    private boolean needsSync;


    public PipeEntity() {
        super(Pipe.ITEM_PIPE_BLOCK_ENTITY);
        roundRobinIndex = 0;
        roundRobinMode = false;
        items = new HashSet<>();
        wrenched = new HashMap<>();
        for (Direction dir : Direction.values())
            wrenched.put(dir, false);

        filter = FilterInventory.fromTag(new CompoundTag(), 1);
        needsSync = false;
    }

    /*
       Get the transferrable directions from a pipe
       1. If the word is null or the entity at the starting pos is not a pipe return
       2. For each direction
            a. If the pipe is connected to another direction, set that direction as valid and continue. If the pipe on the other
            side is a servo, however, make sure it passes the filter test
            b. If the pipe has a servo on the end continue, can't transfer through a servo
            c. If the adjacent block is an inventory which can recieve an item, add the inventory
       3. Return valid directions
     */
    public static Set<Direction> transferableDirections(BlockPos startingPos, World world, ItemStack stack) {
        Set<Direction> result = new HashSet<>();

        if (world == null)
            return result;

        PipeEntity me = (PipeEntity) world.getBlockEntity(startingPos);

        if (me == null)
            return result;

        for (Direction direction : Direction.values()) {

            if ((me.getSide(direction) == PipeSide.CONNECTED)) {
                BlockPos pos = startingPos.offset(direction);
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof PipeEntity) {
                    PipeSide state = world.getBlockState(startingPos.offset(direction)).get(PipeBlock.getProperty(direction.getOpposite()));
                    boolean bl = false;
                    if (state == PipeSide.CONNECTED)
                        bl = true;
                    if (state == PipeSide.SERVO) {
                        PipeEntity pipeEntity = (PipeEntity) entity;
                        bl = pipeEntity.filter.isEmpty() || pipeEntity.filter.passFilterTest(stack);
                    }
                    if (bl)
                        result.add(direction);

                } else if (entity instanceof Inventory) {
                    Inventory dInv = (Inventory) entity;
                    int[] slots = InventoryUtils.getAvailableSlots(dInv, direction.getOpposite()).toArray();
                    for (int i : slots) {
                        ItemStack currentStack = dInv.getStack(i);
                        if (InventoryUtils.canInsert(dInv, stack, i, direction.getOpposite())) {
                            if (currentStack.isEmpty() || (InventoryUtils.canMergeItems(currentStack, stack) && currentStack.getCount() < currentStack.getMaxCount())) {
                                result.add(direction);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    public FilterInventory getFilter() {
        return filter;
    }

    public Set<TimedPipeResult> getQueuedItems() {
        return items;
    }

    /*
    One is the normal mode, where you return after finding the nearest valid location. When one is false, used for round
    robin mode, it finds all valid locations and returns them in order sorted from nearest to furthest.
    Simple algorithm, performs a BFS search and returns the first valid location it finds to dump the items into
     */
    public ArrayList<PipeResult> findDestinations(ItemStack stack, BlockPos start, boolean one) {

        Direction animate = null;
        for (Direction dir : Direction.values()) {
            if (getPos().offset(dir).equals(start))
                animate = dir;
        }
        ArrayList<PipeResult> result = new ArrayList<>();
        if (world == null)
            return result;

        Deque<PipeResult> to_visit = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        to_visit.add(new PipeResult(this.getPos(), new LinkedList<>(), stack, Direction.NORTH, animate));

        while (to_visit.size() > 0) {
            PipeResult popped = to_visit.pop();
            BlockPos current = popped.getDestination();
            Queue<BlockPos> path = popped.getPath();
            BlockEntity entity = world.getBlockEntity(current);
            if (!current.equals(start) && entity instanceof Inventory) {
                if (!visited.contains(current)) {
                    result.add(popped);
                    if (one)
                        return result;
                }
            }
            if (entity instanceof PipeEntity) {
                Set<Direction> neighbors = PipeEntity.transferableDirections(current, world, stack);
                for (Direction d : neighbors) {
                    if (!visited.contains(current.offset(d))) {
                        LinkedList<BlockPos> newPath = new LinkedList<>(path);
                        newPath.add(current);
                        to_visit.add(new PipeResult(current.offset(d), newPath, stack, d.getOpposite(), animate));
                    }
                }
            }
            visited.add(current);
        }

        return result;
    }

    /*
        Attempts to add an item to the queue to be transferred when the cooldown expires
        1. For each direction
            a. If off cooldown and you have a servo
            b. Get the inventory
            c. If its null or empty, return
            d. For each available slot in the inventory
                e. If it can't extract the stack, or the stack doesn't pass the filter, or is empty continue
                f. If its valid and stuff, perform a normal or round robin search and either add to the queue or pass
     */
    private void addToQueue() {
        for (Direction d : Direction.values()) {
            if (hasServo(d) && cooldown <= 0) {
                Inventory out = InventoryUtils.getInventoryAt(world, this.pos.offset(d));
                Direction opp = d.getOpposite();

                if (out == null || InventoryUtils.isInventoryEmpty(out, opp))
                    continue;

                int[] arr = InventoryUtils.getAvailableSlots(out, opp).toArray();
                for (int i : arr) {

                    ItemStack stack = out.getStack(i);
                    if (!InventoryUtils.canExtract(out, stack, i, opp) || (!filter.isEmpty() && !filter.passFilterTest(stack)))
                        continue;

                    if (stack.isEmpty())
                        continue;
                    ItemStack one = stack.copy();
                    one.setCount(1);

                    PipeResult result;
                    if (isRoundRobinMode()) {
                        ArrayList<PipeResult> results = findDestinations(one, this.pos.offset(d), false);
                        if (results.size() <= roundRobinIndex) {
                            roundRobinIndex = 0;
                        }

                        result = roundRobinIndex < results.size() ? results.get(roundRobinIndex++) : null;
                    } else {
                        ArrayList<PipeResult> results = findDestinations(one, this.pos.offset(d), true);
                        result = results.size() == 0 ? null : results.get(0);
                    }
                    if (result != null) {
                        items.add(new TimedPipeResult(result, 30));
                        stack.decrement(1);
                        cooldown = 10;
                        markDirty();
                        if (result.getLength() < Pipe.PIPE_CONFIG.getConfig().maxRenderPipeLength)
                            needsSync = true;
                        break;
                    }
                }
            }
        }
    }

    public PipeSide getSide(Direction d) {
        if (world == null)
            return null;
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock))
            return null;
        return state.get(PipeBlock.getProperty(d));
    }

    public boolean hasServo(Direction d) {
        return getSide(d) == PipeSide.SERVO;
    }


    public CompoundTag toTag(CompoundTag tag) {
        tag.putInt("wrenched", Formatter.hashToInt(wrenched));
        tag.putInt("rri", this.roundRobinIndex);
        tag.putBoolean("rrm", this.roundRobinMode);
        tag.putInt("cooldown", this.cooldown);
        ListTag list = new ListTag();
        for (TimedPipeResult piped : items)
            list.add(piped.toTag(new CompoundTag(), false));
        tag.put("queue", list);
        tag.put("filter", filter.toTag());
        return super.toTag(tag);
    }

    public boolean isRoundRobinMode() {
        return roundRobinMode;
    }

    public void setRoundRobinMode(boolean roundRobinMode) {
        this.roundRobinMode = roundRobinMode;
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        this.cooldown = tag.getInt("cooldown");
        this.roundRobinIndex = tag.getInt("rri");
        this.roundRobinMode = tag.getBoolean("rrm");
        this.wrenched = Formatter.intToHash(tag.getInt("wrenched"));
        ListTag list = tag.getList("queue", 10);
        items = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            TimedPipeResult result = TimedPipeResult.fromTag(list.getCompound(i));
            items.add(result);
        }

        CompoundTag filter = tag.getCompound("filter");
        this.filter = FilterInventory.fromTag(filter, 1);
        super.fromTag(state, tag);
    }


    public Set<ItemStack> getQueuedStacks() {
        Set<ItemStack> result = new HashSet<>();

        for (TimedPipeResult pipeResult : items) {
            result.add(pipeResult.getPipeResult().getStack());
        }

        return result;
    }


    public void tickQueuedItems() {
        Set<TimedPipeResult> toRemove = new HashSet<>(items.size() / 2 + 1);
        Set<TimedPipeResult> toAdd = new HashSet<>(items.size() / 2 + 1);
        for (TimedPipeResult timed : items) {
            timed.decreaseTime();
            if (timed.getTime() <= 0) {
                if (timed.getPipeResult().getLength() < Pipe.PIPE_CONFIG.getConfig().maxRenderPipeLength)
                    needsSync = true;
                Queue<BlockPos> path = timed.getPipeResult().getPath();
                if (this.pos.equals(path.peek()))
                    path.poll(); //remove current block
                if (path.size() > 0) {
                    BlockPos next = path.peek();
                    BlockEntity entity = world.getBlockEntity(next);
                    if (!(entity instanceof PipeEntity)) {
                        ArrayList<PipeResult> results = findDestinations(timed.getPipeResult().getStack(), getPos(), true);
                        if (results.size() == 0) {
                            timed.setTime(20);
                            timed.setStuck(true);
                        } else {
                            TimedPipeResult zero = new TimedPipeResult(results.get(0), 20);
                            toAdd.add(zero);
                            toRemove.add(timed);
                        }
                    } else {
                        PipeEntity pipeEntity = (PipeEntity) entity;
                        timed.setStuck(false);
                        pipeEntity.addResultToPending(timed);
                        toRemove.add(timed);
                        timed.setTime(20);
                    }
                } else {
                    boolean transferred = transferItem(timed);

                    if (!transferred) {
                        ArrayList<PipeResult> results = findDestinations(timed.getPipeResult().getStack(), getPos(), true);
                        if (results.size() == 0) {
                            timed.setTime(20);
                            timed.setStuck(true);
                        } else {
                            TimedPipeResult zero = new TimedPipeResult(results.get(0), 20);
                            toAdd.add(zero);
                            toRemove.add(timed);
                        }
                    } else
                        toRemove.add(timed);
                }
            }
        }

        items.removeAll(toRemove);
        items.addAll(toAdd);

    }

    @Override
    public void tick() {

        if (world == null)
            return;

        if (world.isClient) {
            for (TimedPipeResult timed : items) {
                timed.decreaseTime();
            }
            return;
        }

        if (cooldown <= 0) {
            addToQueue();
        }

        tickQueuedItems();

        if (needsSync) {
            sync();
            needsSync = false;
        }

        if (cooldown > 0)
            cooldown--;
    }

    private boolean transferItem(TimedPipeResult timedPipeResult) {

        PipeResult processed = timedPipeResult.getPipeResult();
        BlockEntity destination = world.getBlockEntity(processed.getDestination());

        if (!(destination instanceof Inventory)) {
            return false;
        }
        Inventory dInv = (Inventory) destination;

        if (InventoryUtils.isInventoryFull(dInv, processed.getDirection())) {
            return false;
        }

        int[] slots = InventoryUtils.getAvailableSlots(dInv, processed.getDirection()).toArray();
        for (int i : slots) {
            ItemStack currentStack = dInv.getStack(i);
            if (InventoryUtils.canInsert(dInv, processed.getStack(), i, processed.getDirection())) {
                if (currentStack.isEmpty()) {
                    dInv.setStack(i, processed.getStack());
                    dInv.markDirty();
                    return true;
                } else if (InventoryUtils.canMergeItems(currentStack, processed.getStack())) {
                    if (currentStack.getCount() < currentStack.getMaxCount()) {
                        currentStack.increment(1);
                        dInv.markDirty();
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public void addResultToPending(TimedPipeResult result) {
        this.items.add(result);
        if (result.getPipeResult().getLength() < Pipe.PIPE_CONFIG.getConfig().maxRenderPipeLength)
            sync();
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity serverPlayerEntity, PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(pos);
        packetByteBuf.writeInt(this.filter.getFilterType());
        packetByteBuf.writeBoolean(this.filter.isMatchMod());
        packetByteBuf.writeBoolean(this.filter.isMatchNbt());
        packetByteBuf.writeBoolean(this.isRoundRobinMode());
    }

    @Override
    public Text getDisplayName() {
        return new TranslatableText("block.pipe.item_pipe");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new PipeHandler(syncId, inv, this);
    }

    @Override
    public void fromClientTag(CompoundTag tag) {
        ListTag list = tag.getList("queue", 10);
        items = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            TimedPipeResult result = TimedPipeResult.fromTag(list.getCompound(i));
            items.add(result);
        }
        super.fromTag(Pipe.ITEM_PIPE.getDefaultState(), tag);
    }

    @Override
    public CompoundTag toClientTag(CompoundTag tag) {

        Config cfg = Pipe.PIPE_CONFIG.getConfig();
        ListTag list = new ListTag();
        for (TimedPipeResult piped : items)
            if (piped.getPipeResult().getLength() < cfg.maxRenderPipeLength)
                list.add(piped.toTag(new CompoundTag(), true));

        tag.put("queue", list);

        return super.toTag(tag);
    }

    @Override
    public void sync() {
        if (!Pipe.PIPE_CONFIG.getConfig().renderItems)
            return;
        BlockEntityClientSerializable.super.sync();
    }
}
