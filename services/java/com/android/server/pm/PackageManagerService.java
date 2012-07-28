/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.pm;

import static android.Manifest.permission.GRANT_REVOKE_PERMISSIONS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static com.android.internal.util.ArrayUtils.appendInt;
import static com.android.internal.util.ArrayUtils.removeInt;
import static libcore.io.OsConstants.S_ISLNK;

import com.android.internal.app.IAssetRedirectionManager;
import com.android.internal.app.IMediaContainerService;
import com.android.internal.app.ResolverActivity;
import com.android.internal.content.NativeLibraryHelper;
import com.android.internal.content.PackageHelper;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;
import com.android.server.DeviceStorageMonitorService;
import com.android.server.EventLogTags;
import com.android.server.IntentResolver;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.admin.IDevicePolicyManager;
import android.app.backup.IBackupManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.IntentSender.SendIntentException;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ContainerEncryptionParams;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInfoLite;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PackageStats;
import android.content.pm.ParceledListSlice;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.content.pm.ManifestDigest;
import android.content.pm.VerifierDeviceIdentity;
import android.content.pm.VerifierInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserId;
import android.provider.Settings.Secure;
import android.security.SystemKeyStore;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.LogPrinter;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import libcore.io.ErrnoException;
import libcore.io.IoUtils;
import libcore.io.Libcore;

/**
 * Keep track of all those .apks everywhere.
 * 
 * This is very central to the platform's security; please run the unit
 * tests whenever making modifications here:
 * 
mmm frameworks/base/tests/AndroidTests
adb install -r -f out/target/product/passion/data/app/AndroidTests.apk
adb shell am instrument -w -e class com.android.unit_tests.PackageManagerTests com.android.unit_tests/android.test.InstrumentationTestRunner
 * 
 * {@hide}
 */
public class PackageManagerService extends IPackageManager.Stub {
    static final String TAG = "PackageManager";
    static final boolean DEBUG_SETTINGS = false;
    private static final boolean DEBUG_PREFERRED = false;
    static final boolean DEBUG_UPGRADE = false;
    private static final boolean DEBUG_INSTALL = false;
    private static final boolean DEBUG_REMOVE = false;
    private static final boolean DEBUG_SHOW_INFO = false;
    private static final boolean DEBUG_PACKAGE_INFO = false;
    private static final boolean DEBUG_INTENT_MATCHING = false;
    private static final boolean DEBUG_PACKAGE_SCANNING = false;
    private static final boolean DEBUG_APP_DIR_OBSERVER = false;
    private static final boolean DEBUG_VERIFY = false;

    private static final int RADIO_UID = Process.PHONE_UID;
    private static final int LOG_UID = Process.LOG_UID;
    private static final int NFC_UID = Process.NFC_UID;

    private static final boolean GET_CERTIFICATES = true;

    private static final int REMOVE_EVENTS =
        FileObserver.CLOSE_WRITE | FileObserver.DELETE | FileObserver.MOVED_FROM;
    private static final int ADD_EVENTS =
        FileObserver.CLOSE_WRITE /*| FileObserver.CREATE*/ | FileObserver.MOVED_TO;

    private static final int OBSERVER_EVENTS = REMOVE_EVENTS | ADD_EVENTS;
    // Suffix used during package installation when copying/moving
    // package apks to install directory.
    private static final String INSTALL_PACKAGE_SUFFIX = "-";

    private static final int THEME_MAMANER_GUID = 1300;

    static final int SCAN_MONITOR = 1<<0;
    static final int SCAN_NO_DEX = 1<<1;
    static final int SCAN_FORCE_DEX = 1<<2;
    static final int SCAN_UPDATE_SIGNATURE = 1<<3;
    static final int SCAN_NEW_INSTALL = 1<<4;
    static final int SCAN_NO_PATHS = 1<<5;
    static final int SCAN_UPDATE_TIME = 1<<6;
    static final int SCAN_DEFER_DEX = 1<<7;
    static final int SCAN_BOOTING = 1<<8;

    static final int REMOVE_CHATTY = 1<<16;

    /**
     * Whether verification is enabled by default.
     */
    // STOPSHIP: change this to true
    private static final boolean DEFAULT_VERIFY_ENABLE = false;

    /**
     * The default maximum time to wait for the verification agent to return in
     * milliseconds.
     */
    private static final long DEFAULT_VERIFICATION_TIMEOUT = 60 * 1000;

    static final String DEFAULT_CONTAINER_PACKAGE = "com.android.defcontainer";

    static final ComponentName DEFAULT_CONTAINER_COMPONENT = new ComponentName(
            DEFAULT_CONTAINER_PACKAGE,
            "com.android.defcontainer.DefaultContainerService");

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private static final String LIB_DIR_NAME = "lib";

    static final String mTempContainerPrefix = "smdl2tmp";

    final HandlerThread mHandlerThread = new HandlerThread("PackageManager",
            Process.THREAD_PRIORITY_BACKGROUND);
    final PackageHandler mHandler;

    final int mSdkVersion = Build.VERSION.SDK_INT;
    final String mSdkCodename = "REL".equals(Build.VERSION.CODENAME)
            ? null : Build.VERSION.CODENAME;

    final Context mContext;
    final boolean mFactoryTest;
    final boolean mOnlyCore;
    final boolean mNoDexOpt;
    final DisplayMetrics mMetrics;
    final int mDefParseFlags;
    final String[] mSeparateProcesses;

    // This is where all application persistent data goes.
    final File mAppDataDir;

    // This is where all application persistent data goes for secondary users.
    final File mUserAppDataDir;

    /** The location for ASEC container files on internal storage. */
    final String mAsecInternalPath;

    // This is the object monitoring the framework dir.
    final FileObserver mFrameworkInstallObserver;

    // This is the object monitoring the system app dir.
    final FileObserver mSystemInstallObserver;

    // This is the object monitoring the system app dir.
    final FileObserver mVendorInstallObserver;

    // This is the object monitoring mAppInstallDir.
    final FileObserver mAppInstallObserver;

    // This is the object monitoring mDrmAppPrivateInstallDir.
    final FileObserver mDrmAppInstallObserver;

    // Used for priviledge escalation.  MUST NOT BE CALLED WITH mPackages
    // LOCK HELD.  Can be called with mInstallLock held.
    final Installer mInstaller;

    final File mFrameworkDir;
    final File mSystemAppDir;
    final File mVendorAppDir;
    final File mAppInstallDir;
    final File mDalvikCacheDir;

    // Directory containing the private parts (e.g. code and non-resource assets) of forward-locked
    // apps.
    final File mDrmAppPrivateInstallDir;

    // ----------------------------------------------------------------

    // Lock for state used when installing and doing other long running
    // operations.  Methods that must be called with this lock held have
    // the prefix "LI".
    final Object mInstallLock = new Object();

    // These are the directories in the 3rd party applications installed dir
    // that we have currently loaded packages from.  Keys are the application's
    // installed zip file (absolute codePath), and values are Package.
    final HashMap<String, PackageParser.Package> mAppDirs =
            new HashMap<String, PackageParser.Package>();

    // Information for the parser to write more useful error messages.
    File mScanningPath;
    int mLastScanError;

    final int[] mOutPermissions = new int[3];

    // ----------------------------------------------------------------

    // Keys are String (package name), values are Package.  This also serves
    // as the lock for the global state.  Methods that must be called with
    // this lock held have the prefix "LP".
    final HashMap<String, PackageParser.Package> mPackages =
            new HashMap<String, PackageParser.Package>();

    final Settings mSettings;
    boolean mRestoredSettings;

    // Group-ids that are given to all packages as read from etc/permissions/*.xml.
    int[] mGlobalGids;

    // These are the built-in uid -> permission mappings that were read from the
    // etc/permissions.xml file.
    final SparseArray<HashSet<String>> mSystemPermissions =
            new SparseArray<HashSet<String>>();

    // These are the built-in shared libraries that were read from the
    // etc/permissions.xml file.
    final HashMap<String, String> mSharedLibraries = new HashMap<String, String>();

    // Temporary for building the final shared libraries for an .apk.
    String[] mTmpSharedLibraries = null;

    // These are the features this devices supports that were read from the
    // etc/permissions.xml file.
    final HashMap<String, FeatureInfo> mAvailableFeatures =
            new HashMap<String, FeatureInfo>();

    // All available activities, for your resolving pleasure.
    final ActivityIntentResolver mActivities =
            new ActivityIntentResolver();

    // All available receivers, for your resolving pleasure.
    final ActivityIntentResolver mReceivers =
            new ActivityIntentResolver();

    // All available services, for your resolving pleasure.
    final ServiceIntentResolver mServices = new ServiceIntentResolver();

    // Keys are String (provider class name), values are Provider.
    final HashMap<ComponentName, PackageParser.Provider> mProvidersByComponent =
            new HashMap<ComponentName, PackageParser.Provider>();

    // Mapping from provider base names (first directory in content URI codePath)
    // to the provider information.
    final HashMap<String, PackageParser.Provider> mProviders =
            new HashMap<String, PackageParser.Provider>();

    // Mapping from instrumentation class names to info about them.
    final HashMap<ComponentName, PackageParser.Instrumentation> mInstrumentation =
            new HashMap<ComponentName, PackageParser.Instrumentation>();

    // Mapping from permission names to info about them.
    final HashMap<String, PackageParser.PermissionGroup> mPermissionGroups =
            new HashMap<String, PackageParser.PermissionGroup>();

    // Packages whose data we have transfered into another package, thus
    // should no longer exist.
    final HashSet<String> mTransferedPackages = new HashSet<String>();
    
    // Broadcast actions that are only available to the system.
    final HashSet<String> mProtectedBroadcasts = new HashSet<String>();

    /** List of packages waiting for verification. */
    final SparseArray<PackageVerificationState> mPendingVerification
            = new SparseArray<PackageVerificationState>();

    final ArrayList<PackageParser.Package> mDeferredDexOpt =
            new ArrayList<PackageParser.Package>();

    /** Token for keys in mPendingVerification. */
    private int mPendingVerificationToken = 0;

    boolean mSystemReady;
    boolean mSafeMode;
    boolean mHasSystemUidErrors;

    ApplicationInfo mAndroidApplication;
    final ActivityInfo mResolveActivity = new ActivityInfo();
    final ResolveInfo mResolveInfo = new ResolveInfo();
    ComponentName mResolveComponentName;
    PackageParser.Package mPlatformPackage;

    IAssetRedirectionManager mAssetRedirectionManager;

    // Set of pending broadcasts for aggregating enable/disable of components.
    final HashMap<String, ArrayList<String>> mPendingBroadcasts
            = new HashMap<String, ArrayList<String>>();
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

    static final int WRITE_SETTINGS_DELAY = 10*1000;  // 10 seconds

    // Delay time in millisecs
    static final int BROADCAST_DELAY = 10 * 1000;

    static UserManager sUserManager;

