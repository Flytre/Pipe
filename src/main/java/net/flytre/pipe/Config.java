package net.flytre.pipe;

import com.google.gson.annotations.SerializedName;

public class Config {
    @SerializedName("render_items")
    private boolean renderItems = true;

    public boolean shouldRenderItems() {
        return renderItems;
    }
}
