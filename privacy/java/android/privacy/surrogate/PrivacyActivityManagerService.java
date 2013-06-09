/**
 * Copyright (C) 2012 Svyatoslav Hresyk
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses>.
 */

package android.privacy.surrogate;

import android.content.Context;
import android.content.Intent;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.privacy.PrivacySettingsManagerService;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;

/**
 * Provides privacy handling for {@link com.android.server.am.ActivityManagerService}
 * @author Svyatoslav Hresyk
 * {@hide}
 */
public final class PrivacyActivityManagerService {
    
    private static final String TAG = "PrivacyActivityManagerService";
    
    private static final String SMS_RECEIVED_ACTION_INTENT = "android.provider.Telephony.SMS_RECEIVED";
    private static final String WAP_PUSH_RECEIVED_INTENT = "android.provider.Telephony.WAP_PUSH_RECEIVED";
    private static final String DATA_SMS_RECEIVED_INTENT = "android.intent.action.DATA_SMS_RECEIVED";
    
    private static PrivacySettingsManager pSetMan;
    
    private static Intent tmpIn;
    private static long tmpInHash = 0;
    private static int tmpInReceivers = 0;
    
    private static Intent tmpOut;
    private static long tmpOutHash = 0;
    private static int tmpOutReceivers = 0;
    
    private static Intent tmpSms;
    private static long tmpSmsHash = 0;
    private static int tmpSmsReceivers = 0;
    
    private static Intent tmpMms;
    private static long tmpMmsHash = 0;
    private static int tmpMmsReceivers = 0;
    
    private static long tmpPackageAddedHash = 0;
    
    /**
     * Intercepts broadcasts and replaces the broadcast contents according to 
     * privacy permissions
     * @param packageName may not be null
     * @param context if you use JellyBean, just pass NULL because all is splittet up to BroadcastQueue.java!!
     * @param uid must be >= 0
     * @param intent intent.getAction() may not return null
     */
    public static void enforcePrivacyPermission(String packageName, int uid, Intent intent, Context context, int receivers) {
        if (pSetMan == null && context != null) pSetMan = (PrivacySettingsManager) context.getSystemService("privacy");
	if (pSetMan == null && context == null) pSetMan = new PrivacySettingsManager(null, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy"))); //we can pass null here
        PrivacySettings pSet;
        String action = intent.getAction();
        String output;
        // outgoing call
        if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            pSet = pSetMan.getSettings(packageName, uid);
            output = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            
            // store the original version to supply real values to trusted applications
            // since Android sends the same intent to multiple receivers
            if (tmpOutHash != hashCode(intent)) {
                tmpOut = (Intent)intent.clone();
                tmpOutHash = hashCode(intent);
                tmpOutReceivers = receivers;
            }
            
            try {
                if (pSet != null && pSet.getOutgoingCallsSetting() != PrivacySettings.REAL) {
                    output = "";
                    intent.putExtra(Intent.EXTRA_PHONE_NUMBER, output);
                    pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_OUTGOING_CALL, null, pSet);
                } else if (tmpOutHash == hashCode(intent)) {
                    // if this intent was stored before, get the real value since it could have been modified
                    output = tmpOut.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                    intent.putExtra(Intent.EXTRA_PHONE_NUMBER, output);
                    pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_OUTGOING_CALL, null, pSet);
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to enforce intent broadcast permission", e);
            }
            
            if (tmpOutReceivers > 1) {
                tmpOutReceivers--;
            } else { // free memory after all receivers have been served
                tmpOut = null;
            }
            
//            Log.d(TAG, "broadcasting intent " + action + " - " + packageName + " (" + uid + ") output: " + output);
        // incoming call
        } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                // the EXTRA_INCOMING_NUMBER is NOT only present when state is EXTRA_STATE_RINGING
                // Android documentation is WRONG; the EXTRA_INCOMING_NUMBER will also be there when hanging up (IDLE?)
                /* && intent.getStringExtra(TelephonyManager.EXTRA_STATE).equals(TelephonyManager.EXTRA_STATE_RINGING)*/) {
            output = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            // don't do anything if no incoming phone number is broadcasted
            if (output == null || output.isEmpty()) return;
            
            pSet = pSetMan.getSettings(packageName, uid);
            
            if (tmpInHash != hashCode(intent)) {
                tmpIn = (Intent)intent.clone();
                tmpInHash = hashCode(intent);
                tmpInReceivers = receivers;
            }
            
            try {
                if (pSet != null && pSet.getIncomingCallsSetting() != PrivacySettings.REAL) {
                    output = "";
                    intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, output);
                    pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_INCOMING_CALL, null, pSet);
                } else if (tmpInHash == hashCode(intent)) {
                    output = tmpIn.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                    intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, output);
                    pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_INCOMING_CALL, null, pSet);
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to enforce intent broadcast permission", e);
            }
            
            if (tmpInReceivers > 1) {
                tmpInReceivers--;
            } else { // free memory after all receivers have been served
                tmpIn = null;
            }
            
