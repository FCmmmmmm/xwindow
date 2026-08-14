package org.plugin.window.rpc.gaode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GaodeRestApi {

    /**
     * 查询高德天气。
     *
     * @param cityId     高德城市编码（adcode）
     * @param extensions "base"（实时天气）或 "all"（天气预报）
     * @param apiKey     高德 Web 服务 API Key，由调用方从 {@code GaodeApiKeyStore} 读取
     */
    public Weather Weather(String cityId, String extensions, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            // 未配置 API Key，调用方会将其视为获取失败并提示
            return null;
        }
        HttpURLConnection connection = null;
        try {
            String urlStr = "https://restapi.amap.com/v3/weather/weatherInfo?city=" + cityId
                    + "&key=" + apiKey.trim() + "&extensions=" + extensions;
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            if (connection.getResponseCode() == 200) {
                StringBuilder jsonStr = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonStr.append(line);
                    }
                }
                return Weather.fromJson(jsonStr.toString().trim());
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
