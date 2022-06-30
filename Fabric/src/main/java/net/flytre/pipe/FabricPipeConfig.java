package net.flytre.pipe;


import com.google.gson.annotations.SerializedName;
import net.flytre.flytre_lib.api.config.ConfigEventAcceptor;
import net.flytre.flytre_lib.api.config.annotation.Description;
import net.flytre.flytre_lib.api.config.annotation.DisplayName;
import net.flytre.flytre_lib.api.config.network.SyncedConfig;
import net.flytre.pipe.impl.item.VanillaItemPipeLogic;
import net.flytre.pipe.impl.item.ItemPipeBlock;
import net.flytre.pipe.impl.item.ItemPipeEntity;
import net.flytre.pipe.impl.item.FabricItemPipeLogic;

public class FabricPipeConfig implements SyncedConfig, ConfigEventAcceptor {

    @Description("Pipes have swappable logic. Pipes can directly use vanilla inventory logic, or the fabric transfer API. The transfer API is unstable, so set this config option to VANILLA if something breaks. Setting this field requires a game restart.")
    @SerializedName("pipe_logic_type")
    @DisplayName("Pipe Logic Type")
    public PipeLogicType pipeLogicType;

    public FabricPipeConfig() {
        this.pipeLogicType = PipeLogicType.FABRIC;
    }

    public enum PipeLogicType {
        VANILLA,
        FABRIC
    }

    @Override
    public void onReload() {
        if (pipeLogicType == FabricPipeConfig.PipeLogicType.FABRIC) {
            ItemPipeEntity.setItemPipeLogic(FabricItemPipeLogic.INSTANCE);
            ItemPipeBlock.setItemPipeLogic(FabricItemPipeLogic.INSTANCE);
        } else {
            ItemPipeEntity.setItemPipeLogic(VanillaItemPipeLogic.INSTANCE);
            ItemPipeBlock.setItemPipeLogic(VanillaItemPipeLogic.INSTANCE);
        }
    }
}
