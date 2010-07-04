package android.content.pm;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParser;

import android.os.Parcelable;
import android.os.Parcel;
import android.util.AttributeSet;
import android.content.res.Resources;

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
 * @hide
 */
public class SoundsInfo extends BaseThemeInfo {

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


    public SoundsInfo(XmlPullParser parser, Resources res, AttributeSet attrs) throws XmlPullParserException {
        super();

        type = InfoObjectType.TYPE_SOUNDPACK;
        Map<String, Integer> tempMap = new HashMap<String, Integer>(attributesLookupTable);
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (!ApplicationInfo.isPlutoNamespace(parser.getAttributeNamespace(i))) {
                continue;
            }
            String key = attrs.getAttributeName(i);
            if (tempMap.containsKey(key)) {
                int index = tempMap.get(key);
                tempMap.remove(key);

                switch (index) {
                    case NAME_INDEX:
                        name = getResolvedString(res, attrs, i);
                        break;

                    case THUMBNAIL_INDEX:
                        thumbnail = attrs.getAttributeValue(i);
                        break;

                    case AUTHOR_INDEX:
                        author = getResolvedString(res, attrs, i);
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
                        copyright = getResolvedString(res, attrs, i);
                        break;
                }
            }
        }
        if (!tempMap.isEmpty()) {
            throw new XmlPullParserException("Not all compulsory attributes are specified in <sounds>");
        }
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
        super(source);
    }

}
