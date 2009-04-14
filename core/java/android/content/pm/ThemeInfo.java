/**
 * 
 */
package android.content.pm;

import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParserException;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

/**
 * Overall information about "theme" package.  This corresponds
 * to the information collected from AndroidManifest.xml (theme tag).
 *
 * Below is an example of theme tag
 *    <theme
 *        pluto:name="Pluto Default"
 *        pluto:thumbnail="@drawable/app_thumbnail"
 *        pluto:author="John Doe"
 *        pluto:ringtoneFileName="media/audio/ringtone.mp3"
 *        pluto:notificationRingtoneFileName="media/audio/locked/notification.mp3"
 *        pluto:copyright="T-Mobile, 2009"
 *        pluto:wallpaperImage="media/images/wallpaper.jpg"
 *        pluto:favesBackground="media/images/locked/background.jpg"
 *        pluto:soundpackName="<package_name>/<sound_pack_name>"
 *    />
 *
 */
public final class ThemeInfo implements Parcelable {

    /**
     * The name of the wallpaper image file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see wallpaperImage attribute
     */
    public String wallpaperImageName;

    /**
     * The name of the favorites background image file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see favesBackground attribute
     *
     */
    public String favesImageName;

    /**
     * The name of the favorite apps background image file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see favesAppBackground attribute
     *
     */
    public String favesAppImageName;

    /**
     * The resource id of theme thumbnail.
     * Specifies a theme thumbnail image resource as @drawable/foo.
     *
     * @see thumbnail attribute
     *
     */
    public int thumbnail = - 1;

    /**
     * The resource id of Android UI Style.
     * Specifies an Android UI Style as @style/bar.
     *
     * @see androidUiStyle attribute
     * 
     */
    public int theme = -1;

    /**
     * The name of the theme (as displayed by UI).
     *
     * @see name attribute
     *
     */
    public String themeName;

    /**
     * The name of the call ringtone audio file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see ringtoneFileName attribute
     *
     */
    public String ringtoneFileName;

    /**
     * The name of the call ringtone as shown to user.
     *
     * @see ringtoneName attribute
     *
     */
    public String ringtoneName;

    /**
     * The name of the notification ringtone audio file.
     * Specifies a relative path in assets subfolder.
     * If the parent's name is "locked" - DRM protected.
     *
     * @see notificationRingtoneFileName attribute
     *
     */
    public String notificationRingtoneFileName;

    /**
     * The name of the notification ringtone as shown to user.
     *
     * @see notificationRingtoneName attribute
     *
     */
    public String notificationRingtoneName;

    /**
     * The author name of the theme package.
     *
     * @see author attribute
     *
     */
    public String author;

    /**
     * The copyright text.
     *
     * @see copyright attribute
     *
     */
    public String copyright;

    // There is no corresposponding flag in manifest file
    // This flag is set to true iff any media resource is DRM protected
    public boolean isDrmProtected = false;

    /**
     * The name of a sound pack.
     *
     * @see soundpack attribute
     *
     */
    public String soundPackName;

    /**
     * {@link #themePackage}
     *
     */
    private static final int THEME_PACKAGE_INDEX = 0;

    /**
     * {@link #thumbnail}
     *
     */
    private static final int THUMBNAIL_INDEX = 1;

    /**
     * {@link #author}
     *
     */
    private static final int AUTHOR_INDEX = 2;

    /**
     * {@link #ringtoneName}
     *
     */
    private static final int RINGTONE_FILE_NAME_INDEX = 3;

    /**
     * {@link #notificationRingtoneName}
     *
     */
    private static final int NOTIFICATION_RINGTONE_FILE_NAME_INDEX = 4;

    /**
     * {@link #favesImageName}
     *
     */
    private static final int FAVES_IMAGE_NAME_INDEX = 5;

    /**
     * {@link #favesAppImageName}
     *
     */
    private static final int FAVES_APP_IMAGE_NAME_INDEX = 6;

    /**
     * {@link #wallpaperImageName}
     *
     */
    private static final int WALLPAPER_IMAGE_NAME_INDEX = 7;

    /**
     * {@link #copyright}
     *
     */
    private static final int COPYRIGHT_INDEX = 8;

    /**
     * {@link #theme}
     *
     */
    private static final int THEME_INDEX = 9;

    /**
     * {@link #ringtoneName}
     *
     */
    private static final int RINGTONE_NAME_INDEX = 10;

    /**
     * {@link #notificationRingtoneName}
     *
     */
    private static final int NOTIFICATION_RINGTONE_NAME_INDEX = 11;

    /**
     * {@link #soundPackName}
     *
     */
    private static final int SOUNDPACK_NAME_INDEX = 12;

    private static final String [] compulsoryAttributes = new String [] {
        "name",
        "thumbnail",
        "author",
    };

    private static final String [] optionalAttributes = new String [] {
        "ringtoneFileName",
        "notificationRingtoneFileName",
        "favesBackground",
        "favesAppsBackground",
        "wallpaperImage",
        "copyright",
        "androidUiStyle",
        "ringtoneName",
        "notificationRingtoneName",
        "soundpackName",
    };

