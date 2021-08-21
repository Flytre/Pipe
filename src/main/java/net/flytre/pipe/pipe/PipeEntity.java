package net.flytre.pipe.pipe;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.flytre.flytre_lib.api.base.util.Formatter;
import net.flytre.flytre_lib.api.base.util.InventoryUtils;
import net.flytre.flytre_lib.api.storage.inventory.filter.FilterInventory;
import net.flytre.flytre_lib.api.storage.inventory.filter.Filtered;
import net.flytre.pipe.Config;
import net.flytre.pipe.Pipe;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Some terms defined:
 * -A block entity is used to store custom data and logic for blocks with special properties. An example would be a chest, which stores data
 * about what items are inside or a sign, which stores data about the text written on it.
 * -Implementing ExtendedScreenHandlerFactory basically means that pipes have screens
 * -BlockEntityClientSerializable is an interface that allows me to send custom data from the server to the client about this block entity
 * -Filtered is an interface from my Minecraft modding library which indicates that this class contains a filter (which restricts the passage of items by a predicate, essentially)
 * -A BlockState is used to store predefined states for a block. For example, the way stairs are facing, whether a lamp is lit, or what 4 sides of a tree trunk have bark
 * -A BlockPos is basically just a coordinate of 3 integers
 * -Nbt is Minecraft's method of serializing data, and is used for any data that needs to persist between play sessions
 * <p>
 * <p>
 * The way pipes work, is that a sequence of pipes connects a source(s) to destination(s). Sources are indicated by the presence of a servo on that end of the pipe
 * which indicates that items should be pulled from that location and not deposited there. After analyzing the network of connected pipes from that pipe, the filters,
 * and the configuration of the pipe (i.e. round robin mode, and "wrenched" sides, explained below), the pipe chooses the route and transfers the item from the source to the right
 * destination. This should be the closest (least distance travelled) valid destination, if round robin mode is off, or the next valid destination, if round robin is on.
 * <p>
 * <p>
 * That all being said, a pipe entity is a block entity that stores data about a pipe
 */
public final class PipeEntity extends BlockEntity implements ExtendedScreenHandlerFactory, BlockEntityClientSerializable, Filtered {


