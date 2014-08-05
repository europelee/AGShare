package org.upnp.alljoynservice.end;

import org.alljoyn.bus.annotation.BusSignalHandler;

import android.util.Log;

public class AjBusSignalHandler
{
	private static final String TAG = "AjBusSignalHandler";

	private String mRecvStr = "";
	private boolean mRecvStatus = false;
	private boolean mRecvBytesStatus = false;
	public static final String iFaceName = "org.upnp.alljoynservice.DeviceInterface";
	public static final String sendStrSigName = "sendInfoOnSignal";
	public static final String sendByteSigName = "sendBytesOnSignal";

	// now just support one listener
	private IBusDataListener mIBusDataListener = null;

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
			
			// getInfoFromSignal is deprecated now!
            if (null == mIBusDataListener)
            {
                Log.e(TAG, "mIBusDataListener is null, data lost");
                mRecvStatus = false;
                return;
            }
            
            //RecvBusData is a key point, if it slow,
            boolean bRet = mIBusDataListener.RecvBusData(str);
            if (!bRet)
            {
                Log.e(TAG, "mIBusDataListener.RecvBusData fail!");
            }
            
            mRecvStatus = false;			
		}
	}

	@BusSignalHandler(iface = "org.upnp.alljoynservice.DeviceInterface", signal = "sendBytesOnSignal")
	public void handleBytesOnSignal(byte[] data)
	{
		Log.i(TAG, "handleBytesOnSignal");
		synchronized (AjBusSignalHandler.this)
		{
			// future, modify to be blocked or writed into tmp file?
			if (mRecvBytesStatus)
			{
				Log.i(TAG, "data still not recved yet");
				return;
			}
			mRecvBytesStatus = true;
			
			if (null == mIBusDataListener)
			{
				Log.e(TAG, "mIBusDataListener is null, data lost");
				mRecvBytesStatus = false;
				return;
			}
			
			//RecvBusData is a key point, if it slow,
			boolean bRet = mIBusDataListener.RecvBusData(data);
			if (!bRet)
			{
				Log.e(TAG, "mIBusDataListener.RecvBusData fail!");
			}
			
			mRecvBytesStatus = false;
		}
	}

	public boolean getRecvStatus()
	{
		return mRecvStatus;
	}

	@Deprecated
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

	public boolean getRecvBytesStatus()
	{
		return mRecvBytesStatus;
	}

	public void setBusDataListener(IBusDataListener iListener)
	{
		mIBusDataListener = iListener;
	}
}
