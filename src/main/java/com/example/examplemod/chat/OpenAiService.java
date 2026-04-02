package com.example.examplemod.chat;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class OpenAiService {

    private static final OpenAIClient client = OpenAIOkHttpClient.fromEnv();

    public static String ask(String prompt) {
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5_2)
                    .input(prompt)
                    .build();

            Response response = client.responses().create(params);

            StringBuilder sb = new StringBuilder();

            response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .forEach(outputText -> sb.append(outputText.text()));

            return sb.toString().trim();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}