package org.plugin.window.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;

/**
 * 高德（AMap）Web 服务 API Key 的安全存储封装。
 * <p>
 * 密钥交由 IDE 的 {@link PasswordSafe}（macOS 钥匙串 / Windows 凭据库 / KeePass）保管，
 * 不会明文落到 xwindow-*.xml 里。
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
     * <p>
     * 钥匙串访问可能阻塞，调用方应尽量放在后台线程（设置页 reset 属常规例外）。
     */
    public static String getKey() {
        String password = PasswordSafe.getInstance().getPassword(attributes());
        return password == null ? "" : password;
    }

    /** 保存密钥；传入空值等同于清除。 */
    public static void setKey(String key) {
        if (key == null || key.isBlank()) {
            PasswordSafe.getInstance().set(attributes(), null);
        } else {
            PasswordSafe.getInstance().setPassword(attributes(), key.trim());
        }
    }
}
