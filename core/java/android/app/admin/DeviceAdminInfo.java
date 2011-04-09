/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.app.admin;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Printer;
import android.util.SparseArray;
import android.util.Xml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class is used to specify meta information of a device administrator
 * component.
 */
public final class DeviceAdminInfo implements Parcelable {
    static final String TAG = "DeviceAdminInfo";

    /**
     * A type of policy that this device admin can use: limit the passwords
     * that the user can select, via {@link DevicePolicyManager#setPasswordQuality}
     * and {@link DevicePolicyManager#setPasswordMinimumLength}.
     *
     * <p>To control this policy, the device admin must have a "limit-password"
     * tag in the "uses-policies" section of its meta-data.
     */
    public static final int USES_POLICY_LIMIT_PASSWORD = 0;

    /**
     * A type of policy that this device admin can use: able to watch login
     * attempts from the user, via {@link DeviceAdminReceiver#ACTION_PASSWORD_FAILED},
     * {@link DeviceAdminReceiver#ACTION_PASSWORD_SUCCEEDED}, and
     * {@link DevicePolicyManager#getCurrentFailedPasswordAttempts}.
     *
     * <p>To control this policy, the device admin must have a "watch-login"
     * tag in the "uses-policies" section of its meta-data.
     */
    public static final int USES_POLICY_WATCH_LOGIN = 1;

    /**
     * A type of policy that this device admin can use: able to reset the
     * user's password via
     * {@link DevicePolicyManager#resetPassword}.
     *
     * <p>To control this policy, the device admin must have a "reset-password"
     * tag in the "uses-policies" section of its meta-data.
     */
    public static final int USES_POLICY_RESET_PASSWORD = 2;

    /**
     * A type of policy that this device admin can use: able to force the device
     * to lock via{@link DevicePolicyManager#lockNow} or limit the
     * maximum lock timeout for the device via
     * {@link DevicePolicyManager#setMaximumTimeToLock}.
     *
     * <p>To control this policy, the device admin must have a "force-lock"
     * tag in the "uses-policies" section of its meta-data.
     */
    public static final int USES_POLICY_FORCE_LOCK = 3;

    /**
     * A type of policy that this device admin can use: able to factory
     * reset the device, erasing all of the user's data, via
     * {@link DevicePolicyManager#wipeData}.
     *
     * <p>To control this policy, the device admin must have a "wipe-data"
     * tag in the "uses-policies" section of its meta-data.
     */
    public static final int USES_POLICY_WIPE_DATA = 4;

    /** @hide */
    public static class PolicyInfo {
        public final int ident;
        final public String tag;
        final public int label;
        final public int description;

        public PolicyInfo(int identIn, String tagIn, int labelIn, int descriptionIn) {
            ident = identIn;
            tag = tagIn;
            label = labelIn;
            description = descriptionIn;
        }
    }

    static ArrayList<PolicyInfo> sPoliciesDisplayOrder = new ArrayList<PolicyInfo>();
    static HashMap<String, Integer> sKnownPolicies = new HashMap<String, Integer>();
    static SparseArray<PolicyInfo> sRevKnownPolicies = new SparseArray<PolicyInfo>();

    static {
        sPoliciesDisplayOrder.add(new PolicyInfo(USES_POLICY_WIPE_DATA, "wipe-data",
                com.android.internal.R.string.policylab_wipeData,
                com.android.internal.R.string.policydesc_wipeData));
        sPoliciesDisplayOrder.add(new PolicyInfo(USES_POLICY_RESET_PASSWORD, "reset-password",
                com.android.internal.R.string.policylab_resetPassword,
                com.android.internal.R.string.policydesc_resetPassword));
        sPoliciesDisplayOrder.add(new PolicyInfo(USES_POLICY_LIMIT_PASSWORD, "limit-password",
                com.android.internal.R.string.policylab_limitPassword,
                com.android.internal.R.string.policydesc_limitPassword));
        sPoliciesDisplayOrder.add(new PolicyInfo(USES_POLICY_WATCH_LOGIN, "watch-login",
                com.android.internal.R.string.policylab_watchLogin,
                com.android.internal.R.string.policydesc_watchLogin));
        sPoliciesDisplayOrder.add(new PolicyInfo(USES_POLICY_FORCE_LOCK, "force-lock",
                com.android.internal.R.string.policylab_forceLock,
                com.android.internal.R.string.policydesc_forceLock));

        for (int i=0; i<sPoliciesDisplayOrder.size(); i++) {
            PolicyInfo pi = sPoliciesDisplayOrder.get(i);
            sRevKnownPolicies.put(pi.ident, pi);
            sKnownPolicies.put(pi.tag, pi.ident);
        }
    }

