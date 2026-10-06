package com.zcshou.gogogo.shizuku;

interface ILabPrivilegedService {
    void destroy() = 16777114;
    String getIdentityReport() = 1;
    String getLocationReport() = 2;
    String getSystemReport() = 3;
    String getUserPolicyReport() = 4;
}
