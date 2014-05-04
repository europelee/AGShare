package com.example.testupnp;




import org.alljoyn.bus.BusException;

import com.tpv.smarthome.communication.upnp.EndPtService;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%";
	private EditText mLoopNumEdit = null;
	private SharedPreferences sp = null;
	private Button btnTest3 = null;
	private Button btnTest8 = null;
	private Button btnTest9 = null;
	private String sBClient = "client";
	private Thread mServiceThread = null;
	private EndPtService localService = null;
	private boolean      mIsBound;
    Intent intent;
    ComponentName mRunningService = null;
	private boolean  bSetupSession = false;
	//private EndPtService  mEndPtS = null;
	
	static {


		Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
		System.loadLibrary("alljoyn_java");
	}
	
    private ServiceConnection mConnection = new ServiceConnection() 
    {          
    	public void onServiceConnected(ComponentName className,IBinder localBinder) 
    	{             
    		localService = ((EndPtService.LocalBinder) localBinder).getService();         
    	}          
    	public void onServiceDisconnected(ComponentName arg0) 
    	{              localService = null;         
    	}     
    };
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            
    	switch(msg.what)
        {
    		case 0:
    			Toast.makeText(MainActivity.this, "test ServiceRunnable toast",
					Toast.LENGTH_LONG).show();
    			break;
    		case 1:
    			Toast.makeText(MainActivity.this, "connect HomeInfoCenter succ!",
					Toast.LENGTH_LONG).show();
    			break;
    		case 2:
    			Toast.makeText(MainActivity.this, "lost session to HomeInfoCenter",
					Toast.LENGTH_LONG).show();	
    			break;
    		case 3:
    			Toast.makeText(MainActivity.this, "service not active!",
					Toast.LENGTH_LONG).show();	
    			break;	
    		default:
    			Toast.makeText(MainActivity.this, "unkown message!",
					Toast.LENGTH_LONG).show();	
    			break;
        }
     }};
     
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mIsBound = false;
		//获取SharedPreferences对象
		Context ctx = MainActivity.this;      
		
	    sp = ctx.getSharedPreferences("APref", MODE_PRIVATE);
		
		//存入数据
		Editor editor = sp.edit();
	
		editor.putBoolean(sBClient, true);
	
		
		editor.commit();
		
		btnTest3 = (Button)findViewById(R.id.button7);
		btnTest3.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.i(TAG, "hello save!");
				
				Log.i(TAG, "before: client:"+sp.getBoolean(sBClient, true));
				
				Editor editor = sp.edit();
			
				Boolean bSaveAud = sp.getBoolean(sBClient, true);
				editor.putBoolean(sBClient, !bSaveAud);
		
				
				editor.commit();
				
				Log.i(TAG, "after:  client:"+sp.getBoolean(sBClient, true));
				
				if (sp.getBoolean(sBClient, true))
					btnTest3.setText("client");
				else
					btnTest3.setText("server");
				
			}
		});
		
		btnTest8 = (Button)findViewById(R.id.button8);
		btnTest8.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mServiceThread = new Thread(new ServiceRunnable(), "mServiceThread");
				mServiceThread.start();
				
			}
		});
		
		mLoopNumEdit = (EditText) findViewById(R.id.editLoopNum);
		mLoopNumEdit.setText("hello alljoyn!");
		
		btnTest9 = (Button)findViewById(R.id.button9);
		btnTest9.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if (!sp.getBoolean(sBClient, true))
				{
					String info = null;
					info = localService.getDevService().getTestRPCStr();
					 Toast.makeText(getApplicationContext(), info, Toast.LENGTH_LONG).show();
					return ;
				}
				String sNLoop = mLoopNumEdit.getText().toString();
				
				try {
					localService.getDevI().testRPC(sNLoop);
				} catch (BusException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void startService()
	{

		/*
		if(mRunningService!=null)
		{
    		boolean isStop = stopService(intent);
			
    		if (false == isStop)
    			Log.w(TAG, "stop service fail!!!!!!!!");
    		
			mRunningService = null;
			
			
			try
			{
				Thread.sleep(50);
			}
			catch(Exception ee)
			{
			}
		}
		*/
		 
		
		//unbindService(mConnection);
		
		if (mIsBound)
			return;
		
		try
		{
			Thread.sleep(500);
		}
		catch(Exception ee)
		{
		}
		
        intent = new Intent(this, EndPtService.class);
        Bundle bundle = new Bundle(); 
        
        if (sp.getBoolean(sBClient, true) == true)
        bundle.putString(EndPtService.class.getName(), EndPtService.CLIENT);
        if (sp.getBoolean(sBClient, true) == false)
        bundle.putString(EndPtService.class.getName(), EndPtService.SERVER);
        
        intent.putExtras(bundle);
       
        boolean ans = bindService(intent, mConnection, BIND_AUTO_CREATE);  
        
        if (!ans)
        {
        	Log.e(TAG, "bindservice fail");
        	return;
        }
        
        mIsBound = true;
        
        /*
        mRunningService =  startService(intent);
        
        if (mRunningService == null) 
        {
            Log.i(TAG, "onCreate(): failed to startService()");
            Toast.makeText(getApplicationContext(), "TVApplication: failed to startService()", Toast.LENGTH_LONG).show();
            //Toast.makeText(getApplicationContext(), "onCreate(): failed to startService()", Toast.LENGTH_LONG).show();
        }
        else
        	 Toast.makeText(getApplicationContext(), "Start server ok", Toast.LENGTH_LONG).show();
        	 */
	}
	
	
	public void onDestroy()
	{
   	 Log.i(TAG, "TVApplication quit");
   	 
   	 if(mIsBound)
   		 unbindService(mConnection);  
   	mIsBound = false;
   	 	super.onDestroy();
   	 	
   	
		//notifyObservers(APPLICATION_QUIT_EVENT);
   	 
   	 /*
		if(mRunningService!=null)
		{
			Log.i(TAG, "stopService");
   		boolean isStop = stopService(intent);
   		if (false == isStop)
   			Log.w(TAG, "stop service fail!!!");
		}
		mRunningService = null;
		*/
	}
	private boolean isServiceRunning() {
		
		Log.i(TAG, "service name:"+EndPtService.class.getName());
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (EndPtService.class.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}


	class ServiceRunnable implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			Looper.myLooper().prepare();
			startService();
			
			handler.sendEmptyMessage(0);
            
    			
			int nCheckLoop = 10;
			
			while(true)
			{
				if (isServiceRunning())
				{
					nCheckLoop = 10;
					
					try
					{
						Thread.sleep(3000);
					}
					catch(Exception ee)
					{
					}
					
					boolean ans = localService.getSessionStatus();
					if (ans)
					{
						if (!bSetupSession)
						{
							bSetupSession = true;
							Log.i(TAG, "activity get succ session");
							handler.sendEmptyMessage(1);
						}
					}
					else
					{
						if (bSetupSession)
						{
						Log.i(TAG, "activity lost session");
						handler.sendEmptyMessage(2);
						}
						bSetupSession = false;
					}
					

				}
				else
				{
					
					--nCheckLoop;
					
					if (nCheckLoop < 0)
					{
						Log.i(TAG, "service not active!");
						
						handler.sendEmptyMessage(3);
						
						Looper.myLooper().quit();
						
						break;
					}
					
					try
					{
						Thread.sleep(300);
					}
					catch(Exception ee)
					{
					}
				}
				


			}//while 
			
			Looper.myLooper().loop();
			Log.i(TAG, "Looper quit!");
		}
		
	}
}
