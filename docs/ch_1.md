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

> suspend 키워드가 없는 메서드를 사용하면 중단 불가능한가?
- ✅ suspend가 없는 메서드는 중단 불가능하다.
- 왜 그런가?
  - 코루틴의 핵심 기능 중 하나가 "중단(suspension)"
  - suspend 키워드는 **컴파일러에게 "이 함수는 중단 지점을 포함할 수 있다"**고 알려준다.
  - suspend가 없는 함수는 일반 함수이므로, 코루틴 내부에서라도 그 자체로는 절대 중단되지 않는다.

> ✅ 예시 비교

```kotlin
fun regularFunction() {
    Thread.sleep(1000L)  // 블로킹 (스레드를 점유)
}

suspend fun suspendFunction() {
    delay(1000L)  // 중단 가능 (스레드를 점유하지 않음)
}
```
- regularFunction()은 스레드를 블로킹 → 코루틴의 장점을 살릴 수 없음.
- suspendFunction()은 중단 → 다른 코루틴이 해당 스레드에서 계속 실행 가능.

> ✅ 보충 개념: 왜 suspend를 써야 할까?
- 컴파일러가 상태 머신(state machine) 으로 코드를 변환해서 중단/재개가 가능하게 만들어줌
- 이 덕분에 스레드 점유 없이 수천, 수만 개의 코루틴을 띄울 수 있음.

> ✅ 상태 머신으로 변환된다는 뜻

#### 1️⃣ 먼저 아주 단순한 suspend 함수
```kotlin
suspend fun myFunction() {
    println("Step 1")
    delay(1000L)
    println("Step 2")
}
```

- 이 함수는 순서대로 실행 되지만,
- delay()를 만났을 때는 일시 중단 되었다가 나중에 다시 이어서 실행되어야 함.

#### 2️⃣ 문제 발생
- 컴퓨터 입장에선 "어디까지 실행했는지"를 어떻게 기억할까?
  → 스택을 계속 쌓으면 메모리 터짐.

#### 3️⃣ 컴파일러가 상태머신으로 변환
- 컴파일러는 이 suspend 함수를 내부적으로 아래처럼 변환한다 (개념적 의사코드):

```kotlin
class MyFunctionContinuation : Continuation<Unit> {
    var label = 0

    override fun resumeWith(result: Result<Unit>) {
        when (label) {
            0 -> {
                println("Step 1")
                label = 1
                delay(1000L, this)  // 일시 중단, 이후 다시 resumeWith 호출
            }
            1 -> {
                println("Step 2")
                label = 2  // 끝
            }
        }
    }
}
```
- label 이라는 상태값을 만들어서
- "내가 어디까지 실행했는지"를 기록해 놓음.
- delay()가 끝나고 다시 resumeWith가 호출될 때 label 값으로 이어서 실행함



| 일반 함수  | suspend 함수 |
| ------ | ---------- |
| 스택 기반  | 상태머신 기반    |
| 스레드 점유 | 스레드 반환     |
| 중단 불가  | 중단 가능      |


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
