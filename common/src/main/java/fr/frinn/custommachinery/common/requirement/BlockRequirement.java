package fr.frinn.custommachinery.common.requirement;

import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.api.crafting.CraftingResult;
import fr.frinn.custommachinery.api.crafting.ICraftingContext;
import fr.frinn.custommachinery.api.integration.jei.IDisplayInfo;
import fr.frinn.custommachinery.api.integration.jei.IDisplayInfoRequirement;
import fr.frinn.custommachinery.api.requirement.ITickableRequirement;
import fr.frinn.custommachinery.api.requirement.RequirementIOMode;
import fr.frinn.custommachinery.api.requirement.RequirementType;
import fr.frinn.custommachinery.client.render.CustomMachineRenderer;
import fr.frinn.custommachinery.common.component.BlockMachineComponent;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.ComparatorMode;
import fr.frinn.custommachinery.common.util.PartialBlockState;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.ingredient.BlockIngredient;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.codec.DefaultCodecs;
import fr.frinn.custommachinery.impl.requirement.AbstractDelayedChanceableRequirement;
import fr.frinn.custommachinery.impl.requirement.AbstractRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.text.DecimalFormat;
import java.util.*;

public class BlockRequirement extends AbstractDelayedChanceableRequirement<BlockMachineComponent> implements ITickableRequirement<BlockMachineComponent>, IDisplayInfoRequirement {

    public static final NamedCodec<BlockRequirement> CODEC = NamedCodec.record(blockRequirementInstance ->
            blockRequirementInstance.group(
                    RequirementIOMode.CODEC.fieldOf("mode").forGetter(AbstractRequirement::getMode),
                    ACTION.CODEC.fieldOf("action").forGetter(requirement -> requirement.action),
                    DefaultCodecs.BOX.fieldOf("pos").forGetter(requirement -> requirement.pos),
                    NamedCodec.INT.optionalFieldOf("amount", 1).forGetter(requirement -> requirement.amount),
                    ComparatorMode.CODEC.optionalFieldOf("comparator", ComparatorMode.GREATER_OR_EQUALS).forGetter(requirement -> requirement.comparator),
                    PartialBlockState.CODEC.optionalFieldOf("block", PartialBlockState.AIR).forGetter(requirement -> requirement.block),
                    IIngredient.BLOCK.listOf().optionalFieldOf("filter", Collections.emptyList()).forGetter(requirement -> requirement.filter),
                    NamedCodec.BOOL.optionalFieldOf("whitelist", false).forGetter(requirement -> requirement.whitelist),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("delay", 0.0D).forGetter(BlockRequirement::getDelay),
                    NamedCodec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(AbstractDelayedChanceableRequirement::getChance)
            ).apply(blockRequirementInstance, (mode, action, pos, amount, comparator, block, filter, whitelist, delay, chance) -> {
                    BlockRequirement requirement = new BlockRequirement(mode, action, pos, amount, comparator, block, filter, whitelist);
                    requirement.setDelay(delay);
                    requirement.setChance(chance);
                    return requirement;
            }), "Block requirement"
    );

    private final ACTION action;
    private final AABB pos;
    private final int amount;
    private final ComparatorMode comparator;
    private final PartialBlockState block;
    private final List<IIngredient<PartialBlockState>> filter;
    private final boolean whitelist;

    private Map<Block, Set<PartialBlockState>> filterMap;

    public BlockRequirement(RequirementIOMode mode, ACTION action, AABB pos, int amount, ComparatorMode comparator, PartialBlockState block, List<IIngredient<PartialBlockState>> filter, boolean whitelist) {
        super(mode);
        this.action = action;
        this.pos = pos;
        this.amount = amount;
        this.comparator = comparator;
        this.block = block;
        this.filter = filter;
        this.whitelist = whitelist;

        filterMap = new HashMap<>();
        for (IIngredient<PartialBlockState> ingredient: filter) {
            for (PartialBlockState state: ingredient.getAll()) {
                filterMap.computeIfAbsent(state.getBlockState().getBlock(), k -> new HashSet<>()).add(state);
            }
        }
    }

