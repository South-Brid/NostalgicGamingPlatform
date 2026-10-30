#include <jni.h>
#include <pthread.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <android/bitmap.h>

#include "header/bridge.h"
#include "header/memory.h"
#include "header/vmrp.h"

#define MR_DIALOG_EVENT 6
#define MSG_EVENT 0
#define MSG_PAUSE 1
#define MSG_RESUME 2
#define MSG_EDIT_DONE 3
#define QUEUE_SIZE 256

typedef struct {
    int32_t kind, code, p1, p2;
    char *text;
} Msg;

static JavaVM *jvm;
static jclass nativeClass;
static jmethodID midFrame, midExit, midPlaySound, midStopSound, midVibrate;
static jmethodID midDialogCreate, midDialogRelease, midDialogRefresh, midEditCreate, midEditRelease;
static jobject screenBitmap;

static pthread_mutex_t queueLock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t queueCond;
static Msg queue[QUEUE_SIZE];
static int queueHead, queueTail;
static int64_t timerDeadline;
static int paused;
static volatile int running;
static char *startName;
static char *editText;

static int64_t nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static JNIEnv *getEnv(void) {
    JNIEnv *env = NULL;
    if ((*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        (*jvm)->AttachCurrentThread(jvm, &env, NULL);
    }
    return env;
}

static jbyteArray toBytes(JNIEnv *env, const void *data, uint32_t len) {
    if (data == NULL) return NULL;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)len);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)len, (const jbyte *)data);
    return arr;
}

static jbyteArray ucs2Bytes(JNIEnv *env, const char *s) {
    return s ? toBytes(env, s, (uint32_t)wstrlen((char *)s)) : NULL;
}

static jbyteArray utf8Bytes(JNIEnv *env, const char *s) {
    return s ? toBytes(env, s, (uint32_t)strlen(s)) : NULL;
}

static void unref(JNIEnv *env, jobject a, jobject b) {
    if (a != NULL) (*env)->DeleteLocalRef(env, a);
    if (b != NULL) (*env)->DeleteLocalRef(env, b);
}

static void post(int32_t kind, int32_t code, int32_t p1, int32_t p2, char *text) {
    pthread_mutex_lock(&queueLock);
    int next = (queueTail + 1) % QUEUE_SIZE;
    if (next != queueHead) {
        queue[queueTail] = (Msg){kind, code, p1, p2, text};
        queueTail = next;
        pthread_cond_signal(&queueCond);
    } else {
        free(text);
    }
    pthread_mutex_unlock(&queueLock);
}

static void callExit(int code) {
    running = 0;
    JNIEnv *env = getEnv();
    (*env)->CallStaticVoidMethod(env, nativeClass, midExit, code);
}

void vmrp_android_fatal(int code, const char *file, int line) {
    __android_log_print(ANDROID_LOG_ERROR, "vmrp", "fatal exit(%d) at %s:%d", code, file, line);
    callExit(code == 0 ? 0 : 2);
    pthread_exit(NULL);
}

void guiDrawBitmap(uint16_t *bmp, int32_t x, int32_t y, int32_t w, int32_t h) {
    JNIEnv *env = getEnv();
    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env, screenBitmap, &info) < 0 ||
        AndroidBitmap_lockPixels(env, screenBitmap, &pixels) < 0) {
        return;
    }
    int32_t x0 = x < 0 ? 0 : x, y0 = y < 0 ? 0 : y;
    int32_t x1 = x + w > SCREEN_WIDTH ? SCREEN_WIDTH : x + w;
    int32_t y1 = y + h > SCREEN_HEIGHT ? SCREEN_HEIGHT : y + h;
    if (x1 > (int32_t)info.width) x1 = (int32_t)info.width;
    if (y1 > (int32_t)info.height) y1 = (int32_t)info.height;
    for (int32_t row = y0; row < y1; row++) {
        memcpy((uint8_t *)pixels + row * info.stride + x0 * 2, bmp + row * SCREEN_WIDTH + x0, (size_t)(x1 - x0) * 2);
    }
    AndroidBitmap_unlockPixels(env, screenBitmap);
    (*env)->CallStaticVoidMethod(env, nativeClass, midFrame);
}

int32_t timerStart(uint16_t t) {
    pthread_mutex_lock(&queueLock);
    timerDeadline = nowMs() + t;
    pthread_cond_signal(&queueCond);
    pthread_mutex_unlock(&queueLock);
    return MR_SUCCESS;
}

