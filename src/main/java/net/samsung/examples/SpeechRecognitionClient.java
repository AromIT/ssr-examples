package net.samsung.examples;

import net.samsung.examples.connector.SonioxASRFileStreamConnector;
import net.samsung.examples.connector.SonioxASRStreamWebSocketConnector;
import lombok.SneakyThrows;
import net.samsung.examples.connector.SonioxASRConnector;
import net.samsung.examples.connector.SonioxASRStreamConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SpeechRecognitionClient {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognitionClient.class);

    static String propFile = "./conf/application.properties";
    public static Properties prop = new Properties();

    public static void main(String[] args) throws InterruptedException, IOException {

        // 설정 파일 읽기
        readProperties();

        // Connector 생성
        String mode = prop.getProperty("recognition.mode");
        SonioxASRConnector connector = createSonioxASRConnector(mode);

        // Connector Proxy 세팅
        connector.setProxy(
                prop.getProperty("connector.useProxy").equals("true"),
                prop.getProperty("connector.proxyHost"),
                Integer.parseInt(prop.getProperty("connector.proxyPort"))
        );

        // Connector 연결
        connector.connect(
                prop.getProperty("recognition.languageCode"),
                prop.getProperty("recognition.lowLatency").equals("true"),
                prop.getProperty("recognition.nonfinal").equals("true"),
                prop.getProperty("stream.format"),
                Integer.parseInt(prop.getProperty("stream.sampleRate")),
                Integer.parseInt(prop.getProperty("stream.channels")),
                Boolean.parseBoolean(prop.getProperty("recognition.speakerDiarization")),
                Integer.parseInt(prop.getProperty("recognition.minSpeaker")),
                Integer.parseInt(prop.getProperty("recognition.maxSpeaker")),
                prop.getProperty("recognition.boostWords"),
                Integer.parseInt(prop.getProperty("recognition.defaultBoostAmount"))
        );

        // 인식
        recognize(mode, connector);

        int i = 0;

        // 인식 결과 대기
        while (!mode.contains("file")) {
            if (i++ % 30000 == 0) {
                logger.info("waiting for response ... if you want to exit, please press CTRL+C.");
            }
            Thread.sleep(1000);
        }

    }

    private static SonioxASRConnector createSonioxASRConnector(String mode) {
        logger.info("Mode: {}", mode);
        SonioxASRConnector connector = null;
        try {
            if (mode.equals("filestream")) {
                connector = createASRSonioxFileStreamConnector();
            } else if (mode.equals("micstream")) {
                connector = createSonioxASRStreamConnector();
            } else if (mode.equals("micstream_websocket")) {
                connector = createSonioxASRStreamWebSocketConnector();
            } else {
                throw new RuntimeException("the mode is invalid : " + mode);
            }
        } catch (InterruptedException | IOException ex) {
            logger.error(ex.getMessage());
            System.exit(1);
        }

        return connector;
    }

    private static SonioxASRConnector createASRSonioxFileStreamConnector() throws InterruptedException, IOException {
        return new SonioxASRFileStreamConnector(
                prop.getProperty("connector.host"),
                Integer.parseInt(prop.getProperty("connector.port")),
                prop.getProperty("connector.useSSL").equals("true"),
                prop.getProperty("recognition.apiKey")
        );
    }


    private static SonioxASRConnector createSonioxASRStreamConnector() throws InterruptedException, IOException {
        return new SonioxASRStreamConnector(
                prop.getProperty("connector.host"),
                Integer.parseInt(prop.getProperty("connector.port")),
                prop.getProperty("connector.useSSL").equals("true"),
                prop.getProperty("recognition.apiKey")
        );
    }

    private static SonioxASRConnector createSonioxASRStreamWebSocketConnector() throws InterruptedException, IOException {
        return new SonioxASRStreamWebSocketConnector(
                "wss://api.soniox.com",
                Integer.parseInt(prop.getProperty("connector.port")),
                prop.getProperty("connector.useSSL").equals("true"),
                prop.getProperty("recognition.apiKey")
        );
    }

    private static void recognize(String mode, SonioxASRConnector connector) throws IOException {
        if (mode.equals("filestream")) {
            recognizeAudioStreamFromFile((SonioxASRFileStreamConnector) connector);
        } else if (mode.equals("micstream")) {
            recognizeAudioStreamFromMic((SonioxASRStreamConnector) connector);
        } else if (mode.equals("micstream_websocket")) {
            recognizeAudioStreamFromMic((SonioxASRStreamWebSocketConnector) connector);
        } else {
            logger.error("the mode is invalid : {}", mode);
        }
    }

    private static void recognizeAudioStreamFromFile(SonioxASRFileStreamConnector connector) throws IOException {
        connector.recognize(prop.getProperty("file.audio"));
    }

    private static void recognizeAudioStreamFromMic(SonioxASRStreamConnector connector) {

        int BYTES_PER_BUFFER = Integer.parseInt(prop.getProperty("stream.sampleRate")) * 2 * Integer.parseInt(prop.getProperty("stream.channels")) / 4; // buffer size in bytes 16000 x 2(16bit/8bit) x 1(mono) / 4 (250ms)

        //logger.error("mic buffer size=" + BYTES_PER_BUFFER + " samplerate=" + Integer.parseInt(prop.getProperty("stream.sampleRate")) + " channels=" + Integer.parseInt(prop.getProperty("stream.channels")));

        TargetDataLine targetDataLine = null;

        AudioFormat audioFormat = new AudioFormat(Integer.parseInt(prop.getProperty("stream.sampleRate")), 16, Integer.parseInt(prop.getProperty("stream.channels")), true, false);
        DataLine.Info targetInfo =
                new DataLine.Info(
                        TargetDataLine.class,
                        audioFormat); // Set the system information to read from the microphone audio

        // 음성 마이크 입력 처리 Thread 가동
        if (!AudioSystem.isLineSupported(targetInfo)) {
            logger.error("Microphone not supported");
            System.exit(0);
        }

        final boolean[] latchDown = {false};

        try {

            // Target data line captures the audio stream the microphone produces.
            targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
            targetDataLine.open(audioFormat);

            TargetDataLine finalTargetDataLine = targetDataLine;
            class MicBuffer implements Runnable {

                @SneakyThrows
                @Override
                public void run() {
                    finalTargetDataLine.start();

                    while (finalTargetDataLine.isOpen()) {
                        byte[] data = new byte[BYTES_PER_BUFFER];
                        int numBytesRead = finalTargetDataLine.read(data, 0, data.length);
                        if ((numBytesRead <= 0) && (finalTargetDataLine.isOpen())) {
                            continue;
                        }

                        //logger.error("data.length=" + data.length + " numBytesRead=" + numBytesRead);

                        // recognize
                        connector.recognize(data.clone(), data.length);
                    }

                    connector.complete();
                    latchDown[0] = true;
                }
            }

            Thread micThread = new Thread(new MicBuffer());

            micThread.start();
        } catch (Exception e) {
            logger.error("Microphone not supported: " + e.getMessage());
        }
    }

    private static void recognizeAudioStreamFromMic(SonioxASRStreamWebSocketConnector connector) {
        logger.info("recognize...");

        int BYTES_PER_BUFFER = Integer.parseInt(prop.getProperty("stream.sampleRate")) * 2 * Integer.parseInt(prop.getProperty("stream.channels")) / 4; // buffer size in bytes 16000 x 2(16bit/8bit) x 1(mono) / 4 (250ms)

        //logger.error("mic buffer size=" + BYTES_PER_BUFFER + " samplerate=" + Integer.parseInt(prop.getProperty("stream.sampleRate")) + " channels=" + Integer.parseInt(prop.getProperty("stream.channels")));

        TargetDataLine targetDataLine = null;

        AudioFormat audioFormat = new AudioFormat(Integer.parseInt(prop.getProperty("stream.sampleRate")), 16, Integer.parseInt(prop.getProperty("stream.channels")), true, false);
        DataLine.Info targetInfo =
                new DataLine.Info(
                        TargetDataLine.class,
                        audioFormat); // Set the system information to read from the microphone audio

        if (!AudioSystem.isLineSupported(targetInfo)) {
            logger.error("Microphone not supported");
            System.exit(0);
        }

        final boolean[] latchDown = {false};

        try {

            // Target data line captures the audio stream the microphone produces.
            targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
            targetDataLine.open(audioFormat);

            TargetDataLine finalTargetDataLine = targetDataLine;
            class MicBuffer implements Runnable {

                @SneakyThrows
                @Override
                public void run() {
                    finalTargetDataLine.start();

                    while (finalTargetDataLine.isOpen()) {
                        byte[] data = new byte[BYTES_PER_BUFFER];
                        int numBytesRead = finalTargetDataLine.read(data, 0, data.length);
                        if ((numBytesRead <= 0) && (finalTargetDataLine.isOpen())) {
                            continue;
                        }

                        //logger.error("data.length=" + data.length + " numBytesRead=" + numBytesRead);

                        // recognize
                        connector.recognize(data.clone(), data.length);
                    }

                    connector.complete();
                    latchDown[0] = true;
                }
            }

            Thread micThread = new Thread(new MicBuffer());

            micThread.start();
        } catch (Exception e) {
            logger.error("Microphone not supported: " + e.getMessage());
        }
    }

    private static void readProperties() {

        logger.debug("Reading properties from ...");
        logger.debug("----------------------------------------------------------");

        try {
            FileInputStream input = new FileInputStream(propFile);
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            prop.load(reader);

        } catch (FileNotFoundException e) {
            logger.error("Fail to open configuration file : {}", propFile);
            return;
        } catch (IOException e) {
            logger.error("Fail to read configuration file : {}", propFile);
            return;
        }

        List<String> deleteList = new ArrayList<String>();
        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("//")) {
                deleteList.add(key);
            } else {
                logger.debug(key + "=" + prop.getProperty(key));
            }
        }
        prop.remove(deleteList);
        logger.debug("----------------------------------------------------------");
    }
}
