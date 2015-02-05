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
 * limitations under the License.
 */

package com.android.systemui.cm;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.android.systemui.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.internal.util.cm.NavigationRingConstants.*;

public class ShortcutPickHelper {

    private final Context mContext;
    private final AppPickAdapter mAdapter;
    private final Intent mBaseIntent;
    private final int mIconSize;
    private OnPickListener mListener;
    private PackageManager mPackageManager;
    private ActionHolder mActions;
    private FetchAppsTask mFetchAppsTask;
    private List<ItemInfo> mItems;

    public interface OnPickListener {
        void shortcutPicked(String uri);
    }

    public ShortcutPickHelper(Context context, OnPickListener listener) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mItems = Collections.synchronizedList(new ArrayList<ItemInfo>());
        mAdapter = new AppPickAdapter(mContext);
        mBaseIntent = new Intent(Intent.ACTION_MAIN);
        mBaseIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        mListener = listener;
        mIconSize = context.getResources().getDimensionPixelSize(
                android.R.dimen.app_icon_size);
        createActionList();
    }

    public void cleanup() {
        if (mFetchAppsTask != null) {
            // We only un-register if apps were fetched
            mContext.unregisterReceiver(mReceiver);
            cancelAsyncTaskIfNecessary(mFetchAppsTask);
        }
        mFetchAppsTask = null;
        mAdapter.clear();
    }

    private void cancelAsyncTaskIfNecessary(AsyncTask task) {
        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
            task.cancel(true);
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mFetchAppsTask != null && mFetchAppsTask.getStatus() == AsyncTask.Status.RUNNING) {
                return;
            }
            Uri uri = intent.getData();
            if (uri == null) {
                return;
            }
            String packageName = uri.getSchemeSpecificPart();
            if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                mFetchAppsTask = new FetchAppsTask();
                mFetchAppsTask.execute(packageName);
            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                for (int i = 0; i < mItems.size(); i++) {
                    ItemInfo info = mItems.get(i);
                    if (info.componentName.getPackageName().equals(packageName)) {
                        mItems.remove(i);
                        break;
                    }
                }
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    private static class ItemInfo {
        ComponentName componentName;
        String label;
        Drawable icon;
        static ItemInfo populateFromPackage(PackageManager packageManager,
                                            ResolveInfo resolveInfo) {
            String packageName = resolveInfo.activityInfo.packageName;
            try {
                PackageInfo info = packageManager.getPackageInfo(packageName, 0);
                ItemInfo itemInfo = new ItemInfo();
                itemInfo.label = (String) info.applicationInfo.loadLabel(packageManager);
                itemInfo.icon = info.applicationInfo.loadIcon(packageManager);
                itemInfo.componentName = new ComponentName(packageName,
                        resolveInfo.activityInfo.name);
                return itemInfo;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private class FetchAppsTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... packages) {
            Intent baseIntent = new Intent(mBaseIntent);
            if (packages != null && packages.length == 1) {
                baseIntent.setPackage(packages[0]);
            } else {
                mItems.clear();
            }
            for (ResolveInfo item : mPackageManager.queryIntentActivities(baseIntent, 0)) {
                mItems.add(ItemInfo.populateFromPackage(mPackageManager, item));
            }
            sortItems();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void sortItems() {
        Collections.sort(mItems, new Comparator<ItemInfo>() {
            @Override
            public int compare(ItemInfo lhs, ItemInfo rhs) {
                return lhs.label.compareTo(rhs.label);
            }
        });
    }

    private class AppPickAdapter extends ArrayAdapter<ItemInfo> {

        public AppPickAdapter(Context context) {
            super(context, 0, mItems);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(mContext, R.layout.pick_item, null);
            }
            ItemInfo itemInfo = getItem(position);
            TextView textView = (TextView) convertView;
            textView.setText(itemInfo.label);
            itemInfo.icon.setBounds(0, 0, mIconSize, mIconSize);
            textView.setCompoundDrawables(itemInfo.icon, null, null, null);
            return convertView;
        }
    }

    private void pickApp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(R.string.navbar_dialog_title)
                .setAdapter(mAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ItemInfo info = mAdapter.getItem(which);
                        Intent intent = new Intent(mBaseIntent);
                        intent.setComponent(info.componentName);
                        mListener.shortcutPicked(intent.toUri(0));
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mListener.shortcutPicked(null);
                        dialog.cancel();
                    }
                });
        Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    public void pickShortcut(boolean showNone) {
        if (mFetchAppsTask == null) {
            mFetchAppsTask = new FetchAppsTask();
            mFetchAppsTask.execute();

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            mContext.registerReceiver(mReceiver, filter);
        }
        if (showNone) {
            mActions.addAction(ACTION_NONE, R.string.navring_action_none, 0);
        } else {
            mActions.removeAction(ACTION_NONE);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.navbar_dialog_title))
                .setItems(mActions.getEntries(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String item = mActions.getAction(which);
                        if (item.equals(ACTION_APP)) {
                            pickApp();
                            dialog.dismiss();
                        } else {
                            mListener.shortcutPicked(item);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mListener.shortcutPicked(null);
                        dialog.cancel();
                    }
                });

        Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void createActionList() {
        mActions = new ActionHolder();

        mActions.addAction(ACTION_APP, R.string.select_application);

        if (NavigationRingHelpers.isAssistantAvailable(mContext)) {
            mActions.addAction(ACTION_ASSIST, R.string.navring_action_google_now);
        }
        if (NavigationRingHelpers.isTorchAvailable(mContext)) {
            mActions.addAction(ACTION_TORCH, R.string.navring_action_torch);
        }

        mActions.addAction(ACTION_SCREENSHOT, R.string.navring_action_take_screenshot);
        mActions.addAction(ACTION_IME_SWITCHER, R.string.navring_action_open_ime_switcher);
        mActions.addAction(ACTION_SILENT, R.string.navring_action_ring_silent);

        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            mActions.addAction(ACTION_VIBRATE, R.string.navring_action_ring_vibrate);
            mActions.addAction(ACTION_RING_SILENT_VIBRATE,
                    R.string.navring_action_ring_vibrate_silent);
        }

        mActions.addAction(ACTION_KILL_TASK, R.string.navring_action_kill_app);
        mActions.addAction(ACTION_STANDBY, R.string.navring_action_screen_off);
    }

    private class ActionHolder {
        private ArrayList<CharSequence> mAvailableEntries = new ArrayList<CharSequence>();
        private ArrayList<String> mAvailableValues = new ArrayList<String>();

        public void addAction(String action, int entryResId, int index) {
            int itemIndex = getActionIndex(action);
            if (itemIndex != -1) {
                return;
            }
            mAvailableEntries.add(index, mContext.getString(entryResId));
            mAvailableValues.add(index, action);
        }

        public void addAction(String action, int entryResId) {
            int index = getActionIndex(action);
            if (index != -1) {
                return;
            }
            mAvailableEntries.add(mContext.getString(entryResId));
            mAvailableValues.add(action);
        }

        public void removeAction(String action) {
            int index = getActionIndex(action);
            if (index != -1) {
                mAvailableEntries.remove(index);
                mAvailableValues.remove(index);
            }
        }

        public int getActionIndex(String action) {
            int count = mAvailableValues.size();
            for (int i = 0; i < count; i++) {
                if (TextUtils.equals(mAvailableValues.get(i), action)) {
                    return i;
                }
            }
            return -1;
        }
        public String getAction(int index) {
            if (index > mAvailableValues.size()) {
                return null;
            }
            return mAvailableValues.get(index);
        }
        public CharSequence[] getEntries() {
            return mAvailableEntries.toArray(new CharSequence[mAvailableEntries.size()]);
        }
    }
}