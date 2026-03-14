package fr.frinn.custommachinery.forge.transfer;

import fr.frinn.custommachinery.common.component.handler.ItemComponentHandler;
import fr.frinn.custommachinery.common.component.handler.RemoteItemComponentHandler;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.transfer.ICommonItemHandler;
import fr.frinn.custommachinery.impl.component.config.RelativeSide;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;

public class ForgeRemoteItemHandler implements ICommonItemHandler {

    private final RemoteItemComponentHandler remoteItemHandler;
    private final CustomMachineTile tile;

    private SidedItemHandler innerItemHandler;

    private boolean initialized = false;

    public ForgeRemoteItemHandler(RemoteItemComponentHandler handler) {
        this.remoteItemHandler = handler;
        this.tile = (CustomMachineTile) handler.getManager().getTile();
    }

    private boolean init() {
        ItemComponentHandler itemHandler = (ItemComponentHandler) remoteItemHandler.getManager()
                .getComponentHandler(Registration.ITEM_MACHINE_COMPONENT.get()).orElse(null);
        if (itemHandler == null)
            return false;
        innerItemHandler = new SidedItemHandler(null, itemHandler);
        initialized = true;
        return true;
    }

    @Override
    public void configChanged(RelativeSide side, SideMode oldMode, SideMode newMode) {}

    @Override
    public void invalidate() {}

    @Override
    public void tick() {
        if (!tile.shouldAutoIO())
            return;

        BlockPos tilePos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null || level.isClientSide())
            return;

        if (!initialized && !init())
            return;

        for (var remote : remoteItemHandler.getComponents()) {
            Direction d = tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);

            BlockEntity be = level.getBlockEntity(Utils.rotatePos(remote.getPos(), d).offset(tilePos));
            if (be == null)
                continue;
            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(storage -> {
                if (remote.getMode().isInput()) {
                    List<ItemSlot> inputCandidate = innerItemHandler.getSlotList().stream().filter(
                            slot -> remote.isSlotValid(slot.getComponent().getId())
                                    && slot.getComponent().getItemStack().getCount() < slot.getComponent().getCapacity()
                            ).toList();
                    ForgeItemHandler.autoInput(inputCandidate, storage, remote::isItemValid);
                }

                if (remote.getMode().isOutput()) {
                    List<ItemSlot> outputCandidate = innerItemHandler.getSlotList().stream().filter(
                            slot -> remote.isSlotValid(slot.getComponent().getId())
                                    && !slot.getComponent().getItemStack().isEmpty()
                            ).toList();
                    ForgeItemHandler.autoOutput(outputCandidate, storage, remote::isItemValid);
                }
            });
        }
    }
}
