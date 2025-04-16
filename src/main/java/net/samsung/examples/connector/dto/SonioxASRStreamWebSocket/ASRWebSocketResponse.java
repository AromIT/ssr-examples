package net.samsung.examples.connector.dto.SonioxASRStreamWebSocket;

import lombok.Data;

import java.util.List;

@Data
public class ASRWebSocketResponse {
    private List<SocketWordResponse> fw;
    private List<SocketWordResponse> nfw;
    private int fpt;
    private int tpt;
    private String dbg;
    private List<String> spks;
    private SocketMetaDataResponse metadata;
}
