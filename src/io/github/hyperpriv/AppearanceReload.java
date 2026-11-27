package io.github.hyperpriv;

import android.content.Context;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppearanceReload {
    enum Target {
        SYSTEM_UI(Appearance.SYSTEM_UI, "重载系统界面", "刷新已加载的外观设置。电池样式及标记、信号排序、时钟位置、小白条粗细及偏移需正常重启手机。已自定义电池样式时，切换 Duo 也需重启。"),
        HOME(Appearance.HOME, "重启系统桌面", "小米桌面会重新打开并加载图标大小。需要 Root 授权。");

        final String pkg, title, description;

        Target(String pkg, String title, String description) {
            this.pkg = pkg;
            this.title = title;
            this.description = description;
        }

        String command(int user) {
            return "/system/bin/am force-stop --user " + user + " " + pkg
                    + " && /system/bin/am start --user " + user
                    + " -a android.intent.action.MAIN -c android.intent.category.HOME -p " + pkg;
        }
    }

    private static final AtomicBoolean BUSY = new AtomicBoolean();

    static String run(Context context, Target target) {
        if (!BUSY.compareAndSet(false, true)) return "正在重启，请稍候";
        java.lang.Process process = null;
        try {
            Settings.refreshScopes();
            if (Settings.scopes != null && !Settings.scopes.contains(target.pkg))
                return "请先在 LSPosed 中勾选对应作用域";
            if (target == Target.SYSTEM_UI)
                return Settings.requestAppearanceRefresh(context) ? "已发送外观刷新请求" : "无法同步设置，请检查 LSPosed 连接";
            if (!Settings.sync(context)) return "设置尚未同步，请检查 LSPosed 连接";
            process = new ProcessBuilder("su", "-c", target.command(Process.myUid() / 100_000))
                    .redirectErrorStream(true).redirectOutput(new File("/dev/null")).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) return "重启超时，请检查 Root 授权";
            return process.exitValue() == 0 ? "已发送重启请求" : "重启失败，请检查 Root 授权或目标进程状态";
        } catch (IOException | SecurityException error) {
            return "无法执行重启，请检查 Root 授权";
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return "重启操作已中断";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            BUSY.set(false);
        }
    }

    private AppearanceReload() {}
}