int32_t timerStop() {
    pthread_mutex_lock(&queueLock);
    timerDeadline = 0;
    pthread_mutex_unlock(&queueLock);
    return MR_SUCCESS;
}

int32_t editCreate(const char *title, const char *text, int32_t type, int32_t max_size) {
    JNIEnv *env = getEnv();
    jbyteArray t = utf8Bytes(env, title), s = utf8Bytes(env, text);
    int32_t ret = (*env)->CallStaticIntMethod(env, nativeClass, midEditCreate, t, s, type, max_size);
    unref(env, t, s);
    return ret;
}

int32 editRelease(int32 edit) {
    if (editText != NULL) {
        my_freeExt(editText);
        editText = NULL;
    }
    JNIEnv *env = getEnv();
    (*env)->CallStaticVoidMethod(env, nativeClass, midEditRelease, edit);
    return MR_SUCCESS;
}

char *editGetText(int32 edit) {
    return editText;
}

void plat_exit(void) {
    callExit(0);
}

int32_t plat_playSound(int32_t type, const void *data, uint32_t dataLen, int32_t loop) {
    JNIEnv *env = getEnv();
    jbyteArray d = toBytes(env, data, dataLen);
    int32_t ret = (*env)->CallStaticIntMethod(env, nativeClass, midPlaySound, type, d, loop);
    unref(env, d, NULL);
    return ret;
}

int32_t plat_stopSound(int32_t type) {
    JNIEnv *env = getEnv();
    return (*env)->CallStaticIntMethod(env, nativeClass, midStopSound, type);
}

int32_t plat_startShake(int32_t ms) {
    JNIEnv *env = getEnv();
    (*env)->CallStaticVoidMethod(env, nativeClass, midVibrate, ms);
    return MR_SUCCESS;
}

int32_t plat_dialogCreate(const char *title, const char *text, int32_t type, int32_t isText) {
    JNIEnv *env = getEnv();
    jbyteArray t = ucs2Bytes(env, title), s = ucs2Bytes(env, text);
    int32_t ret = (*env)->CallStaticIntMethod(env, nativeClass, midDialogCreate, t, s, type, isText);
    unref(env, t, s);
    return ret;
}

int32_t plat_dialogRelease(int32_t dialog) {
    JNIEnv *env = getEnv();
    (*env)->CallStaticVoidMethod(env, nativeClass, midDialogRelease, dialog);
    return MR_SUCCESS;
}

int32_t plat_dialogRefresh(int32_t dialog, const char *title, const char *text, int32_t type) {
    JNIEnv *env = getEnv();
    jbyteArray t = ucs2Bytes(env, title), s = ucs2Bytes(env, text);
    (*env)->CallStaticVoidMethod(env, nativeClass, midDialogRefresh, dialog, t, s, type);
    unref(env, t, s);
    return MR_SUCCESS;
}

static void dispatch(Msg *m) {
    switch (m->kind) {
        case MSG_EVENT:
            event(m->code, m->p1, m->p2);
            break;
        case MSG_PAUSE:
            pauseVmrp();
            break;
        case MSG_RESUME:
            resumeVmrp();
            break;
        case MSG_EDIT_DONE:
            if (editText != NULL) my_freeExt(editText);
            editText = NULL;
            if (m->text != NULL) {
                size_t len = strlen(m->text);
                editText = my_mallocExt((uint32)len + 1);
                if (editText != NULL) memcpy(editText, m->text, len + 1);
                free(m->text);
            }
            event(MR_DIALOG_EVENT, m->code, 0);
            break;
    }
}

