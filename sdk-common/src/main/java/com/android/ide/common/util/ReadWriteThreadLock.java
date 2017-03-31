/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.util;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import java.io.File;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A lock similar to {@link ReentrantReadWriteLock} used to synchronize threads within the same JVM,
 * even in the event of duplicate class loadings.
 *
 * <p>When attempting to synchronize threads within the same JVM (e.g., making sure that only one
 * instance of a class executes some action at a time), we may choose to create a lock associated
 * with the class and require instances of the class to acquire the lock before executing.
 *
 * <p>However, if that class is loaded multiple times by different class loaders, the JVM considers
 * them as different classes, and there would be multiple locks associated with those classes. The
 * desired effect of synchronizing all threads within the JVM is not achieved; instead, each lock
 * can only take effect for instances of the same class loaded by the same class loader.
 *
 * <p>We create this class to address that limitation. A {@code ReadWriteThreadLock} can be used to
 * synchronize *all* threads in a JVM, even when a class using {@code ReadWriteThreadLock} or the
 * {@code ReadWriteThreadLock} class itself is loaded multiple times by different class loaders.
 *
 * <p>Threads will be synchronized on the same lock object (two lock objects are the same if one
 * equals() the other). The client using {@code ReadWriteThreadLock} will provide a lock object when
 * constructing a {@code ReadWriteThreadLock} instance. Then, different threads using the same or
 * different {@code ReadWriteThreadLock} instances on the same lock object can be synchronized.
 *
 * <p>Note that {@code ReadWriteThreadLock} requires the type (class) of the lock object to be
 * loaded by a single class loader, as objects of the same type loaded by different class loaders
 * cannot be compared (one never equals() the other).
 *
 * <p>The basic usage of this class is similar to {@link ReentrantReadWriteLock} and {@link
 * java.util.concurrent.locks.Lock}. Below is a typical example.
 *
 * <pre>{@code
 * ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(lockObject);
 * ReadWriteThreadLock.Lock lock =
 *     useSharedLock
 *         ? readWriteThreadLock.readLock()
 *         : readWriteThreadLock.writeLock();
 * lock.lock();
 * try {
 *     runnable.run();
 * } finally {
 *     lock.unlock();
 * }
 * }</pre>
 *
 * <p>The key usage difference between {@code ReadWriteThreadLock} and a regular Java lock such as
 * {@link ReentrantReadWriteLock} is that {@code ReadWriteThreadLock} is itself not a lock object
 * (which threads are directly synchronized on), but only a proxy to the actual lock object.
 * Therefore, there could be multiple instances of {@code ReadWriteThreadLock} on the same lock
 * object.
 *
 * <p>Two lock objects are considered the same if one equals() the other. Thus, if the client uses a
 * file as the lock object, in order for this class to detect same physical files via equals() the
 * client needs to normalize the lock file's path when constructing a {@code ReadWriteThreadLock}
 * (preferably using {@link File#getCanonicalFile()}, as there are subtle issues with other methods
 * such as {@link File#getCanonicalPath()}, {@link Path#normalize()}, and {@link
 * Path#toRealPath(LinkOption...)}).
 *
 * <p>This lock is reentrant.
 *
 * <p>This class is thread-safe.
 */
@Immutable
public final class ReadWriteThreadLock {

    @NonNull
    private final ReadWriteThreadLock.Lock readLock = new ReadWriteThreadLock.ReadLock();

    @NonNull
    private final ReadWriteThreadLock.Lock writeLock = new ReadWriteThreadLock.WriteLock();

    /**
     * JVM-wide map from a lock object to a {@link ReentrantReadWriteLock}, used to make sure that
     * there is only one instance of {@code ReentrantReadWriteLock} per lock object within the
     * current JVM.
     */
    @NonNull
    private static final JvmWideVariable<ConcurrentMap<Object, ReentrantReadWriteLock>> lockMap =
            new JvmWideVariable<>(
                    ReadWriteThreadLock.class.getName(),
                    "lockMap",
                    new TypeToken<ConcurrentMap<Object, ReentrantReadWriteLock>>() {},
                    ConcurrentHashMap::new);

    /**
     * The unique {@link ReentrantReadWriteLock} instance corresponding to the given lock object.
     */
    @NonNull
    private final ReentrantReadWriteLock lock;

    /**
     * Creates a {@code ReadWriteThreadLock} instance for the given lock object. Threads will be
     * synchronized on the same lock object (two lock objects are the same if one equals() the
     * other).
     *
     * <p>The type (class) of the lock object must be loaded by a single class loader. Currently,
     * this method requires the single class loader to be the bootstrap class loader.
     *
     * <p>If the client uses a file as the lock object, in order for this class to detect same
     * physical files via equals() the client needs to normalize the lock file's path when
     * constructing a {@code ReadWriteThreadLock} (preferably using {@link File#getCanonicalFile()},
     * as there are subtle issues with other methods such as {@link File#getCanonicalPath()}, {@link
     * Path#normalize()}, and {@link Path#toRealPath(LinkOption...)}).
     *
     * @param lockObject the lock object, must be loaded by a single class loader
     */
    public ReadWriteThreadLock(@NonNull Object lockObject) {
        Preconditions.checkArgument(
                lockObject.getClass().getClassLoader() == null,
                String.format(
                        "%1$s must be loaded by the bootstrap class loader but is loaded by %2$s",
                        lockObject.getClass(),
                        lockObject.getClass().getClassLoader()));

        ConcurrentMap<Object, ReentrantReadWriteLock> map = lockMap.get();
        Preconditions.checkNotNull(map);
        this.lock = map.computeIfAbsent(lockObject, (any) -> new ReentrantReadWriteLock());
    }

    /** Returns the lock used for reading. */
    public ReadWriteThreadLock.Lock readLock() {
        return readLock;
    }

    /** Returns the lock used for writing. */
    public ReadWriteThreadLock.Lock writeLock() {
        return writeLock;
    }

    public interface Lock {

        void lock();

        void unlock();
    }

    @Immutable
    private final class ReadLock implements Lock {

        @Override
        public void lock() {
            lock.readLock().lock();
        }

        @Override
        public void unlock() {
            lock.readLock().unlock();
        }
    }

    @Immutable
    private final class WriteLock implements Lock {

        @Override
        public void lock() {
            lock.writeLock().lock();
        }

        @Override
        public void unlock() {
            lock.writeLock().unlock();
        }
    }
}
