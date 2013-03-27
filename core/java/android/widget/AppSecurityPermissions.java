/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package android.widget;

import com.android.internal.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class contains the SecurityPermissions view implementation.
 * Initially the package's advanced or dangerous security permissions
 * are displayed under categorized
 * groups. Clicking on the additional permissions presents
 * extended information consisting of all groups and permissions.
 * To use this view define a LinearLayout or any ViewGroup and add this
 * view by instantiating AppSecurityPermissions and invoking getPermissionsView.
 * 
 * {@hide}
 */
public class AppSecurityPermissions {

    public static final int WHICH_PERSONAL = 1<<0;
    public static final int WHICH_DEVICE = 1<<1;
    public static final int WHICH_NEW = 1<<2;
    public static final int WHICH_ALL = 0xffff;

    private final static String TAG = "AppSecurityPermissions";
    private final static boolean localLOGV = false;
    private Context mContext;
    private LayoutInflater mInflater;
    private PackageManager mPm;
    private PackageInfo mInstalledPackageInfo;
    private final Map<String, MyPermissionGroupInfo> mPermGroups
            = new HashMap<String, MyPermissionGroupInfo>();
    private final List<MyPermissionGroupInfo> mPermGroupsList
            = new ArrayList<MyPermissionGroupInfo>();
    private final PermissionGroupInfoComparator mPermGroupComparator;
    private final PermissionInfoComparator mPermComparator;
    private List<MyPermissionInfo> mPermsList;
    private CharSequence mNewPermPrefix;
    private Drawable mNormalIcon;
    private Drawable mDangerousIcon;

    static class MyPermissionGroupInfo extends PermissionGroupInfo {
        CharSequence mLabel;

        final ArrayList<MyPermissionInfo> mNewPermissions = new ArrayList<MyPermissionInfo>();
        final ArrayList<MyPermissionInfo> mPersonalPermissions = new ArrayList<MyPermissionInfo>();
        final ArrayList<MyPermissionInfo> mDevicePermissions = new ArrayList<MyPermissionInfo>();
        final ArrayList<MyPermissionInfo> mAllPermissions = new ArrayList<MyPermissionInfo>();

        MyPermissionGroupInfo(PermissionInfo perm) {
            name = perm.packageName;
            packageName = perm.packageName;
        }

        MyPermissionGroupInfo(PermissionGroupInfo info) {
            super(info);
        }

        public Drawable loadGroupIcon(PackageManager pm) {
            if (icon != 0) {
                return loadIcon(pm);
            } else {
                ApplicationInfo appInfo;
                try {
                    appInfo = pm.getApplicationInfo(packageName, 0);
                    return appInfo.loadIcon(pm);
                } catch (NameNotFoundException e) {
                }
            }
            return null;
        }
    }

    static class MyPermissionInfo extends PermissionInfo {
        CharSequence mLabel;

        /**
         * PackageInfo.requestedPermissionsFlags for the new package being installed.
         */
        int mNewReqFlags;

        /**
         * PackageInfo.requestedPermissionsFlags for the currently installed
         * package, if it is installed.
         */
        int mExistingReqFlags;

        /**
         * True if this should be considered a new permission.
         */
        boolean mNew;

        MyPermissionInfo() {
        }

        MyPermissionInfo(PermissionInfo info) {
            super(info);
        }

        MyPermissionInfo(MyPermissionInfo info) {
            super(info);
            mNewReqFlags = info.mNewReqFlags;
            mExistingReqFlags = info.mExistingReqFlags;
            mNew = info.mNew;
        }
    }

    public static class PermissionItemView extends LinearLayout implements View.OnClickListener {
        MyPermissionGroupInfo mGroup;
        MyPermissionInfo mPerm;
        AlertDialog mDialog;

        public PermissionItemView(Context context, AttributeSet attrs) {
            super(context, attrs);
            setClickable(true);
        }

        public void setPermission(MyPermissionGroupInfo grp, MyPermissionInfo perm,
                boolean first, CharSequence newPermPrefix) {
            mGroup = grp;
            mPerm = perm;

            ImageView permGrpIcon = (ImageView) findViewById(R.id.perm_icon);
            TextView permNameView = (TextView) findViewById(R.id.perm_name);

            PackageManager pm = getContext().getPackageManager();
            Drawable icon = null;
            if (first) {
                icon = grp.loadGroupIcon(pm);
            }
            CharSequence label = perm.mLabel;
            if (perm.mNew && newPermPrefix != null) {
                // If this is a new permission, format it appropriately.
                SpannableStringBuilder builder = new SpannableStringBuilder();
                Parcel parcel = Parcel.obtain();
                TextUtils.writeToParcel(newPermPrefix, parcel, 0);
                parcel.setDataPosition(0);
                CharSequence newStr = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel);
                parcel.recycle();
                builder.append(newStr);
                builder.append(label);
                label = builder;
            }

