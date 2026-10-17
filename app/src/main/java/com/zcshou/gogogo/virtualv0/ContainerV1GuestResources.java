package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Controlled API 30+ experiment: attach resources from only our build-bundled,
 * identity-checked fixture APK to a NEW Resources object, never to host global
 * Resources. Host Context / UID and Android component registration unchanged.
 */
public final class ContainerV1GuestResources implements AutoCloseable {
    private final Resources resources;
    private final ResourcesLoader loader;
    private final ResourcesProvider provider;
    private boolean closed;

    private ContainerV1GuestResources(Resources resources, ResourcesLoader loader,
            ResourcesProvider provider) {
        this.resources = resources;
        this.loader = loader;
        this.provider = provider;
    }

    public static ContainerV1GuestResources open(Context context,
            ContainerV0TrustedGuest verifiedGuest) throws Exception {
        if (Build.VERSION.SDK_INT < 30) {
            throw new UnsupportedOperationException("V1 APK resources require Android API 30+");
        }
        File apk = verifiedGuest.getVerifiedResourceApk();
        ResourcesProvider provider;
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                apk, ParcelFileDescriptor.MODE_READ_ONLY)) {
            provider = ResourcesProvider.loadFromApk(fd);
        }
        ResourcesLoader loader = new ResourcesLoader();
        try {
            loader.addProvider(provider);
            Resources host = context.getResources();
            // Both standalone application APKs normally compile resource IDs
            // under 0x7f. Never put the fixture on top of the host APK resource
            // table: names can collide even if we only modify a local Resources.
            // Begin with framework-only assets (0x01) and attach the guest
            // provider (0x7f) to a separate Resources instance.
            // This deprecated public constructor is restricted to V1 research.
            @SuppressWarnings("deprecation")
            Resources isolated = new Resources(Resources.getSystem().getAssets(),
                    host.getDisplayMetrics(), host.getConfiguration());
            isolated.addLoaders(loader);
            if (isolated == host) {
                throw new SecurityException("cannot isolate guest resources from host");
            }
            return new ContainerV1GuestResources(isolated, loader, provider);
        } catch (Exception | Error failure) {
            try {
                loader.clearProviders();
                provider.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public Resources getResources() {
        if (closed) throw new IllegalStateException("resources already closed");
        return resources;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        // Only this private resource instance was modified. Close after guest View
        // was detached / disposed; do not mutate or close any host Resources.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Removing a loader from Resources does NOT release its provider
            // reference. ResourcesProvider.close() otherwise throws:
            // IllegalStateException: used by 1 ResourcesLoader instances.
            resources.removeLoaders(loader);
            loader.clearProviders();
            provider.close();
        }
    }
}
