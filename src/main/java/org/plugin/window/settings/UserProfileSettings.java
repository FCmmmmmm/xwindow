package org.plugin.window.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 应用级持久化配置，保存用户资料（名称、头像图片路径）以及“今日搬砖进度”卡片的可配置项，
 * 存储于 IDE 配置目录的 xwindow-userprofile.xml。
 */
@State(
        name = "XWindowUserProfileSettings",
        storages = @Storage("xwindow-userprofile.xml")
)
public class UserProfileSettings implements PersistentStateComponent<UserProfileSettings> {

    /** “HH:mm” 时间格式，用于上下班时间的序列化与展示。 */
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** 展示名称。 */
    public String userName = "FCmmmmmm";

    /** 本地头像图片的绝对路径，为空则显示默认 emoji 头像。 */
    public String avatarPath = "";

    /** 搬砖进度：上班时间，"HH:mm" 24 小时制。 */
    public String workStartTime = "10:00";

    /** 搬砖进度：下班时间，"HH:mm" 24 小时制，需晚于 workStartTime。 */
    public String workEndTime = "19:00";

    /** 搬砖进度：下班前多少分钟进入“黎明前的黑暗”闪烁提醒。 */
    public int dawnZoneMinutes = 30;

    /** 搬砖进度：像素进度条的格子数量。 */
    public int progressSegments = 20;

    public static UserProfileSettings getInstance() {
        return ApplicationManager.getApplication().getService(UserProfileSettings.class);
    }

    @Override
    public UserProfileSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull UserProfileSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getWorkStartTime() {
        return workStartTime;
    }

    public void setWorkStartTime(String workStartTime) {
        this.workStartTime = workStartTime;
    }

    public String getWorkEndTime() {
        return workEndTime;
    }

    public void setWorkEndTime(String workEndTime) {
        this.workEndTime = workEndTime;
    }

    public int getDawnZoneMinutes() {
        return dawnZoneMinutes;
    }

    public void setDawnZoneMinutes(int dawnZoneMinutes) {
        this.dawnZoneMinutes = dawnZoneMinutes;
    }

    public int getProgressSegments() {
        return progressSegments;
    }

    public void setProgressSegments(int progressSegments) {
        this.progressSegments = progressSegments;
    }

    /** 将字符串解析为 {@link LocalTime}，失败时返回 {@code fallback}。 */
    public static LocalTime parseTimeOrDefault(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FMT);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
