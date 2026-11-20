package org.launchermc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEvaluatorTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void objectsWithoutRulesAreAllowed() {
        assertTrue(RuleEvaluator.isAllowed(json("{\"name\":\"lib\"}")));
        assertTrue(RuleEvaluator.isAllowed(null));
    }

    @Test
    void allowRuleForCurrentOs() {
        String os = RuleEvaluator.currentOsName();
        assertTrue(RuleEvaluator.isAllowed(json("""
                {"rules":[{"action":"allow","os":{"name":"%s"}}]}
                """.formatted(os))));
    }

    @Test
    void denyRuleForCurrentOs() {
        String os = RuleEvaluator.currentOsName();
        assertFalse(RuleEvaluator.isAllowed(json("""
                {"rules":[{"action":"disallow","os":{"name":"%s"}}]}
                """.formatted(os))));
    }

    @Test
    void unknownOsIsNotAllowed() {
        assertFalse(RuleEvaluator.isAllowed(json("""
                {"rules":[{"action":"allow","os":{"name":"plan9"}}]}
                """)));
    }

    @Test
    void featuresAreDisabled() {
        assertFalse(RuleEvaluator.isAllowed(json("""
                {"rules":[{"action":"allow","features":{"is_demo_user":true}}]}
                """)));
    }
}
