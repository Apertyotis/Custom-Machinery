package fr.frinn.custommachinery.forge.transfer;

import fr.frinn.custommachinery.common.component.EnergyMachineComponent;
import fr.frinn.custommachinery.common.component.handler.RemoteEnergyComponentHandler;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.transfer.ICommonEnergyHandler;
import fr.frinn.custommachinery.impl.component.config.RelativeSide;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;


public class ForgeRemoteEnergyHandler implements ICommonEnergyHandler {

    private final RemoteEnergyComponentHandler remoteEnergyHandler;
    private final CustomMachineTile tile;

    private EnergyMachineComponent innerEnergyHandler;

    private boolean initialized = false;

    public ForgeRemoteEnergyHandler(RemoteEnergyComponentHandler handler) {
        this.remoteEnergyHandler = handler;
        this.tile = (CustomMachineTile) handler.getManager().getTile();
    }

    private boolean init() {
        innerEnergyHandler = remoteEnergyHandler.getManager()
                .getComponent(Registration.ENERGY_MACHINE_COMPONENT.get()).orElse(null);
        if (innerEnergyHandler == null)
            return false;
        initialized = true;
        return true;
    }

    @Override
    public void configChanged(RelativeSide side, SideMode oldMode, SideMode newMode) {}

    @Override
    public void invalidate() {}

    @Override
    public void tick() {
        BlockPos tilePos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null || level.isClientSide())
            return;

        if (!initialized && !init())
            return;

        for (var remote : remoteEnergyHandler.getComponents()) {
            Direction d = tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);

            BlockEntity be = level.getBlockEntity(Utils.rotatePos(remote.getPos(), d).offset(tilePos));
            if (be == null)
                continue;
            be.getCapability(ForgeCapabilities.ENERGY).ifPresent(storage -> {
                if(remote.getMode().isInput() && innerEnergyHandler.getEnergy() < innerEnergyHandler.getCapacity()) {
                    int tryExtract = storage.extractEnergy(Integer.MAX_VALUE, true);
                    int inserted = (int) innerEnergyHandler.receiveEnergy(tryExtract, false);
                    if (inserted > 0)
                        storage.extractEnergy(inserted, false);
                }

                if(remote.getMode().isOutput() && innerEnergyHandler.getEnergy() > 0) {
                    int tryExtract = (int) innerEnergyHandler.extractEnergy(Integer.MAX_VALUE, true);
                    int inserted = storage.receiveEnergy(tryExtract, false);
                    if (inserted > 0)
                        innerEnergyHandler.extractEnergy(inserted, false);
                }
            });
        }
    }
}
