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

package com.android.server.connectivity;

import static android.Manifest.permission.BIND_VPN_SERVICE;

import android.app.AppGlobals;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.BaseNetworkStateTracker;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.NetworkInfo.DetailedState;
import android.os.Binder;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemService;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.Credentials;
import android.security.KeyStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.Toast;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.R;
import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;
import com.android.internal.util.Preconditions;
import com.android.server.ConnectivityService.VpnCallback;
import com.android.server.net.BaseNetworkObserver;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import libcore.io.IoUtils;

/**
 * @hide
 */
public class Vpn extends BaseNetworkStateTracker {
    private static final String TAG = "Vpn";
    private static final boolean LOGD = true;
    
    // TODO: create separate trackers for each unique VPN to support
    // automated reconnection

    private final VpnCallback mCallback;

    private String mPackage = VpnConfig.LEGACY_VPN;
    private String mInterface;
    private Connection mConnection;
    private LegacyVpnRunner mLegacyVpnRunner;
    private PendingIntent mStatusIntent;
    private volatile boolean mEnableNotif = true;
    private volatile boolean mEnableTeardown = true;
    private final IConnectivityManager mConnService;
    private VpnConfig mConfig;

    /* list of users using this VPN. */
    @GuardedBy("this")
    private SparseBooleanArray mVpnUsers = null;
    private BroadcastReceiver mUserIntentReceiver = null;

    private final int mUserId;

