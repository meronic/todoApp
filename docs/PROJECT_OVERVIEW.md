# TaskFlow 구조 문서

이 문서는 현재 TaskFlow 프로젝트를 유지보수할 때 빠르게 같이 보기 위한 기준 문서입니다.

## 한 줄 요약

TaskFlow는 Spring Boot 백엔드와 Vue 3 프론트엔드가 같은 저장소 안에 분리되어 있는 Todo 관리 웹 서비스입니다.

앞으로 화면 개발과 유지보수 기준은 Vue 3 프론트엔드입니다. Thymeleaf 화면과 서버 렌더링 컨트롤러는 제거했고, 새 기능은 Vue 화면과 REST API 기준으로 작업합니다.

## 최근 정리 내용

- 깨진 한글 문자열을 정상 문구로 정리했습니다.
- Thymeleaf 의존성, 서버 렌더링 컨트롤러, 서버 렌더링 템플릿을 제거했습니다.
- Todo API 테스트를 생성, 조회, 빈 제목 검증, 제목 수정, 토글, 삭제, 없는 id 처리까지 확장했습니다.
- 없는 Todo id 요청은 `TodoNotFoundException`과 `GlobalExceptionHandler`를 통해 404 JSON 응답을 반환합니다.
- Vue API 주소는 `VITE_API_BASE_URL` 환경 변수로 바꿀 수 있습니다.

- 백엔드: Spring Boot 3.5.14, Java 17, Maven
- 프론트엔드: Vue 3, Vite
- 데이터 접근: Spring Data JPA
- 현재 DB 설정: 별도 설정 없음. `pom.xml`에 H2 런타임 의존성이 있으므로 개발/테스트용 인메모리 DB로 동작하는 형태입니다.

## 최상위 구조

```text
TaskFlow/
├─ pom.xml
├─ src/
│  ├─ main/
│  │  ├─ java/com/youmh/taskflow/
│  │  │  ├─ TaskFlowApplication.java
│  │  │  ├─ config/
│  │  │  ├─ controller/
│  │  │  ├─ dto/
│  │  │  ├─ entity/
│  │  │  ├─ repository/
│  │  │  └─ service/
│  │  └─ resources/
│  │     └─ application.properties
│  └─ test/
│     └─ java/com/youmh/taskflow/
├─ frontend/
│  ├─ package.json
│  ├─ vite.config.js
│  └─ src/
│     ├─ App.vue
│     ├─ main.js
│     └─ style.css
└─ docs/
   └─ PROJECT_OVERVIEW.md
```

`target/`, `frontend/dist/`, `frontend/node_modules/`는 빌드 또는 설치 결과물입니다. 일반적으로 직접 수정하지 않습니다.

## 백엔드 구조

### 실행 진입점

파일: `src/main/java/com/youmh/taskflow/TaskFlowApplication.java`

Spring Boot 애플리케이션의 시작점입니다. `main()`에서 `SpringApplication.run()`을 호출합니다.

### 도메인 엔티티

파일: `src/main/java/com/youmh/taskflow/entity/Todo.java`

현재 Todo 데이터 모델입니다.

```text
Todo
├─ id: Long
├─ title: String
└─ completed: boolean
```

`@Entity`로 JPA 엔티티이며, `id`는 `GenerationType.IDENTITY`로 자동 생성됩니다. Lombok의 `@Getter`, `@Setter`를 사용합니다.

### Repository

파일: `src/main/java/com/youmh/taskflow/repository/TodoRepository.java`

`JpaRepository<Todo, Long>`를 상속합니다. 기본 CRUD 기능은 Spring Data JPA가 제공합니다.

현재 별도 쿼리 메서드는 없습니다.

### Service

파일: `src/main/java/com/youmh/taskflow/service/TodoService.java`

Todo 관련 비즈니스 로직이 모여 있습니다.

- `findAll()`: 전체 Todo 조회
- `create(String title)`: 제목을 trim한 뒤 새 Todo 생성
- `updateTitle(Long id, String title)`: 기존 Todo 제목 수정
- `toggleCompleted(Long id)`: 완료 상태 반전
- `delete(Long id)`: Todo 삭제

