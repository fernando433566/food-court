package es.foodcourt;

import java.util.Collections;
import java.util.List;

final class Popeyes implements Franchise {
    public String id() { return "popeyes-es"; }
    public String name() { return "Popeyes"; }
    public String program() { return "Popeyes Club"; }
    public int accent() { return 0xFFE96413; }
    public int onAccent() { return 0xFFFFF3DD; }
    public String officialPackage() { return "es.popeyes.android"; }
    public String officialWebsite() { return "https://www.popeyes.es/es"; }

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
