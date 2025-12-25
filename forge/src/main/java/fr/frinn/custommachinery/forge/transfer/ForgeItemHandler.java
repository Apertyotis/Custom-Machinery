package fr.frinn.custommachinery.forge.transfer;

import com.google.common.collect.Maps;
import fr.frinn.custommachinery.common.component.handler.ItemComponentHandler;
import fr.frinn.custommachinery.common.util.transfer.ICommonItemHandler;
import fr.frinn.custommachinery.impl.component.config.RelativeSide;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ForgeItemHandler implements ICommonItemHandler {

    private final ItemComponentHandler handler;

    private final IItemHandler generalHandler;
    private final LazyOptional<IItemHandler> capability;
    private final Map<Direction, SidedItemHandler> sidedHandlers = Maps.newEnumMap(Direction.class);
    private final Map<Direction, LazyOptional<IItemHandler>> sidedWrappers = Maps.newEnumMap(Direction.class);
    private final Map<Direction, BlockEntity> neighbourStorages = Maps.newEnumMap(Direction.class);

    private static final RandomSource RAND = RandomSource.create();

    private int cooldown;
    public ForgeItemHandler(ItemComponentHandler handler) {
        this.handler = handler;
        this.generalHandler = new SidedItemHandler(null, handler);
        this.capability = LazyOptional.of(() -> generalHandler);
        for(Direction direction : Direction.values()) {
            SidedItemHandler sided = new SidedItemHandler(direction, handler);
            this.sidedHandlers.put(direction, sided);
            this.sidedWrappers.put(direction, LazyOptional.of(() -> sided));
        }
        // 避免卡顿峰值
        this.cooldown = RAND.nextInt(5);
    }

    public LazyOptional<IItemHandler> getCapability(@Nullable Direction side) {
        if(side == null)
            return this.capability.cast();
        else if(this.handler.getComponents().stream().anyMatch(component -> !component.getConfig().getSideMode(side).isNone()))
            return this.sidedWrappers.get(side).cast();
        else
            return LazyOptional.empty();
    }

    @Override
    public void configChanged(RelativeSide side, SideMode oldMode, SideMode newMode) {
        if(oldMode.isNone() != newMode.isNone()) {
            Direction direction = side.getDirection(this.handler.getManager().getTile().getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
            this.sidedWrappers.get(direction).invalidate();
            this.sidedWrappers.put(direction, LazyOptional.of(() -> this.sidedHandlers.get(direction)));
            if(oldMode.isNone())
                this.handler.getManager().getLevel().updateNeighborsAt(this.handler.getManager().getTile().getBlockPos(), this.handler.getManager().getTile().getBlockState().getBlock());
        }
    }

    @Override
    public void invalidate() {
        this.capability.invalidate();
        this.sidedWrappers.values().forEach(LazyOptional::invalidate);
    }

    @Override
    public void tick() {
        if(--cooldown > 0) return;
        cooldown = 5;
        for(Direction side : Direction.values()) {
            if(this.handler.getComponents().stream().allMatch(component -> component.getConfig().getSideMode(side) == SideMode.NONE))
                continue;

            LazyOptional<IItemHandler> neighbour;

            if(this.neighbourStorages.get(side) == null || this.neighbourStorages.get(side).isRemoved()) {
                this.neighbourStorages.put(side, this.handler.getManager().getLevel().getBlockEntity(this.handler.getManager().getTile().getBlockPos().relative(side)));
                if(this.neighbourStorages.get(side) != null)
                    neighbour = this.neighbourStorages.get(side).getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite());
                else
                    continue;
            }
            else
                neighbour = this.neighbourStorages.get(side).getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite());

            neighbour.ifPresent(storage -> {
                List<ItemSlot> inputCandidate = sidedHandlers.get(side).getSlotList().stream().filter(
                                slot -> slot.getComponent().getConfig().isAutoInput()
                                        && slot.getComponent().getConfig().getSideMode(side).isInput()
                                        && slot.getComponent().getItemStack().getCount() < slot.getComponent().getCapacity())
                        .toList();
                autoInput(inputCandidate, storage);

                List<ItemSlot> outputCandidate = sidedHandlers.get(side).getSlotList().stream().filter(
                                slot -> slot.getComponent().getConfig().isAutoOutput()
                                        && slot.getComponent().getConfig().getSideMode(side).isOutput()
                                        && !slot.getComponent().getItemStack().isEmpty())
                        .toList();
                autoOutput(outputCandidate, storage);
            });
        }
    }

    /**
     * 从外部存储的指定槽取出物品，尽可能填充给定多方块机器物品槽
     * @param to 多方块机器的物品槽
     * @param storage 外部存储
     * @param slot 要提取的外部存储槽位索引
     * @return 如果被提取的槽位为空或变为空，返回真
     * */
    private boolean inputFromSlot(ItemSlot to, IItemHandler storage, int slot) {
        int maxAmount = storage.getSlotLimit(slot);
        ItemStack extract = storage.extractItem(slot, maxAmount, true);
        if (extract.isEmpty()) {
            return true;
        }

        ItemStack insert = ItemHandlerHelper.insertItem(to, extract, false);
        storage.extractItem(slot, extract.getCount() - insert.getCount(), false);
        return insert.isEmpty();
    }

    /**
     * 从外部存储的指定槽列表取出物品，尽可能填充给定多方块机器物品槽，会移除被提取空的槽位索引
     * @param to 多方块机器的物品槽
     * @param storage 外部存储
     * @param slots 要提取的外部存储槽位索引列表
     * @return 如果目标槽位被填满，返回真
     * */
    private boolean inputFromSlots(ItemSlot to, IItemHandler storage, ArrayList<Integer> slots) {
        if (slots != null) {
            var it = slots.iterator();
            while (it.hasNext()) {
                if (inputFromSlot(to, storage, it.next()))
                    it.remove();
            }
        }
        return to.getComponent().getRemainingSpace() <= 0;
    }

    /**
     * 从外部存储自动输入，会尽可能填满给定槽位
     * @param inputCandidate 多方块机器的输入槽列表
     * @param storage 外部存储
     * */
    private void autoInput(List<ItemSlot> inputCandidate, IItemHandler storage) {
        ArrayList<Integer> indexes = null;
        String key = null;
        int i = 0, j = 0;
        // 相邻容器只会遍历一次，已扫描的部分做倒排索引
        HashMap<String, ArrayList<Integer>> invertedIndex = new LinkedHashMap<>();
        // 容器物品种类索引迭代器
        Iterator<Map.Entry<String, ArrayList<Integer>>> head = null;
        // 遍历机器每个输入槽
        while (i < inputCandidate.size()) {
            ItemStack item = inputCandidate.get(i).getComponent().getItemStack();
            if (item.isEmpty()) {
                // 槽位为空时，查看记录，获取相邻容器的第一种/下一种物品位置并提取
                if (head != null && head.hasNext()) {
                    indexes = head.next().getValue();
                    if (indexes == null || indexes.isEmpty()) {
                        head.remove();
                        continue;
                    }
                    if (inputFromSlots(inputCandidate.get(i), storage, indexes)) {
                        head = invertedIndex.entrySet().iterator();
                        i++;
                        continue;
                    }
                }
                // 无记录，或已有记录未填满槽位，需要开始/继续遍历外部存储
                else {
                    boolean found = false;
                    for (; j < storage.getSlots(); j++) {
                        ItemStack item2 = storage.getStackInSlot(j);
                        if (!item2.isEmpty()) {
                            key = item2.getItem().toString();
                            // 找到任意物品则尝试插入
                            if (!inputFromSlot(inputCandidate.get(i), storage, j)) {
                                // 被提取的槽位有剩余物品，需要记录，并使旧迭代器失效
                                invertedIndex
                                        .computeIfAbsent(key, k -> new ArrayList<>())
                                        .add(j);
                                head = null;
                            }
                            // 发生空槽到非空槽的状态迁移，结束当前分支，回到开头
                            found = true;
                            j++;
                            break;
                        }
                    }
                    if (!found) {
                        // 容器已遍历完成，无任何物品，结束输入
                        if (invertedIndex.isEmpty()){
                            return;
                        }
                        // 仍有物品，但不能放到当前槽位，跳过该槽位
                        else {
                            // 重置迭代器，增加循环计数
                            head = invertedIndex.entrySet().iterator();
                            i++;
                        }
                    }
                    else {
                        if (inputCandidate.get(i).getComponent().getRemainingSpace() > 0) {
                            //当前槽仍有空位，回到循环开头
                        } else {
                            // 当前槽已填满，重置迭代器，增加循环计数
                            head = invertedIndex.entrySet().iterator();
                            i++;
                        }
                    }
                }
            } else {
                // 槽位非空时，获取容器可能相同的物品位置（不关注NBT，降低哈希压力
                key = item.getItem().toString();
                indexes = invertedIndex.get(key);
                if (indexes != null && !indexes.isEmpty()) {
                    if (inputFromSlots(inputCandidate.get(i), storage, indexes)) {
                        // 槽位填满，下个循环
                        i++;
                        continue;
                    }
                }
                // 无记录，或已有记录未填满槽位，开始/继续遍历外部存储
                for (; j < storage.getSlots(); j++) {
                    ItemStack item2 = storage.getStackInSlot(j);
                    if (!item2.isEmpty()) {
                        key = item2.getItem().toString();
                        // 找到物品，若种类相同则尝试插入
                        if (ItemStack.isSameItem(item, item2)) {
                            if (!inputFromSlot(inputCandidate.get(i), storage, j)) {
                                // 物品有剩余，需要记录
                                invertedIndex
                                        .computeIfAbsent(key, k -> new ArrayList<>())
                                        .add(j);
                            }
                            if (inputCandidate.get(i).getComponent().getRemainingSpace() <= 0) {
                                // 槽位填满，暂停遍历外部存储
                                j++;
                                break;
                            }
                        }
                        // 种类不同，需要记录
                        else {
                            invertedIndex
                                    .computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(j);
                        }
                    }
                }
                // 循环结束，当前槽仍未填满，则外部存储已全部遍历
                if (inputCandidate.get(i).getComponent().getRemainingSpace() > 0) {
                    // 外部存储为空，结束
                    if (invertedIndex.isEmpty()) {
                        return;
                    }
                }
                // 正常进入下一循环，重置迭代器，增加循环计数
                head = invertedIndex.entrySet().iterator();
                i++;
            }
        }
    }

    /**
     * 对外部存储自动输出，一般会将槽位排空
     * @param outputCandidate 多方块机器的输出槽列表
     * @param storage 外部存储
     * */
    private void autoOutput(List<ItemSlot> outputCandidate, IItemHandler storage) {
        // 输出基本保持原逻辑，因为通常机器自定义输出槽少，而外部储存为标准实现，性能消耗尚可
        outputCandidate.forEach(slot -> {
            int maxAmount = slot.getComponent().getCapacity();
            ItemStack extract = slot.getComponent().extract(maxAmount, true);
            if (!extract.isEmpty()) {
                // 别再优先合并堆叠了，只有在填充玩家背包时才适合
                ItemStack insert = ItemHandlerHelper.insertItemStacked(storage, extract, false);
                slot.getComponent().extract(extract.getCount() - insert.getCount(), false);
            }
        });
    }
}
