package es.foodcourt;

import java.util.Arrays;
import java.util.List;

/** How each brand's card looks in the wallet and what its buttons open. */
final class WalletCardStyle {
    /** Where a menu button under the card leads. */
    enum Target { POINTS, REWARDS, DEALS, LEISURE, CLUB_READER }

    static final class MenuButton {
        final String label;
        final Target target;

        MenuButton(String label, Target target) {
            this.label = label;
            this.target = target;
        }
    }

    /** Drawn card; null for Club·by, which uses {@link ClubByTableArtwork} on a plain surface. */
    final FoodCardDrawable.Kind illustration;
    /** Line under the brand name; the card itself opens {@link Target#POINTS}. */
    final String mainAction;
    /** Background of the menu buttons. */
    final int control;
    final List<MenuButton> menu;

    private WalletCardStyle(FoodCardDrawable.Kind illustration, String mainAction, int control, MenuButton... menu) {
        this.illustration = illustration;
        this.mainAction = mainAction;
        this.control = control;
        this.menu = Arrays.asList(menu);
    }

    static WalletCardStyle of(Franchise brand, boolean oled) {
        if (brand instanceof ClubBy) {
            return new WalletCardStyle(null, "Mi tarjeta", oled ? 0xFF182C43 : 0xFFE7F4FF,
                button("Lector QR", Target.CLUB_READER), button("Promociones", Target.REWARDS),
                button("Ocio", Target.LEISURE));
        }
        if (brand instanceof Amici) {
            return new WalletCardStyle(FoodCardDrawable.Kind.TRATTORIA, "Escanear ticket", oled ? 0xFF482932 : 0xFFF6D8B3,
                button("Restaurante", Target.REWARDS), button("Ocio", Target.LEISURE));
        }
        if (brand instanceof TacoBell) {
            return new WalletCardStyle(FoodCardDrawable.Kind.WRAPPED_TACO, "Mi QR", oled ? 0xFF3C2152 : 0xFFF7EDFF,
                button("Escanear ticket", Target.REWARDS), button("Ofertas", Target.DEALS));
        }
        if (brand instanceof Kfc) {
            return new WalletCardStyle(FoodCardDrawable.Kind.STRIPED_BUCKET, "Mi QR", oled ? 0xFF4A2029 : 0xFFFFF0F1,
                button("Recompensas", Target.REWARDS), button("Cupones", Target.DEALS));
        }
        if (brand instanceof Popeyes) {
            return new WalletCardStyle(FoodCardDrawable.Kind.SPICY_BUCKET, "Código de tienda", oled ? 0xFF533219 : 0xFFFFE8CE,
                button("Recompensas", Target.REWARDS), button("Ofertas", Target.DEALS));
        }
        if (brand instanceof BurgerKing) {
            return new WalletCardStyle(FoodCardDrawable.Kind.GRILLED_BURGER, "Código de tienda", oled ? 0xFF43251C : 0xFFFFF2E3,
                button("Recompensas", Target.REWARDS), button("Ofertas", Target.DEALS));
        }
        return new WalletCardStyle(FoodCardDrawable.Kind.STACKED_BURGER, "Abrir QR de puntos", oled ? 0xFF40311B : 0xFFFFF4D3,
            button("Premios", Target.REWARDS), button("Deals", Target.DEALS));
    }

    private static MenuButton button(String label, Target target) {
        return new MenuButton(label, target);
    }
}