유지보수 기준:

- Todo 생성/수정 규칙은 우선 이 파일에서 관리합니다.
- REST API 컨트롤러가 이 서비스를 사용합니다.
- `updateTitle()`, `toggleCompleted()`, `delete()`는 내부에서 Todo id를 먼저 조회합니다.
- 없는 id는 `TodoNotFoundException`으로 처리되어 404 JSON 응답으로 변환됩니다.

### REST API Controller

파일: `src/main/java/com/youmh/taskflow/controller/TodoApiController.java`

Vue 프론트엔드가 호출하는 JSON API입니다.

| Method | Path | 역할 |
| --- | --- | --- |
| GET | `/api/todos` | Todo 목록 조회 |
| POST | `/api/todos` | Todo 생성 |
| PUT | `/api/todos/{id}` | Todo 제목 수정 |
| PUT | `/api/todos/{id}/toggle` | Todo 완료 상태 변경 |
| DELETE | `/api/todos/{id}` | Todo 삭제 |

생성 요청은 `TodoCreateDto`를 `@Valid @RequestBody`로 받습니다.

### DTO

파일: `src/main/java/com/youmh/taskflow/dto/TodoCreateDto.java`

Todo 생성 요청용 DTO입니다.

- `title`: `@NotBlank` 검증 적용

현재는 생성 요청만 DTO를 사용하고, 응답은 `Todo` 엔티티를 그대로 반환합니다.

### 예외 처리

파일:

- `src/main/java/com/youmh/taskflow/exception/TodoNotFoundException.java`
- `src/main/java/com/youmh/taskflow/exception/GlobalExceptionHandler.java`

없는 Todo id로 토글 또는 삭제를 요청하면 `TodoService`가 `TodoNotFoundException`을 던집니다.

`GlobalExceptionHandler`는 이 예외를 HTTP 404 응답으로 바꾸고, 아래 형태의 JSON을 반환합니다.

```json
{
  "message": "Todo를 찾을 수 없습니다. id=999"
}
```

### CORS 설정

파일: `src/main/java/com/youmh/taskflow/config/WebConfig.java`

Vue 개발 서버에서 백엔드 API를 호출할 수 있도록 CORS를 허용합니다.

- 허용 origin: `http://localhost:5173`, `http://localhost:5174`
- 허용 method: `GET`, `POST`, `PUT`, `DELETE`
- 적용 경로: `/api/**`

`frontend/vite.config.js`의 현재 dev server port는 `5174`입니다.

## 프론트엔드 구조

### Vite 설정

파일: `frontend/vite.config.js`

Vue 플러그인을 사용하고, 개발 서버 포트는 `5174`로 고정되어 있습니다.

### 진입점

파일: `frontend/src/main.js`

Vue 앱을 생성하고 `#app`에 마운트합니다. 전역 CSS로 `style.css`를 import합니다.

### 메인 컴포넌트

파일: `frontend/src/App.vue`

현재 프론트엔드의 핵심 화면과 API 호출 로직이 모두 들어 있습니다.

상태:

- `todos`: Todo 목록
- `title`: 입력 중인 Todo 제목
- `loading`: 목록 로딩 상태
- `errorMessage`: 조회 실패 메시지
- `editingTodoId`: 현재 수정 중인 Todo id
- `editingTitle`: 수정 입력값

API 함수:

- `fetchTodos()`: `GET /api/todos`
- `createTodo()`: `POST /api/todos`
- `updateTodo(todo)`: `PUT /api/todos/{id}`
- `toggleTodo(todo)`: `PUT /api/todos/{id}/toggle`
- `deleteTodo(todo)`: `DELETE /api/todos/{id}`

수정 UI 흐름:

- 일반 상태: 체크박스, 클릭 가능한 카드, 삭제 버튼 표시
- 수정 상태: 해당 Todo만 입력창, 저장 버튼, 취소 버튼 표시
- 저장 성공 후 수정 상태를 닫고 해당 Todo만 로컬 상태에서 갱신

