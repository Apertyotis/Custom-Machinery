package fr.frinn.custommachinery.common.component.handler;

import fr.frinn.custommachinery.PlatformHelper;
import fr.frinn.custommachinery.api.component.*;
import fr.frinn.custommachinery.common.component.RemoteItemComponent;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.transfer.ICommonItemHandler;
import fr.frinn.custommachinery.impl.component.AbstractComponentHandler;

import java.util.List;
import java.util.Optional;

public class RemoteItemComponentHandler extends AbstractComponentHandler<RemoteItemComponent> implements ITickableComponent {

    private final ICommonItemHandler handler = PlatformHelper.createRemoteItemHandler(this);

    public RemoteItemComponentHandler(IMachineComponentManager manager, List<RemoteItemComponent> components) {
        super(manager, components);
    }

    @Override
    public MachineComponentType<RemoteItemComponent> getType() {
        return Registration.REMOTE_ITEM_COMPONENT.get();
    }

    @Override
    public void onRemoved() {
        this.handler.invalidate();
    }

    @Override
    public Optional<RemoteItemComponent> getComponentForID(String id) {
        return Optional.empty();
    }

    @Override
    public void serverTick() {
        this.handler.tick();
    }
}
