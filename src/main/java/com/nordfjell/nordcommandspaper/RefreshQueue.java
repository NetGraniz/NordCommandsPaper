package com.nordfjell.nordcommandspaper;

import java.util.*;
import java.util.function.Consumer;

/** Main-thread only, fixed cap and budget. Replacing work coalesces repeated reloads. */
final class RefreshQueue<T> {
    static final int MAX_PENDING = 4096, BUDGET = 8;
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    synchronized boolean replace(Collection<? extends T> entries) {
        queue.clear();
        boolean complete = true;
        for (T entry : entries) {
            if (queue.size() == MAX_PENDING) { complete = false; break; }
            queue.addLast(entry);
        }
        return complete;
    }
    synchronized void drain(Consumer<T> action) {
        for (int i = 0; i < BUDGET; i++) {
            T value = queue.pollFirst();
            if (value == null) return;
            action.accept(value);
        }
    }
    synchronized int size() { return queue.size(); }
    synchronized void clear() { queue.clear(); }
}

