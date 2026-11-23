package es.foodcourt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MainActivity extends Activity {
    private WalletTheme theme;
    private static final int WHITE = 0xFFFFFFFF;
    private final List<Franchise> franchises = Franchises.ALL;
    private final List<String> addedCards = new ArrayList<>();

    private enum Screen { HOME, MANAGE_CARDS, POINTS, REWARDS, DEALS }

    private Screen screen = Screen.HOME;
    private Franchise franchise;
    private DemoWallet wallet;
    private LinearLayout page;
    private String pointsCode;
    private TextView amiciLevelLabel;
    private View amiciCard;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        wallet = new DemoWallet(getSharedPreferences("demo_wallet", MODE_PRIVATE));
        String storedCards = getPreferences(MODE_PRIVATE).getString("addedCards", "");
        if (!storedCards.isEmpty()) addedCards.addAll(Arrays.asList(storedCards.split(",")));
        if (saved != null) {
            franchise = Franchises.byId(saved.getString("franchise"));
            Screen restored = Screen.valueOf(saved.getString("screen", "HOME"));
            if (franchise != null || restored == Screen.MANAGE_CARDS) screen = restored;
            pointsCode = saved.getString("pointsCode");
        }
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        updateAmiciLevel();
    }

    private void updateAmiciLevel() {
        if (amiciLevelLabel == null) return;
        String level = AmiciBenefits.load(this).level;
        amiciLevelLabel.setVisibility(level.isEmpty() ? View.GONE : View.VISIBLE);
        amiciLevelLabel.setText("Amici " + level);
        amiciCard.setContentDescription("La Tagliatella, Escanear ticket"
            + (level.isEmpty() ? "" : ", Amici " + level));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", screen.name());
        if (franchise != null) state.putString("franchise", franchise.id());
        state.putString("pointsCode", pointsCode);
    }

    @Override public void onBackPressed() {
        if (screen == Screen.HOME) {
            super.onBackPressed();
        } else {
            navigate(Screen.HOME);
        }
    }

    private void navigate(Screen destination) {
        screen = destination;
        render();
    }

    private void render() {
        amiciLevelLabel = null;
        amiciCard = null;
        theme = new WalletTheme(this);
        theme.apply(this);
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(theme.paper);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            }
            return insets;
        });
        page = column();
        page.setPadding(dp(22), dp(18), dp(22), dp(screen == Screen.HOME ? 94 : 24));
        scroll.addView(page);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(content);

        if (screen == Screen.HOME) {
            renderWallet();
            TextView addCard = button("+", theme.accent, theme.onAccent, () -> navigate(Screen.MANAGE_CARDS));
            addCard.setContentDescription("Gestionar tarjetas");
            addCard.setTextSize(30);
            addCard.setPadding(0, 0, 0, 0);
            addCard.setElevation(dp(5));
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.BOTTOM | Gravity.END);
            position.setMarginEnd(dp(22));
            position.bottomMargin = dp(22);
            content.addView(addCard, position);
            return;
        }

        if (screen == Screen.MANAGE_CARDS) {
            renderCardManager();
            return;
        }

        action(page, "‹  Franquicias", theme.secondary, theme.ink, () -> navigate(Screen.HOME));
        heading(page, franchise.name(), 33, theme.ink);
        LinearLayout tabs = new LinearLayout(this);
        add(page, tabs, 12);
        tab(tabs, "Puntos", Screen.POINTS);
        tab(tabs, "Premios", Screen.REWARDS);
        tab(tabs, "Deals", Screen.DEALS);

        switch (screen) {
            case POINTS:
                officialAction("Abrir mi QR oficial", Franchise.Section.POINTS);
                renderPoints();
                break;
            case REWARDS:
                officialAction("Ver premios oficiales", Franchise.Section.REWARDS);
                heading(page, wallet.balance(franchise) + " puntos de prueba", 25, theme.ink);
                for (Franchise.Benefit item : franchise.rewards()) rewardCard(item);
                break;
            case DEALS:
                officialAction("Ver ofertas oficiales", Franchise.Section.DEALS);
                heading(page, "Ofertas de ejemplo", 25, theme.ink);
                for (Franchise.Benefit item : franchise.deals()) dealCard(item);
                break;
        }
    }

    private void renderWallet() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView wordmark = label("Food Court", 23, theme.ink);
        wordmark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(button(theme.oled ? "Tema OLED" : "Tema claro", theme.secondary,
            theme.ink, this::showAppearance));
        add(page, top, 2);

        heading(page, "Tu cartera", 51, theme.ink);
        if (addedCards.isEmpty()) return;
        action(page, "Mis códigos", theme.secondary, theme.ink, () -> {
            String[] brands = new String[franchises.size()];
            for (int i = 0; i < brands.length; i++) brands[i] = franchises.get(i).name();
            startActivity(new Intent(this, DiscountWalletActivity.class).putExtra("brands", brands));
        });
        for (String id : addedCards) {
            for (Franchise item : franchises) {
                if (item.id().equals(id)) brandCard(item);
            }
        }
    }

    private void renderCardManager() {
        float density = getResources().getDisplayMetrics().density;
        ((View) page.getParent().getParent()).setBackground(new StoreroomArt.TileWall(theme.oled, density));
        action(page, "‹  Cartera", theme.secondary, theme.ink, () -> navigate(Screen.HOME));
        heading(page, "Almacén", 43, theme.ink);
        add(page, label("Saca las tarjetas de la estantería a tu cartera, o guárdalas en una caja. "
            + "Mantén pulsada una tarjeta para arrastrarla.", 15, theme.muted), 4);

        LinearLayout counter = column();
        counter.setBackground(new StoreroomArt.SteelPanel(theme.oled, density));
        counter.setPadding(dp(16), dp(14), dp(16), dp(16));
        heading(counter, "En tu cartera", 20, theme.ink);
        acceptDrops(counter, true);
        for (String id : addedCards) {
            for (Franchise item : franchises) {
                if (item.id().equals(id)) walletRow(counter, item);
            }
        }
        if (addedCards.isEmpty()) add(counter, label("Arrastra aquí una caja de la estantería", 14, theme.muted), 10);
        add(page, counter, 16);

        TextView sign = label("ALMACÉN · ESTANTERÍA 1", 13, theme.oled ? 0xFF1A1D21 : 0xFF2E3338);
        sign.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        sign.setLetterSpacing(.12f);
        sign.setPadding(dp(12), dp(5), dp(12), dp(5));
        sign.setBackground(WalletTheme.shape(theme.oled ? 0xFFB9C0C8 : 0xFFF2D24B, dp(4)));
        LinearLayout.LayoutParams signSize = new LinearLayout.LayoutParams(-2, -2);
        signSize.gravity = Gravity.CENTER_HORIZONTAL;
        signSize.topMargin = dp(22);
        page.addView(sign, signSize);

        LinearLayout shelving = column();
        acceptDrops(shelving, false);
        List<Franchise> stored = new ArrayList<>();
        for (Franchise item : franchises) {
            if (!addedCards.contains(item.id())) stored.add(item);
        }
        int rows = Math.max(1, (stored.size() + 1) / 2);
        for (int row = 0; row < rows; row++) {
            LinearLayout shelf = new LinearLayout(this);
            shelf.setGravity(Gravity.BOTTOM);
            shelf.setBackground(new StoreroomArt.ShelfRow(theme.oled, row == 0, density));
            int post = StoreroomArt.ShelfRow.postWidth(density);
            shelf.setPadding(post + dp(8), dp(row == 0 ? 14 : 10), post + dp(8),
                StoreroomArt.ShelfRow.shelfHeight(density));
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, dp(118), 1f);
                if (column == 1) slot.setMarginStart(dp(10));
                if (index < stored.size()) {
                    shelf.addView(storageBox(stored.get(index), density), slot);
                } else if (stored.isEmpty() && column == 0) {
                    TextView empty = label("Arrastra aquí para guardar una tarjeta", 14, theme.muted);
                    empty.setGravity(Gravity.CENTER);
                    shelf.addView(empty, new LinearLayout.LayoutParams(0, dp(118), 2f));
                    break;
                } else {
                    shelf.addView(new View(this), slot);
                }
            }
            shelving.addView(shelf, new LinearLayout.LayoutParams(-1, -2));
        }
        // Feet of the shelving unit.
        LinearLayout feet = new LinearLayout(this);
        for (int i = 0; i < 2; i++) {
            View foot = new View(this);
            foot.setBackground(WalletTheme.shape(theme.oled ? 0xFF2B3036 : 0xFF6F7882, dp(2)));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(StoreroomArt.ShelfRow.postWidth(density) + dp(6), dp(8));
            feet.addView(foot, size);
            if (i == 0) feet.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        shelving.addView(feet, new LinearLayout.LayoutParams(-1, -2));
        add(page, shelving, 8);
    }

    private void acceptDrops(View zone, boolean inWallet) {
        zone.setOnDragListener((view, event) -> {
            if (!(event.getLocalState() instanceof Franchise)) return false;
            if (event.getAction() == DragEvent.ACTION_DROP) {
                moveCard((Franchise) event.getLocalState(), inWallet, null, false);
            }
            return true;
        });
    }

    private View emblem(Franchise item, int size) {
        View badge = new View(this);
        badge.setBackground(new StoreroomArt.Emblem(item.id(), getResources().getDisplayMetrics().density));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return badge;
    }

    private boolean startCardDrag(View view, Franchise item) {
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        return android.os.Build.VERSION.SDK_INT >= 24
            ? view.startDragAndDrop(null, shadow, item, 0)
            : view.startDrag(null, shadow, item, 0);
    }

    /** A card on the steel counter: emblem, name and a button to put it back on the shelf. */
    private void walletRow(LinearLayout zone, Franchise item) {
        FrameLayout tile = new FrameLayout(this);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(58));
        row.setPadding(dp(8), dp(5), dp(4), dp(5));
        row.setBackground(WalletTheme.button(theme.oled ? 0xCC101215 : 0xE6FFFFFF, dp(12)));
        row.setElevation(dp(2));
        tile.addView(row, new FrameLayout.LayoutParams(-1, -2));
        row.addView(emblem(item, 40));
        TextView name = label(item.name(), 16, theme.ink);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setPadding(dp(10), 0, dp(4), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView grip = label("⋮⋮", 16, theme.muted);
        grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(grip);
        TextView move = button("−", theme.secondary, theme.ink, () -> moveCard(item, false, null, false));
        move.setContentDescription("Guardar en el almacén: " + item.name());
        move.setTextSize(20);
        move.setMinHeight(0);
        move.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams moveSize = new LinearLayout.LayoutParams(dp(44), dp(44));
        moveSize.setMarginStart(dp(6));
        row.addView(move, moveSize);

        View marker = new View(this);
        marker.setBackgroundColor(theme.accent);
        marker.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams markerPosition = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        tile.addView(marker, markerPosition);
        tile.setOnLongClickListener(view -> startCardDrag(tile, item));
        tile.setOnDragListener((view, event) -> {
            if (!(event.getLocalState() instanceof Franchise)) return false;
            Franchise dragged = (Franchise) event.getLocalState();
            int action = event.getAction();
            if (action == DragEvent.ACTION_DRAG_LOCATION && dragged != item) {
                int edge = event.getY() < tile.getHeight() / 2f ? Gravity.TOP : Gravity.BOTTOM;
                if (markerPosition.gravity != edge) {
                    markerPosition.gravity = edge;
                    marker.setLayoutParams(markerPosition);
                }
                marker.setVisibility(View.VISIBLE);
            } else if (action == DragEvent.ACTION_DRAG_EXITED || action == DragEvent.ACTION_DRAG_ENDED) {
                marker.setVisibility(View.INVISIBLE);
            } else if (action == DragEvent.ACTION_DROP) {
                marker.setVisibility(View.INVISIBLE);
                if (dragged != item) moveCard(dragged, true, item.id(), event.getY() >= tile.getHeight() / 2f);
            }
            return true;
        });
        add(zone, tile, 8);
    }

    /** A cardboard box on the shelf with the brand emblem and name on its label. */
    private View storageBox(Franchise item, float density) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(new StoreroomArt.Box(theme.oled, item.accent(), density));
        box.setFocusable(true);
        box.setClickable(true);
        box.setContentDescription("Caja de " + item.name() + ". Toca para añadirla a la cartera.");
        box.setOnClickListener(view -> moveCard(item, true, null, false));
        box.setOnLongClickListener(view -> startCardDrag(box, item));

        LinearLayout label = column();
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        label.addView(emblem(item, 40));
        TextView name = label(item.name(), 12, 0xFF2A1B10);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.addView(name, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams labelArea = new FrameLayout.LayoutParams(-1, -1);
        labelArea.topMargin = Math.round(118 * density * .30f);
        labelArea.bottomMargin = Math.round(118 * density * .14f);
        labelArea.leftMargin = dp(10);
        labelArea.rightMargin = dp(10);
        box.addView(label, labelArea);

        TextView add = button("+", theme.accent, theme.onAccent, () -> moveCard(item, true, null, false));
        add.setContentDescription("Añadir a la cartera: " + item.name());
        add.setTextSize(18);
        add.setMinHeight(0);
        add.setPadding(0, 0, 0, 0);
        add.setElevation(dp(3));
        FrameLayout.LayoutParams addSize = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
        addSize.topMargin = dp(2);
        addSize.rightMargin = dp(0);
        box.addView(add, addSize);
        return box;
    }

    private void moveCard(Franchise item, boolean inWallet, String anchor, boolean after) {
        boolean wasInWallet = addedCards.remove(item.id());
        if (inWallet) {
            int position = addedCards.indexOf(anchor);
            addedCards.add(position < 0 ? addedCards.size() : position + (after ? 1 : 0), item.id());
        } else if (!wasInWallet) {
            return;
        }
        getPreferences(MODE_PRIVATE).edit()
            .putString("addedCards", android.text.TextUtils.join(",", addedCards)).apply();
        render();
        // Brands that work through a sender phone: offer to set up the exchange the first time.
        boolean usesSender = item instanceof McDonalds || item instanceof TacoBell
            || item instanceof ClubBy || item instanceof Amici;
        if (!wasInWallet && inWallet && usesSender && !SenderClient.isConfigured(this)) {
            startActivity(new Intent(this, ExchangeActivity.class).putExtra(ExchangeActivity.SET_UP, true));
        }
    }

    private void showAppearance() {
        new AlertDialog.Builder(this, theme.oled ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Apariencia")
            .setSingleChoiceItems(new String[] { "Claro", "Oscuro OLED" }, theme.oled ? 1 : 0,
                (dialog, choice) -> {
                    WalletTheme.save(this, choice == 1);
                    dialog.dismiss();
                    render();
                })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** A brand's card in the wallet: the drawn card (opens the points/QR section) and its menu. */
    private void brandCard(Franchise item) {
        WalletCardStyle style = WalletCardStyle.of(item, theme.oled);
        LinearLayout card = column();
        card.setPadding(dp(14), 0, dp(14), 0);
        add(page, card, 11);

        float density = getResources().getDisplayMetrics().density;
        FoodCardDrawable illustration = style.illustration == null ? null
            : new FoodCardDrawable(style.illustration, theme.oled, density);
        int nameColor = illustration == null ? theme.ink : illustration.ink();
        int hintColor = illustration == null ? (theme.oled ? item.onAccent() : item.accent()) : illustration.hint();

        FrameLayout primary = new FrameLayout(this);
        primary.setMinimumHeight(dp(91));
        if (illustration == null) {
            primary.setBackground(WalletTheme.button(theme.oled ? 0xFF101F2F : 0xFFDDEEFF, dp(20)));
        } else {
            primary.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), illustration,
                new FoodCardDrawable(style.illustration, theme.oled, density)));
            android.graphics.Rect inset = illustration.contentPadding();
            primary.setPadding(inset.left, inset.top, inset.right, inset.bottom);
        }
        primary.setFocusable(true);
        primary.setContentDescription(item.name() + ", " + style.mainAction);
        primary.setOnClickListener(view -> openWalletSection(item, Franchise.Section.POINTS));
        add(card, primary, 0);

        LinearLayout composition = new LinearLayout(this);
        composition.setGravity(Gravity.CENTER_VERTICAL);
        composition.setPadding(dp(15), dp(3), dp(8), dp(3));
        primary.addView(composition, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        LinearLayout words = column();
        words.setPadding(0, dp(3), dp(4), dp(3));
        TextView name = label(item.name(), 19, nameColor);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(words, name, 0);
        TextView hint = label(style.mainAction + "  →", 12, hintColor);
        hint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(words, hint, 1);
        if (item instanceof Amici) {
            amiciCard = primary;
            amiciLevelLabel = label("", 12, hintColor);
            add(words, amiciLevelLabel, 3);
            updateAmiciLevel();
        }
        words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        composition.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        if (illustration == null) {
            int artworkWidth = Math.min(139, (int) (getResources().getConfiguration().screenWidthDp * .38f));
            composition.addView(new ClubByTableArtwork(this), new LinearLayout.LayoutParams(dp(artworkWidth), dp(85)));
        } else {
            // The right side is left free for the decorations drawn on the card itself.
            composition.addView(new View(this), new LinearLayout.LayoutParams(dp(96), dp(1)));
        }

        LinearLayout menu = new LinearLayout(this);
        for (WalletCardStyle.MenuButton button : style.menu) {
            rowButton(menu, button.label, style.control, theme.ink, () -> openMenuTarget(item, button.target));
        }
        add(card, menu, 5);
    }

    private void openMenuTarget(Franchise item, WalletCardStyle.Target target) {
        if (target == WalletCardStyle.Target.CLUB_READER) {
            startActivity(new Intent(this, ClubByActivity.class)
                .putExtra(ClubByActivity.SECTION, ClubByActivity.Page.READER.name()));
        } else {
            openWalletSection(item, Franchise.Section.valueOf(target.name()));
        }
    }

    private void openWalletSection(Franchise item, Franchise.Section section) {
        if (item instanceof ClubBy) {
            startActivity(new Intent(this, ClubByActivity.class).putExtra(ClubByActivity.SECTION, section.name()));
        } else if (item instanceof Amici) {
            startActivity(new Intent(this, AmiciActivity.class).putExtra(AmiciActivity.SECTION, section.name()));
        } else if (item instanceof TacoBell) {
            startActivity(new Intent(this, TacoBellActivity.class)
                .putExtra(TacoBellActivity.SECTION, section.name()));
        } else if (item instanceof BurgerKing || item instanceof Popeyes || item instanceof Kfc) {
            startActivity(new Intent(this, FranchisePortalActivity.class)
                .putExtra(FranchisePortalActivity.BRAND, item.id())
                .putExtra(FranchisePortalActivity.SECTION, section.name()));
        } else if (section == Franchise.Section.POINTS) {
            startActivity(new Intent(this, ExchangeActivity.class)
                .putExtra(ExchangeActivity.OPEN_RECEIVER, true));
        } else {
            select(item, Screen.valueOf(section.name()));
        }
    }

    private void select(Franchise item, Screen destination) {
        if (franchise != item) pointsCode = null;
        franchise = item;
        navigate(destination);
    }

    private void tab(LinearLayout tabs, String title, Screen destination) {
        boolean selected = screen == destination;
        TextView button = rowButton(tabs, title, selected ? theme.ink : theme.secondary,
            selected ? WHITE : theme.ink, () -> navigate(destination));
        button.setSelected(selected);
    }

    private void renderPoints() {
        if (pointsCode == null) pointsCode = DemoWallet.newCode();
        LinearLayout card = panel(page, theme.surface, 14);
        heading(card, "QR para sumar puntos", 23, theme.ink);
        addCode(card, pointsCode);
        action(card, "Generar otro QR", theme.secondary, theme.ink, () -> {
            pointsCode = DemoWallet.newCode();
            render();
        });
    }

    private void rewardCard(Franchise.Benefit item) {
        LinearLayout card = panel(page, theme.surface, 12);
        heading(card, item.title, 22, theme.ink);
        add(card, label(item.points + " puntos", 15, theme.muted), 4);
        String code = wallet.redeemedCode(franchise, item);
        if (code != null) {
            action(card, "Ver código de prueba", theme.secondary, theme.ink, () -> showCode(item.title, code));
        } else if (wallet.balance(franchise) >= item.points) {
            action(card, "Canjear en demostración", theme.secondary, theme.ink, () -> confirmReward(item));
        } else {
            add(card, label("Te faltan " + (item.points - wallet.balance(franchise)) + " puntos", 15, theme.muted), 10);
        }
    }

    private void confirmReward(Franchise.Benefit item) {
        new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage("Se descontarán " + item.points + " puntos de prueba.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Canjear", (dialog, which) -> {
                String code = wallet.redeem(franchise, item);
                render();
                if (code != null) showCode(item.title, code);
            }).show();
    }

    private void dealCard(Franchise.Benefit item) {
        LinearLayout card = panel(page, theme.surface, 12);
        heading(card, item.title, 22, theme.ink);
        add(card, label(item.description, 15, theme.muted), 4);
        action(card, "Activar oferta de prueba", theme.secondary, theme.ink,
            () -> showCode(item.title, DemoWallet.newCode()));
    }

    private void showCode(String title, String code) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(6), dp(20), dp(12));
        add(content, label("Código de demostración", 15, theme.muted), 0);
        addCode(content, code);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setPositiveButton("Cerrar", null).show();
    }

    private void addCode(LinearLayout container, String code) {
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(QrCode.render(code));
        qr.setContentDescription("QR de demostración");
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(176));
        size.gravity = Gravity.CENTER_HORIZONTAL;
        size.topMargin = dp(10);
        container.addView(qr, size);

        TextView text = label(code, 19, theme.ink);
        text.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        add(container, text, 6);
        action(container, "Copiar código", theme.secondary, theme.ink, () -> {
            Ui.copy(this, "Código de demostración", code);
            Ui.toast(this, "Código copiado");
        });
    }

    private void officialAction(String title, Franchise.Section section) {
        action(page, title, franchise.accent(), WHITE, () -> openOfficial(section));
        if (section == Franchise.Section.POINTS && franchise instanceof McDonalds) {
            action(page, "Intercambio con el emisor", theme.secondary, theme.ink,
                () -> startActivity(new Intent(this, ExchangeActivity.class)));
        }
        String hint = franchise.officialHint(section);
        if (hint != null) add(page, label(hint, 14, theme.muted), 10);
        add(page, label("Demostración · Sin validez en restaurante", 14, theme.muted), 18);
    }

    private void openOfficial(Franchise.Section section) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(franchise.officialUri(section)))
                .setPackage(franchise.officialPackage())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (ActivityNotFoundException unavailable) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(franchise.officialWebsite())));
            } catch (ActivityNotFoundException noBrowser) {
                new AlertDialog.Builder(this).setMessage("Instala la app oficial o un navegador.")
                    .setPositiveButton("Cerrar", null).show();
            }
        }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout panel(LinearLayout parent, int color, int margin) {
        LinearLayout panel = column();
        panel.setBackground(background(color, 25));
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        add(parent, panel, margin);
        return panel;
    }

    private void heading(LinearLayout parent, String title, int size, int color) {
        TextView heading = label(title, size, color);
        heading.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        add(parent, heading, 8);
    }

    private TextView label(String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private TextView button(String title, int bg, int fg, Runnable click) {
        android.widget.Button button = new android.widget.Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(fg);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinHeight(dp(48));
        button.setElevation(0);
        button.setStateListAnimator(null);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33000000), background(bg, 16), null));
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private void action(LinearLayout parent, String title, int bg, int fg, Runnable click) {
        add(parent, button(title, bg, fg, click), 10);
    }

    private TextView rowButton(LinearLayout parent, String title, int bg, int fg, Runnable click) {
        TextView button = button(title, bg, fg, click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        if (parent.getChildCount() > 0) params.setMarginStart(dp(8));
        parent.addView(button, params);
        return button;
    }

    private void add(LinearLayout parent, View view, int marginTop) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginTop);
        parent.addView(view, params);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