`API_BASE_URL`은 `VITE_API_BASE_URL` 환경 변수를 먼저 사용하고, 값이 없으면 `http://localhost:8080/api/todos`를 기본값으로 사용합니다.

예시는 `frontend/.env.example`에 있습니다.

### 스타일

파일: `frontend/src/style.css`

단일 페이지 Todo 화면의 전역 스타일입니다. 카드형 패널 없이 중앙 정렬된 목록 UI를 구성합니다.

## 요청 흐름

### Vue에서 Todo 목록 조회

```text
브라우저
→ frontend/src/App.vue fetchTodos()
→ GET http://localhost:8080/api/todos
→ TodoApiController.findAll()
→ TodoService.findAll()
→ TodoRepository.findAll()
→ JSON 응답
```

### Vue에서 Todo 생성

```text
브라우저
→ createTodo()
→ POST /api/todos { "title": "..." }
→ TodoApiController.create()
→ TodoCreateDto @NotBlank 검증
→ TodoService.create()
→ TodoRepository.save()
→ 생성된 Todo JSON 응답
→ 생성된 Todo를 로컬 목록에 추가
```

### Vue에서 Todo 제목 수정

```text
브라우저
→ Todo 카드 클릭
→ startEditing(todo)
→ editingTodoId, editingTitle 설정
→ updateTodo(todo)
→ PUT /api/todos/{id} { "title": "..." }
→ TodoApiController.updateTitle()
→ TodoCreateDto @NotBlank 검증
→ TodoService.updateTitle()
→ TodoRepository.findById()
→ title 값 변경
→ TodoRepository.save()
→ 수정된 Todo를 로컬 목록에서 교체
```

### Vue에서 완료 상태 변경

```text
브라우저
→ toggleTodo(todo)
→ PUT /api/todos/{id}/toggle
→ TodoApiController.toggleCompleted()
→ TodoService.toggleCompleted()
→ TodoRepository.findById()
→ completed 값 반전
→ TodoRepository.save()
→ 변경된 Todo를 로컬 목록에서 교체
```

### Vue에서 삭제

```text
브라우저
→ deleteTodo(todo)
→ DELETE /api/todos/{id}
→ TodoApiController.delete()
→ TodoService.delete()
→ TodoRepository.deleteById()
→ 삭제된 Todo를 로컬 목록에서 제거
```

## 실행 방법

백엔드:

```powershell
.\mvnw spring-boot:run
```

기본 포트는 별도 설정이 없으므로 Spring Boot 기본값인 `8080`입니다.

프론트엔드:

```powershell
cd frontend
npm install
npm run dev
```

Vite 개발 서버는 `http://localhost:5174`에서 실행됩니다.

테스트:

```powershell
.\mvnw test
```

프론트엔드 빌드:

```powershell
cd frontend
npm run build
```

## 테스트 구조

파일: `src/test/java/com/youmh/taskflow/TaskFlowApplicationTests.java`

현재 테스트는 Spring Boot 통합 테스트입니다.

- 애플리케이션 컨텍스트 로딩 확인
- MockMvc로 `/api/todos` 생성 및 조회 확인
- 빈 제목 생성 실패 확인
- Todo 제목 수정 확인
- 빈 제목 수정 실패 확인
- Todo 완료 상태 토글 확인
- Todo 삭제 확인
- 없는 id 요청 시 404 응답 확인

테스트 실행:

```powershell
.\mvnw test
```

## 현재 확인된 유지보수 포인트

### 1. Vue와 REST API가 기준임

현재 화면 기준은 Vue입니다.

- 새 기능은 Vue 화면과 `/api/**` REST API에 추가합니다.
- 서버 렌더링용 Thymeleaf 코드는 제거했습니다.
- 백엔드는 화면을 렌더링하지 않고 JSON API를 제공합니다.

### 2. API URL은 환경 변수로 바꿀 수 있음

`frontend/src/App.vue`는 아래 값을 사용합니다.

```js
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/todos'
```

