#!/bin/bash
# Legado Desktop 后端集成测试（单次 shell 调用内完成：启动→就绪→验证→清理）
# 用法: bash tools/test_backend.sh
set -u

cd /workspace/legado-desktop/backend
export GRADLE_USER_HOME=/workspace/.gradle
export LEGADO_DESKTOP_HOME=${LEGADO_DESKTOP_HOME:-/tmp/legado-test}
PORT=2323
BASE="http://127.0.0.1:$PORT"
RUN_LOG=/tmp/legado_run_test.log
PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); echo "  ✅ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ $1"; }

# ---------- 0. 清理残留 ----------
echo "== 0. 清理残留进程 =="
pkill -f "io.legado.desktop.MainKt" 2>/dev/null
pkill -f "gradlew run" 2>/dev/null
sleep 2
rm -rf "$LEGADO_DESKTOP_HOME"
mkdir -p "$LEGADO_DESKTOP_HOME"

# ---------- 1. 启动服务 ----------
echo "== 1. 启动后端 =="
BIN="build/install/legado-desktop-backend/bin/legado-desktop-backend"
if [ ! -x "$BIN" ]; then
  ./gradlew installDist --console=plain > /tmp/legado_install.log 2>&1
  echo "  installDist 完成"
fi
LEGADO_DESKTOP_HOME="$LEGADO_DESKTOP_HOME" "$BIN" > "$RUN_LOG" 2>&1 &
SERVER_PID=$!
echo "  server pid=$SERVER_PID"

# 轮询 health（最多 60s，JVM 直接启动很快）
echo "== 2. 等待就绪 =="
READY=0
for i in $(seq 1 12); do
  sleep 5
  if curl -s -m 3 "$BASE/api/health" > /dev/null 2>&1; then
    READY=1; echo "  就绪（${i}x5s）"; break
  fi
done
if [ $READY -eq 0 ]; then
  echo "  ❌ 服务未就绪，日志尾部："
  tail -20 "$RUN_LOG"
  kill "$SERVER_PID" 2>/dev/null
  exit 1
fi

# ---------- 3. API 测试 ----------
echo "== 3. API 测试 =="
HEALTH=$(curl -s -m 5 "$BASE/api/health")
echo "  health=$HEALTH"
echo "$HEALTH" | grep -q '"isSuccess":true' && ok "GET /api/health 返回 isSuccess:true" || bad "health 响应异常"
echo "$HEALTH" | grep -q 'legado-desktop-backend' && ok "service 名称正确" || bad "service 名称缺失"

# CORS 预检
CORS=$(curl -s -m 5 -X OPTIONS -i "$BASE/api/health" | head -5 | grep -i "access-control" | head -1)
echo "  cors=$CORS"
[ -n "$CORS" ] && ok "CORS 头存在" || bad "CORS 头缺失"

# 404 路由
NF=$(curl -s -m 5 "$BASE/nonexistent")
echo "  notfound=$NF"
echo "$NF" | grep -q '"isSuccess":false' && ok "未知路由返回 isSuccess:false" || bad "未知路由响应异常"

# ---------- 4. 数据库测试 ----------
echo "== 4. 数据库 schema 测试 =="
DBFILE="$LEGADO_DESKTOP_HOME/books.db"
if [ -f "$DBFILE" ] && [ -s "$DBFILE" ]; then
  ok "books.db 已创建且非空"
else
  bad "books.db 缺失或为空（服务未初始化数据库）"
fi

python3 << 'PYEOF'
import sqlite3, sys
ok_fail = [0, 0]
def ok(m):
    ok_fail[0] += 1; print(f"  ✅ {m}")
def bad(m):
    ok_fail[1] += 1; print(f"  ❌ {m}")

conn = sqlite3.connect("/tmp/legado-test/books.db")
tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
views  = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='view' ORDER BY name")]

expected_tables = {'books','book_groups','book_sources','chapters','replace_rules','rssSources',
                   'rssArticles','cookies','caches','searchBooks','search_keywords','txtTocRules',
                   'ruleSubs','dictRules','highlightRules','bookmarks','readRecord','servers',
                   'httpTTS','keyboardAssists','auto_task_rules','highlights','rssReadRecords','rssStars'}
