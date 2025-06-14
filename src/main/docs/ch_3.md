# 📙 3단계: 구조화된 동시성 (Structured Concurrency)

## 구조화된 동시성이란?

> "코루틴은 반드시 스코프에 소속되어야 하며, 스코프가 닫히면 그 안의 모든 자식 코루틴도 함께 정리된다."


- 쉽게 말해:
  - 스코프=생명주기
  - 자식은 부모 스코프가 살아있는 동안만 실행 가능
  - 부모 취소 → 자식 취소 → 리소스 누수 최소화

---

## 📦 ① 부모-자식 관계와 자동 취소

```kotlin
fun main() = runBlocking {
    coroutineScope {
        launch {
            delay(1000)
            println("Child 1 done")
        }

        launch {
            delay(2000)
            println("Child 2 done")
        }
    }
    println("Parent done")
}
```

실행 결과:

```text
Child 1 done
Child 2 done
Parent done
```

#### ✅ 부모가 취소되면 자식은?

```kotlin
fun main() = runBlocking {
    val job = launch {
        launch {
            delay(1000)
            println("Child done")
        }
    }
    delay(500)
    job.cancel()  // 부모 취소 → 자식도 취소
    println("Parent canceled")
    delay(1500)
}
```

실행 결과:

```text
Parent canceled
```
- 자식은 실행되지 않는다.

#### ✅ 이게 왜 중요한가?

- 자식이 부모보다 오래 살아남아 스레드를 점유하는 문제 → 방지
- 메모리 누수 → 방지
- job leak → 방지
  - 👉 구조화된 동시성의 궁극적 목표: 리소스 누수 없는 안정성

---

## 📦 ② Job의 cancel / cancelAndJoin

### cancel
- Job 취소 요청 → 자식 코루틴 포함해서 취소 시도
- 즉시 정지되지 않고 취소 요청 신호를 보냄 (Cooperative Cancellation)

```kotlin
val job = launch { ... }
job.cancel()

```

### cancelAndJoin
- 취소 요청 후 → 완전히 종료될 때까지 대기

```kotlin
job.cancelAndJoin()
```
- 실전에서는 보통 cancelAndJoin 사용 → 완전 종료 확인 후 다음 작업 진행 가능

---

## 📦 ③ 예외 처리: try-catch vs CoroutineExceptionHandler

### ✅ try-catch: suspend 함수 내부에서 직접 잡기

```kotlin
launch {
    try {
        mightFail()
    } catch (e: Exception) {
        println("Caught: $e")
    }
}
```
- suspend 함수, withContext 내부에선 항상 try-catch 가능

### ✅ CoroutineExceptionHandler: launch에서 uncaught 예외 잡기
```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    println("Caught: $exception")
}

val scope = CoroutineScope(SupervisorJob() + handler)

scope.launch {
    throw RuntimeException("Boom")
}
```
- launch에서는 uncaught 예외를 핸들링 가능
- async에서는 await() 하지 않는 한 이 핸들러로 전달되지 않음 → await()에서 try-catch로 잡아야 함

---

## 📦 ④ supervisorScope와 SupervisorJob의 차이

| 항목      | supervisorScope | SupervisorJob |
| ------- | --------------- | ------------- |
| 종류      | 빌더 함수 (suspend) | Job 객체        |
| 용도      | 블록 내부에서 스코프 생성  | 상위 스코프 관리     |
| 자식 실패 시 | 다른 자식 영향 없음     | 다른 자식 영향 없음   |
| 취소 시    | 부모 취소하면 전체 취소   | 동일            |


### ✅ 간단 예제 비교

#### supervisorScope:
```kotlin
supervisorScope {
    launch { ... }
    launch { ... }
}
```

#### SupervisorJob:
```kotlin
val scope = CoroutineScope(SupervisorJob())
scope.launch { ... }
```
- SupervisorJob은 scope 전체의 기본 정책으로 적용됨
- supervisorScope는 일시적으로 그 블록 안에서만 적용

---

## 📦 핵심 요약표

| 개념                        | 역할                     |
| ------------------------- | ---------------------- |
| CoroutineScope            | 기본적으로 실패 전파            |
| supervisorScope           | 자식 독립성 유지              |
| Job.cancel()              | 취소 요청                  |
| cancelAndJoin()           | 취소 + 종료 대기             |
| CoroutineExceptionHandler | launch uncaught 예외 핸들러 |


---

## 📙 구조화된 동시성 심화편

### ✅ 취소 전파 / Cooperative Cancellation / CancellationException

