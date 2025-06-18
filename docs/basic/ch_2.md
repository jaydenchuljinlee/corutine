# 📗 2단계: 코루틴 빌더 사용하기

---

## ✅ 1️⃣ launch, async, withContext 의 차이

- 이 셋은 **코루틴 빌더**
- 결국 코루틴을 "어떻게 띄울 것인가"를 결정하는 함수들이다.

---

### 🔸 `launch`: fire-and-forget

- **결과를 반환하지 않는** 코루틴 시작
- 보통 **병렬 작업 실행**에 사용

```kotlin
launch {
    println("Doing some work")
}

```
- 반환 타입: `Job`
- 예외: 부모로 전파됨 (Structured Concurrency)
- launch는 결과가 없는 "작업" 이므로 예외가 바로 터져야 함 → 부모에 바로 전달

---

### 🔸 `async`: 값을 반환하는 코루틴

- **결과가 필요한 병렬 작업**에 사용

```kotlin
val result: Deferred<Int> = async {
    delay(1000)
    42
}
println(result.await())

```

- 반환 타입: `Deferred<T>`
- `await()` 호출로 결과를 받아옴
- 예외: await 호출 시 발생

#### ✅ async 의 예외 전파

```kotlin
runBlocking {
    val deferred = async {
        throw RuntimeException("async fail")
    }
    println("still running")  // 여기까지는 정상 실행됨
    deferred.await()          // 이 시점에서 예외가 발생
}
```
- async는 예외를 Deferred에 저장
- 나중에 await() 호출 시 예외 발생
- async는 결과를 기다리는 "미래값" 이므로 → 예외도 결과처럼 담아놨다가 await() 호출 시 꺼내줌

---

### 🔸 `withContext`: 컨텍스트 전환 (suspend 함수 내부에서만 사용)

- **스레드 변경, 디스패처 변경**에 자주 사용됨.
- blocking API 감쌀 때 매우 많이 사용

```kotlin
val result = withContext(Dispatchers.IO) {
    doSomeBlockingIO()
}

```

- 반환 타입: T (결과 바로 반환)
- 기본적으로 suspend 함수 안에서만 호출 가능
- withContext는 launch처럼 예외를 즉시 전파한다.
- withContext 자체는 suspend 함수다

---

| 빌더 | 용도 | 반환 | 예외 전파 |
| --- | --- | --- | --- |
| launch | 병렬 실행 (결과 필요 없음) | Job | 부모로 전파 |
| async | 병렬 실행 (결과 필요함) | Deferred | await 시 발생 |
| withContext | 컨텍스트 전환 | T | 즉시 발생 |

---

## ✅ withContext와 suspend와의 관계

> withContext는 launch처럼 예외를 즉시 전파한다.

### 📌 왜 그런가?
- withContext 자체는 suspend 함수다.
- 내부에서 예외가 발생하면 → 그 즉시 상위 suspend 지점으로 예외가 던져진다.
- 즉:
  - launch: 즉시 부모로 전파
  - async: await() 때까지 보관
  - withContext: 호출 지점으로 바로 예외 전파

## ✅suspend에 대하여

> suspend 함수 자체는 Dispatcher와 전혀 무관하다. Dispatcher 전환은 withContext 를 사용해서 명시적으로 해야 한다.

### 📦 suspend는 "중단 가능"만 의미
- suspend fun 이라고 선언하면
  - "이 함수는 중단 지점이 있을 수 있다"는 컴파일러 힌트를 줄 뿐
  - 어느 스레드에서 실행할지는 전혀 알지 못한다.
- 실제 실행 스레드는 → 호출하는 쪽의 CoroutineContext에 의해 결정됨

```kotlin
suspend fun doSomething() {
    // 여기 안에서는 내가 IO인지, CPU인지 모르고 그냥 실행
}
```

### 📦 Dispatcher 전환은 withContext로 제어

```kotlin
// IO 작업을 할 땐:
suspend fun loadFromDisk(): String = withContext(Dispatchers.IO) {
        // 여기는 IO 스레드풀에서 실행됨
        File("data.txt").readText()
    }

// CPU 작업을 할 땐:
suspend fun calculate(): Int = withContext(Dispatchers.Default) {
    heavyComputation()
}
```

### 📦 왜 이렇게 분리했나?

- 👉 완전히 의도적 설계
  - suspend = 비동기 중단 가능한 점만 표현 (로직의 관점)
  - withContext = 스레드풀 및 스케줄링 제어 (실행 환경 관점)
- 이렇게 설계했기 때문에:
  - 비즈니스 로직과 스레드 관리가 분리됨 → 유지보수 용이
  - 테스트에서 Dispatcher를 교체하기 쉬움
  - 필요할 때만 Dispatcher 전환 → 성능 최적화


