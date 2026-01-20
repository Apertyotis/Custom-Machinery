package fr.frinn.custommachinery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.frinn.custommachinery.api.machine.MachineTile;
import fr.frinn.custommachinery.api.requirement.IRequirement;
import fr.frinn.custommachinery.common.crafting.machine.CustomMachineRecipe;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.integration.config.CMConfig;
import fr.frinn.custommachinery.common.requirement.StructureRequirement;
import fr.frinn.custommachinery.common.util.PartialBlockState;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.Function;

public class CustomMachineRenderer implements BlockEntityRenderer<CustomMachineTile> {

    private static final Map<ResourceLocation, List<BoxRenderer>> boxToRender = new HashMap<>();
    private static final Map<ResourceLocation, List<StructureRenderer>> blocksToRender = new HashMap<>();
    private static final List<RenderRecipeContext> recipeToRender = new ArrayList<>();
    private record RenderRecipeContext(ResourceLocation id, int time, boolean virtual) {}

    public CustomMachineRenderer(BlockEntityRendererProvider.Context context) {

    }

    @Override
    public void render(CustomMachineTile tile, float partialTicks, PoseStack matrix, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if(tile.getLevel() == null)
            return;
        if (!recipeToRender.isEmpty())
            resolveRecipesToRender(tile);
        ResourceLocation machineId = tile.getId();
        Direction machineFacing = tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        if(boxToRender.containsKey(machineId)) {
            List<BoxRenderer> rendererList = boxToRender.get(machineId);
            var it = rendererList.iterator();
            while (it.hasNext()) {
                BoxRenderer boxRenderer = it.next();
                if(boxRenderer.shouldRender())
                    boxRenderer.render(matrix, buffer, machineFacing);
                else
                    it.remove();
            }
            if (rendererList.isEmpty()) boxToRender.remove(machineId);
        }
        if(blocksToRender.containsKey(machineId)) {
            List<StructureRenderer> rendererList = blocksToRender.get(machineId);
            var it = rendererList.iterator();
            while (it.hasNext()) {
                StructureRenderer structureRenderer = it.next();
                if(structureRenderer.shouldRender())
                    structureRenderer.render(matrix, buffer, machineFacing, tile.getLevel(), tile.getBlockPos());
                else
                    it.remove();
            }
            if (rendererList.isEmpty()) blocksToRender.remove(machineId);
        }
    }

    public static void addRenderBox(ResourceLocation machine, AABB box) {
        boxToRender.computeIfAbsent(machine, k -> new ArrayList<>())
                .add(new BoxRenderer(CMConfig.get().boxRenderTime, box));
    }

    public static void addRenderBlock(ResourceLocation machine, Function<Direction, Map<BlockPos, IIngredient<PartialBlockState>>> blocks) {
        blocksToRender.computeIfAbsent(machine, k -> new ArrayList<>())
                .add(new StructureRenderer(CMConfig.get().structureRenderTime, blocks));
    }

    /**
     * 根据配方id添加结构渲染项
     * @param id CM配方的ResourceLocation
     * @param time 渲染毫秒数, 非正值表示无限时长
     * @param virtual 传入true时, 渲染JEI显示的结构要求, 而非真实结构要求
     */
    public static void addBlocksRenderById(ResourceLocation id, int time, boolean virtual) {
        recipeToRender.add(new RenderRecipeContext(id, time, virtual));
    }

    /**
     * 根据BE添加/移除结构渲染项, 搜索传入CM机器的第一个配方结构需求, 加入渲染列表
     * @param be 待检测结构的方块实体
     * @param time 渲染毫秒数, 非正值表示无限时长
     * @return boolean 传入方块实体有配方结构要求时，返回真
     */
    public static boolean addBlocksRenderByBE(BlockEntity be, int time) {
        if (be instanceof MachineTile machine) {
            ResourceLocation id = machine.getMachine().getId();
            if (blocksToRender.containsKey(id)) {
                blocksToRender.remove(id);
                return true;
            }

            var level = machine.getLevel();
            if (level == null) return false;
            for (var recipe: machine.getLevel().getRecipeManager().getAllRecipesFor(Registration.CUSTOM_MACHINE_RECIPE.get())) {
                if (!machine.getMachine().getRecipeIds().contains(recipe.getMachineId()))
                    continue;
                var blocksGetterList = findStructureBlocks(recipe, false);
                if (!blocksGetterList.isEmpty()) {
                    blocksToRender.computeIfAbsent(machine.getMachine().getId(), k -> new ArrayList<>()).addAll(
                            blocksGetterList.stream()
                                    .map(blocks -> new StructureRenderer(time, blocks))
                                    .toList());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 清除当前所有方块与结构需求的渲染
     */
    public static void clearRequirementRenderer() {
        boxToRender.clear();
        blocksToRender.clear();
        recipeToRender.clear();
    }

    /**
     * 让机器遍历待渲染配方列表，将可处理的配方结构需求加入结构渲染
     * @param tile CM方块实体
     */
    private void resolveRecipesToRender(CustomMachineTile tile) {
        if (tile.getLevel() == null) return;
        var it = recipeToRender.iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Optional<? extends Recipe<?>> recipe = tile.getLevel().getRecipeManager().byKey(entry.id);
            if (recipe.isPresent() && recipe.get() instanceof CustomMachineRecipe cmrecipe) {
                if (!tile.getMachine().getRecipeIds().contains(cmrecipe.getMachineId()))
                    continue;
                var blocksGetterList = findStructureBlocks(cmrecipe, entry.virtual);
                if (!blocksGetterList.isEmpty()) {
                    blocksToRender.computeIfAbsent(tile.getId(), k -> new ArrayList<>()).addAll(
                            blocksGetterList.stream()
                                    .map(blocks -> new StructureRenderer(entry.time, blocks))
                                    .toList());
                }
            }
            it.remove();
        }
    }

    /**
     * 给定配方搜索其结构需求
     * @param recipe CM配方
     * @param virtual 传入true时, 渲染JEI显示的结构要求, 而非真实结构要求
     * @return 返回配方结构方块提供器的列表，无结构需求时返回空列表
     */
    private static List<Function<Direction, Map<BlockPos, IIngredient<PartialBlockState>>>> findStructureBlocks(CustomMachineRecipe recipe, boolean virtual) {
        List<Function<Direction, Map<BlockPos, IIngredient<PartialBlockState>>>> blocksGetterList = new ArrayList<>();
        List<IRequirement<?>> requirements;
        if (virtual) requirements = recipe.getJeiRequirements();
        else requirements = recipe.getRequirements();
        for (var requirement: requirements) {
            if (requirement instanceof StructureRequirement structureRequirement) {
                blocksGetterList.add(structureRequirement.getStructure()::getBlocks);
            }
        }
        return blocksGetterList;
    }
}


