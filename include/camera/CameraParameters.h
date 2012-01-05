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

#ifndef ANDROID_HARDWARE_CAMERA_PARAMETERS_H
#define ANDROID_HARDWARE_CAMERA_PARAMETERS_H

#include <utils/KeyedVector.h>
#include <utils/String8.h>

namespace android {

struct Size {
    int width;
    int height;

    Size() {
        width = 0;
        height = 0;
    }

    Size(int w, int h) {
        width = w;
        height = h;
    }
};

class CameraParameters
{
public:
    CameraParameters();
    CameraParameters(const String8 &params) { unflatten(params); }
    ~CameraParameters();

    String8 flatten() const;
    void unflatten(const String8 &params);

    void set(const char *key, const char *value);
    void set(const char *key, int value);
    void setFloat(const char *key, float value);
    const char *get(const char *key) const;
    int getInt(const char *key) const;
    float getFloat(const char *key) const;

    void remove(const char *key);

    void setPreviewSize(int width, int height);
    void getPreviewSize(int *width, int *height) const;
    void getSupportedPreviewSizes(Vector<Size> &sizes) const;

    // Set the dimensions in pixels to the given width and height
    // for video frames. The given width and height must be one
    // of the supported dimensions returned from
    // getSupportedVideoSizes(). Must not be called if
    // getSupportedVideoSizes() returns an empty Vector of Size.
    void setVideoSize(int width, int height);

    // Retrieve the current dimensions (width and height)
    // in pixels for video frames, which must be one of the
    // supported dimensions returned from getSupportedVideoSizes().
    // Must not be called if getSupportedVideoSizes() returns an
    // empty Vector of Size.
    void getVideoSize(int *width, int *height) const;

    void setPreviewFrameRate(int fps);
    int getPreviewFrameRate() const;
    void getPreviewFpsRange(int *min_fps, int *max_fps) const;
    void setPreviewFrameRateMode(const char *mode);
    const char *getPreviewFrameRateMode() const;
    void setPreviewFormat(const char *format);
    const char *getPreviewFormat() const;
    void setPictureSize(int width, int height);
    void getPictureSize(int *width, int *height) const;
    void getSupportedPictureSizes(Vector<Size> &sizes) const;
    void setPictureFormat(const char *format);
    const char *getPictureFormat() const;
    void setTouchIndexAec(int x, int y);
    void getTouchIndexAec(int *x, int *y) const;
    void setTouchIndexAf(int x, int y);
    void getTouchIndexAf(int *x, int *y) const;

    void dump() const;
    status_t dump(int fd, const Vector<String16>& args) const;

    // Parameter keys to communicate between camera application and driver.
    // The access (read/write, read only, or write only) is viewed from the
    // perspective of applications, not driver.

