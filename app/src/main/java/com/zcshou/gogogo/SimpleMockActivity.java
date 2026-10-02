package com.zcshou.gogogo;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;
import com.zcshou.utils.GoUtils;

public class SimpleMockActivity extends AppCompatActivity {
    private EditText longitudeInput;
    private EditText latitudeInput;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("微信定位测试版（无地图）");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("直接输入 WGS-84 坐标。\n妙瓦底已预填：98.50895, 16.68914");
        hint.setTextSize(16);
        hint.setPadding(0, pad, 0, pad);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        longitudeInput = new EditText(this);
        longitudeInput.setHint("经度，例如 98.50895");
        longitudeInput.setText("98.50895");
        longitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(longitudeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        latitudeInput = new EditText(this);
        latitudeInput.setHint("纬度，例如 16.68914");
        latitudeInput.setText("16.68914");
        latitudeInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(latitudeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button startButton = new Button(this);
        startButton.setText("开始模拟");
        root.addView(startButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button stopButton = new Button(this);
        stopButton.setText("停止模拟");
        root.addView(stopButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setText("状态：未启动");
        statusView.setTextSize(16);
        statusView.setPadding(0, pad, 0, 0);
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        startButton.setOnClickListener(v -> startMock());
        stopButton.setOnClickListener(v -> stopMock());
    }

    private void startMock() {
        final double lng;
        final double lat;

        try {
            lng = Double.parseDouble(longitudeInput.getText().toString().trim());
            lat = Double.parseDouble(latitudeInput.getText().toString().trim());
        } catch (Exception e) {
            statusView.setText("状态：坐标格式不正确");
            return;
        }

        if (lng < -180.0 || lng > 180.0 || lat < -90.0 || lat > 90.0) {
            statusView.setText("状态：坐标超出范围");
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            statusView.setText("状态：请先开启系统定位");
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {
            statusView.setText("状态：请先允许悬浮窗");
            GoUtils.showEnableFloatWindowDialog(this);
            return;
        }

        if (!GoUtils.isAllowMockLocation(this)) {
            statusView.setText("状态：请在开发者选项中把本应用设为模拟位置应用");
            GoUtils.showEnableMockLocationDialog(this);
            return;
        }

        Intent intent = new Intent(this, ServiceGo.class);
        intent.putExtra(MainActivity.LNG_MSG_ID, lng);
        intent.putExtra(MainActivity.LAT_MSG_ID, lat);
        intent.putExtra(MainActivity.ALT_MSG_ID, 55.0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        statusView.setText("状态：模拟中\n经度 " + lng + "\n纬度 " + lat);
    }

    private void stopMock() {
        stopService(new Intent(this, ServiceGo.class));
        statusView.setText("状态：已停止");
    }
}
