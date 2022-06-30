package net.flytre.pipe.api;

import net.flytre.flytre_lib.api.base.compat.wrench.WrenchItem;
import net.flytre.flytre_lib.loader.CustomScreenHandlerFactory;
import net.flytre.flytre_lib.loader.ScreenLoaderUtils;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public abstract class AbstractPipeBlock<C> extends BlockWithEntity {

    public static final EnumProperty<PipeSide> UP;
    public static final EnumProperty<PipeSide> DOWN;
    public static final EnumProperty<PipeSide> NORTH;
    public static final EnumProperty<PipeSide> SOUTH;
    public static final EnumProperty<PipeSide> EAST;
    public static final EnumProperty<PipeSide> WEST;

    private static final VoxelShape NODE;
    private static final VoxelShape C_UP;
    private static final VoxelShape C_DOWN;
    private static final VoxelShape C_EAST;
    private static final VoxelShape C_WEST;
    private static final VoxelShape C_NORTH;
    private static final VoxelShape C_SOUTH;
    private static final VoxelShape S_UP;
    private static final VoxelShape S_DOWN;
    private static final VoxelShape S_EAST;
    private static final VoxelShape S_WEST;
    private static final VoxelShape S_NORTH;
    private static final VoxelShape S_SOUTH;

    static {
        UP = EnumProperty.of("pipe_up", PipeSide.class);
        DOWN = EnumProperty.of("pipe_down", PipeSide.class);
        NORTH = EnumProperty.of("pipe_north", PipeSide.class);
        SOUTH = EnumProperty.of("pipe_south", PipeSide.class);
        EAST = EnumProperty.of("pipe_east", PipeSide.class);
        WEST = EnumProperty.of("pipe_west", PipeSide.class);

        NODE = Block.createCuboidShape(4.5, 4.5, 4.5, 11.5, 11.5, 11.5);
        C_DOWN = Block.createCuboidShape(4.5, 0, 4.5, 11.5, 5, 11.5);
        C_UP = Block.createCuboidShape(4.5, 11, 4.5, 11.5, 16, 11.5);
        C_EAST = Block.createCuboidShape(11, 4.5, 4.5, 16, 11.5, 11.5);
        C_WEST = Block.createCuboidShape(0, 4.5, 4.5, 5, 11.5, 11.5);
        C_NORTH = Block.createCuboidShape(4.5, 4.5, 0, 11.5, 11.5, 5);
        C_SOUTH = Block.createCuboidShape(4.5, 4.5, 11, 11.5, 11.5, 16);
        S_UP = Block.createCuboidShape(3.5, 14, 3.5, 12.5, 16, 12.5);
        S_DOWN = Block.createCuboidShape(3.5, 0, 3.5, 12.5, 2, 12.5);
        S_EAST = Block.createCuboidShape(14, 3.5, 3.5, 16, 12.5, 12.5);
        S_WEST = Block.createCuboidShape(0, 3.5, 3.5, 2, 12.5, 12.5);
        S_NORTH = Block.createCuboidShape(3.5, 3.5, 0, 12.5, 12.5, 2);
        S_SOUTH = Block.createCuboidShape(3.5, 3.5, 14, 12.5, 12.5, 16);
    }


    public AbstractPipeBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(UP, PipeSide.NONE)
                .with(DOWN, PipeSide.NONE)
                .with(NORTH, PipeSide.NONE)
                .with(SOUTH, PipeSide.NONE)
                .with(EAST, PipeSide.NONE)
                .with(WEST, PipeSide.NONE));
    }

    public static EnumProperty<PipeSide> getProperty(Direction facing) {
        return switch (facing) {
            case UP -> UP;
            case DOWN -> DOWN;
            case EAST -> EAST;
            case WEST -> WEST;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
        };
    }

    public static boolean areConnectedPipes(World world, BlockPos current, BlockPos next) {
        Direction dir = Direction.fromVector(next.subtract(current));
        if (dir == null)
            return false;

        BlockState currentState = world.getBlockState(current);
        BlockState nextState = world.getBlockState(next);

        if (!areSameTypeOfPipe(currentState, nextState))
            return false;

        return currentState.get(getProperty(dir)) == PipeSide.CONNECTED
                && nextState.get(getProperty(dir.getOpposite())) != PipeSide.NONE;
    }

    public static boolean areSameTypeOfPipe(BlockState one, BlockState two) {
        if (!(one.getBlock() instanceof AbstractPipeBlock<?> block1) || !(two.getBlock() instanceof AbstractPipeBlock<?> block2))
            return false;
        return block1.getResourceHandler().getResourceClass() == block2.getResourceHandler().getResourceClass();
    }

    public boolean areSameTypeOfPipe(Block other) {
        if (!(other instanceof AbstractPipeBlock<?> block2))
            return false;
        return this.getResourceHandler().getResourceClass() == ((AbstractPipeBlock<?>) other).getResourceHandler().getResourceClass();
    }


    /**
     * The servo item needed to put a servo on the pipe
     */
    protected abstract ServoItem getServoFor(BlockState state);

    protected abstract ResourceHandler<C, ?> getResourceHandler();

    protected abstract PipeLogic<C> getPipeLogic();

    protected abstract void scatterResources(World world, BlockPos pos);

    protected abstract Supplier<BlockEntityType<? extends AbstractPipeEntity<C, ?>>> getBlockEntityType();

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {


        if (world.isClient)
            return ActionResult.SUCCESS;

        Item item = player.getStackInHand(hand).getItem();

        Item servoItem = getServoFor(state);

        if (!(item == servoItem) && !(item instanceof WrenchItem)) {
            return attemptOpenScreen(world, state, pos, (ServerPlayerEntity) player);
        }
        Direction side = hit.getSide();
        PipeSide current = state.get(getProperty(side));
        //SERVO
        if (item == servoItem)
            useServo(world, state, pos, player, hand, side, current);


        if (item instanceof WrenchItem) {
            useWrench(world, state, pos, player, side, current);
        }


        return super.onUse(state, world, pos, player, hand, hit);
    }

    private void useWrench(World world, BlockState state, BlockPos pos, PlayerEntity player, Direction side, PipeSide current) {

        if (current == PipeSide.SERVO) {
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), getServoFor(state).getDefaultStack());
            world.setBlockState(pos, state.with(getProperty(side), PipeSide.NONE));
            setWrenched(world, pos, side, false);
            return;
        }


        BlockState offsetState = world.getBlockState(pos.offset(side));

        //if this pipe has no connection and the other pipe is wrenched and this one is not, un-wrench the other side
        if (areSameTypeOfPipe(offsetState.getBlock()) && current == PipeSide.NONE && !isWrenched(world, pos, side) && isWrenched(world, pos.offset(side), side.getOpposite())) {
            world.setBlockState(pos.offset(side), offsetState.with(getProperty(side.getOpposite()), PipeSide.CONNECTED));
            world.setBlockState(pos, state.with(getProperty(side), PipeSide.CONNECTED));
            setWrenched(world, pos.offset(side), side.getOpposite(), false);
            return;
        }


        if (!isWrenched(world, pos, side)) {
            setWrenched(world, pos, side, true);
            world.setBlockState(pos, state.with(getProperty(side), PipeSide.NONE));

            //if this pipe is not wrenched and the other pipe does not have a servo
            //then the other pipe should now have no connection if it didn't before
            if (areSameTypeOfPipe(offsetState.getBlock()) && offsetState.get(getProperty(side.getOpposite())) != PipeSide.SERVO) {
                world.setBlockState(pos.offset(side), offsetState.with(getProperty(side.getOpposite()), PipeSide.NONE));
            }
            return;
        }

        //default is to just un-wrench this block and let events handle the connection forming
        world.setBlockState(pos, state.with(getProperty(side), PipeSide.NONE));
        setWrenched(world, pos, side, false);
        Block block = offsetState.getBlock();
        BlockEntity entity = world.getBlockEntity(pos.offset(side));
        if (isConnectable(world, pos.offset(side), side.getOpposite(), block, entity))
            world.setBlockState(pos, state.with(getProperty(side), PipeSide.CONNECTED));


    }

    private void useServo(World world, BlockState state, BlockPos pos, PlayerEntity player, Hand hand, Direction side, PipeSide current) {
        if (current == PipeSide.CONNECTED || current == PipeSide.NONE) {
            BlockState newState = state.with(getProperty(side), PipeSide.SERVO);
            world.setBlockState(pos, newState);
            setWrenched(world, pos, side, false);
            if (!player.isCreative()) {
                player.getStackInHand(hand).decrement(1);
            }
        }
    }

    private ActionResult attemptOpenScreen(World world, BlockState state, BlockPos pos, ServerPlayerEntity player) {
        for (Direction dir : Direction.values()) {
            if (state.get(getProperty(dir)) == PipeSide.SERVO) {
                this.openScreen(world, pos, player);
                return ActionResult.CONSUME;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = NODE;

        Map<EnumProperty<PipeSide>, VoxelShape> sideToConnectorShape = Map.of(
                UP, C_UP,
                DOWN, C_DOWN,
                NORTH, C_NORTH,
                EAST, C_EAST,
                SOUTH, C_SOUTH,
                WEST, C_WEST
        );

        Map<EnumProperty<PipeSide>, VoxelShape> sideToServoShape = Map.of(
                UP, S_UP,
                DOWN, S_DOWN,
                NORTH, S_NORTH,
                EAST, S_EAST,
                SOUTH, S_SOUTH,
                WEST, S_WEST
        );

        for (var entry : sideToConnectorShape.entrySet()) {
            if (state.get(entry.getKey()) != PipeSide.NONE)
                shape = VoxelShapes.combineAndSimplify(shape, entry.getValue(), BooleanBiFunction.OR);

        }

        for (var entry : sideToServoShape.entrySet()) {
            if (state.get(entry.getKey()) == PipeSide.SERVO)
                shape = VoxelShapes.combineAndSimplify(shape, entry.getValue(), BooleanBiFunction.OR);

        }
        return shape;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState newState, WorldAccess world, BlockPos pos, BlockPos posFrom) {

        if (world.isClient())
            return state;

        BlockState neighborState = world.getBlockState(posFrom);
        Block neighbor = neighborState.getBlock();
        if (state.get(getProperty(direction)) == PipeSide.SERVO || isWrenched(world, pos, direction))
            return state;

        if (areSameTypeOfPipe(state, neighborState) && isWrenched(world, posFrom, direction.getOpposite())) {
            return state;
        }

        if (world instanceof World wrld)
            return state.with(getProperty(direction), isConnectable(wrld, posFrom, Direction.fromVector(posFrom.subtract(pos)), neighbor, world.getBlockEntity(posFrom)) ? PipeSide.CONNECTED : PipeSide.NONE);
        return state;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState();
        BlockPos blockPos = ctx.getBlockPos();
        for (Direction direction : Direction.values()) {
            BlockState neighborState = ctx.getWorld().getBlockState(blockPos.offset(direction));
            Block neighbor = neighborState.getBlock();

            if (areSameTypeOfPipe(state, neighborState) && isWrenched(ctx.getWorld(), blockPos.offset(direction), direction.getOpposite()))
                state = state.with(getProperty(direction), PipeSide.NONE);
            else
                state = state.with(getProperty(direction), isConnectable(ctx.getWorld(), blockPos.offset(direction), direction, neighbor, ctx.getWorld().getBlockEntity(blockPos.offset(direction))) ? PipeSide.CONNECTED : PipeSide.NONE);

        }
        return state;
    }

    private boolean isConnectable(World world, BlockPos pos, Direction direction, Block block, BlockEntity entity) {
        return areSameTypeOfPipe(block) || getPipeLogic().getPipeConnectable().isConnectable(world, pos, direction, block, entity);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            for (Direction dir : Direction.values())
                if (state.get(getProperty(dir)) == PipeSide.SERVO)
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), getServoFor(state).getDefaultStack());
            if (!world.isClient) {
                scatterResources(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);

    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
        return false;
    }

    @Override
    public abstract @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state);

    private void openScreen(World world, BlockPos pos, ServerPlayerEntity player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof CustomScreenHandlerFactory) {
            boolean bl = false;
            BlockState state = world.getBlockState(pos);
            for (Direction dir : Direction.values()) {
                if (state.get(getProperty(dir)) == PipeSide.SERVO)
                    bl = true;
            }

            if (bl)
                ScreenLoaderUtils.openScreen(player, (CustomScreenHandlerFactory) blockEntity);
        }


    }

    private boolean isWrenched(WorldAccess world, BlockPos pos, Direction d) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof AbstractPipeEntity<?,?> abstractPipeEntity))
            return false;
        return abstractPipeEntity.wrenched.get(d);
    }

    private void setWrenched(WorldAccess world, BlockPos pos, Direction d, boolean value) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof AbstractPipeEntity<?,?> abstractPipeEntity))
            return;
        ((AbstractPipeEntity<?,?>) blockEntity).wrenched.put(d, value);
    }


    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, getBlockEntityType().get(), (world2, pos, state2, entity) -> {
            if (!world.isClient) entity.tick();
            else entity.clientTick();
        });
    }

    /**
     * What is this for?
     * Basically, if a block adjacent to a pipe gets changed and that's also connectable to the pipe network, the pipe
     * adjacent to the block will tell all pipes in the network to clear their caches and recalculate routes. This is really
     * important to prevent stale, inaccurate cached values.
     *
     * @param state   Current BlockState of Pipe
     * @param world   Current world
     * @param pos     Current BlockPos of Pipe
     * @param block   Previous block before update
     * @param fromPos Position of block that caused the update
     * @param notify  ??? Notify neighbors maybe?
     */
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {

        BlockState changedState = world.getBlockState(fromPos); //Get the new block at the modified position.
        if (changedState.getBlock() instanceof AirBlock || isConnectable(world, fromPos, Direction.fromVector(fromPos.subtract(pos)), changedState.getBlock(), world.getBlockEntity(fromPos))) {
            BlockEntity pipeEntity = world.getBlockEntity(pos);
            if (pipeEntity instanceof AbstractPipeEntity pipe) {
                pipe.clearNetworkCache(true);
            }
        }
        super.neighborUpdate(state, world, pos, block, fromPos, notify);
    }


}
