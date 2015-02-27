/*
 * Copyright (C) 2015 The CyanogenMod Open Source Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;

import java.util.ArrayList;
import java.util.Arrays;

public class ScreenTimeoutTile extends QSTile<ScreenTimeoutTile.TimeoutState> {
    private static final Intent SETTINGS_INTENT = new Intent("android.settings.DISPLAY_SETTINGS");
    private static final String TIMEOUT_ENTRIES_NAME = "screen_timeout_entries";
    private static final String TIMEOUT_VALUES_NAME = "screen_timeout_values";
    private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    private String[] mEntries, mValues;
    private boolean mShowingDetail;
    ArrayList<Drawable> mAnimationList
            = new ArrayList<Drawable>();

    public ScreenTimeoutTile(Host host) {
        super(host);
        populateList();
    }

    private void populateList() {
        try {
            Context context = mContext.createPackageContext(SETTINGS_PACKAGE_NAME, 0);
            Resources mSettingsResources = context.getResources();
            int id = mSettingsResources.getIdentifier(TIMEOUT_ENTRIES_NAME,
                    "array", SETTINGS_PACKAGE_NAME);
            if (id <= 0) {
                return;
            }
            mEntries = mSettingsResources.getStringArray(id);
            id = mSettingsResources.getIdentifier(TIMEOUT_VALUES_NAME,
                    "array", SETTINGS_PACKAGE_NAME);
            if (id <= 0) {
                return;
            }
            mValues = mSettingsResources.getStringArray(id);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private int getScreenTimeout() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 0);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return new LocationDetailAdapter();
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
                    false, mObserver);
        } else {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }

    @Override
    protected TimeoutState newTileState() {
        return new TimeoutState();
    }

    @Override
    protected void handleClick() {
        if (mEntries.length > 0) {
            mShowingDetail = true;
            mAnimationList.clear();
            showDetail(true);
        }
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(SETTINGS_INTENT);
    }

    private String makeTimeoutSummaryString(int timeout) {
        Resources res = mContext.getResources();
        int resId;

        /* ms -> seconds */
        timeout /= 1000;

        if (timeout >= 60 && timeout % 60 == 0) {
            /* seconds -> minutes */
            timeout /= 60;
            if (timeout >= 60 && timeout % 60 == 0) {
                /* minutes -> hours */
                timeout /= 60;
                resId = com.android.internal.R.plurals.duration_hours;
            } else {
                resId = com.android.internal.R.plurals.duration_minutes;
            }
        } else {
            resId = com.android.internal.R.plurals.duration_seconds;
        }

        return res.getQuantityString(resId, timeout, timeout);
    }

    public static final class TimeoutState extends QSTile.State {
        int previousTimeout;
    }

    private enum Bucket {
        SMALL(0, 30000),
        MEDIUM(60000,300000),
        LARGE(600000, 1800000);
        private final int start;
        private final int stop;

        Bucket(int start, int stop) {
            this.start = start;
            this.stop = stop;
        }

        public static Bucket getBucket(int value) {
            for (Bucket item : Bucket.values()) {
                if (value >= item.start && value <= item.stop) {
                    return item;
                }
            }
            return null;
        }

    }
    @Override
    protected void handleUpdateState(final TimeoutState state, Object arg) {
        if (mAnimationList.isEmpty() && mShowingDetail && arg == null) {
            return;
        }

        int newTimeout = getScreenTimeout();

        int drawableId = 0;
        Resources resources = mContext.getResources();
        Bucket nextBucket = Bucket.getBucket(newTimeout);
        Bucket previousBucket = Bucket.getBucket(state.previousTimeout);

        switch (state.previousTimeout) {
            case 0:
            case 15000:
            case 30000:
                // Default
                drawableId = R.drawable.ic_qs_screen_timeout_med_reverse_avd;
                if (nextBucket == Bucket.MEDIUM) {
                    // Medium
                    drawableId = R.drawable.ic_qs_screen_timeout_short_avd;
                } else if (nextBucket == Bucket.LARGE) {
                    // Large
                    drawableId = R.drawable.ic_qs_screen_timeout_short_reverse_avd;
                }
                break;
            case 60000:
            case 120000:
            case 300000:
                // Default
                drawableId = R.drawable.ic_qs_screen_timeout_short_avd;
                if (nextBucket == Bucket.SMALL) {
                    // Small
                    drawableId = R.drawable.ic_qs_screen_timeout_med_reverse_avd;
                } else if (nextBucket == Bucket.LARGE) {
                    // Large
                    drawableId = R.drawable.ic_qs_screen_timeout_med_avd;
                }
                break;
            case 600000:
            case 1800000:
                drawableId = R.drawable.ic_qs_screen_timeout_med_avd;
                if (nextBucket == Bucket.MEDIUM) {
                    // Small
                    drawableId = R.drawable.ic_qs_screen_timeout_long_reverse_avd;
                } else if (nextBucket == Bucket.SMALL) {
                    // Large
                    drawableId = R.drawable.ic_qs_screen_timeout_long_avd;
                }
                break;
        }

        if (state.icon == null || previousBucket != nextBucket) {
            final Drawable d = resources.getDrawable(drawableId);
            if (d instanceof AnimatedVectorDrawable) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((AnimatedVectorDrawable) d).start();
                    }
                });
            }
            state.icon = d;
        }

        runNextAnimation(state);
        state.visible = true;
        state.label = makeTimeoutSummaryString(newTimeout);
        state.previousTimeout = newTimeout;
    }

    private void runNextAnimation(final TimeoutState state) {
        if (mAnimationList.isEmpty()) {
            return;
        }
        state.icon = mAnimationList.remove(0);
        if (state.icon instanceof AnimatedVectorDrawable) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    ((AnimatedVectorDrawable) state.icon).start();
                }
            });
        }
    }

    private class RadioAdapter extends ArrayAdapter<String> {

        public RadioAdapter(Context context, int resource, String[] objects) {
            super(context, resource, objects);
        }

        public RadioAdapter(Context context, int resource,
                            int textViewResourceId, String[] objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            view = super.getView(position, view, parent);

            view.setMinimumHeight(mContext.getResources() .getDimensionPixelSize(
                    R.dimen.qs_detail_item_height));

            return view;
        }

    }
    private class LocationDetailAdapter implements DetailAdapter, AdapterView.OnItemClickListener {
        private QSDetailItemsList mItems;

        @Override
        public int getTitle() {
            return R.string.quick_settings_screen_timeout_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public Intent getSettingsIntent() {
            return SETTINGS_INTENT;
        }

        @Override
        public void setToggleState(boolean state) {
            // noop
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItems = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            ListView listView = mItems.getListView();
            listView.setOnItemClickListener(this);
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setDivider(null);
            RadioAdapter adapter = new RadioAdapter(context,
                    android.R.layout.simple_list_item_single_choice, mEntries);
            int indexOfSelection = Arrays.asList(mValues).indexOf(String.valueOf(getScreenTimeout()));
            mItems.setAdapter(adapter);
            listView.setItemChecked(indexOfSelection, true);
            mItems.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    mUiHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mShowingDetail = false;
                            refreshState(true);
                        }
                    }, 100);

                }
            });
            return mItems;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            int selectedTimeout = Integer.valueOf(mValues[position]);
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, selectedTimeout);
        }
    }
}
