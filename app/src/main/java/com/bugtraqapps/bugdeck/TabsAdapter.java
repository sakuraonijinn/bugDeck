package com.bugtraqapps.bugdeck;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public final class TabsAdapter extends FragmentStateAdapter {

    public TabsAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:  return new ScannerFragment();
            case 1:  return new HackBarFragment();
            case 2:  return new DorkFragment();
            case 3:  return new DeviceFragment();
            default: throw new IndexOutOfBoundsException("no tab " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
