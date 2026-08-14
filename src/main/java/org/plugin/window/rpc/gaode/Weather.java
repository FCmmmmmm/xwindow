package org.plugin.window.rpc.gaode;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Weather {
    private String status;
    private String count;
    private String info;
    private String infocode;

    @SerializedName("lives")
    private List<LiveWeather> lives;

    @SerializedName("forecasts")
    private List<Forecast> forecasts;

    public String getStatus() {
        return status;
    }

    public String getCount() {
        return count;
    }

    public String getInfo() {
        return info;
    }

    public String getInfocode() {
        return infocode;
    }

    public List<LiveWeather> getLives() {
        return lives;
    }

    public List<Forecast> getForecasts() {
        return forecasts;
    }

    public static Weather fromJson(String json) {
        return new Gson().fromJson(json, Weather.class);
    }

    /**
     * 实时天气数据
     */
    public static class LiveWeather {
        private String province;
        private String city;
        private String adcode;
        private String weather;
        private String temperature;
        private String winddirection;
        private String windpower;
        private String humidity;
        private String reporttime;

        public String getProvince() {
            return province;
        }

        public String getCity() {
            return city;
        }

        public String getAdcode() {
            return adcode;
        }

        public String getWeather() {
            return weather;
        }

        public String getTemperature() {
            return temperature;
        }

        public String getWinddirection() {
            return winddirection;
        }

        public String getWindpower() {
            return windpower;
        }

        public String getHumidity() {
            return humidity;
        }

        public String getReporttime() {
            return reporttime;
        }
    }

    /**
     * 天气预报数据
     */
    public static class Forecast {
        private String city;
        private String adcode;
        private String province;
        private String reporttime;

        @SerializedName("casts")
        private List<Cast> casts;

        public String getCity() {
            return city;
        }

        public String getAdcode() {
            return adcode;
        }

        public String getProvince() {
            return province;
        }

        public String getReporttime() {
            return reporttime;
        }

        public List<Cast> getCasts() {
            return casts;
        }
    }

    /**
     * 预报详情
     */
    public static class Cast {
        private String date;
        private String week;
        private String dayweather;
        private String nightweather;
        private String daytemp;
        private String nighttemp;
        private String daywind;
        private String nightwind;
        private String daypower;
        private String nightpower;

        public String getDate() {
            return date;
        }

        public String getWeek() {
            return week;
        }

        public String getDayweather() {
            return dayweather;
        }

        public String getNightweather() {
            return nightweather;
        }

        public String getDaytemp() {
            return daytemp;
        }

        public String getNighttemp() {
            return nighttemp;
        }

        public String getDaywind() {
            return daywind;
        }

        public String getNightwind() {
            return nightwind;
        }

        public String getDaypower() {
            return daypower;
        }

        public String getNightpower() {
            return nightpower;
        }
    }
}
