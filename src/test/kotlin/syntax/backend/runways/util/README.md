# Performance Benchmark Framework

Kotlin/JVM 프로젝트에서 성능 측정 및 비교를 위한 재사용 가능한 테스트 프레임워크입니다.

## 기능

- 실행 시간, 메모리, CPU 사용량 자동 측정
- 여러 메서드 성능 비교
- 다양한 데이터 크기로 자동 테스트
- 여러 출력 형식 지원 (표, 마크다운, CSV)
- 워밍업 기능 (JIT 컴파일 대비)

## 사용 방법

### 1. 기본 사용법

```kotlin
@Test
fun `성능 비교 테스트`() {
    // 1. 벤치마크 설정
    val benchmark = PerformanceBenchmark(
        config = BenchmarkConfig(
            name = "데이터 삽입 성능 비교",
            dataSizes = listOf(100, 500, 1000),  // 테스트할 데이터 크기
            warmupRuns = 2                        // 워밍업 횟수 (선택)
        ),
        setupBeforeEach = { cleanupData() },     // 각 테스트 전 실행
        cleanupAfterEach = { verifyData() }      // 각 테스트 후 실행
    )

    // 2. 테스트 메서드 등록
    benchmark.addMethod(
        BenchmarkMethod(
            name = "JPA 방식",
            queryCount = { size -> size },               // 쿼리 횟수
            action = { size -> insertWithJPA(size) }     // 실행할 작업
        )
    )

    benchmark.addMethod(
        BenchmarkMethod(
            name = "JDBC Batch",
            queryCount = { 1 },                          // 배치: 1번의 쿼리
            action = { size -> insertWithJDBC(size) }
        )
    )

    // 3. 실행 및 결과 출력
    val results = benchmark.run()
    PerformanceResultFormatter.printTable(results)
}
```

### 2. 여러 메서드 한 번에 등록

```kotlin
benchmark.addMethods(
    BenchmarkMethod("방법 1", { 10 }) { size -> method1(size) },
    BenchmarkMethod("방법 2", { 5 }) { size -> method2(size) },
    BenchmarkMethod("방법 3", { 1 }) { size -> method3(size) }
)
```

### 3. 출력 형식

#### 상세 표 출력
```kotlin
PerformanceResultFormatter.printTable(results)
```

출력:
```
방법                  | 실행시간   | 메모리사용 | CPU사용  | 쿼리횟수
-----------------------------------------------------------------------------
Pure JDBC Batch       |      3ms |   0.50MB |   0.00% |      1회
PostgreSQL COPY       |      4ms |   0.50MB | 100.00% |      1회
JPA                   |    558ms |  21.86MB |  78.32% |    100회
```

#### 간단한 비교
```kotlin
PerformanceResultFormatter.printSimpleComparison(results)
```

출력:
```
1. Pure JDBC Batch      :      3 ms (fastest)
2. PostgreSQL COPY      :      4 ms (0.75x faster)
3. JPA                  :    558 ms (0.01x faster)
```

#### 마크다운 형식 (블로그용)
```kotlin
PerformanceResultFormatter.printMarkdown(results)
```

출력:
```markdown
## 100 개 데이터 삽입

| 방법 | 실행시간 | 메모리사용 | CPU사용 | 쿼리횟수 |
|------|---------|-----------|---------|---------|
| Pure JDBC Batch | 3ms | 0.50MB | 0.00% | 1회 |
| PostgreSQL COPY | 4ms | 0.50MB | 100.00% | 1회 |
```

#### CSV 형식 (엑셀용)
```kotlin
PerformanceResultFormatter.printCSV(results)
```

출력:
```csv
데이터크기,방법,실행시간(ms),메모리(MB),CPU(%),쿼리횟수
100,Pure JDBC Batch,3,0.50,0.00,1
100,PostgreSQL COPY,4,0.50,100.00,1
```

#### 분석 결과
```kotlin
PerformanceResultFormatter.printAnalysis(results)
```

출력:
```
[ 100 개 데이터 분석 ]
- 가장 빠름: Pure JDBC Batch (3ms)
- 가장 느림: JPA (558ms)
- 속도 차이: 186.0배
- 메모리 최소: Pure JDBC Batch (0.50MB)
- 쿼리 최소: Pure JDBC Batch (1회)
```

## 구성 요소

### PerformanceMetrics
CPU, 메모리 사용량을 측정하는 유틸리티

```kotlin
val metrics = PerformanceMetrics()
metrics.start()
// ... 작업 수행
val result = metrics.end(queryCount = 10)  
println(result.format())
```

### PerformanceBenchmark
여러 메서드의 성능을 자동으로 비교하는 프레임워크

### PerformanceResultFormatter
결과를 다양한 형식으로 출력

## 다른 프로젝트에서 사용하기

이 util 패키지를 그대로 복사하여 다른 프로젝트에서 사용할 수 있습니다:

```
src/test/kotlin/your/package/util/
├── PerformanceMetrics.kt
├── PerformanceBenchmark.kt
├── PerformanceResultFormatter.kt
└── README.md
```

패키지명만 변경하면 됩니다:
```kotlin
package your.package.util
```

## 예제

실제 사용 예제는 `CourseMappingServicePerformanceTest.kt`의
`프레임워크 사용 - 자원 사용량 비교` 테스트를 참고하세요.

## 요구사항

- Kotlin 1.9+
- JUnit 5
- Java Management Extensions (JMX) - CPU 측정용