    // Preview frame size in pixels (width x height).
    // Example value: "480x320". Read/Write.
    static const char KEY_PREVIEW_SIZE[];
    // Supported preview frame sizes in pixels.
    // Example value: "800x600,480x320". Read only.
    static const char KEY_SUPPORTED_PREVIEW_SIZES[];
    // The current minimum and maximum preview fps. This controls the rate of
    // preview frames received (CAMERA_MSG_PREVIEW_FRAME). The minimum and
    // maximum fps must be one of the elements from
    // KEY_SUPPORTED_PREVIEW_FPS_RANGE parameter.
    // Example value: "10500,26623"
    static const char KEY_PREVIEW_FPS_RANGE[];
    // The supported preview fps (frame-per-second) ranges. Each range contains
    // a minimum fps and maximum fps. If minimum fps equals to maximum fps, the
    // camera outputs frames in fixed frame rate. If not, the camera outputs
    // frames in auto frame rate. The actual frame rate fluctuates between the
    // minimum and the maximum. The list has at least one element. The list is
    // sorted from small to large (first by maximum fps and then minimum fps).
    // Example value: "(10500,26623),(15000,26623),(30000,30000)"
    static const char KEY_SUPPORTED_PREVIEW_FPS_RANGE[];
    // The image format for preview frames. See CAMERA_MSG_PREVIEW_FRAME in
    // frameworks/base/include/camera/Camera.h.
    // Example value: "yuv420sp" or PIXEL_FORMAT_XXX constants. Read/write.
    static const char KEY_PREVIEW_FORMAT[];
    // Supported image formats for preview frames.
    // Example value: "yuv420sp,yuv422i-yuyv". Read only.
    static const char KEY_SUPPORTED_PREVIEW_FORMATS[];
    // Number of preview frames per second. This is the target frame rate. The
    // actual frame rate depends on the driver.
    // Example value: "15". Read/write.
    static const char KEY_PREVIEW_FRAME_RATE[];
    // Supported number of preview frames per second.
    // Example value: "24,15,10". Read.
    static const char KEY_SUPPORTED_PREVIEW_FRAME_RATES[];
    // The mode of preview frame rate.
    // Example value: "frame-rate-auto, frame-rate-fixed".
    static const char KEY_PREVIEW_FRAME_RATE_MODE[];
    static const char KEY_SUPPORTED_PREVIEW_FRAME_RATE_MODES[];
    static const char KEY_PREVIEW_FRAME_RATE_AUTO_MODE[];
    static const char KEY_PREVIEW_FRAME_RATE_FIXED_MODE[];
    // The dimensions for captured pictures in pixels (width x height).
    // Example value: "1024x768". Read/write.
    static const char KEY_PICTURE_SIZE[];
    // Supported dimensions for captured pictures in pixels.
    // Example value: "2048x1536,1024x768". Read only.
    static const char KEY_SUPPORTED_PICTURE_SIZES[];
    // The image format for captured pictures. See CAMERA_MSG_COMPRESSED_IMAGE
    // in frameworks/base/include/camera/Camera.h.
    // Example value: "jpeg" or PIXEL_FORMAT_XXX constants. Read/write.
    static const char KEY_PICTURE_FORMAT[];
    // Supported image formats for captured pictures.
    // Example value: "jpeg,rgb565". Read only.
    static const char KEY_SUPPORTED_PICTURE_FORMATS[];
    // The width (in pixels) of EXIF thumbnail in Jpeg picture.
    // Example value: "512". Read/write.
    static const char KEY_JPEG_THUMBNAIL_WIDTH[];
    // The height (in pixels) of EXIF thumbnail in Jpeg picture.
    // Example value: "384". Read/write.
    static const char KEY_JPEG_THUMBNAIL_HEIGHT[];
    static const char KEY_SUPPORTED_THUMBNAIL_SIZES[];
    // Supported EXIF thumbnail sizes (width x height). 0x0 means not thumbnail
    // in EXIF.
    // Example value: "512x384,320x240,0x0". Read only.
    static const char KEY_SUPPORTED_JPEG_THUMBNAIL_SIZES[];
    // The quality of the EXIF thumbnail in Jpeg picture. The range is 1 to 100,
    // with 100 being the best.
    // Example value: "90". Read/write.
    static const char KEY_JPEG_THUMBNAIL_QUALITY[];
    // Jpeg quality of captured picture. The range is 1 to 100, with 100 being
    // the best.
    // Example value: "90". Read/write.
    static const char KEY_JPEG_QUALITY[];
    // The rotation angle in degrees relative to the orientation of the camera.
    // This affects the pictures returned from CAMERA_MSG_COMPRESSED_IMAGE. The
    // camera driver may set orientation in the EXIF header without rotating the
    // picture. Or the driver may rotate the picture and the EXIF thumbnail. If
    // the Jpeg picture is rotated, the orientation in the EXIF header will be
    // missing or 1 (row #0 is top and column #0 is left side).
    //
    // Note that the JPEG pictures of front-facing cameras are not mirrored
    // as in preview display.
    //
    // For example, suppose the natural orientation of the device is portrait.
    // The device is rotated 270 degrees clockwise, so the device orientation is
    // 270. Suppose a back-facing camera sensor is mounted in landscape and the
    // top side of the camera sensor is aligned with the right edge of the
    // display in natural orientation. So the camera orientation is 90. The
    // rotation should be set to 0 (270 + 90).
    //
    // Example value: "0" or "90" or "180" or "270". Write only.
    static const char KEY_ROTATION[];
    // GPS latitude coordinate. GPSLatitude and GPSLatitudeRef will be stored in
    // JPEG EXIF header.
    // Example value: "25.032146" or "-33.462809". Write only.
    static const char KEY_GPS_LATITUDE[];
    // GPS longitude coordinate. GPSLongitude and GPSLongitudeRef will be stored
    // in JPEG EXIF header.
    // Example value: "121.564448" or "-70.660286". Write only.
    static const char KEY_GPS_LONGITUDE[];
    // GPS altitude. GPSAltitude and GPSAltitudeRef will be stored in JPEG EXIF
    // header.
    // Example value: "21.0" or "-5". Write only.
    static const char KEY_GPS_ALTITUDE[];
    static const char KEY_GPS_LATITUDE_REF[];
    static const char KEY_GPS_LONGITUDE_REF[];
    static const char KEY_GPS_ALTITUDE_REF[];
    static const char KEY_GPS_STATUS[];
    static const char KEY_EXIF_DATETIME[];

