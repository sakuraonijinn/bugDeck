#!/usr/bin/env python3
"""Generate compile-only stubs for the Android/AndroidX surface BugDeck uses.

Purpose: allow `javac` to typecheck the GUI sources on a device with no Android
SDK. These are never packaged into the APK.

Signatures are copied from the real framework so that a wrong method name,
wrong argument type, wrong visibility, or a reference to a removed resource
fails here -- the same class of bug the GUI is being written to avoid.
"""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "stub")

def w(path, body):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    pkg = path.replace("/", ".")[:path.rfind("/")].replace("/", ".")
    text = body + "\n" if "package " in body else "package %s;\n%s\n" % (pkg, body)
    with open(full, "w") as f:
        f.write(text)

# ---------------------------------------------------------------- android.content
w("android/content/Context.java", """
import android.content.res.Resources;
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public SharedPreferences getSharedPreferences(String n, int m) { throw new UnsupportedOperationException("stub"); }
    public android.content.pm.PackageManager getPackageManager() { throw new UnsupportedOperationException("stub"); }
    public android.content.ContentResolver getContentResolver() { throw new UnsupportedOperationException("stub"); }
    public Object getSystemService(String n) { throw new UnsupportedOperationException("stub"); }
    public AssetManager getAssets() { throw new UnsupportedOperationException("stub"); }
    public Resources getResources() { throw new UnsupportedOperationException("stub"); }
    public String getString(int id) { throw new UnsupportedOperationException("stub"); }
    public String getString(int id, Object... formatArgs) { throw new UnsupportedOperationException("stub"); }
    public CharSequence getText(int id) { throw new UnsupportedOperationException("stub"); }
    public void startActivity(android.content.Intent i) {}
    public static class AssetManager {
        public java.io.InputStream open(String name) throws java.io.IOException { throw new UnsupportedOperationException("stub"); }
    }
}
""")
w("android/content/SharedPreferences.java", """
public interface SharedPreferences {
    interface Editor {
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putBoolean(String key, boolean value);
        void apply();
        boolean commit();
    }
    Editor edit();
    String getString(String key, String defValue);
    int getInt(String key, int defValue);
    boolean getBoolean(String key, boolean defValue);
}
""")
w("android/content/SharedPreferences_Impl.java", "package android.content;\n")
w("android/content/Intent.java", "public class Intent {}\n")
w("android/content/ClipboardManager.java", "public class ClipboardManager { public void setPrimaryClip(ClipData clip) {} }\n")
w("android/content/ClipData.java", "public class ClipData { public static ClipData newPlainText(CharSequence label, CharSequence text) { return new ClipData(); } }\n")
w("android/content/DialogInterface.java", """
public interface DialogInterface {
    int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2;
    interface OnClickListener { void onClick(DialogInterface d, int which); }
    interface OnDismissListener { void onDismiss(DialogInterface d); }
}
""")
w("android/content/res/Resources.java", """
public class Resources {
    public int getColor(int id, Theme theme) { return 0; }
    public String getString(int id) { return ""; }
}
""")
w("android/content/res/Theme.java", "public class Theme {}\n")
w("android/content/res/AssetManager.java", "public class AssetManager {}\n")

# ---------------------------------------------------------------- android.R
w("android/R.java", """
public final class R {
    public static final class layout {
        public static final int simple_spinner_item          = 17301569;
        public static final int simple_spinner_dropdown_item = 17301570;
    }
    public static final class id { }
    public static final class string {
        public static final int ok = 17039370;
        public static final int cancel = 17039372;
    }
    public static final class color { public static final int darker_gray = 0; }
}
""")

# ---------------------------------------------------------------- android.os
w("android/os/Bundle.java", "public class Bundle {}\n")
w("android/os/Looper.java", "public class Looper { public static Looper getMainLooper(){return null;} }\n")
w("android/os/Handler.java", """
import android.view.View;
public class Handler {
    public Handler(Looper looper) {}
    public boolean post(Runnable r) { return true; }
    public boolean postDelayed(Runnable r, long delayMillis) { return true; }
    public void removeCallbacksAndMessages(Object token) {}
}
""")
w("android/os/Parcelable.java", "public interface Parcelable {}\n")

