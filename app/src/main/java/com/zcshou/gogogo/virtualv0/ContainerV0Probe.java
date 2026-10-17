package com.zcshou.gogogo.virtualv0;

import android.content.Context;
import android.os.Process;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import dalvik.system.DexClassLoader;

/** V0: two separate class definitions and logically separate files, NOT separate UIDs. */
public final class ContainerV0Probe {
    private static final String CLASS_NAME = "com.zcshou.gogogo.virtualv0.GuestPayload";

    private ContainerV0Probe() {}

    private static final class GuestLoader extends DexClassLoader {
        GuestLoader(String apk, String optimized, ClassLoader parent) {
            super(apk, optimized, null, parent);
        }

        @Override protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!CLASS_NAME.equals(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> clazz = findLoadedClass(name);
                if (clazz == null) clazz = findClass(name); // Child-first for this one test class.
                if (resolve) resolveClass(clazz);
                return clazz;
            }
        }
    }

    public static String run(Context context) throws Exception {
        Context app = context.getApplicationContext();
        File root = new File(app.getFilesDir(), "virtual_container_v0");
        File dirA = new File(root, "A");
        File dirB = new File(root, "B");
        File codeCache = new File(app.getCodeCacheDir(), "virtual_container_v0");
        for (File dir : new File[] {dirA, dirB, codeCache}) {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
        }
        String ownApk = app.getApplicationInfo().sourceDir;
        GuestLoader loaderA = new GuestLoader(ownApk, codeCache.getAbsolutePath(),
                app.getClassLoader());
        GuestLoader loaderB = new GuestLoader(ownApk, codeCache.getAbsolutePath(),
                app.getClassLoader());
        Class<?> classA = Class.forName(CLASS_NAME, true, loaderA);
        Class<?> classB = Class.forName(CLASS_NAME, true, loaderB);
        if (classA == classB || classA.getClassLoader() != loaderA
                || classB.getClassLoader() != loaderB) {
            throw new AssertionError("ClassLoader isolation failed");
        }
        String nonce = Long.toHexString(System.nanoTime());
        invoke(classA, "A", dirA, "A_" + nonce + "_first");
        invoke(classB, "B", dirB, "B_" + nonce + "_first");
        String lastA = "A_" + nonce + "_last";
        String lastB = "B_" + nonce + "_first";
        invoke(classA, "A", dirA, lastA);
        String textA = new String(Files.readAllBytes(new File(dirA, "slot.txt").toPath()),
                StandardCharsets.UTF_8);
        String textB = new String(Files.readAllBytes(new File(dirB, "slot.txt").toPath()),
                StandardCharsets.UTF_8);
        if (!textA.equals("slot=A\nmarker=" + lastA + "\n")
                || !textB.equals("slot=B\nmarker=" + lastB + "\n")) {
            throw new AssertionError("A/B state not independent");
        }
        return "PASS：两个同名类的 Class 对象不同\n"
                + "A loader: " + System.identityHashCode(loaderA) + "\n"
                + "B loader: " + System.identityHashCode(loaderB) + "\n"
                + "PASS：A → B → A 写入后，B 数据不变\n"
                + "PASS：两个应用私有测试目录互不串线\n"
                + "当前 PID：" + Process.myPid() + "\n"
                + "限制：仍共享同一 UID、宿主进程和权限；"
                + "尚非可安装/启动第三方 APK 的完整虚拟容器。";
    }

    private static void invoke(Class<?> clazz, String slot, File dir, String marker)
            throws Exception {
        Method method = clazz.getMethod("run", String.class, String.class, String.class);
        try {
            method.invoke(null, slot, dir.getCanonicalPath(), marker);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw error;
        }
    }
}
