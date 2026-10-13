package com.zcshou.gogogo;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* 为了启动欢迎页全屏，状态栏被设置了透明，但是会导致其他页面状态栏空白
         * 这里设计如下：
         * 1. 除了 WelcomeActivity 之外的所有 Activity 均继承 BaseActivity
         * 2. WelcomeActivity 单独处理，其他 Activity 手动填充 StatusBar
         * */
        // Settings is now a true primary tab, matching the light/ink status
        // bar used by the other three Tianwaitian destinations.
        getWindow().setStatusBarColor(
                getResources().getColor(R.color.gogogo_bg, this.getTheme()));
        boolean darkTheme = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        android.view.View decor = getWindow().getDecorView();
        int statusFlags = decor.getSystemUiVisibility();
        statusFlags = darkTheme
                ? statusFlags & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                : statusFlags | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(statusFlags);

        setContentView(R.layout.activity_settings);
        TianwaitianBottomNav.attach(this, TianwaitianBottomNav.Tab.SETTINGS);
        // Dedicated entry for the advanced VPN / timezone / Shizuku controls.
        // The full legacy control panel is preserved as a secondary screen.
        android.widget.LinearLayout settingsColumn =
                (android.widget.LinearLayout) findViewById(R.id.settings).getParent();
        com.google.android.material.card.MaterialCardView vpnEntry =
                GoGoUi.navigationTile(this, R.drawable.twt_nav_settings,
                        "VPN 与模拟高级控制",
                        "坐标锁定 · VPN 跟随 · 系统时区 · Shizuku",
                        v -> startActivity(new android.content.Intent(
                                this, SimpleMockActivity.class)));
        android.widget.LinearLayout.LayoutParams vpnEntryLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        vpnEntryLp.setMargins(GoGoUi.dp(this, 16), 0,
                GoGoUi.dp(this, 16), GoGoUi.dp(this, 8));
        settingsColumn.addView(vpnEntry, 1, vpnEntryLp);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings, new FragmentSettings())
            .commit();
        }

        /* 获取默认的顶部的标题栏（安卓称为 ActionBar）*/
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TianwaitianBottomNav.syncSelection(this, TianwaitianBottomNav.Tab.SETTINGS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish(); // back button
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}