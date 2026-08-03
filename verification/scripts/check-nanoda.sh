#!/usr/bin/env bash
set -euo pipefail

# lean-action #169 tracks the incompatibility that this script avoids:
# lean4export emits NDJSON, while lean-action currently builds the stale
# nanoda_lib/debug parser for the legacy export format. Pin both compatible
# sources so an upstream branch move cannot silently change this proof gate.
LEAN4EXPORT_COMMIT="9fb131bb100eb32ccf6836f14e4f8328d13b6792"
NANODA_COMMIT="418320295890faed83a96fd97907b12a3b6728c2"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE="$(cd "$SCRIPT_DIR/../.." && pwd)"
LEAN_PROJECT="$WORKSPACE/verification/lean"
NANODA_CONFIG="$WORKSPACE/verification/contract/nanoda-config.json"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/ocl2cypher-nanoda.XXXXXXXX")"

cleanup() {
  rm -rf -- "$TEMP_ROOT"
}
trap cleanup EXIT

for command_name in git lake cargo; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is unavailable: $command_name" >&2
    exit 1
  fi
done

fetch_pinned_source() {
  local repository_url="$1"
  local commit="$2"
  local destination="$3"

  git init --quiet "$destination"
  git -C "$destination" remote add origin "$repository_url"
  git -C "$destination" fetch --quiet --depth 1 origin "$commit"
  git -C "$destination" checkout --quiet --detach FETCH_HEAD

  local actual_commit
  actual_commit="$(git -C "$destination" rev-parse HEAD)"
  if [[ "$actual_commit" != "$commit" ]]; then
    echo "Pinned source mismatch: expected $commit, actual $actual_commit" >&2
    exit 1
  fi
}

LEAN4EXPORT_DIR="$TEMP_ROOT/lean4export"
NANODA_DIR="$TEMP_ROOT/nanoda_lib"

fetch_pinned_source \
  "https://github.com/leanprover/lean4export.git" \
  "$LEAN4EXPORT_COMMIT" \
  "$LEAN4EXPORT_DIR"
fetch_pinned_source \
  "https://github.com/ammkrn/nanoda_lib.git" \
  "$NANODA_COMMIT" \
  "$NANODA_DIR"

cp "$LEAN_PROJECT/lean-toolchain" "$LEAN4EXPORT_DIR/lean-toolchain"
(
  cd "$LEAN4EXPORT_DIR"
  lake build
)
(
  cd "$NANODA_DIR"
  cargo build --locked --release
)

MODULE_NAME="$(awk '/^[[:space:]]*package[[:space:]]+/ { print $2; exit }' "$LEAN_PROJECT/lakefile.lean")"
if [[ -z "$MODULE_NAME" ]]; then
  echo "Could not detect the Lean package module from lakefile.lean" >&2
  exit 1
fi

echo "Pinned nanoda check: module=$MODULE_NAME, lean4export=$LEAN4EXPORT_COMMIT, nanoda=$NANODA_COMMIT"
(
  cd "$LEAN_PROJECT"
  lake env "$LEAN4EXPORT_DIR/.lake/build/bin/lean4export" "$MODULE_NAME"
) | "$NANODA_DIR/target/release/nanoda_bin" "$NANODA_CONFIG"
