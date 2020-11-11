package com.ashbysoft.java_sdr;

public interface IAudio {
    public AudioDescriptor getAudioDescriptor();
    public AudioSource[] getAudioSources();
    public AudioSource getAudioSource();
    public void setAudioSource(String name);
    public void setAudioSource(AudioSource src);
    public void start();
    public void pause();
    public void resume();
    public void stop();
    public int getICorrection();
    public int getQCorrection();
    public void setICorrection(int i);
    public void setQCorrection(int q);
    public void addHandler(IAudioHandler hand);
    public void remHandler(IAudioHandler hand);
    public void addRawHandler(IRawHandler hand);
    public void remRawHandler(IRawHandler hand);
}
