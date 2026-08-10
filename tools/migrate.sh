#!/bin/bash
# 从 legado 迁移后端源码到 legado-desktop（去除 Android/Room/Parcelable 依赖）
# 用法: bash tools/migrate.sh <相对路径文件...>
# 幂等：每次覆盖目标文件

SRC=/workspace/legado/app/src/main/java/io/legado/app
DST=/workspace/legado-desktop/backend/src/main/kotlin/io/legado/desktop

SED_EXPR=(
  -e 's/^package io\.legado\.app/package io.legado.desktop/'
  -e 's/^import io\.legado\.app\./import io.legado.desktop./'
  # 删除 Android / AndroidX / Android 库 import
  -e '/^import android\./d'
  -e '/^import androidx\./d'
  -e '/^import splitties\./d'
  -e '/^import kotlinx\.parcelize\./d'
  -e '/^import com\.bumptech\.glide\./d'
  -e '/^import com\.google\.android\.material\./d'
  -e '/^import com\.jeremyliao\.liveeventbus\./d'
  # 删除 Room / 注解行
  -e '/^@Parcelize/d'
  -e '/^@Entity/d'
  -e '/^@PrimaryKey/d'
  -e '/^@ColumnInfo/d'
  -e '/^@Index/d'
  -e '/^@ForeignKey/d'
  -e '/^@Ignore/d'
  -e '/^@TypeConverter/d'
  -e '/^@Embedded/d'
  -e '/^@Dao/d'
  -e '/^@Insert/d'
  -e '/^@Delete/d'
  -e '/^@Update/d'
  -e '/^@Transaction/d'
  -e '/^@Keep/d'
  -e '/^@SuppressLint/d'
  # 删除 Parcelable 继承
  -e 's/: Parcelable//g'
  -e 's/, Parcelable//g'
  -e 's/: Parcelable {//g'
)

fail=0
for f in "$@"; do
  src="$SRC/$f"
  dst="$DST/$f"
  if [ ! -f "$src" ]; then
    echo "SKIP(缺失): $f"
    fail=1
    continue
  fi
  mkdir -p "$(dirname "$dst")"
  sed "${SED_EXPR[@]}" "$src" > "$dst"
  echo "OK: $f"
done
exit $fail

# Java 文件迁移（调用方式: bash tools/migrate-java.sh <文件...>）
