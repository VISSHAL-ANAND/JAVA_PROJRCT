@echo off
set "JAVA_HOME=C:\Users\Mithun\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64"
set "PATH=C:\Users\Mithun\apache-maven-3.9.9\bin;%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
echo Starting CAMPUSOS Desktop GUI...
mvn exec:java "-Dexec.mainClass=gui.LoginFrame"
