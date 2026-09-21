#!/bin/bash

# Claude Code 설치
npm install -g @anthropic-ai/claude-code || echo "⚠️ Claude Code 설치 실패"

# nvm node bin PATH 등록 (고정 경로)
NVM_NODE_BIN="/usr/local/share/nvm/versions/node/v20.20.2/bin"
if [ -d "$NVM_NODE_BIN" ]; then
  echo "export PATH=\"$NVM_NODE_BIN:\$PATH\"" >> ~/.bashrc
fi

# 컨테이너 전용 .claude 디렉토리 구성
mkdir -p ~/.claude

# 로그인 인증 파일 (컨테이너 재시작 후에도 로그인 유지)
ln -sf ~/.claude-host/.credentials.json ~/.claude/.credentials.json

# 훅 스크립트 (호스트와 공유)
ln -sf ~/.claude-host/hooks ~/.claude/hooks

# 환경변수 (Discord 토큰 등, 호스트와 공유)
[ -f ~/.claude-host/settings.local.json ] && \
  ln -sf ~/.claude-host/settings.local.json ~/.claude/settings.local.json

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
