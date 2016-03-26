package org.eu.weshare.fragment;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eu.weshare.R;
import org.eu.weshare.WeShareApplication;
import org.eu.weshare.upnpservice.UPnPService;
import org.upnp.alljoynservice.end.IAdvListener;
import org.upnp.alljoynservice.end.IGetServiceListener;
import org.upnp.alljoynservice.end.IJoinCircleListener;

import android.os.Build;
import android.os.Bundle;

import android.support.v4.app.DialogFragment;
import android.util.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;



public class CircleFragment extends DialogFragment {

  private static final String TAG = "CircleFragment";

  private static final String HASHKEY_SERVICE_NAME = "service";
  private static final String HASHKEY_SERVICE_PORT = "port";
  
  private static final String DEFAULT_SERVICE_NAME = "default";
  
  private UPnPService localService = null;
  
  private String mModelName = null;
  private static final String EDIT_SERVICENAME_HINT = "请设定服务名，若无默认:";
  private EditText mServiceNameEdit = null;
  private Button mCreateCircleBtn = null;
  private Button mFindCircleBtn = null;

  private ListView mServiceListView;
  private List<HashMap<String, String>> mServiceList = new ArrayList<HashMap<String, String>>();
  private ArrayAdapter<HashMap<String, String>> mAdapter;
  
  public static CircleFragment newInstance() {
    CircleFragment f = new CircleFragment();
    return f;
  }


  
  private class AdvListener implements IAdvListener {

    @Override
    public void getAdvStatus(boolean status) {
      // TODO Auto-generated method stub
      if (true == status) {
       
        Toast.makeText(CircleFragment.this.getActivity(), "adv succ!", Toast.LENGTH_LONG)
            .show();

        // use FragmentManager&FragmentTransaction hiding fragment better!
        // improve it later.
        CircleFragment.this.dismiss();
      }      
    }
    
  }
  
  private AdvListener mAdvListener = null;
  


  private class JoinCircleListener implements IJoinCircleListener {

    @Override
    public void getJoinStatus(boolean status) {
      // TODO Auto-generated method stub
      if (true == status) {
       
        Toast.makeText(CircleFragment.this.getActivity(), "join circle succ!", Toast.LENGTH_LONG)
            .show();

        // use FragmentManager&FragmentTransaction hiding fragment better!
        // improve it later.
        CircleFragment.this.dismiss();
      }
    }

  }

  private JoinCircleListener mJoinCircleListener = new JoinCircleListener();



  private class GetServiceListener implements IGetServiceListener {

    @Override
    public void foundService(String name, short port) {
      // TODO Auto-generated method stub
      Log.i(TAG, "foundService:" + name + ":" + port);
      
      
      if (true == checkLocalAjService(name)) {
        Log.i(TAG, name+" is localservice");
        return;
      }
      
      HashMap<String, String> tmp = new HashMap<String, String>();
      tmp.put(HASHKEY_SERVICE_NAME, name);
      tmp.put(HASHKEY_SERVICE_PORT, String.valueOf((int) port));
      mServiceList.add(tmp);

      // runonuithread, else the fragment ui does not refresh new content,
      // even call notifyDataSetChanged
      CircleFragment.this.getActivity().runOnUiThread(new Runnable() {

        @Override
        public void run() {
          // TODO Auto-generated method stub
          mAdapter.notifyDataSetChanged();
          // CircleFragment.this.getView().invalidate();
        }

      });

    }

  }

  private GetServiceListener mGetServiceListener = null;

