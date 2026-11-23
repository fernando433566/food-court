package es.foodcourt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every brand the wallet supports, in the order they are offered. */
final class Franchises {
    static final List<Franchise> ALL = Collections.unmodifiableList(Arrays.asList(
        new McDonalds(), new BurgerKing(), new Popeyes(), new Kfc(), new TacoBell(), new Amici(), new ClubBy()));

    private Franchises() { }

    /** The brand with that id, or null. */
    static Franchise byId(String id) {
        for (Franchise franchise : ALL) {
            if (franchise.id().equals(id)) return franchise;
        }
        return null;
    }
}
