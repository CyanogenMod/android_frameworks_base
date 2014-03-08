/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.R;

class TelephonyIcons {
    //***** Signal strength icons

    //GSM/UMTS
    static final int[][] TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };

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
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };

    static final int[] QS_DATA_R = {
        R.drawable.ic_qs_signal_r,
        R.drawable.ic_qs_signal_full_r
    };

    static final int[][] DATA_SIGNAL_STRENGTH = TELEPHONY_SIGNAL_STRENGTH;

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

    static final int[] QS_DATA_G = {
        R.drawable.ic_qs_signal_g,
        R.drawable.ic_qs_signal_full_g
    };

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

    static final int[] QS_DATA_3G = {
        R.drawable.ic_qs_signal_3g,
        R.drawable.ic_qs_signal_full_3g
    };

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

    static final int[] QS_DATA_E = {
        R.drawable.ic_qs_signal_e,
        R.drawable.ic_qs_signal_full_e
    };

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

    static final int[] QS_DATA_H = {
                R.drawable.ic_qs_signal_h,
                R.drawable.ic_qs_signal_full_h
    };

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

    static final int[] QS_DATA_1X = {
        R.drawable.ic_qs_signal_1x,
        R.drawable.ic_qs_signal_full_1x
    };

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

    static final int[] QS_DATA_4G = {
        R.drawable.ic_qs_signal_4g,
        R.drawable.ic_qs_signal_full_4g
    };

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

    static final int[] QS_DATA_LTE = {
        R.drawable.ic_qs_signal_lte,
        R.drawable.ic_qs_signal_full_lte
    };

