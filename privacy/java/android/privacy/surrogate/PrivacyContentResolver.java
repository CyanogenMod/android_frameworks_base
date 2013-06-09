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
import android.content.IContentProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.provider.Browser;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * Provides privacy handling for {@link android.content.ContentResolver}
 * @author Svyatoslav Hresyk 
 * {@hide}
 */
public final class PrivacyContentResolver {
    
    private static final String TAG = "PrivacyContentResolver";

    private static final String SMS_CONTENT_URI_AUTHORITY = "sms";
    private static final String MMS_CONTENT_URI_AUTHORITY = "mms";
    private static final String MMS_SMS_CONTENT_URI_AUTHORITY = "mms-sms";
    
    private static PrivacySettingsManager pSetMan;
    
    /**
     * Returns a dummy database cursor if access is restricted by privacy settings
     * @param uri
     * @param context
     * @param realCursor
     */
    public static Cursor enforcePrivacyPermission(Uri uri, String[] projection, Context context, Cursor realCursor) throws RemoteException {
//    public static Cursor enforcePrivacyPermission(Uri uri, Context context, Cursor realCursor) {
        if (uri != null) {
            if (pSetMan == null) pSetMan = (PrivacySettingsManager) context.getSystemService("privacy");
            String packageName = context.getPackageName();
            int uid = Binder.getCallingUid();
            PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
            String auth = uri.getAuthority();
            String output_label = "[real]";
            Cursor output = realCursor;
            if (auth != null) {
                if (auth.equals(android.provider.Contacts.AUTHORITY) || auth.equals(ContactsContract.AUTHORITY)) {

                    if (pSet != null) {
                        if (pSet.getContactsSetting() == PrivacySettings.EMPTY) {
                            output_label = "[empty]";
                            output = new PrivacyCursor();
                            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_CONTACTS, null, pSet);
                        } else if (pSet.getContactsSetting() == PrivacySettings.CUSTOM && 
                                uri.toString().contains(ContactsContract.Contacts.CONTENT_URI.toString())) {
//                            Log.d(TAG, "enforcePrivacyPermission - URI: " + uri.toString() + " " + uri.getAuthority() + " " + uri.getEncodedAuthority() + " " + uri.getEncodedFragment() + " " + uri.getEncodedPath() + " " + uri.getEncodedQuery() + " " + uri.getEncodedSchemeSpecificPart() + " " + uri.getEncodedUserInfo() + " " + uri.getFragment() + " " + uri.getPath());
//                            Log.d(TAG, "enforcePrivacyPermission - projection: " + arrayToString(projection) + " selection: " + selection + " selectionArgs: " + arrayToString(selectionArgs));
//                            Log.d(TAG, "enforcePrivacyPermission - cursor entries: " + output.getCount());
                            
                            boolean idFound = false;
                            if (projection != null) {
                                for (String p : projection) {
                                    if (p.equals(ContactsContract.Contacts._ID)) {
                                        idFound = true;
                                        break;
                                    }
                                }
                                
//                                if (!idFound) { // add ID to projection
//                                    String[] newProjection = new String[projection.length + 1];
//                                    System.arraycopy(projection, 0, newProjection, 0, projection.length);
//                                    newProjection[projection.length] = ContactsContract.Contacts._ID;
//                                    projection = newProjection;
//                                }
                            }
                            
                            if (!idFound) {
                                output = new PrivacyCursor();
                            } else {
//                            Log.d(TAG, "enforcePrivacyPermission - new projection: " + arrayToString(projection) + " selection: " + selection + " selectionArgs: " + arrayToString(selectionArgs));
                            
                            // re-query
//                            output = provider.query(uri, projection, selection, selectionArgs, sortOrder);
//                            Log.d(TAG, "enforcePrivacyPermission - new cursor entries: " + output.getCount());
                                output = new PrivacyCursor(output, pSet.getAllowedContacts());
                            }
                            pSetMan.notification(packageName, uid, PrivacySettings.CUSTOM, PrivacySettings.DATA_CONTACTS, null, pSet);
                        } else { // REAL
                            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_CONTACTS, null, pSet);
                        }
                    }
                    
                } else if (auth.equals(CalendarContract.AUTHORITY)) {
                    
                    if (pSet != null && pSet.getCalendarSetting() == PrivacySettings.EMPTY) {
                        output_label = "[empty]";
                        output = new PrivacyCursor();
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_CALENDAR, null, pSet);
                    } else {
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_CALENDAR, null, pSet);
                    }
                    
                } else if (auth.equals(MMS_CONTENT_URI_AUTHORITY)) {
                    
                    if (pSet != null && pSet.getMmsSetting() == PrivacySettings.EMPTY) {
                        output_label = "[empty]";
                        output = new PrivacyCursor();
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_MMS, null, pSet);
                    } else {
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_MMS, null, pSet);
                    }
                    
                } else if (auth.equals(SMS_CONTENT_URI_AUTHORITY)) {
                    
                    if (pSet != null && pSet.getSmsSetting() == PrivacySettings.EMPTY) {
                        output_label = "[empty]";
                        output = new PrivacyCursor();
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_SMS, null, pSet);
                    } else {
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_SMS, null, pSet);
                    }
                // all messages, sms and mms
                } else if (auth.equals(MMS_SMS_CONTENT_URI_AUTHORITY) || 
                        auth.equals("mms-sms-v2") /* htc specific, accessed by system messages application */) { 
                    
                    // deny access if access to either sms, mms or both is restricted by privacy settings
                    if (pSet != null && (pSet.getMmsSetting() == PrivacySettings.EMPTY || 
                            pSet.getSmsSetting() == PrivacySettings.EMPTY)) {
                        output_label = "[empty]";
                        output = new PrivacyCursor();
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_MMS_SMS, null, pSet);
                    } else {
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_MMS_SMS, null, pSet);
                    }

                } else if (auth.equals(CallLog.AUTHORITY)) {
                    
                    if (pSet != null && pSet.getCallLogSetting() == PrivacySettings.EMPTY) {
                        output_label = "[empty]";
                        output = new PrivacyCursor();
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_CALL_LOG, null, pSet);
                    } else {
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_CALL_LOG, null, pSet);
                    }

                } else if (auth.equals(Browser.BOOKMARKS_URI.getAuthority())) {
                    
                    if (pSet != null && pSet.getBookmarksSetting() == PrivacySettings.EMPTY) {
                        output_label = "[empty]";
                        output = new PrivacyCursor();
                        pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_BOOKMARKS, null, pSet);
                    } else {
                        pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_BOOKMARKS, null, pSet);
                    }
                    
                }
            }
