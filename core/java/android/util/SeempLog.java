/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.util;

import com.android.internal.os.RuntimeInit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import android.util.Log;
import android.provider.Settings;

/**
 * SeempLog
 *
 * @hide
 */
public final class SeempLog {
    private SeempLog() {
    }

    /**
     * Send a log message to the seemp log.
     * @param api The api triggering this message.
     */
    public static int record(int api) {
        return seemp_println_native(api, "");
    }

    /**
    * Send a log message to the seemp log.
    * @param api The api triggering this message.
    * @param msg The message you would like logged.
    */
    public static int record_str(int api, String msg) {
        if ( msg != null ) {
            return seemp_println_native(api, msg);
        }
        else {
            return seemp_println_native(api, "");
        }
    }

    public static int record_sensor(int api,
            android.hardware.Sensor sensor) {
        if ( sensor != null ) {
            return seemp_println_native(api, "sensor="+sensor.getType());
        }
        else {
            return seemp_println_native(api, "sensor=-1");
        }
    }

    public static int record_sensor_rate(int api,
            android.hardware.Sensor sensor, int rate) {
        if ( sensor != null ) {
            return seemp_println_native(api,
                    "sensor="+sensor.getType() + ",rate="+rate);
        }
        else {
            return seemp_println_native(api, "sensor=-1,rate=" + rate);
        }
    }

    public static int record_uri(int api, android.net.Uri uri) {
        if ( uri != null ) {
            return seemp_println_native(api, "uri, " + uri.toString());
        }
        else {
            return seemp_println_native(api, "uri, null" );
        }
    }

    public static int record_vg_layout(int api,
            android.view.ViewGroup.LayoutParams params) {
        try {
            android.view.WindowManager.LayoutParams p =
                (android.view.WindowManager.LayoutParams) params;
            if ( p != null ) {
                return seemp_println_native(api,
                    "window_type=" + p.type + ",window_flag=" + p.flags);
            }
            else {
                return seemp_println_native(api, "");
            }
        } catch (ClassCastException cce) {
            return seemp_println_native(api, "");
        }
    }

    /** @hide */ public static native int seemp_println_native(int api, String msg);

