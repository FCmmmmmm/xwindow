package org.plugin.window.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;

/**
 * 高德（AMap）Web 服务 API Key 的安全存储封装。
 * <p>
 * 密钥只存 IDE 的 {@link PasswordSafe}（macOS 钥匙串 / Windows 凭据库 / KeePass），
 * 不会明文写入 xwindow-*.xml 配置文件。
 * <p>
 * 钥匙串授权被拒绝时保存会失败，写入前会验证写回，调用方应把失败提示给用户，避免静默丢数据。
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
        try {
            String password = PasswordSafe.getInstance().getPassword(attributes());
            return password == null ? "" : password;
        } catch (Throwable ignored) {
            // 凭据库不可用时按未配置处理
            return "";
        }
    }

    /**
     * 保存密钥；传入空值等同于清除。
     *
     * @return 凭据库写入是否成功（验证写回），调用方应据此提示用户授权钥匙串
     */
    public static boolean setKey(String key) {
        String normalized = key == null ? "" : key.trim();
        try {
            if (normalized.isEmpty()) {
                PasswordSafe.getInstance().set(attributes(), null);
            } else {
                PasswordSafe.getInstance().setPassword(attributes(), normalized);
            }
            // 验证写回：授权被拒绝时这里会拿到 null，从而暴露“静默失败”
            String back = PasswordSafe.getInstance().getPassword(attributes());
            return normalized.isEmpty() || (back != null && back.equals(normalized));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
