/*
 * Copyright (C) 2015 The CyanogenMod Project
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
 * limitations under the License
 */
package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Resources;
import android.media.session.MediaController;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewStub;

import com.android.systemui.R;
import com.android.systemui.tuner.TunerService;

public class MediaExpandableNotificationRow extends ExpandableNotificationRow
        implements TunerService.Tunable {

    private static final String TAG = MediaExpandableNotificationRow.class.getSimpleName();
    public static final boolean DEBUG = false;

    public static final int MAX_QUEUE_ENTRIES = 3;

    private QueueView mQueue;

    private int mMaxQueueHeight;
    private int mRowHeight;
    private int mShadowHeight;
    private int mDisplayedRows;

    private boolean mQueueEnabled = false;

    private static final String NOTIFICATION_PLAY_QUEUE = "cmsystem:" +
            cyanogenmod.providers.CMSettings.System.NOTIFICATION_PLAY_QUEUE;

    public MediaExpandableNotificationRow(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = mContext.getResources();

        // 3 * queue_row_height + shadow height
        mRowHeight = res.getDimensionPixelSize(R.dimen.queue_row_height);
        mShadowHeight = res.getDimensionPixelSize(R.dimen.queue_top_shadow);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TunerService.get(getContext()).addTunable(this, NOTIFICATION_PLAY_QUEUE);
    }

    @Override
    protected void onDetachedFromWindow() {
        TunerService.get(getContext()).removeTunable(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (NOTIFICATION_PLAY_QUEUE.equals(key) && mQueue != null) {
            boolean show = newValue == null || Integer.valueOf(newValue) == 1;
            showQueue(show);
        }
    }

    @Override
    public boolean inflateGuts() {
        if (getGuts() == null) {
            View guts = mGutsStub.inflate();
            ViewStub mediaGuts = (ViewStub) guts.findViewById(R.id.notification_guts_media_stub);
            mediaGuts.inflate();
        }
        if (!mQueueEnabled) {
            return true;
        }
        return !mQueue.isUserSelectingRow();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQueue = (QueueView) findViewById(R.id.queue_view);
        showQueue(mQueueEnabled);
    }

    private void showQueue(boolean show) {
        if (show != mQueueEnabled) {
            mQueueEnabled = show;
            mQueue.setQueueEnabled(mQueueEnabled);
            mQueue.setVisibility(mQueueEnabled ? View.VISIBLE : View.GONE);
            requestLayout();
        }
    }

    public void setMediaController(MediaController mediaController) {
        if (DEBUG) Log.d(TAG, "setMediaController() called with "
                + "mediaController = [" + mediaController + "]");
        if (mQueue != null && mQueue.setController(mediaController) && mQueueEnabled) {
            notifyHeightChanged(true);
        }
    }

    @Override
    protected void updateMaxHeights() {
        // update queue height based on number of rows
        int rows = mQueue != null ? mQueue.getCurrentQueueRowCount() : 0;
        if (rows != mDisplayedRows) {
            mMaxQueueHeight = rows * mRowHeight;
            if (mMaxQueueHeight > 0) {
                mMaxQueueHeight += mShadowHeight;
            }
            mDisplayedRows = rows;
        }

        int intrinsicBefore = getIntrinsicHeight();
        View expandedChild = mPrivateLayout.getExpandedChild();
        if (expandedChild == null) {
            expandedChild = mPrivateLayout.getContractedChild();
        }
        mMaxExpandHeight = expandedChild.getHeight() + mMaxQueueHeight;
        View headsUpChild = mPrivateLayout.getHeadsUpChild();
        if (headsUpChild == null) {
            headsUpChild = mPrivateLayout.getContractedChild();
        }
        mHeadsUpHeight = headsUpChild.getHeight();
        if (intrinsicBefore != getIntrinsicHeight()) {
            notifyHeightChanged(false  /* needsAnimation */);
        }
    }

    @Override
    public int getMaxContentHeight() {
        return super.getMaxContentHeight() + mMaxQueueHeight;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mQueueEnabled && isExpanded() && mQueue.isUserSelectingRow()
                && event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_UP
                && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
            // this is for hotspot propogation?
            return false;
        }
        return super.dispatchTouchEvent(event);
    }
}
