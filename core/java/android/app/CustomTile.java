/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package android.app;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * A class that represents how a quick settings tile is to be presented to
 * the user using the {@link android.app.StatusBarManager}.
 *
 * This closely mirrors {@link com.android.systemui.qs.tiles.CustomQSTile} as that is the
 * final result of the tile.
 *
 * <p>The {@link CustomTile.Builder CustomTile.Builder} has been added to make it
 * easier to construct CustomTiles.</p>
 *
 * @hide
 */
public class CustomTile implements Parcelable {

    private PendingIntent onClick;
    private String onClickUri;
    private String label;
    private boolean visibility = false;
    private String contentDescription;
    private int icon;

    /**
     * Unflatten the CustomTile from a parcel.
     */
    public CustomTile(Parcel parcel)
    {
        this.onClick = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        this.onClickUri = parcel.readString();
        this.label = parcel.readString();
        //Boxing
        this.visibility = (Boolean) parcel.readValue(null);
        this.contentDescription = parcel.readString();
        this.icon = parcel.readInt();
    }

    /**
     * Constructs a CustomTile object with default values.
     * You might want to consider using {@link Builder} instead.
     */
    public CustomTile()
    {
        // Empty constructor
    }

    @Override
    public CustomTile clone() {
        CustomTile that = new CustomTile();
        cloneInto(that);
        return that;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(128);
        if (!TextUtils.isEmpty(onClickUri)) {
            b.append("onClickUri=" + onClickUri);
        }
        if (!TextUtils.isEmpty(label)) {
            b.append("label=" + label);
        }
        b.append("visibility=" + visibility);
        if (!TextUtils.isEmpty(contentDescription)) {
            b.append("contentDescription=" + contentDescription);
        }
        b.append("icon=" + icon);
        return b.toString();
    }

    /**
     * Copy all of this into that
     * @hide
     */
    public void cloneInto(CustomTile that) {
        that.onClick = this.onClick;
        that.onClickUri = this.onClickUri;
        that.label = this.label;
        that.visibility = this.visibility;
        that.contentDescription = this.contentDescription;
        that.icon = this.icon;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        PendingIntent.writePendingIntentOrNullToParcel(onClick, out);
        TextUtils.writeToParcel(onClickUri, out, flags);
        TextUtils.writeToParcel(label, out, flags);
        out.writeValue(visibility);
        TextUtils.writeToParcel(contentDescription, out, flags);
        out.writeInt(icon);
    }

    public static final Parcelable.Creator<CustomTile> CREATOR =
            new Parcelable.Creator<CustomTile>() {
                public CustomTile createFromParcel(Parcel in) {
                    return new CustomTile(in);
                }
                public CustomTile[] newArray(int size)
                {
                    return new CustomTile[][size];
                }
            };

    public PendingIntent getOnClick() {
        return onClick;
    }

    public Uri getOnClickUri() {
        return Uri.parse(onClickUri);
    }

    public String getLabel() {
        return label;
    }

    public boolean getVisibility() {
        return visibility;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public int getIcon() {
        return icon;
    }

    /**
     * Builder class for {@link CustomTile} objects.
     *
     * Provides a convenient way to set the various fields of a {@link CustomTile}
     *
     * <p>Example:
     *
     * <pre class="prettyprint">
     * CustomTile customTile = new CustomTile.Builder(mContext)
     *         .customTile.setLabel("custom label")
     *         .customTile.setContentDescription("custom description")
     *         .customTile.setOnClickIntent(pendingIntent)
     *         .customTile.setOnClickUri(Uri.parse("customuri"));
     *         .customTile.setVisibility(true)
     *         .customTile.setIcon(R.drawable.ic_launcher)
     *         .build();
     * </pre>
     */
    public static class Builder {
        private PendingIntent mOnClick;
        private String mOnClickUri;
        private String mLabel;
        private boolean mVisibility;
        private String mContentDescription;
        private int mIcon;
        private Context mContext;

        /**
         * Constructs a new Builder with the defaults:
         */
        public Builder(Context context) {
            mContext = context;
        }

        public Builder setLabel(String label) {
            mLabel = label;
            return this;
        }

        public Builder setContentDescription(String contentDescription) {
            mContentDescription = contentDescription;
            return this;
        }

        public Builder setOnClickIntent(PendingIntent intent) {
            mOnClick = intent;
            return this;
        }

        public Builder setOnClickUri(Uri uri) {
            mOnClickUri = uri.toString();
            return this;
        }

        public Builder setVisibility(boolean visibility) {
            mVisibility = visibility;
            return this;
        }

        public Builder setIcon(int drawableId) {
            mIcon = drawableId;
            return this;
        }

        public CustomTile build() {
            CustomTile tile = new CustomTile();
            tile.onClick = mOnClick;
            tile.onClickUri = mOnClickUri;
            tile.label = mLabel;
            tile.visibility = mVisibility;
            tile.contentDescription = mContentDescription;
            tile.icon = mIcon;
            return tile;
        }
    }
}
