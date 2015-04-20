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

package org.cyanogenmod.internal.statusbar;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import cyanogenmod.app.CustomTile;

/**
 * Class encapsulating a Custom Tile. Sent by the StatusBarManagerService to clients including
 * the status bar panel.
 * @hide
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


    public StatusBarPanelCustomTile(Parcel in) {
        this.pkg = in.readString();
        this.opPkg = in.readString();
        this.id = in.readInt();
        if (in.readInt() != 0) {
            this.tag = in.readString();
        } else {
            this.tag = null;
        }
        this.uid = in.readInt();
        this.initialPid = in.readInt();
        this.customTile = new CustomTile(in);
        this.user = UserHandle.readFromParcel(in);
        this.postTime = in.readLong();
        this.key = key();
    }

    private String key() {
        return user.getIdentifier() + "|" + pkg + "|" + id + "|" + tag + "|" + uid;
    }

    public static final Parcelable.Creator<StatusBarPanelCustomTile> CREATOR
            = new Parcelable.Creator<StatusBarPanelCustomTile>()
    {
        public StatusBarPanelCustomTile createFromParcel(Parcel parcel)
        {
            return new StatusBarPanelCustomTile(parcel);
        }

        public StatusBarPanelCustomTile[] newArray(int size)
        {
            return new StatusBarPanelCustomTile[size];
        }
    };

    /** The {@link CustomTile} supplied to
     * {@link org.cyanogenmod.platform.app.CMStatusBarManager#publishTile(int,CustomTile)}.
     */
    public CustomTile getCustomTile() {
        return customTile;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.pkg);
        out.writeString(this.opPkg);
        out.writeInt(this.id);
        if (this.tag != null) {
            out.writeInt(1);
            out.writeString(this.tag);
        } else {
            out.writeInt(0);
        }
        out.writeInt(this.uid);
        out.writeInt(this.initialPid);
        this.customTile.writeToParcel(out, flags);
        user.writeToParcel(out, flags);
        out.writeLong(this.postTime);
    }

    @Override
    public StatusBarPanelCustomTile clone() {
        return new StatusBarPanelCustomTile(this.pkg, this.opPkg,
                this.id, this.tag, this.uid, this.initialPid,
                this.customTile.clone(), this.user, this.postTime);
    }

    /**
     * Returns a userHandle for the instance of the app that posted this notification.
     *
     * @deprecated Use {@link #getUser()} instead.
     */
    public int getUserId() {
        return this.user.getIdentifier();
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
