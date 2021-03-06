package pneumaticCraft.common.tileentity;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;

import pneumaticCraft.api.tileentity.IPneumaticMachine;
import pneumaticCraft.common.item.ItemMachineUpgrade;
import pneumaticCraft.common.item.Itemss;
import pneumaticCraft.common.network.NetworkHandler;
import pneumaticCraft.common.network.PacketPlaySound;
import pneumaticCraft.common.network.PacketSpawnParticle;
import pneumaticCraft.common.thirdparty.computercraft.LuaConstant;
import pneumaticCraft.common.thirdparty.computercraft.LuaMethod;
import pneumaticCraft.lib.ModIds;
import pneumaticCraft.lib.Names;
import pneumaticCraft.lib.PneumaticValues;
import pneumaticCraft.lib.Sounds;
import pneumaticCraft.lib.TileEntityConstants;
import cpw.mods.fml.common.Optional;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;

public class TileEntityAirCannon extends TileEntityPneumaticBase implements ISidedInventory, IInventory{

    private ItemStack[] inventory;
    private final Random rand = new Random();
    public float rotationAngle;
    public float heightAngle;
    public float targetRotationAngle;
    public float targetHeightAngle;
    public int ticks = 100;
    public boolean doneTurning = false;
    private boolean redstonePowered = false;
    public int gpsX;
    public int gpsY;
    public int gpsZ;
    public boolean coordWithinReach;
    public boolean fireOnlyOnRightAngle = true;
    private int oldRangeUpgrades;
    private boolean externalControl;//used in the CC API, to disallow the Cannon to update its angles when things like range upgrades / GPS Tool have changed.

    private final int INVENTORY_SIZE = 6;
    public final int CANNON_SLOT = 0;
    public final int GPS_SLOT = 1;
    public final int UPGRADE_SLOT_1 = 2;
    public final int UPGRADE_SLOT_2 = 3;
    public final int UPGRADE_SLOT_3 = 4;
    public final int UPGRADE_SLOT_4 = 5;

    public TileEntityAirCannon(){
        super(PneumaticValues.DANGER_PRESSURE_AIR_CANNON, PneumaticValues.MAX_PRESSURE_AIR_CANNON, PneumaticValues.VOLUME_AIR_CANNON);
        inventory = new ItemStack[INVENTORY_SIZE];
        setUpgradeSlots(new int[]{UPGRADE_SLOT_1, UPGRADE_SLOT_2, UPGRADE_SLOT_3, UPGRADE_SLOT_4});
    }

    @Override
    public void updateEntity(){
        ticks++;
        if(!worldObj.isRemote && ticks > 100) {
            sendDescriptionPacket();
            ticks = 0;
        }

        // GPS Tool read
        if(inventory[1] != null && inventory[1].getItem() == Itemss.GPSTool && !externalControl) {
            if(inventory[1].stackTagCompound != null) {

                NBTTagCompound gpsTag = inventory[1].stackTagCompound;
                int destinationX = gpsTag.getInteger("x");
                int destinationY = gpsTag.getInteger("y");
                int destinationZ = gpsTag.getInteger("z");

                if(destinationX != gpsX || destinationY != gpsY || destinationZ != gpsZ) {

                    gpsX = destinationX;
                    gpsY = destinationY;
                    gpsZ = destinationZ;
                    updateDestination();
                }
            }
        }

        int curRangeUpgrades = Math.min(8, getUpgrades(ItemMachineUpgrade.UPGRADE_RANGE, getUpgradeSlots()));
        if(curRangeUpgrades != oldRangeUpgrades) {
            oldRangeUpgrades = curRangeUpgrades;
            if(!externalControl) updateDestination();
        }

        // update angles
        doneTurning = true;
        float speedMultiplier = getSpeedMultiplierFromUpgrades(getUpgradeSlots());
        if(rotationAngle < targetRotationAngle) {
            if(rotationAngle < targetRotationAngle - TileEntityConstants.CANNON_SLOW_ANGLE) {
                rotationAngle += TileEntityConstants.CANNON_TURN_HIGH_SPEED * speedMultiplier;
            } else {
                rotationAngle += TileEntityConstants.CANNON_TURN_LOW_SPEED * speedMultiplier;
            }
            if(rotationAngle > targetRotationAngle) rotationAngle = targetRotationAngle;
            doneTurning = false;
        }
        if(rotationAngle > targetRotationAngle) {
            if(rotationAngle > targetRotationAngle + TileEntityConstants.CANNON_SLOW_ANGLE) {
                rotationAngle -= TileEntityConstants.CANNON_TURN_HIGH_SPEED * speedMultiplier;
            } else {
                rotationAngle -= TileEntityConstants.CANNON_TURN_LOW_SPEED * speedMultiplier;
            }
            if(rotationAngle < targetRotationAngle) rotationAngle = targetRotationAngle;
            doneTurning = false;
        }
        if(heightAngle < targetHeightAngle) {
            if(heightAngle < targetHeightAngle - TileEntityConstants.CANNON_SLOW_ANGLE) {
                heightAngle += TileEntityConstants.CANNON_TURN_HIGH_SPEED * speedMultiplier;
            } else {
                heightAngle += TileEntityConstants.CANNON_TURN_LOW_SPEED * speedMultiplier;
            }
            if(heightAngle > targetHeightAngle) heightAngle = targetHeightAngle;
            doneTurning = false;
        }
        if(heightAngle > targetHeightAngle) {
            if(heightAngle > targetHeightAngle + TileEntityConstants.CANNON_SLOW_ANGLE) {
                heightAngle -= TileEntityConstants.CANNON_TURN_HIGH_SPEED * speedMultiplier;
            } else {
                heightAngle -= TileEntityConstants.CANNON_TURN_LOW_SPEED * speedMultiplier;
            }
            if(heightAngle < targetHeightAngle) heightAngle = targetHeightAngle;
            doneTurning = false;
        }

        super.updateEntity();

    }

