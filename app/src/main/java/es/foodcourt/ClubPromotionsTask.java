package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Drives «Mis promociones → Promos» in Club·by: lists the promotions as
 * {"promotions":[{"title","level","until","location","kind"}]}, captures one promotion's QR as
 * {"title","qr","details":[…]}, or adds a promotion code through «Escribir» ({"message"}).
 */
final class ClubPromotionsTask extends AppTask {
    enum Mode { LIST, QR, ADD }
    /** Tabs of «Mis promociones» that are read; «Ocio» and «Canjeadas» are left out on purpose. */
    private static final String[] TABS = { "Promos" };
    private static final String[] LEVELS = { "bronce", "plata", "oro", "platino", "diamante", "black" };
    private final Mode mode;
    private final String value;
    private final Map<String, JSONObject> promotions = new LinkedHashMap<>();
    private int tab, scrolls, idleScrolls, homeClicks, captures, sentTicks;
    private boolean tabSelected, opened, sent, written;
    private String detailTitle = "";
    private List<String> details = new ArrayList<>();

    ClubPromotionsTask(Mode mode, String value) {
        this.mode = mode;
        this.value = value == null ? "" : value.trim();
    }

    @Override String packageName() { return ClubBy.PACKAGE; }

    @Override Intent launchIntent() { return ClubBy.appIntent(); }

