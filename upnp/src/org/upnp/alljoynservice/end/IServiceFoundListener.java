package org.upnp.alljoynservice.end;

/**
 * 
 * 
 * IServiceFoundListener
 * 
 * for app layer, add/remove servicefound
 * 2014-7-21 обнГ4:42:25
 * @version 1.0.0
 *
 */
public interface IServiceFoundListener {
  public void addServiceFound(ServiceFound service);
  public void removeServiceFound(ServiceFound service);
}
