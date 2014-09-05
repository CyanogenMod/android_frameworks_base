/*
 * Copyright (C) 2006 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2014, ParanoidAndroid Project.
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

package android.provider;

import android.annotation.ChaosLab;
import android.annotation.ChaosLab.Classification;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.app.SearchManager;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Build.VERSION_CODES;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.AndroidException;
import android.util.Log;

import com.android.internal.widget.ILockSettings;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * The Settings provider contains global system-level device preferences.
 */
public final class Settings {

    // Intent actions for Settings

    /**
     * Activity Action: Show system settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SETTINGS = "android.settings.SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of APNs.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APN_SETTINGS = "android.settings.APN_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of current location
     * sources.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCATION_SOURCE_SETTINGS =
            "android.settings.LOCATION_SOURCE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of wireless controls
     * such as Wi-Fi, Bluetooth and Mobile networks.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIRELESS_SETTINGS =
            "android.settings.WIRELESS_SETTINGS";

    /**
     * Activity Action: Show settings to allow entering/exiting airplane mode.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_AIRPLANE_MODE_SETTINGS =
            "android.settings.AIRPLANE_MODE_SETTINGS";

    /**
     * Activity Action: Show settings for accessibility modules.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of security and
     * location privacy.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SECURITY_SETTINGS =
            "android.settings.SECURITY_SETTINGS";

    /**
     * Activity Action: Show trusted credentials settings, opening to the user tab,
     * to allow management of installed credentials.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_TRUSTED_CREDENTIALS_USER =
            "com.android.settings.TRUSTED_CREDENTIALS_USER";

    /**
     * Activity Action: Show dialog explaining that an installed CA cert may enable
     * monitoring of encrypted network traffic.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MONITORING_CERT_INFO =
            "com.android.settings.MONITORING_CERT_INFO";

    /**
     * Activity Action: Show settings to allow configuration of privacy options.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PRIVACY_SETTINGS =
            "android.settings.PRIVACY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Wi-Fi.

     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.

     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIFI_SETTINGS =
            "android.settings.WIFI_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of a static IP
     * address for Wi-Fi.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIFI_IP_SETTINGS =
            "android.settings.WIFI_IP_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Bluetooth.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_BLUETOOTH_SETTINGS =
            "android.settings.BLUETOOTH_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Wifi Displays.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIFI_DISPLAY_SETTINGS =
            "android.settings.WIFI_DISPLAY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of date and time.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DATE_SETTINGS =
            "android.settings.DATE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of sound and volume.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SOUND_SETTINGS =
            "android.settings.SOUND_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of display.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DISPLAY_SETTINGS =
            "android.settings.DISPLAY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of locale.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCALE_SETTINGS =
            "android.settings.LOCALE_SETTINGS";

    /**
     * Activity Action: Show settings to configure input methods, in particular
     * allowing the user to enable input methods.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_INPUT_METHOD_SETTINGS =
            "android.settings.INPUT_METHOD_SETTINGS";

    /**
     * Activity Action: Show settings to enable/disable input method subtypes.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * To tell which input method's subtypes are displayed in the settings, add
     * {@link #EXTRA_INPUT_METHOD_ID} extra to this Intent with the input method id.
     * If there is no extra in this Intent, subtypes from all installed input methods
     * will be displayed in the settings.
     *
     * @see android.view.inputmethod.InputMethodInfo#getId
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_INPUT_METHOD_SUBTYPE_SETTINGS =
            "android.settings.INPUT_METHOD_SUBTYPE_SETTINGS";

    /**
     * Activity Action: Show a dialog to select input method.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SHOW_INPUT_METHOD_PICKER =
            "android.settings.SHOW_INPUT_METHOD_PICKER";

    /**
     * Activity Action: Show settings to manage the user input dictionary.
     * <p>
     * Starting with {@link android.os.Build.VERSION_CODES#KITKAT},
     * it is guaranteed there will always be an appropriate implementation for this Intent action.
     * In prior releases of the platform this was optional, so ensure you safeguard against it.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_USER_DICTIONARY_SETTINGS =
            "android.settings.USER_DICTIONARY_SETTINGS";

    /**
     * Activity Action: Adds a word to the user dictionary.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: An extra with key <code>word</code> that contains the word
     * that should be added to the dictionary.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_USER_DICTIONARY_INSERT =
            "com.android.settings.USER_DICTIONARY_INSERT";

    /**
     * Activity Action: Show settings to allow configuration of application-related settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_SETTINGS =
            "android.settings.APPLICATION_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of application
     * development-related settings.  As of
     * {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR1} this action is
     * a required part of the platform.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_DEVELOPMENT_SETTINGS =
            "android.settings.APPLICATION_DEVELOPMENT_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of quick launch shortcuts.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_QUICK_LAUNCH_SETTINGS =
            "android.settings.QUICK_LAUNCH_SETTINGS";

    /**
     * Activity Action: Show settings to manage installed applications.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_APPLICATIONS_SETTINGS =
            "android.settings.MANAGE_APPLICATIONS_SETTINGS";

    /**
     * Activity Action: Show settings to manage all applications.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS =
            "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS";

    /**
     * Activity Action: Show screen of details about a particular application.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: The Intent's data URI specifies the application package name
     * to be shown, with the "package" scheme.  That is "package:com.my.app".
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS =
            "android.settings.APPLICATION_DETAILS_SETTINGS";

    /**
     * @hide
     * Activity Action: Show the "app ops" settings screen.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APP_OPS_SETTINGS =
            "android.settings.APP_OPS_SETTINGS";

    /**
     * Activity Action: Show settings for system update functionality.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SYSTEM_UPDATE_SETTINGS =
            "android.settings.SYSTEM_UPDATE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of sync settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * The account types available to add via the add account button may be restricted by adding an
     * {@link #EXTRA_AUTHORITIES} extra to this Intent with one or more syncable content provider's
     * authorities. Only account types which can sync with that content provider will be offered to
     * the user.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SYNC_SETTINGS =
            "android.settings.SYNC_SETTINGS";

    /**
     * Activity Action: Show add account screen for creating a new account.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * The account types available to add may be restricted by adding an {@link #EXTRA_AUTHORITIES}
     * extra to the Intent with one or more syncable content provider's authorities.  Only account
     * types which can sync with that content provider will be offered to the user.
     * <p>
     * Account types can also be filtered by adding an {@link #EXTRA_ACCOUNT_TYPES} extra to the
     * Intent with one or more account types.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ADD_ACCOUNT =
            "android.settings.ADD_ACCOUNT_SETTINGS";

    /**
     * Activity Action: Show settings for selecting the network operator.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NETWORK_OPERATOR_SETTINGS =
            "android.settings.NETWORK_OPERATOR_SETTINGS";

    /**
     * Activity Action: Show settings for selection of 2G/3G.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DATA_ROAMING_SETTINGS =
            "android.settings.DATA_ROAMING_SETTINGS";

    /**
     * Activity Action: Show settings for internal storage.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_INTERNAL_STORAGE_SETTINGS =
            "android.settings.INTERNAL_STORAGE_SETTINGS";
    /**
     * Activity Action: Show settings for memory card storage.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MEMORY_CARD_SETTINGS =
            "android.settings.MEMORY_CARD_SETTINGS";

    /**
     * Activity Action: Show settings for global search.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SEARCH_SETTINGS =
        "android.search.action.SEARCH_SETTINGS";

    /**
     * Activity Action: Show general device information settings (serial
     * number, software version, phone number, etc.).
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DEVICE_INFO_SETTINGS =
        "android.settings.DEVICE_INFO_SETTINGS";

    /**
     * Activity Action: Show NFC settings.
     * <p>
     * This shows UI that allows NFC to be turned on or off.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     * @see android.nfc.NfcAdapter#isEnabled()
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NFC_SETTINGS = "android.settings.NFC_SETTINGS";

    /**
     * Activity Action: Show NFC Sharing settings.
     * <p>
     * This shows UI that allows NDEF Push (Android Beam) to be turned on or
     * off.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     * @see android.nfc.NfcAdapter#isNdefPushEnabled()
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NFCSHARING_SETTINGS =
        "android.settings.NFCSHARING_SETTINGS";

    /**
     * Activity Action: Show NFC Tap & Pay settings
     * <p>
     * This shows UI that allows the user to configure Tap&Pay
     * settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NFC_PAYMENT_SETTINGS =
        "android.settings.NFC_PAYMENT_SETTINGS";

    /**
     * Activity Action: Show Daydream settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @see android.service.dreams.DreamService
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DREAM_SETTINGS = "android.settings.DREAM_SETTINGS";

    /**
     * Activity Action: Show Notification listener settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @see android.service.notification.NotificationListenerService
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NOTIFICATION_LISTENER_SETTINGS
            = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    /**
     * Activity Action: Show settings for video captioning.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CAPTIONING_SETTINGS = "android.settings.CAPTIONING_SETTINGS";

    /**
     * Activity Action: Show the top level print settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PRINT_SETTINGS =
            "android.settings.ACTION_PRINT_SETTINGS";

    // End of Intent actions for Settings

    /**
     * @hide - Private call() method on SettingsProvider to read from 'system' table.
     */
    public static final String CALL_METHOD_GET_SYSTEM = "GET_system";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'secure' table.
     */
    public static final String CALL_METHOD_GET_SECURE = "GET_secure";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'global' table.
     */
    public static final String CALL_METHOD_GET_GLOBAL = "GET_global";

    /**
     * @hide - User handle argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_USER_KEY = "_user";

    /** @hide - Private call() method to write to 'system' table */
    public static final String CALL_METHOD_PUT_SYSTEM = "PUT_system";

    /** @hide - Private call() method to write to 'secure' table */
    public static final String CALL_METHOD_PUT_SECURE = "PUT_secure";

    /** @hide - Private call() method to write to 'global' table */
    public static final String CALL_METHOD_PUT_GLOBAL= "PUT_global";

    /**
     * Activity Extra: Limit available options in launched activity based on the given authority.
     * <p>
     * This can be passed as an extra field in an Activity Intent with one or more syncable content
     * provider's authorities as a String[]. This field is used by some intents to alter the
     * behavior of the called activity.
     * <p>
     * Example: The {@link #ACTION_ADD_ACCOUNT} intent restricts the account types available based
     * on the authority given.
     */
    public static final String EXTRA_AUTHORITIES = "authorities";

    /**
     * Activity Extra: Limit available options in launched activity based on the given account
     * types.
     * <p>
     * This can be passed as an extra field in an Activity Intent with one or more account types
     * as a String[]. This field is used by some intents to alter the behavior of the called
     * activity.
     * <p>
     * Example: The {@link #ACTION_ADD_ACCOUNT} intent restricts the account types to the specified
     * list.
     */
    public static final String EXTRA_ACCOUNT_TYPES = "account_types";

    public static final String EXTRA_INPUT_METHOD_ID = "input_method_id";

    private static final String JID_RESOURCE_PREFIX = "android";

    public static final String AUTHORITY = "settings";

    private static final String TAG = "Settings";
    private static final boolean LOCAL_LOGV = false;

    // Lock ensures that when enabling/disabling the master location switch, we don't end up
    // with a partial enable/disable state in multi-threaded situations.
    private static final Object mLocationSettingsLock = new Object();

    public static class SettingNotFoundException extends AndroidException {
        public SettingNotFoundException(String msg) {
            super(msg);
        }
    }

    /**
     * Common base for tables of name/value settings.
     */
    public static class NameValueTable implements BaseColumns {
        public static final String NAME = "name";
        public static final String VALUE = "value";

        protected static boolean putString(ContentResolver resolver, Uri uri,
                String name, String value) {
            // The database will take care of replacing duplicates.
            try {
                ContentValues values = new ContentValues();
                values.put(NAME, name);
                values.put(VALUE, value);
                resolver.insert(uri, values);
                return true;
            } catch (SQLException e) {
                Log.w(TAG, "Can't set key " + name + " in " + uri, e);
                return false;
            }
        }

        public static Uri getUriFor(Uri uri, String name) {
            return Uri.withAppendedPath(uri, name);
        }
    }

    // Thread-safe.
    private static class NameValueCache {
        private final String mVersionSystemProperty;
        private final Uri mUri;

        private static final String[] SELECT_VALUE =
            new String[] { Settings.NameValueTable.VALUE };
        private static final String NAME_EQ_PLACEHOLDER = "name=?";

        // Must synchronize on 'this' to access mValues and mValuesVersion.
        private final HashMap<String, String> mValues = new HashMap<String, String>();
        private long mValuesVersion = 0;

        // Initially null; set lazily and held forever.  Synchronized on 'this'.
        private IContentProvider mContentProvider = null;

        // The method we'll call (or null, to not use) on the provider
        // for the fast path of retrieving settings.
        private final String mCallGetCommand;
        private final String mCallSetCommand;

        public NameValueCache(String versionSystemProperty, Uri uri,
                String getCommand, String setCommand) {
            mVersionSystemProperty = versionSystemProperty;
            mUri = uri;
            mCallGetCommand = getCommand;
            mCallSetCommand = setCommand;
        }

        private IContentProvider lazyGetProvider(ContentResolver cr) {
            IContentProvider cp = null;
            synchronized (this) {
                cp = mContentProvider;
                if (cp == null) {
                    cp = mContentProvider = cr.acquireProvider(mUri.getAuthority());
                }
            }
            return cp;
        }

        public boolean putStringForUser(ContentResolver cr, String name, String value,
                final int userHandle) {
            try {
                Bundle arg = new Bundle();
                arg.putString(Settings.NameValueTable.VALUE, value);
                arg.putInt(CALL_METHOD_USER_KEY, userHandle);
                IContentProvider cp = lazyGetProvider(cr);
                cp.call(cr.getPackageName(), mCallSetCommand, name, arg);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't set key " + name + " in " + mUri, e);
                return false;
            }
            return true;
        }

        public String getStringForUser(ContentResolver cr, String name, final int userHandle) {
            final boolean isSelf = (userHandle == UserHandle.myUserId());
            if (isSelf) {
                long newValuesVersion = SystemProperties.getLong(mVersionSystemProperty, 0);

                // Our own user's settings data uses a client-side cache
                synchronized (this) {
                    if (mValuesVersion != newValuesVersion) {
                        if (LOCAL_LOGV || false) {
                            Log.v(TAG, "invalidate [" + mUri.getLastPathSegment() + "]: current "
                                    + newValuesVersion + " != cached " + mValuesVersion);
                        }

                        mValues.clear();
                        mValuesVersion = newValuesVersion;
                    }

                    if (mValues.containsKey(name)) {
                        return mValues.get(name);  // Could be null, that's OK -- negative caching
                    }
                }
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "get setting for user " + userHandle
                        + " by user " + UserHandle.myUserId() + " so skipping cache");
            }

            IContentProvider cp = lazyGetProvider(cr);

            // Try the fast path first, not using query().  If this
            // fails (alternate Settings provider that doesn't support
            // this interface?) then we fall back to the query/table
            // interface.
            if (mCallGetCommand != null) {
                try {
                    Bundle args = null;
                    if (!isSelf) {
                        args = new Bundle();
                        args.putInt(CALL_METHOD_USER_KEY, userHandle);
                    }
                    Bundle b = cp.call(cr.getPackageName(), mCallGetCommand, name, args);
                    if (b != null) {
                        String value = b.getPairValue();
                        // Don't update our cache for reads of other users' data
                        if (isSelf) {
                            synchronized (this) {
                                mValues.put(name, value);
                            }
                        } else {
                            if (LOCAL_LOGV) Log.i(TAG, "call-query of user " + userHandle
                                    + " by " + UserHandle.myUserId()
                                    + " so not updating cache");
                        }
                        return value;
                    }
                    // If the response Bundle is null, we fall through
                    // to the query interface below.
                } catch (RemoteException e) {
                    // Not supported by the remote side?  Fall through
                    // to query().
                }
            }

