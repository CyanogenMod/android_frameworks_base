LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CFLAGS += -DHAVE_CONFIG_H -DKHTML_NO_EXCEPTIONS -DGKWQ_NO_JAVA
LOCAL_CFLAGS += -DNO_SUPPORT_JS_BINDING -DQT_NO_WHEELEVENT -DKHTML_NO_XBL
LOCAL_CFLAGS += -U__APPLE__

ifeq ($(TARGET_ARCH), arm)
	LOCAL_CFLAGS += -DPACKED="__attribute__ ((packed))"
else
	LOCAL_CFLAGS += -DPACKED=""
endif

ifeq ($(WITH_JIT),true)
	LOCAL_CFLAGS += -DWITH_JIT
endif

ifneq ($(USE_CUSTOM_RUNTIME_HEAP_MAX),)
  LOCAL_CFLAGS += -DCUSTOM_RUNTIME_HEAP_MAX=$(USE_CUSTOM_RUNTIME_HEAP_MAX)
endif

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

LOCAL_SRC_FILES:= \
	ActivityManager.cpp \
	AndroidRuntime.cpp \
	CursorWindow.cpp \
	Time.cpp \
	com_google_android_gles_jni_EGLImpl.cpp \
	com_google_android_gles_jni_GLImpl.cpp.arm \
	android_app_NativeActivity.cpp \
	android_opengl_GLES10.cpp \
	android_opengl_GLES10Ext.cpp \
	android_opengl_GLES11.cpp \
	android_opengl_GLES11Ext.cpp \
	android_opengl_GLES20.cpp \
	android_database_CursorWindow.cpp \
	android_database_SQLiteCompiledSql.cpp \
	android_database_SQLiteDebug.cpp \
	android_database_SQLiteDatabase.cpp \
	android_database_SQLiteProgram.cpp \
	android_database_SQLiteQuery.cpp \
	android_database_SQLiteStatement.cpp \
	android_emoji_EmojiFactory.cpp \
	android_view_Display.cpp \
	android_view_Surface.cpp \
	android_view_ViewRoot.cpp \
	android_view_InputChannel.cpp \
	android_view_InputQueue.cpp \
	android_view_KeyEvent.cpp \
	android_view_MotionEvent.cpp \
	android_text_AndroidCharacter.cpp \
	android_text_AndroidBidi.cpp \
	android_text_KeyCharacterMap.cpp \
	android_os_Debug.cpp \
	android_os_FileUtils.cpp \
	android_os_MemoryFile.cpp \
	android_os_MessageQueue.cpp \
	android_os_ParcelFileDescriptor.cpp \
	android_os_Power.cpp \
	android_os_StatFs.cpp \
	android_os_SystemClock.cpp \
	android_os_SystemProperties.cpp \
	android_os_UEventObserver.cpp \
	android_net_LocalSocketImpl.cpp \
	android_net_NetUtils.cpp \
	android_net_TrafficStats.cpp \
	android_net_wifi_Wifi.cpp \
	android_nio_utils.cpp \
	android_nfc_NdefMessage.cpp \
	android_nfc_NdefRecord.cpp \
	android_pim_EventRecurrence.cpp \
	android_text_format_Time.cpp \
	android_security_Md5MessageDigest.cpp \
	android_util_AssetManager.cpp \
	android_util_Binder.cpp \
	android_util_EventLog.cpp \
	android_util_Log.cpp \
	android_util_FloatMath.cpp \
	android_util_Process.cpp \
	android_util_StringBlock.cpp \
	android_util_XmlBlock.cpp \
	android_util_PackageRedirectionMap.cpp \
	android/graphics/AutoDecodeCancel.cpp \
	android/graphics/Bitmap.cpp \
	android/graphics/BitmapFactory.cpp \
	android/graphics/Camera.cpp \
	android/graphics/Canvas.cpp \
	android/graphics/ColorFilter.cpp \
	android/graphics/DrawFilter.cpp \
	android/graphics/CreateJavaOutputStreamAdaptor.cpp \
	android/graphics/Graphics.cpp \
	android/graphics/Interpolator.cpp \
	android/graphics/LayerRasterizer.cpp \
	android/graphics/MaskFilter.cpp \
	android/graphics/Matrix.cpp \
	android/graphics/Movie.cpp \
	android/graphics/NinePatch.cpp \
	android/graphics/NinePatchImpl.cpp \
	android/graphics/Paint.cpp \
	android/graphics/Path.cpp \
	android/graphics/PathMeasure.cpp \
	android/graphics/PathEffect.cpp \
	android_graphics_PixelFormat.cpp \
	android/graphics/Picture.cpp \
	android/graphics/PorterDuff.cpp \
	android/graphics/BitmapRegionDecoder.cpp \
	android/graphics/Rasterizer.cpp \
	android/graphics/Region.cpp \
	android/graphics/Shader.cpp \
	android/graphics/Typeface.cpp \
	android/graphics/Utils.cpp \
	android/graphics/Xfermode.cpp \
	android/graphics/YuvToJpegEncoder.cpp \
	android_media_AudioRecord.cpp \
	android_media_AudioSystem.cpp \
	android_media_AudioTrack.cpp \
	android_media_JetPlayer.cpp \
	android_media_ToneGenerator.cpp \
	android_hardware_Camera.cpp \
	android_hardware_SensorManager.cpp \
	android_debug_JNITest.cpp \
	android_util_FileObserver.cpp \
	android/opengl/poly_clip.cpp.arm \
	android/opengl/util.cpp.arm \
	android_bluetooth_HeadsetBase.cpp \
	android_bluetooth_common.cpp \
	android_bluetooth_BluetoothAudioGateway.cpp \
	android_bluetooth_BluetoothSocket.cpp \
	android_bluetooth_ScoSocket.cpp \
	android_server_BluetoothService.cpp \
	android_server_BluetoothEventLoop.cpp \
	android_server_BluetoothA2dpService.cpp \
	android_server_BluetoothHidService.cpp \
	android_server_Watchdog.cpp \
	android_message_digest_sha1.cpp \
	android_ddm_DdmHandleNativeHeap.cpp \
	com_android_internal_os_ZygoteInit.cpp \
	com_android_internal_graphics_NativeUtils.cpp \
	android_backup_BackupDataInput.cpp \
	android_backup_BackupDataOutput.cpp \
	android_backup_FileBackupHelperBase.cpp \
	android_backup_BackupHelperDispatcher.cpp \
	android_content_res_ObbScanner.cpp \
    android_content_res_Configuration.cpp

