/*
 * Copyright (C) 2007 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

package android.content.pm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Overall information about the contents of a package.  This corresponds
 * to all of the information collected from AndroidManifest.xml.
 */
public class PackageInfo implements Parcelable {
    /**
     * The name of this package.  From the &lt;manifest&gt; tag's "name"
     * attribute.
     */
    public String packageName;

    /**
     * The version number of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_versionCode versionCode}
     * attribute.
     */
    public int versionCode;
    
    /**
     * The version name of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_versionName versionName}
     * attribute.
     */
    public String versionName;
    
    /**
     * The shared user ID name of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_sharedUserId sharedUserId}
     * attribute.
     */
    public String sharedUserId;
    
    /**
     * The shared user ID label of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_sharedUserLabel sharedUserLabel}
     * attribute.
     */
    public int sharedUserLabel;
    
    /**
     * Information collected from the &lt;application&gt; tag, or null if
     * there was none.
     */
    public ApplicationInfo applicationInfo;
    
    /**
     * The time at which the app was first installed.  Units are as
     * per {@link System#currentTimeMillis()}.
     */
    public long firstInstallTime;

    /**
     * The time at which the app was last updated.  Units are as
     * per {@link System#currentTimeMillis()}.
     */
    public long lastUpdateTime;

    /**
     * All kernel group-IDs that have been assigned to this package.
     * This is only filled in if the flag {@link PackageManager#GET_GIDS} was set.
     */
    public int[] gids;

    /**
     * Array of all {@link android.R.styleable#AndroidManifestActivity
     * &lt;activity&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_ACTIVITIES} was set.
     */
    public ActivityInfo[] activities;

    /**
     * Array of all {@link android.R.styleable#AndroidManifestReceiver
     * &lt;receiver&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_RECEIVERS} was set.
     */
    public ActivityInfo[] receivers;

    /**
     * Array of all {@link android.R.styleable#AndroidManifestService
     * &lt;service&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_SERVICES} was set.
     */
    public ServiceInfo[] services;

    /**
     * Array of all {@link android.R.styleable#AndroidManifestProvider
     * &lt;provider&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PROVIDERS} was set.
     */
    public ProviderInfo[] providers;

    /**
     * Array of all {@link android.R.styleable#AndroidManifestInstrumentation
     * &lt;instrumentation&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_INSTRUMENTATION} was set.
     */
    public InstrumentationInfo[] instrumentation;
    
    /**
     * Array of all {@link android.R.styleable#AndroidManifestPermission
     * &lt;permission&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PERMISSIONS} was set.
     */
    public PermissionInfo[] permissions;
    
    /**
     * Array of all {@link android.R.styleable#AndroidManifestUsesPermission
     * &lt;uses-permission&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PERMISSIONS} was set.  This list includes
     * all permissions requested, even those that were not granted or known
     * by the system at install time.
     */
    public String[] requestedPermissions;
    
    /**
     * Array of flags of all {@link android.R.styleable#AndroidManifestUsesPermission
     * &lt;uses-permission&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PERMISSIONS} was set.  Each value matches
     * the corresponding entry in {@link #requestedPermissions}, and will have
     * the flags {@link #REQUESTED_PERMISSION_REQUIRED} and
     * {@link #REQUESTED_PERMISSION_GRANTED} set as appropriate.
     */
    public int[] requestedPermissionsFlags;

    /**
     * Flag for {@link #requestedPermissionsFlags}: the requested permission
     * is required for the application to run; the user can not optionally
     * disable it.  Currently all permissions are required.
     */
    public static final int REQUESTED_PERMISSION_REQUIRED = 1<<0;

    /**
     * Flag for {@link #requestedPermissionsFlags}: the requested permission
     * is currently granted to the application.
     */
    public static final int REQUESTED_PERMISSION_GRANTED = 1<<1;

    /**
     * Array of all signatures read from the package file.  This is only filled
     * in if the flag {@link PackageManager#GET_SIGNATURES} was set.
     */
    public Signature[] signatures;
    
    /**
     * Application specified preferred configuration
     * {@link android.R.styleable#AndroidManifestUsesConfiguration
     * &lt;uses-configuration&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none. This is only filled in if the flag
     * {@link PackageManager#GET_CONFIGURATIONS} was set.  
     */
    public ConfigurationInfo[] configPreferences;

    /**
     * The features that this application has said it requires.
     */
    public FeatureInfo[] reqFeatures;

