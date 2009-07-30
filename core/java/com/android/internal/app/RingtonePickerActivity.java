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

import com.tmobile.widget.HeaderTwinButton;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

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
        
    /** The position in the list of the ringtone to sample. */
    private int mSampleRingtonePos = -1;

    /** Whether this list has the 'Silent' item. */
    private boolean mHasSilentItem;
    
    /** The Uri to place a checkmark next to. */
    private Uri mExistingUri;
    
    /** Whether this list has the 'Default' item. */
    private boolean mHasDefaultItem;
    
    /** The Uri to play when the 'Default' item is clicked. */
    private Uri mUriForDefaultItem;
    
    private ListView mListView = null;
    private ListAdapter mAdapter = null;

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
        
        mAdapter = new SimpleCursorAdapter(this, com.android.internal.R.layout.tmobile_list_item_label_checkable,
                mCursor, new String[] { MediaStore.MediaColumns.TITLE },
                new int[] { android.R.id.text1 });
        
        mListView = (ListView)findViewById(android.R.id.list);
        addStaticHeaders();

        mListView.setAdapter(mAdapter);
        
        mListView.setItemsCanFocus(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        selectCurrentRingtone();

        HeaderTwinButton headerTwinButton =
            (HeaderTwinButton)findViewById(com.android.internal.R.id.save_cancel_header);
   
        Button btnSave = (Button)headerTwinButton.getButton();
        btnSave.setId(ID_SAVE_BUTTON);
        btnSave.setOnClickListener(this);

        Button btnCancel = (Button)headerTwinButton.getButton2();
        btnCancel.setOnClickListener(this);
        btnSave.setId(ID_SAVE_BUTTON);
  
        mListView.setOnItemClickListener(this);
        mListView.setOnItemSelectedListener(this);
    }

    public void addStaticHeaders() {
        if (mHasDefaultItem) {
            mDefaultRingtonePos = addDefaultRingtoneItem(mListView);
        }
        
        if (mHasSilentItem) {
            mSilentPos = addSilentItem(mListView);
        }
    }
    
    public void selectCurrentRingtone() {
        if (mHasDefaultItem) {
            if (RingtoneManager.isDefault(mExistingUri)) {
                mListView.setItemChecked(mDefaultRingtonePos, true);
            }
        }

        if (mHasSilentItem) {
            if (mExistingUri == null) {
                mListView.setItemChecked(mSilentPos, true);
            }
        }

        if (mListView.getCheckedItemPosition() == ListView.INVALID_POSITION) {
            int cursorPos = mRingtoneManager.getRingtonePosition(mExistingUri);
            int pos = cursorPositionToListPosition(cursorPos);
            mListView.setItemChecked(pos, true);
        }
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
        CheckedTextView item = (CheckedTextView)getLayoutInflater().inflate(
                com.android.internal.R.layout.tmobile_list_item_label_checkable, null);

        item.setText(textResId);

        listView.addHeaderView(item);

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
            String title = null;
            
            int checkedPos = mListView.getCheckedItemPosition();

            if (checkedPos == mDefaultRingtonePos) {
                // Set it to the default Uri that they originally gave us
                uri = mUriForDefaultItem;
            } else if (checkedPos == mSilentPos) {
                // A null Uri is for the 'Silent' item
                uri = null;
            } else {
                Cursor cursor = (Cursor)mListView.getItemAtPosition(checkedPos);
                title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                uri = mRingtoneManager.getRingtoneUri(listPositionToCursorPosition(checkedPos));
            }

            resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
            resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title);
            setResult(RESULT_OK, resultIntent);
        } else {
            setResult(RESULT_CANCELED);
        }

       
        mCursor.deactivate();
        finish();
    }
    
    /*
     * On item selected via keys
     */
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        playRingtone(position, DELAY_MS_SELECTION_PLAYED);
    }

    public void onNothingSelected(AdapterView<?> parent) {
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
            ringtone = mRingtoneManager.getRingtone(listPositionToCursorPosition(mSampleRingtonePos));
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
    
    private int listPositionToCursorPosition(int listPos) {
        return listPos - mListView.getHeaderViewsCount();
    }
    
    private int cursorPositionToListPosition(int ringtoneManagerPos) {
        if (ringtoneManagerPos < 0) return ringtoneManagerPos;
        return ringtoneManagerPos + mListView.getHeaderViewsCount();
    }
    
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        playRingtone(position, DELAY_MS_SELECTION_PLAYED); 
    }
}
