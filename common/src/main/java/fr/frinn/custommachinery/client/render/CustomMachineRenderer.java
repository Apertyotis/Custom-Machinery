package fr.frinn.custommachinery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.frinn.custommachinery.api.machine.MachineTile;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CustomMachineRenderer implements BlockEntityRenderer<CustomMachineTile> {

    private static final Map<ResourceLocation, BoxRenderer> boxToRender = new HashMap<>();
    private static final Map<ResourceLocation, StructureRenderer> blocksToRender = new HashMap<>();

    public CustomMachineRenderer(BlockEntityRendererProvider.Context context) {

    }

    @Override
    public void render(CustomMachineTile tile, float partialTicks, PoseStack matrix, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if(tile.getLevel() == null)
            return;
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
     * 另外添加独立于原先框架的渲染结构方法，自动搜索BE的配方结构要求并开启/关闭渲染
     * @param be 待检测结构的方块实体
     * @return boolean 传入方块实体有配方结构要求时，返回真
     */
    public static boolean toggleRenderBlock(BlockEntity be) {
        if (be instanceof MachineTile machine) {
            ResourceLocation id = machine.getMachine().getId();
            if (blocksToRender.containsKey(id)) {
                blocksToRender.remove(id);
                return true;
            }

            for (var recipe: machine.getLevel().getRecipeManager().getAllRecipesFor(Registration.CUSTOM_MACHINE_RECIPE.get())) {
                if (!machine.getMachine().getRecipeIds().contains(recipe.getMachineId()))
                    continue;
                var requirement = recipe.getRequirements()
                        .stream()
                        .filter(req -> req instanceof StructureRequirement)
                        .findFirst();
                if (requirement.isPresent()) {
                    var renderer = new StructureRenderer(((StructureRequirement)requirement.get()).getStructure()::getBlocks);
                    blocksToRender.put(machine.getMachine().getId(), renderer);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 清除当前所有方块与结构需求的渲染
     * */
    public static void clearRequirementRenderer() {
        boxToRender.clear();
        blocksToRender.clear();
    }
}


