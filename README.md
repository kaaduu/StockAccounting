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

### Synchronizace s Google Drive (Cloud Sync)

#### Nastavení Google Drive API
1. Vytvořte projekt v [Google Cloud Console](https://console.cloud.google.com/)
2. Povolte **Google Drive API** v projektu
3. Vytvořte OAuth 2.0 klientské ID pro "Desktop application"
4. Stáhněte `client_secrets.json` a uložte do složky `resources/` projektu (tento soubor není verzován v Gitu z bezpečnostních důvodů)

#### Používání synchronizace
1. Otevřete aplikaci a přejděte na **Nastavení** → **Cloud Sync**
2. Zaškrtněte "Povolit synchronizaci s Google Drive"
3. Klikněte na **Připojit** a autorizujte aplikaci přes Google OAuth 2.0
4. Zadejte heslo pro šifrování (zapamatujte si ho, bez něj nelze data obnovit!)
5. Klikněte na **Zálohovat do cloudu** pro vytvoření zálohy

#### Automatická synchronizace
V nastavení Cloud Sync můžete povolit:
- **Při spuštění aplikace** - automatická kontrola novější zálohy při startu
- **Při uložení dat** - automatická záloha po uložení souboru
- **Při importu obchodů** - automatická záloha po importu dat

#### Bezpečnost
- Všechna data jsou šifrována pomocí **AES-256-CBC** před odesláním do cloudu
- Klíč je odvozen pomocí **PBKDF2** (100 000 iterací)
- Integrita dat je ověřována pomocí **HMAC-SHA256**
- Heslo pro šifrování není nikde uloženo, uživatel si ho musí zapamatovat
- API klíče Trading 212 a IBKR jsou také šifrovány v zálohách

⚠️ **Důležité**: Pokud zapomenete heslo pro šifrování, **nelze data obnovit**! Toto je bezpečnostní opatření.

#### Synchronizace na jiném PC
1. Nainstalujte aplikaci na novém PC
2. Nastavte Google Drive API (viz výše)
3. Připojte se k Google Drive v nastavení
4. Použijte stejné heslo pro šifrování jako při záloze
5. Klikněte na **Obnovit z cloudu** pro načtení nastavení
6. Aplikace se automaticky restartuje s novými nastaveními

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

## Nástroje

### ibkr-report - CLI prohlížeč IBKR CSV
Interaktivní nástroj příkazové řádky pro prohlížení IBKR (Interactive Brokers) CSV souborů s pokročilým TUI rozhraním.

**Funkce:**
- **Seznam sekcí**: Zobrazení všech dostupných sekcí v CSV souboru
- **Filtrování**: Podpora filtrování záznamů podle libovolného sloupce
- **TUI mód**: Interaktivní terminálové rozhraní s:
  - Prohlížením dat s vertikálním zarovnáním sloupců
  - Editací viditelných sloupců (přepínání checkboxů)
  - Automatickým ukládáním preferencí sloupců
  - Paginací pro dlouhé seznamy (PgUp/PgDn)

**Použití:**
```bash
cd ibkr-report
make build
./bin/ibkr-report list soubor.csv
./bin/ibkr-report show soubor.csv FIFO --columns Symbol,TotalFifoPnl
./bin/ibkr-report tui soubor.csv
```

**Klávesové zkratky v TUI:**
- `↑/↓` - Navigace
- `Space` - Přepnutí sloupce (v módu editace)
- `PgUp/PgDn` - Stránkování
- `Enter/Esc` - Návrat
- `h` - Přepnutí mezi daty a editací sloupců

Více informací viz [ibkr-report/README.md](ibkr-report/README.md).

---

**Původní dokumentace**: http://lemming.ucw.cz/ucetnictvi/
**Repozitář**: https://github.com/kaaduu/StockAccounting
