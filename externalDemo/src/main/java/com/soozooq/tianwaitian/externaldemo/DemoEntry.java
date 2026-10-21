package com.soozooq.tianwaitian.externaldemo;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Public reflection contract of this deliberately small external demo APK. */
public final class DemoEntry {
    public static final int CONTRACT_VERSION = 1;
    public static final String PACKAGE = "com.soozooq.tianwaitian.externaldemo";
    public static final String RESOURCE_MARKER = "TWT_EXTERNAL_DEMO_V31_RESOURCE_1";

    private final Context hostContext;
    private final Resources guestResources;
    private final DemoState state;
    private View panel;
    private Button button;
    private TextView counter;
    private boolean resourcesVerified;
    private String resourceEvidence = "NOT_CREATED";

    public DemoEntry(Context hostContext, Resources guestResources, int restoredClicks) {
        if (hostContext == null || guestResources == null) {
            throw new IllegalArgumentException("context/resources required");
        }
        this.hostContext = hostContext;
        this.guestResources = guestResources;
        state = new DemoState(restoredClicks);
    }

    public View createView() {
        if (state.isDisposed() || panel != null) {
            throw new IllegalStateException("demo view already created/disposed");
        }
        String resourcePackage = guestResources.getResourcePackageName(R.layout.demo_panel);
        String marker = guestResources.getString(R.string.demo_resource_marker);
        if (!PACKAGE.equals(resourcePackage) || !RESOURCE_MARKER.equals(marker)
                || !"demo_panel".equals(
                        guestResources.getResourceEntryName(R.layout.demo_panel))) {
            throw new SecurityException("external demo resource identity mismatch");
        }

        // Use the already verified V1 resource bridge pattern: guest-only
        // Resources plus a framework inflater without the host AppCompat factory.
        ContextThemeWrapper guestContext = new ContextThemeWrapper(
                hostContext, android.R.style.Theme_Material_Light_NoActionBar) {
            @Override public Resources getResources() { return guestResources; }
            @Override public AssetManager getAssets() { return guestResources.getAssets(); }
            @Override public Object getSystemService(String name) {
                if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                    return LayoutInflater.from(hostContext.getApplicationContext())
                            .cloneInContext(this);
                }
                return super.getSystemService(name);
            }
        };
        panel = LayoutInflater.from(guestContext).inflate(R.layout.demo_panel, null, false);
        TextView title = panel.findViewById(R.id.demo_title);
        counter = panel.findViewById(R.id.demo_counter);
        button = panel.findViewById(R.id.demo_increment);
        if (title == null || counter == null || button == null
                || !guestResources.getString(R.string.demo_title).contentEquals(title.getText())
                || !guestResources.getString(R.string.demo_increment).contentEquals(button.getText())
                || !"android.widget.Button".equals(button.getClass().getName())) {
            throw new IllegalStateException("external demo XML views/resource text mismatch");
        }
        button.setOnClickListener(view -> {
            if (state.isDisposed()) return;
            state.click();
            refreshCounter();
        });
        refreshCounter();
        resourcesVerified = true;
        resourceEvidence = "resourcePackage=" + resourcePackage + "; marker=" + marker
                + "; layout=demo_panel; buttonClass=" + button.getClass().getName()
                + "; backgroundColor=" + Integer.toHexString(
                        guestResources.getColor(R.color.demo_background, null));
        return panel;
    }

    public String onHostLifecycle(String event) { return state.onLifecycle(event); }
    public int getClickCount() { return state.getClickCount(); }
    public boolean resourcesVerified() { return resourcesVerified; }
    public String getResourceEvidence() { return resourceEvidence; }
    public boolean isDisposed() { return state.isDisposed(); }

    public String snapshot() {
        return "entry=" + DemoEntry.class.getName() + "; contract=" + CONTRACT_VERSION
                + "; resourcesVerified=" + resourcesVerified + "; viewReleased=" + (panel == null)
                + "\n" + resourceEvidence + "\n" + state.snapshot();
    }

    public void dispose() {
        if (button != null) button.setOnClickListener(null);
        button = null;
        counter = null;
        panel = null;
        state.dispose();
    }

    private void refreshCounter() {
        counter.setText(guestResources.getString(R.string.demo_counter, state.getClickCount()));
    }
}
