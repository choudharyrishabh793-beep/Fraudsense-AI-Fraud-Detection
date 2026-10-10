package com.fraudsense.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Thread-safe fixed-capacity buffer => GENERICS (<T>) + SYNCHRONIZATION. */
public class RingBuffer<T> {
    private final Deque<T> data = new ArrayDeque<>();
    private final int capacity;

    public RingBuffer(int capacity) { this.capacity = capacity; }

    public synchronized void add(T item) {
        data.addLast(item);
        if (data.size() > capacity) data.removeFirst();
    }

    public synchronized List<T> snapshot() { return new ArrayList<>(data); }
}