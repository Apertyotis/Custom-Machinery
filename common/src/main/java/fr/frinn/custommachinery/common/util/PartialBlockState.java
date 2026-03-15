package fr.frinn.custommachinery.common.util;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import fr.frinn.custommachinery.api.codec.NamedCodec;
import fr.frinn.custommachinery.common.init.CustomMachineBlock;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.blocks.BlockStateParser.BlockResult;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class PartialBlockState implements Predicate<BlockInWorld> {

    public static final PartialBlockState AIR = new PartialBlockState(Blocks.AIR.defaultBlockState(), Collections.emptyList(), null);
    public static final PartialBlockState ANY = new PartialBlockState(Blocks.AIR.defaultBlockState(), Collections.emptyList(), null) {
        @Override
        public boolean test(BlockInWorld cachedBlockInfo) {
            return true;
        }

        @Override
        public String toString() {
            return "ANY";
        }
    };

    public static final PartialBlockState MACHINE = new PartialBlockState(Blocks.AIR.defaultBlockState(), Collections.emptyList(), null) {
        @Override
        public boolean test(BlockInWorld cachedBlockInfo) {
            return cachedBlockInfo.getState().getBlock() instanceof CustomMachineBlock;
        }

        @Override
        public String toString() {
            return "MACHINE";
        }
    };

    public static final NamedCodec<PartialBlockState> CODEC = NamedCodec.STRING.comapFlatMap(s -> {
        StringReader reader = new StringReader(s);
        try {
            BlockResult result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, true);
            return DataResult.success(new PartialBlockState(result.blockState(), Lists.newArrayList(result.properties().keySet()), result.nbt()));
        } catch (CommandSyntaxException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, PartialBlockState::toString, "Partial block state");

    private final BlockState blockState;
    private final List<Property<?>> properties;
    private final CompoundTag nbt;

    public PartialBlockState(BlockState blockState, List<Property<?>> properties, CompoundTag nbt) {
        this.blockState = blockState;
        this.properties = properties;
        this.nbt = nbt;
    }

    public PartialBlockState(Block block) {
        this(block.defaultBlockState(), new ArrayList<>(), null);
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    public List<String> getProperties() {
        return this.properties.stream().map(property -> property.getName() + "=" + this.blockState.getValue(property)).toList();
    }

    public CompoundTag getNbt() {
        return this.nbt;
    }

    public PartialBlockState rotate(Rotation rotation) {
        if (this.blockState.getBlock() instanceof CustomMachineBlock) {
            return this;
        }

        // 常规朝向
        if (this.blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            if (this.properties.contains(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction direction = this.blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
                direction = rotation.rotate(direction);
                BlockState blockState = this.blockState.setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
                List<Property<?>> properties = Lists.newArrayList(this.properties);
                if(!properties.contains(BlockStateProperties.HORIZONTAL_FACING))
                    properties.add(BlockStateProperties.HORIZONTAL_FACING);
                return new PartialBlockState(blockState, properties, this.nbt);
            }
        } else if (this.blockState.hasProperty(BlockStateProperties.FACING)) {
            if (this.properties.contains(BlockStateProperties.FACING)){
                Direction direction = this.blockState.getValue(BlockStateProperties.FACING);
                if(direction.getAxis() == Axis.Y)
                    return this;
                direction = rotation.rotate(direction);
                BlockState blockState = this.blockState.setValue(BlockStateProperties.FACING, direction);
                List<Property<?>> properties = Lists.newArrayList(this.properties);
                if (!properties.contains(BlockStateProperties.FACING))
                    properties.add(BlockStateProperties.FACING);
                return new PartialBlockState(blockState, properties, this.nbt);
            }
        }

        // 轴朝向
        else if (this.blockState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            if (this.properties.contains(BlockStateProperties.HORIZONTAL_AXIS)) {
                Direction direction = Direction.fromAxisAndDirection(
                        this.blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                        Direction.AxisDirection.POSITIVE);
                direction = rotation.rotate(direction);
                BlockState blockState = this.blockState.setValue(BlockStateProperties.HORIZONTAL_AXIS, direction.getAxis());
                List<Property<?>> properties = Lists.newArrayList(this.properties);
                if (!properties.contains(BlockStateProperties.HORIZONTAL_AXIS))
                    properties.add(BlockStateProperties.HORIZONTAL_AXIS);
                return new PartialBlockState(blockState, properties, this.nbt);
            }
        } else if (this.blockState.hasProperty(BlockStateProperties.AXIS)) {
            if (this.properties.contains(BlockStateProperties.AXIS)) {
                Direction direction = Direction.fromAxisAndDirection(
                        this.blockState.getValue(BlockStateProperties.AXIS),
                        Direction.AxisDirection.POSITIVE);
                if (direction.getAxis() == Axis.Y)
                    return this;
                direction = rotation.rotate(direction);
                BlockState blockState = this.blockState.setValue(BlockStateProperties.AXIS, direction.getAxis());
                List<Property<?>> properties = Lists.newArrayList(this.properties);
                if (!properties.contains(BlockStateProperties.AXIS))
                    properties.add(BlockStateProperties.AXIS);
                return new PartialBlockState(blockState, properties, this.nbt);
            }
        }

        // 管道类朝向
        else {
            int directionOffset = switch (rotation) {
                case NONE -> 0;
                case CLOCKWISE_90 -> 1;
                case CLOCKWISE_180 -> 2;
                case COUNTERCLOCKWISE_90 -> 3;
            };
            List<BooleanProperty> plane = List.of(
                    BlockStateProperties.NORTH, BlockStateProperties.EAST,
                    BlockStateProperties.SOUTH, BlockStateProperties.WEST);
            if (this.blockState.getProperties().containsAll(plane)) {
                if (this.properties.stream().anyMatch(plane::contains)) {
                    BlockState blockState = this.blockState
                            .setValue(BlockStateProperties.NORTH, this.blockState.getValue(plane.get((4 - directionOffset) % 4)))
                            .setValue(BlockStateProperties.EAST, this.blockState.getValue(plane.get((5 - directionOffset) % 4)))
                            .setValue(BlockStateProperties.SOUTH, this.blockState.getValue(plane.get((6 - directionOffset) % 4)))
                            .setValue(BlockStateProperties.WEST, this.blockState.getValue(plane.get((7 - directionOffset) % 4)));

                    List<Property<?>> properties = new ArrayList<>();
                    for (var i: this.properties) {
                        if (i.equals(BlockStateProperties.NORTH)) properties.add(plane.get(directionOffset));
                        else if (i.equals(BlockStateProperties.EAST)) properties.add(plane.get((directionOffset + 1) % 4));
                        else if (i.equals(BlockStateProperties.SOUTH)) properties.add(plane.get((directionOffset + 2) % 4));
                        else if (i.equals(BlockStateProperties.WEST)) properties.add(plane.get((directionOffset + 3) % 4));
                        else {
                            properties.add(i);
                        }
                    }
                    return new PartialBlockState(blockState, properties, this.nbt);
                }
            }
        }
        return this;
    }

    @Override
    public boolean test(BlockInWorld cachedBlockInfo) {
        BlockState blockstate = cachedBlockInfo.getState();
        if (!blockstate.is(this.blockState.getBlock())) {
            return false;
        } else {
            for(Property<?> property : this.properties) {
                if (blockstate.getValue(property) != this.blockState.getValue(property)) {
                    return false;
                }
            }

            if (this.nbt == null) {
                return true;
            } else {
                BlockEntity tileentity = cachedBlockInfo.getEntity();
                return tileentity != null && NbtUtils.compareNbt(this.nbt, tileentity.saveWithFullMetadata(), true);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(BuiltInRegistries.BLOCK.getKey(this.blockState.getBlock()));
        if(!this.properties.isEmpty())
            builder.append("[");
        Iterator<Property<?>> iterator = this.properties.iterator();
        while (iterator.hasNext()) {
            Property<?> property = iterator.next();
            Comparable<?> value = this.blockState.getValue(property);
            builder.append(property.getName());
            builder.append("=");
            //noinspection rawtypes,unchecked
            builder.append(((Property) property).getName(value));
            if(iterator.hasNext())
                builder.append(",");
            else
                builder.append("]");
        }

        if(this.nbt != null && !this.nbt.isEmpty())
            builder.append(this.nbt);
        return builder.toString();
    }

    public MutableComponent getName() {
        return this.blockState.getBlock().getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartialBlockState other)) return false;
        if(this.blockState != other.blockState)
            return false;
        if(!new HashSet<>(this.properties).containsAll(other.properties) || !new HashSet<>(other.properties).containsAll(this.properties))
            return false;
        return NbtUtils.compareNbt(this.nbt, other.nbt, true);
    }
}
