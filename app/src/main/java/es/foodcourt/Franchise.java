package es.foodcourt;

import java.util.List;

interface Franchise {
    String id();
    String name();
    String program();
    int accent();
    int onAccent();
    String officialPackage();
    String officialUri(Section section);
    default String officialHint(Section section) { return null; }
    String officialWebsite();
    int demoStartingPoints();
    List<Benefit> rewards();
    List<Benefit> deals();

    enum Section { POINTS, REWARDS, DEALS, LEISURE }

    final class Benefit {
        final String id;
        final String title;
        final String description;
        final int points;

        Benefit(String id, String title, String description, int points) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.points = points;
        }
    }
}
