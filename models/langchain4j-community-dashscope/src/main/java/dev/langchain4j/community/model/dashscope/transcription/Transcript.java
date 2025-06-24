package dev.langchain4j.community.model.dashscope.transcription;

import java.util.List;

public class Transcript {
    private int channel_id;
    private int content_duration_in_milliseconds;
    private String text;
    private List<Sentence> sentences;

    public String getText() {
        return text;
    }

}
