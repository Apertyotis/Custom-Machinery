package fr.frinn.custommachinery.common.requirement;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.crafting.CraftingResult;
import fr.frinn.custommachinery.api.crafting.ICraftingContext;
import fr.frinn.custommachinery.api.requirement.IRequirement;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.common.component.handler.GeneralStructureHandler;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.impl.requirement.AbstractRequirement;

public class GeneralStructureRequirement extends AbstractRequirement<GeneralStructureHandler> {
    public static final NamedCodec<GeneralStructureRequirement> CODEC = NamedCodec.record(instance ->
            instance.group(
                    NamedCodec.STRING.fieldOf("id").forGetter(GeneralStructureRequirement::getId)
            ).apply(instance, GeneralStructureRequirement::new),
            "Structure requirement"
    );

    private final String id;

    public GeneralStructureRequirement(String id) {
        super(RequirementIOMode.INPUT);
        this.id = id;
    }

    @Override
    public RequirementType<? extends IRequirement<?>> getType() {
        return Registration.GENERAL_STRUCTURE_REQUIREMENT.get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public MachineComponentType getComponentType() {
        return Registration.GENERAL_STRUCTURE_COMPONENT.get();
    }

    @Override
    public boolean test(GeneralStructureHandler component, ICraftingContext context) {
        return component.checkStructure(id);
    }

    @Override
    public CraftingResult processStart(GeneralStructureHandler component, ICraftingContext context) {
        return CraftingResult.success();
    }

    @Override
    public CraftingResult processEnd(GeneralStructureHandler component, ICraftingContext context) {
        return CraftingResult.success();
    }

    public String getId() {
        return id;
    }
}
