package com.soozooq.tianwaitian.fixture;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The View hierarchy and its click handler are defined in the independently
 * built, controlled fixture APK. The host only supplies its Android Context
 * and a private parent container. This is NOT guest Activity virtualization.
 */
public final class GuestUiV1 {
    private final Context context;
    private final StringBuilder clickTrace = new StringBuilder();
    private int clicks;
    private TextView counter;
    private boolean disposed;

    public GuestUiV1(Context context, int restoredClicks) {
        if (context == null) throw new IllegalArgumentException("context");
        this.context = context;
        clicks = requireValidClicks(restoredClicks);
    }

    public View createView() {
        if (disposed) throw new IllegalStateException("guest UI already disposed");
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16f * context.getResources().getDisplayMetrics().density + .5f);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackgroundColor(Color.rgb(33, 39, 64));

        TextView heading = new TextView(context);
        heading.setText("独立 APK 创建的真实 Android View");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17f);
        panel.addView(heading);

        counter = new TextView(context);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(16f);
        panel.addView(counter);
        refresh();

        Button tap = new Button(context);
        tap.setText("点我！由独立 APK 内的代码处理点击");
        tap.setOnClickListener(v -> {
            if (disposed) return;
            clicks++;
            if (clickTrace.length() < 1000) {
                clickTrace.append("GUEST CLICK #").append(clicks).append('\n');
            }
            refresh();
        });
        panel.addView(tap);
        return panel;
    }

    public int getClickCount() { return clicks; }

    public String snapshot() {
        return "guestViewClicks=" + clicks
                + "；disposed=" + disposed
                + "；guestViewClass=" + GuestUiV1.class.getName()
                + "\n" + clickTrace;
    }

    public void dispose() {
        disposed = true;
        counter = null;
    }

    public static int requireValidClicks(int count) {
        if (count < 0 || count > 100000) {
            throw new IllegalArgumentException("invalid restored click count");
        }
        return count;
    }

    public static String counterLabel(int count) {
        return "来自独立 APK 的点击计数：" + requireValidClicks(count);
    }

    private void refresh() {
        if (counter != null) counter.setText(counterLabel(clicks));
    }
}