개발 환경에서는 기본값으로 충분합니다. 다른 백엔드 주소를 쓰고 싶으면 `frontend/.env`를 만들고 `frontend/.env.example`을 참고하면 됩니다.

### 3. 프론트 빌드 검증 이슈

`npm run build` 실행 시 현재 환경에서 `esbuild` 프로세스 생성이 `EPERM`으로 실패했습니다.

소스 코드 변경보다는 실행 환경의 권한 문제로 보입니다. 로컬 터미널에서 같은 명령을 다시 실행해 확인하는 것이 좋습니다.

### 4. 예외 응답 정책

없는 Todo id에 대해서는 404 JSON 응답을 반환합니다.

현재는 `TodoNotFoundException`만 처리합니다. 다른 예외는 아직 공통 응답 형식을 만들지 않았습니다.

### 5. 테스트 범위

현재 테스트는 기본 Todo API 흐름과 제목 수정 기능을 다룹니다.

추가로 학습하기 좋은 다음 주제는 검색, 정렬, 완료/미완료 필터입니다.

## 기능을 추가할 때 볼 파일

Todo 필드 추가:

- `Todo.java`
- 필요 시 `TodoCreateDto.java`
- `TodoService.java`
- `TodoApiController.java`
- `frontend/src/App.vue`
- 테스트 파일

API 추가:

- `TodoApiController.java`
- `TodoService.java`
- 필요 시 `TodoRepository.java`
- 테스트 파일

화면 수정:

- `frontend/src/App.vue`
- `frontend/src/style.css`

개발 서버/CORS 수정:

- `frontend/vite.config.js`
- `WebConfig.java`

DB 설정 변경:

- `src/main/resources/application.properties`
- `pom.xml`

## 같이 작업할 때의 기준

이 프로젝트에서는 변경 범위를 작게 유지하는 것이 좋습니다.

새 작업을 할 때는 보통 아래 순서로 진행합니다.

1. 요구사항을 API, 서비스 로직, 화면 중 어디에 해당하는지 나눕니다.
2. 해당 파일만 수정합니다.
3. 백엔드 변경이면 `.\mvnw test`로 확인합니다.
4. 프론트엔드 변경이면 `npm run build`로 확인합니다.
5. 화면 동작이 중요하면 백엔드와 프론트엔드를 같이 띄워 브라우저에서 확인합니다.

## 검증 로그

- 백엔드 테스트는 `.\mvnw test`로 검증합니다.
- 현재 프론트 `npm run build`는 소스 오류가 아니라 `esbuild spawn EPERM` 권한 문제로 실패합니다.
- 프론트 dev 서버와 브라우저 시각 검증은 오래 걸려 중단했습니다. UI 변경 시 사용자가 실행 중인 화면에서 직접 확인하는 방식으로 진행합니다.

## 그룹 기능

Todo는 4개 그룹 중 하나에 속합니다.

- `TODAY`: 오늘 바로 할 일
- `NEXT`: 다음에 할 일
- `LATER`: 급하지 않은 일
- `UNCATEGORIZED`: 미분류

새 Todo의 기본 그룹은 `UNCATEGORIZED`입니다.

백엔드 변경:

- `TodoGroup` enum을 사용합니다.
- `Todo` 엔티티에 `groupType` 필드를 추가했습니다.
- `GET /api/todos`는 최신 Todo가 위에 오도록 `id DESC` 순서로 반환합니다.
- `PUT /api/todos/{id}/group?groupType=TODAY` 형식으로 그룹을 변경합니다.

프론트 변경:

- 상단에서 `전체` / `그룹` 보기를 전환합니다.
- `전체`는 모든 Todo를 최신순으로 보여줍니다.
- `그룹`은 4개 섹션으로 Todo를 나눠 보여줍니다.
- `그룹` 보기에서 Todo 카드를 다른 그룹 섹션으로 드래그 앤 드롭해 그룹을 바꿀 수 있습니다.
- `전체` 보기에서는 Todo 카드에 현재 그룹 배지를 표시합니다.
- PC 화면 기준으로 `그룹` 보기는 2x2 보드 레이아웃을 사용합니다.
- 각 그룹 섹션은 내부 스크롤을 사용해 페이지 전체 스크롤이 과도하게 길어지지 않도록 했습니다.
- 그룹별 색상 테마는 `TODAY=red`, `NEXT=blue`, `LATER=yellow`, `UNCATEGORIZED=green`입니다.
- 그룹 변경 후 전체 목록을 다시 불러오지 않고 해당 Todo만 로컬 상태에서 교체합니다.

