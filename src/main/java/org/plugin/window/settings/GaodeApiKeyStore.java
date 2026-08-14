package org.plugin.window.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;

/**
 * 高德（AMap）Web 服务 API Key 的安全存储封装。
 * <p>
 * 密钥优先持久化到 {@link GaodeSettings}（本地 IDE 配置 xwindow-gaode.xml），保证在
 * 凭据库不可用时（如 macOS 钥匙串未授权）也能正常保存；
 * 同时尽力写入 IDE 的 {@link PasswordSafe}（macOS 钥匙串 / Windows 凭据库）作为兼容兜底，
 * 读取时设置文件优先，凭据库用于读取历史保存过的值。
 */
public final class GaodeApiKeyStore {

    private static final String SERVICE_NAME =
            CredentialAttributesKt.generateServiceName("XWindow", "GaodeApiKey");

    private GaodeApiKeyStore() {
    }

    private static CredentialAttributes attributes() {
        return new CredentialAttributes(SERVICE_NAME);
    }

    /**
     * 读取已保存的密钥，未配置时返回空串。
     */
    public static String getKey() {
        String fromSettings = GaodeSettings.getInstance().getApiKey();
        if (fromSettings != null && !fromSettings.isBlank()) {
            return fromSettings.trim();
        }
        try {
            String password = PasswordSafe.getInstance().getPassword(attributes());
            return password == null ? "" : password;
        } catch (Throwable ignored) {
            // 凭据库不可用时静默降级，不影响读取
            return "";
        }
    }

    /** 保存密钥；传入空值等同于清除。 */
    public static void setKey(String key) {
        String normalized = key == null ? "" : key.trim();
        // 主存储：设置文件，保证持久化
        GaodeSettings.getInstance().setApiKey(normalized);
        // 兜底：尽力写入凭据库，失败不影响保存
        try {
            if (normalized.isEmpty()) {
                PasswordSafe.getInstance().set(attributes(), null);
            } else {
                PasswordSafe.getInstance().setPassword(attributes(), normalized);
            }
        } catch (Throwable ignored) {
            // 凭据库不可用（如钥匙串未授权）时静默忽略
        }
    }
}
