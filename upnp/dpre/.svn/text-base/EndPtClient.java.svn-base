package com.tpv.smarthome.communication.upnp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


public class EndPtClient extends Service implements ServiceConfig {

	private String mJSerName = "com.tpv.smarthome.demoservice";
	private short  mJSerPort = 889;
	private String mProxyObjPath = "/DemoService";
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setConfig(String serviceName, short port) {
		// TODO Auto-generated method stub
		mJSerName = serviceName;
		mJSerPort = port;
	}

	@Override
	public void setObjectPath(String servicePath) {
		// TODO Auto-generated method stub
		mProxyObjPath = servicePath;
	}

	public void onCreate()
	{
		
	}
	
	public void onDestroy()
	{
		
	}
}