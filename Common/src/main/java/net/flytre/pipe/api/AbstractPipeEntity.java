package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.base.util.Formatter;
import net.flytre.flytre_lib.api.storage.inventory.filter.ResourceFilter;
import net.flytre.pipe.impl.registry.Config;
import net.flytre.pipe.impl.registry.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The way pipes work, is that a sequence of pipes connects source(s) to destination(s). Sources are indicated by the presence of a servo on that end of the pipe
 * which indicates that resources should be pulled from that location and not deposited there. After analyzing the network of connected pipes from that pipe, the filters,
 * and the configuration of the pipe (i.e. round-robin mode, and "wrenched" sides, explained below), the pipe chooses the route and transfers the resource from the source to the right
 * destination. This should be the closest (the least distance travelled) valid destination, if round-robin mode is off, or the next valid destination, if round-robin is on.
 * <p>
 * That all being said, a pipe entity is a block entity that stores data about a pipe
 */
public abstract class AbstractPipeEntity<C, F extends ResourceFilter<? super C>> extends BlockEntity {

    /**
     * Stores the current pipe network, recalculated upon any block changes.
     * From this where resources can go is calculated
     */
    private transient final Map<BlockPos, PipeNetworkRoutes<C>> pipeNetwork;
    /**
     * Stores a map of each side of the pipe (indicated by direction) and whether that side is "wrenched"
     * Sides that are "wrenches" artificially block incoming connections, so that pipes can be placed
     * side by side without connecting
     */
    public Map<Direction, Boolean> wrenched;
    /**
     * The network stores the locations of all pipes in the network; The same set is shared across all pipes in the network, so it must
     * not be altered inappropriately.
     */
    private transient NetworkInformation<C> networkInformation = new NetworkInformation<>(getResourceHandler());
    /**
     * Stores how long ago the cache was cleared to prevent clearing it multiple times
     * in one tick
     */
    private int ticksSinceLastCacheClear = 0;

    /**
     * Represents the resources currently flowing through the pipe.
     * A "TimedPipePath" stores:
     * How long until this resource has passed through this pipe and needs to go to the next one
     * Whether the resource is stuck and is attempted to go to an invalid destination
     * (for example, if a pipe in the route is broken after the route the resource will take is calculated)
     * Data about the resource that's going through the pipe, i.e. what resource it is and what properties it has
     * The queue of pipes the resource must travel through to reach the destination
     * The location of the destination (on the coordinate plane)
     * Client: What side of the pipe the resource is travelling towards, for resource rendering purposes
     * The total distance this resource needs to travel
     */
    private Set<TimedPipePath<C>> resources;

    /**
     * Is the pipe in round-robin mode? (Versus closest valid destination)
     */
    private int roundRobinIndex;


    /**
     * Of all the round-robin destinations, which destination is next?
     */
    private boolean roundRobinMode;

    /**
     * A pipe can only transmit resources so often. This stores the cooldown until it can transmit another resources
     */
    private int cooldown;

    /**
     * Stores data about what resources are allowed through the servos of this pipe
     */
    private F filter;

    /**
     * Stores when data needs to be synced to the client, then syncs it when this is true
     */
    private boolean needsSync;
    /**
     * How fast this pipe should move an item through itself.
     */
    private int ticksThroughPipe = 20;

    /**
     * How often this pipe should pull from a servo
     */
    private int ticksBetweenPull = 20;

    /**
     * Initializes pipe speed variables, like how often it should try to pull.
     */
    private boolean speedVariablesSet = false;

