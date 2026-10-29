package com.soozooq.tianwaitian.containerresearch;

import android.app.Application;
import android.content.Context;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.app.configuration.ClientConfiguration;

public final class ProbeApplication extends Application {
    @Override protected void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        BlackBoxCore.get().doAttachBaseContext(context, new ClientConfiguration() {
            @Override public String getHostPackageName() { return context.getPackageName(); }
            @Override public boolean isEnableDaemonService() { return false; }
            @Override public boolean isEnableLauncherActivity() { return false; }
        });
    }

    @Override public void onCreate() {
        super.onCreate();
        BlackBoxCore.get().doCreate();
    }
}
