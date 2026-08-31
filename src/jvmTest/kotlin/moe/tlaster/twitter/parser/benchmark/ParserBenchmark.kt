package moe.tlaster.twitter.parser.benchmark

import com.sun.management.ThreadMXBean as SunThreadMXBean
import java.lang.management.ManagementFactory
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.TwitterParser

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val SAMPLE_MASK = 1023L

@Volatile
private var blackhole = 0

internal object ParserBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        Locale.setDefault(Locale.ROOT)
        if (args.firstOrNull() in listOf("help", "--help", "-h")) {
            printHelp()
            return
        }

        val mode = Mode.parse(args.firstOrNull())
        val workloads = createWorkloads()
        check(workloads.all { it.input.length == 280 })

        println("twitter-parser JVM benchmark")
        println(
            "mode=${mode.id}, java=${System.getProperty("java.version")}, " +
                "cores=${Runtime.getRuntime().availableProcessors()}, " +
                "maxHeap=${Runtime.getRuntime().maxMemory() / 1024 / 1024} MiB",
        )

        warmUp(workloads, mode.warmupSeconds)
        runSingleThreadBenchmarks(workloads, mode)
        runDomainScalingBenchmark(workloads.first { it.name == "domain-valid" }.parser, mode)
        runBatchBenchmark(workloads, mode.batchOperations)
        runConcurrencyBenchmarks(workloads, mode.concurrentSeconds)

        if (blackhole == Int.MIN_VALUE) {
            println("blackhole")
        }
    }
}

private enum class Mode(
    val id: String,
    val warmupSeconds: Long,
    val targetMillis: Long,
    val denseTargetMillis: Long,
    val rounds: Int,
    val batchOperations: Long,
    val concurrentSeconds: Long,
) {
    Quick("quick", 1, 40, 10, 3, 100_000, 1),
    All("all", 2, 120, 25, 5, 1_000_000, 2),
    Stress("stress", 3, 200, 50, 5, 3_000_000, 5),
    ;

    companion object {
        fun parse(value: String?): Mode {
            if (value == null) return All
            return values().firstOrNull { it.id == value.lowercase() }
                ?: error("Unknown mode '$value'. Expected quick, all, or stress.")
        }
    }
}

private data class Workload(
    val name: String,
    val parser: TwitterParser,
    val input: String,
)

private data class SingleResult(
    val workload: Workload,
    val iterations: Int,
    val nanosPerOperation: Double,
    val bytesPerOperation: Double,
)

private data class GcSnapshot(
    val collections: Long,
    val millis: Long,
)

private data class WorkerResult(
    val operations: Long,
    val characters: Long,
    val allocatedBytes: Long,
    val latencySamples: LongArray,
    val latencySampleCount: Int,
    val checksum: Int,
)

private val allocationBean: SunThreadMXBean? by lazy {
    (ManagementFactory.getThreadMXBean() as? SunThreadMXBean)?.also { bean ->
        if (bean.isThreadAllocatedMemorySupported && !bean.isThreadAllocatedMemoryEnabled) {
            bean.isThreadAllocatedMemoryEnabled = true
        }
    }?.takeIf { it.isThreadAllocatedMemorySupported }
}

private fun createWorkloads(): List<Workload> {
    val defaults = TwitterParser()
    val mixedFeatures = TwitterParser(
        enableEmoji = true,
        enableEscapeInUrl = true,
        enableCJKInCashTag = true,
        validMarkInUserName = listOf('.'),
        validMarkInHashTag = listOf('-'),
    )
    val domains = TwitterParser(enableDomainDetection = true)
    val twitterText = TwitterParser(
        enableDomainDetection = true,
        enableNonAsciiInUrl = false,
        enableEscapeInUrl = true,
    )

    return listOf(
        Workload("plain", defaults, "a".repeat(280)),
        Workload(
            "mixed",
            mixedFeatures,
            repeatToLength("Hello @user.name https://example.com/path?q=1 #Kotlin-tag \$AAPL :smile: ", 280),
        ),
        Workload("explicit-url", defaults, repeatToLength("https://example.com/path?q=1 ", 280)),
        Workload("tags", defaults, repeatToLength("@username #hashtag \$AAPL ", 280)),
        Workload("domain-valid", domains, repeatToLength("example.com ", 280)),
        Workload("domain-invalid", domains, repeatToLength("example.invalid ", 280)),
        Workload(
            "twitter-text",
            twitterText,
            repeatToLength("example.com https://example.com/path?q=1 @username #Kotlin \$AAPL ", 280),
        ),
    )
}