//            Log.d(TAG, "query - " + packageName + " (" + uid + ") auth: " + auth + " output: " + output_label);
            return output;
        }
        return realCursor;
    }
    
    private static String arrayToString(String[] array) {
        StringBuffer sb = new StringBuffer();
        if (array != null) for (String bla : array) sb.append("[" + bla + "]");
        else return "";
        return sb.toString();
    }
    /**
     * This method is especially for faking android_id if google wants to read it in their privacy database
     * @author CollegeDev
     * @param uri
     * @param projection
     * @param context
     * @param realCursor
     */
    public static Cursor enforcePrivacyPermission(Uri uri, String[] projection, Context context, Cursor realCursor, boolean google_access) throws RemoteException {
	if (uri != null) {
            if (pSetMan == null) pSetMan = (PrivacySettingsManager) context.getSystemService("privacy");
            String packageName = context.getPackageName();
            int uid = Binder.getCallingUid();
            PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
            String auth = uri.getAuthority();
            String output_label = "[real]";
            Cursor output = realCursor;
            if (auth != null && auth.equals("com.google.android.gsf.gservices")) {
		
		if (pSet != null && pSet.getSimInfoSetting() != PrivacySettings.REAL){
			int actual_pos = realCursor.getPosition();
			int forbidden_position = -1;
			try{
				for(int i=0;i<realCursor.getCount();i++){
					realCursor.moveToNext();
					if(realCursor.getString(0).equals("android_id")){
						forbidden_position = realCursor.getPosition();
						break;
					}
				}
			} catch (Exception e){
				Log.e(TAG,"something went wrong while getting blocked permission for android id");
			} finally{
				realCursor.moveToPosition(actual_pos);
				if(forbidden_position == -1) {Log.i(TAG,"now we return real cursor, because forbidden_pos is -1"); return output;} //give realcursor, because there is no android_id to block
			}
			Log.i(TAG,"now blocking google access to android id and give fake cursor. forbidden_position: " + forbidden_position);
			output_label = "[fake]";
			output = new PrivacyCursor(realCursor,forbidden_position);	
			pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_NETWORK_INFO_SIM, null, pSet);
		} else {
			Log.i(TAG,"google is allowed to get real cursor");
			pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_NETWORK_INFO_SIM, null, pSet);
		}
	    }
	    return output;
	}
	return realCursor;   
    }


















}
