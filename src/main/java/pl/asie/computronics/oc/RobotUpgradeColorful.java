package pl.asie.computronics.oc;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.EnvironmentHost;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.NBTTagCompound;
import pl.asie.computronics.reference.Config;

/**
 * @author Vexatos
 */
public class RobotUpgradeColorful extends ManagedEnvironmentWithComponentConnector {
	private final EnvironmentHost host;
	private int color = -1;
	private boolean needsUpdate;

	public RobotUpgradeColorful(EnvironmentHost host) {
		this.host = host;
		ComponentConnector node = Network.newNode(this, Visibility.Network).withConnector().withComponent("colors", Visibility.Neighbors).create();
		this.setNode(node);
	}

	@Callback(doc = "function(color:number):boolean; Sets the color of the robot. Returns true on success, false and an error message otherwise", direct = true)
	public Object[] setColor(Context context, Arguments args) {
		int color = args.checkInteger(0);
		if(color >= 0 && color <= 0xFFFFFF) {
			if(node.tryChangeBuffer(-Config.COLOR_CHANGE_COST)) {
				setColor(color);
				return new Object[] { true };
			}
			return new Object[] { false, "not enough energy" };
		}
		return new Object[] { false, "number must be between 0 and 16777215" };
	}

	@Callback(doc = "function():number; Returns the color of the robot.", direct = true)
	public Object[] getColor(Context context, Arguments args) {
		return new Object[] { getColor() };
	}

	@Callback(doc = "function():boolean; Resets the colour of the robot. Returns true on success, false and an error message otherwise", direct = true)
	public Object[] resetColor(Context context, Arguments args) {
		if(node.tryChangeBuffer(-Config.COLOR_CHANGE_COST)) {
			setColor(-1);
			return new Object[] { true };
		}
		return new Object[] { false, "not enough energy" };
	}

	public int getColor() {
		return this.color;
	}

	public void setColor(int color) {
		this.color = color;
		needsUpdate = true;
	}

	protected void updateClient() {
		try {
			if(host instanceof Robot) {
				((Robot) host).synchronizeSlot(((Robot) host).componentSlot(node.address()));
			}
		} catch(NullPointerException e) {
			// NO-OP
		}
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void update() {
		if(needsUpdate) {
			updateClient();
			needsUpdate = false;
		}
	}

	@Override
	public void load(NBTTagCompound nbt) {
		super.load(nbt);
		if(nbt.hasKey("computronics:color")) {
			this.color = nbt.getInteger("computronics:color");
		}
		this.needsUpdate = true;
	}

	@Override
	public void save(NBTTagCompound nbt) {
		super.save(nbt);
		nbt.setInteger("computronics:color", this.color);
	}
}
