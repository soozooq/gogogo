package com.soozooq.tianwaitian.externaldemo;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private Button detailsButton;
    private Button backButton;
    private TextView counter;
    private TextView detailsText;
    private EditText noteInput;
    private TextWatcher noteWatcher;
    private View homePage;
    private View detailsPage;
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
        noteInput = panel.findViewById(R.id.demo_note);
        detailsButton = panel.findViewById(R.id.demo_open_details);
        backButton = panel.findViewById(R.id.demo_back);
        detailsText = panel.findViewById(R.id.demo_details_text);
        homePage = panel.findViewById(R.id.demo_home);
        detailsPage = panel.findViewById(R.id.demo_details);
        if (title == null || counter == null || button == null || noteInput == null
                || detailsButton == null || backButton == null || detailsText == null
                || homePage == null || detailsPage == null
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
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DemoState.MAX_NOTE_LENGTH)});
        noteInput.setText(state.getNote());
        noteWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!state.isDisposed()) state.setNote(text.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        };
        noteInput.addTextChangedListener(noteWatcher);
        detailsButton.setOnClickListener(view -> {
            if (state.isDisposed()) return;
            state.openDetails();
            refreshPage();
        });
        backButton.setOnClickListener(view -> handleBack());
        refreshCounter();
        refreshPage();
        resourcesVerified = true;
        resourceEvidence = "resourcePackage=" + resourcePackage + "; marker=" + marker
                + "; layout=demo_panel; buttonClass=" + button.getClass().getName()
                + "; backgroundColor=" + Integer.toHexString(
                        guestResources.getColor(R.color.demo_background, null));
        return panel;
    }

    public String onHostLifecycle(String event) { return state.onLifecycle(event); }
    public int getClickCount() { return state.getClickCount(); }
    public int getInteractionCount() { return state.getInteractionCount(); }
    public boolean resourcesVerified() { return resourcesVerified; }
    public String getResourceEvidence() { return resourceEvidence; }
    public boolean isDisposed() { return state.isDisposed(); }

    /** Only primitive values cross the Host reflection contract. */
    public Bundle saveState() {
        DemoState.SavedState saved = state.save();
        Bundle result = new Bundle();
        result.putInt("schema", saved.schema);
        result.putInt("clicks", saved.clicks);
        result.putString("note", saved.note);
        result.putString("page", saved.page);
        return result;
    }

    public void restoreState(Bundle saved) {
        if (panel != null || state.isDisposed()) {
            throw new IllegalStateException("restore must precede View creation");
        }
        if (saved == null) return;
        // Reject partially populated, future or incorrectly typed state rather
        // than interpreting malformed data as an empty successful restore.
        if (saved.size() != 4 || !(saved.get("schema") instanceof Integer)
                || !(saved.get("clicks") instanceof Integer)
                || !(saved.get("note") instanceof String)
                || !(saved.get("page") instanceof String)) {
            throw new IllegalArgumentException("invalid demo state fields");
        }
        state.restore(new DemoState.SavedState((Integer) saved.get("schema"),
                (Integer) saved.get("clicks"), (String) saved.get("note"),
                (String) saved.get("page")));
    }

    /** Consume the demo's own content-page navigation before Host exit. */
    public boolean handleBack() {
        boolean consumed = state.handleBack();
        if (consumed && panel != null) refreshPage();
        return consumed;
    }

    public String snapshot() {
        return "entry=" + DemoEntry.class.getName() + "; contract=" + CONTRACT_VERSION
                + "; resourcesVerified=" + resourcesVerified + "; viewReleased=" + (panel == null)
                + "\n" + resourceEvidence + "\n" + state.snapshot();
    }

    public void dispose() {
        if (button != null) button.setOnClickListener(null);
        if (detailsButton != null) detailsButton.setOnClickListener(null);
        if (backButton != null) backButton.setOnClickListener(null);
        if (noteInput != null && noteWatcher != null) noteInput.removeTextChangedListener(noteWatcher);
        button = null;
        detailsButton = null;
        backButton = null;
        noteInput = null;
        noteWatcher = null;
        counter = null;
        detailsText = null;
        homePage = null;
        detailsPage = null;
        panel = null;
        state.dispose();
    }

    private void refreshCounter() {
        counter.setText(guestResources.getString(R.string.demo_counter, state.getClickCount()));
    }

    private void refreshPage() {
        boolean details = DemoState.PAGE_DETAILS.equals(state.getPage());
        homePage.setVisibility(details ? View.GONE : View.VISIBLE);
        detailsPage.setVisibility(details ? View.VISIBLE : View.GONE);
        detailsText.setText(guestResources.getString(R.string.demo_details_text,
                state.getClickCount(), state.getNote().isEmpty()
                        ? guestResources.getString(R.string.demo_note_empty) : state.getNote()));
    }
}
