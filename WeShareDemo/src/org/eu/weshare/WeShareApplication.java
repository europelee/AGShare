package org.eu.weshare;


import org.eu.weshare.mediaplayservice.GstreamerService;
import org.eu.weshare.upnpservice.UPnPService;

import android.app.Application;



public class WeShareApplication extends Application {

  static {
    System.loadLibrary("gstreamer_android");
    System.loadLibrary("gsutil");
  }

  public static UPnPService localService = null;
  public static GstreamerService mGstService = null;

  @Override
  public void onCreate() {
    // TODO Auto-generated method stub
    super.onCreate();

    localService = new UPnPService();
    mGstService = GstreamerService.getInstance();
    localService.setSourceListener(mGstService.mInputSourceListener);
  }

  @Override
  public void onTerminate() {
    // TODO Auto-generated method stub
    super.onTerminate();
    mGstService.delInstance();

  }
}
