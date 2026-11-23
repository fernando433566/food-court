package es.foodcourt;

import java.util.Collections;
import java.util.List;

final class TacoBell implements Franchise {
    public String id() { return "taco-bell-es"; }
    public String name() { return "Taco Bell"; }
    public String program() { return "Taco Bell Club"; }
    public int accent() { return 0xFF633296; }
    public int onAccent() { return 0xFFF5E9FF; }
    public String officialPackage() { return "es.tacobell.app"; }
    public String officialWebsite() { return "https://tacobell.es/es/taco-bell-club"; }

    public String officialUri(Section section) {
        return section == Section.DEALS ? "https://tacobell.es/es/promociones" : officialWebsite();
    }

    public int demoStartingPoints() { return 0; }
    public List<Benefit> rewards() { return Collections.emptyList(); }
    public List<Benefit> deals() { return Collections.emptyList(); }
}
