#!/usr/bin/env bash
# Build a macOS installer package (.pkg) that installs the VST3 and AU into the
# standard system plug-in folders.
#
# Usage: build_pkg.sh <artefacts_release_dir> <version>
#
# Signing/notarization are optional and only happen if the relevant environment
# variables are set (so unsigned local/CI builds still succeed):
#   MACOS_INSTALLER_IDENTITY   "Developer ID Installer: Your Name (TEAMID)"
# Notarization (after signing) is left as a documented follow-up; see
# docs/ARCHITECTURE.md (Phase 5).
set -euo pipefail

REL_DIR="${1:?artefacts Release dir required}"
VERSION="${2:-0.0.0}"
IDENT="com.hillbros.voxai"
STAGE="$(mktemp -d)"
ROOT="$STAGE/root"

VST3_DST="$ROOT/Library/Audio/Plug-Ins/VST3"
AU_DST="$ROOT/Library/Audio/Plug-Ins/Components"
mkdir -p "$VST3_DST" "$AU_DST"

[ -d "$REL_DIR/VST3/VoxAI.vst3" ]      && cp -R "$REL_DIR/VST3/VoxAI.vst3" "$VST3_DST/"
[ -d "$REL_DIR/AU/VoxAI.component" ]   && cp -R "$REL_DIR/AU/VoxAI.component" "$AU_DST/"

PKG_OUT="VoxAI-${VERSION}-macos.pkg"

BUILD_ARGS=(--root "$ROOT" --identifier "$IDENT" --version "$VERSION" --install-location "/")
if [ -n "${MACOS_INSTALLER_IDENTITY:-}" ]; then
    echo "Signing installer with: $MACOS_INSTALLER_IDENTITY"
    BUILD_ARGS+=(--sign "$MACOS_INSTALLER_IDENTITY")
else
    echo "MACOS_INSTALLER_IDENTITY not set — building an UNSIGNED .pkg"
fi

pkgbuild "${BUILD_ARGS[@]}" "$PKG_OUT"
echo "Wrote $PKG_OUT"
