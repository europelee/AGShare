package org.upnp.alljoynservice.end;

public interface ServiceConfig
{

	public void setConfig(String serviceName, short port);

	public void setObjectPath(String servicePath);
}