    @Override long firstStepDelayMs() { return 1200; }

    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = collect(root);
        if (click(byLabel(nodes, "Saltar"))) return null; // First-run tips.
        if (byLabel(nodes, "Mis promociones") != null && byLabel(nodes, "Promos") != null) {
            switch (mode) {
                case LIST: return list(nodes);
                case QR: return findPromotion(nodes);
                default: return add(nodes);
            }
        }
        if (byLabel(nodes, "Ampliar QR") != null
                || byLabel(nodes, "Términos y condiciones de uso") != null) {
            if (mode == Mode.QR && opened) return detail(nodes);
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return null;
        }
        if (click(byLabel(nodes, "Tus promos"))) return null;
        if (homeClicks++ < 4) click(byLabel(nodes, "Inicio"));
        return null;
    }

    @Override String timeout() {
        switch (mode) {
            case LIST:
                return promotions.isEmpty()
                    ? error("No se pudieron leer las promociones de Club·by en el emisor. Comprueba la sesión.")
                    : listResult();
            case QR:
                return detailTitle.isEmpty()
                    ? error("No se encontró «" + value + "» en Mis promociones del emisor.")
                    : detailResult("");
            default:
                return sent ? message("Código enviado a Club·by. Actualiza tus promociones para comprobar si se añadió.")
                    : error("No se pudo escribir el código en Club·by del emisor.");
        }
    }

    // ---- Listing

    private String list(List<AccessibilityNodeInfo> nodes) {
        if (!tabSelected) {
            tabSelected = click(byLabel(nodes, TABS[tab]));
            scrolls = 0;
            idleScrolls = 0;
            return null;
        }
        int before = promotions.size();
        for (AccessibilityNodeInfo card : cards(nodes)) {
            JSONObject promotion = parse(card);
            if (promotion != null) merge(promotion);
        }
        idleScrolls = promotions.size() == before ? idleScrolls + 1 : 0;
        AccessibilityNodeInfo scroll = cardScroll(nodes);
        boolean moved = scroll != null && idleScrolls < 2 && scrolls++ < 8
            && scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        if (moved) return null;
        if (++tab < TABS.length) {
            tabSelected = false;
            if (scroll != null) {
                for (int i = 0; i < 10 && scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); i++) { }
            }
            return null;
        }
        return listResult();
    }

    private List<AccessibilityNodeInfo> cards(List<AccessibilityNodeInfo> nodes) {
        List<AccessibilityNodeInfo> cards = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence description = node.getContentDescription();
            if (node.isClickable() && description != null && description.toString().contains("Hasta el")) {
                cards.add(node);
            }
        }
        return cards;
    }

    /** Keeps fields read earlier when a later, partly hidden view of the same card lacks them. */
    private void merge(JSONObject promotion) {
        String key = promotion.optString("title");
        JSONObject previous = promotions.get(key);
        if (previous != null) {
            try {
                for (String field : new String[] { "level", "until", "location" }) {
                    if (promotion.optString(field).isEmpty()) promotion.put(field, previous.optString(field));
                }
                promotion.put("kind", promotion.optString("level").isEmpty() ? "personal" : "level");
            } catch (JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        promotions.put(key, promotion);
    }

    /** The list that holds the promotion cards (not the full-screen pager around it). */
    private AccessibilityNodeInfo cardScroll(List<AccessibilityNodeInfo> nodes) {
        List<AccessibilityNodeInfo> cards = cards(nodes);
        if (cards.isEmpty()) return null;
        for (AccessibilityNodeInfo node = cards.get(0).getParent(); node != null; node = node.getParent()) {
            CharSequence type = node.getClassName();
            if (node.isScrollable() && (type == null || !type.toString().contains("ViewPager"))) return node;
        }
        return null;
    }

    /** Card layout: location on the left; level, title and «Hasta el…» on the right. */
    private JSONObject parse(AccessibilityNodeInfo card) {
        Rect area = bounds(card);
        int middle = area.left + area.width() * 45 / 100;
        String level = "", until = "", location = "";
        List<AccessibilityNodeInfo> right = new ArrayList<>();
        for (AccessibilityNodeInfo node : collect(card)) {
            String text = text(node);
            if (text.isEmpty() || node == card) continue;
            if (text.startsWith("Hasta el")) until = text;
            else if (bounds(node).left >= middle) right.add(node);
            else if (!"Ver condiciones".equalsIgnoreCase(text)) location = text;
        }
        if (until.isEmpty()) {
            for (String part : String.valueOf(card.getContentDescription()).split(",\\s*")) {
                if (part.startsWith("Hasta el")) until = part.trim();
            }
        }
        StringBuilder title = new StringBuilder();
        for (AccessibilityNodeInfo node : right) {
            String text = text(node);
            if (level.isEmpty() && isLevel(text)) level = text;
            else title.append(title.length() == 0 ? "" : " ").append(text);
        }
        if (title.length() == 0) return null;
        try {
            return new JSONObject().put("title", title.toString()).put("level", level).put("until", until)
                .put("location", location).put("kind", level.isEmpty() ? "personal" : "level");
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isLevel(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String level : LEVELS) if (lower.equals(level)) return true;
        return false;
    }

    private String listResult() {
        JSONArray array = new JSONArray();
        for (JSONObject promotion : promotions.values()) array.put(promotion);
        try {
            return new JSONObject().put("promotions", array).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- QR of one promotion

    private String findPromotion(List<AccessibilityNodeInfo> nodes) {
        if (!tabSelected) {
            tabSelected = click(byLabel(nodes, TABS[tab]));
            scrolls = 0;
            return null;
        }
        for (AccessibilityNodeInfo card : cards(nodes)) {
            JSONObject promotion = parse(card);
            if (promotion != null && value.equalsIgnoreCase(promotion.optString("title")) && click(card)) {
                opened = true;
                return null;
            }
        }
        AccessibilityNodeInfo scroll = cardScroll(nodes);
        if (scroll != null && scrolls++ < 8 && scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return null;
        }
        if (++tab < TABS.length) {
            tabSelected = false;
            return null;
        }
        return error("No se encontró «" + value + "» en Mis promociones del emisor. Actualiza la lista.");
    }

    private String detail(List<AccessibilityNodeInfo> nodes) {
        detailTitle = "";
        details = new ArrayList<>();
        AccessibilityNodeInfo qr = null;
        for (AccessibilityNodeInfo node : nodes) {
            String id = node.getViewIdResourceName();
            if ("text-heading".equals(id) && detailTitle.isEmpty()) detailTitle = text(node);
            else if ("text-body".equals(id)) {
                String text = text(node);
                if (!text.isEmpty() && !text.equals("Ampliar QR") && !details.contains(text)
                        && !text.matches("Inicio|Explorar|Club-by|Perfil")) details.add(text);
            }
            CharSequence type = node.getClassName();
            if (type != null && type.toString().endsWith("SvgView") && bounds(node).width() >= 200
                    && (qr == null || bounds(node).width() > bounds(qr).width())) qr = node;
        }
        if (qr == null || android.os.Build.VERSION.SDK_INT < 30 || captures >= 3) return detailResult("");
        captures++;
        requestCapture(bounds(qr));
        return null;
    }

    @Override String onQr(String code) {
        if (code.isEmpty() && captures < 3) return null;
        return detailResult(code);
    }

    private String detailResult(String qr) {
        try {
            return new JSONObject().put("title", detailTitle.isEmpty() ? value : detailTitle)
                .put("qr", qr).put("details", new JSONArray(details)).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- Add a promotion code

    private String add(List<AccessibilityNodeInfo> nodes) {
        if (sent) {
            for (AccessibilityNodeInfo node : nodes) {
                String text = text(node).toLowerCase(Locale.ROOT);
                if (text.matches(".*(añadid|agregad|correctamente|no (es )?válid|incorrect|no existe|caducad"
                        + "|ya (la )?tienes|error).*")) return message(text(node));
            }
            if (++sentTicks > 12) {
                return message("Código enviado a Club·by. Actualiza tus promociones para comprobar si se añadió.");
            }
            return null;
        }
        AccessibilityNodeInfo input = byId(nodes, "add-promotion-dropdown-input");
        if (input == null) {
            click(byId(nodes, "add-promotion-dropdown-write-button"));
            return null;
        }
        if (!value.equals(text(input))) {
            if (!written) {
                setText(input, value);
                written = true;
            } else {
                written = false; // Retry once the field settles.
            }
            return null;
        }
        AccessibilityNodeInfo button = byId(nodes, "add-promotion-dropdown-add-button");
        if (button != null && button.isEnabled() && click(button)) sent = true;
        return null;
    }
}
