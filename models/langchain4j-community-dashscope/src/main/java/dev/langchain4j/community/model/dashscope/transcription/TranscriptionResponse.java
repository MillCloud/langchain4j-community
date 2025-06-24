package dev.langchain4j.community.model.dashscope.transcription;

import java.util.List;

public class TranscriptionResponse {
    private String file_url;
    private List<Transcript> transcripts;

    public List<Transcript> getTranscripts() {
        return transcripts;
    }
}
