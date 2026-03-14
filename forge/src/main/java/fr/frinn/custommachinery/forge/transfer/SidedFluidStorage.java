package fr.frinn.custommachinery.forge.transfer;

import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import fr.frinn.custommachinery.common.component.FluidMachineComponent;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class SidedFluidStorage implements IFluidHandler {

    @Nullable
    private final Direction direction;
    private final FluidComponentHandler handler;

    public SidedFluidStorage(@Nullable Direction direction, FluidComponentHandler handler) {
        this.direction = direction;
        this.handler = handler;
    }

    public List<FluidMachineComponent> getSideComponents(Predicate<SideMode> filter) {
        if(this.direction == null)
            return this.handler.getComponents();
        return this.handler.getComponents().stream().filter(component -> filter.test(component.getConfig().getSideMode(this.direction))).toList();
    }

    @Override
    public int getTanks() {
        return this.handler.getComponents().size();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStackHooksForge.toForge(this.handler.getComponents().get(tank).getFluidStack());
    }

    @Override
    public int getTankCapacity(int tank) {
        return Utils.toInt(this.handler.getComponents().get(tank).getCapacity());
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return this.handler.getComponents().get(tank).isFluidValid(FluidStackHooksForge.fromForge(stack));
    }

    @Override
    public int fill(FluidStack forgeStack, FluidAction action) {
        int remaining = forgeStack.getAmount();

        List<FluidMachineComponent> list = handler.getFluidMap().get(forgeStack.getFluid());
        if (list != null) {
            for (FluidMachineComponent component: list) {
                if (direction != null && !component.getConfig().getSideMode(direction).isInput())
                    continue;
                if (!component.getMode().isInput())
                    continue;
                FluidStack fluid = FluidStackHooksForge.toForge(component.getFluidStack());
                if (!fluid.isEmpty() && fluid.isFluidEqual(forgeStack)) {
                    remaining -= (int) component.insert(
                            forgeStack.getFluid(), remaining, forgeStack.getTag(), action.simulate()
                    );
                    if (remaining <= 0)
                        return forgeStack.getAmount();
                }
            }
        }

        dev.architectury.fluid.FluidStack archStack = FluidStackHooksForge.fromForge(forgeStack);
        for (FluidMachineComponent component: getSideComponents(SideMode::isInput)) {
            if (!component.getFluidStack().isEmpty() || !component.getMode().isInput())
                continue;
            if (!component.isFluidValid(archStack))
                continue;
            remaining -= (int) component.insert(forgeStack.getFluid(), remaining, forgeStack.getTag(), action.simulate());
            if (remaining <= 0)
                return forgeStack.getAmount();
        }

        return forgeStack.getAmount() - remaining;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack maxDrain, FluidAction action) {
        int remainingToDrain = maxDrain.getAmount();

        List<FluidMachineComponent> list = handler.getFluidMap().get(maxDrain.getFluid());
        if (list == null)
            return FluidStack.EMPTY;

        for (FluidMachineComponent component: list) {
            if (direction != null && !component.getConfig().getSideMode(this.direction).isOutput())
                continue;
            if (!component.getMode().isOutput())
                continue;
            FluidStack fluidStack = FluidStackHooksForge.toForge(component.getFluidStack());
            if (!fluidStack.isEmpty() && fluidStack.isFluidEqual(maxDrain)) {
                int extracted = (int) component.extract(remainingToDrain, action.simulate()).getAmount();
                remainingToDrain -= extracted;
            }
            if (remainingToDrain <= 0)
                break;
        }
        return new FluidStack(maxDrain.getFluid(), maxDrain.getAmount() - remainingToDrain, maxDrain.getTag());
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack toDrain = FluidStack.EMPTY;
        int remainingToDrain = maxDrain;
        for (FluidMachineComponent component : this.getSideComponents(SideMode::isOutput)) {
            if (!component.getMode().isOutput())
                continue;
            if(!component.getFluidStack().isEmpty() &&
                    (toDrain.isEmpty() || toDrain.isFluidEqual(FluidStackHooksForge.toForge(component.getFluidStack())))
            ) {
                FluidStack extracted = FluidStackHooksForge.toForge(
                        component.extract(remainingToDrain, action.simulate())
                );
                if (toDrain.isEmpty())
                    toDrain = extracted;
                remainingToDrain -= extracted.getAmount();
                if (remainingToDrain <= 0)
                    break;
            }
        }
        return new FluidStack(toDrain.getFluid(), maxDrain - remainingToDrain, toDrain.getTag());
    }
}
