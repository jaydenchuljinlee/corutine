## 📦 코루틴의 백프레셔와 채널 심화

### 📌 카프카, 레디스 등의 메시지 브로커와 근본적인 차이는 무엇인가?

> 결론부터
- 코루틴의 Channel/Flow 등은 서버 내부의 로직 흐름 제어
- Kafka/Redis 등은 서비스 간 메시지 전달 및 외부 비동기 처리
- 즉, 목적이 다르고, 범위도 다름

### 📌 코루틴 Channel, Flow, Backpressure는 어디서 쓰이는가?

| 역할                           | 설명                             |
| ---------------------------- | ------------------------------ |
| 단일 서버 내 Producer/Consumer 제어 | 예: 하나의 API 핸들러에서 생산/소비 속도 불균형  |
| 비동기 처리 파이프라인                 | 예: 여러 suspend 함수로 이어진 계산 처리 흐름 |
| 내부 상태 전달, UI 상태 브로드캐스트       | Android, Compose, 또는 SSE 서버    |
| 내부 버퍼링, 스로틀링 제어              | 예: DB 저장 시점 조절, 로그 적재 등        |


### ✅ 예시: 코루틴 내부 채널 사용

```kotlin
val channel = Channel<Event>()

launch {
    while (true) {
        val event = channel.receive()
        process(event)
    }
}

launch {
    repeat(1000) {
        channel.send(Event(it))
    }
}
```
- 내부에서 빠르게 생산된 이벤트를 소비자 로직 속도에 맞춰 처리
- <b>외부 시스템과 무관한 내부 처리 흐름에 적합</b>

### ✅ "외부 시스템과 무관한 내부 처리 흐름"의 의미

> 단일 JVM 프로세스 내에서만 이루어지는, 외부 네트워크나 서비스 호출 없이, 내부 모듈 간 비동기 흐름 제어가 필요한 상황

- 네트워크 통신 없음 (Kafka, Redis, HTTP 등 아님)
- 마이크로서비스 간 메시지 교환 아님
- 모든 것이 현재 서버 메모리 내에서 일어남
- "지연 허용", "스케줄링", "속도 조절" 등이 필요할 수 있음

### ✅ 대표 예시

<b>1. 요청 하나에 대한 내부 비동기 흐름 제어</b>

```text
HTTP 요청 → 작업 분기 (코루틴 Channel + Flow)
           → 데이터 수집
           → 결과 조립 후 응답
```
```kotlin
val eventChannel = Channel<MyEvent>()

launch {
    for (event in eventChannel) {
        handle(event)
    }
}

fun doSomething() {
    eventChannel.trySend(MyEvent(...))
}
```
- ✔ 이벤트 핸들링 속도 조절, 내부 백프레셔 목적

<b>2. 서버 상태 추적 및 전파</b>
- 예: 접속 사용자 수 추적, 내부 상태 흐름 알림, 로그 집계 등
```kotlin
val connectionEvents = MutableSharedFlow<ConnectionEvent>()

launch {
    connectionEvents.collect { updateInternalCache(it) }
}
```
- ✔ 상태값 전파, 최신 정보 유지용 (StateFlow/SharedFlow 적합)

<b>3. 비동기 연산 처리 파이프라인</b>
- 예: CSV 업로드 후 비동기 유효성 검증, 가공 → 저장
```kotlin
fileRows.asFlow()
    .buffer()
    .map { validate(it) }
    .filter { it.valid }
    .collect { insertToDb(it) }
```
- ✔ CPU-intensive 병렬 처리, 스로틀링, 순서 유지 등

<b>4. DB 작업과 별개로 내부 통계 수집</b>
- 예: 주문 수 처리 후 메모리 내 통계 처리
```kotlin
val statChannel = Channel<Order>()

launch {
    for (order in statChannel) {
        updateOrderStats(order)
    }
}

fun onOrderCompleted(order: Order) {
    statChannel.trySend(order)
}
```
- ✔ DB와 별개로 처리되며, 외부 시스템은 전혀 관여하지 않음

> 

### 📌 실무에서 코루틴을 쓸 때 의문점, "Flow만으로 충분한데, 왜 Channel, SharedFlow, StateFlow 같은 걸 따로 써야 할까??"

<b>결론부터</b>
- Flow는 단방향 cold stream에 최적화된 모델
- 하지만 실무에서는 hot stream, 브로드캐스트, 상태 보존, 이벤트 버퍼링 같은 기능이 필요하고, 이건 Flow만으로는 못 한다.

### 왜 Flow만으로는 부족한가?
- Flow는 본질적으로 suspend fun을 확장한 cold stream
```kotlin
val f = flow {
    println("collect 되기 전까지 아무것도 실행 안 함")
    emit("A")
    emit("B")
}

f.collect { println(it) }  // 실행 시점에 emit 실행됨
```
- 구독자가 생기기 전까지 아무 일도 일어나지 않음
- 과거 데이터는 모두 무시됨
- 매번 새로 시작됨

<b>⚠ 실무에서 흔히 필요한 패턴들</b>

| 요구 사항                    | Flow로 가능? | 왜 안 되는가?                   |
| ------------------------ | --------- | -------------------------- |
| UI 상태를 계속 유지하고 싶다        | ❌         | Flow는 collect할 때마다 처음부터 시작 |
| 여러 구독자가 동시에 같은 값을 받길 원한다 | ❌         | Flow는 cold → 매 구독마다 새로 시작  |
| 발생한 이벤트를 놓치지 않고 수신하고 싶다  | ❌         | Flow는 과거 이벤트를 저장 안 함       |
| 계속해서 이벤트를 push 하고 싶다     | ❌         | Flow는 `emit` 내부에서만 작동 가능   |
| 코루틴 외부에서 값을 보내고 싶다       | ❌         | `flow {}` 안에서만 emit 가능     |

<b>그래서 Flow 외에 필요한 것들</b>

| 목적                | 도구           | 설명                              |
| ----------------- | ------------ | ------------------------------- |
| 이벤트 push (다대일)    | `Channel`    | 코루틴 사이 직접 통신. Producer가 외부에서 보냄 |
| 상태 유지 + 최신값 전파    | `StateFlow`  | Flow의 최신값 보존 버전. LiveData 대체 가능 |
| 이벤트 브로드캐스트 (1\:N) | `SharedFlow` | 여러 collect 대상에게 동시에 전송 가능       |
| 중복 없는 최신 값 흐름     | `conflate()` | 속도 차이 조절. 하지만 상태 기억 안 함         |

<b>상황 예시 비교</b>

| 실전 상황                        | 적절한 도구                    |
| ---------------------------- | ------------------------- |
| UI에서 현재 상태(로그인 상태 등)를 항상 유지  | `StateFlow`               |
| 여러 consumer가 동시에 실시간 이벤트를 수신 | `SharedFlow`              |
| 내부 모듈 간 이벤트 큐잉               | `Channel`                 |
| 백엔드에서 CSV 파싱 + DB 저장 흐름      | `Flow + buffer()`         |
| Kafka consumer 결과 비동기 가공     | `Channel.consumeAsFlow()` |

### 정리

| 도구           | 특징                        | 대표 용도             |
| ------------ | ------------------------- | ----------------- |
| `Flow`       | cold, 단방향, 수집 시마다 새로 실행   | ETL, 계산 파이프라인     |
| `Channel`    | hot, 외부에서 send 가능, FIFO 큐 | producer-consumer |
| `SharedFlow` | hot, 다대다 브로드캐스트           | 이벤트 알림, 실시간 수신    |
| `StateFlow`  | hot + 상태 보존               | UI 상태, 설정값 전파     |