    static const char KEY_AUTO_EXPOSURE[];
    static const char KEY_SUPPORTED_AUTO_EXPOSURE[];
    static const char KEY_ISO_MODE[];
    static const char KEY_SUPPORTED_ISO_MODES[];
    static const char KEY_LENSSHADE[] ;
    static const char KEY_SUPPORTED_LENSSHADE_MODES[] ;
    static const char KEY_SHARPNESS[];
    static const char KEY_MAX_SHARPNESS[];
    static const char KEY_CONTRAST[];
    static const char KEY_MAX_CONTRAST[];
    static const char KEY_SATURATION[];
    static const char KEY_MAX_SATURATION[];

    // Values for auto exposure settings.
    static const char AUTO_EXPOSURE_FRAME_AVG[];
    static const char AUTO_EXPOSURE_CENTER_WEIGHTED[];
    static const char AUTO_EXPOSURE_SPOT_METERING[];


    // GPS timestamp (UTC in seconds since January 1, 1970). This should be
    // stored in JPEG EXIF header.
    // Example value: "1251192757". Write only.
    static const char KEY_GPS_TIMESTAMP[];
    // GPS Processing Method
    // Example value: "GPS" or "NETWORK". Write only.
    static const char KEY_GPS_PROCESSING_METHOD[];
    // Current white balance setting.
    // Example value: "auto" or WHITE_BALANCE_XXX constants. Read/write.
    static const char KEY_WHITE_BALANCE[];
    // Supported white balance settings.
    // Example value: "auto,incandescent,daylight". Read only.
    static const char KEY_SUPPORTED_WHITE_BALANCE[];
    // Current color effect setting.
    // Example value: "none" or EFFECT_XXX constants. Read/write.
    static const char KEY_EFFECT[];
    // Supported color effect settings.
    // Example value: "none,mono,sepia". Read only.
    static const char KEY_SUPPORTED_EFFECTS[];
    //Touch Af/AEC settings.
    static const char KEY_TOUCH_AF_AEC[];
    static const char KEY_SUPPORTED_TOUCH_AF_AEC[];
    //Touch Index for AEC.
    static const char KEY_TOUCH_INDEX_AEC[];
    //Touch Index for AF.
    static const char KEY_TOUCH_INDEX_AF[];
    // Current antibanding setting.
    // Example value: "auto" or ANTIBANDING_XXX constants. Read/write.
    static const char KEY_ANTIBANDING[];
    // Supported antibanding settings.
    // Example value: "auto,50hz,60hz,off". Read only.
    static const char KEY_SUPPORTED_ANTIBANDING[];
    // Current scene mode.
    // Example value: "auto" or SCENE_MODE_XXX constants. Read/write.
    static const char KEY_SCENE_MODE[];
    // Supported scene mode settings.
    // Example value: "auto,night,fireworks". Read only.
    static const char KEY_SUPPORTED_SCENE_MODES[];
    // Current flash mode.
    // Example value: "auto" or FLASH_MODE_XXX constants. Read/write.
    static const char KEY_FLASH_MODE[];
    // Supported flash modes.
    // Example value: "auto,on,off". Read only.
    static const char KEY_SUPPORTED_FLASH_MODES[];
    // Current focus mode. This will not be empty. Applications should call
    // CameraHardwareInterface.autoFocus to start the focus if focus mode is
    // FOCUS_MODE_AUTO or FOCUS_MODE_MACRO.
    // Example value: "auto" or FOCUS_MODE_XXX constants. Read/write.
    static const char KEY_FOCUS_MODE[];
    // Supported focus modes.
    // Example value: "auto,macro,fixed". Read only.
    static const char KEY_SUPPORTED_FOCUS_MODES[];
    // Focal length in millimeter.
    // Example value: "4.31". Read only.
    static const char KEY_FOCAL_LENGTH[];
    // Horizontal angle of view in degrees.
    // Example value: "54.8". Read only.
    static const char KEY_HORIZONTAL_VIEW_ANGLE[];
    // Vertical angle of view in degrees.
    // Example value: "42.5". Read only.
    static const char KEY_VERTICAL_VIEW_ANGLE[];
    // Exposure compensation index. 0 means exposure is not adjusted.
    // Example value: "0" or "5". Read/write.
    static const char KEY_EXPOSURE_COMPENSATION[];
    // The maximum exposure compensation index (>=0).
    // Example value: "6". Read only.
    static const char KEY_MAX_EXPOSURE_COMPENSATION[];
    // The minimum exposure compensation index (<=0).
    // Example value: "-6". Read only.
    static const char KEY_MIN_EXPOSURE_COMPENSATION[];
    // The exposure compensation step. Exposure compensation index multiply by
    // step eqals to EV. Ex: if exposure compensation index is 6 and step is
    // 0.3333, EV is -2.
    // Example value: "0.333333333" or "0.5". Read only.
    static const char KEY_EXPOSURE_COMPENSATION_STEP[];
    // Current zoom value.
    // Example value: "0" or "6". Read/write.
    static const char KEY_ZOOM[];
    // Maximum zoom value.
    // Example value: "6". Read only.
    static const char KEY_MAX_ZOOM[];
    // The zoom ratios of all zoom values. The zoom ratio is in 1/100
    // increments. Ex: a zoom of 3.2x is returned as 320. The number of list
    // elements is KEY_MAX_ZOOM + 1. The first element is always 100. The last
    // element is the zoom ratio of zoom value KEY_MAX_ZOOM.
    // Example value: "100,150,200,250,300,350,400". Read only.
    static const char KEY_ZOOM_RATIOS[];
    // Whether zoom is supported. Zoom is supported if the value is "true". Zoom
    // is not supported if the value is not "true" or the key does not exist.
    // Example value: "true". Read only.
    static const char KEY_ZOOM_SUPPORTED[];
    // Whether if smooth zoom is supported. Smooth zoom is supported if the
    // value is "true". It is not supported if the value is not "true" or the
    // key does not exist.
    // See CAMERA_CMD_START_SMOOTH_ZOOM, CAMERA_CMD_STOP_SMOOTH_ZOOM, and
    // CAMERA_MSG_ZOOM in frameworks/base/include/camera/Camera.h.
    // Example value: "true". Read only.
    static const char KEY_SMOOTH_ZOOM_SUPPORTED[];

