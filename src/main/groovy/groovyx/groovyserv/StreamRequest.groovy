/*
 * Copyright 2009 the original author or authors.
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
package groovyx.groovyserv

import groovyx.groovyserv.exception.InvalidRequestHeaderException

/**
 * @author NAKANO Yasuharu
 */
class StreamRequest {

    String sizeText // optional: size of input stream
    String command  // optional

    boolean isEmpty() {
        size == 0
    }

    boolean isInterrupted() {
        command == "interrupt"
    }

    int getSize() {
        sizeText?.isInteger() ? (sizeText as int) : 0
    }

    /**
     * @throws InvalidRequestHeaderException
     */
    void check() {
        if (command && !command.matches("interrupt|shutdown|ping")) {
            throw new InvalidRequestHeaderException("Invalid StreamRequest: size=${sizeText}, command=${command}")
        }
    }
}

