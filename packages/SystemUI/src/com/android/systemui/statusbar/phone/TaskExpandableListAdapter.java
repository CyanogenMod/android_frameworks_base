/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

package com.android.systemui.statusbar.phone;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageStats;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.pm.IPackageManager;
import android.widget.Toast;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.util.Calendar;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.ProgressBar;

import android.widget.BaseExpandableListAdapter;
import android.widget.AbsListView;
import com.android.systemui.R;
import android.os.UserHandle;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import java.util.TimerTask;
import java.util.Timer;

/**
 * ExpandableListAdapter to handle packages and activities
 *
 */
public class TaskExpandableListAdapter extends BaseExpandableListAdapter {
    public static final String TAG = "TaskExpandableListAdapter";
    public static final boolean DEBUG = false;
    private final boolean FILTER_LAUNCHER = false;
    private final boolean FILTER_ON = true;
    private static final int MSG_LOAD_FINISHED = 0;
    private static final int MSG_AUTO_LOAD_TIMER = 1;
    private static final int MSG_NORMAL_REFRESH = 2;
    private static final int MSG_STOP_REFRESH = 3;
    private static final int MSG_KILL_ONE = 4;
    private static final int MSG_KILL_ALL = 5;
    private static final int MSG_BRING_TO_FRONT = 6;

    private static final int AUTO_LOAD_RUNNING_PROCESS_TIME = 2000;

    private int[] mGroups = {R.string.tasklistview_title};
    private ArrayList<DetailProcess> mChilds;
    private final AbsListView.LayoutParams mLayoutParamsGroup;
    private final AbsListView.LayoutParams mLayoutParamsChild;
    private final int mLeftPadding;
    private Context mContext;
    private PackageManager mPackageManager;
    private TaskManager mTaskManager;
    private H mHandler = new H();
    private OnTaskActionListener mOnTaskActionListener;
    final IntentFilter mPackageFilter = new IntentFilter();
    private BroadcastReceiver mPackageReceiver = new PackageIntentReceiver();
    private static final Object mLock = new Object();
    private boolean mExpanded;
    private Intent mHomeIntent;
    private LayoutInflater mInflater;
    private boolean mShowTips = false;
    private static final int FLOAT_VIEW_DISPLAY_TIME = 2000;

    public interface Constants {
        public final String TAG = "TaskExpandedList";
        public final boolean DEBUG = false;
        public final boolean WAKELOCKDEBUG = false;
        public final boolean PROCESSDEBUG = false;
    }

