package org.upnp.alljoynservice.end;

/**
 * for app layer, observe succ and fail info
 * 
 * IAlljoynMsgListener
 * 
 * 2014-7-21 обнГ1:58:17
 * @version 1.0.0
 *
 */
public interface IAlljoynMsgListener {
  public void  onSucc(String msg, int msgCode);
  public void  onFail(AlljoynErr err);
}
