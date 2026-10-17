package com.soozooq.tianwaitian.fixture;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A self-authored guest View + event handler, compiled into a SEPARATE fixture
 * APK with its own res/layout, res/string, res/drawable and res/color entries.
 * The host provides a Context and detached resource lookup; it does not install
 * or start the guest APK's Activity.
 */
public final class GuestUiV1 {
    private static final String PACKAGE = "com.soozooq.tianwaitian.fixture";
    private static final String MARKER = "TWT_GUEST_ONLY_2_4_17";

    private final Context hostContext;
    private final Resources guestResources;
    private final StringBuilder clickTrace = new StringBuilder();
    private int clicks;
    private TextView counter;
    private View panel;
    private boolean disposed;
    private boolean resourcesVerified;
    private String resourceEvidence = "(not loaded)";
    private String currentStage = "not started";

    public GuestUiV1(Context context, Resources resources, int restoredClicks) {
        if (context == null || resources == null) throw new IllegalArgumentException("context/resources");
        hostContext = context;
        guestResources = resources;
        clicks = requireValidClicks(restoredClicks);
    }

    public View createView() {
        if (disposed || panel != null) {
            throw new IllegalStateException("guest UI already created/disposed");
        }
        try {
            currentStage = "guest resource package-name lookup";
            // IDs in a second APK can share the same 0x7f namespace as the
            // host. Each lookup must resolve through the separate Resources.
            String resourcePackage = guestResources.getResourcePackageName(
                    R.string.guest_panel_title);
            currentStage = "guest marker string";
            String marker = guestResources.getString(R.string.guest_resource_marker);
            currentStage = "guest title string";
            String title = guestResources.getString(R.string.guest_panel_title);
            currentStage = "guest button string";
            String buttonText = guestResources.getString(R.string.guest_tap_button);
            currentStage = "guest XML layout-name lookup";
            String layoutName = guestResources.getResourceEntryName(R.layout.guest_panel);
            currentStage = "guest drawable load";
            Drawable graphic = guestResources.getDrawable(R.drawable.guest_mark, null);
            currentStage = "guest resource identity check";
            if (!PACKAGE.equals(resourcePackage) || !MARKER.equals(marker)
                    || !title.contains("独立 APK") || graphic == null
                    || !"guest_panel".equals(layoutName)) {
                throw new SecurityException("resource mismatch: package="
                        + resourcePackage + " marker=" + marker
                        + " layout=" + layoutName);
            }

            currentStage = "construct scoped guest View context";
            ContextThemeWrapper guestViewContext = new ContextThemeWrapper(
                    hostContext, android.R.style.Theme_Material_Light_NoActionBar) {
                @Override public Resources getResources() { return guestResources; }
                @Override public AssetManager getAssets() { return guestResources.getAssets(); }
                @Override public Object getSystemService(String name) {
                    if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                        return LayoutInflater.from(hostContext).cloneInContext(this);
                    }
                    return super.getSystemService(name);
                }
            };
            currentStage = "inflate guest_panel.xml";
            panel = LayoutInflater.from(guestViewContext)
                    .inflate(R.layout.guest_panel, null, false);
            currentStage = "find guest views";
            TextView heading = panel.findViewById(R.id.guest_heading);
            ImageView image = panel.findViewById(R.id.guest_image);
            counter = panel.findViewById(R.id.guest_counter);
            Button button = panel.findViewById(R.id.guest_action);
            currentStage = "validate inflated strings and drawable";
            if (heading == null || image == null || counter == null || button == null
                    || !title.contentEquals(heading.getText())
                    || !buttonText.contentEquals(button.getText())
                    || image.getDrawable() == null) {
                throw new IllegalStateException("guest XML did not inflate expected views");
            }
            currentStage = "wire guest button listener";
            button.setOnClickListener(v -> {
                if (disposed) return;
                clicks++;
                if (clickTrace.length() < 1000) {
                    clickTrace.append("GUEST CLICK #").append(clicks).append('\n');
                }
                refresh();
            });
            refresh();
            resourcesVerified = true;
            resourceEvidence = "resourcePackage=" + resourcePackage
                    + "；marker=" + marker
                    + "；layout=" + layoutName
                    + "；title=" + title
                    + "；drawable=" + image.getDrawable().getClass().getSimpleName();
            currentStage = "complete";
            return panel;
        } catch (RuntimeException error) {
            // Reflective invocation throws InvocationTargetException at the
            // host boundary. Preserve the meaningful resource stage here.
            throw new IllegalStateException("fixture guest UI stage=" + currentStage
                    + "; cause=" + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : "; detail=" + error.getMessage()),
                    error);
        }
    }

    public boolean resourcesVerified() { return resourcesVerified; }
    public String getResourceEvidence() { return resourceEvidence; }
    public int getClickCount() { return clicks; }

    public String snapshot() {
        return "guestViewClicks=" + clicks
                + "；disposed=" + disposed
                + "；guestResourcesVerified=" + resourcesVerified
                + "；guestViewClass=" + GuestUiV1.class.getName()
                + "；stage=" + currentStage
                + "\n" + resourceEvidence + "\n" + clickTrace;
    }

    public void dispose() {
        disposed = true;
        if (panel != null) {
            View button = panel.findViewById(R.id.guest_action);
            if (button != null) button.setOnClickListener(null);
        }
        panel = null;
        counter = null;
    }

    public static int requireValidClicks(int count) {
        if (count < 0 || count > 100000) throw new IllegalArgumentException("invalid restored click count");
        return count;
    }

    public static String counterLabel(int count) {
        return "来自独立 APK 的点击计数：" + requireValidClicks(count);
    }

    private void refresh() {
        if (counter != null) counter.setText(counterLabel(clicks));
    }
}
