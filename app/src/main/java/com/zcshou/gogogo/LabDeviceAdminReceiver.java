package com.zcshou.gogogo;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class LabDeviceAdminReceiver extends DeviceAdminReceiver {
    public static ComponentName component(Context context) {
        return new ComponentName(context, LabDeviceAdminReceiver.class);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);

        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = component(context);

        try {
            if (dpm != null && dpm.isProfileOwnerApp(context.getPackageName())) {
                dpm.setProfileName(admin, "GoGoGo Lab");
                dpm.setProfileEnabled(admin);
                Toast.makeText(context,
                        "GoGoGo Lab 工作资料已启用 😈",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            Toast.makeText(context,
                    "工作资料创建完成，但自动启用失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
