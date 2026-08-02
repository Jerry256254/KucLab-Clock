---
schema_version: 1
app_name: KucLab Clock
package_id: dev.kuclab.clock
version_name: "1.1"
version_code: 2
last_updated: 2026-08-02
license: MIT
category: Nástroje
short_description: >-
  Nativní budík, časovač, stopky a hodiny bez reklam a sledování, v designu appky Claude.
full_description: |-
  KucLab Clock je nativní Android budík, časovač, stopky a hodiny — bez reklam,
  bez sledování a bez zbytečných oprávnění. Zdrojový kód je otevřený pod
  licencí MIT.

  Budík nabízí volitelné, vzájemně kombinovatelné "vypínací úkoly" (matematické
  příklady, počet kroků naměřených krokoměrem, zatřesení telefonem) a zvonící
  obrazovku, kterou nejde jen tak opustit tlačítkem zpět ani přepnutím na
  plochu. Časovač po doběhnutí ukáže jen trvalou notifikaci s tlačítky
  "Ukončit" a "+1 min", místo aby zabíral celou obrazovku. Běžící stopky jsou
  vidět i na zamčené obrazovce přes živou notifikaci. Widget na ploše ukazuje
  živě tikající hodiny a řádek s nejbližší relevantní událostí (běžící
  časovač, běžící stopky nebo čas dalšího budíku).

  Vizuální jazyk appky vychází z appky Claude (Anthropic) — teplá krémová/
  uhlová paleta, jílově-oranžová accent barva, klidné pružinové animace a
  haptická odezva. Appka respektuje systémový světlý i tmavý režim.
tags:
  - budík
  - alarm
  - časovač
  - timer
  - stopky
  - stopwatch
  - hodiny
  - clock
  - widget
  - open-source
repository_url: https://github.com/Jerry256254/KucLab-Clock
download_url: https://github.com/Jerry256254/KucLab-Clock/releases/latest
logo: store/logo.png
screenshots:
  - store/screenshots/01_hodiny.png
  - store/screenshots/02_budik.png
  - store/screenshots/03_stopky.png
  - store/screenshots/04_casovac.png
changelog:
  - version: "1.1"
    date: 2026-08-02
    notes:
      - Zvonící budík jde mnohem hůř opustit (Home/naposledy použité appky ho vrátí zpět)
      - Nové vypínací úkoly - kroky (krokoměr) a zatřesení telefonem
      - Respektuje vypnuté odložení u konkrétního budíku
  - version: "1.0"
    date: 2026-08-02
    notes:
      - Kompletní redesign v duchu appky Claude (světlý/tmavý režim, animace, haptické posuvníky)
      - Časovač už nespouští plnoobrazovkový budík - jen trvalou notifikaci se Stop/+1 min
      - Widget: sekundy tikají přes Chronometer, druhý řádek ukazuje nejbližší událost
      - Nastavení budíku se otevírá rovnou celé, vlastní i systémová vyzvánění
      - Běžící stopky mají živou notifikaci na zamčené obrazovce, opravené kola/tlačítka
---

# KucLab Clock

Nativní Android budík, časovač, stopky a hodiny — bez reklam, bez sledování,
bez zbytečných oprávnění. Otevřený zdrojový kód pod licencí MIT.

## Popis

KucLab Clock je nativní Android budík, časovač, stopky a hodiny — bez reklam,
bez sledování a bez zbytečných oprávnění. Zdrojový kód je otevřený pod
licencí MIT.

Budík nabízí volitelné, vzájemně kombinovatelné "vypínací úkoly" (matematické
příklady, počet kroků naměřených krokoměrem, zatřesení telefonem) a zvonící
obrazovku, kterou nejde jen tak opustit tlačítkem zpět ani přepnutím na
plochu. Časovač po doběhnutí ukáže jen trvalou notifikaci s tlačítky
"Ukončit" a "+1 min", místo aby zabíral celou obrazovku. Běžící stopky jsou
vidět i na zamčené obrazovce přes živou notifikaci. Widget na ploše ukazuje
živě tikající hodiny a řádek s nejbližší relevantní událostí (běžící
časovač, běžící stopky nebo čas dalšího budíku).

Vizuální jazyk appky vychází z appky Claude (Anthropic) — teplá krémová/
uhlová paleta, jílově-oranžová accent barva, klidné pružinové animace a
haptická odezva. Appka respektuje systémový světlý i tmavý režim.

## Logo

![KucLab Clock logo](store/logo.png)

## Screenshoty

| Hodiny | Budík | Stopky | Časovač |
|---|---|---|---|
| ![Hodiny](store/screenshots/01_hodiny.png) | ![Budík](store/screenshots/02_budik.png) | ![Stopky](store/screenshots/03_stopky.png) | ![Časovač](store/screenshots/04_casovac.png) |

## Odkazy

- Zdrojový kód: <https://github.com/Jerry256254/KucLab-Clock>
- Stažení nejnovějšího APK: <https://github.com/Jerry256254/KucLab-Clock/releases/latest>
- Licence: [MIT](LICENSE)
