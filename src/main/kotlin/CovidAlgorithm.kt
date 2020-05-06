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

import ai.tock.bot.definition.Intent
import ai.tock.bot.definition.IntentAware
import ai.tock.bot.engine.BotBus
import ai.tock.shared.jackson.mapper
import ai.tock.shared.resourceAsString
import allocovid.SecondaryIntent.ask_age
import allocovid.SecondaryIntent.ask_gender
import allocovid.SecondaryIntent.ask_height
import allocovid.SecondaryIntent.ask_postal_code
import allocovid.SecondaryIntent.ask_temperature
import allocovid.SecondaryIntent.ask_weight
import allocovid.SecondaryIntent.do_not_known
import allocovid.SecondaryIntent.no
import allocovid.SecondaryIntent.yes
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

//Decision tree implementation https://github.com/Delegation-numerique-en-sante/covid19-algorithme-orientation/blob/master/pseudo-code.org#arbre-de-d%C3%A9cision
fun calculateConclusion(s: Score): Conclusion {
    with(conclusions.conclusions) {
        if (s.age.score < 15.0) return FIN1
        if (s.facteurs_gravite_majeurs.boolean) return FIN5
        if (s.fievre.boolean && s.toux.boolean) {
            return if (!s.facteurs_pronostique.boolean) FIN6
            else if (s.facteurs_gravite_mineurs.score < 2) FIN6
            else FIN4
        }
        if (s.fievre.boolean || (s.diarrhees.boolean || (s.toux.boolean && s.douleurs.boolean) || (s.toux.boolean && s.anosmie.boolean) || (s.douleurs.boolean && s.anosmie.boolean))) {
            return if (!s.facteurs_pronostique.boolean) {
                if (!s.facteurs_gravite_mineurs.boolean) {
                    if (s.age.score < 50) FIN2 else FIN3
                } else {
                    FIN3
                }
            } else {
                if (s.facteurs_gravite_mineurs.score < 2) FIN3 else FIN4
            }
        }
        if (s.toux.boolean != s.douleurs.boolean != s.anosmie.boolean) {
            return if (!s.facteurs_pronostique.boolean) FIN2 else FIN7
        }

        return FIN8
    }
}

//json mapping
val questions: Questions = mapper.readValue(resourceAsString("/questions.json").replace("\"Saisie utilisateur\"", "[]"))
val conclusions: Conclusions = mapper.readValue(resourceAsString("/conclusions.json"))

data class Questions(val questions: List<Question>) {
    val start: Question = questions.first()
}

data class Question(
    val node: String,
    val text: String,
    val name: String?,
    val choices: List<Choice> = emptyList()
) {
    val specialIntent: IntentAware? by lazy { specialNodes[node] }
    val intents: List<IntentAware> by lazy { choices.flatMap { it.intents(name) } + listOfNotNull(specialIntent) }
    val endConversation: Boolean by lazy { intents.isEmpty() }
    val cleanedText: String = text.replace("</br>", "\n").replace("<br/>", "\n")

    fun match(bus: BotBus): Choice? =
        choices.find { bus.userText == it.answer || it.intents(name).any { intent -> bus.isIntent(intent) } }
}

data class Choice(
    val answer: String,
    val score: Score?,
    val goto: String?
) {
    fun intents(nodeName: String?): List<IntentAware> =
        covidLabelIntentMap[answer]?.let {
            listOfNotNull(
                it,
                Intent(it.name + "_" + nodeName).takeUnless { nodeName == null }
            )
        } ?: emptyList()
}

data class Score(
    val fievre: Value? = null,
    @JsonProperty("facteurs-gravite-mineurs")
    val facteurs_gravite_mineurs: Value? = null,
    val toux: Value? = null,
    val anosmie: Value? = null,
    val douleurs: Value? = null,
    val diarrhees: Value? = null,
    @JsonProperty("facteurs-gravite-majeurs")
    val facteurs_gravite_majeurs: Value? = null,
    @JsonProperty("facteurs-pronostique")
    val facteurs_pronostique: Value? = null,
    val taille: Value? = null,
    val age: Value? = null,
    val poids: Value? = null,
    val codePostal: Value? = null,
    val temperature: Value? = null,
    val homme: Value? = null,
    val imc: Value? = null,
    val export: ExportData = ExportData()
) {
    fun addScore(score: Score?): Score =
        if (score == null) this
        else
            copy(
                fievre = fievre.add(score.fievre),
                facteurs_gravite_mineurs = facteurs_gravite_mineurs.add(score.facteurs_gravite_mineurs),
                toux = toux.add(score.toux),
                anosmie = anosmie.add(score.anosmie),
                douleurs = douleurs.add(score.douleurs),
                diarrhees = diarrhees.add(score.diarrhees),
                facteurs_gravite_majeurs = facteurs_gravite_majeurs.add(score.facteurs_gravite_majeurs),
                facteurs_pronostique = facteurs_pronostique.add(score.facteurs_pronostique),
                taille = taille.add(score.taille),
                age = age.add(score.age),
                poids = poids.add(score.poids),
                codePostal = codePostal.add(score.codePostal),
                temperature = temperature.add(score.temperature),
                homme = homme.add(score.homme),
                imc = imc.add(score.imc)
            )
}

