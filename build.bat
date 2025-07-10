@echo off
echo Building VelocityEssentials...

echo Building Velocity plugin...
call mvn clean package

echo Building Paper backend...
cd backend
call mvn clean package
cd ..

if not exist output mkdir output
copy target\*.jar output\
copy backend\target\*.jar output\

echo.
echo Build complete! JARs are in the output\ directory:
dir output\