package org.eu.weshare.fragment;

import android.graphics.Bitmap;

public class ImageItem {
  private Bitmap image;
  private String title;
  private String filePath;
  private long dbid;

  public ImageItem(Bitmap image, String title, String filePath, long dbid) {
    super();
    this.image = image;
    this.title = title;
    this.filePath = filePath;
    this.dbid = dbid;
  }

  public long getDBId() {
    return dbid;
  }

  public Bitmap getImage() {
    return image;
  }

  public void setImage(Bitmap image) {
    this.image = image;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getFilePath() {
    return this.filePath;
  }
}
