#include "./header/fileLib.h"
#include "./header/utils.h"

#include <string.h>
#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <malloc.h>
#include <sys/stat.h>
#include <zlib.h>

// mrc_open需要返回0表示失败
static struct rb_root filef_map = RB_ROOT;
static uint32_t filef_count = 0;

// mrc_findStart需要返回-1表示失败
static struct rb_root dirf_map = RB_ROOT;
static uint32_t dirf_count = 0;

#define QUIET_MAX 4096
static unsigned char quietHandles[QUIET_MAX];

static void setQuiet(uint32_t f, int quiet) {
    if (f < QUIET_MAX) quietHandles[f] = (unsigned char)quiet;
}

static int isQuiet(uint32_t f) {
    return f < QUIET_MAX && quietHandles[f];
}

int32_t my_open(const char *filename, uint32_t mode) {
    int f;
    int new_mode = 0;

    if (mode & MR_FILE_RDONLY) new_mode = O_RDONLY;
    if (mode & MR_FILE_WRONLY) new_mode = O_RDWR;
    if (mode & MR_FILE_RDWR) new_mode = O_RDWR;
    if (mode & MR_FILE_CREATE) new_mode |= O_CREAT;
    if ((mode & MR_FILE_RECREATE) && (mode & (MR_FILE_WRONLY | MR_FILE_RDWR))) new_mode |= O_CREAT | O_TRUNC;

#ifdef _WIN32
    new_mode |= O_RAW;
#endif

    f = open((char *)filename, new_mode, S_IRWXU | S_IRWXG | S_IRWXO);
    if (f == -1) {
        printf("open %s mode=%u -> failed %s", filename, mode, strerror(errno));
        return 0;
    }

    filef_count++;
    uIntMap *obj = malloc(sizeof(uIntMap));
    obj->key = filef_count;
    obj->data = (void *)(intptr_t)f;
    uIntMap_insert(&filef_map, obj);
    setQuiet(filef_count, strstr(filename, ".uc2") != NULL);
    struct stat st;
    long long size = fstat(f, &st) == 0 ? (long long)st.st_size : -1;
    printf("open %s mode=%u -> h%u fd%d size=%lld", filename, mode, filef_count, f, size);
    return filef_count;
}

int32_t my_close(int32_t f) {
    uIntMap *obj = uIntMap_delete(&filef_map, f);
    if (obj == NULL) {
        return MR_FAILED;
    }
    if (f == filef_count) {
        filef_count--;
    }
    int fh = (int)(intptr_t)obj->data;
    free(obj);
    if (!isQuiet(f)) printf("close h%d", f);
    setQuiet(f, 0);
    if (close(fh) != 0) {
        return MR_FAILED;
    }
    return MR_SUCCESS;
}

int32_t my_seek(int32_t f, int32_t pos, int method) {
    uIntMap *obj = uIntMap_search(&filef_map, f);
    if (obj == NULL) {
        printf("seek h%d pos=%d m=%d -> bad handle", f, pos, method);
        return MR_FAILED;
    }
    off_t ret = lseek((int)(intptr_t)obj->data, (off_t)pos, method);
    if (!isQuiet(f)) printf("seek h%d pos=%d m=%d -> %lld", f, pos, method, (long long)ret);
    if (ret == -1) {
        return MR_FAILED;
    }
    return MR_SUCCESS;
}

int32_t my_read(int32_t f, void *p, uint32_t l) {
    uIntMap *obj = uIntMap_search(&filef_map, f);
    if (obj == NULL) {
        printf("my_read(%d, %u): bad handle", f, l);
        return MR_FAILED;
    }
    int32_t readnum = read((int)(intptr_t)obj->data, p, (size_t)l);
    int quiet = isQuiet(f);
    if (readnum == -1) {
        if (!quiet) printf("my_read(%d, %u): -1 %s", f, l, strerror(errno));
        return MR_FAILED;
    }
    if (!quiet) {
        if ((uint32_t)readnum < l) printf("my_read(%d, %u): short %d", f, l, readnum);
        else printf("read h%d len=%u -> %d", f, l, readnum);
    }
    return readnum;
}

int32_t my_write(int32_t f, void *p, uint32_t l) {
    uIntMap *obj = uIntMap_search(&filef_map, f);
    if (obj == NULL) {
        printf("my_write(%d, %u): bad handle", f, l);
        return MR_FAILED;
    }
    int32_t writenum = write((int)(intptr_t)obj->data, p, (size_t)l);
    int quiet = isQuiet(f);
    if (writenum == -1) {
        if (!quiet) printf("my_write(%d, %u): -1 %s", f, l, strerror(errno));
        return MR_FAILED;
    }
    if (!quiet) {
        if ((uint32_t)writenum < l) printf("my_write(%d, %u): short %d", f, l, writenum);
        else printf("write h%d len=%u -> %d", f, l, writenum);
    }
    return writenum;
}

