ACTUAL_LOCAL_PATH := $(call my-dir)

# this var will hold all the test apk module names later. 
FrameworkServicesTests_all_apks :=

# We have to include the subdir makefiles first
# so that FrameworkServicesTests_all_apks will be populated correctly.
include $(call all-makefiles-under,$(ACTUAL_LOCAL_PATH))

LOCAL_PATH := $(ACTUAL_LOCAL_PATH)

include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    easymocklib \
    guava \
    mockito-target

LOCAL_JAVA_LIBRARIES := android.test.runner services

LOCAL_PACKAGE_NAME := FrameworksServicesTests

LOCAL_CERTIFICATE := platform

# intermediate dir to include all the test apks as raw resource 
FrameworkServicesTests_intermediates := $(call intermediates-dir-for,APPS,$(LOCAL_PACKAGE_NAME))/test_apks/res
LOCAL_RESOURCE_DIR := $(FrameworkServicesTests_intermediates) $(LOCAL_PATH)/res

include $(BUILD_PACKAGE)

# Rules to copy all the test apks to the intermediate raw resource directory 
FrameworkServicesTests_all_apks_res := $(addprefix $(FrameworkServicesTests_intermediates)/raw/, \
    $(foreach a, $(FrameworkServicesTests_all_apks), $(patsubst FrameworkServicesTests_%,%,$(a))))

$(FrameworkServicesTests_all_apks_res): $(FrameworkServicesTests_intermediates)/raw/%: $(call intermediates-dir-for,APPS,FrameworkServicesTests_%)/package.apk | $(ACP)
	$(call copy-file-to-new-target)

# Use R_file_stamp as dependency because we want the test apks in place before the R.java is generated.
$(R_file_stamp) : $(FrameworkServicesTests_all_apks_res)

FrameworkServicesTests_all_apks :=
FrameworkServicesTests_intermediates :=
FrameworkServicesTests_all_apks_res :=

