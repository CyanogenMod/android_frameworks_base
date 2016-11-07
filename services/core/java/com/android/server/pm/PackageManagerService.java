/*
 * Copyright (c) 2013-2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2006 The Android Open Source Project
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
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_MEDIA_STORAGE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DELETE_KEEP_DATA;
import static android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_POLICY_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.INSTALL_EXTERNAL;
import static android.content.pm.PackageManager.INSTALL_FAILED_ALREADY_EXISTS;
import static android.content.pm.PackageManager.INSTALL_FAILED_CONFLICTING_PROVIDER;
import static android.content.pm.PackageManager.INSTALL_FAILED_DUPLICATE_PACKAGE;
import static android.content.pm.PackageManager.INSTALL_FAILED_DUPLICATE_PERMISSION;
import static android.content.pm.PackageManager.INSTALL_FAILED_EPHEMERAL_INVALID;
import static android.content.pm.PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
import static android.content.pm.PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
import static android.content.pm.PackageManager.INSTALL_FAILED_INVALID_APK;
import static android.content.pm.PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
import static android.content.pm.PackageManager.INSTALL_FAILED_MISSING_SHARED_LIBRARY;
import static android.content.pm.PackageManager.INSTALL_FAILED_PACKAGE_CHANGED;
import static android.content.pm.PackageManager.INSTALL_FAILED_REPLACE_COULDNT_DELETE;
import static android.content.pm.PackageManager.INSTALL_FAILED_SHARED_USER_INCOMPATIBLE;
import static android.content.pm.PackageManager.INSTALL_FAILED_TEST_ONLY;
import static android.content.pm.PackageManager.INSTALL_FAILED_UPDATE_INCOMPATIBLE;
import static android.content.pm.PackageManager.INSTALL_FAILED_USER_RESTRICTED;
import static android.content.pm.PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE;
import static android.content.pm.PackageManager.INSTALL_FORWARD_LOCK;
import static android.content.pm.PackageManager.INSTALL_INTERNAL;
import static android.content.pm.PackageManager.INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
import static android.content.pm.PackageManager.MATCH_ALL;
import static android.content.pm.PackageManager.MATCH_DEBUG_TRIAGED_MISSING;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_FACTORY_ONLY;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.MOVE_FAILED_DEVICE_ADMIN;
import static android.content.pm.PackageManager.MOVE_FAILED_DOESNT_EXIST;
import static android.content.pm.PackageManager.MOVE_FAILED_INTERNAL_ERROR;
import static android.content.pm.PackageManager.MOVE_FAILED_OPERATION_PENDING;
import static android.content.pm.PackageManager.MOVE_FAILED_SYSTEM_PACKAGE;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.content.pm.PackageParser.PARSE_IS_PRIVILEGED;
import static android.content.pm.PackageParser.isApkFile;
import static android.os.Trace.TRACE_TAG_PACKAGE_MANAGER;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_RDWR;

import static com.android.internal.app.IntentForwarderActivity.FORWARD_INTENT_TO_MANAGED_PROFILE;
import static com.android.internal.app.IntentForwarderActivity.FORWARD_INTENT_TO_PARENT;
import static com.android.internal.content.NativeLibraryHelper.LIB64_DIR_NAME;
import static com.android.internal.content.NativeLibraryHelper.LIB_DIR_NAME;
import static com.android.internal.util.ArrayUtils.appendInt;
import static com.android.server.pm.Installer.DEXOPT_PUBLIC;
import static com.android.server.pm.InstructionSets.getAppDexInstructionSets;
import static com.android.server.pm.InstructionSets.getDexCodeInstructionSet;
import static com.android.server.pm.InstructionSets.getDexCodeInstructionSets;
import static com.android.server.pm.InstructionSets.getPreferredInstructionSet;
import static com.android.server.pm.InstructionSets.getPrimaryInstructionSet;
import static com.android.server.pm.PackageManagerServiceCompilerMapping.getCompilerFilterForReason;
import static com.android.server.pm.PackageManagerServiceCompilerMapping.getFullCompilerFilter;
import static com.android.server.pm.PackageManagerServiceCompilerMapping.getNonProfileGuidedCompilerFilter;
import static com.android.server.pm.PermissionsState.PERMISSION_OPERATION_FAILURE;
import static com.android.server.pm.PermissionsState.PERMISSION_OPERATION_SUCCESS;
import static com.android.server.pm.PermissionsState.PERMISSION_OPERATION_SUCCESS_GIDS_CHANGED;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.ResourcesManager;
import android.app.admin.IDevicePolicyManager;
import android.app.admin.SecurityLog;
import android.app.backup.IBackupManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.AppsQueryHelper;
import android.content.pm.ComponentInfo;
import android.content.pm.EphemeralApplicationInfo;
import android.content.pm.EphemeralResolveInfo;
import android.content.pm.EphemeralResolveInfo.EphemeralDigest;
import android.content.pm.EphemeralResolveInfo.EphemeralResolveIntentInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.IOnPermissionsChangeListener;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageDeleteObserver2;
import android.content.pm.IPackageInstallObserver2;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.InstrumentationInfo;
import android.content.pm.IntentFilterVerificationInfo;
import android.content.pm.KeySet;
import android.content.pm.PackageCleanItem;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInfoLite;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.LegacyPackageDeleteObserver;
import android.content.pm.PackageManagerInternal;
import android.content.pm.PackageParser;
import android.content.pm.PackageParser.ActivityIntentInfo;
import android.content.pm.PackageParser.PackageLite;
import android.content.pm.PackageParser.PackageParserException;
import android.content.pm.PackageStats;
import android.content.pm.PackageUserState;
import android.content.pm.ParceledListSlice;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.content.pm.VerifierDeviceIdentity;
import android.content.pm.VerifierInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Environment.UserEnvironment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.UserManagerInternal;
import android.os.storage.IMountService;
import android.os.storage.MountServiceInternal;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.provider.Settings.Global;
import android.security.KeyStore;
import android.security.SystemKeyStore;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.ExceptionUtils;
import android.util.Log;
import android.util.LogPrinter;
import android.util.MathUtils;
import android.util.PrintStreamPrinter;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.Xml;
import android.util.jar.StrictJarFile;
import android.view.Display;

import cyanogenmod.providers.CMSettings;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.IMediaContainerService;
import com.android.internal.app.ResolverActivity;
import com.android.internal.content.NativeLibraryHelper;
import com.android.internal.content.PackageHelper;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.os.IParcelFileDescriptorFactory;
import com.android.internal.os.InstallerConnection.InstallerException;
import com.android.internal.os.RegionalizationEnvironment;
import com.android.internal.os.SomeArgs;
import com.android.internal.os.Zygote;
import com.android.internal.telephony.CarrierAppUtils;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FastPrintWriter;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;
import com.android.internal.util.XmlUtils;
import com.android.server.AttributeCache;
import com.android.server.EventLogTags;
import com.android.server.FgThread;
import com.android.server.IntentResolver;
import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.SystemConfig;
import com.android.server.Watchdog;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.pm.PermissionsState.PermissionState;
import com.android.server.pm.Settings.DatabaseVersion;
import com.android.server.pm.Settings.VersionInfo;
import com.android.server.storage.DeviceStorageMonitorInternal;

import dalvik.system.CloseGuard;
import dalvik.system.DexFile;
import dalvik.system.VMRuntime;

import libcore.io.IoUtils;
import libcore.util.EmptyArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keep track of all those APKs everywhere.
 * <p>
 * Internally there are two important locks:
 * <ul>
 * <li>{@link #mPackages} is used to guard all in-memory parsed package details
 * and other related state. It is a fine-grained lock that should only be held
 * momentarily, as it's one of the most contended locks in the system.
 * <li>{@link #mInstallLock} is used to guard all {@code installd} access, whose
 * operations typically involve heavy lifting of application data on disk. Since
 * {@code installd} is single-threaded, and it's operations can often be slow,
 * this lock should never be acquired while already holding {@link #mPackages}.
 * Conversely, it's safe to acquire {@link #mPackages} momentarily while already
 * holding {@link #mInstallLock}.
 * </ul>
 * Many internal methods rely on the caller to hold the appropriate locks, and
 * this contract is expressed through method name suffixes:
 * <ul>
 * <li>fooLI(): the caller must hold {@link #mInstallLock}
 * <li>fooLIF(): the caller must hold {@link #mInstallLock} and the package
 * being modified must be frozen
 * <li>fooLPr(): the caller must hold {@link #mPackages} for reading
 * <li>fooLPw(): the caller must hold {@link #mPackages} for writing
 * </ul>
 * <p>
 * Because this class is very central to the platform's security; please run all
 * CTS and unit tests whenever making modifications:
 *
 * <pre>
 * $ runtest -c android.content.pm.PackageManagerTests frameworks-core
 * $ cts-tradefed run commandAndExit cts -m AppSecurityTests
 * </pre>
 */
public class PackageManagerService extends IPackageManager.Stub {
    static final String TAG = "PackageManager";
    static final boolean DEBUG_SETTINGS = false;
    static final boolean DEBUG_PREFERRED = false;
    static final boolean DEBUG_UPGRADE = false;
    static final boolean DEBUG_DOMAIN_VERIFICATION = false;
    private static final boolean DEBUG_BACKUP = false;
    private static final boolean DEBUG_INSTALL = false;
    private static final boolean DEBUG_REMOVE = false;
    private static final boolean DEBUG_BROADCASTS = false;
    private static final boolean DEBUG_SHOW_INFO = false;
    private static final boolean DEBUG_PACKAGE_INFO = false;
    private static final boolean DEBUG_INTENT_MATCHING = false;
    private static final boolean DEBUG_PACKAGE_SCANNING = false;
    private static final boolean DEBUG_VERIFY = false;
    private static final boolean DEBUG_FILTERS = false;

    // Debug output for dexopting. This is shared between PackageManagerService, OtaDexoptService
    // and PackageDexOptimizer. All these classes have their own flag to allow switching a single
    // user, but by default initialize to this.
    static final boolean DEBUG_DEXOPT = false;

    private static final boolean DEBUG_ABI_SELECTION = false;
    private static final boolean DEBUG_EPHEMERAL = Build.IS_DEBUGGABLE;
    private static final boolean DEBUG_TRIAGED_MISSING = false;
    private static final boolean DEBUG_APP_DATA = false;
    private static final boolean DEBUG_PROTECTED = false;

    static final boolean CLEAR_RUNTIME_PERMISSIONS_ON_UPGRADE = false;

    private static final boolean DISABLE_EPHEMERAL_APPS = !Build.IS_DEBUGGABLE;

    private static final int RADIO_UID = Process.PHONE_UID;
    private static final int LOG_UID = Process.LOG_UID;
    private static final int NFC_UID = Process.NFC_UID;
    private static final int BLUETOOTH_UID = Process.BLUETOOTH_UID;
    private static final int SHELL_UID = Process.SHELL_UID;

    // Cap the size of permission trees that 3rd party apps can define
    private static final int MAX_PERMISSION_TREE_FOOTPRINT = 32768;     // characters of text

    // Suffix used during package installation when copying/moving
    // package apks to install directory.
    private static final String INSTALL_PACKAGE_SUFFIX = "-";

    static final int SCAN_NO_DEX = 1<<1;
    static final int SCAN_FORCE_DEX = 1<<2;
    static final int SCAN_UPDATE_SIGNATURE = 1<<3;
    static final int SCAN_NEW_INSTALL = 1<<4;
    static final int SCAN_NO_PATHS = 1<<5;
    static final int SCAN_UPDATE_TIME = 1<<6;
    static final int SCAN_DEFER_DEX = 1<<7;
    static final int SCAN_BOOTING = 1<<8;
    static final int SCAN_TRUSTED_OVERLAY = 1<<9;
    static final int SCAN_DELETE_DATA_ON_FAILURES = 1<<10;
    static final int SCAN_REPLACING = 1<<11;
    static final int SCAN_REQUIRE_KNOWN = 1<<12;
    static final int SCAN_MOVE = 1<<13;
    static final int SCAN_INITIAL = 1<<14;
    static final int SCAN_CHECK_ONLY = 1<<15;
    static final int SCAN_DONT_KILL_APP = 1<<17;
    static final int SCAN_IGNORE_FROZEN = 1<<18;

    static final int REMOVE_CHATTY = 1<<16;

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    /**
     * Timeout (in milliseconds) after which the watchdog should declare that
     * our handler thread is wedged.  The usual default for such things is one
     * minute but we sometimes do very lengthy I/O operations on this thread,
     * such as installing multi-gigabyte applications, so ours needs to be longer.
     */
    private static final long WATCHDOG_TIMEOUT = 1000*60*10;     // ten minutes

    /**
     * Wall-clock timeout (in milliseconds) after which we *require* that an fstrim
     * be run on this device.  We use the value in the Settings.Global.MANDATORY_FSTRIM_INTERVAL
     * settings entry if available, otherwise we use the hardcoded default.  If it's been
     * more than this long since the last fstrim, we force one during the boot sequence.
     *
     * This backstops other fstrim scheduling:  if the device is alive at midnight+idle,
     * one gets run at the next available charging+idle time.  This final mandatory
     * no-fstrim check kicks in only of the other scheduling criteria is never met.
     */
    private static final long DEFAULT_MANDATORY_FSTRIM_INTERVAL = 3 * DateUtils.DAY_IN_MILLIS;

    /**
     * Whether verification is enabled by default.
     */
    private static final boolean DEFAULT_VERIFY_ENABLE = true;

    /**
     * The default maximum time to wait for the verification agent to return in
     * milliseconds.
     */
    private static final long DEFAULT_VERIFICATION_TIMEOUT = 10 * 1000;

    /**
     * The default response for package verification timeout.
     *
     * This can be either PackageManager.VERIFICATION_ALLOW or
     * PackageManager.VERIFICATION_REJECT.
     */
    private static final int DEFAULT_VERIFICATION_RESPONSE = PackageManager.VERIFICATION_ALLOW;

    static final String PLATFORM_PACKAGE_NAME = "android";

    static final String DEFAULT_CONTAINER_PACKAGE = "com.android.defcontainer";

    static final ComponentName DEFAULT_CONTAINER_COMPONENT = new ComponentName(
            DEFAULT_CONTAINER_PACKAGE,
            "com.android.defcontainer.DefaultContainerService");

    private static final String KILL_APP_REASON_GIDS_CHANGED =
            "permission grant or revoke changed gids";

    private static final String KILL_APP_REASON_PERMISSIONS_REVOKED =
            "permissions revoked";

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private static final String VENDOR_OVERLAY_DIR = "/vendor/overlay";

    private static int DEFAULT_EPHEMERAL_HASH_PREFIX_MASK = 0xFFFFF000;
    private static int DEFAULT_EPHEMERAL_HASH_PREFIX_COUNT = 5;

    /** Permission grant: not grant the permission. */
    private static final int GRANT_DENIED = 1;

    /** Permission grant: grant the permission as an install permission. */
    private static final int GRANT_INSTALL = 2;

    /** Permission grant: grant the permission as a runtime one. */
    private static final int GRANT_RUNTIME = 3;

    /** Permission grant: grant as runtime a permission that was granted as an install time one. */
    private static final int GRANT_UPGRADE = 4;

    /** Canonical intent used to identify what counts as a "web browser" app */
    private static final Intent sBrowserIntent;
    static {
        sBrowserIntent = new Intent();
        sBrowserIntent.setAction(Intent.ACTION_VIEW);
        sBrowserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        sBrowserIntent.setData(Uri.parse("http:"));
    }

    /**
     * The set of all protected actions [i.e. those actions for which a high priority
     * intent filter is disallowed].
     */
    private static final Set<String> PROTECTED_ACTIONS = new ArraySet<>();
    static {
        PROTECTED_ACTIONS.add(Intent.ACTION_SEND);
        PROTECTED_ACTIONS.add(Intent.ACTION_SENDTO);
        PROTECTED_ACTIONS.add(Intent.ACTION_SEND_MULTIPLE);
        PROTECTED_ACTIONS.add(Intent.ACTION_VIEW);
    }

    // Compilation reasons.
    public static final int REASON_FIRST_BOOT = 0;
    public static final int REASON_BOOT = 1;
    public static final int REASON_INSTALL = 2;
    public static final int REASON_BACKGROUND_DEXOPT = 3;
    public static final int REASON_AB_OTA = 4;
    public static final int REASON_NON_SYSTEM_LIBRARY = 5;
    public static final int REASON_SHARED_APK = 6;
    public static final int REASON_FORCED_DEXOPT = 7;
    public static final int REASON_CORE_APP = 8;

    public static final int REASON_LAST = REASON_CORE_APP;

    /** Special library name that skips shared libraries check during compilation. */
    private static final String SKIP_SHARED_LIBRARY_CHECK = "&";

    private static final String PROTECTED_APPS_TARGET_VALIDATION_COMPONENT =
            "com.android.settings/com.android.settings.applications.ProtectedAppsActivity";

    final ServiceThread mHandlerThread;

    final PackageHandler mHandler;

    private final ProcessLoggingHandler mProcessLoggingHandler;

    /**
     * Messages for {@link #mHandler} that need to wait for system ready before
     * being dispatched.
     */
    private ArrayList<Message> mPostSystemReadyMessages;

    final int mSdkVersion = Build.VERSION.SDK_INT;

    final Context mContext;
    final boolean mFactoryTest;
    final boolean mOnlyCore;
    private boolean mOnlyPowerOffAlarm = false;
    final DisplayMetrics mMetrics;
    final int mDefParseFlags;
    final String[] mSeparateProcesses;
    final boolean mIsUpgrade;
    final boolean mIsPreNUpgrade;
    final boolean mIsAlarmBoot;

    /** The location for ASEC container files on internal storage. */
    final String mAsecInternalPath;

    // Used for privilege escalation. MUST NOT BE CALLED WITH mPackages
    // LOCK HELD.  Can be called with mInstallLock held.
    @GuardedBy("mInstallLock")
    final Installer mInstaller;

    /** Directory where installed third-party apps stored */
    final File mAppInstallDir;
    final File mEphemeralInstallDir;

    /**
     * Directory to which applications installed internally have their
     * 32 bit native libraries copied.
     */
    private File mAppLib32InstallDir;

    // Directory containing the private parts (e.g. code and non-resource assets) of forward-locked
    // apps.
    final File mDrmAppPrivateInstallDir;

    // Directory containing the regionalization 3rd apps.
    final File mRegionalizationAppInstallDir;

    // ----------------------------------------------------------------

    // Lock for state used when installing and doing other long running
    // operations.  Methods that must be called with this lock held have
    // the suffix "LI".
    final Object mInstallLock = new Object();

    // ----------------------------------------------------------------

    // Keys are String (package name), values are Package.  This also serves
    // as the lock for the global state.  Methods that must be called with
    // this lock held have the prefix "LP".
    @GuardedBy("mPackages")
    final ArrayMap<String, PackageParser.Package> mPackages =
            new ArrayMap<String, PackageParser.Package>();

    final ArrayMap<String, Set<String>> mKnownCodebase =
            new ArrayMap<String, Set<String>>();

    // Tracks available target package names -> overlay package paths.
    final ArrayMap<String, ArrayMap<String, PackageParser.Package>> mOverlays =
        new ArrayMap<String, ArrayMap<String, PackageParser.Package>>();

    /**
     * Tracks new system packages [received in an OTA] that we expect to
     * find updated user-installed versions. Keys are package name, values
     * are package location.
     */
    final private ArrayMap<String, File> mExpectingBetter = new ArrayMap<>();
    /**
     * Tracks high priority intent filters for protected actions. During boot, certain
     * filter actions are protected and should never be allowed to have a high priority
     * intent filter for them. However, there is one, and only one exception -- the
     * setup wizard. It must be able to define a high priority intent filter for these
     * actions to ensure there are no escapes from the wizard. We need to delay processing
     * of these during boot as we need to look at all of the system packages in order
     * to know which component is the setup wizard.
     */
    private final List<PackageParser.ActivityIntentInfo> mProtectedFilters = new ArrayList<>();
    /**
     * Whether or not processing protected filters should be deferred.
     */
    private boolean mDeferProtectedFilters = true;

    /**
     * Tracks existing system packages prior to receiving an OTA. Keys are package name.
     */
    final private ArraySet<String> mExistingSystemPackages = new ArraySet<>();
    /**
     * Whether or not system app permissions should be promoted from install to runtime.
     */
    boolean mPromoteSystemApps;

    @GuardedBy("mPackages")
    final Settings mSettings;

    /**
     * Set of package names that are currently "frozen", which means active
     * surgery is being done on the code/data for that package. The platform
     * will refuse to launch frozen packages to avoid race conditions.
     *
     * @see PackageFreezer
     */
    @GuardedBy("mPackages")
    final ArraySet<String> mFrozenPackages = new ArraySet<>();

    final ProtectedPackages mProtectedPackages;

    boolean mFirstBoot;

    // System configuration read by SystemConfig.
    final int[] mGlobalGids;
    final SparseArray<ArraySet<String>> mSystemPermissions;
    final ArrayMap<String, FeatureInfo> mAvailableFeatures;
    final ArrayMap<Signature, ArraySet<String>> mSignatureAllowances;

    // If mac_permissions.xml was found for seinfo labeling.
    boolean mFoundPolicyFile;

    private final EphemeralApplicationRegistry mEphemeralApplicationRegistry;

    public static final class SharedLibraryEntry {
        public final String path;
        public final String apk;

        SharedLibraryEntry(String _path, String _apk) {
            path = _path;
            apk = _apk;
        }
    }

    // Currently known shared libraries.
    final ArrayMap<String, SharedLibraryEntry> mSharedLibraries =
            new ArrayMap<String, SharedLibraryEntry>();

    // All available activities, for your resolving pleasure.
    final ActivityIntentResolver mActivities =
            new ActivityIntentResolver();

    // All available receivers, for your resolving pleasure.
    final ActivityIntentResolver mReceivers =
            new ActivityIntentResolver();

    // All available services, for your resolving pleasure.
    final ServiceIntentResolver mServices = new ServiceIntentResolver();

    // All available providers, for your resolving pleasure.
    final ProviderIntentResolver mProviders = new ProviderIntentResolver();

    // Mapping from provider base names (first directory in content URI codePath)
    // to the provider information.
    final ArrayMap<String, PackageParser.Provider> mProvidersByAuthority =
            new ArrayMap<String, PackageParser.Provider>();

    // Mapping from instrumentation class names to info about them.
    final ArrayMap<ComponentName, PackageParser.Instrumentation> mInstrumentation =
            new ArrayMap<ComponentName, PackageParser.Instrumentation>();

    // Mapping from permission names to info about them.
    final ArrayMap<String, PackageParser.PermissionGroup> mPermissionGroups =
            new ArrayMap<String, PackageParser.PermissionGroup>();

    // Packages whose data we have transfered into another package, thus
    // should no longer exist.
    final ArraySet<String> mTransferedPackages = new ArraySet<String>();

    // Broadcast actions that are only available to the system.
    final ArrayMap<String, String> mProtectedBroadcasts = new ArrayMap<>();

    /** List of packages waiting for verification. */
    final SparseArray<PackageVerificationState> mPendingVerification
            = new SparseArray<PackageVerificationState>();

    /** Set of packages associated with each app op permission. */
    final ArrayMap<String, ArraySet<String>> mAppOpPermissionPackages = new ArrayMap<>();

    final PackageInstallerService mInstallerService;

    private final PackageDexOptimizer mPackageDexOptimizer;

    private AtomicInteger mNextMoveId = new AtomicInteger();
    private final MoveCallbacks mMoveCallbacks;

    private final OnPermissionChangeListeners mOnPermissionChangeListeners;

    // Cache of users who need badging.
    SparseBooleanArray mUserNeedsBadging = new SparseBooleanArray();

    /** Token for keys in mPendingVerification. */
    private int mPendingVerificationToken = 0;

    volatile boolean mSystemReady;
    volatile boolean mSafeMode;
    volatile boolean mHasSystemUidErrors;

    ApplicationInfo mAndroidApplication;
    final ActivityInfo mResolveActivity = new ActivityInfo();
    final ResolveInfo mResolveInfo = new ResolveInfo();
    ComponentName mResolveComponentName;
    PackageParser.Package mPlatformPackage;
    ComponentName mCustomResolverComponentName;

    boolean mResolverReplaced = false;

    private final @Nullable ComponentName mIntentFilterVerifierComponent;
    private final @Nullable IntentFilterVerifier<ActivityIntentInfo> mIntentFilterVerifier;

    private int mIntentFilterVerificationToken = 0;

    /** Component that knows whether or not an ephemeral application exists */
    final ComponentName mEphemeralResolverComponent;
    /** The service connection to the ephemeral resolver */
    final EphemeralResolverConnection mEphemeralResolverConnection;

    /** Component used to install ephemeral applications */
    final ComponentName mEphemeralInstallerComponent;
    final ActivityInfo mEphemeralInstallerActivity = new ActivityInfo();
    final ResolveInfo mEphemeralInstallerInfo = new ResolveInfo();

    final SparseArray<IntentFilterVerificationState> mIntentFilterVerificationStates
            = new SparseArray<IntentFilterVerificationState>();

    final DefaultPermissionGrantPolicy mDefaultPermissionPolicy;

    // List of packages names to keep cached, even if they are uninstalled for all users
    private List<String> mKeepUninstalledPackages;

    private UserManagerInternal mUserManagerInternal;

    private static class IFVerificationParams {
        PackageParser.Package pkg;
        boolean replacing;
        int userId;
        int verifierUid;

        public IFVerificationParams(PackageParser.Package _pkg, boolean _replacing,
                int _userId, int _verifierUid) {
            pkg = _pkg;
            replacing = _replacing;
            userId = _userId;
            replacing = _replacing;
            verifierUid = _verifierUid;
        }
    }

    private interface IntentFilterVerifier<T extends IntentFilter> {
        boolean addOneIntentFilterVerification(int verifierId, int userId, int verificationId,
                                               T filter, String packageName);
        void startVerifications(int userId);
        void receiveVerificationResponse(int verificationId);
    }

    private class IntentVerifierProxy implements IntentFilterVerifier<ActivityIntentInfo> {
        private Context mContext;
        private ComponentName mIntentFilterVerifierComponent;
        private ArrayList<Integer> mCurrentIntentFilterVerifications = new ArrayList<Integer>();

        public IntentVerifierProxy(Context context, ComponentName verifierComponent) {
            mContext = context;
            mIntentFilterVerifierComponent = verifierComponent;
        }

        private String getDefaultScheme() {
            return IntentFilter.SCHEME_HTTPS;
        }

        @Override
        public void startVerifications(int userId) {
            // Launch verifications requests
            int count = mCurrentIntentFilterVerifications.size();
            for (int n=0; n<count; n++) {
                int verificationId = mCurrentIntentFilterVerifications.get(n);
                final IntentFilterVerificationState ivs =
                        mIntentFilterVerificationStates.get(verificationId);

                String packageName = ivs.getPackageName();

                ArrayList<PackageParser.ActivityIntentInfo> filters = ivs.getFilters();
                final int filterCount = filters.size();
                ArraySet<String> domainsSet = new ArraySet<>();
                for (int m=0; m<filterCount; m++) {
                    PackageParser.ActivityIntentInfo filter = filters.get(m);
                    domainsSet.addAll(filter.getHostsList());
                }
                ArrayList<String> domainsList = new ArrayList<>(domainsSet);
                synchronized (mPackages) {
                    if (mSettings.createIntentFilterVerificationIfNeededLPw(
                            packageName, domainsList) != null) {
                        scheduleWriteSettingsLocked();
                    }
                }
                sendVerificationRequest(userId, verificationId, ivs);
            }
            mCurrentIntentFilterVerifications.clear();
        }

        private void sendVerificationRequest(int userId, int verificationId,
                IntentFilterVerificationState ivs) {

            Intent verificationIntent = new Intent(Intent.ACTION_INTENT_FILTER_NEEDS_VERIFICATION);
            verificationIntent.putExtra(
                    PackageManager.EXTRA_INTENT_FILTER_VERIFICATION_ID,
                    verificationId);
            verificationIntent.putExtra(
                    PackageManager.EXTRA_INTENT_FILTER_VERIFICATION_URI_SCHEME,
                    getDefaultScheme());
            verificationIntent.putExtra(
                    PackageManager.EXTRA_INTENT_FILTER_VERIFICATION_HOSTS,
                    ivs.getHostsString());
            verificationIntent.putExtra(
                    PackageManager.EXTRA_INTENT_FILTER_VERIFICATION_PACKAGE_NAME,
                    ivs.getPackageName());
            verificationIntent.setComponent(mIntentFilterVerifierComponent);
            verificationIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            UserHandle user = new UserHandle(userId);
            mContext.sendBroadcastAsUser(verificationIntent, user);
            if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                    "Sending IntentFilter verification broadcast");
        }

        public void receiveVerificationResponse(int verificationId) {
            IntentFilterVerificationState ivs = mIntentFilterVerificationStates.get(verificationId);

            final boolean verified = ivs.isVerified();

            ArrayList<PackageParser.ActivityIntentInfo> filters = ivs.getFilters();
            final int count = filters.size();
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.i(TAG, "Received verification response " + verificationId
                        + " for " + count + " filters, verified=" + verified);
            }
            for (int n=0; n<count; n++) {
                PackageParser.ActivityIntentInfo filter = filters.get(n);
                filter.setVerified(verified);

                if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG, "IntentFilter " + filter.toString()
                        + " verified with result:" + verified + " and hosts:"
                        + ivs.getHostsString());
            }

            mIntentFilterVerificationStates.remove(verificationId);

            final String packageName = ivs.getPackageName();
            IntentFilterVerificationInfo ivi = null;

            synchronized (mPackages) {
                ivi = mSettings.getIntentFilterVerificationLPr(packageName);
            }
            if (ivi == null) {
                Slog.w(TAG, "IntentFilterVerificationInfo not found for verificationId:"
                        + verificationId + " packageName:" + packageName);
                return;
            }
            if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                    "Updating IntentFilterVerificationInfo for package " + packageName
                            +" verificationId:" + verificationId);

            synchronized (mPackages) {
                if (verified) {
                    ivi.setStatus(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS);
                } else {
                    ivi.setStatus(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK);
                }
                scheduleWriteSettingsLocked();

                final int userId = ivs.getUserId();
                if (userId != UserHandle.USER_ALL) {
                    final int userStatus =
                            mSettings.getIntentFilterVerificationStatusLPr(packageName, userId);

                    int updatedStatus = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
                    boolean needUpdate = false;

                    // We cannot override the STATUS_ALWAYS / STATUS_NEVER states if they have
                    // already been set by the User thru the Disambiguation dialog
                    switch (userStatus) {
                        case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED:
                            if (verified) {
                                updatedStatus = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
                            } else {
                                updatedStatus = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
                            }
                            needUpdate = true;
                            break;

                        case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK:
                            if (verified) {
                                updatedStatus = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
                                needUpdate = true;
                            }
                            break;

                        default:
                            // Nothing to do
                    }

                    if (needUpdate) {
                        mSettings.updateIntentFilterVerificationStatusLPw(
                                packageName, updatedStatus, userId);
                        scheduleWritePackageRestrictionsLocked(userId);
                    }
                }
            }
        }

        @Override
        public boolean addOneIntentFilterVerification(int verifierUid, int userId, int verificationId,
                    ActivityIntentInfo filter, String packageName) {
            if (!hasValidDomains(filter)) {
                return false;
            }
            IntentFilterVerificationState ivs = mIntentFilterVerificationStates.get(verificationId);
            if (ivs == null) {
                ivs = createDomainVerificationState(verifierUid, userId, verificationId,
                        packageName);
            }
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.d(TAG, "Adding verification filter for " + packageName + ": " + filter);
            }
            ivs.addFilter(filter);
            return true;
        }

        private IntentFilterVerificationState createDomainVerificationState(int verifierUid,
                int userId, int verificationId, String packageName) {
            IntentFilterVerificationState ivs = new IntentFilterVerificationState(
                    verifierUid, userId, packageName);
            ivs.setPendingState();
            synchronized (mPackages) {
                mIntentFilterVerificationStates.append(verificationId, ivs);
                mCurrentIntentFilterVerifications.add(verificationId);
            }
            return ivs;
        }
    }

    private static boolean hasValidDomains(ActivityIntentInfo filter) {
        return filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                && (filter.hasDataScheme(IntentFilter.SCHEME_HTTP) ||
                        filter.hasDataScheme(IntentFilter.SCHEME_HTTPS));
    }

    ArrayList<ComponentName> mDisabledComponentsList;

    private AppOpsManager mAppOps;

    // Set of pending broadcasts for aggregating enable/disable of components.
    static class PendingPackageBroadcasts {
        // for each user id, a map of <package name -> components within that package>
        final SparseArray<ArrayMap<String, ArrayList<String>>> mUidMap;

        public PendingPackageBroadcasts() {
            mUidMap = new SparseArray<ArrayMap<String, ArrayList<String>>>(2);
        }

        public ArrayList<String> get(int userId, String packageName) {
            ArrayMap<String, ArrayList<String>> packages = getOrAllocate(userId);
            return packages.get(packageName);
        }

        public void put(int userId, String packageName, ArrayList<String> components) {
            ArrayMap<String, ArrayList<String>> packages = getOrAllocate(userId);
            packages.put(packageName, components);
        }

        public void remove(int userId, String packageName) {
            ArrayMap<String, ArrayList<String>> packages = mUidMap.get(userId);
            if (packages != null) {
                packages.remove(packageName);
            }
        }

        public void remove(int userId) {
            mUidMap.remove(userId);
        }

        public int userIdCount() {
            return mUidMap.size();
        }

        public int userIdAt(int n) {
            return mUidMap.keyAt(n);
        }

        public ArrayMap<String, ArrayList<String>> packagesForUserId(int userId) {
            return mUidMap.get(userId);
        }

        public int size() {
            // total number of pending broadcast entries across all userIds
            int num = 0;
            for (int i = 0; i< mUidMap.size(); i++) {
                num += mUidMap.valueAt(i).size();
            }
            return num;
        }

        public void clear() {
            mUidMap.clear();
        }

        private ArrayMap<String, ArrayList<String>> getOrAllocate(int userId) {
            ArrayMap<String, ArrayList<String>> map = mUidMap.get(userId);
            if (map == null) {
                map = new ArrayMap<String, ArrayList<String>>();
                mUidMap.put(userId, map);
            }
            return map;
        }
    }
    final PendingPackageBroadcasts mPendingBroadcasts = new PendingPackageBroadcasts();

    // Service Connection to remote media container service to copy
    // package uri's from external media onto secure containers
    // or internal storage.
    private IMediaContainerService mContainerService = null;

    static final int SEND_PENDING_BROADCAST = 1;
    static final int MCS_BOUND = 3;
    static final int END_COPY = 4;
    static final int INIT_COPY = 5;
    static final int MCS_UNBIND = 6;
    static final int START_CLEANING_PACKAGE = 7;
    static final int FIND_INSTALL_LOC = 8;
    static final int POST_INSTALL = 9;
    static final int MCS_RECONNECT = 10;
    static final int MCS_GIVE_UP = 11;
    static final int UPDATED_MEDIA_STATUS = 12;
    static final int WRITE_SETTINGS = 13;
    static final int WRITE_PACKAGE_RESTRICTIONS = 14;
    static final int PACKAGE_VERIFIED = 15;
    static final int CHECK_PENDING_VERIFICATION = 16;
    static final int START_INTENT_FILTER_VERIFICATIONS = 17;
    static final int INTENT_FILTER_VERIFIED = 18;
    static final int WRITE_PACKAGE_LIST = 19;

    static final int WRITE_SETTINGS_DELAY = 10*1000;  // 10 seconds

    // Delay time in millisecs
    static final int BROADCAST_DELAY = 10 * 1000;

    static UserManagerService sUserManager;

    // Stores a list of users whose package restrictions file needs to be updated
    private ArraySet<Integer> mDirtyUsers = new ArraySet<Integer>();

    final private DefaultContainerConnection mDefContainerConn =
            new DefaultContainerConnection();
    class DefaultContainerConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG_SD_INSTALL) Log.i(TAG, "onServiceConnected");
            IMediaContainerService imcs =
                IMediaContainerService.Stub.asInterface(service);
            mHandler.sendMessage(mHandler.obtainMessage(MCS_BOUND, imcs));
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG_SD_INSTALL) Log.i(TAG, "onServiceDisconnected");
        }
    }

    // Recordkeeping of restore-after-install operations that are currently in flight
    // between the Package Manager and the Backup Manager
    static class PostInstallData {
        public InstallArgs args;
        public PackageInstalledInfo res;

        PostInstallData(InstallArgs _a, PackageInstalledInfo _r) {
            args = _a;
            res = _r;
        }
    }

    final SparseArray<PostInstallData> mRunningInstalls = new SparseArray<PostInstallData>();
    int mNextInstallToken = 1;  // nonzero; will be wrapped back to 1 when ++ overflows

    // XML tags for backup/restore of various bits of state
    private static final String TAG_PREFERRED_BACKUP = "pa";
    private static final String TAG_DEFAULT_APPS = "da";
    private static final String TAG_INTENT_FILTER_VERIFICATION = "iv";

    private static final String TAG_PERMISSION_BACKUP = "perm-grant-backup";
    private static final String TAG_ALL_GRANTS = "rt-grants";
    private static final String TAG_GRANT = "grant";
    private static final String ATTR_PACKAGE_NAME = "pkg";

    private static final String TAG_PERMISSION = "perm";
    private static final String ATTR_PERMISSION_NAME = "name";
    private static final String ATTR_IS_GRANTED = "g";
    private static final String ATTR_USER_SET = "set";
    private static final String ATTR_USER_FIXED = "fixed";
    private static final String ATTR_REVOKE_ON_UPGRADE = "rou";

    // System/policy permission grants are not backed up
    private static final int SYSTEM_RUNTIME_GRANT_MASK =
            FLAG_PERMISSION_POLICY_FIXED
            | FLAG_PERMISSION_SYSTEM_FIXED
            | FLAG_PERMISSION_GRANTED_BY_DEFAULT;

    // And we back up these user-adjusted states
    private static final int USER_RUNTIME_GRANT_MASK =
            FLAG_PERMISSION_USER_SET
            | FLAG_PERMISSION_USER_FIXED
            | FLAG_PERMISSION_REVOKE_ON_UPGRADE;

    final @Nullable String mRequiredVerifierPackage;
    final @NonNull String mRequiredInstallerPackage;
    final @Nullable String mSetupWizardPackage;
    final @NonNull String mServicesSystemSharedLibraryPackageName;
    final @NonNull String mSharedSystemSharedLibraryPackageName;

    private final PackageUsage mPackageUsage = new PackageUsage();
    private final CompilerStats mCompilerStats = new CompilerStats();

    class PackageHandler extends Handler {
        private boolean mBound = false;
        final ArrayList<HandlerParams> mPendingInstalls =
            new ArrayList<HandlerParams>();

        private boolean connectToService() {
            if (DEBUG_SD_INSTALL) Log.i(TAG, "Trying to bind to" +
                    " DefaultContainerService");
            Intent service = new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT);
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
            if (mContext.bindServiceAsUser(service, mDefContainerConn,
                    Context.BIND_AUTO_CREATE, UserHandle.SYSTEM)) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                mBound = true;
                return true;
            }
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            return false;
        }

        private void disconnectService() {
            mContainerService = null;
            mBound = false;
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
            mContext.unbindService(mDefContainerConn);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }

        PackageHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            try {
                doHandleMessage(msg);
            } finally {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            }
        }

        void doHandleMessage(Message msg) {
            switch (msg.what) {
                case INIT_COPY: {
                    HandlerParams params = (HandlerParams) msg.obj;
                    int idx = mPendingInstalls.size();
                    if (DEBUG_INSTALL) Slog.i(TAG, "init_copy idx=" + idx + ": " + params);
                    // If a bind was already initiated we dont really
                    // need to do anything. The pending install
                    // will be processed later on.
                    if (!mBound) {
                        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "bindingMCS",
                                System.identityHashCode(mHandler));
                        // If this is the only one pending we might
                        // have to bind to the service again.
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            params.serviceError();
                            Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "bindingMCS",
                                    System.identityHashCode(mHandler));
                            if (params.traceMethod != null) {
                                Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, params.traceMethod,
                                        params.traceCookie);
                            }
                            return;
                        } else {
                            // Once we bind to the service, the first
                            // pending request will be processed.
                            mPendingInstalls.add(idx, params);
                        }
                    } else {
                        mPendingInstalls.add(idx, params);
                        // Already bound to the service. Just make
                        // sure we trigger off processing the first request.
                        if (idx == 0) {
                            mHandler.sendEmptyMessage(MCS_BOUND);
                        }
                    }
                    break;
                }
                case MCS_BOUND: {
                    if (DEBUG_INSTALL) Slog.i(TAG, "mcs_bound");
                    if (msg.obj != null) {
                        mContainerService = (IMediaContainerService) msg.obj;
                        Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "bindingMCS",
                                System.identityHashCode(mHandler));
                    }
                    if (mContainerService == null) {
                        if (!mBound) {
                            // Something seriously wrong since we are not bound and we are not
                            // waiting for connection. Bail out.
                            Slog.e(TAG, "Cannot bind to media container service");
                            for (HandlerParams params : mPendingInstalls) {
                                // Indicate service bind error
                                params.serviceError();
                                Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                                        System.identityHashCode(params));
                                if (params.traceMethod != null) {
                                    Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER,
                                            params.traceMethod, params.traceCookie);
                                }
                                return;
                            }
                            mPendingInstalls.clear();
                        } else {
                            Slog.w(TAG, "Waiting to connect to media container service");
                        }
                    } else if (mPendingInstalls.size() > 0) {
                        HandlerParams params = mPendingInstalls.get(0);
                        if (params != null) {
                            Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                                    System.identityHashCode(params));
                            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "startCopy");
                            if (params.startCopy()) {
                                // We are done...  look for more work or to
                                // go idle.
                                if (DEBUG_SD_INSTALL) Log.i(TAG,
                                        "Checking for more work or unbind...");
                                // Delete pending install
                                if (mPendingInstalls.size() > 0) {
                                    mPendingInstalls.remove(0);
                                }
                                if (mPendingInstalls.size() == 0) {
                                    if (mBound) {
                                        if (DEBUG_SD_INSTALL) Log.i(TAG,
                                                "Posting delayed MCS_UNBIND");
                                        removeMessages(MCS_UNBIND);
                                        Message ubmsg = obtainMessage(MCS_UNBIND);
                                        // Unbind after a little delay, to avoid
                                        // continual thrashing.
                                        sendMessageDelayed(ubmsg, 10000);
                                    }
                                } else {
                                    // There are more pending requests in queue.
                                    // Just post MCS_BOUND message to trigger processing
                                    // of next pending install.
                                    if (DEBUG_SD_INSTALL) Log.i(TAG,
                                            "Posting MCS_BOUND for next work");
                                    mHandler.sendEmptyMessage(MCS_BOUND);
                                }
                            }
                            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
                        }
                    } else {
                        // Should never happen ideally.
                        Slog.w(TAG, "Empty queue");
                    }
                    break;
                }
                case MCS_RECONNECT: {
                    if (DEBUG_INSTALL) Slog.i(TAG, "mcs_reconnect");
                    if (mPendingInstalls.size() > 0) {
                        if (mBound) {
                            disconnectService();
                        }
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            for (HandlerParams params : mPendingInstalls) {
                                // Indicate service bind error
                                params.serviceError();
                                Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                                        System.identityHashCode(params));
                            }
                            mPendingInstalls.clear();
                        }
                    }
                    break;
                }
                case MCS_UNBIND: {
                    // If there is no actual work left, then time to unbind.
                    if (DEBUG_INSTALL) Slog.i(TAG, "mcs_unbind");

                    if (mPendingInstalls.size() == 0 && mPendingVerification.size() == 0) {
                        if (mBound) {
                            if (DEBUG_INSTALL) Slog.i(TAG, "calling disconnectService()");

                            disconnectService();
                        }
                    } else if (mPendingInstalls.size() > 0) {
                        // There are more pending requests in queue.
                        // Just post MCS_BOUND message to trigger processing
                        // of next pending install.
                        mHandler.sendEmptyMessage(MCS_BOUND);
                    }

                    break;
                }
                case MCS_GIVE_UP: {
                    if (DEBUG_INSTALL) Slog.i(TAG, "mcs_giveup too many retries");
                    HandlerParams params = mPendingInstalls.remove(0);
                    Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                            System.identityHashCode(params));
                    break;
                }
                case SEND_PENDING_BROADCAST: {
                    String packages[];
                    ArrayList<String> components[];
                    int size = 0;
                    int uids[];
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    synchronized (mPackages) {
                        if (mPendingBroadcasts == null) {
                            return;
                        }
                        size = mPendingBroadcasts.size();
                        if (size <= 0) {
                            // Nothing to be done. Just return
                            return;
                        }
                        packages = new String[size];
                        components = new ArrayList[size];
                        uids = new int[size];
                        int i = 0;  // filling out the above arrays

                        for (int n = 0; n < mPendingBroadcasts.userIdCount(); n++) {
                            int packageUserId = mPendingBroadcasts.userIdAt(n);
                            Iterator<Map.Entry<String, ArrayList<String>>> it
                                    = mPendingBroadcasts.packagesForUserId(packageUserId)
                                            .entrySet().iterator();
                            while (it.hasNext() && i < size) {
                                Map.Entry<String, ArrayList<String>> ent = it.next();
                                packages[i] = ent.getKey();
                                components[i] = ent.getValue();
                                PackageSetting ps = mSettings.mPackages.get(ent.getKey());
                                uids[i] = (ps != null)
                                        ? UserHandle.getUid(packageUserId, ps.appId)
                                        : -1;
                                i++;
                            }
                        }
                        size = i;
                        mPendingBroadcasts.clear();
                    }
                    // Send broadcasts
                    for (int i = 0; i < size; i++) {
                        sendPackageChangedBroadcast(packages[i], true, components[i], uids[i]);
                    }
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    break;
                }
                case START_CLEANING_PACKAGE: {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    final String packageName = (String)msg.obj;
                    final int userId = msg.arg1;
                    final boolean andCode = msg.arg2 != 0;
                    synchronized (mPackages) {
                        if (userId == UserHandle.USER_ALL) {
                            int[] users = sUserManager.getUserIds();
                            for (int user : users) {
                                mSettings.addPackageToCleanLPw(
                                        new PackageCleanItem(user, packageName, andCode));
                            }
                        } else {
                            mSettings.addPackageToCleanLPw(
                                    new PackageCleanItem(userId, packageName, andCode));
                        }
                    }
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    startCleaningPackages();
                } break;
                case POST_INSTALL: {
                    if (DEBUG_INSTALL) Log.v(TAG, "Handling post-install for " + msg.arg1);

                    PostInstallData data = mRunningInstalls.get(msg.arg1);
                    final boolean didRestore = (msg.arg2 != 0);
                    mRunningInstalls.delete(msg.arg1);

                    if (data != null) {
                        InstallArgs args = data.args;
                        PackageInstalledInfo parentRes = data.res;

                        final boolean grantPermissions = (args.installFlags
                                & PackageManager.INSTALL_GRANT_RUNTIME_PERMISSIONS) != 0;
                        final boolean killApp = (args.installFlags
                                & PackageManager.INSTALL_DONT_KILL_APP) == 0;
                        final String[] grantedPermissions = args.installGrantPermissions;

                        // Handle the parent package
                        handlePackagePostInstall(parentRes, grantPermissions, killApp,
                                grantedPermissions, didRestore, args.installerPackageName,
                                args.observer);

                        // Handle the child packages
                        final int childCount = (parentRes.addedChildPackages != null)
                                ? parentRes.addedChildPackages.size() : 0;
                        for (int i = 0; i < childCount; i++) {
                            PackageInstalledInfo childRes = parentRes.addedChildPackages.valueAt(i);
                            handlePackagePostInstall(childRes, grantPermissions, killApp,
                                    grantedPermissions, false, args.installerPackageName,
                                    args.observer);
                        }

                        // Log tracing if needed
                        if (args.traceMethod != null) {
                            Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, args.traceMethod,
                                    args.traceCookie);
                        }
                    } else {
                        Slog.e(TAG, "Bogus post-install token " + msg.arg1);
                    }

                    Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "postInstall", msg.arg1);
                } break;
                case UPDATED_MEDIA_STATUS: {
                    if (DEBUG_SD_INSTALL) Log.i(TAG, "Got message UPDATED_MEDIA_STATUS");
                    boolean reportStatus = msg.arg1 == 1;
                    boolean doGc = msg.arg2 == 1;
                    if (DEBUG_SD_INSTALL) Log.i(TAG, "reportStatus=" + reportStatus + ", doGc = " + doGc);
                    if (doGc) {
                        // Force a gc to clear up stale containers.
                        Runtime.getRuntime().gc();
                    }
                    if (msg.obj != null) {
                        @SuppressWarnings("unchecked")
                        Set<AsecInstallArgs> args = (Set<AsecInstallArgs>) msg.obj;
                        if (DEBUG_SD_INSTALL) Log.i(TAG, "Unloading all containers");
                        // Unload containers
                        unloadAllContainers(args);
                    }
                    if (reportStatus) {
                        try {
                            if (DEBUG_SD_INSTALL) Log.i(TAG, "Invoking MountService call back");
                            PackageHelper.getMountService().finishMediaUpdate();
                        } catch (RemoteException e) {
                            Log.e(TAG, "MountService not running?");
                        }
                    }
                } break;
                case WRITE_SETTINGS: {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    synchronized (mPackages) {
                        removeMessages(WRITE_SETTINGS);
                        removeMessages(WRITE_PACKAGE_RESTRICTIONS);
                        mSettings.writeLPr();
                        mDirtyUsers.clear();
                    }
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } break;
                case WRITE_PACKAGE_RESTRICTIONS: {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    synchronized (mPackages) {
                        removeMessages(WRITE_PACKAGE_RESTRICTIONS);
                        for (int userId : mDirtyUsers) {
                            mSettings.writePackageRestrictionsLPr(userId);
                        }
                        mDirtyUsers.clear();
                    }
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } break;
                case WRITE_PACKAGE_LIST: {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    synchronized (mPackages) {
                        removeMessages(WRITE_PACKAGE_LIST);
                        mSettings.writePackageListLPr(msg.arg1);
                    }
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } break;
                case CHECK_PENDING_VERIFICATION: {
                    final int verificationId = msg.arg1;
                    final PackageVerificationState state = mPendingVerification.get(verificationId);

                    if ((state != null) && !state.timeoutExtended()) {
                        final InstallArgs args = state.getInstallArgs();
                        final Uri originUri = Uri.fromFile(args.origin.resolvedFile);

                        Slog.i(TAG, "Verification timed out for " + originUri);
                        mPendingVerification.remove(verificationId);

                        int ret = PackageManager.INSTALL_FAILED_VERIFICATION_FAILURE;

                        if (getDefaultVerificationResponse() == PackageManager.VERIFICATION_ALLOW) {
                            Slog.i(TAG, "Continuing with installation of " + originUri);
                            state.setVerifierResponse(Binder.getCallingUid(),
                                    PackageManager.VERIFICATION_ALLOW_WITHOUT_SUFFICIENT);
                            broadcastPackageVerified(verificationId, originUri,
                                    PackageManager.VERIFICATION_ALLOW,
                                    state.getInstallArgs().getUser());
                            try {
                                ret = args.copyApk(mContainerService, true);
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Could not contact the ContainerService");
                            }
                        } else {
                            broadcastPackageVerified(verificationId, originUri,
                                    PackageManager.VERIFICATION_REJECT,
                                    state.getInstallArgs().getUser());
                        }

                        Trace.asyncTraceEnd(
                                TRACE_TAG_PACKAGE_MANAGER, "verification", verificationId);

                        processPendingInstall(args, ret);
                        mHandler.sendEmptyMessage(MCS_UNBIND);
                    }
                    break;
                }
                case PACKAGE_VERIFIED: {
                    final int verificationId = msg.arg1;

                    final PackageVerificationState state = mPendingVerification.get(verificationId);
                    if (state == null) {
                        Slog.w(TAG, "Invalid verification token " + verificationId + " received");
                        break;
                    }

                    final PackageVerificationResponse response = (PackageVerificationResponse) msg.obj;

                    state.setVerifierResponse(response.callerUid, response.code);

                    if (state.isVerificationComplete()) {
                        mPendingVerification.remove(verificationId);

                        final InstallArgs args = state.getInstallArgs();
                        final Uri originUri = Uri.fromFile(args.origin.resolvedFile);

                        int ret;
                        if (state.isInstallAllowed()) {
                            ret = PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
                            broadcastPackageVerified(verificationId, originUri,
                                    response.code, state.getInstallArgs().getUser());
                            try {
                                ret = args.copyApk(mContainerService, true);
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Could not contact the ContainerService");
                            }
                        } else {
                            ret = PackageManager.INSTALL_FAILED_VERIFICATION_FAILURE;
                        }

                        Trace.asyncTraceEnd(
                                TRACE_TAG_PACKAGE_MANAGER, "verification", verificationId);

                        processPendingInstall(args, ret);
                        mHandler.sendEmptyMessage(MCS_UNBIND);
                    }

                    break;
                }
                case START_INTENT_FILTER_VERIFICATIONS: {
                    IFVerificationParams params = (IFVerificationParams) msg.obj;
                    verifyIntentFiltersIfNeeded(params.userId, params.verifierUid,
                            params.replacing, params.pkg);
                    break;
                }
                case INTENT_FILTER_VERIFIED: {
                    final int verificationId = msg.arg1;

                    final IntentFilterVerificationState state = mIntentFilterVerificationStates.get(
                            verificationId);
                    if (state == null) {
                        Slog.w(TAG, "Invalid IntentFilter verification token "
                                + verificationId + " received");
                        break;
                    }

                    final int userId = state.getUserId();

                    if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                            "Processing IntentFilter verification with token:"
                            + verificationId + " and userId:" + userId);

                    final IntentFilterVerificationResponse response =
                            (IntentFilterVerificationResponse) msg.obj;

                    state.setVerifierResponse(response.callerUid, response.code);

                    if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                            "IntentFilter verification with token:" + verificationId
                            + " and userId:" + userId
                            + " is settings verifier response with response code:"
                            + response.code);

                    if (response.code == PackageManager.INTENT_FILTER_VERIFICATION_FAILURE) {
                        if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG, "Domains failing verification: "
                                + response.getFailedDomainsString());
                    }

                    if (state.isVerificationComplete()) {
                        mIntentFilterVerifier.receiveVerificationResponse(verificationId);
                    } else {
                        if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                                "IntentFilter verification with token:" + verificationId
                                + " was not said to be complete");
                    }

                    break;
                }
            }
        }
    }

    private void handlePackagePostInstall(PackageInstalledInfo res, boolean grantPermissions,
            boolean killApp, String[] grantedPermissions,
            boolean launchedForRestore, String installerPackage,
            IPackageInstallObserver2 installObserver) {
        if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
            // Send the removed broadcasts
            if (res.removedInfo != null) {
                res.removedInfo.sendPackageRemovedBroadcasts(killApp);
            }

            // Now that we successfully installed the package, grant runtime
            // permissions if requested before broadcasting the install.
            if (grantPermissions && res.pkg.applicationInfo.targetSdkVersion
                    >= Build.VERSION_CODES.M) {
                grantRequestedRuntimePermissions(res.pkg, res.newUsers, grantedPermissions);
            }

            final boolean update = res.removedInfo != null
                    && res.removedInfo.removedPackage != null;

            // If this is the first time we have child packages for a disabled privileged
            // app that had no children, we grant requested runtime permissions to the new
            // children if the parent on the system image had them already granted.
            if (res.pkg.parentPackage != null) {
                synchronized (mPackages) {
                    grantRuntimePermissionsGrantedToDisabledPrivSysPackageParentLPw(res.pkg);
                }
            }

            synchronized (mPackages) {
                mEphemeralApplicationRegistry.onPackageInstalledLPw(res.pkg);
            }

            final String packageName = res.pkg.applicationInfo.packageName;
            Bundle extras = new Bundle(1);
            extras.putInt(Intent.EXTRA_UID, res.uid);

            // Determine the set of users who are adding this package for
            // the first time vs. those who are seeing an update.
            int[] firstUsers = EMPTY_INT_ARRAY;
            int[] updateUsers = EMPTY_INT_ARRAY;
            if (res.origUsers == null || res.origUsers.length == 0) {
                firstUsers = res.newUsers;
            } else {
                for (int newUser : res.newUsers) {
                    boolean isNew = true;
                    for (int origUser : res.origUsers) {
                        if (origUser == newUser) {
                            isNew = false;
                            break;
                        }
                    }
                    if (isNew) {
                        firstUsers = ArrayUtils.appendInt(firstUsers, newUser);
                    } else {
                        updateUsers = ArrayUtils.appendInt(updateUsers, newUser);
                    }
                }
            }

            // Send installed broadcasts if the install/update is not ephemeral
            if (!isEphemeral(res.pkg)) {
                mProcessLoggingHandler.invalidateProcessLoggingBaseApkHash(res.pkg.baseCodePath);

                // Send added for users that see the package for the first time
                sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, packageName,
                        extras, 0 /*flags*/, null /*targetPackage*/,
                        null /*finishedReceiver*/, firstUsers);

                // Send added for users that don't see the package for the first time
                if (update) {
                    extras.putBoolean(Intent.EXTRA_REPLACING, true);
                }
                sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, packageName,
                        extras, 0 /*flags*/, null /*targetPackage*/,
                        null /*finishedReceiver*/, updateUsers);

                // Send replaced for users that don't see the package for the first time
                if (update) {
                    sendPackageBroadcast(Intent.ACTION_PACKAGE_REPLACED,
                            packageName, extras, 0 /*flags*/,
                            null /*targetPackage*/, null /*finishedReceiver*/,
                            updateUsers);
                    sendPackageBroadcast(Intent.ACTION_MY_PACKAGE_REPLACED,
                            null /*package*/, null /*extras*/, 0 /*flags*/,
                            packageName /*targetPackage*/,
                            null /*finishedReceiver*/, updateUsers);
                } else if (launchedForRestore && !isSystemApp(res.pkg)) {
                    // First-install and we did a restore, so we're responsible for the
                    // first-launch broadcast.
                    if (DEBUG_BACKUP) {
                        Slog.i(TAG, "Post-restore of " + packageName
                                + " sending FIRST_LAUNCH in " + Arrays.toString(firstUsers));
                    }
                    sendFirstLaunchBroadcast(packageName, installerPackage, firstUsers);
                }

                // Send broadcast package appeared if forward locked/external for all users
                // treat asec-hosted packages like removable media on upgrade
                if (res.pkg.isForwardLocked() || isExternal(res.pkg)) {
                    if (DEBUG_INSTALL) {
                        Slog.i(TAG, "upgrading pkg " + res.pkg
                                + " is ASEC-hosted -> AVAILABLE");
                    }
                    final int[] uidArray = new int[]{res.pkg.applicationInfo.uid};
                    ArrayList<String> pkgList = new ArrayList<>(1);
                    pkgList.add(packageName);
                    sendResourcesChangedBroadcast(true, true, pkgList, uidArray, null);
                }
            }

            // Work that needs to happen on first install within each user
            if (firstUsers != null && firstUsers.length > 0) {
                synchronized (mPackages) {
                    for (int userId : firstUsers) {
                        // If this app is a browser and it's newly-installed for some
                        // users, clear any default-browser state in those users. The
                        // app's nature doesn't depend on the user, so we can just check
                        // its browser nature in any user and generalize.
                        if (packageIsBrowser(packageName, userId)) {
                            mSettings.setDefaultBrowserPackageNameLPw(null, userId);
                        }

                        // We may also need to apply pending (restored) runtime
                        // permission grants within these users.
                        mSettings.applyPendingPermissionGrantsLPw(packageName, userId);
                    }
                }
            }

			if (!update && !isSystemApp(res.pkg)) {
			    boolean privacyGuard = CMSettings.Secure.getIntForUser(
						mContext.getContentResolver(),
						CMSettings.Secure.PRIVACY_GUARD_DEFAULT,
						0, UserHandle.USER_CURRENT) == 1;
				if (privacyGuard) {
					mAppOps.setPrivacyGuardSettingForPackage(
							res.pkg.applicationInfo.uid,
							res.pkg.applicationInfo.packageName, true);
				}
			}

            // Log current value of "unknown sources" setting
            EventLog.writeEvent(EventLogTags.UNKNOWN_SOURCES_ENABLED,
                    getUnknownSourcesSettings());

            // Force a gc to clear up things
            Runtime.getRuntime().gc();

            // Remove the replaced package's older resources safely now
            // We delete after a gc for applications  on sdcard.
            if (res.removedInfo != null && res.removedInfo.args != null) {
                synchronized (mInstallLock) {
                    res.removedInfo.args.doPostDeleteLI(true);
                }
            }
        }

        // If someone is watching installs - notify them
        if (installObserver != null) {
            try {
                Bundle extras = extrasForInstallResult(res);
                installObserver.onPackageInstalled(res.name, res.returnCode,
                        res.returnMsg, extras);
            } catch (RemoteException e) {
                Slog.i(TAG, "Observer no longer exists.");
            }
        }
    }

    private void grantRuntimePermissionsGrantedToDisabledPrivSysPackageParentLPw(
            PackageParser.Package pkg) {
        if (pkg.parentPackage == null) {
            return;
        }
        if (pkg.requestedPermissions == null) {
            return;
        }
        final PackageSetting disabledSysParentPs = mSettings
                .getDisabledSystemPkgLPr(pkg.parentPackage.packageName);
        if (disabledSysParentPs == null || disabledSysParentPs.pkg == null
                || !disabledSysParentPs.isPrivileged()
                || (disabledSysParentPs.childPackageNames != null
                        && !disabledSysParentPs.childPackageNames.isEmpty())) {
            return;
        }
        final int[] allUserIds = sUserManager.getUserIds();
        final int permCount = pkg.requestedPermissions.size();
        for (int i = 0; i < permCount; i++) {
            String permission = pkg.requestedPermissions.get(i);
            BasePermission bp = mSettings.mPermissions.get(permission);
            if (bp == null || !(bp.isRuntime() || bp.isDevelopment())) {
                continue;
            }
            for (int userId : allUserIds) {
                if (disabledSysParentPs.getPermissionsState().hasRuntimePermission(
                        permission, userId)) {
                    grantRuntimePermission(pkg.packageName, permission, userId);
                }
            }
        }
    }

    private StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (vol.type == VolumeInfo.TYPE_PRIVATE) {
                if (vol.state == VolumeInfo.STATE_MOUNTED) {
                    final String volumeUuid = vol.getFsUuid();

                    // Clean up any users or apps that were removed or recreated
                    // while this volume was missing
                    reconcileUsers(volumeUuid);
                    reconcileApps(volumeUuid);

                    // Clean up any install sessions that expired or were
                    // cancelled while this volume was missing
                    mInstallerService.onPrivateVolumeMounted(volumeUuid);

                    loadPrivatePackages(vol);

                } else if (vol.state == VolumeInfo.STATE_EJECTING) {
                    unloadPrivatePackages(vol);
                }
            }

            if (vol.type == VolumeInfo.TYPE_PUBLIC && vol.isPrimary()) {
                if (vol.state == VolumeInfo.STATE_MOUNTED) {
                    updateExternalMediaStatus(true, false);
                } else if (vol.state == VolumeInfo.STATE_EJECTING) {
                    updateExternalMediaStatus(false, false);
                }
            }
        }

        @Override
        public void onVolumeForgotten(String fsUuid) {
            if (TextUtils.isEmpty(fsUuid)) {
                Slog.e(TAG, "Forgetting internal storage is probably a mistake; ignoring");
                return;
            }

            // Remove any apps installed on the forgotten volume
            synchronized (mPackages) {
                final List<PackageSetting> packages = mSettings.getVolumePackagesLPr(fsUuid);
                for (PackageSetting ps : packages) {
                    Slog.d(TAG, "Destroying " + ps.name + " because volume was forgotten");
                    deletePackage(ps.name, new LegacyPackageDeleteObserver(null).getBinder(),
                            UserHandle.USER_SYSTEM, PackageManager.DELETE_ALL_USERS);
                }

                mSettings.onVolumeForgotten(fsUuid);
                mSettings.writeLPr();
            }
        }
    };

    private void grantRequestedRuntimePermissions(PackageParser.Package pkg, int[] userIds,
            String[] grantedPermissions) {
        for (int userId : userIds) {
            grantRequestedRuntimePermissionsForUser(pkg, userId, grantedPermissions);
        }

        // We could have touched GID membership, so flush out packages.list
        synchronized (mPackages) {
            mSettings.writePackageListLPr();
        }
    }

    private void grantRequestedRuntimePermissionsForUser(PackageParser.Package pkg, int userId,
            String[] grantedPermissions) {
        SettingBase sb = (SettingBase) pkg.mExtras;
        if (sb == null) {
            return;
        }

        PermissionsState permissionsState = sb.getPermissionsState();

        final int immutableFlags = PackageManager.FLAG_PERMISSION_SYSTEM_FIXED
                | PackageManager.FLAG_PERMISSION_POLICY_FIXED;

        for (String permission : pkg.requestedPermissions) {
            final BasePermission bp;
            synchronized (mPackages) {
                bp = mSettings.mPermissions.get(permission);
            }
            if (bp != null && (bp.isRuntime() || bp.isDevelopment())
                    && (grantedPermissions == null
                           || ArrayUtils.contains(grantedPermissions, permission))) {
                final int flags = permissionsState.getPermissionFlags(permission, userId);
                // Installer cannot change immutable permissions.
                if ((flags & immutableFlags) == 0) {
                    grantRuntimePermission(pkg.packageName, permission, userId);
                }
            }
        }
    }

    Bundle extrasForInstallResult(PackageInstalledInfo res) {
        Bundle extras = null;
        switch (res.returnCode) {
            case PackageManager.INSTALL_FAILED_DUPLICATE_PERMISSION: {
                extras = new Bundle();
                extras.putString(PackageManager.EXTRA_FAILURE_EXISTING_PERMISSION,
                        res.origPermission);
                extras.putString(PackageManager.EXTRA_FAILURE_EXISTING_PACKAGE,
                        res.origPackage);
                break;
            }
            case PackageManager.INSTALL_SUCCEEDED: {
                extras = new Bundle();
                extras.putBoolean(Intent.EXTRA_REPLACING,
                        res.removedInfo != null && res.removedInfo.removedPackage != null);
                break;
            }
        }
        return extras;
    }

    void scheduleWriteSettingsLocked() {
        if (!mHandler.hasMessages(WRITE_SETTINGS)) {
            mHandler.sendEmptyMessageDelayed(WRITE_SETTINGS, WRITE_SETTINGS_DELAY);
        }
    }

    void scheduleWritePackageListLocked(int userId) {
        if (!mHandler.hasMessages(WRITE_PACKAGE_LIST)) {
            Message msg = mHandler.obtainMessage(WRITE_PACKAGE_LIST);
            msg.arg1 = userId;
            mHandler.sendMessageDelayed(msg, WRITE_SETTINGS_DELAY);
        }
    }

    void scheduleWritePackageRestrictionsLocked(UserHandle user) {
        final int userId = user == null ? UserHandle.USER_ALL : user.getIdentifier();
        scheduleWritePackageRestrictionsLocked(userId);
    }

    void scheduleWritePackageRestrictionsLocked(int userId) {
        final int[] userIds = (userId == UserHandle.USER_ALL)
                ? sUserManager.getUserIds() : new int[]{userId};
        for (int nextUserId : userIds) {
            if (!sUserManager.exists(nextUserId)) return;
            mDirtyUsers.add(nextUserId);
            if (!mHandler.hasMessages(WRITE_PACKAGE_RESTRICTIONS)) {
                mHandler.sendEmptyMessageDelayed(WRITE_PACKAGE_RESTRICTIONS, WRITE_SETTINGS_DELAY);
            }
        }
    }

    public static PackageManagerService main(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
        // Self-check for initial settings.
        PackageManagerServiceCompilerMapping.checkProperties();

        PackageManagerService m = new PackageManagerService(context, installer,
                factoryTest, onlyCore);
        m.enableSystemUserPackages();
        ServiceManager.addService("package", m);
        return m;
    }

    private void enableSystemUserPackages() {
        if (!UserManager.isSplitSystemUser()) {
            return;
        }
        // For system user, enable apps based on the following conditions:
        // - app is whitelisted or belong to one of these groups:
        //   -- system app which has no launcher icons
        //   -- system app which has INTERACT_ACROSS_USERS permission
        //   -- system IME app
        // - app is not in the blacklist
        AppsQueryHelper queryHelper = new AppsQueryHelper(this);
        Set<String> enableApps = new ArraySet<>();
        enableApps.addAll(queryHelper.queryApps(AppsQueryHelper.GET_NON_LAUNCHABLE_APPS
                | AppsQueryHelper.GET_APPS_WITH_INTERACT_ACROSS_USERS_PERM
                | AppsQueryHelper.GET_IMES, /* systemAppsOnly */ true, UserHandle.SYSTEM));
        ArraySet<String> wlApps = SystemConfig.getInstance().getSystemUserWhitelistedApps();
        enableApps.addAll(wlApps);
        enableApps.addAll(queryHelper.queryApps(AppsQueryHelper.GET_REQUIRED_FOR_SYSTEM_USER,
                /* systemAppsOnly */ false, UserHandle.SYSTEM));
        ArraySet<String> blApps = SystemConfig.getInstance().getSystemUserBlacklistedApps();
        enableApps.removeAll(blApps);
        Log.i(TAG, "Applications installed for system user: " + enableApps);
        List<String> allAps = queryHelper.queryApps(0, /* systemAppsOnly */ false,
                UserHandle.SYSTEM);
        final int allAppsSize = allAps.size();
        synchronized (mPackages) {
            for (int i = 0; i < allAppsSize; i++) {
                String pName = allAps.get(i);
                PackageSetting pkgSetting = mSettings.mPackages.get(pName);
                // Should not happen, but we shouldn't be failing if it does
                if (pkgSetting == null) {
                    continue;
                }
                boolean install = enableApps.contains(pName);
                if (pkgSetting.getInstalled(UserHandle.USER_SYSTEM) != install) {
                    Log.i(TAG, (install ? "Installing " : "Uninstalling ") + pName
                            + " for system user");
                    pkgSetting.setInstalled(install, UserHandle.USER_SYSTEM);
                }
            }
        }
    }

    private static void getDefaultDisplayMetrics(Context context, DisplayMetrics metrics) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(
                Context.DISPLAY_SERVICE);
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getMetrics(metrics);
    }

    /**
     * Requests that files preopted on a secondary system partition be copied to the data partition
     * if possible.  Note that the actual copying of the files is accomplished by init for security
     * reasons. This simply requests that the copy takes place and awaits confirmation of its
     * completion. See platform/system/extras/cppreopt/ for the implementation of the actual copy.
     */
    private static void requestCopyPreoptedFiles() {
        final int WAIT_TIME_MS = 100;
        final String CP_PREOPT_PROPERTY = "sys.cppreopt";
        if (SystemProperties.getInt("ro.cp_system_other_odex", 0) == 1) {
            SystemProperties.set(CP_PREOPT_PROPERTY, "requested");
            // We will wait for up to 100 seconds.
            final long timeEnd = SystemClock.uptimeMillis() + 100 * 1000;
            while (!SystemProperties.get(CP_PREOPT_PROPERTY).equals("finished")) {
                try {
                    Thread.sleep(WAIT_TIME_MS);
                } catch (InterruptedException e) {
                    // Do nothing
                }
                if (SystemClock.uptimeMillis() > timeEnd) {
                    SystemProperties.set(CP_PREOPT_PROPERTY, "timed-out");
                    Slog.wtf(TAG, "cppreopt did not finish!");
                    break;
                }
            }
        }
    }

    public PackageManagerService(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_START,
                SystemClock.uptimeMillis());

        if (mSdkVersion <= 0) {
            Slog.w(TAG, "**** ro.build.version.sdk not set!");
        }

        mContext = context;
        mFactoryTest = factoryTest;
        mOnlyCore = onlyCore;
        mMetrics = new DisplayMetrics();
        mSettings = new Settings(mPackages);
        mSettings.addSharedUserLPw("android.uid.system", Process.SYSTEM_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        mSettings.addSharedUserLPw("android.uid.phone", RADIO_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        mSettings.addSharedUserLPw("android.uid.log", LOG_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        mSettings.addSharedUserLPw("android.uid.nfc", NFC_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        mSettings.addSharedUserLPw("android.uid.bluetooth", BLUETOOTH_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        mSettings.addSharedUserLPw("android.uid.shell", SHELL_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);

        File setFile = new File(AlarmManager.POWER_OFF_ALARM_SET_FILE);
        File handleFile = new File(AlarmManager.POWER_OFF_ALARM_HANDLE_FILE);
        mIsAlarmBoot = SystemProperties.getBoolean("ro.alarm_boot", false);
        if (mIsAlarmBoot) {
            mOnlyPowerOffAlarm = true;
        } else if (setFile.exists() && handleFile.exists()) {
            // if it is normal boot, check if power off alarm is handled. And set
            // alarm properties for others to check.
            if (!mOnlyCore && AlarmManager
                    .readPowerOffAlarmFile(AlarmManager.POWER_OFF_ALARM_HANDLE_FILE)
                    .equals(AlarmManager.POWER_OFF_ALARM_HANDLED)) {
                SystemProperties.set("ro.alarm_handled", "true");
                File instanceFile = new File(AlarmManager.POWER_OFF_ALARM_INSTANCE_FILE);
                String instanceValue = AlarmManager
                        .readPowerOffAlarmFile(AlarmManager.POWER_OFF_ALARM_INSTANCE_FILE);
                SystemProperties.set("ro.alarm_instance", instanceValue);

                AlarmManager.writePowerOffAlarmFile(AlarmManager.POWER_OFF_ALARM_HANDLE_FILE,
                        AlarmManager.POWER_OFF_ALARM_NOT_HANDLED);
            }
        }

        mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        String separateProcesses = SystemProperties.get("debug.separate_processes");
        if (separateProcesses != null && separateProcesses.length() > 0) {
            if ("*".equals(separateProcesses)) {
                mDefParseFlags = PackageParser.PARSE_IGNORE_PROCESSES;
                mSeparateProcesses = null;
                Slog.w(TAG, "Running with debug.separate_processes: * (ALL)");
            } else {
                mDefParseFlags = 0;
                mSeparateProcesses = separateProcesses.split(",");
                Slog.w(TAG, "Running with debug.separate_processes: "
                        + separateProcesses);
            }
        } else {
            mDefParseFlags = 0;
            mSeparateProcesses = null;
        }

        mInstaller = installer;
        mPackageDexOptimizer = new PackageDexOptimizer(installer, mInstallLock, context,
                "*dexopt*");
        mMoveCallbacks = new MoveCallbacks(FgThread.get().getLooper());

        mOnPermissionChangeListeners = new OnPermissionChangeListeners(
                FgThread.get().getLooper());

        getDefaultDisplayMetrics(context, mMetrics);

        SystemConfig systemConfig = SystemConfig.getInstance();
        mGlobalGids = systemConfig.getGlobalGids();
        mSystemPermissions = systemConfig.getSystemPermissions();
        mAvailableFeatures = systemConfig.getAvailableFeatures();
        mSignatureAllowances = systemConfig.getSignatureAllowances();
        mProtectedPackages = new ProtectedPackages(mContext);

//        synchronized (mInstallLock) {
        // writer
//        synchronized (mPackages) {
            mHandlerThread = new ServiceThread(TAG,
                    Process.THREAD_PRIORITY_BACKGROUND, true /*allowIo*/);
            mHandlerThread.start();
            mHandler = new PackageHandler(mHandlerThread.getLooper());
            mProcessLoggingHandler = new ProcessLoggingHandler();
            Watchdog.getInstance().addThread(mHandler, WATCHDOG_TIMEOUT);

            mDefaultPermissionPolicy = new DefaultPermissionGrantPolicy(this);

            File dataDir = Environment.getDataDirectory();
            mAppInstallDir = new File(dataDir, "app");
            mAppLib32InstallDir = new File(dataDir, "app-lib");
            mEphemeralInstallDir = new File(dataDir, "app-ephemeral");
            mAsecInternalPath = new File(dataDir, "app-asec").getPath();
            mDrmAppPrivateInstallDir = new File(dataDir, "app-private");
            mRegionalizationAppInstallDir = new File(dataDir, "app-regional");

            sUserManager = new UserManagerService(context, this, mPackages);

            // Propagate permission configuration in to package manager.
            ArrayMap<String, SystemConfig.PermissionEntry> permConfig
                    = systemConfig.getPermissions();
            for (int i=0; i<permConfig.size(); i++) {
                SystemConfig.PermissionEntry perm = permConfig.valueAt(i);
                BasePermission bp = mSettings.mPermissions.get(perm.name);
                if (bp == null) {
                    bp = new BasePermission(perm.name, "android", BasePermission.TYPE_BUILTIN);
                    mSettings.mPermissions.put(perm.name, bp);
                }
                if (perm.gids != null) {
                    bp.setGids(perm.gids, perm.perUser);
                }
            }

            ArrayMap<String, String> libConfig = systemConfig.getSharedLibraries();
            for (int i=0; i<libConfig.size(); i++) {
                mSharedLibraries.put(libConfig.keyAt(i),
                        new SharedLibraryEntry(libConfig.valueAt(i), null));
            }

            mFoundPolicyFile = SELinuxMMAC.readInstallPolicy();

            mFirstBoot = !mSettings.readLPw(sUserManager.getUsers(false));

            if (mFirstBoot) {
                requestCopyPreoptedFiles();
            }

            String customResolverActivity = Resources.getSystem().getString(
                    R.string.config_customResolverActivity);
            if (TextUtils.isEmpty(customResolverActivity)) {
                customResolverActivity = null;
            } else {
                mCustomResolverComponentName = ComponentName.unflattenFromString(
                        customResolverActivity);
            }

            long startTime = SystemClock.uptimeMillis();

            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_SYSTEM_SCAN_START,
                    startTime);

            // Set flag to monitor and not change apk file paths when
            // scanning install directories.
            final int scanFlags = SCAN_NO_PATHS | SCAN_DEFER_DEX | SCAN_BOOTING | SCAN_INITIAL;

            final String bootClassPath = System.getenv("BOOTCLASSPATH");
            final String systemServerClassPath = System.getenv("SYSTEMSERVERCLASSPATH");

            if (bootClassPath == null) {
                Slog.w(TAG, "No BOOTCLASSPATH found!");
            }

            if (systemServerClassPath == null) {
                Slog.w(TAG, "No SYSTEMSERVERCLASSPATH found!");
            }

            final List<String> allInstructionSets = InstructionSets.getAllInstructionSets();
            final String[] dexCodeInstructionSets =
                    getDexCodeInstructionSets(
                            allInstructionSets.toArray(new String[allInstructionSets.size()]));

            /**
             * Ensure all external libraries have had dexopt run on them.
             */
            if (mSharedLibraries.size() > 0) {
                // NOTE: For now, we're compiling these system "shared libraries"
                // (and framework jars) into all available architectures. It's possible
                // to compile them only when we come across an app that uses them (there's
                // already logic for that in scanPackageLI) but that adds some complexity.
                for (String dexCodeInstructionSet : dexCodeInstructionSets) {
                    for (SharedLibraryEntry libEntry : mSharedLibraries.values()) {
                        final String lib = libEntry.path;
                        if (lib == null) {
                            continue;
                        }

                        try {
                            // Shared libraries do not have profiles so we perform a full
                            // AOT compilation (if needed).
                            int dexoptNeeded = DexFile.getDexOptNeeded(
                                    lib, dexCodeInstructionSet,
                                    getCompilerFilterForReason(REASON_SHARED_APK),
                                    false /* newProfile */);
                            if (dexoptNeeded != DexFile.NO_DEXOPT_NEEDED) {
                                mInstaller.dexopt(lib, Process.SYSTEM_UID, dexCodeInstructionSet,
                                        dexoptNeeded, DEXOPT_PUBLIC /*dexFlags*/,
                                        getCompilerFilterForReason(REASON_SHARED_APK),
                                        StorageManager.UUID_PRIVATE_INTERNAL,
                                        SKIP_SHARED_LIBRARY_CHECK);
                            }
                        } catch (FileNotFoundException e) {
                            Slog.w(TAG, "Library not found: " + lib);
                        } catch (IOException | InstallerException e) {
                            Slog.w(TAG, "Cannot dexopt " + lib + "; is it an APK or JAR? "
                                    + e.getMessage());
                        }
                    }
                }
            }

            File frameworkDir = new File(Environment.getRootDirectory(), "framework");

            final VersionInfo ver = mSettings.getInternalVersion();
            mIsUpgrade = !Build.FINGERPRINT.equals(ver.fingerprint);

            // when upgrading from pre-M, promote system app permissions from install to runtime
            mPromoteSystemApps =
                    mIsUpgrade && ver.sdkVersion <= Build.VERSION_CODES.LOLLIPOP_MR1;

            // When upgrading from pre-N, we need to handle package extraction like first boot,
            // as there is no profiling data available.
            mIsPreNUpgrade = mIsUpgrade && ver.sdkVersion < Build.VERSION_CODES.N;

            // save off the names of pre-existing system packages prior to scanning; we don't
            // want to automatically grant runtime permissions for new system apps
            if (mPromoteSystemApps) {
                Iterator<PackageSetting> pkgSettingIter = mSettings.mPackages.values().iterator();
                while (pkgSettingIter.hasNext()) {
                    PackageSetting ps = pkgSettingIter.next();
                    if (isSystemApp(ps)) {
                        mExistingSystemPackages.add(ps.name);
                    }
                }
            }

            // Collect vendor overlay packages.
            // (Do this before scanning any apps.)
            // For security and version matching reason, only consider
            // overlay packages if they reside in VENDOR_OVERLAY_DIR.
            File vendorOverlayDir = new File(VENDOR_OVERLAY_DIR);
            scanDirTracedLI(vendorOverlayDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR
                    | PackageParser.PARSE_TRUSTED_OVERLAY, scanFlags | SCAN_TRUSTED_OVERLAY, 0);

            // Find base frameworks (resource packages without code).
            scanDirTracedLI(frameworkDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR
                    | PackageParser.PARSE_IS_PRIVILEGED,
                    scanFlags | SCAN_NO_DEX, 0);

            // Collected privileged system packages.
            final File privilegedAppDir = new File(Environment.getRootDirectory(), "priv-app");
            scanDirTracedLI(privilegedAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR
                    | PackageParser.PARSE_IS_PRIVILEGED, scanFlags, 0);

            // Collect ordinary system packages.
            final File systemAppDir = new File(Environment.getRootDirectory(), "app");
            scanDirTracedLI(systemAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanFlags, 0);

            // Collect all vendor packages.
            File vendorAppDir = new File("/vendor/app");
            try {
                vendorAppDir = vendorAppDir.getCanonicalFile();
            } catch (IOException e) {
                // failed to look up canonical path, continue with original one
            }
            scanDirTracedLI(vendorAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanFlags, 0);

            // Collect all OEM packages.
            final File oemAppDir = new File(Environment.getOemDirectory(), "app");
            scanDirTracedLI(oemAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanFlags, 0);

            // Collect all Regionalization packages form Carrier's res packages.
            if (RegionalizationEnvironment.isSupported()) {
                Log.d(TAG, "Load Regionalization vendor apks");
                final List<File> RegionalizationDirs =
                        RegionalizationEnvironment.getAllPackageDirectories();
                for (File f : RegionalizationDirs) {
                    File RegionalizationSystemDir = new File(f, "system");
                    // Collect packages in <Package>/system/priv-app
                    scanDirLI(new File(RegionalizationSystemDir, "priv-app"),
                            PackageParser.PARSE_IS_SYSTEM | PackageParser.PARSE_IS_SYSTEM_DIR
                            | PackageParser.PARSE_IS_PRIVILEGED, scanFlags, 0);
                    // Collect packages in <Package>/system/app
                    scanDirLI(new File(RegionalizationSystemDir, "app"),
                            PackageParser.PARSE_IS_SYSTEM | PackageParser.PARSE_IS_SYSTEM_DIR,
                            scanFlags, 0);
                    // Collect overlay in <Package>/system/vendor
                    scanDirLI(new File(RegionalizationSystemDir, "vendor/overlay"),
                            PackageParser.PARSE_IS_SYSTEM | PackageParser.PARSE_IS_SYSTEM_DIR,
                            scanFlags | SCAN_TRUSTED_OVERLAY, 0);
                }
            }

            // Prune any system packages that no longer exist.
            final List<String> possiblyDeletedUpdatedSystemApps = new ArrayList<String>();
            if (!mOnlyCore) {
                Iterator<PackageSetting> psit = mSettings.mPackages.values().iterator();
                while (psit.hasNext()) {
                    PackageSetting ps = psit.next();

                    /*
                     * If this is not a system app, it can't be a
                     * disable system app.
                     */
                    if ((ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        continue;
                    }

                    /*
                     * If the package is scanned, it's not erased.
                     */
                    final PackageParser.Package scannedPkg = mPackages.get(ps.name);
                    if (scannedPkg != null) {
                        /*
                         * If the system app is both scanned and in the
                         * disabled packages list, then it must have been
                         * added via OTA. Remove it from the currently
                         * scanned package so the previously user-installed
                         * application can be scanned.
                         */
                        if (mSettings.isDisabledSystemPackageLPr(ps.name)) {
                            logCriticalInfo(Log.WARN, "Expecting better updated system app for "
                                    + ps.name + "; removing system app.  Last known codePath="
                                    + ps.codePathString + ", installStatus=" + ps.installStatus
                                    + ", versionCode=" + ps.versionCode + "; scanned versionCode="
                                    + scannedPkg.mVersionCode);
                            removePackageLI(scannedPkg, true);
                            mExpectingBetter.put(ps.name, ps.codePath);
                        }

                        continue;
                    }

                    if (!mSettings.isDisabledSystemPackageLPr(ps.name)) {
                        psit.remove();
                        logCriticalInfo(Log.WARN, "System package " + ps.name
                                + " no longer exists; it's data will be wiped");
                        // Actual deletion of code and data will be handled by later
                        // reconciliation step
                    } else {
                        final PackageSetting disabledPs = mSettings.getDisabledSystemPkgLPr(ps.name);
                        if (disabledPs.codePath == null || !disabledPs.codePath.exists()) {
                            possiblyDeletedUpdatedSystemApps.add(ps.name);
                        }
                    }
                }
            }

            //look for any incomplete package installations
            ArrayList<PackageSetting> deletePkgsList = mSettings.getListOfIncompleteInstallPackagesLPr();
            for (int i = 0; i < deletePkgsList.size(); i++) {
                // Actual deletion of code and data will be handled by later
                // reconciliation step
                final String packageName = deletePkgsList.get(i).name;
                logCriticalInfo(Log.WARN, "Cleaning up incompletely installed app: " + packageName);
                synchronized (mPackages) {
                    mSettings.removePackageLPw(packageName);
                }
            }

            //delete tmp files
            deleteTempPackageFiles();

            // Remove any shared userIDs that have no associated packages
            mSettings.pruneSharedUsersLPw();

            if (!mOnlyCore) {
                EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_DATA_SCAN_START,
                        SystemClock.uptimeMillis());
                scanDirTracedLI(mAppInstallDir, 0, scanFlags | SCAN_REQUIRE_KNOWN, 0);

                scanDirTracedLI(mDrmAppPrivateInstallDir, mDefParseFlags
                        | PackageParser.PARSE_FORWARD_LOCK,
                        scanFlags | SCAN_REQUIRE_KNOWN, 0);

                scanDirLI(mEphemeralInstallDir, mDefParseFlags
                        | PackageParser.PARSE_IS_EPHEMERAL,
                        scanFlags | SCAN_REQUIRE_KNOWN, 0);

                // Collect all Regionalization 3rd packages.
                if (RegionalizationEnvironment.isSupported()) {
                    Log.d(TAG, "Load Regionalization 3rd apks from res packages.");
                    final List<String> packages = RegionalizationEnvironment.getAllPackageNames();
                    for (String pack : packages) {
                        File appFolder = new File(mRegionalizationAppInstallDir, pack);
                        Log.d(TAG, "Load Regionalization 3rd apks of path " + appFolder.getPath());
                        scanDirLI(appFolder, 0, scanFlags | SCAN_REQUIRE_KNOWN, 0);
                    }
                }

                /**
                 * Remove disable package settings for any updated system
                 * apps that were removed via an OTA. If they're not a
                 * previously-updated app, remove them completely.
                 * Otherwise, just revoke their system-level permissions.
                 */
                for (String deletedAppName : possiblyDeletedUpdatedSystemApps) {
                    PackageParser.Package deletedPkg = mPackages.get(deletedAppName);
                    mSettings.removeDisabledSystemPackageLPw(deletedAppName);

                    String msg;
                    if (deletedPkg == null) {
                        msg = "Updated system package " + deletedAppName
                                + " no longer exists; it's data will be wiped";
                        // Actual deletion of code and data will be handled by later
                        // reconciliation step
                    } else {
                        msg = "Updated system app + " + deletedAppName
                                + " no longer present; removing system privileges for "
                                + deletedAppName;

                        deletedPkg.applicationInfo.flags &= ~ApplicationInfo.FLAG_SYSTEM;

                        PackageSetting deletedPs = mSettings.mPackages.get(deletedAppName);
                        deletedPs.pkgFlags &= ~ApplicationInfo.FLAG_SYSTEM;
                    }
                    logCriticalInfo(Log.WARN, msg);
                }

                /**
                 * Make sure all system apps that we expected to appear on
                 * the userdata partition actually showed up. If they never
                 * appeared, crawl back and revive the system version.
                 */
                for (int i = 0; i < mExpectingBetter.size(); i++) {
                    final String packageName = mExpectingBetter.keyAt(i);
                    if (!mPackages.containsKey(packageName)) {
                        final File scanFile = mExpectingBetter.valueAt(i);

                        logCriticalInfo(Log.WARN, "Expected better " + packageName
                                + " but never showed up; reverting to system");

                        int reparseFlags = mDefParseFlags;
                        if (FileUtils.contains(privilegedAppDir, scanFile)) {
                            reparseFlags = PackageParser.PARSE_IS_SYSTEM
                                    | PackageParser.PARSE_IS_SYSTEM_DIR
                                    | PackageParser.PARSE_IS_PRIVILEGED;
                        } else if (FileUtils.contains(systemAppDir, scanFile)) {
                            reparseFlags = PackageParser.PARSE_IS_SYSTEM
                                    | PackageParser.PARSE_IS_SYSTEM_DIR;
                        } else if (FileUtils.contains(vendorAppDir, scanFile)) {
                            reparseFlags = PackageParser.PARSE_IS_SYSTEM
                                    | PackageParser.PARSE_IS_SYSTEM_DIR;
                        } else if (FileUtils.contains(oemAppDir, scanFile)) {
                            reparseFlags = PackageParser.PARSE_IS_SYSTEM
                                    | PackageParser.PARSE_IS_SYSTEM_DIR;
                        } else {
                            Slog.e(TAG, "Ignoring unexpected fallback path " + scanFile);
                            continue;
                        }

                        mSettings.enableSystemPackageLPw(packageName);

                        try {
                            scanPackageTracedLI(scanFile, reparseFlags, scanFlags, 0, null);
                        } catch (PackageManagerException e) {
                            Slog.e(TAG, "Failed to parse original system package: "
                                    + e.getMessage());
                        }
                    }
                }
            }
            mExpectingBetter.clear();

            // Resolve protected action filters. Only the setup wizard is allowed to
            // have a high priority filter for these actions.
            mSetupWizardPackage = getSetupWizardPackageName();
            if (mProtectedFilters.size() > 0) {
                if (DEBUG_FILTERS && mSetupWizardPackage == null) {
                    Slog.i(TAG, "No setup wizard;"
                        + " All protected intents capped to priority 0");
                }
                for (ActivityIntentInfo filter : mProtectedFilters) {
                    if (filter.activity.info.packageName.equals(mSetupWizardPackage)) {
                        if (DEBUG_FILTERS) {
                            Slog.i(TAG, "Found setup wizard;"
                                + " allow priority " + filter.getPriority() + ";"
                                + " package: " + filter.activity.info.packageName
                                + " activity: " + filter.activity.className
                                + " priority: " + filter.getPriority());
                        }
                        // skip setup wizard; allow it to keep the high priority filter
                        continue;
                    }
                    Slog.w(TAG, "Protected action; cap priority to 0;"
                            + " package: " + filter.activity.info.packageName
                            + " activity: " + filter.activity.className
                            + " origPrio: " + filter.getPriority());
                    filter.setPriority(0);
                }
            }
            mDeferProtectedFilters = false;
            mProtectedFilters.clear();

            // Now that we know all of the shared libraries, update all clients to have
            // the correct library paths.
            updateAllSharedLibrariesLPw();

            for (SharedUserSetting setting : mSettings.getAllSharedUsersLPw()) {
                // NOTE: We ignore potential failures here during a system scan (like
                // the rest of the commands above) because there's precious little we
                // can do about it. A settings error is reported, though.
                adjustCpuAbisForSharedUserLPw(setting.packages, null /* scanned package */,
                        false /* boot complete */);
            }

            // Now that we know all the packages we are keeping,
            // read and update their last usage times.
            mPackageUsage.read(mPackages);
            mCompilerStats.read();

            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_SCAN_END,
                    SystemClock.uptimeMillis());
            Slog.i(TAG, "Time to scan packages: "
                    + ((SystemClock.uptimeMillis()-startTime)/1000f)
                    + " seconds");

            // If the platform SDK has changed since the last time we booted,
            // we need to re-grant app permission to catch any new ones that
            // appear.  This is really a hack, and means that apps can in some
            // cases get permissions that the user didn't initially explicitly
            // allow...  it would be nice to have some better way to handle
            // this situation.
            int updateFlags = UPDATE_PERMISSIONS_ALL;
            if (ver.sdkVersion != mSdkVersion) {
                Slog.i(TAG, "Platform changed from " + ver.sdkVersion + " to "
                        + mSdkVersion + "; regranting permissions for internal storage");
                updateFlags |= UPDATE_PERMISSIONS_REPLACE_PKG | UPDATE_PERMISSIONS_REPLACE_ALL;
            }
            updatePermissionsLPw(null, null, StorageManager.UUID_PRIVATE_INTERNAL, updateFlags);
            ver.sdkVersion = mSdkVersion;

            // If this is the first boot or an update from pre-M, and it is a normal
            // boot, then we need to initialize the default preferred apps across
            // all defined users.
            if (!onlyCore && (mPromoteSystemApps || mFirstBoot)) {
                for (UserInfo user : sUserManager.getUsers(true)) {
                    mSettings.applyDefaultPreferredAppsLPw(this, user.id);
                    applyFactoryDefaultBrowserLPw(user.id);
                    primeDomainVerificationsLPw(user.id);
                }
            }

            // Prepare storage for system user really early during boot,
            // since core system apps like SettingsProvider and SystemUI
            // can't wait for user to start
            final int storageFlags;
            if (StorageManager.isFileEncryptedNativeOrEmulated()) {
                storageFlags = StorageManager.FLAG_STORAGE_DE;
            } else {
                storageFlags = StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE;
            }
            reconcileAppsDataLI(StorageManager.UUID_PRIVATE_INTERNAL, UserHandle.USER_SYSTEM,
                    storageFlags);

            // Disable components marked for disabling at build-time
            mDisabledComponentsList = new ArrayList<ComponentName>();
            for (String name : mContext.getResources().getStringArray(
                    com.android.internal.R.array.config_disabledComponents)) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                mDisabledComponentsList.add(cn);
                Slog.v(TAG, "Disabling " + name);
                String className = cn.getClassName();
                PackageSetting pkgSetting = mSettings.mPackages.get(cn.getPackageName());
                if (pkgSetting == null || pkgSetting.pkg == null
                        || !pkgSetting.pkg.hasComponentClassName(className)) {
                    Slog.w(TAG, "Unable to disable " + name);
                    continue;
                }
                pkgSetting.disableComponentLPw(className, UserHandle.USER_OWNER);
            }

            // Enable components marked for forced-enable at build-time
            for (String name : mContext.getResources().getStringArray(
                    com.android.internal.R.array.config_forceEnabledComponents)) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                Slog.v(TAG, "Enabling " + name);
                String className = cn.getClassName();
                PackageSetting pkgSetting = mSettings.mPackages.get(cn.getPackageName());
                if (pkgSetting == null || pkgSetting.pkg == null
                        || !pkgSetting.pkg.hasComponentClassName(className)) {
                    Slog.w(TAG, "Unable to enable " + name);
                    continue;
                }
                pkgSetting.enableComponentLPw(className, UserHandle.USER_OWNER);
            }

            // If this is first boot after an OTA, and a normal boot, then
            // we need to clear code cache directories.
            // Note that we do *not* clear the application profiles. These remain valid
            // across OTAs and are used to drive profile verification (post OTA) and
            // profile compilation (without waiting to collect a fresh set of profiles).
            if (mIsUpgrade && !onlyCore) {
                Slog.i(TAG, "Build fingerprint changed; clearing code caches");
                for (int i = 0; i < mSettings.mPackages.size(); i++) {
                    final PackageSetting ps = mSettings.mPackages.valueAt(i);
                    if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, ps.volumeUuid)) {
                        // No apps are running this early, so no need to freeze
                        clearAppDataLIF(ps.pkg, UserHandle.USER_ALL,
                                StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE
                                        | Installer.FLAG_CLEAR_CODE_CACHE_ONLY);
                    }
                }
                ver.fingerprint = Build.FINGERPRINT;
            }

            checkDefaultBrowser();

            // clear only after permissions and other defaults have been updated
            mExistingSystemPackages.clear();
            mPromoteSystemApps = false;

            // All the changes are done during package scanning.
            ver.databaseVersion = Settings.CURRENT_DATABASE_VERSION;

            // can downgrade to reader
            mSettings.writeLPr();

            // Perform dexopt on all apps that mark themselves as coreApps. We do this pretty
            // early on (before the package manager declares itself as early) because other
            // components in the system server might ask for package contexts for these apps.
            //
            // Note that "onlyCore" in this context means the system is encrypted or encrypting
            // (i.e, that the data partition is unavailable).
            if ((isFirstBoot() || isUpgrade() || VMRuntime.didPruneDalvikCache()) && !onlyCore) {
                long start = System.nanoTime();
                List<PackageParser.Package> coreApps = new ArrayList<>();
                for (PackageParser.Package pkg : mPackages.values()) {
                    if (pkg.coreApp) {
                        coreApps.add(pkg);
                    }
                }

                int[] stats = performDexOptUpgrade(coreApps, false,
                        getCompilerFilterForReason(REASON_CORE_APP));

                final int elapsedTimeSeconds =
                        (int) TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
                MetricsLogger.histogram(mContext, "opt_coreapps_time_s", elapsedTimeSeconds);

                if (DEBUG_DEXOPT) {
                    Slog.i(TAG, "Dex-opt core apps took : " + elapsedTimeSeconds + " seconds (" +
                            stats[0] + ", " + stats[1] + ", " + stats[2] + ")");
                }


                // TODO: Should we log these stats to tron too ?
                // MetricsLogger.histogram(mContext, "opt_coreapps_num_dexopted", stats[0]);
                // MetricsLogger.histogram(mContext, "opt_coreapps_num_skipped", stats[1]);
                // MetricsLogger.histogram(mContext, "opt_coreapps_num_failed", stats[2]);
                // MetricsLogger.histogram(mContext, "opt_coreapps_num_total", coreApps.size());
            }

            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_READY,
                    SystemClock.uptimeMillis());

            if (!mOnlyCore) {
                mRequiredVerifierPackage = getRequiredButNotReallyRequiredVerifierLPr();
                mRequiredInstallerPackage = getRequiredInstallerLPr();
                mIntentFilterVerifierComponent = getIntentFilterVerifierComponentNameLPr();
                mIntentFilterVerifier = new IntentVerifierProxy(mContext,
                        mIntentFilterVerifierComponent);
                mServicesSystemSharedLibraryPackageName = getRequiredSharedLibraryLPr(
                        PackageManager.SYSTEM_SHARED_LIBRARY_SERVICES);
                mSharedSystemSharedLibraryPackageName = getRequiredSharedLibraryLPr(
                        PackageManager.SYSTEM_SHARED_LIBRARY_SHARED);
            } else {
                mRequiredVerifierPackage = null;
                mRequiredInstallerPackage = null;
                mIntentFilterVerifierComponent = null;
                mIntentFilterVerifier = null;
                mServicesSystemSharedLibraryPackageName = null;
                mSharedSystemSharedLibraryPackageName = null;
            }

            mInstallerService = new PackageInstallerService(context, this);

            final ComponentName ephemeralResolverComponent = getEphemeralResolverLPr();
            final ComponentName ephemeralInstallerComponent = getEphemeralInstallerLPr();
            // both the installer and resolver must be present to enable ephemeral
            if (ephemeralInstallerComponent != null && ephemeralResolverComponent != null) {
                if (DEBUG_EPHEMERAL) {
                    Slog.i(TAG, "Ephemeral activated; resolver: " + ephemeralResolverComponent
                            + " installer:" + ephemeralInstallerComponent);
                }
                mEphemeralResolverComponent = ephemeralResolverComponent;
                mEphemeralInstallerComponent = ephemeralInstallerComponent;
                setUpEphemeralInstallerActivityLP(mEphemeralInstallerComponent);
                mEphemeralResolverConnection =
                        new EphemeralResolverConnection(mContext, mEphemeralResolverComponent);
            } else {
                if (DEBUG_EPHEMERAL) {
                    final String missingComponent =
                            (ephemeralResolverComponent == null)
                            ? (ephemeralInstallerComponent == null)
                                    ? "resolver and installer"
                                    : "resolver"
                            : "installer";
                    Slog.i(TAG, "Ephemeral deactivated; missing " + missingComponent);
                }
                mEphemeralResolverComponent = null;
                mEphemeralInstallerComponent = null;
                mEphemeralResolverConnection = null;
            }

            mEphemeralApplicationRegistry = new EphemeralApplicationRegistry(this);
        //} // synchronized (mPackages)
        //} // synchronized (mInstallLock)

        // Now after opening every single application zip, make sure they
        // are all flushed.  Not really needed, but keeps things nice and
        // tidy.
        Runtime.getRuntime().gc();

        // The initial scanning above does many calls into installd while
        // holding the mPackages lock, but we're mostly interested in yelling
        // once we have a booted system.
        mInstaller.setWarnIfHeld(mPackages);

        // Expose private service for system components to use.
        LocalServices.addService(PackageManagerInternal.class, new PackageManagerInternalImpl());
    }

    @Override
    public boolean isFirstBoot() {
        return mFirstBoot;
    }

    @Override
    public boolean isOnlyCoreApps() {
        return mOnlyCore;
    }

    @Override
    public boolean isUpgrade() {
        return mIsUpgrade;
    }

    private @Nullable String getRequiredButNotReallyRequiredVerifierLPr() {
        final Intent intent = new Intent(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);

        final List<ResolveInfo> matches = queryIntentReceiversInternal(intent, PACKAGE_MIME_TYPE,
                MATCH_SYSTEM_ONLY | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                UserHandle.USER_SYSTEM);
        if (matches.size() == 1) {
            return matches.get(0).getComponentInfo().packageName;
        } else {
            Log.e(TAG, "There should probably be exactly one verifier; found " + matches);
            return null;
        }
    }

    private @NonNull String getRequiredSharedLibraryLPr(String libraryName) {
        synchronized (mPackages) {
            SharedLibraryEntry libraryEntry = mSharedLibraries.get(libraryName);
            if (libraryEntry == null) {
                throw new IllegalStateException("Missing required shared library:" + libraryName);
            }
            return libraryEntry.apk;
        }
    }

    private @NonNull String getRequiredInstallerLPr() {
        final Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setDataAndType(Uri.fromFile(new File("foo.apk")), PACKAGE_MIME_TYPE);

        final List<ResolveInfo> matches = queryIntentActivitiesInternal(intent, PACKAGE_MIME_TYPE,
                MATCH_SYSTEM_ONLY | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                UserHandle.USER_SYSTEM);
        if (matches.size() == 1) {
            ResolveInfo resolveInfo = matches.get(0);
            if (!resolveInfo.activityInfo.applicationInfo.isPrivilegedApp()) {
                throw new RuntimeException("The installer must be a privileged app");
            }
            return matches.get(0).getComponentInfo().packageName;
        } else {
            throw new RuntimeException("There must be exactly one installer; found " + matches);
        }
    }

    private @NonNull ComponentName getIntentFilterVerifierComponentNameLPr() {
        final Intent intent = new Intent(Intent.ACTION_INTENT_FILTER_NEEDS_VERIFICATION);

        final List<ResolveInfo> matches = queryIntentReceiversInternal(intent, PACKAGE_MIME_TYPE,
                MATCH_SYSTEM_ONLY | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                UserHandle.USER_SYSTEM);
        ResolveInfo best = null;
        final int N = matches.size();
        for (int i = 0; i < N; i++) {
            final ResolveInfo cur = matches.get(i);
            final String packageName = cur.getComponentInfo().packageName;
            if (checkPermission(android.Manifest.permission.INTENT_FILTER_VERIFICATION_AGENT,
                    packageName, UserHandle.USER_SYSTEM) != PackageManager.PERMISSION_GRANTED) {
                continue;
            }

            if (best == null || cur.priority > best.priority) {
                best = cur;
            }
        }

        if (best != null) {
            return best.getComponentInfo().getComponentName();
        } else {
            throw new RuntimeException("There must be at least one intent filter verifier");
        }
    }

    private @Nullable ComponentName getEphemeralResolverLPr() {
        final String[] packageArray =
                mContext.getResources().getStringArray(R.array.config_ephemeralResolverPackage);
        if (packageArray.length == 0 && !Build.IS_DEBUGGABLE) {
            if (DEBUG_EPHEMERAL) {
                Slog.d(TAG, "Ephemeral resolver NOT found; empty package list");
            }
            return null;
        }

        final int resolveFlags =
                MATCH_DIRECT_BOOT_AWARE
                | MATCH_DIRECT_BOOT_UNAWARE
                | (!Build.IS_DEBUGGABLE ? MATCH_SYSTEM_ONLY : 0);
        final Intent resolverIntent = new Intent(Intent.ACTION_RESOLVE_EPHEMERAL_PACKAGE);
        final List<ResolveInfo> resolvers = queryIntentServicesInternal(resolverIntent, null,
                resolveFlags, UserHandle.USER_SYSTEM);

        final int N = resolvers.size();
        if (N == 0) {
            if (DEBUG_EPHEMERAL) {
                Slog.d(TAG, "Ephemeral resolver NOT found; no matching intent filters");
            }
            return null;
        }

        final Set<String> possiblePackages = new ArraySet<>(Arrays.asList(packageArray));
        for (int i = 0; i < N; i++) {
            final ResolveInfo info = resolvers.get(i);

            if (info.serviceInfo == null) {
                continue;
            }

            final String packageName = info.serviceInfo.packageName;
            if (!possiblePackages.contains(packageName) && !Build.IS_DEBUGGABLE) {
                if (DEBUG_EPHEMERAL) {
                    Slog.d(TAG, "Ephemeral resolver not in allowed package list;"
                            + " pkg: " + packageName + ", info:" + info);
                }
                continue;
            }

            if (DEBUG_EPHEMERAL) {
                Slog.v(TAG, "Ephemeral resolver found;"
                        + " pkg: " + packageName + ", info:" + info);
            }
            return new ComponentName(packageName, info.serviceInfo.name);
        }
        if (DEBUG_EPHEMERAL) {
            Slog.v(TAG, "Ephemeral resolver NOT found");
        }
        return null;
    }

    private @Nullable ComponentName getEphemeralInstallerLPr() {
        final Intent intent = new Intent(Intent.ACTION_INSTALL_EPHEMERAL_PACKAGE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setDataAndType(Uri.fromFile(new File("foo.apk")), PACKAGE_MIME_TYPE);

        final int resolveFlags =
                MATCH_DIRECT_BOOT_AWARE
                | MATCH_DIRECT_BOOT_UNAWARE
                | (!Build.IS_DEBUGGABLE ? MATCH_SYSTEM_ONLY : 0);
        final List<ResolveInfo> matches = queryIntentActivitiesInternal(intent, PACKAGE_MIME_TYPE,
                resolveFlags, UserHandle.USER_SYSTEM);
        if (matches.size() == 0) {
            return null;
        } else if (matches.size() == 1) {
            return matches.get(0).getComponentInfo().getComponentName();
        } else {
            throw new RuntimeException(
                    "There must be at most one ephemeral installer; found " + matches);
        }
    }

    private void primeDomainVerificationsLPw(int userId) {
        if (DEBUG_DOMAIN_VERIFICATION) {
            Slog.d(TAG, "Priming domain verifications in user " + userId);
        }

        SystemConfig systemConfig = SystemConfig.getInstance();
        ArraySet<String> packages = systemConfig.getLinkedApps();
        ArraySet<String> domains = new ArraySet<String>();

        for (String packageName : packages) {
            PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg != null) {
                if (!pkg.isSystemApp()) {
                    Slog.w(TAG, "Non-system app '" + packageName + "' in sysconfig <app-link>");
                    continue;
                }

                domains.clear();
                for (PackageParser.Activity a : pkg.activities) {
                    for (ActivityIntentInfo filter : a.intents) {
                        if (hasValidDomains(filter)) {
                            domains.addAll(filter.getHostsList());
                        }
                    }
                }

                if (domains.size() > 0) {
                    if (DEBUG_DOMAIN_VERIFICATION) {
                        Slog.v(TAG, "      + " + packageName);
                    }
                    // 'Undefined' in the global IntentFilterVerificationInfo, i.e. the usual
                    // state w.r.t. the formal app-linkage "no verification attempted" state;
                    // and then 'always' in the per-user state actually used for intent resolution.
                    final IntentFilterVerificationInfo ivi;
                    ivi = mSettings.createIntentFilterVerificationIfNeededLPw(packageName,
                            new ArrayList<String>(domains));
                    ivi.setStatus(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED);
                    mSettings.updateIntentFilterVerificationStatusLPw(packageName,
                            INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS, userId);
                } else {
                    Slog.w(TAG, "Sysconfig <app-link> package '" + packageName
                            + "' does not handle web links");
                }
            } else {
                Slog.w(TAG, "Unknown package " + packageName + " in sysconfig <app-link>");
            }
        }

        scheduleWritePackageRestrictionsLocked(userId);
        scheduleWriteSettingsLocked();
    }

    private void applyFactoryDefaultBrowserLPw(int userId) {
        // The default browser app's package name is stored in a string resource,
        // with a product-specific overlay used for vendor customization.
        String browserPkg = mContext.getResources().getString(
                com.android.internal.R.string.default_browser);
        if (!TextUtils.isEmpty(browserPkg)) {
            // non-empty string => required to be a known package
            PackageSetting ps = mSettings.mPackages.get(browserPkg);
            if (ps == null) {
                Slog.e(TAG, "Product default browser app does not exist: " + browserPkg);
                browserPkg = null;
            } else {
                mSettings.setDefaultBrowserPackageNameLPw(browserPkg, userId);
            }
        }

        // Nothing valid explicitly set? Make the factory-installed browser the explicit
        // default.  If there's more than one, just leave everything alone.
        if (browserPkg == null) {
            calculateDefaultBrowserLPw(userId);
        }
    }

    private void calculateDefaultBrowserLPw(int userId) {
        List<String> allBrowsers = resolveAllBrowserApps(userId);
        final String browserPkg = (allBrowsers.size() == 1) ? allBrowsers.get(0) : null;
        mSettings.setDefaultBrowserPackageNameLPw(browserPkg, userId);
    }

    private List<String> resolveAllBrowserApps(int userId) {
        // Resolve the canonical browser intent and check that the handleAllWebDataURI boolean is set
        List<ResolveInfo> list = queryIntentActivitiesInternal(sBrowserIntent, null,
                PackageManager.MATCH_ALL, userId);

        final int count = list.size();
        List<String> result = new ArrayList<String>(count);
        for (int i=0; i<count; i++) {
            ResolveInfo info = list.get(i);
            if (info.activityInfo == null
                    || !info.handleAllWebDataURI
                    || (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || result.contains(info.activityInfo.packageName)) {
                continue;
            }
            result.add(info.activityInfo.packageName);
        }

        return result;
    }

    private boolean packageIsBrowser(String packageName, int userId) {
        List<ResolveInfo> list = queryIntentActivitiesInternal(sBrowserIntent, null,
                PackageManager.MATCH_ALL, userId);
        final int N = list.size();
        for (int i = 0; i < N; i++) {
            ResolveInfo info = list.get(i);
            if (packageName.equals(info.activityInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void checkDefaultBrowser() {
        final int myUserId = UserHandle.myUserId();
        final String packageName = getDefaultBrowserPackageName(myUserId);
        if (packageName != null) {
            PackageInfo info = getPackageInfo(packageName, 0, myUserId);
            if (info == null) {
                Slog.w(TAG, "Default browser no longer installed: " + packageName);
                synchronized (mPackages) {
                    applyFactoryDefaultBrowserLPw(myUserId);    // leaves ambiguous when > 1
                }
            }
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            if (!(e instanceof SecurityException) && !(e instanceof IllegalArgumentException)) {
                Slog.wtf(TAG, "Package Manager Crash", e);
            }
            throw e;
        }
    }

    static int[] appendInts(int[] cur, int[] add) {
        if (add == null) return cur;
        if (cur == null) return add;
        final int N = add.length;
        for (int i=0; i<N; i++) {
            cur = appendInt(cur, add[i]);
        }
        return cur;
    }

    private PackageInfo generatePackageInfo(PackageSetting ps, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        if (ps == null) {
            return null;
        }
        final PackageParser.Package p = ps.pkg;
        if (p == null) {
            return null;
        }

        final PermissionsState permissionsState = ps.getPermissionsState();

        // Compute GIDs only if requested
        final int[] gids = (flags & PackageManager.GET_GIDS) == 0
                ? EMPTY_INT_ARRAY : permissionsState.computeGids(userId);
        // Compute granted permissions only if package has requested permissions
        final Set<String> permissions = ArrayUtils.isEmpty(p.requestedPermissions)
                ? Collections.<String>emptySet() : permissionsState.getPermissions(userId);
        final PackageUserState state = ps.readUserState(userId);

        return PackageParser.generatePackageInfo(p, gids, flags,
                ps.firstInstallTime, ps.lastUpdateTime, permissions, state, userId);
    }

    @Override
    public void checkPackageStartable(String packageName, int userId) {
        final boolean userKeyUnlocked = StorageManager.isUserKeyUnlocked(userId);

        synchronized (mPackages) {
            final PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                throw new SecurityException("Package " + packageName + " was not found!");
            }

            if (!ps.getInstalled(userId)) {
                throw new SecurityException(
                        "Package " + packageName + " was not installed for user " + userId + "!");
            }

            if (mSafeMode && !ps.isSystem()) {
                throw new SecurityException("Package " + packageName + " not a system app!");
            }

            if (mFrozenPackages.contains(packageName)) {
                throw new SecurityException("Package " + packageName + " is currently frozen!");
            }

            if (!userKeyUnlocked && !(ps.pkg.applicationInfo.isDirectBootAware()
                    || ps.pkg.applicationInfo.isPartiallyDirectBootAware())) {
                throw new SecurityException("Package " + packageName + " is not encryption aware!");
            }
        }
    }

    @Override
    public boolean isPackageAvailable(String packageName, int userId) {
        if (!sUserManager.exists(userId)) return false;
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "is package available");
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if (p != null) {
                final PackageSetting ps = (PackageSetting) p.mExtras;
                if (ps != null) {
                    final PackageUserState state = ps.readUserState(userId);
                    if (state != null) {
                        return PackageParser.isAvailable(state);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForPackage(flags, userId, packageName);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get package info");
        // reader
        synchronized (mPackages) {
            final boolean matchFactoryOnly = (flags & MATCH_FACTORY_ONLY) != 0;
            PackageParser.Package p = null;
            if (matchFactoryOnly) {
                final PackageSetting ps = mSettings.getDisabledSystemPkgLPr(packageName);
                if (ps != null) {
                    return generatePackageInfo(ps, flags, userId);
                }
            }
            if (p == null) {
                p = mPackages.get(packageName);
                if (matchFactoryOnly && p != null && !isSystemApp(p)) {
                    return null;
                }
            }
            if (DEBUG_PACKAGE_INFO)
                Log.v(TAG, "getPackageInfo " + packageName + ": " + p);
            if (p != null) {
                return generatePackageInfo((PackageSetting)p.mExtras, flags, userId);
            }
            if (!matchFactoryOnly && (flags & MATCH_UNINSTALLED_PACKAGES) != 0) {
                final PackageSetting ps = mSettings.mPackages.get(packageName);
                return generatePackageInfo(ps, flags, userId);
            }
        }
        return null;
    }

    @Override
    public String[] currentToCanonicalPackageNames(String[] names) {
        String[] out = new String[names.length];
        // reader
        synchronized (mPackages) {
            for (int i=names.length-1; i>=0; i--) {
                PackageSetting ps = mSettings.mPackages.get(names[i]);
                out[i] = ps != null && ps.realName != null ? ps.realName : names[i];
            }
        }
        return out;
    }

    @Override
    public String[] canonicalToCurrentPackageNames(String[] names) {
        String[] out = new String[names.length];
        // reader
        synchronized (mPackages) {
            for (int i=names.length-1; i>=0; i--) {
                String cur = mSettings.mRenamedPackages.get(names[i]);
                out[i] = cur != null ? cur : names[i];
            }
        }
        return out;
    }

    @Override
    public int getPackageUid(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return -1;
        flags = updateFlagsForPackage(flags, userId, packageName);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get package uid");

        // reader
        synchronized (mPackages) {
            final PackageParser.Package p = mPackages.get(packageName);
            if (p != null && p.isMatch(flags)) {
                return UserHandle.getUid(userId, p.applicationInfo.uid);
            }
            if ((flags & MATCH_UNINSTALLED_PACKAGES) != 0) {
                final PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps != null && ps.isMatch(flags)) {
                    return UserHandle.getUid(userId, ps.appId);
                }
            }
        }

        return -1;
    }

    @Override
    public int[] getPackageGids(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForPackage(flags, userId, packageName);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */,
                "getPackageGids");

        // reader
        synchronized (mPackages) {
            final PackageParser.Package p = mPackages.get(packageName);
            if (p != null && p.isMatch(flags)) {
                PackageSetting ps = (PackageSetting) p.mExtras;
                return ps.getPermissionsState().computeGids(userId);
            }
            if ((flags & MATCH_UNINSTALLED_PACKAGES) != 0) {
                final PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps != null && ps.isMatch(flags)) {
                    return ps.getPermissionsState().computeGids(userId);
                }
            }
        }

        return null;
    }

    static PermissionInfo generatePermissionInfo(BasePermission bp, int flags) {
        if (bp.perm != null) {
            return PackageParser.generatePermissionInfo(bp.perm, flags);
        }
        PermissionInfo pi = new PermissionInfo();
        pi.name = bp.name;
        pi.packageName = bp.sourcePackage;
        pi.nonLocalizedLabel = bp.name;
        pi.protectionLevel = bp.protectionLevel;
        return pi;
    }

    @Override
    public PermissionInfo getPermissionInfo(String name, int flags) {
        // reader
        synchronized (mPackages) {
            final BasePermission p = mSettings.mPermissions.get(name);
            if (p != null) {
                return generatePermissionInfo(p, flags);
            }
            return null;
        }
    }

    @Override
    public @Nullable ParceledListSlice<PermissionInfo> queryPermissionsByGroup(String group,
            int flags) {
        // reader
        synchronized (mPackages) {
            if (group != null && !mPermissionGroups.containsKey(group)) {
                // This is thrown as NameNotFoundException
                return null;
            }

            ArrayList<PermissionInfo> out = new ArrayList<PermissionInfo>(10);
            for (BasePermission p : mSettings.mPermissions.values()) {
                if (group == null) {
                    if (p.perm == null || p.perm.info.group == null) {
                        out.add(generatePermissionInfo(p, flags));
                    }
                } else {
                    if (p.perm != null && group.equals(p.perm.info.group)) {
                        out.add(PackageParser.generatePermissionInfo(p.perm, flags));
                    }
                }
            }
            return new ParceledListSlice<>(out);
        }
    }

    @Override
    public PermissionGroupInfo getPermissionGroupInfo(String name, int flags) {
        // reader
        synchronized (mPackages) {
            return PackageParser.generatePermissionGroupInfo(
                    mPermissionGroups.get(name), flags);
        }
    }

    @Override
    public @NonNull ParceledListSlice<PermissionGroupInfo> getAllPermissionGroups(int flags) {
        // reader
        synchronized (mPackages) {
            final int N = mPermissionGroups.size();
            ArrayList<PermissionGroupInfo> out
                    = new ArrayList<PermissionGroupInfo>(N);
            for (PackageParser.PermissionGroup pg : mPermissionGroups.values()) {
                out.add(PackageParser.generatePermissionGroupInfo(pg, flags));
            }
            return new ParceledListSlice<>(out);
        }
    }

    private ApplicationInfo generateApplicationInfoFromSettingsLPw(String packageName, int flags,
            int userId) {
        if (!sUserManager.exists(userId)) return null;
        PackageSetting ps = mSettings.mPackages.get(packageName);
        if (ps != null) {
            if (ps.pkg == null) {
                final PackageInfo pInfo = generatePackageInfo(ps, flags, userId);
                if (pInfo != null) {
                    return pInfo.applicationInfo;
                }
                return null;
            }
            return PackageParser.generateApplicationInfo(ps.pkg, flags,
                    ps.readUserState(userId), userId);
        }
        return null;
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForApplication(flags, userId, packageName);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get application info");
        // writer
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if (DEBUG_PACKAGE_INFO) Log.v(
                    TAG, "getApplicationInfo " + packageName
                    + ": " + p);
            if (p != null) {
                PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps == null) return null;
                // Note: isEnabledLP() does not apply here - always return info
                return PackageParser.generateApplicationInfo(
                        p, flags, ps.readUserState(userId), userId);
            }
            if ("android".equals(packageName)||"system".equals(packageName)) {
                return mAndroidApplication;
            }
            if ((flags & MATCH_UNINSTALLED_PACKAGES) != 0) {
                return generateApplicationInfoFromSettingsLPw(packageName, flags, userId);
            }
        }
        return null;
    }

    @Override
    public void freeStorageAndNotify(final String volumeUuid, final long freeStorageSize,
            final IPackageDataObserver observer) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CLEAR_APP_CACHE, null);
        // Queue up an async operation since clearing cache may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                boolean success = true;
                synchronized (mInstallLock) {
                    try {
                        mInstaller.freeCache(volumeUuid, freeStorageSize);
                    } catch (InstallerException e) {
                        Slog.w(TAG, "Couldn't clear application caches: " + e);
                        success = false;
                    }
                }
                if (observer != null) {
                    try {
                        observer.onRemoveCompleted(null, success);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "RemoveException when invoking call back");
                    }
                }
            }
        });
    }

    @Override
    public void freeStorage(final String volumeUuid, final long freeStorageSize,
            final IntentSender pi) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CLEAR_APP_CACHE, null);
        // Queue up an async operation since clearing cache may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                boolean success = true;
                synchronized (mInstallLock) {
                    try {
                        mInstaller.freeCache(volumeUuid, freeStorageSize);
                    } catch (InstallerException e) {
                        Slog.w(TAG, "Couldn't clear application caches: " + e);
                        success = false;
                    }
                }
                if(pi != null) {
                    try {
                        // Callback via pending intent
                        int code = success ? 1 : 0;
                        pi.sendIntent(null, code, null,
                                null, null);
                    } catch (SendIntentException e1) {
                        Slog.i(TAG, "Failed to send pending intent");
                    }
                }
            }
        });
    }

    void freeStorage(String volumeUuid, long freeStorageSize) throws IOException {
        synchronized (mInstallLock) {
            try {
                mInstaller.freeCache(volumeUuid, freeStorageSize);
            } catch (InstallerException e) {
                throw new IOException("Failed to free enough space", e);
            }
        }
    }

    /**
     * Update given flags based on encryption status of current user.
     */
    private int updateFlags(int flags, int userId) {
        if ((flags & (PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                | PackageManager.MATCH_DIRECT_BOOT_AWARE)) != 0) {
            // Caller expressed an explicit opinion about what encryption
            // aware/unaware components they want to see, so fall through and
            // give them what they want
        } else {
            // Caller expressed no opinion, so match based on user state
            if (getUserManagerInternal().isUserUnlockingOrUnlocked(userId)) {
                flags |= PackageManager.MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE;
            } else {
                flags |= PackageManager.MATCH_DIRECT_BOOT_AWARE;
            }
        }
        return flags;
    }

    private UserManagerInternal getUserManagerInternal() {
        if (mUserManagerInternal == null) {
            mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
        }
        return mUserManagerInternal;
    }

    /**
     * Update given flags when being used to request {@link PackageInfo}.
     */
    private int updateFlagsForPackage(int flags, int userId, Object cookie) {
        boolean triaged = true;
        if ((flags & (PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS)) != 0) {
            // Caller is asking for component details, so they'd better be
            // asking for specific encryption matching behavior, or be triaged
            if ((flags & (PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                    | PackageManager.MATCH_DIRECT_BOOT_AWARE
                    | PackageManager.MATCH_DEBUG_TRIAGED_MISSING)) == 0) {
                triaged = false;
            }
        }
        if ((flags & (PackageManager.MATCH_UNINSTALLED_PACKAGES
                | PackageManager.MATCH_SYSTEM_ONLY
                | PackageManager.MATCH_DEBUG_TRIAGED_MISSING)) == 0) {
            triaged = false;
        }
        if (DEBUG_TRIAGED_MISSING && (Binder.getCallingUid() == Process.SYSTEM_UID) && !triaged) {
            Log.w(TAG, "Caller hasn't been triaged for missing apps; they asked about " + cookie
                    + " with flags 0x" + Integer.toHexString(flags), new Throwable());
        }
        return updateFlags(flags, userId);
    }

    /**
     * Update given flags when being used to request {@link ApplicationInfo}.
     */
    private int updateFlagsForApplication(int flags, int userId, Object cookie) {
        return updateFlagsForPackage(flags, userId, cookie);
    }

    /**
     * Update given flags when being used to request {@link ComponentInfo}.
     */
    private int updateFlagsForComponent(int flags, int userId, Object cookie) {
        if (cookie instanceof Intent) {
            if ((((Intent) cookie).getFlags() & Intent.FLAG_DEBUG_TRIAGED_MISSING) != 0) {
                flags |= PackageManager.MATCH_DEBUG_TRIAGED_MISSING;
            }
        }

        boolean triaged = true;
        // Caller is asking for component details, so they'd better be
        // asking for specific encryption matching behavior, or be triaged
        if ((flags & (PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DEBUG_TRIAGED_MISSING)) == 0) {
            triaged = false;
        }
        if (DEBUG_TRIAGED_MISSING && (Binder.getCallingUid() == Process.SYSTEM_UID) && !triaged) {
            Log.w(TAG, "Caller hasn't been triaged for missing apps; they asked about " + cookie
                    + " with flags 0x" + Integer.toHexString(flags), new Throwable());
        }

        return updateFlags(flags, userId);
    }

    /**
     * Update given flags when being used to request {@link ResolveInfo}.
     */
    int updateFlagsForResolve(int flags, int userId, Object cookie) {
        // Safe mode means we shouldn't match any third-party components
        if (mSafeMode) {
            flags |= PackageManager.MATCH_SYSTEM_ONLY;
        }

        return updateFlagsForComponent(flags, userId, cookie);
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForComponent(flags, userId, component);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get activity info");
        synchronized (mPackages) {
            PackageParser.Activity a = mActivities.mActivities.get(component);

            if (DEBUG_PACKAGE_INFO) Log.v(TAG, "getActivityInfo " + component + ": " + a);
            if (a != null && mSettings.isEnabledAndMatchLPr(a.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateActivityInfo(a, flags, ps.readUserState(userId),
                        userId);
            }
            if (mResolveComponentName.equals(component)) {
                return PackageParser.generateActivityInfo(mResolveActivity, flags,
                        new PackageUserState(), userId);
            }
        }
        return null;
    }

    @Override
    public boolean activitySupportsIntent(ComponentName component, Intent intent,
            String resolvedType) {
        synchronized (mPackages) {
            if (component.equals(mResolveComponentName)) {
                // The resolver supports EVERYTHING!
                return true;
            }
            PackageParser.Activity a = mActivities.mActivities.get(component);
            if (a == null) {
                return false;
            }
            for (int i=0; i<a.intents.size(); i++) {
                if (a.intents.get(i).match(intent.getAction(), resolvedType, intent.getScheme(),
                        intent.getData(), intent.getCategories(), TAG) >= 0) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForComponent(flags, userId, component);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get receiver info");
        synchronized (mPackages) {
            PackageParser.Activity a = mReceivers.mActivities.get(component);
            if (DEBUG_PACKAGE_INFO) Log.v(
                TAG, "getReceiverInfo " + component + ": " + a);
            if (a != null && mSettings.isEnabledAndMatchLPr(a.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateActivityInfo(a, flags, ps.readUserState(userId),
                        userId);
            }
        }
        return null;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForComponent(flags, userId, component);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get service info");
        synchronized (mPackages) {
            PackageParser.Service s = mServices.mServices.get(component);
            if (DEBUG_PACKAGE_INFO) Log.v(
                TAG, "getServiceInfo " + component + ": " + s);
            if (s != null && mSettings.isEnabledAndMatchLPr(s.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateServiceInfo(s, flags, ps.readUserState(userId),
                        userId);
            }
        }
        return null;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForComponent(flags, userId, component);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */, "get provider info");
        synchronized (mPackages) {
            PackageParser.Provider p = mProviders.mProviders.get(component);
            if (DEBUG_PACKAGE_INFO) Log.v(
                TAG, "getProviderInfo " + component + ": " + p);
            if (p != null && mSettings.isEnabledAndMatchLPr(p.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateProviderInfo(p, flags, ps.readUserState(userId),
                        userId);
            }
        }
        return null;
    }

    @Override
    public String[] getSystemSharedLibraryNames() {
        Set<String> libSet;
        synchronized (mPackages) {
            libSet = mSharedLibraries.keySet();
            int size = libSet.size();
            if (size > 0) {
                String[] libs = new String[size];
                libSet.toArray(libs);
                return libs;
            }
        }
        return null;
    }

    @Override
    public @NonNull String getServicesSystemSharedLibraryPackageName() {
        synchronized (mPackages) {
            return mServicesSystemSharedLibraryPackageName;
        }
    }

    @Override
    public @NonNull String getSharedSystemSharedLibraryPackageName() {
        synchronized (mPackages) {
            return mSharedSystemSharedLibraryPackageName;
        }
    }

    @Override
    public @NonNull ParceledListSlice<FeatureInfo> getSystemAvailableFeatures() {
        synchronized (mPackages) {
            final ArrayList<FeatureInfo> res = new ArrayList<>(mAvailableFeatures.values());

            final FeatureInfo fi = new FeatureInfo();
            fi.reqGlEsVersion = SystemProperties.getInt("ro.opengles.version",
                    FeatureInfo.GL_ES_VERSION_UNDEFINED);
            res.add(fi);

            return new ParceledListSlice<>(res);
        }
    }

    @Override
    public boolean hasSystemFeature(String name, int version) {
        synchronized (mPackages) {
            final FeatureInfo feat = mAvailableFeatures.get(name);
            if (feat == null) {
                return false;
            } else {
                return feat.version >= version;
            }
        }
    }

    @Override
    public int checkPermission(String permName, String pkgName, int userId) {
        if (!sUserManager.exists(userId)) {
            return PackageManager.PERMISSION_DENIED;
        }

        synchronized (mPackages) {
            final PackageParser.Package p = mPackages.get(pkgName);
            if (p != null && p.mExtras != null) {
                final PackageSetting ps = (PackageSetting) p.mExtras;
                final PermissionsState permissionsState = ps.getPermissionsState();
                if (permissionsState.hasPermission(permName, userId)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                // Special case: ACCESS_FINE_LOCATION permission includes ACCESS_COARSE_LOCATION
                if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(permName) && permissionsState
                        .hasPermission(Manifest.permission.ACCESS_FINE_LOCATION, userId)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            }
        }

        return PackageManager.PERMISSION_DENIED;
    }

    @Override
    public int checkUidPermission(String permName, int uid) {
        final int userId = UserHandle.getUserId(uid);

        if (!sUserManager.exists(userId)) {
            return PackageManager.PERMISSION_DENIED;
        }

        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(UserHandle.getAppId(uid));
            if (obj != null) {
                final SettingBase ps = (SettingBase) obj;
                final PermissionsState permissionsState = ps.getPermissionsState();
                if (permissionsState.hasPermission(permName, userId)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                // Special case: ACCESS_FINE_LOCATION permission includes ACCESS_COARSE_LOCATION
                if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(permName) && permissionsState
                        .hasPermission(Manifest.permission.ACCESS_FINE_LOCATION, userId)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            } else {
                ArraySet<String> perms = mSystemPermissions.get(uid);
                if (perms != null) {
                    if (perms.contains(permName)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                    if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(permName) && perms
                            .contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                }
            }
        }

        return PackageManager.PERMISSION_DENIED;
    }

    @Override
    public boolean isPermissionRevokedByPolicy(String permission, String packageName, int userId) {
        if (UserHandle.getCallingUserId() != userId) {
            mContext.enforceCallingPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "isPermissionRevokedByPolicy for user " + userId);
        }

        if (checkPermission(permission, packageName, userId)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            final int flags = getPermissionFlags(permission, packageName, userId);
            return (flags & PackageManager.FLAG_PERMISSION_POLICY_FIXED) != 0;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getPermissionControllerPackageName() {
        synchronized (mPackages) {
            return mRequiredInstallerPackage;
        }
    }

    /**
     * Checks if the request is from the system or an app that has INTERACT_ACROSS_USERS
     * or INTERACT_ACROSS_USERS_FULL permissions, if the userid is not for the caller.
     * @param checkShell whether to prevent shell from access if there's a debugging restriction
     * @param message the message to log on security exception
     */
    void enforceCrossUserPermission(int callingUid, int userId, boolean requireFullPermission,
            boolean checkShell, String message) {
        if (userId < 0) {
            throw new IllegalArgumentException("Invalid userId " + userId);
        }
        if (checkShell) {
            enforceShellRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, callingUid, userId);
        }
        if (userId == UserHandle.getUserId(callingUid)) return;
        if (callingUid != Process.SYSTEM_UID && callingUid != 0) {
            if (requireFullPermission) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, message);
            } else {
                try {
                    mContext.enforceCallingOrSelfPermission(
                            android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, message);
                } catch (SecurityException se) {
                    mContext.enforceCallingOrSelfPermission(
                            android.Manifest.permission.INTERACT_ACROSS_USERS, message);
                }
            }
        }
    }

    void enforceShellRestriction(String restriction, int callingUid, int userHandle) {
        if (callingUid == Process.SHELL_UID) {
            if (userHandle >= 0
                    && sUserManager.hasUserRestriction(restriction, userHandle)) {
                throw new SecurityException("Shell does not have permission to access user "
                        + userHandle);
            } else if (userHandle < 0) {
                Slog.e(TAG, "Unable to check shell permission for user " + userHandle + "\n\t"
                        + Debug.getCallers(3));
            }
        }
    }

    private BasePermission findPermissionTreeLP(String permName) {
        for(BasePermission bp : mSettings.mPermissionTrees.values()) {
            if (permName.startsWith(bp.name) &&
                    permName.length() > bp.name.length() &&
                    permName.charAt(bp.name.length()) == '.') {
                return bp;
            }
        }
        return null;
    }

    private BasePermission checkPermissionTreeLP(String permName) {
        if (permName != null) {
            BasePermission bp = findPermissionTreeLP(permName);
            if (bp != null) {
                if (bp.uid == UserHandle.getAppId(Binder.getCallingUid())) {
                    return bp;
                }
                throw new SecurityException("Calling uid "
                        + Binder.getCallingUid()
                        + " is not allowed to add to permission tree "
                        + bp.name + " owned by uid " + bp.uid);
            }
        }
        throw new SecurityException("No permission tree found for " + permName);
    }

    static boolean compareStrings(CharSequence s1, CharSequence s2) {
        if (s1 == null) {
            return s2 == null;
        }
        if (s2 == null) {
            return false;
        }
        if (s1.getClass() != s2.getClass()) {
            return false;
        }
        return s1.equals(s2);
    }

    static boolean comparePermissionInfos(PermissionInfo pi1, PermissionInfo pi2) {
        if (pi1.icon != pi2.icon) return false;
        if (pi1.logo != pi2.logo) return false;
        if (pi1.protectionLevel != pi2.protectionLevel) return false;
        if (!compareStrings(pi1.name, pi2.name)) return false;
        if (!compareStrings(pi1.nonLocalizedLabel, pi2.nonLocalizedLabel)) return false;
        // We'll take care of setting this one.
        if (!compareStrings(pi1.packageName, pi2.packageName)) return false;
        if (pi1.allowViaWhitelist != pi2.allowViaWhitelist) return false;
        // These are not currently stored in settings.
        //if (!compareStrings(pi1.group, pi2.group)) return false;
        //if (!compareStrings(pi1.nonLocalizedDescription, pi2.nonLocalizedDescription)) return false;
        //if (pi1.labelRes != pi2.labelRes) return false;
        //if (pi1.descriptionRes != pi2.descriptionRes) return false;
        return true;
    }

    int permissionInfoFootprint(PermissionInfo info) {
        int size = info.name.length();
        if (info.nonLocalizedLabel != null) size += info.nonLocalizedLabel.length();
        if (info.nonLocalizedDescription != null) size += info.nonLocalizedDescription.length();
        return size;
    }

    int calculateCurrentPermissionFootprintLocked(BasePermission tree) {
        int size = 0;
        for (BasePermission perm : mSettings.mPermissions.values()) {
            if (perm.uid == tree.uid) {
                size += perm.name.length() + permissionInfoFootprint(perm.perm.info);
            }
        }
        return size;
    }

    void enforcePermissionCapLocked(PermissionInfo info, BasePermission tree) {
        // We calculate the max size of permissions defined by this uid and throw
        // if that plus the size of 'info' would exceed our stated maximum.
        if (tree.uid != Process.SYSTEM_UID) {
            final int curTreeSize = calculateCurrentPermissionFootprintLocked(tree);
            if (curTreeSize + permissionInfoFootprint(info) > MAX_PERMISSION_TREE_FOOTPRINT) {
                throw new SecurityException("Permission tree size cap exceeded");
            }
        }
    }

    boolean addPermissionLocked(PermissionInfo info, boolean async) {
        if (info.labelRes == 0 && info.nonLocalizedLabel == null) {
            throw new SecurityException("Label must be specified in permission");
        }
        BasePermission tree = checkPermissionTreeLP(info.name);
        BasePermission bp = mSettings.mPermissions.get(info.name);
        boolean added = bp == null;
        boolean changed = true;
        int fixedLevel = PermissionInfo.fixProtectionLevel(info.protectionLevel);
        if (added) {
            enforcePermissionCapLocked(info, tree);
            bp = new BasePermission(info.name, tree.sourcePackage,
                    BasePermission.TYPE_DYNAMIC);
        } else if (bp.type != BasePermission.TYPE_DYNAMIC) {
            throw new SecurityException(
                    "Not allowed to modify non-dynamic permission "
                    + info.name);
        } else {
            if (bp.protectionLevel == fixedLevel
                    && bp.perm.owner.equals(tree.perm.owner)
                    && bp.uid == tree.uid
                    && comparePermissionInfos(bp.perm.info, info)) {
                changed = false;
            }
        }
        bp.protectionLevel = fixedLevel;
        info = new PermissionInfo(info);
        info.protectionLevel = fixedLevel;
        bp.perm = new PackageParser.Permission(tree.perm.owner, info);
        bp.perm.info.packageName = tree.perm.info.packageName;
        bp.uid = tree.uid;
        if (added) {
            mSettings.mPermissions.put(info.name, bp);
        }
        if (changed) {
            if (!async) {
                mSettings.writeLPr();
            } else {
                scheduleWriteSettingsLocked();
            }
        }
        return added;
    }

    @Override
    public boolean addPermission(PermissionInfo info) {
        synchronized (mPackages) {
            return addPermissionLocked(info, false);
        }
    }

    @Override
    public boolean addPermissionAsync(PermissionInfo info) {
        synchronized (mPackages) {
            return addPermissionLocked(info, true);
        }
    }

    @Override
    public void removePermission(String name) {
        synchronized (mPackages) {
            checkPermissionTreeLP(name);
            BasePermission bp = mSettings.mPermissions.get(name);
            if (bp != null) {
                if (bp.type != BasePermission.TYPE_DYNAMIC) {
                    throw new SecurityException(
                            "Not allowed to modify non-dynamic permission "
                            + name);
                }
                mSettings.mPermissions.remove(name);
                mSettings.writeLPr();
            }
        }
    }

    private static void enforceDeclaredAsUsedAndRuntimeOrDevelopmentPermission(PackageParser.Package pkg,
            BasePermission bp) {
        int index = pkg.requestedPermissions.indexOf(bp.name);
        if (index == -1) {
            throw new SecurityException("Package " + pkg.packageName
                    + " has not requested permission " + bp.name);
        }
        if (!bp.isRuntime() && !bp.isDevelopment()) {
            throw new SecurityException("Permission " + bp.name
                    + " is not a changeable permission type");
        }
    }

    private boolean isAllowedSignature(PackageParser.Package pkg, String permissionName) {
        for (Signature pkgSig : pkg.mSignatures) {
            ArraySet<String> perms = mSignatureAllowances.get(pkgSig);
            if (perms != null && perms.contains(permissionName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void grantRuntimePermission(String packageName, String name, final int userId) {
        if (!sUserManager.exists(userId)) {
            Log.e(TAG, "No such user:" + userId);
            return;
        }

        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                "grantRuntimePermission");

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, true /* checkShell */,
                "grantRuntimePermission");

        final int uid;
        final SettingBase sb;

        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }

            final BasePermission bp = mSettings.mPermissions.get(name);
            if (bp == null) {
                throw new IllegalArgumentException("Unknown permission: " + name);
            }

            enforceDeclaredAsUsedAndRuntimeOrDevelopmentPermission(pkg, bp);

            // If a permission review is required for legacy apps we represent
            // their permissions as always granted runtime ones since we need
            // to keep the review required permission flag per user while an
            // install permission's state is shared across all users.
            if (Build.PERMISSIONS_REVIEW_REQUIRED
                    && pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M
                    && bp.isRuntime()) {
                return;
            }

            uid = UserHandle.getUid(userId, pkg.applicationInfo.uid);
            sb = (SettingBase) pkg.mExtras;
            if (sb == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }

            final PermissionsState permissionsState = sb.getPermissionsState();

            final int flags = permissionsState.getPermissionFlags(name, userId);
            if ((flags & PackageManager.FLAG_PERMISSION_SYSTEM_FIXED) != 0) {
                throw new SecurityException("Cannot grant system fixed permission "
                        + name + " for package " + packageName);
            }

            if (bp.isDevelopment()) {
                // Development permissions must be handled specially, since they are not
                // normal runtime permissions.  For now they apply to all users.
                if (permissionsState.grantInstallPermission(bp) !=
                        PermissionsState.PERMISSION_OPERATION_FAILURE) {
                    scheduleWriteSettingsLocked();
                }
                return;
            }

            if (pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                Slog.w(TAG, "Cannot grant runtime permission to a legacy app");
                return;
            }

            final int result = permissionsState.grantRuntimePermission(bp, userId);
            switch (result) {
                case PermissionsState.PERMISSION_OPERATION_FAILURE: {
                    return;
                }

                case PermissionsState.PERMISSION_OPERATION_SUCCESS_GIDS_CHANGED: {
                    final int appId = UserHandle.getAppId(pkg.applicationInfo.uid);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            killUid(appId, userId, KILL_APP_REASON_GIDS_CHANGED);
                        }
                    });
                }
                break;
            }

            mOnPermissionChangeListeners.onPermissionsChanged(uid);

            // Not critical if that is lost - app has to request again.
            mSettings.writeRuntimePermissionsForUserLPr(userId, false);
        }

        // Only need to do this if user is initialized. Otherwise it's a new user
        // and there are no processes running as the user yet and there's no need
        // to make an expensive call to remount processes for the changed permissions.
        if (READ_EXTERNAL_STORAGE.equals(name)
                || WRITE_EXTERNAL_STORAGE.equals(name)) {
            final long token = Binder.clearCallingIdentity();
            try {
                if (sUserManager.isInitialized(userId)) {
                    MountServiceInternal mountServiceInternal = LocalServices.getService(
                            MountServiceInternal.class);
                    mountServiceInternal.onExternalStoragePolicyChanged(uid, packageName);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    @Override
    public void revokeRuntimePermission(String packageName, String name, int userId) {
        if (!sUserManager.exists(userId)) {
            Log.e(TAG, "No such user:" + userId);
            return;
        }

        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS,
                "revokeRuntimePermission");

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, true /* checkShell */,
                "revokeRuntimePermission");

        final int appId;

        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }

            final BasePermission bp = mSettings.mPermissions.get(name);
            if (bp == null) {
                throw new IllegalArgumentException("Unknown permission: " + name);
            }

            enforceDeclaredAsUsedAndRuntimeOrDevelopmentPermission(pkg, bp);

            // If a permission review is required for legacy apps we represent
            // their permissions as always granted runtime ones since we need
            // to keep the review required permission flag per user while an
            // install permission's state is shared across all users.
            if (Build.PERMISSIONS_REVIEW_REQUIRED
                    && pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M
                    && bp.isRuntime()) {
                return;
            }

            SettingBase sb = (SettingBase) pkg.mExtras;
            if (sb == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }

            final PermissionsState permissionsState = sb.getPermissionsState();

            final int flags = permissionsState.getPermissionFlags(name, userId);
            if ((flags & PackageManager.FLAG_PERMISSION_SYSTEM_FIXED) != 0) {
                throw new SecurityException("Cannot revoke system fixed permission "
                        + name + " for package " + packageName);
            }

            if (bp.isDevelopment()) {
                // Development permissions must be handled specially, since they are not
                // normal runtime permissions.  For now they apply to all users.
                if (permissionsState.revokeInstallPermission(bp) !=
                        PermissionsState.PERMISSION_OPERATION_FAILURE) {
                    scheduleWriteSettingsLocked();
                }
                return;
            }

            if (permissionsState.revokeRuntimePermission(bp, userId) ==
                    PermissionsState.PERMISSION_OPERATION_FAILURE) {
                return;
            }

            mOnPermissionChangeListeners.onPermissionsChanged(pkg.applicationInfo.uid);

            // Critical, after this call app should never have the permission.
            mSettings.writeRuntimePermissionsForUserLPr(userId, true);

            appId = UserHandle.getAppId(pkg.applicationInfo.uid);
        }

        killUid(appId, userId, KILL_APP_REASON_PERMISSIONS_REVOKED);
    }

    @Override
    public void resetRuntimePermissions() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS,
                "revokeRuntimePermission");

        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.SYSTEM_UID && callingUid != 0) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "resetRuntimePermissions");
        }

        synchronized (mPackages) {
            updatePermissionsLPw(null, null, UPDATE_PERMISSIONS_ALL);
            for (int userId : UserManagerService.getInstance().getUserIds()) {
                final int packageCount = mPackages.size();
                for (int i = 0; i < packageCount; i++) {
                    PackageParser.Package pkg = mPackages.valueAt(i);
                    if (!(pkg.mExtras instanceof PackageSetting)) {
                        continue;
                    }
                    PackageSetting ps = (PackageSetting) pkg.mExtras;
                    resetUserChangesToRuntimePermissionsAndFlagsLPw(ps, userId);
                }
            }
        }
    }

    @Override
    public int getPermissionFlags(String name, String packageName, int userId) {
        if (!sUserManager.exists(userId)) {
            return 0;
        }

        enforceGrantRevokeRuntimePermissionPermissions("getPermissionFlags");

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "getPermissionFlags");

        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                return 0;
            }

            final BasePermission bp = mSettings.mPermissions.get(name);
            if (bp == null) {
                return 0;
            }

            SettingBase sb = (SettingBase) pkg.mExtras;
            if (sb == null) {
                return 0;
            }

            PermissionsState permissionsState = sb.getPermissionsState();
            return permissionsState.getPermissionFlags(name, userId);
        }
    }

    @Override
    public void updatePermissionFlags(String name, String packageName, int flagMask,
            int flagValues, int userId) {
        if (!sUserManager.exists(userId)) {
            return;
        }

        enforceGrantRevokeRuntimePermissionPermissions("updatePermissionFlags");

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, true /* checkShell */,
                "updatePermissionFlags");

        // Only the system can change these flags and nothing else.
        if (getCallingUid() != Process.SYSTEM_UID) {
            flagMask &= ~PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
            flagValues &= ~PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
            flagMask &= ~PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
            flagValues &= ~PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
            flagValues &= ~PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
        }

        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }

            final BasePermission bp = mSettings.mPermissions.get(name);
            if (bp == null) {
                throw new IllegalArgumentException("Unknown permission: " + name);
            }

            SettingBase sb = (SettingBase) pkg.mExtras;
            if (sb == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }

            PermissionsState permissionsState = sb.getPermissionsState();

            boolean hadState = permissionsState.getRuntimePermissionState(name, userId) != null;

            if (permissionsState.updatePermissionFlags(bp, userId, flagMask, flagValues)) {
                // Install and runtime permissions are stored in different places,
                // so figure out what permission changed and persist the change.
                if (permissionsState.getInstallPermissionState(name) != null) {
                    scheduleWriteSettingsLocked();
                } else if (permissionsState.getRuntimePermissionState(name, userId) != null
                        || hadState) {
                    mSettings.writeRuntimePermissionsForUserLPr(userId, false);
                }
            }
        }
    }

    /**
     * Update the permission flags for all packages and runtime permissions of a user in order
     * to allow device or profile owner to remove POLICY_FIXED.
     */
    @Override
    public void updatePermissionFlagsForAllApps(int flagMask, int flagValues, int userId) {
        if (!sUserManager.exists(userId)) {
            return;
        }

        enforceGrantRevokeRuntimePermissionPermissions("updatePermissionFlagsForAllApps");

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, true /* checkShell */,
                "updatePermissionFlagsForAllApps");

        // Only the system can change system fixed flags.
        if (getCallingUid() != Process.SYSTEM_UID) {
            flagMask &= ~PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
            flagValues &= ~PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        }

        synchronized (mPackages) {
            boolean changed = false;
            final int packageCount = mPackages.size();
            for (int pkgIndex = 0; pkgIndex < packageCount; pkgIndex++) {
                final PackageParser.Package pkg = mPackages.valueAt(pkgIndex);
                SettingBase sb = (SettingBase) pkg.mExtras;
                if (sb == null) {
                    continue;
                }
                PermissionsState permissionsState = sb.getPermissionsState();
                changed |= permissionsState.updatePermissionFlagsForAllPermissions(
                        userId, flagMask, flagValues);
            }
            if (changed) {
                mSettings.writeRuntimePermissionsForUserLPr(userId, false);
            }
        }
    }

    private void enforceGrantRevokeRuntimePermissionPermissions(String message) {
        if (mContext.checkCallingOrSelfPermission(Manifest.permission.GRANT_RUNTIME_PERMISSIONS)
                != PackageManager.PERMISSION_GRANTED
            && mContext.checkCallingOrSelfPermission(Manifest.permission.REVOKE_RUNTIME_PERMISSIONS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(message + " requires "
                    + Manifest.permission.GRANT_RUNTIME_PERMISSIONS + " or "
                    + Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);
        }
    }

    @Override
    public boolean shouldShowRequestPermissionRationale(String permissionName,
            String packageName, int userId) {
        if (UserHandle.getCallingUserId() != userId) {
            mContext.enforceCallingPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "canShowRequestPermissionRationale for user " + userId);
        }

        final int uid = getPackageUid(packageName, MATCH_DEBUG_TRIAGED_MISSING, userId);
        if (UserHandle.getAppId(getCallingUid()) != UserHandle.getAppId(uid)) {
            return false;
        }

        if (checkPermission(permissionName, packageName, userId)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        final int flags;

        final long identity = Binder.clearCallingIdentity();
        try {
            flags = getPermissionFlags(permissionName,
                    packageName, userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }

        final int fixedFlags = PackageManager.FLAG_PERMISSION_SYSTEM_FIXED
                | PackageManager.FLAG_PERMISSION_POLICY_FIXED
                | PackageManager.FLAG_PERMISSION_USER_FIXED;

        if ((flags & fixedFlags) != 0) {
            return false;
        }

        return (flags & PackageManager.FLAG_PERMISSION_USER_SET) != 0;
    }

    @Override
    public void addOnPermissionsChangeListener(IOnPermissionsChangeListener listener) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS,
                "addOnPermissionsChangeListener");

        synchronized (mPackages) {
            mOnPermissionChangeListeners.addListenerLocked(listener);
        }
    }

    @Override
    public void removeOnPermissionsChangeListener(IOnPermissionsChangeListener listener) {
        synchronized (mPackages) {
            mOnPermissionChangeListeners.removeListenerLocked(listener);
        }
    }

    @Override
    public boolean isProtectedBroadcast(String actionName) {
        synchronized (mPackages) {
            if (mProtectedBroadcasts.containsKey(actionName)) {
                return true;
            } else if (actionName != null) {
                // TODO: remove these terrible hacks
                if (actionName.startsWith("android.net.netmon.lingerExpired")
                        || actionName.startsWith("com.android.server.sip.SipWakeupTimer")
                        || actionName.startsWith("com.android.internal.telephony.data-reconnect")
                        || actionName.startsWith("android.net.netmon.launchCaptivePortalApp")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isProtectedBroadcastAllowed(String actionName, int callingUid) {
        synchronized (mPackages) {
            if (mProtectedBroadcasts.containsKey(actionName)) {
               final int result = checkUidPermission(mProtectedBroadcasts.get(actionName),
                        callingUid);
                return result == PackageManager.PERMISSION_GRANTED;
            }
        }
        return false;
    }

    @Override
    public int checkSignatures(String pkg1, String pkg2) {
        synchronized (mPackages) {
            final PackageParser.Package p1 = mPackages.get(pkg1);
            final PackageParser.Package p2 = mPackages.get(pkg2);
            if (p1 == null || p1.mExtras == null
                    || p2 == null || p2.mExtras == null) {
                return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
            }
            return compareSignatures(p1.mSignatures, p2.mSignatures);
        }
    }

    @Override
    public int checkUidSignatures(int uid1, int uid2) {
        // Map to base uids.
        uid1 = UserHandle.getAppId(uid1);
        uid2 = UserHandle.getAppId(uid2);
        // reader
        synchronized (mPackages) {
            Signature[] s1;
            Signature[] s2;
            Object obj = mSettings.getUserIdLPr(uid1);
            if (obj != null) {
                if (obj instanceof SharedUserSetting) {
                    s1 = ((SharedUserSetting)obj).signatures.mSignatures;
                } else if (obj instanceof PackageSetting) {
                    s1 = ((PackageSetting)obj).signatures.mSignatures;
                } else {
                    return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
                }
            } else {
                return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
            }
            obj = mSettings.getUserIdLPr(uid2);
            if (obj != null) {
                if (obj instanceof SharedUserSetting) {
                    s2 = ((SharedUserSetting)obj).signatures.mSignatures;
                } else if (obj instanceof PackageSetting) {
                    s2 = ((PackageSetting)obj).signatures.mSignatures;
                } else {
                    return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
                }
            } else {
                return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
            }
            return compareSignatures(s1, s2);
        }
    }

    /**
     * This method should typically only be used when granting or revoking
     * permissions, since the app may immediately restart after this call.
     * <p>
     * If you're doing surgery on app code/data, use {@link PackageFreezer} to
     * guard your work against the app being relaunched.
     */
    private void killUid(int appId, int userId, String reason) {
        final long identity = Binder.clearCallingIdentity();
        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            if (am != null) {
                try {
                    am.killUid(appId, userId, reason);
                } catch (RemoteException e) {
                    /* ignore - same process */
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Compares two sets of signatures. Returns:
     * <br />
     * {@link PackageManager#SIGNATURE_NEITHER_SIGNED}: if both signature sets are null,
     * <br />
     * {@link PackageManager#SIGNATURE_FIRST_NOT_SIGNED}: if the first signature set is null,
     * <br />
     * {@link PackageManager#SIGNATURE_SECOND_NOT_SIGNED}: if the second signature set is null,
     * <br />
     * {@link PackageManager#SIGNATURE_MATCH}: if the two signature sets are identical,
     * <br />
     * {@link PackageManager#SIGNATURE_NO_MATCH}: if the two signature sets differ.
     */
    static int compareSignatures(Signature[] s1, Signature[] s2) {
        if (s1 == null) {
            return s2 == null
                    ? PackageManager.SIGNATURE_NEITHER_SIGNED
                    : PackageManager.SIGNATURE_FIRST_NOT_SIGNED;
        }

        if (s2 == null) {
            return PackageManager.SIGNATURE_SECOND_NOT_SIGNED;
        }

        if (s1.length != s2.length) {
            return PackageManager.SIGNATURE_NO_MATCH;
        }

        // Since both signature sets are of size 1, we can compare without HashSets.
        if (s1.length == 1) {
            return s1[0].equals(s2[0]) ?
                    PackageManager.SIGNATURE_MATCH :
                    PackageManager.SIGNATURE_NO_MATCH;
        }

        ArraySet<Signature> set1 = new ArraySet<Signature>();
        for (Signature sig : s1) {
            set1.add(sig);
        }
        ArraySet<Signature> set2 = new ArraySet<Signature>();
        for (Signature sig : s2) {
            set2.add(sig);
        }
        // Make sure s2 contains all signatures in s1.
        if (set1.equals(set2)) {
            return PackageManager.SIGNATURE_MATCH;
        }
        return PackageManager.SIGNATURE_NO_MATCH;
    }

    /**
     * If the database version for this type of package (internal storage or
     * external storage) is less than the version where package signatures
     * were updated, return true.
     */
    private boolean isCompatSignatureUpdateNeeded(PackageParser.Package scannedPkg) {
        final VersionInfo ver = getSettingsVersionForPackage(scannedPkg);
        return ver.databaseVersion < DatabaseVersion.SIGNATURE_END_ENTITY;
    }

    /**
     * Used for backward compatibility to make sure any packages with
     * certificate chains get upgraded to the new style. {@code existingSigs}
     * will be in the old format (since they were stored on disk from before the
     * system upgrade) and {@code scannedSigs} will be in the newer format.
     */
    private int compareSignaturesCompat(PackageSignatures existingSigs,
            PackageParser.Package scannedPkg) {
        if (!isCompatSignatureUpdateNeeded(scannedPkg)) {
            return PackageManager.SIGNATURE_NO_MATCH;
        }

        ArraySet<Signature> existingSet = new ArraySet<Signature>();
        for (Signature sig : existingSigs.mSignatures) {
            existingSet.add(sig);
        }
        ArraySet<Signature> scannedCompatSet = new ArraySet<Signature>();
        for (Signature sig : scannedPkg.mSignatures) {
            try {
                Signature[] chainSignatures = sig.getChainSignatures();
                for (Signature chainSig : chainSignatures) {
                    scannedCompatSet.add(chainSig);
                }
            } catch (CertificateEncodingException e) {
                scannedCompatSet.add(sig);
            }
        }
        /*
         * Make sure the expanded scanned set contains all signatures in the
         * existing one.
         */
        if (scannedCompatSet.equals(existingSet)) {
            // Migrate the old signatures to the new scheme.
            existingSigs.assignSignatures(scannedPkg.mSignatures);
            // The new KeySets will be re-added later in the scanning process.
            synchronized (mPackages) {
                mSettings.mKeySetManagerService.removeAppKeySetDataLPw(scannedPkg.packageName);
            }
            return PackageManager.SIGNATURE_MATCH;
        }
        return PackageManager.SIGNATURE_NO_MATCH;
    }

    private boolean isRecoverSignatureUpdateNeeded(PackageParser.Package scannedPkg) {
        final VersionInfo ver = getSettingsVersionForPackage(scannedPkg);
        return ver.databaseVersion < DatabaseVersion.SIGNATURE_MALFORMED_RECOVER;
    }

    private int compareSignaturesRecover(PackageSignatures existingSigs,
            PackageParser.Package scannedPkg) {
        if (!isRecoverSignatureUpdateNeeded(scannedPkg)) {
            return PackageManager.SIGNATURE_NO_MATCH;
        }

        String msg = null;
        try {
            if (Signature.areEffectiveMatch(existingSigs.mSignatures, scannedPkg.mSignatures)) {
                logCriticalInfo(Log.INFO, "Recovered effectively matching certificates for "
                        + scannedPkg.packageName);
                return PackageManager.SIGNATURE_MATCH;
            }
        } catch (CertificateException e) {
            msg = e.getMessage();
        }

        logCriticalInfo(Log.INFO,
                "Failed to recover certificates for " + scannedPkg.packageName + ": " + msg);
        return PackageManager.SIGNATURE_NO_MATCH;
    }

    @Override
    public List<String> getAllPackages() {
        synchronized (mPackages) {
            return new ArrayList<String>(mPackages.keySet());
        }
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        uid = UserHandle.getAppId(uid);
        // reader
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(uid);
            if (obj instanceof SharedUserSetting) {
                final SharedUserSetting sus = (SharedUserSetting) obj;
                final int N = sus.packages.size();
                final String[] res = new String[N];
                for (int i = 0; i < N; i++) {
                    res[i] = sus.packages.valueAt(i).name;
                }
                return res;
            } else if (obj instanceof PackageSetting) {
                final PackageSetting ps = (PackageSetting) obj;
                return new String[] { ps.name };
            }
        }
        return null;
    }

    @Override
    public String getNameForUid(int uid) {
        // reader
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(UserHandle.getAppId(uid));
            if (obj instanceof SharedUserSetting) {
                final SharedUserSetting sus = (SharedUserSetting) obj;
                return sus.name + ":" + sus.userId;
            } else if (obj instanceof PackageSetting) {
                final PackageSetting ps = (PackageSetting) obj;
                return ps.name;
            }
        }
        return null;
    }

    @Override
    public int getUidForSharedUser(String sharedUserName) {
        if(sharedUserName == null) {
            return -1;
        }
        // reader
        synchronized (mPackages) {
            final SharedUserSetting suid = mSettings.getSharedUserLPw(sharedUserName, 0, 0, false);
            if (suid == null) {
                return -1;
            }
            return suid.userId;
        }
    }

    @Override
    public int getFlagsForUid(int uid) {
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(UserHandle.getAppId(uid));
            if (obj instanceof SharedUserSetting) {
                final SharedUserSetting sus = (SharedUserSetting) obj;
                return sus.pkgFlags;
            } else if (obj instanceof PackageSetting) {
                final PackageSetting ps = (PackageSetting) obj;
                return ps.pkgFlags;
            }
        }
        return 0;
    }

    @Override
    public int getPrivateFlagsForUid(int uid) {
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(UserHandle.getAppId(uid));
            if (obj instanceof SharedUserSetting) {
                final SharedUserSetting sus = (SharedUserSetting) obj;
                return sus.pkgPrivateFlags;
            } else if (obj instanceof PackageSetting) {
                final PackageSetting ps = (PackageSetting) obj;
                return ps.pkgPrivateFlags;
            }
        }
        return 0;
    }

    @Override
    public boolean isUidPrivileged(int uid) {
        uid = UserHandle.getAppId(uid);
        // reader
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(uid);
            if (obj instanceof SharedUserSetting) {
                final SharedUserSetting sus = (SharedUserSetting) obj;
                final Iterator<PackageSetting> it = sus.packages.iterator();
                while (it.hasNext()) {
                    if (it.next().isPrivileged()) {
                        return true;
                    }
                }
            } else if (obj instanceof PackageSetting) {
                final PackageSetting ps = (PackageSetting) obj;
                return ps.isPrivileged();
            }
        }
        return false;
    }

    @Override
    public String[] getAppOpPermissionPackages(String permissionName) {
        synchronized (mPackages) {
            ArraySet<String> pkgs = mAppOpPermissionPackages.get(permissionName);
            if (pkgs == null) {
                return null;
            }
            return pkgs.toArray(new String[pkgs.size()]);
        }
    }

    @Override
    public ResolveInfo resolveIntent(Intent intent, String resolvedType,
            int flags, int userId) {
        try {
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "resolveIntent");

            if (!sUserManager.exists(userId)) return null;
            flags = updateFlagsForResolve(flags, userId, intent);
            enforceCrossUserPermission(Binder.getCallingUid(), userId,
                    false /*requireFullPermission*/, false /*checkShell*/, "resolve intent");

            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "queryIntentActivities");
            final List<ResolveInfo> query = queryIntentActivitiesInternal(intent, resolvedType,
                    flags, userId);
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);

            final ResolveInfo bestChoice =
                    chooseBestActivity(intent, resolvedType, flags, query, userId);

            if (isEphemeralAllowed(intent, query, userId)) {
                Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "resolveEphemeral");
                final EphemeralResolveInfo ai =
                        getEphemeralResolveInfo(intent, resolvedType, userId);
                if (ai != null) {
                    if (DEBUG_EPHEMERAL) {
                        Slog.v(TAG, "Returning an EphemeralResolveInfo");
                    }
                    bestChoice.ephemeralInstaller = mEphemeralInstallerInfo;
                    bestChoice.ephemeralResolveInfo = ai;
                }
                Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
            }
            return bestChoice;
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    @Override
    public void setLastChosenActivity(Intent intent, String resolvedType, int flags,
            IntentFilter filter, int match, ComponentName activity) {
        final int userId = UserHandle.getCallingUserId();
        if (DEBUG_PREFERRED) {
            Log.v(TAG, "setLastChosenActivity intent=" + intent
                + " resolvedType=" + resolvedType
                + " flags=" + flags
                + " filter=" + filter
                + " match=" + match
                + " activity=" + activity);
            filter.dump(new PrintStreamPrinter(System.out), "    ");
        }
        intent.setComponent(null);
        final List<ResolveInfo> query = queryIntentActivitiesInternal(intent, resolvedType, flags,
                userId);
        // Find any earlier preferred or last chosen entries and nuke them
        findPreferredActivity(intent, resolvedType,
                flags, query, 0, false, true, false, userId);
        // Add the new activity as the last chosen for this filter
        addPreferredActivityInternal(filter, match, null, activity, false, userId,
                "Setting last chosen");
    }

    @Override
    public ResolveInfo getLastChosenActivity(Intent intent, String resolvedType, int flags) {
        final int userId = UserHandle.getCallingUserId();
        if (DEBUG_PREFERRED) Log.v(TAG, "Querying last chosen activity for " + intent);
        final List<ResolveInfo> query = queryIntentActivitiesInternal(intent, resolvedType, flags,
                userId);
        return findPreferredActivity(intent, resolvedType, flags, query, 0,
                false, false, false, userId);
    }


    private boolean isEphemeralAllowed(
            Intent intent, List<ResolveInfo> resolvedActivites, int userId) {
        // Short circuit and return early if possible.
        if (DISABLE_EPHEMERAL_APPS) {
            return false;
        }
        final int callingUser = UserHandle.getCallingUserId();
        if (callingUser != UserHandle.USER_SYSTEM) {
            return false;
        }
        if (mEphemeralResolverConnection == null) {
            return false;
        }
        if (intent.getComponent() != null) {
            return false;
        }
        if (intent.getPackage() != null) {
            return false;
        }
        final boolean isWebUri = hasWebURI(intent);
        if (!isWebUri) {
            return false;
        }
        // Deny ephemeral apps if the user chose _ALWAYS or _ALWAYS_ASK for intent resolution.
        synchronized (mPackages) {
            final int count = resolvedActivites.size();
            for (int n = 0; n < count; n++) {
                ResolveInfo info = resolvedActivites.get(n);
                String packageName = info.activityInfo.packageName;
                PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps != null) {
                    // Try to get the status from User settings first
                    long packedStatus = getDomainVerificationStatusLPr(ps, userId);
                    int status = (int) (packedStatus >> 32);
                    if (status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS
                            || status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK) {
                        if (DEBUG_EPHEMERAL) {
                            Slog.v(TAG, "DENY ephemeral apps;"
                                + " pkg: " + packageName + ", status: " + status);
                        }
                        return false;
                    }
                }
            }
        }
        // We've exhausted all ways to deny ephemeral application; let the system look for them.
        return true;
    }

    private EphemeralResolveInfo getEphemeralResolveInfo(Intent intent, String resolvedType,
            int userId) {
        final int ephemeralPrefixMask = Global.getInt(mContext.getContentResolver(),
                Global.EPHEMERAL_HASH_PREFIX_MASK, DEFAULT_EPHEMERAL_HASH_PREFIX_MASK);
        final int ephemeralPrefixCount = Global.getInt(mContext.getContentResolver(),
                Global.EPHEMERAL_HASH_PREFIX_COUNT, DEFAULT_EPHEMERAL_HASH_PREFIX_COUNT);
        final EphemeralDigest digest = new EphemeralDigest(intent.getData(), ephemeralPrefixMask,
                ephemeralPrefixCount);
        final int[] shaPrefix = digest.getDigestPrefix();
        final byte[][] digestBytes = digest.getDigestBytes();
        final List<EphemeralResolveInfo> ephemeralResolveInfoList =
                mEphemeralResolverConnection.getEphemeralResolveInfoList(
                        shaPrefix, ephemeralPrefixMask);
        if (ephemeralResolveInfoList == null || ephemeralResolveInfoList.size() == 0) {
            // No hash prefix match; there are no ephemeral apps for this domain.
            return null;
        }

        // Go in reverse order so we match the narrowest scope first.
        for (int i = shaPrefix.length - 1; i >= 0 ; --i) {
            for (EphemeralResolveInfo ephemeralApplication : ephemeralResolveInfoList) {
                if (!Arrays.equals(digestBytes[i], ephemeralApplication.getDigestBytes())) {
                    continue;
                }
                final List<IntentFilter> filters = ephemeralApplication.getFilters();
                // No filters; this should never happen.
                if (filters.isEmpty()) {
                    continue;
                }
                // We have a domain match; resolve the filters to see if anything matches.
                final EphemeralIntentResolver ephemeralResolver = new EphemeralIntentResolver();
                for (int j = filters.size() - 1; j >= 0; --j) {
                    final EphemeralResolveIntentInfo intentInfo =
                            new EphemeralResolveIntentInfo(filters.get(j), ephemeralApplication);
                    ephemeralResolver.addFilter(intentInfo);
                }
                List<EphemeralResolveInfo> matchedResolveInfoList = ephemeralResolver.queryIntent(
                        intent, resolvedType, false /*defaultOnly*/, userId);
                if (!matchedResolveInfoList.isEmpty()) {
                    return matchedResolveInfoList.get(0);
                }
            }
        }
        // Hash or filter mis-match; no ephemeral apps for this domain.
        return null;
    }

    private ResolveInfo chooseBestActivity(Intent intent, String resolvedType,
            int flags, List<ResolveInfo> query, int userId) {
        if (query != null) {
            final int N = query.size();
            if (N == 1) {
                return query.get(0);
            } else if (N > 1) {
                final boolean debug = ((intent.getFlags() & Intent.FLAG_DEBUG_LOG_RESOLUTION) != 0);
                // If there is more than one activity with the same priority,
                // then let the user decide between them.
                ResolveInfo r0 = query.get(0);
                ResolveInfo r1 = query.get(1);
                if (DEBUG_INTENT_MATCHING || debug) {
                    Slog.v(TAG, r0.activityInfo.name + "=" + r0.priority + " vs "
                            + r1.activityInfo.name + "=" + r1.priority);
                }
                // If the first activity has a higher priority, or a different
                // default, then it is always desirable to pick it.
                if (r0.priority != r1.priority
                        || r0.preferredOrder != r1.preferredOrder
                        || r0.isDefault != r1.isDefault) {
                    return query.get(0);
                }
                // If we have saved a preference for a preferred activity for
                // this Intent, use that.
                ResolveInfo ri = findPreferredActivity(intent, resolvedType,
                        flags, query, r0.priority, true, false, debug, userId);
                if (ri != null) {
                    return ri;
                }
                ri = new ResolveInfo(mResolveInfo);
                ri.activityInfo = new ActivityInfo(ri.activityInfo);
                ri.activityInfo.labelRes = ResolverActivity.getLabelRes(intent.getAction());
                // If all of the options come from the same package, show the application's
                // label and icon instead of the generic resolver's.
                // Some calls like Intent.resolveActivityInfo query the ResolveInfo from here
                // and then throw away the ResolveInfo itself, meaning that the caller loses
                // the resolvePackageName. Therefore the activityInfo.labelRes above provides
                // a fallback for this case; we only set the target package's resources on
                // the ResolveInfo, not the ActivityInfo.
                final String intentPackage = intent.getPackage();
                if (!TextUtils.isEmpty(intentPackage) && allHavePackage(query, intentPackage)) {
                    final ApplicationInfo appi = query.get(0).activityInfo.applicationInfo;
                    ri.resolvePackageName = intentPackage;
                    if (userNeedsBadging(userId)) {
                        ri.noResourceId = true;
                    } else {
                        ri.icon = appi.icon;
                    }
                    ri.iconResourceId = appi.icon;
                    ri.labelRes = appi.labelRes;
                }
                ri.activityInfo.applicationInfo = new ApplicationInfo(
                        ri.activityInfo.applicationInfo);
                if (userId != 0) {
                    ri.activityInfo.applicationInfo.uid = UserHandle.getUid(userId,
                            UserHandle.getAppId(ri.activityInfo.applicationInfo.uid));
                }
                // Make sure that the resolver is displayable in car mode
                if (ri.activityInfo.metaData == null) ri.activityInfo.metaData = new Bundle();
                ri.activityInfo.metaData.putBoolean(Intent.METADATA_DOCK_HOME, true);
                return ri;
            }
        }
        return null;
    }

    /**
     * Return true if the given list is not empty and all of its contents have
     * an activityInfo with the given package name.
     */
    private boolean allHavePackage(List<ResolveInfo> list, String packageName) {
        if (ArrayUtils.isEmpty(list)) {
            return false;
        }
        for (int i = 0, N = list.size(); i < N; i++) {
            final ResolveInfo ri = list.get(i);
            final ActivityInfo ai = ri != null ? ri.activityInfo : null;
            if (ai == null || !packageName.equals(ai.packageName)) {
                return false;
            }
        }
        return true;
    }

    private ResolveInfo findPersistentPreferredActivityLP(Intent intent, String resolvedType,
            int flags, List<ResolveInfo> query, boolean debug, int userId) {
        final int N = query.size();
        PersistentPreferredIntentResolver ppir = mSettings.mPersistentPreferredActivities
                .get(userId);
        // Get the list of persistent preferred activities that handle the intent
        if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Looking for presistent preferred activities...");
        List<PersistentPreferredActivity> pprefs = ppir != null
                ? ppir.queryIntent(intent, resolvedType,
                        (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId)
                : null;
        if (pprefs != null && pprefs.size() > 0) {
            final int M = pprefs.size();
            for (int i=0; i<M; i++) {
                final PersistentPreferredActivity ppa = pprefs.get(i);
                if (DEBUG_PREFERRED || debug) {
                    Slog.v(TAG, "Checking PersistentPreferredActivity ds="
                            + (ppa.countDataSchemes() > 0 ? ppa.getDataScheme(0) : "<none>")
                            + "\n  component=" + ppa.mComponent);
                    ppa.dump(new LogPrinter(Log.VERBOSE, TAG, Log.LOG_ID_SYSTEM), "  ");
                }
                final ActivityInfo ai = getActivityInfo(ppa.mComponent,
                        flags | MATCH_DISABLED_COMPONENTS, userId);
                if (DEBUG_PREFERRED || debug) {
                    Slog.v(TAG, "Found persistent preferred activity:");
                    if (ai != null) {
                        ai.dump(new LogPrinter(Log.VERBOSE, TAG, Log.LOG_ID_SYSTEM), "  ");
                    } else {
                        Slog.v(TAG, "  null");
                    }
                }
                if (ai == null) {
                    // This previously registered persistent preferred activity
                    // component is no longer known. Ignore it and do NOT remove it.
                    continue;
                }
                for (int j=0; j<N; j++) {
                    final ResolveInfo ri = query.get(j);
                    if (!ri.activityInfo.applicationInfo.packageName
                            .equals(ai.applicationInfo.packageName)) {
                        continue;
                    }
                    if (!ri.activityInfo.name.equals(ai.name)) {
                        continue;
                    }
                    //  Found a persistent preference that can handle the intent.
                    if (DEBUG_PREFERRED || debug) {
                        Slog.v(TAG, "Returning persistent preferred activity: " +
                                ri.activityInfo.packageName + "/" + ri.activityInfo.name);
                    }
                    return ri;
                }
            }
        }
        return null;
    }

    // TODO: handle preferred activities missing while user has amnesia
    ResolveInfo findPreferredActivity(Intent intent, String resolvedType, int flags,
            List<ResolveInfo> query, int priority, boolean always,
            boolean removeMatches, boolean debug, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForResolve(flags, userId, intent);
        // writer
        synchronized (mPackages) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
            }
            if (DEBUG_PREFERRED) intent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);

            // Try to find a matching persistent preferred activity.
            ResolveInfo pri = findPersistentPreferredActivityLP(intent, resolvedType, flags, query,
                    debug, userId);

            // If a persistent preferred activity matched, use it.
            if (pri != null) {
                return pri;
            }

            PreferredIntentResolver pir = mSettings.mPreferredActivities.get(userId);
            // Get the list of preferred activities that handle the intent
            if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Looking for preferred activities...");
            List<PreferredActivity> prefs = pir != null
                    ? pir.queryIntent(intent, resolvedType,
                            (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId)
                    : null;
            if (prefs != null && prefs.size() > 0) {
                boolean changed = false;
                try {
                    // First figure out how good the original match set is.
                    // We will only allow preferred activities that came
                    // from the same match quality.
                    int match = 0;

                    if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Figuring out best match...");

                    final int N = query.size();
                    for (int j=0; j<N; j++) {
                        final ResolveInfo ri = query.get(j);
                        if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Match for " + ri.activityInfo
                                + ": 0x" + Integer.toHexString(match));
                        if (ri.match > match) {
                            match = ri.match;
                        }
                    }

                    if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Best match: 0x"
                            + Integer.toHexString(match));

                    match &= IntentFilter.MATCH_CATEGORY_MASK;
                    final int M = prefs.size();
                    for (int i=0; i<M; i++) {
                        final PreferredActivity pa = prefs.get(i);
                        if (DEBUG_PREFERRED || debug) {
                            Slog.v(TAG, "Checking PreferredActivity ds="
                                    + (pa.countDataSchemes() > 0 ? pa.getDataScheme(0) : "<none>")
                                    + "\n  component=" + pa.mPref.mComponent);
                            pa.dump(new LogPrinter(Log.VERBOSE, TAG, Log.LOG_ID_SYSTEM), "  ");
                        }
                        if (pa.mPref.mMatch != match) {
                            if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Skipping bad match "
                                    + Integer.toHexString(pa.mPref.mMatch));
                            continue;
                        }
                        // If it's not an "always" type preferred activity and that's what we're
                        // looking for, skip it.
                        if (always && !pa.mPref.mAlways) {
                            if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Skipping mAlways=false entry");
                            continue;
                        }
                        final ActivityInfo ai = getActivityInfo(
                                pa.mPref.mComponent, flags | MATCH_DISABLED_COMPONENTS
                                        | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE,
                                userId);
                        if (DEBUG_PREFERRED || debug) {
                            Slog.v(TAG, "Found preferred activity:");
                            if (ai != null) {
                                ai.dump(new LogPrinter(Log.VERBOSE, TAG, Log.LOG_ID_SYSTEM), "  ");
                            } else {
                                Slog.v(TAG, "  null");
                            }
                        }
                        if (ai == null) {
                            // This previously registered preferred activity
                            // component is no longer known.  Most likely an update
                            // to the app was installed and in the new version this
                            // component no longer exists.  Clean it up by removing
                            // it from the preferred activities list, and skip it.
                            Slog.w(TAG, "Removing dangling preferred activity: "
                                    + pa.mPref.mComponent);
                            pir.removeFilter(pa);
                            changed = true;
                            continue;
                        }
                        for (int j=0; j<N; j++) {
                            final ResolveInfo ri = query.get(j);
                            if (!ri.activityInfo.applicationInfo.packageName
                                    .equals(ai.applicationInfo.packageName)) {
                                continue;
                            }
                            if (!ri.activityInfo.name.equals(ai.name)) {
                                continue;
                            }

                            if (removeMatches) {
                                pir.removeFilter(pa);
                                changed = true;
                                if (DEBUG_PREFERRED) {
                                    Slog.v(TAG, "Removing match " + pa.mPref.mComponent);
                                }
                                break;
                            }

                            // Okay we found a previously set preferred or last chosen app.
                            // If the result set is different from when this
                            // was created, we need to clear it and re-ask the
                            // user their preference, if we're looking for an "always" type entry.
                            if (always && !pa.mPref.sameSet(query)) {
                                Slog.i(TAG, "Result set changed, dropping preferred activity for "
                                        + intent + " type " + resolvedType);
                                if (DEBUG_PREFERRED) {
                                    Slog.v(TAG, "Removing preferred activity since set changed "
                                            + pa.mPref.mComponent);
                                }
                                pir.removeFilter(pa);
                                // Re-add the filter as a "last chosen" entry (!always)
                                PreferredActivity lastChosen = new PreferredActivity(
                                        pa, pa.mPref.mMatch, null, pa.mPref.mComponent, false);
                                pir.addFilter(lastChosen);
                                changed = true;
                                return null;
                            }

                            // Yay! Either the set matched or we're looking for the last chosen
                            if (DEBUG_PREFERRED || debug) Slog.v(TAG, "Returning preferred activity: "
                                    + ri.activityInfo.packageName + "/" + ri.activityInfo.name);
                            return ri;
                        }
                    }
                } finally {
                    if (changed) {
                        if (DEBUG_PREFERRED) {
                            Slog.v(TAG, "Preferred activity bookkeeping changed; writing restrictions");
                        }
                        scheduleWritePackageRestrictionsLocked(userId);
                    }
                }
            }
        }
        if (DEBUG_PREFERRED || debug) Slog.v(TAG, "No preferred activity to return");
        return null;
    }

    /*
     * Returns if intent can be forwarded from the sourceUserId to the targetUserId
     */
    @Override
    public boolean canForwardTo(Intent intent, String resolvedType, int sourceUserId,
            int targetUserId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, null);
        List<CrossProfileIntentFilter> matches =
                getMatchingCrossProfileIntentFilters(intent, resolvedType, sourceUserId);
        if (matches != null) {
            int size = matches.size();
            for (int i = 0; i < size; i++) {
                if (matches.get(i).getTargetUserId() == targetUserId) return true;
            }
        }
        if (hasWebURI(intent)) {
            // cross-profile app linking works only towards the parent.
            final UserInfo parent = getProfileParent(sourceUserId);
            synchronized(mPackages) {
                int flags = updateFlagsForResolve(0, parent.id, intent);
                CrossProfileDomainInfo xpDomainInfo = getCrossProfileDomainPreferredLpr(
                        intent, resolvedType, flags, sourceUserId, parent.id);
                return xpDomainInfo != null;
            }
        }
        return false;
    }

    private UserInfo getProfileParent(int userId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            return sUserManager.getProfileParent(userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private List<CrossProfileIntentFilter> getMatchingCrossProfileIntentFilters(Intent intent,
            String resolvedType, int userId) {
        CrossProfileIntentResolver resolver = mSettings.mCrossProfileIntentResolvers.get(userId);
        if (resolver != null) {
            return resolver.queryIntent(intent, resolvedType, false, userId);
        }
        return null;
    }

    @Override
    public @NonNull ParceledListSlice<ResolveInfo> queryIntentActivities(Intent intent,
            String resolvedType, int flags, int userId) {
        try {
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "queryIntentActivities");

            return new ParceledListSlice<>(
                    queryIntentActivitiesInternal(intent, resolvedType, flags, userId));
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    private @NonNull List<ResolveInfo> queryIntentActivitiesInternal(Intent intent,
            String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        flags = updateFlagsForResolve(flags, userId, intent);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */,
                "query intent activities");
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }

        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<ResolveInfo>(1);
            final ActivityInfo ai = getActivityInfo(comp, flags, userId);
            if (ai != null) {
                final ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
            }
            return list;
        }

        // reader
        synchronized (mPackages) {
            final String pkgName = intent.getPackage();
            if (pkgName == null) {
                List<CrossProfileIntentFilter> matchingFilters =
                        getMatchingCrossProfileIntentFilters(intent, resolvedType, userId);
                // Check for results that need to skip the current profile.
                ResolveInfo xpResolveInfo  = querySkipCurrentProfileIntents(matchingFilters, intent,
                        resolvedType, flags, userId);
                if (xpResolveInfo != null) {
                    List<ResolveInfo> result = new ArrayList<ResolveInfo>(1);
                    result.add(xpResolveInfo);
                    return filterIfNotSystemUser(result, userId);
                }

                // Check for results in the current profile.
                List<ResolveInfo> result = mActivities.queryIntent(
                        intent, resolvedType, flags, userId);
                result = filterIfNotSystemUser(result, userId);

                // Check for cross profile results.
                boolean hasNonNegativePriorityResult = hasNonNegativePriority(result);
                xpResolveInfo = queryCrossProfileIntents(
                        matchingFilters, intent, resolvedType, flags, userId,
                        hasNonNegativePriorityResult);
                if (xpResolveInfo != null && isUserEnabled(xpResolveInfo.targetUserId)) {
                    boolean isVisibleToUser = filterIfNotSystemUser(
                            Collections.singletonList(xpResolveInfo), userId).size() > 0;
                    if (isVisibleToUser) {
                        result.add(xpResolveInfo);
                        Collections.sort(result, mResolvePrioritySorter);
                    }
                }
                if (hasWebURI(intent)) {
                    CrossProfileDomainInfo xpDomainInfo = null;
                    final UserInfo parent = getProfileParent(userId);
                    if (parent != null) {
                        xpDomainInfo = getCrossProfileDomainPreferredLpr(intent, resolvedType,
                                flags, userId, parent.id);
                    }
                    if (xpDomainInfo != null) {
                        if (xpResolveInfo != null) {
                            // If we didn't remove it, the cross-profile ResolveInfo would be twice
                            // in the result.
                            result.remove(xpResolveInfo);
                        }
                        if (result.size() == 0) {
                            result.add(xpDomainInfo.resolveInfo);
                            return result;
                        }
                    } else if (result.size() <= 1) {
                        return result;
                    }
                    result = filterCandidatesWithDomainPreferredActivitiesLPr(intent, flags, result,
                            xpDomainInfo, userId);
                    Collections.sort(result, mResolvePrioritySorter);
                }
                return result;
            }
            final PackageParser.Package pkg = mPackages.get(pkgName);
            if (pkg != null) {
                return filterIfNotSystemUser(
                        mActivities.queryIntentForPackage(
                                intent, resolvedType, flags, pkg.activities, userId),
                        userId);
            }
            return new ArrayList<ResolveInfo>();
        }
    }

    private static class CrossProfileDomainInfo {
        /* ResolveInfo for IntentForwarderActivity to send the intent to the other profile */
        ResolveInfo resolveInfo;
        /* Best domain verification status of the activities found in the other profile */
        int bestDomainVerificationStatus;
    }

    private CrossProfileDomainInfo getCrossProfileDomainPreferredLpr(Intent intent,
            String resolvedType, int flags, int sourceUserId, int parentUserId) {
        if (!sUserManager.hasUserRestriction(UserManager.ALLOW_PARENT_PROFILE_APP_LINKING,
                sourceUserId)) {
            return null;
        }
        List<ResolveInfo> resultTargetUser = mActivities.queryIntent(intent,
                resolvedType, flags, parentUserId);

        if (resultTargetUser == null || resultTargetUser.isEmpty()) {
            return null;
        }
        CrossProfileDomainInfo result = null;
        int size = resultTargetUser.size();
        for (int i = 0; i < size; i++) {
            ResolveInfo riTargetUser = resultTargetUser.get(i);
            // Intent filter verification is only for filters that specify a host. So don't return
            // those that handle all web uris.
            if (riTargetUser.handleAllWebDataURI) {
                continue;
            }
            String packageName = riTargetUser.activityInfo.packageName;
            PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                continue;
            }
            long verificationState = getDomainVerificationStatusLPr(ps, parentUserId);
            int status = (int)(verificationState >> 32);
            if (result == null) {
                result = new CrossProfileDomainInfo();
                result.resolveInfo = createForwardingResolveInfoUnchecked(new IntentFilter(),
                        sourceUserId, parentUserId);
                result.bestDomainVerificationStatus = status;
            } else {
                result.bestDomainVerificationStatus = bestDomainVerificationStatus(status,
                        result.bestDomainVerificationStatus);
            }
        }
        // Don't consider matches with status NEVER across profiles.
        if (result != null && result.bestDomainVerificationStatus
                == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER) {
            return null;
        }
        return result;
    }

    /**
     * Verification statuses are ordered from the worse to the best, except for
     * INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER, which is the worse.
     */
    private int bestDomainVerificationStatus(int status1, int status2) {
        if (status1 == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER) {
            return status2;
        }
        if (status2 == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER) {
            return status1;
        }
        return (int) MathUtils.max(status1, status2);
    }

    private boolean isUserEnabled(int userId) {
        long callingId = Binder.clearCallingIdentity();
        try {
            UserInfo userInfo = sUserManager.getUserInfo(userId);
            return userInfo != null && userInfo.isEnabled();
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Filter out activities with systemUserOnly flag set, when current user is not System.
     *
     * @return filtered list
     */
    private List<ResolveInfo> filterIfNotSystemUser(List<ResolveInfo> resolveInfos, int userId) {
        if (userId == UserHandle.USER_SYSTEM) {
            return resolveInfos;
        }
        for (int i = resolveInfos.size() - 1; i >= 0; i--) {
            ResolveInfo info = resolveInfos.get(i);
            if ((info.activityInfo.flags & ActivityInfo.FLAG_SYSTEM_USER_ONLY) != 0) {
                resolveInfos.remove(i);
            }
        }
        return resolveInfos;
    }

    /**
     * @param resolveInfos list of resolve infos in descending priority order
     * @return if the list contains a resolve info with non-negative priority
     */
    private boolean hasNonNegativePriority(List<ResolveInfo> resolveInfos) {
        return resolveInfos.size() > 0 && resolveInfos.get(0).priority >= 0;
    }

    private static boolean hasWebURI(Intent intent) {
        if (intent.getData() == null) {
            return false;
        }
        final String scheme = intent.getScheme();
        if (TextUtils.isEmpty(scheme)) {
            return false;
        }
        return scheme.equals(IntentFilter.SCHEME_HTTP) || scheme.equals(IntentFilter.SCHEME_HTTPS);
    }

    private List<ResolveInfo> filterCandidatesWithDomainPreferredActivitiesLPr(Intent intent,
            int matchFlags, List<ResolveInfo> candidates, CrossProfileDomainInfo xpDomainInfo,
            int userId) {
        final boolean debug = (intent.getFlags() & Intent.FLAG_DEBUG_LOG_RESOLUTION) != 0;

        if (DEBUG_PREFERRED || DEBUG_DOMAIN_VERIFICATION) {
            Slog.v(TAG, "Filtering results with preferred activities. Candidates count: " +
                    candidates.size());
        }

        ArrayList<ResolveInfo> result = new ArrayList<ResolveInfo>();
        ArrayList<ResolveInfo> alwaysList = new ArrayList<ResolveInfo>();
        ArrayList<ResolveInfo> undefinedList = new ArrayList<ResolveInfo>();
        ArrayList<ResolveInfo> alwaysAskList = new ArrayList<ResolveInfo>();
        ArrayList<ResolveInfo> neverList = new ArrayList<ResolveInfo>();
        ArrayList<ResolveInfo> matchAllList = new ArrayList<ResolveInfo>();

        synchronized (mPackages) {
            final int count = candidates.size();
            // First, try to use linked apps. Partition the candidates into four lists:
            // one for the final results, one for the "do not use ever", one for "undefined status"
            // and finally one for "browser app type".
            for (int n=0; n<count; n++) {
                ResolveInfo info = candidates.get(n);
                String packageName = info.activityInfo.packageName;
                PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps != null) {
                    // Add to the special match all list (Browser use case)
                    if (info.handleAllWebDataURI) {
                        matchAllList.add(info);
                        continue;
                    }
                    // Try to get the status from User settings first
                    long packedStatus = getDomainVerificationStatusLPr(ps, userId);
                    int status = (int)(packedStatus >> 32);
                    int linkGeneration = (int)(packedStatus & 0xFFFFFFFF);
                    if (status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS) {
                        if (DEBUG_DOMAIN_VERIFICATION) {
                            Slog.i(TAG, "  + always: " + info.activityInfo.packageName
                                    + " : linkgen=" + linkGeneration);
                        }
                        // Use link-enabled generation as preferredOrder, i.e.
                        // prefer newly-enabled over earlier-enabled.
                        info.preferredOrder = linkGeneration;
                        alwaysList.add(info);
                    } else if (status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER) {
                        if (DEBUG_DOMAIN_VERIFICATION) {
                            Slog.i(TAG, "  + never: " + info.activityInfo.packageName);
                        }
                        neverList.add(info);
                    } else if (status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK) {
                        if (DEBUG_DOMAIN_VERIFICATION) {
                            Slog.i(TAG, "  + always-ask: " + info.activityInfo.packageName);
                        }
                        alwaysAskList.add(info);
                    } else if (status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED ||
                            status == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK) {
                        if (DEBUG_DOMAIN_VERIFICATION) {
                            Slog.i(TAG, "  + ask: " + info.activityInfo.packageName);
                        }
                        undefinedList.add(info);
                    }
                }
            }

            // We'll want to include browser possibilities in a few cases
            boolean includeBrowser = false;

            // First try to add the "always" resolution(s) for the current user, if any
            if (alwaysList.size() > 0) {
                result.addAll(alwaysList);
            } else {
                // Add all undefined apps as we want them to appear in the disambiguation dialog.
                result.addAll(undefinedList);
                // Maybe add one for the other profile.
                if (xpDomainInfo != null && (
                        xpDomainInfo.bestDomainVerificationStatus
                        != INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER)) {
                    result.add(xpDomainInfo.resolveInfo);
                }
                includeBrowser = true;
            }

            // The presence of any 'always ask' alternatives means we'll also offer browsers.
            // If there were 'always' entries their preferred order has been set, so we also
            // back that off to make the alternatives equivalent
            if (alwaysAskList.size() > 0) {
                for (ResolveInfo i : result) {
                    i.preferredOrder = 0;
                }
                result.addAll(alwaysAskList);
                includeBrowser = true;
            }

            if (includeBrowser) {
                // Also add browsers (all of them or only the default one)
                if (DEBUG_DOMAIN_VERIFICATION) {
                    Slog.v(TAG, "   ...including browsers in candidate set");
                }
                if ((matchFlags & MATCH_ALL) != 0) {
                    result.addAll(matchAllList);
                } else {
                    // Browser/generic handling case.  If there's a default browser, go straight
                    // to that (but only if there is no other higher-priority match).
                    final String defaultBrowserPackageName = getDefaultBrowserPackageName(userId);
                    int maxMatchPrio = 0;
                    ResolveInfo defaultBrowserMatch = null;
                    final int numCandidates = matchAllList.size();
                    for (int n = 0; n < numCandidates; n++) {
                        ResolveInfo info = matchAllList.get(n);
                        // track the highest overall match priority...
                        if (info.priority > maxMatchPrio) {
                            maxMatchPrio = info.priority;
                        }
                        // ...and the highest-priority default browser match
                        if (info.activityInfo.packageName.equals(defaultBrowserPackageName)) {
                            if (defaultBrowserMatch == null
                                    || (defaultBrowserMatch.priority < info.priority)) {
                                if (debug) {
                                    Slog.v(TAG, "Considering default browser match " + info);
                                }
                                defaultBrowserMatch = info;
                            }
                        }
                    }
                    if (defaultBrowserMatch != null
                            && defaultBrowserMatch.priority >= maxMatchPrio
                            && !TextUtils.isEmpty(defaultBrowserPackageName))
                    {
                        if (debug) {
                            Slog.v(TAG, "Default browser match " + defaultBrowserMatch);
                        }
                        result.add(defaultBrowserMatch);
                    } else {
                        result.addAll(matchAllList);
                    }
                }

                // If there is nothing selected, add all candidates and remove the ones that the user
                // has explicitly put into the INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER state
                if (result.size() == 0) {
                    result.addAll(candidates);
                    result.removeAll(neverList);
                }
            }
        }
        if (DEBUG_PREFERRED || DEBUG_DOMAIN_VERIFICATION) {
            Slog.v(TAG, "Filtered results with preferred activities. New candidates count: " +
                    result.size());
            for (ResolveInfo info : result) {
                Slog.v(TAG, "  + " + info.activityInfo);
            }
        }
        return result;
    }

    // Returns a packed value as a long:
    //
    // high 'int'-sized word: link status: undefined/ask/never/always.
    // low 'int'-sized word: relative priority among 'always' results.
    private long getDomainVerificationStatusLPr(PackageSetting ps, int userId) {
        long result = ps.getDomainVerificationStatusForUser(userId);
        // if none available, get the master status
        if (result >> 32 == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED) {
            if (ps.getIntentFilterVerificationInfo() != null) {
                result = ((long)ps.getIntentFilterVerificationInfo().getStatus()) << 32;
            }
        }
        return result;
    }

    private ResolveInfo querySkipCurrentProfileIntents(
            List<CrossProfileIntentFilter> matchingFilters, Intent intent, String resolvedType,
            int flags, int sourceUserId) {
        if (matchingFilters != null) {
            int size = matchingFilters.size();
            for (int i = 0; i < size; i ++) {
                CrossProfileIntentFilter filter = matchingFilters.get(i);
                if ((filter.getFlags() & PackageManager.SKIP_CURRENT_PROFILE) != 0) {
                    // Checking if there are activities in the target user that can handle the
                    // intent.
                    ResolveInfo resolveInfo = createForwardingResolveInfo(filter, intent,
                            resolvedType, flags, sourceUserId);
                    if (resolveInfo != null) {
                        return resolveInfo;
                    }
                }
            }
        }
        return null;
    }

    // Return matching ResolveInfo in target user if any.
    private ResolveInfo queryCrossProfileIntents(
            List<CrossProfileIntentFilter> matchingFilters, Intent intent, String resolvedType,
            int flags, int sourceUserId, boolean matchInCurrentProfile) {
        if (matchingFilters != null) {
            // Two {@link CrossProfileIntentFilter}s can have the same targetUserId and
            // match the same intent. For performance reasons, it is better not to
            // run queryIntent twice for the same userId
            SparseBooleanArray alreadyTriedUserIds = new SparseBooleanArray();
            int size = matchingFilters.size();
            for (int i = 0; i < size; i++) {
                CrossProfileIntentFilter filter = matchingFilters.get(i);
                int targetUserId = filter.getTargetUserId();
                boolean skipCurrentProfile =
                        (filter.getFlags() & PackageManager.SKIP_CURRENT_PROFILE) != 0;
                boolean skipCurrentProfileIfNoMatchFound =
                        (filter.getFlags() & PackageManager.ONLY_IF_NO_MATCH_FOUND) != 0;
                if (!skipCurrentProfile && !alreadyTriedUserIds.get(targetUserId)
                        && (!skipCurrentProfileIfNoMatchFound || !matchInCurrentProfile)) {
                    // Checking if there are activities in the target user that can handle the
                    // intent.
                    ResolveInfo resolveInfo = createForwardingResolveInfo(filter, intent,
                            resolvedType, flags, sourceUserId);
                    if (resolveInfo != null) return resolveInfo;
                    alreadyTriedUserIds.put(targetUserId, true);
                }
            }
        }
        return null;
    }

    /**
     * If the filter's target user can handle the intent and is enabled: returns a ResolveInfo that
     * will forward the intent to the filter's target user.
     * Otherwise, returns null.
     */
    private ResolveInfo createForwardingResolveInfo(CrossProfileIntentFilter filter, Intent intent,
            String resolvedType, int flags, int sourceUserId) {
        int targetUserId = filter.getTargetUserId();
        List<ResolveInfo> resultTargetUser = mActivities.queryIntent(intent,
                resolvedType, flags, targetUserId);
        if (resultTargetUser != null && isUserEnabled(targetUserId)) {
            // If all the matches in the target profile are suspended, return null.
            for (int i = resultTargetUser.size() - 1; i >= 0; i--) {
                if ((resultTargetUser.get(i).activityInfo.applicationInfo.flags
                        & ApplicationInfo.FLAG_SUSPENDED) == 0) {
                    return createForwardingResolveInfoUnchecked(filter, sourceUserId,
                            targetUserId);
                }
            }
        }
        return null;
    }

    private ResolveInfo createForwardingResolveInfoUnchecked(IntentFilter filter,
            int sourceUserId, int targetUserId) {
        ResolveInfo forwardingResolveInfo = new ResolveInfo();
        long ident = Binder.clearCallingIdentity();
        boolean targetIsProfile;
        try {
            targetIsProfile = sUserManager.getUserInfo(targetUserId).isManagedProfile();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        String className;
        if (targetIsProfile) {
            className = FORWARD_INTENT_TO_MANAGED_PROFILE;
        } else {
            className = FORWARD_INTENT_TO_PARENT;
        }
        ComponentName forwardingActivityComponentName = new ComponentName(
                mAndroidApplication.packageName, className);
        ActivityInfo forwardingActivityInfo = getActivityInfo(forwardingActivityComponentName, 0,
                sourceUserId);
        if (!targetIsProfile) {
            forwardingActivityInfo.showUserIcon = targetUserId;
            forwardingResolveInfo.noResourceId = true;
        }
        forwardingResolveInfo.activityInfo = forwardingActivityInfo;
        forwardingResolveInfo.priority = 0;
        forwardingResolveInfo.preferredOrder = 0;
        forwardingResolveInfo.match = 0;
        forwardingResolveInfo.isDefault = true;
        forwardingResolveInfo.filter = filter;
        forwardingResolveInfo.targetUserId = targetUserId;
        return forwardingResolveInfo;
    }

    @Override
    public @NonNull ParceledListSlice<ResolveInfo> queryIntentActivityOptions(ComponentName caller,
            Intent[] specifics, String[] specificTypes, Intent intent,
            String resolvedType, int flags, int userId) {
        return new ParceledListSlice<>(queryIntentActivityOptionsInternal(caller, specifics,
                specificTypes, intent, resolvedType, flags, userId));
    }

    private @NonNull List<ResolveInfo> queryIntentActivityOptionsInternal(ComponentName caller,
            Intent[] specifics, String[] specificTypes, Intent intent,
            String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        flags = updateFlagsForResolve(flags, userId, intent);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                false /* requireFullPermission */, false /* checkShell */,
                "query intent activity options");
        final String resultsAction = intent.getAction();

        final List<ResolveInfo> results = queryIntentActivitiesInternal(intent, resolvedType, flags
                | PackageManager.GET_RESOLVED_FILTER, userId);

        if (DEBUG_INTENT_MATCHING) {
            Log.v(TAG, "Query " + intent + ": " + results);
        }

        int specificsPos = 0;
        int N;

        // todo: note that the algorithm used here is O(N^2).  This
        // isn't a problem in our current environment, but if we start running
        // into situations where we have more than 5 or 10 matches then this
        // should probably be changed to something smarter...

        // First we go through and resolve each of the specific items
        // that were supplied, taking care of removing any corresponding
        // duplicate items in the generic resolve list.
        if (specifics != null) {
            for (int i=0; i<specifics.length; i++) {
                final Intent sintent = specifics[i];
                if (sintent == null) {
                    continue;
                }

                if (DEBUG_INTENT_MATCHING) {
                    Log.v(TAG, "Specific #" + i + ": " + sintent);
                }

                String action = sintent.getAction();
                if (resultsAction != null && resultsAction.equals(action)) {
                    // If this action was explicitly requested, then don't
                    // remove things that have it.
                    action = null;
                }

                ResolveInfo ri = null;
                ActivityInfo ai = null;

                ComponentName comp = sintent.getComponent();
                if (comp == null) {
                    ri = resolveIntent(
                        sintent,
                        specificTypes != null ? specificTypes[i] : null,
                            flags, userId);
                    if (ri == null) {
                        continue;
                    }
                    if (ri == mResolveInfo) {
                        // ACK!  Must do something better with this.
                    }
                    ai = ri.activityInfo;
                    comp = new ComponentName(ai.applicationInfo.packageName,
                            ai.name);
                } else {
                    ai = getActivityInfo(comp, flags, userId);
                    if (ai == null) {
                        continue;
                    }
                }

                // Look for any generic query activities that are duplicates
                // of this specific one, and remove them from the results.
                if (DEBUG_INTENT_MATCHING) Log.v(TAG, "Specific #" + i + ": " + ai);
                N = results.size();
                int j;
                for (j=specificsPos; j<N; j++) {
                    ResolveInfo sri = results.get(j);
                    if ((sri.activityInfo.name.equals(comp.getClassName())
                            && sri.activityInfo.applicationInfo.packageName.equals(
                                    comp.getPackageName()))
                        || (action != null && sri.filter.matchAction(action))) {
                        results.remove(j);
                        if (DEBUG_INTENT_MATCHING) Log.v(
                            TAG, "Removing duplicate item from " + j
                            + " due to specific " + specificsPos);
                        if (ri == null) {
                            ri = sri;
                        }
                        j--;
                        N--;
                    }
                }

                // Add this specific item to its proper place.
                if (ri == null) {
                    ri = new ResolveInfo();
                    ri.activityInfo = ai;
                }
                results.add(specificsPos, ri);
                ri.specificIndex = i;
                specificsPos++;
            }
        }

        // Now we go through the remaining generic results and remove any
        // duplicate actions that are found here.
        N = results.size();
        for (int i=specificsPos; i<N-1; i++) {
            final ResolveInfo rii = results.get(i);
            if (rii.filter == null) {
                continue;
            }

            // Iterate over all of the actions of this result's intent
            // filter...  typically this should be just one.
            final Iterator<String> it = rii.filter.actionsIterator();
            if (it == null) {
                continue;
            }
            while (it.hasNext()) {
                final String action = it.next();
                if (resultsAction != null && resultsAction.equals(action)) {
                    // If this action was explicitly requested, then don't
                    // remove things that have it.
                    continue;
                }
                for (int j=i+1; j<N; j++) {
                    final ResolveInfo rij = results.get(j);
                    if (rij.filter != null && rij.filter.hasAction(action)) {
                        results.remove(j);
                        if (DEBUG_INTENT_MATCHING) Log.v(
                            TAG, "Removing duplicate item from " + j
                            + " due to action " + action + " at " + i);
                        j--;
                        N--;
                    }
                }
            }

            // If the caller didn't request filter information, drop it now
            // so we don't have to marshall/unmarshall it.
            if ((flags&PackageManager.GET_RESOLVED_FILTER) == 0) {
                rii.filter = null;
            }
        }

        // Filter out the caller activity if so requested.
        if (caller != null) {
            N = results.size();
            for (int i=0; i<N; i++) {
                ActivityInfo ainfo = results.get(i).activityInfo;
                if (caller.getPackageName().equals(ainfo.applicationInfo.packageName)
                        && caller.getClassName().equals(ainfo.name)) {
                    results.remove(i);
                    break;
                }
            }
        }

        // If the caller didn't request filter information,
        // drop them now so we don't have to
        // marshall/unmarshall it.
        if ((flags&PackageManager.GET_RESOLVED_FILTER) == 0) {
            N = results.size();
            for (int i=0; i<N; i++) {
                results.get(i).filter = null;
            }
        }

        if (DEBUG_INTENT_MATCHING) Log.v(TAG, "Result: " + results);
        return results;
    }

    @Override
    public @NonNull ParceledListSlice<ResolveInfo> queryIntentReceivers(Intent intent,
            String resolvedType, int flags, int userId) {
        return new ParceledListSlice<>(
                queryIntentReceiversInternal(intent, resolvedType, flags, userId));
    }

    private @NonNull List<ResolveInfo> queryIntentReceiversInternal(Intent intent,
            String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        flags = updateFlagsForResolve(flags, userId, intent);
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            List<ResolveInfo> list = new ArrayList<ResolveInfo>(1);
            ActivityInfo ai = getReceiverInfo(comp, flags, userId);
            if (ai != null) {
                ResolveInfo ri = new ResolveInfo();
                ri.activityInfo = ai;
                list.add(ri);
            }
            return list;
        }

        // reader
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            if (pkgName == null) {
                return mReceivers.queryIntent(intent, resolvedType, flags, userId);
            }
            final PackageParser.Package pkg = mPackages.get(pkgName);
            if (pkg != null) {
                return mReceivers.queryIntentForPackage(intent, resolvedType, flags, pkg.receivers,
                        userId);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public ResolveInfo resolveService(Intent intent, String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForResolve(flags, userId, intent);
        List<ResolveInfo> query = queryIntentServicesInternal(intent, resolvedType, flags, userId);
        if (query != null) {
            if (query.size() >= 1) {
                // If there is more than one service with the same priority,
                // just arbitrarily pick the first one.
                return query.get(0);
            }
        }
        return null;
    }

    @Override
    public @NonNull ParceledListSlice<ResolveInfo> queryIntentServices(Intent intent,
            String resolvedType, int flags, int userId) {
        return new ParceledListSlice<>(
                queryIntentServicesInternal(intent, resolvedType, flags, userId));
    }

    private @NonNull List<ResolveInfo> queryIntentServicesInternal(Intent intent,
            String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        flags = updateFlagsForResolve(flags, userId, intent);
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<ResolveInfo>(1);
            final ServiceInfo si = getServiceInfo(comp, flags, userId);
            if (si != null) {
                final ResolveInfo ri = new ResolveInfo();
                ri.serviceInfo = si;
                list.add(ri);
            }
            return list;
        }

        // reader
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            if (pkgName == null) {
                return mServices.queryIntent(intent, resolvedType, flags, userId);
            }
            final PackageParser.Package pkg = mPackages.get(pkgName);
            if (pkg != null) {
                return mServices.queryIntentForPackage(intent, resolvedType, flags, pkg.services,
                        userId);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public @NonNull ParceledListSlice<ResolveInfo> queryIntentContentProviders(Intent intent,
            String resolvedType, int flags, int userId) {
        return new ParceledListSlice<>(
                queryIntentContentProvidersInternal(intent, resolvedType, flags, userId));
    }

    private @NonNull List<ResolveInfo> queryIntentContentProvidersInternal(
            Intent intent, String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return Collections.emptyList();
        flags = updateFlagsForResolve(flags, userId, intent);
        ComponentName comp = intent.getComponent();
        if (comp == null) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector();
                comp = intent.getComponent();
            }
        }
        if (comp != null) {
            final List<ResolveInfo> list = new ArrayList<ResolveInfo>(1);
            final ProviderInfo pi = getProviderInfo(comp, flags, userId);
            if (pi != null) {
                final ResolveInfo ri = new ResolveInfo();
                ri.providerInfo = pi;
                list.add(ri);
            }
            return list;
        }

        // reader
        synchronized (mPackages) {
            String pkgName = intent.getPackage();
            if (pkgName == null) {
                return mProviders.queryIntent(intent, resolvedType, flags, userId);
            }
            final PackageParser.Package pkg = mPackages.get(pkgName);
            if (pkg != null) {
                return mProviders.queryIntentForPackage(
                        intent, resolvedType, flags, pkg.providers, userId);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId) {
        if (!sUserManager.exists(userId)) return ParceledListSlice.emptyList();
        flags = updateFlagsForPackage(flags, userId, null);
        final boolean listUninstalled = (flags & MATCH_UNINSTALLED_PACKAGES) != 0;
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "get installed packages");

        // writer
        synchronized (mPackages) {
            ArrayList<PackageInfo> list;
            if (listUninstalled) {
                list = new ArrayList<PackageInfo>(mSettings.mPackages.size());
                for (PackageSetting ps : mSettings.mPackages.values()) {
                    final PackageInfo pi;
                    if (ps.pkg != null) {
                        pi = generatePackageInfo(ps, flags, userId);
                    } else {
                        pi = generatePackageInfo(ps, flags, userId);
                    }
                    if (pi != null) {
                        list.add(pi);
                    }
                }
            } else {
                list = new ArrayList<PackageInfo>(mPackages.size());
                for (PackageParser.Package p : mPackages.values()) {
                    final PackageInfo pi =
                            generatePackageInfo((PackageSetting)p.mExtras, flags, userId);
                    if (pi != null) {
                        list.add(pi);
                    }
                }
            }

            return new ParceledListSlice<PackageInfo>(list);
        }
    }

    private void addPackageHoldingPermissions(ArrayList<PackageInfo> list, PackageSetting ps,
            String[] permissions, boolean[] tmp, int flags, int userId) {
        int numMatch = 0;
        final PermissionsState permissionsState = ps.getPermissionsState();
        for (int i=0; i<permissions.length; i++) {
            final String permission = permissions[i];
            if (permissionsState.hasPermission(permission, userId)) {
                tmp[i] = true;
                numMatch++;
            } else {
                tmp[i] = false;
            }
        }
        if (numMatch == 0) {
            return;
        }
        final PackageInfo pi;
        if (ps.pkg != null) {
            pi = generatePackageInfo(ps, flags, userId);
        } else {
            pi = generatePackageInfo(ps, flags, userId);
        }
        // The above might return null in cases of uninstalled apps or install-state
        // skew across users/profiles.
        if (pi != null) {
            if ((flags&PackageManager.GET_PERMISSIONS) == 0) {
                if (numMatch == permissions.length) {
                    pi.requestedPermissions = permissions;
                } else {
                    pi.requestedPermissions = new String[numMatch];
                    numMatch = 0;
                    for (int i=0; i<permissions.length; i++) {
                        if (tmp[i]) {
                            pi.requestedPermissions[numMatch] = permissions[i];
                            numMatch++;
                        }
                    }
                }
            }
            list.add(pi);
        }
    }

    @Override
    public ParceledListSlice<PackageInfo> getPackagesHoldingPermissions(
            String[] permissions, int flags, int userId) {
        if (!sUserManager.exists(userId)) return ParceledListSlice.emptyList();
        flags = updateFlagsForPackage(flags, userId, permissions);
        final boolean listUninstalled = (flags & MATCH_UNINSTALLED_PACKAGES) != 0;

        // writer
        synchronized (mPackages) {
            ArrayList<PackageInfo> list = new ArrayList<PackageInfo>();
            boolean[] tmpBools = new boolean[permissions.length];
            if (listUninstalled) {
                for (PackageSetting ps : mSettings.mPackages.values()) {
                    addPackageHoldingPermissions(list, ps, permissions, tmpBools, flags, userId);
                }
            } else {
                for (PackageParser.Package pkg : mPackages.values()) {
                    PackageSetting ps = (PackageSetting)pkg.mExtras;
                    if (ps != null) {
                        addPackageHoldingPermissions(list, ps, permissions, tmpBools, flags,
                                userId);
                    }
                }
            }

            return new ParceledListSlice<PackageInfo>(list);
        }
    }

    @Override
    public ParceledListSlice<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        if (!sUserManager.exists(userId)) return ParceledListSlice.emptyList();
        flags = updateFlagsForApplication(flags, userId, null);
        final boolean listUninstalled = (flags & MATCH_UNINSTALLED_PACKAGES) != 0;

        // writer
        synchronized (mPackages) {
            ArrayList<ApplicationInfo> list;
            if (listUninstalled) {
                list = new ArrayList<ApplicationInfo>(mSettings.mPackages.size());
                for (PackageSetting ps : mSettings.mPackages.values()) {
                    ApplicationInfo ai;
                    if (ps.pkg != null) {
                        ai = PackageParser.generateApplicationInfo(ps.pkg, flags,
                                ps.readUserState(userId), userId);
                    } else {
                        ai = generateApplicationInfoFromSettingsLPw(ps.name, flags, userId);
                    }
                    if (ai != null) {
                        list.add(ai);
                    }
                }
            } else {
                list = new ArrayList<ApplicationInfo>(mPackages.size());
                for (PackageParser.Package p : mPackages.values()) {
                    if (p.mExtras != null) {
                        ApplicationInfo ai = PackageParser.generateApplicationInfo(p, flags,
                                ((PackageSetting)p.mExtras).readUserState(userId), userId);
                        if (ai != null) {
                            list.add(ai);
                        }
                    }
                }
            }

            return new ParceledListSlice<ApplicationInfo>(list);
        }
    }

    @Override
    public ParceledListSlice<EphemeralApplicationInfo> getEphemeralApplications(int userId) {
        if (DISABLE_EPHEMERAL_APPS) {
            return null;
        }

        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_EPHEMERAL_APPS,
                "getEphemeralApplications");
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "getEphemeralApplications");
        synchronized (mPackages) {
            List<EphemeralApplicationInfo> ephemeralApps = mEphemeralApplicationRegistry
                    .getEphemeralApplicationsLPw(userId);
            if (ephemeralApps != null) {
                return new ParceledListSlice<>(ephemeralApps);
            }
        }
        return null;
    }

    @Override
    public boolean isEphemeralApplication(String packageName, int userId) {
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "isEphemeral");
        if (DISABLE_EPHEMERAL_APPS) {
            return false;
        }

        if (!isCallerSameApp(packageName)) {
            return false;
        }
        synchronized (mPackages) {
            PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg != null) {
                return pkg.applicationInfo.isEphemeralApp();
            }
        }
        return false;
    }

    @Override
    public byte[] getEphemeralApplicationCookie(String packageName, int userId) {
        if (DISABLE_EPHEMERAL_APPS) {
            return null;
        }

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "getCookie");
        if (!isCallerSameApp(packageName)) {
            return null;
        }
        synchronized (mPackages) {
            return mEphemeralApplicationRegistry.getEphemeralApplicationCookieLPw(
                    packageName, userId);
        }
    }

    @Override
    public boolean setEphemeralApplicationCookie(String packageName, byte[] cookie, int userId) {
        if (DISABLE_EPHEMERAL_APPS) {
            return true;
        }

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, true /* checkShell */,
                "setCookie");
        if (!isCallerSameApp(packageName)) {
            return false;
        }
        synchronized (mPackages) {
            return mEphemeralApplicationRegistry.setEphemeralApplicationCookieLPw(
                    packageName, cookie, userId);
        }
    }

    @Override
    public Bitmap getEphemeralApplicationIcon(String packageName, int userId) {
        if (DISABLE_EPHEMERAL_APPS) {
            return null;
        }

        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_EPHEMERAL_APPS,
                "getEphemeralApplicationIcon");
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "getEphemeralApplicationIcon");
        synchronized (mPackages) {
            return mEphemeralApplicationRegistry.getEphemeralApplicationIconLPw(
                    packageName, userId);
        }
    }

    private boolean isCallerSameApp(String packageName) {
        PackageParser.Package pkg = mPackages.get(packageName);
        return pkg != null
                && UserHandle.getAppId(Binder.getCallingUid()) == pkg.applicationInfo.uid;
    }

    @Override
    public @NonNull ParceledListSlice<ApplicationInfo> getPersistentApplications(int flags) {
        return new ParceledListSlice<>(getPersistentApplicationsInternal(flags));
    }

    private @NonNull List<ApplicationInfo> getPersistentApplicationsInternal(int flags) {
        final ArrayList<ApplicationInfo> finalList = new ArrayList<ApplicationInfo>();

        // reader
        synchronized (mPackages) {
            final Iterator<PackageParser.Package> i = mPackages.values().iterator();
            final int userId = UserHandle.getCallingUserId();
            while (i.hasNext()) {
                final PackageParser.Package p = i.next();
                if (p.applicationInfo == null) continue;

                final boolean matchesUnaware = ((flags & MATCH_DIRECT_BOOT_UNAWARE) != 0)
                        && !p.applicationInfo.isDirectBootAware();
                final boolean matchesAware = ((flags & MATCH_DIRECT_BOOT_AWARE) != 0)
                        && p.applicationInfo.isDirectBootAware();

                if ((p.applicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0
                        && (!mSafeMode || isSystemApp(p))
                        && (matchesUnaware || matchesAware)) {
                    PackageSetting ps = mSettings.mPackages.get(p.packageName);
                    if (ps != null) {
                        ApplicationInfo ai = PackageParser.generateApplicationInfo(p, flags,
                                ps.readUserState(userId), userId);
                        if (ai != null) {
                            finalList.add(ai);
                        }
                    }
                }
            }
        }

        return finalList;
    }

    @Override
    public ProviderInfo resolveContentProvider(String name, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        flags = updateFlagsForComponent(flags, userId, name);
        // reader
        synchronized (mPackages) {
            final PackageParser.Provider provider = mProvidersByAuthority.get(name);
            PackageSetting ps = provider != null
                    ? mSettings.mPackages.get(provider.owner.packageName)
                    : null;
            return ps != null
                    && mSettings.isEnabledAndMatchLPr(provider.info, flags, userId)
                    ? PackageParser.generateProviderInfo(provider, flags,
                            ps.readUserState(userId), userId)
                    : null;
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void querySyncProviders(List<String> outNames, List<ProviderInfo> outInfo) {
        // reader
        synchronized (mPackages) {
            final Iterator<Map.Entry<String, PackageParser.Provider>> i = mProvidersByAuthority
                    .entrySet().iterator();
            final int userId = UserHandle.getCallingUserId();
            while (i.hasNext()) {
                Map.Entry<String, PackageParser.Provider> entry = i.next();
                PackageParser.Provider p = entry.getValue();
                PackageSetting ps = mSettings.mPackages.get(p.owner.packageName);

                if (ps != null && p.syncable
                        && (!mSafeMode || (p.info.applicationInfo.flags
                                &ApplicationInfo.FLAG_SYSTEM) != 0)) {
                    ProviderInfo info = PackageParser.generateProviderInfo(p, 0,
                            ps.readUserState(userId), userId);
                    if (info != null) {
                        outNames.add(entry.getKey());
                        outInfo.add(info);
                    }
                }
            }
        }
    }

    @Override
    public @NonNull ParceledListSlice<ProviderInfo> queryContentProviders(String processName,
            int uid, int flags) {
        final int userId = processName != null ? UserHandle.getUserId(uid)
                : UserHandle.getCallingUserId();
        if (!sUserManager.exists(userId)) return ParceledListSlice.emptyList();
        flags = updateFlagsForComponent(flags, userId, processName);

        ArrayList<ProviderInfo> finalList = null;
        // reader
        synchronized (mPackages) {
            final Iterator<PackageParser.Provider> i = mProviders.mProviders.values().iterator();
            while (i.hasNext()) {
                final PackageParser.Provider p = i.next();
                PackageSetting ps = mSettings.mPackages.get(p.owner.packageName);
                if (ps != null && p.info.authority != null
                        && (processName == null
                                || (p.info.processName.equals(processName)
                                        && UserHandle.isSameApp(p.info.applicationInfo.uid, uid)))
                        && mSettings.isEnabledAndMatchLPr(p.info, flags, userId)) {
                    if (finalList == null) {
                        finalList = new ArrayList<ProviderInfo>(3);
                    }
                    ProviderInfo info = PackageParser.generateProviderInfo(p, flags,
                            ps.readUserState(userId), userId);
                    if (info != null) {
                        finalList.add(info);
                    }
                }
            }
        }

        if (finalList != null) {
            Collections.sort(finalList, mProviderInitOrderSorter);
            return new ParceledListSlice<ProviderInfo>(finalList);
        }

        return ParceledListSlice.emptyList();
    }

    @Override
    public InstrumentationInfo getInstrumentationInfo(ComponentName name, int flags) {
        // reader
        synchronized (mPackages) {
            final PackageParser.Instrumentation i = mInstrumentation.get(name);
            return PackageParser.generateInstrumentationInfo(i, flags);
        }
    }

    @Override
    public @NonNull ParceledListSlice<InstrumentationInfo> queryInstrumentation(
            String targetPackage, int flags) {
        return new ParceledListSlice<>(queryInstrumentationInternal(targetPackage, flags));
    }

    private @NonNull List<InstrumentationInfo> queryInstrumentationInternal(String targetPackage,
            int flags) {
        ArrayList<InstrumentationInfo> finalList = new ArrayList<InstrumentationInfo>();

        // reader
        synchronized (mPackages) {
            final Iterator<PackageParser.Instrumentation> i = mInstrumentation.values().iterator();
            while (i.hasNext()) {
                final PackageParser.Instrumentation p = i.next();
                if (targetPackage == null
                        || targetPackage.equals(p.info.targetPackage)) {
                    InstrumentationInfo ii = PackageParser.generateInstrumentationInfo(p,
                            flags);
                    if (ii != null) {
                        finalList.add(ii);
                    }
                }
            }
        }

        return finalList;
    }

    private void createIdmapsForPackageLI(PackageParser.Package pkg) {
        ArrayMap<String, PackageParser.Package> overlays = mOverlays.get(pkg.packageName);
        if (overlays == null) {
            Slog.w(TAG, "Unable to create idmap for " + pkg.packageName + ": no overlay packages");
            return;
        }
        for (PackageParser.Package opkg : overlays.values()) {
            // Not much to do if idmap fails: we already logged the error
            // and we certainly don't want to abort installation of pkg simply
            // because an overlay didn't fit properly. For these reasons,
            // ignore the return value of createIdmapForPackagePairLI.
            createIdmapForPackagePairLI(pkg, opkg);
        }
    }

    private boolean createIdmapForPackagePairLI(PackageParser.Package pkg,
            PackageParser.Package opkg) {
        if (!opkg.mTrustedOverlay) {
            Slog.w(TAG, "Skipping target and overlay pair " + pkg.baseCodePath + " and " +
                    opkg.baseCodePath + ": overlay not trusted");
            return false;
        }
        ArrayMap<String, PackageParser.Package> overlaySet = mOverlays.get(pkg.packageName);
        if (overlaySet == null) {
            Slog.e(TAG, "was about to create idmap for " + pkg.baseCodePath + " and " +
                    opkg.baseCodePath + " but target package has no known overlays");
            return false;
        }
        final int sharedGid = UserHandle.getSharedAppGid(pkg.applicationInfo.uid);
        // TODO: generate idmap for split APKs
        try {
            mInstaller.idmap(pkg.baseCodePath, opkg.baseCodePath, sharedGid);
        } catch (InstallerException e) {
            Slog.e(TAG, "Failed to generate idmap for " + pkg.baseCodePath + " and "
                    + opkg.baseCodePath);
            return false;
        }
        PackageParser.Package[] overlayArray =
            overlaySet.values().toArray(new PackageParser.Package[0]);
        Comparator<PackageParser.Package> cmp = new Comparator<PackageParser.Package>() {
            public int compare(PackageParser.Package p1, PackageParser.Package p2) {
                return p1.mOverlayPriority - p2.mOverlayPriority;
            }
        };
        Arrays.sort(overlayArray, cmp);

        pkg.applicationInfo.resourceDirs = new String[overlayArray.length];
        int i = 0;
        for (PackageParser.Package p : overlayArray) {
            pkg.applicationInfo.resourceDirs[i++] = p.baseCodePath;
        }
        return true;
    }

    private void scanDirTracedLI(File dir, final int parseFlags, int scanFlags, long currentTime) {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "scanDir");
        try {
            scanDirLI(dir, parseFlags, scanFlags, currentTime);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    private void scanDirLI(File dir, final int parseFlags, int scanFlags, long currentTime) {
        final File[] files = dir.listFiles();
        if (ArrayUtils.isEmpty(files)) {
            Log.d(TAG, "No files in app dir " + dir);
            return;
        }

        if (DEBUG_PACKAGE_SCANNING) {
            Log.d(TAG, "Scanning app dir " + dir + " scanFlags=" + scanFlags
                    + " flags=0x" + Integer.toHexString(parseFlags));
        }

        Log.d(TAG, "start scanDirLI:"+dir);
        // use multi thread to speed up scanning
        int iMultitaskNum = SystemProperties.getInt("persist.pm.multitask", 6);
        Log.d(TAG, "max thread:" + iMultitaskNum);
        final MultiTaskDealer dealer = (iMultitaskNum > 1) ? MultiTaskDealer.startDealer(
                MultiTaskDealer.PACKAGEMANAGER_SCANER, iMultitaskNum) : null;

        for (File file : files) {
            final boolean isPackage = (isApkFile(file) || file.isDirectory())
                    && !PackageInstallerService.isStageName(file.getName());
            if (!isPackage) {
                // Ignore entries which are not packages
                continue;
            }
           if (RegionalizationEnvironment.isSupported()) {
             if (RegionalizationEnvironment.isExcludedApp(file.getName())) {
               Slog.d(TAG, "Regionalization Excluded:" + file.getName());
               continue;
            }
           }

            final File ref_file = file;
            final int ref_parseFlags = parseFlags;
            final int ref_scanFlags = scanFlags;
            final long ref_currentTime = currentTime;
            Runnable scanTask = new Runnable() {
                public void run() {
                    try {
                        scanPackageTracedLI(ref_file, ref_parseFlags | PackageParser.PARSE_MUST_BE_APK,
                                ref_scanFlags, ref_currentTime, null);
                    } catch (PackageManagerException e) {
                        Slog.w(TAG, "Failed to parse " + ref_file + ": " + e.getMessage());

                        // Delete invalid userdata apps
                        if ((ref_parseFlags & PackageParser.PARSE_IS_SYSTEM) == 0 &&
                                e.error == PackageManager.INSTALL_FAILED_INVALID_APK) {
                            logCriticalInfo(Log.WARN, "Deleting invalid package at " + ref_file);
                            removeCodePathLI(ref_file);
                        }
                    }
                }
            };

            if (dealer != null)
                dealer.addTask(scanTask);
            else
                scanTask.run();
        }

        if (dealer != null)
            dealer.waitAll();
        Log.d(TAG, "end scanDirLI:"+dir);
    }

    private static File getSettingsProblemFile() {
        File dataDir = Environment.getDataDirectory();
        File systemDir = new File(dataDir, "system");
        File fname = new File(systemDir, "uiderrors.txt");
        return fname;
    }

    static void reportSettingsProblem(int priority, String msg) {
        logCriticalInfo(priority, msg);
    }

    static synchronized void logCriticalInfo(int priority, String msg) {
        Slog.println(priority, TAG, msg);
        EventLogTags.writePmCriticalInfo(msg);
        try {
            File fname = getSettingsProblemFile();
            FileOutputStream out = new FileOutputStream(fname, true);
            PrintWriter pw = new FastPrintWriter(out);
            SimpleDateFormat formatter = new SimpleDateFormat();
            String dateString = formatter.format(new Date(System.currentTimeMillis()));
            pw.println(dateString + ": " + msg);
            pw.close();
            FileUtils.setPermissions(
                    fname.toString(),
                    FileUtils.S_IRWXU|FileUtils.S_IRWXG|FileUtils.S_IROTH,
                    -1, -1);
        } catch (java.io.IOException e) {
        }
    }

    private long getLastModifiedTime(PackageParser.Package pkg, File srcFile) {
        if (srcFile.isDirectory()) {
            final File baseFile = new File(pkg.baseCodePath);
            long maxModifiedTime = baseFile.lastModified();
            if (pkg.splitCodePaths != null) {
                for (int i = pkg.splitCodePaths.length - 1; i >=0; --i) {
                    final File splitFile = new File(pkg.splitCodePaths[i]);
                    maxModifiedTime = Math.max(maxModifiedTime, splitFile.lastModified());
                }
            }
            return maxModifiedTime;
        }
        return srcFile.lastModified();
    }

    private void collectCertificatesLI(PackageSetting ps, PackageParser.Package pkg, File srcFile,
            final int policyFlags) throws PackageManagerException {
        if (ps != null
                && ps.codePath.equals(srcFile)
                && ps.timeStamp == getLastModifiedTime(pkg, srcFile)
                && !isCompatSignatureUpdateNeeded(pkg)
                && !isRecoverSignatureUpdateNeeded(pkg)) {
            long mSigningKeySetId = ps.keySetData.getProperSigningKeySet();
            KeySetManagerService ksms = mSettings.mKeySetManagerService;
            ArraySet<PublicKey> signingKs;
            synchronized (mPackages) {
                signingKs = ksms.getPublicKeysFromKeySetLPr(mSigningKeySetId);
            }
            if (ps.signatures.mSignatures != null
                    && ps.signatures.mSignatures.length != 0
                    && signingKs != null) {
                // Optimization: reuse the existing cached certificates
                // if the package appears to be unchanged.
                pkg.mSignatures = ps.signatures.mSignatures;
                pkg.mSigningKeys = signingKs;
                return;
            }

            Slog.w(TAG, "PackageSetting for " + ps.name
                    + " is missing signatures.  Collecting certs again to recover them.");
        } else {
            Log.i(TAG, srcFile.toString() + " changed; collecting certs");
        }

        try {
            PackageParser.collectCertificates(pkg, policyFlags);
        } catch (PackageParserException e) {
            throw PackageManagerException.from(e);
        }
    }

    /**
     *  Traces a package scan.
     *  @see #scanPackageLI(File, int, int, long, UserHandle)
     */
    private PackageParser.Package scanPackageTracedLI(File scanFile, final int parseFlags,
            int scanFlags, long currentTime, UserHandle user) throws PackageManagerException {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "scanPackage");
        try {
            return scanPackageLI(scanFile, parseFlags, scanFlags, currentTime, user);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    /**
     *  Scans a package and returns the newly parsed package.
     *  Returns {@code null} in case of errors and the error code is stored in mLastScanError
     */
    private PackageParser.Package scanPackageLI(File scanFile, int parseFlags, int scanFlags,
            long currentTime, UserHandle user) throws PackageManagerException {
        if (DEBUG_INSTALL) Slog.d(TAG, "Parsing: " + scanFile);
        PackageParser pp = new PackageParser(mContext);
        pp.setSeparateProcesses(mSeparateProcesses);
        pp.setOnlyCoreApps(mOnlyCore);
        pp.setOnlyPowerOffAlarmApps(mOnlyPowerOffAlarm);
        pp.setDisplayMetrics(mMetrics);

        if ((scanFlags & SCAN_TRUSTED_OVERLAY) != 0) {
            parseFlags |= PackageParser.PARSE_TRUSTED_OVERLAY;
        }

        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parsePackage");
        final PackageParser.Package pkg;
        try {
            pkg = pp.parsePackage(scanFile, parseFlags);
        } catch (PackageParserException e) {
            throw PackageManagerException.from(e);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }

        return scanPackageLI(pkg, scanFile, parseFlags, scanFlags, currentTime, user);
    }

    /**
     *  Scans a package and returns the newly parsed package.
     *  @throws PackageManagerException on a parse error.
     */
    private PackageParser.Package scanPackageLI(PackageParser.Package pkg, File scanFile,
            final int policyFlags, int scanFlags, long currentTime, UserHandle user)
            throws PackageManagerException {
        // If the package has children and this is the first dive in the function
        // we scan the package with the SCAN_CHECK_ONLY flag set to see whether all
        // packages (parent and children) would be successfully scanned before the
        // actual scan since scanning mutates internal state and we want to atomically
        // install the package and its children.
        if ((scanFlags & SCAN_CHECK_ONLY) == 0) {
            if (pkg.childPackages != null && pkg.childPackages.size() > 0) {
                scanFlags |= SCAN_CHECK_ONLY;
            }
        } else {
            scanFlags &= ~SCAN_CHECK_ONLY;
        }

        // Scan the parent
        PackageParser.Package scannedPkg = scanPackageInternalLI(pkg, scanFile, policyFlags,
                scanFlags, currentTime, user);

        // Scan the children
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPackage = pkg.childPackages.get(i);
            scanPackageInternalLI(childPackage, scanFile, policyFlags, scanFlags,
                    currentTime, user);
        }


        if ((scanFlags & SCAN_CHECK_ONLY) != 0) {
            return scanPackageLI(pkg, scanFile, policyFlags, scanFlags, currentTime, user);
        }

        return scannedPkg;
    }

    /**
     *  Scans a package and returns the newly parsed package.
     *  @throws PackageManagerException on a parse error.
     */
    private PackageParser.Package scanPackageInternalLI(PackageParser.Package pkg, File scanFile,
            int policyFlags, int scanFlags, long currentTime, UserHandle user)
            throws PackageManagerException {
        PackageSetting ps = null;
        PackageSetting updatedPkg;
        // reader
        synchronized (mPackages) {
            // Look to see if we already know about this package.
            String oldName = mSettings.mRenamedPackages.get(pkg.packageName);
            if (pkg.mOriginalPackages != null && pkg.mOriginalPackages.contains(oldName)) {
                // This package has been renamed to its original name.  Let's
                // use that.
                ps = mSettings.peekPackageLPr(oldName);
            }
            // If there was no original package, see one for the real package name.
            if (ps == null) {
                ps = mSettings.peekPackageLPr(pkg.packageName);
            }
            // Check to see if this package could be hiding/updating a system
            // package.  Must look for it either under the original or real
            // package name depending on our state.
            updatedPkg = mSettings.getDisabledSystemPkgLPr(ps != null ? ps.name : pkg.packageName);
            if (DEBUG_INSTALL && updatedPkg != null) Slog.d(TAG, "updatedPkg = " + updatedPkg);

            // If this is a package we don't know about on the system partition, we
            // may need to remove disabled child packages on the system partition
            // or may need to not add child packages if the parent apk is updated
            // on the data partition and no longer defines this child package.
            if ((policyFlags & PackageParser.PARSE_IS_SYSTEM) != 0) {
                // If this is a parent package for an updated system app and this system
                // app got an OTA update which no longer defines some of the child packages
                // we have to prune them from the disabled system packages.
                PackageSetting disabledPs = mSettings.getDisabledSystemPkgLPr(pkg.packageName);
                if (disabledPs != null) {
                    final int scannedChildCount = (pkg.childPackages != null)
                            ? pkg.childPackages.size() : 0;
                    final int disabledChildCount = disabledPs.childPackageNames != null
                            ? disabledPs.childPackageNames.size() : 0;
                    for (int i = 0; i < disabledChildCount; i++) {
                        String disabledChildPackageName = disabledPs.childPackageNames.get(i);
                        boolean disabledPackageAvailable = false;
                        for (int j = 0; j < scannedChildCount; j++) {
                            PackageParser.Package childPkg = pkg.childPackages.get(j);
                            if (childPkg.packageName.equals(disabledChildPackageName)) {
                                disabledPackageAvailable = true;
                                break;
                            }
                         }
                         if (!disabledPackageAvailable) {
                             mSettings.removeDisabledSystemPackageLPw(disabledChildPackageName);
                         }
                    }
                }
            }
        }

        boolean updatedPkgBetter = false;
        // First check if this is a system package that may involve an update
        if (updatedPkg != null && (policyFlags & PackageParser.PARSE_IS_SYSTEM) != 0) {
            // If new package is not located in "/system/priv-app" (e.g. due to an OTA),
            // it needs to drop FLAG_PRIVILEGED.
            if (locationIsPrivileged(scanFile)) {
                updatedPkg.pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
            } else {
                updatedPkg.pkgPrivateFlags &= ~ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
            }

            if (ps != null && !ps.codePath.equals(scanFile)) {
                // The path has changed from what was last scanned...  check the
                // version of the new path against what we have stored to determine
                // what to do.
                if (DEBUG_INSTALL) Slog.d(TAG, "Path changing from " + ps.codePath);
                if (pkg.mVersionCode <= ps.versionCode) {
                    // The system package has been updated and the code path does not match
                    // Ignore entry. Skip it.
                    if (DEBUG_INSTALL) Slog.i(TAG, "Package " + ps.name + " at " + scanFile
                            + " ignored: updated version " + ps.versionCode
                            + " better than this " + pkg.mVersionCode);
                    if (!updatedPkg.codePath.equals(scanFile)) {
                        Slog.w(PackageManagerService.TAG, "Code path for hidden system pkg "
                                + ps.name + " changing from " + updatedPkg.codePathString
                                + " to " + scanFile);
                        updatedPkg.codePath = scanFile;
                        updatedPkg.codePathString = scanFile.toString();
                        updatedPkg.resourcePath = scanFile;
                        updatedPkg.resourcePathString = scanFile.toString();
                    }
                    updatedPkg.pkg = pkg;
                    updatedPkg.versionCode = pkg.mVersionCode;

                    // Update the disabled system child packages to point to the package too.
                    final int childCount = updatedPkg.childPackageNames != null
                            ? updatedPkg.childPackageNames.size() : 0;
                    for (int i = 0; i < childCount; i++) {
                        String childPackageName = updatedPkg.childPackageNames.get(i);
                        PackageSetting updatedChildPkg = mSettings.getDisabledSystemPkgLPr(
                                childPackageName);
                        if (updatedChildPkg != null) {
                            updatedChildPkg.pkg = pkg;
                            updatedChildPkg.versionCode = pkg.mVersionCode;
                        }
                    }

                    throw new PackageManagerException(Log.WARN, "Package " + ps.name + " at "
                            + scanFile + " ignored: updated version " + ps.versionCode
                            + " better than this " + pkg.mVersionCode);
                } else {
                    // The current app on the system partition is better than
                    // what we have updated to on the data partition; switch
                    // back to the system partition version.
                    // At this point, its safely assumed that package installation for
                    // apps in system partition will go through. If not there won't be a working
                    // version of the app
                    // writer
                    synchronized (mPackages) {
                        // Just remove the loaded entries from package lists.
                        mPackages.remove(ps.name);
                    }

                    logCriticalInfo(Log.WARN, "Package " + ps.name + " at " + scanFile
                            + " reverting from " + ps.codePathString
                            + ": new version " + pkg.mVersionCode
                            + " better than installed " + ps.versionCode);

                    InstallArgs args = createInstallArgsForExisting(packageFlagsToInstallFlags(ps),
                            ps.codePathString, ps.resourcePathString, getAppDexInstructionSets(ps));
                    synchronized (mInstallLock) {
                        args.cleanUpResourcesLI();
                    }
                    synchronized (mPackages) {
                        mSettings.enableSystemPackageLPw(ps.name);
                    }
                    updatedPkgBetter = true;
                }
            }
        }

        if (updatedPkg != null) {
            // An updated system app will not have the PARSE_IS_SYSTEM flag set
            // initially
            policyFlags |= PackageParser.PARSE_IS_SYSTEM;

            // An updated privileged app will not have the PARSE_IS_PRIVILEGED
            // flag set initially
            if ((updatedPkg.pkgPrivateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0) {
                policyFlags |= PackageParser.PARSE_IS_PRIVILEGED;
            }
        }

        // Verify certificates against what was last scanned
        collectCertificatesLI(ps, pkg, scanFile, policyFlags);

        /*
         * A new system app appeared, but we already had a non-system one of the
         * same name installed earlier.
         */
        boolean shouldHideSystemApp = false;
        if (updatedPkg == null && ps != null
                && (policyFlags & PackageParser.PARSE_IS_SYSTEM_DIR) != 0 && !isSystemApp(ps)) {
            /*
             * Check to make sure the signatures match first. If they don't,
             * wipe the installed application and its data.
             */
            if (compareSignatures(ps.signatures.mSignatures, pkg.mSignatures)
                    != PackageManager.SIGNATURE_MATCH) {
                logCriticalInfo(Log.WARN, "Package " + ps.name + " appeared on system, but"
                        + " signatures don't match existing userdata copy; removing");
                try (PackageFreezer freezer = freezePackage(pkg.packageName,
                        "scanPackageInternalLI")) {
                    deletePackageLIF(pkg.packageName, null, true, null, 0, null, false, null);
                }
                ps = null;
            } else {
                /*
                 * If the newly-added system app is an older version than the
                 * already installed version, hide it. It will be scanned later
                 * and re-added like an update.
                 */
                if (pkg.mVersionCode <= ps.versionCode) {
                    shouldHideSystemApp = true;
                    logCriticalInfo(Log.INFO, "Package " + ps.name + " appeared at " + scanFile
                            + " but new version " + pkg.mVersionCode + " better than installed "
                            + ps.versionCode + "; hiding system");
                } else {
                    /*
                     * The newly found system app is a newer version that the
                     * one previously installed. Simply remove the
                     * already-installed application and replace it with our own
                     * while keeping the application data.
                     */
                    logCriticalInfo(Log.WARN, "Package " + ps.name + " at " + scanFile
                            + " reverting from " + ps.codePathString + ": new version "
                            + pkg.mVersionCode + " better than installed " + ps.versionCode);
                    InstallArgs args = createInstallArgsForExisting(packageFlagsToInstallFlags(ps),
                            ps.codePathString, ps.resourcePathString, getAppDexInstructionSets(ps));
                    synchronized (mInstallLock) {
                        args.cleanUpResourcesLI();
                    }
                }
            }
        }

        // The apk is forward locked (not public) if its code and resources
        // are kept in different files. (except for app in either system or
        // vendor path).
        // TODO grab this value from PackageSettings
        if ((policyFlags & PackageParser.PARSE_IS_SYSTEM_DIR) == 0) {
            if (ps != null && !ps.codePath.equals(ps.resourcePath)) {
                policyFlags |= PackageParser.PARSE_FORWARD_LOCK;
            }
        }

        // TODO: extend to support forward-locked splits
        String resourcePath = null;
        String baseResourcePath = null;
        if ((policyFlags & PackageParser.PARSE_FORWARD_LOCK) != 0 && !updatedPkgBetter) {
            if (ps != null && ps.resourcePathString != null) {
                resourcePath = ps.resourcePathString;
                baseResourcePath = ps.resourcePathString;
            } else {
                // Should not happen at all. Just log an error.
                Slog.e(TAG, "Resource path not set for package " + pkg.packageName);
            }
        } else {
            resourcePath = pkg.codePath;
            baseResourcePath = pkg.baseCodePath;
        }

        // Set application objects path explicitly.
        pkg.setApplicationVolumeUuid(pkg.volumeUuid);
        pkg.setApplicationInfoCodePath(pkg.codePath);
        pkg.setApplicationInfoBaseCodePath(pkg.baseCodePath);
        pkg.setApplicationInfoSplitCodePaths(pkg.splitCodePaths);
        pkg.setApplicationInfoResourcePath(resourcePath);
        pkg.setApplicationInfoBaseResourcePath(baseResourcePath);
        pkg.setApplicationInfoSplitResourcePaths(pkg.splitCodePaths);

        // Note that we invoke the following method only if we are about to unpack an application
        PackageParser.Package scannedPkg = scanPackageLI(pkg, policyFlags, scanFlags
                | SCAN_UPDATE_SIGNATURE, currentTime, user);

        /*
         * If the system app should be overridden by a previously installed
         * data, hide the system app now and let the /data/app scan pick it up
         * again.
         */
        if (shouldHideSystemApp) {
            synchronized (mPackages) {
                mSettings.disableSystemPackageLPw(pkg.packageName, true);
            }
        }

        return scannedPkg;
    }

    private static String fixProcessName(String defProcessName,
            String processName, int uid) {
        if (processName == null) {
            return defProcessName;
        }
        return processName;
    }

    private void verifySignaturesLP(PackageSetting pkgSetting, PackageParser.Package pkg)
            throws PackageManagerException {
        if (pkgSetting.signatures.mSignatures != null) {
            // Already existing package. Make sure signatures match
            boolean match = compareSignatures(pkgSetting.signatures.mSignatures, pkg.mSignatures)
                    == PackageManager.SIGNATURE_MATCH;
            if (!match) {
                match = compareSignaturesCompat(pkgSetting.signatures, pkg)
                        == PackageManager.SIGNATURE_MATCH;
            }
            if (!match) {
                match = compareSignaturesRecover(pkgSetting.signatures, pkg)
                        == PackageManager.SIGNATURE_MATCH;
            }
            if (!match) {
                throw new PackageManagerException(INSTALL_FAILED_UPDATE_INCOMPATIBLE, "Package "
                        + pkg.packageName + " signatures do not match the "
                        + "previously installed version; ignoring!");
            }
        }

        // Check for shared user signatures
        if (pkgSetting.sharedUser != null && pkgSetting.sharedUser.signatures.mSignatures != null) {
            // Already existing package. Make sure signatures match
            boolean match = compareSignatures(pkgSetting.sharedUser.signatures.mSignatures,
                    pkg.mSignatures) == PackageManager.SIGNATURE_MATCH;
            if (!match) {
                match = compareSignaturesCompat(pkgSetting.sharedUser.signatures, pkg)
                        == PackageManager.SIGNATURE_MATCH;
            }
            if (!match) {
                match = compareSignaturesRecover(pkgSetting.sharedUser.signatures, pkg)
                        == PackageManager.SIGNATURE_MATCH;
            }
            if (!match) {
                throw new PackageManagerException(INSTALL_FAILED_SHARED_USER_INCOMPATIBLE,
                        "Package " + pkg.packageName
                        + " has no signatures that match those in shared user "
                        + pkgSetting.sharedUser.name + "; ignoring!");
            }
        }
    }

    /**
     * Enforces that only the system UID or root's UID can call a method exposed
     * via Binder.
     *
     * @param message used as message if SecurityException is thrown
     * @throws SecurityException if the caller is not system or root
     */
    private static final void enforceSystemOrRoot(String message) {
        final int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != 0) {
            throw new SecurityException(message);
        }
    }

    @Override
    public void performFstrimIfNeeded() {
        enforceSystemOrRoot("Only the system can request fstrim");

        // Before everything else, see whether we need to fstrim.
        try {
            IMountService ms = PackageHelper.getMountService();
            if (ms != null) {
                boolean doTrim = false;
                final long interval = android.provider.Settings.Global.getLong(
                        mContext.getContentResolver(),
                        android.provider.Settings.Global.FSTRIM_MANDATORY_INTERVAL,
                        DEFAULT_MANDATORY_FSTRIM_INTERVAL);
                if (interval > 0) {
                    final long timeSinceLast = System.currentTimeMillis() - ms.lastMaintenance();
                    if (timeSinceLast > interval) {
                        doTrim = true;
                        Slog.w(TAG, "No disk maintenance in " + timeSinceLast
                                + "; running immediately");
                    }
                }
                if (doTrim) {
                    if (!isFirstBoot()) {
                        try {
                            ActivityManagerNative.getDefault().showBootMessage(
                                    mContext.getResources().getString(
                                            R.string.android_upgrading_fstrim), true);
                        } catch (RemoteException e) {
                        }
                    }
                    ms.runMaintenance();
                }
            } else {
                Slog.e(TAG, "Mount service unavailable!");
            }
        } catch (RemoteException e) {
            // Can't happen; MountService is local
        }
    }

    @Override
    public void updatePackagesIfNeeded() {
        enforceSystemOrRoot("Only the system can request package update");

        // We need to re-extract after an OTA.
        boolean causeUpgrade = isUpgrade();

        // First boot or factory reset.
        // Note: we also handle devices that are upgrading to N right now as if it is their
        //       first boot, as they do not have profile data.
        boolean causeFirstBoot = isFirstBoot() || mIsPreNUpgrade;

        // We need to re-extract after a pruned cache, as AoT-ed files will be out of date.
        boolean causePrunedCache = VMRuntime.didPruneDalvikCache();

        if (!causeUpgrade && !causeFirstBoot && !causePrunedCache) {
            return;
        }

        List<PackageParser.Package> pkgs;
        synchronized (mPackages) {
            pkgs = PackageManagerServiceUtils.getPackagesForDexopt(mPackages.values(), this);
        }

        final long startTime = System.nanoTime();
        final int[] stats = performDexOptUpgrade(pkgs, mIsPreNUpgrade /* showDialog */,
                    getCompilerFilterForReason(causeFirstBoot ? REASON_FIRST_BOOT : REASON_BOOT));

        final int elapsedTimeSeconds =
                (int) TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);

        MetricsLogger.histogram(mContext, "opt_dialog_num_dexopted", stats[0]);
        MetricsLogger.histogram(mContext, "opt_dialog_num_skipped", stats[1]);
        MetricsLogger.histogram(mContext, "opt_dialog_num_failed", stats[2]);
        MetricsLogger.histogram(mContext, "opt_dialog_num_total", getOptimizablePackages().size());
        MetricsLogger.histogram(mContext, "opt_dialog_time_s", elapsedTimeSeconds);
    }

    /**
     * Performs dexopt on the set of packages in {@code packages} and returns an int array
     * containing statistics about the invocation. The array consists of three elements,
     * which are (in order) {@code numberOfPackagesOptimized}, {@code numberOfPackagesSkipped}
     * and {@code numberOfPackagesFailed}.
     */
    private int[] performDexOptUpgrade(List<PackageParser.Package> pkgs, boolean showDialog,
            String compilerFilter) {

        int numberOfPackagesVisited = 0;
        int numberOfPackagesOptimized = 0;
        int numberOfPackagesSkipped = 0;
        int numberOfPackagesFailed = 0;
        final int numberOfPackagesToDexopt = pkgs.size();

        for (PackageParser.Package pkg : pkgs) {
            numberOfPackagesVisited++;

            if (!PackageDexOptimizer.canOptimizePackage(pkg)) {
                if (DEBUG_DEXOPT) {
                    Log.i(TAG, "Skipping update of of non-optimizable app " + pkg.packageName);
                }
                numberOfPackagesSkipped++;
                continue;
            }

            if (DEBUG_DEXOPT) {
                Log.i(TAG, "Updating app " + numberOfPackagesVisited + " of " +
                        numberOfPackagesToDexopt + ": " + pkg.packageName);
            }

            if (showDialog) {
                try {
                    ActivityManagerNative.getDefault().showBootMessage(
                            mContext.getResources().getString(R.string.android_upgrading_apk,
                                    numberOfPackagesVisited, numberOfPackagesToDexopt), true);
                } catch (RemoteException e) {
                }
            }

            // If the OTA updates a system app which was previously preopted to a non-preopted state
            // the app might end up being verified at runtime. That's because by default the apps
            // are verify-profile but for preopted apps there's no profile.
            // Do a hacky check to ensure that if we have no profiles (a reasonable indication
            // that before the OTA the app was preopted) the app gets compiled with a non-profile
            // filter (by default interpret-only).
            // Note that at this stage unused apps are already filtered.
            if (isSystemApp(pkg) &&
                    DexFile.isProfileGuidedCompilerFilter(compilerFilter) &&
                    !Environment.getReferenceProfile(pkg.packageName).exists()) {
                compilerFilter = getNonProfileGuidedCompilerFilter(compilerFilter);
            }

            // checkProfiles is false to avoid merging profiles during boot which
            // might interfere with background compilation (b/28612421).
            // Unfortunately this will also means that "pm.dexopt.boot=speed-profile" will
            // behave differently than "pm.dexopt.bg-dexopt=speed-profile" but that's a
            // trade-off worth doing to save boot time work.
            int dexOptStatus = performDexOptTraced(pkg.packageName,
                    false /* checkProfiles */,
                    compilerFilter,
                    false /* force */);
            switch (dexOptStatus) {
                case PackageDexOptimizer.DEX_OPT_PERFORMED:
                    numberOfPackagesOptimized++;
                    break;
                case PackageDexOptimizer.DEX_OPT_SKIPPED:
                    numberOfPackagesSkipped++;
                    break;
                case PackageDexOptimizer.DEX_OPT_FAILED:
                    numberOfPackagesFailed++;
                    break;
                default:
                    Log.e(TAG, "Unexpected dexopt return code " + dexOptStatus);
                    break;
            }
        }

        return new int[] { numberOfPackagesOptimized, numberOfPackagesSkipped,
                numberOfPackagesFailed };
    }

    @Override
    public void notifyPackageUse(String packageName, int reason) {
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if (p == null) {
                return;
            }
            p.mLastPackageUsageTimeInMills[reason] = System.currentTimeMillis();
        }
    }

    // TODO: this is not used nor needed. Delete it.
    @Override
    public boolean performDexOptIfNeeded(String packageName) {
        int dexOptStatus = performDexOptTraced(packageName,
                false /* checkProfiles */, getFullCompilerFilter(), false /* force */);
        return dexOptStatus != PackageDexOptimizer.DEX_OPT_FAILED;
    }

    @Override
    public boolean performDexOpt(String packageName,
            boolean checkProfiles, int compileReason, boolean force) {
        int dexOptStatus = performDexOptTraced(packageName, checkProfiles,
                getCompilerFilterForReason(compileReason), force);
        return dexOptStatus != PackageDexOptimizer.DEX_OPT_FAILED;
    }

    @Override
    public boolean performDexOptMode(String packageName,
            boolean checkProfiles, String targetCompilerFilter, boolean force) {
        int dexOptStatus = performDexOptTraced(packageName, checkProfiles,
                targetCompilerFilter, force);
        return dexOptStatus != PackageDexOptimizer.DEX_OPT_FAILED;
    }

    private int performDexOptTraced(String packageName,
                boolean checkProfiles, String targetCompilerFilter, boolean force) {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "dexopt");
        try {
            return performDexOptInternal(packageName, checkProfiles,
                    targetCompilerFilter, force);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    // Run dexopt on a given package. Returns true if dexopt did not fail, i.e.
    // if the package can now be considered up to date for the given filter.
    private int performDexOptInternal(String packageName,
                boolean checkProfiles, String targetCompilerFilter, boolean force) {
        PackageParser.Package p;
        synchronized (mPackages) {
            p = mPackages.get(packageName);
            if (p == null) {
                // Package could not be found. Report failure.
                return PackageDexOptimizer.DEX_OPT_FAILED;
            }
            mPackageUsage.maybeWriteAsync(mPackages);
            mCompilerStats.maybeWriteAsync();
        }
        long callingId = Binder.clearCallingIdentity();
        try {
            synchronized (mInstallLock) {
                return performDexOptInternalWithDependenciesLI(p, checkProfiles,
                        targetCompilerFilter, force);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    public ArraySet<String> getOptimizablePackages() {
        ArraySet<String> pkgs = new ArraySet<String>();
        synchronized (mPackages) {
            for (PackageParser.Package p : mPackages.values()) {
                if (PackageDexOptimizer.canOptimizePackage(p)) {
                    pkgs.add(p.packageName);
                }
            }
        }
        return pkgs;
    }

    private int performDexOptInternalWithDependenciesLI(PackageParser.Package p,
            boolean checkProfiles, String targetCompilerFilter,
            boolean force) {
        // Select the dex optimizer based on the force parameter.
        // Note: The force option is rarely used (cmdline input for testing, mostly), so it's OK to
        //       allocate an object here.
        PackageDexOptimizer pdo = force
                ? new PackageDexOptimizer.ForcedUpdatePackageDexOptimizer(mPackageDexOptimizer)
                : mPackageDexOptimizer;

        // Optimize all dependencies first. Note: we ignore the return value and march on
        // on errors.
        Collection<PackageParser.Package> deps = findSharedNonSystemLibraries(p);
        final String[] instructionSets = getAppDexInstructionSets(p.applicationInfo);
        if (!deps.isEmpty()) {
            for (PackageParser.Package depPackage : deps) {
                // TODO: Analyze and investigate if we (should) profile libraries.
                // Currently this will do a full compilation of the library by default.
                pdo.performDexOpt(depPackage, null /* sharedLibraries */, instructionSets,
                        false /* checkProfiles */,
                        getCompilerFilterForReason(REASON_NON_SYSTEM_LIBRARY),
                        getOrCreateCompilerPackageStats(depPackage));
            }
        }
        return pdo.performDexOpt(p, p.usesLibraryFiles, instructionSets, checkProfiles,
                targetCompilerFilter, getOrCreateCompilerPackageStats(p));
    }

    Collection<PackageParser.Package> findSharedNonSystemLibraries(PackageParser.Package p) {
        if (p.usesLibraries != null || p.usesOptionalLibraries != null) {
            ArrayList<PackageParser.Package> retValue = new ArrayList<>();
            Set<String> collectedNames = new ArraySet<>();
            findSharedNonSystemLibrariesRecursive(p, retValue, collectedNames);

            retValue.remove(p);

            return retValue;
        } else {
            return Collections.emptyList();
        }
    }

    private void findSharedNonSystemLibrariesRecursive(PackageParser.Package p,
            Collection<PackageParser.Package> collected, Set<String> collectedNames) {
        if (!collectedNames.contains(p.packageName)) {
            collectedNames.add(p.packageName);
            collected.add(p);

            if (p.usesLibraries != null) {
                findSharedNonSystemLibrariesRecursive(p.usesLibraries, collected, collectedNames);
            }
            if (p.usesOptionalLibraries != null) {
                findSharedNonSystemLibrariesRecursive(p.usesOptionalLibraries, collected,
                        collectedNames);
            }
        }
    }

    private void findSharedNonSystemLibrariesRecursive(Collection<String> libs,
            Collection<PackageParser.Package> collected, Set<String> collectedNames) {
        for (String libName : libs) {
            PackageParser.Package libPkg = findSharedNonSystemLibrary(libName);
            if (libPkg != null) {
                findSharedNonSystemLibrariesRecursive(libPkg, collected, collectedNames);
            }
        }
    }

    private PackageParser.Package findSharedNonSystemLibrary(String libName) {
        synchronized (mPackages) {
            PackageManagerService.SharedLibraryEntry lib = mSharedLibraries.get(libName);
            if (lib != null && lib.apk != null) {
                return mPackages.get(lib.apk);
            }
        }
        return null;
    }

    public void shutdown() {
        mPackageUsage.writeNow(mPackages);
        mCompilerStats.writeNow();
    }

    @Override
    public void dumpProfiles(String packageName) {
        PackageParser.Package pkg;
        synchronized (mPackages) {
            pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
        }
        /* Only the shell, root, or the app user should be able to dump profiles. */
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.SHELL_UID &&
            callingUid != Process.ROOT_UID &&
            callingUid != pkg.applicationInfo.uid) {
            throw new SecurityException("dumpProfiles");
        }

        synchronized (mInstallLock) {
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "dump profiles");
            final int sharedGid = UserHandle.getSharedAppGid(pkg.applicationInfo.uid);
            try {
                List<String> allCodePaths = pkg.getAllCodePathsExcludingResourceOnly();
                String gid = Integer.toString(sharedGid);
                String codePaths = TextUtils.join(";", allCodePaths);
                mInstaller.dumpProfiles(gid, packageName, codePaths);
            } catch (InstallerException e) {
                Slog.w(TAG, "Failed to dump profiles", e);
            }
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    @Override
    public void forceDexOpt(String packageName) {
        enforceSystemOrRoot("forceDexOpt");

        PackageParser.Package pkg;
        synchronized (mPackages) {
            pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
        }

        synchronized (mInstallLock) {
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "dexopt");

            // Whoever is calling forceDexOpt wants a fully compiled package.
            // Don't use profiles since that may cause compilation to be skipped.
            final int res = performDexOptInternalWithDependenciesLI(pkg,
                    false /* checkProfiles */, getCompilerFilterForReason(REASON_FORCED_DEXOPT),
                    true /* force */);

            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
            if (res != PackageDexOptimizer.DEX_OPT_PERFORMED) {
                throw new IllegalStateException("Failed to dexopt: " + res);
            }
        }
    }

    private boolean verifyPackageUpdateLPr(PackageSetting oldPkg, PackageParser.Package newPkg) {
        if ((oldPkg.pkgFlags&ApplicationInfo.FLAG_SYSTEM) == 0) {
            Slog.w(TAG, "Unable to update from " + oldPkg.name
                    + " to " + newPkg.packageName
                    + ": old package not in system partition");
            return false;
        } else if (mPackages.get(oldPkg.name) != null) {
            Slog.w(TAG, "Unable to update from " + oldPkg.name
                    + " to " + newPkg.packageName
                    + ": old package still exists");
            return false;
        }
        return true;
    }

    void removeCodePathLI(File codePath) {
        if (codePath.isDirectory()) {
            try {
                mInstaller.rmPackageDir(codePath.getAbsolutePath());
            } catch (InstallerException e) {
                Slog.w(TAG, "Failed to remove code path", e);
            }
        } else {
            codePath.delete();
        }
    }

    private int[] resolveUserIds(int userId) {
        return (userId == UserHandle.USER_ALL) ? sUserManager.getUserIds() : new int[] { userId };
    }

    private void clearAppDataLIF(PackageParser.Package pkg, int userId, int flags) {
        if (pkg == null) {
            Slog.wtf(TAG, "Package was null!", new Throwable());
            return;
        }
        clearAppDataLeafLIF(pkg, userId, flags);
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            clearAppDataLeafLIF(pkg.childPackages.get(i), userId, flags);
        }
    }

    private void clearAppDataLeafLIF(PackageParser.Package pkg, int userId, int flags) {
        final PackageSetting ps;
        synchronized (mPackages) {
            ps = mSettings.mPackages.get(pkg.packageName);
        }
        for (int realUserId : resolveUserIds(userId)) {
            final long ceDataInode = (ps != null) ? ps.getCeDataInode(realUserId) : 0;
            try {
                mInstaller.clearAppData(pkg.volumeUuid, pkg.packageName, realUserId, flags,
                        ceDataInode);
            } catch (InstallerException e) {
                Slog.w(TAG, String.valueOf(e));
            }
        }
    }

    private void destroyAppDataLIF(PackageParser.Package pkg, int userId, int flags) {
        if (pkg == null) {
            Slog.wtf(TAG, "Package was null!", new Throwable());
            return;
        }
        destroyAppDataLeafLIF(pkg, userId, flags);
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            destroyAppDataLeafLIF(pkg.childPackages.get(i), userId, flags);
        }
    }

    private void destroyAppDataLeafLIF(PackageParser.Package pkg, int userId, int flags) {
        final PackageSetting ps;
        synchronized (mPackages) {
            ps = mSettings.mPackages.get(pkg.packageName);
        }
        for (int realUserId : resolveUserIds(userId)) {
            final long ceDataInode = (ps != null) ? ps.getCeDataInode(realUserId) : 0;
            try {
                mInstaller.destroyAppData(pkg.volumeUuid, pkg.packageName, realUserId, flags,
                        ceDataInode);
            } catch (InstallerException e) {
                Slog.w(TAG, String.valueOf(e));
            }
        }
    }

    private void destroyAppProfilesLIF(PackageParser.Package pkg, int userId) {
        if (pkg == null) {
            Slog.wtf(TAG, "Package was null!", new Throwable());
            return;
        }
        destroyAppProfilesLeafLIF(pkg);
        destroyAppReferenceProfileLeafLIF(pkg, userId, true /* removeBaseMarker */);
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            destroyAppProfilesLeafLIF(pkg.childPackages.get(i));
            destroyAppReferenceProfileLeafLIF(pkg.childPackages.get(i), userId,
                    true /* removeBaseMarker */);
        }
    }

    private void destroyAppReferenceProfileLeafLIF(PackageParser.Package pkg, int userId,
            boolean removeBaseMarker) {
        if (pkg.isForwardLocked()) {
            return;
        }

        for (String path : pkg.getAllCodePathsExcludingResourceOnly()) {
            try {
                path = PackageManagerServiceUtils.realpath(new File(path));
            } catch (IOException e) {
                // TODO: Should we return early here ?
                Slog.w(TAG, "Failed to get canonical path", e);
                continue;
            }

            final String useMarker = path.replace('/', '@');
            for (int realUserId : resolveUserIds(userId)) {
                File profileDir = Environment.getDataProfilesDeForeignDexDirectory(realUserId);
                if (removeBaseMarker) {
                    File foreignUseMark = new File(profileDir, useMarker);
                    if (foreignUseMark.exists()) {
                        if (!foreignUseMark.delete()) {
                            Slog.w(TAG, "Unable to delete foreign user mark for package: "
                                    + pkg.packageName);
                        }
                    }
                }

                File[] markers = profileDir.listFiles();
                if (markers != null) {
                    final String searchString = "@" + pkg.packageName + "@";
                    // We also delete all markers that contain the package name we're
                    // uninstalling. These are associated with secondary dex-files belonging
                    // to the package. Reconstructing the path of these dex files is messy
                    // in general.
                    for (File marker : markers) {
                        if (marker.getName().indexOf(searchString) > 0) {
                            if (!marker.delete()) {
                                Slog.w(TAG, "Unable to delete foreign user mark for package: "
                                    + pkg.packageName);
                            }
                        }
                    }
                }
            }
        }
    }

    private void destroyAppProfilesLeafLIF(PackageParser.Package pkg) {
        try {
            mInstaller.destroyAppProfiles(pkg.packageName);
        } catch (InstallerException e) {
            Slog.w(TAG, String.valueOf(e));
        }
    }

    private void clearAppProfilesLIF(PackageParser.Package pkg, int userId) {
        if (pkg == null) {
            Slog.wtf(TAG, "Package was null!", new Throwable());
            return;
        }
        clearAppProfilesLeafLIF(pkg);
        // We don't remove the base foreign use marker when clearing profiles because
        // we will rename it when the app is updated. Unlike the actual profile contents,
        // the foreign use marker is good across installs.
        destroyAppReferenceProfileLeafLIF(pkg, userId, false /* removeBaseMarker */);
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            clearAppProfilesLeafLIF(pkg.childPackages.get(i));
        }
    }

    private void clearAppProfilesLeafLIF(PackageParser.Package pkg) {
        try {
            mInstaller.clearAppProfiles(pkg.packageName);
        } catch (InstallerException e) {
            Slog.w(TAG, String.valueOf(e));
        }
    }

    private void setInstallAndUpdateTime(PackageParser.Package pkg, long firstInstallTime,
            long lastUpdateTime) {
        // Set parent install/update time
        PackageSetting ps = (PackageSetting) pkg.mExtras;
        if (ps != null) {
            ps.firstInstallTime = firstInstallTime;
            ps.lastUpdateTime = lastUpdateTime;
        }
        // Set children install/update time
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = pkg.childPackages.get(i);
            ps = (PackageSetting) childPkg.mExtras;
            if (ps != null) {
                ps.firstInstallTime = firstInstallTime;
                ps.lastUpdateTime = lastUpdateTime;
            }
        }
    }

    private void addSharedLibraryLPw(ArraySet<String> usesLibraryFiles, SharedLibraryEntry file,
            PackageParser.Package changingLib) {
        if (file.path != null) {
            usesLibraryFiles.add(file.path);
            return;
        }
        PackageParser.Package p = mPackages.get(file.apk);
        if (changingLib != null && changingLib.packageName.equals(file.apk)) {
            // If we are doing this while in the middle of updating a library apk,
            // then we need to make sure to use that new apk for determining the
            // dependencies here.  (We haven't yet finished committing the new apk
            // to the package manager state.)
            if (p == null || p.packageName.equals(changingLib.packageName)) {
                p = changingLib;
            }
        }
        if (p != null) {
            usesLibraryFiles.addAll(p.getAllCodePaths());
        }
    }

    private void updateSharedLibrariesLPw(PackageParser.Package pkg,
            PackageParser.Package changingLib) throws PackageManagerException {
        if (pkg.usesLibraries != null || pkg.usesOptionalLibraries != null) {
            final ArraySet<String> usesLibraryFiles = new ArraySet<>();
            int N = pkg.usesLibraries != null ? pkg.usesLibraries.size() : 0;
            for (int i=0; i<N; i++) {
                final SharedLibraryEntry file = mSharedLibraries.get(pkg.usesLibraries.get(i));
                if (file == null) {
                    throw new PackageManagerException(INSTALL_FAILED_MISSING_SHARED_LIBRARY,
                            "Package " + pkg.packageName + " requires unavailable shared library "
                            + pkg.usesLibraries.get(i) + "; failing!");
                }
                addSharedLibraryLPw(usesLibraryFiles, file, changingLib);
            }
            N = pkg.usesOptionalLibraries != null ? pkg.usesOptionalLibraries.size() : 0;
            for (int i=0; i<N; i++) {
                final SharedLibraryEntry file = mSharedLibraries.get(pkg.usesOptionalLibraries.get(i));
                if (file == null) {
                    Slog.w(TAG, "Package " + pkg.packageName
                            + " desires unavailable shared library "
                            + pkg.usesOptionalLibraries.get(i) + "; ignoring!");
                } else {
                    addSharedLibraryLPw(usesLibraryFiles, file, changingLib);
                }
            }
            N = usesLibraryFiles.size();
            if (N > 0) {
                pkg.usesLibraryFiles = usesLibraryFiles.toArray(new String[N]);
            } else {
                pkg.usesLibraryFiles = null;
            }
        }
    }

    private static boolean hasString(List<String> list, List<String> which) {
        if (list == null) {
            return false;
        }
        for (int i=list.size()-1; i>=0; i--) {
            for (int j=which.size()-1; j>=0; j--) {
                if (which.get(j).equals(list.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateAllSharedLibrariesLPw() {
        for (PackageParser.Package pkg : mPackages.values()) {
            try {
                updateSharedLibrariesLPw(pkg, null);
            } catch (PackageManagerException e) {
                Slog.e(TAG, "updateAllSharedLibrariesLPw failed: " + e.getMessage());
            }
        }
    }

    private ArrayList<PackageParser.Package> updateAllSharedLibrariesLPw(
            PackageParser.Package changingPkg) {
        ArrayList<PackageParser.Package> res = null;
        for (PackageParser.Package pkg : mPackages.values()) {
            if (hasString(pkg.usesLibraries, changingPkg.libraryNames)
                    || hasString(pkg.usesOptionalLibraries, changingPkg.libraryNames)) {
                if (res == null) {
                    res = new ArrayList<PackageParser.Package>();
                }
                res.add(pkg);
                try {
                    updateSharedLibrariesLPw(pkg, changingPkg);
                } catch (PackageManagerException e) {
                    Slog.e(TAG, "updateAllSharedLibrariesLPw failed: " + e.getMessage());
                }
            }
        }
        return res;
    }

    /**
     * Derive the value of the {@code cpuAbiOverride} based on the provided
     * value and an optional stored value from the package settings.
     */
    private static String deriveAbiOverride(String abiOverride, PackageSetting settings) {
        String cpuAbiOverride = null;

        if (NativeLibraryHelper.CLEAR_ABI_OVERRIDE.equals(abiOverride)) {
            cpuAbiOverride = null;
        } else if (abiOverride != null) {
            cpuAbiOverride = abiOverride;
        } else if (settings != null) {
            cpuAbiOverride = settings.cpuAbiOverrideString;
        }

        return cpuAbiOverride;
    }

    private PackageParser.Package scanPackageTracedLI(PackageParser.Package pkg,
            final int policyFlags, int scanFlags, long currentTime, UserHandle user)
                    throws PackageManagerException {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "scanPackage");
        // If the package has children and this is the first dive in the function
        // we recursively scan the package with the SCAN_CHECK_ONLY flag set to see
        // whether all packages (parent and children) would be successfully scanned
        // before the actual scan since scanning mutates internal state and we want
        // to atomically install the package and its children.
        if ((scanFlags & SCAN_CHECK_ONLY) == 0) {
            if (pkg.childPackages != null && pkg.childPackages.size() > 0) {
                scanFlags |= SCAN_CHECK_ONLY;
            }
        } else {
            scanFlags &= ~SCAN_CHECK_ONLY;
        }

        final PackageParser.Package scannedPkg;
        try {
            // Scan the parent
            scannedPkg = scanPackageLI(pkg, policyFlags, scanFlags, currentTime, user);
            // Scan the children
            final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
            for (int i = 0; i < childCount; i++) {
                PackageParser.Package childPkg = pkg.childPackages.get(i);
                scanPackageLI(childPkg, policyFlags,
                        scanFlags, currentTime, user);
            }
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }

        if ((scanFlags & SCAN_CHECK_ONLY) != 0) {
            return scanPackageTracedLI(pkg, policyFlags, scanFlags, currentTime, user);
        }

        return scannedPkg;
    }

    private PackageParser.Package scanPackageLI(PackageParser.Package pkg, final int policyFlags,
            int scanFlags, long currentTime, UserHandle user) throws PackageManagerException {
        boolean success = false;
        try {
            final PackageParser.Package res = scanPackageDirtyLI(pkg, policyFlags, scanFlags,
                    currentTime, user);
            success = true;
            return res;
        } finally {
            if (!success && (scanFlags & SCAN_DELETE_DATA_ON_FAILURES) != 0) {
                // DELETE_DATA_ON_FAILURES is only used by frozen paths
                destroyAppDataLIF(pkg, UserHandle.USER_ALL,
                        StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE);
                destroyAppProfilesLIF(pkg, UserHandle.USER_ALL);
            }
        }
    }

    /**
     * Returns {@code true} if the given file contains code. Otherwise {@code false}.
     */
    private static boolean apkHasCode(String fileName) {
        StrictJarFile jarFile = null;
        try {
            jarFile = new StrictJarFile(fileName,
                    false /*verify*/, false /*signatureSchemeRollbackProtectionsEnforced*/);
            return jarFile.findEntry("classes.dex") != null;
        } catch (IOException ignore) {
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (IOException ignore) {}
        }
        return false;
    }

    /**
     * Enforces code policy for the package. This ensures that if an APK has
     * declared hasCode="true" in its manifest that the APK actually contains
     * code.
     *
     * @throws PackageManagerException If bytecode could not be found when it should exist
     */
    private static void enforceCodePolicy(PackageParser.Package pkg)
            throws PackageManagerException {
        final boolean shouldHaveCode =
                (pkg.applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
        if (shouldHaveCode && !apkHasCode(pkg.baseCodePath)) {
            throw new PackageManagerException(INSTALL_FAILED_INVALID_APK,
                    "Package " + pkg.baseCodePath + " code is missing");
        }

        if (!ArrayUtils.isEmpty(pkg.splitCodePaths)) {
            for (int i = 0; i < pkg.splitCodePaths.length; i++) {
                final boolean splitShouldHaveCode =
                        (pkg.splitFlags[i] & ApplicationInfo.FLAG_HAS_CODE) != 0;
                if (splitShouldHaveCode && !apkHasCode(pkg.splitCodePaths[i])) {
                    throw new PackageManagerException(INSTALL_FAILED_INVALID_APK,
                            "Package " + pkg.splitCodePaths[i] + " code is missing");
                }
            }
        }
    }

    private PackageParser.Package scanPackageDirtyLI(PackageParser.Package pkg,
            final int policyFlags, final int scanFlags, long currentTime, UserHandle user)
            throws PackageManagerException {
        final File scanFile = new File(pkg.codePath);
        if (pkg.applicationInfo.getCodePath() == null ||
                pkg.applicationInfo.getResourcePath() == null) {
            // Bail out. The resource and code paths haven't been set.
            throw new PackageManagerException(INSTALL_FAILED_INVALID_APK,
                    "Code and resource paths haven't been set correctly");
        }

        // Apply policy
        if ((policyFlags&PackageParser.PARSE_IS_SYSTEM) != 0) {
            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
            if (pkg.applicationInfo.isDirectBootAware()) {
                // we're direct boot aware; set for all components
                for (PackageParser.Service s : pkg.services) {
                    s.info.encryptionAware = s.info.directBootAware = true;
                }
                for (PackageParser.Provider p : pkg.providers) {
                    p.info.encryptionAware = p.info.directBootAware = true;
                }
                for (PackageParser.Activity a : pkg.activities) {
                    a.info.encryptionAware = a.info.directBootAware = true;
                }
                for (PackageParser.Activity r : pkg.receivers) {
                    r.info.encryptionAware = r.info.directBootAware = true;
                }
            }
        } else {
            // Only allow system apps to be flagged as core apps.
            pkg.coreApp = false;
            // clear flags not applicable to regular apps
            pkg.applicationInfo.privateFlags &=
                    ~ApplicationInfo.PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE;
            pkg.applicationInfo.privateFlags &=
                    ~ApplicationInfo.PRIVATE_FLAG_DIRECT_BOOT_AWARE;
        }
        pkg.mTrustedOverlay = (policyFlags&PackageParser.PARSE_TRUSTED_OVERLAY) != 0;

        if ((policyFlags&PackageParser.PARSE_IS_PRIVILEGED) != 0) {
            pkg.applicationInfo.privateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
        }

        if ((policyFlags & PackageParser.PARSE_ENFORCE_CODE) != 0) {
            enforceCodePolicy(pkg);
        }

        if (mCustomResolverComponentName != null &&
                mCustomResolverComponentName.getPackageName().equals(pkg.packageName)) {
            setUpCustomResolverActivity(pkg);
        }

        if (pkg.packageName.equals("android")) {
            synchronized (mPackages) {
                if (mAndroidApplication != null) {
                    Slog.w(TAG, "*************************************************");
                    Slog.w(TAG, "Core android package being redefined.  Skipping.");
                    Slog.w(TAG, " file=" + scanFile);
                    Slog.w(TAG, "*************************************************");
                    throw new PackageManagerException(INSTALL_FAILED_DUPLICATE_PACKAGE,
                            "Core android package being redefined.  Skipping.");
                }

                if ((scanFlags & SCAN_CHECK_ONLY) == 0) {
                    // Set up information for our fall-back user intent resolution activity.
                    mPlatformPackage = pkg;
                    pkg.mVersionCode = mSdkVersion;
                    mAndroidApplication = pkg.applicationInfo;

                    if (!mResolverReplaced) {
                        mResolveActivity.applicationInfo = mAndroidApplication;
                        mResolveActivity.name = ResolverActivity.class.getName();
                        mResolveActivity.packageName = mAndroidApplication.packageName;
                        mResolveActivity.processName = "system:ui";
                        mResolveActivity.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
                        mResolveActivity.documentLaunchMode = ActivityInfo.DOCUMENT_LAUNCH_NEVER;
                        mResolveActivity.flags = ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
                        mResolveActivity.theme = R.style.Theme_Material_Dialog_Alert;
                        mResolveActivity.exported = true;
                        mResolveActivity.enabled = true;
                        mResolveActivity.resizeMode = ActivityInfo.RESIZE_MODE_RESIZEABLE;
                        mResolveActivity.configChanges = ActivityInfo.CONFIG_SCREEN_SIZE
                                | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE
                                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                                | ActivityInfo.CONFIG_ORIENTATION
                                | ActivityInfo.CONFIG_KEYBOARD
                                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN;
                        mResolveInfo.activityInfo = mResolveActivity;
                        mResolveInfo.priority = 0;
                        mResolveInfo.preferredOrder = 0;
                        mResolveInfo.match = 0;
                        mResolveComponentName = new ComponentName(
                                mAndroidApplication.packageName, mResolveActivity.name);
                    }
                }
            }
        }

        if (DEBUG_PACKAGE_SCANNING) {
            if ((policyFlags & PackageParser.PARSE_CHATTY) != 0)
                Log.d(TAG, "Scanning package " + pkg.packageName);
        }

        synchronized (mPackages) {
            if (mPackages.containsKey(pkg.packageName)
                    || mSharedLibraries.containsKey(pkg.packageName)) {
                throw new PackageManagerException(INSTALL_FAILED_DUPLICATE_PACKAGE,
                        "Application package " + pkg.packageName
                                + " already installed.  Skipping duplicate.");
            }

            // If we're only installing presumed-existing packages, require that the
            // scanned APK is both already known and at the path previously established
            // for it.  Previously unknown packages we pick up normally, but if we have an
            // a priori expectation about this package's install presence, enforce it.
            // With a singular exception for new system packages. When an OTA contains
            // a new system package, we allow the codepath to change from a system location
            // to the user-installed location. If we don't allow this change, any newer,
            // user-installed version of the application will be ignored.
            if ((scanFlags & SCAN_REQUIRE_KNOWN) != 0) {
                if (mExpectingBetter.containsKey(pkg.packageName)) {
                    logCriticalInfo(Log.WARN,
                            "Relax SCAN_REQUIRE_KNOWN requirement for package " + pkg.packageName);
                } else {
                    PackageSetting known = mSettings.peekPackageLPr(pkg.packageName);
                    if (known != null) {
                        if (DEBUG_PACKAGE_SCANNING) {
                            Log.d(TAG, "Examining " + pkg.codePath
                                    + " and requiring known paths " + known.codePathString
                                    + " & " + known.resourcePathString);
                        }
                        if (!pkg.applicationInfo.getCodePath().equals(known.codePathString)
                                || !pkg.applicationInfo.getResourcePath().equals(
                                known.resourcePathString)) {
                            throw new PackageManagerException(INSTALL_FAILED_PACKAGE_CHANGED,
                                    "Application package " + pkg.packageName
                                            + " found at " + pkg.applicationInfo.getCodePath()
                                            + " but expected at " + known.codePathString
                                            + "; ignoring.");
                        }
                    }
                }
            }
        }

        if (Build.TAGS.equals("test-keys") &&
                !pkg.applicationInfo.sourceDir.startsWith(Environment.getRootDirectory().getPath()) &&
                !pkg.applicationInfo.sourceDir.startsWith("/vendor")) {
            Object obj = mSettings.getUserIdLPr(1000);
            Signature[] s1 = null;
            if (obj instanceof SharedUserSetting) {
                s1 = ((SharedUserSetting)obj).signatures.mSignatures;
            }
            if ((compareSignatures(pkg.mSignatures, s1) == PackageManager.SIGNATURE_MATCH)) {
                throw new PackageManagerException(INSTALL_FAILED_INVALID_INSTALL_LOCATION,
                        "Cannot install platform packages to user storage!");
            }
        }

        // Initialize package source and resource directories
        File destCodeFile = new File(pkg.applicationInfo.getCodePath());
        File destResourceFile = new File(pkg.applicationInfo.getResourcePath());

        SharedUserSetting suid = null;
        PackageSetting pkgSetting = null;

        if (!isSystemApp(pkg)) {
            // Only system apps can use these features.
            pkg.mOriginalPackages = null;
            pkg.mRealPackage = null;
            pkg.mAdoptPermissions = null;
        }

        // Getting the package setting may have a side-effect, so if we
        // are only checking if scan would succeed, stash a copy of the
        // old setting to restore at the end.
        PackageSetting nonMutatedPs = null;

        // writer
        synchronized (mPackages) {
            if (pkg.mSharedUserId != null) {
                suid = mSettings.getSharedUserLPw(pkg.mSharedUserId, 0, 0, true);
                if (suid == null) {
                    throw new PackageManagerException(INSTALL_FAILED_INSUFFICIENT_STORAGE,
                            "Creating application package " + pkg.packageName
                            + " for shared user failed");
                }
                if (DEBUG_PACKAGE_SCANNING) {
                    if ((policyFlags & PackageParser.PARSE_CHATTY) != 0)
                        Log.d(TAG, "Shared UserID " + pkg.mSharedUserId + " (uid=" + suid.userId
                                + "): packages=" + suid.packages);
                }
            }

            // Check if we are renaming from an original package name.
            PackageSetting origPackage = null;
            String realName = null;
            if (pkg.mOriginalPackages != null) {
                // This package may need to be renamed to a previously
                // installed name.  Let's check on that...
                final String renamed = mSettings.mRenamedPackages.get(pkg.mRealPackage);
                if (pkg.mOriginalPackages.contains(renamed)) {
                    // This package had originally been installed as the
                    // original name, and we have already taken care of
                    // transitioning to the new one.  Just update the new
                    // one to continue using the old name.
                    realName = pkg.mRealPackage;
                    if (!pkg.packageName.equals(renamed)) {
                        // Callers into this function may have already taken
                        // care of renaming the package; only do it here if
                        // it is not already done.
                        pkg.setPackageName(renamed);
                    }

                } else {
                    for (int i=pkg.mOriginalPackages.size()-1; i>=0; i--) {
                        if ((origPackage = mSettings.peekPackageLPr(
                                pkg.mOriginalPackages.get(i))) != null) {
                            // We do have the package already installed under its
                            // original name...  should we use it?
                            if (!verifyPackageUpdateLPr(origPackage, pkg)) {
                                // New package is not compatible with original.
                                origPackage = null;
                                continue;
                            } else if (origPackage.sharedUser != null) {
                                // Make sure uid is compatible between packages.
                                if (!origPackage.sharedUser.name.equals(pkg.mSharedUserId)) {
                                    Slog.w(TAG, "Unable to migrate data from " + origPackage.name
                                            + " to " + pkg.packageName + ": old uid "
                                            + origPackage.sharedUser.name
                                            + " differs from " + pkg.mSharedUserId);
                                    origPackage = null;
                                    continue;
                                }
                                // TODO: Add case when shared user id is added [b/28144775]
                            } else {
                                if (DEBUG_UPGRADE) Log.v(TAG, "Renaming new package "
                                        + pkg.packageName + " to old name " + origPackage.name);
                            }
                            break;
                        }
                    }
                }
            }

            if (mTransferedPackages.contains(pkg.packageName)) {
                Slog.w(TAG, "Package " + pkg.packageName
                        + " was transferred to another, but its .apk remains");
            }

            // See comments in nonMutatedPs declaration
            if ((scanFlags & SCAN_CHECK_ONLY) != 0) {
                PackageSetting foundPs = mSettings.peekPackageLPr(pkg.packageName);
                if (foundPs != null) {
                    nonMutatedPs = new PackageSetting(foundPs);
                }
            }

            // Just create the setting, don't add it yet. For already existing packages
            // the PkgSetting exists already and doesn't have to be created.
            pkgSetting = mSettings.getPackageLPw(pkg, origPackage, realName, suid, destCodeFile,
                    destResourceFile, pkg.applicationInfo.nativeLibraryRootDir,
                    pkg.applicationInfo.primaryCpuAbi,
                    pkg.applicationInfo.secondaryCpuAbi,
                    pkg.applicationInfo.flags, pkg.applicationInfo.privateFlags,
                    user, false);
            if (pkgSetting == null) {
                throw new PackageManagerException(INSTALL_FAILED_INSUFFICIENT_STORAGE,
                        "Creating application package " + pkg.packageName + " failed");
            }

            if (pkgSetting.origPackage != null) {
                // If we are first transitioning from an original package,
                // fix up the new package's name now.  We need to do this after
                // looking up the package under its new name, so getPackageLP
                // can take care of fiddling things correctly.
                pkg.setPackageName(origPackage.name);

                // File a report about this.
                String msg = "New package " + pkgSetting.realName
                        + " renamed to replace old package " + pkgSetting.name;
                reportSettingsProblem(Log.WARN, msg);

                // Make a note of it.
                if ((scanFlags & SCAN_CHECK_ONLY) == 0) {
                    mTransferedPackages.add(origPackage.name);
                }

                // No longer need to retain this.
                pkgSetting.origPackage = null;
            }

            if ((scanFlags & SCAN_CHECK_ONLY) == 0 && realName != null) {
                // Make a note of it.
                mTransferedPackages.add(pkg.packageName);
            }

            if (mSettings.isDisabledSystemPackageLPr(pkg.packageName)) {
                pkg.applicationInfo.flags |= ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            }

            if ((policyFlags&PackageParser.PARSE_IS_SYSTEM_DIR) == 0) {
                // Check all shared libraries and map to their actual file path.
                // We only do this here for apps not on a system dir, because those
                // are the only ones that can fail an install due to this.  We
                // will take care of the system apps by updating all of their
                // library paths after the scan is done.
                updateSharedLibrariesLPw(pkg, null);
            }

            if (mFoundPolicyFile) {
                SELinuxMMAC.assignSeinfoValue(pkg);
            }

            pkg.applicationInfo.uid = pkgSetting.appId;
            pkg.mExtras = pkgSetting;
            if (shouldCheckUpgradeKeySetLP(pkgSetting, scanFlags)) {
                if (checkUpgradeKeySetLP(pkgSetting, pkg)) {
                    // We just determined the app is signed correctly, so bring
                    // over the latest parsed certs.
                    pkgSetting.signatures.mSignatures = pkg.mSignatures;
                } else {
                    if ((policyFlags & PackageParser.PARSE_IS_SYSTEM_DIR) == 0) {
                        throw new PackageManagerException(INSTALL_FAILED_UPDATE_INCOMPATIBLE,
                                "Package " + pkg.packageName + " upgrade keys do not match the "
                                + "previously installed version");
                    } else {
                        pkgSetting.signatures.mSignatures = pkg.mSignatures;
                        String msg = "System package " + pkg.packageName
                            + " signature changed; retaining data.";
                        reportSettingsProblem(Log.WARN, msg);
                    }
                }
            } else {
                try {
                    verifySignaturesLP(pkgSetting, pkg);
                    // We just determined the app is signed correctly, so bring
                    // over the latest parsed certs.
                    pkgSetting.signatures.mSignatures = pkg.mSignatures;
                } catch (PackageManagerException e) {
                    if ((policyFlags & PackageParser.PARSE_IS_SYSTEM_DIR) == 0) {
                        throw e;
                    }
                    // The signature has changed, but this package is in the system
                    // image...  let's recover!
                    pkgSetting.signatures.mSignatures = pkg.mSignatures;
                    // However...  if this package is part of a shared user, but it
                    // doesn't match the signature of the shared user, let's fail.
                    // What this means is that you can't change the signatures
                    // associated with an overall shared user, which doesn't seem all
                    // that unreasonable.
                    if (pkgSetting.sharedUser != null) {
                        if (compareSignatures(pkgSetting.sharedUser.signatures.mSignatures,
                                              pkg.mSignatures) != PackageManager.SIGNATURE_MATCH) {
                            throw new PackageManagerException(
                                    INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES,
                                            "Signature mismatch for shared user: "
                                            + pkgSetting.sharedUser);
                        }
                    }
                    // File a report about this.
                    String msg = "System package " + pkg.packageName
                        + " signature changed; retaining data.";
                    reportSettingsProblem(Log.WARN, msg);
                }
            }
            // Verify that this new package doesn't have any content providers
            // that conflict with existing packages.  Only do this if the
            // package isn't already installed, since we don't want to break
            // things that are installed.
            if ((scanFlags & SCAN_NEW_INSTALL) != 0) {
                final int N = pkg.providers.size();
                int i;
                for (i=0; i<N; i++) {
                    PackageParser.Provider p = pkg.providers.get(i);
                    if (p.info.authority != null) {
                        String names[] = p.info.authority.split(";");
                        for (int j = 0; j < names.length; j++) {
                            if (mProvidersByAuthority.containsKey(names[j])) {
                                PackageParser.Provider other = mProvidersByAuthority.get(names[j]);
                                final String otherPackageName =
                                        ((other != null && other.getComponentName() != null) ?
                                                other.getComponentName().getPackageName() : "?");
                                throw new PackageManagerException(
                                        INSTALL_FAILED_CONFLICTING_PROVIDER,
                                                "Can't install because provider name " + names[j]
                                                + " (in package " + pkg.applicationInfo.packageName
                                                + ") is already used by " + otherPackageName);
                            }
                        }
                    }
                }
            }

            if ((scanFlags & SCAN_CHECK_ONLY) == 0 && pkg.mAdoptPermissions != null) {
                // This package wants to adopt ownership of permissions from
                // another package.
                for (int i = pkg.mAdoptPermissions.size() - 1; i >= 0; i--) {
                    final String origName = pkg.mAdoptPermissions.get(i);
                    final PackageSetting orig = mSettings.peekPackageLPr(origName);
                    if (orig != null) {
                        if (verifyPackageUpdateLPr(orig, pkg)) {
                            Slog.i(TAG, "Adopting permissions from " + origName + " to "
                                    + pkg.packageName);
                            mSettings.transferPermissionsLPw(origName, pkg.packageName);
                        }
                    }
                }
            }
        }

        final String pkgName = pkg.packageName;

        final long scanFileTime = getLastModifiedTime(pkg, scanFile);
        final boolean forceDex = (scanFlags & SCAN_FORCE_DEX) != 0;
        pkg.applicationInfo.processName = fixProcessName(
                pkg.applicationInfo.packageName,
                pkg.applicationInfo.processName,
                pkg.applicationInfo.uid);

        if (pkg != mPlatformPackage) {
            // Get all of our default paths setup
            pkg.applicationInfo.initForUser(UserHandle.USER_SYSTEM);
        }

        final String path = scanFile.getPath();
        final String cpuAbiOverride = deriveAbiOverride(pkg.cpuAbiOverride, pkgSetting);

        if ((scanFlags & SCAN_NEW_INSTALL) == 0) {
            derivePackageAbi(pkg, scanFile, cpuAbiOverride, true /* extract libs */);

            // Some system apps still use directory structure for native libraries
            // in which case we might end up not detecting abi solely based on apk
            // structure. Try to detect abi based on directory structure.
            if (isSystemApp(pkg) && !pkg.isUpdatedSystemApp() &&
                    pkg.applicationInfo.primaryCpuAbi == null) {
                setBundledAppAbisAndRoots(pkg, pkgSetting);
                setNativeLibraryPaths(pkg);
            }

        } else {
            if ((scanFlags & SCAN_MOVE) != 0) {
                // We haven't run dex-opt for this move (since we've moved the compiled output too)
                // but we already have this packages package info in the PackageSetting. We just
                // use that and derive the native library path based on the new codepath.
                pkg.applicationInfo.primaryCpuAbi = pkgSetting.primaryCpuAbiString;
                pkg.applicationInfo.secondaryCpuAbi = pkgSetting.secondaryCpuAbiString;
            }

            // Set native library paths again. For moves, the path will be updated based on the
            // ABIs we've determined above. For non-moves, the path will be updated based on the
            // ABIs we determined during compilation, but the path will depend on the final
            // package path (after the rename away from the stage path).
            setNativeLibraryPaths(pkg);
        }

        // This is a special case for the "system" package, where the ABI is
        // dictated by the zygote configuration (and init.rc). We should keep track
        // of this ABI so that we can deal with "normal" applications that run under
        // the same UID correctly.
        if (mPlatformPackage == pkg) {
            pkg.applicationInfo.primaryCpuAbi = VMRuntime.getRuntime().is64Bit() ?
                    Build.SUPPORTED_64_BIT_ABIS[0] : Build.SUPPORTED_32_BIT_ABIS[0];
        }

        // If there's a mismatch between the abi-override in the package setting
        // and the abiOverride specified for the install. Warn about this because we
        // would've already compiled the app without taking the package setting into
        // account.
        if ((scanFlags & SCAN_NO_DEX) == 0 && (scanFlags & SCAN_NEW_INSTALL) != 0) {
            if (cpuAbiOverride == null && pkgSetting.cpuAbiOverrideString != null) {
                Slog.w(TAG, "Ignoring persisted ABI override " + cpuAbiOverride +
                        " for package " + pkg.packageName);
            }
        }

        pkgSetting.primaryCpuAbiString = pkg.applicationInfo.primaryCpuAbi;
        pkgSetting.secondaryCpuAbiString = pkg.applicationInfo.secondaryCpuAbi;
        pkgSetting.cpuAbiOverrideString = cpuAbiOverride;

        // Copy the derived override back to the parsed package, so that we can
        // update the package settings accordingly.
        pkg.cpuAbiOverride = cpuAbiOverride;

        if (DEBUG_ABI_SELECTION) {
            Slog.d(TAG, "Resolved nativeLibraryRoot for " + pkg.applicationInfo.packageName
                    + " to root=" + pkg.applicationInfo.nativeLibraryRootDir + ", isa="
                    + pkg.applicationInfo.nativeLibraryRootRequiresIsa);
        }

        // Push the derived path down into PackageSettings so we know what to
        // clean up at uninstall time.
        pkgSetting.legacyNativeLibraryPathString = pkg.applicationInfo.nativeLibraryRootDir;

        if (DEBUG_ABI_SELECTION) {
            Log.d(TAG, "Abis for package[" + pkg.packageName + "] are" +
                    " primary=" + pkg.applicationInfo.primaryCpuAbi +
                    " secondary=" + pkg.applicationInfo.secondaryCpuAbi);
        }

        if ((scanFlags & SCAN_BOOTING) == 0 && pkgSetting.sharedUser != null) {
            // We don't do this here during boot because we can do it all
            // at once after scanning all existing packages.
            //
            // We also do this *before* we perform dexopt on this package, so that
            // we can avoid redundant dexopts, and also to make sure we've got the
            // code and package path correct.
            adjustCpuAbisForSharedUserLPw(pkgSetting.sharedUser.packages,
                    pkg, true /* boot complete */);
        }

        if (mFactoryTest && pkg.requestedPermissions.contains(
                android.Manifest.permission.FACTORY_TEST)) {
            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_FACTORY_TEST;
        }

        ArrayList<PackageParser.Package> clientLibPkgs = null;

        if ((scanFlags & SCAN_CHECK_ONLY) != 0) {
            if (nonMutatedPs != null) {
                synchronized (mPackages) {
                    mSettings.mPackages.put(nonMutatedPs.name, nonMutatedPs);
                }
            }
            return pkg;
        }

        // Only privileged apps and updated privileged apps can add child packages.
        if (pkg.childPackages != null && !pkg.childPackages.isEmpty()) {
            if ((policyFlags & PARSE_IS_PRIVILEGED) == 0) {
                throw new PackageManagerException("Only privileged apps and updated "
                        + "privileged apps can add child packages. Ignoring package "
                        + pkg.packageName);
            }
            final int childCount = pkg.childPackages.size();
            for (int i = 0; i < childCount; i++) {
                PackageParser.Package childPkg = pkg.childPackages.get(i);
                if (mSettings.hasOtherDisabledSystemPkgWithChildLPr(pkg.packageName,
                        childPkg.packageName)) {
                    throw new PackageManagerException("Cannot override a child package of "
                            + "another disabled system app. Ignoring package " + pkg.packageName);
                }
            }
        }

        // writer
        synchronized (mPackages) {
            if ((pkg.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) != 0) {
                // Only system apps can add new shared libraries.
                if (pkg.libraryNames != null) {
                    for (int i=0; i<pkg.libraryNames.size(); i++) {
                        String name = pkg.libraryNames.get(i);
                        boolean allowed = false;
                        if (pkg.isUpdatedSystemApp()) {
                            // New library entries can only be added through the
                            // system image.  This is important to get rid of a lot
                            // of nasty edge cases: for example if we allowed a non-
                            // system update of the app to add a library, then uninstalling
                            // the update would make the library go away, and assumptions
                            // we made such as through app install filtering would now
                            // have allowed apps on the device which aren't compatible
                            // with it.  Better to just have the restriction here, be
                            // conservative, and create many fewer cases that can negatively
                            // impact the user experience.
                            final PackageSetting sysPs = mSettings
                                    .getDisabledSystemPkgLPr(pkg.packageName);
                            if (sysPs.pkg != null && sysPs.pkg.libraryNames != null) {
                                for (int j=0; j<sysPs.pkg.libraryNames.size(); j++) {
                                    if (name.equals(sysPs.pkg.libraryNames.get(j))) {
                                        allowed = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            allowed = true;
                        }
                        if (allowed) {
                            if (!mSharedLibraries.containsKey(name)) {
                                mSharedLibraries.put(name, new SharedLibraryEntry(null, pkg.packageName));
                            } else if (!name.equals(pkg.packageName)) {
                                Slog.w(TAG, "Package " + pkg.packageName + " library "
                                        + name + " already exists; skipping");
                            }
                        } else {
                            Slog.w(TAG, "Package " + pkg.packageName + " declares lib "
                                    + name + " that is not declared on system image; skipping");
                        }
                    }
                    if ((scanFlags & SCAN_BOOTING) == 0) {
                        // If we are not booting, we need to update any applications
                        // that are clients of our shared library.  If we are booting,
                        // this will all be done once the scan is complete.
                        clientLibPkgs = updateAllSharedLibrariesLPw(pkg);
                    }
                }
            }
        }

        if ((scanFlags & SCAN_BOOTING) != 0) {
            // No apps can run during boot scan, so they don't need to be frozen
        } else if ((scanFlags & SCAN_DONT_KILL_APP) != 0) {
            // Caller asked to not kill app, so it's probably not frozen
        } else if ((scanFlags & SCAN_IGNORE_FROZEN) != 0) {
            // Caller asked us to ignore frozen check for some reason; they
            // probably didn't know the package name
        } else {
            // We're doing major surgery on this package, so it better be frozen
            // right now to keep it from launching
            checkPackageFrozen(pkgName);
        }

        // Also need to kill any apps that are dependent on the library.
        if (clientLibPkgs != null) {
            for (int i=0; i<clientLibPkgs.size(); i++) {
                PackageParser.Package clientPkg = clientLibPkgs.get(i);
                killApplication(clientPkg.applicationInfo.packageName,
                        clientPkg.applicationInfo.uid, "update lib");
            }
        }

        // Make sure we're not adding any bogus keyset info
        KeySetManagerService ksms = mSettings.mKeySetManagerService;
        ksms.assertScannedPackageValid(pkg);

        // writer
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "updateSettings");

        boolean createIdmapFailed = false;
        synchronized (mPackages) {
            // We don't expect installation to fail beyond this point

            if (pkgSetting.pkg != null) {
                // Note that |user| might be null during the initial boot scan. If a codePath
                // for an app has changed during a boot scan, it's due to an app update that's
                // part of the system partition and marker changes must be applied to all users.
                maybeRenameForeignDexMarkers(pkgSetting.pkg, pkg,
                    (user != null) ? user : UserHandle.ALL);
            }

            // Add the new setting to mSettings
            mSettings.insertPackageSettingLPw(pkgSetting, pkg);
            // Add the new setting to mPackages
            mPackages.put(pkg.applicationInfo.packageName, pkg);
            // Make sure we don't accidentally delete its data.
            final Iterator<PackageCleanItem> iter = mSettings.mPackagesToBeCleaned.iterator();
            while (iter.hasNext()) {
                PackageCleanItem item = iter.next();
                if (pkgName.equals(item.packageName)) {
                    iter.remove();
                }
            }

            // Take care of first install / last update times.
            if (currentTime != 0) {
                if (pkgSetting.firstInstallTime == 0) {
                    pkgSetting.firstInstallTime = pkgSetting.lastUpdateTime = currentTime;
                } else if ((scanFlags&SCAN_UPDATE_TIME) != 0) {
                    pkgSetting.lastUpdateTime = currentTime;
                }
            } else if (pkgSetting.firstInstallTime == 0) {
                // We need *something*.  Take time time stamp of the file.
                pkgSetting.firstInstallTime = pkgSetting.lastUpdateTime = scanFileTime;
            } else if ((policyFlags&PackageParser.PARSE_IS_SYSTEM_DIR) != 0) {
                if (scanFileTime != pkgSetting.timeStamp) {
                    // A package on the system image has changed; consider this
                    // to be an update.
                    pkgSetting.lastUpdateTime = scanFileTime;
                }
            }

            // Add the package's KeySets to the global KeySetManagerService
            ksms.addScannedPackageLPw(pkg);

            int N = pkg.providers.size();
            StringBuilder r = null;
            int i;
            for (i=0; i<N; i++) {
                PackageParser.Provider p = pkg.providers.get(i);
                p.info.processName = fixProcessName(pkg.applicationInfo.processName,
                        p.info.processName, pkg.applicationInfo.uid);
                mProviders.addProvider(p);
                p.syncable = p.info.isSyncable;
                if (p.info.authority != null) {
                    String names[] = p.info.authority.split(";");
                    p.info.authority = null;
                    for (int j = 0; j < names.length; j++) {
                        if (j == 1 && p.syncable) {
                            // We only want the first authority for a provider to possibly be
                            // syncable, so if we already added this provider using a different
                            // authority clear the syncable flag. We copy the provider before
                            // changing it because the mProviders object contains a reference
                            // to a provider that we don't want to change.
                            // Only do this for the second authority since the resulting provider
                            // object can be the same for all future authorities for this provider.
                            p = new PackageParser.Provider(p);
                            p.syncable = false;
                        }
                        if (!mProvidersByAuthority.containsKey(names[j])) {
                            mProvidersByAuthority.put(names[j], p);
                            if (p.info.authority == null) {
                                p.info.authority = names[j];
                            } else {
                                p.info.authority = p.info.authority + ";" + names[j];
                            }
                            if (DEBUG_PACKAGE_SCANNING) {
                                if ((policyFlags & PackageParser.PARSE_CHATTY) != 0)
                                    Log.d(TAG, "Registered content provider: " + names[j]
                                            + ", className = " + p.info.name + ", isSyncable = "
                                            + p.info.isSyncable);
                            }
                        } else {
                            PackageParser.Provider other = mProvidersByAuthority.get(names[j]);
                            Slog.w(TAG, "Skipping provider name " + names[j] +
                                    " (in package " + pkg.applicationInfo.packageName +
                                    "): name already used by "
                                    + ((other != null && other.getComponentName() != null)
                                            ? other.getComponentName().getPackageName() : "?"));
                        }
                    }
                }
                if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append(p.info.name);
                }
            }
            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Providers: " + r);
            }

            N = pkg.services.size();
            r = null;
            for (i=0; i<N; i++) {
                PackageParser.Service s = pkg.services.get(i);
                s.info.processName = fixProcessName(pkg.applicationInfo.processName,
                        s.info.processName, pkg.applicationInfo.uid);
                mServices.addService(s);
                if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append(s.info.name);
                }
            }
            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Services: " + r);
            }

            N = pkg.receivers.size();
            r = null;
            for (i=0; i<N; i++) {
                PackageParser.Activity a = pkg.receivers.get(i);
                a.info.processName = fixProcessName(pkg.applicationInfo.processName,
                        a.info.processName, pkg.applicationInfo.uid);
                mReceivers.addActivity(a, "receiver");
                if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append(a.info.name);
                }
            }
            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Receivers: " + r);
            }

            N = pkg.activities.size();
            r = null;
            for (i=0; i<N; i++) {
                PackageParser.Activity a = pkg.activities.get(i);
                a.info.processName = fixProcessName(pkg.applicationInfo.processName,
                        a.info.processName, pkg.applicationInfo.uid);
                mActivities.addActivity(a, "activity");
                if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append(a.info.name);
                }
            }
            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Activities: " + r);
            }

            N = pkg.permissionGroups.size();
            r = null;
            for (i=0; i<N; i++) {
                PackageParser.PermissionGroup pg = pkg.permissionGroups.get(i);
                PackageParser.PermissionGroup cur = mPermissionGroups.get(pg.info.name);
                if (cur == null) {
                    mPermissionGroups.put(pg.info.name, pg);
                    if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                        if (r == null) {
                            r = new StringBuilder(256);
                        } else {
                            r.append(' ');
                        }
                        r.append(pg.info.name);
                    }
                } else {
                    Slog.w(TAG, "Permission group " + pg.info.name + " from package "
                            + pg.info.packageName + " ignored: original from "
                            + cur.info.packageName);
                    if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                        if (r == null) {
                            r = new StringBuilder(256);
                        } else {
                            r.append(' ');
                        }
                        r.append("DUP:");
                        r.append(pg.info.name);
                    }
                }
            }
            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Permission Groups: " + r);
            }

            N = pkg.permissions.size();
            r = null;
            for (i=0; i<N; i++) {
                PackageParser.Permission p = pkg.permissions.get(i);

                // Assume by default that we did not install this permission into the system.
                p.info.flags &= ~PermissionInfo.FLAG_INSTALLED;

                // Now that permission groups have a special meaning, we ignore permission
                // groups for legacy apps to prevent unexpected behavior. In particular,
                // permissions for one app being granted to someone just becase they happen
                // to be in a group defined by another app (before this had no implications).
                if (pkg.applicationInfo.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1) {
                    p.group = mPermissionGroups.get(p.info.group);
                    // Warn for a permission in an unknown group.
                    if (p.info.group != null && p.group == null) {
                        Slog.w(TAG, "Permission " + p.info.name + " from package "
                                + p.info.packageName + " in an unknown group " + p.info.group);
                    }
                }

                ArrayMap<String, BasePermission> permissionMap =
                        p.tree ? mSettings.mPermissionTrees
                                : mSettings.mPermissions;
                BasePermission bp = permissionMap.get(p.info.name);

                // Allow system apps to redefine non-system permissions
                if (bp != null && !Objects.equals(bp.sourcePackage, p.info.packageName)) {
                    final boolean currentOwnerIsSystem = (bp.perm != null
                            && isSystemApp(bp.perm.owner));
                    if (isSystemApp(p.owner)) {
                        if (bp.type == BasePermission.TYPE_BUILTIN && bp.perm == null) {
                            // It's a built-in permission and no owner, take ownership now
                            bp.packageSetting = pkgSetting;
                            bp.perm = p;
                            bp.uid = pkg.applicationInfo.uid;
                            bp.sourcePackage = p.info.packageName;
                            bp.allowViaWhitelist = p.info.allowViaWhitelist;
                            p.info.flags |= PermissionInfo.FLAG_INSTALLED;
                        } else if (!currentOwnerIsSystem) {
                            String msg = "New decl " + p.owner + " of permission  "
                                    + p.info.name + " is system; overriding " + bp.sourcePackage;
                            reportSettingsProblem(Log.WARN, msg);
                            bp = null;
                        }
                    }
                }

                if (bp == null) {
                    bp = new BasePermission(p.info.name, p.info.packageName,
                            BasePermission.TYPE_NORMAL);
                    bp.allowViaWhitelist = p.info.allowViaWhitelist;
                    permissionMap.put(p.info.name, bp);
                }

                if (bp.perm == null) {
                    if (bp.sourcePackage == null
                            || bp.sourcePackage.equals(p.info.packageName)) {
                        BasePermission tree = findPermissionTreeLP(p.info.name);
                        if (tree == null
                                || tree.sourcePackage.equals(p.info.packageName)) {
                            bp.packageSetting = pkgSetting;
                            bp.perm = p;
                            bp.uid = pkg.applicationInfo.uid;
                            bp.sourcePackage = p.info.packageName;
                            bp.allowViaWhitelist = p.info.allowViaWhitelist;
                            p.info.flags |= PermissionInfo.FLAG_INSTALLED;
                            if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                                if (r == null) {
                                    r = new StringBuilder(256);
                                } else {
                                    r.append(' ');
                                }
                                r.append(p.info.name);
                            }
                        } else {
                            Slog.w(TAG, "Permission " + p.info.name + " from package "
                                    + p.info.packageName + " ignored: base tree "
                                    + tree.name + " is from package "
                                    + tree.sourcePackage);
                        }
                    } else {
                        Slog.w(TAG, "Permission " + p.info.name + " from package "
                                + p.info.packageName + " ignored: original from "
                                + bp.sourcePackage);
                    }
                } else if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append("DUP:");
                    r.append(p.info.name);
                }
                if (bp.perm == p) {
                    bp.protectionLevel = p.info.protectionLevel;
                }
            }

            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Permissions: " + r);
            }

            N = pkg.instrumentation.size();
            r = null;
            for (i=0; i<N; i++) {
                PackageParser.Instrumentation a = pkg.instrumentation.get(i);
                a.info.packageName = pkg.applicationInfo.packageName;
                a.info.sourceDir = pkg.applicationInfo.sourceDir;
                a.info.publicSourceDir = pkg.applicationInfo.publicSourceDir;
                a.info.splitSourceDirs = pkg.applicationInfo.splitSourceDirs;
                a.info.splitPublicSourceDirs = pkg.applicationInfo.splitPublicSourceDirs;
                a.info.dataDir = pkg.applicationInfo.dataDir;
                a.info.deviceProtectedDataDir = pkg.applicationInfo.deviceProtectedDataDir;
                a.info.credentialProtectedDataDir = pkg.applicationInfo.credentialProtectedDataDir;

                a.info.nativeLibraryDir = pkg.applicationInfo.nativeLibraryDir;
                a.info.secondaryNativeLibraryDir = pkg.applicationInfo.secondaryNativeLibraryDir;
                mInstrumentation.put(a.getComponentName(), a);
                if ((policyFlags&PackageParser.PARSE_CHATTY) != 0) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append(a.info.name);
                }
            }
            if (r != null) {
                if (DEBUG_PACKAGE_SCANNING) Log.d(TAG, "  Instrumentation: " + r);
            }

            if (pkg.protectedBroadcasts != null) {
                N = pkg.protectedBroadcasts.size();
                for (i=0; i<N; i++) {
                    mProtectedBroadcasts.put(pkg.protectedBroadcasts.keyAt(i),
                            pkg.protectedBroadcasts.valueAt(i));
                }
            }

            pkgSetting.setTimeStamp(scanFileTime);

            // Create idmap files for pairs of (packages, overlay packages).
            // Note: "android", ie framework-res.apk, is handled by native layers.
            if (pkg.mOverlayTarget != null) {
                // This is an overlay package.
                if (pkg.mOverlayTarget != null && !pkg.mOverlayTarget.equals("android")) {
                    if (!mOverlays.containsKey(pkg.mOverlayTarget)) {
                        mOverlays.put(pkg.mOverlayTarget,
                                new ArrayMap<String, PackageParser.Package>());
                    }
                    ArrayMap<String, PackageParser.Package> map = mOverlays.get(pkg.mOverlayTarget);
                    map.put(pkg.packageName, pkg);
                    PackageParser.Package orig = mPackages.get(pkg.mOverlayTarget);
                    if (orig != null && !createIdmapForPackagePairLI(orig, pkg)) {
                        createIdmapFailed = true;
                    }
                }
            } else if (mOverlays.containsKey(pkg.packageName) &&
                    !pkg.packageName.equals("android")) {
                // This is a regular package, with one or more known overlay packages.
                createIdmapsForPackageLI(pkg);
            }
        }

        Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);

        if (createIdmapFailed) {
            throw new PackageManagerException(INSTALL_FAILED_UPDATE_INCOMPATIBLE,
                    "scanPackageLI failed to createIdmap");
        }
        return pkg;
    }

    private void maybeRenameForeignDexMarkers(PackageParser.Package existing,
            PackageParser.Package update, UserHandle user) {
        if (existing.applicationInfo == null || update.applicationInfo == null) {
            // This isn't due to an app installation.
            return;
        }

        final File oldCodePath = new File(existing.applicationInfo.getCodePath());
        final File newCodePath = new File(update.applicationInfo.getCodePath());

        // The codePath hasn't changed, so there's nothing for us to do.
        if (Objects.equals(oldCodePath, newCodePath)) {
            return;
        }

        File canonicalNewCodePath;
        try {
            canonicalNewCodePath = new File(PackageManagerServiceUtils.realpath(newCodePath));
        } catch (IOException e) {
            Slog.w(TAG, "Failed to get canonical path.", e);
            return;
        }

        // This is a bit of a hack. The oldCodePath doesn't exist at this point (because
        // we've already renamed / deleted it) so we cannot call realpath on it. Here we assume
        // that the last component of the path (i.e, the name) doesn't need canonicalization
        // (i.e, that it isn't ".", ".." or a symbolic link). This is a valid assumption for now
        // but may change in the future. Hopefully this function won't exist at that point.
        final File canonicalOldCodePath = new File(canonicalNewCodePath.getParentFile(),
                oldCodePath.getName());

        // Calculate the prefixes of the markers. These are just the paths with "/" replaced
        // with "@".
        String oldMarkerPrefix = canonicalOldCodePath.getAbsolutePath().replace('/', '@');
        if (!oldMarkerPrefix.endsWith("@")) {
            oldMarkerPrefix += "@";
        }
        String newMarkerPrefix = canonicalNewCodePath.getAbsolutePath().replace('/', '@');
        if (!newMarkerPrefix.endsWith("@")) {
            newMarkerPrefix += "@";
        }

        List<String> updatedPaths = update.getAllCodePathsExcludingResourceOnly();
        List<String> markerSuffixes = new ArrayList<String>(updatedPaths.size());
        for (String updatedPath : updatedPaths) {
            String updatedPathName = new File(updatedPath).getName();
            markerSuffixes.add(updatedPathName.replace('/', '@'));
        }

        for (int userId : resolveUserIds(user.getIdentifier())) {
            File profileDir = Environment.getDataProfilesDeForeignDexDirectory(userId);

            for (String markerSuffix : markerSuffixes) {
                File oldForeignUseMark = new File(profileDir, oldMarkerPrefix + markerSuffix);
                File newForeignUseMark = new File(profileDir, newMarkerPrefix + markerSuffix);
                if (oldForeignUseMark.exists()) {
                    try {
                        Os.rename(oldForeignUseMark.getAbsolutePath(),
                                newForeignUseMark.getAbsolutePath());
                    } catch (ErrnoException e) {
                        Slog.w(TAG, "Failed to rename foreign use marker", e);
                        oldForeignUseMark.delete();
                    }
                }
            }
        }
    }

    /**
     * Derive the ABI of a non-system package located at {@code scanFile}. This information
     * is derived purely on the basis of the contents of {@code scanFile} and
     * {@code cpuAbiOverride}.
     *
     * If {@code extractLibs} is true, native libraries are extracted from the app if required.
     */
    private void derivePackageAbi(PackageParser.Package pkg, File scanFile,
                                 String cpuAbiOverride, boolean extractLibs)
            throws PackageManagerException {
        // TODO: We can probably be smarter about this stuff. For installed apps,
        // we can calculate this information at install time once and for all. For
        // system apps, we can probably assume that this information doesn't change
        // after the first boot scan. As things stand, we do lots of unnecessary work.

        // Give ourselves some initial paths; we'll come back for another
        // pass once we've determined ABI below.
        setNativeLibraryPaths(pkg);

        // We would never need to extract libs for forward-locked and external packages,
        // since the container service will do it for us. We shouldn't attempt to
        // extract libs from system app when it was not updated.
        if (pkg.isForwardLocked() || pkg.applicationInfo.isExternalAsec() ||
                (isSystemApp(pkg) && !pkg.isUpdatedSystemApp())) {
            extractLibs = false;
        }

        final String nativeLibraryRootStr = pkg.applicationInfo.nativeLibraryRootDir;
        final boolean useIsaSpecificSubdirs = pkg.applicationInfo.nativeLibraryRootRequiresIsa;

        NativeLibraryHelper.Handle handle = null;
        try {
            handle = NativeLibraryHelper.Handle.create(pkg);
            // TODO(multiArch): This can be null for apps that didn't go through the
            // usual installation process. We can calculate it again, like we
            // do during install time.
            //
            // TODO(multiArch): Why do we need to rescan ASEC apps again ? It seems totally
            // unnecessary.
            final File nativeLibraryRoot = new File(nativeLibraryRootStr);

            // Null out the abis so that they can be recalculated.
            pkg.applicationInfo.primaryCpuAbi = null;
            pkg.applicationInfo.secondaryCpuAbi = null;
            if (isMultiArch(pkg.applicationInfo)) {
                // Warn if we've set an abiOverride for multi-lib packages..
                // By definition, we need to copy both 32 and 64 bit libraries for
                // such packages.
                if (pkg.cpuAbiOverride != null
                        && !NativeLibraryHelper.CLEAR_ABI_OVERRIDE.equals(pkg.cpuAbiOverride)) {
                    Slog.w(TAG, "Ignoring abiOverride for multi arch application.");
                }

                int abi32 = PackageManager.NO_NATIVE_LIBRARIES;
                int abi64 = PackageManager.NO_NATIVE_LIBRARIES;
                if (Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                    if (extractLibs) {
                        abi32 = NativeLibraryHelper.copyNativeBinariesForSupportedAbi(handle,
                                nativeLibraryRoot, Build.SUPPORTED_32_BIT_ABIS,
                                useIsaSpecificSubdirs);
                    } else {
                        abi32 = NativeLibraryHelper.findSupportedAbi(handle, Build.SUPPORTED_32_BIT_ABIS);
                    }
                }

                maybeThrowExceptionForMultiArchCopy(
                        "Error unpackaging 32 bit native libs for multiarch app.", abi32);

                if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                    if (extractLibs) {
                        abi64 = NativeLibraryHelper.copyNativeBinariesForSupportedAbi(handle,
                                nativeLibraryRoot, Build.SUPPORTED_64_BIT_ABIS,
                                useIsaSpecificSubdirs);
                    } else {
                        abi64 = NativeLibraryHelper.findSupportedAbi(handle, Build.SUPPORTED_64_BIT_ABIS);
                    }
                }

                maybeThrowExceptionForMultiArchCopy(
                        "Error unpackaging 64 bit native libs for multiarch app.", abi64);

                if (abi64 >= 0) {
                    pkg.applicationInfo.primaryCpuAbi = Build.SUPPORTED_64_BIT_ABIS[abi64];
                }

                if (abi32 >= 0) {
                    final String abi = Build.SUPPORTED_32_BIT_ABIS[abi32];
                    if (abi64 >= 0) {
                        if (pkg.use32bitAbi) {
                            pkg.applicationInfo.secondaryCpuAbi = pkg.applicationInfo.primaryCpuAbi;
                            pkg.applicationInfo.primaryCpuAbi = abi;
                        } else {
                            pkg.applicationInfo.secondaryCpuAbi = abi;
                        }
                    } else {
                        pkg.applicationInfo.primaryCpuAbi = abi;
                    }
                }

            } else {
                String[] abiList = (cpuAbiOverride != null) ?
                        new String[] { cpuAbiOverride } : Build.SUPPORTED_ABIS;

                // Enable gross and lame hacks for apps that are built with old
                // SDK tools. We must scan their APKs for renderscript bitcode and
                // not launch them if it's present. Don't bother checking on devices
                // that don't have 64 bit support.
                boolean needsRenderScriptOverride = false;
                if (Build.SUPPORTED_64_BIT_ABIS.length > 0 && cpuAbiOverride == null &&
                        NativeLibraryHelper.hasRenderscriptBitcode(handle)) {
                    abiList = Build.SUPPORTED_32_BIT_ABIS;
                    needsRenderScriptOverride = true;
                }

                final int copyRet;
                if (extractLibs) {
                    copyRet = NativeLibraryHelper.copyNativeBinariesForSupportedAbi(handle,
                            nativeLibraryRoot, abiList, useIsaSpecificSubdirs);
                } else {
                    copyRet = NativeLibraryHelper.findSupportedAbi(handle, abiList);
                }

                if (copyRet < 0 && copyRet != PackageManager.NO_NATIVE_LIBRARIES) {
                    throw new PackageManagerException(INSTALL_FAILED_INTERNAL_ERROR,
                            "Error unpackaging native libs for app, errorCode=" + copyRet);
                }

                if (copyRet >= 0) {
                    pkg.applicationInfo.primaryCpuAbi = abiList[copyRet];
                } else if (copyRet == PackageManager.NO_NATIVE_LIBRARIES && cpuAbiOverride != null) {
                    pkg.applicationInfo.primaryCpuAbi = cpuAbiOverride;
                } else if (needsRenderScriptOverride) {
                    pkg.applicationInfo.primaryCpuAbi = abiList[0];
                }
            }
        } catch (IOException ioe) {
            Slog.e(TAG, "Unable to get canonical file " + ioe.toString());
        } finally {
            IoUtils.closeQuietly(handle);
        }

        // Now that we've calculated the ABIs and determined if it's an internal app,
        // we will go ahead and populate the nativeLibraryPath.
        setNativeLibraryPaths(pkg);
    }

    /**
     * Adjusts ABIs for a set of packages belonging to a shared user so that they all match.
     * i.e, so that all packages can be run inside a single process if required.
     *
     * Optionally, callers can pass in a parsed package via {@code newPackage} in which case
     * this function will either try and make the ABI for all packages in {@code packagesForUser}
     * match {@code scannedPackage} or will update the ABI of {@code scannedPackage} to match
     * the ABI selected for {@code packagesForUser}. This variant is used when installing or
     * updating a package that belongs to a shared user.
     *
     * NOTE: We currently only match for the primary CPU abi string. Matching the secondary
     * adds unnecessary complexity.
     */
    private void adjustCpuAbisForSharedUserLPw(Set<PackageSetting> packagesForUser,
            PackageParser.Package scannedPackage, boolean bootComplete) {
        String requiredInstructionSet = null;
        if (scannedPackage != null && scannedPackage.applicationInfo.primaryCpuAbi != null) {
            requiredInstructionSet = VMRuntime.getInstructionSet(
                     scannedPackage.applicationInfo.primaryCpuAbi);
        }

        PackageSetting requirer = null;
        for (PackageSetting ps : packagesForUser) {
            // If packagesForUser contains scannedPackage, we skip it. This will happen
            // when scannedPackage is an update of an existing package. Without this check,
            // we will never be able to change the ABI of any package belonging to a shared
            // user, even if it's compatible with other packages.
            if (scannedPackage == null || !scannedPackage.packageName.equals(ps.name)) {
                if (ps.primaryCpuAbiString == null) {
                    continue;
                }

                final String instructionSet = VMRuntime.getInstructionSet(ps.primaryCpuAbiString);
                if (requiredInstructionSet != null && !instructionSet.equals(requiredInstructionSet)) {
                    // We have a mismatch between instruction sets (say arm vs arm64) warn about
                    // this but there's not much we can do.
                    String errorMessage = "Instruction set mismatch, "
                            + ((requirer == null) ? "[caller]" : requirer)
                            + " requires " + requiredInstructionSet + " whereas " + ps
                            + " requires " + instructionSet;
                    Slog.w(TAG, errorMessage);
                }

                if (requiredInstructionSet == null) {
                    requiredInstructionSet = instructionSet;
                    requirer = ps;
                }
            }
        }

        if (requiredInstructionSet != null) {
            String adjustedAbi;
            if (requirer != null) {
                // requirer != null implies that either scannedPackage was null or that scannedPackage
                // did not require an ABI, in which case we have to adjust scannedPackage to match
                // the ABI of the set (which is the same as requirer's ABI)
                adjustedAbi = requirer.primaryCpuAbiString;
                if (scannedPackage != null) {
                    scannedPackage.applicationInfo.primaryCpuAbi = adjustedAbi;
                }
            } else {
                // requirer == null implies that we're updating all ABIs in the set to
                // match scannedPackage.
                adjustedAbi =  scannedPackage.applicationInfo.primaryCpuAbi;
            }

            for (PackageSetting ps : packagesForUser) {
                if (scannedPackage == null || !scannedPackage.packageName.equals(ps.name)) {
                    if (ps.primaryCpuAbiString != null) {
                        continue;
                    }

                    ps.primaryCpuAbiString = adjustedAbi;
                    if (ps.pkg != null && ps.pkg.applicationInfo != null &&
                            !TextUtils.equals(adjustedAbi, ps.pkg.applicationInfo.primaryCpuAbi)) {
                        ps.pkg.applicationInfo.primaryCpuAbi = adjustedAbi;

                        Slog.i(TAG, "Adjusting ABI for " + ps.name + " to " + adjustedAbi
                                + " (requirer="
                                + (requirer == null ? "null" : (requirer.pkg == null ? "null" : requirer.pkg.packageName))
                                + ", scannedPackage="
                                + (scannedPackage != null ? scannedPackage.packageName : "null")
                                + ")");

                        try {
                            mInstaller.rmdex(ps.codePathString,
                                    getDexCodeInstructionSet(getPreferredInstructionSet()));
                        } catch (InstallerException ignored) {
                        }
                    }
                }
            }
        }
    }

    private void setUpCustomResolverActivity(PackageParser.Package pkg) {
        synchronized (mPackages) {
            mResolverReplaced = true;
            // Set up information for custom user intent resolution activity.
            mResolveActivity.applicationInfo = pkg.applicationInfo;
            mResolveActivity.name = mCustomResolverComponentName.getClassName();
            mResolveActivity.packageName = pkg.applicationInfo.packageName;
            mResolveActivity.processName = pkg.applicationInfo.packageName;
            mResolveActivity.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
            mResolveActivity.flags = ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS |
                    ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS;
            mResolveActivity.theme = 0;
            mResolveActivity.exported = true;
            mResolveActivity.enabled = true;
            mResolveInfo.activityInfo = mResolveActivity;
            mResolveInfo.priority = 0;
            mResolveInfo.preferredOrder = 0;
            mResolveInfo.match = 0;
            mResolveComponentName = mCustomResolverComponentName;
            Slog.i(TAG, "Replacing default ResolverActivity with custom activity: " +
                    mResolveComponentName);
        }
    }

    private void setUpEphemeralInstallerActivityLP(ComponentName installerComponent) {
        final PackageParser.Package pkg = mPackages.get(installerComponent.getPackageName());

        // Set up information for ephemeral installer activity
        mEphemeralInstallerActivity.applicationInfo = pkg.applicationInfo;
        mEphemeralInstallerActivity.name = mEphemeralInstallerComponent.getClassName();
        mEphemeralInstallerActivity.packageName = pkg.applicationInfo.packageName;
        mEphemeralInstallerActivity.processName = pkg.applicationInfo.packageName;
        mEphemeralInstallerActivity.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
        mEphemeralInstallerActivity.flags = ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS |
                ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS;
        mEphemeralInstallerActivity.theme = 0;
        mEphemeralInstallerActivity.exported = true;
        mEphemeralInstallerActivity.enabled = true;
        mEphemeralInstallerInfo.activityInfo = mEphemeralInstallerActivity;
        mEphemeralInstallerInfo.priority = 0;
        mEphemeralInstallerInfo.preferredOrder = 0;
        mEphemeralInstallerInfo.match = 0;

        if (DEBUG_EPHEMERAL) {
            Slog.d(TAG, "Set ephemeral installer activity: " + mEphemeralInstallerComponent);
        }
    }

    private static String calculateBundledApkRoot(final String codePathString) {
        final File codePath = new File(codePathString);
        final File codeRoot;
        if (FileUtils.contains(Environment.getRootDirectory(), codePath)) {
            codeRoot = Environment.getRootDirectory();
        } else if (FileUtils.contains(Environment.getOemDirectory(), codePath)) {
            codeRoot = Environment.getOemDirectory();
        } else if (FileUtils.contains(Environment.getVendorDirectory(), codePath)) {
            codeRoot = Environment.getVendorDirectory();
        } else {
            // Unrecognized code path; take its top real segment as the apk root:
            // e.g. /something/app/blah.apk => /something
            try {
                File f = codePath.getCanonicalFile();
                File parent = f.getParentFile();    // non-null because codePath is a file
                File tmp;
                while ((tmp = parent.getParentFile()) != null) {
                    f = parent;
                    parent = tmp;
                }
                codeRoot = f;
                Slog.w(TAG, "Unrecognized code path "
                        + codePath + " - using " + codeRoot);
            } catch (IOException e) {
                // Can't canonicalize the code path -- shenanigans?
                Slog.w(TAG, "Can't canonicalize code path " + codePath);
                return Environment.getRootDirectory().getPath();
            }
        }
        return codeRoot.getPath();
    }

    /**
     * Derive and set the location of native libraries for the given package,
     * which varies depending on where and how the package was installed.
     */
    private void setNativeLibraryPaths(PackageParser.Package pkg) {
        final ApplicationInfo info = pkg.applicationInfo;
        final String codePath = pkg.codePath;
        final File codeFile = new File(codePath);
        final boolean bundledApp = info.isSystemApp() && !info.isUpdatedSystemApp();
        final boolean asecApp = info.isForwardLocked() || info.isExternalAsec();

        info.nativeLibraryRootDir = null;
        info.nativeLibraryRootRequiresIsa = false;
        info.nativeLibraryDir = null;
        info.secondaryNativeLibraryDir = null;

        if (isApkFile(codeFile)) {
            // Monolithic install
            if (bundledApp) {
                // If "/system/lib64/apkname" exists, assume that is the per-package
                // native library directory to use; otherwise use "/system/lib/apkname".
                final String apkRoot = calculateBundledApkRoot(info.sourceDir);
                final boolean is64Bit = VMRuntime.is64BitInstructionSet(
                        getPrimaryInstructionSet(info));

                // This is a bundled system app so choose the path based on the ABI.
                // if it's a 64 bit abi, use lib64 otherwise use lib32. Note that this
                // is just the default path.
                final String apkName = deriveCodePathName(codePath);
                final String libDir = is64Bit ? LIB64_DIR_NAME : LIB_DIR_NAME;
                info.nativeLibraryRootDir = Environment.buildPath(new File(apkRoot), libDir,
                        apkName).getAbsolutePath();

                if (info.secondaryCpuAbi != null) {
                    final String secondaryLibDir = is64Bit ? LIB_DIR_NAME : LIB64_DIR_NAME;
                    info.secondaryNativeLibraryDir = Environment.buildPath(new File(apkRoot),
                            secondaryLibDir, apkName).getAbsolutePath();
                }
            } else if (asecApp) {
                info.nativeLibraryRootDir = new File(codeFile.getParentFile(), LIB_DIR_NAME)
                        .getAbsolutePath();
            } else {
                final String apkName = deriveCodePathName(codePath);
                info.nativeLibraryRootDir = new File(mAppLib32InstallDir, apkName)
                        .getAbsolutePath();
            }

            info.nativeLibraryRootRequiresIsa = false;
            info.nativeLibraryDir = info.nativeLibraryRootDir;
        } else {
            // Cluster install
            info.nativeLibraryRootDir = new File(codeFile, LIB_DIR_NAME).getAbsolutePath();
            info.nativeLibraryRootRequiresIsa = true;

            info.nativeLibraryDir = new File(info.nativeLibraryRootDir,
                    getPrimaryInstructionSet(info)).getAbsolutePath();

            if (info.secondaryCpuAbi != null) {
                info.secondaryNativeLibraryDir = new File(info.nativeLibraryRootDir,
                        VMRuntime.getInstructionSet(info.secondaryCpuAbi)).getAbsolutePath();
            }
        }
    }

    /**
     * Calculate the abis and roots for a bundled app. These can uniquely
     * be determined from the contents of the system partition, i.e whether
     * it contains 64 or 32 bit shared libraries etc. We do not validate any
     * of this information, and instead assume that the system was built
     * sensibly.
     */
    private void setBundledAppAbisAndRoots(PackageParser.Package pkg,
                                           PackageSetting pkgSetting) {
        final String apkName = deriveCodePathName(pkg.applicationInfo.getCodePath());

        // If "/system/lib64/apkname" exists, assume that is the per-package
        // native library directory to use; otherwise use "/system/lib/apkname".
        final String apkRoot = calculateBundledApkRoot(pkg.applicationInfo.sourceDir);
        setBundledAppAbi(pkg, apkRoot, apkName);
        // pkgSetting might be null during rescan following uninstall of updates
        // to a bundled app, so accommodate that possibility.  The settings in
        // that case will be established later from the parsed package.
        //
        // If the settings aren't null, sync them up with what we've just derived.
        // note that apkRoot isn't stored in the package settings.
        if (pkgSetting != null) {
            pkgSetting.primaryCpuAbiString = pkg.applicationInfo.primaryCpuAbi;
            pkgSetting.secondaryCpuAbiString = pkg.applicationInfo.secondaryCpuAbi;
        }
    }

    /**
     * Deduces the ABI of a bundled app and sets the relevant fields on the
     * parsed pkg object.
     *
     * @param apkRoot the root of the installed apk, something like {@code /system} or {@code /oem}
     *        under which system libraries are installed.
     * @param apkName the name of the installed package.
     */
    private static void setBundledAppAbi(PackageParser.Package pkg, String apkRoot, String apkName) {
        final File codeFile = new File(pkg.codePath);

        final boolean has64BitLibs;
        final boolean has32BitLibs;
        if (isApkFile(codeFile)) {
            // Monolithic install
            has64BitLibs = (new File(apkRoot, new File(LIB64_DIR_NAME, apkName).getPath())).exists();
            has32BitLibs = (new File(apkRoot, new File(LIB_DIR_NAME, apkName).getPath())).exists();
        } else {
            // Cluster install
            final File rootDir = new File(codeFile, LIB_DIR_NAME);
            if (!ArrayUtils.isEmpty(Build.SUPPORTED_64_BIT_ABIS)
                    && !TextUtils.isEmpty(Build.SUPPORTED_64_BIT_ABIS[0])) {
                final String isa = VMRuntime.getInstructionSet(Build.SUPPORTED_64_BIT_ABIS[0]);
                has64BitLibs = (new File(rootDir, isa)).exists();
            } else {
                has64BitLibs = false;
            }
            if (!ArrayUtils.isEmpty(Build.SUPPORTED_32_BIT_ABIS)
                    && !TextUtils.isEmpty(Build.SUPPORTED_32_BIT_ABIS[0])) {
                final String isa = VMRuntime.getInstructionSet(Build.SUPPORTED_32_BIT_ABIS[0]);
                has32BitLibs = (new File(rootDir, isa)).exists();
            } else {
                has32BitLibs = false;
            }
        }

        if (has64BitLibs && !has32BitLibs) {
            // The package has 64 bit libs, but not 32 bit libs. Its primary
            // ABI should be 64 bit. We can safely assume here that the bundled
            // native libraries correspond to the most preferred ABI in the list.

            pkg.applicationInfo.primaryCpuAbi = Build.SUPPORTED_64_BIT_ABIS[0];
            pkg.applicationInfo.secondaryCpuAbi = null;
        } else if (has32BitLibs && !has64BitLibs) {
            // The package has 32 bit libs but not 64 bit libs. Its primary
            // ABI should be 32 bit.

            pkg.applicationInfo.primaryCpuAbi = Build.SUPPORTED_32_BIT_ABIS[0];
            pkg.applicationInfo.secondaryCpuAbi = null;
        } else if (has32BitLibs && has64BitLibs) {
            // The application has both 64 and 32 bit bundled libraries. We check
            // here that the app declares multiArch support, and warn if it doesn't.
            //
            // We will be lenient here and record both ABIs. The primary will be the
            // ABI that's higher on the list, i.e, a device that's configured to prefer
            // 64 bit apps will see a 64 bit primary ABI,

            if ((pkg.applicationInfo.flags & ApplicationInfo.FLAG_MULTIARCH) == 0) {
                Slog.e(TAG, "Package " + pkg + " has multiple bundled libs, but is not multiarch.");
            }

            if (VMRuntime.is64BitInstructionSet(getPreferredInstructionSet())) {
                pkg.applicationInfo.primaryCpuAbi = Build.SUPPORTED_64_BIT_ABIS[0];
                pkg.applicationInfo.secondaryCpuAbi = Build.SUPPORTED_32_BIT_ABIS[0];
            } else {
                pkg.applicationInfo.primaryCpuAbi = Build.SUPPORTED_32_BIT_ABIS[0];
                pkg.applicationInfo.secondaryCpuAbi = Build.SUPPORTED_64_BIT_ABIS[0];
            }
        } else {
            pkg.applicationInfo.primaryCpuAbi = null;
            pkg.applicationInfo.secondaryCpuAbi = null;
        }
    }

    private void killApplication(String pkgName, int appId, String reason) {
        killApplication(pkgName, appId, UserHandle.USER_ALL, reason);
    }

    private void killApplication(String pkgName, int appId, int userId, String reason) {
        // Request the ActivityManager to kill the process(only for existing packages)
        // so that we do not end up in a confused state while the user is still using the older
        // version of the application while the new one gets installed.
        final long token = Binder.clearCallingIdentity();
        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            if (am != null) {
                try {
                    am.killApplication(pkgName, appId, userId, reason);
                } catch (RemoteException e) {
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void removePackageLI(PackageParser.Package pkg, boolean chatty) {
        // Remove the parent package setting
        PackageSetting ps = (PackageSetting) pkg.mExtras;
        if (ps != null) {
            removePackageLI(ps, chatty);
        }
        // Remove the child package setting
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = pkg.childPackages.get(i);
            ps = (PackageSetting) childPkg.mExtras;
            if (ps != null) {
                removePackageLI(ps, chatty);
            }
        }
    }

    void removePackageLI(PackageSetting ps, boolean chatty) {
        if (DEBUG_INSTALL) {
            if (chatty)
                Log.d(TAG, "Removing package " + ps.name);
        }

        // writer
        synchronized (mPackages) {
            mPackages.remove(ps.name);
            final PackageParser.Package pkg = ps.pkg;
            if (pkg != null) {
                cleanPackageDataStructuresLILPw(pkg, chatty);
            }
        }
    }

    void removeInstalledPackageLI(PackageParser.Package pkg, boolean chatty) {
        if (DEBUG_INSTALL) {
            if (chatty)
                Log.d(TAG, "Removing package " + pkg.applicationInfo.packageName);
        }

        // writer
        synchronized (mPackages) {
            // Remove the parent package
            mPackages.remove(pkg.applicationInfo.packageName);
            cleanPackageDataStructuresLILPw(pkg, chatty);

            // Remove the child packages
            final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
            for (int i = 0; i < childCount; i++) {
                PackageParser.Package childPkg = pkg.childPackages.get(i);
                mPackages.remove(childPkg.applicationInfo.packageName);
                cleanPackageDataStructuresLILPw(childPkg, chatty);
            }
        }
    }

    void cleanPackageDataStructuresLILPw(PackageParser.Package pkg, boolean chatty) {
        int N = pkg.providers.size();
        StringBuilder r = null;
        int i;
        for (i=0; i<N; i++) {
            PackageParser.Provider p = pkg.providers.get(i);
            mProviders.removeProvider(p);
            if (p.info.authority == null) {

                /* There was another ContentProvider with this authority when
                 * this app was installed so this authority is null,
                 * Ignore it as we don't have to unregister the provider.
                 */
                continue;
            }
            String names[] = p.info.authority.split(";");
            for (int j = 0; j < names.length; j++) {
                if (mProvidersByAuthority.get(names[j]) == p) {
                    mProvidersByAuthority.remove(names[j]);
                    if (DEBUG_REMOVE) {
                        if (chatty)
                            Log.d(TAG, "Unregistered content provider: " + names[j]
                                    + ", className = " + p.info.name + ", isSyncable = "
                                    + p.info.isSyncable);
                    }
                }
            }
            if (DEBUG_REMOVE && chatty) {
                if (r == null) {
                    r = new StringBuilder(256);
                } else {
                    r.append(' ');
                }
                r.append(p.info.name);
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Providers: " + r);
        }

        N = pkg.services.size();
        r = null;
        for (i=0; i<N; i++) {
            PackageParser.Service s = pkg.services.get(i);
            mServices.removeService(s);
            if (chatty) {
                if (r == null) {
                    r = new StringBuilder(256);
                } else {
                    r.append(' ');
                }
                r.append(s.info.name);
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Services: " + r);
        }

        N = pkg.receivers.size();
        r = null;
        for (i=0; i<N; i++) {
            PackageParser.Activity a = pkg.receivers.get(i);
            mReceivers.removeActivity(a, "receiver");
            if (DEBUG_REMOVE && chatty) {
                if (r == null) {
                    r = new StringBuilder(256);
                } else {
                    r.append(' ');
                }
                r.append(a.info.name);
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Receivers: " + r);
        }

        N = pkg.activities.size();
        r = null;
        for (i=0; i<N; i++) {
            PackageParser.Activity a = pkg.activities.get(i);
            mActivities.removeActivity(a, "activity");
            if (DEBUG_REMOVE && chatty) {
                if (r == null) {
                    r = new StringBuilder(256);
                } else {
                    r.append(' ');
                }
                r.append(a.info.name);
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Activities: " + r);
        }

        N = pkg.permissions.size();
        r = null;
        for (i=0; i<N; i++) {
            PackageParser.Permission p = pkg.permissions.get(i);
            BasePermission bp = mSettings.mPermissions.get(p.info.name);
            if (bp == null) {
                bp = mSettings.mPermissionTrees.get(p.info.name);
            }
            if (bp != null && bp.perm == p) {
                bp.perm = null;
                if (DEBUG_REMOVE && chatty) {
                    if (r == null) {
                        r = new StringBuilder(256);
                    } else {
                        r.append(' ');
                    }
                    r.append(p.info.name);
                }
            }
            if ((p.info.protectionLevel&PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
                ArraySet<String> appOpPkgs = mAppOpPermissionPackages.get(p.info.name);
                if (appOpPkgs != null) {
                    appOpPkgs.remove(pkg.packageName);
                }
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Permissions: " + r);
        }

        N = pkg.requestedPermissions.size();
        r = null;
        for (i=0; i<N; i++) {
            String perm = pkg.requestedPermissions.get(i);
            BasePermission bp = mSettings.mPermissions.get(perm);
            if (bp != null && (bp.protectionLevel&PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
                ArraySet<String> appOpPkgs = mAppOpPermissionPackages.get(perm);
                if (appOpPkgs != null) {
                    appOpPkgs.remove(pkg.packageName);
                    if (appOpPkgs.isEmpty()) {
                        mAppOpPermissionPackages.remove(perm);
                    }
                }
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Permissions: " + r);
        }

        N = pkg.instrumentation.size();
        r = null;
        for (i=0; i<N; i++) {
            PackageParser.Instrumentation a = pkg.instrumentation.get(i);
            mInstrumentation.remove(a.getComponentName());
            if (DEBUG_REMOVE && chatty) {
                if (r == null) {
                    r = new StringBuilder(256);
                } else {
                    r.append(' ');
                }
                r.append(a.info.name);
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Instrumentation: " + r);
        }

        r = null;
        if ((pkg.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) != 0) {
            // Only system apps can hold shared libraries.
            if (pkg.libraryNames != null) {
                for (i=0; i<pkg.libraryNames.size(); i++) {
                    String name = pkg.libraryNames.get(i);
                    SharedLibraryEntry cur = mSharedLibraries.get(name);
                    if (cur != null && cur.apk != null && cur.apk.equals(pkg.packageName)) {
                        mSharedLibraries.remove(name);
                        if (DEBUG_REMOVE && chatty) {
                            if (r == null) {
                                r = new StringBuilder(256);
                            } else {
                                r.append(' ');
                            }
                            r.append(name);
                        }
                    }
                }
            }
        }
        if (r != null) {
            if (DEBUG_REMOVE) Log.d(TAG, "  Libraries: " + r);
        }
    }

    private static boolean hasPermission(PackageParser.Package pkgInfo, String perm) {
        for (int i=pkgInfo.permissions.size()-1; i>=0; i--) {
            if (pkgInfo.permissions.get(i).info.name.equals(perm)) {
                return true;
            }
        }
        return false;
    }

    static final int UPDATE_PERMISSIONS_ALL = 1<<0;
    static final int UPDATE_PERMISSIONS_REPLACE_PKG = 1<<1;
    static final int UPDATE_PERMISSIONS_REPLACE_ALL = 1<<2;

    private void updatePermissionsLPw(PackageParser.Package pkg, int flags) {
        // Update the parent permissions
        updatePermissionsLPw(pkg.packageName, pkg, flags);
        // Update the child permissions
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = pkg.childPackages.get(i);
            updatePermissionsLPw(childPkg.packageName, childPkg, flags);
        }
    }

    private void updatePermissionsLPw(String changingPkg, PackageParser.Package pkgInfo,
            int flags) {
        final String volumeUuid = (pkgInfo != null) ? getVolumeUuidForPackage(pkgInfo) : null;
        updatePermissionsLPw(changingPkg, pkgInfo, volumeUuid, flags);
    }

    private void updatePermissionsLPw(String changingPkg,
            PackageParser.Package pkgInfo, String replaceVolumeUuid, int flags) {
        // Make sure there are no dangling permission trees.
        Iterator<BasePermission> it = mSettings.mPermissionTrees.values().iterator();
        while (it.hasNext()) {
            final BasePermission bp = it.next();
            if (bp.packageSetting == null) {
                // We may not yet have parsed the package, so just see if
                // we still know about its settings.
                bp.packageSetting = mSettings.mPackages.get(bp.sourcePackage);
            }
            if (bp.packageSetting == null) {
                Slog.w(TAG, "Removing dangling permission tree: " + bp.name
                        + " from package " + bp.sourcePackage);
                it.remove();
            } else if (changingPkg != null && changingPkg.equals(bp.sourcePackage)) {
                if (pkgInfo == null || !hasPermission(pkgInfo, bp.name)) {
                    Slog.i(TAG, "Removing old permission tree: " + bp.name
                            + " from package " + bp.sourcePackage);
                    flags |= UPDATE_PERMISSIONS_ALL;
                    it.remove();
                }
            }
        }

        // Make sure all dynamic permissions have been assigned to a package,
        // and make sure there are no dangling permissions.
        it = mSettings.mPermissions.values().iterator();
        while (it.hasNext()) {
            final BasePermission bp = it.next();
            if (bp.type == BasePermission.TYPE_DYNAMIC) {
                if (DEBUG_SETTINGS) Log.v(TAG, "Dynamic permission: name="
                        + bp.name + " pkg=" + bp.sourcePackage
                        + " info=" + bp.pendingInfo);
                if (bp.packageSetting == null && bp.pendingInfo != null) {
                    final BasePermission tree = findPermissionTreeLP(bp.name);
                    if (tree != null && tree.perm != null) {
                        bp.packageSetting = tree.packageSetting;
                        bp.perm = new PackageParser.Permission(tree.perm.owner,
                                new PermissionInfo(bp.pendingInfo));
                        bp.perm.info.packageName = tree.perm.info.packageName;
                        bp.perm.info.name = bp.name;
                        bp.uid = tree.uid;
                    }
                }
            }
            if (bp.packageSetting == null) {
                // We may not yet have parsed the package, so just see if
                // we still know about its settings.
                bp.packageSetting = mSettings.mPackages.get(bp.sourcePackage);
            }
            if (bp.packageSetting == null) {
                Slog.w(TAG, "Removing dangling permission: " + bp.name
                        + " from package " + bp.sourcePackage);
                it.remove();
            } else if (changingPkg != null && changingPkg.equals(bp.sourcePackage)) {
                if (pkgInfo == null || !hasPermission(pkgInfo, bp.name)) {
                    Slog.i(TAG, "Removing old permission: " + bp.name
                            + " from package " + bp.sourcePackage);
                    flags |= UPDATE_PERMISSIONS_ALL;
                    it.remove();
                }
            }
        }

        // Now update the permissions for all packages, in particular
        // replace the granted permissions of the system packages.
        if ((flags&UPDATE_PERMISSIONS_ALL) != 0) {
            for (PackageParser.Package pkg : mPackages.values()) {
                if (pkg != pkgInfo) {
                    // Only replace for packages on requested volume
                    final String volumeUuid = getVolumeUuidForPackage(pkg);
                    final boolean replace = ((flags & UPDATE_PERMISSIONS_REPLACE_ALL) != 0)
                            && Objects.equals(replaceVolumeUuid, volumeUuid);
                    grantPermissionsLPw(pkg, replace, changingPkg);
                }
            }
        }

        if (pkgInfo != null) {
            // Only replace for packages on requested volume
            final String volumeUuid = getVolumeUuidForPackage(pkgInfo);
            final boolean replace = ((flags & UPDATE_PERMISSIONS_REPLACE_PKG) != 0)
                    && Objects.equals(replaceVolumeUuid, volumeUuid);
            grantPermissionsLPw(pkgInfo, replace, changingPkg);
        }
    }

    private void grantPermissionsLPw(PackageParser.Package pkg, boolean replace,
            String packageOfInterest) {
        // IMPORTANT: There are two types of permissions: install and runtime.
        // Install time permissions are granted when the app is installed to
        // all device users and users added in the future. Runtime permissions
        // are granted at runtime explicitly to specific users. Normal and signature
        // protected permissions are install time permissions. Dangerous permissions
        // are install permissions if the app's target SDK is Lollipop MR1 or older,
        // otherwise they are runtime permissions. This function does not manage
        // runtime permissions except for the case an app targeting Lollipop MR1
        // being upgraded to target a newer SDK, in which case dangerous permissions
        // are transformed from install time to runtime ones.

        final PackageSetting ps = (PackageSetting) pkg.mExtras;
        if (ps == null) {
            return;
        }

        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "grantPermissions");

        PermissionsState permissionsState = ps.getPermissionsState();
        PermissionsState origPermissions = permissionsState;

        final int[] currentUserIds = UserManagerService.getInstance().getUserIds();

        boolean runtimePermissionsRevoked = false;
        int[] changedRuntimePermissionUserIds = EMPTY_INT_ARRAY;

        boolean changedInstallPermission = false;

        if (replace) {
            ps.installPermissionsFixed = false;
            if (!ps.isSharedUser()) {
                origPermissions = new PermissionsState(permissionsState);
                permissionsState.reset();
            } else {
                // We need to know only about runtime permission changes since the
                // calling code always writes the install permissions state but
                // the runtime ones are written only if changed. The only cases of
                // changed runtime permissions here are promotion of an install to
                // runtime and revocation of a runtime from a shared user.
                changedRuntimePermissionUserIds = revokeUnusedSharedUserPermissionsLPw(
                        ps.sharedUser, UserManagerService.getInstance().getUserIds());
                if (!ArrayUtils.isEmpty(changedRuntimePermissionUserIds)) {
                    runtimePermissionsRevoked = true;
                }
            }
        }

        permissionsState.setGlobalGids(mGlobalGids);

        final int N = pkg.requestedPermissions.size();
        for (int i=0; i<N; i++) {
            final String name = pkg.requestedPermissions.get(i);
            final BasePermission bp = mSettings.mPermissions.get(name);

            if (DEBUG_INSTALL) {
                Log.i(TAG, "Package " + pkg.packageName + " checking " + name + ": " + bp);
            }

            if (bp == null || bp.packageSetting == null) {
                if (packageOfInterest == null || packageOfInterest.equals(pkg.packageName)) {
                    Slog.w(TAG, "Unknown permission " + name
                            + " in package " + pkg.packageName);
                }
                continue;
            }

            final String perm = bp.name;
            boolean allowedSig = false;
            int grant = GRANT_DENIED;

            // Keep track of app op permissions.
            if ((bp.protectionLevel & PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
                ArraySet<String> pkgs = mAppOpPermissionPackages.get(bp.name);
                if (pkgs == null) {
                    pkgs = new ArraySet<>();
                    mAppOpPermissionPackages.put(bp.name, pkgs);
                }
                pkgs.add(pkg.packageName);
            }

            final int level = bp.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
            final boolean appSupportsRuntimePermissions = pkg.applicationInfo.targetSdkVersion
                    >= Build.VERSION_CODES.M;
            switch (level) {
                case PermissionInfo.PROTECTION_NORMAL: {
                    // For all apps normal permissions are install time ones.
                    grant = GRANT_INSTALL;
                } break;

                case PermissionInfo.PROTECTION_DANGEROUS: {
                    // If a permission review is required for legacy apps we represent
                    // their permissions as always granted runtime ones since we need
                    // to keep the review required permission flag per user while an
                    // install permission's state is shared across all users.
                    if (!appSupportsRuntimePermissions && !Build.PERMISSIONS_REVIEW_REQUIRED) {
                        // For legacy apps dangerous permissions are install time ones.
                        grant = GRANT_INSTALL;
                    } else if (origPermissions.hasInstallPermission(bp.name)) {
                        // For legacy apps that became modern, install becomes runtime.
                        grant = GRANT_UPGRADE;
                    } else if (mPromoteSystemApps
                            && isSystemApp(ps)
                            && mExistingSystemPackages.contains(ps.name)) {
                        // For legacy system apps, install becomes runtime.
                        // We cannot check hasInstallPermission() for system apps since those
                        // permissions were granted implicitly and not persisted pre-M.
                        grant = GRANT_UPGRADE;
                    } else {
                        // For modern apps keep runtime permissions unchanged.
                        grant = GRANT_RUNTIME;
                    }
                } break;

                case PermissionInfo.PROTECTION_SIGNATURE: {
                    // For all apps signature permissions are install time ones.
                    allowedSig = grantSignaturePermission(perm, pkg, bp, origPermissions);
                    if (allowedSig) {
                        grant = GRANT_INSTALL;
                    }
                } break;
            }

            if (DEBUG_INSTALL) {
                Log.i(TAG, "Package " + pkg.packageName + " granting " + perm);
            }

            if (grant != GRANT_DENIED) {
                if (!isSystemApp(ps) && ps.installPermissionsFixed) {
                    // If this is an existing, non-system package, then
                    // we can't add any new permissions to it.
                    if (!allowedSig && !origPermissions.hasInstallPermission(perm)) {
                        // Except...  if this is a permission that was added
                        // to the platform (note: need to only do this when
                        // updating the platform).
                        if (!isNewPlatformPermissionForPackage(perm, pkg)) {
                            grant = GRANT_DENIED;
                        }
                    }
                }

                switch (grant) {
                    case GRANT_INSTALL: {
                        // Revoke this as runtime permission to handle the case of
                        // a runtime permission being downgraded to an install one.
                        // Also in permission review mode we keep dangerous permissions
                        // for legacy apps
                        for (int userId : UserManagerService.getInstance().getUserIds()) {
                            if (origPermissions.getRuntimePermissionState(
                                    bp.name, userId) != null) {
                                // Revoke the runtime permission and clear the flags.
                                origPermissions.revokeRuntimePermission(bp, userId);
                                origPermissions.updatePermissionFlags(bp, userId,
                                      PackageManager.MASK_PERMISSION_FLAGS, 0);
                                // If we revoked a permission permission, we have to write.
                                changedRuntimePermissionUserIds = ArrayUtils.appendInt(
                                        changedRuntimePermissionUserIds, userId);
                            }
                        }
                        // Grant an install permission.
                        if (permissionsState.grantInstallPermission(bp) !=
                                PermissionsState.PERMISSION_OPERATION_FAILURE) {
                            changedInstallPermission = true;
                        }
                    } break;

                    case GRANT_RUNTIME: {
                        // Grant previously granted runtime permissions.
                        for (int userId : UserManagerService.getInstance().getUserIds()) {
                            PermissionState permissionState = origPermissions
                                    .getRuntimePermissionState(bp.name, userId);
                            int flags = permissionState != null
                                    ? permissionState.getFlags() : 0;
                            if (origPermissions.hasRuntimePermission(bp.name, userId)) {
                                if (permissionsState.grantRuntimePermission(bp, userId) ==
                                        PermissionsState.PERMISSION_OPERATION_FAILURE) {
                                    // If we cannot put the permission as it was, we have to write.
                                    changedRuntimePermissionUserIds = ArrayUtils.appendInt(
                                            changedRuntimePermissionUserIds, userId);
                                }
                                // If the app supports runtime permissions no need for a review.
                                if (Build.PERMISSIONS_REVIEW_REQUIRED
                                        && appSupportsRuntimePermissions
                                        && (flags & PackageManager
                                                .FLAG_PERMISSION_REVIEW_REQUIRED) != 0) {
                                    flags &= ~PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
                                    // Since we changed the flags, we have to write.
                                    changedRuntimePermissionUserIds = ArrayUtils.appendInt(
                                            changedRuntimePermissionUserIds, userId);
                                }
                            } else if (Build.PERMISSIONS_REVIEW_REQUIRED
                                    && !appSupportsRuntimePermissions) {
                                // For legacy apps that need a permission review, every new
                                // runtime permission is granted but it is pending a review.
                                // We also need to review only platform defined runtime
                                // permissions as these are the only ones the platform knows
                                // how to disable the API to simulate revocation as legacy
                                // apps don't expect to run with revoked permissions.
                                if (PLATFORM_PACKAGE_NAME.equals(bp.sourcePackage)) {
                                    if ((flags & FLAG_PERMISSION_REVIEW_REQUIRED) == 0) {
                                        flags |= FLAG_PERMISSION_REVIEW_REQUIRED;
                                        // We changed the flags, hence have to write.
                                        changedRuntimePermissionUserIds = ArrayUtils.appendInt(
                                                changedRuntimePermissionUserIds, userId);
                                    }
                                }
                                if (permissionsState.grantRuntimePermission(bp, userId)
                                        != PermissionsState.PERMISSION_OPERATION_FAILURE) {
                                    // We changed the permission, hence have to write.
                                    changedRuntimePermissionUserIds = ArrayUtils.appendInt(
                                            changedRuntimePermissionUserIds, userId);
                                }
                            }
                            // Propagate the permission flags.
                            permissionsState.updatePermissionFlags(bp, userId, flags, flags);
                        }
                    } break;

                    case GRANT_UPGRADE: {
                        // Grant runtime permissions for a previously held install permission.
                        PermissionState permissionState = origPermissions
                                .getInstallPermissionState(bp.name);
                        final int flags = permissionState != null ? permissionState.getFlags() : 0;

                        if (origPermissions.revokeInstallPermission(bp)
                                != PermissionsState.PERMISSION_OPERATION_FAILURE) {
                            // We will be transferring the permission flags, so clear them.
                            origPermissions.updatePermissionFlags(bp, UserHandle.USER_ALL,
                                    PackageManager.MASK_PERMISSION_FLAGS, 0);
                            changedInstallPermission = true;
                        }

                        // If the permission is not to be promoted to runtime we ignore it and
                        // also its other flags as they are not applicable to install permissions.
                        if ((flags & PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE) == 0) {
                            for (int userId : currentUserIds) {
                                if (permissionsState.grantRuntimePermission(bp, userId) !=
                                        PermissionsState.PERMISSION_OPERATION_FAILURE) {
                                    // Transfer the permission flags.
                                    permissionsState.updatePermissionFlags(bp, userId,
                                            flags, flags);
                                    // If we granted the permission, we have to write.
                                    changedRuntimePermissionUserIds = ArrayUtils.appendInt(
                                            changedRuntimePermissionUserIds, userId);
                                }
                            }
                        }
                    } break;

                    default: {
                        if (packageOfInterest == null
                                || packageOfInterest.equals(pkg.packageName)) {
                            Slog.w(TAG, "Not granting permission " + perm
                                    + " to package " + pkg.packageName
                                    + " because it was previously installed without");
                        }
                    } break;
                }
            } else {
                if (permissionsState.revokeInstallPermission(bp) !=
                        PermissionsState.PERMISSION_OPERATION_FAILURE) {
                    // Also drop the permission flags.
                    permissionsState.updatePermissionFlags(bp, UserHandle.USER_ALL,
                            PackageManager.MASK_PERMISSION_FLAGS, 0);
                    changedInstallPermission = true;
                    Slog.i(TAG, "Un-granting permission " + perm
                            + " from package " + pkg.packageName
                            + " (protectionLevel=" + bp.protectionLevel
                            + " flags=0x" + Integer.toHexString(pkg.applicationInfo.flags)
                            + ")");
                } else if ((bp.protectionLevel&PermissionInfo.PROTECTION_FLAG_APPOP) == 0) {
                    // Don't print warning for app op permissions, since it is fine for them
                    // not to be granted, there is a UI for the user to decide.
                    if (packageOfInterest == null || packageOfInterest.equals(pkg.packageName)) {
                        Slog.w(TAG, "Not granting permission " + perm
                                + " to package " + pkg.packageName
                                + " (protectionLevel=" + bp.protectionLevel
                                + " flags=0x" + Integer.toHexString(pkg.applicationInfo.flags)
                                + ")");
                    }
                }
            }
        }

        if ((changedInstallPermission || replace) && !ps.installPermissionsFixed &&
                !isSystemApp(ps) || isUpdatedSystemApp(ps)){
            // This is the first that we have heard about this package, so the
            // permissions we have now selected are fixed until explicitly
            // changed.
            ps.installPermissionsFixed = true;
        }

        // Persist the runtime permissions state for users with changes. If permissions
        // were revoked because no app in the shared user declares them we have to
        // write synchronously to avoid losing runtime permissions state.
        for (int userId : changedRuntimePermissionUserIds) {
            mSettings.writeRuntimePermissionsForUserLPr(userId, runtimePermissionsRevoked);
        }

        Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
    }

    private boolean isNewPlatformPermissionForPackage(String perm, PackageParser.Package pkg) {
        boolean allowed = false;
        final int NP = PackageParser.NEW_PERMISSIONS.length;
        for (int ip=0; ip<NP; ip++) {
            final PackageParser.NewPermissionInfo npi
                    = PackageParser.NEW_PERMISSIONS[ip];
            if (npi.name.equals(perm)
                    && pkg.applicationInfo.targetSdkVersion < npi.sdkVersion) {
                allowed = true;
                Log.i(TAG, "Auto-granting " + perm + " to old pkg "
                        + pkg.packageName);
                break;
            }
        }
        return allowed;
    }

    private boolean grantSignaturePermission(String perm, PackageParser.Package pkg,
            BasePermission bp, PermissionsState origPermissions) {
        boolean allowed;
        allowed = (compareSignatures(
                bp.packageSetting.signatures.mSignatures, pkg.mSignatures)
                        == PackageManager.SIGNATURE_MATCH)
                || (compareSignatures(mPlatformPackage.mSignatures, pkg.mSignatures)
                        == PackageManager.SIGNATURE_MATCH);
        if (!allowed && (bp.protectionLevel
                & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0) {
            if (isSystemApp(pkg)) {
                // For updated system applications, a system permission
                if (pkg.isUpdatedSystemApp()) {
                    final PackageSetting sysPs = mSettings
                            .getDisabledSystemPkgLPr(pkg.packageName);
                    if (sysPs != null && sysPs.getPermissionsState().hasInstallPermission(perm)) {
                        // If the original was granted this permission, we take
                        // that grant decision as read and propagate it to the
                        // update.
                        if (sysPs.isPrivileged()) {
                            allowed = true;
                        }
                    } else {
                        // The system apk may have been updated with an older
                        // version of the one on the data partition, but which
                        // granted a new system permission that it didn't have
                        // before.  In this case we do want to allow the app to
                        // now get the new permission if the ancestral apk is
                        // privileged to get it.
                        if (sysPs != null && sysPs.pkg != null && sysPs.isPrivileged()) {
                            for (int j = 0; j < sysPs.pkg.requestedPermissions.size(); j++) {
                                if (perm.equals(sysPs.pkg.requestedPermissions.get(j))) {
                                    allowed = true;
                                    break;
                                }
                            }
                        }
                        // Also if a privileged parent package on the system image or any of
                        // its children requested a privileged permission, the updated child
                        // packages can also get the permission.
                        if (pkg.parentPackage != null) {
                            final PackageSetting disabledSysParentPs = mSettings
                                    .getDisabledSystemPkgLPr(pkg.parentPackage.packageName);
                            if (disabledSysParentPs != null && disabledSysParentPs.pkg != null
                                    && disabledSysParentPs.isPrivileged()) {
                                if (isPackageRequestingPermission(disabledSysParentPs.pkg, perm)) {
                                    allowed = true;
                                } else if (disabledSysParentPs.pkg.childPackages != null) {
                                    final int count = disabledSysParentPs.pkg.childPackages.size();
                                    for (int i = 0; i < count; i++) {
                                        PackageParser.Package disabledSysChildPkg =
                                                disabledSysParentPs.pkg.childPackages.get(i);
                                        if (isPackageRequestingPermission(disabledSysChildPkg,
                                                perm)) {
                                            allowed = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    allowed = isPrivilegedApp(pkg);
                }
            }
        }
        if (!allowed) {
            if (!allowed && (bp.protectionLevel
                    & PermissionInfo.PROTECTION_FLAG_PRE23) != 0
                    && pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                // If this was a previously normal/dangerous permission that got moved
                // to a system permission as part of the runtime permission redesign, then
                // we still want to blindly grant it to old apps.
                allowed = true;
            }
            if (!allowed && (bp.protectionLevel & PermissionInfo.PROTECTION_FLAG_INSTALLER) != 0
                    && pkg.packageName.equals(mRequiredInstallerPackage)) {
                // If this permission is to be granted to the system installer and
                // this app is an installer, then it gets the permission.
                allowed = true;
            }
            if (!allowed && (bp.protectionLevel & PermissionInfo.PROTECTION_FLAG_VERIFIER) != 0
                    && pkg.packageName.equals(mRequiredVerifierPackage)) {
                // If this permission is to be granted to the system verifier and
                // this app is a verifier, then it gets the permission.
                allowed = true;
            }
            if (!allowed && (bp.protectionLevel
                    & PermissionInfo.PROTECTION_FLAG_PREINSTALLED) != 0
                    && isSystemApp(pkg)) {
                // Any pre-installed system app is allowed to get this permission.
                allowed = true;
            }
            if (!allowed && (bp.protectionLevel
                    & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                // For development permissions, a development permission
                // is granted only if it was already granted.
                allowed = origPermissions.hasInstallPermission(perm);
            }
            if (!allowed && (bp.protectionLevel & PermissionInfo.PROTECTION_FLAG_SETUP) != 0
                    && pkg.packageName.equals(mSetupWizardPackage)) {
                // If this permission is to be granted to the system setup wizard and
                // this app is a setup wizard, then it gets the permission.
                allowed = true;
            }
        }
        if (!allowed && bp.allowViaWhitelist) {
            allowed = isAllowedSignature(pkg, perm);
        }
        return allowed;
    }

    private boolean isPackageRequestingPermission(PackageParser.Package pkg, String permission) {
        final int permCount = pkg.requestedPermissions.size();
        for (int j = 0; j < permCount; j++) {
            String requestedPermission = pkg.requestedPermissions.get(j);
            if (permission.equals(requestedPermission)) {
                return true;
            }
        }
        return false;
    }

    final class ActivityIntentResolver
            extends IntentResolver<PackageParser.ActivityIntentInfo, ResolveInfo> {
        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType,
                boolean defaultOnly, int userId) {
            if (!sUserManager.exists(userId)) return null;
            mFlags = defaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0;
            return super.queryIntent(intent, resolvedType, defaultOnly, userId);
        }

        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType, int flags,
                int userId) {
            if (!sUserManager.exists(userId)) return null;
            mFlags = flags;
            List<ResolveInfo> list = super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId);
            // Remove protected Application components if they're explicitly queried for.
            // Implicit intent queries will be gated when the returned component is acted upon.
            int callingUid = Binder.getCallingUid();
            String[] pkgArray = getPackagesForUid(callingUid);
            List<String> packages = pkgArray == null ? null : Arrays.asList(pkgArray);
            final boolean isNotSystem = callingUid != Process.SYSTEM_UID &&
                    (getFlagsForUid(callingUid) & ApplicationInfo.FLAG_SYSTEM) == 0;

            if (isNotSystem && intent.getComponent() != null) {
               Iterator<ResolveInfo> itr = list.iterator();
                while (itr.hasNext()) {
                    ActivityInfo activityInfo = itr.next().activityInfo;
                    if (activityInfo.applicationInfo.protect && (packages == null
                            || !packages.contains(activityInfo.packageName))) {
                        itr.remove();
                    }
                }
            }
            return list;
        }

        public List<ResolveInfo> queryIntentForPackage(Intent intent, String resolvedType,
                int flags, ArrayList<PackageParser.Activity> packageActivities, int userId) {
            if (!sUserManager.exists(userId)) return null;
            if (packageActivities == null) {
                return null;
            }
            mFlags = flags;
            final boolean defaultOnly = (flags&PackageManager.MATCH_DEFAULT_ONLY) != 0;
            final int N = packageActivities.size();
            ArrayList<PackageParser.ActivityIntentInfo[]> listCut =
                new ArrayList<PackageParser.ActivityIntentInfo[]>(N);

            ArrayList<PackageParser.ActivityIntentInfo> intentFilters;
            for (int i = 0; i < N; ++i) {
                intentFilters = packageActivities.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    PackageParser.ActivityIntentInfo[] array =
                            new PackageParser.ActivityIntentInfo[intentFilters.size()];
                    intentFilters.toArray(array);
                    listCut.add(array);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        /**
         * Finds a privileged activity that matches the specified activity names.
         */
        private PackageParser.Activity findMatchingActivity(
                List<PackageParser.Activity> activityList, ActivityInfo activityInfo) {
            for (PackageParser.Activity sysActivity : activityList) {
                if (sysActivity.info.name.equals(activityInfo.name)) {
                    return sysActivity;
                }
                if (sysActivity.info.name.equals(activityInfo.targetActivity)) {
                    return sysActivity;
                }
                if (sysActivity.info.targetActivity != null) {
                    if (sysActivity.info.targetActivity.equals(activityInfo.name)) {
                        return sysActivity;
                    }
                    if (sysActivity.info.targetActivity.equals(activityInfo.targetActivity)) {
                        return sysActivity;
                    }
                }
            }
            return null;
        }

        public class IterGenerator<E> {
            public Iterator<E> generate(ActivityIntentInfo info) {
                return null;
            }
        }

        public class ActionIterGenerator extends IterGenerator<String> {
            @Override
            public Iterator<String> generate(ActivityIntentInfo info) {
                return info.actionsIterator();
            }
        }

        public class CategoriesIterGenerator extends IterGenerator<String> {
            @Override
            public Iterator<String> generate(ActivityIntentInfo info) {
                return info.categoriesIterator();
            }
        }

        public class SchemesIterGenerator extends IterGenerator<String> {
            @Override
            public Iterator<String> generate(ActivityIntentInfo info) {
                return info.schemesIterator();
            }
        }

        public class AuthoritiesIterGenerator extends IterGenerator<IntentFilter.AuthorityEntry> {
            @Override
            public Iterator<IntentFilter.AuthorityEntry> generate(ActivityIntentInfo info) {
                return info.authoritiesIterator();
            }
        }

        /**
         * <em>WARNING</em> for performance reasons, the passed in intentList WILL BE
         * MODIFIED. Do not pass in a list that should not be changed.
         */
        private <T> void getIntentListSubset(List<ActivityIntentInfo> intentList,
                IterGenerator<T> generator, Iterator<T> searchIterator) {
            // loop through the set of actions; every one must be found in the intent filter
            while (searchIterator.hasNext()) {
                // we must have at least one filter in the list to consider a match
                if (intentList.size() == 0) {
                    break;
                }

                final T searchAction = searchIterator.next();

                // loop through the set of intent filters
                final Iterator<ActivityIntentInfo> intentIter = intentList.iterator();
                while (intentIter.hasNext()) {
                    final ActivityIntentInfo intentInfo = intentIter.next();
                    boolean selectionFound = false;

                    // loop through the intent filter's selection criteria; at least one
                    // of them must match the searched criteria
                    final Iterator<T> intentSelectionIter = generator.generate(intentInfo);
                    while (intentSelectionIter != null && intentSelectionIter.hasNext()) {
                        final T intentSelection = intentSelectionIter.next();
                        if (intentSelection != null && intentSelection.equals(searchAction)) {
                            selectionFound = true;
                            break;
                        }
                    }

                    // the selection criteria wasn't found in this filter's set; this filter
                    // is not a potential match
                    if (!selectionFound) {
                        intentIter.remove();
                    }
                }
            }
        }

        private boolean isProtectedAction(ActivityIntentInfo filter) {
            final Iterator<String> actionsIter = filter.actionsIterator();
            while (actionsIter != null && actionsIter.hasNext()) {
                final String filterAction = actionsIter.next();
                if (PROTECTED_ACTIONS.contains(filterAction)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Adjusts the priority of the given intent filter according to policy.
         * <p>
         * <ul>
         * <li>The priority for non privileged applications is capped to '0'</li>
         * <li>The priority for protected actions on privileged applications is capped to '0'</li>
         * <li>The priority for unbundled updates to privileged applications is capped to the
         *      priority defined on the system partition</li>
         * </ul>
         * <p>
         * <em>NOTE:</em> There is one exception. For security reasons, the setup wizard is
         * allowed to obtain any priority on any action.
         */
        private void adjustPriority(
                List<PackageParser.Activity> systemActivities, ActivityIntentInfo intent) {
            // nothing to do; priority is fine as-is
            if (intent.getPriority() <= 0) {
                return;
            }

            final ActivityInfo activityInfo = intent.activity.info;
            final ApplicationInfo applicationInfo = activityInfo.applicationInfo;

            final boolean privilegedApp =
                    ((applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0);
            if (!privilegedApp) {
                // non-privileged applications can never define a priority >0
                Slog.w(TAG, "Non-privileged app; cap priority to 0;"
                        + " package: " + applicationInfo.packageName
                        + " activity: " + intent.activity.className
                        + " origPrio: " + intent.getPriority());
                intent.setPriority(0);
                return;
            }

            if (systemActivities == null) {
                // the system package is not disabled; we're parsing the system partition
                if (isProtectedAction(intent)) {
                    if (mDeferProtectedFilters) {
                        // We can't deal with these just yet. No component should ever obtain a
                        // >0 priority for a protected actions, with ONE exception -- the setup
                        // wizard. The setup wizard, however, cannot be known until we're able to
                        // query it for the category CATEGORY_SETUP_WIZARD. Which we can't do
                        // until all intent filters have been processed. Chicken, meet egg.
                        // Let the filter temporarily have a high priority and rectify the
                        // priorities after all system packages have been scanned.
                        mProtectedFilters.add(intent);
                        if (DEBUG_FILTERS) {
                            Slog.i(TAG, "Protected action; save for later;"
                                    + " package: " + applicationInfo.packageName
                                    + " activity: " + intent.activity.className
                                    + " origPrio: " + intent.getPriority());
                        }
                        return;
                    } else {
                        if (DEBUG_FILTERS && mSetupWizardPackage == null) {
                            Slog.i(TAG, "No setup wizard;"
                                + " All protected intents capped to priority 0");
                        }
                        if (intent.activity.info.packageName.equals(mSetupWizardPackage)) {
                            if (DEBUG_FILTERS) {
                                Slog.i(TAG, "Found setup wizard;"
                                    + " allow priority " + intent.getPriority() + ";"
                                    + " package: " + intent.activity.info.packageName
                                    + " activity: " + intent.activity.className
                                    + " priority: " + intent.getPriority());
                            }
                            // setup wizard gets whatever it wants
                            return;
                        }
                        Slog.w(TAG, "Protected action; cap priority to 0;"
                                + " package: " + intent.activity.info.packageName
                                + " activity: " + intent.activity.className
                                + " origPrio: " + intent.getPriority());
                        intent.setPriority(0);
                        return;
                    }
                }
                // privileged apps on the system image get whatever priority they request
                return;
            }

            // privileged app unbundled update ... try to find the same activity
            final PackageParser.Activity foundActivity =
                    findMatchingActivity(systemActivities, activityInfo);
            if (foundActivity == null) {
                // this is a new activity; it cannot obtain >0 priority
                if (DEBUG_FILTERS) {
                    Slog.i(TAG, "New activity; cap priority to 0;"
                            + " package: " + applicationInfo.packageName
                            + " activity: " + intent.activity.className
                            + " origPrio: " + intent.getPriority());
                }
                intent.setPriority(0);
                return;
            }

            // found activity, now check for filter equivalence

            // a shallow copy is enough; we modify the list, not its contents
            final List<ActivityIntentInfo> intentListCopy =
                    new ArrayList<>(foundActivity.intents);
            final List<ActivityIntentInfo> foundFilters = findFilters(intent);

            // find matching action subsets
            final Iterator<String> actionsIterator = intent.actionsIterator();
            if (actionsIterator != null) {
                getIntentListSubset(
                        intentListCopy, new ActionIterGenerator(), actionsIterator);
                if (intentListCopy.size() == 0) {
                    // no more intents to match; we're not equivalent
                    if (DEBUG_FILTERS) {
                        Slog.i(TAG, "Mismatched action; cap priority to 0;"
                                + " package: " + applicationInfo.packageName
                                + " activity: " + intent.activity.className
                                + " origPrio: " + intent.getPriority());
                    }
                    intent.setPriority(0);
                    return;
                }
            }

            // find matching category subsets
            final Iterator<String> categoriesIterator = intent.categoriesIterator();
            if (categoriesIterator != null) {
                getIntentListSubset(intentListCopy, new CategoriesIterGenerator(),
                        categoriesIterator);
                if (intentListCopy.size() == 0) {
                    // no more intents to match; we're not equivalent
                    if (DEBUG_FILTERS) {
                        Slog.i(TAG, "Mismatched category; cap priority to 0;"
                                + " package: " + applicationInfo.packageName
                                + " activity: " + intent.activity.className
                                + " origPrio: " + intent.getPriority());
                    }
                    intent.setPriority(0);
                    return;
                }
            }

            // find matching schemes subsets
            final Iterator<String> schemesIterator = intent.schemesIterator();
            if (schemesIterator != null) {
                getIntentListSubset(intentListCopy, new SchemesIterGenerator(),
                        schemesIterator);
                if (intentListCopy.size() == 0) {
                    // no more intents to match; we're not equivalent
                    if (DEBUG_FILTERS) {
                        Slog.i(TAG, "Mismatched scheme; cap priority to 0;"
                                + " package: " + applicationInfo.packageName
                                + " activity: " + intent.activity.className
                                + " origPrio: " + intent.getPriority());
                    }
                    intent.setPriority(0);
                    return;
                }
            }

            // find matching authorities subsets
            final Iterator<IntentFilter.AuthorityEntry>
                    authoritiesIterator = intent.authoritiesIterator();
            if (authoritiesIterator != null) {
                getIntentListSubset(intentListCopy,
                        new AuthoritiesIterGenerator(),
                        authoritiesIterator);
                if (intentListCopy.size() == 0) {
                    // no more intents to match; we're not equivalent
                    if (DEBUG_FILTERS) {
                        Slog.i(TAG, "Mismatched authority; cap priority to 0;"
                                + " package: " + applicationInfo.packageName
                                + " activity: " + intent.activity.className
                                + " origPrio: " + intent.getPriority());
                    }
                    intent.setPriority(0);
                    return;
                }
            }

            // we found matching filter(s); app gets the max priority of all intents
            int cappedPriority = 0;
            for (int i = intentListCopy.size() - 1; i >= 0; --i) {
                cappedPriority = Math.max(cappedPriority, intentListCopy.get(i).getPriority());
            }
            if (intent.getPriority() > cappedPriority) {
                if (DEBUG_FILTERS) {
                    Slog.i(TAG, "Found matching filter(s);"
                            + " cap priority to " + cappedPriority + ";"
                            + " package: " + applicationInfo.packageName
                            + " activity: " + intent.activity.className
                            + " origPrio: " + intent.getPriority());
                }
                intent.setPriority(cappedPriority);
                return;
            }
            // all this for nothing; the requested priority was <= what was on the system
        }

        public final void addActivity(PackageParser.Activity a, String type) {
            mActivities.put(a.getComponentName(), a);
            if (DEBUG_SHOW_INFO)
                Log.v(
                TAG, "  " + type + " " +
                (a.info.nonLocalizedLabel != null ? a.info.nonLocalizedLabel : a.info.name) + ":");
            if (DEBUG_SHOW_INFO)
                Log.v(TAG, "    Class=" + a.info.name);
            final int NI = a.intents.size();
            for (int j=0; j<NI; j++) {
                PackageParser.ActivityIntentInfo intent = a.intents.get(j);
                if ("activity".equals(type)) {
                    final PackageSetting ps =
                            mSettings.getDisabledSystemPkgLPr(intent.activity.info.packageName);
                    final List<PackageParser.Activity> systemActivities =
                            ps != null && ps.pkg != null ? ps.pkg.activities : null;
                    adjustPriority(systemActivities, intent);
                }
                if (DEBUG_SHOW_INFO) {
                    Log.v(TAG, "    IntentFilter:");
                    intent.dump(new LogPrinter(Log.VERBOSE, TAG), "      ");
                }
                if (!intent.debugCheck()) {
                    Log.w(TAG, "==> For Activity " + a.info.name);
                }
                addFilter(intent);
            }
        }

        public final void removeActivity(PackageParser.Activity a, String type) {
            mActivities.remove(a.getComponentName());
            if (DEBUG_SHOW_INFO) {
                Log.v(TAG, "  " + type + " "
                        + (a.info.nonLocalizedLabel != null ? a.info.nonLocalizedLabel
                                : a.info.name) + ":");
                Log.v(TAG, "    Class=" + a.info.name);
            }
            final int NI = a.intents.size();
            for (int j=0; j<NI; j++) {
                PackageParser.ActivityIntentInfo intent = a.intents.get(j);
                if (DEBUG_SHOW_INFO) {
                    Log.v(TAG, "    IntentFilter:");
                    intent.dump(new LogPrinter(Log.VERBOSE, TAG), "      ");
                }
                removeFilter(intent);
            }
        }

        @Override
        protected boolean allowFilterResult(
                PackageParser.ActivityIntentInfo filter, List<ResolveInfo> dest) {
            ActivityInfo filterAi = filter.activity.info;
            for (int i=dest.size()-1; i>=0; i--) {
                ActivityInfo destAi = dest.get(i).activityInfo;
                if (destAi.name == filterAi.name
                        && destAi.packageName == filterAi.packageName) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected ActivityIntentInfo[] newArray(int size) {
            return new ActivityIntentInfo[size];
        }

        @Override
        protected boolean isFilterStopped(PackageParser.ActivityIntentInfo filter, int userId) {
            if (!sUserManager.exists(userId)) return true;
            PackageParser.Package p = filter.activity.owner;
            if (p != null) {
                PackageSetting ps = (PackageSetting)p.mExtras;
                if (ps != null) {
                    // System apps are never considered stopped for purposes of
                    // filtering, because there may be no way for the user to
                    // actually re-launch them.
                    return (ps.pkgFlags&ApplicationInfo.FLAG_SYSTEM) == 0
                            && ps.getStopped(userId);
                }
            }
            return false;
        }

        @Override
        protected boolean isPackageForFilter(String packageName,
                PackageParser.ActivityIntentInfo info) {
            return packageName.equals(info.activity.owner.packageName);
        }

        @Override
        protected ResolveInfo newResult(PackageParser.ActivityIntentInfo info,
                int match, int userId) {
            if (!sUserManager.exists(userId)) return null;
            if (!mSettings.isEnabledAndMatchLPr(info.activity.info, mFlags, userId)) {
                return null;
            }
            final PackageParser.Activity activity = info.activity;
            PackageSetting ps = (PackageSetting) activity.owner.mExtras;
            if (ps == null) {
                return null;
            }
            ActivityInfo ai = PackageParser.generateActivityInfo(activity, mFlags,
                    ps.readUserState(userId), userId);
            if (ai == null) {
                return null;
            }
            final ResolveInfo res = new ResolveInfo();
            res.activityInfo = ai;
            if ((mFlags&PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = info;
            }
            if (info != null) {
                res.handleAllWebDataURI = info.handleAllWebDataURI();
            }
            res.priority = info.getPriority();
            res.preferredOrder = activity.owner.mPreferredOrder;
            //System.out.println("Result: " + res.activityInfo.className +
            //                   " = " + res.priority);
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            if (userNeedsBadging(userId)) {
                res.noResourceId = true;
            } else {
                res.icon = info.icon;
            }
            res.iconResourceId = info.icon;
            res.system = res.activityInfo.applicationInfo.isSystemApp();
            return res;
        }

        @Override
        protected void sortResults(List<ResolveInfo> results) {
            Collections.sort(results, mResolvePrioritySorter);
        }

        @Override
        protected void dumpFilter(PrintWriter out, String prefix,
                PackageParser.ActivityIntentInfo filter) {
            out.print(prefix); out.print(
                    Integer.toHexString(System.identityHashCode(filter.activity)));
                    out.print(' ');
                    filter.activity.printComponentShortName(out);
                    out.print(" filter ");
                    out.println(Integer.toHexString(System.identityHashCode(filter)));
        }

        @Override
        protected Object filterToLabel(PackageParser.ActivityIntentInfo filter) {
            return filter.activity;
        }

        protected void dumpFilterLabel(PrintWriter out, String prefix, Object label, int count) {
            PackageParser.Activity activity = (PackageParser.Activity)label;
            out.print(prefix); out.print(
                    Integer.toHexString(System.identityHashCode(activity)));
                    out.print(' ');
                    activity.printComponentShortName(out);
            if (count > 1) {
                out.print(" ("); out.print(count); out.print(" filters)");
            }
            out.println();
        }

        // Keys are String (activity class name), values are Activity.
        private final ArrayMap<ComponentName, PackageParser.Activity> mActivities
                = new ArrayMap<ComponentName, PackageParser.Activity>();
        private int mFlags;
    }

    private final class ServiceIntentResolver
            extends IntentResolver<PackageParser.ServiceIntentInfo, ResolveInfo> {
        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType,
                boolean defaultOnly, int userId) {
            mFlags = defaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0;
            return super.queryIntent(intent, resolvedType, defaultOnly, userId);
        }

        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType, int flags,
                int userId) {
            if (!sUserManager.exists(userId)) return null;
            mFlags = flags;
            return super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId);
        }

        public List<ResolveInfo> queryIntentForPackage(Intent intent, String resolvedType,
                int flags, ArrayList<PackageParser.Service> packageServices, int userId) {
            if (!sUserManager.exists(userId)) return null;
            if (packageServices == null) {
                return null;
            }
            mFlags = flags;
            final boolean defaultOnly = (flags&PackageManager.MATCH_DEFAULT_ONLY) != 0;
            final int N = packageServices.size();
            ArrayList<PackageParser.ServiceIntentInfo[]> listCut =
                new ArrayList<PackageParser.ServiceIntentInfo[]>(N);

            ArrayList<PackageParser.ServiceIntentInfo> intentFilters;
            for (int i = 0; i < N; ++i) {
                intentFilters = packageServices.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    PackageParser.ServiceIntentInfo[] array =
                            new PackageParser.ServiceIntentInfo[intentFilters.size()];
                    intentFilters.toArray(array);
                    listCut.add(array);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        public final void addService(PackageParser.Service s) {
            mServices.put(s.getComponentName(), s);
            if (DEBUG_SHOW_INFO) {
                Log.v(TAG, "  "
                        + (s.info.nonLocalizedLabel != null
                        ? s.info.nonLocalizedLabel : s.info.name) + ":");
                Log.v(TAG, "    Class=" + s.info.name);
            }
            final int NI = s.intents.size();
            int j;
            for (j=0; j<NI; j++) {
                PackageParser.ServiceIntentInfo intent = s.intents.get(j);
                if (DEBUG_SHOW_INFO) {
                    Log.v(TAG, "    IntentFilter:");
                    intent.dump(new LogPrinter(Log.VERBOSE, TAG), "      ");
                }
                if (!intent.debugCheck()) {
                    Log.w(TAG, "==> For Service " + s.info.name);
                }
                addFilter(intent);
            }
        }

        public final void removeService(PackageParser.Service s) {
            mServices.remove(s.getComponentName());
            if (DEBUG_SHOW_INFO) {
                Log.v(TAG, "  " + (s.info.nonLocalizedLabel != null
                        ? s.info.nonLocalizedLabel : s.info.name) + ":");
                Log.v(TAG, "    Class=" + s.info.name);
            }
            final int NI = s.intents.size();
            int j;
            for (j=0; j<NI; j++) {
                PackageParser.ServiceIntentInfo intent = s.intents.get(j);
                if (DEBUG_SHOW_INFO) {
                    Log.v(TAG, "    IntentFilter:");
                    intent.dump(new LogPrinter(Log.VERBOSE, TAG), "      ");
                }
                removeFilter(intent);
            }
        }

        @Override
        protected boolean allowFilterResult(
                PackageParser.ServiceIntentInfo filter, List<ResolveInfo> dest) {
            ServiceInfo filterSi = filter.service.info;
            for (int i=dest.size()-1; i>=0; i--) {
                ServiceInfo destAi = dest.get(i).serviceInfo;
                if (destAi.name == filterSi.name
                        && destAi.packageName == filterSi.packageName) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected PackageParser.ServiceIntentInfo[] newArray(int size) {
            return new PackageParser.ServiceIntentInfo[size];
        }

        @Override
        protected boolean isFilterStopped(PackageParser.ServiceIntentInfo filter, int userId) {
            if (!sUserManager.exists(userId)) return true;
            PackageParser.Package p = filter.service.owner;
            if (p != null) {
                PackageSetting ps = (PackageSetting)p.mExtras;
                if (ps != null) {
                    // System apps are never considered stopped for purposes of
                    // filtering, because there may be no way for the user to
                    // actually re-launch them.
                    return (ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) == 0
                            && ps.getStopped(userId);
                }
            }
            return false;
        }

        @Override
        protected boolean isPackageForFilter(String packageName,
                PackageParser.ServiceIntentInfo info) {
            return packageName.equals(info.service.owner.packageName);
        }

        @Override
        protected ResolveInfo newResult(PackageParser.ServiceIntentInfo filter,
                int match, int userId) {
            if (!sUserManager.exists(userId)) return null;
            final PackageParser.ServiceIntentInfo info = (PackageParser.ServiceIntentInfo)filter;
            if (!mSettings.isEnabledAndMatchLPr(info.service.info, mFlags, userId)) {
                return null;
            }
            final PackageParser.Service service = info.service;
            PackageSetting ps = (PackageSetting) service.owner.mExtras;
            if (ps == null) {
                return null;
            }
            ServiceInfo si = PackageParser.generateServiceInfo(service, mFlags,
                    ps.readUserState(userId), userId);
            if (si == null) {
                return null;
            }
            final ResolveInfo res = new ResolveInfo();
            res.serviceInfo = si;
            if ((mFlags&PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = filter;
            }
            res.priority = info.getPriority();
            res.preferredOrder = service.owner.mPreferredOrder;
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            res.system = res.serviceInfo.applicationInfo.isSystemApp();
            return res;
        }

        @Override
        protected void sortResults(List<ResolveInfo> results) {
            Collections.sort(results, mResolvePrioritySorter);
        }

        @Override
        protected void dumpFilter(PrintWriter out, String prefix,
                PackageParser.ServiceIntentInfo filter) {
            out.print(prefix); out.print(
                    Integer.toHexString(System.identityHashCode(filter.service)));
                    out.print(' ');
                    filter.service.printComponentShortName(out);
                    out.print(" filter ");
                    out.println(Integer.toHexString(System.identityHashCode(filter)));
        }

        @Override
        protected Object filterToLabel(PackageParser.ServiceIntentInfo filter) {
            return filter.service;
        }

        protected void dumpFilterLabel(PrintWriter out, String prefix, Object label, int count) {
            PackageParser.Service service = (PackageParser.Service)label;
            out.print(prefix); out.print(
                    Integer.toHexString(System.identityHashCode(service)));
                    out.print(' ');
                    service.printComponentShortName(out);
            if (count > 1) {
                out.print(" ("); out.print(count); out.print(" filters)");
            }
            out.println();
        }

//        List<ResolveInfo> filterEnabled(List<ResolveInfo> resolveInfoList) {
//            final Iterator<ResolveInfo> i = resolveInfoList.iterator();
//            final List<ResolveInfo> retList = Lists.newArrayList();
//            while (i.hasNext()) {
//                final ResolveInfo resolveInfo = (ResolveInfo) i;
//                if (isEnabledLP(resolveInfo.serviceInfo)) {
//                    retList.add(resolveInfo);
//                }
//            }
//            return retList;
//        }

        // Keys are String (activity class name), values are Activity.
        private final ArrayMap<ComponentName, PackageParser.Service> mServices
                = new ArrayMap<ComponentName, PackageParser.Service>();
        private int mFlags;
    };

    private final class ProviderIntentResolver
            extends IntentResolver<PackageParser.ProviderIntentInfo, ResolveInfo> {
        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType,
                boolean defaultOnly, int userId) {
            mFlags = defaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0;
            return super.queryIntent(intent, resolvedType, defaultOnly, userId);
        }

        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType, int flags,
                int userId) {
            if (!sUserManager.exists(userId))
                return null;
            mFlags = flags;
            return super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId);
        }

        public List<ResolveInfo> queryIntentForPackage(Intent intent, String resolvedType,
                int flags, ArrayList<PackageParser.Provider> packageProviders, int userId) {
            if (!sUserManager.exists(userId))
                return null;
            if (packageProviders == null) {
                return null;
            }
            mFlags = flags;
            final boolean defaultOnly = (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0;
            final int N = packageProviders.size();
            ArrayList<PackageParser.ProviderIntentInfo[]> listCut =
                    new ArrayList<PackageParser.ProviderIntentInfo[]>(N);

            ArrayList<PackageParser.ProviderIntentInfo> intentFilters;
            for (int i = 0; i < N; ++i) {
                intentFilters = packageProviders.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    PackageParser.ProviderIntentInfo[] array =
                            new PackageParser.ProviderIntentInfo[intentFilters.size()];
                    intentFilters.toArray(array);
                    listCut.add(array);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        public final void addProvider(PackageParser.Provider p) {
            if (mProviders.containsKey(p.getComponentName())) {
                Slog.w(TAG, "Provider " + p.getComponentName() + " already defined; ignoring");
                return;
            }

            mProviders.put(p.getComponentName(), p);
            if (DEBUG_SHOW_INFO) {
                Log.v(TAG, "  "
                        + (p.info.nonLocalizedLabel != null
                                ? p.info.nonLocalizedLabel : p.info.name) + ":");
                Log.v(TAG, "    Class=" + p.info.name);
            }
            final int NI = p.intents.size();
            int j;
            for (j = 0; j < NI; j++) {
                PackageParser.ProviderIntentInfo intent = p.intents.get(j);
                if (DEBUG_SHOW_INFO) {
                    Log.v(TAG, "    IntentFilter:");
                    intent.dump(new LogPrinter(Log.VERBOSE, TAG), "      ");
                }
                if (!intent.debugCheck()) {
                    Log.w(TAG, "==> For Provider " + p.info.name);
                }
                addFilter(intent);
            }
        }

        public final void removeProvider(PackageParser.Provider p) {
            mProviders.remove(p.getComponentName());
            if (DEBUG_SHOW_INFO) {
                Log.v(TAG, "  " + (p.info.nonLocalizedLabel != null
                        ? p.info.nonLocalizedLabel : p.info.name) + ":");
                Log.v(TAG, "    Class=" + p.info.name);
            }
            final int NI = p.intents.size();
            int j;
            for (j = 0; j < NI; j++) {
                PackageParser.ProviderIntentInfo intent = p.intents.get(j);
                if (DEBUG_SHOW_INFO) {
                    Log.v(TAG, "    IntentFilter:");
                    intent.dump(new LogPrinter(Log.VERBOSE, TAG), "      ");
                }
                removeFilter(intent);
            }
        }

        @Override
        protected boolean allowFilterResult(
                PackageParser.ProviderIntentInfo filter, List<ResolveInfo> dest) {
            ProviderInfo filterPi = filter.provider.info;
            for (int i = dest.size() - 1; i >= 0; i--) {
                ProviderInfo destPi = dest.get(i).providerInfo;
                if (destPi.name == filterPi.name
                        && destPi.packageName == filterPi.packageName) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected PackageParser.ProviderIntentInfo[] newArray(int size) {
            return new PackageParser.ProviderIntentInfo[size];
        }

        @Override
        protected boolean isFilterStopped(PackageParser.ProviderIntentInfo filter, int userId) {
            if (!sUserManager.exists(userId))
                return true;
            PackageParser.Package p = filter.provider.owner;
            if (p != null) {
                PackageSetting ps = (PackageSetting) p.mExtras;
                if (ps != null) {
                    // System apps are never considered stopped for purposes of
                    // filtering, because there may be no way for the user to
                    // actually re-launch them.
                    return (ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) == 0
                            && ps.getStopped(userId);
                }
            }
            return false;
        }

        @Override
        protected boolean isPackageForFilter(String packageName,
                PackageParser.ProviderIntentInfo info) {
            return packageName.equals(info.provider.owner.packageName);
        }

        @Override
        protected ResolveInfo newResult(PackageParser.ProviderIntentInfo filter,
                int match, int userId) {
            if (!sUserManager.exists(userId))
                return null;
            final PackageParser.ProviderIntentInfo info = filter;
            if (!mSettings.isEnabledAndMatchLPr(info.provider.info, mFlags, userId)) {
                return null;
            }
            final PackageParser.Provider provider = info.provider;
            PackageSetting ps = (PackageSetting) provider.owner.mExtras;
            if (ps == null) {
                return null;
            }
            ProviderInfo pi = PackageParser.generateProviderInfo(provider, mFlags,
                    ps.readUserState(userId), userId);
            if (pi == null) {
                return null;
            }
            final ResolveInfo res = new ResolveInfo();
            res.providerInfo = pi;
            if ((mFlags & PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = filter;
            }
            res.priority = info.getPriority();
            res.preferredOrder = provider.owner.mPreferredOrder;
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            res.system = res.providerInfo.applicationInfo.isSystemApp();
            return res;
        }

        @Override
        protected void sortResults(List<ResolveInfo> results) {
            Collections.sort(results, mResolvePrioritySorter);
        }

        @Override
        protected void dumpFilter(PrintWriter out, String prefix,
                PackageParser.ProviderIntentInfo filter) {
            out.print(prefix);
            out.print(
                    Integer.toHexString(System.identityHashCode(filter.provider)));
            out.print(' ');
            filter.provider.printComponentShortName(out);
            out.print(" filter ");
            out.println(Integer.toHexString(System.identityHashCode(filter)));
        }

        @Override
        protected Object filterToLabel(PackageParser.ProviderIntentInfo filter) {
            return filter.provider;
        }

        protected void dumpFilterLabel(PrintWriter out, String prefix, Object label, int count) {
            PackageParser.Provider provider = (PackageParser.Provider)label;
            out.print(prefix); out.print(
                    Integer.toHexString(System.identityHashCode(provider)));
                    out.print(' ');
                    provider.printComponentShortName(out);
            if (count > 1) {
                out.print(" ("); out.print(count); out.print(" filters)");
            }
            out.println();
        }

        private final ArrayMap<ComponentName, PackageParser.Provider> mProviders
                = new ArrayMap<ComponentName, PackageParser.Provider>();
        private int mFlags;
    }

    private static final class EphemeralIntentResolver
            extends IntentResolver<EphemeralResolveIntentInfo, EphemeralResolveInfo> {
        @Override
        protected EphemeralResolveIntentInfo[] newArray(int size) {
            return new EphemeralResolveIntentInfo[size];
        }

        @Override
        protected boolean isPackageForFilter(String packageName, EphemeralResolveIntentInfo info) {
            return true;
        }

        @Override
        protected EphemeralResolveInfo newResult(EphemeralResolveIntentInfo info, int match,
                int userId) {
            if (!sUserManager.exists(userId)) {
                return null;
            }
            return info.getEphemeralResolveInfo();
        }
    }

    private static final Comparator<ResolveInfo> mResolvePrioritySorter =
            new Comparator<ResolveInfo>() {
        public int compare(ResolveInfo r1, ResolveInfo r2) {
            int v1 = r1.priority;
            int v2 = r2.priority;
            //System.out.println("Comparing: q1=" + q1 + " q2=" + q2);
            if (v1 != v2) {
                return (v1 > v2) ? -1 : 1;
            }
            v1 = r1.preferredOrder;
            v2 = r2.preferredOrder;
            if (v1 != v2) {
                return (v1 > v2) ? -1 : 1;
            }
            if (r1.isDefault != r2.isDefault) {
                return r1.isDefault ? -1 : 1;
            }
            v1 = r1.match;
            v2 = r2.match;
            //System.out.println("Comparing: m1=" + m1 + " m2=" + m2);
            if (v1 != v2) {
                return (v1 > v2) ? -1 : 1;
            }
            if (r1.system != r2.system) {
                return r1.system ? -1 : 1;
            }
            if (r1.activityInfo != null) {
                return r1.activityInfo.packageName.compareTo(r2.activityInfo.packageName);
            }
            if (r1.serviceInfo != null) {
                return r1.serviceInfo.packageName.compareTo(r2.serviceInfo.packageName);
            }
            if (r1.providerInfo != null) {
                return r1.providerInfo.packageName.compareTo(r2.providerInfo.packageName);
            }
            return 0;
        }
    };

    private static final Comparator<ProviderInfo> mProviderInitOrderSorter =
            new Comparator<ProviderInfo>() {
        public int compare(ProviderInfo p1, ProviderInfo p2) {
            final int v1 = p1.initOrder;
            final int v2 = p2.initOrder;
            return (v1 > v2) ? -1 : ((v1 < v2) ? 1 : 0);
        }
    };

    final void sendPackageBroadcast(final String action, final String pkg, final Bundle extras,
            final int flags, final String targetPkg, final IIntentReceiver finishedReceiver,
            final int[] userIds) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final IActivityManager am = ActivityManagerNative.getDefault();
                    if (am == null) return;
                    final int[] resolvedUserIds;
                    if (userIds == null) {
                        resolvedUserIds = am.getRunningUserIds();
                    } else {
                        resolvedUserIds = userIds;
                    }
                    for (int id : resolvedUserIds) {
                        final Intent intent = new Intent(action,
                                pkg != null ? Uri.fromParts("package", pkg, null) : null);
                        if (extras != null) {
                            intent.putExtras(extras);
                        }
                        if (targetPkg != null) {
                            intent.setPackage(targetPkg);
                        }
                        // Modify the UID when posting to other users
                        int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                        if (uid > 0 && UserHandle.getUserId(uid) != id) {
                            uid = UserHandle.getUid(id, UserHandle.getAppId(uid));
                            intent.putExtra(Intent.EXTRA_UID, uid);
                        }
                        intent.putExtra(Intent.EXTRA_USER_HANDLE, id);
                        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT | flags);
                        if (DEBUG_BROADCASTS) {
                            RuntimeException here = new RuntimeException("here");
                            here.fillInStackTrace();
                            Slog.d(TAG, "Sending to user " + id + ": "
                                    + intent.toShortString(false, true, false, false)
                                    + " " + intent.getExtras(), here);
                        }
                        am.broadcastIntent(null, intent, null, finishedReceiver,
                                0, null, null, null, android.app.AppOpsManager.OP_NONE,
                                null, finishedReceiver != null, false, id);
                    }
                } catch (RemoteException ex) {
                }
            }
        });
    }

    /**
     * Check if the external storage media is available. This is true if there
     * is a mounted external storage medium or if the external storage is
     * emulated.
     */
    private boolean isExternalMediaAvailable() {
        return mMediaMounted || Environment.isExternalStorageEmulated();
    }

    @Override
    public PackageCleanItem nextPackageToClean(PackageCleanItem lastPackage) {
        // writer
        synchronized (mPackages) {
            if (!isExternalMediaAvailable()) {
                // If the external storage is no longer mounted at this point,
                // the caller may not have been able to delete all of this
                // packages files and can not delete any more.  Bail.
                return null;
            }
            final ArrayList<PackageCleanItem> pkgs = mSettings.mPackagesToBeCleaned;
            if (lastPackage != null) {
                pkgs.remove(lastPackage);
            }
            if (pkgs.size() > 0) {
                return pkgs.get(0);
            }
        }
        return null;
    }

    void schedulePackageCleaning(String packageName, int userId, boolean andCode) {
        final Message msg = mHandler.obtainMessage(START_CLEANING_PACKAGE,
                userId, andCode ? 1 : 0, packageName);
        if (mSystemReady) {
            msg.sendToTarget();
        } else {
            if (mPostSystemReadyMessages == null) {
                mPostSystemReadyMessages = new ArrayList<>();
            }
            mPostSystemReadyMessages.add(msg);
        }
    }

    void startCleaningPackages() {
        // reader
        if (!isExternalMediaAvailable()) {
            return;
        }
        synchronized (mPackages) {
            if (mSettings.mPackagesToBeCleaned.isEmpty()) {
                return;
            }
        }
        Intent intent = new Intent(PackageManager.ACTION_CLEAN_EXTERNAL_STORAGE);
        intent.setComponent(DEFAULT_CONTAINER_COMPONENT);
        IActivityManager am = ActivityManagerNative.getDefault();
        if (am != null) {
            try {
                am.startService(null, intent, null, mContext.getOpPackageName(),
                        UserHandle.USER_SYSTEM);
            } catch (RemoteException e) {
            }
        }
    }

    @Override
    public void installPackageAsUser(String originPath, IPackageInstallObserver2 observer,
            int installFlags, String installerPackageName, int userId) {
        android.util.SeempLog.record(90);
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.INSTALL_PACKAGES, null);

        final int callingUid = Binder.getCallingUid();
        enforceCrossUserPermission(callingUid, userId,
                true /* requireFullPermission */, true /* checkShell */, "installPackageAsUser");

        if (isUserRestricted(userId, UserManager.DISALLOW_INSTALL_APPS)) {
            try {
                if (observer != null) {
                    observer.onPackageInstalled("", INSTALL_FAILED_USER_RESTRICTED, null, null);
                }
            } catch (RemoteException re) {
            }
            return;
        }

        if ((callingUid == Process.SHELL_UID) || (callingUid == Process.ROOT_UID)) {
            installFlags |= PackageManager.INSTALL_FROM_ADB;

        } else {
            // Caller holds INSTALL_PACKAGES permission, so we're less strict
            // about installerPackageName.

            installFlags &= ~PackageManager.INSTALL_FROM_ADB;
            installFlags &= ~PackageManager.INSTALL_ALL_USERS;
        }

        UserHandle user;
        if ((installFlags & PackageManager.INSTALL_ALL_USERS) != 0) {
            user = UserHandle.ALL;
        } else {
            user = new UserHandle(userId);
        }

        // Only system components can circumvent runtime permissions when installing.
        if ((installFlags & PackageManager.INSTALL_GRANT_RUNTIME_PERMISSIONS) != 0
                && mContext.checkCallingOrSelfPermission(Manifest.permission
                .INSTALL_GRANT_RUNTIME_PERMISSIONS) == PackageManager.PERMISSION_DENIED) {
            throw new SecurityException("You need the "
                    + "android.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS permission "
                    + "to use the PackageManager.INSTALL_GRANT_RUNTIME_PERMISSIONS flag");
        }

        final File originFile = new File(originPath);
        final OriginInfo origin = OriginInfo.fromUntrustedFile(originFile);

        final Message msg = mHandler.obtainMessage(INIT_COPY);
        final VerificationInfo verificationInfo = new VerificationInfo(
                null /*originatingUri*/, null /*referrer*/, -1 /*originatingUid*/, callingUid);
        final InstallParams params = new InstallParams(origin, null /*moveInfo*/, observer,
                installFlags, installerPackageName, null /*volumeUuid*/, verificationInfo, user,
                null /*packageAbiOverride*/, null /*grantedPermissions*/,
                null /*certificates*/);
        params.setTraceMethod("installAsUser").setTraceCookie(System.identityHashCode(params));
        msg.obj = params;

        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "installAsUser",
                System.identityHashCode(msg.obj));
        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                System.identityHashCode(msg.obj));

        mHandler.sendMessage(msg);
    }

    void installStage(String packageName, File stagedDir, String stagedCid,
            IPackageInstallObserver2 observer, PackageInstaller.SessionParams sessionParams,
            String installerPackageName, int installerUid, UserHandle user,
            Certificate[][] certificates) {
        if (DEBUG_EPHEMERAL) {
            if ((sessionParams.installFlags & PackageManager.INSTALL_EPHEMERAL) != 0) {
                Slog.d(TAG, "Ephemeral install of " + packageName);
            }
        }
        final VerificationInfo verificationInfo = new VerificationInfo(
                sessionParams.originatingUri, sessionParams.referrerUri,
                sessionParams.originatingUid, installerUid);

        final OriginInfo origin;
        if (stagedDir != null) {
            origin = OriginInfo.fromStagedFile(stagedDir);
        } else {
            origin = OriginInfo.fromStagedContainer(stagedCid);
        }

        final Message msg = mHandler.obtainMessage(INIT_COPY);
        final InstallParams params = new InstallParams(origin, null, observer,
                sessionParams.installFlags, installerPackageName, sessionParams.volumeUuid,
                verificationInfo, user, sessionParams.abiOverride,
                sessionParams.grantedRuntimePermissions, certificates);
        params.setTraceMethod("installStage").setTraceCookie(System.identityHashCode(params));
        msg.obj = params;

        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "installStage",
                System.identityHashCode(msg.obj));
        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                System.identityHashCode(msg.obj));

        mHandler.sendMessage(msg);
    }

    private void sendPackageAddedForUser(String packageName, PackageSetting pkgSetting,
            int userId) {
        final boolean isSystem = isSystemApp(pkgSetting) || isUpdatedSystemApp(pkgSetting);
        sendPackageAddedForUser(packageName, isSystem, pkgSetting.appId, userId);
    }

    private void sendPackageAddedForUser(String packageName, boolean isSystem,
            int appId, int userId) {
        Bundle extras = new Bundle(1);
        extras.putInt(Intent.EXTRA_UID, UserHandle.getUid(userId, appId));

        sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED,
                packageName, extras, 0, null, null, new int[] {userId});
        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            if (isSystem && am.isUserRunning(userId, 0)) {
                // The just-installed/enabled app is bundled on the system, so presumed
                // to be able to run automatically without needing an explicit launch.
                // Send it a BOOT_COMPLETED if it would ordinarily have gotten one.
                Intent bcIntent = new Intent(Intent.ACTION_BOOT_COMPLETED)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .setPackage(packageName);
                am.broadcastIntent(null, bcIntent, null, null, 0, null, null, null,
                        android.app.AppOpsManager.OP_NONE, null, false, false, userId);
            }
        } catch (RemoteException e) {
            // shouldn't happen
            Slog.w(TAG, "Unable to bootstrap installed package", e);
        }
    }

    @Override
    public boolean setApplicationHiddenSettingAsUser(String packageName, boolean hidden,
            int userId) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MANAGE_USERS, null);
        PackageSetting pkgSetting;
        final int uid = Binder.getCallingUid();
        enforceCrossUserPermission(uid, userId,
                true /* requireFullPermission */, true /* checkShell */,
                "setApplicationHiddenSetting for user " + userId);

        if (hidden && isPackageDeviceAdmin(packageName, userId)) {
            Slog.w(TAG, "Not hiding package " + packageName + ": has active device admin");
            return false;
        }

        long callingId = Binder.clearCallingIdentity();
        try {
            boolean sendAdded = false;
            boolean sendRemoved = false;
            // writer
            synchronized (mPackages) {
                pkgSetting = mSettings.mPackages.get(packageName);
                if (pkgSetting == null) {
                    return false;
                }
                // Do not allow "android" is being disabled
                if ("android".equals(packageName)) {
                    Slog.w(TAG, "Cannot hide package: android");
                    return false;
                }
                // Only allow protected packages to hide themselves.
                if (hidden && !UserHandle.isSameApp(uid, pkgSetting.appId)
                        && mProtectedPackages.isPackageStateProtected(userId, packageName)) {
                    Slog.w(TAG, "Not hiding protected package: " + packageName);
                    return false;
                }

                if (pkgSetting.getHidden(userId) != hidden) {
                    pkgSetting.setHidden(hidden, userId);
                    mSettings.writePackageRestrictionsLPr(userId);
                    if (hidden) {
                        sendRemoved = true;
                    } else {
                        sendAdded = true;
                    }
                }
            }
            if (sendAdded) {
                sendPackageAddedForUser(packageName, pkgSetting, userId);
                return true;
            }
            if (sendRemoved) {
                killApplication(packageName, UserHandle.getUid(userId, pkgSetting.appId),
                        "hiding pkg");
                sendApplicationHiddenForUser(packageName, pkgSetting, userId);
                return true;
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return false;
    }

    private void sendApplicationHiddenForUser(String packageName, PackageSetting pkgSetting,
            int userId) {
        final PackageRemovedInfo info = new PackageRemovedInfo();
        info.removedPackage = packageName;
        info.removedUsers = new int[] {userId};
        info.uid = UserHandle.getUid(userId, pkgSetting.appId);
        info.sendPackageRemovedBroadcasts(true /*killApp*/);
    }

    private void sendPackagesSuspendedForUser(String[] pkgList, int userId, boolean suspended) {
        if (pkgList.length > 0) {
            Bundle extras = new Bundle(1);
            extras.putStringArray(Intent.EXTRA_CHANGED_PACKAGE_LIST, pkgList);

            sendPackageBroadcast(
                    suspended ? Intent.ACTION_PACKAGES_SUSPENDED
                            : Intent.ACTION_PACKAGES_UNSUSPENDED,
                    null, extras, Intent.FLAG_RECEIVER_REGISTERED_ONLY, null, null,
                    new int[] {userId});
        }
    }

    /**
     * Returns true if application is not found or there was an error. Otherwise it returns
     * the hidden state of the package for the given user.
     */
    @Override
    public boolean getApplicationHiddenSettingAsUser(String packageName, int userId) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MANAGE_USERS, null);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "getApplicationHidden for user " + userId);
        PackageSetting pkgSetting;
        long callingId = Binder.clearCallingIdentity();
        try {
            // writer
            synchronized (mPackages) {
                pkgSetting = mSettings.mPackages.get(packageName);
                if (pkgSetting == null) {
                    return true;
                }
                return pkgSetting.getHidden(userId);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * @hide
     */
    @Override
    public int installExistingPackageAsUser(String packageName, int userId) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.INSTALL_PACKAGES,
                null);
        PackageSetting pkgSetting;
        final int uid = Binder.getCallingUid();
        enforceCrossUserPermission(uid, userId,
                true /* requireFullPermission */, true /* checkShell */,
                "installExistingPackage for user " + userId);
        if (isUserRestricted(userId, UserManager.DISALLOW_INSTALL_APPS)) {
            return PackageManager.INSTALL_FAILED_USER_RESTRICTED;
        }

        long callingId = Binder.clearCallingIdentity();
        try {
            boolean installed = false;

            // writer
            synchronized (mPackages) {
                pkgSetting = mSettings.mPackages.get(packageName);
                if (pkgSetting == null) {
                    return PackageManager.INSTALL_FAILED_INVALID_URI;
                }
                if (!pkgSetting.getInstalled(userId)) {
                    pkgSetting.setInstalled(true, userId);
                    pkgSetting.setHidden(false, userId);
                    mSettings.writePackageRestrictionsLPr(userId);
                    installed = true;
                }
            }

            if (installed) {
                if (pkgSetting.pkg != null) {
                    synchronized (mInstallLock) {
                        // We don't need to freeze for a brand new install
                        prepareAppDataAfterInstallLIF(pkgSetting.pkg);
                    }
                }
                sendPackageAddedForUser(packageName, pkgSetting, userId);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }

        return PackageManager.INSTALL_SUCCEEDED;
    }

    boolean isUserRestricted(int userId, String restrictionKey) {
        Bundle restrictions = sUserManager.getUserRestrictions(userId);
        if (restrictions.getBoolean(restrictionKey, false)) {
            Log.w(TAG, "User is restricted: " + restrictionKey);
            return true;
        }
        return false;
    }

    @Override
    public String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended,
            int userId) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MANAGE_USERS, null);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, true /* checkShell */,
                "setPackagesSuspended for user " + userId);

        if (ArrayUtils.isEmpty(packageNames)) {
            return packageNames;
        }

        // List of package names for whom the suspended state has changed.
        List<String> changedPackages = new ArrayList<>(packageNames.length);
        // List of package names for whom the suspended state is not set as requested in this
        // method.
        List<String> unactionedPackages = new ArrayList<>(packageNames.length);
        long callingId = Binder.clearCallingIdentity();
        try {
            for (int i = 0; i < packageNames.length; i++) {
                String packageName = packageNames[i];
                boolean changed = false;
                final int appId;
                synchronized (mPackages) {
                    final PackageSetting pkgSetting = mSettings.mPackages.get(packageName);
                    if (pkgSetting == null) {
                        Slog.w(TAG, "Could not find package setting for package \"" + packageName
                                + "\". Skipping suspending/un-suspending.");
                        unactionedPackages.add(packageName);
                        continue;
                    }
                    appId = pkgSetting.appId;
                    if (pkgSetting.getSuspended(userId) != suspended) {
                        if (!canSuspendPackageForUserLocked(packageName, userId)) {
                            unactionedPackages.add(packageName);
                            continue;
                        }
                        pkgSetting.setSuspended(suspended, userId);
                        mSettings.writePackageRestrictionsLPr(userId);
                        changed = true;
                        changedPackages.add(packageName);
                    }
                }

                if (changed && suspended) {
                    killApplication(packageName, UserHandle.getUid(userId, appId),
                            "suspending package");
                }
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }

        if (!changedPackages.isEmpty()) {
            sendPackagesSuspendedForUser(changedPackages.toArray(
                    new String[changedPackages.size()]), userId, suspended);
        }

        return unactionedPackages.toArray(new String[unactionedPackages.size()]);
    }

    @Override
    public boolean isPackageSuspendedForUser(String packageName, int userId) {
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */,
                "isPackageSuspendedForUser for user " + userId);
        synchronized (mPackages) {
            final PackageSetting pkgSetting = mSettings.mPackages.get(packageName);
            if (pkgSetting == null) {
                throw new IllegalArgumentException("Unknown target package: " + packageName);
            }
            return pkgSetting.getSuspended(userId);
        }
    }

    private boolean canSuspendPackageForUserLocked(String packageName, int userId) {
        if (isPackageDeviceAdmin(packageName, userId)) {
            Slog.w(TAG, "Cannot suspend/un-suspend package \"" + packageName
                    + "\": has an active device admin");
            return false;
        }

        String activeLauncherPackageName = getActiveLauncherPackageName(userId);
        if (packageName.equals(activeLauncherPackageName)) {
            Slog.w(TAG, "Cannot suspend/un-suspend package \"" + packageName
                    + "\": contains the active launcher");
            return false;
        }

        if (packageName.equals(mRequiredInstallerPackage)) {
            Slog.w(TAG, "Cannot suspend/un-suspend package \"" + packageName
                    + "\": required for package installation");
            return false;
        }

        if (packageName.equals(mRequiredVerifierPackage)) {
            Slog.w(TAG, "Cannot suspend/un-suspend package \"" + packageName
                    + "\": required for package verification");
            return false;
        }

        if (packageName.equals(getDefaultDialerPackageName(userId))) {
            Slog.w(TAG, "Cannot suspend/un-suspend package \"" + packageName
                    + "\": is the default dialer");
            return false;
        }

        if (mProtectedPackages.isPackageStateProtected(userId, packageName)) {
            Slog.w(TAG, "Cannot suspend/un-suspend package \"" + packageName
                    + "\": protected package");
            return false;
        }

        return true;
    }

    private String getActiveLauncherPackageName(int userId) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = resolveIntent(
                intent,
                intent.resolveTypeIfNeeded(mContext.getContentResolver()),
                PackageManager.MATCH_DEFAULT_ONLY,
                userId);

        return resolveInfo == null ? null : resolveInfo.activityInfo.packageName;
    }

    private String getDefaultDialerPackageName(int userId) {
        synchronized (mPackages) {
            return mSettings.getDefaultDialerPackageNameLPw(userId);
        }
    }

    @Override
    public void verifyPendingInstall(int id, int verificationCode) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT,
                "Only package verification agents can verify applications");

        final Message msg = mHandler.obtainMessage(PACKAGE_VERIFIED);
        final PackageVerificationResponse response = new PackageVerificationResponse(
                verificationCode, Binder.getCallingUid());
        msg.arg1 = id;
        msg.obj = response;
        mHandler.sendMessage(msg);
    }

    @Override
    public void extendVerificationTimeout(int id, int verificationCodeAtTimeout,
            long millisecondsToDelay) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT,
                "Only package verification agents can extend verification timeouts");

        final PackageVerificationState state = mPendingVerification.get(id);
        final PackageVerificationResponse response = new PackageVerificationResponse(
                verificationCodeAtTimeout, Binder.getCallingUid());

        if (millisecondsToDelay > PackageManager.MAXIMUM_VERIFICATION_TIMEOUT) {
            millisecondsToDelay = PackageManager.MAXIMUM_VERIFICATION_TIMEOUT;
        }
        if (millisecondsToDelay < 0) {
            millisecondsToDelay = 0;
        }
        if ((verificationCodeAtTimeout != PackageManager.VERIFICATION_ALLOW)
                && (verificationCodeAtTimeout != PackageManager.VERIFICATION_REJECT)) {
            verificationCodeAtTimeout = PackageManager.VERIFICATION_REJECT;
        }

        if ((state != null) && !state.timeoutExtended()) {
            state.extendTimeout();

            final Message msg = mHandler.obtainMessage(PACKAGE_VERIFIED);
            msg.arg1 = id;
            msg.obj = response;
            mHandler.sendMessageDelayed(msg, millisecondsToDelay);
        }
    }

    private void broadcastPackageVerified(int verificationId, Uri packageUri,
            int verificationCode, UserHandle user) {
        final Intent intent = new Intent(Intent.ACTION_PACKAGE_VERIFIED);
        intent.setDataAndType(packageUri, PACKAGE_MIME_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(PackageManager.EXTRA_VERIFICATION_ID, verificationId);
        intent.putExtra(PackageManager.EXTRA_VERIFICATION_RESULT, verificationCode);

        mContext.sendBroadcastAsUser(intent, user,
                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT);
    }

    private ComponentName matchComponentForVerifier(String packageName,
            List<ResolveInfo> receivers) {
        ActivityInfo targetReceiver = null;

        final int NR = receivers.size();
        for (int i = 0; i < NR; i++) {
            final ResolveInfo info = receivers.get(i);
            if (info.activityInfo == null) {
                continue;
            }

            if (packageName.equals(info.activityInfo.packageName)) {
                targetReceiver = info.activityInfo;
                break;
            }
        }

        if (targetReceiver == null) {
            return null;
        }

        return new ComponentName(targetReceiver.packageName, targetReceiver.name);
    }

    private List<ComponentName> matchVerifiers(PackageInfoLite pkgInfo,
            List<ResolveInfo> receivers, final PackageVerificationState verificationState) {
        if (pkgInfo.verifiers.length == 0) {
            return null;
        }

        final int N = pkgInfo.verifiers.length;
        final List<ComponentName> sufficientVerifiers = new ArrayList<ComponentName>(N + 1);
        for (int i = 0; i < N; i++) {
            final VerifierInfo verifierInfo = pkgInfo.verifiers[i];

            final ComponentName comp = matchComponentForVerifier(verifierInfo.packageName,
                    receivers);
            if (comp == null) {
                continue;
            }

            final int verifierUid = getUidForVerifier(verifierInfo);
            if (verifierUid == -1) {
                continue;
            }

            if (DEBUG_VERIFY) {
                Slog.d(TAG, "Added sufficient verifier " + verifierInfo.packageName
                        + " with the correct signature");
            }
            sufficientVerifiers.add(comp);
            verificationState.addSufficientVerifier(verifierUid);
        }

        return sufficientVerifiers;
    }

    private int getUidForVerifier(VerifierInfo verifierInfo) {
        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(verifierInfo.packageName);
            if (pkg == null) {
                return -1;
            } else if (pkg.mSignatures.length != 1) {
                Slog.i(TAG, "Verifier package " + verifierInfo.packageName
                        + " has more than one signature; ignoring");
                return -1;
            }

            /*
             * If the public key of the package's signature does not match
             * our expected public key, then this is a different package and
             * we should skip.
             */

            final byte[] expectedPublicKey;
            try {
                final Signature verifierSig = pkg.mSignatures[0];
                final PublicKey publicKey = verifierSig.getPublicKey();
                expectedPublicKey = publicKey.getEncoded();
            } catch (CertificateException e) {
                return -1;
            }

            final byte[] actualPublicKey = verifierInfo.publicKey.getEncoded();

            if (!Arrays.equals(actualPublicKey, expectedPublicKey)) {
                Slog.i(TAG, "Verifier package " + verifierInfo.packageName
                        + " does not have the expected public key; ignoring");
                return -1;
            }

            return pkg.applicationInfo.uid;
        }
    }

    @Override
    public void finishPackageInstall(int token, boolean didLaunch) {
        enforceSystemOrRoot("Only the system is allowed to finish installs");

        if (DEBUG_INSTALL) {
            Slog.v(TAG, "BM finishing package install for " + token);
        }
        Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "restore", token);

        final Message msg = mHandler.obtainMessage(POST_INSTALL, token, didLaunch ? 1 : 0);
        mHandler.sendMessage(msg);
    }

    /**
     * Get the verification agent timeout.
     *
     * @return verification timeout in milliseconds
     */
    private long getVerificationTimeout() {
        return android.provider.Settings.Global.getLong(mContext.getContentResolver(),
                android.provider.Settings.Global.PACKAGE_VERIFIER_TIMEOUT,
                DEFAULT_VERIFICATION_TIMEOUT);
    }

    /**
     * Get the default verification agent response code.
     *
     * @return default verification response code
     */
    private int getDefaultVerificationResponse() {
        return android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                android.provider.Settings.Global.PACKAGE_VERIFIER_DEFAULT_RESPONSE,
                DEFAULT_VERIFICATION_RESPONSE);
    }

    /**
     * Check whether or not package verification has been enabled.
     *
     * @return true if verification should be performed
     */
    private boolean isVerificationEnabled(int userId, int installFlags) {
        if (!DEFAULT_VERIFY_ENABLE) {
            return false;
        }
        // Ephemeral apps don't get the full verification treatment
        if ((installFlags & PackageManager.INSTALL_EPHEMERAL) != 0) {
            if (DEBUG_EPHEMERAL) {
                Slog.d(TAG, "INSTALL_EPHEMERAL so skipping verification");
            }
            return false;
        }

        boolean ensureVerifyAppsEnabled = isUserRestricted(userId, UserManager.ENSURE_VERIFY_APPS);

        // Check if installing from ADB
        if ((installFlags & PackageManager.INSTALL_FROM_ADB) != 0) {
            // Do not run verification in a test harness environment
            if (ActivityManager.isRunningInTestHarness()) {
                return false;
            }
            if (ensureVerifyAppsEnabled) {
                return true;
            }
            // Check if the developer does not want package verification for ADB installs
            if (android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                    android.provider.Settings.Global.PACKAGE_VERIFIER_INCLUDE_ADB, 1) == 0) {
                return false;
            }
        }

        if (ensureVerifyAppsEnabled) {
            return true;
        }

        return android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                android.provider.Settings.Global.PACKAGE_VERIFIER_ENABLE, 1) == 1;
    }

    @Override
    public void verifyIntentFilter(int id, int verificationCode, List<String> failedDomains)
            throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.INTENT_FILTER_VERIFICATION_AGENT,
                "Only intentfilter verification agents can verify applications");

        final Message msg = mHandler.obtainMessage(INTENT_FILTER_VERIFIED);
        final IntentFilterVerificationResponse response = new IntentFilterVerificationResponse(
                Binder.getCallingUid(), verificationCode, failedDomains);
        msg.arg1 = id;
        msg.obj = response;
        mHandler.sendMessage(msg);
    }

    @Override
    public int getIntentVerificationStatus(String packageName, int userId) {
        synchronized (mPackages) {
            return mSettings.getIntentFilterVerificationStatusLPr(packageName, userId);
        }
    }

    @Override
    public boolean updateIntentVerificationStatus(String packageName, int status, int userId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);

        boolean result = false;
        synchronized (mPackages) {
            result = mSettings.updateIntentFilterVerificationStatusLPw(packageName, status, userId);
        }
        if (result) {
            scheduleWritePackageRestrictionsLocked(userId);
        }
        return result;
    }

    @Override
    public @NonNull ParceledListSlice<IntentFilterVerificationInfo> getIntentFilterVerifications(
            String packageName) {
        synchronized (mPackages) {
            return new ParceledListSlice<>(mSettings.getIntentFilterVerificationsLPr(packageName));
        }
    }

    @Override
    public @NonNull ParceledListSlice<IntentFilter> getAllIntentFilters(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return ParceledListSlice.emptyList();
        }
        synchronized (mPackages) {
            PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null || pkg.activities == null) {
                return ParceledListSlice.emptyList();
            }
            final int count = pkg.activities.size();
            ArrayList<IntentFilter> result = new ArrayList<>();
            for (int n=0; n<count; n++) {
                PackageParser.Activity activity = pkg.activities.get(n);
                if (activity.intents != null && activity.intents.size() > 0) {
                    result.addAll(activity.intents);
                }
            }
            return new ParceledListSlice<>(result);
        }
    }

    @Override
    public boolean setDefaultBrowserPackageName(String packageName, int userId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);

        synchronized (mPackages) {
            boolean result = mSettings.setDefaultBrowserPackageNameLPw(packageName, userId);
            if (packageName != null) {
                result |= updateIntentVerificationStatus(packageName,
                        PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS,
                        userId);
                mDefaultPermissionPolicy.grantDefaultPermissionsToDefaultBrowserLPr(
                        packageName, userId);
            }
            return result;
        }
    }

    @Override
    public String getDefaultBrowserPackageName(int userId) {
        synchronized (mPackages) {
            return mSettings.getDefaultBrowserPackageNameLPw(userId);
        }
    }

    /**
     * Get the "allow unknown sources" setting.
     *
     * @return the current "allow unknown sources" setting
     */
    private int getUnknownSourcesSettings() {
        return android.provider.Settings.Secure.getInt(mContext.getContentResolver(),
                android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS,
                -1);
    }

    @Override
    public void setInstallerPackageName(String targetPackage, String installerPackageName) {
        final int uid = Binder.getCallingUid();
        // writer
        synchronized (mPackages) {
            PackageSetting targetPackageSetting = mSettings.mPackages.get(targetPackage);
            if (targetPackageSetting == null) {
                throw new IllegalArgumentException("Unknown target package: " + targetPackage);
            }

            PackageSetting installerPackageSetting;
            if (installerPackageName != null) {
                installerPackageSetting = mSettings.mPackages.get(installerPackageName);
                if (installerPackageSetting == null) {
                    throw new IllegalArgumentException("Unknown installer package: "
                            + installerPackageName);
                }
            } else {
                installerPackageSetting = null;
            }

            Signature[] callerSignature;
            Object obj = mSettings.getUserIdLPr(uid);
            if (obj != null) {
                if (obj instanceof SharedUserSetting) {
                    callerSignature = ((SharedUserSetting)obj).signatures.mSignatures;
                } else if (obj instanceof PackageSetting) {
                    callerSignature = ((PackageSetting)obj).signatures.mSignatures;
                } else {
                    throw new SecurityException("Bad object " + obj + " for uid " + uid);
                }
            } else {
                throw new SecurityException("Unknown calling UID: " + uid);
            }

            // Verify: can't set installerPackageName to a package that is
            // not signed with the same cert as the caller.
            if (installerPackageSetting != null) {
                if (compareSignatures(callerSignature,
                        installerPackageSetting.signatures.mSignatures)
                        != PackageManager.SIGNATURE_MATCH) {
                    throw new SecurityException(
                            "Caller does not have same cert as new installer package "
                            + installerPackageName);
                }
            }

            // Verify: if target already has an installer package, it must
            // be signed with the same cert as the caller.
            if (targetPackageSetting.installerPackageName != null) {
                PackageSetting setting = mSettings.mPackages.get(
                        targetPackageSetting.installerPackageName);
                // If the currently set package isn't valid, then it's always
                // okay to change it.
                if (setting != null) {
                    if (compareSignatures(callerSignature,
                            setting.signatures.mSignatures)
                            != PackageManager.SIGNATURE_MATCH) {
                        throw new SecurityException(
                                "Caller does not have same cert as old installer package "
                                + targetPackageSetting.installerPackageName);
                    }
                }
            }

            // Okay!
            targetPackageSetting.installerPackageName = installerPackageName;
            if (installerPackageName != null) {
                mSettings.mInstallerPackages.add(installerPackageName);
            }
            scheduleWriteSettingsLocked();
        }
    }

    private void processPendingInstall(final InstallArgs args, final int currentStatus) {
        // Queue up an async operation since the package installation may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                 // Result object to be returned
                PackageInstalledInfo res = new PackageInstalledInfo();
                res.setReturnCode(currentStatus);
                res.uid = -1;
                res.pkg = null;
                res.removedInfo = null;
                if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
                    args.doPreInstall(res.returnCode);
                    synchronized (mInstallLock) {
                        installPackageTracedLI(args, res);
                    }
                    args.doPostInstall(res.returnCode, res.uid);
                }

                // A restore should be performed at this point if (a) the install
                // succeeded, (b) the operation is not an update, and (c) the new
                // package has not opted out of backup participation.
                final boolean update = res.removedInfo != null
                        && res.removedInfo.removedPackage != null;
                final int flags = (res.pkg == null) ? 0 : res.pkg.applicationInfo.flags;
                boolean doRestore = !update
                        && ((flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0);

                // Set up the post-install work request bookkeeping.  This will be used
                // and cleaned up by the post-install event handling regardless of whether
                // there's a restore pass performed.  Token values are >= 1.
                int token;
                if (mNextInstallToken < 0) mNextInstallToken = 1;
                token = mNextInstallToken++;

                PostInstallData data = new PostInstallData(args, res);
                mRunningInstalls.put(token, data);
                if (DEBUG_INSTALL) Log.v(TAG, "+ starting restore round-trip " + token);

                if (res.returnCode == PackageManager.INSTALL_SUCCEEDED && doRestore) {
                    // Pass responsibility to the Backup Manager.  It will perform a
                    // restore if appropriate, then pass responsibility back to the
                    // Package Manager to run the post-install observer callbacks
                    // and broadcasts.
                    IBackupManager bm = IBackupManager.Stub.asInterface(
                            ServiceManager.getService(Context.BACKUP_SERVICE));
                    if (bm != null) {
                        if (DEBUG_INSTALL) Log.v(TAG, "token " + token
                                + " to BM for possible restore");
                        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "restore", token);
                        try {
                            // TODO: http://b/22388012
                            if (bm.isBackupServiceActive(UserHandle.USER_SYSTEM)) {
                                bm.restoreAtInstall(res.pkg.applicationInfo.packageName, token);
                            } else {
                                doRestore = false;
                            }
                        } catch (RemoteException e) {
                            // can't happen; the backup manager is local
                        } catch (Exception e) {
                            Slog.e(TAG, "Exception trying to enqueue restore", e);
                            doRestore = false;
                        }
                    } else {
                        Slog.e(TAG, "Backup Manager not found!");
                        doRestore = false;
                    }
                }

                if (!doRestore) {
                    // No restore possible, or the Backup Manager was mysteriously not
                    // available -- just fire the post-install work request directly.
                    if (DEBUG_INSTALL) Log.v(TAG, "No restore - queue post-install for " + token);

                    Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "postInstall", token);

                    Message msg = mHandler.obtainMessage(POST_INSTALL, token, 0);
                    mHandler.sendMessage(msg);
                }
            }
        });
    }

    /**
     * Callback from PackageSettings whenever an app is first transitioned out of the
     * 'stopped' state.  Normally we just issue the broadcast, but we can't do that if
     * the app was "launched" for a restoreAtInstall operation.  Therefore we check
     * here whether the app is the target of an ongoing install, and only send the
     * broadcast immediately if it is not in that state.  If it *is* undergoing a restore,
     * the first-launch broadcast will be sent implicitly on that basis in POST_INSTALL
     * handling.
     */
    void notifyFirstLaunch(final String pkgName, final String installerPackage, final int userId) {
        // Serialize this with the rest of the install-process message chain.  In the
        // restore-at-install case, this Runnable will necessarily run before the
        // POST_INSTALL message is processed, so the contents of mRunningInstalls
        // are coherent.  In the non-restore case, the app has already completed install
        // and been launched through some other means, so it is not in a problematic
        // state for observers to see the FIRST_LAUNCH signal.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mRunningInstalls.size(); i++) {
                    final PostInstallData data = mRunningInstalls.valueAt(i);
                    if (data.res.returnCode != PackageManager.INSTALL_SUCCEEDED) {
                        continue;
                    }
                    if (pkgName.equals(data.res.pkg.applicationInfo.packageName)) {
                        // right package; but is it for the right user?
                        for (int uIndex = 0; uIndex < data.res.newUsers.length; uIndex++) {
                            if (userId == data.res.newUsers[uIndex]) {
                                if (DEBUG_BACKUP) {
                                    Slog.i(TAG, "Package " + pkgName
                                            + " being restored so deferring FIRST_LAUNCH");
                                }
                                return;
                            }
                        }
                    }
                }
                // didn't find it, so not being restored
                if (DEBUG_BACKUP) {
                    Slog.i(TAG, "Package " + pkgName + " sending normal FIRST_LAUNCH");
                }
                sendFirstLaunchBroadcast(pkgName, installerPackage, new int[] {userId});
            }
        });
    }

    private void sendFirstLaunchBroadcast(String pkgName, String installerPkg, int[] userIds) {
        sendPackageBroadcast(Intent.ACTION_PACKAGE_FIRST_LAUNCH, pkgName, null, 0,
                installerPkg, null, userIds);
    }

    private abstract class HandlerParams {
        private static final int MAX_RETRIES = 4;

        /**
         * Number of times startCopy() has been attempted and had a non-fatal
         * error.
         */
        private int mRetries = 0;

        /** User handle for the user requesting the information or installation. */
        private final UserHandle mUser;
        String traceMethod;
        int traceCookie;

        HandlerParams(UserHandle user) {
            mUser = user;
        }

        UserHandle getUser() {
            return mUser;
        }

        HandlerParams setTraceMethod(String traceMethod) {
            this.traceMethod = traceMethod;
            return this;
        }

        HandlerParams setTraceCookie(int traceCookie) {
            this.traceCookie = traceCookie;
            return this;
        }

        final boolean startCopy() {
            boolean res;
            try {
                if (DEBUG_INSTALL) Slog.i(TAG, "startCopy " + mUser + ": " + this);

                if (++mRetries > MAX_RETRIES) {
                    Slog.w(TAG, "Failed to invoke remote methods on default container service. Giving up");
                    mHandler.sendEmptyMessage(MCS_GIVE_UP);
                    handleServiceError();
                    return false;
                } else {
                    handleStartCopy();
                    res = true;
                }
            } catch (RemoteException e) {
                if (DEBUG_INSTALL) Slog.i(TAG, "Posting install MCS_RECONNECT");
                mHandler.sendEmptyMessage(MCS_RECONNECT);
                res = false;
            }
            handleReturnCode();
            return res;
        }

        final void serviceError() {
            if (DEBUG_INSTALL) Slog.i(TAG, "serviceError");
            handleServiceError();
            handleReturnCode();
        }

        abstract void handleStartCopy() throws RemoteException;
        abstract void handleServiceError();
        abstract void handleReturnCode();
    }

    class MeasureParams extends HandlerParams {
        private final PackageStats mStats;
        private boolean mSuccess;

        private final IPackageStatsObserver mObserver;

        public MeasureParams(PackageStats stats, IPackageStatsObserver observer) {
            super(new UserHandle(stats.userHandle));
            mObserver = observer;
            mStats = stats;
        }

        @Override
        public String toString() {
            return "MeasureParams{"
                + Integer.toHexString(System.identityHashCode(this))
                + " " + mStats.packageName + "}";
        }

        @Override
        void handleStartCopy() throws RemoteException {
            synchronized (mInstallLock) {
                mSuccess = getPackageSizeInfoLI(mStats.packageName, mStats.userHandle, mStats);
            }

            if (mSuccess) {
                boolean mounted = false;
                try {
                    final String status = Environment.getExternalStorageState();
                    mounted = (Environment.MEDIA_MOUNTED.equals(status)
                            || Environment.MEDIA_MOUNTED_READ_ONLY.equals(status));
                } catch (Exception e) {
                }

                if (mounted) {
                    final UserEnvironment userEnv = new UserEnvironment(mStats.userHandle);

                    mStats.externalCacheSize = calculateDirectorySize(mContainerService,
                            userEnv.buildExternalStorageAppCacheDirs(mStats.packageName));

                    mStats.externalDataSize = calculateDirectorySize(mContainerService,
                            userEnv.buildExternalStorageAppDataDirs(mStats.packageName));

                    // Always subtract cache size, since it's a subdirectory
                    mStats.externalDataSize -= mStats.externalCacheSize;

                    mStats.externalMediaSize = calculateDirectorySize(mContainerService,
                            userEnv.buildExternalStorageAppMediaDirs(mStats.packageName));

                    mStats.externalObbSize = calculateDirectorySize(mContainerService,
                            userEnv.buildExternalStorageAppObbDirs(mStats.packageName));
                }
            }
        }

        @Override
        void handleReturnCode() {
            if (mObserver != null) {
                try {
                    mObserver.onGetStatsCompleted(mStats, mSuccess);
                } catch (RemoteException e) {
                    Slog.i(TAG, "Observer no longer exists.");
                }
            }
        }

        @Override
        void handleServiceError() {
            Slog.e(TAG, "Could not measure application " + mStats.packageName
                            + " external storage");
        }
    }

    private static long calculateDirectorySize(IMediaContainerService mcs, File[] paths)
            throws RemoteException {
        long result = 0;
        for (File path : paths) {
            result += mcs.calculateDirectorySize(path.getAbsolutePath());
        }
        return result;
    }

    private static void clearDirectory(IMediaContainerService mcs, File[] paths) {
        for (File path : paths) {
            try {
                mcs.clearDirectory(path.getAbsolutePath());
            } catch (RemoteException e) {
            }
        }
    }

    static class OriginInfo {
        /**
         * Location where install is coming from, before it has been
         * copied/renamed into place. This could be a single monolithic APK
         * file, or a cluster directory. This location may be untrusted.
         */
        final File file;
        final String cid;

        /**
         * Flag indicating that {@link #file} or {@link #cid} has already been
         * staged, meaning downstream users don't need to defensively copy the
         * contents.
         */
        final boolean staged;

        /**
         * Flag indicating that {@link #file} or {@link #cid} is an already
         * installed app that is being moved.
         */
        final boolean existing;

        final String resolvedPath;
        final File resolvedFile;

        static OriginInfo fromNothing() {
            return new OriginInfo(null, null, false, false);
        }

        static OriginInfo fromUntrustedFile(File file) {
            return new OriginInfo(file, null, false, false);
        }

        static OriginInfo fromExistingFile(File file) {
            return new OriginInfo(file, null, false, true);
        }

        static OriginInfo fromStagedFile(File file) {
            return new OriginInfo(file, null, true, false);
        }

        static OriginInfo fromStagedContainer(String cid) {
            return new OriginInfo(null, cid, true, false);
        }

        private OriginInfo(File file, String cid, boolean staged, boolean existing) {
            this.file = file;
            this.cid = cid;
            this.staged = staged;
            this.existing = existing;

            if (cid != null) {
                resolvedPath = PackageHelper.getSdDir(cid);
                resolvedFile = new File(resolvedPath);
            } else if (file != null) {
                resolvedPath = file.getAbsolutePath();
                resolvedFile = file;
            } else {
                resolvedPath = null;
                resolvedFile = null;
            }
        }
    }

    static class MoveInfo {
        final int moveId;
        final String fromUuid;
        final String toUuid;
        final String packageName;
        final String dataAppName;
        final int appId;
        final String seinfo;
        final int targetSdkVersion;

        public MoveInfo(int moveId, String fromUuid, String toUuid, String packageName,
                String dataAppName, int appId, String seinfo, int targetSdkVersion) {
            this.moveId = moveId;
            this.fromUuid = fromUuid;
            this.toUuid = toUuid;
            this.packageName = packageName;
            this.dataAppName = dataAppName;
            this.appId = appId;
            this.seinfo = seinfo;
            this.targetSdkVersion = targetSdkVersion;
        }
    }

    static class VerificationInfo {
        /** A constant used to indicate that a uid value is not present. */
        public static final int NO_UID = -1;

        /** URI referencing where the package was downloaded from. */
        final Uri originatingUri;

        /** HTTP referrer URI associated with the originatingURI. */
        final Uri referrer;

        /** UID of the application that the install request originated from. */
        final int originatingUid;

        /** UID of application requesting the install */
        final int installerUid;

        VerificationInfo(Uri originatingUri, Uri referrer, int originatingUid, int installerUid) {
            this.originatingUri = originatingUri;
            this.referrer = referrer;
            this.originatingUid = originatingUid;
            this.installerUid = installerUid;
        }
    }

    class InstallParams extends HandlerParams {
        final OriginInfo origin;
        final MoveInfo move;
        final IPackageInstallObserver2 observer;
        int installFlags;
        final String installerPackageName;
        final String volumeUuid;
        private InstallArgs mArgs;
        private int mRet;
        final String packageAbiOverride;
        final String[] grantedRuntimePermissions;
        final VerificationInfo verificationInfo;
        final Certificate[][] certificates;

        InstallParams(OriginInfo origin, MoveInfo move, IPackageInstallObserver2 observer,
                int installFlags, String installerPackageName, String volumeUuid,
                VerificationInfo verificationInfo, UserHandle user, String packageAbiOverride,
                String[] grantedPermissions, Certificate[][] certificates) {
            super(user);
            this.origin = origin;
            this.move = move;
            this.observer = observer;
            this.installFlags = installFlags;
            this.installerPackageName = installerPackageName;
            this.volumeUuid = volumeUuid;
            this.verificationInfo = verificationInfo;
            this.packageAbiOverride = packageAbiOverride;
            this.grantedRuntimePermissions = grantedPermissions;
            this.certificates = certificates;
        }

        @Override
        public String toString() {
            return "InstallParams{" + Integer.toHexString(System.identityHashCode(this))
                    + " file=" + origin.file + " cid=" + origin.cid + "}";
        }

        private int installLocationPolicy(PackageInfoLite pkgLite) {
            String packageName = pkgLite.packageName;
            int installLocation = pkgLite.installLocation;
            boolean onSd = (installFlags & PackageManager.INSTALL_EXTERNAL) != 0;
            // reader
            synchronized (mPackages) {
                // Currently installed package which the new package is attempting to replace or
                // null if no such package is installed.
                PackageParser.Package installedPkg = mPackages.get(packageName);
                // Package which currently owns the data which the new package will own if installed.
                // If an app is unstalled while keeping data (e.g., adb uninstall -k), installedPkg
                // will be null whereas dataOwnerPkg will contain information about the package
                // which was uninstalled while keeping its data.
                PackageParser.Package dataOwnerPkg = installedPkg;
                if (dataOwnerPkg  == null) {
                    PackageSetting ps = mSettings.mPackages.get(packageName);
                    if (ps != null) {
                        dataOwnerPkg = ps.pkg;
                    }
                }

                if (dataOwnerPkg != null) {
                    // If installed, the package will get access to data left on the device by its
                    // predecessor. As a security measure, this is permited only if this is not a
                    // version downgrade or if the predecessor package is marked as debuggable and
                    // a downgrade is explicitly requested.
                    //
                    // On debuggable platform builds, downgrades are permitted even for
                    // non-debuggable packages to make testing easier. Debuggable platform builds do
                    // not offer security guarantees and thus it's OK to disable some security
                    // mechanisms to make debugging/testing easier on those builds. However, even on
                    // debuggable builds downgrades of packages are permitted only if requested via
                    // installFlags. This is because we aim to keep the behavior of debuggable
                    // platform builds as close as possible to the behavior of non-debuggable
                    // platform builds.
                    final boolean downgradeRequested =
                            (installFlags & PackageManager.INSTALL_ALLOW_DOWNGRADE) != 0;
                    final boolean packageDebuggable =
                                (dataOwnerPkg.applicationInfo.flags
                                        & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                    final boolean downgradePermitted =
                            (downgradeRequested) && ((Build.IS_DEBUGGABLE) || (packageDebuggable));
                    if (!downgradePermitted) {
                        try {
                            checkDowngrade(dataOwnerPkg, pkgLite);
                        } catch (PackageManagerException e) {
                            Slog.w(TAG, "Downgrade detected: " + e.getMessage());
                            return PackageHelper.RECOMMEND_FAILED_VERSION_DOWNGRADE;
                        }
                    }
                }

                if (installedPkg != null) {
                    if ((installFlags & PackageManager.INSTALL_REPLACE_EXISTING) != 0) {
                        // Check for updated system application.
                        if ((installedPkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            if (onSd) {
                                Slog.w(TAG, "Cannot install update to system app on sdcard");
                                return PackageHelper.RECOMMEND_FAILED_INVALID_LOCATION;
                            }
                            return PackageHelper.RECOMMEND_INSTALL_INTERNAL;
                        } else {
                            if (onSd) {
                                // Install flag overrides everything.
                                return PackageHelper.RECOMMEND_INSTALL_EXTERNAL;
                            }
                            // If current upgrade specifies particular preference
                            if (installLocation == PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY) {
                                // Application explicitly specified internal.
                                return PackageHelper.RECOMMEND_INSTALL_INTERNAL;
                            } else if (installLocation == PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL) {
                                // App explictly prefers external. Let policy decide
                            } else {
                                // Prefer previous location
                                if (isExternal(installedPkg)) {
                                    return PackageHelper.RECOMMEND_INSTALL_EXTERNAL;
                                }
                                return PackageHelper.RECOMMEND_INSTALL_INTERNAL;
                            }
                        }
                    } else {
                        // Invalid install. Return error code
                        return PackageHelper.RECOMMEND_FAILED_ALREADY_EXISTS;
                    }
                }
            }
            // All the special cases have been taken care of.
            // Return result based on recommended install location.
            if (onSd) {
                return PackageHelper.RECOMMEND_INSTALL_EXTERNAL;
            }
            return pkgLite.recommendedInstallLocation;
        }

        /*
         * Invoke remote method to get package information and install
         * location values. Override install location based on default
         * policy if needed and then create install arguments based
         * on the install location.
         */
        public void handleStartCopy() throws RemoteException {
            int ret = PackageManager.INSTALL_SUCCEEDED;

            // If we're already staged, we've firmly committed to an install location
            if (origin.staged) {
                if (origin.file != null) {
                    installFlags |= PackageManager.INSTALL_INTERNAL;
                    installFlags &= ~PackageManager.INSTALL_EXTERNAL;
                } else if (origin.cid != null) {
                    installFlags |= PackageManager.INSTALL_EXTERNAL;
                    installFlags &= ~PackageManager.INSTALL_INTERNAL;
                } else {
                    throw new IllegalStateException("Invalid stage location");
                }
            }

            final boolean onSd = (installFlags & PackageManager.INSTALL_EXTERNAL) != 0;
            final boolean onInt = (installFlags & PackageManager.INSTALL_INTERNAL) != 0;
            final boolean ephemeral = (installFlags & PackageManager.INSTALL_EPHEMERAL) != 0;
            PackageInfoLite pkgLite = null;

            if (onInt && onSd) {
                // Check if both bits are set.
                Slog.w(TAG, "Conflicting flags specified for installing on both internal and external");
                ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
            } else if (onSd && ephemeral) {
                Slog.w(TAG,  "Conflicting flags specified for installing ephemeral on external");
                ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
            } else {
                pkgLite = mContainerService.getMinimalPackageInfo(origin.resolvedPath, installFlags,
                        packageAbiOverride);

                if (DEBUG_EPHEMERAL && ephemeral) {
                    Slog.v(TAG, "pkgLite for install: " + pkgLite);
                }

                /*
                 * If we have too little free space, try to free cache
                 * before giving up.
                 */
                if (!origin.staged && pkgLite.recommendedInstallLocation
                        == PackageHelper.RECOMMEND_FAILED_INSUFFICIENT_STORAGE) {
                    // TODO: focus freeing disk space on the target device
                    final StorageManager storage = StorageManager.from(mContext);
                    final long lowThreshold = storage.getStorageLowBytes(
                            Environment.getDataDirectory());

                    final long sizeBytes = mContainerService.calculateInstalledSize(
                            origin.resolvedPath, isForwardLocked(), packageAbiOverride);

                    try {
                        mInstaller.freeCache(null, sizeBytes + lowThreshold);
                        pkgLite = mContainerService.getMinimalPackageInfo(origin.resolvedPath,
                                installFlags, packageAbiOverride);
                    } catch (InstallerException e) {
                        Slog.w(TAG, "Failed to free cache", e);
                    }

                    /*
                     * The cache free must have deleted the file we
                     * downloaded to install.
                     *
                     * TODO: fix the "freeCache" call to not delete
                     *       the file we care about.
                     */
                    if (pkgLite.recommendedInstallLocation
                            == PackageHelper.RECOMMEND_FAILED_INVALID_URI) {
                        pkgLite.recommendedInstallLocation
                            = PackageHelper.RECOMMEND_FAILED_INSUFFICIENT_STORAGE;
                    }
                }
            }

            if (ret == PackageManager.INSTALL_SUCCEEDED) {
                int loc = pkgLite.recommendedInstallLocation;
                if (loc == PackageHelper.RECOMMEND_FAILED_INVALID_LOCATION) {
                    ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
                } else if (loc == PackageHelper.RECOMMEND_FAILED_ALREADY_EXISTS) {
                    ret = PackageManager.INSTALL_FAILED_ALREADY_EXISTS;
                } else if (loc == PackageHelper.RECOMMEND_FAILED_INSUFFICIENT_STORAGE) {
                    ret = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                } else if (loc == PackageHelper.RECOMMEND_FAILED_INVALID_APK) {
                    ret = PackageManager.INSTALL_FAILED_INVALID_APK;
                } else if (loc == PackageHelper.RECOMMEND_FAILED_INVALID_URI) {
                    ret = PackageManager.INSTALL_FAILED_INVALID_URI;
                } else if (loc == PackageHelper.RECOMMEND_MEDIA_UNAVAILABLE) {
                    ret = PackageManager.INSTALL_FAILED_MEDIA_UNAVAILABLE;
                } else {
                    // Override with defaults if needed.
                    loc = installLocationPolicy(pkgLite);
                    if (loc == PackageHelper.RECOMMEND_FAILED_VERSION_DOWNGRADE) {
                        ret = PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE;
                    } else if (!onSd && !onInt) {
                        // Override install location with flags
                        if (loc == PackageHelper.RECOMMEND_INSTALL_EXTERNAL) {
                            // Set the flag to install on external media.
                            installFlags |= PackageManager.INSTALL_EXTERNAL;
                            installFlags &= ~PackageManager.INSTALL_INTERNAL;
                        } else if (loc == PackageHelper.RECOMMEND_INSTALL_EPHEMERAL) {
                            if (DEBUG_EPHEMERAL) {
                                Slog.v(TAG, "...setting INSTALL_EPHEMERAL install flag");
                            }
                            installFlags |= PackageManager.INSTALL_EPHEMERAL;
                            installFlags &= ~(PackageManager.INSTALL_EXTERNAL
                                    |PackageManager.INSTALL_INTERNAL);
                        } else {
                            // Make sure the flag for installing on external
                            // media is unset
                            installFlags |= PackageManager.INSTALL_INTERNAL;
                            installFlags &= ~PackageManager.INSTALL_EXTERNAL;
                        }
                    }
                }
            }

            final InstallArgs args = createInstallArgs(this);
            mArgs = args;

            if (ret == PackageManager.INSTALL_SUCCEEDED) {
                // TODO: http://b/22976637
                // Apps installed for "all" users use the device owner to verify the app
                UserHandle verifierUser = getUser();
                if (verifierUser == UserHandle.ALL) {
                    verifierUser = UserHandle.SYSTEM;
                }

                /*
                 * Determine if we have any installed package verifiers. If we
                 * do, then we'll defer to them to verify the packages.
                 */
                final int requiredUid = mRequiredVerifierPackage == null ? -1
                        : getPackageUid(mRequiredVerifierPackage, MATCH_DEBUG_TRIAGED_MISSING,
                                verifierUser.getIdentifier());
                if (!origin.existing && requiredUid != -1
                        && isVerificationEnabled(verifierUser.getIdentifier(), installFlags)) {
                    final Intent verification = new Intent(
                            Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
                    verification.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    verification.setDataAndType(Uri.fromFile(new File(origin.resolvedPath)),
                            PACKAGE_MIME_TYPE);
                    verification.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // Query all live verifiers based on current user state
                    final List<ResolveInfo> receivers = queryIntentReceiversInternal(verification,
                            PACKAGE_MIME_TYPE, 0, verifierUser.getIdentifier());

                    if (DEBUG_VERIFY) {
                        Slog.d(TAG, "Found " + receivers.size() + " verifiers for intent "
                                + verification.toString() + " with " + pkgLite.verifiers.length
                                + " optional verifiers");
                    }

                    final int verificationId = mPendingVerificationToken++;

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_ID, verificationId);

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_INSTALLER_PACKAGE,
                            installerPackageName);

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_INSTALL_FLAGS,
                            installFlags);

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_PACKAGE_NAME,
                            pkgLite.packageName);

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_VERSION_CODE,
                            pkgLite.versionCode);

                    if (verificationInfo != null) {
                        if (verificationInfo.originatingUri != null) {
                            verification.putExtra(Intent.EXTRA_ORIGINATING_URI,
                                    verificationInfo.originatingUri);
                        }
                        if (verificationInfo.referrer != null) {
                            verification.putExtra(Intent.EXTRA_REFERRER,
                                    verificationInfo.referrer);
                        }
                        if (verificationInfo.originatingUid >= 0) {
                            verification.putExtra(Intent.EXTRA_ORIGINATING_UID,
                                    verificationInfo.originatingUid);
                        }
                        if (verificationInfo.installerUid >= 0) {
                            verification.putExtra(PackageManager.EXTRA_VERIFICATION_INSTALLER_UID,
                                    verificationInfo.installerUid);
                        }
                    }

                    final PackageVerificationState verificationState = new PackageVerificationState(
                            requiredUid, args);

                    mPendingVerification.append(verificationId, verificationState);

                    final List<ComponentName> sufficientVerifiers = matchVerifiers(pkgLite,
                            receivers, verificationState);

                    /*
                     * If any sufficient verifiers were listed in the package
                     * manifest, attempt to ask them.
                     */
                    if (sufficientVerifiers != null) {
                        final int N = sufficientVerifiers.size();
                        if (N == 0) {
                            Slog.i(TAG, "Additional verifiers required, but none installed.");
                            ret = PackageManager.INSTALL_FAILED_VERIFICATION_FAILURE;
                        } else {
                            for (int i = 0; i < N; i++) {
                                final ComponentName verifierComponent = sufficientVerifiers.get(i);

                                final Intent sufficientIntent = new Intent(verification);
                                sufficientIntent.setComponent(verifierComponent);
                                mContext.sendBroadcastAsUser(sufficientIntent, verifierUser);
                            }
                        }
                    }

                    final ComponentName requiredVerifierComponent = matchComponentForVerifier(
                            mRequiredVerifierPackage, receivers);
                    if (ret == PackageManager.INSTALL_SUCCEEDED
                            && mRequiredVerifierPackage != null) {
                        Trace.asyncTraceBegin(
                                TRACE_TAG_PACKAGE_MANAGER, "verification", verificationId);
                        /*
                         * Send the intent to the required verification agent,
                         * but only start the verification timeout after the
                         * target BroadcastReceivers have run.
                         */
                        verification.setComponent(requiredVerifierComponent);
                        mContext.sendOrderedBroadcastAsUser(verification, verifierUser,
                                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT,
                                new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        final Message msg = mHandler
                                                .obtainMessage(CHECK_PENDING_VERIFICATION);
                                        msg.arg1 = verificationId;
                                        mHandler.sendMessageDelayed(msg, getVerificationTimeout());
                                    }
                                }, null, 0, null, null);

                        /*
                         * We don't want the copy to proceed until verification
                         * succeeds, so null out this field.
                         */
                        mArgs = null;
                    }
                } else {
                    /*
                     * No package verification is enabled, so immediately start
                     * the remote call to initiate copy using temporary file.
                     */
                    ret = args.copyApk(mContainerService, true);
                }
            }

            mRet = ret;
        }

        @Override
        void handleReturnCode() {
            // If mArgs is null, then MCS couldn't be reached. When it
            // reconnects, it will try again to install. At that point, this
            // will succeed.
            if (mArgs != null) {
                processPendingInstall(mArgs, mRet);
            }
        }

        @Override
        void handleServiceError() {
            mArgs = createInstallArgs(this);
            mRet = PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }

        public boolean isForwardLocked() {
            return (installFlags & PackageManager.INSTALL_FORWARD_LOCK) != 0;
        }
    }

    /**
     * Used during creation of InstallArgs
     *
     * @param installFlags package installation flags
     * @return true if should be installed on external storage
     */
    private static boolean installOnExternalAsec(int installFlags) {
        if ((installFlags & PackageManager.INSTALL_INTERNAL) != 0) {
            return false;
        }
        if ((installFlags & PackageManager.INSTALL_EXTERNAL) != 0) {
            return true;
        }
        return false;
    }

    /**
     * Used during creation of InstallArgs
     *
     * @param installFlags package installation flags
     * @return true if should be installed as forward locked
     */
    private static boolean installForwardLocked(int installFlags) {
        return (installFlags & PackageManager.INSTALL_FORWARD_LOCK) != 0;
    }

    private InstallArgs createInstallArgs(InstallParams params) {
        if (params.move != null) {
            return new MoveInstallArgs(params);
        } else if (installOnExternalAsec(params.installFlags) || params.isForwardLocked()) {
            return new AsecInstallArgs(params);
        } else {
            return new FileInstallArgs(params);
        }
    }

    /**
     * Create args that describe an existing installed package. Typically used
     * when cleaning up old installs, or used as a move source.
     */
    private InstallArgs createInstallArgsForExisting(int installFlags, String codePath,
            String resourcePath, String[] instructionSets) {
        final boolean isInAsec;
        if (installOnExternalAsec(installFlags)) {
            /* Apps on SD card are always in ASEC containers. */
            isInAsec = true;
        } else if (installForwardLocked(installFlags)
                && !codePath.startsWith(mDrmAppPrivateInstallDir.getAbsolutePath())) {
            /*
             * Forward-locked apps are only in ASEC containers if they're the
             * new style
             */
            isInAsec = true;
        } else {
            isInAsec = false;
        }

        if (isInAsec) {
            return new AsecInstallArgs(codePath, instructionSets,
                    installOnExternalAsec(installFlags), installForwardLocked(installFlags));
        } else {
            return new FileInstallArgs(codePath, resourcePath, instructionSets);
        }
    }

    static abstract class InstallArgs {
        /** @see InstallParams#origin */
        final OriginInfo origin;
        /** @see InstallParams#move */
        final MoveInfo move;

        final IPackageInstallObserver2 observer;
        // Always refers to PackageManager flags only
        final int installFlags;
        final String installerPackageName;
        final String volumeUuid;
        final UserHandle user;
        final String abiOverride;
        final String[] installGrantPermissions;
        /** If non-null, drop an async trace when the install completes */
        final String traceMethod;
        final int traceCookie;
        final Certificate[][] certificates;

        // The list of instruction sets supported by this app. This is currently
        // only used during the rmdex() phase to clean up resources. We can get rid of this
        // if we move dex files under the common app path.
        /* nullable */ String[] instructionSets;

        InstallArgs(OriginInfo origin, MoveInfo move, IPackageInstallObserver2 observer,
                int installFlags, String installerPackageName, String volumeUuid,
                UserHandle user, String[] instructionSets,
                String abiOverride, String[] installGrantPermissions,
                String traceMethod, int traceCookie, Certificate[][] certificates) {
            this.origin = origin;
            this.move = move;
            this.installFlags = installFlags;
            this.observer = observer;
            this.installerPackageName = installerPackageName;
            this.volumeUuid = volumeUuid;
            this.user = user;
            this.instructionSets = instructionSets;
            this.abiOverride = abiOverride;
            this.installGrantPermissions = installGrantPermissions;
            this.traceMethod = traceMethod;
            this.traceCookie = traceCookie;
            this.certificates = certificates;
        }

        abstract int copyApk(IMediaContainerService imcs, boolean temp) throws RemoteException;
        abstract int doPreInstall(int status);

        /**
         * Rename package into final resting place. All paths on the given
         * scanned package should be updated to reflect the rename.
         */
        abstract boolean doRename(int status, PackageParser.Package pkg, String oldCodePath);
        abstract int doPostInstall(int status, int uid);

        /** @see PackageSettingBase#codePathString */
        abstract String getCodePath();
        /** @see PackageSettingBase#resourcePathString */
        abstract String getResourcePath();

        // Need installer lock especially for dex file removal.
        abstract void cleanUpResourcesLI();
        abstract boolean doPostDeleteLI(boolean delete);

        /**
         * Called before the source arguments are copied. This is used mostly
         * for MoveParams when it needs to read the source file to put it in the
         * destination.
         */
        int doPreCopy() {
            return PackageManager.INSTALL_SUCCEEDED;
        }

        /**
         * Called after the source arguments are copied. This is used mostly for
         * MoveParams when it needs to read the source file to put it in the
         * destination.
         */
        int doPostCopy(int uid) {
            return PackageManager.INSTALL_SUCCEEDED;
        }

        protected boolean isFwdLocked() {
            return (installFlags & PackageManager.INSTALL_FORWARD_LOCK) != 0;
        }

        protected boolean isExternalAsec() {
            return (installFlags & PackageManager.INSTALL_EXTERNAL) != 0;
        }

        protected boolean isEphemeral() {
            return (installFlags & PackageManager.INSTALL_EPHEMERAL) != 0;
        }

        UserHandle getUser() {
            return user;
        }
    }

    private void removeDexFiles(List<String> allCodePaths, String[] instructionSets) {
        if (!allCodePaths.isEmpty()) {
            if (instructionSets == null) {
                throw new IllegalStateException("instructionSet == null");
            }
            String[] dexCodeInstructionSets = getDexCodeInstructionSets(instructionSets);
            for (String codePath : allCodePaths) {
                for (String dexCodeInstructionSet : dexCodeInstructionSets) {
                    try {
                        mInstaller.rmdex(codePath, dexCodeInstructionSet);
                    } catch (InstallerException ignored) {
                    }
                }
            }
        }
    }

    /**
     * Logic to handle installation of non-ASEC applications, including copying
     * and renaming logic.
     */
    class FileInstallArgs extends InstallArgs {
        private File codeFile;
        private File resourceFile;

        // Example topology:
        // /data/app/com.example/base.apk
        // /data/app/com.example/split_foo.apk
        // /data/app/com.example/lib/arm/libfoo.so
        // /data/app/com.example/lib/arm64/libfoo.so
        // /data/app/com.example/dalvik/arm/base.apk@classes.dex

        /** New install */
        FileInstallArgs(InstallParams params) {
            super(params.origin, params.move, params.observer, params.installFlags,
                    params.installerPackageName, params.volumeUuid,
                    params.getUser(), null /*instructionSets*/, params.packageAbiOverride,
                    params.grantedRuntimePermissions,
                    params.traceMethod, params.traceCookie, params.certificates);
            if (isFwdLocked()) {
                throw new IllegalArgumentException("Forward locking only supported in ASEC");
            }
        }

        /** Existing install */
        FileInstallArgs(String codePath, String resourcePath, String[] instructionSets) {
            super(OriginInfo.fromNothing(), null, null, 0, null, null, null, instructionSets,
                    null, null, null, 0, null /*certificates*/);
            this.codeFile = (codePath != null) ? new File(codePath) : null;
            this.resourceFile = (resourcePath != null) ? new File(resourcePath) : null;
        }

        int copyApk(IMediaContainerService imcs, boolean temp) throws RemoteException {
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "copyApk");
            try {
                return doCopyApk(imcs, temp);
            } finally {
                Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
            }
        }

        private int doCopyApk(IMediaContainerService imcs, boolean temp) throws RemoteException {
            if (origin.staged) {
                if (DEBUG_INSTALL) Slog.d(TAG, origin.file + " already staged; skipping copy");
                codeFile = origin.file;
                resourceFile = origin.file;
                return PackageManager.INSTALL_SUCCEEDED;
            }

            try {
                final boolean isEphemeral = (installFlags & PackageManager.INSTALL_EPHEMERAL) != 0;
                final File tempDir =
                        mInstallerService.allocateStageDirLegacy(volumeUuid, isEphemeral);
                codeFile = tempDir;
                resourceFile = tempDir;
            } catch (IOException e) {
                Slog.w(TAG, "Failed to create copy file: " + e);
                return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
            }

            final IParcelFileDescriptorFactory target = new IParcelFileDescriptorFactory.Stub() {
                @Override
                public ParcelFileDescriptor open(String name, int mode) throws RemoteException {
                    if (!FileUtils.isValidExtFilename(name)) {
                        throw new IllegalArgumentException("Invalid filename: " + name);
                    }
                    try {
                        final File file = new File(codeFile, name);
                        final FileDescriptor fd = Os.open(file.getAbsolutePath(),
                                O_RDWR | O_CREAT, 0644);
                        Os.chmod(file.getAbsolutePath(), 0644);
                        return new ParcelFileDescriptor(fd);
                    } catch (ErrnoException e) {
                        throw new RemoteException("Failed to open: " + e.getMessage());
                    }
                }
            };

            int ret = PackageManager.INSTALL_SUCCEEDED;
            ret = imcs.copyPackage(origin.file.getAbsolutePath(), target);
            if (ret != PackageManager.INSTALL_SUCCEEDED) {
                Slog.e(TAG, "Failed to copy package");
                return ret;
            }

            final File libraryRoot = new File(codeFile, LIB_DIR_NAME);
            NativeLibraryHelper.Handle handle = null;
            try {
                handle = NativeLibraryHelper.Handle.create(codeFile);
                ret = NativeLibraryHelper.copyNativeBinariesWithOverride(handle, libraryRoot,
                        abiOverride);
            } catch (IOException e) {
                Slog.e(TAG, "Copying native libraries failed", e);
                ret = PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
            } finally {
                IoUtils.closeQuietly(handle);
            }

            return ret;
        }

        int doPreInstall(int status) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
            }
            return status;
        }

        boolean doRename(int status, PackageParser.Package pkg, String oldCodePath) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
                return false;
            }

            final File targetDir = codeFile.getParentFile();
            final File beforeCodeFile = codeFile;
            final File afterCodeFile = getNextCodePath(targetDir, pkg.packageName);

            if (DEBUG_INSTALL) Slog.d(TAG, "Renaming " + beforeCodeFile + " to " + afterCodeFile);
            try {
                Os.rename(beforeCodeFile.getAbsolutePath(), afterCodeFile.getAbsolutePath());
            } catch (ErrnoException e) {
                Slog.w(TAG, "Failed to rename", e);
                return false;
            }

            if (!SELinux.restoreconRecursive(afterCodeFile)) {
                Slog.w(TAG, "Failed to restorecon");
                return false;
            }

            // Reflect the rename internally
            codeFile = afterCodeFile;
            resourceFile = afterCodeFile;

            // Reflect the rename in scanned details
            pkg.setCodePath(afterCodeFile.getAbsolutePath());
            pkg.setBaseCodePath(FileUtils.rewriteAfterRename(beforeCodeFile,
                    afterCodeFile, pkg.baseCodePath));
            pkg.setSplitCodePaths(FileUtils.rewriteAfterRename(beforeCodeFile,
                    afterCodeFile, pkg.splitCodePaths));

            // Reflect the rename in app info
            pkg.setApplicationVolumeUuid(pkg.volumeUuid);
            pkg.setApplicationInfoCodePath(pkg.codePath);
            pkg.setApplicationInfoBaseCodePath(pkg.baseCodePath);
            pkg.setApplicationInfoSplitCodePaths(pkg.splitCodePaths);
            pkg.setApplicationInfoResourcePath(pkg.codePath);
            pkg.setApplicationInfoBaseResourcePath(pkg.baseCodePath);
            pkg.setApplicationInfoSplitResourcePaths(pkg.splitCodePaths);

            return true;
        }

        int doPostInstall(int status, int uid) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
            }
            return status;
        }

        @Override
        String getCodePath() {
            return (codeFile != null) ? codeFile.getAbsolutePath() : null;
        }

        @Override
        String getResourcePath() {
            return (resourceFile != null) ? resourceFile.getAbsolutePath() : null;
        }

        private boolean cleanUp() {
            if (codeFile == null || !codeFile.exists()) {
                return false;
            }

            removeCodePathLI(codeFile);

            if (resourceFile != null && !FileUtils.contains(codeFile, resourceFile)) {
                resourceFile.delete();
            }

            return true;
        }

        void cleanUpResourcesLI() {
            // Try enumerating all code paths before deleting
            List<String> allCodePaths = Collections.EMPTY_LIST;
            if (codeFile != null && codeFile.exists()) {
                try {
                    final PackageLite pkg = PackageParser.parsePackageLite(codeFile, 0);
                    allCodePaths = pkg.getAllCodePaths();
                } catch (PackageParserException e) {
                    // Ignored; we tried our best
                }
            }

            cleanUp();
            removeDexFiles(allCodePaths, instructionSets);
        }

        boolean doPostDeleteLI(boolean delete) {
            // XXX err, shouldn't we respect the delete flag?
            cleanUpResourcesLI();
            return true;
        }
    }

    private boolean isAsecExternal(String cid) {
        final String asecPath = PackageHelper.getSdFilesystem(cid);
        return !asecPath.startsWith(mAsecInternalPath);
    }

    private static void maybeThrowExceptionForMultiArchCopy(String message, int copyRet) throws
            PackageManagerException {
        if (copyRet < 0) {
            if (copyRet != PackageManager.NO_NATIVE_LIBRARIES &&
                    copyRet != PackageManager.INSTALL_FAILED_NO_MATCHING_ABIS) {
                throw new PackageManagerException(copyRet, message);
            }
        }
    }

    /**
     * Extract the MountService "container ID" from the full code path of an
     * .apk.
     */
    static String cidFromCodePath(String fullCodePath) {
        int eidx = fullCodePath.lastIndexOf("/");
        String subStr1 = fullCodePath.substring(0, eidx);
        int sidx = subStr1.lastIndexOf("/");
        return subStr1.substring(sidx+1, eidx);
    }

    /**
     * Logic to handle installation of ASEC applications, including copying and
     * renaming logic.
     */
    class AsecInstallArgs extends InstallArgs {
        static final String RES_FILE_NAME = "pkg.apk";
        static final String PUBLIC_RES_FILE_NAME = "res.zip";

        String cid;
        String packagePath;
        String resourcePath;

        /** New install */
        AsecInstallArgs(InstallParams params) {
            super(params.origin, params.move, params.observer, params.installFlags,
                    params.installerPackageName, params.volumeUuid,
                    params.getUser(), null /* instruction sets */, params.packageAbiOverride,
                    params.grantedRuntimePermissions,
                    params.traceMethod, params.traceCookie, params.certificates);
        }

        /** Existing install */
        AsecInstallArgs(String fullCodePath, String[] instructionSets,
                        boolean isExternal, boolean isForwardLocked) {
            super(OriginInfo.fromNothing(), null, null, (isExternal ? INSTALL_EXTERNAL : 0)
              | (isForwardLocked ? INSTALL_FORWARD_LOCK : 0), null, null, null,
                    instructionSets, null, null, null, 0, null /*certificates*/);
            // Hackily pretend we're still looking at a full code path
            if (!fullCodePath.endsWith(RES_FILE_NAME)) {
                fullCodePath = new File(fullCodePath, RES_FILE_NAME).getAbsolutePath();
            }

            // Extract cid from fullCodePath
            int eidx = fullCodePath.lastIndexOf("/");
            String subStr1 = fullCodePath.substring(0, eidx);
            int sidx = subStr1.lastIndexOf("/");
            cid = subStr1.substring(sidx+1, eidx);
            setMountPath(subStr1);
        }

        AsecInstallArgs(String cid, String[] instructionSets, boolean isForwardLocked) {
            super(OriginInfo.fromNothing(), null, null, (isAsecExternal(cid) ? INSTALL_EXTERNAL : 0)
              | (isForwardLocked ? INSTALL_FORWARD_LOCK : 0), null, null, null,
                    instructionSets, null, null, null, 0, null /*certificates*/);
            this.cid = cid;
            setMountPath(PackageHelper.getSdDir(cid));
        }

        void createCopyFile() {
            cid = mInstallerService.allocateExternalStageCidLegacy();
        }

        int copyApk(IMediaContainerService imcs, boolean temp) throws RemoteException {
            if (origin.staged && origin.cid != null) {
                if (DEBUG_INSTALL) Slog.d(TAG, origin.cid + " already staged; skipping copy");
                cid = origin.cid;
                setMountPath(PackageHelper.getSdDir(cid));
                return PackageManager.INSTALL_SUCCEEDED;
            }

            if (temp) {
                createCopyFile();
            } else {
                /*
                 * Pre-emptively destroy the container since it's destroyed if
                 * copying fails due to it existing anyway.
                 */
                PackageHelper.destroySdDir(cid);
            }

            final String newMountPath = imcs.copyPackageToContainer(
                    origin.file.getAbsolutePath(), cid, getEncryptKey(), isExternalAsec(),
                    isFwdLocked(), deriveAbiOverride(abiOverride, null /* settings */));

            if (newMountPath != null) {
                setMountPath(newMountPath);
                return PackageManager.INSTALL_SUCCEEDED;
            } else {
                return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
            }
        }

        @Override
        String getCodePath() {
            return packagePath;
        }

        @Override
        String getResourcePath() {
            return resourcePath;
        }

        int doPreInstall(int status) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                // Destroy container
                PackageHelper.destroySdDir(cid);
            } else {
                boolean mounted = PackageHelper.isContainerMounted(cid);
                if (!mounted) {
                    String newMountPath = PackageHelper.mountSdDir(cid, getEncryptKey(),
                            Process.SYSTEM_UID);
                    if (newMountPath != null) {
                        setMountPath(newMountPath);
                    } else {
                        return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                    }
                }
            }
            return status;
        }

        boolean doRename(int status, PackageParser.Package pkg, String oldCodePath) {
            String newCacheId = getNextCodePath(oldCodePath, pkg.packageName, "/" + RES_FILE_NAME);
            String newMountPath = null;
            if (PackageHelper.isContainerMounted(cid)) {
                // Unmount the container
                if (!PackageHelper.unMountSdDir(cid)) {
                    Slog.i(TAG, "Failed to unmount " + cid + " before renaming");
                    return false;
                }
            }
            if (!PackageHelper.renameSdDir(cid, newCacheId)) {
                Slog.e(TAG, "Failed to rename " + cid + " to " + newCacheId +
                        " which might be stale. Will try to clean up.");
                // Clean up the stale container and proceed to recreate.
                if (!PackageHelper.destroySdDir(newCacheId)) {
                    Slog.e(TAG, "Very strange. Cannot clean up stale container " + newCacheId);
                    return false;
                }
                // Successfully cleaned up stale container. Try to rename again.
                if (!PackageHelper.renameSdDir(cid, newCacheId)) {
                    Slog.e(TAG, "Failed to rename " + cid + " to " + newCacheId
                            + " inspite of cleaning it up.");
                    return false;
                }
            }
            if (!PackageHelper.isContainerMounted(newCacheId)) {
                Slog.w(TAG, "Mounting container " + newCacheId);
                newMountPath = PackageHelper.mountSdDir(newCacheId,
                        getEncryptKey(), Process.SYSTEM_UID);
            } else {
                newMountPath = PackageHelper.getSdDir(newCacheId);
            }
            if (newMountPath == null) {
                Slog.w(TAG, "Failed to get cache path for  " + newCacheId);
                return false;
            }
            Log.i(TAG, "Succesfully renamed " + cid +
                    " to " + newCacheId +
                    " at new path: " + newMountPath);
            cid = newCacheId;

            final File beforeCodeFile = new File(packagePath);
            setMountPath(newMountPath);
            final File afterCodeFile = new File(packagePath);

            // Reflect the rename in scanned details
            pkg.setCodePath(afterCodeFile.getAbsolutePath());
            pkg.setBaseCodePath(FileUtils.rewriteAfterRename(beforeCodeFile,
                    afterCodeFile, pkg.baseCodePath));
            pkg.setSplitCodePaths(FileUtils.rewriteAfterRename(beforeCodeFile,
                    afterCodeFile, pkg.splitCodePaths));

            // Reflect the rename in app info
            pkg.setApplicationVolumeUuid(pkg.volumeUuid);
            pkg.setApplicationInfoCodePath(pkg.codePath);
            pkg.setApplicationInfoBaseCodePath(pkg.baseCodePath);
            pkg.setApplicationInfoSplitCodePaths(pkg.splitCodePaths);
            pkg.setApplicationInfoResourcePath(pkg.codePath);
            pkg.setApplicationInfoBaseResourcePath(pkg.baseCodePath);
            pkg.setApplicationInfoSplitResourcePaths(pkg.splitCodePaths);

            return true;
        }

        private void setMountPath(String mountPath) {
            final File mountFile = new File(mountPath);

            final File monolithicFile = new File(mountFile, RES_FILE_NAME);
            if (monolithicFile.exists()) {
                packagePath = monolithicFile.getAbsolutePath();
                if (isFwdLocked()) {
                    resourcePath = new File(mountFile, PUBLIC_RES_FILE_NAME).getAbsolutePath();
                } else {
                    resourcePath = packagePath;
                }
            } else {
                packagePath = mountFile.getAbsolutePath();
                resourcePath = packagePath;
            }
        }

        int doPostInstall(int status, int uid) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
            } else {
                final int groupOwner;
                final String protectedFile;
                if (isFwdLocked()) {
                    groupOwner = UserHandle.getSharedAppGid(uid);
                    protectedFile = RES_FILE_NAME;
                } else {
                    groupOwner = -1;
                    protectedFile = null;
                }

                if (uid < Process.FIRST_APPLICATION_UID
                        || !PackageHelper.fixSdPermissions(cid, groupOwner, protectedFile)) {
                    Slog.e(TAG, "Failed to finalize " + cid);
                    PackageHelper.destroySdDir(cid);
                    return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                }

                boolean mounted = PackageHelper.isContainerMounted(cid);
                if (!mounted) {
                    PackageHelper.mountSdDir(cid, getEncryptKey(), Process.myUid());
                }
            }
            return status;
        }

        private void cleanUp() {
            if (DEBUG_SD_INSTALL) Slog.i(TAG, "cleanUp");

            // Destroy secure container
            PackageHelper.destroySdDir(cid);
        }

        private List<String> getAllCodePaths() {
            final File codeFile = new File(getCodePath());
            if (codeFile != null && codeFile.exists()) {
                try {
                    final PackageLite pkg = PackageParser.parsePackageLite(codeFile, 0);
                    return pkg.getAllCodePaths();
                } catch (PackageParserException e) {
                    // Ignored; we tried our best
                }
            }
            return Collections.EMPTY_LIST;
        }

        void cleanUpResourcesLI() {
            // Enumerate all code paths before deleting
            cleanUpResourcesLI(getAllCodePaths());
        }

        private void cleanUpResourcesLI(List<String> allCodePaths) {
            cleanUp();
            removeDexFiles(allCodePaths, instructionSets);
        }

        String getPackageName() {
            return getAsecPackageName(cid);
        }

        boolean doPostDeleteLI(boolean delete) {
            if (DEBUG_SD_INSTALL) Slog.i(TAG, "doPostDeleteLI() del=" + delete);
            final List<String> allCodePaths = getAllCodePaths();
            boolean mounted = PackageHelper.isContainerMounted(cid);
            if (mounted) {
                // Unmount first
                if (PackageHelper.unMountSdDir(cid)) {
                    mounted = false;
                }
            }
            if (!mounted && delete) {
                cleanUpResourcesLI(allCodePaths);
            }
            return !mounted;
        }

        @Override
        int doPreCopy() {
            if (isFwdLocked()) {
                if (!PackageHelper.fixSdPermissions(cid, getPackageUid(DEFAULT_CONTAINER_PACKAGE,
                        MATCH_SYSTEM_ONLY, UserHandle.USER_SYSTEM), RES_FILE_NAME)) {
                    return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                }
            }

            return PackageManager.INSTALL_SUCCEEDED;
        }

        @Override
        int doPostCopy(int uid) {
            if (isFwdLocked()) {
                if (uid < Process.FIRST_APPLICATION_UID
                        || !PackageHelper.fixSdPermissions(cid, UserHandle.getSharedAppGid(uid),
                                RES_FILE_NAME)) {
                    Slog.e(TAG, "Failed to finalize " + cid);
                    PackageHelper.destroySdDir(cid);
                    return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                }
            }

            return PackageManager.INSTALL_SUCCEEDED;
        }
    }

    /**
     * Logic to handle movement of existing installed applications.
     */
    class MoveInstallArgs extends InstallArgs {
        private File codeFile;
        private File resourceFile;

        /** New install */
        MoveInstallArgs(InstallParams params) {
            super(params.origin, params.move, params.observer, params.installFlags,
                    params.installerPackageName, params.volumeUuid,
                    params.getUser(), null /* instruction sets */, params.packageAbiOverride,
                    params.grantedRuntimePermissions,
                    params.traceMethod, params.traceCookie, params.certificates);
        }

        int copyApk(IMediaContainerService imcs, boolean temp) {
            if (DEBUG_INSTALL) Slog.d(TAG, "Moving " + move.packageName + " from "
                    + move.fromUuid + " to " + move.toUuid);
            synchronized (mInstaller) {
                try {
                    mInstaller.moveCompleteApp(move.fromUuid, move.toUuid, move.packageName,
                            move.dataAppName, move.appId, move.seinfo, move.targetSdkVersion);
                } catch (InstallerException e) {
                    Slog.w(TAG, "Failed to move app", e);
                    return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
                }
            }

            codeFile = new File(Environment.getDataAppDirectory(move.toUuid), move.dataAppName);
            resourceFile = codeFile;
            if (DEBUG_INSTALL) Slog.d(TAG, "codeFile after move is " + codeFile);

            return PackageManager.INSTALL_SUCCEEDED;
        }

        int doPreInstall(int status) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp(move.toUuid);
            }
            return status;
        }

        boolean doRename(int status, PackageParser.Package pkg, String oldCodePath) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp(move.toUuid);
                return false;
            }

            // Reflect the move in app info
            pkg.setApplicationVolumeUuid(pkg.volumeUuid);
            pkg.setApplicationInfoCodePath(pkg.codePath);
            pkg.setApplicationInfoBaseCodePath(pkg.baseCodePath);
            pkg.setApplicationInfoSplitCodePaths(pkg.splitCodePaths);
            pkg.setApplicationInfoResourcePath(pkg.codePath);
            pkg.setApplicationInfoBaseResourcePath(pkg.baseCodePath);
            pkg.setApplicationInfoSplitResourcePaths(pkg.splitCodePaths);

            return true;
        }

        int doPostInstall(int status, int uid) {
            if (status == PackageManager.INSTALL_SUCCEEDED) {
                cleanUp(move.fromUuid);
            } else {
                cleanUp(move.toUuid);
            }
            return status;
        }

        @Override
        String getCodePath() {
            return (codeFile != null) ? codeFile.getAbsolutePath() : null;
        }

        @Override
        String getResourcePath() {
            return (resourceFile != null) ? resourceFile.getAbsolutePath() : null;
        }

        private boolean cleanUp(String volumeUuid) {
            final File codeFile = new File(Environment.getDataAppDirectory(volumeUuid),
                    move.dataAppName);
            Slog.d(TAG, "Cleaning up " + move.packageName + " on " + volumeUuid);
            final int[] userIds = sUserManager.getUserIds();
            synchronized (mInstallLock) {
                // Clean up both app data and code
                // All package moves are frozen until finished
                for (int userId : userIds) {
                    try {
                        mInstaller.destroyAppData(volumeUuid, move.packageName, userId,
                                StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE, 0);
                    } catch (InstallerException e) {
                        Slog.w(TAG, String.valueOf(e));
                    }
                }
                removeCodePathLI(codeFile);
            }
            return true;
        }

        void cleanUpResourcesLI() {
            throw new UnsupportedOperationException();
        }

        boolean doPostDeleteLI(boolean delete) {
            throw new UnsupportedOperationException();
        }
    }

    static String getAsecPackageName(String packageCid) {
        int idx = packageCid.lastIndexOf("-");
        if (idx == -1) {
            return packageCid;
        }
        return packageCid.substring(0, idx);
    }

    // Utility method used to create code paths based on package name and available index.
    private static String getNextCodePath(String oldCodePath, String prefix, String suffix) {
        String idxStr = "";
        int idx = 1;
        // Fall back to default value of idx=1 if prefix is not
        // part of oldCodePath
        if (oldCodePath != null) {
            String subStr = oldCodePath;
            // Drop the suffix right away
            if (suffix != null && subStr.endsWith(suffix)) {
                subStr = subStr.substring(0, subStr.length() - suffix.length());
            }
            // If oldCodePath already contains prefix find out the
            // ending index to either increment or decrement.
            int sidx = subStr.lastIndexOf(prefix);
            if (sidx != -1) {
                subStr = subStr.substring(sidx + prefix.length());
                if (subStr != null) {
                    if (subStr.startsWith(INSTALL_PACKAGE_SUFFIX)) {
                        subStr = subStr.substring(INSTALL_PACKAGE_SUFFIX.length());
                    }
                    try {
                        idx = Integer.parseInt(subStr);
                        if (idx <= 1) {
                            idx++;
                        } else {
                            idx--;
                        }
                    } catch(NumberFormatException e) {
                    }
                }
            }
        }
        idxStr = INSTALL_PACKAGE_SUFFIX + Integer.toString(idx);
        return prefix + idxStr;
    }

    private File getNextCodePath(File targetDir, String packageName) {
        int suffix = 1;
        File result;
        do {
            result = new File(targetDir, packageName + "-" + suffix);
            suffix++;
        } while (result.exists());
        return result;
    }

    // Utility method that returns the relative package path with respect
    // to the installation directory. Like say for /data/data/com.test-1.apk
    // string com.test-1 is returned.
    static String deriveCodePathName(String codePath) {
        if (codePath == null) {
            return null;
        }
        final File codeFile = new File(codePath);
        final String name = codeFile.getName();
        if (codeFile.isDirectory()) {
            return name;
        } else if (name.endsWith(".apk") || name.endsWith(".tmp")) {
            final int lastDot = name.lastIndexOf('.');
            return name.substring(0, lastDot);
        } else {
            Slog.w(TAG, "Odd, " + codePath + " doesn't look like an APK");
            return null;
        }
    }

    static class PackageInstalledInfo {
        String name;
        int uid;
        // The set of users that originally had this package installed.
        int[] origUsers;
        // The set of users that now have this package installed.
        int[] newUsers;
        PackageParser.Package pkg;
        int returnCode;
        String returnMsg;
        PackageRemovedInfo removedInfo;
        ArrayMap<String, PackageInstalledInfo> addedChildPackages;

        public void setError(int code, String msg) {
            setReturnCode(code);
            setReturnMessage(msg);
            Slog.w(TAG, msg);
        }

        public void setError(String msg, PackageParserException e) {
            setReturnCode(e.error);
            setReturnMessage(ExceptionUtils.getCompleteMessage(msg, e));
            Slog.w(TAG, msg, e);
        }

        public void setError(String msg, PackageManagerException e) {
            returnCode = e.error;
            setReturnMessage(ExceptionUtils.getCompleteMessage(msg, e));
            Slog.w(TAG, msg, e);
        }

        public void setReturnCode(int returnCode) {
            this.returnCode = returnCode;
            final int childCount = (addedChildPackages != null) ? addedChildPackages.size() : 0;
            for (int i = 0; i < childCount; i++) {
                addedChildPackages.valueAt(i).returnCode = returnCode;
            }
        }

        private void setReturnMessage(String returnMsg) {
            this.returnMsg = returnMsg;
            final int childCount = (addedChildPackages != null) ? addedChildPackages.size() : 0;
            for (int i = 0; i < childCount; i++) {
                addedChildPackages.valueAt(i).returnMsg = returnMsg;
            }
        }

        // In some error cases we want to convey more info back to the observer
        String origPackage;
        String origPermission;
    }

    /*
     * Install a non-existing package.
     */
    private void installNewPackageLIF(PackageParser.Package pkg, final int policyFlags,
            int scanFlags, UserHandle user, String installerPackageName, String volumeUuid,
            PackageInstalledInfo res) {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "installNewPackage");

        // Remember this for later, in case we need to rollback this install
        String pkgName = pkg.packageName;

        if (DEBUG_INSTALL) Slog.d(TAG, "installNewPackageLI: " + pkg);

        synchronized(mPackages) {
            if (mSettings.mRenamedPackages.containsKey(pkgName)) {
                // A package with the same name is already installed, though
                // it has been renamed to an older name.  The package we
                // are trying to install should be installed as an update to
                // the existing one, but that has not been requested, so bail.
                res.setError(INSTALL_FAILED_ALREADY_EXISTS, "Attempt to re-install " + pkgName
                        + " without first uninstalling package running as "
                        + mSettings.mRenamedPackages.get(pkgName));
                return;
            }
            if (mPackages.containsKey(pkgName)) {
                // Don't allow installation over an existing package with the same name.
                res.setError(INSTALL_FAILED_ALREADY_EXISTS, "Attempt to re-install " + pkgName
                        + " without first uninstalling.");
                return;
            }
        }

        try {
            PackageParser.Package newPackage = scanPackageTracedLI(pkg, policyFlags, scanFlags,
                    System.currentTimeMillis(), user);

            updateSettingsLI(newPackage, installerPackageName, null, res, user);

            if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
                prepareAppDataAfterInstallLIF(newPackage);

            } else {
                // Remove package from internal structures, but keep around any
                // data that might have already existed
                deletePackageLIF(pkgName, UserHandle.ALL, false, null,
                        PackageManager.DELETE_KEEP_DATA, res.removedInfo, true, null);
            }
        } catch (PackageManagerException e) {
            res.setError("Package couldn't be installed in " + pkg.codePath, e);
        }

        Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
    }

    private boolean shouldCheckUpgradeKeySetLP(PackageSetting oldPs, int scanFlags) {
        // Can't rotate keys during boot or if sharedUser.
        if (oldPs == null || (scanFlags&SCAN_INITIAL) != 0 || oldPs.sharedUser != null
                || !oldPs.keySetData.isUsingUpgradeKeySets()) {
            return false;
        }
        // app is using upgradeKeySets; make sure all are valid
        KeySetManagerService ksms = mSettings.mKeySetManagerService;
        long[] upgradeKeySets = oldPs.keySetData.getUpgradeKeySets();
        for (int i = 0; i < upgradeKeySets.length; i++) {
            if (!ksms.isIdValidKeySetId(upgradeKeySets[i])) {
                Slog.wtf(TAG, "Package "
                         + (oldPs.name != null ? oldPs.name : "<null>")
                         + " contains upgrade-key-set reference to unknown key-set: "
                         + upgradeKeySets[i]
                         + " reverting to signatures check.");
                return false;
            }
        }
        return true;
    }

    private boolean checkUpgradeKeySetLP(PackageSetting oldPS, PackageParser.Package newPkg) {
        // Upgrade keysets are being used.  Determine if new package has a superset of the
        // required keys.
        long[] upgradeKeySets = oldPS.keySetData.getUpgradeKeySets();
        KeySetManagerService ksms = mSettings.mKeySetManagerService;
        for (int i = 0; i < upgradeKeySets.length; i++) {
            Set<PublicKey> upgradeSet = ksms.getPublicKeysFromKeySetLPr(upgradeKeySets[i]);
            if (upgradeSet != null && newPkg.mSigningKeys.containsAll(upgradeSet)) {
                return true;
            }
        }
        return false;
    }

    private static void updateDigest(MessageDigest digest, File file) throws IOException {
        try (DigestInputStream digestStream =
                new DigestInputStream(new FileInputStream(file), digest)) {
            while (digestStream.read() != -1) {} // nothing to do; just plow through the file
        }
    }

    private void replacePackageLIF(PackageParser.Package pkg, final int policyFlags, int scanFlags,
            UserHandle user, String installerPackageName, PackageInstalledInfo res) {
        final boolean isEphemeral = (policyFlags & PackageParser.PARSE_IS_EPHEMERAL) != 0;

        final PackageParser.Package oldPackage;
        final String pkgName = pkg.packageName;
        final int[] allUsers;
        final int[] installedUsers;

        synchronized(mPackages) {
            oldPackage = mPackages.get(pkgName);
            if (DEBUG_INSTALL) Slog.d(TAG, "replacePackageLI: new=" + pkg + ", old=" + oldPackage);

            // don't allow upgrade to target a release SDK from a pre-release SDK
            final boolean oldTargetsPreRelease = oldPackage.applicationInfo.targetSdkVersion
                    == android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;
            final boolean newTargetsPreRelease = pkg.applicationInfo.targetSdkVersion
                    == android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;
            if (oldTargetsPreRelease
                    && !newTargetsPreRelease
                    && ((policyFlags & PackageParser.PARSE_FORCE_SDK) == 0)) {
                Slog.w(TAG, "Can't install package targeting released sdk");
                res.setReturnCode(PackageManager.INSTALL_FAILED_UPDATE_INCOMPATIBLE);
                return;
            }

            // don't allow an upgrade from full to ephemeral
            final boolean oldIsEphemeral = oldPackage.applicationInfo.isEphemeralApp();
            if (isEphemeral && !oldIsEphemeral) {
                // can't downgrade from full to ephemeral
                Slog.w(TAG, "Can't replace app with ephemeral: " + pkgName);
                res.setReturnCode(PackageManager.INSTALL_FAILED_EPHEMERAL_INVALID);
                return;
            }

            // verify signatures are valid
            final PackageSetting ps = mSettings.mPackages.get(pkgName);
            if (shouldCheckUpgradeKeySetLP(ps, scanFlags)) {
                if (!checkUpgradeKeySetLP(ps, pkg)) {
                    res.setError(INSTALL_FAILED_UPDATE_INCOMPATIBLE,
                            "New package not signed by keys specified by upgrade-keysets: "
                                    + pkgName);
                    return;
                }
            } else {
                // default to original signature matching
                if (compareSignatures(oldPackage.mSignatures, pkg.mSignatures)
                        != PackageManager.SIGNATURE_MATCH) {
                    res.setError(INSTALL_FAILED_UPDATE_INCOMPATIBLE,
                            "New package has a different signature: " + pkgName);
                    return;
                }
            }

            // don't allow a system upgrade unless the upgrade hash matches
            if (oldPackage.restrictUpdateHash != null && oldPackage.isSystemApp()) {
                byte[] digestBytes = null;
                try {
                    final MessageDigest digest = MessageDigest.getInstance("SHA-512");
                    updateDigest(digest, new File(pkg.baseCodePath));
                    if (!ArrayUtils.isEmpty(pkg.splitCodePaths)) {
                        for (String path : pkg.splitCodePaths) {
                            updateDigest(digest, new File(path));
                        }
                    }
                    digestBytes = digest.digest();
                } catch (NoSuchAlgorithmException | IOException e) {
                    res.setError(INSTALL_FAILED_INVALID_APK,
                            "Could not compute hash: " + pkgName);
                    return;
                }
                if (!Arrays.equals(oldPackage.restrictUpdateHash, digestBytes)) {
                    res.setError(INSTALL_FAILED_INVALID_APK,
                            "New package fails restrict-update check: " + pkgName);
                    return;
                }
                // retain upgrade restriction
                pkg.restrictUpdateHash = oldPackage.restrictUpdateHash;
            }

            // Check for shared user id changes
            String invalidPackageName =
                    getParentOrChildPackageChangedSharedUser(oldPackage, pkg);
            if (invalidPackageName != null) {
                res.setError(INSTALL_FAILED_SHARED_USER_INCOMPATIBLE,
                        "Package " + invalidPackageName + " tried to change user "
                                + oldPackage.mSharedUserId);
                return;
            }

            // In case of rollback, remember per-user/profile install state
            allUsers = sUserManager.getUserIds();
            installedUsers = ps.queryInstalledUsers(allUsers, true);
        }

        // Update what is removed
        res.removedInfo = new PackageRemovedInfo();
        res.removedInfo.uid = oldPackage.applicationInfo.uid;
        res.removedInfo.removedPackage = oldPackage.packageName;
        res.removedInfo.isUpdate = true;
        res.removedInfo.origUsers = installedUsers;
        final int childCount = (oldPackage.childPackages != null)
                ? oldPackage.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            boolean childPackageUpdated = false;
            PackageParser.Package childPkg = oldPackage.childPackages.get(i);
            if (res.addedChildPackages != null) {
                PackageInstalledInfo childRes = res.addedChildPackages.get(childPkg.packageName);
                if (childRes != null) {
                    childRes.removedInfo.uid = childPkg.applicationInfo.uid;
                    childRes.removedInfo.removedPackage = childPkg.packageName;
                    childRes.removedInfo.isUpdate = true;
                    childPackageUpdated = true;
                }
            }
            if (!childPackageUpdated) {
                PackageRemovedInfo childRemovedRes = new PackageRemovedInfo();
                childRemovedRes.removedPackage = childPkg.packageName;
                childRemovedRes.isUpdate = false;
                childRemovedRes.dataRemoved = true;
                synchronized (mPackages) {
                    PackageSetting childPs = mSettings.peekPackageLPr(childPkg.packageName);
                    if (childPs != null) {
                        childRemovedRes.origUsers = childPs.queryInstalledUsers(allUsers, true);
                    }
                }
                if (res.removedInfo.removedChildPackages == null) {
                    res.removedInfo.removedChildPackages = new ArrayMap<>();
                }
                res.removedInfo.removedChildPackages.put(childPkg.packageName, childRemovedRes);
            }
        }

        boolean sysPkg = (isSystemApp(oldPackage));
        if (sysPkg) {
            // Set the system/privileged flags as needed
            final boolean privileged =
                    (oldPackage.applicationInfo.privateFlags
                            & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0;
            final int systemPolicyFlags = policyFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | (privileged ? PackageParser.PARSE_IS_PRIVILEGED : 0);

            replaceSystemPackageLIF(oldPackage, pkg, systemPolicyFlags, scanFlags,
                    user, allUsers, installerPackageName, res);
        } else {
            replaceNonSystemPackageLIF(oldPackage, pkg, policyFlags, scanFlags,
                    user, allUsers, installerPackageName, res);
        }
    }

    public List<String> getPreviousCodePaths(String packageName) {
        final PackageSetting ps = mSettings.mPackages.get(packageName);
        final List<String> result = new ArrayList<String>();
        if (ps != null && ps.oldCodePaths != null) {
            result.addAll(ps.oldCodePaths);
        }
        return result;
    }

    private void replaceNonSystemPackageLIF(PackageParser.Package deletedPackage,
            PackageParser.Package pkg, final int policyFlags, int scanFlags, UserHandle user,
            int[] allUsers, String installerPackageName, PackageInstalledInfo res) {
        if (DEBUG_INSTALL) Slog.d(TAG, "replaceNonSystemPackageLI: new=" + pkg + ", old="
                + deletedPackage);

        String pkgName = deletedPackage.packageName;
        boolean deletedPkg = true;
        boolean addedPkg = false;
        boolean updatedSettings = false;
        final boolean killApp = (scanFlags & SCAN_DONT_KILL_APP) == 0;
        final int deleteFlags = PackageManager.DELETE_KEEP_DATA
                | (killApp ? 0 : PackageManager.DELETE_DONT_KILL_APP);

        final long origUpdateTime = (pkg.mExtras != null)
                ? ((PackageSetting)pkg.mExtras).lastUpdateTime : 0;

        // First delete the existing package while retaining the data directory
        if (!deletePackageLIF(pkgName, null, true, allUsers, deleteFlags,
                res.removedInfo, true, pkg)) {
            // If the existing package wasn't successfully deleted
            res.setError(INSTALL_FAILED_REPLACE_COULDNT_DELETE, "replaceNonSystemPackageLI");
            deletedPkg = false;
        } else {
            // Successfully deleted the old package; proceed with replace.

            // If deleted package lived in a container, give users a chance to
            // relinquish resources before killing.
            if (deletedPackage.isForwardLocked() || isExternal(deletedPackage)) {
                if (DEBUG_INSTALL) {
                    Slog.i(TAG, "upgrading pkg " + deletedPackage + " is ASEC-hosted -> UNAVAILABLE");
                }
                final int[] uidArray = new int[] { deletedPackage.applicationInfo.uid };
                final ArrayList<String> pkgList = new ArrayList<String>(1);
                pkgList.add(deletedPackage.applicationInfo.packageName);
                sendResourcesChangedBroadcast(false, true, pkgList, uidArray, null);
            }

            clearAppDataLIF(pkg, UserHandle.USER_ALL, StorageManager.FLAG_STORAGE_DE
                    | StorageManager.FLAG_STORAGE_CE | Installer.FLAG_CLEAR_CODE_CACHE_ONLY);
            clearAppProfilesLIF(deletedPackage, UserHandle.USER_ALL);

            try {
                final PackageParser.Package newPackage = scanPackageTracedLI(pkg, policyFlags,
                        scanFlags | SCAN_UPDATE_TIME, System.currentTimeMillis(), user);
                updateSettingsLI(newPackage, installerPackageName, allUsers, res, user);

                // Update the in-memory copy of the previous code paths.
                PackageSetting ps = mSettings.mPackages.get(pkgName);
                if (!killApp) {
                    if (ps.oldCodePaths == null) {
                        ps.oldCodePaths = new ArraySet<>();
                    }
                    Collections.addAll(ps.oldCodePaths, deletedPackage.baseCodePath);
                    if (deletedPackage.splitCodePaths != null) {
                        Collections.addAll(ps.oldCodePaths, deletedPackage.splitCodePaths);
                    }
                } else {
                    ps.oldCodePaths = null;
                }
                if (ps.childPackageNames != null) {
                    for (int i = ps.childPackageNames.size() - 1; i >= 0; --i) {
                        final String childPkgName = ps.childPackageNames.get(i);
                        final PackageSetting childPs = mSettings.mPackages.get(childPkgName);
                        childPs.oldCodePaths = ps.oldCodePaths;
                    }
                }
                prepareAppDataAfterInstallLIF(newPackage);
                addedPkg = true;
            } catch (PackageManagerException e) {
                res.setError("Package couldn't be installed in " + pkg.codePath, e);
            }
        }

        if (res.returnCode != PackageManager.INSTALL_SUCCEEDED) {
            if (DEBUG_INSTALL) Slog.d(TAG, "Install failed, rolling pack: " + pkgName);

            // Revert all internal state mutations and added folders for the failed install
            if (addedPkg) {
                deletePackageLIF(pkgName, null, true, allUsers, deleteFlags,
                        res.removedInfo, true, null);
            }

            // Restore the old package
            if (deletedPkg) {
                if (DEBUG_INSTALL) Slog.d(TAG, "Install failed, reinstalling: " + deletedPackage);
                File restoreFile = new File(deletedPackage.codePath);
                // Parse old package
                boolean oldExternal = isExternal(deletedPackage);
                int oldParseFlags  = mDefParseFlags | PackageParser.PARSE_CHATTY |
                        (deletedPackage.isForwardLocked() ? PackageParser.PARSE_FORWARD_LOCK : 0) |
                        (oldExternal ? PackageParser.PARSE_EXTERNAL_STORAGE : 0);
                int oldScanFlags = SCAN_UPDATE_SIGNATURE | SCAN_UPDATE_TIME;
                try {
                    scanPackageTracedLI(restoreFile, oldParseFlags, oldScanFlags, origUpdateTime,
                            null);
                } catch (PackageManagerException e) {
                    Slog.e(TAG, "Failed to restore package : " + pkgName + " after failed upgrade: "
                            + e.getMessage());
                    return;
                }

                synchronized (mPackages) {
                    // Ensure the installer package name up to date
                    setInstallerPackageNameLPw(deletedPackage, installerPackageName);

                    // Update permissions for restored package
                    updatePermissionsLPw(deletedPackage, UPDATE_PERMISSIONS_ALL);

                    mSettings.writeLPr();
                }

                Slog.i(TAG, "Successfully restored package : " + pkgName + " after failed upgrade");
            }
        } else {
            synchronized (mPackages) {
                PackageSetting ps = mSettings.peekPackageLPr(pkg.packageName);
                if (ps != null) {
                    res.removedInfo.removedForAllUsers = mPackages.get(ps.name) == null;
                    if (res.removedInfo.removedChildPackages != null) {
                        final int childCount = res.removedInfo.removedChildPackages.size();
                        // Iterate in reverse as we may modify the collection
                        for (int i = childCount - 1; i >= 0; i--) {
                            String childPackageName = res.removedInfo.removedChildPackages.keyAt(i);
                            if (res.addedChildPackages.containsKey(childPackageName)) {
                                res.removedInfo.removedChildPackages.removeAt(i);
                            } else {
                                PackageRemovedInfo childInfo = res.removedInfo
                                        .removedChildPackages.valueAt(i);
                                childInfo.removedForAllUsers = mPackages.get(
                                        childInfo.removedPackage) == null;
                            }
                        }
                    }
                }
            }
        }
    }

    private void replaceSystemPackageLIF(PackageParser.Package deletedPackage,
            PackageParser.Package pkg, final int policyFlags, int scanFlags, UserHandle user,
            int[] allUsers, String installerPackageName, PackageInstalledInfo res) {
        if (DEBUG_INSTALL) Slog.d(TAG, "replaceSystemPackageLI: new=" + pkg
                + ", old=" + deletedPackage);

        final boolean disabledSystem;

        // Remove existing system package
        removePackageLI(deletedPackage, true);

        synchronized (mPackages) {
            disabledSystem = disableSystemPackageLPw(deletedPackage, pkg);
        }
        if (!disabledSystem) {
            // We didn't need to disable the .apk as a current system package,
            // which means we are replacing another update that is already
            // installed.  We need to make sure to delete the older one's .apk.
            res.removedInfo.args = createInstallArgsForExisting(0,
                    deletedPackage.applicationInfo.getCodePath(),
                    deletedPackage.applicationInfo.getResourcePath(),
                    getAppDexInstructionSets(deletedPackage.applicationInfo));
        } else {
            res.removedInfo.args = null;
        }

        // Successfully disabled the old package. Now proceed with re-installation
        clearAppDataLIF(pkg, UserHandle.USER_ALL, StorageManager.FLAG_STORAGE_DE
                | StorageManager.FLAG_STORAGE_CE | Installer.FLAG_CLEAR_CODE_CACHE_ONLY);
        clearAppProfilesLIF(deletedPackage, UserHandle.USER_ALL);

        res.setReturnCode(PackageManager.INSTALL_SUCCEEDED);
        pkg.setApplicationInfoFlags(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);

        PackageParser.Package newPackage = null;
        try {
            // Add the package to the internal data structures
            newPackage = scanPackageTracedLI(pkg, policyFlags, scanFlags, 0, user);

            // Set the update and install times
            PackageSetting deletedPkgSetting = (PackageSetting) deletedPackage.mExtras;
            setInstallAndUpdateTime(newPackage, deletedPkgSetting.firstInstallTime,
                    System.currentTimeMillis());

            // Update the package dynamic state if succeeded
            if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
                // Now that the install succeeded make sure we remove data
                // directories for any child package the update removed.
                final int deletedChildCount = (deletedPackage.childPackages != null)
                        ? deletedPackage.childPackages.size() : 0;
                final int newChildCount = (newPackage.childPackages != null)
                        ? newPackage.childPackages.size() : 0;
                for (int i = 0; i < deletedChildCount; i++) {
                    PackageParser.Package deletedChildPkg = deletedPackage.childPackages.get(i);
                    boolean childPackageDeleted = true;
                    for (int j = 0; j < newChildCount; j++) {
                        PackageParser.Package newChildPkg = newPackage.childPackages.get(j);
                        if (deletedChildPkg.packageName.equals(newChildPkg.packageName)) {
                            childPackageDeleted = false;
                            break;
                        }
                    }
                    if (childPackageDeleted) {
                        PackageSetting ps = mSettings.getDisabledSystemPkgLPr(
                                deletedChildPkg.packageName);
                        if (ps != null && res.removedInfo.removedChildPackages != null) {
                            PackageRemovedInfo removedChildRes = res.removedInfo
                                    .removedChildPackages.get(deletedChildPkg.packageName);
                            removePackageDataLIF(ps, allUsers, removedChildRes, 0, false);
                            removedChildRes.removedForAllUsers = mPackages.get(ps.name) == null;
                        }
                    }
                }

                updateSettingsLI(newPackage, installerPackageName, allUsers, res, user);
                prepareAppDataAfterInstallLIF(newPackage);
            }
        } catch (PackageManagerException e) {
            res.setReturnCode(INSTALL_FAILED_INTERNAL_ERROR);
            res.setError("Package couldn't be installed in " + pkg.codePath, e);
        }

        if (res.returnCode != PackageManager.INSTALL_SUCCEEDED) {
            // Re installation failed. Restore old information
            // Remove new pkg information
            if (newPackage != null) {
                removeInstalledPackageLI(newPackage, true);
            }
            // Add back the old system package
            try {
                scanPackageTracedLI(deletedPackage, policyFlags, SCAN_UPDATE_SIGNATURE, 0, user);
            } catch (PackageManagerException e) {
                Slog.e(TAG, "Failed to restore original package: " + e.getMessage());
            }

            synchronized (mPackages) {
                if (disabledSystem) {
                    enableSystemPackageLPw(deletedPackage);
                }

                // Ensure the installer package name up to date
                setInstallerPackageNameLPw(deletedPackage, installerPackageName);

                // Update permissions for restored package
                updatePermissionsLPw(deletedPackage, UPDATE_PERMISSIONS_ALL);

                mSettings.writeLPr();
            }

            Slog.i(TAG, "Successfully restored package : " + deletedPackage.packageName
                    + " after failed upgrade");
        }
    }

    /**
     * Checks whether the parent or any of the child packages have a change shared
     * user. For a package to be a valid update the shred users of the parent and
     * the children should match. We may later support changing child shared users.
     * @param oldPkg The updated package.
     * @param newPkg The update package.
     * @return The shared user that change between the versions.
     */
    private String getParentOrChildPackageChangedSharedUser(PackageParser.Package oldPkg,
            PackageParser.Package newPkg) {
        // Check parent shared user
        if (!Objects.equals(oldPkg.mSharedUserId, newPkg.mSharedUserId)) {
            return newPkg.packageName;
        }
        // Check child shared users
        final int oldChildCount = (oldPkg.childPackages != null) ? oldPkg.childPackages.size() : 0;
        final int newChildCount = (newPkg.childPackages != null) ? newPkg.childPackages.size() : 0;
        for (int i = 0; i < newChildCount; i++) {
            PackageParser.Package newChildPkg = newPkg.childPackages.get(i);
            // If this child was present, did it have the same shared user?
            for (int j = 0; j < oldChildCount; j++) {
                PackageParser.Package oldChildPkg = oldPkg.childPackages.get(j);
                if (newChildPkg.packageName.equals(oldChildPkg.packageName)
                        && !Objects.equals(newChildPkg.mSharedUserId, oldChildPkg.mSharedUserId)) {
                    return newChildPkg.packageName;
                }
            }
        }
        return null;
    }

    private void removeNativeBinariesLI(PackageSetting ps) {
        // Remove the lib path for the parent package
        if (ps != null) {
            NativeLibraryHelper.removeNativeBinariesLI(ps.legacyNativeLibraryPathString);
            // Remove the lib path for the child packages
            final int childCount = (ps.childPackageNames != null) ? ps.childPackageNames.size() : 0;
            for (int i = 0; i < childCount; i++) {
                PackageSetting childPs = null;
                synchronized (mPackages) {
                    childPs = mSettings.peekPackageLPr(ps.childPackageNames.get(i));
                }
                if (childPs != null) {
                    NativeLibraryHelper.removeNativeBinariesLI(childPs
                            .legacyNativeLibraryPathString);
                }
            }
        }
    }

    private void enableSystemPackageLPw(PackageParser.Package pkg) {
        // Enable the parent package
        mSettings.enableSystemPackageLPw(pkg.packageName);
        // Enable the child packages
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = pkg.childPackages.get(i);
            mSettings.enableSystemPackageLPw(childPkg.packageName);
        }
    }

    private boolean disableSystemPackageLPw(PackageParser.Package oldPkg,
            PackageParser.Package newPkg) {
        // Disable the parent package (parent always replaced)
        boolean disabled = mSettings.disableSystemPackageLPw(oldPkg.packageName, true);
        // Disable the child packages
        final int childCount = (oldPkg.childPackages != null) ? oldPkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = oldPkg.childPackages.get(i);
            final boolean replace = newPkg.hasChildPackage(childPkg.packageName);
            disabled |= mSettings.disableSystemPackageLPw(childPkg.packageName, replace);
        }
        return disabled;
    }

    private void setInstallerPackageNameLPw(PackageParser.Package pkg,
            String installerPackageName) {
        // Enable the parent package
        mSettings.setInstallerPackageName(pkg.packageName, installerPackageName);
        // Enable the child packages
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = pkg.childPackages.get(i);
            mSettings.setInstallerPackageName(childPkg.packageName, installerPackageName);
        }
    }

    private int[] revokeUnusedSharedUserPermissionsLPw(SharedUserSetting su, int[] allUserIds) {
        // Collect all used permissions in the UID
        ArraySet<String> usedPermissions = new ArraySet<>();
        final int packageCount = su.packages.size();
        for (int i = 0; i < packageCount; i++) {
            PackageSetting ps = su.packages.valueAt(i);
            if (ps.pkg == null) {
                continue;
            }
            final int requestedPermCount = ps.pkg.requestedPermissions.size();
            for (int j = 0; j < requestedPermCount; j++) {
                String permission = ps.pkg.requestedPermissions.get(j);
                BasePermission bp = mSettings.mPermissions.get(permission);
                if (bp != null) {
                    usedPermissions.add(permission);
                }
            }
        }

        PermissionsState permissionsState = su.getPermissionsState();
        // Prune install permissions
        List<PermissionState> installPermStates = permissionsState.getInstallPermissionStates();
        final int installPermCount = installPermStates.size();
        for (int i = installPermCount - 1; i >= 0;  i--) {
            PermissionState permissionState = installPermStates.get(i);
            if (!usedPermissions.contains(permissionState.getName())) {
                BasePermission bp = mSettings.mPermissions.get(permissionState.getName());
                if (bp != null) {
                    permissionsState.revokeInstallPermission(bp);
                    permissionsState.updatePermissionFlags(bp, UserHandle.USER_ALL,
                            PackageManager.MASK_PERMISSION_FLAGS, 0);
                }
            }
        }

        int[] runtimePermissionChangedUserIds = EmptyArray.INT;

        // Prune runtime permissions
        for (int userId : allUserIds) {
            List<PermissionState> runtimePermStates = permissionsState
                    .getRuntimePermissionStates(userId);
            final int runtimePermCount = runtimePermStates.size();
            for (int i = runtimePermCount - 1; i >= 0; i--) {
                PermissionState permissionState = runtimePermStates.get(i);
                if (!usedPermissions.contains(permissionState.getName())) {
                    BasePermission bp = mSettings.mPermissions.get(permissionState.getName());
                    if (bp != null) {
                        permissionsState.revokeRuntimePermission(bp, userId);
                        permissionsState.updatePermissionFlags(bp, userId,
                                PackageManager.MASK_PERMISSION_FLAGS, 0);
                        runtimePermissionChangedUserIds = ArrayUtils.appendInt(
                                runtimePermissionChangedUserIds, userId);
                    }
                }
            }
        }

        return runtimePermissionChangedUserIds;
    }

    private void updateSettingsLI(PackageParser.Package newPackage, String installerPackageName,
            int[] allUsers, PackageInstalledInfo res, UserHandle user) {
        // Update the parent package setting
        updateSettingsInternalLI(newPackage, installerPackageName, allUsers, res.origUsers,
                res, user);
        // Update the child packages setting
        final int childCount = (newPackage.childPackages != null)
                ? newPackage.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPackage = newPackage.childPackages.get(i);
            PackageInstalledInfo childRes = res.addedChildPackages.get(childPackage.packageName);
            updateSettingsInternalLI(childPackage, installerPackageName, allUsers,
                    childRes.origUsers, childRes, user);
        }
    }

    private void updateSettingsInternalLI(PackageParser.Package newPackage,
            String installerPackageName, int[] allUsers, int[] installedForUsers,
            PackageInstalledInfo res, UserHandle user) {
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "updateSettings");

        String pkgName = newPackage.packageName;
        synchronized (mPackages) {
            //write settings. the installStatus will be incomplete at this stage.
            //note that the new package setting would have already been
            //added to mPackages. It hasn't been persisted yet.
            mSettings.setInstallStatus(pkgName, PackageSettingBase.PKG_INSTALL_INCOMPLETE);
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "writeSettings");
            mSettings.writeLPr();
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }

        if (DEBUG_INSTALL) Slog.d(TAG, "New package installed in " + newPackage.codePath);
        synchronized (mPackages) {
            updatePermissionsLPw(newPackage.packageName, newPackage,
                    UPDATE_PERMISSIONS_REPLACE_PKG | (newPackage.permissions.size() > 0
                            ? UPDATE_PERMISSIONS_ALL : 0));
            // For system-bundled packages, we assume that installing an upgraded version
            // of the package implies that the user actually wants to run that new code,
            // so we enable the package.
            PackageSetting ps = mSettings.mPackages.get(pkgName);
            final int userId = user.getIdentifier();
            if (ps != null) {
                if (isSystemApp(newPackage)) {
                    if (DEBUG_INSTALL) {
                        Slog.d(TAG, "Implicitly enabling system package on upgrade: " + pkgName);
                    }
                    // Enable system package for requested users
                    if (res.origUsers != null) {
                        for (int origUserId : res.origUsers) {
                            if (userId == UserHandle.USER_ALL || userId == origUserId) {
                                ps.setEnabled(COMPONENT_ENABLED_STATE_DEFAULT,
                                        origUserId, installerPackageName);
                            }
                        }
                    }
                    // Also convey the prior install/uninstall state
                    if (allUsers != null && installedForUsers != null) {
                        for (int currentUserId : allUsers) {
                            final boolean installed = ArrayUtils.contains(
                                    installedForUsers, currentUserId);
                            if (DEBUG_INSTALL) {
                                Slog.d(TAG, "    user " + currentUserId + " => " + installed);
                            }
                            ps.setInstalled(installed, currentUserId);
                        }
                        // these install state changes will be persisted in the
                        // upcoming call to mSettings.writeLPr().
                    }
                }
                // It's implied that when a user requests installation, they want the app to be
                // installed and enabled.
                if (userId != UserHandle.USER_ALL) {
                    ps.setInstalled(true, userId);
                    ps.setEnabled(COMPONENT_ENABLED_STATE_DEFAULT, userId, installerPackageName);
                }
            }
            res.name = pkgName;
            res.uid = newPackage.applicationInfo.uid;
            res.pkg = newPackage;
            mSettings.setInstallStatus(pkgName, PackageSettingBase.PKG_INSTALL_COMPLETE);
            mSettings.setInstallerPackageName(pkgName, installerPackageName);
            res.setReturnCode(PackageManager.INSTALL_SUCCEEDED);
            //to update install status
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "writeSettings");
            mSettings.writeLPr();
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }

        Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
    }

    private void installPackageTracedLI(InstallArgs args, PackageInstalledInfo res) {
        try {
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "installPackage");
            installPackageLI(args, res);
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }
    }

    private void installPackageLI(InstallArgs args, PackageInstalledInfo res) {
        final int installFlags = args.installFlags;
        final String installerPackageName = args.installerPackageName;
        final String volumeUuid = args.volumeUuid;
        final File tmpPackageFile = new File(args.getCodePath());
        final boolean forwardLocked = ((installFlags & PackageManager.INSTALL_FORWARD_LOCK) != 0);
        final boolean onExternal = (((installFlags & PackageManager.INSTALL_EXTERNAL) != 0)
                || (args.volumeUuid != null));
        final boolean ephemeral = ((installFlags & PackageManager.INSTALL_EPHEMERAL) != 0);
        final boolean forceSdk = ((installFlags & PackageManager.INSTALL_FORCE_SDK) != 0);
        boolean replace = false;
        int scanFlags = SCAN_NEW_INSTALL | SCAN_UPDATE_SIGNATURE;
        if (args.move != null) {
            // moving a complete application; perform an initial scan on the new install location
            scanFlags |= SCAN_INITIAL;
        }
        if ((installFlags & PackageManager.INSTALL_DONT_KILL_APP) != 0) {
            scanFlags |= SCAN_DONT_KILL_APP;
        }

        // Result object to be returned
        res.setReturnCode(PackageManager.INSTALL_SUCCEEDED);

        if (DEBUG_INSTALL) Slog.d(TAG, "installPackageLI: path=" + tmpPackageFile);

        // Sanity check
        if (ephemeral && (forwardLocked || onExternal)) {
            Slog.i(TAG, "Incompatible ephemeral install; fwdLocked=" + forwardLocked
                    + " external=" + onExternal);
            res.setReturnCode(PackageManager.INSTALL_FAILED_EPHEMERAL_INVALID);
            return;
        }

        // Retrieve PackageSettings and parse package
        final int parseFlags = mDefParseFlags | PackageParser.PARSE_CHATTY
                | PackageParser.PARSE_ENFORCE_CODE
                | (forwardLocked ? PackageParser.PARSE_FORWARD_LOCK : 0)
                | (onExternal ? PackageParser.PARSE_EXTERNAL_STORAGE : 0)
                | (ephemeral ? PackageParser.PARSE_IS_EPHEMERAL : 0)
                | (forceSdk ? PackageParser.PARSE_FORCE_SDK : 0);
        PackageParser pp = new PackageParser();
        pp.setSeparateProcesses(mSeparateProcesses);
        pp.setDisplayMetrics(mMetrics);

        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parsePackage");
        final PackageParser.Package pkg;
        try {
            pkg = pp.parsePackage(tmpPackageFile, parseFlags);
        } catch (PackageParserException e) {
            res.setError("Failed parse during installPackageLI", e);
            return;
        } finally {
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        }

        // If we are installing a clustered package add results for the children
        if (pkg.childPackages != null) {
            synchronized (mPackages) {
                final int childCount = pkg.childPackages.size();
                for (int i = 0; i < childCount; i++) {
                    PackageParser.Package childPkg = pkg.childPackages.get(i);
                    PackageInstalledInfo childRes = new PackageInstalledInfo();
                    childRes.setReturnCode(PackageManager.INSTALL_SUCCEEDED);
                    childRes.pkg = childPkg;
                    childRes.name = childPkg.packageName;
                    PackageSetting childPs = mSettings.peekPackageLPr(childPkg.packageName);
                    if (childPs != null) {
                        childRes.origUsers = childPs.queryInstalledUsers(
                                sUserManager.getUserIds(), true);
                    }
                    if ((mPackages.containsKey(childPkg.packageName))) {
                        childRes.removedInfo = new PackageRemovedInfo();
                        childRes.removedInfo.removedPackage = childPkg.packageName;
                    }
                    if (res.addedChildPackages == null) {
                        res.addedChildPackages = new ArrayMap<>();
                    }
                    res.addedChildPackages.put(childPkg.packageName, childRes);
                }
            }
        }

        // If package doesn't declare API override, mark that we have an install
        // time CPU ABI override.
        if (TextUtils.isEmpty(pkg.cpuAbiOverride)) {
            pkg.cpuAbiOverride = args.abiOverride;
        }

        String pkgName = res.name = pkg.packageName;
        if ((pkg.applicationInfo.flags&ApplicationInfo.FLAG_TEST_ONLY) != 0) {
            if ((installFlags & PackageManager.INSTALL_ALLOW_TEST) == 0) {
                res.setError(INSTALL_FAILED_TEST_ONLY, "installPackageLI");
                return;
            }
        }

        try {
            // either use what we've been given or parse directly from the APK
            if (args.certificates != null) {
                try {
                    PackageParser.populateCertificates(pkg, args.certificates);
                } catch (PackageParserException e) {
                    // there was something wrong with the certificates we were given;
                    // try to pull them from the APK
                    PackageParser.collectCertificates(pkg, parseFlags);
                }
            } else {
                PackageParser.collectCertificates(pkg, parseFlags);
            }
        } catch (PackageParserException e) {
            res.setError("Failed collect during installPackageLI", e);
            return;
        }

        // Get rid of all references to package scan path via parser.
        pp = null;
        String oldCodePath = null;
        boolean systemApp = false;
        synchronized (mPackages) {
            // Check if installing already existing package
            if ((installFlags & PackageManager.INSTALL_REPLACE_EXISTING) != 0) {
                String oldName = mSettings.mRenamedPackages.get(pkgName);
                if (pkg.mOriginalPackages != null
                        && pkg.mOriginalPackages.contains(oldName)
                        && mPackages.containsKey(oldName)) {
                    // This package is derived from an original package,
                    // and this device has been updating from that original
                    // name.  We must continue using the original name, so
                    // rename the new package here.
                    pkg.setPackageName(oldName);
                    pkgName = pkg.packageName;
                    replace = true;
                    if (DEBUG_INSTALL) Slog.d(TAG, "Replacing existing renamed package: oldName="
                            + oldName + " pkgName=" + pkgName);
                } else if (mPackages.containsKey(pkgName)) {
                    // This package, under its official name, already exists
                    // on the device; we should replace it.
                    replace = true;
                    if (DEBUG_INSTALL) Slog.d(TAG, "Replace existing pacakge: " + pkgName);
                }

                // Child packages are installed through the parent package
                if (pkg.parentPackage != null) {
                    res.setError(PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
                            "Package " + pkg.packageName + " is child of package "
                                    + pkg.parentPackage.parentPackage + ". Child packages "
                                    + "can be updated only through the parent package.");
                    return;
                }

                if (replace) {
                    // Prevent apps opting out from runtime permissions
                    PackageParser.Package oldPackage = mPackages.get(pkgName);
                    final int oldTargetSdk = oldPackage.applicationInfo.targetSdkVersion;
                    final int newTargetSdk = pkg.applicationInfo.targetSdkVersion;
                    if (oldTargetSdk > Build.VERSION_CODES.LOLLIPOP_MR1
                            && newTargetSdk <= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        res.setError(PackageManager.INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE,
                                "Package " + pkg.packageName + " new target SDK " + newTargetSdk
                                        + " doesn't support runtime permissions but the old"
                                        + " target SDK " + oldTargetSdk + " does.");
                        return;
                    }

                    // Prevent installing of child packages
                    if (oldPackage.parentPackage != null) {
                        res.setError(PackageManager.INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
                                "Package " + pkg.packageName + " is child of package "
                                        + oldPackage.parentPackage + ". Child packages "
                                        + "can be updated only through the parent package.");
                        return;
                    }
                }
            }

            PackageSetting ps = mSettings.mPackages.get(pkgName);
            if (ps != null) {
                if (DEBUG_INSTALL) Slog.d(TAG, "Existing package: " + ps);

                // Quick sanity check that we're signed correctly if updating;
                // we'll check this again later when scanning, but we want to
                // bail early here before tripping over redefined permissions.
                if (shouldCheckUpgradeKeySetLP(ps, scanFlags)) {
                    if (!checkUpgradeKeySetLP(ps, pkg)) {
                        res.setError(INSTALL_FAILED_UPDATE_INCOMPATIBLE, "Package "
                                + pkg.packageName + " upgrade keys do not match the "
                                + "previously installed version");
                        return;
                    }
                } else {
                    try {
                        verifySignaturesLP(ps, pkg);
                    } catch (PackageManagerException e) {
                        res.setError(e.error, e.getMessage());
                        return;
                    }
                }

                oldCodePath = mSettings.mPackages.get(pkgName).codePathString;
                if (ps.pkg != null && ps.pkg.applicationInfo != null) {
                    systemApp = (ps.pkg.applicationInfo.flags &
                            ApplicationInfo.FLAG_SYSTEM) != 0;
                }
                res.origUsers = ps.queryInstalledUsers(sUserManager.getUserIds(), true);
            }

            // Check whether the newly-scanned package wants to define an already-defined perm
            int N = pkg.permissions.size();
            for (int i = N-1; i >= 0; i--) {
                PackageParser.Permission perm = pkg.permissions.get(i);
                BasePermission bp = mSettings.mPermissions.get(perm.info.name);
                if (bp != null) {
                    // If the defining package is signed with our cert, it's okay.  This
                    // also includes the "updating the same package" case, of course.
                    // "updating same package" could also involve key-rotation.
                    final boolean sigsOk;
                    if (bp.sourcePackage.equals(pkg.packageName)
                            && (bp.packageSetting instanceof PackageSetting)
                            && (shouldCheckUpgradeKeySetLP((PackageSetting) bp.packageSetting,
                                    scanFlags))) {
                        sigsOk = checkUpgradeKeySetLP((PackageSetting) bp.packageSetting, pkg);
                    } else {
                        sigsOk = compareSignatures(bp.packageSetting.signatures.mSignatures,
                                pkg.mSignatures) == PackageManager.SIGNATURE_MATCH;
                    }
                    if (!sigsOk) {
                        // If the owning package is the system itself, we log but allow
                        // install to proceed; we fail the install on all other permission
                        // redefinitions.
                        if (!bp.sourcePackage.equals("android")) {
                            res.setError(INSTALL_FAILED_DUPLICATE_PERMISSION, "Package "
                                    + pkg.packageName + " attempting to redeclare permission "
                                    + perm.info.name + " already owned by " + bp.sourcePackage);
                            res.origPermission = perm.info.name;
                            res.origPackage = bp.sourcePackage;
                            return;
                        } else {
                            Slog.w(TAG, "Package " + pkg.packageName
                                    + " attempting to redeclare system permission "
                                    + perm.info.name + "; ignoring new declaration");
                            pkg.permissions.remove(i);
                        }
                    }
                }
            }
        }

        if (systemApp) {
            if (onExternal) {
                // Abort update; system app can't be replaced with app on sdcard
                res.setError(INSTALL_FAILED_INVALID_INSTALL_LOCATION,
                        "Cannot install updates to system apps on sdcard");
                return;
            } else if (ephemeral) {
                // Abort update; system app can't be replaced with an ephemeral app
                res.setError(INSTALL_FAILED_EPHEMERAL_INVALID,
                        "Cannot update a system app with an ephemeral app");
                return;
            }
        }

        if (args.move != null) {
            // We did an in-place move, so dex is ready to roll
            scanFlags |= SCAN_NO_DEX;
            scanFlags |= SCAN_MOVE;

            synchronized (mPackages) {
                final PackageSetting ps = mSettings.mPackages.get(pkgName);
                if (ps == null) {
                    res.setError(INSTALL_FAILED_INTERNAL_ERROR,
                            "Missing settings for moved package " + pkgName);
                }

                // We moved the entire application as-is, so bring over the
                // previously derived ABI information.
                pkg.applicationInfo.primaryCpuAbi = ps.primaryCpuAbiString;
                pkg.applicationInfo.secondaryCpuAbi = ps.secondaryCpuAbiString;
            }

        } else if (!forwardLocked && !pkg.applicationInfo.isExternalAsec()) {
            // Enable SCAN_NO_DEX flag to skip dexopt at a later stage
            scanFlags |= SCAN_NO_DEX;

            try {
                String abiOverride = (TextUtils.isEmpty(pkg.cpuAbiOverride) ?
                    args.abiOverride : pkg.cpuAbiOverride);
                derivePackageAbi(pkg, new File(pkg.codePath), abiOverride,
                        true /* extract libs */);
            } catch (PackageManagerException pme) {
                Slog.e(TAG, "Error deriving application ABI", pme);
                res.setError(INSTALL_FAILED_INTERNAL_ERROR, "Error deriving application ABI");
                return;
            }

            // Shared libraries for the package need to be updated.
            synchronized (mPackages) {
                try {
                    updateSharedLibrariesLPw(pkg, null);
                } catch (PackageManagerException e) {
                    Slog.e(TAG, "updateSharedLibrariesLPw failed: " + e.getMessage());
                }
            }
            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "dexopt");
            // Do not run PackageDexOptimizer through the local performDexOpt
            // method because `pkg` may not be in `mPackages` yet.
            //
            // Also, don't fail application installs if the dexopt step fails.
            mPackageDexOptimizer.performDexOpt(pkg, pkg.usesLibraryFiles,
                    null /* instructionSets */, false /* checkProfiles */,
                    getCompilerFilterForReason(REASON_INSTALL),
                    getOrCreateCompilerPackageStats(pkg));
            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);

            // Notify BackgroundDexOptService that the package has been changed.
            // If this is an update of a package which used to fail to compile,
            // BDOS will remove it from its blacklist.
            BackgroundDexOptService.notifyPackageChanged(pkg.packageName);
        }

        if (!args.doRename(res.returnCode, pkg, oldCodePath)) {
            res.setError(INSTALL_FAILED_INSUFFICIENT_STORAGE, "Failed rename");
            return;
        }

        startIntentFilterVerifications(args.user.getIdentifier(), replace, pkg);

        try (PackageFreezer freezer = freezePackageForInstall(pkgName, installFlags,
                "installPackageLI")) {
            if (replace) {
                replacePackageLIF(pkg, parseFlags, scanFlags | SCAN_REPLACING, args.user,
                        installerPackageName, res);
            } else {
                installNewPackageLIF(pkg, parseFlags, scanFlags | SCAN_DELETE_DATA_ON_FAILURES,
                        args.user, installerPackageName, volumeUuid, res);
            }
        }
        synchronized (mPackages) {
            final PackageSetting ps = mSettings.mPackages.get(pkgName);
            if (ps != null) {
                res.newUsers = ps.queryInstalledUsers(sUserManager.getUserIds(), true);
            }

            final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
            for (int i = 0; i < childCount; i++) {
                PackageParser.Package childPkg = pkg.childPackages.get(i);
                PackageInstalledInfo childRes = res.addedChildPackages.get(childPkg.packageName);
                PackageSetting childPs = mSettings.peekPackageLPr(childPkg.packageName);
                if (childPs != null) {
                    childRes.newUsers = childPs.queryInstalledUsers(
                            sUserManager.getUserIds(), true);
                }
            }
        }
    }

    private void startIntentFilterVerifications(int userId, boolean replacing,
            PackageParser.Package pkg) {
        if (mIntentFilterVerifierComponent == null) {
            Slog.w(TAG, "No IntentFilter verification will not be done as "
                    + "there is no IntentFilterVerifier available!");
            return;
        }

        final int verifierUid = getPackageUid(
                mIntentFilterVerifierComponent.getPackageName(),
                MATCH_DEBUG_TRIAGED_MISSING,
                (userId == UserHandle.USER_ALL) ? UserHandle.USER_SYSTEM : userId);

        Message msg = mHandler.obtainMessage(START_INTENT_FILTER_VERIFICATIONS);
        msg.obj = new IFVerificationParams(pkg, replacing, userId, verifierUid);
        mHandler.sendMessage(msg);

        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageParser.Package childPkg = pkg.childPackages.get(i);
            msg = mHandler.obtainMessage(START_INTENT_FILTER_VERIFICATIONS);
            msg.obj = new IFVerificationParams(childPkg, replacing, userId, verifierUid);
            mHandler.sendMessage(msg);
        }
    }

    private void verifyIntentFiltersIfNeeded(int userId, int verifierUid, boolean replacing,
            PackageParser.Package pkg) {
        int size = pkg.activities.size();
        if (size == 0) {
            if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                    "No activity, so no need to verify any IntentFilter!");
            return;
        }

        final boolean hasDomainURLs = hasDomainURLs(pkg);
        if (!hasDomainURLs) {
            if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                    "No domain URLs, so no need to verify any IntentFilter!");
            return;
        }

        if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG, "Checking for userId:" + userId
                + " if any IntentFilter from the " + size
                + " Activities needs verification ...");

        int count = 0;
        final String packageName = pkg.packageName;

        synchronized (mPackages) {
            // If this is a new install and we see that we've already run verification for this
            // package, we have nothing to do: it means the state was restored from backup.
            if (!replacing) {
                IntentFilterVerificationInfo ivi =
                        mSettings.getIntentFilterVerificationLPr(packageName);
                if (ivi != null) {
                    if (DEBUG_DOMAIN_VERIFICATION) {
                        Slog.i(TAG, "Package " + packageName+ " already verified: status="
                                + ivi.getStatusString());
                    }
                    return;
                }
            }

            // If any filters need to be verified, then all need to be.
            boolean needToVerify = false;
            for (PackageParser.Activity a : pkg.activities) {
                for (ActivityIntentInfo filter : a.intents) {
                    if (filter.needsVerification() && needsNetworkVerificationLPr(filter)) {
                        if (DEBUG_DOMAIN_VERIFICATION) {
                            Slog.d(TAG, "Intent filter needs verification, so processing all filters");
                        }
                        needToVerify = true;
                        break;
                    }
                }
            }

            if (needToVerify) {
                final int verificationId = mIntentFilterVerificationToken++;
                for (PackageParser.Activity a : pkg.activities) {
                    for (ActivityIntentInfo filter : a.intents) {
                        if (filter.handlesWebUris(true) && needsNetworkVerificationLPr(filter)) {
                            if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG,
                                    "Verification needed for IntentFilter:" + filter.toString());
                            mIntentFilterVerifier.addOneIntentFilterVerification(
                                    verifierUid, userId, verificationId, filter, packageName);
                            count++;
                        }
                    }
                }
            }
        }

        if (count > 0) {
            if (DEBUG_DOMAIN_VERIFICATION) Slog.d(TAG, "Starting " + count
                    + " IntentFilter verification" + (count > 1 ? "s" : "")
                    +  " for userId:" + userId);
            mIntentFilterVerifier.startVerifications(userId);
        } else {
            if (DEBUG_DOMAIN_VERIFICATION) {
                Slog.d(TAG, "No filters or not all autoVerify for " + packageName);
            }
        }
    }

    private boolean needsNetworkVerificationLPr(ActivityIntentInfo filter) {
        final ComponentName cn  = filter.activity.getComponentName();
        final String packageName = cn.getPackageName();

        IntentFilterVerificationInfo ivi = mSettings.getIntentFilterVerificationLPr(
                packageName);
        if (ivi == null) {
            return true;
        }
        int status = ivi.getStatus();
        switch (status) {
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED:
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK:
                return true;

            default:
                // Nothing to do
                return false;
        }
    }

    private static boolean isMultiArch(ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_MULTIARCH) != 0;
    }

    private static boolean isExternal(PackageParser.Package pkg) {
        return (pkg.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    private static boolean isExternal(PackageSetting ps) {
        return (ps.pkgFlags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    private static boolean isEphemeral(PackageParser.Package pkg) {
        return pkg.applicationInfo.isEphemeralApp();
    }

    private static boolean isEphemeral(PackageSetting ps) {
        return ps.pkg != null && isEphemeral(ps.pkg);
    }

    private static boolean isSystemApp(PackageParser.Package pkg) {
        return (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isPrivilegedApp(PackageParser.Package pkg) {
        return (pkg.applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0;
    }

    private static boolean hasDomainURLs(PackageParser.Package pkg) {
        return (pkg.applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS) != 0;
    }

    private static boolean isSystemApp(PackageSetting ps) {
        return (ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isUpdatedSystemApp(PackageSetting ps) {
        return (ps.pkgFlags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private int packageFlagsToInstallFlags(PackageSetting ps) {
        int installFlags = 0;
        if (isEphemeral(ps)) {
            installFlags |= PackageManager.INSTALL_EPHEMERAL;
        }
        if (isExternal(ps) && TextUtils.isEmpty(ps.volumeUuid)) {
            // This existing package was an external ASEC install when we have
            // the external flag without a UUID
            installFlags |= PackageManager.INSTALL_EXTERNAL;
        }
        if (ps.isForwardLocked()) {
            installFlags |= PackageManager.INSTALL_FORWARD_LOCK;
        }
        return installFlags;
    }

    private String getVolumeUuidForPackage(PackageParser.Package pkg) {
        if (isExternal(pkg)) {
            if (TextUtils.isEmpty(pkg.volumeUuid)) {
                return StorageManager.UUID_PRIMARY_PHYSICAL;
            } else {
                return pkg.volumeUuid;
            }
        } else {
            return StorageManager.UUID_PRIVATE_INTERNAL;
        }
    }

    private VersionInfo getSettingsVersionForPackage(PackageParser.Package pkg) {
        if (isExternal(pkg)) {
            if (TextUtils.isEmpty(pkg.volumeUuid)) {
                return mSettings.getExternalVersion();
            } else {
                return mSettings.findOrCreateVersion(pkg.volumeUuid);
            }
        } else {
            return mSettings.getInternalVersion();
        }
    }

    private void deleteTempPackageFiles() {
        final FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("vmdl") && name.endsWith(".tmp");
            }
        };
        for (File file : mDrmAppPrivateInstallDir.listFiles(filter)) {
            file.delete();
        }
    }

    @Override
    public void deletePackageAsUser(String packageName, IPackageDeleteObserver observer, int userId,
            int flags) {
        deletePackage(packageName, new LegacyPackageDeleteObserver(observer).getBinder(), userId,
                flags);
    }

    @Override
    public void deletePackage(final String packageName,
            final IPackageDeleteObserver2 observer, final int userId, final int deleteFlags) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DELETE_PACKAGES, null);
        Preconditions.checkNotNull(packageName);
        Preconditions.checkNotNull(observer);
        final int uid = Binder.getCallingUid();
        final boolean deleteAllUsers = (deleteFlags & PackageManager.DELETE_ALL_USERS) != 0;
        final int[] users = deleteAllUsers ? sUserManager.getUserIds() : new int[]{ userId };
        if (UserHandle.getUserId(uid) != userId || (deleteAllUsers && users.length > 1)) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "deletePackage for user " + userId);
        }

        if (isUserRestricted(userId, UserManager.DISALLOW_UNINSTALL_APPS)) {
            try {
                observer.onPackageDeleted(packageName,
                        PackageManager.DELETE_FAILED_USER_RESTRICTED, null);
            } catch (RemoteException re) {
            }
            return;
        }

        if (!deleteAllUsers && getBlockUninstallForUser(packageName, userId)) {
            try {
                observer.onPackageDeleted(packageName,
                        PackageManager.DELETE_FAILED_OWNER_BLOCKED, null);
            } catch (RemoteException re) {
            }
            return;
        }

        if (DEBUG_REMOVE) {
            Slog.d(TAG, "deletePackageAsUser: pkg=" + packageName + " user=" + userId
                    + " deleteAllUsers: " + deleteAllUsers );
        }
        // Queue up an async operation since the package deletion may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                int returnCode;
                if (!deleteAllUsers) {
                    returnCode = deletePackageX(packageName, userId, deleteFlags);
                } else {
                    int[] blockUninstallUserIds = getBlockUninstallForUsers(packageName, users);
                    // If nobody is blocking uninstall, proceed with delete for all users
                    if (ArrayUtils.isEmpty(blockUninstallUserIds)) {
                        returnCode = deletePackageX(packageName, userId, deleteFlags);
                    } else {
                        // Otherwise uninstall individually for users with blockUninstalls=false
                        final int userFlags = deleteFlags & ~PackageManager.DELETE_ALL_USERS;
                        for (int userId : users) {
                            if (!ArrayUtils.contains(blockUninstallUserIds, userId)) {
                                returnCode = deletePackageX(packageName, userId, userFlags);
                                if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                                    Slog.w(TAG, "Package delete failed for user " + userId
                                            + ", returnCode " + returnCode);
                                }
                            }
                        }
                        // The app has only been marked uninstalled for certain users.
                        // We still need to report that delete was blocked
                        returnCode = PackageManager.DELETE_FAILED_OWNER_BLOCKED;
                    }
                }
                try {
                    observer.onPackageDeleted(packageName, returnCode, null);
                } catch (RemoteException e) {
                    Log.i(TAG, "Observer no longer exists.");
                } //end catch
            } //end run
        });
    }

    private int[] getBlockUninstallForUsers(String packageName, int[] userIds) {
        int[] result = EMPTY_INT_ARRAY;
        for (int userId : userIds) {
            if (getBlockUninstallForUser(packageName, userId)) {
                result = ArrayUtils.appendInt(result, userId);
            }
        }
        return result;
    }

    @Override
    public boolean isPackageDeviceAdminOnAnyUser(String packageName) {
        return isPackageDeviceAdmin(packageName, UserHandle.USER_ALL);
    }

    private boolean isPackageDeviceAdmin(String packageName, int userId) {
        IDevicePolicyManager dpm = IDevicePolicyManager.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_POLICY_SERVICE));
        try {
            if (dpm != null) {
                final ComponentName deviceOwnerComponentName = dpm.getDeviceOwnerComponent(
                        /* callingUserOnly =*/ false);
                final String deviceOwnerPackageName = deviceOwnerComponentName == null ? null
                        : deviceOwnerComponentName.getPackageName();
                // Does the package contains the device owner?
                // TODO Do we have to do it even if userId != UserHandle.USER_ALL?  Otherwise,
                // this check is probably not needed, since DO should be registered as a device
                // admin on some user too. (Original bug for this: b/17657954)
                if (packageName.equals(deviceOwnerPackageName)) {
                    return true;
                }
                // Does it contain a device admin for any user?
                int[] users;
                if (userId == UserHandle.USER_ALL) {
                    users = sUserManager.getUserIds();
                } else {
                    users = new int[]{userId};
                }
                for (int i = 0; i < users.length; ++i) {
                    if (dpm.packageHasActiveAdmins(packageName, users[i])) {
                        return true;
                    }
                }
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private boolean shouldKeepUninstalledPackageLPr(String packageName) {
        return mKeepUninstalledPackages != null && mKeepUninstalledPackages.contains(packageName);
    }

    /**
     *  This method is an internal method that could be get invoked either
     *  to delete an installed package or to clean up a failed installation.
     *  After deleting an installed package, a broadcast is sent to notify any
     *  listeners that the package has been removed. For cleaning up a failed
     *  installation, the broadcast is not necessary since the package's
     *  installation wouldn't have sent the initial broadcast either
     *  The key steps in deleting a package are
     *  deleting the package information in internal structures like mPackages,
     *  deleting the packages base directories through installd
     *  updating mSettings to reflect current status
     *  persisting settings for later use
     *  sending a broadcast if necessary
     */
    private int deletePackageX(String packageName, int userId, int deleteFlags) {
        final PackageRemovedInfo info = new PackageRemovedInfo();
        final boolean res;

        final int removeUser = (deleteFlags & PackageManager.DELETE_ALL_USERS) != 0
                ? UserHandle.USER_ALL : userId;

        if (isPackageDeviceAdmin(packageName, removeUser)) {
            Slog.w(TAG, "Not removing package " + packageName + ": has active device admin");
            return PackageManager.DELETE_FAILED_DEVICE_POLICY_MANAGER;
        }

        PackageSetting uninstalledPs = null;

        // for the uninstall-updates case and restricted profiles, remember the per-
        // user handle installed state
        int[] allUsers;
        synchronized (mPackages) {
            uninstalledPs = mSettings.mPackages.get(packageName);
            if (uninstalledPs == null) {
                Slog.w(TAG, "Not removing non-existent package " + packageName);
                return PackageManager.DELETE_FAILED_INTERNAL_ERROR;
            }
            allUsers = sUserManager.getUserIds();
            info.origUsers = uninstalledPs.queryInstalledUsers(allUsers, true);
        }

        final int freezeUser;
        if (isUpdatedSystemApp(uninstalledPs)
                && ((deleteFlags & PackageManager.DELETE_SYSTEM_APP) == 0)) {
            // We're downgrading a system app, which will apply to all users, so
            // freeze them all during the downgrade
            freezeUser = UserHandle.USER_ALL;
        } else {
            freezeUser = removeUser;
        }

        synchronized (mInstallLock) {
            if (DEBUG_REMOVE) Slog.d(TAG, "deletePackageX: pkg=" + packageName + " user=" + userId);
            try (PackageFreezer freezer = freezePackageForDelete(packageName, freezeUser,
                    deleteFlags, "deletePackageX")) {
                res = deletePackageLIF(packageName, UserHandle.of(removeUser), true, allUsers,
                        deleteFlags | REMOVE_CHATTY, info, true, null);
            }
            synchronized (mPackages) {
                if (res) {
                    mEphemeralApplicationRegistry.onPackageUninstalledLPw(uninstalledPs.pkg);
                }
            }
        }

        if (res) {
            final boolean killApp = (deleteFlags & PackageManager.DELETE_DONT_KILL_APP) == 0;
            info.sendPackageRemovedBroadcasts(killApp);
            info.sendSystemPackageUpdatedBroadcasts();
            info.sendSystemPackageAppearedBroadcasts();
        }
        // Force a gc here.
        Runtime.getRuntime().gc();
        // Delete the resources here after sending the broadcast to let
        // other processes clean up before deleting resources.
        if (info.args != null) {
            synchronized (mInstallLock) {
                info.args.doPostDeleteLI(true);
            }
        }

        return res ? PackageManager.DELETE_SUCCEEDED : PackageManager.DELETE_FAILED_INTERNAL_ERROR;
    }

    class PackageRemovedInfo {
        String removedPackage;
        int uid = -1;
        int removedAppId = -1;
        int[] origUsers;
        int[] removedUsers = null;
        boolean isRemovedPackageSystemUpdate = false;
        boolean isUpdate;
        boolean dataRemoved;
        boolean removedForAllUsers;
        // Clean up resources deleted packages.
        InstallArgs args = null;
        ArrayMap<String, PackageRemovedInfo> removedChildPackages;
        ArrayMap<String, PackageInstalledInfo> appearedChildPackages;

        void sendPackageRemovedBroadcasts(boolean killApp) {
            sendPackageRemovedBroadcastInternal(killApp);
            final int childCount = removedChildPackages != null ? removedChildPackages.size() : 0;
            for (int i = 0; i < childCount; i++) {
                PackageRemovedInfo childInfo = removedChildPackages.valueAt(i);
                childInfo.sendPackageRemovedBroadcastInternal(killApp);
            }
        }

        void sendSystemPackageUpdatedBroadcasts() {
            if (isRemovedPackageSystemUpdate) {
                sendSystemPackageUpdatedBroadcastsInternal();
                final int childCount = (removedChildPackages != null)
                        ? removedChildPackages.size() : 0;
                for (int i = 0; i < childCount; i++) {
                    PackageRemovedInfo childInfo = removedChildPackages.valueAt(i);
                    if (childInfo.isRemovedPackageSystemUpdate) {
                        childInfo.sendSystemPackageUpdatedBroadcastsInternal();
                    }
                }
            }
        }

        void sendSystemPackageAppearedBroadcasts() {
            final int packageCount = (appearedChildPackages != null)
                    ? appearedChildPackages.size() : 0;
            for (int i = 0; i < packageCount; i++) {
                PackageInstalledInfo installedInfo = appearedChildPackages.valueAt(i);
                for (int userId : installedInfo.newUsers) {
                    sendPackageAddedForUser(installedInfo.name, true,
                            UserHandle.getAppId(installedInfo.uid), userId);
                }
            }
        }

        private void sendSystemPackageUpdatedBroadcastsInternal() {
            Bundle extras = new Bundle(2);
            extras.putInt(Intent.EXTRA_UID, removedAppId >= 0 ? removedAppId : uid);
            extras.putBoolean(Intent.EXTRA_REPLACING, true);
            sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, removedPackage,
                    extras, 0, null, null, null);
            sendPackageBroadcast(Intent.ACTION_PACKAGE_REPLACED, removedPackage,
                    extras, 0, null, null, null);
            sendPackageBroadcast(Intent.ACTION_MY_PACKAGE_REPLACED, null,
                    null, 0, removedPackage, null, null);
        }

        private void sendPackageRemovedBroadcastInternal(boolean killApp) {
            Bundle extras = new Bundle(2);
            extras.putInt(Intent.EXTRA_UID, removedAppId >= 0  ? removedAppId : uid);
            extras.putBoolean(Intent.EXTRA_DATA_REMOVED, dataRemoved);
            extras.putBoolean(Intent.EXTRA_DONT_KILL_APP, !killApp);
            if (isUpdate || isRemovedPackageSystemUpdate) {
                extras.putBoolean(Intent.EXTRA_REPLACING, true);
            }
            extras.putBoolean(Intent.EXTRA_REMOVED_FOR_ALL_USERS, removedForAllUsers);
            if (removedPackage != null) {
                sendPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, removedPackage,
                        extras, 0, null, null, removedUsers);
                if (dataRemoved && !isRemovedPackageSystemUpdate) {
                    sendPackageBroadcast(Intent.ACTION_PACKAGE_FULLY_REMOVED,
                            removedPackage, extras, 0, null, null, removedUsers);
                }
            }
            if (removedAppId >= 0) {
                sendPackageBroadcast(Intent.ACTION_UID_REMOVED, null, extras, 0, null, null,
                        removedUsers);
            }
        }
    }

    /*
     * This method deletes the package from internal data structures. If the DONT_DELETE_DATA
     * flag is not set, the data directory is removed as well.
     * make sure this flag is set for partially installed apps. If not its meaningless to
     * delete a partially installed application.
     */
    private void removePackageDataLIF(PackageSetting ps, int[] allUserHandles,
            PackageRemovedInfo outInfo, int flags, boolean writeSettings) {
        String packageName = ps.name;
        if (DEBUG_REMOVE) Slog.d(TAG, "removePackageDataLI: " + ps);
        // Retrieve object to delete permissions for shared user later on
        final PackageParser.Package deletedPkg;
        final PackageSetting deletedPs;
        // reader
        synchronized (mPackages) {
            deletedPkg = mPackages.get(packageName);
            deletedPs = mSettings.mPackages.get(packageName);
            if (outInfo != null) {
                outInfo.removedPackage = packageName;
                outInfo.removedUsers = deletedPs != null
                        ? deletedPs.queryInstalledUsers(sUserManager.getUserIds(), true)
                        : null;
            }
        }

        removePackageLI(ps, (flags & REMOVE_CHATTY) != 0);

        if ((flags & PackageManager.DELETE_KEEP_DATA) == 0) {
            final PackageParser.Package resolvedPkg;
            if (deletedPkg != null) {
                resolvedPkg = deletedPkg;
            } else {
                // We don't have a parsed package when it lives on an ejected
                // adopted storage device, so fake something together
                resolvedPkg = new PackageParser.Package(ps.name);
                resolvedPkg.setVolumeUuid(ps.volumeUuid);
            }
            destroyAppDataLIF(resolvedPkg, UserHandle.USER_ALL,
                    StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE);
            destroyAppProfilesLIF(resolvedPkg, UserHandle.USER_ALL);
            if (outInfo != null) {
                outInfo.dataRemoved = true;
            }
            schedulePackageCleaning(packageName, UserHandle.USER_ALL, true);
        }

        // writer
        synchronized (mPackages) {
            if (deletedPs != null) {
                if ((flags&PackageManager.DELETE_KEEP_DATA) == 0) {
                    clearIntentFilterVerificationsLPw(deletedPs.name, UserHandle.USER_ALL);
                    clearDefaultBrowserIfNeeded(packageName);
                    if (outInfo != null) {
                        mSettings.mKeySetManagerService.removeAppKeySetDataLPw(packageName);
                        outInfo.removedAppId = mSettings.removePackageLPw(packageName);
                    }
                    updatePermissionsLPw(deletedPs.name, null, 0);
                    if (deletedPs.sharedUser != null) {
                        // Remove permissions associated with package. Since runtime
                        // permissions are per user we have to kill the removed package
                        // or packages running under the shared user of the removed
                        // package if revoking the permissions requested only by the removed
                        // package is successful and this causes a change in gids.
                        for (int userId : UserManagerService.getInstance().getUserIds()) {
                            final int userIdToKill = mSettings.updateSharedUserPermsLPw(deletedPs,
                                    userId);
                            if (userIdToKill == UserHandle.USER_ALL
                                    || userIdToKill >= UserHandle.USER_SYSTEM) {
                                // If gids changed for this user, kill all affected packages.
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // This has to happen with no lock held.
                                        killApplication(deletedPs.name, deletedPs.appId,
                                                KILL_APP_REASON_GIDS_CHANGED);
                                    }
                                });
                                break;
                            }
                        }
                    }
                    clearPackagePreferredActivitiesLPw(deletedPs.name, UserHandle.USER_ALL);
                }
                // make sure to preserve per-user disabled state if this removal was just
                // a downgrade of a system app to the factory package
                if (allUserHandles != null && outInfo != null && outInfo.origUsers != null) {
                    if (DEBUG_REMOVE) {
                        Slog.d(TAG, "Propagating install state across downgrade");
                    }
                    for (int userId : allUserHandles) {
                        final boolean installed = ArrayUtils.contains(outInfo.origUsers, userId);
                        if (DEBUG_REMOVE) {
                            Slog.d(TAG, "    user " + userId + " => " + installed);
                        }
                        ps.setInstalled(installed, userId);
                    }
                }
            }
            // can downgrade to reader
            if (writeSettings) {
                // Save settings now
                mSettings.writeLPr();
            }
        }
        if (outInfo != null) {
            // A user ID was deleted here. Go through all users and remove it
            // from KeyStore.
            removeKeystoreDataIfNeeded(UserHandle.USER_ALL, outInfo.removedAppId);
        }
    }

    static boolean locationIsPrivileged(File path) {
        try {
            final String privilegedAppDir = new File(Environment.getRootDirectory(), "priv-app")
                    .getCanonicalPath();
            return path.getCanonicalPath().startsWith(privilegedAppDir);
        } catch (IOException e) {
            Slog.e(TAG, "Unable to access code path " + path);
        }
        return false;
    }

    /*
     * Tries to delete system package.
     */
    private boolean deleteSystemPackageLIF(PackageParser.Package deletedPkg,
            PackageSetting deletedPs, int[] allUserHandles, int flags, PackageRemovedInfo outInfo,
            boolean writeSettings) {
        if (deletedPs.parentPackageName != null) {
            Slog.w(TAG, "Attempt to delete child system package " + deletedPkg.packageName);
            return false;
        }

        final boolean applyUserRestrictions
                = (allUserHandles != null) && (outInfo.origUsers != null);
        final PackageSetting disabledPs;
        // Confirm if the system package has been updated
        // An updated system app can be deleted. This will also have to restore
        // the system pkg from system partition
        // reader
        synchronized (mPackages) {
            disabledPs = mSettings.getDisabledSystemPkgLPr(deletedPs.name);
        }

        if (DEBUG_REMOVE) Slog.d(TAG, "deleteSystemPackageLI: newPs=" + deletedPkg.packageName
                + " disabledPs=" + disabledPs);

        if (disabledPs == null) {
            Slog.w(TAG, "Attempt to delete unknown system package "+ deletedPkg.packageName);
            return false;
        } else if (DEBUG_REMOVE) {
            Slog.d(TAG, "Deleting system pkg from data partition");
        }

        if (DEBUG_REMOVE) {
            if (applyUserRestrictions) {
                Slog.d(TAG, "Remembering install states:");
                for (int userId : allUserHandles) {
                    final boolean finstalled = ArrayUtils.contains(outInfo.origUsers, userId);
                    Slog.d(TAG, "   u=" + userId + " inst=" + finstalled);
                }
            }
        }

        // Delete the updated package
        outInfo.isRemovedPackageSystemUpdate = true;
        if (outInfo.removedChildPackages != null) {
            final int childCount = (deletedPs.childPackageNames != null)
                    ? deletedPs.childPackageNames.size() : 0;
            for (int i = 0; i < childCount; i++) {
                String childPackageName = deletedPs.childPackageNames.get(i);
                if (disabledPs.childPackageNames != null && disabledPs.childPackageNames
                        .contains(childPackageName)) {
                    PackageRemovedInfo childInfo = outInfo.removedChildPackages.get(
                            childPackageName);
                    if (childInfo != null) {
                        childInfo.isRemovedPackageSystemUpdate = true;
                    }
                }
            }
        }

        if (disabledPs.versionCode < deletedPs.versionCode) {
            // Delete data for downgrades
            flags &= ~PackageManager.DELETE_KEEP_DATA;
        } else {
            // Preserve data by setting flag
            flags |= PackageManager.DELETE_KEEP_DATA;
        }

        boolean ret = deleteInstalledPackageLIF(deletedPs, true, flags, allUserHandles,
                outInfo, writeSettings, disabledPs.pkg);
        if (!ret) {
            return false;
        }

        // writer
        synchronized (mPackages) {
            // Reinstate the old system package
            enableSystemPackageLPw(disabledPs.pkg);
            // Remove any native libraries from the upgraded package.
            removeNativeBinariesLI(deletedPs);
        }

        // Install the system package
        if (DEBUG_REMOVE) Slog.d(TAG, "Re-installing system package: " + disabledPs);
        int parseFlags = mDefParseFlags
                | PackageParser.PARSE_MUST_BE_APK
                | PackageParser.PARSE_IS_SYSTEM
                | PackageParser.PARSE_IS_SYSTEM_DIR;
        if (locationIsPrivileged(disabledPs.codePath)) {
            parseFlags |= PackageParser.PARSE_IS_PRIVILEGED;
        }

        final PackageParser.Package newPkg;
        try {
            newPkg = scanPackageTracedLI(disabledPs.codePath, parseFlags, SCAN_NO_PATHS, 0, null);
        } catch (PackageManagerException e) {
            Slog.w(TAG, "Failed to restore system package:" + deletedPkg.packageName + ": "
                    + e.getMessage());
            return false;
        }
        try {
            // update shared libraries for the newly re-installed system package
            updateSharedLibrariesLPw(newPkg, null);
        } catch (PackageManagerException e) {
            Slog.e(TAG, "updateAllSharedLibrariesLPw failed: " + e.getMessage());
        }

        prepareAppDataAfterInstallLIF(newPkg);

        // writer
        synchronized (mPackages) {
            PackageSetting ps = mSettings.mPackages.get(newPkg.packageName);

            // Propagate the permissions state as we do not want to drop on the floor
            // runtime permissions. The update permissions method below will take
            // care of removing obsolete permissions and grant install permissions.
            ps.getPermissionsState().copyFrom(deletedPs.getPermissionsState());
            updatePermissionsLPw(newPkg.packageName, newPkg,
                    UPDATE_PERMISSIONS_ALL | UPDATE_PERMISSIONS_REPLACE_PKG);

            if (applyUserRestrictions) {
                if (DEBUG_REMOVE) {
                    Slog.d(TAG, "Propagating install state across reinstall");
                }
                for (int userId : allUserHandles) {
                    final boolean installed = ArrayUtils.contains(outInfo.origUsers, userId);
                    if (DEBUG_REMOVE) {
                        Slog.d(TAG, "    user " + userId + " => " + installed);
                    }
                    ps.setInstalled(installed, userId);

                    mSettings.writeRuntimePermissionsForUserLPr(userId, false);
                }
                // Regardless of writeSettings we need to ensure that this restriction
                // state propagation is persisted
                mSettings.writeAllUsersPackageRestrictionsLPr();
            }
            // can downgrade to reader here
            if (writeSettings) {
                mSettings.writeLPr();
            }
        }
        return true;
    }

    private boolean deleteInstalledPackageLIF(PackageSetting ps,
            boolean deleteCodeAndResources, int flags, int[] allUserHandles,
            PackageRemovedInfo outInfo, boolean writeSettings,
            PackageParser.Package replacingPackage) {
        synchronized (mPackages) {
            if (outInfo != null) {
                outInfo.uid = ps.appId;
            }

            if (outInfo != null && outInfo.removedChildPackages != null) {
                final int childCount = (ps.childPackageNames != null)
                        ? ps.childPackageNames.size() : 0;
                for (int i = 0; i < childCount; i++) {
                    String childPackageName = ps.childPackageNames.get(i);
                    PackageSetting childPs = mSettings.mPackages.get(childPackageName);
                    if (childPs == null) {
                        return false;
                    }
                    PackageRemovedInfo childInfo = outInfo.removedChildPackages.get(
                            childPackageName);
                    if (childInfo != null) {
                        childInfo.uid = childPs.appId;
                    }
                }
            }
        }

        // Delete package data from internal structures and also remove data if flag is set
        removePackageDataLIF(ps, allUserHandles, outInfo, flags, writeSettings);

        // Delete the child packages data
        final int childCount = (ps.childPackageNames != null) ? ps.childPackageNames.size() : 0;
        for (int i = 0; i < childCount; i++) {
            PackageSetting childPs;
            synchronized (mPackages) {
                childPs = mSettings.peekPackageLPr(ps.childPackageNames.get(i));
            }
            if (childPs != null) {
                PackageRemovedInfo childOutInfo = (outInfo != null
                        && outInfo.removedChildPackages != null)
                        ? outInfo.removedChildPackages.get(childPs.name) : null;
                final int deleteFlags = (flags & DELETE_KEEP_DATA) != 0
                        && (replacingPackage != null
                        && !replacingPackage.hasChildPackage(childPs.name))
                        ? flags & ~DELETE_KEEP_DATA : flags;
                removePackageDataLIF(childPs, allUserHandles, childOutInfo,
                        deleteFlags, writeSettings);
            }
        }

        // Delete application code and resources only for parent packages
        if (ps.parentPackageName == null) {
            if (deleteCodeAndResources && (outInfo != null)) {
                outInfo.args = createInstallArgsForExisting(packageFlagsToInstallFlags(ps),
                        ps.codePathString, ps.resourcePathString, getAppDexInstructionSets(ps));
                if (DEBUG_SD_INSTALL) Slog.i(TAG, "args=" + outInfo.args);
            }
        }

        return true;
    }

    @Override
    public boolean setBlockUninstallForUser(String packageName, boolean blockUninstall,
            int userId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DELETE_PACKAGES, null);
        synchronized (mPackages) {
            PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                Log.i(TAG, "Package doesn't exist in set block uninstall " + packageName);
                return false;
            }
            if (!ps.getInstalled(userId)) {
                // Can't block uninstall for an app that is not installed or enabled.
                Log.i(TAG, "Package not installed in set block uninstall " + packageName);
                return false;
            }
            ps.setBlockUninstall(blockUninstall, userId);
            mSettings.writePackageRestrictionsLPr(userId);
        }
        return true;
    }

    @Override
    public boolean getBlockUninstallForUser(String packageName, int userId) {
        synchronized (mPackages) {
            PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                Log.i(TAG, "Package doesn't exist in get block uninstall " + packageName);
                return false;
            }
            return ps.getBlockUninstall(userId);
        }
    }

    @Override
    public boolean setRequiredForSystemUser(String packageName, boolean systemUserApp) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.SYSTEM_UID && callingUid != Process.ROOT_UID) {
            throw new SecurityException(
                    "setRequiredForSystemUser can only be run by the system or root");
        }
        synchronized (mPackages) {
            PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                Log.w(TAG, "Package doesn't exist: " + packageName);
                return false;
            }
            if (systemUserApp) {
                ps.pkgPrivateFlags |= ApplicationInfo.PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER;
            } else {
                ps.pkgPrivateFlags &= ~ApplicationInfo.PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER;
            }
            mSettings.writeLPr();
        }
        return true;
    }

    /*
     * This method handles package deletion in general
     */
    private boolean deletePackageLIF(String packageName, UserHandle user,
            boolean deleteCodeAndResources, int[] allUserHandles, int flags,
            PackageRemovedInfo outInfo, boolean writeSettings,
            PackageParser.Package replacingPackage) {
        if (packageName == null) {
            Slog.w(TAG, "Attempt to delete null packageName.");
            return false;
        }

        if (DEBUG_REMOVE) Slog.d(TAG, "deletePackageLI: " + packageName + " user " + user);

        PackageSetting ps;

        synchronized (mPackages) {
            ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                Slog.w(TAG, "Package named '" + packageName + "' doesn't exist.");
                return false;
            }

            if (ps.parentPackageName != null && (!isSystemApp(ps)
                    || (flags & PackageManager.DELETE_SYSTEM_APP) != 0)) {
                if (DEBUG_REMOVE) {
                    Slog.d(TAG, "Uninstalled child package:" + packageName + " for user:"
                            + ((user == null) ? UserHandle.USER_ALL : user));
                }
                final int removedUserId = (user != null) ? user.getIdentifier()
                        : UserHandle.USER_ALL;
                if (!clearPackageStateForUserLIF(ps, removedUserId, outInfo)) {
                    return false;
                }
                markPackageUninstalledForUserLPw(ps, user);
                scheduleWritePackageRestrictionsLocked(user);
                return true;
            }
        }

        if (((!isSystemApp(ps) || (flags&PackageManager.DELETE_SYSTEM_APP) != 0) && user != null
                && user.getIdentifier() != UserHandle.USER_ALL)) {
            // The caller is asking that the package only be deleted for a single
            // user.  To do this, we just mark its uninstalled state and delete
            // its data. If this is a system app, we only allow this to happen if
            // they have set the special DELETE_SYSTEM_APP which requests different
            // semantics than normal for uninstalling system apps.
            markPackageUninstalledForUserLPw(ps, user);

            if (!isSystemApp(ps)) {
                // Do not uninstall the APK if an app should be cached
                boolean keepUninstalledPackage = shouldKeepUninstalledPackageLPr(packageName);
                if (ps.isAnyInstalled(sUserManager.getUserIds()) || keepUninstalledPackage) {
                    // Other user still have this package installed, so all
                    // we need to do is clear this user's data and save that
                    // it is uninstalled.
                    if (DEBUG_REMOVE) Slog.d(TAG, "Still installed by other users");
                    if (!clearPackageStateForUserLIF(ps, user.getIdentifier(), outInfo)) {
                        return false;
                    }
                    scheduleWritePackageRestrictionsLocked(user);
                    return true;
                } else {
                    // We need to set it back to 'installed' so the uninstall
                    // broadcasts will be sent correctly.
                    if (DEBUG_REMOVE) Slog.d(TAG, "Not installed by other users, full delete");
                    ps.setInstalled(true, user.getIdentifier());
                }
            } else {
                // This is a system app, so we assume that the
                // other users still have this package installed, so all
                // we need to do is clear this user's data and save that
                // it is uninstalled.
                if (DEBUG_REMOVE) Slog.d(TAG, "Deleting system app");
                if (!clearPackageStateForUserLIF(ps, user.getIdentifier(), outInfo)) {
                    return false;
                }
                scheduleWritePackageRestrictionsLocked(user);
                return true;
            }
        }

        // If we are deleting a composite package for all users, keep track
        // of result for each child.
        if (ps.childPackageNames != null && outInfo != null) {
            synchronized (mPackages) {
                final int childCount = ps.childPackageNames.size();
                outInfo.removedChildPackages = new ArrayMap<>(childCount);
                for (int i = 0; i < childCount; i++) {
                    String childPackageName = ps.childPackageNames.get(i);
                    PackageRemovedInfo childInfo = new PackageRemovedInfo();
                    childInfo.removedPackage = childPackageName;
                    outInfo.removedChildPackages.put(childPackageName, childInfo);
                    PackageSetting childPs = mSettings.peekPackageLPr(childPackageName);
                    if (childPs != null) {
                        childInfo.origUsers = childPs.queryInstalledUsers(allUserHandles, true);
                    }
                }
            }
        }

        boolean ret = false;
        if (isSystemApp(ps)) {
            if (DEBUG_REMOVE) Slog.d(TAG, "Removing system package: " + ps.name);
            // When an updated system application is deleted we delete the existing resources
            // as well and fall back to existing code in system partition
            ret = deleteSystemPackageLIF(ps.pkg, ps, allUserHandles, flags, outInfo, writeSettings);
        } else {
            if (DEBUG_REMOVE) Slog.d(TAG, "Removing non-system package: " + ps.name);
            ret = deleteInstalledPackageLIF(ps, deleteCodeAndResources, flags, allUserHandles,
                    outInfo, writeSettings, replacingPackage);
        }

        // Take a note whether we deleted the package for all users
        if (outInfo != null) {
            outInfo.removedForAllUsers = mPackages.get(ps.name) == null;
            if (outInfo.removedChildPackages != null) {
                synchronized (mPackages) {
                    final int childCount = outInfo.removedChildPackages.size();
                    for (int i = 0; i < childCount; i++) {
                        PackageRemovedInfo childInfo = outInfo.removedChildPackages.valueAt(i);
                        if (childInfo != null) {
                            childInfo.removedForAllUsers = mPackages.get(
                                    childInfo.removedPackage) == null;
                        }
                    }
                }
            }
            // If we uninstalled an update to a system app there may be some
            // child packages that appeared as they are declared in the system
            // app but were not declared in the update.
            if (isSystemApp(ps)) {
                synchronized (mPackages) {
                    PackageSetting updatedPs = mSettings.peekPackageLPr(ps.name);
                    final int childCount = (updatedPs.childPackageNames != null)
                            ? updatedPs.childPackageNames.size() : 0;
                    for (int i = 0; i < childCount; i++) {
                        String childPackageName = updatedPs.childPackageNames.get(i);
                        if (outInfo.removedChildPackages == null
                                || outInfo.removedChildPackages.indexOfKey(childPackageName) < 0) {
                            PackageSetting childPs = mSettings.peekPackageLPr(childPackageName);
                            if (childPs == null) {
                                continue;
                            }
                            PackageInstalledInfo installRes = new PackageInstalledInfo();
                            installRes.name = childPackageName;
                            installRes.newUsers = childPs.queryInstalledUsers(allUserHandles, true);
                            installRes.pkg = mPackages.get(childPackageName);
                            installRes.uid = childPs.pkg.applicationInfo.uid;
                            if (outInfo.appearedChildPackages == null) {
                                outInfo.appearedChildPackages = new ArrayMap<>();
                            }
                            outInfo.appearedChildPackages.put(childPackageName, installRes);
                        }
                    }
                }
            }
        }

        return ret;
    }

    private void markPackageUninstalledForUserLPw(PackageSetting ps, UserHandle user) {
        final int[] userIds = (user == null || user.getIdentifier() == UserHandle.USER_ALL)
                ? sUserManager.getUserIds() : new int[] {user.getIdentifier()};
        for (int nextUserId : userIds) {
            if (DEBUG_REMOVE) {
                Slog.d(TAG, "Marking package:" + ps.name + " uninstalled for user:" + nextUserId);
            }
            ps.setUserState(nextUserId, 0, COMPONENT_ENABLED_STATE_DEFAULT,
                    false /*installed*/, true /*stopped*/, true /*notLaunched*/,
                    false /*hidden*/, false /*suspended*/, null, null, null,
                    false /*blockUninstall*/,
                    ps.readUserState(nextUserId).domainVerificationStatus, 0,
                    null, null);
        }
    }

    private boolean clearPackageStateForUserLIF(PackageSetting ps, int userId,
            PackageRemovedInfo outInfo) {
        final PackageParser.Package pkg;
        synchronized (mPackages) {
            pkg = mPackages.get(ps.name);
        }

        final int[] userIds = (userId == UserHandle.USER_ALL) ? sUserManager.getUserIds()
                : new int[] {userId};
        for (int nextUserId : userIds) {
            if (DEBUG_REMOVE) {
                Slog.d(TAG, "Updating package:" + ps.name + " install state for user:"
                        + nextUserId);
            }

            destroyAppDataLIF(pkg, userId,
                    StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE);
            destroyAppProfilesLIF(pkg, userId);
            removeKeystoreDataIfNeeded(nextUserId, ps.appId);
            schedulePackageCleaning(ps.name, nextUserId, false);
            synchronized (mPackages) {
                if (clearPackagePreferredActivitiesLPw(ps.name, nextUserId)) {
                    scheduleWritePackageRestrictionsLocked(nextUserId);
                }
                resetUserChangesToRuntimePermissionsAndFlagsLPw(ps, nextUserId);
            }
        }

        if (outInfo != null) {
            outInfo.removedPackage = ps.name;
            outInfo.removedAppId = ps.appId;
            outInfo.removedUsers = userIds;
        }

        return true;
    }

    private final class ClearStorageConnection implements ServiceConnection {
        IMediaContainerService mContainerService;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                mContainerService = IMediaContainerService.Stub.asInterface(service);
                notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private void clearExternalStorageDataSync(String packageName, int userId, boolean allData) {
        if (DEFAULT_CONTAINER_PACKAGE.equals(packageName)) return;

        final boolean mounted;
        if (Environment.isExternalStorageEmulated()) {
            mounted = true;
        } else {
            final String status = Environment.getExternalStorageState();

            mounted = status.equals(Environment.MEDIA_MOUNTED)
                    || status.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
        }

        if (!mounted) {
            return;
        }

        final Intent containerIntent = new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT);
        int[] users;
        if (userId == UserHandle.USER_ALL) {
            users = sUserManager.getUserIds();
        } else {
            users = new int[] { userId };
        }
        final ClearStorageConnection conn = new ClearStorageConnection();
        if (mContext.bindServiceAsUser(
                containerIntent, conn, Context.BIND_AUTO_CREATE, UserHandle.SYSTEM)) {
            try {
                for (int curUser : users) {
                    long timeout = SystemClock.uptimeMillis() + 5000;
                    synchronized (conn) {
                        long now;
                        while (conn.mContainerService == null &&
                                (now = SystemClock.uptimeMillis()) < timeout) {
                            try {
                                conn.wait(timeout - now);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                    if (conn.mContainerService == null) {
                        return;
                    }

                    final UserEnvironment userEnv = new UserEnvironment(curUser);
                    clearDirectory(conn.mContainerService,
                            userEnv.buildExternalStorageAppCacheDirs(packageName));
                    if (allData) {
                        clearDirectory(conn.mContainerService,
                                userEnv.buildExternalStorageAppDataDirs(packageName));
                        clearDirectory(conn.mContainerService,
                                userEnv.buildExternalStorageAppMediaDirs(packageName));
                    }
                }
            } finally {
                mContext.unbindService(conn);
            }
        }
    }

    @Override
    public void clearApplicationProfileData(String packageName) {
        enforceSystemOrRoot("Only the system can clear all profile data");

        final PackageParser.Package pkg;
        synchronized (mPackages) {
            pkg = mPackages.get(packageName);
        }

        try (PackageFreezer freezer = freezePackage(packageName, "clearApplicationProfileData")) {
            synchronized (mInstallLock) {
                clearAppProfilesLIF(pkg, UserHandle.USER_ALL);
                destroyAppReferenceProfileLeafLIF(pkg, UserHandle.USER_ALL,
                        true /* removeBaseMarker */);
            }
        }
    }

    @Override
    public void clearApplicationUserData(final String packageName,
            final IPackageDataObserver observer, final int userId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CLEAR_APP_USER_DATA, null);

        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                true /* requireFullPermission */, false /* checkShell */, "clear application data");

        if (mProtectedPackages.isPackageDataProtected(userId, packageName)) {
            throw new SecurityException("Cannot clear data for a protected package: "
                    + packageName);
        }
        // Queue up an async operation since the package deletion may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                final boolean succeeded;
                try (PackageFreezer freezer = freezePackage(packageName,
                        "clearApplicationUserData")) {
                    synchronized (mInstallLock) {
                        succeeded = clearApplicationUserDataLIF(packageName, userId);
                    }
                    clearExternalStorageDataSync(packageName, userId, true);
                }
                if (succeeded) {
                    // invoke DeviceStorageMonitor's update method to clear any notifications
                    DeviceStorageMonitorInternal dsm = LocalServices
                            .getService(DeviceStorageMonitorInternal.class);
                    if (dsm != null) {
                        dsm.checkMemory();
                    }
                }
                if(observer != null) {
                    try {
                        observer.onRemoveCompleted(packageName, succeeded);
                    } catch (RemoteException e) {
                        Log.i(TAG, "Observer no longer exists.");
                    }
                } //end if observer
            } //end run
        });
    }

    private boolean clearApplicationUserDataLIF(String packageName, int userId) {
        if (packageName == null) {
            Slog.w(TAG, "Attempt to delete null packageName.");
            return false;
        }

        // Try finding details about the requested package
        PackageParser.Package pkg;
        synchronized (mPackages) {
            pkg = mPackages.get(packageName);
            if (pkg == null) {
                final PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps != null) {
                    pkg = ps.pkg;
                }
            }

            if (pkg == null) {
                Slog.w(TAG, "Package named '" + packageName + "' doesn't exist.");
                return false;
            }

            PackageSetting ps = (PackageSetting) pkg.mExtras;
            resetUserChangesToRuntimePermissionsAndFlagsLPw(ps, userId);
        }

        clearAppDataLIF(pkg, userId,
                StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE);

        final int appId = UserHandle.getAppId(pkg.applicationInfo.uid);
        removeKeystoreDataIfNeeded(userId, appId);

        UserManagerInternal umInternal = getUserManagerInternal();
        final int flags;
        if (umInternal.isUserUnlockingOrUnlocked(userId)) {
            flags = StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE;
        } else if (umInternal.isUserRunning(userId)) {
            flags = StorageManager.FLAG_STORAGE_DE;
        } else {
            flags = 0;
        }
        prepareAppDataContentsLIF(pkg, userId, flags);

        return true;
    }

    /**
     * Reverts user permission state changes (permissions and flags) in
     * all packages for a given user.
     *
     * @param userId The device user for which to do a reset.
     */
    private void resetUserChangesToRuntimePermissionsAndFlagsLPw(int userId) {
        final int packageCount = mPackages.size();
        for (int i = 0; i < packageCount; i++) {
            PackageParser.Package pkg = mPackages.valueAt(i);
            PackageSetting ps = (PackageSetting) pkg.mExtras;
            resetUserChangesToRuntimePermissionsAndFlagsLPw(ps, userId);
        }
    }

    private void resetNetworkPolicies(int userId) {
        LocalServices.getService(NetworkPolicyManagerInternal.class).resetUserState(userId);
    }

    /**
     * Reverts user permission state changes (permissions and flags).
     *
     * @param ps The package for which to reset.
     * @param userId The device user for which to do a reset.
     */
    private void resetUserChangesToRuntimePermissionsAndFlagsLPw(
            final PackageSetting ps, final int userId) {
        if (ps.pkg == null) {
            return;
        }

        // These are flags that can change base on user actions.
        final int userSettableMask = FLAG_PERMISSION_USER_SET
                | FLAG_PERMISSION_USER_FIXED
                | FLAG_PERMISSION_REVOKE_ON_UPGRADE
                | FLAG_PERMISSION_REVIEW_REQUIRED;

        final int policyOrSystemFlags = FLAG_PERMISSION_SYSTEM_FIXED
                | FLAG_PERMISSION_POLICY_FIXED;

        boolean writeInstallPermissions = false;
        boolean writeRuntimePermissions = false;

        final int permissionCount = ps.pkg.requestedPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            String permission = ps.pkg.requestedPermissions.get(i);

            BasePermission bp = mSettings.mPermissions.get(permission);
            if (bp == null) {
                continue;
            }

            // If shared user we just reset the state to which only this app contributed.
            if (ps.sharedUser != null) {
                boolean used = false;
                final int packageCount = ps.sharedUser.packages.size();
                for (int j = 0; j < packageCount; j++) {
                    PackageSetting pkg = ps.sharedUser.packages.valueAt(j);
                    if (pkg.pkg != null && !pkg.pkg.packageName.equals(ps.pkg.packageName)
                            && pkg.pkg.requestedPermissions.contains(permission)) {
                        used = true;
                        break;
                    }
                }
                if (used) {
                    continue;
                }
            }

            PermissionsState permissionsState = ps.getPermissionsState();

            final int oldFlags = permissionsState.getPermissionFlags(bp.name, userId);

            // Always clear the user settable flags.
            final boolean hasInstallState = permissionsState.getInstallPermissionState(
                    bp.name) != null;
            // If permission review is enabled and this is a legacy app, mark the
            // permission as requiring a review as this is the initial state.
            int flags = 0;
            if (Build.PERMISSIONS_REVIEW_REQUIRED
                    && ps.pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                flags |= FLAG_PERMISSION_REVIEW_REQUIRED;
            }
            if (permissionsState.updatePermissionFlags(bp, userId, userSettableMask, flags)) {
                if (hasInstallState) {
                    writeInstallPermissions = true;
                } else {
                    writeRuntimePermissions = true;
                }
            }

            // Below is only runtime permission handling.
            if (!bp.isRuntime()) {
                continue;
            }

            // Never clobber system or policy.
            if ((oldFlags & policyOrSystemFlags) != 0) {
                continue;
            }

            // If this permission was granted by default, make sure it is.
            if ((oldFlags & FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0) {
                if (permissionsState.grantRuntimePermission(bp, userId)
                        != PERMISSION_OPERATION_FAILURE) {
                    writeRuntimePermissions = true;
                }
            // If permission review is enabled the permissions for a legacy apps
            // are represented as constantly granted runtime ones, so don't revoke.
            } else if ((flags & FLAG_PERMISSION_REVIEW_REQUIRED) == 0) {
                // Otherwise, reset the permission.
                final int revokeResult = permissionsState.revokeRuntimePermission(bp, userId);
                switch (revokeResult) {
                    case PERMISSION_OPERATION_SUCCESS:
                    case PERMISSION_OPERATION_SUCCESS_GIDS_CHANGED: {
                        writeRuntimePermissions = true;
                        final int appId = ps.appId;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                killUid(appId, userId, KILL_APP_REASON_PERMISSIONS_REVOKED);
                            }
                        });
                    } break;
                }
            }
        }

        // Synchronously write as we are taking permissions away.
        if (writeRuntimePermissions) {
            mSettings.writeRuntimePermissionsForUserLPr(userId, true);
        }

        // Synchronously write as we are taking permissions away.
        if (writeInstallPermissions) {
            mSettings.writeLPr();
        }
    }

    /**
     * Remove entries from the keystore daemon. Will only remove it if the
     * {@code appId} is valid.
     */
    private static void removeKeystoreDataIfNeeded(int userId, int appId) {
        if (appId < 0) {
            return;
        }

        final KeyStore keyStore = KeyStore.getInstance();
        if (keyStore != null) {
            if (userId == UserHandle.USER_ALL) {
                for (final int individual : sUserManager.getUserIds()) {
                    keyStore.clearUid(UserHandle.getUid(individual, appId));
                }
            } else {
                keyStore.clearUid(UserHandle.getUid(userId, appId));
            }
        } else {
            Slog.w(TAG, "Could not contact keystore to clear entries for app id " + appId);
        }
    }

    @Override
    public void deleteApplicationCacheFiles(final String packageName,
            final IPackageDataObserver observer) {
        final int userId = UserHandle.getCallingUserId();
        deleteApplicationCacheFilesAsUser(packageName, userId, observer);
    }

    @Override
    public void deleteApplicationCacheFilesAsUser(final String packageName, final int userId,
            final IPackageDataObserver observer) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DELETE_CACHE_FILES, null);
        enforceCrossUserPermission(Binder.getCallingUid(), userId,
                /* requireFullPermission= */ true, /* checkShell= */ false,
                "delete application cache files");

        final PackageParser.Package pkg;
        synchronized (mPackages) {
            pkg = mPackages.get(packageName);
        }

        // Queue up an async operation since the package deletion may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                synchronized (mInstallLock) {
                    final int flags = StorageManager.FLAG_STORAGE_DE
                            | StorageManager.FLAG_STORAGE_CE;
                    // We're only clearing cache files, so we don't care if the
                    // app is unfrozen and still able to run
                    clearAppDataLIF(pkg, userId, flags | Installer.FLAG_CLEAR_CACHE_ONLY);
                    clearAppDataLIF(pkg, userId, flags | Installer.FLAG_CLEAR_CODE_CACHE_ONLY);
                }
                clearExternalStorageDataSync(packageName, userId, false);
                if (observer != null) {
                    try {
                        observer.onRemoveCompleted(packageName, true);
                    } catch (RemoteException e) {
                        Log.i(TAG, "Observer no longer exists.");
                    }
                }
            }
        });
    }

    @Override
    public void getPackageSizeInfo(final String packageName, int userHandle,
            final IPackageStatsObserver observer) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.GET_PACKAGE_SIZE, null);
        if (packageName == null) {
            throw new IllegalArgumentException("Attempt to get size of null packageName");
        }

        PackageStats stats = new PackageStats(packageName, userHandle);

        /*
         * Queue up an async operation since the package measurement may take a
         * little while.
         */
        Message msg = mHandler.obtainMessage(INIT_COPY);
        msg.obj = new MeasureParams(stats, observer);
        mHandler.sendMessage(msg);
    }

    private boolean getPackageSizeInfoLI(String packageName, int userId, PackageStats stats) {
        final PackageSetting ps;
        synchronized (mPackages) {
            ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                Slog.w(TAG, "Failed to find settings for " + packageName);
                return false;
            }
        }
        try {
            mInstaller.getAppSize(ps.volumeUuid, packageName, userId,
                    StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE,
                    ps.getCeDataInode(userId), ps.codePathString, stats);
        } catch (InstallerException e) {
            Slog.w(TAG, String.valueOf(e));
            return false;
        }

        // For now, ignore code size of packages on system partition
        if (isSystemApp(ps) && !isUpdatedSystemApp(ps)) {
            stats.codeSize = 0;
        }

        return true;
    }

    private int getUidTargetSdkVersionLockedLPr(int uid) {
        Object obj = mSettings.getUserIdLPr(uid);
        if (obj instanceof SharedUserSetting) {
            final SharedUserSetting sus = (SharedUserSetting) obj;
            int vers = Build.VERSION_CODES.CUR_DEVELOPMENT;
            final Iterator<PackageSetting> it = sus.packages.iterator();
            while (it.hasNext()) {
                final PackageSetting ps = it.next();
                if (ps.pkg != null) {
                    int v = ps.pkg.applicationInfo.targetSdkVersion;
                    if (v < vers) vers = v;
                }
            }
            return vers;
        } else if (obj instanceof PackageSetting) {
            final PackageSetting ps = (PackageSetting) obj;
            if (ps.pkg != null) {
                return ps.pkg.applicationInfo.targetSdkVersion;
            }
        }
        return Build.VERSION_CODES.CUR_DEVELOPMENT;
    }

    @Override
    public void addPreferredActivity(IntentFilter filter, int match,
            ComponentName[] set, ComponentName activity, int userId) {
        addPreferredActivityInternal(filter, match, set, activity, true, userId,
                "Adding preferred");
    }

    private void addPreferredActivityInternal(IntentFilter filter, int match,
            ComponentName[] set, ComponentName activity, boolean always, int userId,
            String opname) {
        // writer
        int callingUid = Binder.getCallingUid();
        enforceCrossUserPermission(callingUid, userId,
                true /* requireFullPermission */, false /* checkShell */, "add preferred activity");
        if (filter.countActions() == 0) {
            Slog.w(TAG, "Cannot set a preferred activity with no filter actions");
            return;
        }
        synchronized (mPackages) {
            if (mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.SET_PREFERRED_APPLICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (getUidTargetSdkVersionLockedLPr(callingUid)
                        < Build.VERSION_CODES.FROYO) {
                    Slog.w(TAG, "Ignoring addPreferredActivity() from uid "
                            + callingUid);
                    return;
                }
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
            }

            PreferredIntentResolver pir = mSettings.editPreferredActivitiesLPw(userId);
            Slog.i(TAG, opname + " activity " + activity.flattenToShortString() + " for user "
                    + userId + ":");
            filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
            pir.addFilter(new PreferredActivity(filter, match, set, activity, always));
            scheduleWritePackageRestrictionsLocked(userId);
            postPreferredActivityChangedBroadcast(userId);
        }
    }

    private void postPreferredActivityChangedBroadcast(int userId) {
        mHandler.post(() -> {
            final IActivityManager am = ActivityManagerNative.getDefault();
            if (am == null) {
                return;
            }

            final Intent intent = new Intent(Intent.ACTION_PREFERRED_ACTIVITY_CHANGED);
            intent.putExtra(Intent.EXTRA_USER_HANDLE, userId);
            try {
                am.broadcastIntent(null, intent, null, null,
                        0, null, null, null, android.app.AppOpsManager.OP_NONE,
                        null, false, false, userId);
            } catch (RemoteException e) {
            }
        });
    }

    @Override
    public void replacePreferredActivity(IntentFilter filter, int match,
            ComponentName[] set, ComponentName activity, int userId) {
        if (filter.countActions() != 1) {
            throw new IllegalArgumentException(
                    "replacePreferredActivity expects filter to have only 1 action.");
        }
        if (filter.countDataAuthorities() != 0
                || filter.countDataPaths() != 0
                || filter.countDataSchemes() > 1
                || filter.countDataTypes() != 0) {
            throw new IllegalArgumentException(
                    "replacePreferredActivity expects filter to have no data authorities, " +
                    "paths, or types; and at most one scheme.");
        }

        final int callingUid = Binder.getCallingUid();
        enforceCrossUserPermission(callingUid, userId,
                true /* requireFullPermission */, false /* checkShell */,
                "replace preferred activity");
        synchronized (mPackages) {
            if (mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.SET_PREFERRED_APPLICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (getUidTargetSdkVersionLockedLPr(callingUid)
                        < Build.VERSION_CODES.FROYO) {
                    Slog.w(TAG, "Ignoring replacePreferredActivity() from uid "
                            + Binder.getCallingUid());
                    return;
                }
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
            }

            PreferredIntentResolver pir = mSettings.mPreferredActivities.get(userId);
            if (pir != null) {
                // Get all of the existing entries that exactly match this filter.
                ArrayList<PreferredActivity> existing = pir.findFilters(filter);
                if (existing != null && existing.size() == 1) {
                    PreferredActivity cur = existing.get(0);
                    if (DEBUG_PREFERRED) {
                        Slog.i(TAG, "Checking replace of preferred:");
                        filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
                        if (!cur.mPref.mAlways) {
                            Slog.i(TAG, "  -- CUR; not mAlways!");
                        } else {
                            Slog.i(TAG, "  -- CUR: mMatch=" + cur.mPref.mMatch);
                            Slog.i(TAG, "  -- CUR: mSet="
                                    + Arrays.toString(cur.mPref.mSetComponents));
                            Slog.i(TAG, "  -- CUR: mComponent=" + cur.mPref.mShortComponent);
                            Slog.i(TAG, "  -- NEW: mMatch="
                                    + (match&IntentFilter.MATCH_CATEGORY_MASK));
                            Slog.i(TAG, "  -- CUR: mSet=" + Arrays.toString(set));
                            Slog.i(TAG, "  -- CUR: mComponent=" + activity.flattenToShortString());
                        }
                    }
                    if (cur.mPref.mAlways && cur.mPref.mComponent.equals(activity)
                            && cur.mPref.mMatch == (match&IntentFilter.MATCH_CATEGORY_MASK)
                            && cur.mPref.sameSet(set)) {
                        // Setting the preferred activity to what it happens to be already
                        if (DEBUG_PREFERRED) {
                            Slog.i(TAG, "Replacing with same preferred activity "
                                    + cur.mPref.mShortComponent + " for user "
                                    + userId + ":");
                            filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
                        }
                        return;
                    }
                }

                if (existing != null) {
                    if (DEBUG_PREFERRED) {
                        Slog.i(TAG, existing.size() + " existing preferred matches for:");
                        filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
                    }
                    for (int i = 0; i < existing.size(); i++) {
                        PreferredActivity pa = existing.get(i);
                        if (DEBUG_PREFERRED) {
                            Slog.i(TAG, "Removing existing preferred activity "
                                    + pa.mPref.mComponent + ":");
                            pa.dump(new LogPrinter(Log.INFO, TAG), "  ");
                        }
                        pir.removeFilter(pa);
                    }
                }
            }
            addPreferredActivityInternal(filter, match, set, activity, true, userId,
                    "Replacing preferred");
        }
    }

    @Override
    public void clearPackagePreferredActivities(String packageName) {
        final int uid = Binder.getCallingUid();
        // writer
        synchronized (mPackages) {
            PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null || pkg.applicationInfo.uid != uid) {
                if (mContext.checkCallingOrSelfPermission(
                        android.Manifest.permission.SET_PREFERRED_APPLICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (getUidTargetSdkVersionLockedLPr(Binder.getCallingUid())
                            < Build.VERSION_CODES.FROYO) {
                        Slog.w(TAG, "Ignoring clearPackagePreferredActivities() from uid "
                                + Binder.getCallingUid());
                        return;
                    }
                    mContext.enforceCallingOrSelfPermission(
                            android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
                }
            }

            int user = UserHandle.getCallingUserId();
            if (clearPackagePreferredActivitiesLPw(packageName, user)) {
                scheduleWritePackageRestrictionsLocked(user);
            }
        }
    }

    /** This method takes a specific user id as well as UserHandle.USER_ALL. */
    boolean clearPackagePreferredActivitiesLPw(String packageName, int userId) {
        ArrayList<PreferredActivity> removed = null;
        boolean changed = false;
        for (int i=0; i<mSettings.mPreferredActivities.size(); i++) {
            final int thisUserId = mSettings.mPreferredActivities.keyAt(i);
            PreferredIntentResolver pir = mSettings.mPreferredActivities.valueAt(i);
            if (userId != UserHandle.USER_ALL && userId != thisUserId) {
                continue;
            }
            Iterator<PreferredActivity> it = pir.filterIterator();
            while (it.hasNext()) {
                PreferredActivity pa = it.next();
                // Mark entry for removal only if it matches the package name
                // and the entry is of type "always".
                if (packageName == null ||
                        (pa.mPref.mComponent.getPackageName().equals(packageName)
                                && pa.mPref.mAlways)) {
                    if (removed == null) {
                        removed = new ArrayList<PreferredActivity>();
                    }
                    removed.add(pa);
                }
            }
            if (removed != null) {
                for (int j=0; j<removed.size(); j++) {
                    PreferredActivity pa = removed.get(j);
                    pir.removeFilter(pa);
                }
                changed = true;
            }
        }
        if (changed) {
            postPreferredActivityChangedBroadcast(userId);
        }
        return changed;
    }

    /** This method takes a specific user id as well as UserHandle.USER_ALL. */
    private void clearIntentFilterVerificationsLPw(int userId) {
        final int packageCount = mPackages.size();
        for (int i = 0; i < packageCount; i++) {
            PackageParser.Package pkg = mPackages.valueAt(i);
            clearIntentFilterVerificationsLPw(pkg.packageName, userId);
        }
    }

    /** This method takes a specific user id as well as UserHandle.USER_ALL. */
    void clearIntentFilterVerificationsLPw(String packageName, int userId) {
        if (userId == UserHandle.USER_ALL) {
            if (mSettings.removeIntentFilterVerificationLPw(packageName,
                    sUserManager.getUserIds())) {
                for (int oneUserId : sUserManager.getUserIds()) {
                    scheduleWritePackageRestrictionsLocked(oneUserId);
                }
            }
        } else {
            if (mSettings.removeIntentFilterVerificationLPw(packageName, userId)) {
                scheduleWritePackageRestrictionsLocked(userId);
            }
        }
    }

    void clearDefaultBrowserIfNeeded(String packageName) {
        for (int oneUserId : sUserManager.getUserIds()) {
            String defaultBrowserPackageName = getDefaultBrowserPackageName(oneUserId);
            if (TextUtils.isEmpty(defaultBrowserPackageName)) continue;
            if (packageName.equals(defaultBrowserPackageName)) {
                setDefaultBrowserPackageName(null, oneUserId);
            }
        }
    }

    @Override
    public void resetApplicationPreferences(int userId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
        final long identity = Binder.clearCallingIdentity();
        // writer
        try {
            synchronized (mPackages) {
                clearPackagePreferredActivitiesLPw(null, userId);
                mSettings.applyDefaultPreferredAppsLPw(this, userId);
                // TODO: We have to reset the default SMS and Phone. This requires
                // significant refactoring to keep all default apps in the package
                // manager (cleaner but more work) or have the services provide
                // callbacks to the package manager to request a default app reset.
                applyFactoryDefaultBrowserLPw(userId);
                clearIntentFilterVerificationsLPw(userId);
                primeDomainVerificationsLPw(userId);
                resetUserChangesToRuntimePermissionsAndFlagsLPw(userId);
                scheduleWritePackageRestrictionsLocked(userId);
            }
            resetNetworkPolicies(userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getPreferredActivities(List<IntentFilter> outFilters,
            List<ComponentName> outActivities, String packageName) {

        int num = 0;
        final int userId = UserHandle.getCallingUserId();
        // reader
        synchronized (mPackages) {
            PreferredIntentResolver pir = mSettings.mPreferredActivities.get(userId);
            if (pir != null) {
                final Iterator<PreferredActivity> it = pir.filterIterator();
                while (it.hasNext()) {
                    final PreferredActivity pa = it.next();
                    if (packageName == null
                            || (pa.mPref.mComponent.getPackageName().equals(packageName)
                                    && pa.mPref.mAlways)) {
                        if (outFilters != null) {
                            outFilters.add(new IntentFilter(pa));
                        }
                        if (outActivities != null) {
                            outActivities.add(pa.mPref.mComponent);
                        }
                    }
                }
            }
        }

        return num;
    }

    @Override
    public void addPersistentPreferredActivity(IntentFilter filter, ComponentName activity,
            int userId) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "addPersistentPreferredActivity can only be run by the system");
        }
        if (filter.countActions() == 0) {
            Slog.w(TAG, "Cannot set a preferred activity with no filter actions");
            return;
        }
        synchronized (mPackages) {
            Slog.i(TAG, "Adding persistent preferred activity " + activity + " for user " + userId +
                    ":");
            filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
            mSettings.editPersistentPreferredActivitiesLPw(userId).addFilter(
                    new PersistentPreferredActivity(filter, activity));
            scheduleWritePackageRestrictionsLocked(userId);
            postPreferredActivityChangedBroadcast(userId);
        }
    }

    @Override
    public void clearPackagePersistentPreferredActivities(String packageName, int userId) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "clearPackagePersistentPreferredActivities can only be run by the system");
        }
        ArrayList<PersistentPreferredActivity> removed = null;
        boolean changed = false;
        synchronized (mPackages) {
            for (int i=0; i<mSettings.mPersistentPreferredActivities.size(); i++) {
                final int thisUserId = mSettings.mPersistentPreferredActivities.keyAt(i);
                PersistentPreferredIntentResolver ppir = mSettings.mPersistentPreferredActivities
                        .valueAt(i);
                if (userId != thisUserId) {
                    continue;
                }
                Iterator<PersistentPreferredActivity> it = ppir.filterIterator();
                while (it.hasNext()) {
                    PersistentPreferredActivity ppa = it.next();
                    // Mark entry for removal only if it matches the package name.
                    if (ppa.mComponent.getPackageName().equals(packageName)) {
                        if (removed == null) {
                            removed = new ArrayList<PersistentPreferredActivity>();
                        }
                        removed.add(ppa);
                    }
                }
                if (removed != null) {
                    for (int j=0; j<removed.size(); j++) {
                        PersistentPreferredActivity ppa = removed.get(j);
                        ppir.removeFilter(ppa);
                    }
                    changed = true;
                }
            }

            if (changed) {
                scheduleWritePackageRestrictionsLocked(userId);
                postPreferredActivityChangedBroadcast(userId);
            }
        }
    }

    /**
     * Common machinery for picking apart a restored XML blob and passing
     * it to a caller-supplied functor to be applied to the running system.
     */
    private void restoreFromXml(XmlPullParser parser, int userId,
            String expectedStartTag, BlobXmlRestorer functor)
            throws IOException, XmlPullParserException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
        }
        if (type != XmlPullParser.START_TAG) {
            // oops didn't find a start tag?!
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Didn't find start tag during restore");
            }
            return;
        }
Slog.v(TAG, ":: restoreFromXml() : got to tag " + parser.getName());
        // this is supposed to be TAG_PREFERRED_BACKUP
        if (!expectedStartTag.equals(parser.getName())) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Found unexpected tag " + parser.getName());
            }
            return;
        }

        // skip interfering stuff, then we're aligned with the backing implementation
        while ((type = parser.next()) == XmlPullParser.TEXT) { }
Slog.v(TAG, ":: stepped forward, applying functor at tag " + parser.getName());
        functor.apply(parser, userId);
    }

    private interface BlobXmlRestorer {
        public void apply(XmlPullParser parser, int userId) throws IOException, XmlPullParserException;
    }

    /**
     * Non-Binder method, support for the backup/restore mechanism: write the
     * full set of preferred activities in its canonical XML format.  Returns the
     * XML output as a byte array, or null if there is none.
     */
    @Override
    public byte[] getPreferredActivityBackup(int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call getPreferredActivityBackup()");
        }

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        try {
            final XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(dataStream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_PREFERRED_BACKUP);

            synchronized (mPackages) {
                mSettings.writePreferredActivitiesLPr(serializer, userId, true);
            }

            serializer.endTag(null, TAG_PREFERRED_BACKUP);
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Unable to write preferred activities for backup", e);
            }
            return null;
        }

        return dataStream.toByteArray();
    }

    @Override
    public void restorePreferredActivities(byte[] backup, int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call restorePreferredActivities()");
        }

        try {
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new ByteArrayInputStream(backup), StandardCharsets.UTF_8.name());
            restoreFromXml(parser, userId, TAG_PREFERRED_BACKUP,
                    new BlobXmlRestorer() {
                        @Override
                        public void apply(XmlPullParser parser, int userId)
                                throws XmlPullParserException, IOException {
                            synchronized (mPackages) {
                                mSettings.readPreferredActivitiesLPw(parser, userId);
                            }
                        }
                    } );
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Exception restoring preferred activities: " + e.getMessage());
            }
        }
    }

    /**
     * Non-Binder method, support for the backup/restore mechanism: write the
     * default browser (etc) settings in its canonical XML format.  Returns the default
     * browser XML representation as a byte array, or null if there is none.
     */
    @Override
    public byte[] getDefaultAppsBackup(int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call getDefaultAppsBackup()");
        }

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        try {
            final XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(dataStream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_DEFAULT_APPS);

            synchronized (mPackages) {
                mSettings.writeDefaultAppsLPr(serializer, userId);
            }

            serializer.endTag(null, TAG_DEFAULT_APPS);
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Unable to write default apps for backup", e);
            }
            return null;
        }

        return dataStream.toByteArray();
    }

    @Override
    public void restoreDefaultApps(byte[] backup, int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call restoreDefaultApps()");
        }

        try {
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new ByteArrayInputStream(backup), StandardCharsets.UTF_8.name());
            restoreFromXml(parser, userId, TAG_DEFAULT_APPS,
                    new BlobXmlRestorer() {
                        @Override
                        public void apply(XmlPullParser parser, int userId)
                                throws XmlPullParserException, IOException {
                            synchronized (mPackages) {
                                mSettings.readDefaultAppsLPw(parser, userId);
                            }
                        }
                    } );
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Exception restoring default apps: " + e.getMessage());
            }
        }
    }

    @Override
    public byte[] getIntentFilterVerificationBackup(int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call getIntentFilterVerificationBackup()");
        }

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        try {
            final XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(dataStream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_INTENT_FILTER_VERIFICATION);

            synchronized (mPackages) {
                mSettings.writeAllDomainVerificationsLPr(serializer, userId);
            }

            serializer.endTag(null, TAG_INTENT_FILTER_VERIFICATION);
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Unable to write default apps for backup", e);
            }
            return null;
        }

        return dataStream.toByteArray();
    }

    @Override
    public void restoreIntentFilterVerification(byte[] backup, int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call restorePreferredActivities()");
        }

        try {
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new ByteArrayInputStream(backup), StandardCharsets.UTF_8.name());
            restoreFromXml(parser, userId, TAG_INTENT_FILTER_VERIFICATION,
                    new BlobXmlRestorer() {
                        @Override
                        public void apply(XmlPullParser parser, int userId)
                                throws XmlPullParserException, IOException {
                            synchronized (mPackages) {
                                mSettings.readAllDomainVerificationsLPr(parser, userId);
                                mSettings.writeLPr();
                            }
                        }
                    } );
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Exception restoring preferred activities: " + e.getMessage());
            }
        }
    }

    @Override
    public byte[] getPermissionGrantBackup(int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call getPermissionGrantBackup()");
        }

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        try {
            final XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(dataStream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_PERMISSION_BACKUP);

            synchronized (mPackages) {
                serializeRuntimePermissionGrantsLPr(serializer, userId);
            }

            serializer.endTag(null, TAG_PERMISSION_BACKUP);
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Unable to write default apps for backup", e);
            }
            return null;
        }

        return dataStream.toByteArray();
    }

    @Override
    public void restorePermissionGrants(byte[] backup, int userId) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the system may call restorePermissionGrants()");
        }

        try {
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new ByteArrayInputStream(backup), StandardCharsets.UTF_8.name());
            restoreFromXml(parser, userId, TAG_PERMISSION_BACKUP,
                    new BlobXmlRestorer() {
                        @Override
                        public void apply(XmlPullParser parser, int userId)
                                throws XmlPullParserException, IOException {
                            synchronized (mPackages) {
                                processRestoredPermissionGrantsLPr(parser, userId);
                            }
                        }
                    } );
        } catch (Exception e) {
            if (DEBUG_BACKUP) {
                Slog.e(TAG, "Exception restoring preferred activities: " + e.getMessage());
            }
        }
    }

    private void serializeRuntimePermissionGrantsLPr(XmlSerializer serializer, final int userId)
            throws IOException {
        serializer.startTag(null, TAG_ALL_GRANTS);

        final int N = mSettings.mPackages.size();
        for (int i = 0; i < N; i++) {
            final PackageSetting ps = mSettings.mPackages.valueAt(i);
            boolean pkgGrantsKnown = false;

            PermissionsState packagePerms = ps.getPermissionsState();

            for (PermissionState state : packagePerms.getRuntimePermissionStates(userId)) {
                final int grantFlags = state.getFlags();
                // only look at grants that are not system/policy fixed
                if ((grantFlags & SYSTEM_RUNTIME_GRANT_MASK) == 0) {
                    final boolean isGranted = state.isGranted();
                    // And only back up the user-twiddled state bits
                    if (isGranted || (grantFlags & USER_RUNTIME_GRANT_MASK) != 0) {
                        final String packageName = mSettings.mPackages.keyAt(i);
                        if (!pkgGrantsKnown) {
                            serializer.startTag(null, TAG_GRANT);
                            serializer.attribute(null, ATTR_PACKAGE_NAME, packageName);
                            pkgGrantsKnown = true;
                        }

                        final boolean userSet =
                                (grantFlags & FLAG_PERMISSION_USER_SET) != 0;
                        final boolean userFixed =
                                (grantFlags & FLAG_PERMISSION_USER_FIXED) != 0;
                        final boolean revoke =
                                (grantFlags & FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0;

                        serializer.startTag(null, TAG_PERMISSION);
                        serializer.attribute(null, ATTR_PERMISSION_NAME, state.getName());
                        if (isGranted) {
                            serializer.attribute(null, ATTR_IS_GRANTED, "true");
                        }
                        if (userSet) {
                            serializer.attribute(null, ATTR_USER_SET, "true");
                        }
                        if (userFixed) {
                            serializer.attribute(null, ATTR_USER_FIXED, "true");
                        }
                        if (revoke) {
                            serializer.attribute(null, ATTR_REVOKE_ON_UPGRADE, "true");
                        }
                        serializer.endTag(null, TAG_PERMISSION);
                    }
                }
            }

            if (pkgGrantsKnown) {
                serializer.endTag(null, TAG_GRANT);
            }
        }

        serializer.endTag(null, TAG_ALL_GRANTS);
    }

    private void processRestoredPermissionGrantsLPr(XmlPullParser parser, int userId)
            throws XmlPullParserException, IOException {
        String pkgName = null;
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            final String tagName = parser.getName();
            if (tagName.equals(TAG_GRANT)) {
                pkgName = parser.getAttributeValue(null, ATTR_PACKAGE_NAME);
                if (DEBUG_BACKUP) {
                    Slog.v(TAG, "+++ Restoring grants for package " + pkgName);
                }
            } else if (tagName.equals(TAG_PERMISSION)) {

                final boolean isGranted = "true".equals(parser.getAttributeValue(null, ATTR_IS_GRANTED));
                final String permName = parser.getAttributeValue(null, ATTR_PERMISSION_NAME);

                int newFlagSet = 0;
                if ("true".equals(parser.getAttributeValue(null, ATTR_USER_SET))) {
                    newFlagSet |= FLAG_PERMISSION_USER_SET;
                }
                if ("true".equals(parser.getAttributeValue(null, ATTR_USER_FIXED))) {
                    newFlagSet |= FLAG_PERMISSION_USER_FIXED;
                }
                if ("true".equals(parser.getAttributeValue(null, ATTR_REVOKE_ON_UPGRADE))) {
                    newFlagSet |= FLAG_PERMISSION_REVOKE_ON_UPGRADE;
                }
                if (DEBUG_BACKUP) {
                    Slog.v(TAG, "  + Restoring grant: pkg=" + pkgName + " perm=" + permName
                            + " granted=" + isGranted + " bits=0x" + Integer.toHexString(newFlagSet));
                }
                final PackageSetting ps = mSettings.mPackages.get(pkgName);
                if (ps != null) {
                    // Already installed so we apply the grant immediately
                    if (DEBUG_BACKUP) {
                        Slog.v(TAG, "        + already installed; applying");
                    }
                    PermissionsState perms = ps.getPermissionsState();
                    BasePermission bp = mSettings.mPermissions.get(permName);
                    if (bp != null) {
                        if (isGranted) {
                            perms.grantRuntimePermission(bp, userId);
                        }
                        if (newFlagSet != 0) {
                            perms.updatePermissionFlags(bp, userId, USER_RUNTIME_GRANT_MASK, newFlagSet);
                        }
                    }
                } else {
                    // Need to wait for post-restore install to apply the grant
                    if (DEBUG_BACKUP) {
                        Slog.v(TAG, "        - not yet installed; saving for later");
                    }
                    mSettings.processRestoredPermissionGrantLPr(pkgName, permName,
                            isGranted, newFlagSet, userId);
                }
            } else {
                PackageManagerService.reportSettingsProblem(Log.WARN,
                        "Unknown element under <" + TAG_PERMISSION_BACKUP + ">: " + tagName);
                XmlUtils.skipCurrentTag(parser);
            }
        }

        scheduleWriteSettingsLocked();
        mSettings.writeRuntimePermissionsForUserLPr(userId, false);
    }

    @Override
    public void addCrossProfileIntentFilter(IntentFilter intentFilter, String ownerPackage,
            int sourceUserId, int targetUserId, int flags) {
        mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, null);
        int callingUid = Binder.getCallingUid();
        enforceOwnerRights(ownerPackage, callingUid);
        enforceShellRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, callingUid, sourceUserId);
        if (intentFilter.countActions() == 0) {
            Slog.w(TAG, "Cannot set a crossProfile intent filter with no filter actions");
            return;
        }
        synchronized (mPackages) {
            CrossProfileIntentFilter newFilter = new CrossProfileIntentFilter(intentFilter,
                    ownerPackage, targetUserId, flags);
            CrossProfileIntentResolver resolver =
                    mSettings.editCrossProfileIntentResolverLPw(sourceUserId);
            ArrayList<CrossProfileIntentFilter> existing = resolver.findFilters(intentFilter);
            // We have all those whose filter is equal. Now checking if the rest is equal as well.
            if (existing != null) {
                int size = existing.size();
                for (int i = 0; i < size; i++) {
                    if (newFilter.equalsIgnoreFilter(existing.get(i))) {
                        return;
                    }
                }
            }
            resolver.addFilter(newFilter);
            scheduleWritePackageRestrictionsLocked(sourceUserId);
        }
    }

    @Override
    public void clearCrossProfileIntentFilters(int sourceUserId, String ownerPackage) {
        mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, null);
        int callingUid = Binder.getCallingUid();
        enforceOwnerRights(ownerPackage, callingUid);
        enforceShellRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, callingUid, sourceUserId);
        synchronized (mPackages) {
            CrossProfileIntentResolver resolver =
                    mSettings.editCrossProfileIntentResolverLPw(sourceUserId);
            ArraySet<CrossProfileIntentFilter> set =
                    new ArraySet<CrossProfileIntentFilter>(resolver.filterSet());
            for (CrossProfileIntentFilter filter : set) {
                if (filter.getOwnerPackage().equals(ownerPackage)) {
                    resolver.removeFilter(filter);
                }
            }
            scheduleWritePackageRestrictionsLocked(sourceUserId);
        }
    }

    // Enforcing that callingUid is owning pkg on userId
    private void enforceOwnerRights(String pkg, int callingUid) {
        // The system owns everything.
        if (UserHandle.getAppId(callingUid) == Process.SYSTEM_UID) {
            return;
        }
        int callingUserId = UserHandle.getUserId(callingUid);
        PackageInfo pi = getPackageInfo(pkg, 0, callingUserId);
        if (pi == null) {
            throw new IllegalArgumentException("Unknown package " + pkg + " on user "
                    + callingUserId);
        }
        if (!UserHandle.isSameApp(pi.applicationInfo.uid, callingUid)) {
            throw new SecurityException("Calling uid " + callingUid
                    + " does not own package " + pkg);
        }
    }

    @Override
    public ComponentName getHomeActivities(List<ResolveInfo> allHomeCandidates) {
        return getHomeActivitiesAsUser(allHomeCandidates, UserHandle.getCallingUserId());
    }

    private Intent getHomeIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        return intent;
    }

    private IntentFilter getHomeFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        return filter;
    }

    ComponentName getHomeActivitiesAsUser(List<ResolveInfo> allHomeCandidates,
            int userId) {
        Intent intent  = getHomeIntent();
        List<ResolveInfo> list = queryIntentActivitiesInternal(intent, null,
                PackageManager.GET_META_DATA, userId);
        ResolveInfo preferred = findPreferredActivity(intent, null, 0, list, 0,
                true, false, false, userId);

        allHomeCandidates.clear();
        if (list != null) {
            for (ResolveInfo ri : list) {
                allHomeCandidates.add(ri);
            }
        }
        return (preferred == null || preferred.activityInfo == null)
                ? null
                : new ComponentName(preferred.activityInfo.packageName,
                        preferred.activityInfo.name);
    }

    @Override
    public void setHomeActivity(ComponentName comp, int userId) {
        ArrayList<ResolveInfo> homeActivities = new ArrayList<>();
        getHomeActivitiesAsUser(homeActivities, userId);

        boolean found = false;

        final int size = homeActivities.size();
        final ComponentName[] set = new ComponentName[size];
        for (int i = 0; i < size; i++) {
            final ResolveInfo candidate = homeActivities.get(i);
            final ActivityInfo info = candidate.activityInfo;
            final ComponentName activityName = new ComponentName(info.packageName, info.name);
            set[i] = activityName;
            if (!found && activityName.equals(comp)) {
                found = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Component " + comp + " cannot be home on user "
                    + userId);
        }
        replacePreferredActivity(getHomeFilter(), IntentFilter.MATCH_CATEGORY_EMPTY,
                set, comp, userId);
    }

    private @Nullable String getSetupWizardPackageName() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_SETUP_WIZARD);

        final List<ResolveInfo> matches = queryIntentActivitiesInternal(intent, null,
                MATCH_SYSTEM_ONLY | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE
                        | MATCH_DISABLED_COMPONENTS,
                UserHandle.myUserId());
        if (matches.size() == 1) {
            return matches.get(0).getComponentInfo().packageName;
        } else {
            Slog.e(TAG, "There should probably be exactly one setup wizard; found " + matches.size()
                    + ": matches=" + matches);
            return null;
        }
    }

    @Override
    public void setApplicationEnabledSetting(String appPackageName,
            int newState, int flags, int userId, String callingPackage) {
        if (!sUserManager.exists(userId)) return;
        if (callingPackage == null) {
            callingPackage = Integer.toString(Binder.getCallingUid());
        }
        setEnabledSetting(appPackageName, null, newState, flags, userId, callingPackage);
    }

    @Override
    public void setComponentEnabledSetting(ComponentName componentName,
            int newState, int flags, int userId) {
        if (!sUserManager.exists(userId)) return;
        // Don't allow to enable components marked for disabling at build-time
        if (mDisabledComponentsList.contains(componentName)) {
            Slog.d(TAG, "Ignoring attempt to set enabled state of disabled component "
                    + componentName.flattenToString());
            return;
        }
        setEnabledSetting(componentName.getPackageName(),
                componentName.getClassName(), newState, flags, userId, null);
    }

    private void setEnabledSetting(final String packageName, String className, int newState,
            final int flags, int userId, String callingPackage) {
        if (!(newState == COMPONENT_ENABLED_STATE_DEFAULT
              || newState == COMPONENT_ENABLED_STATE_ENABLED
              || newState == COMPONENT_ENABLED_STATE_DISABLED
              || newState == COMPONENT_ENABLED_STATE_DISABLED_USER
              || newState == COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)) {
            throw new IllegalArgumentException("Invalid new component state: "
                    + newState);
        }

        PackageSetting pkgSetting;
        final int uid = Binder.getCallingUid();
        final int permission;
        if (uid == Process.SYSTEM_UID) {
            permission = PackageManager.PERMISSION_GRANTED;
        } else {
            permission = mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        }
        enforceCrossUserPermission(uid, userId,
                false /* requireFullPermission */, true /* checkShell */, "set enabled");
        final boolean allowedByPermission = (permission == PackageManager.PERMISSION_GRANTED);
        boolean sendNow = false;
        boolean isApp = (className == null);
        String componentName = isApp ? packageName : className;
        int packageUid = -1;
        ArrayList<String> components;

        // writer
        synchronized (mPackages) {
            pkgSetting = mSettings.mPackages.get(packageName);
            if (pkgSetting == null) {
                if (className == null) {
                    throw new IllegalArgumentException("Unknown package: " + packageName);
                }
                throw new IllegalArgumentException(
                        "Unknown component: " + packageName + "/" + className);
            }
        }

        // Limit who can change which apps
        if (!UserHandle.isSameApp(uid, pkgSetting.appId)) {
            // Don't allow apps that don't have permission to modify other apps
            if (!allowedByPermission) {
                throw new SecurityException(
                        "Permission Denial: attempt to change component state from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + uid + ", package uid=" + pkgSetting.appId);
            }
            // Don't allow changing protected packages.
            if (mProtectedPackages.isPackageStateProtected(userId, packageName)) {
                throw new SecurityException("Cannot disable a protected package: " + packageName);
            }
        }

        synchronized (mPackages) {
            if (uid == Process.SHELL_UID) {
                // Shell can only change whole packages between ENABLED and DISABLED_USER states
                int oldState = pkgSetting.getEnabled(userId);
                if (className == null
                    &&
                    (oldState == COMPONENT_ENABLED_STATE_DISABLED_USER
                     || oldState == COMPONENT_ENABLED_STATE_DEFAULT
                     || oldState == COMPONENT_ENABLED_STATE_ENABLED)
                    &&
                    (newState == COMPONENT_ENABLED_STATE_DISABLED_USER
                     || newState == COMPONENT_ENABLED_STATE_DEFAULT
                     || newState == COMPONENT_ENABLED_STATE_ENABLED)) {
                    // ok
                } else {
                    throw new SecurityException(
                            "Shell cannot change component state for " + packageName + "/"
                            + className + " to " + newState);
                }
            }
            if (className == null) {
                // We're dealing with an application/package level state change
                if (pkgSetting.getEnabled(userId) == newState) {
                    // Nothing to do
                    return;
                }
                if (newState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    || newState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    // Don't care about who enables an app.
                    callingPackage = null;
                }
                pkgSetting.setEnabled(newState, userId, callingPackage);
                // pkgSetting.pkg.mSetEnabled = newState;
            } else {
                // We're dealing with a component level state change
                // First, verify that this is a valid class name.
                PackageParser.Package pkg = pkgSetting.pkg;
                if (pkg == null || !pkg.hasComponentClassName(className)) {
                    if (pkg != null &&
                            pkg.applicationInfo.targetSdkVersion >=
                                    Build.VERSION_CODES.JELLY_BEAN) {
                        throw new IllegalArgumentException("Component class " + className
                                + " does not exist in " + packageName);
                    } else {
                        Slog.w(TAG, "Failed setComponentEnabledSetting: component class "
                                + className + " does not exist in " + packageName);
                    }
                }
                switch (newState) {
                case COMPONENT_ENABLED_STATE_ENABLED:
                    if (!pkgSetting.enableComponentLPw(className, userId)) {
                        return;
                    }
                    break;
                case COMPONENT_ENABLED_STATE_DISABLED:
                    if (!pkgSetting.disableComponentLPw(className, userId)) {
                        return;
                    }
                    break;
                case COMPONENT_ENABLED_STATE_DEFAULT:
                    if (!pkgSetting.restoreComponentLPw(className, userId)) {
                        return;
                    }
                    break;
                default:
                    Slog.e(TAG, "Invalid new component state: " + newState);
                    return;
                }
            }
            scheduleWritePackageRestrictionsLocked(userId);
            components = mPendingBroadcasts.get(userId, packageName);
            final boolean newPackage = components == null;
            if (newPackage) {
                components = new ArrayList<String>();
            }
            if (!components.contains(componentName)) {
                components.add(componentName);
            }
            if ((flags&PackageManager.DONT_KILL_APP) == 0) {
                sendNow = true;
                // Purge entry from pending broadcast list if another one exists already
                // since we are sending one right away.
                mPendingBroadcasts.remove(userId, packageName);
            } else {
                if (newPackage) {
                    mPendingBroadcasts.put(userId, packageName, components);
                }
                if (!mHandler.hasMessages(SEND_PENDING_BROADCAST)) {
                    // Schedule a message
                    mHandler.sendEmptyMessageDelayed(SEND_PENDING_BROADCAST, BROADCAST_DELAY);
                }
            }
        }

        long callingId = Binder.clearCallingIdentity();
        try {
            if (sendNow) {
                packageUid = UserHandle.getUid(userId, pkgSetting.appId);
                sendPackageChangedBroadcast(packageName,
                        (flags&PackageManager.DONT_KILL_APP) != 0, components, packageUid);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public void flushPackageRestrictionsAsUser(int userId) {
        if (!sUserManager.exists(userId)) {
            return;
        }
        enforceCrossUserPermission(Binder.getCallingUid(), userId, false /* requireFullPermission*/,
                false /* checkShell */, "flushPackageRestrictions");
        synchronized (mPackages) {
            mSettings.writePackageRestrictionsLPr(userId);
            mDirtyUsers.remove(userId);
            if (mDirtyUsers.isEmpty()) {
                mHandler.removeMessages(WRITE_PACKAGE_RESTRICTIONS);
            }
        }
    }

    private void sendPackageChangedBroadcast(String packageName,
            boolean killFlag, ArrayList<String> componentNames, int packageUid) {
        if (DEBUG_INSTALL)
            Log.v(TAG, "Sending package changed: package=" + packageName + " components="
                    + componentNames);
        Bundle extras = new Bundle(4);
        extras.putString(Intent.EXTRA_CHANGED_COMPONENT_NAME, componentNames.get(0));
        String nameList[] = new String[componentNames.size()];
        componentNames.toArray(nameList);
        extras.putStringArray(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, nameList);
        extras.putBoolean(Intent.EXTRA_DONT_KILL_APP, killFlag);
        extras.putInt(Intent.EXTRA_UID, packageUid);
        // If this is not reporting a change of the overall package, then only send it
        // to registered receivers.  We don't want to launch a swath of apps for every
        // little component state change.
        final int flags = !componentNames.contains(packageName)
                ? Intent.FLAG_RECEIVER_REGISTERED_ONLY : 0;
        sendPackageBroadcast(Intent.ACTION_PACKAGE_CHANGED,  packageName, extras, flags, null, null,
                new int[] {UserHandle.getUserId(packageUid)});
    }

    @Override
    public void setPackageStoppedState(String packageName, boolean stopped, int userId) {
        if (!sUserManager.exists(userId)) return;
        final int uid = Binder.getCallingUid();
        final int permission = mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        final boolean allowedByPermission = (permission == PackageManager.PERMISSION_GRANTED);
        enforceCrossUserPermission(uid, userId,
                true /* requireFullPermission */, true /* checkShell */, "stop package");
        // writer
        synchronized (mPackages) {
            if (mSettings.setPackageStoppedStateLPw(this, packageName, stopped,
                    allowedByPermission, uid, userId)) {
                scheduleWritePackageRestrictionsLocked(userId);
            }
        }
    }

    @Override
    public String getInstallerPackageName(String packageName) {
        // reader
        synchronized (mPackages) {
            return mSettings.getInstallerPackageNameLPr(packageName);
        }
    }

    public boolean isOrphaned(String packageName) {
        // reader
        synchronized (mPackages) {
            return mSettings.isOrphaned(packageName);
        }
    }

    @Override
    public int getApplicationEnabledSetting(String packageName, int userId) {
        if (!sUserManager.exists(userId)) return COMPONENT_ENABLED_STATE_DISABLED;
        int uid = Binder.getCallingUid();
        enforceCrossUserPermission(uid, userId,
                false /* requireFullPermission */, false /* checkShell */, "get enabled");
        // reader
        synchronized (mPackages) {
            return mSettings.getApplicationEnabledSettingLPr(packageName, userId);
        }
    }

    @Override
    public int getComponentEnabledSetting(ComponentName componentName, int userId) {
        if (!sUserManager.exists(userId)) return COMPONENT_ENABLED_STATE_DISABLED;
        int uid = Binder.getCallingUid();
        enforceCrossUserPermission(uid, userId,
                false /* requireFullPermission */, false /* checkShell */, "get component enabled");
        // reader
        synchronized (mPackages) {
            return mSettings.getComponentEnabledSettingLPr(componentName, userId);
        }
    }

    @Override
    public void enterSafeMode() {
        enforceSystemOrRoot("Only the system can request entering safe mode");

        if (!mSystemReady) {
            mSafeMode = true;
        }
    }

    @Override
    public void systemReady() {
        mSystemReady = true;

        // Disable any carrier apps. We do this very early in boot to prevent the apps from being
        // disabled after already being started.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(mContext.getOpPackageName(), this,
                mContext.getContentResolver(), UserHandle.USER_SYSTEM);

        // Read the compatibilty setting when the system is ready.
        boolean compatibilityModeEnabled = android.provider.Settings.Global.getInt(
                mContext.getContentResolver(),
                android.provider.Settings.Global.COMPATIBILITY_MODE, 1) == 1;
        PackageParser.setCompatibilityModeEnabled(compatibilityModeEnabled);
        if (DEBUG_SETTINGS) {
            Log.d(TAG, "compatibility mode:" + compatibilityModeEnabled);
        }

        int[] grantPermissionsUserIds = EMPTY_INT_ARRAY;

        synchronized (mPackages) {
            if (!mIsAlarmBoot) {
                // Verify that all of the preferred activity components actually
                // exist.  It is possible for applications to be updated and at
                // that point remove a previously declared activity component that
                // had been set as a preferred activity.  We try to clean this up
                // the next time we encounter that preferred activity, but it is
                // possible for the user flow to never be able to return to that
                // situation so here we do a sanity check to make sure we haven't
                // left any junk around.
                ArrayList<PreferredActivity> removed = new ArrayList<PreferredActivity>();
                for (int i=0; i<mSettings.mPreferredActivities.size(); i++) {
                    PreferredIntentResolver pir = mSettings.mPreferredActivities.valueAt(i);
                    removed.clear();
                    for (PreferredActivity pa : pir.filterSet()) {
                        if (mActivities.mActivities.get(pa.mPref.mComponent) == null) {
                            removed.add(pa);
                        }
                    }
                    if (removed.size() > 0) {
                        for (int r=0; r<removed.size(); r++) {
                            PreferredActivity pa = removed.get(r);
                            Slog.w(TAG, "Removing dangling preferred activity: "
                                    + pa.mPref.mComponent);
                            pir.removeFilter(pa);
                        }
                        mSettings.writePackageRestrictionsLPr(
                                mSettings.mPreferredActivities.keyAt(i));
                    }
                }
            }

            for (int userId : UserManagerService.getInstance().getUserIds()) {
                if (!mSettings.areDefaultRuntimePermissionsGrantedLPr(userId)) {
                    grantPermissionsUserIds = ArrayUtils.appendInt(
                            grantPermissionsUserIds, userId);
                }
            }
        }
        sUserManager.systemReady();

        // If we upgraded grant all default permissions before kicking off.
        for (int userId : grantPermissionsUserIds) {
            mDefaultPermissionPolicy.grantDefaultPermissions(userId);
        }

        // If we did not grant default permissions, we preload from this the
        // default permission exceptions lazily to ensure we don't hit the
        // disk on a new user creation.
        if (grantPermissionsUserIds == EMPTY_INT_ARRAY) {
            mDefaultPermissionPolicy.scheduleReadDefaultPermissionExceptions();
        }

        // Kick off any messages waiting for system ready
        if (mPostSystemReadyMessages != null) {
            for (Message msg : mPostSystemReadyMessages) {
                msg.sendToTarget();
            }
            mPostSystemReadyMessages = null;
        }

        // Watch for external volumes that come and go over time
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        storage.registerListener(mStorageListener);

        mInstallerService.systemReady();
        mPackageDexOptimizer.systemReady();

        MountServiceInternal mountServiceInternal = LocalServices.getService(
                MountServiceInternal.class);
        mountServiceInternal.addExternalStoragePolicy(
                new MountServiceInternal.ExternalStorageMountPolicy() {
            @Override
            public int getMountMode(int uid, String packageName) {
                if (Process.isIsolated(uid)) {
                    return Zygote.MOUNT_EXTERNAL_NONE;
                }
                if (checkUidPermission(WRITE_MEDIA_STORAGE, uid) == PERMISSION_GRANTED) {
                    return Zygote.MOUNT_EXTERNAL_DEFAULT;
                }
                if (checkUidPermission(READ_EXTERNAL_STORAGE, uid) == PERMISSION_DENIED) {
                    return Zygote.MOUNT_EXTERNAL_DEFAULT;
                }
                if (checkUidPermission(WRITE_EXTERNAL_STORAGE, uid) == PERMISSION_DENIED) {
                    return Zygote.MOUNT_EXTERNAL_READ;
                }
                return Zygote.MOUNT_EXTERNAL_WRITE;
            }

            @Override
            public boolean hasExternalStorage(int uid, String packageName) {
                return true;
            }
        });

        // Now that we're mostly running, clean up stale users and apps
        reconcileUsers(StorageManager.UUID_PRIVATE_INTERNAL);
        reconcileApps(StorageManager.UUID_PRIVATE_INTERNAL);
    }

    @Override
    public boolean isSafeMode() {
        return mSafeMode;
    }

    @Override
    public boolean hasSystemUidErrors() {
        return mHasSystemUidErrors;
    }

    static String arrayToString(int[] array) {
        StringBuffer buf = new StringBuffer(128);
        buf.append('[');
        if (array != null) {
            for (int i=0; i<array.length; i++) {
                if (i > 0) buf.append(", ");
                buf.append(array[i]);
            }
        }
        buf.append(']');
        return buf.toString();
    }

    static class DumpState {
        public static final int DUMP_LIBS = 1 << 0;
        public static final int DUMP_FEATURES = 1 << 1;
        public static final int DUMP_ACTIVITY_RESOLVERS = 1 << 2;
        public static final int DUMP_SERVICE_RESOLVERS = 1 << 3;
        public static final int DUMP_RECEIVER_RESOLVERS = 1 << 4;
        public static final int DUMP_CONTENT_RESOLVERS = 1 << 5;
        public static final int DUMP_PERMISSIONS = 1 << 6;
        public static final int DUMP_PACKAGES = 1 << 7;
        public static final int DUMP_SHARED_USERS = 1 << 8;
        public static final int DUMP_MESSAGES = 1 << 9;
        public static final int DUMP_PROVIDERS = 1 << 10;
        public static final int DUMP_VERIFIERS = 1 << 11;
        public static final int DUMP_PREFERRED = 1 << 12;
        public static final int DUMP_PREFERRED_XML = 1 << 13;
        public static final int DUMP_KEYSETS = 1 << 14;
        public static final int DUMP_VERSION = 1 << 15;
        public static final int DUMP_INSTALLS = 1 << 16;
        public static final int DUMP_INTENT_FILTER_VERIFIERS = 1 << 17;
        public static final int DUMP_DOMAIN_PREFERRED = 1 << 18;
        public static final int DUMP_FROZEN = 1 << 19;
        public static final int DUMP_DEXOPT = 1 << 20;
        public static final int DUMP_COMPILER_STATS = 1 << 21;

        public static final int OPTION_SHOW_FILTERS = 1 << 0;

        private int mTypes;

        private int mOptions;

        private boolean mTitlePrinted;

        private SharedUserSetting mSharedUser;

        public boolean isDumping(int type) {
            if (mTypes == 0 && type != DUMP_PREFERRED_XML) {
                return true;
            }

            return (mTypes & type) != 0;
        }

        public void setDump(int type) {
            mTypes |= type;
        }

        public boolean isOptionEnabled(int option) {
            return (mOptions & option) != 0;
        }

        public void setOptionEnabled(int option) {
            mOptions |= option;
        }

        public boolean onTitlePrinted() {
            final boolean printed = mTitlePrinted;
            mTitlePrinted = true;
            return printed;
        }

        public boolean getTitlePrinted() {
            return mTitlePrinted;
        }

        public void setTitlePrinted(boolean enabled) {
            mTitlePrinted = enabled;
        }

        public SharedUserSetting getSharedUser() {
            return mSharedUser;
        }

        public void setSharedUser(SharedUserSetting user) {
            mSharedUser = user;
        }
    }

    @Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out,
            FileDescriptor err, String[] args, ResultReceiver resultReceiver) {
        (new PackageManagerShellCommand(this)).exec(
                this, in, out, err, args, resultReceiver);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ActivityManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " without permission "
                    + android.Manifest.permission.DUMP);
            return;
        }

        DumpState dumpState = new DumpState();
        boolean fullPreferred = false;
        boolean checkin = false;

        String packageName = null;
        ArraySet<String> permissionNames = null;

        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;

            if ("-a".equals(opt)) {
                // Right now we only know how to print all.
            } else if ("-h".equals(opt)) {
                pw.println("Package manager dump options:");
                pw.println("  [-h] [-f] [--checkin] [cmd] ...");
                pw.println("    --checkin: dump for a checkin");
                pw.println("    -f: print details of intent filters");
                pw.println("    -h: print this help");
                pw.println("  cmd may be one of:");
                pw.println("    l[ibraries]: list known shared libraries");
                pw.println("    f[eatures]: list device features");
                pw.println("    k[eysets]: print known keysets");
                pw.println("    r[esolvers] [activity|service|receiver|content]: dump intent resolvers");
                pw.println("    perm[issions]: dump permissions");
                pw.println("    permission [name ...]: dump declaration and use of given permission");
                pw.println("    pref[erred]: print preferred package settings");
                pw.println("    preferred-xml [--full]: print preferred package settings as xml");
                pw.println("    prov[iders]: dump content providers");
                pw.println("    p[ackages]: dump installed packages");
                pw.println("    s[hared-users]: dump shared user IDs");
                pw.println("    m[essages]: print collected runtime messages");
                pw.println("    v[erifiers]: print package verifier info");
                pw.println("    d[omain-preferred-apps]: print domains preferred apps");
                pw.println("    i[ntent-filter-verifiers]|ifv: print intent filter verifier info");
                pw.println("    version: print database version info");
                pw.println("    write: write current settings now");
                pw.println("    installs: details about install sessions");
                pw.println("    check-permission <permission> <package> [<user>]: does pkg hold perm?");
                pw.println("    dexopt: dump dexopt state");
                pw.println("    compiler-stats: dump compiler statistics");
                pw.println("    <package.name>: info about given package");
                return;
            } else if ("--checkin".equals(opt)) {
                checkin = true;
            } else if ("-f".equals(opt)) {
                dumpState.setOptionEnabled(DumpState.OPTION_SHOW_FILTERS);
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }

        // Is the caller requesting to dump a particular piece of data?
        if (opti < args.length) {
            String cmd = args[opti];
            opti++;
            // Is this a package name?
            if ("android".equals(cmd) || cmd.contains(".")) {
                packageName = cmd;
                // When dumping a single package, we always dump all of its
                // filter information since the amount of data will be reasonable.
                dumpState.setOptionEnabled(DumpState.OPTION_SHOW_FILTERS);
            } else if ("check-permission".equals(cmd)) {
                if (opti >= args.length) {
                    pw.println("Error: check-permission missing permission argument");
                    return;
                }
                String perm = args[opti];
                opti++;
                if (opti >= args.length) {
                    pw.println("Error: check-permission missing package argument");
                    return;
                }
                String pkg = args[opti];
                opti++;
                int user = UserHandle.getUserId(Binder.getCallingUid());
                if (opti < args.length) {
                    try {
                        user = Integer.parseInt(args[opti]);
                    } catch (NumberFormatException e) {
                        pw.println("Error: check-permission user argument is not a number: "
                                + args[opti]);
                        return;
                    }
                }
                pw.println(checkPermission(perm, pkg, user));
                return;
            } else if ("l".equals(cmd) || "libraries".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_LIBS);
            } else if ("f".equals(cmd) || "features".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_FEATURES);
            } else if ("r".equals(cmd) || "resolvers".equals(cmd)) {
                if (opti >= args.length) {
                    dumpState.setDump(DumpState.DUMP_ACTIVITY_RESOLVERS
                            | DumpState.DUMP_SERVICE_RESOLVERS
                            | DumpState.DUMP_RECEIVER_RESOLVERS
                            | DumpState.DUMP_CONTENT_RESOLVERS);
                } else {
                    while (opti < args.length) {
                        String name = args[opti];
                        if ("a".equals(name) || "activity".equals(name)) {
                            dumpState.setDump(DumpState.DUMP_ACTIVITY_RESOLVERS);
                        } else if ("s".equals(name) || "service".equals(name)) {
                            dumpState.setDump(DumpState.DUMP_SERVICE_RESOLVERS);
                        } else if ("r".equals(name) || "receiver".equals(name)) {
                            dumpState.setDump(DumpState.DUMP_RECEIVER_RESOLVERS);
                        } else if ("c".equals(name) || "content".equals(name)) {
                            dumpState.setDump(DumpState.DUMP_CONTENT_RESOLVERS);
                        } else {
                            pw.println("Error: unknown resolver table type: " + name);
                            return;
                        }
                        opti++;
                    }
                }
            } else if ("perm".equals(cmd) || "permissions".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PERMISSIONS);
            } else if ("permission".equals(cmd)) {
                if (opti >= args.length) {
                    pw.println("Error: permission requires permission name");
                    return;
                }
                permissionNames = new ArraySet<>();
                while (opti < args.length) {
                    permissionNames.add(args[opti]);
                    opti++;
                }
                dumpState.setDump(DumpState.DUMP_PERMISSIONS
                        | DumpState.DUMP_PACKAGES | DumpState.DUMP_SHARED_USERS);
            } else if ("pref".equals(cmd) || "preferred".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PREFERRED);
            } else if ("preferred-xml".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PREFERRED_XML);
                if (opti < args.length && "--full".equals(args[opti])) {
                    fullPreferred = true;
                    opti++;
                }
            } else if ("d".equals(cmd) || "domain-preferred-apps".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_DOMAIN_PREFERRED);
            } else if ("p".equals(cmd) || "packages".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PACKAGES);
            } else if ("s".equals(cmd) || "shared-users".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_SHARED_USERS);
            } else if ("prov".equals(cmd) || "providers".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PROVIDERS);
            } else if ("m".equals(cmd) || "messages".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_MESSAGES);
            } else if ("v".equals(cmd) || "verifiers".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_VERIFIERS);
            } else if ("i".equals(cmd) || "ifv".equals(cmd)
                    || "intent-filter-verifiers".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_INTENT_FILTER_VERIFIERS);
            } else if ("version".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_VERSION);
            } else if ("k".equals(cmd) || "keysets".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_KEYSETS);
            } else if ("installs".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_INSTALLS);
            } else if ("frozen".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_FROZEN);
            } else if ("dexopt".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_DEXOPT);
            } else if ("compiler-stats".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_COMPILER_STATS);
            } else if ("write".equals(cmd)) {
                synchronized (mPackages) {
                    mSettings.writeLPr();
                    pw.println("Settings written.");
                    return;
                }
            }
        }

        if (checkin) {
            pw.println("vers,1");
        }

        // reader
        synchronized (mPackages) {
            if (dumpState.isDumping(DumpState.DUMP_VERSION) && packageName == null) {
                if (!checkin) {
                    if (dumpState.onTitlePrinted())
                        pw.println();
                    pw.println("Database versions:");
                    mSettings.dumpVersionLPr(new IndentingPrintWriter(pw, "  "));
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_VERIFIERS) && packageName == null) {
                if (!checkin) {
                    if (dumpState.onTitlePrinted())
                        pw.println();
                    pw.println("Verifiers:");
                    pw.print("  Required: ");
                    pw.print(mRequiredVerifierPackage);
                    pw.print(" (uid=");
                    pw.print(getPackageUid(mRequiredVerifierPackage, MATCH_DEBUG_TRIAGED_MISSING,
                            UserHandle.USER_SYSTEM));
                    pw.println(")");
                } else if (mRequiredVerifierPackage != null) {
                    pw.print("vrfy,"); pw.print(mRequiredVerifierPackage);
                    pw.print(",");
                    pw.println(getPackageUid(mRequiredVerifierPackage, MATCH_DEBUG_TRIAGED_MISSING,
                            UserHandle.USER_SYSTEM));
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_INTENT_FILTER_VERIFIERS) &&
                    packageName == null) {
                if (mIntentFilterVerifierComponent != null) {
                    String verifierPackageName = mIntentFilterVerifierComponent.getPackageName();
                    if (!checkin) {
                        if (dumpState.onTitlePrinted())
                            pw.println();
                        pw.println("Intent Filter Verifier:");
                        pw.print("  Using: ");
                        pw.print(verifierPackageName);
                        pw.print(" (uid=");
                        pw.print(getPackageUid(verifierPackageName, MATCH_DEBUG_TRIAGED_MISSING,
                                UserHandle.USER_SYSTEM));
                        pw.println(")");
                    } else if (verifierPackageName != null) {
                        pw.print("ifv,"); pw.print(verifierPackageName);
                        pw.print(",");
                        pw.println(getPackageUid(verifierPackageName, MATCH_DEBUG_TRIAGED_MISSING,
                                UserHandle.USER_SYSTEM));
                    }
                } else {
                    pw.println();
                    pw.println("No Intent Filter Verifier available!");
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_LIBS) && packageName == null) {
                boolean printedHeader = false;
                final Iterator<String> it = mSharedLibraries.keySet().iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    SharedLibraryEntry ent = mSharedLibraries.get(name);
                    if (!checkin) {
                        if (!printedHeader) {
                            if (dumpState.onTitlePrinted())
                                pw.println();
                            pw.println("Libraries:");
                            printedHeader = true;
                        }
                        pw.print("  ");
                    } else {
                        pw.print("lib,");
                    }
                    pw.print(name);
                    if (!checkin) {
                        pw.print(" -> ");
                    }
                    if (ent.path != null) {
                        if (!checkin) {
                            pw.print("(jar) ");
                            pw.print(ent.path);
                        } else {
                            pw.print(",jar,");
                            pw.print(ent.path);
                        }
                    } else {
                        if (!checkin) {
                            pw.print("(apk) ");
                            pw.print(ent.apk);
                        } else {
                            pw.print(",apk,");
                            pw.print(ent.apk);
                        }
                    }
                    pw.println();
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_FEATURES) && packageName == null) {
                if (dumpState.onTitlePrinted())
                    pw.println();
                if (!checkin) {
                    pw.println("Features:");
                }

                for (FeatureInfo feat : mAvailableFeatures.values()) {
                    if (checkin) {
                        pw.print("feat,");
                        pw.print(feat.name);
                        pw.print(",");
                        pw.println(feat.version);
                    } else {
                        pw.print("  ");
                        pw.print(feat.name);
                        if (feat.version > 0) {
                            pw.print(" version=");
                            pw.print(feat.version);
                        }
                        pw.println();
                    }
                }
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_ACTIVITY_RESOLVERS)) {
                if (mActivities.dump(pw, dumpState.getTitlePrinted() ? "\nActivity Resolver Table:"
                        : "Activity Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS), true)) {
                    dumpState.setTitlePrinted(true);
                }
            }
            if (!checkin && dumpState.isDumping(DumpState.DUMP_RECEIVER_RESOLVERS)) {
                if (mReceivers.dump(pw, dumpState.getTitlePrinted() ? "\nReceiver Resolver Table:"
                        : "Receiver Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS), true)) {
                    dumpState.setTitlePrinted(true);
                }
            }
            if (!checkin && dumpState.isDumping(DumpState.DUMP_SERVICE_RESOLVERS)) {
                if (mServices.dump(pw, dumpState.getTitlePrinted() ? "\nService Resolver Table:"
                        : "Service Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS), true)) {
                    dumpState.setTitlePrinted(true);
                }
            }
            if (!checkin && dumpState.isDumping(DumpState.DUMP_CONTENT_RESOLVERS)) {
                if (mProviders.dump(pw, dumpState.getTitlePrinted() ? "\nProvider Resolver Table:"
                        : "Provider Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS), true)) {
                    dumpState.setTitlePrinted(true);
                }
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_PREFERRED)) {
                for (int i=0; i<mSettings.mPreferredActivities.size(); i++) {
                    PreferredIntentResolver pir = mSettings.mPreferredActivities.valueAt(i);
                    int user = mSettings.mPreferredActivities.keyAt(i);
                    if (pir.dump(pw,
                            dumpState.getTitlePrinted()
                                ? "\nPreferred Activities User " + user + ":"
                                : "Preferred Activities User " + user + ":", "  ",
                            packageName, true, false)) {
                        dumpState.setTitlePrinted(true);
                    }
                }
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_PREFERRED_XML)) {
                pw.flush();
                FileOutputStream fout = new FileOutputStream(fd);
                BufferedOutputStream str = new BufferedOutputStream(fout);
                XmlSerializer serializer = new FastXmlSerializer();
                try {
                    serializer.setOutput(str, StandardCharsets.UTF_8.name());
                    serializer.startDocument(null, true);
                    serializer.setFeature(
                            "http://xmlpull.org/v1/doc/features.html#indent-output", true);
                    mSettings.writePreferredActivitiesLPr(serializer, 0, fullPreferred);
                    serializer.endDocument();
                    serializer.flush();
                } catch (IllegalArgumentException e) {
                    pw.println("Failed writing: " + e);
                } catch (IllegalStateException e) {
                    pw.println("Failed writing: " + e);
                } catch (IOException e) {
                    pw.println("Failed writing: " + e);
                }
            }

            if (!checkin
                    && dumpState.isDumping(DumpState.DUMP_DOMAIN_PREFERRED)
                    && packageName == null) {
                pw.println();
                int count = mSettings.mPackages.size();
                if (count == 0) {
                    pw.println("No applications!");
                    pw.println();
                } else {
                    final String prefix = "  ";
                    Collection<PackageSetting> allPackageSettings = mSettings.mPackages.values();
                    if (allPackageSettings.size() == 0) {
                        pw.println("No domain preferred apps!");
                        pw.println();
                    } else {
                        pw.println("App verification status:");
                        pw.println();
                        count = 0;
                        for (PackageSetting ps : allPackageSettings) {
                            IntentFilterVerificationInfo ivi = ps.getIntentFilterVerificationInfo();
                            if (ivi == null || ivi.getPackageName() == null) continue;
                            pw.println(prefix + "Package: " + ivi.getPackageName());
                            pw.println(prefix + "Domains: " + ivi.getDomainsString());
                            pw.println(prefix + "Status:  " + ivi.getStatusString());
                            pw.println();
                            count++;
                        }
                        if (count == 0) {
                            pw.println(prefix + "No app verification established.");
                            pw.println();
                        }
                        for (int userId : sUserManager.getUserIds()) {
                            pw.println("App linkages for user " + userId + ":");
                            pw.println();
                            count = 0;
                            for (PackageSetting ps : allPackageSettings) {
                                final long status = ps.getDomainVerificationStatusForUser(userId);
                                if (status >> 32 == INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED) {
                                    continue;
                                }
                                pw.println(prefix + "Package: " + ps.name);
                                pw.println(prefix + "Domains: " + dumpDomainString(ps.name));
                                String statusStr = IntentFilterVerificationInfo.
                                        getStatusStringFromValue(status);
                                pw.println(prefix + "Status:  " + statusStr);
                                pw.println();
                                count++;
                            }
                            if (count == 0) {
                                pw.println(prefix + "No configured app linkages.");
                                pw.println();
                            }
                        }
                    }
                }
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_PERMISSIONS)) {
                mSettings.dumpPermissionsLPr(pw, packageName, permissionNames, dumpState);
                if (packageName == null && permissionNames == null) {
                    for (int iperm=0; iperm<mAppOpPermissionPackages.size(); iperm++) {
                        if (iperm == 0) {
                            if (dumpState.onTitlePrinted())
                                pw.println();
                            pw.println("AppOp Permissions:");
                        }
                        pw.print("  AppOp Permission ");
                        pw.print(mAppOpPermissionPackages.keyAt(iperm));
                        pw.println(":");
                        ArraySet<String> pkgs = mAppOpPermissionPackages.valueAt(iperm);
                        for (int ipkg=0; ipkg<pkgs.size(); ipkg++) {
                            pw.print("    "); pw.println(pkgs.valueAt(ipkg));
                        }
                    }
                }
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_PROVIDERS)) {
                boolean printedSomething = false;
                for (PackageParser.Provider p : mProviders.mProviders.values()) {
                    if (packageName != null && !packageName.equals(p.info.packageName)) {
                        continue;
                    }
                    if (!printedSomething) {
                        if (dumpState.onTitlePrinted())
                            pw.println();
                        pw.println("Registered ContentProviders:");
                        printedSomething = true;
                    }
                    pw.print("  "); p.printComponentShortName(pw); pw.println(":");
                    pw.print("    "); pw.println(p.toString());
                }
                printedSomething = false;
                for (Map.Entry<String, PackageParser.Provider> entry :
                        mProvidersByAuthority.entrySet()) {
                    PackageParser.Provider p = entry.getValue();
                    if (packageName != null && !packageName.equals(p.info.packageName)) {
                        continue;
                    }
                    if (!printedSomething) {
                        if (dumpState.onTitlePrinted())
                            pw.println();
                        pw.println("ContentProvider Authorities:");
                        printedSomething = true;
                    }
                    pw.print("  ["); pw.print(entry.getKey()); pw.println("]:");
                    pw.print("    "); pw.println(p.toString());
                    if (p.info != null && p.info.applicationInfo != null) {
                        final String appInfo = p.info.applicationInfo.toString();
                        pw.print("      applicationInfo="); pw.println(appInfo);
                    }
                }
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_KEYSETS)) {
                mSettings.mKeySetManagerService.dumpLPr(pw, packageName, dumpState);
            }

            if (dumpState.isDumping(DumpState.DUMP_PACKAGES)) {
                mSettings.dumpPackagesLPr(pw, packageName, permissionNames, dumpState, checkin);
            }

            if (dumpState.isDumping(DumpState.DUMP_SHARED_USERS)) {
                mSettings.dumpSharedUsersLPr(pw, packageName, permissionNames, dumpState, checkin);
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_PERMISSIONS) && packageName == null) {
                mSettings.dumpRestoredPermissionGrantsLPr(pw, dumpState);
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_INSTALLS) && packageName == null) {
                // XXX should handle packageName != null by dumping only install data that
                // the given package is involved with.
                if (dumpState.onTitlePrinted()) pw.println();
                mInstallerService.dump(new IndentingPrintWriter(pw, "  ", 120));
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_FROZEN) && packageName == null) {
                // XXX should handle packageName != null by dumping only install data that
                // the given package is involved with.
                if (dumpState.onTitlePrinted()) pw.println();

                final IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ", 120);
                ipw.println();
                ipw.println("Frozen packages:");
                ipw.increaseIndent();
                if (mFrozenPackages.size() == 0) {
                    ipw.println("(none)");
                } else {
                    for (int i = 0; i < mFrozenPackages.size(); i++) {
                        ipw.println(mFrozenPackages.valueAt(i));
                    }
                }
                ipw.decreaseIndent();
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_DEXOPT)) {
                if (dumpState.onTitlePrinted()) pw.println();
                dumpDexoptStateLPr(pw, packageName);
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_COMPILER_STATS)) {
                if (dumpState.onTitlePrinted()) pw.println();
                dumpCompilerStatsLPr(pw, packageName);
            }

            if (!checkin && dumpState.isDumping(DumpState.DUMP_MESSAGES) && packageName == null) {
                if (dumpState.onTitlePrinted()) pw.println();
                mSettings.dumpReadMessagesLPr(pw, dumpState);

                pw.println();
                pw.println("Package warning messages:");
                BufferedReader in = null;
                String line = null;
                try {
                    in = new BufferedReader(new FileReader(getSettingsProblemFile()));
                    while ((line = in.readLine()) != null) {
                        if (line.contains("ignored: updated version")) continue;
                        pw.println(line);
                    }
                } catch (IOException ignored) {
                } finally {
                    IoUtils.closeQuietly(in);
                }
            }

            if (checkin && dumpState.isDumping(DumpState.DUMP_MESSAGES)) {
                BufferedReader in = null;
                String line = null;
                try {
                    in = new BufferedReader(new FileReader(getSettingsProblemFile()));
                    while ((line = in.readLine()) != null) {
                        if (line.contains("ignored: updated version")) continue;
                        pw.print("msg,");
                        pw.println(line);
                    }
                } catch (IOException ignored) {
                } finally {
                    IoUtils.closeQuietly(in);
                }
            }
        }
    }

    private void dumpDexoptStateLPr(PrintWriter pw, String packageName) {
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ", 120);
        ipw.println();
        ipw.println("Dexopt state:");
        ipw.increaseIndent();
        Collection<PackageParser.Package> packages = null;
        if (packageName != null) {
            PackageParser.Package targetPackage = mPackages.get(packageName);
            if (targetPackage != null) {
                packages = Collections.singletonList(targetPackage);
            } else {
                ipw.println("Unable to find package: " + packageName);
                return;
            }
        } else {
            packages = mPackages.values();
        }

        for (PackageParser.Package pkg : packages) {
            ipw.println("[" + pkg.packageName + "]");
            ipw.increaseIndent();
            mPackageDexOptimizer.dumpDexoptState(ipw, pkg);
            ipw.decreaseIndent();
        }
    }

    private void dumpCompilerStatsLPr(PrintWriter pw, String packageName) {
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ", 120);
        ipw.println();
        ipw.println("Compiler stats:");
        ipw.increaseIndent();
        Collection<PackageParser.Package> packages = null;
        if (packageName != null) {
            PackageParser.Package targetPackage = mPackages.get(packageName);
            if (targetPackage != null) {
                packages = Collections.singletonList(targetPackage);
            } else {
                ipw.println("Unable to find package: " + packageName);
                return;
            }
        } else {
            packages = mPackages.values();
        }

        for (PackageParser.Package pkg : packages) {
            ipw.println("[" + pkg.packageName + "]");
            ipw.increaseIndent();

            CompilerStats.PackageStats stats = getCompilerPackageStats(pkg.packageName);
            if (stats == null) {
                ipw.println("(No recorded stats)");
            } else {
                stats.dump(ipw);
            }
            ipw.decreaseIndent();
        }
    }

    private String dumpDomainString(String packageName) {
        List<IntentFilterVerificationInfo> iviList = getIntentFilterVerifications(packageName)
                .getList();
        List<IntentFilter> filters = getAllIntentFilters(packageName).getList();

        ArraySet<String> result = new ArraySet<>();
        if (iviList.size() > 0) {
            for (IntentFilterVerificationInfo ivi : iviList) {
                for (String host : ivi.getDomains()) {
                    result.add(host);
                }
            }
        }
        if (filters != null && filters.size() > 0) {
            for (IntentFilter filter : filters) {
                if (filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                        && (filter.hasDataScheme(IntentFilter.SCHEME_HTTP) ||
                                filter.hasDataScheme(IntentFilter.SCHEME_HTTPS))) {
                    result.addAll(filter.getHostsList());
                }
            }
        }

        StringBuilder sb = new StringBuilder(result.size() * 16);
        for (String domain : result) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(domain);
        }
        return sb.toString();
    }

    // ------- apps on sdcard specific code -------
    static final boolean DEBUG_SD_INSTALL = false;

    private static final String SD_ENCRYPTION_KEYSTORE_NAME = "AppsOnSD";

    private static final String SD_ENCRYPTION_ALGORITHM = "AES";

    private boolean mMediaMounted = false;

    static String getEncryptKey() {
        try {
            String sdEncKey = SystemKeyStore.getInstance().retrieveKeyHexString(
                    SD_ENCRYPTION_KEYSTORE_NAME);
            if (sdEncKey == null) {
                sdEncKey = SystemKeyStore.getInstance().generateNewKeyHexString(128,
                        SD_ENCRYPTION_ALGORITHM, SD_ENCRYPTION_KEYSTORE_NAME);
                if (sdEncKey == null) {
                    Slog.e(TAG, "Failed to create encryption keys");
                    return null;
                }
            }
            return sdEncKey;
        } catch (NoSuchAlgorithmException nsae) {
            Slog.e(TAG, "Failed to create encryption keys with exception: " + nsae);
            return null;
        } catch (IOException ioe) {
            Slog.e(TAG, "Failed to retrieve encryption keys with exception: " + ioe);
            return null;
        }
    }

    /*
     * Update media status on PackageManager.
     */
    @Override
    public void updateExternalMediaStatus(final boolean mediaStatus, final boolean reportStatus) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != 0 && callingUid != Process.SYSTEM_UID) {
            throw new SecurityException("Media status can only be updated by the system");
        }
        // reader; this apparently protects mMediaMounted, but should probably
        // be a different lock in that case.
        synchronized (mPackages) {
            Log.i(TAG, "Updating external media status from "
                    + (mMediaMounted ? "mounted" : "unmounted") + " to "
                    + (mediaStatus ? "mounted" : "unmounted"));
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "updateExternalMediaStatus:: mediaStatus=" + mediaStatus
                        + ", mMediaMounted=" + mMediaMounted);
            if (mediaStatus == mMediaMounted) {
                final Message msg = mHandler.obtainMessage(UPDATED_MEDIA_STATUS, reportStatus ? 1
                        : 0, -1);
                mHandler.sendMessage(msg);
                return;
            }
            mMediaMounted = mediaStatus;
        }
        // Queue up an async operation since the package installation may take a
        // little while.
        mHandler.post(new Runnable() {
            public void run() {
                updateExternalMediaStatusInner(mediaStatus, reportStatus, true);
            }
        });
    }

    /**
     * Called by MountService when the initial ASECs to scan are available.
     * Should block until all the ASEC containers are finished being scanned.
     */
    public void scanAvailableAsecs() {
        updateExternalMediaStatusInner(true, false, false);
    }

    /*
     * Collect information of applications on external media, map them against
     * existing containers and update information based on current mount status.
     * Please note that we always have to report status if reportStatus has been
     * set to true especially when unloading packages.
     */
    private void updateExternalMediaStatusInner(boolean isMounted, boolean reportStatus,
            boolean externalStorage) {
        ArrayMap<AsecInstallArgs, String> processCids = new ArrayMap<>();
        int[] uidArr = EmptyArray.INT;

        final String[] list = PackageHelper.getSecureContainerList();
        if (ArrayUtils.isEmpty(list)) {
            Log.i(TAG, "No secure containers found");
        } else {
            // Process list of secure containers and categorize them
            // as active or stale based on their package internal state.

            // reader
            synchronized (mPackages) {
                for (String cid : list) {
                    // Leave stages untouched for now; installer service owns them
                    if (PackageInstallerService.isStageName(cid)) continue;

                    if (DEBUG_SD_INSTALL)
                        Log.i(TAG, "Processing container " + cid);
                    String pkgName = getAsecPackageName(cid);
                    if (pkgName == null) {
                        Slog.i(TAG, "Found stale container " + cid + " with no package name");
                        continue;
                    }
                    if (DEBUG_SD_INSTALL)
                        Log.i(TAG, "Looking for pkg : " + pkgName);

                    final PackageSetting ps = mSettings.mPackages.get(pkgName);
                    if (ps == null) {
                        Slog.i(TAG, "Found stale container " + cid + " with no matching settings");
                        continue;
                    }

                    /*
                     * Skip packages that are not external if we're unmounting
                     * external storage.
                     */
                    if (externalStorage && !isMounted && !isExternal(ps)) {
                        continue;
                    }

                    final AsecInstallArgs args = new AsecInstallArgs(cid,
                            getAppDexInstructionSets(ps), ps.isForwardLocked());
                    // The package status is changed only if the code path
                    // matches between settings and the container id.
                    if (ps.codePathString != null
                            && ps.codePathString.startsWith(args.getCodePath())) {
                        if (DEBUG_SD_INSTALL) {
                            Log.i(TAG, "Container : " + cid + " corresponds to pkg : " + pkgName
                                    + " at code path: " + ps.codePathString);
                        }

                        // We do have a valid package installed on sdcard
                        processCids.put(args, ps.codePathString);
                        final int uid = ps.appId;
                        if (uid != -1) {
                            uidArr = ArrayUtils.appendInt(uidArr, uid);
                        }
                    } else {
                        Slog.i(TAG, "Found stale container " + cid + ": expected codePath="
                                + ps.codePathString);
                    }
                }
            }

            Arrays.sort(uidArr);
        }

        // Process packages with valid entries.
        if (isMounted) {
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "Loading packages");
            loadMediaPackages(processCids, uidArr, externalStorage);
            startCleaningPackages();
            mInstallerService.onSecureContainersAvailable();
        } else {
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "Unloading packages");
            unloadMediaPackages(processCids, uidArr, reportStatus);
        }
    }

    private void sendResourcesChangedBroadcast(boolean mediaStatus, boolean replacing,
            ArrayList<ApplicationInfo> infos, IIntentReceiver finishedReceiver) {
        final int size = infos.size();
        final String[] packageNames = new String[size];
        final int[] packageUids = new int[size];
        for (int i = 0; i < size; i++) {
            final ApplicationInfo info = infos.get(i);
            packageNames[i] = info.packageName;
            packageUids[i] = info.uid;
        }
        sendResourcesChangedBroadcast(mediaStatus, replacing, packageNames, packageUids,
                finishedReceiver);
    }

    private void sendResourcesChangedBroadcast(boolean mediaStatus, boolean replacing,
            ArrayList<String> pkgList, int uidArr[], IIntentReceiver finishedReceiver) {
        sendResourcesChangedBroadcast(mediaStatus, replacing,
                pkgList.toArray(new String[pkgList.size()]), uidArr, finishedReceiver);
    }

    private void sendResourcesChangedBroadcast(boolean mediaStatus, boolean replacing,
            String[] pkgList, int uidArr[], IIntentReceiver finishedReceiver) {
        int size = pkgList.length;
        if (size > 0) {
            // Send broadcasts here
            Bundle extras = new Bundle();
            extras.putStringArray(Intent.EXTRA_CHANGED_PACKAGE_LIST, pkgList);
            if (uidArr != null) {
                extras.putIntArray(Intent.EXTRA_CHANGED_UID_LIST, uidArr);
            }
            if (replacing) {
                extras.putBoolean(Intent.EXTRA_REPLACING, replacing);
            }
            String action = mediaStatus ? Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE
                    : Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE;
            sendPackageBroadcast(action, null, extras, 0, null, finishedReceiver, null);
        }
    }

   /*
     * Look at potentially valid container ids from processCids If package
     * information doesn't match the one on record or package scanning fails,
     * the cid is added to list of removeCids. We currently don't delete stale
     * containers.
     */
    private void loadMediaPackages(ArrayMap<AsecInstallArgs, String> processCids, int[] uidArr,
            boolean externalStorage) {
        ArrayList<String> pkgList = new ArrayList<String>();
        Set<AsecInstallArgs> keys = processCids.keySet();

        for (AsecInstallArgs args : keys) {
            String codePath = processCids.get(args);
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "Loading container : " + args.cid);
            int retCode = PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
            try {
                // Make sure there are no container errors first.
                if (args.doPreInstall(PackageManager.INSTALL_SUCCEEDED) != PackageManager.INSTALL_SUCCEEDED) {
                    Slog.e(TAG, "Failed to mount cid : " + args.cid
                            + " when installing from sdcard");
                    continue;
                }
                // Check code path here.
                if (codePath == null || !codePath.startsWith(args.getCodePath())) {
                    Slog.e(TAG, "Container " + args.cid + " cachepath " + args.getCodePath()
                            + " does not match one in settings " + codePath);
                    continue;
                }
                // Parse package
                int parseFlags = mDefParseFlags;
                if (args.isExternalAsec()) {
                    parseFlags |= PackageParser.PARSE_EXTERNAL_STORAGE;
                }
                if (args.isFwdLocked()) {
                    parseFlags |= PackageParser.PARSE_FORWARD_LOCK;
                }

                synchronized (mInstallLock) {
                    PackageParser.Package pkg = null;
                    try {
                        // Sadly we don't know the package name yet to freeze it
                        pkg = scanPackageTracedLI(new File(codePath), parseFlags,
                                SCAN_IGNORE_FROZEN, 0, null);
                    } catch (PackageManagerException e) {
                        Slog.w(TAG, "Failed to scan " + codePath + ": " + e.getMessage());
                    }
                    // Scan the package
                    if (pkg != null) {
                        /*
                         * TODO why is the lock being held? doPostInstall is
                         * called in other places without the lock. This needs
                         * to be straightened out.
                         */
                        // writer
                        synchronized (mPackages) {
                            retCode = PackageManager.INSTALL_SUCCEEDED;
                            pkgList.add(pkg.packageName);
                            // Post process args
                            args.doPostInstall(PackageManager.INSTALL_SUCCEEDED,
                                    pkg.applicationInfo.uid);
                        }
                    } else {
                        Slog.i(TAG, "Failed to install pkg from  " + codePath + " from sdcard");
                    }
                }

            } finally {
                if (retCode != PackageManager.INSTALL_SUCCEEDED) {
                    Log.w(TAG, "Container " + args.cid + " is stale, retCode=" + retCode);
                }
            }
        }
        // writer
        synchronized (mPackages) {
            // If the platform SDK has changed since the last time we booted,
            // we need to re-grant app permission to catch any new ones that
            // appear. This is really a hack, and means that apps can in some
            // cases get permissions that the user didn't initially explicitly
            // allow... it would be nice to have some better way to handle
            // this situation.
            final VersionInfo ver = externalStorage ? mSettings.getExternalVersion()
                    : mSettings.getInternalVersion();
            final String volumeUuid = externalStorage ? StorageManager.UUID_PRIMARY_PHYSICAL
                    : StorageManager.UUID_PRIVATE_INTERNAL;

            int updateFlags = UPDATE_PERMISSIONS_ALL;
            if (ver.sdkVersion != mSdkVersion) {
                logCriticalInfo(Log.INFO, "Platform changed from " + ver.sdkVersion + " to "
                        + mSdkVersion + "; regranting permissions for external");
                updateFlags |= UPDATE_PERMISSIONS_REPLACE_PKG | UPDATE_PERMISSIONS_REPLACE_ALL;
            }
            updatePermissionsLPw(null, null, volumeUuid, updateFlags);

            // Yay, everything is now upgraded
            ver.forceCurrent();

            // can downgrade to reader
            // Persist settings
            mSettings.writeLPr();
        }
        // Send a broadcast to let everyone know we are done processing
        if (pkgList.size() > 0) {
            sendResourcesChangedBroadcast(true, false, pkgList, uidArr, null);
        }
    }

   /*
     * Utility method to unload a list of specified containers
     */
    private void unloadAllContainers(Set<AsecInstallArgs> cidArgs) {
        // Just unmount all valid containers.
        for (AsecInstallArgs arg : cidArgs) {
            synchronized (mInstallLock) {
                arg.doPostDeleteLI(false);
           }
       }
   }

    /*
     * Unload packages mounted on external media. This involves deleting package
     * data from internal structures, sending broadcasts about disabled packages,
     * gc'ing to free up references, unmounting all secure containers
     * corresponding to packages on external media, and posting a
     * UPDATED_MEDIA_STATUS message if status has been requested. Please note
     * that we always have to post this message if status has been requested no
     * matter what.
     */
    private void unloadMediaPackages(ArrayMap<AsecInstallArgs, String> processCids, int uidArr[],
            final boolean reportStatus) {
        if (DEBUG_SD_INSTALL)
            Log.i(TAG, "unloading media packages");
        ArrayList<String> pkgList = new ArrayList<String>();
        ArrayList<AsecInstallArgs> failedList = new ArrayList<AsecInstallArgs>();
        final Set<AsecInstallArgs> keys = processCids.keySet();
        for (AsecInstallArgs args : keys) {
            String pkgName = args.getPackageName();
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "Trying to unload pkg : " + pkgName);
            // Delete package internally
            PackageRemovedInfo outInfo = new PackageRemovedInfo();
            synchronized (mInstallLock) {
                final int deleteFlags = PackageManager.DELETE_KEEP_DATA;
                final boolean res;
                try (PackageFreezer freezer = freezePackageForDelete(pkgName, deleteFlags,
                        "unloadMediaPackages")) {
                    res = deletePackageLIF(pkgName, null, false, null, deleteFlags, outInfo, false,
                            null);
                }
                if (res) {
                    pkgList.add(pkgName);
                } else {
                    Slog.e(TAG, "Failed to delete pkg from sdcard : " + pkgName);
                    failedList.add(args);
                }
            }
        }

        // reader
        synchronized (mPackages) {
            // We didn't update the settings after removing each package;
            // write them now for all packages.
            mSettings.writeLPr();
        }

        // We have to absolutely send UPDATED_MEDIA_STATUS only
        // after confirming that all the receivers processed the ordered
        // broadcast when packages get disabled, force a gc to clean things up.
        // and unload all the containers.
        if (pkgList.size() > 0) {
            sendResourcesChangedBroadcast(false, false, pkgList, uidArr,
                    new IIntentReceiver.Stub() {
                public void performReceive(Intent intent, int resultCode, String data,
                        Bundle extras, boolean ordered, boolean sticky,
                        int sendingUser) throws RemoteException {
                    Message msg = mHandler.obtainMessage(UPDATED_MEDIA_STATUS,
                            reportStatus ? 1 : 0, 1, keys);
                    mHandler.sendMessage(msg);
                }
            });
        } else {
            Message msg = mHandler.obtainMessage(UPDATED_MEDIA_STATUS, reportStatus ? 1 : 0, -1,
                    keys);
            mHandler.sendMessage(msg);
        }
    }

    private void loadPrivatePackages(final VolumeInfo vol) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                loadPrivatePackagesInner(vol);
            }
        });
    }

    private void loadPrivatePackagesInner(VolumeInfo vol) {
        final String volumeUuid = vol.fsUuid;
        if (TextUtils.isEmpty(volumeUuid)) {
            Slog.e(TAG, "Loading internal storage is probably a mistake; ignoring");
            return;
        }

        final ArrayList<PackageFreezer> freezers = new ArrayList<>();
        final ArrayList<ApplicationInfo> loaded = new ArrayList<>();
        final int parseFlags = mDefParseFlags | PackageParser.PARSE_EXTERNAL_STORAGE;

        final VersionInfo ver;
        final List<PackageSetting> packages;
        synchronized (mPackages) {
            ver = mSettings.findOrCreateVersion(volumeUuid);
            packages = mSettings.getVolumePackagesLPr(volumeUuid);
        }

        for (PackageSetting ps : packages) {
            freezers.add(freezePackage(ps.name, "loadPrivatePackagesInner"));
            synchronized (mInstallLock) {
                final PackageParser.Package pkg;
                try {
                    pkg = scanPackageTracedLI(ps.codePath, parseFlags, SCAN_INITIAL, 0, null);
                    loaded.add(pkg.applicationInfo);

                } catch (PackageManagerException e) {
                    Slog.w(TAG, "Failed to scan " + ps.codePath + ": " + e.getMessage());
                }

                if (!Build.FINGERPRINT.equals(ver.fingerprint)) {
                    clearAppDataLIF(ps.pkg, UserHandle.USER_ALL,
                            StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE
                                    | Installer.FLAG_CLEAR_CODE_CACHE_ONLY);
                }
            }
        }

        // Reconcile app data for all started/unlocked users
        final StorageManager sm = mContext.getSystemService(StorageManager.class);
        final UserManager um = mContext.getSystemService(UserManager.class);
        UserManagerInternal umInternal = getUserManagerInternal();
        for (UserInfo user : um.getUsers()) {
            final int flags;
            if (umInternal.isUserUnlockingOrUnlocked(user.id)) {
                flags = StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE;
            } else if (umInternal.isUserRunning(user.id)) {
                flags = StorageManager.FLAG_STORAGE_DE;
            } else {
                continue;
            }

            try {
                sm.prepareUserStorage(volumeUuid, user.id, user.serialNumber, flags);
                synchronized (mInstallLock) {
                    reconcileAppsDataLI(volumeUuid, user.id, flags);
                }
            } catch (IllegalStateException e) {
                // Device was probably ejected, and we'll process that event momentarily
                Slog.w(TAG, "Failed to prepare storage: " + e);
            }
        }

        synchronized (mPackages) {
            int updateFlags = UPDATE_PERMISSIONS_ALL;
            if (ver.sdkVersion != mSdkVersion) {
                logCriticalInfo(Log.INFO, "Platform changed from " + ver.sdkVersion + " to "
                        + mSdkVersion + "; regranting permissions for " + volumeUuid);
                updateFlags |= UPDATE_PERMISSIONS_REPLACE_PKG | UPDATE_PERMISSIONS_REPLACE_ALL;
            }
            updatePermissionsLPw(null, null, volumeUuid, updateFlags);

            // Yay, everything is now upgraded
            ver.forceCurrent();

            mSettings.writeLPr();
        }

        for (PackageFreezer freezer : freezers) {
            freezer.close();
        }

        if (DEBUG_INSTALL) Slog.d(TAG, "Loaded packages " + loaded);
        sendResourcesChangedBroadcast(true, false, loaded, null);
    }

    private void unloadPrivatePackages(final VolumeInfo vol) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                unloadPrivatePackagesInner(vol);
            }
        });
    }

    private void unloadPrivatePackagesInner(VolumeInfo vol) {
        final String volumeUuid = vol.fsUuid;
        if (TextUtils.isEmpty(volumeUuid)) {
            Slog.e(TAG, "Unloading internal storage is probably a mistake; ignoring");
            return;
        }

        final ArrayList<ApplicationInfo> unloaded = new ArrayList<>();
        synchronized (mInstallLock) {
        synchronized (mPackages) {
            final List<PackageSetting> packages = mSettings.getVolumePackagesLPr(volumeUuid);
            for (PackageSetting ps : packages) {
                if (ps.pkg == null) continue;

                final ApplicationInfo info = ps.pkg.applicationInfo;
                final int deleteFlags = PackageManager.DELETE_KEEP_DATA;
                final PackageRemovedInfo outInfo = new PackageRemovedInfo();

                try (PackageFreezer freezer = freezePackageForDelete(ps.name, deleteFlags,
                        "unloadPrivatePackagesInner")) {
                    if (deletePackageLIF(ps.name, null, false, null, deleteFlags, outInfo,
                            false, null)) {
                        unloaded.add(info);
                    } else {
                        Slog.w(TAG, "Failed to unload " + ps.codePath);
                    }
                }

                // Try very hard to release any references to this package
                // so we don't risk the system server being killed due to
                // open FDs
                AttributeCache.instance().removePackage(ps.name);
            }

            mSettings.writeLPr();
        }
        }

        if (DEBUG_INSTALL) Slog.d(TAG, "Unloaded packages " + unloaded);
        sendResourcesChangedBroadcast(false, false, unloaded, null);

        // Try very hard to release any references to this path so we don't risk
        // the system server being killed due to open FDs
        ResourcesManager.getInstance().invalidatePath(vol.getPath().getAbsolutePath());

        for (int i = 0; i < 3; i++) {
            System.gc();
            System.runFinalization();
        }
    }

    /**
     * Prepare storage areas for given user on all mounted devices.
     */
    void prepareUserData(int userId, int userSerial, int flags) {
        synchronized (mInstallLock) {
            final StorageManager storage = mContext.getSystemService(StorageManager.class);
            for (VolumeInfo vol : storage.getWritablePrivateVolumes()) {
                final String volumeUuid = vol.getFsUuid();
                prepareUserDataLI(volumeUuid, userId, userSerial, flags, true);
            }
        }
    }

    private void prepareUserDataLI(String volumeUuid, int userId, int userSerial, int flags,
            boolean allowRecover) {
        // Prepare storage and verify that serial numbers are consistent; if
        // there's a mismatch we need to destroy to avoid leaking data
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        try {
            storage.prepareUserStorage(volumeUuid, userId, userSerial, flags);

            if ((flags & StorageManager.FLAG_STORAGE_DE) != 0 && !mOnlyCore) {
                UserManagerService.enforceSerialNumber(
                        Environment.getDataUserDeDirectory(volumeUuid, userId), userSerial);
                if (Objects.equals(volumeUuid, StorageManager.UUID_PRIVATE_INTERNAL)) {
                    UserManagerService.enforceSerialNumber(
                            Environment.getDataSystemDeDirectory(userId), userSerial);
                }
            }
            if ((flags & StorageManager.FLAG_STORAGE_CE) != 0 && !mOnlyCore) {
                UserManagerService.enforceSerialNumber(
                        Environment.getDataUserCeDirectory(volumeUuid, userId), userSerial);
                if (Objects.equals(volumeUuid, StorageManager.UUID_PRIVATE_INTERNAL)) {
                    UserManagerService.enforceSerialNumber(
                            Environment.getDataSystemCeDirectory(userId), userSerial);
                }
            }

            synchronized (mInstallLock) {
                mInstaller.createUserData(volumeUuid, userId, userSerial, flags);
            }
        } catch (Exception e) {
            logCriticalInfo(Log.WARN, "Destroying user " + userId + " on volume " + volumeUuid
                    + " because we failed to prepare: " + e);
            destroyUserDataLI(volumeUuid, userId,
                    StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE);

            if (allowRecover) {
                // Try one last time; if we fail again we're really in trouble
                prepareUserDataLI(volumeUuid, userId, userSerial, flags, false);
            }
        }
    }

    /**
     * Destroy storage areas for given user on all mounted devices.
     */
    void destroyUserData(int userId, int flags) {
        synchronized (mInstallLock) {
            final StorageManager storage = mContext.getSystemService(StorageManager.class);
            for (VolumeInfo vol : storage.getWritablePrivateVolumes()) {
                final String volumeUuid = vol.getFsUuid();
                destroyUserDataLI(volumeUuid, userId, flags);
            }
        }
    }

    private void destroyUserDataLI(String volumeUuid, int userId, int flags) {
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        try {
            // Clean up app data, profile data, and media data
            mInstaller.destroyUserData(volumeUuid, userId, flags);

            // Clean up system data
            if (Objects.equals(volumeUuid, StorageManager.UUID_PRIVATE_INTERNAL)) {
                if ((flags & StorageManager.FLAG_STORAGE_DE) != 0) {
                    FileUtils.deleteContentsAndDir(Environment.getUserSystemDirectory(userId));
                    FileUtils.deleteContentsAndDir(Environment.getDataSystemDeDirectory(userId));
                }
                if ((flags & StorageManager.FLAG_STORAGE_CE) != 0) {
                    FileUtils.deleteContentsAndDir(Environment.getDataSystemCeDirectory(userId));
                }
            }

            // Data with special labels is now gone, so finish the job
            storage.destroyUserStorage(volumeUuid, userId, flags);

        } catch (Exception e) {
            logCriticalInfo(Log.WARN,
                    "Failed to destroy user " + userId + " on volume " + volumeUuid + ": " + e);
        }
    }

    /**
     * Examine all users present on given mounted volume, and destroy data
     * belonging to users that are no longer valid, or whose user ID has been
     * recycled.
     */
    private void reconcileUsers(String volumeUuid) {
        final List<File> files = new ArrayList<>();
        Collections.addAll(files, FileUtils
                .listFilesOrEmpty(Environment.getDataUserDeDirectory(volumeUuid)));
        Collections.addAll(files, FileUtils
                .listFilesOrEmpty(Environment.getDataUserCeDirectory(volumeUuid)));
        Collections.addAll(files, FileUtils
                .listFilesOrEmpty(Environment.getDataSystemDeDirectory()));
        Collections.addAll(files, FileUtils
                .listFilesOrEmpty(Environment.getDataSystemCeDirectory()));
        for (File file : files) {
            if (!file.isDirectory()) continue;

            final int userId;
            final UserInfo info;
            try {
                userId = Integer.parseInt(file.getName());
                info = sUserManager.getUserInfo(userId);
            } catch (NumberFormatException e) {
                Slog.w(TAG, "Invalid user directory " + file);
                continue;
            }

            boolean destroyUser = false;
            if (info == null) {
                logCriticalInfo(Log.WARN, "Destroying user directory " + file
                        + " because no matching user was found");
                destroyUser = true;
            } else if (!mOnlyCore) {
                try {
                    UserManagerService.enforceSerialNumber(file, info.serialNumber);
                } catch (IOException e) {
                    logCriticalInfo(Log.WARN, "Destroying user directory " + file
                            + " because we failed to enforce serial number: " + e);
                    destroyUser = true;
                }
            }

            if (destroyUser) {
                synchronized (mInstallLock) {
                    destroyUserDataLI(volumeUuid, userId,
                            StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE);
                }
            }
        }
    }

    private void assertPackageKnown(String volumeUuid, String packageName)
            throws PackageManagerException {
        synchronized (mPackages) {
            final PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                throw new PackageManagerException("Package " + packageName + " is unknown");
            } else if (!TextUtils.equals(volumeUuid, ps.volumeUuid)) {
                throw new PackageManagerException(
                        "Package " + packageName + " found on unknown volume " + volumeUuid
                                + "; expected volume " + ps.volumeUuid);
            }
        }
    }

    private void assertPackageKnownAndInstalled(String volumeUuid, String packageName, int userId)
            throws PackageManagerException {
        synchronized (mPackages) {
            final PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                throw new PackageManagerException("Package " + packageName + " is unknown");
            } else if (!TextUtils.equals(volumeUuid, ps.volumeUuid)) {
                throw new PackageManagerException(
                        "Package " + packageName + " found on unknown volume " + volumeUuid
                                + "; expected volume " + ps.volumeUuid);
            } else if (!ps.getInstalled(userId)) {
                throw new PackageManagerException(
                        "Package " + packageName + " not installed for user " + userId);
            }
        }
    }

    /**
     * Examine all apps present on given mounted volume, and destroy apps that
     * aren't expected, either due to uninstallation or reinstallation on
     * another volume.
     */
    private void reconcileApps(String volumeUuid) {
        final File[] files = FileUtils
                .listFilesOrEmpty(Environment.getDataAppDirectory(volumeUuid));
        for (File file : files) {
            final boolean isPackage = (isApkFile(file) || file.isDirectory())
                    && !PackageInstallerService.isStageName(file.getName());
            if (!isPackage) {
                // Ignore entries which are not packages
                continue;
            }

            try {
                final PackageLite pkg = PackageParser.parsePackageLite(file,
                        PackageParser.PARSE_MUST_BE_APK);
                assertPackageKnown(volumeUuid, pkg.packageName);

            } catch (PackageParserException | PackageManagerException e) {
                logCriticalInfo(Log.WARN, "Destroying " + file + " due to: " + e);
                synchronized (mInstallLock) {
                    removeCodePathLI(file);
                }
            }
        }
    }

    /**
     * Reconcile all app data for the given user.
     * <p>
     * Verifies that directories exist and that ownership and labeling is
     * correct for all installed apps on all mounted volumes.
     */
    void reconcileAppsData(int userId, int flags) {
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        for (VolumeInfo vol : storage.getWritablePrivateVolumes()) {
            final String volumeUuid = vol.getFsUuid();
            synchronized (mInstallLock) {
                reconcileAppsDataLI(volumeUuid, userId, flags);
            }
        }
    }

    /**
     * Reconcile all app data on given mounted volume.
     * <p>
     * Destroys app data that isn't expected, either due to uninstallation or
     * reinstallation on another volume.
     * <p>
     * Verifies that directories exist and that ownership and labeling is
     * correct for all installed apps.
     */
    private void reconcileAppsDataLI(String volumeUuid, int userId, int flags) {
        Slog.v(TAG, "reconcileAppsData for " + volumeUuid + " u" + userId + " 0x"
                + Integer.toHexString(flags));

        final File ceDir = Environment.getDataUserCeDirectory(volumeUuid, userId);
        final File deDir = Environment.getDataUserDeDirectory(volumeUuid, userId);

        boolean restoreconNeeded = false;

        // First look for stale data that doesn't belong, and check if things
        // have changed since we did our last restorecon
        if ((flags & StorageManager.FLAG_STORAGE_CE) != 0) {
            if (StorageManager.isFileEncryptedNativeOrEmulated()
                    && !StorageManager.isUserKeyUnlocked(userId)) {
                throw new RuntimeException(
                        "Yikes, someone asked us to reconcile CE storage while " + userId
                                + " was still locked; this would have caused massive data loss!");
            }

            restoreconNeeded |= SELinuxMMAC.isRestoreconNeeded(ceDir);

            final File[] files = FileUtils.listFilesOrEmpty(ceDir);
            for (File file : files) {
                final String packageName = file.getName();
                try {
                    assertPackageKnownAndInstalled(volumeUuid, packageName, userId);
                } catch (PackageManagerException e) {
                    logCriticalInfo(Log.WARN, "Destroying " + file + " due to: " + e);
                    try {
                        mInstaller.destroyAppData(volumeUuid, packageName, userId,
                                StorageManager.FLAG_STORAGE_CE, 0);
                    } catch (InstallerException e2) {
                        logCriticalInfo(Log.WARN, "Failed to destroy: " + e2);
                    }
                }
            }
        }
        if ((flags & StorageManager.FLAG_STORAGE_DE) != 0) {
            restoreconNeeded |= SELinuxMMAC.isRestoreconNeeded(deDir);

            final File[] files = FileUtils.listFilesOrEmpty(deDir);
            for (File file : files) {
                final String packageName = file.getName();
                try {
                    assertPackageKnownAndInstalled(volumeUuid, packageName, userId);
                } catch (PackageManagerException e) {
                    logCriticalInfo(Log.WARN, "Destroying " + file + " due to: " + e);
                    try {
                        mInstaller.destroyAppData(volumeUuid, packageName, userId,
                                StorageManager.FLAG_STORAGE_DE, 0);
                    } catch (InstallerException e2) {
                        logCriticalInfo(Log.WARN, "Failed to destroy: " + e2);
                    }
                }
            }
        }

        // Ensure that data directories are ready to roll for all packages
        // installed for this volume and user
        final List<PackageSetting> packages;
        synchronized (mPackages) {
            packages = mSettings.getVolumePackagesLPr(volumeUuid);
        }
        int preparedCount = 0;
        for (PackageSetting ps : packages) {
            final String packageName = ps.name;
            if (ps.pkg == null) {
                Slog.w(TAG, "Odd, missing scanned package " + packageName);
                // TODO: might be due to legacy ASEC apps; we should circle back
                // and reconcile again once they're scanned
                continue;
            }

            if (ps.getInstalled(userId)) {
                prepareAppDataLIF(ps.pkg, userId, flags, restoreconNeeded);

                if (maybeMigrateAppDataLIF(ps.pkg, userId)) {
                    // We may have just shuffled around app data directories, so
                    // prepare them one more time
                    prepareAppDataLIF(ps.pkg, userId, flags, restoreconNeeded);
                }

                preparedCount++;
            }
        }

        if (restoreconNeeded) {
            if ((flags & StorageManager.FLAG_STORAGE_CE) != 0) {
                SELinuxMMAC.setRestoreconDone(ceDir);
            }
            if ((flags & StorageManager.FLAG_STORAGE_DE) != 0) {
                SELinuxMMAC.setRestoreconDone(deDir);
            }
        }

        Slog.v(TAG, "reconcileAppsData finished " + preparedCount
                + " packages; restoreconNeeded was " + restoreconNeeded);
    }

    /**
     * Prepare app data for the given app just after it was installed or
     * upgraded. This method carefully only touches users that it's installed
     * for, and it forces a restorecon to handle any seinfo changes.
     * <p>
     * Verifies that directories exist and that ownership and labeling is
     * correct for all installed apps. If there is an ownership mismatch, it
     * will try recovering system apps by wiping data; third-party app data is
     * left intact.
     * <p>
     * <em>Note: To avoid a deadlock, do not call this method with {@code mPackages} lock held</em>
     */
    private void prepareAppDataAfterInstallLIF(PackageParser.Package pkg) {
        final PackageSetting ps;
        synchronized (mPackages) {
            ps = mSettings.mPackages.get(pkg.packageName);
            mSettings.writeKernelMappingLPr(ps);
        }

        final UserManager um = mContext.getSystemService(UserManager.class);
        UserManagerInternal umInternal = getUserManagerInternal();
        for (UserInfo user : um.getUsers()) {
            final int flags;
            if (umInternal.isUserUnlockingOrUnlocked(user.id)) {
                flags = StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE;
            } else if (umInternal.isUserRunning(user.id)) {
                flags = StorageManager.FLAG_STORAGE_DE;
            } else {
                continue;
            }

            if (ps.getInstalled(user.id)) {
                // Whenever an app changes, force a restorecon of its data
                // TODO: when user data is locked, mark that we're still dirty
                prepareAppDataLIF(pkg, user.id, flags, true);
            }
        }
    }

    /**
     * Prepare app data for the given app.
     * <p>
     * Verifies that directories exist and that ownership and labeling is
     * correct for all installed apps. If there is an ownership mismatch, this
     * will try recovering system apps by wiping data; third-party app data is
     * left intact.
     */
    private void prepareAppDataLIF(PackageParser.Package pkg, int userId, int flags,
            boolean restoreconNeeded) {
        if (pkg == null) {
            Slog.wtf(TAG, "Package was null!", new Throwable());
            return;
        }
        prepareAppDataLeafLIF(pkg, userId, flags, restoreconNeeded);
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            prepareAppDataLeafLIF(pkg.childPackages.get(i), userId, flags, restoreconNeeded);
        }
    }

    private void prepareAppDataLeafLIF(PackageParser.Package pkg, int userId, int flags,
            boolean restoreconNeeded) {
        if (DEBUG_APP_DATA) {
            Slog.v(TAG, "prepareAppData for " + pkg.packageName + " u" + userId + " 0x"
                    + Integer.toHexString(flags) + (restoreconNeeded ? " restoreconNeeded" : ""));
        }

        final String volumeUuid = pkg.volumeUuid;
        final String packageName = pkg.packageName;
        final ApplicationInfo app = pkg.applicationInfo;
        final int appId = UserHandle.getAppId(app.uid);

        Preconditions.checkNotNull(app.seinfo);

        try {
            mInstaller.createAppData(volumeUuid, packageName, userId, flags,
                    appId, app.seinfo, app.targetSdkVersion);
        } catch (InstallerException e) {
            if (app.isSystemApp()) {
                logCriticalInfo(Log.ERROR, "Failed to create app data for " + packageName
                        + ", but trying to recover: " + e);
                destroyAppDataLeafLIF(pkg, userId, flags);
                try {
                    mInstaller.createAppData(volumeUuid, packageName, userId, flags,
                            appId, app.seinfo, app.targetSdkVersion);
                    logCriticalInfo(Log.DEBUG, "Recovery succeeded!");
                } catch (InstallerException e2) {
                    logCriticalInfo(Log.DEBUG, "Recovery failed!");
                }
            } else {
                Slog.e(TAG, "Failed to create app data for " + packageName + ": " + e);
            }
        }

        if (restoreconNeeded) {
            try {
                mInstaller.restoreconAppData(volumeUuid, packageName, userId, flags, appId,
                        app.seinfo);
            } catch (InstallerException e) {
                Slog.e(TAG, "Failed to restorecon for " + packageName + ": " + e);
            }
        }

        if ((flags & StorageManager.FLAG_STORAGE_CE) != 0) {
            try {
                // CE storage is unlocked right now, so read out the inode and
                // remember for use later when it's locked
                // TODO: mark this structure as dirty so we persist it!
                final long ceDataInode = mInstaller.getAppDataInode(volumeUuid, packageName, userId,
                        StorageManager.FLAG_STORAGE_CE);
                synchronized (mPackages) {
                    final PackageSetting ps = mSettings.mPackages.get(packageName);
                    if (ps != null) {
                        ps.setCeDataInode(ceDataInode, userId);
                    }
                }
            } catch (InstallerException e) {
                Slog.e(TAG, "Failed to find inode for " + packageName + ": " + e);
            }
        }

        prepareAppDataContentsLeafLIF(pkg, userId, flags);
    }

    private void prepareAppDataContentsLIF(PackageParser.Package pkg, int userId, int flags) {
        if (pkg == null) {
            Slog.wtf(TAG, "Package was null!", new Throwable());
            return;
        }
        prepareAppDataContentsLeafLIF(pkg, userId, flags);
        final int childCount = (pkg.childPackages != null) ? pkg.childPackages.size() : 0;
        for (int i = 0; i < childCount; i++) {
            prepareAppDataContentsLeafLIF(pkg.childPackages.get(i), userId, flags);
        }
    }

    private void prepareAppDataContentsLeafLIF(PackageParser.Package pkg, int userId, int flags) {
        final String volumeUuid = pkg.volumeUuid;
        final String packageName = pkg.packageName;
        final ApplicationInfo app = pkg.applicationInfo;

        if ((flags & StorageManager.FLAG_STORAGE_CE) != 0) {
            // Create a native library symlink only if we have native libraries
            // and if the native libraries are 32 bit libraries. We do not provide
            // this symlink for 64 bit libraries.
            if (app.primaryCpuAbi != null && !VMRuntime.is64BitAbi(app.primaryCpuAbi)) {
                final String nativeLibPath = app.nativeLibraryDir;
                try {
                    mInstaller.linkNativeLibraryDirectory(volumeUuid, packageName,
                            nativeLibPath, userId);
                } catch (InstallerException e) {
                    Slog.e(TAG, "Failed to link native for " + packageName + ": " + e);
                }
            }
        }
    }

    /**
     * For system apps on non-FBE devices, this method migrates any existing
     * CE/DE data to match the {@code defaultToDeviceProtectedStorage} flag
     * requested by the app.
     */
    private boolean maybeMigrateAppDataLIF(PackageParser.Package pkg, int userId) {
        if (pkg.isSystemApp() && !StorageManager.isFileEncryptedNativeOrEmulated()
                && PackageManager.APPLY_DEFAULT_TO_DEVICE_PROTECTED_STORAGE) {
            final int storageTarget = pkg.applicationInfo.isDefaultToDeviceProtectedStorage()
                    ? StorageManager.FLAG_STORAGE_DE : StorageManager.FLAG_STORAGE_CE;
            try {
                mInstaller.migrateAppData(pkg.volumeUuid, pkg.packageName, userId,
                        storageTarget);
            } catch (InstallerException e) {
                logCriticalInfo(Log.WARN,
                        "Failed to migrate " + pkg.packageName + ": " + e.getMessage());
            }
            return true;
        } else {
            return false;
        }
    }

    public PackageFreezer freezePackage(String packageName, String killReason) {
        return freezePackage(packageName, UserHandle.USER_ALL, killReason);
    }

    public PackageFreezer freezePackage(String packageName, int userId, String killReason) {
        return new PackageFreezer(packageName, userId, killReason);
    }

    public PackageFreezer freezePackageForInstall(String packageName, int installFlags,
            String killReason) {
        return freezePackageForInstall(packageName, UserHandle.USER_ALL, installFlags, killReason);
    }

    public PackageFreezer freezePackageForInstall(String packageName, int userId, int installFlags,
            String killReason) {
        if ((installFlags & PackageManager.INSTALL_DONT_KILL_APP) != 0) {
            return new PackageFreezer();
        } else {
            return freezePackage(packageName, userId, killReason);
        }
    }

    public PackageFreezer freezePackageForDelete(String packageName, int deleteFlags,
            String killReason) {
        return freezePackageForDelete(packageName, UserHandle.USER_ALL, deleteFlags, killReason);
    }

    public PackageFreezer freezePackageForDelete(String packageName, int userId, int deleteFlags,
            String killReason) {
        if ((deleteFlags & PackageManager.DELETE_DONT_KILL_APP) != 0) {
            return new PackageFreezer();
        } else {
            return freezePackage(packageName, userId, killReason);
        }
    }

    /**
     * Class that freezes and kills the given package upon creation, and
     * unfreezes it upon closing. This is typically used when doing surgery on
     * app code/data to prevent the app from running while you're working.
     */
    private class PackageFreezer implements AutoCloseable {
        private final String mPackageName;
        private final PackageFreezer[] mChildren;

        private final boolean mWeFroze;

        private final AtomicBoolean mClosed = new AtomicBoolean();
        private final CloseGuard mCloseGuard = CloseGuard.get();

        /**
         * Create and return a stub freezer that doesn't actually do anything,
         * typically used when someone requested
         * {@link PackageManager#INSTALL_DONT_KILL_APP} or
         * {@link PackageManager#DELETE_DONT_KILL_APP}.
         */
        public PackageFreezer() {
            mPackageName = null;
            mChildren = null;
            mWeFroze = false;
            mCloseGuard.open("close");
        }

        public PackageFreezer(String packageName, int userId, String killReason) {
            synchronized (mPackages) {
                mPackageName = packageName;
                mWeFroze = mFrozenPackages.add(mPackageName);

                final PackageSetting ps = mSettings.mPackages.get(mPackageName);
                if (ps != null) {
                    killApplication(ps.name, ps.appId, userId, killReason);
                }

                final PackageParser.Package p = mPackages.get(packageName);
                if (p != null && p.childPackages != null) {
                    final int N = p.childPackages.size();
                    mChildren = new PackageFreezer[N];
                    for (int i = 0; i < N; i++) {
                        mChildren[i] = new PackageFreezer(p.childPackages.get(i).packageName,
                                userId, killReason);
                    }
                } else {
                    mChildren = null;
                }
            }
            mCloseGuard.open("close");
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                mCloseGuard.warnIfOpen();
                close();
            } finally {
                super.finalize();
            }
        }

        @Override
        public void close() {
            mCloseGuard.close();
            if (mClosed.compareAndSet(false, true)) {
                synchronized (mPackages) {
                    if (mWeFroze) {
                        mFrozenPackages.remove(mPackageName);
                    }

                    if (mChildren != null) {
                        for (PackageFreezer freezer : mChildren) {
                            freezer.close();
                        }
                    }
                }
            }
        }
    }

    /**
     * Verify that given package is currently frozen.
     */
    private void checkPackageFrozen(String packageName) {
        synchronized (mPackages) {
            if (!mFrozenPackages.contains(packageName)) {
                Slog.wtf(TAG, "Expected " + packageName + " to be frozen!", new Throwable());
            }
        }
    }

    @Override
    public int movePackage(final String packageName, final String volumeUuid) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MOVE_PACKAGE, null);

        final UserHandle user = new UserHandle(UserHandle.getCallingUserId());
        final int moveId = mNextMoveId.getAndIncrement();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    movePackageInternal(packageName, volumeUuid, moveId, user);
                } catch (PackageManagerException e) {
                    Slog.w(TAG, "Failed to move " + packageName, e);
                    mMoveCallbacks.notifyStatusChanged(moveId,
                            PackageManager.MOVE_FAILED_INTERNAL_ERROR);
                }
            }
        });
        return moveId;
    }

    private void movePackageInternal(final String packageName, final String volumeUuid,
            final int moveId, UserHandle user) throws PackageManagerException {
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        final PackageManager pm = mContext.getPackageManager();

        final boolean currentAsec;
        final String currentVolumeUuid;
        final File codeFile;
        final String installerPackageName;
        final String packageAbiOverride;
        final int appId;
        final String seinfo;
        final String label;
        final int targetSdkVersion;
        final PackageFreezer freezer;
        final int[] installedUserIds;

        // reader
        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            final PackageSetting ps = mSettings.mPackages.get(packageName);
            if (pkg == null || ps == null) {
                throw new PackageManagerException(MOVE_FAILED_DOESNT_EXIST, "Missing package");
            }

            if (pkg.applicationInfo.isSystemApp()) {
                throw new PackageManagerException(MOVE_FAILED_SYSTEM_PACKAGE,
                        "Cannot move system application");
            }

            if (pkg.applicationInfo.isExternalAsec()) {
                currentAsec = true;
                currentVolumeUuid = StorageManager.UUID_PRIMARY_PHYSICAL;
            } else if (pkg.applicationInfo.isForwardLocked()) {
                currentAsec = true;
                currentVolumeUuid = "forward_locked";
            } else {
                currentAsec = false;
                currentVolumeUuid = ps.volumeUuid;

                final File probe = new File(pkg.codePath);
                final File probeOat = new File(probe, "oat");
                if (!probe.isDirectory() || !probeOat.isDirectory()) {
                    throw new PackageManagerException(MOVE_FAILED_INTERNAL_ERROR,
                            "Move only supported for modern cluster style installs");
                }
            }

            if (Objects.equals(currentVolumeUuid, volumeUuid)) {
                throw new PackageManagerException(MOVE_FAILED_INTERNAL_ERROR,
                        "Package already moved to " + volumeUuid);
            }
            if (pkg.applicationInfo.isInternal() && isPackageDeviceAdminOnAnyUser(packageName)) {
                throw new PackageManagerException(MOVE_FAILED_DEVICE_ADMIN,
                        "Device admin cannot be moved");
            }

            if (mFrozenPackages.contains(packageName)) {
                throw new PackageManagerException(MOVE_FAILED_OPERATION_PENDING,
                        "Failed to move already frozen package");
            }

            codeFile = new File(pkg.codePath);
            installerPackageName = ps.installerPackageName;
            packageAbiOverride = ps.cpuAbiOverrideString;
            appId = UserHandle.getAppId(pkg.applicationInfo.uid);
            seinfo = pkg.applicationInfo.seinfo;
            label = String.valueOf(pm.getApplicationLabel(pkg.applicationInfo));
            targetSdkVersion = pkg.applicationInfo.targetSdkVersion;
            freezer = freezePackage(packageName, "movePackageInternal");
            installedUserIds = ps.queryInstalledUsers(sUserManager.getUserIds(), true);
        }

        final Bundle extras = new Bundle();
        extras.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        extras.putString(Intent.EXTRA_TITLE, label);
        mMoveCallbacks.notifyCreated(moveId, extras);

        int installFlags;
        final boolean moveCompleteApp;
        final File measurePath;

        if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, volumeUuid)) {
            installFlags = INSTALL_INTERNAL;
            moveCompleteApp = !currentAsec;
            measurePath = Environment.getDataAppDirectory(volumeUuid);
        } else if (Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, volumeUuid)) {
            installFlags = INSTALL_EXTERNAL;
            moveCompleteApp = false;
            measurePath = storage.getPrimaryPhysicalVolume().getPath();
        } else {
            final VolumeInfo volume = storage.findVolumeByUuid(volumeUuid);
            if (volume == null || volume.getType() != VolumeInfo.TYPE_PRIVATE
                    || !volume.isMountedWritable()) {
                freezer.close();
                throw new PackageManagerException(MOVE_FAILED_INTERNAL_ERROR,
                        "Move location not mounted private volume");
            }

            Preconditions.checkState(!currentAsec);

            installFlags = INSTALL_INTERNAL;
            moveCompleteApp = true;
            measurePath = Environment.getDataAppDirectory(volumeUuid);
        }

        final PackageStats stats = new PackageStats(null, -1);
        synchronized (mInstaller) {
            for (int userId : installedUserIds) {
                if (!getPackageSizeInfoLI(packageName, userId, stats)) {
                    freezer.close();
                    throw new PackageManagerException(MOVE_FAILED_INTERNAL_ERROR,
                            "Failed to measure package size");
                }
            }
        }

        if (DEBUG_INSTALL) Slog.d(TAG, "Measured code size " + stats.codeSize + ", data size "
                + stats.dataSize);

        final long startFreeBytes = measurePath.getFreeSpace();
        final long sizeBytes;
        if (moveCompleteApp) {
            sizeBytes = stats.codeSize + stats.dataSize;
        } else {
            sizeBytes = stats.codeSize;
        }

        if (sizeBytes > storage.getStorageBytesUntilLow(measurePath)) {
            freezer.close();
            throw new PackageManagerException(MOVE_FAILED_INTERNAL_ERROR,
                    "Not enough free space to move");
        }

        mMoveCallbacks.notifyStatusChanged(moveId, 10);

        final CountDownLatch installedLatch = new CountDownLatch(1);
        final IPackageInstallObserver2 installObserver = new IPackageInstallObserver2.Stub() {
            @Override
            public void onUserActionRequired(Intent intent) throws RemoteException {
                throw new IllegalStateException();
            }

            @Override
            public void onPackageInstalled(String basePackageName, int returnCode, String msg,
                    Bundle extras) throws RemoteException {
                if (DEBUG_INSTALL) Slog.d(TAG, "Install result for move: "
                        + PackageManager.installStatusToString(returnCode, msg));

                installedLatch.countDown();
                freezer.close();

                final int status = PackageManager.installStatusToPublicStatus(returnCode);
                switch (status) {
                    case PackageInstaller.STATUS_SUCCESS:
                        mMoveCallbacks.notifyStatusChanged(moveId,
                                PackageManager.MOVE_SUCCEEDED);
                        break;
                    case PackageInstaller.STATUS_FAILURE_STORAGE:
                        mMoveCallbacks.notifyStatusChanged(moveId,
                                PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE);
                        break;
                    default:
                        mMoveCallbacks.notifyStatusChanged(moveId,
                                PackageManager.MOVE_FAILED_INTERNAL_ERROR);
                        break;
                }
            }
        };

        final MoveInfo move;
        if (moveCompleteApp) {
            // Kick off a thread to report progress estimates
            new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            if (installedLatch.await(1, TimeUnit.SECONDS)) {
                                break;
                            }
                        } catch (InterruptedException ignored) {
                        }

                        final long deltaFreeBytes = startFreeBytes - measurePath.getFreeSpace();
                        final int progress = 10 + (int) MathUtils.constrain(
                                ((deltaFreeBytes * 80) / sizeBytes), 0, 80);
                        mMoveCallbacks.notifyStatusChanged(moveId, progress);
                    }
                }
            }.start();

            final String dataAppName = codeFile.getName();
            move = new MoveInfo(moveId, currentVolumeUuid, volumeUuid, packageName,
                    dataAppName, appId, seinfo, targetSdkVersion);
        } else {
            move = null;
        }

        installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;

        final Message msg = mHandler.obtainMessage(INIT_COPY);
        final OriginInfo origin = OriginInfo.fromExistingFile(codeFile);
        final InstallParams params = new InstallParams(origin, move, installObserver, installFlags,
                installerPackageName, volumeUuid, null /*verificationInfo*/, user,
                packageAbiOverride, null /*grantedPermissions*/, null /*certificates*/);
        params.setTraceMethod("movePackage").setTraceCookie(System.identityHashCode(params));
        msg.obj = params;

        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "movePackage",
                System.identityHashCode(msg.obj));
        Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                System.identityHashCode(msg.obj));

        mHandler.sendMessage(msg);
    }

    @Override
    public int movePrimaryStorage(String volumeUuid) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MOVE_PACKAGE, null);

        final int realMoveId = mNextMoveId.getAndIncrement();
        final Bundle extras = new Bundle();
        extras.putString(VolumeRecord.EXTRA_FS_UUID, volumeUuid);
        mMoveCallbacks.notifyCreated(realMoveId, extras);

        final IPackageMoveObserver callback = new IPackageMoveObserver.Stub() {
            @Override
            public void onCreated(int moveId, Bundle extras) {
                // Ignored
            }

            @Override
            public void onStatusChanged(int moveId, int status, long estMillis) {
                mMoveCallbacks.notifyStatusChanged(realMoveId, status, estMillis);
            }
        };

        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        storage.setPrimaryStorageUuid(volumeUuid, callback);
        return realMoveId;
    }

    @Override
    public int getMoveStatus(int moveId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS, null);
        return mMoveCallbacks.mLastStatus.get(moveId);
    }

    @Override
    public void registerMoveCallback(IPackageMoveObserver callback) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS, null);
        mMoveCallbacks.register(callback);
    }

    @Override
    public void unregisterMoveCallback(IPackageMoveObserver callback) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS, null);
        mMoveCallbacks.unregister(callback);
    }

    @Override
    public boolean setInstallLocation(int loc) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS,
                null);
        if (getInstallLocation() == loc) {
            return true;
        }
        if (loc == PackageHelper.APP_INSTALL_AUTO || loc == PackageHelper.APP_INSTALL_INTERNAL
                || loc == PackageHelper.APP_INSTALL_EXTERNAL) {
            android.provider.Settings.Global.putInt(mContext.getContentResolver(),
                    android.provider.Settings.Global.DEFAULT_INSTALL_LOCATION, loc);
            return true;
        }
        return false;
   }

    @Override
    public int getInstallLocation() {
        return android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                android.provider.Settings.Global.DEFAULT_INSTALL_LOCATION,
                PackageHelper.APP_INSTALL_AUTO);
    }

    /** Called by UserManagerService */
    void cleanUpUser(UserManagerService userManager, int userHandle) {
        synchronized (mPackages) {
            mDirtyUsers.remove(userHandle);
            mUserNeedsBadging.delete(userHandle);
            mSettings.removeUserLPw(userHandle);
            mPendingBroadcasts.remove(userHandle);
            mEphemeralApplicationRegistry.onUserRemovedLPw(userHandle);
            removeUnusedPackagesLPw(userManager, userHandle);
        }
    }

    /**
     * We're removing userHandle and would like to remove any downloaded packages
     * that are no longer in use by any other user.
     * @param userHandle the user being removed
     */
    private void removeUnusedPackagesLPw(UserManagerService userManager, final int userHandle) {
        final boolean DEBUG_CLEAN_APKS = false;
        int [] users = userManager.getUserIds();
        Iterator<PackageSetting> psit = mSettings.mPackages.values().iterator();
        while (psit.hasNext()) {
            PackageSetting ps = psit.next();
            if (ps.pkg == null) {
                continue;
            }
            final String packageName = ps.pkg.packageName;
            // Skip over if system app
            if ((ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            if (DEBUG_CLEAN_APKS) {
                Slog.i(TAG, "Checking package " + packageName);
            }
            boolean keep = shouldKeepUninstalledPackageLPr(packageName);
            if (keep) {
                if (DEBUG_CLEAN_APKS) {
                    Slog.i(TAG, "  Keeping package " + packageName + " - requested by DO");
                }
            } else {
                for (int i = 0; i < users.length; i++) {
                    if (users[i] != userHandle && ps.getInstalled(users[i])) {
                        keep = true;
                        if (DEBUG_CLEAN_APKS) {
                            Slog.i(TAG, "  Keeping package " + packageName + " for user "
                                    + users[i]);
                        }
                        break;
                    }
                }
            }
            if (!keep) {
                if (DEBUG_CLEAN_APKS) {
                    Slog.i(TAG, "  Removing package " + packageName);
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        deletePackageX(packageName, userHandle, 0);
                    } //end run
                });
            }
        }
    }

    /** Called by UserManagerService */
    void createNewUser(int userId) {
        synchronized (mInstallLock) {
            mSettings.createNewUserLI(this, mInstaller, userId);
        }
        synchronized (mPackages) {
            scheduleWritePackageRestrictionsLocked(userId);
            scheduleWritePackageListLocked(userId);
            applyFactoryDefaultBrowserLPw(userId);
            primeDomainVerificationsLPw(userId);
        }
    }

    void onNewUserCreated(final int userId) {
        mDefaultPermissionPolicy.grantDefaultPermissions(userId);
        // If permission review for legacy apps is required, we represent
        // dagerous permissions for such apps as always granted runtime
        // permissions to keep per user flag state whether review is needed.
        // Hence, if a new user is added we have to propagate dangerous
        // permission grants for these legacy apps.
        if (Build.PERMISSIONS_REVIEW_REQUIRED) {
            updatePermissionsLPw(null, null, UPDATE_PERMISSIONS_ALL
                    | UPDATE_PERMISSIONS_REPLACE_ALL);
        }
    }

    @Override
    public VerifierDeviceIdentity getVerifierDeviceIdentity() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT,
                "Only package verification agents can read the verifier device identity");

        synchronized (mPackages) {
            return mSettings.getVerifierDeviceIdentityLPw();
        }
    }

    @Override
    public void setPermissionEnforced(String permission, boolean enforced) {
        // TODO: Now that we no longer change GID for storage, this should to away.
        mContext.enforceCallingOrSelfPermission(Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                "setPermissionEnforced");
        if (READ_EXTERNAL_STORAGE.equals(permission)) {
            synchronized (mPackages) {
                if (mSettings.mReadExternalStorageEnforced == null
                        || mSettings.mReadExternalStorageEnforced != enforced) {
                    mSettings.mReadExternalStorageEnforced = enforced;
                    mSettings.writeLPr();
                }
            }
            // kill any non-foreground processes so we restart them and
            // grant/revoke the GID.
            final IActivityManager am = ActivityManagerNative.getDefault();
            if (am != null) {
                final long token = Binder.clearCallingIdentity();
                try {
                    am.killProcessesBelowForeground("setPermissionEnforcement");
                } catch (RemoteException e) {
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            }
        } else {
            throw new IllegalArgumentException("No selective enforcement for " + permission);
        }
    }

    @Override
    @Deprecated
    public boolean isPermissionEnforced(String permission) {
        return true;
    }

    @Override
    public void setComponentProtectedSetting(ComponentName componentName, boolean newState,
            int userId) {
        enforceCrossUserPermission(Binder.getCallingUid(), userId, false, false, "set protected");

        String packageName = componentName.getPackageName();
        String className = componentName.getClassName();

        PackageSetting pkgSetting;
        ArrayList<String> components;

        synchronized (mPackages) {
            pkgSetting = mSettings.mPackages.get(packageName);

            if (pkgSetting == null) {
                if (className == null) {
                    throw new IllegalArgumentException(
                            "Unknown package: " + packageName);
                }
                throw new IllegalArgumentException(
                        "Unknown component: " + packageName
                                + "/" + className);
            }

            //Protection levels must be applied at the Component Level!
            if (className == null) {
                throw new IllegalArgumentException(
                        "Must specify Component Class name."
                );
            } else {
                PackageParser.Package pkg = pkgSetting.pkg;
                if (pkg == null || !pkg.hasComponentClassName(className)) {
                    if (pkg.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN) {
                        throw new IllegalArgumentException("Component class " + className
                                + " does not exist in " + packageName);
                    } else {
                        Slog.w(TAG, "Failed setComponentProtectedSetting: component class "
                                + className + " does not exist in " + packageName);
                    }
                }

                pkgSetting.protectComponentLPw(className, newState, userId);
                mSettings.writePackageRestrictionsLPr(userId);

                components = mPendingBroadcasts.get(userId, packageName);
                final boolean newPackage = components == null;
                if (newPackage) {
                    components = new ArrayList<String>();
                }
                if (!components.contains(className)) {
                    components.add(className);
                }
            }
        }

        long callingId = Binder.clearCallingIdentity();
        try {
            int packageUid = UserHandle.getUid(userId, pkgSetting.appId);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public boolean isComponentProtected(String callingPackage, int callingUid,
            ComponentName componentName, int userId) {
        if (DEBUG_PROTECTED) Log.d(TAG, "Checking if component is protected "
                + componentName.flattenToShortString() + " from calling package " + callingPackage
                + " and callinguid " + callingUid);

        enforceCrossUserPermission(Binder.getCallingUid(), userId, false, false, "set protected");

        //Allow managers full access
        List<String> protectedComponentManagers =
                CMSettings.Secure.getDelimitedStringAsList(mContext.getContentResolver(),
                        CMSettings.Secure.PROTECTED_COMPONENT_MANAGERS, "|");
        if (protectedComponentManagers.contains(callingPackage)) {
            if (DEBUG_PROTECTED) Log.d(TAG, "Calling package is a protected manager, allow");
            return false;
        }

        String packageName = componentName.getPackageName();
        String className = componentName.getClassName();

        //If this component is launched from the same package, allow it.
        if (TextUtils.equals(packageName, callingPackage)) {
            if (DEBUG_PROTECTED) Log.d(TAG, "Calling package is same as target, allow");
            return false;
        }

        //If this component is launched from a validation component, allow it.
        if (TextUtils.equals(PROTECTED_APPS_TARGET_VALIDATION_COMPONENT,
                componentName.flattenToString()) && callingUid == Process.SYSTEM_UID) {
            return false;
        }

        //If this component is launched from the system or a uid of a protected component, allow it.
        boolean fromProtectedComponentUid = false;
        for (String protectedComponentManager : protectedComponentManagers) {
            int packageUid = getPackageUid(protectedComponentManager,
                    MATCH_DEBUG_TRIAGED_MISSING, userId);
            if (packageUid != -1 && callingUid == packageUid) {
                fromProtectedComponentUid = true;
            }
        }

        if (TextUtils.equals(callingPackage, "android") && callingUid == Process.SYSTEM_UID
                || callingPackage == null && fromProtectedComponentUid) {
            if (DEBUG_PROTECTED) Log.d(TAG, "Calling package is android or manager, allow");
            return false;
        }

        PackageSetting pkgSetting;
        ArraySet<String> components;

        synchronized (mPackages) {
            pkgSetting = mSettings.mPackages.get(packageName);

            if (pkgSetting == null) {
                if (className == null) {
                    throw new IllegalArgumentException(
                            "Unknown package: " + packageName);
                }
                throw new IllegalArgumentException(
                        "Unknown component: " + packageName
                                + "/" + className);
            }
            // Get all the protected components
            components = pkgSetting.getProtectedComponents(userId);
            if (DEBUG_PROTECTED) Log.d(TAG, "Got " + components.size() + " protected components");
            return components.size() > 0;
        }
    }

    @Override
    public boolean isStorageLow() {
        final long token = Binder.clearCallingIdentity();
        try {
            final DeviceStorageMonitorInternal
                    dsm = LocalServices.getService(DeviceStorageMonitorInternal.class);
            if (dsm != null) {
                return dsm.isMemoryLow();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public IPackageInstaller getPackageInstaller() {
        return mInstallerService;
    }

    private boolean userNeedsBadging(int userId) {
        int index = mUserNeedsBadging.indexOfKey(userId);
        if (index < 0) {
            final UserInfo userInfo;
            final long token = Binder.clearCallingIdentity();
            try {
                userInfo = sUserManager.getUserInfo(userId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            final boolean b;
            if (userInfo != null && userInfo.isManagedProfile()) {
                b = true;
            } else {
                b = false;
            }
            mUserNeedsBadging.put(userId, b);
            return b;
        }
        return mUserNeedsBadging.valueAt(index);
    }

    @Override
    public KeySet getKeySetByAlias(String packageName, String alias) {
        if (packageName == null || alias == null) {
            return null;
        }
        synchronized(mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                Slog.w(TAG, "KeySet requested for unknown package: " + packageName);
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
            KeySetManagerService ksms = mSettings.mKeySetManagerService;
            return new KeySet(ksms.getKeySetByAliasAndPackageNameLPr(packageName, alias));
        }
    }

    @Override
    public KeySet getSigningKeySet(String packageName) {
        if (packageName == null) {
            return null;
        }
        synchronized(mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                Slog.w(TAG, "KeySet requested for unknown package: " + packageName);
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
            if (pkg.applicationInfo.uid != Binder.getCallingUid()
                    && Process.SYSTEM_UID != Binder.getCallingUid()) {
                throw new SecurityException("May not access signing KeySet of other apps.");
            }
            KeySetManagerService ksms = mSettings.mKeySetManagerService;
            return new KeySet(ksms.getSigningKeySetByPackageNameLPr(packageName));
        }
    }

    @Override
    public boolean isPackageSignedByKeySet(String packageName, KeySet ks) {
        if (packageName == null || ks == null) {
            return false;
        }
        synchronized(mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                Slog.w(TAG, "KeySet requested for unknown package: " + packageName);
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
            IBinder ksh = ks.getToken();
            if (ksh instanceof KeySetHandle) {
                KeySetManagerService ksms = mSettings.mKeySetManagerService;
                return ksms.packageIsSignedByLPr(packageName, (KeySetHandle) ksh);
            }
            return false;
        }
    }

    @Override
    public boolean isPackageSignedByKeySetExactly(String packageName, KeySet ks) {
        if (packageName == null || ks == null) {
            return false;
        }
        synchronized(mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                Slog.w(TAG, "KeySet requested for unknown package: " + packageName);
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
            IBinder ksh = ks.getToken();
            if (ksh instanceof KeySetHandle) {
                KeySetManagerService ksms = mSettings.mKeySetManagerService;
                return ksms.packageIsSignedByExactlyLPr(packageName, (KeySetHandle) ksh);
            }
            return false;
        }
    }

    private void deletePackageIfUnusedLPr(final String packageName) {
        PackageSetting ps = mSettings.mPackages.get(packageName);
        if (ps == null) {
            return;
        }
        if (!ps.isAnyInstalled(sUserManager.getUserIds())) {
            // TODO Implement atomic delete if package is unused
            // It is currently possible that the package will be deleted even if it is installed
            // after this method returns.
            mHandler.post(new Runnable() {
                public void run() {
                    deletePackageX(packageName, 0, PackageManager.DELETE_ALL_USERS);
                }
            });
        }
    }

    /**
     * Check and throw if the given before/after packages would be considered a
     * downgrade.
     */
    private static void checkDowngrade(PackageParser.Package before, PackageInfoLite after)
            throws PackageManagerException {
        if (after.versionCode < before.mVersionCode) {
            throw new PackageManagerException(INSTALL_FAILED_VERSION_DOWNGRADE,
                    "Update version code " + after.versionCode + " is older than current "
                    + before.mVersionCode);
        } else if (after.versionCode == before.mVersionCode) {
            if (after.baseRevisionCode < before.baseRevisionCode) {
                throw new PackageManagerException(INSTALL_FAILED_VERSION_DOWNGRADE,
                        "Update base revision code " + after.baseRevisionCode
                        + " is older than current " + before.baseRevisionCode);
            }

            if (!ArrayUtils.isEmpty(after.splitNames)) {
                for (int i = 0; i < after.splitNames.length; i++) {
                    final String splitName = after.splitNames[i];
                    final int j = ArrayUtils.indexOf(before.splitNames, splitName);
                    if (j != -1) {
                        if (after.splitRevisionCodes[i] < before.splitRevisionCodes[j]) {
                            throw new PackageManagerException(INSTALL_FAILED_VERSION_DOWNGRADE,
                                    "Update split " + splitName + " revision code "
                                    + after.splitRevisionCodes[i] + " is older than current "
                                    + before.splitRevisionCodes[j]);
                        }
                    }
                }
            }
        }
    }

    private static class MoveCallbacks extends Handler {
        private static final int MSG_CREATED = 1;
        private static final int MSG_STATUS_CHANGED = 2;

        private final RemoteCallbackList<IPackageMoveObserver>
                mCallbacks = new RemoteCallbackList<>();

        private final SparseIntArray mLastStatus = new SparseIntArray();

        public MoveCallbacks(Looper looper) {
            super(looper);
        }

        public void register(IPackageMoveObserver callback) {
            mCallbacks.register(callback);
        }

        public void unregister(IPackageMoveObserver callback) {
            mCallbacks.unregister(callback);
        }

        @Override
        public void handleMessage(Message msg) {
            final SomeArgs args = (SomeArgs) msg.obj;
            final int n = mCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                final IPackageMoveObserver callback = mCallbacks.getBroadcastItem(i);
                try {
                    invokeCallback(callback, msg.what, args);
                } catch (RemoteException ignored) {
                }
            }
            mCallbacks.finishBroadcast();
            args.recycle();
        }

        private void invokeCallback(IPackageMoveObserver callback, int what, SomeArgs args)
                throws RemoteException {
            switch (what) {
                case MSG_CREATED: {
                    callback.onCreated(args.argi1, (Bundle) args.arg2);
                    break;
                }
                case MSG_STATUS_CHANGED: {
                    callback.onStatusChanged(args.argi1, args.argi2, (long) args.arg3);
                    break;
                }
            }
        }

        private void notifyCreated(int moveId, Bundle extras) {
            Slog.v(TAG, "Move " + moveId + " created " + extras.toString());

            final SomeArgs args = SomeArgs.obtain();
            args.argi1 = moveId;
            args.arg2 = extras;
            obtainMessage(MSG_CREATED, args).sendToTarget();
        }

        private void notifyStatusChanged(int moveId, int status) {
            notifyStatusChanged(moveId, status, -1);
        }

        private void notifyStatusChanged(int moveId, int status, long estMillis) {
            Slog.v(TAG, "Move " + moveId + " status " + status);

            final SomeArgs args = SomeArgs.obtain();
            args.argi1 = moveId;
            args.argi2 = status;
            args.arg3 = estMillis;
            obtainMessage(MSG_STATUS_CHANGED, args).sendToTarget();

            synchronized (mLastStatus) {
                mLastStatus.put(moveId, status);
            }
        }
    }

    private final static class OnPermissionChangeListeners extends Handler {
        private static final int MSG_ON_PERMISSIONS_CHANGED = 1;

        private final RemoteCallbackList<IOnPermissionsChangeListener> mPermissionListeners =
                new RemoteCallbackList<>();

        public OnPermissionChangeListeners(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_PERMISSIONS_CHANGED: {
                    final int uid = msg.arg1;
                    handleOnPermissionsChanged(uid);
                } break;
            }
        }

        public void addListenerLocked(IOnPermissionsChangeListener listener) {
            mPermissionListeners.register(listener);

        }

        public void removeListenerLocked(IOnPermissionsChangeListener listener) {
            mPermissionListeners.unregister(listener);
        }

        public void onPermissionsChanged(int uid) {
            if (mPermissionListeners.getRegisteredCallbackCount() > 0) {
                obtainMessage(MSG_ON_PERMISSIONS_CHANGED, uid, 0).sendToTarget();
            }
        }

        private void handleOnPermissionsChanged(int uid) {
            final int count = mPermissionListeners.beginBroadcast();
            try {
                for (int i = 0; i < count; i++) {
                    IOnPermissionsChangeListener callback = mPermissionListeners
                            .getBroadcastItem(i);
                    try {
                        callback.onPermissionsChanged(uid);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Permission listener is dead", e);
                    }
                }
            } finally {
                mPermissionListeners.finishBroadcast();
            }
        }
    }

    private class PackageManagerInternalImpl extends PackageManagerInternal {
        @Override
        public void setLocationPackagesProvider(PackagesProvider provider) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.setLocationPackagesProviderLPw(provider);
            }
        }

        @Override
        public void setVoiceInteractionPackagesProvider(PackagesProvider provider) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.setVoiceInteractionPackagesProviderLPw(provider);
            }
        }

        @Override
        public void setSmsAppPackagesProvider(PackagesProvider provider) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.setSmsAppPackagesProviderLPw(provider);
            }
        }

        @Override
        public void setDialerAppPackagesProvider(PackagesProvider provider) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.setDialerAppPackagesProviderLPw(provider);
            }
        }

        @Override
        public void setSimCallManagerPackagesProvider(PackagesProvider provider) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.setSimCallManagerPackagesProviderLPw(provider);
            }
        }

        @Override
        public void setSyncAdapterPackagesprovider(SyncAdapterPackagesProvider provider) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.setSyncAdapterPackagesProviderLPw(provider);
            }
        }

        @Override
        public void grantDefaultPermissionsToDefaultSmsApp(String packageName, int userId) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.grantDefaultPermissionsToDefaultSmsAppLPr(
                        packageName, userId);
            }
        }

        @Override
        public void grantDefaultPermissionsToDefaultDialerApp(String packageName, int userId) {
            synchronized (mPackages) {
                mSettings.setDefaultDialerPackageNameLPw(packageName, userId);
                mDefaultPermissionPolicy.grantDefaultPermissionsToDefaultDialerAppLPr(
                        packageName, userId);
            }
        }

        @Override
        public void grantDefaultPermissionsToDefaultSimCallManager(String packageName, int userId) {
            synchronized (mPackages) {
                mDefaultPermissionPolicy.grantDefaultPermissionsToDefaultSimCallManagerLPr(
                        packageName, userId);
            }
        }

        @Override
        public void setKeepUninstalledPackages(final List<String> packageList) {
            Preconditions.checkNotNull(packageList);
            List<String> removedFromList = null;
            synchronized (mPackages) {
                if (mKeepUninstalledPackages != null) {
                    final int packagesCount = mKeepUninstalledPackages.size();
                    for (int i = 0; i < packagesCount; i++) {
                        String oldPackage = mKeepUninstalledPackages.get(i);
                        if (packageList != null && packageList.contains(oldPackage)) {
                            continue;
                        }
                        if (removedFromList == null) {
                            removedFromList = new ArrayList<>();
                        }
                        removedFromList.add(oldPackage);
                    }
                }
                mKeepUninstalledPackages = new ArrayList<>(packageList);
                if (removedFromList != null) {
                    final int removedCount = removedFromList.size();
                    for (int i = 0; i < removedCount; i++) {
                        deletePackageIfUnusedLPr(removedFromList.get(i));
                    }
                }
            }
        }

        @Override
        public boolean isPermissionsReviewRequired(String packageName, int userId) {
            synchronized (mPackages) {
                // If we do not support permission review, done.
                if (!Build.PERMISSIONS_REVIEW_REQUIRED) {
                    return false;
                }

                PackageSetting packageSetting = mSettings.mPackages.get(packageName);
                if (packageSetting == null) {
                    return false;
                }

                // Permission review applies only to apps not supporting the new permission model.
                if (packageSetting.pkg.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M) {
                    return false;
                }

                // Legacy apps have the permission and get user consent on launch.
                PermissionsState permissionsState = packageSetting.getPermissionsState();
                return permissionsState.isPermissionReviewRequired(userId);
            }
        }

        @Override
        public ApplicationInfo getApplicationInfo(String packageName, int userId) {
            return PackageManagerService.this.getApplicationInfo(packageName, 0 /*flags*/, userId);
        }

        @Override
        public ComponentName getHomeActivitiesAsUser(List<ResolveInfo> allHomeCandidates,
                int userId) {
            return PackageManagerService.this.getHomeActivitiesAsUser(allHomeCandidates, userId);
        }

        @Override
        public void setDeviceAndProfileOwnerPackages(
                int deviceOwnerUserId, String deviceOwnerPackage,
                SparseArray<String> profileOwnerPackages) {
            mProtectedPackages.setDeviceAndProfileOwnerPackages(
                    deviceOwnerUserId, deviceOwnerPackage, profileOwnerPackages);
        }

        @Override
        public boolean isPackageDataProtected(int userId, String packageName) {
            return mProtectedPackages.isPackageDataProtected(userId, packageName);
        }
    }

    @Override
    public void grantDefaultPermissionsToEnabledCarrierApps(String[] packageNames, int userId) {
        enforceSystemOrPhoneCaller("grantPermissionsToEnabledCarrierApps");
        synchronized (mPackages) {
            final long identity = Binder.clearCallingIdentity();
            try {
                mDefaultPermissionPolicy.grantDefaultPermissionsToEnabledCarrierAppsLPr(
                        packageNames, userId);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private static void enforceSystemOrPhoneCaller(String tag) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.PHONE_UID && callingUid != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "Cannot call " + tag + " from UID " + callingUid);
        }
    }

    boolean isHistoricalPackageUsageAvailable() {
        return mPackageUsage.isHistoricalPackageUsageAvailable();
    }

    /**
     * Return a <b>copy</b> of the collection of packages known to the package manager.
     * @return A copy of the values of mPackages.
     */
    Collection<PackageParser.Package> getPackages() {
        synchronized (mPackages) {
            return new ArrayList<>(mPackages.values());
        }
    }

    /**
     * Logs process start information (including base APK hash) to the security log.
     * @hide
     */
    public void logAppProcessStartIfNeeded(String processName, int uid, String seinfo,
            String apkFile, int pid) {
        if (!SecurityLog.isLoggingEnabled()) {
            return;
        }
        Bundle data = new Bundle();
        data.putLong("startTimestamp", System.currentTimeMillis());
        data.putString("processName", processName);
        data.putInt("uid", uid);
        data.putString("seinfo", seinfo);
        data.putString("apkFile", apkFile);
        data.putInt("pid", pid);
        Message msg = mProcessLoggingHandler.obtainMessage(
                ProcessLoggingHandler.LOG_APP_PROCESS_START_MSG);
        msg.setData(data);
        mProcessLoggingHandler.sendMessage(msg);
    }

    public CompilerStats.PackageStats getCompilerPackageStats(String pkgName) {
        return mCompilerStats.getPackageStats(pkgName);
    }

    public CompilerStats.PackageStats getOrCreateCompilerPackageStats(PackageParser.Package pkg) {
        return getOrCreateCompilerPackageStats(pkg.packageName);
    }

    public CompilerStats.PackageStats getOrCreateCompilerPackageStats(String pkgName) {
        return mCompilerStats.getOrCreatePackageStats(pkgName);
    }

    public void deleteCompilerPackageStats(String pkgName) {
        mCompilerStats.deletePackageStats(pkgName);
    }
}
