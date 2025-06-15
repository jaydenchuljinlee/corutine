# 📒 4단계: 디스패처와 컨텍스트 이해

---

## ✅ ① Dispatcher 종류

<b>Dispatcher = 코루틴이 실제로 어떤 스레드/스레드풀에서 실행될지를 제어하는 스케줄러</b>

### 🔸 Dispatchers.Default

- CPU 바운드 작업용 기본 Dispatcher
- 내부적으로 CPU core 개수 기반 스레드풀 사용
- 예: 계산, 파싱, 압축

```kotlin
withContext(Dispatchers.Default) { heavyCalculation() }
```
<b>👉 실전 튜닝시 제일 민감한 Dispatcher</b>

### 🔸 Dispatchers.IO
- 블로킹 IO 작업용 Dispatcher
- DB, 파일, 네트워크, HTTP 호출 등
- 내부적으로 스레드 수 제한이 매우 느슨함 (실질적 무제한까지 확장 가능)

```kotlin
withContext(Dispatchers.IO) { jdbcQuery() }
```
<b>👉 스레드 확장이 자유롭지만 남용하면 전체 JVM 스레드 폭증 위험 있음</b>

> Dispatchers.IO는 내부적으로 unbounded (사실상 무제한) 스레드풀로 설계되어 있다.
> 하지만 JVM 시스템 전체가 폭발하지 않도록 몇 가지 보호장치를 두고 있다.

#### ① 기본 원리: Dispatchers.IO = unbounded thread pool

- Kotlin 1.3부터 등장한 이 모델은:
  - Dispatchers.IO → 내부적으로 "공유 IO Thread Pool"
- 이 Thread Pool은 내부적으로 다음으로 만들어짐:
```kotlin
private val IO = LimitedDispatcher(
    CORE_POOL_SIZE,
    SYSTEM_IO_PARALLELISM,
    ... 
)
```

| 이름                      | 값                       |
| ----------------------- | ----------------------- |
| CORE\_POOL\_SIZE        | CPU 코어 수                |
| SYSTEM\_IO\_PARALLELISM | `64 * CPU` (실제로 기본 한계치) |

- 즉, 초기 core 개수는 제한되어 있지만
- 필요하면 최대 CPU * 64까지 늘어날 수 있다.

👉 예를 들어, 8코어 머신에서는 → 512개의 IO 스레드까지 올라갈 수 있음.

#### ② 왜 이렇게 만들었나?

<b>🔸 IO 작업의 특성 때문</b>

- 대부분의 IO는:
  - 네트워크, 파일, DB 호출 → blocking time이 길다
  - CPU는 사용 안 하고 스레드만 대기함
- 그렇기 때문에 스레드 수를 제한하면 오히려 전체 시스템 처리량이 떨어진다.

<b>🔸 스레드풀의 설계 딜레마</b>

| CPU 작업                  | IO 작업                      |
| ----------------------- | -------------------------- |
| 스레드가 많으면 컨텍스트 스위칭 비용 발생 | 스레드가 많아도 대부분 blocking 대기 중 |
| 스레드 수 제한 필요             | 스레드 수 많이 열어도 비교적 괜찮음       |

#### ③ 그래도 무제한이 위험하지 않나?

Kotlin의 보호장치

| 보호장치                                               | 역할            |
| -------------------------------------------------- | ------------- |
| 기본 최대치 `CPU * 64`                                  | 무제한 확장 방지     |
| `kotlinx.coroutines.io.parallelism` 시스템 속성으로 조절 가능 | 튜닝 가능성 제공     |
| 코어 개수 기반 초기 스레드 유지                                 | 과도한 스레드 생성 억제 |

#### ④ 실전에서 문제되는 경우

- 대량의 blocking IO (DB, Redis, 3rd-party HTTP 호출 등) 를 한꺼번에 Dispatchers.IO에 몰아넣으면 →
  JVM 스레드 수가 몇백~천 단위까지 치솟을 수 있음