# ---------------------------------------------------------------- android.view
w("android/view/View.java", """
import android.content.Context;
import android.content.res.Resources;
import android.view.ViewGroup.LayoutParams;
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    public View(Context context) {}
    public void setVisibility(int visibility) {}
    public int getVisibility() { return VISIBLE; }
    public void setEnabled(boolean enabled) {}
    public boolean isEnabled() { return true; }
    public void setOnClickListener(OnClickListener l) {}
    public void setOnLongClickListener(OnLongClickListener l) {}
    public Context getContext() { return null; }
    public Resources getResources() { return null; }
    public <T extends View> T findViewById(int id) { return null; }
    public void setEnabledState(boolean b) {}
    public void setPadding(int l, int t, int r, int b) {}
    public void setGravity(int gravity) {}
    public void setBackgroundColor(int color) {}
    public void setLayoutParams(ViewGroup.LayoutParams params) {}
    public ViewGroup.LayoutParams getLayoutParams() { return null; }
    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
}
""")
w("android/view/ViewGroup.java", """
import android.content.Context;
public class ViewGroup extends View {
    public ViewGroup(Context context) { super(context); }
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2;
        public int width, height;
        public float weight;
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public LayoutParams(int w, int h) { width = w; height = h; }
        public LayoutParams(int w, int h, float weight) {
            width = w; height = h; this.weight = weight;
        }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public MarginLayoutParams(int w, int h) { super(w, h); }
    }
    public void addView(View child) {}
    public void addView(View child, LayoutParams params) {}
    public void removeAllViews() {}
}
""")
w("android/view/LayoutInflater.java", """
import android.content.Context;
import android.view.ViewGroup;
public class LayoutInflater {
    public static LayoutInflater from(Context context) { return new LayoutInflater(); }
    public View inflate(int resource, ViewGroup root) { return null; }
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) { return null; }
}
""")
w("android/view/Menu.java", "public interface Menu {}\n")
w("android/view/MenuItem.java", "public interface MenuItem { int getItemId(); }\n")
w("android/view/MenuInflater.java", "public class MenuInflater { public void inflate(int menuRes, Menu menu) {} }\n")
w("android/view/Gravity.java", """
public class Gravity {
    public static final int TOP = 48;
    public static final int BOTTOM = 80;
    public static final int LEFT = 3;
    public static final int RIGHT = 5;
    public static final int START = 0x00800003;
    public static final int END = 0x00800005;
    public static final int CENTER = 17;
    public static final int CENTER_HORIZONTAL = 1;
    public static final int CENTER_VERTICAL = 16;
}
""")

