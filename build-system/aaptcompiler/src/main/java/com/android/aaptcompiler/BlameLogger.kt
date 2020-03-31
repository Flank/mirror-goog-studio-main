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

package com.android.aaptcompiler

import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.android.utils.ILogger
import java.io.File

class BlameLogger(val logger: ILogger, val blameMap: (Source) -> Source = { it }): ILogger {

    data class Source(val file: File, val line: Int, val column: Int) {
        fun toSourceFilePosition() =
            SourceFilePosition(file, SourcePosition(line, column, -1, line, column, -1))

        companion object {
            fun fromSourceFilePosition(filePosition: SourceFilePosition) =
                Source(filePosition.file.sourceFile!!,
                    filePosition.position.startLine,
                    filePosition.position.startColumn)
        }
    }

    override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) {
        logger.error(t, msgFormat, *transformSources(args))
    }

    override fun warning(msgFormat: String, vararg args: Any?) {
        logger.warning(msgFormat, *transformSources(args))
    }

    override fun info(msgFormat: String, vararg args: Any?) {
        logger.info(msgFormat, *transformSources(args))
    }

    override fun lifecycle(msgFormat: String, vararg args: Any?) {
        logger.lifecycle(msgFormat, *transformSources(args))
    }

    override fun quiet(msgFormat: String, vararg args: Any?) {
        logger.quiet(msgFormat, *transformSources(args))
    }

    override fun verbose(msgFormat: String, vararg args: Any?) {
        logger.verbose(msgFormat, *transformSources(args))
    }

    private fun transformSources(args: Array<out Any?>): Array<Any?> =
        args.map {
            if (it is Source) {
                blameMap(it)
            } else {
                it
            }
        }.toTypedArray()
}