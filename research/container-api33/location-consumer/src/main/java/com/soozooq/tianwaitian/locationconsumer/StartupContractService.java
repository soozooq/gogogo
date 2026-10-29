package com.soozooq.tianwaitian.locationconsumer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Only named when creating a test PendingIntent; this control never sends it. */
public final class StartupContractService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
