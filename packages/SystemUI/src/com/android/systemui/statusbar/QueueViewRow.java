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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaDescription;
import android.media.session.MediaSession;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.systemui.R;

public class QueueViewRow extends RelativeLayout {

    private static final String TAG = QueueViewRow.class.getSimpleName();

    private UserRowInteractionListener mHotSpotChangeListener;

    private ImageView mArt;
    private TextView mTitle;
    private TextView mSummary;

    public QueueViewRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mArt = (ImageView) findViewById(R.id.art);
        mTitle = (TextView) findViewById(R.id.title);
        mSummary = (TextView) findViewById(R.id.summary);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mHotSpotChangeListener != null) {
            mHotSpotChangeListener.onDrawableStateChanged();
        }
    }

    @Override
    public void dispatchDrawableHotspotChanged(float x, float y) {
        super.dispatchDrawableHotspotChanged(x, y);
        if (mHotSpotChangeListener != null) {
            mHotSpotChangeListener.onHotSpotChanged(x, y);
        }
    }

    public void setHotSpotChangeListener(UserRowInteractionListener listener) {
        mHotSpotChangeListener = listener;
    }

    public TextView getTitle() {
        return mTitle;
    }

    public TextView getSummary() {
        return mSummary;
    }

    public void setQueueItem(MediaSession.QueueItem queueItem) {
        setTag(queueItem);

        MediaDescription metadata = queueItem.getDescription();

        final Bitmap bitmap = metadata.getIconBitmap();
        mArt.setImageBitmap(bitmap);
        mArt.setVisibility(bitmap != null ? View.VISIBLE : View.GONE);

        mTitle.setText(metadata.getTitle());
        mSummary.setText(metadata.getSubtitle());
    }

    /* package */ interface UserRowInteractionListener {
        public void onHotSpotChanged(float x, float y);
        public void onDrawableStateChanged();
    }
}
