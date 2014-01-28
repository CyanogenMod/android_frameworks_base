/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.os;

import java.io.PrintWriter;

/**
 * Representation of a user on the device.
 */
public final class UserHandle implements Parcelable {
    /**
     * @hide Range of uids allocated for a user.
     */
    public static final int PER_USER_RANGE = 100000;

    /** @hide A user id to indicate all users on the device */
    public static final int USER_ALL = -1;

    /** @hide A user handle to indicate all users on the device */
    public static final UserHandle ALL = new UserHandle(USER_ALL);

    /** @hide A user id to indicate the currently active user */
    public static final int USER_CURRENT = -2;

    /** @hide A user handle to indicate the current user of the device */
    public static final UserHandle CURRENT = new UserHandle(USER_CURRENT);

    /** @hide A user id to indicate that we would like to send to the current
     *  user, but if this is calling from a user process then we will send it
     *  to the caller's user instead of failing wiht a security exception */
    public static final int USER_CURRENT_OR_SELF = -3;

    /** @hide A user handle to indicate that we would like to send to the current
     *  user, but if this is calling from a user process then we will send it
     *  to the caller's user instead of failing wiht a security exception */
    public static final UserHandle CURRENT_OR_SELF = new UserHandle(USER_CURRENT_OR_SELF);

    /** @hide An undefined user id */
    public static final int USER_NULL = -10000;

    /** @hide A user id constant to indicate the "owner" user of the device */
    public static final int USER_OWNER = 0;

    /** @hide A user handle to indicate the primary/owner user of the device */
    public static final UserHandle OWNER = new UserHandle(USER_OWNER);

    /**
     * @hide Enable multi-user related side effects. Set this to false if
     * there are problems with single user use-cases.
     */
    public static final boolean MU_ENABLED = true;

    final int mHandle;

    /**
     * Checks to see if the user id is the same for the two uids, i.e., they belong to the same
     * user.
     * @hide
     */
    public static final boolean isSameUser(int uid1, int uid2) {
        return getUserId(uid1) == getUserId(uid2);
    }

    /**
     * Checks to see if both uids are referring to the same app id, ignoring the user id part of the
     * uids.
     * @param uid1 uid to compare
     * @param uid2 other uid to compare
     * @return whether the appId is the same for both uids
     * @hide
     */
    public static final boolean isSameApp(int uid1, int uid2) {
        return getAppId(uid1) == getAppId(uid2);
    }

    /** @hide */
    public static final boolean isIsolated(int uid) {
        if (uid > 0) {
            final int appId = getAppId(uid);
            return appId >= Process.FIRST_ISOLATED_UID && appId <= Process.LAST_ISOLATED_UID;
        } else {
            return false;
        }
    }

    /** @hide */
    public static boolean isApp(int uid) {
        if (uid > 0) {
            final int appId = getAppId(uid);
            return appId >= Process.FIRST_APPLICATION_UID && appId <= Process.LAST_APPLICATION_UID;
        } else {
            return false;
        }
    }

    /**
     * Returns the user id for a given uid.
     * @hide
     */
    public static final int getUserId(int uid) {
        if (MU_ENABLED) {
            return uid / PER_USER_RANGE;
        } else {
            return 0;
        }
    }

    /** @hide */
    public static final int getCallingUserId() {
        return getUserId(Binder.getCallingUid());
    }

    /**
     * Returns the uid that is composed from the userId and the appId.
     * @hide
     */
    public static final int getUid(int userId, int appId) {
        if (MU_ENABLED) {
            return userId * PER_USER_RANGE + (appId % PER_USER_RANGE);
        } else {
            return appId;
        }
    }

