#ifndef VMRP_ANDROID_PORT_H
#define VMRP_ANDROID_PORT_H

#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>

#define printf(...) vmrp_log(__VA_ARGS__)
#define puts(s) vmrp_log_str(s)
#define exit(code) vmrp_android_fatal(code, __FILE__, __LINE__)

void vmrp_android_fatal(int code, const char *file, int line) __attribute__((noreturn));
void vmrp_log(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
void vmrp_log_str(const char *s);

#endif
