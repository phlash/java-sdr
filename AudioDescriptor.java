package com.ashbysoft.java_sdr;

public class AudioDescriptor {
    public int rate;    // samples/sec
    public int bits;    // bits/sample
    public int chns;    // no. channels
    public int size;    // sample frame size (in bytes)
    public int blen;    // buffer length (in bytes)
    public AudioDescriptor(int r, int b, int c, int s, int l) {
        rate = r;
        bits = b;
        chns = c;
        size = s;
        blen = l;
    }
}
