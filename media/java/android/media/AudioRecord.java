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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.Log;

/**
 * The AudioRecord class manages the audio resources for Java applications
 * to record audio from the audio input hardware of the platform. This is
 * achieved by "pulling" (reading) the data from the AudioRecord object. The
 * application is responsible for polling the AudioRecord object in time using one of
 * the following three methods:  {@link #read(byte[],int, int)}, {@link #read(short[], int, int)}
 * or {@link #read(ByteBuffer, int)}. The choice of which method to use will be based
 * on the audio data storage format that is the most convenient for the user of AudioRecord.
 * <p>Upon creation, an AudioRecord object initializes its associated audio buffer that it will
 * fill with the new audio data. The size of this buffer, specified during the construction,
 * determines how long an AudioRecord can record before "over-running" data that has not
 * been read yet. Data should be read from the audio hardware in chunks of sizes inferior to
 * the total recording buffer size.
 */
public class AudioRecord
{
    //---------------------------------------------------------
    // Constants
    //--------------------

    /** Minimum value for sample rate */
    private static final int SAMPLE_RATE_HZ_MIN = 4000;
    /** Maximum value for sample rate */
    private static final int SAMPLE_RATE_HZ_MAX = 192000;

    /**
     *  indicates AudioRecord state is not successfully initialized.
     */
    public static final int STATE_UNINITIALIZED = 0;
    /**
     *  indicates AudioRecord state is ready to be used
     */
    public static final int STATE_INITIALIZED   = 1;

    /**
     * indicates AudioRecord recording state is not recording
     */
    public static final int RECORDSTATE_STOPPED = 1;  // matches SL_RECORDSTATE_STOPPED
    /**
     * indicates AudioRecord recording state is recording
     */
    public static final int RECORDSTATE_RECORDING = 3;// matches SL_RECORDSTATE_RECORDING

    /**
     * Denotes a successful operation.
     */
    public  static final int SUCCESS                               = AudioSystem.SUCCESS;
    /**
     * Denotes a generic operation failure.
     */
    public  static final int ERROR                                 = AudioSystem.ERROR;
    /**
     * Denotes a failure due to the use of an invalid value.
     */
    public  static final int ERROR_BAD_VALUE                       = AudioSystem.BAD_VALUE;
    /**
     * Denotes a failure due to the improper use of a method.
     */
    public  static final int ERROR_INVALID_OPERATION               = AudioSystem.INVALID_OPERATION;

    // Error codes:
    // to keep in sync with frameworks/base/core/jni/android_media_AudioRecord.cpp
    private static final int AUDIORECORD_ERROR_SETUP_ZEROFRAMECOUNT      = -16;
    private static final int AUDIORECORD_ERROR_SETUP_INVALIDCHANNELMASK  = -17;
    private static final int AUDIORECORD_ERROR_SETUP_INVALIDFORMAT       = -18;
    private static final int AUDIORECORD_ERROR_SETUP_INVALIDSOURCE       = -19;
    private static final int AUDIORECORD_ERROR_SETUP_NATIVEINITFAILED    = -20;

    // Events:
    // to keep in sync with frameworks/av/include/media/AudioRecord.h
    /**
     * Event id denotes when record head has reached a previously set marker.
     */
    private static final int NATIVE_EVENT_MARKER  = 2;
    /**
     * Event id denotes when previously set update period has elapsed during recording.
     */
    private static final int NATIVE_EVENT_NEW_POS = 3;

    private final static String TAG = "android.media.AudioRecord";

    /** @hide */
    public final static String SUBMIX_FIXED_VOLUME = "fixedVolume";

