/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package android.bluetooth;

import java.util.ArrayList;
import android.util.Log;

import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.net.Uri;

/**
 * Represents the AVRCP Metadata of remote Bluetooth Device.
 *
 * {@see BluetoothAvrcpController}
 *
 * {@hide}
 */
public final class BluetoothAvrcpInfo implements Parcelable, BaseColumns{

    private byte[] supportedPlayerAttributes;// attributes supported
    private byte[] numSupportedPlayerAttribValues; // number of values of each attribute
    private String TAG = "BluetoothAvrcpInfo";
    /*
     * This would a list of values of all AttributeIds
     */
    private byte[] supportedPlayerAtribValues; // actual values lies here.

    /* Default Constructor */
    public BluetoothAvrcpInfo() {
        supportedPlayerAttributes = null;
        numSupportedPlayerAttribValues = null;
        supportedPlayerAtribValues = null;
    }
    public BluetoothAvrcpInfo(byte[] attribIds, byte[] numValueSupported, byte[] valuesSupported) {
        int numAttributes = attribIds.length;
        int zz = 0;
        supportedPlayerAttributes = new byte[numAttributes];
        numSupportedPlayerAttribValues = new byte[numAttributes];
        supportedPlayerAtribValues = new byte[valuesSupported.length];
        for (zz = 0; zz < numAttributes; zz++) {
            supportedPlayerAttributes[zz] = attribIds[zz];
            numSupportedPlayerAttribValues[zz] = numValueSupported[zz];
        }
        for (zz = 0; zz < supportedPlayerAtribValues.length; zz++)
            supportedPlayerAtribValues[zz] = valuesSupported[zz];
    }
    /*
     * Reading Structure back from Paracel
     */
    public BluetoothAvrcpInfo(Parcel source){
        ArrayList<Byte> attribs =  new ArrayList<Byte>();
        ArrayList<Byte> numAttribVal =  new ArrayList<Byte>();
        ArrayList<Byte> attribVals =  new ArrayList<Byte>();
        Byte numAttributes = source.readByte();
        /*
         * Read from Source
         */
        for(int xx = 0; xx < numAttributes ; xx++) {
            attribs.add(source.readByte());
            numAttribVal.add(source.readByte());
            for (int zz = 0; zz < numAttribVal.get(xx); zz++) {
                attribVals.add(source.readByte());
            }
        }

        /*
         * Write Back to Private Data Structures
         */
        supportedPlayerAttributes =  new byte[attribs.size()];
        for (int zz = 0; zz< attribs.size(); zz++) {
            supportedPlayerAttributes[zz] = attribs.get(zz);
        }

        numSupportedPlayerAttribValues =  new byte[numAttribVal.size()];
        for (int zz = 0; zz< numAttribVal.size(); zz++) {
            numSupportedPlayerAttribValues[zz] = numAttribVal.get(zz);
        }

        supportedPlayerAtribValues =  new byte[attribVals.size()];
        for (int zz = 0; zz< attribVals.size(); zz++) {
            supportedPlayerAtribValues[zz] = attribVals.get(zz);
        }
    }

    public int describeContents() {
        return 0;
    }

    /* While flatenning the structure we would use the follwing way
     * NumAttributes,ID, numValues, Values
     */
    public void writeToParcel(Parcel out, int flags) {
        byte numSuppAttributes = (byte)supportedPlayerAttributes.length;
        out.writeByte(numSuppAttributes);
        for (int xx = 0; xx < numSuppAttributes; xx++) {
            out.writeByte(supportedPlayerAttributes[xx]);
            out.writeByte(numSupportedPlayerAttribValues[xx]);
            for (int zz = 0; zz < numSupportedPlayerAttribValues[xx]; zz++) {
                out.writeByte(supportedPlayerAtribValues[zz]);
            }
        }
    }

    public byte[] getSupportedPlayerAttributes() {
        return supportedPlayerAttributes;
    }

    public byte getNumSupportedPlayerAttributeVal(byte playerAttributeId) {
        for (int zz = 0; zz < supportedPlayerAttributes.length; zz++) {
            if (playerAttributeId == supportedPlayerAttributes[zz]) {
                return numSupportedPlayerAttribValues[zz];
            }
        }
        return 0;
    }

