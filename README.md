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

Projekt lze sestavit a spustit z příkazové řádky pomocí přiložených skriptů (vyžaduje JDK 21):

1. **Sestavení**: `./build.sh` (vytvoří adresář `dist` s připravenou aplikací)
2. **Spuštění**: `./run.sh`

## Jak spustit aplikaci (Distribuce)

V adresáři `dist` najdete vše potřebné pro běh aplikace:

#### Windows
- Spusťte soubor `run.bat` dvojitým kliknutím.

#### Linux
- Spusťte příkaz `./run.sh` v adresáři `dist`.

## CI/CD (Gitea Actions)

### Remote Repository Setup
This project uses Gitea for version control and CI/CD. The remote is configured as:
- `gitea` (Gitea server): `ssh://git@192.168.88.97:222/kadu/stock_accounting`

To push changes and trigger releases:
1. Commit your changes: `git add . && git commit -m "your message"`
2. Push to Gitea: `git push gitea modernization-java21`
3. Create a release tag: `./create-release-tag.sh` (creates vYYYY.MM.DD format)
4. Push the tag: `git push gitea --tags`

The Gitea Actions workflow will automatically build and release the application.

Projekt používá Gitea Actions pro automatické sestavení. Při vytvoření tagu (např. `v1.0.0`) se automaticky vytvoří Release se ZIP archivem připraveným k použití.
