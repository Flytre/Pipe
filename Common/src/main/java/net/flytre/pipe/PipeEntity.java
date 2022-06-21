package net.flytre.pipe;

import net.flytre.flytre_lib.api.base.util.Formatter;
import net.flytre.flytre_lib.api.base.util.InventoryUtils;
import net.flytre.flytre_lib.api.storage.inventory.filter.FilterInventory;
import net.flytre.flytre_lib.api.storage.inventory.filter.Filtered;
import net.flytre.flytre_lib.api.storage.inventory.filter.packet.FilterEventHandler;
import net.flytre.flytre_lib.loader.CustomScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Some terms defined:
 * -An ItemStack (item stack) represents some quantity of some item. For example, 64 carrots, 10 apples, or 1 enchanted diamond sword.
 * -A block entity is used to store custom data and logic for blocks with special properties. An example would be a chest, which stores data
 * about what items are inside or a sign, which stores data about the text written on it.
 * -Implementing ExtendedScreenHandlerFactory basically means that pipes have screens
 * -BlockEntityClientSerializable is an interface that allows me to send custom data from the server to the client about this block entity
 * -Filtered is an interface from my Minecraft modding library which indicates that this class contains a filter (which restricts the passage of items by a predicate, essentially)
 * -A BlockState is used to store predefined states for a block. For example, the way stairs are facing, whether a lamp is lit, or what 4 sides of a tree trunk have bark
 * -A BlockPos is basically just a coordinate of 3 integers
 * -Nbt is Minecraft's method of serializing data (to a file), and is used for any data that needs to persist between play sessions
 * <p>
 * <p>
 * The way pipes work, is that a sequence of pipes connects source(s) to destination(s). Sources are indicated by the presence of a servo on that end of the pipe
 * which indicates that items should be pulled from that location and not deposited there. After analyzing the network of connected pipes from that pipe, the filters,
 * and the configuration of the pipe (i.e. round robin mode, and "wrenched" sides, explained below), the pipe chooses the route and transfers the item from the source to the right
 * destination. This should be the closest (least distance travelled) valid destination, if round robin mode is off, or the next valid destination, if round robin is on.
 * <p>
 * <p>
 * That all being said, a pipe entity is a block entity that stores data about a pipe
 */
@SuppressWarnings("DuplicatedCode")
public final class PipeEntity extends BlockEntity implements CustomScreenHandlerFactory, Filtered, FilterEventHandler {

    /**
     * Stores the current pipe network, recalculated upon any block changes.
     * From this where items can go is calculated
     */
    private transient final Map<BlockPos, PipeNetworkRoutes> pipeNetwork;
    /**
     * The network stores the locations of all pipes in the network; The same set is shared across all pipes in the network so it must
     * not be altered inappropriately.
     */
    public transient NetworkInformation networkInformation = new NetworkInformation();
    /**
     * Stores a map of each side of the pipe (indicated by direction) and whether that side is "wrenched"
     * Sides that are "wrenches" artificially block incoming connections, so that pipes can be placed
     * side by side without connecting
     */
    public Map<Direction, Boolean> wrenched;
    /**
     * How long since the cache has been cleared by block update.
     * The point of this variable is to prevent clearing the cache multiple times in the same tick,
     * which is an expensive operation because it propagates through the entire pipe network and has
     * to clear the caches of all pipes after finding them
     */
    private int ticksSinceLastCacheClear = 0;
    /**
     * Represents the items currently flowing through the pipe.
     * A "TimedPipePath" stores:
     * How long until this item has passed through this pipe and needs to go to the next one
     * Whether the item is stuck and is attempted to go to an invalid destination
     * (for example, if a pipe in the route is broken after the route the item will take is calculated)
     * Data about the item that's going through the pipe, i.e. what item it is and what properties it has
     * The queue of pipes the item must travel through to reach the destination
     * The location of the destination (on the coordinate plane)
     * Client: What side of the pipe the item is travelling towards, for item rendering purposes
     * The total distance this item needs to travel
     */
    private Set<TimedPipePath> items;
    /**
     * The following two variables store data about round robin mode:
     * -Is the pipe in round-robin mode? (Versus closest valid destination)
     * -Of all the round-robin destinations, which destination is next?
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
     * How fast this pipe should move items, so its speed.
     */
    private int ticksPerOperation = 20;
    /**
     * Checks if ticksPerOperation is set to match whether this is a fast pipe or normal, and if not
     * takes care of that.
     */
    private boolean speedSet = false;

