#include <dlfcn.h>
#include <stdio.h>

/* dlopen a .so given as argv[1]; report success or the exact linker error. */
int main(int argc, char **argv) {
    if (argc < 2) { printf("usage: loader <path.so>\n"); return 2; }
    void *h = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!h) { printf("DLOPEN_FAIL: %s\n", dlerror()); return 1; }
    printf("DLOPEN_OK\n");
    void *s = dlsym(h, "probe");
    printf("%s\n", s ? "SYM_OK" : "SYM_MISS");
    return 0;
}
