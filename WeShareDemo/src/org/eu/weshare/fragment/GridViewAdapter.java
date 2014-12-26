package org.eu.weshare.fragment;

import java.util.ArrayList;
import java.util.HashMap;

import org.eu.comm.CommType.FileCategory;
import org.eu.comm.CommUtil;
import org.eu.comm.FileIconLoader;
import org.eu.comm.FileIconLoader.IconLoadFinishListener;

import org.eu.comm.Util;
import org.eu.weshare.R;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class GridViewAdapter extends ArrayAdapter<ImageItem> implements IconLoadFinishListener {
  private static final String TAG = "GridViewAdapter";
  private Context context;
  private int layoutResourceId;
  private ArrayList<ImageItem> data = new ArrayList<ImageItem>();
  private FileIconLoader mIconLoader;
  private static HashMap<String, Integer> fileExtToIcons = new HashMap<String, Integer>();
  static {
    addItem(new String[] {"mp3"}, R.drawable.file_icon_mp3);
    addItem(new String[] {"wma"}, R.drawable.file_icon_wma);
    addItem(new String[] {"wav"}, R.drawable.file_icon_wav);
    addItem(new String[] {"mid"}, R.drawable.file_icon_mid);
    addItem(new String[] {"mp4", "wmv", "mpeg", "m4v", "3gp", "3gpp", "3g2", "3gpp2", "asf"},
        R.drawable.file_icon_video);
    addItem(new String[] {"jpg", "jpeg", "gif", "png", "bmp", "wbmp"}, R.drawable.file_icon_picture);
    addItem(new String[] {"txt", "log", "xml", "ini", "lrc"}, R.drawable.file_icon_txt);
    addItem(new String[] {"doc", "ppt", "docx", "pptx", "xsl", "xslx",},
        R.drawable.file_icon_office);
    addItem(new String[] {"pdf"}, R.drawable.file_icon_pdf);
    addItem(new String[] {"zip"}, R.drawable.file_icon_zip);
    addItem(new String[] {"mtz"}, R.drawable.file_icon_theme);
    addItem(new String[] {"rar"}, R.drawable.file_icon_rar);
  }

  private static void addItem(String[] exts, int resId) {
    if (exts != null) {
      for (String ext : exts) {
        fileExtToIcons.put(ext.toLowerCase(), resId);
      }
    }
  }

  public static int getFileIcon(String ext) {
    Integer i = fileExtToIcons.get(ext.toLowerCase());
    if (i != null) {
      return i.intValue();
    } else {
      return R.drawable.file_icon_default;
    }

  }

  public GridViewAdapter(Context context, int layoutResourceId, ArrayList<ImageItem> data) {
    super(context, layoutResourceId, data);
    this.layoutResourceId = layoutResourceId;
    this.context = context;
    this.data = data;
    mIconLoader = new FileIconLoader(context, this);
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    Log.i(TAG, "pos:" + position);
    View row = convertView;
    ViewHolder holder = null;

    if (row == null) {
      LayoutInflater inflater = ((Activity) context).getLayoutInflater();
      row = inflater.inflate(layoutResourceId, parent, false);
      holder = new ViewHolder();
      holder.imageTitle = (TextView) row.findViewById(R.id.text);
      holder.image = (ImageView) row.findViewById(R.id.image);
      row.setTag(holder);
      Log.i(TAG, "null");
    } else {
      holder = (ViewHolder) row.getTag();
    }

    ImageItem item = data.get(position);
    holder.imageTitle.setText(item.getTitle());
    holder.image.setScaleType(ImageView.ScaleType.FIT_XY);
    // holder.image.setImageBitmap(item.getImage());
    setIcon(item, holder.image);
    return row;
  }

  static class ViewHolder {
    TextView imageTitle;
    ImageView image;
  }

  private void setIcon(ImageItem fileInfo, ImageView fileImage) {

    String filePath = fileInfo.getFilePath();
    Log.d(TAG, "setIcon:" + filePath);
    long fileId = fileInfo.getDBId();
    String extFromFilename = Util.getExtFromFilename(filePath);

    boolean set = false;
    int id = getFileIcon(extFromFilename);
    fileImage.setImageResource(id);

    mIconLoader.cancelRequest(fileImage);
    FileCategory fc = CommUtil.getCategoryFromPath(filePath);

    set = mIconLoader.loadIcon(fileImage, filePath, fileId, fc);

    if (!set) fileImage.setImageResource(R.drawable.file_icon_default);
  }

  @Override
  public void onIconLoadFinished(ImageView view) {
    // TODO Auto-generated method stub

  }
}
