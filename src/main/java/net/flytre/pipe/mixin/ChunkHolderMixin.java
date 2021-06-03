package net.flytre.pipe.mixin;

import net.flytre.pipe.Pipe;
import net.flytre.pipe.pipe.PipeEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {

    @Shadow
    @Final
    private ChunkHolder.PlayersWatchingChunkProvider playersWatchingChunkProvider;
    @Shadow
    @Final
    ChunkPos pos;

    @Inject(method = "sendBlockEntityUpdatePacket", at = @At("HEAD"), cancellable = true)
    public void pipe$customPipeUpdates(World world, BlockPos pos, CallbackInfo ci) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof PipeEntity))
            return;
        ci.cancel();
        PipeEntity pipeEntity = (PipeEntity) blockEntity;
        BlockEntityUpdateS2CPacket blockEntityUpdateS2CPacket = pipeEntity.toUpdatePacket();
        if (blockEntityUpdateS2CPacket != null) {
            this.pipe$sendPacketToNearPlayers(pos, blockEntityUpdateS2CPacket);
        }
    }

    private void pipe$sendPacketToNearPlayers(BlockPos pos, BlockEntityUpdateS2CPacket packet) {
        int distance = Pipe.PIPE_CONFIG.getConfig().maxItemRenderDistance;
        this.playersWatchingChunkProvider.getPlayersWatchingChunk(this.pos, false).forEach((serverPlayerEntity) -> {
            if (serverPlayerEntity.getBlockPos().getSquaredDistance(pos) < distance * distance) {
                serverPlayerEntity.networkHandler.sendPacket(packet);
            }
        });
    }
}
