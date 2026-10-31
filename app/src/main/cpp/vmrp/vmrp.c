#include "./header/vmrp.h"

#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "./header/bridge.h"
#include "./header/fileLib.h"
#include "./header/memory.h"
#include "./header/utils.h"
#include "./header/debug.h"

#ifdef __EMSCRIPTEN__
#include <emscripten.h>
#endif

static uint8_t *mrpMem;  // 模拟器的全部内存
static uc_engine *uc = NULL;

// 返回的内存禁止free
#ifdef __EMSCRIPTEN__
EMSCRIPTEN_KEEPALIVE
#endif
void *getMrpMemPtr(uint32_t addr) {
    return mrpMem + (addr - START_ADDRESS);
}

#ifdef __EMSCRIPTEN__
EMSCRIPTEN_KEEPALIVE
#endif
uint32_t toMrpMemAddr(void *ptr) {
    return ((uint8_t *)ptr - mrpMem) + START_ADDRESS;
}

#ifdef DEBUG
static void hook_block(uc_engine *uc, uint64_t address, uint32_t size, void *user_data) {
    printf(">>> Tracing basic block at 0x%" PRIx64 ", block size = 0x%x\n", address, size);
}
static void hook_mem_valid(uc_engine *uc, uc_mem_type type, uint64_t address, int size, int64_t value, void *user_data) {
    printf(">>> Tracing mem_valid mem_type:%s at 0x%" PRIx64 ", size:0x%x, value:0x%" PRIx64 "\n",
           memTypeStr(type), address, size, value);
    if (type == UC_MEM_READ && size <= 4) {
        uint32_t v, pc;
        uc_mem_read(uc, address, &v, size);
        uc_reg_read(uc, UC_ARM_REG_PC, &pc);
        printf("PC:0x%X,read:0x%X\n", pc, v);
    }
}

#endif

#define MAX_LENIENT_PAGES 1024
static int lenientPages = 0;
static int lenientLogged = 0;

static void logLenientAccess(uc_engine *uc, uc_mem_type type, uint64_t address) {
    uint32_t pc = 0, lr = 0, sp = 0;
    uc_reg_read(uc, UC_ARM_REG_PC, &pc);
    uc_reg_read(uc, UC_ARM_REG_LR, &lr);
    uc_reg_read(uc, UC_ARM_REG_SP, &sp);
    printf("lenient map: type=%s addr=0x%" PRIx64 " PC=0x%X LR=0x%X", memTypeStr(type), address, pc, lr);
    dumpREG(uc);
    uint32_t code = (pc & ~3u) - 16;
    uint8_t buf[32];
    if (uc_mem_read(uc, code, buf, sizeof(buf)) == UC_ERR_OK) {
        for (int i = 0; i < 32; i += 16) {
            printf("code@0x%X: %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
                   code + i, buf[i], buf[i + 1], buf[i + 2], buf[i + 3], buf[i + 4], buf[i + 5], buf[i + 6], buf[i + 7],
                   buf[i + 8], buf[i + 9], buf[i + 10], buf[i + 11], buf[i + 12], buf[i + 13], buf[i + 14], buf[i + 15]);
        }
    }
    uint32_t words[16];
    if (uc_mem_read(uc, sp, words, sizeof(words)) == UC_ERR_OK) {
        for (int i = 0; i < 16; i += 4) {
            printf("sp+%02X: %08X %08X %08X %08X", i * 4, words[i], words[i + 1], words[i + 2], words[i + 3]);
        }
    }
}

static bool hook_mem_invalid(uc_engine *uc, uc_mem_type type, uint64_t address, int size, int64_t value, void *user_data) {
    printf(">>> Tracing mem_invalid mem_type:%s at 0x%" PRIx64 ", size:0x%x, value:0x%" PRIx64 "\n",
           memTypeStr(type), address, size, value);
    if ((type == UC_MEM_READ_UNMAPPED || type == UC_MEM_WRITE_UNMAPPED) && lenientPages < MAX_LENIENT_PAGES) {
        uint64_t begin = address & ~(uint64_t)0xFFF;
        uint64_t end = (address + (size > 0 ? size : 4) + 0xFFF) & ~(uint64_t)0xFFF;
        if (uc_mem_map(uc, begin, end - begin, UC_PROT_READ | UC_PROT_WRITE) == UC_ERR_OK) {
            lenientPages += (int)((end - begin) >> 12);
            if (lenientLogged < 4) {
                lenientLogged++;
                logLenientAccess(uc, type, address);
            }
            return true;
        }
    }
    dumpREG(uc);
    return false;
}

int freeVmrp(uc_engine *uc) {
    free(mrpMem);
    uc_close(uc);
    return 0;
}

