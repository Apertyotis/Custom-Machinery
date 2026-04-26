package fr.frinn.custommachinery.common.requirement;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.crafting.CraftingResult;
import fr.frinn.custommachinery.api.crafting.ICraftingContext;
import fr.frinn.custommachinery.api.requirement.IRequirement;
import fr.frinn.custommachinery.api.requirement.ITickableRequirement;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.common.component.ParticleComponent;
import fr.frinn.custommachinery.common.crafting.machine.MachineProcessor;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.requirement.AbstractDelayedChanceableRequirement;
import fr.frinn.custommachinery.impl.requirement.AbstractDelayedRequirement;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;


public class ParticleRequirement extends AbstractDelayedChanceableRequirement<ParticleComponent> implements ITickableRequirement<ParticleComponent> {
    public static final NamedCodec<ParticleRequirement> CODEC = NamedCodec.record(instance ->
            instance.group(
                    NamedCodec.STRING.fieldOf("particle").forGetter(requirement -> requirement.particle),
                    MachineProcessor.PHASE.CODEC.fieldOf("phase").forGetter(requirement -> requirement.phase),
                    DefaultCodecs.VEC3.optionalFieldOf("pos", Vec3.ZERO).forGetter(requirement -> requirement.pos),
                    DefaultCodecs.VEC3.optionalFieldOf("delta", Vec3.ZERO).forGetter(requirement -> requirement.delta),
                    NamedCodec.FLOAT.optionalFieldOf("speed", 0f).forGetter(requirement -> requirement.speed),
                    NamedCodec.INT.optionalFieldOf("count", 0).forGetter(requirement -> requirement.count),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("delay", 0.0D).forGetter(AbstractDelayedRequirement::getDelay),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(AbstractDelayedChanceableRequirement::getChance)
            ).apply(instance, (particle, phase, pos, delta, speed, count, delay, chance) -> {
                ParticleRequirement requirement = new ParticleRequirement(particle, phase, pos, delta, speed, count);
                requirement.setDelay(delay);
                requirement.setChance(chance);
                return requirement;
            }),
    "Particle requirement"
    );

    private final String particle;
    private final MachineProcessor.PHASE phase;
    private final Vec3 pos;
    private final Vec3 delta;
    private final float speed;
    private final int count;
    private ParticleOptions particleData;

    public ParticleRequirement(String particle, MachineProcessor.PHASE phase, Vec3 pos, Vec3 delta, float speed, int count) {
        super(RequirementIOMode.INPUT);
        this.particle = particle;
        this.phase = phase;
        this.pos = pos;
        this.delta = delta;
        this.speed = speed;
        this.count = count;

        try {
            StringReader reader = new StringReader(particle);
            ResourceLocation id = ResourceLocation.read(reader);
            //noinspection rawtypes
            ParticleType type = BuiltInRegistries.PARTICLE_TYPE.get(id);
            if (type != null) {
                //noinspection unchecked
                particleData = type.getDeserializer().fromCommand(type, reader);
            }
        } catch (CommandSyntaxException ignored) {

        }
    }

    @Override
    public RequirementType<? extends IRequirement<?>> getType() {
        return Registration.PARTICLE_REQUIREMENT.get();
    }

    @Override
    public MachineComponentType<ParticleComponent> getComponentType() {
        return Registration.PARTICLE_COMPONENT.get();
    }

    @Override
    public boolean test(ParticleComponent component, ICraftingContext context) {
        return true;
    }

    @Override
    public CraftingResult processStart(ParticleComponent component, ICraftingContext context) {
        if (phase == MachineProcessor.PHASE.STARTING && !isDelayed())
            playParticle(component);
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processTick(ParticleComponent component, ICraftingContext context) {
        if (phase == MachineProcessor.PHASE.CRAFTING_TICKABLE && !isDelayed())
            playParticle(component);
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processEnd(ParticleComponent component, ICraftingContext context) {
        if (phase == MachineProcessor.PHASE.ENDING && !isDelayed())
            playParticle(component);
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult execute(ParticleComponent component, ICraftingContext context) {
        playParticle(component);
        return CraftingResult.pass();
    }

    public void playParticle(ParticleComponent component) {
        if (particleData == null)
            return;

        BlockEntity entity = component.getManager().getTile();
        Direction d = entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 pos = Vec3.atCenterOf(entity.getBlockPos()).add(Utils.rotateVec3(this.pos, d));
        if (component.getManager().getLevel() instanceof ServerLevel level) {
            level.sendParticles(particleData, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed);
        }
    }
}
