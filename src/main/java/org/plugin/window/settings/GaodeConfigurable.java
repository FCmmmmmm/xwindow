package org.plugin.window.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * 设置页：Settings/Preferences → 工具箱高德地图，配置 API Key 与城市信息。
 * <p>
 * API Key 通过 {@link GaodeApiKeyStore} 存入 IDE 凭据库，城市信息存于 {@link GaodeSettings}。
 */
public class GaodeConfigurable implements Configurable {

    private JBPasswordField apiKeyField;
    private JBTextField cityCodeField;
    private JBTextField cityNameField;
    private JBLabel keychainWarningLabel;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "工具箱高德地图";
    }

    @Override
    public @Nullable JComponent createComponent() {
        apiKeyField = new JBPasswordField();
        cityCodeField = new JBTextField();
        cityNameField = new JBTextField();

        JBLabel keyHint = new JBLabel(
                "<html>在 lbs.amap.com 控制台创建「Web服务」类型 Key 后填入此处。<br>"
                        + "密钥保存在 IDE 凭据库（macOS 钥匙串 / Windows 凭据库），不会明文写入配置文件。</html>");
        keyHint.setForeground(JBColor.GRAY);
        keyHint.setBorder(JBUI.Borders.empty(4, 2, 8, 0));

        JBLabel cityHint = new JBLabel(
                "<html>城市编码为高德 adcode，例如北京 110101、上海 310000；<br>"
                        + "可在 lbs.amap.com/tools/poi 查询。</html>");
        cityHint.setForeground(JBColor.GRAY);
        cityHint.setBorder(JBUI.Borders.empty(4, 2, 8, 0));

        keychainWarningLabel = new JBLabel();
        keychainWarningLabel.setForeground(JBColor.RED);
        keychainWarningLabel.setBorder(JBUI.Borders.empty(4, 2, 8, 0));
        keychainWarningLabel.setVisible(false);

        root = FormBuilder.createFormBuilder()
                .addLabeledComponent("API Key：", apiKeyField)
                .addComponent(keyHint)
                .addLabeledComponent("城市编码：", cityCodeField)
                .addLabeledComponent("城市名称：", cityNameField)
                .addComponent(cityHint)
                .addComponent(keychainWarningLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        reset();
        return root;
    }

    @Override
    public boolean isModified() {
        GaodeSettings s = GaodeSettings.getInstance();
        return !Objects.equals(readApiKey(), GaodeApiKeyStore.getKey())
                || !Objects.equals(cityCodeField.getText().trim(), s.getCityCode())
                || !Objects.equals(cityNameField.getText().trim(), s.getCityName());
    }

    @Override
    public void apply() {
        GaodeSettings s = GaodeSettings.getInstance();
        String key = readApiKey();
        boolean credentialStoreOk = GaodeApiKeyStore.setKey(key);
        s.setCityCode(cityCodeField.getText().trim());
        s.setCityName(cityNameField.getText().trim());
        if (!key.isEmpty() && !credentialStoreOk) {
            keychainWarningLabel.setText(
                    "<html>⚠ 密钥已保存到插件配置文件，但系统钥匙串写入被拒绝（授权弹窗可能被忽略）。<br>"
                            + "如需使用系统钥匙串：重启 IDE 后重新保存，并在弹窗中选择「始终允许」。</html>");
            keychainWarningLabel.setVisible(true);
        } else {
            keychainWarningLabel.setVisible(false);
        }
    }

    @Override
    public void reset() {
        GaodeSettings s = GaodeSettings.getInstance();
        apiKeyField.setText(GaodeApiKeyStore.getKey());
        cityCodeField.setText(s.getCityCode());
        cityNameField.setText(s.getCityName());
        keychainWarningLabel.setVisible(false);
    }

    /** 从密码框取值，用完立刻清掉临时字符数组。 */
    private String readApiKey() {
        char[] chars = apiKeyField.getPassword();
        try {
            return new String(chars).trim();
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    @Override
    public void disposeUIResources() {
        apiKeyField = null;
        cityCodeField = null;
        cityNameField = null;
        keychainWarningLabel = null;
        root = null;
    }
}
