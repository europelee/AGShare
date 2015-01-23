package org.eu.weshare.upnpservice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;


import org.eu.comm.CommUtil;

import org.eu.weshare.mediaplayservice.IInputSourceListener;

import org.upnp.alljoynservice.ConstData.AlljoynConst;
import org.upnp.alljoynservice.end.AlljoynErr;
import org.upnp.alljoynservice.end.BaseEndService;
import org.upnp.alljoynservice.end.CliEndService;
import org.upnp.alljoynservice.end.EndPtService;
import org.upnp.alljoynservice.end.IAdvListener;
import org.upnp.alljoynservice.end.IAlljoynMsgListener;
import org.upnp.alljoynservice.end.IBusDataListener;
import org.upnp.alljoynservice.end.IGetServiceListener;
import org.upnp.alljoynservice.end.IJoinCircleListener;
import org.upnp.alljoynservice.end.IJoinListener;
import org.upnp.alljoynservice.end.IServiceFoundListener;
import org.upnp.alljoynservice.end.ISessionStatusListener;
import org.upnp.alljoynservice.end.ServiceFound;
import org.upnp.alljoynservice.end.SvrEndService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * 
 * 
 * UPnPService
 * 
 * 2014-7-17 ÏÂÎç2:04:19
 * 
 * @version 1.0.0
 * 
 */
public class UPnPService {



  private final static String TAG = "UPnPService";

  static {
    Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
    System.loadLibrary("alljoyn_java");
  }

  private final static String AD_SERVICE_NAME = "org.eu.weshare";
  private final static short AD_SERVICE_PORT = 889;
  private final static String BUS_OBJ_PATH = "/upnpservice";
  private String mCircleName = "";
  private boolean mIsCli = true;
  private boolean mBRun = false;

  private volatile boolean mIsLost = true;
  private String mCirNameCB = "";
  
  // for app layer
  private IGetServiceListener mCircleServicelistener = null;
  private IJoinCircleListener mJoinCircleListener = null;
  private IAdvListener mAdvListener = null;
  private Context   mContext = null;
  // some listenrs

  private IInputSourceListener mSourceListener = null;
  
  public void setSourceListener(IInputSourceListener l) {
    mSourceListener = l;
  }
  
  private class BusDataListener implements IBusDataListener {

    @Override
    public boolean RecvBusData(byte[] data) {
      // TODO Auto-generated method stub
      if (null != mSourceListener) {
        mSourceListener.inputData(data);
      }
      return true;
    }

    @Override
    public boolean RecvBusData(String data) {
      // TODO Auto-generated method stub
      Log.i(TAG, data);
         
      //temp code
      String info = data;
      
      int fLen = (int) Long.parseLong(info);
      
      if (null != mSourceListener) {
        mSourceListener.setRecvLen(fLen);
      }
      
      return true;
    }

  }

  private IBusDataListener mBusDataListener = new BusDataListener();

  private class UPnPMsgListener implements IAlljoynMsgListener {

    @Override
    public void onSucc(String msg, int msgCode) {
      // TODO Auto-generated method stub
      Log.i(TAG, msg);
      if (AlljoynConst.MSG_SUCC_ALLJOYN_SERVICE_EXIT == msgCode) {
        mBRun = false;
      }

      if (AlljoynConst.MSG_SUCC_CONN_CODE == msgCode) {
        mBRun = true;
      }

      if (AlljoynConst.MSG_SUCC_AD_SERVICE_CODE == msgCode) {
        mBRun = true;
        if (null != mAdvListener) {
          mAdvListener.getAdvStatus(true);
        }
      }
    }

    @Override
    public void onFail(AlljoynErr err) {
      // TODO Auto-generated method stub
      Log.i(TAG, "errCode:" + err.errCode + " errInfo:" + err.errInfo);
    }

  }

  private class JoinListener implements IJoinListener {