    /** @hide */
    @IntDef({
        READ_BLOCKING,
        READ_NON_BLOCKING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReadMode {}

    /**
     * The read mode indicating the read operation will block until all data
     * requested has been read.
     */
    public final static int READ_BLOCKING = 0;

    /**
     * The read mode indicating the read operation will return immediately after
     * reading as much audio data as possible without blocking.
     */
    public final static int READ_NON_BLOCKING = 1;

    //---------------------------------------------------------
    // Used exclusively by native code
    //--------------------
    /**
     * Accessed by native methods: provides access to C++ AudioRecord object
     */
    @SuppressWarnings("unused")
    private long mNativeRecorderInJavaObj;

    /**
     * Accessed by native methods: provides access to the callback data.
     */
    @SuppressWarnings("unused")
    private long mNativeCallbackCookie;

    /**
     * Accessed by native methods: provides access to the JNIDeviceCallback instance.
     */
    @SuppressWarnings("unused")
    private long mNativeDeviceCallback;


    //---------------------------------------------------------
    // Member variables
    //--------------------
    /**
     * The audio data sampling rate in Hz.
     */
    private int mSampleRate;
    /**
     * The number of input audio channels (1 is mono, 2 is stereo)
     */
    private int mChannelCount;
    /**
     * The audio channel position mask
     */
    private int mChannelMask;
    /**
     * The audio channel index mask
     */
    private int mChannelIndexMask;
    /**
     * The encoding of the audio samples.
     * @see AudioFormat#ENCODING_PCM_8BIT
     * @see AudioFormat#ENCODING_PCM_16BIT
     * @see AudioFormat#ENCODING_PCM_FLOAT
     */
    private int mAudioFormat;
    /**
     * Where the audio data is recorded from.
     */
    private int mRecordSource;
    /**
     * Indicates the state of the AudioRecord instance.
     */
    private int mState = STATE_UNINITIALIZED;
    /**
     * Indicates the recording state of the AudioRecord instance.
     */
    private int mRecordingState = RECORDSTATE_STOPPED;
    /**
     * Lock to make sure mRecordingState updates are reflecting the actual state of the object.
     */
    private final Object mRecordingStateLock = new Object();
    /**
     * The listener the AudioRecord notifies when the record position reaches a marker
     * or for periodic updates during the progression of the record head.
     *  @see #setRecordPositionUpdateListener(OnRecordPositionUpdateListener)
     *  @see #setRecordPositionUpdateListener(OnRecordPositionUpdateListener, Handler)
     */
    private OnRecordPositionUpdateListener mPositionListener = null;
    /**
     * Lock to protect position listener updates against event notifications
     */
    private final Object mPositionListenerLock = new Object();
    /**
     * Handler for marker events coming from the native code
     */
    private NativeEventHandler mEventHandler = null;
    /**
     * Looper associated with the thread that creates the AudioRecord instance
     */
    private Looper mInitializationLooper = null;
    /**
     * Size of the native audio buffer.
     */
    private int mNativeBufferSizeInBytes = 0;
    /**
     * Audio session ID
     */
    private int mSessionId = AudioSystem.AUDIO_SESSION_ALLOCATE;
    /**
     * AudioAttributes
     */
    private AudioAttributes mAudioAttributes;
    private boolean mIsSubmixFullVolume = false;

    //---------------------------------------------------------
    // Constructor, Finalize
    //--------------------
    /**
     * Class constructor.
     * Though some invalid parameters will result in an {@link IllegalArgumentException} exception,
     * other errors do not.  Thus you should call {@link #getState()} immediately after construction
     * to confirm that the object is usable.
     * @param audioSource the recording source.
     *   See {@link MediaRecorder.AudioSource} for the recording source definitions.
     * @param sampleRateInHz the sample rate expressed in Hertz. 44100Hz is currently the only
     *   rate that is guaranteed to work on all devices, but other rates such as 22050,
     *   16000, and 11025 may work on some devices.
     * @param channelConfig describes the configuration of the audio channels.
     *   See {@link AudioFormat#CHANNEL_IN_MONO} and
     *   {@link AudioFormat#CHANNEL_IN_STEREO}.  {@link AudioFormat#CHANNEL_IN_MONO} is guaranteed
     *   to work on all devices.
     * @param audioFormat the format in which the audio data is to be returned.
     *   See {@link AudioFormat#ENCODING_PCM_8BIT}, {@link AudioFormat#ENCODING_PCM_16BIT},
     *   and {@link AudioFormat#ENCODING_PCM_FLOAT}.
     * @param bufferSizeInBytes the total size (in bytes) of the buffer where audio data is written
     *   to during the recording. New audio data can be read from this buffer in smaller chunks
     *   than this size. See {@link #getMinBufferSize(int, int, int)} to determine the minimum
     *   required buffer size for the successful creation of an AudioRecord instance. Using values
     *   smaller than getMinBufferSize() will result in an initialization failure.
     * @throws java.lang.IllegalArgumentException
     */
    public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat,
            int bufferSizeInBytes)
    throws IllegalArgumentException {
        this((new AudioAttributes.Builder())
                    .setInternalCapturePreset(audioSource)
                    .build(),
                (new AudioFormat.Builder())
                    .setChannelMask(getChannelMaskFromLegacyConfig(channelConfig,
                                        true/*allow legacy configurations*/))
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRateInHz)
                    .build(),
                bufferSizeInBytes,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    /**
     * @hide
     * Class constructor with {@link AudioAttributes} and {@link AudioFormat}.
     * @param attributes a non-null {@link AudioAttributes} instance. Use
     *     {@link AudioAttributes.Builder#setAudioSource(int)} for configuring the audio
     *     source for this instance.
     * @param format a non-null {@link AudioFormat} instance describing the format of the data
     *     that will be recorded through this AudioRecord. See {@link AudioFormat.Builder} for
     *     configuring the audio format parameters such as encoding, channel mask and sample rate.
     * @param bufferSizeInBytes the total size (in bytes) of the buffer where audio data is written
     *   to during the recording. New audio data can be read from this buffer in smaller chunks
     *   than this size. See {@link #getMinBufferSize(int, int, int)} to determine the minimum
     *   required buffer size for the successful creation of an AudioRecord instance. Using values
     *   smaller than getMinBufferSize() will result in an initialization failure.
     * @param sessionId ID of audio session the AudioRecord must be attached to, or
     *   {@link AudioManager#AUDIO_SESSION_ID_GENERATE} if the session isn't known at construction
     *   time. See also {@link AudioManager#generateAudioSessionId()} to obtain a session ID before
     *   construction.
     * @throws IllegalArgumentException
     */
    @SystemApi
    public AudioRecord(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes,
            int sessionId) throws IllegalArgumentException {
        mRecordingState = RECORDSTATE_STOPPED;

        if (attributes == null) {
            throw new IllegalArgumentException("Illegal null AudioAttributes");
        }
        if (format == null) {
            throw new IllegalArgumentException("Illegal null AudioFormat");
        }

        // remember which looper is associated with the AudioRecord instanciation
        if ((mInitializationLooper = Looper.myLooper()) == null) {
            mInitializationLooper = Looper.getMainLooper();
        }

        // is this AudioRecord using REMOTE_SUBMIX at full volume?
        if (attributes.getCapturePreset() == MediaRecorder.AudioSource.REMOTE_SUBMIX) {
            final AudioAttributes.Builder filteredAttr = new AudioAttributes.Builder();
            final Iterator<String> tagsIter = attributes.getTags().iterator();
            while (tagsIter.hasNext()) {
                final String tag = tagsIter.next();
                if (tag.equalsIgnoreCase(SUBMIX_FIXED_VOLUME)) {
                    mIsSubmixFullVolume = true;
                    Log.v(TAG, "Will record from REMOTE_SUBMIX at full fixed volume");
                } else { // SUBMIX_FIXED_VOLUME: is not to be propagated to the native layers
                    filteredAttr.addTag(tag);
                }
            }
            filteredAttr.setInternalCapturePreset(attributes.getCapturePreset());
            mAudioAttributes = filteredAttr.build();
        } else {
            mAudioAttributes = attributes;
        }

        int rate = 0;
        if ((format.getPropertySetMask()
                & AudioFormat.AUDIO_FORMAT_HAS_PROPERTY_SAMPLE_RATE) != 0)
        {
            rate = format.getSampleRate();
        } else {
            rate = AudioSystem.getPrimaryOutputSamplingRate();
            if (rate <= 0) {
                rate = 44100;
            }
        }

        int encoding = AudioFormat.ENCODING_DEFAULT;
        if ((format.getPropertySetMask() & AudioFormat.AUDIO_FORMAT_HAS_PROPERTY_ENCODING) != 0)
        {
            encoding = format.getEncoding();
        }

        audioParamCheck(attributes.getCapturePreset(), rate, encoding);

        if ((format.getPropertySetMask()
                & AudioFormat.AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_INDEX_MASK) != 0) {
            mChannelIndexMask = format.getChannelIndexMask();
            mChannelCount = format.getChannelCount();
        }
        if ((format.getPropertySetMask()
                & AudioFormat.AUDIO_FORMAT_HAS_PROPERTY_CHANNEL_MASK) != 0) {
            mChannelMask = getChannelMaskFromLegacyConfig(format.getChannelMask(), false);
            mChannelCount = format.getChannelCount();
        } else if (mChannelIndexMask == 0) {
            mChannelMask = getChannelMaskFromLegacyConfig(AudioFormat.CHANNEL_IN_DEFAULT, false);
            mChannelCount =  AudioFormat.channelCountFromInChannelMask(mChannelMask);
        }

        audioBuffSizeCheck(bufferSizeInBytes);

        int[] session = new int[1];
        session[0] = sessionId;
        //TODO: update native initialization when information about hardware init failure
        //      due to capture device already open is available.
        int initResult = native_setup( new WeakReference<AudioRecord>(this),
                mAudioAttributes, mSampleRate, mChannelMask, mChannelIndexMask,
                mAudioFormat, mNativeBufferSizeInBytes,
                session, ActivityThread.currentOpPackageName());
        if (initResult != SUCCESS) {
            loge("Error code "+initResult+" when initializing native AudioRecord object.");
            return; // with mState == STATE_UNINITIALIZED
        }

        mSessionId = session[0];

        mState = STATE_INITIALIZED;
    }

    /**
     * Builder class for {@link AudioRecord} objects.
     * Use this class to configure and create an <code>AudioRecord</code> instance. By setting the
     * recording source and audio format parameters, you indicate which of
     * those vary from the default behavior on the device.
     * <p> Here is an example where <code>Builder</code> is used to specify all {@link AudioFormat}
     * parameters, to be used by a new <code>AudioRecord</code> instance:
     *
     * <pre class="prettyprint">
     * AudioRecord recorder = new AudioRecord.Builder()
     *         .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
     *         .setAudioFormat(new AudioFormat.Builder()
     *                 .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
     *                 .setSampleRate(32000)
     *                 .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
     *                 .build())
     *         .setBufferSize(2*minBuffSize)
     *         .build();
     * </pre>
     * <p>
     * If the audio source is not set with {@link #setAudioSource(int)},
     * {@link MediaRecorder.AudioSource#DEFAULT} is used.
     * <br>If the audio format is not specified or is incomplete, its sample rate will be the
     * default output sample rate of the device (see
     * {@link AudioManager#PROPERTY_OUTPUT_SAMPLE_RATE}), its channel configuration will be
     * {@link AudioFormat#CHANNEL_IN_MONO}, and the encoding will be
     * {@link AudioFormat#ENCODING_PCM_16BIT}.
     * <br>If the buffer size is not specified with {@link #setBufferSizeInBytes(int)},
     * the minimum buffer size for the source is used.
     */
    public static class Builder {
        private AudioAttributes mAttributes;
        private AudioFormat mFormat;
        private int mBufferSizeInBytes;
        private int mSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE;

        /**
         * Constructs a new Builder with the default values as described above.
         */
        public Builder() {
        }

        /**
         * @param source the audio source.
         * See {@link MediaRecorder.AudioSource} for the supported audio source definitions.
         * @return the same Builder instance.
         * @throws IllegalArgumentException
         */
        public Builder setAudioSource(int source) throws IllegalArgumentException {
            if ( (source < MediaRecorder.AudioSource.DEFAULT) ||
                    (source > MediaRecorder.getAudioSourceMax()) ) {
                throw new IllegalArgumentException("Invalid audio source " + source);
            }
            mAttributes = new AudioAttributes.Builder()
                    .setInternalCapturePreset(source)
                    .build();
            return this;
        }

        /**
         * @hide
         * To be only used by system components. Allows specifying non-public capture presets
         * @param attributes a non-null {@link AudioAttributes} instance that contains the capture
         *     preset to be used.
         * @return the same Builder instance.
         * @throws IllegalArgumentException
         */
        @SystemApi
        public Builder setAudioAttributes(@NonNull AudioAttributes attributes)
                throws IllegalArgumentException {
            if (attributes == null) {
                throw new IllegalArgumentException("Illegal null AudioAttributes argument");
            }
            if (attributes.getCapturePreset() == MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID) {
                throw new IllegalArgumentException(
                        "No valid capture preset in AudioAttributes argument");
            }
            // keep reference, we only copy the data when building
            mAttributes = attributes;
            return this;
        }

        /**
         * Sets the format of the audio data to be captured.
         * @param format a non-null {@link AudioFormat} instance
         * @return the same Builder instance.
         * @throws IllegalArgumentException
         */
        public Builder setAudioFormat(@NonNull AudioFormat format) throws IllegalArgumentException {
            if (format == null) {
                throw new IllegalArgumentException("Illegal null AudioFormat argument");
            }
            // keep reference, we only copy the data when building
            mFormat = format;
            return this;
        }

        /**
         * Sets the total size (in bytes) of the buffer where audio data is written
         * during the recording. New audio data can be read from this buffer in smaller chunks
         * than this size. See {@link #getMinBufferSize(int, int, int)} to determine the minimum
         * required buffer size for the successful creation of an AudioRecord instance.
         * Since bufferSizeInBytes may be internally increased to accommodate the source
         * requirements, use {@link #getBufferSizeInFrames()} to determine the actual buffer size
         * in frames.
         * @param bufferSizeInBytes a value strictly greater than 0
         * @return the same Builder instance.
         * @throws IllegalArgumentException
         */
        public Builder setBufferSizeInBytes(int bufferSizeInBytes) throws IllegalArgumentException {
            if (bufferSizeInBytes <= 0) {
                throw new IllegalArgumentException("Invalid buffer size " + bufferSizeInBytes);
            }
            mBufferSizeInBytes = bufferSizeInBytes;
            return this;
        }

        /**
         * @hide
         * To be only used by system components.
         * @param sessionId ID of audio session the AudioRecord must be attached to, or
         *     {@link AudioManager#AUDIO_SESSION_ID_GENERATE} if the session isn't known at
         *     construction time.
         * @return the same Builder instance.
         * @throws IllegalArgumentException
         */
        @SystemApi
        public Builder setSessionId(int sessionId) throws IllegalArgumentException {
            if (sessionId < 0) {
                throw new IllegalArgumentException("Invalid session ID " + sessionId);
            }
            mSessionId = sessionId;
            return this;
        }

        /**
         * @return a new {@link AudioRecord} instance successfully initialized with all
         *     the parameters set on this <code>Builder</code>.
         * @throws UnsupportedOperationException if the parameters set on the <code>Builder</code>
         *     were incompatible, or if they are not supported by the device,
         *     or if the device was not available.
         */
        public AudioRecord build() throws UnsupportedOperationException {
            if (mFormat == null) {
                mFormat = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build();
            } else {
                if (mFormat.getEncoding() == AudioFormat.ENCODING_INVALID) {
                    mFormat = new AudioFormat.Builder(mFormat)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build();
                }
                if (mFormat.getChannelMask() == AudioFormat.CHANNEL_INVALID
                        && mFormat.getChannelIndexMask() == AudioFormat.CHANNEL_INVALID) {
                    mFormat = new AudioFormat.Builder(mFormat)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build();
                }
            }
            if (mAttributes == null) {
                mAttributes = new AudioAttributes.Builder()
                        .setInternalCapturePreset(MediaRecorder.AudioSource.DEFAULT)
                        .build();
            }
            try {
                // If the buffer size is not specified,
                // use a single frame for the buffer size and let the
                // native code figure out the minimum buffer size.
                if (mBufferSizeInBytes == 0) {
                    mBufferSizeInBytes = mFormat.getChannelCount()
                            * mFormat.getBytesPerSample(mFormat.getEncoding());
                }
                final AudioRecord record = new AudioRecord(
                        mAttributes, mFormat, mBufferSizeInBytes, mSessionId);
                if (record.getState() == STATE_UNINITIALIZED) {
                    // release is not necessary
                    throw new UnsupportedOperationException("Cannot create AudioRecord");
                }
                return record;
            } catch (IllegalArgumentException e) {
                throw new UnsupportedOperationException(e.getMessage());
            }
        }
    }

    // Convenience method for the constructor's parameter checks.
    // This, getChannelMaskFromLegacyConfig and audioBuffSizeCheck are where constructor
    // IllegalArgumentException-s are thrown
    private static int getChannelMaskFromLegacyConfig(int inChannelConfig,
            boolean allowLegacyConfig) {
        int mask;
        switch (inChannelConfig) {
        case AudioFormat.CHANNEL_IN_DEFAULT: // AudioFormat.CHANNEL_CONFIGURATION_DEFAULT
        case AudioFormat.CHANNEL_IN_MONO:
        case AudioFormat.CHANNEL_CONFIGURATION_MONO:
            mask = AudioFormat.CHANNEL_IN_MONO;
            break;
        case AudioFormat.CHANNEL_IN_STEREO:
        case AudioFormat.CHANNEL_CONFIGURATION_STEREO:
            mask = AudioFormat.CHANNEL_IN_STEREO;
            break;
        case (AudioFormat.CHANNEL_IN_FRONT | AudioFormat.CHANNEL_IN_BACK):
            mask = inChannelConfig;
            break;
        default:
            throw new IllegalArgumentException("Unsupported channel configuration.");
        }

        if (!allowLegacyConfig && ((inChannelConfig == AudioFormat.CHANNEL_CONFIGURATION_MONO)
                || (inChannelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO))) {
            // only happens with the constructor that uses AudioAttributes and AudioFormat
            throw new IllegalArgumentException("Unsupported deprecated configuration.");
        }

        return mask;
    }
    // postconditions:
    //    mRecordSource is valid
    //    mAudioFormat is valid
    //    mSampleRate is valid
    private void audioParamCheck(int audioSource, int sampleRateInHz, int audioFormat)
            throws IllegalArgumentException {

        //--------------
        // audio source
        if ( (audioSource < MediaRecorder.AudioSource.DEFAULT) ||
             ((audioSource > MediaRecorder.getAudioSourceMax()) &&
              (audioSource != MediaRecorder.AudioSource.RADIO_TUNER) &&
              (audioSource != MediaRecorder.AudioSource.HOTWORD)) )  {
            throw new IllegalArgumentException("Invalid audio source.");
        }
        mRecordSource = audioSource;

        //--------------
        // sample rate
        if ((sampleRateInHz < SAMPLE_RATE_HZ_MIN) || (sampleRateInHz > SAMPLE_RATE_HZ_MAX)) {
            throw new IllegalArgumentException(sampleRateInHz
                    + "Hz is not a supported sample rate.");
        }
        mSampleRate = sampleRateInHz;

        //--------------
        // audio format
        switch (audioFormat) {
        case AudioFormat.ENCODING_DEFAULT:
            mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
            break;
        case AudioFormat.ENCODING_PCM_FLOAT:
        case AudioFormat.ENCODING_PCM_16BIT:
        case AudioFormat.ENCODING_PCM_8BIT:
        case AudioFormat.ENCODING_AMRNB:
        case AudioFormat.ENCODING_AMRWB:
        case AudioFormat.ENCODING_EVRC:
        case AudioFormat.ENCODING_EVRCB:
        case AudioFormat.ENCODING_EVRCWB:
        case AudioFormat.ENCODING_EVRCNW:
            mAudioFormat = audioFormat;
            break;
        default:
            throw new IllegalArgumentException("Unsupported sample encoding."
                    + " Should be ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, or ENCODING_PCM_FLOAT.");
        }
    }


    // Convenience method for the contructor's audio buffer size check.
    // preconditions:
    //    mChannelCount is valid
    //    mAudioFormat is AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT,
    //                 or AudioFormat.ENCODING_PCM_FLOAT
    // postcondition:
    //    mNativeBufferSizeInBytes is valid (multiple of frame size, positive)
    private void audioBuffSizeCheck(int audioBufferSize) throws IllegalArgumentException {
        // NB: this section is only valid with PCM data.
        // To update when supporting compressed formats
        int frameSizeInBytes = mChannelCount
            * (AudioFormat.getBytesPerSample(mAudioFormat));
        if ((audioBufferSize % frameSizeInBytes != 0) || (audioBufferSize < 1)) {
            throw new IllegalArgumentException("Invalid audio buffer size.");
        }

        mNativeBufferSizeInBytes = audioBufferSize;
    }



    /**
     * Releases the native AudioRecord resources.
     * The object can no longer be used and the reference should be set to null
     * after a call to release()
     */
    public void release() {
        try {
            stop();
        } catch(IllegalStateException ise) {
            // don't raise an exception, we're releasing the resources.
        }
        native_release();
        mState = STATE_UNINITIALIZED;
    }


    @Override
    protected void finalize() {
        // will cause stop() to be called, and if appropriate, will handle fixed volume recording
        release();
    }


    //--------------------------------------------------------------------------
    // Getters
    //--------------------
    /**
     * Returns the configured audio data sample rate in Hz
     */
    public int getSampleRate() {
        return mSampleRate;
    }

    /**
     * Returns the audio recording source.
     * @see MediaRecorder.AudioSource
     */
    public int getAudioSource() {
        return mRecordSource;
    }

    /**
     * Returns the configured audio data encoding. See {@link AudioFormat#ENCODING_PCM_8BIT},
     * {@link AudioFormat#ENCODING_PCM_16BIT}, and {@link AudioFormat#ENCODING_PCM_FLOAT}.
     */
    public int getAudioFormat() {
        return mAudioFormat;
    }

    /**
     * Returns the configured channel position mask.
     * <p> See {@link AudioFormat#CHANNEL_IN_MONO}
     * and {@link AudioFormat#CHANNEL_IN_STEREO}.
     * This method may return {@link AudioFormat#CHANNEL_INVALID} if
     * a channel index mask is used.
     * Consider {@link #getFormat()} instead, to obtain an {@link AudioFormat},
     * which contains both the channel position mask and the channel index mask.
     */
    public int getChannelConfiguration() {
        return mChannelMask;
    }

    /**
     * Returns the configured <code>AudioRecord</code> format.
     * @return an {@link AudioFormat} containing the
     * <code>AudioRecord</code> parameters at the time of configuration.
     */
    public @NonNull AudioFormat getFormat() {
        AudioFormat.Builder builder = new AudioFormat.Builder()
            .setSampleRate(mSampleRate)
            .setEncoding(mAudioFormat);
        if (mChannelMask != AudioFormat.CHANNEL_INVALID) {
            builder.setChannelMask(mChannelMask);
        }
        if (mChannelIndexMask != AudioFormat.CHANNEL_INVALID  /* 0 */) {
            builder.setChannelIndexMask(mChannelIndexMask);
        }
        return builder.build();
    }

    /**
     * Returns the configured number of channels.
     */
    public int getChannelCount() {
        return mChannelCount;
    }

    /**
     * Returns the state of the AudioRecord instance. This is useful after the
     * AudioRecord instance has been created to check if it was initialized
     * properly. This ensures that the appropriate hardware resources have been
     * acquired.
     * @see AudioRecord#STATE_INITIALIZED
     * @see AudioRecord#STATE_UNINITIALIZED
     */
    public int getState() {
        return mState;
    }

    /**
     * Returns the recording state of the AudioRecord instance.
     * @see AudioRecord#RECORDSTATE_STOPPED
     * @see AudioRecord#RECORDSTATE_RECORDING
     */
    public int getRecordingState() {
        synchronized (mRecordingStateLock) {
            return mRecordingState;
        }
    }

    /**
     *  Returns the frame count of the native <code>AudioRecord</code> buffer.
     *  This is greater than or equal to the bufferSizeInBytes converted to frame units
     *  specified in the <code>AudioRecord</code> constructor or Builder.
     *  The native frame count may be enlarged to accommodate the requirements of the
     *  source on creation or if the <code>AudioRecord</code>
     *  is subsequently rerouted.
     *  @return current size in frames of the <code>AudioRecord</code> buffer.
     *  @throws IllegalStateException
     */
    public int getBufferSizeInFrames() {
        return native_get_buffer_size_in_frames();
    }

    /**
     * Returns the notification marker position expressed in frames.
     */
    public int getNotificationMarkerPosition() {
        return native_get_marker_pos();
    }

    /**
     * Returns the notification update period expressed in frames.
     */
    public int getPositionNotificationPeriod() {
        return native_get_pos_update_period();
    }

    /**
     * Returns the minimum buffer size required for the successful creation of an AudioRecord
     * object, in byte units.
     * Note that this size doesn't guarantee a smooth recording under load, and higher values
     * should be chosen according to the expected frequency at which the AudioRecord instance
     * will be polled for new data.
     * See {@link #AudioRecord(int, int, int, int, int)} for more information on valid
     * configuration values.
     * @param sampleRateInHz the sample rate expressed in Hertz.
     * @param channelConfig describes the configuration of the audio channels.
     *   See {@link AudioFormat#CHANNEL_IN_MONO} and
     *   {@link AudioFormat#CHANNEL_IN_STEREO}
     * @param audioFormat the format in which the audio data is represented.
     *   See {@link AudioFormat#ENCODING_PCM_16BIT}.
     * @return {@link #ERROR_BAD_VALUE} if the recording parameters are not supported by the
     *  hardware, or an invalid parameter was passed,
     *  or {@link #ERROR} if the implementation was unable to query the hardware for its
     *  input properties,
     *   or the minimum buffer size expressed in bytes.
     * @see #AudioRecord(int, int, int, int, int)
     */
    static public int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        int channelCount = 0;
        switch (channelConfig) {
        case AudioFormat.CHANNEL_IN_DEFAULT: // AudioFormat.CHANNEL_CONFIGURATION_DEFAULT
        case AudioFormat.CHANNEL_IN_MONO:
        case AudioFormat.CHANNEL_CONFIGURATION_MONO:
            channelCount = 1;
            break;
        case AudioFormat.CHANNEL_IN_STEREO:
        case AudioFormat.CHANNEL_CONFIGURATION_STEREO:
        case (AudioFormat.CHANNEL_IN_FRONT | AudioFormat.CHANNEL_IN_BACK):
            channelCount = 2;
            break;
        case AudioFormat.CHANNEL_IN_5POINT1:
            channelCount = 6;
            break;
        case AudioFormat.CHANNEL_INVALID:
        default:
            loge("getMinBufferSize(): Invalid channel configuration.");
            return ERROR_BAD_VALUE;
        }

        int size = native_get_min_buff_size(sampleRateInHz, channelCount, audioFormat);
        if (size == 0) {
            return ERROR_BAD_VALUE;
        }
        else if (size == -1) {
            return ERROR;
        }
        else {
            return size;
        }
    }

    /**
     * Returns the audio session ID.
     *
     * @return the ID of the audio session this AudioRecord belongs to.
     */
    public int getAudioSessionId() {
        return mSessionId;
    }

    //---------------------------------------------------------
    // Transport control methods
    //--------------------
    /**
     * Starts recording from the AudioRecord instance.
     * @throws IllegalStateException
     */
    public void startRecording()
    throws IllegalStateException {
        android.util.SeempLog.record(70);
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("startRecording() called on an "
                    + "uninitialized AudioRecord.");
        }

        // start recording
        synchronized(mRecordingStateLock) {
            if (native_start(MediaSyncEvent.SYNC_EVENT_NONE, 0) == SUCCESS) {
                handleFullVolumeRec(true);
                mRecordingState = RECORDSTATE_RECORDING;
            }
        }

        if (getRecordingState() == RECORDSTATE_RECORDING &&
                getAudioSource() == MediaRecorder.AudioSource.HOTWORD) {
            handleHotwordInput(true);
        }
    }

