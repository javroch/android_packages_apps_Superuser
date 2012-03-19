LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES := libarity android-support-v4
LOCAL_PACKAGE_NAME := Superuser
LOCAL_SRC_FILES := $(call all-java-files-under,src)

# We're using a modified Superuser source, I don't want it updated via the Mar.. err Play Store anymore
# LOCAL_CERTIFICATE := superuser

include $(BUILD_PACKAGE)

##