  private String getDevModelName() {
    String model = Build.MODEL;
    Log.i(TAG, "dev-model:" + model);
    
    char first = model.charAt(0);
    if (('a' <= first && first <= 'z')||('A' <= first && first <= 'Z')) {
      ;
    }
    else {
       model = DEFAULT_SERVICE_NAME+"-"+model;
    }
    
    int blankIndex = model.indexOf(' ');
    if (-1 != blankIndex)
    {
      model = model.substring(0, blankIndex);
    }
    
    int dotIndex = model.indexOf('.');
    if (-1 != dotIndex)
    {
      model = model.substring(0, dotIndex);
    }
    
    return model;
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    if (getDialog() != null) {
      getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    View root = inflater.inflate(R.layout.fragment_circle, container, false);

    String devName = getDevModelName();
    mServiceNameEdit = (EditText) root.findViewById(R.id.edit_servicename);
    mServiceNameEdit.setHint("[" + EDIT_SERVICENAME_HINT + devName + "]");
/*
    Button resetBtn = (Button) root.findViewById(R.id.button_reset);
    resetBtn.setOnClickListener(new View.OnClickListener() {
      
      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        //localService.stopService(CircleFragment.this.getActivity());       
      }
    });
 */   
    mCreateCircleBtn = (Button) root.findViewById(R.id.button_createcircle);
    mCreateCircleBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        localService = WeShareApplication.localSvrService;
        
        mModelName = mServiceNameEdit.getText().toString();
        if (0 == mModelName.length()) {
          mModelName = getDevModelName();
        }
        if (true == localService.isBound()) {
          Toast.makeText(CircleFragment.this.getActivity(), "new circle!", Toast.LENGTH_LONG)
              .show();

          // called in another thread, else endservice never called because of sleep on below
          Thread stopThread = new Thread(new Runnable() {

            @Override
            public void run() {
              // TODO Auto-generated method stub
              localService.stopService(CircleFragment.this.getActivity());
            }

          }, "stopthread");
          stopThread.start();
        }
       
        Thread startTread = new Thread(new Runnable(){

          @Override
          public void run() {
            // TODO Auto-generated method stub
            while (true == localService.isServiceRunning()) {
              try {
                Thread.sleep(1000);
                Log.i(TAG, "waiting...");
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
            
            boolean ans =
                localService.InitService(CircleFragment.this.getActivity(), false, mModelName);
            if (false == ans) {
              // notify
              return;
            }
            
            mAdvListener = new AdvListener();
            localService.setAdvListener(mAdvListener);
            localService.startService();             
          }
          
        }, "startThread");
        startTread.start();

      }
    });

    mFindCircleBtn = (Button) root.findViewById(R.id.button_findcircle);
    mFindCircleBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        localService = WeShareApplication.localCliService;
        
        if (true == localService.isBound()) {
          Toast.makeText(CircleFragment.this.getActivity(), "search again!", Toast.LENGTH_LONG)
              .show();

          // called in another thread, else endservice never called because of sleep on below
          Thread stopThread = new Thread(new Runnable() {

            @Override
            public void run() {
              // TODO Auto-generated method stub
              localService.stopService(CircleFragment.this.getActivity());
            }

          }, "stopthread");
          stopThread.start();
        }
        
        
          Thread startTread = new Thread(new Runnable(){

            @Override
            public void run() {
              // TODO Auto-generated method stub
              while (true == localService.isServiceRunning()) {
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                }
              }
            
            boolean ans = localService.InitService(CircleFragment.this.getActivity(), true, null);
            if (false == ans) {
              // notify
              return;
            }
            mGetServiceListener = new GetServiceListener();
            localService.setCircleServiceListener(mGetServiceListener);
            localService.setJoinCircleListener(mJoinCircleListener);
            localService.startService();              
            }
            
          }, "startThread");
          startTread.start();
        
      }
    });

    mServiceListView = (ListView) root.findViewById(R.id.service_listview);
    mServiceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        // TODO Auto-generated method stub
        HashMap<String, String> tmp = mAdapter.getItem(arg2);
        String name = tmp.get(HASHKEY_SERVICE_NAME);
        short port = Short.parseShort(tmp.get(HASHKEY_SERVICE_PORT));

        Log.i(TAG, name + ":" + port);
        WeShareApplication.localCliService.joinCircle(name, port);


      }
    });

    mAdapter =
        new ArrayAdapter<HashMap<String, String>>(CircleFragment.this.getActivity(),
            android.R.layout.simple_list_item_1, mServiceList);
    mServiceListView.setAdapter(mAdapter);

    return root;
  }


  @Override
  public void onStart() {
    super.onStart();

    // below code would override customsize by main_bg.png in fragment_circle.xml
  }
  
  private boolean checkLocalAjService(String serviceName) {

    Log.d(TAG, "checkLocalAjService "+serviceName+" cmp "+WeShareApplication.localSvrService.getCircleName());
    boolean ans = WeShareApplication.localSvrService.isServiceRunning();
    if (false == ans) {
      Log.d(TAG, "svrservice not running");
      return false;
    }
    
    return serviceName.endsWith(WeShareApplication.localSvrService.getCircleName());
  }
}
