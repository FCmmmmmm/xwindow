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
import org.plugin.window.settings.PanelSettings;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainWindow {
    private static final int TIME_REFRESH_INTERVAL = 1000; // 1秒
    private static final int WEATHER_REFRESH_INTERVAL = 1800000; // 30分钟
    private static final int POEM_REFRESH_INTERVAL = 3600000; // 1小时
    private static final int DEEPSEEK_REFRESH_INTERVAL = 1800000; // 30分钟（余额变化慢，不必频繁请求）

    private final JPanel Layout; //当前页面布局容器
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); //异步线程调度器
    /** 当前所有已打开的工具窗口实例，设置页应用后统一实时重建。 */
    private static final List<MainWindow> INSTANCES = new CopyOnWriteArrayList<>();

    private final Timer timeTimer;
    private final Timer weatherTimer;
    private final Timer poemTimer;
    private final Timer deepSeekTimer;

    private JPanel mainPanel; //当前卡片容器，重建布局时整体替换
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

        // 定时器固定创建，按配置启停；事件里对空面板做空指针保护
        timeTimer = new Timer(TIME_REFRESH_INTERVAL, e -> TimeDisplay());
        weatherTimer = new Timer(WEATHER_REFRESH_INTERVAL, e -> WeatherDisplay());
        poemTimer = new Timer(POEM_REFRESH_INTERVAL, e -> PoemDisplay());
        deepSeekTimer = new Timer(DEEPSEEK_REFRESH_INTERVAL, e -> {
            if (DeepSeekSettings.getInstance().isAutoRefreshEnabled()) {
                DeepSeekDisplay();
            }
        });

        INSTANCES.add(this);

        // 按配置初始化布局
        rebuildLayout();
        // 启动自动刷新机制（仅启用的卡片对应定时器会运行）
        OpenAutoRefresh();
    }

    /** 按 {@link PanelSettings} 重建整个卡片列表，旧面板先释放再整体替换。 */
    private void rebuildLayout() {
        PanelSettings settings = PanelSettings.getInstance();

        // 释放旧面板占用的内部定时器等资源
        disposePanels();

        // 主容器 - 垂直布局
        JPanel rebuilt = new JBPanel<>();
        rebuilt.setLayout(new BoxLayout(rebuilt, BoxLayout.Y_AXIS));

        // 个人信息卡片：头像 + 名称 + 副标题（置顶）
        if (settings.isClockAvatarEnabled()) {
            this.ClockAvatarPanel = new ClockAvatarPanel();
            rebuilt.add(this.ClockAvatarPanel.GenerateClockAvatarCard());
            rebuilt.add(Box.createVerticalStrut(15));
        }

        // 独立时钟卡片：大号时间 + 日期
        if (settings.isClockEnabled()) {
            this.ClockPanel = new ClockPanel();
            rebuilt.add(this.ClockPanel.GenerateClockCard());
            rebuilt.add(Box.createVerticalStrut(15));
        }

        // 今日搬砖进度条（10:00–19:00 工作时段）
        if (settings.isWorkProgressEnabled()) {
            this.WorkProgressPanel = new WorkProgressPanel();
            rebuilt.add(this.WorkProgressPanel.GenerateWorkProgressCard());
            rebuilt.add(Box.createVerticalStrut(15));
        }

        // 天气卡片
        if (settings.isWeatherEnabled()) {
            this.WeatherPanel = new WeatherPanel();
            rebuilt.add(this.WeatherPanel.GenerateWeatherCard());
            rebuilt.add(Box.createVerticalStrut(20));
        }

        // DeepSeek 余额卡片（卡片内自带一键查询按钮）
        if (settings.isDeepSeekEnabled()) {
            this.DeepSeekPanel = new DeepSeekPanel();
            rebuilt.add(this.DeepSeekPanel.GenerateDeepSeekCard(this::DeepSeekDisplay));
            rebuilt.add(Box.createVerticalStrut(20));
        }

        // 用户/诗歌卡片（头像+名称可在设置中配置，诗句按时段变化）
        if (settings.isPoemEnabled()) {
            this.PoemPanel = new PoemPanel();
            rebuilt.add(this.PoemPanel.GeneratePoemCard());
            rebuilt.add(Box.createVerticalStrut(20));
        }

        // 赛博篝火（敲键添柴的小火苗）
        if (settings.isFireEnabled()) {
            this.FirePanel = new FirePanel();
            rebuilt.add(this.FirePanel.GenerateFireCard());
            rebuilt.add(Box.createVerticalStrut(20));
        }

        // 音乐动效（伪实时频谱柱，节奏跳动）
        if (settings.isMusicEnabled()) {
            this.MusicSpectrumPanel = new MusicSpectrumPanel();
            rebuilt.add(this.MusicSpectrumPanel.GenerateMusicCard());
            rebuilt.add(Box.createVerticalStrut(20));
        }

        // 刷新按钮
        rebuilt.add(GenerateAutoButtonSection());

        Layout.removeAll();
        mainPanel = rebuilt;
        Layout.add(mainPanel, BorderLayout.NORTH);
        Layout.revalidate();
        Layout.repaint();

        // 按最新配置启停定时器
        syncTimers();

        // 首屏余额查询走 executor，避免在卡片构建阶段把网络请求压在 EDT 上
        if (settings.isDeepSeekEnabled()) {
            DeepSeekDisplay();
        }
    }

    /** 释放旧面板占用的内部资源（搬砖进度、篝火、频谱各自持有独立定时器/监听器）。 */
    private void disposePanels() {
        if (this.WorkProgressPanel != null) {
            this.WorkProgressPanel.dispose();
            this.WorkProgressPanel = null;
        }
        if (this.FirePanel != null) {
            this.FirePanel.dispose();
            this.FirePanel = null;
        }
        if (this.MusicSpectrumPanel != null) {
            this.MusicSpectrumPanel.dispose();
            this.MusicSpectrumPanel = null;
        }
        this.ClockAvatarPanel = null;
        this.ClockPanel = null;
        this.WeatherPanel = null;
        this.PoemPanel = null;
        this.DeepSeekPanel = null;
    }

    /** 设置页保存后调用：重建所有已打开的工具窗口，实时生效。 */
    public static void rebuildAll() {
        for (MainWindow window : INSTANCES) {
            window.rebuildLayout();
        }
    }

    /** 工具窗口内容被移除时注销实例，避免对已关闭的窗口做重建。 */
    static void unregister(MainWindow window) {
        INSTANCES.remove(window);
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
        syncTimers();
    }

    /** 依据当前配置启停四个刷新定时器：卡片被隐藏时对应定时器同步停用。 */
    private void syncTimers() {
        PanelSettings settings = PanelSettings.getInstance();
        setTimerRunning(timeTimer,
                settings.isClockAvatarEnabled() || settings.isClockEnabled() || settings.isWorkProgressEnabled()
                        || settings.isMusicEnabled());
        setTimerRunning(weatherTimer, settings.isWeatherEnabled());
        setTimerRunning(poemTimer, settings.isPoemEnabled());
        setTimerRunning(deepSeekTimer, settings.isDeepSeekEnabled());
    }

    private static void setTimerRunning(Timer timer, boolean running) {
        if (running != timer.isRunning()) {
            if (running) {
                timer.start();
            } else {
                timer.stop();
            }
        }
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















