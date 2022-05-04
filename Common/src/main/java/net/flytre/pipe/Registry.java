package net.flytre.pipe;

import net.flytre.flytre_lib.api.base.util.PacketUtils;
import net.flytre.flytre_lib.api.config.ConfigHandler;
import net.flytre.flytre_lib.api.config.ConfigRegistry;
import net.flytre.flytre_lib.loader.BlockEntityFactory;
import net.flytre.flytre_lib.loader.ItemTabCreator;
import net.flytre.flytre_lib.loader.LoaderAgnosticRegistry;
import net.flytre.pipe.network.PipeModeC2SPacket;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public class Registry {


    public static final ItemGroup TAB = ItemTabCreator.create(
            new Identifier("pipe", "all"), Registry::getTabStack);

    public static final Supplier<Block> ITEM_PIPE = registerBlock(() -> new PipeBlock(AbstractBlock.Settings.of(Material.METAL).hardness(0.9f)), "item_pipe");
    public static final Supplier<Block> FAST_PIPE = registerBlock(() -> new PipeBlock(AbstractBlock.Settings.of(Material.METAL).hardness(0.6f)), "fast_pipe");


    public static final ConfigHandler<Config> PIPE_CONFIG = new ConfigHandler<>(new Config(), "pipe");
    public static Supplier<BlockEntityType<PipeEntity>> ITEM_PIPE_BLOCK_ENTITY;
    public static Supplier<ScreenHandlerType<PipeHandler>> ITEM_PIPE_SCREEN_HANDLER;

    public static <T extends Block> Supplier<T> registerBlock(Supplier<T> block, String id) {
        final var temp = LoaderAgnosticRegistry.registerBlock(block, Constants.MOD_ID, id);
        LoaderAgnosticRegistry.registerItem(() -> new BlockItem(temp.get(), new Item.Settings().group(TAB)), Constants.MOD_ID, id);
        return temp;
    }

    private static ItemStack getTabStack() {
        return new ItemStack(ITEM_PIPE.get());
    }


    public static void init() {
        ITEM_PIPE_BLOCK_ENTITY = LoaderAgnosticRegistry.registerBlockEntityType(() -> BlockEntityFactory.createBuilder(PipeEntity::new, ITEM_PIPE.get(), FAST_PIPE.get()).build(null), "pipe", "item_pipe");
        ItemRegistry.init();
        ITEM_PIPE_SCREEN_HANDLER = LoaderAgnosticRegistry.registerExtendedScreen(PipeHandler::new, "pipe", "item_pipe");

        PacketUtils.registerC2SPacket(PipeModeC2SPacket.class, PipeModeC2SPacket::new);
        ConfigRegistry.registerServerConfig(PIPE_CONFIG);
    }


}
