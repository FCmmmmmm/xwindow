package org.plugin.window.content;

import org.plugin.window.rpc.gaode.GaodeRestApi;
import org.plugin.window.rpc.gaode.Weather;
import org.plugin.window.settings.GaodeApiKeyStore;

public class WeatherContent {

    /** 结构化天气信息，供面板按设计稿布局（大号气温 + 天气/湿度）。 */
    public record WeatherInfo(String temperature, String weather, String humidity, String icon) {}

    /**
     * 查询实时天气并返回结构化信息，失败时返回 {@code null}。
     * 必须在非 EDT 线程调用（内含网络请求）。
     */
    public static WeatherInfo getWeatherInfo(String cityId) {
        GaodeRestApi api = new GaodeRestApi();
        Weather response = api.Weather(cityId, "base", GaodeApiKeyStore.getKey());
        if (response == null || response.getLives() == null || response.getLives().isEmpty()) {
            return null;
        }

        Weather.LiveWeather live = response.getLives().get(0);
        return new WeatherInfo(
                live.getTemperature(),
                live.getWeather(),
                live.getHumidity(),
                iconFor(live.getWeather())
        );
    }

    public static String getWeatherText(String cityId, String extensions) {
        WeatherInfo info = WeatherInfoFrom(cityId, extensions);
        if (info == null) {
            return "获取天气信息失败";
        }
        return String.format(
                "%s°C · %s %s · 💧 %s%%",
                info.temperature(),
                info.icon(),
                info.weather(),
                info.humidity()
        );
    }

    /** 兼容旧调用：按给定扩展类型取 {@link WeatherInfo}，失败返回 null。 */
    private static WeatherInfo WeatherInfoFrom(String cityId, String extensions) {
        GaodeRestApi api = new GaodeRestApi();
        Weather response = api.Weather(cityId, extensions, GaodeApiKeyStore.getKey());
        if (response == null || response.getLives() == null || response.getLives().isEmpty()) {
            return null;
        }
        Weather.LiveWeather live = response.getLives().get(0);
        return new WeatherInfo(
                live.getTemperature(),
                live.getWeather(),
                live.getHumidity(),
                iconFor(live.getWeather())
        );
    }

    /** 根据天气描述动态匹配图标。 */
    private static String iconFor(String weatherDesc) {
        if (weatherDesc == null) {
            return "☁️";
        }
        if (weatherDesc.contains("晴")) {
            return "☀️";
        } else if (weatherDesc.contains("雨")) {
            return "🌧️";
        } else if (weatherDesc.contains("雪")) {
            return "❄️";
        } else if (weatherDesc.contains("云") || weatherDesc.contains("阴")) {
            return "☁️";
        }
        return "☁️";
    }
}