    /**
     * Something to note is that state-dependent data about a block entity is usually initialized in the readNbt() method, and not in the
     * constructor. Therefore the constructor is almost meaningless except as initializing a default state.
     */
    public PipeEntity(BlockPos pos, BlockState state) {
        super(Registry.ITEM_PIPE_BLOCK_ENTITY.get(), pos, state);
        pipeNetwork = new HashMap<>();
        roundRobinIndex = 0;
        roundRobinMode = false;
        items = new HashSet<>();
        wrenched = Arrays.stream(Direction.values()).collect(Collectors.toMap(Function.identity(), i -> false));
        filter = FilterInventory.readNbt(new NbtCompound(), 1); //Basically, it's asking the filter inventory to read from no nbt, so it creates a default filter inventory.
        needsSync = false;
    }

    /**
     * @param stack       the item stack to check for
     * @param destination the destination inventory
     * @param direction   the direction to insert from
     * @return Checks if item stack X be inserted into inventory Y from Direction D. However, it also takes into consideration all other items flowing into the inventory
     * from the network, and if those being inserted already would make it impossible for item stack X to be inserted, MAY return false.
     */
    public static boolean canInsertFirm(World world, NetworkInformation networkInformation, ItemStack stack, Inventory destination, Direction direction, boolean isStuck) {


        Set<TimedPipePath> paths = networkInformation.getTrackedPaths(stack);


        //basically, if multiple items are stuck they will block EACH OTHER from finding a valid container; using isStuck to manually override that behavior prevents this.
        int flowCt = isStuck ? 0 : ((int) paths.stream().filter(k -> InventoryUtils.getInventoryAt(world, k.getPipePath().getDestination()) == destination).count());


        //if there are no items of that type flowing through the pipe return true
        if (flowCt == 0) {
            return destination != null && InventoryUtils.getAvailableSlots(destination, direction.getOpposite()).anyMatch(i -> {
                ItemStack slotStack = destination.getStack(i);
                if (InventoryUtils.canInsert(destination, stack, i, direction.getOpposite()))
                    return slotStack.isEmpty() || (InventoryUtils.canMergeItems(slotStack, stack) && slotStack.getCount() < slotStack.getMaxCount());
                return false;
            });
        } else {
            //basically, estimate how many items are going into the container and if it's going to be full, don't send the item
            if (destination == null)
                return false;
            int[] slots = InventoryUtils.getAvailableSlots(destination, direction.getOpposite()).toArray();
            ItemStack copy = stack.copy();
            copy.setCount(flowCt + 1);
            for (int slot : slots) {
                ItemStack slotStack = destination.getStack(slot);

                if (slotStack.getCount() >= slotStack.getMaxCount())
                    continue;

                if (InventoryUtils.canInsert(destination, copy, slot, direction.getOpposite())) {
                    if (slotStack.isEmpty()) {
                        if (copy.getCount() < destination.getMaxCountPerStack())
                            return true;
                        else
                            copy.decrement(destination.getMaxCountPerStack());
                    } else if (InventoryUtils.canMergeItems(slotStack, copy)) {
                        int slotMaxCount = slotStack.getMaxCount() == 64 ? Math.max(slotStack.getCount(),destination.getMaxCountPerStack()) : slotStack.getMaxCount();
                        int target = slotMaxCount - slotStack.getCount();

                        if (copy.getCount() <= target)
                            return true;
                        else
                            copy.decrement(target);
                    }
                }
            }
            return false;
        }

    }

