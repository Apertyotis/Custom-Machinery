package fr.frinn.custommachinery.common.component.handler;

import fr.frinn.custommachinery.PlatformHelper;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.ITickableComponent;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.component.RemoteEnergyComponent;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.transfer.ICommonEnergyHandler;
import fr.frinn.custommachinery.impl.component.AbstractComponentHandler;

import java.util.List;
import java.util.Optional;

public class RemoteEnergyComponentHandler extends AbstractComponentHandler<RemoteEnergyComponent> implements ITickableComponent {

    private final ICommonEnergyHandler handler = PlatformHelper.createRemoteEnergyHandler(this);

    public RemoteEnergyComponentHandler(IMachineComponentManager manager, List<RemoteEnergyComponent> components) {
        super(manager, components);
    }

    @Override
    public MachineComponentType<RemoteEnergyComponent> getType() {
        return Registration.REMOTE_ENERGY_COMPONENT.get();
    }

    @Override
    public void onRemoved() {
        this.handler.invalidate();
    }

    @Override
    public Optional<RemoteEnergyComponent> getComponentForID(String id) {
        return Optional.empty();
    }

    @Override
    public void serverTick() {
        this.handler.tick();
    }
}
