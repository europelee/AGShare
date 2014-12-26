package org.eu.comm;

public class MMPlayerOpenEvent {
  private boolean mIsOpen;

  public MMPlayerOpenEvent(boolean isOpen) {
    this.mIsOpen = isOpen;
  }

  public boolean getOpenStatus() {
    return mIsOpen;
  }
}
