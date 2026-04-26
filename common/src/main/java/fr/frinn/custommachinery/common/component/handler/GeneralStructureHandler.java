package fr.frinn.custommachinery.common.component.handler;

import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.component.GeneralStructureComponent;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.impl.component.AbstractComponentHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GeneralStructureHandler extends AbstractComponentHandler<GeneralStructureComponent> {
    private final Map<String, StructureRecord> records;

    public GeneralStructureHandler(IMachineComponentManager manager, List<GeneralStructureComponent> components) {
        super(manager, components);
        records = new HashMap<>();
        for (var component: components) {
            records.put(component.getId(), new StructureRecord(component));
        }
    }

    @Override
    public Optional<GeneralStructureComponent> getComponentForID(String id) {
        return Optional.ofNullable(records.get(id).component);
    }

    @Override
    public MachineComponentType<?> getType() {
        return Registration.GENERAL_STRUCTURE_COMPONENT.get();
    }

    public boolean checkStructure(String id) {
        StructureRecord entry = records.get(id);
        if (entry == null)
            return false;

        BlockEntity machine = getManager().getTile();
        Level level = getManager().getLevel();
        long current = level.getGameTime();

        if (entry.isExpired(current)) {
            boolean result = entry.component.getStructure().match(level, machine.getBlockPos(),
                    machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
            entry.setValid(current, result);
            return result;
        } else {
            return entry.valid;
        }
    }

    private static class StructureRecord {
        final GeneralStructureComponent component;
        long timestamp;
        int cooldown;
        boolean valid;

        static final int MIN_COOLDOWN = 20;
        static final int MAX_COOLDOWN = 160;

        StructureRecord(GeneralStructureComponent component) {
            this.component = component;
            timestamp = -1;
            cooldown = 20;
            valid = false;
        }

        boolean isExpired(long current) {
            if (timestamp < 0)
                return true;
            return current >= timestamp + cooldown;
        }

        void setValid(long current, boolean valid) {
            this.timestamp = current;
            this.valid = valid;
            if (valid) {
                this.cooldown = Mth.clamp(cooldown * 2, MIN_COOLDOWN, MAX_COOLDOWN);
            } else {
                this.cooldown = MIN_COOLDOWN;
            }
        }
    }
}
