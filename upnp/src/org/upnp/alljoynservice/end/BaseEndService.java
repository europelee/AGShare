package org.upnp.alljoynservice.end;

import org.upnp.alljoynservice.ConstData.AlljoynConst;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class BaseEndService extends Service implements ServiceConfig{

  private static final String LOG_TAG = "BaseEndService";
  //private IBinder mBinder = new LocalBinder();
  
  private AjCommMgr  mAjCommMgr = null;
  private boolean mIsBound = false;
  
  
  public class LocalBinder extends Binder {
    public BaseEndService getService() {
      return BaseEndService.this;
    }
  }
  
  
  @Override
  public void setConfig(String serviceName, short port) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void setObjectPath(String servicePath) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean onUnbind(Intent intent) {
    Log.i(LOG_TAG, "onUnbind");
    mIsBound = false;
    return super.onUnbind(intent);
  }
  
  @Override
  public IBinder onBind(Intent arg0) {
    // TODO Auto-generated method stub
    Log.i(LOG_TAG, "onbind");
    return null;
    /*
    mIsBound = true;
    if (null != mAjCommMgr) {
      mAjCommMgr.setAjCommParam(AlljoynConst.SERVER, arg0);
    }
    return mBinder;
    */
  }

  public void onCreate() {
    Log.i(LOG_TAG, "onCreate");
    if (null == mAjCommMgr)
    {
      mAjCommMgr = new AjCommMgr(this.getApplicationContext(), this.getPackageName());
      mAjCommMgr.initAjEventHandler();
    }
  }
  
  public void onDestroy() {
    Log.i(LOG_TAG, "onDestroy");
    mIsBound = false;
    if (null != mAjCommMgr) {
      mAjCommMgr.disconnect();
    }
    
    try {
      Thread.sleep(300);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    
  }
  
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(LOG_TAG, "Received start id " + startId + ": " + intent);
    if (null == intent)
      return START_NOT_STICKY;
    
    mIsBound = true;
    if (null != mAjCommMgr) {
      mAjCommMgr.setAjCommParam(AlljoynConst.SERVER, intent);
    }
    
    return START_STICKY;
  }
  
  public boolean isBound() {
    return mIsBound;
  }
  
  public void setBound(boolean isBound) {
    mIsBound = isBound;
  }
  
  public AjCommMgr getAjCommMgr() {
    return mAjCommMgr;
  }
}