private fun repeatToLength(value: String, length: Int): String {
    return value.repeat((length + value.length - 1) / value.length).substring(0, length)
}

private fun denseDots(length: Int): String {
    return CharArray(length) { index -> if (index % 2 == 0) 'a' else '.' }
        .also { it[it.lastIndex] = 'a' }
        .concatToString()
}

private fun warmUp(workloads: List<Workload>, seconds: Long) {
    val deadline = System.nanoTime() + seconds * NANOS_PER_SECOND.toLong()
    var index = 0
    var checksum = 0
    while (System.nanoTime() < deadline) {
        checksum = 31 * checksum + parse(workloads[index])
        index++
        if (index == workloads.size) index = 0
    }
    blackhole = checksum
}

private fun runSingleThreadBenchmarks(workloads: List<Workload>, mode: Mode) {
    println()
    println("Single-thread steady state")
    printSingleHeader()
    val results = workloads.map { workload ->
        measureSingle(workload, mode.targetMillis, mode.rounds).also(::printSingle)
    }
    check(results.all { it.iterations > 0 && it.nanosPerOperation > 0 })
}

private fun runDomainScalingBenchmark(parser: TwitterParser, mode: Mode) {
    println()
    println("Domain-detection scaling (adversarial dots)")
    printSingleHeader()
    runOperations(Workload("dense-dots-warmup", parser, denseDots(64)), 1_000)
    val results = listOf(128, 256, 512, 1_024, 2_048, 4_096).map { length ->
        measureSingle(
            Workload("dense-dots-$length", parser, denseDots(length)),
            mode.denseTargetMillis,
            mode.rounds,
        ).also(::printSingle)
    }
    check(results.all { it.iterations > 0 && it.nanosPerOperation > 0 })
    check(results.last().nanosPerOperation > results.first().nanosPerOperation)
}

private fun measureSingle(workload: Workload, targetMillis: Long, rounds: Int): SingleResult {
    val iterations = calibrate(workload, targetMillis * 1_000_000)
    val beforeAllocated = allocatedBytes()
    runOperations(workload, iterations)
    val afterAllocated = allocatedBytes()
    val elapsed = LongArray(rounds) {
        val started = System.nanoTime()
        runOperations(workload, iterations)
        System.nanoTime() - started
    }.sortedArray()
    val nanosPerOperation = elapsed[elapsed.size / 2].toDouble() / iterations
    val bytesPerOperation = if (beforeAllocated >= 0 && afterAllocated >= beforeAllocated) {
        (afterAllocated - beforeAllocated).toDouble() / iterations
    } else {
        -1.0
    }
    return SingleResult(workload, iterations, nanosPerOperation, bytesPerOperation)
}

private fun calibrate(workload: Workload, targetNanos: Long): Int {
    var iterations = 1
    repeat(6) {
        val started = System.nanoTime()
        runOperations(workload, iterations)
        val elapsed = (System.nanoTime() - started).coerceAtLeast(1)
        if (elapsed >= targetNanos / 2) return iterations
        val multiplier = (targetNanos / elapsed).coerceIn(2, 100)
        val next = (iterations.toLong() * multiplier).coerceAtMost(1_000_000).toInt()
        if (next == iterations) return iterations
        iterations = next
    }
    return iterations
}

private fun runOperations(workload: Workload, operations: Int) {
    var checksum = 0
    repeat(operations) {
        checksum = 31 * checksum + parse(workload)
    }
    blackhole = checksum
}

private fun parse(workload: Workload): Int {
    val tokens: List<Token> = workload.parser.parse(workload.input)
    var checksum = tokens.size
    if (tokens.isNotEmpty()) {
        checksum = 31 * checksum + tokens[tokens.lastIndex].value.length
    }
    return checksum
}

private fun printSingleHeader() {
    println("case                     chars    iters       us/op      ops/s    Mchar/s     KiB/op")
}

