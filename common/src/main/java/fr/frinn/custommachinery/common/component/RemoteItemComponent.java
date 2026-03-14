package fr.frinn.custommachinery.common.component;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.ComponentIOMode;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.IMachineComponentTemplate;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.component.AbstractMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class RemoteItemComponent extends AbstractMachineComponent{

    private final BlockPos pos;
    private final Set<String> slotID;
    private final List<IIngredient<Item>> filter;
    private final boolean whitelist;

    public RemoteItemComponent(IMachineComponentManager manager, BlockPos pos, ComponentIOMode mode, List<String> slotID, List<IIngredient<Item>> filter, boolean whitelist) {
        super(manager, mode);
        this.pos = pos;
        this.slotID = new HashSet<>(slotID);
        this.filter = filter;
        this.whitelist = whitelist;
    }

    @Override
    public MachineComponentType<RemoteItemComponent> getType() {
        return Registration.REMOTE_ITEM_COMPONENT.get();
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isSlotValid(String id) {
        return slotID.contains(id);
    }

    public boolean isItemValid(ItemStack stack) {
        return filter.stream().anyMatch(ingredient -> ingredient.test(stack.getItem())) == whitelist;
    }

    public record Template(BlockPos pos,
                           ComponentIOMode mode,
                           List<String> slotID,
                           List<IIngredient<Item>> filter,
                           boolean whitelist
    ) implements IMachineComponentTemplate<RemoteItemComponent> {

        public static final NamedCodec<RemoteItemComponent.Template> CODEC = NamedCodec.record(remoteItemComponentTemplate ->
                remoteItemComponentTemplate.group(
                        DefaultCodecs.POS.fieldOf("pos").forGetter(template -> template.pos),
                        ComponentIOMode.CODEC.optionalFieldOf("mode", ComponentIOMode.BOTH).forGetter(template -> template.mode),
                        NamedCodec.list(NamedCodec.STRING).fieldOf("slotID").forGetter(template -> template.slotID),
                        IIngredient.ITEM.listOf().optionalFieldOf("filter", Collections.emptyList()).forGetter(template -> template.filter),
                        NamedCodec.BOOL.optionalFieldOf("whitelist", false).forGetter(template -> template.whitelist)
                ).apply(remoteItemComponentTemplate, Template::new),
                "Remote item component"
        );

        @Override
        public MachineComponentType<RemoteItemComponent> getType() {
            return Registration.REMOTE_ITEM_COMPONENT.get();
        }

        @Override
        public String getId() {
            return "";
        }

        @Override
        public boolean canAccept(Object ingredient, boolean isInput, IMachineComponentManager manager) {
            return false;
        }

        @Override
        public RemoteItemComponent build(IMachineComponentManager manager) {
            return new RemoteItemComponent(manager, this.pos, this.mode, this.slotID, this.filter, this.whitelist);
        }
    }
}
