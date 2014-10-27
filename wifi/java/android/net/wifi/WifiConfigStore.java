/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.net.wifi;
import com.android.internal.R;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.NetworkInfo.DetailedState;
import android.net.ProxyProperties;
import android.net.RouteInfo;
import android.net.wifi.WifiConfiguration.IpAssignment;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiConfiguration.ProxySettings;
import android.net.wifi.WifiConfiguration.Status;
import android.net.wifi.NetworkUpdateResult;
import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Message;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.provider.Settings;
import android.security.KeyStore;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class provides the API to manage configured
 * wifi networks. The API is not thread safe is being
 * used only from WifiStateMachine.
 *
 * It deals with the following
 * - Add/update/remove a WifiConfiguration
 *   The configuration contains two types of information.
 *     = IP and proxy configuration that is handled by WifiConfigStore and
 *       is saved to disk on any change.
 *
 *       The format of configuration file is as follows:
 *       <version>
 *       <netA_key1><netA_value1><netA_key2><netA_value2>...<EOS>
 *       <netB_key1><netB_value1><netB_key2><netB_value2>...<EOS>
 *       ..
 *
 *       (key, value) pairs for a given network are grouped together and can
 *       be in any order. A EOS at the end of a set of (key, value) pairs
 *       indicates that the next set of (key, value) pairs are for a new
 *       network. A network is identified by a unique ID_KEY. If there is no
 *       ID_KEY in the (key, value) pairs, the data is discarded.
 *
 *       An invalid version on read would result in discarding the contents of
 *       the file. On the next write, the latest version is written to file.
 *
 *       Any failures during read or write to the configuration file are ignored
 *       without reporting to the user since the likelihood of these errors are
 *       low and the impact on connectivity is low.
 *
 *     = SSID & security details that is pushed to the supplicant.
 *       supplicant saves these details to the disk on calling
 *       saveConfigCommand().
 *
 *       We have two kinds of APIs exposed:
 *        > public API calls that provide fine grained control
 *          - enableNetwork, disableNetwork, addOrUpdateNetwork(),
 *          removeNetwork(). For these calls, the config is not persisted
 *          to the disk. (TODO: deprecate these calls in WifiManager)
 *        > The new API calls - selectNetwork(), saveNetwork() & forgetNetwork().
 *          These calls persist the supplicant config to disk.
 *
 * - Maintain a list of configured networks for quick access
 *
 */
class WifiConfigStore {

    private Context mContext;
    private static final String TAG = "WifiConfigStore";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    private static final String SUPPLICANT_CONFIG_FILE = "/data/misc/wifi/wpa_supplicant.conf";

    /* configured networks with network id as the key */
    private HashMap<Integer, WifiConfiguration> mConfiguredNetworks =
            new HashMap<Integer, WifiConfiguration>();

    /* A network id is a unique identifier for a network configured in the
     * supplicant. Network ids are generated when the supplicant reads
     * the configuration file at start and can thus change for networks.
     * We store the IP configuration for networks along with a unique id
     * that is generated from SSID and security type of the network. A mapping
     * from the generated unique id to network id of the network is needed to
     * map supplicant config to IP configuration. */
    private HashMap<Integer, Integer> mNetworkIds =
            new HashMap<Integer, Integer>();

    /* Tracks the highest priority of configured networks */
    private int mLastPriority = -1;

    private static final String ipConfigFile = Environment.getDataDirectory() +
            "/misc/wifi/ipconfig.txt";

    private static final int IPCONFIG_FILE_VERSION = 2;

    /* IP and proxy configuration keys */
    private static final String ID_KEY = "id";
    private static final String IP_ASSIGNMENT_KEY = "ipAssignment";
    private static final String LINK_ADDRESS_KEY = "linkAddress";
    private static final String GATEWAY_KEY = "gateway";
    private static final String DNS_KEY = "dns";
    private static final String PROXY_SETTINGS_KEY = "proxySettings";
    private static final String PROXY_HOST_KEY = "proxyHost";
    private static final String PROXY_PORT_KEY = "proxyPort";
    private static final String PROXY_PAC_FILE = "proxyPac";
    private static final String EXCLUSION_LIST_KEY = "exclusionList";
    private static final String EOS = "eos";

    private final LocalLog mLocalLog;
    private final WpaConfigFileObserver mFileObserver;

    private WifiNative mWifiNative;
    private final KeyStore mKeyStore = KeyStore.getInstance();

    private static final int WIFI_AUTO_CONNECT_TYPE_AUTO = 0;
    private static final int DATA_TO_WIFI_CONNECT_TYPE_AUTO = 0;

    WifiConfigStore(Context c, WifiNative wn) {
        mContext = c;
        mWifiNative = wn;

        if (VDBG) {
            mLocalLog = mWifiNative.getLocalLog();
            mFileObserver = new WpaConfigFileObserver();
            mFileObserver.startWatching();
        } else {
            mLocalLog = null;
            mFileObserver = null;
        }
    }

    class WpaConfigFileObserver extends FileObserver {

        public WpaConfigFileObserver() {
            super(SUPPLICANT_CONFIG_FILE, CLOSE_WRITE);
        }

        @Override
        public void onEvent(int event, String path) {
            if (event == CLOSE_WRITE) {
                File file = new File(SUPPLICANT_CONFIG_FILE);
                if (VDBG) localLog("wpa_supplicant.conf changed; new size = " + file.length());
            }
        }
    }


    /**
     * Fetch the list of configured networks
     * and enable all stored networks in supplicant.
     */
    void loadAndEnableAllNetworks() {
        if (DBG) log("Loading config and enabling all networks");
        loadConfiguredNetworks();
        enableAllNetworks();
    }

    /**
     * Fetch the list of currently configured networks
     * @return List of networks
     */
    List<WifiConfiguration> getConfiguredNetworks() {
        List<WifiConfiguration> networks = new ArrayList<WifiConfiguration>();
        for(WifiConfiguration config : mConfiguredNetworks.values()) {
            networks.add(new WifiConfiguration(config));
        }
        return networks;
    }

    boolean isWifiAuto() {
        int autoConnectPolicy = Settings.System
                .getInt(mContext.getContentResolver(), Settings.System.WIFI_AUTO_CONNECT_TYPE,
                        WIFI_AUTO_CONNECT_TYPE_AUTO);
        return (autoConnectPolicy == WIFI_AUTO_CONNECT_TYPE_AUTO);
    }

