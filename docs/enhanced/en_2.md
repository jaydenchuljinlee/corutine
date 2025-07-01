## 📦 ① Dispatcher 제어 원리와 제한

### ✅ 1.1 Dispatcher란?
> <b>Dispatcher는 코루틴이 어떤 스레드 또는 스레드 풀에서 실행될지를 결정하는 구성 요소</b>

Kotlin 코루틴에서 기본 제공 Dispatcher

| Dispatcher            | 설명         | 스레드 수 제한                                                |
| --------------------- | ---------- | ------------------------------------------------------- |
| `Dispatchers.Default` | CPU 연산용    | CPU 코어 수 (`Runtime.getRuntime().availableProcessors()`) |
| `Dispatchers.IO`      | IO 차단 작업용  | 기본적으로 거의 무제한 (64 \* CPU, 필요 시 확장)                       |
| `Dispatchers.Main`    | Android 전용 | UI 스레드 (Android 환경에서만 사용)                               |

기본 정책 요약

| Dispatcher               | 특징                      | 언제 사용                |
| ------------------------ | ----------------------- | -------------------- |
| `Default`                | 병렬성 보장 (코어 수 기반), 계산 작업 | CPU-bound 작업         |
| `IO`                     | 매우 느슨한 제한, blocking 허용  | DB, 파일, 네트워크 등 I/O   |
| `Unconfined`             | 호출자 스레드에서 실행            | 특수 상황 (테스트, 빠른 전환 등) |
| `newSingleThreadContext` | 고립된 스레드                 | 쓰레드 고정이 필요할 때 (비추천)  |

### ✅ 1.2 실무에서 문제가 되는 경우

> 문제 1: Dispatchers.IO 남용

```kotlin
withContext(Dispatchers.IO) {
    repeat(100_000) {
        launch {
            doIO()
        }
    }
}
```
- IO dispatcher는 거의 무제한으로 스레드를 생성하기 때문에
- <b>JVM 스레드 수가 폭증 → GC 및 컨텍스트 스위칭 비용 폭증 → OutOfMemoryError 위험</b>
- CPU-bound에서는 반드시 non-blocking 작업만 수행해야 함

스레드 풀 제한 보기

```kotlin
println(Runtime.getRuntime().availableProcessors()) // Default dispatcher 스레드 수 기준

```

스레드 추적 실습 예시

```kotlin
repeat(1000) {
    GlobalScope.launch(Dispatchers.IO) {
        println("Thread: ${Thread.currentThread().name}")
        delay(1000)
    }
}
```
- 로그 출력 시 DefaultDispatcher-worker-N, IO-Dispatcher-worker-N 스레드가 보임
- IO는 N이 매우 커질 수 있음

---

## 📦 Dispatchers.IO의 스레드 풀 구조

### ✅ “무제한처럼 보이는” 스레드 풀 구조

📌 <b>핵심: IO Dispatcher는 sharedPool 구조를 기반</b>
```kotlin
Dispatchers.IO = LimitedDispatcher(sharedPool, maxPoolSize)
```
- 내부적으로 Blocking I/O에 특화된 스레드 풀을 갖고 있음
- Kotlin 1.6 기준 기본 `maxPoolSize = 64 * CPU 코어 수`
- 코어 수가 8이면 → `64 * 8 = 512개까지 확장 가능`

📌 <b>sharedPool 구조란?</b>

> Kotlin의 기본 Dispatcher들 (Default, IO, Unconfined)은
내부적으로 단일 글로벌 스레드 풀인 CoroutineScheduler (일명 sharedPool) 을 공유한다.

- `Dispatchers.IO` = sharedPool에 붙어 있는 "논리적 Dispatcher"
- `Dispatchers.Default` = sharedPool의 CPU 제한 영역
- `Dispatchers.Unconfined`도 결과적으로 sharedPool로 되돌아감

