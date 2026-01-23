@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-22
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d %1
mvn %2 %3 %4 %5 %6 %7 %8 %9
