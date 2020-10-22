package com.ashbysoft.java_sdr;

import java.nio.ByteBuffer;

public interface IAudio {
    public AudioDescriptor getAudioDescriptor();
    public String[] getAudioDevices();
    public String getAudioSource();
    public void setAudioSource(String dev);
    public void Start();
    public void Pause();
    public void Resume();
    public void Stop();
    public void addHandler(IAudioHandler hand);
    public void remHandler(IAudioHandler hand);
}
