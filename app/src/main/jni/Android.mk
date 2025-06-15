LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := cafemode_native
LOCAL_SRC_FILES := native-lib.cpp

# Add the path to the system audio effect headers
LOCAL_C_INCLUDES += system/media/audio_effects/include

# Standard libraries
LOCAL_LDLIBS := -llog -landroid

include $(BUILD_SHARED_LIBRARY)