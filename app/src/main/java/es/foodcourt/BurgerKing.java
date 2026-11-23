package es.foodcourt;

import java.util.Collections;
import java.util.List;

final class BurgerKing implements Franchise {
    public String id() { return "burger-king-es"; }
    public String name() { return "Burger King"; }
    public String program() { return "MyBurgerKing"; }
    public int accent() { return 0xFFD32805; }
    public int onAccent() { return 0xFFFFF3DD; }
    public String officialPackage() { return "es.burgerking.android"; }
    public String officialWebsite() { return "https://www.burgerking.es/es"; }

    public String officialUri(Section section) {
        switch (section) {
            case POINTS: return officialWebsite() + "/redeem";
            case REWARDS: return officialWebsite() + "/rewards/list";
            case DEALS: return officialWebsite() + "/rewards/offers";
        }
        throw new AssertionError(section);
    }

    public int demoStartingPoints() { return 0; }
    public List<Benefit> rewards() { return Collections.emptyList(); }
    public List<Benefit> deals() { return Collections.emptyList(); }
}
