
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_JAVA_LIBRARIES += \
	android-support-v4 \
	androidx.appcompat_appcompat \
	androidx-constraintlayout_constraintlayout-solver \
	androidx-constraintlayout_constraintlayout\
	android-opt-timezonepicker \
	com.google.android.material_material\
	android.hidl.base-V1.0-java \
	android.hardware.automotive.vehicle-V2.0-java \
	vehicle-hal-support-lib \
# 	vendor.aptiv.hardware.automotive.vehicle-V2.0-java


LOCAL_SRC_FILES := $(call all-java-files-under, src)

#LOCAL_VENDOR_MODULE := true

LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PACKAGE_NAME := AptivUsbOtg
LOCAL_CERTIFICATE := platform
#LOCAL_DEX_PREOPT := false

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/AndroidManifest.xml 
	
	

include $(BUILD_PACKAGE)