    /**
     * Identifies all the possible directions an item could potentially go to from a position. This could be an output
     * inventory or another pipe.
     */
    public static Collection<ValidDirection> getValidDirections(BlockPos startingPos, World world) {
        Set<ValidDirection> result = new HashSet<>();

        if (world == null)
            return result;

        PipeEntity me = (PipeEntity) world.getBlockEntity(startingPos);

        if (me == null)
            return result;

        for (Direction direction : Direction.values()) { //For each possible direction

            if (me.getSide(direction) == PipeSide.CONNECTED) { //If this pipe is connected to something
                BlockPos pos = startingPos.offset(direction);
                BlockEntity entity = world.getBlockEntity(pos);
                Inventory inventory = InventoryUtils.getInventoryAt(world, pos);

                if (entity instanceof PipeEntity pipeEntity) { //If connected to another pipe
                    PipeSide state = world.getBlockState(startingPos.offset(direction)).get(PipeBlock.getProperty(direction.getOpposite()));
                    if (state == PipeSide.CONNECTED)
                        result.add(new ValidDirection(direction, null, null));
                    else if (state == PipeSide.SERVO)
                        result.add(new ValidDirection(direction, () -> pipeEntity.filter, pipeEntity::isRoundRobinMode));

                } else if (inventory != null) { //If connected to an inventory
                    result.add(new ValidDirection(direction, null, null));
                }
            }
        }

        return result;
    }

    @Override
    public FilterInventory getFilter() {
        return filter;
    }

    public Set<TimedPipePath> getQueuedItems() {
        return items;
    }

    /**
     * @return the list of routes the designated item could take.
     * <p>
     * Internally, it uses a cache based approach although this is subject to change.
     */
    public List<PipePath> findDestinations(ItemStack stack, BlockPos start, boolean stuck) {

        if (!pipeNetwork.containsKey(start))
            pipeNetwork.put(start, getNetwork(start));

        List<StackFreePath> paths = pipeNetwork.get(start).getPathsFor(stack, (item, pos, dir) -> {

            if (world == null)
                return false;

            BlockEntity entity = world.getBlockEntity(pos);
            if (!(entity instanceof Inventory inv))
                return false;
            return canInsertFirm(world, networkInformation, item, inv, dir, stuck);
        });
        return paths.stream().map(i -> PipePath.get(i, stack)).toList();
    }

    /**
     * This method is used for item inventory extraction. It searches all connected inventories that
     * are marked for extraction (via servo), and attempts to find an item it can extract and transfer to destination
     * inventory(s)
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

                    List<PipePath> results = findDestinations(one, this.pos.offset(d), false);
                    if (results.size() <= roundRobinIndex) {
                        roundRobinIndex = 0;
                    }

                    PipePath result = roundRobinIndex < results.size() ? results.get(roundRobinIndex++) : null;

                    if (result != null) {
                        {
                            TimedPipePath addition = new TimedPipePath(result, ticksPerOperation * 3 / 2);
                            items.add(addition);
                            networkInformation.addTrackedPath(addition);
                        }
                        stack.decrement(1);
                        out.markDirty();
                        markDirty();
                        if (result.getLength() < Registry.PIPE_CONFIG.getConfig().maxRenderPipeLength)
                            needsSync = true;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Gets the PipeSide for the given Direction, aka whether/how the pipe is connected at that side.
     */
    public PipeSide getSide(Direction d) {
        if (world == null)
            return null;

        BlockState state = getCachedState();
        if (!(state.getBlock() instanceof PipeBlock))
            return null;
        return state.get(PipeBlock.getProperty(d));
    }

    public boolean hasServo(Direction d) {
        return getSide(d) == PipeSide.SERVO;
    }

