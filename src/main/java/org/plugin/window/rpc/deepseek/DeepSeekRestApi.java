package org.plugin.window.rpc.deepseek;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek 余额查询 REST 客户端，与 {@code rpc.gaode.GaodeRestApi} 保持同样的形状：
 * HttpURLConnection、短超时、异常不外抛。
 * <p>
 * 差别在于本接口需要 Bearer 鉴权，且要把「Key 无效」与「网络失败」区分开来反馈给 UI，
 * 因此返回 {@link Result} 而不是可能为 null 的模型对象。
 */
public class DeepSeekRestApi {

    private static final String BALANCE_URL = "https://api.deepseek.com/user/balance";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    /**
     * 一次查询的结果：成功时 {@code balance} 非空，失败时 {@code error} 为可直接展示的中文文案。
     */
    public record Result(Balance balance, String error) {

        public static Result ok(Balance balance) {
            return new Result(balance, null);
        }

        public static Result fail(String error) {
            return new Result(null, error);
        }

        public boolean isOk() {
            return balance != null;
        }
    }

    /**
     * 查询账户余额。必须在非 EDT 线程调用。
     *
     * @param apiKey DeepSeek API Key，为空时直接返回未配置提示
     */
    public Result Balance(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.fail("未配置 API Key");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BALANCE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            int code = connection.getResponseCode();
            if (code == 200) {
                StringBuilder jsonStr = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonStr.append(line);
                    }
                }
                Balance balance = Balance.fromJson(jsonStr.toString().trim());
                if (balance == null || balance.getBalanceInfos() == null) {
                    return Result.fail("响应解析失败");
                }
                return Result.ok(balance);
            } else if (code == 401 || code == 403) {
                return Result.fail("API Key 无效");
            } else if (code == 429) {
                return Result.fail("请求过于频繁");
            } else {
                return Result.fail("查询失败 (HTTP " + code + ")");
            }
        } catch (Exception e) {
            return Result.fail("查询失败，请检查网络");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