int32_t my_rename(const char *oldname, const char *newname) {
    int ret = rename(oldname, newname);
    printf("rename %s %s -> %d", oldname, newname, ret);
    if (ret != 0) {
        return MR_FAILED;
    }
    return MR_SUCCESS;
}

int32_t my_remove(const char *filename) {
    int ret = remove(filename);
    printf("remove %s -> %d", filename, ret);
    if (ret != 0) {
        return MR_FAILED;
    }
    return MR_SUCCESS;
}

int32_t my_getLen(const char *filename) {
    struct stat s1;
    int ret = stat(filename, &s1);
    if (ret != 0) {
        printf("getLen %s -> -1", filename);
        return -1;
    }
    printf("getLen %s -> %d", filename, (int)s1.st_size);
    return s1.st_size;
}

int32_t my_mkDir(const char *name) {
    int ret;
    if (access(name, F_OK) == 0) {  //检测是否已存在
        goto ok;
    }
#ifndef _WIN32
    ret = mkdir(name, S_IRWXU | S_IRWXG | S_IRWXO);
#else
    ret = mkdir(name);
#endif
    if (ret != 0) {
        printf("mkDir %s -> %d", name, MR_FAILED);
        return MR_FAILED;
    }
ok:
    printf("mkDir %s -> %d", name, MR_SUCCESS);
    return MR_SUCCESS;
}

int32_t my_rmDir(const char *name) {
    int ret = rmdir(name);
    printf("rmDir %s -> %d", name, ret);
    if (ret != 0) {
        return MR_FAILED;
    }
    return MR_SUCCESS;
}

int32_t my_info(const char *filename) {
    struct stat s1;
    int ret = stat(filename, &s1);
    int32_t result = MR_IS_INVALID;

    if (ret == 0) {
        if (s1.st_mode & S_IFDIR) {
            result = MR_IS_DIR;
        } else if (s1.st_mode & S_IFREG) {
            result = MR_IS_FILE;
        }
    }
    printf("info %s -> %d", filename, result);
    return result;
}

int32_t my_opendir(const char *name) {
    DIR *pDir = opendir(name);
    if (pDir != NULL) {
        dirf_count++;
        uIntMap *obj = malloc(sizeof(uIntMap));
        obj->key = dirf_count;
        obj->data = (void *)pDir;
        uIntMap_insert(&dirf_map, obj);
        printf("opendir %s -> %u", name, dirf_count);
        return dirf_count;
    }
    printf("opendir %s -> %d", name, MR_FAILED);
    return MR_FAILED;
}

char *my_readdir(int32_t f) {
    uIntMap *obj = uIntMap_search(&dirf_map, f);
    if (obj == NULL) {
        return NULL;
    }
    struct dirent *pDt = readdir((DIR *)obj->data); // 手册说返回的内存可能是静态分配的，不要尝试free()
    if (pDt != NULL) {
        return pDt->d_name;
    }
    return NULL;
}

int32_t my_closedir(int32_t f) {
    uIntMap *obj = uIntMap_delete(&dirf_map, f);
    if (obj == NULL) {
        return MR_FAILED;
    }
    if (f == dirf_count) {
        dirf_count--;
    }
    DIR *pDir = (DIR *)obj->data;
    free(obj);
    if (closedir(pDir) != 0) {
        return MR_FAILED;
    }
    return MR_SUCCESS;
}

void writeFile(const char *filename, void *data, uint32 length) {
    int fh = my_open(filename, MR_FILE_CREATE | MR_FILE_RDWR);
    int32_t wLen = 0;
    char *ptr = (char *)data;
    do {
        ptr += wLen;
        wLen = my_write(fh, ptr, length < 1000 ? length : 1000);
        if (wLen == MR_FAILED) {
            break;
        }
        length -= wLen;
    } while (length > 0);
    my_close(fh);
}

char *readFile(const char *filename) {
    int32_t len = my_getLen(filename);
    char *p = malloc(len);
    if (p == NULL) {
        return NULL;
    }
    int32_t fh = my_open(filename, MR_FILE_RDONLY);
    if (fh) {
        int32_t rLen = 0;
        char *ptr = p;
        do {
            ptr += rLen;
            rLen = my_read(fh, ptr, len);
            if (rLen == MR_FAILED) {
                free(p);
                return NULL;
            }
            len -= rLen;
        } while (len > 0);
        my_close(fh);
        return p;
    }
    return NULL;
}
