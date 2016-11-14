/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.content.pm;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.res.XmlResourceParser;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.text.Html;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Printer;
import android.text.BidiFormatter;
import android.text.TextPaint;
import android.text.Html;
import java.text.Collator;
import java.util.Comparator;

/**
 * Base class containing information common to all package items held by
 * the package manager.  This provides a very common basic set of attributes:
 * a label, icon, and meta-data.  This class is not intended
 * to be used by itself; it is simply here to share common definitions
 * between all items returned by the package manager.  As such, it does not
 * itself implement Parcelable, but does provide convenience methods to assist
 * in the implementation of Parcelable in subclasses.
 */
public class PackageItemInfo {
    private static final float MAX_LABEL_SIZE_PX = 500f;
    /**
     * Public name of this item. From the "android:name" attribute.
     */
    public String name;
    
    /**
     * Name of the package that this item is in.
     */
    public String packageName;
    
    /**
     * A string resource identifier (in the package's resources) of this
     * component's label.  From the "label" attribute or, if not set, 0.
     */
    public int labelRes;
    
    /**
     * The string provided in the AndroidManifest file, if any.  You
     * probably don't want to use this.  You probably want
     * {@link PackageManager#getApplicationLabel}
     */
    public CharSequence nonLocalizedLabel;
    
    /**
     * A drawable resource identifier (in the package's resources) of this
     * component's icon.  From the "icon" attribute or, if not set, 0.
     */
    public int icon;
    
    /**
     * A drawable resource identifier (in the package's resources) of this
     * component's banner.  From the "banner" attribute or, if not set, 0.
     */
    public int banner;

    /**
     * A drawable resource identifier (in the package's resources) of this
     * component's logo. Logos may be larger/wider than icons and are
     * displayed by certain UI elements in place of a name or name/icon
     * combination. From the "logo" attribute or, if not set, 0. 
     */
    public int logo;
    
    /**
     * Additional meta-data associated with this component.  This field
     * will only be filled in if you set the
     * {@link PackageManager#GET_META_DATA} flag when requesting the info.
     */
    public Bundle metaData;

    /**
     * If different of UserHandle.USER_NULL, The icon of this item will be the one of that user.
     * @hide
     */
    public int showUserIcon;

    /**
     * A drawable resource identifier in the icon pack's resources
     * If there isn't an icon pack or not set, then 0.
     * @hide
     */
    public int themedIcon;

    public PackageItemInfo() {
        showUserIcon = UserHandle.USER_NULL;
    }

    public PackageItemInfo(PackageItemInfo orig) {
        name = orig.name;
        if (name != null) name = name.trim();
        packageName = orig.packageName;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        if (nonLocalizedLabel != null) nonLocalizedLabel = nonLocalizedLabel.toString().trim();
        icon = orig.icon;
        banner = orig.banner;
        logo = orig.logo;
        metaData = orig.metaData;
        showUserIcon = orig.showUserIcon;
        themedIcon = orig.themedIcon;
    }