ifeq ($(BOARD_HAVE_FM_RADIO),true)
ifeq ($(BOARD_WLAN_DEVICE),bcm4329)
	LOCAL_SRC_FILES += android_hardware_fm_bcm4325.cpp
endif
ifeq ($(BOARD_WLAN_DEVICE),wl1251)
	LOCAL_SRC_FILES += android_hardware_fm_wl1271.cpp
endif
ifeq ($(BOARD_WLAN_DEVICE),wl1271)
	LOCAL_SRC_FILES += android_hardware_fm_wl1271.cpp
endif
endif

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	$(LOCAL_PATH)/android/graphics \
	$(call include-path-for, bluedroid) \
	$(call include-path-for, libhardware)/hardware \
	$(call include-path-for, libhardware_legacy)/hardware_legacy \
	$(LOCAL_PATH)/../../include/ui \
	$(LOCAL_PATH)/../../include/utils \
	external/skia/include/core \
	external/skia/include/effects \
	external/skia/include/images \
	external/skia/src/ports \
	external/skia/include/utils \
	external/sqlite/dist \
	external/sqlite/android \
	external/expat/lib \
	external/openssl/include \
	external/tremor/Tremor \
	external/icu4c/i18n \
	external/icu4c/common \
	external/jpeg \
	frameworks/opt/emoji

LOCAL_SHARED_LIBRARIES := \
	libexpat \
	libnativehelper \
	libcutils \
	libutils \
	libbinder \
	libnetutils \
	libui \
	libgui \
	libsurfaceflinger_client \
	libcamera_client \
	libskiagl \
	libskia \
	libsqlite \
	libdvm \
	libEGL \
	libGLESv1_CM \
	libGLESv2 \
	libETC1 \
	libhardware \
	libhardware_legacy \
	libsonivox \
	libcrypto \
	libssl \
	libicuuc \
	libicui18n \
	libmedia \
	libwpa_client \
	libjpeg \
	libnfc_ndef

ifeq ($(BOARD_HAVE_BLUETOOTH),true)
LOCAL_C_INCLUDES += \
	external/dbus \
	system/bluetooth/bluez-clean-headers
LOCAL_CFLAGS += -DHAVE_BLUETOOTH
LOCAL_SHARED_LIBRARIES += libbluedroid libdbus
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += \
	libdl
  # we need to access the private Bionic header
  # <bionic_tls.h> in com_google_android_gles_jni_GLImpl.cpp
  LOCAL_CFLAGS += -I$(LOCAL_PATH)/../../../../bionic/libc/private
endif

LOCAL_LDLIBS += -lpthread -ldl

ifeq ($(TARGET_SIMULATOR),true)
ifeq ($(TARGET_OS)-$(TARGET_ARCH),linux-x86)
LOCAL_LDLIBS += -lrt
endif
endif

ifeq ($(WITH_MALLOC_LEAK_CHECK),true)
	LOCAL_CFLAGS += -DMALLOC_LEAK_CHECK
endif

ifneq ($(TARGET_RECOVERY_PRE_COMMAND),)
	LOCAL_CFLAGS += -DRECOVERY_PRE_COMMAND='$(TARGET_RECOVERY_PRE_COMMAND)'
endif

LOCAL_MODULE:= libandroid_runtime

ifneq ($(BOARD_MOBILEDATA_INTERFACE_NAME),)
        LOCAL_CFLAGS += -DMOBILE_IFACE_NAME='$(BOARD_MOBILEDATA_INTERFACE_NAME)'
endif

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
