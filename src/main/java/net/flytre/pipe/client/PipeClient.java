package net.flytre.pipe.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.flytre.pipe.Pipe;
import net.minecraft.client.render.RenderLayer;

@Environment(EnvType.CLIENT)
public class PipeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(Pipe.ITEM_PIPE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(Pipe.FAST_PIPE, RenderLayer.getCutout());
        BlockEntityRendererRegistry.INSTANCE.register(Pipe.ITEM_PIPE_BLOCK_ENTITY, PipeRenderer::new);
        ScreenRegistry.register(Pipe.ITEM_PIPE_SCREEN_HANDLER, PipeScreen::new);
    }
}
