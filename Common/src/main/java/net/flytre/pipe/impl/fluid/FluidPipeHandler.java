package net.flytre.pipe.impl.fluid;

import net.flytre.flytre_lib.api.storage.fluid.core.FluidFilterInventory;
import net.flytre.flytre_lib.api.storage.fluid.core.FluidStack;
import net.flytre.flytre_lib.api.storage.fluid.gui.FluidHandler;
import net.flytre.flytre_lib.api.storage.fluid.gui.FluidSlot;
import net.flytre.flytre_lib.mixin.storage.fluid.BucketItemAccessor;
import net.flytre.pipe.impl.registry.Registry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

public class FluidPipeHandler extends ScreenHandler {
    private final FluidFilterInventory inv;
    private BlockPos pos;
    private boolean synced;
    private int filterType;
    private boolean matchMod;
    private boolean matchNbt;
    private boolean isRoundRobin;

    public FluidPipeHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new FluidPipeEntity(BlockPos.ORIGIN, Registry.FLUID_PIPE.get().getDefaultState()));
        pos = buf.readBlockPos();
        synced = true;
        filterType = buf.readInt();
        matchMod = buf.readBoolean();
        matchNbt = buf.readBoolean();
        isRoundRobin = buf.readBoolean();
    }

    public FluidPipeHandler(int syncId, PlayerInventory playerInventory, FluidPipeEntity entity) {
        super(Registry.FLUID_PIPE_SCREEN_HANDLER.get(), syncId);

        this.inv = entity.getFilter();
        pos = BlockPos.ORIGIN;

        inv.onOpen(playerInventory.player);
        int m;
        int l;
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 3; ++l) {
                ((FluidHandler) this).addSlot(new FluidSlot(inv, l + m * 3, 62 + l * 18, 17 + m * 18, true) {
                    @Override
                    public void markDirty() {
                        entity.clearNetworkCache(false);
                    }
                });
            }
        }

        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 84 + m * 18));
            }
        }

        for (m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 142));
        }
    }

    /**
     * \@Override
     */
    public FluidStack onFluidSlotClick(int slotId, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        FluidSlot slot = ((FluidHandler) this).getFluidSlots().get(slotId);
        FluidStack slotStack = slot.getStack();

        if (!slotStack.isEmpty()) {
            slot.setStack(FluidStack.EMPTY);
            return slotStack;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        throw new AssertionError("This method should not be called");
    }

    @Override
    public void onSlotClick(int slotId, int clickData, SlotActionType actionType, PlayerEntity playerEntity) {


        if (slotId < 0) {
            super.onSlotClick(slotId, clickData, actionType, playerEntity);
            return;
        }

        ItemStack stack = getSlot(slotId).getStack();

        if (stack.getItem() instanceof BucketItem item) {
            Fluid fluid = ((BucketItemAccessor) item).getFluid();
            if (!inv.containsAnyFluid(Set.of(fluid))) {
                for (int i = 0; i < ((FluidHandler) this).getFluidSlots().size(); i++) {
                    FluidSlot slot = ((FluidHandler) this).getFluidSlot(i);
                    if (slot.inventory == inv && slot.getStack().isEmpty()) {
                        slot.setStack(new FluidStack(fluid, 1));
                        break;
                    }
                }
            }
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        this.inv.onClose(player);
    }

    public boolean getSynced() {
        return synced;
    }

    public int getFilterType() {
        return filterType;
    }

    public int getModMatch() {
        return matchMod ? 1 : 0;
    }


    public int getNbtMatch() {
        return matchNbt ? 1 : 0;
    }

    public int getRoundRobinMode() {
        return isRoundRobin ? 1 : 0;
    }

}
