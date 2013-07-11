package icbm.gangshao.turret.mount;

import icbm.api.explosion.IExplosive;
import icbm.core.ZhuYaoICBM;
import icbm.core.di.IRedstoneReceptor;
import icbm.gangshao.ProjectileType;
import icbm.gangshao.ZhuYaoGangShao;

import java.util.HashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.core.electricity.ElectricityPack;
import universalelectricity.core.vector.Vector3;
import universalelectricity.prefab.network.IPacketReceiver;
import calclavia.lib.CalculationHelper;
import calclavia.lib.multiblock.IMultiBlock;
import calclavia.lib.multiblock.TileEntityMulti;

/**
 * Railgun
 * 
 * @author Calclavia
 */
public class TCiGuiPao extends TPaoTaiQi implements IPacketReceiver, IRedstoneReceptor, IMultiBlock
{
	private int gunChargingTicks = 0;

	private boolean redstonePowerOn = false;
	/** Is current ammo antimatter */
	private boolean isAntimatter;

	private float explosionSize;

	private int explosionDepth;

	/** A counter used client side for the smoke and streaming effects of the Railgun after a shot. */
	private int endTicks = 0;

	public TCiGuiPao()
	{
		this.baseFiringDelay = 80;
		this.minFiringDelay = 50;

		this.maxPitch = 60;
		this.minPitch = -60;
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.getPlatform() != null)
		{
			if (this.redstonePowerOn)
			{
				this.tryActivateWeapon();
			}

			if (this.gunChargingTicks > 0)
			{
				this.gunChargingTicks++;

				if (this.gunChargingTicks >= this.getFireDelay())
				{
					this.onFire();
					this.gunChargingTicks = 0;
				}
			}

			if (this.worldObj.isRemote && this.endTicks-- > 0)
			{
				MovingObjectPosition objectMouseOver = this.rayTrace(2000);

				if (objectMouseOver != null && objectMouseOver.hitVec != null)
				{
					this.drawParticleStreamTo(new Vector3(objectMouseOver.hitVec));
				}
			}
		}
	}

	@Override
	public void tryActivateWeapon()
	{
		if (this.canActivateWeapon() && this.gunChargingTicks == 0)
		{
			this.onWeaponActivated();
		}
	}

	@SuppressWarnings("unchecked")
	public void onFire()
	{
		if (!this.worldObj.isRemote)
		{
			while (this.explosionDepth > 0)
			{
				MovingObjectPosition objectMouseOver = this.rayTrace(2000);

				if (objectMouseOver != null)
				{
					if (!ZhuYaoGangShao.isProtected(this.worldObj, new Vector3(objectMouseOver), ZhuYaoGangShao.FLAG_RAILGUN))
					{
						if (this.isAntimatter)
						{
							/** Remove Redmatter Explosions. */
							int radius = 50;
							AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(objectMouseOver.blockX - radius, objectMouseOver.blockY - radius, objectMouseOver.blockZ - radius, objectMouseOver.blockX + radius, objectMouseOver.blockY + radius, objectMouseOver.blockZ + radius);
							List<Entity> missilesNearby = worldObj.getEntitiesWithinAABB(Entity.class, bounds);

							for (Entity entity : missilesNearby)
							{
								if (entity instanceof IExplosive)
								{
									entity.setDead();
								}
							}
						}

						Vector3 blockPosition = new Vector3(objectMouseOver.hitVec);

						int blockID = blockPosition.getBlockID(this.worldObj);
						Block block = Block.blocksList[blockID];

						// Any hardness under zero is unbreakable
						if (block != null && block.getBlockHardness(this.worldObj, blockPosition.intX(), blockPosition.intY(), blockPosition.intZ()) != -1)
						{
							this.worldObj.setBlock(objectMouseOver.blockX, objectMouseOver.blockY, objectMouseOver.blockZ, 0, 0, 2);
						}

						Entity responsibleEntity = this.entityFake != null ? this.entityFake.riddenByEntity : null;
						this.worldObj.newExplosion(responsibleEntity, blockPosition.x, blockPosition.y, blockPosition.z, explosionSize, true, true);
					}
				}

				this.explosionDepth--;
			}
		}
	}

	@Override
	public void renderShot(Vector3 target)
	{
		this.endTicks = 20;
	}

	@Override
	public void playFiringSound()
	{
		this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, ZhuYaoICBM.PREFIX + "railgun", 5F, 1F);
	}

	@Override
	public float getVoltage()
	{
		return 220;
	}

	@Override
	public void onDestroy(TileEntity callingBlock)
	{
		this.worldObj.setBlock(this.xCoord, this.yCoord, this.zCoord, 0);
		this.worldObj.setBlock(this.xCoord, this.yCoord + 1, this.zCoord, 0);
	}

	@Override
	public void onCreate(Vector3 position)
	{
		this.worldObj.setBlock(position.intX(), position.intY() + 1, position.intZ(), ZhuYaoICBM.bJia.blockID, 0, 2);
		((TileEntityMulti) this.worldObj.getBlockTileEntity(position.intX(), position.intY() + 1, position.intZ())).setMainBlock(position);
	}

	@Override
	public Vector3 getCenter()
	{
		return new Vector3(this).add(new Vector3(0.5, 1.5, 0.5));
	}

	@Override
	public Vector3 getMuzzle()
	{
		return this.getCenter().add(Vector3.multiply(CalculationHelper.getDeltaPositionFromRotation(this.currentRotationYaw, this.currentRotationPitch), 1.6));
	}

	@Override
	public void onPowerOn()
	{
		this.redstonePowerOn = true;
	}

	@Override
	public void onPowerOff()
	{
		this.redstonePowerOn = false;
	}

	@Override
	public float getFiringRequest()
	{
		return 1000000;
	}

	@Override
	public void onWeaponActivated()
	{
		super.onWeaponActivated();
		this.gunChargingTicks = 1;
		this.redstonePowerOn = false;
		this.isAntimatter = false;
		ItemStack ammoStack = this.getPlatform().hasAmmunition(ProjectileType.RAILGUN);

		if (ammoStack != null)
		{
			if (ammoStack.equals(ZhuYaoGangShao.antimatterBullet) && this.getPlatform().useAmmunition(ammoStack))
			{
				this.isAntimatter = true;
			}
			else
			{
				this.getPlatform().useAmmunition(ammoStack);
			}
		}

		this.getPlatform().provideElectricity(ForgeDirection.UP, ElectricityPack.getFromWatts(this.getFiringRequest(), this.getVoltage()), true);

		this.explosionSize = 5f;
		this.explosionDepth = 5;

		if (this.isAntimatter)
		{
			this.explosionSize = 8f;
			this.explosionDepth = 10;
		}

		this.playFiringSound();
	}

	@Override
	public boolean canActivateWeapon()
	{
		if (this.getPlatform() != null)
		{
			if (this.getPlatform().hasAmmunition(ProjectileType.RAILGUN) != null)
			{
				if (this.getPlatform().provideElectricity(ForgeDirection.UP, ElectricityPack.getFromWatts(this.getFiringRequest(), this.getVoltage()), false).getWatts() >= this.getFiringRequest())
				{
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public float addInformation(HashMap<String, Integer> map, EntityPlayer player)
	{
		super.addInformation(map, player);
		return 2;
	}

	@Override
	public int getMaxHealth()
	{
		return 450;
	}
}