/*
* Modification Copyright (C) 2014 AOSB Project
* Author Simon Lightfoot @slightfoot
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.systemui.recent.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.graphics.Bitmap;

public class Person implements Parcelable {
    private final int    mId;
    private final int    mContactID;
    private final Bitmap mIcon;
    private final String mName;
    private final String mStatus;
	
	public Person(int id, Bitmap icon, String name, String status, int ContactID) {
        mId     = id;
        mIcon   = icon;
        mName   = name;
        mStatus = status;
        mContactID = ContactID;
    }
	
    public int getId() {
        return mId;
    }

    public int getContactID() {
        return mContactID;
    }
	
	public Bitmap getIcon() {
        return mIcon;
    }
	
    public String getName() {
        return mName;
    }
	
    public String getStatus() {
        return mStatus;
    }
	
    // -- Parcel
	
    private Person(Parcel in) {

        mId     = in.readInt();
        mContactID = in.readInt();
        mIcon   = in.readParcelable(getClass().getClassLoader());
        mName   = in.readString();
        mStatus = in.readString();
    }
	
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeInt(mContactID);
        if ( mIcon != null ) dest.writeParcelable(mIcon, flags);
        dest.writeString(mName);
        dest.writeString(mStatus);
    }
	
	// -- Parcelable
	
    @Override
	public int describeContents() {
        return 0;
    }
	
    public static final Parcelable.Creator<Person> CREATOR = new Parcelable.Creator<Person>() {
        public Person createFromParcel(Parcel in) {
            return new Person(in);
        }
		
		public Person[] newArray(int size) {
            return new Person[size];
        }
    };
}
