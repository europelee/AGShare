package org.eu.weshare.mediaplayservice;

import org.eu.comm.CommType;

import org.eu.weshare.R;
import org.eu.weshare.WeShareApplication;
import org.upnp.gstreamerutil.GstMsgListener;
import org.upnp.ui.GStreamerSurfaceView;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageButton;
import android.widget.TextView;

public class MultiMediaPlayer implements GstMsgListener, SurfaceHolder.Callback {

  private final static String TAG = "MultiMediaPlayer";

  private WindowManager mWMgr = null;

  private WindowManager.LayoutParams mWmParams = null;

  private Context mContext = null;

  // private GStreamerSurfaceView mMediaView = null;
  private View mMediaView = null;
  private GStreamerSurfaceView mMediaSubView = null;

  private SurfaceHolder mSurHolder = null;

  private static MultiMediaPlayer mMMPlayer = null;

  private GstreamerService mGstService = null;

  private TextView mTipTV = null;
  private static Bitmap mPauseIcon = null;
  private boolean mIsPause = true;
  private boolean mIsMax = true;

  private Paint p = new Paint();

  public static MultiMediaPlayer getInstance(Context context) {
    if (null == mMMPlayer) {
      mMMPlayer = new MultiMediaPlayer(context);
    }

    return mMMPlayer;
  }

  public static void delInstance() {
    mMMPlayer = null;
  }

  private void delSelf() {
    destroyVideoView();
    mMMPlayer = null;
  }