## 캘린더 기능

Todo는 선택적으로 날짜를 가질 수 있습니다.

- `dueDate`: `LocalDate`, 기본값은 `null`
- 새 Todo는 날짜 없이 생성됩니다.

백엔드 API:

- `PUT /api/todos/{id}/due-date?dueDate=2026-06-12`: 날짜 지정
- `DELETE /api/todos/{id}/due-date`: 날짜 제거

프론트 동작:

- `전체` 페이지 오른쪽에 14일 캘린더 패널을 표시합니다.
- Todo 카드를 날짜 칸으로 드래그 앤 드롭하면 해당 날짜가 저장됩니다.
- Todo 카드에는 날짜 배지를 표시합니다.
- 날짜를 제거할 수 있는 `날짜 제거` 버튼을 표시합니다.
- 날짜 변경 후 전체 목록을 다시 불러오지 않고 해당 Todo만 로컬 상태에서 교체합니다.

## 최근 변경: 전체 페이지 월간 캘린더

- 전체 페이지의 캘린더를 14일 목록형에서 월간 캘린더 형태로 변경했습니다.
- 캘린더는 현재 월 기준 7열 x 6주 그리드로 표시됩니다.
- 이전 달/다음 달 버튼으로 월을 이동할 수 있습니다.
- Todo 카드를 날짜 칸으로 드래그 앤 드롭하면 해당 날짜가 `dueDate`로 저장됩니다.
- 전체 페이지는 Todo 목록과 캘린더를 1:1 비율의 두 영역으로 배치해 PC 화면에서 균형 있게 보이도록 조정했습니다.
- Todo 목록과 캘린더는 각각 내부 스크롤을 사용해, 데이터가 많아져도 페이지 전체가 과도하게 길어지지 않도록 했습니다.

## 최근 검증 로그: 월간 캘린더 UI 변경

- `frontend/src/App.vue`는 `@vue/compiler-sfc` 파싱 검증을 통과했습니다.
- `./mvnw test`는 13개 테스트 모두 통과했습니다.
- `npm run build`는 기존과 동일하게 `esbuild spawn EPERM` 환경 권한 문제로 실패했습니다.

## 최근 변경: 캘린더 날짜별 Todo 제목 표시

- 월간 캘린더 날짜 칸에 해당 날짜의 Todo 제목을 최대 3개까지 표시합니다.
- 제목이 날짜 칸보다 길면 한 줄 말줄임(`...`)으로 표시되도록 CSS를 조정했습니다.
- 날짜 칸 크기는 유지하고 내부 내용만 줄여 보여주도록 처리했습니다.

## 최근 변경: 완료 Todo 시인성 개선

- 완료된 Todo 카드는 연한 그린 배경과 테두리로 표시해 미완료 카드와 구분되도록 했습니다.
- 캘린더 안의 Todo 제목도 완료 여부에 따라 색상과 취소선이 다르게 보이도록 했습니다.

## 최근 변경: 캘린더 내부 Todo 날짜 이동

- 캘린더 날짜 칸 안에 표시된 Todo 제목을 직접 드래그할 수 있게 했습니다.
- 캘린더 내부의 다른 날짜 칸에 드롭하면 기존 날짜 변경 API를 사용해 `dueDate`가 이동됩니다.
- 드래그 가능한 Todo 제목에는 grab 커서를 적용했습니다.

## 최근 변경: 캘린더 Todo 그룹 컬러 표시

- 캘린더 날짜 칸 안의 Todo 제목에 그룹별 테마 색상을 적용했습니다.
- `TODAY=red`, `NEXT=blue`, `LATER=yellow`, `UNCATEGORIZED=green` 색 체계를 그룹보드와 동일하게 사용합니다.
- 완료된 캘린더 Todo는 그룹 색을 유지하면서 투명도와 취소선으로 구분합니다.