    /**
     * Returns the app id (or base uid) for a given uid, stripping out the user id from it.
     * @hide
     */
    public static final int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }

    /**
     * Returns the shared app gid for a given uid or appId.
     * @hide
     */
    public static final int getSharedAppGid(int id) {
        return Process.FIRST_SHARED_APPLICATION_GID + (id % PER_USER_RANGE)
                - Process.FIRST_APPLICATION_UID;
    }

    /**
     * Generate a text representation of the uid, breaking out its individual
     * components -- user, app, isolated, etc.
     * @hide
     */
    public static void formatUid(StringBuilder sb, int uid) {
        if (uid < Process.FIRST_APPLICATION_UID) {
            sb.append(uid);
        } else {
            sb.append('u');
            sb.append(getUserId(uid));
            final int appId = getAppId(uid);
            if (appId >= Process.FIRST_ISOLATED_UID && appId <= Process.LAST_ISOLATED_UID) {
                sb.append('i');
                sb.append(appId - Process.FIRST_ISOLATED_UID);
            } else if (appId >= Process.FIRST_APPLICATION_UID) {
                sb.append('a');
                sb.append(appId - Process.FIRST_APPLICATION_UID);
            } else {
                sb.append('s');
                sb.append(appId);
            }
        }
    }

    /**
     * Generate a text representation of the uid, breaking out its individual
     * components -- user, app, isolated, etc.
     * @hide
     */
    public static void formatUid(PrintWriter pw, int uid) {
        if (uid < Process.FIRST_APPLICATION_UID) {
            pw.print(uid);
        } else {
            pw.print('u');
            pw.print(getUserId(uid));
            final int appId = getAppId(uid);
            if (appId >= Process.FIRST_ISOLATED_UID && appId <= Process.LAST_ISOLATED_UID) {
                pw.print('i');
                pw.print(appId - Process.FIRST_ISOLATED_UID);
            } else if (appId >= Process.FIRST_APPLICATION_UID) {
                pw.print('a');
                pw.print(appId - Process.FIRST_APPLICATION_UID);
            } else {
                pw.print('s');
                pw.print(appId);
            }
        }
    }

    /**
     * Returns the user id of the current process
     * @return user id of the current process
     * @hide
     */
    public static final int myUserId() {
        return getUserId(Process.myUid());
    }

    /** @hide */
    public UserHandle(int h) {
        mHandle = h;
    }

    /** @hide */
    public int getIdentifier() {
        return mHandle;
    }

    @Override
    public String toString() {
        return "UserHandle{" + mHandle + "}";
    }

    @Override
    public boolean equals(Object obj) {
        try {
            if (obj != null) {
                UserHandle other = (UserHandle)obj;
                return mHandle == other.mHandle;
            }
        } catch (ClassCastException e) {
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mHandle;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mHandle);
    }

    /**
     * Write a UserHandle to a Parcel, handling null pointers.  Must be
     * read with {@link #readFromParcel(Parcel)}.
     *
     * @param h The UserHandle to be written.
     * @param out The Parcel in which the UserHandle will be placed.
     *
     * @see #readFromParcel(Parcel)
     */
    public static void writeToParcel(UserHandle h, Parcel out) {
        if (h != null) {
            h.writeToParcel(out, 0);
        } else {
            out.writeInt(USER_NULL);
        }
    }

    /**
     * Read a UserHandle from a Parcel that was previously written
     * with {@link #writeToParcel(UserHandle, Parcel)}, returning either
     * a null or new object as appropriate.
     *
     * @param in The Parcel from which to read the UserHandle
     * @return Returns a new UserHandle matching the previously written
     * object, or null if a null had been written.
     *
     * @see #writeToParcel(UserHandle, Parcel)
     */
    public static UserHandle readFromParcel(Parcel in) {
        int h = in.readInt();
        return h != USER_NULL ? new UserHandle(h) : null;
    }

    public static final Parcelable.Creator<UserHandle> CREATOR
            = new Parcelable.Creator<UserHandle>() {
        public UserHandle createFromParcel(Parcel in) {
            return new UserHandle(in);
        }

        public UserHandle[] newArray(int size) {
            return new UserHandle[size];
        }
    };

    /**
     * Instantiate a new UserHandle from the data in a Parcel that was
     * previously written with {@link #writeToParcel(Parcel, int)}.  Note that you
     * must not use this with data written by
     * {@link #writeToParcel(UserHandle, Parcel)} since it is not possible
     * to handle a null UserHandle here.
     *
     * @param in The Parcel containing the previously written UserHandle,
     * positioned at the location in the buffer where it was written.
     */
    public UserHandle(Parcel in) {
        mHandle = in.readInt();
    }
}
