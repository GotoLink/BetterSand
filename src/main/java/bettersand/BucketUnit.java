package bettersand;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBucket;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * Created by GotoLink on 01/11/2014.
 * A bucket that contains a specific amount of block data
 */
public class BucketUnit extends ItemBucket{
    public final int quantity;
    public final Block fluid;
    public BucketUnit(Block block, int unit){
        super(block);
        if(block==null || unit<=0)
            throw new IllegalArgumentException("Couldn't built valid bucket unit");
        this.quantity = unit - 1;
        this.fluid = block;
    }

    @Override
    public boolean tryPlaceContainedLiquid(World world, BlockPos pos)
    {
        Block block = world.getBlockState(pos).getBlock();
        boolean flag = !block.getMaterial().isSolid();

        if (!block.isAir(world, pos) && !flag)
        {
            return false;
        }
        else
        {
            if (!world.isRemote && flag && !block.getMaterial().isLiquid())
            {
                world.destroyBlock(pos, true);
            }
            world.setBlockState(pos, this.fluid.getBlockState().getBaseState().withProperty(BetterSand.Finite.LEVEL, this.quantity));

            return true;
        }
    }
}
