#!/bin/bash

# Claude Code 설치
npm install -g @anthropic-ai/claude-code || echo "⚠️ Claude Code 설치 실패"

# nvm node bin PATH 등록 (고정 경로)
NVM_NODE_BIN="/usr/local/share/nvm/versions/node/v20.20.2/bin"
if [ -d "$NVM_NODE_BIN" ]; then
  echo "export PATH=\"$NVM_NODE_BIN:\$PATH\"" >> ~/.bashrc
fi

# 호스트 settings.local.json 복사 (Discord 토큰 등 환경변수)
[ -f ~/.claude-host/settings.local.json ] && \
  cp ~/.claude-host/settings.local.json ~/.claude/settings.local.json || true

# 컨테이너 전용 settings.json (Linux 경로 사용)
cat > ~/.claude/settings.json << 'EOF'
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node /home/vscode/.claude/hooks/discord-notify.js",
            "async": true
          }
        ]
      }
    ]
  }
}
EOF

# frontend 의존성 설치
if [ -f "frontend/package.json" ]; then
  echo "📦 frontend 패키지 설치 중..."
  cd frontend && npm install && cd ..
fi

echo "✅ 세팅 완료!"
echo ""
echo "▶ 인프라 시작:  docker compose up -d"
echo "▶ 백엔드 실행:  cd backend && ./gradlew :api:bootRun --args='--spring.profiles.active=local'"
echo "▶ Claude 자율:  claude --dangerously-skip-permissions"
