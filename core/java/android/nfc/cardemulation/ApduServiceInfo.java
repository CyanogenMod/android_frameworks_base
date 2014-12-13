/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.nfc.cardemulation;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @hide
 */
public final class ApduServiceInfo implements Parcelable {
    static final String TAG = "ApduServiceInfo";

    /**
     * The service that implements this
     */
    final ResolveInfo mService;

    /**
     * Description of the service
     */
    final String mDescription;

    /**
     * Whether this service represents AIDs running on the host CPU
     */
    final boolean mOnHost;

    /**
     * Mapping from category to static AID group
     */
    final HashMap<String, AidGroup> mStaticAidGroups;

    /**
     * Mapping from category to dynamic AID group
     */
    final HashMap<String, AidGroup> mDynamicAidGroups;

    /**
     * Whether this service should only be started when the device is unlocked.
     */
    final boolean mRequiresDeviceUnlock;

    /**
     * The id of the service banner specified in XML.
     */
    final int mBannerResourceId;

    /**
     * The uid of the package the service belongs to
     */
    final int mUid;

    /**
     * Secure Element Name if it's off host
     */
    final String mSeName;

    /**
     * @hide
     */
    public ApduServiceInfo(ResolveInfo info, boolean onHost, String description,
            ArrayList<AidGroup> staticAidGroups, ArrayList<AidGroup> dynamicAidGroups,
            boolean requiresUnlock, int bannerResource, int uid) {
        this.mService = info;
        this.mDescription = description;
        this.mStaticAidGroups = new HashMap<String, AidGroup>();
        this.mDynamicAidGroups = new HashMap<String, AidGroup>();
        this.mOnHost = onHost;
        this.mRequiresDeviceUnlock = requiresUnlock;
        for (AidGroup aidGroup : staticAidGroups) {
            this.mStaticAidGroups.put(aidGroup.category, aidGroup);
        }
        for (AidGroup aidGroup : dynamicAidGroups) {
            this.mDynamicAidGroups.put(aidGroup.category, aidGroup);
        }
        this.mBannerResourceId = bannerResource;
        this.mUid = uid;
        if (onHost)
            this.mSeName = new String("DH");
        else
            this.mSeName = new String("SIM1"); // UICC as default for off host
    }

    /**
     * @hide
     */
    public ApduServiceInfo(ResolveInfo info, boolean onHost, String description,
            ArrayList<AidGroup> staticAidGroups, ArrayList<AidGroup> dynamicAidGroups,
            boolean requiresUnlock, int bannerResource, int uid, String seName) {
        this.mService = info;
        this.mDescription = description;
        this.mStaticAidGroups = new HashMap<String, AidGroup>();
        this.mDynamicAidGroups = new HashMap<String, AidGroup>();
        this.mOnHost = onHost;
        this.mRequiresDeviceUnlock = requiresUnlock;
        for (AidGroup aidGroup : staticAidGroups) {
            this.mStaticAidGroups.put(aidGroup.category, aidGroup);
        }
        for (AidGroup aidGroup : dynamicAidGroups) {
            this.mDynamicAidGroups.put(aidGroup.category, aidGroup);
        }
        this.mBannerResourceId = bannerResource;
        this.mUid = uid;
        this.mSeName = seName;
    }