            Cursor c = null;
            try {
                c = cp.query(cr.getPackageName(), mUri, SELECT_VALUE, NAME_EQ_PLACEHOLDER,
                             new String[]{name}, null, null);
                if (c == null) {
                    Log.w(TAG, "Can't get key " + name + " from " + mUri);
                    return null;
                }

                String value = c.moveToNext() ? c.getString(0) : null;
                synchronized (this) {
                    mValues.put(name, value);
                }
                if (LOCAL_LOGV) {
                    Log.v(TAG, "cache miss [" + mUri.getLastPathSegment() + "]: " +
                            name + " = " + (value == null ? "(null)" : value));
                }
                return value;
            } catch (RemoteException e) {
                Log.w(TAG, "Can't get key " + name + " from " + mUri, e);
                return null;  // Return null, but don't cache it.
            } finally {
                if (c != null) c.close();
            }
        }
    }

    /**
     * System settings, containing miscellaneous system preferences.  This
     * table holds simple name/value pairs.  There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class System extends NameValueTable {
        public static final String SYS_PROP_SETTING_VERSION = "sys.settings_system_version";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/system");

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_SYSTEM,
                CALL_METHOD_PUT_SYSTEM);

        private static final HashSet<String> MOVED_TO_SECURE;
        static {
            MOVED_TO_SECURE = new HashSet<String>(30);
            MOVED_TO_SECURE.add(Secure.ANDROID_ID);
            MOVED_TO_SECURE.add(Secure.HTTP_PROXY);
            MOVED_TO_SECURE.add(Secure.LOCATION_PROVIDERS_ALLOWED);
            MOVED_TO_SECURE.add(Secure.LOCK_BIOMETRIC_WEAK_FLAGS);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_ENABLED);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_VISIBLE);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED);
            MOVED_TO_SECURE.add(Secure.LOCK_NUMPAD_RANDOM);
            MOVED_TO_SECURE.add(Secure.LOCK_BEFORE_UNLOCK);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_SIZE);
            MOVED_TO_SECURE.add(Secure.LOCK_DOTS_VISIBLE);
            MOVED_TO_SECURE.add(Secure.LOCK_SHOW_ERROR_PATH);
            MOVED_TO_SECURE.add(Secure.LOGGING_ID);
            MOVED_TO_SECURE.add(Secure.PARENTAL_CONTROL_ENABLED);
            MOVED_TO_SECURE.add(Secure.PARENTAL_CONTROL_LAST_UPDATE);
            MOVED_TO_SECURE.add(Secure.PARENTAL_CONTROL_REDIRECT_URL);
            MOVED_TO_SECURE.add(Secure.SETTINGS_CLASSNAME);
            MOVED_TO_SECURE.add(Secure.USE_GOOGLE_MAIL);
            MOVED_TO_SECURE.add(Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
            MOVED_TO_SECURE.add(Secure.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY);
            MOVED_TO_SECURE.add(Secure.WIFI_NUM_OPEN_NETWORKS_KEPT);
            MOVED_TO_SECURE.add(Secure.WIFI_ON);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_AP_COUNT);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_MAX_AP_CHECKS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_ON);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_PING_COUNT);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_PING_DELAY_MS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_PING_TIMEOUT_MS);
        }

        private static final HashSet<String> MOVED_TO_GLOBAL;
        private static final HashSet<String> MOVED_TO_SECURE_THEN_GLOBAL;
        static {
            MOVED_TO_GLOBAL = new HashSet<String>();
            MOVED_TO_SECURE_THEN_GLOBAL = new HashSet<String>();

            // these were originally in system but migrated to secure in the past,
            // so are duplicated in the Secure.* namespace
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.ADB_ENABLED);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.BLUETOOTH_ON);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.DATA_ROAMING);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.DEVICE_PROVISIONED);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.INSTALL_NON_MARKET_APPS);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.USB_MASS_STORAGE_ENABLED);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.HTTP_PROXY);

            // these are moving directly from system to global
            MOVED_TO_GLOBAL.add(Settings.Global.AIRPLANE_MODE_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.AIRPLANE_MODE_RADIOS);
            MOVED_TO_GLOBAL.add(Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
            MOVED_TO_GLOBAL.add(Settings.Global.AUTO_TIME);
            MOVED_TO_GLOBAL.add(Settings.Global.AUTO_TIME_ZONE);
            MOVED_TO_GLOBAL.add(Settings.Global.CAR_DOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.CAR_UNDOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.DESK_DOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.DESK_UNDOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.DOCK_SOUNDS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.LOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.UNLOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.LOW_BATTERY_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.POWER_SOUNDS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_SLEEP_POLICY);
            MOVED_TO_GLOBAL.add(Settings.Global.MODE_RINGER);
            MOVED_TO_GLOBAL.add(Settings.Global.WINDOW_ANIMATION_SCALE);
            MOVED_TO_GLOBAL.add(Settings.Global.TRANSITION_ANIMATION_SCALE);
            MOVED_TO_GLOBAL.add(Settings.Global.ANIMATOR_DURATION_SCALE);
            MOVED_TO_GLOBAL.add(Settings.Global.FANCY_IME_ANIMATIONS);
            MOVED_TO_GLOBAL.add(Settings.Global.COMPATIBILITY_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.EMERGENCY_TONE);
            MOVED_TO_GLOBAL.add(Settings.Global.CALL_AUTO_RETRY);
            MOVED_TO_GLOBAL.add(Settings.Global.DEBUG_APP);
            MOVED_TO_GLOBAL.add(Settings.Global.WAIT_FOR_DEBUGGER);
            MOVED_TO_GLOBAL.add(Settings.Global.SHOW_PROCESSES);
            MOVED_TO_GLOBAL.add(Settings.Global.ALWAYS_FINISH_ACTIVITIES);
            MOVED_TO_GLOBAL.add(Settings.Global.TZINFO_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.TZINFO_UPDATE_METADATA_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SELINUX_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SELINUX_UPDATE_METADATA_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SMS_SHORT_CODES_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SMS_SHORT_CODES_UPDATE_METADATA_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.CERT_PIN_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.CERT_PIN_UPDATE_METADATA_URL);
        }

        /** @hide */
        public static void getMovedKeys(HashSet<String> outKeySet) {
            outKeySet.addAll(MOVED_TO_GLOBAL);
            outKeySet.addAll(MOVED_TO_SECURE_THEN_GLOBAL);
        }

        /** @hide */
        public static void getNonLegacyMovedKeys(HashSet<String> outKeySet) {
            outKeySet.addAll(MOVED_TO_GLOBAL);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userHandle) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Secure, returning read-only value.");
                return Secure.getStringForUser(resolver, name, userHandle);
            }
            if (MOVED_TO_GLOBAL.contains(name) || MOVED_TO_SECURE_THEN_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global, returning read-only value.");
                return Global.getStringForUser(resolver, name, userHandle);
            }
            return sNameValueCache.getStringForUser(resolver, name, userHandle);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
                int userHandle) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Secure, value is unchanged.");
                return false;
            }
            if (MOVED_TO_GLOBAL.contains(name) || MOVED_TO_SECURE_THEN_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global, value is unchanged.");
                return false;
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userHandle);
        }

        /**
         * Construct the content URI for a particular name/value pair,
         * useful for monitoring changes with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI, or null if not present
         */
        public static Uri getUriFor(String name) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                    + " to android.provider.Settings.Secure, returning Secure URI.");
                return Secure.getUriFor(Secure.CONTENT_URI, name);
            }
            if (MOVED_TO_GLOBAL.contains(name) || MOVED_TO_SECURE_THEN_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global, returning read-only global URI.");
                return Global.getUriFor(Global.CONTENT_URI, name);
            }
            return getUriFor(CONTENT_URI, name);
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userHandle) {
            return putStringForUser(cr, name, Integer.toString(value), userHandle);
        }

        /**
         * @hide
         * Convenience function for retrieving a single system settings value
         * as a boolean. Note that internally setting values are always
         * stored as strings; this function converts the string to a boolean
         * for you. It will only return true if the stored value is "1"
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static boolean getBoolean(ContentResolver cr, String name, boolean def) {
            return getBooleanForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean getBooleanForUser(ContentResolver cr, String name, boolean def,
                                                int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            try {
                if(v != null)
                    return "1".equals(v);
                else
                    return def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * @hide
         * Convenience function for updating a single settings value as a
         * boolean. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name. Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string (1 or 0) before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putBoolean(ContentResolver cr, String name, boolean value) {
            return putBooleanForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putBooleanForUser(ContentResolver cr, String name, boolean value,
                                                int userHandle) {
            return putStringForUser(cr, name, value ? "1" : "0", userHandle);
        }

        /**
         * @hide
         * Methods to handle storing and retrieving arraylists
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putArrayList(ContentResolver cr, String name, ArrayList<String> list) {
            return putArrayListForUser(cr, name, list, UserHandle.myUserId());
        }

        public static boolean putArrayListForUser(ContentResolver cr, String name, ArrayList<String> list, int userHandle) {
            if (list != null && list.size() > 0) {
                String joined = TextUtils.join("|",list);
                return putStringForUser(cr, name, joined, userHandle);
            } else {
                return putStringForUser(cr, name, "", userHandle);
            }
        }

        public static ArrayList<String> getArrayList(ContentResolver cr, String name) {
            return getArrayListForUser(cr, name,  UserHandle.myUserId());
        }

        public static ArrayList<String> getArrayListForUser(ContentResolver cr, String name, int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            ArrayList<String> list = new ArrayList<String>();
            if (v != null) {
                if (!v.isEmpty()){
                    String[] split = v.split("\\|");
                    for (String i : split) {
                        list.add(i);
                    }
                }
            }
            return list;
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userHandle) {
            String valString = getStringForUser(cr, name, userHandle);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String valString = getStringForUser(cr, name, userHandle);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userHandle) {
            return putStringForUser(cr, name, Long.toString(value), userHandle);
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String v = getStringForUser(cr, name, userHandle);
            if (v == null) {
                throw new SettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userHandle) {
            return putStringForUser(cr, name, Float.toString(value), userHandle);
        }

        /**
         * Convenience function to read all of the current
         * configuration-related settings into a
         * {@link Configuration} object.
         *
         * @param cr The ContentResolver to access.
         * @param outConfig Where to place the configuration settings.
         */
        public static void getConfiguration(ContentResolver cr, Configuration outConfig) {
            getConfigurationForUser(cr, outConfig, UserHandle.myUserId());
        }

        /** @hide */
        public static void getConfigurationForUser(ContentResolver cr, Configuration outConfig,
                int userHandle) {
            outConfig.fontScale = Settings.System.getFloatForUser(
                cr, FONT_SCALE, outConfig.fontScale, userHandle);
            if (outConfig.fontScale < 0) {
                outConfig.fontScale = 1;
            }
        }

        /**
         * @hide Erase the fields in the Configuration that should be applied
         * by the settings.
         */
        public static void clearConfiguration(Configuration inoutConfig) {
            inoutConfig.fontScale = 0;
        }

        /**
         * Convenience function to write a batch of configuration-related
         * settings from a {@link Configuration} object.
         *
         * @param cr The ContentResolver to access.
         * @param config The settings to write.
         * @return true if the values were set, false on database errors
         */
        public static boolean putConfiguration(ContentResolver cr, Configuration config) {
            return putConfigurationForUser(cr, config, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putConfigurationForUser(ContentResolver cr, Configuration config,
                int userHandle) {
            return Settings.System.putFloatForUser(cr, FONT_SCALE, config.fontScale, userHandle);
        }

        /** @hide */
        public static boolean hasInterestingConfigurationChanges(int changes) {
            return (changes & ActivityInfo.CONFIG_FONT_SCALE) != 0;
        }

        /** @deprecated - Do not use */
        @Deprecated
        public static boolean getShowGTalkServiceStatus(ContentResolver cr) {
            return getShowGTalkServiceStatusForUser(cr, UserHandle.myUserId());
        }

        /**
         * @hide
         * @deprecated - Do not use
         */
        public static boolean getShowGTalkServiceStatusForUser(ContentResolver cr,
                int userHandle) {
            return getIntForUser(cr, SHOW_GTALK_SERVICE_STATUS, 0, userHandle) != 0;
        }

        /** @deprecated - Do not use */
        @Deprecated
        public static void setShowGTalkServiceStatus(ContentResolver cr, boolean flag) {
            setShowGTalkServiceStatusForUser(cr, flag, UserHandle.myUserId());
        }

        /**
         * @hide
         * @deprecated - Do not use
         */
        @Deprecated
        public static void setShowGTalkServiceStatusForUser(ContentResolver cr, boolean flag,
                int userHandle) {
            putIntForUser(cr, SHOW_GTALK_SERVICE_STATUS, flag ? 1 : 0, userHandle);
        }

        /**
         * @deprecated Use {@link android.provider.Settings.Global#STAY_ON_WHILE_PLUGGED_IN} instead
         */
        @Deprecated
        public static final String STAY_ON_WHILE_PLUGGED_IN = Global.STAY_ON_WHILE_PLUGGED_IN;

        /**
         * What happens when the user presses the end call button if they're not
         * on a call.<br/>
         * <b>Values:</b><br/>
         * 0 - The end button does nothing.<br/>
         * 1 - The end button goes to the home screen.<br/>
         * 2 - The end button puts the device to sleep and locks the keyguard.<br/>
         * 3 - The end button goes to the home screen.  If the user is already on the
         * home screen, it puts the device to sleep.
         */
        public static final String END_BUTTON_BEHAVIOR = "end_button_behavior";

        /**
         * END_BUTTON_BEHAVIOR value for "go home".
         * @hide
         */
        public static final int END_BUTTON_BEHAVIOR_HOME = 0x1;

        /**
         * END_BUTTON_BEHAVIOR value for "go to sleep".
         * @hide
         */
        public static final int END_BUTTON_BEHAVIOR_SLEEP = 0x2;

        /**
         * END_BUTTON_BEHAVIOR default value.
         * @hide
         */
        public static final int END_BUTTON_BEHAVIOR_DEFAULT = END_BUTTON_BEHAVIOR_SLEEP;

        /**
         * Is advanced settings mode turned on. 0 == no, 1 == yes
         * @hide
         */
        public static final String ADVANCED_SETTINGS = "advanced_settings";

        /**
         * ADVANCED_SETTINGS default value.
         * @hide
         */
        public static final int ADVANCED_SETTINGS_DEFAULT = 0;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AIRPLANE_MODE_ON} instead
         */
        @Deprecated
        public static final String AIRPLANE_MODE_ON = Global.AIRPLANE_MODE_ON;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_BLUETOOTH} instead
         */
        @Deprecated
        public static final String RADIO_BLUETOOTH = Global.RADIO_BLUETOOTH;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_WIFI} instead
         */
        @Deprecated
        public static final String RADIO_WIFI = Global.RADIO_WIFI;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_WIMAX} instead
         * {@hide}
         */
        @Deprecated
        public static final String RADIO_WIMAX = Global.RADIO_WIMAX;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_CELL} instead
         */
        @Deprecated
        public static final String RADIO_CELL = Global.RADIO_CELL;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_NFC} instead
         */
        @Deprecated
        public static final String RADIO_NFC = Global.RADIO_NFC;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AIRPLANE_MODE_RADIOS} instead
         */
        @Deprecated
        public static final String AIRPLANE_MODE_RADIOS = Global.AIRPLANE_MODE_RADIOS;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AIRPLANE_MODE_TOGGLEABLE_RADIOS} instead
         *
         * {@hide}
         */
        @Deprecated
        public static final String AIRPLANE_MODE_TOGGLEABLE_RADIOS =
                Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY} instead
         */
        @Deprecated
        public static final String WIFI_SLEEP_POLICY = Global.WIFI_SLEEP_POLICY;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY_DEFAULT} instead
         */
        @Deprecated
        public static final int WIFI_SLEEP_POLICY_DEFAULT = Global.WIFI_SLEEP_POLICY_DEFAULT;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED} instead
         */
        @Deprecated
        public static final int WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED =
                Global.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY_NEVER} instead
         */
        @Deprecated
        public static final int WIFI_SLEEP_POLICY_NEVER = Global.WIFI_SLEEP_POLICY_NEVER;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#MODE_RINGER} instead
         */
        @Deprecated
        public static final String MODE_RINGER = Global.MODE_RINGER;

        /**
         * Whether to use static IP and other static network attributes.
         * <p>
         * Set to 1 for true and 0 for false.
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_USE_STATIC_IP = "wifi_use_static_ip";

        /**
         * The static IP address.
         * <p>
         * Example: "192.168.1.51"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_IP = "wifi_static_ip";

        /**
         * If using static IP, the gateway's IP address.
         * <p>
         * Example: "192.168.1.1"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_GATEWAY = "wifi_static_gateway";

        /**
         * If using static IP, the net mask.
         * <p>
         * Example: "255.255.255.0"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_NETMASK = "wifi_static_netmask";

        /**
         * If using static IP, the primary DNS's IP address.
         * <p>
         * Example: "192.168.1.1"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_DNS1 = "wifi_static_dns1";

        /**
         * If using static IP, the secondary DNS's IP address.
         * <p>
         * Example: "192.168.1.2"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_DNS2 = "wifi_static_dns2";

        /**
         * Allows automatic retrieval of mms contents
         * <p>Type: INT</p>
         * 0 -- false
         * 1 -- true
         * @hide
         */
        public static final String MMS_AUTO_RETRIEVAL = "mms_auto_retrieval";

        /**
         * Allows automatic retrieval of mms contents during roaming
         * <p>Type: INT</p>
         * 0 -- false
         * 1 -- true
         * @hide
         */
        public static final String MMS_AUTO_RETRIEVAL_ON_ROAMING = "mms_auto_on_roaming";

        /**
         * Determines whether remote devices may discover and/or connect to
         * this device.
         * <P>Type: INT</P>
         * 2 -- discoverable and connectable
         * 1 -- connectable but not discoverable
         * 0 -- neither connectable nor discoverable
         */
        public static final String BLUETOOTH_DISCOVERABILITY =
            "bluetooth_discoverability";

        /**
         * Bluetooth discoverability timeout.  If this value is nonzero, then
         * Bluetooth becomes discoverable for a certain number of seconds,
         * after which is becomes simply connectable.  The value is in seconds.
         */
        public static final String BLUETOOTH_DISCOVERABILITY_TIMEOUT =
            "bluetooth_discoverability_timeout";

        /**
         * If all file types can be accepted over Bluetooth OBEX.
         * @hide
         */
        public static final String BLUETOOTH_ACCEPT_ALL_FILES =
            "bluetooth_accept_all_files";

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOCK_PATTERN_ENABLED}
         * instead
         */
        @Deprecated
        public static final String LOCK_PATTERN_ENABLED = Secure.LOCK_PATTERN_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOCK_PATTERN_VISIBLE}
         * instead
         */
        @Deprecated
        public static final String LOCK_PATTERN_VISIBLE = "lock_pattern_visible_pattern";

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED}
         * instead
         */
        @Deprecated
        public static final String LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED =
            "lock_pattern_tactile_feedback_enabled";


        /**
         * A formatted string of the next alarm that is set, or the empty string
         * if there is no alarm set.
         */
        public static final String NEXT_ALARM_FORMATTED = "next_alarm_formatted";

        /**
         * Scaling factor for fonts, float.
         */
        public static final String FONT_SCALE = "font_scale";

        /**
         * Name of an application package to be debugged.
         *
         * @deprecated Use {@link Global#DEBUG_APP} instead
         */
        @Deprecated
        public static final String DEBUG_APP = Global.DEBUG_APP;

        /**
         * If 1, when launching DEBUG_APP it will wait for the debugger before
         * starting user code.  If 0, it will run normally.
         *
         * @deprecated Use {@link Global#WAIT_FOR_DEBUGGER} instead
         */
        @Deprecated
        public static final String WAIT_FOR_DEBUGGER = Global.WAIT_FOR_DEBUGGER;

        /**
         * Whether or not to dim the screen. 0=no  1=yes
         * @deprecated This setting is no longer used.
         */
        @Deprecated
        public static final String DIM_SCREEN = "dim_screen";

        /**
         * The timeout before the screen turns off.
         */
        public static final String SCREEN_OFF_TIMEOUT = "screen_off_timeout";

        /**
         * The screen backlight brightness between 0 and 255.
         */
        public static final String SCREEN_BRIGHTNESS = "screen_brightness";

        /**
         * Control whether to enable automatic brightness mode.
         */
        public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";

        /**
         * Adjustment to auto-brightness to make it generally more (>0.0 <1.0)
         * or less (<0.0 >-1.0) bright.
         * @hide
         */
        public static final String SCREEN_AUTO_BRIGHTNESS_ADJ = "screen_auto_brightness_adj";

        /**
         * SCREEN_BRIGHTNESS_MODE value for manual mode.
         */
        public static final int SCREEN_BRIGHTNESS_MODE_MANUAL = 0;

        /**
         * SCREEN_BRIGHTNESS_MODE value for automatic mode.
         */
        public static final int SCREEN_BRIGHTNESS_MODE_AUTOMATIC = 1;

        /**
         * Custom automatic brightness light sensor levels.
         * The value is a comma separated int array with length N.
         * Example: "100,300,3000".
         *
         * @hide
         */
        public static final String AUTO_BRIGHTNESS_LUX = "auto_brightness_lux";

        /**
         * Custom automatic brightness display backlight brightness values.
         * The value is a comma separated int array with length N+1.
         * Example: "10,50,100,255".
         *
         * @hide
         */
        public static final String AUTO_BRIGHTNESS_BACKLIGHT = "auto_brightness_backlight";

        /**
         * Correction factor for auto-brightness adjustment light sensor
         * debounce times.
         * Smaller factors will make the adjustment more responsive, but might
         * cause flicker and/or cause higher CPU usage.
         * Valid range is 0.2 ... 3
         *
         * @hide
         */
        public static final String AUTO_BRIGHTNESS_RESPONSIVENESS = "auto_brightness_responsiveness";

        /**
         * Whether to enable adjustment of automatic brightness adjustment
         * to sunrise and sunset.
         * @hide
         */
        public static final String AUTO_BRIGHTNESS_TWILIGHT_ADJUSTMENT = "auto_brightness_twilight_adjustment";

        /**
         * The keyboard brightness to be used while the screen is on.
         * Valid value range is between 0 and {@link PowerManager#getMaximumKeyboardBrightness()}
         * @hide
         */
        public static final String KEYBOARD_BRIGHTNESS = "keyboard_brightness";

        /**
         * The button brightness to be used while the screen is on or after a button press,
         * depending on the value of {@link BUTTON_BACKLIGHT_TIMEOUT}.
         * Valid value range is between 0 and {@link PowerManager#getMaximumButtonBrightness()}
         * @hide
         */
        public static final String BUTTON_BRIGHTNESS = "button_brightness";

        /**
         * The time in ms to keep the button backlight on after pressing a button.
         * A value of 0 will keep the buttons on for as long as the screen is on.
         * @hide
         */
        public static final String BUTTON_BACKLIGHT_TIMEOUT = "button_backlight_timeout";

        /**
         * Whether to enable the electron beam animation when turning screen off
         *
         * @hide */
        //public static final String SCREEN_OFF_ANIMATION = "screen_off_animation";

        /**
         * Control whether the process CPU usage meter should be shown.
         *
         * @deprecated Use {@link Global#SHOW_PROCESSES} instead
         */
        @Deprecated
        public static final String SHOW_PROCESSES = Global.SHOW_PROCESSES;

        /**
         * If 1, the activity manager will aggressively finish activities and
         * processes as soon as they are no longer needed.  If 0, the normal
         * extended lifetime is used.
         *
         * @deprecated Use {@link Global#ALWAYS_FINISH_ACTIVITIES} instead
         */
        @Deprecated
        public static final String ALWAYS_FINISH_ACTIVITIES = Global.ALWAYS_FINISH_ACTIVITIES;

        /**
         * Volume Overlay Mode, This is behaviour of the volume overlay panel
         * Defaults to 0 - which is simple
         * @hide
         */
        public static final String MODE_VOLUME_OVERLAY = "mode_volume_overlay";

        /** @hide */
        public static final int VOLUME_OVERLAY_SINGLE = 0;
        /** @hide */
        public static final int VOLUME_OVERLAY_EXPANDABLE = 1;
        /** @hide */
        public static final int VOLUME_OVERLAY_EXPANDED = 2;
        /** @hide */
        public static final int VOLUME_OVERLAY_NONE = 3;

        /**
         * Whether the torch will pulse on incoming call
         * @hide
         */
        public static final String TORCH_WHILE_RINGING = "torch_while_ringing";

        /**
         * Pulse rate of the incoming call torch (if enabled)
         * @hide
         */
        public static final String TORCH_WHILE_RINGING_PERIOD = "torch_while_ringing_period";

        /**
         * Timeout for volume panel
         * @hide
         */
        public static final String VOLUME_PANEL_TIMEOUT = "volume_panel_timeout";

        /**
         * Volume Adjust Sounds Enable, This is the noise made when using volume hard buttons
         * Defaults to 1 - sounds enabled
         * @hide
         */
        public static final String VOLUME_ADJUST_SOUNDS_ENABLED = "volume_adjust_sounds_enabled";

        /**
         * Determines which streams are affected by ringer mode changes. The
         * stream type's bit should be set to 1 if it should be muted when going
         * into an inaudible ringer mode.
         */
        public static final String MODE_RINGER_STREAMS_AFFECTED = "mode_ringer_streams_affected";

         /**
          * Determines which streams are affected by mute. The
          * stream type's bit should be set to 1 if it should be muted when a mute request
          * is received.
          */
         public static final String MUTE_STREAMS_AFFECTED = "mute_streams_affected";

        /**
         * Whether vibrate is on for different events. This is used internally,
         * changing this value will not change the vibrate. See AudioManager.
         */
        public static final String VIBRATE_ON = "vibrate_on";

        /**
         * If 1, redirects the system vibrator to all currently attached input devices
         * that support vibration.  If there are no such input devices, then the system
         * vibrator is used instead.
         * If 0, does not register the system vibrator.
         *
         * This setting is mainly intended to provide a compatibility mechanism for
         * applications that only know about the system vibrator and do not use the
         * input device vibrator API.
         *
         * @hide
         */
        public static final String VIBRATE_INPUT_DEVICES = "vibrate_input_devices";

        /**
         * Ringer volume. This is used internally, changing this value will not
         * change the volume. See AudioManager.
         */
        public static final String VOLUME_RING = "volume_ring";

        /**
         * System/notifications volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_SYSTEM = "volume_system";

        /**
         * Voice call volume. This is used internally, changing this value will
         * not change the volume. See AudioManager.
         */
        public static final String VOLUME_VOICE = "volume_voice";

        /**
         * Music/media/gaming volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_MUSIC = "volume_music";

        /**
         * Alarm volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_ALARM = "volume_alarm";

        /**
         * Notification volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_NOTIFICATION = "volume_notification";

        /**
         * Bluetooth Headset volume. This is used internally, changing this value will
         * not change the volume. See AudioManager.
         */
        public static final String VOLUME_BLUETOOTH_SCO = "volume_bluetooth_sco";

        /**
         * Whether to display a warning dialog when the user attempts to increase media
         * volume above a safe limit while a headset is connected. This feature is enabled
         * by default to comply with safety regulations and the user must agree to a waiver
         * if they wish to disable it.
         * @hide
         */
        public static final String SAFE_HEADSET_VOLUME = "safe_headset_volume";

        /**
         * Whether to reduce media volume to a safe limit each time a headset is plugged in.
         * @hide
         */
        public static final String SAFE_HEADSET_VOLUME_RESTORE = "safe_headset_volume_restore";

        /**
         * Master volume (float in the range 0.0f to 1.0f).
         * @hide
         */
        public static final String VOLUME_MASTER = "volume_master";

        /**
         * Master volume mute (int 1 = mute, 0 = not muted).
         *
         * @hide
         */
        public static final String VOLUME_MASTER_MUTE = "volume_master_mute";

        /**
         * Whether the notifications should use the ring volume (value of 1) or
         * a separate notification volume (value of 0). In most cases, users
         * will have this enabled so the notification and ringer volumes will be
         * the same. However, power users can disable this and use the separate
         * notification volume control.
         * <p>
         * Note: This is a one-off setting that will be removed in the future
         * when there is profile support. For this reason, it is kept hidden
         * from the public APIs.
         *
         * @hide
         * @deprecated
         */
        @Deprecated
        public static final String NOTIFICATIONS_USE_RING_VOLUME =
            "notifications_use_ring_volume";

        /**
         * Whether the blacklisting feature for phone calls is enabled
         * @hide
         */
        public static final String PHONE_BLACKLIST_ENABLED = "phone_blacklist_enabled";

        /**
         * Whether a notification should be shown when a call/message is blocked
         * @hide
         */
        public static final String PHONE_BLACKLIST_NOTIFY_ENABLED = "phone_blacklist_notify_enabled";

        /**
         * Whether the blacklisting feature for phone calls from private numbers is enabled
         * @hide
         */
        public static final String PHONE_BLACKLIST_PRIVATE_NUMBER_MODE = "phone_blacklist_private_number_enabled";

        /**
         * Whether the blacklisting feature for phone calls from unknown numbers is enabled
         * @hide
         */
        public static final String PHONE_BLACKLIST_UNKNOWN_NUMBER_MODE = "phone_blacklist_unknown_number_enabled";

        /**
         * Constants to be used for {@link PHONE_BLACKLIST_PRIVATE_NUMBER_MODE} and
         * {@link PHONE_BLACKLIST_UNKNOWN_NUMBER_MODE}.
         * @hide
         */
        public static final int BLACKLIST_DO_NOT_BLOCK = 0;
        /**
         * @hide
         */
        public static final int BLACKLIST_BLOCK = 1;
        /**
         * @hide
         */
        public static final int BLACKLIST_PHONE_SHIFT = 0;
        /**
         * @hide
         */
        public static final int BLACKLIST_MESSAGE_SHIFT = 4;

        /**
         * Whether the regex blacklisting feature for phone calls is enabled
         * @hide
         */
        public static final String PHONE_BLACKLIST_REGEX_ENABLED = "phone_blacklist_regex_enabled";

        /**
         * Whether the phone ringtone should be played in an increasing manner
         * @hide
         */
        public static final String INCREASING_RING = "increasing_ring";

        /**
         * Minimum volume index for increasing ring volume
         * @hide
         */
        public static final String INCREASING_RING_MIN_VOLUME = "increasing_ring_min_vol";

        /**
         * Time (in ms) between ringtone volume increases
         * @hide
         */
        public static final String INCREASING_RING_INTERVAL = "increasing_ring_interval";

        /**
         * Whether silent mode should allow vibration feedback. This is used
         * internally in AudioService and the Sound settings activity to
         * coordinate decoupling of vibrate and silent modes. This setting
         * will likely be removed in a future release with support for
         * audio/vibe feedback profiles.
         *
         * Not used anymore. On devices with vibrator, the user explicitly selects
         * silent or vibrate mode.
         * Kept for use by legacy database upgrade code in DatabaseHelper.
         * @hide
         */
        public static final String VIBRATE_IN_SILENT = "vibrate_in_silent";

        /**
         * The mapping of stream type (integer) to its setting.
         */
        public static final String[] VOLUME_SETTINGS = {
            VOLUME_VOICE, VOLUME_SYSTEM, VOLUME_RING, VOLUME_MUSIC,
            VOLUME_ALARM, VOLUME_NOTIFICATION, VOLUME_BLUETOOTH_SCO
        };

        /**
         * Appended to various volume related settings to record the previous
         * values before they the settings were affected by a silent/vibrate
         * ringer mode change.
         */
        public static final String APPEND_FOR_LAST_AUDIBLE = "_last_audible";

        /**
         * Persistent store for the system-wide default ringtone URI.
         * <p>
         * If you need to play the default ringtone at any given time, it is recommended
         * you give {@link #DEFAULT_RINGTONE_URI} to the media player.  It will resolve
         * to the set default ringtone at the time of playing.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final String RINGTONE = "ringtone";

        /**
         * A {@link Uri} that will point to the current default ringtone at any
         * given time.
         * <p>
         * If the current default ringtone is in the DRM provider and the caller
         * does not have permission, the exception will be a
         * FileNotFoundException.
         */
        public static final Uri DEFAULT_RINGTONE_URI = getUriFor(RINGTONE);

        /**
         * Persistent store for the system-wide default notification sound.
         *
         * @see #RINGTONE
         * @see #DEFAULT_NOTIFICATION_URI
         */
        public static final String NOTIFICATION_SOUND = "notification_sound";

        /**
         * A {@link Uri} that will point to the current default notification
         * sound at any given time.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final Uri DEFAULT_NOTIFICATION_URI = getUriFor(NOTIFICATION_SOUND);

        /**
         * Persistent store for the system-wide default alarm alert.
         *
         * @see #RINGTONE
         * @see #DEFAULT_ALARM_ALERT_URI
         */
        public static final String ALARM_ALERT = "alarm_alert";

        /**
         * A {@link Uri} that will point to the current default alarm alert at
         * any given time.
         *
         * @see #DEFAULT_ALARM_ALERT_URI
         */
        public static final Uri DEFAULT_ALARM_ALERT_URI = getUriFor(ALARM_ALERT);

        /**
         * Persistent store for the system default media button event receiver.
         *
         * @hide
         */
        public static final String MEDIA_BUTTON_RECEIVER = "media_button_receiver";

        /**
         * Setting to enable Auto Replace (AutoText) in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_AUTO_REPLACE = "auto_replace";

        /**
         * Setting to enable Auto Caps in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_AUTO_CAPS = "auto_caps";

        /**
         * Setting to enable Auto Punctuate in text editors. 1 = On, 0 = Off. This
         * feature converts two spaces to a "." and space.
         */
        public static final String TEXT_AUTO_PUNCTUATE = "auto_punctuate";

        /**
         * Setting to showing password characters in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_SHOW_PASSWORD = "show_password";

        public static final String SHOW_GTALK_SERVICE_STATUS =
                "SHOW_GTALK_SERVICE_STATUS";

        /**
         * Name of activity to use for wallpaper on the home screen.
         *
         * @deprecated Use {@link WallpaperManager} instead.
         */
        @Deprecated
        public static final String WALLPAPER_ACTIVITY = "wallpaper_activity";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AUTO_TIME}
         * instead
         */
        @Deprecated
        public static final String AUTO_TIME = Global.AUTO_TIME;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AUTO_TIME_ZONE}
         * instead
         */
        @Deprecated
        public static final String AUTO_TIME_ZONE = Global.AUTO_TIME_ZONE;

        /**
         * Display times as 12 or 24 hours
         *   12
         *   24
         */
        public static final String TIME_12_24 = "time_12_24";

        /**
         * Date format string
         *   mm/dd/yyyy
         *   dd/mm/yyyy
         *   yyyy/mm/dd
         */
        public static final String DATE_FORMAT = "date_format";

        /**
         * Whether the setup wizard has been run before (on first boot), or if
         * it still needs to be run.
         *
         * nonzero = it has been run in the past
         * 0 = it has not been run in the past
         */
        public static final String SETUP_WIZARD_HAS_RUN = "setup_wizard_has_run";

        /**
         * Scaling factor for normal window animations. Setting to 0 will disable window
         * animations.
         *
         * @deprecated Use {@link Global#WINDOW_ANIMATION_SCALE} instead
         */
        @Deprecated
        public static final String WINDOW_ANIMATION_SCALE = Global.WINDOW_ANIMATION_SCALE;

        /**
         * Scaling factor for activity transition animations. Setting to 0 will disable window
         * animations.
         *
         * @deprecated Use {@link Global#TRANSITION_ANIMATION_SCALE} instead
         */
        @Deprecated
        public static final String TRANSITION_ANIMATION_SCALE = Global.TRANSITION_ANIMATION_SCALE;

        /**
         * Scaling factor for Animator-based animations. This affects both the start delay and
         * duration of all such animations. Setting to 0 will cause animations to end immediately.
         * The default value is 1.
         *
         * @deprecated Use {@link Global#ANIMATOR_DURATION_SCALE} instead
         */
        @Deprecated
        public static final String ANIMATOR_DURATION_SCALE = Global.ANIMATOR_DURATION_SCALE;

        /**
         * Control whether the accelerometer will be used to change screen
         * orientation.  If 0, it will not be used unless explicitly requested
         * by the application; if 1, it will be used by default unless explicitly
         * disabled by the application.
         */
        public static final String ACCELEROMETER_ROTATION = "accelerometer_rotation";

        /**
         * Control whether the accelerometer will be used to change lockscreen
         * orientation.  If 0, it will not be used; if 1, it will be used by default.
         * @hide
         */
        public static final String LOCKSCREEN_ROTATION = "lockscreen_rotation";
        
        /**
         * Whether music controls should be shown on the lockscreen if a supporting
         * music player is active.
         * @hide
         */
        public static final String LOCKSCREEN_MUSIC_CONTROLS = "lockscreen_music_controls";

        /**
         * Control the type of rotation which can be performed using the accelerometer
         * if ACCELEROMETER_ROTATION is enabled.
         * Value is a bitwise combination of
         * 1 = 0 degrees (portrait)
         * 2 = 90 degrees (left)
         * 4 = 180 degrees (inverted portrait)
         * 8 = 270 degrees (right)
         * Setting to 0 is effectively orientation lock
         * @hide
         */
        public static final String ACCELEROMETER_ROTATION_ANGLES = "accelerometer_rotation_angles";

        /**
         * Default screen rotation when no other policy applies.
         * When {@link #ACCELEROMETER_ROTATION} is zero and no on-screen Activity expresses a
         * preference, this rotation value will be used. Must be one of the
         * {@link android.view.Surface#ROTATION_0 Surface rotation constants}.
         *
         * @see Display#getRotation
         */
        public static final String USER_ROTATION = "user_rotation";

        /**
         * Control whether the rotation lock toggle in the System UI should be hidden.
         * Typically this is done for accessibility purposes to make it harder for
         * the user to accidentally toggle the rotation lock while the display rotation
         * has been locked for accessibility.
         *
         * If 0, then rotation lock toggle is not hidden for accessibility (although it may be
         * unavailable for other reasons).  If 1, then the rotation lock toggle is hidden.
         *
         * @hide
         */
        public static final String HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY =
                "hide_rotation_lock_toggle_for_accessibility";

        /**
         * Call recording format value
         * 0: AMR_WB
         * 1: MPEG_4
         * Default: 0
         * @hide
         */
        public static final String CALL_RECORDING_FORMAT = "call_recording_format";

        /**
         * Whether the phone vibrates when it is ringing due to an incoming call. This will
         * be used by Phone and Setting apps; it shouldn't affect other apps.
         * The value is boolean (1 or 0).
         *
         * Note: this is not same as "vibrate on ring", which had been available until ICS.
         * It was about AudioManager's setting and thus affected all the applications which
         * relied on the setting, while this is purely about the vibration setting for incoming
         * calls.
         *
         * @hide
         */
        public static final String VIBRATE_WHEN_RINGING = "vibrate_when_ringing";

        /**
         * Whether the audible DTMF tones are played by the dialer when dialing. The value is
         * boolean (1 or 0).
         */
        public static final String DTMF_TONE_WHEN_DIALING = "dtmf_tone";

        /**
         * CDMA only settings
         * DTMF tone type played by the dialer when dialing.
         *                 0 = Normal
         *                 1 = Long
         * @hide
         */
        public static final String DTMF_TONE_TYPE_WHEN_DIALING = "dtmf_tone_type";

        /**
         * Whether incall glowpad background is transparent or not.  The value is
         * boolean (1 or 0).
        */
        public static final String INCALL_GLOWPAD_TRANSPARENCY = "incall_glowpad_transparency";

        /**
         * Whether the hearing aid is enabled. The value is
         * boolean (1 or 0).
         * @hide
         */
        public static final String HEARING_AID = "hearing_aid";

        /**
         * CDMA only settings
         * TTY Mode
         * 0 = OFF
         * 1 = FULL
         * 2 = VCO
         * 3 = HCO
         * @hide
         */
        public static final String TTY_MODE = "tty_mode";

        /**
         * Whether noise suppression is enabled. The value is
         * boolean (1 or 0).
         * @hide
         */
        public static final String NOISE_SUPPRESSION = "noise_suppression";

        /**
         * Whether the sounds effects (key clicks, lid open ...) are enabled. The value is
         * boolean (1 or 0).
         */
        public static final String SOUND_EFFECTS_ENABLED = "sound_effects_enabled";

        /**
         * Whether the haptic feedback (long presses, ...) are enabled. The value is
         * boolean (1 or 0).
         */
        public static final String HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled";

        /**
         * Minimum vibration duration in milliseconds (0-100ms)
         * @hide
         */
        public static final String MINIMUM_VIBRATION_DURATION = "minimum_vibration_duration";

        /**
         * @deprecated Each application that shows web suggestions should have its own
         * setting for this.
         */
        @Deprecated
        public static final String SHOW_WEB_SUGGESTIONS = "show_web_suggestions";

        /**
         * Whether the notification LED should repeatedly flash when a notification is
         * pending. The value is boolean (1 or 0).
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE = "notification_light_pulse";

        /**
         * What color to use for the notification LED by default
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR = "notification_light_pulse_default_color";

        /**
         * How long to flash the notification LED by default
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON = "notification_light_pulse_default_led_on";

        /**
         * How long to wait between flashes for the notification LED by default
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF = "notification_light_pulse_default_led_off";

        /**
         * What color to use for the missed call notification LED
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_COLOR = "notification_light_pulse_call_color";

        /**
         * How long to flash the missed call notification LED
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_LED_ON = "notification_light_pulse_call_led_on";

        /**
         * How long to wait between flashes for the missed call notification LED
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF = "notification_light_pulse_call_led_off";

        /**
         * What color to use for the voicemail notification LED
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR = "notification_light_pulse_vmail_color";

        /**
         * How long to flash the voicemail notification LED
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON = "notification_light_pulse_vmail_led_on";

        /**
         * How long to wait between flashes for the voicemail notification LED
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF = "notification_light_pulse_vmail_led_off";

        /**
         * Whether to use the custom LED values for the notification pulse LED.
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE = "notification_light_pulse_custom_enable";

        /**
         * Which custom LED values to use for the notification pulse LED.
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES = "notification_light_pulse_custom_values";

        /**
         * Whether the battery light should be enabled (if hardware supports it)
         * The value is boolean (1 or 0).
         * @hide
         */
        public static final String BATTERY_LIGHT_ENABLED = "battery_light_enabled";

        /**
         * Whether the battery LED should repeatedly flash when the battery is low
         * on charge. The value is boolean (1 or 0).
         * @hide
         */
        public static final String BATTERY_LIGHT_PULSE = "battery_light_pulse";

        /**
         * What color to use for the battery LED while charging - low
         * @hide
         */
        public static final String BATTERY_LIGHT_LOW_COLOR = "battery_light_low_color";

        /**
         * What color to use for the battery LED while charging - medium
         * @hide
         */
        public static final String BATTERY_LIGHT_MEDIUM_COLOR = "battery_light_medium_color";

        /**
         * What color to use for the battery LED while charging - full
         * @hide
         */
        public static final String BATTERY_LIGHT_FULL_COLOR = "battery_light_full_color";

        /** Sprint MWI Quirk: Show message wait indicator notifications
         * @hide
         */
        public static final String ENABLE_MWI_NOTIFICATION = "enable_mwi_notification";

    	/**
         * Battery warning preferences
         *
         * 0 = show dialog + play sound (default)
         * 1 = fire notification + play sound
         * 2 = show dialog only
         * 3 = fire notification only
         * 4 = play sound only
         * 5 = none
         * @hide
         */
        public static final String POWER_UI_LOW_BATTERY_WARNING_POLICY = "power_ui_low_battery_warning_policy";

        /**
         * Screen-On Notification Light,
         * should default to 1 (yes, Notification Light is enabled when screen is on)
         * @hide
         */
        public static final String SCREEN_ON_NOTIFICATION_LED = "screen_on_notification_led"; 

        /**
         * Show pointer location on screen?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String POINTER_LOCATION = "pointer_location";

        /**
         * Show icon when stylus is used?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String STYLUS_ICON_ENABLED = "stylus_icon_enabled";

        /**
         * Reverse default app picker behaviour
         * @hide
         */
        public static final String REVERSE_DEFAULT_APP_PICKER = "reverse_default_app_picker";

        /**
         * Show touch positions on screen?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String SHOW_TOUCHES = "show_touches";

        /**
         * Log raw orientation data from {@link WindowOrientationListener} for use with the
         * orientationplot.py tool.
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String WINDOW_ORIENTATION_LISTENER_LOG =
                "window_orientation_listener_log";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#POWER_SOUNDS_ENABLED}
         * instead
         * @hide
         */
        @Deprecated
        public static final String POWER_SOUNDS_ENABLED = Global.POWER_SOUNDS_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DOCK_SOUNDS_ENABLED}
         * instead
         * @hide
         */
        @Deprecated
        public static final String DOCK_SOUNDS_ENABLED = Global.DOCK_SOUNDS_ENABLED;

        /**
         * Whether to play sounds when the keyguard is shown and dismissed.
         * @hide
         */
        public static final String LOCKSCREEN_SOUNDS_ENABLED = "lockscreen_sounds_enabled";

        /**
         * Whether the lockscreen should be completely disabled.
         * @hide
         */
        public static final String LOCKSCREEN_DISABLED = "lockscreen.disabled";

        /**
         * Whether to display notification messages around ring
         * @hide
         */
        public static final String ACTIVE_DISPLAY_TEXT = "active_display_text";

        /**
         * Time to redisplay notifications on screen from when screen turns off, 0 = never redisplay
         * @hide
         */
        public static final String ACTIVE_DISPLAY_REDISPLAY = "active_display_redisplay";

        /**
         * Brightness of the display when displaying the active display view
         * @hide
         */
        public static final String ACTIVE_DISPLAY_BRIGHTNESS = "active_display_brightness";

        /**
         * Whether to include ongoing/non-clearable notifications
         * @hide
         */
        public static final String ACTIVE_DISPLAY_ALL_NOTIFICATIONS = "active_display_all_notifications";

        /**
         * Whether to invert the colors when in bright light
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SUNLIGHT_MODE = "active_display_sunlight_mode";

        /**
         * Whether to turn off the device when gets pocketed again and was waked up by active display
         * @hide
         */
        public static final String ACTIVE_DISPLAY_TURNOFF_MODE = "active_display_turnoff_mode";

        /**
         * Whether to display the date above the time
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHOW_DATE = "active_display_show_date";

        /**
         * Whether to display AM/PM after time when in 12h format
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHOW_AMPM = "active_display_show_ampm";

        /**
         * active notifications
         *@hide
         */
        public static final String ACTIVE_NOTIFICATIONS = "active_notifications";

        /**
         * active notifications mode
         * Listpreference handle for Active display, Lockscreen Notification and Peek
         *@hide
         */
        public static final String ACTIVE_NOTIFICATIONS_MODE = "active_notifications_mode";

        /**
         * Threshold of the proximity sensor to turn on the device.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_THRESHOLD = "active_display_threshold";

        /**
         * use Active display content view instead default one.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_CONTENT = "active_display_content";

        /**
         * Timeout of the display when there is no user interaction
         * @hide
         */
        public static final String ACTIVE_DISPLAY_TIMEOUT = "active_display_timeout";

        /**
         * A list of packages to exclude from being displayed in active display.
         * This should be a string of packages separated by |
         * @hide
         */
        public static final String ACTIVE_DISPLAY_EXCLUDED_APPS = "active_display_excluded_apps";

        /**
         * A list of packages to exclude from being message displayed in active display.
         * This should be a string of packages separated by |
         * @hide
         */
        public static final String ACTIVE_DISPLAY_PRIVACY_APPS = "active_display_privacy_apps";

        /**
         * allow bypass active display when lockscreen isSecure
         * and there is no notifications
         * @hide
         */
        public static final String ACTIVE_DISPLAY_BYPASS = "active_display_bypass";

        /**
         * Whether to not showing active display when there is annoying notifications.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_ANNOYING = "active_display_annoying";

        /**
         * double tap every where to sleep on active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_DOUBLE_TAP = "active_display_double_tap";

        /**
         * shake device to show/hide active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHAKE_EVENT = "active_display_shake_event";

        /**
         * force shake device to show active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHAKE_FORCE = "active_display_shake_force";

        /**
         * shake device to show/hide active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHAKE_QUITE_HOURS = "active_display_shake_quiet_hours";

        /**
         * shake threshold active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHAKE_THRESHOLD = "active_display_shake_threshold";

        /**
         * shake timeout active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHAKE_TIMEOUT = "active_display_shake_timeout";

        /**
         * shake between interval active display.
         * @hide
         */
        public static final String ACTIVE_DISPLAY_SHAKE_LONGTHRESHOLD = "active_display_shake_long_threshold";

        /**
         * Stores values for custom lockscreen targets
         * @hide
         */
        public static final String LOCKSCREEN_TARGETS = "lockscreen_targets";

        /**
         * Whether phone lockscreen uses 5 or 8 targets
         * @hide
         */
        public static final String LOCKSCREEN_EIGHT_TARGETS = "lockscreen_eight_targets";

        /**
         * Defines the shortcuts to be shown on lockscreen
         * Usage is like this: target:icon|target:icon|target:icon
         * if :icon is not set, default application icon will be used
         * @hide
         */
        public static final String LOCKSCREEN_SHORTCUTS = "lockscreen_shortcuts";

        /**
         * Whether shorcuts open with normal or longpress
         * @hide
         */
        public static final String LOCKSCREEN_SHORTCUTS_LONGPRESS =
                "lockscreen_shortcuts_longpress";

        /**
         * Whether to show the camera widget on lockscreen
         * @hide
         */
        public static final String LOCKSCREEN_CAMERA_WIDGET = "lockscreen_camera_widget";

        /**
         * Whether to hide the lockscreen gadgets glowing hints
         * @hide
         */
        public static final String LOCKSCREEN_DISABLE_HINTS = "lockscreen_disable_hints";

        /**
         * Whether to minimize lockscreen challenge on screen turned on
         * @hide
         */
        public static final String LOCKSCREEN_MAXIMIZE_WIDGETS = "lockscreen_maximize_widgets";

        /**
         * Whether to use the carousel as widget container on portrait view
         * @hide
         */
        public static final String LOCKSCREEN_USE_WIDGET_CONTAINER_CAROUSEL =
                "lockscreen_use_widget_container_carousel";

        /**
         * Whether to hide the frame behind lockscreen widgets
         * @hide
         */
        public static final String LOCKSCREEN_WIDGET_FRAME_ENABLED =
                "lockscreen_widget_frame_enabled";

        /**
         * Whether double-tap and hold on the lock glowpad starts the torch
         * @hide
         */
        public static final String LOCKSCREEN_GLOWPAD_TORCH = "lockscreen_glowpad_torch";

        /**
         * Whether to display the gesture anywhere trigger region or not.
         * @hide
         */
        @ChaosLab(name="Identicons", classification=Classification.NEW_FIELD)
        public static final String IDENTICONS_ENABLED = "identicons_enabled";

        /**
         * Identicons style setting.
         * @hide
         */
        @ChaosLab(name="Identicons", classification=Classification.NEW_FIELD)
        public static final String IDENTICONS_STYLE = "identicons_style";

        /**
         * Whether to enable the modlock keyguard
         * @hide
         */
        public static final String LOCKSCREEN_MODLOCK_ENABLED = "lockscreen_modlock_enabled";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#LOW_BATTERY_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String LOW_BATTERY_SOUND = Global.LOW_BATTERY_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DESK_DOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String DESK_DOCK_SOUND = Global.DESK_DOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DESK_UNDOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String DESK_UNDOCK_SOUND = Global.DESK_UNDOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#CAR_DOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String CAR_DOCK_SOUND = Global.CAR_DOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#CAR_UNDOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String CAR_UNDOCK_SOUND = Global.CAR_UNDOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#LOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String LOCK_SOUND = Global.LOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#UNLOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String UNLOCK_SOUND = Global.UNLOCK_SOUND;

        /**
         * Receive incoming SIP calls?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String SIP_RECEIVE_CALLS = "sip_receive_calls";

        /**
         * Call Preference String.
         * "SIP_ALWAYS" : Always use SIP with network access
         * "SIP_ADDRESS_ONLY" : Only if destination is a SIP address
         * "SIP_ASK_ME_EACH_TIME" : Always ask me each time
         * @hide
         */
        public static final String SIP_CALL_OPTIONS = "sip_call_options";

        /**
         * One of the sip call options: Always use SIP with network access.
         * @hide
         */
        public static final String SIP_ALWAYS = "SIP_ALWAYS";

        /**
         * One of the sip call options: Only if destination is a SIP address.
         * @hide
         */
        public static final String SIP_ADDRESS_ONLY = "SIP_ADDRESS_ONLY";

        /**
         * One of the sip call options: Always ask me each time.
         * @hide
         */
        public static final String SIP_ASK_ME_EACH_TIME = "SIP_ASK_ME_EACH_TIME";

        /**
         * Pointer speed setting.
         * This is an integer value in a range between -7 and +7, so there are 15 possible values.
         *   -7 = slowest
         *    0 = default speed
         *   +7 = fastest
         * @hide
         */
        public static final String POINTER_SPEED = "pointer_speed";

        /**
         * Use the Notification Power Widget? (Who wouldn't!)
         *
         * @hide
         */
        public static final String EXPANDED_VIEW_WIDGET = "expanded_view_widget";

        /**
         * I am the lolrus.
         * <p>
         * Nonzero values indicate that the user has a bukkit.
         * Backward-compatible with <code>PrefGetPreference(prefAllowEasterEggs)</code>.
         * @hide
         */
        public static final String EGG_MODE = "egg_mode";

        /**
         * Enable Notification Toggles Icon Color
         *
         * @hide
         */
        public static final String ENABLE_TOGGLE_COLORS = "enable_toggle_colors";

         /**
         * Enable Notification Toggles Bar
         *
         * @hide
         */
        public static final String ENABLE_TOGGLE_BAR = "enable_toggle_bar";

        /**
         * Notification Toggles Icon Color (On)
         *
         * @hide
         */
        public static final String TOGGLE_ICON_ON_COLOR = "toggle_icon_color_on";

        /**
         * Notification Toggles Icon Color (Off)
         *
         * @hide
         */
        public static final String TOGGLE_ICON_OFF_COLOR = "toggle_icon_color_off";

       /**
         * Animate-flip Quick Settings Panel Tiles on click
         *
         * @hide
         */
        public static final String QUICK_SETTINGS_TILES_FLIP = "quick_settings_tiles_flip";

        /**
         * Let Quick Settings Panel Tiles vibrate on click
         *
         * @hide
         */
        public static final String QUICK_SETTINGS_TILES_VIBRATE = "quick_settings_tiles_vibrate";

        /**
         * Quick Settings Panel Tiles to Use
         *
         * @hide
         */
        public static final String QUICK_SETTINGS_TILES = "quick_settings_tiles";

        /**
         * The OpenCNAM paid account ID
         *
         * @hide
         */
        public static final String DIALER_OPENCNAM_ACCOUNT_SID = "dialer_opencnam_account_sid";

        /**
         * The OpenCNAM authentication token
         *
         * @hide
         */
        public static final String DIALER_OPENCNAM_AUTH_TOKEN = "dialer_opencnam_auth_token";

        /**
         * Heads Up Notifications
         *
         * @hide
         */
        public static final String HEADS_UP_NOTIFICATION = "heads_up_enabled";

        /**
         * Heads Up in Floating Window
         *
         * @hide
         */
        public static final String HEADS_UP_FLOATING_WINDOW = "heads_up_floating_window";

        /**
         * Which applications to disable heads up notifications in
         *
         * @hide
         */
        public static final String HEADS_UP_CUSTOM_VALUES = "heads_up_custom_values";

        /**
         * Which applications to disable heads up notifications for
         *
         * @hide
         */
        public static final String HEADS_UP_BLACKLIST_VALUES = "heads_up_blacklist_values";

        /**
         * Whether heads up notification is shown on the bottom of the screen (default = disabled)
         *
         * @hide
         */
        public static final String HEADS_UP_GRAVITY_BOTTOM = "heads_up_gravity_bottom";

        /**
         * Whether heads up notification is expanded by default (default = disabled)
         *
         * @hide
         */
        public static final String HEADS_UP_EXPANDED = "heads_up_expanded";

        /**
         * Time where heads up is disabled by user interaction (default = 5 minutes)
         *
         * @hide
         */
        public static final String HEADS_UP_SNOOZE_TIME = "heads_up_snooze_time";

        /**
         * Time how long heads up will show till it is automatically hidden.
         * If time = 0 notifications stays till the user interacts with it.
         *
         * @hide
         */
        public static final String HEADS_UP_NOTIFCATION_DECAY = "heads_up_notifcation_decay";

        /**
         * Whether notification updates from background notifications should be shown as heads up.
         *
         * @hide
         */
        public static final String HEADS_UP_SHOW_UPDATE = "heads_up_show_update";

        /**
         * Whether incomming call UI stays in background and shows as heads up notification
         *
         * @hide
         */
        public static final String CALL_UI_AS_HEADS_UP = "call_ui_as_heads_up";

        /**
         * Choose between CallUI HEADSUP method
         *
         * @hide
         */
        public static final String CALL_UI_AS_HEADS_UP_MODE = "call_ui_as_heads_up_mode";

        /**
         * Quick Settings Panel Dynamic Tiles
         *
         * @hide
         */
        public static final String QS_DYNAMIC_ALARM = "qs_dyanmic_alarm";

        /**
         * Quick Settings Panel Dynamic Tiles
         *
         * @hide
         */
        public static final String QS_DYNAMIC_BUGREPORT = "qs_dyanmic_bugreport";

        /**
         * Quick Settings Panel Dynamic Tiles
         *
         * @hide
         */
        public static final String QS_DYNAMIC_IME = "qs_dyanmic_ime";

        /**
         * Quick Settings Panel Dynamic Tiles
         *
         * @hide
         */
        public static final String QS_DYNAMIC_EQUALIZER = "qs_dynamic_equalizer";

        /**
         * Quick Settings Panel Dynamic Tiles
         *
         * @hide
         */
        public static final String QS_DYNAMIC_USBTETHER = "qs_dyanmic_usbtether";

        /**
         * Quick Settings Panel Dynamic Tiles
         *
         * @hide
         */
        public static final String QS_DYNAMIC_WIFI = "qs_dyanmic_wifi";

        /**
         * Quick Settings Quick Pulldown
         *
         * @hide
         */
        public static final String QS_QUICK_PULLDOWN = "qs_quick_pulldown";

        /**
         * Quick Settings Collapse Pane
         *
         * @hide
         */
        public static final String QS_COLLAPSE_PANEL = "qs_collapse_panel";

        /**
         * Quick Settings Smart Pulldown
         *
         * @hide
         */
        public static final String QS_SMART_PULLDOWN = "qs_smart_pulldown";

        /**
         * Quick Settings Quick access ribbon
         *
         * @hide
         */
        public static final String QS_QUICK_ACCESS = "qs_quick_access";

        /**
         * Quick Settings Quick access ribbon - linked layout
         *
         * @hide
         */
        public static final String QS_QUICK_ACCESS_LINKED = "qs_quick_access_linked";

        /**
         * Quick Settings Ribbon Tiles to Use
         *
         * @hide
         */
        public static final String QUICK_SETTINGS_RIBBON_TILES = "quick_settings_ribbon_tiles";

        /**
         * Quick Settings Quick access ribbon - enable labels
         *
         * @hide
         */
        public static final String QS_QUICK_ACCESS_LABEL = "qs_quick_access_label";

        /**
         * Whether to hide the notification screen after clicking on a widget
         * button
         *
         * @hide
         */
        public static final String EXPANDED_HIDE_ONCHANGE = "expanded_hide_onchange";

        /**
         * Hide scroll bar in power widget
         *
         * @hide
         */
        public static final String EXPANDED_HIDE_SCROLLBAR = "expanded_hide_scrollbar";

        /**
         * Haptic feedback in power widget
         *
         * @hide
         */
        public static final String EXPANDED_HAPTIC_FEEDBACK = "expanded_haptic_feedback";

        /**
         * Widget Buttons to Use
         *
         * @hide
         */
        public static final String WIDGET_BUTTONS = "expanded_widget_buttons";

        /**
         * Widget Buttons to Use - Tablet
         *
         * @hide
         */
        public static final String WIDGET_BUTTONS_TABLET = "expanded_widget_buttons_tablet";

        /**
         * multiuser pref
         * @hide
         */
        public static final String ALLOW_MULTIUSER = "allow_multiuser";

        /**
         * Navigation controls to Use
         *
         * @hide
         */
        public static final String NAV_BUTTONS = "nav_buttons";

        /**
        * Notification Power Widget - Custom Brightness Mode
        * @hide
        */
        public static final String EXPANDED_BRIGHTNESS_MODE = "expanded_brightness_mode";

        /**
         * Show the pending notification counts as overlays on the status bar
         * @hide
         */
        public static final String STATUS_BAR_NOTIF_COUNT = "status_bar_notif_count";

        /**
         * Color of the status bar notification text
         * @hide
         */
        public static final String STATUS_BAR_NOTIF_TEXT_COLOR = "status_bar_notif_text_color";

        /**
         * Color of the status bar notification count icon
         * @hide
         */
        public static final String STATUS_BAR_NOTIF_COUNT_ICON_COLOR = "status_bar_notif_count_icon_color";

        /**
         * Wether to colorize the notification icons on the status bar
         * @hide
         */
        public static final String STATUS_BAR_COLORIZE_NOTIF_ICONS = "status_bar_colorize_notif_icons";

        /*
         * Color of the status bar notification icons
         * @hide
         */
        public static final String STATUS_BAR_NOTIF_SYSTEM_ICON_COLOR = "status_bar_notif_system_icon_color";

        /**
         * Color of the status bar notif count text
         * @hide
         */
        public static final String STATUS_BAR_NOTIF_COUNT_TEXT_COLOR = "status_bar_notif_count_text_color";

       /**
         * HALO enabled, should default to 0 (HALO is disabled)
         * @hide
         */
        public static final String HALO_ENABLED = "halo_enabled";

        /**
        * Notification Power Widget - Custom Torch Mode
        * @hide
        */
        public static final String EXPANDED_FLASH_MODE = "expanded_flash_mode";

        /**
        * AutoHide CombinedBar on tablets.
        * @hide
        */
        public static final String COMBINED_BAR_AUTO_HIDE = "combined_bar_auto_hide";

        /**
         * Display style of AM/PM next to clock in status bar
         * 0: Normal display (Eclair stock)
         * 1: Small display (Froyo stock)
         * 2: No display (Gingerbread/ICS stock)
         * default: 2
         * @hide
         */
        public static final String STATUS_BAR_AM_PM = "status_bar_am_pm";

        /**
         * Whether to show the signal text or signal bars.
         * default: 0
         * 0: show signal bars
         * 1: show signal text numbers
         * 2: show signal text numbers w/small dBm appended
         * @hide
         */
        public static final String STATUS_BAR_SIGNAL_TEXT = "status_bar_signal";

        /**
         * Hide Singal Bars
         *
         * @hide
         */
        public static final String STATUSBAR_HIDE_SIGNAL_BARS = "statusbar_hide_signal_bars";

        /**
         * Show when WiFi or data mobile is sending/receiving data
         * @hide
         */
        public static final String STATUS_BAR_NETWORK_ACTIVITY = "status_bar_network_activity";

        /**
         * Network traffic indicator, goes from least to greatest significant bitwise
         * 0 = Display up-stream traffic if set
         * 1 = Display down-stream traffic if set
         * 2 = Show as Byte/s if set
         * 16-31 = Refresh interval(ms) min: 250 max: 32750 default: 1000
         * @hide
         */
        public static final String NETWORK_TRAFFIC_STATE = "network_traffic_state";

        /**
         * Whether or not to hide the network traffic indicator when there is no activity
         * @hide
         */
        public static final String NETWORK_TRAFFIC_AUTOHIDE = "network_traffic_autohide";

        /**
         * Network traffic inactivity threshold (default is 10 kBs)
         * @hide
         */
        public static final String NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD = "network_traffic_autohide_threshold";

        /**
         * Network stats Color style
         * @hide
         */
        public static final String NETWORK_TRAFFIC_COLOR = "network_traffic_color";

        /**
         * Network stats Color style
         * @hide
         */
        public static final String NETWORK_TRAFFIC_ICON_COLOR = "network_traffic_icon_color";

        /**
         * Whether to show the IME switcher in the status bar
         * @hide
         */
        public static final String STATUS_BAR_IME_SWITCHER = "status_bar_ime_switcher";

        /**
         * Whether to use the custom quick unlock screen control
         * @hide
         */
        public static final String LOCKSCREEN_QUICK_UNLOCK_CONTROL = "lockscreen_quick_unlock_control";

        /**
         * Whether to show statusbar signal text
         *
         * @hide
         */
        public static final String STATUSBAR_SIGNAL_TEXT = "statusbar_signal_text";

        /**
         * statusbar signal text color
         *
         * @hide
         */
        public static final String STATUSBAR_SIGNAL_TEXT_COLOR = "statusbar_signal_text_color";

        /**
         * Boolean value whether to link ringtone and notification volumes
         *
         * @hide
         */
        public static final String VOLUME_LINK_NOTIFICATION = "volume_link_notification";

        /**
         * Whether to unlock the menu key.  The value is boolean (1 or 0).
         * @hide
         */
        public static final String MENU_UNLOCK_SCREEN = "menu_unlock_screen";

        /**
         * Whether to wake the screen with the home key, the value is boolean.
         * @hide
         */
        public static final String HOME_WAKE_SCREEN = "home_wake_screen";

        /**
         * Whether to wake the screen with the volume keys, the value is boolean.
         * @hide
         */
        public static final String VOLUME_WAKE_SCREEN = "volume_wake_screen";

        /**
         * Whether or not volume button music controls should be enabled to seek media tracks
         * @hide
         */
        public static final String VOLBTN_MUSIC_CONTROLS = "volbtn_music_controls";

        /**
         * Whether or not to launch default music player when headset is connected
         * @hide
         */
        public static final String HEADSET_CONNECT_PLAYER = "headset_connect_player";

        /**
         * Whether national data roaming should be used.
         * @hide
         */
        public static final String MVNO_ROAMING = "mvno_roaming";

		/*
         * Preference for the button backlight. The value is enum.
		 * 0 for on touch
		 * 1 for off
		 * 2 for on
		 * 3 for force off
		 * 4 for force on.
		 */
        public static final String BUTTON_BACKLIGHT_MODE = "button_backlight_mode";

        /**
         * Whether to enable quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Timout ignored and active
         * 3 = Timeout enabled and active
         * 4 = Timeout enabled and waiting on charging/wifi connected
         * @hide
         */
        public static final String QUIET_HOURS_ENABLED = "quiet_hours_enabled";
        public static final String QUIET_HOURS_STATE = "quiet_hours_state";
 
        /**
         * Whether quiet hours will enable or disable themselves on volume change
         * 0 = Setting disabled
         * 1 = When device is silenced
         * 2 = When device is set to vibrate or silent
         * @hide
         */
        public static final String QUIET_HOURS_AUTOMATIC = "quiet_hours_automatic";

        /**
         * Whether to enable quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and power connected
         * @hide
         */
        public static final String QUIET_HOURS_REQUIRE_CHARGING = "quiet_hours_require_charging";

        /**
         * Whether to enable quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and wifi connected
         * @hide
         */
        public static final String QUIET_HOURS_REQUIRE_WIFI = "quiet_hours_require_wifi";

        /**
         * If we do or do not use daily time-range preferences
         * @hide
         */
        public static final String QUIET_HOURS_DAILY = "quiet_hours_daily";

        /**
         * Holding cartridge for start times if single day preference is enabled
         * @hide
         */
        public static final String QUIET_HOURS_START = "quiet_hours_start";

        /**
         * Holding cartridge for end times if single day preference is enabled
         * @hide
         */
        public static final String QUIET_HOURS_END = "quiet_hours_end";

        /**
         * Sets when quiet hours starts for each day.  Parsed as a split string
         * by a single controller to update all settings values simultaneiously.
         * This is stored in minutes from the start of the day.
         * 0 - 6 are parsed and compared to Sunday (1) through Saturday (7)
         * 7 - 13 are parsed and compared to Sunday through Saturday
         * for additional day times (work/weekend-scheduling/etc).
         * @hide
         */
        public static final String[] QUIET_HOURS_START_TIMES = new String[] {
            "quiet_hours_start_times_sun",
            "quiet_hours_start_times_mon",
            "quiet_hours_start_times_tues",
            "quiet_hours_start_times_wed",
            "quiet_hours_start_times_thurs",
            "quiet_hours_start_times_fri",
            "quiet_hours_start_times_sat",
            "quiet_hours_start_times_sun_extra",
            "quiet_hours_start_times_mon_extra",
            "quiet_hours_start_times_tues_extra",
            "quiet_hours_start_times_wed_extra",
            "quiet_hours_start_times_thurs_extra",
            "quiet_hours_start_times_fri_extra",
            "quiet_hours_start_times_sat_extra"
        };

        /**
         * Sets when quiet hours end for each day.  Parsed as a split string
         * by a single controller to update all settings values simultaneiously.
         * This is stored in minutes from the start of the day.
         * 0 - 6 are parsed and compared to Sunday (1) through Saturday (7)
         * 7 - 13 are parsed and compared to Sunday through Saturday
         * for additional day times (work/weekend-scheduling/etc).
         * @hide
         */
        public static final String[] QUIET_HOURS_END_TIMES = new String[] {
            "quiet_hours_end_times_sun",
            "quiet_hours_end_times_mon",
            "quiet_hours_end_times_tues",
            "quiet_hours_end_times_wed",
            "quiet_hours_end_times_thurs",
            "quiet_hours_end_times_fri",
            "quiet_hours_end_times_sat",
            "quiet_hours_end_times_sun_extra",
            "quiet_hours_end_times_mon_extra",
            "quiet_hours_end_times_tues_extra",
            "quiet_hours_end_times_wed_extra",
            "quiet_hours_end_times_thurs_extra",
            "quiet_hours_end_times_fri_extra",
            "quiet_hours_end_times_sat_extra"
        };

        /**
         * Whether to remove the sound from phone ringing during quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and active
         * @hide
         */
        public static final String QUIET_HOURS_RINGER = "quiet_hours_ringer";

        /**
         * Constant: Keep ringer on for all numbers during quiet hours
         * @hide
         */
        public static final int QUIET_HOURS_RINGER_ALLOW_ALL = 0;

        /**
         * Constant: Only ring for numbers in contact list during quiet hours
         * @hide
         */
        public static final int QUIET_HOURS_RINGER_CONTACTS_ONLY = 1;

        /**
         * Constant: Only ring for favorite contacts during quiet hours
         * @hide
         */
        public static final int QUIET_HOURS_RINGER_FAVORITES_ONLY = 2;

        /**
         * Constant: Disable ringer during quiet hours
         * @hide
         */
        public static final int QUIET_HOURS_RINGER_DISABLED = 3;

        /**
         * Whether to remove the sound from outgoing notifications during quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and active
         * @hide
         */
        public static final String QUIET_HOURS_MUTE = "quiet_hours_mute";

        /**
         * Whether to disable haptic feedback during quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and active
         * @hide
         */
        public static final String QUIET_HOURS_HAPTIC = "quiet_hours_haptic";

        /**
         * Whether to remove the vibration from outgoing notifications during quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and active
         * @hide
         */
        public static final String QUIET_HOURS_STILL = "quiet_hours_still";

        /**
         * Whether to attempt to dim the LED color during quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and active
         * @hide
         */
        public static final String QUIET_HOURS_DIM = "quiet_hours_dim";

        /**
         * Whether to remove the system sounds during quiet hours.
         * 0 = Setting disabled
         * 1 = Setting enabled but inactive
         * 2 = Setting enabled and active
         * @hide
         */
        public static final String QUIET_HOURS_SYSTEM = "quiet_hours_system";

        /**
         * Sets the lockscreen background style
         * @hide
         */
        public static final String LOCKSCREEN_BACKGROUND = "lockscreen_background";

        /**
         * Show the pending notification counts as overlays on the status bar
         * @hide
         */
        public static final String SYSTEM_PROFILES_ENABLED = "system_profiles_enabled";

        /**
         * Whether the power menu reboot menu is enabled
         * @hide
         */
        public static final String POWER_MENU_REBOOT_ENABLED = "power_menu_reboot_enabled";

        /**
         * Whether power menu screenshot is enabled
         * @hide
         */
        public static final String POWER_MENU_SCREENSHOT_ENABLED = "power_menu_screenshot_enabled";

        /**
         * Whether power menu profiles switcher is enabled
         * @hide
         */
        public static final String POWER_MENU_PROFILES_ENABLED = "power_menu_profiles_enabled";

        /**
         * Whether power menu screen record is enabled
         * @hide
         */
        public static final String POWER_MENU_SCREENRECORD_ENABLED = "power_menu_screenrecord_enabled";

        /**
         * Whether power menu airplane toggle is enabled
         * @hide
         */
        public static final String POWER_MENU_AIRPLANE_ENABLED = "power_menu_airplane_enabled";

        /**
         * Whether power menu user switcher is enabled
         * @hide
         */
        public static final String POWER_MENU_USER_ENABLED = "power_menu_user_enabled";

        /**
         * Whether power menu silent mode is enabled
         * @hide
         */
        public static final String POWER_MENU_SOUND_ENABLED = "power_menu_silent_enabled";

        /**
          * Swap volume buttons when the screen is rotated
          * 0 - Disabled
          * 1 - Enabled (screen is rotated by 90 or 180 degrees: phone, hybrid)
          * 2 - Enabled (screen is rotated by 180 or 270 degrees: tablet)
          * @hide
          */
         public static final String SWAP_VOLUME_KEYS_ON_ROTATION = "swap_volume_keys_on_rotation";

         /**
          * Volume keys control cursor in text fields (default is 0)
          * 0 - Disabled
          * 1 - Volume up/down moves cursor left/right
          * 2 - Volume up/down moves cursor right/left
          * @hide
          */
         public static final String VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";

        /**
         * toggle to "fix" the following: (found in NotificationManagerService)
         *  new in 4.2: if there was supposed to be a sound and we're in vibrate mode,
         *  we always vibrate, even if no vibration was specified
         * @hide
         */
        public static final String NOTIFICATION_CONVERT_SOUND_TO_VIBRATION = "convert_sound_to_vibration";

        /**
         * Whether to allow notification vibration while notification alerts are disabled
         * (e.g. during phone calls). The vibration pattern to be used will be a subtle one;
         * custom vibration is disabled at that point.
         * @hide
         */
        public static final String NOTIFICATION_VIBRATE_DURING_ALERTS_DISABLED = "vibrate_while_no_alerts";

        /**
         * Vibrate when expanding notifications
         * @hide
         */
        public static final String VIBRATE_NOTIF_EXPAND = "vibrate_notif_expand";

        /**
         * Volume key controls ringtone or media sound stream
         *
         * @hide
         */
        public static final String VOLUME_KEYS_CONTROL_RING_STREAM = "volume_keys_control_ring_stream";

        /**
         * Whether custom hardware key rebinding is enabled
         * @hide
         */
        public static final String HARDWARE_KEY_REBINDING = "hardware_key_rebinding";

        /**
         * Action to perform when the back key is pressed (default: ACTION_BACK)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_BACK_ACTION = "key_back_action";

        /**
         * Action to perform when the back key is long-pressed. (default: ACTION_NULL)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_BACK_LONG_PRESS_ACTION = "key_back_long_press_action";

        /**
         * Action to perform when the back key is double tapped. (default: ACTION_NULL)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_BACK_DOUBLE_TAP_ACTION = "key_back_double_tap_action";

        /**
         * Action to perform when the home key is pressed. (default: ACTION_HOME)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_HOME_ACTION = "key_home_action";

        /**
         * Action to perform when the home key is long-pressed. (default: ACTION_RECENTS)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_HOME_LONG_PRESS_ACTION = "key_home_long_press_action";

        /**
         * Action to perform when the home key is double taped. (default: ACTION_NULL)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_HOME_DOUBLE_TAP_ACTION = "key_home_double_tap_action";

        /**
         * Action to perform when the menu key is pressed. (default: ACTION_MENU)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_MENU_ACTION = "key_menu_action";

        /**
         * Action to perform when the menu key is long-pressed.
         * (Default is ACTION_NULL on devices with a search key, ACTION_SEARCH on devices without)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_MENU_LONG_PRESS_ACTION = "key_menu_long_press_action";

        /**
         * Action to perform when the menu key is double tapped. (default: ACTION_NULL)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_MENU_DOUBLE_TAP_ACTION = "key_menu_double_tap_action";

        /**
         * Action to perform when the assistant (search) key is pressed. (default: ACTION_SEARCH)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_ASSIST_ACTION = "key_assist_action";

        /**
         * Action to perform when the assistant (search) key is long-pressed.
         * (default: ACTION_VOICE_SEARCH)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_ASSIST_LONG_PRESS_ACTION = "key_assist_long_press_action";

        /**
         * Action to perform when the assistant (search) key is double tapped.
         * (default: ACTION_NULL) (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_ASSIST_DOUBLE_TAP_ACTION = "key_assist_double_tap_action";

        /**
         * Action to perform when the app switch key is pressed. (default: ACTION_RECENTS)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_APP_SWITCH_ACTION = "key_app_switch_action";

        /**
         * Action to perform when the app switch key is long-pressed. (default: ACTION_NULL)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_APP_SWITCH_LONG_PRESS_ACTION =
                "key_app_switch_long_press_action";

        /**
         * Action to perform when the app switch key is double tapped. (default: ACTION_NULL)
         * (See ButtonsConstants.java for valid values)
         * @hide
         */
        public static final String KEY_APP_SWITCH_DOUBLE_TAP_ACTION =
                "key_app_switch_double_tap_action";

        /**
         * Whether to unlock the screen with the home key.  The value is boolean (1 or 0).
         * @hide
         */
        public static final String HOME_UNLOCK_SCREEN = "home_unlock_screen";

        /**
         * Show or hide clock
         * 0 - hide
         * 1 - show (default)
         * @hide
         */
        public static final String STATUS_BAR_CLOCK = "status_bar_clock";

        /**
         * AM/PM Style for clock options
         * 0 - Normal AM/PM
         * 1 - Small AM/PM
         * 2 - No AM/PM
         * @hide
         */
        public static final String STATUSBAR_CLOCK_AM_PM_STYLE = "statusbar_clock_am_pm_style";

        /**
         * Style of clock
         * 0 - Hide Clock
         * 1 - Right Clock
         * 2 - Center Clock
         * @hide
         */
        public static final String STATUSBAR_CLOCK_STYLE = "statusbar_clock_style";

        /**
         * Setting for clock color
         * @hide
         */
        public static final String STATUSBAR_CLOCK_COLOR = "statusbar_clock_color";

        /**
        * @hide
        * Shows custom date before clock time
        * 0 - No Date
        * 1 - Small Date
        * 2 - Normal Date
        */
        public static final String STATUSBAR_CLOCK_DATE_DISPLAY = "statusbar_clock_date_display";

        /**
        * @hide
        * Sets the date string style
        * 0 - Regular style
        * 1 - Lowercase
        * 2 - Uppercase
        */
        public static final String STATUSBAR_CLOCK_DATE_STYLE = "statusbar_clock_date_style";

        /**
        * @hide
        * Stores the java DateFormat string for the date
        */
        public static final String STATUSBAR_CLOCK_DATE_FORMAT = "statusbar_clock_date_format";

        /**
         * Whether to mute annoying notifications
         * @hide
         */
        public static final String MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD =
                "mute_annoying_notifications_threshold";

        /**
         * Control the display of the action overflow button within app UI.
         * 0 = use system default
         * 1 = force on
         * @hide
         */
        public static final String UI_FORCE_OVERFLOW_BUTTON = "ui_force_overflow_button";

        /**
         * Automatic keyboard rotation timeout.  0 to disable completely.
         * @hide
         */
        public static final String KEYBOARD_ROTATION_TIMEOUT = "keyboard_rotation_timeout";

    	/**
         * Override and forcefully disable the fullscreen keyboard
         * @hide
         */
        public static final String DISABLE_FULLSCREEN_KEYBOARD = "disable_fullscreen_keyboard";

        /**
         * Forces formal text input.  1 to replace emoticon key with enter key.
         * @hide
         */
        public static final String FORMAL_TEXT_INPUT = "formal_text_input";

        /**
         * Electronic beam animation mode
         * 0 = off,
         * 1 = always horizontal,
         * 2 = always vertical,
         * 3 = dependent on orientation
         * @hide
         */
        public static final String SYSTEM_POWER_CRT_MODE = "system_power_crt_mode";

        /**
         * QuickSettings dynamic tiles configuration
         * @hide
         */
        public static final String QUICK_SETTINGS_DYNAMIC_TILES = "quick_settings_dynamic_tiles";

        /**
         * Number of QuickSettings tiles per row
         * @hide
         */
        public static final String QUICK_TILES_PER_ROW = "quick_tiles_per_row";

        /**
         * Whether on landscape tiles quantity per row are duplicated
         * @hide
         */
        public static final String QUICK_TILES_PER_ROW_DUPLICATE_LANDSCAPE =
                "quick_tiles_per_row_duplicate_landscape";

        /**
         * Color of QuickSettings tiles text
         * @hide
         */
        public static final String QUICK_TILES_TEXT_COLOR = "quick_tiles_text_color";


        /**
         * QuickSettings tiles background color
         *
         * @hide
         */
        public static final String QUICK_TILES_BG_COLOR = "quick_tiles_bg_color";

        /**
         * QuickSettings tiles background color on pressed
         *
         * @hide
         */
        public static final String QUICK_TILES_BG_PRESSED_COLOR = "quick_tiles_bg_pressed_color";

        /**
         * QuickSettings tiles background alpha
         *
         * @hide
         */
        public static final String QUICK_TILES_BG_ALPHA = "quick_tiles_bg_alpha";

        /**
         * QuickSettings music tile mode
         * @hide
         */
        public static final String MUSIC_TILE_MODE = "music_tile_mode";

        /**
         * Custom toggle click/long-click/icons for infinite toggles: actions 1-5
         * @hide
         */
        public static final String CUSTOM_TOGGLE_ACTIONS = "custom_toggle_actions";

        /**
         * Contact strings for infinite toggles
         * @hide
         */
        public static final String TILE_CONTACT_ACTIONS = "tile_contact_actions";

        /**
         * Parsed booleans from string for infinite toggles (unlock/collapse-shade/match-icon)
         * @hide
         */
        public static final String CUSTOM_TOGGLE_EXTRAS = "custom_toggle_extras";

        /**
         * Reminder alert on / off
         * @hide
         */
        public static final String REMINDER_ALERT_ENABLED = "reminder_alert_enabled";

        /**
         * Reminder alert extras
         * 0 = no alert
         * 1 = alert rings once
         * 2 = alert rings until dismissed
         * @hide
         */
        public static final String REMINDER_ALERT_NOTIFY = "reminder_alert_notify";

        /**
         * Reminder alert ringer
         * @hide
         */
        public static final String REMINDER_ALERT_RINGER = "reminder_alert_ringer";

        /**
         * Reminder alert flip interval
         * @hide
         */
        public static final String REMINDER_ALERT_INTERVAL = "reminder_alert_interval";

        /**
         * QuickSettings network modes to switch
         * @hide
         */
        public static final String EXPANDED_NETWORK_MODE = "expanded_network_mode";

        /**
         * QuickSettings screen timeout modes to switch
         * @hide
         */
        public static final String EXPANDED_SCREENTIMEOUT_MODE = "expanded_screentimeout_mode";

        /**
         * QuickSettings ring modes to switch
         * @hide
         */
        public static final String EXPANDED_RING_MODE = "expanded_ring_mode";

        /**
         * Display style of the status bar battery information
         * default: 0
         * @hide
         */
        public static final String STATUS_BAR_BATTERY = "status_bar_battery";

        /**
         * Circle battery icon color
         * in statusbar
         * @hide
         */
        public static final String STATUS_BAR_BATTERY_COLOR = "status_bar_battery_color";

        /**
         * Battery icon text color
         * in statusbar
         * @hide
         */
        public static final String STATUS_BAR_BATTERY_TEXT_COLOR = "status_bar_battery_text_color";

        /**
         * Battery icon text charging color
         * in statusbar
         * @hide
         */
        public static final String STATUS_BAR_BATTERY_TEXT_CHARGING_COLOR =
                "status_bar_battery_text_charging_color";

        /**
         * Circle battery animation speed during charge
         * in statusbar
         * @hide
         */
        public static final String STATUS_BAR_CIRCLE_BATTERY_ANIMATIONSPEED =
                "status_bar_circle_battery_animationspeed";

        /**
         * Whether to show the Circle battery status dotted in statusbar
         * @hide
         */
        public static final String STATUS_BAR_CIRCLE_DOTTED = "battery_circle_dotted";

        /**
         * Length of the Circle battery status dots in statusbar, (if enabled "dotted")
         *
         * Values 0 - 10
         * default : 3
         *
         * @hide
         */
        public static final String STATUS_BAR_CIRCLE_DOT_LENGTH = "battery_circle_dot_length";

        /**
         * Interval of the Circle battery status dots in statusbar, (if enabled "dotted")
         *
         * Values 0 - 10
         * default : 2
         *
         * @hide
         */
        public static final String STATUS_BAR_CIRCLE_DOT_INTERVAL = "battery_circle_dot_interval";

        /**
         * Offset of the Circle battery status dots in statusbar, (if enabled "dotted")
         *
         * Values 0 - 10
         * default : 0
         *
         * @hide
         */
        public static final String STATUS_BAR_CIRCLE_DOT_OFFSET = "battery_circle_dot_offset";

        /**
        * Whether to control brightness from status bar
        *
        * @hide
        */
       public static final String STATUS_BAR_BRIGHTNESS_CONTROL = "status_bar_brightness_control";

        /**
         * MediaScanner behavior on boot.
         * 0 = enabled
         * 1 = ask (notification)
         * 2 = disabled
         * @hide
         */
        public static final String MEDIA_SCANNER_ON_BOOT = "media_scanner_on_boot";

       /**
        * Sets the portrait background of notification drawer
        * @hide
        */
        public static final String NOTIFICATION_BACKGROUND = "notification_background";

       /**
        * Sets the landscape background of notification drawer
        * @hide
        */
        public static final String NOTIFICATION_BACKGROUND_LANDSCAPE = "notification_background_landscape";

       /**
        * Sets the alpha (transparency) of notification wallpaper
        * @hide
        */
        public static final String NOTIFICATION_BACKGROUND_ALPHA = "notification_background_alpha";

       /**
        * Sets the alpha (transparency) of the notification
        * @hide
        */
        public static final String NOTIFICATION_ALPHA = "notification_alpha";

        /**
         * Navigation bar button color
         * @hide
         */
        public static final String NAVIGATION_BAR_BUTTON_TINT = "navigation_bar_button_tint";

        /**
         * Option To Colorize Navigation bar buttons in different modes
         * 0 = all, 1 = system icons, 2 = system icons + custom user icons
         * @hide
         */
        public static final String NAVIGATION_BAR_BUTTON_TINT_MODE = "navigation_bar_button_tint_mode";

        /**
         * Navigation bar glow color
         * @hide
         */
        public static final String NAVIGATION_BAR_GLOW_TINT = "navigation_bar_glow_tint";

        /**
         * Wether navigation bar is enabled or not
         * @hide
         */
        public static final String NAVIGATION_BAR_SHOW = "navigation_bar_show";

        /**
         * Wether navigation bar is on landscape on the bottom or on the right
         * @hide
         */
        public static final String NAVIGATION_BAR_CAN_MOVE = "navigation_bar_can_move";

        /**
         * Navigation bar height when it is on protrait
         * @hide
         */
        public static final String NAVIGATION_BAR_HEIGHT = "navigation_bar_height";

        /**
         * Navigation bar height when it is on landscape at the bottom
         * @hide
         */
        public static final String NAVIGATION_BAR_HEIGHT_LANDSCAPE = "navigation_bar_height_landscape";

        /**
         * Navigation bar height when it is on landscape at the right
         * @hide
         */
        public static final String NAVIGATION_BAR_WIDTH = "navigation_bar_width";

        /**
         * Custom navigation bar intent and action configuration
         * @hide
         */
        public static final String NAVIGATION_BAR_CONFIG = "navigation_bar_config";

        /**
         * Custom navring intent and action configuration
         *
         * @hide
         */
        public static final String NAVRING_CONFIG = "navring_config";

        /**
         * Wether the navbar menu button is on the left/right/both
         * @hide
         */
        public static final String MENU_LOCATION = "menu_location";

        /**
         * Wether the navbar menu button should show or not
         * @hide
         */
        public static final String MENU_VISIBILITY = "menu_visibility";

        /**
         * Hide network labels in the notification drawer
         * @hide
         */
        public static final String NOTIFICATION_HIDE_LABELS = "notification_hide_labels";

        /**
          * Stores values for notification shortcut targets
          * @hide
          */
        public static final String NOTIFICATION_SHORTCUTS_CONFIG = "notification_shortcuts_config";

        /**
         * Stores the value for notification shortcuts icon color
         * @hide
         */
        public static final String NOTIFICATION_SHORTCUTS_COLOR = "notification_shortcuts_color";

        /**
         * Whether to colorize the default application icons
         * @hide
         */
        public static final String NOTIFICATION_SHORTCUTS_COLOR_MODE = "notification_shortcuts_color_mode";

        /**
         * Config for advanced power menu
         *
         * @hide
         */
        public static final String POWER_MENU_CONFIG = "power_menu_config";

        /**
         * Text color for advanced power menu
         *
         * @hide
         */
        public static final String POWER_MENU_TEXT_COLOR = "power_menu_text_color";

        /**
         * Icon color for advanced power menu
         *
         * @hide
         */
        public static final String POWER_MENU_ICON_COLOR = "power_menu_icon_color";

        /**
         * Icon color mode for advanced power menu
         *
         * @hide
         */
        public static final String POWER_MENU_ICON_COLOR_MODE = "power_menu_icon_color_mode";

        /**
         * Expanded desktop on/off state
         * @hide
         */
        public static final String EXPANDED_DESKTOP_STATE = "expanded_desktop_state";

        /**
         * Expanded desktop style (with status bar or without status bar)
         * @hide
         */
        public static final String EXPANDED_DESKTOP_STYLE = "expanded_desktop_style";

        /**
         * Expanded desktop system bars visibility in locked state
         * @hide
         */
        public static final String EXPANDED_DESKTOP_SYSTEM_BARS_VISIBILITY = "expanded_desktop_system_bars_visibility";

        /**
         * Whether fcharge is enabled or not if kernel supports it
         * @hide
         */
        public static final String FCHARGE_ENABLED = "fcharge_enabled";

    	/**
         * Whether or not to show circle battery around the lockscreen ring
         * @hide
         */
        public static final String BATTERY_AROUND_LOCKSCREEN_RING = "battery_around_lockscreen_ring";

        /**
         * HALO, should default to 0 (no, do not show)
         * @hide
         */
        public static final String HALO_ACTIVE = "halo_active";

        /**
         * HALO reversed?, should default to 1 (yes, reverse)
         * @hide
         */
        public static final String HALO_REVERSED = "halo_reversed";

        /** Weather to allow headsethook to launch voice commands
         * @hide
         */
        public static final String HEADSETHOOK_LAUNCH_VOICE = "headsethook_launch_voice";

        /**
         * HALO hide?, should default to 0 (no, do not hide)
         * @hide
         */
        public static final String HALO_HIDE = "halo_hide";

        /**
         * HALO pause activities?, defaults to 0 (no, do not pause) on devices which isLargeRAM() == true
         * otherwise it defaults to 1 (yes, do pause)
         * @hide
         */
        public static final String HALO_PAUSE = "halo_pause";

        /**
         * HALO size fraction, default is 1.0f (normal)
         * @hide
         */
        public static final String HALO_SIZE = "halo_size";

        /**
         * HALO ninja?, should default to 0 (no, do not disappear when empty)
         * @hide
         */
        public static final String HALO_NINJA = "halo_ninja";

        /**
         * HALO message box?, should default to 1 (yes, show message box on incoming notification)
         * @hide
         */
        public static final String HALO_MSGBOX = "halo_msgbox";

        /**
         * HALO notificatoin count?, should default to 4 (both)
         * @hide
         */
        public static final String HALO_NOTIFY_COUNT = "halo_notify_count";

        /**
         * HALO message box animation?, should default to 2 (flip animation)
         * @hide
         */
        public static final String HALO_MSGBOX_ANIMATION = "halo_msgbox_animation";

        /**
         * HALO unlock ping?, should default to 0 (no, do not ping on unlock)
         * @hide
         */
        public static final String HALO_UNLOCK_PING = "halo_unlock_ping";

        /**
         * Enable custom HALO Colors
         * @hide
         **/
        public static final String HALO_COLOR = "halo_color";

        /**
         * HALO Circle Color
         * @hide
         **/
        public static final String HALO_CIRCLE_COLOR = "halo_circle_color";

        /**
         * HALO Effect Color
         **/
        public static final String HALO_EFFECT_COLOR = "halo_effect_color";
        
        /**
         * HALO Notification Title Color
         * @hide
         **/
        public static final String HALO_NOTIFICATION_TITLE_COLOR = "halo_notification_title_color";

        /**
         * HALO Notification Description Color
         **/
        public static final String HALO_NOTIFICATION_DESC_COLOR = "halo_notification_desc_color";
        
        /**
         * HALO Speech Bubble Color
         **/
        public static final String HALO_SPEECH_BUBBLE_COLOR = "halo_speech_bubble_color";
        
        /**
         * HALO Text Color
         **/
        public static final String HALO_TEXT_COLOR = "halo_text_color";

    	/**
         * whether which Ram Usage Bar mode is used on recent switcher
         * 0 = none, 1 = only app use, 2 = app and cache use, 3 = app, cache and system use
         * @hide
         */
        public static final String RECENTS_RAM_BAR_MODE = "recents_ram_bar_mode";

        /**
         * Ram Usage Bar system mem color
         *
         * @hide
         */
        public static final String RECENTS_RAM_BAR_MEM_COLOR = "recents_ram_bar_mem_color";

        /**
         * Ram Usage Bar cached mem color
         *
         * @hide
         */
        public static final String RECENTS_RAM_BAR_CACHE_COLOR = "recents_ram_bar_cache_color";

        /**
         * Ram Usage Bar app mem color
         *
         * @hide
         */
        public static final String RECENTS_RAM_BAR_ACTIVE_APPS_COLOR = "recents_ram_bar_active_apps_color";

        /**
         * @hide
         */
        public static final String WAKELOCK_BLOCKING_ENABLED = "wakelock_blocking_enabled";

        /**
         * @hide
         */
        public static final String WAKELOCK_BLOCKING_LIST = "wakelock_blocking_list";

        /**
         * Whether incomming call UI stays in background
         *
         * @hide
         */
        public static final String CALL_UI_IN_BACKGROUND = "call_ui_in_background";

        /**
         * @hide
         */
        public static final String SMART_PHONE_CALLER = "smart_phone_caller";

        /**
         * Should Flip to Silence be used
         *
         * @hide
         */
        public static final String FLIP_ACTION_KEY = "flip_action";

        /**
         * Should call status sounds be player
         *
         * @hide
         */
        public static final String CALL_END_SOUND = "call_end_sound";

        /**
         * Enable Stylus Gestures
         *
         * @hide
         */
        public static final String ENABLE_STYLUS_GESTURES = "enable_stylus_gestures";

        /**
         * Left Swipe Action
         *
         * @hide
         */
        public static final String GESTURES_LEFT_SWIPE = "gestures_left_swipe";

        /**
         * Right Swipe Action
         *
         * @hide
         */
        public static final String GESTURES_RIGHT_SWIPE = "gestures_right_swipe";

        /**
         * Up Swipe Action
         *
         * @hide
         */
        public static final String GESTURES_UP_SWIPE = "gestures_up_swipe";

        /**
         * down Swipe Action
         *
         * @hide
         */
        public static final String GESTURES_DOWN_SWIPE = "gestures_down_swipe";

        /**
         * Long press Action
         *
         * @hide
         */
        public static final String GESTURES_LONG_PRESS = "gestures_long_press";

        /**
         * double tap Action
         *
         * @hide
         */
        public static final String GESTURES_DOUBLE_TAP = "gestures_double_tap";

        /**
         * What application to launch when the user click the clock in the notification bar
         * @hide
         */
        public static final String CLOCK_SHORTCUT = "clock_shortcut";

        /**
         * What application to launch when the user click the calendar in the notification bar
         * @hide
         */
        public static final String CALENDAR_SHORTCUT = "calendar_shortcut";

        /**
         * show clear all recents button
         *  @hide
         */
        public static final String SHOW_CLEAR_RECENTS_BUTTON = "clear_recents_button";

        /**
         * location of the clear all rectents button
         * @hide
         */
        public static final String CLEAR_RECENTS_BUTTON_LOCATION = "clear_recents_button_location";

        /**
         * Color of the clear all button
         *
         * @hide
         */
        public static final String CLEAR_RECENTS_BUTTON_COLOR = "clear_recents_all_button_color";

        /**
         * show carrier in statusbar. The value is
         * boolean (1 or 0).
         */
        public static final String STATUS_BAR_CARRIER = "status_bar_carrier";

        /**
         * Carrier Label Custom Color
         * @hide
         */
        public static final String STATUS_BAR_CARRIER_COLOR = "status_bar_carrier_color";

        /**
         * custom carrier label. The value is
         * String.
         */
        public static final String CUSTOM_CARRIER_LABEL = "custom_carrier_label";

        /**
         * Color of the carrier and wifi network name in the notification drawer
         * @hide
         */
        public static final String NOTIFICATION_CARRIER_WIFI_LABEL_COLOR = "notification_carrier_wifi_label_color";

        /**
         * Whether or not to use the app sidebar
         *
         * @hide
         */
        public static final String APP_SIDEBAR_ENABLED = "app_sidebar_enabled";

        /**
         * User defined transparency level for sidebar
         *
         * @hide
         */
        public static final String APP_SIDEBAR_TRANSPARENCY = "app_sidebar_transparency";

        /**
         * Disable text labels for app sidebar items
         *
         * @hide
         */
        public static final String APP_SIDEBAR_DISABLE_LABELS = "app_sidebar_disable_labels";

        /**
         * Position of app sidebar
         *
         * @hide
         */
        public static final String APP_SIDEBAR_POSITION = "app_sidebar_position";

        /**
         * Width of the appbar trigger
         *
         * @hide
         */
        public static final String APP_SIDEBAR_TRIGGER_WIDTH = "app_sidebar_trigger_width";

        /**
         * Position of appbar trigger
         *
         * @hide
         */
        public static final String APP_SIDEBAR_TRIGGER_TOP = "app_sidebar_trigger_top";

        /**
         * Height of the appbar trigger
         *
         * @hide
         */
        public static final String APP_SIDEBAR_TRIGGER_HEIGHT = "app_sidebar_trigger_height";

        /**
         * Whether to display the trigger region or not
         *
         * @hide
         */
        public static final String APP_SIDEBAR_SHOW_TRIGGER = "app_sidebar_show_trigger";

        /**
         * Whether the lockscreen vibrate should be enabled.
         * @hide
         */
        public static final String LOCKSCREEN_VIBRATE_ENABLED = "lockscreen.vibrate_enabled";

        // PA PIE //

        /**
         * On or off the Pie.
         * @hide
         */
        public static final String PIE_CONTROLS = "pie_controls";

        /**
         * Pie menu, should default to 1 (yes, show)
         * @hide
         */
        public static final String PIE_MENU = "pie_menu";

        /**
         * Pie will not rotate. Should default to 1, (yes, do not rotate)
         * @hide
         */
        public static final String PIE_STICK = "pie_stick";

        /**
         * Pie search, should default to 1 (yes, show)
         * @hide
         */
        public static final String PIE_SEARCH = "pie_search";

        /**
         * Center Pie? Should default to 1 (yes, center)
         * @hide
         */
        public static final String PIE_CENTER = "pie_center";

        /**
         * Pie last app, should default to 0 (no, show only when needed)
         * @hide
         */
        public static final String PIE_LAST_APP = "pie_last_app";

        /**
         * Pie kill task, default to 0 (off)
         * @hide
         */
        public static final String PIE_KILL_TASK = "pie_kill_task";

        /**
         * Pie action widgets, default to off
         * @hide
         */
        public static final String PIE_APP_WINDOW = "pie_app_window";

        /**
         * Pie action notifications, default to off
         * @hide
         */
        public static final String PIE_ACT_NOTIF = "pie_act_notif";

        /**
         * Pie action quicksettings, default to off
         * @hide
         */
        public static final String PIE_ACT_QS = "pie_act_qs";

        /**
         * Pie screenshot, should default to 0 (no, not show)
         * @hide
         */
        public static final String PIE_SCREENSHOT = "pie_screenshot";

        /**
         * Pie power, should default to 0 (no, show only when needed)
         * @hide
         */
        public static final String PIE_POWER = "pie_power";

        /**
         * Pie torch, should default to 0 (no, show only when needed)
         * @hide
         */
        public static final String PIE_TORCH = "pie_torch";

        /**
         * Pie gesture, should default to 0 (no, show only when needed)
         * @hide
         */
        public static final String PIE_GESTURE = "pie_gesture";

        /**
         * Pie omniSwitch, should default to 0 (no, show only when needed)
         * @hide
         */
        public static final String PIE_OMNISWITCH = "pie_omniSwitch";

        /*
         * Pie gap angle, should default to 2
         * @hide
         */
        public static final String PIE_GAP = "pie_gap";

        /**
         * Pie empty angle, should default to 12
         * @hide
         */
        public static final String PIE_ANGLE = "pie_angle";

        /**
         * Pie trigger fraction, should default to 1
         * @hide
         */
        public static final String PIE_TRIGGER = "pie_trigger";

        /**
         * Location of the pie in the screen
         * 0 = Gravity.LEFT
         * 1 = Gravity.TOP
         * 2 = Gravity.RIGHT
         * 3 = Gravity.BOTTOM (default)
         * @hide
         */
        public static final String PIE_GRAVITY = "pie_gravity";

        /**
         * Pie status report
         * 0 = Bare
         * 1 = Quick
         * 2 = Default
         * 3 = Slow
         * @hide
         */
        public static final String PIE_MODE = "pie_mode";

        /**
         * Pie size fraction, default is 1.0f (normal)
         * @hide
         */
        public static final String PIE_SIZE = "pie_size";

        /**
         * Pie Notification Ability
         * @hide
         */
        public static final String PIE_NOTIFICATIONS = "pie_notifications";

        // PIE COLORS EVERYWHERE! //

        /**
         * @hide
         */
        public static final String PIE_ENABLE_COLOR = "pie_enable_color";

        /**
         * @hide
         */
        public static final String PIE_JUICE = "pie_juice";

        /**
         * @hide
         */
        public static final String PIE_BUTTON_COLOR = "pie_button_color";

        /**
         * @hide
         */
        public static final String PIE_SNAP_BACKGROUND = "pie_snap_background";

        /**
         * @hide
         */
        public static final String PIE_BACKGROUND = "pie_background";

        /**
         * @hide
         */
        public static final String PIE_SELECT = "pie_select";

        /**
         * @hide
         */
        public static final String PIE_OUTLINES = "pie_outlines";

        /**
         * @hide
         */
        public static final String PIE_STATUS_CLOCK = "pie_status_clock";

        /**
         * @hide
         */
        public static final String PIE_STATUS = "pie_status";

        /**
         * @hide
         */
        public static final String PIE_CHEVRON_LEFT = "pie_chevron_left";

        /**
         * @hide
         */
        public static final String PIE_CHEVRON_RIGHT = "pie_chevron_right";

        // PIE COLORS EVERYWHERE! //

        /**
         * Whether pie controls are enabled
         * @hide
         */
        public static final String SPIE_CONTROLS = "spie_controls";

        /**
         * Whether pie triggers on the left and right edge should be reduced if IME shows up.
         * Default = 1 (enabled)
         * @hide
         */
        public static final String PIE_IME_CONTROL = "pie_ime_control";

        /**
         * Whether dynamic menu button is shown or not or dynamic (default)
         * @hide
         */
        public static final String SPIE_MENU = "spie_menu";

        /**
         * Whether right edge PIE is mirrored or not
         * @hide
         */
        public static final String PIE_MIRROR_RIGHT = "pie_mirror_right";

        /**
         * Pie show text (0 or 1)
         * @hide
         */
        public static final String PIE_SHOW_TEXT = "pie_show_text";

        /**
         * Pie show snap (0 or 1)
         * @hide
         */
        public static final String PIE_SHOW_SNAP = "pie_show_snap";

        /**
         * Pie show background (0 or 1)
         * @hide
         */
        public static final String PIE_SHOW_BACKGROUND = "pie_show_background";

        /**
         * Locations of the pie in the screen.
         * (1<<0) = LEFT
         * (1<<1) = BOTTOM
         * (1<<2) = RIGHT
         * (1<<3) = TOP
         * Default: LEFT
         * @hide
         */
        public static final String SPIE_GRAVITY = "spie_gravity";

        /**
         * Relative pie size (fraction)
         * Default: 1.0f
         * @hide
         */
        public static final String SPIE_SIZE = "spie_size";

        /**
         * Pie button color
         * @hide
         */
        public static final String SPIE_BUTTON_COLOR = "spie_button_color";

        /**
         * Pie button press color
         * @hide
         */
        public static final String PIE_BUTTON_PRESSED_COLOR = "pie_button_pressed_color";

        /**
         * Pie button long press color
         * @hide
         */
        public static final String PIE_BUTTON_LONG_PRESSED_COLOR = "pie_button_long_pressed_color";

        /**
         * Pie button outline color
         * @hide
         */
        public static final String PIE_BUTTON_OUTLINE_COLOR = "pie_button_outline_color";

        /**
         * Pie background color
         * @hide
         */
        public static final String PIE_BACKGROUND_COLOR = "pie_background_color";

        /**
         * Pie snap color
         * @hide
         */
        public static final String PIE_SNAP_COLOR = "pie_snap_color";

        /**
         * Pie text color
         * @hide
         */
        public static final String PIE_TEXT_COLOR = "pie_text_color";

        /**
         * Pie icon color
         * @hide
         */
        public static final String PIE_ICON_COLOR = "pie_icon_color";

        /**
         * Pie icon color mode
         * @hide
         */
        public static final String PIE_ICON_COLOR_MODE = "pie_icon_color_mode";

        /**
         * Pie button alpha
         * @hide
         */
        public static final String PIE_BUTTON_ALPHA = "pie_button_alpha";

        /**
         * Pie button pressed and long pressed alpha
         * @hide
         */
        public static final String PIE_BUTTON_PRESSED_ALPHA = "pie_button_pressed_alpha";

        /**
         * Pie background alpha
         * @hide
         */
        public static final String PIE_BACKGROUND_ALPHA = "pie_background_alpha";

        /**
         * Pie buttons configuration first layer
         * @hide
         */
        public static final String PIE_BUTTONS_CONFIG = "pie_buttons_config";

        /**
         * Pie buttons configuration second layer
         * @hide
         */
        public static final String PIE_BUTTONS_CONFIG_SECOND_LAYER =
                "pie_buttons_config_second_layer";

        /**
         * Whether to enable torch by long pressing power from a screen off state
         *
         * @hide
         */
        public static final String ENABLE_FAST_TORCH = "enable_fast_torch";

        /**
         * Width and height of output vide expressed in WxH
         * @hide
         */
        public static final String SCREEN_RECORDER_OUTPUT_DIMENSIONS = "screen_recorder_output_dimensions";

        /**
         * Screen recorder framerate in bits per second
         * @hide
         */
        public static final String SCREEN_RECORDER_BITRATE = "screen_recorder_bitrate";

        /**
         * Whether to include audio when recording a video
         * @hide
         */
        public static final String SCREEN_RECORDER_RECORD_AUDIO = "screen_recorder_record_audio";

        /**
         * Whether to use gesture anywhere feature.
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_ENABLED = "gesture_anywhere_enabled";

        /**
         * Position of gesture anywhere trigger. Value is either Gravity.LEFT or Gravity.RIGHT
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_POSITION = "gesture_anywhere_position";

        /**
         * Last time gestures were altered.
         * Used to determine if gestures should be reloaded by the view.
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_CHANGED = "gesture_anywhere_changed";

        /**
         * Width of the gesture anywhere trigger.
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_TRIGGER_WIDTH = "gesture_anywhere_trigger_width";

        /**
         * Position of gesture anywhere trigger.
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_TRIGGER_TOP = "gesture_anywhere_trigger_top";

        /**
         * Height of the gesture anywhere trigger.
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_TRIGGER_HEIGHT = "gesture_anywhere_trigger_height";

        /**
         * Whether to display the gesture anywhere trigger region or not.
         * Used internally for showing the trigger in settings so user can see its placement
         * @hide
         */
        @ChaosLab(name="GestureAnywhere", classification=Classification.NEW_FIELD)
        public static final String GESTURE_ANYWHERE_SHOW_TRIGGER = "gesture_anywhere_show_trigger";

        /**
         *  Enable statusbar double tap gesture on to put device to sleep
         * @hide
         */
        public static final String DOUBLE_TAP_SLEEP_GESTURE = "double_tap_sleep_gesture";

        /**
         *  Enable statusbar double tap gesture on to put device to sleep
         * @hide
         */
        public static final String LOCKSCREEN_DOUBLE_TAP_SLEEP_GESTURE = "lockscreen_double_tap_sleep_gesture";

        /**
         * Allows to show the background activity back the lockscreen
         * @hide
         */
        public static final String LOCKSCREEN_SEE_THROUGH = "lockscreen_see_through";

        /**
         * Enables/disables lockscreen notifications
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS = "lockscreen_notifications";

        /**
         * A list of packages to exclude from being displayed as lockscreen notifications.
         * This should be a string of packages separated by |
         * @hide
        */
        public static final String LOCKSCREEN_NOTIFICATIONS_EXCLUDED_APPS = "lockscreen_notifications_excluded_apps";

        /**
         * Allows lockscreen notifications based on security type present
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_ALLOWED = "lockscreen_notifications_allowed";

        /**
         * Set a custom notification background color
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_COLOR = "lockscreen_notifications_color";

        /**
         * Wakes the device when a new notifications is received
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_WAKE_ON_NOTIFICATION = "lockscreen_notifications_wake_on_notification";

        /**
         * Turn screen on when device is pulled out of pocket
         * @hide
         */
        public static final String ACTIVE_NOTIFICATIONS_POCKET_MODE = "active_notifications_pocket_mode";

        /**
         * obey quiet hours
         * @hide
         */
        public static final String ACTIVE_NOTIFICATIONS_QUIET_HOURS = "active_notifications_quiet_hours";

        /**
         * Hide low priority notifications such as google now weather notifications from lockscreen notifications
         * @hide
         */
        public static final String ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY = "lockscreen_notifications_hide_low_priority";

        /**
         * Hide non clearable notifications from lockscreen notifications
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_HIDE_NON_CLEARABLE = "lockscreen_notifications_hide_non_clearable";

        /**
         * Allows dismissing even non-clearable notifications from lockscreen notifications.
         * Of course, this doesn't really dismiss them, they're just not shown on lockscreen anymore.
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_DISMISS_ALL = "lockscreen_notifications_dismiss_all";

        /**
         * Toggle between extended and normal view by longpressing the notification
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_EXPANDED_VIEW = "lockscreen_notifications_expanded_view";

        /**
         * Show lockscreen notifications extended when possible
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_FORCE_EXPANDED_VIEW = "lockscreen_notifications_force_expanded_view";

        /**
         * Sets the count of notifications shown at once
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_HEIGHT = "lockscreen_notifications_height";

        /**
         * Changes the offset of the notifications to the top of the screen
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_OFFSET_TOP = "lockscreen_notifications_offset_top";
        
        /**
         * Enables a privacy mode which disables showing notifications.
         * @hide
         */
        public static final String ACTIVE_NOTIFICATIONS_PRIVACY_MODE = "active_notifications_privacy_mode";

        /**
         * Enables dynamic with for shown notifications
         * @hide
         */
        public static final String LOCKSCREEN_NOTIFICATIONS_DYNAMIC_WIDTH = "lockscreen_notifications_dynamic_width";

        /**
         * Allows blurring the lockscreen background
         * @hide
        */
        public static final String LOCKSCREEN_BLUR_BEHIND = "lockscreen_blur_behind";
        public static final String LOCKSCREEN_BLUR_RADIUS = "lockscreen_blur_radius";

        /**
         * Sets the lockscreen background style
         * @hide
         */
        public static final String LOCKSCREEN_BACKGROUND_STYLE = "lockscreen_background_style";

        /**
         * Color for lockscreen background when set to color fill
         * @hide
         */
        public static final String LOCKSCREEN_BACKGROUND_COLOR = "lockscreen_background_color";

        /**
         * @hide
         */
        public static final String WEATHER_PANEL_LONGCLICK = "weather_panel_longclick";

        /**
         * @hide
         */
        public static final String WEATHER_PANEL_SHORTCLICK = "weather_panel_shortclick";

        /**
         * How to show weather on the statusbar
         * 0 = off
         * 1 = AOKP info above carrier
         * 2 = AOKP weather panel
         * 3 = AICP info
         * 4 = AICP notification
         * @hide
         */
        public static final String STATUSBAR_WEATHER_STYLE = "statusbar_weather_style";

        /**
         * @hide
         */
        public static final String USE_WEATHER = "use_weather";

        /**
         * @hide
         */
        public static final String WEATHER_SHOW_LOCATION = "weather_show_location";

        /**
         * @hide
         */
        public static final String SYSTEMUI_WEATHER_NOTIFICATION = "cfx_weather_notification";

        /**
         * @hide
         */
        public static final String SYSTEMUI_WEATHER_ICON = "cfx_weather_icon";

        /**
         * @hide
         */
        public static final String GESTURE_ONE = "gesture_one";

        /**
         * @hide
         */
        public static final String GESTURE_TWO = "gesture_two";

        /**
         * @hide
         */
        public static final String GESTURE_THREE = "gesture_three";

        /**
         * @hide
         */
        public static final String GESTURE_FOUR = "gesture_four";

        /**
         * @hide
         */
        public static final String GESTURE_TYPE_ONE = "gesture_type_one";

        /**
         * Defines the custom path to use for UI sound effects (null for default)
         * @hide
         */
        public static final String CUSTOM_SOUND_EFFECTS_PATH = "custom_sound_effects_path";

         /**
         * Action for long-pressing back button on lock screen
         * @hide
         */
        public static final String LOCKSCREEN_LONG_BACK_ACTION = "lockscreen_long_back_action";

        /**
         * Action for long-pressing home button on lock screen
         * @hide
         */
        public static final String LOCKSCREEN_LONG_HOME_ACTION = "lockscreen_long_home_action";

        /**
         * Action for long-pressing menu button on lock screen
         * @hide
         */
        public static final String LOCKSCREEN_LONG_MENU_ACTION = "lockscreen_long_menu_action";

        /**
         * @hide
         */
        public static final String GESTURE_TYPE_TWO = "gesture_type_two";

        /**
         * @hide
         */
        public static final String GESTURE_TYPE_THREE = "gesture_type_three";

        /**
         * @hide
         */
        public static final String GESTURE_TYPE_FOUR = "gesture_type_four";

        /**
         * @hide
         */
        public static final String GESTURE_APP_ONE = "gesture_app_one";

        /**
         * @hide
         */
        public static final String GESTURE_APP_TWO = "gesture_app_two";

        /**
         * @hide
         */
        public static final String GESTURE_APP_THREE = "gesture_app_three";

        /**
         * @hide
         */
        public static final String GESTURE_APP_FOUR = "gesture_app_four";

        /**
         * @hide
         */
        public static final String TOUCH_ZONE_ONE = "touch_zone_one";

        /**
         * @hide
         */
        public static final String TOUCH_ZONE_TWO = "touch_zone_two";

        /**
         * @hide
         */
        public static final String TOUCH_ZONE_THREE = "touch_zone_three";

        /**
         * @hide
         */
        public static final String TOUCH_ZONE_FOUR = "touch_zone_four";

        /**
          * Always show the battery status on the lockscreen
          * @hide
          */
        public static final String LOCKSCREEN_ALWAYS_SHOW_BATTERY = "lockscreen_always_show_battery";

        /**
         * @hide
         */
        public static final String GESTURE_SWIPE_CAPTURE = "gesture_swipe_capture";

        /**
         * @hide
         */
        public static final String GESTURE_SWIPE_DISTANCE = "gesture_swipe_distance";

        /**
         * @hide
         */
        public static final String SHOW_GESTURES = "show_gestures";

        /**
         * @hide
         */
        public static final String GESTURE_BLACKLIST = "gesture_blacklist";

        /**
         * @hide
         */
        public static final String LARGE_RECENTS = "large_recents";

        /**
         * Whether to show the battery bar
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR = "statusbar_battery_bar";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_COLOR = "statusbar_battery_bar_color";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_THICKNESS = "statusbar_battery_bar_thickness";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_STYLE = "statusbar_battery_bar_style";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_ANIMATE = "statusbar_battery_bar_animate";

        /**
         * use Alt Activity Resolver Grid
         * boolean
         *
         * @hide
         */
        public static final String ACTIVITY_RESOLVER_USE_ALT = "activity_resolver_use_alt";

        /**
         * TeloRadio enable
         * @hide
         */
        public static final String TELO_RADIO_ENABLED = "telo_radio_enabled";

        /**
         * TeloRadio 2g with wifi
         * @hide
         */
        public static final String TELO_RADIO_2G_WIFI = "telo_radio_2g_wifi";

        /**
         * TeloRadio LTE in high power
         * @hide
         */
        public static final String TELO_RADIO_LTE = "telo_radio_lte";

        /**
         * TeloRadio change 2g when screenoff
         * @hide
         */
        public static final String TELO_RADIO_2G_SCREENOFF = "telo_radio_2g_screenoff";

        /**
         * TeloRadio time to change 2g when screenoff
         * @hide
         */
        public static final String TELO_RADIO_2G_SCREENOFF_TIME = "telo_radio_2g_screenoff_timeout";

        /**
         * TeloRadio change 3g when unlock device
         * @hide
         */
        public static final String TELO_RADIO_GO3G_UNLOCK = "telo_radio_go_3g_unlock";

        /**
         *  TeloRadio Low power network
         * @hide
         */
        public static final String TELO_RADIO_LOW_POWER = "telo_radio_low_power";

        /**
         * TeloRadio High power network
         * @hide
         */
        public static final String TELO_RADIO_HIGH_POWER = "telo_radio_high_power";

        /**
         * Enable looking up of phone numbers of nearby places
         *
         * @hide
         */
        public static final String ENABLE_FORWARD_LOOKUP = "enable_forward_lookup";

        /**
         * Enable looking up of phone numbers of people
         *
         * @hide
         */
        public static final String ENABLE_PEOPLE_LOOKUP = "enable_people_lookup";

        /**
         * Enable looking up of information of phone numbers not in the contacts
         *
         * @hide
         */
        public static final String ENABLE_REVERSE_LOOKUP = "enable_reverse_lookup";

        /**
         * The forward lookup provider
         *
         * @hide
         */
        public static final String FORWARD_LOOKUP_PROVIDER = "forward_lookup_provider";

        /**
         * The people lookup provider
         *
         * @hide
         */
        public static final String PEOPLE_LOOKUP_PROVIDER = "people_lookup_provider";

        /**
         * The reverse lookup provider
         *
         * @hide
         */
        public static final String REVERSE_LOOKUP_PROVIDER = "reverse_lookup_provider";

        /**
         * Use EdgeGesture Service for system gestures in PhoneWindowManager
         * @hide
         */
        public static final String USE_EDGE_SERVICE_FOR_GESTURES = "edge_service_for_gestures";

        /**
         * Disable FC Notifications
         *
         * @hide
         */
        public static final String DISABLE_FC_NOTIFICATIONS = "disable_fc_notifications";

        /**
         * Custom Recent Control
         *
         * @hide
         */
        public static final String RECENTS_STYLE = "recents_style";

        /**
         * Whether recent panel gravity is left or right (default = Gravity.RIGHT).
         * @hide
         */
        public static final String RECENT_PANEL_GRAVITY = "recent_panel_gravity";

        /**
         * Size of recent panel view in percent (default = 100).
         * @hide
         */
        public static final String RECENT_PANEL_SCALE_FACTOR = "recent_panel_scale_factor";

        /**
         * User favorite tasks for recent panel.
         * @hide
         */
        public static final String RECENT_PANEL_FAVORITES = "recent_panel_favorites";

        /**
         * iOS8 Bubble Recentes 
         * 0 = Disabled 
         * 1 = Favourite contacts
         * 2 = Recent call contacts
         * @hide
         */
        public static final String BUBBLE_RECENT = "bubble_recent";

        /**
         * The alpha value of the On-The-Go overlay.
         *
         * @hide
         */
        public static final String ON_THE_GO_ALPHA = "on_the_go_alpha";

        /**
         * The camera instance to use.
         * 0 = Rear Camera
         * 1 = Front Camera
         *
         * @hide
         */
        public static final String ON_THE_GO_CAMERA = "on_the_go_camera";

        /**
         * Whether the service should restart itself or not.
         *
         * @hide
         */
        public static final String ON_THE_GO_SERVICE_RESTART = "on_the_go_service_restart";

        /**
         * Whether to display app circle sidebar
         * @hide
         */
        public static final String ENABLE_APP_CIRCLE_BAR = "enable_app_circle_bar";

        /**
         * A list of packages to include in app circle bar.
         * This should be a string of packages separated by |
         * @hide
         */
        public static final String WHITELIST_APP_CIRCLE_BAR = "whitelist_app_circle_bar";

        /**
         * Set app circle bar trigger width.
         * @hide
         */
        public static final String APP_CIRCLE_SIDEBAR_TRIGGER_WIDTH = "app_circle_bar_trigger_width";

        /**
         * Whether to enable voice wakeup.  The value is boolean (1 or 0).
         * @hide
         */
        public static final String VOICE_WAKEUP = "voice_wakeup";

        /**
         * An intent (a flattened Uri String) to launch when user voice launch
         * action is detected. An empty or null string will launch the default
         * voice search activity.
         * @hide
         */
        public static final String VOICE_LAUNCH_INTENT = "voice_launch_intent";

        /**
         * Recent panel expanded mode (auto = 0, always = 1, never = 0).
         * default = 0.
         *
         * @hide
         */
        public static final String RECENT_PANEL_EXPANDED_MODE = "recent_panel_expanded_mode";

        /**
         * Custom system animations
         */
        public static final String[] ACTIVITY_ANIMATION_CONTROLS = new String[] {
                "activity_open",
                "activity_close",
                "task_open",
                "task_close",
                "task_to_front",
                "task_to_back",
                "wallpaper_open",
                "wallpaper_close",
                "wallpaper_intra_open",
                "wallpaper_intra_close",
                "toast_animation",
        };

        /**
         *
         * @hide
         */
        public static final String ANIMATION_CONTROLS_DURATION = "animation_controls_duration";

        /**
         *
         * @hide
         */
        public static final String ANIMATION_CONTROLS_NO_SCROLL = "animation_controls_no_scroll";

        /**
         *
         * @hide
         */
        public static final String ANIMATION_CONTROLS_NO_OVERRIDE = "animation_controls_no_override";

        /**
         *
         * @hide
         */
        public static final String LISTVIEW_ANIMATION_CACHE = "listview_animation_cache";

        /**
         *
         * @hide
         */
        public static final String LISTVIEW_ANIMATION_EXCLUDED_APPS = "listview_animation_excluded_apps";

        /**
         * ListView Animations
         * 0 == None
         * 1 == Wave (Left)
         * 2 == Wave (Right)
         * 3 == Scale
         * 4 == Alpha
         * 5 == Stack (Top)
         * 6 == Stack (Bottom)
         * 7 == Translate (Left)
         * 8 == Translate (Right)
         * @hide
         */
        public static final String LISTVIEW_ANIMATION = "listview_animation";

        /**
         * ListView Interpolators
         * 0 == None
         * 1 == accelerate_interpolator
         * 2 == decelerate_interpolator
         * 3 == accelerate_decelerate_interpolator
         * 4 == anticipate_interpolator
         * 5 == overshoot_interpolator
         * 6 == anticipate_overshoot_interpolator
         * 7 == bounce_interpolator
         * @hide
         */
        public static final String LISTVIEW_INTERPOLATOR = "listview_interpolator";

        /**
         *
         * @hide
         */
        public static final String LISTVIEW_DURATION = "listview_duration";

        /**
         *
         * @hide
         */
        public static final String ANIMATION_IME_DURATION = "animation_ime_duration";

        /**
         *
         * @hide
         */
        public static final String ANIMATION_IME_ENTER = "animation_ime_enter";

        /**
         *
         * @hide
         */
        public static final String ANIMATION_IME_EXIT = "animation_ime_exit";

        /**
         *
         * @hide
         */
        public static final String ANIMATION_IME_INTERPOLATOR = "animation_ime_interpolator";

        /**
         * Determine custom scroll friction.
         * @hide
         */
        public static final String CUSTOM_SCROLL_FRICTION = "custom_scroll_friction";

        /**
         * Determine custom fling velocity.
         * @hide
         */
        public static final String CUSTOM_FLING_VELOCITY = "custom_fling_velocity";

        /**
         * Determine custom overscroll distance.
         * @hide
         */
        public static final String CUSTOM_OVERSCROLL_DISTANCE = "custom_overscroll_distance";

        /**
         * Determine custom overfling distance.
         * @hide
         */
        public static final String CUSTOM_OVERFLING_DISTANCE = "custom_overfling_distance";

    	/**
         *
         * SMOOTH PROGRESS BAR Mirror
         * @hide
         */
        public static final String PROGRESSBAR_MIRROR = "progressbar_mirror";

        /**
         *
         * SMOOTH PROGRESS BAR Reverse
         * @hide
         */
        public static final String PROGRESSBAR_REVERSE = "progressbar_reverse";

        /**
         *
         * SMOOTH PROGRESS BAR Speed
         * @hide
         */
        public static final String PROGRESSBAR_SPEED = "progressbar_speed";

        /**
         *
         * SMOOTH PROGRESS BAR Width
         * @hide
         */
        public static final String PROGRESSBAR_WIDTH = "progressbar_width";

        /**
         *
         * SMOOTH PROGRESS BAR Length
         * @hide
         */
        public static final String PROGRESSBAR_LENGTH = "progressbar_length";

        /**
         *
         * SMOOTH PROGRESS BAR Count
         * @hide
         */
        public static final String PROGRESSBAR_COUNT = "progressbar_count";

        /**
         *
         * SMOOTH PROGRESS BAR Color_1
         * @hide
         */
        public static final String PROGRESSBAR_COLOR_1 = "progressbar_color_1";

        /**
         *
         * SMOOTH PROGRESS BAR Color_2
         * @hide
         */
        public static final String PROGRESSBAR_COLOR_2 = "progressbar_color_2";

        /**
         *
         * SMOOTH PROGRESS BAR Color_3
         * @hide
         */
        public static final String PROGRESSBAR_COLOR_3 = "progressbar_color_3";

        /**
         *
         * SMOOTH PROGRESS BAR Color_4
         * @hide
         */
        public static final String PROGRESSBAR_COLOR_4 = "progressbar_color_4";

        /**
         * OverScroll Glow Color
         * @hide
         */
        public static final String OVERSCROLL_GLOW_COLOR = "overscroll_glow_color";

        /**
         * OverScroll effects configuration
         * @hide
         */
        public static final String OVERSCROLL_EFFECT = "overscroll_effect";

        /**
         * Sets the overscroller weight (edge bounce effect on lists)
         * @hide
         */
        public static final String OVERSCROLL_WEIGHT = "overscroll_weight";

        /**
         * Settings: Whether to force multi pane mode for Settings
         *
         * @hide
         */
        public static final String FORCE_MULTI_PANE = "force_multi_pane";

        /**
         * Statusbar Wifi Color
         * @hide
         */
        public static final String STATUS_BAR_WIFI_COLOR = "status_bar_wifi_color";

        /**
         * Statusbar Data Color
         * @hide
         */
        public static final String STATUS_BAR_DATA_COLOR = "status_bar_data_color";

        /**
         * Statusbar Airplain Color
         * @hide
         */
        public static final String STATUS_BAR_AIRPLANE_COLOR = "status_bar_airplane_color";

        /**
         * Statusbar Volume Color
         * @hide
         */
        public static final String STATUS_BAR_VOLUME_COLOR = "status_bar_volume_color";

        /**
         * Whether to not showing active display when there is annoying notifications.
         * Set the timeout of peek when pikcing up the device
         * @hide
         */
        public static final String PEEK_PICKUP_TIMEOUT = "peek_pickup_timeout";

        /**
         * Time to show notification
         * 5000ms = default
         * @hide
         */
        public static final String PEEK_WAKE_TIMEOUT = "peek_wake_timeout";

        /**
         * In call dialpad state.
         * 0 = hidden
         * 1 = showing
         * @hide
         */
        public static final String DIALPAD_STATE = "dialpad_state";

        /**
         * Display second in the Clock
         * @hide
         */
        public static final String CLOCK_USE_SECOND = "clock_use_second";

        /**
         * Weather Tile Icon
         * @hide
         */
        public static final String WEATHER_TILE_ICON = "weather_tile_icon";

        /**
         * Heads Up background color
         * @hide
         */
        public static final String HEADS_UP_BG_COLOR = "heads_up_bg_color";

        /**
         * Heads Up text color
         * @hide
         */
        public static final String HEADS_UP_TEXT_COLOR = "heads_up_text_color";

        /**
         * Sensitivity of all system shake events
         * @hide
         */
        public static final String SHAKE_SENSITIVITY = "shake_sensitivity";

        /**
         * Apps where shake events are disabled
         * @hide
         */
        public static final String DISABLED_SHAKE_APPS = "disabled_shake_apps";

        /**
         * Whether to enable the shake listener actions.
         * @hide
         */
        public static final String SHAKE_LISTENER_ENABLED = "shake_listener_enabled";

        /**
         * Shake events for shaking along the x, y, and z axis.
         * @hide
         */
        public static final String[] SHAKE_EVENTS_REGULAR = new String[] {
            "shake_events_regular_x",
            "shake_events_regular_y",
            "shake_events_regular_z"
        };

        /**
         * Recent panel: Show topmost task
         *
         * @hide
         */
        public static final String RECENT_PANEL_SHOW_TOPMOST = "recent_panel_show_topmost";

        /**
         * Hover, default is 0 (off).
         * 0 = disabled
         * 1 = enabled
         * @hide
         */
        public static final String HOVER_STATE = "hover_state";

        /**
         * Hover is enabled, default is 0 (off).
         * @hide
         */
        public static final String HOVER_ENABLED = "hover_enabled";

        /**
         * Hover is active, default is 0 (off).
         * 0 = disabled
         * 1 = enabled
         * @hide
         */
        public static final String HOVER_ACTIVE = "hover_active";

        /**
         * Hide HOVER-Button is StatusBar, default is 0 (off).
         *
         * @hide
         */
        public static final String HOVER_HIDE_BUTTON = "hover_hide_button";

        /**
         * Hover: long fade out delay, default is 5000ms (5s).
         *
         * @hide
         */
        public static final String HOVER_LONG_FADE_OUT_DELAY = "hover_long_fade_out_delay";

        /**
         * Hover: micro fade out delay, default is 1250ms (1,25s).
         *
         * @hide
         */
        public static final String HOVER_MICRO_FADE_OUT_DELAY = "hover_micro_fade_out_delay";

        /**
         * Hover: Only show up if StatusBar is hidden, default is 1 (on).
         *
         * @hide
         */
        public static final String HOVER_REQUIRE_FULLSCREEN_MODE = "hover_require_fullscreen_mode";

        /**
         * Hover: Exclude non-clearable notifications, default is 0 (off).
         *
         * @hide
         */
        public static final String HOVER_EXCLUDE_NON_CLEARABLE = "hover_exclude_non_clearable";

        /**
         * Hover: Exclude low priority notifications, default is 0 (off).
         *
         * @hide
         */
        public static final String HOVER_EXCLUDE_LOW_PRIORITY = "hover_exclude_low_priority";

        /**
         * Hover: Option to exclude from insecure lockscreen, default is 0 (off).
         *
         * @hide
         */
        public static final String HOVER_EXCLUDE_FROM_INSECURE_LOCK_SCREEN = "hover_exclude_from_insecure_lock_screen";

        /**
         * Padding above and below dialpad keys in dialer.
         * @hide
         */
        public static final String DIALKEY_PADDING = "dialkey_padding";

        /**
         * Swipe recents for floating windows option
         * @hide
         */
        public static final String RECENTS_SWIPE_FLOATING = "recents_swipe_floating";

        /**
         * Swipe notification for floating window option
         * @hide
         */
        public static final String STATUS_BAR_NOTIFICATION_SWIPE_FLOATING = "status_bar_notification_swipe_floating";

        /**
         * Disable Immersive Message
         * @hide
         */
        public static final String DISABLE_IMMERSIVE_MESSAGE = "disable_immersive_message";

        /**
         * Use HOME/END instead of UP/DOWN as longpress action for NavBar IME Cursors
         *
         * @hide
         */
        public static final String IME_CURSOR_LONGPRESS_ACTION = "ime_cursor_longpress_action";

       /**
         * Whether to enable swiping your finger across the statusbar to change the brightness.
         * Boolean value. Defaults to true.
         * @hide
         */
        public static final String STATUSBAR_BRIGHTNESS_SLIDER = "statusbar_brightness_slider";

        /**
         * @hide
         */
        public static final String STATUSBAR_TOGGLES_BRIGHTNESS_LOC = "statusbar_toggles_brightness_loc";

        /**
         * Locale for secondary overlay on dialer for t9 search input
         * @hide
         */
        public static final String T9_SEARCH_INPUT_LOCALE = "t9_search_input_locale";

        /**
         * @hide
         */
        public static final String PROXIMITY_ON_WAKE = "proximity_on_wake";

        /**
         * Recent panel background color
         *
         * @hide
         */
        public static final String RECENT_PANEL_BG_COLOR = "recent_panel_bg_color";

        /**
         * Recents Panel Custom Color for Stock View
         * @hide
         */
        public static final String RECENTS_PANEL_STOCK_COLOR = "recents_panel_stock_color";

        /**
         * Detailed incall info
         *
         * @hide
         */
        public static final String DETAILED_INCALL_INFO = "detailed_incall_info";

        /**
         * The style of the incoming call screen.
         * Default is {@link INCOMING_CALL_STYLE_FULLSCREEN_PHOTO}.
         * @hide
         */
        public static final String INCOMING_CALL_STYLE = "incoming_call_style";

        /** @hide */
        public static final int INCOMING_CALL_STYLE_CLASSIC = 0;
        /** @hide */
        public static final int INCOMING_CALL_STYLE_FULLSCREEN_PHOTO = 1;

        /**
         * Boolean value. Whether to show the 4G icon when on LTE.
         * True = show 4G
         * False = show LTE
         * @hide
         */
        public static final String STATUSBAR_SIGNAL_SHOW_4G_FOR_LTE = "statusbar_signal_show_4g_for_lte";

        /**
         * Show call recording button in incallui (default = 0)
         * @hide
         */
        public static final String ALLOW_CALL_RECORDING = "allow_call_recording";

        /**
         * whether to colorize the account icons of the settings app root list
         *
         * @hide
         */
        public static final String SETTINGS_ROOT_LIST_COLORIZE_ACCOUNT_ICONS = "settings_root_list_colorize_account_icons";

        /**
         * Colors of the settings app root list icons
         *
         * @hide
         */
         public static final String SETTINGS_ROOT_LIST_ICON_COLOR = "settings_root_list_icon_color";

        /**
         * Colors of the settings app root list category text
         *
         * @hide
         */
         public static final String SETTINGS_ROOT_LIST_CATEGORY_TEXT_COLOR = "settings_root_list_category_text_color";

        /**
         * Colors of the settings app root list title text
         *
         * @hide
         */
        public static final String SETTINGS_ROOT_LIST_TITLE_TEXT_COLOR = "settings_root_list_title_text_color";

        /**
         * Text color of the settings app root list switch widget for state on
         * 
         * @hide
         */
        public static final String SETTINGS_ROOT_LIST_SWITCH_ON_TEXT_COLOR = "settings_root_list_switch_on_text_color";

        /**
         * Text color of the settings app root list switch widget for state off
         * 
         * @hide
         */
        public static final String SETTINGS_ROOT_LIST_SWITCH_OFF_TEXT_COLOR = "settings_root_list_switch_off_text_color";

        /**
         * Status bar opaque color
         * @hide
         */
         public static final String STATUS_BAR_OPAQUE_COLOR = "status_bar_opaque_color";

        /**
         * Status bar semi transparent color
         * @hide
         */
        public static final String STATUS_BAR_SEMI_TRANS_COLOR = "status_bar_semi_trans_color";

        /**
         * Status bar gradient color
         * @hide
         */
        public static final String STATUS_BAR_GRADIENT_COLOR = "status_bar_gradient_color";

        /**
         * Navigation bar opaque color
         * @hide
         */
         public static final String NAVIGATION_BAR_OPAQUE_COLOR = "navigation_bar_opaque_color";

        /**
         * Navigation bar semi transparent color
         * @hide
         */
        public static final String NAVIGATION_BAR_SEMI_TRANS_COLOR = "navigation_bar_semi_trans_color";

        /**
         * Navigation bar gradient color
         * @hide
         */
        public static final String NAVIGATION_BAR_GRADIENT_COLOR = "navigation_bar_gradient_color";

        /**
         * Wether to play the bootanimation preview looped
         * @hide
         */
        public static final String BOOTANIMATION_PREVIEW_LOOP = "bootanimation_preview_loop";

        /**
         * Recent card background color
         *
         * @hide
         */
        public static final String RECENT_CARD_BG_COLOR = "recent_card_bg_color";

        /**
         * Recent card text color
         *
         * @hide
         */
        public static final String RECENT_CARD_TEXT_COLOR = "recent_card_text_color";

        /**
         * touch wake
         *
         * @hide
         */
        public static final String TOUCH_WAKE = "touch_wake";

        /**
         * Whether lid wakes the device
         * @hide
         */
        public static final String LOCKSCREEN_LID_WAKE = "lockscreen_lid_wake";

        /**
         * Whether lid puts the device to sleep
         * @hide
         */
        public static final String LOCKSCREEN_LID_SLEEP = "lockscreen_lid_sleep";

        /**
         * Whether the smart cover is activated or not
         * @hide
         */
        public static final String SMART_COVER_ACTIVATED = "smart_cover_activated";

        /**
         * This preference enables showing the power menu on LockScreen.
         * @hide
         */
        public static final String LOCKSCREEN_ENABLE_POWER_MENU = "lockscreen_enable_power_menu";

        /**
         * Force expanded notifications on all apps that support it.
         * @hide
         */
        public static final String FORCE_EXPANDED_NOTIFICATIONS = "force_expanded_notifications";

        /**
         * Whether wifi settings will connect to access point automatically
         * 0 = automatically
         * 1 = manually
         * @hide
         */
        public static final String WIFI_AUTO_CONNECT_TYPE = "wifi_auto_connect_type";

        /**
         * Whether wifi settings will connect to access point automatically when
         * network from mobile network transform to Wifi network
         * 0 = automatically
         * 1 = manually
         * 2 = always ask
         *
         * @hide
         */
        public static final String DATA_TO_WIFI_CONNECT_TYPE = "data_to_wifi_connect_type";

        /**
         * Whether to disable forced navigation bar during immersive mode and keyboard is showing
         *
         * @hide
         */
        public static final String DISABLE_FORCED_NAVBAR = "disable_forced_navbar";

        /**
         * Whether to disable navbar or statusbar system gesture when in immersive mode
         * 0 - both enabled (default)
         * 1 - disable navbar gesture
         * 2 - disable statusbar gesture
         * @hide
         */
        public static final String DISABLE_SYSTEM_GESTURES = "disable_system_gestures";

        /**
        　* Optionally hide the status bar alarm icon
         * 0: default, shown
         * 1: hidden
        　* @hide
         */
        public static final String STATUS_BAR_HIDE_ALARM_ICON = "statusbar_hide_alarm_icon";

        /**
         * Disable the statusbar ticker
         * Default is enabled
         * @hide
         */
        public static final String TICKER_DISABLED = "ticker_disabled";

         /**
         * Settings to backup. This is here so that it's in the same place as the settings
         * keys and easy to update.
         *
         * NOTE: Settings are backed up and restored in the order they appear
         *       in this array. If you have one setting depending on another,
         *       make sure that they are ordered appropriately.
         *
         * @hide
         */
        public static final String[] SETTINGS_TO_BACKUP = {
            STAY_ON_WHILE_PLUGGED_IN,   // moved to global
            WIFI_USE_STATIC_IP,
            WIFI_STATIC_IP,
            WIFI_STATIC_GATEWAY,
            WIFI_STATIC_NETMASK,
            WIFI_STATIC_DNS1,
            WIFI_STATIC_DNS2,
            MMS_AUTO_RETRIEVAL,
            MMS_AUTO_RETRIEVAL_ON_ROAMING,
            BLUETOOTH_DISCOVERABILITY,
            BLUETOOTH_DISCOVERABILITY_TIMEOUT,
            BLUETOOTH_ACCEPT_ALL_FILES,
            DIM_SCREEN,
            SCREEN_OFF_TIMEOUT,
            SCREEN_BRIGHTNESS,
            SCREEN_BRIGHTNESS_MODE,
            SCREEN_AUTO_BRIGHTNESS_ADJ,
            VIBRATE_INPUT_DEVICES,
            MODE_RINGER_STREAMS_AFFECTED,
            VOLUME_VOICE,
            VOLUME_SYSTEM,
            VOLUME_RING,
            VOLUME_MUSIC,
            VOLUME_ALARM,
            VOLUME_NOTIFICATION,
            VOLUME_BLUETOOTH_SCO,
            VOLUME_VOICE + APPEND_FOR_LAST_AUDIBLE,
            VOLUME_SYSTEM + APPEND_FOR_LAST_AUDIBLE,
            VOLUME_RING + APPEND_FOR_LAST_AUDIBLE,
            VOLUME_MUSIC + APPEND_FOR_LAST_AUDIBLE,
            VOLUME_ALARM + APPEND_FOR_LAST_AUDIBLE,
            VOLUME_NOTIFICATION + APPEND_FOR_LAST_AUDIBLE,
            VOLUME_BLUETOOTH_SCO + APPEND_FOR_LAST_AUDIBLE,
            TEXT_AUTO_REPLACE,
            TEXT_AUTO_CAPS,
            TEXT_AUTO_PUNCTUATE,
            TEXT_SHOW_PASSWORD,
            AUTO_TIME,                  // moved to global
            AUTO_TIME_ZONE,             // moved to global
            TIME_12_24,
            DATE_FORMAT,
            ACCELEROMETER_ROTATION,
            LOCKSCREEN_ROTATION,
            USER_ROTATION,
            DTMF_TONE_WHEN_DIALING,
            DTMF_TONE_TYPE_WHEN_DIALING,
            HEARING_AID,
            TTY_MODE,
            NOISE_SUPPRESSION,
            SOUND_EFFECTS_ENABLED,
            HAPTIC_FEEDBACK_ENABLED,
            POWER_SOUNDS_ENABLED,       // moved to global
            DOCK_SOUNDS_ENABLED,        // moved to global
            LOCKSCREEN_SOUNDS_ENABLED,
            SHOW_WEB_SUGGESTIONS,
            NOTIFICATION_LIGHT_PULSE,
            NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR,
            NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON,
            NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF,
            NOTIFICATION_VIBRATE_DURING_ALERTS_DISABLED,
            SIP_CALL_OPTIONS,
            SIP_RECEIVE_CALLS,
            POINTER_SPEED,
            VIBRATE_WHEN_RINGING,
            RINGTONE,
            NOTIFICATION_SOUND,
            QUIET_HOURS_ENABLED,
            QUIET_HOURS_START,
            QUIET_HOURS_END,
            QUIET_HOURS_RINGER,
            QUIET_HOURS_MUTE,
            QUIET_HOURS_SYSTEM,
            QUIET_HOURS_HAPTIC,
            QUIET_HOURS_STILL,
            QUIET_HOURS_DIM,
            SYSTEM_PROFILES_ENABLED,
            POWER_MENU_SCREENSHOT_ENABLED,
            POWER_MENU_REBOOT_ENABLED,
            POWER_MENU_PROFILES_ENABLED,
            POWER_MENU_AIRPLANE_ENABLED,
            POWER_MENU_SOUND_ENABLED,
            POWER_MENU_USER_ENABLED,
            LOCKSCREEN_VIBRATE_ENABLED,
            LOCKSCREEN_MODLOCK_ENABLED,
            PHONE_BLACKLIST_ENABLED,
            PHONE_BLACKLIST_NOTIFY_ENABLED,
            PHONE_BLACKLIST_PRIVATE_NUMBER_MODE,
            PHONE_BLACKLIST_UNKNOWN_NUMBER_MODE,
            PHONE_BLACKLIST_REGEX_ENABLED,
            LOCKSCREEN_ALWAYS_SHOW_BATTERY,
            TELO_RADIO_ENABLED,
            TELO_RADIO_2G_WIFI,
            TELO_RADIO_LTE,
            TELO_RADIO_2G_SCREENOFF,
            TELO_RADIO_GO3G_UNLOCK,
            INCOMING_CALL_STYLE,
        };

        /**
         * Settings to reset on user choice. They will fall back to their default value (0).
         *
         * @hide
         */
        public static final String[] SETTINGS_TO_RESET = {
            RECENTS_SWIPE_FLOATING,
            STATUS_BAR_NOTIFICATION_SWIPE_FLOATING
        };

        // Settings moved to Settings.Secure

        /**
         * @deprecated Use {@link android.provider.Settings.Global#ADB_ENABLED}
         * instead
         */
        @Deprecated
        public static final String ADB_ENABLED = Global.ADB_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#ANDROID_ID} instead
         */
        @Deprecated
        public static final String ANDROID_ID = Secure.ANDROID_ID;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#BLUETOOTH_ON} instead
         */
        @Deprecated
        public static final String BLUETOOTH_ON = Global.BLUETOOTH_ON;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DATA_ROAMING} instead
         */
        @Deprecated
        public static final String DATA_ROAMING = Global.DATA_ROAMING;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DEVICE_PROVISIONED} instead
         */
        @Deprecated
        public static final String DEVICE_PROVISIONED = Global.DEVICE_PROVISIONED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#HTTP_PROXY} instead
         */
        @Deprecated
        public static final String HTTP_PROXY = Global.HTTP_PROXY;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#INSTALL_NON_MARKET_APPS} instead
         */
        @Deprecated
        public static final String INSTALL_NON_MARKET_APPS = Global.INSTALL_NON_MARKET_APPS;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOCATION_PROVIDERS_ALLOWED}
         * instead
         */
        @Deprecated
        public static final String LOCATION_PROVIDERS_ALLOWED = Secure.LOCATION_PROVIDERS_ALLOWED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOGGING_ID} instead
         */
        @Deprecated
        public static final String LOGGING_ID = Secure.LOGGING_ID;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#NETWORK_PREFERENCE} instead
         */
        @Deprecated
        public static final String NETWORK_PREFERENCE = Global.NETWORK_PREFERENCE;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#PARENTAL_CONTROL_ENABLED}
         * instead
         */
        @Deprecated
        public static final String PARENTAL_CONTROL_ENABLED = Secure.PARENTAL_CONTROL_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#PARENTAL_CONTROL_LAST_UPDATE}
         * instead
         */
        @Deprecated
        public static final String PARENTAL_CONTROL_LAST_UPDATE = Secure.PARENTAL_CONTROL_LAST_UPDATE;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#PARENTAL_CONTROL_REDIRECT_URL}
         * instead
         */
        @Deprecated
        public static final String PARENTAL_CONTROL_REDIRECT_URL =
            Secure.PARENTAL_CONTROL_REDIRECT_URL;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#SETTINGS_CLASSNAME} instead
         */
        @Deprecated
        public static final String SETTINGS_CLASSNAME = Secure.SETTINGS_CLASSNAME;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#USB_MASS_STORAGE_ENABLED} instead
         */
        @Deprecated
        public static final String USB_MASS_STORAGE_ENABLED = Global.USB_MASS_STORAGE_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#USE_GOOGLE_MAIL} instead
         */
        @Deprecated
        public static final String USE_GOOGLE_MAIL = Global.USE_GOOGLE_MAIL;

       /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_MAX_DHCP_RETRY_COUNT} instead
         */
        @Deprecated
        public static final String WIFI_MAX_DHCP_RETRY_COUNT = Global.WIFI_MAX_DHCP_RETRY_COUNT;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS} instead
         */
        @Deprecated
        public static final String WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS =
                Global.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON} instead
         */
        @Deprecated
        public static final String WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
                Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON;

        /**
         * wake up when plugged or unplugged
         *
         * @hide
         */
        public static final String WAKEUP_WHEN_PLUGGED_UNPLUGGED = "wakeup_when_plugged_unplugged";

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY} instead
         */
        @Deprecated
        public static final String WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY =
                Global.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_NUM_OPEN_NETWORKS_KEPT}
         * instead
         */
        @Deprecated
        public static final String WIFI_NUM_OPEN_NETWORKS_KEPT = Global.WIFI_NUM_OPEN_NETWORKS_KEPT;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_ON} instead
         */
        @Deprecated
        public static final String WIFI_ON = Global.WIFI_ON;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE}
         * instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE =
                Secure.WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#WIFI_WATCHDOG_AP_COUNT} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_AP_COUNT = Secure.WIFI_WATCHDOG_AP_COUNT;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS =
                Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED =
                Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS}
         * instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS =
                Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT =
            Secure.WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#WIFI_WATCHDOG_MAX_AP_CHECKS}
         * instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_MAX_AP_CHECKS = Secure.WIFI_WATCHDOG_MAX_AP_CHECKS;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_WATCHDOG_ON} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_ON = Global.WIFI_WATCHDOG_ON;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#WIFI_WATCHDOG_PING_COUNT} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_PING_COUNT = Secure.WIFI_WATCHDOG_PING_COUNT;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#WIFI_WATCHDOG_PING_DELAY_MS}
         * instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_PING_DELAY_MS = Secure.WIFI_WATCHDOG_PING_DELAY_MS;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#WIFI_WATCHDOG_PING_TIMEOUT_MS}
         * instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_PING_TIMEOUT_MS =
            Secure.WIFI_WATCHDOG_PING_TIMEOUT_MS;
    }

    /**
     * Secure system settings, containing system preferences that applications
     * can read but are not allowed to write.  These are for preferences that
     * the user must explicitly modify through the system UI or specialized
     * APIs for those values, not modified directly by applications.
     */
    public static final class Secure extends NameValueTable {
        public static final String SYS_PROP_SETTING_VERSION = "sys.settings_secure_version";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/secure");

        // Populated lazily, guarded by class object:
        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_SECURE,
                CALL_METHOD_PUT_SECURE);

        private static ILockSettings sLockSettings = null;

        private static boolean sIsSystemProcess;
        private static final HashSet<String> MOVED_TO_LOCK_SETTINGS;
        private static final HashSet<String> MOVED_TO_GLOBAL;
        static {
            MOVED_TO_LOCK_SETTINGS = new HashSet<String>(3);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_PATTERN_ENABLED);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_PATTERN_VISIBLE);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_GESTURE_ENABLED);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_GESTURE_VISIBLE);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_PATTERN_SIZE);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_DOTS_VISIBLE);
            MOVED_TO_LOCK_SETTINGS.add(Secure.LOCK_SHOW_ERROR_PATH);

            MOVED_TO_GLOBAL = new HashSet<String>();
            MOVED_TO_GLOBAL.add(Settings.Global.ADB_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.ASSISTED_GPS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.BLUETOOTH_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.BUGREPORT_IN_POWER_MENU);
            MOVED_TO_GLOBAL.add(Settings.Global.CDMA_CELL_BROADCAST_SMS);
            MOVED_TO_GLOBAL.add(Settings.Global.CDMA_ROAMING_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.CDMA_SUBSCRIPTION_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.DATA_ACTIVITY_TIMEOUT_MOBILE);
            MOVED_TO_GLOBAL.add(Settings.Global.DATA_ACTIVITY_TIMEOUT_WIFI);
            MOVED_TO_GLOBAL.add(Settings.Global.DATA_ROAMING);
            MOVED_TO_GLOBAL.add(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.DEVICE_PROVISIONED);
            MOVED_TO_GLOBAL.add(Settings.Global.DISPLAY_DENSITY_FORCED);
            MOVED_TO_GLOBAL.add(Settings.Global.DISPLAY_SIZE_FORCED);
            MOVED_TO_GLOBAL.add(Settings.Global.DOWNLOAD_MAX_BYTES_OVER_MOBILE);
            MOVED_TO_GLOBAL.add(Settings.Global.DOWNLOAD_RECOMMENDED_MAX_BYTES_OVER_MOBILE);
            MOVED_TO_GLOBAL.add(Settings.Global.INSTALL_NON_MARKET_APPS);
            MOVED_TO_GLOBAL.add(Settings.Global.MOBILE_DATA);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_DEV_BUCKET_DURATION);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_DEV_DELETE_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_DEV_PERSIST_BYTES);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_DEV_ROTATE_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_GLOBAL_ALERT_BYTES);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_POLL_INTERVAL);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_REPORT_XT_OVER_DEV);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_SAMPLE_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_TIME_CACHE_MAX_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_BUCKET_DURATION);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_DELETE_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_PERSIST_BYTES);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_ROTATE_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_TAG_BUCKET_DURATION);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_TAG_DELETE_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_TAG_PERSIST_BYTES);
            MOVED_TO_GLOBAL.add(Settings.Global.NETSTATS_UID_TAG_ROTATE_AGE);
            MOVED_TO_GLOBAL.add(Settings.Global.NETWORK_PREFERENCE);
            MOVED_TO_GLOBAL.add(Settings.Global.NITZ_UPDATE_DIFF);
            MOVED_TO_GLOBAL.add(Settings.Global.NITZ_UPDATE_SPACING);
            MOVED_TO_GLOBAL.add(Settings.Global.NTP_SERVER);
            MOVED_TO_GLOBAL.add(Settings.Global.NTP_TIMEOUT);
            MOVED_TO_GLOBAL.add(Settings.Global.PDP_WATCHDOG_ERROR_POLL_COUNT);
            MOVED_TO_GLOBAL.add(Settings.Global.PDP_WATCHDOG_LONG_POLL_INTERVAL_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.PDP_WATCHDOG_MAX_PDP_RESET_FAIL_COUNT);
            MOVED_TO_GLOBAL.add(Settings.Global.PDP_WATCHDOG_POLL_INTERVAL_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.PDP_WATCHDOG_TRIGGER_PACKET_COUNT);
            MOVED_TO_GLOBAL.add(Settings.Global.SAMPLING_PROFILER_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SETUP_PREPAID_DETECTION_REDIR_HOST);
            MOVED_TO_GLOBAL.add(Settings.Global.SETUP_PREPAID_DETECTION_TARGET_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.TETHER_DUN_APN);
            MOVED_TO_GLOBAL.add(Settings.Global.TETHER_DUN_REQUIRED);
            MOVED_TO_GLOBAL.add(Settings.Global.TETHER_SUPPORTED);
            MOVED_TO_GLOBAL.add(Settings.Global.USB_MASS_STORAGE_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.USE_GOOGLE_MAIL);
            MOVED_TO_GLOBAL.add(Settings.Global.WEB_AUTOFILL_QUERY_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_COUNTRY_CODE);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_COUNTRY_CODE_USER);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_FRAMEWORK_SCAN_INTERVAL_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_FREQUENCY_BAND);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_IDLE_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_MAX_DHCP_RETRY_COUNT);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_NUM_OPEN_NETWORKS_KEPT);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_P2P_DEVICE_NAME);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_SAVED_STATE);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_SUPPLICANT_SCAN_INTERVAL_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_SUPPLICANT_SCAN_INTERVAL_WFD_CONNECTED_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_SUSPEND_OPTIMIZATIONS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_WATCHDOG_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.WIMAX_NETWORKS_AVAILABLE_NOTIFICATION_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.PACKAGE_VERIFIER_ENABLE);
            MOVED_TO_GLOBAL.add(Settings.Global.PACKAGE_VERIFIER_TIMEOUT);
            MOVED_TO_GLOBAL.add(Settings.Global.PACKAGE_VERIFIER_DEFAULT_RESPONSE);
            MOVED_TO_GLOBAL.add(Settings.Global.DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.GPRS_REGISTER_CHECK_PERIOD_MS);
            MOVED_TO_GLOBAL.add(Settings.Global.WTF_IS_FATAL);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_DISCHARGE_DURATION_THRESHOLD);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_DISCHARGE_THRESHOLD);
            MOVED_TO_GLOBAL.add(Settings.Global.SEND_ACTION_APP_ERROR);
            MOVED_TO_GLOBAL.add(Settings.Global.DROPBOX_AGE_SECONDS);
            MOVED_TO_GLOBAL.add(Settings.Global.DROPBOX_MAX_FILES);
            MOVED_TO_GLOBAL.add(Settings.Global.DROPBOX_QUOTA_KB);
            MOVED_TO_GLOBAL.add(Settings.Global.DROPBOX_QUOTA_PERCENT);
            MOVED_TO_GLOBAL.add(Settings.Global.DROPBOX_RESERVE_PERCENT);
            MOVED_TO_GLOBAL.add(Settings.Global.DROPBOX_TAG_PREFIX);
            MOVED_TO_GLOBAL.add(Settings.Global.ERROR_LOGCAT_PREFIX);
            MOVED_TO_GLOBAL.add(Settings.Global.SYS_FREE_STORAGE_LOG_INTERVAL);
            MOVED_TO_GLOBAL.add(Settings.Global.DISK_FREE_CHANGE_REPORTING_THRESHOLD);
            MOVED_TO_GLOBAL.add(Settings.Global.SYS_STORAGE_THRESHOLD_PERCENTAGE);
            MOVED_TO_GLOBAL.add(Settings.Global.SYS_STORAGE_THRESHOLD_MAX_BYTES);
            MOVED_TO_GLOBAL.add(Settings.Global.SYS_STORAGE_FULL_THRESHOLD_BYTES);
            MOVED_TO_GLOBAL.add(Settings.Global.SYNC_MAX_RETRY_DELAY_IN_SECONDS);
            MOVED_TO_GLOBAL.add(Settings.Global.CONNECTIVITY_CHANGE_DELAY);
            MOVED_TO_GLOBAL.add(Settings.Global.CAPTIVE_PORTAL_DETECTION_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.CAPTIVE_PORTAL_SERVER);
            MOVED_TO_GLOBAL.add(Settings.Global.NSD_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.SET_INSTALL_LOCATION);
            MOVED_TO_GLOBAL.add(Settings.Global.DEFAULT_INSTALL_LOCATION);
            MOVED_TO_GLOBAL.add(Settings.Global.INET_CONDITION_DEBOUNCE_UP_DELAY);
            MOVED_TO_GLOBAL.add(Settings.Global.INET_CONDITION_DEBOUNCE_DOWN_DELAY);
            MOVED_TO_GLOBAL.add(Settings.Global.READ_EXTERNAL_STORAGE_ENFORCED_DEFAULT);
            MOVED_TO_GLOBAL.add(Settings.Global.HTTP_PROXY);
            MOVED_TO_GLOBAL.add(Settings.Global.GLOBAL_HTTP_PROXY_HOST);
            MOVED_TO_GLOBAL.add(Settings.Global.GLOBAL_HTTP_PROXY_PORT);
            MOVED_TO_GLOBAL.add(Settings.Global.GLOBAL_HTTP_PROXY_EXCLUSION_LIST);
            MOVED_TO_GLOBAL.add(Settings.Global.SET_GLOBAL_HTTP_PROXY);
            MOVED_TO_GLOBAL.add(Settings.Global.DEFAULT_DNS_SERVER);
            MOVED_TO_GLOBAL.add(Settings.Global.PREFERRED_NETWORK_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_OPTION);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_NORMAL_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_POWER_SAVING_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_SCREEN_OFF);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_IGNORE_LOCKED);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_MODE_CHANGE_DELAY);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_BATTERY_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_BATTERY_LEVEL);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_BLUETOOTH_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_LOCATION_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_WIFI_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_DATA_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_SHOW_TOAST);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_NETWORK_INTERVAL_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_NOSIGNAL_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_SYNC_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_KILLALL_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_LED_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_LED_DISABLE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_VIBRATE_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_VIBRATE_DISABLE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_CPU_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_CPU_FREQ);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_CPU_FREQ_DEFAULT);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_BRIGHTNESS_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_BRIGHTNESS_LEVEL);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_START);
            MOVED_TO_GLOBAL.add(Settings.Global.BATTERY_SAVER_END);
        }

        /** @hide */
        public static void getMovedKeys(HashSet<String> outKeySet) {
            outKeySet.addAll(MOVED_TO_GLOBAL);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userHandle) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.Secure"
                        + " to android.provider.Settings.Global.");
                return Global.getStringForUser(resolver, name, userHandle);
            }

            if (MOVED_TO_LOCK_SETTINGS.contains(name)) {
                synchronized (Secure.class) {
                    if (sLockSettings == null) {
                        sLockSettings = ILockSettings.Stub.asInterface(
                                (IBinder) ServiceManager.getService("lock_settings"));
                        sIsSystemProcess = Process.myUid() == Process.SYSTEM_UID;
                    }
                }
                if (sLockSettings != null && !sIsSystemProcess) {
                    try {
                        return sLockSettings.getString(name, "0", userHandle);
                    } catch (RemoteException re) {
                        // Fall through
                    }
                }
            }

            return sNameValueCache.getStringForUser(resolver, name, userHandle);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
                int userHandle) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global");
                return Global.putStringForUser(resolver, name, value, userHandle);
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userHandle);
        }

        /**
         * Construct the content URI for a particular name/value pair,
         * useful for monitoring changes with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI, or null if not present
         */
        public static Uri getUriFor(String name) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.Secure"
                        + " to android.provider.Settings.Global, returning global URI.");
                return Global.getUriFor(Global.CONTENT_URI, name);
            }
            return getUriFor(CONTENT_URI, name);
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userHandle) {
            if (LOCATION_MODE.equals(name)) {
                // HACK ALERT: temporary hack to work around b/10491283.
                // TODO: once b/10491283 fixed, remove this hack
                return getLocationModeForUser(cr, userHandle);
            }
            String v = getStringForUser(cr, name, userHandle);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            if (LOCATION_MODE.equals(name)) {
                // HACK ALERT: temporary hack to work around b/10491283.
                // TODO: once b/10491283 fixed, remove this hack
                return getLocationModeForUser(cr, userHandle);
            }
            String v = getStringForUser(cr, name, userHandle);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userHandle) {
            if (LOCATION_MODE.equals(name)) {
                // HACK ALERT: temporary hack to work around b/10491283.
                // TODO: once b/10491283 fixed, remove this hack
                return setLocationModeForUser(cr, value, userHandle);
            }
            return putStringForUser(cr, name, Integer.toString(value), userHandle);
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userHandle) {
            String valString = getStringForUser(cr, name, userHandle);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String valString = getStringForUser(cr, name, userHandle);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a secure settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userHandle) {
            return putStringForUser(cr, name, Long.toString(value), userHandle);
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String v = getStringForUser(cr, name, userHandle);
            if (v == null) {
                throw new SettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userHandle) {
            return putStringForUser(cr, name, Float.toString(value), userHandle);
        }

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DEVELOPMENT_SETTINGS_ENABLED}
         * instead
         */
        @Deprecated
        public static final String DEVELOPMENT_SETTINGS_ENABLED =
                Global.DEVELOPMENT_SETTINGS_ENABLED;

        /**
         * When the user has enable the option to have a "bug report" command
         * in the power menu.
         * @deprecated Use {@link android.provider.Settings.Global#BUGREPORT_IN_POWER_MENU} instead
         * @hide
         */
        @Deprecated
        public static final String BUGREPORT_IN_POWER_MENU = "bugreport_in_power_menu";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#ADB_ENABLED} instead
         */
        @Deprecated
        public static final String ADB_ENABLED = Global.ADB_ENABLED;

        /**
         * The TCP/IP port to run ADB on, or -1 for USB
         * @hide
         */
        public static final String ADB_PORT = "adb_port";

        /**
         * Whether to display the ADB notification.
         * @hide
         */
        public static final String ADB_NOTIFY = "adb_notify";

        /**
         * Whether to reboot the device if an unknown ADB host is detected while screen is locked
         * @hide
         */
        public static final String ADB_PARANOID = "adb_paranoid";

        /**
         * The hostname for this device
         * @hide
         */
        public static final String DEVICE_HOSTNAME = "device_hostname";

        /**
         * Setting to allow mock locations and location provider status to be injected into the
         * LocationManager service for testing purposes during application development.  These
         * locations and status values  override actual location and status information generated
         * by network, gps, or other location providers.
         */
        public static final String ALLOW_MOCK_LOCATION = "mock_location";

        /**
         * Setting to allow the use of com.android.internal.telephony.SMSDispatcher#MockSmsReceiver
         * to simulate the reception of SMS for testing purposes during application development.
         * @hide
         */
         public static final String ALLOW_MOCK_SMS = "mock_sms";

        /**
         * A 64-bit number (as a hex string) that is randomly
         * generated on the device's first boot and should remain
         * constant for the lifetime of the device.  (The value may
         * change if a factory reset is performed on the device.)
         */
        public static final String ANDROID_ID = "android_id";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#BLUETOOTH_ON} instead
         */
        @Deprecated
        public static final String BLUETOOTH_ON = Global.BLUETOOTH_ON;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DATA_ROAMING} instead
         */
        @Deprecated
        public static final String DATA_ROAMING = Global.DATA_ROAMING;

        /**
         * Setting to record the input method used by default, holding the ID
         * of the desired method.
         */
        public static final String DEFAULT_INPUT_METHOD = "default_input_method";

        /**
         * Setting to record the input method subtype used by default, holding the ID
         * of the desired method.
         */
        public static final String SELECTED_INPUT_METHOD_SUBTYPE =
                "selected_input_method_subtype";

        /**
         * Setting to record the history of input method subtype, holding the pair of ID of IME
         * and its last used subtype.
         * @hide
         */
        public static final String INPUT_METHODS_SUBTYPE_HISTORY =
                "input_methods_subtype_history";

        /**
         * Setting to record the visibility of input method selector
         */
        public static final String INPUT_METHOD_SELECTOR_VISIBILITY =
                "input_method_selector_visibility";

        /**
         * bluetooth HCI snoop log configuration
         * @hide
         */
        public static final String BLUETOOTH_HCI_LOG =
                "bluetooth_hci_log";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DEVICE_PROVISIONED} instead
         */
        @Deprecated
        public static final String DEVICE_PROVISIONED = Global.DEVICE_PROVISIONED;

        /**
         * Whether the current user has been set up via setup wizard (0 = false, 1 = true)
         * @hide
         */
        public static final String USER_SETUP_COMPLETE = "user_setup_complete";

        /**
         * List of input methods that are currently enabled.  This is a string
         * containing the IDs of all enabled input methods, each ID separated
         * by ':'.
         */
        public static final String ENABLED_INPUT_METHODS = "enabled_input_methods";

        /**
         * List of system input methods that are currently disabled.  This is a string
         * containing the IDs of all disabled input methods, each ID separated
         * by ':'.
         * @hide
         */
        public static final String DISABLED_SYSTEM_INPUT_METHODS = "disabled_system_input_methods";

        /**
         * Host name and port for global http proxy. Uses ':' seperator for
         * between host and port.
         *
         * @deprecated Use {@link Global#HTTP_PROXY}
         */
        @Deprecated
        public static final String HTTP_PROXY = Global.HTTP_PROXY;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#INSTALL_NON_MARKET_APPS} instead
         */
        @Deprecated
        public static final String INSTALL_NON_MARKET_APPS = Global.INSTALL_NON_MARKET_APPS;

        /**
         * Comma-separated list of location providers that activities may access.
         *
         * @deprecated use {@link #LOCATION_MODE}
         */
        @Deprecated
        public static final String LOCATION_PROVIDERS_ALLOWED = "location_providers_allowed";

        /**
         * The degree of location access enabled by the user.
         * <p/>
         * When used with {@link #putInt(ContentResolver, String, int)}, must be one of {@link
         * #LOCATION_MODE_HIGH_ACCURACY}, {@link #LOCATION_MODE_SENSORS_ONLY}, {@link
         * #LOCATION_MODE_BATTERY_SAVING}, or {@link #LOCATION_MODE_OFF}. When used with {@link
         * #getInt(ContentResolver, String)}, the caller must gracefully handle additional location
         * modes that might be added in the future.
         */
        public static final String LOCATION_MODE = "location_mode";

        /**
         * The last degree of location access enabled by the user.
         * <p/>
         * Must be one of {@link
         * #LOCATION_MODE_HIGH_ACCURACY}, {@link #LOCATION_MODE_SENSORS_ONLY}, {@link
         * #LOCATION_MODE_BATTERY_SAVING}.
         *
         * @hide
         */
        public static final String LOCATION_LAST_MODE = "location_last_mode";

        /**
         * Location access disabled.
         */
        public static final int LOCATION_MODE_OFF = 0;
        /**
         * Network Location Provider disabled, but GPS and other sensors enabled.
         */
        public static final int LOCATION_MODE_SENSORS_ONLY = 1;
        /**
         * Reduced power usage, such as limiting the number of GPS updates per hour. Requests
         * with {@link android.location.Criteria#POWER_HIGH} may be downgraded to
         * {@link android.location.Criteria#POWER_MEDIUM}.
         */
        public static final int LOCATION_MODE_BATTERY_SAVING = 2;
        /**
         * Best-effort location computation allowed.
         */
        public static final int LOCATION_MODE_HIGH_ACCURACY = 3;

        /**
         * A flag containing settings used for biometric weak
         * @hide
         */
        public static final String LOCK_BIOMETRIC_WEAK_FLAGS =
                "lock_biometric_weak_flags";

        /**
         * Whether autolock is enabled (0 = false, 1 = true)
         */
        public static final String LOCK_PATTERN_ENABLED = "lock_pattern_autolock";

        /**
         * Whether lock pattern is visible as user enters (0 = false, 1 = true)
         */
        public static final String LOCK_PATTERN_VISIBLE = "lock_pattern_visible_pattern";

        /**
         * Whether the NumKeyPad will change the orders of numbers
         * in a PIN locked lockscreen
         * 0 = off | 1 = always | 2 = only on request
         * @hide
          */

        public static final String LOCK_NUMPAD_RANDOM = "lock_numpad_random";

        /**
         * Colorize custom lock icon true/false
         * @hide
         */
        public static final String LOCKSCREEN_COLORIZE_LOCK = "lockscreen_colorize_lock";

        /**
         * Lockscreen custom lock icon
         * @hide
         */
        public static final String LOCKSCREEN_LOCK_ICON = "lockscreen_lock_icon";

        /**
         * Lockscreen lock color (handle and expanded locks)
         * @hide
         */
        public static final String LOCKSCREEN_LOCK_COLOR = "lockscreen_lock_color";

        /**
         * Lockscreen dots color (glowpad dots)
         * @hide
         */
        public static final String LOCKSCREEN_DOTS_COLOR = "lockscreen_dots_color";

        /**
         * Lockscreen frame color (widgets/security frame color)
         * @hide
         */
        public static final String LOCKSCREEN_FRAME_COLOR = "lockscreen_frame_color";

        /**
         * Lockscreen widget add, glowpad ring, text, failed pattern ring colors
         * @hide
         */
        public static final String LOCKSCREEN_MISC_COLOR = "lockscreen_misc_color";

        /**
         * Lockscreen targets and pattern ring colors
         * @hide
         */
        public static final String LOCKSCREEN_TARGETS_COLOR = "lockscreen_targets_color";

        /**
         *Whether lock before unlock is enabled or disabled
         * @hide
         */
        public static final String LOCK_BEFORE_UNLOCK = "lock_before_unlock";

        /**
         * Shake events for x,y,z cords
         * @hide
         */
        public static final String[] LOCK_SHAKE_EVENTS = new String[] {
            "lock_shake_events_x",
            "lock_shake_events_y",
            "lock_shake_events_z"
        };

        /**
         *Whether shaking the device enables a secure screen
         * @hide
         */
        public static final String LOCK_SHAKE_TEMP_SECURE = "lock_shake_temp_secure";

        /**
         *When LOCK_SHAKE_TEMP_SECURE is enabled, the time
         *before a secure lock will auto-engage in milliseconds
         * @hide
         */
        public static final String LOCK_SHAKE_SECURE_TIMER = "lock_shake_secure_timer";

        /**
         *Whether the device will unlock itself or not upon completeion
         *of the insecure lock challenge
         * @hide
         */
        public static final String LOCK_TEMP_SECURE_MODE = "lock_temp_secure_mode";

        /**
         * Determines the width and height of the LockPatternView widget
         * @hide
         */
        public static final String LOCK_PATTERN_SIZE = "lock_pattern_size";

        /**
         * Whether lock pattern will show dots (0 = false, 1 = true)
         * @hide
         */
        public static final String LOCK_DOTS_VISIBLE = "lock_pattern_dotsvisible";

        /**
         * Whether lockscreen error pattern is visible (0 = false, 1 = true)
         * @hide
         */
        public static final String LOCK_SHOW_ERROR_PATH = "lock_pattern_show_error_path";

        /**
         * Whether lock pattern will vibrate as user enters (0 = false, 1 =
         * true)
         *
         * @deprecated Starting in {@link VERSION_CODES#JELLY_BEAN_MR1} the
         *             lockscreen uses
         *             {@link Settings.System#HAPTIC_FEEDBACK_ENABLED}.
         */
        @Deprecated
        public static final String
                LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED = "lock_pattern_tactile_feedback_enabled";

        /**
         * Whether autolock is enabled (0 = false, 1 = true)
         * @hide
         */
        public static final String LOCK_GESTURE_ENABLED = "lock_gesture_autolock";

        /**
         * Whether lock gesture is visible as user enters (0 = false, 1 = true)
         * @hide
         */
        public static final String LOCK_GESTURE_VISIBLE = "lock_gesture_visible_pattern";

        /**
         * This preference allows the device to be locked given time after screen goes off,
         * subject to current DeviceAdmin policy limits.
         * @hide
         */
        public static final String LOCK_SCREEN_LOCK_AFTER_TIMEOUT = "lock_screen_lock_after_timeout";


        /**
         * This preference contains the string that shows for owner info on LockScreen.
         * @hide
         * @deprecated
         */
        public static final String LOCK_SCREEN_OWNER_INFO = "lock_screen_owner_info";

        /**
         * Allow all (non keyguard specific) widgets to be added to the lockscreen
         * @hide
         */
        public static final String ALLOW_ALL_LOCKSCREEN_WIDGETS =
            "allow_all_lockscreen_widgets";

        /**
         * Ids of the user-selected appwidgets on the lockscreen (comma-delimited).
         * @hide
         */
        public static final String LOCK_SCREEN_APPWIDGET_IDS =
            "lock_screen_appwidget_ids";

        /**
         * Id of the appwidget shown on the lock screen when appwidgets are disabled.
         * @hide
         */
        public static final String LOCK_SCREEN_FALLBACK_APPWIDGET_ID =
            "lock_screen_fallback_appwidget_id";

        /**
         * Index of the lockscreen appwidget to restore, -1 if none.
         * @hide
         */
        public static final String LOCK_SCREEN_STICKY_APPWIDGET =
            "lock_screen_sticky_appwidget";

        /**
         * This preference enables showing the owner info on LockScreen.
         * @hide
         * @deprecated
         */
        public static final String LOCK_SCREEN_OWNER_INFO_ENABLED =
            "lock_screen_owner_info_enabled";

        /**
         * Chamber on / off (custom setting shortcuts)
         * @hide
         */
        public static final String CHAMBER_OF_SECRETS = "chamber_of_secrets";

        /**
         * The Logging ID (a unique 64-bit value) as a hex string.
         * Used as a pseudonymous identifier for logging.
         * @deprecated This identifier is poorly initialized and has
         * many collisions.  It should not be used.
         */
        @Deprecated
        public static final String LOGGING_ID = "logging_id";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#NETWORK_PREFERENCE} instead
         */
        @Deprecated
        public static final String NETWORK_PREFERENCE = Global.NETWORK_PREFERENCE;

        /**
         * No longer supported.
         */
        public static final String PARENTAL_CONTROL_ENABLED = "parental_control_enabled";

        /**
         * No longer supported.
         */
        public static final String PARENTAL_CONTROL_LAST_UPDATE = "parental_control_last_update";

        /**
         * No longer supported.
         */
        public static final String PARENTAL_CONTROL_REDIRECT_URL = "parental_control_redirect_url";

        /**
         * Settings classname to launch when Settings is clicked from All
         * Applications.  Needed because of user testing between the old
         * and new Settings apps.
         */
        // TODO: 881807
        public static final String SETTINGS_CLASSNAME = "settings_classname";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#USB_MASS_STORAGE_ENABLED} instead
         */
        @Deprecated
        public static final String USB_MASS_STORAGE_ENABLED = Global.USB_MASS_STORAGE_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#USE_GOOGLE_MAIL} instead
         */
        @Deprecated
        public static final String USE_GOOGLE_MAIL = Global.USE_GOOGLE_MAIL;

        /**
         * If accessibility is enabled.
         */
        public static final String ACCESSIBILITY_ENABLED = "accessibility_enabled";

        /**
         * If touch exploration is enabled.
         */
        public static final String TOUCH_EXPLORATION_ENABLED = "touch_exploration_enabled";

        /**
         * List of the enabled accessibility providers.
         */
        public static final String ENABLED_ACCESSIBILITY_SERVICES =
            "enabled_accessibility_services";

        /**
         * List of the accessibility services to which the user has granted
         * permission to put the device into touch exploration mode.
         *
         * @hide
         */
        public static final String TOUCH_EXPLORATION_GRANTED_ACCESSIBILITY_SERVICES =
            "touch_exploration_granted_accessibility_services";

        /**
         * Whether to speak passwords while in accessibility mode.
         */
        public static final String ACCESSIBILITY_SPEAK_PASSWORD = "speak_password";

        /**
         * If injection of accessibility enhancing JavaScript screen-reader
         * is enabled.
         * <p>
         *   Note: The JavaScript based screen-reader is served by the
         *   Google infrastructure and enable users with disabilities to
         *   efficiently navigate in and explore web content.
         * </p>
         * <p>
         *   This property represents a boolean value.
         * </p>
         * @hide
         */
        public static final String ACCESSIBILITY_SCRIPT_INJECTION =
            "accessibility_script_injection";

        /**
         * The URL for the injected JavaScript based screen-reader used
         * for providing accessibility of content in WebView.
         * <p>
         *   Note: The JavaScript based screen-reader is served by the
         *   Google infrastructure and enable users with disabilities to
         *   efficiently navigate in and explore web content.
         * </p>
         * <p>
         *   This property represents a string value.
         * </p>
         * @hide
         */
        public static final String ACCESSIBILITY_SCREEN_READER_URL =
            "accessibility_script_injection_url";

        /**
         * Key bindings for navigation in built-in accessibility support for web content.
         * <p>
         *   Note: These key bindings are for the built-in accessibility navigation for
         *   web content which is used as a fall back solution if JavaScript in a WebView
         *   is not enabled or the user has not opted-in script injection from Google.
         * </p>
         * <p>
         *   The bindings are separated by semi-colon. A binding is a mapping from
         *   a key to a sequence of actions (for more details look at
         *   android.webkit.AccessibilityInjector). A key is represented as the hexademical
         *   string representation of an integer obtained from a meta state (optional) shifted
         *   sixteen times left and bitwise ored with a key code. An action is represented
         *   as a hexademical string representation of an integer where the first two digits
         *   are navigation action index, the second, the third, and the fourth digit pairs
         *   represent the action arguments. The separate actions in a binding are colon
         *   separated. The key and the action sequence it maps to are separated by equals.
         * </p>
         * <p>
         *   For example, the binding below maps the DPAD right button to traverse the
         *   current navigation axis once without firing an accessibility event and to
         *   perform the same traversal again but to fire an event:
         *   <code>
         *     0x16=0x01000100:0x01000101;
         *   </code>
         * </p>
         * <p>
         *   The goal of this binding is to enable dynamic rebinding of keys to
         *   navigation actions for web content without requiring a framework change.
         * </p>
         * <p>
         *   This property represents a string value.
         * </p>
         * @hide
         */
        public static final String ACCESSIBILITY_WEB_CONTENT_KEY_BINDINGS =
            "accessibility_web_content_key_bindings";

        /**
         * Setting that specifies whether the display magnification is enabled.
         * Display magnifications allows the user to zoom in the display content
         * and is targeted to low vision users. The current magnification scale
         * is controlled by {@link #ACCESSIBILITY_DISPLAY_MAGNIFICATION_SCALE}.
         *
         * @hide
         */
        public static final String ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED =
                "accessibility_display_magnification_enabled";

        /**
         * Setting that specifies what the display magnification scale is.
         * Display magnifications allows the user to zoom in the display
         * content and is targeted to low vision users. Whether a display
         * magnification is performed is controlled by
         * {@link #ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED}
         *
         * @hide
         */
        public static final String ACCESSIBILITY_DISPLAY_MAGNIFICATION_SCALE =
                "accessibility_display_magnification_scale";

        /**
         * Setting that specifies whether the display magnification should be
         * automatically updated. If this fearture is enabled the system will
         * exit magnification mode or pan the viewport when a context change
         * occurs. For example, on staring a new activity or rotating the screen,
         * the system may zoom out so the user can see the new context he is in.
         * Another example is on showing a window that is not visible in the
         * magnified viewport the system may pan the viewport to make the window
         * the has popped up so the user knows that the context has changed.
         * Whether a screen magnification is performed is controlled by
         * {@link #ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED}
         *
         * @hide
         */
        public static final String ACCESSIBILITY_DISPLAY_MAGNIFICATION_AUTO_UPDATE =
                "accessibility_display_magnification_auto_update";

        /**
         * Setting that specifies whether timed text (captions) should be
         * displayed in video content. Text display properties are controlled by
         * the following settings:
         * <ul>
         * <li>{@link #ACCESSIBILITY_CAPTIONING_LOCALE}
         * <li>{@link #ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR}
         * <li>{@link #ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR}
         * <li>{@link #ACCESSIBILITY_CAPTIONING_EDGE_COLOR}
         * <li>{@link #ACCESSIBILITY_CAPTIONING_EDGE_TYPE}
         * <li>{@link #ACCESSIBILITY_CAPTIONING_TYPEFACE}
         * <li>{@link #ACCESSIBILITY_CAPTIONING_FONT_SCALE}
         * </ul>
         *
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_ENABLED =
                "accessibility_captioning_enabled";

        /**
         * Setting that specifies the language for captions as a locale string,
         * e.g. en_US.
         *
         * @see java.util.Locale#toString
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_LOCALE =
                "accessibility_captioning_locale";

        /**
         * Integer property that specifies the preset style for captions, one
         * of:
         * <ul>
         * <li>{@link android.view.accessibility.CaptioningManager.CaptionStyle#PRESET_CUSTOM}
         * <li>a valid index of {@link android.view.accessibility.CaptioningManager.CaptionStyle#PRESETS}
         * </ul>
         *
         * @see java.util.Locale#toString
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_PRESET =
                "accessibility_captioning_preset";

        /**
         * Integer property that specifes the background color for captions as a
         * packed 32-bit color.
         *
         * @see android.graphics.Color#argb
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR =
                "accessibility_captioning_background_color";

        /**
         * Integer property that specifes the foreground color for captions as a
         * packed 32-bit color.
         *
         * @see android.graphics.Color#argb
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR =
                "accessibility_captioning_foreground_color";

        /**
         * Integer property that specifes the edge type for captions, one of:
         * <ul>
         * <li>{@link android.view.accessibility.CaptioningManager.CaptionStyle#EDGE_TYPE_NONE}
         * <li>{@link android.view.accessibility.CaptioningManager.CaptionStyle#EDGE_TYPE_OUTLINE}
         * <li>{@link android.view.accessibility.CaptioningManager.CaptionStyle#EDGE_TYPE_DROP_SHADOW}
         * </ul>
         *
         * @see #ACCESSIBILITY_CAPTIONING_EDGE_COLOR
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_EDGE_TYPE =
                "accessibility_captioning_edge_type";

        /**
         * Integer property that specifes the edge color for captions as a
         * packed 32-bit color.
         *
         * @see #ACCESSIBILITY_CAPTIONING_EDGE_TYPE
         * @see android.graphics.Color#argb
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_EDGE_COLOR =
                "accessibility_captioning_edge_color";

        /**
         * String property that specifies the typeface for captions, one of:
         * <ul>
         * <li>DEFAULT
         * <li>MONOSPACE
         * <li>SANS_SERIF
         * <li>SERIF
         * </ul>
         *
         * @see android.graphics.Typeface
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_TYPEFACE =
                "accessibility_captioning_typeface";

        /**
         * Floating point property that specifies font scaling for captions.
         *
         * @hide
         */
        public static final String ACCESSIBILITY_CAPTIONING_FONT_SCALE =
                "accessibility_captioning_font_scale";

        /**
         * The timout for considering a press to be a long press in milliseconds.
         * @hide
         */
        public static final String LONG_PRESS_TIMEOUT = "long_press_timeout";

        /**
         * List of the enabled print services.
         * @hide
         */
        public static final String ENABLED_PRINT_SERVICES =
            "enabled_print_services";

        /**
         * List of the system print services we enabled on first boot. On
         * first boot we enable all system, i.e. bundled print services,
         * once, so they work out-of-the-box.
         * @hide
         */
        public static final String ENABLED_ON_FIRST_BOOT_SYSTEM_PRINT_SERVICES =
            "enabled_on_first_boot_system_print_services";

        /**
         * Setting to always use the default text-to-speech settings regardless
         * of the application settings.
         * 1 = override application settings,
         * 0 = use application settings (if specified).
         *
         * @deprecated  The value of this setting is no longer respected by
         * the framework text to speech APIs as of the Ice Cream Sandwich release.
         */
        @Deprecated
        public static final String TTS_USE_DEFAULTS = "tts_use_defaults";

        /**
         * Default text-to-speech engine speech rate. 100 = 1x
         */
        public static final String TTS_DEFAULT_RATE = "tts_default_rate";

        /**
         * Default text-to-speech engine pitch. 100 = 1x
         */
        public static final String TTS_DEFAULT_PITCH = "tts_default_pitch";

        /**
         * Default text-to-speech engine.
         */
        public static final String TTS_DEFAULT_SYNTH = "tts_default_synth";

        /**
         * Default text-to-speech language.
         *
         * @deprecated this setting is no longer in use, as of the Ice Cream
         * Sandwich release. Apps should never need to read this setting directly,
         * instead can query the TextToSpeech framework classes for the default
         * locale. {@link TextToSpeech#getLanguage()}.
         */
        @Deprecated
        public static final String TTS_DEFAULT_LANG = "tts_default_lang";

        /**
         * Default text-to-speech country.
         *
         * @deprecated this setting is no longer in use, as of the Ice Cream
         * Sandwich release. Apps should never need to read this setting directly,
         * instead can query the TextToSpeech framework classes for the default
         * locale. {@link TextToSpeech#getLanguage()}.
         */
        @Deprecated
        public static final String TTS_DEFAULT_COUNTRY = "tts_default_country";

        /**
         * Default text-to-speech locale variant.
         *
         * @deprecated this setting is no longer in use, as of the Ice Cream
         * Sandwich release. Apps should never need to read this setting directly,
         * instead can query the TextToSpeech framework classes for the
         * locale that is in use {@link TextToSpeech#getLanguage()}.
         */
        @Deprecated
        public static final String TTS_DEFAULT_VARIANT = "tts_default_variant";

        /**
         * Stores the default tts locales on a per engine basis. Stored as
         * a comma seperated list of values, each value being of the form
         * {@code engine_name:locale} for example,
         * {@code com.foo.ttsengine:eng-USA,com.bar.ttsengine:esp-ESP}. This
         * supersedes {@link #TTS_DEFAULT_LANG}, {@link #TTS_DEFAULT_COUNTRY} and
         * {@link #TTS_DEFAULT_VARIANT}. Apps should never need to read this
         * setting directly, and can query the TextToSpeech framework classes
         * for the locale that is in use.
         *
         * @hide
         */
        public static final String TTS_DEFAULT_LOCALE = "tts_default_locale";

        /**
         * Space delimited list of plugin packages that are enabled.
         */
        public static final String TTS_ENABLED_PLUGINS = "tts_enabled_plugins";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON}
         * instead.
         */
        @Deprecated
        public static final String WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
                Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY}
         * instead.
         */
        @Deprecated
        public static final String WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY =
                Global.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_NUM_OPEN_NETWORKS_KEPT}
         * instead.
         */
        @Deprecated
        public static final String WIFI_NUM_OPEN_NETWORKS_KEPT =
                Global.WIFI_NUM_OPEN_NETWORKS_KEPT;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_ON}
         * instead.
         */
        @Deprecated
        public static final String WIFI_ON = Global.WIFI_ON;

        /**
         * The acceptable packet loss percentage (range 0 - 100) before trying
         * another AP on the same network.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE =
                "wifi_watchdog_acceptable_packet_loss_percentage";

        /**
         * The number of access points required for a network in order for the
         * watchdog to monitor it.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_AP_COUNT = "wifi_watchdog_ap_count";

        /**
         * The delay between background checks.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS =
                "wifi_watchdog_background_check_delay_ms";

        /**
         * Whether the Wi-Fi watchdog is enabled for background checking even
         * after it thinks the user has connected to a good access point.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED =
                "wifi_watchdog_background_check_enabled";

        /**
         * The timeout for a background ping
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS =
                "wifi_watchdog_background_check_timeout_ms";

        /**
         * The number of initial pings to perform that *may* be ignored if they
         * fail. Again, if these fail, they will *not* be used in packet loss
         * calculation. For example, one network always seemed to time out for
         * the first couple pings, so this is set to 3 by default.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT =
            "wifi_watchdog_initial_ignored_ping_count";

        /**
         * The maximum number of access points (per network) to attempt to test.
         * If this number is reached, the watchdog will no longer monitor the
         * initial connection state for the network. This is a safeguard for
         * networks containing multiple APs whose DNS does not respond to pings.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_MAX_AP_CHECKS = "wifi_watchdog_max_ap_checks";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_WATCHDOG_ON} instead
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_ON = "wifi_watchdog_on";

        /**
         * A comma-separated list of SSIDs for which the Wi-Fi watchdog should be enabled.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_WATCH_LIST = "wifi_watchdog_watch_list";

        /**
         * The number of pings to test if an access point is a good connection.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_PING_COUNT = "wifi_watchdog_ping_count";

        /**
         * The delay between pings.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_PING_DELAY_MS = "wifi_watchdog_ping_delay_ms";

        /**
         * The timeout per ping.
         * @deprecated This setting is not used.
         */
        @Deprecated
        public static final String WIFI_WATCHDOG_PING_TIMEOUT_MS = "wifi_watchdog_ping_timeout_ms";

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_MAX_DHCP_RETRY_COUNT} instead
         */
        @Deprecated
        public static final String WIFI_MAX_DHCP_RETRY_COUNT = Global.WIFI_MAX_DHCP_RETRY_COUNT;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS} instead
         */
        @Deprecated
        public static final String WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS =
                Global.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS;

        /**
         * Whether the Wimax should be on.  Only the WiMAX service should touch this.
         * @hide
         */
        public static final String WIMAX_ON = "wimax_on";

        /**
         * Whether background data usage is allowed.
         *
         * @deprecated As of {@link VERSION_CODES#ICE_CREAM_SANDWICH},
         *             availability of background data depends on several
         *             combined factors. When background data is unavailable,
         *             {@link ConnectivityManager#getActiveNetworkInfo()} will
         *             now appear disconnected.
         */
        @Deprecated
        public static final String BACKGROUND_DATA = "background_data";

        /**
         * Origins for which browsers should allow geolocation by default.
         * The value is a space-separated list of origins.
         */
        public static final String ALLOWED_GEOLOCATION_ORIGINS
                = "allowed_geolocation_origins";

        /**
         * The preferred TTY mode     0 = TTy Off, CDMA default
         *                            1 = TTY Full
         *                            2 = TTY HCO
         *                            3 = TTY VCO
         * @hide
         */
        public static final String PREFERRED_TTY_MODE =
                "preferred_tty_mode";

        /**
         * Whether the enhanced voice privacy mode is enabled.
         * 0 = normal voice privacy
         * 1 = enhanced voice privacy
         * @hide
         */
        public static final String ENHANCED_VOICE_PRIVACY_ENABLED = "enhanced_voice_privacy_enabled";

        /**
         * Whether the TTY mode mode is enabled.
         * 0 = disabled
         * 1 = enabled
         * @hide
         */
        public static final String TTY_MODE_ENABLED = "tty_mode_enabled";

        /**
         * Controls whether settings backup is enabled.
         * Type: int ( 0 = disabled, 1 = enabled )
         * @hide
         */
        public static final String BACKUP_ENABLED = "backup_enabled";

        /**
         * Controls whether application data is automatically restored from backup
         * at install time.
         * Type: int ( 0 = disabled, 1 = enabled )
         * @hide
         */
        public static final String BACKUP_AUTO_RESTORE = "backup_auto_restore";

        /**
         * Indicates whether settings backup has been fully provisioned.
         * Type: int ( 0 = unprovisioned, 1 = fully provisioned )
         * @hide
         */
        public static final String BACKUP_PROVISIONED = "backup_provisioned";

        /**
         * Component of the transport to use for backup/restore.
         * @hide
         */
        public static final String BACKUP_TRANSPORT = "backup_transport";

        /**
         * Version for which the setup wizard was last shown.  Bumped for
         * each release when there is new setup information to show.
         * @hide
         */
        public static final String LAST_SETUP_SHOWN = "last_setup_shown";

        /**
         * The interval in milliseconds after which Wi-Fi is considered idle.
         * When idle, it is possible for the device to be switched from Wi-Fi to
         * the mobile data network.
         * @hide
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_IDLE_MS}
         * instead.
         */
        @Deprecated
        public static final String WIFI_IDLE_MS = Global.WIFI_IDLE_MS;

        /**
         * The global search provider chosen by the user (if multiple global
         * search providers are installed). This will be the provider returned
         * by {@link SearchManager#getGlobalSearchActivity()} if it's still
         * installed. This setting is stored as a flattened component name as
         * per {@link ComponentName#flattenToString()}.
         *
         * @hide
         */
        public static final String SEARCH_GLOBAL_SEARCH_ACTIVITY =
                "search_global_search_activity";

        /**
         * The number of promoted sources in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_NUM_PROMOTED_SOURCES = "search_num_promoted_sources";
        /**
         * The maximum number of suggestions returned by GlobalSearch.
         * @hide
         */
        public static final String SEARCH_MAX_RESULTS_TO_DISPLAY = "search_max_results_to_display";
        /**
         * The number of suggestions GlobalSearch will ask each non-web search source for.
         * @hide
         */
        public static final String SEARCH_MAX_RESULTS_PER_SOURCE = "search_max_results_per_source";
        /**
         * The number of suggestions the GlobalSearch will ask the web search source for.
         * @hide
         */
        public static final String SEARCH_WEB_RESULTS_OVERRIDE_LIMIT =
                "search_web_results_override_limit";
        /**
         * The number of milliseconds that GlobalSearch will wait for suggestions from
         * promoted sources before continuing with all other sources.
         * @hide
         */
        public static final String SEARCH_PROMOTED_SOURCE_DEADLINE_MILLIS =
                "search_promoted_source_deadline_millis";
        /**
         * The number of milliseconds before GlobalSearch aborts search suggesiton queries.
         * @hide
         */
        public static final String SEARCH_SOURCE_TIMEOUT_MILLIS = "search_source_timeout_millis";
        /**
         * The maximum number of milliseconds that GlobalSearch shows the previous results
         * after receiving a new query.
         * @hide
         */
        public static final String SEARCH_PREFILL_MILLIS = "search_prefill_millis";
        /**
         * The maximum age of log data used for shortcuts in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_MAX_STAT_AGE_MILLIS = "search_max_stat_age_millis";
        /**
         * The maximum age of log data used for source ranking in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_MAX_SOURCE_EVENT_AGE_MILLIS =
                "search_max_source_event_age_millis";
        /**
         * The minimum number of impressions needed to rank a source in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_MIN_IMPRESSIONS_FOR_SOURCE_RANKING =
                "search_min_impressions_for_source_ranking";
        /**
         * The minimum number of clicks needed to rank a source in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_MIN_CLICKS_FOR_SOURCE_RANKING =
                "search_min_clicks_for_source_ranking";
        /**
         * The maximum number of shortcuts shown by GlobalSearch.
         * @hide
         */
        public static final String SEARCH_MAX_SHORTCUTS_RETURNED = "search_max_shortcuts_returned";
        /**
         * The size of the core thread pool for suggestion queries in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_QUERY_THREAD_CORE_POOL_SIZE =
                "search_query_thread_core_pool_size";
        /**
         * The maximum size of the thread pool for suggestion queries in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_QUERY_THREAD_MAX_POOL_SIZE =
                "search_query_thread_max_pool_size";
        /**
         * The size of the core thread pool for shortcut refreshing in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_SHORTCUT_REFRESH_CORE_POOL_SIZE =
                "search_shortcut_refresh_core_pool_size";
        /**
         * The maximum size of the thread pool for shortcut refreshing in GlobalSearch.
         * @hide
         */
        public static final String SEARCH_SHORTCUT_REFRESH_MAX_POOL_SIZE =
                "search_shortcut_refresh_max_pool_size";
        /**
         * The maximun time that excess threads in the GlobalSeach thread pools will
         * wait before terminating.
         * @hide
         */
        public static final String SEARCH_THREAD_KEEPALIVE_SECONDS =
                "search_thread_keepalive_seconds";
        /**
         * The maximum number of concurrent suggestion queries to each source.
         * @hide
         */
        public static final String SEARCH_PER_SOURCE_CONCURRENT_QUERY_LIMIT =
                "search_per_source_concurrent_query_limit";

        /**
         * Whether or not alert sounds are played on MountService events. (0 = false, 1 = true)
         * @hide
         */
        public static final String MOUNT_PLAY_NOTIFICATION_SND = "mount_play_not_snd";

        /**
         * Whether or not UMS auto-starts on UMS host detection. (0 = false, 1 = true)
         * @hide
         */
        public static final String MOUNT_UMS_AUTOSTART = "mount_ums_autostart";

        /**
         * Whether or not a notification is displayed on UMS host detection. (0 = false, 1 = true)
         * @hide
         */
        public static final String MOUNT_UMS_PROMPT = "mount_ums_prompt";

        /**
         * Whether or not a notification is displayed while UMS is enabled. (0 = false, 1 = true)
         * @hide
         */
        public static final String MOUNT_UMS_NOTIFY_ENABLED = "mount_ums_notify_enabled";

        /**
         * If nonzero, ANRs in invisible background processes bring up a dialog.
         * Otherwise, the process will be silently killed.
         * @hide
         */
        public static final String ANR_SHOW_BACKGROUND = "anr_show_background";

        /**
         * The {@link ComponentName} string of the service to be used as the voice recognition
         * service.
         *
         * @hide
         */
        public static final String VOICE_RECOGNITION_SERVICE = "voice_recognition_service";

        /**
         * Stores whether an user has consented to have apps verified through PAM.
         * The value is boolean (1 or 0).
         *
         * @hide
         */
        public static final String PACKAGE_VERIFIER_USER_CONSENT =
            "package_verifier_user_consent";

        /**
         * The {@link ComponentName} string of the selected spell checker service which is
         * one of the services managed by the text service manager.
         *
         * @hide
         */
        public static final String SELECTED_SPELL_CHECKER = "selected_spell_checker";

        /**
         * The {@link ComponentName} string of the selected subtype of the selected spell checker
         * service which is one of the services managed by the text service manager.
         *
         * @hide
         */
        public static final String SELECTED_SPELL_CHECKER_SUBTYPE =
                "selected_spell_checker_subtype";

        /**
         * The {@link ComponentName} string whether spell checker is enabled or not.
         *
         * @hide
         */
        public static final String SPELL_CHECKER_ENABLED = "spell_checker_enabled";

        /**
         * What happens when the user presses the Power button while in-call
         * and the screen is on.<br/>
         * <b>Values:</b><br/>
         * 1 - The Power button turns off the screen and locks the device. (Default behavior)<br/>
         * 2 - The Power button hangs up the current call.<br/>
         *
         * @hide
         */
        public static final String INCALL_POWER_BUTTON_BEHAVIOR = "incall_power_button_behavior";

        /**
         * INCALL_POWER_BUTTON_BEHAVIOR value for "turn off screen".
         * @hide
         */
        public static final int INCALL_POWER_BUTTON_BEHAVIOR_SCREEN_OFF = 0x1;

        /**
         * INCALL_POWER_BUTTON_BEHAVIOR value for "hang up".
         * @hide
         */
        public static final int INCALL_POWER_BUTTON_BEHAVIOR_HANGUP = 0x2;

        /**
         * INCALL_POWER_BUTTON_BEHAVIOR default value.
         * @hide
         */
        public static final int INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT =
                INCALL_POWER_BUTTON_BEHAVIOR_SCREEN_OFF;

        /**
         * The current night mode that has been selected by the user.  Owned
         * and controlled by UiModeManagerService.  Constants are as per
         * UiModeManager.
         * @hide
         */
        public static final String UI_NIGHT_MODE = "ui_night_mode";

        /**
         * The current theme mode that has been selected by the user.  Owned
         * and controlled by UiModeManagerService.
         * @hide
         */
        public static final String UI_THEME_MODE = "ui_theme_mode";

        /**
         * Auto theme mode which switches either based on daytime or lightsensor
         * values: 0 = manual (default), 1 = auto twilight (based on daytime)
         * 2 = auto lightsensor (based on light conditions)
         * @hide
         */
        public static final String UI_THEME_AUTO_MODE = "ui_theme_auto_mode";

        /**
         * Whether screensavers are enabled.
         * @hide
         */
        public static final String SCREENSAVER_ENABLED = "screensaver_enabled";

        /**
         * The user's chosen screensaver components.
         *
         * These will be launched by the PhoneWindowManager after a timeout when not on
         * battery, or upon dock insertion (if SCREENSAVER_ACTIVATE_ON_DOCK is set to 1).
         * @hide
         */
        public static final String SCREENSAVER_COMPONENTS = "screensaver_components";

        /**
         * If screensavers are enabled, whether the screensaver should be automatically launched
         * when the device is inserted into a (desk) dock.
         * @hide
         */
        public static final String SCREENSAVER_ACTIVATE_ON_DOCK = "screensaver_activate_on_dock";

        /**
         * If screensavers are enabled, whether the screensaver should be automatically launched
         * when the screen times out when not on battery.
         * @hide
         */
        public static final String SCREENSAVER_ACTIVATE_ON_SLEEP = "screensaver_activate_on_sleep";

        /**
         * If screensavers are enabled, the default screensaver component.
         * @hide
         */
        public static final String SCREENSAVER_DEFAULT_COMPONENT = "screensaver_default_component";

        /**
         * The default NFC payment component
         * @hide
         */
        public static final String NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component";

        /**
         * Specifies the package name currently configured to be the primary sms application
         * @hide
         */
        public static final String SMS_DEFAULT_APPLICATION = "sms_default_application";

        /**
         * Name of a package that the current user has explicitly allowed to see all of that
         * user's notifications.
         *
         * @hide
         */
        public static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";

        /** @hide */
        public static final String BAR_SERVICE_COMPONENT = "bar_service_component";

        /** @hide */
        public static final String IMMERSIVE_MODE_CONFIRMATIONS = "immersive_mode_confirmations";

        /** @hide */
        public static final String HOVER_FIRST_TIME = "hover_first_time";

        /**
         * This is the query URI for finding a print service to install.
         *
         * @hide
         */
        public static final String PRINT_SERVICE_SEARCH_URI = "print_service_search_uri";

        /**
         * This is the query URI for finding a NFC payment service to install.
         *
         * @hide
         */
        public static final String PAYMENT_SERVICE_SEARCH_URI = "payment_service_search_uri";

        /**
         * Whether to allow killing of the foreground app by long-pressing the Back button
         * @hide
         */
        public static final String KILL_APP_LONGPRESS_BACK = "kill_app_longpress_back";

        /**
         * Whether to include options in power menu for rebooting into recovery or bootloader
         * @hide
         */
        public static final String ADVANCED_REBOOT = "advanced_reboot";

        /**
         * Whether to display the 'Wipe data' and 'Force close' options in the notification
         * area and in the recent app list
         * @hide
         */
        public static final String DEVELOPMENT_SHORTCUT = "development_shortcut";

        /**
         * Whether newly installed apps should run with privacy guard by default
         * @hide
         */
         public static final String PRIVACY_GUARD_DEFAULT = "privacy_guard_default";

        /**
         * Whether a notification should be shown if privacy guard is enabled
         * @hide
         */
         public static final String PRIVACY_GUARD_NOTIFICATION = "privacy_guard_notification";

        /**
         * Protected Components
         * @hide
         */
        public static final String PROTECTED_COMPONENTS = "protected_components";

        /**
         * The global recents long press activity chosen by the user.
         * This setting is stored as a flattened component name as
         * per {@link ComponentName#flattenToString()}.
         *
         * @hide
         */
        public static final String RECENTS_LONG_PRESS_ACTIVITY = "recents_long_press_activity";

        /**
         * This are the settings to be backed up.
         *
         * NOTE: Settings are backed up and restored in the order they appear
         *       in this array. If you have one setting depending on another,
         *       make sure that they are ordered appropriately.
         *
         * @hide
         */
        public static final String[] SETTINGS_TO_BACKUP = {
            BUGREPORT_IN_POWER_MENU,                            // moved to global
            ALLOW_MOCK_LOCATION,
            ALLOW_MOCK_SMS,
            PARENTAL_CONTROL_ENABLED,
            PARENTAL_CONTROL_REDIRECT_URL,
            USB_MASS_STORAGE_ENABLED,                           // moved to global
            ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED,
            ACCESSIBILITY_DISPLAY_MAGNIFICATION_SCALE,
            ACCESSIBILITY_DISPLAY_MAGNIFICATION_AUTO_UPDATE,
            ACCESSIBILITY_SCRIPT_INJECTION,
            BACKUP_AUTO_RESTORE,
            ENABLED_ACCESSIBILITY_SERVICES,
            TOUCH_EXPLORATION_GRANTED_ACCESSIBILITY_SERVICES,
            TOUCH_EXPLORATION_ENABLED,
            ACCESSIBILITY_ENABLED,
            ACCESSIBILITY_SPEAK_PASSWORD,
            ACCESSIBILITY_CAPTIONING_ENABLED,
            ACCESSIBILITY_CAPTIONING_LOCALE,
            ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR,
            ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR,
            ACCESSIBILITY_CAPTIONING_EDGE_TYPE,
            ACCESSIBILITY_CAPTIONING_EDGE_COLOR,
            ACCESSIBILITY_CAPTIONING_TYPEFACE,
            ACCESSIBILITY_CAPTIONING_FONT_SCALE,
            TTS_USE_DEFAULTS,
            TTS_DEFAULT_RATE,
            TTS_DEFAULT_PITCH,
            TTS_DEFAULT_SYNTH,
            TTS_DEFAULT_LANG,
            TTS_DEFAULT_COUNTRY,
            TTS_ENABLED_PLUGINS,
            TTS_DEFAULT_LOCALE,
            WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,            // moved to global
            WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY,               // moved to global
            WIFI_NUM_OPEN_NETWORKS_KEPT,                        // moved to global
            MOUNT_PLAY_NOTIFICATION_SND,
            MOUNT_UMS_AUTOSTART,
            MOUNT_UMS_PROMPT,
            MOUNT_UMS_NOTIFY_ENABLED,
            UI_NIGHT_MODE,
            UI_THEME_MODE,
            UI_THEME_AUTO_MODE,
            ADVANCED_REBOOT,
            PRIVACY_GUARD_DEFAULT,
            DEVELOPMENT_SHORTCUT,
            PRIVACY_GUARD_NOTIFICATION
        };

        /**
         * Helper method for determining if a location provider is enabled.
         *
         * @param cr the content resolver to use
         * @param provider the location provider to query
         * @return true if the provider is enabled
         *
         * @deprecated use {@link #LOCATION_MODE} or
         *             {@link LocationManager#isProviderEnabled(String)}
         */
        @Deprecated
        public static final boolean isLocationProviderEnabled(ContentResolver cr, String provider) {
            return isLocationProviderEnabledForUser(cr, provider, UserHandle.myUserId());
        }

        /**
         * Helper method for determining if a location provider is enabled.
         * @param cr the content resolver to use
         * @param provider the location provider to query
         * @param userId the userId to query
         * @return true if the provider is enabled
         * @deprecated use {@link #LOCATION_MODE} or
         *             {@link LocationManager#isProviderEnabled(String)}
         * @hide
         */
        @Deprecated
        public static final boolean isLocationProviderEnabledForUser(ContentResolver cr, String provider, int userId) {
            String allowedProviders = Settings.Secure.getStringForUser(cr,
                    LOCATION_PROVIDERS_ALLOWED, userId);
            return TextUtils.delimitedStringContains(allowedProviders, ',', provider);
        }

        /**
         * Thread-safe method for enabling or disabling a single location provider.
         * @param cr the content resolver to use
         * @param provider the location provider to enable or disable
         * @param enabled true if the provider should be enabled
         * @deprecated use {@link #putInt(ContentResolver, String, int)} and {@link #LOCATION_MODE}
         */
        @Deprecated
        public static final void setLocationProviderEnabled(ContentResolver cr,
                String provider, boolean enabled) {
            setLocationProviderEnabledForUser(cr, provider, enabled, UserHandle.myUserId());
        }

        /**
         * Thread-safe method for enabling or disabling a single location provider.
         *
         * @param cr the content resolver to use
         * @param provider the location provider to enable or disable
         * @param enabled true if the provider should be enabled
         * @param userId the userId for which to enable/disable providers
         * @return true if the value was set, false on database errors
         * @deprecated use {@link #putIntForUser(ContentResolver, String, int, int)} and
         *             {@link #LOCATION_MODE}
         * @hide
         */
        @Deprecated
        public static final boolean setLocationProviderEnabledForUser(ContentResolver cr,
                String provider, boolean enabled, int userId) {
            synchronized (mLocationSettingsLock) {
                // to ensure thread safety, we write the provider name with a '+' or '-'
                // and let the SettingsProvider handle it rather than reading and modifying
                // the list of enabled providers.
                if (enabled) {
                    provider = "+" + provider;
                } else {
                    provider = "-" + provider;
                }
                return putStringForUser(cr, Settings.Secure.LOCATION_PROVIDERS_ALLOWED, provider,
                        userId);
            }
        }

        /**
         * Thread-safe method for setting the location mode to one of
         * {@link #LOCATION_MODE_HIGH_ACCURACY}, {@link #LOCATION_MODE_SENSORS_ONLY},
         * {@link #LOCATION_MODE_BATTERY_SAVING}, or {@link #LOCATION_MODE_OFF}.
         *
         * @param cr the content resolver to use
         * @param mode such as {@link #LOCATION_MODE_HIGH_ACCURACY}
         * @param userId the userId for which to change mode
         * @return true if the value was set, false on database errors
         *
         * @throws IllegalArgumentException if mode is not one of the supported values
         */
        private static final boolean setLocationModeForUser(ContentResolver cr, int mode,
                int userId) {
            synchronized (mLocationSettingsLock) {
                boolean gps = false;
                boolean network = false;
                switch (mode) {
                    case LOCATION_MODE_OFF:
                        break;
                    case LOCATION_MODE_SENSORS_ONLY:
                        gps = true;
                        break;
                    case LOCATION_MODE_BATTERY_SAVING:
                        network = true;
                        break;
                    case LOCATION_MODE_HIGH_ACCURACY:
                        gps = true;
                        network = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid location mode: " + mode);
                }
                boolean gpsSuccess = Settings.Secure.setLocationProviderEnabledForUser(
                        cr, LocationManager.GPS_PROVIDER, gps, userId);
                boolean nlpSuccess = Settings.Secure.setLocationProviderEnabledForUser(
                        cr, LocationManager.NETWORK_PROVIDER, network, userId);
                return gpsSuccess && nlpSuccess;
            }
        }

        /**
         * Thread-safe method for reading the location mode, returns one of
         * {@link #LOCATION_MODE_HIGH_ACCURACY}, {@link #LOCATION_MODE_SENSORS_ONLY},
         * {@link #LOCATION_MODE_BATTERY_SAVING}, or {@link #LOCATION_MODE_OFF}.
         *
         * @param cr the content resolver to use
         * @param userId the userId for which to read the mode
         * @return the location mode
         */
        private static final int getLocationModeForUser(ContentResolver cr, int userId) {
            synchronized (mLocationSettingsLock) {
                boolean gpsEnabled = Settings.Secure.isLocationProviderEnabledForUser(
                        cr, LocationManager.GPS_PROVIDER, userId);
                boolean networkEnabled = Settings.Secure.isLocationProviderEnabledForUser(
                        cr, LocationManager.NETWORK_PROVIDER, userId);
                if (gpsEnabled && networkEnabled) {
                    return LOCATION_MODE_HIGH_ACCURACY;
                } else if (gpsEnabled) {
                    return LOCATION_MODE_SENSORS_ONLY;
                } else if (networkEnabled) {
                    return LOCATION_MODE_BATTERY_SAVING;
                } else {
                    return LOCATION_MODE_OFF;
                }
            }
        }
    }

    /**
     * Global system settings, containing preferences that always apply identically
     * to all defined users.  Applications can read these but are not allowed to write;
     * like the "Secure" settings, these are for preferences that the user must
     * explicitly modify through the system UI or specialized APIs for those values.
     */
    public static final class Global extends NameValueTable {
        public static final String SYS_PROP_SETTING_VERSION = "sys.settings_global_version";

        /**
         * The content:// style URL for global secure settings items.  Not public.
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/global");

        /**
         * Setting whether the global gesture for enabling accessibility is enabled.
         * If this gesture is enabled the user will be able to perfrom it to enable
         * the accessibility state without visiting the settings app.
         * @hide
         */
        public static final String ENABLE_ACCESSIBILITY_GLOBAL_GESTURE_ENABLED =
                "enable_accessibility_global_gesture_enabled";

        /**
         * Whether Airplane Mode is on.
         */
        public static final String AIRPLANE_MODE_ON = "airplane_mode_on";

        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify Bluetooth radio.
         */
        public static final String RADIO_BLUETOOTH = "bluetooth";

        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify Wi-Fi radio.
         */
        public static final String RADIO_WIFI = "wifi";

        /**
         * {@hide}
         */
        public static final String RADIO_WIMAX = "wimax";
        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify Cellular radio.
         */
        public static final String RADIO_CELL = "cell";

        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify NFC radio.
         */
        public static final String RADIO_NFC = "nfc";

        /**
         * A comma separated list of radios that need to be disabled when airplane mode
         * is on. This overrides WIFI_ON and BLUETOOTH_ON, if Wi-Fi and bluetooth are
         * included in the comma separated list.
         */
        public static final String AIRPLANE_MODE_RADIOS = "airplane_mode_radios";

        /**
         * A comma separated list of radios that should to be disabled when airplane mode
         * is on, but can be manually reenabled by the user.  For example, if RADIO_WIFI is
         * added to both AIRPLANE_MODE_RADIOS and AIRPLANE_MODE_TOGGLEABLE_RADIOS, then Wifi
         * will be turned off when entering airplane mode, but the user will be able to reenable
         * Wifi in the Settings app.
         *
         * {@hide}
         */
        public static final String AIRPLANE_MODE_TOGGLEABLE_RADIOS = "airplane_mode_toggleable_radios";

        /**
         * The policy for deciding when Wi-Fi should go to sleep (which will in
         * turn switch to using the mobile data as an Internet connection).
         * <p>
         * Set to one of {@link #WIFI_SLEEP_POLICY_DEFAULT},
         * {@link #WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED}, or
         * {@link #WIFI_SLEEP_POLICY_NEVER}.
         */
        public static final String WIFI_SLEEP_POLICY = "wifi_sleep_policy";

        /**
         * Value for {@link #WIFI_SLEEP_POLICY} to use the default Wi-Fi sleep
         * policy, which is to sleep shortly after the turning off
         * according to the {@link #STAY_ON_WHILE_PLUGGED_IN} setting.
         */
        public static final int WIFI_SLEEP_POLICY_DEFAULT = 0;

        /**
         * Value for {@link #WIFI_SLEEP_POLICY} to use the default policy when
         * the device is on battery, and never go to sleep when the device is
         * plugged in.
         */
        public static final int WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED = 1;

        /**
         * Value for {@link #WIFI_SLEEP_POLICY} to never go to sleep.
         */
        public static final int WIFI_SLEEP_POLICY_NEVER = 2;

        /**
         * Value to specify if the user prefers the date, time and time zone
         * to be automatically fetched from the network (NITZ). 1=yes, 0=no
         */
        public static final String AUTO_TIME = "auto_time";

        /**
         * Value to specify if the user prefers the time zone
         * to be automatically fetched from the network (NITZ). 1=yes, 0=no
         */
        public static final String AUTO_TIME_ZONE = "auto_time_zone";

        /**
         * URI for the car dock "in" event sound.
         * @hide
         */
        public static final String CAR_DOCK_SOUND = "car_dock_sound";

        /**
         * URI for the car dock "out" event sound.
         * @hide
         */
        public static final String CAR_UNDOCK_SOUND = "car_undock_sound";

        /**
         * URI for the desk dock "in" event sound.
         * @hide
         */
        public static final String DESK_DOCK_SOUND = "desk_dock_sound";

        /**
         * URI for the desk dock "out" event sound.
         * @hide
         */
        public static final String DESK_UNDOCK_SOUND = "desk_undock_sound";

        /**
         * Whether to play a sound for dock events.
         * @hide
         */
        public static final String DOCK_SOUNDS_ENABLED = "dock_sounds_enabled";

        /**
         * URI for the "device locked" (keyguard shown) sound.
         * @hide
         */
        public static final String LOCK_SOUND = "lock_sound";

        /**
         * URI for the "device unlocked" sound.
         * @hide
         */
        public static final String UNLOCK_SOUND = "unlock_sound";

        /**
         * URI for the low battery sound file.
         * @hide
         */
        public static final String LOW_BATTERY_SOUND = "low_battery_sound";

        /**
         * Whether to play a sound for low-battery alerts.
         * @hide
         */
        public static final String POWER_SOUNDS_ENABLED = "power_sounds_enabled";

        /**
         * Whether to sound when charger power is connected/disconnected
         * @hide
         */
        public static final String POWER_NOTIFICATIONS_ENABLED = "power_notifications_enabled";

        /**
         * Whether to vibrate when charger power is connected/disconnected
         * @hide
         */
        public static final String POWER_NOTIFICATIONS_VIBRATE = "power_notifications_vibrate";

        /**
         * URI for power notification sounds
         * @hide
         */
        public static final String POWER_NOTIFICATIONS_RINGTONE = "power_notifications_ringtone";

        /**
         * URI for the "wireless charging started" sound.
         * @hide
         */
        public static final String WIRELESS_CHARGING_STARTED_SOUND =
                "wireless_charging_started_sound";

        /**
         * Whether we keep the device on while the device is plugged in.
         * Supported values are:
         * <ul>
         * <li>{@code 0} to never stay on while plugged in</li>
         * <li>{@link BatteryManager#BATTERY_PLUGGED_AC} to stay on for AC charger</li>
         * <li>{@link BatteryManager#BATTERY_PLUGGED_USB} to stay on for USB charger</li>
         * <li>{@link BatteryManager#BATTERY_PLUGGED_WIRELESS} to stay on for wireless charger</li>
         * </ul>
         * These values can be OR-ed together.
         */
        public static final String STAY_ON_WHILE_PLUGGED_IN = "stay_on_while_plugged_in";

        /**
         * When the user has enable the option to have a "bug report" command
         * in the power menu.
         * @hide
         */
        public static final String BUGREPORT_IN_POWER_MENU = "bugreport_in_power_menu";

        /**
         * Whether to wake the display when plugging or unplugging the charger
         *
         * @hide
         */
        public static final String WAKE_WHEN_PLUGGED_OR_UNPLUGGED = "wake_when_plugged_or_unplugged";

        /**
         * Whether ADB is enabled.
         */
        public static final String ADB_ENABLED = "adb_enabled";

        /**
         * Whether assisted GPS should be enabled or not.
         * @hide
         */
        public static final String ASSISTED_GPS_ENABLED = "assisted_gps_enabled";

        /**
         * Whether bluetooth is enabled/disabled
         * 0=disabled. 1=enabled.
         */
        public static final String BLUETOOTH_ON = "bluetooth_on";

        /**
         * CDMA Cell Broadcast SMS
         *                            0 = CDMA Cell Broadcast SMS disabled
         *                            1 = CDMA Cell Broadcast SMS enabled
         * @hide
         */
        public static final String CDMA_CELL_BROADCAST_SMS =
                "cdma_cell_broadcast_sms";

        /**
         * The CDMA roaming mode 0 = Home Networks, CDMA default
         *                       1 = Roaming on Affiliated networks
         *                       2 = Roaming on any networks
         * @hide
         */
        public static final String CDMA_ROAMING_MODE = "roaming_settings";

        /**
         * The CDMA subscription mode 0 = RUIM/SIM (default)
         *                                1 = NV
         * @hide
         */
        public static final String CDMA_SUBSCRIPTION_MODE = "subscription_mode";

        /** Inactivity timeout to track mobile data activity.
        *
        * If set to a positive integer, it indicates the inactivity timeout value in seconds to
        * infer the data activity of mobile network. After a period of no activity on mobile
        * networks with length specified by the timeout, an {@code ACTION_DATA_ACTIVITY_CHANGE}
        * intent is fired to indicate a transition of network status from "active" to "idle". Any
        * subsequent activity on mobile networks triggers the firing of {@code
        * ACTION_DATA_ACTIVITY_CHANGE} intent indicating transition from "idle" to "active".
        *
        * Network activity refers to transmitting or receiving data on the network interfaces.
        *
        * Tracking is disabled if set to zero or negative value.
        *
        * @hide
        */
       public static final String DATA_ACTIVITY_TIMEOUT_MOBILE = "data_activity_timeout_mobile";

       /** Timeout to tracking Wifi data activity. Same as {@code DATA_ACTIVITY_TIMEOUT_MOBILE}
        * but for Wifi network.
        * @hide
        */
       public static final String DATA_ACTIVITY_TIMEOUT_WIFI = "data_activity_timeout_wifi";

       /**
        * Whether or not data roaming is enabled. (0 = false, 1 = true)
        */
       public static final String DATA_ROAMING = "data_roaming";

       /**
        * The value passed to a Mobile DataConnection via bringUp which defines the
        * number of retries to preform when setting up the initial connection. The default
        * value defined in DataConnectionTrackerBase#DEFAULT_MDC_INITIAL_RETRY is currently 1.
        * @hide
        */
       public static final String MDC_INITIAL_MAX_RETRY = "mdc_initial_max_retry";

       /**
        * Whether user has enabled development settings.
        */
       public static final String DEVELOPMENT_SETTINGS_ENABLED = "development_settings_enabled";

       /**
        * Whether the device has been provisioned (0 = false, 1 = true)
        */
       public static final String DEVICE_PROVISIONED = "device_provisioned";

       /**
        * The saved value for WindowManagerService.setForcedDisplayDensity().
        * One integer in dpi.  If unset, then use the real display density.
        * @hide
        */
       public static final String DISPLAY_DENSITY_FORCED = "display_density_forced";

       /**
        * The saved value for WindowManagerService.setForcedDisplaySize().
        * Two integers separated by a comma.  If unset, then use the real display size.
        * @hide
        */
       public static final String DISPLAY_SIZE_FORCED = "display_size_forced";

       /**
        * The maximum size, in bytes, of a download that the download manager will transfer over
        * a non-wifi connection.
        * @hide
        */
       public static final String DOWNLOAD_MAX_BYTES_OVER_MOBILE =
               "download_manager_max_bytes_over_mobile";

       /**
        * The recommended maximum size, in bytes, of a download that the download manager should
        * transfer over a non-wifi connection. Over this size, the use will be warned, but will
        * have the option to start the download over the mobile connection anyway.
        * @hide
        */
       public static final String DOWNLOAD_RECOMMENDED_MAX_BYTES_OVER_MOBILE =
               "download_manager_recommended_max_bytes_over_mobile";

       /**
        * Whether the package installer should allow installation of apps downloaded from
        * sources other than Google Play.
        *
        * 1 = allow installing from other sources
        * 0 = only allow installing from Google Play
        */
       public static final String INSTALL_NON_MARKET_APPS = "install_non_market_apps";

       /**
        * Whether mobile data connections are allowed by the user.  See
        * ConnectivityManager for more info.
        * @hide
        */
       public static final String MOBILE_DATA = "mobile_data";

       /** {@hide} */
       public static final String NETSTATS_ENABLED = "netstats_enabled";
       /** {@hide} */
       public static final String NETSTATS_POLL_INTERVAL = "netstats_poll_interval";
       /** {@hide} */
       public static final String NETSTATS_TIME_CACHE_MAX_AGE = "netstats_time_cache_max_age";
       /** {@hide} */
       public static final String NETSTATS_GLOBAL_ALERT_BYTES = "netstats_global_alert_bytes";
       /** {@hide} */
       public static final String NETSTATS_SAMPLE_ENABLED = "netstats_sample_enabled";
       /** {@hide} */
       public static final String NETSTATS_REPORT_XT_OVER_DEV = "netstats_report_xt_over_dev";

       /** {@hide} */
       public static final String NETSTATS_DEV_BUCKET_DURATION = "netstats_dev_bucket_duration";
       /** {@hide} */
       public static final String NETSTATS_DEV_PERSIST_BYTES = "netstats_dev_persist_bytes";
       /** {@hide} */
       public static final String NETSTATS_DEV_ROTATE_AGE = "netstats_dev_rotate_age";
       /** {@hide} */
       public static final String NETSTATS_DEV_DELETE_AGE = "netstats_dev_delete_age";

       /** {@hide} */
       public static final String NETSTATS_UID_BUCKET_DURATION = "netstats_uid_bucket_duration";
       /** {@hide} */
       public static final String NETSTATS_UID_PERSIST_BYTES = "netstats_uid_persist_bytes";
       /** {@hide} */
       public static final String NETSTATS_UID_ROTATE_AGE = "netstats_uid_rotate_age";
       /** {@hide} */
       public static final String NETSTATS_UID_DELETE_AGE = "netstats_uid_delete_age";

       /** {@hide} */
       public static final String NETSTATS_UID_TAG_BUCKET_DURATION = "netstats_uid_tag_bucket_duration";
       /** {@hide} */
       public static final String NETSTATS_UID_TAG_PERSIST_BYTES = "netstats_uid_tag_persist_bytes";
       /** {@hide} */
       public static final String NETSTATS_UID_TAG_ROTATE_AGE = "netstats_uid_tag_rotate_age";
       /** {@hide} */
       public static final String NETSTATS_UID_TAG_DELETE_AGE = "netstats_uid_tag_delete_age";

       /**
        * User preference for which network(s) should be used. Only the
        * connectivity service should touch this.
        */
       public static final String NETWORK_PREFERENCE = "network_preference";

       /**
        * If the NITZ_UPDATE_DIFF time is exceeded then an automatic adjustment
        * to SystemClock will be allowed even if NITZ_UPDATE_SPACING has not been
        * exceeded.
        * @hide
        */
       public static final String NITZ_UPDATE_DIFF = "nitz_update_diff";

       /**
        * The length of time in milli-seconds that automatic small adjustments to
        * SystemClock are ignored if NITZ_UPDATE_DIFF is not exceeded.
        * @hide
        */
       public static final String NITZ_UPDATE_SPACING = "nitz_update_spacing";

       /** Preferred NTP server. {@hide} */
       public static final String NTP_SERVER = "ntp_server";
       /** Timeout in milliseconds to wait for NTP server. {@hide} */
       public static final String NTP_TIMEOUT = "ntp_timeout";

       /**
        * Whether the package manager should send package verification broadcasts for verifiers to
        * review apps prior to installation.
        * 1 = request apps to be verified prior to installation, if a verifier exists.
        * 0 = do not verify apps before installation
        * @hide
        */
       public static final String PACKAGE_VERIFIER_ENABLE = "package_verifier_enable";

       /** Timeout for package verification.
        * @hide */
       public static final String PACKAGE_VERIFIER_TIMEOUT = "verifier_timeout";

       /** Default response code for package verification.
        * @hide */
       public static final String PACKAGE_VERIFIER_DEFAULT_RESPONSE = "verifier_default_response";

       /**
        * Show package verification setting in the Settings app.
        * 1 = show (default)
        * 0 = hide
        * @hide
        */
       public static final String PACKAGE_VERIFIER_SETTING_VISIBLE = "verifier_setting_visible";

       /**
        * Run package verificaiton on apps installed through ADB/ADT/USB
        * 1 = perform package verification on ADB installs (default)
        * 0 = bypass package verification on ADB installs
        * @hide
        */
       public static final String PACKAGE_VERIFIER_INCLUDE_ADB = "verifier_verify_adb_installs";

       /**
        * The interval in milliseconds at which to check packet counts on the
        * mobile data interface when screen is on, to detect possible data
        * connection problems.
        * @hide
        */
       public static final String PDP_WATCHDOG_POLL_INTERVAL_MS =
               "pdp_watchdog_poll_interval_ms";

       /**
        * The interval in milliseconds at which to check packet counts on the
        * mobile data interface when screen is off, to detect possible data
        * connection problems.
        * @hide
        */
       public static final String PDP_WATCHDOG_LONG_POLL_INTERVAL_MS =
               "pdp_watchdog_long_poll_interval_ms";

       /**
        * The interval in milliseconds at which to check packet counts on the
        * mobile data interface after {@link #PDP_WATCHDOG_TRIGGER_PACKET_COUNT}
        * outgoing packets has been reached without incoming packets.
        * @hide
        */
       public static final String PDP_WATCHDOG_ERROR_POLL_INTERVAL_MS =
               "pdp_watchdog_error_poll_interval_ms";

       /**
        * The number of outgoing packets sent without seeing an incoming packet
        * that triggers a countdown (of {@link #PDP_WATCHDOG_ERROR_POLL_COUNT}
        * device is logged to the event log
        * @hide
        */
       public static final String PDP_WATCHDOG_TRIGGER_PACKET_COUNT =
               "pdp_watchdog_trigger_packet_count";

       /**
        * The number of polls to perform (at {@link #PDP_WATCHDOG_ERROR_POLL_INTERVAL_MS})
        * after hitting {@link #PDP_WATCHDOG_TRIGGER_PACKET_COUNT} before
        * attempting data connection recovery.
        * @hide
        */
       public static final String PDP_WATCHDOG_ERROR_POLL_COUNT =
               "pdp_watchdog_error_poll_count";

       /**
        * The number of failed PDP reset attempts before moving to something more
        * drastic: re-registering to the network.
        * @hide
        */
       public static final String PDP_WATCHDOG_MAX_PDP_RESET_FAIL_COUNT =
               "pdp_watchdog_max_pdp_reset_fail_count";

       /**
        * A positive value indicates how often the SamplingProfiler
        * should take snapshots. Zero value means SamplingProfiler
        * is disabled.
        *
        * @hide
        */
       public static final String SAMPLING_PROFILER_MS = "sampling_profiler_ms";

       /**
        * URL to open browser on to allow user to manage a prepay account
        * @hide
        */
       public static final String SETUP_PREPAID_DATA_SERVICE_URL =
               "setup_prepaid_data_service_url";

       /**
        * URL to attempt a GET on to see if this is a prepay device
        * @hide
        */
       public static final String SETUP_PREPAID_DETECTION_TARGET_URL =
               "setup_prepaid_detection_target_url";

       /**
        * Host to check for a redirect to after an attempt to GET
        * SETUP_PREPAID_DETECTION_TARGET_URL. (If we redirected there,
        * this is a prepaid device with zero balance.)
        * @hide
        */
       public static final String SETUP_PREPAID_DETECTION_REDIR_HOST =
               "setup_prepaid_detection_redir_host";

       /**
        * The interval in milliseconds at which to check the number of SMS sent out without asking
        * for use permit, to limit the un-authorized SMS usage.
        *
        * @hide
        */
       public static final String SMS_OUTGOING_CHECK_INTERVAL_MS =
               "sms_outgoing_check_interval_ms";

       /**
        * The number of outgoing SMS sent without asking for user permit (of {@link
        * #SMS_OUTGOING_CHECK_INTERVAL_MS}
        *
        * @hide
        */
       public static final String SMS_OUTGOING_CHECK_MAX_COUNT =
               "sms_outgoing_check_max_count";

       /**
        * Used to disable SMS short code confirmation - defaults to true.
        * True indcates we will do the check, etc.  Set to false to disable.
        * @see com.android.internal.telephony.SmsUsageMonitor
        * @hide
        */
       public static final String SMS_SHORT_CODE_CONFIRMATION = "sms_short_code_confirmation";

        /**
         * Used to select which country we use to determine premium sms codes.
         * One of com.android.internal.telephony.SMSDispatcher.PREMIUM_RULE_USE_SIM,
         * com.android.internal.telephony.SMSDispatcher.PREMIUM_RULE_USE_NETWORK,
         * or com.android.internal.telephony.SMSDispatcher.PREMIUM_RULE_USE_BOTH.
         * @hide
         */
        public static final String SMS_SHORT_CODE_RULE = "sms_short_code_rule";

       /**
        * Used to select TCP's default initial receiver window size in segments - defaults to a build config value
        * @hide
        */
       public static final String TCP_DEFAULT_INIT_RWND = "tcp_default_init_rwnd";

       /**
        * Used to disable Tethering on a device - defaults to true
        * @hide
        */
       public static final String TETHER_SUPPORTED = "tether_supported";

       /**
        * Used to require DUN APN on the device or not - defaults to a build config value
        * which defaults to false
        * @hide
        */
       public static final String TETHER_DUN_REQUIRED = "tether_dun_required";

       /**
        * Used to hold a gservices-provisioned apn value for DUN.  If set, or the
        * corresponding build config values are set it will override the APN DB
        * values.
        * Consists of a comma seperated list of strings:
        * "name,apn,proxy,port,username,password,server,mmsc,mmsproxy,mmsport,mcc,mnc,auth,type"
        * note that empty fields can be ommitted: "name,apn,,,,,,,,,310,260,,DUN"
        * @hide
        */
       public static final String TETHER_DUN_APN = "tether_dun_apn";

       /**
        * USB Mass Storage Enabled
        */
       public static final String USB_MASS_STORAGE_ENABLED = "usb_mass_storage_enabled";

       /**
        * If this setting is set (to anything), then all references
        * to Gmail on the device must change to Google Mail.
        */
       public static final String USE_GOOGLE_MAIL = "use_google_mail";

       /** Autofill server address (Used in WebView/browser).
        * {@hide} */
       public static final String WEB_AUTOFILL_QUERY_URL =
           "web_autofill_query_url";

       /**
        * Whether Wifi display is enabled/disabled
        * 0=disabled. 1=enabled.
        * @hide
        */
       public static final String WIFI_DISPLAY_ON = "wifi_display_on";

       /**
        * Whether Wifi display certification mode is enabled/disabled
        * 0=disabled. 1=enabled.
        * @hide
        */
       public static final String WIFI_DISPLAY_CERTIFICATION_ON =
               "wifi_display_certification_on";

       /**
        * WPS Configuration method used by Wifi display, this setting only
        * takes effect when WIFI_DISPLAY_CERTIFICATION_ON is 1 (enabled).
        *
        * Possible values are:
        *
        * WpsInfo.INVALID: use default WPS method chosen by framework
        * WpsInfo.PBC    : use Push button
        * WpsInfo.KEYPAD : use Keypad
        * WpsInfo.DISPLAY: use Display
        * @hide
        */
       public static final String WIFI_DISPLAY_WPS_CONFIG =
           "wifi_display_wps_config";

       /**
        * Whether to notify the user of open networks.
        * <p>
        * If not connected and the scan results have an open network, we will
        * put this notification up. If we attempt to connect to a network or
        * the open network(s) disappear, we remove the notification. When we
        * show the notification, we will not show it again for
        * {@link android.provider.Settings.Secure#WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY} time.
        */
       public static final String WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
               "wifi_networks_available_notification_on";
       /**
        * {@hide}
        */
       public static final String WIMAX_NETWORKS_AVAILABLE_NOTIFICATION_ON =
               "wimax_networks_available_notification_on";

       /**
        * Delay (in seconds) before repeating the Wi-Fi networks available notification.
        * Connecting to a network will reset the timer.
        */
       public static final String WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY =
               "wifi_networks_available_repeat_delay";

       /**
        * 802.11 country code in ISO 3166 format
        * @hide
        */
       public static final String WIFI_COUNTRY_CODE = "wifi_country_code";

       /**
        * 802.11 country code in ISO 3166 format custom user value
        * @hide
        */
       public static final String WIFI_COUNTRY_CODE_USER = "wifi_country_code_user";

       /**
        * The interval in milliseconds to issue wake up scans when wifi needs
        * to connect. This is necessary to connect to an access point when
        * device is on the move and the screen is off.
        * @hide
        */
       public static final String WIFI_FRAMEWORK_SCAN_INTERVAL_MS =
               "wifi_framework_scan_interval_ms";

       /**
        * The interval in milliseconds after which Wi-Fi is considered idle.
        * When idle, it is possible for the device to be switched from Wi-Fi to
        * the mobile data network.
        * @hide
        */
       public static final String WIFI_IDLE_MS = "wifi_idle_ms";

       /**
        * When the number of open networks exceeds this number, the
        * least-recently-used excess networks will be removed.
        */
       public static final String WIFI_NUM_OPEN_NETWORKS_KEPT = "wifi_num_open_networks_kept";

       /**
        * Whether the Wi-Fi should be on.  Only the Wi-Fi service should touch this.
        */
       public static final String WIFI_ON = "wifi_on";

       /**
        * Setting to allow scans to be enabled even wifi is turned off for connectivity.
        * @hide
        */
       public static final String WIFI_SCAN_ALWAYS_AVAILABLE =
                "wifi_scan_always_enabled";

       /**
        * Used to save the Wifi_ON state prior to tethering.
        * This state will be checked to restore Wifi after
        * the user turns off tethering.
        *
        * @hide
        */
       public static final String WIFI_SAVED_STATE = "wifi_saved_state";

       /**
        * The interval in milliseconds to scan as used by the wifi supplicant
        * @hide
        */
       public static final String WIFI_SUPPLICANT_SCAN_INTERVAL_MS =
               "wifi_supplicant_scan_interval_ms";

       /**
        * The interval in milliseconds to scan at supplicant when p2p is connected
        * @hide
        */
       public static final String WIFI_SCAN_INTERVAL_WHEN_P2P_CONNECTED_MS =
               "wifi_scan_interval_p2p_connected_ms";

       /**
        * The intervel in milliseconds to scan at supplicant when wfd session
        * @hide
        */
       public static final String WIFI_SUPPLICANT_SCAN_INTERVAL_WFD_CONNECTED_MS =
                 "wifi_scan_intervel_wfd_connected_ms";

       /**
        * Whether the Wi-Fi watchdog is enabled.
        */
       public static final String WIFI_WATCHDOG_ON = "wifi_watchdog_on";

       /**
        * Setting to turn off poor network avoidance on Wi-Fi. Feature is enabled by default and
        * the setting needs to be set to 0 to disable it.
        * @hide
        */
       public static final String WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED =
               "wifi_watchdog_poor_network_test_enabled";

       /**
        * Setting to turn on suspend optimizations at screen off on Wi-Fi. Enabled by default and
        * needs to be set to 0 to disable it.
        * @hide
        */
       public static final String WIFI_SUSPEND_OPTIMIZATIONS_ENABLED =
               "wifi_suspend_optimizations_enabled";

       /**
        * The maximum number of times we will retry a connection to an access
        * point for which we have failed in acquiring an IP address from DHCP.
        * A value of N means that we will make N+1 connection attempts in all.
        */
       public static final String WIFI_MAX_DHCP_RETRY_COUNT = "wifi_max_dhcp_retry_count";

       /**
        * Maximum amount of time in milliseconds to hold a wakelock while waiting for mobile
        * data connectivity to be established after a disconnect from Wi-Fi.
        */
       public static final String WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS =
           "wifi_mobile_data_transition_wakelock_timeout_ms";

       /**
        * The operational wifi frequency band
        * Set to one of {@link WifiManager#WIFI_FREQUENCY_BAND_AUTO},
        * {@link WifiManager#WIFI_FREQUENCY_BAND_5GHZ} or
        * {@link WifiManager#WIFI_FREQUENCY_BAND_2GHZ}
        *
        * @hide
        */
       public static final String WIFI_FREQUENCY_BAND = "wifi_frequency_band";

       /**
        * The Wi-Fi peer-to-peer device name
        * @hide
        */
       public static final String WIFI_P2P_DEVICE_NAME = "wifi_p2p_device_name";

       /**
        * The min time between wifi disable and wifi enable
        * @hide
        */
       public static final String WIFI_REENABLE_DELAY_MS = "wifi_reenable_delay";

       /**
        * The number of milliseconds to delay when checking for data stalls during
        * non-aggressive detection. (screen is turned off.)
        * @hide
        */
       public static final String DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS =
               "data_stall_alarm_non_aggressive_delay_in_ms";

       /**
        * The number of milliseconds to delay when checking for data stalls during
        * aggressive detection. (screen on or suspected data stall)
        * @hide
        */
       public static final String DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS =
               "data_stall_alarm_aggressive_delay_in_ms";

       /**
        * The number of milliseconds to allow the provisioning apn to remain active
        * @hide
        */
       public static final String PROVISIONING_APN_ALARM_DELAY_IN_MS =
               "provisioning_apn_alarm_delay_in_ms";

       /**
        * The interval in milliseconds at which to check gprs registration
        * after the first registration mismatch of gprs and voice service,
        * to detect possible data network registration problems.
        *
        * @hide
        */
       public static final String GPRS_REGISTER_CHECK_PERIOD_MS =
               "gprs_register_check_period_ms";

       /**
        * Nonzero causes Log.wtf() to crash.
        * @hide
        */
       public static final String WTF_IS_FATAL = "wtf_is_fatal";

       /**
        * Ringer mode. This is used internally, changing this value will not
        * change the ringer mode. See AudioManager.
        */
       public static final String MODE_RINGER = "mode_ringer";

       /**
        * Overlay display devices setting.
        * The associated value is a specially formatted string that describes the
        * size and density of simulated secondary display devices.
        * <p>
        * Format: {width}x{height}/{dpi};...
        * </p><p>
        * Example:
        * <ul>
        * <li><code>1280x720/213</code>: make one overlay that is 1280x720 at 213dpi.</li>
        * <li><code>1920x1080/320;1280x720/213</code>: make two overlays, the first
        * at 1080p and the second at 720p.</li>
        * <li>If the value is empty, then no overlay display devices are created.</li>
        * </ul></p>
        *
        * @hide
        */
       public static final String OVERLAY_DISPLAY_DEVICES = "overlay_display_devices";

        /**
         * Threshold values for the duration and level of a discharge cycle,
         * under which we log discharge cycle info.
         *
         * @hide
         */
        public static final String
                BATTERY_DISCHARGE_DURATION_THRESHOLD = "battery_discharge_duration_threshold";

        /** @hide */
        public static final String BATTERY_DISCHARGE_THRESHOLD = "battery_discharge_threshold";

        /**
         * Flag for allowing ActivityManagerService to send ACTION_APP_ERROR
         * intents on application crashes and ANRs. If this is disabled, the
         * crash/ANR dialog will never display the "Report" button.
         * <p>
         * Type: int (0 = disallow, 1 = allow)
         *
         * @hide
         */
        public static final String SEND_ACTION_APP_ERROR = "send_action_app_error";

        /**
         * Maximum age of entries kept by {@link DropBoxManager}.
         *
         * @hide
         */
        public static final String DROPBOX_AGE_SECONDS = "dropbox_age_seconds";

        /**
         * Maximum number of entry files which {@link DropBoxManager} will keep
         * around.
         *
         * @hide
         */
        public static final String DROPBOX_MAX_FILES = "dropbox_max_files";

        /**
         * Maximum amount of disk space used by {@link DropBoxManager} no matter
         * what.
         *
         * @hide
         */
        public static final String DROPBOX_QUOTA_KB = "dropbox_quota_kb";

        /**
         * Percent of free disk (excluding reserve) which {@link DropBoxManager}
         * will use.
         *
         * @hide
         */
        public static final String DROPBOX_QUOTA_PERCENT = "dropbox_quota_percent";

        /**
         * Percent of total disk which {@link DropBoxManager} will never dip
         * into.
         *
         * @hide
         */
        public static final String DROPBOX_RESERVE_PERCENT = "dropbox_reserve_percent";

        /**
         * Prefix for per-tag dropbox disable/enable settings.
         *
         * @hide
         */
        public static final String DROPBOX_TAG_PREFIX = "dropbox:";

        /**
         * Lines of logcat to include with system crash/ANR/etc. reports, as a
         * prefix of the dropbox tag of the report type. For example,
         * "logcat_for_system_server_anr" controls the lines of logcat captured
         * with system server ANR reports. 0 to disable.
         *
         * @hide
         */
        public static final String ERROR_LOGCAT_PREFIX = "logcat_for_";

        /**
         * The interval in minutes after which the amount of free storage left
         * on the device is logged to the event log
         *
         * @hide
         */
        public static final String SYS_FREE_STORAGE_LOG_INTERVAL = "sys_free_storage_log_interval";

        /**
         * Threshold for the amount of change in disk free space required to
         * report the amount of free space. Used to prevent spamming the logs
         * when the disk free space isn't changing frequently.
         *
         * @hide
         */
        public static final String
                DISK_FREE_CHANGE_REPORTING_THRESHOLD = "disk_free_change_reporting_threshold";

        /**
         * Minimum percentage of free storage on the device that is used to
         * determine if the device is running low on storage. The default is 10.
         * <p>
         * Say this value is set to 10, the device is considered running low on
         * storage if 90% or more of the device storage is filled up.
         *
         * @hide
         */
        public static final String
                SYS_STORAGE_THRESHOLD_PERCENTAGE = "sys_storage_threshold_percentage";

        /**
         * Maximum byte size of the low storage threshold. This is to ensure
         * that {@link #SYS_STORAGE_THRESHOLD_PERCENTAGE} does not result in an
         * overly large threshold for large storage devices. Currently this must
         * be less than 2GB. This default is 500MB.
         *
         * @hide
         */
        public static final String
                SYS_STORAGE_THRESHOLD_MAX_BYTES = "sys_storage_threshold_max_bytes";

        /**
         * Minimum bytes of free storage on the device before the data partition
         * is considered full. By default, 1 MB is reserved to avoid system-wide
         * SQLite disk full exceptions.
         *
         * @hide
         */
        public static final String
                SYS_STORAGE_FULL_THRESHOLD_BYTES = "sys_storage_full_threshold_bytes";

        /**
         * The maximum reconnect delay for short network outages or when the
         * network is suspended due to phone use.
         *
         * @hide
         */
        public static final String
                SYNC_MAX_RETRY_DELAY_IN_SECONDS = "sync_max_retry_delay_in_seconds";

        /**
         * The number of milliseconds to delay before sending out
         * {@link ConnectivityManager#CONNECTIVITY_ACTION} broadcasts.
         *
         * @hide
         */
        public static final String CONNECTIVITY_CHANGE_DELAY = "connectivity_change_delay";


        /**
         * Network sampling interval, in seconds. We'll generate link information
         * about bytes/packets sent and error rates based on data sampled in this interval
         *
         * @hide
         */

        public static final String CONNECTIVITY_SAMPLING_INTERVAL_IN_SECONDS =
                "connectivity_sampling_interval_in_seconds";

        /**
         * The series of successively longer delays used in retrying to download PAC file.
         * Last delay is used between successful PAC downloads.
         *
         * @hide
         */
        public static final String PAC_CHANGE_DELAY = "pac_change_delay";

        /**
         * Setting to turn off captive portal detection. Feature is enabled by
         * default and the setting needs to be set to 0 to disable it.
         *
         * @hide
         */
        public static final String
                CAPTIVE_PORTAL_DETECTION_ENABLED = "captive_portal_detection_enabled";

        /**
         * The server used for captive portal detection upon a new conection. A
         * 204 response code from the server is used for validation.
         *
         * @hide
         */
        public static final String CAPTIVE_PORTAL_SERVER = "captive_portal_server";

        /**
         * Whether network service discovery is enabled.
         *
         * @hide
         */
        public static final String NSD_ON = "nsd_on";

        /**
         * Let user pick default install location.
         *
         * @hide
         */
        public static final String SET_INSTALL_LOCATION = "set_install_location";

        /**
         * Default install location value.
         * 0 = auto, let system decide
         * 1 = internal
         * 2 = sdcard
         * @hide
         */
        public static final String DEFAULT_INSTALL_LOCATION = "default_install_location";

        /**
         * ms during which to consume extra events related to Inet connection
         * condition after a transtion to fully-connected
         *
         * @hide
         */
        public static final String
                INET_CONDITION_DEBOUNCE_UP_DELAY = "inet_condition_debounce_up_delay";

        /**
         * ms during which to consume extra events related to Inet connection
         * condtion after a transtion to partly-connected
         *
         * @hide
         */
        public static final String
                INET_CONDITION_DEBOUNCE_DOWN_DELAY = "inet_condition_debounce_down_delay";

        /** {@hide} */
        public static final String
                READ_EXTERNAL_STORAGE_ENFORCED_DEFAULT = "read_external_storage_enforced_default";

        /**
         * Host name and port for global http proxy. Uses ':' seperator for
         * between host and port.
         */
        public static final String HTTP_PROXY = "http_proxy";

        /**
         * Host name for global http proxy. Set via ConnectivityManager.
         *
         * @hide
         */
        public static final String GLOBAL_HTTP_PROXY_HOST = "global_http_proxy_host";

        /**
         * Integer host port for global http proxy. Set via ConnectivityManager.
         *
         * @hide
         */
        public static final String GLOBAL_HTTP_PROXY_PORT = "global_http_proxy_port";

        /**
         * Exclusion list for global proxy. This string contains a list of
         * comma-separated domains where the global proxy does not apply.
         * Domains should be listed in a comma- separated list. Example of
         * acceptable formats: ".domain1.com,my.domain2.com" Use
         * ConnectivityManager to set/get.
         *
         * @hide
         */
        public static final String
                GLOBAL_HTTP_PROXY_EXCLUSION_LIST = "global_http_proxy_exclusion_list";

        /**
         * The location PAC File for the proxy.
         * @hide
         */
        public static final String
                GLOBAL_HTTP_PROXY_PAC = "global_proxy_pac_url";

        /**
         * Enables the UI setting to allow the user to specify the global HTTP
         * proxy and associated exclusion list.
         *
         * @hide
         */
        public static final String SET_GLOBAL_HTTP_PROXY = "set_global_http_proxy";

        /**
         * Setting for default DNS in case nobody suggests one
         *
         * @hide
         */
        public static final String DEFAULT_DNS_SERVER = "default_dns_server";

        /** {@hide} */
        public static final String
                BLUETOOTH_HEADSET_PRIORITY_PREFIX = "bluetooth_headset_priority_";
        /** {@hide} */
        public static final String
                BLUETOOTH_A2DP_SINK_PRIORITY_PREFIX = "bluetooth_a2dp_sink_priority_";
        /** {@hide} */
        public static final String
                BLUETOOTH_LAST_CONNECTED_A2DP_SEP_TYPE = "bluetooth_last_connected_a2dp_sep_type_";
        /** {@hide} */
        public static final String
                BLUETOOTH_INPUT_DEVICE_PRIORITY_PREFIX = "bluetooth_input_device_priority_";
        /** {@hide} */
        public static final String
                BLUETOOTH_MAP_PRIORITY_PREFIX = "bluetooth_map_priority_";

        /**
         * Get the key that retrieves a bluetooth headset's priority.
         * @hide
         */
        public static final String getBluetoothHeadsetPriorityKey(String address) {
            return BLUETOOTH_HEADSET_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
        }

        /**
         * Get the key that retrieves a bluetooth a2dp sink's priority.
         * @hide
         */
        public static final String getBluetoothA2dpSinkPriorityKey(String address) {
            return BLUETOOTH_A2DP_SINK_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
        }

        /**
         * Get the key that retrieves a bluetooth last connected a2dp profile.
         * @hide
         */
        public static final String getBluetoothLastConnectedA2dpSepTypeKey(String address) {
            return BLUETOOTH_LAST_CONNECTED_A2DP_SEP_TYPE + address.toUpperCase(Locale.ROOT);
        }

        /**
         * Get the key that retrieves a bluetooth Input Device's priority.
         * @hide
         */
        public static final String getBluetoothInputDevicePriorityKey(String address) {
            return BLUETOOTH_INPUT_DEVICE_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
        }

        /**
         * Get the key that retrieves a bluetooth map priority.
         * @hide
         */
        public static final String getBluetoothMapPriorityKey(String address) {
            return BLUETOOTH_MAP_PRIORITY_PREFIX + address.toUpperCase(Locale.ROOT);
        }
        /**
         * Scaling factor for normal window animations. Setting to 0 will
         * disable window animations.
         */
        public static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";

        /**
         * Scaling factor for activity transition animations. Setting to 0 will
         * disable window animations.
         */
        public static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";

        /**
         * Scaling factor for Animator-based animations. This affects both the
         * start delay and duration of all such animations. Setting to 0 will
         * cause animations to end immediately. The default value is 1.
         */
        public static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";

        /**
         * Scaling factor for normal window animations. Setting to 0 will
         * disable window animations.
         *
         * @hide
         */
        public static final String FANCY_IME_ANIMATIONS = "fancy_ime_animations";

        /**
         * If 0, the compatibility mode is off for all applications.
         * If 1, older applications run under compatibility mode.
         * TODO: remove this settings before code freeze (bug/1907571)
         * @hide
         */
        public static final String COMPATIBILITY_MODE = "compatibility_mode";

        /**
         * CDMA only settings
         * Emergency Tone  0 = Off
         *                 1 = Alert
         *                 2 = Vibrate
         * @hide
         */
        public static final String EMERGENCY_TONE = "emergency_tone";

        /**
         * CDMA only settings
         * Whether the auto retry is enabled. The value is
         * boolean (1 or 0).
         * @hide
         */
        public static final String CALL_AUTO_RETRY = "call_auto_retry";

        /**
         * The preferred network mode   7 = Global
         *                              6 = EvDo only
         *                              5 = CDMA w/o EvDo
         *                              4 = CDMA / EvDo auto
         *                              3 = GSM / WCDMA auto
         *                              2 = WCDMA only
         *                              1 = GSM only
         *                              0 = GSM / WCDMA preferred
         * @hide
         */
        public static final String PREFERRED_NETWORK_MODE =
                "preferred_network_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_OPTION =
                "battery_saver_option";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_NORMAL_MODE =
                "battery_saver_normal_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_POWER_SAVING_MODE =
                "battery_saver_power_saving_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_SCREEN_OFF =
                "battery_saver_screen_off";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_IGNORE_LOCKED =
                "battery_saver_ignore_locked";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_MODE_CHANGE_DELAY =
                "battery_saver_mode_change_delay";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_BATTERY_MODE =
                "battery_saver_battery_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_BATTERY_LEVEL =
                "battery_saver_battery_level";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_BLUETOOTH_MODE =
                "battery_saver_bluetooth_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_LOCATION_MODE =
                "battery_saver_location_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_WIFI_MODE =
                "battery_saver_wifi_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_DATA_MODE =
                "battery_saver_data_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_NOSIGNAL_MODE =
                "battery_saver_nosignal_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_NETWORK_INTERVAL_MODE =
                "battery_saver_network_interval_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_SYNC_MODE =
                "battery_saver_sync_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_SHOW_TOAST =
                "battery_saver_show_toast";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_KILLALL_MODE =
                "battery_saver_killall_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_LED_MODE =
                "battery_saver_led_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_LED_DISABLE =
                "battery_saver_led_disable";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_VIBRATE_MODE =
                "battery_saver_vibrate_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_VIBRATE_DISABLE =
                "battery_saver_vibrate_disable";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_CPU_MODE =
                "battery_saver_cpu_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_CPU_FREQ =
                "battery_saver_cpu_freq";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_CPU_FREQ_DEFAULT =
                "battery_saver_cpu_freq_default";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_BRIGHTNESS_MODE =
                "battery_saver_brightness_mode";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_BRIGHTNESS_LEVEL =
                "battery_saver_brightness_level";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_START =
                "battery_saver_start";

        /**
         * @hide
         */
        public static final String BATTERY_SAVER_END =
                "battery_saver_end";

        /**
         * Name of an application package to be debugged.
         */
        public static final String DEBUG_APP = "debug_app";

        /**
         * If 1, when launching DEBUG_APP it will wait for the debugger before
         * starting user code.  If 0, it will run normally.
         */
        public static final String WAIT_FOR_DEBUGGER = "wait_for_debugger";

        /**
         * Control whether the process CPU usage meter should be shown.
         */
        public static final String SHOW_PROCESSES = "show_processes";

        /**
         * If 1, the activity manager will aggressively finish activities and
         * processes as soon as they are no longer needed.  If 0, the normal
         * extended lifetime is used.
         */
        public static final String ALWAYS_FINISH_ACTIVITIES =
                "always_finish_activities";

        /**
         * Use Dock audio output for media:
         *      0 = disabled
         *      1 = enabled
         * @hide
         */
        public static final String DOCK_AUDIO_MEDIA_ENABLED = "dock_audio_media_enabled";

        /**
         * Persisted safe headphone volume management state by AudioService
         * @hide
         */
        public static final String AUDIO_SAFE_VOLUME_STATE = "audio_safe_volume_state";

        /**
         * URL for tzinfo (time zone) updates
         * @hide
         */
        public static final String TZINFO_UPDATE_CONTENT_URL = "tzinfo_content_url";

        /**
         * URL for tzinfo (time zone) update metadata
         * @hide
         */
        public static final String TZINFO_UPDATE_METADATA_URL = "tzinfo_metadata_url";

        /**
         * URL for selinux (mandatory access control) updates
         * @hide
         */
        public static final String SELINUX_UPDATE_CONTENT_URL = "selinux_content_url";

        /**
         * URL for selinux (mandatory access control) update metadata
         * @hide
         */
        public static final String SELINUX_UPDATE_METADATA_URL = "selinux_metadata_url";

        /**
         * URL for sms short code updates
         * @hide
         */
        public static final String SMS_SHORT_CODES_UPDATE_CONTENT_URL =
                "sms_short_codes_content_url";

        /**
         * URL for sms short code update metadata
         * @hide
         */
        public static final String SMS_SHORT_CODES_UPDATE_METADATA_URL =
                "sms_short_codes_metadata_url";

        /**
         * URL for cert pinlist updates
         * @hide
         */
        public static final String CERT_PIN_UPDATE_CONTENT_URL = "cert_pin_content_url";

        /**
         * URL for cert pinlist updates
         * @hide
         */
        public static final String CERT_PIN_UPDATE_METADATA_URL = "cert_pin_metadata_url";

        /**
         * URL for intent firewall updates
         * @hide
         */
        public static final String INTENT_FIREWALL_UPDATE_CONTENT_URL =
                "intent_firewall_content_url";

        /**
         * URL for intent firewall update metadata
         * @hide
         */
        public static final String INTENT_FIREWALL_UPDATE_METADATA_URL =
                "intent_firewall_metadata_url";

        /**
         * SELinux enforcement status. If 0, permissive; if 1, enforcing.
         * @hide
         */
        public static final String SELINUX_STATUS = "selinux_status";

        /**
         * Developer setting to force RTL layout.
         * @hide
         */
        public static final String DEVELOPMENT_FORCE_RTL = "debug.force_rtl";

        /**
         * Milliseconds after screen-off after which low battery sounds will be silenced.
         *
         * If zero, battery sounds will always play.
         * Defaults to @integer/def_low_battery_sound_timeout in SettingsProvider.
         *
         * @hide
         */
        public static final String LOW_BATTERY_SOUND_TIMEOUT = "low_battery_sound_timeout";

        /**
         * Enable the QuickBoot feature
         *
         * @hide
         */
        public static final String ENABLE_QUICKBOOT = "enable_quickboot";

        /**
         * Settings to backup. This is here so that it's in the same place as the settings
         * keys and easy to update.
         *
         * These keys may be mentioned in the SETTINGS_TO_BACKUP arrays in System
         * and Secure as well.  This is because those tables drive both backup and
         * restore, and restore needs to properly whitelist keys that used to live
         * in those namespaces.  The keys will only actually be backed up / restored
         * if they are also mentioned in this table (Global.SETTINGS_TO_BACKUP).
         *
         * NOTE: Settings are backed up and restored in the order they appear
         *       in this array. If you have one setting depending on another,
         *       make sure that they are ordered appropriately.
         *
         * @hide
         */
        public static final String[] SETTINGS_TO_BACKUP = {
            BUGREPORT_IN_POWER_MENU,
            STAY_ON_WHILE_PLUGGED_IN,
            WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
            AUTO_TIME,
            AUTO_TIME_ZONE,
            POWER_SOUNDS_ENABLED,
            POWER_NOTIFICATIONS_ENABLED,
            POWER_NOTIFICATIONS_VIBRATE,
            POWER_NOTIFICATIONS_RINGTONE,
            DOCK_SOUNDS_ENABLED,
            USB_MASS_STORAGE_ENABLED,
            ENABLE_ACCESSIBILITY_GLOBAL_GESTURE_ENABLED,
            WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
            WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY,
            WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
            WIFI_NUM_OPEN_NETWORKS_KEPT,
            EMERGENCY_TONE,
            CALL_AUTO_RETRY,
            DOCK_AUDIO_MEDIA_ENABLED
        };

        // Populated lazily, guarded by class object:
        private static NameValueCache sNameValueCache = new NameValueCache(
                    SYS_PROP_SETTING_VERSION,
                    CONTENT_URI,
                    CALL_METHOD_GET_GLOBAL,
                    CALL_METHOD_PUT_GLOBAL);

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userHandle) {
            return sNameValueCache.getStringForUser(resolver, name, userHandle);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver,
                String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver,
                String name, String value, int userHandle) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Global.putString(name=" + name + ", value=" + value
                        + " for " + userHandle);
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userHandle);
        }

        /**
         * Construct the content URI for a particular name/value pair,
         * useful for monitoring changes with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI, or null if not present
         */
        public static Uri getUriFor(String name) {
            return getUriFor(CONTENT_URI, name);
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            String v = getString(cr, name);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws SettingNotFoundException {
            String v = getString(cr, name);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putString(cr, name, Integer.toString(value));
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            String valString = getString(cr, name);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws SettingNotFoundException {
            String valString = getString(cr, name);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a secure settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putString(cr, name, Long.toString(value));
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            String v = getString(cr, name);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single secure settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws SettingNotFoundException {
            String v = getString(cr, name);
            if (v == null) {
                throw new SettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putString(cr, name, Float.toString(value));
        }
    }

    /**
     * User-defined bookmarks and shortcuts.  The target of each bookmark is an
     * Intent URL, allowing it to be either a web page or a particular
     * application activity.
     *
     * @hide
     */
    public static final class Bookmarks implements BaseColumns
    {
        private static final String TAG = "Bookmarks";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/bookmarks");

        /**
         * The row ID.
         * <p>Type: INTEGER</p>
         */
        public static final String ID = "_id";

        /**
         * Descriptive name of the bookmark that can be displayed to the user.
         * If this is empty, the title should be resolved at display time (use
         * {@link #getTitle(Context, Cursor)} any time you want to display the
         * title of a bookmark.)
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String TITLE = "title";

        /**
         * Arbitrary string (displayed to the user) that allows bookmarks to be
         * organized into categories.  There are some special names for
         * standard folders, which all start with '@'.  The label displayed for
         * the folder changes with the locale (via {@link #getLabelForFolder}) but
         * the folder name does not change so you can consistently query for
         * the folder regardless of the current locale.
         *
         * <P>Type: TEXT</P>
         *
         */
        public static final String FOLDER = "folder";

        /**
         * The Intent URL of the bookmark, describing what it points to.  This
         * value is given to {@link android.content.Intent#getIntent} to create
         * an Intent that can be launched.
         * <P>Type: TEXT</P>
         */
        public static final String INTENT = "intent";

        /**
         * Optional shortcut character associated with this bookmark.
         * <P>Type: INTEGER</P>
         */
        public static final String SHORTCUT = "shortcut";

        /**
         * The order in which the bookmark should be displayed
         * <P>Type: INTEGER</P>
         */
        public static final String ORDERING = "ordering";

        private static final String[] sIntentProjection = { INTENT };
        private static final String[] sShortcutProjection = { ID, SHORTCUT };
        private static final String sShortcutSelection = SHORTCUT + "=?";

        /**
         * Convenience function to retrieve the bookmarked Intent for a
         * particular shortcut key.
         *
         * @param cr The ContentResolver to query.
         * @param shortcut The shortcut key.
         *
         * @return Intent The bookmarked URL, or null if there is no bookmark
         *         matching the given shortcut.
         */
        public static Intent getIntentForShortcut(ContentResolver cr, char shortcut)
        {
            Intent intent = null;

            Cursor c = cr.query(CONTENT_URI,
                    sIntentProjection, sShortcutSelection,
                    new String[] { String.valueOf((int) shortcut) }, ORDERING);
            // Keep trying until we find a valid shortcut
            try {
                while (intent == null && c.moveToNext()) {
                    try {
                        String intentURI = c.getString(c.getColumnIndexOrThrow(INTENT));
                        intent = Intent.parseUri(intentURI, 0);
                    } catch (java.net.URISyntaxException e) {
                        // The stored URL is bad...  ignore it.
                    } catch (IllegalArgumentException e) {
                        // Column not found
                        Log.w(TAG, "Intent column not found", e);
                    }
                }
            } finally {
                if (c != null) c.close();
            }

            return intent;
        }

        /**
         * Add a new bookmark to the system.
         *
         * @param cr The ContentResolver to query.
         * @param intent The desired target of the bookmark.
         * @param title Bookmark title that is shown to the user; null if none
         *            or it should be resolved to the intent's title.
         * @param folder Folder in which to place the bookmark; null if none.
         * @param shortcut Shortcut that will invoke the bookmark; 0 if none. If
         *            this is non-zero and there is an existing bookmark entry
         *            with this same shortcut, then that existing shortcut is
         *            cleared (the bookmark is not removed).
         * @return The unique content URL for the new bookmark entry.
         */
        public static Uri add(ContentResolver cr,
                                           Intent intent,
                                           String title,
                                           String folder,
                                           char shortcut,
                                           int ordering)
        {
            // If a shortcut is supplied, and it is already defined for
            // another bookmark, then remove the old definition.
            if (shortcut != 0) {
                cr.delete(CONTENT_URI, sShortcutSelection,
                        new String[] { String.valueOf((int) shortcut) });
            }

            ContentValues values = new ContentValues();
            if (title != null) values.put(TITLE, title);
            if (folder != null) values.put(FOLDER, folder);
            values.put(INTENT, intent.toUri(0));
            if (shortcut != 0) values.put(SHORTCUT, (int) shortcut);
            values.put(ORDERING, ordering);
            return cr.insert(CONTENT_URI, values);
        }

        /**
         * Return the folder name as it should be displayed to the user.  This
         * takes care of localizing special folders.
         *
         * @param r Resources object for current locale; only need access to
         *          system resources.
         * @param folder The value found in the {@link #FOLDER} column.
         *
         * @return CharSequence The label for this folder that should be shown
         *         to the user.
         */
        public static CharSequence getLabelForFolder(Resources r, String folder) {
            return folder;
        }

        /**
         * Return the title as it should be displayed to the user. This takes
         * care of localizing bookmarks that point to activities.
         *
         * @param context A context.
         * @param cursor A cursor pointing to the row whose title should be
         *        returned. The cursor must contain at least the {@link #TITLE}
         *        and {@link #INTENT} columns.
         * @return A title that is localized and can be displayed to the user,
         *         or the empty string if one could not be found.
         */
        public static CharSequence getTitle(Context context, Cursor cursor) {
            int titleColumn = cursor.getColumnIndex(TITLE);
            int intentColumn = cursor.getColumnIndex(INTENT);
            if (titleColumn == -1 || intentColumn == -1) {
                throw new IllegalArgumentException(
                        "The cursor must contain the TITLE and INTENT columns.");
            }

            String title = cursor.getString(titleColumn);
            if (!TextUtils.isEmpty(title)) {
                return title;
            }

            String intentUri = cursor.getString(intentColumn);
            if (TextUtils.isEmpty(intentUri)) {
                return "";
            }

            Intent intent;
            try {
                intent = Intent.parseUri(intentUri, 0);
            } catch (URISyntaxException e) {
                return "";
            }

            PackageManager packageManager = context.getPackageManager();
            ResolveInfo info = packageManager.resolveActivity(intent, 0);
            return info != null ? info.loadLabel(packageManager) : "";
        }
    }

    /**
     * Returns the device ID that we should use when connecting to the mobile gtalk server.
     * This is a string like "android-0x1242", where the hex string is the Android ID obtained
     * from the GoogleLoginService.
     *
     * @param androidId The Android ID for this device.
     * @return The device ID that should be used when connecting to the mobile gtalk server.
     * @hide
     */
    public static String getGTalkDeviceId(long androidId) {
        return "android-" + Long.toHexString(androidId);
    }
}
