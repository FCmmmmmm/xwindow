package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.plugin.window.content.WeatherContent;
import org.plugin.window.settings.GaodeSettings;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WeatherPanel {
    private static final String TIME_FORMAT = "HH:mm:ss";

    private JBLabel tempLabel;
    private JBLabel weatherLabel;
    private JBLabel humidityLabel;
    private JBLabel refreshLabel;

    public JPanel GenerateWeatherCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        // 顶行：标题 + 城市
        JBLabel titleLabel = new JBLabel("🌤 今日天气");
        titleLabel.setForeground(JBColor.foreground());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        JBLabel cityLabel = new JBLabel("◎ " + GaodeSettings.getInstance().getCityName());
        cityLabel.setFont(cityLabel.getFont().deriveFont(Font.PLAIN, 11f));
        cityLabel.setForeground(JBColor.GRAY);

        JPanel header = new JBPanel<>(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(cityLabel, BorderLayout.EAST);

        // 中部：大号气温 + 右侧天气/湿度
        tempLabel = new JBLabel("--°C");
        tempLabel.setForeground(JBColor.foreground());
        tempLabel.setFont(tempLabel.getFont().deriveFont(Font.BOLD, 30f));

        weatherLabel = new JBLabel("--");
        weatherLabel.setFont(weatherLabel.getFont().deriveFont(Font.BOLD, 14f));
        weatherLabel.setForeground(JBColor.foreground());

        humidityLabel = new JBLabel("💧 --%");
        humidityLabel.setFont(humidityLabel.getFont().deriveFont(Font.PLAIN, 12f));
        humidityLabel.setForeground(JBColor.GRAY);

        // 天气与湿度放在同一行，避免两行堆叠显得局促
        JPanel rightInfo = new JBPanel<>();
        rightInfo.setLayout(new BoxLayout(rightInfo, BoxLayout.X_AXIS));
        rightInfo.setOpaque(false);
        rightInfo.add(weatherLabel);
        rightInfo.add(Box.createHorizontalStrut(14));
        rightInfo.add(humidityLabel);

        JPanel mainRow = new JBPanel<>();
        mainRow.setLayout(new BoxLayout(mainRow, BoxLayout.X_AXIS));
        mainRow.setOpaque(false);
        mainRow.add(tempLabel);
        mainRow.add(Box.createHorizontalGlue());
        mainRow.add(rightInfo);

        // 底部：上次刷新
        refreshLabel = new JBLabel(" ");
        refreshLabel.setFont(refreshLabel.getFont().deriveFont(Font.PLAIN, 9f));
        refreshLabel.setForeground(JBColor.GRAY);
        refreshLabel.setHorizontalAlignment(SwingConstants.CENTER);
        refreshLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(header);
        content.add(Box.createVerticalStrut(10));
        content.add(mainRow);
        content.add(Box.createVerticalStrut(6));
        content.add(refreshLabel);

        card.add(content, BorderLayout.CENTER);

        GenerateDisplayText(); // 添加到主容器之后 立刻初始加载天气信息
        return card;
    }

    public void GenerateDisplayText() {
        if (this.tempLabel == null) {
            return; // 如果标签还未初始化，直接返回
        }

        //noinspection DialogTitleCapitalization
        this.tempLabel.setText("--°C");
        this.weatherLabel.setText("正在获取...");

        WeatherContent.WeatherInfo info =
                WeatherContent.getWeatherInfo(GaodeSettings.getInstance().getCityCode());
        // 回到 EDT 线程更新 UI
        SwingUtilities.invokeLater(() -> {
            if (this.tempLabel == null) {
                return;
            }
            if (info == null) {
                this.tempLabel.setText("获取失败");
                this.weatherLabel.setText("请检查网络");
            } else {
                this.tempLabel.setText(info.temperature() + "°C");
                this.weatherLabel.setText(info.icon() + " " + info.weather());
                this.humidityLabel.setText("💧 " + info.humidity() + "%");
            }
            this.refreshLabel.setText(
                    "上次刷新: " + LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT)));
        });
    }
}