    // ANGLE METHODS -------------------------------------------------

    private void updateDestination(){
        doneTurning = false;
        // send a packet to the client to be able to show the right GPS coords
        // in the gui.
        if(!worldObj.isRemote) sendDescriptionPacket();
        // take dispenser upgrade in account
        double payloadFrictionY = 0.98D;// this value will differ when a
                                        // dispenser upgrade is inserted.
        double payloadFrictionX = 0.98D;
        double payloadGravity = 0.04D;
        if(getUpgrades(ItemMachineUpgrade.UPGRADE_DISPENSER_DAMAGE, getUpgradeSlots()) > 0 && inventory[0] != null) {// if
            // there
            // is
            // a
            // dispenser
            // upgrade
            // inserted.
            Item item = inventory[0].getItem();
            if(item == Items.potionitem || item == Items.experience_bottle || item == Items.egg || item == Items.snowball) {// EntityThrowable
                payloadFrictionY = 0.99D;
                payloadGravity = 0.03D;
            } else if(item == Items.arrow) {
                payloadFrictionY = 0.99D;
                payloadGravity = 0.05D;
            } else if(item == Items.minecart || item == Items.chest_minecart || item == Items.hopper_minecart || item == Items.tnt_minecart || item == Items.furnace_minecart) {
                payloadFrictionY = 0.95D;
            }
            // else if(itemID == Item.fireballCharge.itemID){
            // payloadGravity = 0.0D;
            // }

            // family items (throwable) which only differ in gravity.
            if(item == Items.potionitem) payloadGravity = 0.05D;
            else if(item == Items.experience_bottle) payloadGravity = 0.07D;

            payloadFrictionX = payloadFrictionY;

            // items which have different frictions for each axis.
            if(item == Items.boat) {
                payloadFrictionX = 0.99D;
                payloadFrictionY = 0.95D;
            }
            if(item == Items.spawn_egg) {
                payloadFrictionY = 0.98D;
                payloadFrictionX = 0.91D;
                payloadGravity = 0.08D;
            }
        }

        // calculate the heading.
        double deltaX = gpsX - xCoord;
        double deltaZ = gpsZ - zCoord;
        float calculatedRotationAngle;
        if(deltaX >= 0 && deltaZ < 0) {
            calculatedRotationAngle = (float)(Math.atan(Math.abs(deltaX / deltaZ)) / Math.PI * 180D);
        } else if(deltaX >= 0 && deltaZ >= 0) {
            calculatedRotationAngle = (float)(Math.atan(Math.abs(deltaZ / deltaX)) / Math.PI * 180D) + 90;
        } else if(deltaX < 0 && deltaZ >= 0) {
            calculatedRotationAngle = (float)(Math.atan(Math.abs(deltaX / deltaZ)) / Math.PI * 180D) + 180;
        } else {
            calculatedRotationAngle = (float)(Math.atan(Math.abs(deltaZ / deltaX)) / Math.PI * 180D) + 270;
        }

        // calculate the height angle.
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double deltaY = gpsY - yCoord;
        float calculatedHeightAngle = calculateBestHeightAngle(distance, deltaY, getForce(), payloadGravity, payloadFrictionX, payloadFrictionY);

        setTargetAngles(calculatedRotationAngle, calculatedHeightAngle);
    }

