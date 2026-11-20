package org.launchermc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    @Test
    void microsoftAccountExpires() {
        MinecraftAccount account = MinecraftAccount.microsoft("Steve", "uuid", "token", "xuid",
                System.currentTimeMillis() + 60_000);
        assertTrue(account.microsoft());
        assertFalse(account.expired());
        assertTrue(account.withToken("next", System.currentTimeMillis() - 1).expired());
    }

    @Test
    void accountStoreRoundTrip() {
        AccountStore.clear();
        AccountStore.Saved saved = new AccountStore.Saved("refresh", "client", "Steve",
                "uuid", "xuid");
        try {
            AccountStore.save(saved);
            AccountStore.Saved loaded = AccountStore.load();
            assertNotNull(loaded);
            assertEquals(saved, loaded);
            assertEquals("refresh", loaded.refreshToken());
        } finally {
            AccountStore.clear();
        }
        assertNull(AccountStore.load());
    }
}
