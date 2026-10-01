@echo off
setlocal
cd /d "%~dp0"
set "APP_JAR=app\controle-gastos-1.0.0.jar"
if exist "%APP_JAR%" goto run
set "APP_JAR=target\controle-gastos-1.0.0.jar"
if exist "%APP_JAR%" goto run
echo Compilando o projeto. A primeira execucao precisa de internet e JDK 21.
call mvnw.cmd -B -ntp -DskipTests package
if errorlevel 1 goto failed
:run
echo.
echo Abra http://localhost:8080 no navegador quando aparecer "Started".
echo Os dados de demonstracao sao ficticios e desaparecem ao encerrar.
echo Para encerrar, pressione Ctrl+C.
echo.
java -jar "%APP_JAR%" --spring.profiles.active=demo
if errorlevel 1 goto failed
exit /b 0
:failed
echo.
echo Nao foi possivel iniciar. Confira o Java 21 e as instrucoes do README.
pause
exit /b 1
