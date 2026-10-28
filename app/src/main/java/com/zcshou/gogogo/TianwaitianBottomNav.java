package com.zcshou.gogogo;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

/**
 * The same native four-tab bottom navigation on all Tianwaitian primary screens.
 *
 * The original page view gets real weighted space ABOVE the navigation bar;
 * the bar does not cover map gestures, preference rows or scrollable diagnostics.
 * Reorder-to-front keeps each tab's existing state without stacking duplicates
 * every time a user revisits it. Secondary screens retain normal Back behavior.
 */
public final class TianwaitianBottomNav {
    public enum Tab {
        LOCATION(R.id.twt_tab_location, TianwaitianHomeActivity.class),
        DIAGNOSTICS(R.id.twt_tab_diagnostics, LabHubActivity.class),
        AUDIT(R.id.twt_tab_audit, LabPrivacyAuditActivity.class),
        SETTINGS(R.id.twt_tab_settings, SettingsActivity.class);

        final int menuId;
        final Class<? extends AppCompatActivity> destination;
        Tab(int id, Class<? extends AppCompatActivity> target) {
            menuId = id;
            destination = target;
        }
    }

    private TianwaitianBottomNav() {}

    /**
     * Invoke immediately after the primary Activity calls setContentView().
     * No fragments or content ids are replaced; only the existing page root
     * is moved into a vertical shell with a bottom navigation control.
     */
    public static void attach(AppCompatActivity activity, Tab selected) {
        FrameLayout host = activity.findViewById(android.R.id.content);
        if (host == null || host.getChildCount() != 1) {
            throw new IllegalStateException("Expected one primary screen root");
        }
        View page = host.getChildAt(0);
        host.removeView(page);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(GoGoUi.color(activity, R.color.gogogo_bg));
        shell.addView(page, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // A crisp subtle divider keeps the floating map deck distinct from the bar.
        View divider = new View(activity);
        divider.setBackgroundColor(GoGoUi.color(activity, R.color.gogogo_border));
        shell.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, GoGoUi.dp(activity, 1)));

        BottomNavigationView nav = new BottomNavigationView(activity);
        nav.setId(R.id.twt_bottom_navigation);
        nav.inflateMenu(R.menu.twt_bottom_nav);
        nav.setBackgroundColor(GoGoUi.color(activity, R.color.gogogo_surface));
        nav.setElevation(0);
        nav.setItemIconSize(GoGoUi.dp(activity, 23));
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setItemHorizontalTranslationEnabled(false);
        ColorStateList tint = new ColorStateList(
                new int[][] {
                        new int[]{android.R.attr.state_checked},
                        new int[]{}},
                new int[]{
                        GoGoUi.color(activity, R.color.twt_nav_selected),
                        GoGoUi.color(activity, R.color.gogogo_text_muted)});
        nav.setItemIconTintList(tint);
        nav.setItemTextColor(tint);
        // Do not set MenuItem.checked alone: NavigationBarView tracks its own
        // selectedItemId internally. Divergence caused the one-tab-off purple
        // highlight seen on the actual device screenshots.
        nav.setSelectedItemId(selected.menuId);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == selected.menuId) return true;
            for (Tab candidate : Tab.values()) {
                if (item.getItemId() == candidate.menuId) {
                    Intent intent = new Intent(activity, candidate.destination);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(intent);
                    // Never let the outgoing screen select the destination tab
                    // while its old content is still visible during transition.
                    // The incoming (or reordered) Activity owns that selection.
                    return false;
                }
            }
            return false;
        });
        shell.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Refresh internal presenter selection after reordering an existing tab. */
    public static void syncSelection(AppCompatActivity activity, Tab current) {
        BottomNavigationView nav = activity.findViewById(R.id.twt_bottom_navigation);
        if (nav == null) return;
        if (nav.getSelectedItemId() != current.menuId) {
            nav.setSelectedItemId(current.menuId);
        }
    }
}
