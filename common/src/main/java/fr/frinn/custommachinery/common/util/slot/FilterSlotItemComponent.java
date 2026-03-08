package fr.frinn.custommachinery.common.util.slot;

import fr.frinn.custommachinery.common.component.ItemMachineComponent;
import fr.frinn.custommachinery.common.network.CSetFilterSlotItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class FilterSlotItemComponent extends SlotItemComponent {
    private boolean rendering = false;

    public FilterSlotItemComponent(ItemMachineComponent component, int index, int x, int y) {
        super(component, index, x, y);
    }

    //Call from the client only, will ask the server to place the item in the slot to avoid desync issues.
    public void setFromClient(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        new CSetFilterSlotItemPacket(copy, this.getComponent().getManager().getTile().getBlockPos(), this.getComponent().getId()).sendToServer();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        this.getComponent().setItemStack(copy);
        return false;
    }

    public void clearFilterItem() {
        this.getComponent().setItemStack(ItemStack.EMPTY);
    }

    public void setRendering(boolean rendering) {
        this.rendering = rendering;
    }

    // 仅渲染期间返回内部物品
    @Override
    public @NotNull ItemStack getItem() {
        if (rendering)
            return super.getItem();
        else
            return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
