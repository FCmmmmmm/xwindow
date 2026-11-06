package org.plugin.window.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.plugin.window.MainWindow;

import javax.swing.*;

/**
 * 设置页：Settings/Preferences → 工具箱看板，用复选框控制工具窗口展示哪些卡片。
 * <p>
 * 勾选状态保存到 {@link PanelSettings}，应用后通过 {@link MainWindow#rebuildAll()} 实时重建布局。
 */
public class PanelConfigurable implements Configurable {

    private JBCheckBox clockAvatarBox;
    private JBCheckBox clockBox;
    private JBCheckBox workProgressBox;
    private JBCheckBox weatherBox;
    private JBCheckBox deepSeekBox;
    private JBCheckBox fortuneBox;
    private JBCheckBox poemBox;
    private JBCheckBox fireBox;
    private JBCheckBox musicBox;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "工具箱看板";
    }

    @Override
    public @Nullable JComponent createComponent() {
        clockAvatarBox = new JBCheckBox("个人卡片（头像 + 名称）");
        clockBox = new JBCheckBox("时钟卡片");
        workProgressBox = new JBCheckBox("今日搬砖进度卡片");
        weatherBox = new JBCheckBox("天气卡片");
        deepSeekBox = new JBCheckBox("DeepSeek 余额卡片");
        fortuneBox = new JBCheckBox("今日指引卡片（点击弹窗，调用大模型生成今日吉凶；产生 API 费用）");
        poemBox = new JBCheckBox("用户/诗歌卡片");
        fireBox = new JBCheckBox("赛博篝火卡片");
        musicBox = new JBCheckBox("音乐频谱卡片");

        JBLabel hint = new JBLabel(
                "<html>取消勾选对应卡片后，工具窗口会立即移除该模块（刷新定时器同步停用）。<br>"
                        + "「手动刷新」按钮始终保留。</html>");
        hint.setForeground(JBColor.GRAY);
        hint.setBorder(JBUI.Borders.empty(8, 2, 0, 0));

        root = FormBuilder.createFormBuilder()
                .addComponent(clockAvatarBox)
                .addComponent(clockBox)
                .addComponent(workProgressBox)
                .addComponent(weatherBox)
                .addComponent(deepSeekBox)
                .addComponent(fortuneBox)
                .addComponent(poemBox)
                .addComponent(fireBox)
                .addComponent(musicBox)
                .addComponent(hint)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        reset();
        return root;
    }

    @Override
    public boolean isModified() {
        PanelSettings s = PanelSettings.getInstance();
        return clockAvatarBox.isSelected() != s.isClockAvatarEnabled()
                || clockBox.isSelected() != s.isClockEnabled()
                || workProgressBox.isSelected() != s.isWorkProgressEnabled()
                || weatherBox.isSelected() != s.isWeatherEnabled()
                || deepSeekBox.isSelected() != s.isDeepSeekEnabled()
                || fortuneBox.isSelected() != s.isFortuneEnabled()
                || poemBox.isSelected() != s.isPoemEnabled()
                || fireBox.isSelected() != s.isFireEnabled()
                || musicBox.isSelected() != s.isMusicEnabled();
    }

    @Override
    public void apply() {
        PanelSettings s = PanelSettings.getInstance();
        s.setClockAvatarEnabled(clockAvatarBox.isSelected());
        s.setClockEnabled(clockBox.isSelected());
        s.setWorkProgressEnabled(workProgressBox.isSelected());
        s.setWeatherEnabled(weatherBox.isSelected());
        s.setDeepSeekEnabled(deepSeekBox.isSelected());
        s.setFortuneEnabled(fortuneBox.isSelected());
        s.setPoemEnabled(poemBox.isSelected());
        s.setFireEnabled(fireBox.isSelected());
        s.setMusicEnabled(musicBox.isSelected());

        // 实时重建所有已打开的工具窗口，无需重启 IDE
        MainWindow.rebuildAll();
    }

    @Override
    public void reset() {
        PanelSettings s = PanelSettings.getInstance();
        clockAvatarBox.setSelected(s.isClockAvatarEnabled());
        clockBox.setSelected(s.isClockEnabled());
        workProgressBox.setSelected(s.isWorkProgressEnabled());
        weatherBox.setSelected(s.isWeatherEnabled());
        deepSeekBox.setSelected(s.isDeepSeekEnabled());
        fortuneBox.setSelected(s.isFortuneEnabled());
        poemBox.setSelected(s.isPoemEnabled());
        fireBox.setSelected(s.isFireEnabled());
        musicBox.setSelected(s.isMusicEnabled());
    }

    @Override
    public void disposeUIResources() {
        clockAvatarBox = null;
        clockBox = null;
        workProgressBox = null;
        weatherBox = null;
        deepSeekBox = null;
        fortuneBox = null;
        poemBox = null;
        fireBox = null;
        musicBox = null;
        root = null;
    }
}
