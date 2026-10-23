package com.soozooq.tianwaitian.containerresearch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProbeActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button open;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 24);
        TextView title = new TextView(this);
        title.setText("独立容器研究\n仅核验未改动的 Fossify 计算器；尚不支持微信。\n未启用定位替换。");
        root.addView(title);
        Button choose = new Button(this);
        choose.setText("选择已核验的计算器 APK");
        choose.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("application/vnd.android.package-archive");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick, 1);
        });
        root.addView(choose);
        open = new Button(this);
        open.setText("打开已导入应用");
        open.setEnabled(false);
        open.setOnClickListener(v -> {
            try { status.setText(GuestImport.requestLaunch() ? "已请求启动；需要实际界面确认运行。" : "启动请求失败。"); }
            catch (RuntimeException failure) { status.setText("启动失败：" + failure.getClass().getSimpleName()); }
        });
        root.addView(open);
        status = new TextView(this);
        status.setText("尚未导入；其他 APK 不在此次执行范围内。");
        root.addView(status);
        setContentView(root);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        open.setEnabled(false);
        status.setText("正在核验和导入……");
        io.execute(() -> {
            String message;
            boolean installed = false;
            File file = null;
            try {
                file = GuestImport.copyAndVerify(getApplicationContext(), uri);
                GuestImport.installVerified(file);
                installed = true;
                message = "导入接口已返回成功；尚未证明应用运行。";
            } catch (Exception failure) { message = "导入失败：" + failure.getClass().getSimpleName(); }
            finally { if (file != null) file.delete(); }
            final String text = message;
            final boolean ready = installed;
            runOnUiThread(() -> { if (!destroyed) { status.setText(text); open.setEnabled(ready); } });
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }
}
