/*
 * Copyright (C) 2020 e-voyageurs technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package allocovid

import ai.tock.nlp.entity.StringValue
import java.math.BigDecimal
import java.math.RoundingMode

private val replaceDoubleSpacesRegexp = "\\s+".toRegex()

fun extractAge(ageString: String?): BigDecimal? {
    return ageString
        ?.replace(",", " ")
        ?.replace("virgule", " ")
        ?.replace("et demi", " 5")
        ?.filter { it.isDigit() || it.isWhitespace() }?.trim()?.takeUnless { it.isBlank() }
        ?.replace(replaceDoubleSpacesRegexp, " ")
        ?.run {
            val s = split(" ")
            if (s.size == 1) {
                toBigDecimal()
            } else {
                s[0].toBigDecimal()
            }
        }
        ?.takeIf { it.toDouble() > 0 && it.toDouble() < 150 }
}

fun extractGender(value: StringValue?, genderText: String?): Boolean? {
    if (value != null) {
        return value.value == "woman"
    }
    val text = genderText?.toLowerCase() ?: ""
    return if (text.contains("femme") || text.contains("fille")) {
        true
    } else if (text.contains("bonhomme") || text.contains("homme")) false
    else null
}

fun extractWeight(weightString: String?): BigDecimal? {
    return weightString
        ?.replace(",", " ")
        ?.replace("virgule", " ")
        ?.replace("et demi", " 5")
        ?.replace("kilo", " ")
        ?.replace("kg", " ")
        ?.filter { it.isDigit() || it.isWhitespace() }?.trim()?.takeUnless { it.isBlank() }
        ?.replace(replaceDoubleSpacesRegexp, " ")
        ?.run {
            val s = split(" ")
            if (s.size == 1) {
                toBigDecimal()
            } else {
                s[0].toBigDecimal()
            }
        }
        ?.takeIf { it.toDouble() > 0 && it.toDouble() < 300 }
}

fun extractHeight(heightString: String?): BigDecimal? {
    return heightString
        ?.replace(",", " ")
        ?.replace("virgule", " ")
        ?.replace("un mètre", "1 ")
        ?.replace("en mettre", "1 ")
        ?.replace("à mettre", "1 ")
        ?.replace("à mettre", "1 ")
        ?.replace("deux mètres", "2 ")
        ?.replace("de mettre", "2 ")
        ?.replace("mettre", "1 ")
        ?.replace("metres", " ")
        ?.replace("metre", " ")
        ?.replace("mètres", " ")
        ?.replace("mètre", " ")
        ?.filter { it.isDigit() || it.isWhitespace() }?.trim()?.takeUnless { it.isBlank() }
        ?.replace(replaceDoubleSpacesRegexp, " ")
        ?.run {
            val s = split(" ")
            if (s.size == 1) {
                toBigDecimal()
            } else {
                s[0].toBigDecimal() + s[1].let {
                    if (it.length == 1) it.substring(0, 1).toBigDecimal().divide(BigDecimal("10"))
                    else it.substring(0, 2).toBigDecimal().divide(BigDecimal("100"))
                }
            }
        }
        ?.run { if (toDouble() > 99 && toDouble() < 300) divide(BigDecimal("100")) else this }
        ?.takeIf { it.toDouble() > 0.5 && it.toDouble() < 3 }
        ?.run { setScale(2, RoundingMode.CEILING) }
}

fun extractTemperature(temperatureString: String?): BigDecimal? {
    return temperatureString
        ?.replace(",", " ")
        ?.replace("virgule", " ")
        ?.replace("et demi", " 5")
        ?.replace(" de$".toRegex(), " 2")
        ?.filter { it.isDigit() || it.isWhitespace() }?.trim()?.takeUnless { it.isBlank() }
        ?.replace(replaceDoubleSpacesRegexp, " ")
        ?.run {
            val s = split(" ")
            if (s.size == 1) {
                toBigDecimal()
            } else {
                s[0].toBigDecimal() + s[1].substring(0, 1).toBigDecimal().divide(BigDecimal("10"))
            }
        }
        ?.takeIf { it.toDouble() > 30 && it.toDouble() < 50 }
}

data class PostalCode(val value: String) {
    companion object {
        fun parse(input: String?): PostalCode? {
            val parts =
                input?.replace(",", " ")?.trim()
                    ?.split(" ")
                    ?.map { it.filter { char -> char.isDigit() } }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            return when (parts.size) {
                1 -> parts[0].filter { it.isDigit() }
                2 -> parsePostalCodeTwoParts(parts[0], parts[1])
                3 -> parsePostalCodeThreeParts(parts[0], parts[1], parts[2])
                else -> parts.joinToString("")
            }.verifyFiveDigit()
        }

        private fun parsePostalCodeThreeParts(part1: String, part2: String, part3: String): String {
            val dep =
                when {
                    part2.endsWith("1000") -> part1.removeSuffix("000").padStart(2, '0')
                    part2.endsWith("00") -> part1.removeSuffix("00").padStart(2, '0') + part2.substring(0, 1)
                    part2 == "0" -> part1.removeSuffix("0")
                    else -> part1 + part2
                }

            return dep + (5 - part3.length - dep.length).let { if (it < 1) "" else "0".repeat(it) } + part3
        }

        private fun parsePostalCodeTwoParts(part1: String, part2: String): String {
            val dep = (if (part1.endsWith("000")) part1.removeSuffix("000") else part1).padStart(2, '0')
            val compl = if (part2.toIntOrNull() in 1000..1999) part2.substring(1) else part2.padStart(3, '0')
            return dep + compl
        }

        private fun String.verifyFiveDigit(): PostalCode? =
            this.run { if (length > 5) substring(0, 5) else this }
                .run {
                    if (all { it.isDigit() } && length >= 2) PostalCode(this) else null
                }
    }

    fun toValue(): Value = Value(value.toDouble())
}