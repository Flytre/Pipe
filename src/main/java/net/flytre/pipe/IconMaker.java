package net.flytre.pipe;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;

@FunctionalInterface
public interface IconMaker<T extends Block> {

    IconMaker<Block> STANDARD = (block) -> new BlockItem(block, new Item.Settings().group(Pipe.TAB));

    BlockItem create(T block);
}