    /**
     * Cache is stored as a linked hash map, so unused entries are automatically removed to save memory
     */
    private final transient LinkedHashMap<CacheKey, CacheResult> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<CacheKey, CacheResult> eldest) {

            long time = world == null ? Long.MAX_VALUE : world.getTime();
            long entryTime = eldest.getValue().time;
            return time - entryTime > 300 && size() > 10;
        }
    };

    /**
     * Stores a map of each side of the pipe (indicated by direction) and whether that side is "wrenched"
     * Sides that are "wrenches" artificially block incoming connections, so that pipes can be placed
     * side by side without connecting
     */
    public Map<Direction, Boolean> wrenched;

    /**
     * How long ago the cache was used. If the cache hasn't been used in a long time, which means
     * items stopped flowing through that pipe, just empty the cache to save memory
     */
    private transient long lastCacheTick = 0;

    /**
     * Represents the items currently flowing through the pipe.
     * A "TimedPipeResult" stores:
     * How long until this item has passed through this pipe and needs to go to the next one
     * Whether the item is stuck and is attempted to go to an invalid destination
     * (for example, if a pipe in the route is broken after the route the item will take is calculated)
     * Data about the item that's going through the pipe, i.e. what item it is and what properties it has
     * The queue of pipes the item must travel through to reach the destination
     * The location of the destination (on the coordinate plane)
     * Client: What side of the pipe the item is travelling towards, for item rendering purposes
     * The total distance this item needs to travel
     */
    private Set<TimedPipeResult> items;

    /**
     * The following two variables store data about round robin mode:
     * -Is the pipe in round robin mode? (Versus closest valid destination)
     * -Of all the round robin destinations, which destination is next?
     */
    private int roundRobinIndex;
    private boolean roundRobinMode;

    /**
     * A pipe can only transmit items so often. This stores the cooldown until it can transmit another item
     */
    private int cooldown;

    /**
     * A filter inventory is a utility class in my Minecraft library mod that stores information about a filter -
     * namely, data about what kinds of items are allowed through it and whether any given item would be allowed to
     * pass through
     */
    private FilterInventory filter;

    /**
     * Stores when data needs to be synced to the client, then syncs it when this is true
     */
    private boolean needsSync;


    /**
     * Something to note is that state-dependent data about a block entity is usually initialized in the readNbt() method, and not in the
     * constructor. Therefore the constructor is almost meaningless except as initializing a default state.
     */
    public PipeEntity(BlockPos pos, BlockState state) {
        super(Pipe.ITEM_PIPE_BLOCK_ENTITY, pos, state);
        roundRobinIndex = 0;
        roundRobinMode = false;
        items = new HashSet<>();
        wrenched = Arrays.stream(Direction.values()).collect(Collectors.toMap(Function.identity(), i -> false));
        filter = FilterInventory.readNbt(new NbtCompound(), 1); //Basically, its asking the filter inventory to read from no nbt, so it creates a default filter inventory.
        needsSync = false;
    }

    /**
     * Get the transferable directions from a pipe
     * 1. If the world is null (this should never happen) or the entity at the starting pos is not a pipe return
     * 2. For each direction (indicating the 6 adjacent blocks to the pipe, north, up, east, etc.)
     * a. If the pipe is connected to that direction, set that direction as valid and continue. If the pipe on the other
     * side is a servo, however, make sure it passes the filter test
     * b. If the pipe has a servo on the end continue, can't transfer through a servo
     * c. If the adjacent block is an inventory which can receive an item, add the inventory
     * 3. Return valid directions
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

    /**
     * Duplicates a list of pipe results, as pipe results are mutable.
     */
    private static List<PipeResult> duplicate(List<PipeResult> list) {
        List<PipeResult> result = new ArrayList<>();
        for (PipeResult pipeResult : list)
            result.add(pipeResult.clone());
        return result;
    }

    /**
     * The validate method is used to ensure that a previously calculated cached route is still valid
     * This could return false if the destination block is destroyed, a filter is changed so the item is no longer valid,
     * a pipe along the route is destroyed, etc.
     */
    private boolean validate(ItemStack stack, BlockPos start, PipeResult result) {
        assert world != null;
        LinkedList<BlockPos> path = result.getPath();
        Iterator<BlockPos> iterator = path.iterator();
        BlockEntity startEntity = world.getBlockEntity(start);

        while (iterator.hasNext()) {
            BlockPos next = iterator.next();

            if (next.equals(start))
                continue;

            PipeEntity nextPipe = (PipeEntity) world.getBlockEntity(next);
            Direction direction = Direction.fromVector(next.subtract(start));

            if (!validateHelper(stack, next, startEntity, nextPipe, direction, false))
                return false;

            start = next;
            startEntity = nextPipe;
        }

        BlockPos finalPos = result.getDestination();
        return validateHelper(stack, finalPos, startEntity, world.getBlockEntity(finalPos), Direction.fromVector(finalPos.subtract(start)), true);
    }


    private boolean validateHelper(ItemStack stack, BlockPos next, BlockEntity currentEntity, BlockEntity nextEntity, Direction direction, boolean end) {

        assert world != null;

        PipeEntity currentEntityPipe = null;
        PipeEntity nextEntityPipe = null;

        if (currentEntity instanceof PipeEntity)
            currentEntityPipe = (PipeEntity) currentEntity;

        if (nextEntity instanceof PipeEntity)
            nextEntityPipe = (PipeEntity) nextEntity;

        if (!end && (currentEntityPipe == null || currentEntityPipe.getSide(direction) == PipeSide.CONNECTED)) {
            if (nextEntityPipe != null) {
                PipeSide state = world.getBlockState(next).get(PipeBlock.getProperty(direction.getOpposite()));
                boolean bl = false;
                if (state == PipeSide.CONNECTED)
                    bl = true;
                if (state == PipeSide.SERVO) {
                    bl = nextEntityPipe.filter.isEmpty() || nextEntityPipe.filter.passFilterTest(stack);
                }

                return bl;
            }
        } else if (end && nextEntity instanceof Inventory) {
            Inventory dInv = (Inventory) nextEntity;
            int[] slots = InventoryUtils.getAvailableSlots(dInv, direction.getOpposite()).toArray();
            for (int i : slots) {
                ItemStack currentStack = dInv.getStack(i);
                if (InventoryUtils.canInsert(dInv, stack, i, direction.getOpposite())) {
                    if (currentStack.isEmpty() || (InventoryUtils.canMergeItems(currentStack, stack) && currentStack.getCount() < currentStack.getMaxCount())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public FilterInventory getFilter() {
        return filter;
    }

    public Set<TimedPipeResult> getQueuedItems() {
        return items;
    }

    /**
     * One=true indicates the normal mode, where the method returns after finding the nearest valid location. When one is false, used for round
     * robin mode, it finds all valid locations and returns them in order sorted from nearest to furthest.
     * Performs a BFS search and returns the first valid location it finds to dump the items into
     */
    private List<PipeResult> internalFindDestinations(ItemStack stack, BlockPos start, boolean one) {

        Direction animate = null;
        for (Direction dir : Direction.values()) {
            if (getPos().offset(dir).equals(start))
                animate = dir;
        }
        List<PipeResult> result = new ArrayList<>();
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

    /**
     * Checks the cache, and if nothing is found, calculates a route
     */
    public List<PipeResult> findDestinations(ItemStack stack, BlockPos start, boolean one) {

        assert world != null;
        CacheKey key = new CacheKey(stack, start, one);
        if (cache.containsKey(key)) {
            List<PipeResult> val = cache.get(key).value;
            if (val.stream().allMatch(i -> validate(stack, start, i))) {
                lastCacheTick = world.getTime();
                return val;
            } else {
                cache.remove(key);
            }
        }

        List<PipeResult> toCache = internalFindDestinations(stack, start, one);
        cache.put(key, new CacheResult(world.getTime(), toCache));
        return duplicate(toCache);
    }

    /**
     * Attempts to transfer an item from the inventory(s) the pipe is connected to by adding it to the to-transfer queue
     * 1. For each direction
     * a. If off cooldown and that side has a servo
     * b. Get the connected inventory
     * c. If its null or empty (invalid), return
     * d. For each available slot in the inventory
     * e. If it can't extract the stack, or the stack doesn't pass the filter, or is empty continue
     * f. If its valid and stuff, perform a normal or round robin search and either add to the queue or pass
     */
    private void addToQueue() {
        for (Direction d : Direction.values()) {
            if (hasServo(d) && cooldown <= 0) {
                assert world != null;
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
                        List<PipeResult> results = findDestinations(one, this.pos.offset(d), false);
                        if (results.size() <= roundRobinIndex) {
                            roundRobinIndex = 0;
                        }

                        result = roundRobinIndex < results.size() ? results.get(roundRobinIndex++) : null;
                    } else {
                        List<PipeResult> results = findDestinations(one, this.pos.offset(d), true);
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


    /**
     * Serialize data to be saved
     */
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.putInt("wrenched", Formatter.mapToInt(wrenched));
        tag.putInt("rri", this.roundRobinIndex);
        tag.putBoolean("rrm", this.roundRobinMode);
        tag.putInt("cooldown", this.cooldown);
        NbtList list = new NbtList();
        for (TimedPipeResult piped : items)
            list.add(piped.toTag(new NbtCompound(), false));
        tag.put("queue", list);
        tag.put("filter", filter.writeNbt());
        return super.writeNbt(tag);
    }

    public boolean isRoundRobinMode() {
        return roundRobinMode;
    }

    public void setRoundRobinMode(boolean roundRobinMode) {
        this.roundRobinMode = roundRobinMode;
    }

    /**
     * Read serialized data to set the state
     */
    @Override
    public void readNbt(NbtCompound tag) {
        this.cooldown = tag.getInt("cooldown");
        this.roundRobinIndex = tag.getInt("rri");
        this.roundRobinMode = tag.getBoolean("rrm");
        this.wrenched = Formatter.intToMap(tag.getInt("wrenched"));
        readQueueFromTag(tag);

        NbtCompound filter = tag.getCompound("filter");
        this.filter = FilterInventory.readNbt(filter, 1);
        super.readNbt(tag);
    }


    public Set<ItemStack> getQueuedStacks() {
        Set<ItemStack> result = new HashSet<>();

        for (TimedPipeResult pipeResult : items) {
            result.add(pipeResult.getPipeResult().getStack());
        }

        return result;
    }


    /**
     * Looks long but not really scary:
     * <p>
     * Basically, tick down the time remaining of each item in the pipe
     * If the time is 0, try and transfer it to the next pipe along the sequence
     * If the next pipe / destination doesn't exist, handle that.
     * Else, remove this item from the pipe and add it to the next pipe
     */
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
                    assert world != null;
                    BlockEntity entity = world.getBlockEntity(next);
                    if (!(entity instanceof PipeEntity)) {
                        List<PipeResult> results = findDestinations(timed.getPipeResult().getStack(), getPos(), true);
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
                        List<PipeResult> results = findDestinations(timed.getPipeResult().getStack(), getPos(), true);
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

    /**
     * Client should only handle client-side logic, which is just rendering
     * Tick functions are executed every 50 milliseconds
     */
    public void clientTick() {
        for (TimedPipeResult timed : items) {
            timed.decreaseTime();
        }
    }

    /**
     * Server side logic.
     * Tick functions are executed every 50 milliseconds
     */
    public void tick() {
        if (cooldown <= 0)
            addToQueue();

        tickQueuedItems();

        if (needsSync) {
            sync();
            needsSync = false;
        }

        if (cooldown > 0)
            cooldown--;

        if (lastCacheTick > 600) { //30 secs
            cache.clear();
        }
    }

    /**
     * Attempts to transfer an item to the destination from the last pipe.
     * Return false if it fails so executing code can handle it
     */
    private boolean transferItem(TimedPipeResult timedPipeResult) {

        PipeResult processed = timedPipeResult.getPipeResult();
        assert world != null;
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

    /**
     * Send data to the client about what to display on the pipe's screen.
     */
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

    /**
     * ScreenHandlers represent server side logic for a screen displayed client sde
     */
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new PipeHandler(syncId, inv, this);
    }

    @Override
    public void fromClientTag(NbtCompound tag) {
        readQueueFromTag(tag);
        super.readNbt(tag);
    }

    private void readQueueFromTag(NbtCompound tag) {
        NbtList list = tag.getList("queue", 10);
        items = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            TimedPipeResult result = TimedPipeResult.fromTag(list.getCompound(i));
            items.add(result);
        }
    }

    @Override
    public NbtCompound toClientTag(NbtCompound tag) {

        Config cfg = Pipe.PIPE_CONFIG.getConfig();
        NbtList list = new NbtList();
        for (TimedPipeResult piped : items)
            if (piped.getPipeResult().getLength() < cfg.maxRenderPipeLength)
                list.add(piped.toTag(new NbtCompound(), true));

        tag.put("queue", list);

        return super.writeNbt(tag);
    }

    @Override
    public void sync() {
        if (!Pipe.PIPE_CONFIG.getConfig().renderItems)
            return;
        BlockEntityClientSerializable.super.sync();
    }


    private record CacheKey(@NotNull ItemStack stack, @NotNull BlockPos start, boolean one) {


        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            CacheKey cacheKey = (CacheKey) o;

            if (one != cacheKey.one) return false;
            if (!start.equals(cacheKey.start))
                return false;
            return ItemStack.areEqual(stack, cacheKey.stack);
        }

        @Override
        public int hashCode() {
            int result = Registry.ITEM.getId(stack.getItem()).hashCode(); // O(1)
            result = 31 * result + start.hashCode();
            result = 31 * result + (one ? 1 : 0);
            return result;
        }
    }


    private record CacheResult(long time, List<PipeResult> value) {

    }
}
