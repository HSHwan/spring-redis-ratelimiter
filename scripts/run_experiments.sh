#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
K6_DIR="$PROJECT_ROOT/k6"
RESULTS_DIR="$PROJECT_ROOT/results"
PYTHON_SCRIPT="$SCRIPT_DIR/generate_final_graphs.py"
REDIS_CONTAINER="rate-limit-redis"

# ----------------------------------------------------------------
# Scenario A
# ----------------------------------------------------------------
echo "----------------------------------------------------------------"
echo "🧪 [1/4] Scenario A 실행..."
k6 run --quiet "$K6_DIR/scenario_user.js"

echo "⏳ 데이터 Scrape 대기 중 (30초)..." 
sleep 30
echo "📊 Scenario A 그래프 생성..."
python "$PYTHON_SCRIPT" --target A

echo "💤 다음 테스트를 위해 쿨다운 중 (90초)..."
sleep 90

# ----------------------------------------------------------------
# Scenario B
# ----------------------------------------------------------------
echo "----------------------------------------------------------------"
echo "🧪 [2/4] Scenario B 실행..."
k6 run --quiet "$K6_DIR/scenario_global.js"

echo "⏳ 데이터 Scrape 대기 중 (30초)..."
sleep 30
echo "📊 Scenario B 그래프 생성..."
python "$PYTHON_SCRIPT" --target B

echo "💤 다음 테스트를 위해 쿨다운 중 (90초)..."
sleep 90

# ----------------------------------------------------------------
# Scenario C
# ----------------------------------------------------------------
echo "----------------------------------------------------------------"
echo "🧪 [3/4] Scenario C 실행..."
k6 run --quiet "$K6_DIR/scenario_dual.js"

echo "⏳ 데이터 Scrape 대기 중 (30초)..."
sleep 30
echo "📊 Scenario C 그래프 생성..."
python "$PYTHON_SCRIPT" --target C

echo "💤 다음 테스트를 위해 쿨다운 중 (90초)..."
sleep 90

# ----------------------------------------------------------------
# 4. Fail-Open Test
# ----------------------------------------------------------------
echo "----------------------------------------------------------------"
echo "🧪 [4/4] Fail-Open 테스트 실행..."

docker start $REDIS_CONTAINER > /dev/null 2>&1
sleep 5 

(
  sleep 30 
  echo "💥 [Chaos] Redis 강제 종료!"
  docker stop $REDIS_CONTAINER
) &

k6 run --quiet "$K6_DIR/scenario_fail_open.js"

echo "🚑 Redis 복구 중..."
docker start $REDIS_CONTAINER > /dev/null 2>&1

echo "⏳ 데이터 Scrape 대기 중 (30초)..."
sleep 30
echo "📊 Fail-Open 그래프 생성..."
python "$PYTHON_SCRIPT" --target FAIL

echo "----------------------------------------------------------------"
echo "✨ 모든 작업 완료! 👉 $RESULTS_DIR"