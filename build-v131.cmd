@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
set "ANDROID_HOME=C:\Users\owner\AppData\Local\Android\Sdk"
set "GRADLE_USER_HOME=C:\Users\owner\AppData\Local\Temp\suicanfc-kd-gradle-home"
call "C:\Users\owner\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" --offline --no-daemon :app:assembleDefaultRelease
