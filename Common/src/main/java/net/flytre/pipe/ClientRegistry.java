package net.flytre.pipe;

import net.flytre.flytre_lib.loader.LoaderAgnosticClientRegistry;
import net.flytre.flytre_lib.loader.RenderLayerRegistry;
import net.flytre.pipe.client.PipeRenderer;
import net.flytre.pipe.client.PipeScreen;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.RenderLayer;

public class ClientRegistry {

    public static void init() {
        RenderLayerRegistry.registerBlockLayer(RenderLayer.getTranslucent(), Registry.ITEM_PIPE);
        RenderLayerRegistry.registerBlockLayer(RenderLayer.getTranslucent(), Registry.FAST_PIPE);
        LoaderAgnosticClientRegistry.register(() -> Registry.ITEM_PIPE_BLOCK_ENTITY.get(), PipeRenderer::new);
        LoaderAgnosticClientRegistry.register(() -> Registry.ITEM_PIPE_SCREEN_HANDLER.get(), PipeScreen::new);
    }
}
