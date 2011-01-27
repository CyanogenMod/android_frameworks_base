
package android.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class ProfileGroup implements Parcelable {

    private String name;

    private Uri soundOverride;

    private Mode soundMode = Mode.DEFAULT;

    private Mode vibrateMode = Mode.DEFAULT;

    private Mode lightsMode = Mode.DEFAULT;

    public static final Parcelable.Creator<ProfileGroup> CREATOR = new Parcelable.Creator<ProfileGroup>() {
        public ProfileGroup createFromParcel(Parcel in) {
            return new ProfileGroup(in);
        }

        @Override
        public ProfileGroup[] newArray(int size) {
            return new ProfileGroup[size];
        }
    };

    public ProfileGroup(String name) {
        this.name = name;
    }

    private ProfileGroup(Parcel in) {
        readFromParcel(in);
    }

    public String getName() {
        return name;
    }

    public void setSoundOverride(Uri sound) {
        this.soundOverride = sound;
    }

    public Uri getSoundOverride() {
        return soundOverride;
    }

    public void setSoundMode(Mode soundMode) {
        this.soundMode = soundMode;
    }

    public Mode getSoundMode() {
        return soundMode;
    }

    public void setVibrateMode(Mode vibrateMode) {
        this.vibrateMode = vibrateMode;
    }

    public Mode getVibrateMode() {
        return vibrateMode;
    }

    public void setLightsMode(Mode lightsMode) {
        this.lightsMode = lightsMode;
    }

    public Mode getLightsMode() {
        return lightsMode;
    }

    // TODO : add support for LEDs / screen etc.

    /* package */Notification processNotification(Notification notification) {

        switch (soundMode) {
            case OVERRIDE:
                notification.sound = soundOverride;
                break;
            case SUPPRESS:
                silenceNotification(notification);
                break;
            case DEFAULT:
        }
        switch (vibrateMode) {
            case OVERRIDE:
                notification.defaults |= Notification.DEFAULT_VIBRATE;
                break;
            case SUPPRESS:
                suppressVibrate(notification);
                break;
            case DEFAULT:
        }
        switch (lightsMode) {
            case OVERRIDE:
                notification.defaults |= Notification.DEFAULT_LIGHTS;
                break;
            case SUPPRESS:
                suppressLights(notification);
                break;
            case DEFAULT:
        }
        return notification;
    }

    private void silenceNotification(Notification notification) {
        notification.defaults &= (~Notification.DEFAULT_SOUND);
        notification.sound = null;
    }

    private void suppressVibrate(Notification notification) {
        notification.defaults &= (~Notification.DEFAULT_VIBRATE);
        notification.vibrate = null;
    }

    private void suppressLights(Notification notification) {
        notification.defaults &= (~Notification.DEFAULT_LIGHTS);
        notification.flags &= (~Notification.FLAG_SHOW_LIGHTS);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeParcelable(soundOverride, flags);

        dest.writeString(soundMode.name());
        dest.writeString(vibrateMode.name());
        dest.writeString(lightsMode.name());
    }

    public void readFromParcel(Parcel in) {
        name = in.readString();
        soundOverride = in.readParcelable(null);

        soundMode = Mode.valueOf(Mode.class, in.readString());
        vibrateMode = Mode.valueOf(Mode.class, in.readString());
        lightsMode = Mode.valueOf(Mode.class, in.readString());
    }

    public enum Mode {
        SUPPRESS, DEFAULT, OVERRIDE;
    }

}