    /**
     * Constant corresponding to <code>auto</code> in
     * the {@link android.R.attr#installLocation} attribute.
     * @hide
     */
    public static final int INSTALL_LOCATION_UNSPECIFIED = -1;
    /**
     * Constant corresponding to <code>auto</code> in
     * the {@link android.R.attr#installLocation} attribute.
     * @hide
     */
    public static final int INSTALL_LOCATION_AUTO = 0;
    /**
     * Constant corresponding to <code>internalOnly</code> in
     * the {@link android.R.attr#installLocation} attribute.
     * @hide
     */
    public static final int INSTALL_LOCATION_INTERNAL_ONLY = 1;
    /**
     * Constant corresponding to <code>preferExternal</code> in
     * the {@link android.R.attr#installLocation} attribute.
     * @hide
     */
    public static final int INSTALL_LOCATION_PREFER_EXTERNAL = 2;
    /**
     * The install location requested by the activity.  From the
     * {@link android.R.attr#installLocation} attribute, one of
     * {@link #INSTALL_LOCATION_AUTO},
     * {@link #INSTALL_LOCATION_INTERNAL_ONLY},
     * {@link #INSTALL_LOCATION_PREFER_EXTERNAL}
     * @hide
     */
    public int installLocation = INSTALL_LOCATION_INTERNAL_ONLY;

    // Is Theme Apk
    /**
     * {@hide}
     */
    public boolean isThemeApk = false;

    /**
     * {@hide}
     */
    public boolean hasIconPack = false;

    /**
     * {@hide}
     */
    public ArrayList<String> mOverlayTargets;

    // Is Legacy Theme Apk
    /**
     * {@hide}
     */
    public boolean isLegacyThemeApk = false;

    // Is Legacy Icon Apk
    /**
     * {@hide}
     */
    public boolean isLegacyIconPackApk = false;

    // ThemeInfo
    /**
     * {@hide}
     */
    public ThemeInfo [] themeInfos;

    // ThemeInfo
    /**
     * {@hide}
     */
    public LegacyThemeInfo [] legacyThemeInfos;

    /**
     * Contains a mapping of packages and their redirected resources found in
     * legacy themes.
     *
     * i.e
     * com.android.systemui
     *      |--- color/status_bar_clock_color -> color/com_android_systemui_status_bar_clock_color
     *      |--- drawable/ic_notifications -> drawable/com_android_systemui_ic_notifications
     *      |--- ...
     *      |--- ...
     * com.android.settings
     *      |--- dimen/normal_height -> dimen/com_android_settings_normal_height
     *      |--- drawable/ic_location -> drawable/com_android_settings_ic_location
     *      |--- drawable/ic_menu_trash_holo_dark -> drawable/com_android_settings_ic_menu_trash
     *      |--- ...
     *      |--- ...
     *
     *  {@hide}
     */
    public Map<String, Map<String, String>> packageRedirections;

    /** @hide */
    public boolean requiredForAllUsers;

    /** @hide */
    public String restrictedAccountType;

    /** @hide */
    public String requiredAccountType;

    /**
     * What package, if any, this package will overlay.
     *
     * Package name of target package, or null.
     * @hide
     */
    public String overlayTarget;

    public PackageInfo() {
    }

