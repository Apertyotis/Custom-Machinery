package fr.frinn.custommachinery.forge.transfer;

import com.google.common.collect.Maps;
import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import fr.frinn.custommachinery.common.component.FluidMachineComponent;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.util.transfer.ICommonFluidHandler;
import fr.frinn.custommachinery.impl.component.config.RelativeSide;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.Map;

public class ForgeFluidHandler implements ICommonFluidHandler {

    private final FluidComponentHandler fluidHandler;

    private final IFluidHandler generalHandler;
    private final LazyOptional<IFluidHandler> capability;
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedWrappers = Maps.newEnumMap(Direction.class);
    private final Map<Direction, BlockEntity> neighbourStorages = Maps.newEnumMap(Direction.class);
    private final InteractionFluidStorage interactionFluidStorage;

    public ForgeFluidHandler(FluidComponentHandler fluidHandler) {
        this.fluidHandler = fluidHandler;
        this.generalHandler = new SidedFluidStorage(null, fluidHandler);
        this.capability = LazyOptional.of(() -> this.generalHandler);
        for(Direction direction : Direction.values()) {
            SidedFluidStorage storage = new SidedFluidStorage(direction, fluidHandler);
            this.sidedWrappers.put(direction, LazyOptional.of(() -> storage));
        }
        this.interactionFluidStorage = new InteractionFluidStorage(this.fluidHandler);
    }

    public <T> LazyOptional<T> getCapability(@Nullable Direction side) {
        if(side == null)
            return this.capability.cast();
        else if(this.fluidHandler.getComponents().stream().anyMatch(component -> !component.getConfig().getSideMode(side).isNone()))
            return this.sidedWrappers.get(side).cast();
        else
            return LazyOptional.empty();
    }

    @Override
    public void configChanged(RelativeSide side, SideMode oldMode, SideMode newMode) {
        if(oldMode.isNone() != newMode.isNone())
            this.fluidHandler.getManager().getLevel().updateNeighborsAt(this.fluidHandler.getManager().getTile().getBlockPos(), this.fluidHandler.getManager().getTile().getBlockState().getBlock());
    }

    @Override
    public void invalidate() {
        this.capability.invalidate();
        this.sidedWrappers.values().forEach(LazyOptional::invalidate);
    }

    @Override
    public void tick() {
        if (!((CustomMachineTile) this.fluidHandler.getManager().getTile()).shouldAutoIO())
            return;

        //I/O between the machine and neighbour blocks.
        for(Direction side : Direction.values()) {
            if(this.fluidHandler.getComponents().stream().allMatch(component -> component.getConfig().getSideMode(side) == SideMode.NONE))
                continue;

            LazyOptional<IFluidHandler> neighbour;

            if(this.neighbourStorages.get(side) == null || this.neighbourStorages.get(side).isRemoved()) {
                this.neighbourStorages.put(side, this.fluidHandler.getManager().getLevel().getBlockEntity(this.fluidHandler.getManager().getTile().getBlockPos().relative(side)));
                if(this.neighbourStorages.get(side) != null)
                    neighbour = this.neighbourStorages.get(side).getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite());
                else
                    continue;
            }
            else
                neighbour = this.neighbourStorages.get(side).getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite());

            neighbour.ifPresent(tank -> this.fluidHandler.getComponents().forEach(component -> {
                if (component.getConfig().isAutoInput() && component.getConfig().getSideMode(side).isInput()
                        && component.getFluidStack().getAmount() < component.getCapacity()
                ) {
                    insertComponent(component, tank);
                }

                if (component.getConfig().isAutoOutput() && component.getConfig().getSideMode(side).isOutput()
                        && component.getFluidStack().getAmount() > 0
                ) {
                    extractComponent(component, tank);
                }
            }));
        }
    }

    /** Right click with fluid handler compatibility **/
    @Override
    public boolean interactWithFluidHandler(Player player, InteractionHand hand) {
        return FluidUtil.interactWithFluidHandler(player, hand, this.interactionFluidStorage);
    }

    public static void insertComponent(FluidMachineComponent component, IFluidHandler tank) {
        FluidStack tryExtract = tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!tryExtract.isEmpty() && component.isFluidValid(FluidStackHooksForge.fromForge(tryExtract))) {
            long filled = component.insert(tryExtract.getFluid(), tryExtract.getAmount(), tryExtract.getTag(), false);
            if (filled > 0) {
                FluidStack extracted = tryExtract.copy();
                extracted.setAmount((int) filled);
                tank.drain(extracted, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public static void extractComponent(FluidMachineComponent component, IFluidHandler tank) {
        dev.architectury.fluid.FluidStack tryExtract = component.extract(Integer.MAX_VALUE, true);
        int filled = tank.fill(FluidStackHooksForge.toForge(tryExtract), IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            component.extract(filled, false);
        }
    }
}
