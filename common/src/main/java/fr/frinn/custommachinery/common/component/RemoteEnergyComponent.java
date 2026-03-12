package fr.frinn.custommachinery.common.component;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.ComponentIOMode;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.IMachineComponentTemplate;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.component.AbstractMachineComponent;
import net.minecraft.core.BlockPos;

public class RemoteEnergyComponent extends AbstractMachineComponent {

    private final BlockPos pos;

    public RemoteEnergyComponent(IMachineComponentManager manager, BlockPos pos, ComponentIOMode mode) {
        super(manager, mode);
        this.pos = pos;
    }

    @Override
    public MachineComponentType<RemoteEnergyComponent> getType() {
        return Registration.REMOTE_ENERGY_COMPONENT.get();
    }

    public BlockPos getPos() {
        return pos;
    }

    public record Template(BlockPos pos,
                           ComponentIOMode mode
    ) implements IMachineComponentTemplate<RemoteEnergyComponent> {

        public static final NamedCodec<RemoteEnergyComponent.Template> CODEC = NamedCodec.record(remoteEnergyComponentTemplate ->
                        remoteEnergyComponentTemplate.group(
                                DefaultCodecs.POS.fieldOf("pos").forGetter(template -> template.pos),
                                ComponentIOMode.CODEC.optionalFieldOf("mode", ComponentIOMode.BOTH).forGetter(template -> template.mode)
                        ).apply(remoteEnergyComponentTemplate, RemoteEnergyComponent.Template::new),
                "Remote item component"
        );

        @Override
        public MachineComponentType<RemoteEnergyComponent> getType() {
            return Registration.REMOTE_ENERGY_COMPONENT.get();
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
        public RemoteEnergyComponent build(IMachineComponentManager manager) {
            return new RemoteEnergyComponent(manager, this.pos, this.mode);
        }
    }
}
