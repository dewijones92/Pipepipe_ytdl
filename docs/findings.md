# Findings: yt-dlp on Android 6.0 (API 23)

## The question
PipePipe's maintainer closed the "SponsorBlock-on-download" request saying yt-dlp covers it — but yt-dlp was later **dropped** from the app. That raised a side-question: *was the reason it was dropped (it forces `minSdk 24`) actually a hard wall?* i.e. **can modern yt-dlp run on Android 6.0 / API 23 at all?**

## Why API 24 is the stated floor
yt-dlp is a Python program; to run it on Android you embed a CPython runtime. The Python-on-Android library PipePipe used (`youtubedl-android`) declares `minSdk 24`, and an app's `minSdk` can't be lower than any library it pulls in. The deeper reason: Bionic (Android's libc) added a handful of functions at API 24, and it **version-tags** them — e.g. a binary built for API 24 imports `getifaddrs@LIBC_N` (N = Nougat = API 24). On an API-23 device, `libc.so` doesn't export that versioned symbol, so the dynamic linker refuses to load the library.

## Proof of the mechanism (real Android 6.0 / x86_64 emulator)
Two tiny NDK libs + a `dlopen` loader, run on a real API-23 emulator:

```
CONTROL  libok23.so   (built for API 23) ->  DLOPEN_OK
TEST     libneeds24.so (built for API 24, calls getifaddrs) ->
         DLOPEN_FAIL: cannot locate symbol "getifaddrs"
```

Confirmed: `libneeds24.so` imports `getifaddrs@LIBC_N` / `freeifaddrs@LIBC_N` as UNDEFINED, version-tagged `LIBC_N`.

## The gap is tiny and shimmable
A real CPython 3.13 build (Termux) needs, beyond API-23 base libc:

| arch | symbols above API-23 base libs |
|---|---|
| x86_64 | `lockf, preadv, pwritev` (+ `getifaddrs/if_nameindex` surfaced by `_socket` at runtime) |
| arm64  | `lockf, preadv, pwritev, memfd_create` |

All are thin syscall/fcntl wrappers. `shim/api23_shim.c` backfills them; loaded via `LD_PRELOAD`. (Termux solves the same problem with its own `libandroid-support.so`.) The extension modules `_ssl/_socket/_ctypes/_hashlib/...` need nothing beyond API-23 base.

## End-to-end results

### x86_64 — full Android-6.0 emulator (real kernel)
```
RUNG A  python3.13 -> PYTHON 3.13.13
RUNG B  import ssl,ctypes,socket,sqlite3,lzma,bz2 -> OK; OpenSSL 3.6.3; libffi OK
RUNG C  yt-dlp --version -> 2026.06.09
RUNG D  https GET -> HTTP 204 TLS OK
RUNG D  yt-dlp extract https://youtu.be/jNQXAC9IVRw -> "Me at the zoo"  19
```

### arm64 — qemu-user on the image's real bionic libc
```
RUNG A  python3.13 -> ARM64 PYTHON 3.13.13 | uname.machine = aarch64
RUNG B  import ssl,ctypes,socket,sqlite3 -> OK; OpenSSL 3.6.3; libffi OK
RUNG C  yt-dlp --version -> 2026.06.09
RUNG D  https GET -> HTTP 204 TLS OK on aarch64
RUNG D  yt-dlp extract -> "Me at the zoo"  19
```

Bionic `libc.so` + `linker64` were extracted from the API-23 `arm64-v8a` system image with `debugfs`; run under `qemu-aarch64-static`.

## Caveats (calibrated)
- **qemu-user uses the host kernel**, so the arm64 result proves the *userspace/ABI* layer, not an Android-6.0 *kernel*. Untested edge: `memfd_create` (Linux 3.17+) on a ~2015 device kernel, *if* called (yt-dlp doesn't).
- DNS under bare qemu-user needed a pre-seeded `hosts` file (no Android `netd`/property service in the sandbox). Not a device limitation — a real phone's framework provides DNS.
- **Feasible ≠ advisable.** Shipping this in PipePipe still means a tens-of-MB Python runtime, a maintained shim + build, and a stale-on-device yt-dlp. The verdict that the maintainer was right to drop it stands; only the *feasibility* claim changed.

## Timeline reference (PipePipe yt-dlp era, from git history)
```
2024-12-01  use yt-dlp as fallback for YouTube
2025-01-12  fully migrate to yt-dlp
2025-01-11  bump minSdk since yt-dlp requires py 3.11 and minSdk 24
2025-01-17  drop yt-dlp            (+ revert the minSdk bump back to 23)
```
