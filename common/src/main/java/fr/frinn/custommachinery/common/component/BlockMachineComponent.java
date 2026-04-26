package fr.frinn.custommachinery.common.component;

import fr.frinn.custommachinery.api.component.ComponentIOMode;
import fr.frinn.custommachinery.api.component.IMachineComponentManager;
import fr.frinn.custommachinery.api.component.MachineComponentType;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.requirement.BlockRequirement;
import fr.frinn.custommachinery.common.util.PartialBlockState;
import fr.frinn.custommachinery.common.util.Utils;
import fr.frinn.custommachinery.common.util.ingredient.IIngredient;
import fr.frinn.custommachinery.impl.component.AbstractMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockMachineComponent extends AbstractMachineComponent {

    public BlockMachineComponent(IMachineComponentManager manager) {
        super(manager, ComponentIOMode.BOTH);
    }

    @Override
    public MachineComponentType<BlockMachineComponent> getType() {
        return Registration.BLOCK_MACHINE_COMPONENT.get();
    }

    private static class FilterCache {
        Map<Block, Set<PartialBlockState>> filterMap;
        Map<BlockPos, Boolean> cachedResultInWorld;
        long timestamp;

        private static final Map<BlockRequirement, FilterCache> cachedFilter = new IdentityHashMap<>();

        FilterCache(Map<Block, Set<PartialBlockState>> filterMap) {
            this.filterMap = filterMap;
            cachedResultInWorld = new HashMap<>();
            timestamp = -1;
        }

        static FilterCache getCachedRequirementFilter(BlockRequirement requirement) {
            if (!cachedFilter.containsKey(requirement)) {
                Map<Block, Set<PartialBlockState>> filterMap = requirement.moveFilterMap();
                FilterCache cache = null;
                for (FilterCache value: cachedFilter.values()) {
                    if (value.filterMap.equals(filterMap)) {
                        cache = value;
                        break;
                    }
                }
                if (cache == null) {
                    cache = new FilterCache(filterMap);
                }
                cachedFilter.put(requirement, cache);
                return cache;
            } else {
                return cachedFilter.get(requirement);
            }
        }

        boolean test(BlockPos pos, BlockInWorld block, long timestamp) {
            if (this.timestamp != timestamp) {
                this.timestamp = timestamp;
                cachedResultInWorld.clear();
                boolean result = testInner(block);
                cachedResultInWorld.put(pos, result);
                return result;
            } else if (!cachedResultInWorld.containsKey(pos)) {
                boolean result = testInner(block);
                cachedResultInWorld.put(pos, result);
                return result;
            } else {
                return cachedResultInWorld.get(pos);
            }
        }

        private boolean testInner(BlockInWorld block) {
            Set<PartialBlockState> states = filterMap.get(block.getState().getBlock());
            if (states == null)
                return false;
            for (PartialBlockState pState: states) {
                if (pState.test(block))
                    return true;
            }
            return false;
        }

        void invalidate() {
            cachedResultInWorld.clear();
        }
    }

    private static class BlockCache {
        private static final Map<BlockPos, BlockInWorld> cachedBlock = new HashMap<>();
        private static long timestamp = -1;

        static BlockInWorld getCachedBlockInWorld(LevelReader level, BlockPos pos, long timestamp) {
            if (BlockCache.timestamp != timestamp) {
                BlockCache.timestamp = timestamp;
                cachedBlock.clear();
                BlockInWorld block = new BlockInWorld(level, pos, false);
                cachedBlock.put(pos, block);
                return block;
            } else if (!cachedBlock.containsKey(pos)) {
                BlockInWorld block = new BlockInWorld(level, pos, false);
                cachedBlock.put(pos, block);
                return block;
            } else {
                return cachedBlock.get(pos);
            }
        }

        static void invalidate() {
            cachedBlock.clear();
        }
    }

    public long getBlockAmount(BlockRequirement requirement, AABB box, boolean whitelist) {
        BlockEntity entity = getManager().getTile();
        Level level = getManager().getLevel();
        long current = level.getGameTime();
        FilterCache filter = FilterCache.getCachedRequirementFilter(requirement);

        box = Utils.rotateBox(box, entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING))
                .move(entity.getBlockPos());
        return BlockPos.betweenClosedStream(box).filter(pos -> {
            BlockInWorld block = BlockCache.getCachedBlockInWorld(level, pos, current);
            //noinspection ConstantValue
            if (block.getState() == null) {
                return false;
            }
            return filter.test(pos, block, current) == whitelist;
        }).count();
    }

    public long getBlockAmount(AABB box, List<IIngredient<PartialBlockState>> filter, boolean whitelist) {
        BlockEntity entity = getManager().getTile();
        Level level = getManager().getLevel();
        long current = level.getGameTime();

        box = Utils.rotateBox(box, entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING))
                .move(entity.getBlockPos());
        return BlockPos.betweenClosedStream(box).filter(pos -> {
            BlockInWorld block = BlockCache.getCachedBlockInWorld(level, pos, current);
            //noinspection ConstantValue
            if (block.getState() == null) {
                return false;
            }
            return filter.stream()
                    .flatMap(ingredient -> ingredient.getAll().stream())
                    .anyMatch(pState -> pState.test(block) == whitelist);
        }).count();
    }

    public boolean placeBlock(AABB box, PartialBlockState block, int amount) {
        box = Utils.rotateBox(box, getManager().getTile().getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        box = box.move(getManager().getTile().getBlockPos());
        if(BlockPos.betweenClosedStream(box).map(getManager().getLevel()::getBlockState).filter(state -> state.getBlock() == Blocks.AIR).count() < amount)
            return false;
        BlockCache.invalidate();
        AtomicInteger toPlace = new AtomicInteger(amount);
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            if(toPlace.get() > 0 && getManager().getLevel().getBlockState(pos).getBlock() == Blocks.AIR) {
                setBlock(getManager().getLevel(), pos, block);
                toPlace.addAndGet(-1);
            }
        });
        return true;
    }

    public boolean replaceBlock(BlockRequirement requirement, AABB box, PartialBlockState pState, int amount, boolean drop, boolean whitelist) {
        if(getBlockAmount(requirement, box, whitelist) < amount)
            return false;

        BlockEntity entity = getManager().getTile();
        Level level = getManager().getLevel();
        long current = level.getGameTime();
        FilterCache filter = FilterCache.getCachedRequirementFilter(requirement);
        box = Utils.rotateBox(box, entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING))
                .move(entity.getBlockPos());

        AtomicInteger toPlace = new AtomicInteger(amount);
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            if(toPlace.get() > 0) {
                BlockInWorld block = BlockCache.getCachedBlockInWorld(level, pos, current);
                //noinspection ConstantValue
                if (block.getState() == null)
                    return;
                if (filter.test(pos, block, current) == whitelist) {
                    if(!block.getState().isAir())
                        level.destroyBlock(pos, drop);
                    setBlock(level, pos, pState);
                    toPlace.addAndGet(-1);
                }
            }
        });

        filter.invalidate();
        BlockCache.invalidate();
        return true;
    }

    public boolean breakBlock(BlockRequirement requirement, AABB box, int amount, boolean drop, boolean whitelist) {
        if(getBlockAmount(requirement, box, whitelist) < amount)
            return false;

        BlockEntity entity = getManager().getTile();
        Level level = getManager().getLevel();
        long current = level.getGameTime();
        FilterCache filter = FilterCache.getCachedRequirementFilter(requirement);
        box = Utils.rotateBox(box, entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING))
                .move(entity.getBlockPos());

        AtomicInteger toBreak = new AtomicInteger(amount);
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            if(toBreak.get() > 0) {
                BlockInWorld block = BlockCache.getCachedBlockInWorld(level, pos, current);
                if (filter.test(pos, block, current)) {
                    if(!block.getState().isAir())
                        level.destroyBlock(pos, drop);
                    toBreak.addAndGet(-1);
                }
            }
        });

        filter.invalidate();
        BlockCache.invalidate();
        return true;
    }

    private void setBlock(Level world, BlockPos pos, PartialBlockState state) {
        world.setBlockAndUpdate(pos, state.getBlockState());
        BlockEntity tile = world.getBlockEntity(pos);
        if(tile != null && state.getNbt() != null && !state.getNbt().isEmpty()) {
            CompoundTag nbt = state.getNbt().copy();
            nbt.putInt("x", pos.getX());
            nbt.putInt("y", pos.getY());
            nbt.putInt("z", pos.getZ());
            tile.load(nbt);
        }
    }
}
