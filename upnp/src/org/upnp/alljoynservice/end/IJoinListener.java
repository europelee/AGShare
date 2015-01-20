package org.upnp.alljoynservice.end;

/**
 * 
 * 
 * IJoinListener for service monitoring cli join/leave 2014-7-22 ионГ8:31:27
 * 
 * @version 1.0.0
 * 
 */
public interface IJoinListener {
  public void addJoiner(long sessionId, String joinerName);

  public void delJoiner(long sessionId, String joinerName);
  
  public void getSessionStatus(String serviceName, boolean sessionStatus);
}
