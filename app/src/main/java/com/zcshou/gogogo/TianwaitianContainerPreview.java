package com.zcshou.gogogo;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Explicit user-selected WGS84 handoff; the receiver only prefills its ordinary UI. */
final class TianwaitianContainerPreview {
    private static final String PACKAGE = "com.soozooq.tianwaitian.containerresearch";
    private static final String ACTION = PACKAGE + ".action.PREFILL_TARGET_V1";

    private TianwaitianContainerPreview() {}

    private static ComponentName component() {
        return new ComponentName(PACKAGE, PACKAGE + ".ProbeActivity");
    }

    static boolean isAvailable(Context context) {
        if (Build.SUPPORTED_ABIS.length == 0 || !"arm64-v8a".equals(Build.SUPPORTED_ABIS[0])) return false;
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(component(), 0);
            return info.enabled && info.exported && info.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    static void chooseApp(Activity activity, double longitude, double latitude) {
        final LabHomeCoordinates.Point point;
        try { point = LabHomeCoordinates.parse(Double.toString(longitude), Double.toString(latitude)); }
        catch (IllegalArgumentException invalid) {
            Toast.makeText(activity, "请先选择有效地点。", Toast.LENGTH_SHORT).show(); return;
        }
        new MaterialAlertDialogBuilder(activity).setTitle("将选点带入原版应用预览")
                .setItems(new String[] {"微信", "抖音"}, (dialog, index) -> {
                    Intent intent = new Intent(ACTION).setComponent(component())
                            .putExtra("coordinate_system", "WGS84")
                            .putExtra("guest_package", index == 0 ? "com.tencent.mm" : "com.ss.android.ugc.aweme")
                            .putExtra("longitude", point.longitude).putExtra("latitude", point.latitude);
                    try { activity.startActivity(intent); }
                    catch (ActivityNotFoundException | SecurityException unavailable) {
                        Toast.makeText(activity, "预览入口暂不可用，请返回后重试。", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }
}
