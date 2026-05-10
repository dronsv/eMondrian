#!/usr/bin/env bash
set -euo pipefail

repo="${SCHEMA_EDITOR_REPO:-https://github.com/SergeiSemenkov/SchemaEditor.git}"
ref="${SCHEMA_EDITOR_REF:-0f54e9da4058fd0f2d77e5ea6b5e2e607e20445e}"
target="$(cd "$(dirname "$0")" && pwd)/schema-editor"

if [ "${SCHEMA_EDITOR_REFRESH:-false}" != "true" ]; then
  test -f "$target/package.json"
  test -f "$target/package-lock.json"
  exit 0
fi

if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
  echo "SCHEMA_EDITOR_REF must be a full 40-char commit SHA, got: $ref" >&2
  exit 2
fi

rm -rf "$target"
mkdir "$target"
git -C "$target" init -q
git -C "$target" remote add origin "$repo"
git -C "$target" fetch --depth 1 origin "$ref"
git -C "$target" checkout --detach FETCH_HEAD -q

actual="$(git -C "$target" rev-parse HEAD)"
if [ "$actual" != "$ref" ]; then
  echo "SchemaEditor checkout mismatch: expected $ref, got $actual" >&2
  exit 3
fi
