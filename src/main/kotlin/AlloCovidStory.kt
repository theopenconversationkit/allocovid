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

import ai.tock.bot.connector.ConnectorMessage
import ai.tock.bot.connector.web.webConnectorType
import ai.tock.bot.connector.web.webMessage
import ai.tock.bot.connector.web.webQuickReply
import ai.tock.bot.connector.whatsapp.whatsAppConnectorType
import ai.tock.bot.definition.Intent
import ai.tock.bot.definition.IntentAware
import ai.tock.bot.definition.story
import ai.tock.bot.engine.BotBus
import ai.tock.bot.engine.dialog.NextUserActionState
import ai.tock.bot.engine.feature.FeatureType
import ai.tock.bot.engine.message.Suggestion
import ai.tock.bot.engine.message.TextWithSuggestions
import ai.tock.nlp.entity.StringValue
import ai.tock.shared.error
import ai.tock.translator.raw
import allocovid.AlloCovidFeature.CORONAVIRUS_DEBUG_MODE
import allocovid.PrimaryIntent.detect_coronavirus
import allocovid.SecondaryIntent.ask_age
import allocovid.SecondaryIntent.ask_gender
import allocovid.SecondaryIntent.ask_height
import allocovid.SecondaryIntent.ask_postal_code
import allocovid.SecondaryIntent.ask_temperature
import allocovid.SecondaryIntent.ask_weight
import allocovid.SecondaryIntent.cancel
import allocovid.SecondaryIntent.do_not_known
import allocovid.SecondaryIntent.goodbye
import allocovid.SecondaryIntent.repeat
import allocovid.SecondaryIntent.reset
import mu.KotlinLogging
import kotlin.math.pow
import kotlin.math.round

val detectCoronavirus = story(
    detect_coronavirus.name,
    secondaryIntents = setOf(cancel, goodbye, repeat, reset) + questions.questions.flatMap { it.intents }
) {
    var removeData = false
    try {
        val state = getData()
            //reset handling
            ?.takeUnless { isIntent(reset) }
            ?: State(null)

        //cancel or goodbye handling
        if (state.question != null && (isIntent(cancel) || isIntent(goodbye))) {
            withAlloMedia(AlloMediaMessage(true))
            end("À bientôt !")
            removeData = true
            return@story
        }

        //repeat handling
        if (isIntent(repeat) && state.question != null) {
            sendAnswer(state.question, state.score)
            configureAllowedIntents(state.question)
            return@story
        }

        val oldQuestion = state.question
        val (newScore: Score?, newQuestion: Question) = if (oldQuestion == null) {
            null to questions.start
        } else {
            handleQuestion(state, oldQuestion)
        }
        if (newQuestion.node.startsWith("fin")) {
            val s = state.score.addScore(newScore)
            logger.debug { "End score: $s" }
            val conclusion = calculateConclusion(s)
            removeData = true
            withAlloMedia(AlloMediaMessage(true))
            end {
                conclusion.cleanMessage
            }
        } else {
            configureAllowedIntents(newQuestion)
            val new = state.score.addScore(newScore)
            setData(state.copy(question = newQuestion, score = new))

            sendAnswer(newQuestion, new)
        }
    } catch (e: Exception) {
        logger.error(e)
        end("Désolé, je n'ai pas compris. Pouvez-vous répéter s'il vous plaît ?")
    } finally {
        if (removeData) {
            removeData()
        }
    }
}

private fun BotBus.handleQuestion(state: State, question: Question): Pair<Score?, Question> {
    val answer = when (question.specialIntent) {
        ask_temperature -> handleTemperature(question)
        ask_age -> handleAge()
        ask_weight -> handleWeight()
        ask_height -> handleHeight(state.score, question)
        ask_postal_code -> handlePostalCode()
        ask_gender -> handleGender(question)
        else -> handleDefault(question)
    }
    return if (answer.notUnderstoodLabel != null) {
        end(answer.notUnderstoodLabel)
        null to question
    } else {
        answer.newScore to findQuestion(answer.newNode ?: error("null none for $answer"))
    }
}

private data class Answer(val newScore: Score?, val newNode: String?, val notUnderstoodLabel: String? = null) {
    constructor(notUnderstoodLabel: String) : this(null, null, notUnderstoodLabel)
}

