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

package com.android.systemui.statusbar.policy;

import android.provider.Settings;
import com.android.systemui.R;

class TelephonyIcons {
    //***** Signal strength icons
    
    public static int signalStrengthBarNumber = Settings.System.getIntForUser(resolver,
        		        Settings.System.STATUS_BAR_SIGNAL_BAR_NUMBER,
        		        mCurrentUserId);

    //GSM/UMTS
    static final int[][] TELEPHONY_SIGNAL_STRENGTH = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_signal6_0_fully,
                  R.drawable.stat_sys_signal6_1_fully,
                  R.drawable.stat_sys_signal6_2_fully,
                  R.drawable.stat_sys_signal6_3_fully,
                  R.drawable.stat_sys_signal6_4_fully,
                  R.drawable.stat_sys_signal6_5_fully,
                  R.drawable.stat_sys_signal6_6_fully },
                { R.drawable.stat_sys_signal6_0_fully,
                  R.drawable.stat_sys_signal6_1_fully,
                  R.drawable.stat_sys_signal6_2_fully,
                  R.drawable.stat_sys_signal6_3_fully,
                  R.drawable.stat_sys_signal6_4_fully,
                  R.drawable.stat_sys_signal6_5_fully,
                  R.drawable.stat_sys_signal6_6_fully }
                break;
            case 5:
                { R.drawable.stat_sys_signal5_0_fully,
                  R.drawable.stat_sys_signal5_1_fully,
                  R.drawable.stat_sys_signal5_2_fully,
                  R.drawable.stat_sys_signal5_3_fully,
                  R.drawable.stat_sys_signal5_4_fully,
                  R.drawable.stat_sys_signal5_5_fully },
                { R.drawable.stat_sys_signal5_0_fully,
                  R.drawable.stat_sys_signal5_1_fully,
                  R.drawable.stat_sys_signal5_2_fully,
                  R.drawable.stat_sys_signal5_3_fully,
                  R.drawable.stat_sys_signal5_4_fully,
                  R.drawable.stat_sys_signal5_5_fully }
                break;
            case 4:
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
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
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
        }
    };

    static final int[][] QS_TELEPHONY_SIGNAL_STRENGTH = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.ic_qs_signal6_0,
                  R.drawable.ic_qs_signal6_1,
                  R.drawable.ic_qs_signal6_2,
                  R.drawable.ic_qs_signal6_3,
                  R.drawable.ic_qs_signal6_4,
                  R.drawable.ic_qs_signal6_5,
                  R.drawable.ic_qs_signal6_6 },
                { R.drawable.ic_qs_signal6_full_0,
                  R.drawable.ic_qs_signal6_full_1,
                  R.drawable.ic_qs_signal6_full_2,
                  R.drawable.ic_qs_signal6_full_3,
                  R.drawable.ic_qs_signal6_full_4,
                  R.drawable.ic_qs_signal6_full_5,
                  R.drawable.ic_qs_signal6_full_6 }
                break;
            case 5:
                { R.drawable.ic_qs_signal5_0,
                  R.drawable.ic_qs_signal5_1,
                  R.drawable.ic_qs_signal5_2,
                  R.drawable.ic_qs_signal5_3,
                  R.drawable.ic_qs_signal5_4,
                  R.drawable.ic_qs_signal5_5 },
                { R.drawable.ic_qs_signal5_full_0,
                  R.drawable.ic_qs_signal5_full_1,
                  R.drawable.ic_qs_signal5_full_2,
                  R.drawable.ic_qs_signal5_full_3,
                  R.drawable.ic_qs_signal5_full_4,
                  R.drawable.ic_qs_signal5_full_5 }
                break;
            case 4:
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
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
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
        }
    };

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_signal6_0_fully,
                  R.drawable.stat_sys_signal6_1_fully,
                  R.drawable.stat_sys_signal6_2_fully,
                  R.drawable.stat_sys_signal6_3_fully,
                  R.drawable.stat_sys_signal6_4_fully,
                  R.drawable.stat_sys_signal6_5_fully,
                  R.drawable.stat_sys_signal6_6_fully },
                { R.drawable.stat_sys_signal6_0_fully,
                  R.drawable.stat_sys_signal6_1_fully,
                  R.drawable.stat_sys_signal6_2_fully,
                  R.drawable.stat_sys_signal6_3_fully,
                  R.drawable.stat_sys_signal6_4_fully,
                  R.drawable.stat_sys_signal6_5_fully,
                  R.drawable.stat_sys_signal6_6_fully }
                break;
            case 5:
                { R.drawable.stat_sys_signal5_0_fully,
                  R.drawable.stat_sys_signal5_1_fully,
                  R.drawable.stat_sys_signal5_2_fully,
                  R.drawable.stat_sys_signal5_3_fully,
                  R.drawable.stat_sys_signal5_4_fully,
                  R.drawable.stat_sys_signal5_5_fully },
                { R.drawable.stat_sys_signal5_0_fully,
                  R.drawable.stat_sys_signal5_1_fully,
                  R.drawable.stat_sys_signal5_2_fully,
                  R.drawable.stat_sys_signal5_3_fully,
                  R.drawable.stat_sys_signal5_4_fully,
                  R.drawable.stat_sys_signal5_5_fully }
                break;
            case 4:
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
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
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
        }
    };

    static final int[] QS_DATA_R = {
        R.drawable.ic_qs_signal_r,
        R.drawable.ic_qs_signal_full_r
    };

    static final int[][] DATA_SIGNAL_STRENGTH = TELEPHONY_SIGNAL_STRENGTH;

    //***** Data connection icons

    //GSM/UMTS
    static final int[][] DATA_G = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g },
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g },
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g },
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g },
                { R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g,
                  R.drawable.stat_sys_data_fully_connected_g }
        }
    };

    static final int[] QS_DATA_G = {
        R.drawable.ic_qs_signal_g,
        R.drawable.ic_qs_signal_full_g
    };

    static final int[][] DATA_3G = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g },
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g },
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g },
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g },
                { R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g,
                  R.drawable.stat_sys_data_fully_connected_3g }
        }
    };

    static final int[] QS_DATA_3G = {
        R.drawable.ic_qs_signal_3g,
        R.drawable.ic_qs_signal_full_3g
    };

    static final int[][] DATA_E = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e },
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e },
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e },
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e },
                { R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e,
                  R.drawable.stat_sys_data_fully_connected_e }
        }
    };

    static final int[] QS_DATA_E = {
        R.drawable.ic_qs_signal_e,
        R.drawable.ic_qs_signal_full_e
    };

    //3.5G
    static final int[][] DATA_H = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h },
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h },
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h },
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h },
                { R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h,
                  R.drawable.stat_sys_data_fully_connected_h }
        }
    };

    static final int[] QS_DATA_H = {
                R.drawable.ic_qs_signal_h,
                R.drawable.ic_qs_signal_full_h
    };

    //HPSPA+
    static final int[][] DATA_HP = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp },
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp },
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp },
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp },
                { R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp,
                  R.drawable.stat_sys_data_fully_connected_hp }
        }
    };

    static final int[] QS_DATA_HP = {
                R.drawable.ic_qs_signal_hp,
                R.drawable.ic_qs_signal_full_hp

    };

    //DC-HPSPA+
    static final int[][] DATA_DC = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc },
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc },
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc },
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc },
                { R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc,
                  R.drawable.stat_sys_data_fully_connected_dc }
        }
    };

    static final int[] QS_DATA_DC = {
                R.drawable.ic_qs_signal_dc,
                R.drawable.ic_qs_signal_full_dc

    };

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][] DATA_1X = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x },
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x },
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x },
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x },
                { R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x,
                  R.drawable.stat_sys_data_fully_connected_1x }
        }
    };

    static final int[] QS_DATA_1X = {
        R.drawable.ic_qs_signal_1x,
        R.drawable.ic_qs_signal_full_1x
    };

    // LTE and eHRPD
    static final int[][] DATA_4G = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g },
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g },
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g },
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g },
                { R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g,
                  R.drawable.stat_sys_data_fully_connected_4g }
        }
    };

    static final int[] QS_DATA_4G = {
        R.drawable.ic_qs_signal_4g,
        R.drawable.ic_qs_signal_full_4g
    };

    // LTE branded "LTE"
    static final int[][] DATA_LTE = {
        switch (signalStrengthBarNumber) {
            case 6:
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte },
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte }
                break;
            case 5:
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte },
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte }
                break;
            case 4:
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte },
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte }
                break;
            default:
                // we shouldn't get here as the num of bars has to be 4, 5, or 6,
                // but just in case it does happen
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte },
                { R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte,
                  R.drawable.stat_sys_data_fully_connected_lte }
        }
    };

    static final int[] QS_DATA_LTE = {
        R.drawable.ic_qs_signal_lte,
        R.drawable.ic_qs_signal_full_lte
    };

}

