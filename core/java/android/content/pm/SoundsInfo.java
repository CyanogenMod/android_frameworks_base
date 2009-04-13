package android.content.pm;

import org.xmlpull.v1.XmlPullParserException;

import android.os.Parcelable;
import android.os.Parcel;
import android.util.AttributeSet;

import java.util.Map;
import java.util.HashMap;

/**
 * Overall information about sound pack.  This corresponds
 * to the information collected from AndroidManifest.xml (sounds tag).
 *
 * Below is an example of sounds tag
 *    <sounds
 *        pluto:name="Pluto Default"
 *        pluto:thumbnail="@drawable/app_thumbnail"
 *        pluto:author="John Doe"
 *        pluto:ringtoneFileName="media/audio/ringtone.mp3"
 *        pluto:notificationRingtoneFileName="media/audio/locked/notification.mp3"
 *        pluto:copyright="T-Mobile, 2009"
 *    />
 *
 */
public class SoundsInfo implements Parcelable {

    /**
     * The resource id of theme thumbnail.
     * Specifies a theme thumbnail image resource as @drawable/foo.
     *
     * @see thumbnail attribute
     *
     */
    public int thumbnail = - 1;

    /**
     * The name of the theme (as displayed by UI).
     *
     * @see name attribute
     *
     */
    public String name;

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

    private static final String [] attributes = new String [] {
        "name",
        "thumbnail",
        "author",
        "ringtoneFileName",
        "notificationRingtoneFileName",
        "ringtoneName",
        "notificationRingtoneName",
        "copyright",
    };

    private static final String LOCKED_NAME = "locked/";

    private static Map<String, Integer> attributesLookupTable;

    static {
        attributesLookupTable = new HashMap<String, Integer>();
        for (int i = 0; i < attributes.length; i++) {
            attributesLookupTable.put(attributes[i], i);
        }
    }

    /**
     * {@link #name}
     *
     */
    private static final int NAME_INDEX = 0;

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
     * {@link #ringtoneName}
     *
     */
    private static final int RINGTONE_NAME_INDEX = 5;

    /**
     * {@link #notificationRingtoneName}
     *
     */
    private static final int NOTIFICATION_RINGTONE_NAME_INDEX = 6;

    /**
     * {@link #copyright}
     *
     */
    private static final int COPYRIGHT_INDEX = 7;


    public SoundsInfo(AttributeSet attrs) throws XmlPullParserException {
        Map<String, Integer> tempMap = new HashMap<String, Integer>(attributesLookupTable);
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String key = attrs.getAttributeName(i);
            if (tempMap.containsKey(key)) {
                int index = tempMap.get(key);
                tempMap.remove(key);

                switch (index) {
                    case NAME_INDEX:
                        name = attrs.getAttributeValue(i);
                        break;

                    case THUMBNAIL_INDEX:
                        thumbnail = attrs.getAttributeResourceValue(i, -1);
                        break;

                    case AUTHOR_INDEX:
                        author = attrs.getAttributeValue(i);
                        break;

                    case RINGTONE_FILE_NAME_INDEX:
                        ringtoneFileName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(ringtoneFileName);
                        break;

                    case NOTIFICATION_RINGTONE_FILE_NAME_INDEX:
                        notificationRingtoneFileName = attrs.getAttributeValue(i);
                        changeDrmFlagIfNeeded(notificationRingtoneFileName);
                        break;

                    case RINGTONE_NAME_INDEX:
                        ringtoneName = attrs.getAttributeValue(i);
                        break;

                    case NOTIFICATION_RINGTONE_NAME_INDEX:
                        notificationRingtoneName = attrs.getAttributeValue(i);

                    case COPYRIGHT_INDEX:
                        copyright = attrs.getAttributeValue(i);
                        break;
                }
            }
        }
        if (!tempMap.isEmpty()) {
            throw new XmlPullParserException("Not all compulsory attributes are specified in <sounds>");
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
        dest.writeString(name);
        dest.writeInt(thumbnail);
        dest.writeString(author);
        dest.writeString(copyright);
        dest.writeString(ringtoneFileName);
        dest.writeString(notificationRingtoneFileName);
        dest.writeString(ringtoneName);
        dest.writeString(notificationRingtoneName);
        dest.writeInt(isDrmProtected? 1 : 0);
    }

    public static final Parcelable.Creator<SoundsInfo> CREATOR
            = new Parcelable.Creator<SoundsInfo>() {
        public SoundsInfo createFromParcel(Parcel source) {
            return new SoundsInfo(source);
        }

        public SoundsInfo[] newArray(int size) {
            return new SoundsInfo[size];
        }
    };

    private SoundsInfo(Parcel source) {
        name = source.readString();
        thumbnail = source.readInt();
        author = source.readString();
        copyright = source.readString();
        ringtoneFileName = source.readString();
        notificationRingtoneFileName = source.readString();
        ringtoneName = source.readString();
        notificationRingtoneName = source.readString();
        isDrmProtected = (source.readInt() != 0);
    }

    private void changeDrmFlagIfNeeded(String resourcePath) {
        if (resourcePath != null && resourcePath.contains(LOCKED_NAME)) {
            isDrmProtected = true;
        }
    }

}