//            Log.d(TAG, "broadcasting intent " + action + " - " + packageName + " (" + uid + ") output: " + output);
        // incoming SMS
        } else if (action.equals(SMS_RECEIVED_ACTION_INTENT)) {
            pSet = pSetMan.getSettings(packageName, uid);
            output = "[real]";
//            Log.d(TAG, "package: " + packageName + " uid: " + uid);
            
            Object[] o = ((Object[])intent.getSerializableExtra("pdus"));
            byte[] b = o != null ? (byte[])o[0] : null;
            
            if (tmpSmsHash != hashCode(intent)) {
                tmpSms = (Intent)intent.clone();
                tmpSmsHash = hashCode(intent);
                tmpSmsReceivers = receivers;
//                Log.d(TAG, "new intent; saving copy: receivers: " + receivers + " hash: " + tmpSmsHash + " " + 
//                        "pdu number: " + (o != null ? o.length : "null") + " " + 
//                        "1st pdu length: " + (b != null ? b.length : "null"));
            } else {
//                Log.d(TAG, "known intent; hash: " + hashCode(intent) + " remaining receivers: " + tmpSmsReceivers);
            }
            
            try {
                if (pSet != null && pSet.getSmsSetting() != PrivacySettings.REAL) {
                    output = "[empty]";
                    
                    Object[] emptypdusObj = new Object[1];
                    emptypdusObj[0] = (Object) new byte[] {0,32,1,-127,-16,0,0,17,-112,1,48,34,34,-128,1,32};
                    intent.putExtra("pdus", emptypdusObj);
                    
//                    Log.d(TAG, "permission denied, replaced pdu; pdu number: " + 
//                            (o != null ? o.length : "null") + " " +
//                        "1st pdu length:" + (b != null ? b.length : "null"));
                    pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_SMS, null, pSet);
                } else if (tmpSmsHash == hashCode(intent)) {
                    intent.putExtra("pdus", tmpSms.getSerializableExtra("pdus"));
                    
                    o = ((Object[])intent.getSerializableExtra("pdus"));
                    b = o != null ? (byte[])o[0] : null;
//                    Log.d(TAG, "permission granted, inserting saved pdus; pdu number: " + 
//                            (o != null ? o.length : "null") + " " +
//                            "1st pdu length:" + (b != null ? b.length : "null"));
                    pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_SMS, null, pSet);
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to enforce intent broadcast permission", e);
            }
            
            if (tmpSmsReceivers > 1) {
                tmpSmsReceivers--;
            } else { // free memory after all receivers have been served
//                Log.d(TAG, "removing intent with hash: " + tmpSmsHash);
                tmpSms = null;
            }            
            