    /**
     * The BroadcastReceiver that implements this device admin component.
     */
    final ResolveInfo mReceiver;

    /**
     * Whether this should be visible to the user.
     */
    boolean mVisible;

    /**
     * The policies this administrator needs access to.
     */
    int mUsesPolicies;

    /**
     * Constructor.
     *
     * @param context The Context in which we are parsing the device admin.
     * @param receiver The ResolveInfo returned from the package manager about
     * this device admin's component.
     */
    public DeviceAdminInfo(Context context, ResolveInfo receiver)
            throws XmlPullParserException, IOException {
        mReceiver = receiver;
        ActivityInfo ai = receiver.activityInfo;

        PackageManager pm = context.getPackageManager();

        XmlResourceParser parser = null;
        try {
            parser = ai.loadXmlMetaData(pm, DeviceAdminReceiver.DEVICE_ADMIN_META_DATA);
            if (parser == null) {
                throw new XmlPullParserException("No "
                        + DeviceAdminReceiver.DEVICE_ADMIN_META_DATA + " meta-data");
            }

            Resources res = pm.getResourcesForApplication(ai.applicationInfo);

            AttributeSet attrs = Xml.asAttributeSet(parser);

            int type;
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }

            String nodeName = parser.getName();
            if (!"device-admin".equals(nodeName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with device-admin tag");
            }

            TypedArray sa = res.obtainAttributes(attrs,
                    com.android.internal.R.styleable.DeviceAdmin);

            mVisible = sa.getBoolean(
                    com.android.internal.R.styleable.DeviceAdmin_visible, true);

            sa.recycle();

            int outerDepth = parser.getDepth();
            while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                   && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                String tagName = parser.getName();
                if (tagName.equals("uses-policies")) {
                    int innerDepth = parser.getDepth();
                    while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
                           && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                            continue;
                        }
                        String policyName = parser.getName();
                        Integer val = sKnownPolicies.get(policyName);
                        if (val != null) {
                            mUsesPolicies |= 1 << val.intValue();
                        } else {
                            Log.w(TAG, "Unknown tag under uses-policies of "
                                    + getComponent() + ": " + policyName);
                        }
                    }
                }
            }
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException(
                    "Unable to create context for: " + ai.packageName);
        } finally {
            if (parser != null) parser.close();
        }
    }

    DeviceAdminInfo(Parcel source) {
        mReceiver = ResolveInfo.CREATOR.createFromParcel(source);
        mUsesPolicies = source.readInt();
    }

    /**
     * Return the .apk package that implements this device admin.
     */
    public String getPackageName() {
        return mReceiver.activityInfo.packageName;
    }

    /**
     * Return the class name of the receiver component that implements
     * this device admin.
     */
    public String getReceiverName() {
        return mReceiver.activityInfo.name;
    }

    /**
     * Return the raw information about the receiver implementing this
     * device admin.  Do not modify the returned object.
     */
    public ActivityInfo getActivityInfo() {
        return mReceiver.activityInfo;
    }

    /**
     * Return the component of the receiver that implements this device admin.
     */
    public ComponentName getComponent() {
        return new ComponentName(mReceiver.activityInfo.packageName,
                mReceiver.activityInfo.name);
    }

    /**
     * Load the user-displayed label for this device admin.
     *
     * @param pm Supply a PackageManager used to load the device admin's
     * resources.
     */
    public CharSequence loadLabel(PackageManager pm) {
        return mReceiver.loadLabel(pm);
    }

    /**
     * Load user-visible description associated with this device admin.
     *
     * @param pm Supply a PackageManager used to load the device admin's
     * resources.
     */
    public CharSequence loadDescription(PackageManager pm) throws NotFoundException {
        if (mReceiver.activityInfo.descriptionRes != 0) {
            String packageName = mReceiver.resolvePackageName;
            ApplicationInfo applicationInfo = null;
            if (packageName == null) {
                packageName = mReceiver.activityInfo.packageName;
                applicationInfo = mReceiver.activityInfo.applicationInfo;
            }
            return pm.getText(packageName,
                    mReceiver.activityInfo.descriptionRes, applicationInfo);
        }
        throw new NotFoundException();
    }

    /**
     * Load the user-displayed icon for this device admin.
     *
     * @param pm Supply a PackageManager used to load the device admin's
     * resources.
     */
    public Drawable loadIcon(PackageManager pm) {
        return mReceiver.loadIcon(pm);
    }

    /**
     * Returns whether this device admin would like to be visible to the
     * user, even when it is not enabled.
     */
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * Return true if the device admin has requested that it be able to use
     * the given policy control.  The possible policy identifier inputs are:
     * {@link #USES_POLICY_LIMIT_PASSWORD}, {@link #USES_POLICY_WATCH_LOGIN},
     * {@link #USES_POLICY_RESET_PASSWORD}, {@link #USES_POLICY_FORCE_LOCK},
     * {@link #USES_POLICY_WIPE_DATA}.
     */
    public boolean usesPolicy(int policyIdent) {
        return (mUsesPolicies & (1<<policyIdent)) != 0;
    }

    /**
     * Return the XML tag name for the given policy identifier.  Valid identifiers
     * are as per {@link #usesPolicy(int)}.  If the given identifier is not
     * known, null is returned.
     */
    public String getTagForPolicy(int policyIdent) {
        return sRevKnownPolicies.get(policyIdent).tag;
    }

    /** @hide */
    public ArrayList<PolicyInfo> getUsedPolicies() {
        ArrayList<PolicyInfo> res = new ArrayList<PolicyInfo>();
        for (int i=0; i<sPoliciesDisplayOrder.size(); i++) {
            PolicyInfo pi = sPoliciesDisplayOrder.get(i);
            if (usesPolicy(pi.ident)) {
                res.add(pi);
            }
        }
        return res;
    }

    /** @hide */
    public void writePoliciesToXml(XmlSerializer out)
            throws IllegalArgumentException, IllegalStateException, IOException {
        out.attribute(null, "flags", Integer.toString(mUsesPolicies));
    }

    /** @hide */
    public void readPoliciesFromXml(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        mUsesPolicies = Integer.parseInt(
                parser.getAttributeValue(null, "flags"));
    }

    public void dump(Printer pw, String prefix) {
        pw.println(prefix + "Receiver:");
        mReceiver.dump(pw, prefix + "  ");
    }

    @Override
    public String toString() {
        return "DeviceAdminInfo{" + mReceiver.activityInfo.name + "}";
    }

    /**
     * Used to package this object into a {@link Parcel}.
     *
     * @param dest The {@link Parcel} to be written.
     * @param flags The flags used for parceling.
     */
    public void writeToParcel(Parcel dest, int flags) {
        mReceiver.writeToParcel(dest, flags);
        dest.writeInt(mUsesPolicies);
    }

    /**
     * Used to make this class parcelable.
     */
    public static final Parcelable.Creator<DeviceAdminInfo> CREATOR =
            new Parcelable.Creator<DeviceAdminInfo>() {
        public DeviceAdminInfo createFromParcel(Parcel source) {
            return new DeviceAdminInfo(source);
        }

        public DeviceAdminInfo[] newArray(int size) {
            return new DeviceAdminInfo[size];
        }
    };

    public int describeContents() {
        return 0;
    }
}
