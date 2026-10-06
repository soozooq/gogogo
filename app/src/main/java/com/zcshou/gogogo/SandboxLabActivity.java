package com.zcshou.gogogo;

import android.app.AlertDialog;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class SandboxLabActivity extends AppCompatActivity {
    private static final int REQ_PROVISION_PROFILE = 6401;

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
        title.setText("📦 GoGoGo Sandbox Lab 9");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView note = new TextView(this);
        note.setText("这里走 Android 官方 Work Profile / DPC 机制。"
                + "GoGoGo 只负责发起系统 provisioning；真正创建工作资料前，"
                + "Android 会显示自己的确认页面，必须由你手动确认。");
        note.setTextSize(14);
        note.setPadding(0, dp(8), 0, dp(10));
        root.addView(note, matchWrap());

        root.addView(sectionTitle("Work Profile 能力"));
        capabilityView = body();
        root.addView(capabilityView, matchWrap());

        root.addView(sectionTitle("当前用户 / 资料"));
        profilesView = body();
        root.addView(profilesView, matchWrap());

        Button provision = button("🧪 创建实验 Work Profile（系统会再次确认）",
                v -> confirmProvisionManagedProfile());
        root.addView(provision, matchWrap());

        Button refresh = button("↻ 重新探测", v -> refreshSandboxState());
        root.addView(refresh, matchWrap());

        Button userSettings = button("👥 打开系统用户 / 多用户设置", v -> openUserSettings());
        root.addView(userSettings, matchWrap());

        Button accounts = button("💼 打开账号 / 工作资料相关设置", v -> openAccountSettings());
        root.addView(accounts, matchWrap());

        Button observatory = button("🔭 Cross-profile Observatory", v ->
                startActivity(new Intent(this, CrossProfileObservatoryActivity.class)));
        root.addView(observatory, matchWrap());

        Button avf = button("🧪 AVF / pKVM 说明", v -> showAvfPlan());
        root.addView(avf, matchWrap());

        Button plan = button("🧬 查看 Sandbox 下一阶段", v -> showNextStage());
        root.addView(plan, matchWrap());

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
        boolean avfFeature =
                getPackageManager().hasSystemFeature("android.software.virtualization_framework");
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
        caps.append("Android Virtualization Framework：").append(yesNo(avfFeature)).append("\n");
        caps.append("API >= 34（AVF API 起点）：")
                .append(yesNo(android.os.Build.VERSION.SDK_INT >= 34)).append("\n");
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

        if (profileOwner) {
            caps.append("\n结论：GoGoGo 已经是当前工作资料的 Profile Owner 😈");
        } else if (managedUsersFeature && provisioningAllowed) {
            caps.append("\n结论：这台设备可以尝试走 Android 官方 Work Profile provisioning ✅");
        } else if (managedUsersFeature) {
            caps.append("\n结论：系统支持 Managed Users，但当前状态不允许再创建新 Work Profile。");
        } else {
            caps.append("\n结论：ROM 没暴露标准 Managed Users，后面更适合研究应用级沙箱。");
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

    private void confirmProvisionManagedProfile() {
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        boolean allowed = false;
        boolean alreadyOwner = false;
        try {
            if (dpm != null) {
                allowed = dpm.isProvisioningAllowed(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
                alreadyOwner = dpm.isProfileOwnerApp(getPackageName());
            }
        } catch (Throwable ignored) {
        }

        if (alreadyOwner) {
            Toast.makeText(this,
                    "GoGoGo 已经是当前资料的 Profile Owner，不需要再创建",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!allowed) {
            new AlertDialog.Builder(this)
                    .setTitle("当前不能创建 Work Profile")
                    .setMessage("Android 当前状态不允许新的 Managed Profile。"
                            + "常见原因是已经有工作资料、ROM 限制，或者设备策略不允许。"
                            + "\n\nGoGoGo 不会尝试绕过系统限制。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("创建 GoGoGo Lab 工作资料？")
                .setMessage("下一步会跳到 Android 官方的 Work Profile 创建页面。"
                        + "\n\n如果你在系统页面确认，Android 会创建一个隔离的工作资料，"
                        + "并把 GoGoGo 放进去作为该资料的 Profile Owner。"
                        + "\n\n这会改变设备的多用户/工作资料状态；不想创建就直接在系统页取消。")
                .setPositiveButton("打开系统创建页面", (d, w) -> launchManagedProfileProvisioning())
                .setNegativeButton("取消", null)
                .show();
    }

    private void launchManagedProfileProvisioning() {
        try {
            Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            intent.putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    LabDeviceAdminReceiver.component(this));
            startActivityForResult(intent, REQ_PROVISION_PROFILE);
        } catch (Throwable t) {
            Toast.makeText(this,
                    "系统无法启动 Work Profile provisioning："
                            + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROVISION_PROFILE) {
            refreshSandboxState();
            Toast.makeText(this,
                    resultCode == RESULT_OK
                            ? "系统 provisioning 流程已返回"
                            : "Work Profile 创建流程已取消或未完成",
                    Toast.LENGTH_LONG).show();
        }
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

    private void showAvfPlan() {
        boolean avf = getPackageManager()
                .hasSystemFeature("android.software.virtualization_framework");

        TextView text = body();
        text.setText(
                "AVF 系统特性：" + yesNo(avf)
                        + "\nAndroid API：" + android.os.Build.VERSION.SDK_INT
                        + "\n\nAVF 是 Android 官方的虚拟化框架，底层是 pKVM，"
                        + "可以运行 protected VM / Microdroid。"
                        + "\n\n普通第三方 APK 不能直接获得 MANAGE_VIRTUAL_MACHINE 权限，"
                        + "所以 GoGoGo 这阶段先做能力探测和 Shizuku 高权限报告。"
                        + "\n如果以后换到支持的开发设备 / 预装环境，再研究真正 Microdroid payload。");

        new AlertDialog.Builder(this)
                .setTitle("🧪 Android Virtualization Framework")
                .setView(text)
                .setPositiveButton("继续研究 😈", null)
                .show();
    }

    private void showNextStage() {
        TextView text = body();
        text.setText(
                "如果 Work Profile 真创建成功，下一阶段可以继续：\n\n"
                        + "1. 在主空间和工作资料里分别跑 GoGoGo 环境快照。\n"
                        + "2. 比较两边的定位、网络、传感器、包可见性和 Shizuku 权限。\n"
                        + "3. 加 CrossProfileApps 实验，研究两个资料之间允许的跳转边界。\n"
                        + "4. 再判断要不要继续研究 VirtualApp / Twoyi 这种应用级或系统级虚拟化。\n\n"
                        + "这条线全部优先走 Android 官方隔离机制，不做第三方应用风控绕过。");

        new AlertDialog.Builder(this)
                .setTitle("🧪 Sandbox 下一阶段")
                .setView(text)
                .setPositiveButton("继续加 😈", null)
                .show();
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