missing = expected_tables - set(tables)
if not missing:
    ok(f"表齐全（{len(tables)} 张）")
else:
    bad(f"缺表: {missing}")

if 'book_sources_part' in views:
    ok("视图 book_sources_part 已创建")
else:
    bad("视图 book_sources_part 缺失")

# 冒烟：book_sources 插入/查询/更新/删除
try:
    conn.execute("INSERT OR REPLACE INTO book_sources (bookSourceUrl, bookSourceName, bookSourceType, lastUpdateTime, respondTime, weight) VALUES ('https://test.com', '测试源', 0, 0, 0, 0)")
    conn.commit()
    n = conn.execute("SELECT COUNT(*) FROM book_sources").fetchone()[0]
    assert n >= 1
    ok("book_sources 插入成功")
    # 视图联动
    v = conn.execute("SELECT bookSourceUrl FROM book_sources_part WHERE bookSourceUrl='https://test.com'").fetchone()
    assert v and v[0] == 'https://test.com'
    ok("book_sources_part 视图联动正确")
    conn.execute("DELETE FROM book_sources WHERE bookSourceUrl='https://test.com'")
    conn.commit()
    ok("book_sources 删除成功")
except Exception as e:
    bad(f"book_sources 冒烟失败: {e}")

# 冒烟：books 外键级联（chapters）—— 需开启 foreign_keys
try:
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("INSERT OR REPLACE INTO books (bookUrl, origin, name) VALUES ('https://test.com/book', 'loc_book', '测试书')")
    conn.execute("INSERT OR REPLACE INTO chapters (url, title, isVolume, baseUrl, bookUrl, `index`, isVip, isPay) VALUES ('https://test.com/c1', '第一章', 0, '', 'https://test.com/book', 0, 0, 0)")
    conn.commit()
    conn.execute("DELETE FROM books WHERE bookUrl='https://test.com/book'")
    conn.commit()
    n = conn.execute("SELECT COUNT(*) FROM chapters WHERE bookUrl='https://test.com/book'").fetchone()[0]
    assert n == 0
    ok("books→chapters 外键级联删除生效")
except Exception as e:
    bad(f"外键级联测试失败: {e}")
conn.close()
print(f"  python 断言: pass={ok_fail[0]} fail={ok_fail[1]}")
PYEOF

# ---------- 4.5 DAO 冒烟（Kotlin DAO 层全量 CRUD，--dao-smoke-test 跑完即退出） ----------
echo "== 4.5 DAO 冒烟（24 DAO CRUD + flow + collate localized + IN(:list)） =="
LEGADO_DESKTOP_HOME="$LEGADO_DESKTOP_HOME" "$BIN" --dao-smoke-test > /tmp/legado_dao_smoke.log 2>&1
DAO_EXIT=$?
if [ $DAO_EXIT -eq 0 ]; then
  ok "DAO 冒烟全部通过（$(grep -c '✅' /tmp/legado_dao_smoke.log) 项断言）"
else
  bad "DAO 冒烟失败（exit=$DAO_EXIT）"
  grep "❌" /tmp/legado_dao_smoke.log | head -10
fi

# ---------- 5. 停止服务 ----------
echo "== 5. 停止服务 =="
if [ -n "${SERVER_PID:-}" ]; then kill "$SERVER_PID" 2>/dev/null; echo "  killed server pid=$SERVER_PID"; fi
sleep 1
# 兜底：精确 kill MainKt 进程（不误伤）
MAIN_PID=$(pgrep -f "io.legado.desktop.MainKt" | head -1)
if [ -n "$MAIN_PID" ]; then kill "$MAIN_PID" 2>/dev/null; echo "  killed MainKt pid=$MAIN_PID"; fi
echo "  清理完成"

# ---------- 汇总 ----------
echo ""
echo "========== 测试汇总 =========="
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
[ $FAIL -eq 0 ] && echo "  ✅ 全部通过" || echo "  ❌ 有失败项"
exit $FAIL
