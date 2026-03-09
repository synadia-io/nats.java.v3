// Copyright 2020 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.examples.z;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Z4 {

    public static void main(String[] args) {
    }

    static class BlockingArrayDeque<E> {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();
        private final ArrayDeque<E> queue;
        private final int capacity;
        private final boolean bounded;

        /**
         * Creates an unbounded blocking deque
         */
        public BlockingArrayDeque() {
            this.queue = new ArrayDeque<>();
            this.capacity = Integer.MAX_VALUE;
            this.bounded = false;
        }

        /**
         * Creates a bounded blocking deque with initial capacity
         */
        public BlockingArrayDeque(int capacity) {
            this.queue = new ArrayDeque<>(capacity);
            this.capacity = capacity;
            this.bounded = true;
        }

        /**
         * Inserts element, waiting up to the specified time if necessary for space
         * @return true if successful, false if timed out
         */
        public boolean offer(E element, long timeout, TimeUnit unit) throws InterruptedException {
            if (element == null) {
                throw new NullPointerException();
            }

            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (bounded && queue.size() >= capacity) {
                    if (nanos <= 0) {
                        return false;
                    }
                    nanos = notFull.awaitNanos(nanos);
                }
                queue.offer(element);
                notEmpty.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Inserts element immediately if space available
         * @return true if successful, false if full (for bounded queues)
         */
        public boolean offer(E element) {
            if (element == null) {
                throw new NullPointerException();
            }

            lock.lock();
            try {
                if (bounded && queue.size() >= capacity) {
                    return false;
                }
                queue.offer(element);
                notEmpty.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Inserts element, blocking if necessary for space (bounded queues only)
         */
        public void put(E element) throws InterruptedException {
            if (element == null) {
                throw new NullPointerException();
            }

            lock.lockInterruptibly();
            try {
                while (bounded && queue.size() >= capacity) {
                    notFull.await();
                }
                queue.offer(element);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Retrieves and removes head, waiting up to the specified time if empty
         * @return element, or null if timed out
         */
        public E poll(long timeout, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) {
                    if (nanos <= 0) {
                        return null;
                    }
                    nanos = notEmpty.awaitNanos(nanos);
                }
                E element = queue.poll();
                if (bounded) {
                    notFull.signal();
                }
                return element;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Retrieves and removes head immediately
         * @return element, or null if empty
         */
        public E poll() {
            lock.lock();
            try {
                E element = queue.poll();
                if (element != null && bounded) {
                    notFull.signal();
                }
                return element;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Retrieves and removes head, blocking if empty
         */
        public E take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) {
                    notEmpty.await();
                }
                E element = queue.poll();
                if (bounded) {
                    notFull.signal();
                }
                return element;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Retrieves but doesn't remove head
         */
        public E peek() {
            lock.lock();
            try {
                return queue.peek();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Drains all elements to the given collection
         * @return number of elements drained
         */
        public int drainTo(Collection<? super E> collection) {
            if (collection == null) {
                throw new NullPointerException();
            }
            if (collection == this) {
                throw new IllegalArgumentException();
            }

            lock.lock();
            try {
                int count = 0;
                E element;
                while ((element = queue.poll()) != null) {
                    collection.add(element);
                    count++;
                }
                if (bounded && count > 0) {
                    notFull.signalAll();
                }
                return count;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Drains up to maxElements to the given collection
         * @return number of elements drained
         */
        public int drainTo(Collection<? super E> collection, int maxElements) {
            if (collection == null) {
                throw new NullPointerException();
            }
            if (collection == this) {
                throw new IllegalArgumentException();
            }
            if (maxElements <= 0) {
                return 0;
            }

            lock.lock();
            try {
                int count = 0;
                E element;
                while (count < maxElements && (element = queue.poll()) != null) {
                    collection.add(element);
                    count++;
                }
                if (bounded && count > 0) {
                    notFull.signalAll();
                }
                return count;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns current number of elements
         */
        public int size() {
            lock.lock();
            try {
                return queue.size();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns true if empty
         */
        public boolean isEmpty() {
            lock.lock();
            try {
                return queue.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns remaining capacity (Integer.MAX_VALUE for unbounded)
         */
        public int remainingCapacity() {
            lock.lock();
            try {
                return capacity - queue.size();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Removes all elements
         */
        public void clear() {
            lock.lock();
            try {
                queue.clear();
                if (bounded) {
                    notFull.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Provides direct access to the underlying lock for complex operations
         */
        public ReentrantLock getLock() {
            return lock;
        }
    }}
