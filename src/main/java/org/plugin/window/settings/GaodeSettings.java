package org.plugin.window.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 应用级持久化配置，保存高德天气卡片的 API Key 与城市信息，存储于 IDE 配置目录的 xwindow-gaode.xml。
 * <p>
 * API Key 也存在这里（本地 IDE 配置，不随源码分发），凭据库仅作为兼容兜底，见 {@link GaodeApiKeyStore}。
 */
@State(
        name = "XWindowGaodeSettings",
        storages = @Storage("xwindow-gaode.xml")
)
public class GaodeSettings implements PersistentStateComponent<GaodeSettings> {

    /** 高德城市编码（adcode），默认北京东城。 */
    public String cityCode = "110101";

    /** 卡片上显示的城市名称。 */
    public String cityName = "北京";

    /** 高德 Web 服务 API Key。 */
    public String apiKey = "";

    public static GaodeSettings getInstance() {
        return ApplicationManager.getApplication().getService(GaodeSettings.class);
    }

    @Override
    public GaodeSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GaodeSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getCityCode() {
        return cityCode;
    }

    public void setCityCode(String cityCode) {
        this.cityCode = cityCode;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
