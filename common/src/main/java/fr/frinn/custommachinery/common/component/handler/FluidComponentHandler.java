package fr.frinn.custommachinery.common.component.handler;

import dev.architectury.fluid.FluidStack;
import fr.frinn.custommachinery.PlatformHelper;
import fr.frinn.custommachinery.api.component.IDumpComponent;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.ISerializableComponent;
import fr.frinn.custommachinery.api.component.ITickableComponent;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.network.ISyncable;
import fr.frinn.custommachinery.api.network.ISyncableStuff;
import fr.frinn.custommachinery.common.component.FluidMachineComponent;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.common.util.transfer.ICommonFluidHandler;
import fr.frinn.custommachinery.impl.component.AbstractComponentHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class FluidComponentHandler extends AbstractComponentHandler<FluidMachineComponent> implements ISerializableComponent, ISyncableStuff, ITickableComponent, IDumpComponent {

    private final ICommonFluidHandler handler = PlatformHelper.createFluidHandler(this);

    private final Map<Fluid, List<FluidMachineComponent>> fluidMap = new LinkedHashMap<>();
    private final Map<String, FluidMachineComponent> componentMap = new HashMap<>();
    private boolean dirty = true;

    public FluidComponentHandler(IMachineComponentManager manager, List<FluidMachineComponent> components) {
        super(manager, components);
        components.forEach(component -> {
            component.getConfig().setCallback(this.handler::configChanged);
            if(component.getMode().isInput())
                this.inputs.add(component);
            if(component.getMode().isOutput())
                this.outputs.add(component);
            componentMap.put(component.getId(), component);
        });
    }

    public ICommonFluidHandler getCommonFluidHandler() {
        return this.handler;
    }

    public void markDirty() {
        dirty = true;
    }

    public Map<Fluid, List<FluidMachineComponent>> getFluidMap() {
        if (!dirty)
            return fluidMap;
        dirty = false;
        fluidMap.clear();
        for (FluidMachineComponent component: getComponents()) {
            fluidMap.computeIfAbsent(component.getFluidStack().getFluid(), key -> new ArrayList<>())
                    .add(component);
        }
        return fluidMap;
    }

    @Override
    public MachineComponentType<FluidMachineComponent> getType() {
        return Registration.FLUID_MACHINE_COMPONENT.get();
    }

    @Override
    public Optional<FluidMachineComponent> getComponentForID(String id) {
        return Optional.ofNullable(componentMap.get(id));
    }

    @Override
    public void onRemoved() {
        this.handler.invalidate();
    }

    @Override
    public void serverTick() {
        this.handler.tick();
    }

    @Override
    public void serialize(CompoundTag nbt) {
        ListTag componentsNBT = new ListTag();
        this.getComponents().forEach(component -> {
            CompoundTag componentNBT = new CompoundTag();
            component.serialize(componentNBT);
            componentNBT.putString("id", component.getId());
            componentsNBT.add(componentNBT);
        });
        nbt.put("fluids", componentsNBT);
    }

    @Override
    public void deserialize(CompoundTag nbt) {
        if(nbt.contains("fluids", Tag.TAG_LIST)) {
            ListTag componentsNBT = nbt.getList("fluids", Tag.TAG_COMPOUND);
            for (Tag inbt: componentsNBT) {
                if (inbt instanceof CompoundTag compoundTag) {
                    getComponentForID(compoundTag.getString("id"))
                            .ifPresent(component -> component.deserialize(compoundTag));
                }
            }
        }
    }

    @Override
    public void getStuffToSync(Consumer<ISyncable<?, ?>> container) {
        this.getComponents().forEach(component -> component.getStuffToSync(container));
    }

    @Override
    public void dump(List<String> ids) {
        this.getComponents().stream()
                .filter(component -> ids.contains(component.getId()))
                .forEach(component -> component.setFluidStack(FluidStack.empty()));
    }

    public long fill(FluidStack toInsert, boolean simulate) {
        long amount = toInsert.getAmount();

        List<FluidMachineComponent> list = getFluidMap().get(toInsert.getFluid());
        if (list != null) {
            for (var component: list) {
                if (amount <= 0)
                    return toInsert.getAmount();

                FluidStack stack = component.getFluidStack();
                if (component.getMode().isInput() && stack.isFluidEqual(toInsert) && stack.isTagEqual(toInsert)) {
                    amount -= component.insert(toInsert.getFluid(), amount, toInsert.getTag(), simulate);
                }
            }
        }
        for (var component: this.inputs) {
            if (amount <= 0)
                return toInsert.getAmount();
            if (!component.getFluidStack().isEmpty())
                continue;
            if (component.isFluidValid(toInsert)) {
                amount -= component.insert(toInsert.getFluid(), amount, toInsert.getTag(), simulate);
            }
        }
        return toInsert.getAmount() - amount;
    }

    public FluidStack drain(FluidStack maxDrain, boolean simulate) {
        long remainingToDrain = maxDrain.getAmount();

        for (FluidMachineComponent component : this.outputs) {
            FluidStack stack = component.getFluidStack();
            if(!stack.isEmpty() && stack.isFluidEqual(maxDrain)) {
                FluidStack extracted = component.extract(maxDrain.getAmount(), true);
                if(extracted.getAmount() >= remainingToDrain) {
                    if(!simulate)
                        component.extract(remainingToDrain, false);
                    return maxDrain;
                } else {
                    if(!simulate)
                        component.extract(extracted.getAmount(), false);
                    remainingToDrain -= extracted.getAmount();
                }
            }
        }
        if(remainingToDrain == maxDrain.getAmount())
            return FluidStack.empty();
        else
            return FluidStack.create(maxDrain.getFluid(), maxDrain.getAmount() - remainingToDrain, maxDrain.getTag());
    }

    /** RECIPE STUFF **/

    private final List<FluidMachineComponent> inputs = new ArrayList<>();
    private final List<FluidMachineComponent> outputs = new ArrayList<>();

    public long getFluidAmount(String tank, IIngredient<Fluid> ingredients, @Nullable CompoundTag nbt) {
        List<Fluid> fluids = ingredients.getAll();
        long count = 0;
        for (var component: this.inputs) {
            if (!tank.isEmpty() && !component.getId().equals(tank))
                continue;
            FluidStack stack = component.getFluidStack();
            for (Fluid fluid: fluids) {
                if (stack.getFluid() == fluid) {
                    CompoundTag toTested = stack.getTag();
                    if (nbt == null || nbt.isEmpty() || (toTested != null && Utils.testNBT(toTested, nbt))) {
                        count += stack.getAmount();
                    }
                    break;
                }
            }
        }
        return count;
    }

    public long getSpaceForFluid(String tank, Fluid fluid, @Nullable CompoundTag nbt) {
        long count = 0;
        for (var component: this.outputs) {
            if (!tank.isEmpty() && !component.getId().equals(tank))
                continue;
            if (component.isFluidValid(fluid, nbt))
                count += component.getRecipeRemainingSpace();
        }
        return count;
    }

    public void removeFromInputs(String tank, IIngredient<Fluid> ingredients, long amount, @Nullable CompoundTag nbt) {
        for (var component: this.inputs) {
            if (amount <= 0)
                break;
            if (!tank.isEmpty() && !component.getId().equals(tank))
                continue;
            FluidStack stack = component.getFluidStack();
            if (ingredients.test(stack.getFluid()) && Objects.equals(stack.getTag(), nbt)) {
                long maxExtract = Math.min(stack.getAmount(), amount);
                amount -= maxExtract;
                component.recipeExtract(maxExtract);
            }
        }
    }

    public void addToOutputs(String tank, FluidStack toInsert) {
        long amount = toInsert.getAmount();
        List<FluidMachineComponent> list = getFluidMap().get(toInsert.getFluid());
        if (list != null) {
            for (var component: list) {
                if (amount <= 0)
                    return;
                if (!tank.isEmpty() && !component.getId().equals(tank))
                    continue;
                FluidStack stack = component.getFluidStack();
                if (component.getMode().isOutput() && stack.isFluidEqual(toInsert) && stack.isTagEqual(toInsert)) {
                    long maxInsert = Math.min(component.getRecipeRemainingSpace(), amount);
                    amount -= maxInsert;
                    component.recipeInsert(toInsert.getFluid(), maxInsert, toInsert.getTag());
                }
            }
        }
        for (var component: this.outputs) {
            if (amount <= 0)
                return;
            if (!tank.isEmpty() && !component.getId().equals(tank))
                continue;
            if (!component.getFluidStack().isEmpty())
                continue;
            if (component.isFluidValid(toInsert)) {
                long maxInsert = Math.min(component.getRecipeRemainingSpace(), amount);
                amount -= maxInsert;
                component.recipeInsert(toInsert.getFluid(), maxInsert, toInsert.getTag());
            }
        }
    }
}
