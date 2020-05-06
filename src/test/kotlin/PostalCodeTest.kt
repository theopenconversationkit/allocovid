package allocovid

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PostalCodeTest {

    @Test
    fun parsePostalCode_case_empty() {
        assertThat(PostalCode.parse(null)).isNull()
        assertThat(PostalCode.parse(" ")).isNull()
        assertThat(PostalCode.parse("0")).isNull()

    }

    @Test
    fun parsePostalCode_simple() {
        assertThat(PostalCode.parse("75  011")?.value).isEqualTo("75011")
        assertThat(PostalCode.parse("75011")?.value).isEqualTo("75011")
        assertThat(PostalCode.parse("75 011")?.value).isEqualTo("75011")
        assertThat(PostalCode.parse("63630")?.value).isEqualTo("63630")
        assertThat(PostalCode.parse("63,629")?.value).isEqualTo("63629")
        assertThat(PostalCode.parse("03 000")?.value).isEqualTo("03000")
        assertThat(PostalCode.parse("100")?.value).isEqualTo("100")
        assertThat(PostalCode.parse("876220")?.value).isEqualTo("87622")
        assertThat(PostalCode.parse("87 2637")?.value).isEqualTo("87263")
        assertThat(PostalCode.parse("87 26 54")?.value).isEqualTo("87265")
    }

    @Test
    fun parsePostalCode_improve_1() {
        assertThat(PostalCode.parse("75 01")?.value).isEqualTo("75001")
        assertThat(PostalCode.parse("75 03")?.value).isEqualTo("75003")
    }

    @Test
    fun parsePostalCode_improve_2() {
        assertThat(PostalCode.parse("75000 11")?.value).isEqualTo("75011")
        assertThat(PostalCode.parse("75000 13")?.value).isEqualTo("75013")
        assertThat(PostalCode.parse("75000 8")?.value).isEqualTo("75008")
        assertThat(PostalCode.parse("78000 100")?.value).isEqualTo("78100")
    }

    @Test
    fun parsePostalCode_improve_3() {
        assertThat(PostalCode.parse("75 1000")?.value).isEqualTo("75000")
    }

    @Test
    fun parsePostalCode_improve_4() {
        assertThat(PostalCode.parse("75 1000 11")?.value).isEqualTo("75011")
        assertThat(PostalCode.parse("75 1000 1")?.value).isEqualTo("75001")
        assertThat(PostalCode.parse("78 700 11")?.value).isEqualTo("78711")
        assertThat(PostalCode.parse("78 800 2")?.value).isEqualTo("78802")
        assertThat(PostalCode.parse("6 100 27")?.value).isEqualTo("06127")
        assertThat(PostalCode.parse("75000 0 1")?.value).isEqualTo("75001")
        assertThat(PostalCode.parse("20 1000 102")?.value).isEqualTo("20102")

    }

    @Test
    fun parsePostalCode_improve_5() {
        assertThat(PostalCode.parse("75 1020")?.value).isEqualTo("75020")
    }
}