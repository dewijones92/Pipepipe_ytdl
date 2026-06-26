#include <ifaddrs.h>
/* getifaddrs / freeifaddrs were added to Android's Bionic libc at API 24.
 * A .so compiled targeting API 24 will carry an undefined reference to it. */
int probe(void) {
    struct ifaddrs *ifa = 0;
    int r = getifaddrs(&ifa);
    if (ifa) freeifaddrs(ifa);
    return r;
}
