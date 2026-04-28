package fr.frinn.custommachinery.common.component;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.ComponentIOMode;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.IMachineComponentTemplate;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.BlockStructure;
import fr.frinn.custommachinery.common.util.PartialBlockState;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.component.AbstractMachineComponent;

import java.util.List;
import java.util.Map;

public class GeneralStructureComponent extends AbstractMachineComponent {

    public static final int DEFAULT_MAX_COOLDOWN = 160;

    private final String id;
    private final int cooldown;
    private final BlockStructure structure;

    public GeneralStructureComponent(IMachineComponentManager manager, Template template) {
        super(manager, ComponentIOMode.NONE);
        id = template.id;
        cooldown = template.cooldown;

        BlockStructure.Builder builder = BlockStructure.Builder.start();
        for(List<String> levels : template.pattern)
            builder.aisle(levels.toArray(new String[0]));
        for(Map.Entry<Character, IIngredient<PartialBlockState>> key : template.keys.entrySet())
            builder.where(key.getKey(), key.getValue());
        this.structure = builder.build();
    }

    @Override
    public MachineComponentType<?> getType() {
        return Registration.GENERAL_STRUCTURE_COMPONENT.get();
    }

    public String getId() {
        return id;
    }

    public int getCooldown() {
        return cooldown;
    }

    public BlockStructure getStructure() {
        return structure;
    }

    public record Template(
            String id,
            List<List<String>> pattern,
            Map<Character, IIngredient<PartialBlockState>> keys,
            int cooldown
    ) implements IMachineComponentTemplate<GeneralStructureComponent> {

        public static final NamedCodec<GeneralStructureComponent.Template> CODEC = NamedCodec.record(generalStructureComponentTemplate ->
                        generalStructureComponentTemplate.group(
                                NamedCodec.STRING.fieldOf("id").forGetter(template -> template.id),
                                NamedCodec.STRING.listOf().listOf().fieldOf("pattern").forGetter(template -> template.pattern),
                                NamedCodec.unboundedMap(DefaultCodecs.CHARACTER, IIngredient.BLOCK, "Map<Character, Block>").fieldOf("keys").forGetter(requirement -> requirement.keys),
                                NamedCodec.INT.optionalFieldOf("cooldown", DEFAULT_MAX_COOLDOWN).forGetter(template -> template.cooldown)
                        ).apply(generalStructureComponentTemplate, GeneralStructureComponent.Template::new),
                "General structure component"
        );

        @Override
        public MachineComponentType<GeneralStructureComponent> getType() {
            return Registration.GENERAL_STRUCTURE_COMPONENT.get();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean canAccept(Object ingredient, boolean isInput, IMachineComponentManager manager) {
            return false;
        }

        @Override
        public GeneralStructureComponent build(IMachineComponentManager manager) {
            return new GeneralStructureComponent(manager, this);
        }
    }
}