## 최근 변경: 캘린더 5주 표시 조정

- 월간 캘린더 표시 셀을 6주(42칸)에서 5주(35칸) 기준으로 줄였습니다.
- 캘린더 그리드 행도 5행으로 조정했습니다.
- 전체 페이지의 Todo 목록과 캘린더 높이를 5주 달력에 맞춰 더 컴팩트하게 조정했습니다.

## 최근 변경: 캘린더 6주 표시 복구

- 직전 5주 캘린더 조정을 되돌리고 월간 캘린더를 다시 6주(42칸) 기준으로 복구했습니다.
- Todo 목록과 캘린더 높이도 이전 6주 달력 기준으로 되돌렸습니다.

## 최근 변경: Todo 카드 날짜 UI 단순화

- 날짜가 없는 Todo 카드에서는 `날짜 없음` 배지를 표시하지 않도록 변경했습니다.
- 기본 카드 화면에서 `날짜 제거` 버튼을 숨겨 UI를 단순하게 정리했습니다.
- 날짜 제거 API 함수는 코드에 유지해 이후 필요하면 다시 UI를 붙일 수 있습니다.

## 최근 변경: 그룹보드 날짜 표시와 삭제 아이콘

- 그룹보드 Todo 카드에도 날짜가 있는 경우 날짜 배지를 표시하도록 변경했습니다.
- 전체 목록과 그룹보드의 삭제 버튼을 텍스트 `삭제` 대신 작은 아이콘 버튼으로 변경했습니다.
- 삭제 버튼에는 `aria-label`과 `title`을 유지해 의미를 보존했습니다.

## 최근 변경: 그룹보드 진행 중/완료 섹션 분리

- 그룹보드의 각 그룹 내부를 `진행 중`과 `완료` 섹션으로 나눴습니다.
- 완료 섹션은 클릭해서 접고 펼칠 수 있게 했습니다.
- 기존 그룹 이동, 완료 토글, 날짜 배지, 삭제 아이콘 기능은 유지했습니다.

## 최근 변경: 그룹보드 날짜 소분류와 캘린더 빠른 추가

- 그룹보드의 진행 중 Todo를 `기한 초과`, `오늘`, `예정`, `날짜 없음` 기준으로 나눠 표시합니다.
- 캘린더 날짜 칸을 클릭하면 해당 날짜로 Todo를 바로 추가할 수 있는 간단 입력창이 열립니다.
- 캘린더 빠른 추가는 Todo 생성 후 기존 날짜 지정 API를 사용해 `dueDate`를 저장합니다.

## 최근 변경: 캘린더 빠른 추가 입력 위치와 저장 방식 개선

- 캘린더 날짜 클릭 시 입력창이 날짜 칸 내부가 아니라 캘린더 상단에 표시되도록 변경했습니다.
- 빠른 추가 입력 후 Enter를 누르면 해당 날짜로 Todo가 저장됩니다.
- 입력 내용이 있는 상태에서 화면 바깥을 클릭해도 저장되도록 변경했습니다.
- 입력 내용이 비어 있으면 바깥 클릭 시 입력창만 닫힙니다.

## 최근 변경: 캘린더 날짜 칸 내부 빠른 추가 복구

- 캘린더 빠른 추가 입력창을 다시 날짜 칸 내부에 표시하도록 변경했습니다.
- 날짜 칸의 Enter 키 이벤트를 제거해 입력창 submit이 막히지 않도록 했습니다.
- 입력창에서 Enter를 누르거나 입력 후 포커스가 빠져나가면 해당 날짜로 Todo가 저장됩니다.
- blur와 submit이 겹칠 때 중복 저장되지 않도록 저장 중 잠금 값을 추가했습니다.

## 최근 변경: 로고 이동, 그룹 추가 기본값, 카운트/완료 효과 개선

