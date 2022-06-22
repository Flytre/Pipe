package net.flytre.pipe;


import net.flytre.flytre_lib.loader.LoaderCore;
import net.flytre.pipe.impl.ItemPipeBlock;
import net.flytre.pipe.impl.ItemPipeEntity;
import net.flytre.pipe.impl.registry.ClientRegistry;
import net.flytre.pipe.impl.registry.Constants;
import net.flytre.pipe.impl.registry.Registry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod(Constants.MOD_ID)
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeMod {

    public ForgeMod() {
        LoaderCore.registerForgeMod(Constants.MOD_ID, Registry::init);

        if (FMLLoader.getDist() == Dist.CLIENT) {
            ClientRegistry.init();
            ForgeClientSetup.init();
        }
        ItemPipeEntity.setItemPipeLogic(ForgeItemPipeLogic.INSTANCE);
        ItemPipeBlock.setItemPipeLogic(ForgeItemPipeLogic.INSTANCE);
    }
}