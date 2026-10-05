package com.nordfjell.nordcommandspaper;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** Single owned reader and at most one in-flight/result job; Bukkit-free. */
final class ReloadGate<T> implements AutoCloseable {
    record Outcome<T>(T value, String error) {}
    private final ThreadPoolExecutor worker;
    private final ArrayBlockingQueue<Outcome<T>> result = new ArrayBlockingQueue<>(1);
    private boolean busy, closed;

    ReloadGate(String name) {
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, name);
                    thread.setDaemon(true);
                    return thread;
                });
        worker.prestartCoreThread();
    }
    synchronized boolean start(Callable<T> load) {
        if (closed || busy) return false;
        busy = true;
        worker.execute(() -> {
            Outcome<T> outcome;
            try { outcome = new Outcome<>(load.call(), null); }
            catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                outcome = new Outcome<>(null, e instanceof java.io.IOException ? e.getMessage() : e.getClass().getSimpleName());
            }
            synchronized (this) {
                if (!closed) result.offer(outcome);
            }
        });
        return true;
    }
    synchronized void drain(Consumer<Outcome<T>> apply) {
        Outcome<T> outcome = result.poll();
        if (outcome == null || closed) return;
        // Keep the gate busy during publication so a reentrant start cannot overlap.
        try { apply.accept(outcome); }
        finally { busy = false; }
    }
    synchronized boolean busy() { return busy; }
    @Override public void close() {
        synchronized (this) { closed = true; result.clear(); }
        worker.shutdownNow();
        try { worker.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

