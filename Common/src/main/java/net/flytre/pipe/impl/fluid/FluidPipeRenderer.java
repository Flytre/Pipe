package net.flytre.pipe.impl.fluid;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.flytre.flytre_lib.api.base.util.RenderUtils;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;
import net.flytre.pipe.api.AbstractPipeEntity;
import net.flytre.pipe.api.ClientPathValidityChecker;
import net.flytre.pipe.api.PipeSide;
import net.flytre.pipe.api.TimedPipePath;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static net.flytre.pipe.api.AbstractPipeBlock.*;

public class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeEntity> {

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
    private static final List<ClientPathValidityChecker<FluidStack>> VALIDITY_CHECKERS = Lists.newArrayList();

    static {
        NODE = createCuboidShape(5.1, 5.1, 5.1, 10.9, 10.9, 10.9);
        C_DOWN = createCuboidShape(5.1, 0, 5.1, 10.9, 5, 10.9);
        C_UP = createCuboidShape(5.1, 11, 5.1, 10.9, 16, 10.9);
        C_EAST = createCuboidShape(11, 5.1, 5.1, 16, 10.9, 10.9);
        C_WEST = createCuboidShape(0, 5.1, 5.1, 5, 10.9, 10.9);
        C_NORTH = createCuboidShape(5.1, 5.1, 0, 10.9, 10.9, 5);
        C_SOUTH = createCuboidShape(5.1, 5.1, 11, 10.9, 10.9, 16);
        S_UP = createCuboidShape(4.1, 13.9, 4.1, 11.9, 15.9, 11.9);
        S_DOWN = createCuboidShape(4.1, 0.1, 4.1, 11.9, 2, 11.9);
        S_EAST = createCuboidShape(13.9, 4.1, 4.1, 15.9, 11.9, 11.9);
        S_WEST = createCuboidShape(0.1, 4.1, 4.1, 2, 11.9, 11.9);
        S_NORTH = createCuboidShape(4.1, 4.1, 0.1, 11.9, 11.9, 2);
        S_SOUTH = createCuboidShape(4.1, 4.1, 13.9, 11.9, 11.9, 15.9);
    }


    public FluidPipeRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    private static void renderLargeSprite(VertexConsumer builder, MatrixStack stack, Sprite sprite, int light, int overlay, float x1, float x2, float y1, float y2, float z1, float z2, int[] color, float spriteSize) {

        RenderSystem.disableCull();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        stack.push();
        stack.translate(x1, y1, z1);
        float xSize = x2 - x1;
        float ySize = y2 - y1;
        float zSize = z2 - z1;

        for (float x = 0, upperX = Math.min(x + spriteSize, xSize); x < xSize; x += spriteSize, upperX = Math.min(x + spriteSize, xSize)) {
            for (float y = 0, upperY = Math.min(y + spriteSize, ySize); y < ySize; y += spriteSize, upperY = Math.min(y + spriteSize, ySize)) {
                for (float z = 0, upperZ = Math.min(z + spriteSize, zSize); z < zSize; z += spriteSize, upperZ = Math.min(z + spriteSize, zSize)) {
                    stack.push();
                    stack.translate(x, y, z);
                    Matrix4f pos = stack.peek().getPositionMatrix();
                    RenderUtils.renderSpriteSide(builder, pos, sprite, Direction.DOWN, light, overlay, 0, upperX, 0, upperY - y, 0, upperZ - z, color);
                    RenderUtils.renderSpriteSide(builder, pos, sprite, Direction.UP, light, overlay, 0, upperX - x, 0, upperY - y, 0, upperZ - z, color);
                    RenderUtils.renderSpriteSide(builder, pos, sprite, Direction.NORTH, light, overlay, 0, upperX - x, 0, upperY - y, 0, upperZ - z, color);
                    RenderUtils.renderSpriteSide(builder, pos, sprite, Direction.SOUTH, light, overlay, 0, upperX - x, 0, upperY - y, 0, upperZ - z, color);
                    RenderUtils.renderSpriteSide(builder, pos, sprite, Direction.WEST, light, overlay, 0, upperX - x, 0, upperY - y, 0, upperZ - z, color);
                    RenderUtils.renderSpriteSide(builder, pos, sprite, Direction.EAST, light, overlay, 0, upperX - x, 0, upperY - y, 0, upperZ - z, color);
                    stack.pop();
                }
            }
        }

        stack.pop();

        RenderSystem.enableCull();
    }

