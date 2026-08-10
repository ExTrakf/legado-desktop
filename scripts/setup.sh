#!/usr/bin/env bash
# Legado Desktop 后端依赖安装与构建
# 用法: bash scripts/setup.sh
set -euo pipefail

cd "$(dirname "$0")/../backend"

echo "==> [1/4] 检查 Java（需 17+）"
java -version 2>&1 | head -1

echo "==> [2/4] Gradle Wrapper（首次自动下载 Gradle 8.14.4，约 130MB）"
# 官方源慢的话，取消下一行注释改用腾讯镜像：
# sed -i 's#services.gradle.org/distributions#mirrors.cloud.tencent.com/gradle#g' gradle/wrapper/gradle-wrapper.properties
./gradlew --version

echo "==> [3/4] 构建（首次下载 Kotlin 插件 + 依赖，约 100MB）"
./gradlew build -x test

echo "==> [4/4] 启动后端（默认 http://127.0.0.1:2323）"
echo "    运行: cd backend && ./gradlew run"
echo "    验证: curl http://127.0.0.1:2323/api/health"
