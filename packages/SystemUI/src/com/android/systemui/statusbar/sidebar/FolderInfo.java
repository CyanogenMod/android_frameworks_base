/*
 * Copyright (C) 2013 The ChameleonOS Project
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

package com.android.systemui.statusbar.sidebar;

import java.util.ArrayList;

/**
 * Represents a folder containing shortcuts or apps.
 */
public class FolderInfo extends ItemInfo {

    /**
     * Whether this folder has been opened
     */
    boolean opened;

    /**
     * The apps
     */
    ArrayList<AppItemInfo> contents = new ArrayList<AppItemInfo>();

    ArrayList<FolderListener> listeners = new ArrayList<FolderListener>();

    public FolderInfo() {
        itemType = ItemInfo.TYPE_FOLDER;
        container = CONTAINER_SIDEBAR;
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(AppItemInfo item) {
        item.container = ItemInfo.CONTAINER_FOLDER;
        contents.add(item);
        for (FolderListener listener : listeners) {
            listener.onAdd(item);
        }
        itemsChanged();
    }

    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(AppItemInfo item) {
        contents.remove(item);
        for (FolderListener listener : listeners) {
            listener.onRemove(item);
        }
        itemsChanged();
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        for (FolderListener listener : listeners) {
            listener.onTitleChanged(title);
        }
    }
/*
    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);
        values.put(LauncherSettings.Favorites.TITLE, title.toString());
    }
*/
    public void addListener(FolderListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FolderListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    void itemsChanged() {
        for (FolderListener listener : listeners) {
            listener.onItemsChanged();
        }
    }

    public void unbind() {
        listeners.clear();
    }

    interface FolderListener {
        public void onAdd(AppItemInfo item);
        public void onRemove(AppItemInfo item);
        public void onTitleChanged(CharSequence title);
        public void onItemsChanged();
    }
}
