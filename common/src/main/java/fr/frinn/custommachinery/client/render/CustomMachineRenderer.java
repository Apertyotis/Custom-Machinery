package fr.frinn.custommachinery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.integration.config.CMConfig;
import fr.frinn.custommachinery.common.util.PartialBlockState;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.Function;

public class CustomMachineRenderer implements BlockEntityRenderer<CustomMachineTile> {

    private static final Map<ResourceLocation, List<BoxRenderer>> boxToRender = new HashMap<>();
    private static final Map<ResourceLocation, List<StructureRenderer>> blocksToRender = new HashMap<>();
    private static final Map<ResourceLocation, Map<String, StructureRenderer>> generalStructureToRender = new HashMap<>();

    public CustomMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CustomMachineTile tile, float partialTicks, PoseStack matrix, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if(tile.getLevel() == null)
            return;
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

        Map<String, StructureRenderer> map = generalStructureToRender.get(machineId);
        if (map != null) {
            List<String> toRemove = new ArrayList<>();
            for (var entry: map.entrySet()) {
                if (entry.getValue().shouldRender()) {
                    entry.getValue().initForGeneralStructure(tile, entry.getKey());
                    entry.getValue().render(matrix, buffer, machineFacing, tile.getLevel(), tile.getBlockPos());
                } else {
                    toRemove.add(entry.getKey());
                }
            }
            for (String key: toRemove) {
                map.remove(key);
            }
            if (map.isEmpty()) {
                generalStructureToRender.remove(machineId);
            }
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
     * 渲染指定类型机器的指定结构，重复调用则覆盖渲染时长
     * @param machineId 机器 id
     * @param structureId 结构 id
     * @param time 渲染时长，非正值表示无限
     */
    public static void addGeneralStructureRenderById(ResourceLocation machineId, String structureId, int time) {
        Map<String, StructureRenderer> map = generalStructureToRender.computeIfAbsent(machineId, k -> new HashMap<>());
        StructureRenderer renderer = map.get(structureId);
        if (renderer == null) {
            map.put(structureId, new StructureRenderer(time));
        } else {
            renderer.setTime(time);
        }
    }
}
