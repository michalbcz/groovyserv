/*
 * Copyright 2009-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jggug.kobo.groovyserv

import org.jggug.kobo.groovyserv.utils.DebugUtils

/**
 * @author NAKANO Yasuharu
 */
class GServThreadGroup extends ThreadGroup {

    private String id

    GServThreadGroup(String name) {
        super(name)
        this.id = "${GServThreadGroup.simpleName}:${name}"
    }

    GServThreadGroup(ThreadGroup parent, String name) {
        super(parent, name)
        this.id = "${GServThreadGroup.simpleName}:${name}:${parent.name}"
    }

    @Override
    void uncaughtException(Thread thread, Throwable e) {
        if (containsCaused(e, ThreadDeath)) {
            DebugUtils.verboseLog id, "Thread is stopped by force: ${thread}" // ignored details
        } else {
            DebugUtils.errorLog id, "Uncaught exception: ${thread}", e
        }
    }

    private boolean containsCaused(Throwable e, Class exceptionType) {
        if (e == null) {
            return false
        }
        if (e in exceptionType) {
            return true
        }
        if (e.cause) {
            return containsCaused(e.cause, exceptionType)
        }
    }

    @Override
    String toString() { name }

}

