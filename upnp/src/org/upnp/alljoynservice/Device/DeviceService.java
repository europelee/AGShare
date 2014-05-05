package org.upnp.alljoynservice.Device;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusMethod;
import org.alljoyn.bus.annotation.BusSignal;

public class DeviceService implements DeviceInterface, BusObject
{

	private String mTestRPCStr = "";

	private/* volatile */String mReqStr = ""; // need atom operator
	private/* volatile */String mGetStr = ""; // need atom operator

	private volatile boolean mIsNotify = false;
	private boolean mIsRecv = false;

	public String getTestRPCStr()
	{
		return mTestRPCStr;
	}

	@Override
	@BusMethod
	public void testRPC(String recvInfo) throws BusException
	{
		// TODO Auto-generated method stub
		mTestRPCStr = recvInfo;
	}

	// requireStrInfo and sendCliInfo also need syn,
	// its orders must be first sendcliinfo, then requirestrinfo
	@Override
	@BusMethod
	public boolean getNotifyStatus()
	{
		return mIsNotify;
	}

	@Override
	@BusMethod
	public String requireStrInfo() throws BusException
	{
		// TODO Auto-generated method stub
		mIsNotify = false;
		return mReqStr;
	}

	public void sendCliInfo(String info)
	{

		mReqStr = info;
		mIsNotify = true;
	}

	@Override
	@BusMethod
	public synchronized void getStrInfo(String recvInfo) throws BusException
	{
		// TODO Auto-generated method stub

		mGetStr = recvInfo;
		mIsRecv = true;
	}

	@Override
	@BusMethod
	public boolean getRecvStatus()
	{
		return mIsRecv;
	}

	public synchronized String recvCliInfo()
	{
		// need syn with getStrInfo, eg when exec new, then mGetStr = recvInfo,
		// last mGetStr = ""
		mIsRecv = false;
		return mGetStr;
	}

	@Override
	@BusSignal
	public void sendInfoOnSignal(String str) throws BusException
	{
		// TODO Auto-generated method stub
		
	}

}