    /**
     * Serialize data to be saved for when the area the pipe is in is unloaded.
     */
    @Override
    protected void writeNbt(NbtCompound tag) {
        tag.putInt("wrenched", Formatter.mapToInt(wrenched));
        tag.putInt("rri", this.roundRobinIndex);
        tag.putBoolean("rrm", this.roundRobinMode);
        tag.putInt("cooldown", this.cooldown);
        NbtList list = new NbtList();
        for (TimedPipePath piped : items)
            list.add(piped.toTag(new NbtCompound(), false));
        tag.put("queue", list);
        tag.put("filter", filter.writeNbt());
    }

    public boolean isRoundRobinMode() {
        return roundRobinMode;
    }

    public void setRoundRobinMode(boolean roundRobinMode) {
        this.roundRobinMode = roundRobinMode;
    }

    /**
     * Read serialized data to set the state of the pipe when the area its in is loaded
     */
    @Override
    public void readNbt(NbtCompound tag) {
        if (tag.contains("cooldown"))
            this.cooldown = tag.getInt("cooldown");

        if (tag.contains("rri"))
            this.roundRobinIndex = tag.getInt("rri");

        if (tag.contains("rrm"))
            this.roundRobinMode = tag.getBoolean("rrm");

        if (tag.contains("wrenched"))
            this.wrenched = Formatter.intToMap(tag.getInt("wrenched"));
        readQueueFromTag(tag);

        if (tag.contains("filter")) {
            NbtCompound filter = tag.getCompound("filter");
            this.filter = FilterInventory.readNbt(filter, 1);
        }

        if (tag.contains("ticksPerOperation"))
            ticksPerOperation = tag.getInt("ticksPerOperation");

        super.readNbt(tag);
    }

    /**
     * Gets a collection of all item stacks that are travelling through the pipe currently
     */
    public Collection<ItemStack> getQueuedStacks() {
        Set<ItemStack> result = new HashSet<>();

        for (TimedPipePath pipeResult : items) {
            result.add(pipeResult.getPipePath().getStack());
        }

        return result;
    }

    /**
     * Looks long but is not scary:
     * <p>
     * Basically, tick down the time remaining of each item in the pipe
     * If the time is 0, try and transfer it to the next pipe along the sequence
     * If the next pipe / destination doesn't exist, handle that.
     * Else, remove this item from the pipe and add it to the next pipe
     */
    public void tickQueuedItems() {
        Set<TimedPipePath> toRemove = new HashSet<>(items.size() / 2 + 1);
        Set<TimedPipePath> toAdd = new HashSet<>(items.size() / 2 + 1);

        for (TimedPipePath timed : items) {
            timed.decreaseTime();
            if (timed.getTime() <= 0) {
                if (timed.getPipePath().getLength() < Registry.PIPE_CONFIG.getConfig().maxRenderPipeLength)
                    needsSync = true;
                Queue<BlockPos> path = timed.getPipePath().getPath();
                if (this.pos.equals(path.peek()))
                    path.poll(); //remove current block
                if (path.size() > 0) {
                    BlockPos next = path.peek();
                    assert world != null;
                    BlockEntity entity = world.getBlockEntity(next);
                    if (!(entity instanceof PipeEntity pipeEntity) || !(isConnectedToPipe(next, pipeEntity))) {
                        reroute(toRemove, toAdd, timed);
                    } else {
                        timed.setStuck(false);
                        pipeEntity.addResultToPending(timed);
                        toRemove.add(timed);
                        timed.setTime(pipeEntity.ticksPerOperation);
                    }
                } else {
                    boolean transferred = transferItem(timed);

                    if (!transferred) {
                        reroute(toRemove, toAdd, timed);
                    } else
                        toRemove.add(timed);
                }
            }
        }

        items.removeAll(toRemove);
        networkInformation.removeTrackedPaths(toRemove);
        items.addAll(toAdd);
        networkInformation.addTrackedPaths(toAdd);

    }

