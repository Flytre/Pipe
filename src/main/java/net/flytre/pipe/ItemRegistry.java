package net.flytre.pipe;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.flytre.pipe.item.ServoItem;
import net.flytre.pipe.item.PipeWrench;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ItemRegistry {
    public static final Item SERVO = new ServoItem(new FabricItemSettings().group(Pipe.TAB));
    public static final Item WRENCH = new PipeWrench(new FabricItemSettings().group(Pipe.TAB).maxCount(1));

    public static void init() {
        Registry.register(Registry.ITEM, new Identifier("pipe", "servo"), SERVO);
        Registry.register(Registry.ITEM, new Identifier("pipe", "wrench"), WRENCH);
    }
}
