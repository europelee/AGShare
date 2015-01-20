package org.upnp.alljoynservice.end;


import org.upnp.alljoynservice.ConstData.AlljoynConst;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SvrEndService extends BaseEndService{
  
  private static final String LOG_TAG = "SvrEndService";
  private final IBinder mBinder = new LocalBinder();
  
  public class LocalBinder extends Binder {
    public BaseEndService getService() {
      return SvrEndService.this;
    }
  }
  
  @Override
  public IBinder onBind(Intent arg0) {
    Log.i(LOG_TAG, "onBind");
    this.setBound(true);
    if (null != this.getAjCommMgr()) {
      this.getAjCommMgr().setAjCommParam(AlljoynConst.SERVER, arg0);
    }
    return mBinder;
  }
}
