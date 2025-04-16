@echo on

set JAVA_HOME=C:\tools\jdk\jdk-17.0.2\bin
set PROGRAM_PATH=./

set CLASSPATH="./target/speech-recognition-client-1.0.3-jar-with-dependencies.jar;%PROGRAM_PATH%conf/"

java -cp %CLASSPATH% net.samsung.examples.SpeechRecognitionClient
