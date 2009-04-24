/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.tmobile.widget.HeaderTwinButton;
import com.tmobile.widget.ListItemTwinIconLabelText;

/**
 * The {@link RingtonePickerActivity} allows the user to choose one from all of the
 * available ringtones. The chosen ringtone's URI will be persisted as a string.
 *
 * @see RingtoneManager#ACTION_RINGTONE_PICKER
 */
public final class RingtonePickerActivity extends Activity implements AdapterView.OnItemSelectedListener,
    AdapterView.OnItemClickListener, Runnable, View.OnClickListener {
    
    private static final int ID_SAVE_BUTTON = 1;
    private static final int ID_CANCEL_BUTTON = 2;
    
    private static final String TAG = "RingtonePickerActivity";

    private static final int DELAY_MS_SELECTION_PLAYED = 300;
    
    private RingtoneManager mRingtoneManager;
    
    private Cursor mCursor;
    private Handler mHandler;

    /** The position in the list of the 'Silent' item. */
    private int mSilentPos = -1;
    
    /** The position in the list of the 'Default' item. */
    private int mDefaultRingtonePos = -1;

    /** The position in the list of the last clicked item. */
    private int mClickedPos = -1;
    
    /** The position in the list of the ringtone to sample. */
    private int mSampleRingtonePos = -1;

    /** Whether this list has the 'Silent' item. */
    private boolean mHasSilentItem;
    
    /** The Uri to place a checkmark next to. */
    private Uri mExistingUri;
    
    /** The number of static items in the list. */
    private int mStaticItemCount;
    
    /** Whether this list has the 'Default' item. */
    private boolean mHasDefaultItem;
    
    /** The Uri to play when the 'Default' item is clicked. */
    private Uri mUriForDefaultItem;
    
    private ListView mListView = null;
    private RingtoneAdapter mAdapter = null;

    /**
     * A Ringtone for the default ringtone. In most cases, the RingtoneManager
     * will stop the previous ringtone. However, the RingtoneManager doesn't
     * manage the default ringtone for us, so we should stop this one manually.
     */
    private Ringtone mDefaultRingtone;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        Intent intent = getIntent();

        /*
         * Get whether to show the 'Default' item, and the URI to play when the
         * default is clicked
         */
        mHasDefaultItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        mUriForDefaultItem = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI);
        if (mUriForDefaultItem == null) {
            mUriForDefaultItem = Settings.System.DEFAULT_RINGTONE_URI;
        }
        
        // Get whether to show the 'Silent' item
        mHasSilentItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        
        // Give the Activity so it can do managed queries
        mRingtoneManager = new RingtoneManager(this);

        // Get whether to include DRM ringtones
        boolean includeDrm = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM,
                true);
        mRingtoneManager.setIncludeDrm(includeDrm);
        
        // Get the types of ringtones to show
        int types = intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1);
        if (types != -1) {
            mRingtoneManager.setType(types);
        }
        
        mCursor = mRingtoneManager.getCursor();
        
        // The volume keys will control the stream that we are choosing a ringtone for
        setVolumeControlStream(mRingtoneManager.inferStreamType());

        // Get the URI whose list item should have a checkmark
        mExistingUri = intent
                .getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);

        setContentView(com.android.internal.R.layout.tmobile_ringtone);
        
        mAdapter = new RingtoneAdapter(this, com.android.internal.R.layout.tmobile_ringtone_list_item,
                mCursor, new String[] {}, new int[] {}, false);      
        
        mListView = (ListView)findViewById(android.R.id.list);
        this.onPrepareListView(mListView);

        mListView.setAdapter(mAdapter);
        
        mListView.setItemsCanFocus(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

         HeaderTwinButton headerTwinButton =
         (HeaderTwinButton)findViewById(com.android.internal.R.id.save_cancel_header);
  
 
        Button btnSave = (Button)headerTwinButton.getButton();
        btnSave.setId(ID_SAVE_BUTTON);
        btnSave.setOnClickListener(this);

        Button btnCancel = (Button)headerTwinButton.getButton2();
        btnCancel.setOnClickListener(this);
        btnSave.setId(ID_SAVE_BUTTON);
  
        mListView.setOnItemClickListener(this); 

        if (-1 == mClickedPos) {
            mClickedPos = 0;
        }

        Log.d(TAG, "mClickedPos:"+ mClickedPos);
        mListView.setItemChecked(mClickedPos, true);     
        

        Log.d(TAG, "ListView; ChildCount" + mListView.getChildCount() + " Count :"+ mListView.getCount());                    
        if(mClickedPos < mListView.getHeaderViewsCount()){
            View v = mListView.getAdapter().getView(mClickedPos, null, null);
            if(v != null){
                Log.d(TAG, "Setting Checkbox for position:"+ mClickedPos);
                ImageView imageView = (ImageView)v.findViewById(com.android.internal.R.id.icon2);           
                imageView.setVisibility(View.VISIBLE); 
            }
        }else {
            Cursor cursor = (Cursor)mListView.getAdapter().getItem(mClickedPos);
            mAdapter.setSelectedPosition(cursor.getPosition(), false);
        }
    }
 

    public void onPrepareListView(ListView listView) {
        
        if (mHasDefaultItem) {
            mDefaultRingtonePos = addDefaultRingtoneItem(listView);
            
            if (RingtoneManager.isDefault(mExistingUri)) {
                mClickedPos = mDefaultRingtonePos;
            }
        }
        
        if (mHasSilentItem) {
            mSilentPos = addSilentItem(listView);
            
            // The 'Silent' item should use a null Uri
            if (mExistingUri == null) {
                mClickedPos = mSilentPos;
            }
        }

        if (mClickedPos == -1) {
            mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(mExistingUri));
        }
 
        // Put a checkmark next to an item.
        //mAlertParams.mCheckedItem = mClickedPos;
    }

    
    /**
     * Adds a static item to the top of the list. A static item is one that is not from the
     * RingtoneManager.
     * 
     * @param listView The ListView to add to.
     * @param textResId The resource ID of the text for the item.
     * @return The position of the inserted item.
     */
    private int addStaticItem(ListView listView, int textResId) {
       ListItemTwinIconLabelText item = (ListItemTwinIconLabelText)getLayoutInflater().inflate(
                com.android.internal.R.layout.tmobile_ringtone_list_item, null);
        
       item.setItemLabelText(getResources().getString(textResId));
       item.setIcon2(getResources().getDrawable(com.android.internal.R.drawable.pluto_chechkbox_focus));
       ImageView imageView = (ImageView)item.findViewById(com.android.internal.R.id.icon2);
       imageView.setVisibility(View.GONE);
        
       listView.addHeaderView(item);
        
       mStaticItemCount++;
       return listView.getHeaderViewsCount() - 1;
    }
    
    private int addDefaultRingtoneItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.ringtone_default);
    }
    
    private int addSilentItem(ListView listView) {
        return addStaticItem(listView, com.android.internal.R.string.ringtone_silent);
    }
    
    /*
     * On click of Ok/Cancel buttons
     */
    public void onClick(View v) {
        Log.d(TAG, "onClick : " + v.getId());        
        boolean positiveResult = false;
        switch (v.getId()) {
            case ID_SAVE_BUTTON:
                positiveResult = true;
                break;
        }

        
        // Stop playing the previous ringtone
        mRingtoneManager.stopPreviousRingtone();
        
        if (positiveResult) {
            Intent resultIntent = new Intent();
            Uri uri = null;
            
            if (mClickedPos == mDefaultRingtonePos) {
                // Set it to the default Uri that they originally gave us
                uri = mUriForDefaultItem;
            } else if (mClickedPos == mSilentPos) {
                // A null Uri is for the 'Silent' item
                uri = null;
            } else {
                uri = mRingtoneManager.getRingtoneUri(getRingtoneManagerPosition(mClickedPos));
            }

            resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
            setResult(RESULT_OK, resultIntent);
//            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE,uri);
        } else {
            setResult(RESULT_CANCELED);
        }

       
        mCursor.deactivate();
        finish();
    }
    
    /*
     * On item selected via keys
     */
    public void onItemSelected(AdapterView parent, View view, int position, long id) {
        playRingtone(position, DELAY_MS_SELECTION_PLAYED);
    }

    public void onNothingSelected(AdapterView parent) {
    }

    private void playRingtone(int position, int delayMs) {
        mHandler.removeCallbacks(this);
        mSampleRingtonePos = position;
        mHandler.postDelayed(this, delayMs);
    }
    
    public void run() {
        
        if (mSampleRingtonePos == mSilentPos) {
            return;
        }
        
        /*
         * Stop the default ringtone, if it's playing (other ringtones will be
         * stopped by the RingtoneManager when we get another Ringtone from it.
         */
        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
            mDefaultRingtone = null;
        }
        
        Ringtone ringtone;
        if (mSampleRingtonePos == mDefaultRingtonePos) {
            if (mDefaultRingtone == null) {
                mDefaultRingtone = RingtoneManager.getRingtone(this, mUriForDefaultItem);
            }
            ringtone = mDefaultRingtone;
            
            /*
             * Normally the non-static RingtoneManager.getRingtone stops the
             * previous ringtone, but we're getting the default ringtone outside
             * of the RingtoneManager instance, so let's stop the previous
             * ringtone manually.
             */
            mRingtoneManager.stopPreviousRingtone();
            
        } else {
            ringtone = mRingtoneManager.getRingtone(getRingtoneManagerPosition(mSampleRingtonePos));
        }
        
        if (ringtone != null) {
            ringtone.play();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopAnyPlayingRingtone();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAnyPlayingRingtone();
    }

    private void stopAnyPlayingRingtone() {

        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
        }
        
        if (mRingtoneManager != null) {
            mRingtoneManager.stopPreviousRingtone();
        }
    }
    
    private int getRingtoneManagerPosition(int listPos) {
        return listPos - mStaticItemCount;
    }
    
    private int getListPosition(int ringtoneManagerPos) {
        
        // If the manager position is -1 (for not found), return that
        if (ringtoneManagerPos < 0) return ringtoneManagerPos;
        
        return ringtoneManagerPos + mStaticItemCount;
    }
    
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        Log.d(TAG, "onItemClick"+position);   
        
        Cursor cursor = (Cursor)parent.getItemAtPosition(position);
        if(cursor != null){
            if(mClickedPos == mDefaultRingtonePos || mClickedPos == mSilentPos){
                View staticView = parent.getChildAt(mClickedPos);
                setStaticViewVisibleState(staticView, View.GONE);
            }
            mAdapter.setSelectedPosition(cursor.getPosition(), true);
        }else {
            setStaticViewVisibleState(v, View.VISIBLE);
            mAdapter.setSelectedPosition(-1, true);
        }
        
        playRingtone(position, DELAY_MS_SELECTION_PLAYED);        
       
        this.mClickedPos = position;
        switch (position) {

        }

    }

    private void setStaticViewVisibleState(View v, int state) {
        ImageView imageView = (ImageView)v.findViewById(com.android.internal.R.id.icon2);
        imageView.setVisibility(state);
    }    
    
    
    public class RingtoneAdapter extends SimpleCursorAdapter {
        int selectedPos = -1;
        public RingtoneAdapter(Context context, int layout, Cursor c, String[] from, int[] to, boolean visible) {
            super(context, layout, c, from, to);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView textView = (TextView)view.findViewById(com.android.internal.R.id.itemLabelText);
            String text = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            textView.setText(text);

            ImageView imageView = (ImageView)view.findViewById(com.android.internal.R.id.icon2);
            imageView.setImageDrawable(getResources().getDrawable(com.android.internal.R.drawable.pluto_chechkbox_focus));
            
            
            int viewPos = cursor.getPosition();
            if (viewPos == selectedPos) {
                imageView.setVisibility(View.VISIBLE);
            } else {
                imageView.setVisibility(View.GONE);
            }

        }        
        
        public void setSelectedPosition(int pos, boolean notify) {
            selectedPos = pos;
            if(notify){
                notifyDataSetChanged();
            }
        }
        
    }



    
}
