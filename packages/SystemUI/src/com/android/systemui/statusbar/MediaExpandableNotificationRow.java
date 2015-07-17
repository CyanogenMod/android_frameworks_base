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
import android.media.session.MediaSession;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import com.android.systemui.R;

import java.util.List;

public class MediaExpandableNotificationRow extends ExpandableNotificationRow {

    private static final String TAG = MediaExpandableNotificationRow.class.getSimpleName();
    public static final boolean DEBUG = false || Build.IS_DEBUGGABLE;

    public static final int MAX_QUEUE_ENTRIES = 3;

    private MediaNotificationBackgroundView mPublicBackground, mPrivateBackground;
    private QueueView mQueue;

    private Integer mMaxQueueHeight;
    private int mDisplayedRows;

    private MediaController mController;

    public MediaExpandableNotificationRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxQueueHeight = getMaxQueueHeight();
    }

    public boolean inflateGuts() {
        return !mQueue.isUserSelectingRow();
    }

    @Override
    public int getMaxNotificationHeight() {
        return mMaxQueueHeight + super.getMaxNotificationHeight();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQueue = (QueueView) findViewById(R.id.queue_view);

        mPrivateBackground = (MediaNotificationBackgroundView) findViewById(R.id.backgroundNormal);
        mPublicBackground = (MediaNotificationBackgroundView)  findViewById(R.id.backgroundDimmed);

        mPrivateBackground.mQueueHeight = getMaxQueueHeight();
        mPublicBackground.mQueueHeight = getMaxQueueHeight();
    }

    private int getMaxQueueHeight() {
        int rows = mQueue != null ? mQueue.getCurrentQueueRowCount() : 0;

        if (mDisplayedRows != rows) {
            mDisplayedRows = rows;
            mMaxQueueHeight = null;
        }
        if (mMaxQueueHeight == null) {
            Resources res = mContext.getResources();
            // (num of max queue rows * their height) + shadow
            mMaxQueueHeight = (mDisplayedRows
                    * res.getDimensionPixelSize(R.dimen.queue_row_height)) // 3 * queue_row_height
                    + res.getDimensionPixelSize(R.dimen.queue_top_shadow); // 3dp or so
        }
        return mMaxQueueHeight;
    }

    @Override
    protected boolean filterMotionEvent(MotionEvent event) {
        if (isExpanded() && mQueue.isUserSelectingRow()
                && event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_UP
                && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
            return false;
        }

        return super.filterMotionEvent(event);
    }

    @Override
    public int getMaxHeight() {
        final int maxHeight = super.getMaxHeight();
        return maxHeight + getMaxQueueHeight();
    }

    public void setMediaController(MediaController mediaController) {
        if (DEBUG) Log.d(TAG, "setMediaController() called with "
                + "mediaController = [" + mediaController + "]");
        mController = mediaController;
        if (mQueue.setController(mController)) {
            notifyHeightChanged();
        }
    }

    /**
     * Apply an expansion state to the layout.
     */
    @Override
    public void applyExpansionToLayout() {
        if (DEBUG) Log.i(TAG, "applyExpansionToLayout()");
        if (mGuts != null && mGuts.getVisibility() == View.VISIBLE) {
            setActualHeight(mGuts.getActualHeight(), true);
            return;
        }

        boolean expand = isExpanded();
        if (expand && isExpandable()) {
            setActualHeight(mMaxExpandHeight);
        } else {
            setActualHeight(getMinHeight());
        }
    }

    @Override
    protected void updateMaxExpandHeight() {
        int intrinsicBefore = getIntrinsicHeight();
        mMaxExpandHeight = mPrivateLayout.getMaxHeight() + getMaxQueueHeight();
        if (intrinsicBefore != getIntrinsicHeight()) {
            notifyHeightChanged();
        }
    }

    @Override
    public int getIntrinsicHeight() {
        if (mGuts != null && mGuts.getVisibility() == View.VISIBLE) {
            return mGuts.getActualHeight();
        }
        if (mShowingPublicForIntrinsicHeight) {
            return getMinHeight();
        }
        if (isUserLocked()) {
            return getActualHeight();
        }
        boolean inExpansionState = isExpanded();
        if (!inExpansionState) {
            // not expanded, so we return the collapsed size
            return getMinHeight();
        }
        return getMaxExpandHeight();
    }

    @Override
    public int getMaxExpandHeight() {
        if (mGuts != null && mGuts.getVisibility() == View.VISIBLE) {
            return mGuts.getActualHeight();
        }

        return mShowingPublicForIntrinsicHeight
                ? getMinHeight()
                : mMaxExpandHeight;
    }
}
