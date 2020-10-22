package com.ashbysoft.java_sdr;

import java.util.Iterator;
import javax.swing.JMenuItem;

public interface IUIComponent {
    public Iterator<JMenuItem> getMenuItems();
    public Iterator<Character> getHotKeys();
    public void hotKey(char c);
}
