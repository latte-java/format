#!/usr/bin/env bash
#
# Copyright (c) 2026 The Latte Project
# SPDX-License-Identifier: MIT
#
# Runs the Latte Java formatter from a built bundle, mirroring `latte run`.
#
# Expected layout (produced by the `bundle` target in project.latte):
#   build/bundle/format.sh   - this script
#   build/bundle/lib/        - the formatter jar and all runtime dependencies
#
# Usage: format.sh [options] <directory> [code-style.xml]
#
# Honors JAVA_HOME (falls back to `java` on PATH) and forwards JAVA_OPTS to the JVM. Unlike a server bundle this does
# NOT change directory: the formatter is given a directory to format, so relative path arguments must keep resolving
# against the caller's working directory. The module path therefore references the bundle's jars by absolute path.
set -euo pipefail

BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi

# Build a colon-separated module path from every jar in lib/ (absolute paths, since we do not cd into the bundle).
MODULE_PATH=""
for jar in "$BUNDLE_DIR"/lib/*.jar; do
  MODULE_PATH="${MODULE_PATH:+$MODULE_PATH:}$jar"
done

exec "$JAVA_BIN" \
  ${JAVA_OPTS:-} \
  --module-path "$MODULE_PATH" \
  --module org.lattejava.format/org.lattejava.format.Main \
  "$@"
