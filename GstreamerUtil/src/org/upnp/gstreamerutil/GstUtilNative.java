package org.upnp.gstreamerutil;

import android.util.Log;


public class GstUtilNative
{
	private static final String  TAG = "GstUtilNative";
	private GstMsgListener mGstMsgObserver = null;
	private boolean        mIsInited	   = false;
	private native void nativeInit(); // Initialize native code, build pipeline,
										// etc

	private native void nativeFinalize(); // Destroy pipeline and shutdown
											// native code

	private native void nativePlay(); // Set pipeline to PLAYING

	private native void nativePause(); // Set pipeline to PAUSED

	private native void nativeInputData(byte [] data);
	
	private native boolean nativeSetRecvLen(long len);
	
	private native void		nativeSetChunkSize(int csize);
	private native void 	nativeSetBuffScale(int scale);
	private native void 	nativeSetMediaType(int mediatype);
    private native void nativeSurfaceInit(Object surface); // A new surface is available
    private native void nativeSurfaceFinalize(); // Surface about to be destroyed
    private native void nativeSnapshot(String filePath, int width);
    
	private static native boolean nativeClassInit(); // Initialize native class:
														// cache Method IDs for
														// callbacks
	
	private long native_custom_data; // Native code will use this to keep
										// private data

	
	public void setChunkSize(int csize)
	{
		nativeSetChunkSize(csize);
	}
	/**
	 * 
	* @Title: setBuffScale 
	* @Description: buffing before building pipeline, scale [0,100]
	* @param @param scale
	* @return void
	* @throws
	 */
	public void setBuffScale(int scale)
	{
		nativeSetBuffScale(scale);
	}
	
	/**
	 * 
	* @Title: surfaceInit 
	* @Description: set surfaceview for gstreamer pipeline sink
	* @param @param surface
	* @return void
	* @throws
	 */
	public void surfaceInit(Object surface)
	{
		nativeSurfaceInit(surface);
	}
	
	/**
	 * 
	* @Title: surfaceFinalize 
	* @Description: clear surfaceview for gstreamer pipeline sink
	* @param 
	* @return void
	* @throws
	 */
	public void surfaceFinalize()
	{
		nativeSurfaceFinalize();
	}
	
	/**
	 * 
	* @Title: setMediaType 
	* @Description: 0: audio default, 1: video 
	* @param @param mediatype
	* @return void
	* @throws
	 */
	public void  setMediaType(int mediatype)
	{
		nativeSetMediaType(mediatype);
	}
	
	/**
	 * 
	* @Title: setRecvLen 
	* @Description: set audio/video file len
	* @param @param len
	* @return void
	* @throws
	 */
	public boolean setRecvLen(long len)
	{
		return nativeSetRecvLen(len);
	}
	
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
	 * @param filePath
	 * @param width
	 */
	public void gstVideoSnapShot(final String filePath, int width)
	{
		nativeSnapshot(filePath, width);
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
		//if (true == mIsInited)
	    //the above if should be removed, input not related with play!
		nativeInputData(data);
	}
	
	public void play()
	{
		if (true == mIsInited)		
		nativePlay();
	}
	
	public void pause()
	{
		if (true == mIsInited)
		nativePause();
	}
	
	public void InitGstreamer()
	{

		nativeInit();
		mIsInited = true;
	}
	
	public void FinGstreamer()
	{
		nativeFinalize();
		mIsInited = false;
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