fun Value?.add(v: Value?): Value? =
    when {
        this == null && v == null -> null
        this == null && v != null -> v
        this != null && v == null -> this
        this != null && v != null -> copy(value = value + v.value)
        else -> error("better compiler")
    }

private fun ageCategory(age: Value?): String? =
    when {
        age?.value == null -> null
        age.value < 15.0 -> "inf_15"
        age.value < 50.0 -> "from_15_to_49"
        age.value < 70.0 -> "from_50_to_69"
        else -> "sup_70"
    }


val Value?.score: Double get() = this?.value ?: 0.0
val Value?.boolean: Boolean get() = score != 0.0

data class Value(
    val value: Double
) {
    override fun toString(): String = value.toString()
}

data class Conclusions(
    val conclusions: ConclusionsList
)

data class ConclusionsList(
    val FIN1: Conclusion,
    val FIN8: Conclusion,
    val FIN7: Conclusion,
    val FIN3: Conclusion,
    val FIN4: Conclusion,
    val FIN5: Conclusion,
    val FIN6: Conclusion,
    val FIN2: Conclusion
)

data class Conclusion(
    val message: String,
    val notification: String
) {
    val cleanMessage: String = message.replace("<br/>", "\n").replace("[15](tel:15)", "15")

    private val orientation: String by lazy {
        with(conclusions.conclusions) {
            when (this@Conclusion) {
                FIN1 -> "less_15"
                FIN2 -> "home_surveillance"
                FIN3 -> "consultation_surveillance_1"
                FIN4 -> "consultation_surveillance_2"
                FIN5 -> "SAMU"
                FIN6 -> "consultation_surveillance_3"
                FIN7 -> "consultation_surveillance_4"
                else -> "surveillance"
            }
        }
    }
}

//see https://github.com/Delegation-numerique-en-sante/covid19-algorithme-orientation/blob/master/implementation.org#variables-%C3%A0-obligatoirement-sauvegarder-pour-partage
data class ExportData(
    val algo_version: String = "2020-05-05",
    val form_version: String = "2020-05-05",
    val date: OffsetDateTime = OffsetDateTime.now(),
    val lastUpdate: OffsetDateTime = OffsetDateTime.now(),
    val duration: Long = Duration.between(date, lastUpdate).toSeconds(),
    val postal_code: String? = null,
    val orientation: String? = null,
    val age_range: String? = null,
    val imc: BigDecimal? = null,
    val feeding_day: Boolean? = null,
    val breathlessness: Boolean? = null,
    val temperature_cat: Boolean? = null,
    val fever_algo: Boolean? = null,
    val tiredness: Boolean? = null,
    val tiredness_details: Boolean? = null,
    val cough: Boolean? = null,
    val agueusia_anosmia: Boolean? = null,
    val sore_throat_aches: Boolean? = null,
    val diarrhea: Boolean? = null,
    val diabetes: Boolean? = null,
    val cancer: Boolean? = null,
    val breathing_disease: Boolean? = null,
    val kidney_disease: Boolean? = null,
    val liver_disease: Boolean? = null,
    val pregnant: Int? = null,
    val heart_disease: Int? = null,
    val heart_disease_algo: Boolean? = null,
    val immunosuppressant_disease: Int? = null,
    val immunosuppressant_disease_algo: Boolean? = null,
    val immunosuppressant_drug: Int? = null,
    val immunosuppressant_drug_algo: Boolean? = null,
    val id: String = UUID.randomUUID().toString()
)


private val covidLabelIntentMap = mapOf(
    "Oui" to yes,
    "Non" to no,
    "Je ne sais pas" to do_not_known,
    "Non applicable" to do_not_known
)

private val specialNodes = mapOf(
    "1.1.1" to ask_temperature,
    "2.1" to ask_age,
    "2.2" to ask_weight,
    "2.3" to ask_height,
    "2.13" to ask_postal_code,
    "2.14" to ask_gender
)