package bettersand;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBucket;
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
    public boolean tryPlaceContainedLiquid(World world, int x, int y, int z)
    {
        Block block = world.getBlock(x, y, z);
        boolean flag = !block.getMaterial().isSolid();

        if (!block.isAir(world, x, y, z) && !flag)
        {
            return false;
        }
        else
        {
            if (!world.isRemote && flag && !block.getMaterial().isLiquid())
            {
                world.func_147480_a(x, y, z, true);
            }
            world.setBlock(x, y, z, this.fluid, this.quantity, 3);

            return true;
        }
    }
}
