package io.github.hyperpriv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** The package manager is the source of truth for the current Android user. */
final class TelemetryPackages {
    enum Component {
        SYSTEM_HELPER("SystemHelper", "com.mobiletools.systemhelper", "systemhelper_report",
                "组件的设备注册、信号和应用信息上报入口将不可用。"),
        ANALYTICS("小米统计（Analytics）", "com.miui.analytics", "analytics",
                "经此包处理的统计事件与 OneTrack 服务将不可用。"),
        MSA("MSA 广告服务", "com.miui.systemAdSolution", "msa",
                "由 MSA 提供的广告、推荐和相关统计入口将不可用。"),
        AUTO_REGISTRATION("AutoRegistration", "com.xiaomi.registration", null,
                "运营商设备和 SIM 自动注册入口将不可用，可能影响依赖注册的运营商服务。"),
        MI_SIGHT("MiSightService", "com.miui.misightservice", null,
                "经此包处理的系统诊断、事件上报和故障反馈可能受影响。"),
        SECURITY_ONE_TRACK("SecurityOnetrackService", "com.xiaomi.security.onetrack", null,
                "经此包处理的安全组件事件上报将不可用。");

        final String title;
        final String packageName;
        final String hookKey;
        final String uninstallImpact;

        Component(String title, String packageName, String hookKey, String uninstallImpact) {
            this.title = title;
            this.packageName = packageName;
            this.hookKey = hookKey;
            this.uninstallImpact = uninstallImpact;
        }

        boolean hasHook() {
            return hookKey != null;
        }
    }

    enum Mode {
        DEFAULT("默认"), HOOK("仅拦截"), UNINSTALL("备份并卸载");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    static boolean managesHook(String key) {
        for (Component component : Component.values()) if (key.equals(component.hookKey)) return true;
        return false;
    }

    static Mode selectedMode(State state, boolean hookConfigured, boolean hasBackup) {
        if (state == State.NOT_INSTALLED && hasBackup) return Mode.UNINSTALL;
        if (state == State.ENABLED) return hookConfigured ? Mode.HOOK : Mode.DEFAULT;
        return null;
    }

    static boolean visible(State state, boolean hasBackup) {
        return state != State.NOT_INSTALLED || hasBackup;
    }

    enum HookSupport {
        UNAVAILABLE, VERIFIED_PROFILE, EXPERIMENTAL
    }

    static HookSupport hookSupport(Context context, Component component, State state,
                                   boolean compatibility) {
        if (!component.hasHook() || state == State.UNKNOWN) return HookSupport.UNAVAILABLE;
        try {
            int flags = TelemetryBackups.PACKAGE_FLAGS;
            if (state == State.NOT_INSTALLED) flags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
            PackageInfo info = context.getPackageManager().getPackageInfo(component.packageName, flags);
            if (state == State.NOT_INSTALLED) {
                if (!TelemetryBackups.matchesExisting(context, info)) return HookSupport.UNAVAILABLE;
            } else if (!RuntimeProfile.allowed(component.hookKey)) {
                return HookSupport.UNAVAILABLE;
            }
            ApplicationInfo app = info.applicationInfo;
            long version = info.getLongVersionCode();
            if (component == Component.SYSTEM_HELPER) {
                HelperCompatibility.Match match = HelperCompatibility.resolve(version,
                        EuEnvironment.hash(app.sourceDir), app.sourceDir, compatibility);
                if (match == null) return HookSupport.UNAVAILABLE;
                return match.experimental ? HookSupport.EXPERIMENTAL : HookSupport.VERIFIED_PROFILE;
            }

            List<Rule> rules = Compatibility.select(Profiles.ALL, component.packageName,
                    version, compatibility);
            rules.removeIf(rule -> !component.hookKey.equals(rule.group));
            if (rules.isEmpty()) return HookSupport.UNAVAILABLE;
            List<String> paths = new ArrayList<>();
            paths.add(app.sourceDir);
            if (app.splitSourceDirs != null) Collections.addAll(paths, app.splitSourceDirs);
            DexEntrances.Result scan = DexEntrances.inspect(paths.toArray(new String[0]), rules);
            return matchedSupport(rules.get(0).version == version, scan);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return HookSupport.UNAVAILABLE;
        }
    }

    static HookSupport matchedSupport(boolean knownVersion, DexEntrances.Result scan) {
        if (scan.found.isEmpty()) return HookSupport.UNAVAILABLE;
        return knownVersion ? HookSupport.VERIFIED_PROFILE : HookSupport.EXPERIMENTAL;
    }

    enum State {
        ENABLED("已启用", true),
        DISABLED("已停用", true),
        DISABLED_UNTIL_USED("待启用", true),
        NOT_INSTALLED("未安装", false),
        UNKNOWN("无法读取系统状态", false);

        final String label;
        final boolean canChange;

        State(String label, boolean canChange) {
            this.label = label;
            this.canChange = canChange;
        }
    }

    private static final AtomicReference<Component> PENDING = new AtomicReference<>();

    static State state(PackageManager manager, Component component) {
        try {
            ApplicationInfo app = manager.getApplicationInfo(component.packageName,
                    TelemetryBackups.PACKAGE_FLAGS);
            int setting = manager.getApplicationEnabledSetting(component.packageName);
            return effectiveState(setting, app.enabled);
        } catch (PackageManager.NameNotFoundException error) {
            return State.NOT_INSTALLED;
        } catch (RuntimeException error) {
            return State.UNKNOWN;
        }
    }