    public byte[] getSupportedPlayerAttributeVlaues (byte playerAttributeId) {
        int index = 0;
        int zz = 0;
        boolean attributeFound = false;
        for (zz = 0; zz < supportedPlayerAttributes.length; zz++) {
            if (playerAttributeId == supportedPlayerAttributes[zz]) {
                attributeFound = true;
                break;
            }
            else
               index = index + numSupportedPlayerAttribValues[zz];
        }
        if (attributeFound) {
            byte[] supportedValues =  new byte[numSupportedPlayerAttribValues[zz]];
            for (int xx = 0; xx < numSupportedPlayerAttribValues[zz]; xx++)
                supportedValues[xx] = supportedPlayerAtribValues[xx + index];
            return supportedValues;
        }
        else
            return new byte[0];
    }
    public void putPlayerSettingAttributes(byte[] attribIds, byte[] numValueSupported, byte[] valuesSupported) {
        int numAttributes = attribIds.length;
        int zz = 0;
        supportedPlayerAttributes = new byte[numAttributes];
        numSupportedPlayerAttribValues = new byte[numAttributes];
        supportedPlayerAtribValues = new byte[valuesSupported.length];
        for (zz = 0; zz < numAttributes; zz++) {
            supportedPlayerAttributes[zz] = attribIds[zz];
            numSupportedPlayerAttribValues[zz] = numValueSupported[zz];
        }
        for (zz = 0; zz < supportedPlayerAtribValues.length; zz++)
            supportedPlayerAtribValues[zz] = valuesSupported[zz];
   }
    public static final Parcelable.Creator<BluetoothAvrcpInfo> CREATOR =
        new Parcelable.Creator<BluetoothAvrcpInfo>() {
            public BluetoothAvrcpInfo createFromParcel(Parcel in) {
                return new BluetoothAvrcpInfo(in);
            }
            public BluetoothAvrcpInfo[] newArray(int size) {
                return new BluetoothAvrcpInfo[size];
            }
    };

    public static final String PERMISSION_ACCESS = "android.permission.ACCESS_BLUETOOTH_AVRCP_CT_DATA";
    public static final Uri CONTENT_URI = Uri.parse("content://com.android.bluetooth.avrcp/btavrcp_ct");

    /*
     * BaseColumns already has _ID and COUNT values
     * Below mentioned strings are used to implement different columns
     * of AVRCP MetaData table.
     * TRACK_NUM       : Ineteger value containing the order number of
     *                   the audio-file on its original recording.
     *                   Numeric ASCII string converted to Integer
     * TITLE           : Text field representing the title, song name
     * ARTIST_NAME     : Text field representing artist(s), performer(s)
     * ALBUM_NAME      : Text field representing the title of the recording
     *                   (source) from which the audio in the file is taken.
     * TOTAL_TRACKS    : Integet value containing the total number of tracks
     *                   or elements on the original recording.
     * GENRE           : Text field representing the category of the composition
     *                   characterized by a particular style.
     * PLAYING_TIME    : Integer containing the length of the audio file in
     *                   milliseconds for eg 02:30 = 150000
     * PLAY_STATUS     : Text feild showing current state of track. Possible
     *                   values would be Playing, Stopped, Paused, Forward_Seek
     *                   REV_SEEK
     * REPEAT_STATUS   : String describing Repeat mode status on remote Media Player
     *                   Posible values "NOT SUPPORTED", "OFF" "Single Track Repeat"
     *                   "All Track Repeat" "Group Repeat"
     * SHUFFLE_STATUS  : String describing Shuffle mode status on remote Media Player
     *                   Posible values "NOT SUPPORTED", "OFF" "All Track Shuffle"
     *                   "Group Shuffle"
     * SCAN_STAUS      : String describing SCAN mode status on remote Media Player
     *                   Possible values "NOT SUPPORTED", "OFF","ALL Tracks Scan"
     *                   "Group Scan"
     *
     * EQUALIZER_STATUS: String describing EQUALIZER mode status on remote Media Player
     *                   Possible values "NOT SUPPORTED", "OFF","ON"
     */
    public static final String TRACK_NUM = "track_num";
    public static final String TITLE = "title";
    public static final String ARTIST_NAME = "artist_name";
    public static final String ALBUM_NAME = "album_name";
    public static final String TOTAL_TRACKS = "total_tracks";
    public static final String GENRE = "genre";
    public static final String PLAYING_TIME = "playing_time";
    public static final String TOTAL_TRACK_TIME = "total_track_time";
    public static final String PLAY_STATUS = "play_status";
    public static final String REPEAT_STATUS = "repeat_status";
    public static final String SHUFFLE_STATUS = "shuffle_status";
    public static final String SCAN_STATUS = "scan_status";
    public static final String EQUALIZER_STATUS = "equalizer_status";

