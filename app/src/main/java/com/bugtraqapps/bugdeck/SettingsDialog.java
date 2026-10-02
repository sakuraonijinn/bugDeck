package com.bugtraqapps.bugdeck;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.bugtraqapps.bugdeck.core.Fetcher;
import com.bugtraqapps.bugdeck.dork.DorkProviders;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Credential storage for the credentialed dork providers.
 *
 * <p>Brave and Google keys are secrets, so they are kept in private
 * SharedPreferences rather than in a world-readable file or in the dork
 * provider's URL. The keyless engines (bing/ddg/mojeek/startpage/searxng)
 * need nothing here, which is why the tab works with a fresh install.
 */
public final class SettingsDialog {

    private static final String PREFS = "bugdeck";
    private static final String K_BRAVE = "brave_api_key";
    private static final String K_GOOGLE = "google_api_key";
    private static final String K_CX = "google_cse_id";
    private static final String K_UA = "user_agent";
    private static final String K_TIMEOUT = "timeout_ms";

    private SettingsDialog() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String braveKey(Context c)  { return prefs(c).getString(K_BRAVE, ""); }
    public static String googleKey(Context c) { return prefs(c).getString(K_GOOGLE, ""); }
    public static String cseId(Context c)     { return prefs(c).getString(K_CX, ""); }

    public static String userAgent(Context c) {
        String ua = prefs(c).getString(K_UA, "");
        return (ua == null || ua.trim().isEmpty()) ? Fetcher.DEFAULT_UA : ua.trim();
    }

    public static int timeoutMs(Context c) {
        int t = prefs(c).getInt(K_TIMEOUT, 8000);
        return (t < 1000 || t > 120000) ? 8000 : t;
    }

    /** Push the saved cx into the core, which reads it from a static holder. */
    public static void applyToCore(Context c) {
        DorkProviders.CseIdHolder.cx = cseId(c);
    }

    public static void show(final MainActivity activity) {
        final SharedPreferences p = prefs(activity);
        final View v = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null);

        final TextInputEditText brave  = v.findViewById(R.id.set_brave);
        final TextInputEditText google = v.findViewById(R.id.set_google);
        final TextInputEditText cx     = v.findViewById(R.id.set_cx);
        final TextInputEditText ua     = v.findViewById(R.id.set_ua);

        brave.setText(p.getString(K_BRAVE, ""));
        google.setText(p.getString(K_GOOGLE, ""));
        cx.setText(p.getString(K_CX, ""));
        ua.setText(userAgent(activity));

        new AlertDialog.Builder(activity)
            .setTitle(R.string.settings)
            .setView(v)
            .setPositiveButton(android.R.string.ok, (d, which) -> {
                p.edit()
                 .putString(K_BRAVE, text(brave))
                 .putString(K_GOOGLE, text(google))
                 .putString(K_CX, text(cx))
                 .putString(K_UA, text(ua))
                 .apply();
                applyToCore(activity);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static String text(TextInputEditText f) {
        CharSequence c = f.getText();
        return c == null ? "" : c.toString().trim();
    }
}
