package dev.langchain4j.community.model.dashscope;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionTaskResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.community.model.dashscope.transcription.Transcript;
import dev.langchain4j.community.model.dashscope.transcription.TranscriptionResponse;
import io.reactivex.Flowable;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class QwenTtsModel {

    private final String apiKey;
    private final String modelName;

    public QwenTtsModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    public void streamCall(String content) throws ApiException, NoApiKeyException, UploadFileException {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(this.apiKey)
                .model(this.modelName)
                .text(content)
                .voice(AudioParameters.Voice.CHERRY)
                .build();

        Flowable<MultiModalConversationResult> result = conv.streamCall(param);
        result.blockingForEach(r -> {
            try {
                // 1. 获取Base64编码的音频数据
                String base64Data = r.getOutput().getAudio().getData();
                byte[] audioBytes = Base64.getDecoder().decode(base64Data);

                // 2. 配置音频格式（根据API返回的音频格式调整）
                AudioFormat format = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        24000, // 采样率（需与API返回格式一致）
                        16,    // 采样位数
                        1,     // 声道数
                        2,     // 帧大小（位数/字节数）
                        16000, // 数据传输率
                        false  // 是否压缩
                );

                // 3. 实时播放音频数据
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    if (line != null) {
                        line.open(format);
                        line.start();
                        line.write(audioBytes, 0, audioBytes.length);
                        line.drain();
                    }
                }
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        });


    }


    public String transcriptionToText(String filePath) {

        // 创建转写请求参数，需要用真实apikey替换your-dashscope-api-key
        TranscriptionParam param =
                TranscriptionParam.builder()
                        // 若没有将API Key配置到环境变量中，需将下面这行代码注释放开，并将apiKey替换为自己的API Key
                         .apiKey(apiKey)
                        .model(modelName)
                        // “language_hints”只支持paraformer-v2和paraformer-realtime-v2模型
                        .parameter("language_hints", new String[]{"zh", "en"})
                        .fileUrls(
                                Arrays.asList(filePath))
                        .build();


        try {
            Transcription transcription = new Transcription();
            // 提交转写请求
            TranscriptionResult result = transcription.asyncCall(param);
            // 等待转写完成
//            System.out.println("RequestId: " + result.getRequestId());
            result = transcription.wait(
                    TranscriptionQueryParam.FromTranscriptionParam(param, result.getTaskId()));
            // 获取转写结果
            List<TranscriptionTaskResult> taskResultList = result.getResults();
            if (taskResultList != null && taskResultList.size() > 0) {
                for (TranscriptionTaskResult taskResult : taskResultList) {
                    String transcriptionUrl = taskResult.getTranscriptionUrl();
                    HttpURLConnection connection =
                            (HttpURLConnection) new URL(transcriptionUrl).openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();
                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    // 反序列化为自定义对象
                    TranscriptionResponse response = gson.fromJson(reader, TranscriptionResponse.class);

                    Transcript transcript = response.getTranscripts().get(0);
                    if (transcript != null) {
                        return transcript.getText();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
        }

        return "";
    }


    public void sambert(String content) {
        CountDownLatch latch = new CountDownLatch(1);
        SpeechSynthesizer synthesizer = new SpeechSynthesizer();
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .text(content)
                        .model(modelName)
                        .sampleRate(48000)
                        .format(SpeechSynthesisAudioFormat.PCM) // 流式合成使用PCM或者MP3
                        .build();

        // 播放线程
        class PlaybackRunnable implements Runnable {
            // 设置音频格式，请根据实际自身设备，合成音频参数和平台选择配置
            // 这里选择48k16bit单通道，建议客户根据选用的模型采样率情况和自身设备兼容性选择其他采样率和格式
            private AudioFormat af = new AudioFormat(48000, 16, 1, true, false);
            private DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
            private SourceDataLine targetSource = null;
            private AtomicBoolean runFlag = new AtomicBoolean(true);
            private ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();

            // 准备播放器
            public void prepare() throws LineUnavailableException {
                targetSource = (SourceDataLine) AudioSystem.getLine(info);
                targetSource.open(af, 4096);
                targetSource.start();
            }

            public void put(ByteBuffer buffer) {
                queue.add(buffer);
            }

            // 停止播放
            public void stop() {
                runFlag.set(false);
            }

            @Override
            public void run() {
                if (targetSource == null) {
                    return;
                }

                while (runFlag.get()) {
                    if (queue.isEmpty()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {

                        }
                        continue;
                    }

                    ByteBuffer buffer = queue.poll();
                    if (buffer == null) {
                        continue;
                    }

                    byte[] data = buffer.array();
                    targetSource.write(data, 0, data.length);
                }

                // 将缓存全部播放完
                if (!queue.isEmpty()) {
                    ByteBuffer buffer = null;
                    while ((buffer = queue.poll()) != null) {
                        byte[] data = buffer.array();
                        targetSource.write(data, 0, data.length);
                    }
                }

                // 释放播放器
                targetSource.drain();
                targetSource.stop();
                targetSource.close();
            }
        }

        // 创建一个继承自ResultCallback<SpeechSynthesisResult>的子类来实现回调接口
        class ReactCallback extends ResultCallback<SpeechSynthesisResult> {
            private PlaybackRunnable playbackRunnable = null;

            public ReactCallback(PlaybackRunnable playbackRunnable) {
                this.playbackRunnable = playbackRunnable;
            }

            // 当服务侧返回流式合成结果后回调
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                // 通过getAudio获取流式结果二进制数据
                if (result.getAudioFrame() != null) {
                    // 将数据流式推给播放器
                    playbackRunnable.put(result.getAudioFrame());
                }
            }

            // 当服务侧完成合成后回调
            @Override
            public void onComplete() {
                // 告知播放线程结束
                playbackRunnable.stop();
                latch.countDown();
            }

            // 当出现错误时回调
            @Override
            public void onError(Exception e) {
                // 告诉播放线程结束
                System.out.println(e);
                playbackRunnable.stop();
                latch.countDown();
            }
        }

        PlaybackRunnable playbackRunnable = new PlaybackRunnable();
        try {
            playbackRunnable.prepare();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        Thread playbackThread = new Thread(playbackRunnable);
        // 启动播放线程
        playbackThread.start();
        // 带Callback的call方法将不会阻塞当前线程
        synthesizer.call(param, new ReactCallback(playbackRunnable));
//        System.out.println("requestId: " + synthesizer.getLastRequestId());
        // 等待合成完成
        try {
            latch.await();
            // 等待播放线程全部播放完
            playbackThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}