    public ApduServiceInfo(PackageManager pm, ResolveInfo info, boolean onHost) throws
            XmlPullParserException, IOException {
        ServiceInfo si = info.serviceInfo;
        XmlResourceParser parser = null;
        try {
            if (onHost) {
                parser = si.loadXmlMetaData(pm, HostApduService.SERVICE_META_DATA);
                if (parser == null) {
                    throw new XmlPullParserException("No " + HostApduService.SERVICE_META_DATA +
                            " meta-data");
                }
            } else {
                parser = si.loadXmlMetaData(pm, OffHostApduService.SERVICE_META_DATA);
                if (parser == null) {
                    throw new XmlPullParserException("No " + OffHostApduService.SERVICE_META_DATA +
                            " meta-data");
                }
            }

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next();
            }

            String tagName = parser.getName();
            if (onHost && !"host-apdu-service".equals(tagName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with <host-apdu-service> tag");
            } else if (!onHost && !"offhost-apdu-service".equals(tagName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with <offhost-apdu-service> tag");
            }

            Resources res = pm.getResourcesForApplication(si.applicationInfo);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            if (onHost) {
                TypedArray sa = res.obtainAttributes(attrs,
                        com.android.internal.R.styleable.HostApduService);
                mService = info;
                mDescription = sa.getString(
                        com.android.internal.R.styleable.HostApduService_description);
                mRequiresDeviceUnlock = sa.getBoolean(
                        com.android.internal.R.styleable.HostApduService_requireDeviceUnlock,
                        false);
                mBannerResourceId = sa.getResourceId(
                        com.android.internal.R.styleable.HostApduService_apduServiceBanner, -1);
                sa.recycle();
            } else {
                TypedArray sa = res.obtainAttributes(attrs,
                        com.android.internal.R.styleable.OffHostApduService);
                mService = info;
                mDescription = sa.getString(
                        com.android.internal.R.styleable.OffHostApduService_description);
                mRequiresDeviceUnlock = false;
                mBannerResourceId = sa.getResourceId(
                        com.android.internal.R.styleable.OffHostApduService_apduServiceBanner, -1);
                sa.recycle();
            }

            mStaticAidGroups = new HashMap<String, AidGroup>();
            mDynamicAidGroups = new HashMap<String, AidGroup>();
            mOnHost = onHost;

            final int depth = parser.getDepth();
            AidGroup currentGroup = null;
            String seName = null;

            // Parsed values for the current AID group
            while (((eventType = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && eventType != XmlPullParser.END_DOCUMENT) {
                tagName = parser.getName();
                if (eventType == XmlPullParser.START_TAG && "aid-group".equals(tagName) &&
                        currentGroup == null) {
                    final TypedArray groupAttrs = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.AidGroup);
                    // Get category of AID group
                    String groupCategory = groupAttrs.getString(
                            com.android.internal.R.styleable.AidGroup_category);
                    String groupDescription = groupAttrs.getString(
                            com.android.internal.R.styleable.AidGroup_description);
                    if (!CardEmulation.CATEGORY_PAYMENT.equals(groupCategory)) {
                        groupCategory = CardEmulation.CATEGORY_OTHER;
                    }
                    currentGroup = mStaticAidGroups.get(groupCategory);
                    if (currentGroup != null) {
                        if (!CardEmulation.CATEGORY_OTHER.equals(groupCategory)) {
                            Log.e(TAG, "Not allowing multiple aid-groups in the " +
                                    groupCategory + " category");
                            currentGroup = null;
                        }
                    } else {
                        currentGroup = new AidGroup(groupCategory, groupDescription);
                    }
                    groupAttrs.recycle();
                } else if (eventType == XmlPullParser.END_TAG && "aid-group".equals(tagName) &&
                        currentGroup != null) {
                    if (currentGroup.aids.size() > 0) {
                        if (!mStaticAidGroups.containsKey(currentGroup.category)) {
                            mStaticAidGroups.put(currentGroup.category, currentGroup);
                        }
                    } else {
                        Log.e(TAG, "Not adding <aid-group> with empty or invalid AIDs");
                    }
                    currentGroup = null;
                } else if (eventType == XmlPullParser.START_TAG && "aid-filter".equals(tagName) &&
                        currentGroup != null) {
                    final TypedArray a = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.AidFilter);
                    String aid = a.getString(com.android.internal.R.styleable.AidFilter_name).
                            toUpperCase();
                    if (CardEmulation.isValidAid(aid) && !currentGroup.aids.contains(aid)) {
                        currentGroup.aids.add(aid);
                    } else {
                        Log.e(TAG, "Ignoring invalid or duplicate aid: " + aid);
                    }
                    a.recycle();
                } else if (eventType == XmlPullParser.START_TAG &&
                        "aid-prefix-filter".equals(tagName) && currentGroup != null) {
                    final TypedArray a = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.AidFilter);
                    String aid = a.getString(com.android.internal.R.styleable.AidFilter_name).
                            toUpperCase();
                    // Add wildcard char to indicate prefix
                    aid = aid.concat("*");
                    if (CardEmulation.isValidAid(aid) && !currentGroup.aids.contains(aid)) {
                        currentGroup.aids.add(aid);
                    } else {
                        Log.e(TAG, "Ignoring invalid or duplicate aid: " + aid);
                    }
                    a.recycle();
                }
                else if (eventType == XmlPullParser.START_TAG && "secure-element".equals(tagName) &&
                           currentGroup != null) {
                    final TypedArray a = res.obtainAttributes(attrs, com.android.internal.R.styleable.AidFilter);
                    seName = a.getString(com.android.internal.R.styleable.AidFilter_name);
                    a.recycle();
                }
            }
            if (mOnHost == true)
                mSeName = new String("DH");
            else {
                if (seName == null)
                    mSeName = new String("SIM1"); // UICC as default for off host
                else
                    mSeName = seName;
            }

        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to create context for: " + si.packageName);
        } finally {
            if (parser != null) parser.close();
        }
        // Set uid
        mUid = si.applicationInfo.uid;
    }

