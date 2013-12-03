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

package android.os.storage;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import java.io.File;

/**
 * Description of a storage volume and its capabilities, including the
 * filesystem path where it may be mounted.
 *
 * @hide
 */
public class StorageVolume implements Parcelable {

    // TODO: switch to more durable token
    private int mStorageId;

    private int mFilesystemId;

    private final File mPath;
    private final int mDescriptionId;
    private final boolean mPrimary;
    private final boolean mRemovable;
    private final boolean mEmulated;
    private final int mMtpReserveSpace;
    private final boolean mAllowMassStorage;
    /** Maximum file size for the storage, or zero for no limit */
    private final long mMaxFileSize;
    /** When set, indicates exclusive ownership of this volume */
    private final UserHandle mOwner;

    // StorageVolume extra for ACTION_MEDIA_REMOVED, ACTION_MEDIA_UNMOUNTED, ACTION_MEDIA_CHECKING,
    // ACTION_MEDIA_NOFS, ACTION_MEDIA_MOUNTED, ACTION_MEDIA_SHARED, ACTION_MEDIA_UNSHARED,
    // ACTION_MEDIA_BAD_REMOVAL, ACTION_MEDIA_UNMOUNTABLE and ACTION_MEDIA_EJECT broadcasts.
    public static final String EXTRA_STORAGE_VOLUME = "storage_volume";

    public StorageVolume(File path, int descriptionId, boolean primary, boolean removable,
            boolean emulated, int mtpReserveSpace, boolean allowMassStorage, long maxFileSize,
            UserHandle owner) {
        mFilesystemId = -1;
        mPath = path;
        mDescriptionId = descriptionId;
        mPrimary = primary;
        mRemovable = removable;
        mEmulated = emulated;
        mMtpReserveSpace = mtpReserveSpace;
        mAllowMassStorage = allowMassStorage;
        mMaxFileSize = maxFileSize;
        mOwner = owner;
    }

    private StorageVolume(Parcel in) {
        mStorageId = in.readInt();
        mFilesystemId = in.readInt();
        mPath = new File(in.readString());
        mDescriptionId = in.readInt();
        mPrimary = in.readInt() != 0;
        mRemovable = in.readInt() != 0;
        mEmulated = in.readInt() != 0;
        mMtpReserveSpace = in.readInt();
        mAllowMassStorage = in.readInt() != 0;
        mMaxFileSize = in.readLong();
        mOwner = in.readParcelable(null);
    }

    public static StorageVolume fromTemplate(StorageVolume template, File path, UserHandle owner) {
        return new StorageVolume(path, template.mDescriptionId, template.mPrimary,
                template.mRemovable, template.mEmulated, template.mMtpReserveSpace,
                template.mAllowMassStorage, template.mMaxFileSize, owner);
    }

    /**
     * Returns the mount path for the volume.
     *
     * @return the mount path
     */
    public String getPath() {
        return mPath.toString();
    }

    public File getPathFile() {
        return mPath;
    }

    /**
     * Returns a user visible description of the volume.
     *
     * @return the volume description
     */
    public String getDescription(Context context) {
        return context.getResources().getString(mDescriptionId);
    }

    public int getDescriptionId() {
        return mDescriptionId;
    }

    public boolean isPrimary() {
        return mPrimary;
    }

    /**
     * Returns true if the volume is removable.
     *
     * @return is removable
     */
    public boolean isRemovable() {
        return mRemovable;
    }

    /**
     * Returns true if the volume is emulated.
     *
     * @return is removable
     */
    public boolean isEmulated() {
        return mEmulated;
    }

    /**
     * Returns the MTP storage ID for the volume.
     * this is also used for the storage_id column in the media provider.
     *
     * @return MTP storage ID
     */
    public int getStorageId() {
        return mStorageId;
    }

    /**
     * Do not call this unless you are MountService
     */
    public void setStorageId(int index) {
        // storage ID is 0x00010001 for primary storage,
        // then 0x00020001, 0x00030001, etc. for secondary storages
        mStorageId = ((index + 1) << 16) + 1;
    }

    public int getFilesystemId() {
        return mFilesystemId;
    }

    public void setFilesystemId(int id) {
        mFilesystemId = id;
    }

    /**
     * Number of megabytes of space to leave unallocated by MTP.
     * MTP will subtract this value from the free space it reports back
     * to the host via GetStorageInfo, and will not allow new files to
     * be added via MTP if there is less than this amount left free in the storage.
     * If MTP has dedicated storage this value should be zero, but if MTP is
     * sharing storage with the rest of the system, set this to a positive value
     * to ensure that MTP activity does not result in the storage being
     * too close to full.
     *
     * @return MTP reserve space
     */
    public int getMtpReserveSpace() {
        return mMtpReserveSpace;
    }

    /**
     * Returns true if this volume can be shared via USB mass storage.
     *
     * @return whether mass storage is allowed
     */
    public boolean allowMassStorage() {
        return mAllowMassStorage;
    }

    /**
     * Returns maximum file size for the volume, or zero if it is unbounded.
     *
     * @return maximum file size
     */
    public long getMaxFileSize() {
        return mMaxFileSize;
    }

    public UserHandle getOwner() {
        return mOwner;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof StorageVolume && mPath != null) {
            StorageVolume volume = (StorageVolume)obj;
            return (mPath.equals(volume.mPath));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mPath.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("StorageVolume [");
        builder.append("mStorageId=").append(mStorageId);
        builder.append("mFilesystemId=").append(mFilesystemId);
        builder.append(" mPath=").append(mPath);
        builder.append(" mDescriptionId=").append(mDescriptionId);
        builder.append(" mPrimary=").append(mPrimary);
        builder.append(" mRemovable=").append(mRemovable);
        builder.append(" mEmulated=").append(mEmulated);
        builder.append(" mMtpReserveSpace=").append(mMtpReserveSpace);
        builder.append(" mAllowMassStorage=").append(mAllowMassStorage);
        builder.append(" mMaxFileSize=").append(mMaxFileSize);
        builder.append(" mOwner=").append(mOwner);
        builder.append("]");
        return builder.toString();
    }

    public static final Creator<StorageVolume> CREATOR = new Creator<StorageVolume>() {
        @Override
        public StorageVolume createFromParcel(Parcel in) {
            return new StorageVolume(in);
        }

        @Override
        public StorageVolume[] newArray(int size) {
            return new StorageVolume[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mStorageId);
        parcel.writeInt(mFilesystemId);
        parcel.writeString(mPath.toString());
        parcel.writeInt(mDescriptionId);
        parcel.writeInt(mPrimary ? 1 : 0);
        parcel.writeInt(mRemovable ? 1 : 0);
        parcel.writeInt(mEmulated ? 1 : 0);
        parcel.writeInt(mMtpReserveSpace);
        parcel.writeInt(mAllowMassStorage ? 1 : 0);
        parcel.writeLong(mMaxFileSize);
        parcel.writeParcelable(mOwner, flags);
    }
}
