
package android.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class NotificationGroup implements Parcelable {

    private String name;

    private Set<String> packages = new HashSet<String>();

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
        this.name = name;
    }

    private NotificationGroup(Parcel in) {
        readFromParcel(in);
    }

    public String getName() {
        return name;
    }

    public void addPackage(String pkg) {
        packages.add(pkg);
    }

    public String[] getPackages() {
        return packages.toArray(new String[packages.size()]);
    }

    public void removePackage(String pkg) {
        packages.remove(pkg);
    }

    public boolean hasPackage(String pkg) {
        boolean result = packages.contains(pkg);
        Log.i("PROFILE", "Group: " + name + " containing : " + pkg + " : " + result);
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeStringArray(getPackages());
    }

    public void readFromParcel(Parcel in) {
        name = in.readString();
        packages.addAll(Arrays.asList(in.readStringArray()));
    }

}
