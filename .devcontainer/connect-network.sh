#!/bin/bash
# devcontainer가 docker-outside-of-docker 방식으로 떠 있어서
# docker compose(mysql/redis)와 기본적으로 다른 네트워크에 있음.
# 매 컨테이너 시작(postStartCommand)마다 같은 네트워크에 재연결해서
# 로컬 통합 테스트/앱 실행 시 lifelog-mysql / lifelog-redis 접근 가능하게 한다.

NETWORK="sideproject_default"
CONTAINER="DevAgentContainer"

if docker network inspect "$NETWORK" >/dev/null 2>&1; then
  if docker network inspect "$NETWORK" -f '{{range .Containers}}{{.Name}} {{end}}' | grep -qw "$CONTAINER"; then
    echo "ℹ️  ${CONTAINER} already connected to ${NETWORK}"
  else
    docker network connect "$NETWORK" "$CONTAINER" \
      && echo "✅ ${CONTAINER} connected to ${NETWORK}"
  fi
else
  echo "⚠️  ${NETWORK} network not found — run 'docker compose up -d' first, then reopen the devcontainer"
fi
