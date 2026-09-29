# 소셜 로그인 개발자 콘솔 등록 체크리스트

카카오 / 네이버 / 구글 콘솔에 등록해야 하는 항목입니다. 로컬 개발(`localhost`) 기준이며, 운영 도메인은 Phase 6 배포 때 추가합니다.

## 공통 원칙

- **제공자마다 앱은 하나만 만듭니다.** 카카오·네이버의 회원 ID는 앱 단위로 발급됩니다. 앱을 새로 만들면 기존에 연결된 계정이 전부 끊깁니다. 환경이 늘어나면 redirect URI만 추가합니다.
- **이메일은 필수 동의로 설정합니다.** 인증된 이메일이 없으면 로그인이 거부되도록 설계되어 있습니다.
- **Client Secret은 채팅에 붙여 넣지 말고 루트 `.env`에 직접 넣습니다.** `.env`는 git에 올라가지 않습니다.
- 콘솔 메뉴 이름은 개편으로 조금 다를 수 있습니다. 항목 이름을 기준으로 찾아 주세요.

---

## 1. 카카오 — https://developers.kakao.com

> 메뉴 경로는 카카오 공식 문서(2026-09 확인) 기준입니다. 모두 [앱 관리 페이지](https://developers.kakao.com/console/app)에서 앱을 선택한 뒤 들어갑니다.

- [ ] **앱 만들기**: 앱 관리 페이지 → 앱 추가 (앱 이름 예: Lifelog)
- [ ] **Web 도메인**: [앱] → [제품 링크 관리] → [웹 도메인] → `http://localhost:5173`
- [ ] **REST API 키 설정**: [앱] → [플랫폼 키] → [REST API 키]
  - [ ] [카카오 로그인 리다이렉트 URI]: `http://localhost:5173/oauth/callback/kakao`
  - [ ] [클라이언트 시크릿]: 코드 확인, 활성화 상태 확인
- [ ] **iOS 번들 ID**: [앱] → [플랫폼 키] → [네이티브 앱 키] → `com.lifelog.mobile`
- [ ] **카카오 로그인 활성화**: [카카오 로그인] → [사용 설정] → 상태 ON (OFF면 로그인 시 `KOE004` 에러)
- [ ] **동의항목**: [카카오 로그인] → [동의항목]
  - [ ] 닉네임: 필수 동의
  - [ ] 카카오계정(이메일): 필수 동의
    - ⚠️ 공식 문서상 이메일을 **필수 동의**로 설정하려면 비즈 앱 전환, 신청 자격 확인, 비즈니스 정보 심사가 필요합니다.
    - 필수 동의를 설정할 수 없으면 **선택 동의**로 설정하세요. 사용자가 이메일 제공에 동의하지 않으면 백엔드가 로그인을 거부(400)하고 재동의를 안내합니다.

**확인할 값**

| 값 | 콘솔 위치 | 넣을 곳 |
|---|---|---|
| REST API 키 | [앱] → [플랫폼 키] → [REST API 키] | `.env` → `KAKAO_CLIENT_ID`, `frontend/.env` → `VITE_KAKAO_CLIENT_ID` |
| 클라이언트 시크릿 🔒 | [앱] → [플랫폼 키] → [REST API 키] → [클라이언트 시크릿] | `.env` → `KAKAO_CLIENT_SECRET` |
| 앱 ID (숫자) | 앱 관리 페이지 목록 또는 앱 선택 후 상단 앱 정보 영역 | `.env` → `KAKAO_APP_ID` |
| 네이티브 앱 키 | [앱] → [플랫폼 키] → [네이티브 앱 키] | Flutter용, 나중에 사용 (지금은 메모만) |

---

## 2. 네이버 — https://developers.naver.com

- [ ] **앱 등록**: Application → 애플리케이션 등록 (앱 이름 예: Lifelog)
- [ ] **사용 API**: 네이버 로그인
- [ ] **제공 정보 선택**
  - [ ] 이메일 주소: **필수**
  - [ ] 별명 또는 이름: 필수 (둘 중 하나 이상)
- [ ] **로그인 오픈 API 서비스 환경**: PC 웹 추가
  - [ ] 서비스 URL: `http://localhost:5173`
  - [ ] Callback URL
    - `http://localhost:5173/oauth/callback/naver` (웹용)
    - `http://localhost:8080/api/v1/auth/social/naver/app-callback` (Flutter 앱용)
    - 여러 개를 등록할 수 없으면 웹용만 먼저 넣어 주세요.
- [ ] **멤버관리**: 검수 전 "개발 중" 상태에서는 등록된 계정만 로그인할 수 있습니다. 테스트할 네이버 ID를 등록해 주세요.
- iOS 환경은 추가하지 않아도 됩니다. Flutter도 브라우저 로그인 방식을 씁니다.

**확인할 값**

| 값 | 넣을 곳 |
|---|---|
| Client ID | `.env` → `NAVER_CLIENT_ID`, `frontend/.env` → `VITE_NAVER_CLIENT_ID` |
| Client Secret 🔒 | `.env` → `NAVER_CLIENT_SECRET` |

---

## 3. 구글 — https://console.cloud.google.com

- [ ] **프로젝트 생성** (예: lifelog)
- [ ] **OAuth 동의 화면** (Google Auth Platform)
  - [ ] 대상(User type): 외부
  - [ ] 앱 이름, 지원 이메일 입력
  - [ ] 범위(데이터 액세스): `openid`, `email`, `profile`
  - [ ] 테스트 사용자: 로그인할 구글 계정 추가 (테스트 모드에서는 여기 등록된 계정만 로그인됨)
- [ ] **사용자 인증 정보 → OAuth 클라이언트 ID ①: 웹 애플리케이션**
  - [ ] 승인된 JavaScript 원본: `http://localhost:5173`
  - [ ] 승인된 리디렉션 URI: `http://localhost:5173/oauth/callback/google`
- [ ] **OAuth 클라이언트 ID ②: iOS**
  - [ ] 번들 ID: `com.lifelog.mobile`

**확인할 값**

| 값 | 넣을 곳 |
|---|---|
| 웹 Client ID | `.env` → `GOOGLE_WEB_CLIENT_ID`, `frontend/.env` → `VITE_GOOGLE_CLIENT_ID` |
| 웹 Client Secret 🔒 | `.env` → `GOOGLE_WEB_CLIENT_SECRET` |
| iOS Client ID | `.env` → `GOOGLE_IOS_CLIENT_ID` |

---

## 4. `.env`에 넣을 변수

루트 `.env` (백엔드):

```dotenv
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
KAKAO_APP_ID=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
GOOGLE_WEB_CLIENT_ID=
GOOGLE_WEB_CLIENT_SECRET=
GOOGLE_IOS_CLIENT_ID=
```

`frontend/.env` (공개값만, **Secret 절대 금지**):

```dotenv
VITE_KAKAO_CLIENT_ID=
VITE_NAVER_CLIENT_ID=
VITE_GOOGLE_CLIENT_ID=
```

- 변수 이름은 PR ②에서 `.env.example`에 추가됩니다. 먼저 넣어 둬도 됩니다.
- 백엔드는 `.env`를 자동으로 읽지 않습니다. 실행 전에 `set -a; source .env; set +a`로 불러와야 합니다.

## 5. 등록 후 알려줄 것

- 3개 제공자 등록 완료 여부
- 네이버 Callback URL을 2개 모두 등록할 수 있었는지
- 카카오 이메일 동의항목을 필수로 설정할 수 있었는지 (비즈 앱 전환 여부)

값 자체는 알려줄 필요 없습니다. `.env`에 넣었다고만 알려주세요.
