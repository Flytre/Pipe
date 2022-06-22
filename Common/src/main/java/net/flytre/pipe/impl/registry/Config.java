package net.flytre.pipe.impl.registry;

import com.google.gson.annotations.SerializedName;
import net.flytre.flytre_lib.api.config.annotation.Description;
import net.flytre.flytre_lib.api.config.annotation.DisplayName;

public class Config {

    @Description("Whether items should be rendered inside pipes.")
    @SerializedName("render_items")
    @DisplayName("Render Items")
    public boolean renderItems;

    @Description("How far the player can see items rendered in the pipe from.")
    @SerializedName("max_item_render_distance")
    @DisplayName("Max Render Distance")
    public int maxItemRenderDistance;


    @Description("If an item has to move more than this many blocks through pipes to reach its destination inventory, it won't be rendered.")
    @SerializedName("max_render_pipe_length")
    @DisplayName("Max Render Pipe Length")
    public int maxRenderPipeLength;

    public Config() {
        renderItems = true;
        maxItemRenderDistance = 24;
        maxRenderPipeLength = 64;
    }
}
