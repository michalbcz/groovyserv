#!/usr/bin/ruby
#
# Copyright 2009-2010 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
require 'socket'

DESTPORT=1961

def mk_dir(path)
  begin
    if File.ftype(path) == "directory" then
      return true
    end
  rescue Errno::ENOENT
  end
  Dir.mkdir(path);
end

def start_server
  puts "starting server..."
  mk_dir(ENV['HOME']+"/.groovy")
  mk_dir(ENV['HOME']+"/.groovy/groovyserver")
  system (File.dirname($0) + '/groovyserver >> ~/.groovy/groovyserver/groovyserver.log 2>&1')
end

def read_headers(soc)
  headers={}
  while (line = soc.gets) != nil && line != "\n" do
    chomp line
    /([A-Za-z]*): (.*)/ =~ line
    headers[$1]=$2;
  end
  headers
end

def session(s)
  s.puts("Cwd: "+Dir::getwd)
  ARGV.each {|it|
    s.puts("Arg: "+it)
  }
  f = open(ENV['HOME']+"/.groovy/groovyserver/key")
  s.puts("Cookie: "+f.read)
  f.close()
  s.puts("")

  while true do
    IO.select([s,$stdin]).each {|ins|
      if ins[0] == s then
        headers = read_headers(s)
        if (headers['Status'] != nil) then
          exit(headers['Status'].to_i)
        end
        data = s.read(headers['Size'].to_i)
        if data == nil then
          break
        end
        if headers['Channel'] == 'o' then
          $stdout.print data
        elsif headers['Channel'] == 'e' then
          $stderr.print data
        end
      elsif ins[0] == $stdin then
        begin
          data = $stdin.read_nonblock(512)
        rescue EOFError
          s.write("Size: 0\n\n")
        else
          s.write("Size: #{data.length}\n\n")
          s.write(data)
        end
      end
    }
  end
end

Signal.trap(:INT) { $soc.close }

begin
  $soc = TCPSocket.open("localhost", DESTPORT)
rescue Errno::ECONNREFUSED
  start_server
  retry
end
begin
  session($soc)
rescue
  exit(1)
ensure
  if ! $soc.closed? then
    $soc.close
  end
end