    // Stores a list of users whose package restrictions file needs to be updated
    private HashSet<Integer> mDirtyUsers = new HashSet<Integer>();

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
    };

    // Recordkeeping of restore-after-install operations that are currently in flight
    // between the Package Manager and the Backup Manager
    class PostInstallData {
        public InstallArgs args;
        public PackageInstalledInfo res;

        PostInstallData(InstallArgs _a, PackageInstalledInfo _r) {
            args = _a;
            res = _r;
        }
    };
    final SparseArray<PostInstallData> mRunningInstalls = new SparseArray<PostInstallData>();
    int mNextInstallToken = 1;  // nonzero; will be wrapped back to 1 when ++ overflows

    private final String mRequiredVerifierPackage;

    class PackageHandler extends Handler {
        private boolean mBound = false;
        final ArrayList<HandlerParams> mPendingInstalls =
            new ArrayList<HandlerParams>();

        private boolean connectToService() {
            if (DEBUG_SD_INSTALL) Log.i(TAG, "Trying to bind to" +
                    " DefaultContainerService");
            Intent service = new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT);
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
            if (mContext.bindService(service, mDefContainerConn,
                    Context.BIND_AUTO_CREATE)) {
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
                    if (DEBUG_INSTALL) Slog.i(TAG, "init_copy");
                    HandlerParams params = (HandlerParams) msg.obj;
                    int idx = mPendingInstalls.size();
                    if (DEBUG_INSTALL) Slog.i(TAG, "idx=" + idx);
                    // If a bind was already initiated we dont really
                    // need to do anything. The pending install
                    // will be processed later on.
                    if (!mBound) {
                        // If this is the only one pending we might
                        // have to bind to the service again.
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            params.serviceError();
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
                    }
                    if (mContainerService == null) {
                        // Something seriously wrong. Bail out
                        Slog.e(TAG, "Cannot bind to media container service");
                        for (HandlerParams params : mPendingInstalls) {
                            // Indicate service bind error
                            params.serviceError();
                        }
                        mPendingInstalls.clear();
                    } else if (mPendingInstalls.size() > 0) {
                        HandlerParams params = mPendingInstalls.get(0);
                        if (params != null) {
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
                                            "Posting MCS_BOUND for next woek");
                                    mHandler.sendEmptyMessage(MCS_BOUND);
                                }
                            }
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
                    mPendingInstalls.remove(0);
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
                        Iterator<Map.Entry<String, ArrayList<String>>>
                                it = mPendingBroadcasts.entrySet().iterator();
                        int i = 0;
                        while (it.hasNext() && i < size) {
                            Map.Entry<String, ArrayList<String>> ent = it.next();
                            packages[i] = ent.getKey();
                            components[i] = ent.getValue();
                            PackageSetting ps = mSettings.mPackages.get(ent.getKey());
                            uids[i] = (ps != null) ? ps.appId : -1;
                            i++;
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
                    String packageName = (String)msg.obj;
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                    synchronized (mPackages) {
                        if (!mSettings.mPackagesToBeCleaned.contains(packageName)) {
                            mSettings.mPackagesToBeCleaned.add(packageName);
                        }
                    }
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    startCleaningPackages();
                } break;
                case POST_INSTALL: {
                    if (DEBUG_INSTALL) Log.v(TAG, "Handling post-install for " + msg.arg1);
                    PostInstallData data = mRunningInstalls.get(msg.arg1);
                    mRunningInstalls.delete(msg.arg1);
                    boolean deleteOld = false;

                    if (data != null) {
                        InstallArgs args = data.args;
                        PackageInstalledInfo res = data.res;

                        if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
                            res.removedInfo.sendBroadcast(false, true, false);
                            Bundle extras = new Bundle(1);
                            extras.putInt(Intent.EXTRA_UID, res.uid);
                            final boolean update = res.removedInfo.removedPackage != null;
                            if (update) {
                                extras.putBoolean(Intent.EXTRA_REPLACING, true);
                            }
                            String category = null;
                            if(res.pkg.mIsThemeApk) {
                                category = Intent.CATEGORY_THEME_PACKAGE_INSTALLED_STATE_CHANGE;
                            }
                            sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED,
                                    res.pkg.applicationInfo.packageName, category,
                                    extras, null, null, UserId.USER_ALL);
                            if (update) {
                                sendPackageBroadcast(Intent.ACTION_PACKAGE_REPLACED,
                                        res.pkg.applicationInfo.packageName, category,
                                        extras, null, null, UserId.USER_ALL);
                                sendPackageBroadcast(Intent.ACTION_MY_PACKAGE_REPLACED,
                                        null, null, null,
                                        res.pkg.applicationInfo.packageName, null,
                                        UserId.USER_ALL);
                            }
                            if (res.removedInfo.args != null) {
                                // Remove the replaced package's older resources safely now
                                deleteOld = true;
                            }
                        }
                        // Force a gc to clear up things
                        Runtime.getRuntime().gc();
                        // We delete after a gc for applications  on sdcard.
                        if (deleteOld) {
                            synchronized (mInstallLock) {
                                res.removedInfo.args.doPostDeleteLI(true);
                            }
                        }
                        if (args.observer != null) {
                            try {
                                args.observer.packageInstalled(res.name, res.returnCode);
                            } catch (RemoteException e) {
                                Slog.i(TAG, "Observer no longer exists.");
                            }
                        }
                    } else {
                        Slog.e(TAG, "Bogus post-install token " + msg.arg1);
                    }
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
                case CHECK_PENDING_VERIFICATION: {
                    final int verificationId = msg.arg1;
                    final PackageVerificationState state = mPendingVerification.get(verificationId);

                    if (state != null) {
                        final InstallArgs args = state.getInstallArgs();
                        Slog.i(TAG, "Verification timed out for " + args.packageURI.toString());
                        mPendingVerification.remove(verificationId);

                        int ret = PackageManager.INSTALL_FAILED_VERIFICATION_TIMEOUT;
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

                        int ret;
                        if (state.isInstallAllowed()) {
                            ret = PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
                            try {
                                ret = args.copyApk(mContainerService, true);
                            } catch (RemoteException e) {
                                Slog.e(TAG, "Could not contact the ContainerService");
                            }
                        } else {
                            ret = PackageManager.INSTALL_FAILED_VERIFICATION_FAILURE;
                        }

                        processPendingInstall(args, ret);

                        mHandler.sendEmptyMessage(MCS_UNBIND);
                    }

                    break;
                }
            }
        }
    }

    void scheduleWriteSettingsLocked() {
        if (!mHandler.hasMessages(WRITE_SETTINGS)) {
            mHandler.sendEmptyMessageDelayed(WRITE_SETTINGS, WRITE_SETTINGS_DELAY);
        }
    }

    void scheduleWritePackageRestrictionsLocked(int userId) {
        if (!sUserManager.exists(userId)) return;
        mDirtyUsers.add(userId);
        if (!mHandler.hasMessages(WRITE_PACKAGE_RESTRICTIONS)) {
            mHandler.sendEmptyMessageDelayed(WRITE_PACKAGE_RESTRICTIONS, WRITE_SETTINGS_DELAY);
        }
    }

    public static final IPackageManager main(Context context, boolean factoryTest,
            boolean onlyCore) {
        PackageManagerService m = new PackageManagerService(context, factoryTest, onlyCore);
        ServiceManager.addService("package", m);
        return m;
    }

    static String[] splitString(String str, char sep) {
        int count = 1;
        int i = 0;
        while ((i=str.indexOf(sep, i)) >= 0) {
            count++;
            i++;
        }

        String[] res = new String[count];
        i=0;
        count = 0;
        int lastI=0;
        while ((i=str.indexOf(sep, i)) >= 0) {
            res[count] = str.substring(lastI, i);
            count++;
            i++;
            lastI = i;
        }
        res[count] = str.substring(lastI, str.length());
        return res;
    }

    public PackageManagerService(Context context, boolean factoryTest, boolean onlyCore) {
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_START,
                SystemClock.uptimeMillis());

        if (mSdkVersion <= 0) {
            Slog.w(TAG, "**** ro.build.version.sdk not set!");
        }

        mContext = context;
        mFactoryTest = factoryTest;
        mOnlyCore = onlyCore;
        mNoDexOpt = "eng".equals(SystemProperties.get("ro.build.type"));
        mMetrics = new DisplayMetrics();
        mSettings = new Settings();
        mSettings.addSharedUserLPw("android.uid.system",
                Process.SYSTEM_UID, ApplicationInfo.FLAG_SYSTEM);
        mSettings.addSharedUserLPw("android.uid.phone", RADIO_UID, ApplicationInfo.FLAG_SYSTEM);
        mSettings.addSharedUserLPw("android.uid.log", LOG_UID, ApplicationInfo.FLAG_SYSTEM);
        mSettings.addSharedUserLPw("com.tmobile.thememanager", THEME_MAMANER_GUID, ApplicationInfo.FLAG_SYSTEM);
        mSettings.addSharedUserLPw("android.uid.nfc", NFC_UID, ApplicationInfo.FLAG_SYSTEM);

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

        mInstaller = new Installer();

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        d.getMetrics(mMetrics);

        synchronized (mInstallLock) {
        // writer
        synchronized (mPackages) {
            mHandlerThread.start();
            mHandler = new PackageHandler(mHandlerThread.getLooper());

            File dataDir = Environment.getDataDirectory();
            mAppDataDir = new File(dataDir, "data");
            mAsecInternalPath = new File(dataDir, "app-asec").getPath();
            mUserAppDataDir = new File(dataDir, "user");
            mDrmAppPrivateInstallDir = new File(dataDir, "app-private");

            sUserManager = new UserManager(mInstaller, mUserAppDataDir);

            readPermissions();

            mRestoredSettings = mSettings.readLPw(getUsers());
            long startTime = SystemClock.uptimeMillis();

            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_SYSTEM_SCAN_START,
                    startTime);

            // Set flag to monitor and not change apk file paths when
            // scanning install directories.
            int scanMode = SCAN_MONITOR | SCAN_NO_PATHS | SCAN_DEFER_DEX | SCAN_BOOTING;
            if (mNoDexOpt) {
                Slog.w(TAG, "Running ENG build: no pre-dexopt!");
                scanMode |= SCAN_NO_DEX;
            }

            final HashSet<String> libFiles = new HashSet<String>();

            mFrameworkDir = new File(Environment.getRootDirectory(), "framework");
            mDalvikCacheDir = new File(dataDir, "dalvik-cache");

            boolean didDexOpt = false;

            /**
             * Out of paranoia, ensure that everything in the boot class
             * path has been dexed.
             */
            String bootClassPath = System.getProperty("java.boot.class.path");
            if (bootClassPath != null) {
                String[] paths = splitString(bootClassPath, ':');
                for (int i=0; i<paths.length; i++) {
                    try {
                        if (dalvik.system.DexFile.isDexOptNeeded(paths[i])) {
                            libFiles.add(paths[i]);
                            mInstaller.dexopt(paths[i], Process.SYSTEM_UID, true);
                            didDexOpt = true;
                        }
                    } catch (FileNotFoundException e) {
                        Slog.w(TAG, "Boot class path not found: " + paths[i]);
                    } catch (IOException e) {
                        Slog.w(TAG, "Cannot dexopt " + paths[i] + "; is it an APK or JAR? "
                                + e.getMessage());
                    }
                }
            } else {
                Slog.w(TAG, "No BOOTCLASSPATH found!");
            }

            /**
             * Also ensure all external libraries have had dexopt run on them.
             */
            if (mSharedLibraries.size() > 0) {
                Iterator<String> libs = mSharedLibraries.values().iterator();
                while (libs.hasNext()) {
                    String lib = libs.next();
                    try {
                        if (dalvik.system.DexFile.isDexOptNeeded(lib)) {
                            libFiles.add(lib);
                            mInstaller.dexopt(lib, Process.SYSTEM_UID, true);
                            didDexOpt = true;
                        }
                    } catch (FileNotFoundException e) {
                        Slog.w(TAG, "Library not found: " + lib);
                    } catch (IOException e) {
                        Slog.w(TAG, "Cannot dexopt " + lib + "; is it an APK or JAR? "
                                + e.getMessage());
                    }
                }
            }

            // Gross hack for now: we know this file doesn't contain any
            // code, so don't dexopt it to avoid the resulting log spew.
            libFiles.add(mFrameworkDir.getPath() + "/framework-res.apk");

            /**
             * And there are a number of commands implemented in Java, which
             * we currently need to do the dexopt on so that they can be
             * run from a non-root shell.
             */
            String[] frameworkFiles = mFrameworkDir.list();
            if (frameworkFiles != null) {
                for (int i=0; i<frameworkFiles.length; i++) {
                    File libPath = new File(mFrameworkDir, frameworkFiles[i]);
                    String path = libPath.getPath();
                    // Skip the file if we alrady did it.
                    if (libFiles.contains(path)) {
                        continue;
                    }
                    // Skip the file if it is not a type we want to dexopt.
                    if (!path.endsWith(".apk") && !path.endsWith(".jar")) {
                        continue;
                    }
                    try {
                        if (dalvik.system.DexFile.isDexOptNeeded(path)) {
                            mInstaller.dexopt(path, Process.SYSTEM_UID, true);
                            didDexOpt = true;
                        }
                    } catch (FileNotFoundException e) {
                        Slog.w(TAG, "Jar not found: " + path);
                    } catch (IOException e) {
                        Slog.w(TAG, "Exception reading jar: " + path, e);
                    }
                }
            }

            if (didDexOpt) {
                // If we had to do a dexopt of one of the previous
                // things, then something on the system has changed.
                // Consider this significant, and wipe away all other
                // existing dexopt files to ensure we don't leave any
                // dangling around.
                String[] files = mDalvikCacheDir.list();
                if (files != null) {
                    for (int i=0; i<files.length; i++) {
                        String fn = files[i];
                        if (fn.startsWith("data@app@")
                                || fn.startsWith("data@app-private@")) {
                            Slog.i(TAG, "Pruning dalvik file: " + fn);
                            (new File(mDalvikCacheDir, fn)).delete();
                        }
                    }
                }
            }

            // Find base frameworks (resource packages without code).
            mFrameworkInstallObserver = new AppDirObserver(
                mFrameworkDir.getPath(), OBSERVER_EVENTS, true);
            mFrameworkInstallObserver.startWatching();
            scanDirLI(mFrameworkDir, PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR,
                    scanMode | SCAN_NO_DEX, 0);

            // Collect all system packages.
            mSystemAppDir = new File(Environment.getRootDirectory(), "app");
            mSystemInstallObserver = new AppDirObserver(
                mSystemAppDir.getPath(), OBSERVER_EVENTS, true);
            mSystemInstallObserver.startWatching();
            scanDirLI(mSystemAppDir, PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanMode, 0);

            // Collect all vendor packages.
            mVendorAppDir = new File("/vendor/app");
            mVendorInstallObserver = new AppDirObserver(
                mVendorAppDir.getPath(), OBSERVER_EVENTS, true);
            mVendorInstallObserver.startWatching();
            scanDirLI(mVendorAppDir, PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanMode, 0);

            if (DEBUG_UPGRADE) Log.v(TAG, "Running installd update commands");
            mInstaller.moveFiles();

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
                            Slog.i(TAG, "Expecting better updatd system app for " + ps.name
                                    + "; removing system app");
                            removePackageLI(scannedPkg, true);
                        }

                        continue;
                    }

                    if (!mSettings.isDisabledSystemPackageLPr(ps.name)) {
                        psit.remove();
                        String msg = "System package " + ps.name
                                + " no longer exists; wiping its data";
                        reportSettingsProblem(Log.WARN, msg);
                        mInstaller.remove(ps.name, 0);
                        sUserManager.removePackageForAllUsers(ps.name);
                    } else {
                        final PackageSetting disabledPs = mSettings.getDisabledSystemPkgLPr(ps.name);
                        if (disabledPs.codePath == null || !disabledPs.codePath.exists()) {
                            possiblyDeletedUpdatedSystemApps.add(ps.name);
                        }
                    }
                }
            }

            mAppInstallDir = new File(dataDir, "app");
            //look for any incomplete package installations
            ArrayList<PackageSetting> deletePkgsList = mSettings.getListOfIncompleteInstallPackagesLPr();
            //clean up list
            for(int i = 0; i < deletePkgsList.size(); i++) {
                //clean up here
                cleanupInstallFailedPackage(deletePkgsList.get(i));
            }
            //delete tmp files
            deleteTempPackageFiles();

            if (!mOnlyCore) {
                EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_DATA_SCAN_START,
                        SystemClock.uptimeMillis());
                mAppInstallObserver = new AppDirObserver(
                    mAppInstallDir.getPath(), OBSERVER_EVENTS, false);
                mAppInstallObserver.startWatching();
                scanDirLI(mAppInstallDir, 0, scanMode, 0);
    
                mDrmAppInstallObserver = new AppDirObserver(
                    mDrmAppPrivateInstallDir.getPath(), OBSERVER_EVENTS, false);
                mDrmAppInstallObserver.startWatching();
                scanDirLI(mDrmAppPrivateInstallDir, PackageParser.PARSE_FORWARD_LOCK,
                        scanMode, 0);

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
                                + " no longer exists; wiping its data";

                        mInstaller.remove(deletedAppName, 0);
                        sUserManager.removePackageForAllUsers(deletedAppName);
                    } else {
                        msg = "Updated system app + " + deletedAppName
                                + " no longer present; removing system privileges for "
                                + deletedAppName;

                        deletedPkg.applicationInfo.flags &= ~ApplicationInfo.FLAG_SYSTEM;

                        PackageSetting deletedPs = mSettings.mPackages.get(deletedAppName);
                        deletedPs.pkgFlags &= ~ApplicationInfo.FLAG_SYSTEM;
                    }
                    reportSettingsProblem(Log.WARN, msg);
                }
            } else {
                mAppInstallObserver = null;
                mDrmAppInstallObserver = null;
            }

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
            final boolean regrantPermissions = mSettings.mInternalSdkPlatform
                    != mSdkVersion;
            if (regrantPermissions) Slog.i(TAG, "Platform changed from "
                    + mSettings.mInternalSdkPlatform + " to " + mSdkVersion
                    + "; regranting permissions for internal storage");
            mSettings.mInternalSdkPlatform = mSdkVersion;
            
            updatePermissionsLPw(null, null, UPDATE_PERMISSIONS_ALL
                    | (regrantPermissions
                            ? (UPDATE_PERMISSIONS_REPLACE_PKG|UPDATE_PERMISSIONS_REPLACE_ALL)
                            : 0));

            // Verify that all of the preferred activity components actually
            // exist.  It is possible for applications to be updated and at
            // that point remove a previously declared activity component that
            // had been set as a preferred activity.  We try to clean this up
            // the next time we encounter that preferred activity, but it is
            // possible for the user flow to never be able to return to that
            // situation so here we do a sanity check to make sure we haven't
            // left any junk around.
            ArrayList<PreferredActivity> removed = new ArrayList<PreferredActivity>();
            for (PreferredActivity pa : mSettings.mPreferredActivities.filterSet()) {
                if (mActivities.mActivities.get(pa.mPref.mComponent) == null) {
                    removed.add(pa);
                }
            }
            for (int i=0; i<removed.size(); i++) {
                PreferredActivity pa = removed.get(i);
                Slog.w(TAG, "Removing dangling preferred activity: "
                        + pa.mPref.mComponent);
                mSettings.mPreferredActivities.removeFilter(pa);
            }

            // can downgrade to reader
            mSettings.writeLPr();

            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_READY,
                    SystemClock.uptimeMillis());

            // Now after opening every single application zip, make sure they
            // are all flushed.  Not really needed, but keeps things nice and
            // tidy.
            Runtime.getRuntime().gc();

            mRequiredVerifierPackage = getRequiredVerifierLPr();
        } // synchronized (mPackages)
        } // synchronized (mInstallLock)
    }

    public boolean isFirstBoot() {
        return !mRestoredSettings;
    }

    private String getRequiredVerifierLPr() {
        final Intent verification = new Intent(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
        final List<ResolveInfo> receivers = queryIntentReceivers(verification, PACKAGE_MIME_TYPE,
                PackageManager.GET_DISABLED_COMPONENTS, 0 /* TODO: Which userId? */);

        String requiredVerifier = null;

        final int N = receivers.size();
        for (int i = 0; i < N; i++) {
            final ResolveInfo info = receivers.get(i);

            if (info.activityInfo == null) {
                continue;
            }

            final String packageName = info.activityInfo.packageName;

            final PackageSetting ps = mSettings.mPackages.get(packageName);
            if (ps == null) {
                continue;
            }

            if (!ps.grantedPermissions
                    .contains(android.Manifest.permission.PACKAGE_VERIFICATION_AGENT)) {
                continue;
            }

            if (requiredVerifier != null) {
                throw new RuntimeException("There can be only one required verifier");
            }

            requiredVerifier = packageName;
        }

        return requiredVerifier;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            if (!(e instanceof SecurityException) && !(e instanceof IllegalArgumentException)) {
                Slog.e(TAG, "Package Manager Crash", e);
            }
            throw e;
        }
    }

    void cleanupInstallFailedPackage(PackageSetting ps) {
        Slog.i(TAG, "Cleaning up incompletely installed app: " + ps.name);
        int retCode = mInstaller.remove(ps.name, 0);
        if (retCode < 0) {
            Slog.w(TAG, "Couldn't remove app data directory for package: "
                       + ps.name + ", retcode=" + retCode);
        } else {
            sUserManager.removePackageForAllUsers(ps.name);
        }
        if (ps.codePath != null) {
            if (!ps.codePath.delete()) {
                Slog.w(TAG, "Unable to remove old code file: " + ps.codePath);
            }
        }
        if (ps.resourcePath != null) {
            if (!ps.resourcePath.delete() && !ps.resourcePath.equals(ps.codePath)) {
                Slog.w(TAG, "Unable to remove old code file: " + ps.resourcePath);
            }
        }
        mSettings.removePackageLPw(ps.name);
    }

    void readPermissions() {
        // Read permissions from .../etc/permission directory.
        File libraryDir = new File(Environment.getRootDirectory(), "etc/permissions");
        if (!libraryDir.exists() || !libraryDir.isDirectory()) {
            Slog.w(TAG, "No directory " + libraryDir + ", skipping");
            return;
        }
        if (!libraryDir.canRead()) {
            Slog.w(TAG, "Directory " + libraryDir + " cannot be read");
            return;
        }

        // Iterate over the files in the directory and scan .xml files
        for (File f : libraryDir.listFiles()) {
            // We'll read platform.xml last
            if (f.getPath().endsWith("etc/permissions/platform.xml")) {
                continue;
            }

            if (!f.getPath().endsWith(".xml")) {
                Slog.i(TAG, "Non-xml file " + f + " in " + libraryDir + " directory, ignoring");
                continue;
            }
            if (!f.canRead()) {
                Slog.w(TAG, "Permissions library file " + f + " cannot be read");
                continue;
            }

            readPermissionsFromXml(f);
        }

        // Read permissions from .../etc/permissions/platform.xml last so it will take precedence
        final File permFile = new File(Environment.getRootDirectory(),
                "etc/permissions/platform.xml");
        readPermissionsFromXml(permFile);
    }

    private void readPermissionsFromXml(File permFile) {
        FileReader permReader = null;
        try {
            permReader = new FileReader(permFile);
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "Couldn't find or open permissions file " + permFile);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(permReader);

            XmlUtils.beginDocument(parser, "permissions");

            while (true) {
                XmlUtils.nextElement(parser);
                if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                    break;
                }

                String name = parser.getName();
                if ("group".equals(name)) {
                    String gidStr = parser.getAttributeValue(null, "gid");
                    if (gidStr != null) {
                        int gid = Integer.parseInt(gidStr);
                        mGlobalGids = appendInt(mGlobalGids, gid);
                    } else {
                        Slog.w(TAG, "<group> without gid at "
                                + parser.getPositionDescription());
                    }

                    XmlUtils.skipCurrentTag(parser);
                    continue;
                } else if ("permission".equals(name)) {
                    String perm = parser.getAttributeValue(null, "name");
                    if (perm == null) {
                        Slog.w(TAG, "<permission> without name at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    perm = perm.intern();
                    readPermission(parser, perm);

                } else if ("assign-permission".equals(name)) {
                    String perm = parser.getAttributeValue(null, "name");
                    if (perm == null) {
                        Slog.w(TAG, "<assign-permission> without name at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    String uidStr = parser.getAttributeValue(null, "uid");
                    if (uidStr == null) {
                        Slog.w(TAG, "<assign-permission> without uid at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    int uid = Process.getUidForName(uidStr);
                    if (uid < 0) {
                        Slog.w(TAG, "<assign-permission> with unknown uid \""
                                + uidStr + "\" at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    perm = perm.intern();
                    HashSet<String> perms = mSystemPermissions.get(uid);
                    if (perms == null) {
                        perms = new HashSet<String>();
                        mSystemPermissions.put(uid, perms);
                    }
                    perms.add(perm);
                    XmlUtils.skipCurrentTag(parser);

                } else if ("library".equals(name)) {
                    String lname = parser.getAttributeValue(null, "name");
                    String lfile = parser.getAttributeValue(null, "file");
                    if (lname == null) {
                        Slog.w(TAG, "<library> without name at "
                                + parser.getPositionDescription());
                    } else if (lfile == null) {
                        Slog.w(TAG, "<library> without file at "
                                + parser.getPositionDescription());
                    } else {
                        //Log.i(TAG, "Got library " + lname + " in " + lfile);
                        mSharedLibraries.put(lname, lfile);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("feature".equals(name)) {
                    String fname = parser.getAttributeValue(null, "name");
                    if (fname == null) {
                        Slog.w(TAG, "<feature> without name at "
                                + parser.getPositionDescription());
                    } else {
                        //Log.i(TAG, "Got feature " + fname);
                        FeatureInfo fi = new FeatureInfo();
                        fi.name = fname;
                        mAvailableFeatures.put(fname, fi);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else {
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }

            }
            permReader.close();
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "Got execption parsing permissions.", e);
        } catch (IOException e) {
            Slog.w(TAG, "Got execption parsing permissions.", e);
        }
    }

    void readPermission(XmlPullParser parser, String name)
            throws IOException, XmlPullParserException {

        name = name.intern();

        BasePermission bp = mSettings.mPermissions.get(name);
        if (bp == null) {
            bp = new BasePermission(name, null, BasePermission.TYPE_BUILTIN);
            mSettings.mPermissions.put(name, bp);
        }
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
            if ("group".equals(tagName)) {
                String gidStr = parser.getAttributeValue(null, "gid");
                if (gidStr != null) {
                    int gid = Process.getGidForName(gidStr);
                    bp.gids = appendInt(bp.gids, gid);
                } else {
                    Slog.w(TAG, "<group> without gid at "
                            + parser.getPositionDescription());
                }
            }
            XmlUtils.skipCurrentTag(parser);
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

    static int[] removeInts(int[] cur, int[] rem) {
        if (rem == null) return cur;
        if (cur == null) return cur;
        final int N = rem.length;
        for (int i=0; i<N; i++) {
            cur = removeInt(cur, rem[i]);
        }
        return cur;
    }

    PackageInfo generatePackageInfo(PackageParser.Package p, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        PackageInfo pi;
        if ((flags & PackageManager.GET_UNINSTALLED_PACKAGES) != 0) {
            // The package has been uninstalled but has retained data and resources.
            pi = PackageParser.generatePackageInfo(p, null, flags, 0, 0, null, false, 0, userId);
        } else {
            final PackageSetting ps = (PackageSetting) p.mExtras;
            if (ps == null) {
                return null;
            }
            final GrantedPermissions gp = ps.sharedUser != null ? ps.sharedUser : ps;
            pi = PackageParser.generatePackageInfo(p, gp.gids, flags,
                    ps.firstInstallTime, ps.lastUpdateTime, gp.grantedPermissions,
                    ps.getStopped(userId), ps.getEnabled(userId), userId);
            pi.applicationInfo.enabledSetting = ps.getEnabled(userId);
            pi.applicationInfo.enabled =
                    pi.applicationInfo.enabledSetting == COMPONENT_ENABLED_STATE_DEFAULT
                    || pi.applicationInfo.enabledSetting == COMPONENT_ENABLED_STATE_ENABLED;
        }
        return pi;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        // reader
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if (DEBUG_PACKAGE_INFO)
                Log.v(TAG, "getPackageInfo " + packageName + ": " + p);
            if (p != null) {
                return generatePackageInfo(p, flags, userId);
            }
            if((flags & PackageManager.GET_UNINSTALLED_PACKAGES) != 0) {
                return generatePackageInfoFromSettingsLPw(packageName, flags, userId);
            }
        }
        return null;
    }

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
    public int getPackageUid(String packageName, int userId) {
        if (!sUserManager.exists(userId)) return -1;
        // reader
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if(p != null) {
                return UserId.getUid(userId, p.applicationInfo.uid);
            }
            PackageSetting ps = mSettings.mPackages.get(packageName);
            if((ps == null) || (ps.pkg == null) || (ps.pkg.applicationInfo == null)) {
                return -1;
            }
            p = ps.pkg;
            return p != null ? UserId.getUid(userId, p.applicationInfo.uid) : -1;
        }
    }

    public int[] getPackageGids(String packageName) {
        // reader
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if (DEBUG_PACKAGE_INFO)
                Log.v(TAG, "getPackageGids" + packageName + ": " + p);
            if (p != null) {
                final PackageSetting ps = (PackageSetting)p.mExtras;
                final SharedUserSetting suid = ps.sharedUser;
                int[] gids = suid != null ? suid.gids : ps.gids;

                // include GIDs for any unenforced permissions
                if (!isPermissionEnforcedLocked(READ_EXTERNAL_STORAGE)) {
                    final BasePermission basePerm = mSettings.mPermissions.get(
                            READ_EXTERNAL_STORAGE);
                    gids = appendInts(gids, basePerm.gids);
                }

                return gids;
            }
        }
        // stupid thing to indicate an error.
        return new int[0];
    }

    static final PermissionInfo generatePermissionInfo(
            BasePermission bp, int flags) {
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

    public List<PermissionInfo> queryPermissionsByGroup(String group, int flags) {
        // reader
        synchronized (mPackages) {
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

            if (out.size() > 0) {
                return out;
            }
            return mPermissionGroups.containsKey(group) ? out : null;
        }
    }

    public PermissionGroupInfo getPermissionGroupInfo(String name, int flags) {
        // reader
        synchronized (mPackages) {
            return PackageParser.generatePermissionGroupInfo(
                    mPermissionGroups.get(name), flags);
        }
    }

    public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
        // reader
        synchronized (mPackages) {
            final int N = mPermissionGroups.size();
            ArrayList<PermissionGroupInfo> out
                    = new ArrayList<PermissionGroupInfo>(N);
            for (PackageParser.PermissionGroup pg : mPermissionGroups.values()) {
                out.add(PackageParser.generatePermissionGroupInfo(pg, flags));
            }
            return out;
        }
    }

    private ApplicationInfo generateApplicationInfoFromSettingsLPw(String packageName, int flags,
            int userId) {
        if (!sUserManager.exists(userId)) return null;
        PackageSetting ps = mSettings.mPackages.get(packageName);
        if (ps != null) {
            if (ps.pkg == null) {
                PackageInfo pInfo = generatePackageInfoFromSettingsLPw(packageName, flags, userId);
                if (pInfo != null) {
                    return pInfo.applicationInfo;
                }
                return null;
            }
            return PackageParser.generateApplicationInfo(ps.pkg, flags, ps.getStopped(userId),
                    ps.getEnabled(userId), userId);
        }
        return null;
    }

    private PackageInfo generatePackageInfoFromSettingsLPw(String packageName, int flags,
            int userId) {
        if (!sUserManager.exists(userId)) return null;
        PackageSetting ps = mSettings.mPackages.get(packageName);
        if (ps != null) {
            PackageParser.Package pkg = new PackageParser.Package(packageName);
            if (ps.pkg == null) {
                ps.pkg = new PackageParser.Package(packageName);
                ps.pkg.applicationInfo.packageName = packageName;
                ps.pkg.applicationInfo.flags = ps.pkgFlags;
                ps.pkg.applicationInfo.publicSourceDir = ps.resourcePathString;
                ps.pkg.applicationInfo.sourceDir = ps.codePathString;
                ps.pkg.applicationInfo.dataDir =
                        getDataPathForPackage(ps.pkg.packageName, 0).getPath();
                ps.pkg.applicationInfo.nativeLibraryDir = ps.nativeLibraryPathString;
            }
            // ps.pkg.mSetEnabled = ps.getEnabled(userId);
            // ps.pkg.mSetStopped = ps.getStopped(userId);
            return generatePackageInfo(ps.pkg, flags, userId);
        }
        return null;
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
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
                return PackageParser.generateApplicationInfo(p, flags, ps.getStopped(userId),
                        ps.getEnabled(userId));
            }
            if ("android".equals(packageName)||"system".equals(packageName)) {
                return mAndroidApplication;
            }
            if((flags & PackageManager.GET_UNINSTALLED_PACKAGES) != 0) {
                return generateApplicationInfoFromSettingsLPw(packageName, flags, userId);
            }
        }
        return null;
    }


    public void freeStorageAndNotify(final long freeStorageSize, final IPackageDataObserver observer) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CLEAR_APP_CACHE, null);
        // Queue up an async operation since clearing cache may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                int retCode = -1;
                retCode = mInstaller.freeCache(freeStorageSize);
                if (retCode < 0) {
                    Slog.w(TAG, "Couldn't clear application caches");
                }
                if (observer != null) {
                    try {
                        observer.onRemoveCompleted(null, (retCode >= 0));
                    } catch (RemoteException e) {
                        Slog.w(TAG, "RemoveException when invoking call back");
                    }
                }
            }
        });
    }

    public void freeStorage(final long freeStorageSize, final IntentSender pi) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CLEAR_APP_CACHE, null);
        // Queue up an async operation since clearing cache may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                int retCode = -1;
                retCode = mInstaller.freeCache(freeStorageSize);
                if (retCode < 0) {
                    Slog.w(TAG, "Couldn't clear application caches");
                }
                if(pi != null) {
                    try {
                        // Callback via pending intent
                        int code = (retCode >= 0) ? 1 : 0;
                        pi.sendIntent(null, code, null,
                                null, null);
                    } catch (SendIntentException e1) {
                        Slog.i(TAG, "Failed to send pending intent");
                    }
                }
            }
        });
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            PackageParser.Activity a = mActivities.mActivities.get(component);

            if (DEBUG_PACKAGE_INFO) Log.v(TAG, "getActivityInfo " + component + ": " + a);
            if (a != null && mSettings.isEnabledLPr(a.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateActivityInfo(a, flags, ps.getStopped(userId),
                        ps.getEnabled(userId), userId);
            }
            if (mResolveComponentName.equals(component)) {
                return mResolveActivity;
            }
        }
        return null;
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            PackageParser.Activity a = mReceivers.mActivities.get(component);
            if (DEBUG_PACKAGE_INFO) Log.v(
                TAG, "getReceiverInfo " + component + ": " + a);
            if (a != null && mSettings.isEnabledLPr(a.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateActivityInfo(a, flags, ps.getStopped(userId),
                        ps.getEnabled(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            PackageParser.Service s = mServices.mServices.get(component);
            if (DEBUG_PACKAGE_INFO) Log.v(
                TAG, "getServiceInfo " + component + ": " + s);
            if (s != null && mSettings.isEnabledLPr(s.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateServiceInfo(s, flags, ps.getStopped(userId),
                        ps.getEnabled(userId), userId);
            }
        }
        return null;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        synchronized (mPackages) {
            PackageParser.Provider p = mProvidersByComponent.get(component);
            if (DEBUG_PACKAGE_INFO) Log.v(
                TAG, "getProviderInfo " + component + ": " + p);
            if (p != null && mSettings.isEnabledLPr(p.info, flags, userId)) {
                PackageSetting ps = mSettings.mPackages.get(component.getPackageName());
                if (ps == null) return null;
                return PackageParser.generateProviderInfo(p, flags, ps.getStopped(userId),
                        ps.getEnabled(userId), userId);
            }
        }
        return null;
    }

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

    public FeatureInfo[] getSystemAvailableFeatures() {
        Collection<FeatureInfo> featSet;
        synchronized (mPackages) {
            featSet = mAvailableFeatures.values();
            int size = featSet.size();
            if (size > 0) {
                FeatureInfo[] features = new FeatureInfo[size+1];
                featSet.toArray(features);
                FeatureInfo fi = new FeatureInfo();
                fi.reqGlEsVersion = SystemProperties.getInt("ro.opengles.version",
                        FeatureInfo.GL_ES_VERSION_UNDEFINED);
                features[size] = fi;
                return features;
            }
        }
        return null;
    }

    public boolean hasSystemFeature(String name) {
        synchronized (mPackages) {
            return mAvailableFeatures.containsKey(name);
        }
    }

    private void checkValidCaller(int uid, int userId) {
        if (UserId.getUserId(uid) == userId || uid == Process.SYSTEM_UID || uid == 0)
            return;

        throw new SecurityException("Caller uid=" + uid
                + " is not privileged to communicate with user=" + userId);
    }

    public int checkPermission(String permName, String pkgName) {
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(pkgName);
            if (p != null && p.mExtras != null) {
                PackageSetting ps = (PackageSetting)p.mExtras;
                if (ps.sharedUser != null) {
                    if (ps.sharedUser.grantedPermissions.contains(permName)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                } else if (ps.grantedPermissions.contains(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            }
            if (!isPermissionEnforcedLocked(permName)) {
                return PackageManager.PERMISSION_GRANTED;
            }
        }
        return PackageManager.PERMISSION_DENIED;
    }

    public int checkUidPermission(String permName, int uid) {
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(UserId.getAppId(uid));
            if (obj != null) {
                GrantedPermissions gp = (GrantedPermissions)obj;
                if (gp.grantedPermissions.contains(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            } else {
                HashSet<String> perms = mSystemPermissions.get(uid);
                if (perms != null && perms.contains(permName)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            }
            if (!isPermissionEnforcedLocked(permName)) {
                return PackageManager.PERMISSION_GRANTED;
            }
        }
        return PackageManager.PERMISSION_DENIED;
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
                if (bp.uid == UserId.getAppId(Binder.getCallingUid())) {
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
        // These are not currently stored in settings.
        //if (!compareStrings(pi1.group, pi2.group)) return false;
        //if (!compareStrings(pi1.nonLocalizedDescription, pi2.nonLocalizedDescription)) return false;
        //if (pi1.labelRes != pi2.labelRes) return false;
        //if (pi1.descriptionRes != pi2.descriptionRes) return false;
        return true;
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

    public boolean addPermission(PermissionInfo info) {
        synchronized (mPackages) {
            return addPermissionLocked(info, false);
        }
    }

    public boolean addPermissionAsync(PermissionInfo info) {
        synchronized (mPackages) {
            return addPermissionLocked(info, true);
        }
    }

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

    public void grantPermission(String packageName, String permissionName) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.GRANT_REVOKE_PERMISSIONS, null);
        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
            final BasePermission bp = mSettings.mPermissions.get(permissionName);
            if (bp == null) {
                throw new IllegalArgumentException("Unknown permission: " + packageName);
            }
            if (!pkg.requestedPermissions.contains(permissionName)) {
                throw new SecurityException("Package " + packageName
                        + " has not requested permission " + permissionName);
            }
            if ((bp.protectionLevel&PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) == 0) {
                throw new SecurityException("Permission " + permissionName
                        + " is not a development permission");
            }
            final PackageSetting ps = (PackageSetting) pkg.mExtras;
            if (ps == null) {
                return;
            }
            final GrantedPermissions gp = ps.sharedUser != null ? ps.sharedUser : ps;
            if (gp.grantedPermissions.add(permissionName)) {
                if (ps.haveGids) {
                    gp.gids = appendInts(gp.gids, bp.gids);
                }
                mSettings.writeLPr();
            }
        }
    }

    public void revokePermission(String packageName, String permissionName) {
        synchronized (mPackages) {
            final PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                throw new IllegalArgumentException("Unknown package: " + packageName);
            }
            if (pkg.applicationInfo.uid != Binder.getCallingUid()) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.GRANT_REVOKE_PERMISSIONS, null);
            }
            final BasePermission bp = mSettings.mPermissions.get(permissionName);
            if (bp == null) {
                throw new IllegalArgumentException("Unknown permission: " + packageName);
            }
            if (!pkg.requestedPermissions.contains(permissionName)) {
                throw new SecurityException("Package " + packageName
                        + " has not requested permission " + permissionName);
            }
            if ((bp.protectionLevel&PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) == 0) {
                throw new SecurityException("Permission " + permissionName
                        + " is not a development permission");
            }
            final PackageSetting ps = (PackageSetting) pkg.mExtras;
            if (ps == null) {
                return;
            }
            final GrantedPermissions gp = ps.sharedUser != null ? ps.sharedUser : ps;
            if (gp.grantedPermissions.remove(permissionName)) {
                gp.grantedPermissions.remove(permissionName);
                if (ps.haveGids) {
                    gp.gids = removeInts(gp.gids, bp.gids);
                }
                mSettings.writeLPr();
            }
        }
    }

    public boolean isProtectedBroadcast(String actionName) {
        synchronized (mPackages) {
            return mProtectedBroadcasts.contains(actionName);
        }
    }

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

    public int checkUidSignatures(int uid1, int uid2) {
        // Map to base uids.
        uid1 = UserId.getAppId(uid1);
        uid2 = UserId.getAppId(uid2);
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

    static int compareSignatures(Signature[] s1, Signature[] s2) {
        if (s1 == null) {
            return s2 == null
                    ? PackageManager.SIGNATURE_NEITHER_SIGNED
                    : PackageManager.SIGNATURE_FIRST_NOT_SIGNED;
        }
        if (s2 == null) {
            return PackageManager.SIGNATURE_SECOND_NOT_SIGNED;
        }
        HashSet<Signature> set1 = new HashSet<Signature>();
        for (Signature sig : s1) {
            set1.add(sig);
        }
        HashSet<Signature> set2 = new HashSet<Signature>();
        for (Signature sig : s2) {
            set2.add(sig);
        }
        // Make sure s2 contains all signatures in s1.
        if (set1.equals(set2)) {
            return PackageManager.SIGNATURE_MATCH;
        }
        return PackageManager.SIGNATURE_NO_MATCH;
    }

    public String[] getPackagesForUid(int uid) {
        uid = UserId.getAppId(uid);
        // reader
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(uid);
            if (obj instanceof SharedUserSetting) {
                final SharedUserSetting sus = (SharedUserSetting) obj;
                final int N = sus.packages.size();
                final String[] res = new String[N];
                final Iterator<PackageSetting> it = sus.packages.iterator();
                int i = 0;
                while (it.hasNext()) {
                    res[i++] = it.next().name;
                }
                return res;
            } else if (obj instanceof PackageSetting) {
                final PackageSetting ps = (PackageSetting) obj;
                return new String[] { ps.name };
            }
        }
        return null;
    }

    public String getNameForUid(int uid) {
        // reader
        synchronized (mPackages) {
            Object obj = mSettings.getUserIdLPr(UserId.getAppId(uid));
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

    public int getUidForSharedUser(String sharedUserName) {
        if(sharedUserName == null) {
            return -1;
        }
        // reader
        synchronized (mPackages) {
            final SharedUserSetting suid = mSettings.getSharedUserLPw(sharedUserName, 0, false);
            if (suid == null) {
                return -1;
            }
            return suid.userId;
        }
    }

    @Override
    public ResolveInfo resolveIntent(Intent intent, String resolvedType,
            int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        List<ResolveInfo> query = queryIntentActivities(intent, resolvedType, flags, userId);
        return chooseBestActivity(intent, resolvedType, flags, query, userId);
    }

    private ResolveInfo chooseBestActivity(Intent intent, String resolvedType,
            int flags, List<ResolveInfo> query, int userId) {
        if (query != null) {
            final int N = query.size();
            if (N == 1) {
                return query.get(0);
            } else if (N > 1) {
                // If there is more than one activity with the same priority,
                // then let the user decide between them.
                ResolveInfo r0 = query.get(0);
                ResolveInfo r1 = query.get(1);
                if (DEBUG_INTENT_MATCHING) {
                    Log.d(TAG, r0.activityInfo.name + "=" + r0.priority + " vs "
                            + r1.activityInfo.name + "=" + r1.priority);
                }
                // If the first activity has a higher priority, or a different
                // default, then it is always desireable to pick it.
                if (r0.priority != r1.priority
                        || r0.preferredOrder != r1.preferredOrder
                        || r0.isDefault != r1.isDefault) {
                    return query.get(0);
                }
                // If we have saved a preference for a preferred activity for
                // this Intent, use that.
                ResolveInfo ri = findPreferredActivity(intent, resolvedType,
                        flags, query, r0.priority, userId);
                if (ri != null) {
                    return ri;
                }
                return mResolveInfo;
            }
        }
        return null;
    }

    ResolveInfo findPreferredActivity(Intent intent, String resolvedType,
            int flags, List<ResolveInfo> query, int priority, int userId) {
        if (!sUserManager.exists(userId)) return null;
        // writer
        synchronized (mPackages) {
            if (intent.getSelector() != null) {
                intent = intent.getSelector(); 
            }
            if (DEBUG_PREFERRED) intent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
            List<PreferredActivity> prefs =
                    mSettings.mPreferredActivities.queryIntent(intent, resolvedType,
                            (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId);
            if (prefs != null && prefs.size() > 0) {
                // First figure out how good the original match set is.
                // We will only allow preferred activities that came
                // from the same match quality.
                int match = 0;

                if (DEBUG_PREFERRED) {
                    Log.v(TAG, "Figuring out best match...");
                }

                final int N = query.size();
                for (int j=0; j<N; j++) {
                    final ResolveInfo ri = query.get(j);
                    if (DEBUG_PREFERRED) {
                        Log.v(TAG, "Match for " + ri.activityInfo + ": 0x"
                                + Integer.toHexString(match));
                    }
                    if (ri.match > match) {
                        match = ri.match;
                    }
                }

                if (DEBUG_PREFERRED) {
                    Log.v(TAG, "Best match: 0x" + Integer.toHexString(match));
                }

                match &= IntentFilter.MATCH_CATEGORY_MASK;
                final int M = prefs.size();
                for (int i=0; i<M; i++) {
                    final PreferredActivity pa = prefs.get(i);
                    if (pa.mPref.mMatch != match) {
                        continue;
                    }
                    final ActivityInfo ai = getActivityInfo(pa.mPref.mComponent, flags, userId);
                    if (DEBUG_PREFERRED) {
                        Log.v(TAG, "Got preferred activity:");
                        if (ai != null) {
                            ai.dump(new LogPrinter(Log.VERBOSE, TAG), "  ");
                        } else {
                            Log.v(TAG, "  null");
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
                        mSettings.mPreferredActivities.removeFilter(pa);
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

                        // Okay we found a previously set preferred app.
                        // If the result set is different from when this
                        // was created, we need to clear it and re-ask the
                        // user their preference.
                        if (!pa.mPref.sameSet(query, priority)) {
                            Slog.i(TAG, "Result set changed, dropping preferred activity for "
                                    + intent + " type " + resolvedType);
                            mSettings.mPreferredActivities.removeFilter(pa);
                            return null;
                        }

                        // Yay!
                        return ri;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent,
            String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
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
                return mActivities.queryIntent(intent, resolvedType, flags, userId);
            }
            final PackageParser.Package pkg = mPackages.get(pkgName);
            if (pkg != null) {
                return mActivities.queryIntentForPackage(intent, resolvedType, flags,
                        pkg.activities, userId);
            }
            return new ArrayList<ResolveInfo>();
        }
    }

    @Override
    public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller,
            Intent[] specifics, String[] specificTypes, Intent intent,
            String resolvedType, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        final String resultsAction = intent.getAction();

        List<ResolveInfo> results = queryIntentActivities(intent, resolvedType, flags
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
    public List<ResolveInfo> queryIntentReceivers(Intent intent, String resolvedType, int flags,
            int userId) {
        if (!sUserManager.exists(userId)) return null;
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
            return null;
        }
    }

    @Override
    public ResolveInfo resolveService(Intent intent, String resolvedType, int flags, int userId) {
        List<ResolveInfo> query = queryIntentServices(intent, resolvedType, flags, userId);
        if (!sUserManager.exists(userId)) return null;
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
    public List<ResolveInfo> queryIntentServices(Intent intent, String resolvedType, int flags,
            int userId) {
        if (!sUserManager.exists(userId)) return null;
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
            return null;
        }
    }

    private static final int getContinuationPoint(final String[] keys, final String key) {
        final int index;
        if (key == null) {
            index = 0;
        } else {
            final int insertPoint = Arrays.binarySearch(keys, key);
            if (insertPoint < 0) {
                index = -insertPoint;
            } else {
                index = insertPoint + 1;
            }
        }
        return index;
    }

    public ParceledListSlice<PackageInfo> getInstalledPackages(int flags, String lastRead) {
        final ParceledListSlice<PackageInfo> list = new ParceledListSlice<PackageInfo>();
        final boolean listUninstalled = (flags & PackageManager.GET_UNINSTALLED_PACKAGES) != 0;
        final String[] keys;
        int userId = UserId.getCallingUserId();

        // writer
        synchronized (mPackages) {
            if (listUninstalled) {
                keys = mSettings.mPackages.keySet().toArray(new String[mSettings.mPackages.size()]);
            } else {
                keys = mPackages.keySet().toArray(new String[mPackages.size()]);
            }

            Arrays.sort(keys);
            int i = getContinuationPoint(keys, lastRead);
            final int N = keys.length;

            while (i < N) {
                final String packageName = keys[i++];

                PackageInfo pi = null;
                if (listUninstalled) {
                    final PackageSetting ps = mSettings.mPackages.get(packageName);
                    if (ps != null) {
                        pi = generatePackageInfoFromSettingsLPw(ps.name, flags, userId);
                    }
                } else {
                    final PackageParser.Package p = mPackages.get(packageName);
                    if (p != null) {
                        pi = generatePackageInfo(p, flags, userId);
                    }
                }

                if (pi != null && list.append(pi)) {
                    break;
                }
            }

            if (i == N) {
                list.setLastSlice(true);
            }
        }

        return list;
    }

    public List<PackageInfo> getInstalledThemePackages() {
        // Returns a list of theme APKs.
        ArrayList<PackageInfo> finalList = new ArrayList<PackageInfo>();
        List<PackageInfo> installedPackagesList = mContext.getPackageManager().getInstalledPackages(0);
        Iterator<PackageInfo> i = installedPackagesList.iterator();
        while (i.hasNext()) {
            final PackageInfo pi = i.next();
            if (pi != null && pi.isThemeApk) {
                finalList.add(pi);
            }
        }
        return finalList;
    }

    @Override
    public ParceledListSlice<ApplicationInfo> getInstalledApplications(int flags,
            String lastRead, int userId) {
        if (!sUserManager.exists(userId)) return null;
        final ParceledListSlice<ApplicationInfo> list = new ParceledListSlice<ApplicationInfo>();
        final boolean listUninstalled = (flags & PackageManager.GET_UNINSTALLED_PACKAGES) != 0;
        final String[] keys;

        // writer
        synchronized (mPackages) {
            if (listUninstalled) {
                keys = mSettings.mPackages.keySet().toArray(new String[mSettings.mPackages.size()]);
            } else {
                keys = mPackages.keySet().toArray(new String[mPackages.size()]);
            }

            Arrays.sort(keys);
            int i = getContinuationPoint(keys, lastRead);
            final int N = keys.length;

            while (i < N) {
                final String packageName = keys[i++];

                ApplicationInfo ai = null;
                final PackageSetting ps = mSettings.mPackages.get(packageName);
                if (listUninstalled) {
                    if (ps != null) {
                        ai = generateApplicationInfoFromSettingsLPw(ps.name, flags, userId);
                    }
                } else {
                    final PackageParser.Package p = mPackages.get(packageName);
                    if (p != null && ps != null) {
                        ai = PackageParser.generateApplicationInfo(p, flags, ps.getStopped(userId),
                                ps.getEnabled(userId), userId);
                    }
                }

                if (ai != null && list.append(ai)) {
                    break;
                }
            }

            if (i == N) {
                list.setLastSlice(true);
            }
        }

        return list;
    }

    public List<ApplicationInfo> getPersistentApplications(int flags) {
        final ArrayList<ApplicationInfo> finalList = new ArrayList<ApplicationInfo>();

        // reader
        synchronized (mPackages) {
            final Iterator<PackageParser.Package> i = mPackages.values().iterator();
            final int userId = UserId.getCallingUserId();
            while (i.hasNext()) {
                final PackageParser.Package p = i.next();
                if (p.applicationInfo != null
                        && (p.applicationInfo.flags&ApplicationInfo.FLAG_PERSISTENT) != 0
                        && (!mSafeMode || isSystemApp(p))) {
                    PackageSetting ps = mSettings.mPackages.get(p.packageName);
                    finalList.add(PackageParser.generateApplicationInfo(p, flags,
                            ps != null ? ps.getStopped(userId) : false,
                            ps != null ? ps.getEnabled(userId) : COMPONENT_ENABLED_STATE_DEFAULT,
                            userId));
                }
            }
        }

        return finalList;
    }

    @Override
    public ProviderInfo resolveContentProvider(String name, int flags, int userId) {
        if (!sUserManager.exists(userId)) return null;
        // reader
        synchronized (mPackages) {
            final PackageParser.Provider provider = mProviders.get(name);
            PackageSetting ps = provider != null
                    ? mSettings.mPackages.get(provider.owner.packageName)
                    : null;
            return provider != null
                    && mSettings.isEnabledLPr(provider.info, flags, userId)
                    && (!mSafeMode || (provider.info.applicationInfo.flags
                            &ApplicationInfo.FLAG_SYSTEM) != 0)
                    ? PackageParser.generateProviderInfo(provider, flags,
                            ps != null ? ps.getStopped(userId) : false,
                            ps != null ? ps.getEnabled(userId) : COMPONENT_ENABLED_STATE_DEFAULT,
                            userId)
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
            final Iterator<Map.Entry<String, PackageParser.Provider>> i = mProviders.entrySet()
                    .iterator();
            final int userId = UserId.getCallingUserId();
            while (i.hasNext()) {
                Map.Entry<String, PackageParser.Provider> entry = i.next();
                PackageParser.Provider p = entry.getValue();
                PackageSetting ps = mSettings.mPackages.get(p.owner.packageName);

                if (p.syncable
                        && (!mSafeMode || (p.info.applicationInfo.flags
                                &ApplicationInfo.FLAG_SYSTEM) != 0)) {
                    outNames.add(entry.getKey());
                    outInfo.add(PackageParser.generateProviderInfo(p, 0,
                            ps != null ? ps.getStopped(userId) : false,
                            ps != null ? ps.getEnabled(userId) : COMPONENT_ENABLED_STATE_DEFAULT,
                            userId));
                }
            }
        }
    }

    public List<ProviderInfo> queryContentProviders(String processName,
            int uid, int flags) {
        ArrayList<ProviderInfo> finalList = null;

        // reader
        synchronized (mPackages) {
            final Iterator<PackageParser.Provider> i = mProvidersByComponent.values().iterator();
            final int userId = processName != null ?
                    UserId.getUserId(uid) : UserId.getCallingUserId();
            while (i.hasNext()) {
                final PackageParser.Provider p = i.next();
                PackageSetting ps = mSettings.mPackages.get(p.owner.packageName);
                if (p.info.authority != null
                        && (processName == null
                                || (p.info.processName.equals(processName)
                                        && UserId.isSameApp(p.info.applicationInfo.uid, uid)))
                        && mSettings.isEnabledLPr(p.info, flags, userId)
                        && (!mSafeMode
                                || (p.info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)) {
                    if (finalList == null) {
                        finalList = new ArrayList<ProviderInfo>(3);
                    }
                    finalList.add(PackageParser.generateProviderInfo(p, flags,
                            ps != null ? ps.getStopped(userId) : false,
                            ps != null ? ps.getEnabled(userId) : COMPONENT_ENABLED_STATE_DEFAULT,
                            userId));
                }
            }
        }

        if (finalList != null) {
            Collections.sort(finalList, mProviderInitOrderSorter);
        }

        return finalList;
    }

    public InstrumentationInfo getInstrumentationInfo(ComponentName name,
            int flags) {
        // reader
        synchronized (mPackages) {
            final PackageParser.Instrumentation i = mInstrumentation.get(name);
            return PackageParser.generateInstrumentationInfo(i, flags);
        }
    }

    public List<InstrumentationInfo> queryInstrumentation(String targetPackage,
            int flags) {
        ArrayList<InstrumentationInfo> finalList =
            new ArrayList<InstrumentationInfo>();

        // reader
        synchronized (mPackages) {
            final Iterator<PackageParser.Instrumentation> i = mInstrumentation.values().iterator();
            while (i.hasNext()) {
                final PackageParser.Instrumentation p = i.next();
                if (targetPackage == null
                        || targetPackage.equals(p.info.targetPackage)) {
                    finalList.add(PackageParser.generateInstrumentationInfo(p,
                            flags));
                }
            }
        }

        return finalList;
    }

    private void scanDirLI(File dir, int flags, int scanMode, long currentTime) {
        String[] files = dir.list();
        if (files == null) {
            Log.d(TAG, "No files in app dir " + dir);
            return;
        }

        if (DEBUG_PACKAGE_SCANNING) {
            Log.d(TAG, "Scanning app dir " + dir);
        }

        int i;
        for (i=0; i<files.length; i++) {
            File file = new File(dir, files[i]);
            if (!isPackageFilename(files[i])) {
                // Ignore entries which are not apk's
                continue;
            }
            PackageParser.Package pkg = scanPackageLI(file,
                    flags|PackageParser.PARSE_MUST_BE_APK, scanMode, currentTime);
            // Don't mess around with apps in system partition.
            if (pkg == null && (flags & PackageParser.PARSE_IS_SYSTEM) == 0 &&
                    mLastScanError == PackageManager.INSTALL_FAILED_INVALID_APK) {
                // Delete the apk
                Slog.w(TAG, "Cleaning up failed install of " + file);
                file.delete();
            }
        }
    }

    private static File getSettingsProblemFile() {
        File dataDir = Environment.getDataDirectory();
        File systemDir = new File(dataDir, "system");
        File fname = new File(systemDir, "uiderrors.txt");
        return fname;
    }
    
    static void reportSettingsProblem(int priority, String msg) {
        try {
            File fname = getSettingsProblemFile();
            FileOutputStream out = new FileOutputStream(fname, true);
            PrintWriter pw = new PrintWriter(out);
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
        Slog.println(priority, TAG, msg);
    }

    private boolean collectCertificatesLI(PackageParser pp, PackageSetting ps,
            PackageParser.Package pkg, File srcFile, int parseFlags) {
        if (GET_CERTIFICATES) {
            if (ps != null
                    && ps.codePath.equals(srcFile)
                    && ps.timeStamp == srcFile.lastModified()) {
                if (ps.signatures.mSignatures != null
                        && ps.signatures.mSignatures.length != 0) {
                    // Optimization: reuse the existing cached certificates
                    // if the package appears to be unchanged.
                    pkg.mSignatures = ps.signatures.mSignatures;
                    return true;
                }
                
                Slog.w(TAG, "PackageSetting for " + ps.name + " is missing signatures.  Collecting certs again to recover them.");
            } else {
                Log.i(TAG, srcFile.toString() + " changed; collecting certs");
            }
            
            if (!pp.collectCertificates(pkg, parseFlags)) {
                mLastScanError = pp.getParseError();
                return false;
            }
        }
        return true;
    }

    /*
     *  Scan a package and return the newly parsed package.
     *  Returns null in case of errors and the error code is stored in mLastScanError
     */
    private PackageParser.Package scanPackageLI(File scanFile,
            int parseFlags, int scanMode, long currentTime) {
        mLastScanError = PackageManager.INSTALL_SUCCEEDED;
        String scanPath = scanFile.getPath();
        parseFlags |= mDefParseFlags;
        PackageParser pp = new PackageParser(scanPath);
        pp.setSeparateProcesses(mSeparateProcesses);
        pp.setOnlyCoreApps(mOnlyCore);
        final PackageParser.Package pkg = pp.parsePackage(scanFile,
                scanPath, mMetrics, parseFlags);
        if (pkg == null) {
            mLastScanError = pp.getParseError();
            return null;
        }
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
        }
        // First check if this is a system package that may involve an update
        if (updatedPkg != null && (parseFlags&PackageParser.PARSE_IS_SYSTEM) != 0) {
            if (ps != null && !ps.codePath.equals(scanFile)) {
                // The path has changed from what was last scanned...  check the
                // version of the new path against what we have stored to determine
                // what to do.
                if (pkg.mVersionCode < ps.versionCode) {
                    // The system package has been updated and the code path does not match
                    // Ignore entry. Skip it.
                    Log.i(TAG, "Package " + ps.name + " at " + scanFile
                            + " ignored: updated version " + ps.versionCode
                            + " better than this " + pkg.mVersionCode);
                    mLastScanError = PackageManager.INSTALL_FAILED_DUPLICATE_PACKAGE;
                    return null;
                } else {
                    // The current app on the system partion is better than
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
                    Slog.w(TAG, "Package " + ps.name + " at " + scanFile
                            + "reverting from " + ps.codePathString
                            + ": new version " + pkg.mVersionCode
                            + " better than installed " + ps.versionCode);

                    InstallArgs args = createInstallArgs(packageFlagsToInstallFlags(ps),
                            ps.codePathString, ps.resourcePathString, ps.nativeLibraryPathString);
                    synchronized (mInstaller) {
                        args.cleanUpResourcesLI();
                    }
                    synchronized (mPackages) {
                        mSettings.enableSystemPackageLPw(ps.name);
                    }
                }
            }
        }

        if (updatedPkg != null) {
            // An updated system app will not have the PARSE_IS_SYSTEM flag set
            // initially
            parseFlags |= PackageParser.PARSE_IS_SYSTEM;
        }
        // Verify certificates against what was last scanned
        if (!collectCertificatesLI(pp, ps, pkg, scanFile, parseFlags)) {
            Slog.w(TAG, "Failed verifying certificates for package:" + pkg.packageName);
            return null;
        }

        /*
         * A new system app appeared, but we already had a non-system one of the
         * same name installed earlier.
         */
        boolean shouldHideSystemApp = false;
        if (updatedPkg == null && ps != null
                && (parseFlags & PackageParser.PARSE_IS_SYSTEM_DIR) != 0 && !isSystemApp(ps)) {
            /*
             * Check to make sure the signatures match first. If they don't,
             * wipe the installed application and its data.
             */
            if (compareSignatures(ps.signatures.mSignatures, pkg.mSignatures)
                    != PackageManager.SIGNATURE_MATCH) {
                deletePackageLI(pkg.packageName, true, 0, null, false);
                ps = null;
            } else {
                /*
                 * If the newly-added system app is an older version than the
                 * already installed version, hide it. It will be scanned later
                 * and re-added like an update.
                 */
                if (pkg.mVersionCode < ps.versionCode) {
                    shouldHideSystemApp = true;
                } else {
                    /*
                     * The newly found system app is a newer version that the
                     * one previously installed. Simply remove the
                     * already-installed application and replace it with our own
                     * while keeping the application data.
                     */
                    Slog.w(TAG, "Package " + ps.name + " at " + scanFile + "reverting from "
                            + ps.codePathString + ": new version " + pkg.mVersionCode
                            + " better than installed " + ps.versionCode);
                    InstallArgs args = createInstallArgs(packageFlagsToInstallFlags(ps),
                            ps.codePathString, ps.resourcePathString, ps.nativeLibraryPathString);
                    synchronized (mInstaller) {
                        args.cleanUpResourcesLI();
                    }
                }
            }
        }

        // The apk is forward locked (not public) if its code and resources
        // are kept in different files.
        // TODO grab this value from PackageSettings
        if (ps != null && !ps.codePath.equals(ps.resourcePath)) {
            parseFlags |= PackageParser.PARSE_FORWARD_LOCK;
        }

        String codePath = null;
        String resPath = null;
        if ((parseFlags & PackageParser.PARSE_FORWARD_LOCK) != 0) {
            if (ps != null && ps.resourcePathString != null) {
                resPath = ps.resourcePathString;
            } else {
                // Should not happen at all. Just log an error.
                Slog.e(TAG, "Resource path not set for pkg : " + pkg.packageName);
            }
        } else {
            resPath = pkg.mScanPath;
        }
        codePath = pkg.mScanPath;
        // Set application objects path explicitly.
        setApplicationInfoPaths(pkg, codePath, resPath);
        // Note that we invoke the following method only if we are about to unpack an application
        PackageParser.Package scannedPkg = scanPackageLI(pkg, parseFlags, scanMode
                | SCAN_UPDATE_SIGNATURE, currentTime);

        /*
         * If the system app should be overridden by a previously installed
         * data, hide the system app now and let the /data/app scan pick it up
         * again.
         */
        if (shouldHideSystemApp) {
            synchronized (mPackages) {
                /*
                 * We have to grant systems permissions before we hide, because
                 * grantPermissions will assume the package update is trying to
                 * expand its permissions.
                 */
                grantPermissionsLPw(pkg, true);
                mSettings.disableSystemPackageLPw(pkg.packageName);
            }
        }

        return scannedPkg;
    }

    private static void setApplicationInfoPaths(PackageParser.Package pkg, String destCodePath,
            String destResPath) {
        pkg.mPath = pkg.mScanPath = destCodePath;
        pkg.applicationInfo.sourceDir = destCodePath;
        pkg.applicationInfo.publicSourceDir = destResPath;
    }

    private static String fixProcessName(String defProcessName,
            String processName, int uid) {
        if (processName == null) {
            return defProcessName;
        }
        return processName;
    }

    private boolean verifySignaturesLP(PackageSetting pkgSetting,
            PackageParser.Package pkg) {
        if (pkgSetting.signatures.mSignatures != null) {
            // Already existing package. Make sure signatures match
            if (compareSignatures(pkgSetting.signatures.mSignatures, pkg.mSignatures) !=
                PackageManager.SIGNATURE_MATCH) {
                    Slog.e(TAG, "Package " + pkg.packageName
                            + " signatures do not match the previously installed version; ignoring!");
                    mLastScanError = PackageManager.INSTALL_FAILED_UPDATE_INCOMPATIBLE;
                    return false;
                }
        }
        // Check for shared user signatures
        if (pkgSetting.sharedUser != null && pkgSetting.sharedUser.signatures.mSignatures != null) {
            if (compareSignatures(pkgSetting.sharedUser.signatures.mSignatures,
                    pkg.mSignatures) != PackageManager.SIGNATURE_MATCH) {
                Slog.e(TAG, "Package " + pkg.packageName
                        + " has no signatures that match those in shared user "
                        + pkgSetting.sharedUser.name + "; ignoring!");
                mLastScanError = PackageManager.INSTALL_FAILED_SHARED_USER_INCOMPATIBLE;
                return false;
            }
        }
        return true;
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

    public void performBootDexOpt() {
        ArrayList<PackageParser.Package> pkgs = null;
        synchronized (mPackages) {
            if (mDeferredDexOpt.size() > 0) {
                pkgs = new ArrayList<PackageParser.Package>(mDeferredDexOpt);
                mDeferredDexOpt.clear();
            }
        }
        if (pkgs != null) {
            for (int i=0; i<pkgs.size(); i++) {
                if (!isFirstBoot()) {
                    try {
                        ActivityManagerNative.getDefault().showBootMessage(
                                mContext.getResources().getString(
                                        com.android.internal.R.string.android_upgrading_apk,
                                        i+1, pkgs.size()), true);
                    } catch (RemoteException e) {
                    }
                }
                PackageParser.Package p = pkgs.get(i);
                synchronized (mInstallLock) {
                    if (!p.mDidDexOpt) {
                        performDexOptLI(p, false, false);
                    }
                }
            }
        }
    }

    public boolean performDexOpt(String packageName) {
        enforceSystemOrRoot("Only the system can request dexopt be performed");

        if (!mNoDexOpt) {
            return false;
        }

        PackageParser.Package p;
        synchronized (mPackages) {
            p = mPackages.get(packageName);
            if (p == null || p.mDidDexOpt) {
                return false;
            }
        }
        synchronized (mInstallLock) {
            return performDexOptLI(p, false, false) == DEX_OPT_PERFORMED;
        }
    }

    static final int DEX_OPT_SKIPPED = 0;
    static final int DEX_OPT_PERFORMED = 1;
    static final int DEX_OPT_DEFERRED = 2;
    static final int DEX_OPT_FAILED = -1;

    private int performDexOptLI(PackageParser.Package pkg, boolean forceDex, boolean defer) {
        boolean performed = false;
        if ((pkg.applicationInfo.flags&ApplicationInfo.FLAG_HAS_CODE) != 0) {
            String path = pkg.mScanPath;
            int ret = 0;
            try {
                if (forceDex || dalvik.system.DexFile.isDexOptNeeded(path)) {
                    if (!forceDex && defer) {
                        mDeferredDexOpt.add(pkg);
                        return DEX_OPT_DEFERRED;
                    } else {
                        Log.i(TAG, "Running dexopt on: " + pkg.applicationInfo.packageName);
                        ret = mInstaller.dexopt(path, pkg.applicationInfo.uid,
                                !isForwardLocked(pkg));
                        pkg.mDidDexOpt = true;
                        performed = true;
                    }
                }
            } catch (FileNotFoundException e) {
                Slog.w(TAG, "Apk not found for dexopt: " + path);
                ret = -1;
            } catch (IOException e) {
                Slog.w(TAG, "IOException reading apk: " + path, e);
                ret = -1;
            } catch (dalvik.system.StaleDexCacheError e) {
                Slog.w(TAG, "StaleDexCacheError when reading apk: " + path, e);
                ret = -1;
            } catch (Exception e) {
                Slog.w(TAG, "Exception when doing dexopt : ", e);
                ret = -1;
            }
            if (ret < 0) {
                //error from installer
                return DEX_OPT_FAILED;
            }
        }

        return performed ? DEX_OPT_PERFORMED : DEX_OPT_SKIPPED;
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

    File getDataPathForUser(int userId) {
        return new File(mUserAppDataDir.getAbsolutePath() + File.separator + userId);
    }

    private File getDataPathForPackage(String packageName, int userId) {
        /*
         * Until we fully support multiple users, return the directory we
         * previously would have. The PackageManagerTests will need to be
         * revised when this is changed back..
         */
        if (userId == 0) {
            return new File(mAppDataDir, packageName);
        } else {
            return new File(mUserAppDataDir.getAbsolutePath() + File.separator + userId
                + File.separator + packageName);
        }
    }

    private PackageParser.Package scanPackageLI(PackageParser.Package pkg,
            int parseFlags, int scanMode, long currentTime) {
        File scanFile = new File(pkg.mScanPath);
        if (scanFile == null || pkg.applicationInfo.sourceDir == null ||
                pkg.applicationInfo.publicSourceDir == null) {
            // Bail out. The resource and code paths haven't been set.
            Slog.w(TAG, " Code and resource paths haven't been set correctly");
            mLastScanError = PackageManager.INSTALL_FAILED_INVALID_APK;
            return null;
        }
        mScanningPath = scanFile;

        if ((parseFlags&PackageParser.PARSE_IS_SYSTEM) != 0) {
            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }

        if (pkg.packageName.equals("android")) {
            synchronized (mPackages) {
                if (mAndroidApplication != null) {
                    Slog.w(TAG, "*************************************************");
                    Slog.w(TAG, "Core android package being redefined.  Skipping.");
                    Slog.w(TAG, " file=" + mScanningPath);
                    Slog.w(TAG, "*************************************************");
                    mLastScanError = PackageManager.INSTALL_FAILED_DUPLICATE_PACKAGE;
                    return null;
                }

                // Set up information for our fall-back user intent resolution
                // activity.
                mPlatformPackage = pkg;
                pkg.mVersionCode = mSdkVersion;
                mAndroidApplication = pkg.applicationInfo;
                mResolveActivity.applicationInfo = mAndroidApplication;
                mResolveActivity.name = ResolverActivity.class.getName();
                mResolveActivity.packageName = mAndroidApplication.packageName;
                mResolveActivity.processName = mAndroidApplication.processName;
                mResolveActivity.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
                mResolveActivity.flags = ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
                mResolveActivity.theme = com.android.internal.R.style.Theme_Holo_Dialog_Alert;
                mResolveActivity.exported = true;
                mResolveActivity.enabled = true;
                mResolveInfo.activityInfo = mResolveActivity;
                mResolveInfo.priority = 0;
                mResolveInfo.preferredOrder = 0;
                mResolveInfo.match = 0;
                mResolveComponentName = new ComponentName(
                        mAndroidApplication.packageName, mResolveActivity.name);
            }
        }

        if (DEBUG_PACKAGE_SCANNING) {
            if ((parseFlags & PackageParser.PARSE_CHATTY) != 0)
                Log.d(TAG, "Scanning package " + pkg.packageName);
        }

        if (mPackages.containsKey(pkg.packageName)
                || mSharedLibraries.containsKey(pkg.packageName)) {
            Slog.w(TAG, "Application package " + pkg.packageName
                    + " already installed.  Skipping duplicate.");
            mLastScanError = PackageManager.INSTALL_FAILED_DUPLICATE_PACKAGE;
            return null;
        }

        // Initialize package source and resource directories
        File destCodeFile = new File(pkg.applicationInfo.sourceDir);
        File destResourceFile = new File(pkg.applicationInfo.publicSourceDir);

        SharedUserSetting suid = null;
        PackageSetting pkgSetting = null;

        if (!isSystemApp(pkg)) {
            // Only system apps can use these features.
            pkg.mOriginalPackages = null;
            pkg.mRealPackage = null;
            pkg.mAdoptPermissions = null;
        }

        // writer
        synchronized (mPackages) {
            // Check all shared libraries and map to their actual file path.
            if (pkg.usesLibraries != null || pkg.usesOptionalLibraries != null) {
                if (mTmpSharedLibraries == null ||
                        mTmpSharedLibraries.length < mSharedLibraries.size()) {
                    mTmpSharedLibraries = new String[mSharedLibraries.size()];
                }
                int num = 0;
                int N = pkg.usesLibraries != null ? pkg.usesLibraries.size() : 0;
                for (int i=0; i<N; i++) {
                    final String file = mSharedLibraries.get(pkg.usesLibraries.get(i));
                    if (file == null) {
                        Slog.e(TAG, "Package " + pkg.packageName
                                + " requires unavailable shared library "
                                + pkg.usesLibraries.get(i) + "; failing!");
                        mLastScanError = PackageManager.INSTALL_FAILED_MISSING_SHARED_LIBRARY;
                        return null;
                    }
                    mTmpSharedLibraries[num] = file;
                    num++;
                }
                N = pkg.usesOptionalLibraries != null ? pkg.usesOptionalLibraries.size() : 0;
                for (int i=0; i<N; i++) {
                    final String file = mSharedLibraries.get(pkg.usesOptionalLibraries.get(i));
                    if (file == null) {
                        Slog.w(TAG, "Package " + pkg.packageName
                                + " desires unavailable shared library "
                                + pkg.usesOptionalLibraries.get(i) + "; ignoring!");
                    } else {
                        mTmpSharedLibraries[num] = file;
                        num++;
                    }
                }
                if (num > 0) {
                    pkg.usesLibraryFiles = new String[num];
                    System.arraycopy(mTmpSharedLibraries, 0,
                            pkg.usesLibraryFiles, 0, num);
                }
            }

            if (pkg.mSharedUserId != null) {
                suid = mSettings.getSharedUserLPw(pkg.mSharedUserId,
                        pkg.applicationInfo.flags, true);
                if (suid == null) {
                    Slog.w(TAG, "Creating application package " + pkg.packageName
                            + " for shared user failed");
                    mLastScanError = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                    return null;
                }
                if (DEBUG_PACKAGE_SCANNING) {
                    if ((parseFlags & PackageParser.PARSE_CHATTY) != 0)
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
            
            // Just create the setting, don't add it yet. For already existing packages
            // the PkgSetting exists already and doesn't have to be created.
            pkgSetting = mSettings.getPackageLPw(pkg, origPackage, realName, suid, destCodeFile,
                    destResourceFile, pkg.applicationInfo.nativeLibraryDir,
                    pkg.applicationInfo.flags, true, false);
            if (pkgSetting == null) {
                Slog.w(TAG, "Creating application package " + pkg.packageName + " failed");
                mLastScanError = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                return null;
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
                mTransferedPackages.add(origPackage.name);
                
                // No longer need to retain this.
                pkgSetting.origPackage = null;
            }
            
            if (realName != null) {
                // Make a note of it.
                mTransferedPackages.add(pkg.packageName);
            }
            
            if (mSettings.isDisabledSystemPackageLPr(pkg.packageName)) {
                pkg.applicationInfo.flags |= ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            }

            pkg.applicationInfo.uid = pkgSetting.appId;
            pkg.mExtras = pkgSetting;

            if (!verifySignaturesLP(pkgSetting, pkg)) {
                if ((parseFlags&PackageParser.PARSE_IS_SYSTEM_DIR) == 0) {
                    return null;
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
                        Log.w(TAG, "Signature mismatch for shared user : " + pkgSetting.sharedUser);
                        mLastScanError = PackageManager.INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES;
                        return null;
                    }
                }
                // File a report about this.
                String msg = "System package " + pkg.packageName
                        + " signature changed; retaining data.";
                reportSettingsProblem(Log.WARN, msg);
            }

            // Verify that this new package doesn't have any content providers
            // that conflict with existing packages.  Only do this if the
            // package isn't already installed, since we don't want to break
            // things that are installed.
            if ((scanMode&SCAN_NEW_INSTALL) != 0) {
                final int N = pkg.providers.size();
                int i;
                for (i=0; i<N; i++) {
                    PackageParser.Provider p = pkg.providers.get(i);
                    if (p.info.authority != null) {
                        String names[] = p.info.authority.split(";");
                        for (int j = 0; j < names.length; j++) {
                            if (mProviders.containsKey(names[j])) {
                                PackageParser.Provider other = mProviders.get(names[j]);
                                Slog.w(TAG, "Can't install because provider name " + names[j] +
                                        " (in package " + pkg.applicationInfo.packageName +
                                        ") is already used by "
                                        + ((other != null && other.getComponentName() != null)
                                                ? other.getComponentName().getPackageName() : "?"));
                                mLastScanError = PackageManager.INSTALL_FAILED_CONFLICTING_PROVIDER;
                                return null;
                            }
                        }
                    }
                }
            }

            if (pkg.mAdoptPermissions != null) {
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
        
        final long scanFileTime = scanFile.lastModified();
        final boolean forceDex = (scanMode&SCAN_FORCE_DEX) != 0;
        pkg.applicationInfo.processName = fixProcessName(
                pkg.applicationInfo.packageName,
                pkg.applicationInfo.processName,
                pkg.applicationInfo.uid);

        File dataPath;
        if (mPlatformPackage == pkg) {
            // The system package is special.
            dataPath = new File (Environment.getDataDirectory(), "system");
            pkg.applicationInfo.dataDir = dataPath.getPath();
        } else {
            // This is a normal package, need to make its data directory.
            dataPath = getDataPathForPackage(pkg.packageName, 0);

            boolean uidError = false;

            if (dataPath.exists()) {
                // XXX should really do this check for each user.
                mOutPermissions[1] = 0;
                FileUtils.getPermissions(dataPath.getPath(), mOutPermissions);

                // If we have mismatched owners for the data path, we have a problem.
                if (mOutPermissions[1] != pkg.applicationInfo.uid) {
                    boolean recovered = false;
                    if (mOutPermissions[1] == 0) {
                        // The directory somehow became owned by root.  Wow.
                        // This is probably because the system was stopped while
                        // installd was in the middle of messing with its libs
                        // directory.  Ask installd to fix that.
                        int ret = mInstaller.fixUid(pkgName, pkg.applicationInfo.uid,
                                pkg.applicationInfo.uid);
                        if (ret >= 0) {
                            recovered = true;
                            String msg = "Package " + pkg.packageName
                                    + " unexpectedly changed to uid 0; recovered to " +
                                    + pkg.applicationInfo.uid;
                            reportSettingsProblem(Log.WARN, msg);
                        }
                    }
                    if (!recovered && ((parseFlags&PackageParser.PARSE_IS_SYSTEM) != 0
                            || (scanMode&SCAN_BOOTING) != 0)) {
                        // If this is a system app, we can at least delete its
                        // current data so the application will still work.
                        int ret = mInstaller.remove(pkgName, 0);
                        if (ret >= 0) {
                            // TODO: Kill the processes first
                            // Remove the data directories for all users
                            sUserManager.removePackageForAllUsers(pkgName);
                            // Old data gone!
                            String prefix = (parseFlags&PackageParser.PARSE_IS_SYSTEM) != 0
                                    ? "System package " : "Third party package ";
                            String msg = prefix + pkg.packageName
                                    + " has changed from uid: "
                                    + mOutPermissions[1] + " to "
                                    + pkg.applicationInfo.uid + "; old data erased";
                            reportSettingsProblem(Log.WARN, msg);
                            recovered = true;

                            // And now re-install the app.
                            ret = mInstaller.install(pkgName, pkg.applicationInfo.uid,
                                    pkg.applicationInfo.uid);
                            if (ret == -1) {
                                // Ack should not happen!
                                msg = prefix + pkg.packageName
                                        + " could not have data directory re-created after delete.";
                                reportSettingsProblem(Log.WARN, msg);
                                mLastScanError = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                                return null;
                            }
                            // Create data directories for all users
                            sUserManager.installPackageForAllUsers(pkgName,
                                    pkg.applicationInfo.uid);
                        }
                        if (!recovered) {
                            mHasSystemUidErrors = true;
                        }
                    } else if (!recovered) {
                        // If we allow this install to proceed, we will be broken.
                        // Abort, abort!
                        mLastScanError = PackageManager.INSTALL_FAILED_UID_CHANGED;
                        return null;
                    }
                    if (!recovered) {
                        pkg.applicationInfo.dataDir = "/mismatched_uid/settings_"
                            + pkg.applicationInfo.uid + "/fs_"
                            + mOutPermissions[1];
                        pkg.applicationInfo.nativeLibraryDir = pkg.applicationInfo.dataDir;
                        String msg = "Package " + pkg.packageName
                                + " has mismatched uid: "
                                + mOutPermissions[1] + " on disk, "
                                + pkg.applicationInfo.uid + " in settings";
                        // writer
                        synchronized (mPackages) {
                            mSettings.mReadMessages.append(msg);
                            mSettings.mReadMessages.append('\n');
                            uidError = true;
                            if (!pkgSetting.uidError) {
                                reportSettingsProblem(Log.ERROR, msg);
                            }
                        }
                    }
                }
                pkg.applicationInfo.dataDir = dataPath.getPath();
            } else {
                if (DEBUG_PACKAGE_SCANNING) {
                    if ((parseFlags & PackageParser.PARSE_CHATTY) != 0)
                        Log.v(TAG, "Want this data dir: " + dataPath);
                }
                //invoke installer to do the actual installation
                int ret = mInstaller.install(pkgName, pkg.applicationInfo.uid,
                        pkg.applicationInfo.uid);
                if (ret < 0) {
                    // Error from installer
                    mLastScanError = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                    return null;
                }
                // Create data directories for all users
                sUserManager.installPackageForAllUsers(pkgName, pkg.applicationInfo.uid);

                if (dataPath.exists()) {
                    pkg.applicationInfo.dataDir = dataPath.getPath();
                } else {
                    Slog.w(TAG, "Unable to create data directory: " + dataPath);
                    pkg.applicationInfo.dataDir = null;
                }
            }

            /*
             * Set the data dir to the default "/data/data/<package name>/lib"
             * if we got here without anyone telling us different (e.g., apps
             * stored on SD card have their native libraries stored in the ASEC
             * container with the APK).
             *
             * This happens during an upgrade from a package settings file that
             * doesn't have a native library path attribute at all.
             */
            if (pkg.applicationInfo.nativeLibraryDir == null && pkg.applicationInfo.dataDir != null) {
                if (pkgSetting.nativeLibraryPathString == null) {
                    final String nativeLibraryPath = new File(dataPath, LIB_DIR_NAME).getPath();
                    pkg.applicationInfo.nativeLibraryDir = nativeLibraryPath;
                    pkgSetting.nativeLibraryPathString = nativeLibraryPath;
                } else {
                    pkg.applicationInfo.nativeLibraryDir = pkgSetting.nativeLibraryPathString;
                }
            }

            pkgSetting.uidError = uidError;
        }

        String path = scanFile.getPath();
        /* Note: We don't want to unpack the native binaries for
         *        system applications, unless they have been updated
         *        (the binaries are already under /system/lib).
         *        Also, don't unpack libs for apps on the external card
         *        since they should have their libraries in the ASEC
         *        container already.
         *
         *        In other words, we're going to unpack the binaries
         *        only for non-system apps and system app upgrades.
         */
        if (pkg.applicationInfo.nativeLibraryDir != null) {
            try {
                final File nativeLibraryDir = new File(pkg.applicationInfo.nativeLibraryDir);
                final String dataPathString = dataPath.getCanonicalPath();

                if (isSystemApp(pkg) && !isUpdatedSystemApp(pkg)) {
                    /*
                     * Upgrading from a previous version of the OS sometimes
                     * leaves native libraries in the /data/data/<app>/lib
                     * directory for system apps even when they shouldn't be.
                     * Recent changes in the JNI library search path
                     * necessitates we remove those to match previous behavior.
                     */
                    if (NativeLibraryHelper.removeNativeBinariesFromDirLI(nativeLibraryDir)) {
                        Log.i(TAG, "removed obsolete native libraries for system package "
                                + path);
                    }
                } else if (nativeLibraryDir.getParentFile().getCanonicalPath()
                        .equals(dataPathString)) {
                    /*
                     * Make sure the native library dir isn't a symlink to
                     * something. If it is, ask installd to remove it and create
                     * a directory so we can copy to it afterwards.
                     */
                    boolean isSymLink;
                    try {
                        isSymLink = S_ISLNK(Libcore.os.lstat(nativeLibraryDir.getPath()).st_mode);
                    } catch (ErrnoException e) {
                        // This shouldn't happen, but we'll fail-safe.
                        isSymLink = true;
                    }
                    if (isSymLink) {
                        mInstaller.unlinkNativeLibraryDirectory(dataPathString);
                    }

                    /*
                     * If this is an internal application or our
                     * nativeLibraryPath points to our data directory, unpack
                     * the libraries if necessary.
                     */
                    NativeLibraryHelper.copyNativeBinariesIfNeededLI(scanFile, nativeLibraryDir);
                } else {
                    Slog.i(TAG, "Linking native library dir for " + path);
                    mInstaller.linkNativeLibraryDirectory(dataPathString,
                            pkg.applicationInfo.nativeLibraryDir);
                }
            } catch (IOException ioe) {
                Log.e(TAG, "Unable to get canonical file " + ioe.toString());
            }
        }
        pkg.mScanPath = path;

        if ((scanMode&SCAN_NO_DEX) == 0) {
            if (performDexOptLI(pkg, forceDex, (scanMode&SCAN_DEFER_DEX) != 0)
                    == DEX_OPT_FAILED) {
                mLastScanError = PackageManager.INSTALL_FAILED_DEXOPT;
                return null;
            }
        }

        if (mFactoryTest && pkg.requestedPermissions.contains(
                android.Manifest.permission.FACTORY_TEST)) {
            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_FACTORY_TEST;
        }

        // Request the ActivityManager to kill the process(only for existing packages)
        // so that we do not end up in a confused state while the user is still using the older
        // version of the application while the new one gets installed.
        if ((parseFlags & PackageManager.INSTALL_REPLACE_EXISTING) != 0) {
            killApplication(pkg.applicationInfo.packageName,
                        pkg.applicationInfo.uid);
        }

        // writer
        synchronized (mPackages) {
            // We don't expect installation to fail beyond this point,
            if ((scanMode&SCAN_MONITOR) != 0) {
                mAppDirs.put(pkg.mPath, pkg);
            }
            // Add the new setting to mSettings
            mSettings.insertPackageSettingLPw(pkgSetting, pkg);
            // Add the new setting to mPackages
            mPackages.put(pkg.applicationInfo.packageName, pkg);
            // Make sure we don't accidentally delete its data.
            mSettings.mPackagesToBeCleaned.remove(pkgName);
            
            // Take care of first install / last update times.
            if (currentTime != 0) {
                if (pkgSetting.firstInstallTime == 0) {
                    pkgSetting.firstInstallTime = pkgSetting.lastUpdateTime = currentTime;
                } else if ((scanMode&SCAN_UPDATE_TIME) != 0) {
                    pkgSetting.lastUpdateTime = currentTime;
                }
            } else if (pkgSetting.firstInstallTime == 0) {
                // We need *something*.  Take time time stamp of the file.
                pkgSetting.firstInstallTime = pkgSetting.lastUpdateTime = scanFileTime;
            } else if ((parseFlags&PackageParser.PARSE_IS_SYSTEM_DIR) != 0) {
                if (scanFileTime != pkgSetting.timeStamp) {
                    // A package on the system image has changed; consider this
                    // to be an update.
                    pkgSetting.lastUpdateTime = scanFileTime;
                }
            }

            int N = pkg.providers.size();
            StringBuilder r = null;
            int i;
            for (i=0; i<N; i++) {
                PackageParser.Provider p = pkg.providers.get(i);
                p.info.processName = fixProcessName(pkg.applicationInfo.processName,
                        p.info.processName, pkg.applicationInfo.uid);
                mProvidersByComponent.put(new ComponentName(p.info.packageName,
                        p.info.name), p);
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
                        if (!mProviders.containsKey(names[j])) {
                            mProviders.put(names[j], p);
                            if (p.info.authority == null) {
                                p.info.authority = names[j];
                            } else {
                                p.info.authority = p.info.authority + ";" + names[j];
                            }
                            if (DEBUG_PACKAGE_SCANNING) {
                                if ((parseFlags & PackageParser.PARSE_CHATTY) != 0)
                                    Log.d(TAG, "Registered content provider: " + names[j]
                                            + ", className = " + p.info.name + ", isSyncable = "
                                            + p.info.isSyncable);
                            }
                        } else {
                            PackageParser.Provider other = mProviders.get(names[j]);
                            Slog.w(TAG, "Skipping provider name " + names[j] +
                                    " (in package " + pkg.applicationInfo.packageName +
                                    "): name already used by "
                                    + ((other != null && other.getComponentName() != null)
                                            ? other.getComponentName().getPackageName() : "?"));
                        }
                    }
                }
                if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                    if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                    if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                HashMap<String, BasePermission> permissionMap =
                        p.tree ? mSettings.mPermissionTrees
                        : mSettings.mPermissions;
                p.group = mPermissionGroups.get(p.info.group);
                if (p.info.group == null || p.group != null) {
                    BasePermission bp = permissionMap.get(p.info.name);
                    if (bp == null) {
                        bp = new BasePermission(p.info.name, p.info.packageName,
                                BasePermission.TYPE_NORMAL);
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
                                if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                    } else if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                } else {
                    Slog.w(TAG, "Permission " + p.info.name + " from package "
                            + p.info.packageName + " ignored: no group "
                            + p.group);
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
                a.info.dataDir = pkg.applicationInfo.dataDir;
                a.info.nativeLibraryDir = pkg.applicationInfo.nativeLibraryDir;
                mInstrumentation.put(a.getComponentName(), a);
                if ((parseFlags&PackageParser.PARSE_CHATTY) != 0) {
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
                    mProtectedBroadcasts.add(pkg.protectedBroadcasts.get(i));
                }
            }

            pkgSetting.setTimeStamp(scanFileTime);
        }

        return pkg;
    }

    private void killApplication(String pkgName, int uid) {
        // Request the ActivityManager to kill the process(only for existing packages)
        // so that we do not end up in a confused state while the user is still using the older
        // version of the application while the new one gets installed.
        IActivityManager am = ActivityManagerNative.getDefault();
        if (am != null) {
            try {
                am.killApplicationWithUid(pkgName, uid);
            } catch (RemoteException e) {
            }
        }
    }

    // NOTE: this method can return null if the SystemServer is still
    // initializing
    public IAssetRedirectionManager getAssetRedirectionManager() {
        if (mAssetRedirectionManager != null) {
            return mAssetRedirectionManager;
        }
        IBinder b = ServiceManager.getService("assetredirection");
        mAssetRedirectionManager = IAssetRedirectionManager.Stub.asInterface(b);
        return mAssetRedirectionManager;
    }

    private void cleanAssetRedirections(PackageParser.Package pkg) {
        IAssetRedirectionManager rm = getAssetRedirectionManager();
        if (rm == null) {
            return;
        }
        try {
            if (pkg.mIsThemeApk) {
                rm.clearRedirectionMapsByTheme(pkg.packageName, null);
            } else {
                rm.clearPackageRedirectionMap(pkg.packageName);
            }
        } catch (RemoteException e) {
        }
    }

    void removePackageLI(PackageParser.Package pkg, boolean chatty) {
        if (DEBUG_INSTALL) {
            if (chatty)
                Log.d(TAG, "Removing package " + pkg.applicationInfo.packageName);
        }

        // writer
        synchronized (mPackages) {
            cleanAssetRedirections(pkg);

            mPackages.remove(pkg.applicationInfo.packageName);
            if (pkg.mPath != null) {
                mAppDirs.remove(pkg.mPath);
            }

            int N = pkg.providers.size();
            StringBuilder r = null;
            int i;
            for (i=0; i<N; i++) {
                PackageParser.Provider p = pkg.providers.get(i);
                mProvidersByComponent.remove(new ComponentName(p.info.packageName,
                        p.info.name));
                if (p.info.authority == null) {

                    /* The is another ContentProvider with this authority when
                     * this app was installed so this authority is null,
                     * Ignore it as we don't have to unregister the provider.
                     */
                    continue;
                }
                String names[] = p.info.authority.split(";");
                for (int j = 0; j < names.length; j++) {
                    if (mProviders.get(names[j]) == p) {
                        mProviders.remove(names[j]);
                        if (DEBUG_REMOVE) {
                            if (chatty)
                                Log.d(TAG, "Unregistered content provider: " + names[j]
                                        + ", className = " + p.info.name + ", isSyncable = "
                                        + p.info.isSyncable);
                        }
                    }
                }
                if (chatty) {
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
                if (chatty) {
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
                if (chatty) {
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
                    if (chatty) {
                        if (r == null) {
                            r = new StringBuilder(256);
                        } else {
                            r.append(' ');
                        }
                        r.append(p.info.name);
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
                if (chatty) {
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
        }
    }

    private static final boolean isPackageFilename(String name) {
        return name != null && name.endsWith(".apk");
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

    private void updatePermissionsLPw(String changingPkg,
            PackageParser.Package pkgInfo, int flags) {
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
                    grantPermissionsLPw(pkg, (flags&UPDATE_PERMISSIONS_REPLACE_ALL) != 0);
                }
            }
        }
        
        if (pkgInfo != null) {
            grantPermissionsLPw(pkgInfo, (flags&UPDATE_PERMISSIONS_REPLACE_PKG) != 0);
        }
    }

    private void grantPermissionsLPw(PackageParser.Package pkg, boolean replace) {
        final PackageSetting ps = (PackageSetting) pkg.mExtras;
        if (ps == null) {
            return;
        }
        final GrantedPermissions gp = ps.sharedUser != null ? ps.sharedUser : ps;
        HashSet<String> origPermissions = gp.grantedPermissions;
        boolean changedPermission = false;

        if (replace) {
            ps.permissionsFixed = false;
            if (gp == ps) {
                origPermissions = new HashSet<String>(gp.grantedPermissions);
                gp.grantedPermissions.clear();
                gp.gids = mGlobalGids;
            }
        }

        if (gp.gids == null) {
            gp.gids = mGlobalGids;
        }

        final int N = pkg.requestedPermissions.size();
        for (int i=0; i<N; i++) {
            final String name = pkg.requestedPermissions.get(i);
            //final boolean required = pkg.requestedPermssionsRequired.get(i);
            final BasePermission bp = mSettings.mPermissions.get(name);
            if (DEBUG_INSTALL) {
                if (gp != ps) {
                    Log.i(TAG, "Package " + pkg.packageName + " checking " + name + ": " + bp);
                }
            }
            if (bp != null && bp.packageSetting != null) {
                final String perm = bp.name;
                boolean allowed;
                boolean allowedSig = false;
                final int level = bp.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                if (level == PermissionInfo.PROTECTION_NORMAL
                        || level == PermissionInfo.PROTECTION_DANGEROUS) {
                    allowed = true;
                } else if (bp.packageSetting == null) {
                    // This permission is invalid; skip it.
                    allowed = false;
                } else if (level == PermissionInfo.PROTECTION_SIGNATURE) {
                    allowed = (compareSignatures(
                            bp.packageSetting.signatures.mSignatures, pkg.mSignatures)
                                    == PackageManager.SIGNATURE_MATCH)
                            || (compareSignatures(mPlatformPackage.mSignatures, pkg.mSignatures)
                                    == PackageManager.SIGNATURE_MATCH);
                    if (!allowed && (bp.protectionLevel
                            & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
                        if (isSystemApp(pkg)) {
                            // For updated system applications, a system permission
                            // is granted only if it had been defined by the original application.
                            if (isUpdatedSystemApp(pkg)) {
                                final PackageSetting sysPs = mSettings
                                        .getDisabledSystemPkgLPr(pkg.packageName);
                                final GrantedPermissions origGp = sysPs.sharedUser != null
                                        ? sysPs.sharedUser : sysPs;
                                if (origGp.grantedPermissions.contains(perm)) {
                                    allowed = true;
                                } else {
                                    allowed = false;
                                }
                            } else {
                                allowed = true;
                            }
                        }
                    }
                    if (!allowed && (bp.protectionLevel
                            & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                        // For development permissions, a development permission
                        // is granted only if it was already granted.
                        if (origPermissions.contains(perm)) {
                            allowed = true;
                        } else {
                            allowed = false;
                        }
                    }
                    if (allowed) {
                        allowedSig = true;
                    }
                } else {
                    allowed = false;
                }
                if (DEBUG_INSTALL) {
                    if (gp != ps) {
                        Log.i(TAG, "Package " + pkg.packageName + " granting " + perm);
                    }
                }
                if (allowed) {
                    if ((ps.pkgFlags&ApplicationInfo.FLAG_SYSTEM) == 0
                            && ps.permissionsFixed) {
                        // If this is an existing, non-system package, then
                        // we can't add any new permissions to it.
                        if (!allowedSig && !gp.grantedPermissions.contains(perm)) {
                            allowed = false;
                            // Except...  if this is a permission that was added
                            // to the platform (note: need to only do this when
                            // updating the platform).
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
                        }
                    }
                    if (allowed) {
                        if (!gp.grantedPermissions.contains(perm)) {
                            changedPermission = true;
                            gp.grantedPermissions.add(perm);
                            gp.gids = appendInts(gp.gids, bp.gids);
                        } else if (!ps.haveGids) {
                            gp.gids = appendInts(gp.gids, bp.gids);
                        }
                    } else {
                        Slog.w(TAG, "Not granting permission " + perm
                                + " to package " + pkg.packageName
                                + " because it was previously installed without");
                    }
                } else {
                    if (gp.grantedPermissions.remove(perm)) {
                        changedPermission = true;
                        gp.gids = removeInts(gp.gids, bp.gids);
                        Slog.i(TAG, "Un-granting permission " + perm
                                + " from package " + pkg.packageName
                                + " (protectionLevel=" + bp.protectionLevel
                                + " flags=0x" + Integer.toHexString(pkg.applicationInfo.flags)
                                + ")");
                    } else {
                        Slog.w(TAG, "Not granting permission " + perm
                                + " to package " + pkg.packageName
                                + " (protectionLevel=" + bp.protectionLevel
                                + " flags=0x" + Integer.toHexString(pkg.applicationInfo.flags)
                                + ")");
                    }
                }
            } else {
                Slog.w(TAG, "Unknown permission " + name
                        + " in package " + pkg.packageName);
            }
        }

        if ((changedPermission || replace) && !ps.permissionsFixed &&
                ((ps.pkgFlags&ApplicationInfo.FLAG_SYSTEM) == 0) ||
                ((ps.pkgFlags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)){
            // This is the first that we have heard about this package, so the
            // permissions we have now selected are fixed until explicitly
            // changed.
            ps.permissionsFixed = true;
        }
        ps.haveGids = true;
    }
    
    private final class ActivityIntentResolver
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
            return super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0, userId);
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
            ArrayList<ArrayList<PackageParser.ActivityIntentInfo>> listCut =
                new ArrayList<ArrayList<PackageParser.ActivityIntentInfo>>(N);

            ArrayList<PackageParser.ActivityIntentInfo> intentFilters;
            for (int i = 0; i < N; ++i) {
                intentFilters = packageActivities.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    listCut.add(intentFilters);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        public final void addActivity(PackageParser.Activity a, String type) {
            final boolean systemApp = isSystemApp(a.info.applicationInfo);
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
                if (!systemApp && intent.getPriority() > 0 && "activity".equals(type)) {
                    intent.setPriority(0);
                    Log.w(TAG, "Package " + a.info.applicationInfo.packageName + " has activity "
                            + a.className + " with priority > 0, forcing to 0");
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
        protected boolean isFilterStopped(PackageParser.ActivityIntentInfo filter, int userId) {
            if (!sUserManager.exists(userId)) return true;
            PackageParser.Package p = filter.activity.owner;
            if (p != null) {
                PackageSetting ps = (PackageSetting)p.mExtras;
                if (ps != null) {
                    // System apps are never considered stopped for purposes of
                    // filtering, because there may be no way for the user to
                    // actually re-launch them.
                    return ps.getStopped(userId) && (ps.pkgFlags&ApplicationInfo.FLAG_SYSTEM) == 0;
                }
            }
            return false;
        }

        @Override
        protected String packageForFilter(PackageParser.ActivityIntentInfo info) {
            return info.activity.owner.packageName;
        }
        
        @Override
        protected ResolveInfo newResult(PackageParser.ActivityIntentInfo info,
                int match, int userId) {
            if (!sUserManager.exists(userId)) return null;
            if (!mSettings.isEnabledLPr(info.activity.info, mFlags, userId)) {
                return null;
            }
            final PackageParser.Activity activity = info.activity;
            if (mSafeMode && (activity.info.applicationInfo.flags
                    &ApplicationInfo.FLAG_SYSTEM) == 0) {
                return null;
            }
            final ResolveInfo res = new ResolveInfo();
            PackageSetting ps = (PackageSetting) activity.owner.mExtras;
            res.activityInfo = PackageParser.generateActivityInfo(activity, mFlags,
                    ps != null ? ps.getStopped(userId) : false,
                    ps != null ? ps.getEnabled(userId) : COMPONENT_ENABLED_STATE_DEFAULT,
                    userId);
            if ((mFlags&PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = info;
            }
            res.priority = info.getPriority();
            res.preferredOrder = activity.owner.mPreferredOrder;
            //System.out.println("Result: " + res.activityInfo.className +
            //                   " = " + res.priority);
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            res.system = isSystemApp(res.activityInfo.applicationInfo);
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
                    out.print(filter.activity.getComponentShortName());
                    out.print(" filter ");
                    out.println(Integer.toHexString(System.identityHashCode(filter)));
        }

//        List<ResolveInfo> filterEnabled(List<ResolveInfo> resolveInfoList) {
//            final Iterator<ResolveInfo> i = resolveInfoList.iterator();
//            final List<ResolveInfo> retList = Lists.newArrayList();
//            while (i.hasNext()) {
//                final ResolveInfo resolveInfo = i.next();
//                if (isEnabledLP(resolveInfo.activityInfo)) {
//                    retList.add(resolveInfo);
//                }
//            }
//            return retList;
//        }

        // Keys are String (activity class name), values are Activity.
        private final HashMap<ComponentName, PackageParser.Activity> mActivities
                = new HashMap<ComponentName, PackageParser.Activity>();
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
            ArrayList<ArrayList<PackageParser.ServiceIntentInfo>> listCut =
                new ArrayList<ArrayList<PackageParser.ServiceIntentInfo>>(N);

            ArrayList<PackageParser.ServiceIntentInfo> intentFilters;
            for (int i = 0; i < N; ++i) {
                intentFilters = packageServices.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    listCut.add(intentFilters);
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
        protected boolean isFilterStopped(PackageParser.ServiceIntentInfo filter, int userId) {
            if (!sUserManager.exists(userId)) return true;
            PackageParser.Package p = filter.service.owner;
            if (p != null) {
                PackageSetting ps = (PackageSetting)p.mExtras;
                if (ps != null) {
                    // System apps are never considered stopped for purposes of
                    // filtering, because there may be no way for the user to
                    // actually re-launch them.
                    return ps.getStopped(userId)
                            && (ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) == 0;
                }
            }
            return false;
        }

        @Override
        protected String packageForFilter(PackageParser.ServiceIntentInfo info) {
            return info.service.owner.packageName;
        }
        
        @Override
        protected ResolveInfo newResult(PackageParser.ServiceIntentInfo filter,
                int match, int userId) {
            if (!sUserManager.exists(userId)) return null;
            final PackageParser.ServiceIntentInfo info = (PackageParser.ServiceIntentInfo)filter;
            if (!mSettings.isEnabledLPr(info.service.info, mFlags, userId)) {
                return null;
            }
            final PackageParser.Service service = info.service;
            if (mSafeMode && (service.info.applicationInfo.flags
                    &ApplicationInfo.FLAG_SYSTEM) == 0) {
                return null;
            }
            final ResolveInfo res = new ResolveInfo();
            PackageSetting ps = (PackageSetting) service.owner.mExtras;
            res.serviceInfo = PackageParser.generateServiceInfo(service, mFlags,
                    ps != null ? ps.getStopped(userId) : false,
                    ps != null ? ps.getEnabled(userId) : COMPONENT_ENABLED_STATE_DEFAULT,
                    userId);
            if ((mFlags&PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = filter;
            }
            res.priority = info.getPriority();
            res.preferredOrder = service.owner.mPreferredOrder;
            //System.out.println("Result: " + res.activityInfo.className +
            //                   " = " + res.priority);
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            res.system = isSystemApp(res.serviceInfo.applicationInfo);
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
                    out.print(filter.service.getComponentShortName());
                    out.print(" filter ");
                    out.println(Integer.toHexString(System.identityHashCode(filter)));
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
        private final HashMap<ComponentName, PackageParser.Service> mServices
                = new HashMap<ComponentName, PackageParser.Service>();
        private int mFlags;
    };

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

    static final void sendPackageBroadcast(String action, String pkg, String intentCategory,
            Bundle extras, String targetPkg, IIntentReceiver finishedReceiver, int userId) {
        IActivityManager am = ActivityManagerNative.getDefault();
        if (am != null) {
            try {
                int[] userIds = userId == UserId.USER_ALL
                        ? sUserManager.getUserIds() 
                        : new int[] {userId};
                for (int id : userIds) {
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
                    if (uid > 0 && id > 0) {
                        uid = UserId.getUid(id, UserId.getAppId(uid));
                        intent.putExtra(Intent.EXTRA_UID, uid);
                    }
                    intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                    if (intentCategory != null) {
                        intent.addCategory(intentCategory);
                    }
                    am.broadcastIntent(null, intent, null, finishedReceiver,
                            0, null, null, null, finishedReceiver != null, false, id);
                }
            } catch (RemoteException ex) {
            }
        }
    }

    /**
     * Check if the external storage media is available. This is true if there
     * is a mounted external storage medium or if the external storage is
     * emulated.
     */
    private boolean isExternalMediaAvailable() {
        return mMediaMounted || Environment.isExternalStorageEmulated();
    }

    public String nextPackageToClean(String lastPackage) {
        // writer
        synchronized (mPackages) {
            if (!isExternalMediaAvailable()) {
                // If the external storage is no longer mounted at this point,
                // the caller may not have been able to delete all of this
                // packages files and can not delete any more.  Bail.
                return null;
            }
            if (lastPackage != null) {
                mSettings.mPackagesToBeCleaned.remove(lastPackage);
            }
            return mSettings.mPackagesToBeCleaned.size() > 0
                    ? mSettings.mPackagesToBeCleaned.get(0) : null;
        }
    }

    void schedulePackageCleaning(String packageName) {
        mHandler.sendMessage(mHandler.obtainMessage(START_CLEANING_PACKAGE, packageName));
    }
    
    void startCleaningPackages() {
        // reader
        synchronized (mPackages) {
            if (!isExternalMediaAvailable()) {
                return;
            }
            if (mSettings.mPackagesToBeCleaned.size() <= 0) {
                return;
            }
        }
        Intent intent = new Intent(PackageManager.ACTION_CLEAN_EXTERNAL_STORAGE);
        intent.setComponent(DEFAULT_CONTAINER_COMPONENT);
        IActivityManager am = ActivityManagerNative.getDefault();
        if (am != null) {
            try {
                am.startService(null, intent, null);
            } catch (RemoteException e) {
            }
        }
    }
    
    private final class AppDirObserver extends FileObserver {
        public AppDirObserver(String path, int mask, boolean isrom) {
            super(path, mask);
            mRootDir = path;
            mIsRom = isrom;
        }

        public void onEvent(int event, String path) {
            String removedPackage = null;
            int removedUid = -1;
            String addedPackage = null;
            int addedUid = -1;
            String category = null;

            // TODO post a message to the handler to obtain serial ordering
            synchronized (mInstallLock) {
                String fullPathStr = null;
                File fullPath = null;
                if (path != null) {
                    fullPath = new File(mRootDir, path);
                    fullPathStr = fullPath.getPath();
                }

                if (DEBUG_APP_DIR_OBSERVER)
                    Log.v(TAG, "File " + fullPathStr + " changed: " + Integer.toHexString(event));

                if (!isPackageFilename(path)) {
                    if (DEBUG_APP_DIR_OBSERVER)
                        Log.v(TAG, "Ignoring change of non-package file: " + fullPathStr);
                    return;
                }

                // Ignore packages that are being installed or
                // have just been installed.
                if (ignoreCodePath(fullPathStr)) {
                    return;
                }
                PackageParser.Package p = null;
                // reader
                synchronized (mPackages) {
                    p = mAppDirs.get(fullPathStr);
                }
                if ((event&REMOVE_EVENTS) != 0) {
                    if (p != null) {
                        if (p.mIsThemeApk) {
                            category = Intent.CATEGORY_THEME_PACKAGE_INSTALLED_STATE_CHANGE;
                        }
                        removePackageLI(p, true);
                        removedPackage = p.applicationInfo.packageName;
                        removedUid = p.applicationInfo.uid;
                    }
                }

                if ((event&ADD_EVENTS) != 0) {
                    if (p == null) {
                        p = scanPackageLI(fullPath,
                                (mIsRom ? PackageParser.PARSE_IS_SYSTEM
                                        | PackageParser.PARSE_IS_SYSTEM_DIR: 0) |
                                PackageParser.PARSE_CHATTY |
                                PackageParser.PARSE_MUST_BE_APK,
                                SCAN_MONITOR | SCAN_NO_PATHS | SCAN_UPDATE_TIME,
                                System.currentTimeMillis());
                        if (p != null) {
                            /*
                             * TODO this seems dangerous as the package may have
                             * changed since we last acquired the mPackages
                             * lock.
                             */
                            // writer
                            synchronized (mPackages) {
                                updatePermissionsLPw(p.packageName, p,
                                        p.permissions.size() > 0 ? UPDATE_PERMISSIONS_ALL : 0);
                            }
                            addedPackage = p.applicationInfo.packageName;
                            addedUid = p.applicationInfo.uid;
                        }
                    }
                    if (p != null && p.mIsThemeApk) {
                        category = Intent.CATEGORY_THEME_PACKAGE_INSTALLED_STATE_CHANGE;
                    }
                }

                // reader
                synchronized (mPackages) {
                    mSettings.writeLPr();
                }
            }

            if (removedPackage != null) {
                Bundle extras = new Bundle(1);
                extras.putInt(Intent.EXTRA_UID, removedUid);
                extras.putBoolean(Intent.EXTRA_DATA_REMOVED, false);
                sendPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, removedPackage, category,
                        extras, null, null, UserId.USER_ALL);
            }
            if (addedPackage != null) {
                Bundle extras = new Bundle(1);
                extras.putInt(Intent.EXTRA_UID, addedUid);
                sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, addedPackage, category,
                        extras, null, null, UserId.USER_ALL);
            }
        }

        private final String mRootDir;
        private final boolean mIsRom;
    }

    /* Called when a downloaded package installation has been confirmed by the user */
    public void installPackage(
            final Uri packageURI, final IPackageInstallObserver observer, final int flags) {
        installPackage(packageURI, observer, flags, null);
    }

    /* Called when a downloaded package installation has been confirmed by the user */
    public void installPackage(
            final Uri packageURI, final IPackageInstallObserver observer, final int flags,
            final String installerPackageName) {
        installPackageWithVerification(packageURI, observer, flags, installerPackageName, null,
                null, null);
    }

    @Override
    public void installPackageWithVerification(Uri packageURI, IPackageInstallObserver observer,
            int flags, String installerPackageName, Uri verificationURI,
            ManifestDigest manifestDigest, ContainerEncryptionParams encryptionParams) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.INSTALL_PACKAGES, null);

        final int uid = Binder.getCallingUid();

        final int filteredFlags;

        if (uid == Process.SHELL_UID || uid == 0) {
            if (DEBUG_INSTALL) {
                Slog.v(TAG, "Install from ADB");
            }
            filteredFlags = flags | PackageManager.INSTALL_FROM_ADB;
        } else {
            filteredFlags = flags & ~PackageManager.INSTALL_FROM_ADB;
        }

        final Message msg = mHandler.obtainMessage(INIT_COPY);
        msg.obj = new InstallParams(packageURI, observer, filteredFlags, installerPackageName,
                verificationURI, manifestDigest, encryptionParams);
        mHandler.sendMessage(msg);
    }

    @Override
    public void verifyPendingInstall(int id, int verificationCode) throws RemoteException {
        final Message msg = mHandler.obtainMessage(PACKAGE_VERIFIED);
        final PackageVerificationResponse response = new PackageVerificationResponse(
                verificationCode, Binder.getCallingUid());
        msg.arg1 = id;
        msg.obj = response;
        mHandler.sendMessage(msg);
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

    public void finishPackageInstall(int token) {
        enforceSystemOrRoot("Only the system is allowed to finish installs");

        if (DEBUG_INSTALL) {
            Slog.v(TAG, "BM finishing package install for " + token);
        }

        final Message msg = mHandler.obtainMessage(POST_INSTALL, token, 0);
        mHandler.sendMessage(msg);
    }

    /**
     * Get the verification agent timeout.
     *
     * @return verification timeout in milliseconds
     */
    private long getVerificationTimeout() {
        return android.provider.Settings.Secure.getLong(mContext.getContentResolver(),
                android.provider.Settings.Secure.PACKAGE_VERIFIER_TIMEOUT,
                DEFAULT_VERIFICATION_TIMEOUT);
    }

    /**
     * Check whether or not package verification has been enabled.
     *
     * @return true if verification should be performed
     */
    private boolean isVerificationEnabled() {
        return android.provider.Settings.Secure.getInt(mContext.getContentResolver(),
                android.provider.Settings.Secure.PACKAGE_VERIFIER_ENABLE,
                DEFAULT_VERIFY_ENABLE ? 1 : 0) == 1 ? true : false;
    }

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
                throw new SecurityException("Unknown calling uid " + uid);
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
                res.returnCode = currentStatus;
                res.uid = -1;
                res.pkg = null;
                res.removedInfo = new PackageRemovedInfo();
                if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
                    args.doPreInstall(res.returnCode);
                    synchronized (mInstallLock) {
                        installPackageLI(args, true, res);
                    }
                    args.doPostInstall(res.returnCode, res.uid);
                }

                // A restore should be performed at this point if (a) the install
                // succeeded, (b) the operation is not an update, and (c) the new
                // package has a backupAgent defined.
                final boolean update = res.removedInfo.removedPackage != null;
                boolean doRestore = (!update
                        && res.pkg != null
                        && res.pkg.applicationInfo.backupAgentName != null);

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
                        try {
                            bm.restoreAtInstall(res.pkg.applicationInfo.packageName, token);
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
                    Message msg = mHandler.obtainMessage(POST_INSTALL, token, 0);
                    mHandler.sendMessage(msg);
                }
            }
        });
    }

    private abstract class HandlerParams {
        private static final int MAX_RETRIES = 4;

        /**
         * Number of times startCopy() has been attempted and had a non-fatal
         * error.
         */
        private int mRetries = 0;

        final boolean startCopy() {
            boolean res;
            try {
                if (DEBUG_INSTALL) Slog.i(TAG, "startCopy");

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
            mObserver = observer;
            mStats = stats;
        }

        @Override
        void handleStartCopy() throws RemoteException {
            synchronized (mInstallLock) {
                mSuccess = getPackageSizeInfoLI(mStats.packageName, mStats);
            }

            final boolean mounted;
            if (Environment.isExternalStorageEmulated()) {
                mounted = true;
            } else {
                final String status = Environment.getExternalStorageState();

                mounted = status.equals(Environment.MEDIA_MOUNTED)
                        || status.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
            }

            if (mounted) {
                final File externalCacheDir = Environment
                        .getExternalStorageAppCacheDirectory(mStats.packageName);
                final long externalCacheSize = mContainerService
                        .calculateDirectorySize(externalCacheDir.getPath());
                mStats.externalCacheSize = externalCacheSize;

                final File externalDataDir = Environment
                        .getExternalStorageAppDataDirectory(mStats.packageName);
                long externalDataSize = mContainerService.calculateDirectorySize(externalDataDir
                        .getPath());

                if (externalCacheDir.getParentFile().equals(externalDataDir)) {
                    externalDataSize -= externalCacheSize;
                }
                mStats.externalDataSize = externalDataSize;

                final File externalMediaDir = Environment
                        .getExternalStorageAppMediaDirectory(mStats.packageName);
                mStats.externalMediaSize = mContainerService
                        .calculateDirectorySize(externalMediaDir.getPath());

                final File externalObbDir = Environment
                        .getExternalStorageAppObbDirectory(mStats.packageName);
                mStats.externalObbSize = mContainerService.calculateDirectorySize(externalObbDir
                        .getPath());
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

    class InstallParams extends HandlerParams {
        final IPackageInstallObserver observer;
        int flags;

        private final Uri mPackageURI;
        final String installerPackageName;
        final Uri verificationURI;
        final ManifestDigest manifestDigest;
        private InstallArgs mArgs;
        private int mRet;
        private File mTempPackage;
        final ContainerEncryptionParams encryptionParams;

        InstallParams(Uri packageURI,
                IPackageInstallObserver observer, int flags,
                String installerPackageName, Uri verificationURI, ManifestDigest manifestDigest,
                ContainerEncryptionParams encryptionParams) {
            this.mPackageURI = packageURI;
            this.flags = flags;
            this.observer = observer;
            this.installerPackageName = installerPackageName;
            this.verificationURI = verificationURI;
            this.manifestDigest = manifestDigest;
            this.encryptionParams = encryptionParams;
        }

        private int installLocationPolicy(PackageInfoLite pkgLite, int flags) {
            String packageName = pkgLite.packageName;
            int installLocation = pkgLite.installLocation;
            boolean onSd = (flags & PackageManager.INSTALL_EXTERNAL) != 0;
            // reader
            synchronized (mPackages) {
                PackageParser.Package pkg = mPackages.get(packageName);
                if (pkg != null) {
                    if ((flags & PackageManager.INSTALL_REPLACE_EXISTING) != 0) {
                        // Check for updated system application.
                        if ((pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
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
                                if (isExternal(pkg)) {
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
            final boolean onSd = (flags & PackageManager.INSTALL_EXTERNAL) != 0;
            final boolean onInt = (flags & PackageManager.INSTALL_INTERNAL) != 0;
            PackageInfoLite pkgLite = null;

            if (onInt && onSd) {
                // Check if both bits are set.
                Slog.w(TAG, "Conflicting flags specified for installing on both internal and external");
                ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
            } else {
                final long lowThreshold;

                final DeviceStorageMonitorService dsm = (DeviceStorageMonitorService) ServiceManager
                        .getService(DeviceStorageMonitorService.SERVICE);
                if (dsm == null) {
                    Log.w(TAG, "Couldn't get low memory threshold; no free limit imposed");
                    lowThreshold = 0L;
                } else {
                    lowThreshold = dsm.getMemoryLowThreshold();
                }

                try {
                    mContext.grantUriPermission(DEFAULT_CONTAINER_PACKAGE, mPackageURI,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    final File packageFile;
                    if (encryptionParams != null || !"file".equals(mPackageURI.getScheme())) {
                        ParcelFileDescriptor out = null;

                        mTempPackage = createTempPackageFile(mDrmAppPrivateInstallDir);
                        if (mTempPackage != null) {
                            try {
                                out = ParcelFileDescriptor.open(mTempPackage,
                                        ParcelFileDescriptor.MODE_READ_WRITE);
                            } catch (FileNotFoundException e) {
                                Slog.e(TAG, "Failed to create temporary file for : " + mPackageURI);
                            }

                            // Make a temporary file for decryption.
                            ret = mContainerService
                                    .copyResource(mPackageURI, encryptionParams, out);

                            packageFile = mTempPackage;

                            FileUtils.setPermissions(packageFile.getAbsolutePath(),
                                    FileUtils.S_IRUSR | FileUtils.S_IWUSR | FileUtils.S_IROTH,
                                    -1, -1);
                        } else {
                            packageFile = null;
                        }
                    } else {
                        packageFile = new File(mPackageURI.getPath());
                    }

                    if (packageFile != null) {
                        // Remote call to find out default install location
                        pkgLite = mContainerService.getMinimalPackageInfo(
                                packageFile.getAbsolutePath(), flags, lowThreshold);
                    }
                } finally {
                    mContext.revokeUriPermission(mPackageURI,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
                    loc = installLocationPolicy(pkgLite, flags);
                    if (!onSd && !onInt) {
                        // Override install location with flags
                        if (loc == PackageHelper.RECOMMEND_INSTALL_EXTERNAL) {
                            // Set the flag to install on external media.
                            flags |= PackageManager.INSTALL_EXTERNAL;
                            flags &= ~PackageManager.INSTALL_INTERNAL;
                        } else {
                            // Make sure the flag for installing on external
                            // media is unset
                            flags |= PackageManager.INSTALL_INTERNAL;
                            flags &= ~PackageManager.INSTALL_EXTERNAL;
                        }
                    }
                }
            }

            final InstallArgs args = createInstallArgs(this);
            mArgs = args;

            if (ret == PackageManager.INSTALL_SUCCEEDED) {
                /*
                 * Determine if we have any installed package verifiers. If we
                 * do, then we'll defer to them to verify the packages.
                 */
                final int requiredUid = mRequiredVerifierPackage == null ? -1
                        : getPackageUid(mRequiredVerifierPackage, 0);
                if (requiredUid != -1 && isVerificationEnabled()) {
                    final Intent verification = new Intent(
                            Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
                    verification.setDataAndType(getPackageUri(), PACKAGE_MIME_TYPE);
                    verification.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    final List<ResolveInfo> receivers = queryIntentReceivers(verification, null,
                            PackageManager.GET_DISABLED_COMPONENTS, 0 /* TODO: Which userId? */);

                    if (DEBUG_VERIFY) {
                        Slog.d(TAG, "Found " + receivers.size() + " verifiers for intent "
                                + verification.toString() + " with " + pkgLite.verifiers.length
                                + " optional verifiers");
                    }

                    final int verificationId = mPendingVerificationToken++;

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_ID, verificationId);

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_INSTALLER_PACKAGE,
                            installerPackageName);

                    verification.putExtra(PackageManager.EXTRA_VERIFICATION_INSTALL_FLAGS, flags);

                    if (verificationURI != null) {
                        verification.putExtra(PackageManager.EXTRA_VERIFICATION_URI,
                                verificationURI);
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

                                mContext.sendBroadcast(sufficientIntent);
                            }
                        }
                    }

                    final ComponentName requiredVerifierComponent = matchComponentForVerifier(
                            mRequiredVerifierPackage, receivers);
                    if (ret == PackageManager.INSTALL_SUCCEEDED
                            && mRequiredVerifierPackage != null) {
                        /*
                         * Send the intent to the required verification agent,
                         * but only start the verification timeout after the
                         * target BroadcastReceivers have run.
                         */
                        verification.setComponent(requiredVerifierComponent);
                        mContext.sendOrderedBroadcast(verification,
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

            if (mTempPackage != null) {
                if (!mTempPackage.delete()) {
                    Slog.w(TAG, "Couldn't delete temporary file: "
                            + mTempPackage.getAbsolutePath());
                }
            }
        }

        @Override
        void handleServiceError() {
            mArgs = createInstallArgs(this);
            mRet = PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }

        public boolean isForwardLocked() {
            return (flags & PackageManager.INSTALL_FORWARD_LOCK) != 0;
        }

        public Uri getPackageUri() {
            if (mTempPackage != null) {
                return Uri.fromFile(mTempPackage);
            } else {
                return mPackageURI;
            }
        }
    }

    /*
     * Utility class used in movePackage api.
     * srcArgs and targetArgs are not set for invalid flags and make
     * sure to do null checks when invoking methods on them.
     * We probably want to return ErrorPrams for both failed installs
     * and moves.
     */
    class MoveParams extends HandlerParams {
        final IPackageMoveObserver observer;
        final int flags;
        final String packageName;
        final InstallArgs srcArgs;
        final InstallArgs targetArgs;
        int uid;
        int mRet;

        MoveParams(InstallArgs srcArgs, IPackageMoveObserver observer, int flags,
                String packageName, String dataDir, int uid) {
            this.srcArgs = srcArgs;
            this.observer = observer;
            this.flags = flags;
            this.packageName = packageName;
            this.uid = uid;
            if (srcArgs != null) {
                Uri packageUri = Uri.fromFile(new File(srcArgs.getCodePath()));
                targetArgs = createInstallArgs(packageUri, flags, packageName, dataDir);
            } else {
                targetArgs = null;
            }
        }

        public void handleStartCopy() throws RemoteException {
            mRet = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
            // Check for storage space on target medium
            if (!targetArgs.checkFreeStorage(mContainerService)) {
                Log.w(TAG, "Insufficient storage to install");
                return;
            }

            mRet = srcArgs.doPreCopy();
            if (mRet != PackageManager.INSTALL_SUCCEEDED) {
                return;
            }

            mRet = targetArgs.copyApk(mContainerService, false);
            if (mRet != PackageManager.INSTALL_SUCCEEDED) {
                srcArgs.doPostCopy(uid);
                return;
            }

            mRet = srcArgs.doPostCopy(uid);
            if (mRet != PackageManager.INSTALL_SUCCEEDED) {
                return;
            }

            mRet = targetArgs.doPreInstall(mRet);
            if (mRet != PackageManager.INSTALL_SUCCEEDED) {
                return;
            }

            if (DEBUG_SD_INSTALL) {
                StringBuilder builder = new StringBuilder();
                if (srcArgs != null) {
                    builder.append("src: ");
                    builder.append(srcArgs.getCodePath());
                }
                if (targetArgs != null) {
                    builder.append(" target : ");
                    builder.append(targetArgs.getCodePath());
                }
                Log.i(TAG, builder.toString());
            }
        }

        @Override
        void handleReturnCode() {
            targetArgs.doPostInstall(mRet, uid);
            int currentStatus = PackageManager.MOVE_FAILED_INTERNAL_ERROR;
            if (mRet == PackageManager.INSTALL_SUCCEEDED) {
                currentStatus = PackageManager.MOVE_SUCCEEDED;
            } else if (mRet == PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE){
                currentStatus = PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE;
            }
            processPendingMove(this, currentStatus);
        }

        @Override
        void handleServiceError() {
            mRet = PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
        }
    }

    /**
     * Used during creation of InstallArgs
     *
     * @param flags package installation flags
     * @return true if should be installed on external storage
     */
    private static boolean installOnSd(int flags) {
        if ((flags & PackageManager.INSTALL_INTERNAL) != 0) {
            return false;
        }
        if ((flags & PackageManager.INSTALL_EXTERNAL) != 0) {
            return true;
        }
        return false;
    }

    /**
     * Used during creation of InstallArgs
     *
     * @param flags package installation flags
     * @return true if should be installed as forward locked
     */
    private static boolean installForwardLocked(int flags) {
        return (flags & PackageManager.INSTALL_FORWARD_LOCK) != 0;
    }

    private InstallArgs createInstallArgs(InstallParams params) {
        if (installOnSd(params.flags) || params.isForwardLocked()) {
            return new AsecInstallArgs(params);
        } else {
            return new FileInstallArgs(params);
        }
    }

    private InstallArgs createInstallArgs(int flags, String fullCodePath, String fullResourcePath,
            String nativeLibraryPath) {
        final boolean isInAsec;
        if (installOnSd(flags)) {
            /* Apps on SD card are always in ASEC containers. */
            isInAsec = true;
        } else if (installForwardLocked(flags)
                && !fullCodePath.startsWith(mDrmAppPrivateInstallDir.getAbsolutePath())) {
            /*
             * Forward-locked apps are only in ASEC containers if they're the
             * new style
             */
            isInAsec = true;
        } else {
            isInAsec = false;
        }

        if (isInAsec) {
            return new AsecInstallArgs(fullCodePath, fullResourcePath, nativeLibraryPath,
                    installOnSd(flags), installForwardLocked(flags));
        } else {
            return new FileInstallArgs(fullCodePath, fullResourcePath, nativeLibraryPath);
        }
    }

    // Used by package mover
    private InstallArgs createInstallArgs(Uri packageURI, int flags, String pkgName, String dataDir) {
        if (installOnSd(flags) || installForwardLocked(flags)) {
            String cid = getNextCodePath(packageURI.getPath(), pkgName, "/"
                    + AsecInstallArgs.RES_FILE_NAME);
            return new AsecInstallArgs(packageURI, cid, installOnSd(flags),
                    installForwardLocked(flags));
        } else {
            return new FileInstallArgs(packageURI, pkgName, dataDir);
        }
    }

    static abstract class InstallArgs {
        final IPackageInstallObserver observer;
        // Always refers to PackageManager flags only
        final int flags;
        final Uri packageURI;
        final String installerPackageName;
        final ManifestDigest manifestDigest;

        InstallArgs(Uri packageURI, IPackageInstallObserver observer, int flags,
                String installerPackageName, ManifestDigest manifestDigest) {
            this.packageURI = packageURI;
            this.flags = flags;
            this.observer = observer;
            this.installerPackageName = installerPackageName;
            this.manifestDigest = manifestDigest;
        }

        abstract void createCopyFile();
        abstract int copyApk(IMediaContainerService imcs, boolean temp) throws RemoteException;
        abstract int doPreInstall(int status);
        abstract boolean doRename(int status, String pkgName, String oldCodePath);

        abstract int doPostInstall(int status, int uid);
        abstract String getCodePath();
        abstract String getResourcePath();
        abstract String getNativeLibraryPath();
        // Need installer lock especially for dex file removal.
        abstract void cleanUpResourcesLI();
        abstract boolean doPostDeleteLI(boolean delete);
        abstract boolean checkFreeStorage(IMediaContainerService imcs) throws RemoteException;

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
         *
         * @return
         */
        int doPostCopy(int uid) {
            return PackageManager.INSTALL_SUCCEEDED;
        }

        protected boolean isFwdLocked() {
            return (flags & PackageManager.INSTALL_FORWARD_LOCK) != 0;
        }
    }

    class FileInstallArgs extends InstallArgs {
        File installDir;
        String codeFileName;
        String resourceFileName;
        String libraryPath;
        boolean created = false;

        FileInstallArgs(InstallParams params) {
            super(params.getPackageUri(), params.observer, params.flags,
                    params.installerPackageName, params.manifestDigest);
        }

        FileInstallArgs(String fullCodePath, String fullResourcePath, String nativeLibraryPath) {
            super(null, null, 0, null, null);
            File codeFile = new File(fullCodePath);
            installDir = codeFile.getParentFile();
            codeFileName = fullCodePath;
            resourceFileName = fullResourcePath;
            libraryPath = nativeLibraryPath;
        }

        FileInstallArgs(Uri packageURI, String pkgName, String dataDir) {
            super(packageURI, null, 0, null, null);
            installDir = isFwdLocked() ? mDrmAppPrivateInstallDir : mAppInstallDir;
            String apkName = getNextCodePath(null, pkgName, ".apk");
            codeFileName = new File(installDir, apkName + ".apk").getPath();
            resourceFileName = getResourcePathFromCodePath();
            libraryPath = new File(dataDir, LIB_DIR_NAME).getPath();
        }

        boolean checkFreeStorage(IMediaContainerService imcs) throws RemoteException {
            final long lowThreshold;

            final DeviceStorageMonitorService dsm = (DeviceStorageMonitorService) ServiceManager
                    .getService(DeviceStorageMonitorService.SERVICE);
            if (dsm == null) {
                Log.w(TAG, "Couldn't get low memory threshold; no free limit imposed");
                lowThreshold = 0L;
            } else {
                if (dsm.isMemoryLow()) {
                    Log.w(TAG, "Memory is reported as being too low; aborting package install");
                    return false;
                }

                lowThreshold = dsm.getMemoryLowThreshold();
            }

            try {
                mContext.grantUriPermission(DEFAULT_CONTAINER_PACKAGE, packageURI,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return imcs.checkInternalFreeStorage(packageURI, isFwdLocked(), lowThreshold);
            } finally {
                mContext.revokeUriPermission(packageURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        String getCodePath() {
            return codeFileName;
        }

        void createCopyFile() {
            installDir = isFwdLocked() ? mDrmAppPrivateInstallDir : mAppInstallDir;
            codeFileName = createTempPackageFile(installDir).getPath();
            resourceFileName = getResourcePathFromCodePath();
            created = true;
        }

        int copyApk(IMediaContainerService imcs, boolean temp) throws RemoteException {
            if (temp) {
                // Generate temp file name
                createCopyFile();
            }
            // Get a ParcelFileDescriptor to write to the output file
            File codeFile = new File(codeFileName);
            if (!created) {
                try {
                    codeFile.createNewFile();
                    // Set permissions
                    if (!setPermissions()) {
                        // Failed setting permissions.
                        return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                    }
                } catch (IOException e) {
                   Slog.w(TAG, "Failed to create file " + codeFile);
                   return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                }
            }
            ParcelFileDescriptor out = null;
            try {
                out = ParcelFileDescriptor.open(codeFile, ParcelFileDescriptor.MODE_READ_WRITE);
            } catch (FileNotFoundException e) {
                Slog.e(TAG, "Failed to create file descriptor for : " + codeFileName);
                return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
            }
            // Copy the resource now
            int ret = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
            try {
                mContext.grantUriPermission(DEFAULT_CONTAINER_PACKAGE, packageURI,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ret = imcs.copyResource(packageURI, null, out);
            } finally {
                IoUtils.closeQuietly(out);
                mContext.revokeUriPermission(packageURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (isFwdLocked()) {
                final File destResourceFile = new File(getResourcePath());

                // Copy the public files
                try {
                    PackageHelper.extractPublicFiles(codeFileName, destResourceFile);
                } catch (IOException e) {
                    Slog.e(TAG, "Couldn't create a new zip file for the public parts of a"
                            + " forward-locked app.");
                    destResourceFile.delete();
                    return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                }
            }
            return ret;
        }

        int doPreInstall(int status) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
            }
            return status;
        }

        boolean doRename(int status, final String pkgName, String oldCodePath) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
                return false;
            } else {
                final File oldCodeFile = new File(getCodePath());
                final File oldResourceFile = new File(getResourcePath());

                // Rename APK file based on packageName
                final String apkName = getNextCodePath(oldCodePath, pkgName, ".apk");
                final File newCodeFile = new File(installDir, apkName + ".apk");
                if (!oldCodeFile.renameTo(newCodeFile)) {
                    return false;
                }
                codeFileName = newCodeFile.getPath();

                // Rename public resource file if it's forward-locked.
                final File newResFile = new File(getResourcePathFromCodePath());
                if (isFwdLocked() && !oldResourceFile.renameTo(newResFile)) {
                    return false;
                }
                resourceFileName = getResourcePathFromCodePath();

                // Attempt to set permissions
                if (!setPermissions()) {
                    return false;
                }

                return true;
            }
        }

        int doPostInstall(int status, int uid) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                cleanUp();
            }
            return status;
        }

        String getResourcePath() {
            return resourceFileName;
        }

        private String getResourcePathFromCodePath() {
            final String codePath = getCodePath();
            if (isFwdLocked()) {
                final StringBuilder sb = new StringBuilder();

                sb.append(mAppInstallDir.getPath());
                sb.append('/');
                sb.append(getApkName(codePath));
                sb.append(".zip");

                /*
                 * If our APK is a temporary file, mark the resource as a
                 * temporary file as well so it can be cleaned up after
                 * catastrophic failure.
                 */
                if (codePath.endsWith(".tmp")) {
                    sb.append(".tmp");
                }

                return sb.toString();
            } else {
                return codePath;
            }
        }

        @Override
        String getNativeLibraryPath() {
            return libraryPath;
        }

        private boolean cleanUp() {
            boolean ret = true;
            String sourceDir = getCodePath();
            String publicSourceDir = getResourcePath();
            if (sourceDir != null) {
                File sourceFile = new File(sourceDir);
                if (!sourceFile.exists()) {
                    Slog.w(TAG, "Package source " + sourceDir + " does not exist.");
                    ret = false;
                }
                // Delete application's code and resources
                sourceFile.delete();
            }
            if (publicSourceDir != null && !publicSourceDir.equals(sourceDir)) {
                final File publicSourceFile = new File(publicSourceDir);
                if (!publicSourceFile.exists()) {
                    Slog.w(TAG, "Package public source " + publicSourceFile + " does not exist.");
                }
                if (publicSourceFile.exists()) {
                    publicSourceFile.delete();
                }
            }
            return ret;
        }

        void cleanUpResourcesLI() {
            String sourceDir = getCodePath();
            if (cleanUp()) {
                int retCode = mInstaller.rmdex(sourceDir);
                if (retCode < 0) {
                    Slog.w(TAG, "Couldn't remove dex file for package: "
                            +  " at location "
                            + sourceDir + ", retcode=" + retCode);
                    // we don't consider this to be a failure of the core package deletion
                }
            }
        }

        private boolean setPermissions() {
            // TODO Do this in a more elegant way later on. for now just a hack
            if (!isFwdLocked()) {
                final int filePermissions =
                    FileUtils.S_IRUSR|FileUtils.S_IWUSR|FileUtils.S_IRGRP
                    |FileUtils.S_IROTH;
                int retCode = FileUtils.setPermissions(getCodePath(), filePermissions, -1, -1);
                if (retCode != 0) {
                    Slog.e(TAG, "Couldn't set new package file permissions for " +
                            getCodePath()
                            + ". The return code was: " + retCode);
                    // TODO Define new internal error
                    return false;
                }
                return true;
            }
            return true;
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

    class AsecInstallArgs extends InstallArgs {
        static final String RES_FILE_NAME = "pkg.apk";
        static final String PUBLIC_RES_FILE_NAME = "res.zip";

        String cid;
        String packagePath;
        String resourcePath;
        String libraryPath;

        AsecInstallArgs(InstallParams params) {
            super(params.getPackageUri(), params.observer, params.flags,
                    params.installerPackageName, params.manifestDigest);
        }

        AsecInstallArgs(String fullCodePath, String fullResourcePath, String nativeLibraryPath,
                boolean isExternal, boolean isForwardLocked) {
            super(null, null, (isExternal ? PackageManager.INSTALL_EXTERNAL : 0)
                    | (isForwardLocked ? PackageManager.INSTALL_FORWARD_LOCK : 0), null, null);
            // Extract cid from fullCodePath
            int eidx = fullCodePath.lastIndexOf("/");
            String subStr1 = fullCodePath.substring(0, eidx);
            int sidx = subStr1.lastIndexOf("/");
            cid = subStr1.substring(sidx+1, eidx);
            setCachePath(subStr1);
        }

        AsecInstallArgs(String cid, boolean isForwardLocked) {
            super(null, null, (isAsecExternal(cid) ? PackageManager.INSTALL_EXTERNAL : 0)
                    | (isForwardLocked ? PackageManager.INSTALL_FORWARD_LOCK : 0), null, null);
            this.cid = cid;
            setCachePath(PackageHelper.getSdDir(cid));
        }

        AsecInstallArgs(Uri packageURI, String cid, boolean isExternal, boolean isForwardLocked) {
            super(packageURI, null, (isExternal ? PackageManager.INSTALL_EXTERNAL : 0)
                    | (isForwardLocked ? PackageManager.INSTALL_FORWARD_LOCK : 0), null, null);
            this.cid = cid;
        }

        void createCopyFile() {
            cid = getTempContainerId();
        }

        boolean checkFreeStorage(IMediaContainerService imcs) throws RemoteException {
            try {
                mContext.grantUriPermission(DEFAULT_CONTAINER_PACKAGE, packageURI,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return imcs.checkExternalFreeStorage(packageURI, isFwdLocked());
            } finally {
                mContext.revokeUriPermission(packageURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        private final boolean isExternal() {
            return (flags & PackageManager.INSTALL_EXTERNAL) != 0;
        }

        int copyApk(IMediaContainerService imcs, boolean temp) throws RemoteException {
            if (temp) {
                createCopyFile();
            } else {
                /*
                 * Pre-emptively destroy the container since it's destroyed if
                 * copying fails due to it existing anyway.
                 */
                PackageHelper.destroySdDir(cid);
            }

            final String newCachePath;
            try {
                mContext.grantUriPermission(DEFAULT_CONTAINER_PACKAGE, packageURI,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                newCachePath = imcs.copyResourceToContainer(packageURI, cid, getEncryptKey(),
                        RES_FILE_NAME, PUBLIC_RES_FILE_NAME, isExternal(), isFwdLocked());
            } finally {
                mContext.revokeUriPermission(packageURI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (newCachePath != null) {
                setCachePath(newCachePath);
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

        @Override
        String getNativeLibraryPath() {
            return libraryPath;
        }

        int doPreInstall(int status) {
            if (status != PackageManager.INSTALL_SUCCEEDED) {
                // Destroy container
                PackageHelper.destroySdDir(cid);
            } else {
                boolean mounted = PackageHelper.isContainerMounted(cid);
                if (!mounted) {
                    String newCachePath = PackageHelper.mountSdDir(cid, getEncryptKey(),
                            Process.SYSTEM_UID);
                    if (newCachePath != null) {
                        setCachePath(newCachePath);
                    } else {
                        return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                    }
                }
            }
            return status;
        }

        boolean doRename(int status, final String pkgName,
                String oldCodePath) {
            String newCacheId = getNextCodePath(oldCodePath, pkgName, "/" + RES_FILE_NAME);
            String newCachePath = null;
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
                newCachePath = PackageHelper.mountSdDir(newCacheId,
                        getEncryptKey(), Process.SYSTEM_UID);
            } else {
                newCachePath = PackageHelper.getSdDir(newCacheId);
            }
            if (newCachePath == null) {
                Slog.w(TAG, "Failed to get cache path for  " + newCacheId);
                return false;
            }
            Log.i(TAG, "Succesfully renamed " + cid +
                    " to " + newCacheId +
                    " at new path: " + newCachePath);
            cid = newCacheId;
            setCachePath(newCachePath);
            return true;
        }

        private void setCachePath(String newCachePath) {
            File cachePath = new File(newCachePath);
            libraryPath = new File(cachePath, LIB_DIR_NAME).getPath();
            packagePath = new File(cachePath, RES_FILE_NAME).getPath();

            if (isFwdLocked()) {
                resourcePath = new File(cachePath, PUBLIC_RES_FILE_NAME).getPath();
            } else {
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
                    groupOwner = uid;
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

        void cleanUpResourcesLI() {
            String sourceFile = getCodePath();
            // Remove dex file
            int retCode = mInstaller.rmdex(sourceFile);
            if (retCode < 0) {
                Slog.w(TAG, "Couldn't remove dex file for package: "
                        + " at location "
                        + sourceFile.toString() + ", retcode=" + retCode);
                // we don't consider this to be a failure of the core package deletion
            }
            cleanUp();
        }

        boolean matchContainer(String app) {
            if (cid.startsWith(app)) {
                return true;
            }
            return false;
        }

        String getPackageName() {
            return getAsecPackageName(cid);
        }

        boolean doPostDeleteLI(boolean delete) {
            boolean ret = false;
            boolean mounted = PackageHelper.isContainerMounted(cid);
            if (mounted) {
                // Unmount first
                ret = PackageHelper.unMountSdDir(cid);
            }
            if (ret && delete) {
                cleanUpResourcesLI();
            }
            return ret;
        }

        @Override
        int doPreCopy() {
            if (isFwdLocked()) {
                if (!PackageHelper.fixSdPermissions(cid,
                        getPackageUid(DEFAULT_CONTAINER_PACKAGE, 0), RES_FILE_NAME)) {
                    return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                }
            }

            return PackageManager.INSTALL_SUCCEEDED;
        }

        @Override
        int doPostCopy(int uid) {
            if (isFwdLocked()) {
                if (uid < Process.FIRST_APPLICATION_UID
                        || !PackageHelper.fixSdPermissions(cid, uid, RES_FILE_NAME)) {
                    Slog.e(TAG, "Failed to finalize " + cid);
                    PackageHelper.destroySdDir(cid);
                    return PackageManager.INSTALL_FAILED_CONTAINER_ERROR;
                }
            }

            return PackageManager.INSTALL_SUCCEEDED;
        }
    };

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
            if (subStr.endsWith(suffix)) {
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

    // Utility method used to ignore ADD/REMOVE events
    // by directory observer.
    private static boolean ignoreCodePath(String fullPathStr) {
        String apkName = getApkName(fullPathStr);
        int idx = apkName.lastIndexOf(INSTALL_PACKAGE_SUFFIX);
        if (idx != -1 && ((idx+1) < apkName.length())) {
            // Make sure the package ends with a numeral
            String version = apkName.substring(idx+1);
            try {
                Integer.parseInt(version);
                return true;
            } catch (NumberFormatException e) {}
        }
        return false;
    }
    
    // Utility method that returns the relative package path with respect
    // to the installation directory. Like say for /data/data/com.test-1.apk
    // string com.test-1 is returned.
    static String getApkName(String codePath) {
        if (codePath == null) {
            return null;
        }
        int sidx = codePath.lastIndexOf("/");
        int eidx = codePath.lastIndexOf(".");
        if (eidx == -1) {
            eidx = codePath.length();
        } else if (eidx == 0) {
            Slog.w(TAG, " Invalid code path, "+ codePath + " Not a valid apk name");
            return null;
        }
        return codePath.substring(sidx+1, eidx);
    }

    class PackageInstalledInfo {
        String name;
        int uid;
        PackageParser.Package pkg;
        int returnCode;
        PackageRemovedInfo removedInfo;
    }

    /*
     * Install a non-existing package.
     */
    private void installNewPackageLI(PackageParser.Package pkg,
            int parseFlags,
            int scanMode,
            String installerPackageName, PackageInstalledInfo res) {
        // Remember this for later, in case we need to rollback this install
        String pkgName = pkg.packageName;

        boolean dataDirExists = getDataPathForPackage(pkg.packageName, 0).exists();
        res.name = pkgName;
        synchronized(mPackages) {
            if (mSettings.mRenamedPackages.containsKey(pkgName)) {
                // A package with the same name is already installed, though
                // it has been renamed to an older name.  The package we
                // are trying to install should be installed as an update to
                // the existing one, but that has not been requested, so bail.
                Slog.w(TAG, "Attempt to re-install " + pkgName
                        + " without first uninstalling package running as "
                        + mSettings.mRenamedPackages.get(pkgName));
                res.returnCode = PackageManager.INSTALL_FAILED_ALREADY_EXISTS;
                return;
            }
            if (mPackages.containsKey(pkgName) || mAppDirs.containsKey(pkg.mPath)) {
                // Don't allow installation over an existing package with the same name.
                Slog.w(TAG, "Attempt to re-install " + pkgName
                        + " without first uninstalling.");
                res.returnCode = PackageManager.INSTALL_FAILED_ALREADY_EXISTS;
                return;
            }
        }
        mLastScanError = PackageManager.INSTALL_SUCCEEDED;
        PackageParser.Package newPackage = scanPackageLI(pkg, parseFlags, scanMode,
                System.currentTimeMillis());
        if (newPackage == null) {
            Slog.w(TAG, "Package couldn't be installed in " + pkg.mPath);
            if ((res.returnCode=mLastScanError) == PackageManager.INSTALL_SUCCEEDED) {
                res.returnCode = PackageManager.INSTALL_FAILED_INVALID_APK;
            }
        } else {
            updateSettingsLI(newPackage,
                    installerPackageName,
                    res);
            // delete the partially installed application. the data directory will have to be
            // restored if it was already existing
            if (res.returnCode != PackageManager.INSTALL_SUCCEEDED) {
                // remove package from internal structures.  Note that we want deletePackageX to
                // delete the package data and cache directories that it created in
                // scanPackageLocked, unless those directories existed before we even tried to
                // install.
                deletePackageLI(
                        pkgName, false,
                        dataDirExists ? PackageManager.DONT_DELETE_DATA : 0,
                                res.removedInfo, true);
            }
        }
    }

    private void replacePackageLI(PackageParser.Package pkg,
            int parseFlags,
            int scanMode,
            String installerPackageName, PackageInstalledInfo res) {

        PackageParser.Package oldPackage;
        String pkgName = pkg.packageName;
        // First find the old package info and check signatures
        synchronized(mPackages) {
            oldPackage = mPackages.get(pkgName);
            if (compareSignatures(oldPackage.mSignatures, pkg.mSignatures)
                    != PackageManager.SIGNATURE_MATCH) {
                Slog.w(TAG, "New package has a different signature: " + pkgName);
                res.returnCode = PackageManager.INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES;
                return;
            }
        }
        boolean sysPkg = (isSystemApp(oldPackage));
        if (sysPkg) {
            replaceSystemPackageLI(oldPackage, pkg, parseFlags, scanMode, installerPackageName, res);
        } else {
            replaceNonSystemPackageLI(oldPackage, pkg, parseFlags, scanMode, installerPackageName, res);
        }
    }

    private void replaceNonSystemPackageLI(PackageParser.Package deletedPackage,
            PackageParser.Package pkg,
            int parseFlags, int scanMode,
            String installerPackageName, PackageInstalledInfo res) {
        PackageParser.Package newPackage = null;
        String pkgName = deletedPackage.packageName;
        boolean deletedPkg = true;
        boolean updatedSettings = false;

        long origUpdateTime;
        if (pkg.mExtras != null) {
            origUpdateTime = ((PackageSetting)pkg.mExtras).lastUpdateTime;
        } else {
            origUpdateTime = 0;
        }

        // First delete the existing package while retaining the data directory
        if (!deletePackageLI(pkgName, true, PackageManager.DONT_DELETE_DATA,
                res.removedInfo, true)) {
            // If the existing package wasn't successfully deleted
            res.returnCode = PackageManager.INSTALL_FAILED_REPLACE_COULDNT_DELETE;
            deletedPkg = false;
        } else {
            // Successfully deleted the old package. Now proceed with re-installation
            mLastScanError = PackageManager.INSTALL_SUCCEEDED;
            newPackage = scanPackageLI(pkg, parseFlags, scanMode | SCAN_UPDATE_TIME,
                    System.currentTimeMillis());
            if (newPackage == null) {
                Slog.w(TAG, "Package couldn't be installed in " + pkg.mPath);
                if ((res.returnCode=mLastScanError) == PackageManager.INSTALL_SUCCEEDED) {
                    res.returnCode = PackageManager.INSTALL_FAILED_INVALID_APK;
                }
            } else {
                updateSettingsLI(newPackage,
                        installerPackageName,
                        res);
                updatedSettings = true;
            }
        }

        if (res.returnCode != PackageManager.INSTALL_SUCCEEDED) {
            // remove package from internal structures.  Note that we want deletePackageX to
            // delete the package data and cache directories that it created in
            // scanPackageLocked, unless those directories existed before we even tried to
            // install.
            if(updatedSettings) {
                deletePackageLI(
                        pkgName, true,
                        PackageManager.DONT_DELETE_DATA,
                                res.removedInfo, true);
            }
            // Since we failed to install the new package we need to restore the old
            // package that we deleted.
            if(deletedPkg) {
                File restoreFile = new File(deletedPackage.mPath);
                // Parse old package
                boolean oldOnSd = isExternal(deletedPackage);
                int oldParseFlags  = mDefParseFlags | PackageParser.PARSE_CHATTY |
                        (isForwardLocked(deletedPackage) ? PackageParser.PARSE_FORWARD_LOCK : 0) |
                        (oldOnSd ? PackageParser.PARSE_ON_SDCARD : 0);
                int oldScanMode = (oldOnSd ? 0 : SCAN_MONITOR) | SCAN_UPDATE_SIGNATURE
                        | SCAN_UPDATE_TIME;
                if (scanPackageLI(restoreFile, oldParseFlags, oldScanMode,
                        origUpdateTime) == null) {
                    Slog.e(TAG, "Failed to restore package : " + pkgName + " after failed upgrade");
                    return;
                }
                // Restore of old package succeeded. Update permissions.
                // writer
                synchronized (mPackages) {
                    updatePermissionsLPw(deletedPackage.packageName, deletedPackage,
                            UPDATE_PERMISSIONS_ALL);
                    // can downgrade to reader
                    mSettings.writeLPr();
                }
                Slog.i(TAG, "Successfully restored package : " + pkgName + " after failed upgrade");
            }
        }
    }

    private void replaceSystemPackageLI(PackageParser.Package deletedPackage,
            PackageParser.Package pkg,
            int parseFlags, int scanMode,
            String installerPackageName, PackageInstalledInfo res) {
        PackageParser.Package newPackage = null;
        boolean updatedSettings = false;
        parseFlags |= PackageManager.INSTALL_REPLACE_EXISTING |
                PackageParser.PARSE_IS_SYSTEM;
        String packageName = deletedPackage.packageName;
        res.returnCode = PackageManager.INSTALL_FAILED_REPLACE_COULDNT_DELETE;
        if (packageName == null) {
            Slog.w(TAG, "Attempt to delete null packageName.");
            return;
        }
        PackageParser.Package oldPkg;
        PackageSetting oldPkgSetting;
        // reader
        synchronized (mPackages) {
            oldPkg = mPackages.get(packageName);
            oldPkgSetting = mSettings.mPackages.get(packageName);
            if((oldPkg == null) || (oldPkg.applicationInfo == null) ||
                    (oldPkgSetting == null)) {
                Slog.w(TAG, "Couldn't find package:"+packageName+" information");
                return;
            }
        }

        killApplication(packageName, oldPkg.applicationInfo.uid);

        res.removedInfo.uid = oldPkg.applicationInfo.uid;
        res.removedInfo.removedPackage = packageName;
        // Remove existing system package
        removePackageLI(oldPkg, true);
        // writer
        synchronized (mPackages) {
            if (!mSettings.disableSystemPackageLPw(packageName) && deletedPackage != null) {
                // We didn't need to disable the .apk as a current system package,
                // which means we are replacing another update that is already
                // installed.  We need to make sure to delete the older one's .apk.
                res.removedInfo.args = createInstallArgs(0,
                        deletedPackage.applicationInfo.sourceDir,
                        deletedPackage.applicationInfo.publicSourceDir,
                        deletedPackage.applicationInfo.nativeLibraryDir);
            } else {
                res.removedInfo.args = null;
            }
        }
        
        // Successfully disabled the old package. Now proceed with re-installation
        mLastScanError = PackageManager.INSTALL_SUCCEEDED;
        pkg.applicationInfo.flags |= ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        newPackage = scanPackageLI(pkg, parseFlags, scanMode, 0);
        if (newPackage == null) {
            Slog.w(TAG, "Package couldn't be installed in " + pkg.mPath);
            if ((res.returnCode=mLastScanError) == PackageManager.INSTALL_SUCCEEDED) {
                res.returnCode = PackageManager.INSTALL_FAILED_INVALID_APK;
            }
        } else {
            if (newPackage.mExtras != null) {
                final PackageSetting newPkgSetting = (PackageSetting)newPackage.mExtras;
                newPkgSetting.firstInstallTime = oldPkgSetting.firstInstallTime;
                newPkgSetting.lastUpdateTime = System.currentTimeMillis();
            }
            updateSettingsLI(newPackage, installerPackageName, res);
            updatedSettings = true;
        }

        if (res.returnCode != PackageManager.INSTALL_SUCCEEDED) {
            // Re installation failed. Restore old information
            // Remove new pkg information
            if (newPackage != null) {
                removePackageLI(newPackage, true);
            }
            // Add back the old system package
            scanPackageLI(oldPkg, parseFlags, SCAN_MONITOR | SCAN_UPDATE_SIGNATURE, 0);
            // Restore the old system information in Settings
            synchronized(mPackages) {
                if (updatedSettings) {
                    mSettings.enableSystemPackageLPw(packageName);
                    mSettings.setInstallerPackageName(packageName,
                            oldPkgSetting.installerPackageName);
                }
                mSettings.writeLPr();
            }
        }
    }

    // Utility method used to move dex files during install.
    private int moveDexFilesLI(PackageParser.Package newPackage) {
        int retCode;
        if ((newPackage.applicationInfo.flags&ApplicationInfo.FLAG_HAS_CODE) != 0) {
            retCode = mInstaller.movedex(newPackage.mScanPath, newPackage.mPath);
            if (retCode != 0) {
                if (mNoDexOpt) {
                    /*
                     * If we're in an engineering build, programs are lazily run
                     * through dexopt. If the .dex file doesn't exist yet, it
                     * will be created when the program is run next.
                     */
                    Slog.i(TAG, "dex file doesn't exist, skipping move: " + newPackage.mPath);
                } else {
                    Slog.e(TAG, "Couldn't rename dex file: " + newPackage.mPath);
                    return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                }
            }
        }
        return PackageManager.INSTALL_SUCCEEDED;
    }

    private void updateSettingsLI(PackageParser.Package newPackage,
            String installerPackageName, PackageInstalledInfo res) {
        String pkgName = newPackage.packageName;
        synchronized (mPackages) {
            //write settings. the installStatus will be incomplete at this stage.
            //note that the new package setting would have already been
            //added to mPackages. It hasn't been persisted yet.
            mSettings.setInstallStatus(pkgName, PackageSettingBase.PKG_INSTALL_INCOMPLETE);
            mSettings.writeLPr();
        }

        if ((res.returnCode = moveDexFilesLI(newPackage))
                != PackageManager.INSTALL_SUCCEEDED) {
            // Discontinue if moving dex files failed.
            return;
        }

        Log.d(TAG, "New package installed in " + newPackage.mPath);

        cleanAssetRedirections(newPackage);

        if (newPackage.mIsThemeApk) {
            /* DBS-TODO
            boolean isThemePackageDrmProtected = false;
            int N = newPackage.mThemeInfos.size();
            for (int i = 0; i < N; i++) {
                if (newPackage.mThemeInfos.get(i).isDrmProtected) {
                    isThemePackageDrmProtected = true;
                    break;
                }
            }
            if (isThemePackageDrmProtected) {
                splitThemePackage(newPackage.mPath);
            }
            */
        }

        synchronized (mPackages) {
            updatePermissionsLPw(newPackage.packageName, newPackage,
                    UPDATE_PERMISSIONS_REPLACE_PKG | (newPackage.permissions.size() > 0
                            ? UPDATE_PERMISSIONS_ALL : 0));
            res.name = pkgName;
            res.uid = newPackage.applicationInfo.uid;
            res.pkg = newPackage;
            mSettings.setInstallStatus(pkgName, PackageSettingBase.PKG_INSTALL_COMPLETE);
            mSettings.setInstallerPackageName(pkgName, installerPackageName);
            res.returnCode = PackageManager.INSTALL_SUCCEEDED;
            //to update install status
            mSettings.writeLPr();
        }
    }

    private void deleteLockedZipFileIfExists(String originalPackagePath) {
        String lockedZipFilePath = PackageParser.getLockedZipFilePath(originalPackagePath);
        File zipFile = new File(lockedZipFilePath);
        if (zipFile.exists() && zipFile.isFile()) {
            if (!zipFile.delete()) {
                Log.w(TAG, "Couldn't delete locked zip file: " + originalPackagePath);
            }
        }
    }
    private void splitThemePackage(File originalFile) {
        final String originalPackagePath = originalFile.getPath();
        final String lockedZipFilePath = PackageParser.getLockedZipFilePath(originalPackagePath);

        try {
            final List<String> drmProtectedEntries = new ArrayList<String>();
            final ZipFile privateZip = new ZipFile(originalFile.getPath());

            final Enumeration<? extends ZipEntry> privateZipEntries = privateZip.entries();
            while (privateZipEntries.hasMoreElements()) {
                final ZipEntry zipEntry = privateZipEntries.nextElement();
                final String zipEntryName = zipEntry.getName();
                if (zipEntryName.startsWith("assets/") && zipEntryName.contains("/locked/")) {
                    drmProtectedEntries.add(zipEntryName);
                }
            }
            privateZip.close();

            String [] args = new String[0];
            args = drmProtectedEntries.toArray(args);
            int code = mContext.getAssets().splitDrmProtectedThemePackage(
                    originalPackagePath,
                    lockedZipFilePath,
                    args);
            if (code != 0) {
                Log.e("PackageManagerService",
                        "splitDrmProtectedThemePackage returned = " + code);
            }
            code = FileUtils.setPermissions(
                    lockedZipFilePath,
                    0640,
                    -1,
                    THEME_MAMANER_GUID);
            if (code != 0) {
                Log.e("PackageManagerService",
                        "Set permissions for " + lockedZipFilePath + " returned = " + code);
            }
            code = FileUtils.setPermissions(
                    originalPackagePath,
                    0644,
                    -1, -1);
            if (code != 0) {
                Log.e("PackageManagerService",
                        "Set permissions for " + originalPackagePath + " returned = " + code);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failure to generate new zip files for theme");
        }
    }

    private void installPackageLI(InstallArgs args,
            boolean newInstall, PackageInstalledInfo res) {
        int pFlags = args.flags;
        String installerPackageName = args.installerPackageName;
        File tmpPackageFile = new File(args.getCodePath());
        boolean forwardLocked = ((pFlags & PackageManager.INSTALL_FORWARD_LOCK) != 0);
        boolean onSd = ((pFlags & PackageManager.INSTALL_EXTERNAL) != 0);
        boolean replace = false;
        int scanMode = (onSd ? 0 : SCAN_MONITOR) | SCAN_FORCE_DEX | SCAN_UPDATE_SIGNATURE
                | (newInstall ? SCAN_NEW_INSTALL : 0);
        // Result object to be returned
        res.returnCode = PackageManager.INSTALL_SUCCEEDED;

        // Retrieve PackageSettings and parse package
        int parseFlags = mDefParseFlags | PackageParser.PARSE_CHATTY
                | (forwardLocked ? PackageParser.PARSE_FORWARD_LOCK : 0)
                | (onSd ? PackageParser.PARSE_ON_SDCARD : 0);
        PackageParser pp = new PackageParser(tmpPackageFile.getPath());
        pp.setSeparateProcesses(mSeparateProcesses);
        final PackageParser.Package pkg = pp.parsePackage(tmpPackageFile,
                null, mMetrics, parseFlags);
        if (pkg == null) {
            res.returnCode = pp.getParseError();
            return;
        }
        String pkgName = res.name = pkg.packageName;
        if ((pkg.applicationInfo.flags&ApplicationInfo.FLAG_TEST_ONLY) != 0) {
            if ((pFlags&PackageManager.INSTALL_ALLOW_TEST) == 0) {
                res.returnCode = PackageManager.INSTALL_FAILED_TEST_ONLY;
                return;
            }
        }
        if (GET_CERTIFICATES && !pp.collectCertificates(pkg, parseFlags)) {
            res.returnCode = pp.getParseError();
            return;
        }

        /* If the installer passed in a manifest digest, compare it now. */
        if (args.manifestDigest != null) {
            if (DEBUG_INSTALL) {
                final String parsedManifest = pkg.manifestDigest == null ? "null"
                        : pkg.manifestDigest.toString();
                Slog.d(TAG, "Comparing manifests: " + args.manifestDigest.toString() + " vs. "
                        + parsedManifest);
            }

            if (!args.manifestDigest.equals(pkg.manifestDigest)) {
                res.returnCode = PackageManager.INSTALL_FAILED_PACKAGE_CHANGED;
                return;
            }
        } else if (DEBUG_INSTALL) {
            final String parsedManifest = pkg.manifestDigest == null
                    ? "null" : pkg.manifestDigest.toString();
            Slog.d(TAG, "manifestDigest was not present, but parser got: " + parsedManifest);
        }

        // Get rid of all references to package scan path via parser.
        pp = null;
        String oldCodePath = null;
        boolean systemApp = false;
        synchronized (mPackages) {
            // Check if installing already existing package
            if ((pFlags&PackageManager.INSTALL_REPLACE_EXISTING) != 0) {
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
                } else if (mPackages.containsKey(pkgName)) {
                    // This package, under its official name, already exists
                    // on the device; we should replace it.
                    replace = true;
                }
            }
            PackageSetting ps = mSettings.mPackages.get(pkgName);
            if (ps != null) {
                oldCodePath = mSettings.mPackages.get(pkgName).codePathString;
                if (ps.pkg != null && ps.pkg.applicationInfo != null) {
                    systemApp = (ps.pkg.applicationInfo.flags &
                            ApplicationInfo.FLAG_SYSTEM) != 0;
                }
            }
        }

        if (systemApp && onSd) {
            // Disable updates to system apps on sdcard
            Slog.w(TAG, "Cannot install updates to system apps on sdcard");
            res.returnCode = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
            return;
        }

        if (!args.doRename(res.returnCode, pkgName, oldCodePath)) {
            res.returnCode = PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
            return;
        }
        // Set application objects path explicitly after the rename
        setApplicationInfoPaths(pkg, args.getCodePath(), args.getResourcePath());
        pkg.applicationInfo.nativeLibraryDir = args.getNativeLibraryPath();
        if (replace) {
            replacePackageLI(pkg, parseFlags, scanMode,
                    installerPackageName, res);
        } else {
            installNewPackageLI(pkg, parseFlags, scanMode,
                    installerPackageName,res);
        }
    }

    private static boolean isForwardLocked(PackageParser.Package pkg) {
        return (pkg.applicationInfo.flags & ApplicationInfo.FLAG_FORWARD_LOCK) != 0;
    }


    private boolean isForwardLocked(PackageSetting ps) {
        return (ps.pkgFlags & ApplicationInfo.FLAG_FORWARD_LOCK) != 0;
    }

    private static boolean isExternal(PackageParser.Package pkg) {
        return (pkg.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    private static boolean isExternal(PackageSetting ps) {
        return (ps.pkgFlags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    private static boolean isSystemApp(PackageParser.Package pkg) {
        return (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isSystemApp(ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isSystemApp(PackageSetting ps) {
        return (ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isUpdatedSystemApp(PackageParser.Package pkg) {
        return (pkg.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private int packageFlagsToInstallFlags(PackageSetting ps) {
        int installFlags = 0;
        if (isExternal(ps)) {
            installFlags |= PackageManager.INSTALL_EXTERNAL;
        }
        if (isForwardLocked(ps)) {
            installFlags |= PackageManager.INSTALL_FORWARD_LOCK;
        }
        return installFlags;
    }

    private void deleteTempPackageFiles() {
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("vmdl") && name.endsWith(".tmp");
            }
        };
        String tmpFilesList[] = mAppInstallDir.list(filter);
        if(tmpFilesList == null) {
            return;
        }
        for(int i = 0; i < tmpFilesList.length; i++) {
            File tmpFile = new File(mAppInstallDir, tmpFilesList[i]);
            tmpFile.delete();
        }
    }

    private File createTempPackageFile(File installDir) {
        File tmpPackageFile;
        try {
            tmpPackageFile = File.createTempFile("vmdl", ".tmp", installDir);
        } catch (IOException e) {
            Slog.e(TAG, "Couldn't create temp file for downloaded package file.");
            return null;
        }
        try {
            FileUtils.setPermissions(
                    tmpPackageFile.getCanonicalPath(), FileUtils.S_IRUSR|FileUtils.S_IWUSR,
                    -1, -1);
        } catch (IOException e) {
            Slog.e(TAG, "Trouble getting the canoncical path for a temp file.");
            return null;
        }
        return tmpPackageFile;
    }

    public void deletePackage(final String packageName,
                              final IPackageDeleteObserver observer,
                              final int flags) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DELETE_PACKAGES, null);
        // Queue up an async operation since the package deletion may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                final int returnCode = deletePackageX(packageName, true, true, flags);
                if (observer != null) {
                    try {
                        observer.packageDeleted(packageName, returnCode);
                    } catch (RemoteException e) {
                        Log.i(TAG, "Observer no longer exists.");
                    } //end catch
                } //end if
            } //end run
        });
    }

    /**
     *  This method is an internal method that could be get invoked either
     *  to delete an installed package or to clean up a failed installation.
     *  After deleting an installed package, a broadcast is sent to notify any
     *  listeners that the package has been installed. For cleaning up a failed
     *  installation, the broadcast is not necessary since the package's
     *  installation wouldn't have sent the initial broadcast either
     *  The key steps in deleting a package are
     *  deleting the package information in internal structures like mPackages,
     *  deleting the packages base directories through installd
     *  updating mSettings to reflect current status
     *  persisting settings for later use
     *  sending a broadcast if necessary
     */
    private int deletePackageX(String packageName, boolean sendBroadCast,
                                   boolean deleteCodeAndResources, int flags) {
        final PackageRemovedInfo info = new PackageRemovedInfo();
        final boolean res;

        IDevicePolicyManager dpm = IDevicePolicyManager.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_POLICY_SERVICE));
        try {
            if (dpm != null && dpm.packageHasActiveAdmins(packageName)) {
                Slog.w(TAG, "Not removing package " + packageName + ": has active device admin");
                return PackageManager.DELETE_FAILED_DEVICE_POLICY_MANAGER;
            }
        } catch (RemoteException e) {
        }
        
        synchronized (mPackages) {
            PackageParser.Package p = mPackages.get(packageName);
            if (p != null) {
                info.isThemeApk = p.mIsThemeApk;
                if (info.isThemeApk && deleteCodeAndResources &&
                    !info.isRemovedPackageSystemUpdate && sendBroadCast) {
                    deleteLockedZipFileIfExists(p.mPath);
                }
            }
        }
 
        synchronized (mInstallLock) {
            res = deletePackageLI(packageName, deleteCodeAndResources,
                    flags | REMOVE_CHATTY, info, true);
        }

        if (res && sendBroadCast) {
            boolean systemUpdate = info.isRemovedPackageSystemUpdate;
            info.sendBroadcast(deleteCodeAndResources, systemUpdate, true);

            // If the removed package was a system update, the old system packaged
            // was re-enabled; we need to broadcast this information
            if (systemUpdate) {
                Bundle extras = new Bundle(1);
                extras.putInt(Intent.EXTRA_UID, info.removedUid >= 0 ? info.removedUid : info.uid);
                extras.putBoolean(Intent.EXTRA_REPLACING, true);

                String category = null;
                if (info.isThemeApk) {
                    category = Intent.CATEGORY_THEME_PACKAGE_INSTALLED_STATE_CHANGE;
                }

                sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, packageName, category,
                        extras, null, null, UserId.USER_ALL);
                sendPackageBroadcast(Intent.ACTION_PACKAGE_REPLACED, packageName, category,
                        extras, null, null, UserId.USER_ALL);
                sendPackageBroadcast(Intent.ACTION_MY_PACKAGE_REPLACED, null, null,
                        null, packageName, null, UserId.USER_ALL);
            }
        }
        // Force a gc here.
        Runtime.getRuntime().gc();
        // Delete the resources here after sending the broadcast to let
        // other processes clean up before deleting resources.
        if (info.args != null) {
            synchronized (mInstallLock) {
                info.args.doPostDeleteLI(deleteCodeAndResources);
            }
        }

        return res ? PackageManager.DELETE_SUCCEEDED : PackageManager.DELETE_FAILED_INTERNAL_ERROR;
    }

    static class PackageRemovedInfo {
        String removedPackage;
        int uid = -1;
        int removedUid = -1;
        boolean isRemovedPackageSystemUpdate = false;
        // Clean up resources deleted packages.
        InstallArgs args = null;
        boolean isThemeApk = false;

        void sendBroadcast(boolean fullRemove, boolean replacing,
                 boolean deleteLockedZipFileIfExists) {
            Bundle extras = new Bundle(1);
            extras.putInt(Intent.EXTRA_UID, removedUid >= 0 ? removedUid : uid);
            extras.putBoolean(Intent.EXTRA_DATA_REMOVED, fullRemove);
            if (replacing) {
                extras.putBoolean(Intent.EXTRA_REPLACING, true);
            }
            if (removedPackage != null) {
                String category = null;
                if (isThemeApk) {
                    category = Intent.CATEGORY_THEME_PACKAGE_INSTALLED_STATE_CHANGE;
                }
                sendPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, removedPackage, category,
                        extras, null, null, UserId.USER_ALL);
                if (fullRemove && !replacing) {
                    sendPackageBroadcast(Intent.ACTION_PACKAGE_FULLY_REMOVED, removedPackage, category,
                            extras, null, null, UserId.USER_ALL);
                }
            }
            if (removedUid >= 0) {
                sendPackageBroadcast(Intent.ACTION_UID_REMOVED, null, null, extras, null, null,
                        UserId.getUserId(removedUid));
            }
        }
    }

    /*
     * This method deletes the package from internal data structures. If the DONT_DELETE_DATA
     * flag is not set, the data directory is removed as well.
     * make sure this flag is set for partially installed apps. If not its meaningless to
     * delete a partially installed application.
     */
    private void removePackageDataLI(PackageParser.Package p, PackageRemovedInfo outInfo,
            int flags, boolean writeSettings) {
        String packageName = p.packageName;
        if (outInfo != null) {
            outInfo.removedPackage = packageName;
        }
        removePackageLI(p, (flags&REMOVE_CHATTY) != 0);
        // Retrieve object to delete permissions for shared user later on
        final PackageSetting deletedPs;
        // reader
        synchronized (mPackages) {
            deletedPs = mSettings.mPackages.get(packageName);
        }
        if ((flags&PackageManager.DONT_DELETE_DATA) == 0) {
            int retCode = mInstaller.remove(packageName, 0);
            if (retCode < 0) {
                Slog.w(TAG, "Couldn't remove app data or cache directory for package: "
                           + packageName + ", retcode=" + retCode);
                // we don't consider this to be a failure of the core package deletion
            } else {
                // TODO: Kill the processes first
                sUserManager.removePackageForAllUsers(packageName);
            }
            schedulePackageCleaning(packageName);
        }
        // writer
        synchronized (mPackages) {
            if (deletedPs != null) {
                if ((flags&PackageManager.DONT_DELETE_DATA) == 0) {
                    if (outInfo != null) {
                        outInfo.removedUid = mSettings.removePackageLPw(packageName);
                    }
                    if (deletedPs != null) {
                        updatePermissionsLPw(deletedPs.name, null, 0);
                        if (deletedPs.sharedUser != null) {
                            // remove permissions associated with package
                            mSettings.updateSharedUserPermsLPw(deletedPs, mGlobalGids);
                        }
                    }
                    clearPackagePreferredActivitiesLPw(deletedPs.name);
                }
            }
            // can downgrade to reader
            if (writeSettings) {
                // Save settings now
                mSettings.writeLPr();
            }
        }
    }

    /*
     * Tries to delete system package.
     */
    private boolean deleteSystemPackageLI(PackageParser.Package p,
            int flags, PackageRemovedInfo outInfo, boolean writeSettings) {
        ApplicationInfo applicationInfo = p.applicationInfo;
        //applicable for non-partially installed applications only
        if (applicationInfo == null) {
            Slog.w(TAG, "Package " + p.packageName + " has no applicationInfo.");
            return false;
        }
        PackageSetting ps = null;
        // Confirm if the system package has been updated
        // An updated system app can be deleted. This will also have to restore
        // the system pkg from system partition
        // reader
        synchronized (mPackages) {
            ps = mSettings.getDisabledSystemPkgLPr(p.packageName);
        }
        if (ps == null) {
            Slog.w(TAG, "Attempt to delete unknown system package "+ p.packageName);
            return false;
        } else {
            Log.i(TAG, "Deleting system pkg from data partition");
        }
        // Delete the updated package
        outInfo.isRemovedPackageSystemUpdate = true;
        if (ps.versionCode < p.mVersionCode) {
            // Delete data for downgrades
            flags &= ~PackageManager.DONT_DELETE_DATA;
        } else {
            // Preserve data by setting flag
            flags |= PackageManager.DONT_DELETE_DATA;
        }
        boolean ret = deleteInstalledPackageLI(p, true, flags, outInfo,
                writeSettings);
        if (!ret) {
            return false;
        }
        // writer
        synchronized (mPackages) {
            // Reinstate the old system package
            mSettings.enableSystemPackageLPw(p.packageName);
            // Remove any native libraries from the upgraded package.
            NativeLibraryHelper.removeNativeBinariesLI(p.applicationInfo.nativeLibraryDir);
        }
        // Install the system package
        PackageParser.Package newPkg = scanPackageLI(ps.codePath,
                PackageParser.PARSE_MUST_BE_APK | PackageParser.PARSE_IS_SYSTEM,
                SCAN_MONITOR | SCAN_NO_PATHS, 0);

        if (newPkg == null) {
            Slog.w(TAG, "Failed to restore system package:"+p.packageName+" with error:" + mLastScanError);
            return false;
        }
        // writer
        synchronized (mPackages) {
            updatePermissionsLPw(newPkg.packageName, newPkg,
                    UPDATE_PERMISSIONS_ALL | UPDATE_PERMISSIONS_REPLACE_PKG);
            // can downgrade to reader here
            if (writeSettings) {
                mSettings.writeLPr();
            }
        }
        return true;
    }

    private boolean deleteInstalledPackageLI(PackageParser.Package p,
            boolean deleteCodeAndResources, int flags, PackageRemovedInfo outInfo,
            boolean writeSettings) {
        ApplicationInfo applicationInfo = p.applicationInfo;
        if (applicationInfo == null) {
            Slog.w(TAG, "Package " + p.packageName + " has no applicationInfo.");
            return false;
        }
        if (outInfo != null) {
            outInfo.uid = applicationInfo.uid;
        }

        // Delete package data from internal structures and also remove data if flag is set
        removePackageDataLI(p, outInfo, flags, writeSettings);

        // Delete application code and resources
        if (deleteCodeAndResources) {
            // TODO can pick up from PackageSettings as well
            int installFlags = isExternal(p) ? PackageManager.INSTALL_EXTERNAL : 0;
            installFlags |= isForwardLocked(p) ? PackageManager.INSTALL_FORWARD_LOCK : 0;
            outInfo.args = createInstallArgs(installFlags, applicationInfo.sourceDir,
                    applicationInfo.publicSourceDir, applicationInfo.nativeLibraryDir);
        }
        return true;
    }

    /*
     * This method handles package deletion in general
     */
    private boolean deletePackageLI(String packageName,
            boolean deleteCodeAndResources, int flags, PackageRemovedInfo outInfo,
            boolean writeSettings) {
        if (packageName == null) {
            Slog.w(TAG, "Attempt to delete null packageName.");
            return false;
        }
        PackageParser.Package p;
        boolean dataOnly = false;
        synchronized (mPackages) {
            p = mPackages.get(packageName);
            if (p == null) {
                //this retrieves partially installed apps
                dataOnly = true;
                PackageSetting ps = mSettings.mPackages.get(packageName);
                if (ps == null) {
                    Slog.w(TAG, "Package named '" + packageName +"' doesn't exist.");
                    return false;
                }
                p = ps.pkg;
            }
        }
        if (p == null) {
            Slog.w(TAG, "Package named '" + packageName +"' doesn't exist.");
            return false;
        }

        if (dataOnly) {
            // Delete application data first
            removePackageDataLI(p, outInfo, flags, writeSettings);
            return true;
        }
        // At this point the package should have ApplicationInfo associated with it
        if (p.applicationInfo == null) {
            Slog.w(TAG, "Package " + p.packageName + " has no applicationInfo.");
            return false;
        }
        boolean ret = false;
        if (isSystemApp(p)) {
            Log.i(TAG, "Removing system package:"+p.packageName);
            // When an updated system application is deleted we delete the existing resources as well and
            // fall back to existing code in system partition
            ret = deleteSystemPackageLI(p, flags, outInfo, writeSettings);
        } else {
            Log.i(TAG, "Removing non-system package:"+p.packageName);
            // Kill application pre-emptively especially for apps on sd.
            killApplication(packageName, p.applicationInfo.uid);
            ret = deleteInstalledPackageLI(p, deleteCodeAndResources, flags, outInfo,
                    writeSettings);
        }
        return ret;
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

    private void clearExternalStorageDataSync(String packageName, boolean allData) {
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
        ClearStorageConnection conn = new ClearStorageConnection();
        if (mContext.bindService(containerIntent, conn, Context.BIND_AUTO_CREATE)) {
            try {
                long timeout = SystemClock.uptimeMillis() + 5000;
                synchronized (conn) {
                    long now = SystemClock.uptimeMillis();
                    while (conn.mContainerService == null && now < timeout) {
                        try {
                            conn.wait(timeout - now);
                        } catch (InterruptedException e) {
                        }
                    }
                }
                if (conn.mContainerService == null) {
                    return;
                }
                final File externalCacheDir = Environment
                        .getExternalStorageAppCacheDirectory(packageName);
                try {
                    conn.mContainerService.clearDirectory(externalCacheDir.toString());
                } catch (RemoteException e) {
                }
                if (allData) {
                    final File externalDataDir = Environment
                            .getExternalStorageAppDataDirectory(packageName);
                    try {
                        conn.mContainerService.clearDirectory(externalDataDir.toString());
                    } catch (RemoteException e) {
                    }
                    final File externalMediaDir = Environment
                            .getExternalStorageAppMediaDirectory(packageName);
                    try {
                        conn.mContainerService.clearDirectory(externalMediaDir.toString());
                    } catch (RemoteException e) {
                    }
                }
            } finally {
                mContext.unbindService(conn);
            }
        }
    }

    @Override
    public void clearApplicationUserData(final String packageName,
            final IPackageDataObserver observer, final int userId) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CLEAR_APP_USER_DATA, null);
        checkValidCaller(Binder.getCallingUid(), userId);
        // Queue up an async operation since the package deletion may take a little while.
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                final boolean succeeded;
                synchronized (mInstallLock) {
                    succeeded = clearApplicationUserDataLI(packageName, userId);
                }
                clearExternalStorageDataSync(packageName, true);
                if (succeeded) {
                    // invoke DeviceStorageMonitor's update method to clear any notifications
                    DeviceStorageMonitorService dsm = (DeviceStorageMonitorService)
                            ServiceManager.getService(DeviceStorageMonitorService.SERVICE);
                    if (dsm != null) {
                        dsm.updateMemory();
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

    private boolean clearApplicationUserDataLI(String packageName, int userId) {
        if (packageName == null) {
            Slog.w(TAG, "Attempt to delete null packageName.");
            return false;
        }
        PackageParser.Package p;
        boolean dataOnly = false;
        synchronized (mPackages) {
            p = mPackages.get(packageName);
            if(p == null) {
                dataOnly = true;
                PackageSetting ps = mSettings.mPackages.get(packageName);
                if((ps == null) || (ps.pkg == null)) {
                    Slog.w(TAG, "Package named '" + packageName +"' doesn't exist.");
                    return false;
                }
                p = ps.pkg;
            }
        }

        if (!dataOnly) {
            //need to check this only for fully installed applications
            if (p == null) {
                Slog.w(TAG, "Package named '" + packageName +"' doesn't exist.");
                return false;
            }
            final ApplicationInfo applicationInfo = p.applicationInfo;
            if (applicationInfo == null) {
                Slog.w(TAG, "Package " + packageName + " has no applicationInfo.");
                return false;
            }
        }
        int retCode = mInstaller.clearUserData(packageName, userId);
        if (retCode < 0) {
            Slog.w(TAG, "Couldn't remove cache files for package: "
                    + packageName);
            return false;
        }
        return true;
    }

    public void deleteApplicationCacheFiles(final String packageName,
            final IPackageDataObserver observer) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DELETE_CACHE_FILES, null);
        // Queue up an async operation since the package deletion may take a little while.
        final int userId = UserId.getCallingUserId();
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                final boolean succeded;
                synchronized (mInstallLock) {
                    succeded = deleteApplicationCacheFilesLI(packageName, userId);
                }
                clearExternalStorageDataSync(packageName, false);
                if(observer != null) {
                    try {
                        observer.onRemoveCompleted(packageName, succeded);
                    } catch (RemoteException e) {
                        Log.i(TAG, "Observer no longer exists.");
                    }
                } //end if observer
            } //end run
        });
    }

    private boolean deleteApplicationCacheFilesLI(String packageName, int userId) {
        if (packageName == null) {
            Slog.w(TAG, "Attempt to delete null packageName.");
            return false;
        }
        PackageParser.Package p;
        synchronized (mPackages) {
            p = mPackages.get(packageName);
        }
        if (p == null) {
            Slog.w(TAG, "Package named '" + packageName +"' doesn't exist.");
            return false;
        }
        final ApplicationInfo applicationInfo = p.applicationInfo;
        if (applicationInfo == null) {
            Slog.w(TAG, "Package " + packageName + " has no applicationInfo.");
            return false;
        }
        // TODO: Pass userId to deleteCacheFiles
        int retCode = mInstaller.deleteCacheFiles(packageName);
        if (retCode < 0) {
            Slog.w(TAG, "Couldn't remove cache files for package: "
                       + packageName);
            return false;
        }
        return true;
    }

    public void getPackageSizeInfo(final String packageName,
            final IPackageStatsObserver observer) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.GET_PACKAGE_SIZE, null);

        PackageStats stats = new PackageStats(packageName);

        /*
         * Queue up an async operation since the package measurement may take a
         * little while.
         */
        Message msg = mHandler.obtainMessage(INIT_COPY);
        msg.obj = new MeasureParams(stats, observer);
        mHandler.sendMessage(msg);
    }

    private boolean getPackageSizeInfoLI(String packageName, PackageStats pStats) {
        if (packageName == null) {
            Slog.w(TAG, "Attempt to get size of null packageName.");
            return false;
        }
        PackageParser.Package p;
        boolean dataOnly = false;
        String asecPath = null;
        synchronized (mPackages) {
            p = mPackages.get(packageName);
            if(p == null) {
                dataOnly = true;
                PackageSetting ps = mSettings.mPackages.get(packageName);
                if((ps == null) || (ps.pkg == null)) {
                    Slog.w(TAG, "Package named '" + packageName +"' doesn't exist.");
                    return false;
                }
                p = ps.pkg;
            }
            if (p != null && (isExternal(p) || isForwardLocked(p))) {
                String secureContainerId = cidFromCodePath(p.applicationInfo.sourceDir);
                if (secureContainerId != null) {
                    asecPath = PackageHelper.getSdFilesystem(secureContainerId);
                }
            }
        }
        String publicSrcDir = null;
        if(!dataOnly) {
            final ApplicationInfo applicationInfo = p.applicationInfo;
            if (applicationInfo == null) {
                Slog.w(TAG, "Package " + packageName + " has no applicationInfo.");
                return false;
            }
            if (isForwardLocked(p)) {
                publicSrcDir = applicationInfo.publicSourceDir;
            }
        }
        int res = mInstaller.getSizeInfo(packageName, p.mPath, publicSrcDir,
                asecPath, pStats);
        if (res < 0) {
            return false;
        }

        // Fix-up for forward-locked applications in ASEC containers.
        if (!isExternal(p)) {
            pStats.codeSize += pStats.externalCodeSize;
            pStats.externalCodeSize = 0L;
        }

        return true;
    }


    public void addPackageToPreferred(String packageName) {
        Slog.w(TAG, "addPackageToPreferred: this is now a no-op");
    }

    public void removePackageFromPreferred(String packageName) {
        Slog.w(TAG, "removePackageFromPreferred: this is now a no-op");
    }

    public List<PackageInfo> getPreferredPackages(int flags) {
        return new ArrayList<PackageInfo>();
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
    
    public void addPreferredActivity(IntentFilter filter, int match,
            ComponentName[] set, ComponentName activity) {
        // writer
        synchronized (mPackages) {
            if (mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.SET_PREFERRED_APPLICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (getUidTargetSdkVersionLockedLPr(Binder.getCallingUid())
                        < Build.VERSION_CODES.FROYO) {
                    Slog.w(TAG, "Ignoring addPreferredActivity() from uid "
                            + Binder.getCallingUid());
                    return;
                }
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
            }
            
            Slog.i(TAG, "Adding preferred activity " + activity + ":");
            filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
            mSettings.mPreferredActivities.addFilter(
                    new PreferredActivity(filter, match, set, activity));
            scheduleWriteSettingsLocked();            
        }
    }

    public void replacePreferredActivity(IntentFilter filter, int match,
            ComponentName[] set, ComponentName activity) {
        if (filter.countActions() != 1) {
            throw new IllegalArgumentException(
                    "replacePreferredActivity expects filter to have only 1 action.");
        }
        if (filter.countCategories() != 1) {
            throw new IllegalArgumentException(
                    "replacePreferredActivity expects filter to have only 1 category.");
        }
        if (filter.countDataAuthorities() != 0
                || filter.countDataPaths() != 0
                || filter.countDataSchemes() != 0
                || filter.countDataTypes() != 0) {
            throw new IllegalArgumentException(
                    "replacePreferredActivity expects filter to have no data authorities, " +
                    "paths, schemes or types.");
        }

        synchronized (mPackages) {
            if (mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.SET_PREFERRED_APPLICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (getUidTargetSdkVersionLockedLPr(Binder.getCallingUid())
                        < Build.VERSION_CODES.FROYO) {
                    Slog.w(TAG, "Ignoring replacePreferredActivity() from uid "
                            + Binder.getCallingUid());
                    return;
                }
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
            }
            
            ArrayList<PreferredActivity> removed = null;
            Iterator<PreferredActivity> it = mSettings.mPreferredActivities.filterIterator();
            String action = filter.getAction(0);
            String category = filter.getCategory(0);
            while (it.hasNext()) {
                PreferredActivity pa = it.next();
                if (pa.getAction(0).equals(action) && pa.getCategory(0).equals(category)) {
                    if (removed == null) {
                        removed = new ArrayList<PreferredActivity>();
                    }
                    removed.add(pa);
                    Log.i(TAG, "Removing preferred activity " + pa.mPref.mComponent + ":");
                    filter.dump(new LogPrinter(Log.INFO, TAG), "  ");
                }
            }
            if (removed != null) {
                for (int i=0; i<removed.size(); i++) {
                    PreferredActivity pa = removed.get(i);
                    mSettings.mPreferredActivities.removeFilter(pa);
                }
            }
            addPreferredActivity(filter, match, set, activity);
        }
    }

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

            if (clearPackagePreferredActivitiesLPw(packageName)) {
                scheduleWriteSettingsLocked();            
            }
        }
    }

    boolean clearPackagePreferredActivitiesLPw(String packageName) {
        ArrayList<PreferredActivity> removed = null;
        Iterator<PreferredActivity> it = mSettings.mPreferredActivities.filterIterator();
        while (it.hasNext()) {
            PreferredActivity pa = it.next();
            if (pa.mPref.mComponent.getPackageName().equals(packageName)) {
                if (removed == null) {
                    removed = new ArrayList<PreferredActivity>();
                }
                removed.add(pa);
            }
        }
        if (removed != null) {
            for (int i=0; i<removed.size(); i++) {
                PreferredActivity pa = removed.get(i);
                mSettings.mPreferredActivities.removeFilter(pa);
            }
            return true;
        }
        return false;
    }

    public int getPreferredActivities(List<IntentFilter> outFilters,
            List<ComponentName> outActivities, String packageName) {

        int num = 0;
        // reader
        synchronized (mPackages) {
            final Iterator<PreferredActivity> it = mSettings.mPreferredActivities.filterIterator();
            while (it.hasNext()) {
                final PreferredActivity pa = it.next();
                if (packageName == null
                        || pa.mPref.mComponent.getPackageName().equals(packageName)) {
                    if (outFilters != null) {
                        outFilters.add(new IntentFilter(pa));
                    }
                    if (outActivities != null) {
                        outActivities.add(pa.mPref.mComponent);
                    }
                }
            }
        }

        return num;
    }

    @Override
    public void setApplicationEnabledSetting(String appPackageName,
            int newState, int flags, int userId) {
        if (!sUserManager.exists(userId)) return;
        setEnabledSetting(appPackageName, null, newState, flags, userId);
    }

    @Override
    public void setComponentEnabledSetting(ComponentName componentName,
            int newState, int flags, int userId) {
        if (!sUserManager.exists(userId)) return;
        setEnabledSetting(componentName.getPackageName(),
                componentName.getClassName(), newState, flags, userId);
    }

    private void setEnabledSetting(
            final String packageName, String className, int newState, final int flags, int userId) {
        if (!(newState == COMPONENT_ENABLED_STATE_DEFAULT
              || newState == COMPONENT_ENABLED_STATE_ENABLED
              || newState == COMPONENT_ENABLED_STATE_DISABLED
              || newState == COMPONENT_ENABLED_STATE_DISABLED_USER)) {
            throw new IllegalArgumentException("Invalid new component state: "
                    + newState);
        }
        PackageSetting pkgSetting;
        final int uid = Binder.getCallingUid();
        final int permission = mContext.checkCallingPermission(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        checkValidCaller(uid, userId);
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
                    throw new IllegalArgumentException(
                            "Unknown package: " + packageName);
                }
                throw new IllegalArgumentException(
                        "Unknown component: " + packageName
                        + "/" + className);
            }
            // Allow root and verify that userId is not being specified by a different user
            if (!allowedByPermission && !UserId.isSameApp(uid, pkgSetting.appId)) {
                throw new SecurityException(
                        "Permission Denial: attempt to change component state from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + uid + ", package uid=" + pkgSetting.appId);
            }
            if (className == null) {
                // We're dealing with an application/package level state change
                if (pkgSetting.getEnabled(userId) == newState) {
                    // Nothing to do
                    return;
                }
                pkgSetting.setEnabled(newState, userId);
                // pkgSetting.pkg.mSetEnabled = newState;
            } else {
                // We're dealing with a component level state change
                // First, verify that this is a valid class name.
                PackageParser.Package pkg = pkgSetting.pkg;
                if (pkg == null || !pkg.hasComponentClassName(className)) {
                    if (pkg.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN) {
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
            mSettings.writePackageRestrictionsLPr(userId);
            packageUid = UserId.getUid(userId, pkgSetting.appId);
            components = mPendingBroadcasts.get(packageName);
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
                mPendingBroadcasts.remove(packageName);
            } else {
                if (newPackage) {
                    mPendingBroadcasts.put(packageName, components);
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
                sendPackageChangedBroadcast(packageName,
                        (flags&PackageManager.DONT_KILL_APP) != 0, components, packageUid);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
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
        sendPackageBroadcast(Intent.ACTION_PACKAGE_CHANGED,  packageName, null, extras, null, null,
                UserId.getUserId(packageUid));
    }

    public void setPackageStoppedState(String packageName, boolean stopped, int userId) {
        if (!sUserManager.exists(userId)) return;
        final int uid = Binder.getCallingUid();
        final int permission = mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        final boolean allowedByPermission = (permission == PackageManager.PERMISSION_GRANTED);
        checkValidCaller(uid, userId);
        // writer
        synchronized (mPackages) {
            if (mSettings.setPackageStoppedStateLPw(packageName, stopped, allowedByPermission,
                    uid, userId)) {
                scheduleWritePackageRestrictionsLocked(userId);
            }
        }
    }

    public String getInstallerPackageName(String packageName) {
        // reader
        synchronized (mPackages) {
            return mSettings.getInstallerPackageNameLPr(packageName);
        }
    }

    @Override
    public int getApplicationEnabledSetting(String packageName, int userId) {
        if (!sUserManager.exists(userId)) return COMPONENT_ENABLED_STATE_DISABLED;
        int uid = Binder.getCallingUid();
        checkValidCaller(uid, userId);
        // reader
        synchronized (mPackages) {
            return mSettings.getApplicationEnabledSettingLPr(packageName, userId);
        }
    }

    @Override
    public int getComponentEnabledSetting(ComponentName componentName, int userId) {
        if (!sUserManager.exists(userId)) return COMPONENT_ENABLED_STATE_DISABLED;
        int uid = Binder.getCallingUid();
        checkValidCaller(uid, userId);
        // reader
        synchronized (mPackages) {
            return mSettings.getComponentEnabledSettingLPr(componentName, userId);
        }
    }

    public void enterSafeMode() {
        enforceSystemOrRoot("Only the system can request entering safe mode");

        if (!mSystemReady) {
            mSafeMode = true;
        }
    }

    public void systemReady() {
        mSystemReady = true;

        // Read the compatibilty setting when the system is ready.
        boolean compatibilityModeEnabled = android.provider.Settings.System.getInt(
                mContext.getContentResolver(),
                android.provider.Settings.System.COMPATIBILITY_MODE, 1) == 1;
        PackageParser.setCompatibilityModeEnabled(compatibilityModeEnabled);
        if (DEBUG_SETTINGS) {
            Log.d(TAG, "compatibility mode:" + compatibilityModeEnabled);
        }
    }

    public boolean isSafeMode() {
        return mSafeMode;
    }

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

        public static final int DUMP_RESOLVERS = 1 << 2;

        public static final int DUMP_PERMISSIONS = 1 << 3;

        public static final int DUMP_PACKAGES = 1 << 4;

        public static final int DUMP_SHARED_USERS = 1 << 5;

        public static final int DUMP_MESSAGES = 1 << 6;

        public static final int DUMP_PROVIDERS = 1 << 7;

        public static final int DUMP_VERIFIERS = 1 << 8;

        public static final int DUMP_PREFERRED = 1 << 9;

        public static final int DUMP_PREFERRED_XML = 1 << 10;

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
        
        String packageName = null;
        
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
                pw.println("  [-h] [-f] [cmd] ...");
                pw.println("    -f: print details of intent filters");
                pw.println("    -h: print this help");
                pw.println("  cmd may be one of:");
                pw.println("    l[ibraries]: list known shared libraries");
                pw.println("    f[ibraries]: list device features");
                pw.println("    r[esolvers]: dump intent resolvers");
                pw.println("    perm[issions]: dump permissions");
                pw.println("    pref[erred]: print preferred package settings");
                pw.println("    preferred-xml: print preferred package settings as xml");
                pw.println("    prov[iders]: dump content providers");
                pw.println("    p[ackages]: dump installed packages");
                pw.println("    s[hared-users]: dump shared user IDs");
                pw.println("    m[essages]: print collected runtime messages");
                pw.println("    v[erifiers]: print package verifier info");
                pw.println("    <package.name>: info about given package");
                return;
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
            } else if ("l".equals(cmd) || "libraries".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_LIBS);
            } else if ("f".equals(cmd) || "features".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_FEATURES);
            } else if ("r".equals(cmd) || "resolvers".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_RESOLVERS);
            } else if ("perm".equals(cmd) || "permissions".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PERMISSIONS);
            } else if ("pref".equals(cmd) || "preferred".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PREFERRED);
            } else if ("preferred-xml".equals(cmd)) {
                dumpState.setDump(DumpState.DUMP_PREFERRED_XML);
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
            }
        }

        // reader
        synchronized (mPackages) {
            if (dumpState.isDumping(DumpState.DUMP_VERIFIERS) && packageName == null) {
                if (dumpState.onTitlePrinted())
                    pw.println(" ");
                pw.println("Verifiers:");
                pw.print("  Required: ");
                pw.print(mRequiredVerifierPackage);
                pw.print(" (uid=");
                pw.print(getPackageUid(mRequiredVerifierPackage, 0));
                pw.println(")");
            }

            if (dumpState.isDumping(DumpState.DUMP_LIBS) && packageName == null) {
                if (dumpState.onTitlePrinted())
                    pw.println(" ");
                pw.println("Libraries:");
                final Iterator<String> it = mSharedLibraries.keySet().iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    pw.print("  ");
                    pw.print(name);
                    pw.print(" -> ");
                    pw.println(mSharedLibraries.get(name));
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_FEATURES) && packageName == null) {
                if (dumpState.onTitlePrinted())
                    pw.println(" ");
                pw.println("Features:");
                Iterator<String> it = mAvailableFeatures.keySet().iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    pw.print("  ");
                    pw.println(name);
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_RESOLVERS)) {
                if (mActivities.dump(pw, dumpState.getTitlePrinted() ? "\nActivity Resolver Table:"
                        : "Activity Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS))) {
                    dumpState.setTitlePrinted(true);
                }
                if (mReceivers.dump(pw, dumpState.getTitlePrinted() ? "\nReceiver Resolver Table:"
                        : "Receiver Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS))) {
                    dumpState.setTitlePrinted(true);
                }
                if (mServices.dump(pw, dumpState.getTitlePrinted() ? "\nService Resolver Table:"
                        : "Service Resolver Table:", "  ", packageName,
                        dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS))) {
                    dumpState.setTitlePrinted(true);
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_PREFERRED)) {
                if (mSettings.mPreferredActivities.dump(pw,
                        dumpState.getTitlePrinted() ? "\nPreferred Activities:"
                            : "Preferred Activities:", "  ",
                        packageName, dumpState.isOptionEnabled(DumpState.OPTION_SHOW_FILTERS))) {
                    dumpState.setTitlePrinted(true);
                }
            }

            if (dumpState.isDumping(DumpState.DUMP_PREFERRED_XML)) {
                pw.flush();
                FileOutputStream fout = new FileOutputStream(fd);
                BufferedOutputStream str = new BufferedOutputStream(fout);
                XmlSerializer serializer = new FastXmlSerializer();
                try {
                    serializer.setOutput(str, "utf-8");
                    serializer.startDocument(null, true);
                    serializer.setFeature(
                            "http://xmlpull.org/v1/doc/features.html#indent-output", true);
                    mSettings.writePreferredActivitiesLPr(serializer);
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

            if (dumpState.isDumping(DumpState.DUMP_PERMISSIONS)) {
                mSettings.dumpPermissionsLPr(pw, packageName, dumpState);
            }

            if (dumpState.isDumping(DumpState.DUMP_PROVIDERS)) {
                boolean printedSomething = false;
                for (PackageParser.Provider p : mProvidersByComponent.values()) {
                    if (packageName != null && !packageName.equals(p.info.packageName)) {
                        continue;
                    }
                    if (!printedSomething) {
                        if (dumpState.onTitlePrinted())
                            pw.println(" ");
                        pw.println("Registered ContentProviders:");
                        printedSomething = true;
                    }
                    pw.print("  "); pw.print(p.getComponentShortName()); pw.println(":");
                    pw.print("    "); pw.println(p.toString());
                }
                printedSomething = false;
                for (Map.Entry<String, PackageParser.Provider> entry : mProviders.entrySet()) {
                    PackageParser.Provider p = entry.getValue();
                    if (packageName != null && !packageName.equals(p.info.packageName)) {
                        continue;
                    }
                    if (!printedSomething) {
                        if (dumpState.onTitlePrinted())
                            pw.println(" ");
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
            
            if (dumpState.isDumping(DumpState.DUMP_PACKAGES)) {
                mSettings.dumpPackagesLPr(pw, packageName, dumpState);
            }

            if (dumpState.isDumping(DumpState.DUMP_SHARED_USERS)) {
                mSettings.dumpSharedUsersLPr(pw, packageName, dumpState);
            }

            if (dumpState.isDumping(DumpState.DUMP_MESSAGES) && packageName == null) {
                if (dumpState.onTitlePrinted())
                    pw.println(" ");
                mSettings.dumpReadMessagesLPr(pw, dumpState);

                pw.println(" ");
                pw.println("Package warning messages:");
                final File fname = getSettingsProblemFile();
                FileInputStream in = null;
                try {
                    in = new FileInputStream(fname);
                    final int avail = in.available();
                    final byte[] data = new byte[avail];
                    in.read(data);
                    pw.print(new String(data));
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
    }

    // ------- apps on sdcard specific code -------
    static final boolean DEBUG_SD_INSTALL = false;

    private static final String SD_ENCRYPTION_KEYSTORE_NAME = "AppsOnSD";

    private static final String SD_ENCRYPTION_ALGORITHM = "AES";

    private boolean mMediaMounted = false;

    private String getEncryptKey() {
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

    /* package */static String getTempContainerId() {
        int tmpIdx = 1;
        String list[] = PackageHelper.getSecureContainerList();
        if (list != null) {
            for (final String name : list) {
                // Ignore null and non-temporary container entries
                if (name == null || !name.startsWith(mTempContainerPrefix)) {
                    continue;
                }

                String subStr = name.substring(mTempContainerPrefix.length());
                try {
                    int cid = Integer.parseInt(subStr);
                    if (cid >= tmpIdx) {
                        tmpIdx = cid + 1;
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        return mTempContainerPrefix + tmpIdx;
    }

    /*
     * Update media status on PackageManager.
     */
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
                updateExternalMediaStatusInner(mediaStatus, reportStatus);
            }
        });
    }

    /**
     * Called by MountService when the initial ASECs to scan are available.
     * Should block until all the ASEC containers are finished being scanned.
     */
    public void scanAvailableAsecs() {
        updateExternalMediaStatusInner(true, false);
    }

    /*
     * Collect information of applications on external media, map them against
     * existing containers and update information based on current mount status.
     * Please note that we always have to report status if reportStatus has been
     * set to true especially when unloading packages.
     */
    private void updateExternalMediaStatusInner(boolean isMounted, boolean reportStatus) {
        // Collection of uids
        int uidArr[] = null;
        // Collection of stale containers
        HashSet<String> removeCids = new HashSet<String>();
        // Collection of packages on external media with valid containers.
        HashMap<AsecInstallArgs, String> processCids = new HashMap<AsecInstallArgs, String>();
        // Get list of secure containers.
        final String list[] = PackageHelper.getSecureContainerList();
        if (list == null || list.length == 0) {
            Log.i(TAG, "No secure containers on sdcard");
        } else {
            // Process list of secure containers and categorize them
            // as active or stale based on their package internal state.
            int uidList[] = new int[list.length];
            int num = 0;
            // reader
            synchronized (mPackages) {
                for (String cid : list) {
                    if (DEBUG_SD_INSTALL)
                        Log.i(TAG, "Processing container " + cid);
                    String pkgName = getAsecPackageName(cid);
                    if (pkgName == null) {
                        if (DEBUG_SD_INSTALL)
                            Log.i(TAG, "Container : " + cid + " stale");
                        removeCids.add(cid);
                        continue;
                    }
                    if (DEBUG_SD_INSTALL)
                        Log.i(TAG, "Looking for pkg : " + pkgName);

                    final PackageSetting ps = mSettings.mPackages.get(pkgName);
                    if (ps == null) {
                        Log.i(TAG, "Deleting container with no matching settings " + cid);
                        removeCids.add(cid);
                        continue;
                    }

                    final AsecInstallArgs args = new AsecInstallArgs(cid, isForwardLocked(ps));
                    // The package status is changed only if the code path
                    // matches between settings and the container id.
                    if (ps.codePathString != null && ps.codePathString.equals(args.getCodePath())) {
                        if (DEBUG_SD_INSTALL) {
                            Log.i(TAG, "Container : " + cid + " corresponds to pkg : " + pkgName
                                    + " at code path: " + ps.codePathString);
                        }

                        // We do have a valid package installed on sdcard
                        processCids.put(args, ps.codePathString);
                        final int uid = ps.appId;
                        if (uid != -1) {
                            uidList[num++] = uid;
                        }
                    } else {
                        Log.i(TAG, "Deleting stale container for " + cid);
                        removeCids.add(cid);
                    }
                }
            }

            if (num > 0) {
                // Sort uid list
                Arrays.sort(uidList, 0, num);
                // Throw away duplicates
                uidArr = new int[num];
                uidArr[0] = uidList[0];
                int di = 0;
                for (int i = 1; i < num; i++) {
                    if (uidList[i - 1] != uidList[i]) {
                        uidArr[di++] = uidList[i];
                    }
                }
            }
        }
        // Process packages with valid entries.
        if (isMounted) {
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "Loading packages");
            loadMediaPackages(processCids, uidArr, removeCids);
            startCleaningPackages();
        } else {
            if (DEBUG_SD_INSTALL)
                Log.i(TAG, "Unloading packages");
            unloadMediaPackages(processCids, uidArr, reportStatus);
        }
    }

   private void sendResourcesChangedBroadcast(boolean mediaStatus, ArrayList<String> pkgList,
            int uidArr[], IIntentReceiver finishedReceiver) {
        int size = pkgList.size();
        if (size > 0) {
            // Send broadcasts here
            Bundle extras = new Bundle();
            extras.putStringArray(Intent.EXTRA_CHANGED_PACKAGE_LIST, pkgList
                    .toArray(new String[size]));
            if (uidArr != null) {
                extras.putIntArray(Intent.EXTRA_CHANGED_UID_LIST, uidArr);
            }
            String action = mediaStatus ? Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE
                    : Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE;
            sendPackageBroadcast(action, null, null, extras, null, finishedReceiver, UserId.USER_ALL);
        }
    }

   /*
     * Look at potentially valid container ids from processCids If package
     * information doesn't match the one on record or package scanning fails,
     * the cid is added to list of removeCids. We currently don't delete stale
     * containers.
     */
   private void loadMediaPackages(HashMap<AsecInstallArgs, String> processCids, int uidArr[],
            HashSet<String> removeCids) {
        ArrayList<String> pkgList = new ArrayList<String>();
        Set<AsecInstallArgs> keys = processCids.keySet();
        boolean doGc = false;
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
                if (codePath == null || !codePath.equals(args.getCodePath())) {
                    Slog.e(TAG, "Container " + args.cid + " cachepath " + args.getCodePath()
                            + " does not match one in settings " + codePath);
                    continue;
                }
                // Parse package
                int parseFlags = mDefParseFlags;
                if (args.isExternal()) {
                    parseFlags |= PackageParser.PARSE_ON_SDCARD;
                }
                if (args.isFwdLocked()) {
                    parseFlags |= PackageParser.PARSE_FORWARD_LOCK;
                }

                doGc = true;
                synchronized (mInstallLock) {
                    final PackageParser.Package pkg = scanPackageLI(new File(codePath), parseFlags,
                            0, 0);
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
                    // Don't destroy container here. Wait till gc clears things
                    // up.
                    removeCids.add(args.cid);
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
            final boolean regrantPermissions = mSettings.mExternalSdkPlatform != mSdkVersion;
            if (regrantPermissions)
                Slog.i(TAG, "Platform changed from " + mSettings.mExternalSdkPlatform + " to "
                        + mSdkVersion + "; regranting permissions for external storage");
            mSettings.mExternalSdkPlatform = mSdkVersion;

            // Make sure group IDs have been assigned, and any permission
            // changes in other apps are accounted for
            updatePermissionsLPw(null, null, UPDATE_PERMISSIONS_ALL
                    | (regrantPermissions
                            ? (UPDATE_PERMISSIONS_REPLACE_PKG|UPDATE_PERMISSIONS_REPLACE_ALL)
                            : 0));
            // can downgrade to reader
            // Persist settings
            mSettings.writeLPr();
        }
        // Send a broadcast to let everyone know we are done processing
        if (pkgList.size() > 0) {
            sendResourcesChangedBroadcast(true, pkgList, uidArr, null);
        }
        // Force gc to avoid any stale parser references that we might have.
        if (doGc) {
            Runtime.getRuntime().gc();
        }
        // List stale containers and destroy stale temporary containers.
        if (removeCids != null) {
            for (String cid : removeCids) {
                if (cid.startsWith(mTempContainerPrefix)) {
                    Log.i(TAG, "Destroying stale temporary container " + cid);
                    PackageHelper.destroySdDir(cid);
                } else {
                    Log.w(TAG, "Container " + cid + " is stale");
               }
           }
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
     * data from internal structures, sending broadcasts about diabled packages,
     * gc'ing to free up references, unmounting all secure containers
     * corresponding to packages on external media, and posting a
     * UPDATED_MEDIA_STATUS message if status has been requested. Please note
     * that we always have to post this message if status has been requested no
     * matter what.
     */
    private void unloadMediaPackages(HashMap<AsecInstallArgs, String> processCids, int uidArr[],
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
                boolean res = deletePackageLI(pkgName, false, PackageManager.DONT_DELETE_DATA,
                        outInfo, false);
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
            sendResourcesChangedBroadcast(false, pkgList, uidArr, new IIntentReceiver.Stub() {
                public void performReceive(Intent intent, int resultCode, String data,
                        Bundle extras, boolean ordered, boolean sticky) throws RemoteException {
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

    public void movePackage(final String packageName, final IPackageMoveObserver observer,
            final int flags) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MOVE_PACKAGE, null);
        int returnCode = PackageManager.MOVE_SUCCEEDED;
        int currFlags = 0;
        int newFlags = 0;
        // reader
        synchronized (mPackages) {
            PackageParser.Package pkg = mPackages.get(packageName);
            if (pkg == null) {
                returnCode = PackageManager.MOVE_FAILED_DOESNT_EXIST;
            } else {
                // Disable moving fwd locked apps and system packages
                if (pkg.applicationInfo != null && isSystemApp(pkg)) {
                    Slog.w(TAG, "Cannot move system application");
                    returnCode = PackageManager.MOVE_FAILED_SYSTEM_PACKAGE;
                } else if (pkg.mOperationPending) {
                    Slog.w(TAG, "Attempt to move package which has pending operations");
                    returnCode = PackageManager.MOVE_FAILED_OPERATION_PENDING;
                } else {
                    // Find install location first
                    if ((flags & PackageManager.MOVE_EXTERNAL_MEDIA) != 0
                            && (flags & PackageManager.MOVE_INTERNAL) != 0) {
                        Slog.w(TAG, "Ambigous flags specified for move location.");
                        returnCode = PackageManager.MOVE_FAILED_INVALID_LOCATION;
                    } else {
                        newFlags = (flags & PackageManager.MOVE_EXTERNAL_MEDIA) != 0 ? PackageManager.INSTALL_EXTERNAL
                                : PackageManager.INSTALL_INTERNAL;
                        currFlags = isExternal(pkg) ? PackageManager.INSTALL_EXTERNAL
                                : PackageManager.INSTALL_INTERNAL;

                        if (newFlags == currFlags) {
                            Slog.w(TAG, "No move required. Trying to move to same location");
                            returnCode = PackageManager.MOVE_FAILED_INVALID_LOCATION;
                        } else {
                            if (isForwardLocked(pkg)) {
                                currFlags |= PackageManager.INSTALL_FORWARD_LOCK;
                                newFlags |= PackageManager.INSTALL_FORWARD_LOCK;
                            }
                        }
                    }
                    if (returnCode == PackageManager.MOVE_SUCCEEDED) {
                        pkg.mOperationPending = true;
                    }
                }
            }

            /*
             * TODO this next block probably shouldn't be inside the lock. We
             * can't guarantee these won't change after this is fired off
             * anyway.
             */
            if (returnCode != PackageManager.MOVE_SUCCEEDED) {
                processPendingMove(new MoveParams(null, observer, 0, packageName, null, -1),
                        returnCode);
            } else {
                Message msg = mHandler.obtainMessage(INIT_COPY);
                InstallArgs srcArgs = createInstallArgs(currFlags, pkg.applicationInfo.sourceDir,
                        pkg.applicationInfo.publicSourceDir, pkg.applicationInfo.nativeLibraryDir);
                MoveParams mp = new MoveParams(srcArgs, observer, newFlags, packageName,
                        pkg.applicationInfo.dataDir, pkg.applicationInfo.uid);
                msg.obj = mp;
                mHandler.sendMessage(msg);
            }
        }
    }

    private void processPendingMove(final MoveParams mp, final int currentStatus) {
        // Queue up an async operation since the package deletion may take a
        // little while.
        mHandler.post(new Runnable() {
            public void run() {
                // TODO fix this; this does nothing.
                mHandler.removeCallbacks(this);
                int returnCode = currentStatus;
                if (currentStatus == PackageManager.MOVE_SUCCEEDED) {
                    int uidArr[] = null;
                    ArrayList<String> pkgList = null;
                    synchronized (mPackages) {
                        PackageParser.Package pkg = mPackages.get(mp.packageName);
                        if (pkg == null) {
                            Slog.w(TAG, " Package " + mp.packageName
                                    + " doesn't exist. Aborting move");
                            returnCode = PackageManager.MOVE_FAILED_DOESNT_EXIST;
                        } else if (!mp.srcArgs.getCodePath().equals(pkg.applicationInfo.sourceDir)) {
                            Slog.w(TAG, "Package " + mp.packageName + " code path changed from "
                                    + mp.srcArgs.getCodePath() + " to "
                                    + pkg.applicationInfo.sourceDir
                                    + " Aborting move and returning error");
                            returnCode = PackageManager.MOVE_FAILED_INTERNAL_ERROR;
                        } else {
                            uidArr = new int[] {
                                pkg.applicationInfo.uid
                            };
                            pkgList = new ArrayList<String>();
                            pkgList.add(mp.packageName);
                        }
                    }
                    if (returnCode == PackageManager.MOVE_SUCCEEDED) {
                        // Send resources unavailable broadcast
                        sendResourcesChangedBroadcast(false, pkgList, uidArr, null);
                        // Update package code and resource paths
                        synchronized (mInstallLock) {
                            synchronized (mPackages) {
                                PackageParser.Package pkg = mPackages.get(mp.packageName);
                                // Recheck for package again.
                                if (pkg == null) {
                                    Slog.w(TAG, " Package " + mp.packageName
                                            + " doesn't exist. Aborting move");
                                    returnCode = PackageManager.MOVE_FAILED_DOESNT_EXIST;
                                } else if (!mp.srcArgs.getCodePath().equals(
                                        pkg.applicationInfo.sourceDir)) {
                                    Slog.w(TAG, "Package " + mp.packageName
                                            + " code path changed from " + mp.srcArgs.getCodePath()
                                            + " to " + pkg.applicationInfo.sourceDir
                                            + " Aborting move and returning error");
                                    returnCode = PackageManager.MOVE_FAILED_INTERNAL_ERROR;
                                } else {
                                    final String oldCodePath = pkg.mPath;
                                    final String newCodePath = mp.targetArgs.getCodePath();
                                    final String newResPath = mp.targetArgs.getResourcePath();
                                    final String newNativePath = mp.targetArgs
                                            .getNativeLibraryPath();

                                    try {
                                        final File newNativeDir = new File(newNativePath);

                                        final String libParentDir = newNativeDir.getParentFile()
                                                .getCanonicalPath();
                                        if (newNativeDir.getParentFile().getCanonicalPath()
                                                .equals(pkg.applicationInfo.dataDir)) {
                                            if (mInstaller
                                                    .unlinkNativeLibraryDirectory(pkg.applicationInfo.dataDir) < 0) {
                                                returnCode = PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE;
                                            } else {
                                                NativeLibraryHelper.copyNativeBinariesIfNeededLI(
                                                        new File(newCodePath), newNativeDir);
                                            }
                                        } else {
                                            if (mInstaller.linkNativeLibraryDirectory(
                                                    pkg.applicationInfo.dataDir, newNativePath) < 0) {
                                                returnCode = PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE;
                                            }
                                        }
                                    } catch (IOException e) {
                                        returnCode = PackageManager.MOVE_FAILED_INVALID_LOCATION;
                                    }


                                    if (returnCode == PackageManager.MOVE_SUCCEEDED) {
                                        pkg.mPath = newCodePath;
                                        // Move dex files around
                                        if (moveDexFilesLI(pkg) != PackageManager.INSTALL_SUCCEEDED) {
                                            // Moving of dex files failed. Set
                                            // error code and abort move.
                                            pkg.mPath = pkg.mScanPath;
                                            returnCode = PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE;
                                        }
                                    }

                                    if (returnCode == PackageManager.MOVE_SUCCEEDED) {
                                        pkg.mScanPath = newCodePath;
                                        pkg.applicationInfo.sourceDir = newCodePath;
                                        pkg.applicationInfo.publicSourceDir = newResPath;
                                        pkg.applicationInfo.nativeLibraryDir = newNativePath;
                                        PackageSetting ps = (PackageSetting) pkg.mExtras;
                                        ps.codePath = new File(pkg.applicationInfo.sourceDir);
                                        ps.codePathString = ps.codePath.getPath();
                                        ps.resourcePath = new File(
                                                pkg.applicationInfo.publicSourceDir);
                                        ps.resourcePathString = ps.resourcePath.getPath();
                                        ps.nativeLibraryPathString = newNativePath;
                                        // Set the application info flag
                                        // correctly.
                                        if ((mp.flags & PackageManager.INSTALL_EXTERNAL) != 0) {
                                            pkg.applicationInfo.flags |= ApplicationInfo.FLAG_EXTERNAL_STORAGE;
                                        } else {
                                            pkg.applicationInfo.flags &= ~ApplicationInfo.FLAG_EXTERNAL_STORAGE;
                                        }
                                        ps.setFlags(pkg.applicationInfo.flags);
                                        mAppDirs.remove(oldCodePath);
                                        mAppDirs.put(newCodePath, pkg);
                                        // Persist settings
                                        mSettings.writeLPr();
                                    }
                                }
                            }
                        }
                        // Send resources available broadcast
                        sendResourcesChangedBroadcast(true, pkgList, uidArr, null);
                    }
                }
                if (returnCode != PackageManager.MOVE_SUCCEEDED) {
                    // Clean up failed installation
                    if (mp.targetArgs != null) {
                        mp.targetArgs.doPostInstall(PackageManager.INSTALL_FAILED_INTERNAL_ERROR,
                                -1);
                    }
                } else {
                    // Force a gc to clear things up.
                    Runtime.getRuntime().gc();
                    // Delete older code
                    synchronized (mInstallLock) {
                        mp.srcArgs.doPostDeleteLI(true);
                    }
                }

                // Allow more operations on this file if we didn't fail because
                // an operation was already pending for this package.
                if (returnCode != PackageManager.MOVE_FAILED_OPERATION_PENDING) {
                    synchronized (mPackages) {
                        PackageParser.Package pkg = mPackages.get(mp.packageName);
                        if (pkg != null) {
                            pkg.mOperationPending = false;
                       }
                   }
                }

                IPackageMoveObserver observer = mp.observer;
                if (observer != null) {
                    try {
                        observer.packageMoved(mp.packageName, returnCode);
                    } catch (RemoteException e) {
                        Log.i(TAG, "Observer no longer exists.");
                    }
                }
            }
        });
    }

    public boolean setInstallLocation(int loc) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS,
                null);
        if (getInstallLocation() == loc) {
            return true;
        }
        if (loc == PackageHelper.APP_INSTALL_AUTO || loc == PackageHelper.APP_INSTALL_INTERNAL
                || loc == PackageHelper.APP_INSTALL_EXTERNAL) {
            android.provider.Settings.System.putInt(mContext.getContentResolver(),
                    android.provider.Settings.Secure.DEFAULT_INSTALL_LOCATION, loc);
            return true;
        }
        return false;
   }

    public int getInstallLocation() {
        return android.provider.Settings.System.getInt(mContext.getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INSTALL_LOCATION,
                PackageHelper.APP_INSTALL_AUTO);
    }

    public UserInfo createUser(String name, int flags) {
        // TODO(kroot): Add a real permission for creating users
        enforceSystemOrRoot("Only the system can create users");

        UserInfo userInfo = sUserManager.createUser(name, flags);
        if (userInfo != null) {
            Intent addedIntent = new Intent(Intent.ACTION_USER_ADDED);
            addedIntent.putExtra(Intent.EXTRA_USERID, userInfo.id);
            mContext.sendBroadcast(addedIntent, android.Manifest.permission.MANAGE_ACCOUNTS);
        }
        return userInfo;
    }

    public boolean removeUser(int userId) {
        // TODO(kroot): Add a real permission for removing users
        enforceSystemOrRoot("Only the system can remove users");

        if (userId == 0 || !sUserManager.exists(userId)) {
            return false;
        }

        cleanUpUser(userId);

        if (sUserManager.removeUser(userId)) {
            // Let other services shutdown any activity
            Intent addedIntent = new Intent(Intent.ACTION_USER_REMOVED);
            addedIntent.putExtra(Intent.EXTRA_USERID, userId);
            mContext.sendBroadcast(addedIntent, android.Manifest.permission.MANAGE_ACCOUNTS);
        }
        sUserManager.removePackageFolders(userId);
        return true;
    }

    private void cleanUpUser(int userId) {
        // Disable all the packages for the user first
        synchronized (mPackages) {
            Set<Entry<String, PackageSetting>> entries = mSettings.mPackages.entrySet();
            for (Entry<String, PackageSetting> entry : entries) {
                entry.getValue().removeUser(userId);
            }
            if (mDirtyUsers.remove(userId));
            mSettings.removeUserLPr(userId);
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
    public List<UserInfo> getUsers() {
        enforceSystemOrRoot("Only the system can query users");
        return sUserManager.getUsers();
    }

    @Override
    public UserInfo getUser(int userId) {
        enforceSystemOrRoot("Only the system can remove users");
        return sUserManager.getUser(userId);
    }

    @Override
    public void updateUserName(int userId, String name) {
        enforceSystemOrRoot("Only the system can rename users");
        sUserManager.updateUserName(userId, name);
    }

    @Override
    public void setPermissionEnforced(String permission, boolean enforced) {
        mContext.enforceCallingOrSelfPermission(GRANT_REVOKE_PERMISSIONS, null);
        if (READ_EXTERNAL_STORAGE.equals(permission)) {
            synchronized (mPackages) {
                if (mSettings.mReadExternalStorageEnforced == null
                        || mSettings.mReadExternalStorageEnforced != enforced) {
                    mSettings.mReadExternalStorageEnforced = enforced;
                    mSettings.writeLPr();

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
                }
            }
        } else {
            throw new IllegalArgumentException("No selective enforcement for " + permission);
        }
    }

    @Override
    public boolean isPermissionEnforced(String permission) {
        synchronized (mPackages) {
            return isPermissionEnforcedLocked(permission);
        }
    }

    private boolean isPermissionEnforcedLocked(String permission) {
        if (READ_EXTERNAL_STORAGE.equals(permission)) {
            if (mSettings.mReadExternalStorageEnforced != null) {
                return mSettings.mReadExternalStorageEnforced;
            } else {
                // if user hasn't defined, fall back to secure default
                return Secure.getInt(mContext.getContentResolver(),
                        Secure.READ_EXTERNAL_STORAGE_ENFORCED_DEFAULT, 0) != 0;
            }
        } else {
            return true;
        }
    }

    public boolean isStorageLow() {
        final long token = Binder.clearCallingIdentity();
        try {
            final DeviceStorageMonitorService dsm = (DeviceStorageMonitorService) ServiceManager
                    .getService(DeviceStorageMonitorService.SERVICE);
            return dsm.isMemoryLow();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