    public AbstractPipeEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        pipeNetwork = new HashMap<>();
        roundRobinIndex = 0;
        roundRobinMode = false;
        resources = new HashSet<>();
        wrenched = Arrays.stream(Direction.values()).collect(Collectors.toMap(Function.identity(), i -> false));
        filter = getResourceHandler().getDefaultFilter();
        needsSync = false;
    }

    /**
     * Identifies all the possible directions a resource could potentially go to from a position. This could be an output
     * destination or another pipe.
     */
    private Collection<ValidDirection<C>> getValidDirections(BlockPos startingPos, World world, PipeNetworkRoutes<C> parent) {
        Set<ValidDirection<C>> result = new HashSet<>();

        if (world == null)
            return result;

        AbstractPipeEntity<?, ?> me = (AbstractPipeEntity<?, ?>) world.getBlockEntity(startingPos);

        if (me == null || !(me.getResourceHandler().getResourceClass() == getResourceHandler().getResourceClass()))
            return result;

        for (Direction direction : Direction.values()) { //For each possible direction

            if (me.getSide(direction) == PipeSide.CONNECTED) { //If this pipe is connected to something
                BlockPos pos = startingPos.offset(direction);
                BlockEntity entity = world.getBlockEntity(pos);

                if (entity != null && isPipeWithSameResource(entity)) {
                    @SuppressWarnings("unchecked") AbstractPipeEntity<C, ?> abstractPipeEntity = (AbstractPipeEntity<C, ?>) entity;

                    if (!(abstractPipeEntity.getResourceHandler().getResourceClass() == getResourceHandler().getResourceClass()))
                        continue;

                    PipeSide state = world.getBlockState(startingPos.offset(direction)).get(AbstractPipeBlock.getProperty(direction.getOpposite()));

                    boolean isFiltered = abstractPipeEntity instanceof FilteredPipe;

                    if (state == PipeSide.CONNECTED && !isFiltered)
                        result.add(new ValidDirection<>(direction, false, null, null));
                    else if (state == PipeSide.CONNECTED)
                        result.add(new ValidDirection<>(direction, false, () -> abstractPipeEntity.new FilteredResourceFilter(), parent::isRoundRobin));
                    else if (state == PipeSide.SERVO && !isFiltered)
                        result.add(new ValidDirection<>(direction, false, () -> abstractPipeEntity.filter, abstractPipeEntity::isRoundRobinMode));
                    else if (state == PipeSide.SERVO)
                        result.add(new ValidDirection<>(direction, false, () -> abstractPipeEntity.new FilteredResourceFilter(), abstractPipeEntity::isRoundRobinMode));


                } else if (getPipeLogic().getStorgeFinder().hasStorage(world, pos, direction.getOpposite())) {
                    result.add(new ValidDirection<>(direction, true, null, null));
                }
            }
        }

        return result;
    }

    /**
     * @param resource       the resource to check for
     * @param destinationPos the position of the destination
     * @param direction      the direction to insert from
     * @return The amount of the resource that can be inserted into the destination
     */


    private long getInsertionAmount(C resource, BlockPos destinationPos, Direction direction, boolean isStuck) {
        List<NetworkInformation<C>> networks = new ArrayList<>();
        for (Direction potential : Direction.values()) {
            assert world != null;
            BlockPos offset = destinationPos.offset(potential);
            BlockEntity other = world.getBlockEntity(offset);
            if (isPipeWithSameResource(other)) {
                assert other != null;
                //noinspection unchecked
                NetworkInformation<C> network = ((AbstractPipeEntity<C, ?>) other).networkInformation;
                if (!networks.contains(network))
                    networks.add(network);
            }
        }
        Set<TimedPipePath<C>> paths = networks.stream().map(i -> i.getTrackedPaths(resource)).flatMap(Collection::stream).collect(Collectors.toSet());

        //basically, if multiple resources are stuck they will block EACH OTHER from finding a valid container; using isStuck to manually override that behavior prevents this.
        long flowCount = isStuck ? 0 :
                (paths.stream()
                        .filter(k -> k.getDestination() == destinationPos)
                        .map(r -> getResourceHandler().getQuantity(r.getResource()))
                        .reduce(0L, Long::sum)
                );
        //Insertion count should always be >= 0 AND should not include flow
        return Math.max(0, getPipeLogic().getInsertionChecker().getInsertionAmount(world, resource, destinationPos, direction, isStuck, flowCount) - flowCount);
    }


    public F getFilter() {
        return filter;
    }

    public Set<TimedPipePath<C>> getQueuedResources() {
        return resources;
    }

    /**
     * @return the list of routes the designated resource could take.
     * <p>
     * Internally, it uses a cache based approach although this is subject to change.
     */

    private List<PipePath<C>> findDestinations(C resource, BlockPos start, boolean stuck) {

        if (!pipeNetwork.containsKey(start))
            pipeNetwork.put(start, getNetwork(start));

        List<PipePath.PotentialQuantified<C>> paths = pipeNetwork.get(start).getQuantifiedPathsFor(resource, (resource2, pos, dir) -> {
            if (world == null)
                return 0L;
            return getInsertionAmount(resource2, pos, dir, stuck);
        });

        record Destination(BlockPos pos, Direction direction) {
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Destination that = (Destination) o;

                if (!pos.equals(that.pos)) return false;
                return direction == that.direction;
            }

            @Override
            public int hashCode() {
                int result = pos.hashCode();
                result = 31 * result + direction.hashCode();
                return result;
            }
        }

        //There could be multiple paths to get to the same destination, so eliminate all duplicates
        Set<Destination> destinations = new HashSet<>();
        List<PipePath<C>> result = new ArrayList<>();

        for (var pq : paths) {
            Destination destination = new Destination(pq.potential().destination(), pq.potential().direction());
            if (!destinations.contains(destination) && pq.amount() > 0) {
                destinations.add(destination);
                result.add(PipePath.get(pq, resource));
            }
        }

        return result;
    }

    /**
     * This method is used for resource destination extraction. It searches all connected destination that
     * are marked for extraction (via servo), and attempts to find a resource it can extract and transfer to destination
     * storage(s)
     */
    private void addToQueue() {
        for (Direction direction : Direction.values()) {
            if (hasServo(direction) && cooldown <= 0) {
                assert world != null;
                if (getPipeLogic().getStorageExtractor().extract(world, pos, direction, filter, (stack) -> extractGreedy(pos, direction, stack), getAmountToExtract()))
                    break;
            }
        }
    }

    /**
     * @return the amount of resource extracted
     */
    private long extractGreedy(BlockPos pipePos, Direction direction, C resource) {
        List<PipePath<C>> results = findDestinations(resource, pipePos.offset(direction), false);
        if (results.size() <= roundRobinIndex) {
            roundRobinIndex = 0;
        }

        PipePath<C> result = roundRobinIndex < results.size() ? results.get(roundRobinIndex++) : null;

        if (result == null)
            return 0;

        TimedPipePath<C> addition = new TimedPipePath<>(result, ticksThroughPipe * 3 / 2);
        resources.add(addition);
        networkInformation.addTrackedPath(addition);

        markDirty();
        if (result.getLength() < Registry.PIPE_CONFIG.getConfig().maxRenderPipeLength)
            needsSync = true;

        return getResourceHandler().getQuantity(result.getResource());
    }


    protected abstract ResourceHandler<C, F> getResourceHandler();

    protected abstract PipeLogic<C> getPipeLogic();

    protected abstract int getBaseTicksBetweenPull();

    protected abstract int getBaseTicksThroughPipe();

    protected abstract long getAmountToExtract();

    /**
     * Gets the PipeSide for the given Direction, aka whether/how the pipe is connected at that side.
     */
    public PipeSide getSide(Direction d) {
        if (world == null)
            return null;

        BlockState state = getCachedState();
        if (!(state.getBlock() instanceof AbstractPipeBlock))
            return null;
        return state.get(AbstractPipeBlock.getProperty(d));
    }

    public boolean hasServo(Direction d) {
        return getSide(d) == PipeSide.SERVO;
    }


    public boolean isRoundRobinMode() {
        return roundRobinMode;
    }

    public void setRoundRobinMode(boolean roundRobinMode) {
        this.roundRobinMode = roundRobinMode;
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
        for (TimedPipePath<C> piped : resources)
            list.add(piped.toTag(new NbtCompound(), false));
        tag.put("queue", list);
        tag.put("filter", getResourceHandler().writeFilterNbt(filter));
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
            this.filter = getResourceHandler().readFilterNbt(tag.getCompound("filter"));
        }

        if (tag.contains("ticksThroughPipe"))
            ticksThroughPipe = tag.getInt("ticksThroughPipe");
        else
            ticksThroughPipe = getBaseTicksThroughPipe();

        if (tag.contains("ticksBetweenPull"))
            ticksBetweenPull = tag.getInt("ticksBetweenPull");
        else
            ticksBetweenPull = getBaseTicksBetweenPull();

        super.readNbt(tag);
    }

    /**
     * Gets a collection of all resources that are travelling through the pipe currently
     */
    public Collection<C> getContainedResources() {
        Set<C> result = new HashSet<>();

        for (TimedPipePath<C> pipeResult : resources) {
            result.add(pipeResult.getResource());
        }

        return result;
    }

    public boolean isPipeWithSameResource(BlockEntity other) {
        if (other == null)
            return false;

        if (!(other instanceof AbstractPipeEntity<?, ?> abstractPipeEntity))
            return false;

        return abstractPipeEntity.getResourceHandler().getResourceClass() == getResourceHandler().getResourceClass();
    }

    /**
     * Looks long but is not scary:
     * <p>
     * Basically, tick down the time remaining of each resource in the pipe
     * If the time is 0, try and transfer it to the next pipe along the sequence
     * If the next pipe / destination doesn't exist, handle that.
     * Else, remove this resource from the pipe and add it to the next pipe
     */
    private void tickQueuedResources() {
        Set<TimedPipePath<C>> toRemove = new HashSet<>(resources.size() / 2 + 1);
        Set<TimedPipePath<C>> removeResourcesOnly = new HashSet<>(resources.size() / 2 + 1);
        Set<TimedPipePath<C>> toAdd = new HashSet<>(resources.size() / 2 + 1);

        for (TimedPipePath<C> timed : resources) {
            timed.decreaseTime();
            if (timed.getTime() <= 0) {
                if (timed.getLength() < Registry.PIPE_CONFIG.getConfig().maxRenderPipeLength)
                    needsSync = true;
                Queue<BlockPos> path = timed.getPath();
                if (this.pos.equals(path.peek()))
                    path.poll(); //remove current block
                if (path.size() > 0) {
                    BlockPos next = path.peek();
                    assert world != null;
                    BlockEntity entity = world.getBlockEntity(next);
                    if (!canTransferToNext(next, entity, timed.getResource())) {
                        reroute(toRemove, toAdd, timed);
                    } else {
                        if (timed.isStuck()) {
                            timed.getPath().addFirst(this.pos);
                            timed.setTime(ticksThroughPipe);
                            timed.setStuck(false);
                        } else {
                            @SuppressWarnings("unchecked") AbstractPipeEntity<C, ?> abstractPipeEntity = (AbstractPipeEntity<C, ?>) entity;
                            assert abstractPipeEntity != null;
                            abstractPipeEntity.addResultToPending(timed);
                            removeResourcesOnly.add(timed);
                            timed.setTime(abstractPipeEntity.ticksThroughPipe);
                        }
                    }
                } else {
                    boolean transferred = getPipeLogic().getStorageInserter().insert(world, timed.getDestination(), timed.getDirection(), timed.getResource());
                    if (!transferred) {
                        reroute(toRemove, toAdd, timed);
                    } else
                        toRemove.add(timed);
                }
            }
        }

        resources.removeAll(toRemove);
        resources.removeAll(removeResourcesOnly);
        networkInformation.removeTrackedPaths(toRemove);
        resources.addAll(toAdd);
        networkInformation.addTrackedPaths(toAdd);
    }

    public boolean canTransferToNext(BlockPos next, BlockEntity entity, C resource) {
        if (!isConnectedToPipe(next, entity))
            return false;
        return !(entity instanceof FilteredPipe pipe) || pipe.canAccept(resource);
    }


    protected boolean isConnectedToPipe(BlockPos next, BlockEntity nextEntity) {

        if (nextEntity == null)
            return false;

        if (!(nextEntity instanceof AbstractPipeEntity<?, ?> abstractPipeEntity))
            return false;

        if (!isPipeWithSameResource(nextEntity))
            return false;

        Direction dir = Direction.fromVector(next.subtract(pos));
        if (dir == null)
            return false;
        return getSide(dir) == PipeSide.CONNECTED && abstractPipeEntity.getSide(dir.getOpposite()) != PipeSide.NONE;
    }

    /**
     * Random helper method to avoid duplicate code. Either marks the current piped resource as stuck, or moves it
     * to the next pipe.
     */
    private void reroute(Set<TimedPipePath<C>> toRemove, Set<TimedPipePath<C>> toAdd, TimedPipePath<C> timed) {
        List<PipePath<C>> results = findDestinations(timed.getResource(), getPos(), false);
        if (results.size() == 0) {
            timed.setTime(20);
            timed.setStuck(true);
        } else {
            TimedPipePath<C> zero = new TimedPipePath<>(results.get(0), ticksThroughPipe, false);
            toAdd.add(zero);
            toRemove.add(timed);
        }
    }

    /**
     * Client should only handle client-side logic, which is just rendering
     * Tick functions are executed every 50 milliseconds
     */
    public void clientTick() {
        for (TimedPipePath<C> timed : resources) {
            timed.decreaseTime();
        }
    }

    public void tick() {

        if (networkInformation.isEmpty()) { //basically, artifically construct the network of this pipe if its empty;
            clearNetworkCache(true);
        }

        if (!speedVariablesSet && world != null) {
            speedVariablesSet = true;
            ticksThroughPipe = getBaseTicksThroughPipe();
            ticksBetweenPull = getBaseTicksBetweenPull();
        }

        if (cooldown > 0)
            cooldown--;

        tickQueuedResources();

        if (cooldown <= 0) {
            addToQueue();
            cooldown = ticksBetweenPull;
        }


        if (needsSync) {
            sync();
            needsSync = false;
        }

        ticksSinceLastCacheClear++;
    }

    /**
     * The reason to use this method instead of adding directly is to decrease network traffic by not sending an update to the client
     * if this item does not need to be rendered.
     */
    private void addResultToPending(TimedPipePath<C> result) {
        this.resources.add(result);
        this.networkInformation.addTrackedPath(result);
        if (result.getLength() < Registry.PIPE_CONFIG.getConfig().maxRenderPipeLength)
            sync();
    }

    @Override
    public @NotNull Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this, (i -> ((AbstractPipeEntity<?, ?>) i).toClientTag()));
    }

    private void readQueueFromTag(NbtCompound tag) {
        NbtList list = tag.getList("queue", 10);
        resources = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            TimedPipePath<C> result = TimedPipePath.fromTag(list.getCompound(i), getResourceHandler());
            networkInformation.addTrackedPath(result);
            resources.add(result);
        }
    }

    public int getTicksBetweenPull() {
        return ticksBetweenPull;
    }

    public int getTicksThroughPipe() {
        return ticksThroughPipe;
    }

    public NbtCompound toClientTag() {
        NbtCompound tag = new NbtCompound();
        Config cfg = Registry.PIPE_CONFIG.getConfig();
        NbtList list = new NbtList();
        for (TimedPipePath<C> piped : resources)
            if (piped.getLength() < cfg.maxRenderPipeLength)
                list.add(piped.toTag(new NbtCompound(), true));

        tag.put("queue", list);
        tag.putInt("ticksThroughPipe", ticksThroughPipe);
        super.writeNbt(tag);
        return tag;
    }

    private void sync() {
        if (!Registry.PIPE_CONFIG.getConfig().renderItems)
            return;
        Objects.requireNonNull(getWorld()).updateListeners(this.getPos(), this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
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

        NetworkInformation<C> information = new NetworkInformation<>(getResourceHandler());
        clearNetworkCacheRipple(information, clearPipeNetwork);
    }

    private void clearNetworkCacheRipple(NetworkInformation<C> information, boolean clearPipeNetwork) {
        information.addPosition(pos);
        information.addTrackedPaths(resources);

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

                BlockEntity entity = world.getBlockEntity(pos);

                if (isPipeWithSameResource(entity)) { //If connected to another pipe
                    @SuppressWarnings("unchecked") AbstractPipeEntity<C, ?> abstractPipeEntity = (AbstractPipeEntity<C, ?>) entity;
                    PipeSide state = world.getBlockState(this.pos.offset(direction)).get(AbstractPipeBlock.getProperty(direction.getOpposite()));
                    if (state == PipeSide.CONNECTED || (state == PipeSide.SERVO && !(side == PipeSide.SERVO))) {
                        assert abstractPipeEntity != null;
                        abstractPipeEntity.clearNetworkCacheRipple(networkInformation, clearPipeNetwork);
                    }
                }
            }
        }
    }

    private PipeNetworkRoutes<C> getNetwork(BlockPos start) {

        Direction animate = null;
        for (Direction dir : Direction.values()) {
            if (getPos().offset(dir).equals(start))
                animate = dir;
        }
        PipeNetworkRoutes<C> result = new PipeNetworkRoutes.RecursiveNode<>(this::getFilter, this::isRoundRobinMode);
        if (world == null)
            return result;

        //The supplier is used so recursive nodes aren't added early and prioritized a bit extra even
        //when a terminal is closer
        record TempPipeData<C>(PipePath.Potential<C> path, Supplier<PipeNetworkRoutes<C>> parent,
                               boolean storage) {
        }

        Deque<TempPipeData<C>> toVisit = new LinkedList<>();
        toVisit.add(new TempPipeData<>(new PipePath.Potential<>(this.getPos(), new LinkedList<>(), getResourceHandler(), Direction.NORTH, animate), () -> result, false));

        while (toVisit.size() > 0) {
            TempPipeData<C> popped = toVisit.pop();
            BlockPos current = popped.path().destination();
            Queue<BlockPos> path = popped.path().path();
            PipeNetworkRoutes<C> parent = popped.parent().get();


            if (parent.isTerminal())
                continue;


            if (!current.equals(start) && popped.storage()) {
                parent.addTerminal(popped.path());
                continue;
            }

            if (isPipeWithSameResource(world.getBlockEntity(current))) {
                Collection<ValidDirection<C>> neighbors = getValidDirections(current, world, parent);
                for (ValidDirection<C> transferResult : neighbors) {
                    Direction direction = transferResult.direction();
                    BlockPos offset = current.offset(direction);
                    if (transferResult.storage() || !path.contains(offset)) {

                        LinkedList<BlockPos> newPath = new LinkedList<>(path);
                        newPath.add(current);

                        if (transferResult.roundRobin() == null || transferResult.filter() == null) {
                            toVisit.add(new TempPipeData<>(new PipePath.Potential<>(offset, newPath, getResourceHandler(), direction.getOpposite(), animate), () -> parent, transferResult.storage()));
                        } else {
                            toVisit.add(new TempPipeData<>(new PipePath.Potential<>(offset, newPath, getResourceHandler(), direction.getOpposite(), animate), () -> parent.addNonTerminal(() -> transferResult.filter().get(), transferResult.roundRobin()), transferResult.storage()));
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * roundRobin is a supplier so changes in the boolean are detected live rather than invalidating the cache
     * same thing with filter inventory to counter chunk unloading and reloading
     */
    private record ValidDirection<C>(Direction direction, boolean storage, Supplier<ResourceFilter<? super C>> filter,
                                     Supplier<Boolean> roundRobin) {
    }

    private class FilteredResourceFilter implements ResourceFilter<C> {

        @Override
        public boolean passFilterTest(C resource) {
            if (!(AbstractPipeEntity.this instanceof FilteredPipe filteredPipe))
                return filter.passFilterTest(resource);
            if (!filteredPipe.canAccept(resource))
                return false;
            return filter.isEmpty() || filter.passFilterTest(resource);
        }

        @Override
        public boolean isEmpty() {
            if (!(AbstractPipeEntity.this instanceof FilteredPipe))
                return filter.isEmpty();
            return false;
        }
    }
}
