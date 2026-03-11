#!/bin/bash
echo "🛑 모든 서비스를 종료하고 컨테이너를 삭제합니다..."

#docker compose -f app/app-compose.yml down
docker compose -f management/management-compose.yml down
docker compose -f database/db-compose.yml down

echo "✅ 모든 컨테이너가 정리되었습니다."