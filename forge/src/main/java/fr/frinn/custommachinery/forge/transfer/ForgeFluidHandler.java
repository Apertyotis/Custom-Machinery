package fr.frinn.custommachinery.forge.transfer;

import com.google.common.collect.Maps;
import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;
import fr.frinn.custommachinery.common.component.FluidMachineComponent;
import fr.frinn.custommachinery.common.component.handler.FluidComponentHandler;
import fr.frinn.custommachinery.common.init.CustomMachineTile;
import fr.frinn.custommachinery.common.util.transfer.ICommonFluidHandler;
import fr.frinn.custommachinery.impl.component.config.RelativeSide;
import fr.frinn.custommachinery.impl.component.config.SideMode;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class ForgeFluidHandler implements ICommonFluidHandler {

    private final FluidComponentHandler fluidHandler;

    private final IFluidHandler generalHandler;
    private final LazyOptional<IFluidHandler> capability;
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedWrappers = Maps.newEnumMap(Direction.class);
    private final Map<Direction, BlockEntity> neighbourStorages = Maps.newEnumMap(Direction.class);
    private final InteractionFluidStorage interactionFluidStorage;

    public ForgeFluidHandler(FluidComponentHandler fluidHandler) {
        this.fluidHandler = fluidHandler;
        this.generalHandler = new SidedFluidStorage(null, fluidHandler);
        this.capability = LazyOptional.of(() -> this.generalHandler);
        for(Direction direction : Direction.values()) {
            SidedFluidStorage storage = new SidedFluidStorage(direction, fluidHandler);
            this.sidedWrappers.put(direction, LazyOptional.of(() -> storage));
        }
        this.interactionFluidStorage = new InteractionFluidStorage(this.fluidHandler);
    }

    public <T> LazyOptional<T> getCapability(@Nullable Direction side) {
        if(side == null)
            return this.capability.cast();
        else if(this.fluidHandler.getComponents().stream().anyMatch(component -> !component.getConfig().getSideMode(side).isNone()))
            return this.sidedWrappers.get(side).cast();
        else
            return LazyOptional.empty();
    }

    @Override
    public void configChanged(RelativeSide side, SideMode oldMode, SideMode newMode) {
        if(oldMode.isNone() != newMode.isNone())
            this.fluidHandler.getManager().getLevel().updateNeighborsAt(this.fluidHandler.getManager().getTile().getBlockPos(), this.fluidHandler.getManager().getTile().getBlockState().getBlock());
    }

    @Override
    public void invalidate() {
        this.capability.invalidate();
        this.sidedWrappers.values().forEach(LazyOptional::invalidate);
    }

    @Override
    public void tick() {
        if (!((CustomMachineTile) this.fluidHandler.getManager().getTile()).shouldAutoIO())
            return;

        //I/O between the machine and neighbour blocks.
        for(Direction side : Direction.values()) {
            if(this.fluidHandler.getComponents().stream().allMatch(component -> component.getConfig().getSideMode(side) == SideMode.NONE))
                continue;

            LazyOptional<IFluidHandler> neighbour;

            if(this.neighbourStorages.get(side) == null || this.neighbourStorages.get(side).isRemoved()) {
                this.neighbourStorages.put(side, this.fluidHandler.getManager().getLevel().getBlockEntity(this.fluidHandler.getManager().getTile().getBlockPos().relative(side)));
                if(this.neighbourStorages.get(side) != null)
                    neighbour = this.neighbourStorages.get(side).getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite());
                else
                    continue;
            }
            else
                neighbour = this.neighbourStorages.get(side).getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite());

            neighbour.ifPresent(tank -> {
                List<FluidMachineComponent> inputCandidate = fluidHandler.getComponents().stream().filter(
                        component -> component.getConfig().isAutoInput()
                                && component.getConfig().getSideMode(side).isInput()
                                && component.getRemainingSpace() > 0)
                        .toList();
                autoInput(inputCandidate, tank, null);

                List<FluidMachineComponent> outputCandidate = fluidHandler.getComponents().stream().filter(
                        component -> component.getConfig().isAutoOutput()
                                && component.getConfig().getSideMode(side).isOutput()
                                && component.getFluidStack().getAmount() > 0
                                && component.getMaxOutput() > 0)
                        .toList();
                autoOutput(outputCandidate, tank, null);
            });
        }
    }

    /** Right click with fluid handler compatibility **/
    @Override
    public boolean interactWithFluidHandler(Player player, InteractionHand hand) {
        return FluidUtil.interactWithFluidHandler(player, hand, this.interactionFluidStorage);
    }

    /**
     * 从外部储罐的指定槽取出流体，尽可能填充给定多方块机器流体槽
     * @param to 多方块机器的物品槽
     * @param tank 外部储罐
     * @param slot 要提取的外部储罐槽位索引
     * @return 如果被提取的槽位为空或变为空，返回真
     * */
    private static boolean inputFromSlot(FluidMachineComponent to, IFluidHandler tank, int slot) {
        FluidStack tryExtract = tank.getFluidInTank(slot);
        tryExtract = tank.drain(tryExtract, IFluidHandler.FluidAction.SIMULATE);
        if (tryExtract.isEmpty())
            return true;
        if (to.isFluidValid(FluidStackHooksForge.fromForge(tryExtract))) {
            long filled = to.insert(tryExtract.getFluid(), tryExtract.getAmount(), tryExtract.getTag(), false);
            if (filled > 0) {
                FluidStack extracted = tryExtract.copy();
                extracted.setAmount((int) filled);
                tank.drain(extracted, IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return tank.getFluidInTank(slot).isEmpty();
    }

    /**
     * 从外部储罐的指定槽列表取出流体，尽可能填充给定多方块机器流体槽，会移除被提取空的槽位索引
     * @param to 多方块机器的流体槽
     * @param tank 外部储罐
     * @param slots 要提取的外部储罐槽位索引列表
     * @return 如果目标槽位被填满，返回真
     * */
    private static boolean inputFromSlots(FluidMachineComponent to, IFluidHandler tank, ArrayList<Integer> slots) {
        if (slots != null) {
            slots.removeIf(integer -> inputFromSlot(to, tank, integer));
        }
        return to.getRemainingSpace() <= 0;
    }

    /**
     * 从外部储罐自动输入，会尽可能填满给定槽位
     * @param inputCandidate 多方块机器的输入槽列表
     * @param tank 外部储罐
     * @param filter 仅从外部储罐获取符合条件的流体
     * */
    public static void autoInput(List<FluidMachineComponent> inputCandidate, IFluidHandler tank, Predicate<FluidStack> filter) {
        ArrayList<Integer> indexes;
        String key;
        int i = 0, j = 0;
        filter = filter == null ? item -> true : filter;
        // 相邻容器只会遍历一次，已扫描的部分做倒排索引
        HashMap<String, ArrayList<Integer>> invertedIndex = new LinkedHashMap<>();
        Iterator<Map.Entry<String, ArrayList<Integer>>> head = null;
        // 遍历机器每个输入槽
        while (i < inputCandidate.size()) {
            FluidStack fluid1 = FluidStackHooksForge.toForge(inputCandidate.get(i).getFluidStack());
            if (fluid1.isEmpty()) {
                // 槽位为空时，查看记录，获取相邻储罐的第一种/下一种流体位置并提取
                if (head != null && head.hasNext()) {
                    indexes = head.next().getValue();
                    if (indexes == null || indexes.isEmpty()) {
                        head.remove();
                        continue;
                    }
                    if (inputFromSlots(inputCandidate.get(i), tank, indexes)) {
                        head = invertedIndex.entrySet().iterator();
                        i++;
                    }
                }
                // 无记录，或已有记录未填满槽位，需要开始/继续遍历外部储罐
                else {
                    boolean found = false;
                    for (; j < tank.getTanks(); j++) {
                        FluidStack fluid2 = tank.getFluidInTank(j);
                        if (!fluid2.isEmpty() && filter.test(fluid2)) {
                            key = fluid2.getFluid().getFluidType().toString();
                            // 找到任意物品则尝试插入
                            if (!inputFromSlot(inputCandidate.get(i), tank, j)) {
                                // 被提取的槽位有剩余流体，需要记录，并使旧迭代器失效
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
                        // 储罐已遍历完成，无任何流体，结束输入
                        if (invertedIndex.isEmpty()){
                            return;
                        }
                        // 仍有流体，但不能放到当前槽位，跳过该槽位
                        else {
                            // 重置迭代器，增加循环计数
                            head = invertedIndex.entrySet().iterator();
                            i++;
                        }
                    }
                    else {
                        if (inputCandidate.get(i).getRemainingSpace() <= 0) {
                            // 当前槽已填满，重置迭代器，增加循环计数
                            head = invertedIndex.entrySet().iterator();
                            i++;
                        }
                        // 这之后会回到循环开头
                    }
                }
            } else {
                // 槽位非空时，获取储罐可能相同的流体位置（不关注NBT，降低哈希压力
                key = fluid1.getFluid().getFluidType().toString();
                indexes = invertedIndex.get(key);
                if (indexes != null && !indexes.isEmpty()) {
                    if (inputFromSlots(inputCandidate.get(i), tank, indexes)) {
                        // 槽位填满，下个循环
                        i++;
                        continue;
                    }
                }
                // 无记录，或已有记录未填满槽位，开始/继续遍历外部储罐
                for (; j < tank.getTanks(); j++) {
                    FluidStack fluid2 = tank.getFluidInTank(j);
                    if (!fluid2.isEmpty() && filter.test(fluid2)) {
                        key = fluid2.getFluid().getFluidType().toString();
                        // 找到流体，若种类相同则尝试插入
                        if (fluid1.isFluidEqual(fluid2)) {
                            if (!inputFromSlot(inputCandidate.get(i), tank, j)) {
                                // 流体有剩余，需要记录
                                invertedIndex
                                        .computeIfAbsent(key, k -> new ArrayList<>())
                                        .add(j);
                            }
                            if (inputCandidate.get(i).getRemainingSpace() <= 0) {
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
                // 循环结束，当前槽仍未填满，则外部储罐已全部遍历
                if (inputCandidate.get(i).getRemainingSpace() > 0) {
                    // 外部储罐为空，结束
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
     * 对外部储罐自动输出，一般会将槽位排空
     * @param outputCandidate 多方块机器的输出槽列表
     * @param tank 外部储罐
     * @param filter 仅输出内部符合条件的流体
     * */
    public static void autoOutput(List<FluidMachineComponent> outputCandidate, IFluidHandler tank, Predicate<FluidStack> filter) {
        outputCandidate.forEach(slot -> {
            FluidStack tryExtract = FluidStackHooksForge.toForge(slot.extract(Integer.MAX_VALUE, true));
            if (filter != null && !filter.test(tryExtract))
                return;
            int filled = tank.fill(tryExtract, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0)
                slot.extract(filled, false);
        });
    }
}