    // The distances (in meters) from the camera to where an object appears to
    // be in focus. The object is sharpest at the optimal focus distance. The
    // depth of field is the far focus distance minus near focus distance.
    //
    // Focus distances may change after starting auto focus, canceling auto
    // focus, or starting the preview. Applications can read this anytime to get
    // the latest focus distances. If the focus mode is FOCUS_MODE_CONTINUOUS,
    // focus distances may change from time to time.
    //
    // This is intended to estimate the distance between the camera and the
    // subject. After autofocus, the subject distance may be within near and far
    // focus distance. However, the precision depends on the camera hardware,
    // autofocus algorithm, the focus area, and the scene. The error can be
    // large and it should be only used as a reference.
    //
    // Far focus distance > optimal focus distance > near focus distance. If
    // the far focus distance is infinity, the value should be "Infinity" (case
    // sensitive). The format is three float values separated by commas. The
    // first is near focus distance. The second is optimal focus distance. The
    // third is far focus distance.
    // Example value: "0.95,1.9,Infinity" or "0.049,0.05,0.051". Read only.
    static const char KEY_FOCUS_DISTANCES[];

    // The current dimensions in pixels (width x height) for video frames.
    // The width and height must be one of the supported sizes retrieved
    // via KEY_SUPPORTED_VIDEO_SIZES.
    // Example value: "1280x720". Read/write.
    static const char KEY_VIDEO_SIZE[];

    // The image format for video frames. See CAMERA_MSG_VIDEO_FRAME in
    // frameworks/base/include/camera/Camera.h.
    // Example value: "yuv420sp" or PIXEL_FORMAT_XXX constants. Read only.
    static const char KEY_VIDEO_FRAME_FORMAT[];

    // Value for KEY_ZOOM_SUPPORTED or KEY_SMOOTH_ZOOM_SUPPORTED.
    static const char TRUE[];

    // Value for KEY_FOCUS_DISTANCES.
    static const char FOCUS_DISTANCE_INFINITY[];

    //Continuous AF.
    static const char KEY_CAF[];
    static const char KEY_CONTINUOUS_AF[];
    static const char KEY_SUPPORTED_CAF[];
    static const char KEY_SUPPORTED_CONTINUOUS_AF[];

