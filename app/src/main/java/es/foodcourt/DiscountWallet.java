package es.foodcourt;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** «Mis códigos»: discount codes the user saved, stored as JSON in shared preferences. */
final class DiscountWallet {
    private static final Pattern DATE = Pattern.compile("\\b[0-9]{2}/[0-9]{2}/[0-9]{4}\\b");

    static List<Code> load(Context context) {
        List<Code> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(storage(context).getString("codes", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                result.add(new Code(item.getString("id"), item.getString("brand"), item.getString("title"),
                    item.getString("value"), item.getString("format"), item.optString("validity")));
            }
        } catch (JSONException ignored) {
            result.clear();
        }
        return result;
    }

    static void save(Context context, String brand, String title, String value, String format, String validity) {
        List<Code> data = load(context);
        for (java.util.Iterator<Code> it = data.iterator(); it.hasNext();) {
            Code existing = it.next();
            if (existing.brand.equals(brand) && existing.value.equals(value)) it.remove();
        }
        data.add(0, new Code(UUID.randomUUID().toString(), brand, title, value, format, validity));
        write(context, data);
    }

    static void remove(Context context, String id) {
        List<Code> data = load(context);
        for (java.util.Iterator<Code> it = data.iterator(); it.hasNext();) {
            if (it.next().id.equals(id)) it.remove();
        }
        write(context, data);
    }

    static boolean isExpired(String validity) {
        Matcher dates = DATE.matcher(validity);
        String end = null;
        while (dates.find()) end = dates.group();
        if (end == null) return false;
        SimpleDateFormat parser = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        parser.setLenient(false);
        try {
            Date date = parser.parse(end);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            return date != null && date.before(today.getTime());
        } catch (java.text.ParseException invalid) {
            return false;
        }
    }

    private static SharedPreferences storage(Context context) {
        return context.getSharedPreferences("discount_wallet", Context.MODE_PRIVATE);
    }

    private static void write(Context context, List<Code> codes) {
        JSONArray array = new JSONArray();
        for (Code code : codes) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", code.id);
                item.put("brand", code.brand);
                item.put("title", code.title);
                item.put("value", code.value);
                item.put("format", code.format);
                item.put("validity", code.validity);
                array.put(item);
            } catch (JSONException invalid) {
                throw new IllegalStateException(invalid);
            }
        }
        storage(context).edit().putString("codes", array.toString()).apply();
    }

    static final class Code {
        final String id;
        final String brand;
        final String title;
        final String value;
        final String format;
        final String validity;

        Code(String id, String brand, String title, String value, String format, String validity) {
            this.id = id;
            this.brand = brand;
            this.title = title;
            this.value = value;
            this.format = format;
            this.validity = validity;
        }
    }
}
