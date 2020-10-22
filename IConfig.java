package com.ashbysoft.java_sdr;

public interface IConfig {
    public String getConfig(String key, String def);
    public int getIntConfig(String key, int def);
    public void setConfig(String key, String val);
    public void setIntConfig(String key, int val);
}