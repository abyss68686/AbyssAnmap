#!/usr/bin/env bash
# Download the exact upstream revisions represented by the supplied archives,
# then stage the Nmap runtime data and the complete Vulscan bundle as APK
# assets. Keeping upstream archives out of the Git repository makes the
# Android project compact while retaining a reproducible build.

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$project_root/.upstream-download"
third_party_dir="$project_root/third_party"
asset_dir="$project_root/app/src/main/assets/nmap-data"

# These Git object IDs are stored in the comments of the two supplied GitHub
# archive files (nmap-master.zip and vulscan-2.1.zip).
nmap_commit="5650f35c6d094a6bf79c3c8ad8a36de73d0a8ef1"
vulscan_commit="6beff39b5cc0f7a84c3cd0fe716f75b6f26b4ee8"
archive_directory="${UPSTREAM_ARCHIVE_DIR:-}"

download_archive() {
    local url="$1"
    local destination="$2"
    curl --fail --location --retry 3 --retry-delay 2 --silent --show-error \
        "$url" --output "$destination"
}

single_directory() {
    local root="$1"
    local found
    found="$(find "$root" -mindepth 1 -maxdepth 1 -type d -print -quit)"
    if [[ -z "$found" ]]; then
        printf 'Archive extraction did not produce a source directory in %s\n' "$root" >&2
        exit 1
    fi
    printf '%s\n' "$found"
}

rm -rf "$work_dir"
mkdir -p "$work_dir/nmap" "$work_dir/vulscan"

if [[ -n "$archive_directory" ]]; then
    nmap_archive="$archive_directory/nmap-master.zip"
    vulscan_archive="$archive_directory/vulscan-2.1.zip"
    for archive in "$nmap_archive" "$vulscan_archive"; do
        if [[ ! -f "$archive" ]]; then
            printf 'Expected supplied archive not found: %s\n' "$archive" >&2
            exit 1
        fi
    done
    cp "$nmap_archive" "$work_dir/nmap.zip"
    cp "$vulscan_archive" "$work_dir/vulscan.zip"
else
    download_archive \
        "https://github.com/nmap/nmap/archive/${nmap_commit}.zip" \
        "$work_dir/nmap.zip"
    download_archive \
        "https://github.com/scipag/vulscan/archive/${vulscan_commit}.zip" \
        "$work_dir/vulscan.zip"
fi

unzip -q "$work_dir/nmap.zip" -d "$work_dir/nmap"
unzip -q "$work_dir/vulscan.zip" -d "$work_dir/vulscan"

nmap_source="$(single_directory "$work_dir/nmap")"
vulscan_source="$(single_directory "$work_dir/vulscan")"

rm -rf "$third_party_dir/nmap" "$third_party_dir/vulscan" "$asset_dir"
mkdir -p "$third_party_dir" "$asset_dir"
mv "$nmap_source" "$third_party_dir/nmap"
mv "$vulscan_source" "$third_party_dir/vulscan"

for data_file in \
    nmap-mac-prefixes \
    nmap-os-db \
    nmap-protocols \
    nmap-rpc \
    nmap-service-probes \
    nmap-services \
    nse_main.lua; do
    cp -a "$third_party_dir/nmap/$data_file" "$asset_dir/$data_file"
done

cp -a "$third_party_dir/nmap/nselib" "$asset_dir/nselib"
cp -a "$third_party_dir/nmap/scripts" "$asset_dir/scripts"
mkdir -p "$asset_dir/scripts/vulscan"
cp -a "$third_party_dir/vulscan/." "$asset_dir/scripts/vulscan/"

# The supplied Nmap script.db contains one stale entry for a non-existent
# sap-hana-auth.nse. Remove only that row so database selectors match the
# complete set of supplied script files.
awk '!/sap-hana-auth\.nse/' "$asset_dir/scripts/script.db" > "$asset_dir/scripts/script.db.tmp"
mv "$asset_dir/scripts/script.db.tmp" "$asset_dir/scripts/script.db"

printf '%s\n' \
    'Abyss Anmap data bundle' \
    "nmap-source=$nmap_commit" \
    'vulscan=2.1 (2019-09-23)' \
    'nse-catalog=611 supplied official nmap scripts plus vulscan/vulscan.nse' \
    'script-db=stale sap-hana-auth.nse row omitted; matching supplied script files' \
    > "$asset_dir/asset-version.txt"

printf 'Prepared Nmap and Vulscan data from the supplied upstream revisions.\n'
