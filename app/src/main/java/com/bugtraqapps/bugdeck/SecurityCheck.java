package com.bugtraqapps.bugdeck;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local device security posture check.
 *
 * <p>Runs without root and reports what it can see from app-level permissions.
 * Rooted or managed devices expose more; this is a baseline, not a forensic
 * sweep. For remote targets, see {@link UnifiedClient#remoteDiagnose(String)}.
 */
public final class SecurityCheck {

    private SecurityCheck() {}

    // ------------------------------------------------------------------
    // Result model
    // ------------------------------------------------------------------

    public static class Finding {
        public enum Level { INFO, LOW, MEDIUM, HIGH, CRITICAL }
        public final Level level;
        public final String category;
        public final String detail;
        public Finding(Level level, String category, String detail) {
            this.level = level; this.category = category; this.detail = detail;
        }
    }

    public static List<Finding> run(Context ctx) {
        List<Finding> out = new ArrayList<>();
        out.add(new Finding(Finding.Level.INFO, "device", "model=" + Build.MODEL
                + " sdk=" + Build.VERSION.SDK_INT
                + " fingerprint=" + Build.FINGERPRINT));

        checkRootIndicators(out);
        checkSystemProperties(out);
        checkDebuggableApps(ctx, out);
        checkRiskyPermissions(ctx, out);
        checkSuspiciousProcesses(out);
        checkNetworkListeners(out);
        checkAccessibility(ctx, out);
        checkDeviceAdmin(ctx, out);
        return out;
    }

    // ------------------------------------------------------------------
    // Checks
    // ------------------------------------------------------------------

    private static void checkRootIndicators(List<Finding> out) {
        String[] suPaths = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/bin/failsafe/su", "/system/usr/we-need-root/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
            "/system/app/Kinguser.apk", "/system/app/KingRoot.apk",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/priv-app/Superuser.apk"
        };
        for (String p : suPaths) {
            if (new File(p).exists()) {
                out.add(new Finding(Finding.Level.CRITICAL, "root",
                        "found: " + p));
            }
        }
        // PATH-based heuristic
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String seg : pathEnv.split(":")) {
                if (new File(seg, "su").exists()) {
                    out.add(new Finding(Finding.Level.HIGH, "root",
                            "su in PATH: " + seg + "/su"));
                    break;
                }
            }
        }
        if (out.stream().noneMatch(f -> f.category.equals("root"))) {
            out.add(new Finding(Finding.Level.INFO, "root",
                    "no common su indicators found (app-level check only)"));
        }
    }

    private static void checkSystemProperties(List<Finding> out) {
        // These are system properties readable by any app
        String[] props = {
            "ro.secure", "ro.debuggable", "ro.kernel.qemu",
            "ro.debuggable", "ro.build.type"
        };
        for (String prop : props) {
            try {
                Class<?> sp = Class.forName("android.os.SystemProperties");
                java.lang.reflect.Method m = sp.getMethod("get", String.class);
                String val = (String) m.invoke(null, prop);
                if (val != null && !val.isEmpty()) {
                    if ("1".equals(val) && (prop.equals("ro.secure")
                            || prop.equals("ro.debuggable"))) {
                        out.add(new Finding(Finding.Level.HIGH, "system",
                                prop + "=" + val));
                    } else if ("1".equals(val) && prop.equals("ro.kernel.qemu")) {
                        out.add(new Finding(Finding.Level.MEDIUM, "system",
                                prop + "=" + val + " (emulator)"));
                    } else {
                        out.add(new Finding(Finding.Level.INFO, "system",
                                prop + "=" + val));
                    }
                }
            } catch (Exception e) {
                // SystemProperties not accessible — fine
            }
        }
    }

    private static void checkDebuggableApps(Context ctx, List<Finding> out) {
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        int debuggable = 0;
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                debuggable++;
                if (debuggable <= 5) {
                    out.add(new Finding(Finding.Level.MEDIUM, "debuggable",
                            ai.packageName));
                }
            }
        }
        if (debuggable > 5) {
            out.add(new Finding(Finding.Level.MEDIUM, "debuggable",
                    "… and " + (debuggable - 5) + " more"));
        } else if (debuggable == 0) {
            out.add(new Finding(Finding.Level.INFO, "debuggable",
                    "none found"));
        }
    }

    private static void checkRiskyPermissions(Context ctx, List<Finding> out) {
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        String[] risky = {
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.ACCESSIBILITY_SERVICE"
        };
        Set<String> flagged = new LinkedHashSet<>();
        for (ApplicationInfo ai : apps) {
            if (ai.uid == Process.myUid()) continue; // skip self
            PackageManager permMgr = pm;
            StringBuilder sb = new StringBuilder();
            for (String r : risky) {
                if (ContextCompat.checkSelfPermission(ctx, r)
                        == PackageManager.PERMISSION_GRANTED) {
                    sb.append(r.substring(r.lastIndexOf('.') + 1)).append(" ");
                }
            }
            if (sb.length() > 0) {
                String entry = ai.packageName + ": " + sb.toString().trim();
                flagged.add(entry);
            }
        }
        if (flagged.isEmpty()) {
            out.add(new Finding(Finding.Level.INFO, "permissions",
                    "no risky permissions detected on third-party apps"));
        } else {
            int shown = 0;
            for (String s : flagged) {
                if (++shown > 6) break;
                out.add(new Finding(Finding.Level.MEDIUM, "permissions", s));
            }
            if (flagged.size() > 6) {
                out.add(new Finding(Finding.Level.MEDIUM, "permissions",
                        "… and " + (flagged.size() - 6) + " more"));
            }
        }
    }

    private static void checkSuspiciousProcesses(List<Finding> out) {
        List<String> suspicious = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec("ps -A");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern frida = Pattern.compile("frida", Pattern.CASE_INSENSITIVE);
            Pattern xhunter = Pattern.compile("xhunter", Pattern.CASE_INSENSITIVE);
            Pattern su = Pattern.compile("\\bsu\\b", Pattern.CASE_INSENSITIVE);
            while ((line = r.readLine()) != null) {
                Matcher m;
                if ((m = frida.matcher(line)).find()) {
                    suspicious.add(line.trim());
                } else if ((m = xhunter.matcher(line)).find()) {
                    suspicious.add(line.trim());
                } else if ((m = su.matcher(line)).find()) {
                    suspicious.add(line.trim());
                }
            }
            r.close();
            p.waitFor();
        } catch (Exception e) {
            out.add(new Finding(Finding.Level.LOW, "processes",
                    "could not enumerate processes: " + e.getMessage()));
        }
        if (suspicious.isEmpty()) {
            out.add(new Finding(Finding.Level.INFO, "processes",
                    "no frida/xhunter/su processes found"));
        } else {
            int shown = 0;
            for (String s : suspicious) {
                if (++shown > 5) break;
                Level lvl = Finding.Level.MEDIUM;
                if (s.contains("frida")) lvl = Finding.Level.HIGH;
                else if (s.contains("su")) lvl = Finding.Level.CRITICAL;
                out.add(new Finding(lvl, "processes", s));
            }
            if (suspicious.size() > 5) {
                out.add(new Finding(Finding.Level.MEDIUM, "processes",
                        "… and " + (suspicious.size() - 5) + " more"));
            }
        }
    }

    private static void checkNetworkListeners(List<Finding> out) {
        // /proc/net/tcp is world-readable; parse for LISTEN sockets
        List<String> listeners = new ArrayList<>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new File("/proc/net/tcp").openStream()));
            String header = r.readLine(); // skip header
            String line;
            Pattern p = Pattern.compile(
                    "^\\s*\\d+:\\s+[0-9A-F]+:([0-9A-F]+)\\s+[0-9A-F]+:0000\\s+([0-9A-F]+)",
                    Pattern.CASE_INSENSITIVE);
            while ((line = r.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    int localPort = Integer.parseInt(m.group(1), 16);
                    String state = m.group(2);
                    if ("0A".equals(state)) { // LISTEN
                        listeners.add(String.valueOf(localPort));
                    }
                }
            }
            r.close();
        } catch (Exception e) {
            out.add(new Finding(Finding.Level.LOW, "network",
                    "could not read /proc/net/tcp"));
        }
        // Flag anything above 1024 that isn't a known app port
        Set<Integer> suspicious = new LinkedHashSet<>();
        for (String s : listeners) {
            int port = Integer.parseInt(s);
            if (port > 1024 && port != 8080 && port != 7080 && port != 9090
                    && port != 27042) {
                suspicious.add(port);
            }
        }
        if (listeners.isEmpty()) {
            out.add(new Finding(Finding.Level.INFO, "network",
                    "no TCP listeners detected"));
        } else {
            out.add(new Finding(Finding.Level.INFO, "network",
                    "listening on ports: " + String.join(",", listeners)));
            if (!suspicious.isEmpty()) {
                for (int p : suspicious) {
                    out.add(new Finding(Finding.Level.MEDIUM, "network",
                            "unexpected listener on port " + p));
                }
            }
        }
    }

    private static void checkAccessibility(Context ctx, List<Finding> out) {
        try {
            String enabled = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    "accessibility_enabled");
            if ("1".equals(enabled)) {
                out.add(new Finding(Finding.Level.HIGH, "accessibility",
                        "accessibility service is enabled (potential keylogger / overlay risk)"));
            } else {
                out.add(new Finding(Finding.Level.INFO, "accessibility",
                        "disabled"));
            }
        } catch (Exception e) {
            out.add(new Finding(Finding.Level.LOW, "accessibility",
                    "could not read accessibility state"));
        }
    }

    private static void checkDeviceAdmin(Context ctx, List<Finding> out) {
        List<String> admins = new ArrayList<>();
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            // DeviceAdminReceiver list is available via PackageManager
            PackageManager pm = ctx.getPackageManager();
            java.util.Set<String> enabled = pm.getStringEnabledDeviceAdmins(ctx);
            if (enabled != null) {
                for (String comp : enabled) {
                    admins.add(comp);
                }
            }
        } catch (Exception e) {
            // not available on all API levels
        }
        if (admins.isEmpty()) {
            out.add(new Finding(Finding.Level.INFO, "device_admin",
                    "no device admin receivers enabled"));
        } else {
            for (String a : admins) {
                out.add(new Finding(Finding.Level.MEDIUM, "device_admin", a));
            }
        }
    }
}
