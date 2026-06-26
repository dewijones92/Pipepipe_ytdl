/* libshim: backfill libc functions Bionic added at API 24, so a Python/yt-dlp
 * build for API 24 resolves its symbols on an API 23 (Android 6.0) device.
 * Network-enumeration fns are benign stubs (yt-dlp's HTTPS path never calls them). */
#include <sys/uio.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>

#ifndef F_ULOCK
#define F_ULOCK 0
#define F_LOCK  1
#define F_TLOCK 2
#define F_TEST  3
#endif

int lockf(int fd, int cmd, off_t len) {
    struct flock fl; fl.l_whence = SEEK_CUR; fl.l_start = 0; fl.l_len = len;
    if (cmd == F_ULOCK) { fl.l_type = F_UNLCK; return fcntl(fd, F_SETLK,  &fl); }
    if (cmd == F_LOCK)  { fl.l_type = F_WRLCK; return fcntl(fd, F_SETLKW, &fl); }
    if (cmd == F_TLOCK) { fl.l_type = F_WRLCK; return fcntl(fd, F_SETLK,  &fl); }
    if (cmd == F_TEST)  { fl.l_type = F_WRLCK;
        if (fcntl(fd, F_GETLK, &fl) < 0) return -1;
        return fl.l_type == F_UNLCK ? 0 : -1; }
    return -1;
}
ssize_t preadv(int fd, const struct iovec *iov, int n, off_t off) {
    return syscall(SYS_preadv,  fd, iov, n, (unsigned long)off, (unsigned long)((unsigned long long)off >> 32));
}
ssize_t pwritev(int fd, const struct iovec *iov, int n, off_t off) {
    return syscall(SYS_pwritev, fd, iov, n, (unsigned long)off, (unsigned long)((unsigned long long)off >> 32));
}

/* network interface enumeration (added API 24) — benign stubs */
struct ifaddrs;
int  getifaddrs(struct ifaddrs **ifap) { if (ifap) *ifap = 0; return 0; }
void freeifaddrs(struct ifaddrs *ifa)  { (void)ifa; }

struct if_nameindex { unsigned int if_index; char *if_name; };
struct if_nameindex *if_nameindex(void) {
    return (struct if_nameindex *)calloc(1, sizeof(struct if_nameindex)); /* {0,NULL} terminator */
}
void if_freenameindex(struct if_nameindex *p) { free(p); }

/* memfd_create: Bionic wrapper added API 30; surfaced by the arm64 CPython build */
int memfd_create(const char *name, unsigned int flags) {
    return (int)syscall(SYS_memfd_create, name, flags);
}
