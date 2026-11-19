package io.github.hyperpriv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Stores verified APK sets in HyperPriv's private files directory. */
final class TelemetryBackups {
    static final int PACKAGE_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS
            | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;
    private static final String METADATA = "backup.json";
    private static final Pattern USER = Pattern.compile("UserInfo\\{(\\d+):");
    private static final Pattern SESSION = Pattern.compile("\\[(\\d+)\\]");

    static boolean exists(Context context, String packageName) {
        return archive(context, packageName).getBaseFile().isFile();
    }

    static boolean matchesExisting(Context context, PackageInfo info) {
        File file = archive(context, info.packageName).getBaseFile();
        try (ZipFile zip = new ZipFile(file)) {
            return new Backup(zip, info.packageName).matches(info);
        } catch (IOException error) {
            return false;
        }
    }

    static String backupAndUninstall(Context context, String packageName, int userId)
            throws IOException, InterruptedException {
        PackageManager manager = context.getPackageManager();
        PackageInfo installed = info(manager, packageName, PACKAGE_FLAGS);
        if (installed == null) return "此用户尚未安装该组件";
        writeArchive(context, installed);
        try (ZipFile zip = new ZipFile(archive(context, packageName).getBaseFile())) {
            Backup saved = new Backup(zip, packageName);
            saved.verifyEntries(zip);
            // Refuse to remove a newer version that was installed during the copy.
            if (!saved.matches(info(manager, packageName, PACKAGE_FLAGS))) {
                return "安装包在备份期间发生变化，请重新备份";
            }
            Command result = pm(context, "uninstall --user " + userId + " --versionCode "
                    + saved.version + " " + packageName, null);
            if (info(manager, packageName, PACKAGE_FLAGS) == null) return "APK 已备份，组件已卸载";
            return result.failure("APK 已备份，系统未卸载组件");
        }
    }

    static String restore(Context context, String packageName, int userId)
            throws IOException, InterruptedException {
        PackageManager manager = context.getPackageManager();
        AtomicFile file = archive(context, packageName);
        if (!file.getBaseFile().isFile()) return "找不到该组件的 APK 备份";
        try (ZipFile zip = new ZipFile(file.getBaseFile())) {
            Backup saved = new Backup(zip, packageName);
            saved.verifyEntries(zip);
            PackageInfo existing = info(manager, packageName,
                    PACKAGE_FLAGS | PackageManager.MATCH_UNINSTALLED_PACKAGES);
            if (saved.matches(existing)) {
                Command result = pm(context, "install-existing --user " + userId + " " + packageName, null);
                if (saved.matches(info(manager, packageName, PACKAGE_FLAGS))) return null;
                return result.failure("系统未能恢复现有安装包");
            }
            if (existing != null && existing.getLongVersionCode() > saved.version) {
                return "系统已有更新版本，无法恢复旧版 APK";
            }
            if (installedForOtherUser(context, packageName, userId)) {
                return "其他用户正在使用不同版本，恢复会影响该用户";
            }
            String error = installBackup(context, zip, saved, userId);
            if (saved.matches(info(manager, packageName, PACKAGE_FLAGS))) return null;
            return error == null ? "系统未确认已恢复备份 APK" : error;
        }
    }

    private static AtomicFile archive(Context context, String packageName) {
        return new AtomicFile(new File(new File(context.getFilesDir(), "telemetry-apks"),
                packageName + ".zip"));
    }

    private static PackageInfo info(PackageManager manager, String name, int flags) {
        try {
            return manager.getPackageInfo(name, flags);
        } catch (PackageManager.NameNotFoundException error) {
            return null;
        }
    }

    private static List<File> sources(PackageInfo info) {
        List<File> files = new ArrayList<>();
        files.add(new File(info.applicationInfo.sourceDir));
        if (info.applicationInfo.splitSourceDirs != null) {
            for (String split : info.applicationInfo.splitSourceDirs) files.add(new File(split));
        }
        return files;
    }

