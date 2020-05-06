# AlloCovid (fr)

- Source code de l'implémentation de la partie conversationnelle de [AlloCovid](https://www.allocovid.com/)

- Specification suivie: https://github.com/Delegation-numerique-en-sante/covid19-algorithme-orientation

- Développé avec [Tock](https://doc.tock.ai/)

### Limitations actuelles :

- The phrases qualifiées du modèle de sont pas encore fournies - elles arrivent bientôt
- L'export INSERM n'est pas disponible - Un export standard sera bientôt mis en place (en suivant la spécification: https://github.com/Delegation-numerique-en-sante/covid19-algorithme-orientation-check/blob/master/schema.json)
- Le fichier de surcharge des réponses spécifiques au bot AlloCovid n'est pas disponible à date - ce sont les réponses exactes de la spécification qui sont mises en place dans le code source.

### Description de l'implémentation

- Le [fichier original des questions de la spécification] a été légèrement [modifié] 
pour prendre en compte les besoins de l'implémentation - mais pas trop afin d'être en mesure de suivre rapidement ses évolutions
- Pour chaque étape du dialogue, outre les [intentions communes], des intentions d'approbation ou de dénégation spécifiques à l'étape sont mises en place.
- Des tests de conversations sont disponibles afin de vérifier le format de sortie: [ConversationTest]
- Un [connecteur spécifique] a été développé pour AlloMedia  
- Pour utiliser le bot, merci de consulter [la documentation de Tock](). La classe de départ est [AlloCovid]
- N'hésitez pas à nous contacter via [Github](https://github.com/theopenconversationkit/allocovid/issues) pour toute question !

### Testé avec 3 connecteurs

- AlloMedia (numéro national)
- WhatsApp 
- Web