    public Vpn(Context context, VpnCallback callback, INetworkManagementService netService,
            IConnectivityManager connService, int userId) {
        // TODO: create dedicated TYPE_VPN network type
        super(ConnectivityManager.TYPE_DUMMY);
        mContext = context;
        mCallback = callback;
        mConnService = connService;
        mUserId = userId;

        try {
            netService.registerObserver(mObserver);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Problem registering observer", e);
        }
        if (userId == UserHandle.USER_OWNER) {
            // Owner's VPN also needs to handle restricted users
            mUserIntentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE,
                            UserHandle.USER_NULL);
                    if (userId == UserHandle.USER_NULL) return;

                    if (Intent.ACTION_USER_ADDED.equals(action)) {
                        onUserAdded(userId);
                    } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                        onUserRemoved(userId);
                    }
                }
            };

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_USER_ADDED);
            intentFilter.addAction(Intent.ACTION_USER_REMOVED);
            mContext.registerReceiverAsUser(
                    mUserIntentReceiver, UserHandle.ALL, intentFilter, null, null);
        }
    }

    /**
     * Set if this object is responsible for showing its own notifications. When
     * {@code false}, notifications are handled externally by someone else.
     */
    public void setEnableNotifications(boolean enableNotif) {
        mEnableNotif = enableNotif;
    }

    /**
     * Set if this object is responsible for watching for {@link NetworkInfo}
     * teardown. When {@code false}, teardown is handled externally by someone
     * else.
     */
    public void setEnableTeardown(boolean enableTeardown) {
        mEnableTeardown = enableTeardown;
    }

    @Override
    protected void startMonitoringInternal() {
        // Ignored; events are sent through callbacks for now
    }

    @Override
    public boolean teardown() {
        // TODO: finish migration to unique tracker for each VPN
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean reconnect() {
        // TODO: finish migration to unique tracker for each VPN
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTcpBufferSizesPropName() {
        return PROP_TCP_BUFFER_UNKNOWN;
    }

    /**
     * Update current state, dispaching event to listeners.
     */
    private void updateState(DetailedState detailedState, String reason) {
        if (LOGD) Log.d(TAG, "setting state=" + detailedState + ", reason=" + reason);
        mNetworkInfo.setDetailedState(detailedState, reason, null);
        mCallback.onStateChanged(new NetworkInfo(mNetworkInfo));
    }

    /**
     * Prepare for a VPN application. This method is designed to solve
     * race conditions. It first compares the current prepared package
     * with {@code oldPackage}. If they are the same, the prepared
     * package is revoked and replaced with {@code newPackage}. If
     * {@code oldPackage} is {@code null}, the comparison is omitted.
     * If {@code newPackage} is the same package or {@code null}, the
     * revocation is omitted. This method returns {@code true} if the
     * operation is succeeded.
     *
     * Legacy VPN is handled specially since it is not a real package.
     * It uses {@link VpnConfig#LEGACY_VPN} as its package name, and
     * it can be revoked by itself.
     *
     * @param oldPackage The package name of the old VPN application.
     * @param newPackage The package name of the new VPN application.
     * @return true if the operation is succeeded.
     */
    public synchronized boolean prepare(String oldPackage, String newPackage) {
        // Return false if the package does not match.
        if (oldPackage != null && !oldPackage.equals(mPackage)) {
            return false;
        }

        // Return true if we do not need to revoke.
        if (newPackage == null ||
                (newPackage.equals(mPackage) && !newPackage.equals(VpnConfig.LEGACY_VPN))) {
            return true;
        }

        // Check if the caller is authorized.
        enforceControlPermission();

        // Reset the interface and hide the notification.
        if (mInterface != null) {
            final long token = Binder.clearCallingIdentity();
            try {
                mCallback.restore();
                final int size = mVpnUsers.size();
                final boolean forwardDns = (mConfig.dnsServers != null &&
                        mConfig.dnsServers.size() != 0);
                for (int i = 0; i < size; i++) {
                    int user = mVpnUsers.keyAt(i);
                    mCallback.clearUserForwarding(mInterface, user, forwardDns);
                    hideNotification(user);
                }

                mCallback.clearMarkedForwarding(mInterface);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            jniReset(mInterface);
            mInterface = null;
            mVpnUsers = null;
        }

        // Revoke the connection or stop LegacyVpnRunner.
        if (mConnection != null) {
            try {
                mConnection.mService.transact(IBinder.LAST_CALL_TRANSACTION,
                        Parcel.obtain(), null, IBinder.FLAG_ONEWAY);
            } catch (Exception e) {
                // ignore
            }
            mContext.unbindService(mConnection);
            mConnection = null;
        } else if (mLegacyVpnRunner != null) {
            mLegacyVpnRunner.exit();
            mLegacyVpnRunner = null;
        }

        Log.i(TAG, "Switched from " + mPackage + " to " + newPackage);
        mPackage = newPackage;
        mConfig = null;
        updateState(DetailedState.IDLE, "prepare");
        return true;
    }

    /**
     * Protect a socket from VPN rules by binding it to the main routing table.
     * The socket is NOT closed by this method.
     *
     * @param socket The socket to be bound.
     */
    public void protect(ParcelFileDescriptor socket) throws Exception {

        PackageManager pm = mContext.getPackageManager();
        int appUid = pm.getPackageUid(mPackage, mUserId);
        if (Binder.getCallingUid() != appUid) {
            throw new SecurityException("Unauthorized Caller");
        }
        // protect the socket from routing rules
        final long token = Binder.clearCallingIdentity();
        try {
            mCallback.protect(socket);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

    }

    /**
     * Establish a VPN network and return the file descriptor of the VPN
     * interface. This methods returns {@code null} if the application is
     * revoked or not prepared.
     *
     * @param config The parameters to configure the network.
     * @return The file descriptor of the VPN interface.
     */
    public synchronized ParcelFileDescriptor establish(VpnConfig config) {
        // Check if the caller is already prepared.
        UserManager mgr = UserManager.get(mContext);
        PackageManager pm = mContext.getPackageManager();
        ApplicationInfo app = null;
        try {
            app = AppGlobals.getPackageManager().getApplicationInfo(mPackage, 0, mUserId);
            if (Binder.getCallingUid() != app.uid) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        // Check if the service is properly declared.
        Intent intent = new Intent(VpnConfig.SERVICE_INTERFACE);
        intent.setClassName(mPackage, config.user);
        long token = Binder.clearCallingIdentity();
        try {
            // Restricted users are not allowed to create VPNs, they are tied to Owner
            UserInfo user = mgr.getUserInfo(mUserId);
            if (user.isRestricted()) {
                throw new SecurityException("Restricted users cannot establish VPNs");
            }

            ResolveInfo info = AppGlobals.getPackageManager().resolveService(intent,
                                                                        null, 0, mUserId);
            if (info == null) {
                throw new SecurityException("Cannot find " + config.user);
            }
            if (!BIND_VPN_SERVICE.equals(info.serviceInfo.permission)) {
                throw new SecurityException(config.user + " does not require " + BIND_VPN_SERVICE);
            }
        } catch (RemoteException e) {
                throw new SecurityException("Cannot find " + config.user);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        // Save the old config in case we need to go back.
        VpnConfig oldConfig = mConfig;
        String oldInterface = mInterface;
        Connection oldConnection = mConnection;
        SparseBooleanArray oldUsers = mVpnUsers;

        // Configure the interface. Abort if any of these steps fails.
        ParcelFileDescriptor tun = ParcelFileDescriptor.adoptFd(jniCreate(config.mtu));
        try {
            updateState(DetailedState.CONNECTING, "establish");
            String interfaze = jniGetName(tun.getFd());

            // TEMP use the old jni calls until there is support for netd address setting
            StringBuilder builder = new StringBuilder();
            for (LinkAddress address : config.addresses) {
                builder.append(" " + address);
            }
            if (jniSetAddresses(interfaze, builder.toString()) < 1) {
                throw new IllegalArgumentException("At least one address must be specified");
            }
            Connection connection = new Connection();
            if (!mContext.bindServiceAsUser(intent, connection, Context.BIND_AUTO_CREATE,
                        new UserHandle(mUserId))) {
                throw new IllegalStateException("Cannot bind " + config.user);
            }

            mConnection = connection;
            mInterface = interfaze;

            // Fill more values.
            config.user = mPackage;
            config.interfaze = mInterface;
            config.startTime = SystemClock.elapsedRealtime();
            mConfig = config;

            // Set up forwarding and DNS rules.
            mVpnUsers = new SparseBooleanArray();
            token = Binder.clearCallingIdentity();
            try {
                mCallback.setMarkedForwarding(mInterface);
                mCallback.setRoutes(mInterface, config.routes);
                mCallback.override(mInterface, config.dnsServers, config.searchDomains);
                addVpnUserLocked(mUserId);
                // If we are owner assign all Restricted Users to this VPN
                if (mUserId == UserHandle.USER_OWNER) {
                    for (UserInfo user : mgr.getUsers()) {
                        if (user.isRestricted()) {
                            try {
                                addVpnUserLocked(user.id);
                            } catch (Exception e) {
                                Log.wtf(TAG, "Failed to add user " + user.id + " to owner's VPN");
                            }
                        }
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }

            if (oldConnection != null) {
                mContext.unbindService(oldConnection);
            }
            if (oldInterface != null && !oldInterface.equals(interfaze)) {
                // Remove the old tun's user forwarding rules
                // The new tun's user rules have already been added so they will take over
                // as rules are deleted. This prevents data leakage as the rules are moved over.
                token = Binder.clearCallingIdentity();
                try {
                        final int size = oldUsers.size();
                        final boolean forwardDns = (oldConfig.dnsServers != null &&
                                oldConfig.dnsServers.size() != 0);
                        for (int i = 0; i < size; i++) {
                            int user = oldUsers.keyAt(i);
                            mCallback.clearUserForwarding(oldInterface, user, forwardDns);
                        }
                        mCallback.clearMarkedForwarding(oldInterface);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
                jniReset(oldInterface);
            }
        } catch (RuntimeException e) {
            updateState(DetailedState.FAILED, "establish");
            IoUtils.closeQuietly(tun);
            // make sure marked forwarding is cleared if it was set
            token = Binder.clearCallingIdentity();
            try {
                mCallback.clearMarkedForwarding(mInterface);
            } catch (Exception ingored) {
                // ignored
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            // restore old state
            mConfig = oldConfig;
            mConnection = oldConnection;
            mVpnUsers = oldUsers;
            mInterface = oldInterface;
            throw e;
        }
        Log.i(TAG, "Established by " + config.user + " on " + mInterface);

        // TODO: ensure that contract class eventually marks as connected
        updateState(DetailedState.AUTHENTICATING, "establish");
        return tun;
    }

    /**
     * Check if a given address is covered by the VPN's routing rules.
     */
    public boolean isAddressCovered(InetAddress address) {
        synchronized (Vpn.this) {
            if (!isRunningLocked()) {
                return false;
            }
            return RouteInfo.selectBestRoute(mConfig.routes, address) != null;
        }
    }

    private boolean isRunningLocked() {
        return mVpnUsers != null;
    }

    private void addVpnUserLocked(int user) {
        enforceControlPermission();

        if (!isRunningLocked()) {
            throw new IllegalStateException("VPN is not active");
        }

        final boolean forwardDns = (mConfig.dnsServers != null &&
                mConfig.dnsServers.size() != 0);

        // add the user
        mCallback.addUserForwarding(mInterface, user, forwardDns);
        mVpnUsers.put(user, true);

        // show the notification
        if (!mPackage.equals(VpnConfig.LEGACY_VPN)) {
            // Load everything for the user's notification
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo app = null;
            try {
                app = AppGlobals.getPackageManager().getApplicationInfo(mPackage, 0, mUserId);
            } catch (RemoteException e) {
                throw new IllegalStateException("Invalid application");
            }
            String label = app.loadLabel(pm).toString();
            // Load the icon and convert it into a bitmap.
            Drawable icon = app.loadIcon(pm);
            Bitmap bitmap = null;
            if (icon.getIntrinsicWidth() > 0 && icon.getIntrinsicHeight() > 0) {
                int width = mContext.getResources().getDimensionPixelSize(
                        android.R.dimen.notification_large_icon_width);
                int height = mContext.getResources().getDimensionPixelSize(
                        android.R.dimen.notification_large_icon_height);
                icon.setBounds(0, 0, width, height);
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bitmap);
                icon.draw(c);
                c.setBitmap(null);
            }
            showNotification(label, bitmap, user);
        } else {
            showNotification(null, null, user);
        }
    }

    private void removeVpnUserLocked(int user) {
            enforceControlPermission();

            if (!isRunningLocked()) {
                throw new IllegalStateException("VPN is not active");
            }
            final boolean forwardDns = (mConfig.dnsServers != null &&
                    mConfig.dnsServers.size() != 0);
            mCallback.clearUserForwarding(mInterface, user, forwardDns);
            mVpnUsers.delete(user);
            hideNotification(user);
    }

    private void onUserAdded(int userId) {
        // If the user is restricted tie them to the owner's VPN
        synchronized(Vpn.this) {
            UserManager mgr = UserManager.get(mContext);
            UserInfo user = mgr.getUserInfo(userId);
            if (user.isRestricted()) {
                try {
                    addVpnUserLocked(userId);
                } catch (Exception e) {
                    Log.wtf(TAG, "Failed to add restricted user to owner", e);
                }
            }
        }
    }

    private void onUserRemoved(int userId) {
        // clean up if restricted
        synchronized(Vpn.this) {
            UserManager mgr = UserManager.get(mContext);
            UserInfo user = mgr.getUserInfo(userId);
            if (user.isRestricted()) {
                try {
                    removeVpnUserLocked(userId);
                } catch (Exception e) {
                    Log.wtf(TAG, "Failed to remove restricted user to owner", e);
                }
            }
        }
    }

    /**
     * Return the configuration of the currently running VPN.
     */
    public VpnConfig getVpnConfig() {
        enforceControlPermission();
        return mConfig;
    }

    @Deprecated
    public synchronized void interfaceStatusChanged(String iface, boolean up) {
        try {
            mObserver.interfaceStatusChanged(iface, up);
        } catch (RemoteException e) {
            // ignored; target is local
        }
    }

    private INetworkManagementEventObserver mObserver = new BaseNetworkObserver() {
        @Override
        public void interfaceStatusChanged(String interfaze, boolean up) {
            synchronized (Vpn.this) {
                if (!up && mLegacyVpnRunner != null) {
                    mLegacyVpnRunner.check(interfaze);
                }
            }
        }

        @Override
        public void interfaceRemoved(String interfaze) {
            synchronized (Vpn.this) {
                if (interfaze.equals(mInterface) && jniCheck(interfaze) == 0) {
                    final long token = Binder.clearCallingIdentity();
                    try {
                        final int size = mVpnUsers.size();
                        final boolean forwardDns = (mConfig.dnsServers != null &&
                                mConfig.dnsServers.size() != 0);
                        for (int i = 0; i < size; i++) {
                            int user = mVpnUsers.keyAt(i);
                            mCallback.clearUserForwarding(mInterface, user, forwardDns);
                            hideNotification(user);
                        }
                        mVpnUsers = null;
                        mCallback.clearMarkedForwarding(mInterface);

                        mCallback.restore();
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                    mInterface = null;
                    if (mConnection != null) {
                        mContext.unbindService(mConnection);
                        mConnection = null;
                        updateState(DetailedState.DISCONNECTED, "interfaceRemoved");
                    } else if (mLegacyVpnRunner != null) {
                        mLegacyVpnRunner.exit();
                        mLegacyVpnRunner = null;
                    }
                }
            }
        }
    };

    private void enforceControlPermission() {
        // System user is allowed to control VPN.
        if (Binder.getCallingUid() == Process.SYSTEM_UID) {
            return;
        }
        int appId = UserHandle.getAppId(Binder.getCallingUid());
        final long token = Binder.clearCallingIdentity();
        try {
            // System dialogs are also allowed to control VPN.
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo app = pm.getApplicationInfo(VpnConfig.DIALOGS_PACKAGE, 0);
            if (appId == app.uid) {
                return;
            }
        } catch (Exception e) {
            // ignore
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        throw new SecurityException("Unauthorized Caller");
    }

    private class Connection implements ServiceConnection {
        private IBinder mService;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    }

    private void showNotification(String label, Bitmap icon, int user) {
        if (!mEnableNotif) return;
        mStatusIntent = VpnConfig.getIntentForStatusPanel(mContext);

        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm != null) {
            String title = (label == null) ? mContext.getString(R.string.vpn_title) :
                    mContext.getString(R.string.vpn_title_long, label);
            String text = (mConfig.session == null) ? mContext.getString(R.string.vpn_text) :
                    mContext.getString(R.string.vpn_text_long, mConfig.session);

            Notification notification = new Notification.Builder(mContext)
                    .setSmallIcon(R.drawable.vpn_connected)
                    .setLargeIcon(icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(mStatusIntent)
                    .setDefaults(0)
                    .setOngoing(true)
                    .build();
            nm.notifyAsUser(null, R.drawable.vpn_connected, notification, new UserHandle(user));
        }
    }

    private void hideNotification(int user) {
        if (!mEnableNotif) return;
        mStatusIntent = null;

        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm != null) {
            nm.cancelAsUser(null, R.drawable.vpn_connected, new UserHandle(user));
        }
    }

    private native int jniCreate(int mtu);
    private native String jniGetName(int tun);
    private native int jniSetAddresses(String interfaze, String addresses);
    private native int jniSetRoutes(String interfaze, String routes);
    private native void jniReset(String interfaze);
    private native int jniCheck(String interfaze);

    private static RouteInfo findIPv4DefaultRoute(LinkProperties prop) {
        for (RouteInfo route : prop.getAllRoutes()) {
            // Currently legacy VPN only works on IPv4.
            if (route.isDefaultRoute() && route.getGateway() instanceof Inet4Address) {
                return route;
            }
        }

        throw new IllegalStateException("Unable to find IPv4 default gateway");
    }

    /**
     * Start legacy VPN, controlling native daemons as needed. Creates a
     * secondary thread to perform connection work, returning quickly.
     */
    public void startLegacyVpn(VpnProfile profile, KeyStore keyStore, LinkProperties egress) {
        enforceControlPermission();
        if (!keyStore.isUnlocked()) {
            throw new IllegalStateException("KeyStore isn't unlocked");
        }

        final RouteInfo ipv4DefaultRoute = findIPv4DefaultRoute(egress);
        final String gateway = ipv4DefaultRoute.getGateway().getHostAddress();
        final String iface = ipv4DefaultRoute.getInterface();

        // Load certificates.
        String privateKey = "";
        String userCert = "";
        String caCert = "";
        String serverCert = "";
        if (!profile.ipsecUserCert.isEmpty()) {
            privateKey = Credentials.USER_PRIVATE_KEY + profile.ipsecUserCert;
            byte[] value = keyStore.get(Credentials.USER_CERTIFICATE + profile.ipsecUserCert);
            userCert = (value == null) ? null : new String(value, StandardCharsets.UTF_8);
        }
        if (!profile.ipsecCaCert.isEmpty()) {
            byte[] value = keyStore.get(Credentials.CA_CERTIFICATE + profile.ipsecCaCert);
            caCert = (value == null) ? null : new String(value, StandardCharsets.UTF_8);
        }
        if (!profile.ipsecServerCert.isEmpty()) {
            byte[] value = keyStore.get(Credentials.USER_CERTIFICATE + profile.ipsecServerCert);
            serverCert = (value == null) ? null : new String(value, StandardCharsets.UTF_8);
        }
        if (privateKey == null || userCert == null || caCert == null || serverCert == null) {
            throw new IllegalStateException("Cannot load credentials");
        }

        // Prepare arguments for racoon.
        String[] racoon = null;
        switch (profile.type) {
            case VpnProfile.TYPE_L2TP_IPSEC_PSK:
                racoon = new String[] {
                    iface, profile.server, "udppsk", profile.ipsecIdentifier,
                    profile.ipsecSecret, "1701",
                };
                break;
            case VpnProfile.TYPE_L2TP_IPSEC_RSA:
                racoon = new String[] {
                    iface, profile.server, "udprsa", privateKey, userCert,
                    caCert, serverCert, "1701",
                };
                break;
            case VpnProfile.TYPE_IPSEC_XAUTH_PSK:
                racoon = new String[] {
                    iface, profile.server, "xauthpsk", profile.ipsecIdentifier,
                    profile.ipsecSecret, profile.username, profile.password, "", gateway,
                };
                break;
            case VpnProfile.TYPE_IPSEC_XAUTH_RSA:
                racoon = new String[] {
                    iface, profile.server, "xauthrsa", privateKey, userCert,
                    caCert, serverCert, profile.username, profile.password, "", gateway,
                };
                break;
            case VpnProfile.TYPE_IPSEC_HYBRID_RSA:
                racoon = new String[] {
                    iface, profile.server, "hybridrsa",
                    caCert, serverCert, profile.username, profile.password, "", gateway,
                };
                break;
        }

        // Prepare arguments for mtpd.
        String[] mtpd = null;
        switch (profile.type) {
            case VpnProfile.TYPE_PPTP:
                mtpd = new String[] {
                    iface, "pptp", profile.server, "1723",
                    "name", profile.username, "password", profile.password,
                    "linkname", "vpn", "refuse-eap", "nodefaultroute",
                    "usepeerdns", "idle", "1800", "mtu", "1400", "mru", "1400",
                    (profile.mppe ? "+mppe" : "nomppe"),
                };
                break;
            case VpnProfile.TYPE_L2TP_IPSEC_PSK:
            case VpnProfile.TYPE_L2TP_IPSEC_RSA:
                mtpd = new String[] {
                    iface, "l2tp", profile.server, "1701", profile.l2tpSecret,
                    "name", profile.username, "password", profile.password,
                    "linkname", "vpn", "refuse-eap", "nodefaultroute",
                    "usepeerdns", "idle", "1800", "mtu", "1400", "mru", "1400",
                };
                break;
        }

        VpnConfig config = new VpnConfig();
        config.legacy = true;
        config.user = profile.key;
        config.interfaze = iface;
        config.session = profile.name;

        config.addLegacyRoutes(profile.routes);
        if (!profile.dnsServers.isEmpty()) {
            config.dnsServers = Arrays.asList(profile.dnsServers.split(" +"));
        }
        if (!profile.searchDomains.isEmpty()) {
            config.searchDomains = Arrays.asList(profile.searchDomains.split(" +"));
        }
        startLegacyVpn(config, racoon, mtpd);
    }

    private synchronized void startLegacyVpn(VpnConfig config, String[] racoon, String[] mtpd) {
        stopLegacyVpn();

        // Prepare for the new request. This also checks the caller.
        prepare(null, VpnConfig.LEGACY_VPN);
        updateState(DetailedState.CONNECTING, "startLegacyVpn");

        // Start a new LegacyVpnRunner and we are done!
        mLegacyVpnRunner = new LegacyVpnRunner(config, racoon, mtpd);
        mLegacyVpnRunner.start();
    }

    public synchronized void stopLegacyVpn() {
        if (mLegacyVpnRunner != null) {
            mLegacyVpnRunner.exit();
            mLegacyVpnRunner = null;

            synchronized (LegacyVpnRunner.TAG) {
                // wait for old thread to completely finish before spinning up
                // new instance, otherwise state updates can be out of order.
            }
        }
    }

    /**
     * Return the information of the current ongoing legacy VPN.
     */
    public synchronized LegacyVpnInfo getLegacyVpnInfo() {
        // Check if the caller is authorized.
        enforceControlPermission();
        if (mLegacyVpnRunner == null) return null;

        final LegacyVpnInfo info = new LegacyVpnInfo();
        info.key = mConfig.user;
        info.state = LegacyVpnInfo.stateFromNetworkInfo(mNetworkInfo);
        if (mNetworkInfo.isConnected()) {
            info.intent = mStatusIntent;
        }
        return info;
    }

    public VpnConfig getLegacyVpnConfig() {
        if (mLegacyVpnRunner != null) {
            return mConfig;
        } else {
            return null;
        }
    }

    /**
     * Bringing up a VPN connection takes time, and that is all this thread
     * does. Here we have plenty of time. The only thing we need to take
     * care of is responding to interruptions as soon as possible. Otherwise
     * requests will be piled up. This can be done in a Handler as a state
     * machine, but it is much easier to read in the current form.
     */
    private class LegacyVpnRunner extends Thread {
        private static final String TAG = "LegacyVpnRunner";

        private final String[] mDaemons;
        private final String[][] mArguments;
        private final LocalSocket[] mSockets;
        private final String mOuterInterface;
        private final AtomicInteger mOuterConnection =
                new AtomicInteger(ConnectivityManager.TYPE_NONE);

        private long mTimer = -1;

        /**
         * Watch for the outer connection (passing in the constructor) going away.
         */
        private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!mEnableTeardown) return;

                if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    if (intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE,
                            ConnectivityManager.TYPE_NONE) == mOuterConnection.get()) {
                        NetworkInfo info = (NetworkInfo)intent.getExtra(
                                ConnectivityManager.EXTRA_NETWORK_INFO);
                        if (info != null && !info.isConnectedOrConnecting()) {
                            try {
                                mObserver.interfaceStatusChanged(mOuterInterface, false);
                            } catch (RemoteException e) {}
                        }
                    }
                }
            }
        };

        public LegacyVpnRunner(VpnConfig config, String[] racoon, String[] mtpd) {
            super(TAG);
            mConfig = config;
            mDaemons = new String[] {"racoon", "mtpd"};
            // TODO: clear arguments from memory once launched
            mArguments = new String[][] {racoon, mtpd};
            mSockets = new LocalSocket[mDaemons.length];

            // This is the interface which VPN is running on,
            // mConfig.interfaze will change to point to OUR
            // internal interface soon. TODO - add inner/outer to mconfig
            // TODO - we have a race - if the outer iface goes away/disconnects before we hit this
            // we will leave the VPN up.  We should check that it's still there/connected after
            // registering
            mOuterInterface = mConfig.interfaze;

            try {
                mOuterConnection.set(
                        mConnService.findConnectionTypeForIface(mOuterInterface));
            } catch (Exception e) {
                mOuterConnection.set(ConnectivityManager.TYPE_NONE);
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mBroadcastReceiver, filter);
        }

        public void check(String interfaze) {
            if (interfaze.equals(mOuterInterface)) {
                Log.i(TAG, "Legacy VPN is going down with " + interfaze);
                exit();
            }
        }

        public void exit() {
            // We assume that everything is reset after stopping the daemons.
            interrupt();
            for (LocalSocket socket : mSockets) {
                IoUtils.closeQuietly(socket);
            }
            updateState(DetailedState.DISCONNECTED, "exit");
            try {
                mContext.unregisterReceiver(mBroadcastReceiver);
            } catch (IllegalArgumentException e) {}
        }

        @Override
        public void run() {
            // Wait for the previous thread since it has been interrupted.
            Log.v(TAG, "Waiting");
            synchronized (TAG) {
                Log.v(TAG, "Executing");
                execute();
                monitorDaemons();
            }
        }

        private void checkpoint(boolean yield) throws InterruptedException {
            long now = SystemClock.elapsedRealtime();
            if (mTimer == -1) {
                mTimer = now;
                Thread.sleep(1);
            } else if (now - mTimer <= 60000) {
                Thread.sleep(yield ? 200 : 1);
            } else {
                updateState(DetailedState.FAILED, "checkpoint");
                throw new IllegalStateException("Time is up");
            }
        }

        private void execute() {
            // Catch all exceptions so we can clean up few things.
            boolean initFinished = false;
            try {
                // Initialize the timer.
                checkpoint(false);

                // Wait for the daemons to stop.
                for (String daemon : mDaemons) {
                    while (!SystemService.isStopped(daemon)) {
                        checkpoint(true);
                    }
                }

                // Clear the previous state.
                File state = new File("/data/misc/vpn/state");
                state.delete();
                if (state.exists()) {
                    throw new IllegalStateException("Cannot delete the state");
                }
                new File("/data/misc/vpn/abort").delete();
                initFinished = true;

                // Check if we need to restart any of the daemons.
                boolean restart = false;
                for (String[] arguments : mArguments) {
                    restart = restart || (arguments != null);
                }
                if (!restart) {
                    updateState(DetailedState.DISCONNECTED, "execute");
                    return;
                }
                updateState(DetailedState.CONNECTING, "execute");

                // Start the daemon with arguments.
                for (int i = 0; i < mDaemons.length; ++i) {
                    String[] arguments = mArguments[i];
                    if (arguments == null) {
                        continue;
                    }

                    // Start the daemon.
                    String daemon = mDaemons[i];
                    SystemService.start(daemon);

                    // Wait for the daemon to start.
                    while (!SystemService.isRunning(daemon)) {
                        checkpoint(true);
                    }

                    // Create the control socket.
                    mSockets[i] = new LocalSocket();
                    LocalSocketAddress address = new LocalSocketAddress(
                            daemon, LocalSocketAddress.Namespace.RESERVED);

                    // Wait for the socket to connect.
                    while (true) {
                        try {
                            mSockets[i].connect(address);
                            break;
                        } catch (Exception e) {
                            // ignore
                        }
                        checkpoint(true);
                    }
                    mSockets[i].setSoTimeout(500);

                    // Send over the arguments.
                    OutputStream out = mSockets[i].getOutputStream();
                    for (String argument : arguments) {
                        byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
                        if (bytes.length >= 0xFFFF) {
                            throw new IllegalArgumentException("Argument is too large");
                        }
                        out.write(bytes.length >> 8);
                        out.write(bytes.length);
                        out.write(bytes);
                        checkpoint(false);
                    }
                    out.write(0xFF);
                    out.write(0xFF);
                    out.flush();

                    // Wait for End-of-File.
                    InputStream in = mSockets[i].getInputStream();
                    while (true) {
                        try {
                            if (in.read() == -1) {
                                break;
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                        checkpoint(true);
                    }
                }

                // Wait for the daemons to create the new state.
                while (!state.exists()) {
                    // Check if a running daemon is dead.
                    for (int i = 0; i < mDaemons.length; ++i) {
                        String daemon = mDaemons[i];
                        if (mArguments[i] != null && !SystemService.isRunning(daemon)) {
                            throw new IllegalStateException(daemon + " is dead");
                        }
                    }
                    checkpoint(true);
                }

                // Now we are connected. Read and parse the new state.
                String[] parameters = FileUtils.readTextFile(state, 0, null).split("\n", -1);
                if (parameters.length != 6) {
                    throw new IllegalStateException("Cannot parse the state");
                }

                // Set the interface and the addresses in the config.
                mConfig.interfaze = parameters[0].trim();

                mConfig.addLegacyAddresses(parameters[1]);
                // Set the routes if they are not set in the config.
                if (mConfig.routes == null || mConfig.routes.isEmpty()) {
                    mConfig.addLegacyRoutes(parameters[2]);
                }

                // Set the DNS servers if they are not set in the config.
                if (mConfig.dnsServers == null || mConfig.dnsServers.size() == 0) {
                    String dnsServers = parameters[3].trim();
                    if (!dnsServers.isEmpty()) {
                        mConfig.dnsServers = Arrays.asList(dnsServers.split(" "));
                    }
                }

                // Set the search domains if they are not set in the config.
                if (mConfig.searchDomains == null || mConfig.searchDomains.size() == 0) {
                    String searchDomains = parameters[4].trim();
                    if (!searchDomains.isEmpty()) {
                        mConfig.searchDomains = Arrays.asList(searchDomains.split(" "));
                    }
                }

                // Set the routes.
                long token = Binder.clearCallingIdentity();
                try {
                    mCallback.setMarkedForwarding(mConfig.interfaze);
                    mCallback.setRoutes(mConfig.interfaze, mConfig.routes);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }

                // Here is the last step and it must be done synchronously.
                synchronized (Vpn.this) {
                    // Set the start time
                    mConfig.startTime = SystemClock.elapsedRealtime();

                    // Check if the thread is interrupted while we are waiting.
                    checkpoint(false);

                    // Check if the interface is gone while we are waiting.
                    if (jniCheck(mConfig.interfaze) == 0) {
                        throw new IllegalStateException(mConfig.interfaze + " is gone");
                    }

                    // Now INetworkManagementEventObserver is watching our back.
                    mInterface = mConfig.interfaze;
                    mVpnUsers = new SparseBooleanArray();

                    token = Binder.clearCallingIdentity();
                    try {
                        mCallback.override(mInterface, mConfig.dnsServers, mConfig.searchDomains);
                        addVpnUserLocked(mUserId);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }

                    // Assign all restircted users to this VPN
                    // (Legacy VPNs are Owner only)
                    UserManager mgr = UserManager.get(mContext);
                    token = Binder.clearCallingIdentity();
                    try {
                        for (UserInfo user : mgr.getUsers()) {
                            if (user.isRestricted()) {
                                try {
                                    addVpnUserLocked(user.id);
                                } catch (Exception e) {
                                    Log.wtf(TAG, "Failed to add user " + user.id
                                            + " to owner's VPN");
                                }
                            }
                        }
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                    Log.i(TAG, "Connected!");
                    updateState(DetailedState.CONNECTED, "execute");
                }
            } catch (Exception e) {
                Log.i(TAG, "Aborting", e);
                // make sure the routing is cleared
                try {
                    mCallback.clearMarkedForwarding(mConfig.interfaze);
                } catch (Exception ignored) {
                }
                exit();
            } finally {
                // Kill the daemons if they fail to stop.
                if (!initFinished) {
                    for (String daemon : mDaemons) {
                        SystemService.stop(daemon);
                    }
                }

                // Do not leave an unstable state.
                if (!initFinished || mNetworkInfo.getDetailedState() == DetailedState.CONNECTING) {
                    updateState(DetailedState.FAILED, "execute");
                }
            }
        }

        /**
         * Monitor the daemons we started, moving to disconnected state if the
         * underlying services fail.
         */
        private void monitorDaemons() {
            if (!mNetworkInfo.isConnected()) {
                return;
            }

            try {
                while (true) {
                    Thread.sleep(2000);
                    for (int i = 0; i < mDaemons.length; i++) {
                        if (mArguments[i] != null && SystemService.isStopped(mDaemons[i])) {
                            return;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "interrupted during monitorDaemons(); stopping services");
            } finally {
                for (String daemon : mDaemons) {
                    SystemService.stop(daemon);
                }

                updateState(DetailedState.DISCONNECTED, "babysit");
            }
        }
    }
}
