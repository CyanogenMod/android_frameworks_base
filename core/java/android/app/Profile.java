package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public class Profile implements Parcelable
{

    public static final Parcelable.Creator<Profile> CREATOR = new Parcelable.Creator<Profile>() {
                                                                public Profile createFromParcel(Parcel in)
                                                                {
                                                                    return new Profile(in);
                                                                }

                                                                @Override
                                                                public Profile[] newArray(int size)
                                                                {
                                                                    return new Profile[size];
                                                                }
                                                            };

    public Profile(String name)
    {
        this.name = name;
    }

    private Profile(Parcel in)
    {
        readFromParcel(in);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
    }

    public void readFromParcel(Parcel in)
    {
    }

    private String name;

    public String getName()
    {
        return name;
    }

}