# ---------------------------------------------------------------- android.widget
w("android/widget/TextView.java", """
import android.content.Context;
import android.view.View;
public class TextView extends View {
    public TextView(Context context) { super(context); }
    public void setText(CharSequence text) {}
    public void setText(int resid) {}
    public CharSequence getText() { return ""; }
    public void setTextColor(int color) {}
    public void setTextAppearance(int resId) {}
    public void setTextSize(float size) {}
    public void setTypeface(android.graphics.Typeface tf) {}
    public void setHint(CharSequence hint) {}
    public void setHint(int resid) {}
    public void setMinLines(int lines) {}
    public void setMaxLines(int lines) {}
    public void setHorizontallyScrolling(boolean scroll) {}
    public void setTextIsSelectable(boolean selectable) {}
    public void setMovementMethod(android.text.method.MovementMethod m) {}
    public void setSingleLine(boolean single) {}
}
""")
w("android/widget/EditText.java", """
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
public class EditText extends TextView {
    public EditText(Context context) { super(context); }
    public Editable getText() { return null; }
    public void addTextChangedListener(TextWatcher watcher) {}
    public void setSingleLine(boolean single) {}
}
""")
w("android/widget/Button.java", """
import android.content.Context;
public class Button extends TextView { public Button(Context context) { super(context); } }
""")
w("android/widget/CheckBox.java", """
import android.content.Context;
public class CheckBox extends Button {
    public CheckBox(Context context) { super(context); }
    public boolean isChecked() { return false; }
    public void setChecked(boolean checked) {}
}
""")
w("android/widget/CompoundButton.java", """
import android.content.Context;
import android.widget.CheckBox;
public class CompoundButton extends Button {
    public CompoundButton(Context context) { super(context); }
    public boolean isChecked() { return false; }
    public void setChecked(boolean checked) {}
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {}
    public interface OnCheckedChangeListener { void onCheckedChanged(CompoundButton b, boolean checked); }
}
""")
w("android/widget/ProgressBar.java", """
import android.content.Context;
import android.view.View;
public class ProgressBar extends View {
    public ProgressBar(Context context) { super(context); }
    public void setMax(int max) {}
    public int getMax() { return 0; }
    public void setProgress(int progress) {}
    public void setIndeterminate(boolean indeterminate) {}
}
""")
w("android/widget/AbsSeekBar.java", """
import android.content.Context;
public class AbsSeekBar extends ProgressBar {
    public AbsSeekBar(Context context) { super(context); }
    public int getProgress() { return 0; }
    public void setProgress(int progress) {}
    public void setMax(int max) {}
}
""")
w("android/widget/SeekBar.java", """
import android.content.Context;
public class SeekBar extends AbsSeekBar {
    public SeekBar(Context context) { super(context); }
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {}
    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);
        void onStartTrackingTouch(SeekBar seekBar);
        void onStopTrackingTouch(SeekBar seekBar);
    }
}
""")
w("android/widget/RadioButton.java", """
import android.content.Context;
public class RadioButton extends CompoundButton { public RadioButton(Context context) { super(context); } }
""")
w("android/widget/RadioGroup.java", """
import android.content.Context;
import android.view.ViewGroup;
public class RadioGroup extends LinearLayout {
    public RadioGroup(Context context) { super(context); }
    public int getCheckedRadioButtonId() { return -1; }
    public void check(int id) {}
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {}
    public interface OnCheckedChangeListener { void onCheckedChanged(RadioGroup group, int checkedId); }
}
""")
w("android/widget/LinearLayout.java", """
import android.content.Context;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1;
    public LinearLayout(Context context) { super(context); }
    public void setOrientation(int orientation) {}
    public int getOrientation() { return VERTICAL; }
}
""")
w("android/widget/FrameLayout.java", """
import android.content.Context;
import android.view.ViewGroup;
public class FrameLayout extends ViewGroup { public FrameLayout(Context context) { super(context); } }
""")
w("android/widget/ScrollView.java", """
import android.content.Context;
public class ScrollView extends FrameLayout { public ScrollView(Context context) { super(context); } }
""")
w("android/widget/ArrayAdapter.java", """
import android.content.Context;
import java.util.List;
public class ArrayAdapter<T> {
    public ArrayAdapter(Context context, int resource, T[] objects) {}
    public ArrayAdapter(Context context, int resource, List<T> objects) {}
    public void setDropDownViewResource(int resource) {}
    public T getItem(int position) { return null; }
    public int getCount() { return 0; }
}
""")
w("android/widget/AdapterView.java", """
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
public abstract class AdapterView<V> extends ViewGroup {
    public AdapterView(Context context) { super(context); }
    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);
        void onNothingSelected(AdapterView<?> parent);
    }
    public int getSelectedItemPosition() { return 0; }
    public Object getSelectedItem() { return null; }
}
""")
w("android/widget/Spinner.java", """
import android.content.Context;
public class Spinner extends AdapterView<Object> {
    public Spinner(Context context) { super(context); }
    public void setAdapter(Object adapter) {}
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {}
}
""")
w("android/widget/Toast.java", """
import android.content.Context;
public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;
    public static Toast makeText(Context context, CharSequence text, int duration) { return new Toast(); }
    public static Toast makeText(Context context, int resId, int duration) { return new Toast(); }
    public void show() {}
}
""")

# ---------------------------------------------------------------- android.text
w("android/text/Editable.java", "public interface Editable extends CharSequence {}\n")
w("android/text/TextWatcher.java", """
public interface TextWatcher {
    void beforeTextChanged(CharSequence s, int start, int count, int after);
    void onTextChanged(CharSequence s, int start, int before, int count);
    void afterTextChanged(Editable s);
}
""")