static void *emuThread(void *arg) {
    getEnv();
    if (startVmrp(startName) != MR_SUCCESS) {
        callExit(1);
        return NULL;
    }
    while (running) {
        pthread_mutex_lock(&queueLock);
        for (;;) {
            if (!running || queueHead != queueTail) break;
            if (timerDeadline && !paused) {
                int64_t wait = timerDeadline - nowMs();
                if (wait <= 0) break;
                struct timespec ts;
                clock_gettime(CLOCK_MONOTONIC, &ts);
                ts.tv_sec += wait / 1000;
                ts.tv_nsec += (wait % 1000) * 1000000;
                if (ts.tv_nsec >= 1000000000) {
                    ts.tv_sec++;
                    ts.tv_nsec -= 1000000000;
                }
                pthread_cond_timedwait(&queueCond, &queueLock, &ts);
            } else {
                pthread_cond_wait(&queueCond, &queueLock);
            }
        }
        int fire = running && !paused && timerDeadline && nowMs() >= timerDeadline;
        int has = !fire && queueHead != queueTail;
        Msg m;
        if (fire) {
            timerDeadline = 0;
        } else if (has) {
            m = queue[queueHead];
            queueHead = (queueHead + 1) % QUEUE_SIZE;
            if (m.kind == MSG_PAUSE) paused = 1;
            if (m.kind == MSG_RESUME) paused = 0;
        }
        pthread_mutex_unlock(&queueLock);
        if (fire) {
            timer();
        } else if (has) {
            dispatch(&m);
        }
    }
    return NULL;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_ru_playsoftware_j2meloader_mrp_MrpNative_nativeStart(JNIEnv *env, jclass clazz, jstring root, jstring mrp,
                                                           jobject bitmap) {
    if (running) return JNI_FALSE;
    nativeClass = (*env)->NewGlobalRef(env, clazz);
    midFrame = (*env)->GetStaticMethodID(env, clazz, "onFrame", "()V");
    midExit = (*env)->GetStaticMethodID(env, clazz, "onExit", "(I)V");
    midPlaySound = (*env)->GetStaticMethodID(env, clazz, "playSound", "(I[BI)I");
    midStopSound = (*env)->GetStaticMethodID(env, clazz, "stopSound", "(I)I");
    midVibrate = (*env)->GetStaticMethodID(env, clazz, "vibrate", "(I)V");
    midDialogCreate = (*env)->GetStaticMethodID(env, clazz, "dialogCreate", "([B[BII)I");
    midDialogRelease = (*env)->GetStaticMethodID(env, clazz, "dialogRelease", "(I)V");
    midDialogRefresh = (*env)->GetStaticMethodID(env, clazz, "dialogRefresh", "(I[B[BI)V");
    midEditCreate = (*env)->GetStaticMethodID(env, clazz, "editCreate", "([B[BII)I");
    midEditRelease = (*env)->GetStaticMethodID(env, clazz, "editRelease", "(I)V");
    screenBitmap = (*env)->NewGlobalRef(env, bitmap);

    const char *rootStr = (*env)->GetStringUTFChars(env, root, NULL);
    int ok = chdir(rootStr) == 0;
    (*env)->ReleaseStringUTFChars(env, root, rootStr);
    if (!ok) return JNI_FALSE;
    const char *mrpStr = (*env)->GetStringUTFChars(env, mrp, NULL);
    startName = strdup(mrpStr);
    (*env)->ReleaseStringUTFChars(env, mrp, mrpStr);

    pthread_condattr_t attr;
    pthread_condattr_init(&attr);
    pthread_condattr_setclock(&attr, CLOCK_MONOTONIC);
    pthread_cond_init(&queueCond, &attr);
    pthread_condattr_destroy(&attr);

    running = 1;
    pthread_t thread;
    pthread_attr_t threadAttr;
    pthread_attr_init(&threadAttr);
    pthread_attr_setstacksize(&threadAttr, 8 * 1024 * 1024);
    int err = pthread_create(&thread, &threadAttr, emuThread, NULL);
    pthread_attr_destroy(&threadAttr);
    if (err != 0) {
        running = 0;
        return JNI_FALSE;
    }
    pthread_detach(thread);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_ru_playsoftware_j2meloader_mrp_MrpNative_nativeEvent(JNIEnv *env, jclass clazz, jint code, jint p1, jint p2) {
    post(MSG_EVENT, code, p1, p2, NULL);
}

JNIEXPORT void JNICALL
Java_ru_playsoftware_j2meloader_mrp_MrpNative_nativePause(JNIEnv *env, jclass clazz) {
    post(MSG_PAUSE, 0, 0, 0, NULL);
}

JNIEXPORT void JNICALL
Java_ru_playsoftware_j2meloader_mrp_MrpNative_nativeResume(JNIEnv *env, jclass clazz) {
    post(MSG_RESUME, 0, 0, 0, NULL);
}

JNIEXPORT void JNICALL
Java_ru_playsoftware_j2meloader_mrp_MrpNative_nativeEditDone(JNIEnv *env, jclass clazz, jbyteArray utf8) {
    char *text = NULL;
    if (utf8 != NULL) {
        jsize len = (*env)->GetArrayLength(env, utf8);
        text = malloc((size_t)len + 1);
        (*env)->GetByteArrayRegion(env, utf8, 0, len, (jbyte *)text);
        text[len] = '\0';
    }
    post(MSG_EDIT_DONE, text != NULL ? 0 : 1, 0, 0, text);
}
