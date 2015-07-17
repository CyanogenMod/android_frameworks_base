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
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;

import cyanogenmod.providers.CMSettings;

public class MediaExpandableNotificationRow extends ExpandableNotificationRow {

    private static final String TAG = MediaExpandableNotificationRow.class.getSimpleName();
    public static final boolean DEBUG = false;

    public static final int MAX_QUEUE_ENTRIES = 3;

    private QueueView mQueue;

    private int mMaxQueueHeight;
    private int mRowHeight;
    private int mShadowHeight;
    private int mDisplayedRows;

    private SettingsObserver mSettingsObserver;
    private boolean mQueueEnabled;

    public MediaExpandableNotificationRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSettingsObserver = new SettingsObserver(new Handler());
        mQueueEnabled = isQueueEnabled(context);

        Resources res = mContext.getResources();

        // 3 * queue_row_height + shadow height
        mRowHeight = res.getDimensionPixelSize(R.dimen.queue_row_height);
        mShadowHeight = res.getDimensionPixelSize(R.dimen.queue_top_shadow);
    }

    public boolean inflateGuts() {
        if (getGuts() == null) {
            mGutsStub.inflate();
        }
        if (!mQueueEnabled) {
            return true;
        }
        return !mQueue.isUserSelectingRow();
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
            notifyHeightChanged(false /* needsAnimation */);
        }
        invalidateOutline();
    }

    @Override
    public int getIntrinsicHeight() {
        if (getGuts() != null && getGuts().isShown()) {
            return getGuts().getActualHeight();
        }
        if (!mQueueEnabled) {
            return super.getIntrinsicHeight();
        }
        if (mHideSensitiveForIntrinsicHeight) {
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
        return getMaxContentHeight();
    }

    @Override
    public int getMaxContentHeight() {
        /**
         * calls into NotificationContentView.getMaxHeight()
         */
        return getShowingLayout().getMaxHeight() + mMaxQueueHeight;
    }

    @Override
    public void setHeightRange(int rowMinHeight, int rowMaxHeight) {
        super.setHeightRange(rowMinHeight, rowMaxHeight);
        mMaxViewHeight = Math.max(rowMaxHeight, getMaxContentHeight());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQueue = (QueueView) findViewById(R.id.queue_view);
        mQueue.setQueueEnabled(mQueueEnabled);
        mQueue.setVisibility(mQueueEnabled ? View.VISIBLE : View.GONE);
    }

    public void setMediaController(MediaController mediaController) {
        if (DEBUG) Log.d(TAG, "setMediaController() called with "
                + "mediaController = [" + mediaController + "]");
        if (mQueue.setController(mediaController) && mQueueEnabled) {
            notifyHeightChanged(true);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (filterMotionEvent(ev)) {
            return super.dispatchGenericMotionEvent(ev);
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (filterMotionEvent(ev)) {
            return super.dispatchTouchEvent(ev);
        }
        return false;
    }

    @Override
    protected boolean filterMotionEvent(MotionEvent event) {
        if (!mQueueEnabled) {
            return super.filterMotionEvent(event);
        }
        if (isExpanded() && mQueue.isUserSelectingRow()
                && event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_UP
                && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
            // this is for hotspot propogation?
            return false;
        }
        return super.filterMotionEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    private class SettingsObserver extends UserContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            mContext.getContentResolver().registerContentObserver(
                    CMSettings.System.getUriFor(CMSettings.System.NOTIFICATION_PLAY_QUEUE),
                    true, this);
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            mQueueEnabled = isQueueEnabled(mContext);
            mQueue.setQueueEnabled(mQueueEnabled);
            mQueue.setVisibility(mQueueEnabled ? View.VISIBLE : View.GONE);
            requestLayout();
        }
    }

    public static boolean isQueueEnabled(Context context) {
        return CMSettings.System.getInt(context.getContentResolver(),
                CMSettings.System.NOTIFICATION_PLAY_QUEUE, 1) == 1;
    }
}
