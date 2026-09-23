package com.dcorp.skyprint.template

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionEvaluatorTest {
    private fun scope(vararg pairs: Pair<String, Any?>) = Scope.root(TemplateValue.of(mapOf(*pairs)))

    @Test
    fun `path don thuan truthy`() {
        assertTrue(ConditionEvaluator.evaluate(scope("discount" to 5000), "discount"))
        assertFalse(ConditionEvaluator.evaluate(scope("discount" to 0), "discount"))
    }

    @Test
    fun `phu dinh voi dau cham than`() {
        assertTrue(ConditionEvaluator.evaluate(scope("x" to 0), "!x"))
        assertFalse(ConditionEvaluator.evaluate(scope("x" to 1), "!x"))
    }

    @Test
    fun `so sanh so`() {
        assertTrue(ConditionEvaluator.evaluate(scope("discount" to 5000), "discount > 0"))
        assertFalse(ConditionEvaluator.evaluate(scope("discount" to 0), "discount > 0"))
        assertTrue(ConditionEvaluator.evaluate(scope("qty" to 3), "qty >= 3"))
    }

    @Test
    fun `so sanh chuoi bang`() {
        assertTrue(ConditionEvaluator.evaluate(scope("state" to "OPEN"), "state == 'OPEN'"))
        assertFalse(ConditionEvaluator.evaluate(scope("state" to "OPEN"), "state == 'CLOSED'"))
    }

    @Test
    fun `and va or trai sang phai`() {
        assertTrue(ConditionEvaluator.evaluate(scope("a" to 1, "b" to 0), "a == 1 && b == 0"))
        assertFalse(ConditionEvaluator.evaluate(scope("a" to 1, "b" to 1), "a == 1 && b == 0"))
        assertTrue(ConditionEvaluator.evaluate(scope("a" to 0, "b" to 1), "a == 1 || b == 1"))
    }
}