# ---------------------------------------------------------------- androidx
w("androidx/annotation/NonNull.java", """
package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface NonNull {}
""")
w("androidx/annotation/Nullable.java", """
package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface Nullable {}
""")
w("android/app/Activity.java", """
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
public class Activity extends Context {
    protected void onCreate(Bundle savedInstanceState) {}
    protected void onResume() {}
    protected void onPause() {}
    public void setContentView(int layoutResID) {}
    public <T extends android.view.View> T findViewById(int id) { return null; }
    public MenuInflater getMenuInflater() { return new MenuInflater(); }
    public boolean onCreateOptionsMenu(Menu menu) { return false; }
    public boolean onOptionsItemSelected(MenuItem item) { return false; }
    public void runOnUiThread(Runnable action) {}
    public void finish() {}
    public Intent getIntent() { return null; }
}
""")
w("androidx/fragment/app/Fragment.java", """
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
public class Fragment {
    public void onCreate(Bundle savedInstanceState) {}
    public void onResume() {}
    public void onPause() {}
    public void onStop() {}
    public void onStart() {}
    public void onDestroyView() {}
    public String getString(int resId) { return ""; }
    public String getString(int resId, Object... formatArgs) { return ""; }
    public android.content.res.Resources getResources() { return null; }
    public void onViewCreated(View v, Bundle savedInstanceState) {}
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { return null; }
    public void onDestroy() {}
    public Context requireContext() { return null; }
    public Activity getActivity() { return null; }
    public <T extends FragmentActivity> T requireActivity() { return null; }
    public void setArguments(Bundle b) {}
    public boolean isAdded() { return true; }
}
""")
w("androidx/fragment/app/FragmentActivity.java", "import android.app.Activity;\npublic class FragmentActivity extends Activity {}\n")
w("androidx/fragment/app/FragmentManager.java", "public class FragmentManager {}\n")
w("androidx/lifecycle/ViewModelProvider.java", "public class ViewModelProvider {}\n")
w("androidx/viewpager2/widget/ViewPager2.java", """
import android.content.Context;
import android.view.ViewGroup;
public class ViewPager2 extends ViewGroup {
    public ViewPager2(Context context) { super(context); }
    public void setAdapter(androidx.viewpager2.adapter.RecyclerViewAdapter adapter) {}
    public void setCurrentItem(int item) {}
    public int getCurrentItem() { return 0; }
}
""")
w("androidx/viewpager2/adapter/RecyclerViewAdapter.java", "public abstract class RecyclerViewAdapter {}\n")
w("androidx/viewpager2/adapter/FragmentStateAdapter.java", """
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
public abstract class FragmentStateAdapter extends RecyclerViewAdapter {
    public FragmentStateAdapter(FragmentActivity activity) {}
    public abstract Fragment createFragment(int position);
    public abstract int getItemCount();
}
""")
w("androidx/recyclerview/widget/RecyclerView.java", """
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
public class RecyclerView extends ViewGroup {
    public RecyclerView(Context context) { super(context); }
    public void setLayoutManager(LayoutManager layout) {}
    public void setAdapter(Adapter<?> adapter) {}
    public void scrollToPosition(int position) {}
    public abstract static class LayoutManager {}
    public abstract static class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);
        public abstract void onBindViewHolder(VH holder, int position);
        public abstract int getItemCount();
        public void notifyDataSetChanged() {}
        public void notifyItemInserted(int position) {}
        public void notifyItemRangeInserted(int start, int count) {}
    }
    public abstract static class ViewHolder {
        public final View itemView;
        public ViewHolder(View itemView) { this.itemView = itemView; }
    }
}
""")
w("androidx/recyclerview/widget/LinearLayoutManager.java", """
import android.content.Context;
public class LinearLayoutManager extends RecyclerView.LayoutManager {
    public LinearLayoutManager(Context context) {}
}
""")
w("androidx/appcompat/app/ActionBar.java", "public abstract class ActionBar {}\n")
w("androidx/appcompat/app/AppCompatActivity.java", """
import androidx.fragment.app.FragmentActivity;
public class AppCompatActivity extends FragmentActivity {
    public void setSupportActionBar(androidx.appcompat.widget.Toolbar toolbar) {}
    public androidx.appcompat.app.ActionBar getSupportActionBar() { return null; }
}
""")
w("androidx/appcompat/app/AlertDialog.java", """
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
public class AlertDialog {
    public static class Builder {
        public Builder(Context context) {}
        public Builder setTitle(int titleResId) { return this; }
        public Builder setView(View view) { return this; }
        public Builder setPositiveButton(int textResId, DialogInterface.OnClickListener l) { return this; }
        public Builder setNegativeButton(int textResId, DialogInterface.OnClickListener l) { return this; }
        public AlertDialog show() { return new AlertDialog(); }
    }
    public void dismiss() {}
}
""")
w("androidx/appcompat/widget/Toolbar.java", """
import android.content.Context;
import android.view.ViewGroup;
public class Toolbar extends ViewGroup { public Toolbar(Context context) { super(context); } }
""")
w("androidx/constraintlayout/widget/ConstraintLayout.java", """
package androidx.constraintlayout.widget;
import android.content.Context;
import android.view.ViewGroup;
public class ConstraintLayout extends ViewGroup { public ConstraintLayout(Context context) { super(context); } }
""")

