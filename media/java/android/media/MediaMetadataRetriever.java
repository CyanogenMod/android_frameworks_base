/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.media;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.IBinder;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Map;

/**
 * MediaMetadataRetriever class provides a unified interface for retrieving
 * frame and meta data from an input media file.
 */
public class MediaMetadataRetriever
{
    static {
        System.loadLibrary("media_jni");
        native_init();
    }

    // The field below is accessed by native methods
    @SuppressWarnings("unused")
    private long mNativeContext;
 
    private static final int EMBEDDED_PICTURE_TYPE_ANY = 0xFFFF;

    public MediaMetadataRetriever() {
        native_setup();
    }

    /**
     * Sets the data source (file pathname) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     * 
     * @param path The path of the input media file.
     * @throws IllegalArgumentException If the path is invalid.
     */
    public void setDataSource(String path) throws IllegalArgumentException {
        if (path == null) {
            throw new IllegalArgumentException();
        }

        try (FileInputStream is = new FileInputStream(path)) {
            FileDescriptor fd = is.getFD();
            setDataSource(fd, 0, 0x7ffffffffffffffL);
        } catch (FileNotFoundException fileEx) {
            throw new IllegalArgumentException();
        } catch (IOException ioEx) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Sets the data source (URI) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     *
     * @param uri The URI of the input media.
     * @param headers the headers to be sent together with the request for the data
     * @throws IllegalArgumentException If the URI is invalid.
     */
    public void setDataSource(String uri,  Map<String, String> headers)
            throws IllegalArgumentException {
        int i = 0;
        String[] keys = new String[headers.size()];
        String[] values = new String[headers.size()];
        for (Map.Entry<String, String> entry: headers.entrySet()) {
            keys[i] = entry.getKey();
            values[i] = entry.getValue();
            ++i;
        }

        _setDataSource(
                MediaHTTPService.createHttpServiceBinderIfNecessary(uri),
                uri,
                keys,
                values);
    }

    private native void _setDataSource(
        IBinder httpServiceBinder, String uri, String[] keys, String[] values)
        throws IllegalArgumentException;

    /**
     * Sets the data source (FileDescriptor) to use.  It is the caller's
     * responsibility to close the file descriptor. It is safe to do so as soon
     * as this call returns. Call this method before the rest of the methods in
     * this class. This method may be time-consuming.
     * 
     * @param fd the FileDescriptor for the file you want to play
     * @param offset the offset into the file where the data to be played starts,
     * in bytes. It must be non-negative
     * @param length the length in bytes of the data to be played. It must be
     * non-negative.
     * @throws IllegalArgumentException if the arguments are invalid
     */
    public native void setDataSource(FileDescriptor fd, long offset, long length)
            throws IllegalArgumentException;
    
    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's
     * responsibility to close the file descriptor. It is safe to do so as soon
     * as this call returns. Call this method before the rest of the methods in
     * this class. This method may be time-consuming.
     * 
     * @param fd the FileDescriptor for the file you want to play
     * @throws IllegalArgumentException if the FileDescriptor is invalid
     */
    public void setDataSource(FileDescriptor fd)
            throws IllegalArgumentException {
        // intentionally less than LONG_MAX
        setDataSource(fd, 0, 0x7ffffffffffffffL);
    }
    
    /**
     * Sets the data source as a content Uri. Call this method before 
     * the rest of the methods in this class. This method may be time-consuming.
     * 
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalArgumentException if the Uri is invalid
     * @throws SecurityException if the Uri cannot be used due to lack of
     * permission.
     */
    public void setDataSource(Context context, Uri uri)
        throws IllegalArgumentException, SecurityException {
        if (uri == null) {
            throw new IllegalArgumentException();
        }
        
        String scheme = uri.getScheme();
        if(scheme == null || scheme.equals("file")) {
            setDataSource(uri.getPath());
            return;
        }

        AssetFileDescriptor fd = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            try {
                fd = resolver.openAssetFileDescriptor(uri, "r");
            } catch(FileNotFoundException e) {
                throw new IllegalArgumentException();
            }
            if (fd == null) {
                throw new IllegalArgumentException();
            }
            FileDescriptor descriptor = fd.getFileDescriptor();
            if (!descriptor.valid()) {
                throw new IllegalArgumentException();
            }
            // Note: using getDeclaredLength so that our behavior is the same
            // as previous versions when the content provider is returning
            // a full file.
            if (fd.getDeclaredLength() < 0) {
                setDataSource(descriptor);
            } else {
                setDataSource(descriptor, fd.getStartOffset(), fd.getDeclaredLength());
            }
            return;
        } catch (SecurityException ex) {
        } finally {
            try {
                if (fd != null) {
                    fd.close();
                }
            } catch(IOException ioEx) {
            }
        }
        setDataSource(uri.toString());
    }

