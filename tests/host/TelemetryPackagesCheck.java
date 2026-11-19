package io.github.hyperpriv;

import android.content.pm.PackageManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class TelemetryPackagesCheck {
    public static void main(String[] args) {
        check(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, true, TelemetryPackages.State.ENABLED);
        check(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, false, TelemetryPackages.State.DISABLED);
        check(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, false, TelemetryPackages.State.ENABLED);
        check(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, true, TelemetryPackages.State.DISABLED);
        check(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, true, TelemetryPackages.State.DISABLED);
        check(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, false,
                TelemetryPackages.State.DISABLED_UNTIL_USED);
        check(-1, true, TelemetryPackages.State.UNKNOWN);
        require(!TelemetryPackages.State.NOT_INSTALLED.canChange, "absent applications must be read-only");
        require(!TelemetryPackages.State.UNKNOWN.canChange, "unknown application state must be read-only");
        for (TelemetryPackages.State state : TelemetryPackages.State.values()) {
            require(TelemetryPackages.visible(state, false) == (state != TelemetryPackages.State.NOT_INSTALLED),
                    "only absent packages without a backup should be hidden");
            require(TelemetryPackages.visible(state, true), "a backup must keep the recovery row available");
        }
        DexEntrances.Result missing = new DexEntrances.Result(true, Collections.emptySet());
        DexEntrances.Result matched = new DexEntrances.Result(true, Collections.singleton("entry"));
        require(TelemetryPackages.matchedSupport(false, missing) == TelemetryPackages.HookSupport.UNAVAILABLE,
                "fallback version alone must not expose the Hook");
        require(TelemetryPackages.matchedSupport(false, matched) == TelemetryPackages.HookSupport.EXPERIMENTAL,
                "matching fallback method must be marked experimental");
        require(TelemetryPackages.matchedSupport(true, matched) == TelemetryPackages.HookSupport.VERIFIED_PROFILE,
                "a matching known version keeps its existing mode");
        require(TelemetryPackages.selectedMode(TelemetryPackages.State.ENABLED, false, false)
                == TelemetryPackages.Mode.DEFAULT, "active package with Hook off must use default");
        require(TelemetryPackages.selectedMode(TelemetryPackages.State.ENABLED, true, true)
                == TelemetryPackages.Mode.HOOK, "active package with Hook on must show interception");
        require(TelemetryPackages.selectedMode(TelemetryPackages.State.DISABLED, true, true) == null,
                "a disabled package must not be presented as uninstalled");
        require(TelemetryPackages.selectedMode(TelemetryPackages.State.NOT_INSTALLED, false, true)
                == TelemetryPackages.Mode.UNINSTALL, "a backed up uninstalled package must show the restore path");
        require(TelemetryPackages.selectedMode(TelemetryPackages.State.DISABLED_UNTIL_USED, true, false) == null,
                "an app that can wake on use must not show as stopped");
        require(TelemetryPackages.selectedMode(TelemetryPackages.State.NOT_INSTALLED, true, false) == null,
                "an absent package cannot have an active mode");
        Set<String> hookKeys = new HashSet<>();
        for (TelemetryPackages.Component component : TelemetryPackages.Component.values()) {
            if (!component.hasHook()) continue;
            require(Options.accepts(component.hookKey)
                    && UiCatalog.target(component.hookKey).equals(component.packageName)
                    && TelemetryPackages.managesHook(component.hookKey)
                    && hookKeys.add(component.hookKey), "existing Hook must map to one component");
        }
        require(hookKeys.size() == 3 && !TelemetryPackages.managesHook("battery_report"),
                "only three existing Hook controls should be merged");
        Set<String> packages = new HashSet<>();
        for (TelemetryPackages.Component component : TelemetryPackages.Component.values()) {
            require(packages.add(component.packageName), "package listed more than once");
            require(TelemetryPackages.enableCommand(component, 10).equals(
                    "/system/bin/pm enable --user 10 " + component.packageName),
                    "restore must target only the selected package and user");
        }
        try {
            TelemetryPackages.enableCommand(TelemetryPackages.Component.SYSTEM_HELPER, -1);
            throw new AssertionError("negative user ID accepted");
        } catch (IllegalArgumentException expected) {
            // The shell must always receive a specific Android user.
        }
        System.out.println("Telemetry package visibility, backup recovery, Hook recognition and per-user commands passed");
    }

    private static void check(int setting, boolean appEnabled, TelemetryPackages.State expected) {
        require(TelemetryPackages.effectiveState(setting, appEnabled) == expected,
                "unexpected state for " + setting + " with manifest value " + appEnabled);
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
