package org.upnp.alljoynservice.end;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;

import org.upnp.alljoynservice.ConstData.AlljoynConst;
import org.upnp.alljoynservice.Device.DeviceInterface;
import org.upnp.alljoynservice.Device.DeviceService;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class EndPtService extends Service implements ServiceConfig {

  private static final String TAG = "EndPtService";

  public final static String SERVICE_NAME_KEY = "EndServiceName";
  public final static String SERVICE_PORT_KEY = "EndServicePort";
  public final static String SERVICE_OBJPATH_KEY = "BusObjPath";
  public final static String CIRCLE_NAME_KEY = "CircleName";

  private static final int CONNECT = 0;
  private static final int DISCONNECT = 1;
  private static final int JOIN_SESSION = 2;
  private static final int MESSAGE_POST_TOAST = 3;

  private enum ServiceRole {
    SERVER_END, CLIENT_END // need doublerole future?
  };

  public static final String CLIENT = "client";
  public static final String SERVER = "server";

  private ServiceRole mRole; // ugly, may be better to use inhert and strategy
  // pattern(for bus communication)

  private volatile boolean mIsjoinSession;

  private boolean mIsConnected;
  private volatile boolean mIsAccept;// just action, not status
  private volatile boolean mAwareLosSess;
  private int mSessionId;
  private String mServiceName = "org.smarthome.demoservice";
  private short mServicePort = 889;
  private String mBusObjPath = "/DemoService";
  private String mSerGUIDName = "";
  private BackgroundHandler mBackgroundHandler = null;
  private HandlerThread busThread = null;
  private BusAttachment mBus = null;

  private DeviceInterface mDevI = null;
  private DeviceService mDev = null;
  private AlljoynBusListener mBusListener = null;

  private ProxyBusObject mProxyObj = null;

  private DeviceInterface mSignalInterface = null; // for service
  public AjBusSignalHandler mSigHandler = new AjBusSignalHandler();// null; // for client

  private String mCircleName = "weshare_circlce";
  private ISessionStatusListener mSessionStatusListener = null;

  private IAlljoynMsgListener mAlljoynMsgListener = null;
  private IServiceFoundListener mServiceFoundListener = null;
  private IJoinListener mJoinListener = null;

  private boolean mIsBound = false;

  /**
   * mDataBuffer refer buffermem provided by app layer then EndPtService pass it to mSigHandler
   * mSigHandler use it for saving bytes recved from peer
   */
  // private byte[] mDataBuffer = null;
  // private AlljoynSessionListener mSessionListener = null;

  // for monitoring clientlist
  // key: uniquename, value: sessionid
  // when session use multipoint, some different joinernames would correspond
  // to same sessionid
  HashMap<Long, ArrayList<String>> mClientList = new HashMap<Long, ArrayList<String>>();

  // used in joinsession
  private ArrayList<ServiceFound> mServiceFoundList = new ArrayList<ServiceFound>();


  /**
   * 
   * @Title: getClientList
   * @Description: deepcopy, used by app layer, avoid sync needed at app layer
   * @param @return
   * @return HashMap<Long,ArrayList<String>>
   * @throws
   */
  public HashMap<Long, ArrayList<String>> getClientList() {
    HashMap<Long, ArrayList<String>> targetList = new HashMap<Long, ArrayList<String>>();

    synchronized (EndPtService.class) {
      for (Iterator<Long> keyIt = mClientList.keySet().iterator(); keyIt.hasNext();) {
        Object key = keyIt.next();

        ArrayList<String> nameList = mClientList.get(key);
        targetList.put((Long) key, new ArrayList<String>());

        int lSize = nameList.size();
        for (int i = 0; i < lSize; ++i) {
          targetList.get(key).add(nameList.get(i));
        }
      }

    }
    return targetList;
  }

  // 使用ArrayList实现一个Key对应一个ArrayList实现一对多
  /**
   * 
   * @Title: putAdd
   * @Description: TODO
   * @param @param key
   * @param @param s
   * @return void
   * @throws
   */
  private void putAdd(Long key, String s) {
    synchronized (EndPtService.this) {

      if (!mClientList.containsKey(key)) {

        mClientList.put(key, new ArrayList<String>());

      }

      if (!(mClientList.get(key).contains(s))) mClientList.get(key).add(s);
    }
  }

  private void delElem(Long key, String s) {
    synchronized (EndPtService.this) {
      if (!mClientList.containsKey(key)) {

        Log.i(TAG, "del " + s + " fail: not key " + key);
        return;

      }

      boolean bRet = mClientList.get(key).contains(s);
      if (!bRet) {
        Log.i(TAG, "error happen, key:" + key + " not have " + s);
        return;
      }

      bRet = mClientList.get(key).remove(s);
      if (!bRet) Log.i(TAG, "error happen, key:" + key + " remove " + s + " fail");
    }
  }

  /**
   * 
   * @Title: sendOverSignal
   * @Description: communication by bus signal
   * @param @param str
   * @return boolean
   * @throws
   */
  public boolean sendOverSignal(String str) {
    if (null == mSignalInterface) {
      Log.e(TAG, "mSignalInterface is null");
      return false;
    }

    try {
      mSignalInterface.sendInfoOnSignal(str);
    } catch (BusException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return false;
    }

    return true;
  }

  public boolean sendOverSignal(byte[] data) {
    if (null == mSignalInterface) {
      Log.e(TAG, "mSignalInterface is null");
      return false;
    }

    try {
      mSignalInterface.sendBytesOnSignal(data);
    } catch (BusException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return false;
    }

    return true;
  }

  /**
   * 
   * @Title: registerSignalHandlersHelper
   * @Description: TODO
   * @param @param handlerName
   * @param @param ifName
   * @param @param sigName
   * @param @param arg1
   * @param @return
   * @return boolean
   * @throws
   */
  private boolean registerSignalHandlersHelper(String handlerName, String ifName, String sigName,
      Class<?>... arg1) {
    Status status;
    Method handleMethod = null;
    try {
      handleMethod = AjBusSignalHandler.class.getDeclaredMethod(handlerName, arg1);
    } catch (NoSuchMethodException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      Log.e(TAG, "handleBusSignal not found");
      return false;
    }

    if (null != handleMethod) {
      status = mBus.registerSignalHandler(ifName, sigName, mSigHandler, handleMethod);

      if (status != Status.OK) {
        Log.e(TAG, "registerSignalHandlers fail!:" + status.toString());
        return false;
      } else {
        Log.i(TAG, "registerSignalHandlers succ");
        return true;
      }
    }

    return false;
  }

  /**
   * 
   * @Title: registerSignalHandlers
   * @Description: TODO
   * @param @return
   * @return boolean
   * @throws
   */
  private boolean registerSignalHandlers() {

    registerSignalHandlersHelper("handleBusSignal", AjBusSignalHandler.iFaceName,
        AjBusSignalHandler.sendStrSigName, new Class<?>[] {String.class});

    registerSignalHandlersHelper("handleBytesOnSignal", AjBusSignalHandler.iFaceName,
        AjBusSignalHandler.sendByteSigName, new Class<?>[] {byte[].class});

    return true;
  }

  /**
   * 
   * @Title: unregisterSignalHandlersHelper
   * @Description: TODO
   * @param @param handlerName
   * @param @param arg1
   * @param @return
   * @return boolean
   * @throws
   */
  private boolean unregisterSignalHandlersHelper(String handlerName, Class<?>... arg1) {
    Method handleMethod = null;
    try {
      handleMethod = AjBusSignalHandler.class.getDeclaredMethod(handlerName, arg1);
    } catch (NoSuchMethodException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      Log.e(TAG, "handleBusSignal not found");
      return false;
    }

    mBus.unregisterSignalHandler(mSigHandler, handleMethod);

    return true;
  }

  /**
   * 
   * @Title: unregisterSignalHandlers
   * @Description: TODO
   * @param @return
   * @return boolean
   * @throws
   */
  private boolean unregisterSignalHandlers() {
    unregisterSignalHandlersHelper("handleBusSignal", new Class<?>[] {String.class});
    unregisterSignalHandlersHelper("handleBytesOnSignal", new Class<?>[] {byte[].class});
    return true;

  }

  public void setAcceptStatus(boolean status) {
    this.mIsAccept = status;
  }

  public boolean getAcceptStatus() {
    return this.mIsAccept;
  }

  public void setLostCliStatus(boolean status) {
    this.mAwareLosSess = status;
  }

  public boolean getLostCliStatus() {
    return mAwareLosSess;
  }

  public boolean getSessionStatus() {
    return mIsjoinSession;
  }

  public DeviceInterface getDevI() {
    return mDevI;
  }

  public DeviceService getDevService() {
    return mDev;
  }

  private void setRole(short roleId) {
    switch (roleId) {
      case 0:
        mRole = ServiceRole.SERVER_END;
        Log.i(TAG, "ROLE:" + mRole.toString());
        break;
      case 1:
        mRole = ServiceRole.CLIENT_END;
        Log.i(TAG, "ROLE:" + mRole.toString());
        break;
      default:
        Log.e(TAG, "roleId invalid!");
        break;
    }
  }

  private void logStatus(String msg, Status status) {
    String log = String.format("%s: %s", msg, status);
    if (status == Status.OK) {
      Log.i(TAG, log);
    } else {
      Message toastMsg = mBackgroundHandler.obtainMessage(MESSAGE_POST_TOAST, log);
      mBackgroundHandler.sendMessage(toastMsg);
      Log.e(TAG, log);
    }
  }

  private class AlljoynBusListener extends BusListener {

    public void foundAdvertisedName(String name, short transport, String namePrefix) {
      Log.i(TAG, String.format("MyBusListener.foundAdvertisedName(%s, 0x%04x, %s)", name,
          transport, namePrefix));

      mServiceFoundList.add(new ServiceFound(name, transport));

      //for cli
      mCircleName = name.substring(namePrefix.length()+1);
      
      if (null != mServiceFoundListener) {
        mServiceFoundListener.addServiceFound(new ServiceFound(name, transport));
      }
      // if (!mIsjoinSession) {
      // Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
      // msg.arg1 = transport;
      // msg.obj = name;
      // mBackgroundHandler.sendMessage(msg);
      // }

    }

    public void lostAdvertisedName(String name, short transport, String namePrefix) {
      Log.i(TAG, String.format("MyBusListener.lostAdvertisedName(%s, 0x%04x, %s)", name, transport,
          namePrefix));
      Log.i(TAG, "because of session-based, so ignore the above lost-info msg");
      // because session-based, so dont need set mIsjoinSession = false
      // mIsjoinSession = false;
      int lSize = mServiceFoundList.size();
      int i = 0;
      for (i = 0; i < lSize; ++i) {
        ServiceFound tmp = mServiceFoundList.get(i);
        if (tmp.mServiceNameFound.equals(name) && tmp.mServicePortFound == transport) {
          Log.i(TAG, name + ":" + transport + " removed");
          break;
        }
      }

      if (i >= 0 && i < lSize) {
        mServiceFoundList.remove(i);
        if (null != mServiceFoundListener) {
          mServiceFoundListener.removeServiceFound(new ServiceFound(name, transport));
        }
      }

    }

    public void nameOwnerChanged(String busName, String previousOwner, String newOwner) {
      Log.i(TAG, "nameOwnerChanged: " + "busName:" + busName + " previousOwner:" + previousOwner
          + " newOwner:" + newOwner);
    }
  }

  private class AlljoynSessionListener extends SessionListener {
    @Override
    public void sessionLost(int sessionId, int reason) {

      // Any implementation of this function must be multithread safe
      // mIsjoinSession = false; //just cli need

      //
      synchronized (EndPtService.this) {
        // sessionid need be casted to long type, else containskey
        // return false
        // fuck java!
        boolean bExist = mClientList.containsKey((long) sessionId);
        if (bExist) {
          ArrayList<String> joinerList = mClientList.get((long) sessionId);
          joinerList.clear();

          mClientList.remove((long) sessionId);

          mAwareLosSess = true;
          
          if (null != mJoinListener) {
            mJoinListener.getSessionStatus(mCircleName, false);
          }
          
          mSignalInterface = null;

          logStatus(
              String.format("AlljoynSessionListener.sessionLost(sessionId = %d, reason = %d)",
                  sessionId, reason), Status.OK);
        } else {
          Log.e(TAG, "happen exception error, not exist sessionid " + sessionId);
        }
      }
    }

    @Override
    public void sessionMemberAdded(int sessionId, String uniqueName) {
      Log.i(TAG, "add:" + "sessid:" + sessionId + " uniquename:" + uniqueName);
      putAdd((long) sessionId, uniqueName);
      if (null != mJoinListener) {
        mJoinListener.addJoiner((long) sessionId, uniqueName);
      }
    }

    @Override
    public void sessionMemberRemoved(int sessionId, String uniqueName) {
      Log.i(TAG, "remove:" + "sessid:" + sessionId + " uniquename:" + uniqueName);
      delElem((long) sessionId, uniqueName);
      if (null != mJoinListener) {
        mJoinListener.delJoiner((long) sessionId, uniqueName);
      }
    }
  };

  private final class BackgroundHandler extends Handler {

    public BackgroundHandler(Looper looper) {
      super(looper);
    }

    public void handleMessage(Message msg) {
      switch (msg.what) {

        case CONNECT:

          doConnect();
          break;

        case DISCONNECT:

          doDisconnect();
          break;

        case JOIN_SESSION:
          doJoinSession((String) msg.obj);
          break;

        case MESSAGE_POST_TOAST:

          Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_LONG).show();
          break;

        default:
          break;
      }
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i("LocalService", "Received start id " + startId + ": " + intent);
    
    if (null == intent)
      return START_NOT_STICKY;
    
    Bundle bunde = intent.getExtras();

    // String sRole = bunde.getString(EndPtService.class.getName(), CLIENT);
    // //for android 3.1 above
    String sRole = bunde.get(EndPtService.class.getName()).toString();
    if (sRole.equals(CLIENT)) setRole((short) 1);

    if (sRole.equals(SERVER)) setRole((short) 0);

    if (null != mRole)
      Log.i(TAG, "ROLE IS " + sRole + ".." + mRole.toString());
    else {
      Log.e(TAG, "ROLE IS " + sRole + " error: mRole is null");
      return START_NOT_STICKY;
    }

    Object obj = null;
    obj = bunde.get(EndPtService.SERVICE_NAME_KEY);

    if (null != obj) {
      this.mServiceName = obj.toString();
    }

    obj = bunde.get(EndPtService.SERVICE_PORT_KEY);
    if (null != obj) {
      this.mServicePort =
          (short) Integer.parseInt(bunde.get(EndPtService.SERVICE_PORT_KEY).toString());
    }

    obj = bunde.get(EndPtService.SERVICE_OBJPATH_KEY);
    if (null != obj) {
      this.mBusObjPath = obj.toString();
    }

    obj = bunde.get(EndPtService.CIRCLE_NAME_KEY);
    if (null != obj) {
      this.mCircleName = obj.toString();
    }

    mIsBound = true;
   // mBackgroundHandler.sendEmptyMessage(CONNECT);

    return START_STICKY;
  }

  // /bindservice
  private IBinder binder = null;

  public class LocalBinder extends Binder {
    public EndPtService getService() {
      return EndPtService.this;
    }
  }

  @Override
  public boolean onUnbind(Intent intent) {
    System.out.println("------onUnbind");
    mIsBound = false;
    return super.onUnbind(intent);
  }

  @Override
  public IBinder onBind(Intent intent) {

    Log.i(TAG, "onBind");
    Bundle bunde = intent.getExtras();

    // String sRole = bunde.getString(EndPtService.class.getName(), CLIENT);
    // //for android 3.1 above
    String sRole = bunde.get(EndPtService.class.getName()).toString();
    if (sRole.equals(CLIENT)) setRole((short) 1);

    if (sRole.equals(SERVER)) setRole((short) 0);

    if (null != mRole)
      Log.i(TAG, "ROLE IS " + sRole + ".." + mRole.toString());
    else {
      Log.e(TAG, "ROLE IS " + sRole + " error: mRole is null");
      return null;
    }

    Object obj = null;
    obj = bunde.get(EndPtService.SERVICE_NAME_KEY);

    if (null != obj) {
      this.mServiceName = obj.toString();
    }

    obj = bunde.get(EndPtService.SERVICE_PORT_KEY);
    if (null != obj) {
      this.mServicePort =
          (short) Integer.parseInt(bunde.get(EndPtService.SERVICE_PORT_KEY).toString());
    }

    obj = bunde.get(EndPtService.SERVICE_OBJPATH_KEY);
    if (null != obj) {
      this.mBusObjPath = obj.toString();
    }

    obj = bunde.get(EndPtService.CIRCLE_NAME_KEY);
    if (null != obj) {
      this.mCircleName = obj.toString();
    }

    mIsBound = true;
    
    binder  = new LocalBinder();
    
    return binder;
  }

  @Override
  public void setConfig(String serviceName, short port) {
    // TODO Auto-generated method stub
    this.mServiceName = serviceName;
    this.mServicePort = port;
  }

  @Override
  public void setObjectPath(String servicePath) {
    // TODO Auto-generated method stub
    this.mBusObjPath = servicePath;
  }

  public void onCreate() {
    Log.i(TAG, "EndPtService onCreate");

    mIsjoinSession = false;
    mIsConnected = false;
    mIsAccept = false;
    mAwareLosSess = false;

    mBusListener = new AlljoynBusListener();

    if (null == busThread) busThread = new HandlerThread("BusHandler");

    busThread.start();

    mBackgroundHandler = new BackgroundHandler(busThread.getLooper());

  }

  public void onDestroy() {
    Log.i(TAG, "EndPtService onDestroy");
    mIsBound = false;
    /* Disconnect to prevent any resource leaks. */
    mBackgroundHandler.sendEmptyMessage(DISCONNECT);

    try {
      Thread.sleep(300);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    // quitsafely better, for processing disconn msg
    /*
     * boolean isStop = this.busThread.quit(); if (false == isStop) Log.i(TAG,
     * "busthread stop fail!!!");
     */
  }

  private void doConnect() {
    Status status;

    Log.i(TAG, "doConnect entry");

    if (mIsConnected) {
      Log.i(TAG, "already connected!");

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onSucc("already connected!", AlljoynConst.MSG_SUCC_DEFAULT_CODE);
      }

      return;
    }

    // 1. Create message bus
    if (mBus == null) {
      org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
      mBus = new BusAttachment(getPackageName(), BusAttachment.RemoteMessage.Receive);
      mBus.setLogLevels("ALL=1;ALLJOYN=7");
    }

    // 2. register buslistener
    mBus.registerBusListener(mBusListener);

    // 3. Create and register the bus object that will be used to send and receive signals
    if (ServiceRole.SERVER_END == mRole || ServiceRole.CLIENT_END == mRole) {

      if (null == mDev) mDev = new DeviceService();

      status = mBus.registerBusObject(mDev, mBusObjPath);
      logStatus("BusAttachment.registerBusObject(mBusObjPath)", status);
      if (status != Status.OK) {
        Log.e(TAG, "registerBusObject fail!");

        if (null != mAlljoynMsgListener) {
          mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_REGISTER_BUSLISTENER_FAIL,
              "registerBusObject fail"));
        }

        mBus.unregisterBusListener(mBusListener);

        return;
      }
    }

    // 4.Connect to the local daemon
    status = mBus.connect();
    logStatus("BusAttachment.connect()", status);
    if (status != Status.OK) {
      Log.e(TAG, "BusAttachment.connect() fail!");

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_BUS_CONNECT_FAIL,
            "BusAttachment.connect fail"));
      }

      if (ServiceRole.SERVER_END == mRole || ServiceRole.CLIENT_END == mRole)// ugly
      {
        mBus.unregisterBusObject(mDev);
      }

      mBus.unregisterBusListener(mBusListener);
      return;
    }

    Log.d(TAG, "bus uniquename:"+mBus.getUniqueName());
    
    mIsConnected = true;

    // 3.2 signalhandler that will be used to receive signals
    if (ServiceRole.CLIENT_END == mRole || ServiceRole.SERVER_END == mRole) {
      if (null == mSigHandler) {
        mSigHandler = new AjBusSignalHandler();
      }

      // register
      registerSignalHandlers();
    }

    if (ServiceRole.CLIENT_END == mRole)// ugly
    {
      // mServiceName is NAME_PREFIX of mSerGUIDName
      status = mBus.findAdvertisedName(mServiceName);
      logStatus("BusAttachement.findAdvertisedName " + mServiceName, status);
      if (Status.OK != status) {

        if (null != mAlljoynMsgListener) {
          mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_ASYNC_FIND_ADVERNAME_FAIL,
              "BusAttachement.findAdvertisedName  fail"));
        }

        // unregister
        unregisterSignalHandlers();

        mBus.unregisterBusObject(mDev);

        mBus.unregisterBusListener(mBusListener);
        mBus.disconnect();

        mIsConnected = false;
        return;
      }


      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onSucc("do connect succ!", AlljoynConst.MSG_SUCC_CONN_CODE);
      }

      return; // below: service_end
    }

    /*
     * Create a new session listening on the contact port of the chat service.
     */
    Mutable.ShortValue contactPort = new Mutable.ShortValue(mServicePort);

    SessionOpts sessionOpts = new SessionOpts();
    sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
    sessionOpts.isMultipoint = true;
    sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
    /*
     * Explicitly add the Wi-Fi Direct transport into our advertisements. This sample is typically
     * used in a "cable- replacement" scenario and so it should work well over that transport. It
     * may seem odd that ANY actually excludes Wi-Fi Direct, but there are topological and
     * advertisement/ discovery problems with WFD that make it problematic to always enable.
     */
    sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;

    status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
      @Override
      public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
        if (sessionPort == mServicePort) {
          Log.i(TAG, "acceptSessionJoiner:" + "sessionPort:" + sessionPort + " joiner:" + joiner);
          return true;
        } else {
          return false;
        }
      }

      @Override
      public void sessionJoined(short sessionPort, int sessionId, String joiner) {

        // Any implementation of this function must be
        // multithread safe
        Log.i(TAG, String.format("BusListener.sessionJoined(%d, %d, %s): on RAW_PORT", sessionPort,
            sessionId, joiner));

        mSessionId = sessionId;
        
        
        
        // in fact, endptservice only support one session with
        // multipoints
        // so, we don't check sessionid value is same as
        // mSessionId(old)
        // and admit mSignalInterface never with different
        // sessionid by default
        if (null == mSignalInterface) {
          // future, custom joiner?
          // use SignalEmitter(BusObject source, String destination, int sessionId,
          // SignalEmitter.GlobalBroadcast globalBroadcast)
          // for a special dest
          SignalEmitter emitter =
              new SignalEmitter(mDev, mSessionId, SignalEmitter.GlobalBroadcast.Off);
          mSignalInterface = emitter.getInterface(DeviceInterface.class);
        }
        //
        putAdd((long) mSessionId, joiner);
        if (null != mJoinListener) {
          mJoinListener.getSessionStatus(mCircleName, true);
          mJoinListener.addJoiner((long) sessionId, joiner);
        }
        // for monitoring cli
        Status val = mBus.setSessionListener(mSessionId, new AlljoynSessionListener());
        if (val != Status.OK) {
          Log.i(TAG, "set sessionlistener for joiner(" + joiner + ") fail!");

          if (null != mAlljoynMsgListener) {
            mAlljoynMsgListener.onFail(new AlljoynErr(
                AlljoynConst.MSG_ERR_SET_SESSIONLISTENER_4CLI_FAIL,
                "setSessionListener for monitoring cli  fail"));
          }

        }

        mIsAccept = true;
      }
    });

    logStatus(
        String.format("BusAttachment.bindSessionPort(%d, %s)", contactPort.value,
            sessionOpts.toString()), status);

    if (status != Status.OK) {

      Log.e(TAG, "BusAttachment.bindSessionPort fail!");

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_BIND_SESSIONPORT_FAIL,
            "BusAttachment.bindSessionPort fail"));
      }

      unregisterSignalHandlers();
      mBus.unregisterBusObject(mDev);
      mBus.unregisterBusListener(mBusListener);
      mBus.disconnect();
      return;
    }

    int flag =
        BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING
            | BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;

    // mSerGUIDName = mServiceName + "." + mBus.getGlobalGUIDString() + "." + mCircleName;
    mSerGUIDName = mServiceName + "." + mCircleName;

    status = mBus.requestName(mSerGUIDName, flag);
    logStatus(String.format("BusAttachment.requestName(%s, 0x%08x)", mSerGUIDName, flag), status);
    if (status == Status.OK) {
      status = mBus.advertiseName(mSerGUIDName, SessionOpts.TRANSPORT_ANY);
      logStatus("BusAttachement.advertiseName " + mSerGUIDName, status);
      if (status != Status.OK) {

        if (null != mAlljoynMsgListener) {
          mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_ADVERTISENAME_FAIL,
              "BusAttachement.advertiseName fail"));
        }

        status = mBus.releaseName(mSerGUIDName);
        logStatus(String.format("BusAttachment.releaseName(%s)", mSerGUIDName), status);
        mBus.unbindSessionPort(mServicePort);
        unregisterSignalHandlers();
        mBus.unregisterBusObject(mDev);
        mBus.unregisterBusListener(mBusListener);
        mBus.disconnect();
        return;
      }
    } else {
      Log.e(TAG, "mBus.requestName fail");

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_REQ_NAME_FAIL,
            "mBus.requestName fail"));
      }

      mBus.unbindSessionPort(mServicePort);
      unregisterSignalHandlers();
      mBus.unregisterBusObject(mDev);
      mBus.unregisterBusListener(mBusListener);
      mBus.disconnect();
      return;
    }

    if (null != mAlljoynMsgListener) {
      mAlljoynMsgListener.onSucc("doConnect succ", AlljoynConst.MSG_SUCC_AD_SERVICE_CODE);
    }

  }

  private void doDisconnect() {
    if (!mIsConnected) {

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener
            .onSucc("doDisconnect succ!", AlljoynConst.MSG_SUCC_ALLJOYN_SERVICE_EXIT);
      }
      return;
    }

    if (ServiceRole.SERVER_END == mRole) {
      mBus.cancelAdvertiseName(mSerGUIDName, SessionOpts.TRANSPORT_ANY);
      mBus.releaseName(mSerGUIDName);
      mBus.unbindSessionPort(mServicePort);

    }

    mSignalInterface = null;

    mBus.unregisterBusListener(mBusListener);

    if (ServiceRole.SERVER_END == mRole || ServiceRole.CLIENT_END == mRole) // ugly
      mBus.unregisterBusObject(mDev);

    if (ServiceRole.CLIENT_END == mRole || ServiceRole.SERVER_END == mRole) {
      unregisterSignalHandlers();
    }

    if (ServiceRole.CLIENT_END == mRole && mIsjoinSession)// ugly
    {
      Status status = mBus.leaveSession(mSessionId);
      logStatus("BusAttachment.leaveSession()", status);
    }

    Log.i(TAG, "disconnect start");
    mBus.disconnect();// 关闭需要较长时间
    Log.i(TAG, "doDisconnect end");
    // mBackgroundHandler.getLooper().quit(); //called in func onDestroy!
    mBus.release();


    mIsConnected = false;

    synchronized (EndPtService.this) {
      mClientList.clear();
    }



    boolean isStop = this.busThread.quit();
    if (false == isStop) Log.i(TAG, "busthread stop fail!!!");

    this.busThread = null;


    if (null != mAlljoynMsgListener) {
      mAlljoynMsgListener.onSucc("doDisconnect succ!", AlljoynConst.MSG_SUCC_ALLJOYN_SERVICE_EXIT);
    }

  }

  private boolean doJoinSession(String name) {
    short contactPort = 0;// CONTACT_DEVICE_SERVICE_PORT
    Status status = Status.FAIL;
    SessionOpts sessionOpts = new SessionOpts();
    Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

    contactPort = mServicePort;

    try {
      status = mBus.joinSession(name, contactPort, sessionId, sessionOpts, new SessionListener() {
        @Override
        public void sessionLost(int sessionId, int reason) {
          mIsjoinSession = false;
        //if not, when service lost then come again, cli would not sendsignal
          mSignalInterface = null; 
          logStatus(String.format("MyBusListener.sessionLost(sessionId = %d, reason = %d)",
              sessionId, reason), Status.OK);

          if (null != mSessionStatusListener) {
            mSessionStatusListener.getSessionStatus(mCircleName, mIsjoinSession);
          }
        }
        
        @Override
        public void sessionMemberAdded(int sessionId, String uniqueName) {
          Log.i(TAG, "notice  add:" + "sessid:" + sessionId + " uniquename:" + uniqueName);
          putAdd((long) sessionId, uniqueName);
          if (null != mSessionStatusListener) {
            mSessionStatusListener.addJoiner((long) sessionId, uniqueName);
          }
        }

        @Override
        public void sessionMemberRemoved(int sessionId, String uniqueName) {
          Log.i(TAG, "notice remove:" + "sessid:" + sessionId + " uniquename:" + uniqueName);
          delElem((long) sessionId, uniqueName);
          if (null != mSessionStatusListener) {
            mSessionStatusListener.delJoiner((long) sessionId, uniqueName);
          }
        }
        
      });
    } catch (Exception ex) {
      Log.e(TAG, "doJoinSession joinSession error");

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_JOIN_SESSION_FAIL,
            "doJoinSession joinSession error"));
      }

      return false;
    }

    logStatus("BusAttachment.joinSession() - sessionId: " + sessionId.value + " name:" + name,
        status);

    if (Status.OK != status && Status.ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED != status) {

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_JOIN_SESSION_FAIL,
            "doJoinSession joinSession error2"));
      }

      return false;
    }

    if (Status.ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED == status) {
      mIsjoinSession = true;
      if (null != mSessionStatusListener) {
        mSessionStatusListener.getSessionStatus(mCircleName, mIsjoinSession);
      }
      return true;
    }
    // mServiceName a prefix name, while name : mServiceName+guid
    mProxyObj =
        mBus.getProxyBusObject(name, mBusObjPath, sessionId.value,
            new Class<?>[] {DeviceInterface.class});

    // mProxyObj.setReplyTimeout(3);
    mDevI = mProxyObj.getInterface(DeviceInterface.class);

    mSessionId = sessionId.value;
    if (null == mSignalInterface) {

      // use SignalEmitter(BusObject source, String destination, int sessionId,
      // SignalEmitter.GlobalBroadcast globalBroadcast)
      // for a special dest
      SignalEmitter emitter =
          new SignalEmitter(mDev, mSessionId, SignalEmitter.GlobalBroadcast.Off);
      mSignalInterface = emitter.getInterface(DeviceInterface.class);
    }

    mIsjoinSession = true;

    if (null != mSessionStatusListener) {
      mSessionStatusListener.getSessionStatus(mCircleName, mIsjoinSession);
    }

    if (mDevI == null) {
      Log.e(TAG, "doJoinSession mDevI==null");

      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onFail(new AlljoynErr(AlljoynConst.MSG_ERR_GET_BUSINTERFACE_FAIL,
            "doJoinSession mDevI==null"));
      }

    } else {
      Log.i(TAG, "doJoinSession mDevI ok");

    }

    if (null != mAlljoynMsgListener) {
      mAlljoynMsgListener.onSucc("doJoinSession succ!", AlljoynConst.MSG_SUCC_DEFAULT_CODE);
    }


    return true;
  }

  /**
   * 
   * @Title: setBusDataListener
   * @Description: pass iBusListener to AjBusSignalHandler
   * @param @param iBusListener
   * @return void
   * @throws
   */
  public void setBusDataListener(IBusDataListener iBusListener) {
    if (null != mSigHandler) {
      mSigHandler.setBusDataListener(iBusListener);
    } else {
      Log.i(TAG, "setBusDataListener fail: mSigHandler null");
    }
  }

  /**
   * 
   * start(take into the function from onbind, for easily control) void
   * 
   * @exception
   * @since 1.0.0
   */
  public void start() {
    /*
     * System.out.println("Handler--->" + Thread.currentThread().getName()+" "
     * +Thread.currentThread().getId()); int nLoop = 4; while(true) { if (null == busThread || false
     * == this.busThread.isAlive()) { nLoop--;
     * 
     * if (nLoop < 0) { Log.e(TAG, "busthread not alive"); return; }
     * 
     * try { Thread.sleep(1000); } catch (InterruptedException e) { // TODO Auto-generated catch
     * block e.printStackTrace(); }
     * 
     * } else break; }
     * 
     * Log.i(TAG, "busthread id:"+busThread.getName()+","+busThread.getId());
     */
    mBackgroundHandler.sendEmptyMessage(CONNECT);
  }

  /**
   * 
   * getSessionStatusListener(instead of getSessionStatus)
   * 
   * @param listener void
   * @exception
   * @since 1.0.0
   */
  public void getSessionStatusListener(ISessionStatusListener listener) {
    mSessionStatusListener = listener;
  }


  /**
   * 
   * joinSession(the method description)
   * 
   * @param serviceName
   * @param servicePort void
   * @exception
   * @since 1.0.0
   */
  public void joinSession(String serviceName, short servicePort) {
    // mIsjoinSession, now only support one session online
    if (!mIsjoinSession) {
      Log.i(TAG, "namefound: " + serviceName + " portfound:" + servicePort);
      int lSize = mServiceFoundList.size();
      int i = 0;
      for (i = 0; i < lSize; ++i) {
        ServiceFound tmp = mServiceFoundList.get(i);
        if (tmp.mServiceNameFound.equals(serviceName) && tmp.mServicePortFound == servicePort) {
          break;
        }
      }

      if (i >= lSize) {
        Log.i(TAG, "the service invalid, not exist int foundlist");
        return;
      }

      Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
      msg.arg1 = servicePort;
      msg.obj = serviceName;
      mBackgroundHandler.sendMessage(msg);
    } else {
      // already join
      if (null != mAlljoynMsgListener) {
        mAlljoynMsgListener.onSucc("already joinSession", AlljoynConst.MSG_SUCC_DEFAULT_CODE);
      }
    }
  }

  /**
   * 
   * setAlljoynMsgListener(the method description)
   * 
   * @param listener void
   * @exception
   * @since 1.0.0
   */
  public void setAlljoynMsgListener(IAlljoynMsgListener listener) {
    mAlljoynMsgListener = listener;
  }

  /**
   * 
   * setServiceFoundListener(for cli finding service)
   * 
   * @param listener void
   * @exception
   * @since 1.0.0
   */
  public void setServiceFoundListener(IServiceFoundListener listener) {
    mServiceFoundListener = listener;
  }

  /**
   * 
   * setJoinListener(for app layer on service side monitoring joiners)
   * 
   * @param listener void
   * @exception
   * @since 1.0.0
   */
  public void setJoinListener(IJoinListener listener) {
    mJoinListener = listener;
  }

  /**
   * 
   * isBound(the method description)
   * @return 
   * boolean
   * @exception 
   * @since  1.0.0
   */
  public boolean isBound() {
    return mIsBound;
  }
  
  /**
   * 
   * setRecvLen(a temp method)
   * @param len 
   * void
   * @exception 
   * @since  1.0.0
   */
  public void setRecvLen(int len) {
    
    if (null == mDev) {
      Log.e(TAG, "mDev is null");
      return;
    }
    
    mDev.mRecvLen = len;
  }
  
  /**
   * 
   * getRecvLen(a temp method)
   * @return 
   * int
   * @exception 
   * @since  1.0.0
   */
  public int getRecvLen() {
    if (null == mDev) {
      Log.e(TAG, "mDev is null");
      return 0;
    }
    
    int len = 0;
    try {
      len = mDev.getRecvLen();
    } catch (BusException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return len;
  }
}
