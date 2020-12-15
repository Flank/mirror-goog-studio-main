/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.testutils.file

import java.io.IOException
import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.ProviderMismatchException
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

/**
 * A [FileSystemProvider] that delegates all operations to the provider of another file system.
 * Can be subclassed to introduce additional behaviors useful for testing, for example, I/O errors
 * or artificial delays.
 */
open class DelegatingFileSystemProvider(delegateFileSystem: FileSystem) : FileSystemProvider() {
    val delegate: FileSystemProvider = delegateFileSystem.provider()
    private val delegatingFileSystem = DelegatingFileSystem(delegateFileSystem)
    private val scheme = "delegate:${delegate.scheme}"

    val fileSystem: FileSystem
      get() = delegatingFileSystem

    override fun getScheme(): String {
        return scheme
    }

    @Throws(IOException::class)
    override fun newFileSystem(uri: URI, env: MutableMap<String, *>): FileSystem {
        return DelegatingFileSystem(delegate.newFileSystem(uri, env))
    }

    override fun getFileSystem(uri: URI): FileSystem {
        return delegate.getFileSystem(delegateUri(uri))
    }

    override fun getPath(uri: URI): Path {
        return delegate.getPath(delegateUri(uri))
    }

    @Throws(IOException::class)
    override fun newByteChannel(
            path: Path, options: Set<OpenOption>, vararg attrs: FileAttribute<*>
    ) : SeekableByteChannel {
        return delegate.newByteChannel(delegate(path), options, *attrs)
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
            dir: Path, filter: DirectoryStream.Filter<in Path>): DirectoryStream<Path> {
        val delegateStream = delegate.newDirectoryStream(delegate(dir)) { true }
        return object : DirectoryStream<Path> {
            override fun iterator(): MutableIterator<Path> {
                return DelegatingPathIterator(delegateStream.iterator())
                        .asSequence()
                        .filter { filter.accept(it) }
                        .iterator()
                        .asMutableIterator()
            }

            override fun close() {
                delegateStream.close()
            }
        }
    }

