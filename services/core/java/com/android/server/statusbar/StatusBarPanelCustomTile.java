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

package com.android.server.statusbar;

import android.app.CustomTile;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

/**
 * Class encapsulating a Custom Tile. Sent by the StatusBarManagerService to clients including
 * the status bar panel.
 */
public class StatusBarPanelCustomTile implements Parcelable {

    private final String pkg;
    private final int id;
    private final String tag;
    private final String key;

    private final int uid;
    private final String opPkg;
    private final int initialPid;
    private final CustomTile customTile;
    private final UserHandle user;
    private final long postTime;
    /** @hide */
    public StatusBarPanelCustomTile(String pkg, String opPkg, int id, String tag, int uid,
                                 int initialPid, CustomTile customTile, UserHandle user) {
        this(pkg, opPkg, id, tag, uid, initialPid, customTile, user,
                System.currentTimeMillis());
    }

    public StatusBarPanelCustomTile(String pkg, String opPkg, int id, String tag, int uid,
                                 int initialPid, CustomTile customTile, UserHandle user,
                                 long postTime) {
        if (pkg == null) throw new NullPointerException();
        if (customTile == null) throw new NullPointerException();

        this.pkg = pkg;
        this.opPkg = opPkg;
        this.id = id;
        this.tag = tag;
        this.uid = uid;
        this.initialPid = initialPid;
        this.customTile = customTile;
        this.user = user;
        this.postTime = postTime;
        this.key = key();
    }

    private String key() {
        return user.getIdentifier() + "|" + pkg + "|" + id + "|" + tag + "|" + uid;
    }

    /** The {@link android.app.Notification} supplied to
     * {@link android.app.StatusBarManager#createTile(int,android.app.CustomTile)}. */
    public CustomTile getCustomTile() {
        return customTile;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {

    }

    public String getPackage() {
        return pkg;
    }

    public int getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public String getKey() {
        return key;
    }

    public int getUid() {
        return uid;
    }

    public String getOpPkg() {
        return opPkg;
    }

    public int getInitialPid() {
        return initialPid;
    }

    public UserHandle getUser() {
        return user;
    }

    public long getPostTime() {
        return postTime;
    }
}
