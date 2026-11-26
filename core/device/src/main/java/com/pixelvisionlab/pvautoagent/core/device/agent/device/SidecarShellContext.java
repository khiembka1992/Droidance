// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device.agent.device;

import com.pixelvisionlab.pvautoagent.core.agent.device.*;

import com.pixelvisionlab.pvautoagent.core.device.agent.device.*;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Field;

/**
 * Context factory for the shell app_process server.
 * Wraps the system context through the shell package context so framework package/uid
 * attribution stays aligned for the shell uid.
 *
 * <p>System managers such as {@link android.content.ClipboardManager} capture the
 * Context they were obtained from and use its package/AttributionSource for later
 * permission and foreground checks. To let the background sidecar write and read the
 * primary clip with shell identity, {@link #getSystemService(String)} re-points the
 * returned service's {@code mContext} field to this shell context. These shell
 * constants are Android platform identity, not business rules.
 */
public final class SidecarShellContext extends ContextWrapper {
    private static final String TAG = "SidecarShellContext";
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final int SHELL_UID = 2000;
    private static final String ATTRIBUTION_TAG = "pvautoagent-sidecar";

    private SidecarShellContext(Context base) {
        super(base);
    }

    public static Context create() {
        int uid = android.os.Process.myUid();
        if (uid != SHELL_UID) {
            throw new IllegalStateException(
                    "unsupported sidecar uid=" + uid);
        }
        Context system = null;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getDeclaredMethod("systemMain").invoke(null);
            system = (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
        } catch (Throwable ignored) {
            system = null;
        }
        if (system == null) {
            throw new IllegalStateException("system context unavailable");
        }
        Context base;
        try {
            base = system.createPackageContext(SHELL_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable ignored) {
            base = system;
        }
        return new SidecarShellContext(base);
    }

    @Override
    public String getPackageName() {
        return SHELL_PACKAGE;
    }

    @Override
    public String getOpPackageName() {
        return SHELL_PACKAGE;
    }

    public String getBasePackageName() {
        return SHELL_PACKAGE;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public int getDeviceId() {
        return Context.DEVICE_ID_DEFAULT;
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        if (SHELL_PACKAGE.equals(packageName)) {
            return this;
        }
        return super.createPackageContext(packageName, flags);
    }

    // AttributionSource only exists since API 31; the sidecar runs as app_process on the real device's actual API,
    // and minSdk 28 is only the build floor, so at runtime it never reaches this method on a system <31, hence NewApi is suppressed.
    @SuppressLint("NewApi")
    @Override
    public android.content.AttributionSource getAttributionSource() {
        return new android.content.AttributionSource.Builder(SHELL_UID)
                .setPackageName(SHELL_PACKAGE)
                .setAttributionTag(ATTRIBUTION_TAG)
                .build();
    }

    /**
     * Obtain the service, then re-point its captured {@code mContext} to this shell
     * context so later framework calls attribute to the shell uid/package. When the
     * field is absent across the class hierarchy the original service is kept and a
     * generic (non-sensitive) message is logged.
     */
    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service == null) {
            return null;
        }
        Class<?> cls = service.getClass();
        while (cls != null) {
            try {
                Field contextField = cls.getDeclaredField("mContext");
                contextField.setAccessible(true);
                contextField.set(service, this);
                return service;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Throwable t) {
                Log.w(TAG, "getSystemService(" + name + ") context patch failed");
                return service;
            }
        }
        return service;
    }
}