private fun findQuestion(node: String): Question = questions.questions.first { it.node == node }
private fun questionResult(choice: Choice): Answer = Answer(choice.score, choice.goto)

private fun BotBus.handleDefault(oldQuestion: Question): Answer {
    return oldQuestion.match(this)
        ?.let { c ->
            Answer(
                c.score,
                c.goto
            )
        }
        ?: Answer("Désolé, je n’ai pas compris votre réponse. Répondez par oui ou par non")
}

private fun BotBus.handleTemperature(oldQuestion: Question): Answer {
    val temperatureString =
        entityText(temperatureEntity)?.takeIf { e -> hasActionEntity(temperatureEntity) && e.any { it.isDigit() } }
            ?: userText
    val temperature = extractTemperature(temperatureString)
    return if (temperature == null) {
        if (isIntent(do_not_known)) {
            questionResult(oldQuestion.choices[4])
        } else {
            Answer("Je n'ai pas bien compris votre température. Pouvez-vous l'indiquer à nouveau ?")
        }
    } else {
        val t = temperature.toDouble()
        questionResult(oldQuestion.match(this)
            ?: oldQuestion.choices[
                when {
                    t <= 35.5 -> 0
                    t <= 37.7 -> 1
                    t <= 38.9 -> 2
                    else -> 3
                }
            ]).run {
            copy(
                newScore = newScore?.addScore(Score(temperature = Value(temperature.toDouble())))
            )
        }
    }
}

private fun BotBus.handleAge(): Answer {
    val ageString = entityText(ageEntity)?.takeIf { e -> hasActionEntity(ageEntity) && e.any { it.isDigit() } }
        ?: userText
    val age = extractAge(ageString)
    return if (age == null) {
        Answer("Je n'ai pas bien compris votre âge. Pouvez-vous l'indiquer à nouveau ?")
    } else {
        Answer(
            Score(age = Value(age.toDouble()), facteurs_pronostique = Value(1.0).takeIf { age.toDouble() >= 70.0 }),
            if (age.toDouble() < 15.0) "fin" else "1.7"
        )
    }
}

private fun BotBus.handleWeight(): Answer {
    val weightString = entityText(weightEntity)?.takeIf { hasActionEntity(weightEntity) && it.any { it.isDigit() } }
        ?: userText
    val weight = extractWeight(weightString)
    return if (weight == null) {
        Answer("Je n'ai pas bien compris votre poids. Pouvez-vous l'indiquer à nouveau ?")
    } else {
        Answer(
            Score(poids = Value(weight.toDouble())),
            "2.3"
        )
    }
}

private fun BotBus.handleGender(oldQuestion: Question): Answer {
    val women = extractGender(
        entityValue<StringValue>(genderEntity)?.takeIf { hasActionEntity(genderEntity) },
        entityText(genderEntity)?.takeIf { hasActionEntity(genderEntity) }
            ?: userText
    )
    return if (women == null) {
        Answer("Désolé, je n’ai pas compris votre réponse. Dites-moi, par exemple : \"je suis une femme\" ou : \"je suis un homme\".")
    } else {
        Answer(
            Score(homme = Value(if (women) 2.0 else 1.0)),
            oldQuestion.choices[if (women) 1 else 0].goto
        )
    }
}

private fun BotBus.handlePostalCode(): Answer {
    val postalCodeString = entityText(postalCodeEntity)?.takeIf { e -> hasActionEntity(postalCodeEntity) && e.any { it.isDigit() } }
        ?: userText
    val postalCode = PostalCode.parse(postalCodeString)
    return if (postalCode == null) {
        changeContextValue("postal_code_error", (contextValue<Int>("postal_code_error") ?: 0) + 1)
        if (contextValue<Int>("postal_code_error") ?: 0 < 3) {
            Answer("Je n'ai pas bien compris votre code postal. Pouvez-vous l'indiquer à nouveau ?")
        } else {
            Answer(null, "fin")
        }
    } else {
        Answer(Score(codePostal = postalCode.toValue()), "fin")
    }
}

