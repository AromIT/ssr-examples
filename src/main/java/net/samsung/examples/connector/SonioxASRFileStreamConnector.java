package net.samsung.examples.connector;

import net.samsung.examples.connector.dto.EachWord;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soniox.speech_service.SpeechServiceGrpc;
import soniox.speech_service.SpeechServiceOuterClass;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class SonioxASRFileStreamConnector extends SonioxASRConnector {
    private static final Logger logger = LoggerFactory.getLogger(SonioxASRFileStreamConnector.class);

    private List<String> results;
    private List<EachWord> wordList;

    private final Object lock = new Object();

    final int BUFFER_SIZE = 5 * 1024 * 1024;    // 5MB Buffer

    private SpeechServiceGrpc.SpeechServiceStub stub = null;
    private StreamObserver<SpeechServiceOuterClass.TranscribeStreamRequest> stream = null;

    public SonioxASRFileStreamConnector(String address, int port, boolean useSSL, String apiKey) {
        super(address, port, useSSL, apiKey);
    }

    public void connect(String languageCode, boolean useLowLatency, boolean nonFinal, String format, int sampleRate, int channels, boolean speakerDiarization, int minSpeaker, int maxSpeaker, String boostWords, int boostAmount) throws IOException {

        if (stream != null) {
            throw new IllegalStateException("stream is already set");
        }

        results = new ArrayList<>();
        wordList = new ArrayList<>();

        // Create the channel.
        final ManagedChannel channel = useProxy ? getProxyChannel() : getNonProxyChannel(useSSL);

        // Create the stub.
        stub = SpeechServiceGrpc.newStub(channel);
        stream = stub.transcribeStream(new StreamObserver<SpeechServiceOuterClass.TranscribeStreamResponse>() {
            @Override
            public void onNext(SpeechServiceOuterClass.TranscribeStreamResponse response) {
                if (response.getResult().getWordsList().isEmpty()) {
                    return;
                }
                response.getResult().getWordsList().forEach(word -> {
                    wordList.add(
                            EachWord.builder()
                                    .channel(response.getResult().getChannel())
                                    .speaker(word.getSpeaker())
                                    .text(word.getText())
                                    .startMs(word.getStartMs())
                                    .build()
                    );
                });
            }

            @Override
            public void onError(Throwable throwable) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                logger.error("TranscribeStreamResponse onError() message={} trace={}", throwable.getMessage(), sw.toString());
                results.add(throwable.getMessage());

                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onCompleted() {
                logger.info("TranscribeStreamResponse onCompleted() results");

                // startMs 기준 정렬
                wordList.sort(Comparator.comparingInt(EachWord::getStartMs));

                // 설정값에 따라 출력 제어
                boolean enableMultiChannel = channels > 1; // 필요 시 외부 파라미터로 설정
                boolean enableSpeakerDiarization = speakerDiarization; // 필요 시 외부 파라미터로 설정

                int prevSpeaker = -1;
                int prevChannel = -1;
                StringBuilder sb = new StringBuilder();

                for (EachWord word : wordList) {
                    int currentSpeaker = word.getSpeaker();
                    int currentChannel = word.getChannel();

                    // speaker 또는 channel이 바뀌면 출력
                    boolean speakerChanged = enableSpeakerDiarization && currentSpeaker != prevSpeaker;
                    boolean channelChanged = enableMultiChannel && currentChannel != prevChannel;

                    if ((speakerChanged || channelChanged) && sb.length() > 0) {
                        StringBuilder logPrefix = new StringBuilder();
                        if (enableMultiChannel) logPrefix.append("Channel ").append(prevChannel);
                        if (enableMultiChannel && enableSpeakerDiarization) logPrefix.append(", ");
                        if (enableSpeakerDiarization) logPrefix.append("Speaker ").append(prevSpeaker);
                        logger.info(logPrefix + ": " + sb.toString().trim());
                        sb.setLength(0);
                    }

                    sb.append(word.getText());
                    prevSpeaker = currentSpeaker;
                    prevChannel = currentChannel;
                }

                // 마지막 조합 출력
                if (sb.length() > 0) {
                    StringBuilder logPrefix = new StringBuilder();
                    if (enableMultiChannel) logPrefix.append("Channel ").append(prevChannel);
                    if (enableMultiChannel && enableSpeakerDiarization) logPrefix.append(", ");
                    if (enableSpeakerDiarization) logPrefix.append("Speaker ").append(prevSpeaker);
                    logger.info(logPrefix + ": " + sb.toString().trim());
                }

                synchronized (lock) {
                    lock.notify();
                }
            }

        });

        SpeechServiceOuterClass.TranscriptionConfig.Builder configBuilder = getConfigBuilder(
                languageCode, useLowLatency, format, sampleRate, channels,
                true, speakerDiarization, minSpeaker, maxSpeaker, boostWords, boostAmount, nonFinal);

        // Send the first request without data.
        SpeechServiceOuterClass.TranscribeStreamRequest request =
                SpeechServiceOuterClass.TranscribeStreamRequest.newBuilder()
                        .setApiKey(apiKey)
                        .setConfig(configBuilder)
                        .build();

        stream.onNext(request);

        logger.info("connected !!");
    }

    public void recognize(String fileName) {
        logger.info("recognize file : {}", fileName);

        if (stream == null) {
            throw new IllegalStateException("stream is not set, yet");
        }

        // Read the file in chunks and send requests with data.
        try (final FileInputStream fileStream = new FileInputStream(fileName)) {

            byte[] buffer = new byte[BUFFER_SIZE];

            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                stream.onNext(SpeechServiceOuterClass.TranscribeStreamRequest.newBuilder()
                        .setAudio(ByteString.copyFrom(buffer, 0, bytesRead))
                        .build());
            }
        } catch (IOException e) {
            logger.error("upload() exception: " + e.getMessage());
            throw new RuntimeException(e);
        }

        stream.onCompleted();

        logger.info("recognized !!");
        logger.info("Waiting for result...");

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Completed !!");
    }
}