    /**
     * Retrieve the current textual label associated with this item.  This
     * will call back on the given PackageManager to load the label from
     * the application.
     * 
     * @param pm A PackageManager from which the label can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a CharSequence containing the item's label.  If the
     * item does not have a label, its name is returned.
     */
    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null) {
            return nonLocalizedLabel;
        }
        if (labelRes != 0) {
            CharSequence label = pm.getText(packageName, labelRes, getApplicationInfo());
            if (label != null) {
                return label.toString().trim();
            }
        }
        if (name != null) {
            return name;
        }
        return packageName;
    }
 
    /**
     * Same as {@link #loadLabel(PackageManager)} with the addition that
     * the returned label is safe for being presented in the UI since it
     * will not contain new lines and the length will be limited to a
     * reasonable amount. This prevents a malicious party to influence UI
     * layout via the app label misleading the user into performing a
     * detrimental for them action. If the label is too long it will be
     * truncated and ellipsized at the end.
     *
     * @param pm A PackageManager from which the label can be loaded; usually
     * the PackageManager from which you originally retrieved this item
     * @return Returns a CharSequence containing the item's label. If the
     * item does not have a label, its name is returned.
     *
     * @hide
     */
    @SystemApi
    public @NonNull CharSequence loadSafeLabel(@NonNull PackageManager pm) {
        // loadLabel() always returns non-null
        String label = loadLabel(pm).toString();
        // strip HTML tags to avoid <br> and other tags overwriting original message
        String labelStr = Html.fromHtml(label).toString();

        // If the label contains new line characters it may push the UI
        // down to hide a part of it. Labels shouldn't have new line
        // characters, so just truncate at the first time one is seen.
        final int labelLength = labelStr.length();
        int offset = 0;
        while (offset < labelLength) {
            final int codePoint = labelStr.codePointAt(offset);
            final int type = Character.getType(codePoint);
            if (type == Character.LINE_SEPARATOR
                    || type == Character.CONTROL
                    || type == Character.PARAGRAPH_SEPARATOR) {
                labelStr = labelStr.substring(0, offset);
                break;
            }
            // replace all non-break space to " " in order to be trimmed
            if (type == Character.SPACE_SEPARATOR) {
                labelStr = labelStr.substring(0, offset) + " " + labelStr.substring(offset +
                        Character.charCount(codePoint));
            }
            offset += Character.charCount(codePoint);
        }

        labelStr = labelStr.trim();
        if (labelStr.isEmpty()) {
            return packageName;
        }
        TextPaint paint = new TextPaint();
        paint.setTextSize(42);

        return TextUtils.ellipsize(labelStr, paint, MAX_LABEL_SIZE_PX,
                TextUtils.TruncateAt.END);
    }

    /**
     * Retrieve the current graphical icon associated with this item.  This
     * will call back on the given PackageManager to load the icon from
     * the application.
     * 
     * @param pm A PackageManager from which the icon can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a Drawable containing the item's icon.  If the
     * item does not have an icon, the item's default icon is returned
     * such as the default activity icon.
     */
    public Drawable loadIcon(PackageManager pm) {
        return pm.loadItemIcon(this, getApplicationInfo());
    }

    /**
     * Retrieve the current graphical icon associated with this item without
     * the addition of a work badge if applicable.
     * This will call back on the given PackageManager to load the icon from
     * the application.
     *
     * @param pm A PackageManager from which the icon can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     *
     * @return Returns a Drawable containing the item's icon.  If the
     * item does not have an icon, the item's default icon is returned
     * such as the default activity icon.
     */
    public Drawable loadUnbadgedIcon(PackageManager pm) {
        return pm.loadUnbadgedItemIcon(this, getApplicationInfo());
    }

    /**
     * Retrieve the current graphical banner associated with this item.  This
     * will call back on the given PackageManager to load the banner from
     * the application.
     *
     * @param pm A PackageManager from which the banner can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     *
     * @return Returns a Drawable containing the item's banner.  If the item
     * does not have a banner, this method will return null.
     */
    public Drawable loadBanner(PackageManager pm) {
        if (banner != 0) {
            Drawable dr = pm.getDrawable(packageName, banner, getApplicationInfo());
            if (dr != null) {
                return dr;
            }
        }
        return loadDefaultBanner(pm);
    }

    /**
     * Retrieve the default graphical icon associated with this item.
     * 
     * @param pm A PackageManager from which the icon can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a Drawable containing the item's default icon
     * such as the default activity icon.
     * 
     * @hide
     */
    public Drawable loadDefaultIcon(PackageManager pm) {
        return pm.getDefaultActivityIcon();
    }

    /**
     * Retrieve the default graphical banner associated with this item.
     *
     * @param pm A PackageManager from which the banner can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     *
     * @return Returns a Drawable containing the item's default banner
     * or null if no default logo is available.
     *
     * @hide
     */
    protected Drawable loadDefaultBanner(PackageManager pm) {
        return null;
    }

    /**
     * Retrieve the current graphical logo associated with this item. This
     * will call back on the given PackageManager to load the logo from
     * the application.
     * 
     * @param pm A PackageManager from which the logo can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a Drawable containing the item's logo. If the item
     * does not have a logo, this method will return null.
     */
    public Drawable loadLogo(PackageManager pm) {
        if (logo != 0) {
            Drawable d = pm.getDrawable(packageName, logo, getApplicationInfo());
            if (d != null) {
                return d;
            }
        }
        return loadDefaultLogo(pm);
    }
    
    /**
     * Retrieve the default graphical logo associated with this item.
     * 
     * @param pm A PackageManager from which the logo can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * 
     * @return Returns a Drawable containing the item's default logo
     * or null if no default logo is available.
     * 
     * @hide
     */
    protected Drawable loadDefaultLogo(PackageManager pm) {
        return null;
    }
    
    /**
     * Load an XML resource attached to the meta-data of this item.  This will
     * retrieved the name meta-data entry, and if defined call back on the
     * given PackageManager to load its XML file from the application.
     * 
     * @param pm A PackageManager from which the XML can be loaded; usually
     * the PackageManager from which you originally retrieved this item.
     * @param name Name of the meta-date you would like to load.
     * 
     * @return Returns an XmlPullParser you can use to parse the XML file
     * assigned as the given meta-data.  If the meta-data name is not defined
     * or the XML resource could not be found, null is returned.
     */
    public XmlResourceParser loadXmlMetaData(PackageManager pm, String name) {
        if (metaData != null) {
            int resid = metaData.getInt(name);
            if (resid != 0) {
                return pm.getXml(packageName, resid, getApplicationInfo());
            }
        }
        return null;
    }

    /**
     * @hide Flag for dumping: include all details.
     */
    public static final int DUMP_FLAG_DETAILS = 1<<0;

    /**
     * @hide Flag for dumping: include nested ApplicationInfo.
     */
    public static final int DUMP_FLAG_APPLICATION = 1<<1;

    /**
     * @hide Flag for dumping: all flags to dump everything.
     */
    public static final int DUMP_FLAG_ALL = DUMP_FLAG_DETAILS | DUMP_FLAG_APPLICATION;

    protected void dumpFront(Printer pw, String prefix) {
        if (name != null) {
            pw.println(prefix + "name=" + name);
        }
        pw.println(prefix + "packageName=" + packageName);
        if (labelRes != 0 || nonLocalizedLabel != null || icon != 0 || banner != 0) {
            pw.println(prefix + "labelRes=0x" + Integer.toHexString(labelRes)
                    + " nonLocalizedLabel=" + nonLocalizedLabel
                    + " icon=0x" + Integer.toHexString(icon)
                    + " banner=0x" + Integer.toHexString(banner));
        }
    }
    
    protected void dumpBack(Printer pw, String prefix) {
        // no back here
    }
    
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(name);
        dest.writeString(packageName);
        dest.writeInt(labelRes);
        TextUtils.writeToParcel(nonLocalizedLabel, dest, parcelableFlags);
        dest.writeInt(icon);
        dest.writeInt(logo);
        dest.writeBundle(metaData);
        dest.writeInt(banner);
        dest.writeInt(showUserIcon);
        dest.writeInt(themedIcon);
    }
    
    protected PackageItemInfo(Parcel source) {
        name = source.readString();
        packageName = source.readString();
        labelRes = source.readInt();
        nonLocalizedLabel
                = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        icon = source.readInt();
        logo = source.readInt();
        metaData = source.readBundle();
        banner = source.readInt();
        showUserIcon = source.readInt();
        themedIcon = source.readInt();
    }

    /**
     * Get the ApplicationInfo for the application to which this item belongs,
     * if available, otherwise returns null.
     * 
     * @return Returns the ApplicationInfo of this item, or null if not known.
     * 
     * @hide
     */
    protected ApplicationInfo getApplicationInfo() {
        return null;
    }

    public static class DisplayNameComparator
            implements Comparator<PackageItemInfo> {
        public DisplayNameComparator(PackageManager pm) {
            mPM = pm;
        }

        public final int compare(PackageItemInfo aa, PackageItemInfo ab) {
            CharSequence  sa = aa.loadLabel(mPM);
            if (sa == null) sa = aa.name;
            CharSequence  sb = ab.loadLabel(mPM);
            if (sb == null) sb = ab.name;
            return sCollator.compare(sa.toString(), sb.toString());
        }

        private final Collator   sCollator = Collator.getInstance();
        private PackageManager   mPM;
    }
}
