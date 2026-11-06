@echo off
REM Runs every manual acceptance script through the CLI and prints the transcripts.
REM Usage (from the project root):  mvnw.cmd -q package -DskipTests  then  manual-tests\run-all.cmd
cd /d "%~dp0.."
if not exist target\course-waitlist-1.0.0.jar (echo Build first: mvnw.cmd package & exit /b 1)
for %%f in (manual-tests\ac*.txt) do (
  echo ==================================================================
  echo  %%f
  echo ==================================================================
  java -jar target\course-waitlist-1.0.0.jar < "%%f"
  echo.
)
