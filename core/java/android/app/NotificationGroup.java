
package android.app;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NotificationGroup implements Parcelable {

    private String mName;

    private Set<String> mPackages = new HashSet<String>();

    public static final Parcelable.Creator<NotificationGroup> CREATOR = new Parcelable.Creator<NotificationGroup>() {
        public NotificationGroup createFromParcel(Parcel in) {
            return new NotificationGroup(in);
        }

        @Override
        public NotificationGroup[] newArray(int size) {
            return new NotificationGroup[size];
        }
    };

    public NotificationGroup(String name) {
        this.mName = name;
    }

    private NotificationGroup(Parcel in) {
        readFromParcel(in);
    }

    public String getName() {
        return mName;
    }

    public void addPackage(String pkg) {
        mPackages.add(pkg);
    }

    public String[] getPackages() {
        return mPackages.toArray(new String[mPackages.size()]);
    }

    public void removePackage(String pkg) {
        mPackages.remove(pkg);
    }

    public boolean hasPackage(String pkg) {
        boolean result = mPackages.contains(pkg);
        Log.i("PROFILE", "Group: " + mName + " containing : " + pkg + " : " + result);
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeStringArray(getPackages());
    }

    public void readFromParcel(Parcel in) {
        mName = in.readString();
        mPackages.addAll(Arrays.asList(in.readStringArray()));
    }

    public String getXmlString() {
        StringBuilder builder = new StringBuilder();
        getXmlString(builder);
        return builder.toString();
    }

    public void getXmlString(StringBuilder builder) {
        builder.append("<notificationGroup name=\"" + getName() + "\">\n");
        for (String pkg : mPackages) {
            builder.append("<package>" + pkg + "</package>\n");
        }
        builder.append("</notificationGroup>\n");
    }

    public static NotificationGroup fromXml(XmlPullParser xpp) throws XmlPullParserException,
            IOException {
        NotificationGroup notificationGroup = new NotificationGroup(xpp.getAttributeValue(null,
                "name"));
        int event = xpp.next();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("notificationGroup")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("package")) {
                    String pkg = xpp.nextText();
                    notificationGroup.addPackage(pkg);
                }
            }
            event = xpp.next();
        }
        return notificationGroup;
    }
}
