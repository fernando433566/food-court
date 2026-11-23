package es.foodcourt;

import java.util.Collections;
import java.util.List;

final class Kfc implements Franchise {
    public String id() { return "kfc-es"; }
    public String name() { return "KFC"; }
    public String program() { return "El Club de la Hucha"; }
    public int accent() { return 0xFFAC1730; }
    public int onAccent() { return 0xFFFFE6E9; }
    /** KFC is used through its website only (see FranchisePortalActivity). */
    public String officialPackage() { return ""; }
    public String officialWebsite() { return "https://www.kfc.es"; }

    public String officialUri(Section section) {
        switch (section) {
            case POINTS: return officialWebsite() + "/";
            case REWARDS: return officialWebsite() + "/loyalty";
            case DEALS: return officialWebsite() + "/ofertas";
        }
        throw new AssertionError(section);
    }

    public int demoStartingPoints() { return 0; }
    public List<Benefit> rewards() { return Collections.emptyList(); }
    public List<Benefit> deals() { return Collections.emptyList(); }
}
