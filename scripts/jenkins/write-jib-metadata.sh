#!/usr/bin/env bash
# write-jib-metadata.sh <jib-image.digest file> <metadata file>
#
# Jib (the Docker-free dev image build) writes the digest it pushed to target/jib-image.digest; the lock step reads buildx's
# metadata file ({"containerimage.digest": "sha256:..."}). This writes the same fact in that shape and refuses anything that is not
# a sha256 digest, so a push that reported no digest can never be locked.
set -euo pipefail
d="$(tr -d '[:space:]' < "${1:?digest file}")"
printf '%s' "$d" | grep -Eq '^sha256:[0-9a-f]{64}$' || { echo "jib push reported no image digest ('$d')" >&2; exit 1; }
printf '{"containerimage.digest": "%s"}\n' "$d" > "${2:?metadata file}"
