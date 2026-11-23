package es.foodcourt;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Best-effort split of an Amici ticket QR into ticket number, visit date and total.
 * The QR format is not documented, so this assumes it carries the same data as the manual form
 * in plain text and guesses where each part starts and ends. The user always reviews the result.
 */
final class AmiciTicketQr {
    /** How far back a date found in the QR may be to be taken as the visit date. */
    private static final int RECENT_DAYS = 60;
    private static final Pattern AMOUNT = Pattern.compile("^[0-9]{1,5}[.,][0-9]{2}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s;|,#&=_]+");
    private static final String[] TOKEN_DATES = {
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yy", "dd-MM-yy",
        "yyyyMMdd", "ddMMyyyy", "ddMMyy", "yyMMdd"
    };
    /** Formats searched inside a run of digits, longest first. */
    private static final String[] EMBEDDED_DATES = { "yyyyMMdd", "ddMMyyyy", "ddMMyy", "yyMMdd" };

    static final class Guess {
        String number = "";
        Date date;
        String amount = "";

        boolean isEmpty() {
            return number.isEmpty() && date == null && amount.isEmpty();
        }
    }

    static Guess guess(String raw, Date today) {
        Guess result = new Guess();
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return result;
        // A URL may carry the data in its path or query: keep only that part.
        int query = text.indexOf('?');
        if (text.matches("(?i)^https?://.*")) {
            text = query >= 0 ? text.substring(query + 1) : text.replaceFirst("(?i)^https?://[^/]+/?", "");
        }
        // Keep decimal commas («23,50») before commas are treated as separators.
        text = text.replaceAll("(?<![0-9/.-])([0-9]{1,5}),([0-9]{2})(?![0-9])", "$1.$2");
        List<String> tokens = new ArrayList<>();
        for (String token : SEPARATORS.split(text)) {
            if (!token.isEmpty()) tokens.add(token);
        }
        // Prefer tokens that clearly are a date or an amount.
        List<String> rest = new ArrayList<>();
        for (String token : tokens) {
            String clean = token.replace("€", "");
            if (result.amount.isEmpty() && AMOUNT.matcher(clean).matches()) {
                result.amount = AmiciTicket.normalizeAmount(clean);
                continue;
            }
            if (result.date == null && clean.matches(".*[/.-].*")) {
                Date date = parseToken(clean, today);
                if (date != null) {
                    result.date = date;
                    continue;
                }
            }
            if (clean.contains(":") && clean.matches("[0-9:]+")) continue; // A time, not useful.
            rest.add(clean);
        }
        if (result.date == null) {
            for (int i = 0; i < rest.size(); i++) {
                String token = rest.get(i);
                if (!token.matches("[0-9]{6,8}")) continue;
                Date date = parseToken(token, today);
                if (date != null) {
                    result.date = date;
                    rest.remove(i);
                    break;
                }
            }
        }
        if (result.date == null && rest.size() == 1 && rest.get(0).matches("[0-9]{10,}")) {
            splitDigits(rest.get(0), today, result);
            return result;
        }
        // The ticket number is the longest remaining token that contains digits.
        for (String token : rest) {
            if (token.matches(".*[0-9].*") && token.matches("[A-Za-z0-9/._-]{1,40}")
                    && token.length() > result.number.length()) {
                result.number = token;
            }
        }
        if (result.amount.isEmpty()) {
            for (String token : rest) {
                if (!token.equals(result.number) && token.matches("[0-9]{1,5}[.,][0-9]{1,2}")) {
                    result.amount = AmiciTicket.normalizeAmount(token);
                    break;
                }
            }
        }
        return result;
    }

    /** One run of digits: find a recent date inside, then ticket number and amount around it. */
    private static void splitDigits(String digits, Date today, Guess result) {
        for (String pattern : EMBEDDED_DATES) {
            int length = pattern.length();
            for (int start = 0; start + length <= digits.length(); start++) {
                Date date = parse(digits.substring(start, start + length), pattern, today);
                if (date == null) continue;
                String before = digits.substring(0, start);
                String after = digits.substring(start + length);
                String number = before.length() >= after.length() ? before : after;
                String other = number == before ? after : before;
                if (number.isEmpty()) continue;
                result.date = date;
                result.number = stripZeros(number);
                if (other.matches("[0-9]{3,7}")) {
                    long cents = Long.parseLong(other);
                    result.amount = String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
                }
                return;
            }
        }
        result.number = digits.length() <= 40 ? digits : "";
    }

    private static String stripZeros(String number) {
        String stripped = number.replaceFirst("^0+(?=.)", "");
        return stripped.length() <= 40 ? stripped : number.substring(number.length() - 40);
    }

    private static Date parseToken(String token, Date today) {
        for (String pattern : TOKEN_DATES) {
            if (pattern.length() != token.length()) continue;
            Date date = parse(token, pattern, today);
            if (date != null) return date;
        }
        return null;
    }

    private static Date parse(String value, String pattern, Date today) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setLenient(false);
        try {
            Date date = format.parse(value);
            if (!format.format(date).equals(value)) return null;
            Calendar earliest = Calendar.getInstance();
            earliest.setTime(today);
            earliest.add(Calendar.DAY_OF_MONTH, -RECENT_DAYS);
            Calendar latest = Calendar.getInstance();
            latest.setTime(today);
            latest.add(Calendar.DAY_OF_MONTH, 1);
            return date.after(earliest.getTime()) && date.before(latest.getTime()) ? date : null;
        } catch (ParseException invalid) {
            return null;
        }
    }
}
