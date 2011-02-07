
package android.app;

import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

public class Profile implements Parcelable {

    private String name;

    private Map<String, ProfileGroup> profileGroups = new HashMap<String, ProfileGroup>();

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
    
    public void addProfleGroup(ProfileGroup group){
        profileGroups.put(group.getName(), group);
    }
    
    public void removeProfileGroup(String name){
        profileGroups.remove(name);
    }
    
    public ProfileGroup[] getProfileGroups(){
        return profileGroups.values().toArray(new ProfileGroup[profileGroups.size()]);
    }
    
    public ProfileGroup getProfileGroup(String name){
        return profileGroups.get(name);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeParcelableArray(
                profileGroups.values().toArray(
                        new Parcelable[profileGroups.size()]), flags);
    }

    public void readFromParcel(Parcel in) {
        name = in.readString();
        for (Parcelable group : in.readParcelableArray(null)) {
            ProfileGroup grp = (ProfileGroup)group;
            profileGroups.put(grp.getName(), grp);
        }
    }

    public String getName() {
        return name;
    }

    public Notification processNotification(String groupName, Notification notification) {
        ProfileGroup profileGroupSettings = profileGroups.get(groupName);
        notification = profileGroupSettings.processNotification(notification);
        return notification;
    }

}