    @Override
    public void addJoiner(long sessionId, String joinerName) {
      // TODO Auto-generated method stub
      Log.i(TAG, "sessionid: " + sessionId + " joinerName" + joinerName);
    }

    @Override
    public void delJoiner(long sessionId, String joinerName) {
      // TODO Auto-generated method stub
      Log.i(TAG, "sessionid: " + sessionId + " joinerName" + joinerName);
    }

    @Override
    public void getSessionStatus(String serviceName, boolean sessionStatus) {
      // TODO Auto-generated method stub
      Log.i(TAG, "svr session-status:"+sessionStatus);
      mCirNameCB = serviceName;
      mIsLost = !sessionStatus;
    }

  }

  private class ServiceFoundListener implements IServiceFoundListener {

    @Override
    public void addServiceFound(ServiceFound service) {
      // TODO Auto-generated method stub
      Log.i(TAG, "addServiceFound: " + service.mServiceNameFound + " " + service.mServicePortFound);
      // cli could join now
      // mEndService.joinSession(service.mServiceNameFound, service.mServicePortFound);
      if (null != mCircleServicelistener)
        mCircleServicelistener.foundService(service.mServiceNameFound, service.mServicePortFound);
    }

    @Override
    public void removeServiceFound(ServiceFound service) {
      // TODO Auto-generated method stub
      Log.i(TAG, "removeServiceFound: " + service.mServiceNameFound + " "
          + service.mServicePortFound);
    }

  }

  private class SessionStatusListener implements ISessionStatusListener {

    @Override
    public void getSessionStatus(String serviceName, boolean status) {
      // TODO Auto-generated method stub
      Log.i(TAG, "session-staus:" + status+" cirName:"+serviceName);
      mCirNameCB = serviceName;
      mIsLost = !status;
      if (null != mJoinCircleListener) {
        mJoinCircleListener.getJoinStatus(status);
      }
    }

    @Override
    public void addJoiner(long sessionId, String joinerName) {
      // TODO Auto-generated method stub
      Log.i(TAG, "sessionid: " + sessionId + " joinerName" + joinerName);
    }

    @Override
    public void delJoiner(long sessionId, String joinerName) {
      // TODO Auto-generated method stub
      Log.i(TAG, "sessionid: " + sessionId + " joinerName" + joinerName);
    }

  }

  private IJoinListener mJoinListener = new JoinListener();
  private IServiceFoundListener mServiceFoundListener = new ServiceFoundListener();
  private ISessionStatusListener mSessionStatusListener = new SessionStatusListener();
  private IAlljoynMsgListener mAlljoynMsgListener = new UPnPMsgListener();

  /**
   * may support two service: cli and svr
   */
  private BaseEndService mEndService = null;

  private boolean mIsBound = false;

