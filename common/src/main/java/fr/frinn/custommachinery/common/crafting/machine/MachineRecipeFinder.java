package fr.frinn.custommachinery.common.crafting.machine;

import fr.frinn.custommachinery.api.machine.MachineTile;
import fr.frinn.custommachinery.common.crafting.CraftingContext;
import fr.frinn.custommachinery.common.crafting.RecipeChecker;
import fr.frinn.custommachinery.common.init.Registration;
import fr.frinn.custommachinery.common.util.Comparators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class MachineRecipeFinder {

    private final MachineTile tile;
    private final int baseCooldown;
    private List<RecipeChecker<CustomMachineRecipe>> recipes;
    private List<RecipeChecker<CustomMachineRecipe>> okToCheck;
    private boolean inventoryChanged = true;

    private int recipeCheckCooldown;

    public MachineRecipeFinder(MachineTile tile, int baseCooldown) {
        this.tile = tile;
        this.baseCooldown = baseCooldown;
    }

    public void init() {
        if(tile.getLevel() == null)
            throw new IllegalStateException("Broken machine " + tile.getMachine().getId() + "doesn't have a world");
        this.recipes = tile.getLevel().getRecipeManager()
                .getAllRecipesFor(Registration.CUSTOM_MACHINE_RECIPE.get())
                .stream()
                .filter(recipe -> tile.getMachine().getRecipeIds().contains(recipe.getMachineId()))
                .sorted(Comparators.RECIPE_PRIORITY_COMPARATOR.reversed())
                .map(CustomMachineRecipe::checker)
                .toList();
        this.okToCheck = new ArrayList<>();
        this.recipeCheckCooldown = tile.getLevel().random.nextInt(this.baseCooldown);
    }

    /**
     * 默认每20tick触发一次搜索
     * 当存储变化时，将机器所有配方加入待确认列表
     * 遍历配方，当存储无变化、配方条件无世界交互类时，跳过检查
     * 否则检查配方，并传入存储变化标记
     * 传入存储变化标记时，清除配方存储满足标记，遍历配方存储类条件，全部通过则设定存储满足标记，否则配方检查失败
     * 然后检查世界交互条件，全部通过时配方检查成功，否则失败
     * 传入无存储变化标记时，检查存储满足标记，通过则继续检查世界交互条件
     * 如果该配方检查成功，则清除存储变化标记，返回该配方
     * 如果该配方检查不成功，再检查存储满足标记，将存储不满足的配方移出待确认列表
     * 全部检查完成后，清除存储变化标记，返回空结果
     * @param context 机器合成上下文，用于计算各种升级属性
     * @param immediately 是否立即搜索配方，否则会等待搜索冷却
     * @return 返回匹配配方中优先级最高的，无匹配则为Optional.empty()
     * */
    public Optional<CustomMachineRecipe> findRecipe(CraftingContext.Mutable context, boolean immediately) {
        if(tile.getLevel() == null)
            return Optional.empty();

        if(immediately || this.recipeCheckCooldown-- <= 0) {
            this.recipeCheckCooldown = this.baseCooldown;
            if(this.inventoryChanged) {
                this.okToCheck.clear();
                this.okToCheck.addAll(this.recipes);
            }
            Iterator<RecipeChecker<CustomMachineRecipe>> iterator = this.okToCheck.iterator();
            while (iterator.hasNext()) {
                RecipeChecker<CustomMachineRecipe> checker = iterator.next();
                if(!this.inventoryChanged && checker.isInventoryRequirementsOnly())
                    continue;
                if(checker.check(this.tile, context.setRecipe(checker.getRecipe()), this.inventoryChanged)) {
                    setInventoryChanged(false);
                    return Optional.of(checker.getRecipe());
                }
                if(!checker.isInventoryRequirementsOk())
                    iterator.remove();
            }
            setInventoryChanged(false);
        }
        return Optional.empty();
    }

    public void setInventoryChanged(boolean changed) {
        this.inventoryChanged = changed;
    }
}
