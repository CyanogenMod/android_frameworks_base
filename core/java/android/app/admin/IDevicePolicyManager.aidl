/*
**
** Copyright 2010, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.app.admin;

import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import java.util.List;

/**
 * Internal IPC interface to the device policy service.
 * {@hide}
 */
interface IDevicePolicyManager {
    void setPasswordQuality(in ComponentName who, int quality);
    int getPasswordQuality(in ComponentName who, int userHandle);

    void setPasswordMinimumLength(in ComponentName who, int length);
    int getPasswordMinimumLength(in ComponentName who, int userHandle);

    void setPasswordMinimumUpperCase(in ComponentName who, int length);
    int getPasswordMinimumUpperCase(in ComponentName who, int userHandle);

    void setPasswordMinimumLowerCase(in ComponentName who, int length);
    int getPasswordMinimumLowerCase(in ComponentName who, int userHandle);

    void setPasswordMinimumLetters(in ComponentName who, int length);
    int getPasswordMinimumLetters(in ComponentName who, int userHandle);

    void setPasswordMinimumNumeric(in ComponentName who, int length);
    int getPasswordMinimumNumeric(in ComponentName who, int userHandle);

    void setPasswordMinimumSymbols(in ComponentName who, int length);
    int getPasswordMinimumSymbols(in ComponentName who, int userHandle);

    void setPasswordMinimumNonLetter(in ComponentName who, int length);
    int getPasswordMinimumNonLetter(in ComponentName who, int userHandle);

    void setPasswordHistoryLength(in ComponentName who, int length);
    int getPasswordHistoryLength(in ComponentName who, int userHandle);

    void setPasswordExpirationTimeout(in ComponentName who, long expiration);
    long getPasswordExpirationTimeout(in ComponentName who, int userHandle);

    long getPasswordExpiration(in ComponentName who, int userHandle);

    boolean isActivePasswordSufficient(int userHandle);
    int getCurrentFailedPasswordAttempts(int userHandle);
    int getProfileWithMinimumFailedPasswordsForWipe(int userHandle);

    void setMaximumFailedPasswordsForWipe(in ComponentName admin, int num);
    int getMaximumFailedPasswordsForWipe(in ComponentName admin, int userHandle);

    boolean resetPassword(String password, int flags);

    void setMaximumTimeToLock(in ComponentName who, long timeMs);
    long getMaximumTimeToLock(in ComponentName who, int userHandle);

    void lockNow();

    void wipeData(int flags, int userHandle);

    ComponentName setGlobalProxy(in ComponentName admin, String proxySpec, String exclusionList);
    ComponentName getGlobalProxyAdmin(int userHandle);
    void setRecommendedGlobalProxy(in ComponentName admin, in ProxyInfo proxyInfo);

    int setStorageEncryption(in ComponentName who, boolean encrypt);
    boolean getStorageEncryption(in ComponentName who, int userHandle);
    int getStorageEncryptionStatus(int userHandle);

    void setCameraDisabled(in ComponentName who, boolean disabled);
    boolean getCameraDisabled(in ComponentName who, int userHandle);

    void setScreenCaptureDisabled(in ComponentName who, boolean disabled);
    boolean getScreenCaptureDisabled(in ComponentName who, int userHandle);

    void setKeyguardDisabledFeatures(in ComponentName who, int which);
    int getKeyguardDisabledFeatures(in ComponentName who, int userHandle);

    void setActiveAdmin(in ComponentName policyReceiver, boolean refreshing, int userHandle);
    boolean isAdminActive(in ComponentName policyReceiver, int userHandle);
    List<ComponentName> getActiveAdmins(int userHandle);
    boolean packageHasActiveAdmins(String packageName, int userHandle);
    void getRemoveWarning(in ComponentName policyReceiver, in RemoteCallback result, int userHandle);
    void removeActiveAdmin(in ComponentName policyReceiver, int userHandle);
    boolean hasGrantedPolicy(in ComponentName policyReceiver, int usesPolicy, int userHandle);

    void setActivePasswordState(int quality, int length, int letters, int uppercase, int lowercase,
        int numbers, int symbols, int nonletter, int userHandle);
    void reportFailedPasswordAttempt(int userHandle);
    void reportSuccessfulPasswordAttempt(int userHandle);

    boolean setDeviceOwner(String packageName, String ownerName);
    boolean isDeviceOwner(String packageName);
    String getDeviceOwner();
    String getDeviceOwnerName();
    void clearDeviceOwner(String packageName);

    boolean setProfileOwner(in ComponentName who, String ownerName, int userHandle);
    ComponentName getProfileOwner(int userHandle);
    String getProfileOwnerName(int userHandle);
    void setProfileEnabled(in ComponentName who);
    void setProfileName(in ComponentName who, String profileName);
    void clearProfileOwner(in ComponentName who);
    boolean hasUserSetupCompleted();

    boolean installCaCert(in ComponentName admin, in byte[] certBuffer);
    void uninstallCaCerts(in ComponentName admin, in String[] aliases);
    void enforceCanManageCaCerts(in ComponentName admin);

    boolean installKeyPair(in ComponentName who, in byte[] privKeyBuffer, in byte[] certBuffer, String alias);
    void choosePrivateKeyAlias(int uid, in Uri uri, in String alias, IBinder aliasCallback);

