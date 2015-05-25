package bettersand;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameData;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
        Item sandBucket = new BucketUnit(sandUnit, UNIT).setUnlocalizedName("bucketSand").setContainerItem(FluidContainerRegistry.EMPTY_BUCKET.getItem());
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
        if(Arrays.binarySearch(generate, populate.world.provider.getDimensionId()) >= 0){
            int xMin = populate.chunkX * 16 + 8, xMax = xMin + 16;
            int zMin = populate.chunkZ * 16 + 8, zMax = zMin + 16;
            for(int x = xMin; x < xMax; x++) {
                for (int z = zMin; z < zMax; z++) {
                    BlockPos pos = populate.world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).down();
                    IBlockState state = populate.world.getBlockState(pos);
                    if (isDefaultSand(state) && isAirAround(populate.world, pos)) {
                        populate.world.setBlockState(pos, getFilledSand());
                    }
                }
            }
        }
    }

    private IBlockState getFilledSand(){
        return sandUnit.getBlockState().getBaseState().withProperty(Finite.LEVEL, UNIT - 1);
    }

    private boolean isDefaultSand(IBlockState state){
        return state.getBlock() == Blocks.sand && state.getBlock().getMetaFromState(state) == 0;
    }

    /**
     * Check for air blocks on all sides.
     *
     * @param world to search in
     * @param pos to search around
     * @return true if at least on side is adjacent to an air block
     */
    private boolean isAirAround(World world, BlockPos pos){
        return world.isAirBlock(pos.west()) || world.isAirBlock(pos.east()) || world.isAirBlock(pos.north()) || world.isAirBlock(pos.south());
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
            Block block = event.world.getBlockState(event.target.getBlockPos()).getBlock();
            if(block.getMaterial() == SAND_MATERIAL && block instanceof IFluidBlock) {
                ItemStack result = FluidContainerRegistry.fillFluidContainer(((IFluidBlock) block).drain(event.world, event.target.getBlockPos(), false), event.current);
                if(result!=null) {
                    ((IFluidBlock) block).drain(event.world, event.target.getBlockPos(), true);
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
        if(isDefaultSand(breakEvent.state)){
            if(breakEvent.getPlayer()!=null && EnchantmentHelper.getSilkTouchModifier(breakEvent.getPlayer()))
                return;
            breakEvent.setCanceled(true);
            breakEvent.world.setBlockState(breakEvent.pos, getFilledSand());
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
            setRenderLayer(EnumWorldBlockLayer.CUTOUT);//Check this ?
        }

        @Override
        public void updateTick(World world, BlockPos pos, IBlockState state, Random rand){
            if(world.getBlockState(pos.up()).getBlock().getMaterial() == Material.sand){
                world.setBlockState(pos.up(), state.withProperty(LEVEL, quantaPerBlock-1));
            }
            super.updateTick(world, pos, state, rand);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state)
        {
            return new AxisAlignedBB(pos, pos).addCoord(1, getFilledPercentage(world, pos) * 0.65D, 1);
        }
    }
}