    private static void writeArchive(Context context, PackageInfo installed) throws IOException {
        AtomicFile archive = archive(context, installed.packageName);
        List<File> apks = sources(installed);
        FileOutputStream stream = archive.startWrite();
        try {
            JSONArray hashes = new JSONArray();
            try (ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(stream) {
                @Override public void close() throws IOException { flush(); }
            })) {
                for (int index = 0; index < apks.size(); index++) {
                    zip.putNextEntry(entry(index));
                    try (InputStream in = Files.newInputStream(apks.get(index).toPath())) {
                        hashes.put(copyAndHash(in, zip));
                    }
                    zip.closeEntry();
                }
                JSONObject data = new JSONObject();
                data.put("package", installed.packageName);
                data.put("versionCode", installed.getLongVersionCode());
                data.put("sha256", hashes);
                zip.putNextEntry(new ZipEntry(METADATA));
                zip.write(data.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            archive.finishWrite(stream);
        } catch (JSONException | IOException | RuntimeException error) {
            archive.failWrite(stream);
            throw new IOException("APK 备份未完成", error);
        }
    }

    private static ZipEntry entry(int index) {
        return new ZipEntry(index == 0 ? "base.apk" : "split_" + index + ".apk");
    }

    private static String copyAndHash(InputStream input, OutputStream output) throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                hash.update(buffer, 0, count);
                if (output != null) output.write(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    private static final class Backup {
        final String packageName;
        final long version;
        final List<String> hashes = new ArrayList<>();

        Backup(ZipFile zip, String expected) throws IOException {
            try {
                ZipEntry metadata = zip.getEntry(METADATA);
                if (metadata == null || metadata.getSize() < 0 || metadata.getSize() > 65536) {
                    throw new IOException("APK 备份索引无效");
                }
                try (InputStream input = zip.getInputStream(metadata)) {
                    byte[] bytes = new byte[(int) metadata.getSize()];
                    new java.io.DataInputStream(input).readFully(bytes);
                    JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    packageName = data.getString("package");
                    version = data.getLong("versionCode");
                    JSONArray values = data.getJSONArray("sha256");
                    if (!expected.equals(packageName) || version < 0 || values.length() == 0) {
                        throw new IOException("APK 备份标识无效");
                    }
                    for (int index = 0; index < values.length(); index++) {
                        String value = values.getString(index);
                        if (!value.matches("[a-f0-9]{64}")) throw new IOException("APK 备份校验值无效");
                        hashes.add(value);
                    }
                }
            } catch (JSONException error) {
                throw new IOException("APK 备份索引无效", error);
            }
        }

        void verifyEntries(ZipFile zip) throws IOException {
            for (int index = 0; index < hashes.size(); index++) {
                ZipEntry apk = zip.getEntry(entry(index).getName());
                if (apk == null || apk.getSize() <= 0) throw new IOException("APK 备份缺少文件");
                try (InputStream input = zip.getInputStream(apk)) {
                    if (!hashes.get(index).equals(copyAndHash(input, null))) {
                        throw new IOException("APK 备份校验失败");
                    }
                }
            }
        }

        boolean matches(PackageInfo info) throws IOException {
            if (info == null || !packageName.equals(info.packageName)
                    || info.getLongVersionCode() != version) return false;
            List<File> current = sources(info);
            if (current.size() != hashes.size()) return false;
            for (int index = 0; index < current.size(); index++) {
                try (InputStream input = Files.newInputStream(current.get(index).toPath())) {
                    if (!hashes.get(index).equals(copyAndHash(input, null))) return false;
                }
            }
            return true;
        }
    }

    private static boolean installedForOtherUser(Context context, String packageName, int owner)
            throws IOException, InterruptedException {
        Command users = pm(context, "list users", null);
        if (!users.ok) throw new IOException("无法读取 Android 用户列表");
        Matcher matcher = USER.matcher(users.output);
        boolean found = false;
        while (matcher.find()) {
            int user = Integer.parseInt(matcher.group(1));
            if (user == owner) {
                found = true;
                continue;
            }
            Command packages = pm(context, "list packages --user " + user + " " + packageName, null);
            if (!packages.ok) throw new IOException("无法核对其他用户的安装状态");
            for (String line : packages.output.split("\\R")) {
                if (line.trim().equals("package:" + packageName)) return true;
            }
        }
        if (!found) throw new IOException("无法确认当前 Android 用户");
        return false;
    }

    private static String installBackup(Context context, ZipFile zip, Backup saved, int userId)
            throws IOException, InterruptedException {
        Command creation = pm(context, "install-create -r --user " + userId, null);
        Matcher match = SESSION.matcher(creation.output);
        if (!creation.ok || !match.find()) return creation.failure("无法创建 APK 恢复会话");
        String session = match.group(1);
        boolean committed = false;
        try {
            for (int index = 0; index < saved.hashes.size(); index++) {
                ZipEntry apk = zip.getEntry(entry(index).getName());
                File staged = File.createTempFile("telemetry-restore-", ".apk", context.getCacheDir());
                try {
                    try (InputStream in = zip.getInputStream(apk);
                         OutputStream out = Files.newOutputStream(staged.toPath())) {
                        if (!saved.hashes.get(index).equals(copyAndHash(in, out))) {
                            return "恢复文件校验失败";
                        }
                    }
                    if (index == 0) {
                        PackageInfo base = context.getPackageManager().getPackageArchiveInfo(staged.getPath(), 0);
                        if (base == null || !saved.packageName.equals(base.packageName)
                                || saved.version != base.getLongVersionCode()) return "恢复 APK 标识不符";
                    }
                    Command result = pm(context, "install-write -S " + staged.length() + " "
                            + session + " " + apk.getName() + " -", staged);
                    if (!result.ok) return result.failure("无法写入 APK 恢复会话");
                } finally {
                    Files.deleteIfExists(staged.toPath());
                }
            }
            Command install = pm(context, "install-commit " + session, null);
            committed = install.ok;
            return committed ? null : install.failure("系统拒绝恢复备份 APK");
        } finally {
            if (!committed) pm(context, "install-abandon " + session, null);
        }
    }

    private static final class Command {
        final boolean ok;
        final String output;

        Command(boolean ok, String output) {
            this.ok = ok;
            this.output = output;
        }

        String failure(String message) {
            if (ok || output.trim().isEmpty()) return message;
            String text = output.trim().replaceAll("\\s+", " ");
            return message + " · " + text.substring(0, Math.min(125, text.length()));
        }
    }

    private static Command pm(Context context, String command, File input)
            throws IOException, InterruptedException {
        File output = File.createTempFile("telemetry-pm-", ".log", context.getCacheDir());
        java.lang.Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("su", "-c", "/system/bin/pm " + command)
                    .redirectErrorStream(true).redirectOutput(output);
            if (input != null) builder.redirectInput(input);
            process = builder.start();
            if (!process.waitFor(35, TimeUnit.SECONDS)) return new Command(false, "操作超时");
            try (InputStream result = Files.newInputStream(output.toPath())) {
                byte[] bytes = new byte[(int) Math.min(output.length(), 65536)];
                new java.io.DataInputStream(result).readFully(bytes);
                return new Command(process.exitValue() == 0, new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output.toPath());
        }
    }

    private TelemetryBackups() {}
}
