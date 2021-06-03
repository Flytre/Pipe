package net.flytre.pipe;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.flytre.flytre_lib.common.inventory.filter.FilterInventory;
import net.flytre.pipe.pipe.PipeEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


public class Packets {
    public static final Identifier PIPE_MODE = new Identifier("pipe", "pipe_mode");

    public static void serverPacketRecieved() {


        ServerPlayNetworking.registerGlobalReceiver(PIPE_MODE, (server, player, handler, attachedData, responseSender) -> {
            BlockPos pos = attachedData.readBlockPos();
            int newMode = attachedData.readInt();
            World world = player.getEntityWorld();
            server.execute(() -> {
                BlockEntity entity = world.getBlockEntity(pos);
                if (!(entity instanceof PipeEntity))
                    return;
                PipeEntity ip = (PipeEntity) entity;
                ip.setRoundRobinMode(newMode != 0);
            });
        });


    }
}
