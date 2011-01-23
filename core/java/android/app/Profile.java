
package android.app;

import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

public class Profile implements Parcelable {

    private String name;

    private Map<String, NotificationGroup> notificationGroups = new HashMap<String, NotificationGroup>();

    public static final Parcelable.Creator<Profile> CREATOR = new Parcelable.Creator<Profile>() {
        public Profile createFromParcel(Parcel in) {
            return new Profile(in);
        }

        @Override
        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };

    public Profile(String name) {
        this.name = name;
    }

    private Profile(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeParcelableArray(
                notificationGroups.values().toArray(
                        new NotificationGroup[notificationGroups.size()]), flags);
    }

    public void readFromParcel(Parcel in) {
        name = in.readString();
        for (NotificationGroup group : (NotificationGroup[]) in.readParcelableArray(null)) {
            notificationGroups.put(group.getName(), group);
        }
    }

    public String getName() {
        return name;
    }

    public Notification processNotification(String pkg, Notification notification) {
        for (NotificationGroup group : notificationGroups.values()) {
            if (group.hasPackage(pkg)) {
                notification = group.processNotification(notification);
            }
        }
        return notification;
    }

}