  private ServiceConnection mConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder localBinder) {

      if (className.getClassName().equals(SvrEndService.class.getName()))
      mEndService = ((SvrEndService.LocalBinder) localBinder).getService();

      if (className.getClassName().equals(CliEndService.class.getName()))
      mEndService = ((CliEndService.LocalBinder) localBinder).getService();
      
    }

    public void onServiceDisconnected(ComponentName arg0) {
      mEndService = null;
    }
  };


  private static Handler mHandler = new Handler();
  
  /**
   * 
   * InitService(the method description)
   * 
   * @param context
   * @param isCli void
   * @exception
   * @since 1.0.0
   */
  public boolean InitService(Context context, boolean isCli, String circleName) {


    Log.i(TAG, "startService");

    if (mIsBound) {
      Log.w(TAG, "already start service");
      return false;
    }

    mContext = context;
    Intent intent = null;
    if (true == isCli) {
      intent = new Intent(context, CliEndService.class);
    }
    else {
      intent = new Intent(context, SvrEndService.class);
    }
    
    Bundle bundle = new Bundle();

    mIsCli = isCli;

    if (true == isCli)
      bundle.putString(EndPtService.class.getName(), EndPtService.CLIENT);
    else {
      bundle.putString(EndPtService.class.getName(), EndPtService.SERVER);
      if (null == circleName) {
        Log.e(TAG, "service role, but not set circlename");

        return false;
      }
      mCircleName = circleName;
    }
    bundle.putString(EndPtService.SERVICE_NAME_KEY, AD_SERVICE_NAME);
    bundle.putShort(EndPtService.SERVICE_PORT_KEY, AD_SERVICE_PORT);
    bundle.putString(EndPtService.SERVICE_OBJPATH_KEY, BUS_OBJ_PATH);
    bundle.putString(EndPtService.CIRCLE_NAME_KEY, mCircleName);

    intent.putExtras(bundle);
    
    boolean ans = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    if (!ans) {
      Log.e(TAG, "bindservice fail");
      return false;
    }

    mIsBound = true;

    return true;
  }

  private class ServiceRunnable implements Runnable {

    @Override
    public void run() {
      int nLoop = 10;
      while (null == mEndService || false == mEndService.isBound()) {
        nLoop--;
        if (nLoop < 0) {
          Log.e(TAG, "mEndService is null, may system busy");
          return;
        }

        try {
          Thread.sleep(1000);
        } catch (Exception ee) {}


      }

      if (null == mEndService || false == mEndService.isBound()) {
        return;
      }



      mEndService.getAjCommMgr().setAlljoynMsgListener(mAlljoynMsgListener);
      mEndService.getAjCommMgr().setBusDataListener(mBusDataListener);

      if (true == mIsCli) {
        mEndService.getAjCommMgr().setServiceFoundListener(mServiceFoundListener);
        mEndService.getAjCommMgr().getSessionStatusListener(mSessionStatusListener);

      } else {
        mEndService.getAjCommMgr().setJoinListener(mJoinListener);
      }

      // need waiting onbind finish!
      // todo for waiting
     
      mEndService.getAjCommMgr().connect();
    }

  }

  public void startService() {

    Thread serviceThread = new Thread(new ServiceRunnable(), "serviceThread");
    serviceThread.start();

  }

  /**
   * 
   * stopService(the method description)
   * 
   * @param context void
   * @exception
   * @since 1.0.0
   */
  public void stopService(Context context) {
    Log.i(TAG, "stopService");

    if (mIsBound) context.unbindService(mConnection);

    mIsBound = false;
  }

  public boolean isBound() {
    return mIsBound;

  }

  public void setCircleName(String circleName) {
    mCircleName = circleName;
  }

  /**
   * 
   * setCircleServiceListener(the method description) void
   * 
   * @exception
   * @since 1.0.0
   */
  public void setCircleServiceListener(IGetServiceListener listener) {
    mCircleServicelistener = listener;
  }

  /**
   * 
   * joinCircle(the method description)
   * 
   * @param name
   * @param port void
   * @exception
   * @since 1.0.0
   */
  public void joinCircle(String name, short port) {
    mEndService.getAjCommMgr().joinSession(name, port);
  }

  /**
   * 
   * setJoinCircleListener(the method description)
   * 
   * @param listener void
   * @exception
   * @since 1.0.0
   */
  public void setJoinCircleListener(IJoinCircleListener listener) {
    mJoinCircleListener = listener;
  }

  /**
   * those listener take away from circlefragment better!
   * 
   * setAdvListener(the method description)
   * 
   * @param listener void
   * @exception
   * @since 1.0.0
   */
  public void setAdvListener(IAdvListener listener) {
    mAdvListener = listener;
  }

  /**
   * 
   * isServiceRunning(the method description)
   * 
   * @param context
   * @return boolean
   * @exception
   * @since 1.0.0
   */
  public boolean isServiceRunning() {
    return mBRun;
  }

  /**
   * 
   * sendOnSignal(the method description)
   * 
   * @param str
   * @return boolean
   * @exception
   * @since 1.0.0
   */
  public boolean sendOnSignal(String str) {
    return mEndService.getAjCommMgr().sendOverSignal(str);
  }

  /**
   * 
   * sendOnSignal(the method description)
   * 
   * @param data
   * @return boolean
   * @exception
   * @since 1.0.0
   */
  public boolean sendOnSignal(byte[] data) {
    return mEndService.getAjCommMgr().sendOverSignal(data);
  }

  public void setBusDataListener(IBusDataListener l) {
    mBusDataListener = l;
  }
  
  public void shareContent(final String filePath) {
    // first check onsession
    //need check to protect send again!
    Thread shareThread = new Thread(new Runnable() {
      public void run() {

        
        boolean ans = doSendRaw(filePath);
        if (!ans) {
          Log.e(TAG, "doSendRaw fail!");
        }

      }
    }, "shareThread");

    shareThread.start();
  
  }
  
  /**
   * 
   * getCirName(the method description)
   * @return 
   * String
   * @exception 
   * @since  1.0.0
   */
  public String getCirNameCB() {
    return mCirNameCB;
  }
  
  public String getCircleName() {
    return mCircleName;
  }
  /**
   * 
   * sessionLost(the method description)
   * @return 
   * boolean
   * @exception 
   * @since  1.0.0
   */
  public boolean  sessionLost () {
    return mIsLost;
  }
  
  private boolean doSendRaw(String fileName) {
    Log.d(TAG, fileName);

    File file = new File(fileName);

    if (false == file.exists()) {
      return false;
    } else {
      
      long fLen = file.length();
      String sLen = Long.toString(fLen);
      boolean ans = sendOnSignal(sLen);
      if (!ans)
      {
        mHandler.post(new Runnable() {

          @Override
          public void run() {
            // TODO Auto-generated method stub
            Toast.makeText(mContext,
              "sendOverSignal fail@", Toast.LENGTH_LONG).show();
          }
          
        });

        return false;
      }
      else
      {
          Log.i(TAG, "file len:"+sLen);
      }
      
      InputStream in = null;
      try {
        in = new FileInputStream(file);
      } catch (FileNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      byte[] refMsg = new byte[1024 * 1024];// Ä§ÊõÊý
      int len = 0;

      int nChunk = 1024 * 128;

      while (true) {
        try {
          len = in.read(refMsg, 0, refMsg.length);

          Log.i(TAG, "len:" + len);
          if (len < 0) {

            Log.i(TAG, "finish writing!");
            break;
          }

        } catch (Exception ex) {
          Log.d(TAG, ex.toString() + ex.getClass().getName());
          ex.printStackTrace();
          break;
        }

        try {

          int nloop = len / nChunk;
          int left = len % nChunk;
          int i = 0;
          ans = true;
          for (i = 0; i < nloop; ++i) {
            byte[] subBt = CommUtil.subBytes(refMsg, i * nChunk, nChunk);
            ans = sendOnSignal(subBt);
            if (!ans) {
              Log.e(TAG, "sendOnSignal fail!");
            }
          }
          if (0 < left) {
            byte[] subBt = CommUtil.subBytes(refMsg, i * nChunk, left);
            ans = sendOnSignal(subBt);
          }

          if (!ans) {
            Log.e(TAG, "sendOnSignal fail!");
          }
        } catch (Exception ex) {
          StringWriter writer = new StringWriter();
          ex.printStackTrace(new PrintWriter(writer));
          Log.d(TAG, writer.getBuffer().toString());

          break;

        }

      }

      try {
        in.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      Log.i(TAG, "finish writing!");
      
      mHandler.post(new Runnable() {

        @Override
        public void run() {
          // TODO Auto-generated method stub
          Toast.makeText(mContext, "finish sending!", Toast.LENGTH_LONG).show();
        }
        
      });
    }

    return true;
  }

}