    /**
     * Starts recording from the AudioRecord instance when the specified synchronization event
     * occurs on the specified audio session.
     * @throws IllegalStateException
     * @param syncEvent event that triggers the capture.
     * @see MediaSyncEvent
     */
    public void startRecording(MediaSyncEvent syncEvent)
    throws IllegalStateException {
        android.util.SeempLog.record(70);
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("startRecording() called on an "
                    + "uninitialized AudioRecord.");
        }

        // start recording
        synchronized(mRecordingStateLock) {
            if (native_start(syncEvent.getType(), syncEvent.getAudioSessionId()) == SUCCESS) {
                handleFullVolumeRec(true);
                mRecordingState = RECORDSTATE_RECORDING;
            }
        }
    }

    /**
     * Stops recording.
     * @throws IllegalStateException
     */
    public void stop()
    throws IllegalStateException {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("stop() called on an uninitialized AudioRecord.");
        }

        // stop recording
        synchronized(mRecordingStateLock) {
            handleFullVolumeRec(false);
            native_stop();
            mRecordingState = RECORDSTATE_STOPPED;
        }

        if (getAudioSource() == MediaRecorder.AudioSource.HOTWORD) {
            handleHotwordInput(false);
        }
    }

    private final IBinder mICallBack = new Binder();
    private void handleFullVolumeRec(boolean starting) {
        if (!mIsSubmixFullVolume) {
            return;
        }
        final IBinder b = ServiceManager.getService(android.content.Context.AUDIO_SERVICE);
        final IAudioService ias = IAudioService.Stub.asInterface(b);
        try {
            ias.forceRemoteSubmixFullVolume(starting, mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Error talking to AudioService when handling full submix volume", e);
        }
    }

    private void handleHotwordInput(boolean listening) {
        final IBinder b = ServiceManager.getService(android.content.Context.AUDIO_SERVICE);
        final IAudioService ias = IAudioService.Stub.asInterface(b);
        try {
            // If the caller tries to start recording with the HOTWORD input
            // before AUDIO_SERVICE has started, IAudioService may not be available.
            if (ias != null) {
                ias.handleHotwordInput(listening);
            } else {
                Log.e(TAG, "Error talking to AudioService when handling hotword input, "
                        + "AudioService unavailable");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error talking to AudioService when handling hotword input.", e);
        }
    }

    //---------------------------------------------------------
    // Audio data supply
    //--------------------
    /**
     * Reads audio data from the audio hardware for recording into a byte array.
     * The format specified in the AudioRecord constructor should be
     * {@link AudioFormat#ENCODING_PCM_8BIT} to correspond to the data in the array.
     * @param audioData the array to which the recorded audio data is written.
     * @param offsetInBytes index in audioData from which the data is written expressed in bytes.
     * @param sizeInBytes the number of requested bytes.
     * @return the number of bytes that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of bytes will not exceed sizeInBytes.
     */
    public int read(@NonNull byte[] audioData, int offsetInBytes, int sizeInBytes) {
        return read(audioData, offsetInBytes, sizeInBytes, READ_BLOCKING);
    }

    /**
     * Reads audio data from the audio hardware for recording into a byte array.
     * The format specified in the AudioRecord constructor should be
     * {@link AudioFormat#ENCODING_PCM_8BIT} to correspond to the data in the array.
     * @param audioData the array to which the recorded audio data is written.
     * @param offsetInBytes index in audioData from which the data is written expressed in bytes.
     * @param sizeInBytes the number of requested bytes.
     * @param readMode one of {@link #READ_BLOCKING}, {@link #READ_NON_BLOCKING}.
     *     <br>With {@link #READ_BLOCKING}, the read will block until all the requested data
     *     is read.
     *     <br>With {@link #READ_NON_BLOCKING}, the read will return immediately after
     *     reading as much audio data as possible without blocking.
     * @return the number of bytes that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of bytes will not exceed sizeInBytes.
     */
    public int read(@NonNull byte[] audioData, int offsetInBytes, int sizeInBytes,
            @ReadMode int readMode) {
        if (mState != STATE_INITIALIZED  || mAudioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
            return ERROR_INVALID_OPERATION;
        }

        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            Log.e(TAG, "AudioRecord.read() called with invalid blocking mode");
            return ERROR_BAD_VALUE;
        }

        if ( (audioData == null) || (offsetInBytes < 0 ) || (sizeInBytes < 0)
                || (offsetInBytes + sizeInBytes < 0)  // detect integer overflow
                || (offsetInBytes + sizeInBytes > audioData.length)) {
            return ERROR_BAD_VALUE;
        }

        return native_read_in_byte_array(audioData, offsetInBytes, sizeInBytes,
                readMode == READ_BLOCKING);
    }

    /**
     * Reads audio data from the audio hardware for recording into a short array.
     * The format specified in the AudioRecord constructor should be
     * {@link AudioFormat#ENCODING_PCM_16BIT} to correspond to the data in the array.
     * @param audioData the array to which the recorded audio data is written.
     * @param offsetInShorts index in audioData from which the data is written expressed in shorts.
     * @param sizeInShorts the number of requested shorts.
     * @return the number of shorts that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of shorts will not exceed sizeInShorts.
     */
    public int read(@NonNull short[] audioData, int offsetInShorts, int sizeInShorts) {
        return read(audioData, offsetInShorts, sizeInShorts, READ_BLOCKING);
    }

    /**
     * Reads audio data from the audio hardware for recording into a short array.
     * The format specified in the AudioRecord constructor should be
     * {@link AudioFormat#ENCODING_PCM_16BIT} to correspond to the data in the array.
     * @param audioData the array to which the recorded audio data is written.
     * @param offsetInShorts index in audioData from which the data is written expressed in shorts.
     * @param sizeInShorts the number of requested shorts.
     * @param readMode one of {@link #READ_BLOCKING}, {@link #READ_NON_BLOCKING}.
     *     <br>With {@link #READ_BLOCKING}, the read will block until all the requested data
     *     is read.
     *     <br>With {@link #READ_NON_BLOCKING}, the read will return immediately after
     *     reading as much audio data as possible without blocking.
     * @return the number of shorts that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of shorts will not exceed sizeInShorts.
     */
    public int read(@NonNull short[] audioData, int offsetInShorts, int sizeInShorts,
            @ReadMode int readMode) {
        if (mState != STATE_INITIALIZED || mAudioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
            return ERROR_INVALID_OPERATION;
        }

        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            Log.e(TAG, "AudioRecord.read() called with invalid blocking mode");
            return ERROR_BAD_VALUE;
        }

        if ( (audioData == null) || (offsetInShorts < 0 ) || (sizeInShorts < 0)
                || (offsetInShorts + sizeInShorts < 0)  // detect integer overflow
                || (offsetInShorts + sizeInShorts > audioData.length)) {
            return ERROR_BAD_VALUE;
        }

        return native_read_in_short_array(audioData, offsetInShorts, sizeInShorts,
                readMode == READ_BLOCKING);
    }

    /**
     * Reads audio data from the audio hardware for recording into a float array.
     * The format specified in the AudioRecord constructor should be
     * {@link AudioFormat#ENCODING_PCM_FLOAT} to correspond to the data in the array.
     * @param audioData the array to which the recorded audio data is written.
     * @param offsetInFloats index in audioData from which the data is written.
     * @param sizeInFloats the number of requested floats.
     * @param readMode one of {@link #READ_BLOCKING}, {@link #READ_NON_BLOCKING}.
     *     <br>With {@link #READ_BLOCKING}, the read will block until all the requested data
     *     is read.
     *     <br>With {@link #READ_NON_BLOCKING}, the read will return immediately after
     *     reading as much audio data as possible without blocking.
     * @return the number of floats that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of floats will not exceed sizeInFloats.
     */
    public int read(@NonNull float[] audioData, int offsetInFloats, int sizeInFloats,
            @ReadMode int readMode) {
        if (mState == STATE_UNINITIALIZED) {
            Log.e(TAG, "AudioRecord.read() called in invalid state STATE_UNINITIALIZED");
            return ERROR_INVALID_OPERATION;
        }

        if (mAudioFormat != AudioFormat.ENCODING_PCM_FLOAT) {
            Log.e(TAG, "AudioRecord.read(float[] ...) requires format ENCODING_PCM_FLOAT");
            return ERROR_INVALID_OPERATION;
        }

        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            Log.e(TAG, "AudioRecord.read() called with invalid blocking mode");
            return ERROR_BAD_VALUE;
        }

        if ((audioData == null) || (offsetInFloats < 0) || (sizeInFloats < 0)
                || (offsetInFloats + sizeInFloats < 0)  // detect integer overflow
                || (offsetInFloats + sizeInFloats > audioData.length)) {
            return ERROR_BAD_VALUE;
        }

        return native_read_in_float_array(audioData, offsetInFloats, sizeInFloats,
                readMode == READ_BLOCKING);
    }

    /**
     * Reads audio data from the audio hardware for recording into a direct buffer. If this buffer
     * is not a direct buffer, this method will always return 0.
     * Note that the value returned by {@link java.nio.Buffer#position()} on this buffer is
     * unchanged after a call to this method.
     * The representation of the data in the buffer will depend on the format specified in
     * the AudioRecord constructor, and will be native endian.
     * @param audioBuffer the direct buffer to which the recorded audio data is written.
     * @param sizeInBytes the number of requested bytes. It is recommended but not enforced
     *    that the number of bytes requested be a multiple of the frame size (sample size in
     *    bytes multiplied by the channel count).
     * @return the number of bytes that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of bytes will not exceed sizeInBytes.
     *    The number of bytes read will truncated to be a multiple of the frame size.
     */
    public int read(@NonNull ByteBuffer audioBuffer, int sizeInBytes) {
        return read(audioBuffer, sizeInBytes, READ_BLOCKING);
    }

    /**
     * Reads audio data from the audio hardware for recording into a direct buffer. If this buffer
     * is not a direct buffer, this method will always return 0.
     * Note that the value returned by {@link java.nio.Buffer#position()} on this buffer is
     * unchanged after a call to this method.
     * The representation of the data in the buffer will depend on the format specified in
     * the AudioRecord constructor, and will be native endian.
     * @param audioBuffer the direct buffer to which the recorded audio data is written.
     * @param sizeInBytes the number of requested bytes. It is recommended but not enforced
     *    that the number of bytes requested be a multiple of the frame size (sample size in
     *    bytes multiplied by the channel count).
     * @param readMode one of {@link #READ_BLOCKING}, {@link #READ_NON_BLOCKING}.
     *     <br>With {@link #READ_BLOCKING}, the read will block until all the requested data
     *     is read.
     *     <br>With {@link #READ_NON_BLOCKING}, the read will return immediately after
     *     reading as much audio data as possible without blocking.
     * @return the number of bytes that were read or {@link #ERROR_INVALID_OPERATION}
     *    if the object wasn't properly initialized, or {@link #ERROR_BAD_VALUE} if
     *    the parameters don't resolve to valid data and indexes.
     *    The number of bytes will not exceed sizeInBytes.
     *    The number of bytes read will truncated to be a multiple of the frame size.
     */
    public int read(@NonNull ByteBuffer audioBuffer, int sizeInBytes, @ReadMode int readMode) {
        if (mState != STATE_INITIALIZED) {
            return ERROR_INVALID_OPERATION;
        }

        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            Log.e(TAG, "AudioRecord.read() called with invalid blocking mode");
            return ERROR_BAD_VALUE;
        }

        if ( (audioBuffer == null) || (sizeInBytes < 0) ) {
            return ERROR_BAD_VALUE;
        }

        return native_read_in_direct_buffer(audioBuffer, sizeInBytes, readMode == READ_BLOCKING);
    }

    //--------------------------------------------------------------------------
    // Initialization / configuration
    //--------------------
    /**
     * Sets the listener the AudioRecord notifies when a previously set marker is reached or
     * for each periodic record head position update.
     * @param listener
     */
    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener) {
        setRecordPositionUpdateListener(listener, null);
    }

    /**
     * Sets the listener the AudioRecord notifies when a previously set marker is reached or
     * for each periodic record head position update.
     * Use this method to receive AudioRecord events in the Handler associated with another
     * thread than the one in which you created the AudioRecord instance.
     * @param listener
     * @param handler the Handler that will receive the event notification messages.
     */
    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener,
                                                    Handler handler) {
        synchronized (mPositionListenerLock) {

            mPositionListener = listener;

            if (listener != null) {
                if (handler != null) {
                    mEventHandler = new NativeEventHandler(this, handler.getLooper());
                } else {
                    // no given handler, use the looper the AudioRecord was created in
                    mEventHandler = new NativeEventHandler(this, mInitializationLooper);
                }
            } else {
                mEventHandler = null;
            }
        }

    }


    /**
     * Sets the marker position at which the listener is called, if set with
     * {@link #setRecordPositionUpdateListener(OnRecordPositionUpdateListener)} or
     * {@link #setRecordPositionUpdateListener(OnRecordPositionUpdateListener, Handler)}.
     * @param markerInFrames marker position expressed in frames
     * @return error code or success, see {@link #SUCCESS}, {@link #ERROR_BAD_VALUE},
     *  {@link #ERROR_INVALID_OPERATION}
     */
    public int setNotificationMarkerPosition(int markerInFrames) {
        if (mState == STATE_UNINITIALIZED) {
            return ERROR_INVALID_OPERATION;
        }
        return native_set_marker_pos(markerInFrames);
    }


    //--------------------------------------------------------------------------
    // (Re)Routing Info
    //--------------------
    /**
     * Defines the interface by which applications can receive notifications of routing
     * changes for the associated {@link AudioRecord}.
     */
    public interface OnRoutingChangedListener {
        /**
         * Called when the routing of an AudioRecord changes from either and explicit or
         * policy rerouting. Use {@link #getRoutedDevice()} to retrieve the newly routed-from
         * device.
         */
        public void onRoutingChanged(AudioRecord audioRecord);
    }

    /**
     * Returns an {@link AudioDeviceInfo} identifying the current routing of this AudioRecord.
     * Note: The query is only valid if the AudioRecord is currently recording. If it is not,
     * <code>getRoutedDevice()</code> will return null.
     */
    public AudioDeviceInfo getRoutedDevice() {
        int deviceId = native_getRoutedDeviceId();
        if (deviceId == 0) {
            return null;
        }
        AudioDeviceInfo[] devices =
                AudioManager.getDevicesStatic(AudioManager.GET_DEVICES_INPUTS);
        for (int i = 0; i < devices.length; i++) {
            if (devices[i].getId() == deviceId) {
                return devices[i];
            }
        }
        return null;
    }

    /**
     * The list of AudioRecord.OnRoutingChangedListener interface added (with
     * {@link AudioRecord#addOnRoutingChangedListener(OnRoutingChangedListener,android.os.Handler)}
     * by an app to receive (re)routing notifications.
     */
    private ArrayMap<OnRoutingChangedListener, NativeRoutingEventHandlerDelegate>
        mRoutingChangeListeners =
            new ArrayMap<OnRoutingChangedListener, NativeRoutingEventHandlerDelegate>();

    /**
     * Adds an {@link OnRoutingChangedListener} to receive notifications of routing changes
     * on this AudioRecord.
     * @param listener The {@link OnRoutingChangedListener} interface to receive notifications
     * of rerouting events.
     * @param handler  Specifies the {@link Handler} object for the thread on which to execute
     * the callback. If <code>null</code>, the {@link Handler} associated with the main
     * {@link Looper} will be used.
     */
    public void addOnRoutingChangedListener(OnRoutingChangedListener listener,
            android.os.Handler handler) {
        if (listener != null && !mRoutingChangeListeners.containsKey(listener)) {
            synchronized (mRoutingChangeListeners) {
                if (mRoutingChangeListeners.size() == 0) {
                    native_enableDeviceCallback();
                }
                mRoutingChangeListeners.put(
                    listener, new NativeRoutingEventHandlerDelegate(this, listener,
                            handler != null ? handler : new Handler(mInitializationLooper)));
            }
        }
    }

    /**
     * Removes an {@link OnRoutingChangedListener} which has been previously added
     * to receive rerouting notifications.
     * @param listener The previously added {@link OnRoutingChangedListener} interface to remove.
     */
    public void removeOnRoutingChangedListener(OnRoutingChangedListener listener) {
        synchronized (mRoutingChangeListeners) {
            if (mRoutingChangeListeners.containsKey(listener)) {
                mRoutingChangeListeners.remove(listener);
                if (mRoutingChangeListeners.size() == 0) {
                    native_disableDeviceCallback();
                }
            }
        }
    }

    /**
     * Helper class to handle the forwarding of native events to the appropriate listener
     * (potentially) handled in a different thread
     */
    private class NativeRoutingEventHandlerDelegate {
        private final Handler mHandler;

        NativeRoutingEventHandlerDelegate(final AudioRecord record,
                                   final OnRoutingChangedListener listener,
                                   Handler handler) {
            // find the looper for our new event handler
            Looper looper;
            if (handler != null) {
                looper = handler.getLooper();
            } else {
                // no given handler, use the looper the AudioRecord was created in
                looper = mInitializationLooper;
            }

            // construct the event handler with this looper
            if (looper != null) {
                // implement the event handler delegate
                mHandler = new Handler(looper) {
                    @Override
                    public void handleMessage(Message msg) {
                        if (record == null) {
                            return;
                        }
                        switch(msg.what) {
                        case AudioSystem.NATIVE_EVENT_ROUTING_CHANGE:
                            if (listener != null) {
                                listener.onRoutingChanged(record);
                            }
                            break;
                        default:
                            loge("Unknown native event type: " + msg.what);
                            break;
                        }
                    }
                };
            } else {
                mHandler = null;
            }
        }

        Handler getHandler() {
            return mHandler;
        }
    }
    /**
     * Sends device list change notification to all listeners.
     */
    private void broadcastRoutingChange() {
        Collection<NativeRoutingEventHandlerDelegate> values;
        synchronized (mRoutingChangeListeners) {
            values = mRoutingChangeListeners.values();
        }
        AudioManager.resetAudioPortGeneration();
        for(NativeRoutingEventHandlerDelegate delegate : values) {
            Handler handler = delegate.getHandler();
            if (handler != null) {
                handler.sendEmptyMessage(AudioSystem.NATIVE_EVENT_ROUTING_CHANGE);
            }
        }
    }

    /**
     * Sets the period at which the listener is called, if set with
     * {@link #setRecordPositionUpdateListener(OnRecordPositionUpdateListener)} or
     * {@link #setRecordPositionUpdateListener(OnRecordPositionUpdateListener, Handler)}.
     * It is possible for notifications to be lost if the period is too small.
     * @param periodInFrames update period expressed in frames
     * @return error code or success, see {@link #SUCCESS}, {@link #ERROR_INVALID_OPERATION}
     */
    public int setPositionNotificationPeriod(int periodInFrames) {
        if (mState == STATE_UNINITIALIZED) {
            return ERROR_INVALID_OPERATION;
        }
        return native_set_pos_update_period(periodInFrames);
    }

    //--------------------------------------------------------------------------
    // Explicit Routing
    //--------------------
    private AudioDeviceInfo mPreferredDevice = null;

    /**
     * Specifies an audio device (via an {@link AudioDeviceInfo} object) to route
     * the input to this AudioRecord.
     * @param deviceInfo The {@link AudioDeviceInfo} specifying the audio source.
     *  If deviceInfo is null, default routing is restored.
     * @return true if successful, false if the specified {@link AudioDeviceInfo} is non-null and
     * does not correspond to a valid audio input device.
     */
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {
        // Do some validation....
        if (deviceInfo != null && !deviceInfo.isSource()) {
            return false;
        }

        int preferredDeviceId = deviceInfo != null ? deviceInfo.getId() : 0;
        boolean status = native_setInputDevice(preferredDeviceId);
        if (status == true) {
            synchronized (this) {
                mPreferredDevice = deviceInfo;
            }
        }
        return status;
    }

    /**
     * Returns the selected input specified by {@link #setPreferredDevice}. Note that this
     * is not guarenteed to correspond to the actual device being used for recording.
     */
    public AudioDeviceInfo getPreferredDevice() {
        synchronized (this) {
            return mPreferredDevice;
        }
    }

    //---------------------------------------------------------
    // Interface definitions
    //--------------------
    /**
     * Interface definition for a callback to be invoked when an AudioRecord has
     * reached a notification marker set by {@link AudioRecord#setNotificationMarkerPosition(int)}
     * or for periodic updates on the progress of the record head, as set by
     * {@link AudioRecord#setPositionNotificationPeriod(int)}.
     */
    public interface OnRecordPositionUpdateListener  {
        /**
         * Called on the listener to notify it that the previously set marker has been reached
         * by the recording head.
         */
        void onMarkerReached(AudioRecord recorder);

        /**
         * Called on the listener to periodically notify it that the record head has reached
         * a multiple of the notification period.
         */
        void onPeriodicNotification(AudioRecord recorder);
    }



    //---------------------------------------------------------
    // Inner classes
    //--------------------

    /**
     * Helper class to handle the forwarding of native events to the appropriate listener
     * (potentially) handled in a different thread
     */
    private class NativeEventHandler extends Handler {

        private final AudioRecord mAudioRecord;

        NativeEventHandler(AudioRecord recorder, Looper looper) {
            super(looper);
            mAudioRecord = recorder;
        }

        @Override
        public void handleMessage(Message msg) {
            OnRecordPositionUpdateListener listener = null;
            synchronized (mPositionListenerLock) {
                listener = mAudioRecord.mPositionListener;
            }

            switch (msg.what) {
            case NATIVE_EVENT_MARKER:
                if (listener != null) {
                    listener.onMarkerReached(mAudioRecord);
                }
                break;
            case NATIVE_EVENT_NEW_POS:
                if (listener != null) {
                    listener.onPeriodicNotification(mAudioRecord);
                }
                break;
            default:
                loge("Unknown native event type: " + msg.what);
                break;
            }
        }
    };


    //---------------------------------------------------------
    // Java methods called from the native side
    //--------------------
    @SuppressWarnings("unused")
    private static void postEventFromNative(Object audiorecord_ref,
            int what, int arg1, int arg2, Object obj) {
        //logd("Event posted from the native side: event="+ what + " args="+ arg1+" "+arg2);
        AudioRecord recorder = (AudioRecord)((WeakReference)audiorecord_ref).get();
        if (recorder == null) {
            return;
        }

        if (what == AudioSystem.NATIVE_EVENT_ROUTING_CHANGE) {
            recorder.broadcastRoutingChange();
            return;
        }

        if (recorder.mEventHandler != null) {
            Message m =
                recorder.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            recorder.mEventHandler.sendMessage(m);
        }

    }


    //---------------------------------------------------------
    // Native methods called from the Java side
    //--------------------

    private native final int native_setup(Object audiorecord_this,
            Object /*AudioAttributes*/ attributes,
            int sampleRate, int channelMask, int channelIndexMask, int audioFormat,
            int buffSizeInBytes, int[] sessionId, String opPackageName);

    // TODO remove: implementation calls directly into implementation of native_release()
    private native final void native_finalize();

    private native final void native_release();

    private native final int native_start(int syncEvent, int sessionId);

    private native final void native_stop();

    private native final int native_read_in_byte_array(byte[] audioData,
            int offsetInBytes, int sizeInBytes, boolean isBlocking);

    private native final int native_read_in_short_array(short[] audioData,
            int offsetInShorts, int sizeInShorts, boolean isBlocking);

    private native final int native_read_in_float_array(float[] audioData,
            int offsetInFloats, int sizeInFloats, boolean isBlocking);

    private native final int native_read_in_direct_buffer(Object jBuffer,
            int sizeInBytes, boolean isBlocking);

    private native final int native_get_buffer_size_in_frames();

    private native final int native_set_marker_pos(int marker);
    private native final int native_get_marker_pos();

    private native final int native_set_pos_update_period(int updatePeriod);
    private native final int native_get_pos_update_period();

    static private native final int native_get_min_buff_size(
            int sampleRateInHz, int channelCount, int audioFormat);

    private native final boolean native_setInputDevice(int deviceId);
    private native final int native_getRoutedDeviceId();
    private native final void native_enableDeviceCallback();
    private native final void native_disableDeviceCallback();

    //---------------------------------------------------------
    // Utility methods
    //------------------

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }
}
