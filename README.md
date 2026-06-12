# TaskFlow

Spring Boot와 Vue 3로 만든 Todo 관리 웹 서비스입니다.

Todo를 등록하고 완료 처리할 수 있으며, 아이젠하워 방식의 그룹 보드와 월간 캘린더를 함께 사용해 할 일을 정리할 수 있습니다.

## 주요 기능

- Todo 등록, 수정, 삭제
- 완료 / 미완료 상태 변경
- 최신 Todo가 위에 보이는 정렬
- 4개 그룹 분류
  - 오늘 바로 할 일
  - 그 다음 할 일
  - 급하지 않은 일
  - 미분류
- 그룹보드에서 드래그 앤 드롭으로 그룹 이동
- 전체 페이지에서 월간 캘린더 표시
- Todo 카드를 캘린더 날짜로 드래그 앤 드롭해 날짜 지정
- 캘린더 내부 Todo를 다른 날짜로 드래그 앤 드롭해 일정 이동
- 캘린더에 그룹별 색상 표시
- 완료된 Todo와 미완료 Todo의 시각적 구분

## 기술 스택

### Backend

- Java 17
- Spring Boot 3.5.14
- Spring Web
- Spring Data JPA
- H2 Database
- Lombok
- Maven

### Frontend

- Vue 3
- Vite
- CSS

## 프로젝트 구조

```text
TaskFlow/
├─ src/
│  ├─ main/java/com/youmh/taskflow/
│  │  ├─ config/
│  │  ├─ controller/
│  │  ├─ dto/
│  │  ├─ entity/
│  │  ├─ exception/
│  │  ├─ repository/
│  │  └─ service/
│  └─ test/
├─ frontend/
│  ├─ src/
│  │  ├─ App.vue
│  │  ├─ main.js
│  │  └─ style.css
│  ├─ package.json
│  └─ vite.config.js
├─ docs/
└─ pom.xml
```

## 실행 방법

### 1. 백엔드 실행

```powershell
.\mvnw spring-boot:run
```

백엔드는 기본적으로 아래 주소에서 실행됩니다.

```text
http://localhost:8080
```

### 2. 프론트엔드 실행

```powershell
cd frontend
npm install
npm run dev
```

프론트엔드는 아래 주소에서 실행됩니다.

```text
http://localhost:5174
```

## API 목록

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/todos` | Todo 목록 조회 |
| POST | `/api/todos` | Todo 생성 |
| PUT | `/api/todos/{id}` | Todo 제목 수정 |
| PUT | `/api/todos/{id}/toggle` | 완료 상태 변경 |
| PUT | `/api/todos/{id}/group?groupType=TODAY` | 그룹 변경 |
| PUT | `/api/todos/{id}/due-date?dueDate=2026-06-12` | 날짜 지정 |
| DELETE | `/api/todos/{id}/due-date` | 날짜 제거 |
| DELETE | `/api/todos/{id}` | Todo 삭제 |

## 테스트

백엔드 테스트는 아래 명령어로 실행합니다.

```powershell
.\mvnw test
```

## 현재 데이터베이스

현재는 H2 인메모리 데이터베이스를 사용합니다.

애플리케이션을 재시작하면 저장된 데이터가 초기화될 수 있습니다. 이후 실제 저장이 필요하면 MySQL, PostgreSQL 같은 외부 DB로 변경할 수 있습니다.

## 앞으로 개선할 수 있는 기능

- 사용자 로그인
- 실제 DB 연동
- Todo 검색
- 기간별 필터
- 구글 캘린더 연동
- 배포 환경 구성

## 개발 메모

이 프로젝트는 Spring Boot와 Vue 3를 함께 학습하기 위한 소규모 Todo 서비스입니다.

프론트엔드는 Vue에서 화면과 사용자 상호작용을 담당하고, 백엔드는 REST API로 Todo 데이터를 관리합니다.
