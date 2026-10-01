package fr.lkdm.homelink.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import fr.lkdm.homelink.dashboard.client.widget.HomeLayout;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HomeLayoutTest {
    private static DashboardWidget widget(HomeLayout.Size size) {
        return HomeLayout.resized(HomeLayout.create(DashboardWidget.Type.DEVICE_SUMMARY, UUID.randomUUID(), ""), size);
    }

    @Test void packsLeftToRightThenWrapsToTheNextRow() {
        var packed = HomeLayout.pack(List.of(widget(HomeLayout.Size.HALF), widget(HomeLayout.Size.QUARTER),
                widget(HomeLayout.Size.QUARTER), widget(HomeLayout.Size.FULL)));
        assertEquals(List.of(0, 6, 9, 0), packed.stream().map(DashboardWidget::x).toList());
        assertEquals(List.of(0, 0, 0, 3), packed.stream().map(DashboardWidget::y).toList());
    }

    @Test void refusesLayoutsTallerThanTheGrid() {
        List<DashboardWidget> widgets = new ArrayList<>();
        for (int i = 0; i <= DashboardWidget.MAX_ROWS / HomeLayout.Size.FULL.height; i++) widgets.add(widget(HomeLayout.Size.FULL));
        assertNull(HomeLayout.pack(widgets));
    }

    @Test void orderedReadsTopToBottomThenLeftToRight() {
        var packed = HomeLayout.pack(List.of(widget(HomeLayout.Size.HALF), widget(HomeLayout.Size.HALF), widget(HomeLayout.Size.FULL)));
        var reversed = new ArrayList<>(packed);
        java.util.Collections.reverse(reversed);
        assertEquals(packed, HomeLayout.ordered(reversed));
    }

    @Test void sizesCycle() {
        assertEquals(HomeLayout.Size.HALF, HomeLayout.Size.QUARTER.next());
        assertEquals(HomeLayout.Size.QUARTER, HomeLayout.Size.FULL.next());
    }
}
