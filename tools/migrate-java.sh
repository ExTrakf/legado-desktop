#!/bin/bash
# 迁移 .java 文件（包名替换 + 删 android import）
SRC=/workspace/legado/app/src/main/java/io/legado/app
DST=/workspace/legado-desktop/backend/src/main/java/io/legado/desktop
for f in "$@"; do
  mkdir -p "$(dirname "$DST/$f")"
  sed -e 's/^package io\.legado\.app/package io.legado.desktop/' \
      -e 's/^import io\.legado\.app\./import io.legado.desktop./' \
      -e '/^import android\./d' \
      -e '/^import androidx\./d' \
      "$SRC/$f" > "$DST/$f"
  echo "OK: $f"
done