    static State effectiveState(int setting, boolean applicationEnabled) {
        switch (setting) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return applicationEnabled ? State.ENABLED : State.DISABLED;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return State.ENABLED;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return State.DISABLED;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return State.DISABLED_UNTIL_USED;
            default:
                return State.UNKNOWN;
        }
    }

    static boolean busy() {
        return PENDING.get() != null;
    }

    static boolean changing(Component component) {
        return PENDING.get() == component;
    }

    static void change(Context context, Component component, Mode mode) {
        if (mode == Mode.HOOK && !component.hasHook()) return;
        Context app = context.getApplicationContext();
        if (!PENDING.compareAndSet(null, component)) return;

        new Thread(() -> {
            String message;
            try {
                message = changeMode(app, component, mode);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                message = "修改已中断，请查看当前状态";
            } catch (IOException | RuntimeException error) {
                Log.i("HyperPriv", "Component state change failed: " + error.getClass().getSimpleName());
                message = error.getMessage() == null ? "操作失败，请确认 Root 授权" : error.getMessage();
            }
            final String notice = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                PENDING.set(null);
                Settings.notifyChanged();
                Toast.makeText(app, component.title + " · " + notice, Toast.LENGTH_LONG).show();
            });
        }, "HyperPriv-component-state").start();
    }

    private static String changeMode(Context context, Component component, Mode mode)
            throws IOException, InterruptedException {
        PackageManager manager = context.getPackageManager();
        State initial = state(manager, component);
        // Android reserves a range of 100,000 UIDs per user.
        int userId = Process.myUid() / 100_000;
        if (mode == Mode.UNINSTALL) {
            if (!initial.canChange) return initial.label;
            return TelemetryBackups.backupAndUninstall(context, component.packageName, userId);
        }
        boolean needsRestore = initial == State.NOT_INSTALLED
                && TelemetryBackups.exists(context, component.packageName);
        if (!initial.canChange && !needsRestore) return initial.label;
        if (!component.hasHook()) return enable(context, component, needsRestore, userId);

        String key = component.hookKey;
        SharedPreferences own = Settings.local(context);
        boolean defaultValue = RuntimeProfile.defaultValue(key);
        boolean hadPreference = own.contains(key);
        boolean previous = own.getBoolean(key, defaultValue);
        boolean wanted = mode == Mode.HOOK;
        if (wanted && hookSupport(context, component, initial,
                own.getBoolean(Compatibility.KEY, RuntimeProfile.defaultValue(Compatibility.KEY)))
                == HookSupport.UNAVAILABLE) {
            return "未匹配到拦截入口";
        }
        if (wanted && initial != State.ENABLED) {
            if (!own.getBoolean("master", RuntimeProfile.defaultValue("master"))) {
                return "请先开启 HyperPriv 总开关";
            }
            java.util.List<String> scopes = Settings.scopes;
            if (scopes == null || !scopes.contains(component.packageName)) {
                return "请在 LSPosed 中确认组件作用域后再恢复";
            }
        }
        boolean changed = previous != wanted;
        if (changed && !own.edit().putBoolean(key, wanted).commit()) return "无法保存 Hook 设置";

        boolean synced = Settings.sync(context);
        if (initial == State.ENABLED) {
            return synced ? mode.label : "已保存 Hook 设置，等待同步到 LSPosed";
        }

        // Apply the interception setting before an enabled package can start.
        try {
            if (wanted && !synced) return "请先连接 LSPosed，再恢复并启用拦截";
            String result = enable(context, component, needsRestore, userId);
            return !synced && state(manager, component) == State.ENABLED
                    ? "组件已启用，Hook 设置等待同步" : result;
        } finally {
            if (changed && state(manager, component) != State.ENABLED) {
                SharedPreferences.Editor restore = own.edit();
                if (hadPreference) restore.putBoolean(key, previous);
                else restore.remove(key);
                if (!restore.commit()) Log.i("HyperPriv", "Could not restore telemetry Hook preference");
                Settings.sync(context);
            }
        }
    }

    private static String enable(Context context, Component component, boolean restoreBackup, int userId)
            throws IOException, InterruptedException {
        if (restoreBackup) {
            String error = TelemetryBackups.restore(context, component.packageName, userId);
            if (error != null) return error;
            RuntimeProfile.initializeUi(context);
            Settings.sync(context);
        }
        PackageManager manager = context.getPackageManager();
        State initial = state(manager, component);
        if (initial == State.ENABLED || !initial.canChange) return initial.label;

        File discard = new File("/dev/null");
        java.lang.Process process = new ProcessBuilder("su", "-c", enableCommand(component, userId))
                .redirectErrorStream(true).redirectOutput(discard).start();
        try {
            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                return "操作超时，请确认 Root 授权并重新查看状态";
            }
            for (int attempt = 0; attempt < 5; attempt++) {
                State current = state(manager, component);
                if (current == State.ENABLED || !current.canChange) return current.label;
                if (process.exitValue() != 0) break;
                Thread.sleep(100);
            }
            return process.exitValue() == 0 ? "系统未确认目标状态"
                    : "系统拒绝修改，请检查 Root 授权及系统限制";
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    static String enableCommand(Component component, int userId) {
        if (userId < 0) throw new IllegalArgumentException("Invalid Android user ID");
        return "/system/bin/pm enable --user " + userId + " " + component.packageName;
    }

    private TelemetryPackages() {}
}