---

## ✅ 2️⃣ CoroutineScope 와 Job 구조

### 🔸 CoroutineScope

- **코루틴을 띄우기 위한 컨테이너**
- `launch`, `async`는 반드시 CoroutineScope 내에서 호출됨

```kotlin
scope.launch { ... }
scope.async { ... }

```

- 스코프가 종료되면, 하위 코루틴도 취소됨 → **Structured Concurrency 핵심**

---

### 🔸 Job

- 코루틴의 **생명주기 관리 객체**
- cancel, join, cancelAndJoin 등 제어 가능

```kotlin
val job = scope.launch {
    delay(1000)
}
job.cancel()

```

- `launch` 는 항상 Job 반환
- `async` 는 Deferred (Job + 결과 포함 객체) 반환

---

### 🔸 SupervisorJob (참고)

- 자식 중 하나 실패 → 나머지 자식은 영향 안 받음 (부모도 살아있음)
- supervisorScope 와 연관됨

---

## ✅ 3️⃣ coroutineScope vs supervisorScope

이 부분이 실무에서 매우 중요

---

### 🔸 coroutineScope

- **기본 Structured Concurrency 스코프**
- 자식 중 하나 실패 → 전체 취소됨

```kotlin
suspend fun doWork() = coroutineScope {
    launch { ... }
    launch { ... }
}

```

- 하나가 실패하면 다른 launch들도 취소됨

---

### 🔸 supervisorScope

- 자식 중 하나 실패 → 다른 자식들은 계속 실행됨
- 독립적인 병렬 작업이 필요한 경우 사용

```kotlin
suspend fun doWork() = supervisorScope {
    launch { ... }  // 실패해도 다른 launch는 계속 됨
    launch { ... }
}

```

---

| 스코프 | 특징 |
| --- | --- |
| coroutineScope | 자식 실패 → 전체 취소 |
| supervisorScope | 자식 실패 → 나머지 유지 |

---

## ✅ 간단 정리 그림

```text
CoroutineScope
    ├─ launch → Job
    ├─ async → Deferred
    └─ withContext → 컨텍스트 전환

```

```text
코루틴 스코프
    ├─ coroutineScope : 실패 전파 (엄격)
    └─ supervisorScope : 실패 무시 (유연)

```

## ✅ Spring에서는 왜 CoroutineScope를 기본으로 사용할까??

> 기본이 CoroutineScope인 이유는: 기본값으로 안전하게 실패 전파 하는 것이 더 일반적이기 때문이다.
> <i>SupervisorScope는 예외를 무시하고 계속 돌리겠다는 의도가 명확한 경우에만 사용한다</i>

### ① CoroutineScope (기본 스코프)의 실패 전파 원칙
- CoroutineScope는 기본적으로 구조화된 동시성의 원칙을 따름
  - "자식이 실패하면 부모가 실패하고 전체가 정리된다"

```kotlin
coroutineScope {
    launch { taskA() }
    launch { taskB() } // 여기서 예외 터지면 전체 취소
}
```

- 장점:
  - 예외 발생 시 전체 정리 → 리소스 누수 방지
  - 안전함 → 장애 은폐 없이 바로 상위 스코프로 전파
  - 일반적인 요청 처리, 트랜잭션 등에서 자연스러운 정책

### ② SupervisorScope 가 필요한 경우
- SupervisorScope는 명시적 "부분 실패 허용" 정책이 필요할 때만 사용

```kotlin
supervisorScope {
    launch { taskA() }
    launch { taskB() } // taskB 터져도 taskA 계속 실행됨
}
```

- 장점:
  - 독립적인 작업 병렬 수행
  - 하나의 작업 실패가 전체 실패를 유발하지 않음
- 단점:
  - 실패 은폐 위험 → 실수로 장애가 묻힐 수 있음

### ③ 기본값을 왜 이렇게 설계했나?

| 선택                   | 이유                        |
| -------------------- | ------------------------- |
| CoroutineScope가 기본   | 실패 전파가 기본 → 안정적           |
| SupervisorScope는 선택적 | 실패 무시가 특수 케이스이므로 명시적으로 써라 |

>  결국 Kotlin 코루틴은 "실패는 기본적으로 반드시 잡아라" 철학을 따르고 있는 것


### ④ Kotlin 공식 가이드라인 철학

| 상황                        | 추천              |
| ------------------------- | --------------- |
| **비즈니스 로직, 트랜잭션, 일관성**    | CoroutineScope  |
| **다수의 독립적 병렬 작업**         | SupervisorScope |
| **장애 은폐 원치 않음 (default)** | CoroutineScope  |

### 