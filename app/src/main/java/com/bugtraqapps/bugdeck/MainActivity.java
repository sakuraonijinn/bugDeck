package com.bugtraqapps.bugdeck;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public final class MainActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private TabsAdapter adapter;

    private static final int[] TAB_TITLES = {
        R.string.tab_scanner, R.string.tab_hackbar, R.string.tab_dork
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        pager = findViewById(R.id.pager);
        adapter = new TabsAdapter(this);
        pager.setAdapter(adapter);

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            if (position >= 0 && position < TAB_TITLES.length) {
                tab.setText(TAB_TITLES[position]);
            }
        }).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            SettingsDialog.show(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