private fun printSingle(result: SingleResult) {
    val nanos = result.nanosPerOperation
    val kib = if (result.bytesPerOperation >= 0) result.bytesPerOperation / 1024 else Double.NaN
    System.out.printf(
        Locale.ROOT,
        "%-24s %5d %8d %11.2f %10.0f %10.2f %10.2f%n",
        result.workload.name,
        result.workload.input.length,
        result.iterations,
        nanos / 1_000,
        NANOS_PER_SECOND / nanos,
        result.workload.input.length * 1_000.0 / nanos,
        kib,
    )
}

private fun runBatchBenchmark(workloads: List<Workload>, operations: Long) {
    println()
    println("Sequential mixed batch")
    val beforeGc = gcSnapshot()
    val beforeAllocated = allocatedBytes()
    val started = System.nanoTime()
    var workloadIndex = 0
    var completed = 0L
    var characters = 0L
    var checksum = 0
    while (completed < operations) {
        val workload = workloads[workloadIndex]
        checksum = 31 * checksum + parse(workload)
        characters += workload.input.length
        completed++
        workloadIndex++
        if (workloadIndex == workloads.size) workloadIndex = 0
    }
    val elapsedNanos = System.nanoTime() - started
    val afterAllocated = allocatedBytes()
    val gc = gcSnapshot() - beforeGc
    blackhole = checksum

    val seconds = elapsedNanos / NANOS_PER_SECOND
    val allocated = if (beforeAllocated >= 0 && afterAllocated >= beforeAllocated) {
        afterAllocated - beforeAllocated
    } else {
        -1
    }
    val allocatedGiB = if (allocated >= 0) allocated / 1024.0 / 1024 / 1024 else Double.NaN
    val bytesPerOperation = if (allocated >= 0) allocated.toDouble() / completed else Double.NaN
    System.out.printf(
        Locale.ROOT,
        "ops=%,d, elapsed=%.2fs, throughput=%,.0f ops/s, %.2f Mchar/s, allocated=%.2f GiB (%.0f B/op), GC=%d / %d ms%n",
        completed,
        seconds,
        completed / seconds,
        characters / seconds / 1_000_000,
        allocatedGiB,
        bytesPerOperation,
        gc.collections,
        gc.millis,
    )
}

private fun runConcurrencyBenchmarks(workloads: List<Workload>, seconds: Long) {
    println()
    println("Concurrent mixed load (${seconds}s per level, latency sampled 1/1024 ops)")
    println("threads       ops/s  speedup    Mchar/s    p50 us    p95 us    p99 us     KiB/op       GC")
    val cores = Runtime.getRuntime().availableProcessors()
    val threadCounts = listOf(1, 2, 4, 8, 16, cores).filter { it <= cores }.distinct()
    var baseline = 0.0
    threadCounts.forEach { threads ->
        val result = runConcurrentLevel(workloads, threads, seconds)
        val throughput = result.operations / result.elapsedSeconds
        val kibPerOperation = if (result.allocatedBytes >= 0) {
            result.allocatedBytes.toDouble() / result.operations / 1024
        } else {
            Double.NaN
        }
        if (baseline == 0.0) baseline = throughput
        System.out.printf(
            Locale.ROOT,
            "%7d %,11.0f %8.2f %10.2f %9.2f %9.2f %9.2f %10.2f %4d/%dms%n",
            threads,
            throughput,
            throughput / baseline,
            result.characters / result.elapsedSeconds / 1_000_000,
            percentile(result.latencies, 0.50) / 1_000.0,
            percentile(result.latencies, 0.95) / 1_000.0,
            percentile(result.latencies, 0.99) / 1_000.0,
            kibPerOperation,
            result.gc.collections,
            result.gc.millis,
        )
    }
}

private data class ConcurrentLevelResult(
    val operations: Long,
    val characters: Long,
    val elapsedSeconds: Double,
    val allocatedBytes: Long,
    val latencies: LongArray,
    val gc: GcSnapshot,
)

