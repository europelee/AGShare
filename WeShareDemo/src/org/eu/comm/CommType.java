package org.eu.comm;

public class CommType {
  public final static String VIDEO_TYPE[] =
      new String[] {"avi", "mkv", "webm", "ogv", "mp4", "mov"};

  public final static String MUSIC_TYPE[] = new String[] {"flac", "mp3", "ogg", "aac"};

  public final static String PICTURE_TYPE[] = new String[] {"png", "jpg"};

  public final static int VIDEO_PAGE_INDEX = 0;
  public final static int MUSIC_PAGE_INDEX = 1;
  public final static int PICTURE_PAGE_INDEX = 2;
  public final static int SEARCH_PAGE_INDEX = 3;

  public final static int MEDIA_WIDTH = 320;
  public final static int MEDIA_HEIGHT = 240;

  public final static int MAX_MEDIA_WIDTH = 1920;
  public final static int MAX_MEDIA_HEIGHT = 1080;

  public static int mDevWidth = 0;
  public static int mDevHeight = 0;

  public enum FileCategory {
    All, Music, Video, Picture, Theme, Doc, Zip, Apk, Custom, Other, Favorite
  }

}