    boolean isDataToWifiAuto() {
        int gsmToWifiPolicy = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DATA_TO_WIFI_CONNECT_TYPE, DATA_TO_WIFI_CONNECT_TYPE_AUTO);
        return (gsmToWifiPolicy == DATA_TO_WIFI_CONNECT_TYPE_AUTO);
    }

    int existActiveNetwork() {
        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null)
            return -1;
        return info.getType();
    }

    boolean isWifiAutoConn() {
        // If no active network(info == null) and wifi connection type is auto
        // connect, auto connect to wifi.
        // If the active network type is wifi, wifi connection type is auto
        // auto connect to wifi. This allows wifi auto-reconnect when
        // wifi disconnected for unexpected reason.
        return isWifiAuto() && ((existActiveNetwork() == -1)
                                  || (existActiveNetwork()
                                       == ConnectivityManager.TYPE_WIFI));
    }

    boolean isDataToWifiAutoConn() {
        // If the active network type is mobile or wimax, wifi connection
        // type is auto connect and GSM to WLAN connection type is auto
        // connect, auto connect to wifi.
        return isWifiAuto()
                && (((existActiveNetwork()
                    == ConnectivityManager.TYPE_MOBILE) ||
                     (existActiveNetwork()
                    == ConnectivityManager.TYPE_WIMAX)) &&
                isDataToWifiAuto());

    }

    boolean shouldAutoConnect() {
        if(isWifiAutoConn() || isDataToWifiAutoConn()){
            Log.d(TAG, " wifi or gsm to wlan conn type is auto , should auto connect");
            return true;
        }
        Log.d(TAG, "Shouldn't auto connect");
        return false;
    }

    /**
     * enable all networks and save config. This will be a no-op if the list
     * of configured networks indicates all networks as being enabled
     */
    void enableAllNetworks() {
        if (mContext.getResources().getBoolean(R.bool.wifi_autocon)
                && !shouldAutoConnect()) {
            return;
        }

        boolean networkEnabledStateChanged = false;
        for(WifiConfiguration config : mConfiguredNetworks.values()) {
            if(config != null && config.status == Status.DISABLED) {
                if(mWifiNative.enableNetwork(config.networkId, false)) {
                    networkEnabledStateChanged = true;
                    config.status = Status.ENABLED;
                } else {
                    loge("Enable network failed on " + config.networkId);
                }
            }
        }

        if (networkEnabledStateChanged) {
            mWifiNative.saveConfig();
            sendConfiguredNetworksChangedBroadcast();
        }
    }


    /**
     * Selects the specified network for connection. This involves
     * updating the priority of all the networks and enabling the given
     * network while disabling others.
     *
     * Selecting a network will leave the other networks disabled and
     * a call to enableAllNetworks() needs to be issued upon a connection
     * or a failure event from supplicant
     *
     * @param netId network to select for connection
     * @return false if the network id is invalid
     */
    boolean selectNetwork(int netId) {
        if (VDBG) localLog("selectNetwork", netId);
        if (netId == INVALID_NETWORK_ID) return false;

        // Reset the priority of each network at start or if it goes too high.
        if (mLastPriority == -1 || mLastPriority > 1000000) {
            for(WifiConfiguration config : mConfiguredNetworks.values()) {
                if (config.networkId != INVALID_NETWORK_ID) {
                    config.priority = 0;
                    addOrUpdateNetworkNative(config);
                }
            }
            mLastPriority = 0;
        }

        // Set to the highest priority and save the configuration.
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = netId;
        config.priority = ++mLastPriority;

        addOrUpdateNetworkNative(config);
        mWifiNative.saveConfig();

        /* Enable the given network while disabling all other networks */
        enableNetworkWithoutBroadcast(netId, true);

       /* Avoid saving the config & sending a broadcast to prevent settings
        * from displaying a disabled list of networks */
        return true;
    }

    /**
     * Add/update the specified configuration and save config
     *
     * @param config WifiConfiguration to be saved
     * @return network update result
     */
    NetworkUpdateResult saveNetwork(WifiConfiguration config) {
        if (VDBG) localLog("saveNetwork", config.networkId);
        // A new network cannot have null SSID
        if (config == null || (config.networkId == INVALID_NETWORK_ID &&
                config.SSID == null)) {
            return new NetworkUpdateResult(INVALID_NETWORK_ID);
        }

        boolean newNetwork = (config.networkId == INVALID_NETWORK_ID);
        NetworkUpdateResult result = addOrUpdateNetworkNative(config);
        int netId = result.getNetworkId();
        /* enable a new network */
        if (newNetwork && netId != INVALID_NETWORK_ID) {
            mWifiNative.enableNetwork(netId, false);
            mConfiguredNetworks.get(netId).status = Status.ENABLED;
        }
        mWifiNative.saveConfig();
        sendConfiguredNetworksChangedBroadcast(config, result.isNewNetwork() ?
                WifiManager.CHANGE_REASON_ADDED : WifiManager.CHANGE_REASON_CONFIG_CHANGE);
        return result;
    }

    void updateStatus(int netId, DetailedState state) {
        if (netId != INVALID_NETWORK_ID) {
            WifiConfiguration config = mConfiguredNetworks.get(netId);
            if (config == null) return;
            switch (state) {
                case CONNECTED:
                    config.status = Status.CURRENT;
                    break;
                case DISCONNECTED:
                    //If network is already disabled, keep the status
                    if (config.status == Status.CURRENT) {
                        config.status = Status.ENABLED;
                    }
                    break;
                default:
                    //do nothing, retain the existing state
                    break;
            }
        }
    }

    /**
     * Forget the specified network and save config
     *
     * @param netId network to forget
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean forgetNetwork(int netId) {
        if (VDBG) localLog("forgetNetwork", netId);
        if (mWifiNative.removeNetwork(netId)) {
            if (mContext.getResources().getBoolean(R.bool.wifi_autocon)
                && !shouldAutoConnect()) {
                // do not enable networks to avoid auto connect
            } else {
                for(WifiConfiguration config : mConfiguredNetworks.values()) {
                    if(config != null && config.status == Status.DISABLED) {
                        if(mWifiNative.enableNetwork(config.networkId, false)) {
                            config.status = Status.ENABLED;
                        } else {
                            loge("Enable network failed on " + config.networkId);
                        }
                    }
                }
            }
            mWifiNative.saveConfig();
            removeConfigAndSendBroadcastIfNeeded(netId);
            return true;
        } else {
            loge("Failed to remove network " + netId);
            return false;
        }
    }

    /**
     * Add/update a network. Note that there is no saveConfig operation.
     * This function is retained for compatibility with the public
     * API. The more powerful saveNetwork() is used by the
     * state machine
     *
     * @param config wifi configuration to add/update
     * @return network Id
     */
    int addOrUpdateNetwork(WifiConfiguration config) {
        if (VDBG) localLog("addOrUpdateNetwork", config.networkId);
        NetworkUpdateResult result = addOrUpdateNetworkNative(config);
        if (result.getNetworkId() != WifiConfiguration.INVALID_NETWORK_ID) {
            sendConfiguredNetworksChangedBroadcast(mConfiguredNetworks.get(result.getNetworkId()),
                    result.isNewNetwork ? WifiManager.CHANGE_REASON_ADDED :
                            WifiManager.CHANGE_REASON_CONFIG_CHANGE);
        }
        return result.getNetworkId();
    }

    /**
     * Remove a network. Note that there is no saveConfig operation.
     * This function is retained for compatibility with the public
     * API. The more powerful forgetNetwork() is used by the
     * state machine for network removal
     *
     * @param netId network to be removed
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean removeNetwork(int netId) {
        if (VDBG) localLog("removeNetwork", netId);
        boolean ret = mWifiNative.removeNetwork(netId);
        if (ret) {
            removeConfigAndSendBroadcastIfNeeded(netId);
        }
        return ret;
    }

    private void removeConfigAndSendBroadcastIfNeeded(int netId) {
        WifiConfiguration config = mConfiguredNetworks.get(netId);
        if (config != null) {
            // Remove any associated keys
            if (config.enterpriseConfig != null) {
                config.enterpriseConfig.removeKeys(mKeyStore);
            }
            mConfiguredNetworks.remove(netId);
            mNetworkIds.remove(configKey(config));

            writeIpAndProxyConfigurations();
            sendConfiguredNetworksChangedBroadcast(config, WifiManager.CHANGE_REASON_REMOVED);
        }
    }

    /**
     * Enable a network. Note that there is no saveConfig operation.
     * This function is retained for compatibility with the public
     * API. The more powerful selectNetwork()/saveNetwork() is used by the
     * state machine for connecting to a network
     *
     * @param netId network to be enabled
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean enableNetwork(int netId, boolean disableOthers) {
        boolean ret = enableNetworkWithoutBroadcast(netId, disableOthers);
        if (disableOthers) {
            if (VDBG) localLog("enableNetwork(disableOthers=true) ", netId);
            sendConfiguredNetworksChangedBroadcast();
        } else {
            if (VDBG) localLog("enableNetwork(disableOthers=false) ", netId);
            WifiConfiguration enabledNetwork = null;
            synchronized(mConfiguredNetworks) {
                enabledNetwork = mConfiguredNetworks.get(netId);
            }
            // check just in case the network was removed by someone else.
            if (enabledNetwork != null) {
                sendConfiguredNetworksChangedBroadcast(enabledNetwork,
                        WifiManager.CHANGE_REASON_CONFIG_CHANGE);
            }
        }
        return ret;
    }

    boolean enableNetworkWithoutBroadcast(int netId, boolean disableOthers) {
        boolean ret = mWifiNative.enableNetwork(netId, disableOthers);

        WifiConfiguration config = mConfiguredNetworks.get(netId);
        if (config != null) config.status = Status.ENABLED;

        if (disableOthers) {
            markAllNetworksDisabledExcept(netId);
        }
        return ret;
    }

    void disableAllNetworks() {
        if (VDBG) localLog("disableAllNetworks");
        boolean networkDisabled = false;
        for(WifiConfiguration config : mConfiguredNetworks.values()) {
            if(config != null && config.status != Status.DISABLED) {
                if(mWifiNative.disableNetwork(config.networkId)) {
                    networkDisabled = true;
                    config.status = Status.DISABLED;
                } else {
                    loge("Disable network failed on " + config.networkId);
                }
            }
        }

        if (networkDisabled) {
            sendConfiguredNetworksChangedBroadcast();
        }
    }
    /**
     * Disable a network. Note that there is no saveConfig operation.
     * @param netId network to be disabled
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean disableNetwork(int netId) {
        return disableNetwork(netId, WifiConfiguration.DISABLED_UNKNOWN_REASON);
    }

    /**
     * Disable a network. Note that there is no saveConfig operation.
     * @param netId network to be disabled
     * @param reason reason code network was disabled
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean disableNetwork(int netId, int reason) {
        if (VDBG) localLog("disableNetwork", netId);
        boolean ret = mWifiNative.disableNetwork(netId);
        WifiConfiguration network = null;
        WifiConfiguration config = mConfiguredNetworks.get(netId);
        /* Only change the reason if the network was not previously disabled */
        if (config != null && config.status != Status.DISABLED) {
            config.status = Status.DISABLED;
            config.disableReason = reason;
            network = config;
        }
        if (network != null) {
            sendConfiguredNetworksChangedBroadcast(network,
                    WifiManager.CHANGE_REASON_CONFIG_CHANGE);
        }
        return ret;
    }

    /**
     * Save the configured networks in supplicant to disk
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    boolean saveConfig() {
        return mWifiNative.saveConfig();
    }

    /**
     * Start WPS pin method configuration with pin obtained
     * from the access point
     * @param config WPS configuration
     * @return Wps result containing status and pin
     */
    WpsResult startWpsWithPinFromAccessPoint(WpsInfo config) {
        WpsResult result = new WpsResult();
        if (mWifiNative.startWpsRegistrar(config.BSSID, config.pin)) {
            /* WPS leaves all networks disabled */
            markAllNetworksDisabled();
            result.status = WpsResult.Status.SUCCESS;
        } else {
            loge("Failed to start WPS pin method configuration");
            result.status = WpsResult.Status.FAILURE;
        }
        return result;
    }

    /**
     * Start WPS pin method configuration with pin obtained
     * from the device
     * @return WpsResult indicating status and pin
     */
    WpsResult startWpsWithPinFromDevice(WpsInfo config) {
        WpsResult result = new WpsResult();
        result.pin = mWifiNative.startWpsPinDisplay(config.BSSID);
        /* WPS leaves all networks disabled */
        if (!TextUtils.isEmpty(result.pin)) {
            markAllNetworksDisabled();
            result.status = WpsResult.Status.SUCCESS;
        } else {
            loge("Failed to start WPS pin method configuration");
            result.status = WpsResult.Status.FAILURE;
        }
        return result;
    }

    /**
     * Start WPS push button configuration
     * @param config WPS configuration
     * @return WpsResult indicating status and pin
     */
    WpsResult startWpsPbc(WpsInfo config) {
        WpsResult result = new WpsResult();
        if (mWifiNative.startWpsPbc(config.BSSID)) {
            /* WPS leaves all networks disabled */
            markAllNetworksDisabled();
            result.status = WpsResult.Status.SUCCESS;
        } else {
            loge("Failed to start WPS push button configuration");
            result.status = WpsResult.Status.FAILURE;
        }
        return result;
    }

    /**
     * Fetch the link properties for a given network id
     * @return LinkProperties for the given network id
     */
    LinkProperties getLinkProperties(int netId) {
        WifiConfiguration config = mConfiguredNetworks.get(netId);
        if (config != null) return new LinkProperties(config.linkProperties);
        return null;
    }

    /**
     * set IP configuration for a given network id
     */
    void setLinkProperties(int netId, LinkProperties linkProperties) {
        WifiConfiguration config = mConfiguredNetworks.get(netId);
        if (config != null) {
            // add old proxy details - TODO - is this still needed?
            if(config.linkProperties != null) {
                linkProperties.setHttpProxy(config.linkProperties.getHttpProxy());
            }
            config.linkProperties = linkProperties;
        }
    }

    /**
     * clear IP configuration for a given network id
     * @param network id
     */
    void clearLinkProperties(int netId) {
        WifiConfiguration config = mConfiguredNetworks.get(netId);
        if (config != null && config.linkProperties != null) {
            // Clear everything except proxy
            ProxyProperties proxy = config.linkProperties.getHttpProxy();
            config.linkProperties.clear();
            config.linkProperties.setHttpProxy(proxy);
        }
    }


    /**
     * Fetch the proxy properties for a given network id
     * @param network id
     * @return ProxyProperties for the network id
     */
    ProxyProperties getProxyProperties(int netId) {
        LinkProperties linkProperties = getLinkProperties(netId);
        if (linkProperties != null) {
            return new ProxyProperties(linkProperties.getHttpProxy());
        }
        return null;
    }

    /**
     * Return if the specified network is using static IP
     * @param network id
     * @return {@code true} if using static ip for netId
     */
    boolean isUsingStaticIp(int netId) {
        WifiConfiguration config = mConfiguredNetworks.get(netId);
        if (config != null && config.ipAssignment == IpAssignment.STATIC) {
            return true;
        }
        return false;
    }

    /**
     * Should be called when a single network configuration is made.
     * @param network The network configuration that changed.
     * @param reason The reason for the change, should be one of WifiManager.CHANGE_REASON_ADDED,
     * WifiManager.CHANGE_REASON_REMOVED, or WifiManager.CHANGE_REASON_CHANGE.
     */
    private void sendConfiguredNetworksChangedBroadcast(WifiConfiguration network,
            int reason) {
        Intent intent = new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_MULTIPLE_NETWORKS_CHANGED, false);
        intent.putExtra(WifiManager.EXTRA_WIFI_CONFIGURATION, network);
        intent.putExtra(WifiManager.EXTRA_CHANGE_REASON, reason);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * Should be called when multiple network configuration changes are made.
     */
    private void sendConfiguredNetworksChangedBroadcast() {
        Intent intent = new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_MULTIPLE_NETWORKS_CHANGED, true);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    void loadConfiguredNetworks() {
        String listStr = mWifiNative.listNetworks();
        mLastPriority = 0;

        mConfiguredNetworks.clear();
        mNetworkIds.clear();

        if (listStr == null)
            return;

        String[] lines = listStr.split("\n");
        // Skip the first line, which is a header
        for (int i = 1; i < lines.length; i++) {
            String[] result = lines[i].split("\t");
            // network-id | ssid | bssid | flags
            WifiConfiguration config = new WifiConfiguration();
            try {
                config.networkId = Integer.parseInt(result[0]);
            } catch(NumberFormatException e) {
                loge("Failed to read network-id '" + result[0] + "'");
                continue;
            }
            if (result.length > 3) {
                if (result[3].indexOf("[CURRENT]") != -1)
                    config.status = WifiConfiguration.Status.CURRENT;
                else if (result[3].indexOf("[DISABLED]") != -1)
                    config.status = WifiConfiguration.Status.DISABLED;
                else
                    config.status = WifiConfiguration.Status.ENABLED;
            } else {
                config.status = WifiConfiguration.Status.ENABLED;
            }
            readNetworkVariables(config);
            if (config.priority > mLastPriority) {
                mLastPriority = config.priority;
            }
            config.ipAssignment = IpAssignment.DHCP;
            config.proxySettings = ProxySettings.NONE;

            if (mNetworkIds.containsKey(configKey(config))) {
                // That SSID is already known, just ignore this duplicate entry
                if (VDBG) localLog("discarded duplicate network", config.networkId);
            } else {
                mConfiguredNetworks.put(config.networkId, config);
                mNetworkIds.put(configKey(config), config.networkId);
                if (VDBG) localLog("loaded configured network", config.networkId);
            }
        }

        readIpAndProxyConfigurations();
        sendConfiguredNetworksChangedBroadcast();

        if (VDBG) localLog("loadConfiguredNetworks loaded " + mNetworkIds.size() + " networks");

        if (mNetworkIds.size() == 0) {
            // no networks? Lets log if the wpa_supplicant.conf file contents
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(SUPPLICANT_CONFIG_FILE));
                if (VDBG) localLog("--- Begin wpa_supplicant.conf Contents ---");
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (VDBG) localLog(line);
                }
                if (VDBG) localLog("--- End wpa_supplicant.conf Contents ---");
            } catch (FileNotFoundException e) {
                if (VDBG) localLog("Could not open " + SUPPLICANT_CONFIG_FILE + ", " + e);
            } catch (IOException e) {
                if (VDBG) localLog("Could not read " + SUPPLICANT_CONFIG_FILE + ", " + e);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    // Just ignore the fact that we couldn't close
                }
            }
        }
    }

    /* Mark all networks except specified netId as disabled */
    private void markAllNetworksDisabledExcept(int netId) {
        for(WifiConfiguration config : mConfiguredNetworks.values()) {
            if(config != null && config.networkId != netId) {
                if (config.status != Status.DISABLED) {
                    config.status = Status.DISABLED;
                    config.disableReason = WifiConfiguration.DISABLED_UNKNOWN_REASON;
                }
            }
        }
    }

    private void markAllNetworksDisabled() {
        markAllNetworksDisabledExcept(INVALID_NETWORK_ID);
    }

    boolean needsUnlockedKeyStore() {

        // Any network using certificates to authenticate access requires
        // unlocked key store; unless the certificates can be stored with
        // hardware encryption

        for(WifiConfiguration config : mConfiguredNetworks.values()) {

            if (config.allowedKeyManagement.get(KeyMgmt.WPA_EAP)
                    && config.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {

                if (config.enterpriseConfig.needsSoftwareBackedKeyStore()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void writeIpAndProxyConfigurations() {

        /* Make a copy */
        List<WifiConfiguration> networks = new ArrayList<WifiConfiguration>();
        for(WifiConfiguration config : mConfiguredNetworks.values()) {
            networks.add(new WifiConfiguration(config));
        }

        DelayedDiskWrite.write(networks);
    }

    private static class DelayedDiskWrite {

        private static HandlerThread sDiskWriteHandlerThread;
        private static Handler sDiskWriteHandler;
        /* Tracks multiple writes on the same thread */
        private static int sWriteSequence = 0;
        private static final String TAG = "DelayedDiskWrite";

        static void write (final List<WifiConfiguration> networks) {

            /* Do a delayed write to disk on a seperate handler thread */
            synchronized (DelayedDiskWrite.class) {
                if (++sWriteSequence == 1) {
                    sDiskWriteHandlerThread = new HandlerThread("WifiConfigThread");
                    sDiskWriteHandlerThread.start();
                    sDiskWriteHandler = new Handler(sDiskWriteHandlerThread.getLooper());
                }
            }

            sDiskWriteHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onWriteCalled(networks);
                    }
                });
        }

        private static void onWriteCalled(List<WifiConfiguration> networks) {

            DataOutputStream out = null;
            try {
                out = new DataOutputStream(new BufferedOutputStream(
                            new FileOutputStream(ipConfigFile)));

                out.writeInt(IPCONFIG_FILE_VERSION);

                for(WifiConfiguration config : networks) {
                    boolean writeToFile = false;

                    try {
                        LinkProperties linkProperties = config.linkProperties;
                        switch (config.ipAssignment) {
                            case STATIC:
                                out.writeUTF(IP_ASSIGNMENT_KEY);
                                out.writeUTF(config.ipAssignment.toString());
                                for (LinkAddress linkAddr : linkProperties.getLinkAddresses()) {
                                    out.writeUTF(LINK_ADDRESS_KEY);
                                    out.writeUTF(linkAddr.getAddress().getHostAddress());
                                    out.writeInt(linkAddr.getNetworkPrefixLength());
                                }
                                for (RouteInfo route : linkProperties.getRoutes()) {
                                    out.writeUTF(GATEWAY_KEY);
                                    LinkAddress dest = route.getDestination();
                                    if (dest != null) {
                                        out.writeInt(1);
                                        out.writeUTF(dest.getAddress().getHostAddress());
                                        out.writeInt(dest.getNetworkPrefixLength());
                                    } else {
                                        out.writeInt(0);
                                    }
                                    if (route.getGateway() != null) {
                                        out.writeInt(1);
                                        out.writeUTF(route.getGateway().getHostAddress());
                                    } else {
                                        out.writeInt(0);
                                    }
                                }
                                for (InetAddress inetAddr : linkProperties.getDnses()) {
                                    out.writeUTF(DNS_KEY);
                                    out.writeUTF(inetAddr.getHostAddress());
                                }
                                writeToFile = true;
                                break;
                            case DHCP:
                                out.writeUTF(IP_ASSIGNMENT_KEY);
                                out.writeUTF(config.ipAssignment.toString());
                                writeToFile = true;
                                break;
                            case UNASSIGNED:
                                /* Ignore */
                                break;
                            default:
                                loge("Ignore invalid ip assignment while writing");
                                break;
                        }

                        switch (config.proxySettings) {
                            case STATIC:
                                ProxyProperties proxyProperties = linkProperties.getHttpProxy();
                                String exclusionList = proxyProperties.getExclusionList();
                                out.writeUTF(PROXY_SETTINGS_KEY);
                                out.writeUTF(config.proxySettings.toString());
                                out.writeUTF(PROXY_HOST_KEY);
                                out.writeUTF(proxyProperties.getHost());
                                out.writeUTF(PROXY_PORT_KEY);
                                out.writeInt(proxyProperties.getPort());
                                out.writeUTF(EXCLUSION_LIST_KEY);
                                out.writeUTF(exclusionList);
                                writeToFile = true;
                                break;
                            case PAC:
                                ProxyProperties proxyPacProperties = linkProperties.getHttpProxy();
                                out.writeUTF(PROXY_SETTINGS_KEY);
                                out.writeUTF(config.proxySettings.toString());
                                out.writeUTF(PROXY_PAC_FILE);
                                out.writeUTF(proxyPacProperties.getPacFileUrl());
                                writeToFile = true;
                                break;
                            case NONE:
                                out.writeUTF(PROXY_SETTINGS_KEY);
                                out.writeUTF(config.proxySettings.toString());
                                writeToFile = true;
                                break;
                            case UNASSIGNED:
                                /* Ignore */
                                break;
                            default:
                                loge("Ignthisore invalid proxy settings while writing");
                                break;
                        }
                        if (writeToFile) {
                            out.writeUTF(ID_KEY);
                            out.writeInt(configKey(config));
                        }
                    } catch (NullPointerException e) {
                        loge("Failure in writing " + config.linkProperties + e);
                    }
                    out.writeUTF(EOS);
                }

            } catch (IOException e) {
                loge("Error writing data file");
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception e) {}
                }

                //Quit if no more writes sent
                synchronized (DelayedDiskWrite.class) {
                    if (--sWriteSequence == 0) {
                        sDiskWriteHandler.getLooper().quit();
                        sDiskWriteHandler = null;
                        sDiskWriteHandlerThread = null;
                    }
                }
            }
        }

        private static void loge(String s) {
            Log.e(TAG, s);
        }
    }

    private void readIpAndProxyConfigurations() {

        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(
                    ipConfigFile)));

            int version = in.readInt();
            if (version != 2 && version != 1) {
                loge("Bad version on IP configuration file, ignore read");
                return;
            }

            while (true) {
                int id = -1;
                // Default is DHCP with no proxy
                IpAssignment ipAssignment = IpAssignment.DHCP;
                ProxySettings proxySettings = ProxySettings.NONE;
                LinkProperties linkProperties = new LinkProperties();
                String proxyHost = null;
                String pacFileUrl = null;
                int proxyPort = -1;
                String exclusionList = null;
                String key;

                do {
                    key = in.readUTF();
                    try {
                        if (key.equals(ID_KEY)) {
                            id = in.readInt();
                        } else if (key.equals(IP_ASSIGNMENT_KEY)) {
                            ipAssignment = IpAssignment.valueOf(in.readUTF());
                        } else if (key.equals(LINK_ADDRESS_KEY)) {
                            LinkAddress linkAddr = new LinkAddress(
                                    NetworkUtils.numericToInetAddress(in.readUTF()), in.readInt());
                            linkProperties.addLinkAddress(linkAddr);
                        } else if (key.equals(GATEWAY_KEY)) {
                            LinkAddress dest = null;
                            InetAddress gateway = null;
                            if (version == 1) {
                                // only supported default gateways - leave the dest/prefix empty
                                gateway = NetworkUtils.numericToInetAddress(in.readUTF());
                            } else {
                                if (in.readInt() == 1) {
                                    dest = new LinkAddress(
                                            NetworkUtils.numericToInetAddress(in.readUTF()),
                                            in.readInt());
                                }
                                if (in.readInt() == 1) {
                                    gateway = NetworkUtils.numericToInetAddress(in.readUTF());
                                }
                            }
                            linkProperties.addRoute(new RouteInfo(dest, gateway));
                        } else if (key.equals(DNS_KEY)) {
                            linkProperties.addDns(
                                    NetworkUtils.numericToInetAddress(in.readUTF()));
                        } else if (key.equals(PROXY_SETTINGS_KEY)) {
                            proxySettings = ProxySettings.valueOf(in.readUTF());
                        } else if (key.equals(PROXY_HOST_KEY)) {
                            proxyHost = in.readUTF();
                        } else if (key.equals(PROXY_PORT_KEY)) {
                            proxyPort = in.readInt();
                        } else if (key.equals(PROXY_PAC_FILE)) {
                            pacFileUrl = in.readUTF();
                        } else if (key.equals(EXCLUSION_LIST_KEY)) {
                            exclusionList = in.readUTF();
                        } else if (key.equals(EOS)) {
                            break;
                        } else {
                            loge("Ignore unknown key " + key + "while reading");
                        }
                    } catch (IllegalArgumentException e) {
                        loge("Ignore invalid address while reading" + e);
                    }
                } while (true);

                if (id != -1) {
                    WifiConfiguration config = mConfiguredNetworks.get(
                            mNetworkIds.get(id));

                    if (config == null) {
                        loge("configuration found for missing network, ignored");
                    } else {
                        config.linkProperties = linkProperties;
                        switch (ipAssignment) {
                            case STATIC:
                            case DHCP:
                                config.ipAssignment = ipAssignment;
                                break;
                            case UNASSIGNED:
                                loge("BUG: Found UNASSIGNED IP on file, use DHCP");
                                config.ipAssignment = IpAssignment.DHCP;
                                break;
                            default:
                                loge("Ignore invalid ip assignment while reading");
                                break;
                        }

                        switch (proxySettings) {
                            case STATIC:
                                config.proxySettings = proxySettings;
                                ProxyProperties proxyProperties =
                                    new ProxyProperties(proxyHost, proxyPort, exclusionList);
                                linkProperties.setHttpProxy(proxyProperties);
                                break;
                            case PAC:
                                config.proxySettings = proxySettings;
                                ProxyProperties proxyPacProperties = 
                                        new ProxyProperties(pacFileUrl);
                                linkProperties.setHttpProxy(proxyPacProperties);
                                break;
                            case NONE:
                                config.proxySettings = proxySettings;
                                break;
                            case UNASSIGNED:
                                loge("BUG: Found UNASSIGNED proxy on file, use NONE");
                                config.proxySettings = ProxySettings.NONE;
                                break;
                            default:
                                loge("Ignore invalid proxy settings while reading");
                                break;
                        }
                    }
                } else {
                    if (DBG) log("Missing id while parsing configuration");
                }
            }
        } catch (EOFException ignore) {
        } catch (IOException e) {
            loge("Error parsing configuration" + e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {}
            }
        }
    }

    private NetworkUpdateResult addOrUpdateNetworkNative(WifiConfiguration config) {
        /*
         * If the supplied networkId is INVALID_NETWORK_ID, we create a new empty
         * network configuration. Otherwise, the networkId should
         * refer to an existing configuration.
         */

        if (VDBG) localLog("addOrUpdateNetworkNative " + config.getPrintableSsid());

        int netId = config.networkId;
        boolean newNetwork = false;
        // networkId of INVALID_NETWORK_ID means we want to create a new network
        if (netId == INVALID_NETWORK_ID) {
            Integer savedNetId = mNetworkIds.get(configKey(config));
            if (savedNetId != null) {
                netId = savedNetId;
            } else {
                newNetwork = true;
                netId = mWifiNative.addNetwork();
                if (netId < 0) {
                    loge("Failed to add a network!");
                    return new NetworkUpdateResult(INVALID_NETWORK_ID);
                }
            }
        }

        boolean updateFailed = true;

        setVariables: {

            if (config.BSSID != null &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.bssidVarName,
                        config.BSSID)) {
                loge("failed to set BSSID: "+config.BSSID);
                break setVariables;
            }

            if (config.SSID != null &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.ssidVarName,
                        config.SSID)) {
                loge("failed to set SSID: "+config.SSID);
                break setVariables;
            }

            if (config.isIBSS) {
                if(!mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.modeVarName,
                        "1")) {
                    loge("failed to set adhoc mode");
                    break setVariables;
                }
                if(!mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.frequencyVarName,
                        Integer.toString(config.frequency))) {
                    loge("failed to set frequency");
                    break setVariables;
                }
            }

            String allowedKeyManagementString =
                makeString(config.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
            if (config.allowedKeyManagement.cardinality() != 0 &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.KeyMgmt.varName,
                        allowedKeyManagementString)) {
                loge("failed to set key_mgmt: "+
                        allowedKeyManagementString);
                break setVariables;
            }

            String allowedProtocolsString =
                makeString(config.allowedProtocols, WifiConfiguration.Protocol.strings);
            if (config.allowedProtocols.cardinality() != 0 &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.Protocol.varName,
                        allowedProtocolsString)) {
                loge("failed to set proto: "+
                        allowedProtocolsString);
                break setVariables;
            }

            String allowedAuthAlgorithmsString =
                makeString(config.allowedAuthAlgorithms, WifiConfiguration.AuthAlgorithm.strings);
            if (config.allowedAuthAlgorithms.cardinality() != 0 &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.AuthAlgorithm.varName,
                        allowedAuthAlgorithmsString)) {
                loge("failed to set auth_alg: "+
                        allowedAuthAlgorithmsString);
                break setVariables;
            }

            String allowedPairwiseCiphersString =
                    makeString(config.allowedPairwiseCiphers,
                    WifiConfiguration.PairwiseCipher.strings);
            if (config.allowedPairwiseCiphers.cardinality() != 0 &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.PairwiseCipher.varName,
                        allowedPairwiseCiphersString)) {
                loge("failed to set pairwise: "+
                        allowedPairwiseCiphersString);
                break setVariables;
            }

            String allowedGroupCiphersString =
                makeString(config.allowedGroupCiphers, WifiConfiguration.GroupCipher.strings);
            if (config.allowedGroupCiphers.cardinality() != 0 &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.GroupCipher.varName,
                        allowedGroupCiphersString)) {
                loge("failed to set group: "+
                        allowedGroupCiphersString);
                break setVariables;
            }

            // Prevent client screw-up by passing in a WifiConfiguration we gave it
            // by preventing "*" as a key.
            if (config.preSharedKey != null && !config.preSharedKey.equals("*") &&
                    !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.pskVarName,
                        config.preSharedKey)) {
                loge("failed to set psk");
                break setVariables;
            }

            boolean hasSetKey = false;
            if (config.wepKeys != null) {
                for (int i = 0; i < config.wepKeys.length; i++) {
                    // Prevent client screw-up by passing in a WifiConfiguration we gave it
                    // by preventing "*" as a key.
                    if (config.wepKeys[i] != null && !config.wepKeys[i].equals("*")) {
                        if (!mWifiNative.setNetworkVariable(
                                    netId,
                                    WifiConfiguration.wepKeyVarNames[i],
                                    config.wepKeys[i])) {
                            loge("failed to set wep_key" + i + ": " + config.wepKeys[i]);
                            break setVariables;
                        }
                        hasSetKey = true;
                    }
                }
            }

            if (hasSetKey) {
                if (!mWifiNative.setNetworkVariable(
                            netId,
                            WifiConfiguration.wepTxKeyIdxVarName,
                            Integer.toString(config.wepTxKeyIndex))) {
                    loge("failed to set wep_tx_keyidx: " + config.wepTxKeyIndex);
                    break setVariables;
                }
            }

            if (!mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.priorityVarName,
                        Integer.toString(config.priority))) {
                loge(config.SSID + ": failed to set priority: "
                        +config.priority);
                break setVariables;
            }

            if (config.hiddenSSID && !mWifiNative.setNetworkVariable(
                        netId,
                        WifiConfiguration.hiddenSSIDVarName,
                        Integer.toString(config.hiddenSSID ? 1 : 0))) {
                loge(config.SSID + ": failed to set hiddenSSID: "+
                        config.hiddenSSID);
                break setVariables;
            }

            if (config.enterpriseConfig != null &&
                    config.enterpriseConfig.getEapMethod() != WifiEnterpriseConfig.Eap.NONE) {

                WifiEnterpriseConfig enterpriseConfig = config.enterpriseConfig;

                if (enterpriseConfig.needsKeyStore()) {
                    /**
                     * Keyguard settings may eventually be controlled by device policy.
                     * We check here if keystore is unlocked before installing
                     * credentials.
                     * TODO: Do we need a dialog here ?
                     */
                    if (mKeyStore.state() != KeyStore.State.UNLOCKED) {
                        loge(config.SSID + ": key store is locked");
                        break setVariables;
                    }

                    try {
                        /* config passed may include only fields being updated.
                         * In order to generate the key id, fetch uninitialized
                         * fields from the currently tracked configuration
                         */
                        WifiConfiguration currentConfig = mConfiguredNetworks.get(netId);
                        String keyId = config.getKeyIdForCredentials(currentConfig);

                        if (!enterpriseConfig.installKeys(mKeyStore, keyId)) {
                            loge(config.SSID + ": failed to install keys");
                            break setVariables;
                        }
                    } catch (IllegalStateException e) {
                        loge(config.SSID + " invalid config for key installation");
                        break setVariables;
                    }
                }

                HashMap<String, String> enterpriseFields = enterpriseConfig.getFields();
                for (String key : enterpriseFields.keySet()) {
                        String value = enterpriseFields.get(key);
                        if (!mWifiNative.setNetworkVariable(
                                    netId,
                                    key,
                                    value)) {
                            enterpriseConfig.removeKeys(mKeyStore);
                            loge(config.SSID + ": failed to set " + key +
                                    ": " + value);
                            break setVariables;
                        }
                }
            }
            updateFailed = false;
        } //end of setVariables

        if (updateFailed) {
            if (newNetwork) {
                mWifiNative.removeNetwork(netId);
                loge("Failed to set a network variable, removed network: " + netId);
            }
            return new NetworkUpdateResult(INVALID_NETWORK_ID);
        }

        /* An update of the network variables requires reading them
         * back from the supplicant to update mConfiguredNetworks.
         * This is because some of the variables (SSID, wep keys &
         * passphrases) reflect different values when read back than
         * when written. For example, wep key is stored as * irrespective
         * of the value sent to the supplicant
         */
        WifiConfiguration currentConfig = mConfiguredNetworks.get(netId);
        if (currentConfig == null) {
            currentConfig = new WifiConfiguration();
            currentConfig.ipAssignment = IpAssignment.DHCP;
            currentConfig.proxySettings = ProxySettings.NONE;
            currentConfig.networkId = netId;
        }

        readNetworkVariables(currentConfig);

        mConfiguredNetworks.put(netId, currentConfig);
        mNetworkIds.put(configKey(currentConfig), netId);

        NetworkUpdateResult result = writeIpAndProxyConfigurationsOnChange(currentConfig, config);
        result.setIsNewNetwork(newNetwork);
        result.setNetworkId(netId);
        return result;
    }

    /* Compare current and new configuration and write to file on change */
    private NetworkUpdateResult writeIpAndProxyConfigurationsOnChange(
            WifiConfiguration currentConfig,
            WifiConfiguration newConfig) {
        boolean ipChanged = false;
        boolean proxyChanged = false;
        LinkProperties linkProperties = null;

        switch (newConfig.ipAssignment) {
            case STATIC:
                Collection<LinkAddress> currentLinkAddresses = currentConfig.linkProperties
                        .getLinkAddresses();
                Collection<LinkAddress> newLinkAddresses = newConfig.linkProperties
                        .getLinkAddresses();
                Collection<InetAddress> currentDnses = currentConfig.linkProperties.getDnses();
                Collection<InetAddress> newDnses = newConfig.linkProperties.getDnses();
                Collection<RouteInfo> currentRoutes = currentConfig.linkProperties.getRoutes();
                Collection<RouteInfo> newRoutes = newConfig.linkProperties.getRoutes();

                boolean linkAddressesDiffer =
                        (currentLinkAddresses.size() != newLinkAddresses.size()) ||
                        !currentLinkAddresses.containsAll(newLinkAddresses);
                boolean dnsesDiffer = (currentDnses.size() != newDnses.size()) ||
                        !currentDnses.containsAll(newDnses);
                boolean routesDiffer = (currentRoutes.size() != newRoutes.size()) ||
                        !currentRoutes.containsAll(newRoutes);

                if ((currentConfig.ipAssignment != newConfig.ipAssignment) ||
                        linkAddressesDiffer ||
                        dnsesDiffer ||
                        routesDiffer) {
                    ipChanged = true;
                }
                break;
            case DHCP:
                if (currentConfig.ipAssignment != newConfig.ipAssignment) {
                    ipChanged = true;
                }
                break;
            case UNASSIGNED:
                /* Ignore */
                break;
            default:
                loge("Ignore invalid ip assignment during write");
                break;
        }

        switch (newConfig.proxySettings) {
            case STATIC:
            case PAC:
                ProxyProperties newHttpProxy = newConfig.linkProperties.getHttpProxy();
                ProxyProperties currentHttpProxy = currentConfig.linkProperties.getHttpProxy();

                if (newHttpProxy != null) {
                    proxyChanged = !newHttpProxy.equals(currentHttpProxy);
                } else {
                    proxyChanged = (currentHttpProxy != null);
                }
                break;
            case NONE:
                if (currentConfig.proxySettings != newConfig.proxySettings) {
                    proxyChanged = true;
                }
                break;
            case UNASSIGNED:
                /* Ignore */
                break;
            default:
                loge("Ignore invalid proxy configuration during write");
                break;
        }

        if (!ipChanged) {
            linkProperties = copyIpSettingsFromConfig(currentConfig);
        } else {
            currentConfig.ipAssignment = newConfig.ipAssignment;
            linkProperties = copyIpSettingsFromConfig(newConfig);
            log("IP config changed SSID = " + currentConfig.SSID + " linkProperties: " +
                    linkProperties.toString());
        }


        if (!proxyChanged) {
            linkProperties.setHttpProxy(currentConfig.linkProperties.getHttpProxy());
        } else {
            currentConfig.proxySettings = newConfig.proxySettings;
            linkProperties.setHttpProxy(newConfig.linkProperties.getHttpProxy());
            log("proxy changed SSID = " + currentConfig.SSID);
            if (linkProperties.getHttpProxy() != null) {
                log(" proxyProperties: " + linkProperties.getHttpProxy().toString());
            }
        }

        if (ipChanged || proxyChanged) {
            currentConfig.linkProperties = linkProperties;
            writeIpAndProxyConfigurations();
            sendConfiguredNetworksChangedBroadcast(currentConfig,
                    WifiManager.CHANGE_REASON_CONFIG_CHANGE);
        }
        return new NetworkUpdateResult(ipChanged, proxyChanged);
    }

    private LinkProperties copyIpSettingsFromConfig(WifiConfiguration config) {
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setInterfaceName(config.linkProperties.getInterfaceName());
        for (LinkAddress linkAddr : config.linkProperties.getLinkAddresses()) {
            linkProperties.addLinkAddress(linkAddr);
        }
        for (RouteInfo route : config.linkProperties.getRoutes()) {
            linkProperties.addRoute(route);
        }
        for (InetAddress dns : config.linkProperties.getDnses()) {
            linkProperties.addDns(dns);
        }
        return linkProperties;
    }

    /**
     * Read the variables from the supplicant daemon that are needed to
     * fill in the WifiConfiguration object.
     *
     * @param config the {@link WifiConfiguration} object to be filled in.
     */
    private void readNetworkVariables(WifiConfiguration config) {

        int netId = config.networkId;
        if (netId < 0)
            return;

        /*
         * TODO: maybe should have a native method that takes an array of
         * variable names and returns an array of values. But we'd still
         * be doing a round trip to the supplicant daemon for each variable.
         */
        String value;

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.ssidVarName);
        if (!TextUtils.isEmpty(value)) {
            if (value.charAt(0) != '"') {
                config.SSID = "\"" + WifiSsid.createFromHex(value).toString() + "\"";
                //TODO: convert a hex string that is not UTF-8 decodable to a P-formatted
                //supplicant string
            } else {
                config.SSID = value;
            }
        } else {
            config.SSID = null;
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.bssidVarName);
        if (!TextUtils.isEmpty(value)) {
            config.BSSID = value;
        } else {
            config.BSSID = null;
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.priorityVarName);
        config.priority = -1;
        if (!TextUtils.isEmpty(value)) {
            try {
                config.priority = Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
            }
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.hiddenSSIDVarName);
        config.hiddenSSID = false;
        if (!TextUtils.isEmpty(value)) {
            try {
                config.hiddenSSID = Integer.parseInt(value) != 0;
            } catch (NumberFormatException ignore) {
            }
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.modeVarName);
        config.isIBSS = false;
        if (!TextUtils.isEmpty(value)) {
            try {
                config.isIBSS = Integer.parseInt(value) != 0;
            } catch (NumberFormatException ignore) {
            }
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.frequencyVarName);
        config.frequency = 0;
        if (!TextUtils.isEmpty(value)) {
            try {
                config.frequency = Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
            }
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.wepTxKeyIdxVarName);
        config.wepTxKeyIndex = -1;
        if (!TextUtils.isEmpty(value)) {
            try {
                config.wepTxKeyIndex = Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
            }
        }

        for (int i = 0; i < 4; i++) {
            value = mWifiNative.getNetworkVariable(netId,
                    WifiConfiguration.wepKeyVarNames[i]);
            if (!TextUtils.isEmpty(value)) {
                config.wepKeys[i] = value;
            } else {
                config.wepKeys[i] = null;
            }
        }

        value = mWifiNative.getNetworkVariable(netId, WifiConfiguration.pskVarName);
        if (!TextUtils.isEmpty(value)) {
            config.preSharedKey = value;
        } else {
            config.preSharedKey = null;
        }

        value = mWifiNative.getNetworkVariable(config.networkId,
                WifiConfiguration.Protocol.varName);
        if (!TextUtils.isEmpty(value)) {
            String vals[] = value.split(" ");
            for (String val : vals) {
                int index =
                    lookupString(val, WifiConfiguration.Protocol.strings);
                if (0 <= index) {
                    config.allowedProtocols.set(index);
                }
            }
        }

        value = mWifiNative.getNetworkVariable(config.networkId,
                WifiConfiguration.KeyMgmt.varName);
        if (!TextUtils.isEmpty(value)) {
            String vals[] = value.split(" ");
            for (String val : vals) {
                int index =
                    lookupString(val, WifiConfiguration.KeyMgmt.strings);
                if (0 <= index) {
                    config.allowedKeyManagement.set(index);
                }
            }
        }

        value = mWifiNative.getNetworkVariable(config.networkId,
                WifiConfiguration.AuthAlgorithm.varName);
        if (!TextUtils.isEmpty(value)) {
            String vals[] = value.split(" ");
            for (String val : vals) {
                int index =
                    lookupString(val, WifiConfiguration.AuthAlgorithm.strings);
                if (0 <= index) {
                    config.allowedAuthAlgorithms.set(index);
                }
            }
        }

        value = mWifiNative.getNetworkVariable(config.networkId,
                WifiConfiguration.PairwiseCipher.varName);
        if (!TextUtils.isEmpty(value)) {
            String vals[] = value.split(" ");
            for (String val : vals) {
                int index =
                    lookupString(val, WifiConfiguration.PairwiseCipher.strings);
                if (0 <= index) {
                    config.allowedPairwiseCiphers.set(index);
                }
            }
        }

        value = mWifiNative.getNetworkVariable(config.networkId,
                WifiConfiguration.GroupCipher.varName);
        if (!TextUtils.isEmpty(value)) {
            String vals[] = value.split(" ");
            for (String val : vals) {
                int index =
                    lookupString(val, WifiConfiguration.GroupCipher.strings);
                if (0 <= index) {
                    config.allowedGroupCiphers.set(index);
                }
            }
        }

        if (config.enterpriseConfig == null) {
            config.enterpriseConfig = new WifiEnterpriseConfig();
        }
        HashMap<String, String> enterpriseFields = config.enterpriseConfig.getFields();
        for (String key : WifiEnterpriseConfig.getSupplicantKeys()) {
            value = mWifiNative.getNetworkVariable(netId, key);
            if (!TextUtils.isEmpty(value)) {
                enterpriseFields.put(key, removeDoubleQuotes(value));
            } else {
                enterpriseFields.put(key, WifiEnterpriseConfig.EMPTY_VALUE);
            }
        }

        if (config.enterpriseConfig.migrateOldEapTlsNative(mWifiNative, netId)) {
            saveConfig();
        }

        config.enterpriseConfig.migrateCerts(mKeyStore);
        config.enterpriseConfig.initializeSoftwareKeystoreFlag(mKeyStore);
    }

    private String removeDoubleQuotes(String string) {
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    private String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    private String makeString(BitSet set, String[] strings) {
        StringBuffer buf = new StringBuffer();
        int nextSetBit = -1;

        /* Make sure all set bits are in [0, strings.length) to avoid
         * going out of bounds on strings.  (Shouldn't happen, but...) */
        set = set.get(0, strings.length);

        while ((nextSetBit = set.nextSetBit(nextSetBit + 1)) != -1) {
            buf.append(strings[nextSetBit].replace('_', '-')).append(' ');
        }

        // remove trailing space
        if (set.cardinality() > 0) {
            buf.setLength(buf.length() - 1);
        }

        return buf.toString();
    }

    private int lookupString(String string, String[] strings) {
        int size = strings.length;

        string = string.replace('-', '_');

        for (int i = 0; i < size; i++)
            if (string.equals(strings[i]))
                return i;

        // if we ever get here, we should probably add the
        // value to WifiConfiguration to reflect that it's
        // supported by the WPA supplicant
        loge("Failed to look-up a string: " + string);

        return -1;
    }

    /* Returns a unique for a given configuration */
    private static int configKey(WifiConfiguration config) {
        String key;

        if (config.allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            key = config.SSID + KeyMgmt.strings[KeyMgmt.WPA_PSK];
        } else if (config.allowedKeyManagement.get(KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
            key = config.SSID + KeyMgmt.strings[KeyMgmt.WPA_EAP];
        } else if (config.wepKeys[0] != null) {
            key = config.SSID + "WEP";
        } else {
            key = config.SSID + KeyMgmt.strings[KeyMgmt.NONE];
        }

        return key.hashCode();
    }

    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiConfigStore");
        pw.println("mLastPriority " + mLastPriority);
        pw.println("Configured networks");
        for (WifiConfiguration conf : getConfiguredNetworks()) {
            pw.println(conf);
        }
        pw.println();

        if (mLocalLog != null) {
            pw.println("WifiConfigStore - Log Begin ----");
            mLocalLog.dump(fd, pw, args);
            pw.println("WifiConfigStore - Log End ----");
        }
    }

    public String getConfigFile() {
        return ipConfigFile;
    }

    private void loge(String s) {
        Log.e(TAG, s);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    private void localLog(String s) {
        if (mLocalLog != null) {
            mLocalLog.log(s);
        }
    }

    private void localLog(String s, int netId) {
        if (mLocalLog == null) {
            return;
        }

        WifiConfiguration config;
        synchronized(mConfiguredNetworks) {
            config = mConfiguredNetworks.get(netId);
        }

        if (config != null) {
            mLocalLog.log(s + " " + config.getPrintableSsid());
        } else {
            mLocalLog.log(s + " " + netId);
        }
    }

}