    @Throws(IOException::class)
    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        delegate.createDirectory(delegate(dir), *attrs)
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        return delegate.delete(delegate(path))
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        delegate.copy(delegate(source), delegate(target), *options)
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        delegate.move(delegate(source), delegate(target), *options)
    }

    @Throws(IOException::class)
    override fun isSameFile(path1: Path, path2: Path): Boolean {
        return delegate.isSameFile(delegate(path1), delegate(path2))
    }

    @Throws(IOException::class)
    override fun isHidden(path: Path): Boolean {
        return delegate.isHidden(delegate(path))
    }

    @Throws(IOException::class)
    override fun getFileStore(path: Path): FileStore {
        return delegate.getFileStore(delegate(path))
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        return delegate.checkAccess(delegate(path), *modes)
    }

    override fun <V : FileAttributeView> getFileAttributeView(
            path: Path, type: Class<V>, vararg options: LinkOption): V? {
        return delegate.getFileAttributeView(delegate(path), type, *options)
    }

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
            path: Path, type: Class<A>, vararg options: LinkOption): A {
        return delegate.readAttributes(delegate(path), type, *options)
    }

    @Throws(IOException::class)
    override fun readAttributes(
            path: Path, attributes: String, vararg options: LinkOption): MutableMap<String, Any> {
        return delegate.readAttributes(delegate(path), attributes, *options)
    }

    @Throws(IOException::class)
    override fun setAttribute(
            path: Path, attribute: String, value: Any, vararg options: LinkOption) {
        delegate.setAttribute(delegate(path), attribute, value, *options)
    }

    private fun delegateUri(uri: URI): URI {
        require(uri.scheme.equals(scheme, ignoreCase = true)) {
            "URI $uri does not match this provider"
        }
        return URI(delegate.scheme, uri.authority, uri.path, uri.query, uri.fragment)
    }

    private fun delegate(path: Path): Path {
        if (path is DelegatingPath) {
            return path.delegate
        }
        throw ProviderMismatchException("Path $path does not match this provider")
    }

    private fun <T> Iterator<T>.asMutableIterator(): MutableIterator<T> {
        if (this is MutableIterator<T>) {
            return this
        }
        val iterator = this
        return object: MutableIterator<T> {
            override fun hasNext(): Boolean {
                return iterator.hasNext()
            }

            override fun next(): T {
                return iterator.next()
            }

            override fun remove() {
                throw UnsupportedOperationException("remove")
            }
        }
    }

    private inner class DelegatingFileSystem(val delegate: FileSystem) : FileSystem() {

        override fun provider(): FileSystemProvider {
            return this@DelegatingFileSystemProvider
        }

        override fun close() {
            delegate.close()
        }

        override fun isOpen(): Boolean {
            return delegate.isOpen
        }

        override fun isReadOnly(): Boolean {
            return delegate.isReadOnly
        }

        override fun getSeparator(): String {
            return delegate.separator
        }

        override fun getRootDirectories(): Iterable<Path> {
            return delegate.rootDirectories.map { DelegatingPath(it) }
        }

        override fun getFileStores(): Iterable<FileStore> {
            return delegate.fileStores
        }

        override fun supportedFileAttributeViews(): Set<String> {
            return delegate.supportedFileAttributeViews()
        }

        override fun getPath(first: String, vararg more: String): Path {
            return DelegatingPath(delegate.getPath(first, *more))
        }

        override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
            return delegate.getPathMatcher(syntaxAndPattern)
        }

        override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
            return delegate.userPrincipalLookupService
        }

        override fun newWatchService(): WatchService {
            return delegate.newWatchService()
        }
    }

    private inner class DelegatingPath(val delegate: Path) : Path by delegate {

        override fun getFileSystem(): FileSystem {
            return delegatingFileSystem
        }

        override fun getRoot(): Path? {
            val root = delegate.root ?: return null
            return DelegatingPath(root)
        }

        override fun getFileName(): Path? {
            val name = delegate.fileName ?: return null
            return DelegatingPath(name)
        }

        override fun getParent(): Path? {
            val parent = delegate.parent ?: return null
            return DelegatingPath(parent)
        }

        override fun getName(index: Int): Path {
            return DelegatingPath(delegate.getName(index))
        }

        override fun subpath(beginIndex: Int, endIndex: Int): Path {
            return DelegatingPath(delegate.subpath(beginIndex, endIndex))
        }

        override fun normalize(): Path {
            return DelegatingPath(delegate.normalize())
        }

        override fun resolve(other: Path): Path {
            return DelegatingPath(delegate.resolve(delegate(other)))
        }

        override fun resolve(other: String): Path {
            return DelegatingPath(delegate.resolve(other))
        }

        override fun resolveSibling(other: Path): Path {
            return DelegatingPath(delegate.resolveSibling(delegate(other)))
        }

        override fun resolveSibling(other: String): Path {
            return DelegatingPath(delegate.resolveSibling(other))
        }

        override fun relativize(other: Path): Path {
            return DelegatingPath(delegate.relativize(delegate(other)))
        }

        override fun endsWith(other: Path): Boolean {
            if (other !is DelegatingPath) {
                return false
            }
            return delegate.endsWith(other.delegate)
        }

        override fun startsWith(other: Path): Boolean {
            if (other !is DelegatingPath) {
                return false
            }
            return delegate.startsWith(other.delegate)
        }

        override fun compareTo(other: Path?): Int {
            return delegate.compareTo((other as DelegatingPath).delegate)
        }

        override fun toUri(): URI {
            val uri = delegate.toUri()
            return URI(scheme, uri.authority, uri.path, uri.query, uri.fragment)
        }

        override fun toAbsolutePath(): Path {
            return DelegatingPath(delegate.toAbsolutePath())
        }

        override fun toRealPath(vararg options: LinkOption): Path {
            return DelegatingPath(delegate.toRealPath(*options))
        }

        override fun iterator(): MutableIterator<Path> {
            return DelegatingPathIterator(delegate.iterator())
        }

        override fun toString() = delegate.toString()

        override fun equals(other: Any?): Boolean {
            return other is DelegatingPath && delegate == other.delegate
        }

        override fun hashCode() = delegate.hashCode()
    }

    private inner class DelegatingPathIterator(
            private val delegate: MutableIterator<Path>
    ) : MutableIterator<Path> by delegate {
        override fun next(): Path {
            return DelegatingPath(delegate.next())
        }
    }
}


