package com.zcshou.gogogo.isolated;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;

import com.zcshou.gogogo.LabPolicyEngine;
import com.zcshou.service.ServiceGo;

import org.json.JSONObject;

public class LabReferenceMonitorService extends Service {
    private LabPolicyEngine policyEngine;
    private ServiceGo.ServiceGoBinder serviceGoBinder;
    private boolean serviceGoBound;

    private final ServiceConnection serviceGoConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                serviceGoBinder = (ServiceGo.ServiceGoBinder) service;
                serviceGoBound = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceGoBinder = null;
            serviceGoBound = false;
        }
    };

    private final IResourceMonitor.Stub binder = new IResourceMonitor.Stub() {
        @Override
        public String getBrokerSnapshot() {
            int callingUid = Binder.getCallingUid();
            int callingPid = Binder.getCallingPid();

            try {
                JSONObject json = new JSONObject();
                json.put("monitor_pid", Process.myPid());
                json.put("monitor_uid", Process.myUid());
                json.put("caller_pid", callingPid);
                json.put("caller_uid", callingUid);

                LabPolicyEngine engine = policyEngine;
                json.put("policy", engine == null ? "unavailable" : engine.summary());

                ServiceGo.ServiceGoBinder go = serviceGoBinder;
                json.put("servicego_connected", go != null);

                if (go != null) {
                    json.put("raw_lng", go.getLongitude());
                    json.put("raw_lat", go.getLatitude());
                    json.put("raw_speed_mps", go.getSpeedMps());
                    json.put("published_enabled", go.isPolicyPublishing());
                    json.put("published_lng", go.getPublishedLongitude());
                    json.put("published_lat", go.getPublishedLatitude());
                    json.put("published_accuracy_m", go.getPublishedAccuracyMeters());
                    json.put("published_speed_mps", go.getPublishedSpeedMps());
                    json.put("provenance_source", go.getProvenanceSource());
                    json.put("service_policy", go.getPolicySummary());
                }

                return json.toString(2);
            } catch (Throwable t) {
                return "ReferenceMonitor snapshot failed: "
                        + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            }
        }

        @Override
        public String getMonitorIdentity() {
            return "ReferenceMonitor pid=" + Process.myPid()
                    + " uid=" + Process.myUid()
                    + " callerPid=" + Binder.getCallingPid()
                    + " callerUid=" + Binder.getCallingUid();
        }

        @Override
        public long ping(long nonce) {
            return nonce ^ 0x5A5A5A5AL;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        policyEngine = new LabPolicyEngine(this);
        bindToServiceGoIfRunning();
    }

    private void bindToServiceGoIfRunning() {
        if (serviceGoBound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), serviceGoConnection, 0);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        bindToServiceGoIfRunning();
        return binder;
    }

    @Override
    public void onDestroy() {
        if (serviceGoBound) {
            try {
                unbindService(serviceGoConnection);
            } catch (Throwable ignored) {
            }
        }
        serviceGoBinder = null;
        serviceGoBound = false;

        if (policyEngine != null) {
            try {
                policyEngine.close();
            } catch (Throwable ignored) {
            }
            policyEngine = null;
        }
        super.onDestroy();
    }
}
