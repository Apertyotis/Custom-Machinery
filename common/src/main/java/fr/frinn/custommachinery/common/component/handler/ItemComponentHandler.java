package fr.frinn.custommachinery.common.component.handler;

import fr.frinn.custommachinery.PlatformHelper;
import fr.frinn.custommachinery.api.component.IDumpComponent;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.ISerializableComponent;
import fr.frinn.custommachinery.api.component.ITickableComponent;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.component.variant.ITickableComponentVariant;
import fr.frinn.custommachinery.api.network.ISyncable;
import fr.frinn.custommachinery.api.network.ISyncableStuff;
import fr.frinn.custommachinery.common.component.ItemMachineComponent;
import fr.frinn.custommachinery.common.component.variant.item.FilterItemComponentVariant;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.common.util.transfer.ICommonItemHandler;
import fr.frinn.custommachinery.impl.component.AbstractComponentHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ItemComponentHandler extends AbstractComponentHandler<ItemMachineComponent> implements ISerializableComponent, ITickableComponent, ISyncableStuff, IDumpComponent {

    private final RandomSource rand = RandomSource.create();
    private final List<ItemMachineComponent> tickableVariants;
    private final ICommonItemHandler handler = PlatformHelper.createItemHandler(this);

    public ItemComponentHandler(IMachineComponentManager manager, List<ItemMachineComponent> components) {
        super(manager, components);
        components.forEach(component -> {
            component.getConfig().setCallback(this.handler::configChanged);
            if(component.getVariant() != FilterItemComponentVariant.INSTANCE) {
                if(component.getMode().isInput())
                    this.inputs.add(component);
                if(component.getMode().isOutput())
                    this.outputs.add(component);
            }
        });
        this.tickableVariants = components.stream().filter(component -> component.getVariant() instanceof ITickableComponentVariant).toList();
    }

    public ICommonItemHandler getCommonHandler() {
        return this.handler;
    }

    @Override
    public MachineComponentType<ItemMachineComponent> getType() {
        return Registration.ITEM_MACHINE_COMPONENT.get();
    }

    @Override
    public void onRemoved() {
        this.handler.invalidate();
    }

    @Override
    public Optional<ItemMachineComponent> getComponentForID(String id) {
        return this.getComponents().stream().filter(component -> component.getId().equals(id)).findFirst();
    }

    @Override
    public void serialize(CompoundTag nbt) {
        ListTag components = new ListTag();
        this.getComponents().forEach(component -> {
            CompoundTag componentNBT = new CompoundTag();
            component.serialize(componentNBT);
            componentNBT.putString("slotID", component.getId());
            components.add(componentNBT);
        });
        nbt.put("items", components);
    }

    @Override
    public void deserialize(CompoundTag nbt) {
        if(nbt.contains("items", Tag.TAG_LIST)) {
            ListTag components = nbt.getList("items", Tag.TAG_COMPOUND);
            components.forEach(inbt -> {
                if (inbt instanceof CompoundTag componentNBT) {
                    if(componentNBT.contains("slotID", Tag.TAG_STRING)) {
                        this.getComponents().stream().filter(component -> component.getId().equals(componentNBT.getString("slotID"))).findFirst().ifPresent(component -> component.deserialize(componentNBT));
                    }
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void serverTick() {
        this.handler.tick();
        this.tickableVariants.forEach(component -> ((ITickableComponentVariant<ItemMachineComponent>)component.getVariant()).tick(component));
    }

    @Override
    public void getStuffToSync(Consumer<ISyncable<?, ?>> container) {
        this.getComponents().forEach(component -> component.getStuffToSync(container));
    }

    @Override
    public void dump(List<String> ids) {
        this.getComponents().stream()
                .filter(component -> ids.contains(component.getId()))
                .forEach(component -> component.setItemStack(ItemStack.EMPTY));
    }

    /** RECIPE STUFF **/

    private final List<ItemMachineComponent> inputs = new ArrayList<>();
    private final List<ItemMachineComponent> outputs = new ArrayList<>();

    public int getItemAmount(String slot, Item item, @Nullable CompoundTag nbt) {
        int count = 0;
        for (var component: this.inputs) {
            if (testSlotItem(component, slot, item, nbt))
                count += component.getItemStack().getCount();
        }
        return count;
    }

    public int getItemAmount(String slot, IIngredient<Item> ingredients, @Nullable CompoundTag nbt) {
        List<Item> items = ingredients.getAll();
        int count = 0;
        for (var component: this.inputs) {
            if (testSlotItems(component, slot, items, nbt))
                count += component.getItemStack().getCount();
        }
        return count;
    }

    public int getDurabilityAmount(String slot, IIngredient<Item> ingredients, @Nullable CompoundTag nbt) {
        List<Item> items = ingredients.getAll();
        int count = 0;
        for (var component: this.inputs) {
            ItemStack stack = component.getItemStack();
            if (!stack.isDamageableItem())
                continue;
            if (testSlotItems(component, slot, items, nbt))
                count += stack.getMaxDamage() - stack.getDamageValue();
        }
        return count;
    }

    public int getSpaceForItem(String slot, Item item, @Nullable CompoundTag nbt) {
        ItemStack toInsert = Utils.makeItemStack(item, 1, nbt);
        int maxStackSize = toInsert.getMaxStackSize();
        int count = 0;
        for (var component: this.outputs) {
            if (!canPlaceOutput(component, slot, toInsert))
                continue;
            if (component.getItemStack().isEmpty())
                count += Math.min(component.getCapacity(), maxStackSize);
            else
                count += Math.min(component.getCapacity(), maxStackSize) - component.getItemStack().getCount();
        }
        return count;
    }

    private boolean canPlaceOutput(ItemMachineComponent component, @Nullable String slot, ItemStack stack) {
        //Not the specified slot
        if(slot != null && !slot.isEmpty() && !component.getId().equals(slot))
            return false;

        //Check component filter and variant
        if(!component.isItemValid(stack))
            return false;

        //If the slot is empty, any item can go inside
        if(component.getItemStack().isEmpty())
            return true;

        //If the item present in the slot in not the same item, they won't stack
        if(component.getItemStack().getItem() != stack.getItem())
            return false;

        //Check if the stack present in the slot can accept more items
        if(component.getItemStack().getCount() >= Math.min(stack.getMaxStackSize(), component.getCapacity()))
            return false;

        //Check if both items can be merged, using vanilla method
        return ItemStack.isSameItemSameTags(component.getItemStack(), stack);
    }

    public int getSpaceForDurability(String slot, IIngredient<Item> ingredients, @Nullable CompoundTag nbt) {
        List<Item> items = ingredients.getAll();
        int count = 0;
        for (var component: this.inputs) {
            ItemStack stack = component.getItemStack();
            if (!stack.isDamageableItem())
                continue;
            if (testSlotItems(component, slot, items, nbt))
                count += stack.getDamageValue();
        }
        return count;
    }

    public void removeFromInputs(String slot, Item item, int amount, @Nullable CompoundTag nbt) {
        for (var component: this.inputs) {
            if (amount <= 0)
                break;
            if (testSlotItem(component, slot, item, nbt))
                amount -= component.extract(amount, false, false).getCount();
        }
        getManager().markDirty();
    }

    public void removeFromInputs(String slot, IIngredient<Item> ingredients, int amount, @Nullable CompoundTag nbt) {
        List<Item> items = ingredients.getAll();
        for (var component: this.inputs) {
            if (amount <= 0)
                break;
            if (testSlotItems(component, slot, items, nbt))
                amount -= component.extract(amount, false, false).getCount();
        }
        getManager().markDirty();
    }

    public void removeDurability(String slot, IIngredient<Item> ingredients, int amount, @Nullable CompoundTag nbt, boolean canBreak) {
        List<Item> items = ingredients.getAll();
        for (var component: this.inputs) {
            if (amount <= 0)
                break;
            ItemStack stack = component.getItemStack();
            if (!stack.isDamageableItem())
                continue;
            if (testSlotItems(component, slot, items, nbt)) {
                int toRemove = Math.min(stack.getMaxDamage() - stack.getDamageValue(), amount);
                amount -= toRemove;
                if (stack.hurt(toRemove, rand, null) && canBreak) {
                    stack.shrink(1);
                    stack.setDamageValue(0);
                }
            }
        }
        getManager().markDirty();
    }

    public void addToOutputs(String slot, Item item, int amount, @Nullable CompoundTag nbt) {
        ItemStack toInsert = Utils.makeItemStack(item, amount, nbt);
        for (var component: outputs) {
            if (toInsert.isEmpty())
                return;
            if (canPlaceOutput(component, slot, toInsert)) {
                int inserted = component.insert(toInsert, false, true);
                toInsert.shrink(inserted);
            }
        }
    }

    public void repairItem(String slot, IIngredient<Item> ingredients, int amount, @Nullable CompoundTag nbt) {
        List<Item> items = ingredients.getAll();
        for (var component: this.inputs) {
            if (amount <= 0)
                break;
            ItemStack stack = component.getItemStack();
            if (!stack.isDamageableItem())
                continue;
            if (testSlotItems(component, slot, items, nbt)) {
                int toRepair = Math.min(stack.getDamageValue(), amount);
                amount -= toRepair;
                stack.setDamageValue(stack.getDamageValue() - toRepair);
            }
        }
        getManager().markDirty();
    }

    private boolean testSlotItem(ItemMachineComponent component, String slot, Item item, @Nullable CompoundTag nbt) {
        if (!slot.isEmpty() && !component.getId().equals(slot))
            return false;

        ItemStack stack = component.getItemStack();
        if (!stack.is(item))
            return false;

        CompoundTag beTested = stack.getTag();
        return (nbt == null || nbt.isEmpty() || (beTested != null && Utils.testNBT(beTested, nbt)));
    }

    private boolean testSlotItems(ItemMachineComponent component, String slot, List<Item> items, @Nullable CompoundTag nbt) {
        if (!slot.isEmpty() && !component.getId().equals(slot))
            return false;

        ItemStack stack = component.getItemStack();
        for (Item item: items) {
            if (stack.is(item)) {
                CompoundTag beTested = stack.getTag();
                return nbt == null || nbt.isEmpty() || (beTested != null && Utils.testNBT(beTested, nbt));
            }
        }
        return false;
    }
}