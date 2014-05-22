package com.example.testupnp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.alljoyn.bus.BusException;

import org.upnp.alljoynservice.end.*;
import org.upnp.gstreamerutil.GstMsgListener;
import org.upnp.gstreamerutil.GstUtilNative;

import com.gstreamer.GStreamer;

import android.os.Bundle;
import android.os.Environment;
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
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements GstMsgListener
{

	private static final String TAG = "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%";
	private EditText mLoopNumEdit = null;
	private SharedPreferences sp = null;
	private Button btnTest3 = null;
	private Button btnTest8 = null;
	private Button btnTest9 = null;
	private Button btnTest10 = null;
	private Button btnTest11 = null;
	private Button btnTest12 = null;
	private Button btnTest13 = null;
	private String sBClient = "client";
	private Thread mServiceThread = null;
	private EndPtService localService = null;
	private boolean mIsBound;
	Intent intent;
	ComponentName mRunningService = null;
	private boolean bSetupSession = false;
	// private EndPtService mEndPtS = null;
	private String testBytes = "";

	private GstUtilNative mGstNative = new GstUtilNative();
	private Thread mCThread = null;
	private String filePath = "";
	static
	{
		System.loadLibrary("gstreamer_android");
		System.loadLibrary("gsutil");
		Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
		System.loadLibrary("alljoyn_java");
	}

	private class TestListener implements IBusDataListener
	{

		@Override
		public boolean RecvBusData(byte[] arg0)
		{
			Log.i(TAG, "RecvBusData");
			/*
			 * // TODO Auto-generated method stub String bystr =""; try { bystr
			 * = new String(arg0, "US-ASCII"); } catch
			 * (UnsupportedEncodingException e) { // TODO Auto-generated catch
			 * block e.printStackTrace(); }
			 * 
			 * Log.i(TAG, ":"+bystr); testBytes = bystr; return true;
			 */
			Log.i(TAG, "bytes len:" + arg0.length);
			mGstNative.inject2Pipe(arg0);
			return true;
		}

	};

	private TestListener mTestListener = new TestListener();

	private ServiceConnection mConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className,
				IBinder localBinder)
		{
			localService = ((EndPtService.LocalBinder) localBinder)
					.getService();
		}

		public void onServiceDisconnected(ComponentName arg0)
		{
			localService = null;
		}
	};
	private Handler handler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{

			switch (msg.what)
			{
			case 0:
				Toast.makeText(MainActivity.this, "test ServiceRunnable toast",
						Toast.LENGTH_LONG).show();
				break;
			case 1:
				Toast.makeText(MainActivity.this,
						"connect HomeInfoCenter succ!", Toast.LENGTH_LONG)
						.show();
				break;
			case 2:
				Toast.makeText(MainActivity.this,
						"lost session to HomeInfoCenter", Toast.LENGTH_LONG)
						.show();
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
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		try
		{
			GStreamer.init(this);
		}
		catch (Exception e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		mIsBound = false;
		// 获取SharedPreferences对象
		Context ctx = MainActivity.this;

		sp = ctx.getSharedPreferences("APref", MODE_PRIVATE);

		// 存入数据
		Editor editor = sp.edit();

		editor.putBoolean(sBClient, true);

		editor.commit();

		btnTest3 = (Button) findViewById(R.id.button7);
		btnTest3.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				Log.i(TAG, "hello save!");

				Log.i(TAG, "before: client:" + sp.getBoolean(sBClient, true));

				Editor editor = sp.edit();

				Boolean bSaveAud = sp.getBoolean(sBClient, true);
				editor.putBoolean(sBClient, !bSaveAud);

				editor.commit();

				Log.i(TAG, "after:  client:" + sp.getBoolean(sBClient, true));

				if (sp.getBoolean(sBClient, true))
					btnTest3.setText("client");
				else
					btnTest3.setText("server");

			}
		});

		btnTest8 = (Button) findViewById(R.id.button8);
		btnTest8.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				mServiceThread = new Thread(new ServiceRunnable(),
						"mServiceThread");
				mServiceThread.start();

			}
		});

		mLoopNumEdit = (EditText) findViewById(R.id.editLoopNum);
		mLoopNumEdit.setText("hello alljoyn!");

		btnTest9 = (Button) findViewById(R.id.button9);
		btnTest9.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				if (!sp.getBoolean(sBClient, true))
				{
					String info = null;
					info = localService.getDevService().getTestRPCStr();
					Toast.makeText(getApplicationContext(), info,
							Toast.LENGTH_LONG).show();
					return;
				}
				String sNLoop = mLoopNumEdit.getText().toString();

				try
				{
					localService.getDevI().testRPC(sNLoop);
				}
				catch (BusException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});

		btnTest10 = (Button) findViewById(R.id.button10);
		btnTest10.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				/*
				if (sp.getBoolean(sBClient, true))
				{
					String info = null;
					info = localService.mSigHandler.getInfoFromSignal();
					Toast.makeText(getApplicationContext(), info,
							Toast.LENGTH_LONG).show();
					return;
				}

				String sNLoop = mLoopNumEdit.getText().toString();
				boolean ans = localService.sendOverSignal(sNLoop);
				if (!ans)
				{
					Toast.makeText(getApplicationContext(),
							"sendOverSignal fail@", Toast.LENGTH_LONG).show();
				}
				*/
				
				if (sp.getBoolean(sBClient, true))
				{
					String info = null;
					info = localService.mSigHandler.getInfoFromSignal();
					Toast.makeText(getApplicationContext(), info,
							Toast.LENGTH_LONG).show();
					
					int fLen = (int) Long.parseLong(info);
					
					mGstNative.setRecvLen(fLen);
					
					return;
				}		
				
				String fileName = mLoopNumEdit.getText().toString();
				String prePath = Environment.getExternalStorageDirectory()
						.getAbsolutePath();
				filePath = prePath + "/" + fileName;

				Toast.makeText(getApplicationContext(), filePath,
						Toast.LENGTH_LONG).show();
				
				//set file len
				File tmpFile = new File(filePath);
				if (false == tmpFile.exists())
				{
					Toast.makeText(getApplicationContext(), "file not exist!",
							Toast.LENGTH_LONG).show();					
				}
				
				long fLen = tmpFile.length();
				String sLen = Long.toString(fLen);
				boolean ans = localService.sendOverSignal(sLen);
				if (!ans)
				{
					Toast.makeText(getApplicationContext(),
							"sendOverSignal fail@", Toast.LENGTH_LONG).show();
				}
				else
				{
					Toast.makeText(getApplicationContext(), sLen,
							Toast.LENGTH_LONG).show();
				}
				
			}
		});

		btnTest12 = (Button) findViewById(R.id.button12);
		btnTest12.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				if (sp.getBoolean(sBClient, true))
				{
					// gst
					mGstNative.setGstMsgListener(MainActivity.this);
					mGstNative.InitGstreamer();
					return;
				}
			}
		});
		
		btnTest13 = (Button) findViewById(R.id.button13);
		btnTest13.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				if (sp.getBoolean(sBClient, true))
					mGstNative.FinGstreamer();
			}
		});
		
		btnTest11 = (Button) findViewById(R.id.button11);
		btnTest11.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				if (sp.getBoolean(sBClient, true))
				{
					/*
					 * Toast.makeText(getApplicationContext(), testBytes,
					 * Toast.LENGTH_LONG).show(); testBytes = "";
					 */
					mGstNative.play();
					return;
				}

				/*
				 * String sNLoop = mLoopNumEdit.getText().toString(); byte[] tmp
				 * = null; try { tmp = sNLoop.getBytes("US-ASCII"); } catch
				 * (UnsupportedEncodingException e) { // TODO Auto-generated
				 * catch block e.printStackTrace(); }
				 * 
				 * boolean ans = localService.sendOverSignal(tmp); if (!ans) {
				 * Toast.makeText(getApplicationContext(),
				 * "sendOverSignal_bytes fail@", Toast.LENGTH_LONG).show(); }
				 */
				// read mp3 file
				String fileName = mLoopNumEdit.getText().toString();
				String prePath = Environment.getExternalStorageDirectory()
						.getAbsolutePath();
				filePath = prePath + "/" + fileName;
							
				
				mCThread = new Thread(new Runnable()
				{
					public void run()
					{

						Looper.myLooper().prepare();
						boolean ans = doSendRaw(filePath);
						if (!ans)
						{
							Log.e(TAG, "doSendRaw fail!");
						}
					}
				}, "sender");

				mCThread.start();
			}
		});


	}

	public static byte[] subBytes(byte[] src, int begin, int count)
	{
		byte[] bs = new byte[count];
		for (int i = begin; i < begin + count; i++)
			bs[i - begin] = src[i];
		return bs;
	}

	private boolean doSendRaw(String fileName)
	{
		Log.d(TAG, fileName);

		File file = new File(fileName);

		if (false == file.exists())
		{
			return false;
		}
		else
		{
			InputStream in = null;
			try
			{
				in = new FileInputStream(file);
			}
			catch (FileNotFoundException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			byte[] refMsg = new byte[204800];// 魔术数
			int len = 0;

			int nChunk = 102400;
			while (true)
			{
				try
				{
					len = in.read(refMsg, 0, refMsg.length);

					Log.i(TAG, "len:" + len);
					if (len < 0)
					{

						Log.i(TAG, "finish writing!");
						break;
					}

				}
				catch (Exception ex)
				{
					Log.d(TAG, ex.toString() + ex.getClass().getName());
					ex.printStackTrace();
					break;
				}

				try
				{

					int nloop = len / nChunk;
					int left = len % nChunk;
					int i = 0;
					boolean ans = true;
					for (i = 0; i < nloop; ++i)
					{
						byte[] subBt = subBytes(refMsg, i * nChunk, nChunk);
						ans = localService.sendOverSignal(subBt);
						if (!ans)
						{
							Log.e(TAG, "sendOverSignal fail!");
						}
					}
					if (0 < left)
					{
						byte[] subBt = subBytes(refMsg, i * nChunk, left);
						ans = localService.sendOverSignal(subBt);
					}

					if (!ans)
					{
						Log.e(TAG, "sendOverSignal fail!");
					}
				}
				catch (Exception ex)
				{
					StringWriter writer = new StringWriter();
					ex.printStackTrace(new PrintWriter(writer));
					Log.d(TAG, writer.getBuffer().toString());

					break;

				}

			}

			try
			{
				in.close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.i(TAG, "finish writing!");
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void startService()
	{

		/*
		 * if(mRunningService!=null) { boolean isStop = stopService(intent);
		 * 
		 * if (false == isStop) Log.w(TAG, "stop service fail!!!!!!!!");
		 * 
		 * mRunningService = null;
		 * 
		 * 
		 * try { Thread.sleep(50); } catch(Exception ee) { } }
		 */

		// unbindService(mConnection);

		if (mIsBound)
			return;

		try
		{
			Thread.sleep(500);
		}
		catch (Exception ee)
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
		 * mRunningService = startService(intent);
		 * 
		 * if (mRunningService == null) { Log.i(TAG,
		 * "onCreate(): failed to startService()");
		 * Toast.makeText(getApplicationContext(),
		 * "TVApplication: failed to startService()", Toast.LENGTH_LONG).show();
		 * //Toast.makeText(getApplicationContext(),
		 * "onCreate(): failed to startService()", Toast.LENGTH_LONG).show(); }
		 * else Toast.makeText(getApplicationContext(), "Start server ok",
		 * Toast.LENGTH_LONG).show();
		 */
	}

	public void onDestroy()
	{
		Log.i(TAG, "TVApplication quit");

		if (mIsBound)
			unbindService(mConnection);
		mIsBound = false;
		
		if (sp.getBoolean(sBClient, true))
		mGstNative.FinGstreamer();

		super.onDestroy();

		// notifyObservers(APPLICATION_QUIT_EVENT);

		/*
		 * if(mRunningService!=null) { Log.i(TAG, "stopService"); boolean isStop
		 * = stopService(intent); if (false == isStop) Log.w(TAG,
		 * "stop service fail!!!"); } mRunningService = null;
		 */
	}

	private boolean isServiceRunning()
	{

		// Log.i(TAG, "service name:" + EndPtService.class.getName());
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE))
		{
			if (EndPtService.class.getName().equals(
					service.service.getClassName()))
			{
				return true;
			}
		}
		return false;
	}

	class ServiceRunnable implements Runnable
	{

		@Override
		public void run()
		{
			// TODO Auto-generated method stub
			Looper.myLooper().prepare();
			startService();

			handler.sendEmptyMessage(0);

			int nCheckLoop = 10;

			while (true)
			{
				if (isServiceRunning())
				{
					nCheckLoop = 10;

					try
					{
						Thread.sleep(3000);
					}
					catch (Exception ee)
					{
					}

					boolean ans = localService.getSessionStatus();
					if (ans)
					{
						if (!bSetupSession)
						{
							bSetupSession = true;
							Log.i(TAG, "activity get succ session");
							localService.setBusDataListener(mTestListener);
							handler.sendEmptyMessage(1);
						}
					}
					else
					{
						if (bSetupSession)
						{
							Log.i(TAG, "activity lost session");
							localService.setBusDataListener(null);
							handler.sendEmptyMessage(2);
						}
						bSetupSession = false;

						if (sp.getBoolean(sBClient, true) == false)
						{
							HashMap<Long, ArrayList<String>> tmpList = localService
									.getClientList();

							for (Iterator<Long> keyIt = tmpList.keySet()
									.iterator(); keyIt.hasNext();)
							{
								Object key = keyIt.next();

								ArrayList<String> nameList = tmpList.get(key);

								Log.i(TAG, "sessionid: " + (Long) key);

								int lSize = nameList.size();
								for (int i = 0; i < lSize; ++i)
								{
									Log.i(TAG, "name: " + nameList.get(i));
								}
							}
						}
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
					catch (Exception ee)
					{
					}

					if (!mIsBound)
					{
						Log.i(TAG, "service not active!");

						handler.sendEmptyMessage(3);

						Looper.myLooper().quit();

						break;
					}
				}

			}// while

			Looper.myLooper().loop();
			Log.i(TAG, "Looper quit!");
		}

	}

	@Override
	public void CheckGstreamerInited()
	{
		// TODO Auto-generated method stub
		Log.i(TAG, "Gst initialized. Restoring state, playing:");
		// Restore previous playing state

		// Re-enable buttons, now that GStreamer is initialized

	}

	@Override
	public void RecvGstMsg(String arg0)
	{
		// TODO Auto-generated method stub
		final String tmp = arg0;
		final TextView tv = (TextView) this.findViewById(R.id.textview_message);
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				tv.setText(tmp);
			}
		});
	}
}