private fun runConcurrentLevel(
    workloads: List<Workload>,
    threads: Int,
    durationSeconds: Long,
): ConcurrentLevelResult {
    val executor = Executors.newFixedThreadPool(threads)
    val ready = CountDownLatch(threads)
    val start = CountDownLatch(1)
    val done = CountDownLatch(threads)
    val deadline = AtomicLong()
    val failure = AtomicReference<Throwable?>()
    val results = arrayOfNulls<WorkerResult>(threads)

    repeat(threads) { workerIndex ->
        executor.execute {
            val samples = LongArray(65_536)
            ready.countDown()
            try {
                start.await()
                val stopAt = deadline.get()
                val beforeAllocated = allocatedBytes()
                var operations = 0L
                var characters = 0L
                var checksum = 0
                var workloadIndex = workerIndex % workloads.size
                var sampleCount = 0
                while (System.nanoTime() < stopAt) {
                    val workload = workloads[workloadIndex]
                    if (operations and SAMPLE_MASK == 0L && sampleCount < samples.size) {
                        val started = System.nanoTime()
                        checksum = 31 * checksum + parse(workload)
                        samples[sampleCount++] = System.nanoTime() - started
                    } else {
                        checksum = 31 * checksum + parse(workload)
                    }
                    operations++
                    characters += workload.input.length
                    workloadIndex++
                    if (workloadIndex == workloads.size) workloadIndex = 0
                }
                val afterAllocated = allocatedBytes()
                results[workerIndex] = WorkerResult(
                    operations = operations,
                    characters = characters,
                    allocatedBytes = if (beforeAllocated >= 0 && afterAllocated >= beforeAllocated) {
                        afterAllocated - beforeAllocated
                    } else {
                        -1
                    },
                    latencySamples = samples,
                    latencySampleCount = sampleCount,
                    checksum = checksum,
                )
            } catch (throwable: Throwable) {
                failure.compareAndSet(null, throwable)
            } finally {
                done.countDown()
            }
        }
    }

    ready.await()
    val beforeGc = gcSnapshot()
    val started = System.nanoTime()
    deadline.set(started + durationSeconds * NANOS_PER_SECOND.toLong())
    start.countDown()
    done.await()
    val elapsedSeconds = (System.nanoTime() - started) / NANOS_PER_SECOND
    executor.shutdown()
    failure.get()?.let { throw it }

    val completed = results.map { checkNotNull(it) }
    blackhole = completed.fold(0) { checksum, result -> checksum xor result.checksum }
    val sampleCount = completed.sumOf { it.latencySampleCount }
    val latencies = LongArray(sampleCount)
    var offset = 0
    completed.forEach { result ->
        result.latencySamples.copyInto(latencies, offset, endIndex = result.latencySampleCount)
        offset += result.latencySampleCount
    }
    latencies.sort()

    return ConcurrentLevelResult(
        operations = completed.sumOf { it.operations },
        characters = completed.sumOf { it.characters },
        elapsedSeconds = elapsedSeconds,
        allocatedBytes = if (completed.all { it.allocatedBytes >= 0 }) {
            completed.sumOf { it.allocatedBytes }
        } else {
            -1
        },
        latencies = latencies,
        gc = gcSnapshot() - beforeGc,
    )
}

private fun percentile(sortedValues: LongArray, percentile: Double): Long {
    if (sortedValues.isEmpty()) return 0
    val index = (sortedValues.lastIndex * percentile).toInt()
    return sortedValues[index]
}

@Suppress("DEPRECATION")
private fun allocatedBytes(): Long {
    return allocationBean?.getThreadAllocatedBytes(Thread.currentThread().id) ?: -1
}

private fun gcSnapshot(): GcSnapshot {
    return ManagementFactory.getGarbageCollectorMXBeans().fold(GcSnapshot(0, 0)) { total, bean ->
        GcSnapshot(
            collections = total.collections + bean.collectionCount.coerceAtLeast(0),
            millis = total.millis + bean.collectionTime.coerceAtLeast(0),
        )
    }
}

private operator fun GcSnapshot.minus(other: GcSnapshot): GcSnapshot {
    return GcSnapshot(collections - other.collections, millis - other.millis)
}

private fun printHelp() {
    println("Usage: ./gradlew jvmBenchmark [--args=quick|all|stress]")
    println("  quick  short smoke benchmark")
    println("  all    complete benchmark (default)")
    println("  stress longer batch and concurrency runs")
}
