package pneumaticCraft.common.recipes.programs;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import pneumaticCraft.api.recipe.AssemblyRecipe;
import pneumaticCraft.common.tileentity.TileEntityAssemblyController;
import pneumaticCraft.common.tileentity.TileEntityAssemblyDrill;
import pneumaticCraft.common.tileentity.TileEntityAssemblyIOUnit;
import pneumaticCraft.common.tileentity.TileEntityAssemblyLaser;
import pneumaticCraft.common.tileentity.TileEntityAssemblyPlatform;

public class ProgramDrillLaser extends AssemblyProgram{

    @Override
    public EnumMachine[] getRequiredMachines(){
        return new EnumMachine[]{EnumMachine.PLATFORM, EnumMachine.IO_UNIT_EXPORT, EnumMachine.IO_UNIT_IMPORT, EnumMachine.DRILL, EnumMachine.LASER};
    }

    @Override
    public boolean executeStep(TileEntityAssemblyController controller, TileEntityAssemblyPlatform platform, TileEntityAssemblyIOUnit ioUnitImport, TileEntityAssemblyIOUnit ioUnitExport, TileEntityAssemblyDrill drill, TileEntityAssemblyLaser laser){
        if(ioUnitExport.inventory[0] != null) {
            ioUnitExport.exportHeldItem();
        } else {
            if(platform.hasDrilledStack && platform.hasLaseredStack) {
                ioUnitExport.pickUpPlatformItem();
            } else if(platform.hasDrilledStack) {
                if(canItemBeLasered(platform.getHeldStack())) {
                    laser.startLasering();
                } else {
                    controller.resetSetup();
                }
            } else if(platform.getHeldStack() != null) {
                if(canItemBeDrilled(platform.getHeldStack())) {
                    drill.goDrilling();
                } else {
                    controller.resetSetup();
                }
            } else {
                return ioUnitImport.pickUpInventoryItem(getRecipeList());
            }
        }
        return true;
    }

    private boolean canItemBeLasered(ItemStack item){
        for(AssemblyRecipe recipe : AssemblyRecipe.laserRecipes) {
            if(isValidInput(recipe, item)) return true;
        }
        return false;
    }

    private boolean canItemBeDrilled(ItemStack item){
        for(AssemblyRecipe recipe : AssemblyRecipe.drillRecipes) {
            if(isValidInput(recipe, item)) return true;
        }
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag){

    }

    @Override
    public void readFromNBT(NBTTagCompound tag){

    }

    @Override
    public List<AssemblyRecipe> getRecipeList(){
        return AssemblyRecipe.drillLaserRecipes;
    }

}
