package com.soozooq.tianwaitian.locationconsumer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Own private provider in a separate process. No device data, location or network reads. */
public final class StartupContractProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String argument, Bundle extras) {
        if (!"startup".equals(method) || argument == null || argument.length() > 128) {
            throw new IllegalArgumentException("Own startup contract only");
        }
        Bundle result = new Bundle();
        result.putString("scope", "OWN_PRIVATE_PROVIDER_CONTRACT");
        result.putString("nonce", argument);
        result.putInt("provider_pid", Process.myPid());
        result.putInt("caller_pid", Binder.getCallingPid());
        return result;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] arguments, String order) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] arguments) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection,
            String[] arguments) { throw new UnsupportedOperationException(); }
}
