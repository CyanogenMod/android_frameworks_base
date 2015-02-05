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

package com.android.systemui.cm;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
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
    private AsyncTask mSortTask;
    private List<String> mPackages;

    public interface OnPickListener {
        void shortcutPicked(String uri);
    }

    public ShortcutPickHelper(Context context, OnPickListener listener) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mPackages = Collections.synchronizedList(new ArrayList<String>());
        mAdapter = new AppPickAdapter(mContext);

        mBaseIntent = new Intent(Intent.ACTION_MAIN);
        mBaseIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        mFetchAppsTask = new FetchAppsTask();
        mFetchAppsTask.execute();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        context.registerReceiver(mReceiver, filter);

        mListener = listener;
        mIconSize = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        createActionList();
    }

    public void cleanup() {
        if (mSortTask != null)
        mContext.unregisterReceiver(mReceiver);
        cancelAsyncTask(mFetchAppsTask);
        cancelAsyncTask(mSortTask);
    }

    private void cancelAsyncTask(AsyncTask task) {
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

            if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                mPackages.add(intent.getDataString());
                if (mSortTask != null && mSortTask.getStatus() != AsyncTask.Status.FINISHED) {
                    mSortTask.cancel(true);
                }
                mSortTask = new SortTask();
                mSortTask.execute();
            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                mPackages.remove(intent.getDataString());
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    private class FetchAppsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            mPackages.clear();
            for (ResolveInfo item : mPackageManager.queryIntentActivities(mBaseIntent, 0)) {
                mPackages.add(item.activityInfo.packageName);
            }
            sortItems();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private class SortTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            sortItems();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void sortItems() {
        Collections.sort(mPackages, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                try {
                    String leftLabel = (String) mPackageManager.getPackageInfo(lhs, 0)
                            .applicationInfo.loadLabel(mPackageManager);
                    String rightLabel = (String) mPackageManager.getPackageInfo(rhs, 0)
                            .applicationInfo.loadLabel(mPackageManager);
                    return leftLabel.compareTo(rightLabel);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                return 0;
            }
        });
    }

    private class FetchTask extends AsyncTask<String, Void, FetchTask.FetchTaskItem> {

        protected class FetchTaskItem {
            String label;
            Drawable drawable;
        }

        private final WeakReference<TextView> mView;

        public FetchTask(TextView convertView) {
            mView = new WeakReference<TextView>(convertView);
        }

        @Override
        protected FetchTask.FetchTaskItem doInBackground(String... params) {
            FetchTaskItem item = new FetchTaskItem();
            try {
                PackageInfo info = mPackageManager.getPackageInfo(params[0], 0);
                item.label = (String) info.applicationInfo.loadLabel(mPackageManager);
                item.drawable = info.applicationInfo.loadIcon(mPackageManager);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return item;
        }

        @Override
        protected void onPostExecute(FetchTaskItem fetchTaskItem) {
            if (isCancelled()) {
                return;
            }
            TextView view = mView.get();
            if (view != null) {
                view.setText(fetchTaskItem.label);
                fetchTaskItem.drawable.setBounds(0, 0, mIconSize, mIconSize);
                view.setCompoundDrawables(fetchTaskItem.drawable, null, null, null);
            }
        }
    }

    private class AppPickAdapter extends ArrayAdapter<String> {

        public AppPickAdapter(Context context) {
            super(context, 0, mPackages);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = View.inflate(mContext, R.layout.pick_item, null);
            }

            String packageName = getItem(position);

            if (convertView.getTag() != null) {
                ((AsyncTask) convertView.getTag()).cancel(true);
            }

            FetchTask task = new FetchTask((TextView) convertView);
            convertView.setTag(task);
            task.execute(packageName);

            return convertView;
        }
    }

    private void pickApp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(R.string.navbar_dialog_title)
                .setAdapter(mAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = mPackageManager.getLaunchIntentForPackage(mAdapter.getItem(which));
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