  private MultiMediaPlayer(Context context) {

    Log.i(TAG, "MultiMediaPlayer constructor enter:");
    mContext = context;

    mGstService = WeShareApplication.mGstService;
    mGstService.setGstMsgListener(this);
    // mWMgr = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    // the above would result in circlefragment view can not show
    // because of weActivity just only support one cur view?

    mWMgr =
        (WindowManager) mContext.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
    mWmParams = new WindowManager.LayoutParams();
    initFloatViewParam();


    // mMediaView = new GStreamerSurfaceView(context);
    mMediaView = LayoutInflater.from(mContext).inflate(R.layout.displayer_bar, null);
    mMediaSubView = (GStreamerSurfaceView) mMediaView.findViewById(R.id.surface_video);
    mMediaSubView.setOnTouchListener(new View.OnTouchListener() {

      @Override
      public boolean onTouch(View arg0, MotionEvent arg1) {
        // TODO Auto-generated method stub
        Log.i(TAG, "onTouch");

        int eventid = arg1.getAction();

        if (eventid == MotionEvent.ACTION_MOVE) {
          mWmParams.x = (int) arg1.getRawX() - mMediaView.getWidth() / 2;
          mWmParams.y = (int) arg1.getRawY() - mMediaView.getHeight() / 2 - 40;
          mWMgr.updateViewLayout(mMediaView, mWmParams);
        }

        return true; // false:for onclick
      }
    });


    mSurHolder = mMediaSubView.getHolder();
    mSurHolder.addCallback(this);

    mWMgr.addView(mMediaView, mWmParams);
    mMediaView.setVisibility(View.GONE);



    ImageButton playBtn = (ImageButton) mMediaView.findViewById(R.id.button_play);
    playBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        mIsPause = false;
        // invalidateSubView();

        mGstService.play();
      }
    });

    ImageButton pauseBtn = (ImageButton) mMediaView.findViewById(R.id.button_stop);
    pauseBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        mIsPause = true;
        // invalidateSubView();
        mGstService.pause();
      }
    });

    ImageButton plusBtn = (ImageButton) mMediaView.findViewById(R.id.button_plus);
    plusBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        mWmParams.width = mWmParams.width * 2;
        mWmParams.height = mWmParams.height * 2;
        if (mWmParams.width > CommType.MAX_MEDIA_WIDTH
            || mWmParams.height > CommType.MAX_MEDIA_HEIGHT) {
          mWmParams.width = mWmParams.width / 2;
          mWmParams.height = mWmParams.height / 2;
          return;
        }
        Log.i(TAG, "mWmParams.width:" + mWmParams.width + " mWmParams.height:" + mWmParams.height);
        mWmParams.x = mWmParams.x - mWmParams.width / 4;
        mWmParams.y = mWmParams.y - mWmParams.height / 4;
        mGstService.pause();
        updateVideoView();
        mGstService.play();
      }
    });

    ImageButton minusBtn = (ImageButton) mMediaView.findViewById(R.id.button_minus);
    minusBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        mWmParams.width = mWmParams.width / 2;
        mWmParams.height = mWmParams.height / 2;
        if (mWmParams.width < CommType.MEDIA_WIDTH || mWmParams.height < CommType.MEDIA_HEIGHT) {
          mWmParams.width = mWmParams.width * 2;
          mWmParams.height = mWmParams.height * 2;
          return;
        }
        Log.i(TAG, "mWmParams.width:" + mWmParams.width + " mWmParams.height:" + mWmParams.height);
        mWmParams.x = mWmParams.x + mWmParams.width / 4;
        mWmParams.y = mWmParams.y + mWmParams.height / 4;
        mGstService.pause();
        updateVideoView();
        mGstService.play();
      }
    });

    ImageButton maxBtn = (ImageButton) mMediaView.findViewById(R.id.button_maxwin);
    maxBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        mIsMax = !mIsMax;
        if (true == mIsMax) {
          mWmParams.width = CommType.mDevWidth;
          mWmParams.height = CommType.mDevHeight;
          mWmParams.x = 0;
          mWmParams.y = 0;
        } else {
          mWmParams.width = CommType.MEDIA_WIDTH;
          mWmParams.height = CommType.MEDIA_HEIGHT;
          mWmParams.x = CommType.mDevWidth / 2;
          mWmParams.y = CommType.mDevHeight / 2;
        }
        mGstService.pause();
        updateVideoView();
        mGstService.play();
      }
    });

    ImageButton exitBtn = (ImageButton) mMediaView.findViewById(R.id.button_exitmmp);
    exitBtn.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {
        // TODO Auto-generated method stub
        delSelf();
      }
    });


    mTipTV = (TextView) mMediaView.findViewById(R.id.gst_tip);

    // need check com.gstreamer.gstreamer nativInit code
    // i can not explain why the below code must be here, not exec when new mMediaView.
    mGstService.initGstreamer(null);

    BitmapDrawable tmp =
        (BitmapDrawable) (mContext.getResources().getDrawable(android.R.drawable.ic_media_pause));

    mPauseIcon = tmp.getBitmap();
  }

  /**
   * 
   * setMediaViewVisible(the method description)
   * 
   * @param vis void
   * @exception
   * @since 1.0.0
   */
  public void setMediaViewVisible(boolean vis) {

    if (true == vis)
      mMediaView.setVisibility(View.VISIBLE);
    else
      mMediaView.setVisibility(View.GONE);
  }

  public void destroyVideoView() {

    if (mMediaView != null) {
      mMediaView.setVisibility(View.GONE);
      mWMgr.removeView(mMediaView);
      mMediaView = null;
    }

    mGstService.FinGstreamer();

  }

  private void initFloatViewParam() {

    mWmParams.type = LayoutParams.TYPE_PHONE;
    mWmParams.format = PixelFormat.RGBA_8888;

    mWmParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;
    mWmParams.gravity = Gravity.LEFT | Gravity.TOP;
    mWmParams.x = 0;
    mWmParams.y = 0;
    mWmParams.width = CommType.mDevWidth;// WindowManager.LayoutParams.WRAP_CONTENT;
    mWmParams.height = CommType.mDevHeight;// WindowManager.LayoutParams.WRAP_CONTENT;
  }



  @Override
  public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
    // TODO Auto-generated method stub
    Log.i(TAG, "surfaceChanged:" + " width:" + arg2 + " height:" + arg3);
    mGstService.getGstUtilNative().setMediaType(1);
    mGstService.getGstUtilNative().surfaceInit(arg0.getSurface());

    // invalidateSubView();
  }

  @Override
  public void surfaceCreated(SurfaceHolder arg0) {
    // TODO Auto-generated method stub
    Log.i(TAG, "surfaceCreated:");

    // initSurfaceBG();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder arg0) {
    // TODO Auto-generated method stub
    Log.i(TAG, "surfaceDestroyed:");
    mGstService.getGstUtilNative().surfaceFinalize();
    // destroyVideoView();
  }

  @SuppressWarnings("unused")
  private void initSurfaceBG() {

    int fX = mSurHolder.getSurfaceFrame().centerX() - mPauseIcon.getWidth() / 2;
    int fY = mSurHolder.getSurfaceFrame().centerY() - mPauseIcon.getHeight() / 2;

    // 获取Canvas对象，并锁定之
    Canvas canvas = mSurHolder.lockCanvas();

    // 设定Canvas对象的背景颜色
    canvas.drawColor(Color.BLACK);

    p.setXfermode(new PorterDuffXfermode(Mode.SRC_ATOP));
    canvas.drawBitmap(mPauseIcon, fX, fY, p);
    if (canvas != null) {
      // 解除锁定，并提交修改内容
      mSurHolder.unlockCanvasAndPost(canvas);
    }
  }

  private void updateVideoView() {
    // TODO Auto-generated method stub
    Log.i(TAG, "updateVideoView");

    mWMgr.updateViewLayout(mMediaView, mWmParams);

  }

  @SuppressWarnings("unused")
  private void invalidateSubView() {
    Log.i(TAG, "" + mSurHolder.getSurfaceFrame().width() + ","
        + mSurHolder.getSurfaceFrame().height());
    int fX = mSurHolder.getSurfaceFrame().centerX() - mPauseIcon.getWidth() / 2;
    int fY = mSurHolder.getSurfaceFrame().centerY() - mPauseIcon.getHeight() / 2;
    // 获取Canvas对象，并锁定之
    Canvas canvas = mSurHolder.lockCanvas();

    // 设定Canvas对象的背景颜色
    canvas.drawColor(Color.BLACK);

    if (true == mIsPause) {
      p.setXfermode(new PorterDuffXfermode(Mode.SRC_ATOP));

      canvas.drawBitmap(mPauseIcon, fX, fY, p);
    }

    if (canvas != null) {
      // 解除锁定，并提交修改内容
      mSurHolder.unlockCanvasAndPost(canvas);
    }
  }

  @Override
  public void RecvGstMsg(final String message) {
    // TODO Auto-generated method stub
    Log.i(TAG, message);
    mTipTV.post(new Runnable() {

      @Override
      public void run() {
        // TODO Auto-generated method stub
        mTipTV.setText(message);
      }

    });
  }

  @Override
  public void CheckGstreamerInited() {
    // TODO Auto-generated method stub

  }



}
