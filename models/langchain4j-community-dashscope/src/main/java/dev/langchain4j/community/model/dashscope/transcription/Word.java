package dev.langchain4j.community.model.dashscope.transcription;

// 定义用于解析JSON的类
public class Word {
    private int begin_time;
    private int end_time;
    private String text;
    private String punctuation;
}
