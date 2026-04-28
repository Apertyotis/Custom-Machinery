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
import fr.frinn.custommachinery.api.requirement.IRequirement;
import fr.frinn.custommachinery.api.requirement.ITickableRequirement;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.client.integration.jei.wrapper.FluidIngredientWrapper;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.ingredient.FluidTagIngredient;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.requirement.AbstractChanceableRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FluidPerTickRequirement extends AbstractChanceableRequirement<FluidComponentHandler> implements ITickableRequirement<FluidComponentHandler>, IJEIIngredientRequirement<FluidStack> {

    public static final NamedCodec<FluidPerTickRequirement> CODEC = NamedCodec.record(fluidPerTickRequirementInstance ->
            fluidPerTickRequirementInstance.group(
                    RequirementIOMode.CODEC.fieldOf("mode").forGetter(IRequirement::getMode),
                    IIngredient.FLUID.fieldOf("fluid").forGetter(requirement -> requirement.fluid),
                    NamedCodec.LONG.fieldOf("amount").forGetter(requirement -> requirement.amount),
                    NamedCodec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 1.0D).forGetter(AbstractChanceableRequirement::getChance),
                    DefaultCodecs.COMPOUND_TAG.optionalFieldOf("nbt").forGetter(requirement -> Optional.ofNullable(requirement.nbt)),
                    NamedCodec.STRING.optionalFieldOf("tank", "").forGetter(requirement -> requirement.tank)
            ).apply(fluidPerTickRequirementInstance, (mode, fluid, amount, chance, nbt, tank) -> {
                    FluidPerTickRequirement requirement = new FluidPerTickRequirement(mode, fluid, amount, nbt.orElse(null), tank);
                    requirement.setChance(chance);
                    return requirement;
            }), "Fluid per tick requirement"
    );

    private final IIngredient<Fluid> fluid;
    private final long amount;
    @Nullable
    private final CompoundTag nbt;
    private final String tank;

    public FluidPerTickRequirement(RequirementIOMode mode, IIngredient<Fluid> fluid, long amount, @Nullable CompoundTag nbt, String tank) {
        super(mode);
        if(mode == RequirementIOMode.OUTPUT && fluid instanceof FluidTagIngredient)
            throw new IllegalArgumentException("You can't use a tag for an Output Fluid Per Tick Requirement");
        this.fluid = fluid;
        this.amount = amount;
        this.nbt = nbt;
        this.tank = tank;
    }

    @Override
    public RequirementType<FluidPerTickRequirement> getType() {
        return Registration.FLUID_PER_TICK_REQUIREMENT.get();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public MachineComponentType getComponentType() {
        return Registration.FLUID_MACHINE_COMPONENT.get();
    }

    @Override
    public boolean test(FluidComponentHandler component, ICraftingContext context) {
        long amount = context.getPerTickIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.INPUT)
            return component.getFluidAmount(tank, fluid, nbt) >= amount;
        else {
            List<Fluid> fluids = fluid.getAll();
            if(!fluids.isEmpty() && fluids.get(0) != null)
                return component.getSpaceForFluid(tank, fluids.get(0), nbt) >= amount;
            else
                throw new IllegalStateException("Can't use output fluid per tick requirement with fluid tag");
        }
    }

    @Override
    public CraftingResult processStart(FluidComponentHandler component, ICraftingContext context) {
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processTick(FluidComponentHandler component, ICraftingContext context) {
        long amount = context.getPerTickIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.INPUT) {
            long maxDrain = component.getFluidAmount(tank, fluid, nbt);
            if (maxDrain >= amount) {
                component.removeFromInputs(tank, fluid, amount, nbt);
                return CraftingResult.success();
            } else {
                return CraftingResult.error(Component.translatable("custommachinery.requirements.fluid.error.input", this.fluid, amount, maxDrain));
            }
        } else {
            List<Fluid> fluids = fluid.getAll();
            if (!fluids.isEmpty() && fluids.get(0) != null) {
                Fluid fluid = fluids.get(0);
                FluidStack stack = FluidStack.create(fluid, amount, nbt);
                long canInsert = component.getSpaceForFluid(tank, fluid, nbt);
                if (canInsert >= amount) {
                    component.addToOutputs(tank, stack);
                    return CraftingResult.success();
                } else {
                    return CraftingResult.error(Component.translatable("custommachinery.requirements.fluidpertick.error.output", amount, FluidStackHooks.getName(FluidStack.create(fluid, this.amount))));
                }
            } else throw new IllegalStateException("Can't use fluid per tick requirement with fluid tag");
        }
    }

    @Override
    public CraftingResult processEnd(FluidComponentHandler component, ICraftingContext context) {
        return CraftingResult.pass();
    }

    @Override
    public List<IJEIIngredientWrapper<FluidStack>> getJEIIngredientWrappers(IMachineRecipe recipe) {
        return Collections.singletonList(new FluidIngredientWrapper(this.getMode(), this.fluid, this.amount, getChance(), true, this.nbt, this.tank));
    }
}