    public Map<Block, Set<PartialBlockState>> moveFilterMap() {
        Map<Block, Set<PartialBlockState>> filterMap = this.filterMap;
        this.filterMap = null;
        return filterMap;
    }

    @Override
    public RequirementType<?> getType() {
        return Registration.BLOCK_REQUIREMENT.get();
    }

    /**
    * 测试配方时，检查是否有足够的空气/目标方块，进行放置/替换/破坏操作
    */
    @Override
    public boolean test(BlockMachineComponent component, ICraftingContext context) {
        int amount = (int) context.getIntegerModifiedValue(this.amount, this, null);
        return switch (this.action) {
            case CHECK ->
                    this.comparator.compare((int) component.getBlockAmount(this, pos, whitelist), amount);
            case PLACE ->
                    getDelay() != 0 || (int) component.getBlockAmount(pos, Collections.singletonList(BlockIngredient.AIR), true) >= amount;
            case BREAK, DESTROY, REPLACE_BREAK, REPLACE_DESTROY ->
                    getDelay() != 0 || (int) component.getBlockAmount(this, pos, whitelist) >= amount;
        };
    }

    /**
     * 非delay模式时，检查输入模式的方块放置/替换/破坏操作，失败返回error
     * */
    @Override
    public CraftingResult processStart(BlockMachineComponent component, ICraftingContext context) {
        if(this.getMode() == RequirementIOMode.INPUT && getDelay() == 0) {
            return executeInner(component, context);
        }
        return CraftingResult.pass();
    }

    /**
     * 非delay模式时，检查输出模式的方块放置/替换/破坏操作，失败返回error
     * */
    @Override
    public CraftingResult processEnd(BlockMachineComponent component, ICraftingContext context) {
        if(this.getMode() == RequirementIOMode.OUTPUT && getDelay() == 0) {
            return executeInner(component, context);
        }
        return CraftingResult.pass();
    }

    @Override
    public MachineComponentType<BlockMachineComponent> getComponentType() {
        return Registration.BLOCK_MACHINE_COMPONENT.get();
    }

    /**
     * 执行check操作，失败则error
     * */
    @Override
    public CraftingResult processTick(BlockMachineComponent component, ICraftingContext context) {
        if(this.action == ACTION.CHECK) {
            int amount = (int) context.getIntegerModifiedValue(this.amount, this, null);
            long found = component.getBlockAmount(this, this.pos, this.whitelist);
            if(!this.comparator.compare((int) found, amount))
                return CraftingResult.error(Component.translatable("custommachinery.requirements.block.check.error", amount, this.pos.toString(), found));
            return CraftingResult.success();
        }
        return CraftingResult.pass();
    }

    /**
     * 仅delay属于(0,1)时有效，直接放置/替换/摧毁方块，不成功则error
     * */
    @Override
    public CraftingResult execute(BlockMachineComponent component, ICraftingContext context) {
        return executeInner(component, context);
    }

    private CraftingResult executeInner(BlockMachineComponent component, ICraftingContext context) {
        int amount = (int) context.getIntegerModifiedValue(this.amount, this, null);
        switch (this.action) {
            case PLACE -> {
                if (component.placeBlock(pos, block, amount))
                    return CraftingResult.success();
                return CraftingResult.error(Component.translatable("custommachinery.requirements.block.place.error", amount, this.block.getName(), this.pos.toString()));
            }
            case REPLACE_BREAK -> {
                if (component.replaceBlock(this, pos, block, amount, true, whitelist))
                    return CraftingResult.success();
                return CraftingResult.error(Component.translatable("custommachinery.requirements.block.place.error", amount, this.block.getName(), this.pos.toString()));
            }
            case REPLACE_DESTROY -> {
                if (component.replaceBlock(this, pos, block, amount, false, whitelist))
                    return CraftingResult.success();
                return CraftingResult.error(Component.translatable("custommachinery.requirements.block.place.error", amount, this.block.getName(), this.pos.toString()));
            }
            case BREAK -> {
                if (component.breakBlock(this, pos, amount, true, whitelist))
                    return CraftingResult.success();
                return CraftingResult.error(Component.translatable("custommachinery.requirements.block.break.error", amount, this.pos.toString()));
            }
            case DESTROY -> {
                if (component.breakBlock(this, pos, amount, false, whitelist))
                    return CraftingResult.success();
                return CraftingResult.error(Component.translatable("custommachinery.requirements.block.break.error", amount, this.pos.toString()));
            }
            default -> {
                return CraftingResult.pass();
            }
        }
    }

