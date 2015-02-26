/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

    /**
     * An optional intent to execute when the custom tile entry is clicked.  If
     * this is an activity, it must include the
     * {@link android.content.Intent#FLAG_ACTIVITY_NEW_TASK} flag, which requires
     * that you take care of task management as
     **/
    public PendingIntent onClick;

    /**
     * An optional Uri to be parsed and broadcast by
     * {@link com.android.systemui.qs.tiles.CustomQSTile#handleClick}
     **/
    public String onClickUri;

    /**
     * A label specific to the quick settings tile to be created
     */
    public String label;

    /**
     * A visibility option to hide or show the {@link com.android.systemui.qs.tiles.CustomQSTile}
     */
    public boolean visibility = false;

    /**
     * A content description for {@link com.android.systemui.qs.QSTile.State}
     */
    public String contentDescription;

    /**
     * An icon to represent the custom tile
     */
    public int icon;

    /**
     * Unflatten the CustomTile from a parcel.
     */
    public CustomTile(Parcel parcel)
    {
        if (parcel.readInt() != 0) {
            this.onClick = PendingIntent.CREATOR.createFromParcel(parcel);
        }
        if (parcel.readInt() != 0) {
            this.onClickUri = parcel.readString();
        }
        if (parcel.readInt() != 0) {
            this.label = parcel.readString();
        }

        this.visibility = parcel.readByte() != 0;
        if (parcel.readInt() != 0) {
            this.contentDescription = parcel.readString();
        }
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
        StringBuilder b = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");
        if (!TextUtils.isEmpty(onClickUri)) {
            b.append("onClickUri=" + onClickUri + NEW_LINE);
        }
        if (!TextUtils.isEmpty(label)) {
            b.append("label=" + label + NEW_LINE);
        }
        b.append("visibility=" + visibility + NEW_LINE);
        if (!TextUtils.isEmpty(contentDescription)) {
            b.append("contentDescription=" + contentDescription + NEW_LINE);
        }
        b.append("icon=" + icon + NEW_LINE);
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
        if (onClick != null) {
            out.writeInt(1);
            onClick.writeToParcel(out, 0);
        } else {
            out.writeInt(0);
        }
        if (onClickUri != null) {
            out.writeInt(1);
            out.writeString(onClickUri);
        } else {
            out.writeInt(0);
        }
        if (label != null) {
            out.writeInt(1);
            out.writeString(label);
        } else {
            out.writeInt(0);
        }
        out.writeByte((byte) (visibility ? 1 : 0));
        if (contentDescription != null) {
            out.writeInt(1);
            out.writeString(contentDescription);
        } else {
            out.writeInt(0);
        }
        out.writeInt(icon);
    }

    public static final Parcelable.Creator<CustomTile> CREATOR =
            new Parcelable.Creator<CustomTile>() {
                public CustomTile createFromParcel(Parcel in) {
                    return new CustomTile(in);
                }

                @Override
                public CustomTile[] newArray(int size) {
                    return new CustomTile[size];
                }
            };

    /**
     * Builder class for {@link CustomTile} objects.
     *
     * Provides a convenient way to set the various fields of a {@link CustomTile}
     *
     * <p>Example:
     *
     * <pre class="prettyprint">
     * CustomTile customTile = new CustomTile.Builder(mContext)
     *         .setLabel("custom label")
     *         .setContentDescription("custom description")
     *         .setOnClickIntent(pendingIntent)
     *         .setOnClickUri(Uri.parse("custom uri"));
     *         .setVisibility(true)
     *         .setIcon(R.drawable.ic_launcher)
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

        public Builder setLabel(int id) {
            mLabel = mContext.getString(id);
            return this;
        }

        public Builder setContentDescription(String contentDescription) {
            mContentDescription = contentDescription;
            return this;
        }

        public Builder setContentDescription(int id) {
            mContentDescription = mContext.getString(id);
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
