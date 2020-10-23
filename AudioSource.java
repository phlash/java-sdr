package com.ashbysoft.java_sdr;

public class AudioSource {
    private String m_name;
    private String m_desc;

    public AudioSource(String n, String d) {
        m_name = n;
        m_desc = d;
    }
    public String toString() {
        return m_name + " / " + m_desc;
    }
    public boolean equals(Object o) {
        if (o!=null && o instanceof AudioSource) {
            AudioSource a = (AudioSource)o;
            if (a.m_name.equals(m_name) && a.m_desc.equals(m_desc))
                return true;
        }
        return false;
    }
    public String getName() { return m_name; }
    public String getDesc() { return m_desc; }
}