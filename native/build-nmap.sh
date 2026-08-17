#!/usr/bin/env bash
# Cross-compile the supplied Nmap source as an Android PIE executable.
#
# The output deliberately uses a .so name because Android extracts native
# libraries from lib/<abi>/ with executable permission. It is still an ELF PIE
# program, not a JNI library; Abyss Anmap launches it with ProcessBuilder.

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$project_root/third_party/nmap"
abi="${1:-arm64-v8a}"
api_level="${ANDROID_API_LEVEL:-26}"
ndk_root="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [[ ! -d "$source_dir" ]]; then
    printf 'Nmap source not found at %s\n' "$source_dir" >&2
    exit 1
fi

if [[ -z "$ndk_root" || ! -d "$ndk_root" ]]; then
    printf '%s\n' 'Set ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) to an installed Android NDK.' >&2
    exit 1
fi

if [[ -n "${ANDROID_NDK_HOST_TAG:-}" ]]; then
    host_tag="$ANDROID_NDK_HOST_TAG"
else
    case "$(uname -s)-$(uname -m)" in
        Linux-x86_64) host_tag="linux-x86_64" ;;
        Darwin-arm64) host_tag="darwin-arm64" ;;
        Darwin-x86_64) host_tag="darwin-x86_64" ;;
        *)
            printf 'Unsupported NDK host: %s-%s\n' "$(uname -s)" "$(uname -m)" >&2
            exit 1
            ;;
    esac
fi

toolchain="$ndk_root/toolchains/llvm/prebuilt/$host_tag"
if [[ ! -d "$toolchain" ]]; then
    printf 'NDK LLVM toolchain not found at %s\n' "$toolchain" >&2
    exit 1
fi

case "$abi" in
    arm64-v8a)
        target="aarch64-linux-android"
        compiler_prefix="aarch64-linux-android${api_level}"
        runtime_triple="aarch64-linux-android"
        expected_machine="AArch64"
        ;;
    *)
        printf 'Only arm64-v8a is currently supported; got %s\n' "$abi" >&2
        exit 1
        ;;
esac

cc="$toolchain/bin/${compiler_prefix}-clang"
cxx="$toolchain/bin/${compiler_prefix}-clang++"
ar="$toolchain/bin/llvm-ar"
ranlib="$toolchain/bin/llvm-ranlib"
strip="$toolchain/bin/llvm-strip"
readelf="$toolchain/bin/llvm-readelf"
cxx_shared_library="$toolchain/sysroot/usr/lib/$runtime_triple/libc++_shared.so"

for required_tool in "$cc" "$cxx" "$ar" "$ranlib" "$strip" "$readelf"; do
    if [[ ! -x "$required_tool" ]]; then
        printf 'Required NDK tool not found: %s\n' "$required_tool" >&2
        exit 1
    fi
done

if [[ ! -f "$cxx_shared_library" ]]; then
    printf 'NDK C++ shared runtime not found: %s\n' "$cxx_shared_library" >&2
    exit 1
fi

native_parent="$project_root/.native-build"
mkdir -p "$native_parent"
build_dir="$(mktemp -d "$native_parent/${abi}.XXXXXX")"
trap 'rm -rf "$build_dir"' EXIT

cp -a "$source_dir" "$build_dir/nmap"
cd "$build_dir/nmap"

# libdnet's Linux route implementation calls a libc IPv6 macro with dnet's
# own IPv6 type. Android's headers reject that type mismatch, so apply the
# small compatibility patch before configure generates subproject Makefiles.
patch --batch -p1 < "$project_root/native/patches/libdnet-android-ipv6.patch"

export CC="$cc"
export CXX="$cxx"
export AR="$ar"
export RANLIB="$ranlib"
export STRIP="$strip"
export CPP="$cc -E"
export CFLAGS="${CFLAGS:-} -O2 -fPIE -fPIC -D__ANDROID_API__=${api_level}"
export CXXFLAGS="${CXXFLAGS:-} -O2 -fPIE -fPIC -D__ANDROID_API__=${api_level}"
# Current Android NDK LLD rejects a few intentionally optional PCRE exports
# listed in PCRE's version script. GNU ld accepted these historically; this
# restores that behaviour while retaining the hardened PIE linker flags.
export LDFLAGS="${LDFLAGS:-} -pie -Wl,-z,relro,-z,now -Wl,--undefined-version"

./configure \
    --build="$(uname -m)-pc-linux-gnu" \
    --host="$target" \
    --prefix=/usr/local \
    --datadir=/usr/local/share/nmap \
    --disable-nls \
    --without-zenmap \
    --without-ncat \
    --without-nping \
    --without-ndiff \
    --without-openssl \
    --without-libssh2 \
    --with-libpcap=included \
    --with-libpcre=included \
    --with-libz=included \
    --with-libdnet=included \
    --with-liblua=included

jobs="${NATIVE_BUILD_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf '2')}"
make -j"$jobs" nmap

output_dir="$project_root/app/src/main/jniLibs/$abi"
output_file="$output_dir/libnmap.so"
runtime_output_file="$output_dir/libc++_shared.so"
mkdir -p "$output_dir"
temporary_output="$(mktemp "$output_dir/libnmap.so.XXXXXX")"
temporary_runtime_output="$(mktemp "$output_dir/libc++_shared.so.XXXXXX")"
trap 'rm -rf "$build_dir"; rm -f "$temporary_output" "$temporary_runtime_output"' EXIT

cp nmap "$temporary_output"
"$strip" --strip-unneeded "$temporary_output"
cp -L "$cxx_shared_library" "$temporary_runtime_output"

if ! "$readelf" -h "$temporary_output" | grep -q "Machine:.*$expected_machine"; then
    printf 'Built binary has an unexpected architecture.\n' >&2
    exit 1
fi

if ! "$readelf" -l "$temporary_output" | grep -q 'Requesting program interpreter'; then
    printf 'Built binary is missing an Android program interpreter.\n' >&2
    exit 1
fi

if ! "$readelf" -d "$temporary_output" | grep -Fq 'Shared library: [libc++_shared.so]'; then
    printf 'Built binary does not declare the expected NDK C++ shared runtime.\n' >&2
    exit 1
fi

if ! "$readelf" -h "$temporary_runtime_output" | grep -q "Machine:.*$expected_machine"; then
    printf 'Bundled C++ runtime has an unexpected architecture.\n' >&2
    exit 1
fi

mv -f "$temporary_output" "$output_file"
mv -f "$temporary_runtime_output" "$runtime_output_file"
printf 'Built %s and bundled %s\n' "$output_file" "$runtime_output_file"
