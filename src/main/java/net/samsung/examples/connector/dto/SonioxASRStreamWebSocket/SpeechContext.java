package net.samsung.examples.connector.dto.SonioxASRStreamWebSocket;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SpeechContext {
    private List<SpeechContextEntry> entries;

    @Data
    @NoArgsConstructor
    public static class SpeechContextEntry {
        private List<String> phrases;
        private double boost;
    }
}
