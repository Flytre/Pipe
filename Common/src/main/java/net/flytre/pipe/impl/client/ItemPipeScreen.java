package net.flytre.pipe.impl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.flytre.flytre_lib.api.storage.inventory.filter.FilteredScreen;
import net.flytre.flytre_lib.api.storage.inventory.filter.ScreenWithFilter;
import net.flytre.flytre_lib.api.storage.inventory.filter.packet.BlockFilterModeC2SPacket;
import net.flytre.flytre_lib.api.storage.inventory.filter.packet.BlockModMatchC2SPacket;
import net.flytre.flytre_lib.api.storage.inventory.filter.packet.BlockNbtMatchC2SPacket;
import net.flytre.pipe.impl.network.ItemPipeHandler;
import net.flytre.pipe.impl.network.PipeModeC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class ItemPipeScreen extends HandledScreen<ItemPipeHandler> implements ScreenWithFilter {

    private static final Identifier PIPE_MODE_BUTTON = new Identifier("pipe:textures/gui/button/pipe_mode.png");
    protected final ItemPipeHandler handler;
    private final Identifier background;
    private boolean synced;

    public ItemPipeScreen(ItemPipeHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.handler = handler;
        this.background = new Identifier("pipe:textures/gui/container/item_pipe.png");
        synced = false;
    }

    private void onSynced() {
        addButton(handler.getFilterType(), 0, MODE_BUTTON, BlockFilterModeC2SPacket::new, handler::getPos, Text.translatable("block.pipe.item_pipe.whitelist"), Text.translatable("block.pipe.item_pipe.blacklist"));
        addButton(handler.getRoundRobinMode(), 1, PIPE_MODE_BUTTON, PipeModeC2SPacket::new, handler::getPos, Text.translatable("block.pipe.item_pipe.closest"), Text.translatable("block.pipe.item_pipe.round_robin"));
        addButton(handler.getModMatch(), 2, MOD_BUTTON, BlockModMatchC2SPacket::new, handler::getPos, Text.translatable("block.pipe.item_pipe.mod_match.false"), Text.translatable("block.pipe.item_pipe.mod_match.true"));
        addButton(handler.getNbtMatch(), 3, NBT_BUTTON, BlockNbtMatchC2SPacket::new, handler::getPos, Text.translatable("block.pipe.item_pipe.nbt_match.false"), Text.translatable("block.pipe.item_pipe.nbt_match.true"));

    }

    @Override
    public void handledScreenTick() {
        if (!synced && handler.getSynced()) {
            onSynced();
            synced = true;
        }
    }

    @Override
    protected void init() {
        synced = false;
        super.init();
    }

    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, background);
        assert this.client != null;
        this.client.getTextureManager().bindTexture(this.background);
        this.drawTexture(matrices, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);

    }


    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(matrices, mouseX, mouseY);
    }

    @Override
    public MinecraftClient getClient() {
        return client;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void renderTooltipShadow(MatrixStack matrixStack, List<Text> list, int i, int i1) {
        renderTooltip(matrixStack, list, i, i1);
    }

    @Override
    public <T extends Element & Drawable & Selectable> T addDrawableChildShadow(T t) {
        return addDrawableChild(t);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }
}