    public ComponentName getComponent() {
        return new ComponentName(mService.serviceInfo.packageName,
                mService.serviceInfo.name);
    }

    /**
     * Returns a consolidated list of AIDs from the AID groups
     * registered by this service. Note that if a service has both
     * a static (manifest-based) AID group for a category and a dynamic
     * AID group, only the dynamically registered AIDs will be returned
     * for that category.
     * @return List of AIDs registered by the service
     */
    public ArrayList<String> getAids() {
        final ArrayList<String> aids = new ArrayList<String>();
        for (AidGroup group : getAidGroups()) {
            aids.addAll(group.aids);
        }
        return aids;
    }

    /**
     * Returns the registered AID group for this category.
     */
    public AidGroup getDynamicAidGroupForCategory(String category) {
        return mDynamicAidGroups.get(category);
    }

    public boolean removeDynamicAidGroupForCategory(String category) {
        return (mDynamicAidGroups.remove(category) != null);
    }

    /**
     * Returns a consolidated list of AID groups
     * registered by this service. Note that if a service has both
     * a static (manifest-based) AID group for a category and a dynamic
     * AID group, only the dynamically registered AID group will be returned
     * for that category.
     * @return List of AIDs registered by the service
     */
    public ArrayList<AidGroup> getAidGroups() {
        final ArrayList<AidGroup> groups = new ArrayList<AidGroup>();
        for (Map.Entry<String, AidGroup> entry : mDynamicAidGroups.entrySet()) {
            groups.add(entry.getValue());
        }
        for (Map.Entry<String, AidGroup> entry : mStaticAidGroups.entrySet()) {
            if (!mDynamicAidGroups.containsKey(entry.getKey())) {
                // Consolidate AID groups - don't return static ones
                // if a dynamic group exists for the category.
                groups.add(entry.getValue());
            }
        }
        return groups;
    }

    /**
     * Returns the category to which this service has attributed the AID that is passed in,
     * or null if we don't know this AID.
     */
    public String getCategoryForAid(String aid) {
        ArrayList<AidGroup> groups = getAidGroups();
        for (AidGroup group : groups) {
            if (group.aids.contains(aid.toUpperCase())) {
                return group.category;
            }
        }
        return null;
    }

    public boolean hasCategory(String category) {
        return (mStaticAidGroups.containsKey(category) || mDynamicAidGroups.containsKey(category));
    }

    public boolean isOnHost() {
        return mOnHost;
    }

