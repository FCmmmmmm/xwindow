package org.plugin.window.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 应用级持久化配置，保存 DeepSeek 卡片的偏好与余额快照，存储于 IDE 配置目录的 xwindow-deepseek.xml。
 * <p>
 * 注意：API Key <b>不</b>存在这里，见 {@link DeepSeekApiKeyStore}。
 * <p>
 * DeepSeek 官方 API 只提供余额查询（GET /user/balance），没有用量/消耗接口，
 * 因此「今日已消耗」靠这里的余额快照做差值推算，只能覆盖插件运行期间的调用。
 */
@State(
        name = "XWindowDeepSeekSettings",
        storages = @Storage("xwindow-deepseek.xml")
)
public class DeepSeekSettings implements PersistentStateComponent<DeepSeekSettings> {

    /** 优先展示的币种，CNY 或 USD。 */
    public String preferredCurrency = "CNY";

    /** 是否随定时器自动刷新余额。 */
    public boolean autoRefreshEnabled = true;

    /** 上次查询到的余额，用于算「较上次」。金额一律以字符串存储，避免 double 精度误差。 */
    public String lastBalance = "";

    /** 上次查询时刻，ISO LocalDateTime 字符串。 */
    public String lastCheckTime = "";

    /** 今日起算基准余额，用于算「今日已消耗」。 */
    public String baselineBalance = "";

    /** 基准余额所属日期，ISO LocalDate 字符串；跨天后重新起算。 */
    public String baselineDate = "";

    public static DeepSeekSettings getInstance() {
        return ApplicationManager.getApplication().getService(DeepSeekSettings.class);
    }

    @Override
    public DeepSeekSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DeepSeekSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public void setPreferredCurrency(String preferredCurrency) {
        this.preferredCurrency = preferredCurrency;
    }

    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    public void setAutoRefreshEnabled(boolean autoRefreshEnabled) {
        this.autoRefreshEnabled = autoRefreshEnabled;
    }

    public String getLastBalance() {
        return lastBalance;
    }

    public void setLastBalance(String lastBalance) {
        this.lastBalance = lastBalance;
    }

    public String getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(String lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }

    public String getBaselineBalance() {
        return baselineBalance;
    }

    public void setBaselineBalance(String baselineBalance) {
        this.baselineBalance = baselineBalance;
    }

    public String getBaselineDate() {
        return baselineDate;
    }

    public void setBaselineDate(String baselineDate) {
        this.baselineDate = baselineDate;
    }
}
