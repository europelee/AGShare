package org.upnp.alljoynservice.end;

import org.alljoyn.bus.annotation.BusSignalHandler;

import android.util.Log;

public class AjBusSignalHandler
{
	private static final String TAG = "AjBusSignalHandler";

	private String mRecvStr = "";
	private boolean mRecvStatus = false;

	public static final String iFaceName = "org.upnp.alljoynservice.DeviceInterface";
	public static final String sendStrSigName = "sendInfoOnSignal";

	@BusSignalHandler(iface = "org.upnp.alljoynservice.DeviceInterface", signal = "sendInfoOnSignal")
	public void handleBusSignal(String str)
	{
		Log.i(TAG, "handleBusSignal");
		synchronized (AjBusSignalHandler.this)
		{
			// future, modify to be blocked or writed into tmp file?
			if (mRecvStatus)
			{
				Log.i(TAG, "data still not recved by getInfoFromSignal");
				return;
			}
			mRecvStatus = true;
			mRecvStr = str;
		}
	}

	public boolean getRecvStatus()
	{
		return mRecvStatus;
	}

	public String getInfoFromSignal()
	{
		Log.i(TAG, "getInfoFromSignal");
		synchronized (AjBusSignalHandler.this)
		{
			if (!mRecvStatus)
			{
				Log.i(TAG, "recv false, return null");
				return null;
			}

			mRecvStatus = false;
			return mRecvStr;
		}
	}
}