    public String toString() {
        return "PackageInfo{"
            + Integer.toHexString(System.identityHashCode(this))
            + " " + packageName + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(packageName);
        dest.writeInt(versionCode);
        dest.writeString(versionName);
        dest.writeString(sharedUserId);
        dest.writeInt(sharedUserLabel);
        if (applicationInfo != null) {
            dest.writeInt(1);
            applicationInfo.writeToParcel(dest, parcelableFlags);
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(firstInstallTime);
        dest.writeLong(lastUpdateTime);
        dest.writeIntArray(gids);
        dest.writeTypedArray(activities, parcelableFlags);
        dest.writeTypedArray(receivers, parcelableFlags);
        dest.writeTypedArray(services, parcelableFlags);
        dest.writeTypedArray(providers, parcelableFlags);
        dest.writeTypedArray(instrumentation, parcelableFlags);
        dest.writeTypedArray(permissions, parcelableFlags);
        dest.writeStringArray(requestedPermissions);
        dest.writeIntArray(requestedPermissionsFlags);
        dest.writeTypedArray(signatures, parcelableFlags);
        dest.writeTypedArray(configPreferences, parcelableFlags);
        dest.writeTypedArray(reqFeatures, parcelableFlags);
        dest.writeInt(installLocation);
        dest.writeInt(requiredForAllUsers ? 1 : 0);
        dest.writeString(restrictedAccountType);
        dest.writeString(requiredAccountType);

        /* Theme-specific. */
        dest.writeInt((isThemeApk)? 1 : 0);
        dest.writeStringList(mOverlayTargets);
        dest.writeTypedArray(themeInfos, parcelableFlags);
        dest.writeInt(hasIconPack ? 1 : 0);
        /* Legacy Theme-specific. */
        dest.writeInt((isLegacyThemeApk) ? 1 : 0);
        dest.writeInt((isLegacyIconPackApk) ? 1 : 0);
        writeRedirectionsMap(dest);
        dest.writeTypedArray(legacyThemeInfos, parcelableFlags);
    }

    public static final Parcelable.Creator<PackageInfo> CREATOR
            = new Parcelable.Creator<PackageInfo>() {
        public PackageInfo createFromParcel(Parcel source) {
            return new PackageInfo(source);
        }

        public PackageInfo[] newArray(int size) {
            return new PackageInfo[size];
        }
    };

    private PackageInfo(Parcel source) {
        packageName = source.readString();
        versionCode = source.readInt();
        versionName = source.readString();
        sharedUserId = source.readString();
        sharedUserLabel = source.readInt();
        int hasApp = source.readInt();
        if (hasApp != 0) {
            applicationInfo = ApplicationInfo.CREATOR.createFromParcel(source);
        }
        firstInstallTime = source.readLong();
        lastUpdateTime = source.readLong();
        gids = source.createIntArray();
        activities = source.createTypedArray(ActivityInfo.CREATOR);
        receivers = source.createTypedArray(ActivityInfo.CREATOR);
        services = source.createTypedArray(ServiceInfo.CREATOR);
        providers = source.createTypedArray(ProviderInfo.CREATOR);
        instrumentation = source.createTypedArray(InstrumentationInfo.CREATOR);
        permissions = source.createTypedArray(PermissionInfo.CREATOR);
        requestedPermissions = source.createStringArray();
        requestedPermissionsFlags = source.createIntArray();
        signatures = source.createTypedArray(Signature.CREATOR);
        configPreferences = source.createTypedArray(ConfigurationInfo.CREATOR);
        reqFeatures = source.createTypedArray(FeatureInfo.CREATOR);
        installLocation = source.readInt();
        requiredForAllUsers = source.readInt() != 0;
        restrictedAccountType = source.readString();
        requiredAccountType = source.readString();

        /* Theme-specific. */
        isThemeApk = (source.readInt() != 0);
        mOverlayTargets = source.createStringArrayList();
        themeInfos = source.createTypedArray(ThemeInfo.CREATOR);
        hasIconPack = source.readInt() == 1;
        /* Legacy Theme-specific. */
        isLegacyThemeApk = (source.readInt() != 0);
        isLegacyIconPackApk = (source.readInt() != 0);
        readRedirectionsMap(source);
        legacyThemeInfos = source.createTypedArray(LegacyThemeInfo.CREATOR);
    }

    /**
     * Writes the packageRedirections map to the given parcel
     * @param dest
     */
    private void writeRedirectionsMap(Parcel dest) {
        if (packageRedirections == null) {
            dest.writeInt(0);
            return;
        }
        final int numPackages = packageRedirections.size();
        dest.writeInt(numPackages);
        Set<String> pkgs = packageRedirections.keySet();
        for (String pkg : pkgs) {
            dest.writeString(pkg);
            Map<String, String> redirectionsMap = packageRedirections.get(pkg);
            final int numRedirections = redirectionsMap.size();
            dest.writeInt(numRedirections);
            Set<String> redirectKeys = redirectionsMap.keySet();
            for (String redirectKey : redirectKeys) {
                dest.writeString(redirectKey);
                dest.writeString(redirectionsMap.get(redirectKey));
            }
        }
    }

    /**
     * Reads back packageRedirections map from the given parcel
     * @param source
     */
    private void readRedirectionsMap(Parcel source) {
        final int numPackages = source.readInt();
        for (int i = 0; i < numPackages; i++) {
            String pkg = source.readString();
            final int numRedirections = source.readInt();
            Map<String, String> redirectrionsMap = new HashMap<String, String>();
            for (int j = 0; j < numRedirections; j++) {
                redirectrionsMap.put(source.readString(), source.readString());
            }
            packageRedirections.put(pkg, redirectrionsMap);
        }
    }
}