    void setCertInstallerPackage(in ComponentName who, String installerPackage);
    String getCertInstallerPackage(in ComponentName who);

    void addPersistentPreferredActivity(in ComponentName admin, in IntentFilter filter, in ComponentName activity);
    void clearPackagePersistentPreferredActivities(in ComponentName admin, String packageName);

    void setApplicationRestrictions(in ComponentName who, in String packageName, in Bundle settings);
    Bundle getApplicationRestrictions(in ComponentName who, in String packageName);

    void setRestrictionsProvider(in ComponentName who, in ComponentName provider);
    ComponentName getRestrictionsProvider(int userHandle);

    void setUserRestriction(in ComponentName who, in String key, boolean enable);
    void addCrossProfileIntentFilter(in ComponentName admin, in IntentFilter filter, int flags);
    void clearCrossProfileIntentFilters(in ComponentName admin);

    boolean setPermittedAccessibilityServices(in ComponentName admin,in List packageList);
    List getPermittedAccessibilityServices(in ComponentName admin);
    List getPermittedAccessibilityServicesForUser(int userId);

    boolean setPermittedInputMethods(in ComponentName admin,in List packageList);
    List getPermittedInputMethods(in ComponentName admin);
    List getPermittedInputMethodsForCurrentUser();

    boolean setApplicationHidden(in ComponentName admin, in String packageName, boolean hidden);
    boolean isApplicationHidden(in ComponentName admin, in String packageName);

    UserHandle createUser(in ComponentName who, in String name);
    UserHandle createAndInitializeUser(in ComponentName who, in String name, in String profileOwnerName, in ComponentName profileOwnerComponent, in Bundle adminExtras);
    boolean removeUser(in ComponentName who, in UserHandle userHandle);
    boolean switchUser(in ComponentName who, in UserHandle userHandle);

    void enableSystemApp(in ComponentName admin, in String packageName);
    int enableSystemAppWithIntent(in ComponentName admin, in Intent intent);

    void setAccountManagementDisabled(in ComponentName who, in String accountType, in boolean disabled);
    String[] getAccountTypesWithManagementDisabled();
    String[] getAccountTypesWithManagementDisabledAsUser(int userId);

    void setLockTaskPackages(in ComponentName who, in String[] packages);
    String[] getLockTaskPackages(in ComponentName who);
    boolean isLockTaskPermitted(in String pkg);

    void setGlobalSetting(in ComponentName who, in String setting, in String value);
    void setSecureSetting(in ComponentName who, in String setting, in String value);

    void setMasterVolumeMuted(in ComponentName admin, boolean on);
    boolean isMasterVolumeMuted(in ComponentName admin);

    void notifyLockTaskModeChanged(boolean isEnabled, String pkg, int userId);

    void setUninstallBlocked(in ComponentName admin, in String packageName, boolean uninstallBlocked);
    boolean isUninstallBlocked(in ComponentName admin, in String packageName);

    void setCrossProfileCallerIdDisabled(in ComponentName who, boolean disabled);
    boolean getCrossProfileCallerIdDisabled(in ComponentName who);
    boolean getCrossProfileCallerIdDisabledForUser(int userId);
    void startManagedQuickContact(String lookupKey, long contactId, in Intent originalIntent);

    void setBluetoothContactSharingDisabled(in ComponentName who, boolean disabled);
    boolean getBluetoothContactSharingDisabled(in ComponentName who);
    boolean getBluetoothContactSharingDisabledForUser(int userId);

    void setTrustAgentConfiguration(in ComponentName admin, in ComponentName agent,
            in PersistableBundle args);
    List<PersistableBundle> getTrustAgentConfiguration(in ComponentName admin,
            in ComponentName agent, int userId);

    boolean addCrossProfileWidgetProvider(in ComponentName admin, String packageName);
    boolean removeCrossProfileWidgetProvider(in ComponentName admin, String packageName);
    List<String> getCrossProfileWidgetProviders(in ComponentName admin);

    void setAutoTimeRequired(in ComponentName who, boolean required);
    boolean getAutoTimeRequired();

    boolean isRemovingAdmin(in ComponentName adminReceiver, int userHandle);

    boolean setUserEnabled(in ComponentName who);
    boolean isDeviceInitializer(String packageName);
    void clearDeviceInitializer(in ComponentName who);
    boolean setDeviceInitializer(in ComponentName who, in ComponentName initializer);
    String getDeviceInitializer();
    ComponentName getDeviceInitializerComponent();

    void setUserIcon(in ComponentName admin, in Bitmap icon);

    void setSystemUpdatePolicy(in ComponentName who, in SystemUpdatePolicy policy);
    SystemUpdatePolicy getSystemUpdatePolicy();

    boolean setKeyguardDisabled(in ComponentName admin, boolean disabled);
    boolean setStatusBarDisabled(in ComponentName who, boolean disabled);
    boolean getDoNotAskCredentialsOnBoot();

    void notifyPendingSystemUpdate(in long updateReceivedTime);

    void setPermissionPolicy(in ComponentName admin, int policy);
    int  getPermissionPolicy(in ComponentName admin);
    boolean setPermissionGrantState(in ComponentName admin, String packageName,
            String permission, int grantState);
    int getPermissionGrantState(in ComponentName admin, String packageName, String permission);

    boolean requireSecureKeyguard(int userHandle);
}
