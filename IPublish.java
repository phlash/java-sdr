package com.ashbysoft.java_sdr;

public interface IPublish {
    public void setPublish(String key, Object val);
    public Object getPublish(String key, Object def);
    public void listen(IPublishListener list);
    public void unlisten(IPublishListener list);
}
