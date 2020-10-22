package com.ashbysoft.java_sdr;

import com.ashbysoft.java_sdr.IUIComponent;

public interface IUIHost {
    public void addTabbedComponent(IUIComponent comp);
    public void remTabbedComponent(IUIComponent comp);
}
