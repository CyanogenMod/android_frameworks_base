/*
* Copyright (C) 2014 AOSB Project
* Author Hany alsamman @codex-corp
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

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.content.Context;
import android.content.ContentResolver;

import android.provider.ContactsContract;
import android.provider.CallLog.Calls;

import android.graphics.Bitmap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.android.systemui.R;

import java.util.List;
import java.util.ArrayList;

import com.android.systemui.recent.PersonBubbleActivity;

public final class People extends PersonUtils{

	private static int PERSON_LOGS_LIMIT = 6;
	private static int PERSON_STAR_LIMIT = 6;

	public static List<Person> PEOPLE_STARRED(Context ctx) {

        ArrayList<Person> people = new ArrayList<Person>();

        Cursor cursor = ctx.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, "starred=?", new String[] {"1"}, null);

        int i=1;
        int contactID;
        String contactName;
        Bitmap contactIcon;

        try {
            while (cursor.moveToNext()) {
                if(i == PERSON_STAR_LIMIT) break;
                contactID = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                contactIcon = getContactIcon(contactID, ctx);
                people.add( new Person(i, contactIcon, contactName, contactName,contactID) );
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return people;
    }

    public static List<Person> PEOPLE_LOGS(Context ctx) {

        ArrayList<Person> people_logs = new ArrayList<Person>();

        Cursor cursor = ctx.getContentResolver().query(Calls.CONTENT_URI, null, null, null, Calls.DATE + " DESC");

        int i=1;
        int contactID;
        int callContactID;
        String callDuration;
        String callNumber;
        String callName = null;
        Bitmap callContactIcon;

        while(cursor.moveToNext()) {

            if(i == PERSON_LOGS_LIMIT) break;

            try {
                callName = cursor.getString( cursor.getColumnIndex(Calls.CACHED_NAME) );
            } catch (Exception e) {
                //e.printStackTrace();
                callName = null;
            }
            callNumber = cursor.getString( cursor.getColumnIndex(Calls.NUMBER) );
            callContactID = getContactIDFromNumber(callNumber, ctx);
            callDuration = (cursor.getInt( cursor.getColumnIndex(Calls.DURATION) ) != 0) ? 
            calculateTime( cursor.getInt( cursor.getColumnIndex(Calls.DURATION) ) ) : "0";
            callContactIcon = getContactIcon(Long.valueOf(callContactID), ctx);
            // if caller does name have name , set number
            if(callName == null){ callName = callNumber; }

            String dir = null;
            String outgoing = ctx.getResources().getString(R.string.outgoing);
            String incoming = ctx.getResources().getString(R.string.incoming);
            String missed = ctx.getResources().getString(R.string.missed);
            String type = ctx.getResources().getString(R.string.type);
            String duration = ctx.getResources().getString(R.string.duration);
            int dircode = cursor.getInt( cursor.getColumnIndex(Calls.TYPE) );
            switch (dircode) {
                case Calls.OUTGOING_TYPE:
                    dir = outgoing;
                    break;

                case Calls.INCOMING_TYPE:
                    dir = incoming;
                    break;

                case Calls.MISSED_TYPE:
                    dir = missed;
                    break;
            }
            String callInfo = type + dir + "\n" + duration + callDuration;
			people_logs.add( new Person(i, callContactIcon, callName, callInfo, callContactID) );
			i++;
        }
        cursor.close();
		return people_logs;
    }

    public static View inflatePersonView(Context context, ViewGroup parent, Person person) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageButton personView = (ImageButton)inflater.inflate(com.android.systemui.R.layout.button_person, parent, false);
        personView.setImageBitmap(person.getIcon());
        personView.setContentDescription(person.getName());
        personView.setTag(person);
        personView.setOnClickListener(new iOSDoubleClick() {
            @Override
            public void onSingleClick(View v) {
                Context context = v.getContext();
                context.startActivity(PersonBubbleActivity.createIntent(context, v, (Person)v.getTag()));
            }
            @Override
            public void onDoubleClick(View v) {
                Context context = v.getContext();
                PersonUtils.OpenContact(context,(Person)v.getTag());
            }
        });
        return personView;
    }
}
