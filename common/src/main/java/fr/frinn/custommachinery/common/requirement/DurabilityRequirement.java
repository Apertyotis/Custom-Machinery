package fr.frinn.custommachinery.common.requirement;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.crafting.CraftingResult;
import fr.frinn.custommachinery.api.crafting.ICraftingContext;
import fr.frinn.custommachinery.api.crafting.IMachineRecipe;
import fr.frinn.custommachinery.api.integration.jei.IJEIIngredientRequirement;
import fr.frinn.custommachinery.api.integration.jei.IJEIIngredientWrapper;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.client.integration.jei.wrapper.ItemIngredientWrapper;
import fr.frinn.custommachinery.common.component.handler.ItemComponentHandler;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.requirement.AbstractChanceableRequirement;
import fr.frinn.custommachinery.impl.requirement.AbstractRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DurabilityRequirement extends AbstractChanceableRequirement<ItemComponentHandler> implements IJEIIngredientRequirement<ItemStack> {

    public static final NamedCodec<DurabilityRequirement> CODEC = NamedCodec.record(durabilityRequirementInstance ->
            durabilityRequirementInstance.group(
                    RequirementIOMode.CODEC.fieldOf("mode").forGetter(AbstractRequirement::getMode),
                    IIngredient.ITEM.fieldOf("item").forGetter(requirement -> requirement.item),
                    NamedCodec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(requirement -> requirement.amount),
                    DefaultCodecs.COMPOUND_TAG.optionalFieldOf("nbt").forGetter(requirement -> Optional.ofNullable(requirement.nbt)),
                    NamedCodec.BOOL.optionalFieldOf("break", false).forGetter(requirement -> requirement.canBreak),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(AbstractChanceableRequirement::getChance),
                    NamedCodec.STRING.optionalFieldOf("slot", "").forGetter(requirement -> requirement.slot)
            ).apply(durabilityRequirementInstance, (mode, item, amount, nbt, canBreak, chance, slot) -> {
                    DurabilityRequirement requirement = new DurabilityRequirement(mode, item, amount, nbt.orElse(null), canBreak, slot);
                    requirement.setChance(chance);
                    return requirement;
            }), "Durability requirement"
    );

    private final IIngredient<Item> item;
    private final int amount;
    private final CompoundTag nbt;
    private final String slot;
    private final boolean canBreak;

    public DurabilityRequirement(RequirementIOMode mode, IIngredient<Item> item, int amount, @Nullable CompoundTag nbt, boolean canBreak, String slot) {
        super(mode);
        this.item = item;
        this.amount = amount;
        this.nbt = nbt;
        this.canBreak = canBreak;
        this.slot = slot;
    }

    @Override
    public RequirementType<DurabilityRequirement> getType() {
        return Registration.DURABILITY_REQUIREMENT.get();
    }

    @Override
    public boolean test(ItemComponentHandler component, ICraftingContext context) {
        int amount = (int) context.getIntegerModifiedValue(this.amount, this, null);
        if(getMode() == RequirementIOMode.INPUT)
            return component.getDurabilityAmount(slot, item, nbt) >= amount;
        else
            return component.getSpaceForDurability(slot, item, nbt) >= amount;
    }

    @Override
    public CraftingResult processStart(ItemComponentHandler component, ICraftingContext context) {
        int amount = (int) context.getIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.INPUT) {
            int maxRemove = component.getDurabilityAmount(slot, item, nbt);
            if (maxRemove >= amount) {
                component.removeDurability(slot, item, amount, nbt, canBreak);
                return CraftingResult.success();
            } else {
                return CraftingResult.error(Component.translatable("custommachinery.requirements.durability.error.input", this.item, amount, maxRemove));
            }
        }
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processEnd(ItemComponentHandler component, ICraftingContext context) {
        int amount = (int) context.getIntegerModifiedValue(this.amount, this, null);
        if (getMode() == RequirementIOMode.OUTPUT) {
            int maxRepair = component.getSpaceForDurability(slot, item, nbt);
            if (maxRepair >= amount) {
                component.repairItem(slot, item, amount, nbt);
                return CraftingResult.success();
            } else {
                return CraftingResult.error(Component.translatable("custommachinery.requirements.durability.error.output", this.item, amount, maxRepair));
            }
        }
        return CraftingResult.pass();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public MachineComponentType getComponentType() {
        return Registration.ITEM_MACHINE_COMPONENT.get();
    }

    @Override
    public List<IJEIIngredientWrapper<ItemStack>> getJEIIngredientWrappers(IMachineRecipe recipe) {
        return Collections.singletonList(new ItemIngredientWrapper(this.getMode(), this.item, this.amount, getChance(), true, this.nbt, this.slot, true));
    }
}
