package org.upnp.gstreamerutil;

import android.util.Log;


public class GstUtilNative
{
	private static final String  TAG = "GstUtilNative";
	private GstMsgListener mGstMsgObserver = null;
	
	private native void nativeInit(); // Initialize native code, build pipeline,
										// etc

	private native void nativeFinalize(); // Destroy pipeline and shutdown
											// native code

	private native void nativePlay(); // Set pipeline to PLAYING

	private native void nativePause(); // Set pipeline to PAUSED

	private native void nativeInputData(byte [] data);
	
	private static native boolean nativeClassInit(); // Initialize native class:
														// cache Method IDs for
														// callbacks
	
	private long native_custom_data; // Native code will use this to keep
										// private data
	/**
	 * 
	* @Title: setGstMsgListener 
	* @Description: for android app layer, such as activity can interact with gstreamer
	* @param @param observer
	* @return void
	* @throws
	 */
	public void setGstMsgListener(GstMsgListener observer)
	{
		mGstMsgObserver = observer;
	}
	
	/**
	 * 
	* @Title: inject2Pipe 
	* @Description: inject data into gstreamer pipeline from application
	* @param @param data
	* @return void
	* @throws
	 */
	public void inject2Pipe(byte []data)
	{
		nativeInputData(data);
	}
	
	public void play()
	{
		nativePlay();
	}
	
	public void pause()
	{
		nativePause();
	}
	
	public void InitGstreamer()
	{

		nativeInit();
	}
	
	public void FinGstreamer()
	{
		nativeFinalize();
	}
	
    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
    	if (null == mGstMsgObserver)
    	{
    		Log.e(TAG, "mGstMsgObserver is null");
    		return;
    	} 
    	mGstMsgObserver.RecvGstMsg(message);
    }
    
    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
    	if (null == mGstMsgObserver)
    	{
    		Log.e(TAG, "mGstMsgObserver is null");
    		return;
    	}
    	
    	mGstMsgObserver.CheckGstreamerInited();
    }
    static {
        nativeClassInit();
    }
}
