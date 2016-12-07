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

package com.android.server.pm;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Process.PACKAGE_INFO_GID;
import static android.os.Process.SYSTEM_UID;

import static com.android.server.pm.PackageManagerService.DEBUG_DOMAIN_VERIFICATION;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.IntentFilterVerificationInfo;
import android.content.pm.PackageCleanItem;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PackageUserState;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.content.pm.VerifierDeviceIdentity;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.os.PatternMatcher;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Log;
import android.util.LogPrinter;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.util.Xml;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.BackgroundThread;
import com.android.internal.os.InstallerConnection.InstallerException;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.JournaledFile;
import com.android.internal.util.XmlUtils;
import com.android.server.backup.PreferredActivityBackupHelper;
import com.android.server.pm.PackageManagerService.DumpState;
import com.android.server.pm.PermissionsState.PermissionState;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * Holds information about dynamic settings.
 */
final class Settings {
    private static final String TAG = "PackageSettings";

    /**
     * Current version of the package database. Set it to the latest version in
     * the {@link DatabaseVersion} class below to ensure the database upgrade
     * doesn't happen repeatedly.
     * <p>
     * Note that care should be taken to make sure all database upgrades are
     * idempotent.
     */
    public static final int CURRENT_DATABASE_VERSION = DatabaseVersion.SIGNATURE_MALFORMED_RECOVER;

    /**
     * This class contains constants that can be referred to from upgrade code.
     * Insert constant values here that describe the upgrade reason. The version
     * code must be monotonically increasing.
     */
    public static class DatabaseVersion {
        /**
         * The initial version of the database.
         */
        public static final int FIRST_VERSION = 1;

        /**
         * Migrating the Signature array from the entire certificate chain to
         * just the signing certificate.
         */
        public static final int SIGNATURE_END_ENTITY = 2;

        /**
         * There was a window of time in
         * {@link android.os.Build.VERSION_CODES#LOLLIPOP} where we persisted
         * certificates after potentially mutating them. To switch back to the
         * original untouched certificates, we need to force a collection pass.
         */
        public static final int SIGNATURE_MALFORMED_RECOVER = 3;
    }

    private static final boolean DEBUG_STOPPED = false;
    private static final boolean DEBUG_MU = false;
    private static final boolean DEBUG_KERNEL = false;

    private static final String RUNTIME_PERMISSIONS_FILE_NAME = "runtime-permissions.xml";

    private static final String TAG_READ_EXTERNAL_STORAGE = "read-external-storage";
    private static final String ATTR_ENFORCEMENT = "enforcement";

    private static final String TAG_ITEM = "item";
    private static final String TAG_DISABLED_COMPONENTS = "disabled-components";
    private static final String TAG_ENABLED_COMPONENTS = "enabled-components";
    private static final String TAG_PACKAGE_RESTRICTIONS = "package-restrictions";
    private static final String TAG_PACKAGE = "pkg";
    private static final String TAG_SHARED_USER = "shared-user";
    private static final String TAG_RUNTIME_PERMISSIONS = "runtime-permissions";
    private static final String TAG_PERMISSIONS = "perms";
    private static final String TAG_CHILD_PACKAGE = "child-package";

    private static final String TAG_PERSISTENT_PREFERRED_ACTIVITIES =
            "persistent-preferred-activities";
    static final String TAG_CROSS_PROFILE_INTENT_FILTERS =
            "crossProfile-intent-filters";
    private static final String TAG_DOMAIN_VERIFICATION = "domain-verification";
    private static final String TAG_DEFAULT_APPS = "default-apps";
    private static final String TAG_ALL_INTENT_FILTER_VERIFICATION =
            "all-intent-filter-verifications";
    private static final String TAG_DEFAULT_BROWSER = "default-browser";
    private static final String TAG_DEFAULT_DIALER = "default-dialer";
    private static final String TAG_VERSION = "version";

    private static final String TAG_PROTECTED_COMPONENTS = "protected-components";
    private static final String TAG_VISIBLE_COMPONENTS = "visible-components";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_USER = "user";
    private static final String ATTR_CODE = "code";
    private static final String ATTR_GRANTED = "granted";
    private static final String ATTR_FLAGS = "flags";

    private static final String ATTR_CE_DATA_INODE = "ceDataInode";
    private static final String ATTR_INSTALLED = "inst";
    private static final String ATTR_STOPPED = "stopped";
    private static final String ATTR_NOT_LAUNCHED = "nl";
    // Legacy, here for reading older versions of the package-restrictions.
    private static final String ATTR_BLOCKED = "blocked";
    // New name for the above attribute.
    private static final String ATTR_HIDDEN = "hidden";
    private static final String ATTR_SUSPENDED = "suspended";
    private static final String ATTR_BLOCK_UNINSTALL = "blockUninstall";
    private static final String ATTR_ENABLED = "enabled";
    private static final String ATTR_ENABLED_CALLER = "enabledCaller";
    private static final String ATTR_DOMAIN_VERIFICATON_STATE = "domainVerificationStatus";
    private static final String ATTR_APP_LINK_GENERATION = "app-link-generation";

    private static final String ATTR_PACKAGE_NAME = "packageName";
    private static final String ATTR_FINGERPRINT = "fingerprint";
    private static final String ATTR_VOLUME_UUID = "volumeUuid";
    private static final String ATTR_SDK_VERSION = "sdkVersion";
    private static final String ATTR_DATABASE_VERSION = "databaseVersion";
    private static final String ATTR_DONE = "done";

    // Bookkeeping for restored permission grants
    private static final String TAG_RESTORED_RUNTIME_PERMISSIONS = "restored-perms";
    // package name: ATTR_PACKAGE_NAME
    private static final String TAG_PERMISSION_ENTRY = "perm";
    // permission name: ATTR_NAME
    // permission granted (boolean): ATTR_GRANTED
    private static final String ATTR_USER_SET = "set";
    private static final String ATTR_USER_FIXED = "fixed";
    private static final String ATTR_REVOKE_ON_UPGRADE = "rou";

    // Flag mask of restored permission grants that are applied at install time
    private static final int USER_RUNTIME_GRANT_MASK =
            FLAG_PERMISSION_USER_SET
            | FLAG_PERMISSION_USER_FIXED
            | FLAG_PERMISSION_REVOKE_ON_UPGRADE;

    private final Object mLock;

    private final RuntimePermissionPersistence mRuntimePermissionsPersistence;

    private final File mSettingsFilename;
    private final File mBackupSettingsFilename;
    private final File mPackageListFilename;
    private final File mStoppedPackagesFilename;
    private final File mBackupStoppedPackagesFilename;
    private final File mKernelMappingFilename;

    /** Map from package name to settings */
    final ArrayMap<String, PackageSetting> mPackages = new ArrayMap<>();

    /** List of packages that installed other packages */
    final ArraySet<String> mInstallerPackages = new ArraySet<>();

    /** Map from package name to appId */
    private final ArrayMap<String, Integer> mKernelMapping = new ArrayMap<>();

    // List of replaced system applications
    private final ArrayMap<String, PackageSetting> mDisabledSysPackages =
        new ArrayMap<String, PackageSetting>();

    // Set of restored intent-filter verification states
    private final ArrayMap<String, IntentFilterVerificationInfo> mRestoredIntentFilterVerifications =
            new ArrayMap<String, IntentFilterVerificationInfo>();

    // Bookkeeping for restored user permission grants
    final class RestoredPermissionGrant {
        String permissionName;
        boolean granted;
        int grantBits;

        RestoredPermissionGrant(String name, boolean isGranted, int theGrantBits) {
            permissionName = name;
            granted = isGranted;
            grantBits = theGrantBits;
        }
    }

    // This would be more compact as a flat array of restored grants or something, but we
    // may have quite a few, especially during early device lifetime, and avoiding all those
    // linear lookups will be important.
    private final SparseArray<ArrayMap<String, ArraySet<RestoredPermissionGrant>>>
            mRestoredUserGrants =
                new SparseArray<ArrayMap<String, ArraySet<RestoredPermissionGrant>>>();

    private static int mFirstAvailableUid = 0;

    /** Map from volume UUID to {@link VersionInfo} */
    private ArrayMap<String, VersionInfo> mVersion = new ArrayMap<>();

    /**
     * Version details for a storage volume that may hold apps.
     */
    public static class VersionInfo {
        /**
         * These are the last platform API version we were using for the apps
         * installed on internal and external storage. It is used to grant newer
         * permissions one time during a system upgrade.
         */
        int sdkVersion;

        /**
         * The current database version for apps on internal storage. This is
         * used to upgrade the format of the packages.xml database not
         * necessarily tied to an SDK version.
         */
        int databaseVersion;

        /**
         * Last known value of {@link Build#FINGERPRINT}. Used to determine when
         * an system update has occurred, meaning we need to clear code caches.
         */
        String fingerprint;

        /**
         * Force all version information to match current system values,
         * typically after resolving any required upgrade steps.
         */
        public void forceCurrent() {
            sdkVersion = Build.VERSION.SDK_INT;
            databaseVersion = CURRENT_DATABASE_VERSION;
            fingerprint = Build.FINGERPRINT;
        }
    }

    Boolean mReadExternalStorageEnforced;

    /** Device identity for the purpose of package verification. */
    private VerifierDeviceIdentity mVerifierDeviceIdentity;

    // The user's preferred activities associated with particular intent
    // filters.
    final SparseArray<PreferredIntentResolver> mPreferredActivities =
            new SparseArray<PreferredIntentResolver>();

    // The persistent preferred activities of the user's profile/device owner
    // associated with particular intent filters.
    final SparseArray<PersistentPreferredIntentResolver> mPersistentPreferredActivities =
            new SparseArray<PersistentPreferredIntentResolver>();

    // For every user, it is used to find to which other users the intent can be forwarded.
    final SparseArray<CrossProfileIntentResolver> mCrossProfileIntentResolvers =
            new SparseArray<CrossProfileIntentResolver>();

    final ArrayMap<String, SharedUserSetting> mSharedUsers =
            new ArrayMap<String, SharedUserSetting>();
    private final ArrayList<Object> mUserIds = new ArrayList<Object>();
    private final SparseArray<Object> mOtherUserIds =
            new SparseArray<Object>();

    // For reading/writing settings file.
    private final ArrayList<Signature> mPastSignatures =
            new ArrayList<Signature>();
    private final ArrayMap<Long, Integer> mKeySetRefs =
            new ArrayMap<Long, Integer>();

    // Mapping from permission names to info about them.
    final ArrayMap<String, BasePermission> mPermissions =
            new ArrayMap<String, BasePermission>();

    // Mapping from permission tree names to info about them.
    final ArrayMap<String, BasePermission> mPermissionTrees =
            new ArrayMap<String, BasePermission>();

    // Packages that have been uninstalled and still need their external
    // storage data deleted.
    final ArrayList<PackageCleanItem> mPackagesToBeCleaned = new ArrayList<PackageCleanItem>();

    // Packages that have been renamed since they were first installed.
    // Keys are the new names of the packages, values are the original
    // names.  The packages appear everwhere else under their original
    // names.
    final ArrayMap<String, String> mRenamedPackages = new ArrayMap<String, String>();

    // For every user, it is used to find the package name of the default Browser App.
    final SparseArray<String> mDefaultBrowserApp = new SparseArray<String>();

    // For every user, a record of the package name of the default Dialer App.
    final SparseArray<String> mDefaultDialerApp = new SparseArray<String>();

    // App-link priority tracking, per-user
    final SparseIntArray mNextAppLinkGeneration = new SparseIntArray();

    final StringBuilder mReadMessages = new StringBuilder();

    /**
     * Used to track packages that have a shared user ID that hasn't been read
     * in yet.
     * <p>
     * TODO: make this just a local variable that is passed in during package
     * scanning to make it less confusing.
     */
    private final ArrayList<PendingPackage> mPendingPackages = new ArrayList<PendingPackage>();

    private final File mSystemDir;

    public final KeySetManagerService mKeySetManagerService = new KeySetManagerService(mPackages);

    Settings(Object lock) {
        this(Environment.getDataDirectory(), lock);
    }

    Settings(File dataDir, Object lock) {
        mLock = lock;

        mRuntimePermissionsPersistence = new RuntimePermissionPersistence(mLock);

        mSystemDir = new File(dataDir, "system");
        mSystemDir.mkdirs();
        FileUtils.setPermissions(mSystemDir.toString(),
                FileUtils.S_IRWXU|FileUtils.S_IRWXG
                |FileUtils.S_IROTH|FileUtils.S_IXOTH,
                -1, -1);
        mSettingsFilename = new File(mSystemDir, "packages.xml");
        mBackupSettingsFilename = new File(mSystemDir, "packages-backup.xml");
        mPackageListFilename = new File(mSystemDir, "packages.list");
        FileUtils.setPermissions(mPackageListFilename, 0640, SYSTEM_UID, PACKAGE_INFO_GID);

        final File kernelDir = new File("/config/sdcardfs");
        mKernelMappingFilename = kernelDir.exists() ? kernelDir : null;

        // Deprecated: Needed for migration
        mStoppedPackagesFilename = new File(mSystemDir, "packages-stopped.xml");
        mBackupStoppedPackagesFilename = new File(mSystemDir, "packages-stopped-backup.xml");
    }

    PackageSetting getPackageLPw(PackageParser.Package pkg, PackageSetting origPackage,
            String realName, SharedUserSetting sharedUser, File codePath, File resourcePath,
            String legacyNativeLibraryPathString, String primaryCpuAbi, String secondaryCpuAbi,
            int pkgFlags, int pkgPrivateFlags, UserHandle user, boolean add) {
        final String name = pkg.packageName;
        final String parentPackageName = (pkg.parentPackage != null)
                ? pkg.parentPackage.packageName : null;

        List<String> childPackageNames = null;
        if (pkg.childPackages != null) {
            final int childCount = pkg.childPackages.size();
            childPackageNames = new ArrayList<>(childCount);
            for (int i = 0; i < childCount; i++) {
                String childPackageName = pkg.childPackages.get(i).packageName;
                childPackageNames.add(childPackageName);
            }
        }

        PackageSetting p = getPackageLPw(name, origPackage, realName, sharedUser, codePath,
                resourcePath, legacyNativeLibraryPathString, primaryCpuAbi, secondaryCpuAbi,
                pkg.mVersionCode, pkgFlags, pkgPrivateFlags, user, add, true /* allowInstall */,
                parentPackageName, childPackageNames);
        return p;
    }

    PackageSetting peekPackageLPr(String name) {
        return mPackages.get(name);
    }

    void setInstallStatus(String pkgName, final int status) {
        PackageSetting p = mPackages.get(pkgName);
        if(p != null) {
            if(p.getInstallStatus() != status) {
                p.setInstallStatus(status);
            }
        }
    }

    void applyPendingPermissionGrantsLPw(String packageName, int userId) {
        ArrayMap<String, ArraySet<RestoredPermissionGrant>> grantsByPackage =
                mRestoredUserGrants.get(userId);
        if (grantsByPackage == null || grantsByPackage.size() == 0) {
            return;
        }

        ArraySet<RestoredPermissionGrant> grants = grantsByPackage.get(packageName);
        if (grants == null || grants.size() == 0) {
            return;
        }

        final PackageSetting ps = mPackages.get(packageName);
        if (ps == null) {
            Slog.e(TAG, "Can't find supposedly installed package " + packageName);
            return;
        }
        final PermissionsState perms = ps.getPermissionsState();

        for (RestoredPermissionGrant grant : grants) {
            BasePermission bp = mPermissions.get(grant.permissionName);
            if (bp != null) {
                if (grant.granted) {
                    perms.grantRuntimePermission(bp, userId);
                }
                perms.updatePermissionFlags(bp, userId, USER_RUNTIME_GRANT_MASK, grant.grantBits);
            }
        }

        // And remove it from the pending-grant bookkeeping
        grantsByPackage.remove(packageName);
        if (grantsByPackage.size() < 1) {
            mRestoredUserGrants.remove(userId);
        }
        writeRuntimePermissionsForUserLPr(userId, false);
    }

    void setInstallerPackageName(String pkgName, String installerPkgName) {
        PackageSetting p = mPackages.get(pkgName);
        if (p != null) {
            p.setInstallerPackageName(installerPkgName);
            if (installerPkgName != null) {
                mInstallerPackages.add(installerPkgName);
            }
        }
    }

    SharedUserSetting getSharedUserLPw(String name,
            int pkgFlags, int pkgPrivateFlags, boolean create) {
        SharedUserSetting s = mSharedUsers.get(name);
        if (s == null) {
            if (!create) {
                return null;
            }
            s = new SharedUserSetting(name, pkgFlags, pkgPrivateFlags);
            s.userId = newUserIdLPw(s);
            Log.i(PackageManagerService.TAG, "New shared user " + name + ": id=" + s.userId);
            // < 0 means we couldn't assign a userid; fall out and return
            // s, which is currently null
            if (s.userId >= 0) {
                mSharedUsers.put(name, s);
            }
        }

        return s;
    }

    Collection<SharedUserSetting> getAllSharedUsersLPw() {
        return mSharedUsers.values();
    }

    boolean disableSystemPackageLPw(String name, boolean replaced) {
        final PackageSetting p = mPackages.get(name);
        if(p == null) {
            Log.w(PackageManagerService.TAG, "Package " + name + " is not an installed package");
            return false;
        }
        final PackageSetting dp = mDisabledSysPackages.get(name);
        // always make sure the system package code and resource paths dont change
        if (dp == null && p.pkg != null && p.pkg.isSystemApp() && !p.pkg.isUpdatedSystemApp()) {
            if((p.pkg != null) && (p.pkg.applicationInfo != null)) {
                p.pkg.applicationInfo.flags |= ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            }
            mDisabledSysPackages.put(name, p);

            if (replaced) {
                // a little trick...  when we install the new package, we don't
                // want to modify the existing PackageSetting for the built-in
                // version.  so at this point we need a new PackageSetting that
                // is okay to muck with.
                PackageSetting newp = new PackageSetting(p);
                replacePackageLPw(name, newp);
            }
            return true;
        }
        return false;
    }

    PackageSetting enableSystemPackageLPw(String name) {
        PackageSetting p = mDisabledSysPackages.get(name);
        if(p == null) {
            Log.w(PackageManagerService.TAG, "Package " + name + " is not disabled");
            return null;
        }
        // Reset flag in ApplicationInfo object
        if((p.pkg != null) && (p.pkg.applicationInfo != null)) {
            p.pkg.applicationInfo.flags &= ~ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        }
        PackageSetting ret = addPackageLPw(name, p.realName, p.codePath, p.resourcePath,
                p.legacyNativeLibraryPathString, p.primaryCpuAbiString,
                p.secondaryCpuAbiString, p.cpuAbiOverrideString,
                p.appId, p.versionCode, p.pkgFlags, p.pkgPrivateFlags,
                p.parentPackageName, p.childPackageNames);
        mDisabledSysPackages.remove(name);
        return ret;
    }

    boolean isDisabledSystemPackageLPr(String name) {
        return mDisabledSysPackages.containsKey(name);
    }

    void removeDisabledSystemPackageLPw(String name) {
        mDisabledSysPackages.remove(name);
    }

    PackageSetting addPackageLPw(String name, String realName, File codePath, File resourcePath,
            String legacyNativeLibraryPathString, String primaryCpuAbiString,
            String secondaryCpuAbiString, String cpuAbiOverrideString, int uid, int vc, int
            pkgFlags, int pkgPrivateFlags, String parentPackageName,
            List<String> childPackageNames) {
        PackageSetting p = mPackages.get(name);
        if (p != null) {
            if (p.appId == uid) {
                return p;
            }
            PackageManagerService.reportSettingsProblem(Log.ERROR,
                    "Adding duplicate package, keeping first: " + name);
            return null;
        }
        p = new PackageSetting(name, realName, codePath, resourcePath,
                legacyNativeLibraryPathString, primaryCpuAbiString, secondaryCpuAbiString,
                cpuAbiOverrideString, vc, pkgFlags, pkgPrivateFlags, parentPackageName,
                childPackageNames);
        p.appId = uid;
        if (addUserIdLPw(uid, p, name)) {
            mPackages.put(name, p);
            return p;
        }
        return null;
    }

    SharedUserSetting addSharedUserLPw(String name, int uid, int pkgFlags, int pkgPrivateFlags) {
        SharedUserSetting s = mSharedUsers.get(name);
        if (s != null) {
            if (s.userId == uid) {
                return s;
            }
            PackageManagerService.reportSettingsProblem(Log.ERROR,
                    "Adding duplicate shared user, keeping first: " + name);
            return null;
        }
        s = new SharedUserSetting(name, pkgFlags, pkgPrivateFlags);
        s.userId = uid;
        if (addUserIdLPw(uid, s, name)) {
            mSharedUsers.put(name, s);
            return s;
        }
        return null;
    }

    void pruneSharedUsersLPw() {
        ArrayList<String> removeStage = new ArrayList<String>();
        for (Map.Entry<String,SharedUserSetting> entry : mSharedUsers.entrySet()) {
            final SharedUserSetting sus = entry.getValue();
            if (sus == null) {
                removeStage.add(entry.getKey());
                continue;
            }
            // remove packages that are no longer installed
            for (Iterator<PackageSetting> iter = sus.packages.iterator(); iter.hasNext();) {
                PackageSetting ps = iter.next();
                if (mPackages.get(ps.name) == null) {
                    iter.remove();
                }
            }
            if (sus.packages.size() == 0) {
                removeStage.add(entry.getKey());
            }
        }
        for (int i = 0; i < removeStage.size(); i++) {
            mSharedUsers.remove(removeStage.get(i));
        }
    }

    // Transfer ownership of permissions from one package to another.
    void transferPermissionsLPw(String origPkg, String newPkg) {
        // Transfer ownership of permissions to the new package.
        for (int i=0; i<2; i++) {
            ArrayMap<String, BasePermission> permissions =
                    i == 0 ? mPermissionTrees : mPermissions;
            for (BasePermission bp : permissions.values()) {
                if (origPkg.equals(bp.sourcePackage)) {
                    if (PackageManagerService.DEBUG_UPGRADE) Log.v(PackageManagerService.TAG,
                            "Moving permission " + bp.name
                            + " from pkg " + bp.sourcePackage
                            + " to " + newPkg);
                    bp.sourcePackage = newPkg;
                    bp.packageSetting = null;
                    bp.perm = null;
                    if (bp.pendingInfo != null) {
                        bp.pendingInfo.packageName = newPkg;
                    }
                    bp.uid = 0;
                    bp.setGids(null, false);
                }
            }
        }
    }

