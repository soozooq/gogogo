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
            // A detached Resources instance avoids changing the installed app's
            // global Resources; guest APK entries have precedence in this instance.
            // This constructor is deprecated for normal apps; V1 deliberately tests
            // the public resources-loader APIs in this bounded fixture.
            @SuppressWarnings("deprecation")
            Resources isolated = new Resources(
                    host.getAssets(), host.getDisplayMetrics(), host.getConfiguration());
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
