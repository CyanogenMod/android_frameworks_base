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

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.content.res.Resources;

/**
 * Overall information about "theme" package.  This corresponds
 * to the information collected from AndroidManifest.xml
 *
 * Below is an example of the manifest:
 *
 * <meta-data android:name="org.cyanogenmod.theme.name" android:value="Foobar's Theme"/>
 * <meta-data android:name="org.cyanogenmod.theme.author" android:value="Mr.Foo" />
 *
 * @hide
 */
public final class ThemeInfo extends BaseThemeInfo {

    public static final String META_TAG_NAME = "org.cyanogenmod.theme.name";
    public static final String META_TAG_AUTHOR = "org.cyanogenmod.theme.author";

    public ThemeInfo(Bundle bundle) {
        super();
        name = bundle.getString(META_TAG_NAME);
        themeId = name;
        author = bundle.getString(META_TAG_AUTHOR);
    }

    public static final Parcelable.Creator<ThemeInfo> CREATOR
            = new Parcelable.Creator<ThemeInfo>() {
        public ThemeInfo createFromParcel(Parcel source) {
            return new ThemeInfo(source);
        }

        public ThemeInfo[] newArray(int size) {
            return new ThemeInfo[size];
        }
    };

    private ThemeInfo(Parcel source) {
        super(source);
    }
}