# ---------------------------------------------------------------- material
w("com/google/android/material/tabs/TabLayout.java", """
import android.content.Context;
import android.view.ViewGroup;
public class TabLayout extends ViewGroup {
    public TabLayout(Context context) { super(context); }
    public static class Tab {
        public void setText(int resId) {}
        public void setText(CharSequence text) {}
    }
}
""")
w("com/google/android/material/tabs/TabLayoutMediator.java", """
public class TabLayoutMediator {
    public interface TabConfigurationStrategy {
        void onConfigureTab(TabLayout.Tab tab, int position);
    }
    public TabLayoutMediator(TabLayout tabLayout,
                             androidx.viewpager2.widget.ViewPager2 viewPager,
                             TabConfigurationStrategy strategy) {}
    public void attach() {}
    public void detach() {}
}
""")
w("com/google/android/material/appbar/MaterialToolbar.java", """
package com.google.android.material.appbar;
import android.content.Context;
import androidx.appcompat.widget.Toolbar;
public class MaterialToolbar extends Toolbar { public MaterialToolbar(Context context) { super(context); } }
""")
w("com/google/android/material/appbar/AppBarLayout.java", """
package com.google.android.material.appbar;
import android.content.Context;
import android.widget.LinearLayout;
public class AppBarLayout extends LinearLayout { public AppBarLayout(Context context) { super(context); } }
""")

# ---------------------------------------------------------------- org.json
w("org/json/JSONException.java", "package org.json;\npublic class JSONException extends Exception { public JSONException(String s){super(s);} }\n")
# The org.json stubs are functional rather than inert. An inert stub
# silently produces an empty request body, so the contract test would
# pass or fail for the wrong reason. These implement enough of the
# real API to build a payload and read one back.
w("org/json/JsonStub.java", r"""
package org.json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, shared by the JSONObject and JSONArray
 * stubs. Only the subset the app uses is implemented: objects, arrays,
 * strings, numbers, booleans and null.
 */
final class JsonStub {
    private final String text;
    private int at;

    private JsonStub(String text) { this.text = text; }

    static Object parse(String source) throws JSONException {
        JsonStub p = new JsonStub(source == null ? "" : source);
        p.skipSpace();
        Object value = p.value();
        p.skipSpace();
        if (p.at < p.text.length()) { throw new JSONException("trailing content"); }
        return value;
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) { at++; }
    }

    private Object value() throws JSONException {
        skipSpace();
        if (at >= text.length()) { throw new JSONException("unexpected end"); }
        char c = text.charAt(at);
        if (c == '{') { return object(); }
        if (c == '[') { return array(); }
        if (c == '"') { return string(); }
        if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
        if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
        if (text.startsWith("null", at)) { at += 4; return null; }
        return number();
    }

    private JSONObject object() throws JSONException {
        JSONObject out = new JSONObject();
        at++;
        skipSpace();
        if (at < text.length() && text.charAt(at) == '}') { at++; return out; }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            if (at >= text.length() || text.charAt(at) != ':') { throw new JSONException("expected ':'"); }
            at++;
            out.put(key, value());
            skipSpace();
            if (at >= text.length()) { throw new JSONException("unterminated object"); }
            char c = text.charAt(at++);
            if (c == '}') { return out; }
            if (c != ',') { throw new JSONException("expected ',' or '}'"); }
        }
    }

    private JSONArray array() throws JSONException {
        JSONArray out = new JSONArray();
        at++;
        skipSpace();
        if (at < text.length() && text.charAt(at) == ']') { at++; return out; }
        while (true) {
            out.put(value());
            skipSpace();
            if (at >= text.length()) { throw new JSONException("unterminated array"); }
            char c = text.charAt(at++);
            if (c == ']') { return out; }
            if (c != ',') { throw new JSONException("expected ',' or ']'"); }
        }
    }

    private String string() throws JSONException {
        if (at >= text.length() || text.charAt(at) != '"') { throw new JSONException("expected string"); }
        at++;
        StringBuilder sb = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') { return sb.toString(); }
            if (c != '\\') { sb.append(c); continue; }
            if (at >= text.length()) { break; }
            char esc = text.charAt(at++);
            switch (esc) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (at + 4 > text.length()) { throw new JSONException("bad unicode escape"); }
                    sb.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                    break;
                default: sb.append(esc);
            }
        }
        throw new JSONException("unterminated string");
    }

    private Object number() throws JSONException {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) { at++; }
        if (start == at) { throw new JSONException("expected value"); }
        String raw = text.substring(start, at);
        try {
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return Double.valueOf(raw);
            }
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new JSONException("bad number: " + raw);
        }
    }

    static String writeObject(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) { sb.append(','); }
            first = false;
            sb.append(quote(e.getKey())).append(':').append(write(e.getValue()));
        }
        return sb.append('}').toString();
    }

    static String writeArray(List<Object> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) { sb.append(','); }
            sb.append(write(values.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String write(Object value) {
        if (value == null) { return "null"; }
        if (value instanceof String) { return quote((String) value); }
        if (value instanceof Boolean || value instanceof Number) { return String.valueOf(value); }
        if (value instanceof JSONObject) { return value.toString(); }
        if (value instanceof JSONArray) { return value.toString(); }
        return quote(String.valueOf(value));
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
""")

