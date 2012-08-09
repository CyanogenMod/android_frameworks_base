/*
 * Copyright 2012 Ryan Welton
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

package com.android.internal.policy.impl;

import com.android.internal.R;

import com.android.internal.widget.SlidingTab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.provider.Settings;

import android.content.res.Configuration;
import com.android.internal.widget.LockPatternUtils;
import android.graphics.drawable.Drawable;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import java.lang.IllegalArgumentException;

public class SMSTab extends SlidingTab {

    boolean mVisible = true;
    TextView mSMSSenderView;
    TextView mSMSTextContentView;
    View mSpacerView;
    String mPhoneNumber = null;

    private KeyguardScreenCallback mKeyguardScreenCallback;

    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == SMS_RECEIVED_ACTION)
                checkUnread(context);
        }
    };

    public SMSTab(Context context) {
        this(context, null);
    }

    public SMSTab(final Context context, AttributeSet attrs) {
        super(context, attrs);

        mVisible = (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.LOCK_SCREEN_SMS, 1) == 1);

        if(!mVisible)
            setVisibility(View.GONE);

        IntentFilter SMSIntentFilter = new IntentFilter();
        SMSIntentFilter.addAction(SMS_RECEIVED_ACTION);
        context.registerReceiver(mIntentReceiver, SMSIntentFilter, null, getHandler());

        mSMSSenderView = new TextView(context);
        mSMSTextContentView = new TextView(context);

        mSMSSenderView.setTextColor(Color.WHITE);
        mSMSSenderView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        mSMSTextContentView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        mSMSTextContentView.setTextColor(Color.WHITE);

        addView(mSMSSenderView);
        addView(mSMSTextContentView);

        setOnTriggerListener(new OnTriggerListener(){
           public void onTrigger(View v, int whichHandle){
                if(whichHandle == OnTriggerListener.LEFT_HANDLE){
                    openMMSActivity(context);
                    closeLockScreen();
                }else if(whichHandle == OnTriggerListener.RIGHT_HANDLE){
                    openCallActivity(context);
                    closeLockScreen();
                }
            }
            public void onGrabbedStateChange(View v, int grabbedState){
            }
        });

        setLeftHintText(R.string.sms_reply);
        setRightHintText(R.string.sms_callback);

        setLeftTabDrawable(getPackageDrawable("com.android.mms",context));
        setRightTabDrawable(getPackageDrawable("com.android.phone",context));
        checkUnread(context);
    }

    private void closeLockScreen(){
        postDelayed(new Runnable() {
            public void run() {
                mKeyguardScreenCallback.keyguardDone(true);
                mKeyguardScreenCallback.reportSuccessfulUnlockAttempt();
            }
        },0);
    }

    public void initializer(KeyguardScreenCallback kCallback, View spacerView){
        mKeyguardScreenCallback = kCallback;
        mSpacerView = spacerView;
    }

    private void openCallActivity(Context context){
         String uri = "tel:" + mPhoneNumber.trim() ;
         Intent intent = new Intent(Intent.ACTION_CALL);
         intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         intent.setData(Uri.parse(uri));
         context.startActivity(intent);
    }

    private void openMMSActivity(Context context){
        int newestSMS = -1;
        Intent i;
        Uri uri = Uri.parse("content://sms");
        Cursor c = context.getContentResolver().query(uri, new String[]
            { "_id", "thread_id", "address", "person", "date", "body", "type" }, null, null, null);

        if(c.getCount() > 0 && c.moveToFirst())
           newestSMS = c.getInt(1);


        if(newestSMS > 0) {
            i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse("content://mms-sms/conversations/" + newestSMS));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            i = new Intent(Intent.ACTION_MAIN);
            i.setClassName("com.android.mms", "com.android.mms.ui.ConversationList");
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        c.close();
        context.startActivity(i);
    }

    private Drawable getPackageDrawable(String packageName, Context ctx){
        PackageManager pm = ctx.getPackageManager();
        PackageInfo pkgInfo;
        try {
            pkgInfo = pm.getPackageInfo(packageName, 0x00);
        } catch (NameNotFoundException e) {
            return null;
        }
        Drawable img = pkgInfo.applicationInfo.loadIcon(pm);
        return img;
    }

    private void checkUnread(Context context){
        Uri uri = Uri.parse("content://sms/inbox");
        boolean hasText = false;
        Cursor c = context.getContentResolver().query(uri, null, "read = 0", null, null);
        int unreadSMSCount = c.getCount();
        c.deactivate();
        if(unreadSMSCount == 1){
            String number = null;
            String msg = null;
            Cursor cursor = context.getContentResolver().query(uri,new String[] { "_id", "thread_id", "address", "person", "date","body", "type" }, null, null, null);
            String[] columns = new String[] { "address", "body"};
            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                number = cursor.getString(cursor.getColumnIndex(columns[0]));
                msg = cursor.getString(cursor.getColumnIndex(columns[1]));
            }
            mPhoneNumber = number;
            mSMSSenderView.setText(getName(number, context));
            mSMSTextContentView.setText(msg);
            mRightSlider.reset(false);
            hasText = true;

        }else if(unreadSMSCount > 1){
            hasText = true;
            mRightSlider.hideSlider();
        }

        if(hasText && mVisible){
            setVisibility(View.VISIBLE);
            if(mSpacerView != null)
                mSpacerView.setVisibility(View.GONE);
        } else {
            setVisibility(View.GONE);
            if(mSpacerView != null)
                mSpacerView.setVisibility(View.VISIBLE);
        }
    }

    private String getName(String number, Context context){
        String name = null;
        Uri personUri = Uri.withAppendedPath( ContactsContract.PhoneLookup.CONTENT_FILTER_URI, number);
        Cursor cur = context.getContentResolver().query(personUri, new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME }, null, null, null );
        if (cur.moveToFirst()) {
            int nameIndex = cur.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            name = cur.getString(nameIndex);
        }
        name = name == null ? number : name;
        return name;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed,l,t,r,b);
        mSMSSenderView.layout(l,t + getHeight() / 2,r,b);
        mSMSTextContentView.layout(l, t + mSMSSenderView.getLineHeight() + 5 / 2, r, b);
    }

}