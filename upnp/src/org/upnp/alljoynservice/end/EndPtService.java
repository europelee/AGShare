package org.upnp.alljoynservice.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;
import org.upnp.alljoynservice.Device.DeviceInterface;
import org.upnp.alljoynservice.Device.DeviceService;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class EndPtService extends Service implements ServiceConfig
{

	private static final String TAG = "EndPtService";

	private static final int CONNECT = 0;
	private static final int DISCONNECT = 1;
	private static final int JOIN_SESSION = 2;
	private static final int MESSAGE_POST_TOAST = 3;

	private enum ServiceRole
	{
		SERVER_END, CLIENT_END // need doublerole future?
	};

	public static final String CLIENT = "client";
	public static final String SERVER = "server";

	private ServiceRole mRole; // ugly, may be better to use inhert and strategy
								// pattern(for bus communication)

	private volatile boolean mIsjoinSession;

	private boolean mIsConnected;
	private volatile boolean mIsAccept;// just action, not status
	private volatile boolean mAwareLosSess;
	private int mSessionId;
	private String mServiceName = "org.smarthome.demoservice";
	private short mServicePort = 889;
	private String mBusObjPath = "/DemoService";
	private String mSerGUIDName = "";
	private BackgroundHandler mBackgroundHandler = null;
	private HandlerThread busThread = null;
	private BusAttachment mBus = null;

	private DeviceInterface mDevI = null;
	private DeviceService mDev = null;
	private AlljoynBusListener mBusListener = null;

	private ProxyBusObject mProxyObj = null;

	// private AlljoynSessionListener mSessionListener = null;

	// for monitoring clientlist
	// key: uniquename, value: sessionid
	// when session use multipoint, some different joinernames would correspond
	// to same sessionid
	HashMap<Long, ArrayList<String>> mClientList = new HashMap<Long, ArrayList<String>>();

	/**
	 * 
	 * @Title: getClientList
	 * @Description: deepcopy, used by app layer, avoid sync needed at app layer
	 * @param @return
	 * @return HashMap<Long,ArrayList<String>>
	 * @throws
	 */
	public HashMap<Long, ArrayList<String>> getClientList()
	{
		HashMap<Long, ArrayList<String>> targetList = new HashMap<Long, ArrayList<String>>();

		synchronized (EndPtService.class)
		{
			for (Iterator<Long> keyIt = mClientList.keySet().iterator(); keyIt
					.hasNext();)
			{
				Object key = keyIt.next();

				ArrayList<String> nameList = mClientList.get(key);
				targetList.put((Long) key, new ArrayList<String>());

				int lSize = nameList.size();
				for (int i = 0; i < lSize; ++i)
				{
					targetList.get(key).add(nameList.get(i));
				}
			}

		}
		return targetList;
	}

	// 使用ArrayList实现一个Key对应一个ArrayList实现一对多
	/**
	 * 
	 * @Title: putAdd
	 * @Description: TODO
	 * @param @param key
	 * @param @param s
	 * @return void
	 * @throws
	 */
	private void putAdd(Long key, String s)
	{
		synchronized (EndPtService.this)
		{

			if (!mClientList.containsKey(key))
			{

				mClientList.put(key, new ArrayList<String>());

			}

			if (!(mClientList.get(key).contains(s)))
				mClientList.get(key).add(s);
		}
	}

	private void delElem(Long key, String s)
	{
		synchronized (EndPtService.this)
		{
			if (!mClientList.containsKey(key))
			{

				Log.i(TAG, "del " + s + " fail: not key " + key);
				return;

			}

			boolean bRet = mClientList.get(key).contains(s);
			if (!bRet)
			{
				Log.i(TAG, "error happen, key:" + key + " not have " + s);
				return;
			}

			bRet = mClientList.get(key).remove(s);
			if (!bRet)
				Log.i(TAG, "error happen, key:" + key + " remove " + s
						+ " fail");
		}
	}

	public void setAcceptStatus(boolean status)
	{
		this.mIsAccept = status;
	}

	public boolean getAcceptStatus()
	{
		return this.mIsAccept;
	}

	public void setLostCliStatus(boolean status)
	{
		this.mAwareLosSess = status;
	}

	public boolean getLostCliStatus()
	{
		return mAwareLosSess;
	}

	public boolean getSessionStatus()
	{
		return mIsjoinSession;
	}

	public DeviceInterface getDevI()
	{
		return mDevI;
	}

	public DeviceService getDevService()
	{
		return mDev;
	}

	private void setRole(short roleId)
	{
		switch (roleId)
		{
		case 0:
			mRole = ServiceRole.SERVER_END;
			Log.i(TAG, "ROLE:" + mRole.toString());
			break;
		case 1:
			mRole = ServiceRole.CLIENT_END;
			Log.i(TAG, "ROLE:" + mRole.toString());
			break;
		default:
			Log.e(TAG, "roleId invalid!");
			break;
		}
	}

	private void logStatus(String msg, Status status)
	{
		String log = String.format("%s: %s", msg, status);
		if (status == Status.OK)
		{
			Log.i(TAG, log);
		}
		else
		{
			Message toastMsg = mBackgroundHandler.obtainMessage(
					MESSAGE_POST_TOAST, log);
			mBackgroundHandler.sendMessage(toastMsg);
			Log.e(TAG, log);
		}
	}

	private class AlljoynBusListener extends BusListener
	{
		public void foundAdvertisedName(String name, short transport,
				String namePrefix)
		{
			Log.i(TAG, String.format(
					"MyBusListener.foundAdvertisedName(%s, 0x%04x, %s)", name,
					transport, namePrefix));

			if (!mIsjoinSession)
			{
				Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
				msg.arg1 = transport;
				msg.obj = name;
				mBackgroundHandler.sendMessage(msg);
			}

		}

		public void lostAdvertisedName(String name, short transport,
				String namePrefix)
		{
			Log.i(TAG, String.format(
					"MyBusListener.lostAdvertisedName(%s, 0x%04x, %s)", name,
					transport, namePrefix));
			mIsjoinSession = false;

		}

		public void nameOwnerChanged(String busName, String previousOwner,
				String newOwner)
		{
			Log.i(TAG, "nameOwnerChanged: " + "busName:" + busName
					+ " previousOwner:" + previousOwner + " newOwner:"
					+ newOwner);
		}
	}

	private class AlljoynSessionListener extends SessionListener
	{
		@Override
		public void sessionLost(int sessionId, int reason)
		{

			// Any implementation of this function must be multithread safe
			// mIsjoinSession = false; //just cli need

			//
			synchronized (EndPtService.this)
			{
				//sessionid need be casted to long type, else containskey return false
				//fuck java!
				boolean bExist = mClientList.containsKey((long)sessionId);
				if (bExist)
				{
					ArrayList<String> joinerList = mClientList.get((long)sessionId);
					joinerList.clear();

					mClientList.remove((long)sessionId);
					
					mAwareLosSess = true;

					logStatus(
							String.format(
									"AlljoynSessionListener.sessionLost(sessionId = %d, reason = %d)",
									sessionId, reason), Status.OK);
				}
				else
				{
					Log.e(TAG, "happen exception error, not exist sessionid "+sessionId);
				}
			}
		}

		@Override
		public void sessionMemberAdded(int sessionId, String uniqueName)
		{
			Log.i(TAG, "add:" + "sessid:" + sessionId + " uniquename:"
					+ uniqueName);
			putAdd((long) sessionId, uniqueName);
		}

		@Override
		public void sessionMemberRemoved(int sessionId, String uniqueName)
		{
			Log.i(TAG, "remove:" + "sessid:" + sessionId + " uniquename:"
					+ uniqueName);
			delElem((long) sessionId, uniqueName);
		}
	};

	private final class BackgroundHandler extends Handler
	{

		public BackgroundHandler(Looper looper)
		{
			super(looper);
		}

		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{

			case CONNECT:

				doConnect();
				break;

			case DISCONNECT:

				doDisconnect();
				break;

			case JOIN_SESSION:
				doJoinSession((String) msg.obj);
				break;

			case MESSAGE_POST_TOAST:

				Toast.makeText(getApplicationContext(), (String) msg.obj,
						Toast.LENGTH_LONG).show();
				break;

			default:
				break;
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.i("LocalService", "Received start id " + startId + ": " + intent);
		Bundle bunde = intent.getExtras();

		// String sRole = bunde.getString(EndPtService.class.getName(), CLIENT);
		// //for android 3.1 above
		String sRole = bunde.get(EndPtService.class.getName()).toString();
		if (sRole.equals(CLIENT))
			setRole((short) 1);

		if (sRole.equals(SERVER))
			setRole((short) 0);

		if (null != mRole)
			Log.i(TAG, "ROLE IS " + sRole + ".." + mRole.toString());
		else
		{
			Log.e(TAG, "ROLE IS " + sRole + " error: mRole is null");
		}

		mBackgroundHandler.sendEmptyMessage(CONNECT);

		return START_NOT_STICKY;
	}

	// /bindservice
	private final IBinder binder = new LocalBinder();

	public class LocalBinder extends Binder
	{
		public EndPtService getService()
		{
			return EndPtService.this;
		}
	}

	public IBinder onBind(Intent intent)
	{

		Log.i(TAG, "onBind");
		Bundle bunde = intent.getExtras();

		// String sRole = bunde.getString(EndPtService.class.getName(), CLIENT);
		// //for android 3.1 above
		String sRole = bunde.get(EndPtService.class.getName()).toString();
		if (sRole.equals(CLIENT))
			setRole((short) 1);

		if (sRole.equals(SERVER))
			setRole((short) 0);

		if (null != mRole)
			Log.i(TAG, "ROLE IS " + sRole + ".." + mRole.toString());
		else
		{
			Log.e(TAG, "ROLE IS " + sRole + " error: mRole is null");
		}

		mBackgroundHandler.sendEmptyMessage(CONNECT);
		return binder;
	}

	@Override
	public void setConfig(String serviceName, short port)
	{
		// TODO Auto-generated method stub
		this.mServiceName = serviceName;
		this.mServicePort = port;
	}

	@Override
	public void setObjectPath(String servicePath)
	{
		// TODO Auto-generated method stub
		this.mBusObjPath = servicePath;
	}

	public void onCreate()
	{
		Log.i(TAG, "EndPtService onCreate");

		mIsjoinSession = false;
		mIsConnected = false;
		mIsAccept = false;
		mAwareLosSess = false;

		mBusListener = new AlljoynBusListener();

		if (null == busThread)
			busThread = new HandlerThread("BusHandler");

		busThread.start();

		mBackgroundHandler = new BackgroundHandler(busThread.getLooper());

	}

	public void onDestroy()
	{
		Log.i(TAG, "EndPtService onDestroy");
		/* Disconnect to prevent any resource leaks. */
		mBackgroundHandler.sendEmptyMessage(DISCONNECT);

		try
		{
			Thread.sleep(300);
		}
		catch (InterruptedException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// quitsafely better, for processing disconn msg
		/*
		 * boolean isStop = this.busThread.quit(); if (false == isStop)
		 * Log.i(TAG, "busthread stop fail!!!");
		 */
	}

	private void doConnect()
	{
		Status status;

		Log.i(TAG, "doConnect entry");

		if (mIsConnected)
		{
			Log.i(TAG, "already connected!");
			return;
		}
		if (mBus == null)
		{
			org.alljoyn.bus.alljoyn.DaemonInit
					.PrepareDaemon(getApplicationContext());
			mBus = new BusAttachment(getPackageName(),
					BusAttachment.RemoteMessage.Receive);
			mBus.setLogLevels("ALL=1;ALLJOYN=7");
		}

		mBus.registerBusListener(mBusListener);

		if (ServiceRole.SERVER_END == mRole)
		{

			if (null == mDev)
				mDev = new DeviceService();

			status = mBus.registerBusObject(mDev, mBusObjPath);
			logStatus("BusAttachment.registerBusObject(mBusObjPath)", status);
			if (status != Status.OK)
			{
				Log.e(TAG, "registerBusObject fail!");
				mBus.unregisterBusListener(mBusListener);
				return;
			}
		}

		status = mBus.connect();
		logStatus("BusAttachment.connect()", status);
		if (status != Status.OK)
		{
			Log.e(TAG, "BusAttachment.connect() fail!");

			if (ServiceRole.SERVER_END == mRole)// ugly
			{
				mBus.unregisterBusObject(mDev);
			}

			mBus.unregisterBusListener(mBusListener);
			return;
		}

		mIsConnected = true;

		if (ServiceRole.CLIENT_END == mRole)// ugly
		{
			status = mBus.findAdvertisedName(mServiceName);
			logStatus("BusAttachement.findAdvertisedName " + mServiceName,
					status);
			if (Status.OK != status)
			{

				mBus.unregisterBusListener(mBusListener);
				mBus.disconnect();

				mIsConnected = false;
				return;
			}

			return; // below: service_end
		}

		/*
		 * Create a new session listening on the contact port of the chat
		 * service.
		 */
		Mutable.ShortValue contactPort = new Mutable.ShortValue(mServicePort);

		SessionOpts sessionOpts = new SessionOpts();
		sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
		sessionOpts.isMultipoint = true;
		sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
		/*
		 * Explicitly add the Wi-Fi Direct transport into our advertisements.
		 * This sample is typically used in a "cable- replacement" scenario and
		 * so it should work well over that transport. It may seem odd that ANY
		 * actually excludes Wi-Fi Direct, but there are topological and
		 * advertisement/ discovery problems with WFD that make it problematic
		 * to always enable.
		 */
		sessionOpts.transports = SessionOpts.TRANSPORT_ANY
				+ SessionOpts.TRANSPORT_WFD;

		status = mBus.bindSessionPort(contactPort, sessionOpts,
				new SessionPortListener()
				{
					@Override
					public boolean acceptSessionJoiner(short sessionPort,
							String joiner, SessionOpts sessionOpts)
					{
						if (sessionPort == mServicePort)
						{
							Log.i(TAG, "acceptSessionJoiner:" + "sessionPort:"
									+ sessionPort + " joiner:" + joiner);
							return true;
						}
						else
						{
							return false;
						}
					}

					@Override
					public void sessionJoined(short sessionPort, int sessionId,
							String joiner)
					{

						// Any implementation of this function must be
						// multithread safe
						Log.i(TAG,
								String.format(
										"BusListener.sessionJoined(%d, %d, %s): on RAW_PORT",
										sessionPort, sessionId, joiner));
						mSessionId = sessionId;
						//
						putAdd((long) mSessionId, joiner);

						// for monitoring cli
						Status val = mBus.setSessionListener(mSessionId,
								new AlljoynSessionListener());
						if (val != Status.OK)
						{
							Log.i(TAG, "set sessionlistener for joiner("
									+ joiner + ") fail!");
						}

						mIsAccept = true;
					}
				});

		logStatus(String.format("BusAttachment.bindSessionPort(%d, %s)",
				contactPort.value, sessionOpts.toString()), status);

		if (status != Status.OK)
		{

			Log.e(TAG, "BusAttachment.bindSessionPort fail!");
			mBus.unregisterBusObject(mDev);
			mBus.unregisterBusListener(mBusListener);
			mBus.disconnect();
			return;
		}

		int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING
				| BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;

		mSerGUIDName = mServiceName + mBus.getGlobalGUIDString();

		status = mBus.requestName(mSerGUIDName, flag);
		logStatus(String.format("BusAttachment.requestName(%s, 0x%08x)",
				mSerGUIDName, flag), status);
		if (status == Status.OK)
		{
			status = mBus
					.advertiseName(mSerGUIDName, SessionOpts.TRANSPORT_ANY);
			logStatus("BusAttachement.advertiseName " + mSerGUIDName, status);
			if (status != Status.OK)
			{
				status = mBus.releaseName(mSerGUIDName);
				logStatus(String.format("BusAttachment.releaseName(%s)",
						mSerGUIDName), status);
				mBus.unbindSessionPort(mServicePort);
				mBus.unregisterBusObject(mDev);
				mBus.unregisterBusListener(mBusListener);
				mBus.disconnect();
				return;
			}
		}
		else
		{
			Log.e(TAG, "mBus.requestName fail");
			mBus.unbindSessionPort(mServicePort);
			mBus.unregisterBusObject(mDev);
			mBus.unregisterBusListener(mBusListener);
			mBus.disconnect();
			return;
		}

	}

	private void doDisconnect()
	{
		if (!mIsConnected)
			return;

		if (ServiceRole.SERVER_END == mRole)
		{
			mBus.cancelAdvertiseName(mSerGUIDName, SessionOpts.TRANSPORT_ANY);
			mBus.releaseName(mSerGUIDName);
			mBus.unbindSessionPort(mServicePort);
		}

		mBus.unregisterBusListener(mBusListener);

		if (ServiceRole.SERVER_END == mRole)// ugly
			mBus.unregisterBusObject(mDev);

		if (ServiceRole.CLIENT_END == mRole && mIsjoinSession)// ugly
		{
			Status status = mBus.leaveSession(mSessionId);
			logStatus("BusAttachment.leaveSession()", status);
		}

		Log.i(TAG, "disconnect start");
		mBus.disconnect();// 关闭需要较长时间
		Log.i(TAG, "doDisconnect end");
		// mBackgroundHandler.getLooper().quit(); //called in func onDestroy!
		mBus.release();

		mIsConnected = false;

		synchronized (EndPtService.this)
		{
			mClientList.clear();
		}
		boolean isStop = this.busThread.quit();
		if (false == isStop)
			Log.i(TAG, "busthread stop fail!!!");
	}

	private boolean doJoinSession(String name)
	{
		short contactPort = 0;// CONTACT_DEVICE_SERVICE_PORT
		Status status = Status.FAIL;
		SessionOpts sessionOpts = new SessionOpts();
		Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

		contactPort = mServicePort;

		try
		{
			status = mBus.joinSession(name, contactPort, sessionId,
					sessionOpts, new SessionListener()
					{
						@Override
						public void sessionLost(int sessionId, int reason)
						{
							mIsjoinSession = false;

							logStatus(
									String.format(
											"MyBusListener.sessionLost(sessionId = %d, reason = %d)",
											sessionId, reason), Status.OK);
						}
					});
		}
		catch (Exception ex)
		{
			Log.e(TAG, "doJoinSession joinSession error");
			return false;
		}

		logStatus("BusAttachment.joinSession() - sessionId: " + sessionId.value
				+ " name:" + name, status);

		if (Status.OK != status)
		{
			return false;
		}

		// mServiceName a prefix name, while name : mServiceName+guid
		mProxyObj = mBus.getProxyBusObject(name, mBusObjPath, sessionId.value,
				new Class<?>[] { DeviceInterface.class });

		// mProxyObj.setReplyTimeout(3);
		mDevI = mProxyObj.getInterface(DeviceInterface.class);

		mSessionId = sessionId.value;
		mIsjoinSession = true;

		if (mDevI == null)
		{
			Log.e(TAG, "doJoinSession mDevI==null");

		}
		else
		{
			Log.i(TAG, "doJoinSession mDevI ok");

		}

		return true;
	}
}