    //Face Detection
    static const char KEY_FACE_DETECTION[];
    static const char KEY_SUPPORTED_FACE_DETECTION[];

    //Continuous AF.
    static const char KEY_SELECTABLE_ZONE_AF[];
    static const char KEY_SUPPORTED_SELECTABLE_ZONE_AF[];

    static const char KEY_SHUTTER_SOUND[];
    static const char KEY_BRIGHTNESS_MODE[];

    static const char KEY_HISTOGRAM[] ;
    static const char KEY_SUPPORTED_HISTOGRAM_MODES[] ;
    // Values for HISTOGRAM
    static const char HISTOGRAM_ENABLE[] ;
    static const char HISTOGRAM_DISABLE[] ;

    static const char KEY_SKIN_TONE_ENHANCEMENT[] ;
    static const char KEY_SUPPORTED_SKIN_TONE_ENHANCEMENT_MODES[] ;
    // Values for SKIN TONE ENHANCEMENT
    static const char SKIN_TONE_ENHANCEMENT_ENABLE[] ;
    static const char SKIN_TONE_ENHANCEMENT_DISABLE[] ;

    // Values for auto scene detection settings.
    static const char SCENE_DETECT_OFF[];
    static const char SCENE_DETECT_ON[];

    // Values for auto exposure settings.
    static const char SELECTABLE_ZONE_AF_AUTO[];
    static const char SELECTABLE_ZONE_AF_SPOT_METERING[];
    static const char SELECTABLE_ZONE_AF_CENTER_WEIGHTED[];
    static const char SELECTABLE_ZONE_AF_FRAME_AVERAGE[];

    // Values for Face Detection settings.
    static const char FACE_DETECTION_OFF[];
    static const char FACE_DETECTION_ON[];

 
    // Current auto scene detection mode.
    // Example value: "off" or SCENE_DETECT_XXX constants. Read/write.
    static const char KEY_SCENE_DETECT[];
    // Supported auto scene detection settings.
    // Example value: "off,backlight,snow/cloudy". Read only.
    static const char KEY_SUPPORTED_SCENE_DETECT[];
    // Values for white balance settings.
    static const char WHITE_BALANCE_AUTO[];
    static const char WHITE_BALANCE_INCANDESCENT[];
    static const char WHITE_BALANCE_FLUORESCENT[];
    static const char WHITE_BALANCE_WARM_FLUORESCENT[];
    static const char WHITE_BALANCE_DAYLIGHT[];
    static const char WHITE_BALANCE_CLOUDY_DAYLIGHT[];
    static const char WHITE_BALANCE_TWILIGHT[];
    static const char WHITE_BALANCE_SHADE[];

    // Values for effect settings.
    static const char EFFECT_NONE[];
    static const char EFFECT_MONO[];
    static const char EFFECT_NEGATIVE[];
    static const char EFFECT_SOLARIZE[];
    static const char EFFECT_SEPIA[];
    static const char EFFECT_POSTERIZE[];
    static const char EFFECT_WHITEBOARD[];
    static const char EFFECT_BLACKBOARD[];
    static const char EFFECT_AQUA[];

    // Values for Touch AF/AEC
    static const char TOUCH_AF_AEC_OFF[] ;
    static const char TOUCH_AF_AEC_ON[] ;

    // Values for antibanding settings.
    static const char ANTIBANDING_AUTO[];
    static const char ANTIBANDING_50HZ[];
    static const char ANTIBANDING_60HZ[];
    static const char ANTIBANDING_OFF[];

    // Values for flash mode settings.
    // Flash will not be fired.
    static const char FLASH_MODE_OFF[];
    // Flash will be fired automatically when required. The flash may be fired
    // during preview, auto-focus, or snapshot depending on the driver.
    static const char FLASH_MODE_AUTO[];
    // Flash will always be fired during snapshot. The flash may also be
    // fired during preview or auto-focus depending on the driver.
    static const char FLASH_MODE_ON[];
    // Flash will be fired in red-eye reduction mode.
    static const char FLASH_MODE_RED_EYE[];
    // Constant emission of light during preview, auto-focus and snapshot.
    // This can also be used for video recording.
    static const char FLASH_MODE_TORCH[];