w("org/json/JSONObject.java", """
package org.json;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class JSONObject {
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    public JSONObject() {}
    public JSONObject(String source) throws JSONException {
        Object parsed = JsonStub.parse(source);
        if (!(parsed instanceof JSONObject)) { throw new JSONException("not an object"); }
        values.putAll(((JSONObject) parsed).values);
    }
    public Iterator<String> keys() { return new ArrayList<String>(values.keySet()).iterator(); }
    public int length() { return values.size(); }
    public Object get(String key) throws JSONException {
        if (!values.containsKey(key)) { throw new JSONException("no such key: " + key); }
        return values.get(key);
    }
    public JSONObject getJSONObject(String key) throws JSONException {
        Object v = get(key);
        if (!(v instanceof JSONObject)) { throw new JSONException("not an object: " + key); }
        return (JSONObject) v;
    }
    public JSONArray getJSONArray(String key) throws JSONException {
        Object v = get(key);
        if (!(v instanceof JSONArray)) { throw new JSONException("not an array: " + key); }
        return (JSONArray) v;
    }
    public JSONArray optJSONArray(String key) {
        Object v = values.get(key);
        return (v instanceof JSONArray) ? (JSONArray) v : null;
    }
    public JSONObject optJSONObject(String key) {
        Object v = values.get(key);
        return (v instanceof JSONObject) ? (JSONObject) v : null;
    }
    public String optString(String key) { return optString(key, ""); }
    public String optString(String key, String fallback) {
        Object v = values.get(key);
        return (v == null) ? fallback : String.valueOf(v);
    }
    public int optInt(String key, int fallback) {
        Object v = values.get(key);
        if (v instanceof Number) { return ((Number) v).intValue(); }
        return fallback;
    }
    public boolean optBoolean(String key, boolean fallback) {
        Object v = values.get(key);
        if (v instanceof Boolean) { return ((Boolean) v).booleanValue(); }
        return fallback;
    }
    public boolean has(String key) { return values.containsKey(key); }
    public JSONObject put(String name, Object value) throws JSONException { values.put(name, value); return this; }
    public JSONObject put(String name, String value) throws JSONException { values.put(name, value); return this; }
    public JSONObject put(String name, int value) throws JSONException { values.put(name, Integer.valueOf(value)); return this; }
    public JSONObject put(String name, boolean value) throws JSONException { values.put(name, Boolean.valueOf(value)); return this; }
    public JSONObject put(String name, double value) throws JSONException { values.put(name, Double.valueOf(value)); return this; }
    public String toString() { return JsonStub.writeObject(values); }
}
""")
w("org/json/JSONArray.java", """
package org.json;
import java.util.ArrayList;
import java.util.List;
public class JSONArray {
    private final List<Object> values = new ArrayList<Object>();
    public JSONArray() {}
    public JSONArray(String source) throws JSONException {
        Object parsed = JsonStub.parse(source);
        if (!(parsed instanceof JSONArray)) { throw new JSONException("not an array"); }
        values.addAll(((JSONArray) parsed).values);
    }
    public int length() { return values.size(); }
    public Object get(int index) throws JSONException {
        if (index < 0 || index >= values.size()) { throw new JSONException("index out of range: " + index); }
        return values.get(index);
    }
    public String getString(int index) throws JSONException { return String.valueOf(get(index)); }
    public String optString(int index) {
        if (index < 0 || index >= values.size()) { return ""; }
        return String.valueOf(values.get(index));
    }
    public JSONObject getJSONObject(int index) throws JSONException {
        Object v = get(index);
        if (!(v instanceof JSONObject)) { throw new JSONException("not an object"); }
        return (JSONObject) v;
    }
    public JSONObject optJSONObject(int index) {
        if (index < 0 || index >= values.size()) { return null; }
        Object v = values.get(index);
        return (v instanceof JSONObject) ? (JSONObject) v : null;
    }
    public JSONArray optJSONArray(int index) {
        if (index < 0 || index >= values.size()) { return null; }
        Object v = values.get(index);
        return (v instanceof JSONArray) ? (JSONArray) v : null;
    }
    public int getInt(int index) throws JSONException {
        Object v = get(index);
        return (v instanceof Number) ? ((Number) v).intValue() : 0;
    }
    public JSONArray put(Object value) { values.add(value); return this; }
    public String toString() { return JsonStub.writeArray(values); }
}
""")

