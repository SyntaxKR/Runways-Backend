package syntax.backend.runways.util

/**
 * 성능 벤치마크 설정
 */
data class BenchmarkConfig(
    val name: String,                       // 벤치마크 이름
    val dataSizes: List<Int> = listOf(100, 500, 1000),  // 테스트 데이터 크기
    val warmupRuns: Int = 0                 // 워밍업 실행 횟수 (JIT 컴파일 대비)
)

/**
 * 테스트 대상 메서드 정의
 */
data class BenchmarkMethod(
    val name: String,                       // 메서드 이름
    val queryCount: (dataSize: Int) -> Int, // 쿼리 횟수 계산 함수
    val networkDescription: (dataSize: Int) -> String,  // 네트워크 설명
    val action: (dataSize: Int) -> Unit     // 실행할 작업
)

/**
 * 성능 벤치마크 프레임워크
 *
 * 사용 예시:
 * ```
 * val benchmark = PerformanceBenchmark(
 *     config = BenchmarkConfig(name = "Bulk Insert 비교"),
 *     setupBeforeEach = { cleanupData() },
 *     cleanupAfterEach = { verifyData() }
 * )
 *
 * benchmark.addMethod(BenchmarkMethod(
 *     name = "JPA",
 *     queryCount = { size -> size },
 *     networkDescription = { size -> "$size번 (개별 INSERT)" },
 *     action = { size -> insertWithJpa(size) }
 * ))
 *
 * val results = benchmark.run()
 * PerformanceResultFormatter.printTable(results)
 * ```
 */
class PerformanceBenchmark(
    private val config: BenchmarkConfig,
    private val setupBeforeEach: (() -> Unit)? = null,
    private val cleanupAfterEach: (() -> Unit)? = null
) {
    private val methods = mutableListOf<BenchmarkMethod>()

    /**
     * 테스트 메서드 추가
     */
    fun addMethod(method: BenchmarkMethod) {
        methods.add(method)
    }

    /**
     * 여러 메서드 한 번에 추가
     */
    fun addMethods(vararg methods: BenchmarkMethod) {
        this.methods.addAll(methods)
    }

    /**
     * 벤치마크 실행
     */
    fun run(): Map<Int, List<BenchmarkResult>> {
        println("\n" + "=".repeat(100))
        println(config.name)
        println("=".repeat(100))

        val allResults = mutableMapOf<Int, List<BenchmarkResult>>()

        config.dataSizes.forEach { size ->
            println("\n[ 테스트 데이터 크기: $size 개 ]")
            println("-".repeat(100))

            val results = mutableListOf<BenchmarkResult>()

            methods.forEach { method ->
                // 워밍업 실행
                repeat(config.warmupRuns) {
                    setupBeforeEach?.invoke()
                    method.action(size)
                    cleanupAfterEach?.invoke()
                }

                // 실제 측정
                setupBeforeEach?.invoke()
                val metrics = PerformanceMetrics()
                metrics.start()

                method.action(size)

                val perfResult = metrics.end(
                    queryCount = method.queryCount(size),
                    networkRoundTrips = method.networkDescription(size)
                )

                cleanupAfterEach?.invoke()

                results.add(
                    BenchmarkResult(
                        methodName = method.name,
                        dataSize = size,
                        performance = perfResult
                    )
                )
            }

            allResults[size] = results

            // 각 데이터 크기 테스트 완료 후 마지막 정리
            setupBeforeEach?.invoke()
        }

        println("\n" + "=".repeat(100))
        println("벤치마크 완료")
        println("=".repeat(100) + "\n")

        return allResults
    }
}

/**
 * 벤치마크 결과
 */
data class BenchmarkResult(
    val methodName: String,
    val dataSize: Int,
    val performance: PerformanceResult
)