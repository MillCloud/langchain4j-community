package dev.langchain4j.community.model.dashscope;

import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

public class QwenTtsModelTest {

    @Test
    void testStreamCall() throws NoApiKeyException, UploadFileException, InputRequiredException {

        String apiKey = "sk-623eb96409874092993fe7fd7d42e33d";

        QwenTtsModel qwenTtsModel = new QwenTtsModel(apiKey, "sambert-zhixiang-v1");
        String content = "Listen here, boy. I'm gonna teach you the secret formula on one condition. You can never let it fall into the hands of Plankton.";
        qwenTtsModel.sambert(content);

    }


    @Test
    void testCallWithLocalFile() throws NoApiKeyException, UploadFileException {

        String apiKey = "sk-623eb96409874092993fe7fd7d42e33d";

        QwenTtsModel qwenTtsModel = new QwenTtsModel(apiKey, "paraformer-v1");
        String defaultAudioFileUrl = "http://edf-kb.mtu.plus/jvs-public/ten_1/2_2/jvs-auth-mgr/audio/2025/06/18/2025-06-181935187218940887040-66cbedce47b45_1724640718.mp3";
        String rtn = qwenTtsModel.transcriptionToText(defaultAudioFileUrl);
        System.out.println(rtn);

    }

}