    private float calculateBestHeightAngle(double distance, double deltaY, float force, double payloadGravity, double payloadFrictionX, double payloadFrictionY){
        double bestAngle = 0;
        double bestDistance = Float.MAX_VALUE;
        if(payloadGravity == 0D) {
            return 90F - (float)(Math.atan(deltaY / distance) * 180F / Math.PI);
        }
        for(double i = Math.PI * 0.25D; i < Math.PI * 0.50D; i += 0.001D) {
            double motionX = Math.cos(i) * force;// calculate the x component of
                                                 // the vector
            double motionY = Math.sin(i) * force;// calculate the y component of
                                                 // the vector
            double posX = 0;
            double posY = 0;
            while(posY > deltaY || motionY > 0) { // simulate movement, until we
                                                  // reach the y-level required
                posX += motionX;
                posY += motionY;
                motionY -= payloadGravity;// gravity
                motionX *= payloadFrictionX;// friction
                motionY *= payloadFrictionY;// friction
            }
            double distanceToTarget = Math.abs(distance - posX);// take the
                                                                // distance
            if(distanceToTarget < bestDistance) {// and return the best angle.
                bestDistance = distanceToTarget;
                bestAngle = i;
            }
        }
        coordWithinReach = bestDistance < 1.5D;
        return 90F - (float)(bestAngle * 180D / Math.PI);
    }

    public synchronized void setTargetAngles(float rotationAngle, float heightAngle){
        targetRotationAngle = rotationAngle;
        targetHeightAngle = heightAngle;
        if(!worldObj.isRemote) scheduleDescriptionPacket();
    }

    // this function calculates with the parsed in X and Z angles and the force
    // the needed, and outputs the X, Y and Z velocities.
    public double[] getVelocityVector(float angleX, float angleZ, float force){
        double[] velocities = new double[3];
        velocities[0] = Math.sin((double)angleZ / 180 * Math.PI);
        velocities[1] = Math.cos((double)angleX / 180 * Math.PI);
        velocities[2] = Math.cos((double)angleZ / 180 * Math.PI) * -1;

        velocities[0] *= Math.sin((double)angleX / 180 * Math.PI);
        velocities[2] *= Math.sin((double)angleX / 180 * Math.PI);
        // calculate the total velocity vector, in relation.
        double vectorTotal = velocities[0] * velocities[0] + velocities[1] * velocities[1] + velocities[2] * velocities[2];
        vectorTotal = force / vectorTotal; // calculate the relation between the
                                           // forces to be shot, and the
                                           // calculated vector (the scale).
        for(int i = 0; i < 3; i++) {
            velocities[i] *= vectorTotal; // scale up the velocities
            // System.out.println("velocities " + i + " = " + velocities[i]);
        }
        return velocities;
    }

    public boolean hasCoordinate(){
        return gpsX != 0 || gpsY != 0 || gpsZ != 0;
    }

    // PNEUMATIC METHODS -----------------------------------------

    @Override
    protected void disperseAir(){
        super.disperseAir();
        List<Pair<ForgeDirection, IPneumaticMachine>> teList = getConnectedPneumatics();
        if(teList.size() == 0) airLeak(ForgeDirection.getOrientation(getBlockMetadata()));
    }

