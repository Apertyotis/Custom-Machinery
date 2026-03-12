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

public class RemoteFluidComponent extends AbstractMachineComponent {

    private final BlockPos pos;

    public RemoteFluidComponent(IMachineComponentManager manager, BlockPos pos, ComponentIOMode mode) {
        super(manager, mode);
        this.pos = pos;
    }

    @Override
    public MachineComponentType<RemoteFluidComponent> getType() {
        return Registration.REMOTE_FLUID_COMPONENT.get();
    }

    public BlockPos getPos() {
        return pos;
    }

    public record Template(BlockPos pos,
                           ComponentIOMode mode
    ) implements IMachineComponentTemplate<RemoteFluidComponent> {

        public static final NamedCodec<RemoteFluidComponent.Template> CODEC = NamedCodec.record(remoteFluidComponentTemplate ->
                        remoteFluidComponentTemplate.group(
                                DefaultCodecs.POS.fieldOf("pos").forGetter(template -> template.pos),
                                ComponentIOMode.CODEC.optionalFieldOf("mode", ComponentIOMode.BOTH).forGetter(template -> template.mode)
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
            return new RemoteFluidComponent(manager, this.pos, this.mode);
        }
    }
}
