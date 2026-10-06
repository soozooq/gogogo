package com.zcshou.gogogo;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class SandboxLabActivity extends AppCompatActivity {
    private TextView capabilityView;
    private TextView profilesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshSandboxState();
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("📦 GoGoGo Sandbox Lab");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView note = new TextView(this);
        note.setText("这一页先探测 Android 原生 Work Profile / 多用户隔离能力。"
                + "不会自动把 GoGoGo 设成设备管理员，也不会偷偷创建工作资料。");
        note.setTextSize(14);
        note.setPadding(0, dp(8), 0, dp(10));
        root.addView(note, matchWrap());

        root.addView(sectionTitle("Work Profile 能力"));
        capabilityView = body();
        root.addView(capabilityView, matchWrap());

        root.addView(sectionTitle("当前用户 / 资料"));
        profilesView = body();
        root.addView(profilesView, matchWrap());

        Button refresh = button("↻ 重新探测", v -> refreshSandboxState());
        root.addView(refresh, matchWrap());

        Button userSettings = button("👥 打开系统用户 / 多用户设置", v -> openUserSettings());
        root.addView(userSettings, matchWrap());

        Button accounts = button("💼 打开账号 / 工作资料相关设置", v -> openAccountSettings());
        root.addView(accounts, matchWrap());

        Button copyPlan = button("🧪 查看下一阶段沙箱计划", v -> showNextStage());
        root.addView(copyPlan, matchWrap());

        Button back = button("← 返回实验仪表盘", v -> finish());
        root.addView(back, matchWrap());

        setContentView(scroll);
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setPadding(0, dp(14), 0, dp(4));
        return v;
    }

    private TextView body() {
        TextView v = new TextView(this);
        v.setTextSize(14);
        v.setTextIsSelectable(true);
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        return v;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshSandboxState() {
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);

        boolean managedUsersFeature =
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
        boolean profileOwner = false;
        boolean deviceOwner = false;
        boolean provisioningAllowed = false;
        boolean currentManaged = false;

        try {
            if (dpm != null) {
                profileOwner = dpm.isProfileOwnerApp(getPackageName());
                deviceOwner = dpm.isDeviceOwnerApp(getPackageName());
                provisioningAllowed = dpm.isProvisioningAllowed(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (um != null) currentManaged = um.isManagedProfile();
        } catch (Throwable ignored) {
        }

        StringBuilder caps = new StringBuilder();
        caps.append("Managed Users 系统特性：").append(yesNo(managedUsersFeature)).append("\n");
        caps.append("当前是否 Work Profile：").append(yesNo(currentManaged)).append("\n");
        caps.append("GoGoGo = Profile Owner：").append(yesNo(profileOwner)).append("\n");
        caps.append("GoGoGo = Device Owner：").append(yesNo(deviceOwner)).append("\n");
        caps.append("系统允许创建 Managed Profile：").append(yesNo(provisioningAllowed)).append("\n");
        try {
            long currentSerial = um == null ? -1L
                    : um.getSerialNumberForUser(Process.myUserHandle());
            caps.append("当前 UserHandle：").append(Process.myUserHandle()).append("\n");
            caps.append("当前用户 serial：").append(currentSerial).append("\n");
        } catch (Throwable ignored) {
            caps.append("当前 UserHandle：").append(Process.myUserHandle()).append("\n");
        }

        if (managedUsersFeature && provisioningAllowed) {
            caps.append("\n结论：这台设备具备继续做原生 Work Profile 沙箱实验的基础 ✅");
        } else if (managedUsersFeature) {
            caps.append("\n结论：系统支持 Managed Users，但当前状态不允许直接创建新 Work Profile。");
        } else {
            caps.append("\n结论：ROM 没暴露标准 Managed Users 特性，后续更适合研究应用级沙箱。");
        }
        capabilityView.setText(caps.toString());

        StringBuilder profiles = new StringBuilder();
        if (um == null) {
            profiles.append("UserManager 不可用");
        } else {
            try {
                List<UserHandle> handles = um.getUserProfiles();
                profiles.append("可见资料数量：").append(handles.size()).append("\n");
                UserHandle me = Process.myUserHandle();

                for (int i = 0; i < handles.size(); i++) {
                    UserHandle handle = handles.get(i);
                    long serial = um.getSerialNumberForUser(handle);
                    boolean quiet = false;
                    try {
                        quiet = um.isQuietModeEnabled(handle);
                    } catch (Throwable ignored) {
                    }

                    profiles.append("\n#").append(i + 1)
                            .append(handle.equals(me) ? "（当前）" : "")
                            .append("\n  UserHandle: ").append(handle)
                            .append("\n  serial: ").append(serial)
                            .append("\n  quiet mode: ").append(yesNo(quiet));
                }
            } catch (Throwable t) {
                profiles.append("读取用户资料失败：").append(t.getClass().getSimpleName());
            }
        }
        profilesView.setText(profiles.toString());
    }

    private void openUserSettings() {
        try {
            startActivity(new Intent("android.settings.USER_SETTINGS"));
        } catch (Throwable first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this, "系统没有可打开的用户设置页", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openAccountSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
        } catch (Throwable first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this, "系统没有可打开的账号设置页", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showNextStage() {
        TextView text = body();
        text.setText(
                "下一阶段可以继续加：\n\n"
                        + "1. GoGoGo 自己作为实验 DPC，走 Android 官方 Managed Profile provisioning。\n"
                        + "2. 在工作资料里部署 GoGoGo 测试组件，验证位置 / 网络 / 传感器隔离边界。\n"
                        + "3. 做主空间 vs 工作资料的环境快照对比。\n"
                        + "4. 再评估是否值得研究 VirtualApp / Twoyi 那种应用级或系统级虚拟化。\n\n"
                        + "这里先不自动 Provision，避免误操作把设备管理状态搞乱。");

        new android.app.AlertDialog.Builder(this)
                .setTitle("🧪 Sandbox 下一阶段")
                .setView(text)
                .setPositiveButton("懂了，继续加 😈", null)
                .show();
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