private fun BotBus.handleHeight(oldScore: Score?, oldQuestion: Question): Answer {
    val heightString = entityText(heightEntity)?.takeIf { hasActionEntity(heightEntity) && it.any { it.isDigit() } }
        ?: userText
    val height = extractHeight(heightString)
    return if (height == null) {
        oldQuestion.match(this)
            ?.let { c ->
                val imc = round(10 * ((oldScore?.poids?.value ?: 1.0) /
                    (c.score?.taille?.value ?: 1.0).pow(2.0))) / 10.0
                Answer(
                    c.score?.addScore(Score(imc = Value(imc)))?.run {
                        if (imc >= 30.0) {
                            addScore(Score(facteurs_pronostique = Value(1.0)))
                        } else {
                            this
                        }
                    },
                    c.goto
                )
            } ?: Answer("Je n'ai pas bien compris votre taille. Pouvez-vous l'indiquer à nouveau ?")
    } else {
        val h = height.toDouble()
        oldQuestion.choices[
            when {
                h <= 1.5 -> 0
                h <= 1.6 -> 1
                h <= 1.7 -> 2
                h <= 1.8 -> 3
                else -> 4
            }
        ].run {
            val imc = round(10 * ((oldScore?.poids?.value ?: 1.0) / height.toDouble().pow(2.0))) / 10.0
            Answer(
                Score(taille = Value(height.toDouble()), imc = Value(imc)).run {
                    if (imc >= 30.0) {
                        addScore(Score(facteurs_pronostique = Value(1.0)))
                    } else {
                        this
                    }
                },
                "2.13"
            )
        }
    }
}


private fun BotBus.configureAllowedIntents(newQuestion: Question) {
    nextUserActionState = NextUserActionState(
        mapOf(
            cancel to -0.6,
            goodbye to -0.6,
            reset to -0.6,
            repeat to -0.5
        ) + newQuestion.intents.map { it to 0.0 }
    )
}

enum class PrimaryIntent : IntentAware {
    detect_coronavirus;

    private val intent = Intent(name)

    override fun wrappedIntent(): Intent = intent
}

enum class SecondaryIntent : IntentAware {
    yes,
    no,
    cancel,
    goodbye,
    repeat,
    reset,
    do_not_known,
    ask_age,
    ask_temperature,
    ask_weight,
    ask_height,
    ask_postal_code,
    ask_gender;

    private val intent = Intent(name)

    override fun wrappedIntent(): Intent = intent
}

data class State(
    val questionNode: String?,
    val score: Score = Score()
) {
    constructor(question: Question?, score: Score) : this(question?.node, score)

    fun copy(question: Question, score: Score) = copy(question.node, score)

    @Transient
    val question: Question? = questionNode?.let { findQuestion(it) }
}

private fun BotBus.setData(state: State) {
    logger.debug { "new state: $state" }
    changeContextValue("covid", state)
}

private fun BotBus.removeData() {
    changeContextValue("covid", null)
}

private fun BotBus.getData(): State? = contextValue("covid")

private fun BotBus.sendAnswer(newQuestion: Question, score: Score) {
    val text = newQuestion.cleanedText
    val translatedSequence = translate(text)
    end {
        if (supportTextWithSuggestions) {
            listOfNotNull(
                if (isFeatureEnabled(CORONAVIRUS_DEBUG_MODE)) {
                    score.toString().raw
                } else {
                    null
                },

                buildDefault(
                    TextWithSuggestions(
                        translatedSequence,
                        newQuestion.choices.map { Suggestion(it.answer) }
                    )
                )
            )
        } else {
            translatedSequence
        }
    }
}

private val BotBus.supportTextWithSuggestions: Boolean
    get() = targetConnectorType != alloMediaConnectorType && targetConnectorType != whatsAppConnectorType

private fun BotBus.buildDefault(message: TextWithSuggestions): ConnectorMessage =
    when (targetConnectorType) {
        webConnectorType -> webMessage(
            message.text,
            message.suggestions.map { webQuickReply(it.title) }
        )
        else -> error("not yet supported")
    }

private val temperatureEntity by lazy { alloCovidBot.entity("temperature") }
private val ageEntity by lazy { alloCovidBot.entity("age") }
private val weightEntity by lazy { alloCovidBot.entity("weight") }
private val genderEntity by lazy { alloCovidBot.entity("gender") }
private val postalCodeEntity by lazy { alloCovidBot.entity("postal_code") }
private val heightEntity by lazy { alloCovidBot.entity("height") }

private val logger = KotlinLogging.logger {}

private enum class AlloCovidFeature : FeatureType {
    CORONAVIRUS_DEBUG_MODE
}