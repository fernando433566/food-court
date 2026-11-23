package es.foodcourt;

import android.net.Uri;
import android.text.Html;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

final class ClubByCatalog {
    enum Brand {
        ALL("Club·by", "club", ""),
        VIPS("VIPS", "vips", "www.vips.es"),
        FOSTERS("Foster’s Hollywood", "fostershollywood", "www.fostershollywood.es"),
        GINOS("Ginos", "ginos", "www.ginos.es"),
        FRIDAYS("Fridays", "tgifridays", "www.tgifridays.es");

        final String label, key, host;

        Brand(String label, String key, String host) {
            this.label = label;
            this.key = key;
            this.host = host;
        }

        String url() { return "https://" + host + "/promociones"; }
    }

    private static final Pattern PIN = Pattern.compile("data-promo\\s*=\\s*\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE);
    private static final int MAX_BODY = 3 * 1024 * 1024;

    static List<Offer> read(Brand source) throws IOException {
        HttpsURLConnection request = (HttpsURLConnection) new URL(source.url()).openConnection();
        request.setConnectTimeout(5500);
        request.setReadTimeout(7000);
        request.setInstanceFollowRedirects(false);
        request.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FoodCourt");
        try {
            if (request.getResponseCode() != 200) throw new IOException("Catálogo no disponible.");
            try (InputStream stream = request.getInputStream()) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int amount;
                while ((amount = stream.read(buffer)) != -1) {
                    if (data.size() + amount > MAX_BODY) throw new IOException("Catálogo demasiado grande.");
                    data.write(buffer, 0, amount);
                }
                return parse(new String(data.toByteArray(), StandardCharsets.UTF_8), source);
            }
        } finally {
            request.disconnect();
        }
    }

    private static List<Offer> parse(String html, Brand source) throws IOException {
        Map<String, Offer> offers = new LinkedHashMap<>();
        Matcher rows = PIN.matcher(html);
        boolean found = false;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        while (rows.find()) {
            try {
                JSONObject item = new JSONObject(decode(rows.group(1)));
                found = true;
                String brand = value(item, "brand");
                boolean collective = brand.equals(Brand.ALL.key)
                    && value(item, "marca").equalsIgnoreCase("multimarca");
                if (!collective && !brand.equals(source.key)) continue;
                String start = value(item, "fechaDesde");
                String until = value(item, "fechaHasta");
                if (item.optBoolean("expired")
                        || (start.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && start.compareTo(today) > 0)
                        || (until.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && until.compareTo(today) < 0)) continue;
                String title = decode(value(item, "nombrePromocion"));
                if (title.isEmpty()) continue;
                String uri = value(item, "enlace1Link");
                if (!isTrusted(uri)) uri = source.url();
                String terms = decode(value(item, "enlace2Content"));
                if (terms.isEmpty() && value(item, "enlace1Text").toLowerCase(Locale.ROOT).contains("condiciones")) {
                    terms = decode(value(item, "enlace1Content"));
                }
                Offer offer = new Offer(title, decode(value(item, "descripcion")), terms,
                    decode(value(item, "aplicaEn")), until, source, collective, uri,
                    value(item, "numeroDeCupon"));
                if (!offers.containsKey(offer.key())) offers.put(offer.key(), offer);
            } catch (JSONException ignored) {
                // An invalid tile does not prevent the other offers from loading.
            }
        }
        if (!found) throw new IOException("Formato de catálogo no disponible.");
        return new ArrayList<>(offers.values());
    }

    static boolean isTrusted(String address) {
        Uri uri = Uri.parse(address);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
        String host = uri.getHost();
        for (Brand brand : Brand.values()) {
            if (brand.host.equalsIgnoreCase(host)) return true;
        }
        return "www.clubby.es".equalsIgnoreCase(host) || "clubby.es".equalsIgnoreCase(host);
    }

    private static String value(JSONObject item, String name) {
        if (item.isNull(name)) return "";
        Object value = item.opt(name);
        return value instanceof String || value instanceof Number ? String.valueOf(value).trim() : "";
    }

    @SuppressWarnings("deprecation")
    private static String decode(String value) {
        return Html.fromHtml(value).toString().trim();
    }

    static final class Offer {
        final String title, description, terms, channel, end, url, code;
        final Brand source;
        final boolean collective;

        Offer(String title, String description, String terms, String channel, String end, Brand source,
                boolean collective, String url, String code) {
            this.title = title;
            this.description = description;
            this.terms = terms;
            this.channel = channel;
            this.end = end;
            this.source = source;
            this.collective = collective;
            this.url = url;
            this.code = code;
        }

        String key() {
            return (collective ? "club" : source.key) + '|' + title.toLowerCase(Locale.ROOT)
                + '|' + channel + '|' + end;
        }
    }
}
