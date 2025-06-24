package dev.langchain4j.community.model.dashscope.transcription;

import java.util.List;

public class Sentence {
    private int begin_time;
    private int end_time;
    private String text;
    private List<Word> words;
}
