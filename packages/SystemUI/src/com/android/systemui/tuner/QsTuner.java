/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.tuner;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSDragPanel;
import com.android.systemui.qs.QSPage;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host.Callback;
import com.android.systemui.qs.QSTile.ResourceIcon;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.policy.SecurityController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QsTuner extends Fragment implements Callback {

    private static final String TAG = "QsTuner";

    private static final int MENU_RESET = Menu.FIRST;
    private static final int MENU_EDIT = Menu.FIRST + 1;

    private DraggableQsPanel mQsPanel;
    private CustomHost mTileHost;

    private ScrollView mScrollRoot;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, com.android.internal.R.string.reset);
        menu.add(0, MENU_EDIT, 0, "toggle edit");
    }

    public void onResume() {
        super.onResume();
        MetricsLogger.visibility(getContext(), MetricsLogger.TUNER_QS, true);
    }

    public void onPause() {
        super.onPause();
        MetricsLogger.visibility(getContext(), MetricsLogger.TUNER_QS, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_EDIT:
                mQsPanel.setEditing(!mQsPanel.isEditing());
                break;
            case MENU_RESET:
                mTileHost.resetTiles();
                break;
            case android.R.id.home:
                getFragmentManager().popBackStack();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mScrollRoot = (ScrollView) inflater.inflate(R.layout.tuner_qs, container, false);

        mQsPanel = new DraggableQsPanel(getContext());
        mTileHost = new CustomHost(getContext());
        mTileHost.setCallback(this);
        mQsPanel.setTiles(mTileHost.getTiles());
        mQsPanel.setHost(mTileHost);
        mQsPanel.refreshAllTiles();
        ((ViewGroup) mScrollRoot.findViewById(R.id.all_details)).addView(mQsPanel, 0);

        return mScrollRoot;
    }

    @Override
    public void onDestroyView() {
        mTileHost.destroy();
        super.onDestroyView();
    }

    @Override
    public void onTilesChanged() {
        mQsPanel.setTiles(mTileHost.getTiles());
    }

    @Override
    public void setEditing(boolean editing) {
        mQsPanel.setEditing(editing);
    }

    @Override
    public boolean isEditing() {
        return mTileHost.isEditing();
    }

    @Override
    public void goToSettingsPage() {
    }

    private static class CustomHost extends QSTileHost {

        public CustomHost(Context context) {
            super(context, null, null, null, null, null, null, null, null, null,
                    null, null, new BlankSecurityController());
        }

        @Override
        public QSTile<?> createTile(String tileSpec) {
            return new DraggableTile(this, tileSpec);
        }

        public void replace(String oldTile, String newTile) {
            if (oldTile.equals(newTile)) {
                return;
            }
            MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_REORDER, oldTile + ","
                    + newTile);
            List<String> order = new ArrayList<>(mTileSpecs);
            int index = order.indexOf(oldTile);
            if (index < 0) {
                Log.e(TAG, "Can't find " + oldTile);
                return;
            }
            order.remove(newTile);
            order.add(index, newTile);
            setTiles(order);
        }

        private static class BlankSecurityController implements SecurityController {
            @Override
            public boolean hasDeviceOwner() {
                return false;
            }

            @Override
            public boolean hasProfileOwner() {
                return false;
            }

            @Override
            public String getDeviceOwnerName() {
                return null;
            }

            @Override
            public String getProfileOwnerName() {
                return null;
            }

            @Override
            public boolean isVpnEnabled() {
                return false;
            }

            @Override
            public String getPrimaryVpnName() {
                return null;
            }

            @Override
            public String getProfileVpnName() {
                return null;
            }

            @Override
            public void onUserSwitched(int newUserId) {
            }

            @Override
            public void addCallback(SecurityControllerCallback callback) {
            }

            @Override
            public void removeCallback(SecurityControllerCallback callback) {
            }
        }
    }

    public static class DraggableTile extends QSTile<QSTile.State> {
        private String mSpec;
        private QSTileView mView;

        protected DraggableTile(QSTile.Host host, String tileSpec) {
            super(host);
            Log.d(TAG, "Creating tile " + tileSpec);
            mSpec = tileSpec;
        }

        @Override
        public QSTileView createTileView(Context context) {
            mView = super.createTileView(context);
            return mView;
        }

        @Override
        public boolean hasDualTargetsDetails() {
            return "wifi".equals(mSpec) || "bt".equals(mSpec);
        }

        @Override
        public void setListening(boolean listening) {
        }

        @Override
        protected QSTile.State newTileState() {
            return new QSTile.State();
        }

        @Override
        protected void handleClick() {
        }

        @Override
        protected void handleUpdateState(QSTile.State state, Object arg) {
            state.visible = true;
            state.icon = ResourceIcon.get(getIcon());
            state.label = getLabel();
        }

        private String getLabel() {
            int resource = QSTileHost.getLabelResource(mSpec);
            if (resource != 0) {
                return mContext.getString(resource);
            }
            if (mSpec.startsWith(IntentTile.PREFIX)) {
                int lastDot = mSpec.lastIndexOf('.');
                if (lastDot >= 0) {
                    return mSpec.substring(lastDot + 1, mSpec.length() - 1);
                } else {
                    return mSpec.substring(IntentTile.PREFIX.length(), mSpec.length() - 1);
                }
            }
            return mSpec;
        }

        private int getIcon() {
            if (mSpec.equals("wifi")) return R.drawable.ic_qs_wifi_full_3;
            else if (mSpec.equals("bt")) return R.drawable.ic_qs_bluetooth_connected;
            else if (mSpec.equals("inversion")) return R.drawable.ic_invert_colors_enable;
            else if (mSpec.equals("cell")) return R.drawable.ic_qs_signal_full_3;
            else if (mSpec.equals("airplane")) return R.drawable.ic_signal_airplane_enable;
            else if (mSpec.equals("dnd")) return R.drawable.ic_qs_dnd_on;
            else if (mSpec.equals("rotation")) return R.drawable.ic_portrait_from_auto_rotate;
            else if (mSpec.equals("flashlight")) return R.drawable.ic_signal_flashlight_enable;
            else if (mSpec.equals("location")) return R.drawable.ic_signal_location_enable;
            else if (mSpec.equals("cast")) return R.drawable.ic_qs_cast_on;
            else if (mSpec.equals("hotspot")) return R.drawable.ic_hotspot_enable;
            else if (mSpec.equals("adb_network")) return R.drawable.ic_qs_network_adb_on;
            return R.drawable.android;
        }

        @Override
        public int getMetricsCategory() {
            return 20000;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof DraggableTile) {
                return mSpec.equals(((DraggableTile) o).mSpec);
            }
            return false;
        }

        @Override
        public String toString() {
            return mSpec;
        }
    }

    private class DraggableQsPanel extends QSDragPanel {

        public DraggableQsPanel(Context context) {
            super(context);

            setEditing(true);
        }

    }

}
