package org.upnp.alljoynservice.Device;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusInterface;
import org.alljoyn.bus.annotation.BusSignal;
//import org.alljoyn.bus.annotation.BusProperty;
//import org.alljoyn.bus.annotation.BusSignal;
import org.alljoyn.bus.annotation.BusMethod;

@BusInterface(name = "org.upnp.alljoynservice.DeviceInterface")
public interface DeviceInterface
{

	@BusMethod
	void testRPC(String recvInfo) throws BusException;

	@BusMethod
	String requireStrInfo() throws BusException;

	@BusMethod
	void getStrInfo(String recvInfo) throws BusException;

	@BusMethod
	boolean getNotifyStatus() throws BusException;

	@BusMethod
	boolean getRecvStatus() throws BusException;
	
    @BusSignal
    public void sendInfoOnSignal(String str) throws BusException;
}