    /*
     * Default values for each of the items
    */
    public static final int TRACK_NUM_INVALID = 0xFF;
    public static final String TITLE_INVALID = "NOT_SUPPORTED";
    public static final String ARTIST_NAME_INVALID = "NOT_SUPPORTED";
    public static final String ALBUM_NAME_INVALID = "NOT_SUPPORTED";
    public static final int TOTAL_TRACKS_INVALID = 0xFF;
    public static final String GENRE_INVALID = "NOT_SUPPORTED";
    public static final int PLAYING_TIME_INVALID = 0xFF;
    public static final int TOTAL_TRACK_TIME_INVALID = 0xFF;
    public static final String PLAY_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String REPEAT_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String SHUFFLE_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String SCAN_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String EQUALIZER_STATUS_INVALID = "NOT_SUPPORTED";

    /*
     *Element Id Values for GetMetaData
    */
    public static final int MEDIA_ATTRIBUTE_ALL = 0x00;
    public static final int MEDIA_ATTRIBUTE_TITLE = 0x01;
    public static final int MEDIA_ATTRIBUTE_ARTIST_NAME = 0x02;
    public static final int MEDIA_ATTRIBUTE_ALBUM_NAME = 0x03;
    public static final int MEDIA_ATTRIBUTE_TRACK_NUMBER = 0x04;
    public static final int MEDIA_ATTRIBUTE_TOTAL_TRACK_NUMBER = 0x05;
    public static final int MEDIA_ATTRIBUTE_GENRE = 0x06;
    public static final int MEDIA_ATTRIBUTE_PLAYING_TIME = 0x07;

    /*
     *PlayStatusId Values for GetPlayStatus
    */
    public static final int MEDIA_PLAYSTATUS_ALL = 0x08;
    public static final int MEDIA_PLAYSTATUS_SONG_TOTAL_LEN = 0x09;
    public static final int MEDIA_PLAYSTATUS_SONG_CUR_POS = 0x0a;
    public static final int MEDIA_PLAYSTATUS_SONG_PLAY_STATUS = 0x0b;

    /*
     * Values for SetPlayerApplicationSettings
    */
    public static final byte ATTRIB_EQUALIZER_STATUS = 0x01;
    public static final byte ATTRIB_REPEAT_STATUS = 0x02;
    public static final byte ATTRIB_SHUFFLE_STATUS = 0x03;
    public static final byte ATTRIB_SCAN_STATUS = 0x04;

    public static final byte EQUALIZER_STATUS_OFF = 0x01;
    public static final byte EQUALIZER_STATUS_ON = 0x02;

    public static final byte REPEAT_STATUS_OFF = 0x01;
    public static final byte REPEAT_STATUS_SINGLE_TRACK_REPEAT = 0x02;
    public static final byte REPEAT_STATUS_ALL_TRACK_REPEAT = 0x03;
    public static final byte REPEAT_STATUS_GROUP_REPEAT = 0x04;

    public static final byte SHUFFLE_STATUS_OFF = 0x01;
    public static final byte SHUFFLE_STATUS_ALL_TRACK_SHUFFLE = 0x02;
    public static final byte SHUFFLE_STATUS_GROUP_SHUFFLE = 0x03;

    public static final byte SCAN_STATUS_OFF = 0x01;
    public static final byte SCAN_STATUS_ALL_TRACK_SCAN = 0x02;
    public static final byte SCAN_STATUS_GROUP_SCAN = 0x03;

    public static final int BTRC_FEAT_METADATA = 0x01;
    public static final int BTRC_FEAT_ABSOLUTE_VOLUME = 0x02;
    public static final int BTRC_FEAT_BROWSE = 0x04;

}
