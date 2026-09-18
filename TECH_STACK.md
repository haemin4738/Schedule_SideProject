# Tech Stack

## Backend
| 항목 | 기술 |
|------|------|
| Framework | Spring Boot 3.x |
| Language | Java 21 |
| Build | Gradle (멀티모듈) |
| DB | MySQL 8.x |
| Cache | Redis |
| Auth | JWT + OAuth2 |
| 실시간 | SSE (Server-Sent Events) |
| 검색/통계 | Elasticsearch |
| 이벤트 | ApplicationEventPublisher → Kafka (추후) |
| API 문서 | springdoc-openapi (Swagger UI) |

## Frontend
| 항목 | 기술 |
|------|------|
| 웹 | React (Vite) |
| 모바일 | Flutter (iOS) |
| 상태관리 | TBD |
| API 통신 | TBD |

## Infrastructure
| 항목 | 기술 |
|------|------|
| 컨테이너 | Docker Compose |
| IaC | Terraform |
| CI/CD | GitHub Actions |
| 배포 환경 | 개인 노트북 (상시 서버) |
| 외부 접근 | DuckDNS + 포트포워딩 → Cloudflare Tunnel (필요 시) |
| 자동화 | n8n (Slack/이메일 알림) |
| 스토리지 | 외장 SSD (Docker 데이터 + DB volume) |

## Architecture
- **패턴**: CQRS + 이벤트 기반
- **도메인**: events(코어) + job_applications, expenses(확장) — Table Per Type
- **Read Model**: Elasticsearch 통합 인덱싱

## Phase 순서
0. 프로젝트 세팅 (API 스펙, 멀티모듈, CI)
1. 일정관리 코어 + React/Flutter 동시 연동
2. 실시간성 + 캐싱 (Redis, SSE)
3. 구직활동 관리 확장
4. 가계부 확장
5. CQRS + Elasticsearch + Kafka
6. 배포 + n8n 자동화
