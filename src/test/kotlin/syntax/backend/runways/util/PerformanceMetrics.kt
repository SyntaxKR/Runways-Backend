package syntax.backend.runways.util

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

/**
 * 성능 측정 결과를 담는 데이터 클래스
 */
data class PerformanceResult(
    val executionTimeMs: Long,           // 실행 시간 (ms)
    val memoryUsedMB: Double,            // 메모리 사용량 (MB)
    val cpuTimeMs: Long,                 // CPU 시간 (ms)
    val cpuUsagePercent: Double,         // CPU 사용률 (%)
    val queryCount: Int = 0              // 쿼리 실행 횟수 (= 네트워크 왕복 횟수)
)

/**
 * 성능 측정 유틸리티 클래스
 */
class PerformanceMetrics {
    private val runtime = Runtime.getRuntime()
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()

    private var beforeMemory: Long = 0
    private var beforeCpuTime: Long = 0
    private var startTime: Long = 0

    init {
        // CPU 시간 측정 활성화
        if (threadBean.isThreadCpuTimeSupported) {
            threadBean.isThreadCpuTimeEnabled = true
        }
    }

    /**
     * 측정 시작
     */
    fun start() {
        // GC 실행으로 정확한 측정
        System.gc()
        Thread.sleep(100)

        beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        beforeCpuTime = threadBean.currentThreadCpuTime  // 나노초 단위
        startTime = System.currentTimeMillis()
    }

    /**
     * 측정 종료 및 결과 반환
     */
    fun end(queryCount: Int = 0): PerformanceResult {
        val executionTime = System.currentTimeMillis() - startTime
        val afterCpuTime = threadBean.currentThreadCpuTime

        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsed = (afterMemory - beforeMemory).toDouble() / 1024 / 1024  // MB

        // CPU 시간 계산 (나노초 -> 밀리초)
        val cpuTime = (afterCpuTime - beforeCpuTime) / 1_000_000

        // CPU 사용률 계산 (CPU 시간 / 실행 시간 * 100)
        val cpuUsage = if (executionTime > 0) {
            (cpuTime.toDouble() / executionTime.toDouble() * 100).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        return PerformanceResult(
            executionTimeMs = executionTime,
            memoryUsedMB = memoryUsed,
            cpuTimeMs = cpuTime,
            cpuUsagePercent = cpuUsage,
            queryCount = queryCount
        )
    }

    /**
     * 작업 실행 및 성능 측정을 한 번에 수행
     */
    inline fun <T> measure(
        queryCount: Int = 0,
        block: () -> T
    ): Pair<T, PerformanceResult> {
        start()
        val result = block()
        val metrics = end(queryCount)
        return result to metrics
    }
}

/**
 * 성능 측정 결과를 포맷팅하여 출력
 */
fun PerformanceResult.format(): String {
    return buildString {
        appendLine("실행 시간: ${executionTimeMs}ms")
        appendLine("메모리 사용: ${"%.2f".format(memoryUsedMB)}MB")
        appendLine("CPU 시간: ${cpuTimeMs}ms")
        appendLine("CPU 사용률: ${"%.2f".format(cpuUsagePercent)}%")
        if (queryCount > 0) {
            appendLine("쿼리 횟수: ${queryCount}회")
        }
    }
}