| 용어                                   | 개념                           |
| ------------------------------------ | ---------------------------- |
| **취소 전파 (Cancellation Propagation)** | 부모가 취소되면 자식도 자동 취소           |
| **Cooperative Cancellation**         | 자식이 스스로 취소 요청을 감지하고 협조적으로 취소 |
| **CancellationException**            | 취소가 발생했을 때 코루틴이 받는 표준 예외     |

---

### ① 취소 전파 (Cancellation Propagation)

#### ✅ 기본 규칙
- 코루틴은 항상 부모-자식 관계를 가진다
- 부모가 취소되면 → 자식 코루틴 전체에 자동으로 취소 요청이 전파된다

```kotlin
runBlocking {
    val parent = launch {
        launch {
            delay(1000)
            println("Child done")
        }
    }

    delay(100)
    parent.cancel()  // 부모 취소 → 자식도 취소
}
```
- Child done 출력되지 않음 → 자식도 취소됨


### ② Cooperative Cancellation (협조적 취소)

#### ✅ 코루틴은 강제 종료되지 않는다

- 코루틴은 기본적으로 스레드처럼 강제 중단되지 않음
- 대신 취소 요청이 들어왔다는 신호만 받는다
- 이 신호를 보고 스스로 종료해야 한다 → 이게 협조적(Cooperative)

#### ✅ 협조적으로 취소에 참여하는 방법:

| 방법               | 설명                          |
| ---------------- | --------------------------- |
| `suspend` 함수 호출  | 대부분의 suspend 함수는 자동으로 취소 체크 |
| `ensureActive()` | 수동으로 활성 상태 확인               |
| `yield()`        | 취소 체크 + 컨텍스트 양보             |
| `isActive`       | 현재 취소 여부 확인                 |

```kotlin
runBlocking {
    val job = launch {
        repeat(10) { i ->
            delay(500)
            println("Processing $i")
        }
    }
    delay(1300)
    job.cancel()
}
```
- delay()는 suspend 함수 → 내부에서 자동으로 취소 체크 수행
- 그래서 안전하게 중단됨

#### ✅ 하지만 블로킹 코드에서는 협조가 안됨
```kotlin
runBlocking {
    val job = launch {
        repeat(10) { i ->
            Thread.sleep(500)  // ⚠️ 블로킹 → 취소 못함
            println("Processing $i")
        }
    }
    delay(1300)
    job.cancel()
}
```
- Thread.sleep() 은 suspend가 아님 → 취소 요청을 감지하지 못함 → 계속 실행됨
- 이게 실전에서 취소가 안 먹는 이유의 90%

---

### ④ CancellationException

#### ✅ 코루틴이 취소되면 내부적으로 CancellationException 발생
- 취소는 항상 이 예외로 표현됨
- 보통 개발자는 굳이 이걸 캐치하지 않아도 됨 → 자동으로 부모에게 전파됨

```kotlin
runBlocking {
    val job = launch {
        try {
            delay(1000)
        } catch (e: CancellationException) {
            println("Cancelled!")
        }
    }

    delay(100)
    job.cancel()
}
```

#### ✅ 중요한 특징:
- CancellationException 은 정상적인 흐름으로 간주
- 일반적인 예외와 달리 잡지 않아도 예외 로그를 찍지 않음
- 하지만 필요한 경우 try-catch로 감싸서 취소 정리 작업을 할 수 있음 (예: 리소스 해제)

---

### ④ 한눈에 정리 요약

| 개념         | 동작                            |
| ---------- | ----------------------------- |
| 부모가 취소됨    | 모든 자식에 취소 전파                  |
| 자식이 취소됨    | 자신만 취소 (부모에는 영향 없음)           |
| suspend 함수 | 자동으로 취소 체크                    |
| 블로킹 함수     | 취소 감지 못함                      |
| 취소 예외      | 항상 `CancellationException` 발생 |

---

### ⑤ 실전 장애 예시 (이걸 몰라서 터지는 사고들)

| 잘못된 코드                                  | 장애 증상                  |
| --------------------------------------- | ---------------------- |
| Thread.sleep() 남발                       | 취소가 안 먹혀서 서비스가 멈춤      |
| try-catch로 CancellationException 무조건 삼킴 | 취소 신호 무시 → Job leak 발생 |
| 부모 Scope를 무시하고 launch 남발                | 스코프 밖에 살아남아 메모리 누수     |


---

### ⑥ 실전 안전 패턴

- suspend가 아닌 blocking API → 반드시 withContext(Dispatchers.IO) 로 감싸기
- cleanup 필요 시 → try { ... } finally { cleanup }
- 부모 스코프 항상 적절히 관리하기


