package allocovid

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import java.math.BigDecimal
import kotlin.test.assertEquals

class TemperatureTest {

    @Test
    fun `38 degres 5 should be handled well`() {
        assertEquals(BigDecimal("38.5"), extractTemperature("38 degrés 5"))
    }
}