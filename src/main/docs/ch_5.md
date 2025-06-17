# 📕 5단계: 고급 비동기 흐름 제어

---

| 기술           | 개념                                 |
| ------------ | ---------------------------------- |
| `Flow`       | Cold Stream (수요가 있을 때만 생성)         |
| `Channel`    | Hot Stream (메시지 큐 같은 실시간 송수신)      |
| `SharedFlow` | Hot Stream (다수 구독자 지원, 재전송 가능)     |
| `StateFlow`  | 최신 상태 보존형 Hot Stream (LiveData 대체) |


## 📦 ① Flow와 cold stream의 개념

### ✅ Flow의 기본 아이디어
> "데이터 스트림을 비동기 시퀀스처럼 다룰 수 있게 만든 코루틴 기반 스트림 API"

- Cold Stream → 수요(collect)가 발생할 때마다 새로 실행됨
- suspend 기반으로 비동기적으로 데이터를 하나씩 emit 함

```kotlin
fun simpleFlow(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}
```
- emit() : 값을 발행 (중단 가능)
- collect() : 값을 구독 (suspend 함수)

```kotlin
runBlocking {
    simpleFlow().collect { println(it) }
}
```
- 출력:
```kotlin
1
2
3
```

### ✅ 왜 Cold인가?
- collect() 가 호출될 때마다 새로 실행된다.
```kotlin
runBlocking {
    val flow = simpleFlow()
    flow.collect()  // 실행
    flow.collect()  // 다시 실행 (새로운 스트림 시작)
}
```
- 그래서 "계속 만들어내는 공장"이라고 이해하면 정확하다

---

## 📦 ② Channel: 실시간 송수신 핫 스트림 (Hot Stream)
> "코루틴 간 메시지 파이프"
- Producer와 Consumer 간 비동기 버퍼 제공
- SendChannel, ReceiveChannel 로 송수신 분리
- Cold Stream과 달리 → 데이터는 지속적으로 흘러가고 누가 collect 하든 말든 emit됨

### ✅ Channel 기본 예제

```kotlin
val channel = Channel<Int>()

launch {
    for (i in 1..3) {
        channel.send(i)
    }
    channel.close()
}

for (value in channel) {
    println(value)
}
```
- Channel은 내부적으로 Queue + Suspendable API라고 이해하면 쉽다.

### ✅ Channel의 특징

| 특징              | 설명                 |
| --------------- | ------------------ |
| Backpressure 지원 | 버퍼 초과 시 suspend    |
| 생산자/소비자 분리      | 한쪽이 늦어도 suspend 발생 |
| 닫기 가능           | close() 로 종료 신호    |

👉 실전에서는 **"Producer-Consumer 패턴, Queue 대체, 파이프라인 설계"**에 많이 사용

---

## 📦 ③ SharedFlow & StateFlow

<b>이 둘은 Channel과 Flow의 단점을 보완해서 나온 "Hot Stream 고급 버전"</b>

### 🔸 SharedFlow
> Hot Stream + 다수 구독자 + Replay 지원

- 항상 실행중인 스트림
- emit() 호출 시 → 현재 활성화된 모든 구독자에게 전달
- Replay 캐시 가능 → 새 구독자도 과거 값 일부 수신 가능
```kotlin
val sharedFlow = MutableSharedFlow<Int>(replay = 2)
sharedFlow.emit(1)
sharedFlow.emit(2)
sharedFlow.collect { println(it) }
```
👉 실시간 방송, 알림 시스템, MSA Pub-Sub 등에 적합

### 🔸 StateFlow
> 상태를 가지는 Hot Stream
> → 항상 최신 상태 1개를 유지함 (LiveData 대체 가능)

```kotlin
val stateFlow = MutableStateFlow(0)
stateFlow.value = 1
stateFlow.collect { println(it) }
```
👉 UI 상태, 단일 상태 관리, ViewModel 상태 등에 적합

### ✅ Flow / Channel / SharedFlow / StateFlow 핵심 요약

| 기술         | Cold/Hot | 재생성             | 상태 유지 | 주 용도              |
| ---------- | -------- | --------------- | ----- | ----------------- |
| Flow       | Cold     | collect마다 새로 시작 | ❌     | 일반 스트림            |
| Channel    | Hot      | 지속적             | ❌     | Producer-Consumer |
| SharedFlow | Hot      | 유지              | ❌     | 이벤트 브로드캐스트        |
| StateFlow  | Hot      | 유지              | ✅     | 상태관리              |

---

## 📦 각 설계의 한계

| 기술         | 태생적 설계                           |
| ---------- | -------------------------------- |
| Flow       | Cold Stream                      |
| Channel    | Hot Stream (queue 기반)            |
| SharedFlow | Hot Stream (multi-subscriber 지원) |
| StateFlow  | Hot Stream (상태 유지 최적화)           |

### ✅ Flow의 한계 (Cold Stream의 한계)

| 장점                     | 단점                         |
| ---------------------- | -------------------------- |
| 지연 실행 (collect 될 때 시작) | 실시간 push 모델 부적합            |
| 일시적인 데이터 흐름            | 계속 흘러가는 실시간 데이터 부적합        |
| 단일 구독자 패턴              | 여러 subscriber에게 동시에 전달 어려움 |
| 쉽게 중단 가능               | 새로운 subscriber마다 처음부터 재시작  |

<b>👉 그래서 실시간 이벤트 스트림/브로드캐스트에 적합하지 않음</b>

### ✅ Channel의 한계

