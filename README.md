# KucLab Clock

Nativní Android hodiny, budík, časovač a stopky — bez reklam, bez sledování,
bez zbytečných oprávnění. Otevřený zdrojový kód pod licencí MIT.

[![Stáhnout nejnovější APK](https://img.shields.io/github/v/release/Jerry256254/KucLab-Clock?label=St%C3%A1hnout%20APK&style=for-the-badge&color=D97757)](https://github.com/Jerry256254/KucLab-Clock/releases/latest)

**➡️ [Stáhnout .apk z poslední verze](https://github.com/Jerry256254/KucLab-Clock/releases/latest)** —
žádná registrace, žádný obchod, jen soubor ke stažení a instalaci (v
telefonu je potřeba povolit instalaci z neznámých zdrojů, protože appka
není z Play Store).

## Funkce

- **Hodiny** — velký ciferník s přesnými sekundami a datem.
- **Budík**
  - libovolný počet budíků, opakování podle dní v týdnu
  - vyzvánění: výběr ze systémových tónů nebo tří vestavěných tónů appky
  - volitelné odložení (nastavitelný počet minut) — lze u konkrétního budíku i
    úplně vypnout
  - volitelné, vzájemně kombinovatelné "vypínací úkoly": matematické
    příklady, počet kroků (krokoměr — donutí vás doopravdy vstát a chodit)
    nebo počet zatřesení telefonem; dokud nejsou splněné všechny zapnuté
    úkoly, budík nejde vypnout
  - zvonící budík nejde opustit tlačítkem zpět, přepnutím na plochu ani přes
    naposledy použité aplikace — pokusí se vrátit zpátky na obrazovku (v
    mezích toho, co Android běžné appce vůbec dovolí — telefon zůstává
    použitelný pro tísňová volání)
  - spolehlivé buzení i při zamčené obrazovce a v úsporném režimu baterie
- **Časovač**
  - nastavení posuvníkem nebo rychlými předvolbami (1/5/10/15/30 min)
  - po doběhnutí nezobrazuje plnoobrazovkový budík — jen trvalou notifikaci s
    tlačítky „Ukončit“ a „+1 min“, kterou nejde omylem smazat
- **Stopky**
  - běžící stopky jsou vidět i na zamčené obrazovce / always-on displeji přes
    živou notifikaci (`Chronometer`, `CATEGORY_STOPWATCH`)
  - kola se ukládají a scrollují odděleně od ovládacích tlačítek
- **Widget na plochu** — hodiny s živě tikajícími sekundami (bez zbytečného
  vybíjení baterie) a řádek s nejbližší relevantní událostí: běžící časovač,
  běžící stopky, nebo čas dalšího budíku.

## Design

Vizuální jazyk appky vychází z Claude (Anthropic) — teplá krémová/uhlová
paleta, jílově-oranžová accent barva, klidné pružinové animace a haptická
odezva na každém posuvníku a přepínači. Appka respektuje systémový světlý i
tmavý režim.

## Sestavení ze zdrojového kódu

Vyžaduje JDK 17 a Android SDK (compileSdk/targetSdk 34, minSdk 26).

```bash
./gradlew assembleDebug
```

Výsledný balíček najdete v `app/build/outputs/apk/debug/app-debug.apk`.

## Technologie

Kotlin, Jetpack Compose (Material 3), žádné externí runtime závislosti mimo
AndroidX — vše běží nativně a offline.

## Licence

[MIT](LICENSE)