    public static final int SEEMP_API_android_provider_Settings__get_ANDROID_ID_                                                 =     7;
    public static final int SEEMP_API_android_provider_Settings__get_ACCELEROMETER_ROTATION_                                     =    96;
    public static final int SEEMP_API_android_provider_Settings__get_USER_ROTATION_                                              =    97;
    public static final int SEEMP_API_android_provider_Settings__get_ADB_ENABLED_                                                =    98;
    public static final int SEEMP_API_android_provider_Settings__get_DEBUG_APP_                                                  =    99;
    public static final int SEEMP_API_android_provider_Settings__get_WAIT_FOR_DEBUGGER_                                          =   100;
    public static final int SEEMP_API_android_provider_Settings__get_AIRPLANE_MODE_ON_                                           =   101;
    public static final int SEEMP_API_android_provider_Settings__get_AIRPLANE_MODE_RADIOS_                                       =   102;
    public static final int SEEMP_API_android_provider_Settings__get_ALARM_ALERT_                                                =   103;
    public static final int SEEMP_API_android_provider_Settings__get_NEXT_ALARM_FORMATTED_                                       =   104;
    public static final int SEEMP_API_android_provider_Settings__get_ALWAYS_FINISH_ACTIVITIES_                                   =   105;
    public static final int SEEMP_API_android_provider_Settings__get_LOGGING_ID_                                                 =   106;
    public static final int SEEMP_API_android_provider_Settings__get_ANIMATOR_DURATION_SCALE_                                    =   107;
    public static final int SEEMP_API_android_provider_Settings__get_WINDOW_ANIMATION_SCALE_                                     =   108;
    public static final int SEEMP_API_android_provider_Settings__get_FONT_SCALE_                                                 =   109;
    public static final int SEEMP_API_android_provider_Settings__get_SCREEN_BRIGHTNESS_                                          =   110;
    public static final int SEEMP_API_android_provider_Settings__get_SCREEN_BRIGHTNESS_MODE_                                     =   111;
    public static final int SEEMP_API_android_provider_Settings__get_SCREEN_BRIGHTNESS_MODE_AUTOMATIC_                           =   112;
    public static final int SEEMP_API_android_provider_Settings__get_SCREEN_BRIGHTNESS_MODE_MANUAL_                              =   113;
    public static final int SEEMP_API_android_provider_Settings__get_SCREEN_OFF_TIMEOUT_                                         =   114;
    public static final int SEEMP_API_android_provider_Settings__get_DIM_SCREEN_                                                 =   115;
    public static final int SEEMP_API_android_provider_Settings__get_TRANSITION_ANIMATION_SCALE_                                 =   116;
    public static final int SEEMP_API_android_provider_Settings__get_STAY_ON_WHILE_PLUGGED_IN_                                   =   117;
    public static final int SEEMP_API_android_provider_Settings__get_WALLPAPER_ACTIVITY_                                         =   118;
    public static final int SEEMP_API_android_provider_Settings__get_SHOW_PROCESSES_                                             =   119;
    public static final int SEEMP_API_android_provider_Settings__get_SHOW_WEB_SUGGESTIONS_                                       =   120;
    public static final int SEEMP_API_android_provider_Settings__get_SHOW_GTALK_SERVICE_STATUS_                                  =   121;
    public static final int SEEMP_API_android_provider_Settings__get_USE_GOOGLE_MAIL_                                            =   122;
    public static final int SEEMP_API_android_provider_Settings__get_AUTO_TIME_                                                  =   123;
    public static final int SEEMP_API_android_provider_Settings__get_AUTO_TIME_ZONE_                                             =   124;
    public static final int SEEMP_API_android_provider_Settings__get_DATE_FORMAT_                                                =   125;
    public static final int SEEMP_API_android_provider_Settings__get_TIME_12_24_                                                 =   126;
    public static final int SEEMP_API_android_provider_Settings__get_BLUETOOTH_DISCOVERABILITY_                                  =   127;
    public static final int SEEMP_API_android_provider_Settings__get_BLUETOOTH_DISCOVERABILITY_TIMEOUT_                          =   128;
    public static final int SEEMP_API_android_provider_Settings__get_BLUETOOTH_ON_                                               =   129;
    public static final int SEEMP_API_android_provider_Settings__get_DEVICE_PROVISIONED_                                         =   130;
    public static final int SEEMP_API_android_provider_Settings__get_SETUP_WIZARD_HAS_RUN_                                       =   131;
    public static final int SEEMP_API_android_provider_Settings__get_DTMF_TONE_WHEN_DIALING_                                     =   132;
    public static final int SEEMP_API_android_provider_Settings__get_END_BUTTON_BEHAVIOR_                                        =   133;
    public static final int SEEMP_API_android_provider_Settings__get_RINGTONE_                                                   =   134;
    public static final int SEEMP_API_android_provider_Settings__get_MODE_RINGER_                                                =   135;
    public static final int SEEMP_API_android_provider_Settings__get_INSTALL_NON_MARKET_APPS_                                    =   136;
    public static final int SEEMP_API_android_provider_Settings__get_LOCATION_PROVIDERS_ALLOWED_                                 =   137;
    public static final int SEEMP_API_android_provider_Settings__get_LOCK_PATTERN_ENABLED_                                       =   138;
    public static final int SEEMP_API_android_provider_Settings__get_LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED_                      =   139;
    public static final int SEEMP_API_android_provider_Settings__get_LOCK_PATTERN_VISIBLE_                                       =   140;
    public static final int SEEMP_API_android_provider_Settings__get_NETWORK_PREFERENCE_                                         =   141;
    public static final int SEEMP_API_android_provider_Settings__get_DATA_ROAMING_                                               =   142;
    public static final int SEEMP_API_android_provider_Settings__get_HTTP_PROXY_                                                 =   143;
    public static final int SEEMP_API_android_provider_Settings__get_PARENTAL_CONTROL_ENABLED_                                   =   144;
    public static final int SEEMP_API_android_provider_Settings__get_PARENTAL_CONTROL_LAST_UPDATE_                               =   145;
    public static final int SEEMP_API_android_provider_Settings__get_PARENTAL_CONTROL_REDIRECT_URL_                              =   146;
    public static final int SEEMP_API_android_provider_Settings__get_RADIO_BLUETOOTH_                                            =   147;
    public static final int SEEMP_API_android_provider_Settings__get_RADIO_CELL_                                                 =   148;
    public static final int SEEMP_API_android_provider_Settings__get_RADIO_NFC_                                                  =   149;
    public static final int SEEMP_API_android_provider_Settings__get_RADIO_WIFI_                                                 =   150;
    public static final int SEEMP_API_android_provider_Settings__get_SYS_PROP_SETTING_VERSION_                                   =   151;
    public static final int SEEMP_API_android_provider_Settings__get_SETTINGS_CLASSNAME_                                         =   152;
    public static final int SEEMP_API_android_provider_Settings__get_TEXT_AUTO_CAPS_                                             =   153;
    public static final int SEEMP_API_android_provider_Settings__get_TEXT_AUTO_PUNCTUATE_                                        =   154;
    public static final int SEEMP_API_android_provider_Settings__get_TEXT_AUTO_REPLACE_                                          =   155;
    public static final int SEEMP_API_android_provider_Settings__get_TEXT_SHOW_PASSWORD_                                         =   156;
    public static final int SEEMP_API_android_provider_Settings__get_USB_MASS_STORAGE_ENABLED_                                   =   157;
    public static final int SEEMP_API_android_provider_Settings__get_VIBRATE_ON_                                                 =   158;
    public static final int SEEMP_API_android_provider_Settings__get_HAPTIC_FEEDBACK_ENABLED_                                    =   159;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_ALARM_                                               =   160;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_BLUETOOTH_SCO_                                       =   161;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_MUSIC_                                               =   162;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_NOTIFICATION_                                        =   163;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_RING_                                                =   164;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_SYSTEM_                                              =   165;
    public static final int SEEMP_API_android_provider_Settings__get_VOLUME_VOICE_                                               =   166;
    public static final int SEEMP_API_android_provider_Settings__get_SOUND_EFFECTS_ENABLED_                                      =   167;
    public static final int SEEMP_API_android_provider_Settings__get_MODE_RINGER_STREAMS_AFFECTED_                               =   168;
    public static final int SEEMP_API_android_provider_Settings__get_MUTE_STREAMS_AFFECTED_                                      =   169;
    public static final int SEEMP_API_android_provider_Settings__get_NOTIFICATION_SOUND_                                         =   170;
    public static final int SEEMP_API_android_provider_Settings__get_APPEND_FOR_LAST_AUDIBLE_                                    =   171;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_MAX_DHCP_RETRY_COUNT_                                  =   172;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS_            =   173;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON_                    =   174;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY_                       =   175;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_NUM_OPEN_NETWORKS_KEPT_                                =   176;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_ON_                                                    =   177;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_SLEEP_POLICY_                                          =   178;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_SLEEP_POLICY_DEFAULT_                                  =   179;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_SLEEP_POLICY_NEVER_                                    =   180;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED_                      =   181;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_STATIC_DNS1_                                           =   182;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_STATIC_DNS2_                                           =   183;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_STATIC_GATEWAY_                                        =   184;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_STATIC_IP_                                             =   185;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_STATIC_NETMASK_                                        =   186;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_USE_STATIC_IP_                                         =   187;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE_            =   188;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_AP_COUNT_                                     =   189;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS_                    =   190;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED_                     =   191;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS_                  =   192;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT_                   =   193;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_MAX_AP_CHECKS_                                =   194;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_ON_                                           =   195;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_PING_COUNT_                                   =   196;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_PING_DELAY_MS_                                =   197;
    public static final int SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_PING_TIMEOUT_MS_                              =   198;
    public static final int SEEMP_API_android_provider_Settings__put_ACCELEROMETER_ROTATION_                                     =   199;
    public static final int SEEMP_API_android_provider_Settings__put_USER_ROTATION_                                              =   200;
    public static final int SEEMP_API_android_provider_Settings__put_ADB_ENABLED_                                                =   201;
    public static final int SEEMP_API_android_provider_Settings__put_DEBUG_APP_                                                  =   202;
    public static final int SEEMP_API_android_provider_Settings__put_WAIT_FOR_DEBUGGER_                                          =   203;
    public static final int SEEMP_API_android_provider_Settings__put_AIRPLANE_MODE_ON_                                           =   204;
    public static final int SEEMP_API_android_provider_Settings__put_AIRPLANE_MODE_RADIOS_                                       =   205;
    public static final int SEEMP_API_android_provider_Settings__put_ALARM_ALERT_                                                =   206;
    public static final int SEEMP_API_android_provider_Settings__put_NEXT_ALARM_FORMATTED_                                       =   207;
    public static final int SEEMP_API_android_provider_Settings__put_ALWAYS_FINISH_ACTIVITIES_                                   =   208;
    public static final int SEEMP_API_android_provider_Settings__put_ANDROID_ID_                                                 =   209;
    public static final int SEEMP_API_android_provider_Settings__put_LOGGING_ID_                                                 =   210;
    public static final int SEEMP_API_android_provider_Settings__put_ANIMATOR_DURATION_SCALE_                                    =   211;
    public static final int SEEMP_API_android_provider_Settings__put_WINDOW_ANIMATION_SCALE_                                     =   212;
    public static final int SEEMP_API_android_provider_Settings__put_FONT_SCALE_                                                 =   213;
    public static final int SEEMP_API_android_provider_Settings__put_SCREEN_BRIGHTNESS_                                          =   214;
    public static final int SEEMP_API_android_provider_Settings__put_SCREEN_BRIGHTNESS_MODE_                                     =   215;
    public static final int SEEMP_API_android_provider_Settings__put_SCREEN_BRIGHTNESS_MODE_AUTOMATIC_                           =   216;
    public static final int SEEMP_API_android_provider_Settings__put_SCREEN_BRIGHTNESS_MODE_MANUAL_                              =   217;
    public static final int SEEMP_API_android_provider_Settings__put_SCREEN_OFF_TIMEOUT_                                         =   218;
    public static final int SEEMP_API_android_provider_Settings__put_DIM_SCREEN_                                                 =   219;
    public static final int SEEMP_API_android_provider_Settings__put_TRANSITION_ANIMATION_SCALE_                                 =   220;
    public static final int SEEMP_API_android_provider_Settings__put_STAY_ON_WHILE_PLUGGED_IN_                                   =   221;
    public static final int SEEMP_API_android_provider_Settings__put_WALLPAPER_ACTIVITY_                                         =   222;
    public static final int SEEMP_API_android_provider_Settings__put_SHOW_PROCESSES_                                             =   223;
    public static final int SEEMP_API_android_provider_Settings__put_SHOW_WEB_SUGGESTIONS_                                       =   224;
    public static final int SEEMP_API_android_provider_Settings__put_SHOW_GTALK_SERVICE_STATUS_                                  =   225;
    public static final int SEEMP_API_android_provider_Settings__put_USE_GOOGLE_MAIL_                                            =   226;
    public static final int SEEMP_API_android_provider_Settings__put_AUTO_TIME_                                                  =   227;
    public static final int SEEMP_API_android_provider_Settings__put_AUTO_TIME_ZONE_                                             =   228;
    public static final int SEEMP_API_android_provider_Settings__put_DATE_FORMAT_                                                =   229;
    public static final int SEEMP_API_android_provider_Settings__put_TIME_12_24_                                                 =   230;
    public static final int SEEMP_API_android_provider_Settings__put_BLUETOOTH_DISCOVERABILITY_                                  =   231;
    public static final int SEEMP_API_android_provider_Settings__put_BLUETOOTH_DISCOVERABILITY_TIMEOUT_                          =   232;
    public static final int SEEMP_API_android_provider_Settings__put_BLUETOOTH_ON_                                               =   233;
    public static final int SEEMP_API_android_provider_Settings__put_DEVICE_PROVISIONED_                                         =   234;
    public static final int SEEMP_API_android_provider_Settings__put_SETUP_WIZARD_HAS_RUN_                                       =   235;
    public static final int SEEMP_API_android_provider_Settings__put_DTMF_TONE_WHEN_DIALING_                                     =   236;
    public static final int SEEMP_API_android_provider_Settings__put_END_BUTTON_BEHAVIOR_                                        =   237;
    public static final int SEEMP_API_android_provider_Settings__put_RINGTONE_                                                   =   238;
    public static final int SEEMP_API_android_provider_Settings__put_MODE_RINGER_                                                =   239;
    public static final int SEEMP_API_android_provider_Settings__put_INSTALL_NON_MARKET_APPS_                                    =   240;
    public static final int SEEMP_API_android_provider_Settings__put_LOCATION_PROVIDERS_ALLOWED_                                 =   241;
    public static final int SEEMP_API_android_provider_Settings__put_LOCK_PATTERN_ENABLED_                                       =   242;
    public static final int SEEMP_API_android_provider_Settings__put_LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED_                      =   243;
    public static final int SEEMP_API_android_provider_Settings__put_LOCK_PATTERN_VISIBLE_                                       =   244;
    public static final int SEEMP_API_android_provider_Settings__put_NETWORK_PREFERENCE_                                         =   245;
    public static final int SEEMP_API_android_provider_Settings__put_DATA_ROAMING_                                               =   246;
    public static final int SEEMP_API_android_provider_Settings__put_HTTP_PROXY_                                                 =   247;
    public static final int SEEMP_API_android_provider_Settings__put_PARENTAL_CONTROL_ENABLED_                                   =   248;
    public static final int SEEMP_API_android_provider_Settings__put_PARENTAL_CONTROL_LAST_UPDATE_                               =   249;
    public static final int SEEMP_API_android_provider_Settings__put_PARENTAL_CONTROL_REDIRECT_URL_                              =   250;
    public static final int SEEMP_API_android_provider_Settings__put_RADIO_BLUETOOTH_                                            =   251;
    public static final int SEEMP_API_android_provider_Settings__put_RADIO_CELL_                                                 =   252;
    public static final int SEEMP_API_android_provider_Settings__put_RADIO_NFC_                                                  =   253;
    public static final int SEEMP_API_android_provider_Settings__put_RADIO_WIFI_                                                 =   254;
    public static final int SEEMP_API_android_provider_Settings__put_SYS_PROP_SETTING_VERSION_                                   =   255;
    public static final int SEEMP_API_android_provider_Settings__put_SETTINGS_CLASSNAME_                                         =   256;
    public static final int SEEMP_API_android_provider_Settings__put_TEXT_AUTO_CAPS_                                             =   257;
    public static final int SEEMP_API_android_provider_Settings__put_TEXT_AUTO_PUNCTUATE_                                        =   258;
    public static final int SEEMP_API_android_provider_Settings__put_TEXT_AUTO_REPLACE_                                          =   259;
    public static final int SEEMP_API_android_provider_Settings__put_TEXT_SHOW_PASSWORD_                                         =   260;
    public static final int SEEMP_API_android_provider_Settings__put_USB_MASS_STORAGE_ENABLED_                                   =   261;
    public static final int SEEMP_API_android_provider_Settings__put_VIBRATE_ON_                                                 =   262;
    public static final int SEEMP_API_android_provider_Settings__put_HAPTIC_FEEDBACK_ENABLED_                                    =   263;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_ALARM_                                               =   264;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_BLUETOOTH_SCO_                                       =   265;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_MUSIC_                                               =   266;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_NOTIFICATION_                                        =   267;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_RING_                                                =   268;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_SYSTEM_                                              =   269;
    public static final int SEEMP_API_android_provider_Settings__put_VOLUME_VOICE_                                               =   270;
    public static final int SEEMP_API_android_provider_Settings__put_SOUND_EFFECTS_ENABLED_                                      =   271;
    public static final int SEEMP_API_android_provider_Settings__put_MODE_RINGER_STREAMS_AFFECTED_                               =   272;
    public static final int SEEMP_API_android_provider_Settings__put_MUTE_STREAMS_AFFECTED_                                      =   273;
    public static final int SEEMP_API_android_provider_Settings__put_NOTIFICATION_SOUND_                                         =   274;
    public static final int SEEMP_API_android_provider_Settings__put_APPEND_FOR_LAST_AUDIBLE_                                    =   275;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_MAX_DHCP_RETRY_COUNT_                                  =   276;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS_            =   277;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON_                    =   278;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY_                       =   279;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_NUM_OPEN_NETWORKS_KEPT_                                =   280;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_ON_                                                    =   281;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_SLEEP_POLICY_                                          =   282;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_SLEEP_POLICY_DEFAULT_                                  =   283;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_SLEEP_POLICY_NEVER_                                    =   284;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED_                      =   285;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_STATIC_DNS1_                                           =   286;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_STATIC_DNS2_                                           =   287;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_STATIC_GATEWAY_                                        =   288;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_STATIC_IP_                                             =   289;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_STATIC_NETMASK_                                        =   290;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_USE_STATIC_IP_                                         =   291;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE_            =   292;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_AP_COUNT_                                     =   293;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS_                    =   294;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED_                     =   295;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS_                  =   296;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT_                   =   297;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_MAX_AP_CHECKS_                                =   298;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_ON_                                           =   299;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_PING_COUNT_                                   =   300;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_PING_DELAY_MS_                                =   301;
    public static final int SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_PING_TIMEOUT_MS_                              =   302;

