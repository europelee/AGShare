package org.eu.weshare.fragment;

import java.io.File;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;

import org.eu.comm.CommType;
import org.eu.comm.CommType.FileCategory;
import org.eu.comm.CommUtil;
import org.eu.comm.FileInfo;
import org.eu.comm.FileSortHelper;
import org.eu.comm.FileSortHelper.SortMethod;
import org.eu.weshare.R;
import org.eu.weshare.WeShareApplication;
import org.eu.weshare.upnpservice.UPnPService;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Video.Thumbnails;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Toast;



public class ShareDataFragment extends Fragment {

  private static final String TAG = "ShareDataFragment";
  public static final String ARG_SECTION_NUMBER = "section_number";
  private GridView gridView;
  private GridViewAdapter customGridAdapter;

  private ArrayList<ImageItem> mUserData = null;
  private int mPagePos = -1;
  // private UPnPService mUPnPService = null;

  FileSortHelper sort = new FileSortHelper();
  private HashMap<Integer, FileInfo> mFileNameList = new HashMap<Integer, FileInfo>();

  public ShareDataFragment() {

  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    Log.i(TAG, "onCreateView");

    mPagePos = this.getArguments().getInt(ARG_SECTION_NUMBER);

    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    DisplayMetrics dm = new DisplayMetrics();
    getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

    FrameLayout fl = new FrameLayout(getActivity());
    fl.setLayoutParams(params);

    final int margin =
        (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources()
            .getDisplayMetrics());

    gridView = new GridView(getActivity());
    mUserData = getShareData(mPagePos);
    customGridAdapter = new GridViewAdapter(getActivity(), R.layout.row_grid, mUserData);
    gridView.setAdapter(customGridAdapter);

    gridView.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        String keyStr = mUserData.get(position).getTitle();
        Log.i(TAG, position + "#Selected:" + keyStr);
        // todo
        // send selected file to peer(s)
        String file = mUserData.get(position).getFilePath();
        Log.i(TAG, file);

        fileShareSetting(file);
      }
    });
    params.setMargins(margin, margin, margin, margin);
    gridView.setLayoutParams(params);
    gridView.setGravity(Gravity.CENTER);
    gridView.setNumColumns(GridView.AUTO_FIT);
    gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    gridView.setDrawSelectorOnTop(true);
    // gridView.setVerticalSpacing(5);
    // gridView.setColumnWidth(dm.widthPixels/4);
    gridView.setColumnWidth(180);
    gridView.setFocusable(true);
    gridView.setFocusableInTouchMode(true);
    fl.addView(gridView);

    return fl;
    // return rootView;
  }


  private ArrayList<ImageItem> getShareData(int pos) {
    ArrayList<ImageItem> data = null;
    switch (pos) {
      case CommType.VIDEO_PAGE_INDEX:
        data = getVideoDataEx();
        break;

      case CommType.MUSIC_PAGE_INDEX:
        data = getMusicDataEx();
        break;

      case CommType.PICTURE_PAGE_INDEX:
        data = getPictureDataEx();
        break;


      default:
        Log.i(TAG, pos + ": invalid!");
        break;
    }

    return data;
  }

  private Uri getContentUriByCategory(FileCategory cat) {
    Uri uri;
    String volumeName = "external";
    switch (cat) {
      case Theme:
      case Doc:
      case Zip:
      case Apk:
        uri = Files.getContentUri(volumeName);
        break;
      case Music:
        uri = Audio.Media.getContentUri(volumeName);
        break;
      case Video:
        uri = Video.Media.getContentUri(volumeName);
        break;
      case Picture:
        uri = Images.Media.getContentUri(volumeName);
        break;
      default:
        uri = null;
    }
    return uri;
  }

  private String buildSelectionByCategory(FileCategory cat) {
    String selection = null;
    switch (cat) {
      case Theme:
        selection = FileColumns.DATA + " LIKE '%.mtz'";
        break;
      case Doc:
        // selection = buildDocSelection();
        break;
      case Zip:
        // selection = "(" + FileColumns.MIME_TYPE + " == '" + Util.sZipFileMimeType + "')";
        break;
      case Apk:
        selection = FileColumns.DATA + " LIKE '%.apk'";
        break;
      default:
        selection = null;
    }
    return selection;
  }

  private Cursor query(FileCategory fc, SortMethod sort) {
    Uri uri = getContentUriByCategory(fc);
    String selection = buildSelectionByCategory(fc);
    String sortOrder = buildSortOrder(sort);


    if (uri == null) {
      Log.e(TAG, "invalid uri, category:" + fc.name());
      return null;
    }

    Log.i(TAG, "query:" + uri.getPath());


    String[] columns =
        new String[] {FileColumns._ID, FileColumns.DATA, FileColumns.SIZE,
            FileColumns.DATE_MODIFIED};

    return getActivity().getContentResolver().query(uri, columns, selection, null, sortOrder);
  }

  private String buildSortOrder(SortMethod sort) {
    String sortOrder = null;
    switch (sort) {
      case name:
        sortOrder = FileColumns.TITLE + " asc";
        break;
      case size:
        sortOrder = FileColumns.SIZE + " asc";
        break;
      case date:
        sortOrder = FileColumns.DATE_MODIFIED + " desc";
        break;
      case type:
        sortOrder = FileColumns.MIME_TYPE + " asc, " + FileColumns.TITLE + " asc";
        break;
    }
    return sortOrder;
  }

  @SuppressWarnings("unused")
  private ArrayList<ImageItem> getVideoData() {

    final ArrayList<ImageItem> imageItems = new ArrayList<ImageItem>();
    // video files
    File rootDir = Environment.getExternalStorageDirectory();
    Log.i(TAG, "dir:" + rootDir.getAbsolutePath());
    CommUtil.initSearch();
    CommUtil.search(rootDir, CommType.VIDEO_TYPE);
    List<String> list = CommUtil.getSearchList();

    for (int i = 0; i < list.size(); ++i) {
      String filePath = list.get(i);

      Bitmap tmp = CommUtil.getVideoThumbnail(filePath, 128, 128, Thumbnails.MINI_KIND);
      if (null == tmp) {
        tmp = BitmapFactory.decodeResource(this.getResources(), R.drawable.film_icon);
      }
      String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
      Log.i(TAG, fileName);
      imageItems.add(new ImageItem(tmp, fileName, filePath, 0));
    }
    return imageItems;
  }

  private ArrayList<ImageItem> getVideoDataEx() {
    ArrayList<ImageItem> imageItems = new ArrayList<ImageItem>();
    Cursor c = query(FileCategory.Video, sort.getSortMethod());
    getAllFilesEx(c, imageItems);
    return imageItems;
  }


  @SuppressWarnings("unused")
  private ArrayList<ImageItem> getMusicData() {
    final ArrayList<ImageItem> imageItems = CommUtil.getMusicThumbnail(getActivity());
    return imageItems;
  }

  private ArrayList<ImageItem> getMusicDataEx() {
    ArrayList<ImageItem> imageItems = new ArrayList<ImageItem>();
    Cursor c = query(FileCategory.Music, sort.getSortMethod());
    getAllFilesEx(c, imageItems);
    return imageItems;
  }

  /**
   * getPictureData getVideoData merged into one
   * 
   * @return
   */
  @SuppressWarnings("unused")
  private ArrayList<ImageItem> getPictureData() {
    final ArrayList<ImageItem> imageItems = new ArrayList<ImageItem>();

    File rootDir = Environment.getExternalStorageDirectory();
    Log.i(TAG, "dir:" + rootDir.getAbsolutePath());
    CommUtil.initSearch();
    CommUtil.search(rootDir, CommType.PICTURE_TYPE);
    List<String> list = CommUtil.getSearchList();

    for (int i = 0; i < list.size(); ++i) {
      String filePath = list.get(i);

      Bitmap tmp = CommUtil.getPictureThumbnail(filePath, 128, 128);
      String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
      Log.i(TAG, fileName);
      imageItems.add(new ImageItem(tmp, fileName, filePath, 0));
    }

    return imageItems;
  }

  private ArrayList<ImageItem> getPictureDataEx() {

    ArrayList<ImageItem> imageItems = new ArrayList<ImageItem>();
    Cursor c = query(FileCategory.Picture, sort.getSortMethod());
    getAllFilesEx(c, imageItems);
    return imageItems;

  }

  private FileInfo getFileInfo(Cursor cursor) {
    return (cursor == null || cursor.getCount() == 0) ? null : org.eu.comm.Util.GetFileInfo(cursor
        .getString(1));
  }

  @SuppressWarnings("unused")
  private void getAllFiles(Cursor c) {

    Cursor cursor = c;
    if (cursor.moveToFirst()) {
      do {
        Integer position = Integer.valueOf(cursor.getPosition());
        if (mFileNameList.containsKey(position)) continue;
        FileInfo fileInfo = getFileInfo(cursor);
        if (fileInfo != null) {
          mFileNameList.put(position, fileInfo);
          Log.d(TAG, position + ":" + fileInfo.filePath);
        }
      } while (cursor.moveToNext());
    }


  }

  private void getAllFilesEx(Cursor c, ArrayList<ImageItem> imageItems) {

    Cursor cursor = c;
    if (cursor.moveToFirst()) {
      do {
        Integer position = Integer.valueOf(cursor.getPosition());

        FileInfo fileInfo = getFileInfo(cursor);
        if (fileInfo != null) {
          imageItems.add(new ImageItem(null, fileInfo.fileName, fileInfo.filePath, fileInfo.dbId));
          Log.d(TAG, position + ":" + fileInfo.filePath);
        }
      } while (cursor.moveToNext());
    }


  }


  private void fileShareSetting(final String file) {

    ArrayList<CharSequence> list = new ArrayList<CharSequence>();

    if (false == WeShareApplication.localSvrService.sessionLost()) {
      list.add(WeShareApplication.localSvrService.getCirName());
    }

    if (false == WeShareApplication.localCliService.sessionLost()) {
      list.add(WeShareApplication.localCliService.getCirName());
    }

    if (list.size() <= 0) {
      return;
    }

    final CharSequence[] items;
    items = list.toArray(new CharSequence[list.size()]);

    AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());

    builder.setTitle("Pick a color");

    final boolean[] checkItems = new boolean[list.size()];
    for (int i = 0; i < list.size(); ++i) {
      checkItems[i] = false;
    }
    builder.setMultiChoiceItems(items, checkItems, new OnMultiChoiceClickListener() {

      @Override
      public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        // TODO Auto-generated method stub
      }
    });

    builder.setNegativeButton("取消", null);

    builder.setPositiveButton("确定", new OnClickListener() {

      @Override
      public void onClick(DialogInterface arg0, int arg1) {
        // TODO Auto-generated method stub
        String s = "您选择了：\n";

        for (int i = 0; i < checkItems.length; i++) {
          if (true == checkItems[i]) {
            s += items[i] + "\n";
            if (items[i].equals(WeShareApplication.localSvrService.getCirName())) {
              WeShareApplication.localSvrService.shareContent(file);
            } else if (items[i].equals(WeShareApplication.localCliService.getCirName())) {
              WeShareApplication.localCliService.shareContent(file);
            }
          }
        }


        Toast.makeText(ShareDataFragment.this.getActivity().getApplicationContext(), s,
            Toast.LENGTH_SHORT).show();

      }

    });

    builder.show();

  }
}
