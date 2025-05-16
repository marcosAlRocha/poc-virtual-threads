package org.poc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

public class Main {

    private static final int THREADS = 100000;
    private static final int ROUNDS = 10;

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
            case 1 -> simulateWork = Main::simulateHeavyComputation;
            case 2 -> simulateWork = Main::simulateIOOperations;
            case 3 -> simulateWork = Main::simulateStringProcessing;
            case 4 -> simulateWork = Main::simulateActiveWait;
            case 5 -> simulateWork = Main::simulateMixedTasks;
            default -> {
                System.out.println("Opção inválida. Usando processamento de strings como padrão.");
                simulateWork = Main::simulateStringProcessing;
            }
        }

        System.out.println("===== Benchmark: Threads Comuns =====");
        runBenchmark(false);

        System.out.println("\n===== Benchmark: Threads Virtuais =====");
        runBenchmark(true);
    }

    private static Runnable simulateWork;

    private static void simulateHeavyComputation() {
        int number = ThreadLocalRandom.current().nextInt(10_000, 20_000);
        long result = 1;
        for (int i = 2; i <= number; i++) {
            result *= i;
            result %= 1_000_000_000;
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
        // Cálculo
        int number = ThreadLocalRandom.current().nextInt(1_000, 5_000);
        for (int i = 0; i < number; i++) {
            Math.log(i + 1);
        }

        // Memória
        int size = ThreadLocalRandom.current().nextInt(50_000, 100_000);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 128);
        }

        // Espera
        LockSupport.parkNanos(ThreadLocalRandom.current().nextInt(5, 20) * 1_000_000L);
    }

    private static void runBenchmark(boolean virtual) throws InterruptedException {
        long totalTime = 0;
        long totalMemory = 0;
        long totalGCs = 0;
        long totalGCTime = 0;

        for (int round = 1; round <= ROUNDS; round++) {
            System.out.println("\n🔄 Rodada " + round + "...");
            var result = executeRound(virtual);
            totalTime += result.time;
            totalMemory += result.heapMemory + result.nonHeapMemory;
            totalGCs += result.gcCount;
            totalGCTime += result.gcTime;
        }

        System.out.println("\n=== MÉDIAS ===");
        System.out.println("Tempo médio: " + (totalTime / ROUNDS) + " ms");
        System.out.println("Memória média usada: " + formatMB(totalMemory / ROUNDS));
        System.out.println("");
        System.out.println("GCs médios: " + (totalGCs / ROUNDS));
        System.out.println("Tempo médio de GC: " + (totalGCTime / ROUNDS) + " ms");
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

        System.out.println("Tempo: " + time + " ms");
        System.out.println("Memória usada (heap): " + formatMB(heapMemoryUsed));
        System.out.println("Memória usada (non-heap): " + formatMB(nonHeapMemoryUsed));
        System.out.println("GCs: " + gcCount);
        System.out.println("Tempo de GC: " + gcTime + " ms");

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

    private record Result(long time, long heapMemory, long nonHeapMemory, long gcCount, long gcTime) {
    }
}
