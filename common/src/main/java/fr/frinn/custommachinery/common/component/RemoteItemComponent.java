package fr.frinn.custommachinery.common.component;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.*;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.component.AbstractMachineComponent;
import net.minecraft.core.BlockPos;


public class RemoteItemComponent extends AbstractMachineComponent{

    private final BlockPos pos;

    public RemoteItemComponent(IMachineComponentManager manager, BlockPos pos, ComponentIOMode mode) {
        super(manager, mode);
        this.pos = pos;
    }

    @Override
    public MachineComponentType<RemoteItemComponent> getType() {
        return Registration.REMOTE_ITEM_COMPONENT.get();
    }

    public BlockPos getPos() {
        return pos;
    }

    public record Template(BlockPos pos,
                           ComponentIOMode mode
    ) implements IMachineComponentTemplate<RemoteItemComponent> {

        public static final NamedCodec<RemoteItemComponent.Template> CODEC = NamedCodec.record(remoteItemComponentTemplate ->
                remoteItemComponentTemplate.group(
                        DefaultCodecs.POS.fieldOf("pos").forGetter(template -> template.pos),
                        ComponentIOMode.CODEC.optionalFieldOf("mode", ComponentIOMode.BOTH).forGetter(template -> template.mode)
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
            return new RemoteItemComponent(manager, this.pos, this.mode);
        }
    }
}
