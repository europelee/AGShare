package org.eu.comm;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eu.comm.CommType.FileCategory;
import org.eu.comm.MediaFile.MediaFileType;
import org.eu.weshare.fragment.ImageItem;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;


public class CommUtil {

  private static final String TAG = "CommUtil";
  private static List<String> mFileList = new ArrayList<String>();

  public static void initSearch() {
    mFileList.clear();
  }

  public static void search(File file, String[] ext) {
    if (file != null) {
      if (file.isDirectory()) {
        File[] listFile = file.listFiles();
        if (listFile != null) {
          for (int i = 0; i < listFile.length; i++) {
            search(listFile[i], ext);
          }
        }
      } else {
        String filename = file.getAbsolutePath();
        for (int i = 0; i < ext.length; i++) {
          if (filename.endsWith(ext[i]) || filename.endsWith(ext[i].toUpperCase())) {
            mFileList.add(filename);
            break;
          }
        }
      }
    }
  }

  public static List<String> getSearchList() {
    return mFileList;
  }

  /**
   * code coped from https://github.com/zstar2013/android_apk_icon_test
   * 
   * @param apkPaht
   * @return
   */
  public static Drawable getApkIcon(String apkPath, Context context) {
    String PATH_PackageParser = "android.content.pm.PackageParser";
    String PATH_AssetManager = "android.content.res.AssetManager";
    try {
      Class pkgParserCls = Class.forName(PATH_PackageParser);
      Class[] typeArgs = new Class[1];
      typeArgs[0] = String.class;
      Constructor pkgParserCt = pkgParserCls.getConstructor(typeArgs);
      Object[] valueArgs = new Object[1];
      valueArgs[0] = apkPath;
      Object pkgParser = pkgParserCt.newInstance(valueArgs);
      Log.d(TAG, "pkgParser:" + pkgParser.toString());

      DisplayMetrics metrics = new DisplayMetrics();
      metrics.setToDefaults();
      typeArgs = new Class[4];
      typeArgs[0] = File.class;
      typeArgs[1] = String.class;
      typeArgs[2] = DisplayMetrics.class;
      typeArgs[3] = Integer.TYPE;
      Method pkgParser_parsePackageMtd = pkgParserCls.getDeclaredMethod("parsePackage", typeArgs);
      valueArgs = new Object[4];
      valueArgs[0] = new File(apkPath);
      valueArgs[1] = apkPath;
      valueArgs[2] = metrics;
      valueArgs[3] = 0;
      Object pkgParserPkg = pkgParser_parsePackageMtd.invoke(pkgParser, valueArgs);
      Field appInfoFld = pkgParserPkg.getClass().getDeclaredField("applicationInfo");
      ApplicationInfo info = (ApplicationInfo) appInfoFld.get(pkgParserPkg);

      Log.d(TAG, "pkg:" + info.packageName + "uid=" + info.uid);

      Class assetMagCls = Class.forName(PATH_AssetManager);
      Constructor assetMagCt = assetMagCls.getConstructor((Class[]) null);
      Object assetMag = assetMagCt.newInstance((Object[]) null);
      typeArgs = new Class[1];
      typeArgs[0] = String.class;
      Method assetMag_addAssetPathMtd = assetMagCls.getDeclaredMethod("addAssetPath", typeArgs);
      valueArgs = new Object[1];
      valueArgs[0] = apkPath;
      assetMag_addAssetPathMtd.invoke(assetMag, valueArgs);
      Resources res = context.getResources();
      typeArgs = new Class[3];
      typeArgs[0] = assetMag.getClass();
      typeArgs[1] = res.getDisplayMetrics().getClass();
      typeArgs[2] = res.getConfiguration().getClass();
      Constructor resct = Resources.class.getConstructor(typeArgs);
      valueArgs = new Object[3];
      valueArgs[0] = assetMag;
      valueArgs[1] = res.getDisplayMetrics();
      valueArgs[2] = res.getConfiguration();
      res = (Resources) resct.newInstance(valueArgs);
      CharSequence label = null;
      if (info.labelRes != 0) {
        label = res.getText(info.labelRes);
      }
      Log.d(TAG, "label=" + label);

      if (info.icon != 0) {
        Drawable icon = res.getDrawable(info.icon);
        return icon;

      }
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchFieldException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InstantiationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return null;
  }

  public static Bitmap getBitmap(Drawable drawable) {
    Bitmap bitmap = Bitmap.createBitmap(

    drawable.getIntrinsicWidth(),

    drawable.getIntrinsicHeight(),

    drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888

    : Bitmap.Config.RGB_565);

    Canvas canvas = new Canvas(bitmap);

    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

    drawable.draw(canvas);

    return bitmap;
  }

  /**
   * code coped from http://blog.csdn.net/akon_vm/article/details/7419274 获取视频的缩略图
   * 先通过ThumbnailUtils来创建一个视频的缩略图，然后再利用ThumbnailUtils来生成指定大小的缩略图。
   * 如果想要的缩略图的宽和高都小于MICRO_KIND，则类型要使用MICRO_KIND作为kind的值，这样会节省内存。
   * 
   * @param videoPath 视频的路径
   * @param width 指定输出视频缩略图的宽度
   * @param height 指定输出视频缩略图的高度度
   * @param kind 参照MediaStore.Images.Thumbnails类中的常量MINI_KIND和MICRO_KIND。 其中，MINI_KIND: 512 x
   *        384，MICRO_KIND: 96 x 96
   * @return 指定大小的视频缩略图
   */
  public static Bitmap getVideoThumbnail(String videoPath, int width, int height, int kind) {
    Bitmap bitmap = null;
    // 获取视频的缩略图
    bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);

    if (null != bitmap)
      bitmap =
          ThumbnailUtils.extractThumbnail(bitmap, width, height,
              ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    else {

    }
    return bitmap;
  }

  /**
   * 
   * 功能 通过album_id查找 album_art 如果找不到返回null
   * 
   * @param album_id
   * @return album_art
   */
  public static String getAlbumArt(long album_id, Context context) {
    String mUriAlbums = "content://media/external/audio/albums";
    String[] projection = new String[] {"album_art"};
    Cursor cur =
        context.getContentResolver().query(Uri.parse(mUriAlbums + "/" + Long.toString(album_id)),
            projection, null, null, null);
    String album_art = null;
    if (cur.getCount() > 0 && cur.getColumnCount() > 0) {
      cur.moveToNext();
      album_art = cur.getString(0);
    } else {
      Log.d(TAG, "album_art is null");
    }
    cur.close();
    cur = null;
    return album_art;
  }

  public static ArrayList<ImageItem> getMusicThumbnail(Context context) {
    final ArrayList<ImageItem> imageItems = new ArrayList<ImageItem>();

    Cursor cursor =
        context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            new String[] {MediaStore.Audio.Media.DURATION, // 音乐的总时间
                MediaStore.Audio.Media.ARTIST, // 艺术家
                MediaStore.Audio.Media._ID, // id号
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DISPLAY_NAME, // 音乐文件名
                MediaStore.Audio.Media.DATA, // 音乐文件的路径
                MediaStore.Audio.Media.ALBUM}, null, null, null);

    Bitmap bitmap = null;

    if (cursor.moveToFirst()) {
      do {
        String fileName =
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
        String filePath =
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        // String album = cursor.getString(cursor
        // .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
        int albumId = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));

        // Log.i(TAG, fileName + " " + filePath + " " + albumId);
        String album = getAlbumArt(albumId, context);
        Log.i(TAG, fileName + " " + filePath + " " + album);
        bitmap = BitmapFactory.decodeFile(album);
        imageItems.add(new ImageItem(bitmap, fileName, filePath, 0));

      } while (cursor.moveToNext());
    }

    cursor.close();
    return imageItems;
  }

  /**
   * 
   * getPictureThumbnail(the method description)
   * 
   * @param filePath
   * @param width
   * @param height
   * @return Bitmap
   * @exception
   * @since 1.0.0
   */
  public static Bitmap getPictureThumbnail(String filePath, int width, int height) {
    Bitmap bitmap = null;
    // 获取视频的缩略图
    bitmap = BitmapFactory.decodeFile(filePath, null);

    if (null != bitmap)
      bitmap =
          ThumbnailUtils.extractThumbnail(bitmap, width, height,
              ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    else {

    }
    return bitmap;
  }

  public static byte[] subBytes(byte[] src, int begin, int count) {
    byte[] bs = new byte[count];
    for (int i = begin; i < begin + count; i++)
      bs[i - begin] = src[i];
    return bs;
  }


  public static FileCategory getCategoryFromPath(String path) {
    MediaFileType type = MediaFile.getFileType(path);
    if (type != null) {
      if (MediaFile.isAudioFileType(type.fileType)) return FileCategory.Music;
      if (MediaFile.isVideoFileType(type.fileType)) return FileCategory.Video;
      if (MediaFile.isImageFileType(type.fileType)) return FileCategory.Picture;
      if (Util.sDocMimeTypesSet.contains(type.mimeType)) return FileCategory.Doc;
    }

    int dotPosition = path.lastIndexOf('.');
    if (dotPosition < 0) {
      return FileCategory.Other;
    }

    return FileCategory.Other;
  }
}
