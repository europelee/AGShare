package org.eu.weshare;


import org.eu.weshare.mediaplayservice.GstreamerService;
import org.eu.weshare.upnpservice.UPnPService;

import android.app.Application;



public class WeShareApplication extends Application {

  static {
    System.loadLibrary("gstreamer_android");
    System.loadLibrary("gsutil");
  }

  public static UPnPService localSvrService = null;
  public static UPnPService localCliService = null;
  public static GstreamerService mGstService = null;

  @Override
  public void onCreate() {
    // TODO Auto-generated method stub
    super.onCreate();

    mGstService = GstreamerService.getInstance();
    
    localSvrService = new UPnPService();
    localSvrService.setSourceListener(mGstService.mInputSourceListener);
    
    localCliService = new UPnPService();
    localCliService.setSourceListener(mGstService.mInputSourceListener);
  }

  @Override
  public void onTerminate() {
    // TODO Auto-generated method stub
    super.onTerminate();
    mGstService.delInstance();

  }
}
