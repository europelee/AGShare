package org.eu.weshare.mediaplayservice;

/**
 * 
 * 
 * IInputSourceListener
 * for UPnPService sending media stream to GstService 
 * 2014-8-28 обнГ12:08:59
 * @version 1.0.0
 *
 */
public interface IInputSourceListener {
  public void  setRecvLen(int len);
  public void  inputData(byte[] data);
}