    public boolean requiresUnlock() {
        return mRequiresDeviceUnlock;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getUid() {
        return mUid;
    }

    public void setOrReplaceDynamicAidGroup(AidGroup aidGroup) {
        mDynamicAidGroups.put(aidGroup.getCategory(), aidGroup);
    }

    public CharSequence loadLabel(PackageManager pm) {
        return mService.loadLabel(pm);
    }

    public Drawable loadIcon(PackageManager pm) {
        return mService.loadIcon(pm);
    }

    public Drawable loadBanner(PackageManager pm) {
        Resources res;
        try {
            res = pm.getResourcesForApplication(mService.serviceInfo.packageName);
            Drawable banner = res.getDrawable(mBannerResourceId);
            return banner;
        } catch (NotFoundException e) {
            Log.e(TAG, "Could not load banner.");
            return null;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not load banner.");
            return null;
        }
    }

    public String getSeName() {
        return mSeName;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("ApduService: ");
        out.append(getComponent());
        out.append(", description: " + mDescription);
        out.append(", Static AID Groups: ");
        for (AidGroup aidGroup : mStaticAidGroups.values()) {
            out.append(aidGroup.toString());
        }
        out.append(", Dynamic AID Groups: ");
        for (AidGroup aidGroup : mDynamicAidGroups.values()) {
            out.append(aidGroup.toString());
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApduServiceInfo)) return false;
        ApduServiceInfo thatService = (ApduServiceInfo) o;

        return thatService.getComponent().equals(this.getComponent());
    }

    @Override
    public int hashCode() {
        return getComponent().hashCode();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mService.writeToParcel(dest, flags);
        dest.writeString(mDescription);
        dest.writeInt(mOnHost ? 1 : 0);
        dest.writeInt(mStaticAidGroups.size());
        if (mStaticAidGroups.size() > 0) {
            dest.writeTypedList(new ArrayList<AidGroup>(mStaticAidGroups.values()));
        }
        dest.writeInt(mDynamicAidGroups.size());
        if (mDynamicAidGroups.size() > 0) {
            dest.writeTypedList(new ArrayList<AidGroup>(mDynamicAidGroups.values()));
        }
        dest.writeInt(mRequiresDeviceUnlock ? 1 : 0);
        dest.writeInt(mBannerResourceId);
        dest.writeInt(mUid);
        dest.writeString(mSeName);
    };

    public static final Parcelable.Creator<ApduServiceInfo> CREATOR =
            new Parcelable.Creator<ApduServiceInfo>() {
        @Override
        public ApduServiceInfo createFromParcel(Parcel source) {
            ResolveInfo info = ResolveInfo.CREATOR.createFromParcel(source);
            String description = source.readString();
            boolean onHost = source.readInt() != 0;
            ArrayList<AidGroup> staticAidGroups = new ArrayList<AidGroup>();
            int numStaticGroups = source.readInt();
            if (numStaticGroups > 0) {
                source.readTypedList(staticAidGroups, AidGroup.CREATOR);
            }
            ArrayList<AidGroup> dynamicAidGroups = new ArrayList<AidGroup>();
            int numDynamicGroups = source.readInt();
            if (numDynamicGroups > 0) {
                source.readTypedList(dynamicAidGroups, AidGroup.CREATOR);
            }
            boolean requiresUnlock = source.readInt() != 0;
            int bannerResource = source.readInt();
            int uid = source.readInt();

            //return new ApduServiceInfo(info, onHost, description, staticAidGroups,
            //        dynamicAidGroups, requiresUnlock, bannerResource, uid);
            String seName = source.readString();
            return new ApduServiceInfo(info, onHost, description, staticAidGroups,
                    dynamicAidGroups, requiresUnlock, bannerResource, uid, seName);
        }

        @Override
        public ApduServiceInfo[] newArray(int size) {
            return new ApduServiceInfo[size];
        }
    };

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("    " + getComponent() +
                " (Description: " + getDescription() + ")");
        pw.println("    Static AID groups:");
        for (AidGroup group : mStaticAidGroups.values()) {
            pw.println("        Category: " + group.category);
            for (String aid : group.aids) {
                pw.println("            AID: " + aid);
            }
        }
        pw.println("    Dynamic AID groups:");
        for (AidGroup group : mDynamicAidGroups.values()) {
            pw.println("        Category: " + group.category);
            for (String aid : group.aids) {
                pw.println("            AID: " + aid);
            }
        }
    }
}