    // Values for scene mode settings.
    static const char SCENE_MODE_AUTO[];
    static const char SCENE_MODE_ACTION[];
    static const char SCENE_MODE_PORTRAIT[];
    static const char SCENE_MODE_LANDSCAPE[];
    static const char SCENE_MODE_NIGHT[];
    static const char SCENE_MODE_NIGHT_PORTRAIT[];
    static const char SCENE_MODE_THEATRE[];
    static const char SCENE_MODE_BEACH[];
    static const char SCENE_MODE_SNOW[];
    static const char SCENE_MODE_SUNSET[];
    static const char SCENE_MODE_STEADYPHOTO[];
    static const char SCENE_MODE_FIREWORKS[];
    static const char SCENE_MODE_SPORTS[];
    static const char SCENE_MODE_PARTY[];
    static const char SCENE_MODE_CANDLELIGHT[];
    // Applications are looking for a barcode. Camera driver will be optimized
    // for barcode reading.
    static const char SCENE_MODE_BARCODE[];
    static const char SCENE_MODE_BACKLIGHT[];
    static const char SCENE_MODE_FLOWERS[];
    static const char SCENE_MODE_AR[];

    // Formats for setPreviewFormat and setPictureFormat.
    static const char PIXEL_FORMAT_YUV422SP[];
    static const char PIXEL_FORMAT_YUV420SP[]; // NV21
    static const char PIXEL_FORMAT_YUV420P[];
    static const char PIXEL_FORMAT_YUV422I[]; // YUY2
    static const char PIXEL_FORMAT_RGB565[];
    static const char PIXEL_FORMAT_JPEG[];
    static const char PIXEL_FORMAT_RAW[];
    static const char PIXEL_FORMAT_YUV420SP_ADRENO[]; // ADRENO

    // Values for focus mode settings.
    // Auto-focus mode. Applications should call
    // CameraHardwareInterface.autoFocus to start the focus in this mode.
    static const char FOCUS_MODE_AUTO[];
    // Focus is set at infinity. Applications should not call
    // CameraHardwareInterface.autoFocus in this mode.
    static const char FOCUS_MODE_INFINITY[];
    static const char FOCUS_MODE_NORMAL[];
    // Macro (close-up) focus mode. Applications should call
    // CameraHardwareInterface.autoFocus to start the focus in this mode.
    static const char FOCUS_MODE_MACRO[];
    // Focus is fixed. The camera is always in this mode if the focus is not
    // adjustable. If the camera has auto-focus, this mode can fix the
    // focus, which is usually at hyperfocal distance. Applications should
    // not call CameraHardwareInterface.autoFocus in this mode.
    static const char FOCUS_MODE_FIXED[];
    // Extended depth of field (EDOF). Focusing is done digitally and
    // continuously. Applications should not call
    // CameraHardwareInterface.autoFocus in this mode.
    static const char FOCUS_MODE_EDOF[];
    // Continuous auto focus mode intended for video recording. The camera
    // continuously tries to focus. This is ideal for shooting video.
    // Applications still can call CameraHardwareInterface.takePicture in this
    // mode but the subject may not be in focus. Auto focus starts when the
    // parameter is set. Applications should not call
    // CameraHardwareInterface.autoFocus in this mode. To stop continuous focus,
    // applications should change the focus mode to other modes.
    static const char FOCUS_MODE_CONTINUOUS_VIDEO[];
    static const char FOCUS_MODE_CONTINUOUS_CAMERA[];

    // Values for Continuous AF
    static const char CAF_OFF[] ;
    static const char CAF_ON[] ;
    // Proprietaries from CodeAurora use these...
    static const char CONTINUOUS_AF_OFF[] ;
    static const char CONTINUOUS_AF_ON[] ;

    static const char ISO_AUTO[];
    static const char ISO_HJR[] ;
    static const char ISO_100[];
    static const char ISO_200[] ;
    static const char ISO_400[];
    static const char ISO_800[];
    static const char ISO_1600[];
    // Values for Lens Shading
    static const char LENSSHADE_ENABLE[] ;
    static const char LENSSHADE_DISABLE[] ;

    static const char KEY_LUMA_ADAPTION[];
    static const char FOCUS_MODE_MANUAL[];
    static const char KEY_MANUAL_FOCUS[];
    static const char KEY_VT_MODE[];

    enum {
        CAMERA_ORIENTATION_UNKNOWN = 0,
        CAMERA_ORIENTATION_PORTRAIT = 1,
        CAMERA_ORIENTATION_LANDSCAPE = 2,
    };
    int getOrientation() const;
    void setOrientation(int orientation);
    void setPreviewFpsRange(int minFPS,int maxFPS);

private:
    DefaultKeyedVector<String8,String8>    mMap;
};

}; // namespace android

#endif
