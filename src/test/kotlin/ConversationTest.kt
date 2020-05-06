package allocovid

import ai.tock.bot.connector.web.webConnectorType
import ai.tock.bot.connector.whatsapp.whatsAppConnectorType
import ai.tock.bot.definition.Intent
import ai.tock.bot.test.asGenericMessage
import ai.tock.bot.test.toBeSimpleTextMessage
import ai.tock.bot.test.toHaveGlobalText
import ch.tutteli.atrium.verbs.expect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ConversationTest {

    @RegisterExtension
    @JvmField
    internal val tockExtension = AlloCovidTockExtension()

    @Test
    fun `GIVEN AlloMedia connector type WHEN age below 15 THEN conversation is over`() {
        tockExtension.send("Test covid", detectCoronavirus, connectorType = alloMediaConnectorType) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Bonjour! Vous pensez avoir été exposé au Coronavirus COVID-19 et avez des symptômes. Souhaitez vous démarrer le test ?"
            )
        }

        tockExtension.send("Oui", SecondaryIntent.yes) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Quel est votre âge ?"
            )
        }

        tockExtension.send("12", SecondaryIntent.ask_age) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Prenez contact avec votre médecin généraliste au moindre doute.\n" +
                    "Cette application n’est pour l’instant pas adaptée aux personnes de moins de 15 ans.\n" +
                    "En cas d’urgence, appelez le 15."
            )
        }
    }

    @Test
    fun `GIVEN WhatsApp connector type WHEN breath is missing THEN response is as expected`() {
        tockExtension.send("Test covid", detectCoronavirus, connectorType = whatsAppConnectorType) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Bonjour! Vous pensez avoir été exposé au Coronavirus COVID-19 et avez des symptômes. Souhaitez vous démarrer le test ?"
            )
        }

        tockExtension.send("Oui", SecondaryIntent.yes) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Quel est votre âge ?"
            )
        }

        tockExtension.send("65 ans", SecondaryIntent.ask_age) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Êtes-vous dans l'impossibilité de vous alimenter ou boire DEPUIS 24 HEURES OU PLUS ?"
            )
        }
        tockExtension.send("Non", SecondaryIntent.no) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Dans les dernières 24 heures, avez-vous noté un manque de souffle INHABITUEL lorsque vous parlez ou faites un petit effort ?"
            )
        }

        tockExtension.send("Oui je ne respire plus bien", Intent("yes_breathlessness")) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Quel est le code postal du lieu où vous résidez actuellement ?"
            )
        }

        tockExtension.send("20 1000 102", SecondaryIntent.ask_postal_code) {
            expect(firstBusAnswer).toBeSimpleTextMessage(
                "Appelez le 15."
            )
        }
    }

    @Test
    fun `GIVEN web connector type WHEN complete test with no symptoms THEN response is as expected`() {
        tockExtension.send("Test covid", detectCoronavirus, connectorType = webConnectorType) {
            expect(firstBusAnswer).asGenericMessage {
                toHaveGlobalText(
                    "Bonjour! Vous pensez avoir été exposé au Coronavirus COVID-19 et avez des symptômes. Souhaitez vous démarrer le test ?"
                )
            }

            tockExtension.send("Je veux mon neveu !", SecondaryIntent.yes) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Quel est votre âge ?"
                    )
                }
            }

            tockExtension.send("55 ans", SecondaryIntent.ask_age) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Êtes-vous dans l'impossibilité de vous alimenter ou boire DEPUIS 24 HEURES OU PLUS ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Dans les dernières 24 heures, avez-vous noté un manque de souffle INHABITUEL lorsque vous parlez ou faites un petit effort ?"
                    )
                }
            }

            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Pensez-vous avoir eu de la fièvre ces derniers jours (frissons, sueurs) ?"
                    )
                }
            }
            tockExtension.send("Pas de fièvre", Intent("no_fever")) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une toux ou une augmentation de votre toux habituelle ces derniers jours ?"
                    )
                }
            }
            tockExtension.send("Je ne tousse pas", Intent("no_cough")) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous noté une forte diminution de votre goût, ou de votre odorat, ces derniers jours ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous un mal de gorge, ou des douleurs musculaires, ou des courbatures inhabituelles ces derniers jours ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous de la diarrhée ces dernières 24 heures (au moins 3 selles molles) ?"
                    )
                }
            }
            tockExtension.send("Mes selles sont parfaites", Intent("no_diarrhea")) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une fatigue inhabituelle ces derniers jours ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une hypertension artérielle mal équilibrée ? Ou une maladie cardiaque ou vasculaire ? Ou prenez-vous un traitement à visée cardiologique ?"
                    )
                }
            }
            tockExtension.send("Pas de maladie du coeur", Intent("no_heart_disease")) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Êtes-vous diabétique ?"
                    )
                }
            }
            tockExtension.send("Non plus", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous ou avez-vous eu un cancer dans les 3 dernières années ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une maladie respiratoire ? Ou êtes-vous suivi par un pneumologue ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une insuffisance rénale chronique dialysée ?"
                    )
                }
            }
            tockExtension.send("Absolument pas", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une maladie chronique du foie ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Êtes-vous un homme ou une femme ?"
                    )
                }
            }
            tockExtension.send("Une femme", SecondaryIntent.ask_gender) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Êtes-vous enceinte ?"
                    )
                }
            }
            tockExtension.send("Heureusement que non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Avez-vous une maladie connue pour diminuer vos défenses immunitaires ?"
                    )
                }
            }
            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Prenez-vous un traitement immunosuppresseur ?"
                    )
                }
            }

            tockExtension.send("Non", SecondaryIntent.no) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Quel est votre poids en kilogrammes ?"
                    )
                }
            }
            tockExtension.send("70", SecondaryIntent.ask_weight) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Quelle est votre taille en mètres ?"
                    )
                }
            }
            tockExtension.send("1 mètre 70", SecondaryIntent.ask_height) {
                expect(firstBusAnswer).asGenericMessage {
                    toHaveGlobalText(
                        "Quel est le code postal du lieu où vous résidez actuellement ?"
                    )
                }
            }
            tockExtension.send("75 001", SecondaryIntent.ask_postal_code) {
                expect(firstBusAnswer).toBeSimpleTextMessage(
                    "Votre situation ne relève probablement pas du COVID 19. N’hésitez pas à contacter votre médecin en cas de doute. Vous pouvez refaire le test en cas de nouveau symptôme pour réévaluer la situation. Pour toute information concernant le COVID 19, composer le 0 800 130 000."
                )
            }
        }
    }
}