@ECHO OFF
SETLOCAL
CD /D "%~dp0"
IF EXIST "gradle\wrapper\gradle-wrapper.jar" (
  java -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  gradle %*
)
ENDLOCAL
