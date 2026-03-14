package fr.frinn.custommachinery.common.component;

import dev.architectury.fluid.FluidStack;
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
import net.minecraft.world.level.material.Fluid;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoteFluidComponent extends AbstractMachineComponent {

    private final BlockPos pos;
    private final Set<String> slotID;
    private final List<IIngredient<Fluid>> filter;
    private final boolean whitelist;

    public RemoteFluidComponent(
            IMachineComponentManager manager, BlockPos pos, ComponentIOMode mode,
            List<String> slotID, List<IIngredient<Fluid>> filter, boolean whitelist
    ) {
        super(manager, mode);
        this.pos = pos;
        this.slotID = new HashSet<>(slotID);
        this.filter = filter;
        this.whitelist = whitelist;
    }

    @Override
    public MachineComponentType<RemoteFluidComponent> getType() {
        return Registration.REMOTE_FLUID_COMPONENT.get();
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isSlotValid(String id) {
        return slotID.contains(id);
    }

    public boolean isFluidValid(FluidStack stack) {
        return filter.stream().anyMatch(ingredient -> ingredient.test(stack.getFluid())) == whitelist;
    }

    public record Template(BlockPos pos,
                           ComponentIOMode mode,
                           List<String> slotID,
                           List<IIngredient<Fluid>> filter,
                           boolean whitelist
    ) implements IMachineComponentTemplate<RemoteFluidComponent> {
        public static final NamedCodec<RemoteFluidComponent.Template> CODEC = NamedCodec.record(remoteFluidComponentTemplate ->
                        remoteFluidComponentTemplate.group(
                                DefaultCodecs.POS.fieldOf("pos").forGetter(template -> template.pos),
                                ComponentIOMode.CODEC.optionalFieldOf("mode", ComponentIOMode.BOTH).forGetter(template -> template.mode),
                                NamedCodec.list(NamedCodec.STRING).fieldOf("slotID").forGetter(template -> template.slotID),
                                IIngredient.FLUID.listOf().optionalFieldOf("filter", Collections.emptyList()).forGetter(template -> template.filter),
                                NamedCodec.BOOL.optionalFieldOf("whitelist", false).forGetter(template -> template.whitelist)
                        ).apply(remoteFluidComponentTemplate, RemoteFluidComponent.Template::new),
                "Remote fluid component"
        );

        @Override
        public MachineComponentType<RemoteFluidComponent> getType() {
            return Registration.REMOTE_FLUID_COMPONENT.get();
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
        public RemoteFluidComponent build(IMachineComponentManager manager) {
            return new RemoteFluidComponent(manager, this.pos, this.mode, this.slotID, this.filter, this.whitelist);
        }
    }
}
