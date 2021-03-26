package net.flytre.pipe.rei;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.BaseBoundsHandler;
import me.shedaniel.rei.api.DisplayHelper;
import me.shedaniel.rei.api.plugins.REIPluginV0;
import net.flytre.flytre_lib.client.gui.CoordinateProvider;
import net.flytre.pipe.client.PipeScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class PipePlugin implements REIPluginV0 {

    public static final Identifier PLUGIN = new Identifier("pipe", "rei_plugin");


    @Override
    public Identifier getPluginIdentifier() {
        return PLUGIN;
    }


    @Override
    public void registerBounds(DisplayHelper displayHelper) {
        BaseBoundsHandler baseBoundsHandler = BaseBoundsHandler.getInstance();
        baseBoundsHandler.registerExclusionZones(PipeScreen.class, () ->
        {
            Screen currentScreen = MinecraftClient.getInstance().currentScreen;
            List<Rectangle> result = new ArrayList<>();
            if (currentScreen instanceof PipeScreen) {
                PipeScreen pipeScreen = (PipeScreen) currentScreen;
                int x = pipeScreen.getX();
                int y = pipeScreen.getY();
                result.add(new Rectangle(x, y, 176, 170));
                result.add(new Rectangle(x + 176, y, 20, 80));
            }
            return result;
        });
    }
}
