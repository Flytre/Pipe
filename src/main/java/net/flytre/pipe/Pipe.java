package net.flytre.pipe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.flytre.flytre_lib.api.base.util.PacketUtils;
import net.flytre.flytre_lib.api.config.ConfigHandler;
import net.flytre.flytre_lib.api.config.ConfigRegistry;
import net.flytre.pipe.network.PipeModeC2SPacket;
import net.flytre.pipe.pipe.PipeHandler;
import net.flytre.pipe.pipe.PipeBlock;
import net.flytre.pipe.pipe.PipeEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class Pipe implements ModInitializer {

    public static final Block ITEM_PIPE = new PipeBlock(FabricBlockSettings.of(Material.METAL).hardness(0.9f));
    public static final Block FAST_PIPE = new PipeBlock(FabricBlockSettings.of(Material.METAL).hardness(0.6f));
    public static final ItemGroup TAB = FabricItemGroupBuilder.build(
            new Identifier("pipe", "all"),
            () -> new ItemStack(ITEM_PIPE));
    public static BlockEntityType<PipeEntity> ITEM_PIPE_BLOCK_ENTITY;
    public static ScreenHandlerType<PipeHandler> ITEM_PIPE_SCREEN_HANDLER;
    public static ConfigHandler<Config> PIPE_CONFIG = new ConfigHandler<>(new Config(), "pipe");

    public static <T extends Block> void registerBlock(T block, String id, IconMaker<T> creator) {
        Registry.register(Registry.BLOCK, new Identifier("pipe", id), block);
        Registry.register(Registry.ITEM, new Identifier("pipe", id), creator.create(block));
    }

    @Override
    public void onInitialize() {
        registerBlock(ITEM_PIPE, "item_pipe", IconMaker.STANDARD);
        registerBlock(FAST_PIPE, "fast_pipe", IconMaker.STANDARD);
        ITEM_PIPE_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, "pipe:item_pipe", FabricBlockEntityTypeBuilder.create(PipeEntity::new, ITEM_PIPE, FAST_PIPE).build(null));
        ItemRegistry.init();
        ITEM_PIPE_SCREEN_HANDLER = ScreenHandlerRegistry.registerExtended(new Identifier("pipe", "item_pipe"), PipeHandler::new);
        PacketUtils.registerC2SPacket(PipeModeC2SPacket.class, PipeModeC2SPacket::new);
        ConfigRegistry.registerServerConfig(PIPE_CONFIG);
    }
}
