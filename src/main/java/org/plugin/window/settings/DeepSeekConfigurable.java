package org.plugin.window.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * 设置页：Settings/Preferences → 工具箱 DeepSeek，配置 API Key、币种与自动刷新。
 * <p>
 * API Key 通过 {@link DeepSeekApiKeyStore} 存入 IDE 凭据库，其余偏好存于 {@link DeepSeekSettings}。
 */
public class DeepSeekConfigurable implements Configurable {

    private static final String[] CURRENCIES = {"CNY", "USD"};

    private JBPasswordField apiKeyField;
    private ComboBox<String> currencyCombo;
    private JBCheckBox autoRefreshCheckBox;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "工具箱 DeepSeek";
    }

    @Override
    public @Nullable JComponent createComponent() {
        apiKeyField = new JBPasswordField();
        currencyCombo = new ComboBox<>(CURRENCIES);
        autoRefreshCheckBox = new JBCheckBox("每 30 分钟自动刷新余额");

        JBLabel keyHint = new JBLabel(
                "<html>在 platform.deepseek.com 创建 API Key 后填入此处。<br>"
                        + "密钥保存在 IDE 凭据库（macOS 钥匙串 / Windows 凭据库），不会明文写入配置文件。</html>");
        keyHint.setForeground(JBColor.GRAY);
        keyHint.setBorder(JBUI.Borders.empty(4, 2, 8, 0));

        JBLabel usageHint = new JBLabel(
                "<html>关于「使用情况」：<br>"
                        + "· DeepSeek 官方 API 只提供余额查询，<b>没有</b>用量/消耗明细接口。<br>"
                        + "· 卡片上的「今日已消耗」「较上次」由本地余额快照做差值推算得出。<br>"
                        + "· 因此只能覆盖插件运行期间发生的调用，也无法按模型或请求拆分。<br>"
                        + "· 检测到余额上升时视为充值，并重置当日起算基准。<br>"
                        + "· 需要精确账单请前往 DeepSeek 开放平台控制台查看。</html>");
        usageHint.setForeground(JBColor.GRAY);
        usageHint.setBorder(JBUI.Borders.empty(8, 2, 0, 0));

        root = FormBuilder.createFormBuilder()
                .addLabeledComponent("API Key：", apiKeyField)
                .addComponent(keyHint)
                .addLabeledComponent("优先币种：", currencyCombo)
                .addComponent(autoRefreshCheckBox)
                .addComponent(usageHint)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        reset();
        return root;
    }

    @Override
    public boolean isModified() {
        DeepSeekSettings s = DeepSeekSettings.getInstance();
        return !Objects.equals(readApiKey(), DeepSeekApiKeyStore.getKey())
                || !Objects.equals(currencyCombo.getItem(), s.getPreferredCurrency())
                || autoRefreshCheckBox.isSelected() != s.isAutoRefreshEnabled();
    }

    @Override
    public void apply() {
        DeepSeekSettings s = DeepSeekSettings.getInstance();
        DeepSeekApiKeyStore.setKey(readApiKey());
        s.setPreferredCurrency(currencyCombo.getItem());
        s.setAutoRefreshEnabled(autoRefreshCheckBox.isSelected());
    }

    @Override
    public void reset() {
        DeepSeekSettings s = DeepSeekSettings.getInstance();
        apiKeyField.setText(DeepSeekApiKeyStore.getKey());
        String currency = s.getPreferredCurrency();
        currencyCombo.setItem(
                currency == null || currency.isBlank() ? CURRENCIES[0] : currency);
        autoRefreshCheckBox.setSelected(s.isAutoRefreshEnabled());
    }

    /** 从密码框取值，用完立刻清掉临时字符数组。 */
    private String readApiKey() {
        char[] chars = apiKeyField.getPassword();
        try {
            return new String(chars).trim();
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    @Override
    public void disposeUIResources() {
        apiKeyField = null;
        currencyCombo = null;
        autoRefreshCheckBox = null;
        root = null;
    }
}
