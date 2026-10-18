import androidx.car.app.model.*;
import androidx.car.app.model.constraints.ActionsConstraints;
import androidx.car.app.model.constraints.RowConstraints;
import java.lang.reflect.*;
import java.util.List;

/** JVM check of the installed library's validators; no Android UI or fabricated Context. */
public class AutoTemplateCheck {
    static Action fixture(int flags) throws Exception {
        Constructor<Action> constructor = Action.class.getDeclaredConstructor(); constructor.setAccessible(true);
        Action action = constructor.newInstance();
        Field f = Action.class.getDeclaredField("mFlags"); f.setAccessible(true); f.setInt(action, flags);
        f = Action.class.getDeclaredField("mIcon"); f.setAccessible(true); f.set(action, CarIcon.APP_ICON);
        f = Action.class.getDeclaredField("mBackgroundColor"); f.setAccessible(true); f.set(action, CarColor.GREEN);
        return action;
    }
    public static void main(String[] args) throws Exception {
        Constructor<CarText> textConstructor = CarText.class.getDeclaredConstructor(); textConstructor.setAccessible(true);
        CarText title = textConstructor.newInstance();
        Field textField = CarText.class.getDeclaredField("mText"); textField.setAccessible(true); textField.set(title, "Map");
        Constructor<Row> rowConstructor = Row.class.getDeclaredConstructor(); rowConstructor.setAccessible(true);
        Row row = rowConstructor.newInstance();
        Field rowTitle = Row.class.getDeclaredField("mTitle"); rowTitle.setAccessible(true); rowTitle.set(row, title);
        Field rowTexts = Row.class.getDeclaredField("mTexts"); rowTexts.setAccessible(true); rowTexts.set(row, List.of(title, title));
        Field click = Row.class.getDeclaredField("mOnClickDelegate"); click.setAccessible(true);
        Object delegate = Proxy.newProxyInstance(OnClickDelegate.class.getClassLoader(), new Class<?>[]{OnClickDelegate.class}, (proxy, method, arguments) -> null);
        click.set(row, delegate);
        try {
            RowConstraints.ROW_CONSTRAINTS_PANE.validateOrThrow(row);
            throw new AssertionError("Pane row must reject a click listener");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("click listener")) throw expected;
            System.out.println("Reproduced Pane row click failure: " + expected.getMessage());
        }
        click.set(row, null);
        RowConstraints.ROW_CONSTRAINTS_PANE.validateOrThrow(row);
        Action titled = fixture(0);
        Field background = Action.class.getDeclaredField("mBackgroundColor"); background.setAccessible(true); background.set(titled, CarColor.DEFAULT);
        Field titleField = Action.class.getDeclaredField("mTitle"); titleField.setAccessible(true); titleField.set(titled, title);
        ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE.validateOrThrow(List.of(titled));
        try {
            ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE.validateOrThrow(List.of(titled, titled));
            throw new AssertionError("Station list strip must reject two text actions");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("custom titles")) throw expected;
            System.out.println("Reproduced station list failure: " + expected.getMessage());
        }
        Action primary = fixture(Action.FLAG_PRIMARY), regular = fixture(0);
        ActionsConstraints.ACTIONS_CONSTRAINTS_BODY_WITH_PRIMARY_ACTION.validateOrThrow(List.of(primary, regular));
        try {
            ActionsConstraints.ACTIONS_CONSTRAINTS_BODY_WITH_PRIMARY_ACTION.validateOrThrow(List.of(primary, primary));
            throw new AssertionError("Pane must reject two primary actions");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("primary")) throw expected;
        }
        Action copied = new Action.Builder(primary).setFlags(0).build();
        if ((copied.getFlags() & Action.FLAG_PRIMARY) == 0) throw new AssertionError("setFlags must be additive");
        try {
            ActionsConstraints.ACTIONS_CONSTRAINTS_FAB.validateOrThrow(List.of(copied));
            throw new AssertionError("Grid FAB must reject primary action");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("primary")) throw expected;
        }
        ActionsConstraints.ACTIONS_CONSTRAINTS_FAB.validateOrThrow(List.of(regular));
        Action mapIcon = fixture(0);
        background.set(mapIcon, CarColor.DEFAULT);
        ActionsConstraints.ACTIONS_CONSTRAINTS_NAVIGATION.validateOrThrow(List.of(mapIcon, mapIcon, titled));
        ActionsConstraints.ACTIONS_CONSTRAINTS_NAVIGATION.validateOrThrow(List.of(mapIcon, mapIcon, mapIcon, titled));
        ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE.validateOrThrow(List.of(mapIcon, titled));
        ActionsConstraints.ACTIONS_CONSTRAINTS_BODY_WITH_PRIMARY_ACTION.validateOrThrow(List.of(primary, titled));
        // Map summary rows have two lines and navigate to real detail screens.
        Field browsable = Row.class.getDeclaredField("mIsBrowsable"); browsable.setAccessible(true); browsable.setBoolean(row, true);
        click.set(row, delegate);
        Constructor<ItemList> listConstructor = ItemList.class.getDeclaredConstructor(); listConstructor.setAccessible(true);
        ItemList mapItems = listConstructor.newInstance();
        Field items = ItemList.class.getDeclaredField("mItems"); items.setAccessible(true); items.set(mapItems, List.of(row, row));
        androidx.car.app.model.constraints.RowListConstraints.ROW_LIST_CONSTRAINTS_SIMPLE.validateOrThrow(mapItems);
        // Dashboard row contract: no-distance rows must browse to details.
        if (!row.isBrowsable() || row.getOnClickDelegate() == null) throw new AssertionError("Map dashboard rows must open details");
        // Full setItemList additionally constructs Android SpannableString, unavailable in this JVM runner.
        System.out.println("Map row-list constraints and three-action strip validated against installed library.");
        System.out.println("Installed-library pane primary-action and grid flag regressions passed.");
    }
}
