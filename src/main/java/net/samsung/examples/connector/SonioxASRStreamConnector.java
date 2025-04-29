package net.samsung.examples.connector;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soniox.speech_service.SpeechServiceGrpc;
import soniox.speech_service.SpeechServiceOuterClass;

import java.io.IOException;

public class SonioxASRStreamConnector extends SonioxASRConnector {
    private static final Logger logger = LoggerFactory.getLogger(SonioxASRStreamConnector.class);

    private StringBuilder finalSpeech = new StringBuilder();

    private StreamObserver<SpeechServiceOuterClass.TranscribeStreamRequest> stream = null;

    public SonioxASRStreamConnector(String address, int port, boolean useSSL, String apiKey) {
        super(address, port, useSSL, apiKey);
    }

    public void connect(String languageCode, boolean useLowLatency, boolean nonFinal, String format, int sampleRate, int channels, boolean speakerDiarization, int minSpeaker, int maxSpeaker, String boostWords, int defaultBoostAmount) throws IOException {

        if (stream != null) {
            throw new IllegalStateException("stream is already set");
        }

        // Create the channel.
        final ManagedChannel channel = useProxy ? getProxyChannel() : getNonProxyChannel(useSSL);

        // Create the stub.
        SpeechServiceGrpc.SpeechServiceStub stub = SpeechServiceGrpc.newStub(channel);
        stream = stub.transcribeStream(new StreamObserver<SpeechServiceOuterClass.TranscribeStreamResponse>() {
            @Override
            public void onNext(SpeechServiceOuterClass.TranscribeStreamResponse response) {
                if (!response.hasResult()) {
                    return;
                }

                // local 변수
                StringBuilder nonFinalSpeech = new StringBuilder();
                int finalSpeaker = -1;
                int currSpeaker = -1;

                for (SpeechServiceOuterClass.Word word : response.getResult().getWordsList()) {

                    if (word.getText().isEmpty()) {
                        continue;
                    }

                    if (currSpeaker == -1) {
                        finalSpeaker = currSpeaker = word.getSpeaker();
                    }

                    // 계속 단어를 기록하여 문장을 완성한다
                    if (word.getIsFinal()) {
                        if (finalSpeech.length() != 0 || !word.getText().equals(" ")) {

                            //finalSpeech.append("(" + String.valueOf(word.getSpeaker()) + ")" + word.getText());
                            finalSpeech.append(word.getText());
                        }

                        // Log the finalized sentence
                        // If it's the final result and 2 seconds have passed, or punctuation is detected, then split the sentence
                        if (word.getText().matches("[.!?。]") || finalSpeaker != word.getSpeaker()) {

                            logger.info("speaker={} final=1 speech={}", finalSpeaker, finalSpeech.toString());

                            // Log and clear
                            finalSpeech = new StringBuilder();
                        }
                        finalSpeaker = currSpeaker = word.getSpeaker();
                    } else {
                        //nonFinalSpeech.append("(" + String.valueOf(word.getSpeaker()) + ")" + word.getText());
                        nonFinalSpeech.append(word.getText());
                        if (word.getSpeaker() != 0) {
                            currSpeaker = word.getSpeaker();
                        }
                    }
                }

                // Log the final tentative sentence
                if (nonFinal && currSpeaker != -1 && (finalSpeech.length() > 0 || nonFinalSpeech.length() > 0)) {
                    logger.info("speaker={} final={} speech={}/{}", currSpeaker, nonFinalSpeech.length() > 0 ? "0" : "1", finalSpeech.toString(), nonFinalSpeech.toString());
                }
            }

            @Override
            public void onCompleted() {
                logger.info("onCompleted");
            }

            @Override
            public void onError(Throwable t) {
                if (finalSpeech.length() > 0) {
                    // logging
                    logger.info("speaker=? final=1 speech={}/", finalSpeech);
                    finalSpeech = new StringBuilder();
                }

                io.grpc.Status status = io.grpc.Status.fromThrowable(t);
                logger.error("onError: {}", status);
            }
        });

        SpeechServiceOuterClass.TranscriptionConfig.Builder configBuilder = getConfigBuilder(
                languageCode, useLowLatency, format, sampleRate, channels,
                true, speakerDiarization, minSpeaker, maxSpeaker, boostWords, defaultBoostAmount, nonFinal);

        // Send the first request without data.
        SpeechServiceOuterClass.TranscribeStreamRequest request = SpeechServiceOuterClass.TranscribeStreamRequest.newBuilder()
                .setApiKey(apiKey)
                .setConfig(configBuilder)
                .build();

        stream.onNext(request);

        logger.info("connect() requested");
    }

    public void recognize(byte[] buffer, int bufferLen) throws IOException {
        if (stream == null) {
            throw new IllegalStateException("stream is not set, yet");
        }

        ByteString audioData = ByteString.copyFrom(buffer, 0, bufferLen);
        SpeechServiceOuterClass.TranscribeStreamRequest request = SpeechServiceOuterClass.TranscribeStreamRequest.newBuilder().setAudio(audioData).build();
        stream.onNext(request);
    }

    public void complete() throws IOException {
        if (stream == null) {
            throw new IllegalStateException("stream is not set, yet");
        }

        stream.onCompleted();
        stream = null;

        logger.info("completed !!");
    }
}
