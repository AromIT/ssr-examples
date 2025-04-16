package net.samsung.examples.connector;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soniox.speech_service.SpeechServiceOuterClass;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class SonioxASRConnector {
    private static final Logger logger = LoggerFactory.getLogger(SonioxASRConnector.class);

    protected String address = "127.0.0.1";
    protected int port = 0;
    protected boolean useSSL = false;
    protected String apiKey = "";
    protected boolean useProxy = false;
    protected String proxyHost = "127.0.0.1";
    protected int proxyPort = 0;

    public SonioxASRConnector(String address, int port, boolean useSSL, String apiKey) {
        this.address = address;
        this.port = port;
        this.useSSL = useSSL;
        this.apiKey = apiKey;
    }

    public void setProxy(boolean useProxy, String proxyHost, int proxyPort) {
        this.useProxy = useProxy;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public abstract void connect(String languageCode, boolean useLowLatency, boolean nonFinal, String format, int sampleRate, int channels, boolean speakerDiarization, int minSpeaker, int maxSpeaker, String boostWords, int boostAmount) throws IOException;

    protected ManagedChannel getNonProxyChannel(boolean useSSL) {
        logger.info("getNonProxyChannel() useSSL={}", useSSL);
        return useSSL
                ? Grpc.newChannelBuilderForAddress(address, port, TlsChannelCredentials.newBuilder().build()).build()
                : Grpc.newChannelBuilderForAddress(address, port, InsecureChannelCredentials.create()).build();
    }

    protected ManagedChannel getProxyChannel() {
        return Grpc.newChannelBuilderForAddress(
                address, port, TlsChannelCredentials.newBuilder().build()).proxyDetector(buildSetting()).build();
    }

    protected ProxyDetector buildSetting() {
        logger.info("soniox.stt.proxy.host : {}", proxyHost);
        logger.info("soniox.stt.proxy.port : {}", proxyPort);

        return new ProxyDetector() {
            @Nullable
            @Override
            public ProxiedSocketAddress proxyFor(SocketAddress socketAddress) throws IOException {
                logger.info("proxyFor() socketAddress={}", socketAddress.toString());
                return HttpConnectProxiedSocketAddress.newBuilder()
                        .setProxyAddress(new InetSocketAddress(proxyHost, proxyPort))
                        .setTargetAddress((InetSocketAddress) socketAddress)
                        .build();
            }
        };
    }

    protected static SpeechServiceOuterClass.TranscriptionConfig.Builder getConfigBuilder(String languageCode, boolean useLowLatency, String format, int sampleRate, int channels, boolean isStream, boolean speakerDiarization, int minSpeaker, int maxSpeaker, String boostWords, int defaultBoostAmount, boolean nonFinal) {
        SpeechServiceOuterClass.TranscriptionConfig.Builder configBuilder = SpeechServiceOuterClass.TranscriptionConfig.newBuilder();

        if (!isStream && useLowLatency) {
            throw new RuntimeException("Low latency not supported");
        }

        configBuilder.setModel(getDefaultModel(languageCode, useLowLatency));

        if (format != null && !format.isEmpty()) {
            configBuilder.setAudioFormat(format);
        }
        if (sampleRate != 0) {
            configBuilder.setSampleRateHertz(sampleRate);
        }
        if (channels != 0) {
            configBuilder.setEnableSeparateRecognitionPerChannel(channels > 1);
            configBuilder.setNumAudioChannels(channels);
        }
        if (nonFinal) {
            configBuilder.setIncludeNonfinal(nonFinal);
        }

        if (speakerDiarization && languageCode.equals("en")) {
            if(useLowLatency) configBuilder.setEnableStreamingSpeakerDiarization(true);
            else configBuilder.setEnableGlobalSpeakerDiarization(true);

            configBuilder.setMinNumSpeakers(minSpeaker);
            configBuilder.setMaxNumSpeakers(maxSpeaker);
        }

        if (!languageCode.equals("zh") && boostWords != null && !boostWords.isEmpty() && !boostWords.equals(" ")) {
            configBuilder.setSpeechContext(getSpeechContexts(boostWords.split(","), defaultBoostAmount));
        }

        return configBuilder;
    }

    protected static String getDefaultModel(String languageCode, boolean useLowLatency) {
        String modelName = languageCode + "_v2" + (useLowLatency ? "_lowlatency" : "");
        logger.debug("modelName={}", modelName);
        return modelName;
    }

    protected static SpeechServiceOuterClass.SpeechContext getSpeechContexts(String[] boostWords, Integer defaultBoostWordAmount) {
        SpeechServiceOuterClass.SpeechContext.Builder scBuilder = SpeechServiceOuterClass.SpeechContext.newBuilder();

        if (defaultBoostWordAmount == null) {
            defaultBoostWordAmount = 0;
        }

        int i = 0;
        for (String word : boostWords) {
            if (word.equals(" ") || word.isEmpty()) {
                continue;
            }
            String[] splitValues = word.split(":");

            System.out.println("[" + i + "] Phrases:" + splitValues[0]);
            System.out.println("[" + i + "] Boost:" + (splitValues.length == 2 ? Double.parseDouble(splitValues[1]) : defaultBoostWordAmount));

            // Create SpeechContextEntry
            SpeechServiceOuterClass.SpeechContextEntry entry =
                    SpeechServiceOuterClass.SpeechContextEntry.newBuilder()
                            .addPhrases(splitValues[0])
                            .setBoost(splitValues.length == 2 ? Double.parseDouble(splitValues[1]) : defaultBoostWordAmount).build();

            // Add SpeechContextEntry
            scBuilder.addEntries(entry);

            i++;
        }

        return scBuilder.build();
    }

}
