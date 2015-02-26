package com.android.systemui.statusbar;

import android.service.statusbar.StatusBarPanelCustomTile;
import android.util.ArrayMap;

/**
 * Created by Adnan on 3/2/15.
 */
public class CustomTileData {
    public static final class Entry {
        public String key;
        public StatusBarPanelCustomTile statusBarPanelCustomTile;

        public Entry(StatusBarPanelCustomTile sbc) {
            this.key = sbc.getKey();
        }
    }

    private final ArrayMap<String, Entry> mEntries = new ArrayMap<>();

    public ArrayMap<String, Entry> getEntries() {
        return mEntries;
    }

    public void add(Entry entry) {
        mEntries.put(entry.statusBarPanelCustomTile.getKey(), entry);
    }

    public Entry remove(String key) {
        Entry removed = mEntries.remove(key);
        if (removed == null) return null;
        return removed;
    }

    public Entry get(String key) {
        return mEntries.get(key);
    }

    public Entry get(int i) {
        return mEntries.valueAt(i);
    }

    public void clear() {
        mEntries.clear();
    }

    public int size() {
        return mEntries.size();
    }
}
