package es.foodcourt;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class AmiciBenefits {
    final String level;
    final List<Offer> restaurant;
    final List<Offer> leisure;

    AmiciBenefits(String level, List<Offer> restaurant, List<Offer> leisure) {
        this.level = level;
        this.restaurant = restaurant;
        this.leisure = leisure;
    }

    static AmiciBenefits load(Context context) {
        SharedPreferences storage = preferences(context);
        return new AmiciBenefits(storage.getString("level", ""),
            read(storage.getString("restaurant", "[]")), read(storage.getString("leisure", "[]")));
    }

    /** Level and coupons as the sender reports them. */
    static AmiciBenefits fromRemote(JSONObject answer) {
        return new AmiciBenefits(answer.optString("level"), read(String.valueOf(answer.optJSONArray("restaurant"))),
            read(String.valueOf(answer.optJSONArray("leisure"))));
    }

    void save(Context context) {
        preferences(context).edit().putString("level", level).putString("restaurant", write(restaurant))
            .putString("leisure", write(leisure)).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("amici_benefits", Context.MODE_PRIVATE);
    }

    private static List<Offer> read(String json) {
        List<Offer> offers = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject offer = array.getJSONObject(i);
                offers.add(new Offer(offer.getString("title"), offer.optString("description")));
            }
        } catch (org.json.JSONException ignored) {
            offers.clear();
        }
        return offers;
    }

    private static String write(List<Offer> offers) {
        JSONArray array = new JSONArray();
        for (Offer offer : offers) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", offer.title);
                item.put("description", offer.description);
                array.put(item);
            } catch (org.json.JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        return array.toString();
    }

    static final class Offer {
        final String title;
        final String description;

        Offer(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }
}
