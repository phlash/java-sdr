package com.ashbysoft.java_sdr;

public interface IAudioHandler {
    // sample buffer always contains IQ data (2*floats/sample) in -1.0/1.0 range
    public void receive(float[] buf);
}