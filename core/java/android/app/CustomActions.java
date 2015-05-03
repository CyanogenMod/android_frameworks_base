/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.app;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** @hide */
public final class CustomActions implements Parcelable {

    private List<CustomAction> mCustomActions = new ArrayList<CustomAction>();
    private boolean mDirty;

    /** @hide */
    public static final Creator<CustomActions> CREATOR
            = new Creator<CustomActions>() {
        public CustomActions createFromParcel(Parcel in) {
            return new CustomActions(in);
        }

        @Override
        public CustomActions[] newArray(int size) {
            return new CustomActions[size];
        }
    };

    public CustomActions(Parcel parcel) {
        readFromParcel(parcel);
    }

    public CustomActions() {
        mDirty = false;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    public void addAction(CustomAction action) {
        mCustomActions.add(action);
    }

    public List<CustomAction> getCustomActions() {
        return mCustomActions;
    }

    public void processActions(Context context) {
        Intent broadcastIntent = new Intent("com.android.settings.profiles.SEND_ACTIONS");
        ArrayList<String> actions = new ArrayList<String>(mCustomActions.size());
        for (CustomAction action : mCustomActions) {
            actions.add(action.mId + "/" + action.mState);
        }
        broadcastIntent.putExtra("customActions", actions);
        context.sendBroadcast(broadcastIntent);
    }

    /** @hide */
    public static CustomActions fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        CustomActions customActions = new CustomActions();
        while (event != XmlPullParser.END_TAG || !xpp.getName().equals("customActions")) {
            if (event == XmlPullParser.START_TAG) {
                String actionId = "";
                String actionState = "";
                String name = xpp.getName();
                if (name.equals("action")) {
                    xpp.nextTag();
                    if (xpp.getName().equals("id")) {
                        actionId = xpp.nextText();
                        xpp.nextTag();
                    }
                    if (xpp.getName().equals("state")) {
                        actionState = xpp.nextText();
                        xpp.nextTag();
                    }
                    CustomAction action = new CustomAction(actionId, actionState);
                    customActions.addAction(action);
                }
            }
            event = xpp.next();
        }
        return customActions;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<customActions>\n");
        for (CustomAction action : getCustomActions()) {
            builder.append("<action>\n<id>");
            builder.append(action.mId);
            builder.append("</id>\n<state>");
            builder.append(action.mState);
            builder.append("</state>\n");
            builder.append("</action>\n");
        }
        builder.append("</customActions>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDirty ? 1 : 0);
        dest.writeTypedList(mCustomActions);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mDirty = in.readInt() != 0;
        in.readTypedList(mCustomActions, CustomAction.CREATOR);
    }

    public static class CustomAction implements Parcelable {
        public String mId;
        public String mState;

        public CustomAction(String id, String state) {
            mId = id;
            mState = state;
        }

        public CustomAction(Parcel in) {
            mId = in.readString();
            mState = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mId);
            dest.writeString(mState);
        }

        public static final Creator<CustomAction> CREATOR
        = new Creator<CustomAction>() {
            public CustomAction createFromParcel(Parcel in) {
                return new CustomAction(in);
            }

            @Override
            public CustomAction[] newArray(int size) {
                return new CustomAction[size];
            }
        };

    }


}
