package org.poc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

public class Main {

    private static final int THREADS = 100000;
    private static final int ROUNDS = 10;

    private static Runnable simulateWork;
    private static String simulationName;

    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Escolha a simulação de trabalho:");
        System.out.println("1 - Cálculos Computacionais Pesados");
        System.out.println("2 - Operações de I/O Simuladas");
        System.out.println("3 - Processamento de Strings");
        System.out.println("4 - Espera Ativa");
        System.out.println("5 - Tarefas Mistas");
        int choice = scanner.nextInt();

        switch (choice) {
            case 1 -> { simulateWork = Main::simulateHeavyComputation; simulationName = "Cálculos Computacionais"; }
            case 2 -> { simulateWork = Main::simulateIOOperations; simulationName = "Operações de I/O"; }
            case 3 -> { simulateWork = Main::simulateStringProcessing; simulationName = "Processamento de Strings"; }
            case 4 -> { simulateWork = Main::simulateActiveWait; simulationName = "Espera Ativa"; }
            case 5 -> { simulateWork = Main::simulateMixedTasks; simulationName = "Tarefas Mistas"; }
            default -> {
                System.out.println("Opção inválida. Usando processamento de strings como padrão.");
                simulateWork = Main::simulateStringProcessing;
                simulationName = "Processamento de Strings";
            }
        }

        List<Result> nativeResults = new ArrayList<>();
        List<Result> virtualResults = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            System.out.println("\n🔄 Rodada " + round + " (Threads Nativas)...");
            nativeResults.add(executeRound(false));
        }
        for (int round = 1; round <= ROUNDS; round++) {
            System.out.println("\n🔄 Rodada " + round + " (Threads Virtuais)...");
            virtualResults.add(executeRound(true));
        }

        printResultsTable(simulationName, THREADS, nativeResults, virtualResults);
        printSummary(nativeResults, virtualResults);
    }

    private static void simulateHeavyComputation() {
        int number = ThreadLocalRandom.current().nextInt(10_000, 20_000);
        long result = 1;
        for (int i = 2; i <= number; i++) {
            result *= i;
            result %= 1_000_000_007;
        }
    }

    private static void simulateIOOperations() {
        int size = ThreadLocalRandom.current().nextInt(100_000, 500_000);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        byte[] copy = data.clone();
    }

    private static void simulateStringProcessing() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            builder.append("ThreadWork").append(i);
        }
        String result = builder.toString().replace("Thread", "Work");
    }

    private static void simulateActiveWait() {
        long end = System.nanoTime() + ThreadLocalRandom.current().nextInt(10, 50) * 1_000_000L;
        while (System.nanoTime() < end) {
            Math.sqrt(ThreadLocalRandom.current().nextDouble());
        }
    }

    private static void simulateMixedTasks() {
        int number = ThreadLocalRandom.current().nextInt(1_000, 5_000);
        for (int i = 0; i < number; i++) {
            Math.log(i + 1);
        }
        int size = ThreadLocalRandom.current().nextInt(50_000, 100_000);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 128);
        }
        LockSupport.parkNanos(ThreadLocalRandom.current().nextInt(5, 20) * 1_000_000L);
    }

    private static Result executeRound(boolean virtual) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        Instant start = Instant.now();

        long gcCountBefore = getTotalGCCount();
        long gcTimeBefore = getTotalGCTime();

        for (int i = 0; i < THREADS; i++) {
            Thread thread = virtual
                    ? Thread.ofVirtual().unstarted(simulateWork)
                    : new Thread(simulateWork);
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        Instant end = Instant.now();
        long heapMemoryUsed = getHeapMemoryUsed();
        long nonHeapMemoryUsed = getNonHeapMemoryUsed();
        long gcCount = getTotalGCCount() - gcCountBefore;
        long gcTime = getTotalGCTime() - gcTimeBefore;

        long time = Duration.between(start, end).toMillis();

        System.out.printf("Tempo: %d ms | Memória: %s | GCs: %d | GC Time: %d ms%n",
                time, formatMB(heapMemoryUsed + nonHeapMemoryUsed), gcCount, gcTime);

        return new Result(time, heapMemoryUsed, nonHeapMemoryUsed, gcCount, gcTime);
    }

    private static long getHeapMemoryUsed() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static long getNonHeapMemoryUsed() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
    }

    private static long getTotalGCCount() {
        return ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
    }

    private static long getTotalGCTime() {
        return ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .sum();
    }

    private static String formatMB(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    private static void printResultsTable(String simName, int threads, List<Result> nativeResults, List<Result> virtualResults) {
        System.out.println("\n=== RESULTADOS DAS SIMULAÇÕES ===");
        System.out.printf("Simulação: %-25s | Threads: %-8d | Rodadas: %-3d%n", simName, threads, nativeResults.size());
        System.out.println("----------------------------------------------------------------------------------------------------------");
        // Cabeçalho principal com alinhamento manual para "Threads Virtuais"
        System.out.printf("%-8s | %-30s | %-40s%n", "Rodada", "Threads Nativas", "              Threads Virtuais");
        // Subcabeçalho das colunas
        System.out.printf("%-8s | %-10s | %-12s | %-6s | %-8s | %-10s | %-12s | %-6s | %-8s%n",
                "", "Tempo(ms)", "Memória(MB)", "GCs", "GC(ms)",
                "Tempo(ms)", "Memória(MB)", "GCs", "GC(ms)");
        System.out.println("----------------------------------------------------------------------------------------------------------");
        for (int i = 0; i < nativeResults.size(); i++) {
            Result n = nativeResults.get(i);
            Result v = virtualResults.get(i);
            System.out.printf("%-8d | %-10d | %-12s | %-6d | %-8d | %-10d | %-12s | %-6d | %-8d%n",
                    (i + 1),
                    n.time, formatMB(n.heapMemory + n.nonHeapMemory), n.gcCount, n.gcTime,
                    v.time, formatMB(v.heapMemory + v.nonHeapMemory), v.gcCount, v.gcTime
            );
        }
        System.out.println("----------------------------------------------------------------------------------------------------------");
    }


    private static String formatResultRow(Result r) {
        return String.format("%4d ms | %7s | %2d GC | %4d ms",
                r.time,
                formatMB(r.heapMemory + r.nonHeapMemory),
                r.gcCount,
                r.gcTime
        );
    }

    private static void printSummary(List<Result> nativeResults, List<Result> virtualResults) {
        System.out.println("\n=== SUMÁRIO DAS MÉDIAS ===");
        printSummaryFor("Threads Nativas", nativeResults);
        printSummaryFor("Threads Virtuais", virtualResults);
    }

    private static void printSummaryFor(String label, List<Result> results) {
        double avgTime = results.stream().mapToLong(r -> r.time).average().orElse(0);
        double avgMem = results.stream().mapToLong(r -> r.heapMemory + r.nonHeapMemory).average().orElse(0);
        double avgGC = results.stream().mapToLong(r -> r.gcCount).average().orElse(0);
        double avgGCTime = results.stream().mapToLong(r -> r.gcTime).average().orElse(0);

        System.out.printf("%-18s | Tempo médio: %-10.2f ms | Memória média: %-10s | GC's: %-6.2f | GC Tempo médio: %-8.2f ms%n",
                label, avgTime, formatMB((long) avgMem), avgGC, avgGCTime);
    }

    private record Result(long time, long heapMemory, long nonHeapMemory, long gcCount, long gcTime) {}
}