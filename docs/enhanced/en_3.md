# 📦 예제를 통해 이해하기

## ✅ 1. limitedParallelism(n) 실전 제어 예제

### 목적:
- `Dispatchers.IO`와 같은 shared dispatcher가 <b>동시 실행 수를 과도하게 늘리는 걸 막기 위해</b>
- 실제로 n개까지만 동시 실행을 허용하는 제한 wrapper를 구성
- 예시 링크 => [[limitedParallelismTest.java]](https://github.com/jaydenchuljinlee/corutine/main/src/test/kotlin/com/example/corutine/enhancement/limitedParallelismTest.kt)
  - 동시에 16개만 실행되고, 나머지는 큐에서 대기
  - `Dispatchers.IO`지만 논리적으로 제한된 pool처럼 동작

## ✅ 2. Semaphore를 통한 IO 동시성 제어

### 목적:
- dispatcher를 고정하지 않더라도, 논리적인 동시성 제어
- dispatcher는 그대로 유지하고 동시 접근량만 막기
- 예제 링크 => [[SemaphoreTest.java]](https://github.com/jaydenchuljinlee/corutine/main/src/test/kotlin/com/example/corutine/enhancement/SemaphoreTest.kt)
  - 최대 10개까지 동시에 실행
  - dispatcher는 IO라 스레드 생성은 자유롭지만, <b>로직 동시 실행량은 세마포어로 제어</b>

## ✅ 3. 커스텀 Dispatcher로 sharedPool 분리

### 목적:
- 특정 작업을 sharedPool과 분리된 ThreadPool에서 처리
- 외부 API, 민감한 리소스 작업 격리 시 유용
- 예제 링크 => [[SemaphoreTest.java]](https://github.com/jaydenchuljinlee/corutine/main/src/test/kotlin/com/example/corutine/enhancement/CustomDispatcherTest.kt)
  - 스레드가 `DefaultDispatcher-worker`나 `IO-Dispatcher-worker`가 아니라 `pool-1-thread-n`
    - 완전히 별도의 ThreadPool에서 동작
  - <b>GC 컨플릭트, sharedPool 과점유 방지</b>

## ✅ 4. CoroutineScheduler의 스레드 정책 시뮬레이션

### 목적:
- 대부분의 스레드가 `DefaultDispatcher-worker-N` 또는 `IO-Dispatcher-worker-N`으로 출력됨
  - 이게 바로 sharedPool에서 동적으로 할당된 worker thread



## ✅ 5. Dispatcher 충돌 예시 (예제 부적합)

- 예시 링크 => [[ConcurrencyTest.java]](https://github.com/jaydenchuljinlee/corutine/main/src/test/kotlin/com/example/corutine/enhancement/SharedPoolDispatcherSaturationTest.kt)
  - IO에서 CPU 연산이 실행됨 → sharedPool 포화
  - Default에서 실행되어야 할 다른 연산들이 지연됨 → UI 멈춤, Kafka consumer 지연 등
- `sharedPool에 대한 부하 실험`의 한계
  - 코루틴은 `Dispatchers.IO`와 `Dispatchers.Default`는 내부적으로 <b>같은 CoroutineScheduler 기반의 shared pool</b>을 사용
  - `Dispatchers.IO`는 I/O 바운드 작업 시 필요한 만큼 스레드를 계속 만들어서 부하를 흡수 가능
  - Dispatchers.IO는 스레드를 거의 무한대로 만들어서 작업을 처리하려 하므로, 실제로 경합 상태를 만들어내기가 굉장히 어렵고, 실험이 유의미하지 않음

