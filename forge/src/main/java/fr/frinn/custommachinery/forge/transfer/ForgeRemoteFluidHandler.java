package fr.frinn.custommachinery.forge.transfer;

import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import fr.frinn.custommachinery.common.component.FluidMachineComponent;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.component.handler.RemoteFluidComponentHandler;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.transfer.ICommonFluidHandler;
import fr.frinn.custommachinery.impl.component.config.RelativeSide;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

import static fr.frinn.custommachinery.forge.transfer.ForgeFluidHandler.autoInput;
import static fr.frinn.custommachinery.forge.transfer.ForgeFluidHandler.autoOutput;


public class ForgeRemoteFluidHandler implements ICommonFluidHandler {

    private final RemoteFluidComponentHandler remoteFluidHandler;
    private final CustomMachineTile tile;

    private FluidComponentHandler innerFluidHandler;

    private boolean initialized = false;

    public ForgeRemoteFluidHandler(RemoteFluidComponentHandler handler) {
        this.remoteFluidHandler = handler;
        this.tile = (CustomMachineTile) handler.getManager().getTile();
    }

    public boolean init() {
        innerFluidHandler = (FluidComponentHandler) remoteFluidHandler.getManager()
                .getComponentHandler(Registration.FLUID_MACHINE_COMPONENT.get()).orElse(null);
        if (innerFluidHandler == null)
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
        if (!tile.shouldAutoIO())
            return;

        BlockPos tilePos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null || level.isClientSide())
            return;

        if (!initialized && !init())
            return;

        for (var remote: remoteFluidHandler.getComponents()) {
            Direction d = tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);

            BlockEntity be = level.getBlockEntity(Utils.rotatePos(remote.getPos(), d).offset(tilePos));
            if (be == null)
                continue;

            LazyOptional<IFluidHandler> handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER);

            handler.ifPresent(tank -> {
                List<FluidMachineComponent> inputCandidate = innerFluidHandler.getComponents().stream().filter(
                        component -> remote.isSlotValid(component.getId())
                                && component.getRemainingSpace() > 0)
                        .toList();
                autoInput(inputCandidate, tank, fluid -> remote.isFluidValid(FluidStackHooksForge.fromForge(fluid)));

                List<FluidMachineComponent> outputCandidate = innerFluidHandler.getComponents().stream().filter(
                        component -> remote.isSlotValid(component.getId())
                                && component.getFluidStack().getAmount() > 0
                                && component.getMaxOutput() > 0)
                        .toList();
                autoOutput(outputCandidate, tank, fluid -> remote.isFluidValid(FluidStackHooksForge.fromForge(fluid)));
            });
        }
    }

    @Override
    public boolean interactWithFluidHandler(Player player, InteractionHand hand) {
        return false;
    }
}
