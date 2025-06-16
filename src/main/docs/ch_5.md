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

### 🔸 conflate
> 중간 값 스킵하고 최신 값만 수신

```kotlin
flow {
    for (i in 1..100) emit(i)
}.conflate().collect { println(it) }
```
👉 속도차이가 심할 때 과거 값 드롭 → 최신 값 우선 처리


