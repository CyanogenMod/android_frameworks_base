# This file contains list of  vendor framework jars
# Make sure this file inclusion is added after
# LOCAL_JAVA_LIBRARIES defined in your module


# Add vendor jars here
VENDOR_FRAMEWORKS_CORE_JARS := \
     org.codeaurora.Performance


#Update the LOCAL_JAVA_LIBRARIES
ifneq ($(strip $(VENDOR_FRAMEWORKS_CORE_JARS)),)
LOCAL_JAVA_LIBRARIES += $(VENDOR_FRAMEWORKS_CORE_JARS)
endif