📌 <b>내부 구조 예 (Kotlin 소스코드 관점)</b>
- Kotlin은 내부적으로 CoroutineScheduler 라는 글로벌 스레드 풀을 유지한다.
```kotlin
val sharedPool = CoroutineScheduler(
    corePoolSize = CPU_COUNT,
    maxPoolSize = CPU_COUNT * 128,
)

// 할당
Dispatchers.Default = LimitedDispatcher(sharedPool, limit = CPU_COUNT)
Dispatchers.IO = UnlimitedDispatcher(sharedPool, max = CPU_COUNT * 64)
```

📌 <b>sharedPool 기반이 의미하는 것</b>
| 항목                                 | 의미                                          |
| ---------------------------------- | ------------------------------------------- |
| 스레드 풀이 여러 Dispatcher가 공유하는 하나      | 스레드 개수를 최소화하면서도 동시성 보장                      |
| Dispatcher끼리 경쟁 가능                 | IO 작업 과잉 시, Default 스레드까지 밀릴 수 있음           |
| 커스텀 Dispatcher 만들면 sharedPool을 벗어남 | `Executors.newFixedThreadPool()` 등은 별도 풀 생성 |

📌 <b>Dispatchers.IO의 최대 할당량은 어디까지인가?</b>

> `Dispatchers.IO`는 기본적으로 `CPU_COUNT * 64` 까지 확장 가능하지만,
결국 <b>전체 shared</b> `CoroutineScheduler`의 `maxPoolSize` <b>(기본: `CPU_COUNT * 128`) 한도 내에서만</b> 동작한다.
- IO 스레드만 <b>512개까지 이론적 확장 가능하지만</b>
- `CoroutineScheduler`의 `maxPoolSize`를 넘을 수는 없다.
- <b>기본 설정에서는 1024개(8코어 시)</b> 이상으로는 절대 확장되지 않음

📌 <b>Dispatchers.IO의 스레드 제한 정책</b>
- Kotlin 코루틴 내부 소스 기준으로 보면
```kotlin
private const val IO_PARALLELISM_DEFAULT = 64

// 즉
val maxIOThreads = CPU_COUNT * 64
```
- 이건 IO Dispatcher만의 최대 병렬 처리 수 (`parallelism`)에 대한 제한
- 이 제한은 논리적 병렬 처리 허용량이지 실제 스레드 수는 아님
  - 실제 스레드는 아래의 <b>CoroutineScheduler</b>가 생성
  - 즉, IO Dispatcher가 요청을 많이 해도 실제 스레드 수는 sharedPool이 판단하여 늘림

📌 <b>CoroutineScheduler는 어떻게 작동할까?</b>
- Kotlin의 모든 기본 Dispatcher들은 내부적으로 이 CoroutineScheduler를 공유한다
```kotlin
CoroutineScheduler(
    corePoolSize = CPU_COUNT,
    maxPoolSize = CPU_COUNT * 128, // = 1024 (for 8-core)
)
```
- 총 스레드 수 제한이 `maxPoolSize`에 걸림
- 이 범위 내에서 <b>Default / IO / 기타 Dispatcher 작업을 함께 수행</b>
- IO Dispatcher는 이 shared pool을 점진적으로 사용함 (요청량 기반으로 늘어남)

📌 결론
| 항목                              | 설명                              |
| ------------------------------- | ------------------------------- |
| IO Dispatcher의 이론적 상한           | `CPU * 64` → 512 (8코어 기준)       |
| 실제 가능한 총 스레드 수                  | `CPU * 128` → 1024              |
| 이 이상 가능 여부                      | ❌ CoroutineScheduler에 의해 제한됨    |
| Default Dispatcher는 이 중 몇 개 사용? | 기본적으로 `CPU` 개수 만큼 (ex. 8개)      |
| 나머지 Thread                      | IO, coroutine resume, 기타 작업이 사용 |


📌 확장 가능하게 만들 수는 없을까?
- Kotlin 자체 코드를 커스터마이징하거나,
- `Dispatchers.IO.limitedParallelism(n)`으로 논리적 제한을 변경하는 건 가능하지만,
- <b>CoroutineScheduler</b>의 `maxPoolSize` <b>자체를 변경</b>하려면 현재로선 <b>내부 코드 수정</b> 또는 <b>커스텀 Dispatcher 구축</b>밖에 없다.