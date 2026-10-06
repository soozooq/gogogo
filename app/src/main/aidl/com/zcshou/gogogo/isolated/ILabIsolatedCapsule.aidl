package com.zcshou.gogogo.isolated;

import com.zcshou.gogogo.isolated.IResourceMonitor;

interface ILabIsolatedCapsule {
    void setReferenceMonitor(IResourceMonitor monitor);
    String getIdentityReport();
    String runDirectAccessProbe();
    String requestBrokerSnapshot();
    long echo(long nonce);
    String benchmarkMonitor(int iterations);
}
