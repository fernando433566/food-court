package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * Registers a ticket in Amici through «Añadir visita → Añadir datos manualmente»: types the
 * ticket number and total, picks the visit date in Android's date picker, presses «Registrar
 * visita» and returns Amici's result sheet (title and description) or error.
 */
final class AmiciTicketTask extends AppTask {
    private static final Amici FRANCHISE = new Amici();
    private final AmiciTicket ticket;
    private boolean sent, openedHome;
    private int pickerMoves, backPresses;

    AmiciTicketTask(AmiciTicket ticket) {
        this.ticket = ticket;
    }

    @Override String packageName() { return FRANCHISE.officialPackage(); }

    @Override Intent launchIntent() { return view(FRANCHISE.officialUri(Franchise.Section.POINTS), packageName()); }

    @Override long stepIntervalMs() { return 450; }

    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = collect(root);
        AccessibilityNodeInfo picker = byId(nodes, "android:id/datePicker");
        if (picker != null) return pickDate(nodes, picker);
        if (byId(nodes, "resultImage") != null) {
            String title = text(byId(nodes, "titleText"));
            String description = text(byId(nodes, "descriptionText"));
            click(byId(nodes, "actionButton"));
            String message = (title + (description.isEmpty() ? "" : ". " + description)).trim();
            return message.isEmpty() ? "Amici respondió sin detalles. Revisa la app en el emisor." : message;
        }
        AccessibilityNodeInfo alert = byId(nodes, "android:id/message");
        if (alert != null && sent) {
            String message = text(alert);
            click(byId(nodes, "android:id/button1"));
            return message.isEmpty() ? "Amici mostró un error al registrar el ticket." : message;
        }
        AccessibilityNodeInfo number = editTextIn(byId(nodes, "ticketNumberEditText"));
        if (number != null) {
            if (!sent) fillForm(nodes, number);
            return null;
        }
        if (click(byId(nodes, "enterTicketButton")) || click(byId(nodes, "addVisitButton"))) return null;
        if (byId(nodes, "design_bottom_sheet") != null && backPresses < 3) {
            backPresses++; // A promotional sheet covers the home screen.
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            return null;
        }
        if (!openedHome) openedHome = click(byId(nodes, "home_nav_graph"));
        return null;
    }

    /** One field per step, so each change settles before the next: number, date, total, then send. */
    private void fillForm(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo number) {
        if (!ticket.number.equals(text(number))) {
            setText(number, ticket.number);
            return;
        }
        AccessibilityNodeInfo date = editTextIn(byId(nodes, "visitDate"));
        if (date != null && !ticket.displayDate().equals(text(date))) {
            click(date); // Opens the date picker handled in pickDate().
            return;
        }
        AccessibilityNodeInfo amount = editTextIn(byId(nodes, "ticketValueEditText"));
        if (amount != null && !ticket.amount.equals(text(amount))) {
            setText(amount, ticket.amount);
            return;
        }
        AccessibilityNodeInfo register = byId(nodes, "registerTicketButton");
        if (register != null && register.isEnabled() && click(register)) sent = true;
    }

    private String pickDate(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo picker) {
        AccessibilityNodeInfo day = byLabel(collect(picker), ticket.pickerLabel());
        if (day != null) {
            click(day);
            click(byId(nodes, "android:id/button1"));
        } else if (pickerMoves < 3) {
            pickerMoves++;
            click(byId(nodes, ticket.isBeforeShownMonth(picker) ? "android:id/prev" : "android:id/next"));
        } else {
            click(byId(nodes, "android:id/button2"));
            return "Amici no permite elegir el " + ticket.displayDate() + " como fecha de visita.";
        }
        return null;
    }

    @Override String timeout() {
        return sent ? "Amici no confirmó el registro. Revisa la app en el emisor."
            : "No se pudo completar el formulario de Amici. Comprueba la sesión en el emisor.";
    }
}