- 이 경우 다음 현상 발생 가능:
  - 컨텍스트 스위칭 폭증
  - GC 오버헤드 증가
  - JVM 스레드 스택 메모리 고갈
  - 시스템 전체 부하 급상승

#### ⑤ 그래서 실전에서는 이렇게 튜닝한다

<b>✅ 가능한 경우:</b>
- 가능한 IO 라이브러리를 suspend-friendly API로 전환 (Ex: Retrofit Coroutine Adapter, R2DBC, WebClient 등)

<b>✅ unavoidable blocking 이 있을 때:</b>
- 커스텀 Dispatcher 별도 구성 → 스레드풀 독립 관리

```kotlin
val customIoDispatcher = Executors.newFixedThreadPool(100).asCoroutineDispatcher()
```





### 🔸 Dispatchers.Main
- UI 메인 스레드 Dispatcher
- Android, Compose, Swing, JavaFX 등 UI 프레임워크에서 사용

```kotlin
withContext(Dispatchers.Main) { updateUI() }
```
<b>👉 JVM 서버에는 보통 없음 (Android 환경 전용으로 보는 게 실전에서는 더 일반적)</b>

### 🔸 Dispatchers.Unconfined
- 실전에서는 거의 쓰지 마라
- 최초 호출 스레드에서 실행 → 다음 suspend 이후에는 호출한 스코프의 디스패처 따라감
- 실험용 혹은 협력 라이브러리 구현에서만 사용

```kotlin
withContext(Dispatchers.Unconfined) { ... }
```

---

## ✅ ② CoroutineContext 구성 요소

사실 `Dispatcher` 도 `CoroutineContext` 의 한 부분

### 🔸 CoroutineContext란?
- 코루틴이 실행될 때 함께 전달되는 실행 환경 정보의 모음
- 구성 요소:
  - `Dispatcher` → 스레드 제어
  - `Job` → 취소 관리
  - `CoroutineName` → 디버깅, 로깅
  - `CoroutineExceptionHandler` → 예외 핸들링
  - (기타 사용자 정의 요소 추가 가능)

### 🔸 실제 예시
```kotlin
val context = Dispatchers.IO + CoroutineName("UserFetch")
launch(context) {
    println("Running as ${coroutineContext[CoroutineName]}")
}
```
- Context는 사실상 <b>Map 같은 구조로 합쳐서 관리됨</b>

---

## ✅ ③ 커스텀 Dispatcher 구성
<b>커스텀 ExecutorService → Dispatcher로 변환 가능</b>

### <b>예시: 고정 스레드풀 Dispatcher 만들기</b>
```kotlin
val myDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

CoroutineScope(myDispatcher).launch {
    doSomething()
}
```
- ✅ 대규모 CPU 바운드/IO 바운드 튜닝 시 매우 유용

### Dispatcher 종료 주의사항
- 직접 만든 Dispatcher는 반드시 수명 관리 필요
```kotlin
myDispatcher.close()  // 리소스 누수 방지
```

---

## ✅ ④ 실전에서 Dispatcher 분리 기준

| 작업 종류                | Dispatcher 추천                       |
| -------------------- | ----------------------------------- |
| CPU 연산 (파싱, 압축, 계산)  | Dispatchers.Default                 |
| DB, HTTP, File IO    | Dispatchers.IO                      |
| UI 렌더링               | Dispatchers.Main                    |
| 특수 Blocking 작업       | withContext(Dispatchers.IO) 내부에서 처리 |
| 높은 concurrency 제한 필요 | 커스텀 Dispatcher 고려                   |

---

## ✅ ⑤ 실전 장애 흔한 오해

| 실수                  | 결과                      |
| ------------------- | ----------------------- |
| Default에 IO 작업 몰아넣음 | CPU 스레드 블로킹 → 전체 시스템 멈춤 |
| IO에 CPU 바운드 작업 몰아넣음 | 스레드 폭증, GC 압박           |
| 커스텀 Dispatcher 누수   | Executor 살아남아 메모리 리크    |