    public TaskExpandableListAdapter(Context context) {
        super();
        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mTaskManager = new TaskManager(mContext);
        mLeftPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.tasklist_app_icon_size);

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayoutParamsGroup = new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLayoutParamsChild = new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, mLeftPadding);

        mPackageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        mPackageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mPackageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        mPackageFilter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
        mPackageFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        mPackageFilter.addAction(Intent.ACTION_UID_REMOVED);
        mPackageFilter.addDataScheme("package");
        mContext.registerReceiver(mPackageReceiver, mPackageFilter);
        mHomeIntent = new Intent(Intent.ACTION_MAIN);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
    }

    public void unregisterReceiver() {
        if (mPackageReceiver != null) mContext.unregisterReceiver(mPackageReceiver);
    }

    public static interface OnTaskActionListener {
        public void onTaskKilled();
        public void onTaskBroughtToFront();
    }

    public void setOnTaskActionListener(OnTaskActionListener onTaskActionListener) {
        mOnTaskActionListener = onTaskActionListener;
    }

    public DetailProcess getChild(int groupPosition, int childPosition) {
        synchronized (mLock) {
            final boolean validChild = childPosition >= 0 && childPosition < mChilds.size();
            if (validChild) {
                return mChilds.get(childPosition);
            } else {
                return null;
            }
        }
    }

    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    public int getChildrenCount(int groupPosition) {
        synchronized (mLock) {
            return mChilds!=null ? mChilds.size() : 0;
        }
    }

    public void updateChildsList() {
        synchronized (mLock) {
            mChilds = mTaskManager.getList();
            notifyDataSetChanged();
        }
    }

    public TextView getGenericView() {
        TextView textView = new TextView(mContext);
        textView.setLayoutParams(mLayoutParamsGroup);
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        textView.setPadding(mLeftPadding, 0, 0, 0);
        return textView;
    }

    public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        if (null == convertView) {
            convertView = mInflater.inflate(R.layout.tasklist_item, null);
        }
        TextView textView = (TextView)convertView.findViewById(R.id.taskname);
        TextView memSizeView = (TextView)convertView.findViewById(R.id.memorysize);
        TextView textViewKill = (TextView)convertView.findViewById(R.id.kill);
        ImageView imageView2 = (ImageView)convertView.findViewById(R.id.icon);
        final DetailProcess dp = getChild(groupPosition, childPosition);
        if (dp != null) {
            final String name = dp.getTitle();
            textView.setText(name);
            memSizeView.setText(dp.getMemSizeStr());
            textView.setTag("taskitem-" + childPosition);
            //textView.setLayoutParams(mLayoutParamsChild);
            textViewKill.setTag("killchild-" + childPosition);
            textViewKill.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    final String tag = (String)v.getTag();
                    if (tag.startsWith("killchild-")) {
                        try {
                            Message msg = Message.obtain();
                            msg.what = MSG_KILL_ONE;
                            msg.arg1 = Integer.valueOf(tag.substring(10));
                            mHandler.sendMessage(msg);
                        } catch (NumberFormatException ex) {
                            //unexecutable
                        }
                    }
                }
            });

            imageView2.setImageDrawable(dp.getIcon());
            imageView2.setTag("icon-" + childPosition);
            imageView2.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    final String tag = (String)v.getTag();
                    Log.v("TestTask","imageView2 get childs tag=" + tag);
                    if (tag.startsWith("icon-")) {
                        try {
                            Message msg = Message.obtain();
                            msg.what = MSG_BRING_TO_FRONT;
                            msg.arg1 = Integer.valueOf(tag.substring(5));
                            mHandler.sendMessage(msg);
                            Log.v("TestTask","imageView2 get childs arg1=" + msg.arg1);
                        } catch (NumberFormatException ex) {
                            //unexecutable
                        }
                    }
                }
            });

            textView.setFocusable(true);
            textView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    final String tag = (String)v.getTag();
                    Log.v("TestTask","textView get childs tag=" + tag);
                    if (true || tag.startsWith("taskitem-")) {
                        try {
                            Message msg = Message.obtain();
                            msg.what = MSG_BRING_TO_FRONT;
                            msg.arg1 = Integer.valueOf(tag.substring(9));
                            mHandler.sendMessage(msg);
                            Log.v("TestTask","textView get childs arg1=" + msg.arg1);
                        } catch (NumberFormatException ex) {
                            //unexecutable
                        }
                    }
                }
            });
        }
        return convertView;
    }

    public Integer getGroup(int groupPosition) {
        return mGroups[groupPosition];
    }

    public int getGroupCount() {
        return mGroups.length;
    }

    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    public View getGroupView(int groupPosition,
            boolean isExpanded, View convertView, ViewGroup parent) {
        if (null == convertView) {
            convertView = mInflater.inflate(R.layout.tasklist_group, null);
        }
        TextView title = (TextView)convertView.findViewById(R.id.category);

        //add ProgressBar
        ProgressBar memProgressBar =
                (ProgressBar)convertView.findViewById(R.id.memUsageProgressBar);
        int max = (int)readTotalMem();
        int currentMem = (int)readAvailMem();
        memProgressBar.setMax(max);
        memProgressBar.setProgress(currentMem);

        TextView memUsage = (TextView)convertView.findViewById(R.id.memUsage);
        int groupTitle = getGroup(groupPosition);
        title.setText(groupTitle);
        title.setTag("category-" + groupPosition);
        TextView killall = (TextView)convertView.findViewById(R.id.killall);
        killall.setText(R.string.tasklist_stopall);
        killall.setTag("killall-" + groupPosition);
        killall.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mHandler.sendEmptyMessage(MSG_KILL_ALL);
            }
        });
        killall.setEnabled(mExpanded);

        //taskmanager memUsage setvisibility
        if (mExpanded) {
            memUsage.setVisibility(View.VISIBLE);
            String availMem = Formatter.formatShortFileSize(mContext, readAvailMem());
            String totalMem = Formatter.formatShortFileSize(mContext, readTotalMem());
            String toast = mContext.getString(
                    R.string.tasklist_memory_usage,
                    availMem,
                    totalMem);
            memUsage.setText(toast);
            //add ProgressBar
            memProgressBar.setVisibility(View.VISIBLE);
        } else {
            memUsage.setVisibility(View.GONE);
            //add ProgressBar
            memProgressBar.setVisibility(View.GONE);
        }
        return convertView;
    }

    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);
        mChilds = null;
        mHandler.removeMessages(MSG_AUTO_LOAD_TIMER);
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
        mHandler.sendEmptyMessageDelayed(MSG_AUTO_LOAD_TIMER,AUTO_LOAD_RUNNING_PROCESS_TIME);
    }

    public void expandGroup(View v, int groupPosition) {
        mHandler.sendEmptyMessage(MSG_NORMAL_REFRESH);
        mExpanded = true;
    }

    public void collapseGroup(View v, int groupPosition) {
        mHandler.sendEmptyMessage(MSG_STOP_REFRESH);
        mExpanded = false;
    }

    public void killAllChild() {
        synchronized (mLock) {
            if (mChilds != null) {
                if (DEBUG) {
                    Log.v(TAG,"killALLChild get childs size " + mChilds.size());
                }
                int allHomes = findHome();
                String prefHome = allHomes > 1 ? findPrefHome():null;
                for (final DetailProcess detailProcess : mChilds) {
                    final String packageName = detailProcess.getPackageName();
                    if (!detailProcess.isHome() || allHomes > 1) {
                        if (DEBUG) {
                            Log.v(TAG,"killALLChild packageName " + packageName);
                            if (prefHome != null) {
                                Log.v(TAG,"killALLChild prefHome " + prefHome);
                            }
                        }
                        if (!packageName.equals(prefHome)) {
                            if (detailProcess.isHome()) {
                                clearPrefer(packageName);
                                allHomes --;
                            }
                            killChildByName(packageName);
                        }
                    }
                }
                mChilds.clear();
            } else {
                Log.e(TAG,"killAllChild got childs null");
            }
            notifyDataSetChanged();
        }
    }

    public void killChildByName(String packageName) {
        if (packageName == null) {
            Log.e(TAG,"killChildByName got packageName null");
            return;
        }
        mTaskManager.mActivityManager.forceStopPackage(packageName);
        mOnTaskActionListener.onTaskKilled();
    }

    public void killChild(int childPosition) {
        synchronized (mLock) {
            if (childPosition < mChilds.size()) {
                final DetailProcess detailProcess = mChilds.get(childPosition);
                final String packageName = detailProcess.getPackageName();
                if (detailProcess.isHome()) {
                    if (findHome() == 1) {
                        showTips(R.string.message_keep_one_launcher);
                        return;
                    } else {
                        clearPrefer(packageName);
                    }
                }
                if (DEBUG) {
                    Log.d(TAG,"ready to kill " + packageName);
                }
                mTaskManager.mActivityManager.forceStopPackage(packageName);
                mOnTaskActionListener.onTaskKilled();
            } else {
                Log.e(TAG,"kill child with illegle pos");
            }
            notifyDataSetChanged();
        }
    }

    public void bringChildtoFront(int childPosition) {
        boolean success = false;
        synchronized (mLock) {
            if (childPosition < mChilds.size()) {
                final DetailProcess detailProcess = mChilds.get(childPosition);
                final String packageName = detailProcess.getPackageName();
                if (DEBUG) {
                    Log.d(TAG,"bring " + packageName + " to front!");
                }
                if (packageName.equals(mContext.getPackageName())) {
                    return;
                }
                Intent i = mTaskManager.getBringtoFrontIntent(packageName);
                if (i == null) {
                    i = detailProcess.getIntent();
                }
                if (i != null) {
                    try {
                        i.setFlags((i.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                   | Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(i);
                        success = true;
                    } catch (Exception ee) {
                        //start activity exception
                        Log.d(TAG,"start activity meets exception " + ee.getMessage());
                    }
                }
                if (!success) {
                    i = new Intent();
                    i.setComponent(detailProcess.getBaseActivity());
                    try {
                        i.setFlags((i.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                   | Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(i);
                    } catch (Exception ee) {
                        //start activity exception
                        Log.d(TAG,"start activity meets exception " + ee.getMessage());
                    }
                }
                mOnTaskActionListener.onTaskBroughtToFront();
            }
        }
    }

    public int findHome() {
        int result = 0;
        if (FILTER_LAUNCHER) {
            //ignore find result
            result = 1;
        } else {
            if (mChilds != null && mChilds.size() > 0) {
                //find home
                for (DetailProcess dp : mChilds) {
                    if (dp.isHome()) {
                        result++;
                    }
                }
            } else {
                result = 0;
            }
        }
        if (DEBUG) {
            Log.d(TAG,"got " + result + " home(s)");
        }
        return result;
    }

    public String findPrefHome() {
        final IPackageManager mPmService = android.app.ActivityThread.getPackageManager();
        ResolveInfo preferred;
        try {
            preferred = mPmService.resolveIntent(mHomeIntent, null,
                    PackageManager.MATCH_DEFAULT_ONLY,UserHandle.myUserId());
        } catch (RemoteException e) {
            preferred = null;
        }
        if (preferred != null) {
            if (DEBUG) {
                Log.d(TAG, "preferred resolver info: " + preferred.activityInfo.packageName);
            }
            return preferred.activityInfo.packageName;
        }
        return null;
    }

    public void startHome() {
        Intent homeIntent = buildHomeIntent(Intent.CATEGORY_HOME);
        if (homeIntent != null) {
            try {
                mContext.startActivity(homeIntent);
            } catch (ActivityNotFoundException e) {
            }
        }
    }

    public void clearPrefer(String packagename) {
        ResolveInfo preferred;
        final IPackageManager mPmService = android.app.ActivityThread.getPackageManager();
        try {
            preferred = mPmService.resolveIntent(mHomeIntent, null,
                    PackageManager.MATCH_DEFAULT_ONLY,UserHandle.myUserId());
        } catch (RemoteException e) {
            preferred = null;
        }
        if (preferred != null &&
            packagename.equals(preferred.activityInfo.packageName)) {
            if (DEBUG) {
                Log.v(TAG,"clearPrefer packagename " + packagename);
                Log.v(TAG,"clearPrefer preferred " + preferred.activityInfo.packageName);
            }
            mPackageManager.clearPackagePreferredActivities(packagename);
        }
    }

    static Intent buildHomeIntent(String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }


    private long extractMemValue(byte[] buffer, int index) {
        while (index < buffer.length && buffer[index] != '\n') {
            if (buffer[index] >= '0' && buffer[index] <= '9') {
                int start = index;
                index++;
                while (index < buffer.length && buffer[index] >= '0'
                    && buffer[index] <= '9') {
                    index++;
                }
                String str = new String(buffer, 0, start, index-start);
                return ((long)Integer.parseInt(str)) * 1024;
            }
            index++;
        }
        return 0;
    }

    private boolean matchText(byte[] buffer, int index, String text) {
        int N = text.length();
        if ((index+N) >= buffer.length) {
            return false;
        }
        for (int i=0; i<N; i++) {
            if (buffer[index+i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private long readAvailMem() {
        byte[] buffer = new byte[1024];
        try {
            long memFree = 0;
            long memCached = 0;
            FileInputStream is = new FileInputStream("/proc/meminfo");
            final String MEM_FREE = "MemFree";
            final String MEM_CACHED = "Cached";
            int len = is.read(buffer);
            is.close();
            final int BUFLEN = buffer.length;
            for (int i=0; i<len && (memFree == 0 || memCached == 0); i++) {
                if (matchText(buffer, i, MEM_FREE)) {
                    i += MEM_FREE.length();
                    memFree = extractMemValue(buffer, i);
                } else if (matchText(buffer, i, MEM_CACHED)) {
                    i += MEM_CACHED.length();
                    memCached = extractMemValue(buffer, i);
                }
                while (i < BUFLEN && buffer[i] != '\n') {
                    i++;
                }
            }
            return memFree + memCached;
        } catch (java.io.FileNotFoundException e) {
        } catch (java.io.IOException e) {
        }
        return 0;
    }

    private long readTotalMem() {
        byte[] buffer = new byte[1024];
        try {
            long memTotal = 0;
            FileInputStream is = new FileInputStream("/proc/meminfo");
            final String MEM_TOTAL = "MemTotal";
            int len = is.read(buffer);
            is.close();
            final int BUFLEN = buffer.length;
            for (int i=0; i<len && memTotal == 0; i++) {
                if (matchText(buffer, i, MEM_TOTAL)) {
                    i += MEM_TOTAL.length();
                    memTotal = extractMemValue(buffer, i);
                }
                while (i < BUFLEN && buffer[i] != '\n') {
                    i++;
                }
            }
            return memTotal;
        } catch (java.io.FileNotFoundException e) {
        } catch (java.io.IOException e) {
        }
        return 0;
    }

    public class DetailProcess implements Comparable {
        private ActivityInfo mActivityInfo = null;
        private String mPackageName = null;
        private ActivityManager.RunningTaskInfo mTaskInfo = null;
        private String mTitle = null;
        private Drawable mIcon;
        private PackageManager mPackageManager;
        private Intent mIntent = null;
        private boolean mIsHome;
        private String mMemSizeStr = null;

        public void setMemSizeStr(String sizeStr) {
            mMemSizeStr = sizeStr;
        }

        public String getMemSizeStr() {
            return mMemSizeStr != null ? mMemSizeStr : "";
        }

        public DetailProcess(Context mContext, ActivityManager.RunningTaskInfo info) {
            mTaskInfo = info;
            mPackageManager = mContext.getPackageManager();
            if (mTaskInfo != null) {
                mPackageName = mTaskInfo.baseActivity.getPackageName();
            }
        }

        public void setHome(boolean ishome) {
            mIsHome = ishome;
        }

        public boolean isHome() {
            return mIsHome;
        }

        public String getPackageName() {
            return mPackageName != null ? mPackageName:null;
        }

        public ComponentName getBaseActivity() {
            if (mTaskInfo != null) {
                return mTaskInfo.baseActivity;
            }
            return null;
        }

        public ComponentName getTopActivity() {
            if (mTaskInfo != null) {
                return mTaskInfo.topActivity;
            }
            return null;
        }

        public Intent getIntent() {
            if (mIntent != null) {
                return mIntent;
            }
            mIntent = new Intent();
            mIntent.setComponent(getBaseActivity());
            mIntent = mIntent.cloneFilter();
            mIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            return mIntent;
        }

        public String getTitle() {
            if (mTitle == null) {
                if (mActivityInfo == null) {
                    try {
                        mActivityInfo = mPackageManager.getActivityInfo(getBaseActivity(), 0);
                    } catch (NameNotFoundException e) {
                        Log.e(TAG,"name not found when get getTitle");
                    }
                }

                if (mActivityInfo != null) {
                    mTitle = mActivityInfo.loadLabel(mPackageManager).toString();
                }
            }
            return mTitle != null ? mTitle:"";
        }

        public Drawable getIcon() {
            if (mIcon == null) {
                if (getBaseActivity() != null) {
                    try {
                        mIcon = mPackageManager.getActivityIcon(getBaseActivity());
                    } catch (NameNotFoundException e) {
                        Log.e(TAG,"name not found when get Icon");
                    }
                }
            }
            return mIcon;
        }

        public boolean filter() {
            if (mPackageName != null) {
                return mPackageName.equals("com.android.phone")
                        || mPackageName.equals("com.android.dialer")
                        || mPackageName.equals("com.quicinc.fmradio");
            }
            return true;
        }

        public int compareTo(Object another) {
            if (another instanceof DetailProcess && another != null) {
                return this.getTitle().compareTo(((DetailProcess) another).getTitle());
            }
            return -1;
        }

        public String dump() {
            StringBuilder sb = new StringBuilder();
            return sb.toString();
        }
    }

    public class TaskManager implements Constants {
        public final String TAG = "TaskManager";
        private final int MAX_TASK_NUM = 100;
        private ActivityManager mActivityManager = null;
        private PackageManager mPackageManager;
        private ArrayList<DetailProcess> mDetailProcessList;
        private boolean mTaskListLoading = false;
        private List<ActivityManager.RecentTaskInfo> mRecentTasks;

        public TaskManager(Context context) {
            mActivityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            mPackageManager = context.getPackageManager();
        }

        //get bring to front intent for a running app
        //we get it from recent task to make sure it works
        public Intent getBringtoFrontIntent(String packagename) {
            mRecentTasks = mActivityManager.getRecentTasks(MAX_TASK_NUM,
                    ActivityManager.RECENT_IGNORE_UNAVAILABLE);
            int numTasks = mRecentTasks.size();
            for (int i = 0; i < numTasks ; ++i) {
                final ActivityManager.RecentTaskInfo info = mRecentTasks.get(i);
                Intent intent = new Intent(info.baseIntent);
                if (info.origActivity != null) {
                    intent.setComponent(info.origActivity);
                }
                intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                final ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0);
                if (resolveInfo != null
                        && packagename.equals(resolveInfo.activityInfo.packageName)) {
                    return intent;
                } else {
                    continue;
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        synchronized public void getRunningProcess() {
            if (DEBUG) {
                Log.d(TAG,"getRunningProcess start time0 " + SystemClock.uptimeMillis());
            }
            final List<RunningTaskInfo> listrti = mActivityManager.getRunningTasks(MAX_TASK_NUM);
            mDetailProcessList = new ArrayList<DetailProcess>();

            if (DEBUG) {
                for (RunningTaskInfo rti : listrti) {
                    Log.d(TAG,"RunningTaskInfo getPackageName() "
                            + rti.baseActivity.getPackageName());
                    Log.d(TAG,"RunningTaskInfo description() " + rti.description);
                    Log.d(TAG,"RunningTaskInfo topActivity() " + rti.topActivity.getPackageName());
                }
            }

            List<ResolveInfo> homeResolveList = mPackageManager.queryIntentActivities(mHomeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                    | PackageManager.GET_RESOLVED_FILTER);
            if (DEBUG) {
                final int count = homeResolveList.size();
                for (int i = 0; i < count; i++) {
                    Log.d(TAG,"getRunningProcess home has "
                            + homeResolveList.get(i).activityInfo.packageName);
                }
            }
            for (RunningTaskInfo rti : listrti) {
                DetailProcess dp = new DetailProcess(mContext, rti);
                final String psPackageName = dp.getPackageName();
                boolean needshow = true;

                if (FILTER_LAUNCHER || mContext.getResources()
                        .getBoolean(R.bool.config_showRecentsTopButtons)) {
                    // Skip the current home activity.
                    if (homeResolveList!= null) {
                        final int count = homeResolveList.size();
                        for (int i = 0; i < count; i++) {
                            if (homeResolveList.get(i).activityInfo.packageName
                                    .equals(psPackageName)) {
                                needshow = false;
                                break;
                            }
                        }
                    }
                } else {
                    // mark home activity.
                    if (DEBUG) {
                        Log.d(TAG,"getRunningProcess psPackageName " + psPackageName);
                    }
                    if (needshow) {
                        if (homeResolveList!= null) {
                            final int count = homeResolveList.size();
                            if (DEBUG) {
                                Log.v(TAG,"get " + count + " home(s) in system ");
                            }
                            for (int i = 0; i < count; i++) {
                                if (DEBUG) {
                                    Log.v(TAG,"get home name "
                                            + homeResolveList.get(i).activityInfo.packageName);
                                }
                                if (homeResolveList.get(i).activityInfo.packageName
                                        .equals(psPackageName)) {
                                    if (DEBUG) {
                                        Log.d(TAG,"sethome " + psPackageName);
                                    }
                                    dp.setHome(true);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (FILTER_ON) {
                    if (needshow && appNeedHide(psPackageName)) {
                        needshow = false;
                    }
                }
                boolean running = false;
                if (needshow) {
                    List<RunningAppProcessInfo> procs =
                            mActivityManager.getRunningAppProcesses();
                    try {
                        ApplicationInfo appInfo =
                                mPackageManager.getApplicationInfo(psPackageName, 0 /* no flags */);
                        for (RunningAppProcessInfo proc : procs) {
                            final String pname = proc.processName;
                            if (pname.equals(appInfo.processName)) {
                                running = true;
                                dp.setMemSizeStr(getMemSizeStr(proc.pid));
                                break;
                            }
                        }
                    } catch (NameNotFoundException e) {
                    }
                }
                if (needshow && running && !dp.filter()) {
                    //get title at the first time when we load running task to avoid some sync problem
                    dp.getTitle();
                    //get icon at the first time when we load running task to avoid some sync problem
                    dp.getIcon();
                    mDetailProcessList.add(dp);
                }
            }
            // Collections.sort(listdp, APP_NAME_COMPARATOR);
            mHandler.sendEmptyMessage(MSG_LOAD_FINISHED);
            if (DEBUG) {
                Log.d(TAG,"getRunningProcess end time " + SystemClock.uptimeMillis());
            }
        }

        private String getMemSizeStr(int pid) {
            int pids[] = {pid};
            Debug.MemoryInfo[] dinfos = mActivityManager.getProcessMemoryInfo(pids);
            int size = dinfos != null ? dinfos[0].getTotalPss() : 0;
            // getTotalPss() return total PSS memory usage in kB. Change it to B.
            size = 1024 * size;
            return Formatter.formatShortFileSize(mContext, size);
        }

        public ArrayList<DetailProcess> getList() {
            return mDetailProcessList;
        }

        public void refresh() {
            mTaskListLoading = true;
            Thread t = new Thread(new Runnable() {
                public void run() {
                // TODO Auto-generated method stub
                getRunningProcess();
                }
            });
            t.start();
        }

        public boolean appNeedHide(String psPackageName) {//app filter
            if ("com.android.stk".equals(psPackageName)||
                    "com.android.settings".equals(psPackageName)||
                    "com.android.bluetooth".equals(psPackageName)||
                    "com.android.systemui".equals(psPackageName)){
                return true;
            }else{
                for(DetailProcess dp:getList()){
                    String packageName = dp.getPackageName();
                    if(packageName.equals(psPackageName)){
                    //Log.d(TAG,"already have the app named := " + psPackageName);
                        return true;
                    }
                }
                //Log.d(TAG,"appNeedshow psPackageName= " + psPackageName);
                return false;
            }
        }
    }

    private class H extends Handler {
        public void handleMessage(Message m) {
            if (m.what == MSG_LOAD_FINISHED) {
                updateChildsList();
                mTaskManager.mTaskListLoading = false;
            } else if (m.what == MSG_AUTO_LOAD_TIMER) {
                if (hasMessages(MSG_AUTO_LOAD_TIMER)) {
                    removeMessages(MSG_AUTO_LOAD_TIMER);
                }
                if (hasMessages(MSG_NORMAL_REFRESH)) {
                    removeMessages(MSG_NORMAL_REFRESH);
                }
                if (!mTaskManager.mTaskListLoading) {
                    mTaskManager.refresh();
                }
                sendEmptyMessageDelayed(MSG_AUTO_LOAD_TIMER,
                        AUTO_LOAD_RUNNING_PROCESS_TIME);
            } else if (m.what == MSG_NORMAL_REFRESH) {
                if (hasMessages(MSG_NORMAL_REFRESH)) {
                    removeMessages(MSG_NORMAL_REFRESH);
                }
                if (hasMessages(MSG_AUTO_LOAD_TIMER)) {
                    removeMessages(MSG_AUTO_LOAD_TIMER);
                }
                if (!mTaskManager.mTaskListLoading) {
                    mTaskManager.refresh();
                }
                sendEmptyMessageDelayed(MSG_AUTO_LOAD_TIMER,
                        AUTO_LOAD_RUNNING_PROCESS_TIME);
            } else if (m.what == MSG_STOP_REFRESH) {
                removeMessages(MSG_AUTO_LOAD_TIMER);
                removeMessages(MSG_NORMAL_REFRESH);
                removeMessages(MSG_KILL_ONE);
                removeMessages(MSG_KILL_ALL);
            } else if (m.what == MSG_KILL_ONE) {
                if (hasMessages(MSG_LOAD_FINISHED)) {
                    mTaskManager.mTaskListLoading = false;
                    removeMessages(MSG_LOAD_FINISHED);
                }
                killChild(m.arg1);
            } else if (m.what == MSG_KILL_ALL) {
                if (hasMessages(MSG_LOAD_FINISHED)) {
                    mTaskManager.mTaskListLoading = false;
                    removeMessages(MSG_LOAD_FINISHED);
                }
                killAllChild();
            } else if (m.what == MSG_BRING_TO_FRONT) {
                bringChildtoFront(m.arg1);
            }
        }
    }

    //observe package relative intents
    private class PackageIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("PackageIntentReceiver","onReceive " + intent.toString());
            mHandler.sendEmptyMessage(MSG_NORMAL_REFRESH);
        }
    }

    private void showTips(int resid) {
        if (mShowTips) {
            Log.w(TAG,"The floating window is showing, stop showing another one.");
            return;
        }

        //Android toast is not able to be shown on top of notifications, so
        //implement a floating window to replace it.
        LayoutInflater inflate = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View floatView = inflate.inflate(
                com.android.internal.R.layout.transient_notification, null);
        final TextView textView = (TextView) floatView
               .findViewById(com.android.internal.R.id.message);

        textView.setText(mContext.getString(resid));
        final WindowManager windowManager = (WindowManager) mContext
                .getSystemService(mContext.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.y = windowManager.getDefaultDisplay().getHeight()/3;
        params.windowAnimations = com.android.internal.R.style.Animation_Toast;
        windowManager.addView(floatView, params);
        mShowTips = true;

        //close notification in two seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                windowManager.removeView(floatView);
                mShowTips = false;
            }
        }, FLOAT_VIEW_DISPLAY_TIME);
     }

    private static final Collator sCollator = Collator.getInstance();
    public static final Comparator<DetailProcess> APP_NAME_COMPARATOR
    = new Comparator<DetailProcess>() {
        public final int compare(DetailProcess a, DetailProcess b) {
            return sCollator.compare(a.getTitle(), b.getTitle());
        }
    };
}
