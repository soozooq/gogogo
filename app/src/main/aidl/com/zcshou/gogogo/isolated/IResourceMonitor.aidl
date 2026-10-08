package com.zcshou.gogogo.isolated;

interface IResourceMonitor {
    String getBrokerSnapshot();
    String getMonitorIdentity();
    long ping(long nonce);
}
