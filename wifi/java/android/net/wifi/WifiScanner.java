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

package android.net.wifi;

import android.annotation.SystemApi;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.WorkSource;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Preconditions;
import com.android.internal.util.Protocol;

import java.util.List;


/**
 * This class provides a way to scan the Wifi universe around the device
 * Get an instance of this class by calling
 * {@link android.content.Context#getSystemService(String) Context.getSystemService(Context
 * .WIFI_SCANNING_SERVICE)}.
 * @hide
 */
@SystemApi
public class WifiScanner {

    /** no band specified; use channel list instead */
    public static final int WIFI_BAND_UNSPECIFIED = 0;      /* not specified */

    /** 2.4 GHz band */
    public static final int WIFI_BAND_24_GHZ = 1;           /* 2.4 GHz band */
    /** 5 GHz band excluding DFS channels */
    public static final int WIFI_BAND_5_GHZ = 2;            /* 5 GHz band without DFS channels */
    /** DFS channels from 5 GHz band only */
    public static final int WIFI_BAND_5_GHZ_DFS_ONLY  = 4;  /* 5 GHz band with DFS channels */
    /** 5 GHz band including DFS channels */
    public static final int WIFI_BAND_5_GHZ_WITH_DFS  = 6;  /* 5 GHz band with DFS channels */
    /** Both 2.4 GHz band and 5 GHz band; no DFS channels */
    public static final int WIFI_BAND_BOTH = 3;             /* both bands without DFS channels */
    /** Both 2.4 GHz band and 5 GHz band; with DFS channels */
    public static final int WIFI_BAND_BOTH_WITH_DFS = 7;    /* both bands with DFS channels */

    /** Minimum supported scanning period */
    public static final int MIN_SCAN_PERIOD_MS = 1000;      /* minimum supported period */
    /** Maximum supported scanning period */
    public static final int MAX_SCAN_PERIOD_MS = 1024000;   /* maximum supported period */

    /** No Error */
    public static final int REASON_SUCCEEDED = 0;
    /** Unknown error */
    public static final int REASON_UNSPECIFIED = -1;
    /** Invalid listener */
    public static final int REASON_INVALID_LISTENER = -2;
    /** Invalid request */
    public static final int REASON_INVALID_REQUEST = -3;
    /** Invalid request */
    public static final int REASON_NOT_AUTHORIZED = -4;
    /** An outstanding request with the same listener hasn't finished yet. */
    public static final int REASON_DUPLICATE_REQEUST = -5;

    /** @hide */
    public static final String GET_AVAILABLE_CHANNELS_EXTRA = "Channels";

    /**
     * Generic action callback invocation interface
     *  @hide
     */
    @SystemApi
    public static interface ActionListener {
        public void onSuccess();
        public void onFailure(int reason, String description);
    }

