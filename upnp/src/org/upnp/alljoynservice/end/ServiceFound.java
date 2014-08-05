package org.upnp.alljoynservice.end;

public class ServiceFound {
  public String mServiceNameFound;
  public short  mServicePortFound;
  public ServiceFound(String name, short port){
    mServiceNameFound = name;
    mServicePortFound = port;
  }
}
