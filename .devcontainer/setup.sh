#!/bin/bash
set -e

# Claude Code 설치
npm install -g @anthropic-ai/claude-code

# nvm node bin을 PATH에 등록
NODE_BIN=$(npm root -g | sed 's|/lib/node_modules||')/bin
echo "export PATH=\"$NODE_BIN:\$PATH\"" >> ~/.bashrc
echo "export PATH=\"$NODE_BIN:\$PATH\"" >> ~/.zshrc 2>/dev/null || true

# 승인 없이 자율 실행 설정 (devcontainer 안은 격리되어 안전)
mkdir -p ~/.claude
cat > ~/.claude/settings.json << 'EOF'
{
  "autoApproveTools": [
    "Read", "Write", "Edit", "Bash", "Glob", "Grep"
  ]
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
