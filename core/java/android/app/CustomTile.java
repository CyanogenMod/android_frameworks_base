package android.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.regex.Pattern;

/**
 * A class that represents how a quick settings tile is to be presented to
 * the user using the {@link android.app.StatusBarManager}.
 *
 * This closely mirrors {@link com.android.systemui.qs.tiles.IntentTile} as that is the
 * final result of the tile.
 *
 * <p>The {@link CustomTile.Builder CustomTile.Builder} has been added to make it
 * easier to construct CustomTiles.</p>
 *
 * @hide
 */
public class CustomTile implements Parcelable {
    private static final String PREFIX = "customTile(";
    private static final String SUFFIX = ")";
    private static final String DELIMITER = ",";

    private PendingIntent onClick;
    private String onClickUri;
    private String label;
    private boolean visibility = false;
    private String contentDescription;
    private byte[] iconBytes;

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
        parcel.readByteArray(iconBytes);
    }

    /**
     * Constructs a CustomTile object with default values.
     * You might want to consider using {@link Builder} instead.
     */
    public CustomTile()
    {
        // Empty constructor
    }

    /**
     * Constructs a CustomTile object from an flattened intent
     * string
     * @param flatTile
     */
    public CustomTile(String flatTile) {
        unflattenFromString(flatTile);
    }

    /**
     * Unflatten a tile from a flattened string
     * @param flatTile
     */
    public void unflattenFromString(String flatTile) {
        if (TextUtils.isEmpty(flatTile) || !flatTile.startsWith(PREFIX)
                || !flatTile.endsWith(SUFFIX)) {
            throw new IllegalArgumentException("Bad custom tile spec: " + flatTile);
        }

        //Remove prefix and suffix
        String strippedString = flatTile.substring(PREFIX.length(),
                flatTile.length() - SUFFIX.length());

        final String[] array = TextUtils.split(strippedString, Pattern.quote(DELIMITER));
        for (String item : array) {
            if (TextUtils.isEmpty(item)) {
                continue;
            }
            if (item.startsWith("onClick")) {
                // SO DIRTY
                String onClickString = item.substring(item.indexOf("="), item.length());
                byte[] unmarshalled = Base64.decode(onClickString, 0);
                Parcel newParcel = Parcel.obtain();
                newParcel.unmarshall(unmarshalled, 0, unmarshalled.length);
                newParcel.setDataPosition(0);
                onClick = PendingIntent.readPendingIntentOrNullFromParcel(newParcel);
            }
            if (item.startsWith("onClickUri")) {
                onClickUri = item.substring(item.indexOf("="), item.length());
            }
        }
    }

    /**
     * Flattens a tile to string
     */
    public String flattenToString() {
        StringBuilder b = new StringBuilder(128);
        b.append(PREFIX);
        if (onClick != null) {
            // Write the parcel and marshall it so we can serialize it.
            Parcel parcel = Parcel.obtain();
            PendingIntent.writePendingIntentOrNullToParcel(onClick, parcel);
            byte[] marshalledParcel = parcel.marshall();
            b.append("onClick="
                    + Base64.encodeToString(marshalledParcel, 0, marshalledParcel.length, 0));
            b.append(DELIMITER);
        }
        if (!TextUtils.isEmpty(onClickUri)) {
            b.append("onClickUri=" + onClickUri);
            b.append(DELIMITER);
        }
        if (!TextUtils.isEmpty(label)) {
            b.append("label=" + label);
            b.append(DELIMITER);
        }
        b.append("visibility=" + visibility);
        if (!TextUtils.isEmpty(contentDescription)) {
            b.append("contentDescription=" + contentDescription);
            b.append(DELIMITER);
        }
        if (iconBytes != null) {
            b.append("iconByte=" + new String(iconBytes));
        }
        b.append(")");
        return b.toString();
    }

    @Override
    public CustomTile clone() {
        CustomTile that = new CustomTile();
        cloneInto(that);
        return that;
    }

    @Override
    public String toString() {
        return flattenToString();
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
        that.iconBytes = this.iconBytes;
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
        out.writeByteArray(iconBytes);
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

    public byte[] getIconBytes() {
        return iconBytes;
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
        private byte[] mIconBytes;
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
            this.mContentDescription = contentDescription;
            return this;
        }

        public Builder setOnClickIntent(PendingIntent intent) {
            this.mOnClick = intent;
            return this;
        }

        public Builder setOnClickUri(Uri uri) {
            this.mOnClickUri = uri.toString();
            return this;
        }

        public Builder setVisibility(boolean visibility) {
            this.mVisibility = visibility;
            return this;
        }

        public Builder setIcon(int drawableId) {
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), drawableId);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            mIconBytes = stream.toByteArray();
            return this;
        }

        public CustomTile build() {
            CustomTile tile = new CustomTile();
            tile.onClick = mOnClick;
            tile.onClickUri = mOnClickUri;
            tile.label = mLabel;
            tile.visibility = mVisibility;
            tile.contentDescription = mContentDescription;
            tile.iconBytes = mIconBytes;
            return tile;
        }
    }
}