    public static void addValidityChecker(ClientPathValidityChecker<FluidStack> checker) {
        VALIDITY_CHECKERS.add(checker);
    }

    private VoxelShape getOutlineShape(BlockState state) {
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
    public void render(FluidPipeEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        int ticksThroughPipe = entity.getTicksThroughPipe();

        VoxelShape finalShape = VoxelShapes.empty();
        VoxelShape mergeShape = getOutlineShape(entity.getCachedState());


        Collection<FluidStack> collection = entity.getContainedResources();
        FluidStack toRender = FluidStack.EMPTY;
        for (FluidStack next : collection) {
            if (!next.isEmpty()) {
                toRender = next;
                break;
            }
        }


        if (toRender.isEmpty() || entity.getWorld() == null)
            return;

        for (TimedPipePath<FluidStack> timed : entity.getQueuedResources()) {

            if (timed.getTime() < -1)
                continue;

            LinkedList<BlockPos> path = timed.getPath();
            BlockPos current = path.size() > 0 ? path.get(0) : entity.getPos();
            BlockPos next = path.size() <= 1 ? timed.getDestination() : path.get(1);

            Direction dir = Direction.fromVector(next.subtract(current));
            if (dir == null)
                continue;

            BlockEntity nextEntity = entity.getWorld().getBlockEntity(next);

            if (entity.canTransferToNext(next, nextEntity, timed.getResource())) {
                AbstractPipeEntity<?, ?> nextPipe = (AbstractPipeEntity<?, ?>) nextEntity;
                assert nextPipe != null;
                mergeShape = VoxelShapes.combineAndSimplify(mergeShape, getOutlineShape(nextPipe.getCachedState()).offset(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ()), BooleanBiFunction.OR);
            }


            float mult = ticksThroughPipe - timed.getTime() + tickDelta;

            if (entity.getWorld() != null && VALIDITY_CHECKERS.stream().noneMatch(checker -> checker.isValidPath(entity.getWorld(), current, next))) {
                mult = 0;
            }

            float dx = (next.getX() - current.getX()) / (float) ticksThroughPipe * (mult);
            float dy = (next.getY() - current.getY()) / (float) ticksThroughPipe * (mult);
            float dz = (next.getZ() - current.getZ()) / (float) ticksThroughPipe * (mult);

            if (timed.getAnim() != null) {
                if (mult < 0f) {
                    BlockPos pos = current.offset(timed.getAnim());
                    dx = (pos.getX() - current.getX()) / (float) ticksThroughPipe * (-mult);
                    dy = (pos.getY() - current.getY()) / (float) ticksThroughPipe * (-mult);
                    dz = (pos.getZ() - current.getZ()) / (float) ticksThroughPipe * (-mult);
                }
            }

            if (timed.isStuck()) {
                dx = dy = dz = 0;
            }


            float sz = 2.7f; //"radius" of the cube of this fluid
            //offset to center in block
            VoxelShape add = createCuboidShape((dx * 16) - sz, (dy * 16) - sz, (dz * 16) - sz, (dx * 16) + sz, (dy * 16) + sz, (dz * 16) + sz).offset(0.5, 0.5, 0.5);
            finalShape = VoxelShapes.combineAndSimplify(finalShape, add, BooleanBiFunction.OR);


        }

        Sprite texture = RenderUtils.getSprite(entity.getWorld(), entity.getPos(), toRender.getFluid());
        int[] color = RenderUtils.unpackColor(RenderUtils.color(entity.getWorld(), entity.getPos(), toRender.getFluid()));

        finalShape = VoxelShapes.combineAndSimplify(finalShape, mergeShape, BooleanBiFunction.AND);

        var boxes = finalShape.getBoundingBoxes();
        for (var box : boxes) {
            renderLargeSprite(vertexConsumers.getBuffer(RenderLayer.getTranslucentMovingBlock()), matrices, texture, light, overlay, (float) box.minX, (float) box.maxX, (float) box.minY, (float) box.maxY, (float) box.minZ, (float) box.maxZ, color, 1f);
        }
    }
}
