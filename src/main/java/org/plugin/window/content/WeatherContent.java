package org.plugin.window.content;

import org.plugin.window.rpc.gaode.GaodeRestApi;
import org.plugin.window.rpc.gaode.Weather;
import org.plugin.window.settings.GaodeApiKeyStore;

import java.util.ArrayList;
import java.util.List;

public class WeatherContent {

    /** 结构化天气信息，供面板按设计稿布局（大号气温 + 天气/湿度）。 */
    public record WeatherInfo(String temperature, String weather, String humidity, String icon) {}

    /** 单日预报：日期、星期、白天/夜间天气、白天/夜间气温、风力。 */
    public record DayForecast(String date, String week, String dayWeather, String nightWeather,
                              String dayTemp, String nightTemp, String dayWind, String dayPower) {
    }

    /**
     * 查询多日预报，失败或无数据时返回空列表。
     * <p>
     * 注意：高德 v3 天气接口 {@code extensions=all} 只返回<b>当天起 4 天</b>的预报，
     * 并非完整一周；要拿 7–15 天需换和风天气等其它数据源。
     * 必须在非 EDT 线程调用（内含网络请求）。
     */
    public static List<DayForecast> getForecast(String cityId) {
        GaodeRestApi api = new GaodeRestApi();
        Weather response = api.Weather(cityId, "all", GaodeApiKeyStore.getKey());
        if (response == null || response.getForecasts() == null || response.getForecasts().isEmpty()) {
            return List.of();
        }
        Weather.Forecast forecast = response.getForecasts().get(0);
        if (forecast.getCasts() == null) {
            return List.of();
        }
        List<DayForecast> days = new ArrayList<>();
        for (Weather.Cast cast : forecast.getCasts()) {
            days.add(new DayForecast(
                    cast.getDate(),
                    cast.getWeek(),
                    cast.getDayweather(),
                    cast.getNightweather(),
                    cast.getDaytemp(),
                    cast.getNighttemp(),
                    cast.getDaywind(),
                    cast.getDaypower()
            ));
        }
        return days;
    }

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

    /** 实况明细：比 {@link WeatherInfo} 多带风向风力与发布时间，供提示词使用。 */
    public record LiveDetail(String temperature, String weather, String humidity,
                             String windDirection, String windPower, String reportTime) {
    }

    /**
     * 查询实况明细（含风向风力），失败返回 {@code null}。
     * 必须在非 EDT 线程调用（内含网络请求）。
     */
    public static LiveDetail getLiveDetail(String cityId) {
        GaodeRestApi api = new GaodeRestApi();
        Weather response = api.Weather(cityId, "base", GaodeApiKeyStore.getKey());
        if (response == null || response.getLives() == null || response.getLives().isEmpty()) {
            return null;
        }
        Weather.LiveWeather live = response.getLives().get(0);
        return new LiveDetail(
                live.getTemperature(),
                live.getWeather(),
                live.getHumidity(),
                live.getWinddirection(),
                live.getWindpower(),
                live.getReporttime()
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