    /**
     * Sets the data source (MediaDataSource) to use.
     *
     * @param dataSource the MediaDataSource for the media you want to play
     */
    public void setDataSource(MediaDataSource dataSource)
            throws IllegalArgumentException {
        _setDataSource(dataSource);
    }

    private native void _setDataSource(MediaDataSource dataSource)
          throws IllegalArgumentException;

    /**
     * Call this method after setDataSource(). This method retrieves the
     * meta data value associated with the keyCode.
     * 
     * The keyCode currently supported is listed below as METADATA_XXX
     * constants. With any other value, it returns a null pointer.
     * 
     * @param keyCode One of the constants listed below at the end of the class.
     * @return The meta data value associate with the given keyCode on success; 
     * null on failure.
     */
    public native String extractMetadata(int keyCode);

    /**
     * Call this method after setDataSource(). This method finds a
     * representative frame close to the given time position by considering
     * the given option if possible, and returns it as a bitmap. This is
     * useful for generating a thumbnail for an input data source or just
     * obtain and display a frame at the given time position.
     *
     * @param timeUs The time position where the frame will be retrieved.
     * When retrieving the frame at the given time position, there is no
     * guarantee that the data source has a frame located at the position.
     * When this happens, a frame nearby will be returned. If timeUs is
     * negative, time position and option will ignored, and any frame
     * that the implementation considers as representative may be returned.
     *
     * @param option a hint on how the frame is found. Use
     * {@link #OPTION_PREVIOUS_SYNC} if one wants to retrieve a sync frame
     * that has a timestamp earlier than or the same as timeUs. Use
     * {@link #OPTION_NEXT_SYNC} if one wants to retrieve a sync frame
     * that has a timestamp later than or the same as timeUs. Use
     * {@link #OPTION_CLOSEST_SYNC} if one wants to retrieve a sync frame
     * that has a timestamp closest to or the same as timeUs. Use
     * {@link #OPTION_CLOSEST} if one wants to retrieve a frame that may
     * or may not be a sync frame but is closest to or the same as timeUs.
     * {@link #OPTION_CLOSEST} often has larger performance overhead compared
     * to the other options if there is no sync frame located at timeUs.
     *
     * @return A Bitmap containing a representative video frame, which 
     *         can be null, if such a frame cannot be retrieved.
     */
    public Bitmap getFrameAtTime(long timeUs, int option) {
        if (option < OPTION_PREVIOUS_SYNC ||
            option > OPTION_CLOSEST) {
            throw new IllegalArgumentException("Unsupported option: " + option);
        }

        return _getFrameAtTime(timeUs, option);
    }

