#!/bin/bash

# Claude Code 설치
npm install -g @anthropic-ai/claude-code || echo "⚠️ Claude Code 설치 실패"

# nvm node bin PATH 등록 (동적 경로)
NVM_NODE_BIN=$(ls -d /usr/local/share/nvm/versions/node/*/bin 2>/dev/null | sort -V | tail -1)
if [ -n "$NVM_NODE_BIN" ]; then
  echo "export PATH=\"$NVM_NODE_BIN:\$PATH\"" >> ~/.bashrc
fi

# ~/.claude volume 소유권 vscode로 변경 (named volume은 root 소유로 생성됨)
sudo chown -R vscode:vscode ~/.claude

# 호스트 settings.local.json 복사 (Discord 토큰 등 환경변수)
[ -f ~/.claude-host/settings.local.json ] && \
  cp ~/.claude-host/settings.local.json ~/.claude/settings.local.json || true

# 컨테이너 전용 settings.json (Linux 경로 사용)
cat > ~/.claude/settings.json << 'EOF'
{
  "skipDangerousModePermissionPrompt": true,
  "permissions": {
    "deny": ["Read(.env)", "Read(.env.*)"]
  },
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node /home/vscode/.claude/hooks/check-task-summary.js",
            "asyncRewake": true
          }
        ]
      },
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

# git 사용자 설정
git config --global user.name "haemin4738"
git config --global user.email "leeheamin12@gmail.com"

# git 자격증명 — GITHUB_TOKEN으로 자동 인증 (자율 실행 시 프롬프트 방지)
git config --global credential.helper '!f() { echo username=x-token; echo password=$GITHUB_TOKEN; }; f'

# frontend 의존성 설치
if [ -f "frontend/package.json" ]; then
  echo "📦 frontend 패키지 설치 중..."
  cd frontend && npm install && cd ..
fi

# Flutter SDK 설치 (mobile 코드 flutter analyze/test 실행용)
if [ ! -d /usr/local/flutter ]; then
  echo "📱 Flutter SDK 설치 중..."
  sudo git clone --depth 1 -b stable https://github.com/flutter/flutter.git /usr/local/flutter
  sudo chown -R "$(whoami)":"$(whoami)" /usr/local/flutter
fi
echo 'export PATH="/usr/local/flutter/bin:$PATH"' | sudo tee /etc/profile.d/flutter.sh >/dev/null
export PATH="/usr/local/flutter/bin:$PATH"
flutter config --no-analytics >/dev/null 2>&1 || true
if [ -f "mobile/pubspec.yaml" ]; then
  echo "📱 mobile 패키지 설치 중..."
  cd mobile && flutter pub get && cd ..
fi

echo "✅ 세팅 완료!"
echo ""
echo "▶ 인프라 시작:  docker compose up -d"
echo "▶ 백엔드 실행:  cd backend && ./gradlew :api:bootRun --args='--spring.profiles.active=local'"
echo "▶ Claude 자율:  claude --dangerously-skip-permissions"
