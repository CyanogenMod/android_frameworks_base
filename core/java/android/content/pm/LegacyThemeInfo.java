/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
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

import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.content.res.Resources;

/**
 * Overall information about "theme" package.  This corresponds
 * to the information collected from AndroidManifest.xml (theme tag).
 *
 * Below is an example of theme tag
 *    <theme
 *        pluto:name="Pluto Default"
 *        pluto:preview="@drawable/preview"
 *        pluto:author="John Doe"
 *        pluto:ringtoneFileName="media/audio/ringtone.mp3"
 *        pluto:notificationRingtoneFileName="media/audio/locked/notification.mp3"
 *        pluto:copyright="T-Mobile, 2009"
 *    />
 *
 * @hide
 */
public final class LegacyThemeInfo extends BaseThemeInfo {
    private enum AttributeIndex {
        THEME_PACKAGE_INDEX,
        PREVIEW_INDEX,
        AUTHOR_INDEX,
        THEME_INDEX,
        THEME_STYLE_NAME_INDEX,
        THUMBNAIL_INDEX,
        RINGTONE_FILE_NAME_INDEX,
        NOTIFICATION_RINGTONE_FILE_NAME_INDEX,
        WALLPAPER_IMAGE_INDEX,
        COPYRIGHT_INDEX,
        RINGTONE_NAME_INDEX,
        NOTIFICATION_RINGTONE_NAME_INDEX,
        STYLE_INDEX;

        public static AttributeIndex get(int ordinal) {
            return values()[ordinal];
        }
    };

    private static final String PLUTO_SCHEMA = "http://www.w3.org/2001/pluto.html";

    private static final String [] compulsoryAttributes = new String [] {
            "name",
            "preview",
            "author",
            "themeId",
            "styleName",
    };

    private static final String [] optionalAttributes = new String [] {
            "thumbnail",
            "ringtoneFileName",
            "notificationRingtoneFileName",
            "wallpaperImage",
            "copyright",
            "ringtoneName",
            "notificationRingtoneName",
            "styleId",
    };

    private static final Map<String, AttributeIndex> sAttributesLookupTable;

    static {
        sAttributesLookupTable = new HashMap<String, AttributeIndex>();
        for (int i = 0; i < compulsoryAttributes.length; i++) {
            sAttributesLookupTable.put(compulsoryAttributes[i], AttributeIndex.get(i));
        }

        for (int i = 0; i < optionalAttributes.length; i++) {
            sAttributesLookupTable.put(optionalAttributes[i],
                    AttributeIndex.get(compulsoryAttributes.length + i));
        }
    }

    public int previewResourceId = 0;
    public int thumbnailResourceId = 0;
    public int wallpaperResourceId = 0;
    public String ringtoneFileName;
    public String notificationFileName;

    public String copyright;

    public LegacyThemeInfo(XmlPullParser parser, Resources res, AttributeSet attrs) throws XmlPullParserException {
        super();

        Map<String, AttributeIndex> tempMap =
                new HashMap<String, AttributeIndex>(sAttributesLookupTable);
        int numberOfCompulsoryAttributes = 0;
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (!isPlutoNamespace(parser.getAttributeNamespace(i))) {
                continue;
            }
            String key = attrs.getAttributeName(i);
            if (tempMap.containsKey(key)) {
                AttributeIndex index = tempMap.get(key);
                tempMap.remove(key);

                if (index.ordinal() < compulsoryAttributes.length) {
                    numberOfCompulsoryAttributes++;
                }
                switch (index) {
                    case THEME_PACKAGE_INDEX:
                        // theme name
                        name = getResolvedString(res, attrs, i);
                        break;

                    case THUMBNAIL_INDEX:
                        // theme thumbprint
                        thumbnailResourceId = attrs.getAttributeResourceValue(i, 0);
                        break;

                    case AUTHOR_INDEX:
                        // theme author
                        author = getResolvedString(res, attrs, i);
                        break;

                    case THEME_INDEX:
                        // androidUiStyle attribute
                        themeId = attrs.getAttributeValue(i);
                        break;

                    case THEME_STYLE_NAME_INDEX:
                        break;

                    case RINGTONE_FILE_NAME_INDEX:
                        // ringtone
                        ringtoneFileName = attrs.getAttributeValue(i);
                        break;

                    case NOTIFICATION_RINGTONE_FILE_NAME_INDEX:
                        // notification ringtone
                        notificationFileName = attrs.getAttributeValue(i);
                        break;

                    case WALLPAPER_IMAGE_INDEX:
                        // wallpaperImage attribute
                        wallpaperResourceId = attrs.getAttributeResourceValue(i, 0);
                        break;

                    case COPYRIGHT_INDEX:
                        // themeCopyright attribute
                        copyright = getResolvedString(res, attrs, i);
                        break;

                    case RINGTONE_NAME_INDEX:
                        // ringtone UI name
                        break;

                    case NOTIFICATION_RINGTONE_NAME_INDEX:
                        // notification ringtone UI name
                        break;

                    case STYLE_INDEX:
                        break;

                    case PREVIEW_INDEX:
                        // theme thumbprint
                        previewResourceId = attrs.getAttributeResourceValue(i, 0);
                        break;
                }
            }
        }
        if (numberOfCompulsoryAttributes < compulsoryAttributes.length) {
            throw new XmlPullParserException("Not all compulsory attributes are specified in <theme>");
        }
    }

    public static final Parcelable.Creator<LegacyThemeInfo> CREATOR
            = new Parcelable.Creator<LegacyThemeInfo>() {
        public LegacyThemeInfo createFromParcel(Parcel source) {
            return new LegacyThemeInfo(source);
        }

        public LegacyThemeInfo[] newArray(int size) {
            return new LegacyThemeInfo[size];
        }
    };

    private LegacyThemeInfo(Parcel source) {
        super(source);
        previewResourceId = source.readInt();
        thumbnailResourceId = source.readInt();
        wallpaperResourceId = source.readInt();
        ringtoneFileName = source.readString();
        notificationFileName = source.readString();
    }

    /**
     * @hide
     */
    public static boolean isPlutoNamespace(String namespace) {
        return namespace != null && namespace.equalsIgnoreCase(PLUTO_SCHEMA);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(previewResourceId);
        dest.writeInt(thumbnailResourceId);
        dest.writeInt(wallpaperResourceId);
        dest.writeString(ringtoneFileName);
        dest.writeString(notificationFileName);
    }
}