# ---------------------------------------------------------------- material
w("com/google/android/material/button/MaterialButton.java", """
package com.google.android.material.button;
import android.content.Context;
import android.widget.Button;
public class MaterialButton extends Button { public MaterialButton(Context c) { super(c); } }
""")
w("com/google/android/material/button/MaterialButtonToggleGroup.java", """
package com.google.android.material.button;
import android.content.Context;
import android.widget.LinearLayout;
public class MaterialButtonToggleGroup extends LinearLayout {
    public MaterialButtonToggleGroup(Context c) { super(c); }
    public void check(int id) {}
    public int getCheckedButtonId() { return 0; }
    public void addOnButtonCheckedListener(OnButtonCheckedListener l) {}
    public interface OnButtonCheckedListener {
        void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked);
    }
}
""")
w("com/google/android/material/chip/Chip.java", """
package com.google.android.material.chip;
import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;
public class Chip extends TextView {
    public Chip(Context c) { super(c); }
    public void setChipBackgroundColor(ColorStateList color) {}
    public void setEnsureMinTouchTargetSize(boolean b) {}
}
""")
w("com/google/android/material/slider/Slider.java", """
package com.google.android.material.slider;
import android.content.Context;
import android.widget.AbsSeekBar;
public class Slider extends AbsSeekBar {
    public Slider(Context c) { super(c); }
    public float getValue() { return 0f; }
    public void setValue(float v) {}
    public void addOnChangeListener(OnChangeListener l) {}
    public interface OnChangeListener {
        void onValueChange(Slider slider, float value, boolean fromUser);
    }
}
""")
w("com/google/android/material/textfield/TextInputLayout.java", """
package com.google.android.material.textfield;
import android.content.Context;
import android.widget.LinearLayout;
public class TextInputLayout extends LinearLayout {
    public TextInputLayout(Context c) { super(c); }
    public void setHint(CharSequence hint) {}
    public void setEndIconMode(int mode) {}
}
""")
w("com/google/android/material/textfield/TextInputEditText.java", """
package com.google.android.material.textfield;
import android.content.Context;
import android.widget.EditText;
public class TextInputEditText extends EditText { public TextInputEditText(Context c) { super(c); } }
""")
w("com/google/android/material/R.java", """
package com.google.android.material;
public class R {
    public static final class style {
        public static final int TextAppearance_Material3_BodyMedium = 1;
        public static final int TextAppearance_Material3_HeadlineSmall = 2;
        public static final int TextAppearance_Material3_LabelLarge = 3;
        public static final int Widget_Material3_Button = 4;
        public static final int Widget_Material3_TextInputLayout_OutlinedBox = 5;
    }
}
""")
w("com/google/android/material/card/MaterialCardView.java", """
package com.google.android.material.card;
import android.content.Context;
import android.widget.FrameLayout;
public class MaterialCardView extends FrameLayout {
    public MaterialCardView(Context c) { super(c); }
    public void setCardBackgroundColor(int color) {}
    public void setRadius(float r) {}
    public void setCardElevation(float elevation) {}
    public void setStrokeWidth(int width) {}
    public void setStrokeColor(int color) {}
    public void setContentPadding(int l, int t, int r, int b) {}
}
""")
w("com/google/android/material/checkbox/MaterialCheckBox.java", """
package com.google.android.material.checkbox;
import android.content.Context;
import android.widget.CheckBox;
public class MaterialCheckBox extends CheckBox { public MaterialCheckBox(Context c) { super(c); } }
""")
w("androidx/core/content/ContextCompat.java", """
package androidx.core.content;
import android.content.Context;
public class ContextCompat {
    public static int checkSelfPermission(Context context, String permission) { return 0; }
    public static int getColor(Context context, int id) { return 0; }
    public static String getString(Context context, int id) { return ""; }
}
""")
w("android/content/res/ColorStateList.java", """
package android.content.res;
public class ColorStateList {
    public static ColorStateList valueOf(int color) { return new ColorStateList(); }
}
""")


# ---------------------------------------------------------------- android.webkit
w("android/webkit/WebView.java", """
package android.webkit;
import android.content.Context;
public class WebView extends android.view.View {
    public WebView(Context context) { super(context); }
    public android.webkit.WebSettings getSettings() { return new android.webkit.WebSettings(); }
    public void loadUrl(String url) {}
    public void setWebViewClient(WebViewClient client) {}
    public void destroy() {}
}
""")
w("android/webkit/WebSettings.java", """
package android.webkit;
public class WebSettings {
    public void setJavaScriptEnabled(boolean enabled) {}
    public void setDomStorageEnabled(boolean enabled) {}
    public void setAllowFileAccess(boolean enabled) {}
    public void setAllowContentAccess(boolean enabled) {}
}
""")
w("android/webkit/WebViewClient.java", """
package android.webkit;
import android.view.View;
public class WebViewClient {
    public void onPageFinished(WebView view, String url) {}
}
""")