uc_engine *initVmrp() {
    uc_engine *uc;
    uc_err err;
    uc_hook trace;

    err = uc_open(UC_ARCH_ARM, UC_MODE_ARM, &uc);
    if (err) {
        printf("Failed on uc_open() with error returned: %u (%s)\n", err, uc_strerror(err));
        return NULL;
    }

    mrpMem = malloc(TOTAL_MEMORY);
    // unicorn存在BUG，UC_HOOK_MEM_INVALID只能拦截第一次UC_MEM_FETCH_PROT，所以干脆设置成可执行，统一在UC_HOOK_CODE事件中处理
    err = uc_mem_map_ptr(uc, START_ADDRESS, TOTAL_MEMORY, UC_PROT_ALL, mrpMem);
    if (err) {
        printf("Failed mem map: %u (%s)\n", err, uc_strerror(err));
        goto end;
    }
    initMemoryManager(MEMORY_MANAGER_ADDRESS, MEMORY_MANAGER_SIZE);

    err = bridge_init(uc);
    if (err) {
        printf("Failed bridge_init(): %u (%s)\n", err, uc_strerror(err));
        goto end;
    }

#ifdef __ANDROID__
    uc_mem_map(uc, 0, CODE_ADDRESS, UC_PROT_READ | UC_PROT_WRITE);
#endif
#if 0 
    // 一些游戏检测内存时会去读写0-CODE_ADDRESS地址的值, 不清楚为什么要这样做，如果这块内存没有映射到unicorn会因为无效的内存访问导致程序退出
    // 映射一块内存后那些无法运行的游戏就能打开了，没有深入去研究，先记录在这里
    char *ppp = malloc(CODE_ADDRESS);
    // memset(ppp, 0xFF, CODE_ADDRESS);
    memset(ppp, 0, CODE_ADDRESS);
    uc_mem_map_ptr(uc, 0, CODE_ADDRESS, UC_PROT_ALL, ppp);

    // uc_mem_map(uc, 0, CODE_ADDRESS, UC_PROT_ALL);
    // uc_hook_add(uc, &trace, UC_HOOK_MEM_VALID, hook_mem_valid, NULL, 0, CODE_ADDRESS);
#endif

#ifdef DEBUG
    uc_hook_add(uc, &trace, UC_HOOK_BLOCK, hook_block, NULL, 1, 0);
    uc_hook_add(uc, &trace, UC_HOOK_MEM_VALID, hook_mem_valid, NULL, 1, 0);
    uc_hook_add(uc, &trace, UC_HOOK_CODE, hook_code_debug, NULL, 1, 0);
#endif
    uc_hook_add(uc, &trace, UC_HOOK_MEM_INVALID, hook_mem_invalid, NULL, 1, 0, 0);

    // 设置栈
    uint32_t value = STACK_ADDRESS + STACK_SIZE;  // 满递减
    uc_reg_write(uc, UC_ARM_REG_SP, &value);

    return uc;
end:
    uc_close(uc);
    return NULL;
}

#ifdef __EMSCRIPTEN__
EMSCRIPTEN_KEEPALIVE
int32_t c_event(int32_t code, int32_t p1, int32_t p2) {
    if (uc) {
        return bridge_dsm_mr_event(uc, code, p1, p2);
    }
    return MR_FAILED;
}
#endif

int32_t event(int32_t code, int32_t p1, int32_t p2) {
    if (uc) {
        return bridge_dsm_mr_event(uc, code, p1, p2);
    }
    return MR_FAILED;
}

int32_t timer() {
    if (uc) {
        return bridge_dsm_mr_timer(uc);
    }
    return MR_FAILED;
}

int32_t loadCode() {
    char *filename = "cfunction.ext";
    int32_t len = my_getLen(filename);
    char *buf = readFile(filename);
    if (buf == NULL) {
        return MR_FAILED;
    }
    uc_mem_write(uc, CODE_ADDRESS, buf, len);
    return MR_SUCCESS;
}

int startVmrp(const char *mrpName) {
    uc = initVmrp();
    if (uc == NULL) {
        printf("initVmrp() fail.\n");
        return MR_FAILED;
    }

    if (loadCode() == MR_FAILED) {
        printf("loadCode fail.\n");
        return MR_FAILED;
    }
    bridge_ext_init(uc);

    if (bridge_dsm_init(uc) == MR_SUCCESS) {
        printf("bridge_dsm_init success\n");
        dumpREG(uc);

        char *filename = (char *)mrpName;
        // char *filename = "winmine.mrp";
        char *extName = "start.mr";
        // char *extName = "cfunction.ext";

        uint32_t ret = bridge_dsm_mr_start_dsm(uc, filename, extName, NULL);
        printf("bridge_dsm_mr_start_dsm('%s','%s',NULL): 0x%X\n", filename, extName, ret);
        return MR_SUCCESS;
    }
    return MR_FAILED;
}

int32_t pauseVmrp() {
    return uc ? bridge_dsm_mr_pauseApp(uc) : MR_FAILED;
}

int32_t resumeVmrp() {
    return uc ? bridge_dsm_mr_resumeApp(uc) : MR_FAILED;
}