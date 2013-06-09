/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

///////////////////////////////////////////
import android.os.Environment;
import java.io.FileWriter;
import java.io.File;
import android.os.Binder;
import android.os.Process;
import android.os.ServiceManager;
import android.content.pm.IPackageManager;
import android.content.Context;
import java.util.Random;

import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
///////////////////////////////////////////

/**
 * Used to record audio and video. The recording control is based on a
 * simple state machine (see below).
 *
 * <p><img src="{@docRoot}images/mediarecorder_state_diagram.gif" border="0" />
 * </p>
 *
 * <p>A common case of using MediaRecorder to record audio works as follows:
 *
 * <pre>MediaRecorder recorder = new MediaRecorder();
 * recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
 * recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
 * recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
 * recorder.setOutputFile(PATH_NAME);
 * recorder.prepare();
 * recorder.start();   // Recording is now started
 * ...
 * recorder.stop();
 * recorder.reset();   // You can reuse the object by going back to setAudioSource() step
 * recorder.release(); // Now the object cannot be reused
 * </pre>
 *
 * <p>Applications may want to register for informational and error
 * events in order to be informed of some internal update and possible
 * runtime errors during recording. Registration for such events is
 * done by setting the appropriate listeners (via calls
 * (to {@link #setOnInfoListener(OnInfoListener)}setOnInfoListener and/or
 * {@link #setOnErrorListener(OnErrorListener)}setOnErrorListener).
 * In order to receive the respective callback associated with these listeners,
 * applications are required to create MediaRecorder objects on threads with a
 * Looper running (the main UI thread by default already has a Looper running).
 *
 * <p><strong>Note:</strong> Currently, MediaRecorder does not work on the emulator.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about how to use MediaRecorder for recording video, read the
 * <a href="{@docRoot}guide/topics/media/camera.html#capture-video">Camera</a> developer guide.
 * For more information about how to use MediaRecorder for recording sound, read the
 * <a href="{@docRoot}guide/topics/media/audio-capture.html">Audio Capture</a> developer guide.</p>
 * </div>
 */
public class MediaRecorder
{
    static {
        System.loadLibrary("media_jni");
        native_init();
    }
    private final static String TAG = "MediaRecorder";

    // The two fields below are accessed by native methods
    @SuppressWarnings("unused")
    private int mNativeContext;

    @SuppressWarnings("unused")
    private Surface mSurface;

    private String mPath;
    private FileDescriptor mFd;
    private EventHandler mEventHandler;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //BEGIN PRIVACY 
    
    /** default value of privacy path. You have to add the package name at the end to write file in directory of the app itself*/
    private static final String PRIVACY_PATH_DEF = "/data/data/";
    
    /**
     * This variable will be set if user use path to save file. Only if user is not allowed!
     */
    private String pPath = null;
    
    /**
     * Path where Filedescriptor linked to.
     */
    private String pFileDescriptorPath = null;
    
    /**
     * This variable will be set if user use FileDescriptor so save file. Only if user is not allowed!
     */
    private FileDescriptor pFileDescriptor = null;
    
    
    private PrivacyRunner pRunner = null;
    
    
    private boolean deletedFile = false;
    
    
    private static final int STATE_RECORD_AUDIO = 0;
    private static final int STATE_RECORD_BOTH = 1;
    private static final int MODE_RECORD_AUDIO = 2;
    private static final int MODE_RECORD_BOTH = 3;
    private static final int IS_ALLOWED = -1;
    private static final int IS_NOT_ALLOWED = -2;
    private static final int GOT_ERROR = -3;

    private static final int MIC_DATA_ACCESS = 10;
    private static final int BOTH_DATA_ACCESS = 11;

    private static final String PRIVACY_TAG = "PM,MediaRecorder";

    /**
     * {@hide} This context will ever be null, because we dont need it but pass it to the pSetMan!
     */
    private Context context;
    
    private PrivacySettingsManager pSetMan;
    
    private IPackageManager mPm;
    
    private boolean privacyMode = false;
    
    private boolean stoppedStream = false;
    
    private int ACTUAL_STATE = STATE_RECORD_AUDIO;
    

    //END PRIVACY
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Default constructor.
     */
    public MediaRecorder() {

        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //BEGIN PRIVACY
        
        initiate();
        
        //END PRIVACY
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


        /* Native setup requires a weak reference to our object.
         * It's easier to create it here than in C++.
         */
        native_setup(new WeakReference<MediaRecorder>(this));
    }

    /**
     * Sets a Camera to use for recording. Use this function to switch
     * quickly between preview and capture mode without a teardown of
     * the camera object. {@link android.hardware.Camera#unlock()} should be
     * called before this. Must call before prepare().
     *
     * @param c the Camera to use for recording
     */
    public native void setCamera(Camera c);

    /**
     * Sets a Surface to show a preview of recorded media (video). Calls this
     * before prepare() to make sure that the desirable preview display is
     * set. If {@link #setCamera(Camera)} is used and the surface has been
     * already set to the camera, application do not need to call this. If
     * this is called with non-null surface, the preview surface of the camera
     * will be replaced by the new surface. If this method is called with null
     * surface or not called at all, media recorder will not change the preview
     * surface of the camera.
     *
     * @param sv the Surface to use for the preview
     * @see android.hardware.Camera#setPreviewDisplay(android.view.SurfaceHolder)
     */
    public void setPreviewDisplay(Surface sv) {

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    	//BEGIN PRIVACY 
    	ACTUAL_STATE = STATE_RECORD_BOTH;
    	//END PRIVACY
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        mSurface = sv;
    }



    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //BEGIN PRIVACY 
    
    /**
     * PrivacyStop. Should be called within privacyRunner
     */
    private void privacyStop(){
    	try{
    		stop();
    	} catch(Exception e){
    		Log.e(PRIVACY_TAG,"Got exception while trying to call privacyStop()");
    	}
    }
    
