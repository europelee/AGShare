package org.upnp.alljoynservice.end;

/**
 * 
 * 
 * ISessionStatusListener for cli 2014-7-21 ионГ8:59:49
 * 
 * @version 1.0.0
 * 
 */
public interface ISessionStatusListener {
  public void getSessionStatus(String serviceName, boolean status);

  public void addJoiner(long sessionId, String joinerName);

  public void delJoiner(long sessionId, String joinerName);
}
