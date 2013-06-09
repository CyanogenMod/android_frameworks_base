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

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.Random;

import java.math.BigInteger;
import java.util.Map;

/**
 * Dummy database cursor. Used by {@link android.privacy.surrogate.PrivacyContentResolver} 
 * when access should be blocked without crashing the calling application (for this purpose none 
 * of the methods returns null) or for fine-granular control of access to individual database entries.
 * The latter may produce inconsistencies from the applicatin point of view based on getPosition()
 * and moveToPosition().
 * {@hide}
 */
public class PrivacyCursor implements Cursor {
    
    private Cursor realCursor;
    
    private int[] allowedIds;

    private int blockedColumnAndroidID; 

    private static final String[] mask = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};

    private boolean googleCursor;
    
    private int[] allowedIdMapping;
    
    private final static String TAG = "PrivacyCursor";
    
    public PrivacyCursor() {
    }
    
    /**
     * 
     * @param realCursor may not be null
     * @param allowedIds
     */
    public PrivacyCursor(Cursor realCursor, int[] allowedIds) {
        this.realCursor = (allowedIds == null || allowedIds.length == 0) ? null : realCursor;
        this.allowedIds = allowedIds;
        if (this.realCursor != null) {
            int currentPos = this.realCursor.getPosition();
            this.allowedIdMapping = new int[allowedIds.length];
            int i = 0;
            while (this.realCursor.moveToNext()) {
                if (isAllowed(this.realCursor)) {
                    allowedIdMapping[i] = this.realCursor.getPosition();
                    i++;
                }
            }
            this.realCursor.moveToPosition(currentPos);
        }
    }
    /*
     * This constructor is only for android_id	 
     * @param blockedColumnAndroidID pass -11 if should block all, or the columnIndex where android_id is!
     */
    public PrivacyCursor(Cursor realCursor, int blockedColumnAndroidID){
	if(blockedColumnAndroidID == -11)
		this.realCursor = null;
	else
		this.realCursor = realCursor;
	this.blockedColumnAndroidID = blockedColumnAndroidID;
	this.googleCursor = true;
	Log.i(TAG,"constructor is ready for google cursor! forbidden_position: " + blockedColumnAndroidID);
    }
     
    /**
     * just create fakeID
     * @return fake ID or null if something went wrong
     */
    private String getFakeID(){
	try{
		Random rand = new Random();
		String output = "";
		for(int i=0; i < 16;i++){
			output += mask[rand.nextInt(15)];
		}
		return output;
	} catch (Exception e){
		Log.e(TAG,"something went wrong with creating fake ID");
		return null;
	}
    }

    @Override
    public void close() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.close();
    }

    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.copyStringToBuffer(columnIndex, buffer);
    }

    @Override
    public void deactivate() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.deactivate();
    }

    @Override
    public byte[] getBlob(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getBlob(columnIndex);
        return new byte[0];
    }

    @Override
    public int getColumnCount() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getColumnCount();
        return 0;
    }

    @Override
    public int getColumnIndex(String columnName) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getColumnIndex(columnName);
        return -1;
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getColumnIndexOrThrow(columnName);
        return -1;
    }

    @Override
    public String getColumnName(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getColumnName(columnIndex);
        return "";
    }

    @Override
    public String[] getColumnNames() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getColumnNames();
        return new String[] { "" };
    }

    @Override
    public int getCount() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            Log.d(TAG, "getCount: " + allowedIdMapping.length);
            return allowedIdMapping.length;
        }
	else if (realCursor != null)
	    return realCursor.getCount();
        return 0;
    }

    @Override
    public double getDouble(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getDouble(columnIndex);
        return 0;
    }

    @Override
    public Bundle getExtras() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getExtras();
        return new Bundle();
    }

    @Override
    public float getFloat(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getFloat(columnIndex);
        return 0;
    }

    @Override
    public int getInt(int columnIndex) {
        if (realCursor != null) {
            int result = realCursor.getInt(columnIndex);
//            Log.d(TAG, "getInt - columnIndex: " + columnIndex + " name: " + realCursor.getColumnName(columnIndex) + " result: " + result);
            return result;
        }
        return 0;
    }

    @Override
    public long getLong(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getLong(columnIndex);
        return 0;
    }

    @Override
    public int getPosition() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            int result = getMappedPos(realCursor.getPosition());
            Log.d(TAG, "getPosition - mapped position: " + result + " real position: " + realCursor.getPosition());
            return result;
        }
	else if (realCursor != null)
	    return realCursor.getPosition();
        return -1;
    }

    @Override
    public short getShort(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getShort(columnIndex);
        return 0;
    }

    @Override
    public String getString(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
	if(googleCursor && (columnIndex == 1) && (realCursor.getPosition() == blockedColumnAndroidID)){	
		Log.i(TAG,"google tries to get android_id with getString()");
		String id = getFakeID();
		if(id != null){
			try{
				BigInteger value = new BigInteger(id,16);
				String fakeValue = String.valueOf(value);
				Log.i(TAG,"returned : " + fakeValue + "(String) as fake android id");
				return fakeValue;
			}
			catch(Exception e){
				e.printStackTrace();
				Log.i(TAG,"returned \"\" as android_id");
				return "";
			}
		}
		else{Log.i(TAG,"returned \"\" as android_id"); return "";}
	}
        if (realCursor != null) return realCursor.getString(columnIndex);
        return "";
    }

    @Override
    public int getType(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getType(columnIndex);
        return realCursor.FIELD_TYPE_NULL;
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.getWantsAllOnMoveCalls();
        return false;
    }

    @Override
    public boolean isAfterLast() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.isAfterLast();
        return false;
    }

    @Override
    public boolean isBeforeFirst() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.isBeforeFirst();
        return false;
    }

    @Override
    public boolean isClosed() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.isClosed();
        return false;
    }

    @Override
    public boolean isFirst() {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
//            Log.d(TAG, "isFirst");
            if (realCursor.getPosition() == allowedIdMapping[0]) return true;
        }
	else if(realCursor != null)
	    return realCursor.isFirst();
        return false;
    }

    @Override
    public boolean isLast() {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
//            Log.d(TAG, "isLast");
            if (realCursor.getPosition() == allowedIdMapping[allowedIdMapping.length - 1]) return true;
        }
	else if (realCursor != null)
	    return realCursor.isLast();
        return false;
    }

    @Override
    public boolean isNull(int columnIndex) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.isNull(columnIndex);
        return false;
    }

    @Override
    public boolean move(int offset) {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            Log.d(TAG, "move - offset: " + offset);
            try {
                int realPos = allowedIdMapping[getMappedPos(realCursor.getPosition()) + offset];
                boolean result = realCursor.moveToPosition(realPos);
//                Log.d(TAG, "move - position: " + realCursor.getPosition() + " result: " + result);
                return result;
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
	else if(realCursor != null) //here we go with our google cursor
		realCursor.move(offset);
        return false;
    }

    @Override
    public boolean moveToFirst() {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            boolean result = realCursor.moveToPosition(allowedIdMapping[0]); 
//            Log.d(TAG, "moveToFirst - position: " + allowedIdMapping[0] + " result: " + result);
            return result;
        }
	else if (realCursor != null)
		return realCursor.moveToFirst();
        return false;
    }

    @Override
    public boolean moveToLast() {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            boolean result = realCursor.moveToPosition(allowedIdMapping[allowedIdMapping.length - 1]);
//            Log.d(TAG, "moveToLast - real position: " + realCursor.getPosition() + " result: " + result);
            return result;
        }
	else if(realCursor != null)
		return realCursor.moveToLast();
        return false;
    }

    @Override
    public boolean moveToNext() {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            if (!realCursor.moveToNext()) return false;
            boolean result = true;
            while (result == true && !isAllowed(realCursor)) result = realCursor.moveToNext();
//            Log.d(TAG, "moveToNext - real position: " + realCursor.getPosition() + " result: " + result);
            return result;
        }
	else if(realCursor != null)
		return realCursor.moveToNext();
        return false;
    }

    @Override
    public boolean moveToPosition(int position) {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            try {
                boolean result = realCursor.moveToPosition(allowedIdMapping[position]);
//                Log.d(TAG, "moveToPosition - real position: " + realCursor.getPosition() + " result: " + result);
                return result;
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
	else if(realCursor != null)
		return realCursor.moveToPosition(position);
        return false;
    }

    @Override
    public boolean moveToPrevious() {
        if (realCursor != null && allowedIdMapping != null && !googleCursor) {
            if (!realCursor.moveToPrevious()) return false;
            boolean result = true;
            while (result == true && !isAllowed(realCursor)) result = realCursor.moveToPrevious();
//            Log.d(TAG, "moveToPrevious - real position: " + realCursor.getPosition() + " result: " + result);
            return result;
        }
	else if(realCursor != null)
		return realCursor.moveToPrevious();
        return false;
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.registerContentObserver(observer);        
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.registerDataSetObserver(observer);        
    }

    @Override
    public boolean requery() {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.requery();
        return false;
    }

    @Override
    public Bundle respond(Bundle extras) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) return realCursor.respond(extras);        
        return new Bundle();
    }

    @Override
    public void setNotificationUri(ContentResolver cr, Uri uri) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.setNotificationUri(cr, uri);        
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.unregisterContentObserver(observer);        
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
//        Log.d(TAG, new Exception().getStackTrace()[0].getMethodName());
        if (realCursor != null) realCursor.unregisterDataSetObserver(observer);
    }

    private int getContactId(Cursor c) {
        int colIndex = c.getColumnIndex(ContactsContract.Contacts._ID);
        int result = -1;
        if (colIndex != -1) result = c.getInt(colIndex); 
//        Log.d(TAG, "getContactId - colIndex: " + colIndex + " id: " + result);
        return result;
    }
    
    private boolean isAllowed(int id) {
        boolean result = false;
        for (int i : allowedIds) {
            if (id == i) {
                result = true;
                break;
            }
        }
//        Log.d(TAG, "isAllowed - id: " + id + " result: " + result);
        return result;
    }
    
    private boolean isAllowed(Cursor realCursor) {
        return isAllowed(getContactId(realCursor));
    }
    
    /**
     * TODO: switch to HashMap etc to speed this up?
     * @param realPos
     * @return
     */
    private int getMappedPos(int realPos) {
        for (int i = 0; i < allowedIdMapping.length; i++) {
            if (allowedIdMapping[i] == realPos) return i;
        }
        return -1;
    }
	

}
