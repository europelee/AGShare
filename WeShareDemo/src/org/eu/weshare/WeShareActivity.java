package org.eu.weshare;

import java.util.Locale;

import org.eu.comm.CommType;
import org.eu.comm.MMPlayerOpenEvent;
import org.eu.weshare.fragment.CircleFragment;
import org.eu.weshare.fragment.SearchFragment;
import org.eu.weshare.fragment.ShareDataFragment;

import org.eu.weshare.mediaplayservice.MultiMediaPlayer;
import org.eu.weshare.upnpservice.UPnPService;

import com.gstreamer.GStreamer;

import android.app.ActionBar;

import android.app.FragmentTransaction;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import android.support.v4.view.ViewPager;
import android.util.Log;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class WeShareActivity extends FragmentActivity implements ActionBar.TabListener {



  private static final String TAG = "WeShareActivity";

  private MultiMediaPlayer mMMPlayer = null;


  /**
   * The {@link android.support.v4.view.PagerAdapter} that will provide fragments for each of the
   * sections. We use a {@link android.support.v4.app.FragmentPagerAdapter} derivative, which will
   * keep every loaded fragment in memory. If this becomes too memory intensive, it may be best to
   * switch to a {@link android.support.v4.app.FragmentStatePagerAdapter}.
   */
  SectionsPagerAdapter mSectionsPagerAdapter;

  /**
   * The {@link ViewPager} that will host the section contents.
   */
  ViewPager mViewPager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_we_share);

    Log.i(TAG, "onCreate");

    // Set up the action bar.
    final ActionBar actionBar = getActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

    // Create the adapter that will return a fragment for each of the three
    // primary sections of the app.
    mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

    // Set up the ViewPager with the sections adapter.
    mViewPager = (ViewPager) findViewById(R.id.pager);
    mViewPager.setAdapter(mSectionsPagerAdapter);
    mViewPager.setOffscreenPageLimit(4);
    // When swiping between different sections, select the corresponding
    // tab. We can also use ActionBar.Tab#select() to do this if we have
    // a reference to the Tab.
    mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageSelected(int position) {
        actionBar.setSelectedNavigationItem(position);
      }
    });

    // For each of the sections in the app, add a tab to the action bar.
    for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
      // Create a tab with text corresponding to the page title defined by
      // the adapter. Also specify this Activity object, which implements
      // the TabListener interface, as the callback (listener) for when
      // this tab is selected.
      actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(i))
          .setTabListener(this));
    }

    getDevSize();

    // mMMPlayer = new MultiMediaPlayer(WeShareActivity.this);
    try {
      GStreamer.init(this);
    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }


  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy");

    super.onDestroy();

    UPnPService localService = WeShareApplication.localService;
    if (null != localService) {
      localService.stopService(this);
    }


    if (null != mMMPlayer) {
      Log.i(TAG, "destroyVideoView");
      mMMPlayer.destroyVideoView();
      MultiMediaPlayer.delInstance();
    }

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.we_share, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {

      case R.id.action_settings:
        Log.i(TAG, "action_settings");
        CircleFragment dialog = new CircleFragment();
        dialog.show(getSupportFragmentManager(), "CircleFragment");
        return true;

      case R.id.action_videoview:
        Log.i(TAG, "action_videoview");
        mMMPlayer = MultiMediaPlayer.getInstance(this);
        mMMPlayer.setMediaViewVisible(true);
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    // When the given tab is selected, switch to the corresponding page in
    // the ViewPager.
    mViewPager.setCurrentItem(tab.getPosition());
  }

  @Override
  public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {}

  @Override
  public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {}

  /**
   * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the
   * sections/tabs/pages.
   */
  public class SectionsPagerAdapter extends FragmentPagerAdapter {

    public SectionsPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int position) {
      // getItem is called to instantiate the fragment for the given page.
      // Return a DummySectionFragment (defined as a static inner class
      // below) with the page number as its lone argument.

      Log.i(TAG, "getItem pos:" + position);
      Fragment fragment = null;
      switch (position) {
        case CommType.VIDEO_PAGE_INDEX:

        case CommType.MUSIC_PAGE_INDEX:

        case CommType.PICTURE_PAGE_INDEX:

          fragment = new ShareDataFragment();
          Bundle args = new Bundle();
          args.putInt(ShareDataFragment.ARG_SECTION_NUMBER, position);
          fragment.setArguments(args);



          break;

        case CommType.SEARCH_PAGE_INDEX:
          fragment = new SearchFragment();

          break;
      }

      return fragment;

    }

    @Override
    public int getCount() {
      // Show 3 total pages.
      Resources res = getResources();
      String[] shareAssort = res.getStringArray(R.array.share_ids);
      return shareAssort.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      Locale l = Locale.getDefault();
      Resources res = getResources();
      String[] shareAssort = res.getStringArray(R.array.share_ids);

      if (position >= 0 && position <= shareAssort.length)
        return shareAssort[position].toUpperCase(l);

      return null;
    }
  }

  /**
   * A dummy fragment representing a section of the app, but that simply displays dummy text.
   */
  public static class DummySectionFragment extends Fragment {
    /**
     * The fragment argument representing the section number for this fragment.
     */
    public static final String ARG_SECTION_NUMBER = "section_number";

    public DummySectionFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      Log.i("xxxxxxxxxx3", "oncreateview");
      View rootView = inflater.inflate(R.layout.fragment_we_share_dummy, container, false);
      TextView dummyTextView = (TextView) rootView.findViewById(R.id.section_label);
      dummyTextView.setText(Integer.toString(getArguments().getInt(ARG_SECTION_NUMBER)));
      return rootView;
    }
  }

  /*
   * @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
   * 
   * // +android.permission.RESTART_PACKAGES // from http://www.linuxidc.com/Linux/2011-09/43347.htm
   * 
   * if(keyCode==KeyEvent.KEYCODE_BACK&&event.getRepeatCount()==0){ AlertDialog.Builder
   * alertbBuilder=new AlertDialog.Builder(this);
   * alertbBuilder.setTitle("真的要离开？").setMessage("你确定要离开？").setPositiveButton("确定", new
   * DialogInterface.OnClickListener() {
   * 
   * @Override public void onClick(DialogInterface dialog, int which) { //结束这个Activity int nPid =
   * android.os.Process.myPid(); android.os.Process.killProcess(nPid);
   * 
   * } }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
   * 
   * @Override public void onClick(DialogInterface dialog, int which) { dialog.cancel(); }
   * }).create(); alertbBuilder.show(); } return true; }
   */

  private void getDevSize() {
    android.util.DisplayMetrics metric = new android.util.DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metric);
    CommType.mDevWidth = metric.widthPixels; // 屏幕宽度（像素）
    CommType.mDevHeight = metric.heightPixels; // 屏幕高度（像素）
    Log.i(TAG, "mDevWidth:" + CommType.mDevWidth + " mDevHeight:" + CommType.mDevHeight);
  }

}