//            Log.d(TAG, "broadcasting intent " + action + " - " + packageName + " (" + uid + ") output: " + output);
        // incoming MMS
        } else if (action.equals(WAP_PUSH_RECEIVED_INTENT) ||
                action.equals(DATA_SMS_RECEIVED_INTENT)) {
            pSet = pSetMan.getSettings(packageName, uid);
            output = "[real]";
            
            Object[] o = ((Object[])intent.getSerializableExtra("pdus"));
            byte[] b = o != null ? (byte[])o[0] : null;
            
            // TODO: remove unnecessary receivers count
            if (tmpMmsHash != hashCode(intent)) {
                tmpMms = (Intent)intent.clone();
                tmpMmsHash = hashCode(intent);
                tmpMmsReceivers = receivers;
//                Log.d(TAG, "new intent; saving copy: receivers: " + receivers + " hash: " + tmpMmsHash + " " + 
//                        "pdu number: " + (o != null ? o.length : "null") + " " + 
//                        "1st pdu length: " + (b != null ? b.length : "null"));
            } else {
//                Log.d(TAG, "known intent; hash: " + hashCode(intent) + " remaining receivers: " + tmpMmsReceivers);
            }
            
            try {
                if (pSet != null && pSet.getMmsSetting() != PrivacySettings.REAL) {
                    output = "[empty]";
                    
                    Object[] emptypdusObj = new Object[1];
                    emptypdusObj[0] = (Object) new byte[] {0,32,1,-127,-16,0,0,17,-112,1,48,34,34,-128,1,32};
                    intent.putExtra("pdus", emptypdusObj);
                    pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_MMS, null, pSet);
                } else if (tmpMmsHash == hashCode(intent)) {
                    intent.putExtra("pdus", tmpMms.getSerializableExtra("pdus"));
                    
                    o = ((Object[])intent.getSerializableExtra("pdus"));
                    b = o != null ? (byte[])o[0] : null;
//                    Log.d(TAG, "permission granted, inserting saved pdus; pdu number: " + 
//                            (o != null ? o.length : "null") + " " +
//                            "1st pdu length:" + (b != null ? b.length : "null"));
                    pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_MMS, null, pSet);
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to enforce intent broadcast permission", e);
            }
            
            if (tmpMmsReceivers > 1) {
                tmpMmsReceivers--;
            } else { // free memory after all receivers have been served
//                Log.d(TAG, "removing intent with hash: " + tmpMmsHash);
                tmpMms = null;
            }
            
//            Log.d(TAG, "broadcasting intent " + action + " - " + packageName + " (" + uid + ") output: " + output);
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            pSetMan.setBootCompleted();
            
            pSet = pSetMan.getSettings(packageName);
            
            if (pSet != null && pSet.getIntentBootCompletedSetting() != PrivacySettings.REAL) {
                //no notification since all applications will receive this -> spam
                intent.setAction("catchBootComplete");
		//Log.i(TAG,"package: " + packageName + " blocked INTENT_BOOT_COMPLETE");
                //intent.setPackage("com.android.privacy.pdroid.extension");
                pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_INTENT_BOOT_COMPLETED, null, null);
            } else {
                intent.setAction(Intent.ACTION_BOOT_COMPLETED);
		//Log.i(TAG,"package: " + packageName + " allowed INTENT_BOOT_COMPLETE");
                pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_INTENT_BOOT_COMPLETED, null, null);
            }
        } else if (action.equals(Intent.ACTION_PACKAGE_ADDED)) {
//            Log.d(TAG, "enforcePrivacyPermission - ACTION_PACKAGE_ADDED; receivers: " + receivers);
            
            // update privacy settings; only do this once for a single Intent
            if (tmpPackageAddedHash != hashCode(intent)) {
                tmpPackageAddedHash = hashCode(intent);
                
                String addedPackageName = intent.getData().getSchemeSpecificPart();
                int addedUid = intent.getExtras().getInt(Intent.EXTRA_UID);
//                Log.d(TAG, "enforcePrivacyPermission - installed package " + addedPackageName + " " + addedUid);
                pSet = pSetMan.getSettings(addedPackageName, addedUid);
                // the settings in the privacy DB contain a different UID
                if (pSet != null && pSet.getUid() != addedUid) { // update the UID
//                    Log.i(TAG, "installed package UID (" + addedUid + ") doesn't match privacy settings UID (" + pSet.getUid() + "); updating...");
                    pSet.setUid(addedUid);
                    /*boolean updateSuccess = */pSetMan.saveSettings(pSet);
//                    if (!updateSuccess) Log.w(TAG, "could not update privacy settings UID; purge needed");
                }
            }
        }
    }
    
    private static long hashCode(Intent intent) {
        long privacyHash = intent.getLongExtra("privacy_hash", 0);
        if (privacyHash == 0) {
            privacyHash = intent.filterHashCode() + System.currentTimeMillis();
            intent.putExtra("privacy_hash", privacyHash);
        }
        return privacyHash;
    }
}
