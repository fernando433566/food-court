package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads Amici: the level and the coupons (restaurant and partners) as
 * {"level","restaurant":[{"title","description"}],"leisure":[…]}, or one coupon's code as
 * {"title","code","available","discount","rules"}. Used by the sender for a receiver and by
 * {@link AmiciAccessService} on the phone that has Amici.
 */
final class AmiciBenefitsTask extends AppTask {
    private static final Amici FRANCHISE = new Amici();
    private final String couponTitle;
    private final Map<String, AmiciBenefits.Offer> restaurant = new LinkedHashMap<>();
    private final Map<String, AmiciBenefits.Offer> leisure = new LinkedHashMap<>();
    private String level = "";
    private boolean openedClub, partnerVisible, rewound, clickedOffer;
    private int scrolls, backPresses;

    /** @param couponTitle null to read level and coupons, or the coupon whose code is wanted. */
    AmiciBenefitsTask(String couponTitle) {
        this.couponTitle = couponTitle == null || couponTitle.trim().isEmpty() ? null : couponTitle.trim();
    }

    @Override String packageName() { return FRANCHISE.officialPackage(); }

    @Override Intent launchIntent() { return view(FRANCHISE.officialUri(Franchise.Section.POINTS), packageName()); }

    @Override long firstStepDelayMs() { return 1200; }

    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = collect(root);
        if (byId(nodes, "design_bottom_sheet") != null && byId(nodes, "txtTitle") == null
                && byId(nodes, "txtName") == null && backPresses < 3) {
            backPresses++; // A promotional sheet covers the app.
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return null;
        }
        if (couponTitle != null) return readCoupon(nodes);
        return readBenefits(nodes) ? benefits() : null;
    }

    @Override String timeout() {
        if (couponTitle != null) return error("Amici no mostró el código de «" + couponTitle + "» en el emisor.");
        return level.isEmpty() ? error("No se pudo leer Amici en el emisor. Comprueba que tenga la sesión iniciada.")
            : benefits();
    }

    private boolean readBenefits(List<AccessibilityNodeInfo> nodes) {
        String rank = rank(text(byId(nodes, "currentRankTextView")));
        if (!rank.isEmpty()) level = rank;
        AccessibilityNodeInfo header = byId(nodes, "partnerCouponsTextView");
        int partnerStart = header == null ? Integer.MAX_VALUE : bounds(header).top;
        if (header != null) partnerVisible = true;
        for (AccessibilityNodeInfo node : nodes) {
            if (!isId(node, "txtTitle")) continue;
            String title = text(node);
            if (title.isEmpty()) continue;
            AccessibilityNodeInfo card = node.getParent();
            String description = card == null ? "" : text(byId(collect(card), "txtDescription"));
            boolean partner = leisure.containsKey(title) || (!restaurant.containsKey(title)
                && (header == null ? partnerVisible : bounds(node).top > partnerStart));
            Map<String, AmiciBenefits.Offer> destination = partner ? leisure : restaurant;
            AmiciBenefits.Offer previous = destination.get(title);
            if (previous == null || !description.isEmpty()) {
                destination.put(title, new AmiciBenefits.Offer(title, description));
            }
        }
        AccessibilityNodeInfo scrollable = largestScrollable(nodes);
        if (level.isEmpty() && !openedClub && byId(nodes, "addVisitButton") == null) {
            openedClub = click(byId(nodes, "loyalty_nav_graph"));
            return false;
        }
        if (level.isEmpty()) {
            if (!openedClub) openedClub = click(byId(nodes, "loyalty_nav_graph"));
            if (!rewound && scrollable != null) {
                rewound = !scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            }
            return false;
        }
        if (scrolls++ >= 8) return true;
        return scrollable != null
            ? !scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            : partnerVisible;
    }

    private String readCoupon(List<AccessibilityNodeInfo> nodes) {
        String title = text(byId(nodes, "txtName"));
        if (couponTitle.equals(title)) {
            String code = text(byId(nodes, "txtCode"));
            if (!code.isEmpty()) {
                try {
                    return new JSONObject().put("title", title).put("code", code)
                        .put("available", text(byId(nodes, "txtAvailable")))
                        .put("discount", text(byId(nodes, "txtDiscount")))
                        .put("rules", text(byId(nodes, "txtLegalDescription"))).toString();
                } catch (JSONException impossible) {
                    throw new IllegalStateException(impossible);
                }
            }
            AccessibilityNodeInfo detail = largestScrollable(nodes);
            if (detail != null && scrolls++ < 3) detail.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            return null;
        }
        if (!title.isEmpty()) {
            // Another coupon is open: go back to the list.
            if (!openedClub) openedClub = click(byId(nodes, "loyalty_nav_graph"));
            return null;
        }
        if (clickedOffer) return null;
        for (AccessibilityNodeInfo node : nodes) {
            if (isId(node, "txtTitle") && couponTitle.equals(text(node)) && click(node)) {
                clickedOffer = true;
                return null;
            }
        }
        if (byId(nodes, "addVisitButton") == null && !openedClub) {
            openedClub = click(byId(nodes, "loyalty_nav_graph"));
            return null;
        }
        if (!openedClub) {
            openedClub = click(byId(nodes, "loyalty_nav_graph"));
            return null;
        }
        AccessibilityNodeInfo scroll = largestScrollable(nodes);
        if (scroll != null && !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) && !rewound) {
            rewound = !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        return null;
    }

    private String benefits() {
        try {
            return new JSONObject().put("level", level).put("restaurant", offers(restaurant))
                .put("leisure", offers(leisure)).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JSONArray offers(Map<String, AmiciBenefits.Offer> offers) throws JSONException {
        JSONArray array = new JSONArray();
        for (AmiciBenefits.Offer offer : offers.values()) {
            array.put(new JSONObject().put("title", offer.title).put("description", offer.description));
        }
        return array;
    }

    private boolean isId(AccessibilityNodeInfo node, String id) {
        return (packageName() + ":id/" + id).equals(node.getViewIdResourceName());
    }

    /** Amici shows the level in English or Spanish; the wallet always uses Spanish. */
    private static String rank(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "bronze": case "bronce": return "Bronce";
            case "silver": case "plata": return "Plata";
            case "gold": case "oro": case "dorado": return "Oro";
            default: return "";
        }
    }
}