    public static boolean areConnectedPipes(World world, BlockPos current, BlockPos next) {
        Direction dir = Direction.fromVector(next.subtract(current));
        if(dir == null)
            return false;

        BlockState currentState = world.getBlockState(current);
        BlockState nextState = world.getBlockState(next);

        if(!(currentState.getBlock() instanceof PipeBlock) || !(nextState.getBlock() instanceof PipeBlock))
            return false;

        return currentState.get(PipeBlock.getProperty(dir)) == PipeSide.CONNECTED
        && nextState.get(PipeBlock.getProperty(dir.getOpposite())) != PipeSide.NONE;
    }

    private boolean isConnectedToPipe(BlockPos next, PipeEntity nextEntity) {

        Direction dir = Direction.fromVector(next.subtract(pos));
        if (dir == null)
            return false;
        return getSide(dir) == PipeSide.CONNECTED && nextEntity.getSide(dir.getOpposite()) != PipeSide.NONE;
    }

    /**
     * Random helper method to avoid duplicate code. Either marks the current piped item as stuck, or moves it
     * to the next pipe.
     */
    private void reroute(Set<TimedPipePath> toRemove, Set<TimedPipePath> toAdd, TimedPipePath timed) {
        List<PipePath> results = findDestinations(timed.getPipePath().getStack(), getPos(), false);
        if (results.size() == 0) {
            timed.setTime(20);
            timed.setStuck(true);
        } else {
            TimedPipePath zero = new TimedPipePath(results.get(0), ticksPerOperation, false);
            toAdd.add(zero);
            toRemove.add(timed);
        }
    }

    /**
     * Client should only handle client-side logic, which is just rendering
     * Tick functions are executed every 50 milliseconds
     */
    public void clientTick() {
        for (TimedPipePath timed : items) {
            timed.decreaseTime();
        }
    }

    /**
     * Server side logic.
     * Tick functions are executed every 50 milliseconds, so there are 20 ticks per second
     */
    public void tick() {

        if (networkInformation.isEmpty()) { //basically, artifically construct the network of this pipe if its empty;
            clearNetworkCache(true);
        }

        if (!speedSet && world != null) {
            speedSet = true;
            Block block = getCachedState().getBlock();
            ticksPerOperation = block == Registry.FAST_PIPE.get() ? 8 : (block == Registry.LIGHTNING_PIPE.get() ? 3 : 20);
        }

        if (cooldown > 0)
            cooldown--;

        tickQueuedItems();

        if (cooldown <= 0) {
            addToQueue();
            cooldown = ticksPerOperation / 2;
        }


        if (needsSync) {
            sync();
            needsSync = false;
        }

        ticksSinceLastCacheClear++;
    }

