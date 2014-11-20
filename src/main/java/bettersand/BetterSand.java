package bettersand;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.*;

import java.util.Arrays;
import java.util.Random;

@Mod(modid="BetterSand", name= "Better Sand")
public final class BetterSand {
    /**
     * The name used for flowing sand, and corresponding fluid in registries
     */
    public static final String REGISTRY = "sand";
    /**
     * Number of parts a sand block can be split into
     */
    public static final int UNIT = 3;
    /**
     * The material that flowing sand is made of: an opaque liquid
     */
    public static final Material SAND_MATERIAL = new MaterialLiquid(MapColor.sandColor){
        @Override
        public boolean isOpaque(){return true;}
    };
    /**
     * Flowing sand block
     */
    private Block sandUnit;
    /**
     * Dimensions where generation occurs
     */
    private int[] generate = new int[]{0};

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event){
        boolean craft = true;
        try {
            Configuration config = new Configuration(event.getSuggestedConfigurationFile());
            generate = config.get("World Generation", "Modded sand generate in world", generate, "Select dimension ids in which special sand will generate. Default is overworld.").getIntList();
            craft = config.getBoolean("Craft sand bucket into sand", "Crafting", craft, "Set to false to remove crafting recipe");
            config.save();
        }catch (Exception ignored){}
        Arrays.sort(generate);
        Fluid liquidSand;
        if(!FluidRegistry.isFluidRegistered(REGISTRY)) {
            liquidSand = new BlockFluid(REGISTRY).setDensity(3000).setViscosity(4000);
            FluidRegistry.registerFluid(liquidSand);
        }else{
            liquidSand = FluidRegistry.getFluid(REGISTRY);
        }
        if(liquidSand.canBePlacedInWorld()){
            sandUnit = liquidSand.getBlock();
        }else{
            sandUnit = new Finite(liquidSand).setQuantaPerBlock(UNIT).setBlockTextureName("sand");
            GameRegistry.registerBlock(sandUnit, REGISTRY);
        }
        Item sandBucket = new BucketUnit(sandUnit, UNIT).setUnlocalizedName("bucketSand").setContainerItem(FluidContainerRegistry.EMPTY_BUCKET.getItem()).setTextureName("bucket_sand");
        if(FluidContainerRegistry.registerFluidContainer(liquidSand, new ItemStack(sandBucket), FluidContainerRegistry.EMPTY_BUCKET)){
            GameRegistry.registerItem(sandBucket, REGISTRY+"Bucket");
            if(craft)
                GameRegistry.addShapelessRecipe(new ItemStack(Blocks.sand), sandBucket);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Replace sand blocks on top of the selected dimension ids
     * Note: doesn't trigger in flat worlds
     */
    @SubscribeEvent
    public void onPopulating(PopulateChunkEvent.Post populate){
        if(Arrays.binarySearch(generate, populate.world.provider.dimensionId) >= 0){
            int xMin = populate.chunkX * 16 + 8, xMax = xMin + 16;
            int zMin = populate.chunkZ * 16 + 8, zMax = zMin + 16;
            for(int x = xMin; x < xMax; x++) {
                for (int z = zMin; z < zMax; z++) {
                    int y = populate.world.getTopSolidOrLiquidBlock(x, z) - 1;
                    if (populate.world.getBlock(x, y, z) == Blocks.sand && populate.world.getBlockMetadata(x, y, z) == 0 && isAirAround(populate.world, x, y, z)) {
                        populate.world.setBlock(x, y, z, sandUnit, (UNIT - 1), 3);
                    }
                }
            }
        }
    }

    /**
     * Check for air blocks on all sides.
     *
     * @param world to search in
     * @param x to search around
     * @param y to search around
     * @param z to search around
     * @return true if at least on side is adjacent to an air block
     */
    private boolean isAirAround(World world, int x, int y, int z){
        return world.isAirBlock(x-1, y, z) || world.isAirBlock(x+1, y, z) || world.isAirBlock(x, y, z-1) || world.isAirBlock(x, y, z+1);
    }

    /**
     * Handle entity interaction with sand
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void inSand(LivingEvent.LivingUpdateEvent event){
        if(event.entity.isEntityAlive() && event.entity.isInsideOfMaterial(SAND_MATERIAL)){
            event.entity.attackEntityFrom(DamageSource.inWall, 1.0F);
        }
    }

    /**
     * Handle bucket usage
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBucketFill(FillBucketEvent event){
        if (event.target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && FluidContainerRegistry.isEmptyContainer(event.current)) {
            int i = event.target.blockX;
            int j = event.target.blockY;
            int k = event.target.blockZ;
            Block block = event.world.getBlock(i, j, k);
            if(block.getMaterial() == SAND_MATERIAL && block instanceof IFluidBlock) {
                ItemStack result = FluidContainerRegistry.fillFluidContainer(((IFluidBlock) block).drain(event.world, i, j, k, false), event.current);
                if(result!=null) {
                    ((IFluidBlock) block).drain(event.world, i, j, k, true);
                    event.setResult(Event.Result.ALLOW);
                    event.result = result;
                }
            }
        }
    }

    /**
     * Handle sand blocks broken by player
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSandBreak(BlockEvent.BreakEvent breakEvent){
        if(breakEvent.block == Blocks.sand && breakEvent.world.getBlockMetadata(breakEvent.x, breakEvent.y, breakEvent.z) == 0){
            if(breakEvent.getPlayer()!=null && EnchantmentHelper.getSilkTouchModifier(breakEvent.getPlayer()))
                return;
            breakEvent.setCanceled(true);
            breakEvent.world.setBlock(breakEvent.x, breakEvent.y, breakEvent.z, sandUnit, UNIT-1, 3);
        }
    }

    public static class BlockFluid extends Fluid{
        public BlockFluid(String name) {
            super(name);
            setIcons(GameData.getBlockRegistry().getObject(name).getIcon(0, 0));
        }

        @Override
        public String getUnlocalizedName() {
            return "tile."+getName()+".name";
        }
    }

    public static class Finite extends BlockFluidFinite{

        public Finite(Fluid fluid) {
            super(fluid, SAND_MATERIAL);
        }

        @Override
        public void updateTick(World world, int x, int y, int z, Random rand){
            if(world.getBlock(x, y+1, z).getMaterial() == Material.sand){
                world.setBlock(x, y+1, z, this, this.quantaPerBlock-1, 3);
            }
            super.updateTick(world, x, y, z, rand);
        }
        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z)
        {
            return AxisAlignedBB.getBoundingBox((double)x, (double)y, (double)z, (double)x + 1.0D, (double)y + (double) getFilledPercentage(world, x, y, z)*0.65F, (double)z + 1.0D);
        }
    }
}
