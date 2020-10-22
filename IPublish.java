package com.ashbysoft.java_sdr;

public interface IPublish {
    public void setPublish(String key, String val);
    public String getPublish(String key, String def);
    public void listen(IPublishListener list);
    public void unlisten(IPublishListener list);
}
