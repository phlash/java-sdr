package com.ashbysoft.java_sdr;

public interface IRawHandler {
    // sample buffer contains raw bytes from radio
    public void receive(byte[] buf);
}