---
schema_version: 1
app_name: KucLab Clock
package_id: dev.kuclab.clock
version_name: "1.5"
version_code: 6
last_updated: 2026-10-06
license: MIT
category: Nástroje
short_description: >-
  Chytrý nativní budík s kontrolou probuzení, časovačem, stopkami a widgetem — bez reklam a sledování.
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

  Každý budík má vlastní hlasitost, délku pozvolného zesílení, ranní atmosféru
  a zprávu. Příští opakované zvonění lze jednorázově přeskočit bez vypnutí
  dalších dnů. Volitelná kontrola probuzení se po několika minutách zeptá,
  jestli jste opravdu vzhůru, a při neodpovědi budík znovu spustí.

  Tmavé rozhraní používá ploché neutrální povrchy, jedinou teplou akcentní
  barvu a jasnou informační hierarchii. Pokročilé volby budíku jsou dostupné
  až po rozbalení příslušné sekce, takže základní nastavení zůstává rychlé.
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
  - version: "1.5"
    date: 2026-10-06
    notes:
      - Kompletně přepracované UX bez dekorativního glass efektu a zbytečných čipů
      - Kompaktní navigace, ploché povrchy a jednotná teplá akcentní barva
      - Nový progresivní editor budíku s českým vstupem času a rozbalovacími sekcemi
      - Přehlednější seznam budíků bez zkracování důležitých informací
      - Nové obrazovky hodin, stopek, časovače, nastavení a zvonění
  - version: "1.4"
    date: 2026-10-06
    notes:
      - Nový tmavý glass design celé aplikace a čtyři volitelné atmosféry obrazovky zvonění
      - Samostatná hlasitost a pozvolné zesílení pro každý budík
      - Jednorázové přeskočení příštího opakovaného zvonění
      - Kontrola probuzení po vypnutí; při neodpovědi se budík znovu spustí
      - Vlastní ranní zpráva pro každý budík
  - version: "1.3"
    date: 2026-08-10
    notes:
      - Budík se nedá zabít vysunutím appky z naposledy použitých - služba přežije a vrátí obrazovku zpět
      - Widget se teď přizpůsobuje velikosti, na kterou ho na ploše zvětšíte/zmenšíte
      - "Stabilní podpisový klíč: tahle a všechny další verze se budou instalovat jako update, ne jako smazání a nová instalace (jednorázově je potřeba tuhle verzi nainstalovat přes odinstalování staré)"
  - version: "1.2"
    date: 2026-08-10
    notes:
      - Zvonící budík jde nyní opustit prakticky nemožné - hlídač ho vrátí zpět i po Recents/přepnutí appky, ne jen po Home
      - Widget se u běžícího časovače/stopek/budíku aktualizuje spolehlivě, ne jen jednou za hodinu
      - Widget je klikatelný - otevře appku rovnou na obrazovce dané události
      - Nastavení: stav oprávnění se po návratu ze systémových nastavení aktualizuje sám
      - Nastavení: tlačítko pro ruční kontrolu dostupné aktualizace appky
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

Každý budík má vlastní hlasitost, náběh zvuku, atmosféru a ranní zprávu.
Opakovaný budík lze jednorázově přeskočit a kontrola probuzení jej při
neodpovědi znovu spustí.

Tmavé rozhraní používá ploché neutrální povrchy, teplý akcent a čitelnou
typografii. Pokročilé volby budíku jsou seskupené do rozbalovacích sekcí.

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
