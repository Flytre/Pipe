package net.flytre.pipe.impl.client;

import com.google.common.collect.Lists;
import net.flytre.pipe.impl.ItemPipeEntity;
import net.flytre.pipe.api.TimedPipePath;
import net.flytre.pipe.api.ClientPathValidityChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3f;

import java.util.LinkedList;
import java.util.List;

public class ItemPipeRenderer implements BlockEntityRenderer<ItemPipeEntity> {

    private static final List<ClientPathValidityChecker<ItemStack>> VALIDITY_CHECKERS = Lists.newArrayList(ClientPathValidityChecker.PIPE);

    public ItemPipeRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    public static void addValidityChecker(ClientPathValidityChecker<ItemStack> checker) {
        VALIDITY_CHECKERS.add(checker);
    }

    @Override
    public void render(ItemPipeEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {


        int ticksPerOperation = entity.getTicksPerOperation();

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);

        for (TimedPipePath<ItemStack> timed : entity.getQueuedResources()) {

            if (timed.getTime() < -1)
                continue;

            matrices.push();
            LinkedList<BlockPos> path = timed.getPath();
            BlockPos current = path.size() > 0 ? path.get(0) : entity.getPos();
            BlockPos next = path.size() <= 1 ? timed.getDestination() : path.get(1);

            float mult = ticksPerOperation - timed.getTime() + tickDelta;

            if (entity.getWorld() != null && VALIDITY_CHECKERS.stream().noneMatch(checker -> checker.isValidPath(entity.getWorld(), current, next))) {
                mult = 0;
            }

            float dx = (next.getX() - current.getX()) / (float) ticksPerOperation * (mult);
            float dy = (next.getY() - current.getY()) / (float) ticksPerOperation * (mult);
            float dz = (next.getZ() - current.getZ()) / (float) ticksPerOperation * (mult);

            if (timed.getAnim() != null) {
                if (mult < 0f) {
                    BlockPos pos = current.offset(timed.getAnim());
                    dx = (pos.getX() - current.getX()) / (float) ticksPerOperation * (-mult);
                    dy = (pos.getY() - current.getY()) / (float) ticksPerOperation * (-mult);
                    dz = (pos.getZ() - current.getZ()) / (float) ticksPerOperation * (-mult);
                }
            }
            if (!timed.isStuck())
                matrices.translate(dx, dy, dz);

            matrices.translate(0, -0.125, 0);

            if (timed.getAnim() != null && mult < 0f) {
                float scale = 0.5f + (mult + (float) (ticksPerOperation / 2)) / (float) (ticksPerOperation / 2) * 0.3f;
                matrices.scale(scale, scale, scale);
            } else
                matrices.scale(0.8f, 0.8f, 0.8f);
            if (entity.getWorld() != null)
                matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((entity.getWorld().getTime() + tickDelta) * 4 * (20.0f / ticksPerOperation)));
            MinecraftClient.getInstance().getItemRenderer().renderItem(timed.getResource(), ModelTransformation.Mode.GROUND, light, overlay, matrices, vertexConsumers, 0);
            matrices.pop();
        }

        matrices.pop();
    }
}
