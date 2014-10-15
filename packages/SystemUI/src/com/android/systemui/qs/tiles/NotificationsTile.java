/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.volume.VolumeComponent;
import com.android.systemui.volume.VolumePanel;
import com.android.systemui.volume.ZenModePanel;

/** Quick settings tile: Notifications **/
public class NotificationsTile extends QSTile<NotificationsTile.NotificationsState> {
    private final ZenModeController mZenController;
    private final AudioManager mAudioManager;

    private boolean mListening;

    public NotificationsTile(Host host) {
        super(host);
        mZenController = host.getZenModeController();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected NotificationsState newTileState() {
        return new NotificationsState();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mZenController.addCallback(mCallback);
            final IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mZenController.removeCallback(mCallback);
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    protected void handleClick() {
        showDetail(true);
    }

    @Override
    protected void handleUpdateState(NotificationsState state, Object arg) {
        state.visible = true;
        state.zen = mZenController.getZen();
        state.ringerMode = mAudioManager.getRingerMode();
        state.iconId = getNotificationIconId(state.zen, state.ringerMode);
        state.label = mContext.getString(R.string.quick_settings_notifications_label);
    }

    private int getNotificationIconId(int zenMode, int ringerMode) {
        int retValue = R.drawable.ic_qs_ringer_audible;
        if (zenMode == Global.ZEN_MODE_NO_INTERRUPTIONS) {
            retValue = R.drawable.ic_qs_zen_on;
        } else if (zenMode == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS){
            retValue = R.drawable.ic_qs_zen_important;
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            retValue = R.drawable.ic_qs_ringer_vibrate;
        } else if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            retValue = R.drawable.ic_qs_ringer_silent;
        }
        return retValue;
    }

    private final ZenModeController.Callback mCallback = new ZenModeController.Callback() {
        @Override
        public void onZenChanged(int zen) {
            if (DEBUG) Log.d(TAG, "onZenChanged " + zen);
            refreshState();
        }
    };

    public static final class NotificationsState extends QSTile.State {
        public int zen;
        public int ringerMode;

        @Override
        public boolean copyTo(State other) {
            final NotificationsState o = (NotificationsState) other;
            final boolean changed = o.zen != zen || o.ringerMode != ringerMode;
            o.zen = zen;
            o.ringerMode = ringerMode;
            return super.copyTo(other) || changed;
        }

        @Override
        protected StringBuilder toStringBuilder() {
            final StringBuilder rt = super.toStringBuilder();
            rt.insert(rt.length() - 1, ",zen=" + zen + ",ringerMode=" + ringerMode);
            return rt;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(intent.getAction())) {
                refreshState();
            }
        }
    };

    private final DetailAdapter mDetailAdapter = new DetailAdapter() {

        @Override
        public int getTitle() {
            return R.string.quick_settings_notifications_label;
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        public void setToggleState(boolean state) {
            // noop
        }

        public Intent getSettingsIntent() {
            return ZenModePanel.ZEN_SETTINGS;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            if (convertView != null) return convertView;
            final VolumeComponent volumeComponent = mHost.getVolumeComponent();
            final VolumePanel vp = new VolumePanel(mContext, parent, mZenController);
            final View v = vp.getContentView();
            v.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewDetachedFromWindow(View v) {
                    volumeComponent.setVolumePanel(null);
                }

                @Override
                public void onViewAttachedToWindow(View v) {
                    vp.updateStates();
                    vp.onConfigurationChanged(null);
                    volumeComponent.setVolumePanel(vp);
                }
            });
            vp.setZenModePanelCallback(new ZenModePanel.Callback() {
                @Override
                public void onMoreSettings() {
                    mHost.startSettingsActivity(ZenModePanel.ZEN_SETTINGS);
                }

                @Override
                public void onInteraction() {
                    // noop
                }

                @Override
                public void onExpanded(boolean expanded) {
                }
            });
            vp.postVolumeChanged(AudioManager.STREAM_RING, AudioManager.FLAG_SHOW_UI);
            return v;
        }
    };
}
