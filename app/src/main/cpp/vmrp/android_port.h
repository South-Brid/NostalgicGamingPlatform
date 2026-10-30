#ifndef VMRP_ANDROID_PORT_H
#define VMRP_ANDROID_PORT_H

#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>

#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "vmrp", __VA_ARGS__)
#define puts(s) __android_log_write(ANDROID_LOG_DEBUG, "vmrp", s)
#define exit(code) vmrp_android_fatal(code, __FILE__, __LINE__)

void vmrp_android_fatal(int code, const char *file, int line) __attribute__((noreturn));

#endif
