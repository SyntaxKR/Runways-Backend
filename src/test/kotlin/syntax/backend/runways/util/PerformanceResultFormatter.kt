package syntax.backend.runways.util

/**
 * 성능 측정 결과 포맷터
 */
object PerformanceResultFormatter {

    /**
     * 벤치마크 결과를 표 형태로 출력
     */
    fun printTable(results: Map<Int, List<BenchmarkResult>>) {
        results.forEach { (size, benchmarkResults) ->
            printTableForSize(size, benchmarkResults)
        }
    }

    /**
     * 특정 데이터 크기의 결과를 표로 출력
     */
    fun printTableForSize(dataSize: Int, results: List<BenchmarkResult>) {
        println("\n[ 테스트 데이터 크기: $dataSize 개 ]")
        println("-".repeat(100))
        println()

        // 헤더 출력
        println("%-35s | %10s | %12s | %10s | %15s".format(
            "방법", "실행시간", "메모리사용", "CPU사용", "쿼리횟수"
        ))
        println("-".repeat(100))

        // 실행 시간 기준으로 정렬
        val sortedResults = results.sortedBy { it.performance.executionTimeMs }

        // 데이터 출력
        sortedResults.forEach { result ->
            println("%-35s | %8dms | %10.2fMB | %8.2f%% | %13d회".format(
                result.methodName,
                result.performance.executionTimeMs,
                result.performance.memoryUsedMB,
                result.performance.cpuUsagePercent,
                result.performance.queryCount
            ))
        }

        println()
    }

    /**
     * 실행 시간만 간단하게 출력
     */
    fun printSimpleComparison(results: Map<Int, List<BenchmarkResult>>) {
        results.forEach { (size, benchmarkResults) ->
            println("\n[ 테스트 데이터 크기: $size 개 ]")
            println("-".repeat(80))

            val sortedResults = benchmarkResults.sortedBy { it.performance.executionTimeMs }

            sortedResults.forEachIndexed { index, result ->
                val rank = index + 1
                val speedup = if (index > 0) {
                    String.format("(%.2fx faster)", sortedResults[0].performance.executionTimeMs.toDouble() / result.performance.executionTimeMs.toDouble())
                } else {
                    "(fastest)"
                }
                println("$rank. %-35s : %6d ms %s".format(result.methodName, result.performance.executionTimeMs, speedup))
            }

            println()
        }
    }

    /**
     * CSV 형식으로 출력 (엑셀에서 사용 가능)
     */
    fun printCSV(results: Map<Int, List<BenchmarkResult>>) {
        println("데이터크기,방법,실행시간(ms),메모리(MB),CPU(%),쿼리횟수")

        results.forEach { (size, benchmarkResults) ->
            benchmarkResults.forEach { result ->
                println("$size,${result.methodName},${result.performance.executionTimeMs}," +
                        "${"%.2f".format(result.performance.memoryUsedMB)}," +
                        "${"%.2f".format(result.performance.cpuUsagePercent)}," +
                        "${result.performance.queryCount}")
            }
        }
    }

    /**
     * 마크다운 테이블 형식으로 출력 (블로그/문서용)
     */
    fun printMarkdown(results: Map<Int, List<BenchmarkResult>>) {
        results.forEach { (size, benchmarkResults) ->
            println("\n## $size 개 데이터 삽입\n")
            println("| 방법 | 실행시간 | 메모리사용 | CPU사용 | 쿼리횟수 |")
            println("|------|---------|-----------|---------|---------|")

            val sortedResults = benchmarkResults.sortedBy { it.performance.executionTimeMs }

            sortedResults.forEach { result ->
                println("| ${result.methodName} | ${result.performance.executionTimeMs}ms | " +
                        "${"%.2f".format(result.performance.memoryUsedMB)}MB | " +
                        "${"%.2f".format(result.performance.cpuUsagePercent)}% | " +
                        "${result.performance.queryCount}회 |")
            }

            println()
        }
    }

    /**
     * 성능 비교 분석 출력
     */
    fun printAnalysis(results: Map<Int, List<BenchmarkResult>>) {
        results.forEach { (size, benchmarkResults) ->
            println("\n[ $size 개 데이터 분석 ]")

            val sortedResults = benchmarkResults.sortedBy { it.performance.executionTimeMs }
            val fastest = sortedResults.first()
            val slowest = sortedResults.last()

            println("- 가장 빠름: ${fastest.methodName} (${fastest.performance.executionTimeMs}ms)")
            println("- 가장 느림: ${slowest.methodName} (${slowest.performance.executionTimeMs}ms)")
            println("- 속도 차이: ${slowest.performance.executionTimeMs / fastest.performance.executionTimeMs.toDouble()}배")

            // 메모리 효율성
            val leastMemory = benchmarkResults.minBy { it.performance.memoryUsedMB }
            println("- 메모리 최소: ${leastMemory.methodName} (${"%.2f".format(leastMemory.performance.memoryUsedMB)}MB)")

            // 쿼리 효율성
            val leastQueries = benchmarkResults.minBy { it.performance.queryCount }
            println("- 쿼리 최소: ${leastQueries.methodName} (${leastQueries.performance.queryCount}회)")

            println()
        }
    }
}