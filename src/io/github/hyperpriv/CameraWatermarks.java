package io.github.hyperpriv;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Extends the date window for complete cached templates on audited Camera builds. */
final class CameraWatermarks {
    private static final Pattern IMAGE = Pattern.compile(
            "[a-zA-Z0-9_./-]+\\.(?:webp|png|jpe?g|heic|gif|bmp|svg)(?![a-zA-Z0-9_./-])",
            Pattern.CASE_INSENSITIVE);
    private final AppearanceHooks hooks;
    private final File root;

    CameraWatermarks(AppearanceHooks hooks, Context context) {
        this.hooks = hooks;
        root = new File(context.getFilesDir(), "watermarks");
    }

    void install(CameraCompatibility.Profile profile) {
        hooks.register("camera_watermarks", () -> {
            String item = CameraCompatibility.ITEM;
            String dates = profile.dateFilter;
            String catalog = profile.catalogFilter;
            Executable dateFilter = hooks.method(dates, "invoke", "Ljava/lang/Object;", "Ljava/lang/Object;");
            Executable idFilter = hooks.method(catalog, "invoke", "Ljava/lang/Object;", "Ljava/lang/Object;");
            Method config = (Method) hooks.method(item, profile.metadataMethod,
                    "L" + profile.accessorType().replace('.', '/') + ";");
            Class<?> itemType = config.getDeclaringClass();
            ClassLoader loader = itemType.getClassLoader();
            Field metadata = profile.metadataWrapper == null ? null : hooks.field(profile.metadataWrapper, "c",
                    Class.forName(profile.metadataClass, false, loader));
            Field limits = hooks.field(profile.metadataClass, "i", Class.forName(profile.limitsClass, false, loader));
            Field validity = hooks.field(profile.limitsClass, "a", String.class);
            Field folder = hooks.field(item, "a", Path.class);
            Field dateVariant = hooks.field(dates, "a", int.class);
            Field now = hooks.field(dates, "b", Object.class);
            Field catalogVariant = profile.catalogVariant == null ? null : hooks.field(catalog, profile.catalogVariant, int.class);
            Field publishedIds = hooks.field(catalog, profile.catalogIds, catalogVariant == null ? List.class : Object.class);
            Runnable hit = hooks.firstHit("camera_watermarks");
            hookDate(dateFilter, config, metadata, limits, validity, folder, dateVariant, now, itemType, hit);
            hooks.hook(idFilter, chain -> {
                Object result = chain.proceed();
                Object predicate = chain.getThisObject();
                Object candidate = chain.getArg(0);
                if (Boolean.TRUE.equals(result) && hooks.active() && itemType.isInstance(candidate)
                        && (catalogVariant == null || catalogVariant.getInt(predicate) == 0)
                        && publishedIds.get(predicate) instanceof List<?>) {
                    String window = readWindow(candidate, config, metadata, limits, validity);
                    LocalDateTime until = endOfWindow(window);
                    if (until != null && LocalDateTime.now().isAfter(until)
                            && completeTemplate((Path) folder.get(candidate), window)) {
                        hit.run();
                        return Boolean.FALSE;
                    }
                }
                return result;
            });
        });
    }

    private void hookDate(Executable dateFilter, Method config, Field metadata, Field limits,
            Field validity, Field folder, Field variant, Field now, Class<?> itemType, Runnable hit) {
        hooks.hook(dateFilter, chain -> {
            Object result = chain.proceed();
            Object predicate = chain.getThisObject();
            Object candidate = chain.getArg(0);
            if (!Boolean.TRUE.equals(result) || !hooks.active() || !itemType.isInstance(candidate)
                    || variant.getInt(predicate) != 1 || !(now.get(predicate) instanceof LocalDateTime)) return result;
            String window = readWindow(candidate, config, metadata, limits, validity);
            LocalDateTime until = endOfWindow(window);
            if (until != null && ((LocalDateTime) now.get(predicate)).isAfter(until)
                    && completeTemplate((Path) folder.get(candidate), window)) {
                hit.run();
                return Boolean.FALSE;
            }
            return result;
        });
    }

    private static String readWindow(Object item, Method accessor, Field metadata, Field limits, Field validity)
            throws ReflectiveOperationException {
        Object source = accessor.invoke(item);
        if (source != null && metadata != null) source = metadata.get(source);
        Object constraints = source == null ? null : limits.get(source);
        return constraints == null ? null : (String) validity.get(constraints);
    }

    private static LocalDateTime endOfWindow(String value) {
        if (value == null) return null;
        String[] parts = value.split("~", -1);
        if (parts.length != 2) return null;
        try {
            LocalDateTime first = LocalDateTime.parse(parts[0].trim(), DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime last = LocalDateTime.parse(parts[1].trim(), DateTimeFormatter.ISO_DATE_TIME);
            return last.isBefore(first) ? null : last;
        } catch (DateTimeParseException error) { return null; }
    }

    private boolean completeTemplate(Path folder, String window) {
        try {
            Path base = root.toPath().toRealPath();
            Path directory = folder.toRealPath();
            if (!directory.startsWith(base) || base.relativize(directory).getNameCount() != 2) return false;
            Path config = directory.resolve("config.json").toRealPath();
            if (!config.startsWith(directory) || !Files.isRegularFile(config) || Files.size(config) > 2 * 1024 * 1024) return false;
            JSONObject body = new JSONObject(new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
            JSONObject metadata = body.optJSONObject("metadata");
            JSONArray layouts = body.optJSONArray("layout_group");
            JSONObject resources = body.optJSONObject("wmRes");
            if (metadata == null || metadata.optString("id").isEmpty()
                    || layouts == null || layouts.length() == 0 || resources == null) return false;
            JSONObject limits = metadata.optJSONObject("limitation");
            return limits != null && window.equals(limits.optString("validity"))
                    && checkImages(directory, resources, 0) && checkImages(directory, layouts, 0);
        } catch (IOException | org.json.JSONException | RuntimeException error) { return false; }
    }

    private static boolean checkImages(Path directory, Object value, int depth) throws IOException {
        if (depth > 32) return false;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (Iterator<String> it = object.keys(); it.hasNext();)
                if (!checkImages(directory, object.opt(it.next()), depth + 1)) return false;
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++)
                if (!checkImages(directory, array.opt(i), depth + 1)) return false;
        } else if (value instanceof String) {
            String reference = (String) value;
            if (reference.contains("://") || reference.startsWith("//")) return true;
            Matcher matches = IMAGE.matcher(reference);
            while (matches.find()) {
                Path path = directory.resolve(matches.group()).toRealPath();
                if (!path.startsWith(directory) || !Files.isRegularFile(path) || Files.size(path) == 0) return false;
            }
        }
        return true;
    }

}
