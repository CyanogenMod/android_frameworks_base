#
# Audio Package 12 - K
#
# Include this file in a product makefile to include these audio files
#
#

LOCAL_PATH := frameworks/base/data/sounds

# Simple files that do not require renaming
ALARM_FILES := Argon Carbon Helium Krypton Neon Oxygen Osmium Platinum
NOTIFICATION_FILES := Ariel Ceres Carme Elara Europa Iapetus Io Rhea Salacia Titan Tethys
RINGTONE_FILES := Callisto Dione Ganymede Luna Oberon Phobos Sedna Titania Triton Umbriel
EFFECT_FILES := Effect_Tick KeypressReturn KeypressInvalid KeypressDelete KeypressSpacebar KeypressStandard \
    VideoRecord camera_click camera_focus LowBattery Dock Undock Lock Unlock WirelessChargingStarted

PRODUCT_COPY_FILES += $(foreach fn,$(ALARM_FILES),\
    $(LOCAL_PATH)/alarms/ogg/$(fn).ogg:system/media/audio/alarms/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(NOTIFICATION_FILES),\
    $(LOCAL_PATH)/notifications/ogg/$(fn).ogg:system/media/audio/notifications/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(RINGTONE_FILES),\
    $(LOCAL_PATH)/ringtones/ogg/$(fn).ogg:system/media/audio/ringtones/$(fn).ogg)

PRODUCT_COPY_FILES += $(foreach fn,$(EFFECT_FILES),\
    $(LOCAL_PATH)/effects/ogg/$(fn).ogg:system/media/audio/ui/$(fn).ogg)
