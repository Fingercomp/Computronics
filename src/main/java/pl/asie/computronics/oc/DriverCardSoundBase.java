package pl.asie.computronics.oc;

import cpw.mods.fml.common.network.NetworkRegistry;
import li.cil.oc.api.driver.EnvironmentHost;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.prefab.ManagedEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import pl.asie.computronics.Computronics;
import pl.asie.computronics.network.PacketType;
import pl.asie.computronics.reference.Config;
import pl.asie.computronics.util.sound.Audio;
import pl.asie.computronics.util.sound.AudioType;
import pl.asie.lib.network.Packet;

import java.io.IOException;

/**
 * @author Vexatos
 */
public abstract class DriverCardSoundBase extends ManagedEnvironment {

	protected final EnvironmentHost host;
	protected final Long[] expirationList;
	protected final String playMethodName;

	public DriverCardSoundBase(EnvironmentHost host, String playMethodName) {
		this.host = host;
		this.expirationList = new Long[8];
		this.playMethodName = playMethodName;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	protected FreqPair[] sendBuffer;

	@Override
	public void update() {
		final long currentTime = host.world().getTotalWorldTime();
		for(int i = 0; i < expirationList.length; i++) {
			if(expirationList[i] != null && expirationList[i] <= currentTime) {
				expirationList[i] = null;
			}
		}
		if(sendBuffer != null) {
			try {
				sendSound(host.world(), host.xPosition(),
					host.yPosition(), host.zPosition(), sendBuffer);
			} catch(Exception e) {
				e.printStackTrace();
			}
			sendBuffer = null;
		}
	}

	protected int getActiveChannelCount() {
		return getNonNullCount(expirationList);
	}

	protected static class FreqPair {

		protected final int frequency;
		protected final int duration;

		protected FreqPair(int frequency, int duration) {
			this.frequency = frequency;
			this.duration = duration;
		}
	}

	protected static <E, T extends Iterable<E>> int getNonNullCount(T array) {
		int c = 0;
		for(E e : array) {
			if(e != null) {
				++c;
			}
		}
		return c;
	}

	protected static <E> int getNonNullCount(E[] array) {
		int c = 0;
		for(E e : array) {
			if(e != null) {
				++c;
			}
		}
		return c;
	}

	protected Object[] tryConsumeEnergy(double v, String methodName) {
		if(this.node() instanceof Connector) {
			int power = this.tryConsumeEnergy(v);
			if(power < 0) {
				return new Object[] { false, power + ": " + methodName + ": not enough energy available: required"
					+ v + ", found " + ((Connector) node()).globalBuffer() };
			}
		}
		return null;
	}

	protected Object[] tryQueueSound(FreqPair[] freqPairs, Object[] result, double v, String methodName) throws Exception {
		Object[] error = tryConsumeEnergy(v, methodName);
		if(error != null) {
			return error;
		}
		if(sendBuffer == null) {
			sendBuffer = freqPairs;
		} else {
			int size = Math.min(sendBuffer.length, freqPairs.length);
			for(int i = 0; i < size; i++) {
				if(sendBuffer[i] == null) {
					sendBuffer[i] = freqPairs[i];
				}
			}
		}
		return result;
	}

	protected void sendSound(World world, double x, double y, double z, FreqPair[] freqPairs) throws Exception {
		final int size = Math.min(freqPairs.length, 8);
		byte hits = 0;
		for(int i = 0; i < size; i++) {
			if(freqPairs[i] != null) {
				hits |= 1 << i;
			}
		}
		Packet packet = Computronics.packet.create(PacketType.COMPUTER_BEEP.ordinal())
			.writeInt(world.provider.dimensionId)
			.writeInt(MathHelper.floor_double(x))
			.writeInt(MathHelper.floor_double(y))
			.writeInt(MathHelper.floor_double(z))
			.writeByte(hits);
		for(int i = 0; i < freqPairs.length; i++) {
			FreqPair freqPair = freqPairs[i];
			if(freqPair != null) {
				packet
					.writeByte((byte) getMode(i).ordinal())
					.writeShort((short) freqPair.frequency)
					.writeShort((short) freqPair.duration);
			}
		}
		Computronics.packet.sendToAllAround(packet, new NetworkRegistry.TargetPoint(
			world.provider.dimensionId,
			MathHelper.floor_double(x),
			MathHelper.floor_double(y),
			MathHelper.floor_double(z), Config.SOUND_RADIUS));

	}

	protected abstract AudioType getMode(int channel);

	public static void onSound(Packet packet, EntityPlayer player) throws IOException {
		int dimension = packet.readInt();
		if(isInDimension(player, dimension)) {
			int x = packet.readInt();
			int y = packet.readInt();
			int z = packet.readInt();
			int hits = packet.readUnsignedByte();
			for(int i = 0; i < 8; i++) {
				if(((hits >> i) & 1) == 1) {
					AudioType type = AudioType.fromIndex(packet.readUnsignedByte());
					short frequency = packet.readShort();
					short duration = packet.readShort();
					Audio.instance().play(x + 0.5f, y + 0.5f, z + 0.5f, type, frequency & 0xFFFF, duration & 0xFFFF);
				}
			}
		}
	}

	protected int tryConsumeEnergy(double v) {
		if(v < 0) {
			return -2;
		}
		v = -v;
		if(this.node() instanceof Connector) {
			Connector connector = ((Connector) this.node());
			return connector.tryChangeBuffer(v) ? 1 : -1;
		}
		return 0;
	}

	protected static double optDouble(Number value, double def) {
		if(value == null) {
			return def;
		}
		return value.doubleValue();
	}

	protected static boolean isInDimension(EntityPlayer player, int dimension) {
		return player != null && player.worldObj != null && player.worldObj.provider != null
			&& player.worldObj.provider.dimensionId == dimension;
	}

}