    @Override
    public void getDisplayInfo(IDisplayInfo info) {
        MutableComponent action = null;
        switch (this.action) {
            case CHECK -> action = Component.translatable("custommachinery.requirements.block.check.info");
            case BREAK -> {
                if (this.getMode() == RequirementIOMode.INPUT)
                    action = Component.translatable("custommachinery.requirements.block.break.info.input");
                else
                    action = Component.translatable("custommachinery.requirements.block.break.info.output");
            }
            case DESTROY -> {
                if (this.getMode() == RequirementIOMode.INPUT)
                    action = Component.translatable("custommachinery.requirements.block.destroy.info.input");
                else
                    action = Component.translatable("custommachinery.requirements.block.destroy.info.output");
            }
            case PLACE -> {
                if (this.getMode() == RequirementIOMode.INPUT)
                    action = Component.translatable("custommachinery.requirements.block.place.info.input", this.amount, this.block.getName());
                else
                    action = Component.translatable("custommachinery.requirements.block.place.info.output", this.amount, this.block.getName());
            }
            case REPLACE_BREAK, REPLACE_DESTROY -> {
                if (this.getMode() == RequirementIOMode.INPUT)
                    action = Component.translatable("custommachinery.requirements.block.replace.info.input", this.amount, this.block.getName());
                else
                    action = Component.translatable("custommachinery.requirements.block.replace.info.output", this.amount, this.block.getName());
            }
        }
        if(action != null)
            info.addTooltip(action.withStyle(ChatFormatting.AQUA));

        if(this.getChance() < 1) {
            DecimalFormat df = new DecimalFormat("0.####");
            String chanceString = df.format(this.getChance() * 100);
            info.addTooltip(Component.translatable("custommachinery.requirements.block.chance.info", chanceString)
                    .withStyle(ChatFormatting.GOLD));
        }

        if(this.action != ACTION.PLACE) {
            if(this.action != ACTION.CHECK)
                info.addTooltip(Component.translatable("custommachinery.requirements.block." + (this.whitelist ? "allowed" : "denied")).withStyle(this.whitelist ? ChatFormatting.GREEN : ChatFormatting.RED));
            if(this.whitelist && this.filter.isEmpty())
                info.addTooltip(Component.literal("-").append(Component.translatable("custommachinery.requirements.block.none")));
            else if(!this.whitelist && this.filter.isEmpty())
                info.addTooltip(Component.literal("-").append(Component.translatable("custommachinery.requirements.block.all")));
            else    // 合并显示上完全重复的文本
                this.filter.stream()
                        .map(Utils::getBlockName)
                        .distinct()
                        .forEach(blockName -> info.addTooltip(Component.literal("- ").append(blockName)));
        }
        info.addTooltip(Component.translatable("custommachinery.requirements.block.info.box").withStyle(ChatFormatting.GOLD));
        info.setClickAction((machine, recipe, mouseButton) -> CustomMachineRenderer.addRenderBox(machine.getId(), this.pos));
        info.setItemIcon(Items.GRASS_BLOCK);
    }

    public enum ACTION {
        CHECK,
        BREAK,
        DESTROY,
        PLACE,
        REPLACE_BREAK,
        REPLACE_DESTROY;

        public static final NamedCodec<ACTION> CODEC = NamedCodec.enumCodec(ACTION.class);

        public static ACTION value(String value) {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        }
    }
}
