package android.app;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.Settings;
import android.os.Parcel;
import android.os.Parcelable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

/** @hide */
public final class WallpaperSettings implements Parcelable {

    private boolean mOverride;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<WallpaperSettings> CREATOR = new Parcelable.Creator<WallpaperSettings>() {
        public WallpaperSettings createFromParcel(Parcel in) {
            return new WallpaperSettings(in);
        }

        @Override
        public WallpaperSettings[] newArray(int size) {
            return new WallpaperSettings[size];
        }
    };


    public WallpaperSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    public WallpaperSettings() {
        this(false);
    }

    public WallpaperSettings(boolean override) {
        mOverride = override;
        mDirty = false;
    }

    public void setOverride(boolean override) {
        mOverride = override;
        mDirty = true;
    }

    public boolean isOverride() {
        return mOverride;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    public void processOverride(Context context, UUID profileUuid) {
        if (isOverride()) {
    		try {
    			Context contextSettings = context.createPackageContext("com.android.settings", Context.CONTEXT_IGNORE_SECURITY);
                // Get stored wallpaper
                String filename = "wallpaper_" + profileUuid.toString() + ".jpg";
                FileInputStream fis = contextSettings.openFileInput(filename);
                Bitmap bmWallpaper = BitmapFactory.decodeStream(fis);
    			WallpaperManager mWallpaperManager = WallpaperManager.getInstance(context);
				mWallpaperManager.setBitmap(bmWallpaper);	
    		} catch (Exception e) {
                //Log.i(TAG, "Profile wallpaper not found. (UUID " + profileUuid.toString() + ")");
    		}
        }
    }

    /** @hide */
    public static WallpaperSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        WallpaperSettings WallpaperDescriptor = new WallpaperSettings();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("WallpaperDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("override")) {
                    WallpaperDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            }
            event = xpp.next();
        }
        return WallpaperDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<WallpaperDescriptor>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</WallpaperDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mOverride = in.readInt() != 0;
        mDirty = in.readInt() != 0;
    }


}