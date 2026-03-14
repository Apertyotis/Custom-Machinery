package fr.frinn.custommachinery.forge.transfer;

import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import fr.frinn.custommachinery.common.component.FluidMachineComponent;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.util.Utils;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

class InteractionFluidStorage implements IFluidHandler {

    private final FluidComponentHandler handler;

    public InteractionFluidStorage(FluidComponentHandler handler) {
        this.handler = handler;
    }

    @Override
    public int getTanks() {
        return this.handler.getComponents().size();
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStackHooksForge.toForge(this.handler.getComponents().get(tank).getFluidStack());
    }

    @Override
    public int getTankCapacity(int tank) {
        return Utils.toInt(this.handler.getComponents().get(tank).getCapacity());
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return this.handler.getComponents().get(tank).isFluidValid(FluidStackHooksForge.fromForge(stack));
    }

    @Override
    public int fill(FluidStack forgeStack, FluidAction action) {
        int remaining = forgeStack.getAmount();

        List<FluidMachineComponent> list = handler.getFluidMap().get(forgeStack.getFluid());
        if (list != null) {
            for (FluidMachineComponent component: list) {
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
        for (FluidMachineComponent component: handler.getComponents()) {
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

    @Nonnull
    @Override
    public FluidStack drain(FluidStack maxDrain, FluidAction action) {
        int remainingToDrain = maxDrain.getAmount();

        List<FluidMachineComponent> list = handler.getFluidMap().get(maxDrain.getFluid());
        if (list == null)
            return FluidStack.EMPTY;

        for (FluidMachineComponent component: list) {
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

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        int remainingToDrain = maxDrain;
        Map<Fluid, List<FluidMachineComponent>> map = handler.getFluidMap();

        for (var entry: map.entrySet()) {
            List<FluidMachineComponent> list = entry.getValue();
            FluidStack toDrain = FluidStack.EMPTY;
            for (var component: list) {
                if (component.getFluidStack().isEmpty() || !component.getMode().isOutput())
                    continue;
                if (toDrain.isEmpty() || toDrain.isFluidEqual(FluidStackHooksForge.toForge(component.getFluidStack()))) {
                    FluidStack extracted = FluidStackHooksForge.toForge(component.extract(remainingToDrain, action.simulate()));
                    remainingToDrain -= extracted.getAmount();
                    if (toDrain.isEmpty())
                        toDrain = extracted;
                    if (remainingToDrain <= 0)
                        break;
                }
            }
            if (!toDrain.isEmpty())
                return new FluidStack(toDrain.getFluid(), maxDrain - remainingToDrain, toDrain.getTag());
        }
        return FluidStack.EMPTY;
    }
}
