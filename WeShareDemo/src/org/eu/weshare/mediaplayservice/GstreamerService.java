package org.eu.weshare.mediaplayservice;


import org.upnp.gstreamerutil.GstMsgListener;
import org.upnp.gstreamerutil.GstUtilNative;

import com.gstreamer.GStreamer;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/**
 * 
 * 
 * GstreamerService
 * 
 * 2014-7-17 ÏÂÎç2:02:56
 * 
 * @version 1.0.0
 * 
 */
public class GstreamerService {

  private static final String TAG = "GstreamerService";

  
  private class InputSourceListener implements IInputSourceListener {

    @Override
    public void setRecvLen(int len) {
      // TODO Auto-generated method stub
      Log.i(TAG, "flen:"+len);
      boolean ans = mGstNative.setRecvLen(len);
      if (false == ans)
      {
        Log.e(TAG, "mGstNative.setRecvLen fail");
        return;
      }
      mGstNative.setBuffScale(5);
      //mGstNative.play();
    }

    @Override
    public void inputData(byte[] data) {
      // TODO Auto-generated method stub
      Log.i(TAG, "len:"+data.length);
      mGstNative.inject2Pipe(data);
    }
    
  }
  
  public InputSourceListener mInputSourceListener = new InputSourceListener();

  private static GstreamerService mGstService = null;
  
  private static GstUtilNative mGstNative = new GstUtilNative();
  
  public static GstreamerService getInstance() {
    if (null == mGstService) {
      return new GstreamerService();
    }
    
    return mGstService;
  }

  public void delInstance() {
    mGstService = null;
  }
  /**
   * 
   * initGstreamer(the method description) void
   * 
   * @exception
   * @since 1.0.0
   */
  public void initGstreamer(Context context) {
//    try {
//      GStreamer.init(context);
//    } catch (Exception e1) {
//      // TODO Auto-generated catch block
//      e1.printStackTrace();
//    }

    mGstNative.InitGstreamer();
  }

  /**
   * 
   * getGstUtilNative(the method description)
   * @return 
   * GstUtilNative
   * @exception 
   * @since  1.0.0
   */
  public GstUtilNative getGstUtilNative() {
    return mGstNative;
  }
  
  /**
   * 
   * FinGstreamer(the method description) 
   * void
   * @exception 
   * @since  1.0.0
   */
  public void FinGstreamer() {
    mGstNative.FinGstreamer();
  }
  
  /**
   * 
   * setGstMsgListener(the method description)
   * @param listener 
   * void
   * @exception 
   * @since  1.0.0
   */
  public void setGstMsgListener(GstMsgListener listener) {
    mGstNative.setGstMsgListener(listener);
  }
  
  public void play() {
    
    mGstNative.play();
  }
  
  public void pause() {
    mGstNative.pause();
  }
}
