Akciove Ucetnictvi - vetsinu kodu a logiky napsal Michal Kara (lemming@ucw.cz) a od 2020 jsou mensinove opravy a vylepseni udrzovany zde.
   vychazi z puvodniho zdrojoveho kodu siren pod GPL na http://lemming.ucw.cz/ucetnictvi/ (zde je i detailni popis pouziti aplikace)
   
Changes:   
16.10.2021  - Kontrola import souboru pro IB TradeLog, FlexQuery
            - Oprava menu/novy ted spravne inicializuje i helpery, ktere zmizeli (jako datum apod)
            - Oprava vyhledavani podle poznamky - hleda jako regexp tedy vyraz kdekoliv uvnitr poznamky

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

To compile: download NetBeans and set java platform JDK 1.8 (on linux Zulu is good choice https://www.azul.com/downloads/zulu-community/?package=jdk)

Project Properties -> Libraries and here tab Compile set classpath:
libjar/DatePicker.jar
libjar/swing-layout-1.0.4.jar
libjar/jcalendar-1.3.2.jar
