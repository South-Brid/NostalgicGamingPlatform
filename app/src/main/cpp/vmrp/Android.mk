LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := unicorn
LOCAL_SRC_FILES := unicorn/lib/$(TARGET_ARCH_ABI)/libunicorn.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/unicorn/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := vmrp
LOCAL_SRC_FILES := bridge.c fileLib.c memory.c network.c rbtree.c utils.c vmrp.c vmrp_android.c
LOCAL_CFLAGS := -DVMRP -DNETWORK_SUPPORT -O2 -include $(LOCAL_PATH)/android_port.h \
	-Wno-pointer-to-int-cast -Wno-int-to-pointer-cast -Wno-unused-variable -Wno-format \
	-Wno-invalid-source-encoding -Wno-incompatible-pointer-types-discards-qualifiers
LOCAL_STATIC_LIBRARIES := unicorn
LOCAL_LDLIBS := -llog -ljnigraphics -lz -lm
include $(BUILD_SHARED_LIBRARY)