    /**
     * This method search automatically the current package path and return it. If we haven't found any path, we return the path to SDcard if we are able to write to it.
     * If we're not able to write to sdCard -> return null
     * @return internal path to package directory or path to SDCard if package not found and we have rights to save files on SDCard. If something went wrong or we couldn't find
     * anything of it -> return null
     */
    private String getPrivacyPath(){
    	final String[] packages = getPackageName();
    	Random value = new Random();
		String current_package = null, data_name = value.nextLong() + ".tmp";
		FileWriter fWriter = null;
		File deleteMe = null;
		
		for(int i=0;i<packages.length;i++){
			try{
				//first check if cache folder exist
				File folder = new File(PRIVACY_PATH_DEF + packages[i] + "/cache/");
				folder.mkdirs();
				fWriter = new FileWriter(PRIVACY_PATH_DEF + packages[i] + "/cache/" + data_name);
	            fWriter.write("test");
	            fWriter.flush();
	            fWriter.close();
	            deleteMe = new File(PRIVACY_PATH_DEF + packages[i] + "/cache/" + data_name);
	            deleteMe.delete();
	            Log.i(PRIVACY_TAG,"found our package: " + packages[i] + " with internal path. File: " + data_name);
	            //all is fine, break now and save our current package name!
	            current_package = packages[i];
	            break;
	    	} catch(Exception e){
	    		//we're not allowed to write in this directory -> this is not our package!
	    	} finally{
	    		fWriter = null;
	            deleteMe = null;
	            System.gc();
	    	}
		}
    	if(current_package != null){
    		Log.i(PRIVACY_TAG,"returned file: " + data_name + " for package: " + current_package + " with internal path. Path: " + PRIVACY_PATH_DEF + current_package + "/cache/" + data_name);
    		return PRIVACY_PATH_DEF + current_package + "/cache/" + data_name;
    	}
    	else{ //last chance, try to write to SD-Card
    		try{
    			String sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        		fWriter = new FileWriter(sdPath + "/" + data_name);
                fWriter.write("test");
                fWriter.flush();
                fWriter.close();
                deleteMe = new File(sdPath + "/" + data_name);
                deleteMe.delete();
                Log.i(PRIVACY_TAG,"Return filePath:  " + sdPath + "/" + data_name + " . It is on SDCard!");
                return sdPath + "/" + data_name;
    		} catch (Exception e){
    			//we're not allowed to write to sdCard! 
    			//return null
    			return null;
    		}
    	}
    }
    
    
    /**
     * This method does exactly what the method getPrivacyPath() does, but it returns an FileDescriptor to path
     * @return FileDescriptor to privacyFile or null if something went wrong
     */
    private FileDescriptor getPrivacyFileDescriptor(){
    	final String[] packages = getPackageName();
    	Random value = new Random();
		String current_package = null, data_name = value.nextLong() + ".tmp";
		FileWriter fWriter = null;
		File deleteMe = null;
		
		for(int i=0;i<packages.length;i++){
			try{
				//first check if cache folder exist
				File folder = new File(PRIVACY_PATH_DEF + packages[i] + "/cache/");
				folder.mkdirs();
				fWriter = new FileWriter(PRIVACY_PATH_DEF + packages[i] + "/cache/" + data_name);
	            fWriter.write("test");
	            fWriter.flush();
	            fWriter.close();
	            deleteMe = new File(PRIVACY_PATH_DEF + packages[i] + "/cache/" + data_name);
	            deleteMe.delete();
	            Log.i(PRIVACY_TAG,"found our package: " + packages[i] + " with internal path. File: " + data_name);
	            //all is fine, break now and save our current package name!
	            current_package = packages[i];
	            break;
	    	} catch(Exception e){
	    		//we're not allowed to write in this directory -> this is not our package!
	    	} finally{
	    		fWriter = null;
	            deleteMe = null;
	            System.gc();
	    	}
		}
    	if(current_package != null){
    		try{
    			FileOutputStream fos = new  FileOutputStream(PRIVACY_PATH_DEF + current_package + "/cache/" + data_name);
    			FileDescriptor fD = fos.getFD();
    			pFileDescriptorPath = PRIVACY_PATH_DEF + current_package + "/cache/" + data_name;
    			Log.i(PRIVACY_TAG,"returned fileDescriptor for package: " + current_package + " with internal path. Path: " + PRIVACY_PATH_DEF + current_package + "/cache/" + data_name);
    			return fD;
    		} catch(Exception e){
    			Log.e(PRIVACY_TAG,"Got exception while creating fileDescriptor -> return null");
    			return null;
    		}
    	}
    	else{ //last chance, try to write to SD-Card
    		try{
    			String sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        		fWriter = new FileWriter(sdPath + "/" + data_name);
                fWriter.write("test");
                fWriter.flush();
                fWriter.close();
                deleteMe = new File(sdPath + "/" + data_name);
                deleteMe.delete();
                FileOutputStream fos = new  FileOutputStream(sdPath + "/" + data_name);
                FileDescriptor fD = fos.getFD();
                pFileDescriptorPath = sdPath + "/" + data_name;
                Log.i(PRIVACY_TAG,"Returned FileDescriptor. Path:  " + sdPath + "/" + data_name + " . It is on SDCard!");
                return fD;
    		} catch (Exception e){
    			//we're not allowed to write to sdCard! 
    			//return null
    			return null;
    		}
    	}
    }
    
    
    
