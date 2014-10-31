package bettersand;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.*;

import java.util.Random;

@Mod(modid="BetterSand", name= "Better Sand")
public class BetterSand {
    public static final int UNIT = 3;
    public static final Material SAND_MATERIAL = new MaterialLiquid(MapColor.sandColor){
        @Override
        public boolean isOpaque(){return true;}
    };
    Fluid liquidSand;
    Block sandUnit;
    Item sandBucket;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event){
        if(!FluidRegistry.isFluidRegistered("sand")) {
            liquidSand = new Fluid("sand") {
                @Override
                public String getUnlocalizedName() {
                    return "tile.sand.name";
                }
                @Override
                public IIcon getStillIcon()
                {
                    return Blocks.sand.getIcon(0, 0);
                }
                @Override
                public IIcon getFlowingIcon()
                {
                    return Blocks.sand.getIcon(0, 0);
                }
            }.setDensity(3000).setViscosity(4000);
            FluidRegistry.registerFluid(liquidSand);
        }else{
            liquidSand = FluidRegistry.getFluid("sand");
        }
        if(liquidSand.canBePlacedInWorld()){
            sandUnit = liquidSand.getBlock();
        }else{
            sandUnit = new BlockFluidFinite(liquidSand, SAND_MATERIAL){
                @Override
                public void updateTick(World world, int x, int y, int z, Random rand){
                    if(world.getBlock(x, y+1, z).getMaterial()==Material.sand){
                        world.setBlock(x, y+1, z, this, this.quantaPerBlock-1, 3);
                    }
                    super.updateTick(world, x, y, z, rand);
                }
                @Override
                public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z)
                {
                    return AxisAlignedBB.getBoundingBox((double)x, (double)y, (double)z, (double)x + 1.0D, (double)y + (double) getFilledPercentage(world, x, y, z)*0.65F, (double)z + 1.0D);
                }
            }.setQuantaPerBlock(UNIT).setBlockTextureName("sand");
            GameRegistry.registerBlock(sandUnit, "sand");
        }
        sandBucket = new ItemBucket(sandUnit).setUnlocalizedName("bucketSand").setContainerItem(FluidContainerRegistry.EMPTY_BUCKET.getItem()).setTextureName("bucket_sand");
        if(FluidContainerRegistry.registerFluidContainer(liquidSand, new ItemStack(sandBucket), FluidContainerRegistry.EMPTY_BUCKET)){
            GameRegistry.registerItem(sandBucket, "sandBucket");
        }
        MinecraftForge.EVENT_BUS.register(this);
        GameRegistry.addShapelessRecipe(new ItemStack(Blocks.sand), sandBucket);
    }

    /**
     * Place random patch of sand in the overworld
     */
    @SubscribeEvent
    public void onPopulating(PopulateChunkEvent.Post populate){
        if(populate.world.provider.dimensionId==0){
            int x = populate.chunkX*16 + populate.rand.nextInt(16) + 8;
            int z = populate.chunkZ*16 + populate.rand.nextInt(16) + 8;
            int y = populate.world.getTopSolidOrLiquidBlock(x, z) - 1;
            if(populate.world.getBlock(x, y, z).getMaterial()==Material.sand){
                populate.world.setBlock(x, y + 1, z, sandUnit, 2*(UNIT-1), 3);
            }
        }
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
            if(block.getMaterial()==SAND_MATERIAL && block instanceof IFluidBlock) {
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
        if(breakEvent.block == Blocks.sand){
            breakEvent.setCanceled(true);
            breakEvent.world.setBlock(breakEvent.x, breakEvent.y, breakEvent.z, sandUnit, UNIT-1, 3);
        }
    }
}
