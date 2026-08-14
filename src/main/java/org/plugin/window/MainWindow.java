package org.plugin.window;

import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.plugin.window.panel.ClockAvatarPanel;
import org.plugin.window.panel.ClockPanel;
import org.plugin.window.panel.UiFactory;
import org.plugin.window.panel.DeepSeekPanel;
import org.plugin.window.panel.FirePanel;
import org.plugin.window.panel.MusicSpectrumPanel;
import org.plugin.window.panel.MusicVisualizerPanel;
import org.plugin.window.panel.PoemPanel;
import org.plugin.window.panel.WeatherPanel;
import org.plugin.window.panel.WorkProgressPanel;
import org.plugin.window.settings.DeepSeekSettings;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainWindow {
    private static final int TIME_REFRESH_INTERVAL = 1000; // 1秒
    private static final int WEATHER_REFRESH_INTERVAL = 1800000; // 30分钟
    private static final int POEM_REFRESH_INTERVAL = 3600000; // 1小时
    private static final int DEEPSEEK_REFRESH_INTERVAL = 1800000; // 30分钟（余额变化慢，不必频繁请求）

    private final JPanel Layout; //当前页面布局容器
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); //异步线程调度器
    private ClockPanel ClockPanel;
    private ClockAvatarPanel ClockAvatarPanel;
    private WeatherPanel WeatherPanel;
    private PoemPanel PoemPanel;
    private WorkProgressPanel WorkProgressPanel;
    private FirePanel FirePanel;
    private MusicVisualizerPanel MusicVisualizerPanel;
    private MusicSpectrumPanel MusicSpectrumPanel;
    private DeepSeekPanel DeepSeekPanel;


    public MainWindow() {
        Layout = new JBPanel<>(new BorderLayout());
        // 不透明背景：用主题标准面板背景色盖住 IDE 背景图，整窗保持统一的固实底色
        Layout.setOpaque(true);
        Layout.setBackground(UIUtil.getPanelBackground());
        Layout.setBorder(JBUI.Borders.empty(15));

        // 初始化自动布局
        initLayout();
        // 启动自动刷新机制
        OpenAutoRefresh();
    }

    private void initLayout() {
        // 主容器 - 垂直布局
        JPanel mainPanel = new JBPanel<>();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // 个人信息卡片：头像 + 名称 + 副标题（置顶）
        this.ClockAvatarPanel = new ClockAvatarPanel();
        mainPanel.add(this.ClockAvatarPanel.GenerateClockAvatarCard());
        mainPanel.add(Box.createVerticalStrut(15));

        // 独立时钟卡片：大号时间 + 日期
        this.ClockPanel = new ClockPanel();
        mainPanel.add(this.ClockPanel.GenerateClockCard());
        mainPanel.add(Box.createVerticalStrut(15));

        // 今日搬砖进度条（10:00–19:00 工作时段）
        this.WorkProgressPanel = new WorkProgressPanel();
        mainPanel.add(this.WorkProgressPanel.GenerateWorkProgressCard());
        mainPanel.add(Box.createVerticalStrut(15));

        // 天气卡片
       this.WeatherPanel = new WeatherPanel();
        JPanel weatherCard = this.WeatherPanel.GenerateWeatherCard();
        mainPanel.add(weatherCard);
        mainPanel.add(Box.createVerticalStrut(20));

        // DeepSeek 余额卡片（卡片内自带一键查询按钮）
        this.DeepSeekPanel = new DeepSeekPanel();
        mainPanel.add(this.DeepSeekPanel.GenerateDeepSeekCard(this::DeepSeekDisplay));
        mainPanel.add(Box.createVerticalStrut(20));

        // 用户/诗歌卡片（头像+名称可在设置中配置，诗句按时段变化）
        this.PoemPanel = new PoemPanel();
        JPanel poemCard = this.PoemPanel.GeneratePoemCard();
        mainPanel.add(poemCard);
        mainPanel.add(Box.createVerticalStrut(20));

//        // 赛博篝火（敲键添柴的小火苗）
//        this.FirePanel = new FirePanel();
//        mainPanel.add(this.FirePanel.GenerateFireCard());
//        mainPanel.add(Box.createVerticalStrut(20));

        //        // 音乐动效（伪实时频谱柱，节奏跳动）—— 已从布局移除，代码保留以备恢复
//        this.MusicSpectrumPanel = new MusicSpectrumPanel();
//        mainPanel.add(this.MusicSpectrumPanel.GenerateMusicCard());
//        mainPanel.add(Box.createVerticalStrut(20));

        // 刷新按钮
        mainPanel.add(GenerateAutoButtonSection());

        Layout.add(mainPanel, BorderLayout.NORTH);

        // 首屏余额查询走 executor，避免在卡片构建阶段把网络请求压在 EDT 上
        DeepSeekDisplay();
    }


    private JPanel GenerateAutoButtonSection() {
        JPanel buttonPanel = new JBPanel<>(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);

        // 蓝紫渐变刷新按钮：渐变底 + 圆角 + hover 提亮
        UiFactory.GradientButton refreshButton = new UiFactory.GradientButton("🔄 手动刷新");
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 13f));
        refreshButton.setPreferredSize(new Dimension(140, 36));
        refreshButton.addActionListener(e -> {
            TimeDisplay();
            WeatherDisplay();
            PoemDisplay();
            DeepSeekDisplay();
        });

        buttonPanel.add(refreshButton);

        return buttonPanel;
    }

    private void OpenAutoRefresh() {
        // 时间自动刷新：每秒
        Timer timeTimer = new Timer(TIME_REFRESH_INTERVAL, e -> TimeDisplay());
        timeTimer.start();

        // 天气自动刷新：每30分钟
        Timer weatherTimer = new Timer(WEATHER_REFRESH_INTERVAL, e -> WeatherDisplay());
        weatherTimer.start();

        // 诗歌自动刷新：每1小时
        Timer poemTimer = new Timer(POEM_REFRESH_INTERVAL, e -> PoemDisplay());
        poemTimer.start();

        // DeepSeek 余额自动刷新：每30分钟，可在设置页关闭
        Timer deepSeekTimer = new Timer(DEEPSEEK_REFRESH_INTERVAL, e -> {
            if (DeepSeekSettings.getInstance().isAutoRefreshEnabled()) {
                DeepSeekDisplay();
            }
        });
        deepSeekTimer.start();
    }

    private void TimeDisplay() {
        if (this.ClockPanel != null) {
            this.ClockPanel.refreshClock();
        }
        if (this.ClockAvatarPanel != null) {
            this.ClockAvatarPanel.refreshName(); // 后台改头像/名称也能生效
        }
        if (this.WorkProgressPanel != null) {
            this.WorkProgressPanel.refresh();
        }
        // 频谱律动与时段数据轻量联动：仅更新强度系数，无网络、无额外定时器
        // 音乐模块已从布局移除，此联动为空闲保护，保留代码以备恢复
        if (this.MusicSpectrumPanel != null) {
            this.MusicSpectrumPanel.RefreshLinkage();
        }
    }

    private void WeatherDisplay() {
        // 异步获取天气信息
        executor.submit(() -> {
            if (this.WeatherPanel!=null){
                this.WeatherPanel.GenerateDisplayText();
            }
        });
    }

    private void PoemDisplay() {
        // 异步生成诗歌
        executor.submit(() -> {
            if (this.PoemPanel != null) {
                this.PoemPanel.GenerateDisplayText();
            }
        });
    }

    private void DeepSeekDisplay() {
        // 异步查询 DeepSeek 余额
        executor.submit(() -> {
            if (this.DeepSeekPanel != null) {
                this.DeepSeekPanel.GenerateDisplayText();
            }
        });
    }


    public JPanel getContent() {
        return Layout;
    }
}



















