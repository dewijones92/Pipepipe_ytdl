import sys, ssl, ctypes, socket, hashlib, lzma, bz2, sqlite3, zlib, json, select, math, _decimal
print("imports OK ->", "ssl/ctypes/socket/hashlib/lzma/bz2/sqlite3/zlib/decimal")
print("openssl:", ssl.OPENSSL_VERSION)
print("ctypes libffi works:", bool(ctypes.CDLL(None)))
