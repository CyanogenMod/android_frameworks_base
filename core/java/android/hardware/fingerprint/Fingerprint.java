/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.hardware.fingerprint;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

/**
 * @hide
 */
public class Fingerprint implements Parcelable {
    private static final String TAG = Fingerprint.class.getSimpleName();

    /**
     * Note: Do not change these member variables without also changing them in JNI.
     */
    private String mName;
    private int mFingerId;
    private int mUserId;

    /**
     * Note: Do not change this constructor without also changing it in JNI.
     */
    public Fingerprint() { };

    public Fingerprint(String name, Integer fingerId, Integer userId) {
        mName = name;
        mFingerId = fingerId;
        mUserId = userId;
    }

    private Fingerprint(Parcel source) {
        mName = source.readString();
        mFingerId = source.readInt();
        mUserId = source.readInt();
    }

    public String getName() {
        return mName;
    }

    public Integer getFingerId() {
        return mFingerId;
    }

    public Integer getUserId() {
        return mUserId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mFingerId);
        dest.writeInt(mUserId);
    }

    public static final Parcelable.Creator<Fingerprint> CREATOR
            = new Parcelable.Creator<Fingerprint>() {
        public Fingerprint createFromParcel(Parcel source) {
            return new Fingerprint(source);
        }

        public Fingerprint[] newArray(int size) {
            return new Fingerprint[size];
        }
    };

    public static class Builder {
        private String mName;
        private Integer mId;
        private Integer mUserId;

        public Builder(Fingerprint fp) {
            this.mName = fp.getName();
            this.mId = fp.getFingerId();
            this.mUserId = fp.getUserId();
        }

        public Builder name(String name) {
            this.mName = name;
            return this;
        }

        public Builder id(int id) {
            this.mId = id;
            return this;
        }

        public Fingerprint build() {
            return new Fingerprint(mName, mId, mUserId);
        }
    }

    public static class JsonSerializer {
        private static final String NAME_ID = "fingerId";
        private static final String NAME_FINGERNAME = "fingerName";
        private static final String NAME_USERID = "userId";

        public static String toJson(List<Fingerprint> fingerprints) {
            String json = null;
            try (
                Writer writer = new StringWriter();
                JsonWriter jsonWriter = new JsonWriter(writer)
            ) {
                jsonWriter.beginArray();
                for(Fingerprint fingerprint : fingerprints) {
                    writeFingerprint(jsonWriter, fingerprint);
                }
                jsonWriter.endArray();
                json = writer.toString();
            } catch (IOException e) {
                Log.e(TAG, "Could not serialize fingerprint", e);
            }
            return json;
        };

        private static void writeFingerprint(JsonWriter writer, Fingerprint fingerprint)
                throws IOException {
            writer.beginObject();
            writer.name(NAME_ID).value(fingerprint.getFingerId());
            writer.name(NAME_FINGERNAME).value(fingerprint.getName());
            writer.name(NAME_USERID).value(fingerprint.getUserId());
            writer.endObject();
        }

        public static List<Fingerprint> fromJson(String json) {
            List<Fingerprint> fingerprints = new LinkedList<Fingerprint>();
            if (json == null) return fingerprints;

            try (
                StringReader reader = new StringReader(json);
                JsonReader jsonReader = new JsonReader(reader)
            ) {
                jsonReader.beginArray();
                while(jsonReader.hasNext()) {
                    fingerprints.add(readFingerprint(jsonReader));
                }
                jsonReader.endArray();
            } catch(Exception e) {
                Log.e(TAG, "Could not parse fingerprint from: " + json, e);
            }
            return fingerprints;
        }

        private static Fingerprint readFingerprint(JsonReader reader) throws IOException {
            String fingerName = null;
            int id = 0;
            int userId = 0;
            reader.beginObject();
            while(reader.hasNext()) {
                String name = reader.nextName();
                if (NAME_ID.equals(name) && reader.peek() != JsonToken.NULL) {
                    id = reader.nextInt();
                } else if (NAME_FINGERNAME.equals(name) && reader.peek() != JsonToken.NULL) {
                    fingerName = reader.nextString();
                } else if (NAME_USERID.equals(name)) {
                    userId = reader.nextInt();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            return new Fingerprint(fingerName, id, userId);
        }
    }
}
