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


