package fr.frinn.custommachinery.common.component.handler;

import fr.frinn.custommachinery.PlatformHelper;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.ITickableComponent;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.component.RemoteFluidComponent;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.transfer.ICommonFluidHandler;
import fr.frinn.custommachinery.impl.component.AbstractComponentHandler;

import java.util.List;
import java.util.Optional;

public class RemoteFluidComponentHandler extends AbstractComponentHandler<RemoteFluidComponent> implements ITickableComponent {

    private final ICommonFluidHandler handler = PlatformHelper.createRemoteFluidHandler(this);

    public RemoteFluidComponentHandler(IMachineComponentManager manager, List<RemoteFluidComponent> components) {
        super(manager, components);
    }

    @Override
    public MachineComponentType<RemoteFluidComponent> getType() {
        return Registration.REMOTE_FLUID_COMPONENT.get();
    }

    @Override
    public void onRemoved() {
        this.handler.invalidate();
    }

    @Override
    public Optional<RemoteFluidComponent> getComponentForID(String id) {
        return Optional.empty();
    }

    @Override
    public void serverTick() {
        this.handler.tick();
    }
}