- 사이드바 상단 `TaskFlow` 로고를 클릭하면 전체 메인 화면으로 이동하도록 변경했습니다.
- 전체 화면에서 특정 우선순위 그룹 필터를 보고 있을 때 Todo를 추가하면 해당 그룹으로 바로 등록되도록 했습니다.
- 그룹보드의 그룹별 카운트 숫자를 제목 바로 옆에 배치해 현황을 더 쉽게 볼 수 있게 했습니다.
- Todo 완료 처리 시 짧은 강조 애니메이션이 나타나도록 추가했습니다.

## 최근 변경: 그룹명 변경과 완료 효과 강화

- 그룹 표시명을 `중요하고 급한 일`, `중요하지만 급하지 않은 일`, `부서업무, 개인업무`, `미분류`로 변경했습니다.
- 내부 enum 값은 유지해 기존 데이터와 API는 그대로 동작합니다.
- 완료 처리 시 카드 배경, 테두리, 그림자, 체크 버튼, 제목 색상이 함께 반응하도록 애니메이션을 강화했습니다.

## 최근 변경: 그룹 표시명 단축

- 그룹 표시명을 `즉시 처리`, `중요한 일`, `계획 업무`, `미분류`로 변경했습니다.
- 내부 enum 값은 유지해 기존 Todo 데이터와 API 동작은 그대로 유지됩니다.
- 사이드바, 전체 목록 배지, 그룹보드 제목, 캘린더 칩에 동일하게 적용됩니다.

## 최근 변경: 그룹 필터 화면 제목 동기화

- 전체 페이지에서 특정 그룹 필터를 선택하면 상단 제목이 `전체 할 일`이 아니라 선택한 그룹명으로 표시되도록 변경했습니다.
- 그룹 필터 화면의 우측 배지는 선택된 Todo 개수를 보여주도록 조정했습니다.

## 최근 변경: 사이드바 그룹 필터 중복 제거

- 사이드바 상단에 `전체` 메뉴가 이미 있으므로 그룹 필터 영역의 `전체 그룹` 버튼을 제거했습니다.
- 그룹 필터 영역에는 실제 4개 그룹만 표시됩니다.

## 최근 변경: 그룹보드 드래그 앤 드롭 시인성 강화

- 그룹보드에서 Todo를 드래그하는 동안 각 그룹 영역이 점선 드롭존으로 표시되도록 변경했습니다.
- 현재 마우스가 올라간 그룹은 더 강한 테두리, 그림자, 확대 효과로 강조됩니다.
- 드래그 중 각 그룹에 `여기에 놓기`, 현재 대상에는 `여기로 이동` 안내 문구를 표시합니다.

## 최근 변경: 드래그 앤 드롭 시각 효과 개선

- Todo 드래그 시작 시 마우스 옆에 따라다니는 커스텀 드래그 미리보기 카드를 추가했습니다.
- 드래그 중 원래 카드가 더 흐리게 표시되어 현재 이동 중인 항목을 구분하기 쉽게 했습니다.
- 그룹보드 드롭 대상은 점선 대신 더 강한 배경, 내부 테두리, 그림자, 확대 효과로 강조되도록 변경했습니다.

## 최근 변경: 그룹보드 드롭 안내 문구 제거

- 드래그 중 표시되던 `여기로 이동`, `여기에 놓기` 안내 문구를 제거했습니다.
- 마우스 옆 Todo 미리보기와 그룹 드롭 대상 강조 효과는 유지했습니다.

## 최근 변경: 검색과 우선순위 통계 추가

- 작업영역 상단에 Todo 제목 검색 입력창을 추가했습니다.
- 검색은 전체 목록, 그룹 필터, 그룹보드, 캘린더 표시 Todo에 적용됩니다.
- 우선순위 그룹별 진행 중/완료/전체 통계를 상단 카드로 표시합니다.
- 통계는 검색 결과가 아니라 전체 Todo 기준으로 계산됩니다.

## 최근 변경: 검색과 우선순위 통계 배치 조정