| 장점                      | 단점                            |
| ----------------------- | ----------------------------- |
| Producer-Consumer에 적합   | 단일 consumer 모델에 최적화됨          |
| 실시간 송수신 가능              | 여러 subscriber 지원 복잡           |
| Buffer, Backpressure 지원 | 캐시/Replay 기능 부재               |
| Coroutine 기반 메시지 큐      | 최근 subscriber가 과거 이벤트를 볼 수 없음 |

<b>👉 결국 Channel은 단일 소비자 큐 기반에 가깝다. 실시간 브로드캐스트에는 부적합.</b>


---

## 📦 왜 SharedFlow & StateFlow가 만들어졌나?
- 이 두 개는 사실 실시간 이벤트 스트림 문제 해결 전용으로 설계되었다.

### ✅ SharedFlow가 만든 개선점
- Cold Flow와 Channel의 단점을 보완 → 진짜 Hot Stream

| 개선 기능           | 설명                     |
| --------------- | ---------------------- |
| 다수 구독자          | 여러 consumer가 동시에 구독 가능 |
| Replay 지원       | 새 구독자도 일부 과거 이벤트 수신 가능 |
| Broadcast 모델    | 이벤트 분배 패턴을 단순화         |
| Buffer + Replay | 동시에 버퍼링 + 재전송 지원       |

<b>👉 Channel + Flow의 장점을 통합하고, 다수 구독자를 위한 안정적 Hot Stream Broadcast 설계</b>

### ✅ StateFlow가 만든 개선점
- SharedFlow의 파생 → 상태 보존 전용 모델

| 개선 기능           | 설명                      |
| --------------- | ----------------------- |
| 항상 하나의 최신 값 유지  | 가장 최근 상태 유지             |
| 구독 시 즉시 현재 값 수신 | UI 상태 관리에 최적화           |
| LiveData 대체 가능  | Android에서도 안정적 상태 관리 가능 |

<b>👉 "상태를 가질 수 있는 SharedFlow"</b>


```text
               ┌──────────────┐
               │    Flow      │  ← cold stream, 단일 구독자
               └──────────────┘
                      ↓ (hot화)
               ┌──────────────┐
               │   Channel    │  ← hot stream, 단일 consumer
               └──────────────┘
                      ↓ (다수 구독자 지원)
               ┌──────────────┐
               │  SharedFlow  │  ← hot stream, multi-consumer, replay 지원
               └──────────────┘
                      ↓ (상태 유지)
               ┌──────────────┐
               │  StateFlow   │  ← 상태 관리 특화 버전
               └──────────────┘

```

---

## 📦 ④ Flow 연산자들 (flowOn, buffer, conflate)

### 🔸 flowOn
> Dispatcher 전환 지점

```kotlin
flow {
    emit(doIO())
}.flowOn(Dispatchers.IO)
```
👉 emit 블록이 IO에서 실행됨

### 🔸 buffer
> 버퍼 사이즈 조정 (Backpressure 튜닝)

```kotlin
flow {
    emit(1)
    emit(2)
}.buffer(10)
```
👉 emit과 collect의 속도 차이를 흡수

- 여기서 buffer(10) 이라는 건 →
  Flow 내부의 emit() 가 최대 10개까지 버퍼에 쌓일 수 있다는 뜻
- collect()가 느려도 → 10개까지는 그냥 emit이 suspend되지 않고 계속 밀어넣을 수 있다.

<b>만약 emit 호출이 buffer 용량을 초과하면 어떻게 되나?</b>
- 초과하는 순간부터 emit() 호출이 suspend된다. 즉, 더 이상 버퍼를 넘칠 수 없다.
- 실제 동작
```text
emit(1) → 버퍼 1/10
emit(2) → 버퍼 2/10
...
emit(10) → 버퍼 10/10 (가득 참)
emit(11) → suspend (collect가 읽어줄 때까지 대기)
```

> buffer를 사용함에 따른 이점

| 장점       | 설명                              |
| -------- | ------------------------------- |
| 스레드 안전   | 버퍼 사이즈 초과로 인한 race condition 없음 |
| OOM 방지   | 무한 버퍼링 방지                       |
| 흐름 제어 자동 | 생산자 속도가 소비자 속도에 맞춰 자동 조절        |


### 🔸 conflate
> 중간 값 스킵하고 최신 값만 수신

```kotlin
flow {
    for (i in 1..100) emit(i)
}.conflate().collect { println(it) }
```
👉 속도차이가 심할 때 과거 값 드롭 → 최신 값 우선 처리

<b>상황</b>
- 생산자가 너무 빠르고
- 소비자가 상대적으로 느릴 때

<b>일반 buffer()만 사용하면</b>
- 버퍼가 가득 차면 → emit() suspend
- 생산자는 대기해야 함 → 시스템 전체 속도가 생산자의 속도보다 소비자의 속도에 종속됨

<b>conflate()를 쓰면</b>
- 과거 값은 버리고 최신 값만 보장
- `emit()`은 거의 suspend되지 않음 → 생산자 자유롭게 계속 실행 가능

<b>유용한 상황</b>
- 이벤트 스트림 중 "최신 값만 중요할 때"

| 대표 사례    | 설명                     |
| -------- | ---------------------- |
| UI 렌더링   | 스크롤, 애니메이션, 위치 등       |
| 실시간 모니터링 | CPU, 메모리, 센서 데이터       |
| 실시간 차트   | 1초에 100개 들어와도 최신 값만 표시 |
