Akciove Ucetnictvi - vetsinu kodu a logiky napsal Michal Kara (lemming@ucw.cz) a od 2020 jsou mensinove opravy a vylepseni udrzovany zde.
   vychazi z puvodniho zdrojoveho kodu siren pod GPL na http://lemming.ucw.cz/ucetnictvi/ (zde je i detailni popis pouziti aplikace)
   
See [CHANGES.md](CHANGES.md) for the latest modernization updates.

Changes:
02.05.2022    - Pridan import Revolut csv - pro VejbyCZ <br>   
              - Stav uctu ukazu frakcni mnozstvi<br>
<br>

17.10.2021  - Pridana moznost v menu Otevrit - pridat, vhodne pokud si vedete corporate aktivity v extra souboru (a behem roku ruzne importujete a ocistujete trade data)<br>
<br>
16.10.2021  - Kontrola import souboru pro IB TradeLog, FlexQuery<br>
            - Oprava menu/novy ted spravne inicializuje i helpery, ktere zmizeli (jako datum apod)<br>
            - Oprava vyhledavani podle poznamky - hleda jako regexp tedy vyraz kdekoliv uvnitr poznamky<br>
            - <a href="https://github.com/kaaduu/StockAccounting/issues/4">Issue 4</a> uzavren

15.06.2021 - Pridat export do FIO formatu pro ucely importu na http://kacka.baldsoft.com<br>
           - Pridano vyhledavani podle poznamky<br>

10.06.2021 - T212 Import fix - konverze GBX na GBP<br>
                   - vyber souboru - nastaven defaultni pohled - details <br>
                   - Pridan sloupec Poznamka (pri importu vlozi jmeno spolecnosti a pripadne dalsi pomocne informace)<br>
                   - Nastaveni maximalni sirky sloupcu v hlavnim okne<br>
                   - Opravena hodnota amount (pocet akcii) pri importu  na double - podpora frakcnich akcii (castecne jiz umelo)<br>
                   - zmena verzovani na format : 2021 JUN rev 1<br>
31.05.2021 - Pridana podpora importu z Trading 212 Invest<br>
01.01.2021 - Import/ IB Tradelog: oprava ktera neignoruje obchody  ktere maji jen 15 sloupce mimo 16<br>
                   - pridana podpora pro FOP_TRD (future options)<br>
                   - opraveno detekce CASH_TRD jako penize<br>


=======================
# StockAccounting

To compile with NetBeans: download NetBeans and set java platform JDK 1.8 (on linux Zulu is good choice https://www.azul.com/downloads/zulu-community/?package=jdk)

Project Properties -> Libraries and here tab Compile set classpath:
libjar/DatePicker.jar
libjar/swing-layout-1.0.4.jar
libjar/jcalendar-1.3.2.jar

### Sestavení a spuštění

Projekt lze sestavit a spustit z příkazové řádky pomocí přiložených skriptů (vyžaduje JDK 17 nebo vyšší):

1. **Sestavení**: `./build.sh` (vytvoří adresář `dist` s připravenou aplikací)
2. **Spuštění**: `./run.sh`

## Jak spustit aplikaci (Distribuce)

### Požadavky na systém
- **Java 17 nebo vyšší** - aplikace automaticky zkontroluje verzi při spuštění
- Pokud nemáte správnou verzi Java, skript zobrazí instrukce pro instalaci

### Instalace Java (pokud potřeba)
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

## Jak spustit aplikaci (Distribuce)

V adresáři `dist` najdete vše potřebné pro běh aplikace:

#### Windows
- Spusťte soubor `run.bat` dvojitým kliknutím.

#### Linux
- Spusťte příkaz `./run.sh` v adresáři `dist`.

## CI/CD (Gitea Actions)

### Nastavení vzdáleného repozitáře
Tento projekt používá duální vzdálené repozitáře pro správu verzí a CI/CD:
- `gitea` (Primární - Gitea server): `ssh://git@192.168.88.97:222/kadu/stock_accounting`
- `origin` (Sekundární - GitHub): `https://github.com/kaaduu/StockAccounting.git`

Pro odeslání změn a spuštění vydání:
1. Commitněte změny: `git add . && git commit -m "vaše zpráva"`
2. Odešlete do Gitea (primární): `git push gitea modernization-java21`
3. Odešlete do GitHub (volitelné): `git push origin modernization-java21`
4. Vytvořte značku vydání: `./create-release-tag.sh` (vytvoří formát vRRRR.MM.DD)
5. Odešlete značky do obou: `git push gitea --tags && git push origin --tags`

Gitea Actions workflow automaticky sestaví a vydá aplikaci.

## Informace o verzi
Aplikace zobrazuje informace o verzi automaticky na základě git značek.
- **Označená vydání**: Zobrazuje název značky (např. `v2026.01.15` nebo `v2026.01.15-2-g123abc`)
- **Vývojové sestavení**: Zobrazuje `dev-build` pokud git není k dispozici
- **Informace o běhu**: Zobrazuje verzi Java, dodavatele a detaily operačního systému

**Poznámka**: Můžete upravit `create-release-tag.sh` pro odeslání značek do `origin` (GitHub) místo `gitea` změnou příkazu push ve skriptu. Test značka `v2026.01.15-origin-test` byla úspěšně odeslána pouze do GitHub.

Projekt používá Gitea Actions pro automatické sestavení. Při vytvoření tagu (např. `v1.0.0`) se automaticky vytvoří Release se ZIP archivem připraveným k použití.
