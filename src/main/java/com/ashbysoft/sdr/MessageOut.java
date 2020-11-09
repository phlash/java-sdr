package com.ashbysoft.sdr;

// Interface implemented by message output components (us!)
interface MessageOut {
    public void logMsg(String s);
    public void statusMsg(String s);
}