    private static final String LOCKED_NAME = "locked/";

    private static Map<String, Integer> attributesLookupTable;

    static {
        attributesLookupTable = new HashMap<String, Integer>();
        for (int i = 0; i < compulsoryAttributes.length; i++) {
            attributesLookupTable.put(compulsoryAttributes[i], i);
        }

        for (int i = 0; i < optionalAttributes.length; i++) {
            attributesLookupTable.put(optionalAttributes[i], compulsoryAttributes.length + i);
        }
    }

    public ThemeInfo(AttributeSet attrs) throws XmlPullParserException {
        Map<String, Integer> tempMap = new HashMap<String, Integer>(attributesLookupTable);
        int numberOfCompulsoryAttributes = 0;
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String key = attrs.getAttributeName(i);
            if (tempMap.containsKey(key)) {
                int index = tempMap.get(key);
                tempMap.remove(key);

                if (index < compulsoryAttributes.length) {
                    numberOfCompulsoryAttributes++;
                }
                switch (index) {
                    case THEME_PACKAGE_INDEX:
                        // theme name
                        themeName = attrs.getAttributeValue(i);
                        break;

                    case THUMBNAIL_INDEX:
                        // theme thumbprint
                        thumbnail = attrs.getAttributeResourceValue(i, -1);
                        break;

                    case AUTHOR_INDEX:
                        // theme author
                        author = attrs.getAttributeValue(i);
                        break;

                    case RINGTONE_FILE_NAME_INDEX:
                        // ringtone
                        ringtoneFileName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(ringtoneFileName);
                        break;

                    case NOTIFICATION_RINGTONE_FILE_NAME_INDEX:
                        // notification ringtone
                        notificationRingtoneFileName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(notificationRingtoneFileName);
                        break;

                    case FAVES_IMAGE_NAME_INDEX:
                        // faves background
                        favesImageName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(favesImageName);
                        break;

                    case FAVES_APP_IMAGE_NAME_INDEX:
                        // favesAppBackground attribute
                        favesAppImageName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(favesAppImageName);
                        break;

                    case WALLPAPER_IMAGE_NAME_INDEX:
                        // wallpaperImage attribute
                        wallpaperImageName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(wallpaperImageName);
                        break;

                    case COPYRIGHT_INDEX:
                        // themeCopyright attribute
                        copyright = attrs.getAttributeValue(i);
                        break;

                    case THEME_INDEX:
                        // androidUiStyle attribute
                        theme = attrs.getAttributeResourceValue(i, -1);
                        break;

                    case RINGTONE_NAME_INDEX:
                        // ringtone UI name
                        ringtoneName = attrs.getAttributeValue(i);
                        break;

                    case NOTIFICATION_RINGTONE_NAME_INDEX:
                        // notification ringtone UI name
                        notificationRingtoneName = attrs.getAttributeValue(i);
                        break;

                    case SOUNDPACK_NAME_INDEX:
                        soundPackName = attrs.getAttributeValue(i);
                        break;
                }
            }
        }
        if (numberOfCompulsoryAttributes < compulsoryAttributes.length) {
            throw new XmlPullParserException("Not all compulsory attributes are specified in <theme>");
        }
    }

    /*
     * Describe the kinds of special objects contained in this Parcelable's
     * marshalled representation.
     *  
     * @return a bitmask indicating the set of special object types marshalled
     * by the Parcelable.
     *
     * @see android.os.Parcelable#describeContents()
     */
    public int describeContents() {
        return 0;
    }

    /*
     * Flatten this object in to a Parcel.
     * 
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     * May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     * 
     * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(wallpaperImageName);
        dest.writeString(favesImageName);
        dest.writeString(favesAppImageName);
        dest.writeInt(thumbnail);
        dest.writeInt(theme);
        dest.writeString(themeName);
        dest.writeString(ringtoneFileName);
        dest.writeString(notificationRingtoneFileName);
        dest.writeString(ringtoneName);
        dest.writeString(notificationRingtoneName);
        dest.writeString(author);
        dest.writeString(copyright);
        dest.writeInt(isDrmProtected? 1 : 0);
        dest.writeString(soundPackName);
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
        wallpaperImageName = source.readString();
        favesImageName = source.readString();
        favesAppImageName = source.readString();
        thumbnail = source.readInt();
        theme = source.readInt();
        themeName = source.readString();
        ringtoneFileName = source.readString();
        notificationRingtoneFileName = source.readString();
        ringtoneName = source.readString();
        notificationRingtoneName = source.readString();
        author = source.readString();
        copyright = source.readString();
        isDrmProtected = (source.readInt() != 0);
        soundPackName = source.readString();
    }

    private void changeDrmFlagIfNeeded(String resourcePath) {
        if (resourcePath != null && resourcePath.contains(LOCKED_NAME)) {
            isDrmProtected = true;
        }
    }

}
