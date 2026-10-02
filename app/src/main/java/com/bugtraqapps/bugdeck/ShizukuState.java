package com.bugtraqapps.bugdeck;

import android.content.Context;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Shizuku availability, behind reflection so the app never hard-depends on it.
 *
 * <p>Why reflection rather than calling {@link Shizuku} directly: Shizuku is
 * OPTIONAL here. BugDeck must install, launch and work fully without it, and a
 * direct call would throw on a device where the library's classes fail to
 * initialise. Everything here therefore returns a plain "no" instead of
 * throwing, and every caller has a real fallback.
 *
 * <p>The only thing Shizuku buys this app is privileged identity for the Device
 * tab (logcat, installed packages). It does not make the scanner better: the
 * core talks HTTP over {@code java.net}, which is unaffected by it.
 */
final class ShizukuState {

    private ShizukuState() {}

    /** What the user can actually do right now. */
    enum Status {
        /** Binder alive and permission granted. */
        READY,
        /** Shizuku is installed and running, but permission was not granted yet. */
        NEEDS_PERMISSION,
        /** Shizuku present but the server is not running. */
        NOT_RUNNING,
        /** Shizuku is not installed, or the API is too old. */
        UNAVAILABLE
    }

    /**
     * Resolve the current status.
     *
     * <p>Every call into the Shizuku library is wrapped: a device with a broken
     * or partial install must degrade to {@link Status#UNAVAILABLE}, never take
     * the app down with it.
     */
    static Status status() {
        try {
            if (!isPresent()) return Status.UNAVAILABLE;
            if (!binderAlive()) return Status.NOT_RUNNING;
            if (checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return Status.READY;
            }
            return Status.NEEDS_PERMISSION;
        } catch (Throwable t) {
            // Never propagate: this is an optional feature.
            return Status.UNAVAILABLE;
        }
    }

    static boolean isReady() {
        return status() == Status.READY;
    }

    /** Ask for the Shizuku permission. Safe to call when not installed. */
    static void requestPermission() {
        try {
            Class<?> c = Class.forName("rikka.shizuku.Shizuku");
            Method m = c.getMethod("requestPermission", int.class);
            m.invoke(null, 0);
        } catch (Throwable ignored) {
            // Nothing sensible to do; the UI will still show as unavailable.
        }
    }

    /** True when the library class itself loaded, i.e. the dependency is present. */
    private static boolean isPresent() {
        try {
            Class.forName("rikka.shizuku.Shizuku");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean binderAlive() {
        try {
            Class<?> c = Class.forName("rikka.shizuku.Shizuku");
            Method m = c.getMethod("pingBinder");
            Object alive = m.invoke(null);
            return Boolean.TRUE.equals(alive);
        } catch (Throwable t) {
            return false;
        }
    }

    private static int checkSelfPermission() {
        try {
            Class<?> c = Class.forName("rikka.shizuku.Shizuku");
            Method m = c.getMethod("checkSelfPermission");
            Object r = m.invoke(null);
            return r instanceof Integer ? (Integer) r : PackageManager.PERMISSION_DENIED;
        } catch (Throwable t) {
            return PackageManager.PERMISSION_DENIED;
        }
    }

    /**
     * Run a command with shell identity via Shizuku, returning its output.
     *
     * <p>Used by the Device tab for logcat and package enumeration. Returns
     * null when Shizuku is unavailable so callers can show the fallback message
     * instead of an empty screen.
     */
    static String[] newProcess(String[] cmd) {
        if (!isReady()) return null;
        try {
            Class<?> c = Class.forName("rikka.shizuku.Shizuku");
            Method m = c.getMethod("newProcess", String[].class, String[].class,
                    String.class, android.os.RemoteCallback.class);
            Object process = m.invoke(null, cmd, new String[0], null, null);
            if (process == null) return null;
            return readAll(process);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Drain a Shizuku remote process's output streams. */
    private static String[] readAll(Object process) {
        java.io.InputStream out = invokeStream(process, "getOutputStream");
        java.io.InputStream err = invokeStream(process, "getErrorStream");
        java.io.InputStream[] all = { out, err };
        String[] result = new String[2];
        for (int i = 0; i < all.length; i++) {
            result[i] = drain(all[i]);
        }
        return result;
    }

    private static java.io.InputStream invokeStream(Object process, String getter) {
        try {
            Method m = process.getClass().getMethod(getter);
            Object s = m.invoke(process);
            return s instanceof java.io.InputStream ? (java.io.InputStream) s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Read a stream to a bounded size.
     *
     * <p>Bounded on purpose: logcat is unbounded output, and reading it whole
     * would be an OOM waiting to happen on a device that has been up for weeks.
     */
    private static String drain(java.io.InputStream in) {
        if (in == null) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
                total += n;
                if (total > 512 * 1024) break;   // half a megabyte is plenty
            }
        } catch (Throwable ignored) {
            // return whatever was read before the stream misbehaved
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
        return new String(bos.toByteArray());
    }

    /** Human-readable explanation for the Device tab. */
    static String explain(Context c, Status s) {
        switch (s) {
            case READY:
                return c.getString(R.string.shizuku_ready);
            case NEEDS_PERMISSION:
                return c.getString(R.string.shizuku_needs_perm);
            case NOT_RUNNING:
                return c.getString(R.string.shizuku_not_running);
            default:
                return c.getString(R.string.shizuku_missing);
        }
    }
}
