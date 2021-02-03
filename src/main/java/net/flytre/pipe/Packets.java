package net.flytre.pipe;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.flytre.flytre_lib.common.inventory.FilterInventory;
import net.flytre.pipe.pipe.PipeEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


public class Packets {
    public static final Identifier FILTER_TYPE = new Identifier("pipe", "filter_type");
    public static final Identifier PIPE_MODE = new Identifier("pipe", "pipe_mode");
    public static final Identifier NBT_MATCH = new Identifier("pipe", "nbt_match");
    public static final Identifier MOD_MATCH = new Identifier("pipe", "mod_match");


    public static void serverPacketRecieved() {


        ServerPlayNetworking.registerGlobalReceiver(FILTER_TYPE, (server, player, handler, attachedData, responseSender) -> {

            BlockPos pos = attachedData.readBlockPos();
            int newMode = attachedData.readInt();
            World world = player.getEntityWorld();
            server.execute(() -> {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof PipeEntity) {
                    PipeEntity ip = (PipeEntity) entity;
                    FilterInventory iv = ip.getFilter();
                    iv.setFilterType(newMode);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(NBT_MATCH, (server, player, handler, attachedData, responseSender) -> {

            BlockPos pos = attachedData.readBlockPos();
            int newMode = attachedData.readInt();
            World world = player.getEntityWorld();
            server.execute(() -> {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof PipeEntity) {
                    PipeEntity ip = (PipeEntity) entity;
                    FilterInventory iv = ip.getFilter();
                    iv.setMatchNbt(newMode == 1);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MOD_MATCH, (server, player, handler, attachedData, responseSender) -> {

            BlockPos pos = attachedData.readBlockPos();
            int newMode = attachedData.readInt();
            World world = player.getEntityWorld();
            server.execute(() -> {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof PipeEntity) {
                    PipeEntity ip = (PipeEntity) entity;
                    FilterInventory iv = ip.getFilter();
                    iv.setMatchMod(newMode == 1);
                }
            });
        });


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