    private PackageSetting getPackageLPw(String name, PackageSetting origPackage,
            String realName, SharedUserSetting sharedUser, File codePath, File resourcePath,
            String legacyNativeLibraryPathString, String primaryCpuAbiString,
            String secondaryCpuAbiString, int vc, int pkgFlags, int pkgPrivateFlags,
            UserHandle installUser, boolean add, boolean allowInstall, String parentPackage,
            List<String> childPackageNames) {
        PackageSetting p = mPackages.get(name);
        UserManagerService userManager = UserManagerService.getInstance();
        if (p != null) {
            p.primaryCpuAbiString = primaryCpuAbiString;
            p.secondaryCpuAbiString = secondaryCpuAbiString;
            if (childPackageNames != null) {
                p.childPackageNames = new ArrayList<>(childPackageNames);
            }

            if (!p.codePath.equals(codePath)) {
                // Check to see if its a disabled system app
                if ((p.pkgFlags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // This is an updated system app with versions in both system
                    // and data partition. Just let the most recent version
                    // take precedence.
                    Slog.w(PackageManagerService.TAG, "Trying to update system app code path from "
                            + p.codePathString + " to " + codePath.toString());
                } else {
                    // Just a change in the code path is not an issue, but
                    // let's log a message about it.
                    Slog.i(PackageManagerService.TAG, "Package " + name + " codePath changed from "
                            + p.codePath + " to " + codePath + "; Retaining data and using new");

                    // The owner user's installed flag is set false
                    // when the application was installed by other user
                    // and the installed flag is not updated
                    // when the application is appended as system app later.
                    if ((pkgFlags & ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            getDisabledSystemPkgLPr(name) == null) {
                        List<UserInfo> allUserInfos = getAllUsers();
                        if (allUserInfos != null) {
                            for (UserInfo userInfo : allUserInfos) {
                                p.setInstalled(true, userInfo.id);
                            }
                        }
                    }

                    /*
                     * Since we've changed paths, we need to prefer the new
                     * native library path over the one stored in the
                     * package settings since we might have moved from
                     * internal to external storage or vice versa.
                     */
                    p.legacyNativeLibraryPathString = legacyNativeLibraryPathString;
                }
            }
            if (p.sharedUser != sharedUser) {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Package " + name + " shared user changed from "
                        + (p.sharedUser != null ? p.sharedUser.name : "<nothing>")
                        + " to "
                        + (sharedUser != null ? sharedUser.name : "<nothing>")
                        + "; replacing with new");
                p = null;
            } else {
                // If what we are scanning is a system (and possibly privileged) package,
                // then make it so, regardless of whether it was previously installed only
                // in the data partition.
                p.pkgFlags |= pkgFlags & ApplicationInfo.FLAG_SYSTEM;
                p.pkgPrivateFlags |= pkgPrivateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
            }
        }
        if (p == null) {
            if (origPackage != null) {
                // We are consuming the data from an existing package.
                p = new PackageSetting(origPackage.name, name, codePath, resourcePath,
                        legacyNativeLibraryPathString, primaryCpuAbiString, secondaryCpuAbiString,
                        null /* cpuAbiOverrideString */, vc, pkgFlags, pkgPrivateFlags,
                        parentPackage, childPackageNames);
                if (PackageManagerService.DEBUG_UPGRADE) Log.v(PackageManagerService.TAG, "Package "
                        + name + " is adopting original package " + origPackage.name);
                // Note that we will retain the new package's signature so
                // that we can keep its data.
                PackageSignatures s = p.signatures;
                p.copyFrom(origPackage);
                p.signatures = s;
                p.sharedUser = origPackage.sharedUser;
                p.appId = origPackage.appId;
                p.origPackage = origPackage;
                p.getPermissionsState().copyFrom(origPackage.getPermissionsState());
                mRenamedPackages.put(name, origPackage.name);
                name = origPackage.name;
                // Update new package state.
                p.setTimeStamp(codePath.lastModified());
            } else {
                p = new PackageSetting(name, realName, codePath, resourcePath,
                        legacyNativeLibraryPathString, primaryCpuAbiString, secondaryCpuAbiString,
                        null /* cpuAbiOverrideString */, vc, pkgFlags, pkgPrivateFlags,
                        parentPackage, childPackageNames);
                p.setTimeStamp(codePath.lastModified());
                p.sharedUser = sharedUser;
                // If this is not a system app, it starts out stopped.
                if ((pkgFlags&ApplicationInfo.FLAG_SYSTEM) == 0) {
                    if (DEBUG_STOPPED) {
                        RuntimeException e = new RuntimeException("here");
                        e.fillInStackTrace();
                        Slog.i(PackageManagerService.TAG, "Stopping package " + name, e);
                    }
                    List<UserInfo> users = getAllUsers();
                    final int installUserId = installUser != null ? installUser.getIdentifier() : 0;
                    if (users != null && allowInstall) {
                        for (UserInfo user : users) {
                            // By default we consider this app to be installed
                            // for the user if no user has been specified (which
                            // means to leave it at its original value, and the
                            // original default value is true), or we are being
                            // asked to install for all users, or this is the
                            // user we are installing for.
                            final boolean installed = installUser == null
                                    || (installUserId == UserHandle.USER_ALL
                                        && !isAdbInstallDisallowed(userManager, user.id))
                                    || installUserId == user.id;
                            p.setUserState(user.id, 0, COMPONENT_ENABLED_STATE_DEFAULT,
                                    installed,
                                    true, // stopped,
                                    true, // notLaunched
                                    false, // hidden
                                    false, // suspended
                                    null, null, null,
                                    false, // blockUninstall
                                    INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED, 0,
                                    null,
                                    null
                                    );
                            writePackageRestrictionsLPr(user.id);
                        }
                    }
                }
                if (sharedUser != null) {
                    p.appId = sharedUser.userId;
                } else {
                    // Clone the setting here for disabled system packages
                    PackageSetting dis = mDisabledSysPackages.get(name);
                    if (dis != null) {
                        // For disabled packages a new setting is created
                        // from the existing user id. This still has to be
                        // added to list of user id's
                        // Copy signatures from previous setting
                        if (dis.signatures.mSignatures != null) {
                            p.signatures.mSignatures = dis.signatures.mSignatures.clone();
                        }
                        p.appId = dis.appId;
                        // Clone permissions
                        p.getPermissionsState().copyFrom(dis.getPermissionsState());
                        // Clone component info
                        List<UserInfo> users = getAllUsers();
                        if (users != null) {
                            for (UserInfo user : users) {
                                int userId = user.id;
                                p.setDisabledComponentsCopy(
                                        dis.getDisabledComponents(userId), userId);
                                p.setEnabledComponentsCopy(
                                        dis.getEnabledComponents(userId), userId);
                            }
                        }
                        // Add new setting to list of user ids
                        addUserIdLPw(p.appId, p, name);
                    } else {
                        // Assign new user id
                        p.appId = newUserIdLPw(p);
                    }
                }
            }
            if (p.appId < 0) {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Package " + name + " could not be assigned a valid uid");
                return null;
            }
            if (add) {
                // Finish adding new package by adding it and updating shared
                // user preferences
                addPackageSettingLPw(p, name, sharedUser);
            }
        } else {
            if (installUser != null && allowInstall) {
                // The caller has explicitly specified the user they want this
                // package installed for, and the package already exists.
                // Make sure it conforms to the new request.
                List<UserInfo> users = getAllUsers();
                if (users != null) {
                    for (UserInfo user : users) {
                        if ((installUser.getIdentifier() == UserHandle.USER_ALL
                                    && !isAdbInstallDisallowed(userManager, user.id))
                                || installUser.getIdentifier() == user.id) {
                            boolean installed = p.getInstalled(user.id);
                            if (!installed) {
                                p.setInstalled(true, user.id);
                                writePackageRestrictionsLPr(user.id);
                            }
                        }
                    }
                }
            }
        }
        return p;
    }

    boolean isAdbInstallDisallowed(UserManagerService userManager, int userId) {
        return userManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES,
                userId);
    }

    void insertPackageSettingLPw(PackageSetting p, PackageParser.Package pkg) {
        p.pkg = pkg;
        // pkg.mSetEnabled = p.getEnabled(userId);
        // pkg.mSetStopped = p.getStopped(userId);
        final String volumeUuid = pkg.applicationInfo.volumeUuid;
        final String codePath = pkg.applicationInfo.getCodePath();
        final String resourcePath = pkg.applicationInfo.getResourcePath();
        final String legacyNativeLibraryPath = pkg.applicationInfo.nativeLibraryRootDir;
        // Update volume if needed
        if (!Objects.equals(volumeUuid, p.volumeUuid)) {
            Slog.w(PackageManagerService.TAG, "Volume for " + p.pkg.packageName +
                    " changing from " + p.volumeUuid + " to " + volumeUuid);
            p.volumeUuid = volumeUuid;
        }
        // Update code path if needed
        if (!Objects.equals(codePath, p.codePathString)) {
            Slog.w(PackageManagerService.TAG, "Code path for " + p.pkg.packageName +
                    " changing from " + p.codePathString + " to " + codePath);
            p.codePath = new File(codePath);
            p.codePathString = codePath;
        }
        //Update resource path if needed
        if (!Objects.equals(resourcePath, p.resourcePathString)) {
            Slog.w(PackageManagerService.TAG, "Resource path for " + p.pkg.packageName +
                    " changing from " + p.resourcePathString + " to " + resourcePath);
            p.resourcePath = new File(resourcePath);
            p.resourcePathString = resourcePath;
        }
        // Update the native library paths if needed
        if (!Objects.equals(legacyNativeLibraryPath, p.legacyNativeLibraryPathString)) {
            p.legacyNativeLibraryPathString = legacyNativeLibraryPath;
        }

        // Update the required Cpu Abi
        p.primaryCpuAbiString = pkg.applicationInfo.primaryCpuAbi;
        p.secondaryCpuAbiString = pkg.applicationInfo.secondaryCpuAbi;
        p.cpuAbiOverrideString = pkg.cpuAbiOverride;
        // Update version code if needed
        if (pkg.mVersionCode != p.versionCode) {
            p.versionCode = pkg.mVersionCode;
        }
        // Update signatures if needed.
        if (p.signatures.mSignatures == null) {
            p.signatures.assignSignatures(pkg.mSignatures);
        }
        // Update flags if needed.
        if (pkg.applicationInfo.flags != p.pkgFlags) {
            p.pkgFlags = pkg.applicationInfo.flags;
        }
        // If this app defines a shared user id initialize
        // the shared user signatures as well.
        if (p.sharedUser != null && p.sharedUser.signatures.mSignatures == null) {
            p.sharedUser.signatures.assignSignatures(pkg.mSignatures);
        }
        addPackageSettingLPw(p, pkg.packageName, p.sharedUser);
    }

