@echo off


echo %OSTYPE%

set LOCAL_PATH=%~dp0
set TARGET_DIR=%LOCAL_PATH%..\target\%1
set CUR_PATH=%CD%

IF NOT EXIST %LOCAL_PATH%%1\ (
echo nothing to compile for %1 
exit 1
)


md %TARGET_DIR%

robocopy %LOCAL_PATH%%1 %TARGET_DIR% /E /NFL /NDL /NJH /NJS /nc /ns /np

cd %TARGET_DIR%

make

set FOLDER=windows_64
echo OS detected %FOLDER%

set OUTPUT_DIR=..\classes\META-INF\lib\%FOLDER%

md %OUTPUT_DIR%

robocopy target\release %OUTPUT_DIR% /E /NFL /NDL /NJH /NJS /nc /ns /np

cd %CUR_PATH%

echo %LOCAL_PATH%
echo %TARGET_DIR%
echo %CUR_PATH%
