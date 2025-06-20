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

