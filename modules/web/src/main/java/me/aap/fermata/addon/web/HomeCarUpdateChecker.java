package me.aap.fermata.addon.web;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;

/**
 * Phone-side HomeCar GitHub release checker.
 *
 * - Uses the public GitHub Releases API (no token, password or HA data).
 * - At most once per 24h automatically, manual checks always permitted.
 * - Never executes an installer or opens prompts in a projected CarActivity.
 * - Never blocks the UI or holds the WebView's lifecycle.
 */
public final class HomeCarUpdateChecker {
    private static final String API =
            "https://api.github.com/repos/RenanEspinar/HomeCar/releases?per_page=20";
    private static final String RELEASE_URL_PREFIX =
            "https://github.com/RenanEspinar/HomeCar/releases/";
    private static final long INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;
    private static final String PREFS = "homecar_update_check";
    private static final String LAST_CHECK = "last_successful_check";
    private static final String LAST_NOTIFIED = "last_notified_version";
    private static final Pattern VERSION = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+]([a-zA-Z0-9.\\-]+))?$"
    );
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private HomeCarUpdateChecker() {}

    public static void check(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Context app = activity.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(LAST_CHECK, 0);
        // Guard against clock changes as well as the ordinary 24-hour limit.
        if (!manual && last > 0 && now >= last && (now - last) < INTERVAL_MS) return;
        if (!RUNNING.compareAndSet(false, true)) return;

        final String current = BuildConfig.VERSION_NAME;
        Thread worker = new Thread(() -> {
            Result result = fetchLatest(current);
            activity.runOnUiThread(() -> {
                RUNNING.set(false);
                if (result.success) prefs.edit().putLong(LAST_CHECK,
                        System.currentTimeMillis()).apply();
                if (activity.isFinishing() || activity.isDestroyed()) return;

                if (!result.success) {
                    if (manual) message(activity, "Actualizaciones de HomeCar",
                            "No se pudo consultar GitHub: " + result.error);
                    return;
                }
                if (result.tag == null) {
                    if (manual) message(activity, "Actualizaciones de HomeCar",
                            "Aún no hay versiones publicadas en GitHub Releases.");
                    return;
                }
                if (!result.newer) {
                    if (manual) message(activity, "HomeCar actualizado",
                            "Versión instalada: " + current + "\nÚltima versión publicada: "
                                    + result.tag + "\n\nNo hay actualizaciones disponibles.");
                    return;
                }

                if (!manual && result.tag.equals(prefs.getString(LAST_NOTIFIED, ""))) return;
                prefs.edit().putString(LAST_NOTIFIED, result.tag).apply();

                final String url = result.url;
                new AlertDialog.Builder(activity)
                        .setTitle("Nueva versión de HomeCar")
                        .setMessage("Instalada: " + current + "\nDisponible: " + result.tag
                                + "\n\nPuedes revisar los cambios y descargarla desde GitHub. "
                                + "HomeCar no instala APK automáticamente.")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Ver en GitHub", (d, which) -> {
                            if (url == null || !url.startsWith(RELEASE_URL_PREFIX)) return;
                            try {
                                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (ActivityNotFoundException ignored) {
                                message(activity, "Actualizaciones de HomeCar",
                                        "No se encontró un navegador para abrir GitHub.");
                            }
                        }).show();
            });
        }, "HomeCar-GitHub-Updates");
        worker.setDaemon(true);
        worker.start();
    }

    private static Result fetchLatest(String installed) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "HomeCar-Android-UpdateChecker");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return Result.fail("HTTP " + code);
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = stream.read(buf)) != -1) {
                    if (bytes.size() + len > 512 * 1024) return Result.fail("Respuesta demasiado grande");
                    bytes.write(buf, 0, len);
                }
            }
            JSONArray releases = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            String bestTag = null;
            String bestUrl = null;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || release.optBoolean("draft", false)) continue;
                String tag = release.optString("tag_name", "");
                if (parsedVersion(tag) == null) continue;
                String url = release.optString("html_url", "");
                if (!url.startsWith(RELEASE_URL_PREFIX)) continue;
                if (bestTag == null || compareVersions(tag, bestTag) > 0) {
                    bestTag = tag;
                    bestUrl = url;
                }
            }
            return Result.ok(bestTag, bestUrl,
                    bestTag != null && compareVersions(bestTag, installed) > 0);
        } catch (Exception ex) {
            return Result.fail(ex.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Returns major, minor, patch, pre-release precedence or null. */
    private static int[] parsedVersion(String version) {
        Matcher match = VERSION.matcher(version.trim());
        if (!match.matches()) return null;
        try {
            int major = Integer.parseInt(match.group(1));
            int minor = Integer.parseInt(match.group(2));
            int patch = Integer.parseInt(match.group(3));
            String suffix = match.group(4);
            int stage = 100000; // A stable release beats all its pre-releases.
            if (suffix != null && !suffix.isEmpty()) {
                String s = suffix.toLowerCase(Locale.ROOT);
                String[] parts = s.split("\\.");
                int n = 0;
                if (parts.length >= 2) {
                    try { n = Math.min(9999, Integer.parseInt(parts[1])); }
                    catch (NumberFormatException ignored) { n = 0; }
                }
                if (s.startsWith("rc")) stage = 50000 + n;
                else if (s.startsWith("beta")) stage = 30000 + n;
                else if (s.startsWith("alpha")) stage = 10000 + n;
                else stage = 1000 + n;
            }
            return new int[] {major, minor, patch, stage};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int compareVersions(String left, String right) {
        int[] a = parsedVersion(left);
        int[] b = parsedVersion(right);
        if (a == null || b == null) return 0;
        for (int i = 0; i < 4; i++) {
            int compared = Integer.compare(a[i], b[i]);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static void message(Activity activity, String title, String description) {
        if (!activity.isFinishing() && !activity.isDestroyed())
            new AlertDialog.Builder(activity).setTitle(title)
                    .setMessage(description).setPositiveButton(android.R.string.ok, null).show();
    }

    private static final class Result {
        final boolean success;
        final String tag;
        final String url;
        final boolean newer;
        final String error;

        private Result(boolean success, String tag, String url, boolean newer, String error) {
            this.success = success;
            this.tag = tag;
            this.url = url;
            this.newer = newer;
            this.error = error;
        }

        static Result ok(String tag, String url, boolean newer) {
            return new Result(true, tag, url, newer, null);
        }
        static Result fail(String error) {
            return new Result(false, null, null, false, error);
        }
    }
}
