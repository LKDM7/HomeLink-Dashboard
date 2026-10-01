package fr.lkdm.homelink.dashboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.lkdm.homelink.dashboard.network.NetworkNames;
import org.junit.jupiter.api.Test;

class NetworkNamesTest {
    @Test void acceptsOrdinaryNames() {
        assertTrue(NetworkNames.isValid("Maison"));
        assertTrue(NetworkNames.isValid("Base nord — atelier 2"));
        assertTrue(NetworkNames.isValid("x".repeat(128)));
    }

    @Test void rejectsEmptyOversizedAndFormattingNames() {
        assertFalse(NetworkNames.isValid(null));
        assertFalse(NetworkNames.isValid("   "));
        assertFalse(NetworkNames.isValid("x".repeat(129)));
        assertFalse(NetworkNames.isValid("§cRouge"), "section sign would inject chat formatting");
        assertFalse(NetworkNames.isValid("ligne\nsuivante"));
        assertFalse(NetworkNames.isValid("invisible​join"), "format characters hide text");
    }
}
