#!/bin/bash

# 1. 공통 네트워크 확인 및 생성
# 권한이 없다면 chmod +x 실행
echo "🌐 네트워크 설정을 확인합니다..."
./network/setup-network.sh

# 2. 데이터베이스 계층 실행
echo "📈 데이터베이스 서버를 가동합니다..."
docker compose -f database/db-compose.yml up -d

# 3. 인프라/관리 계층 실행
echo "📈 관리 도구 및 프록시 서버를 가동합니다..."
docker compose -f management/management-compose.yml up -d

# 4. 모니터링 계층 실행
echo "📈 모니터링 서버를 가동합니다..."
docker compose -f monitoring/monitoring-compose.yml up -d

# 5. 애플리케이션 계층 실행
echo "메인 서비스를 가동합니다..."
docker compose -f app/app-compose.yml up -d

echo "✅ 모든 서비스가 성공적으로 배포되었습니다!"
docker ps -a