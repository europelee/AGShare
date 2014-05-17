package org.upnp.gstreamerutil;

public interface GstMsgListener
{
	public void RecvGstMsg(String message);
	public void CheckGstreamerInited();
}