# ---------------------------------------------------------------- android.graphics
w("android/graphics/Typeface.java", """
package android.graphics;
public class Typeface {
    public static final Typeface DEFAULT = new Typeface();
    public static final Typeface DEFAULT_BOLD = new Typeface();
    public static final Typeface MONOSPACE = new Typeface();
    public static Typeface create(String name, int style) { return new Typeface(); }
}
""")

# ---------------------------------------------------------------- android.os extras
w("android/os/Build.java", """
package android.os;
public class Build {
    public static final String MODEL = "stub";
    public static final String FINGERPRINT = "stub";
    public static final String MANUFACTURER = "stub";
    public static class VERSION { public static final int SDK_INT = 34; }
}
""")

w("android/os/Process.java", """
package android.os;
public class Process {
    public static int myUid() { return 0; }
}
""")

# ---------------------------------------------------------------- android.net
w("android/net/Uri.java", """
package android.net;
public class Uri {
    public static String encode(String s) { return s; }
    public static String encode(String s, String allow) { return s; }
    public static Uri parse(String s) { return new Uri(); }
    public String toString() { return ""; }
}
""")

# ---------------------------------------------------------------- android.util
w("android/util/Log.java", """
package android.util;
public class Log {
    public static int v(String tag, String msg) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static String getStackTraceString(Throwable t) { return ""; }
}
""")

# ---------------------------------------------------------------- android.text.method
w("android/text/method/MovementMethod.java", """
package android.text.method;
public interface MovementMethod { }
""")

w("android/text/method/ScrollingMovementMethod.java", """
package android.text.method;
public class ScrollingMovementMethod implements MovementMethod { }
""")

# ---------------------------------------------------------------- android.provider
w("android/provider/Settings.java", """
package android.provider;
import android.content.Context;
public class Settings {
    public static class Secure {
        public static String getString(android.content.ContentResolver r, String name) { return null; }
        public static int getInt(android.content.ContentResolver r, String name, int def) { return def; }
    }
    public static class Global {
        public static String getString(android.content.ContentResolver r, String name) { return null; }
    }
    public static class System {
        public static String getString(android.content.ContentResolver r, String name) { return null; }
    }
}
""")

w("android/content/ContentResolver.java", """
package android.content;
public class ContentResolver { }
""")

# ---------------------------------------------------------------- android.content.pm
w("android/content/pm/ApplicationInfo.java", """
package android.content.pm;
public class ApplicationInfo {
    public static final int FLAG_DEBUGGABLE = 2;
    public static final int FLAG_SYSTEM = 1;
    public int flags;
    public int uid;
    public String packageName;
    public String name;
}
""")

w("android/content/pm/PackageManager.java", """
package android.content.pm;
import java.util.List;
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int GET_META_DATA = 128;
    public static final int MATCH_DISABLED_COMPONENTS = 512;
    public List<ApplicationInfo> getInstalledApplications(int flags) { return null; }
    public ApplicationInfo getApplicationInfo(String packageName, int flags) { return null; }
}
""")

# ---------------------------------------------------------------- android.app extras
w("android/app/Application.java", """
package android.app;
import android.content.Context;
public class Application extends Context {
    public Context getApplicationContext() { return this; }
}
""")

w("android/app/ActivityManager.java", """
package android.app;
import java.util.List;
public class ActivityManager {
    public static class RunningAppProcessInfo {
        public int pid;
        public int uid;
        public String processName;
    }
    public List<RunningAppProcessInfo> getRunningAppProcesses() { return null; }
}
""")

w("android/app/AlertDialog.java", """
package android.app;
import android.content.DialogInterface;
public class AlertDialog {
    public static class Builder {
        public Builder(android.content.Context context) { }
        public Builder setTitle(int titleId) { return this; }
        public Builder setTitle(CharSequence title) { return this; }
        public Builder setView(android.view.View view) { return this; }
        public Builder setPositiveButton(int text, DialogInterface.OnClickListener l) { return this; }
        public Builder setNegativeButton(int text, DialogInterface.OnClickListener l) { return this; }
        public Builder setMessage(int messageId) { return this; }
        public AlertDialog create() { return new AlertDialog(); }
        public AlertDialog show() { return new AlertDialog(); }
    }
    public void dismiss() { }
    public void show() { }
}
""")

print("stubs written under", ROOT)
