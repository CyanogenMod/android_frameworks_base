/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import cyanogenmod.app.StatusBarPanelCustomTile;

import android.util.ArrayMap;

/**
 * Custom tile data to keep track of created 3rd party tiles
 */
public class CustomTileData {
    public static final class Entry {
        public final String key;
        public final StatusBarPanelCustomTile sbc;

        public Entry(StatusBarPanelCustomTile sbc) {
            this.key = sbc.persistableKey();
            this.sbc = sbc;
        }
    }

    private final ArrayMap<String, Entry> mEntries = new ArrayMap<>();

    public ArrayMap<String, Entry> getEntries() {
        return mEntries;
    }

    public void add(Entry entry) {
        mEntries.put(entry.key, entry);
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