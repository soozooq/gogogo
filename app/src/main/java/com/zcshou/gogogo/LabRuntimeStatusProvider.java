package com.zcshou.gogogo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import com.zcshou.service.ServiceGo;

/**
 * Private, same-UID cross-process observation of the main-process ServiceGo
 * flag. Calls do not start the mock service or request a location.
 */
public final class LabRuntimeStatusProvider extends ContentProvider {
    public static final String AUTHORITY_SUFFIX = ".lab.runtime";
    public static final String METHOD_OBSERVE = "observe";

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_OBSERVE.equals(method)) return null;
        Bundle result = new Bundle();
        result.putBoolean("running", ServiceGo.sRunning);
        result.putLong("observed_elapsed_ms", SystemClock.elapsedRealtime());
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        return null;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        return 0;
    }
}