    /**
     * Attempts to transfer an item to the destination from the last pipe.
     * Return false if it fails so executing code can handle it
     */
    private boolean transferItem(TimedPipePath timedPipePath) {

        PipePath processed = timedPipePath.getPipePath();
        assert world != null;
        Inventory inv = InventoryUtils.getInventoryAt(world, processed.getDestination());

        if (inv == null)
            return false;

        int[] slots = InventoryUtils.getAvailableSlots(inv, processed.getDirection()).toArray();
        for (int i : slots) {
            ItemStack currentStack = inv.getStack(i);
            if (InventoryUtils.canInsert(inv, processed.getStack(), i, processed.getDirection())) {
                if (currentStack.isEmpty()) {
                    inv.setStack(i, processed.getStack());
                    inv.markDirty();
                    return true;
                } else if (InventoryUtils.canMergeItems(currentStack, processed.getStack())) {
                    int slotMaxCount = currentStack.getMaxCount() == 64 ? Math.max(currentStack.getCount(),inv.getMaxCountPerStack()) : currentStack.getMaxCount();
                    if (currentStack.getCount() < slotMaxCount) {
                        currentStack.increment(1);
                        inv.markDirty();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The reason to use this method instead of adding directly is to decrease network traffic by not sending an update to the client
     * if this item does not need to be rendered.
     */
    public void addResultToPending(TimedPipePath result) {
        this.items.add(result);
        this.networkInformation.addTrackedPath(result);
        if (result.getPipePath().getLength() < Registry.PIPE_CONFIG.getConfig().maxRenderPipeLength)
            sync();
    }

    /**
     * Send data to the client about what to display on the pipe's screen.
     */
    @Override
    public void sendPacket(PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(pos);
        packetByteBuf.writeInt(this.filter.getFilterType());
        packetByteBuf.writeBoolean(this.filter.isMatchMod());
        packetByteBuf.writeBoolean(this.filter.isMatchNbt());
        packetByteBuf.writeBoolean(this.isRoundRobinMode());
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.pipe.item_pipe");
    }

    /**
     * ScreenHandlers represent server side logic for a screen displayed client sde
     */
    @Override
    public @NotNull ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new PipeHandler(syncId, inv, this);
    }

    @Override
    public @NotNull Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this, (i -> ((PipeEntity) i).toClientTag()));
    }

    private void readQueueFromTag(NbtCompound tag) {
        NbtList list = tag.getList("queue", 10);
        items = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            TimedPipePath result = TimedPipePath.fromTag(list.getCompound(i));
            networkInformation.addTrackedPath(result);
            items.add(result);
        }
    }

    public NbtCompound toClientTag() {
        NbtCompound tag = new NbtCompound();
        Config cfg = Registry.PIPE_CONFIG.getConfig();
        NbtList list = new NbtList();
        for (TimedPipePath piped : items)
            if (piped.getPipePath().getLength() < cfg.maxRenderPipeLength)
                list.add(piped.toTag(new NbtCompound(), true));

        tag.put("queue", list);
        tag.putInt("ticksPerOperation", ticksPerOperation);
        super.writeNbt(tag);
        return tag;
    }

    private void sync() {
        if (!Registry.PIPE_CONFIG.getConfig().renderItems)
            return;
        Objects.requireNonNull(getWorld()).updateListeners(this.getPos(), this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
    }

    public int getTicksPerOperation() {
        return ticksPerOperation;
    }

    /**
     * Clears the caches of all pipes in the network. Used to invalidate stale cache values
     */
    public void clearNetworkCache(boolean clearPipeNetwork) {
        //Basically if you try to clear the cache multiple times in 1 tick, i.e. with a large number of blocks being placed at once,
        //The cache will not have regenerated yet, so it'll still be empty BUT you still recur through the whole network, causing lag.
        //To solve this, the class stores how long since the last cache clear as an integer.
        if (ticksSinceLastCacheClear == 0)
            return;

        NetworkInformation information = new NetworkInformation();
        clearNetworkCacheRipple(information, clearPipeNetwork);
    }

    private void clearNetworkCacheRipple(NetworkInformation information, boolean clearPipeNetwork) {
        information.addPosition(pos);
        information.addTrackedPaths(items);

        //all pipes in a network share the same information
        this.networkInformation = information;
        ticksSinceLastCacheClear = 0;

        if (clearPipeNetwork) {
            pipeNetwork.clear();
        }

        for (Direction direction : Direction.values()) { //For each possible direction

            PipeSide side = getSide(direction);
            if ((side == PipeSide.CONNECTED || side == PipeSide.SERVO)) { //If this pipe is connected to something
                BlockPos pos = this.pos.offset(direction);

                if (networkInformation.getPositions().contains(pos))
                    continue;

                assert world != null;
                if (world.getBlockEntity(pos) instanceof PipeEntity pipeEntity) { //If connected to another pipe
                    PipeSide state = world.getBlockState(this.pos.offset(direction)).get(PipeBlock.getProperty(direction.getOpposite()));
                    if (state == PipeSide.CONNECTED || (state == PipeSide.SERVO && !(side == PipeSide.SERVO))) {
                        pipeEntity.clearNetworkCacheRipple(networkInformation, clearPipeNetwork);
                    }
                }
            }
        }
    }

    /**
     * Event that's automagically called when a filter update packet is received so the cache can be cleared.
     */
    @Override
    public void onPacketReceived() {
        clearNetworkCache(false);
    }

    private PipeNetworkRoutes getNetwork(BlockPos start) {

        Direction animate = null;
        for (Direction dir : Direction.values()) {
            if (getPos().offset(dir).equals(start))
                animate = dir;
        }
        PipeNetworkRoutes result = new PipeNetworkRoutes.RecursiveNode(this::getFilter, this::isRoundRobinMode);
        if (world == null)
            return result;

        //The supplier is used so recursive nodes aren't added early and prioritized a bit extra even
        //when a terminal is closer
        record TempPipeData(StackFreePath path, Supplier<PipeNetworkRoutes> parent) {
        }

        Deque<TempPipeData> toVisit = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<VisitedAngle> inventorySides = new HashSet<>();
        toVisit.add(new TempPipeData(new StackFreePath(this.getPos(), new LinkedList<>(), Direction.NORTH, animate), () -> result));

        while (toVisit.size() > 0) {
            TempPipeData popped = toVisit.pop();
            BlockPos current = popped.path().getDestination();
            Queue<BlockPos> path = popped.path().getPath();
            PipeNetworkRoutes parent = popped.parent.get();

            if (parent.isTerminal())
                continue;

            if (!current.equals(start)) {
                if (InventoryUtils.getInventoryAt(world, current) != null) {
                    VisitedAngle side = new VisitedAngle(popped.path().getDirection(), current);
                    if (!(inventorySides.contains(side))) {
                        parent.addTerminal(popped.path());
                        inventorySides.add(side);
                    }
                }
            }

            if (!parent.isTerminal() && world.getBlockEntity(current) instanceof PipeEntity) {
                Collection<ValidDirection> neighbors = PipeEntity.getValidDirections(current, world);
                for (ValidDirection transferResult : neighbors) {
                    Direction direction = transferResult.direction();
                    if ((!visited.contains(current.offset(direction)) || InventoryUtils.getInventoryAt(world, current.offset(direction)) != null)) {

                        LinkedList<BlockPos> newPath = new LinkedList<>(path);
                        newPath.add(current);

                        if (transferResult.roundRobin() == null || transferResult.filter() == null) {
                            toVisit.add(new TempPipeData(new StackFreePath(current.offset(direction), newPath, direction.getOpposite(), animate), () -> parent));
                        } else {
                            toVisit.add(new TempPipeData(new StackFreePath(current.offset(direction), newPath, direction.getOpposite(), animate), () -> parent.addNonTerminal(transferResult.filter(), transferResult.roundRobin())));
                        }
                    }
                }
            }
            visited.add(current);
        }

        return result;
    }

    /**
     * When doing the BFS search, the pos stores the actual position while the direction stores
     * the direction travelled to get to that position. This is important because you can access
     * inventories from multiple sides, and in round-robin mode it should hit all sides.
     */
    private record VisitedAngle(@Nullable Direction direction, BlockPos pos) {


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            VisitedAngle that = (VisitedAngle) o;

            if (direction != that.direction) return false;
            return Objects.equals(pos, that.pos);
        }

        @Override
        public int hashCode() {
            int result = direction != null ? direction.hashCode() : 0;
            result = 31 * result + (pos != null ? pos.hashCode() : 0);
            return result;
        }
    }

    /**
     * roundRobin is a supplier so changes in the boolean are detected live rather than invalidating the cache
     * same thing with filter inventory to counter chunk unloading and reloading
     */
    private record ValidDirection(Direction direction, Supplier<FilterInventory> filter, Supplier<Boolean> roundRobin) {
    }

}