    /**
     * gives you all the possible channels; channel is specified as an
     * integer with frequency in MHz i.e. channel 1 is 2412
     * @hide
     */
    public List<Integer> getAvailableChannels(int band) {
        try {
            Bundle bundle =  mService.getAvailableChannels(band);
            return bundle.getIntegerArrayList(GET_AVAILABLE_CHANNELS_EXTRA);
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * provides channel specification for scanning
     */
    public static class ChannelSpec {
        /**
         * channel frequency in MHz; for example channel 1 is specified as 2412
         */
        public int frequency;
        /**
         * if true, scan this channel in passive fashion.
         * This flag is ignored on DFS channel specification.
         * @hide
         */
        public boolean passive;                                    /* ignored on DFS channels */
        /**
         * how long to dwell on this channel
         * @hide
         */
        public int dwellTimeMS;                                    /* not supported for now */

        /**
         * default constructor for channel spec
         */
        public ChannelSpec(int frequency) {
            this.frequency = frequency;
            passive = false;
            dwellTimeMS = 0;
        }
    }

    /**
     * reports {@link ScanListener#onResults} when underlying buffers are full
     * this is simply the lack of the {@link #REPORT_EVENT_AFTER_EACH_SCAN} flag
     * @deprecated It is not supported anymore.
     */
    @Deprecated
    public static final int REPORT_EVENT_AFTER_BUFFER_FULL = 0;
    /**
     * reports {@link ScanListener#onResults} after each scan
     */
    public static final int REPORT_EVENT_AFTER_EACH_SCAN = (1 << 0);
    /**
     * reports {@link ScanListener#onFullResult} whenever each beacon is discovered
     */
    public static final int REPORT_EVENT_FULL_SCAN_RESULT = (1 << 1);
    /**
     * Do not place scans in the chip's scan history buffer
     */
    public static final int REPORT_EVENT_NO_BATCH = (1 << 2);


    /** {@hide} */
    public static final String SCAN_PARAMS_SCAN_SETTINGS_KEY = "ScanSettings";
    /** {@hide} */
    public static final String SCAN_PARAMS_WORK_SOURCE_KEY = "WorkSource";
    /**
     * scan configuration parameters to be sent to {@link #startBackgroundScan}
     */
    public static class ScanSettings implements Parcelable {

        /** one of the WIFI_BAND values */
        public int band;
        /** list of channels; used when band is set to WIFI_BAND_UNSPECIFIED */
        public ChannelSpec[] channels;
        /**
         * list of networkId's of hidden networks to scan for.
         * These Id's should correspond to the wpa_supplicant's networkId's and will be used
         * in connectivity scans using wpa_supplicant.
         * {@hide}
         * */
        public int[] hiddenNetworkIds;
        /** period of background scan; in millisecond, 0 => single shot scan */
        public int periodInMs;
        /** must have a valid REPORT_EVENT value */
        public int reportEvents;
        /** defines number of bssids to cache from each scan */
        public int numBssidsPerScan;
        /**
         * defines number of scans to cache; use it with REPORT_EVENT_AFTER_BUFFER_FULL
         * to wake up at fixed interval
         */
        public int maxScansToCache;
        /**
         * if maxPeriodInMs is non zero or different than period, then this bucket is
         * a truncated binary exponential backoff bucket and the scan period will grow
         * exponentially as per formula: actual_period(N) = period * (2 ^ (N/stepCount))
         * to maxPeriodInMs
         */
        public int maxPeriodInMs;
        /**
         * for truncated binary exponential back off bucket, number of scans to perform
         * for a given period
         */
        public int stepCount;
        /**
         * Flag to indicate if the scan settings are targeted for PNO scan.
         * {@hide}
         */
        public boolean isPnoScan;

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(band);
            dest.writeInt(periodInMs);
            dest.writeInt(reportEvents);
            dest.writeInt(numBssidsPerScan);
            dest.writeInt(maxScansToCache);
            dest.writeInt(maxPeriodInMs);
            dest.writeInt(stepCount);
            dest.writeInt(isPnoScan ? 1 : 0);
            if (channels != null) {
                dest.writeInt(channels.length);
                for (int i = 0; i < channels.length; i++) {
                    dest.writeInt(channels[i].frequency);
                    dest.writeInt(channels[i].dwellTimeMS);
                    dest.writeInt(channels[i].passive ? 1 : 0);
                }
            } else {
                dest.writeInt(0);
            }
            dest.writeIntArray(hiddenNetworkIds);
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<ScanSettings> CREATOR =
                new Creator<ScanSettings>() {
                    public ScanSettings createFromParcel(Parcel in) {
                        ScanSettings settings = new ScanSettings();
                        settings.band = in.readInt();
                        settings.periodInMs = in.readInt();
                        settings.reportEvents = in.readInt();
                        settings.numBssidsPerScan = in.readInt();
                        settings.maxScansToCache = in.readInt();
                        settings.maxPeriodInMs = in.readInt();
                        settings.stepCount = in.readInt();
                        settings.isPnoScan = in.readInt() == 1;
                        int num_channels = in.readInt();
                        settings.channels = new ChannelSpec[num_channels];
                        for (int i = 0; i < num_channels; i++) {
                            int frequency = in.readInt();
                            ChannelSpec spec = new ChannelSpec(frequency);
                            spec.dwellTimeMS = in.readInt();
                            spec.passive = in.readInt() == 1;
                            settings.channels[i] = spec;
                        }
                        settings.hiddenNetworkIds = in.createIntArray();
                        return settings;
                    }

                    public ScanSettings[] newArray(int size) {
                        return new ScanSettings[size];
                    }
                };

    }

    /**
     * all the information garnered from a single scan
     */
    public static class ScanData implements Parcelable {
        /** scan identifier */
        private int mId;
        /** additional information about scan
         * 0 => no special issues encountered in the scan
         * non-zero => scan was truncated, so results may not be complete
         */
        private int mFlags;
        /**
         * Indicates the buckets that were scanned to generate these results.
         * This is not relevant to WifiScanner API users and is used internally.
         * {@hide}
         */
        private int mBucketsScanned;
        /**
         * Indicates that the scan results received are as a result of a scan of all available
         * channels. This should only be expected to function for single scans.
         * {@hide}
         */
        private boolean mAllChannelsScanned;
        /** all scan results discovered in this scan, sorted by timestamp in ascending order */
        private ScanResult mResults[];

        ScanData() {}

        public ScanData(int id, int flags, ScanResult[] results) {
            mId = id;
            mFlags = flags;
            mResults = results;
        }

        /** {@hide} */
        public ScanData(int id, int flags, int bucketsScanned, boolean allChannelsScanned,
                ScanResult[] results) {
            mId = id;
            mFlags = flags;
            mBucketsScanned = bucketsScanned;
            mAllChannelsScanned = allChannelsScanned;
            mResults = results;
        }

        public ScanData(ScanData s) {
            mId = s.mId;
            mFlags = s.mFlags;
            mBucketsScanned = s.mBucketsScanned;
            mAllChannelsScanned = s.mAllChannelsScanned;
            mResults = new ScanResult[s.mResults.length];
            for (int i = 0; i < s.mResults.length; i++) {
                ScanResult result = s.mResults[i];
                ScanResult newResult = new ScanResult(result);
                mResults[i] = newResult;
            }
        }

        public int getId() {
            return mId;
        }

        public int getFlags() {
            return mFlags;
        }

        /** {@hide} */
        public int getBucketsScanned() {
            return mBucketsScanned;
        }

        /** {@hide} */
        public boolean isAllChannelsScanned() {
            return mAllChannelsScanned;
        }

        public ScanResult[] getResults() {
            return mResults;
        }

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            if (mResults != null) {
                dest.writeInt(mId);
                dest.writeInt(mFlags);
                dest.writeInt(mBucketsScanned);
                dest.writeInt(mAllChannelsScanned ? 1 : 0);
                dest.writeInt(mResults.length);
                for (int i = 0; i < mResults.length; i++) {
                    ScanResult result = mResults[i];
                    result.writeToParcel(dest, flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<ScanData> CREATOR =
                new Creator<ScanData>() {
                    public ScanData createFromParcel(Parcel in) {
                        int id = in.readInt();
                        int flags = in.readInt();
                        int bucketsScanned = in.readInt();
                        boolean allChannelsScanned = in.readInt() != 0;
                        int n = in.readInt();
                        ScanResult results[] = new ScanResult[n];
                        for (int i = 0; i < n; i++) {
                            results[i] = ScanResult.CREATOR.createFromParcel(in);
                        }
                        return new ScanData(id, flags, bucketsScanned, allChannelsScanned, results);
                    }

                    public ScanData[] newArray(int size) {
                        return new ScanData[size];
                    }
                };
    }

    public static class ParcelableScanData implements Parcelable {

        public ScanData mResults[];

        public ParcelableScanData(ScanData[] results) {
            mResults = results;
        }

        public ScanData[] getResults() {
            return mResults;
        }

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            if (mResults != null) {
                dest.writeInt(mResults.length);
                for (int i = 0; i < mResults.length; i++) {
                    ScanData result = mResults[i];
                    result.writeToParcel(dest, flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<ParcelableScanData> CREATOR =
                new Creator<ParcelableScanData>() {
                    public ParcelableScanData createFromParcel(Parcel in) {
                        int n = in.readInt();
                        ScanData results[] = new ScanData[n];
                        for (int i = 0; i < n; i++) {
                            results[i] = ScanData.CREATOR.createFromParcel(in);
                        }
                        return new ParcelableScanData(results);
                    }

                    public ParcelableScanData[] newArray(int size) {
                        return new ParcelableScanData[size];
                    }
                };
    }

    public static class ParcelableScanResults implements Parcelable {

        public ScanResult mResults[];

        public ParcelableScanResults(ScanResult[] results) {
            mResults = results;
        }

        public ScanResult[] getResults() {
            return mResults;
        }

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            if (mResults != null) {
                dest.writeInt(mResults.length);
                for (int i = 0; i < mResults.length; i++) {
                    ScanResult result = mResults[i];
                    result.writeToParcel(dest, flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<ParcelableScanResults> CREATOR =
                new Creator<ParcelableScanResults>() {
                    public ParcelableScanResults createFromParcel(Parcel in) {
                        int n = in.readInt();
                        ScanResult results[] = new ScanResult[n];
                        for (int i = 0; i < n; i++) {
                            results[i] = ScanResult.CREATOR.createFromParcel(in);
                        }
                        return new ParcelableScanResults(results);
                    }

                    public ParcelableScanResults[] newArray(int size) {
                        return new ParcelableScanResults[size];
                    }
                };
    }

    /** {@hide} */
    public static final String PNO_PARAMS_PNO_SETTINGS_KEY = "PnoSettings";
    /** {@hide} */
    public static final String PNO_PARAMS_SCAN_SETTINGS_KEY = "ScanSettings";
    /**
     * PNO scan configuration parameters to be sent to {@link #startPnoScan}.
     * Note: This structure needs to be in sync with |wifi_epno_params| struct in gscan HAL API.
     * {@hide}
     */
    public static class PnoSettings implements Parcelable {
        /**
         * Pno network to be added to the PNO scan filtering.
         * {@hide}
         */
        public static class PnoNetwork {
            /*
             * Pno flags bitmask to be set in {@link #PnoNetwork.flags}
             */
            /** Whether directed scan needs to be performed (for hidden SSIDs) */
            public static final byte FLAG_DIRECTED_SCAN = (1 << 0);
            /** Whether PNO event shall be triggered if the network is found on A band */
            public static final byte FLAG_A_BAND = (1 << 1);
            /** Whether PNO event shall be triggered if the network is found on G band */
            public static final byte FLAG_G_BAND = (1 << 2);
            /**
             * Whether strict matching is required
             * If required then the firmware must store the network's SSID and not just a hash
             */
            public static final byte FLAG_STRICT_MATCH = (1 << 3);
            /**
             * If this SSID should be considered the same network as the currently connected
             * one for scoring.
             */
            public static final byte FLAG_SAME_NETWORK = (1 << 4);

            /*
             * Code for matching the beacon AUTH IE - additional codes. Bitmask to be set in
             * {@link #PnoNetwork.authBitField}
             */
            /** Open Network */
            public static final byte AUTH_CODE_OPEN = (1 << 0);
            /** WPA_PSK or WPA2PSK */
            public static final byte AUTH_CODE_PSK = (1 << 1);
            /** any EAPOL */
            public static final byte AUTH_CODE_EAPOL = (1 << 2);

            /** SSID of the network */
            public String ssid;
            /** Network ID in wpa_supplicant */
            public int networkId;
            /** Assigned priority for the network */
            public int priority;
            /** Bitmask of the FLAG_XXX */
            public byte flags;
            /** Bitmask of the ATUH_XXX */
            public byte authBitField;

            /**
             * default constructor for PnoNetwork
             */
            public PnoNetwork(String ssid) {
                this.ssid = ssid;
                flags = 0;
                authBitField = 0;
            }
        }

        /** Connected vs Disconnected PNO flag {@hide} */
        public boolean isConnected;
        /** Minimum 5GHz RSSI for a BSSID to be considered */
        public int min5GHzRssi;
        /** Minimum 2.4GHz RSSI for a BSSID to be considered */
        public int min24GHzRssi;
        /** Maximum score that a network can have before bonuses */
        public int initialScoreMax;
        /**
         *  Only report when there is a network's score this much higher
         *  than the current connection.
         */
        public int currentConnectionBonus;
        /** score bonus for all networks with the same network flag */
        public int sameNetworkBonus;
        /** score bonus for networks that are not open */
        public int secureBonus;
        /** 5GHz RSSI score bonus (applied to all 5GHz networks) */
        public int band5GHzBonus;
        /** Pno Network filter list */
        public PnoNetwork[] networkList;

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(isConnected ? 1 : 0);
            dest.writeInt(min5GHzRssi);
            dest.writeInt(min24GHzRssi);
            dest.writeInt(initialScoreMax);
            dest.writeInt(currentConnectionBonus);
            dest.writeInt(sameNetworkBonus);
            dest.writeInt(secureBonus);
            dest.writeInt(band5GHzBonus);
            if (networkList != null) {
                dest.writeInt(networkList.length);
                for (int i = 0; i < networkList.length; i++) {
                    dest.writeString(networkList[i].ssid);
                    dest.writeInt(networkList[i].networkId);
                    dest.writeInt(networkList[i].priority);
                    dest.writeByte(networkList[i].flags);
                    dest.writeByte(networkList[i].authBitField);
                }
            } else {
                dest.writeInt(0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<PnoSettings> CREATOR =
                new Creator<PnoSettings>() {
                    public PnoSettings createFromParcel(Parcel in) {
                        PnoSettings settings = new PnoSettings();
                        settings.isConnected = in.readInt() == 1;
                        settings.min5GHzRssi = in.readInt();
                        settings.min24GHzRssi = in.readInt();
                        settings.initialScoreMax = in.readInt();
                        settings.currentConnectionBonus = in.readInt();
                        settings.sameNetworkBonus = in.readInt();
                        settings.secureBonus = in.readInt();
                        settings.band5GHzBonus = in.readInt();
                        int numNetworks = in.readInt();
                        settings.networkList = new PnoNetwork[numNetworks];
                        for (int i = 0; i < numNetworks; i++) {
                            String ssid = in.readString();
                            PnoNetwork network = new PnoNetwork(ssid);
                            network.networkId = in.readInt();
                            network.priority = in.readInt();
                            network.flags = in.readByte();
                            network.authBitField = in.readByte();
                            settings.networkList[i] = network;
                        }
                        return settings;
                    }

                    public PnoSettings[] newArray(int size) {
                        return new PnoSettings[size];
                    }
                };

    }

    /**
     * interface to get scan events on; specify this on {@link #startBackgroundScan} or
     * {@link #startScan}
     */
    public interface ScanListener extends ActionListener {
        /**
         * Framework co-ordinates scans across multiple apps; so it may not give exactly the
         * same period requested. If period of a scan is changed; it is reported by this event.
         */
        public void onPeriodChanged(int periodInMs);
        /**
         * reports results retrieved from background scan and single shot scans
         */
        public void onResults(ScanData[] results);
        /**
         * reports full scan result for each access point found in scan
         */
        public void onFullResult(ScanResult fullScanResult);
    }

    /**
     * interface to get PNO scan events on; specify this on {@link #startDisconnectedPnoScan} and
     * {@link #startConnectedPnoScan}.
     * {@hide}
     */
    public interface PnoScanListener extends ScanListener {
        /**
         * Invoked when one of the PNO networks are found in scan results.
         */
        void onPnoNetworkFound(ScanResult[] results);
    }

    /**
     * Register a listener that will receive results from all single scans
     * Either the onSuccess/onFailure will be called once when the listener is registered. After
     * (assuming onSuccess was called) all subsequent single scan results will be delivered to the
     * listener. It is possible that onFullResult will not be called for all results of the first
     * scan if the listener was registered during the scan.
     *
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this request, and must also be specified to cancel the request.
     *                 Multiple requests should also not share this object.
     * {@hide}
     */
    public void registerScanListener(ScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_REGISTER_SCAN_LISTENER, 0, key);
    }

    /**
     * Deregister a listener for ongoing single scans
     * @param listener specifies which scan to cancel; must be same object as passed in {@link
     *  #registerScanListener}
     * {@hide}
     */
    public void deregisterScanListener(ScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = removeListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_DEREGISTER_SCAN_LISTENER, 0, key);
    }

    /** start wifi scan in background
     * @param settings specifies various parameters for the scan; for more information look at
     * {@link ScanSettings}
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this scan, and must also be specified to cancel the scan. Multiple
     *                 scans should also not share this object.
     */
    public void startBackgroundScan(ScanSettings settings, ScanListener listener) {
        startBackgroundScan(settings, listener, null);
    }

    /** start wifi scan in background
     * @param settings specifies various parameters for the scan; for more information look at
     * {@link ScanSettings}
     * @param workSource WorkSource to blame for power usage
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this scan, and must also be specified to cancel the scan. Multiple
     *                 scans should also not share this object.
     */
    public void startBackgroundScan(ScanSettings settings, ScanListener listener,
            WorkSource workSource) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        Bundle scanParams = new Bundle();
        scanParams.putParcelable(SCAN_PARAMS_SCAN_SETTINGS_KEY, settings);
        scanParams.putParcelable(SCAN_PARAMS_WORK_SOURCE_KEY, workSource);
        mAsyncChannel.sendMessage(CMD_START_BACKGROUND_SCAN, 0, key, scanParams);
    }

    /**
     * stop an ongoing wifi scan
     * @param listener specifies which scan to cancel; must be same object as passed in {@link
     *  #startBackgroundScan}
     */
    public void stopBackgroundScan(ScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = removeListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_STOP_BACKGROUND_SCAN, 0, key);
    }
    /**
     * reports currently available scan results on appropriate listeners
     * @return true if all scan results were reported correctly
     */
    public boolean getScanResults() {
        validateChannel();
        Message reply = mAsyncChannel.sendMessageSynchronously(CMD_GET_SCAN_RESULTS, 0);
        return reply.what == CMD_OP_SUCCEEDED;
    }

    /**
     * starts a single scan and reports results asynchronously
     * @param settings specifies various parameters for the scan; for more information look at
     * {@link ScanSettings}
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this scan, and must also be specified to cancel the scan. Multiple
     *                 scans should also not share this object.
     */
    public void startScan(ScanSettings settings, ScanListener listener) {
        startScan(settings, listener, null);
    }

    /**
     * starts a single scan and reports results asynchronously
     * @param settings specifies various parameters for the scan; for more information look at
     * {@link ScanSettings}
     * @param workSource WorkSource to blame for power usage
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this scan, and must also be specified to cancel the scan. Multiple
     *                 scans should also not share this object.
     */
    public void startScan(ScanSettings settings, ScanListener listener, WorkSource workSource) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        Bundle scanParams = new Bundle();
        scanParams.putParcelable(SCAN_PARAMS_SCAN_SETTINGS_KEY, settings);
        scanParams.putParcelable(SCAN_PARAMS_WORK_SOURCE_KEY, workSource);
        mAsyncChannel.sendMessage(CMD_START_SINGLE_SCAN, 0, key, scanParams);
    }

    /**
     * stops an ongoing single shot scan; only useful after {@link #startScan} if onResults()
     * hasn't been called on the listener, ignored otherwise
     * @param listener
     */
    public void stopScan(ScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = removeListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_STOP_SINGLE_SCAN, 0, key);
    }

    private void startPnoScan(ScanSettings scanSettings, PnoSettings pnoSettings, int key) {
        // Bundle up both the settings and send it across.
        Bundle pnoParams = new Bundle();
        // Set the PNO scan flag.
        scanSettings.isPnoScan = true;
        pnoParams.putParcelable(PNO_PARAMS_SCAN_SETTINGS_KEY, scanSettings);
        pnoParams.putParcelable(PNO_PARAMS_PNO_SETTINGS_KEY, pnoSettings);
        mAsyncChannel.sendMessage(CMD_START_PNO_SCAN, 0, key, pnoParams);
    }
    /**
     * Start wifi connected PNO scan
     * @param scanSettings specifies various parameters for the scan; for more information look at
     * {@link ScanSettings}
     * @param pnoSettings specifies various parameters for PNO; for more information look at
     * {@link PnoSettings}
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this scan, and must also be specified to cancel the scan. Multiple
     *                 scans should also not share this object.
     * {@hide}
     */
    public void startConnectedPnoScan(ScanSettings scanSettings, PnoSettings pnoSettings,
            PnoScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        Preconditions.checkNotNull(pnoSettings, "pnoSettings cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        pnoSettings.isConnected = true;
        startPnoScan(scanSettings, pnoSettings, key);
    }
    /**
     * Start wifi disconnected PNO scan
     * @param scanSettings specifies various parameters for the scan; for more information look at
     * {@link ScanSettings}
     * @param pnoSettings specifies various parameters for PNO; for more information look at
     * {@link PnoSettings}
     * @param listener specifies the object to report events to. This object is also treated as a
     *                 key for this scan, and must also be specified to cancel the scan. Multiple
     *                 scans should also not share this object.
     * {@hide}
     */
    public void startDisconnectedPnoScan(ScanSettings scanSettings, PnoSettings pnoSettings,
            PnoScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        Preconditions.checkNotNull(pnoSettings, "pnoSettings cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        pnoSettings.isConnected = false;
        startPnoScan(scanSettings, pnoSettings, key);
    }
    /**
     * Stop an ongoing wifi PNO scan
     * @param listener specifies which scan to cancel; must be same object as passed in {@link
     *  #startPnoScan}
     * TODO(rpius): Check if we can remove pnoSettings param in stop.
     * {@hide}
     */
    public void stopPnoScan(ScanListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = removeListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_STOP_PNO_SCAN, 0, key);
    }

    /** specifies information about an access point of interest */
    public static class BssidInfo {
        /** bssid of the access point; in XX:XX:XX:XX:XX:XX format */
        public String bssid;
        /** low signal strength threshold; more information at {@link ScanResult#level} */
        public int low;                                            /* minimum RSSI */
        /** high signal threshold; more information at {@link ScanResult#level} */
        public int high;                                           /* maximum RSSI */
        /** channel frequency (in KHz) where you may find this BSSID */
        public int frequencyHint;
    }

    /** @hide */
    @SystemApi
    public static class WifiChangeSettings implements Parcelable {
        public int rssiSampleSize;                          /* sample size for RSSI averaging */
        public int lostApSampleSize;                        /* samples to confirm AP's loss */
        public int unchangedSampleSize;                     /* samples to confirm no change */
        public int minApsBreachingThreshold;                /* change threshold to trigger event */
        public int periodInMs;                              /* scan period in millisecond */
        public BssidInfo[] bssidInfos;

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(rssiSampleSize);
            dest.writeInt(lostApSampleSize);
            dest.writeInt(unchangedSampleSize);
            dest.writeInt(minApsBreachingThreshold);
            dest.writeInt(periodInMs);
            if (bssidInfos != null) {
                dest.writeInt(bssidInfos.length);
                for (int i = 0; i < bssidInfos.length; i++) {
                    BssidInfo info = bssidInfos[i];
                    dest.writeString(info.bssid);
                    dest.writeInt(info.low);
                    dest.writeInt(info.high);
                    dest.writeInt(info.frequencyHint);
                }
            } else {
                dest.writeInt(0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<WifiChangeSettings> CREATOR =
                new Creator<WifiChangeSettings>() {
                    public WifiChangeSettings createFromParcel(Parcel in) {
                        WifiChangeSettings settings = new WifiChangeSettings();
                        settings.rssiSampleSize = in.readInt();
                        settings.lostApSampleSize = in.readInt();
                        settings.unchangedSampleSize = in.readInt();
                        settings.minApsBreachingThreshold = in.readInt();
                        settings.periodInMs = in.readInt();
                        int len = in.readInt();
                        settings.bssidInfos = new BssidInfo[len];
                        for (int i = 0; i < len; i++) {
                            BssidInfo info = new BssidInfo();
                            info.bssid = in.readString();
                            info.low = in.readInt();
                            info.high = in.readInt();
                            info.frequencyHint = in.readInt();
                            settings.bssidInfos[i] = info;
                        }
                        return settings;
                    }

                    public WifiChangeSettings[] newArray(int size) {
                        return new WifiChangeSettings[size];
                    }
                };

    }

    /** configure WifiChange detection
     * @param rssiSampleSize number of samples used for RSSI averaging
     * @param lostApSampleSize number of samples to confirm an access point's loss
     * @param unchangedSampleSize number of samples to confirm there are no changes
     * @param minApsBreachingThreshold minimum number of access points that need to be
     *                                 out of range to detect WifiChange
     * @param periodInMs indicates period of scan to find changes
     * @param bssidInfos access points to watch
     */
    public void configureWifiChange(
            int rssiSampleSize,                             /* sample size for RSSI averaging */
            int lostApSampleSize,                           /* samples to confirm AP's loss */
            int unchangedSampleSize,                        /* samples to confirm no change */
            int minApsBreachingThreshold,                   /* change threshold to trigger event */
            int periodInMs,                                 /* period of scan */
            BssidInfo[] bssidInfos                          /* signal thresholds to crosss */
            )
    {
        validateChannel();

        WifiChangeSettings settings = new WifiChangeSettings();
        settings.rssiSampleSize = rssiSampleSize;
        settings.lostApSampleSize = lostApSampleSize;
        settings.unchangedSampleSize = unchangedSampleSize;
        settings.minApsBreachingThreshold = minApsBreachingThreshold;
        settings.periodInMs = periodInMs;
        settings.bssidInfos = bssidInfos;

        configureWifiChange(settings);
    }

    /**
     * interface to get wifi change events on; use this on {@link #startTrackingWifiChange}
     */
    public interface WifiChangeListener extends ActionListener {
        /** indicates that changes were detected in wifi environment
         * @param results indicate the access points that exhibited change
         */
        public void onChanging(ScanResult[] results);           /* changes are found */
        /** indicates that no wifi changes are being detected for a while
         * @param results indicate the access points that are bing monitored for change
         */
        public void onQuiescence(ScanResult[] results);         /* changes settled down */
    }

    /**
     * track changes in wifi environment
     * @param listener object to report events on; this object must be unique and must also be
     *                 provided on {@link #stopTrackingWifiChange}
     */
    public void startTrackingWifiChange(WifiChangeListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_START_TRACKING_CHANGE, 0, key);
    }

    /**
     * stop tracking changes in wifi environment
     * @param listener object that was provided to report events on {@link
     * #stopTrackingWifiChange}
     */
    public void stopTrackingWifiChange(WifiChangeListener listener) {
        int key = removeListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_STOP_TRACKING_CHANGE, 0, key);
    }

    /** @hide */
    @SystemApi
    public void configureWifiChange(WifiChangeSettings settings) {
        validateChannel();
        mAsyncChannel.sendMessage(CMD_CONFIGURE_WIFI_CHANGE, 0, 0, settings);
    }

    /** interface to receive hotlist events on; use this on {@link #setHotlist} */
    public static interface BssidListener extends ActionListener {
        /** indicates that access points were found by on going scans
         * @param results list of scan results, one for each access point visible currently
         */
        public void onFound(ScanResult[] results);
        /** indicates that access points were missed by on going scans
         * @param results list of scan results, for each access point that is not visible anymore
         */
        public void onLost(ScanResult[] results);
    }

    /** @hide */
    @SystemApi
    public static class HotlistSettings implements Parcelable {
        public BssidInfo[] bssidInfos;
        public int apLostThreshold;

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(apLostThreshold);

            if (bssidInfos != null) {
                dest.writeInt(bssidInfos.length);
                for (int i = 0; i < bssidInfos.length; i++) {
                    BssidInfo info = bssidInfos[i];
                    dest.writeString(info.bssid);
                    dest.writeInt(info.low);
                    dest.writeInt(info.high);
                    dest.writeInt(info.frequencyHint);
                }
            } else {
                dest.writeInt(0);
            }
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<HotlistSettings> CREATOR =
                new Creator<HotlistSettings>() {
                    public HotlistSettings createFromParcel(Parcel in) {
                        HotlistSettings settings = new HotlistSettings();
                        settings.apLostThreshold = in.readInt();
                        int n = in.readInt();
                        settings.bssidInfos = new BssidInfo[n];
                        for (int i = 0; i < n; i++) {
                            BssidInfo info = new BssidInfo();
                            info.bssid = in.readString();
                            info.low = in.readInt();
                            info.high = in.readInt();
                            info.frequencyHint = in.readInt();
                            settings.bssidInfos[i] = info;
                        }
                        return settings;
                    }

                    public HotlistSettings[] newArray(int size) {
                        return new HotlistSettings[size];
                    }
                };
    }

    /**
     * set interesting access points to find
     * @param bssidInfos access points of interest
     * @param apLostThreshold number of scans needed to indicate that AP is lost
     * @param listener object provided to report events on; this object must be unique and must
     *                 also be provided on {@link #stopTrackingBssids}
     */
    public void startTrackingBssids(BssidInfo[] bssidInfos,
                                    int apLostThreshold, BssidListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = addListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        HotlistSettings settings = new HotlistSettings();
        settings.bssidInfos = bssidInfos;
        settings.apLostThreshold = apLostThreshold;
        mAsyncChannel.sendMessage(CMD_SET_HOTLIST, 0, key, settings);
    }

    /**
     * remove tracking of interesting access points
     * @param listener same object provided in {@link #startTrackingBssids}
     */
    public void stopTrackingBssids(BssidListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        int key = removeListener(listener);
        if (key == INVALID_KEY) return;
        validateChannel();
        mAsyncChannel.sendMessage(CMD_RESET_HOTLIST, 0, key);
    }


    /* private members and methods */

    private static final String TAG = "WifiScanner";
    private static final boolean DBG = false;

    /* commands for Wifi Service */
    private static final int BASE = Protocol.BASE_WIFI_SCANNER;

    /** @hide */
    public static final int CMD_SCAN                        = BASE + 0;
    /** @hide */
    public static final int CMD_START_BACKGROUND_SCAN       = BASE + 2;
    /** @hide */
    public static final int CMD_STOP_BACKGROUND_SCAN        = BASE + 3;
    /** @hide */
    public static final int CMD_GET_SCAN_RESULTS            = BASE + 4;
    /** @hide */
    public static final int CMD_SCAN_RESULT                 = BASE + 5;
    /** @hide */
    public static final int CMD_SET_HOTLIST                 = BASE + 6;
    /** @hide */
    public static final int CMD_RESET_HOTLIST               = BASE + 7;
    /** @hide */
    public static final int CMD_AP_FOUND                    = BASE + 9;
    /** @hide */
    public static final int CMD_AP_LOST                     = BASE + 10;
    /** @hide */
    public static final int CMD_START_TRACKING_CHANGE       = BASE + 11;
    /** @hide */
    public static final int CMD_STOP_TRACKING_CHANGE        = BASE + 12;
    /** @hide */
    public static final int CMD_CONFIGURE_WIFI_CHANGE       = BASE + 13;
    /** @hide */
    public static final int CMD_WIFI_CHANGE_DETECTED        = BASE + 15;
    /** @hide */
    public static final int CMD_WIFI_CHANGES_STABILIZED     = BASE + 16;
    /** @hide */
    public static final int CMD_OP_SUCCEEDED                = BASE + 17;
    /** @hide */
    public static final int CMD_OP_FAILED                   = BASE + 18;
    /** @hide */
    public static final int CMD_PERIOD_CHANGED              = BASE + 19;
    /** @hide */
    public static final int CMD_FULL_SCAN_RESULT            = BASE + 20;
    /** @hide */
    public static final int CMD_START_SINGLE_SCAN           = BASE + 21;
    /** @hide */
    public static final int CMD_STOP_SINGLE_SCAN            = BASE + 22;
    /** @hide */
    public static final int CMD_SINGLE_SCAN_COMPLETED       = BASE + 23;
    /** @hide */
    public static final int CMD_START_PNO_SCAN              = BASE + 24;
    /** @hide */
    public static final int CMD_STOP_PNO_SCAN               = BASE + 25;
    /** @hide */
    public static final int CMD_PNO_NETWORK_FOUND           = BASE + 26;
    /** @hide */
    public static final int CMD_REGISTER_SCAN_LISTENER      = BASE + 27;
    /** @hide */
    public static final int CMD_DEREGISTER_SCAN_LISTENER    = BASE + 28;

    private Context mContext;
    private IWifiScanner mService;

    private static final int INVALID_KEY = 0;
    private int mListenerKey = 1;

    private final SparseArray mListenerMap = new SparseArray();
    private final Object mListenerMapLock = new Object();

    private AsyncChannel mAsyncChannel;
    private final Handler mInternalHandler;

    /**
     * Create a new WifiScanner instance.
     * Applications will almost always want to use
     * {@link android.content.Context#getSystemService Context.getSystemService()} to retrieve
     * the standard {@link android.content.Context#WIFI_SERVICE Context.WIFI_SERVICE}.
     * @param context the application context
     * @param service the Binder interface
     * @param looper the Looper used to deliver callbacks
     * @hide
     */
    public WifiScanner(Context context, IWifiScanner service, Looper looper) {
        mContext = context;
        mService = service;

        Messenger messenger = null;
        try {
            messenger = mService.getMessenger();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        if (messenger == null) {
            throw new IllegalStateException("getMessenger() returned null!  This is invalid.");
        }

        mAsyncChannel = new AsyncChannel();

        mInternalHandler = new ServiceHandler(looper);
        mAsyncChannel.connectSync(mContext, mInternalHandler, messenger);
        // We cannot use fullyConnectSync because it sends the FULL_CONNECTION message
        // synchronously, which causes WifiScanningService to receive the wrong replyTo value.
        mAsyncChannel.sendMessage(AsyncChannel.CMD_CHANNEL_FULL_CONNECTION);
    }

    private void validateChannel() {
        if (mAsyncChannel == null) throw new IllegalStateException(
                "No permission to access and change wifi or a bad initialization");
    }

    // Add a listener into listener map. If the listener already exists, return INVALID_KEY and
    // send an error message to internal handler; Otherwise add the listener to the listener map and
    // return the key of the listener.
    private int addListener(ActionListener listener) {
        synchronized (mListenerMapLock) {
            boolean keyExists = (getListenerKey(listener) != INVALID_KEY);
            // Note we need to put the listener into listener map even if it's a duplicate as the
            // internal handler will need the key to find the listener. In case of duplicates,
            // removing duplicate key logic will be handled in internal handler.
            int key = putListener(listener);
            if (keyExists) {
                if (DBG) Log.d(TAG, "listener key already exists");
                OperationResult operationResult = new OperationResult(REASON_DUPLICATE_REQEUST,
                        "Outstanding request with same key not stopped yet");
                Message message = Message.obtain(mInternalHandler, CMD_OP_FAILED, 0, key,
                        operationResult);
                message.sendToTarget();
                return INVALID_KEY;
            } else {
                return key;
            }
        }
    }

    private int putListener(Object listener) {
        if (listener == null) return INVALID_KEY;
        int key;
        synchronized (mListenerMapLock) {
            do {
                key = mListenerKey++;
            } while (key == INVALID_KEY);
            mListenerMap.put(key, listener);
        }
        return key;
    }

    private Object getListener(int key) {
        if (key == INVALID_KEY) return null;
        synchronized (mListenerMapLock) {
            Object listener = mListenerMap.get(key);
            return listener;
        }
    }

    private int getListenerKey(Object listener) {
        if (listener == null) return INVALID_KEY;
        synchronized (mListenerMapLock) {
            int index = mListenerMap.indexOfValue(listener);
            if (index == -1) {
                return INVALID_KEY;
            } else {
                return mListenerMap.keyAt(index);
            }
        }
    }

    private Object removeListener(int key) {
        if (key == INVALID_KEY) return null;
        synchronized (mListenerMapLock) {
            Object listener = mListenerMap.get(key);
            mListenerMap.remove(key);
            return listener;
        }
    }

    private int removeListener(Object listener) {
        int key = getListenerKey(listener);
        if (key == INVALID_KEY) {
            Log.e(TAG, "listener cannot be found");
            return key;
        }
        synchronized (mListenerMapLock) {
            mListenerMap.remove(key);
            return key;
        }
    }

    /** @hide */
    public static class OperationResult implements Parcelable {
        public int reason;
        public String description;

        public OperationResult(int reason, String description) {
            this.reason = reason;
            this.description = description;
        }

        /** Implement the Parcelable interface {@hide} */
        public int describeContents() {
            return 0;
        }

        /** Implement the Parcelable interface {@hide} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(reason);
            dest.writeString(description);
        }

        /** Implement the Parcelable interface {@hide} */
        public static final Creator<OperationResult> CREATOR =
                new Creator<OperationResult>() {
                    public OperationResult createFromParcel(Parcel in) {
                        int reason = in.readInt();
                        String description = in.readString();
                        return new OperationResult(reason, description);
                    }

                    public OperationResult[] newArray(int size) {
                        return new OperationResult[size];
                    }
                };
    }

    private class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED:
                    return;
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                    Log.e(TAG, "Channel connection lost");
                    // This will cause all further async API calls on the WifiManager
                    // to fail and throw an exception
                    mAsyncChannel = null;
                    getLooper().quit();
                    return;
            }

            Object listener = getListener(msg.arg2);

            if (listener == null) {
                if (DBG) Log.d(TAG, "invalid listener key = " + msg.arg2);
                return;
            } else {
                if (DBG) Log.d(TAG, "listener key = " + msg.arg2);
            }

            switch (msg.what) {
                    /* ActionListeners grouped together */
                case CMD_OP_SUCCEEDED :
                    ((ActionListener) listener).onSuccess();
                    break;
                case CMD_OP_FAILED : {
                        OperationResult result = (OperationResult)msg.obj;
                        ((ActionListener) listener).onFailure(result.reason, result.description);
                        removeListener(msg.arg2);
                    }
                    break;
                case CMD_SCAN_RESULT :
                    ((ScanListener) listener).onResults(
                            ((ParcelableScanData) msg.obj).getResults());
                    return;
                case CMD_FULL_SCAN_RESULT :
                    ScanResult result = (ScanResult) msg.obj;
                    ((ScanListener) listener).onFullResult(result);
                    return;
                case CMD_PERIOD_CHANGED:
                    ((ScanListener) listener).onPeriodChanged(msg.arg1);
                    return;
                case CMD_AP_FOUND:
                    ((BssidListener) listener).onFound(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                case CMD_AP_LOST:
                    ((BssidListener) listener).onLost(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                case CMD_WIFI_CHANGE_DETECTED:
                    ((WifiChangeListener) listener).onChanging(
                            ((ParcelableScanResults) msg.obj).getResults());
                   return;
                case CMD_WIFI_CHANGES_STABILIZED:
                    ((WifiChangeListener) listener).onQuiescence(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                case CMD_SINGLE_SCAN_COMPLETED:
                    if (DBG) Log.d(TAG, "removing listener for single scan");
                    removeListener(msg.arg2);
                    break;
                case CMD_PNO_NETWORK_FOUND:
                    ((PnoScanListener) listener).onPnoNetworkFound(
                            ((ParcelableScanResults) msg.obj).getResults());
                    return;
                default:
                    if (DBG) Log.d(TAG, "Ignoring message " + msg.what);
                    return;
            }
        }
    }
}