    /**
     * Call this method after setDataSource(). This method finds a
     * representative frame close to the given time position if possible,
     * and returns it as a bitmap. This is useful for generating a thumbnail
     * for an input data source. Call this method if one does not care
     * how the frame is found as long as it is close to the given time;
     * otherwise, please call {@link #getFrameAtTime(long, int)}.
     *
     * @param timeUs The time position where the frame will be retrieved.
     * When retrieving the frame at the given time position, there is no
     * guarentee that the data source has a frame located at the position.
     * When this happens, a frame nearby will be returned. If timeUs is
     * negative, time position and option will ignored, and any frame
     * that the implementation considers as representative may be returned.
     *
     * @return A Bitmap containing a representative video frame, which
     *         can be null, if such a frame cannot be retrieved.
     *
     * @see #getFrameAtTime(long, int)
     */
    public Bitmap getFrameAtTime(long timeUs) {
        return getFrameAtTime(timeUs, OPTION_CLOSEST_SYNC);
    }

    /**
     * Call this method after setDataSource(). This method finds a
     * representative frame at any time position if possible,
     * and returns it as a bitmap. This is useful for generating a thumbnail
     * for an input data source. Call this method if one does not
     * care about where the frame is located; otherwise, please call
     * {@link #getFrameAtTime(long)} or {@link #getFrameAtTime(long, int)}
     *
     * @return A Bitmap containing a representative video frame, which
     *         can be null, if such a frame cannot be retrieved.
     *
     * @see #getFrameAtTime(long)
     * @see #getFrameAtTime(long, int)
     */
    public Bitmap getFrameAtTime() {
        return getFrameAtTime(-1, OPTION_CLOSEST_SYNC);
    }

    private native Bitmap _getFrameAtTime(long timeUs, int option);

    
    /**
     * Call this method after setDataSource(). This method finds the optional
     * graphic or album/cover art associated associated with the data source. If
     * there are more than one pictures, (any) one of them is returned.
     * 
     * @return null if no such graphic is found.
     */
    public byte[] getEmbeddedPicture() {
        return getEmbeddedPicture(EMBEDDED_PICTURE_TYPE_ANY);
    }

    private native byte[] getEmbeddedPicture(int pictureType);

    /**
     * Call it when one is done with the object. This method releases the memory
     * allocated internally.
     */
    public native void release();
    private native void native_setup();
    private static native void native_init();

    private native final void native_finalize();

    @Override
    protected void finalize() throws Throwable {
        try {
            native_finalize();
        } finally {
            super.finalize();
        }
    }

    /**
     * Option used in method {@link #getFrameAtTime(long, int)} to get a
     * frame at a specified location.
     *
     * @see #getFrameAtTime(long, int)
     */
    /* Do not change these option values without updating their counterparts
     * in include/media/stagefright/MediaSource.h!
     */
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a sync (or key) frame associated with a data source that is located
     * right before or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_PREVIOUS_SYNC    = 0x00;
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a sync (or key) frame associated with a data source that is located
     * right after or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_NEXT_SYNC        = 0x01;
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a sync (or key) frame associated with a data source that is located
     * closest to (in time) or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_CLOSEST_SYNC     = 0x02;
    /**
     * This option is used with {@link #getFrameAtTime(long, int)} to retrieve
     * a frame (not necessarily a key frame) associated with a data source that
     * is located closest to or at the given time.
     *
     * @see #getFrameAtTime(long, int)
     */
    public static final int OPTION_CLOSEST          = 0x03;

