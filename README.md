# Akciové účetnictví (StockAccounting)

## Popis projektu
Komplexní aplikace pro vedení akciového účetnictví s podporou více brokerů a pokročilými funkcemi výpočtu daní a kurzů měn.

## Autoři a poděkování
Většinu kódu a logiky napsal **Michal Kara** (lemming@ucw.cz).
Od roku 2020 jsou menší opravy a vylepšení udržovány zde.

Vychází z původního zdrojového kódu šířeného pod GPL na http://lemming.ucw.cz/ucetnictvi/

## Systémové požadavky
- **Java 17 nebo vyšší** - aplikace automaticky zkontroluje verzi při spuštění

## Instalace

### Java (pokud potřeba)
**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

**Fedora/CentOS:**
```bash
sudo dnf install java-17-openjdk
```

**Windows/macOS:**
Stáhněte z: https://adoptium.net/temurin/releases/

## Sestavení a spuštění

### Z příkazové řádky
```bash
# Sestavení (doporučeno)
./gradlew clean installDist

# Spuštění
./run.sh
```

Poznámka: `./build.sh` je zachováno kvůli kompatibilitě a interně volá `./gradlew`.

### S NetBeans
Project Properties → Libraries → Compile classpath:
- libjar/DatePicker.jar
- libjar/swing-layout-1.0.4.jar
- libjar/jcalendar-1.4.jar
- libjar/ib-twsapi-1042.01.jar
- libjar/protobuf-java-4.33.4.jar

## Funkce aplikace

### Import obchodů
- **Interactive Brokers**: TradeLog, FlexQuery CSV
- **Trading 212 Invest**: CSV export s konverzí GBX→GBP
- **Revolut**: CSV export
- **FIO**: CSV export obchodů
- **BrokerJet**: HTML export (legacy)

**Podrobná dokumentace formátů a poznámek**: [IMPORT_DATA.md](IMPORT_DATA.md)

### Export výsledků
- **CSV export** s oddělením měny do samostatného sloupce
- **FIO formát** pro import na kacka.baldsoft.com
- **HTML export** s barevným zvýrazněním zisků/ztrát

### Správa kurzů měn
- **Denní kurzy**: Přesné historické kurzy z ČNB s automatickým načítáním
- **Sjednocené kurzy**: Jednotný kurz podle pokynů MFČR pro daňové účely
- **Hierarchická správa**: Stromová struktura Měna → Roky s počtem kurzů
- **CSV export/import**: Externí správa kurzů v tabulkových editorech

### Externí API
Podrobná dokumentace všech používaných API (Trading212, ČNB) viz [API.md](API.md).

### Výpočty daní
- **Komplexní přehledy**: Zisky, ztráty, dividendy s daňovou optimalizací
- **Frakční akcie**: Plná podpora částečných akcií
- **Multi-broker**: Konsolidace obchodů z různých brokerů
- **Přesné kurzy**: Výběr mezi denním a sjednoceným kurzem

### Pokročilé funkce
- **Vyhledávání**: Podle poznámky, data, symbolu s regexp podporou
- **Filtry**: Pokročilé filtrování obchodů podle kritérií
- **Zálohování**: Automatické zálohování kurzů před hromadnými operacemi
- **Undo funkce**: Vrácení posledního smazání kurzů
- **IBKR TWS API**: Načtení pozic z lokálního TWS a porovnání se „Stav účtu“ (sloupec TWS + barevné zvýraznění)

## Historie změn
Viz [CHANGES.md](CHANGES.md) pro detailní historii všech změn od roku 2021.

## Přispívání
Detailní informace pro vývojáře viz [CONTRIBUTING.md](CONTRIBUTING.md).

---

**Původní dokumentace**: http://lemming.ucw.cz/ucetnictvi/
**Repozitář**: https://github.com/kaaduu/StockAccounting
