package fr.frinn.custommachinery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.frinn.custommachinery.api.machine.MachineTile;
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

    private static final Map<ResourceLocation, BoxRenderer> boxToRender = new HashMap<>();
    private static final Map<ResourceLocation, StructureRenderer> blocksToRender = new HashMap<>();
    private static final Map<ResourceLocation, Integer> recipeToRender = new HashMap<>();

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
            BoxRenderer boxRenderer = boxToRender.get(machineId);
            if(boxRenderer.shouldRender())
                boxRenderer.render(matrix, buffer, machineFacing);
            else
                boxToRender.remove(machineId);
        }
        if(blocksToRender.containsKey(machineId)) {
            StructureRenderer structureRenderer = blocksToRender.get(machineId);
            if(structureRenderer.shouldRender())
                structureRenderer.render(matrix, buffer, machineFacing, tile.getLevel(), tile.getBlockPos());
            else
                blocksToRender.remove(machineId);
        }
    }

    public static void addRenderBox(ResourceLocation machine, AABB box) {
        boxToRender.put(machine, new BoxRenderer(CMConfig.get().boxRenderTime, box));
    }

    public static void addRenderBlock(ResourceLocation machine, Function<Direction, Map<BlockPos, IIngredient<PartialBlockState>>> blocks) {
        blocksToRender.put(machine, new StructureRenderer(CMConfig.get().structureRenderTime, blocks));
    }

    /**
     * 根据配方id添加结构渲染项
     * @param id CM配方的ResourceLocation
     * @param time 渲染毫秒数, 非正值表示无限时长
     */
    public static void addBlocksRenderById(ResourceLocation id, int time) {
        recipeToRender.put(id, time);
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
                var blocksGetter = findStructureBlocks(recipe);
                if (blocksGetter != null) {
                    blocksToRender.put(machine.getMachine().getId(), new StructureRenderer(time, blocksGetter));
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
    }

    /**
     * 让机器遍历待渲染配方列表，将可处理的配方结构需求加入结构渲染
     * @param tile CM方块实体
     */
    private void resolveRecipesToRender(CustomMachineTile tile) {
        var it = recipeToRender.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Optional<? extends Recipe<?>> recipe = tile.getLevel().getRecipeManager().byKey(entry.getKey());
            if (recipe.isPresent() && recipe.get() instanceof CustomMachineRecipe cmrecipe) {
                if (!tile.getMachine().getRecipeIds().contains(cmrecipe.getMachineId()))
                    continue;
                var blocksGetter = findStructureBlocks(cmrecipe);
                if (blocksGetter != null)
                    blocksToRender.put(tile.getId(), new StructureRenderer(entry.getValue(), blocksGetter));
            }
            it.remove();
        }
    }

    /**
     * 给定配方搜索其结构需求
     * @param recipe CM配方
     * @return 返回配方首个结构需求的方块提供器，无结构需求时返回null
     */
    private static Function<Direction, Map<BlockPos, IIngredient<PartialBlockState>>> findStructureBlocks(CustomMachineRecipe recipe) {
        for (var requirement: recipe.getRequirements()) {
            if (requirement instanceof StructureRequirement structureRequirement) {
                return structureRequirement.getStructure()::getBlocks;
            }
        }
        return null;
    }
}