///////////////////////////////////////////////////////////////////
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
    static final String TAG = "TelephonyIcons";

    static TypedArray mDataTypeArray, mDataTypeGenerationArray;
    static String[] mDataTypeDescriptionArray, mDataTypeGenerationDescArray;
    static TypedArray mDataActivityArray;
    static TypedArray mSignalStrengthArray, mSignalStrengthRoamingArray;
    static TypedArray mSignalNullArray, mNoSimArray;
    static String[] mSignalStrengthDesc;

    static int[] mSelectedDataTypeIcon;
    static int mSelectedQSDataTypeIcon;
    static String mSelectedDataTypeDesc;
    static int mSelectedDataActivityIndex;
    static int mSelectedSignalStreagthIndex;

    private static Resources mRes;
    private static boolean isInitiated = false;

    static void initAll(Context context) {
        if (isInitiated) {
            log(TAG, "initAll, already initiated!");
            return;
        }

        mRes = context.getResources();
        try {
            mDataTypeArray = mRes.obtainTypedArray(
                    R.array.multi_data_type);
            mDataTypeDescriptionArray = mRes.getStringArray(
                    R.array.telephony_data_type_description);
            mDataTypeGenerationArray = mRes.obtainTypedArray(
                    R.array.telephony_data_type_generation);
            mDataTypeGenerationDescArray = mRes.getStringArray(
                    R.array.telephony_data_type_generation_description);
            mDataActivityArray = mRes.obtainTypedArray(
                    R.array.multi_data_activity);
            mSignalStrengthArray = mRes.obtainTypedArray(
                    R.array.multi_signal_strength);
            mSignalStrengthRoamingArray = mRes.obtainTypedArray(
                    R.array.multi_signal_strength_roaming);
            mSignalNullArray = mRes.obtainTypedArray(
                    R.array.multi_signal_null);
            mNoSimArray = mRes.obtainTypedArray(
                    R.array.multi_no_sim);
            mSignalStrengthDesc = mRes.getStringArray(
                    R.array.signal_strength_description);
        } catch (android.content.res.Resources.NotFoundException e) {
            isInitiated = false;

            mDataTypeArray.recycle();
            mDataTypeGenerationArray.recycle();
            mDataActivityArray.recycle();
            mSignalStrengthArray.recycle();
            mSignalStrengthRoamingArray.recycle();
            mSignalNullArray.recycle();
            mNoSimArray.recycle();

            log(TAG, "initAll, exception happened: " + e);
            return;
        }

        if (mSelectedDataTypeIcon == null
            && mDataTypeArray.length() != 0) {
            mSelectedDataTypeIcon = new int[mDataTypeArray.length()];
        }
        isInitiated = true;
    }

    static int getNoSimIcon() {
        return getNoSimIcon(DEFAULT_SUB);
    }

    static int getNoSimIcon(int sub){
        return mNoSimArray == null ? 0 : mNoSimArray.getResourceId(sub, 0);
    }

    static int getSignalNullIcon() {
        return getSignalNullIcon(DEFAULT_SUB);
    }

    static int getSignalNullIcon(int sub) {
        return mSignalNullArray == null ? 0 : mSignalNullArray.
                getResourceId(sub, 0);
    }

     static void updateDataType(int type, boolean showAtLeast3G,
            boolean show4GforLte, boolean hspaDistinguishable,
            int inetCondition) {
        updateDataType(DEFAULT_SUB, type, showAtLeast3G,
            show4GforLte, hspaDistinguishable, inetCondition);
    }

    static void updateDataType(int sub, int type, boolean showAtLeast3G,
            boolean show4GforLte, boolean hspaDistinguishable,
            int inetCondition) {
        log(TAG, "updateDataType "
                + String.format("sub=%d, type=%d, inetCondition=%d",
                        sub, type, inetCondition)
                + " showAtLeast3G=" + String.valueOf(showAtLeast3G)
                + " show4GforLte=" + String.valueOf(show4GforLte)
                + " hspaDistinguishable=" + String.valueOf(hspaDistinguishable));

        TypedArray dataTypeArray = mRes.obtainTypedArray(
                mDataTypeArray.getResourceId(sub, 0));

        switch (type) {
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                if (!showAtLeast3G) {
                    mSelectedDataTypeIcon[sub] = dataTypeArray.getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = 0;
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedDataActivityIndex = 0;
                    mSelectedSignalStreagthIndex = 0;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_EDGE:
                if (!showAtLeast3G) {
                    mSelectedDataTypeIcon[sub] = dataTypeArray.getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_E[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedDataActivityIndex = DATA_TYPE_E;
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_E;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                mSelectedDataActivityIndex = DATA_TYPE_3G;
                mSelectedDataTypeIcon[sub] = dataTypeArray.getResourceId(type, 0);
                mSelectedQSDataTypeIcon = QS_DATA_3G[inetCondition];
                mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_UMTS;
                break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                if (hspaDistinguishable) {
                    mSelectedDataActivityIndex = DATA_TYPE_H;
                    mSelectedDataTypeIcon[sub] = dataTypeArray
                            .getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_H[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_H;
                } else {
                    mSelectedDataActivityIndex = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[sub] = mDataTypeGenerationArray
                            .getResourceId(0, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_3G[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeGenerationDescArray[0];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_3G;

                }
                break;
            case TelephonyManager.NETWORK_TYPE_CDMA:
                if (!showAtLeast3G) {
                    mSelectedDataActivityIndex = DATA_TYPE_1X;
                    mSelectedDataTypeIcon[sub] = dataTypeArray
                            .getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_1X[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_CDMA;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                if (!showAtLeast3G) {
                    mSelectedDataActivityIndex = DATA_TYPE_1X;
                    mSelectedDataTypeIcon[sub] = dataTypeArray
                            .getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_1X[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_1X;
                    break;
                } else {
                    // fall through
                }
            case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                    mSelectedDataActivityIndex = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[sub] = dataTypeArray
                            .getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_3G[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_3G;
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                if (show4GforLte) {
                    mSelectedDataActivityIndex = DATA_TYPE_4G;
                    mSelectedDataTypeIcon[sub] = mDataTypeGenerationArray
                            .getResourceId(1, 0);;
                    mSelectedQSDataTypeIcon = QS_DATA_4G[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeGenerationDescArray[1];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_4G;
                } else {
                    mSelectedDataActivityIndex = DATA_TYPE_LTE;
                    mSelectedDataTypeIcon[sub] = dataTypeArray
                            .getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_LTE[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_4G;
                }
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_GSM:
                if (!showAtLeast3G) {
                    mSelectedDataActivityIndex = DATA_TYPE_G;
                    mSelectedDataTypeIcon[sub] = dataTypeArray
                            .getResourceId(type, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_G[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeDescriptionArray[type];
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_G;
                } else {
                    mSelectedDataActivityIndex = DATA_TYPE_3G;
                    mSelectedDataTypeIcon[sub] = mDataTypeGenerationArray
                            .getResourceId(0, 0);
                    mSelectedQSDataTypeIcon = QS_DATA_3G[inetCondition];
                    mSelectedDataTypeDesc = mDataTypeGenerationDescArray[0];;
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_3G;
                }
                break;
            default:
                    mSelectedDataActivityIndex = DATA_TYPE_UNKNOWN;
                    mSelectedDataTypeIcon[sub] = 0;
                    mSelectedQSDataTypeIcon = 0;
                    mSelectedDataTypeDesc = "";
                    mSelectedSignalStreagthIndex = SIGNAL_STRENGTH_TYPE_G;
                break;
        }
        log(TAG, "updateDataType "
                + String.format(
                "mSelectedDataTypeIcon[%d]=%d, mSelectedDataActivityIndex=%d",
                sub, mSelectedDataTypeIcon[sub], mSelectedDataActivityIndex));
    }

    static int getQSDataTypeIcon() {
        return mSelectedQSDataTypeIcon;
    }

    static int getDataTypeIcon() {
        return mSelectedDataTypeIcon[DEFAULT_SUB];
    }

    static int getDataTypeIcon(int sub) {
        log(TAG, "getDataTypeIcon " + String.format("sub=%d", sub));
        return mSelectedDataTypeIcon[sub];
    }

    static String getDataTypeDesc() {
        return mSelectedDataTypeDesc;
    }

    static int getDataActivity(int activity){
        return getDataActivity(DEFAULT_SUB, activity);
    }

    static int getDataActivity(int sub, int activity) {
        log(TAG, String.format("getDataActivity, sub=%d, activity=%d",
                sub, activity));

        TypedArray dataActivityArray = mRes.obtainTypedArray(
                mDataActivityArray.getResourceId(sub, 0));
        TypedArray selectedTypeArray = mRes.obtainTypedArray(
                dataActivityArray.getResourceId(mSelectedDataActivityIndex, 0));

        return selectedTypeArray.getResourceId(activity, 0);
    }

    static int getSignalStrengthIcon(int inetCondition, int level,
            boolean isRoaming) {
        return getSignalStrengthIcon(DEFAULT_SUB, inetCondition,
                level, isRoaming);
    }

    static int getSignalStrengthIcon(int sub, int inetCondition, int level,
            boolean isRoaming) {
        log(TAG, "getSignalStrengthIcon: "
                + String.format("sub=%d, inetCondition=%d, level=%d",
                sub, inetCondition, level)
                + " isRoaming=" + String.valueOf(isRoaming));

        log(TAG, String.format("mSignalStrengthArray.length() = %d",
                mSignalStrengthArray.length()));
        TypedArray signalStrengthArray, selectedTypeArray;
        if (!isRoaming) {
            signalStrengthArray = mRes.obtainTypedArray(
                    mSignalStrengthArray.getResourceId(sub, 0));
        } else {
            signalStrengthArray = mRes.obtainTypedArray(
                    mSignalStrengthRoamingArray.getResourceId(sub, 0));
        }

        log(TAG, String.format("signalStrengthArray.length() = %d",
                signalStrengthArray.length()));

        selectedTypeArray = mRes.obtainTypedArray(signalStrengthArray
                .getResourceId(mSelectedSignalStreagthIndex, 0));

        log(TAG, String.format("selectedTypeArray.length() = %d",
                selectedTypeArray.length()));

        TypedArray inetArray = mRes.obtainTypedArray(
                selectedTypeArray.getResourceId(inetCondition, 0));

        log(TAG, String.format("inetArray.length() = %d",
                inetArray.length()));

        return inetArray.getResourceId(level, 0);
    }

    static String getSignalStrengthDes(int level) {
        return getSignalStrengthDes(DEFAULT_SUB, level);
    }

    static String  getSignalStrengthDes(int sub, int level) {
        return mSignalStrengthDesc[level];
    }

    private static void log(String tag, String str){
        if (DEBUG) {
            Log.d(tag, str);
        }
    }
}

