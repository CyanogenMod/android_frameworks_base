package com.android.systemui.statusbar.appcirclesidebar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackageAdapter extends BaseAdapter implements InputItemHandler.InputHandleListener {

    private final Context mContext;
    private List<ResolveInfo> mApplications;
    private final LayoutInflater mInflater;
    private final PackageManager mPackageManager;
    private OnCircleItemClickListener mOnCircleItemClickListener;
    private Set<String> mIncludedApps = new HashSet<String>();

    private final InputItemHandler mInputItemHandler;

    private static final String[] AUTO_ADD_PACKAGES = new String[] {
        "com.android.settings",
        "com.android.phone",
        "com.android.mms",
        "com.android.dialer",
        "org.omnirom.omniswitch",
        "com.android.browser",
        "com.android.camera2",
        "com.andrew.apollo",
        "net.nurik.roman.dashclock",
        "com.android.contacts"
    };

    public PackageAdapter(final Context ctx) {
        mContext = ctx;

        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPackageManager = mContext.getPackageManager();

        mInputItemHandler = new InputItemHandler(ctx);

        mApplications = new ArrayList<ResolveInfo>();
    }

    /**
     * Reloads the application list and calls {@link #notifyDataSetChanged()} on this {@link Adapter}.
     */
    public void reloadApplications() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        mApplications = mPackageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA);

        ArrayList<ResolveInfo> whiteListedApplications = new ArrayList<ResolveInfo>();
        for (ResolveInfo info : mApplications) {
            if (!isIncludeApp(info.activityInfo.packageName)) {
                // Remove this app from the list
                whiteListedApplications.add(info);
            }
        }
        mApplications.removeAll(whiteListedApplications);

        Collections.sort(mApplications, new ResolveInfo.DisplayNameComparator(mPackageManager));

        notifyDataSetChanged();
    }

    /**
     * Since we want to loop our list we return {@link Integer#MAX_VALUE}.
     *
     * {@inheritDoc}
     */
    public int getCount() {
        return Integer.MAX_VALUE;
    }

    /**
     * Retrieves an item in a position from the range (0; {@link Integer#MAX_VALUE}).
     *
     * {@inheritDoc}
     */
    public Object getItem(final int position) {
        if (mApplications.size() > 0) {
            return mApplications.get(position % mApplications.size());
        }

        return null;
    }

    public long getItemId(final int position) {
        final ResolveInfo info = ((ResolveInfo) getItem(position));

        if (info == null) {
            return 0l;
        } else {
            return info.activityInfo.applicationInfo.uid;
        }
    }

    public View getView(final int position, final View convertView, final ViewGroup parent) {

        ViewHolder holder;

        View v = convertView;
        if (v == null) {

            v = mInflater.inflate(R.layout.circle_list_item, null);

            mInputItemHandler.registerInputHandler(v, this);

            holder = new ViewHolder();
            holder.mIcon = (ImageView) v.findViewById(R.id.icon);
            holder.mAppName = (TextView) v.findViewById(R.id.label_app_name);

            v.setTag(holder);
        } else {
            holder = (ViewHolder) v.getTag();
        }

        // We use those tags to have the access to parent and position in listeners.

        v.setTag(R.id.key_parent, parent);
        v.setTag(R.id.key_position, position);

        final ResolveInfo resolveInfo = (ResolveInfo) getItem(position);
        if (resolveInfo != null) {
            holder.mIcon.setImageDrawable(resolveInfo.loadIcon(mPackageManager));
            holder.mAppName.setText(resolveInfo.loadLabel(mPackageManager));
        } else {
            holder.mIcon.setImageDrawable(null);
            holder.mAppName.setText(null);
        }

        return v;
    }

    private class ViewHolder {
        ImageView mIcon;
        TextView mAppName;
    }

    public void setOnCircleItemClickListener(final OnCircleItemClickListener listener) {
        mOnCircleItemClickListener = listener;
    }

    public interface OnCircleItemClickListener {
        void onClick(View v, BaseAdapter adapter);
        void onLongClick(View v, BaseAdapter adapter);
    }

    @Override
    public void handleOnClickEvent(View v) {
        if (mOnCircleItemClickListener != null) {
            mOnCircleItemClickListener.onClick(v, PackageAdapter.this);
        }
    }

    @Override
    public void handleOnLongClickEvent(View v) {
        if (mOnCircleItemClickListener != null) {
            mOnCircleItemClickListener.onLongClick(v, PackageAdapter.this);
        }
    }

    private boolean isIncludeApp(String packageName) {
        if (mIncludedApps != null) {
            return mIncludedApps.contains(packageName);
        }
        return isAutoAddApp(packageName);
    }

    public void createIncludedAppsSet(String includedApps) {
        if (TextUtils.isEmpty(includedApps)) {
            mIncludedApps = null;
            return;
        }
        String[] appsToInclude = includedApps.split("\\|");
        mIncludedApps = new HashSet<String>(Arrays.asList(appsToInclude));
    }

    private boolean isAutoAddApp(String getPackageName) {
        boolean showingApp = false;
        for (String packageName : AUTO_ADD_PACKAGES) {
             if (packageName.equals(getPackageName)) {
                 showingApp = true;
                 break;
             }
        }
        return showingApp;
    }
}
