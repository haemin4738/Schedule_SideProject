# Lifelog 프로젝트

일정관리 기반 라이프로그 서비스. 실사용 + 포트폴리오 겸용.

## 기술 스택

- **언어**: Java 21
- **프레임워크**: Spring Boot 3.3.5
- **빌드 도구**: Gradle (멀티모듈 — api / domain / infrastructure)
- **DB**: MySQL 8, Redis 7
- **인증**: JWT (access 15분 / refresh 7일) + OAuth2
- **실시간**: SSE
- **프론트**: React (Vite), Flutter (iOS)
- **컨테이너**: Docker / Docker Compose
- **CI/CD**: GitHub Actions

## 빌드 및 실행

```bash
# 인프라 실행 (MySQL, Redis)
docker compose up -d

# 빌드
cd backend && ./gradlew build

# 테스트 전체 실행
./gradlew test

# 단일 테스트 실행
./gradlew test --tests "com.lifelog.패키지.테스트클래스"

# 앱 실행 (로컬 프로파일 필수)
./gradlew :api:bootRun --args='--spring.profiles.active=local'

# 인프라 종료
docker compose down
```

## 프로젝트 구조

```
backend/
├── api/            # controllers, DTOs, Security/Swagger config
├── domain/         # entity, repository 인터페이스, domain service, event
└── infrastructure/ # JPA 구현체, Redis, OAuth2 client

frontend/           # React (Vite) — Phase 1
mobile/             # Flutter (iOS) — Phase 1
docs/
└── api-spec.yaml   # OpenAPI 3.0 스펙
docker-compose.yml
```

의존 방향: `api → domain ← infrastructure`  
베이스 패키지: `com.lifelog`

## 환경 설정

민감한 값(DB 비밀번호, JWT secret 등)은 루트의 `.env` 파일로 관리.  
`.env`는 gitignore됨 — **절대 커밋하지 않는다**.  
`.env.example`을 복사해서 사용:

```bash
cp .env.example .env
# 값 수정 후 docker compose up -d
```

환경별 Spring 설정은 `application-{profile}.yml`로 분리.  
로컬 실행 시 반드시 `--spring.profiles.active=local` 적용.

## API 스펙

`docs/api-spec.yaml` — OpenAPI 3.0  
로컬 Swagger UI: http://localhost:8080/swagger-ui.html

모든 API 응답 envelope:

```json
{ "success": true, "data": {}, "error": null }
{ "success": true, "data": [], "error": null, "meta": { "page": 0, "size": 20, "total": 100, "totalPages": 5 } }
```

## Git 브랜치 전략

- 기능 추가/수정은 **반드시 새 브랜치**에서 작업 — 직접 main push 금지
- 작업 완료 후 PR 생성
- 브랜치 네이밍: `feat/기능명`, `fix/수정내용`

### 커밋 타입

| 타입 | 설명 |
|------|------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 리팩토링 |
| style | 코드 스타일 변경 |
| docs | 문서 수정 |
| test | 테스트 추가/수정 |
| chore | 설정/빌드 등 기타 변경 |

커밋 메시지 형식: `feat: 로그인 기능 구현`

## 도메인 구조

- `events` — 코어 (Table Per Type 기반)
- `job_applications` — Phase 3 확장
- `expenses` — Phase 4 확장

## Phase 순서

0. ✅ 프로젝트 세팅 (API 스펙, 멀티모듈, CI, Docker)
1. 일정관리 코어 + React/Flutter 동시 연동
2. 실시간성 + 캐싱 (Redis, SSE)
3. 구직활동 관리 확장
4. 가계부 확장
5. CQRS + Elasticsearch + Kafka
6. 배포 + n8n 자동화 (노트북 서버 + 외장 SSD)
