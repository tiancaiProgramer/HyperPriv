package io.github.hyperpriv;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class CameraCompatibility {
    static final String ITEM = "com.xiaomi.cam.watermark.b";
    private static final Map<String, String> DEX_HASHES = new HashMap<>();

    static final class Profile {
        final long version;
        final String apkHash, dexHash;
        final String dateFilter, catalogFilter, metadataMethod, metadataClass, limitsClass;
        final String metadataWrapper, catalogVariant, catalogIds;

        Profile(long version, String apkHash, String dexHash, String dateFilter, String catalogFilter,
                String metadataMethod, String metadataClass, String limitsClass, String metadataWrapper,
                String catalogVariant, String catalogIds) {
            this.version = version;
            this.apkHash = apkHash;
            this.dexHash = dexHash;
            this.dateFilter = dateFilter;
            this.catalogFilter = catalogFilter;
            this.metadataMethod = metadataMethod;
            this.metadataClass = metadataClass;
            this.limitsClass = limitsClass;
            this.metadataWrapper = metadataWrapper;
            this.catalogVariant = catalogVariant;
            this.catalogIds = catalogIds;
        }

        String accessorType() { return metadataWrapper == null ? metadataClass : metadataWrapper; }
    }

    static final Profile MIX_FOLD_3 = new Profile(640003101L,
            "832a4b7d86b8ad269c09cf3d831c15ea0b10148bad41025339311dc5393c8d2c",
            "e16c6afc1f45096dc451b6fbd887bc218d643e11631be38a2eb536acdd768dbb",
            "Nf.e", "l9.u", "o", "Cc.a", "Cc.d", "zc.a", "a", "b");
    static final Profile XIAOMI_15_EU = new Profile(640005300L,
            "f0f74b5188ab9df41eea991f203f8c8cb81270ae5253cac2563ee5846e52aa2f",
            "32f099b809ac5e1c59c47f64e61d262fc6cd7bd32102c81203e0ac01cc9f0716",
            "cg.i", "o9.r", "C", "Gc.a", "Gc.d", null, null, "a");

    static final class Selection {
        final Profile profile;
        final AppearanceSupport.Match match;
        Selection(Profile profile, AppearanceSupport.Match match) {
            this.profile = profile;
            this.match = match;
        }
    }

    static Selection match(long version, String hash, String path, String[] splits, boolean compatibility) {
        if (version == MIX_FOLD_3.version && MIX_FOLD_3.apkHash.equals(hash)
                && (splits == null || splits.length == 0)) {
            return new Selection(MIX_FOLD_3, AppearanceSupport.Match.EXACT);
        }
        if (!compatibility) return new Selection(null, AppearanceSupport.Match.NONE);
        // Resource/signature repacks can reuse an audited profile when all executable DEX files are identical.
        String identity = dexIdentity(path, splits);
        for (Profile profile : new Profile[]{MIX_FOLD_3, XIAOMI_15_EU})
            if (profile.dexHash.equals(identity)) return new Selection(profile, AppearanceSupport.Match.COMPATIBLE);
        return new Selection(null, AppearanceSupport.Match.NONE);
    }

    static synchronized String dexIdentity(String path, String[] splits) {
        try {
            String[] archives = new String[1 + (splits == null ? 0 : splits.length)];
            archives[0] = path;
            if (splits != null) {
                String[] ordered = splits.clone();
                Arrays.sort(ordered);
                System.arraycopy(ordered, 0, archives, 1, ordered.length);
            }
            StringBuilder key = new StringBuilder();
            for (String archive : archives) {
                File file = new File(archive);
                key.append(archive).append(':').append(file.length()).append(':').append(file.lastModified()).append('|');
            }
            String cached = DEX_HASHES.get(key.toString());
            if (cached != null) return cached;

            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            MessageDigest identity = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[131072];
            int entries = 0;
            for (int i = 0; i < archives.length; i++) {
                try (ZipFile zip = new ZipFile(archives[i])) {
                    Map<String, ZipEntry> dex = new TreeMap<>();
                    Enumeration<? extends ZipEntry> all = zip.entries();
                    while (all.hasMoreElements()) {
                        ZipEntry entry = all.nextElement();
                        if (!entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) continue;
                        if (dex.put(entry.getName(), entry) != null) return "";
                    }
                    if (i == 0 && !dex.containsKey("classes.dex")) return "";
                    for (ZipEntry entry : dex.values()) {
                        if (++entries > 64) return "";
                        long size = 0;
                        try (InputStream in = zip.getInputStream(entry)) {
                            int count;
                            while ((count = in.read(buffer)) != -1) {
                                size += count;
                                if (size > 64 * 1024 * 1024) throw new IOException("DEX too large");
                                fileDigest.update(buffer, 0, count);
                            }
                        }
                        if (i != 0) identity.update(("split:" + i + "/").getBytes(StandardCharsets.UTF_8));
                        identity.update(entry.getName().getBytes(StandardCharsets.UTF_8));
                        identity.update((byte) 0);
                        identity.update(fileDigest.digest());
                    }
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : identity.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            String result = hex.toString();
            DEX_HASHES.put(key.toString(), result);
            return result;
        } catch (Exception error) {
            return "";
        }
    }

    private CameraCompatibility() {}
}
