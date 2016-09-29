/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.MobileSignalController.MobileIconGroup;

class TelephonyIcons {
    //***** Signal strength icons

    static final int TELEPHONY_NUM_LEVELS = 5;

    //GSM/UMTS
    static final int TELEPHONY_NO_NETWORK = R.drawable.stat_sys_signal_null;

    static final int[][] TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };

    static final int QS_TELEPHONY_NO_NETWORK = R.drawable.ic_qs_signal_no_signal;

    static final int[][] QS_TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.ic_qs_signal_0,
          R.drawable.ic_qs_signal_1,
          R.drawable.ic_qs_signal_2,
          R.drawable.ic_qs_signal_3,
          R.drawable.ic_qs_signal_4 },
        { R.drawable.ic_qs_signal_full_0,
          R.drawable.ic_qs_signal_full_1,
          R.drawable.ic_qs_signal_full_2,
          R.drawable.ic_qs_signal_full_3,
          R.drawable.ic_qs_signal_full_4 }
    };

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };

    //Default roaming icons with R indicator
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_R = {
        { R.drawable.stat_sys_signal_0_default_roam,
          R.drawable.stat_sys_signal_1_default_roam,
          R.drawable.stat_sys_signal_2_default_roam,
          R.drawable.stat_sys_signal_3_default_roam,
          R.drawable.stat_sys_signal_4_default_roam },
        { R.drawable.stat_sys_signal_0_default_fully_roam,
          R.drawable.stat_sys_signal_1_default_fully_roam,
          R.drawable.stat_sys_signal_2_default_fully_roam,
          R.drawable.stat_sys_signal_3_default_fully_roam,
          R.drawable.stat_sys_signal_4_default_fully_roam }
    };

    //CarrierNetworkChange
    static final int[][] TELEPHONY_CARRIER_NETWORK_CHANGE = {
            { R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation },
            { R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation,
              R.drawable.stat_sys_signal_carrier_network_change_animation }
        };

    static final int[][] QS_TELEPHONY_CARRIER_NETWORK_CHANGE = {
        { R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation },
        { R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation,
          R.drawable.ic_qs_signal_carrier_network_change_animation }
    };

    static final int QS_DATA_R = R.drawable.ic_qs_signal_r;

    //***** Data connection icons

    //GSM/UMTS
    static final int[][] DATA_G = {
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g },
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g }
        };

    static final int QS_DATA_G = R.drawable.ic_qs_signal_g;

    static final int[][] DATA_3G = {
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g }
        };

    static final int QS_DATA_3G = R.drawable.ic_qs_signal_3g;
    static final int QS_DATA_3G_PLUS = R.drawable.ic_qs_signal_3g_plus;

    static final int[][] DATA_E = {
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e }
        };

    static final int QS_DATA_E = R.drawable.ic_qs_signal_e;

    //3.5G
    static final int[][] DATA_H = {
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h }
    };

    static final int QS_DATA_H = R.drawable.ic_qs_signal_h;
    static final int QS_DATA_H_PLUS = R.drawable.ic_qs_signal_h_plus;

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][] DATA_1X = {
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x }
            };

    static final int QS_DATA_1X = R.drawable.ic_qs_signal_1x;

    // LTE and eHRPD
    static final int[][] DATA_4G = {
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g },
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g }
        };

    static final int QS_DATA_4G = R.drawable.ic_qs_signal_4g;

    static final int[][] DATA_4G_PLUS = {
            { R.drawable.stat_sys_data_fully_connected_4g_plus,
              R.drawable.stat_sys_data_fully_connected_4g_plus,
              R.drawable.stat_sys_data_fully_connected_4g_plus,
              R.drawable.stat_sys_data_fully_connected_4g_plus },
            { R.drawable.stat_sys_data_fully_connected_4g_plus,
              R.drawable.stat_sys_data_fully_connected_4g_plus,
              R.drawable.stat_sys_data_fully_connected_4g_plus,
              R.drawable.stat_sys_data_fully_connected_4g_plus }
    };

    static final int QS_DATA_4G_LTE = R.drawable.ic_qs_signal_4g_lte;

    static final int QS_DATA_4G_PLUS = R.drawable.ic_qs_signal_4g_plus;

    // LTE branded "LTE"
    static final int[][] DATA_LTE = {
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte },
            { R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte,
                    R.drawable.stat_sys_data_fully_connected_lte }
    };

    static final int QS_DATA_LTE = R.drawable.ic_qs_signal_lte;
    static final int QS_DATA_LTE_PLUS = R.drawable.ic_qs_signal_lte_plus;

    static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_airplane_mode;
    static final int ROAMING_ICON = R.drawable.stat_sys_data_fully_connected_roam;
    static final int ICON_LTE = R.drawable.stat_sys_data_fully_connected_lte;
    static final int ICON_LTE_PLUS = R.drawable.stat_sys_data_fully_connected_lte_plus;
    static final int ICON_G = R.drawable.stat_sys_data_fully_connected_g;
    static final int ICON_E = R.drawable.stat_sys_data_fully_connected_e;
    static final int ICON_H = R.drawable.stat_sys_data_fully_connected_h;
    static final int ICON_H_PLUS = R.drawable.stat_sys_data_fully_connected_h_plus;
    static final int ICON_3G = R.drawable.stat_sys_data_fully_connected_3g;
    static final int ICON_3G_PLUS = R.drawable.stat_sys_data_fully_connected_3g_plus;
    static final int ICON_4G = R.drawable.stat_sys_data_fully_connected_4g;
    static final int ICON_4G_LTE = R.drawable.stat_sys_data_fully_connected_4g_lte;
    static final int ICON_4G_PLUS = R.drawable.stat_sys_data_fully_connected_4g_plus;
    static final int ICON_1X = R.drawable.stat_sys_data_fully_connected_1x;
    static final int ICON_CARRIER_NETWORK_CHANGE =
            R.drawable.stat_sys_signal_carrier_network_change_animation;

    static final int ICON_DATA_DISABLED = R.drawable.stat_sys_data_disabled;

    static final int QS_ICON_LTE = R.drawable.ic_qs_signal_lte;
    static final int QS_ICON_3G = R.drawable.ic_qs_signal_3g;
    static final int QS_ICON_4G = R.drawable.ic_qs_signal_4g;
    static final int QS_ICON_4G_LTE = R.drawable.ic_qs_signal_4g_lte;
    static final int QS_ICON_4G_PLUS = R.drawable.ic_qs_signal_4g_plus;
    static final int QS_ICON_1X = R.drawable.ic_qs_signal_1x;
    static final int QS_ICON_CARRIER_NETWORK_CHANGE =
            R.drawable.ic_qs_signal_carrier_network_change_animation;
    static final int DATA_TYPE_UNKNOWN = 0;
    static final int DATA_TYPE_G = 1;
    static final int DATA_TYPE_E = 2;
    static final int DATA_TYPE_2G = 3;
    static final int DATA_TYPE_3G = 4;
    static final int DATA_TYPE_4G = 5;
    static final int DATA_TYPE_H = 6;
    static final int DATA_TYPE_HP = 7;
    static final int DATA_TYPE_1X = 8;
    static final int DATA_TYPE_LTE = 9;
    static final int DATA_TYPE_4G_PLUS = 10;

    static final int SIGNAL_STRENGTH_TYPE_G = 0;
    static final int SIGNAL_STRENGTH_TYPE_E = 1;
    static final int SIGNAL_STRENGTH_TYPE_3G = 2;
    static final int SIGNAL_STRENGTH_TYPE_4G = 3;
    static final int SIGNAL_STRENGTH_TYPE_H = 4;
    static final int SIGNAL_STRENGTH_TYPE_HP = 5;
    static final int SIGNAL_STRENGTH_TYPE_1X = 6;
    static final int SIGNAL_STRENGTH_TYPE_CDMA = 7;
    static final int SIGNAL_STRENGTH_TYPE_UMTS = 8;

    static final boolean DEBUG = true;
    static final int DEFAULT_SUB = 0;
    static final int INET_TYPE_NUM = 2;
    static final int SIGNAL_LEVEL_NUM = SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
    static final String TAG = "TelephonyIcons";
    static final String NS = "com.android.systemui";

    static String[] mDataTypeArray, mDataTypeGenerationArray;
    static String[] mDataTypeDescriptionArray, mDataTypeGenerationDescArray;
    static String[] mDataActivityArray;
    static String[] mSignalStrengthArray, mSignalStrengthRoamingArray;
    static String[] mSignalNullArray;
    static String[] mSignalStrengthDesc;

    static int[] mSelectedDataTypeIcon;
    static int[] mSelectedQSDataTypeIcon;
    static String[] mSelectedDataTypeDesc;
    static int[] mSelectedDataActivityIndex;
    static int[] mSelectedSignalStreagthIndex;
    static SparseArray<Integer> mStacked2SingleIconLookup;

    private static Resources mRes;
    private static boolean isInitiated = false;

    static void readIconsFromXml(Context context) {
        if (isInitiated) {
            log(TAG, "readIconsFromXml, already read!");
            return;
        }

        mRes = context.getResources();
        try {
            mDataTypeArray = mRes.getStringArray(R.array.multi_data_type);
            mDataTypeDescriptionArray = mRes.getStringArray(
                    R.array.telephony_data_type_description);
            mDataTypeGenerationArray = mRes.getStringArray(
                    R.array.telephony_data_type_generation);
            mDataTypeGenerationDescArray = mRes.getStringArray(
                    R.array.telephony_data_type_generation_description);
            mDataActivityArray = mRes.getStringArray(R.array.multi_data_activity);
            mSignalStrengthArray = mRes.getStringArray(R.array.multi_signal_strength);
            mSignalStrengthRoamingArray = mRes.getStringArray(
                    R.array.multi_signal_strength_roaming);
            mSignalNullArray = mRes.getStringArray(R.array.multi_signal_null);
            mSignalStrengthDesc = mRes.getStringArray(R.array.signal_strength_description);
            initStacked2SingleIconLookup();
        } catch (android.content.res.Resources.NotFoundException e) {
            isInitiated = false;
            log(TAG, "readIconsFromXml, exception happened: " + e);
            return;
        }

        if (mSelectedDataTypeIcon == null
            && mDataTypeArray.length != 0) {
            mSelectedDataTypeIcon = new int[mDataTypeArray.length];
        }
        if (mSelectedQSDataTypeIcon == null
            && mDataTypeArray.length != 0) {
            mSelectedQSDataTypeIcon = new int[mDataTypeArray.length];
        }
        if (mSelectedDataTypeDesc == null
            && mDataTypeArray.length != 0) {
            mSelectedDataTypeDesc = new String[mDataTypeArray.length];
        }
        if (mSelectedDataActivityIndex == null
            && mDataActivityArray.length != 0) {
            mSelectedDataActivityIndex = new int[mDataActivityArray.length];
        }
        if (mSelectedSignalStreagthIndex == null
            && mSignalStrengthArray.length != 0) {
            mSelectedSignalStreagthIndex = new int[mSignalStrengthArray.length];
        }
        isInitiated = true;
    }

    static void initStacked2SingleIconLookup() {
        mStacked2SingleIconLookup = new SparseArray<>();
        TypedArray stackedIcons = mRes.obtainTypedArray(R.array.stacked_signal_icons);
        TypedArray singleIcons = mRes.obtainTypedArray(R.array.single_signal_icons);

        mStacked2SingleIconLookup.clear();
        for (int i = 0; i < stackedIcons.length() && i < singleIcons.length(); i++) {
            mStacked2SingleIconLookup.put(stackedIcons.getResourceId(i,0),
                    singleIcons.getResourceId(i,0));
        }
        stackedIcons.recycle();
        singleIcons.recycle();
        log(TAG, "initStacked2SingleIconLookup: size=" + mStacked2SingleIconLookup.size());
    }

    static int getSignalNullIcon(int slot) {
        if (mSignalNullArray == null) {
            return 0;
        }
        String resName = mSignalNullArray[slot];
        log(TAG, "null signal icon name: " + resName);
        int resId = mRes.getIdentifier(resName, null, NS);
        return resId;
    }

    static void updateDataType(int slot, int type, boolean showAtLeast3G,
            boolean show4GforLte, boolean hspaDistinguishable, int inet) {
        log(TAG, "updateDataType "
                + String.format("slot=%d, type=%d, inetCondition=%d",
                        slot, type, inet)
                + " showAtLeast3G=" + String.valueOf(showAtLeast3G)
                + " show4GforLte=" + String.valueOf(show4GforLte)
                + " hspaDistinguishable=" + String.valueOf(hspaDistinguishable));

        String resName = mDataTypeArray[slot];
        int resId = mRes.getIdentifier(resName, null, NS);
        String[] dataTypeArray = mRes.getStringArray(resId);

        log(TAG, "data type item name: " + resName + " id:" + resId);

        switch (type) {
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                if (!showAtLeast3G) {
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = 0;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedDataActivityIndex[slot] = 0;
                    mSelectedSignalStreagthIndex[slot] = 0;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_EDGE:
                if (!showAtLeast3G) {
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_E;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_E;
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_E;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                mSelectedDataActivityIndex[slot] = DATA_TYPE_3G;
                mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                        dataTypeArray[type], null, NS);
                mSelectedQSDataTypeIcon[slot] = QS_DATA_3G;
                mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_UMTS;
                break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
                if (hspaDistinguishable || MobileSignalController.isCarrierOneSupported()) {
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    if (type == TelephonyManager.NETWORK_TYPE_HSPA &&
                            MobileSignalController.isCarrierOneSupported()) {
                        mSelectedDataActivityIndex[slot] = DATA_TYPE_HP;
                        mSelectedDataTypeIcon[slot] = ICON_H_PLUS;
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_H_PLUS;
                        mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_HP;
                    } else {
                        mSelectedDataActivityIndex[slot] = DATA_TYPE_H;
                        mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                                dataTypeArray[type], null, NS);
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_H;
                        mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_H;
                    }
                } else {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            mDataTypeGenerationArray[0], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_3G;
                    mSelectedDataTypeDesc[slot] = mDataTypeGenerationDescArray[0];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_3G;

                }
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                if (hspaDistinguishable || MobileSignalController.
                        isCarrierOneSupported()) {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_HP;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_H;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_HP;
                } else {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            mDataTypeGenerationArray[0], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_3G;
                    if(mRes.getBoolean(R.bool.config_show4gForHspap) ||
                            mRes.getBoolean(R.bool.show_network_indicators)){
                        mSelectedDataActivityIndex[slot] = DATA_TYPE_4G;
                        mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                                mDataTypeGenerationArray[3], null, NS);
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_4G;
                    }
                    mSelectedDataTypeDesc[slot] = mDataTypeGenerationDescArray[0];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_3G;
                }
                break;
            case TelephonyManager.NETWORK_TYPE_CDMA:
                if (!showAtLeast3G) {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_1X;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_1X;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_CDMA;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                if (!showAtLeast3G) {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_1X;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_1X;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_1X;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_3G;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_3G;
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                if (!show4GforLte || MobileSignalController.isCarrierOneSupported()) {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_LTE;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    if ( type == TelephonyManager.NETWORK_TYPE_LTE_CA) {
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_LTE_PLUS;
                    } else {
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_LTE;
                    }
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_4G;
                } else {
                    if ( type == TelephonyManager.NETWORK_TYPE_LTE_CA) {
                        mSelectedDataActivityIndex[slot] = DATA_TYPE_4G_PLUS;
                        //Select 4G+ icon.
                        mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                                mDataTypeGenerationArray[2], null, NS);
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_4G_PLUS;
                    } else {
                        mSelectedDataActivityIndex[slot] = DATA_TYPE_4G;
                        mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                                mDataTypeGenerationArray[1], null, NS);
                        mSelectedQSDataTypeIcon[slot] = QS_DATA_4G;
                    }
                    mSelectedDataTypeDesc[slot] = mDataTypeGenerationDescArray[1];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_4G;
                }
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_GSM:
                if (!showAtLeast3G) {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_G;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            dataTypeArray[type], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_G;
                    mSelectedDataTypeDesc[slot] = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_G;
                } else {
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[slot] = mRes.getIdentifier(
                            mDataTypeGenerationArray[0], null, NS);
                    mSelectedQSDataTypeIcon[slot] = QS_DATA_3G;
                    mSelectedDataTypeDesc[slot] = mDataTypeGenerationDescArray[0];;
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_3G;
                }
                break;
            default:
                    mSelectedDataActivityIndex[slot] = DATA_TYPE_UNKNOWN;
                    mSelectedDataTypeIcon[slot] = 0;
                    mSelectedQSDataTypeIcon[slot] = 0;
                    mSelectedDataTypeDesc[slot] = "";
                    mSelectedSignalStreagthIndex[slot] = SIGNAL_STRENGTH_TYPE_G;
                break;
        }
        log(TAG, "updateDataType " + String.format(
                "mSelectedDataTypeIcon[%d]=%d, mSelectedDataActivityIndex=%d",
                slot, mSelectedDataTypeIcon[slot], mSelectedDataActivityIndex[slot]));
    }


    static int getQSDataTypeIcon(int slot) {
        return mSelectedQSDataTypeIcon[slot];
    }

    static int getDataTypeIcon(int slot) {
        log(TAG, "getDataTypeIcon " + String.format("sub=%d", slot));
        return mSelectedDataTypeIcon[slot];
    }

    static int getDataTypeDesc(int slot) {
        return mRes.getIdentifier(mSelectedDataTypeDesc[slot], null, NS);
    }

    static int getDataActivity(int slot, int activity) {
        log(TAG, String.format("getDataActivity, slot=%d, activity=%d",
                slot, activity));

        String[] dataActivityArray = mRes.getStringArray(
                mRes.getIdentifier(mDataActivityArray[slot], null, NS));
        String[] selectedTypeArray = mRes.getStringArray(mRes.getIdentifier(
                dataActivityArray[mSelectedDataActivityIndex[slot]], null, NS));

        return mRes.getIdentifier(selectedTypeArray[activity], null, NS);
    }

    static int getSignalStrengthIcon(int slot, int inet, int level, boolean roaming) {
        log(TAG, "getSignalStrengthIcon: " + String.format(
                "slot=%d, inetCondition=%d, level=%d, roaming=%b", slot, inet, level, roaming));

        String[] signalStrengthArray, selectedTypeArray;

        signalStrengthArray = mRes.getStringArray(mRes.getIdentifier(!roaming ?
                mSignalStrengthArray[slot] : mSignalStrengthRoamingArray[slot], null, NS));
        log(TAG, String.format("signalStrengthArray.length=%d", signalStrengthArray.length));

        selectedTypeArray = mRes.getStringArray(mRes.getIdentifier(
                signalStrengthArray[mSelectedSignalStreagthIndex[slot]], null, NS));
        log(TAG, String.format("selectedTypeArray.length=%d", selectedTypeArray.length));

        String[] inetArray = mRes.getStringArray(
                mRes.getIdentifier(selectedTypeArray[inet], null, NS));
        log(TAG, String.format("inetArray.length=%d", inetArray.length));

        return mRes.getIdentifier(inetArray[level], null, NS);
    }


    static int convertMobileStrengthIcon(int stackedIcon) {
        if (mStacked2SingleIconLookup == null) {
            return stackedIcon;
        }
        int index = mStacked2SingleIconLookup.indexOfKey(stackedIcon);
        if (index >= 0) {
            return mStacked2SingleIconLookup.get(stackedIcon);
        }
        return stackedIcon;
    }

    static int getStackedVoiceIcon(int level) {
        int retValue = 0;
        switch(level){
            case SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN:
                retValue = R.drawable.stat_sys_signal_0_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_POOR:
                retValue = R.drawable.stat_sys_signal_1_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_MODERATE:
                retValue = R.drawable.stat_sys_signal_2_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GOOD:
                retValue = R.drawable.stat_sys_signal_3_2g;
                break;
            case SignalStrength.SIGNAL_STRENGTH_GREAT:
                retValue = R.drawable.stat_sys_signal_4_2g;
                break;
            default:
                break;
        }
        return retValue;
    }

    static int getRoamingSignalIconId(int level, int inet){
        return TELEPHONY_SIGNAL_STRENGTH_ROAMING_R[inet][level];
    }

    static int[]  getSignalStrengthDes(int slot) {
        int[] resId = new int[SIGNAL_LEVEL_NUM];
        for (int i = 0; i < SIGNAL_LEVEL_NUM; i++) {
            resId[i] = mRes.getIdentifier(mSignalStrengthDesc[i], null, NS);
        }
        return resId;
    }

    private static void log(String tag, String str){
        if (DEBUG) {
            Log.d(tag, str);
        }
    }


    static final int QS_ICON_DATA_DISABLED = R.drawable.ic_qs_data_disabled;

    static final MobileIconGroup CARRIER_NETWORK_CHANGE = new MobileIconGroup(
            "CARRIER_NETWORK_CHANGE",
            TelephonyIcons.TELEPHONY_CARRIER_NETWORK_CHANGE,
            TelephonyIcons.QS_TELEPHONY_CARRIER_NETWORK_CHANGE,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.ICON_CARRIER_NETWORK_CHANGE,
            TelephonyIcons.QS_ICON_CARRIER_NETWORK_CHANGE,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_carrier_network_change_mode,
            0,
            false,
            0
            );

    static final MobileIconGroup THREE_G = new MobileIconGroup(
            "3G",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3g,
            TelephonyIcons.ICON_3G,
            true,
            TelephonyIcons.QS_DATA_3G
            );

    static final MobileIconGroup THREE_G_PLUS = new MobileIconGroup(
            "3G+",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3g_plus,
            TelephonyIcons.ICON_3G_PLUS,
            true,
            TelephonyIcons.QS_DATA_3G_PLUS
            );

    static final MobileIconGroup WFC = new MobileIconGroup(
            "WFC",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            0, 0, false, 0
            );

    static final MobileIconGroup UNKNOWN = new MobileIconGroup(
            "Unknown",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            0, 0, false, 0
            );

    static final MobileIconGroup E = new MobileIconGroup(
            "E",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_edge,
            TelephonyIcons.ICON_E,
            false,
            TelephonyIcons.QS_DATA_E
            );

    static final MobileIconGroup ONE_X = new MobileIconGroup(
            "1X",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_cdma,
            TelephonyIcons.ICON_1X,
            true,
            TelephonyIcons.QS_DATA_1X
            );

    static final MobileIconGroup G = new MobileIconGroup(
            "G",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_gprs,
            TelephonyIcons.ICON_G,
            false,
            TelephonyIcons.QS_DATA_G
            );

    static final MobileIconGroup H = new MobileIconGroup(
            "H",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3_5g,
            TelephonyIcons.ICON_H,
            false,
            TelephonyIcons.QS_DATA_H
            );

    static final MobileIconGroup H_PLUS = new MobileIconGroup(
            "H+",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3_5g_plus,
            TelephonyIcons.ICON_H_PLUS,
            false,
            TelephonyIcons.QS_DATA_H_PLUS
            );

    static final MobileIconGroup FOUR_G = new MobileIconGroup(
            "4G",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_4g,
            TelephonyIcons.ICON_4G,
            true,
            TelephonyIcons.QS_DATA_4G
            );

    static final MobileIconGroup FOUR_G_LTE = new MobileIconGroup(
            "4GLTE",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_4g_lte,
            TelephonyIcons.ICON_4G_LTE,
            true,
            TelephonyIcons.QS_DATA_4G_LTE
            );

    static final MobileIconGroup FOUR_G_PLUS = new MobileIconGroup(
            "4G+",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0,0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_4g_plus,
            TelephonyIcons.ICON_4G_PLUS,
            true,
            TelephonyIcons.QS_DATA_4G_PLUS
            );

    static final MobileIconGroup LTE = new MobileIconGroup(
            "LTE",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_lte,
            TelephonyIcons.ICON_LTE,
            true,
            TelephonyIcons.QS_DATA_LTE
            );

    static final MobileIconGroup ROAMING = new MobileIconGroup(
            "Roaming",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_roaming,
            TelephonyIcons.ROAMING_ICON,
            false,
            TelephonyIcons.QS_DATA_R
            );

    static final MobileIconGroup DATA_DISABLED = new MobileIconGroup(
            "DataDisabled",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_cell_data_off,
            TelephonyIcons.ICON_DATA_DISABLED,
            false,
            TelephonyIcons.QS_ICON_DATA_DISABLED
            );
}

