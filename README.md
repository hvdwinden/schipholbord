# Schipholbord ✈️

Vertrek- en aankomstbord van Schiphol in de stijl van de oude klapborden (split-flap).

- Vertrek en aankomst, met gate/bagageband en status
- Zoeken op route: stad (`londen`), luchthaven (`LHR`) of vlucht (`KL1001`)
- Werkt direct met demovluchten; echte vluchten via de officiële
  [Schiphol Public Flight API](https://developer.schiphol.nl) (gratis sleutel, in te vullen via ⚙️)

Online: https://hvdwinden.github.io/schipholbord/

Niet verbonden aan Royal Schiphol Group.

## Android-app

`android/` bevat een kleine app die het bord in een WebView toont. Aanvragen naar de Schiphol-API
gaan via de app zelf, zodat browserbeveiliging (CORS) ze niet tegenhoudt.

```bash
cd android && ./gradlew assembleDebug
```
