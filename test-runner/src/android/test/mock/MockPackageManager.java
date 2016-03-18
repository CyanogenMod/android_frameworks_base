/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.test.mock;

import android.app.PackageInstallObserver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ContainerEncryptionParams;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.InstrumentationInfo;
import android.content.pm.IntentFilterVerificationInfo;
import android.content.pm.KeySet;
import android.content.pm.ManifestDigest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.VerificationParams;
import android.content.pm.VerifierDeviceIdentity;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.storage.VolumeInfo;

import java.util.List;

/**
 * A mock {@link android.content.pm.PackageManager} class.  All methods are non-functional and throw
 * {@link java.lang.UnsupportedOperationException}. Override it to provide the operations that you
 * need.
 */
public class MockPackageManager extends PackageManager {

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] currentToCanonicalPackageNames(String[] names) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] canonicalToCurrentPackageNames(String[] names) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Intent getLaunchIntentForPackage(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Intent getLeanbackLaunchIntentForPackage(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override

    public int[] getPackageGids(String packageName) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public int getPackageUid(String packageName, int userHandle)
            throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionInfo getPermissionInfo(String name, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PermissionInfo> queryPermissionsByGroup(String group, int flags)
            throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionGroupInfo getPermissionGroupInfo(String name,
            int flags) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName className, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName className, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName className, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName className, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions,
            int flags) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int checkPermission(String permName, String pkgName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPermissionRevokedByPolicy(String permName, String pkgName) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public String getPermissionControllerPackageName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addPermission(PermissionInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addPermissionAsync(PermissionInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removePermission(String name) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public void grantRuntimePermission(String packageName, String permissionName,
            UserHandle user) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public void revokeRuntimePermission(String packageName, String permissionName,
            UserHandle user) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public int getPermissionFlags(String permissionName, String packageName, UserHandle user) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public void updatePermissionFlags(String permissionName, String packageName,
            int flagMask, int flagValues, UserHandle user) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public boolean shouldShowRequestPermissionRationale(String permission) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public void addOnPermissionsChangeListener(OnPermissionsChangedListener listener) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public void removeOnPermissionsChangeListener(OnPermissionsChangedListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int checkSignatures(String pkg1, String pkg2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int checkSignatures(int uid1, int uid2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNameForUid(int uid) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public int getUidForSharedUser(String sharedUserName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public ResolveInfo resolveActivityAsUser(Intent intent, int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public List<ResolveInfo> queryIntentActivitiesAsUser(Intent intent,
                                                   int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller,
            Intent[] specifics, Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public List<ResolveInfo> queryIntentServicesAsUser(Intent intent, int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public List<ResolveInfo> queryIntentContentProvidersAsUser(
            Intent intent, int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProviderInfo resolveContentProvider(String name, int flags) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public ProviderInfo resolveContentProviderAsUser(String name, int flags, int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InstrumentationInfo> queryInstrumentation(
            String targetPackage, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getDrawable(String packageName, int resid, ApplicationInfo appInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getActivityIcon(ComponentName activityName)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getActivityIcon(Intent intent) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getDefaultActivityIcon() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getActivityBanner(ComponentName activityName)
            throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getActivityBanner(Intent intent) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getApplicationBanner(ApplicationInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getApplicationBanner(String packageName) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getApplicationIcon(ApplicationInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getActivityLogo(ComponentName activityName) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getActivityLogo(Intent intent) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getApplicationLogo(ApplicationInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getApplicationLogo(String packageName) throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getUserBadgedIcon(Drawable icon, UserHandle user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Drawable getUserBadgedDrawableForDensity(Drawable drawable, UserHandle user,
            Rect badgeLocation,
            int badgeDensity) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public Drawable getUserBadgeForDensity(UserHandle user, int density) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence getUserBadgedLabel(CharSequence label, UserHandle user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence getText(String packageName, int resid, ApplicationInfo appInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public XmlResourceParser getXml(String packageName, int resid,
            ApplicationInfo appInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence getApplicationLabel(ApplicationInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resources getResourcesForActivity(ComponentName activityName)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resources getResourcesForApplication(ApplicationInfo app) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resources getResourcesForApplication(String appPackageName)
    throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public Resources getResourcesForApplicationAsUser(String appPackageName, int userId) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public Resources getThemedResourcesForApplication(ApplicationInfo app, String themePkgName)
            throws NameNotFoundException {
        return null;
    }

    /** @hide */
    @Override
    public Resources getThemedResourcesForApplication(String appPackageName, String themePkgName)
            throws NameNotFoundException {
        return null;
    }

    /** @hide */
    @Override
    public Resources getThemedResourcesForApplicationAsUser(String appPackageName,
            String themePkgName, int userId) throws NameNotFoundException {
        return null;
    }

    @Override
    public PackageInfo getPackageArchiveInfo(String archiveFilePath, int flags) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public void installPackage(Uri packageURI, IPackageInstallObserver observer,
            int flags, String installerPackageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInstallerPackageName(String targetPackage,
            String installerPackageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInstallerPackageName(String packageName) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public int getMoveStatus(int moveId) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public void registerMoveCallback(MoveCallback callback, Handler handler) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public void unregisterMoveCallback(MoveCallback callback) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public int movePackage(String packageName, VolumeInfo vol) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public VolumeInfo getPackageCurrentVolume(ApplicationInfo app) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public List<VolumeInfo> getPackageCandidateVolumes(ApplicationInfo app) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public int movePrimaryStorage(VolumeInfo vol) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public VolumeInfo getPrimaryStorageCurrentVolume() {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public List<VolumeInfo> getPrimaryStorageCandidateVolumes() {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public void clearApplicationUserData(
            String packageName, IPackageDataObserver observer) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public void deleteApplicationCacheFiles(
            String packageName, IPackageDataObserver observer) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public void freeStorageAndNotify(String volumeUuid, long idealStorageSize,
            IPackageDataObserver observer) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public void freeStorage(String volumeUuid, long idealStorageSize, IntentSender pi) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public void deletePackage(
            String packageName, IPackageDeleteObserver observer, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addPackageToPreferred(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removePackageFromPreferred(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PackageInfo> getPreferredPackages(int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setComponentEnabledSetting(ComponentName componentName,
            int newState, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getComponentEnabledSetting(ComponentName componentName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setApplicationEnabledSetting(String packageName, int newState, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getApplicationEnabledSetting(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addPreferredActivity(IntentFilter filter,
            int match, ComponentName[] set, ComponentName activity) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public void replacePreferredActivity(IntentFilter filter,
            int match, ComponentName[] set, ComponentName activity) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void clearPackagePreferredActivities(String packageName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide - to match hiding in superclass
     */
    @Override
    public void getPackageSizeInfo(String packageName, int userHandle,
            IPackageStatsObserver observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPreferredActivities(List<IntentFilter> outFilters,
            List<ComponentName> outActivities, String packageName) {
        throw new UnsupportedOperationException();
    }

    /** @hide - hidden in superclass */
    @Override
    public ComponentName getHomeActivities(List<ResolveInfo> outActivities) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getSystemSharedLibraryNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FeatureInfo[] getSystemAvailableFeatures() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasSystemFeature(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSafeMode() {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public KeySet getKeySetByAlias(String packageName, String alias) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public KeySet getSigningKeySet(String packageName) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public boolean isSignedBy(String packageName, KeySet ks) {
        throw new UnsupportedOperationException();
    }

    /** @hide */
    @Override
    public boolean isSignedByExactly(String packageName, KeySet ks) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void installPackageWithVerification(Uri packageURI, IPackageInstallObserver observer,
            int flags, String installerPackageName, Uri verificationURI,
            ManifestDigest manifestDigest, ContainerEncryptionParams encryptionParams) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void installPackageWithVerificationAndEncryption(Uri packageURI,
            IPackageInstallObserver observer, int flags, String installerPackageName,
            VerificationParams verificationParams, ContainerEncryptionParams encryptionParams) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public boolean setApplicationHiddenSettingAsUser(String packageName, boolean hidden,
            UserHandle user) {
        return false;
    }

    /**
     * @hide
     */
    @Override
    public boolean getApplicationHiddenSettingAsUser(String packageName, UserHandle user) {
        return false;
    }

    /**
     * @hide
     */
    @Override
    public int installExistingPackage(String packageName)
            throws NameNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void verifyPendingInstall(int id, int verificationCode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void extendVerificationTimeout(int id, int verificationCodeAtTimeout,
            long millisecondsToDelay) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void verifyIntentFilter(int id, int verificationCode, List<String> outFailedDomains) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public int getIntentVerificationStatus(String packageName, int userId) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public boolean updateIntentVerificationStatus(String packageName, int status, int userId) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public List<IntentFilterVerificationInfo> getIntentFilterVerifications(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<IntentFilter> getAllIntentFilters(String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDefaultBrowserPackageName(int userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setDefaultBrowserPackageName(String packageName, int userId) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public VerifierDeviceIdentity getVerifierDeviceIdentity() {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public boolean isUpgrade() {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void setComponentProtectedSetting(ComponentName componentName, boolean newState) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public boolean isComponentProtected(String callingPackage, ComponentName componentName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void installPackage(Uri packageURI, PackageInstallObserver observer,
            int flags, String installerPackageName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void installPackageWithVerification(Uri packageURI,
            PackageInstallObserver observer, int flags, String installerPackageName,
            Uri verificationURI, ManifestDigest manifestDigest,
            ContainerEncryptionParams encryptionParams) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void installPackageWithVerificationAndEncryption(Uri packageURI,
            PackageInstallObserver observer, int flags, String installerPackageName,
            VerificationParams verificationParams, ContainerEncryptionParams encryptionParams) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void addCrossProfileIntentFilter(IntentFilter filter, int sourceUserId, int targetUserId,
            int flags) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void clearCrossProfileIntentFilters(int sourceUserId) {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    public PackageInstaller getPackageInstaller() {
        throw new UnsupportedOperationException();
    }

    /** {@hide} */
    @Override
    public boolean isPackageAvailable(String packageName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    public Drawable loadItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    public Drawable loadUnbadgedItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public void updateIconMaps(String pkgName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @hide
     */
    @Override
    public int processThemeResources(String themePkgName) {
        throw new UnsupportedOperationException();
    }
}
