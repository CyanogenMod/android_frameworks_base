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
import com.android.systemui.statusbar.policy.NetworkControllerImpl.MobileSignalController.MobileIconGroup;

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

    static final int[] QS_DATA_R = {
        R.drawable.ic_qs_signal_r,
        R.drawable.ic_qs_signal_r
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
        R.drawable.ic_qs_signal_g
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
        R.drawable.ic_qs_signal_3g
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
        R.drawable.ic_qs_signal_e
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
                R.drawable.ic_qs_signal_h
    };

    //HSPA+
    static final int[][] DATA_HP = {
            { R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp },
            { R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp }
    };

    static final int[] QS_DATA_HP = {
                R.drawable.ic_qs_signal_hp,
                R.drawable.ic_qs_signal_hp

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
        R.drawable.ic_qs_signal_1x
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
        R.drawable.ic_qs_signal_4g
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
        R.drawable.ic_qs_signal_lte
    };

    static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_airplane_mode;
    static final int ROAMING_ICON = R.drawable.stat_sys_data_fully_connected_roam;
    static final int ICON_LTE = R.drawable.stat_sys_data_fully_connected_lte;
    static final int ICON_G = R.drawable.stat_sys_data_fully_connected_g;
    static final int ICON_E = R.drawable.stat_sys_data_fully_connected_e;
    static final int ICON_H = R.drawable.stat_sys_data_fully_connected_h;
    static final int ICON_HP = R.drawable.stat_sys_data_fully_connected_hp;
    static final int ICON_3G = R.drawable.stat_sys_data_fully_connected_3g;
    static final int ICON_4G = R.drawable.stat_sys_data_fully_connected_4g;
    static final int ICON_1X = R.drawable.stat_sys_data_fully_connected_1x;

    static final int QS_ICON_LTE = R.drawable.ic_qs_signal_lte;
    static final int QS_ICON_3G = R.drawable.ic_qs_signal_3g;
    static final int QS_ICON_4G = R.drawable.ic_qs_signal_4g;
    static final int QS_ICON_1X = R.drawable.ic_qs_signal_1x;

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

    static final MobileIconGroup UNKNOWN = new MobileIconGroup(
            "Unknown",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            0, 0, false, new int[2]
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

    static final MobileIconGroup HP = new MobileIconGroup(
            "H+",
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH,
            TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH,
            0, 0,
            TelephonyIcons.TELEPHONY_NO_NETWORK,
            TelephonyIcons.QS_TELEPHONY_NO_NETWORK,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.accessibility_data_connection_3_5g,
            TelephonyIcons.ICON_HP,
            false,
            TelephonyIcons.QS_DATA_HP
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
}
