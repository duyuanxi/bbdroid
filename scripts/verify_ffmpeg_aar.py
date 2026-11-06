#!/usr/bin/env python3
"""Validate the bundled AAR's ABI matrix, ELF architecture, dependency closure and 16 KB alignment."""
import pathlib
import struct
import sys
import tempfile
import subprocess
import re
import zipfile

AAR = pathlib.Path(__file__).resolve().parents[1] / "app/libs/ffmpeg-kit-full-gpl-8.1.7.aar"
ABIS = {"arm64-v8a": 183, "x86_64": 62}
REQUIRED = {"libc++_shared.so", "libavutil.so", "libswresample.so", "libswscale.so", "libavcodec.so", "libavformat.so", "libavfilter.so", "libavdevice.so", "libffmpegkit_abidetect.so", "libffmpegkit.so"}
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so", "libcamera2ndk.so", "libmediandk.so", "libGLESv1_CM.so", "libGLESv2.so"}

def verify():
    count = 0
    with zipfile.ZipFile(AAR) as archive, tempfile.TemporaryDirectory() as directory:
        actual_abis = {n.split('/')[1] for n in archive.namelist() if n.startswith('jni/') and n.endswith('.so')}
        assert actual_abis == set(ABIS), f"Unexpected ABI matrix: {actual_abis}"
        for abi, machine in ABIS.items():
            names = {n.rsplit('/', 1)[1] for n in archive.namelist() if n.startswith(f'jni/{abi}/') and n.endswith('.so')}
            assert REQUIRED <= names, f"Missing native libraries: {REQUIRED - names}"
            for name in names:
                data = archive.read(f'jni/{abi}/{name}')
                assert data[:6] == b'\x7fELF\x02\x01', f"Not little-endian ELF64: {abi}/{name}"
                assert struct.unpack_from('<H', data, 18)[0] == machine, f"Wrong machine: {abi}/{name}"
                phoff = struct.unpack_from('<Q', data, 32)[0]
                phsize, phcount = struct.unpack_from('<HH', data, 54)
                for i in range(phcount):
                    header = struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize)
                    if header[0] == 1:  # PT_LOAD
                        assert header[7] >= 16384, f"Not 16 KB aligned: {abi}/{name}"
                        assert (header[2] - header[3]) % 16384 == 0, f"Offset/address mismatch: {abi}/{name}"
                path = pathlib.Path(directory) / name
                path.write_bytes(data)
                dynamic = subprocess.check_output(['readelf', '-d', str(path)], text=True)
                needed = set(re.findall(r'Shared library: \[(.*?)\]', dynamic))
                assert needed <= names | SYSTEM, f"Unresolved DT_NEEDED: {abi}/{name}: {needed - names - SYSTEM}"
                count += 1
    print(f"PASS: {count} native libraries; arm64-v8a + x86_64; dependency closure; 16 KB ELF alignment")

if __name__ == '__main__':
    try:
        verify()
    except (AssertionError, OSError, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