    private final static java.util.Map<String,Integer> value_to_get_map;
    static {
        value_to_get_map = new java.util.HashMap<String,Integer>( 198 );
        value_to_get_map.put(Settings.System.NOTIFICATION_SOUND,
                SEEMP_API_android_provider_Settings__get_NOTIFICATION_SOUND_);
        value_to_get_map.put(Settings.System.DTMF_TONE_WHEN_DIALING,
                SEEMP_API_android_provider_Settings__get_DTMF_TONE_WHEN_DIALING_);
        value_to_get_map.put(Settings.System.LOCK_PATTERN_ENABLED,
                SEEMP_API_android_provider_Settings__get_LOCK_PATTERN_ENABLED_);
        value_to_get_map.put(Settings.System.WIFI_MAX_DHCP_RETRY_COUNT,
                SEEMP_API_android_provider_Settings__get_WIFI_MAX_DHCP_RETRY_COUNT_);
        value_to_get_map.put(Settings.System.AUTO_TIME,
                SEEMP_API_android_provider_Settings__get_AUTO_TIME_);
        value_to_get_map.put(Settings.System.SETUP_WIZARD_HAS_RUN,
                SEEMP_API_android_provider_Settings__get_SETUP_WIZARD_HAS_RUN_);
        value_to_get_map.put(Settings.System.SYS_PROP_SETTING_VERSION,
                SEEMP_API_android_provider_Settings__get_SYS_PROP_SETTING_VERSION_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS_);
        value_to_get_map.put(Settings.System.LOCATION_PROVIDERS_ALLOWED,
                SEEMP_API_android_provider_Settings__get_LOCATION_PROVIDERS_ALLOWED_);
        value_to_get_map.put(Settings.System.ALARM_ALERT,
                SEEMP_API_android_provider_Settings__get_ALARM_ALERT_);
        value_to_get_map.put(Settings.System.VIBRATE_ON,
                SEEMP_API_android_provider_Settings__get_VIBRATE_ON_);
        value_to_get_map.put(Settings.System.USB_MASS_STORAGE_ENABLED,
                SEEMP_API_android_provider_Settings__get_USB_MASS_STORAGE_ENABLED_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_PING_DELAY_MS,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_PING_DELAY_MS_);
        value_to_get_map.put(Settings.System.FONT_SCALE,
                SEEMP_API_android_provider_Settings__get_FONT_SCALE_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_AP_COUNT,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_AP_COUNT_);
        value_to_get_map.put(Settings.System.ALWAYS_FINISH_ACTIVITIES,
                SEEMP_API_android_provider_Settings__get_ALWAYS_FINISH_ACTIVITIES_);
        value_to_get_map.put(Settings.System.ACCELEROMETER_ROTATION,
                SEEMP_API_android_provider_Settings__get_ACCELEROMETER_ROTATION_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_PING_TIMEOUT_MS,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_PING_TIMEOUT_MS_);
        value_to_get_map.put(Settings.System.VOLUME_NOTIFICATION,
                SEEMP_API_android_provider_Settings__get_VOLUME_NOTIFICATION_);
        value_to_get_map.put(Settings.System.AIRPLANE_MODE_ON,
                SEEMP_API_android_provider_Settings__get_AIRPLANE_MODE_ON_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS_);
        value_to_get_map.put(Settings.System.WIFI_STATIC_IP,
                SEEMP_API_android_provider_Settings__get_WIFI_STATIC_IP_);
        value_to_get_map.put(Settings.System.RADIO_BLUETOOTH,
                SEEMP_API_android_provider_Settings__get_RADIO_BLUETOOTH_);
        value_to_get_map.put(Settings.System.BLUETOOTH_DISCOVERABILITY_TIMEOUT,
                SEEMP_API_android_provider_Settings__get_BLUETOOTH_DISCOVERABILITY_TIMEOUT_);
        value_to_get_map.put(Settings.System.VOLUME_RING,
                SEEMP_API_android_provider_Settings__get_VOLUME_RING_);
        value_to_get_map.put(Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                SEEMP_API_android_provider_Settings__get_MODE_RINGER_STREAMS_AFFECTED_);
        value_to_get_map.put(Settings.System.VOLUME_SYSTEM,
                SEEMP_API_android_provider_Settings__get_VOLUME_SYSTEM_);
        value_to_get_map.put(Settings.System.SCREEN_OFF_TIMEOUT,
                SEEMP_API_android_provider_Settings__get_SCREEN_OFF_TIMEOUT_);
        value_to_get_map.put(Settings.System.RADIO_WIFI,
                SEEMP_API_android_provider_Settings__get_RADIO_WIFI_);
        value_to_get_map.put(Settings.System.AUTO_TIME_ZONE,
                SEEMP_API_android_provider_Settings__get_AUTO_TIME_ZONE_);
        value_to_get_map.put(Settings.System.TEXT_AUTO_CAPS,
                SEEMP_API_android_provider_Settings__get_TEXT_AUTO_CAPS_);
        value_to_get_map.put(Settings.System.WALLPAPER_ACTIVITY,
                SEEMP_API_android_provider_Settings__get_WALLPAPER_ACTIVITY_);
        value_to_get_map.put(Settings.System.ANIMATOR_DURATION_SCALE,
                SEEMP_API_android_provider_Settings__get_ANIMATOR_DURATION_SCALE_);
        value_to_get_map.put(Settings.System.WIFI_NUM_OPEN_NETWORKS_KEPT,
                SEEMP_API_android_provider_Settings__get_WIFI_NUM_OPEN_NETWORKS_KEPT_);
        value_to_get_map.put(Settings.System.LOCK_PATTERN_VISIBLE,
                SEEMP_API_android_provider_Settings__get_LOCK_PATTERN_VISIBLE_);
        value_to_get_map.put(Settings.System.VOLUME_VOICE,
                SEEMP_API_android_provider_Settings__get_VOLUME_VOICE_);
        value_to_get_map.put(Settings.System.DEBUG_APP,
                SEEMP_API_android_provider_Settings__get_DEBUG_APP_);
        value_to_get_map.put(Settings.System.WIFI_ON,
                SEEMP_API_android_provider_Settings__get_WIFI_ON_);
        value_to_get_map.put(Settings.System.TEXT_SHOW_PASSWORD,
                SEEMP_API_android_provider_Settings__get_TEXT_SHOW_PASSWORD_);
        value_to_get_map.put(Settings.System.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY,
                SEEMP_API_android_provider_Settings__get_WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY_);
        value_to_get_map.put(Settings.System.WIFI_SLEEP_POLICY,
                SEEMP_API_android_provider_Settings__get_WIFI_SLEEP_POLICY_);
        value_to_get_map.put(Settings.System.VOLUME_MUSIC,
                SEEMP_API_android_provider_Settings__get_VOLUME_MUSIC_);
        value_to_get_map.put(Settings.System.PARENTAL_CONTROL_LAST_UPDATE,
                SEEMP_API_android_provider_Settings__get_PARENTAL_CONTROL_LAST_UPDATE_);
        value_to_get_map.put(Settings.System.DEVICE_PROVISIONED,
                SEEMP_API_android_provider_Settings__get_DEVICE_PROVISIONED_);
        value_to_get_map.put(Settings.System.HTTP_PROXY,
                SEEMP_API_android_provider_Settings__get_HTTP_PROXY_);
        value_to_get_map.put(Settings.System.ANDROID_ID,
                SEEMP_API_android_provider_Settings__get_ANDROID_ID_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_MAX_AP_CHECKS,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_MAX_AP_CHECKS_);
        value_to_get_map.put(Settings.System.END_BUTTON_BEHAVIOR,
                SEEMP_API_android_provider_Settings__get_END_BUTTON_BEHAVIOR_);
        value_to_get_map.put(Settings.System.NEXT_ALARM_FORMATTED,
                SEEMP_API_android_provider_Settings__get_NEXT_ALARM_FORMATTED_);
        value_to_get_map.put(Settings.System.RADIO_CELL,
                SEEMP_API_android_provider_Settings__get_RADIO_CELL_);
        value_to_get_map.put(Settings.System.PARENTAL_CONTROL_ENABLED,
                SEEMP_API_android_provider_Settings__get_PARENTAL_CONTROL_ENABLED_);
        value_to_get_map.put(Settings.System.BLUETOOTH_ON,
                SEEMP_API_android_provider_Settings__get_BLUETOOTH_ON_);
        value_to_get_map.put(Settings.System.WINDOW_ANIMATION_SCALE,
                SEEMP_API_android_provider_Settings__get_WINDOW_ANIMATION_SCALE_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED_);
        value_to_get_map.put(Settings.System.BLUETOOTH_DISCOVERABILITY,
                SEEMP_API_android_provider_Settings__get_BLUETOOTH_DISCOVERABILITY_);
        value_to_get_map.put(Settings.System.WIFI_STATIC_DNS1,
                SEEMP_API_android_provider_Settings__get_WIFI_STATIC_DNS1_);
        value_to_get_map.put(Settings.System.WIFI_STATIC_DNS2,
                SEEMP_API_android_provider_Settings__get_WIFI_STATIC_DNS2_);
        value_to_get_map.put(Settings.System.HAPTIC_FEEDBACK_ENABLED,
                SEEMP_API_android_provider_Settings__get_HAPTIC_FEEDBACK_ENABLED_);
        value_to_get_map.put(Settings.System.SHOW_WEB_SUGGESTIONS,
                SEEMP_API_android_provider_Settings__get_SHOW_WEB_SUGGESTIONS_);
        value_to_get_map.put(Settings.System.PARENTAL_CONTROL_REDIRECT_URL,
                SEEMP_API_android_provider_Settings__get_PARENTAL_CONTROL_REDIRECT_URL_);
        value_to_get_map.put(Settings.System.DATE_FORMAT,
                SEEMP_API_android_provider_Settings__get_DATE_FORMAT_);
        value_to_get_map.put(Settings.System.RADIO_NFC,
                SEEMP_API_android_provider_Settings__get_RADIO_NFC_);
        value_to_get_map.put(Settings.System.AIRPLANE_MODE_RADIOS,
                SEEMP_API_android_provider_Settings__get_AIRPLANE_MODE_RADIOS_);
        value_to_get_map.put(Settings.System.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED,
                SEEMP_API_android_provider_Settings__get_LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED_);
        value_to_get_map.put(Settings.System.TIME_12_24,
                SEEMP_API_android_provider_Settings__get_TIME_12_24_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT_);
        value_to_get_map.put(Settings.System.VOLUME_BLUETOOTH_SCO,
                SEEMP_API_android_provider_Settings__get_VOLUME_BLUETOOTH_SCO_);
        value_to_get_map.put(Settings.System.USER_ROTATION,
                SEEMP_API_android_provider_Settings__get_USER_ROTATION_);
        value_to_get_map.put(Settings.System.WIFI_STATIC_GATEWAY,
                SEEMP_API_android_provider_Settings__get_WIFI_STATIC_GATEWAY_);
        value_to_get_map.put(Settings.System.STAY_ON_WHILE_PLUGGED_IN,
                SEEMP_API_android_provider_Settings__get_STAY_ON_WHILE_PLUGGED_IN_);
        value_to_get_map.put(Settings.System.SOUND_EFFECTS_ENABLED,
                SEEMP_API_android_provider_Settings__get_SOUND_EFFECTS_ENABLED_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_PING_COUNT,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_PING_COUNT_);
        value_to_get_map.put(Settings.System.DATA_ROAMING,
                SEEMP_API_android_provider_Settings__get_DATA_ROAMING_);
        value_to_get_map.put(Settings.System.SETTINGS_CLASSNAME,
                SEEMP_API_android_provider_Settings__get_SETTINGS_CLASSNAME_);
        value_to_get_map.put(Settings.System.TRANSITION_ANIMATION_SCALE,
                SEEMP_API_android_provider_Settings__get_TRANSITION_ANIMATION_SCALE_);
        value_to_get_map.put(Settings.System.WAIT_FOR_DEBUGGER,
                SEEMP_API_android_provider_Settings__get_WAIT_FOR_DEBUGGER_);
        value_to_get_map.put(Settings.System.INSTALL_NON_MARKET_APPS,
                SEEMP_API_android_provider_Settings__get_INSTALL_NON_MARKET_APPS_);
        value_to_get_map.put(Settings.System.ADB_ENABLED,
                SEEMP_API_android_provider_Settings__get_ADB_ENABLED_);
        value_to_get_map.put(Settings.System.WIFI_USE_STATIC_IP,
                SEEMP_API_android_provider_Settings__get_WIFI_USE_STATIC_IP_);
        value_to_get_map.put(Settings.System.DIM_SCREEN,
                SEEMP_API_android_provider_Settings__get_DIM_SCREEN_);
        value_to_get_map.put(Settings.System.VOLUME_ALARM,
                SEEMP_API_android_provider_Settings__get_VOLUME_ALARM_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_ON,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_ON_);
        value_to_get_map.put(Settings.System.WIFI_STATIC_NETMASK,
                SEEMP_API_android_provider_Settings__get_WIFI_STATIC_NETMASK_);
        value_to_get_map.put(Settings.System.NETWORK_PREFERENCE,
                SEEMP_API_android_provider_Settings__get_NETWORK_PREFERENCE_);
        value_to_get_map.put(Settings.System.SHOW_PROCESSES,
                SEEMP_API_android_provider_Settings__get_SHOW_PROCESSES_);
        value_to_get_map.put(Settings.System.TEXT_AUTO_REPLACE,
                SEEMP_API_android_provider_Settings__get_TEXT_AUTO_REPLACE_);
        value_to_get_map.put(Settings.System.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                SEEMP_API_android_provider_Settings__get_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON_);
        value_to_get_map.put(Settings.System.APPEND_FOR_LAST_AUDIBLE,
                SEEMP_API_android_provider_Settings__get_APPEND_FOR_LAST_AUDIBLE_);
        value_to_get_map.put(Settings.System.SHOW_GTALK_SERVICE_STATUS,
                SEEMP_API_android_provider_Settings__get_SHOW_GTALK_SERVICE_STATUS_);
        value_to_get_map.put(Settings.System.SCREEN_BRIGHTNESS,
                SEEMP_API_android_provider_Settings__get_SCREEN_BRIGHTNESS_);
        value_to_get_map.put(Settings.System.USE_GOOGLE_MAIL,
                SEEMP_API_android_provider_Settings__get_USE_GOOGLE_MAIL_);
        value_to_get_map.put(Settings.System.RINGTONE,
                SEEMP_API_android_provider_Settings__get_RINGTONE_);
        value_to_get_map.put(Settings.System.LOGGING_ID,
                SEEMP_API_android_provider_Settings__get_LOGGING_ID_);
        value_to_get_map.put(Settings.System.MODE_RINGER,
                SEEMP_API_android_provider_Settings__get_MODE_RINGER_);
        value_to_get_map.put(Settings.System.MUTE_STREAMS_AFFECTED,
                SEEMP_API_android_provider_Settings__get_MUTE_STREAMS_AFFECTED_);
        value_to_get_map.put(Settings.System.WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE,
                SEEMP_API_android_provider_Settings__get_WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE_);
        value_to_get_map.put(Settings.System.TEXT_AUTO_PUNCTUATE,
                SEEMP_API_android_provider_Settings__get_TEXT_AUTO_PUNCTUATE_);
        value_to_get_map.put(Settings.System.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS,
                SEEMP_API_android_provider_Settings__get_WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS_);
        value_to_get_map.put(Settings.System.SCREEN_BRIGHTNESS_MODE,
                SEEMP_API_android_provider_Settings__get_SCREEN_BRIGHTNESS_MODE_);
    }