    /**
     * {@hide}
     * @return package names of current process which is using this object or null if something went wrong
     */
    private String[] getPackageName(){
    	try{
    		if(mPm != null){
        		int uid = Process.myUid();
        		String[] package_names = mPm.getPackagesForUid(uid);
        		return package_names;
        	}
    		else{
    			mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
    			int uid = Process.myUid();
        		String[] package_names = mPm.getPackagesForUid(uid);
        		return package_names;
    		}
    	}
    	catch(Exception e){
    		e.printStackTrace();
    		Log.e(PRIVACY_TAG,"something went wrong with getting package name");
    		return null;
    	}
    }
    /**
     * {@hide}
     * This method should be used, because in some devices the uid has more than one package within!
     * @param privacySetting the Mode which has to be tested -> MODE_RECORD_AUDIO, MODE_RECORD_BOTH
     * @return IS_ALLOWED (-1) if all packages allowed, IS_NOT_ALLOWED(-2) if one of these packages not allowed, GOT_ERROR (-3) if something went wrong
     */
    private int checkIfPackagesAllowed(int privacySetting){
    	try{
    		//boolean isAllowed = false;
    		if(pSetMan != null){
    			PrivacySettings pSet = null;
	    		String[] package_names = getPackageName();
	    		int uid = Process.myUid();
	    		if(package_names != null){
	    			switch(privacySetting){
	    				case MODE_RECORD_AUDIO:
	    					
				        	for(int i=0;i < package_names.length; i++){
				        		pSet = pSetMan.getSettings(package_names[i], uid);
				        		if(pSet != null && (pSet.getRecordAudioSetting() != PrivacySettings.REAL)){ //if pSet is null, we allow application to access to mic
				        			return IS_NOT_ALLOWED;
				        		}
				        		pSet = null;
				        	}
	    			    	return IS_ALLOWED;
	    					
	    				case MODE_RECORD_BOTH:
	    					
				        	for(int i=0;i < package_names.length; i++){
				        		pSet = pSetMan.getSettings(package_names[i], uid);
				        		if(pSet != null && ((pSet.getRecordAudioSetting() != PrivacySettings.REAL) || (pSet.getCameraSetting() != PrivacySettings.REAL))){ //if pSet is null, we allow application to access to mic
				        			return IS_NOT_ALLOWED;
				        		}
				        		pSet = null;
				        	}
	    			    	return IS_ALLOWED;
					default: return GOT_ERROR;
	    					
	    			}
	    		}
	    		else{
	    			Log.e(PRIVACY_TAG,"return GOT_ERROR, because package_names are NULL");
	    			return GOT_ERROR;
	    		}
    		}
    		else{
    			Log.e(PRIVACY_TAG,"return GOT_ERROR, because pSetMan is NULL");
    			return GOT_ERROR;
    		}
    	}
    	catch (Exception e){
    		e.printStackTrace();
    		Log.e(PRIVACY_TAG,"Got exception in checkIfPackagesAllowed");
    		return GOT_ERROR;
    	}
    }
    /**
     * {@hide}
     * This method sets up all variables which are needed for privacy mode! It also writes to privacyMode, if everything was successfull or not! 
     * -> privacyMode = true ok! otherwise false!
     * CALL THIS METHOD IN CONSTRUCTOR!
     */
    private void initiate(){
    	try{
    		context = null;
    		pSetMan = new PrivacySettingsManager(context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));
    		mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
       	 	//runner = new PrivacyRunner();
       	 	privacyMode = true;
    	}
    	catch(Exception e){
    		e.printStackTrace();
    		Log.e(PRIVACY_TAG, "Something went wrong with initalize variables");
    		privacyMode = false;
    	}
    }
 
     /**
     * Loghelper method, true = access successful, false = blocked access. 
     * {@hide}
     */
    private void dataAccess(boolean success, int micOrBoth){
	String package_names[] = getPackageName();
	if(success && package_names != null){
		switch(micOrBoth){
			case MIC_DATA_ACCESS:
				for(int i=0;i<package_names.length;i++)
					Log.i(PRIVACY_TAG,"Allowed Package: -" + package_names[i] + "- accessing microphone.");
				break;
			case BOTH_DATA_ACCESS:
				for(int i=0;i<package_names.length;i++)
					Log.i(PRIVACY_TAG,"Allowed Package: -" + package_names[i] + "- accessing microphone and camera.");
				break;
		}
		
	}
	else if(package_names != null){
		switch(micOrBoth){
		case MIC_DATA_ACCESS:
				for(int i=0;i<package_names.length;i++)
					Log.i(PRIVACY_TAG,"Blocked Package: -" + package_names[i] + "- accessing microphone.");
				break;
			case BOTH_DATA_ACCESS:
				for(int i=0;i<package_names.length;i++)
					Log.i(PRIVACY_TAG,"Blocked Package: -" + package_names[i] + "- accessing microphone and camera.");
				break;
		}
	}
    }
    //END PRIVACY
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



    /**
     * Defines the audio source. These constants are used with
     * {@link MediaRecorder#setAudioSource(int)}.
     */
    public final class AudioSource {
      /* Do not change these values without updating their counterparts
       * in system/core/include/system/audio.h!
       */
        private AudioSource() {}

        /** Default audio source **/
        public static final int DEFAULT = 0;

        /** Microphone audio source */
        public static final int MIC = 1;

        /** Voice call uplink (Tx) audio source */
        public static final int VOICE_UPLINK = 2;

        /** Voice call downlink (Rx) audio source */
        public static final int VOICE_DOWNLINK = 3;

        /** Voice call uplink + downlink audio source */
        public static final int VOICE_CALL = 4;

        /** Microphone audio source with same orientation as camera if available, the main
         *  device microphone otherwise */
        public static final int CAMCORDER = 5;

        /** Microphone audio source tuned for voice recognition if available, behaves like
         *  {@link #DEFAULT} otherwise. */
        public static final int VOICE_RECOGNITION = 6;

        /** Microphone audio source tuned for voice communications such as VoIP. It
         *  will for instance take advantage of echo cancellation or automatic gain control
         *  if available. It otherwise behaves like {@link #DEFAULT} if no voice processing
         *  is applied.
         */
        public static final int VOICE_COMMUNICATION = 7;

        /**
         * @hide
         * Audio source for remote submix.
         */
        public static final int REMOTE_SUBMIX_SOURCE = 8;
    }

    /**
     * Defines the video source. These constants are used with
     * {@link MediaRecorder#setVideoSource(int)}.
     */
    public final class VideoSource {
      /* Do not change these values without updating their counterparts
       * in include/media/mediarecorder.h!
       */
        private VideoSource() {}
        public static final int DEFAULT = 0;
        /** Camera video source */
        public static final int CAMERA = 1;
        /** @hide */
        public static final int GRALLOC_BUFFER = 2;
    }

    /**
     * Defines the output format. These constants are used with
     * {@link MediaRecorder#setOutputFormat(int)}.
     */
    public final class OutputFormat {
      /* Do not change these values without updating their counterparts
       * in include/media/mediarecorder.h!
       */
        private OutputFormat() {}
        public static final int DEFAULT = 0;
        /** 3GPP media file format*/
        public static final int THREE_GPP = 1;
        /** MPEG4 media file format*/
        public static final int MPEG_4 = 2;

        /** The following formats are audio only .aac or .amr formats */

        /**
         * AMR NB file format
         * @deprecated  Deprecated in favor of MediaRecorder.OutputFormat.AMR_NB
         */
        public static final int RAW_AMR = 3;

        /** AMR NB file format */
        public static final int AMR_NB = 3;

        /** AMR WB file format */
        public static final int AMR_WB = 4;

        /** @hide AAC ADIF file format */
        public static final int AAC_ADIF = 5;

        /** AAC ADTS file format */
        public static final int AAC_ADTS = 6;

        /** @hide Stream over a socket, limited to a single stream */
        public static final int OUTPUT_FORMAT_RTP_AVP = 7;

        /** @hide H.264/AAC data encapsulated in MPEG2/TS */
        public static final int OUTPUT_FORMAT_MPEG2TS = 8;

        /** @hide QCP file format */
        public static final int QCP = 9;
        /** @hide 3GPP2 media file format*/
        public static final int THREE_GPP2 = 10;
        /** @hide WAVE media file format*/
        public static final int WAVE = 11;
    };

    /**
     * Defines the audio encoding. These constants are used with
     * {@link MediaRecorder#setAudioEncoder(int)}.
     */
    public final class AudioEncoder {
      /* Do not change these values without updating their counterparts
       * in include/media/mediarecorder.h!
       */
        private AudioEncoder() {}
        public static final int DEFAULT = 0;
        /** AMR (Narrowband) audio codec */
        public static final int AMR_NB = 1;
        /** AMR (Wideband) audio codec */
        public static final int AMR_WB = 2;
        /** AAC Low Complexity (AAC-LC) audio codec */
        public static final int AAC = 3;
        /** High Efficiency AAC (HE-AAC) audio codec */
        public static final int HE_AAC = 4;
        /** Enhanced Low Delay AAC (AAC-ELD) audio codec */
        public static final int AAC_ELD = 5;
        /** @hide EVRC audio codec */
        public static final int EVRC = 6;
        /** @hide QCELP audio codec */
        public static final int QCELP =7;
        /** @hide Linear PCM audio codec */
        public static final int LPCM =8;
    }

    /**
     * Defines the video encoding. These constants are used with
     * {@link MediaRecorder#setVideoEncoder(int)}.
     */
    public final class VideoEncoder {
      /* Do not change these values without updating their counterparts
       * in include/media/mediarecorder.h!
       */
        private VideoEncoder() {}
        public static final int DEFAULT = 0;
        public static final int H263 = 1;
        public static final int H264 = 2;
        public static final int MPEG_4_SP = 3;
    }

    /**
     * Sets the audio source to be used for recording. If this method is not
     * called, the output file will not contain an audio track. The source needs
     * to be specified before setting recording-parameters or encoders. Call
     * this only before setOutputFormat().
     *
     * @param audio_source the audio source to use
     * @throws IllegalStateException if it is called after setOutputFormat()
     * @see android.media.MediaRecorder.AudioSource
     */
    public native void setAudioSource(int audio_source)
            throws IllegalStateException;

    /**
     * Gets the maximum value for audio sources.
     * @see android.media.MediaRecorder.AudioSource
     */
    public static final int getAudioSourceMax() {
        // FIXME disable selection of the remote submxi source selection once test code
        //       doesn't rely on it
        return AudioSource.REMOTE_SUBMIX_SOURCE;
        //return AudioSource.VOICE_COMMUNICATION;
    }

    /**
     * Sets the video source to be used for recording. If this method is not
     * called, the output file will not contain an video track. The source needs
     * to be specified before setting recording-parameters or encoders. Call
     * this only before setOutputFormat().
     *
     * @param video_source the video source to use
     * @throws IllegalStateException if it is called after setOutputFormat()
     * @see android.media.MediaRecorder.VideoSource
     */
    public native void setVideoSource(int video_source)
            throws IllegalStateException;

    /**
     * Uses the settings from a CamcorderProfile object for recording. This method should
     * be called after the video AND audio sources are set, and before setOutputFile().
     * If a time lapse CamcorderProfile is used, audio related source or recording
     * parameters are ignored.
     *
     * @param profile the CamcorderProfile to use
     * @see android.media.CamcorderProfile
     */
    public void setProfile(CamcorderProfile profile) {

    	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    	//BEGIN PRIVACY 
    	ACTUAL_STATE = STATE_RECORD_BOTH;
    	//END PRIVACY
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        setOutputFormat(profile.fileFormat);
        setVideoFrameRate(profile.videoFrameRate);
        setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        setVideoEncodingBitRate(profile.videoBitRate);
        setVideoEncoder(profile.videoCodec);
        if (profile.quality >= CamcorderProfile.QUALITY_TIME_LAPSE_LOW &&
             profile.quality <= CamcorderProfile.QUALITY_TIME_LAPSE_QVGA) {
            // Nothing needs to be done. Call to setCaptureRate() enables
            // time lapse video recording.
        } else {
            setAudioEncodingBitRate(profile.audioBitRate);
            setAudioChannels(profile.audioChannels);
            setAudioSamplingRate(profile.audioSampleRate);
            setAudioEncoder(profile.audioCodec);
        }
    }

    /**
     * Set video frame capture rate. This can be used to set a different video frame capture
     * rate than the recorded video's playback rate. This method also sets the recording mode
     * to time lapse. In time lapse video recording, only video is recorded. Audio related
     * parameters are ignored when a time lapse recording session starts, if an application
     * sets them.
     *
     * @param fps Rate at which frames should be captured in frames per second.
     * The fps can go as low as desired. However the fastest fps will be limited by the hardware.
     * For resolutions that can be captured by the video camera, the fastest fps can be computed using
     * {@link android.hardware.Camera.Parameters#getPreviewFpsRange(int[])}. For higher
     * resolutions the fastest fps may be more restrictive.
     * Note that the recorder cannot guarantee that frames will be captured at the
     * given rate due to camera/encoder limitations. However it tries to be as close as
     * possible.
     */
    public void setCaptureRate(double fps) {

    	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    	//BEGIN PRIVACY 
    	ACTUAL_STATE = STATE_RECORD_BOTH;
    	//END PRIVACY
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // Make sure that time lapse is enabled when this method is called.
        setParameter("time-lapse-enable=1");

        double timeBetweenFrameCapture = 1 / fps;
        int timeBetweenFrameCaptureMs = (int) (1000 * timeBetweenFrameCapture);
        setParameter("time-between-time-lapse-frame-capture=" + timeBetweenFrameCaptureMs);
    }

    /**
     * Sets the orientation hint for output video playback.
     * This method should be called before prepare(). This method will not
     * trigger the source video frame to rotate during video recording, but to
     * add a composition matrix containing the rotation angle in the output
     * video if the output format is OutputFormat.THREE_GPP or
     * OutputFormat.MPEG_4 so that a video player can choose the proper
     * orientation for playback. Note that some video players may choose
     * to ignore the compostion matrix in a video during playback.
     *
     * @param degrees the angle to be rotated clockwise in degrees.
     * The supported angles are 0, 90, 180, and 270 degrees.
     * @throws IllegalArgumentException if the angle is not supported.
     *
     */
    public void setOrientationHint(int degrees) {
        if (degrees != 0   &&
            degrees != 90  &&
            degrees != 180 &&
            degrees != 270) {
            throw new IllegalArgumentException("Unsupported angle: " + degrees);
        }
        setParameter("video-param-rotation-angle-degrees=" + degrees);
    }

    /**
     * Set and store the geodata (latitude and longitude) in the output file.
     * This method should be called before prepare(). The geodata is
     * stored in udta box if the output format is OutputFormat.THREE_GPP
     * or OutputFormat.MPEG_4, and is ignored for other output formats.
     * The geodata is stored according to ISO-6709 standard.
     *
     * @param latitude latitude in degrees. Its value must be in the
     * range [-90, 90].
     * @param longitude longitude in degrees. Its value must be in the
     * range [-180, 180].
     *
     * @throws IllegalArgumentException if the given latitude or
     * longitude is out of range.
     *
     */
    public void setLocation(float latitude, float longitude) {
        int latitudex10000  = (int) (latitude * 10000 + 0.5);
        int longitudex10000 = (int) (longitude * 10000 + 0.5);

        if (latitudex10000 > 900000 || latitudex10000 < -900000) {
            String msg = "Latitude: " + latitude + " out of range.";
            throw new IllegalArgumentException(msg);
        }
        if (longitudex10000 > 1800000 || longitudex10000 < -1800000) {
            String msg = "Longitude: " + longitude + " out of range";
            throw new IllegalArgumentException(msg);
        }

        setParameter("param-geotag-latitude=" + latitudex10000);
        setParameter("param-geotag-longitude=" + longitudex10000);
    }

    /**
     * Sets the format of the output file produced during recording. Call this
     * after setAudioSource()/setVideoSource() but before prepare().
     *
     * <p>It is recommended to always use 3GP format when using the H.263
     * video encoder and AMR audio encoder. Using an MPEG-4 container format
     * may confuse some desktop players.</p>
     *
     * @param output_format the output format to use. The output format
     * needs to be specified before setting recording-parameters or encoders.
     * @throws IllegalStateException if it is called after prepare() or before
     * setAudioSource()/setVideoSource().
     * @see android.media.MediaRecorder.OutputFormat
     */
    public native void setOutputFormat(int output_format)
            throws IllegalStateException;

    /**
     * Sets the width and height of the video to be captured.  Must be called
     * after setVideoSource(). Call this after setOutFormat() but before
     * prepare().
     *
     * @param width the width of the video to be captured
     * @param height the height of the video to be captured
     * @throws IllegalStateException if it is called after
     * prepare() or before setOutputFormat()
     */
    public native void setVideoSize(int width, int height)
            throws IllegalStateException;

    /**
     * Sets the frame rate of the video to be captured.  Must be called
     * after setVideoSource(). Call this after setOutFormat() but before
     * prepare().
     *
     * @param rate the number of frames per second of video to capture
     * @throws IllegalStateException if it is called after
     * prepare() or before setOutputFormat().
     *
     * NOTE: On some devices that have auto-frame rate, this sets the
     * maximum frame rate, not a constant frame rate. Actual frame rate
     * will vary according to lighting conditions.
     */
    public native void setVideoFrameRate(int rate) throws IllegalStateException;

    /**
     * Sets the maximum duration (in ms) of the recording session.
     * Call this after setOutFormat() but before prepare().
     * After recording reaches the specified duration, a notification
     * will be sent to the {@link android.media.MediaRecorder.OnInfoListener}
     * with a "what" code of {@link #MEDIA_RECORDER_INFO_MAX_DURATION_REACHED}
     * and recording will be stopped. Stopping happens asynchronously, there
     * is no guarantee that the recorder will have stopped by the time the
     * listener is notified.
     *
     * @param max_duration_ms the maximum duration in ms (if zero or negative, disables the duration limit)
     *
     */
    public native void setMaxDuration(int max_duration_ms) throws IllegalArgumentException;

    /**
     * Sets the maximum filesize (in bytes) of the recording session.
     * Call this after setOutFormat() but before prepare().
     * After recording reaches the specified filesize, a notification
     * will be sent to the {@link android.media.MediaRecorder.OnInfoListener}
     * with a "what" code of {@link #MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED}
     * and recording will be stopped. Stopping happens asynchronously, there
     * is no guarantee that the recorder will have stopped by the time the
     * listener is notified.
     *
     * @param max_filesize_bytes the maximum filesize in bytes (if zero or negative, disables the limit)
     *
     */
    public native void setMaxFileSize(long max_filesize_bytes) throws IllegalArgumentException;

    /**
     * Sets the audio encoder to be used for recording. If this method is not
     * called, the output file will not contain an audio track. Call this after
     * setOutputFormat() but before prepare().
     *
     * @param audio_encoder the audio encoder to use.
     * @throws IllegalStateException if it is called before
     * setOutputFormat() or after prepare().
     * @see android.media.MediaRecorder.AudioEncoder
     */
    public native void setAudioEncoder(int audio_encoder)
            throws IllegalStateException;

    /**
     * Sets the video encoder to be used for recording. If this method is not
     * called, the output file will not contain an video track. Call this after
     * setOutputFormat() and before prepare().
     *
     * @param video_encoder the video encoder to use.
     * @throws IllegalStateException if it is called before
     * setOutputFormat() or after prepare()
     * @see android.media.MediaRecorder.VideoEncoder
     */
    public native void setVideoEncoder(int video_encoder)
            throws IllegalStateException;

    /**
     * Sets the audio sampling rate for recording. Call this method before prepare().
     * Prepare() may perform additional checks on the parameter to make sure whether
     * the specified audio sampling rate is applicable. The sampling rate really depends
     * on the format for the audio recording, as well as the capabilities of the platform.
     * For instance, the sampling rate supported by AAC audio coding standard ranges
     * from 8 to 96 kHz, the sampling rate supported by AMRNB is 8kHz, and the sampling
     * rate supported by AMRWB is 16kHz. Please consult with the related audio coding
     * standard for the supported audio sampling rate.
     *
     * @param samplingRate the sampling rate for audio in samples per second.
     */
    public void setAudioSamplingRate(int samplingRate) {
        if (samplingRate <= 0) {
            throw new IllegalArgumentException("Audio sampling rate is not positive");
        }
        setParameter("audio-param-sampling-rate=" + samplingRate);
    }

    /**
     * Sets the number of audio channels for recording. Call this method before prepare().
     * Prepare() may perform additional checks on the parameter to make sure whether the
     * specified number of audio channels are applicable.
     *
     * @param numChannels the number of audio channels. Usually it is either 1 (mono) or 2
     * (stereo).
     */
    public void setAudioChannels(int numChannels) {
        if (numChannels <= 0) {
            throw new IllegalArgumentException("Number of channels is not positive");
        }
        setParameter("audio-param-number-of-channels=" + numChannels);
    }

    /**
     * Sets the audio encoding bit rate for recording. Call this method before prepare().
     * Prepare() may perform additional checks on the parameter to make sure whether the
     * specified bit rate is applicable, and sometimes the passed bitRate will be clipped
     * internally to ensure the audio recording can proceed smoothly based on the
     * capabilities of the platform.
     *
     * @param bitRate the audio encoding bit rate in bits per second.
     */
    public void setAudioEncodingBitRate(int bitRate) {
        if (bitRate <= 0) {
            throw new IllegalArgumentException("Audio encoding bit rate is not positive");
        }
        setParameter("audio-param-encoding-bitrate=" + bitRate);
    }

    /**
     * Sets the video encoding bit rate for recording. Call this method before prepare().
     * Prepare() may perform additional checks on the parameter to make sure whether the
     * specified bit rate is applicable, and sometimes the passed bitRate will be
     * clipped internally to ensure the video recording can proceed smoothly based on
     * the capabilities of the platform.
     *
     * @param bitRate the video encoding bit rate in bits per second.
     */
    public void setVideoEncodingBitRate(int bitRate) {

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    	//BEGIN PRIVACY 
    	ACTUAL_STATE = STATE_RECORD_BOTH;
    	//END PRIVACY
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        if (bitRate <= 0) {
            throw new IllegalArgumentException("Video encoding bit rate is not positive");
        }
        setParameter("video-param-encoding-bitrate=" + bitRate);
    }

    /**
     * Currently not implemented. It does nothing.
     * @deprecated Time lapse mode video recording using camera still image capture
     * is not desirable, and will not be supported.
     * @hide
     */
    public void setAuxiliaryOutputFile(FileDescriptor fd)
    {
        Log.w(TAG, "setAuxiliaryOutputFile(FileDescriptor) is no longer supported.");
    }

    /**
     * Currently not implemented. It does nothing.
     * @deprecated Time lapse mode video recording using camera still image capture
     * is not desirable, and will not be supported.
     * @hide
     */
    public void setAuxiliaryOutputFile(String path)
    {
        Log.w(TAG, "setAuxiliaryOutputFile(String) is no longer supported.");
    }

    /**
     * Pass in the file descriptor of the file to be written. Call this after
     * setOutputFormat() but before prepare().
     *
     * @param fd an open file descriptor to be written into.
     * @throws IllegalStateException if it is called before
     * setOutputFormat() or after prepare()
     */
    public void setOutputFile(FileDescriptor fd) throws IllegalStateException
    {
        mPath = null;
        mFd = fd;
        deletedFile = false;
    }

    /**
     * Sets the path of the output file to be produced. Call this after
     * setOutputFormat() but before prepare().
     *
     * @param path The pathname to use.
     * @throws IllegalStateException if it is called before
     * setOutputFormat() or after prepare()
     */
    public void setOutputFile(String path) throws IllegalStateException
    {
        mFd = null;
        mPath = path;
        deletedFile = false;
    }

    // native implementation
    private native void _setOutputFile(FileDescriptor fd, long offset, long length)
        throws IllegalStateException, IOException;
    private native void _prepare() throws IllegalStateException, IOException;

    /**
     * Prepares the recorder to begin capturing and encoding data. This method
     * must be called after setting up the desired audio and video sources,
     * encoders, file format, etc., but before start().
     *
     * @throws IllegalStateException if it is called after
     * start() or before setOutputFormat().
     * @throws IOException if prepare fails otherwise.
     */
    public void prepare() throws IllegalStateException, IOException
    {

    	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    	//BEGIN PRIVACY
    	if(!privacyMode){
    		initiate();
    	}
    	deletedFile = false;
		boolean skip = false;
		switch(ACTUAL_STATE){
    		case STATE_RECORD_AUDIO:
				if(checkIfPackagesAllowed(MODE_RECORD_AUDIO) == IS_NOT_ALLOWED /* || checkIfPackagesAllowed(MODE_RECORD_BOTH) == IS_NOT_ALLOWED*/){
					String x[] = getPackageName();
					if(x != null && x.length > 0)
						pSetMan.notification(x[0], 0, PrivacySettings.EMPTY, PrivacySettings.DATA_RECORD_AUDIO, null, null);
					pRunner = new PrivacyRunner();
					//here wo do not need to exchange the path or filedescriptor, because we can interrupt very quick!
					pRunner.setDelay(50); // try very low value
					pRunner.start();
					skip = true;
	//				if(x != null) Log.i(PRIVACY_TAG,"now throw exception in prepare method for package: " + x[0]);
	//				else Log.i(PRIVACY_TAG,"now throw exception in prepare method");
	//				if(ACTUAL_STATE == STATE_RECORD_BOTH){
	//					dataAccess(false, BOTH_DATA_ACCESS);
	//					if(x != null)
	//						pSetMan.notification(x[0], 0, PrivacySettings.EMPTY, PrivacySettings.DATA_CAMERA, null, pSetMan.getSettings(x[0], Process.myUid()));
	//				}
	//				else{
	//					dataAccess(false, MIC_DATA_ACCESS);
	//					if(x != null)
	//						pSetMan.notification(x[0], 0, PrivacySettings.EMPTY, PrivacySettings.DATA_RECORD_AUDIO, null, pSetMan.getSettings(x[0], Process.myUid()));
	//					//now test something, because a lot of applications crashes if we throw illegalstateException. We intercept now when applications wants to record audio!
	//					//skip = true;
	//					//break;
	//				}
	//				throw new IllegalStateException(); //now throw exception to prevent recording 
				}
				break;
    		case STATE_RECORD_BOTH:
				if(checkIfPackagesAllowed(MODE_RECORD_BOTH) == IS_NOT_ALLOWED){
					String x[] = getPackageName();
					if(x != null && x.length > 0)
						pSetMan.notification(x[0], 0, PrivacySettings.EMPTY, PrivacySettings.DATA_CAMERA, null, null);
					if(mPath != null){
						//now overwrite path
						mPath = getPrivacyPath();
					} else if(mFd != null){
						//now overwrite fileDescriptor
						mFd = getPrivacyFileDescriptor();
					} else{
						//no chance to get it, throw exception
						throw new IOException("No valid output file");
					}
					pRunner = new PrivacyRunner();
					//we use default time for video record
					pRunner.start();
					skip = true;
					
				}
				break;
		}
		//END PRIVACY
	    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		String packageName[] = getPackageName();
		if(!skip){
			if (ACTUAL_STATE == STATE_RECORD_BOTH && packageName != null && packageName.length > 0) {
				pSetMan.notification(packageName[0], 0, PrivacySettings.REAL, PrivacySettings.DATA_CAMERA, null, null);
			} else if (packageName != null && packageName.length > 0) {
				pSetMan.notification(packageName[0], 0, PrivacySettings.REAL, PrivacySettings.DATA_RECORD_AUDIO, null, null);
			}
			deletedFile = true;
		}

        if (mPath != null) {
            FileOutputStream fos = new FileOutputStream(mPath);
            try {
                _setOutputFile(fos.getFD(), 0, 0);
            } finally {
                fos.close();
            }
        } else if (mFd != null) {
            _setOutputFile(mFd, 0, 0);
        } else {
            throw new IOException("No valid output file");
        }

        _prepare();
    }

    /**
     * Begins capturing and encoding data to the file specified with
     * setOutputFile(). Call this after prepare().
     *
     * <p>Since API level 13, if applications set a camera via
     * {@link #setCamera(Camera)}, the apps can use the camera after this method
     * call. The apps do not need to lock the camera again. However, if this
     * method fails, the apps should still lock the camera back. The apps should
     * not start another recording session during recording.
     *
     * @throws IllegalStateException if it is called before
     * prepare().
     */
    public native void start() throws IllegalStateException;

    /**
     * Stops recording. Call this after start(). Once recording is stopped,
     * you will have to configure it again as if it has just been constructed.
     * Note that a RuntimeException is intentionally thrown to the
     * application, if no valid audio/video data has been received when stop()
     * is called. This happens if stop() is called immediately after
     * start(). The failure lets the application take action accordingly to
     * clean up the output file (delete the output file, for instance), since
     * the output file is not properly constructed when this happens.
     *
     * @throws IllegalStateException if it is called before start()
     */
    public native void stop() throws IllegalStateException;

    /**
     * Restarts the MediaRecorder to its idle state. After calling
     * this method, you will have to configure it again as if it had just been
     * constructed.
     */
    public void reset() {
        native_reset();
        if(!deletedFile){
        	if(mPath != null){
				File tmp = new File(mPath);
				if(tmp.delete())
					deletedFile = true;
			} else if(mFd != null && pFileDescriptorPath != null){
				File tmp = new File(pFileDescriptorPath);
				if(tmp.delete())
					deletedFile = true;
			} else{
				Log.e(PRIVACY_TAG,"Can't delete temporary File, because all is null?! It could be that we only want to record audio?!");
				deletedFile = false;
			}
        }
        //
        pRunner = null;
        System.gc();
        //
        // make sure none of the listeners get called anymore
        mEventHandler.removeCallbacksAndMessages(null);
    }

    private native void native_reset();

    /**
     * Returns the maximum absolute amplitude that was sampled since the last
     * call to this method. Call this only after the setAudioSource().
     *
     * @return the maximum absolute amplitude measured since the last call, or
     * 0 when called for the first time
     * @throws IllegalStateException if it is called before
     * the audio source has been set.
     */
    public native int getMaxAmplitude() throws IllegalStateException;

    /* Do not change this value without updating its counterpart
     * in include/media/mediarecorder.h or mediaplayer.h!
     */
    /** Unspecified media recorder error.
     * @see android.media.MediaRecorder.OnErrorListener
     */
    public static final int MEDIA_RECORDER_ERROR_UNKNOWN = 1;
    /** Media server died. In this case, the application must release the
     * MediaRecorder object and instantiate a new one.
     * @see android.media.MediaRecorder.OnErrorListener
     */
    public static final int MEDIA_ERROR_SERVER_DIED = 100;

    /**
     * Interface definition for a callback to be invoked when an error
     * occurs while recording.
     */
    public interface OnErrorListener
    {
        /**
         * Called when an error occurs while recording.
         *
         * @param mr the MediaRecorder that encountered the error
         * @param what    the type of error that has occurred:
         * <ul>
         * <li>{@link #MEDIA_RECORDER_ERROR_UNKNOWN}
         * <li>{@link #MEDIA_ERROR_SERVER_DIED}
         * </ul>
         * @param extra   an extra code, specific to the error type
         */
        void onError(MediaRecorder mr, int what, int extra);
    }

    /**
     * Register a callback to be invoked when an error occurs while
     * recording.
     *
     * @param l the callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l)
    {
        mOnErrorListener = l;
    }

    /* Do not change these values without updating their counterparts
     * in include/media/mediarecorder.h!
     */
    /** Unspecified media recorder error.
     * @see android.media.MediaRecorder.OnInfoListener
     */
    public static final int MEDIA_RECORDER_INFO_UNKNOWN              = 1;
    /** A maximum duration had been setup and has now been reached.
     * @see android.media.MediaRecorder.OnInfoListener
     */
    public static final int MEDIA_RECORDER_INFO_MAX_DURATION_REACHED = 800;
    /** A maximum filesize had been setup and has now been reached.
     * @see android.media.MediaRecorder.OnInfoListener
     */
    public static final int MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED = 801;

    /** informational events for individual tracks, for testing purpose.
     * The track informational event usually contains two parts in the ext1
     * arg of the onInfo() callback: bit 31-28 contains the track id; and
     * the rest of the 28 bits contains the informational event defined here.
     * For example, ext1 = (1 << 28 | MEDIA_RECORDER_TRACK_INFO_TYPE) if the
     * track id is 1 for informational event MEDIA_RECORDER_TRACK_INFO_TYPE;
     * while ext1 = (0 << 28 | MEDIA_RECORDER_TRACK_INFO_TYPE) if the track
     * id is 0 for informational event MEDIA_RECORDER_TRACK_INFO_TYPE. The
     * application should extract the track id and the type of informational
     * event from ext1, accordingly.
     *
     * FIXME:
     * Please update the comment for onInfo also when these
     * events are unhidden so that application knows how to extract the track
     * id and the informational event type from onInfo callback.
     *
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_LIST_START        = 1000;
    /** Signal the completion of the track for the recording session.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_COMPLETION_STATUS = 1000;
    /** Indicate the recording progress in time (ms) during recording.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_PROGRESS_IN_TIME  = 1001;
    /** Indicate the track type: 0 for Audio and 1 for Video.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_TYPE              = 1002;
    /** Provide the track duration information.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_DURATION_MS       = 1003;
    /** Provide the max chunk duration in time (ms) for the given track.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_MAX_CHUNK_DUR_MS  = 1004;
    /** Provide the total number of recordd frames.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_ENCODED_FRAMES    = 1005;
    /** Provide the max spacing between neighboring chunks for the given track.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INTER_CHUNK_TIME_MS    = 1006;
    /** Provide the elapsed time measuring from the start of the recording
     * till the first output frame of the given track is received, excluding
     * any intentional start time offset of a recording session for the
     * purpose of eliminating the recording sound in the recorded file.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_INITIAL_DELAY_MS  = 1007;
    /** Provide the start time difference (delay) betweeen this track and
     * the start of the movie.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_START_OFFSET_MS   = 1008;
    /** Provide the total number of data (in kilo-bytes) encoded.
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_DATA_KBYTES       = 1009;
    /**
     * {@hide}
     */
    public static final int MEDIA_RECORDER_TRACK_INFO_LIST_END          = 2000;


    /**
     * Interface definition for a callback to be invoked when an error
     * occurs while recording.
     */
    public interface OnInfoListener
    {
        /**
         * Called when an error occurs while recording.
         *
         * @param mr the MediaRecorder that encountered the error
         * @param what    the type of error that has occurred:
         * <ul>
         * <li>{@link #MEDIA_RECORDER_INFO_UNKNOWN}
         * <li>{@link #MEDIA_RECORDER_INFO_MAX_DURATION_REACHED}
         * <li>{@link #MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED}
         * </ul>
         * @param extra   an extra code, specific to the error type
         */
        void onInfo(MediaRecorder mr, int what, int extra);
    }

    /**
     * Register a callback to be invoked when an informational event occurs while
     * recording.
     *
     * @param listener the callback that will be run
     */
    public void setOnInfoListener(OnInfoListener listener)
    {
        mOnInfoListener = listener;
    }

    private class EventHandler extends Handler
    {
        private MediaRecorder mMediaRecorder;

        public EventHandler(MediaRecorder mr, Looper looper) {
            super(looper);
            mMediaRecorder = mr;
        }

        /* Do not change these values without updating their counterparts
         * in include/media/mediarecorder.h!
         */
        private static final int MEDIA_RECORDER_EVENT_LIST_START = 1;
        private static final int MEDIA_RECORDER_EVENT_ERROR      = 1;
        private static final int MEDIA_RECORDER_EVENT_INFO       = 2;
        private static final int MEDIA_RECORDER_EVENT_LIST_END   = 99;

        /* Events related to individual tracks */
        private static final int MEDIA_RECORDER_TRACK_EVENT_LIST_START = 100;
        private static final int MEDIA_RECORDER_TRACK_EVENT_ERROR      = 100;
        private static final int MEDIA_RECORDER_TRACK_EVENT_INFO       = 101;
        private static final int MEDIA_RECORDER_TRACK_EVENT_LIST_END   = 1000;


        @Override
        public void handleMessage(Message msg) {
            if (mMediaRecorder.mNativeContext == 0) {
                Log.w(TAG, "mediarecorder went away with unhandled events");
                return;
            }
            switch(msg.what) {
            case MEDIA_RECORDER_EVENT_ERROR:
            case MEDIA_RECORDER_TRACK_EVENT_ERROR:
                if (mOnErrorListener != null)
                    mOnErrorListener.onError(mMediaRecorder, msg.arg1, msg.arg2);

                return;

            case MEDIA_RECORDER_EVENT_INFO:
            case MEDIA_RECORDER_TRACK_EVENT_INFO:
                if (mOnInfoListener != null)
                    mOnInfoListener.onInfo(mMediaRecorder, msg.arg1, msg.arg2);

                return;

            default:
                Log.e(TAG, "Unknown message type " + msg.what);
                return;
            }
        }
    }

    /**
     * Called from native code when an interesting event happens.  This method
     * just uses the EventHandler system to post the event back to the main app thread.
     * We use a weak reference to the original MediaRecorder object so that the native
     * code is safe from the object disappearing from underneath it.  (This is
     * the cookie passed to native_setup().)
     */
    private static void postEventFromNative(Object mediarecorder_ref,
                                            int what, int arg1, int arg2, Object obj)
    {
        MediaRecorder mr = (MediaRecorder)((WeakReference)mediarecorder_ref).get();
        if (mr == null) {
            return;
        }

        if (mr.mEventHandler != null) {
            Message m = mr.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            mr.mEventHandler.sendMessage(m);
        }
    }

    /**
     * Releases resources associated with this MediaRecorder object.
     * It is good practice to call this method when you're done
     * using the MediaRecorder. In particular, whenever an Activity
     * of an application is paused (its onPause() method is called),
     * or stopped (its onStop() method is called), this method should be
     * invoked to release the MediaRecorder object, unless the application
     * has a special need to keep the object around. In addition to
     * unnecessary resources (such as memory and instances of codecs)
     * being held, failure to call this method immediately if a
     * MediaRecorder object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and recording
     * failure for other applications if no multiple instances of the
     * same codec are supported on a device. Even if multiple instances
     * of the same codec are supported, some performance degradation
     * may be expected when unnecessary multiple instances are used
     * at the same time.
     */
    public native void release();

    private static native final void native_init();

    private native final void native_setup(Object mediarecorder_this) throws IllegalStateException;

    private native final void native_finalize();

    private native void setParameter(String nameValuePair);

    @Override
    protected void finalize() { 
    	
    	if(!deletedFile){
        	if(mPath != null){
				File tmp = new File(mPath);
				if(tmp.delete())
					deletedFile = true;
			} else if(mFd != null && pFileDescriptorPath != null){
				File tmp = new File(pFileDescriptorPath);
				if(tmp.delete())
					deletedFile = true;
			} else{
				Log.e(PRIVACY_TAG,"Can't delete temporary File, because all is null?! It could be that we only want to record audio?!");
				deletedFile = false;
			}
        }
    	native_finalize(); }
    
    
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//BEGIN PRIVACY
	/**
	* Helper class to interrupt stream.
	* @author CollegeDev
	* {@hide}
	*/
	private class PrivacyRunner extends Thread{
	
		private static final long OFFSET_DELAY = 2500;	
		
		private long delay = OFFSET_DELAY;
		
		public PrivacyRunner(){
		
		}
		
		public void setDelay(long delay){
			this.delay = delay;
		}
		
		public long getDelay(){
			return delay;
		}
		
		@Override
		public void run() {
			try{
				Thread.sleep(delay);
				//now we're going to stop stream
				privacyStop();
				if(mPath != null){
					File tmp = new File(mPath);
					if(tmp.delete())
						deletedFile = true;
				} else if(mFd != null && pFileDescriptorPath != null){
					File tmp = new File(pFileDescriptorPath);
					if(tmp.delete())
						deletedFile = true;
				} else{
					Log.e(PRIVACY_TAG,"Can't delete temporary File, because all is null?! It could be that we only want to record audio?!");
					deletedFile = false;
				}
			}
			catch(Exception e){
				Log.e(PRIVACY_TAG,"Something went wrong while waiting for cancel the stream!");
				e.printStackTrace();
			}
			finally{
				privacyStop();
			}
		}
	
	}
	//END PRIVACY
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
}
