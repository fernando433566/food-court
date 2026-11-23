package es.foodcourt;

import java.util.Arrays;
import java.util.List;

final class McDonalds implements Franchise {
    public String id() { return "mcdonalds"; }
    public String name() { return "McDonald’s"; }
    public String program() { return "MyMcDonald’s"; }
    public int accent() { return 0xFF942821; }
    public int onAccent() { return 0xFFFFD466; }
    public String officialPackage() { return "com.mcdonalds.mobileapp"; }
    public String officialUri(Section section) {
        switch (section) {
            case POINTS: return "gmalite://gmalite-loyalty";
            case REWARDS: return "gmalite://gmalite-deals-rewards-list";
            case DEALS: return "gmalite://gmalite-deals";
        }
        throw new AssertionError(section);
    }
    public String officialHint(Section section) {
        return section == Section.REWARDS
            ? "Si se abre la pantalla principal, entra en Premios y pulsa Ver todo."
            : null;
    }
    public String officialWebsite() { return "https://mcdonalds.es/mymcdonalds"; }
    public int demoStartingPoints() { return 650; }

    public List<Benefit> rewards() {
        return Arrays.asList(
            new Benefit("fries", "Patatas medianas", "Premio de ejemplo", 380),
            new Benefit("chicken", "McChicken™", "Premio de ejemplo", 800)
        );
    }

    public List<Benefit> deals() {
        return Arrays.asList(
            new Benefit("menu", "Menú con descuento", "Ejemplo de un 20 % de descuento", 0),
            new Benefit("coffee", "Café y desayuno", "Ejemplo de una promoción de desayuno", 0)
        );
    }
}
