@echo off
REM 快速启动 WatchWorld Native 桌面预览
REM 双击此文件即可运行，无需打开终端手动输入命令
cd /d "%~dp0"
call gradlew.bat :desktopApp:run
pause
