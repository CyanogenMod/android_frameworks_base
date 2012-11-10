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

import com.android.systemui.R;

class TelephonyIcons {
    //***** Signal strength icons

    //GSM/UMTS
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

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ALT = {
        { R.drawable.stat_sys_signal_0_alt,
          R.drawable.stat_sys_signal_1_alt,
          R.drawable.stat_sys_signal_2_alt,
          R.drawable.stat_sys_signal_3_alt,
          R.drawable.stat_sys_signal_4_alt },
        { R.drawable.stat_sys_signal_0_fully_alt,
          R.drawable.stat_sys_signal_1_fully_alt,
          R.drawable.stat_sys_signal_2_fully_alt,
          R.drawable.stat_sys_signal_3_fully_alt,
          R.drawable.stat_sys_signal_4_fully_alt }
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

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_ALT = {
        { R.drawable.stat_sys_signal_0_alt,
          R.drawable.stat_sys_signal_1_alt,
          R.drawable.stat_sys_signal_2_alt,
          R.drawable.stat_sys_signal_3_alt,
          R.drawable.stat_sys_signal_4_alt },
        { R.drawable.stat_sys_signal_0_fully_alt,
          R.drawable.stat_sys_signal_1_fully_alt,
          R.drawable.stat_sys_signal_2_fully_alt,
          R.drawable.stat_sys_signal_3_fully_alt,
          R.drawable.stat_sys_signal_4_fully_alt }
    };

    static final int[][] DATA_SIGNAL_STRENGTH = TELEPHONY_SIGNAL_STRENGTH;

    static final int[][] DATA_SIGNAL_STRENGTH_ALT = TELEPHONY_SIGNAL_STRENGTH_ALT;

    //***** Data connection icons

    //GSM/UMTS
    static final int[][] DATA_G = {
            { R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g },
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g }
        };

    static final int[][] DATA_G_ALT = {
        { R.drawable.stat_sys_data_connected_g_alt,
          R.drawable.stat_sys_data_connected_g_alt,
          R.drawable.stat_sys_data_connected_g_alt,
          R.drawable.stat_sys_data_connected_g_alt },
        { R.drawable.stat_sys_data_fully_connected_g_alt,
          R.drawable.stat_sys_data_fully_connected_g_alt,
          R.drawable.stat_sys_data_fully_connected_g_alt,
          R.drawable.stat_sys_data_fully_connected_g_alt }
    };

    static final int[][] DATA_3G = {
            { R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g }
        };

    static final int[][] DATA_3G_ALT = {
        { R.drawable.stat_sys_data_connected_3g_alt,
          R.drawable.stat_sys_data_connected_3g_alt,
          R.drawable.stat_sys_data_connected_3g_alt,
          R.drawable.stat_sys_data_connected_3g_alt },
        { R.drawable.stat_sys_data_fully_connected_3g_alt,
          R.drawable.stat_sys_data_fully_connected_3g_alt,
          R.drawable.stat_sys_data_fully_connected_3g_alt,
          R.drawable.stat_sys_data_fully_connected_3g_alt }
    };

    static final int[][] DATA_E = {
            { R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e }
        };

    static final int[][] DATA_E_ALT = {
        { R.drawable.stat_sys_data_connected_e_alt,
          R.drawable.stat_sys_data_connected_e_alt,
          R.drawable.stat_sys_data_connected_e_alt,
          R.drawable.stat_sys_data_connected_e_alt },
        { R.drawable.stat_sys_data_fully_connected_e_alt,
          R.drawable.stat_sys_data_fully_connected_e_alt,
          R.drawable.stat_sys_data_fully_connected_e_alt,
          R.drawable.stat_sys_data_fully_connected_e_alt }
    };

    //3.5G
    static final int[][] DATA_H = {
            { R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h }
    };

    static final int[][] DATA_H_ALT= {
        { R.drawable.stat_sys_data_connected_h,
          R.drawable.stat_sys_data_connected_h,
          R.drawable.stat_sys_data_connected_h,
          R.drawable.stat_sys_data_connected_h },
        { R.drawable.stat_sys_data_fully_connected_h,
          R.drawable.stat_sys_data_fully_connected_h,
          R.drawable.stat_sys_data_fully_connected_h,
          R.drawable.stat_sys_data_fully_connected_h }
    };

    //HPSPA+
    static final int[][] DATA_HP = {
            { R.drawable.stat_sys_data_connected_hp,
              R.drawable.stat_sys_data_connected_hp,
              R.drawable.stat_sys_data_connected_hp,
              R.drawable.stat_sys_data_connected_hp },
            { R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp,
              R.drawable.stat_sys_data_fully_connected_hp }
    };

    static final int[][] DATA_HP_ALT = {
        { R.drawable.stat_sys_data_connected_hp_alt,
          R.drawable.stat_sys_data_connected_hp_alt,
          R.drawable.stat_sys_data_connected_hp_alt,
          R.drawable.stat_sys_data_connected_hp_alt },
        { R.drawable.stat_sys_data_fully_connected_hp_alt,
          R.drawable.stat_sys_data_fully_connected_hp_alt,
          R.drawable.stat_sys_data_fully_connected_hp_alt,
          R.drawable.stat_sys_data_fully_connected_hp_alt }
    };

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][] DATA_1X = {
            { R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x }
            };

    static final int[][] DATA_1X_ALT = {
        { R.drawable.stat_sys_data_connected_1x_alt,
          R.drawable.stat_sys_data_connected_1x_alt,
          R.drawable.stat_sys_data_connected_1x_alt,
          R.drawable.stat_sys_data_connected_1x_alt },
        { R.drawable.stat_sys_data_fully_connected_1x_alt,
          R.drawable.stat_sys_data_fully_connected_1x_alt,
          R.drawable.stat_sys_data_fully_connected_1x_alt,
          R.drawable.stat_sys_data_fully_connected_1x_alt }
        };

    // LTE and eHRPD
    static final int[][] DATA_4G = {
            { R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g },
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g }
        };

    static final int[][] DATA_4G_ALT = {
        { R.drawable.stat_sys_data_connected_4g_alt,
          R.drawable.stat_sys_data_connected_4g_alt,
          R.drawable.stat_sys_data_connected_4g_alt,
          R.drawable.stat_sys_data_connected_4g_alt },
        { R.drawable.stat_sys_data_fully_connected_4g_alt,
          R.drawable.stat_sys_data_fully_connected_4g_alt,
          R.drawable.stat_sys_data_fully_connected_4g_alt,
          R.drawable.stat_sys_data_fully_connected_4g_alt }
    };
}

