package es.foodcourt;

import android.view.accessibility.AccessibilityNodeInfo;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Data for Amici's «Añadir datos manualmente»: ticket number, visit date and total amount. */
final class AmiciTicket {
    /** Amici only accepts tickets issued in the last five days. */
    static final int MAX_AGE_DAYS = 5;
    final String number;
    final Date date;
    final String amount;

    private AmiciTicket(String number, Date date, String amount) {
        this.number = number;
        this.date = date;
        this.amount = amount;
    }

    static String encode(String number, Date date, String amount) {
        return number.trim() + "\n" + iso().format(date) + "\n" + normalizeAmount(amount);
    }

    static AmiciTicket parse(String value) {
        if (value == null) return null;
        String[] parts = value.split("\n", -1);
        if (parts.length != 3) return null;
        String number = parts[0].trim();
        String amount = normalizeAmount(parts[2]);
        if (!number.matches("[A-Za-z0-9/._-]{1,40}") || amount.isEmpty()) return null;
        try {
            SimpleDateFormat format = iso();
            format.setLenient(false);
            return new AmiciTicket(number, format.parse(parts[1].trim()), amount);
        } catch (ParseException invalid) {
            return null;
        }
    }

    /** Accepts «23,50», «23.5» or «23» and returns «23.50» / «23». Empty when invalid. */
    static String normalizeAmount(String value) {
        String amount = value == null ? "" : value.trim().replace("€", "").replace(" ", "").replace(',', '.');
        if (!amount.matches("[0-9]{1,5}(\\.[0-9]{1,2})?")) return "";
        int dot = amount.indexOf('.');
        if (dot >= 0 && amount.length() - dot == 2) amount += "0";
        return amount;
    }

    /** Text Amici shows in its date field once the date is chosen. */
    String displayDate() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(date);
    }

    /** Content description of the day in Android's DatePicker, for example «01 noviembre 2026». */
    String pickerLabel() {
        return new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(date);
    }

    /** Whether the ticket date is in an earlier month than the one the picker shows. */
    boolean isBeforeShownMonth(AccessibilityNodeInfo picker) {
        SimpleDateFormat format = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(picker);
        while (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.removeFirst();
            CharSequence description = node.getContentDescription();
            if (description != null) {
                try {
                    Calendar shown = Calendar.getInstance();
                    shown.setTime(format.parse(description.toString()));
                    Calendar target = Calendar.getInstance();
                    target.setTime(date);
                    return target.get(Calendar.YEAR) * 12 + target.get(Calendar.MONTH)
                        < shown.get(Calendar.YEAR) * 12 + shown.get(Calendar.MONTH);
                } catch (ParseException notADay) {
                    // Keep looking for a day cell.
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) nodes.add(child);
            }
        }
        return true;
    }

    private static SimpleDateFormat iso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    }
}
