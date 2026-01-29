#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
K6_DIR="$PROJECT_ROOT/k6"
RESULTS_DIR="$PROJECT_ROOT/results"

REDIS_CONTAINER="rate-limit-redis"

echo "🚀 경로 설정 완료: $PROJECT_ROOT"
mkdir -p "$RESULTS_DIR"

echo "🧹 기존 결과 파일 정리 중..."
rm -f "$RESULTS_DIR"/*.csv "$RESULTS_DIR"/*.png

# ----------------------------------------------------------------
# Scenario A: User Limit Test
# ----------------------------------------------------------------
echo "🧪 [1/4] Scenario A: User Limit 테스트 실행 중..."
k6 run --quiet --out csv="$RESULTS_DIR/result_user.csv" "$K6_DIR/scenario_user.js"
echo "✅ Scenario A 완료."
sleep 3

# ----------------------------------------------------------------
# Scenario B: Global Limit Test
# ----------------------------------------------------------------
echo "🧪 [2/4] Scenario B: Global Limit 테스트 실행 중..."
k6 run --quiet --out csv="$RESULTS_DIR/result_global.csv" "$K6_DIR/scenario_global.js"
echo "✅ Scenario B 완료."
sleep 3

# ----------------------------------------------------------------
# Scenario C: Dual-Layer Test
# ----------------------------------------------------------------
echo "🧪 [3/4] Scenario C: Dual-Layer 테스트 실행 중..."
k6 run --quiet --out csv="$RESULTS_DIR/result_dual.csv" "$K6_DIR/scenario_dual.js"
echo "✅ Scenario C 완료."
sleep 3

# ----------------------------------------------------------------
# Fail-Open Test
# ----------------------------------------------------------------
echo "🧪 [4/4] Fail-Open (Redis 장애) 테스트 실행 중..."

docker start $REDIS_CONTAINER > /dev/null 2>&1
sleep 2

(
  sleep 20
  echo "💥 Redis 강제 종료!"
  docker stop $REDIS_CONTAINER
) &

k6 run --quiet --out csv="$RESULTS_DIR/result_fail_open.csv" "$K6_DIR/scenario_fail_open.js"

echo "🚑 Redis 복구 중..."
docker start $REDIS_CONTAINER > /dev/null 2>&1
echo "✅ Fail-Open 테스트 완료."

# ----------------------------------------------------------------
# Python 그래프 생성
# ----------------------------------------------------------------
echo "----------------------------------------------------------------"
echo "📊 Python 스크립트를 실행하여 그래프를 생성합니다..."

# 파이썬 스크립트 실행 (스크립트 위치에서 실행)
python3 "$SCRIPT_DIR/generate_graphs.py"

echo "✨ 모든 작업이 완료되었습니다!"
echo "📂 결과 파일 위치: $RESULTS_DIR"