## 📘 코루틴 기초 이해

### 1️⃣ 코루틴이란?

- **경량 스레드**와 유사한 개념.
- JVM 스레드를 차지하지 않고 **중단 가능한 작업을 순차적 코드처럼 작성** 가능.
- 비동기 처리를 **복잡한 콜백 없이 간결하게** 표현 가능.

> 예: 기존의 콜백 기반 코드

```kotlin
api.call { result ->
    ui.update(result)
}

```

> 코루틴 기반 코드

```kotlin
val result = api.call()
ui.update(result)
```

### 2️⃣ `suspend` 키워드
- **중단 가능한 함수**임을 나타냄.
- `suspend` 함수는 **일반 함수에서 직접 호출 불가**, 반드시 **코루틴 빌더** 내부에서만 호출 가능.

> 예시:

```kotlin
suspend fun getData(): String {
    delay(1000L)
    return "Hello"
}

```

---

### 3️⃣ `runBlocking`: 코루틴을 시작하는 가장 간단한 방법

- 메인 함수나 테스트 환경에서 **최상위 코루틴을 실행**할 때 사용.
- 내부에서 코루틴을 시작하고 **모두 끝날 때까지 블로킹**함.

> 예시:

```kotlin
fun main() = runBlocking {
    println("Start")
    delay(1000L)  // non-blocking delay
    println("End")
}

```

---

### 4️⃣ `delay()` vs `Thread.sleep()`

| 함수 | 동작 방식 | 스레드 점유 여부 | 사용 위치 |
| --- | --- | --- | --- |
| `delay()` | 비동기 일시 정지 | ❌ 점유 안함 | 코루틴 내부 |
| `Thread.sleep()` | 스레드 블로킹 | ✅ 점유함 | 일반 함수에서도 사용 가능 |

---
