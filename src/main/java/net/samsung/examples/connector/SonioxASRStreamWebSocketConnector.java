package net.samsung.examples.connector;

import net.samsung.examples.connector.dto.SonioxASRStreamWebSocket.ASRWebSocketResponse;
import net.samsung.examples.connector.dto.SonioxASRStreamWebSocket.SocketWordResponse;
import net.samsung.examples.connector.dto.SonioxASRStreamWebSocket.SpeechContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SonioxASRStreamWebSocketConnector extends SonioxASRConnector {
    private static final Logger logger = LoggerFactory.getLogger(SonioxASRStreamWebSocketConnector.class);

    private final String apiKey;
    private final HttpClient httpClient;
    private final URI wsUri;
    private WebSocket webSocket;
    private boolean isSocketOpen = false;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StringBuilder finalSpeech = new StringBuilder();
    private final StringBuilder nonFinalSpeech = new StringBuilder();

    public SonioxASRStreamWebSocketConnector(String address, int port, boolean useSSL, String apiKey) {
        super(address, port, useSSL, apiKey);
        ExecutorService executor = Executors.newCachedThreadPool();
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().executor(executor).build();
        this.wsUri = URI.create(address + "/transcribe-websocket");
    }

    public void connect(String languageCode, boolean useLowLatency, boolean nonFinal, String format, int sampleRate, int channels, boolean speakerDiarization, int minSpeaker, int maxSpeaker, String boostWords, int defaultBoostAmount) {

        if (webSocket != null) {
            throw new IllegalStateException("WebSocket is already set");
        }

        this.webSocket = this.httpClient.newWebSocketBuilder().buildAsync(wsUri, new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                logger.info("=== WebSocket opened ===");
                try {
                    // Send start request
                    JSONObject startRequest = new JSONObject();
                    startRequest.put("api_key", apiKey);
                    startRequest.put("audio_format", format);
                    startRequest.put("sample_rate_hertz", sampleRate);
                    startRequest.put("num_audio_channels", channels);
                    startRequest.put("include_nonfinal", nonFinal);
                    startRequest.put("min_num_speakers", minSpeaker);
                    startRequest.put("max_num_speakers", maxSpeaker);
                    startRequest.put("enable_streaming_speaker_diarization", speakerDiarization);
                    startRequest.put("model", getDefaultModel(languageCode, useLowLatency));
                    if (boostWords != null && !boostWords.isEmpty()) {
                        String speechContext = objectMapper.writeValueAsString(getSpeechContext(boostWords.split(","), defaultBoostAmount));
                        JSONObject speechContextObject = new JSONObject(speechContext);
                        startRequest.put("speech_context", speechContextObject);
                    }
                    logger.info(startRequest.toString());

                    webSocket.sendText(startRequest.toString(), true).join();

                    isSocketOpen = true;

                    WebSocket.Listener.super.onOpen(webSocket);
                } catch (JSONException | JsonProcessingException e) {
                    logger.error(e.getMessage());
                    logger.error(Arrays.toString(e.getStackTrace()));
                    throw new RuntimeException(e);
                }
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                nonFinalSpeech.setLength(0);
                int finalSpeaker = -1;
                int currSpeaker = -1;

                try {
                    ASRWebSocketResponse response = objectMapper.readValue(data.toString(), ASRWebSocketResponse.class);

                    boolean isFinal = !response.getFw().isEmpty() && response.getNfw().isEmpty();   // If fw exists and nfw is null -> final
                    finalSpeech.append(response.getFw().stream()
                            .map(SocketWordResponse::getT)
                            .collect(Collectors.joining()));
                    for (SocketWordResponse wordResponse : response.getNfw()) {
                        if (currSpeaker == -1) {
                            finalSpeaker = currSpeaker = wordResponse.getSpk();
                        }

                        nonFinalSpeech.append(wordResponse.getT());
                    }

                    if (finalSpeech.toString().matches(".*[.!?。]$")) {
                        logger.info("speaker={} final=1 speech={}", finalSpeaker, finalSpeech);
                        finalSpeech.setLength(0);
                    } else {
                        if (nonFinalSpeech.length() > 0 || finalSpeech.length() > 0) {
                            logger.info("speaker={} final={} speech={}/{}", currSpeaker, nonFinalSpeech.length() > 0 ? "0" : "1", finalSpeech, nonFinalSpeech);
                        }
                        if (isFinal) finalSpeech.setLength(0);
                    }

                    return WebSocket.Listener.super.onText(webSocket, data, last);
                } catch (JsonProcessingException e) {
                    logger.error(e.getMessage());
                    logger.error(Arrays.toString(e.getStackTrace()));
                    throw new RuntimeException(e);
                }
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                if (!reason.isEmpty()) {
                    logger.error("Close error: {}", reason);
                }

                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                logger.error("On Error: {}", error.getMessage());
            }
        }).join();
    }

    private SpeechContext getSpeechContext(String[] boostWords, Integer defaultBoostAmount) {
        SpeechContext speech_context = new SpeechContext();
        speech_context.setEntries(new ArrayList<>());

        if (defaultBoostAmount == null) {
            defaultBoostAmount = 0;
        }

        int i = 0;
        for (String word : boostWords) {
            if (word.equals(" ") || word.isEmpty()) {
                continue;
            }
            String[] splitValues = word.split(":");

            System.out.println("[" + i + "] Phrases:" + splitValues[0]);
            System.out.println("[" + i + "] Boost:" + (splitValues.length == 2 ? Double.parseDouble(splitValues[1]) : defaultBoostAmount));

            // Create SpeechContextEntry
            SpeechContext.SpeechContextEntry entry = new SpeechContext.SpeechContextEntry();
            entry.setPhrases(List.of(splitValues[0]));
            entry.setBoost(splitValues.length == 2 ? Double.parseDouble(splitValues[1]) : defaultBoostAmount);

            // Add SpeechContextEntry
            speech_context.getEntries().add(entry);

            i++;
        }

        return speech_context;
    }

    public void recognize(byte[] buffer, int bufferLen) {
        ByteBuffer audioData = ByteBuffer.allocate(bufferLen);
        audioData.put(buffer);
        audioData.flip();
        if (webSocket != null && isSocketOpen && audioData.hasRemaining()) {
            webSocket.sendBinary(audioData, true);
        } else {
            logger.warn("WebSocket is not ready of empty buffer.");
        }
    }

    public void complete() {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "WebSocket normally closed");
    }

}
