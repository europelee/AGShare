package org.upnp.alljoynservice.end;

public class AlljoynErr {
  public int errCode;
  public String  errInfo;
  public AlljoynErr(int errCode, String errInfo)
  {
    this.errCode = errCode;
    this.errInfo = errInfo;
  }
}
