#!/bin/bash

JAVA_HOME="<JDK_BIN_PATH>"
PROGRAM_PATH="./"

export CLASSPATH="${PROGRAM_PATH}speech-recognition-client-1.0.3-jar-with-dependencies.jar:${PROGRAM_PATH}conf/"

java -cp ${CLASSPATH} net.samsung.examples.SpeechRecognitionClient
