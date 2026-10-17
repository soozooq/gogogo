package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

import com.zcshou.gogogo.virtualv0.ContainerV2ManifestRouteProbe;

/**
 * A NON-EXPORTED, REGISTERED TIANWAITIAN HOST Activity.
 * System starts THIS Activity, never the uninstalled guest Activity.
 * It reads a single trusted fixture's manifest and executes its safe router.
 */
public final class VirtualContainerV2ProxyActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView progress = new TextView(this);
        progress.setPadding(36, 50, 36, 36);
        progress.setText("天外天 V2 宿主代理：正在解析独立测试 APK ...");
        setContentView(progress);

        Intent inbound = getIntent();
        String action = inbound == null ? null : inbound.getAction();
        String nonce = inbound == null ? null
                : inbound.getStringExtra(VirtualContainerV2Activity.EXTRA_NONCE);
        String payload = inbound == null ? null
                : inbound.getStringExtra(VirtualContainerV2Activity.EXTRA_PAYLOAD);
        if (nonce == null || !nonce.matches("[a-f0-9-]{36}")) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        new Thread(() -> {
            ContainerV2ManifestRouteProbe.Result check =
                    ContainerV2ManifestRouteProbe.run(getApplicationContext(), action, payload);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Intent outbound = new Intent();
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_NONCE, nonce);
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_RESPONSE, check.response);
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_DETAIL, check.detail);
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_DIGEST, check.sha256);
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_ARCHIVE_OK, check.passed);
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_PROXY_PID, Process.myPid());
                outbound.putExtra(VirtualContainerV2Activity.EXTRA_PROXY_UID, Process.myUid());
                setResult(check.passed ? RESULT_OK : RESULT_CANCELED, outbound);
                finish();
            });
        }, "twt-v2-trusted-intent").start();
    }
}