    @Override
    public boolean isConnectedTo(ForgeDirection side){
        return ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord)) == side;
    }

    public float getForce(){
        return 2F + oldRangeUpgrades;
    }

    // INVENTORY METHODS- && NBT
    // ------------------------------------------------------------

    /**
     * Returns the number of slots in the inventory.
     */
    @Override
    public int getSizeInventory(){

        return inventory.length;
    }

    /**
     * Returns the stack in slot i
     */
    @Override
    public ItemStack getStackInSlot(int slot){

        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount){

        ItemStack itemStack = getStackInSlot(slot);
        if(itemStack != null) {
            if(itemStack.stackSize <= amount) {
                setInventorySlotContents(slot, null);
            } else {
                itemStack = itemStack.splitStack(amount);
                if(itemStack.stackSize == 0) {
                    setInventorySlotContents(slot, null);
                }
            }
        }

        return itemStack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot){

        ItemStack itemStack = getStackInSlot(slot);
        if(itemStack != null) {
            setInventorySlotContents(slot, null);
        }
        return itemStack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack itemStack){
        inventory[slot] = itemStack;
        if(itemStack != null && itemStack.stackSize > getInventoryStackLimit()) {
            itemStack.stackSize = getInventoryStackLimit();
        }
    }

    @Override
    public String getInventoryName(){
        return Names.AIR_CANNON;
    }

    @Override
    public int getInventoryStackLimit(){
        return 64;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound){
        super.readFromNBT(nbtTagCompound);
        redstonePowered = nbtTagCompound.getBoolean("redstonePowered");
        targetRotationAngle = nbtTagCompound.getFloat("targetRotationAngle");
        targetHeightAngle = nbtTagCompound.getFloat("targetHeightAngle");
        rotationAngle = nbtTagCompound.getFloat("rotationAngle");
        heightAngle = nbtTagCompound.getFloat("heightAngle");
        gpsX = nbtTagCompound.getInteger("gpsX");
        gpsY = nbtTagCompound.getInteger("gpsY");
        gpsZ = nbtTagCompound.getInteger("gpsZ");
        fireOnlyOnRightAngle = nbtTagCompound.getBoolean("fireOnRightAngle");
        coordWithinReach = nbtTagCompound.getBoolean("targetWithinReach");
        // Read in the ItemStacks in the inventory from NBT
        NBTTagList tagList = nbtTagCompound.getTagList("Items", 10);
        inventory = new ItemStack[getSizeInventory()];
        for(int i = 0; i < tagList.tagCount(); ++i) {
            NBTTagCompound tagCompound = tagList.getCompoundTagAt(i);
            byte slot = tagCompound.getByte("Slot");
            if(slot >= 0 && slot < inventory.length) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(tagCompound);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbtTagCompound){
        super.writeToNBT(nbtTagCompound);
        nbtTagCompound.setBoolean("redstonePowered", redstonePowered);
        nbtTagCompound.setFloat("targetRotationAngle", targetRotationAngle);
        nbtTagCompound.setFloat("targetHeightAngle", targetHeightAngle);
        nbtTagCompound.setFloat("rotationAngle", rotationAngle);
        nbtTagCompound.setFloat("heightAngle", heightAngle);
        nbtTagCompound.setInteger("gpsX", gpsX);
        nbtTagCompound.setInteger("gpsY", gpsY);
        nbtTagCompound.setInteger("gpsZ", gpsZ);
        nbtTagCompound.setBoolean("fireOnRightAngle", fireOnlyOnRightAngle);
        nbtTagCompound.setBoolean("targetWithinReach", coordWithinReach);
        // Write the ItemStacks in the inventory to NBT
        NBTTagList tagList = new NBTTagList();
        for(int currentIndex = 0; currentIndex < inventory.length; ++currentIndex) {
            if(inventory[currentIndex] != null) {
                NBTTagCompound tagCompound = new NBTTagCompound();
                tagCompound.setByte("Slot", (byte)currentIndex);
                inventory[currentIndex].writeToNBT(tagCompound);
                tagList.appendTag(tagCompound);
            }
        }
        nbtTagCompound.setTag("Items", tagList);
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack){
        if(i == GPS_SLOT && itemstack != null && itemstack.getItem() != Itemss.GPSTool) return false;
        if(i > GPS_SLOT && i <= UPGRADE_SLOT_4 && itemstack != null && itemstack.getItem() != Itemss.machineUpgrade) return false;
        return true;
    }

    @Override
    // upgrades and GPS can be inserted/extracted from and into the bottom,
    // other sides are cannon slots.
    public int[] getAccessibleSlotsFromSide(int var1){
        if(var1 == 0) return new int[]{1, 2, 3, 4, 5};
        return new int[]{0};
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack itemstack, int side){
        return true;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack itemstack, int side){
        return true;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer var1){
        return isGuiUseableByPlayer(var1);
    }

    // REDSTONE BEHAVIOUR
    // ------------------------------------------------------------

    @Override
    public void handleGUIButtonPress(int buttonID, EntityPlayer player){
        fireOnlyOnRightAngle = !fireOnlyOnRightAngle;
        sendDescriptionPacket();
    }

    public void onNeighbourBlockChange(int x, int y, int z, Block block){
        if(!block.isAir(worldObj, x, y, z) && worldObj.isBlockIndirectlyGettingPowered(x, y, z) && !redstonePowered && (!fireOnlyOnRightAngle || doneTurning)) {
            fire();
            redstonePowered = true;
        } else if(!worldObj.isBlockIndirectlyGettingPowered(x, y, z) && redstonePowered) {
            redstonePowered = false;
        }
    }

    private synchronized boolean fire(){
        double[] velocity = getVelocityVector(heightAngle, rotationAngle, getForce());
        if(getPressure(ForgeDirection.UNKNOWN) >= PneumaticValues.MIN_PRESSURE_AIR_CANNON && inventory[0] != null) {
            addAir((int)(-500 * getForce()), ForgeDirection.UNKNOWN);
            Entity itemShot = getPayloadEntity();
            itemShot.setPosition(xCoord + 0.5D, yCoord + 1.8D, zCoord + 0.5D);
            if(itemShot instanceof EntityFireball) {
                velocity[0] *= 0.05D;
                velocity[1] *= 0.05D;
                velocity[2] *= 0.05D;
            }

            itemShot.motionX = velocity[0];
            itemShot.motionY = velocity[1];
            itemShot.motionZ = velocity[2];
            if(!worldObj.isRemote) worldObj.spawnEntityInWorld(itemShot);

            if(itemShot instanceof EntityItem) {
                inventory[0] = null;
            } else {
                inventory[0].stackSize--;
                if(inventory[0].stackSize <= 0) inventory[0] = null;
            }
            for(int i = 0; i < 10; i++) {
                double velX = velocity[0] * 0.4D + (rand.nextGaussian() - 0.5D) * 0.05D;
                double velY = velocity[1] * 0.4D + (rand.nextGaussian() - 0.5D) * 0.05D;
                double velZ = velocity[2] * 0.4D + (rand.nextGaussian() - 0.5D) * 0.05D;
                NetworkHandler.sendToAllAround(new PacketSpawnParticle("largesmoke", xCoord + 0.5D, yCoord + 0.7D, zCoord + 0.5D, velX, velY, velZ), worldObj);
            }
            NetworkHandler.sendToAllAround(new PacketPlaySound(Sounds.CANNON_SOUND, xCoord, yCoord, zCoord, 1.0F, rand.nextFloat() / 4F + 0.75F, true), worldObj);
            return true;
        } else {
            return false;
        }
    }

    // warning: no null-check for inventory slot 0
    private Entity getPayloadEntity(){

        if(getUpgrades(ItemMachineUpgrade.UPGRADE_DISPENSER_DAMAGE, getUpgradeSlots()) > 0) {
            Item item = inventory[0].getItem();
            if(item == Item.getItemFromBlock(Blocks.tnt)) {
                EntityTNTPrimed tnt = new EntityTNTPrimed(worldObj);
                tnt.fuse = 80;
                return tnt;
            } else if(item == Items.experience_bottle) return new EntityExpBottle(worldObj);
            else if(item == Items.potionitem) {
                EntityPotion potion = new EntityPotion(worldObj);
                potion.setPotionDamage(inventory[0].getItemDamage());
                return potion;
            } else if(item == Items.arrow) return new EntityArrow(worldObj);
            else if(item == Items.egg) return new EntityEgg(worldObj);
            // else if(itemID == Item.fireballCharge) return new
            // EntitySmallFireball(worldObj);
            else if(item == Items.snowball) return new EntitySnowball(worldObj);
            else if(item == Items.spawn_egg) return ItemMonsterPlacer.spawnCreature(worldObj, inventory[0].getItemDamage(), 0, 0, 0);
            else if(item == Items.minecart) return new EntityMinecartEmpty(worldObj);
            else if(item == Items.chest_minecart) return new EntityMinecartChest(worldObj);
            else if(item == Items.furnace_minecart) return new EntityMinecartFurnace(worldObj);
            else if(item == Items.hopper_minecart) return new EntityMinecartHopper(worldObj);
            else if(item == Items.tnt_minecart) return new EntityMinecartTNT(worldObj);
            else if(item == Items.boat) return new EntityBoat(worldObj);

        }
        EntityItem item = new EntityItem(worldObj);
        item.setEntityItemStack(inventory[0].copy());
        item.age = 4800; // 1200 ticks left to live, = 60s.
        item.lifespan += Math.min(getUpgrades(ItemMachineUpgrade.UPGRADE_ITEM_LIFE, getUpgradeSlots()) * 600, 4800); // add
        // 30s
        // for
        // each
        // life
        // upgrade,
        // to
        // the
        // max
        // of
        // 5
        // min.
        return item;

    }

    @Override
    public boolean hasCustomInventoryName(){
        return true;
    }

    @Override
    public void openInventory(){}

    @Override
    public void closeInventory(){}

    /*
     *  COMPUTERCRAFT API
     */

    @Override
    public String getType(){
        return "airCannon";
    }

    @Override
    @Optional.Method(modid = ModIds.COMPUTERCRAFT)
    protected void addLuaMethods(){
        super.addLuaMethods();
        luaMethods.add(new LuaConstant("getMinWorkingPressure", PneumaticValues.MIN_PRESSURE_AIR_CANNON));

        luaMethods.add(new LuaMethod("setTargetLocation"){
            @Override
            public Object[] call(IComputerAccess computer, ILuaContext context, Object[] args) throws LuaException, InterruptedException{
                if(args.length == 3) {
                    gpsX = ((Double)args[0]).intValue();
                    gpsY = ((Double)args[1]).intValue();
                    gpsZ = ((Double)args[2]).intValue();
                    updateDestination();
                    return new Object[]{coordWithinReach};
                } else {
                    throw new IllegalArgumentException("setTargetLocation requires 3 parameters (x,y,z)");
                }
            }
        });

        luaMethods.add(new LuaMethod("fire"){
            @Override
            public Object[] call(IComputerAccess computer, ILuaContext context, Object[] args) throws LuaException, InterruptedException{
                if(args.length == 0) {
                    return new Object[]{fire()};//returns true if the fire succeeded.
                } else {
                    throw new IllegalArgumentException("fire doesn't take any arguments!");
                }
            }
        });
        luaMethods.add(new LuaMethod("isDoneTurning"){
            @Override
            public Object[] call(IComputerAccess computer, ILuaContext context, Object[] args) throws LuaException, InterruptedException{
                if(args.length == 0) {
                    return new Object[]{doneTurning};
                } else {
                    throw new IllegalArgumentException("isDoneTurning doesn't take any arguments!");
                }
            }
        });

        luaMethods.add(new LuaMethod("setRotationAngle"){
            @Override
            public Object[] call(IComputerAccess computer, ILuaContext context, Object[] args) throws LuaException, InterruptedException{
                if(args.length == 1) {
                    setTargetAngles(((Double)args[0]).floatValue(), targetHeightAngle);
                    return null;
                } else {
                    throw new IllegalArgumentException("setRotationAngle does take one argument!");
                }
            }
        });

        luaMethods.add(new LuaMethod("setHeightAngle"){
            @Override
            public Object[] call(IComputerAccess computer, ILuaContext context, Object[] args) throws LuaException, InterruptedException{
                if(args.length == 1) {
                    setTargetAngles(targetRotationAngle, 90 - ((Double)args[0]).floatValue());
                    return null;
                } else {
                    throw new IllegalArgumentException("setHeightAngle does take one argument!");
                }
            }
        });

        luaMethods.add(new LuaMethod("setExternalControl"){
            @Override
            public Object[] call(IComputerAccess computer, ILuaContext context, Object[] args) throws LuaException, InterruptedException{
                if(args.length == 1) {
                    externalControl = (Boolean)args[0];
                    return null;
                } else {
                    throw new IllegalArgumentException("setExternalControl does take one argument!");
                }
            }
        });
    }
}
