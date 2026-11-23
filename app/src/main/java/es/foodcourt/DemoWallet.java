package es.foodcourt;

import android.content.SharedPreferences;
import java.util.Locale;
import java.util.UUID;

final class DemoWallet {
    private final SharedPreferences storage;

    DemoWallet(SharedPreferences storage) {
        this.storage = storage;
    }

    int balance(Franchise franchise) {
        return storage.getInt(franchise.id() + ".balance", franchise.demoStartingPoints());
    }

    String redeem(Franchise franchise, Franchise.Benefit benefit) {
        String key = franchise.id() + ".reward." + benefit.id;
        String existing = storage.getString(key, null);
        if (existing != null) return existing;

        int remaining = balance(franchise) - benefit.points;
        if (remaining < 0) return null;

        String code = newCode();
        storage.edit().putInt(franchise.id() + ".balance", remaining)
            .putString(key, code).apply();
        return code;
    }

    String redeemedCode(Franchise franchise, Franchise.Benefit benefit) {
        return storage.getString(franchise.id() + ".reward." + benefit.id, null);
    }

    static String newCode() {
        return "FC-DEMO-" + UUID.randomUUID().toString()
            .substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
