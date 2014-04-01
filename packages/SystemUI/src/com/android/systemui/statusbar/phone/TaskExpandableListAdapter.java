/*
 * oasis_zp@hisense add task list manager to notification area according to china mobiel spec
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

    private int[] groups = {R.string.tasklistview_title};
    private ArrayList<DetailProcess> childs;
    private final AbsListView.LayoutParams lpGroup;
    private final AbsListView.LayoutParams lpChild;
    private final int leftPadding;
    private Context ctx;
    private PackageManager pm;
    private TaskManager taskManager;
    private H mHandler = new H();
    private OnTaskActionListener mOnTaskActionListener;
    final IntentFilter sPackageFilter = new IntentFilter();
    private BroadcastReceiver mPackageReceiver = new PackageIntentReceiver();
    private static final Object sLock = new Object();
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
        ctx = context;
        pm = ctx.getPackageManager();
        taskManager = new TaskManager(ctx);
        leftPadding = context.getResources().getDimensionPixelSize(R.dimen.tasklist_app_icon_size);

        mInflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        lpGroup = new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpChild = new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, leftPadding);

        sPackageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        sPackageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        sPackageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        sPackageFilter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
        sPackageFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        sPackageFilter.addAction(Intent.ACTION_UID_REMOVED);
        sPackageFilter.addDataScheme("package");
        ctx.registerReceiver(mPackageReceiver, sPackageFilter);
        mHomeIntent = new Intent(Intent.ACTION_MAIN);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
    }

    public static interface OnTaskActionListener {
        public void onTaskKilled();
        public void onTaskBroughtToFront();
    }

    public void setOnTaskActionListener(OnTaskActionListener onTaskActionListener) {
        mOnTaskActionListener = onTaskActionListener;
    }

    public DetailProcess getChild(int groupPosition, int childPosition) {
        synchronized (sLock) {
            final boolean validChild = childPosition >= 0 && childPosition < childs.size();
            if (validChild) {
                return childs.get(childPosition);
            } else {
                return null;
            }
        }
    }

    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    public int getChildrenCount(int groupPosition) {
        synchronized (sLock) {
            return childs!=null ? childs.size() : 0;
        }
    }

    public void updateChildsList() {
        synchronized (sLock) {
            childs = taskManager.getList();
            notifyDataSetChanged();
        }
    }

    public TextView getGenericView() {
        TextView textView = new TextView(ctx);
        textView.setLayoutParams(lpGroup);
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        textView.setPadding(leftPadding, 0, 0, 0);
        return textView;
    }

    public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        if (null == convertView) {
            convertView = mInflater.inflate(R.layout.tasklist_item, null);
        }
        TextView textView = (TextView)convertView.findViewById(R.id.taskname);
        ImageView imageView = (ImageView)convertView.findViewById(R.id.kill);
        ImageView imageView2 = (ImageView)convertView.findViewById(R.id.icon);
        final DetailProcess dp = getChild(groupPosition, childPosition);
        if (dp != null) {
            final String name = dp.getTitle();
            textView.setText(name);
            textView.setTag("taskitem-" + childPosition);
            //textView.setLayoutParams(lpChild);
            imageView.setTag("killchild-" + childPosition);
            imageView.setOnClickListener(new OnClickListener() {
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
        return groups[groupPosition];
    }

    public int getGroupCount() {
        return groups.length;
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
        ProgressBar memProgressBar = (ProgressBar)convertView.findViewById(R.id.memUsageProgressBar);
        int max = (int)readTotalMem();
        int currentMem = (int)readAvailMem();
        memProgressBar.setMax(max);
        memProgressBar.setProgress(currentMem);

        TextView memUsage = (TextView)convertView.findViewById(R.id.memUsage);
        int groupTitle = getGroup(groupPosition);
        title.setText(groupTitle);
        title.setTag("category-" + groupPosition);
        TextView killall = (TextView)convertView.findViewById(R.id.killall);
        killall.setText(R.string.tasklist_killall);
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
            String availMem = Formatter.formatShortFileSize(ctx, readAvailMem());
            String totalMem = Formatter.formatShortFileSize(ctx, readTotalMem());
            String toast = ctx.getString(
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
        childs = null;
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
        synchronized (sLock) {
            if (childs != null) {
                if (DEBUG) {
                    Log.v(TAG,"killALLChild get childs size " + childs.size());
                }
                int allHomes = findHome();
                String prefHome = allHomes > 1 ? findPrefHome():null;
                for (final DetailProcess detailProcess : childs) {
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
                childs.clear();
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
        taskManager.am.forceStopPackage(packageName);
        mOnTaskActionListener.onTaskKilled();
    }

    public void killChild(int childPosition) {
        synchronized (sLock) {
            if (childPosition < childs.size()) {
                final DetailProcess detailProcess = childs.get(childPosition);
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
                taskManager.am.forceStopPackage(packageName);
                mOnTaskActionListener.onTaskKilled();
                childs.remove(childPosition);
            } else {
                Log.e(TAG,"kill child with illegle pos");
            }
            notifyDataSetChanged();
        }
    }

    public void bringChildtoFront(int childPosition) {
        boolean success = false;
        synchronized (sLock) {
            if (childPosition < childs.size()) {
                final DetailProcess detailProcess = childs.get(childPosition);
                final String packageName = detailProcess.getPackageName();
                if (DEBUG) {
                    Log.d(TAG,"bring " + packageName + " to front!");
                }
                if (packageName.equals(ctx.getPackageName())) {
                    return;
                }
                Intent i = taskManager.getBringtoFrontIntent(packageName);
                if (i == null) {
                    i = detailProcess.getIntent();
                }
                if (i != null) {
                    try {
                        i.setFlags((i.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                   | Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
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
                        ctx.startActivity(i);
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
            if (childs != null && childs.size() > 0) {
                //find home
                for (DetailProcess dp : childs) {
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
                ctx.startActivity(homeIntent);
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
            pm.clearPackagePreferredActivities(packagename);
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
        byte[] mBuffer = new byte[1024];
        try {
            long memFree = 0;
            long memCached = 0;
            FileInputStream is = new FileInputStream("/proc/meminfo");
            int len = is.read(mBuffer);
            is.close();
            final int BUFLEN = mBuffer.length;
            for (int i=0; i<len && (memFree == 0 || memCached == 0); i++) {
                if (matchText(mBuffer, i, "MemFree")) {
                    i += 7;
                    memFree = extractMemValue(mBuffer, i);
                } else if (matchText(mBuffer, i, "Cached")) {
                    i += 6;
                    memCached = extractMemValue(mBuffer, i);
                }
                while (i < BUFLEN && mBuffer[i] != '\n') {
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
        byte[] mBuffer = new byte[1024];
        try {
            long memTotal = 0;
            FileInputStream is = new FileInputStream("/proc/meminfo");
            int len = is.read(mBuffer);
            is.close();
            final int BUFLEN = mBuffer.length;
            for (int i=0; i<len && memTotal == 0; i++) {
                if (matchText(mBuffer, i, "MemTotal")) {
                    i += 7;
                    memTotal = extractMemValue(mBuffer, i);
                }
                while (i < BUFLEN && mBuffer[i] != '\n') {
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
        private ActivityInfo activityInfo = null;
        private String pkgName = null;
        private ActivityManager.RunningTaskInfo taskinfo = null;
        private String title = null;
        private Drawable icon;
        private PackageManager pm;
        private Intent intent = null;
        private boolean isHome;

        public DetailProcess(Context ctx, ActivityManager.RunningTaskInfo info) {
            taskinfo = info;
            pm = ctx.getPackageManager();
            pkgName = info.baseActivity.getPackageName();
        }

        public void setHome(boolean ishome) {
            isHome = ishome;
        }

        public boolean isHome() {
            return isHome;
        }

        public String getPackageName() {
            return pkgName != null ? pkgName:null;
        }

        public ComponentName getBaseActivity() {
            if (taskinfo != null) {
                return taskinfo.baseActivity;
            }
            return null;
        }

        public ComponentName getTopActivity() {
            if (taskinfo != null) {
                return taskinfo.topActivity;
            }
            return null;
        }

        public Intent getIntent() {
            if (intent != null) {
                return intent;
            }
            intent = new Intent();
            intent.setComponent(getBaseActivity());
            intent = intent.cloneFilter();
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            return intent;
        }

        public String getTitle() {
            if (title == null) {
                if (activityInfo == null) {
                    try {
                        activityInfo = pm.getActivityInfo(getBaseActivity(), 0);
                    } catch (NameNotFoundException e) {
                        Log.e(TAG,"name not found when get getTitle");
                    }
                }

                if (activityInfo != null) {
                    title = activityInfo.loadLabel(pm).toString();
                }
            }
            return title != null ? title:"";
        }

        public Drawable getIcon() {
            if (icon == null) {
                if (getBaseActivity() != null) {
                    try {
                        icon = pm.getActivityIcon(getBaseActivity());
                    } catch (NameNotFoundException e) {
                        Log.e(TAG,"name not found when get Icon");
                    }
                }
            }
            return icon;
        }

        public boolean filter() {
            if (pkgName != null) {
                return pkgName.equals("com.android.phone")
                        || pkgName.equals("com.quicinc.fmradio");
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
        private ActivityManager am = null;
        private PackageManager pm;
        private ArrayList<DetailProcess> listdp;
        private boolean bTaskListLoading = false;
        private List<ActivityManager.RecentTaskInfo> recentTasks;

        public TaskManager(Context context) {
            am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            pm = context.getPackageManager();
        }

        //get bring to front intent for a running app
        //we get it from recent task to make sure it works
        public Intent getBringtoFrontIntent(String packagename) {
            recentTasks = am.getRecentTasks(MAX_TASK_NUM,
                    ActivityManager.RECENT_IGNORE_UNAVAILABLE);
            int numTasks = recentTasks.size();
            for (int i = 0; i < numTasks ; ++i) {
                final ActivityManager.RecentTaskInfo info = recentTasks.get(i);
                Intent intent = new Intent(info.baseIntent);
                if (info.origActivity != null) {
                    intent.setComponent(info.origActivity);
                }
                intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
                if (resolveInfo != null && packagename.equals(resolveInfo.activityInfo.packageName)) {
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
            final List<RunningTaskInfo> listrti = am.getRunningTasks(MAX_TASK_NUM);
            listdp = new ArrayList<DetailProcess>();

            if (DEBUG) {
                for (RunningTaskInfo rti : listrti) {
                    Log.d(TAG,"RunningTaskInfo getPackageName() "
                            + rti.baseActivity.getPackageName());
                    Log.d(TAG,"RunningTaskInfo description() " + rti.description);
                    Log.d(TAG,"RunningTaskInfo topActivity() " + rti.topActivity.getPackageName());
                }
            }

            List<ResolveInfo> mHomeResolveList = pm.queryIntentActivities(mHomeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                    | PackageManager.GET_RESOLVED_FILTER);
            if (DEBUG) {
                final int count = mHomeResolveList.size();
                for (int i = 0; i < count; i++) {
                    Log.d(TAG,"getRunningProcess home has "
                            + mHomeResolveList.get(i).activityInfo.packageName);
                }
            }
            for (RunningTaskInfo rti : listrti) {
                DetailProcess dp = new DetailProcess(ctx, rti);
                final String psPackageName = dp.getPackageName();
                boolean needshow = true;

                if (FILTER_LAUNCHER) {
                    // Skip the current home activity.
                    if (mHomeResolveList!= null) {
                        final int count = mHomeResolveList.size();
                        for (int i = 0; i < count; i++) {
                            if (mHomeResolveList.get(i).activityInfo.packageName
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
                    if (FILTER_ON) {
                        if (appNeedHide(psPackageName)) {
                            needshow = false;
                        }
                    }
                    if (needshow) {
                        if (mHomeResolveList!= null) {
                            final int count = mHomeResolveList.size();
                            if (DEBUG) {
                                Log.v(TAG,"get " + count + " home(s) in system ");
                            }
                            for (int i = 0; i < count; i++) {
                                if (DEBUG) {
                                    Log.v(TAG,"get home name "
                                            + mHomeResolveList.get(i).activityInfo.packageName);
                                }
                                if (mHomeResolveList.get(i).activityInfo.packageName
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
                if (needshow && !dp.filter()) {
                    //get title at the first time when we load running task to avoid some sync problem
                    dp.getTitle();
                    //get icon at the first time when we load running task to avoid some sync problem
                    dp.getIcon();
                    listdp.add(dp);
                }
            }
            // Collections.sort(listdp, APP_NAME_COMPARATOR);
            mHandler.sendEmptyMessage(MSG_LOAD_FINISHED);
            if (DEBUG) {
                Log.d(TAG,"getRunningProcess end time " + SystemClock.uptimeMillis());
            }
        }

        public ArrayList<DetailProcess> getList() {
            return listdp;
        }

        public void refresh() {
            bTaskListLoading = true;
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
                taskManager.bTaskListLoading = false;
            } else if (m.what == MSG_AUTO_LOAD_TIMER) {
                if (hasMessages(MSG_AUTO_LOAD_TIMER)) {
                    removeMessages(MSG_AUTO_LOAD_TIMER);
                }
                if (hasMessages(MSG_NORMAL_REFRESH)) {
                    removeMessages(MSG_NORMAL_REFRESH);
                }
                if (!taskManager.bTaskListLoading) {
                    taskManager.refresh();
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
                if (!taskManager.bTaskListLoading) {
                    taskManager.refresh();
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
                    taskManager.bTaskListLoading = false;
                    removeMessages(MSG_LOAD_FINISHED);
                }
                killChild(m.arg1);
            } else if (m.what == MSG_KILL_ALL) {
                if (hasMessages(MSG_LOAD_FINISHED)) {
                    taskManager.bTaskListLoading = false;
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
                ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View floatView = inflate.inflate(
                com.android.internal.R.layout.transient_notification, null);
        final TextView textView = (TextView) floatView
               .findViewById(com.android.internal.R.id.message);

        textView.setText(ctx.getString(resid));
        final WindowManager windowManager = (WindowManager) ctx
                .getSystemService(ctx.WINDOW_SERVICE);
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
