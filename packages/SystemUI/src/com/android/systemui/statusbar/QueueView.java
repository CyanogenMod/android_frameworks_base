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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

public class QueueView extends LinearLayout implements
        QueueViewRow.UserRowInteractionListener, AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener {

    private static final String TAG = QueueView.class.getSimpleName();
    private static final boolean DEBUG = MediaExpandableNotificationRow.DEBUG;

    private MediaController mController;

    private List<MediaSession.QueueItem> mQueue = new ArrayList<>(getMaxQueueRowCount());

    private QueueItemAdapter mAdapter;
    private ListView mList;
    private boolean mQueueEnabled;

    long mLastUserInteraction = -1;

    private MediaController.Callback mCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            super.onPlaybackStateChanged(state);

            if (getParent() != null && updateQueue(mController.getQueue())) {
                getParent().requestLayout();
            }
        }

        @Override
        public void onSessionDestroyed() {
            if (DEBUG) Log.d(TAG, "onSessionDestroyed() called with " + "");
            super.onSessionDestroyed();
            setController(null);
        }
    };

    public QueueView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAdapter = new QueueItemAdapter(context);
        setClipToOutline(false);
        setClipToPadding(false);
    }

    public void setQueueEnabled(boolean enabled) {
        mQueueEnabled = enabled;
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mList = (ListView) findViewById(R.id.queue_list);
        mList.setItemsCanFocus(true);
        mList.setDrawSelectorOnTop(true);
        mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mList.setAdapter(mAdapter);
        mList.setOnItemLongClickListener(this);
        mList.setOnItemClickListener(this);
        mList.setVerticalScrollBarEnabled(false);
    }

    private class QueueItemAdapter extends ArrayAdapter<MediaSession.QueueItem> {

        public QueueItemAdapter(Context context) {
            super(context, R.layout.queue_adapter_row, mQueue);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            if (position > getCount() - 1) {
                return -1;
            }
            return getItem(position).getQueueId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final MediaSession.QueueItem queueItem = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.queue_adapter_row, parent, false);
            }

            QueueViewRow row = (QueueViewRow) convertView;
            row.setHotSpotChangeListener(QueueView.this);

            row.setQueueItem(queueItem);

            return convertView;
        }

        @Override
        public int getCount() {
            if (!mQueueEnabled) {
                return 0;
            }
            return super.getCount();
        }
    }

    public boolean isUserSelectingRow() {
        final long delta = System.currentTimeMillis() - mLastUserInteraction;
        if (DEBUG) Log.i(TAG, "isUserSelectingRow() delta=" + delta);

        if (mLastUserInteraction > 0 && delta < 500) {
            if (DEBUG) Log.w(TAG, "user selecting row bc of hotspot change.");
            return true;
        }

        return false;
    }

    public int getMaxQueueRowCount() {
        return MediaExpandableNotificationRow.MAX_QUEUE_ENTRIES;
    }

    public int getCurrentQueueRowCount() {
        if (mAdapter == null) {
            return 0;
        }
        return mAdapter.getCount();
    }

    @Override
    public void onHotSpotChanged(float x, float y) {
        mLastUserInteraction = System.currentTimeMillis();
    }

    @Override
    public void onDrawableStateChanged() {
        mLastUserInteraction = System.currentTimeMillis();
    }

    /**
     * @param queue
     * @return whether the queue size has changed
     */
    public boolean updateQueue(List<MediaSession.QueueItem> queue) {
        int queueSizeBefore = mAdapter.getCount();

        mQueue.clear();

        if (queue != null) {
            // add everything *after* the currently playing item
            boolean foundNowPlaying = false;

            final PlaybackState playbackState = mController.getPlaybackState();

            long activeQueueId = -1;
            if (playbackState != null) {
                activeQueueId = playbackState.getActiveQueueItemId();
            }

            for (int i = 0; i < queue.size() && mQueue.size() < getMaxQueueRowCount(); i++) {
                final MediaSession.QueueItem item = queue.get(i);
                if (!foundNowPlaying
                        && activeQueueId != -1
                        && activeQueueId == item.getQueueId()) {
                    foundNowPlaying = true;
                    continue;
                }
                if (foundNowPlaying) {
                    mQueue.add(item);
                }
            }

            // add everything
            if (!foundNowPlaying) {
                for(int i = 0; i < getMaxQueueRowCount() && i < queue.size(); i++) {
                    mQueue.add(queue.get(i));
                }
            }
        }
        mAdapter.notifyDataSetChanged();

        return mAdapter.getCount() != queueSizeBefore;
    }

    public boolean setController(MediaController controller) {
        if (mController != null) {
            mController.unregisterCallback(mCallback);
        }
        mController = controller;
        if (mController != null) {
            mController.registerCallback(mCallback);
        }

        return updateQueue(mController != null
                ? mController.getQueue() : null);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final MediaSession.QueueItem itemAtPosition = (MediaSession.QueueItem)
                parent.getItemAtPosition(position);
        if (itemAtPosition != null && mController != null) {
            mController.getTransportControls().skipToQueueItem(itemAtPosition.getQueueId());
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        return true;
    }

}