- 우선순위 통계 카드를 작업영역에서 왼쪽 사이드바로 이동했습니다.
- 검색 입력창은 작업영역 상단 제목 오른쪽에 배치해 상단 공간을 활용하도록 조정했습니다.
- 좁은 화면에서는 헤더 도구가 세로로 정렬되도록 반응형 스타일을 보정했습니다.

## 최근 변경: 1차 다크 대시보드 테마 적용

- 전체 앱 배경, 사이드바, 작업영역, 입력창, Todo 카드, 캘린더, 그룹보드를 다크 테마로 변경했습니다.
- 그룹 색상은 다크 배경에서도 잘 보이도록 red/blue/yellow/green 포인트 컬러를 재조정했습니다.
- 완료 상태, 완료 애니메이션, 드래그 미리보기, 드롭 대상 강조 효과도 다크 테마에 맞게 보정했습니다.
- 기능 구조는 변경하지 않고 CSS 중심으로 테마를 적용했습니다.

## 최근 변경: 그룹보드 밀도 조정

- 다크 테마에 맞춰 그룹보드 패널을 더 촘촘한 매트릭스 형태로 조정했습니다.
- 그룹보드의 Todo 카드를 큰 카드형에서 얇은 리스트 행 형태에 가깝게 변경했습니다.
- 체크 버튼, 날짜 배지, 삭제 아이콘 크기를 그룹보드 안에서 더 작게 조정했습니다.

## 최근 변경: 다크 테마 스크롤바 적용

- 전체 앱 스크롤바를 다크 테마에 맞는 얇은 스타일로 변경했습니다.
- 작업영역, Todo 목록, 그룹보드 내부 스크롤에도 동일한 스크롤바 색상이 적용됩니다.

## 최근 변경: DevOps Task Console 테마 보강

- 사이드바에 API/Vite 포트 정보를 보여주는 런타임 패널을 추가했습니다.
- 전체 색상 팔레트를 GitHub/IDE 다크 톤에 가깝게 재정리했습니다.
- 검색창, 추가 입력창, 배지, 통계 숫자에 monospace 폰트를 섞어 개발자 도구 느낌을 강화했습니다.
- Todo 행과 그룹보드를 issue tracker 스타일에 가깝게 다듬었습니다.

## 최근 변경: 다크 테마 재정리와 매트릭스 가독성 개선

- 사이드바의 API/Vite 포트 런타임 패널을 제거했습니다.
- 어색했던 콘솔 테마 컬러를 차분한 다크 업무 UI 팔레트로 재정리했습니다.
- 아이젠하워 매트릭스 패널, 헤더, Todo 행, 날짜 섹션의 대비를 높여 가독성을 개선했습니다.
- 그룹별 색상은 배경 전체가 아니라 헤더/배지/강조 요소 중심으로 사용하도록 조정했습니다.

## 최근 변경: 매트릭스 완료 항목 체크 UI 보정

- 아이젠하워 매트릭스 완료 섹션의 체크 버튼이 네모처럼 보이지 않도록 원형 크기와 border-radius를 명확히 고정했습니다.
- 완료 목록의 항목 투명도와 체크 버튼 색상을 다크 테마에 맞게 조정했습니다.

## 최근 변경: 매트릭스 완료 행 테두리 제거

- 아이젠하워 매트릭스 완료 목록에서 완료 Todo 행의 직사각형 테두리가 보이지 않도록 제거했습니다.
- 완료 행은 투명 배경과 약한 hover 배경만 사용하도록 조정했습니다.
- 완료 체크 버튼 hit area도 둥글게 보정했습니다.

## 최근 변경: 매트릭스 중심 UI 재정리

- 첨부 이미지 방향에 맞춰 아이젠하워 매트릭스 패널을 더 선명한 다크 카드 형태로 재정리했습니다.
- 그룹 헤더에 I/II/III/IV 원형 마커를 추가하고 그룹별 포인트 컬러를 더 명확히 적용했습니다.
- Todo 행의 텍스트 대비, 체크박스, 완료 목록, 배지 스타일을 다시 조정해 가독성을 개선했습니다.
- 완료 항목의 어색한 사각형 테두리를 제거하고 리스트 행 구분선 중심으로 정리했습니다.
