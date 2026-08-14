package org.plugin.window.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 应用级持久化配置，控制工具窗口展示哪些看板卡片，存储于 IDE 配置目录的 xwindow-panels.xml。
 * <p>
 * 默认除音乐频谱外全部开启；在 Settings/Preferences → 工具箱看板 中勾选后立即生效（无需重启 IDE）。
 */
@State(
        name = "XWindowPanelSettings",
        storages = @Storage("xwindow-panels.xml")
)
public class PanelSettings implements PersistentStateComponent<PanelSettings> {

    /** 顶部个人卡片：头像 + 名称 + 副标题。 */
    public boolean clockAvatarEnabled = true;

    /** 独立时钟卡片：大号时间 + 日期。 */
    public boolean clockEnabled = true;

    /** 今日搬砖进度卡片。 */
    public boolean workProgressEnabled = true;

    /** 天气卡片。 */
    public boolean weatherEnabled = true;

    /** DeepSeek 余额卡片。 */
    public boolean deepSeekEnabled = true;

    /** 用户/诗歌卡片。 */
    public boolean poemEnabled = true;

    /** 赛博篝火卡片（敲键添柴的像素火苗）。 */
    public boolean fireEnabled = true;

    /** 音乐频谱卡片（伪实时律动柱）。 */
    public boolean musicEnabled = false;

    public static PanelSettings getInstance() {
        return ApplicationManager.getApplication().getService(PanelSettings.class);
    }

    @Override
    public PanelSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull PanelSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public boolean isClockAvatarEnabled() {
        return clockAvatarEnabled;
    }

    public void setClockAvatarEnabled(boolean clockAvatarEnabled) {
        this.clockAvatarEnabled = clockAvatarEnabled;
    }

    public boolean isClockEnabled() {
        return clockEnabled;
    }

    public void setClockEnabled(boolean clockEnabled) {
        this.clockEnabled = clockEnabled;
    }

    public boolean isWorkProgressEnabled() {
        return workProgressEnabled;
    }

    public void setWorkProgressEnabled(boolean workProgressEnabled) {
        this.workProgressEnabled = workProgressEnabled;
    }

    public boolean isWeatherEnabled() {
        return weatherEnabled;
    }

    public void setWeatherEnabled(boolean weatherEnabled) {
        this.weatherEnabled = weatherEnabled;
    }

    public boolean isDeepSeekEnabled() {
        return deepSeekEnabled;
    }

    public void setDeepSeekEnabled(boolean deepSeekEnabled) {
        this.deepSeekEnabled = deepSeekEnabled;
    }

    public boolean isPoemEnabled() {
        return poemEnabled;
    }

    public void setPoemEnabled(boolean poemEnabled) {
        this.poemEnabled = poemEnabled;
    }

    public boolean isFireEnabled() {
        return fireEnabled;
    }

    public void setFireEnabled(boolean fireEnabled) {
        this.fireEnabled = fireEnabled;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
    }
}
