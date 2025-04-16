package net.samsung.examples.connector.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EachWord {
    private int channel;
    private int speaker;
    private String text;
    private int startMs;
}
