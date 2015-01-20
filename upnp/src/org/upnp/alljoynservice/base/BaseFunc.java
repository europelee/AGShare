package org.upnp.alljoynservice.base;

import org.alljoyn.bus.Status;

import android.util.Log;

public class BaseFunc {
  public static void logStatus(String TAG, String msg, Status status) {
    String log = String.format("%s: %s", msg, status);
    if (status == Status.OK) {
      Log.i(TAG, log);
    } else {
      Log.e(TAG, log);
    }
  }
}
