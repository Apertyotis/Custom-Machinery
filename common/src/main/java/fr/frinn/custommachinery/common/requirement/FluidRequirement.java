package fr.frinn.custommachinery.common.requirement;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.FluidStackHooks;
import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.crafting.CraftingResult;
import fr.frinn.custommachinery.api.crafting.ICraftingContext;
import fr.frinn.custommachinery.api.crafting.IMachineRecipe;
import fr.frinn.custommachinery.api.integration.jei.IJEIIngredientRequirement;
import fr.frinn.custommachinery.api.integration.jei.IJEIIngredientWrapper;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.client.integration.jei.wrapper.FluidIngredientWrapper;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.ingredient.FluidTagIngredient;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.requirement.AbstractChanceableRequirement;
import fr.frinn.custommachinery.impl.requirement.AbstractRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FluidRequirement extends AbstractChanceableRequirement<FluidComponentHandler> implements IJEIIngredientRequirement<FluidStack> {

    public static final NamedCodec<FluidRequirement> CODEC = NamedCodec.record(fluidRequirementInstance ->
            fluidRequirementInstance.group(
                    RequirementIOMode.CODEC.fieldOf("mode").forGetter(AbstractRequirement::getMode),
                    IIngredient.FLUID.fieldOf("fluid").forGetter(requirement -> requirement.fluid),
                    NamedCodec.LONG.fieldOf("amount").forGetter(requirement -> requirement.amount),
                    NamedCodec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0D).forGetter(AbstractChanceableRequirement::getChance),
                    DefaultCodecs.COMPOUND_TAG.optionalFieldOf("nbt").forGetter(requirement -> Optional.ofNullable(requirement.nbt)),
                    NamedCodec.STRING.optionalFieldOf("tank", "").forGetter(requirement -> requirement.tank)
            ).apply(fluidRequirementInstance, (mode, fluid, amount, chance, nbt, tank) -> {
                    FluidRequirement requirement = new FluidRequirement(mode, fluid, amount, nbt.orElse(null), tank);
                    requirement.setChance(chance);
                    return requirement;
            }), "Fluid requirement"
    );

    private final IIngredient<Fluid> fluid;
    private final long amount;
    @Nullable
    private final CompoundTag nbt;
    private final String tank;

    public FluidRequirement(RequirementIOMode mode, IIngredient<Fluid> fluid, long amount, @Nullable CompoundTag nbt, String tank) {
        super(mode);
        if(mode == RequirementIOMode.OUTPUT && fluid instanceof FluidTagIngredient)
            throw new IllegalArgumentException("You must specify a fluid for an Output Fluid Requirement");
        this.fluid = fluid;
        this.amount = amount;
        this.nbt = nbt;
        this.tank = tank;
    }

    @Override
    public RequirementType<FluidRequirement> getType() {
        return Registration.FLUID_REQUIREMENT.get();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public MachineComponentType getComponentType() {
        return Registration.FLUID_MACHINE_COMPONENT.get();
    }

    @Override
    public boolean test(FluidComponentHandler component, ICraftingContext context) {
        long amount = context.getIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.INPUT) {
            return component.getFluidAmount(tank, fluid, nbt) >= amount;
        } else {
            List<Fluid> fluids = fluid.getAll();
            if (!fluids.isEmpty() && fluids.get(0) != null)
                return component.getSpaceForFluid(tank, fluids.get(0), nbt) >= amount;
            else
                throw new IllegalStateException("Can't use output fluid requirement with fluid tag");
        }
    }

    @Override
    public CraftingResult processStart(FluidComponentHandler component, ICraftingContext context) {
        long amount = context.getIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.INPUT) {
            long maxDrain = component.getFluidAmount(tank, fluid, nbt);
            if (maxDrain >= amount) {
                component.removeFromInputs(this.tank, fluid, amount, nbt);
                return CraftingResult.success();
            } else {
                return CraftingResult.error(Component.translatable("custommachinery.requirements.fluid.error.input", this.fluid, amount, maxDrain));
            }
        }
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processEnd(FluidComponentHandler component, ICraftingContext context) {
        long amount = context.getIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.OUTPUT) {
            List<Fluid> fluids = fluid.getAll();
            if (!fluids.isEmpty() && fluids.get(0) != null) {
                Fluid fluid = fluids.get(0);
                long canFill =  component.getSpaceForFluid(tank, fluid, nbt);
                if (canFill >= amount) {
                    FluidStack stack = FluidStack.create(fluid, amount, nbt);
                    component.addToOutputs(tank, stack);
                    return CraftingResult.success();
                } else {
                    return CraftingResult.error(Component.translatable("custommachinery.requirements.fluid.error.output", amount, FluidStackHooks.getName(FluidStack.create(fluid, this.amount))));
                }
            } else throw new IllegalStateException("Can't use output fluid requirement with fluid tag");
        }
        return CraftingResult.pass();
    }

    @Override
    public List<IJEIIngredientWrapper<FluidStack>> getJEIIngredientWrappers(IMachineRecipe recipe) {
        return Collections.singletonList(new FluidIngredientWrapper(this.getMode(), this.fluid, this.amount, getChance(), false, this.nbt, this.tank));
    }
}
