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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
public class AppSecurityEditablePermissions extends AppSecurityPermissionsBase{

    private enum State {
        NO_PERMS,
        DANGEROUS_ONLY,
        NORMAL_ONLY,
        BOTH
    }

    private class EditableListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            PermissionInfo pi = (PermissionInfo) v.getTag();
            TextView text = (TextView) v.findViewById(R.id.editable_permission);
            SpannableString ss = (SpannableString) text.getText();

            if (mRevokedPerms.contains(pi.name)) {
                mRevokedPerms.remove(pi.name);
                StrikethroughSpan[] spans = ss.getSpans(0, text.length(), StrikethroughSpan.class);
                for (StrikethroughSpan span: spans) {
                    ss.removeSpan(span);
                }
            }
            else {
                mRevokedPerms.add(pi.name);
                ss.setSpan(new StrikethroughSpan(), 0, text.length(), 0);
            }
            String[] rp = new String[mRevokedPerms.size()];
            mRevokedPerms.toArray(rp);
            mPm.setRevokedPermissions(mPackageName, rp);
        }
    }

    private final static String TAG = "AppSecurityEditablePermissions";
    private EditableListener mEditableListener = new EditableListener();
    private boolean localLOGV = false;
    private Context mContext;
    private LayoutInflater mInflater;
    private PackageManager mPm;
    private LinearLayout mPermsView;
    private Map<String, List<PermissionInfo>> mDangerousMap;
    private Map<String, List<PermissionInfo>> mNormalMap;
    private List<PermissionInfo> mPermsList;
    private String mDefaultGrpLabel;
    private String mDefaultGrpName="DefaultGrp";
    private Drawable mNormalIcon;
    private Drawable mDangerousIcon;
    private boolean mExpanded;
    private Drawable mShowMaxIcon;
    private Drawable mShowMinIcon;
    private View mShowMore;
    private TextView mShowMoreText;
    private ImageView mShowMoreIcon;
    private State mCurrentState;
    private LinearLayout mNonDangerousList;
    private LinearLayout mDangerousList;
    private HashMap<String, CharSequence> mGroupLabelCache;
    private HashSet<String> mRevokedPerms;
    private View mNoPermsView;
    private String mPackageName;

    public AppSecurityEditablePermissions(Context context, String packageName) {
        mContext = context;
        mPm = mContext.getPackageManager();
        mPackageName = packageName;
        mRevokedPerms = new HashSet<String>();
        String[] revoked = mPm.getRevokedPermissions(packageName);
        mRevokedPerms.addAll(Arrays.asList(revoked));
        mPermsList = new ArrayList<PermissionInfo>();
        Set<PermissionInfo> permSet = new HashSet<PermissionInfo>();
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
        for(PermissionInfo tmpInfo : permSet) {
            mPermsList.add(tmpInfo);
        }
    }

    private void getAllUsedPermissions(int sharedUid, Set<PermissionInfo> permSet) {
        String sharedPkgList[] = mPm.getPackagesForUid(sharedUid);
        if(sharedPkgList == null || (sharedPkgList.length == 0)) {
            return;
        }
        for(String sharedPkg : sharedPkgList) {
            getPermissionsForPackage(sharedPkg, permSet);
        }
    }

    private void getPermissionsForPackage(String packageName,
            Set<PermissionInfo> permSet) {
        PackageInfo pkgInfo;
        try {
            pkgInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Couldn't retrieve permissions for package:"+packageName);
            return;
        }
        if ((pkgInfo != null) && (pkgInfo.requestedPermissions != null)) {
            extractPerms(pkgInfo.requestedPermissions, permSet);
        }
    }

    private void extractPerms(String strList[], Set<PermissionInfo> permSet) {
        if((strList == null) || (strList.length == 0)) {
            return;
        }
        for(String permName:strList) {
            try {
                PermissionInfo tmpPermInfo = mPm.getPermissionInfo(permName, 0);
                if(tmpPermInfo != null) {
                    permSet.add(tmpPermInfo);
                }
            } catch (NameNotFoundException e) {
                Log.i(TAG, "Ignoring unknown permission:"+permName);
            }
        }
    }

    public int getPermissionCount() {
        return mPermsList.size();
    }

    public View getPermissionsView() {
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPermsView = (LinearLayout) mInflater.inflate(R.layout.app_perms_summary, null);
        mShowMore = mPermsView.findViewById(R.id.show_more);
        mShowMoreIcon = (ImageView) mShowMore.findViewById(R.id.show_more_icon);
        mShowMoreText = (TextView) mShowMore.findViewById(R.id.show_more_text);
        mDangerousList = (LinearLayout) mPermsView.findViewById(R.id.dangerous_perms_list);
        mNonDangerousList = (LinearLayout) mPermsView.findViewById(R.id.non_dangerous_perms_list);
        mNoPermsView = mPermsView.findViewById(R.id.no_permissions);

        // Set up the LinearLayout that acts like a list item.
        mShowMore.setClickable(true);
        mShowMore.setOnClickListener(this);
        mShowMore.setFocusable(true);
        mShowMore.setBackgroundResource(android.R.drawable.list_selector_background);

        // Pick up from framework resources instead.
        mDefaultGrpLabel = mContext.getString(R.string.default_permission_group);
        mNormalIcon = mContext.getResources().getDrawable(R.drawable.ic_text_dot);
        mDangerousIcon = mContext.getResources().getDrawable(R.drawable.ic_bullet_key_permission);
        mShowMaxIcon = mContext.getResources().getDrawable(R.drawable.expander_ic_maximized);
        mShowMinIcon = mContext.getResources().getDrawable(R.drawable.expander_ic_minimized);

        // Set permissions view
        setPermissions(mPermsList);
        return mPermsView;
    }

    /**
     * Canonicalizes the group description before it is displayed to the user.
     *
     * TODO check for internationalization issues remove trailing '.' in str1
     */
    private String canonicalizeGroupDesc(String groupDesc) {
        if ((groupDesc == null) || (groupDesc.length() == 0)) {
            return null;
        }
        // Both str1 and str2 are non-null and are non-zero in size.
        int len = groupDesc.length();
        if(groupDesc.charAt(len-1) == '.') {
            groupDesc = groupDesc.substring(0, len-1);
        }
        return groupDesc;
    }

    private CharSequence getGroupLabel(String grpName) {
        if (grpName == null) {
            //return default label
            return mDefaultGrpLabel;
        }
        CharSequence cachedLabel = mGroupLabelCache.get(grpName);
        if (cachedLabel != null) {
            return cachedLabel;
        }
        PermissionGroupInfo pgi;
        try {
            pgi = mPm.getPermissionGroupInfo(grpName, 0);
        } catch (NameNotFoundException e) {
            Log.i(TAG, "Invalid group name:" + grpName);
            return null;
        }
        CharSequence label = pgi.loadLabel(mPm).toString();
        mGroupLabelCache.put(grpName, label);
        return label;
    }

    /**
     * Utility method that displays permissions from a map containing group name and
     * list of permission descriptions.
     */
    private void displayPermissions(boolean dangerous) {
        Map<String, List<PermissionInfo>> permInfoMap = dangerous ? mDangerousMap : mNormalMap;
        LinearLayout permListView = dangerous ? mDangerousList : mNonDangerousList;
        permListView.removeAllViews();

        Set<String> permInfoStrSet = permInfoMap.keySet();
        for (String loopPermGrpInfoStr : permInfoStrSet) {
            CharSequence grpLabel = getGroupLabel(loopPermGrpInfoStr);
            //guaranteed that grpLabel wont be null since permissions without groups
            //will belong to the default group
            if(localLOGV) Log.i(TAG, "Adding view group:" + grpLabel + ", desc:"
                    + permInfoMap.get(loopPermGrpInfoStr));
            permListView.addView(getPermissionItemView(grpLabel,
                    permInfoMap.get(loopPermGrpInfoStr), dangerous));
        }
    }

    private void displayNoPermissions() {
        mNoPermsView.setVisibility(View.VISIBLE);
    }

    private View getPermissionItemView(CharSequence grpName, List<PermissionInfo> list,
            boolean dangerous) {
        return getPermissionItemView(grpName, list,
                dangerous, dangerous ? mDangerousIcon : mNormalIcon);
    }

    private View getPermissionItemView(CharSequence grpName, List<PermissionInfo> list,
            boolean dangerous, Drawable icon) {
        View permView = mInflater.inflate(R.layout.app_editable_permission_item, null);

        TextView permGrpView = (TextView) permView.findViewById(R.id.editable_permission_group);
        LinearLayout permDescView = (LinearLayout) permView.findViewById(R.id.editable_permissions_list);

        ImageView imgView = (ImageView)permView.findViewById(R.id.editable_perm_icon);
        imgView.setImageDrawable(icon);
        if(grpName != null) {
            permGrpView.setText(grpName);
            for (PermissionInfo pi: list) {
                View ePermView = mInflater.inflate(R.layout.app_editable_permission, null);           
                TextView editablePermView = (TextView) ePermView.findViewById(R.id.editable_permission);
                ImageView editableImgView = (ImageView) ePermView.findViewById(R.id.editable_permission_icon);
                editableImgView.setImageDrawable(icon);

                CharSequence permDesc = pi.loadLabel(mPm);
                SpannableString text = new SpannableString(permDesc + " (" + pi.name + ")");
                if (mRevokedPerms.contains(pi.name)) {
                    text.setSpan(new StrikethroughSpan(), 0, text.length(), 0);
                }
                editablePermView.setText(text, TextView.BufferType.SPANNABLE);
                ePermView.setVisibility(View.VISIBLE);
                editablePermView.setVisibility(View.VISIBLE);
                editableImgView.setVisibility(View.GONE);
                ePermView.setClickable(true);
                ePermView.setTag(pi);
                ePermView.setOnClickListener(mEditableListener);
                permDescView.addView(ePermView);
            }
            permDescView.setVisibility(View.VISIBLE);
        } else {
            permGrpView.setText(list + " test");
            permDescView.setVisibility(View.GONE);
        }
        return permView;
    }

    private void showPermissions() {

        switch(mCurrentState) {
        case NO_PERMS:
            displayNoPermissions();
            break;

        case DANGEROUS_ONLY:
            displayPermissions(true);
            break;

        case NORMAL_ONLY:
            displayPermissions(false);
            break;

        case BOTH:
            displayPermissions(true);
            if (mExpanded) {
                displayPermissions(false);
                mShowMoreIcon.setImageDrawable(mShowMaxIcon);
                mShowMoreText.setText(R.string.perms_hide);
                mNonDangerousList.setVisibility(View.VISIBLE);
            } else {
                mShowMoreIcon.setImageDrawable(mShowMinIcon);
                mShowMoreText.setText(R.string.perms_show_all);
                mNonDangerousList.setVisibility(View.GONE);
            }
            mShowMore.setVisibility(View.VISIBLE);
            break;
        }
    }

    private boolean isDisplayablePermission(PermissionInfo pInfo) {
        if(pInfo.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS ||
                pInfo.protectionLevel == PermissionInfo.PROTECTION_NORMAL) {
            return true;
        }
        return false;
    }

    private static class PermissionInfoComparator implements Comparator<PermissionInfo> {
        private PackageManager mPm;
        private final Collator sCollator = Collator.getInstance();
        PermissionInfoComparator(PackageManager pm) {
            mPm = pm;
        }
        public final int compare(PermissionInfo a, PermissionInfo b) {
            CharSequence sa = a.loadLabel(mPm);
            CharSequence sb = b.loadLabel(mPm);
            return sCollator.compare(sa, sb);
        }
    }

    private void setPermissions(List<PermissionInfo> permList) {
        mGroupLabelCache = new HashMap<String, CharSequence>();
        //add the default label so that uncategorized permissions can go here
        mGroupLabelCache.put(mDefaultGrpName, mDefaultGrpLabel);

        // Additional structures needed to ensure that permissions are unique under 
        // each group
        mDangerousMap = new HashMap<String,  List<PermissionInfo>>();
        mNormalMap = new HashMap<String,  List<PermissionInfo>>();
        PermissionInfoComparator permComparator = new PermissionInfoComparator(mPm);

        if (permList != null) {
            // First pass to group permissions
            for (PermissionInfo pInfo : permList) {
                if(localLOGV) Log.i(TAG, "Processing permission:"+pInfo.name);
                if(!isDisplayablePermission(pInfo)) {
                    if(localLOGV) Log.i(TAG, "Permission:"+pInfo.name+" is not displayable");
                    continue;
                }
                Map<String, List<PermissionInfo> > permInfoMap =
                    (pInfo.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS) ?
                            mDangerousMap : mNormalMap;
                String grpName = (pInfo.group == null) ? mDefaultGrpName : pInfo.group;
                if(localLOGV) Log.i(TAG, "Permission:"+pInfo.name+" belongs to group:"+grpName);
                List<PermissionInfo> grpPermsList = permInfoMap.get(grpName);
                if(grpPermsList == null) {
                    grpPermsList = new ArrayList<PermissionInfo>();
                    permInfoMap.put(grpName, grpPermsList);
                    grpPermsList.add(pInfo);
                } else {
                    int idx = Collections.binarySearch(grpPermsList, pInfo, permComparator);
                    if(localLOGV) Log.i(TAG, "idx="+idx+", list.size="+grpPermsList.size());
                    if (idx < 0) {
                        idx = -idx-1;
                        grpPermsList.add(idx, pInfo);
                    }
                }
            }
        }

        mCurrentState = State.NO_PERMS;
        if(mDangerousMap.size() > 0) {
            mCurrentState = (mNormalMap.size() > 0) ? State.BOTH : State.DANGEROUS_ONLY;
        } else if(mNormalMap.size() > 0) {
            mCurrentState = State.NORMAL_ONLY;
        }
        if(localLOGV) Log.i(TAG, "mCurrentState=" + mCurrentState);
        showPermissions();
    }

    public void onClick(View v) {
        if(localLOGV) Log.i(TAG, "mExpanded="+mExpanded);
        mExpanded = !mExpanded;
        showPermissions();
    }
}
