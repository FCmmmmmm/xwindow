package org.plugin.window.rpc.deepseek;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek 对话补全接口（POST /chat/completions），与 {@link DeepSeekRestApi} 同一套风格：
 * HttpURLConnection、短连接、异常不外抛，失败一律翻成可直接展示的中文文案。
 * <p>
 * 与余额接口的差别：
 * <ul>
 *   <li>需要 POST JSON 请求体，所以显式 {@code doOutput} 写 UTF-8 字节；</li>
 *   <li>大模型首字延迟远大于余额接口，读超时放到 120s（余额接口只有 5s）；</li>
 *   <li>失败时优先取服务端返回的 {@code error.message}，拿不到才退回状态码文案。</li>
 * </ul>
 * 本类不做流式（stream=false）：一次返回整段 Markdown，弹窗渲染逻辑最简单，
 * 对「点一下等几秒出结论」的场景已经够用。
 */
public class DeepSeekChatApi {

    private static final String CHAT_URL = "https://api.deepseek.com/chat/completions";
    /** deepseek-chat 即 V3 系列，价格低、中文表达稳定，适合这类短文案生成。 */
    private static final String MODEL = "deepseek-chat";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 120000;
    private static final double TEMPERATURE = 1.0;
    private static final int MAX_TOKENS = 1600;

    /**
     * 一次对话的结果：成功时 {@code content} 为模型输出（Markdown），失败时 {@code error} 为中文提示。
     */
    public record Result(String content, String error) {

        public static Result ok(String content) {
            return new Result(content, null);
        }

        public static Result fail(String error) {
            return new Result(null, error);
        }

        public boolean isOk() {
            return error == null;
        }
    }

    /**
     * 发起一次单轮对话。必须在非 EDT 线程调用（内含网络请求，最长可能阻塞两分钟）。
     *
     * @param apiKey       DeepSeek API Key，为空时直接返回未配置提示
     * @param systemPrompt 系统提示词，约束角色与输出格式
     * @param userPrompt   用户提示词，承载本次要分析的上下文数据
     */
    public Result Chat(String apiKey, String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.fail("未配置 API Key，请在 设置 → 工具箱 DeepSeek 中填写");
        }

        HttpURLConnection connection = null;
        try {
            // Java 20 起 new URL(String) 已废弃，改用 URI.toURL()
            URL url = java.net.URI.create(CHAT_URL).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);

            byte[] body = buildRequestBody(systemPrompt, userPrompt).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();
            if (code == 200) {
                String json = readAll(connection.getInputStream());
                String content = extractContent(json);
                if (content == null || content.isBlank()) {
                    return Result.fail("模型返回为空，请重试");
                }
                return Result.ok(content.trim());
            }

            // 非 200：尽量把服务端的中文报错透出来，比只给状态码有用得多
            String detail = extractErrorMessage(readAllSafely(connection.getErrorStream()));
            return Result.fail(describeError(code, detail));
        } catch (java.net.SocketTimeoutException e) {
            return Result.fail("等待模型响应超时，请重试");
        } catch (Exception e) {
            return Result.fail("请求失败，请检查网络");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 组装请求体：单条 system + 单条 user，非流式。 */
    private static String buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);

        JsonArray messages = new JsonArray();
        messages.add(systemMessage);
        messages.add(userMessage);

        JsonObject root = new JsonObject();
        root.addProperty("model", MODEL);
        root.add("messages", messages);
        root.addProperty("temperature", TEMPERATURE);
        root.addProperty("max_tokens", MAX_TOKENS);
        root.addProperty("stream", false);
        return root.toString();
    }

    /** 取 choices[0].message.content；结构异常时返回 null。 */
    private static String extractContent(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return null;
            }
            return message.get("content").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 取 error.message；解析不出来返回空串。 */
    private static String extractErrorMessage(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("error")) {
                return "";
            }
            JsonObject error = root.getAsJsonObject("error");
            return error.has("message") ? error.get("message").getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 状态码 + 服务端详情 → 一句人话。 */
    private static String describeError(int code, String detail) {
        String suffix = (detail == null || detail.isBlank()) ? "" : "：" + detail;
        return switch (code) {
            case 401, 403 -> "API Key 无效或已过期" + suffix;
            case 402 -> "DeepSeek 账户余额不足，请先充值" + suffix;
            case 429 -> "请求过于频繁，请稍后再试" + suffix;
            case 500, 502, 503, 504 -> "DeepSeek 服务暂时不可用，请稍后重试" + suffix;
            default -> "生成失败 (HTTP " + code + ")" + suffix;
        };
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /** 读错误流：读不到（null 流或异常）时返回空串，不能因为取错误信息再抛一次。 */
    private static String readAllSafely(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            return readAll(in);
        } catch (Exception e) {
            return "";
        }
    }
}
