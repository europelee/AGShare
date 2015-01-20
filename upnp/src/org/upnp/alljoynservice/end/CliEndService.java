package org.upnp.alljoynservice.end;

import org.upnp.alljoynservice.ConstData.AlljoynConst;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class CliEndService  extends BaseEndService{
  
  private static final String LOG_TAG = "CliEndService";
  private final IBinder mBinder = new LocalBinder();
  
  public class LocalBinder extends Binder {
    public BaseEndService getService() {
      return CliEndService.this;
    }
  }
  
  @Override
  public IBinder onBind(Intent arg0) {
    Log.i(LOG_TAG, "onBind");
    this.setBound(true);
    if (null != this.getAjCommMgr()) {
      this.getAjCommMgr().setAjCommParam(AlljoynConst.CLIENT, arg0);
    }
    return mBinder;
  }
}