    /*
     * Do not change these metadata key values without updating their
     * counterparts in include/media/mediametadataretriever.h!
     */
    /**
     * The metadata key to retrieve the numeric string describing the
     * order of the audio data source on its original recording.
     */
    public static final int METADATA_KEY_CD_TRACK_NUMBER = 0;
    /**
     * The metadata key to retrieve the information about the album title
     * of the data source.
     */
    public static final int METADATA_KEY_ALBUM           = 1;
    /**
     * The metadata key to retrieve the information about the artist of
     * the data source.
     */
    public static final int METADATA_KEY_ARTIST          = 2;
    /**
     * The metadata key to retrieve the information about the author of
     * the data source.
     */
    public static final int METADATA_KEY_AUTHOR          = 3;
    /**
     * The metadata key to retrieve the information about the composer of
     * the data source.
     */
    public static final int METADATA_KEY_COMPOSER        = 4;
    /**
     * The metadata key to retrieve the date when the data source was created
     * or modified.
     */
    public static final int METADATA_KEY_DATE            = 5;
    /**
     * The metadata key to retrieve the content type or genre of the data
     * source.
     */
    public static final int METADATA_KEY_GENRE           = 6;
    /**
     * The metadata key to retrieve the data source title.
     */
    public static final int METADATA_KEY_TITLE           = 7;
    /**
     * The metadata key to retrieve the year when the data source was created
     * or modified.
     */
    public static final int METADATA_KEY_YEAR            = 8;
    /**
     * The metadata key to retrieve the playback duration of the data source.
     */
    public static final int METADATA_KEY_DURATION        = 9;
    /**
     * The metadata key to retrieve the number of tracks, such as audio, video,
     * text, in the data source, such as a mp4 or 3gpp file.
     */
    public static final int METADATA_KEY_NUM_TRACKS      = 10;
    /**
     * The metadata key to retrieve the information of the writer (such as
     * lyricist) of the data source.
     */
    public static final int METADATA_KEY_WRITER          = 11;
    /**
     * The metadata key to retrieve the mime type of the data source. Some
     * example mime types include: "video/mp4", "audio/mp4", "audio/amr-wb",
     * etc.
     */
    public static final int METADATA_KEY_MIMETYPE        = 12;
    /**
     * The metadata key to retrieve the information about the performers or
     * artist associated with the data source.
     */
    public static final int METADATA_KEY_ALBUMARTIST     = 13;
    /**
     * The metadata key to retrieve the numberic string that describes which
     * part of a set the audio data source comes from.
     */
    public static final int METADATA_KEY_DISC_NUMBER     = 14;
    /**
     * The metadata key to retrieve the music album compilation status.
     */
    public static final int METADATA_KEY_COMPILATION     = 15;
    /**
     * If this key exists the media contains audio content.
     */
    public static final int METADATA_KEY_HAS_AUDIO       = 16;
    /**
     * If this key exists the media contains video content.
     */
    public static final int METADATA_KEY_HAS_VIDEO       = 17;
    /**
     * If the media contains video, this key retrieves its width.
     */
    public static final int METADATA_KEY_VIDEO_WIDTH     = 18;
    /**
     * If the media contains video, this key retrieves its height.
     */
    public static final int METADATA_KEY_VIDEO_HEIGHT    = 19;
    /**
     * This key retrieves the average bitrate (in bits/sec), if available.
     */
    public static final int METADATA_KEY_BITRATE         = 20;
    /**
     * This key retrieves the language code of text tracks, if available.
     * If multiple text tracks present, the return value will look like:
     * "eng:chi"
     * @hide
     */
    public static final int METADATA_KEY_TIMED_TEXT_LANGUAGES      = 21;
    /**
     * If this key exists the media is drm-protected.
     * @hide
     */
    public static final int METADATA_KEY_IS_DRM          = 22;
    /**
     * This key retrieves the location information, if available.
     * The location should be specified according to ISO-6709 standard, under
     * a mp4/3gp box "@xyz". Location with longitude of -90 degrees and latitude
     * of 180 degrees will be retrieved as "-90.0000+180.0000", for instance.
     */
    public static final int METADATA_KEY_LOCATION        = 23;
    /**
     * This key retrieves the video rotation angle in degrees, if available.
     * The video rotation angle may be 0, 90, 180, or 270 degrees.
     */
    public static final int METADATA_KEY_VIDEO_ROTATION = 24;
    /**
     * This key retrieves the original capture framerate, if it's
     * available. The capture framerate will be a floating point
     * number.
     */
    public static final int METADATA_KEY_CAPTURE_FRAMERATE = 25;
    // Add more here...
}