            permGrpIcon.setImageDrawable(icon);
            permNameView.setText(label);
            setOnClickListener(this);
            if (localLOGV) Log.i(TAG, "Made perm item " + perm.name
                    + ": " + label + " in group " + grp.name);
        }

        @Override
        public void onClick(View v) {
            if (mGroup != null && mPerm != null) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                PackageManager pm = getContext().getPackageManager();
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(mGroup.mLabel);
                if (mPerm.descriptionRes != 0) {
                    builder.setMessage(mPerm.loadDescription(pm));
                } else {
                    CharSequence appName;
                    try {
                        ApplicationInfo app = pm.getApplicationInfo(mPerm.packageName, 0);
                        appName = app.loadLabel(pm);
                    } catch (NameNotFoundException e) {
                        appName = mPerm.packageName;
                    }
                    StringBuilder sbuilder = new StringBuilder(128);
                    sbuilder.append(getContext().getString(
                            R.string.perms_description_app, appName));
                    sbuilder.append("\n\n");
                    sbuilder.append(mPerm.name);
                    builder.setMessage(sbuilder.toString());
                }
                builder.setCancelable(true);
                builder.setIcon(mGroup.loadGroupIcon(pm));
                mDialog = builder.show();
                mDialog.setCanceledOnTouchOutside(true);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (mDialog != null) {
                mDialog.dismiss();
            }
        }
    }

    public AppSecurityPermissions(Context context, List<PermissionInfo> permList) {
        mContext = context;
        mPm = mContext.getPackageManager();
        loadResources();
        mPermComparator = new PermissionInfoComparator();
        mPermGroupComparator = new PermissionGroupInfoComparator();
        for (PermissionInfo pi : permList) {
            mPermsList.add(new MyPermissionInfo(pi));
        }
        setPermissions(mPermsList);
    }
    
    public AppSecurityPermissions(Context context, String packageName) {
        mContext = context;
        mPm = mContext.getPackageManager();
        loadResources();
        mPermComparator = new PermissionInfoComparator();
        mPermGroupComparator = new PermissionGroupInfoComparator();
        mPermsList = new ArrayList<MyPermissionInfo>();
        Set<MyPermissionInfo> permSet = new HashSet<MyPermissionInfo>();
        PackageInfo pkgInfo;
        try {
            pkgInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Couldn't retrieve permissions for package:"+packageName);
            return;
        }
        // Extract all user permissions
        if((pkgInfo.applicationInfo != null) && (pkgInfo.applicationInfo.uid != -1)) {
            getAllUsedPermissions(pkgInfo.applicationInfo.uid, permSet);
        }
        for(MyPermissionInfo tmpInfo : permSet) {
            mPermsList.add(tmpInfo);
        }
        setPermissions(mPermsList);
    }

    public AppSecurityPermissions(Context context, PackageInfo info) {
        mContext = context;
        mPm = mContext.getPackageManager();
        loadResources();
        mPermComparator = new PermissionInfoComparator();
        mPermGroupComparator = new PermissionGroupInfoComparator();
        mPermsList = new ArrayList<MyPermissionInfo>();
        Set<MyPermissionInfo> permSet = new HashSet<MyPermissionInfo>();
        if(info == null) {
            return;
        }

        // Convert to a PackageInfo
        PackageInfo installedPkgInfo = null;
        // Get requested permissions
        if (info.requestedPermissions != null) {
            try {
                installedPkgInfo = mPm.getPackageInfo(info.packageName,
                        PackageManager.GET_PERMISSIONS);
            } catch (NameNotFoundException e) {
            }
            extractPerms(info, permSet, installedPkgInfo);
        }
        // Get permissions related to  shared user if any
        if (info.sharedUserId != null) {
            int sharedUid;
            try {
                sharedUid = mPm.getUidForSharedUser(info.sharedUserId);
                getAllUsedPermissions(sharedUid, permSet);
            } catch (NameNotFoundException e) {
                Log.w(TAG, "Could'nt retrieve shared user id for:"+info.packageName);
            }
        }
        // Retrieve list of permissions
        for (MyPermissionInfo tmpInfo : permSet) {
            mPermsList.add(tmpInfo);
        }
        setPermissions(mPermsList);
    }

    private void loadResources() {
        // Pick up from framework resources instead.
        mNewPermPrefix = mContext.getText(R.string.perms_new_perm_prefix);
        mNormalIcon = mContext.getResources().getDrawable(R.drawable.ic_text_dot);
        mDangerousIcon = mContext.getResources().getDrawable(R.drawable.ic_bullet_key_permission);
    }

    /**
     * Utility to retrieve a view displaying a single permission.  This provides
     * the old UI layout for permissions; it is only here for the device admin
     * settings to continue to use.
     */
    public static View getPermissionItemView(Context context,
            CharSequence grpName, CharSequence description, boolean dangerous) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        Drawable icon = context.getResources().getDrawable(dangerous
                ? R.drawable.ic_bullet_key_permission : R.drawable.ic_text_dot);
        return getPermissionItemViewOld(context, inflater, grpName,
                description, dangerous, icon);
    }
    
    public PackageInfo getInstalledPackageInfo() {
        return mInstalledPackageInfo;
    }

    private void getAllUsedPermissions(int sharedUid, Set<MyPermissionInfo> permSet) {
        String sharedPkgList[] = mPm.getPackagesForUid(sharedUid);
        if(sharedPkgList == null || (sharedPkgList.length == 0)) {
            return;
        }
        for(String sharedPkg : sharedPkgList) {
            getPermissionsForPackage(sharedPkg, permSet);
        }
    }
    
    private void getPermissionsForPackage(String packageName, 
            Set<MyPermissionInfo> permSet) {
        PackageInfo pkgInfo;
        try {
            pkgInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Couldn't retrieve permissions for package:"+packageName);
            return;
        }
        if ((pkgInfo != null) && (pkgInfo.requestedPermissions != null)) {
            extractPerms(pkgInfo, permSet, pkgInfo);
        }
    }

    private void extractPerms(PackageInfo info, Set<MyPermissionInfo> permSet,
            PackageInfo installedPkgInfo) {
        String[] strList = info.requestedPermissions;
        int[] flagsList = info.requestedPermissionsFlags;
        if ((strList == null) || (strList.length == 0)) {
            return;
        }
        mInstalledPackageInfo = installedPkgInfo;
        for (int i=0; i<strList.length; i++) {
            String permName = strList[i];
            // If we are only looking at an existing app, then we only
            // care about permissions that have actually been granted to it.
            if (installedPkgInfo != null && info == installedPkgInfo) {
                if ((flagsList[i]&PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
                    continue;
                }
            }
            try {
                PermissionInfo tmpPermInfo = mPm.getPermissionInfo(permName, 0);
                if (tmpPermInfo == null) {
                    continue;
                }
                int existingIndex = -1;
                if (installedPkgInfo != null
                        && installedPkgInfo.requestedPermissions != null) {
                    for (int j=0; j<installedPkgInfo.requestedPermissions.length; j++) {
                        if (permName.equals(installedPkgInfo.requestedPermissions[j])) {
                            existingIndex = j;
                            break;
                        }
                    }
                }
                final int existingFlags = existingIndex >= 0 ?
                        installedPkgInfo.requestedPermissionsFlags[existingIndex] : 0;
                if (!isDisplayablePermission(tmpPermInfo, flagsList[i], existingFlags)) {
                    // This is not a permission that is interesting for the user
                    // to see, so skip it.
                    continue;
                }
                final String origGroupName = tmpPermInfo.group;
                String groupName = origGroupName;
                if (groupName == null) {
                    groupName = tmpPermInfo.packageName;
                    tmpPermInfo.group = groupName;
                }
                MyPermissionGroupInfo group = mPermGroups.get(groupName);
                if (group == null) {
                    PermissionGroupInfo grp = null;
                    if (origGroupName != null) {
                        grp = mPm.getPermissionGroupInfo(origGroupName, 0);
                    }
                    if (grp != null) {
                        group = new MyPermissionGroupInfo(grp);
                    } else {
                        // We could be here either because the permission
                        // didn't originally specify a group or the group it
                        // gave couldn't be found.  In either case, we consider
                        // its group to be the permission's package name.
                        tmpPermInfo.group = tmpPermInfo.packageName;
                        group = mPermGroups.get(tmpPermInfo.group);
                        if (group == null) {
                            group = new MyPermissionGroupInfo(tmpPermInfo);
                        }
                        group = new MyPermissionGroupInfo(tmpPermInfo);
                    }
                    mPermGroups.put(tmpPermInfo.group, group);
                }
                final boolean newPerm = installedPkgInfo != null
                        && (existingFlags&PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0;
                MyPermissionInfo myPerm = new MyPermissionInfo(tmpPermInfo);
                myPerm.mNewReqFlags = flagsList[i];
                myPerm.mExistingReqFlags = existingFlags;
                // This is a new permission if the app is already installed and
                // doesn't currently hold this permission.
                myPerm.mNew = newPerm;
                permSet.add(myPerm);
            } catch (NameNotFoundException e) {
                Log.i(TAG, "Ignoring unknown permission:"+permName);
            }
        }
    }
    
    public int getPermissionCount() {
        return getPermissionCount(WHICH_ALL);
    }

    private List<MyPermissionInfo> getPermissionList(MyPermissionGroupInfo grp, int which) {
        if (which == WHICH_NEW) {
            return grp.mNewPermissions;
        } else if (which == WHICH_PERSONAL) {
            return grp.mPersonalPermissions;
        } else if (which == WHICH_DEVICE) {
            return grp.mDevicePermissions;
        } else {
            return grp.mAllPermissions;
        }
    }

    public int getPermissionCount(int which) {
        int N = 0;
        for (int i=0; i<mPermGroupsList.size(); i++) {
            N += getPermissionList(mPermGroupsList.get(i), which).size();
        }
        return N;
    }

    public View getPermissionsView() {
        return getPermissionsView(WHICH_ALL);
    }

    public View getPermissionsView(int which) {
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        LinearLayout permsView = (LinearLayout) mInflater.inflate(R.layout.app_perms_summary, null);
        LinearLayout displayList = (LinearLayout) permsView.findViewById(R.id.perms_list);
        View noPermsView = permsView.findViewById(R.id.no_permissions);

        displayPermissions(mPermGroupsList, displayList, which);
        if (displayList.getChildCount() <= 0) {
            noPermsView.setVisibility(View.VISIBLE);
        }

        return permsView;
    }

    /**
     * Utility method that displays permissions from a map containing group name and
     * list of permission descriptions.
     */
    private void displayPermissions(List<MyPermissionGroupInfo> groups,
            LinearLayout permListView, int which) {
        permListView.removeAllViews();

        int spacing = (int)(8*mContext.getResources().getDisplayMetrics().density);

        for (int i=0; i<groups.size(); i++) {
            MyPermissionGroupInfo grp = groups.get(i);
            final List<MyPermissionInfo> perms = getPermissionList(grp, which);
            for (int j=0; j<perms.size(); j++) {
                MyPermissionInfo perm = perms.get(j);
                View view = getPermissionItemView(grp, perm, j == 0,
                        which != WHICH_NEW ? mNewPermPrefix : null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (j == 0) {
                    lp.topMargin = spacing;
                }
                if (j == grp.mAllPermissions.size()-1) {
                    lp.bottomMargin = spacing;
                }
                if (permListView.getChildCount() == 0) {
                    lp.topMargin *= 2;
                }
                permListView.addView(view, lp);
            }
        }
    }

    private PermissionItemView getPermissionItemView(MyPermissionGroupInfo grp,
            MyPermissionInfo perm, boolean first, CharSequence newPermPrefix) {
        return getPermissionItemView(mContext, mInflater, grp, perm, first, newPermPrefix);
    }

    private static PermissionItemView getPermissionItemView(Context context, LayoutInflater inflater,
            MyPermissionGroupInfo grp, MyPermissionInfo perm, boolean first,
            CharSequence newPermPrefix) {
        PermissionItemView permView = (PermissionItemView)inflater.inflate(
                (perm.flags & PermissionInfo.FLAG_COSTS_MONEY) != 0
                        ? R.layout.app_permission_item_money : R.layout.app_permission_item,
                null);
        permView.setPermission(grp, perm, first, newPermPrefix);
        return permView;
    }

    private static View getPermissionItemViewOld(Context context, LayoutInflater inflater,
            CharSequence grpName, CharSequence permList, boolean dangerous, Drawable icon) {
        View permView = inflater.inflate(R.layout.app_permission_item_old, null);

        TextView permGrpView = (TextView) permView.findViewById(R.id.permission_group);
        TextView permDescView = (TextView) permView.findViewById(R.id.permission_list);

        ImageView imgView = (ImageView)permView.findViewById(R.id.perm_icon);
        imgView.setImageDrawable(icon);
        if(grpName != null) {
            permGrpView.setText(grpName);
            permDescView.setText(permList);
        } else {
            permGrpView.setText(permList);
            permDescView.setVisibility(View.GONE);
        }
        return permView;
    }

    private boolean isDisplayablePermission(PermissionInfo pInfo, int newReqFlags,
            int existingReqFlags) {
        final int base = pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
        // Dangerous and normal permissions are always shown to the user.
        if (base == PermissionInfo.PROTECTION_DANGEROUS ||
                base == PermissionInfo.PROTECTION_NORMAL) {
            return true;
        }
        // Development permissions are only shown to the user if they are already
        // granted to the app -- if we are installing an app and they are not
        // already granted, they will not be granted as part of the install.
        if ((existingReqFlags&PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                && (pInfo.protectionLevel & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            if (localLOGV) Log.i(TAG, "Special perm " + pInfo.name
                    + ": protlevel=0x" + Integer.toHexString(pInfo.protectionLevel));
            return true;
        }
        return false;
    }
    
    private static class PermissionGroupInfoComparator implements Comparator<MyPermissionGroupInfo> {
        private final Collator sCollator = Collator.getInstance();
        PermissionGroupInfoComparator() {
        }
        public final int compare(MyPermissionGroupInfo a, MyPermissionGroupInfo b) {
            if (((a.flags^b.flags)&PermissionGroupInfo.FLAG_PERSONAL_INFO) != 0) {
                return ((a.flags&PermissionGroupInfo.FLAG_PERSONAL_INFO) != 0) ? -1 : 1;
            }
            if (a.priority != b.priority) {
                return a.priority > b.priority ? -1 : 1;
            }
            return sCollator.compare(a.mLabel, b.mLabel);
        }
    }
    
    private static class PermissionInfoComparator implements Comparator<MyPermissionInfo> {
        private final Collator sCollator = Collator.getInstance();
        PermissionInfoComparator() {
        }
        public final int compare(MyPermissionInfo a, MyPermissionInfo b) {
            return sCollator.compare(a.mLabel, b.mLabel);
        }
    }

    private void addPermToList(List<MyPermissionInfo> permList,
            MyPermissionInfo pInfo) {
        if (pInfo.mLabel == null) {
            pInfo.mLabel = pInfo.loadLabel(mPm);
        }
        int idx = Collections.binarySearch(permList, pInfo, mPermComparator);
        if(localLOGV) Log.i(TAG, "idx="+idx+", list.size="+permList.size());
        if (idx < 0) {
            idx = -idx-1;
            permList.add(idx, pInfo);
        }
    }

    private void setPermissions(List<MyPermissionInfo> permList) {
        if (permList != null) {
            // First pass to group permissions
            for (MyPermissionInfo pInfo : permList) {
                if(localLOGV) Log.i(TAG, "Processing permission:"+pInfo.name);
                if(!isDisplayablePermission(pInfo, pInfo.mNewReqFlags, pInfo.mExistingReqFlags)) {
                    if(localLOGV) Log.i(TAG, "Permission:"+pInfo.name+" is not displayable");
                    continue;
                }
                MyPermissionGroupInfo group = mPermGroups.get(pInfo.group);
                if (group != null) {
                    pInfo.mLabel = pInfo.loadLabel(mPm);
                    addPermToList(group.mAllPermissions, pInfo);
                    if (pInfo.mNew) {
                        addPermToList(group.mNewPermissions, pInfo);
                    }
                    if ((group.flags&PermissionGroupInfo.FLAG_PERSONAL_INFO) != 0) {
                        addPermToList(group.mPersonalPermissions, pInfo);
                    } else {
                        addPermToList(group.mDevicePermissions, pInfo);
                    }
                }
            }
        }

        for (MyPermissionGroupInfo pgrp : mPermGroups.values()) {
            if (pgrp.labelRes != 0 || pgrp.nonLocalizedLabel != null) {
                pgrp.mLabel = pgrp.loadLabel(mPm);
            } else {
                ApplicationInfo app;
                try {
                    app = mPm.getApplicationInfo(pgrp.packageName, 0);
                    pgrp.mLabel = app.loadLabel(mPm);
                } catch (NameNotFoundException e) {
                    pgrp.mLabel = pgrp.loadLabel(mPm);
                }
            }
            mPermGroupsList.add(pgrp);
        }
        Collections.sort(mPermGroupsList, mPermGroupComparator);
        if (localLOGV) {
            for (MyPermissionGroupInfo grp : mPermGroupsList) {
                Log.i(TAG, "Group " + grp.name + " personal="
                        + ((grp.flags&PermissionGroupInfo.FLAG_PERSONAL_INFO) != 0)
                        + " priority=" + grp.priority);
            }
        }
    }
}
