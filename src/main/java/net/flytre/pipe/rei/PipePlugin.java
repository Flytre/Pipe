package net.flytre.pipe.rei;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import net.flytre.pipe.client.PipeScreen;

import java.util.List;

public class PipePlugin implements REIClientPlugin {

    @Override
    public void registerScreens(ScreenRegistry registry) {
        ExclusionZones exclusionZones = registry.exclusionZones();
        exclusionZones.register(PipeScreen.class, screen ->
        {
            int x = screen.getX();
            int y = screen.getY();
            return List.of(new Rectangle(x, y, 176, 170), new Rectangle(x + 176, y, 20, 80));
        });
    }
}
