#!/usr/bin/env groovy
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
package org.jggug.kobo.groovyserv;

import org.codehaus.groovy.tools.shell.util.NoExitSecurityManager;
import org.codehaus.groovy.tools.GroovyStarter;


/**
 * GroovyServer runs groovy command background.
 * This makes groovy response time at startup very quicker.
 *
 * Protocol summary:
 * <pre>
 * Request ::= InvocationRequest
 * Response ::= StreamResponse
 *
 * InvocationRequest ::=
 *    'Cwd:' <cwd> CRLF
 *    'Arg:' <argn> CRLF
 *    'Arg:' <arg1> CRLF
 *    'Arg:' <arg2> CRLF
 *    'Cp:' <classpath> CRLF
 *    CRLF
 *    <data from STDIN>
 *
 * StreamResponse ::=
 *    'Status:' <status> CRLF
 *    'Channel:' <id> CRLF
 *    'Size:' <size> CRLF
 *    CRLF
 *    <data for STDERR/STDOUT>
 *  
 *
 *     <cwd> is current working directory.
 *     <arg1><arg2>.. are commandline arguments(optional).
 *     <classpath>.. is the value of environment variable CLASSPATH(optional).
 *     CRLF is carridge return (0x0d ^M) and line feed (0x0a, '\n').
 *     <data from STDIN> is byte sequence from standard input.
 *
 *     <status> is exit status of invoked groovy script.
 *     <id> is 'o' or 'e', where 'o' means standard output of the program.
 *          'e' means standard error of the program.
 *     <size> is the size of chunk.
 *     <data from STDIN> is byte sequence from standard output/error.
 *     
 * </pre>
 *
 * @author UEHARA Junji
 */
class GroovyServer implements Runnable {
	
  final static String HEADER_CURRENT_WORKING_DIR = "Cwd";
  final static String HEADER_ARG = "Arg";
  final static String HEADER_CP = "Cp";
  final static String HEADER_STATUS = "Status";
  final static int DEFAULT_PORT = 1961
	
	final int CR = 0x0d
	final int LF = 0x0a
	
	static BufferedInputStream originalIn = System.in
	static OutputStream originalOut = System.out
	static OutputStream originalErr = System.err

    static readLine(InputStream is) {
      StringBuffer result = new StringBuffer()
      int ch;
      while ((ch = is.read()) != '\n') {
        if (ch == null) {
          return result.toString();
        }
        result.append((char)ch);
      }
      return result.toString();
    }
	
	static Map<String, List<String>> readHeaders(ins) {
      def result = [:]
      def line
      while ((line = readLine(ins)) != "") {
        def kv = line.split(':', 2);
        def key = kv[0]
        def value = kv[1]
        if (!result.containsKey(key)) {
          result[key] = []
        }
        if (value.charAt(0) == ' ') {
          value = value.substring(1);
        }
        result[key] += value
      }
      result
	}
	
	def soc

  static Thread dirOwner

  static currentDir = null;
  def getCurrentDir() {
    currentDir
  }

  def setCurrentDir(dir) {
    synchronized (GroovyServer.class) {
      currentDir = dir
      if (System.getProperty('user.dir') != currentDir) {
        System.setProperty('user.dir', currentDir)
        dirOwner = Thread.currentThread()
        PlatformMethods.chdir(currentDir)
        addClasspath(currentDir)
      }
    }
  }
  
  def addClasspath(classpath) {
    def cp = System.getProperty("groovy.classpath")
    if (cp == null || cp == "") {
      System.setProperty("groovy.classpath", classpath);
    }
    else {
      def pathes = cp.split(File.pathSeparator) as List
      def pathToAdd = ""
      classpath.split(File.pathSeparator).reverseEach {
        if (!(pathes as List).contains(it)) {
          pathToAdd = (it + File.pathSeparator + pathToAdd)
        }
      }
      System.setProperty("groovy.classpath", pathToAdd + cp);
    }
  }
	
  def setupStandardStreams(ins, outs) {
    System.setIn(new MultiplexedInputStream(ins));
    System.setOut(new PrintStream(new ChunkedOutputStream(outs, 'o' as char)));
    System.setErr(new PrintStream(new ChunkedOutputStream(outs, 'e' as char)));
  }

  void process(headers) {
    if (headers[HEADER_CP] != null) {
      addClasspath(headers[HEADER_CP][0]);
    }

    List args = headers[HEADER_ARG];
    for (Iterator<String> it = headers[HEADER_ARG].iterator(); it.hasNext(); ) {
      String s = it.next();
      if (s == "-cp") {
        it.remove();
        String classpath = it.next();
        addClasspath(classpath);
        it.remove();
      }
    }
    GroovyMain2.main(args as String[])
  }

  void checkHeaders(headers) {
    assert headers[HEADER_CURRENT_WORKING_DIR] != null &&
       headers[HEADER_CURRENT_WORKING_DIR][0]
  }

  void run() {
    try {
      soc.withStreams { ins, outs ->
        try {
          Map<String, List<String>> headers = readHeaders(ins);
          setupStandardStreams(ins, outs);
          if (System.getProperty("groovyserver.verbose") == "true") {
            headers.each {k,v ->
              originalErr.println " $k = $v"
            }
          }
          checkHeaders(headers)
          if (currentDir != null
              && System.getProperty('user.dir') != headers[HEADER_CURRENT_WORKING_DIR][0]) {
            
            throw new GroovyServerException("Can't change current directory because of another session running on different dir: "+headers[HEADER_CURRENT_WORKING_DIR][0]);
            synchronized (GroovyServer.class) {
              setCurrentDir(headers[HEADER_CURRENT_WORKING_DIR][0]);
            }
          }
          process(headers);
          outs.write((HEADER_STATUS+": 0\n").bytes);
          outs.write("\n".bytes);
        }
        catch (ExitException e) {
          // GroovyMain2 throws ExitException when
          // it catches ExitException.
          outs.write((HEADER_STATUS+": "+e.exitStatus+ "\n").bytes);
          outs.write("\n".bytes);
        }
        catch (Throwable t) {
          t.printStackTrace(originalErr)
          t.printStackTrace(System.err)
        }
      }
    }
    finally {
      synchronized (GroovyServer.class) {
        dirOwner = null;
      }
      if (System.getProperty("groovyserver.verbose") == "true") {
        originalErr.println("socket close")
      }
      soc.close()
    }
  }

  static void main(String[] args) {
		
    def port = DEFAULT_PORT;
		
    if (System.getProperty('groovy.server.port') != null) {
      port = Integer.parseInt(System.getProperty('groovy.server.port'))
    }
		
    System.setProperty('groovy.runningmode', "server")
		
    System.setSecurityManager(new NoExitSecurityManager2());
		
    def serverSocket = new ServerSocket(port)
		
    Thread worker = null;
    while (true) {
      def soc = serverSocket.accept()
			
      if (soc.localSocketAddress.address.isLoopbackAddress()) {
        if (System.getProperty("groovyserver.verbose") == "true") {
          originalErr.println "accept soc="+soc
        }
				
        // Create new thraed for each connections.
        // Here, don't use ExecutorService or any thread pool system.
        // Because the System.(in/out/err) streams are used distinctly
        // by thread instance. In the other words, threads can't be pooled.
        // So this 'new Thread()' is nesessary.
        //
        worker = new Thread(new GroovyServer(soc:soc));
        worker.start()
      }
      else {
        System.err.println("allow connection from loopback address only")
      }
    }
  }
}

