package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.ParcelFileDescriptor;

/** Private resource scope for the explicitly imported, exact-hash trusted demo. */
public final class ContainerV31GuestResources implements AutoCloseable {
    private final Resources resources;
    private final ResourcesLoader loader;
    private final ResourcesProvider provider;
    private boolean closed;

    private ContainerV31GuestResources(Resources resources, ResourcesLoader loader,
            ResourcesProvider provider) {
        this.resources = resources;
        this.loader = loader;
        this.provider = provider;
    }

    public static ContainerV31GuestResources open(Context context,
            ContainerV31TrustedDemo verifiedDemo) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new UnsupportedOperationException("V3.1 resources require Android API 30+");
        }
        ResourcesProvider provider;
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                verifiedDemo.getVerifiedResourceApk(), ParcelFileDescriptor.MODE_READ_ONLY)) {
            provider = ResourcesProvider.loadFromApk(descriptor);
        }
        ResourcesLoader loader = new ResourcesLoader();
        Resources isolated = null;
        try {
            loader.addProvider(provider);
            Resources host = context.getResources();
            // Host and demo APKs both use resource package ID 0x7f. Starting
            // with only framework assets prevents host resource collisions.
            @SuppressWarnings("deprecation")
            Resources candidate = new Resources(Resources.getSystem().getAssets(),
                    host.getDisplayMetrics(), host.getConfiguration());
            isolated = candidate;
            isolated.addLoaders(loader);
            if (isolated == host) throw new SecurityException("host resource scope reused");
            return new ContainerV31GuestResources(isolated, loader, provider);
        } catch (Exception | Error failure) {
            if (isolated != null) {
                try { isolated.removeLoaders(loader); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { loader.clearProviders(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { provider.close(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public Resources getResources() {
        if (closed) throw new IllegalStateException("demo resources already closed");
        return resources;
    }

    @Override public void close() {
        if (closed) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new IllegalStateException("unsupported resource cleanup API");
        }
        RuntimeException failure = null;
        try { resources.removeLoaders(loader); }
        catch (RuntimeException caught) { failure = caught; }
        // A provider still registered with a loader cannot be closed. Attempt
        // all three operations even if an earlier cleanup operation failed.
        try { loader.clearProviders(); }
        catch (RuntimeException caught) {
            if (failure == null) failure = caught;
            else failure.addSuppressed(caught);
        }
        try { provider.close(); }
        catch (RuntimeException caught) {
            if (failure == null) failure = caught;
            else failure.addSuppressed(caught);
        }
        if (failure != null) throw failure;
        closed = true;
    }
}
