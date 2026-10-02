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
        public LayoutParams(int w, int h) { width = w; height = h; }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public MarginLayoutParams(int w, int h) { super(w, h); }
    }
    public void addView(View child) {}
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
w("android/view/Gravity.java", "public class Gravity { public static final int CENTER_VERTICAL = 16; }\n")

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
    public String getString(int resId) { return ""; }
    public String getString(int resId, Object... formatArgs) { return ""; }
    public android.content.res.Resources getResources() { return null; }
    public void onViewCreated(View v, Bundle savedInstanceState) {}
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { return null; }
    public void onDestroy() {}
    public void onResume() {}
    public View getView() { return null; }
    public Context requireContext() { return null; }
    public Activity getActivity() { return null; }
    public <T> T requireActivity() { return null; }
    public void setArguments(Bundle b) {}
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
w("org/json/JSONObject.java", """
package org.json;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
public class JSONObject {
    public JSONObject() {}
    public JSONObject(String source) throws JSONException {}
    public Iterator<String> keys() { return new ArrayList<String>().iterator(); }
    public Object get(String key) throws JSONException { return null; }
    public JSONObject getJSONObject(String key) throws JSONException { return null; }
    public JSONArray getJSONArray(String key) throws JSONException { return null; }
    public JSONArray optJSONArray(String key) { return null; }
    public JSONObject optJSONObject(String key) { return null; }
    public String optString(String key) { return ""; }
    public String optString(String key, String fallback) { return fallback; }
}
""")
w("org/json/JSONArray.java", """
package org.json;
public class JSONArray {
    public JSONArray() {}
    public JSONArray(String source) throws JSONException {}
    public int length() { return 0; }
    public String getString(int index) throws JSONException { return ""; }
    public String optString(int index) { return ""; }
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
w("com/google/android/material/card/MaterialCardView.java", """
package com.google.android.material.card;
import android.content.Context;
import android.widget.FrameLayout;
public class MaterialCardView extends FrameLayout {
    public MaterialCardView(Context c) { super(c); }
    public void setCardBackgroundColor(int color) {}
    public void setRadius(float r) {}
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

w("android/content/pm/PackageManager.java", """
package android.content.pm;
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
}
""")
w("android/content/pm/ApplicationInfo.java", """
package android.content.pm;
public class ApplicationInfo { public String packageName; }
""")
w("android/content/pm/PackageInfo.java", """
package android.content.pm;
public class PackageInfo { public String packageName; public ApplicationInfo applicationInfo; }
""")
w("android/os/RemoteCallback.java", "public class RemoteCallback {}\n")
w("android/os/RemoteException.java", "public class RemoteException extends Exception {}\n")

# rikka.shizuku is a real gradle dependency; the stub only exists so the GUI
# typecheck can resolve the direct import. All actual use goes through
# reflection in ShizukuState so the app never hard-depends on it at runtime.
w("rikka/shizuku/Shizuku.java", """
package rikka.shizuku;
import android.content.Context;
public class Shizuku {
    public static boolean pingBinder() { return false; }
    public static int checkSelfPermission() { return -1; }
    public static void requestPermission(int code) {}
    public static void addRequestPermissionResultListener(Object l) {}
}
""")

print("stubs written under", ROOT)