    // Utility method that adds a PackageSetting to mPackages and
    // completes updating the shared user attributes and any restored
    // app link verification state
    private void addPackageSettingLPw(PackageSetting p, String name,
            SharedUserSetting sharedUser) {
        mPackages.put(name, p);
        if (sharedUser != null) {
            if (p.sharedUser != null && p.sharedUser != sharedUser) {
                PackageManagerService.reportSettingsProblem(Log.ERROR,
                        "Package " + p.name + " was user "
                        + p.sharedUser + " but is now " + sharedUser
                        + "; I am not changing its files so it will probably fail!");
                p.sharedUser.removePackage(p);
            } else if (p.appId != sharedUser.userId) {
                PackageManagerService.reportSettingsProblem(Log.ERROR,
                    "Package " + p.name + " was user id " + p.appId
                    + " but is now user " + sharedUser
                    + " with id " + sharedUser.userId
                    + "; I am not changing its files so it will probably fail!");
            }

            sharedUser.addPackage(p);
            p.sharedUser = sharedUser;
            p.appId = sharedUser.userId;
        }

        // If the we know about this user id, we have to update it as it
        // has to point to the same PackageSetting instance as the package.
        Object userIdPs = getUserIdLPr(p.appId);
        if (sharedUser == null) {
            if (userIdPs != null && userIdPs != p) {
                replaceUserIdLPw(p.appId, p);
            }
        } else {
            if (userIdPs != null && userIdPs != sharedUser) {
                replaceUserIdLPw(p.appId, sharedUser);
            }
        }

        IntentFilterVerificationInfo ivi = mRestoredIntentFilterVerifications.get(name);
        if (ivi != null) {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.i(TAG, "Applying restored IVI for " + name + " : " + ivi.getStatusString());
            }
            mRestoredIntentFilterVerifications.remove(name);
            p.setIntentFilterVerificationInfo(ivi);
        }
    }

    /*
     * Update the shared user setting when a package using
     * specifying the shared user id is removed. The gids
     * associated with each permission of the deleted package
     * are removed from the shared user's gid list only if its
     * not in use by other permissions of packages in the
     * shared user setting.
     */
    int updateSharedUserPermsLPw(PackageSetting deletedPs, int userId) {
        if ((deletedPs == null) || (deletedPs.pkg == null)) {
            Slog.i(PackageManagerService.TAG,
                    "Trying to update info for null package. Just ignoring");
            return UserHandle.USER_NULL;
        }

        // No sharedUserId
        if (deletedPs.sharedUser == null) {
            return UserHandle.USER_NULL;
        }

        SharedUserSetting sus = deletedPs.sharedUser;

        // Update permissions
        for (String eachPerm : deletedPs.pkg.requestedPermissions) {
            BasePermission bp = mPermissions.get(eachPerm);
            if (bp == null) {
                continue;
            }

            // Check if another package in the shared user needs the permission.
            boolean used = false;
            for (PackageSetting pkg : sus.packages) {
                if (pkg.pkg != null
                        && !pkg.pkg.packageName.equals(deletedPs.pkg.packageName)
                        && pkg.pkg.requestedPermissions.contains(eachPerm)) {
                    used = true;
                    break;
                }
            }
            if (used) {
                continue;
            }

            PermissionsState permissionsState = sus.getPermissionsState();
            PackageSetting disabledPs = getDisabledSystemPkgLPr(deletedPs.pkg.packageName);

            // If the package is shadowing is a disabled system package,
            // do not drop permissions that the shadowed package requests.
            if (disabledPs != null) {
                boolean reqByDisabledSysPkg = false;
                for (String permission : disabledPs.pkg.requestedPermissions) {
                    if (permission.equals(eachPerm)) {
                        reqByDisabledSysPkg = true;
                        break;
                    }
                }
                if (reqByDisabledSysPkg) {
                    continue;
                }
            }

            // Try to revoke as an install permission which is for all users.
            // The package is gone - no need to keep flags for applying policy.
            permissionsState.updatePermissionFlags(bp, userId,
                    PackageManager.MASK_PERMISSION_FLAGS, 0);

            if (permissionsState.revokeInstallPermission(bp) ==
                    PermissionsState.PERMISSION_OPERATION_SUCCESS_GIDS_CHANGED) {
                return UserHandle.USER_ALL;
            }

            // Try to revoke as an install permission which is per user.
            if (permissionsState.revokeRuntimePermission(bp, userId) ==
                    PermissionsState.PERMISSION_OPERATION_SUCCESS_GIDS_CHANGED) {
                return userId;
            }
        }

        return UserHandle.USER_NULL;
    }

    int removePackageLPw(String name) {
        final PackageSetting p = mPackages.get(name);
        if (p != null) {
            mPackages.remove(name);
            removeInstallerPackageStatus(name);
            if (p.sharedUser != null) {
                p.sharedUser.removePackage(p);
                if (p.sharedUser.packages.size() == 0) {
                    mSharedUsers.remove(p.sharedUser.name);
                    removeUserIdLPw(p.sharedUser.userId);
                    return p.sharedUser.userId;
                }
            } else {
                removeUserIdLPw(p.appId);
                return p.appId;
            }
        }
        return -1;
    }

    /**
     * Checks if {@param packageName} is an installer package and if so, clear the installer
     * package name of the packages that are installed by this.
     */
    private void removeInstallerPackageStatus(String packageName) {
        // Check if the package to be removed is an installer package.
        if (!mInstallerPackages.contains(packageName)) {
            return;
        }
        for (int i = 0; i < mPackages.size(); i++) {
            final PackageSetting ps = mPackages.valueAt(i);
            final String installerPackageName = ps.getInstallerPackageName();
            if (installerPackageName != null
                    && installerPackageName.equals(packageName)) {
                ps.setInstallerPackageName(null);
                ps.isOrphaned = true;
            }
        }
        mInstallerPackages.remove(packageName);
    }

    private void replacePackageLPw(String name, PackageSetting newp) {
        final PackageSetting p = mPackages.get(name);
        if (p != null) {
            if (p.sharedUser != null) {
                p.sharedUser.removePackage(p);
                p.sharedUser.addPackage(newp);
            } else {
                replaceUserIdLPw(p.appId, newp);
            }
        }
        mPackages.put(name, newp);
    }

    private boolean addUserIdLPw(int uid, Object obj, Object name) {
        if (uid > Process.LAST_APPLICATION_UID) {
            return false;
        }

        if (uid >= Process.FIRST_APPLICATION_UID) {
            int N = mUserIds.size();
            final int index = uid - Process.FIRST_APPLICATION_UID;
            while (index >= N) {
                mUserIds.add(null);
                N++;
            }
            if (mUserIds.get(index) != null) {
                PackageManagerService.reportSettingsProblem(Log.ERROR,
                        "Adding duplicate user id: " + uid
                        + " name=" + name);
                return false;
            }
            mUserIds.set(index, obj);
        } else {
            if (mOtherUserIds.get(uid) != null) {
                PackageManagerService.reportSettingsProblem(Log.ERROR,
                        "Adding duplicate shared id: " + uid
                                + " name=" + name);
                return false;
            }
            mOtherUserIds.put(uid, obj);
        }
        return true;
    }

    public Object getUserIdLPr(int uid) {
        if (uid >= Process.FIRST_APPLICATION_UID) {
            final int N = mUserIds.size();
            final int index = uid - Process.FIRST_APPLICATION_UID;
            return index < N ? mUserIds.get(index) : null;
        } else {
            return mOtherUserIds.get(uid);
        }
    }

    private void removeUserIdLPw(int uid) {
        if (uid >= Process.FIRST_APPLICATION_UID) {
            final int N = mUserIds.size();
            final int index = uid - Process.FIRST_APPLICATION_UID;
            if (index < N) mUserIds.set(index, null);
        } else {
            mOtherUserIds.remove(uid);
        }
        setFirstAvailableUid(uid+1);
    }

    private void replaceUserIdLPw(int uid, Object obj) {
        if (uid >= Process.FIRST_APPLICATION_UID) {
            final int N = mUserIds.size();
            final int index = uid - Process.FIRST_APPLICATION_UID;
            if (index < N) mUserIds.set(index, obj);
        } else {
            mOtherUserIds.put(uid, obj);
        }
    }

    PreferredIntentResolver editPreferredActivitiesLPw(int userId) {
        PreferredIntentResolver pir = mPreferredActivities.get(userId);
        if (pir == null) {
            pir = new PreferredIntentResolver();
            mPreferredActivities.put(userId, pir);
        }
        return pir;
    }

    PersistentPreferredIntentResolver editPersistentPreferredActivitiesLPw(int userId) {
        PersistentPreferredIntentResolver ppir = mPersistentPreferredActivities.get(userId);
        if (ppir == null) {
            ppir = new PersistentPreferredIntentResolver();
            mPersistentPreferredActivities.put(userId, ppir);
        }
        return ppir;
    }

    CrossProfileIntentResolver editCrossProfileIntentResolverLPw(int userId) {
        CrossProfileIntentResolver cpir = mCrossProfileIntentResolvers.get(userId);
        if (cpir == null) {
            cpir = new CrossProfileIntentResolver();
            mCrossProfileIntentResolvers.put(userId, cpir);
        }
        return cpir;
    }

    /**
     * The following functions suppose that you have a lock for managing access to the
     * mIntentFiltersVerifications map.
     */

    /* package protected */
    IntentFilterVerificationInfo getIntentFilterVerificationLPr(String packageName) {
        PackageSetting ps = mPackages.get(packageName);
        if (ps == null) {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.w(PackageManagerService.TAG, "No package known: " + packageName);
            }
            return null;
        }
        return ps.getIntentFilterVerificationInfo();
    }

    /* package protected */
    IntentFilterVerificationInfo createIntentFilterVerificationIfNeededLPw(String packageName,
            ArrayList<String> domains) {
        PackageSetting ps = mPackages.get(packageName);
        if (ps == null) {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.w(PackageManagerService.TAG, "No package known: " + packageName);
            }
            return null;
        }
        IntentFilterVerificationInfo ivi = ps.getIntentFilterVerificationInfo();
        if (ivi == null) {
            ivi = new IntentFilterVerificationInfo(packageName, domains);
            ps.setIntentFilterVerificationInfo(ivi);
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.d(PackageManagerService.TAG,
                        "Creating new IntentFilterVerificationInfo for pkg: " + packageName);
            }
        } else {
            ivi.setDomains(domains);
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.d(PackageManagerService.TAG,
                        "Setting domains to existing IntentFilterVerificationInfo for pkg: " +
                                packageName + " and with domains: " + ivi.getDomainsString());
            }
        }
        return ivi;
    }

    int getIntentFilterVerificationStatusLPr(String packageName, int userId) {
        PackageSetting ps = mPackages.get(packageName);
        if (ps == null) {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.w(PackageManagerService.TAG, "No package known: " + packageName);
            }
            return INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
        }
        return (int)(ps.getDomainVerificationStatusForUser(userId) >> 32);
    }

    boolean updateIntentFilterVerificationStatusLPw(String packageName, final int status, int userId) {
        // Update the status for the current package
        PackageSetting current = mPackages.get(packageName);
        if (current == null) {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.w(PackageManagerService.TAG, "No package known: " + packageName);
            }
            return false;
        }

        final int alwaysGeneration;
        if (status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS) {
            alwaysGeneration = mNextAppLinkGeneration.get(userId) + 1;
            mNextAppLinkGeneration.put(userId, alwaysGeneration);
        } else {
            alwaysGeneration = 0;
        }

        current.setDomainVerificationStatusForUser(status, alwaysGeneration, userId);
        return true;
    }

    /**
     * Used for Settings App and PackageManagerService dump. Should be read only.
     */
    List<IntentFilterVerificationInfo> getIntentFilterVerificationsLPr(
            String packageName) {
        if (packageName == null) {
            return Collections.<IntentFilterVerificationInfo>emptyList();
        }
        ArrayList<IntentFilterVerificationInfo> result = new ArrayList<>();
        for (PackageSetting ps : mPackages.values()) {
            IntentFilterVerificationInfo ivi = ps.getIntentFilterVerificationInfo();
            if (ivi == null || TextUtils.isEmpty(ivi.getPackageName()) ||
                    !ivi.getPackageName().equalsIgnoreCase(packageName)) {
                continue;
            }
            result.add(ivi);
        }
        return result;
    }

    boolean removeIntentFilterVerificationLPw(String packageName, int userId) {
        PackageSetting ps = mPackages.get(packageName);
        if (ps == null) {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.w(PackageManagerService.TAG, "No package known: " + packageName);
            }
            return false;
        }
        ps.clearDomainVerificationStatusForUser(userId);
        return true;
    }

    boolean removeIntentFilterVerificationLPw(String packageName, int[] userIds) {
        boolean result = false;
        for (int userId : userIds) {
            result |= removeIntentFilterVerificationLPw(packageName, userId);
        }
        return result;
    }

    boolean setDefaultBrowserPackageNameLPw(String packageName, int userId) {
        if (userId == UserHandle.USER_ALL) {
            return false;
        }
        mDefaultBrowserApp.put(userId, packageName);
        writePackageRestrictionsLPr(userId);
        return true;
    }

    String getDefaultBrowserPackageNameLPw(int userId) {
        return (userId == UserHandle.USER_ALL) ? null : mDefaultBrowserApp.get(userId);
    }

    boolean setDefaultDialerPackageNameLPw(String packageName, int userId) {
        if (userId == UserHandle.USER_ALL) {
            return false;
        }
        mDefaultDialerApp.put(userId, packageName);
        writePackageRestrictionsLPr(userId);
        return true;
    }

    String getDefaultDialerPackageNameLPw(int userId) {
        return (userId == UserHandle.USER_ALL) ? null : mDefaultDialerApp.get(userId);
    }

    private File getUserPackagesStateFile(int userId) {
        // TODO: Implement a cleaner solution when adding tests.
        // This instead of Environment.getUserSystemDirectory(userId) to support testing.
        File userDir = new File(new File(mSystemDir, "users"), Integer.toString(userId));
        return new File(userDir, "package-restrictions.xml");
    }

    private File getUserRuntimePermissionsFile(int userId) {
        // TODO: Implement a cleaner solution when adding tests.
        // This instead of Environment.getUserSystemDirectory(userId) to support testing.
        File userDir = new File(new File(mSystemDir, "users"), Integer.toString(userId));
        return new File(userDir, RUNTIME_PERMISSIONS_FILE_NAME);
    }

    private File getUserPackagesStateBackupFile(int userId) {
        return new File(Environment.getUserSystemDirectory(userId),
                "package-restrictions-backup.xml");
    }

    void writeAllUsersPackageRestrictionsLPr() {
        List<UserInfo> users = getAllUsers();
        if (users == null) return;

        for (UserInfo user : users) {
            writePackageRestrictionsLPr(user.id);
        }
    }

    void writeAllRuntimePermissionsLPr() {
        for (int userId : UserManagerService.getInstance().getUserIds()) {
            mRuntimePermissionsPersistence.writePermissionsForUserAsyncLPr(userId);
        }
    }

    boolean areDefaultRuntimePermissionsGrantedLPr(int userId) {
        return mRuntimePermissionsPersistence
                .areDefaultRuntimPermissionsGrantedLPr(userId);
    }

    void onDefaultRuntimePermissionsGrantedLPr(int userId) {
        mRuntimePermissionsPersistence
                .onDefaultRuntimePermissionsGrantedLPr(userId);
    }

    public VersionInfo findOrCreateVersion(String volumeUuid) {
        VersionInfo ver = mVersion.get(volumeUuid);
        if (ver == null) {
            ver = new VersionInfo();
            ver.forceCurrent();
            mVersion.put(volumeUuid, ver);
        }
        return ver;
    }

    public VersionInfo getInternalVersion() {
        return mVersion.get(StorageManager.UUID_PRIVATE_INTERNAL);
    }

    public VersionInfo getExternalVersion() {
        return mVersion.get(StorageManager.UUID_PRIMARY_PHYSICAL);
    }

    public void onVolumeForgotten(String fsUuid) {
        mVersion.remove(fsUuid);
    }

    /**
     * Applies the preferred activity state described by the given XML.  This code
     * also supports the restore-from-backup code path.
     *
     * @see PreferredActivityBackupHelper
     */
    void readPreferredActivitiesLPw(XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                PreferredActivity pa = new PreferredActivity(parser);
                if (pa.mPref.getParseError() == null) {
                    editPreferredActivitiesLPw(userId).addFilter(pa);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Error in package manager settings: <preferred-activity> "
                                    + pa.mPref.getParseError() + " at "
                                    + parser.getPositionDescription());
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <preferred-activities>: " + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private void readPersistentPreferredActivitiesLPw(XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                PersistentPreferredActivity ppa = new PersistentPreferredActivity(parser);
                editPersistentPreferredActivitiesLPw(userId).addFilter(ppa);
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <" + TAG_PERSISTENT_PREFERRED_ACTIVITIES + ">: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private void readCrossProfileIntentFiltersLPw(XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            final String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                CrossProfileIntentFilter cpif = new CrossProfileIntentFilter(parser);
                editCrossProfileIntentResolverLPw(userId).addFilter(cpif);
            } else {
                String msg = "Unknown element under " +  TAG_CROSS_PROFILE_INTENT_FILTERS + ": " +
                        tagName;
                PackageManagerService.reportSettingsProblem(Log.WARN, msg);
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private void readDomainVerificationLPw(XmlPullParser parser, PackageSettingBase packageSetting)
            throws XmlPullParserException, IOException {
        IntentFilterVerificationInfo ivi = new IntentFilterVerificationInfo(parser);
        packageSetting.setIntentFilterVerificationInfo(ivi);
        Log.d(TAG, "Read domain verification for package: " + ivi.getPackageName());
    }

    private void readRestoredIntentFilterVerifications(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            final String tagName = parser.getName();
            if (tagName.equals(TAG_DOMAIN_VERIFICATION)) {
                IntentFilterVerificationInfo ivi = new IntentFilterVerificationInfo(parser);
                if (DEBUG_DOMAIN_VERIFICATION) {
                    Slog.i(TAG, "Restored IVI for " + ivi.getPackageName()
                            + " status=" + ivi.getStatusString());
                }
                mRestoredIntentFilterVerifications.put(ivi.getPackageName(), ivi);
            } else {
                Slog.w(TAG, "Unknown element: " + tagName);
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void readDefaultAppsLPw(XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals(TAG_DEFAULT_BROWSER)) {
                String packageName = parser.getAttributeValue(null, ATTR_PACKAGE_NAME);
                mDefaultBrowserApp.put(userId, packageName);
            } else if (tagName.equals(TAG_DEFAULT_DIALER)) {
                String packageName = parser.getAttributeValue(null, ATTR_PACKAGE_NAME);
                mDefaultDialerApp.put(userId, packageName);
            } else {
                String msg = "Unknown element under " +  TAG_DEFAULT_APPS + ": " +
                        parser.getName();
                PackageManagerService.reportSettingsProblem(Log.WARN, msg);
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void readPackageRestrictionsLPr(int userId) {
        if (DEBUG_MU) {
            Log.i(TAG, "Reading package restrictions for user=" + userId);
        }
        FileInputStream str = null;
        File userPackagesStateFile = getUserPackagesStateFile(userId);
        File backupFile = getUserPackagesStateBackupFile(userId);
        if (backupFile.exists()) {
            try {
                str = new FileInputStream(backupFile);
                mReadMessages.append("Reading from backup stopped packages file\n");
                PackageManagerService.reportSettingsProblem(Log.INFO,
                        "Need to read from backup stopped packages file");
                if (userPackagesStateFile.exists()) {
                    // If both the backup and normal file exist, we
                    // ignore the normal one since it might have been
                    // corrupted.
                    Slog.w(PackageManagerService.TAG, "Cleaning up stopped packages file "
                            + userPackagesStateFile);
                    userPackagesStateFile.delete();
                }
            } catch (java.io.IOException e) {
                // We'll try for the normal settings file.
            }
        }

        try {
            if (str == null) {
                if (!userPackagesStateFile.exists()) {
                    mReadMessages.append("No stopped packages file found\n");
                    PackageManagerService.reportSettingsProblem(Log.INFO,
                            "No stopped packages file; "
                            + "assuming all started");
                    // At first boot, make sure no packages are stopped.
                    // We usually want to have third party apps initialize
                    // in the stopped state, but not at first boot.  Also
                    // consider all applications to be installed.
                    for (PackageSetting pkg : mPackages.values()) {
                        pkg.setUserState(userId, 0, COMPONENT_ENABLED_STATE_DEFAULT,
                                true,   // installed
                                false,  // stopped
                                false,  // notLaunched
                                false,  // hidden
                                false,  // suspended
                                null, null, null,
                                false, // blockUninstall
                                INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED, 0,
                                null,
                                null
                                );
                    }
                    return;
                }
                str = new FileInputStream(userPackagesStateFile);
            }
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(str, StandardCharsets.UTF_8.name());

            int type;
            while ((type=parser.next()) != XmlPullParser.START_TAG
                       && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                mReadMessages.append("No start tag found in package restrictions file\n");
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "No start tag found in package manager stopped packages");
                return;
            }

            int maxAppLinkGeneration = 0;

            int outerDepth = parser.getDepth();
            PackageSetting ps = null;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG
                           || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG
                        || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals(TAG_PACKAGE)) {
                    String name = parser.getAttributeValue(null, ATTR_NAME);
                    ps = mPackages.get(name);
                    if (ps == null) {
                        Slog.w(PackageManagerService.TAG, "No package known for stopped package "
                                + name);
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }

                    final long ceDataInode = XmlUtils.readLongAttribute(parser, ATTR_CE_DATA_INODE,
                            0);
                    final boolean installed = XmlUtils.readBooleanAttribute(parser, ATTR_INSTALLED,
                            true);
                    final boolean stopped = XmlUtils.readBooleanAttribute(parser, ATTR_STOPPED,
                            false);
                    final boolean notLaunched = XmlUtils.readBooleanAttribute(parser,
                            ATTR_NOT_LAUNCHED, false);

                    // For backwards compatibility with the previous name of "blocked", which
                    // now means hidden, read the old attribute as well.
                    final String blockedStr = parser.getAttributeValue(null, ATTR_BLOCKED);
                    boolean hidden = blockedStr == null
                            ? false : Boolean.parseBoolean(blockedStr);
                    final String hiddenStr = parser.getAttributeValue(null, ATTR_HIDDEN);
                    hidden = hiddenStr == null
                            ? hidden : Boolean.parseBoolean(hiddenStr);

                    final boolean suspended = XmlUtils.readBooleanAttribute(parser, ATTR_SUSPENDED,
                            false);
                    final boolean blockUninstall = XmlUtils.readBooleanAttribute(parser,
                            ATTR_BLOCK_UNINSTALL, false);
                    final int enabled = XmlUtils.readIntAttribute(parser, ATTR_ENABLED,
                            COMPONENT_ENABLED_STATE_DEFAULT);
                    final String enabledCaller = parser.getAttributeValue(null,
                            ATTR_ENABLED_CALLER);

                    final int verifState = XmlUtils.readIntAttribute(parser,
                            ATTR_DOMAIN_VERIFICATON_STATE,
                            PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED);
                    final int linkGeneration = XmlUtils.readIntAttribute(parser,
                            ATTR_APP_LINK_GENERATION, 0);
                    if (linkGeneration > maxAppLinkGeneration) {
                        maxAppLinkGeneration = linkGeneration;
                    }

                    ArraySet<String> enabledComponents = null;
                    ArraySet<String> disabledComponents = null;
                    ArraySet<String> protectedComponents = null;
                    ArraySet<String> visibleComponents = null;

                    int packageDepth = parser.getDepth();
                    while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG
                            || parser.getDepth() > packageDepth)) {
                        if (type == XmlPullParser.END_TAG
                                || type == XmlPullParser.TEXT) {
                            continue;
                        }
                        tagName = parser.getName();
                        if (tagName.equals(TAG_ENABLED_COMPONENTS)) {
                            enabledComponents = readComponentsLPr(parser);
                        } else if (tagName.equals(TAG_DISABLED_COMPONENTS)) {
                            disabledComponents = readComponentsLPr(parser);
                        } else if (tagName.equals(TAG_PROTECTED_COMPONENTS)) {
                            protectedComponents = readComponentsLPr(parser);
                        } else if (tagName.equals(TAG_VISIBLE_COMPONENTS)) {
                            visibleComponents = readComponentsLPr(parser);
                        }
                    }

                    ps.setUserState(userId, ceDataInode, enabled, installed, stopped, notLaunched,
                            hidden, suspended, enabledCaller, enabledComponents, disabledComponents,
                            blockUninstall, verifState, linkGeneration,
                            protectedComponents, visibleComponents);
                } else if (tagName.equals("preferred-activities")) {
                    readPreferredActivitiesLPw(parser, userId);
                } else if (tagName.equals(TAG_PERSISTENT_PREFERRED_ACTIVITIES)) {
                    readPersistentPreferredActivitiesLPw(parser, userId);
                } else if (tagName.equals(TAG_CROSS_PROFILE_INTENT_FILTERS)) {
                    readCrossProfileIntentFiltersLPw(parser, userId);
                } else if (tagName.equals(TAG_DEFAULT_APPS)) {
                    readDefaultAppsLPw(parser, userId);
                } else {
                    Slog.w(PackageManagerService.TAG, "Unknown element under <stopped-packages>: "
                          + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }

            str.close();

            mNextAppLinkGeneration.put(userId, maxAppLinkGeneration + 1);

        } catch (XmlPullParserException e) {
            mReadMessages.append("Error reading: " + e.toString());
            PackageManagerService.reportSettingsProblem(Log.ERROR,
                    "Error reading stopped packages: " + e);
            Slog.wtf(PackageManagerService.TAG, "Error reading package manager stopped packages",
                    e);

        } catch (java.io.IOException e) {
            mReadMessages.append("Error reading: " + e.toString());
            PackageManagerService.reportSettingsProblem(Log.ERROR, "Error reading settings: " + e);
            Slog.wtf(PackageManagerService.TAG, "Error reading package manager stopped packages",
                    e);
        }
    }

    private ArraySet<String> readComponentsLPr(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        ArraySet<String> components = null;
        int type;
        int outerDepth = parser.getDepth();
        String tagName;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG
                    || type == XmlPullParser.TEXT) {
                continue;
            }
            tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                String componentName = parser.getAttributeValue(null, ATTR_NAME);
                if (componentName != null) {
                    if (components == null) {
                        components = new ArraySet<String>();
                    }
                    components.add(componentName);
                }
            }
        }
        return components;
    }

    /**
     * Record the state of preferred activity configuration into XML.  This is used both
     * for recording packages.xml internally and for supporting backup/restore of the
     * preferred activity configuration.
     */
    void writePreferredActivitiesLPr(XmlSerializer serializer, int userId, boolean full)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(null, "preferred-activities");
        PreferredIntentResolver pir = mPreferredActivities.get(userId);
        if (pir != null) {
            for (final PreferredActivity pa : pir.filterSet()) {
                serializer.startTag(null, TAG_ITEM);
                pa.writeToXml(serializer, full);
                serializer.endTag(null, TAG_ITEM);
            }
        }
        serializer.endTag(null, "preferred-activities");
    }

    void writePersistentPreferredActivitiesLPr(XmlSerializer serializer, int userId)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(null, TAG_PERSISTENT_PREFERRED_ACTIVITIES);
        PersistentPreferredIntentResolver ppir = mPersistentPreferredActivities.get(userId);
        if (ppir != null) {
            for (final PersistentPreferredActivity ppa : ppir.filterSet()) {
                serializer.startTag(null, TAG_ITEM);
                ppa.writeToXml(serializer);
                serializer.endTag(null, TAG_ITEM);
            }
        }
        serializer.endTag(null, TAG_PERSISTENT_PREFERRED_ACTIVITIES);
    }

    void writeCrossProfileIntentFiltersLPr(XmlSerializer serializer, int userId)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(null, TAG_CROSS_PROFILE_INTENT_FILTERS);
        CrossProfileIntentResolver cpir = mCrossProfileIntentResolvers.get(userId);
        if (cpir != null) {
            for (final CrossProfileIntentFilter cpif : cpir.filterSet()) {
                serializer.startTag(null, TAG_ITEM);
                cpif.writeToXml(serializer);
                serializer.endTag(null, TAG_ITEM);
            }
        }
        serializer.endTag(null, TAG_CROSS_PROFILE_INTENT_FILTERS);
    }

    void writeDomainVerificationsLPr(XmlSerializer serializer,
                                     IntentFilterVerificationInfo verificationInfo)
            throws IllegalArgumentException, IllegalStateException, IOException {
        if (verificationInfo != null && verificationInfo.getPackageName() != null) {
            serializer.startTag(null, TAG_DOMAIN_VERIFICATION);
            verificationInfo.writeToXml(serializer);
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.d(TAG, "Wrote domain verification for package: "
                        + verificationInfo.getPackageName());
            }
            serializer.endTag(null, TAG_DOMAIN_VERIFICATION);
        }
    }

    // Specifically for backup/restore
    void writeAllDomainVerificationsLPr(XmlSerializer serializer, int userId)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(null, TAG_ALL_INTENT_FILTER_VERIFICATION);
        final int N = mPackages.size();
        for (int i = 0; i < N; i++) {
            PackageSetting ps = mPackages.valueAt(i);
            IntentFilterVerificationInfo ivi = ps.getIntentFilterVerificationInfo();
            if (ivi != null) {
                writeDomainVerificationsLPr(serializer, ivi);
            }
        }
        serializer.endTag(null, TAG_ALL_INTENT_FILTER_VERIFICATION);
    }

    // Specifically for backup/restore
    void readAllDomainVerificationsLPr(XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        mRestoredIntentFilterVerifications.clear();

        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_DOMAIN_VERIFICATION)) {
                IntentFilterVerificationInfo ivi = new IntentFilterVerificationInfo(parser);
                final String pkgName = ivi.getPackageName();
                final PackageSetting ps = mPackages.get(pkgName);
                if (ps != null) {
                    // known/existing package; update in place
                    ps.setIntentFilterVerificationInfo(ivi);
                    if (DEBUG_DOMAIN_VERIFICATION) {
                        Slog.d(TAG, "Restored IVI for existing app " + pkgName
                                + " status=" + ivi.getStatusString());
                    }
                } else {
                    mRestoredIntentFilterVerifications.put(pkgName, ivi);
                    if (DEBUG_DOMAIN_VERIFICATION) {
                        Slog.d(TAG, "Restored IVI for pending app " + pkgName
                                + " status=" + ivi.getStatusString());
                    }
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <all-intent-filter-verification>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    // Specifically for backup/restore
    public void processRestoredPermissionGrantLPr(String pkgName, String permission,
            boolean isGranted, int restoredFlagSet, int userId)
            throws IOException, XmlPullParserException {
        mRuntimePermissionsPersistence.rememberRestoredUserGrantLPr(
                pkgName, permission, isGranted, restoredFlagSet, userId);
    }

    void writeDefaultAppsLPr(XmlSerializer serializer, int userId)
            throws IllegalArgumentException, IllegalStateException, IOException {
        serializer.startTag(null, TAG_DEFAULT_APPS);
        String defaultBrowser = mDefaultBrowserApp.get(userId);
        if (!TextUtils.isEmpty(defaultBrowser)) {
            serializer.startTag(null, TAG_DEFAULT_BROWSER);
            serializer.attribute(null, ATTR_PACKAGE_NAME, defaultBrowser);
            serializer.endTag(null, TAG_DEFAULT_BROWSER);
        }
        String defaultDialer = mDefaultDialerApp.get(userId);
        if (!TextUtils.isEmpty(defaultDialer)) {
            serializer.startTag(null, TAG_DEFAULT_DIALER);
            serializer.attribute(null, ATTR_PACKAGE_NAME, defaultDialer);
            serializer.endTag(null, TAG_DEFAULT_DIALER);
        }
        serializer.endTag(null, TAG_DEFAULT_APPS);
    }

    void writePackageRestrictionsLPr(int userId) {
        if (DEBUG_MU) {
            Log.i(TAG, "Writing package restrictions for user=" + userId);
        }
        // Keep the old stopped packages around until we know the new ones have
        // been successfully written.
        File userPackagesStateFile = getUserPackagesStateFile(userId);
        File backupFile = getUserPackagesStateBackupFile(userId);
        new File(userPackagesStateFile.getParent()).mkdirs();
        if (userPackagesStateFile.exists()) {
            // Presence of backup settings file indicates that we failed
            // to persist packages earlier. So preserve the older
            // backup for future reference since the current packages
            // might have been corrupted.
            if (!backupFile.exists()) {
                if (!userPackagesStateFile.renameTo(backupFile)) {
                    Slog.wtf(PackageManagerService.TAG,
                            "Unable to backup user packages state file, "
                            + "current changes will be lost at reboot");
                    return;
                }
            } else {
                userPackagesStateFile.delete();
                Slog.w(PackageManagerService.TAG, "Preserving older stopped packages backup");
            }
        }

        try {
            final FileOutputStream fstr = new FileOutputStream(userPackagesStateFile);
            final BufferedOutputStream str = new BufferedOutputStream(fstr);

            final XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(str, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

            serializer.startTag(null, TAG_PACKAGE_RESTRICTIONS);

            for (final PackageSetting pkg : mPackages.values()) {
                final PackageUserState ustate = pkg.readUserState(userId);
                if (DEBUG_MU) Log.i(TAG, "  pkg=" + pkg.name + ", state=" + ustate.enabled);

                serializer.startTag(null, TAG_PACKAGE);
                serializer.attribute(null, ATTR_NAME, pkg.name);
                if (ustate.ceDataInode != 0) {
                    XmlUtils.writeLongAttribute(serializer, ATTR_CE_DATA_INODE, ustate.ceDataInode);
                }
                if (!ustate.installed) {
                    serializer.attribute(null, ATTR_INSTALLED, "false");
                }
                if (ustate.stopped) {
                    serializer.attribute(null, ATTR_STOPPED, "true");
                }
                if (ustate.notLaunched) {
                    serializer.attribute(null, ATTR_NOT_LAUNCHED, "true");
                }
                if (ustate.hidden) {
                    serializer.attribute(null, ATTR_HIDDEN, "true");
                }
                if (ustate.suspended) {
                    serializer.attribute(null, ATTR_SUSPENDED, "true");
                }
                if (ustate.blockUninstall) {
                    serializer.attribute(null, ATTR_BLOCK_UNINSTALL, "true");
                }
                if (ustate.enabled != COMPONENT_ENABLED_STATE_DEFAULT) {
                    serializer.attribute(null, ATTR_ENABLED,
                            Integer.toString(ustate.enabled));
                    if (ustate.lastDisableAppCaller != null) {
                        serializer.attribute(null, ATTR_ENABLED_CALLER,
                                ustate.lastDisableAppCaller);
                    }
                }
                if (ustate.domainVerificationStatus !=
                        PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED) {
                    XmlUtils.writeIntAttribute(serializer, ATTR_DOMAIN_VERIFICATON_STATE,
                            ustate.domainVerificationStatus);
                }
                if (ustate.appLinkGeneration != 0) {
                    XmlUtils.writeIntAttribute(serializer, ATTR_APP_LINK_GENERATION,
                            ustate.appLinkGeneration);
                }
                if (!ArrayUtils.isEmpty(ustate.enabledComponents)) {
                    serializer.startTag(null, TAG_ENABLED_COMPONENTS);
                    for (final String name : ustate.enabledComponents) {
                        serializer.startTag(null, TAG_ITEM);
                        serializer.attribute(null, ATTR_NAME, name);
                        serializer.endTag(null, TAG_ITEM);
                    }
                    serializer.endTag(null, TAG_ENABLED_COMPONENTS);
                }
                if (!ArrayUtils.isEmpty(ustate.disabledComponents)) {
                    serializer.startTag(null, TAG_DISABLED_COMPONENTS);
                    for (final String name : ustate.disabledComponents) {
                        serializer.startTag(null, TAG_ITEM);
                        serializer.attribute(null, ATTR_NAME, name);
                        serializer.endTag(null, TAG_ITEM);
                    }
                    serializer.endTag(null, TAG_DISABLED_COMPONENTS);
                }

                if (!ArrayUtils.isEmpty(ustate.protectedComponents)) {
                    serializer.startTag(null, TAG_PROTECTED_COMPONENTS);
                    for (final String name : ustate.protectedComponents) {
                        serializer.startTag(null, TAG_ITEM);
                        serializer.attribute(null, ATTR_NAME, name);
                        serializer.endTag(null, TAG_ITEM);
                    }
                    serializer.endTag(null, TAG_PROTECTED_COMPONENTS);
                }
                if (!ArrayUtils.isEmpty(ustate.visibleComponents)) {
                    serializer.startTag(null, TAG_VISIBLE_COMPONENTS);
                    for (final String name : ustate.visibleComponents) {
                        serializer.startTag(null, TAG_ITEM);
                        serializer.attribute(null, ATTR_NAME, name);
                        serializer.endTag(null, TAG_ITEM);
                    }
                    serializer.endTag(null, TAG_VISIBLE_COMPONENTS);
                }

                serializer.endTag(null, TAG_PACKAGE);
            }

            writePreferredActivitiesLPr(serializer, userId, true);
            writePersistentPreferredActivitiesLPr(serializer, userId);
            writeCrossProfileIntentFiltersLPr(serializer, userId);
            writeDefaultAppsLPr(serializer, userId);

            serializer.endTag(null, TAG_PACKAGE_RESTRICTIONS);

            serializer.endDocument();

            str.flush();
            FileUtils.sync(fstr);
            str.close();

            // New settings successfully written, old ones are no longer
            // needed.
            backupFile.delete();
            FileUtils.setPermissions(userPackagesStateFile.toString(),
                    FileUtils.S_IRUSR|FileUtils.S_IWUSR
                    |FileUtils.S_IRGRP|FileUtils.S_IWGRP,
                    -1, -1);

            // Done, all is good!
            return;
        } catch(java.io.IOException e) {
            Slog.wtf(PackageManagerService.TAG,
                    "Unable to write package manager user packages state, "
                    + " current changes will be lost at reboot", e);
        }

        // Clean up partially written files
        if (userPackagesStateFile.exists()) {
            if (!userPackagesStateFile.delete()) {
                Log.i(PackageManagerService.TAG, "Failed to clean up mangled file: "
                        + mStoppedPackagesFilename);
            }
        }
    }

    void readInstallPermissionsLPr(XmlPullParser parser,
            PermissionsState permissionsState) throws IOException, XmlPullParserException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG
                    || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                String name = parser.getAttributeValue(null, ATTR_NAME);

                BasePermission bp = mPermissions.get(name);
                if (bp == null) {
                    Slog.w(PackageManagerService.TAG, "Unknown permission: " + name);
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }

                String grantedStr = parser.getAttributeValue(null, ATTR_GRANTED);
                final boolean granted = grantedStr == null
                        || Boolean.parseBoolean(grantedStr);

                String flagsStr = parser.getAttributeValue(null, ATTR_FLAGS);
                final int flags = (flagsStr != null)
                        ? Integer.parseInt(flagsStr, 16) : 0;

                if (granted) {
                    if (permissionsState.grantInstallPermission(bp) ==
                            PermissionsState.PERMISSION_OPERATION_FAILURE) {
                        Slog.w(PackageManagerService.TAG, "Permission already added: " + name);
                        XmlUtils.skipCurrentTag(parser);
                    } else {
                        permissionsState.updatePermissionFlags(bp, UserHandle.USER_ALL,
                                PackageManager.MASK_PERMISSION_FLAGS, flags);
                    }
                } else {
                    if (permissionsState.revokeInstallPermission(bp) ==
                            PermissionsState.PERMISSION_OPERATION_FAILURE) {
                        Slog.w(PackageManagerService.TAG, "Permission already added: " + name);
                        XmlUtils.skipCurrentTag(parser);
                    } else {
                        permissionsState.updatePermissionFlags(bp, UserHandle.USER_ALL,
                                PackageManager.MASK_PERMISSION_FLAGS, flags);
                    }
                }
            } else {
                Slog.w(PackageManagerService.TAG, "Unknown element under <permissions>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    void writePermissionsLPr(XmlSerializer serializer, List<PermissionState> permissionStates)
            throws IOException {
        if (permissionStates.isEmpty()) {
            return;
        }

        serializer.startTag(null, TAG_PERMISSIONS);

        for (PermissionState permissionState : permissionStates) {
            serializer.startTag(null, TAG_ITEM);
            serializer.attribute(null, ATTR_NAME, permissionState.getName());
            serializer.attribute(null, ATTR_GRANTED, String.valueOf(permissionState.isGranted()));
            serializer.attribute(null, ATTR_FLAGS, Integer.toHexString(permissionState.getFlags()));
            serializer.endTag(null, TAG_ITEM);
        }

        serializer.endTag(null, TAG_PERMISSIONS);
    }

    void writeChildPackagesLPw(XmlSerializer serializer, List<String> childPackageNames)
            throws IOException {
        if (childPackageNames == null) {
            return;
        }
        final int childCount = childPackageNames.size();
        for (int i = 0; i < childCount; i++) {
            String childPackageName = childPackageNames.get(i);
            serializer.startTag(null, TAG_CHILD_PACKAGE);
            serializer.attribute(null, ATTR_NAME, childPackageName);
            serializer.endTag(null, TAG_CHILD_PACKAGE);
        }
    }

    // Note: assumed "stopped" field is already cleared in all packages.
    // Legacy reader, used to read in the old file format after an upgrade. Not used after that.
    void readStoppedLPw() {
        FileInputStream str = null;
        if (mBackupStoppedPackagesFilename.exists()) {
            try {
                str = new FileInputStream(mBackupStoppedPackagesFilename);
                mReadMessages.append("Reading from backup stopped packages file\n");
                PackageManagerService.reportSettingsProblem(Log.INFO,
                        "Need to read from backup stopped packages file");
                if (mSettingsFilename.exists()) {
                    // If both the backup and normal file exist, we
                    // ignore the normal one since it might have been
                    // corrupted.
                    Slog.w(PackageManagerService.TAG, "Cleaning up stopped packages file "
                            + mStoppedPackagesFilename);
                    mStoppedPackagesFilename.delete();
                }
            } catch (java.io.IOException e) {
                // We'll try for the normal settings file.
            }
        }

        try {
            if (str == null) {
                if (!mStoppedPackagesFilename.exists()) {
                    mReadMessages.append("No stopped packages file found\n");
                    PackageManagerService.reportSettingsProblem(Log.INFO,
                            "No stopped packages file file; assuming all started");
                    // At first boot, make sure no packages are stopped.
                    // We usually want to have third party apps initialize
                    // in the stopped state, but not at first boot.
                    for (PackageSetting pkg : mPackages.values()) {
                        pkg.setStopped(false, 0);
                        pkg.setNotLaunched(false, 0);
                    }
                    return;
                }
                str = new FileInputStream(mStoppedPackagesFilename);
            }
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(str, null);

            int type;
            while ((type=parser.next()) != XmlPullParser.START_TAG
                       && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                mReadMessages.append("No start tag found in stopped packages file\n");
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "No start tag found in package manager stopped packages");
                return;
            }

            int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG
                           || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG
                        || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals(TAG_PACKAGE)) {
                    String name = parser.getAttributeValue(null, ATTR_NAME);
                    PackageSetting ps = mPackages.get(name);
                    if (ps != null) {
                        ps.setStopped(true, 0);
                        if ("1".equals(parser.getAttributeValue(null, ATTR_NOT_LAUNCHED))) {
                            ps.setNotLaunched(true, 0);
                        }
                    } else {
                        Slog.w(PackageManagerService.TAG,
                                "No package known for stopped package " + name);
                    }
                    XmlUtils.skipCurrentTag(parser);
                } else {
                    Slog.w(PackageManagerService.TAG, "Unknown element under <stopped-packages>: "
                          + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }

            str.close();

        } catch (XmlPullParserException e) {
            mReadMessages.append("Error reading: " + e.toString());
            PackageManagerService.reportSettingsProblem(Log.ERROR,
                    "Error reading stopped packages: " + e);
            Slog.wtf(PackageManagerService.TAG, "Error reading package manager stopped packages",
                    e);

        } catch (java.io.IOException e) {
            mReadMessages.append("Error reading: " + e.toString());
            PackageManagerService.reportSettingsProblem(Log.ERROR, "Error reading settings: " + e);
            Slog.wtf(PackageManagerService.TAG, "Error reading package manager stopped packages",
                    e);

        }
    }

    void writeLPr() {
        //Debug.startMethodTracing("/data/system/packageprof", 8 * 1024 * 1024);

        // Keep the old settings around until we know the new ones have
        // been successfully written.
        if (mSettingsFilename.exists()) {
            // Presence of backup settings file indicates that we failed
            // to persist settings earlier. So preserve the older
            // backup for future reference since the current settings
            // might have been corrupted.
            if (!mBackupSettingsFilename.exists()) {
                if (!mSettingsFilename.renameTo(mBackupSettingsFilename)) {
                    Slog.wtf(PackageManagerService.TAG,
                            "Unable to backup package manager settings, "
                            + " current changes will be lost at reboot");
                    return;
                }
            } else {
                mSettingsFilename.delete();
                Slog.w(PackageManagerService.TAG, "Preserving older settings backup");
            }
        }

        mPastSignatures.clear();

        try {
            FileOutputStream fstr = new FileOutputStream(mSettingsFilename);
            BufferedOutputStream str = new BufferedOutputStream(fstr);

            //XmlSerializer serializer = XmlUtils.serializerInstance();
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(str, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

            serializer.startTag(null, "packages");

            for (int i = 0; i < mVersion.size(); i++) {
                final String volumeUuid = mVersion.keyAt(i);
                final VersionInfo ver = mVersion.valueAt(i);

                serializer.startTag(null, TAG_VERSION);
                XmlUtils.writeStringAttribute(serializer, ATTR_VOLUME_UUID, volumeUuid);
                XmlUtils.writeIntAttribute(serializer, ATTR_SDK_VERSION, ver.sdkVersion);
                XmlUtils.writeIntAttribute(serializer, ATTR_DATABASE_VERSION, ver.databaseVersion);
                XmlUtils.writeStringAttribute(serializer, ATTR_FINGERPRINT, ver.fingerprint);
                serializer.endTag(null, TAG_VERSION);
            }

            if (mVerifierDeviceIdentity != null) {
                serializer.startTag(null, "verifier");
                serializer.attribute(null, "device", mVerifierDeviceIdentity.toString());
                serializer.endTag(null, "verifier");
            }

            if (mReadExternalStorageEnforced != null) {
                serializer.startTag(null, TAG_READ_EXTERNAL_STORAGE);
                serializer.attribute(
                        null, ATTR_ENFORCEMENT, mReadExternalStorageEnforced ? "1" : "0");
                serializer.endTag(null, TAG_READ_EXTERNAL_STORAGE);
            }

            serializer.startTag(null, "permission-trees");
            for (BasePermission bp : mPermissionTrees.values()) {
                writePermissionLPr(serializer, bp);
            }
            serializer.endTag(null, "permission-trees");

            serializer.startTag(null, "permissions");
            for (BasePermission bp : mPermissions.values()) {
                writePermissionLPr(serializer, bp);
            }
            serializer.endTag(null, "permissions");

            for (final PackageSetting pkg : mPackages.values()) {
                writePackageLPr(serializer, pkg);
            }

            for (final PackageSetting pkg : mDisabledSysPackages.values()) {
                writeDisabledSysPackageLPr(serializer, pkg);
            }

            for (final SharedUserSetting usr : mSharedUsers.values()) {
                serializer.startTag(null, "shared-user");
                serializer.attribute(null, ATTR_NAME, usr.name);
                serializer.attribute(null, "userId",
                        Integer.toString(usr.userId));
                usr.signatures.writeXml(serializer, "sigs", mPastSignatures);
                writePermissionsLPr(serializer, usr.getPermissionsState()
                        .getInstallPermissionStates());
                serializer.endTag(null, "shared-user");
            }

            if (mPackagesToBeCleaned.size() > 0) {
                for (PackageCleanItem item : mPackagesToBeCleaned) {
                    final String userStr = Integer.toString(item.userId);
                    serializer.startTag(null, "cleaning-package");
                    serializer.attribute(null, ATTR_NAME, item.packageName);
                    serializer.attribute(null, ATTR_CODE, item.andCode ? "true" : "false");
                    serializer.attribute(null, ATTR_USER, userStr);
                    serializer.endTag(null, "cleaning-package");
                }
            }

            if (mRenamedPackages.size() > 0) {
                for (Map.Entry<String, String> e : mRenamedPackages.entrySet()) {
                    serializer.startTag(null, "renamed-package");
                    serializer.attribute(null, "new", e.getKey());
                    serializer.attribute(null, "old", e.getValue());
                    serializer.endTag(null, "renamed-package");
                }
            }

            final int numIVIs = mRestoredIntentFilterVerifications.size();
            if (numIVIs > 0) {
                if (DEBUG_DOMAIN_VERIFICATION) {
                    Slog.i(TAG, "Writing restored-ivi entries to packages.xml");
                }
                serializer.startTag(null, "restored-ivi");
                for (int i = 0; i < numIVIs; i++) {
                    IntentFilterVerificationInfo ivi = mRestoredIntentFilterVerifications.valueAt(i);
                    writeDomainVerificationsLPr(serializer, ivi);
                }
                serializer.endTag(null, "restored-ivi");
            } else {
                if (DEBUG_DOMAIN_VERIFICATION) {
                    Slog.i(TAG, "  no restored IVI entries to write");
                }
            }

            mKeySetManagerService.writeKeySetManagerServiceLPr(serializer);

            serializer.endTag(null, "packages");

            serializer.endDocument();

            str.flush();
            FileUtils.sync(fstr);
            str.close();

            // New settings successfully written, old ones are no longer
            // needed.
            mBackupSettingsFilename.delete();
            FileUtils.setPermissions(mSettingsFilename.toString(),
                    FileUtils.S_IRUSR|FileUtils.S_IWUSR
                    |FileUtils.S_IRGRP|FileUtils.S_IWGRP,
                    -1, -1);

            writeKernelMappingLPr();
            writePackageListLPr();
            writeAllUsersPackageRestrictionsLPr();
            writeAllRuntimePermissionsLPr();
            return;

        } catch(XmlPullParserException e) {
            Slog.wtf(PackageManagerService.TAG, "Unable to write package manager settings, "
                    + "current changes will be lost at reboot", e);
        } catch(java.io.IOException e) {
            Slog.wtf(PackageManagerService.TAG, "Unable to write package manager settings, "
                    + "current changes will be lost at reboot", e);
        }
        // Clean up partially written files
        if (mSettingsFilename.exists()) {
            if (!mSettingsFilename.delete()) {
                Slog.wtf(PackageManagerService.TAG, "Failed to clean up mangled file: "
                        + mSettingsFilename);
            }
        }
        //Debug.stopMethodTracing();
    }

    void writeKernelMappingLPr() {
        if (mKernelMappingFilename == null) return;

        final String[] known = mKernelMappingFilename.list();
        final ArraySet<String> knownSet = new ArraySet<>(known.length);
        for (String name : known) {
            knownSet.add(name);
        }

        for (final PackageSetting ps : mPackages.values()) {
            // Package is actively claimed
            knownSet.remove(ps.name);
            writeKernelMappingLPr(ps);
        }

        // Remove any unclaimed mappings
        for (int i = 0; i < knownSet.size(); i++) {
            final String name = knownSet.valueAt(i);
            if (DEBUG_KERNEL) Slog.d(TAG, "Dropping mapping " + name);

            mKernelMapping.remove(name);
            new File(mKernelMappingFilename, name).delete();
        }
    }

    void writeKernelMappingLPr(PackageSetting ps) {
        if (mKernelMappingFilename == null) return;

        final Integer cur = mKernelMapping.get(ps.name);
        if (cur != null && cur.intValue() == ps.appId) {
            // Ignore when mapping already matches
            return;
        }

        if (DEBUG_KERNEL) Slog.d(TAG, "Mapping " + ps.name + " to " + ps.appId);

        final File dir = new File(mKernelMappingFilename, ps.name);
        dir.mkdir();

        final File file = new File(dir, "appid");
        try {
            FileUtils.stringToFile(file, Integer.toString(ps.appId));
            mKernelMapping.put(ps.name, ps.appId);
        } catch (IOException ignored) {
        }
    }

    void writePackageListLPr() {
        writePackageListLPr(-1);
    }

    void writePackageListLPr(int creatingUserId) {
        // Only derive GIDs for active users (not dying)
        final List<UserInfo> users = UserManagerService.getInstance().getUsers(true);
        int[] userIds = new int[users.size()];
        for (int i = 0; i < userIds.length; i++) {
            userIds[i] = users.get(i).id;
        }
        if (creatingUserId != -1) {
            userIds = ArrayUtils.appendInt(userIds, creatingUserId);
        }

        // Write package list file now, use a JournaledFile.
        File tempFile = new File(mPackageListFilename.getAbsolutePath() + ".tmp");
        JournaledFile journal = new JournaledFile(mPackageListFilename, tempFile);

        final File writeTarget = journal.chooseForWrite();
        FileOutputStream fstr;
        BufferedWriter writer = null;
        try {
            fstr = new FileOutputStream(writeTarget);
            writer = new BufferedWriter(new OutputStreamWriter(fstr, Charset.defaultCharset()));
            FileUtils.setPermissions(fstr.getFD(), 0640, SYSTEM_UID, PACKAGE_INFO_GID);

            StringBuilder sb = new StringBuilder();
            for (final PackageSetting pkg : mPackages.values()) {
                if (pkg.pkg == null || pkg.pkg.applicationInfo == null
                        || pkg.pkg.applicationInfo.dataDir == null) {
                    if (!"android".equals(pkg.name)) {
                        Slog.w(TAG, "Skipping " + pkg + " due to missing metadata");
                    }
                    continue;
                }

                final ApplicationInfo ai = pkg.pkg.applicationInfo;
                final String dataPath = ai.dataDir;
                final boolean isDebug = (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                final int[] gids = pkg.getPermissionsState().computeGids(userIds);

                // Avoid any application that has a space in its path.
                if (dataPath.indexOf(' ') >= 0)
                    continue;

                // we store on each line the following information for now:
                //
                // pkgName    - package name
                // userId     - application-specific user id
                // debugFlag  - 0 or 1 if the package is debuggable.
                // dataPath   - path to package's data path
                // seinfo     - seinfo label for the app (assigned at install time)
                // gids       - supplementary gids this app launches with
                //
                // NOTE: We prefer not to expose all ApplicationInfo flags for now.
                //
                // DO NOT MODIFY THIS FORMAT UNLESS YOU CAN ALSO MODIFY ITS USERS
                // FROM NATIVE CODE. AT THE MOMENT, LOOK AT THE FOLLOWING SOURCES:
                //   frameworks/base/libs/packagelistparser
                //   system/core/run-as/run-as.c
                //
                sb.setLength(0);
                sb.append(ai.packageName);
                sb.append(" ");
                sb.append(ai.uid);
                sb.append(isDebug ? " 1 " : " 0 ");
                sb.append(dataPath);
                sb.append(" ");
                sb.append(ai.seinfo);
                sb.append(" ");
                if (gids != null && gids.length > 0) {
                    sb.append(gids[0]);
                    for (int i = 1; i < gids.length; i++) {
                        sb.append(",");
                        sb.append(gids[i]);
                    }
                } else {
                    sb.append("none");
                }
                sb.append("\n");
                writer.append(sb);
            }
            writer.flush();
            FileUtils.sync(fstr);
            writer.close();
            journal.commit();
        } catch (Exception e) {
            Slog.wtf(TAG, "Failed to write packages.list", e);
            IoUtils.closeQuietly(writer);
            journal.rollback();
        }
    }

    void writeDisabledSysPackageLPr(XmlSerializer serializer, final PackageSetting pkg)
            throws java.io.IOException {
        serializer.startTag(null, "updated-package");
        serializer.attribute(null, ATTR_NAME, pkg.name);
        if (pkg.realName != null) {
            serializer.attribute(null, "realName", pkg.realName);
        }
        serializer.attribute(null, "codePath", pkg.codePathString);
        serializer.attribute(null, "ft", Long.toHexString(pkg.timeStamp));
        serializer.attribute(null, "it", Long.toHexString(pkg.firstInstallTime));
        serializer.attribute(null, "ut", Long.toHexString(pkg.lastUpdateTime));
        serializer.attribute(null, "version", String.valueOf(pkg.versionCode));
        if (!pkg.resourcePathString.equals(pkg.codePathString)) {
            serializer.attribute(null, "resourcePath", pkg.resourcePathString);
        }
        if (pkg.legacyNativeLibraryPathString != null) {
            serializer.attribute(null, "nativeLibraryPath", pkg.legacyNativeLibraryPathString);
        }
        if (pkg.primaryCpuAbiString != null) {
           serializer.attribute(null, "primaryCpuAbi", pkg.primaryCpuAbiString);
        }
        if (pkg.secondaryCpuAbiString != null) {
            serializer.attribute(null, "secondaryCpuAbi", pkg.secondaryCpuAbiString);
        }
        if (pkg.cpuAbiOverrideString != null) {
            serializer.attribute(null, "cpuAbiOverride", pkg.cpuAbiOverrideString);
        }

        if (pkg.sharedUser == null) {
            serializer.attribute(null, "userId", Integer.toString(pkg.appId));
        } else {
            serializer.attribute(null, "sharedUserId", Integer.toString(pkg.appId));
        }

        if (pkg.parentPackageName != null) {
            serializer.attribute(null, "parentPackageName", pkg.parentPackageName);
        }

        writeChildPackagesLPw(serializer, pkg.childPackageNames);

        // If this is a shared user, the permissions will be written there.
        if (pkg.sharedUser == null) {
            writePermissionsLPr(serializer, pkg.getPermissionsState()
                    .getInstallPermissionStates());
        }

        serializer.endTag(null, "updated-package");
    }

    void writePackageLPr(XmlSerializer serializer, final PackageSetting pkg)
            throws java.io.IOException {
        serializer.startTag(null, "package");
        serializer.attribute(null, ATTR_NAME, pkg.name);
        if (pkg.realName != null) {
            serializer.attribute(null, "realName", pkg.realName);
        }
        serializer.attribute(null, "codePath", pkg.codePathString);
        if (!pkg.resourcePathString.equals(pkg.codePathString)) {
            serializer.attribute(null, "resourcePath", pkg.resourcePathString);
        }

        if (pkg.legacyNativeLibraryPathString != null) {
            serializer.attribute(null, "nativeLibraryPath", pkg.legacyNativeLibraryPathString);
        }
        if (pkg.primaryCpuAbiString != null) {
            serializer.attribute(null, "primaryCpuAbi", pkg.primaryCpuAbiString);
        }
        if (pkg.secondaryCpuAbiString != null) {
            serializer.attribute(null, "secondaryCpuAbi", pkg.secondaryCpuAbiString);
        }
        if (pkg.cpuAbiOverrideString != null) {
            serializer.attribute(null, "cpuAbiOverride", pkg.cpuAbiOverrideString);
        }

        serializer.attribute(null, "publicFlags", Integer.toString(pkg.pkgFlags));
        serializer.attribute(null, "privateFlags", Integer.toString(pkg.pkgPrivateFlags));
        serializer.attribute(null, "ft", Long.toHexString(pkg.timeStamp));
        serializer.attribute(null, "it", Long.toHexString(pkg.firstInstallTime));
        serializer.attribute(null, "ut", Long.toHexString(pkg.lastUpdateTime));
        serializer.attribute(null, "version", String.valueOf(pkg.versionCode));
        if (pkg.sharedUser == null) {
            serializer.attribute(null, "userId", Integer.toString(pkg.appId));
        } else {
            serializer.attribute(null, "sharedUserId", Integer.toString(pkg.appId));
        }
        if (pkg.uidError) {
            serializer.attribute(null, "uidError", "true");
        }
        if (pkg.installStatus == PackageSettingBase.PKG_INSTALL_INCOMPLETE) {
            serializer.attribute(null, "installStatus", "false");
        }
        if (pkg.installerPackageName != null) {
            serializer.attribute(null, "installer", pkg.installerPackageName);
        }
        if (pkg.isOrphaned) {
            serializer.attribute(null, "isOrphaned", "true");
        }
        if (pkg.volumeUuid != null) {
            serializer.attribute(null, "volumeUuid", pkg.volumeUuid);
        }
        if (pkg.parentPackageName != null) {
            serializer.attribute(null, "parentPackageName", pkg.parentPackageName);
        }

        writeChildPackagesLPw(serializer, pkg.childPackageNames);

        pkg.signatures.writeXml(serializer, "sigs", mPastSignatures);

        writePermissionsLPr(serializer, pkg.getPermissionsState()
                    .getInstallPermissionStates());

        writeSigningKeySetLPr(serializer, pkg.keySetData);
        writeUpgradeKeySetsLPr(serializer, pkg.keySetData);
        writeKeySetAliasesLPr(serializer, pkg.keySetData);
        writeDomainVerificationsLPr(serializer, pkg.verificationInfo);

        serializer.endTag(null, "package");
    }

    void writeSigningKeySetLPr(XmlSerializer serializer,
            PackageKeySetData data) throws IOException {
        serializer.startTag(null, "proper-signing-keyset");
        serializer.attribute(null, "identifier",
                Long.toString(data.getProperSigningKeySet()));
        serializer.endTag(null, "proper-signing-keyset");
    }

    void writeUpgradeKeySetsLPr(XmlSerializer serializer,
            PackageKeySetData data) throws IOException {
        long properSigning = data.getProperSigningKeySet();
        if (data.isUsingUpgradeKeySets()) {
            for (long id : data.getUpgradeKeySets()) {
                serializer.startTag(null, "upgrade-keyset");
                serializer.attribute(null, "identifier", Long.toString(id));
                serializer.endTag(null, "upgrade-keyset");
            }
        }
    }

    void writeKeySetAliasesLPr(XmlSerializer serializer,
            PackageKeySetData data) throws IOException {
        for (Map.Entry<String, Long> e: data.getAliases().entrySet()) {
            serializer.startTag(null, "defined-keyset");
            serializer.attribute(null, "alias", e.getKey());
            serializer.attribute(null, "identifier", Long.toString(e.getValue()));
            serializer.endTag(null, "defined-keyset");
        }
    }

    void writePermissionLPr(XmlSerializer serializer, BasePermission bp)
            throws XmlPullParserException, java.io.IOException {
        if (bp.sourcePackage != null) {
            serializer.startTag(null, TAG_ITEM);
            serializer.attribute(null, ATTR_NAME, bp.name);
            serializer.attribute(null, "package", bp.sourcePackage);
            if (bp.protectionLevel != PermissionInfo.PROTECTION_NORMAL) {
                serializer.attribute(null, "protection", Integer.toString(bp.protectionLevel));
            }
            if (PackageManagerService.DEBUG_SETTINGS)
                Log.v(PackageManagerService.TAG, "Writing perm: name=" + bp.name + " type="
                        + bp.type);
            if (bp.type == BasePermission.TYPE_DYNAMIC) {
                final PermissionInfo pi = bp.perm != null ? bp.perm.info : bp.pendingInfo;
                if (pi != null) {
                    serializer.attribute(null, "type", "dynamic");
                    if (pi.icon != 0) {
                        serializer.attribute(null, "icon", Integer.toString(pi.icon));
                    }
                    if (pi.nonLocalizedLabel != null) {
                        serializer.attribute(null, "label", pi.nonLocalizedLabel.toString());
                    }
                }
            }
            if (bp.allowViaWhitelist) {
                serializer.attribute(null, "allowViaWhitelist", Integer.toString(1));
            }
            serializer.endTag(null, TAG_ITEM);
        }
    }

    ArrayList<PackageSetting> getListOfIncompleteInstallPackagesLPr() {
        final ArraySet<String> kList = new ArraySet<String>(mPackages.keySet());
        final Iterator<String> its = kList.iterator();
        final ArrayList<PackageSetting> ret = new ArrayList<PackageSetting>();
        while (its.hasNext()) {
            final String key = its.next();
            final PackageSetting ps = mPackages.get(key);
            if (ps.getInstallStatus() == PackageSettingBase.PKG_INSTALL_INCOMPLETE) {
                ret.add(ps);
            }
        }
        return ret;
    }

    void addPackageToCleanLPw(PackageCleanItem pkg) {
        if (!mPackagesToBeCleaned.contains(pkg)) {
            mPackagesToBeCleaned.add(pkg);
        }
    }

    boolean readLPw(@NonNull List<UserInfo> users) {
        FileInputStream str = null;
        if (mBackupSettingsFilename.exists()) {
            try {
                str = new FileInputStream(mBackupSettingsFilename);
                mReadMessages.append("Reading from backup settings file\n");
                PackageManagerService.reportSettingsProblem(Log.INFO,
                        "Need to read from backup settings file");
                if (mSettingsFilename.exists()) {
                    // If both the backup and settings file exist, we
                    // ignore the settings since it might have been
                    // corrupted.
                    Slog.w(PackageManagerService.TAG, "Cleaning up settings file "
                            + mSettingsFilename);
                    mSettingsFilename.delete();
                }
            } catch (java.io.IOException e) {
                // We'll try for the normal settings file.
            }
        }

        mPendingPackages.clear();
        mPastSignatures.clear();
        mKeySetRefs.clear();
        mInstallerPackages.clear();

        try {
            if (str == null) {
                if (!mSettingsFilename.exists()) {
                    mReadMessages.append("No settings file found\n");
                    PackageManagerService.reportSettingsProblem(Log.INFO,
                            "No settings file; creating initial state");
                    // It's enough to just touch version details to create them
                    // with default values
                    findOrCreateVersion(StorageManager.UUID_PRIVATE_INTERNAL);
                    findOrCreateVersion(StorageManager.UUID_PRIMARY_PHYSICAL);
                    return false;
                }
                str = new FileInputStream(mSettingsFilename);
            }
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(str, StandardCharsets.UTF_8.name());

            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                mReadMessages.append("No start tag found in settings file\n");
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "No start tag found in package manager settings");
                Slog.wtf(PackageManagerService.TAG,
                        "No start tag found in package manager settings");
                return false;
            }

            int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("package")) {
                    readPackageLPw(parser);
                } else if (tagName.equals("permissions")) {
                    readPermissionsLPw(mPermissions, parser);
                } else if (tagName.equals("permission-trees")) {
                    readPermissionsLPw(mPermissionTrees, parser);
                } else if (tagName.equals("shared-user")) {
                    readSharedUserLPw(parser);
                } else if (tagName.equals("preferred-packages")) {
                    // no longer used.
                } else if (tagName.equals("preferred-activities")) {
                    // Upgrading from old single-user implementation;
                    // these are the preferred activities for user 0.
                    readPreferredActivitiesLPw(parser, 0);
                } else if (tagName.equals(TAG_PERSISTENT_PREFERRED_ACTIVITIES)) {
                    // TODO: check whether this is okay! as it is very
                    // similar to how preferred-activities are treated
                    readPersistentPreferredActivitiesLPw(parser, 0);
                } else if (tagName.equals(TAG_CROSS_PROFILE_INTENT_FILTERS)) {
                    // TODO: check whether this is okay! as it is very
                    // similar to how preferred-activities are treated
                    readCrossProfileIntentFiltersLPw(parser, 0);
                } else if (tagName.equals(TAG_DEFAULT_BROWSER)) {
                    readDefaultAppsLPw(parser, 0);
                } else if (tagName.equals("updated-package")) {
                    readDisabledSysPackageLPw(parser);
                } else if (tagName.equals("cleaning-package")) {
                    String name = parser.getAttributeValue(null, ATTR_NAME);
                    String userStr = parser.getAttributeValue(null, ATTR_USER);
                    String codeStr = parser.getAttributeValue(null, ATTR_CODE);
                    if (name != null) {
                        int userId = UserHandle.USER_SYSTEM;
                        boolean andCode = true;
                        try {
                            if (userStr != null) {
                                userId = Integer.parseInt(userStr);
                            }
                        } catch (NumberFormatException e) {
                        }
                        if (codeStr != null) {
                            andCode = Boolean.parseBoolean(codeStr);
                        }
                        addPackageToCleanLPw(new PackageCleanItem(userId, name, andCode));
                    }
                } else if (tagName.equals("renamed-package")) {
                    String nname = parser.getAttributeValue(null, "new");
                    String oname = parser.getAttributeValue(null, "old");
                    if (nname != null && oname != null) {
                        mRenamedPackages.put(nname, oname);
                    }
                } else if (tagName.equals("restored-ivi")) {
                    readRestoredIntentFilterVerifications(parser);
                } else if (tagName.equals("last-platform-version")) {
                    // Upgrade from older XML schema
                    final VersionInfo internal = findOrCreateVersion(
                            StorageManager.UUID_PRIVATE_INTERNAL);
                    final VersionInfo external = findOrCreateVersion(
                            StorageManager.UUID_PRIMARY_PHYSICAL);

                    internal.sdkVersion = XmlUtils.readIntAttribute(parser, "internal", 0);
                    external.sdkVersion = XmlUtils.readIntAttribute(parser, "external", 0);
                    internal.fingerprint = external.fingerprint =
                            XmlUtils.readStringAttribute(parser, "fingerprint");

                } else if (tagName.equals("database-version")) {
                    // Upgrade from older XML schema
                    final VersionInfo internal = findOrCreateVersion(
                            StorageManager.UUID_PRIVATE_INTERNAL);
                    final VersionInfo external = findOrCreateVersion(
                            StorageManager.UUID_PRIMARY_PHYSICAL);

                    internal.databaseVersion = XmlUtils.readIntAttribute(parser, "internal", 0);
                    external.databaseVersion = XmlUtils.readIntAttribute(parser, "external", 0);

                } else if (tagName.equals("verifier")) {
                    final String deviceIdentity = parser.getAttributeValue(null, "device");
                    try {
                        mVerifierDeviceIdentity = VerifierDeviceIdentity.parse(deviceIdentity);
                    } catch (IllegalArgumentException e) {
                        Slog.w(PackageManagerService.TAG, "Discard invalid verifier device id: "
                                + e.getMessage());
                    }
                } else if (TAG_READ_EXTERNAL_STORAGE.equals(tagName)) {
                    final String enforcement = parser.getAttributeValue(null, ATTR_ENFORCEMENT);
                    mReadExternalStorageEnforced = "1".equals(enforcement);
                } else if (tagName.equals("keyset-settings")) {
                    mKeySetManagerService.readKeySetsLPw(parser, mKeySetRefs);
                } else if (TAG_VERSION.equals(tagName)) {
                    final String volumeUuid = XmlUtils.readStringAttribute(parser,
                            ATTR_VOLUME_UUID);
                    final VersionInfo ver = findOrCreateVersion(volumeUuid);
                    ver.sdkVersion = XmlUtils.readIntAttribute(parser, ATTR_SDK_VERSION);
                    ver.databaseVersion = XmlUtils.readIntAttribute(parser, ATTR_SDK_VERSION);
                    ver.fingerprint = XmlUtils.readStringAttribute(parser, ATTR_FINGERPRINT);
                } else {
                    Slog.w(PackageManagerService.TAG, "Unknown element under <packages>: "
                            + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }

            str.close();

        } catch (XmlPullParserException e) {
            mReadMessages.append("Error reading: " + e.toString());
            PackageManagerService.reportSettingsProblem(Log.ERROR, "Error reading settings: " + e);
            Slog.wtf(PackageManagerService.TAG, "Error reading package manager settings", e);

        } catch (java.io.IOException e) {
            mReadMessages.append("Error reading: " + e.toString());
            PackageManagerService.reportSettingsProblem(Log.ERROR, "Error reading settings: " + e);
            Slog.wtf(PackageManagerService.TAG, "Error reading package manager settings", e);
        }

        // If the build is setup to drop runtime permissions
        // on update drop the files before loading them.
        if (PackageManagerService.CLEAR_RUNTIME_PERMISSIONS_ON_UPGRADE) {
            final VersionInfo internal = getInternalVersion();
            if (!Build.FINGERPRINT.equals(internal.fingerprint)) {
                for (UserInfo user : users) {
                    mRuntimePermissionsPersistence.deleteUserRuntimePermissionsFile(user.id);
                }
            }
        }

        final int N = mPendingPackages.size();

        for (int i = 0; i < N; i++) {
            final PendingPackage pp = mPendingPackages.get(i);
            Object idObj = getUserIdLPr(pp.sharedId);
            if (idObj != null && idObj instanceof SharedUserSetting) {
                PackageSetting p = getPackageLPw(pp.name, null, pp.realName,
                        (SharedUserSetting) idObj, pp.codePath, pp.resourcePath,
                        pp.legacyNativeLibraryPathString, pp.primaryCpuAbiString,
                        pp.secondaryCpuAbiString, pp.versionCode, pp.pkgFlags, pp.pkgPrivateFlags,
                        null, true /* add */, false /* allowInstall */, pp.parentPackageName,
                        pp.childPackageNames);
                if (p == null) {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Unable to create application package for " + pp.name);
                    continue;
                }
                p.copyFrom(pp);
            } else if (idObj != null) {
                String msg = "Bad package setting: package " + pp.name + " has shared uid "
                        + pp.sharedId + " that is not a shared uid\n";
                mReadMessages.append(msg);
                PackageManagerService.reportSettingsProblem(Log.ERROR, msg);
            } else {
                String msg = "Bad package setting: package " + pp.name + " has shared uid "
                        + pp.sharedId + " that is not defined\n";
                mReadMessages.append(msg);
                PackageManagerService.reportSettingsProblem(Log.ERROR, msg);
            }
        }
        mPendingPackages.clear();

        if (mBackupStoppedPackagesFilename.exists()
                || mStoppedPackagesFilename.exists()) {
            // Read old file
            readStoppedLPw();
            mBackupStoppedPackagesFilename.delete();
            mStoppedPackagesFilename.delete();
            // Migrate to new file format
            writePackageRestrictionsLPr(UserHandle.USER_SYSTEM);
        } else {
            for (UserInfo user : users) {
                readPackageRestrictionsLPr(user.id);
            }
        }

        for (UserInfo user : users) {
            mRuntimePermissionsPersistence.readStateForUserSyncLPr(user.id);
        }

        /*
         * Make sure all the updated system packages have their shared users
         * associated with them.
         */
        final Iterator<PackageSetting> disabledIt = mDisabledSysPackages.values().iterator();
        while (disabledIt.hasNext()) {
            final PackageSetting disabledPs = disabledIt.next();
            final Object id = getUserIdLPr(disabledPs.appId);
            if (id != null && id instanceof SharedUserSetting) {
                disabledPs.sharedUser = (SharedUserSetting) id;
            }
        }

        mReadMessages.append("Read completed successfully: " + mPackages.size() + " packages, "
                + mSharedUsers.size() + " shared uids\n");

        writeKernelMappingLPr();

        return true;
    }

    void applyDefaultPreferredAppsLPw(PackageManagerService service, int userId) {
        // First pull data from any pre-installed apps.
        for (PackageSetting ps : mPackages.values()) {
            if ((ps.pkgFlags&ApplicationInfo.FLAG_SYSTEM) != 0 && ps.pkg != null
                    && ps.pkg.preferredActivityFilters != null) {
                ArrayList<PackageParser.ActivityIntentInfo> intents
                        = ps.pkg.preferredActivityFilters;
                for (int i=0; i<intents.size(); i++) {
                    PackageParser.ActivityIntentInfo aii = intents.get(i);
                    applyDefaultPreferredActivityLPw(service, aii, new ComponentName(
                            ps.name, aii.activity.className), userId);
                }
            }
        }

        // Read preferred apps from .../etc/preferred-apps directory.
        File preferredDir = new File(Environment.getRootDirectory(), "etc/preferred-apps");
        if (!preferredDir.exists() || !preferredDir.isDirectory()) {
            return;
        }
        if (!preferredDir.canRead()) {
            Slog.w(TAG, "Directory " + preferredDir + " cannot be read");
            return;
        }

        // Iterate over the files in the directory and scan .xml files
        for (File f : preferredDir.listFiles()) {
            if (!f.getPath().endsWith(".xml")) {
                Slog.i(TAG, "Non-xml file " + f + " in " + preferredDir + " directory, ignoring");
                continue;
            }
            if (!f.canRead()) {
                Slog.w(TAG, "Preferred apps file " + f + " cannot be read");
                continue;
            }

            if (PackageManagerService.DEBUG_PREFERRED) Log.d(TAG, "Reading default preferred " + f);
            InputStream str = null;
            try {
                str = new BufferedInputStream(new FileInputStream(f));
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(str, null);

                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG
                        && type != XmlPullParser.END_DOCUMENT) {
                    ;
                }

                if (type != XmlPullParser.START_TAG) {
                    Slog.w(TAG, "Preferred apps file " + f + " does not have start tag");
                    continue;
                }
                if (!"preferred-activities".equals(parser.getName())) {
                    Slog.w(TAG, "Preferred apps file " + f
                            + " does not start with 'preferred-activities'");
                    continue;
                }
                readDefaultPreferredActivitiesLPw(service, parser, userId);
            } catch (XmlPullParserException e) {
                Slog.w(TAG, "Error reading apps file " + f, e);
            } catch (IOException e) {
                Slog.w(TAG, "Error reading apps file " + f, e);
            } finally {
                if (str != null) {
                    try {
                        str.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    private void applyDefaultPreferredActivityLPw(PackageManagerService service,
            IntentFilter tmpPa, ComponentName cn, int userId) {
        // The initial preferences only specify the target activity
        // component and intent-filter, not the set of matches.  So we
        // now need to query for the matches to build the correct
        // preferred activity entry.
        if (PackageManagerService.DEBUG_PREFERRED) {
            Log.d(TAG, "Processing preferred:");
            tmpPa.dump(new LogPrinter(Log.DEBUG, TAG), "  ");
        }
        Intent intent = new Intent();
        int flags = PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        intent.setAction(tmpPa.getAction(0));
        for (int i=0; i<tmpPa.countCategories(); i++) {
            String cat = tmpPa.getCategory(i);
            if (cat.equals(Intent.CATEGORY_DEFAULT)) {
                flags |= MATCH_DEFAULT_ONLY;
            } else {
                intent.addCategory(cat);
            }
        }

        boolean doNonData = true;
        boolean hasSchemes = false;

        for (int ischeme=0; ischeme<tmpPa.countDataSchemes(); ischeme++) {
            boolean doScheme = true;
            String scheme = tmpPa.getDataScheme(ischeme);
            if (scheme != null && !scheme.isEmpty()) {
                hasSchemes = true;
            }
            for (int issp=0; issp<tmpPa.countDataSchemeSpecificParts(); issp++) {
                Uri.Builder builder = new Uri.Builder();
                builder.scheme(scheme);
                PatternMatcher ssp = tmpPa.getDataSchemeSpecificPart(issp);
                builder.opaquePart(ssp.getPath());
                Intent finalIntent = new Intent(intent);
                finalIntent.setData(builder.build());
                applyDefaultPreferredActivityLPw(service, finalIntent, flags, cn,
                        scheme, ssp, null, null, userId);
                doScheme = false;
            }
            for (int iauth=0; iauth<tmpPa.countDataAuthorities(); iauth++) {
                boolean doAuth = true;
                IntentFilter.AuthorityEntry auth = tmpPa.getDataAuthority(iauth);
                for (int ipath=0; ipath<tmpPa.countDataPaths(); ipath++) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(scheme);
                    if (auth.getHost() != null) {
                        builder.authority(auth.getHost());
                    }
                    PatternMatcher path = tmpPa.getDataPath(ipath);
                    builder.path(path.getPath());
                    Intent finalIntent = new Intent(intent);
                    finalIntent.setData(builder.build());
                    applyDefaultPreferredActivityLPw(service, finalIntent, flags, cn,
                            scheme, null, auth, path, userId);
                    doAuth = doScheme = false;
                }
                if (doAuth) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(scheme);
                    if (auth.getHost() != null) {
                        builder.authority(auth.getHost());
                    }
                    Intent finalIntent = new Intent(intent);
                    finalIntent.setData(builder.build());
                    applyDefaultPreferredActivityLPw(service, finalIntent, flags, cn,
                            scheme, null, auth, null, userId);
                    doScheme = false;
                }
            }
            if (doScheme) {
                Uri.Builder builder = new Uri.Builder();
                builder.scheme(scheme);
                Intent finalIntent = new Intent(intent);
                finalIntent.setData(builder.build());
                applyDefaultPreferredActivityLPw(service, finalIntent, flags, cn,
                        scheme, null, null, null, userId);
            }
            doNonData = false;
        }

        for (int idata=0; idata<tmpPa.countDataTypes(); idata++) {
            String mimeType = tmpPa.getDataType(idata);
            if (hasSchemes) {
                Uri.Builder builder = new Uri.Builder();
                for (int ischeme=0; ischeme<tmpPa.countDataSchemes(); ischeme++) {
                    String scheme = tmpPa.getDataScheme(ischeme);
                    if (scheme != null && !scheme.isEmpty()) {
                        Intent finalIntent = new Intent(intent);
                        builder.scheme(scheme);
                        finalIntent.setDataAndType(builder.build(), mimeType);
                        applyDefaultPreferredActivityLPw(service, finalIntent, flags, cn,
                                scheme, null, null, null, userId);
                    }
                }
            } else {
                Intent finalIntent = new Intent(intent);
                finalIntent.setType(mimeType);
                applyDefaultPreferredActivityLPw(service, finalIntent, flags, cn,
                        null, null, null, null, userId);
            }
            doNonData = false;
        }

        if (doNonData) {
            applyDefaultPreferredActivityLPw(service, intent, flags, cn,
                    null, null, null, null, userId);
        }
    }

    private void applyDefaultPreferredActivityLPw(PackageManagerService service,
            Intent intent, int flags, ComponentName cn, String scheme, PatternMatcher ssp,
            IntentFilter.AuthorityEntry auth, PatternMatcher path, int userId) {
        flags = service.updateFlagsForResolve(flags, userId, intent);
        List<ResolveInfo> ri = service.mActivities.queryIntent(intent,
                intent.getType(), flags, 0);
        if (PackageManagerService.DEBUG_PREFERRED) Log.d(TAG, "Queried " + intent
                + " results: " + ri);
        int systemMatch = 0;
        int thirdPartyMatch = 0;
        if (ri != null && ri.size() > 1) {
            boolean haveAct = false;
            ComponentName haveNonSys = null;
            ComponentName[] set = new ComponentName[ri.size()];
            for (int i=0; i<ri.size(); i++) {
                ActivityInfo ai = ri.get(i).activityInfo;
                set[i] = new ComponentName(ai.packageName, ai.name);
                if ((ai.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) == 0) {
                    if (ri.get(i).match >= thirdPartyMatch) {
                        // Keep track of the best match we find of all third
                        // party apps, for use later to determine if we actually
                        // want to set a preferred app for this intent.
                        if (PackageManagerService.DEBUG_PREFERRED) Log.d(TAG, "Result "
                                + ai.packageName + "/" + ai.name + ": non-system!");
                        haveNonSys = set[i];
                        break;
                    }
                } else if (cn.getPackageName().equals(ai.packageName)
                        && cn.getClassName().equals(ai.name)) {
                    if (PackageManagerService.DEBUG_PREFERRED) Log.d(TAG, "Result "
                            + ai.packageName + "/" + ai.name + ": default!");
                    haveAct = true;
                    systemMatch = ri.get(i).match;
                } else {
                    if (PackageManagerService.DEBUG_PREFERRED) Log.d(TAG, "Result "
                            + ai.packageName + "/" + ai.name + ": skipped");
                }
            }
            if (haveNonSys != null && thirdPartyMatch < systemMatch) {
                // If we have a matching third party app, but its match is not as
                // good as the built-in system app, then we don't want to actually
                // consider it a match because presumably the built-in app is still
                // the thing we want users to see by default.
                haveNonSys = null;
            }
            if (haveAct && haveNonSys == null) {
                IntentFilter filter = new IntentFilter();
                if (intent.getAction() != null) {
                    filter.addAction(intent.getAction());
                }
                if (intent.getCategories() != null) {
                    for (String cat : intent.getCategories()) {
                        filter.addCategory(cat);
                    }
                }
                if ((flags & MATCH_DEFAULT_ONLY) != 0) {
                    filter.addCategory(Intent.CATEGORY_DEFAULT);
                }
                if (scheme != null) {
                    filter.addDataScheme(scheme);
                }
                if (ssp != null) {
                    filter.addDataSchemeSpecificPart(ssp.getPath(), ssp.getType());
                }
                if (auth != null) {
                    filter.addDataAuthority(auth);
                }
                if (path != null) {
                    filter.addDataPath(path);
                }
                if (intent.getType() != null) {
                    try {
                        filter.addDataType(intent.getType());
                    } catch (IntentFilter.MalformedMimeTypeException ex) {
                        Slog.w(TAG, "Malformed mimetype " + intent.getType() + " for " + cn);
                    }
                }
                PreferredActivity pa = new PreferredActivity(filter, systemMatch, set, cn, true);
                editPreferredActivitiesLPw(userId).addFilter(pa);
            } else if (haveNonSys == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("No component ");
                sb.append(cn.flattenToShortString());
                sb.append(" found setting preferred ");
                sb.append(intent);
                sb.append("; possible matches are ");
                for (int i=0; i<set.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(set[i].flattenToShortString());
                }
                Slog.w(TAG, sb.toString());
            } else {
                Slog.i(TAG, "Not setting preferred " + intent + "; found third party match "
                        + haveNonSys.flattenToShortString());
            }
        } else {
            Slog.w(TAG, "No potential matches found for " + intent + " while setting preferred "
                    + cn.flattenToShortString());
        }
    }

    private void readDefaultPreferredActivitiesLPw(PackageManagerService service,
            XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                PreferredActivity tmpPa = new PreferredActivity(parser);
                if (tmpPa.mPref.getParseError() == null) {
                    applyDefaultPreferredActivityLPw(service, tmpPa, tmpPa.mPref.mComponent,
                            userId);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Error in package manager settings: <preferred-activity> "
                                    + tmpPa.mPref.getParseError() + " at "
                                    + parser.getPositionDescription());
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <preferred-activities>: " + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private int readInt(XmlPullParser parser, String ns, String name, int defValue) {
        String v = parser.getAttributeValue(ns, name);
        try {
            if (v == null) {
                return defValue;
            }
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            PackageManagerService.reportSettingsProblem(Log.WARN,
                    "Error in package manager settings: attribute " + name
                            + " has bad integer value " + v + " at "
                            + parser.getPositionDescription());
        }
        return defValue;
    }

    private void readPermissionsLPw(ArrayMap<String, BasePermission> out, XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            final String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                final String name = parser.getAttributeValue(null, ATTR_NAME);
                final String sourcePackage = parser.getAttributeValue(null, "package");
                final String ptype = parser.getAttributeValue(null, "type");
                if (name != null && sourcePackage != null) {
                    final boolean dynamic = "dynamic".equals(ptype);
                    BasePermission bp = out.get(name);
                    // If the permission is builtin, do not clobber it.
                    if (bp == null || bp.type != BasePermission.TYPE_BUILTIN) {
                        bp = new BasePermission(name.intern(), sourcePackage,
                                dynamic ? BasePermission.TYPE_DYNAMIC : BasePermission.TYPE_NORMAL);
                    }
                    bp.protectionLevel = readInt(parser, null, "protection",
                            PermissionInfo.PROTECTION_NORMAL);
                    bp.protectionLevel = PermissionInfo.fixProtectionLevel(bp.protectionLevel);
                    bp.allowViaWhitelist = readInt(parser, null,
                            "allowViaWhitelist", 0) == 1;
                    if (dynamic) {
                        PermissionInfo pi = new PermissionInfo();
                        pi.packageName = sourcePackage.intern();
                        pi.name = name.intern();
                        pi.icon = readInt(parser, null, "icon", 0);
                        pi.nonLocalizedLabel = parser.getAttributeValue(null, "label");
                        pi.protectionLevel = bp.protectionLevel;
                        pi.allowViaWhitelist = bp.allowViaWhitelist;
                        bp.pendingInfo = pi;
                    }
                    out.put(bp.name, bp);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Error in package manager settings: permissions has" + " no name at "
                                    + parser.getPositionDescription());
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element reading permissions: " + parser.getName() + " at "
                                + parser.getPositionDescription());
            }
            XmlUtils.skipCurrentTag(parser);
        }
    }

    private void readDisabledSysPackageLPw(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        String name = parser.getAttributeValue(null, ATTR_NAME);
        String realName = parser.getAttributeValue(null, "realName");
        String codePathStr = parser.getAttributeValue(null, "codePath");
        String resourcePathStr = parser.getAttributeValue(null, "resourcePath");

        String legacyCpuAbiStr = parser.getAttributeValue(null, "requiredCpuAbi");
        String legacyNativeLibraryPathStr = parser.getAttributeValue(null, "nativeLibraryPath");

        String parentPackageName = parser.getAttributeValue(null, "parentPackageName");

        String primaryCpuAbiStr = parser.getAttributeValue(null, "primaryCpuAbi");
        String secondaryCpuAbiStr = parser.getAttributeValue(null, "secondaryCpuAbi");
        String cpuAbiOverrideStr = parser.getAttributeValue(null, "cpuAbiOverride");

        if (primaryCpuAbiStr == null && legacyCpuAbiStr != null) {
            primaryCpuAbiStr = legacyCpuAbiStr;
        }

        if (resourcePathStr == null) {
            resourcePathStr = codePathStr;
        }
        String version = parser.getAttributeValue(null, "version");
        int versionCode = 0;
        if (version != null) {
            try {
                versionCode = Integer.parseInt(version);
            } catch (NumberFormatException e) {
            }
        }

        int pkgFlags = 0;
        int pkgPrivateFlags = 0;
        pkgFlags |= ApplicationInfo.FLAG_SYSTEM;
        final File codePathFile = new File(codePathStr);
        if (PackageManagerService.locationIsPrivileged(codePathFile)) {
            pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
        }
        PackageSetting ps = new PackageSetting(name, realName, codePathFile,
                new File(resourcePathStr), legacyNativeLibraryPathStr, primaryCpuAbiStr,
                secondaryCpuAbiStr, cpuAbiOverrideStr, versionCode, pkgFlags, pkgPrivateFlags,
                parentPackageName, null);
        String timeStampStr = parser.getAttributeValue(null, "ft");
        if (timeStampStr != null) {
            try {
                long timeStamp = Long.parseLong(timeStampStr, 16);
                ps.setTimeStamp(timeStamp);
            } catch (NumberFormatException e) {
            }
        } else {
            timeStampStr = parser.getAttributeValue(null, "ts");
            if (timeStampStr != null) {
                try {
                    long timeStamp = Long.parseLong(timeStampStr);
                    ps.setTimeStamp(timeStamp);
                } catch (NumberFormatException e) {
                }
            }
        }
        timeStampStr = parser.getAttributeValue(null, "it");
        if (timeStampStr != null) {
            try {
                ps.firstInstallTime = Long.parseLong(timeStampStr, 16);
            } catch (NumberFormatException e) {
            }
        }
        timeStampStr = parser.getAttributeValue(null, "ut");
        if (timeStampStr != null) {
            try {
                ps.lastUpdateTime = Long.parseLong(timeStampStr, 16);
            } catch (NumberFormatException e) {
            }
        }
        String idStr = parser.getAttributeValue(null, "userId");
        ps.appId = idStr != null ? Integer.parseInt(idStr) : 0;
        if (ps.appId <= 0) {
            String sharedIdStr = parser.getAttributeValue(null, "sharedUserId");
            ps.appId = sharedIdStr != null ? Integer.parseInt(sharedIdStr) : 0;
        }

        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            if (parser.getName().equals(TAG_PERMISSIONS)) {
                readInstallPermissionsLPr(parser, ps.getPermissionsState());
            } else if (parser.getName().equals(TAG_CHILD_PACKAGE)) {
                String childPackageName = parser.getAttributeValue(null, ATTR_NAME);
                if (ps.childPackageNames == null) {
                    ps.childPackageNames = new ArrayList<>();
                }
                ps.childPackageNames.add(childPackageName);
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <updated-package>: " + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }

        mDisabledSysPackages.put(name, ps);
    }

    private static int PRE_M_APP_INFO_FLAG_HIDDEN = 1<<27;
    private static int PRE_M_APP_INFO_FLAG_CANT_SAVE_STATE = 1<<28;
    private static int PRE_M_APP_INFO_FLAG_FORWARD_LOCK = 1<<29;
    private static int PRE_M_APP_INFO_FLAG_PRIVILEGED = 1<<30;

    private void readPackageLPw(XmlPullParser parser) throws XmlPullParserException, IOException {
        String name = null;
        String realName = null;
        String idStr = null;
        String sharedIdStr = null;
        String codePathStr = null;
        String resourcePathStr = null;
        String legacyCpuAbiString = null;
        String legacyNativeLibraryPathStr = null;
        String primaryCpuAbiString = null;
        String secondaryCpuAbiString = null;
        String cpuAbiOverrideString = null;
        String systemStr = null;
        String installerPackageName = null;
        String isOrphaned = null;
        String volumeUuid = null;
        String uidError = null;
        int pkgFlags = 0;
        int pkgPrivateFlags = 0;
        long timeStamp = 0;
        long firstInstallTime = 0;
        long lastUpdateTime = 0;
        PackageSettingBase packageSetting = null;
        String version = null;
        int versionCode = 0;
        String parentPackageName;
        try {
            name = parser.getAttributeValue(null, ATTR_NAME);
            realName = parser.getAttributeValue(null, "realName");
            idStr = parser.getAttributeValue(null, "userId");
            uidError = parser.getAttributeValue(null, "uidError");
            sharedIdStr = parser.getAttributeValue(null, "sharedUserId");
            codePathStr = parser.getAttributeValue(null, "codePath");
            resourcePathStr = parser.getAttributeValue(null, "resourcePath");

            legacyCpuAbiString = parser.getAttributeValue(null, "requiredCpuAbi");

            parentPackageName = parser.getAttributeValue(null, "parentPackageName");

            legacyNativeLibraryPathStr = parser.getAttributeValue(null, "nativeLibraryPath");
            primaryCpuAbiString = parser.getAttributeValue(null, "primaryCpuAbi");
            secondaryCpuAbiString = parser.getAttributeValue(null, "secondaryCpuAbi");
            cpuAbiOverrideString = parser.getAttributeValue(null, "cpuAbiOverride");

            if (primaryCpuAbiString == null && legacyCpuAbiString != null) {
                primaryCpuAbiString = legacyCpuAbiString;
            }

            version = parser.getAttributeValue(null, "version");
            if (version != null) {
                try {
                    versionCode = Integer.parseInt(version);
                } catch (NumberFormatException e) {
                }
            }
            installerPackageName = parser.getAttributeValue(null, "installer");
            isOrphaned = parser.getAttributeValue(null, "isOrphaned");
            volumeUuid = parser.getAttributeValue(null, "volumeUuid");

            systemStr = parser.getAttributeValue(null, "publicFlags");
            if (systemStr != null) {
                try {
                    pkgFlags = Integer.parseInt(systemStr);
                } catch (NumberFormatException e) {
                }
                systemStr = parser.getAttributeValue(null, "privateFlags");
                if (systemStr != null) {
                    try {
                        pkgPrivateFlags = Integer.parseInt(systemStr);
                    } catch (NumberFormatException e) {
                    }
                }
            } else {
                // Pre-M -- both public and private flags were stored in one "flags" field.
                systemStr = parser.getAttributeValue(null, "flags");
                if (systemStr != null) {
                    try {
                        pkgFlags = Integer.parseInt(systemStr);
                    } catch (NumberFormatException e) {
                    }
                    if ((pkgFlags & PRE_M_APP_INFO_FLAG_HIDDEN) != 0) {
                        pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_HIDDEN;
                    }
                    if ((pkgFlags & PRE_M_APP_INFO_FLAG_CANT_SAVE_STATE) != 0) {
                        pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE;
                    }
                    if ((pkgFlags & PRE_M_APP_INFO_FLAG_FORWARD_LOCK) != 0) {
                        pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_FORWARD_LOCK;
                    }
                    if ((pkgFlags & PRE_M_APP_INFO_FLAG_PRIVILEGED) != 0) {
                        pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
                    }
                    pkgFlags &= ~(PRE_M_APP_INFO_FLAG_HIDDEN
                            | PRE_M_APP_INFO_FLAG_CANT_SAVE_STATE
                            | PRE_M_APP_INFO_FLAG_FORWARD_LOCK
                            | PRE_M_APP_INFO_FLAG_PRIVILEGED);
                } else {
                    // For backward compatibility
                    systemStr = parser.getAttributeValue(null, "system");
                    if (systemStr != null) {
                        pkgFlags |= ("true".equalsIgnoreCase(systemStr)) ? ApplicationInfo.FLAG_SYSTEM
                                : 0;
                    } else {
                        // Old settings that don't specify system... just treat
                        // them as system, good enough.
                        pkgFlags |= ApplicationInfo.FLAG_SYSTEM;
                    }
                }
            }
            String timeStampStr = parser.getAttributeValue(null, "ft");
            if (timeStampStr != null) {
                try {
                    timeStamp = Long.parseLong(timeStampStr, 16);
                } catch (NumberFormatException e) {
                }
            } else {
                timeStampStr = parser.getAttributeValue(null, "ts");
                if (timeStampStr != null) {
                    try {
                        timeStamp = Long.parseLong(timeStampStr);
                    } catch (NumberFormatException e) {
                    }
                }
            }
            timeStampStr = parser.getAttributeValue(null, "it");
            if (timeStampStr != null) {
                try {
                    firstInstallTime = Long.parseLong(timeStampStr, 16);
                } catch (NumberFormatException e) {
                }
            }
            timeStampStr = parser.getAttributeValue(null, "ut");
            if (timeStampStr != null) {
                try {
                    lastUpdateTime = Long.parseLong(timeStampStr, 16);
                } catch (NumberFormatException e) {
                }
            }
            if (PackageManagerService.DEBUG_SETTINGS)
                Log.v(PackageManagerService.TAG, "Reading package: " + name + " userId=" + idStr
                        + " sharedUserId=" + sharedIdStr);
            int userId = idStr != null ? Integer.parseInt(idStr) : 0;
            if (resourcePathStr == null) {
                resourcePathStr = codePathStr;
            }
            if (realName != null) {
                realName = realName.intern();
            }
            if (name == null) {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Error in package manager settings: <package> has no name at "
                                + parser.getPositionDescription());
            } else if (codePathStr == null) {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Error in package manager settings: <package> has no codePath at "
                                + parser.getPositionDescription());
            } else if (userId > 0) {
                packageSetting = addPackageLPw(name.intern(), realName, new File(codePathStr),
                        new File(resourcePathStr), legacyNativeLibraryPathStr, primaryCpuAbiString,
                        secondaryCpuAbiString, cpuAbiOverrideString, userId, versionCode, pkgFlags,
                        pkgPrivateFlags, parentPackageName, null);
                if (PackageManagerService.DEBUG_SETTINGS)
                    Log.i(PackageManagerService.TAG, "Reading package " + name + ": userId="
                            + userId + " pkg=" + packageSetting);
                if (packageSetting == null) {
                    PackageManagerService.reportSettingsProblem(Log.ERROR, "Failure adding uid "
                            + userId + " while parsing settings at "
                            + parser.getPositionDescription());
                } else {
                    packageSetting.setTimeStamp(timeStamp);
                    packageSetting.firstInstallTime = firstInstallTime;
                    packageSetting.lastUpdateTime = lastUpdateTime;
                }
            } else if (sharedIdStr != null) {
                userId = sharedIdStr != null ? Integer.parseInt(sharedIdStr) : 0;
                if (userId > 0) {
                    packageSetting = new PendingPackage(name.intern(), realName, new File(
                            codePathStr), new File(resourcePathStr), legacyNativeLibraryPathStr,
                            primaryCpuAbiString, secondaryCpuAbiString, cpuAbiOverrideString,
                            userId, versionCode, pkgFlags, pkgPrivateFlags, parentPackageName,
                            null);
                    packageSetting.setTimeStamp(timeStamp);
                    packageSetting.firstInstallTime = firstInstallTime;
                    packageSetting.lastUpdateTime = lastUpdateTime;
                    mPendingPackages.add((PendingPackage) packageSetting);
                    if (PackageManagerService.DEBUG_SETTINGS)
                        Log.i(PackageManagerService.TAG, "Reading package " + name
                                + ": sharedUserId=" + userId + " pkg=" + packageSetting);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Error in package manager settings: package " + name
                                    + " has bad sharedId " + sharedIdStr + " at "
                                    + parser.getPositionDescription());
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Error in package manager settings: package " + name + " has bad userId "
                                + idStr + " at " + parser.getPositionDescription());
            }
        } catch (NumberFormatException e) {
            PackageManagerService.reportSettingsProblem(Log.WARN,
                    "Error in package manager settings: package " + name + " has bad userId "
                            + idStr + " at " + parser.getPositionDescription());
        }
        if (packageSetting != null) {
            packageSetting.uidError = "true".equals(uidError);
            packageSetting.installerPackageName = installerPackageName;
            packageSetting.isOrphaned = "true".equals(isOrphaned);
            packageSetting.volumeUuid = volumeUuid;
            packageSetting.legacyNativeLibraryPathString = legacyNativeLibraryPathStr;
            packageSetting.primaryCpuAbiString = primaryCpuAbiString;
            packageSetting.secondaryCpuAbiString = secondaryCpuAbiString;
            // Handle legacy string here for single-user mode
            final String enabledStr = parser.getAttributeValue(null, ATTR_ENABLED);
            if (enabledStr != null) {
                try {
                    packageSetting.setEnabled(Integer.parseInt(enabledStr), 0 /* userId */, null);
                } catch (NumberFormatException e) {
                    if (enabledStr.equalsIgnoreCase("true")) {
                        packageSetting.setEnabled(COMPONENT_ENABLED_STATE_ENABLED, 0, null);
                    } else if (enabledStr.equalsIgnoreCase("false")) {
                        packageSetting.setEnabled(COMPONENT_ENABLED_STATE_DISABLED, 0, null);
                    } else if (enabledStr.equalsIgnoreCase("default")) {
                        packageSetting.setEnabled(COMPONENT_ENABLED_STATE_DEFAULT, 0, null);
                    } else {
                        PackageManagerService.reportSettingsProblem(Log.WARN,
                                "Error in package manager settings: package " + name
                                        + " has bad enabled value: " + idStr + " at "
                                        + parser.getPositionDescription());
                    }
                }
            } else {
                packageSetting.setEnabled(COMPONENT_ENABLED_STATE_DEFAULT, 0, null);
            }

            if (installerPackageName != null) {
                mInstallerPackages.add(installerPackageName);
            }

            final String installStatusStr = parser.getAttributeValue(null, "installStatus");
            if (installStatusStr != null) {
                if (installStatusStr.equalsIgnoreCase("false")) {
                    packageSetting.installStatus = PackageSettingBase.PKG_INSTALL_INCOMPLETE;
                } else {
                    packageSetting.installStatus = PackageSettingBase.PKG_INSTALL_COMPLETE;
                }
            }

            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                // Legacy
                if (tagName.equals(TAG_DISABLED_COMPONENTS)) {
                    readDisabledComponentsLPw(packageSetting, parser, 0);
                } else if (tagName.equals(TAG_ENABLED_COMPONENTS)) {
                    readEnabledComponentsLPw(packageSetting, parser, 0);
                } else if (tagName.equals("sigs")) {
                    packageSetting.signatures.readXml(parser, mPastSignatures);
                } else if (tagName.equals(TAG_PERMISSIONS)) {
                    readInstallPermissionsLPr(parser,
                            packageSetting.getPermissionsState());
                    packageSetting.installPermissionsFixed = true;
                } else if (tagName.equals("proper-signing-keyset")) {
                    long id = Long.parseLong(parser.getAttributeValue(null, "identifier"));
                    Integer refCt = mKeySetRefs.get(id);
                    if (refCt != null) {
                        mKeySetRefs.put(id, refCt + 1);
                    } else {
                        mKeySetRefs.put(id, 1);
                    }
                    packageSetting.keySetData.setProperSigningKeySet(id);
                } else if (tagName.equals("signing-keyset")) {
                    // from v1 of keysetmanagerservice - no longer used
                } else if (tagName.equals("upgrade-keyset")) {
                    long id = Long.parseLong(parser.getAttributeValue(null, "identifier"));
                    packageSetting.keySetData.addUpgradeKeySetById(id);
                } else if (tagName.equals("defined-keyset")) {
                    long id = Long.parseLong(parser.getAttributeValue(null, "identifier"));
                    String alias = parser.getAttributeValue(null, "alias");
                    Integer refCt = mKeySetRefs.get(id);
                    if (refCt != null) {
                        mKeySetRefs.put(id, refCt + 1);
                    } else {
                        mKeySetRefs.put(id, 1);
                    }
                    packageSetting.keySetData.addDefinedKeySet(id, alias);
                } else if (tagName.equals(TAG_DOMAIN_VERIFICATION)) {
                    readDomainVerificationLPw(parser, packageSetting);
                } else if (tagName.equals(TAG_CHILD_PACKAGE)) {
                    String childPackageName = parser.getAttributeValue(null, ATTR_NAME);
                    if (packageSetting.childPackageNames == null) {
                        packageSetting.childPackageNames = new ArrayList<>();
                    }
                    packageSetting.childPackageNames.add(childPackageName);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Unknown element under <package>: " + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }
        } else {
            XmlUtils.skipCurrentTag(parser);
        }
    }

    private void readDisabledComponentsLPw(PackageSettingBase packageSetting, XmlPullParser parser,
            int userId) throws IOException, XmlPullParserException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                String name = parser.getAttributeValue(null, ATTR_NAME);
                if (name != null) {
                    packageSetting.addDisabledComponent(name.intern(), userId);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Error in package manager settings: <disabled-components> has"
                                    + " no name at " + parser.getPositionDescription());
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <disabled-components>: " + parser.getName());
            }
            XmlUtils.skipCurrentTag(parser);
        }
    }

    private void readEnabledComponentsLPw(PackageSettingBase packageSetting, XmlPullParser parser,
            int userId) throws IOException, XmlPullParserException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_ITEM)) {
                String name = parser.getAttributeValue(null, ATTR_NAME);
                if (name != null) {
                    packageSetting.addEnabledComponent(name.intern(), userId);
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Error in package manager settings: <enabled-components> has"
                                    + " no name at " + parser.getPositionDescription());
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <enabled-components>: " + parser.getName());
            }
            XmlUtils.skipCurrentTag(parser);
        }
    }

    private void readSharedUserLPw(XmlPullParser parser) throws XmlPullParserException,IOException {
        String name = null;
        String idStr = null;
        int pkgFlags = 0;
        int pkgPrivateFlags = 0;
        SharedUserSetting su = null;
        try {
            name = parser.getAttributeValue(null, ATTR_NAME);
            idStr = parser.getAttributeValue(null, "userId");
            int userId = idStr != null ? Integer.parseInt(idStr) : 0;
            if ("true".equals(parser.getAttributeValue(null, "system"))) {
                pkgFlags |= ApplicationInfo.FLAG_SYSTEM;
            }
            if (name == null) {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Error in package manager settings: <shared-user> has no name at "
                                + parser.getPositionDescription());
            } else if (userId == 0) {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Error in package manager settings: shared-user " + name
                                + " has bad userId " + idStr + " at "
                                + parser.getPositionDescription());
            } else {
                if ((su = addSharedUserLPw(name.intern(), userId, pkgFlags, pkgPrivateFlags))
                        == null) {
                    PackageManagerService
                            .reportSettingsProblem(Log.ERROR, "Occurred while parsing settings at "
                                    + parser.getPositionDescription());
                }
            }
        } catch (NumberFormatException e) {
            PackageManagerService.reportSettingsProblem(Log.WARN,
                    "Error in package manager settings: package " + name + " has bad userId "
                            + idStr + " at " + parser.getPositionDescription());
        }

        if (su != null) {
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("sigs")) {
                    su.signatures.readXml(parser, mPastSignatures);
                } else if (tagName.equals("perms")) {
                    readInstallPermissionsLPr(parser, su.getPermissionsState());
                } else {
                    PackageManagerService.reportSettingsProblem(Log.WARN,
                            "Unknown element under <shared-user>: " + parser.getName());
                    XmlUtils.skipCurrentTag(parser);
                }
            }
        } else {
            XmlUtils.skipCurrentTag(parser);
        }
    }

    void createNewUserLI(@NonNull PackageManagerService service, @NonNull Installer installer,
            int userHandle) {
        String[] volumeUuids;
        String[] names;
        int[] appIds;
        String[] seinfos;
        int[] targetSdkVersions;
        int packagesCount;
        synchronized (mPackages) {
            Collection<PackageSetting> packages = mPackages.values();
            packagesCount = packages.size();
            volumeUuids = new String[packagesCount];
            names = new String[packagesCount];
            appIds = new int[packagesCount];
            seinfos = new String[packagesCount];
            targetSdkVersions = new int[packagesCount];
            Iterator<PackageSetting> packagesIterator = packages.iterator();
            for (int i = 0; i < packagesCount; i++) {
                PackageSetting ps = packagesIterator.next();
                if (ps.pkg == null || ps.pkg.applicationInfo == null) {
                    continue;
                }
                // Only system apps are initially installed.
                ps.setInstalled(ps.isSystem(), userHandle);
                // Need to create a data directory for all apps under this user. Accumulate all
                // required args and call the installer after mPackages lock has been released
                volumeUuids[i] = ps.volumeUuid;
                names[i] = ps.name;
                appIds[i] = ps.appId;
                seinfos[i] = ps.pkg.applicationInfo.seinfo;
                targetSdkVersions[i] = ps.pkg.applicationInfo.targetSdkVersion;
            }
        }
        for (int i = 0; i < packagesCount; i++) {
            if (names[i] == null) {
                continue;
            }
            // TODO: triage flags!
            final int flags = StorageManager.FLAG_STORAGE_CE | StorageManager.FLAG_STORAGE_DE;
            try {
                installer.createAppData(volumeUuids[i], names[i], userHandle, flags, appIds[i],
                        seinfos[i], targetSdkVersions[i]);
            } catch (InstallerException e) {
                Slog.w(TAG, "Failed to prepare app data", e);
            }
        }
        synchronized (mPackages) {
            applyDefaultPreferredAppsLPw(service, userHandle);
        }
    }

    void removeUserLPw(int userId) {
        Set<Entry<String, PackageSetting>> entries = mPackages.entrySet();
        for (Entry<String, PackageSetting> entry : entries) {
            entry.getValue().removeUser(userId);
        }
        mPreferredActivities.remove(userId);
        File file = getUserPackagesStateFile(userId);
        file.delete();
        file = getUserPackagesStateBackupFile(userId);
        file.delete();
        removeCrossProfileIntentFiltersLPw(userId);

        mRuntimePermissionsPersistence.onUserRemovedLPw(userId);

        writePackageListLPr();
    }

    void removeCrossProfileIntentFiltersLPw(int userId) {
        synchronized (mCrossProfileIntentResolvers) {
            // userId is the source user
            if (mCrossProfileIntentResolvers.get(userId) != null) {
                mCrossProfileIntentResolvers.remove(userId);
                writePackageRestrictionsLPr(userId);
            }
            // userId is the target user
            int count = mCrossProfileIntentResolvers.size();
            for (int i = 0; i < count; i++) {
                int sourceUserId = mCrossProfileIntentResolvers.keyAt(i);
                CrossProfileIntentResolver cpir = mCrossProfileIntentResolvers.get(sourceUserId);
                boolean needsWriting = false;
                ArraySet<CrossProfileIntentFilter> cpifs =
                        new ArraySet<CrossProfileIntentFilter>(cpir.filterSet());
                for (CrossProfileIntentFilter cpif : cpifs) {
                    if (cpif.getTargetUserId() == userId) {
                        needsWriting = true;
                        cpir.removeFilter(cpif);
                    }
                }
                if (needsWriting) {
                    writePackageRestrictionsLPr(sourceUserId);
                }
            }
        }
    }

    // This should be called (at least) whenever an application is removed
    private void setFirstAvailableUid(int uid) {
        if (uid > mFirstAvailableUid) {
            mFirstAvailableUid = uid;
        }
    }

    // Returns -1 if we could not find an available UserId to assign
    private int newUserIdLPw(Object obj) {
        // Let's be stupidly inefficient for now...
        final int N = mUserIds.size();
        for (int i = mFirstAvailableUid; i < N; i++) {
            if (mUserIds.get(i) == null) {
                mUserIds.set(i, obj);
                return Process.FIRST_APPLICATION_UID + i;
            }
        }

        // None left?
        if (N > (Process.LAST_APPLICATION_UID-Process.FIRST_APPLICATION_UID)) {
            return -1;
        }

        mUserIds.add(obj);
        return Process.FIRST_APPLICATION_UID + N;
    }

    public VerifierDeviceIdentity getVerifierDeviceIdentityLPw() {
        if (mVerifierDeviceIdentity == null) {
            mVerifierDeviceIdentity = VerifierDeviceIdentity.generate();

            writeLPr();
        }

        return mVerifierDeviceIdentity;
    }

    public boolean hasOtherDisabledSystemPkgWithChildLPr(String parentPackageName,
            String childPackageName) {
        final int packageCount = mDisabledSysPackages.size();
        for (int i = 0; i < packageCount; i++) {
            PackageSetting disabledPs = mDisabledSysPackages.valueAt(i);
            if (disabledPs.childPackageNames == null || disabledPs.childPackageNames.isEmpty()) {
                continue;
            }
            if (disabledPs.name.equals(parentPackageName)) {
                continue;
            }
            final int childCount = disabledPs.childPackageNames.size();
            for (int j = 0; j < childCount; j++) {
                String currChildPackageName = disabledPs.childPackageNames.get(j);
                if (currChildPackageName.equals(childPackageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public PackageSetting getDisabledSystemPkgLPr(String name) {
        PackageSetting ps = mDisabledSysPackages.get(name);
        return ps;
    }

    private String compToString(ArraySet<String> cmp) {
        return cmp != null ? Arrays.toString(cmp.toArray()) : "[]";
    }

    boolean isEnabledAndMatchLPr(ComponentInfo componentInfo, int flags, int userId) {
        final PackageSetting ps = mPackages.get(componentInfo.packageName);
        if (ps == null) return false;

        final PackageUserState userState = ps.readUserState(userId);
        return userState.isMatch(componentInfo, flags);
    }

    String getInstallerPackageNameLPr(String packageName) {
        final PackageSetting pkg = mPackages.get(packageName);
        if (pkg == null) {
            throw new IllegalArgumentException("Unknown package: " + packageName);
        }
        return pkg.installerPackageName;
    }

    boolean isOrphaned(String packageName) {
        final PackageSetting pkg = mPackages.get(packageName);
        if (pkg == null) {
            throw new IllegalArgumentException("Unknown package: " + packageName);
        }
        return pkg.isOrphaned;
    }

    int getApplicationEnabledSettingLPr(String packageName, int userId) {
        final PackageSetting pkg = mPackages.get(packageName);
        if (pkg == null) {
            throw new IllegalArgumentException("Unknown package: " + packageName);
        }
        return pkg.getEnabled(userId);
    }

    int getComponentEnabledSettingLPr(ComponentName componentName, int userId) {
        final String packageName = componentName.getPackageName();
        final PackageSetting pkg = mPackages.get(packageName);
        if (pkg == null) {
            throw new IllegalArgumentException("Unknown component: " + componentName);
        }
        final String classNameStr = componentName.getClassName();
        return pkg.getCurrentEnabledStateLPr(classNameStr, userId);
    }

    boolean wasPackageEverLaunchedLPr(String packageName, int userId) {
        final PackageSetting pkgSetting = mPackages.get(packageName);
        if (pkgSetting == null) {
            throw new IllegalArgumentException("Unknown package: " + packageName);
        }
        return !pkgSetting.getNotLaunched(userId);
    }

    boolean setPackageStoppedStateLPw(PackageManagerService pm, String packageName,
            boolean stopped, boolean allowedByPermission, int uid, int userId) {
        int appId = UserHandle.getAppId(uid);
        final PackageSetting pkgSetting = mPackages.get(packageName);
        if (pkgSetting == null) {
            throw new IllegalArgumentException("Unknown package: " + packageName);
        }
        if (!allowedByPermission && (appId != pkgSetting.appId)) {
            throw new SecurityException(
                    "Permission Denial: attempt to change stopped state from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + uid + ", package uid=" + pkgSetting.appId);
        }
        if (DEBUG_STOPPED) {
            if (stopped) {
                RuntimeException e = new RuntimeException("here");
                e.fillInStackTrace();
                Slog.i(TAG, "Stopping package " + packageName, e);
            }
        }
        if (pkgSetting.getStopped(userId) != stopped) {
            pkgSetting.setStopped(stopped, userId);
            // pkgSetting.pkg.mSetStopped = stopped;
            if (pkgSetting.getNotLaunched(userId)) {
                if (pkgSetting.installerPackageName != null) {
                    pm.notifyFirstLaunch(pkgSetting.name, pkgSetting.installerPackageName, userId);
                }
                pkgSetting.setNotLaunched(false, userId);
            }
            return true;
        }
        return false;
    }

    List<UserInfo> getAllUsers() {
        long id = Binder.clearCallingIdentity();
        try {
            return UserManagerService.getInstance().getUsers(false);
        } catch (NullPointerException npe) {
            // packagemanager not yet initialized
        } finally {
            Binder.restoreCallingIdentity(id);
        }
        return null;
    }

    /**
     * Return all {@link PackageSetting} that are actively installed on the
     * given {@link VolumeInfo#fsUuid}.
     */
    List<PackageSetting> getVolumePackagesLPr(String volumeUuid) {
        ArrayList<PackageSetting> res = new ArrayList<>();
        for (int i = 0; i < mPackages.size(); i++) {
            final PackageSetting setting = mPackages.valueAt(i);
            if (Objects.equals(volumeUuid, setting.volumeUuid)) {
                res.add(setting);
            }
        }
        return res;
    }

    static void printFlags(PrintWriter pw, int val, Object[] spec) {
        pw.print("[ ");
        for (int i=0; i<spec.length; i+=2) {
            int mask = (Integer)spec[i];
            if ((val & mask) != 0) {
                pw.print(spec[i+1]);
                pw.print(" ");
            }
        }
        pw.print("]");
    }

    static final Object[] FLAG_DUMP_SPEC = new Object[] {
        ApplicationInfo.FLAG_SYSTEM, "SYSTEM",
        ApplicationInfo.FLAG_DEBUGGABLE, "DEBUGGABLE",
        ApplicationInfo.FLAG_HAS_CODE, "HAS_CODE",
        ApplicationInfo.FLAG_PERSISTENT, "PERSISTENT",
        ApplicationInfo.FLAG_FACTORY_TEST, "FACTORY_TEST",
        ApplicationInfo.FLAG_ALLOW_TASK_REPARENTING, "ALLOW_TASK_REPARENTING",
        ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA, "ALLOW_CLEAR_USER_DATA",
        ApplicationInfo.FLAG_UPDATED_SYSTEM_APP, "UPDATED_SYSTEM_APP",
        ApplicationInfo.FLAG_TEST_ONLY, "TEST_ONLY",
        ApplicationInfo.FLAG_VM_SAFE_MODE, "VM_SAFE_MODE",
        ApplicationInfo.FLAG_ALLOW_BACKUP, "ALLOW_BACKUP",
        ApplicationInfo.FLAG_KILL_AFTER_RESTORE, "KILL_AFTER_RESTORE",
        ApplicationInfo.FLAG_RESTORE_ANY_VERSION, "RESTORE_ANY_VERSION",
        ApplicationInfo.FLAG_EXTERNAL_STORAGE, "EXTERNAL_STORAGE",
        ApplicationInfo.FLAG_LARGE_HEAP, "LARGE_HEAP",
    };

    static final Object[] PRIVATE_FLAG_DUMP_SPEC = new Object[] {
        ApplicationInfo.PRIVATE_FLAG_HIDDEN, "HIDDEN",
        ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE, "CANT_SAVE_STATE",
        ApplicationInfo.PRIVATE_FLAG_FORWARD_LOCK, "FORWARD_LOCK",
        ApplicationInfo.PRIVATE_FLAG_PRIVILEGED, "PRIVILEGED",
        ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS, "HAS_DOMAIN_URLS",
        ApplicationInfo.PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE, "DEFAULT_TO_DEVICE_PROTECTED_STORAGE",
        ApplicationInfo.PRIVATE_FLAG_DIRECT_BOOT_AWARE, "DIRECT_BOOT_AWARE",
        ApplicationInfo.PRIVATE_FLAG_AUTOPLAY, "AUTOPLAY",
        ApplicationInfo.PRIVATE_FLAG_PARTIALLY_DIRECT_BOOT_AWARE, "PARTIALLY_DIRECT_BOOT_AWARE",
        ApplicationInfo.PRIVATE_FLAG_EPHEMERAL, "EPHEMERAL",
        ApplicationInfo.PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER, "REQUIRED_FOR_SYSTEM_USER",
        ApplicationInfo.PRIVATE_FLAG_RESIZEABLE_ACTIVITIES, "RESIZEABLE_ACTIVITIES",
        ApplicationInfo.PRIVATE_FLAG_BACKUP_IN_FOREGROUND, "BACKUP_IN_FOREGROUND",
    };

    void dumpVersionLPr(IndentingPrintWriter pw) {
        pw.increaseIndent();
        for (int i= 0; i < mVersion.size(); i++) {
            final String volumeUuid = mVersion.keyAt(i);
            final VersionInfo ver = mVersion.valueAt(i);
            if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, volumeUuid)) {
                pw.println("Internal:");
            } else if (Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, volumeUuid)) {
                pw.println("External:");
            } else {
                pw.println("UUID " + volumeUuid + ":");
            }
            pw.increaseIndent();
            pw.printPair("sdkVersion", ver.sdkVersion);
            pw.printPair("databaseVersion", ver.databaseVersion);
            pw.println();
            pw.printPair("fingerprint", ver.fingerprint);
            pw.println();
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
    }

    void dumpPackageLPr(PrintWriter pw, String prefix, String checkinTag,
            ArraySet<String> permissionNames, PackageSetting ps, SimpleDateFormat sdf,
            Date date, List<UserInfo> users, boolean dumpAll) {
        if (checkinTag != null) {
            pw.print(checkinTag);
            pw.print(",");
            pw.print(ps.realName != null ? ps.realName : ps.name);
            pw.print(",");
            pw.print(ps.appId);
            pw.print(",");
            pw.print(ps.versionCode);
            pw.print(",");
            pw.print(ps.firstInstallTime);
            pw.print(",");
            pw.print(ps.lastUpdateTime);
            pw.print(",");
            pw.print(ps.installerPackageName != null ? ps.installerPackageName : "?");
            pw.println();
            if (ps.pkg != null) {
                pw.print(checkinTag); pw.print("-"); pw.print("splt,");
                pw.print("base,");
                pw.println(ps.pkg.baseRevisionCode);
                if (ps.pkg.splitNames != null) {
                    for (int i = 0; i < ps.pkg.splitNames.length; i++) {
                        pw.print(checkinTag); pw.print("-"); pw.print("splt,");
                        pw.print(ps.pkg.splitNames[i]); pw.print(",");
                        pw.println(ps.pkg.splitRevisionCodes[i]);
                    }
                }
            }
            for (UserInfo user : users) {
                pw.print(checkinTag);
                pw.print("-");
                pw.print("usr");
                pw.print(",");
                pw.print(user.id);
                pw.print(",");
                pw.print(ps.getInstalled(user.id) ? "I" : "i");
                pw.print(ps.getHidden(user.id) ? "B" : "b");
                pw.print(ps.getSuspended(user.id) ? "SU" : "su");
                pw.print(ps.getStopped(user.id) ? "S" : "s");
                pw.print(ps.getNotLaunched(user.id) ? "l" : "L");
                pw.print(",");
                pw.print(ps.getEnabled(user.id));
                String lastDisabledAppCaller = ps.getLastDisabledAppCaller(user.id);
                pw.print(",");
                pw.print(lastDisabledAppCaller != null ? lastDisabledAppCaller : "?");
                pw.println();
            }
            return;
        }

        pw.print(prefix); pw.print("Package [");
            pw.print(ps.realName != null ? ps.realName : ps.name);
            pw.print("] (");
            pw.print(Integer.toHexString(System.identityHashCode(ps)));
            pw.println("):");

        if (ps.realName != null) {
            pw.print(prefix); pw.print("  compat name=");
            pw.println(ps.name);
        }

        pw.print(prefix); pw.print("  userId="); pw.println(ps.appId);

        if (ps.sharedUser != null) {
            pw.print(prefix); pw.print("  sharedUser="); pw.println(ps.sharedUser);
        }
        pw.print(prefix); pw.print("  pkg="); pw.println(ps.pkg);
        pw.print(prefix); pw.print("  codePath="); pw.println(ps.codePathString);
        if (permissionNames == null) {
            pw.print(prefix); pw.print("  resourcePath="); pw.println(ps.resourcePathString);
            pw.print(prefix); pw.print("  legacyNativeLibraryDir=");
            pw.println(ps.legacyNativeLibraryPathString);
            pw.print(prefix); pw.print("  primaryCpuAbi="); pw.println(ps.primaryCpuAbiString);
            pw.print(prefix); pw.print("  secondaryCpuAbi="); pw.println(ps.secondaryCpuAbiString);
        }
        pw.print(prefix); pw.print("  versionCode="); pw.print(ps.versionCode);
        if (ps.pkg != null) {
            pw.print(" minSdk="); pw.print(ps.pkg.applicationInfo.minSdkVersion);
            pw.print(" targetSdk="); pw.print(ps.pkg.applicationInfo.targetSdkVersion);
        }
        pw.println();
        if (ps.pkg != null) {
            if (ps.pkg.parentPackage != null) {
                PackageParser.Package parentPkg = ps.pkg.parentPackage;
                PackageSetting pps = mPackages.get(parentPkg.packageName);
                if (pps == null || !pps.codePathString.equals(parentPkg.codePath)) {
                    pps = mDisabledSysPackages.get(parentPkg.packageName);
                }
                if (pps != null) {
                    pw.print(prefix); pw.print("  parentPackage=");
                    pw.println(pps.realName != null ? pps.realName : pps.name);
                }
            } else if (ps.pkg.childPackages != null) {
                pw.print(prefix); pw.print("  childPackages=[");
                final int childCount = ps.pkg.childPackages.size();
                for (int i = 0; i < childCount; i++) {
                    PackageParser.Package childPkg = ps.pkg.childPackages.get(i);
                    PackageSetting cps = mPackages.get(childPkg.packageName);
                    if (cps == null || !cps.codePathString.equals(childPkg.codePath)) {
                        cps = mDisabledSysPackages.get(childPkg.packageName);
                    }
                    if (cps != null) {
                        if (i > 0) {
                            pw.print(", ");
                        }
                        pw.print(cps.realName != null ? cps.realName : cps.name);
                    }
                }
                pw.println("]");
            }
            pw.print(prefix); pw.print("  versionName="); pw.println(ps.pkg.mVersionName);
            pw.print(prefix); pw.print("  splits="); dumpSplitNames(pw, ps.pkg); pw.println();
            final int apkSigningVersion = PackageParser.getApkSigningVersion(ps.pkg);
            if (apkSigningVersion != PackageParser.APK_SIGNING_UNKNOWN) {
                pw.print(prefix); pw.print("  apkSigningVersion="); pw.println(apkSigningVersion);
            }
            pw.print(prefix); pw.print("  applicationInfo=");
                pw.println(ps.pkg.applicationInfo.toString());
            pw.print(prefix); pw.print("  flags="); printFlags(pw, ps.pkg.applicationInfo.flags,
                    FLAG_DUMP_SPEC); pw.println();
            if (ps.pkg.applicationInfo.privateFlags != 0) {
                pw.print(prefix); pw.print("  privateFlags="); printFlags(pw,
                        ps.pkg.applicationInfo.privateFlags, PRIVATE_FLAG_DUMP_SPEC); pw.println();
            }
            pw.print(prefix); pw.print("  dataDir="); pw.println(ps.pkg.applicationInfo.dataDir);
            pw.print(prefix); pw.print("  supportsScreens=[");
            boolean first = true;
            if ((ps.pkg.applicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_SMALL_SCREENS) != 0) {
                if (!first)
                    pw.print(", ");
                first = false;
                pw.print("small");
            }
            if ((ps.pkg.applicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_NORMAL_SCREENS) != 0) {
                if (!first)
                    pw.print(", ");
                first = false;
                pw.print("medium");
            }
            if ((ps.pkg.applicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_LARGE_SCREENS) != 0) {
                if (!first)
                    pw.print(", ");
                first = false;
                pw.print("large");
            }
            if ((ps.pkg.applicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_XLARGE_SCREENS) != 0) {
                if (!first)
                    pw.print(", ");
                first = false;
                pw.print("xlarge");
            }
            if ((ps.pkg.applicationInfo.flags & ApplicationInfo.FLAG_RESIZEABLE_FOR_SCREENS) != 0) {
                if (!first)
                    pw.print(", ");
                first = false;
                pw.print("resizeable");
            }
            if ((ps.pkg.applicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_SCREEN_DENSITIES) != 0) {
                if (!first)
                    pw.print(", ");
                first = false;
                pw.print("anyDensity");
            }
            pw.println("]");
            if (ps.pkg.libraryNames != null && ps.pkg.libraryNames.size() > 0) {
                pw.print(prefix); pw.println("  libraries:");
                for (int i=0; i<ps.pkg.libraryNames.size(); i++) {
                    pw.print(prefix); pw.print("    "); pw.println(ps.pkg.libraryNames.get(i));
                }
            }
            if (ps.pkg.usesLibraries != null && ps.pkg.usesLibraries.size() > 0) {
                pw.print(prefix); pw.println("  usesLibraries:");
                for (int i=0; i<ps.pkg.usesLibraries.size(); i++) {
                    pw.print(prefix); pw.print("    "); pw.println(ps.pkg.usesLibraries.get(i));
                }
            }
            if (ps.pkg.usesOptionalLibraries != null
                    && ps.pkg.usesOptionalLibraries.size() > 0) {
                pw.print(prefix); pw.println("  usesOptionalLibraries:");
                for (int i=0; i<ps.pkg.usesOptionalLibraries.size(); i++) {
                    pw.print(prefix); pw.print("    ");
                        pw.println(ps.pkg.usesOptionalLibraries.get(i));
                }
            }
            if (ps.pkg.usesLibraryFiles != null
                    && ps.pkg.usesLibraryFiles.length > 0) {
                pw.print(prefix); pw.println("  usesLibraryFiles:");
                for (int i=0; i<ps.pkg.usesLibraryFiles.length; i++) {
                    pw.print(prefix); pw.print("    "); pw.println(ps.pkg.usesLibraryFiles[i]);
                }
            }
        }
        pw.print(prefix); pw.print("  timeStamp=");
            date.setTime(ps.timeStamp);
            pw.println(sdf.format(date));
        pw.print(prefix); pw.print("  firstInstallTime=");
            date.setTime(ps.firstInstallTime);
            pw.println(sdf.format(date));
        pw.print(prefix); pw.print("  lastUpdateTime=");
            date.setTime(ps.lastUpdateTime);
            pw.println(sdf.format(date));
        if (ps.installerPackageName != null) {
            pw.print(prefix); pw.print("  installerPackageName=");
                    pw.println(ps.installerPackageName);
        }
        if (ps.volumeUuid != null) {
            pw.print(prefix); pw.print("  volumeUuid=");
                    pw.println(ps.volumeUuid);
        }
        pw.print(prefix); pw.print("  signatures="); pw.println(ps.signatures);
        pw.print(prefix); pw.print("  installPermissionsFixed=");
                pw.print(ps.installPermissionsFixed);
                pw.print(" installStatus="); pw.println(ps.installStatus);
        pw.print(prefix); pw.print("  pkgFlags="); printFlags(pw, ps.pkgFlags, FLAG_DUMP_SPEC);
                pw.println();

        if (ps.pkg != null && ps.pkg.permissions != null && ps.pkg.permissions.size() > 0) {
            final ArrayList<PackageParser.Permission> perms = ps.pkg.permissions;
            pw.print(prefix); pw.println("  declared permissions:");
            for (int i=0; i<perms.size(); i++) {
                PackageParser.Permission perm = perms.get(i);
                if (permissionNames != null
                        && !permissionNames.contains(perm.info.name)) {
                    continue;
                }
                pw.print(prefix); pw.print("    "); pw.print(perm.info.name);
                pw.print(": prot=");
                pw.print(PermissionInfo.protectionToString(perm.info.protectionLevel));
                if ((perm.info.flags&PermissionInfo.FLAG_COSTS_MONEY) != 0) {
                    pw.print(", COSTS_MONEY");
                }
                if ((perm.info.flags&PermissionInfo.FLAG_REMOVED) != 0) {
                    pw.print(", HIDDEN");
                }
                if ((perm.info.flags&PermissionInfo.FLAG_INSTALLED) != 0) {
                    pw.print(", INSTALLED");
                }
                pw.println();
            }
        }

        if ((permissionNames != null || dumpAll) && ps.pkg != null
                && ps.pkg.requestedPermissions != null
                && ps.pkg.requestedPermissions.size() > 0) {
            final ArrayList<String> perms = ps.pkg.requestedPermissions;
            pw.print(prefix); pw.println("  requested permissions:");
            for (int i=0; i<perms.size(); i++) {
                String perm = perms.get(i);
                if (permissionNames != null
                        && !permissionNames.contains(perm)) {
                    continue;
                }
                pw.print(prefix); pw.print("    "); pw.println(perm);
            }
        }

        if (ps.sharedUser == null || permissionNames != null || dumpAll) {
            PermissionsState permissionsState = ps.getPermissionsState();
            dumpInstallPermissionsLPr(pw, prefix + "  ", permissionNames, permissionsState);
        }

        for (UserInfo user : users) {
            pw.print(prefix); pw.print("  User "); pw.print(user.id); pw.print(": ");
            pw.print("ceDataInode=");
            pw.print(ps.getCeDataInode(user.id));
            pw.print(" installed=");
            pw.print(ps.getInstalled(user.id));
            pw.print(" hidden=");
            pw.print(ps.getHidden(user.id));
            pw.print(" suspended=");
            pw.print(ps.getSuspended(user.id));
            pw.print(" stopped=");
            pw.print(ps.getStopped(user.id));
            pw.print(" notLaunched=");
            pw.print(ps.getNotLaunched(user.id));
            pw.print(" enabled=");
            pw.println(ps.getEnabled(user.id));
            String lastDisabledAppCaller = ps.getLastDisabledAppCaller(user.id);
            if (lastDisabledAppCaller != null) {
                pw.print(prefix); pw.print("    lastDisabledCaller: ");
                        pw.println(lastDisabledAppCaller);
            }

            if (ps.sharedUser == null) {
                PermissionsState permissionsState = ps.getPermissionsState();
                dumpGidsLPr(pw, prefix + "    ", permissionsState.computeGids(user.id));
                dumpRuntimePermissionsLPr(pw, prefix + "    ", permissionNames, permissionsState
                        .getRuntimePermissionStates(user.id), dumpAll);
            }

            if (permissionNames == null) {
                ArraySet<String> cmp = ps.getDisabledComponents(user.id);
                if (cmp != null && cmp.size() > 0) {
                    pw.print(prefix); pw.println("    disabledComponents:");
                    for (String s : cmp) {
                        pw.print(prefix); pw.print("      "); pw.println(s);
                    }
                }
                cmp = ps.getEnabledComponents(user.id);
                if (cmp != null && cmp.size() > 0) {
                    pw.print(prefix); pw.println("    enabledComponents:");
                    for (String s : cmp) {
                        pw.print(prefix); pw.print("      "); pw.println(s);
                    }
                }
            }
        }
    }

    void dumpPackagesLPr(PrintWriter pw, String packageName, ArraySet<String> permissionNames,
            DumpState dumpState, boolean checkin) {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final Date date = new Date();
        boolean printedSomething = false;
        List<UserInfo> users = getAllUsers();
        for (final PackageSetting ps : mPackages.values()) {
            if (packageName != null && !packageName.equals(ps.realName)
                    && !packageName.equals(ps.name)) {
                continue;
            }
            if (permissionNames != null
                    && !ps.getPermissionsState().hasRequestedPermission(permissionNames)) {
                continue;
            }

            if (!checkin && packageName != null) {
                dumpState.setSharedUser(ps.sharedUser);
            }

            if (!checkin && !printedSomething) {
                if (dumpState.onTitlePrinted())
                    pw.println();
                pw.println("Packages:");
                printedSomething = true;
            }
            dumpPackageLPr(pw, "  ", checkin ? "pkg" : null, permissionNames, ps, sdf, date, users,
                    packageName != null);
        }

        printedSomething = false;
        if (mRenamedPackages.size() > 0 && permissionNames == null) {
            for (final Map.Entry<String, String> e : mRenamedPackages.entrySet()) {
                if (packageName != null && !packageName.equals(e.getKey())
                        && !packageName.equals(e.getValue())) {
                    continue;
                }
                if (!checkin) {
                    if (!printedSomething) {
                        if (dumpState.onTitlePrinted())
                            pw.println();
                        pw.println("Renamed packages:");
                        printedSomething = true;
                    }
                    pw.print("  ");
                } else {
                    pw.print("ren,");
                }
                pw.print(e.getKey());
                pw.print(checkin ? " -> " : ",");
                pw.println(e.getValue());
            }
        }

        printedSomething = false;
        if (mDisabledSysPackages.size() > 0 && permissionNames == null) {
            for (final PackageSetting ps : mDisabledSysPackages.values()) {
                if (packageName != null && !packageName.equals(ps.realName)
                        && !packageName.equals(ps.name)) {
                    continue;
                }
                if (!checkin && !printedSomething) {
                    if (dumpState.onTitlePrinted())
                        pw.println();
                    pw.println("Hidden system packages:");
                    printedSomething = true;
                }
                dumpPackageLPr(pw, "  ", checkin ? "dis" : null, permissionNames, ps, sdf, date,
                        users, packageName != null);
            }
        }
    }

    void dumpPermissionsLPr(PrintWriter pw, String packageName, ArraySet<String> permissionNames,
            DumpState dumpState) {
        boolean printedSomething = false;
        for (BasePermission p : mPermissions.values()) {
            if (packageName != null && !packageName.equals(p.sourcePackage)) {
                continue;
            }
            if (permissionNames != null && !permissionNames.contains(p.name)) {
                continue;
            }
            if (!printedSomething) {
                if (dumpState.onTitlePrinted())
                    pw.println();
                pw.println("Permissions:");
                printedSomething = true;
            }
            pw.print("  Permission ["); pw.print(p.name); pw.print("] (");
                    pw.print(Integer.toHexString(System.identityHashCode(p)));
                    pw.println("):");
            pw.print("    sourcePackage="); pw.println(p.sourcePackage);
            pw.print("    uid="); pw.print(p.uid);
                    pw.print(" gids="); pw.print(Arrays.toString(
                            p.computeGids(UserHandle.USER_SYSTEM)));
                    pw.print(" type="); pw.print(p.type);
                    pw.print(" prot=");
                    pw.println(PermissionInfo.protectionToString(p.protectionLevel));
            if (p.perm != null) {
                pw.print("    perm="); pw.println(p.perm);
                if ((p.perm.info.flags & PermissionInfo.FLAG_INSTALLED) == 0
                        || (p.perm.info.flags & PermissionInfo.FLAG_REMOVED) != 0) {
                    pw.print("    flags=0x"); pw.println(Integer.toHexString(p.perm.info.flags));
                }
            }
            if (p.packageSetting != null) {
                pw.print("    packageSetting="); pw.println(p.packageSetting);
            }
            if (READ_EXTERNAL_STORAGE.equals(p.name)) {
                pw.print("    enforced=");
                pw.println(mReadExternalStorageEnforced);
            }
        }
    }

    void dumpSharedUsersLPr(PrintWriter pw, String packageName, ArraySet<String> permissionNames,
            DumpState dumpState, boolean checkin) {
        boolean printedSomething = false;
        for (SharedUserSetting su : mSharedUsers.values()) {
            if (packageName != null && su != dumpState.getSharedUser()) {
                continue;
            }
            if (permissionNames != null
                    && !su.getPermissionsState().hasRequestedPermission(permissionNames)) {
                continue;
            }
            if (!checkin) {
                if (!printedSomething) {
                    if (dumpState.onTitlePrinted())
                        pw.println();
                    pw.println("Shared users:");
                    printedSomething = true;
                }
                pw.print("  SharedUser [");
                pw.print(su.name);
                pw.print("] (");
                pw.print(Integer.toHexString(System.identityHashCode(su)));
                        pw.println("):");

                String prefix = "    ";
                pw.print(prefix); pw.print("userId="); pw.println(su.userId);

                PermissionsState permissionsState = su.getPermissionsState();
                dumpInstallPermissionsLPr(pw, prefix, permissionNames, permissionsState);

                for (int userId : UserManagerService.getInstance().getUserIds()) {
                    final int[] gids = permissionsState.computeGids(userId);
                    List<PermissionState> permissions = permissionsState
                            .getRuntimePermissionStates(userId);
                    if (!ArrayUtils.isEmpty(gids) || !permissions.isEmpty()) {
                        pw.print(prefix); pw.print("User "); pw.print(userId); pw.println(": ");
                        dumpGidsLPr(pw, prefix + "  ", gids);
                        dumpRuntimePermissionsLPr(pw, prefix + "  ", permissionNames, permissions,
                                packageName != null);
                    }
                }
            } else {
                pw.print("suid,"); pw.print(su.userId); pw.print(","); pw.println(su.name);
            }
        }
    }

    void dumpReadMessagesLPr(PrintWriter pw, DumpState dumpState) {
        pw.println("Settings parse messages:");
        pw.print(mReadMessages.toString());
    }

    void dumpRestoredPermissionGrantsLPr(PrintWriter pw, DumpState dumpState) {
        if (mRestoredUserGrants.size() > 0) {
            pw.println();
            pw.println("Restored (pending) permission grants:");
            for (int userIndex = 0; userIndex < mRestoredUserGrants.size(); userIndex++) {
                ArrayMap<String, ArraySet<RestoredPermissionGrant>> grantsByPackage =
                        mRestoredUserGrants.valueAt(userIndex);
                if (grantsByPackage != null && grantsByPackage.size() > 0) {
                    final int userId = mRestoredUserGrants.keyAt(userIndex);
                    pw.print("  User "); pw.println(userId);

                    for (int pkgIndex = 0; pkgIndex < grantsByPackage.size(); pkgIndex++) {
                        ArraySet<RestoredPermissionGrant> grants = grantsByPackage.valueAt(pkgIndex);
                        if (grants != null && grants.size() > 0) {
                            final String pkgName = grantsByPackage.keyAt(pkgIndex);
                            pw.print("    "); pw.print(pkgName); pw.println(" :");

                            for (RestoredPermissionGrant g : grants) {
                                pw.print("      ");
                                pw.print(g.permissionName);
                                if (g.granted) {
                                    pw.print(" GRANTED");
                                }
                                if ((g.grantBits&FLAG_PERMISSION_USER_SET) != 0) {
                                    pw.print(" user_set");
                                }
                                if ((g.grantBits&FLAG_PERMISSION_USER_FIXED) != 0) {
                                    pw.print(" user_fixed");
                                }
                                if ((g.grantBits&FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0) {
                                    pw.print(" revoke_on_upgrade");
                                }
                                pw.println();
                            }
                        }
                    }
                }
            }
            pw.println();
        }
    }

    private static void dumpSplitNames(PrintWriter pw, PackageParser.Package pkg) {
        if (pkg == null) {
            pw.print("unknown");
        } else {
            // [base:10, config.mdpi, config.xhdpi:12]
            pw.print("[");
            pw.print("base");
            if (pkg.baseRevisionCode != 0) {
                pw.print(":"); pw.print(pkg.baseRevisionCode);
            }
            if (pkg.splitNames != null) {
                for (int i = 0; i < pkg.splitNames.length; i++) {
                    pw.print(", ");
                    pw.print(pkg.splitNames[i]);
                    if (pkg.splitRevisionCodes[i] != 0) {
                        pw.print(":"); pw.print(pkg.splitRevisionCodes[i]);
                    }
                }
            }
            pw.print("]");
        }
    }

    void dumpGidsLPr(PrintWriter pw, String prefix, int[] gids) {
        if (!ArrayUtils.isEmpty(gids)) {
            pw.print(prefix);
            pw.print("gids="); pw.println(
                    PackageManagerService.arrayToString(gids));
        }
    }

    void dumpRuntimePermissionsLPr(PrintWriter pw, String prefix, ArraySet<String> permissionNames,
            List<PermissionState> permissionStates, boolean dumpAll) {
        if (!permissionStates.isEmpty() || dumpAll) {
            pw.print(prefix); pw.println("runtime permissions:");
            for (PermissionState permissionState : permissionStates) {
                if (permissionNames != null
                        && !permissionNames.contains(permissionState.getName())) {
                    continue;
                }
                pw.print(prefix); pw.print("  "); pw.print(permissionState.getName());
                pw.print(": granted="); pw.print(permissionState.isGranted());
                    pw.println(permissionFlagsToString(", flags=",
                            permissionState.getFlags()));
            }
        }
    }

    private static String permissionFlagsToString(String prefix, int flags) {
        StringBuilder flagsString = null;
        while (flags != 0) {
            if (flagsString == null) {
                flagsString = new StringBuilder();
                flagsString.append(prefix);
                flagsString.append("[ ");
            }
            final int flag = 1 << Integer.numberOfTrailingZeros(flags);
            flags &= ~flag;
            flagsString.append(PackageManager.permissionFlagToString(flag));
            flagsString.append(' ');
        }
        if (flagsString != null) {
            flagsString.append(']');
            return flagsString.toString();
        } else {
            return "";
        }
    }

    void dumpInstallPermissionsLPr(PrintWriter pw, String prefix, ArraySet<String> permissionNames,
            PermissionsState permissionsState) {
        List<PermissionState> permissionStates = permissionsState.getInstallPermissionStates();
        if (!permissionStates.isEmpty()) {
            pw.print(prefix); pw.println("install permissions:");
            for (PermissionState permissionState : permissionStates) {
                if (permissionNames != null
                        && !permissionNames.contains(permissionState.getName())) {
                    continue;
                }
                pw.print(prefix); pw.print("  "); pw.print(permissionState.getName());
                    pw.print(": granted="); pw.print(permissionState.isGranted());
                    pw.println(permissionFlagsToString(", flags=",
                        permissionState.getFlags()));
            }
        }
    }

    public void writeRuntimePermissionsForUserLPr(int userId, boolean sync) {
        if (sync) {
            mRuntimePermissionsPersistence.writePermissionsForUserSyncLPr(userId);
        } else {
            mRuntimePermissionsPersistence.writePermissionsForUserAsyncLPr(userId);
        }
    }

    private final class RuntimePermissionPersistence {
        private static final long WRITE_PERMISSIONS_DELAY_MILLIS = 200;
        private static final long MAX_WRITE_PERMISSIONS_DELAY_MILLIS = 2000;

        private final Handler mHandler = new MyHandler();

        private final Object mLock;

        @GuardedBy("mLock")
        private final SparseBooleanArray mWriteScheduled = new SparseBooleanArray();

        @GuardedBy("mLock")
        // The mapping keys are user ids.
        private final SparseLongArray mLastNotWrittenMutationTimesMillis = new SparseLongArray();

        @GuardedBy("mLock")
        // The mapping keys are user ids.
        private final SparseArray<String> mFingerprints = new SparseArray<>();

        @GuardedBy("mLock")
        // The mapping keys are user ids.
        private final SparseBooleanArray mDefaultPermissionsGranted = new SparseBooleanArray();

        public RuntimePermissionPersistence(Object lock) {
            mLock = lock;
        }

        public boolean areDefaultRuntimPermissionsGrantedLPr(int userId) {
            return mDefaultPermissionsGranted.get(userId);
        }

        public void onDefaultRuntimePermissionsGrantedLPr(int userId) {
            mFingerprints.put(userId, Build.FINGERPRINT);
            writePermissionsForUserAsyncLPr(userId);
        }

        public void writePermissionsForUserSyncLPr(int userId) {
            mHandler.removeMessages(userId);
            writePermissionsSync(userId);
        }

        public void writePermissionsForUserAsyncLPr(int userId) {
            final long currentTimeMillis = SystemClock.uptimeMillis();

            if (mWriteScheduled.get(userId)) {
                mHandler.removeMessages(userId);

                // If enough time passed, write without holding off anymore.
                final long lastNotWrittenMutationTimeMillis = mLastNotWrittenMutationTimesMillis
                        .get(userId);
                final long timeSinceLastNotWrittenMutationMillis = currentTimeMillis
                        - lastNotWrittenMutationTimeMillis;
                if (timeSinceLastNotWrittenMutationMillis >= MAX_WRITE_PERMISSIONS_DELAY_MILLIS) {
                    mHandler.obtainMessage(userId).sendToTarget();
                    return;
                }

                // Hold off a bit more as settings are frequently changing.
                final long maxDelayMillis = Math.max(lastNotWrittenMutationTimeMillis
                        + MAX_WRITE_PERMISSIONS_DELAY_MILLIS - currentTimeMillis, 0);
                final long writeDelayMillis = Math.min(WRITE_PERMISSIONS_DELAY_MILLIS,
                        maxDelayMillis);

                Message message = mHandler.obtainMessage(userId);
                mHandler.sendMessageDelayed(message, writeDelayMillis);
            } else {
                mLastNotWrittenMutationTimesMillis.put(userId, currentTimeMillis);
                Message message = mHandler.obtainMessage(userId);
                mHandler.sendMessageDelayed(message, WRITE_PERMISSIONS_DELAY_MILLIS);
                mWriteScheduled.put(userId, true);
            }
        }

        private void writePermissionsSync(int userId) {
            AtomicFile destination = new AtomicFile(getUserRuntimePermissionsFile(userId));

            ArrayMap<String, List<PermissionState>> permissionsForPackage = new ArrayMap<>();
            ArrayMap<String, List<PermissionState>> permissionsForSharedUser = new ArrayMap<>();

            synchronized (mLock) {
                mWriteScheduled.delete(userId);

                final int packageCount = mPackages.size();
                for (int i = 0; i < packageCount; i++) {
                    String packageName = mPackages.keyAt(i);
                    PackageSetting packageSetting = mPackages.valueAt(i);
                    if (packageSetting.sharedUser == null) {
                        PermissionsState permissionsState = packageSetting.getPermissionsState();
                        List<PermissionState> permissionsStates = permissionsState
                                .getRuntimePermissionStates(userId);
                        if (!permissionsStates.isEmpty()) {
                            permissionsForPackage.put(packageName, permissionsStates);
                        }
                    }
                }

                final int sharedUserCount = mSharedUsers.size();
                for (int i = 0; i < sharedUserCount; i++) {
                    String sharedUserName = mSharedUsers.keyAt(i);
                    SharedUserSetting sharedUser = mSharedUsers.valueAt(i);
                    PermissionsState permissionsState = sharedUser.getPermissionsState();
                    List<PermissionState> permissionsStates = permissionsState
                            .getRuntimePermissionStates(userId);
                    if (!permissionsStates.isEmpty()) {
                        permissionsForSharedUser.put(sharedUserName, permissionsStates);
                    }
                }
            }

            FileOutputStream out = null;
            try {
                out = destination.startWrite();

                XmlSerializer serializer = Xml.newSerializer();
                serializer.setOutput(out, StandardCharsets.UTF_8.name());
                serializer.setFeature(
                        "http://xmlpull.org/v1/doc/features.html#indent-output", true);
                serializer.startDocument(null, true);

                serializer.startTag(null, TAG_RUNTIME_PERMISSIONS);

                String fingerprint = mFingerprints.get(userId);
                if (fingerprint != null) {
                    serializer.attribute(null, ATTR_FINGERPRINT, fingerprint);
                }

                final int packageCount = permissionsForPackage.size();
                for (int i = 0; i < packageCount; i++) {
                    String packageName = permissionsForPackage.keyAt(i);
                    List<PermissionState> permissionStates = permissionsForPackage.valueAt(i);
                    serializer.startTag(null, TAG_PACKAGE);
                    serializer.attribute(null, ATTR_NAME, packageName);
                    writePermissions(serializer, permissionStates);
                    serializer.endTag(null, TAG_PACKAGE);
                }

                final int sharedUserCount = permissionsForSharedUser.size();
                for (int i = 0; i < sharedUserCount; i++) {
                    String packageName = permissionsForSharedUser.keyAt(i);
                    List<PermissionState> permissionStates = permissionsForSharedUser.valueAt(i);
                    serializer.startTag(null, TAG_SHARED_USER);
                    serializer.attribute(null, ATTR_NAME, packageName);
                    writePermissions(serializer, permissionStates);
                    serializer.endTag(null, TAG_SHARED_USER);
                }

                serializer.endTag(null, TAG_RUNTIME_PERMISSIONS);

                // Now any restored permission grants that are waiting for the apps
                // in question to be installed.  These are stored as per-package
                // TAG_RESTORED_RUNTIME_PERMISSIONS blocks, each containing some
                // number of individual permission grant entities.
                if (mRestoredUserGrants.get(userId) != null) {
                    ArrayMap<String, ArraySet<RestoredPermissionGrant>> restoredGrants =
                            mRestoredUserGrants.get(userId);
                    if (restoredGrants != null) {
                        final int pkgCount = restoredGrants.size();
                        for (int i = 0; i < pkgCount; i++) {
                            final ArraySet<RestoredPermissionGrant> pkgGrants =
                                    restoredGrants.valueAt(i);
                            if (pkgGrants != null && pkgGrants.size() > 0) {
                                final String pkgName = restoredGrants.keyAt(i);
                                serializer.startTag(null, TAG_RESTORED_RUNTIME_PERMISSIONS);
                                serializer.attribute(null, ATTR_PACKAGE_NAME, pkgName);

                                final int N = pkgGrants.size();
                                for (int z = 0; z < N; z++) {
                                    RestoredPermissionGrant g = pkgGrants.valueAt(z);
                                    serializer.startTag(null, TAG_PERMISSION_ENTRY);
                                    serializer.attribute(null, ATTR_NAME, g.permissionName);

                                    if (g.granted) {
                                        serializer.attribute(null, ATTR_GRANTED, "true");
                                    }

                                    if ((g.grantBits&FLAG_PERMISSION_USER_SET) != 0) {
                                        serializer.attribute(null, ATTR_USER_SET, "true");
                                    }
                                    if ((g.grantBits&FLAG_PERMISSION_USER_FIXED) != 0) {
                                        serializer.attribute(null, ATTR_USER_FIXED, "true");
                                    }
                                    if ((g.grantBits&FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0) {
                                        serializer.attribute(null, ATTR_REVOKE_ON_UPGRADE, "true");
                                    }
                                    serializer.endTag(null, TAG_PERMISSION_ENTRY);
                                }
                                serializer.endTag(null, TAG_RESTORED_RUNTIME_PERMISSIONS);
                            }
                        }
                    }
                }

                serializer.endDocument();
                destination.finishWrite(out);

                if (Build.FINGERPRINT.equals(fingerprint)) {
                    mDefaultPermissionsGranted.put(userId, true);
                }
            // Any error while writing is fatal.
            } catch (Throwable t) {
                Slog.wtf(PackageManagerService.TAG,
                        "Failed to write settings, restoring backup", t);
                destination.failWrite(out);
            } finally {
                IoUtils.closeQuietly(out);
            }
        }

        private void onUserRemovedLPw(int userId) {
            // Make sure we do not
            mHandler.removeMessages(userId);

            for (SettingBase sb : mPackages.values()) {
                revokeRuntimePermissionsAndClearFlags(sb, userId);
            }

            for (SettingBase sb : mSharedUsers.values()) {
                revokeRuntimePermissionsAndClearFlags(sb, userId);
            }

            mDefaultPermissionsGranted.delete(userId);
            mFingerprints.remove(userId);
        }

        private void revokeRuntimePermissionsAndClearFlags(SettingBase sb, int userId) {
            PermissionsState permissionsState = sb.getPermissionsState();
            for (PermissionState permissionState
                    : permissionsState.getRuntimePermissionStates(userId)) {
                BasePermission bp = mPermissions.get(permissionState.getName());
                if (bp != null) {
                    permissionsState.revokeRuntimePermission(bp, userId);
                    permissionsState.updatePermissionFlags(bp, userId,
                            PackageManager.MASK_PERMISSION_FLAGS, 0);
                }
            }
        }

        public void deleteUserRuntimePermissionsFile(int userId) {
            getUserRuntimePermissionsFile(userId).delete();
        }

        public void readStateForUserSyncLPr(int userId) {
            File permissionsFile = getUserRuntimePermissionsFile(userId);
            if (!permissionsFile.exists()) {
                return;
            }

            FileInputStream in;
            try {
                in = new AtomicFile(permissionsFile).openRead();
            } catch (FileNotFoundException fnfe) {
                Slog.i(PackageManagerService.TAG, "No permissions state");
                return;
            }

            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, null);
                parseRuntimePermissionsLPr(parser, userId);

            } catch (XmlPullParserException | IOException e) {
                throw new IllegalStateException("Failed parsing permissions file: "
                        + permissionsFile , e);
            } finally {
                IoUtils.closeQuietly(in);
            }
        }

        // Backup/restore support

        public void rememberRestoredUserGrantLPr(String pkgName, String permission,
                boolean isGranted, int restoredFlagSet, int userId) {
            // This change will be remembered at write-settings time
            ArrayMap<String, ArraySet<RestoredPermissionGrant>> grantsByPackage =
                    mRestoredUserGrants.get(userId);
            if (grantsByPackage == null) {
                grantsByPackage = new ArrayMap<String, ArraySet<RestoredPermissionGrant>>();
                mRestoredUserGrants.put(userId, grantsByPackage);
            }

            ArraySet<RestoredPermissionGrant> grants = grantsByPackage.get(pkgName);
            if (grants == null) {
                grants = new ArraySet<RestoredPermissionGrant>();
                grantsByPackage.put(pkgName, grants);
            }

            RestoredPermissionGrant grant = new RestoredPermissionGrant(permission,
                    isGranted, restoredFlagSet);
            grants.add(grant);
        }

        // Private internals

        private void parseRuntimePermissionsLPr(XmlPullParser parser, int userId)
                throws IOException, XmlPullParserException {
            final int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                switch (parser.getName()) {
                    case TAG_RUNTIME_PERMISSIONS: {
                        String fingerprint = parser.getAttributeValue(null, ATTR_FINGERPRINT);
                        mFingerprints.put(userId, fingerprint);
                        final boolean defaultsGranted = Build.FINGERPRINT.equals(fingerprint);
                        mDefaultPermissionsGranted.put(userId, defaultsGranted);
                    } break;

                    case TAG_PACKAGE: {
                        String name = parser.getAttributeValue(null, ATTR_NAME);
                        PackageSetting ps = mPackages.get(name);
                        if (ps == null) {
                            Slog.w(PackageManagerService.TAG, "Unknown package:" + name);
                            XmlUtils.skipCurrentTag(parser);
                            continue;
                        }
                        parsePermissionsLPr(parser, ps.getPermissionsState(), userId);
                    } break;

                    case TAG_SHARED_USER: {
                        String name = parser.getAttributeValue(null, ATTR_NAME);
                        SharedUserSetting sus = mSharedUsers.get(name);
                        if (sus == null) {
                            Slog.w(PackageManagerService.TAG, "Unknown shared user:" + name);
                            XmlUtils.skipCurrentTag(parser);
                            continue;
                        }
                        parsePermissionsLPr(parser, sus.getPermissionsState(), userId);
                    } break;

                    case TAG_RESTORED_RUNTIME_PERMISSIONS: {
                        final String pkgName = parser.getAttributeValue(null, ATTR_PACKAGE_NAME);
                        parseRestoredRuntimePermissionsLPr(parser, pkgName, userId);
                    } break;
                }
            }
        }

        private void parseRestoredRuntimePermissionsLPr(XmlPullParser parser,
                final String pkgName, final int userId) throws IOException, XmlPullParserException {
            final int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                switch (parser.getName()) {
                    case TAG_PERMISSION_ENTRY: {
                        final String permName = parser.getAttributeValue(null, ATTR_NAME);
                        final boolean isGranted = "true".equals(
                                parser.getAttributeValue(null, ATTR_GRANTED));

                        int permBits = 0;
                        if ("true".equals(parser.getAttributeValue(null, ATTR_USER_SET))) {
                            permBits |= FLAG_PERMISSION_USER_SET;
                        }
                        if ("true".equals(parser.getAttributeValue(null, ATTR_USER_FIXED))) {
                            permBits |= FLAG_PERMISSION_USER_FIXED;
                        }
                        if ("true".equals(parser.getAttributeValue(null, ATTR_REVOKE_ON_UPGRADE))) {
                            permBits |= FLAG_PERMISSION_REVOKE_ON_UPGRADE;
                        }

                        if (isGranted || permBits != 0) {
                            rememberRestoredUserGrantLPr(pkgName, permName, isGranted, permBits, userId);
                        }
                    } break;
                }
            }
        }

        private void parsePermissionsLPr(XmlPullParser parser, PermissionsState permissionsState,
                int userId) throws IOException, XmlPullParserException {
            final int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                switch (parser.getName()) {
                    case TAG_ITEM: {
                        String name = parser.getAttributeValue(null, ATTR_NAME);
                        BasePermission bp = mPermissions.get(name);
                        if (bp == null) {
                            Slog.w(PackageManagerService.TAG, "Unknown permission:" + name);
                            XmlUtils.skipCurrentTag(parser);
                            continue;
                        }

                        String grantedStr = parser.getAttributeValue(null, ATTR_GRANTED);
                        final boolean granted = grantedStr == null
                                || Boolean.parseBoolean(grantedStr);

                        String flagsStr = parser.getAttributeValue(null, ATTR_FLAGS);
                        final int flags = (flagsStr != null)
                                ? Integer.parseInt(flagsStr, 16) : 0;

                        if (granted) {
                            permissionsState.grantRuntimePermission(bp, userId);
                            permissionsState.updatePermissionFlags(bp, userId,
                                        PackageManager.MASK_PERMISSION_FLAGS, flags);
                        } else {
                            permissionsState.updatePermissionFlags(bp, userId,
                                    PackageManager.MASK_PERMISSION_FLAGS, flags);
                        }

                    } break;
                }
            }
        }

        private void writePermissions(XmlSerializer serializer,
                List<PermissionState> permissionStates) throws IOException {
            for (PermissionState permissionState : permissionStates) {
                serializer.startTag(null, TAG_ITEM);
                serializer.attribute(null, ATTR_NAME,permissionState.getName());
                serializer.attribute(null, ATTR_GRANTED,
                        String.valueOf(permissionState.isGranted()));
                serializer.attribute(null, ATTR_FLAGS,
                        Integer.toHexString(permissionState.getFlags()));
                serializer.endTag(null, TAG_ITEM);
            }
        }

        private final class MyHandler extends Handler {
            public MyHandler() {
                super(BackgroundThread.getHandler().getLooper());
            }

            @Override
            public void handleMessage(Message message) {
                final int userId = message.what;
                Runnable callback = (Runnable) message.obj;
                writePermissionsSync(userId);
                if (callback != null) {
                    callback.run();
                }
            }
        }
    }
}
