package net.flytre.pipe;

import com.google.gson.annotations.SerializedName;

public class Config {
    @SerializedName("render_items")
    public final boolean renderItems;

    @SerializedName("max_item_render_distance")
    public final int maxItemRenderDistance;

    @SerializedName("max_render_pipe_length")
    public final int maxRenderPipeLength;

    public Config() {
        renderItems = true;
        maxItemRenderDistance = 24;
        maxRenderPipeLength = 50;
    }
}
