package fr.frinn.custommachinery.common.requirement;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.crafting.CraftingResult;
import fr.frinn.custommachinery.api.crafting.ICraftingContext;
import fr.frinn.custommachinery.api.requirement.IRequirement;
import fr.frinn.custommachinery.api.requirement.ITickableRequirement;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.common.component.SoundComponent;
import fr.frinn.custommachinery.common.crafting.machine.MachineProcessor;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.requirement.AbstractDelayedChanceableRequirement;
import fr.frinn.custommachinery.impl.requirement.AbstractDelayedRequirement;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class SoundRequirement extends AbstractDelayedChanceableRequirement<SoundComponent> implements ITickableRequirement<SoundComponent> {

    public static final NamedCodec<SoundRequirement> CODEC = NamedCodec.record(instance ->
            instance.group(
                    NamedCodec.STRING.fieldOf("sound").forGetter(requirement -> requirement.name),
                    MachineProcessor.PHASE.CODEC.fieldOf("phase").forGetter(requirement -> requirement.phase),
                    DefaultCodecs.VEC3.optionalFieldOf("pos", Vec3.ZERO).forGetter(requirement -> requirement.pos),
                    NamedCodec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(requirement -> requirement.volume),
                    NamedCodec.floatRange(0.0f, 2.0f).optionalFieldOf("pitch", 1.0f).forGetter(requirement -> requirement.pitch),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("delay", 0.0D).forGetter(AbstractDelayedRequirement::getDelay),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(AbstractDelayedChanceableRequirement::getChance)
            ).apply(instance, (name, phase, pos, volume, pitch, delay, chance) -> {
                SoundRequirement requirement = new SoundRequirement(name, phase, pos, volume, pitch);
                requirement.setDelay(delay);
                requirement.setChance(chance);
                return requirement;
            }),
            "Sound requirement"
    );

    private final String name;
    private final MachineProcessor.PHASE phase;
    private final Vec3 pos;
    private final float volume;
    private final float pitch;

    private final ResourceLocation sound;

    public SoundRequirement(String name, MachineProcessor.PHASE phase, Vec3 pos, float volume, float pitch) {
        super(RequirementIOMode.INPUT);
        this.name = name;
        this.phase = phase;
        this.pos = pos;
        this.volume = volume;
        this.pitch = pitch;
        this.sound = ResourceLocation.tryParse(name);
    }

    @Override
    public RequirementType<? extends IRequirement<?>> getType() {
        return Registration.SOUND_REQUIREMENT.get();
    }

    @Override
    public MachineComponentType<SoundComponent> getComponentType() {
        return Registration.SOUND_COMPONENT.get();
    }

    @Override
    public boolean test(SoundComponent component, ICraftingContext context) {
        return true;
    }

    @Override
    public CraftingResult processStart(SoundComponent component, ICraftingContext context) {
        if (phase == MachineProcessor.PHASE.STARTING && !isDelayed())
            playSound(component);
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processTick(SoundComponent component, ICraftingContext context) {
        if (phase == MachineProcessor.PHASE.CRAFTING_TICKABLE && !isDelayed())
            playSound(component);
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult processEnd(SoundComponent component, ICraftingContext context) {
        if (phase == MachineProcessor.PHASE.ENDING && !isDelayed())
            playSound(component);
        return CraftingResult.pass();
    }

    @Override
    public CraftingResult execute(SoundComponent component, ICraftingContext context) {
        playSound(component);
        return CraftingResult.pass();
    }

    public void playSound(SoundComponent component) {
        if (sound == null)
            return;

        SoundEvent event = SoundEvent.createVariableRangeEvent(sound);
        BlockEntity entity = component.getManager().getTile();
        Direction d = entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 pos = Vec3.atCenterOf(entity.getBlockPos()).add(Utils.rotateVec3(this.pos, d));
        component.getManager().getLevel()
                .playSound(null, pos.x, pos.y, pos.z, event, SoundSource.BLOCKS, volume, pitch);
    }
}
