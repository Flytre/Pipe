package net.flytre.pipe.impl.registry;

import net.flytre.flytre_lib.loader.LoaderAgnosticClientRegistry;
import net.flytre.flytre_lib.loader.RenderLayerRegistry;
import net.flytre.pipe.impl.client.FluidPipeScreen;
import net.flytre.pipe.impl.client.ItemPipeRenderer;
import net.flytre.pipe.impl.client.ItemPipeScreen;
import net.flytre.pipe.impl.fluid.FluidPipeRenderer;
import net.minecraft.client.render.RenderLayer;

public class ClientRegistry {

    public static void init() {
        RenderLayerRegistry.registerBlockLayer(RenderLayer.getTranslucent(), Registry.ITEM_PIPE);
        RenderLayerRegistry.registerBlockLayer(RenderLayer.getTranslucent(), Registry.FAST_PIPE);
        RenderLayerRegistry.registerBlockLayer(RenderLayer.getTranslucent(), Registry.LIGHTNING_PIPE);
        RenderLayerRegistry.registerBlockLayer(RenderLayer.getTranslucent(), Registry.FLUID_PIPE);
        LoaderAgnosticClientRegistry.register(() -> Registry.ITEM_PIPE_BLOCK_ENTITY.get(), ItemPipeRenderer::new);
        LoaderAgnosticClientRegistry.register(() -> Registry.ITEM_PIPE_SCREEN_HANDLER.get(), ItemPipeScreen::new);
        LoaderAgnosticClientRegistry.register(() -> Registry.FLUID_PIPE_BLOCK_ENTITY.get(), FluidPipeRenderer::new);
        LoaderAgnosticClientRegistry.register(() -> Registry.FLUID_PIPE_SCREEN_HANDLER.get(), FluidPipeScreen::new);
    }
}
