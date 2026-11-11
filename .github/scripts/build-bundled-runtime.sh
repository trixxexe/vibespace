#!/usr/bin/env bash
set -euo pipefail

output_dir="${1:?usage: build-bundled-runtime.sh OUTPUT_DIR}"
mkdir -p "$output_dir/runtime"

proot_release="v26.10.08-a179d3e"
declare -A arches=(
  [aarch64]=linux/arm64/v8
  [armv7]=linux/arm/v7
  [x86_64]=linux/amd64
  [x86]=linux/386
)

for arch in "${!arches[@]}"; do
  platform="${arches[$arch]}"
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT

  echo "Building Alpine Python/Node runtime for $arch ($platform)"
  docker run --rm --platform "$platform" -v "$work:/out" alpine:3.24.0 sh -ec '
    apk add --no-cache python3 py3-pip nodejs npm util-linux-misc ca-certificates
    mkdir -p /out/rootfs
    tar -C / -czf /out/rootfs.tar.gz --exclude=./dev --exclude=./proc --exclude=./sys --exclude=./tmp .
    apk info -v | sort > /out/packages.txt
  '
  mkdir -p "$work/rootfs"
  tar -xzf "$work/rootfs.tar.gz" -C "$work/rootfs"

  case "$arch" in
    aarch64) checksum=d621d71fb68bf07accbada37ffc4208cf4a963eed70916ee68f848cbd9555cc3 ;;
    armv7) checksum=0c382c0512c3d536dc0685f3f6264b326073c071ebdd961e3346799aae8dd5b3 ;;
    x86) checksum=640da6a26dec3e86d3bdf4f3b9d8f6dd0466ba7be0de36b1ca6e508229610f36 ;;
    x86_64) checksum=ef06aeb47f5bb9c96bb9019fe8efa320ed199151020428b2335af6035c05f166 ;;
  esac

  proot_arch="$arch"
  [[ "$arch" == armv7 ]] && proot_arch=arm
  curl --fail --location --retry 3 \
    "https://github.com/ahmed-alnassif/proot/releases/download/$proot_release/proot-$proot_arch.zip" \
    -o "$work/proot.zip"
  echo "$checksum  $work/proot.zip" | sha256sum --check --status
  mkdir -p "$work/bin"
  unzip -q "$work/proot.zip" -d "$work/bin"
  test -f "$work/bin/proot"
  chmod 0755 "$work/bin/proot"

  mkdir -p "$work/rootfs/dev" "$work/rootfs/proc" "$work/rootfs/sys" "$work/rootfs/tmp"
  chmod 1777 "$work/rootfs/tmp"
  tar -C "$work" -czf "$output_dir/runtime/$arch.tar.gz" bin/proot rootfs
  cp "$work/packages.txt" "$output_dir/runtime/$arch.packages.txt"
  rm -rf "$work"
  trap - EXIT
done

du -h "$output_dir"/runtime/*.tar.gz
