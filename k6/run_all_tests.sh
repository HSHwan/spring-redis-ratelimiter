#!/bin/bash

# 스크립트가 있는 디렉토리(k6)로 이동
cd "$(dirname "$0")" || exit

# 색상 변수
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=================================================${NC}"
echo -e "${GREEN}    🚀 Spring Redis Rate Limiter - Full Test     ${NC}"
echo -e "${GREEN}=================================================${NC}"

# 사전 체크
echo -e "\n${YELLOW}[Check] Checking environment...${NC}"
if ! docker ps | grep -q "rate-limit-redis"; then
    echo -e "${RED}[Error] Redis container is not running!${NC}"
    exit 1
fi
echo "Environment is ready."

# Smoke Test
echo -e "\n${YELLOW}[Step 1/4] Running Smoke Test (Basic Functionality)...${NC}"
k6 run smoke_test.js
echo -e "${GREEN}✔ Smoke Test Completed.${NC}"
sleep 3

# Isolation Test
echo -e "\n${YELLOW}[Step 2/4] Running Isolation Test (User/IP Independence)...${NC}"
k6 run isolation_test.js
echo -e "${GREEN}✔ Isolation Test Completed.${NC}"
sleep 3

# Stress Test
echo -e "\n${YELLOW}[Step 3/4] Running Stress Test (High Load)...${NC}"
echo "Generating massive traffic..."
k6 run stress_test.js
echo -e "${GREEN}✔ Stress Test Completed.${NC}"
sleep 3

# Fail-Open Test (Auto Redis Restart)
echo -e "\n${YELLOW}[Step 4/4] Running Fail-Open Test (Resilience)...${NC}"
echo "⚠️  This test will STOP Redis automatically!"

# k6를 백그라운드(&)로 실행
k6 run fail_open.js &
K6_PID=$!

# k6가 시작되고 부하를 줄 때까지 5초 대기
sleep 5

echo -e "${RED}🛑 [Simulation] STOPPING REDIS CONTAINER... (Fail-Open Trigger)${NC}"
docker stop rate-limit-redis

# Redis가 죽어있는 상태 유지 (Fail-Open 작동 확인 구간)
sleep 10

echo -e "${GREEN}♻️ [Simulation] RESTARTING REDIS CONTAINER... (Recovery)${NC}"
docker start rate-limit-redis

# k6 종료 대기
wait $K6_PID
echo -e "${GREEN}✔ Fail-Open Test Completed.${NC}"

echo -e "\n${GREEN}=================================================${NC}"
echo -e "${GREEN}    🎉 All Tests Finished Successfully!          ${NC}"
echo -e "${GREEN}    📊 Check Grafana at http://localhost:3000    ${NC}"
echo -e "${GREEN}=================================================${NC}"