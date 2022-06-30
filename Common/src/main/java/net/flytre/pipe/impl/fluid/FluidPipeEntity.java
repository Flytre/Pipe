package net.flytre.pipe.impl.fluid;

import net.flytre.flytre_lib.api.storage.fluid.core.FluidFilterInventory;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;
import net.flytre.flytre_lib.api.storage.inventory.filter.FilterSettings;
import net.flytre.flytre_lib.api.storage.inventory.filter.HasFilterSettings;
import net.flytre.flytre_lib.api.storage.inventory.filter.packet.FilterEventHandler;
import net.flytre.flytre_lib.loader.CustomScreenHandlerFactory;
import net.flytre.pipe.api.AbstractPipeEntity;
import net.flytre.pipe.api.FilteredPipe;
import net.flytre.pipe.api.PipeLogic;
import net.flytre.pipe.api.ResourceHandler;
import net.flytre.pipe.impl.item.VanillaItemPipeLogic;
import net.flytre.pipe.impl.registry.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

public class FluidPipeEntity extends AbstractPipeEntity<FluidStack, FluidFilterInventory> implements CustomScreenHandlerFactory, FilterEventHandler, HasFilterSettings, FilteredPipe {

    private static PipeLogic<FluidStack> FLUID_PIPE_LOGIC;



    public FluidPipeEntity(BlockPos pos, BlockState state) {
        super(Registry.FLUID_PIPE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.pipe.fluid_pipe");
    }

    @Override
    public @NotNull ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new FluidPipeHandler(syncId, inv, this);
    }

    @Override
    public void onPacketReceived() {
        clearNetworkCache(false);
    }


    @Override
    protected ResourceHandler<FluidStack, FluidFilterInventory> getResourceHandler() {
        return FluidResourceHandler.INSTANCE;
    }

    @Override
    protected PipeLogic<FluidStack> getPipeLogic() {
        if(FLUID_PIPE_LOGIC == null)
            throw new AssertionError("Fluid pipe logic not set");
        return FLUID_PIPE_LOGIC;
    }

    //fluid pipes should pull every tick
    @Override
    protected int getBaseTicksBetweenPull() {
        return 1;
    }

    @Override
    protected int getBaseTicksThroughPipe() {
        return 20;
    }

    @Override
    protected long getAmountToExtract() {
        return FluidStack.UNITS_PER_BUCKET / 20;
    }

    @Override
    public FilterSettings getFilterSettings() {
        return getFilter();
    }

    /**
     * Send data to the client about what to display on the pipe's screen.
     */
    @Override
    public void sendPacket(PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(pos);
        getResourceHandler().writeFilter(packetByteBuf, getFilter());
        packetByteBuf.writeBoolean(this.isRoundRobinMode());
    }

    @Override
    public boolean canAccept(Object resource) {
        if(!(resource instanceof FluidStack stack))
            return false;

        Collection<FluidStack> collection = getContainedResources();

        Optional<Long> qty = collection.stream().map(FluidStack::getAmount).reduce(Long::sum);
        if (qty.isPresent() && qty.get() > getAmountToExtract() * 40)
            return false;

        FluidStack type = FluidStack.EMPTY;
        for (FluidStack next : collection) {
            if (!next.isEmpty()) {
                type = next;
                break;
            }
        }
        return type.isEmpty() || stack.getFluid() == type.getFluid();
    }

    public static void setFluidPipeLogic(PipeLogic<FluidStack> fluidPipeLogic) {
        FLUID_PIPE_LOGIC = fluidPipeLogic;
    }
}
