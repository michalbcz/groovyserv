/*
 * Copyright 2009-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import static java.lang.Thread.currentThread as currentThread
import static org.jggug.kobo.groovyserv.ProtocolHeader.*


class ChunkedOutputStream extends OutputStream {

    WeakHashMap<ThreadGroup, OutputStream> outPerThreadGroup = [:]
    char streamId

    private ChunkedOutputStream(char streamId) {
        this.streamId = streamId
    }

    static ChunkedOutputStream newOut() {
        new ChunkedOutputStream('o' as char)
    }

    static ChunkedOutputStream newErr() {
        new ChunkedOutputStream('e' as char)
    }

    @Override
    public void flush() throws IOException {
        currentOutputStream.flush()
    }

    @Override
    public void close() throws IOException {
        currentOutputStream.close()
    }

    @Override
    public void write(int b) {
        byte[] buf = new byte[1]
        buf[0] = (b>>8*0) & 0x0000ff
        write(buf, 0, 1)
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (DebugUtils.isVerboseMode()) {
            DebugUtils.errLog("Server==>Client")
            DebugUtils.errLog(" id=" + streamId)
            DebugUtils.errLog(" size=" + len)
            DebugUtils.errLog(DebugUtils.dump(b, off, len))
        }
        OutputStream out = currentOutputStream
        out.write((HEADER_STREAM_ID + ": " + streamId + "\n").bytes)
        out.write((HEADER_SIZE + ": " + len + "\n").bytes)
        out.write("\n".bytes)
        out.write(b, off, len)
    }

    public void bind(OutputStream out, ThreadGroup threadGroup) {
        outPerThreadGroup[threadGroup] = out
    }

    private OutputStream getCurrentOutputStream() {
        return check(outPerThreadGroup[currentThread().threadGroup])
    }

    private static OutputStream check(OutputStream out) {
        if (out == null) {
            def thread = currentThread()
            throw new IllegalStateException("System.out/err can't access from this thread: ${thread}:${thread.id}:${thread.threadGroup}")
        }
        return out
    }

}