    public static int getSeempGetApiIdFromValue( String v )
    {
        Integer result = value_to_get_map.get( v );
        if (result == null)
        {
            result = -1;
        }
        return result;
    }

    private final static java.util.Map<String,Integer> value_to_put_map;
    static {
        value_to_put_map = new java.util.HashMap<String,Integer>( 198 );
        value_to_put_map.put(Settings.System.NOTIFICATION_SOUND,
                SEEMP_API_android_provider_Settings__put_NOTIFICATION_SOUND_);
        value_to_put_map.put(Settings.System.DTMF_TONE_WHEN_DIALING,
                SEEMP_API_android_provider_Settings__put_DTMF_TONE_WHEN_DIALING_);
        value_to_put_map.put(Settings.System.LOCK_PATTERN_ENABLED,
                SEEMP_API_android_provider_Settings__put_LOCK_PATTERN_ENABLED_);
        value_to_put_map.put(Settings.System.WIFI_MAX_DHCP_RETRY_COUNT,
                SEEMP_API_android_provider_Settings__put_WIFI_MAX_DHCP_RETRY_COUNT_);
        value_to_put_map.put(Settings.System.AUTO_TIME,
                SEEMP_API_android_provider_Settings__put_AUTO_TIME_);
        value_to_put_map.put(Settings.System.SETUP_WIZARD_HAS_RUN,
                SEEMP_API_android_provider_Settings__put_SETUP_WIZARD_HAS_RUN_);
        value_to_put_map.put(Settings.System.SYS_PROP_SETTING_VERSION,
                SEEMP_API_android_provider_Settings__put_SYS_PROP_SETTING_VERSION_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS_);
        value_to_put_map.put(Settings.System.LOCATION_PROVIDERS_ALLOWED,
                SEEMP_API_android_provider_Settings__put_LOCATION_PROVIDERS_ALLOWED_);
        value_to_put_map.put(Settings.System.ALARM_ALERT,
                SEEMP_API_android_provider_Settings__put_ALARM_ALERT_);
        value_to_put_map.put(Settings.System.VIBRATE_ON,
                SEEMP_API_android_provider_Settings__put_VIBRATE_ON_);
        value_to_put_map.put(Settings.System.USB_MASS_STORAGE_ENABLED,
                SEEMP_API_android_provider_Settings__put_USB_MASS_STORAGE_ENABLED_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_PING_DELAY_MS,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_PING_DELAY_MS_);
        value_to_put_map.put(Settings.System.FONT_SCALE,
                SEEMP_API_android_provider_Settings__put_FONT_SCALE_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_AP_COUNT,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_AP_COUNT_);
        value_to_put_map.put(Settings.System.ALWAYS_FINISH_ACTIVITIES,
                SEEMP_API_android_provider_Settings__put_ALWAYS_FINISH_ACTIVITIES_);
        value_to_put_map.put(Settings.System.ACCELEROMETER_ROTATION,
                SEEMP_API_android_provider_Settings__put_ACCELEROMETER_ROTATION_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_PING_TIMEOUT_MS,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_PING_TIMEOUT_MS_);
        value_to_put_map.put(Settings.System.VOLUME_NOTIFICATION,
                SEEMP_API_android_provider_Settings__put_VOLUME_NOTIFICATION_);
        value_to_put_map.put(Settings.System.AIRPLANE_MODE_ON,
                SEEMP_API_android_provider_Settings__put_AIRPLANE_MODE_ON_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS_);
        value_to_put_map.put(Settings.System.WIFI_STATIC_IP,
                SEEMP_API_android_provider_Settings__put_WIFI_STATIC_IP_);
        value_to_put_map.put(Settings.System.RADIO_BLUETOOTH,
                SEEMP_API_android_provider_Settings__put_RADIO_BLUETOOTH_);
        value_to_put_map.put(Settings.System.BLUETOOTH_DISCOVERABILITY_TIMEOUT,
                SEEMP_API_android_provider_Settings__put_BLUETOOTH_DISCOVERABILITY_TIMEOUT_);
        value_to_put_map.put(Settings.System.VOLUME_RING,
                SEEMP_API_android_provider_Settings__put_VOLUME_RING_);
        value_to_put_map.put(Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                SEEMP_API_android_provider_Settings__put_MODE_RINGER_STREAMS_AFFECTED_);
        value_to_put_map.put(Settings.System.VOLUME_SYSTEM,
                SEEMP_API_android_provider_Settings__put_VOLUME_SYSTEM_);
        value_to_put_map.put(Settings.System.SCREEN_OFF_TIMEOUT,
                SEEMP_API_android_provider_Settings__put_SCREEN_OFF_TIMEOUT_);
        value_to_put_map.put(Settings.System.RADIO_WIFI,
                SEEMP_API_android_provider_Settings__put_RADIO_WIFI_);
        value_to_put_map.put(Settings.System.AUTO_TIME_ZONE,
                SEEMP_API_android_provider_Settings__put_AUTO_TIME_ZONE_);
        value_to_put_map.put(Settings.System.TEXT_AUTO_CAPS,
                SEEMP_API_android_provider_Settings__put_TEXT_AUTO_CAPS_);
        value_to_put_map.put(Settings.System.WALLPAPER_ACTIVITY,
                SEEMP_API_android_provider_Settings__put_WALLPAPER_ACTIVITY_);
        value_to_put_map.put(Settings.System.ANIMATOR_DURATION_SCALE,
                SEEMP_API_android_provider_Settings__put_ANIMATOR_DURATION_SCALE_);
        value_to_put_map.put(Settings.System.WIFI_NUM_OPEN_NETWORKS_KEPT,
                SEEMP_API_android_provider_Settings__put_WIFI_NUM_OPEN_NETWORKS_KEPT_);
        value_to_put_map.put(Settings.System.LOCK_PATTERN_VISIBLE,
                SEEMP_API_android_provider_Settings__put_LOCK_PATTERN_VISIBLE_);
        value_to_put_map.put(Settings.System.VOLUME_VOICE,
                SEEMP_API_android_provider_Settings__put_VOLUME_VOICE_);
        value_to_put_map.put(Settings.System.DEBUG_APP,
                SEEMP_API_android_provider_Settings__put_DEBUG_APP_);
        value_to_put_map.put(Settings.System.WIFI_ON,
                SEEMP_API_android_provider_Settings__put_WIFI_ON_);
        value_to_put_map.put(Settings.System.TEXT_SHOW_PASSWORD,
                SEEMP_API_android_provider_Settings__put_TEXT_SHOW_PASSWORD_);
        value_to_put_map.put(Settings.System.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY,
                SEEMP_API_android_provider_Settings__put_WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY_);
        value_to_put_map.put(Settings.System.WIFI_SLEEP_POLICY,
                SEEMP_API_android_provider_Settings__put_WIFI_SLEEP_POLICY_);
        value_to_put_map.put(Settings.System.VOLUME_MUSIC,
                SEEMP_API_android_provider_Settings__put_VOLUME_MUSIC_);
        value_to_put_map.put(Settings.System.PARENTAL_CONTROL_LAST_UPDATE,
                SEEMP_API_android_provider_Settings__put_PARENTAL_CONTROL_LAST_UPDATE_);
        value_to_put_map.put(Settings.System.DEVICE_PROVISIONED,
                SEEMP_API_android_provider_Settings__put_DEVICE_PROVISIONED_);
        value_to_put_map.put(Settings.System.HTTP_PROXY,
                SEEMP_API_android_provider_Settings__put_HTTP_PROXY_);
        value_to_put_map.put(Settings.System.ANDROID_ID,
                SEEMP_API_android_provider_Settings__put_ANDROID_ID_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_MAX_AP_CHECKS,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_MAX_AP_CHECKS_);
        value_to_put_map.put(Settings.System.END_BUTTON_BEHAVIOR,
                SEEMP_API_android_provider_Settings__put_END_BUTTON_BEHAVIOR_);
        value_to_put_map.put(Settings.System.NEXT_ALARM_FORMATTED,
                SEEMP_API_android_provider_Settings__put_NEXT_ALARM_FORMATTED_);
        value_to_put_map.put(Settings.System.RADIO_CELL,
                SEEMP_API_android_provider_Settings__put_RADIO_CELL_);
        value_to_put_map.put(Settings.System.PARENTAL_CONTROL_ENABLED,
                SEEMP_API_android_provider_Settings__put_PARENTAL_CONTROL_ENABLED_);
        value_to_put_map.put(Settings.System.BLUETOOTH_ON,
                SEEMP_API_android_provider_Settings__put_BLUETOOTH_ON_);
        value_to_put_map.put(Settings.System.WINDOW_ANIMATION_SCALE,
                SEEMP_API_android_provider_Settings__put_WINDOW_ANIMATION_SCALE_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED_);
        value_to_put_map.put(Settings.System.BLUETOOTH_DISCOVERABILITY,
                SEEMP_API_android_provider_Settings__put_BLUETOOTH_DISCOVERABILITY_);
        value_to_put_map.put(Settings.System.WIFI_STATIC_DNS1,
                SEEMP_API_android_provider_Settings__put_WIFI_STATIC_DNS1_);
        value_to_put_map.put(Settings.System.WIFI_STATIC_DNS2,
                SEEMP_API_android_provider_Settings__put_WIFI_STATIC_DNS2_);
        value_to_put_map.put(Settings.System.HAPTIC_FEEDBACK_ENABLED,
                SEEMP_API_android_provider_Settings__put_HAPTIC_FEEDBACK_ENABLED_);
        value_to_put_map.put(Settings.System.SHOW_WEB_SUGGESTIONS,
                SEEMP_API_android_provider_Settings__put_SHOW_WEB_SUGGESTIONS_);
        value_to_put_map.put(Settings.System.PARENTAL_CONTROL_REDIRECT_URL,
                SEEMP_API_android_provider_Settings__put_PARENTAL_CONTROL_REDIRECT_URL_);
        value_to_put_map.put(Settings.System.DATE_FORMAT,
                SEEMP_API_android_provider_Settings__put_DATE_FORMAT_);
        value_to_put_map.put(Settings.System.RADIO_NFC,
                SEEMP_API_android_provider_Settings__put_RADIO_NFC_);
        value_to_put_map.put(Settings.System.AIRPLANE_MODE_RADIOS,
                SEEMP_API_android_provider_Settings__put_AIRPLANE_MODE_RADIOS_);
        value_to_put_map.put(Settings.System.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED,
                SEEMP_API_android_provider_Settings__put_LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED_);
        value_to_put_map.put(Settings.System.TIME_12_24,
                SEEMP_API_android_provider_Settings__put_TIME_12_24_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT_);
        value_to_put_map.put(Settings.System.VOLUME_BLUETOOTH_SCO,
                SEEMP_API_android_provider_Settings__put_VOLUME_BLUETOOTH_SCO_);
        value_to_put_map.put(Settings.System.USER_ROTATION,
                SEEMP_API_android_provider_Settings__put_USER_ROTATION_);
        value_to_put_map.put(Settings.System.WIFI_STATIC_GATEWAY,
                SEEMP_API_android_provider_Settings__put_WIFI_STATIC_GATEWAY_);
        value_to_put_map.put(Settings.System.STAY_ON_WHILE_PLUGGED_IN,
                SEEMP_API_android_provider_Settings__put_STAY_ON_WHILE_PLUGGED_IN_);
        value_to_put_map.put(Settings.System.SOUND_EFFECTS_ENABLED,
                SEEMP_API_android_provider_Settings__put_SOUND_EFFECTS_ENABLED_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_PING_COUNT,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_PING_COUNT_);
        value_to_put_map.put(Settings.System.DATA_ROAMING,
                SEEMP_API_android_provider_Settings__put_DATA_ROAMING_);
        value_to_put_map.put(Settings.System.SETTINGS_CLASSNAME,
                SEEMP_API_android_provider_Settings__put_SETTINGS_CLASSNAME_);
        value_to_put_map.put(Settings.System.TRANSITION_ANIMATION_SCALE,
                SEEMP_API_android_provider_Settings__put_TRANSITION_ANIMATION_SCALE_);
        value_to_put_map.put(Settings.System.WAIT_FOR_DEBUGGER,
                SEEMP_API_android_provider_Settings__put_WAIT_FOR_DEBUGGER_);
        value_to_put_map.put(Settings.System.INSTALL_NON_MARKET_APPS,
                SEEMP_API_android_provider_Settings__put_INSTALL_NON_MARKET_APPS_);
        value_to_put_map.put(Settings.System.ADB_ENABLED,
                SEEMP_API_android_provider_Settings__put_ADB_ENABLED_);
        value_to_put_map.put(Settings.System.WIFI_USE_STATIC_IP,
                SEEMP_API_android_provider_Settings__put_WIFI_USE_STATIC_IP_);
        value_to_put_map.put(Settings.System.DIM_SCREEN,
                SEEMP_API_android_provider_Settings__put_DIM_SCREEN_);
        value_to_put_map.put(Settings.System.VOLUME_ALARM,
                SEEMP_API_android_provider_Settings__put_VOLUME_ALARM_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_ON,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_ON_);
        value_to_put_map.put(Settings.System.WIFI_STATIC_NETMASK,
                SEEMP_API_android_provider_Settings__put_WIFI_STATIC_NETMASK_);
        value_to_put_map.put(Settings.System.NETWORK_PREFERENCE,
                SEEMP_API_android_provider_Settings__put_NETWORK_PREFERENCE_);
        value_to_put_map.put(Settings.System.SHOW_PROCESSES,
                SEEMP_API_android_provider_Settings__put_SHOW_PROCESSES_);
        value_to_put_map.put(Settings.System.TEXT_AUTO_REPLACE,
                SEEMP_API_android_provider_Settings__put_TEXT_AUTO_REPLACE_);
        value_to_put_map.put(Settings.System.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                SEEMP_API_android_provider_Settings__put_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON_);
        value_to_put_map.put(Settings.System.APPEND_FOR_LAST_AUDIBLE,
                SEEMP_API_android_provider_Settings__put_APPEND_FOR_LAST_AUDIBLE_);
        value_to_put_map.put(Settings.System.SHOW_GTALK_SERVICE_STATUS,
                SEEMP_API_android_provider_Settings__put_SHOW_GTALK_SERVICE_STATUS_);
        value_to_put_map.put(Settings.System.SCREEN_BRIGHTNESS,
                SEEMP_API_android_provider_Settings__put_SCREEN_BRIGHTNESS_);
        value_to_put_map.put(Settings.System.USE_GOOGLE_MAIL,
                SEEMP_API_android_provider_Settings__put_USE_GOOGLE_MAIL_);
        value_to_put_map.put(Settings.System.RINGTONE,
                SEEMP_API_android_provider_Settings__put_RINGTONE_);
        value_to_put_map.put(Settings.System.LOGGING_ID,
                SEEMP_API_android_provider_Settings__put_LOGGING_ID_);
        value_to_put_map.put(Settings.System.MODE_RINGER,
                SEEMP_API_android_provider_Settings__put_MODE_RINGER_);
        value_to_put_map.put(Settings.System.MUTE_STREAMS_AFFECTED,
                SEEMP_API_android_provider_Settings__put_MUTE_STREAMS_AFFECTED_);
        value_to_put_map.put(Settings.System.WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE,
                SEEMP_API_android_provider_Settings__put_WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE_);
        value_to_put_map.put(Settings.System.TEXT_AUTO_PUNCTUATE,
                SEEMP_API_android_provider_Settings__put_TEXT_AUTO_PUNCTUATE_);
        value_to_put_map.put(Settings.System.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS,
                SEEMP_API_android_provider_Settings__put_WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS_);
        value_to_put_map.put(Settings.System.SCREEN_BRIGHTNESS_MODE,
                SEEMP_API_android_provider_Settings__put_SCREEN_BRIGHTNESS_MODE_);
    }

    public static int getSeempPutApiIdFromValue( String v )
    {
        Integer result = value_to_put_map.get( v );
        if (result == null)
        {
            result = -1;
        }
        return result;
    }
}
