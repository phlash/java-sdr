package com.ashbysoft.java_sdr;

public interface ILogger {
    public void logMsg(String msg);
    public void statusMsg(String msg);
    public void alertMsg(String msg);
}