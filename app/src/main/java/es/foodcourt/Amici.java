package es.foodcourt;

import java.util.Collections;
import java.util.List;

final class Amici implements Franchise {
    public String id() { return "tagliatella-amici"; }
    public String name() { return "La Tagliatella"; }
    public String program() { return "Amici"; }
    public int accent() { return 0xFF8B2034; }
    public int onAccent() { return 0xFFFFE9BA; }
    public String officialPackage() { return "com.amrest.latagliatella"; }
    public String officialWebsite() { return "https://www.latagliatella.es/info-amici"; }
    public String officialUri(Section section) { return "https://www.latagliatella.es/amici"; }
    public int demoStartingPoints() { return 0; }
    public List<Benefit> rewards() { return Collections.emptyList(); }
    public List<Benefit> deals() { return Collections.emptyList(); }
}
