package com.ashbysoft.java_sdr;

import java.nio.ByteBuffer;

public interface IAudioHandler {
    public void receive(ByteBuffer buf);
}