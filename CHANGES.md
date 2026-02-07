# Changes

# Změny

Všechny významné změny projektu StockAccounting budou zdokumentovány v tomto souboru.

## [Trading 212: sjednocený import (API + CSV) + dividendy/úroky/transformace] - 2026-01-27

### Přidáno
- Import → Trading 212: sjednocený formát pro API i lokální CSV (jeden výběr + možnost „Vybrat soubor...“).
- Import → Trading 212: režim obsahu (Vše / Pouze obchody / Pouze transformace / Pouze dividendy / Pouze úroky).
- Trading 212 CSV: import dividend (BRUTTO + srážková daň), úroků z hotovosti a úroků z půjčování CP (tickery `Kreditni.Urok`, `CP.Urok`).
- Trading 212 CSV: import corporate actions (stock split open/close) jako transformace (TRANS_SUB/TRANS_ADD) včetně normalizace kolizí v rámci stejné minuty.
- Trading 212 CSV: podpora „Dividend manufactured payment“ (importováno jako dividenda; označeno v poznámce).

### Změněno
- Import → Trading 212: formáty „T212 Invest - csv“ jsou dočasně vypnuté ve výběru (viditelné, ale nejdou zvolit) – nahrazuje je sjednocený import.
- Trading 212: poplatky „Currency conversion fee“ se ignorují (nejsou vhodné pro přepočet přes jednotný/denní kurz).
- UI: v hlavním okně jsou přepínače „Metadata“ a „Sekundy“ vždy viditelné (nejsou schované v rozbalených filtrech).

### Opraveno
- Import → Trading 212: časové posuny `TimeShift:+Nm` se nyní aplikují pouze kvůli transformacím (TRANS_SUB/TRANS_ADD), ne kvůli běžným obchodům ve stejné minutě.
- Hlavní okno: tlačítka „Zpět“ a „Zpět import“ už nejsou součástí rozbalovacích filtrů; zobrazují se jen pokud je skutečně možné akci vrátit.
- Filtry: přidáno tlačítko „Reset času“ u rozsahu „Od/Do“ pro návrat na výchozí období (1900-01-01 → nyní).
- Hlavní okno: doplněny tooltipy u chybějících tlačítek, aby bylo jasné, co akce provede.
- Import → Trading 212: tlačítko „Sloučit do databáze“ je nyní dostupné i v případě, že náhled obsahuje pouze položky „K aktualizaci“ (bez nových řádků).
- Import → Trading 212: výběr roku se po „Načíst z API“ ihned aktualizuje; pokud API vrátí 0 záznamů, rok se označí jako „Imported: 0 transakcí“.
- Import → Trading 212: v okně importu je přidáno tlačítko „Sloučit do databáze“ pro aplikování náhledu do hlavní databáze.
- Import: tlačítka „Sloučit do databáze“ se nyní aktivují jen pokud existují nové řádky nebo položky k aktualizaci; u IBKR Flex je sloučení oddělené od tlačítka pro načtení.
- Import: při přepínání formátů se korektně skrývá/zobrazuje celý blok ovládání Trading 212, aby se nepřekrýval s IBKR Flex UI.
- Stav účtu: načítání pozic z TWS už typicky neselhává na první pokus (čeká na handshake a při timeoutu automaticky jednou zopakuje).
- Import → Trading 212: přidáno tlačítko „Detaily...“ pro zobrazení seznamu akcí (Action) v CSV, včetně počtů a seznamu podporovaných akcí.
- Import → Trading 212: dividendy se nyní importují pro všechny akce `Dividend (...)`, ne jen pro konkrétní variantu názvu.
- Import → Trading 212: „Detaily...“ nyní správně rozpoznají všechny varianty `Dividend (...)` jako podporované a oddělují záměrně ignorované akce (Deposit/Withdrawal/konverze) od nepodporovaných.
- Import → IBKR Flex: „Detaily...” nyní fungují i pro legacy CSV (zobrazí alespoň sekce a počty řádků) a pro v2 navíc ukazují přehled typů v TRADES/CTRN/CORP (importováno/disabled/ignorováno) včetně FXTR dividend fallback.

## [IBKR Flex: CTRN „Other Fees” se neimportují] - 2026-02-06

### Změněno
- Import → IBKR Flex: cash typ CTRN „Other Fees” (např. ADR fee a market-data poplatky) se nově ignoruje, aby se nemapoval jako „Dividenda/Daň”. Dividendové a úrokové položky (Dividends / Withholding Tax / Broker Interest) zůstávají importované.

### Opraveno
- Import → IBKR Flex: dividendy v sekci CTRN se už nemohou chybně vyhodnotit jako corporate action (např. kvůli hodnotě `COMMON` v poli SubCategory); CTRN se parsuje před heuristikami.
- Import → IBKR Flex: FXTR dividend fallback nyní funguje i u CSV v2 s řádky typu `HEADER/DATA` (brutto dividendy se doplní ze sekce FXTR, pokud v CTRN chybí) a zároveň se z FXTR vyfiltrují řádky obsahující `TAX`.
- Import → IBKR Flex: v sekci TRNT (Trades) se nově importují jen řádky `LevelOfDetail=EXECUTION`; řádky typu `ORDER`/`SYMBOL_SUMMARY`/`ASSET_SUMMARY` se ignorují.
- Import → Náhled importu: v tabulce náhledu lze nově ručně přepínat pouze sloupec „Ignorovat“ (ostatní pole jsou v náhledu zamknutá).

## [Kurzy měn: automatická detekce roků a měn] - 2026-01-26

### Přidáno
- Nastavení → Kurzy měn: při „Načíst kurzy“ aplikace nově zjistí roky a měny použité v hlavním okně (z „Datum“ i „Datum vypořádání“) a nabídne jejich doplnění do tabulky jednotných kurzů, aby byly zahrnuty do načítání z ČNB.

## [Nápověda: Logy událostí s detaily] - 2026-01-26

### Změněno
- Nápověda → Logy: seznam událostí je nyní přehlednější (úroveň INFO/WARN/ERROR) a pro vybrané chyby umí zobrazit detail (stacktrace).

### Přidáno
- Základní logování klíčových akcí (otevření/uložení souboru, IBKR sloučení a chyby, detekce nových roků/měn při načítání kurzů).
- Logování stahování kurzů: jednotné kurzy z ČNB (start/výsledek/uloženo-odmítnuto) a „Chytré stažení“ denních kurzů (roky/měny + souhrn chyb).

## [Sestavení: přechod na Gradle Wrapper] - 2026-01-26

### Změněno
- Sestavení projektu je nově dostupné přes `./gradlew` (Gradle Wrapper). `build.sh` zůstává kvůli kompatibilitě a interně volá Gradle.
- Spuštění na Windows (`run.bat`) nově podporuje i gradlovský výstup do `dist\lib\`.

## [UI: moderní vzhled (FlatLaf)] - 2026-01-26

### Změněno
- Aplikace používá moderní Swing Look&Feel (FlatLaf) pro čistší a konzistentnější vzhled napříč platformami.

## [IBKR Flex: sjednocení parsování API a souboru] - 2026-01-26

### Opraveno
- Import → IBKR Flex: „Načíst z IBKR“ nyní používá stejnou logiku náhledu jako „Načíst ze souboru“ (včetně detekce CSV v2 sekcí, normalizace kolizí a konzistentního vyhodnocení duplikátů).

## [IBKR Flex: režim obsahu a detaily ACCT] - 2026-01-26

### Opraveno
- Import → IBKR Flex: režimy „Pouze obchody“ a „Pouze transformace“ už neobsahují dividendy (CTRN).

### Přidáno
- Import → IBKR Flex → Detaily: pokud je v CSV v2 přítomná sekce ACCT, zobrazí se základní informace o účtu/vlastníkovi a souhrn konsolidace obchodů podle IBOrderID.

## [IBKR Flex: nápověda pro nastavení Flex Query] - 2026-01-26

### Přidáno
- Import → IBKR Flex: tlačítko „Nápověda“ s doporučeným nastavením Flex Query (sekce, Cash Transactions typy a konfigurace exportu).

## [IBKR Flex: režim „Pouze úroky“] - 2026-01-26

### Přidáno
- Import → IBKR Flex: nový režim „Pouze úroky“ ve výběru obsahu (zobrazí pouze úrokové položky z CTRN).

## [IBKR Flex: import úroků z CTRN] - 2026-01-26

### Přidáno
- Import → IBKR Flex: import typů CTRN „Broker Interest Received/Paid“ a související „Withholding Tax“ jako typ „Úrok“.
- „Broker Interest Paid“ a „Broker Fees“ se zatím importují jako „disabled“ (viditelné, ale ignorované ve výpočtech).

### Změněno
- Import → IBKR Flex (CSV v2): pokud v CTRN chybí brutto dividendy, aplikace je nově doplní ze sekce FXTR (Forex P/L Details) jako dividendy BRUTTO a v importu na to upozorní.

## [Výpočet: shrnutí úroků] - 2026-01-26

### Přidáno
- Výpočet: nová záložka „Úroky“ se souhrnem úroků (hrubá/daň/zaplacený/poplatek) včetně exportu do CSV/HTML.

## [Nastavení: výběr vzhledu aplikace] - 2026-01-26

### Přidáno
- Nastavení → System: volba vzhledu (System/FlatLaf Light/Dark/IntelliJ/Darcula). Změna se projeví po restartu.

## [Nastavení: volba písem] - 2026-01-26

### Přidáno
- Nastavení → System → Vzhled: možnost nastavit písmo aplikace (rodina + velikost) a monospace písmo (pro logy, detaily chyb a importní texty). Změna se projeví po restartu.
- Předvolba „Tahoma 11“ pro rychlé nastavení klasického vzhledu.

### Změněno
- Výchozí vzhled je „System (OS)“, aby odpovídal původnímu chování na větvi master.

## [UI: modernizace vzhledu a použitelnosti] - 2026-01-26

### Změněno
- Import/Compute/About/Main: odstraněny některé pevné velikosti a typografie, okna se lépe přizpůsobují různým obrazovkám.
- Dialogy pro chyby nyní umí zobrazit detaily (stacktrace) a zapisují se do logů.
- Hlavní okno: přidán rychlý filtr (Ticker/Trh/Note) a možnost skrýt/zobrazit pokročilé filtry.
- Výpočet: upozornění na datum vypořádání na konci roku nabízí rychlý filtr v hlavním okně.
- O aplikaci: upraveno rozložení a typografie (čitelnější hierarchie informací).
- Nastavení → System: přeskupeno do sekcí pro rychlejší orientaci.
- Import → IBKR Flex: tlačítka přeskupena do kroků (Zdroj/Náhled/Obsah).
- Nastavení → Kurzy měn: akce sjednoceny do přehlednější skupiny.
- Hlavní tabulka: přidán tooltip popisující stav řádku (Ignorováno/Nově importováno/Aktualizováno).
- Import: při generování náhledu lze nově bezpečně použít Storno (zrušení náhledu).

## [Oprava zobrazení náhledu barvy v nastavení] - 2026-01-24

### Opraveno
- Nastavení/Systém: náhled barvy pro „aktualizované" řádky je nyní viditelný (dříve byl překryt tlačítkem kvůli chybné pozici v mřížce).
- Import (IBKR Flex): při sloučení importu do databáze se nyní zachovává „ID transakce" (TxnID/ActionID) a další metadata i v případě, že nejsou zapsaná v poznámce.
- Import (IBKR Flex): při kolizi časů v rámci jedné minuty se nově automaticky posouvají obchody a další záznamy o +N minut tak, aby transformace (pár Odebrání/Přidání) byly v dané minutě jediné.
- Import (IBKR Flex): posun času už nepřepisuje datum vypořádání (SettleDateTarget) u běžných obchodů.
- Import (IBKR Flex): aktualizace existujících záznamů je nyní vždy zapnutá a tlačítko „Sloučit do databáze" je dostupné i v režimu „pouze aktualizace" (0 nových, jen duplikáty).
- Hlavní okno: tlačítko „Kopírovat" nyní kopíruje vybrané řádky jako CSV včetně hlavičky a všech sloupců (s uvozovkami a escapováním).

## [Podklad pro DP: oprava barvení zisku v HTML new] - 2026-01-25

### Opraveno
- Podklad pro DP: správné vyhodnocení záporných hodnot ve sloupci „Zisk CZK“ při exportu „Uložit HTML new“ i pro formáty s oddělovači tisíců (např. `-2,300.79`, `-73,474.81`).

### Přidáno
- Podklad pro DP: export „Uložit HTML new“ pro CP obsahuje sekce ve stylu Kačky („Souhrn“, „Výstupy podle jednotlivých roků“, „Výstupy podle jednotlivých akcií“) při použití jednotného kurzu.

## [Otevírání souborů: preferovat poslední otevřený] - 2026-01-25

### Opraveno
- Při startu aplikace se při zapnutém „Auto-load“ nově preferuje poslední explicitně otevřený soubor (nastavení „lastOpenedFile“) před „nejnovějším“ souborem v datové složce.
- „Uložit jako“ nyní aktualizuje „lastOpenedFile“, aby se nově uložený soubor stal výchozím pro další spuštění.

## [Podklad pro DP: Kačka-like tabulka podle akcií] - 2026-01-25

### Opraveno
- Podklad pro DP: sekce „Výstupy podle jednotlivých akcií“ v exportu „Uložit HTML new“ pro CP je nyní ve stylu Kačky (BUY/SELL řádky, Počet/Cena/Poplatky/Objem/Zisk/% a běžící „Stav CP“).
- Podklad pro DP: „Stav CP“ v této sekci nově zohledňuje i transformace (split/reverse split/rename) přes řádky TRANS_SUB/TRANS_ADD, které jsou ve výpisu označené jako corporate action.
- Podklad pro DP: oprava exportu pro corporate actions přes více tickerů (např. ATNF→ETHZ, CS→UBS, NEOS→AYTU) – zpracování probíhá globálně (jako ve výpočtu), takže se nespouští chyba „pouze jedna transformace ve stejný čas“.

### Odstraněno
- Nastavení/Systém: odstraněna neimplementovaná volba „Zobrazovat sloupec # (pořadí řádku)" - funkce nebyla nikdy dokončena.
- Menu „Soubor → Import z IBKR Flex..." odstraněno - nahrazeno sjednoceným importem přes „Import od brokera → IBKR Flex".

## [IBKR Flex: podpora CSV v2 (BOF/BOS/EOS)] - 2026-01-25

### Přidáno
- Import (IBKR Flex): podpora novějšího CSV formátu s hlavičkami a trailery (BOF/BOA/BOS/EOS/EOA/EOF) vedle původního „legacy" CSV.
- Import (IBKR Flex): informace o verzi CSV se zobrazuje vedle výběru formátu; u CSV v2 se v tooltipu vypíší obsažené sekce (např. Trades, Corporate Actions).
- Import (IBKR Flex, CSV v2): tooltip se nyní zobrazuje seskupeně podle účtu (BOA) a varování o chybějících povinných sekcích se vyhodnocuje pro každý účet zvlášť.
- Import (IBKR Flex, CSV v2): vedle informace o verzi CSV je tlačítko „Detaily...“, které otevře okno se seznamem sekcí seskupeným podle účtu (podobné dialogu „Nápověda → Logy“).
- Import (IBKR Flex, CSV v2): zpracování sekce `CTRN` (Cash Transactions) jako dividend (Dividends / Payment In Lieu Of Dividends) a srážkové daně/poplatků (Withholding Tax / Other Fees) včetně podpory tickeru `CASH.internal`.
- Import (IBKR Flex, CSV v2): pro dividendy z `CTRN` se ukládá `TransactionID` do sloupce „ID transakce“.
- Import (IBKR Flex): přidán režim „Pouze dividendy“.
- Import (IBKR Flex): režim „Obchody + transformace“ přejmenován na „Vše (obchody + transformace + dividendy)“.
- Nastavení → Kurzy měn: přidán export jednotných kurzů do JSON.
- Hlavní okno: možnost označit řádky jako „Ignorovat“ (šedé) – takové řádky se nepočítají do dividend ani obchodních výpočtů.

### Opraveno
- Import (IBKR Flex): při „Načíst ze souboru" se nyní vždy vymaže předchozí náhled před načtením nového souboru, aby nedocházelo k matoucímu zobrazení starých dat.

### Přidáno
- Import (IBKR Flex): u CSV v2 se při náhledu zobrazí upozornění, pokud chybí povinné sekce `Trades` nebo `Corporate Actions`.

## [Sjednocení výběru formátu importu a výběru souboru] - 2026-01-22

### Změněno
- Odstraněno duplicitní okno „Formát importu“ při volbě „Soubor → Import od brokera“.
- Import se nyní otevírá přímo v okně importu a automaticky předvybere poslední použitý formát.

### Přidáno
- Tlačítko „Vybrat soubor…“ přímo v okně importu pro lokální souborové importy.
- Výchozí filtr souborů podle formátu (např. `.tlg` pro IB TradeLog, `.csv` pro Fio/Revolut/T212, `.htm/.html` pro BrokerJet).
- Nastavení pro volbu výběru souboru: nativní (OS) nebo Java (Swing).
- IB TradeLog: při výběru souboru lze označit více souborů najednou (např. měsíční exporty) a načíst je jako jeden import; přípona není rozhodující, kontroluje se hlavička `ACCOUNT_INFORMATION`.

### Opraveno
- IB TradeLog: zpětně kompatibilní párování duplikátů pro starší data bez sekund a bez `TxnID` (minutové párování s požadavkem na jednoznačnou shodu), aby šlo re-importem doplnit sekundy a metadata.
- IB TradeLog: lepší zpětné doplňování `TxnID` i při více identických obchodech ve stejné minutě (párování podle pořadí `TxnID` a serialu, pokud počty souhlasí).
- IB TradeLog: náhled importu už nezobrazuje „nové“ řádky v případech, kdy je lze jednoznačně spárovat s existujícími řádky bez `TxnID` (budou zařazeny mezi „duplikáty k aktualizaci“).
- IB TradeLog: při importu z více souborů (a nově i při výběru jediného souboru) se interní deduplikace řídí primárně `TxnID`, aby se nesloučily různé obchody se stejným časem.
- IBKR Flex: rozpoznání sloupce `SettleDateTarget` pro „Datum vypořádání“.
- IBKR Flex (corporate actions): ukládání `ActionID` do sloupce „ID transakce“ a doplnění `Broker`/`ID účtu`; poznámka je nyní čistě popisná bez vnořeného `ActionID:`.
- Načítání databáze: automatická oprava duplicitních interních serialů (aby se nerozbíjelo zvýrazňování a aktualizace při importu).
- UI: zvýraznění nových/aktualizovaných řádků se automaticky vyčistí při „Otevřít - nový“, „Otevřít - přidat“ a „Nový“.

### Přidáno
- Podklad pro DP: nové tlačítko „Uložit HTML new“ (pro CP/deriváty/cash) s možností automaticky otevřít uložený soubor v prohlížeči.
- Podklad pro DP: upozornění na případy, kdy je „Datum vypořádání“ shodné s „Datum“ u obchodů na přelomu roku (zejména IB TradeLog).
 - Podklad pro DP: upozornění na případy, kdy je „Datum vypořádání“ shodné s „Datum“ u obchodů 29.–31.12 vybraného roku; upozornění se zobrazuje i ve stavovém řádku.
- Hlavní okno: tlačítko „Zpět import“ pro vrácení posledního importu (vložené řádky i aktualizace duplikátů).
- Hlavní okno: tlačítko „Smazat TxnID“ pro odstranění Broker/ID účtu/ID transakce z vybraných řádků (včetně Note).
- Import: v náhledu je vidět i sekce „K aktualizaci“ (side-by-side porovnání DB vs import) a krátký souhrn Nové/K aktualizaci/Neimportované.
- IBKR Flex (soubor): při zpracování souboru se zobrazuje „busy“ překryv (spinner), aby UI nezamrzlo.
- IBKR Flex: při změně režimu (obchody/transformace) se náhled automaticky obnoví; tlačítko „Obnovit náhled“ se při neaktuálním náhledu zvýrazní.
- Nastavení: volba pro vypnutí automatického zobrazení okna „O aplikaci“ při startu.
- IBKR Flex: po „Sloučit do databáze“ se okno importu automaticky zavře.
- Po importu: aplikace automaticky skočí na první aktualizovaný řádek (nebo první nový řádek) v aktuálním pohledu tabulky.
- IBKR Flex: po „Sloučit do databáze“ se už nezobrazuje modální okno se statistikami; místo toho se zobrazí krátká zpráva ve stavovém řádku.

### Opraveno
- IBKR Flex: ošetřen vzácný případ, kdy více obchodů spadne do stejné minuty (aplikace ukládá čas pouze na minuty) a import je dříve mylně vyhodnotil jako duplicitu. Nově se jeden záznam aktualizuje a další se přidají jako nové řádky deterministicky.
- Při tomto ošetření se zachovává existující `TxnID` v databázi (pokud je již vyplněn) a časový posun se aplikuje na „druhý“ obchod.

### Přidáno
- Sjednocená cache pro importy a API stahování pod `~/.stockaccounting/cache/<broker>/`.
- Automatická migrace starých cache umístění (Trading 212 a IBKR Flex) do sjednocené cache.
- Při importu z lokálních souborů se vybraný soubor zkopíruje do cache pro snadné opětovné použití a ladění.

Poznámka: Pro Interactive Brokers se používá společná složka `~/.stockaccounting/cache/ib/` (TradeLog i Flex).

### Přidáno
- Stav účtu: v nastavení lze upravit parametry připojení k IB TWS (host/port/clientId), timeout a výchozí účet pro načítání pozic.
- Stav účtu: v porovnání pozic z TWS se zkouší běžné varianty tickerů (např. `BRK.B` vs `BRK B`) a porovnání se zachová i po přepočtu.
- Nastavení: přidána samostatná karta „IBKR TWS API“ včetně tlačítka „Otestovat připojení“.
- Oprava: doplněna chybějící knihovna `protobuf-java` pro IB TWS API (jinak padalo načítání pozic). Použita verze `4.33.4`.
- Dokumentace: doplněn popis funkce porovnání pozic z TWS a potřebných závislostí.

### Přidáno
- Čas transakcí nyní podporuje přesnost na sekundy (import, ukládání/načítání, porovnání duplicit). Ukládá se zpětně kompatibilně: sekundy se do souboru zapisují jen pokud jsou nenulové.
- IBKR Flex: časové posuny pro korporátní akce se nyní dělají po sekundách (+N s) místo po minutách.
- IBKR import: interní posun času při řešení kolizí duplicit se nyní dělá po sekundách (+N s).
- UI: editor data v hlavní tabulce již nemaže sekundy (maže se pouze milisekundová část).
- Import: porovnání duplicit nově preferuje shodu podle `TxnID` (pokud je vyplněno), aby se předešlo duplicitám při přechodu ze záznamů bez sekund na záznamy se sekundami.
- Hlavní okno: přidán přepínač „Sekundy“ pro zobrazení/skrývání sekund ve sloupcích data.
- Hlavní okno: přidáno tlačítko „Kopírovat“ pro zkopírování vybraných řádků do schránky jako TSV.
- Hlavní okno: „Smazat řádek“ podporuje multivýběr a je doplněno o „Zpět“ (obnova posledního smazání); při obnově se vždy zruší všechny filtry.
- Nastavení: přidána volba barvy a zapnutí/vypnutí zvýraznění nových a aktualizovaných řádků po importu, včetně náhledu.
- Hlavní okno: přidáno tlačítko „Vyčistit barvy“ pro zrušení zvýraznění.
- IBKR Flex: přidán výběr typů transformací (RS/TC/IC/TO), výchozí je importovat RS+TC a ignorovat IC+TO.
- Import: při shodě podle `TxnID` se při „Aktualizovat duplikáty“ doplní i přesný čas v poli Datum (sekundy), aby historická data bez sekund šla postupně zpřesnit.
- Import: u aktualizovaných řádků se tučně zvýrazní konkrétní změněné sloupce (Datum, Poplatky, Vypořádání, Note) a „Vyčistit barvy“ odstraní jak barvy, tak tučné písmo.
- Ukládání: do interního formátu `.dat` se nově ukládají i metadata `Broker`, `ID účtu`, `ID transakce` a `Code` jako samostatná pole (zpětně kompatibilní; staré soubory se při uložení automaticky doplní).
- Import: pro IB TradeLog, IBKR Flex a Trading 212 se metadata (Broker/ID účtu/ID transakce/Code) nově ukládají přímo do transakce, ne pouze do Poznámky.
- Trading 212: doplněno ukládání `TxnID` i pro starší importéry a API transformaci.
- IBKR Flex: obchody nyní ukládají `Broker/ID účtu/ID transakce/Code` přímo do transakce (nejen do Poznámky).

## [Oprava IBKR Flex Web Service - implementace podle oficiální API dokumentace] - 2026-01-20

### Opraveno - Kritické chyby API integrace
- **ZLOMENÁ FUNKČNOST OPRAVENA**: IBKR import nyní funguje správně podle oficiální API specifikace
- **Neexistující endpoint**: Odstraněno volání `/GetStatus` endpointu, který ve Flex Web Service API neexistuje
  - Nahrazeno správným endpointem `/GetStatement` podle dokumentace
  - Toto způsobovalo 10minutové timeouty s "Report status: RUNNING"
  
- **Neplatné parametry požadavku**: Odstraněny nepodporované parametry `startDate` a `endDate` z `/SendRequest`
  - API akceptuje pouze: `t` (token), `q` (queryID), `v` (version=3)
  - Rozsah dat musí být nakonfigurován v šabloně Flex Query v Client Portal
  
- **Nesprávný workflow**: Implementován správný 2-krokový proces podle IBKR dokumentace
  - **Před** (3 kroky - ŠPATNĚ):
    1. Zavolat `/SendRequest` s daty → ReferenceCode
    2. Pollovat `/GetStatus` v smyčce → čekat na "Finished"
    3. Zavolat `/downloadCsvReport` → získat CSV
  - **Po** (2 kroky - SPRÁVNĚ):
    1. Zavolat `/SendRequest` → ReferenceCode
    2. Počkat 20s a pollovat `/GetStatement` → získat CSV (nebo error 1019 pokud není hotovo)

### Vylepšeno - Zpracování chyb
- **Parsování chybových odpovědí**: Přidáno zpracování `<Status>Fail</Status>` XML odpovědí
  - Extrahuje `ErrorCode` a `ErrorMessage` z API odpovědí
  - Podporuje všech 21 chybových kódů (1001-1021) dokumentovaných v IBKR API
  
- **Specializované výjimky**: Přidána `ReportNotReadyException` pro error 1019
  - Error 1019 ("Statement generation in progress") je očekávaný - způsobí retry
  - Ostatní errory jsou fatální a propagují se jako výjimky
  
- **Rate limit handling**: Detekce error 1018 ("Too many requests")
  - API limity: 1 požadavek/s, 10 požadavků/min na token
  - Automatická detekce a informativní chybové hlášky

### Změněno - Konfigurace
- **Rozsah dat**: Datumy již NELZE zadávat v API voláních
  - **DŮLEŽITÉ**: Rozsah dat musí být nakonfigurován v šabloně Flex Query v Client Portal
  - Podporované rozsahy v šabloně: "Last Year", "Last Month", "Last 7 Days", "Custom Range", atd.
  - Pokud importujete data za konkrétní rok, vytvořte v Client Portal šablonu s příslušným rozsahem
  
- **Polling strategie**: Optimalizováno podle IBKR doporučení
  - Počáteční čekání: 20 sekund (dle příkladu v dokumentaci)
  - Retry interval: 10 sekund
  - Maximum pokusů: 60 (celkem 10 minut)

### Technické detaily - API specifikace
- **Base URL**: `https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService`
- **Endpointy**:
  - `/SendRequest?t={token}&q={queryID}&v=3` → vrací ReferenceCode
  - `/GetStatement?t={token}&q={referenceCode}&v=3` → vrací CSV nebo error
- **Povinné hlavičky**: `User-Agent: StockAccounting/1.0`
- **Timeouty**: Connect 10s, Read 30s, Request 60s
- **Chybové kódy** (kompletní seznam v kódu):
  - 1001: Dočasná chyba generování
  - 1012: Token expiroval
  - 1014: Neplatné Query ID
  - 1015: Neplatný token
  - 1018: Rate limit překročen
  - 1019: Report se stále generuje (retry)
  - 1021: Dočasná chyba retrievalu

### Dokumentace použita
- IBKR Flex Web Service API: https://www.interactivebrokers.com/campus/ibkr-api-page/flex-web-service/
- Oficiální Python příklady a workflow z IBKR dokumentace

### Soubory změněny
- `src/cz/datesoft/stockAccounting/IBKRFlexClient.java`
  - Odstraněn `checkReportStatus()` a `/GetStatus` endpoint
  - Přidán `downloadReport()` s `/GetStatement` endpointem
  - Kompletně přepsán `requestAndDownloadReport()` podle API specifikace
  - Vylepšen `parseReferenceCode()` s plnou podporou error stavů
  - Přidána třída `ReportNotReadyException` pro retry logic
  - Odstraněna třída `FlexReportStatus` (již není potřeba)
  - Rozšířena třída `FlexRequestResult` o `errorCode` a `errorMessage`
- `src/cz/datesoft/stockAccounting/IBKRFlexImporter.java`
  - Odstraněno předávání `startDate` a `endDate` do API
  - Přidány informativní logy o konfiguraci rozsahu dat
- `src/cz/datesoft/stockAccounting/SettingsWindow.java`
  - Aktualizován test připojení bez date parametrů
  - Vylepšeno zobrazení chybových kódů z API

## [Kritické opravy IBKR API importu] - 2026-01-20

### Opraveno - Kritické chyby
- **Nesprávný formát data pro aktuální rok**: Opraven chybný formát data v `IBKRFlexImporter.java:134`, který způsoboval selhání importu dat z aktuálního roku
  - **Před**: `String.format("%02d%02d%04d", měsíc, den, rok)` generoval "01202026" (MMddyyyy)
  - **Po**: `String.format("%04d%02d%02d", rok, měsíc, den)` generuje "20260120" (yyyyMMdd)
  - **Dopad**: IBKR API vyžaduje formát yyyyMMdd - všechny importy aktuálního roku selhávaly s neplatným parametrem
  
- **Thread leak z ExecutorService**: Opraven únik vláken v `IBKRFlexClient.java` - ExecutorService nikdy nebyl vypnutý
  - Přidána robustní metoda `shutdown()` s graceful termination (5s timeout)
  - Přidána metoda `cleanup()` v `IBKRFlexImporter` volaná v finally bloku
  - **Dopad**: Při každém importu zůstávala vlákna běžící na pozadí, což vedlo k únikům paměti

### Vylepšeno - Zvýšená spolehlivost
- **Validace CSV sloupců**: Přidána kontrola, zda byly detekovány všechny požadované sloupce (Date, Symbol, Quantity, Price)
  - Loguje varování pokud chybí povinné sloupce a používají se výchozí indexy
  - Pomáhá odhalit problémy při změně formátu IBKR CSV

- **Rozšířené mapování typů transakcí**: Vylepšeno rozpoznávání typů transakcí z IBKR
  - Přidáno: interest, withholding tax, deposits/withdrawals, options, corporate actions
  - Přidáno logování pro neznámé typy transakcí vyžadující manuální kontrolu
  - **Před**: Pouze základní buy/sell/dividend/fee
  - **Po**: Kompletní pokrytí běžných IBKR transakcí s detailním logováním

### Změněno - Technické vylepšení
- **Zjednodušeno timeout zpracování**: Odstraněn redundantní wrapper okolo HttpClient timeoutů
  - HttpClient již má vestavěné timeouty (connect: 10s, read: 30s, request: 60s)
  - Odstraněno duplicitní ExecutorService wrapping každého HTTP požadavku
  - Lepší error zprávy pro různé typy timeoutů (connect vs read)

- **Nahrazeno debug logování**: Všechny `System.out.println("DEBUG: ...")` nahrazeny standardním loggingem
  - **IBKRFlexClient**: 6 debug příkazů → `logger.info()` / `logger.fine()`
  - **IBKRFlexImporter**: 8 debug příkazů → `logger.info()` / `logger.fine()` / `logger.warning()`
  - **IBKRFlexProgressDialog**: 16 debug příkazů → `logger.info()` / `logger.fine()`
  - Celkem odstraněno 36 debug výpisů z produkčního kódu

### Technické detaily
- **Formát data IBKR API**: API očekává formát `yyyyMMdd` pro parametry `startDate` a `endDate`
- **Resource management**: Všechny importy nyní korektně uvolňují zdroje pomocí try-finally bloků
- **Logging levels**: 
  - `logger.info()` pro důležité události (úspěšný import, chyby)
  - `logger.fine()` pro detailní trasování (pro debugging s -verbose)
  - `logger.warning()` pro chybové stavy a neznámé typy transakcí

### Soubory změněny
- `src/cz/datesoft/stockAccounting/IBKRFlexImporter.java` - oprava formátu data, přidán cleanup
- `src/cz/datesoft/stockAccounting/IBKRFlexClient.java` - zlepšený shutdown, zjednodušené timeouty
- `src/cz/datesoft/stockAccounting/IBKRFlexParser.java` - validace sloupců, rozšířené mapování typů
- `src/cz/datesoft/stockAccounting/IBKRFlexProgressDialog.java` - nahrazeno debug logování

## [Oprava EDT blokování v IBKR Flex Progress Dialog] - 2026-01-19

### Opraveno
- **EDT blokování**: Kritická chyba kde progress dialog se zobrazoval prázdný (bez progress baru a tlačítek) a aplikace se zasekávala. Problém byl v pořadí operací v `waitForCompletion()`:
  1. `setVisible(true)` na non-modal dialog vracel okamžitě (dialog ještě nebyl vykreslen)
  2. `latch.await()` blokoval EDT vlákno
  3. Dialog nikdy nedostal šanci se vykreslit

### Změněno
- **IBKRFlexProgressDialog.java**:
  - Změněno z non-modal (`false`) na modal (`true`) dialog pro správné blokování
  - Přesunuto volání `startImport()` PŘED `setVisible(true)` v volajícím kódu
  - Import se spustí jako background worker, pak se zobrazí modal dialog
  - Dialog se zavře až po dokončení importu (CountDownLatch)
  - Přidáno rozsáhlé debug logování pro sledování flow: constructor, startImport, doInBackground, process, done
  - Změněna tlačítko "Zrušit" na "Zavřít" po dokončení/chybě
  - Přidáno `setResizable(false)` pro konzistenci

- **IBKRFlexImportWindow.java**:
  - Aktualizováno volání `IBKRFlexProgressDialog`: odstraněn `importWorker` parametr
  - Nyní volá `progressDialog.startImport()` PŘED `progressDialog.waitForCompletion()`

### Technické detaily
- **Původní problém**: `setVisible(true)` na non-modal JDialog vrací okamžitě, i když dialog není vykreslen
- **Řešení**: Modal dialog blokuje volání kód dokud není `setVisible(false)`, import běží na pozadí přes SwingWorker
- **Flow**: startImport() → execute worker → setVisible(true) → čeká na done() → setVisible(false)
- **Debug výstup**:
  ```
  DEBUG: ProgressDialog.startImport() - creating worker
  DEBUG: ProgressDialog.startImport() - executing worker
  DEBUG: SwingWorker.doInBackground() - starting
  DEBUG: SwingWorker.process() - chunks=...
  DEBUG: SwingWorker.done() - called
  ```

## [Striktní timeout enforcement pro IBKR Flex HTTP požadavky] - 2026-01-19

## [Oprava EDT blokování v IBKR Flex Progress Dialog] - 2026-01-19

### Opraveno
- **EDT blokování**: Kritická chyba kde progress dialog vypadal zamrzlý s prázdným obsahem. Problém byl v `waitForCompletion()` metodě, která používala busy-wait smyčku s `Thread.sleep(100)` přímo na EDT vlákně, což blokovalo veškeré UI aktualizace.

### Změněno
- **IBKRFlexProgressDialog.java**:
  - Nahrazen busy-wait loop (`while (!completed) { Thread.sleep(100); }`) za Swing Timer (`javax.swing.Timer`)
  - Použit `CountDownLatch` pro čekání na dokončení bez blokování EDT
  - Přidán debug výstup do konstruktoru pro diagnostiku UI komponent
  - Debug logování ověřuje vytvoření `progressBar`, `statusLabel` a `cancelButton` komponent

### Technické detaily
- **Původní problém**: `Thread.sleep(100)` na EDT vlákně zabraňovalo repaint() a validaci() volání
- **Řešení**: Swing Timer běží na EDT a pravidelně kontroluje `completed` flag bez blokování
- **Přínos**: Progress dialog se nyní správně zobrazuje a reaguje na uživatelské interakce

## [Diagnostika a oprava zaseknutí IBKR Flex importu] - 2026-01-19

### Opraveno
- **HTTP timeout**: Přidán konečný timeout na HTTP požadavky (30s connect, 60s read) pro zabránění nekonečného blokování při nedostupnosti IBKR API
- **Cache bug**: Opravena chyba v `IBKRFlexCache.loadCacheFromDisk()` kde `if (csvFiles != null) return;` způsobovalo přeskočení načítání cache

### Přidáno
- **Debug logging**: Rozsáhlé debug logování do `importYear()` metody pro identifikaci přesného místa zaseknutí
  - Loguje: start, check cache, použití cache, stahování z API, parsování, dokončení
  - Zobrazuje počet transakcí při úspěšném importu
- **Validace credentials**: Přidána validace Flex Token a Query ID před zahájením importu
  - Metoda `getValidationError()` vrací uživatelsky přívětivou chybovou zprávu
  - Kontrola před API voláním zobrazí dialog s instrukcemi k nastavení
- **Detailní logování API**: Rozšířené logování v `requestAndDownloadReport()` pro sledování každého poll kroku

### Změněno
- **IBKRFlexClient.java**:
  - Přidány konstanty `CONNECT_TIMEOUT_SECONDS=30` a `READ_TIMEOUT_SECONDS=60`
  - HttpClient nyní používá `HttpClient.newBuilder()` s `connectTimeout()`
  - Detailní logování průběhu report request/polling/download
- **IBKRFlexImporter.java**:
  - Přidána metoda `validateCredentials()` pro kontrolu nastavení
  - Přidána metoda `getValidationError()` pro uživatelsky přívětivé chybové zprávy
  - Debug `System.out.println()` v `importYear()` pro diagnostiku
- **IBKRFlexCache.java**:
  - Opravena podmínka z `if (csvFiles != null) return;` na `if (csvFiles == null) return;`
- **IBKRFlexImportWindow.java**:
  - Přidána validace credentials před zahájením importu
  - Zobrazení chybového dialogu s instrukcemi při chybějícím nastavení

## [Vylepšený Trading 212 import s CSV cache a per-file stavem] - 2026-01-18

### Opraveno
- **NullPointerException při ukládání Settings**: Opravena chyba, kdy kliknutí na OK ve Settings okně způsobovalo pád aplikace. Problém byl v inicializaci modelu (RTableModel), který nebyl vytvořen pokud bylo okno otevřeno přes setVisible() místo showDialog().

### Přidáno
- **CSV Cache systém**: Lokální ukládání stažených CSV exportů z Trading 212 pro rychlejší re-import bez API volání
  - Cache umístěná v `~/.trading212/csv_cache/{accountId}/{year}.csv`
  - Automatické ukládání při stažení z API
  - Automatické použití cache při příštím importu stejného roku
  - Metadata soubor s informacemi o velikosti a času stažení
- **Tlačítko "🔄 Obnovit z API"**: Možnost vynutit nové stažení i když existuje cache
  - Aktivní pouze když je cache k dispozici
  - Potvrzovací dialog před přepsáním cache dat
- **Per-file import stav**: Stav importu (.t212state soubor) vázaný na konkrétní .dat soubor místo globálního nastavení
  - Každý .dat soubor má vlastní .t212state sidecar soubor
  - Obsahuje accountId a historii importovaných roků
  - Řeší problém kdy "(Imported)" status byl zavádějící při otevření jiného .dat souboru
- **Rozšířené statusy roků**: Rok dropdown nyní zobrazuje kombinované statusy
  - `(Not Imported)` - Rok ještě nebyl importován
  - `(Cached)` - Rok má cache ale nebyl importován do tohoto .dat souboru
  - `(Imported)` - Rok byl importován (bez cache)
  - `(Imported • Cached)` - Rok byl importován a má cache
  - `(Partial)` - Částečný import (pouze běžný rok)
- **Kontrola API přihlašovacích údajů**: Automatická detekce chybějících API credentials
  - Import tlačítko se mění na "⚙ Nastavit Trading 212 API..." pokud credentials chybí
  - Kliknutí otevře přímo Settings okno (v budoucnu na Trading 212 tab)
  - Po zavření Settings okna se automaticky znovu zkontroluje dostupnost credentials
- **Account ID tracking**: Automatické získání account ID z API pro správné cache a state ukládání
  - Fallback na "demo"/"live" pokud API volání selže

### Změněno
- **Trading212Importer.java**: 
  - Integrován CSV cache check před API voláním
  - Přidán `forceRefresh` flag pro vynucení stažení
  - Přidána metoda `getAccountId()` pro získání account ID z API
  - Cache se ukládá automaticky po úspěšném stažení CSV (GUI i headless režim)
- **ImportWindow.java**:
  - Přidáno tlačítko "🔄 Obnovit z API" vedle year dropdownu
  - Implementována logika `hasValidApiCredentials()` a `openSettings_Trading212Tab()`
  - Aktualizace import tlačítka textu podle stavu credentials
  - `getTrading212YearStatus()` nyní kontroluje cache existence
  - `performTrading212Import()` přijímá `forceRefresh` parametr
- **Trading212ImportState.java**:
  - Přidány metody `loadFromFile()` a `saveToFile()` pro per-file stav
  - Přidáno `accountId` pole
  - Metoda `getSidecarFile()` pro získání .t212state souboru
- **CsvReportProgressDialog.java**:
  - Přidána metoda `setCacheParameters()` pro předání cache info
  - Automatické ukládání CSV při stažení v GUI dialogu

### Technické detaily
- **Cache struktura**:
  ```
  ~/.trading212/
  ├── csv_cache/
  │   ├── {accountId}/
  │   │   ├── 2021.csv
  │   │   ├── 2022.csv
  │   │   ├── metadata.json
  ```
- **Sidecar soubor formát** (.t212state):
  ```json
  {
    "accountId": "U15493818",
    "years": {
      "2021": {"fullyImported": true, "lastImportDate": "...", "transactionCount": 145}
    }
  }
  ```
- **Cache výhody**:
  - Instant import z cache (žádné čekání na API)
  - Offline import možnost když je cache k dispozici
  - Snížení API rate limit problémů
  - Per-account izolace (demo vs live)

### Pozn ámky
- Cache se **nemazá automaticky** - zůstává uložena navždy nebo dokud uživatel nevymaže ručně
- **Žádný limit velikosti cache** - může růst neomezeně
- SettingsWindow integrace (setSelectedTab, cache management UI) bude dokončena v další verzi

## [Rozšířené parsování poznámek a filtrování metadat] - 2026-01-18

### Opraveno
- **Automatické filtrování Effect selectboxu**: Opraveno chybějící automatické aplikování filtru při změně výběru v Effect selectboxu (nyní konzistentní s Broker a AccountID selectboxy)
- **Automatické filtrování Typ selectboxu**: Opraveno chybějící automatické aplikování filtru při změně výběru v Typ selectboxu

### Přidáno
- **Parsování metadat z poznámek**: Automatické extrahování Broker, AccountID, TxnID a Code z poznámek ve formátu "Description|Broker:VALUE|AccountID:VALUE|TxnID:VALUE|Code:VALUE"
- **Nové sloupce v tabulce**: Přidány sloupce Broker, ID účtu, ID transakce a Efekt před sloupcem Note
- **Podpora více kódů efektů**: Code pole může obsahovat více hodnot oddělených středníky (např. "Code:A;C" → "Assignment", "Code:C;Ep" → "Expired")
- **Rozšířené filtrování**: Nové filtry pro Broker (selectbox s existujícími hodnotami), AccountID (selectbox s existujícími hodnotami) a Effect (selectbox s možnostmi Assignment, Exercise, Expired)
- **Inteligentní filtrování efektů**: Filtrování podle efektu funguje i pro kombinované efekty
- **Viditelnost sloupců**: Možnost skrýt/zobrazit metadata sloupce v Nastavení (výchozí: zobrazené)

### Implementace
- **Transaction.java**: Rozšířena metoda `getEffect()` pro zpracování více kódů se středníkem jako oddělovačem
- **TransactionSet.java**: 
  - Aktualizována metoda `applyFilter()` s parametry pro broker, accountId a effect
  - Přidány metody `getBrokersModel()` a `getAccountIdsModel()` pro dynamické naplnění selectboxů
  - Selectboxy se automaticky aktualizují po načtení souboru nebo importu dat
- **MainWindow.java**: 
  - Broker a AccountID nyní jako JComboBox (selectbox) místo textových polí
  - Selectboxy se automaticky naplní jedinečnými hodnotami z načtených transakcí
  - Metoda `refreshMetadataFilters()` volána po načtení/importu dat
  - Dvouřádkové uspořádání filtrů (řádek 0: hlavní filtry, řádek 1: metadata filtry)
  - Metadata filtry vizuálně odsazeny vlevo pro hierarchii
  - Tlačítka a separátor přes oba řádky pro čistší vzhled
  - Minimální velikost okna 1200x600px
  - Checkbox "Show Metadata" přímo v řádku 1 pro rychlý přístup
- **ImportWindow.java**: Volá `mainWindow.refreshMetadataFilters()` po dokončení importu
- **SettingsWindow.java**: Přidán checkbox pro zobrazení/skrytí metadata sloupců (též dostupný v hlavním okně)
- **Form layout**: Aktualizován MainWindow.form pro nové filtry v GridBagLayout

### Podporované formáty poznámek
- **Plný formát**: "AAPL 20DEC24 240 C|Broker:IB|AccountID:U15493818|TxnID:3452118503|Code:A;C"
- **Částečný formát**: "TSLA 16FEB18 350.0 C|Broker:IB|Code:C;Ep" (chybějící pole = prázdná hodnota)
- **Rozpoznané kódy efektů**: A=Assignment, Ex=Exercise, Ep=Expired
- **Ignorované kódy**: C (Closing), O (Opening) a další jsou ignorovány

### Příklady parsování efektů
- **"Code:A;C"** → "Assignment" (C ignorováno)
- **"Code:C;Ep"** → "Expired" (C ignorováno)
- **"Code:A;Ex"** → "Assignment, Exercise" (oba zobrazeny)
- **"Code:C;O"** → "" (oba ignorovány, prázdná hodnota)
- **Bez Code pole** → "" (prázdná hodnota)

### Pouze pro deriváty
- Sloupec Efekt se vyplňuje pouze pro derivátové transakce (DIRECTION_DBUY, DIRECTION_DSELL)
- Běžné akcie nemají efekty, zobrazují prázdnou hodnotu

### Uživatelské rozhraní
- **Dvouřádkové uspořádání filtrů**: 
  - Řádek 0: Filtrovat, Zrušit, Od, Do, Ticker, Trh, Typ, tlačítka
  - Řádek 1: Note, Broker, Account ID, Effect, Show Metadata (vizuálně odsazeno)
- **Note pole**: Zvětšeno na 150px pro delší poznámky
- **Tlačítka akce**: Smazat řádek a Seřadit centrované přes oba řádky
- **Velikost okna**: Minimální 1200x600px (vhodné pro běžné monitory)
- **Selectbox filtry**: Broker a Account ID nyní jako rozbalovací seznamy s existujícími hodnotami
- **Checkbox viditelnosti**: "Show Metadata" v řádku 1 pro rychlé skrytí/zobrazení metadata sloupců

## [Aktualizace duplikátních záznamů při re-importu] - 2026-01-18

### Přidáno
- **Checkbox "Aktualizovat duplikáty"**: Nová možnost v importním dialogu pro re-import existujících záznamů
- **Aktualizace poznámek**: Při re-importu se aktualizují Poznámky, Poplatky, Měna poplatku a Datum vypořádání
- **Vizuální zvýraznění**: Aktualizované řádky jsou zvýrazněny světle žlutou barvou v hlavním okně
- **Persistence nastavení**: Stav checkboxu se ukládá a obnovuje mezi relacemi
- **Univerzální podpora**: Funguje pro všechny typy importu (IB TradeLog, Fio, BrokerJet, Trading212 API, atd.)

### Použití
1. Při importu souboru s duplikáty se zobrazí počet nalezených duplikátů
2. Zaškrtněte "Aktualizovat duplikáty" pro přepsání existujících záznamů
3. Text se změní z "X duplikátů vyfiltrováno" na "X duplikátů k aktualizaci"
4. Po importu jsou aktualizované řádky zvýrazněny žlutě
5. Zvýraznění zůstává až do restartu aplikace

### Technické detaily
- **Transaction.java**: Přidána metoda `updateFromTransaction()` pro aktualizaci vybraných polí
- **TransactionSet.java**: 
  - Metoda `findDuplicateTransaction()` pro nalezení existujícího záznamu
  - Metoda `updateDuplicateTransaction()` pro aktualizaci a označení
  - HashSet `updatedTransactionSerials` pro sledování aktualizovaných záznamů
  - Metoda `isRecentlyUpdated()` pro kontrolu zvýraznění
- **ImportWindow.java**: 
  - Checkbox UI s event handlerem
  - Logika pro sledování duplikátů určených k aktualizaci
  - Integrováno do file-based i API importů
- **MainWindow.java**: 
  - `HighlightedCellRenderer` pro obecné buňky
  - `HighlightedDateRenderer` pro datumové sloupce
  - Aplikováno na všechny sloupce tabulky
- **Settings.java**: 
  - Pole `updateDuplicatesOnImport` pro persistenci
  - Gettery/settery a ukládání/načítání z preferences

### Důvod změny
- Po změně formátu sloupce Poznámky byly staré záznamy v původním formátu
- Re-import duplikátů byl blokován, takže poznámky nemohly být aktualizovány
- Tato funkce umožňuje selektivní aktualizaci existujících záznamů novými daty

## [Oprava synchronizace formátu importu] - 2026-01-18

### Opraveno
- **Kritická chyba synchronizace formátu**: Opraveno selhávání parseru při programových importe (např. IB TradeLog s formátem 3) kvůli nesprávnému používání UI stavu místo přednastaveného formátu
- **Mechanismus programového přepisu**: Implementován `currentImportFormat` pro spolehlivé přepsání UI stavu při programových importe
- **Robustní správa stavu**: Automatické vymazání programového formátu po dokončení importu nebo při manuálních změnách
- **Vylepšené ladění**: Rozšířené logování zobrazující jak programový tak UI formát pro diagnostiku problémů
- **Kritická chyba časování**: Opraveno předčasné volání `loadImport()` při nastavování datumů, které používalo starý formát

### Technické detaily
- **ImportWindow.java**: Přidána instance proměnná `currentImportFormat` s prioritou před UI stavem
- **loadImport()**: Používá `currentImportFormat > 0 ? currentImportFormat : cbFormat.getSelectedIndex()`
- **startImport()**: Nastavuje programový formát PŘED nastavením datumů, aby se zabránilo předčasným voláním
- **Vymazání stavu**: Automatické vymazání při úspěchu, neúspěchu nebo manuálních změnách
- **Rozšířené logování**: Přidáno logování časování pro sledování sekvence operací
- **Zpětná kompatibilita**: Žádný vliv na existující UI-based import workflow

## [Vylepšení UI Trading 212 API importu] - 2026-01-17

### Přidáno
- **Progress bar for API imports**: GUI progress dialog s countdown timer a možností zrušení během API stahování
- **Dynamic window titles**: "Import z Trading 212 API" pro API importy místo generického "Import souboru"
- **Streamlined UI layout**: Horizontální uspořádání ovládacích prvků pro lepší využití prostoru
- **Simplified workflow**: Odstranění cache tlačítek pro zjednodušení pracovního postupu
- **Enhanced note format**: Bohatší poznámky s názvem společnosti, broker identifikací a ISIN kódem

### Opraveno
- **Progress bar parent frame**: Oprava reference na parent frame pro správné zobrazování progress dialogu
- **UI consistency**: Sjednocení layoutu Trading 212 import okna s ostatními částmi aplikace
- **Note field content**: Změna formátu poznámek z "Imported from Trading 212 CSV - [action]" na "[název společnosti]|Broker:T212|ISIN:[ISIN]"

### Implementace
- Aktualizace `ImportWindow.java` pro dynamické titulky oken a zjednodušený layout
- Aktualizace `Trading212CsvParser.java` pro nový formát poznámek s názvem společnosti a ISIN
- Aktualizace `ImportT212.java` a `ImportT212CZK.java` pro konzistentní poznámky s názvy společností
- Aktualizace `ImportIBTradeLog.java` pro rozšířené poznámky s Account ID, Transaction ID a status kódy
- Oprava `setParentFrame()` volání pro správnou podporu progress dialogu
- Odstranění nepotřebných cache funkcí (`bUseCached`, `bClearCache`)
- Přepracování GridBagLayout pro horizontální uspořádání ovládacích prvků

## [Inteligentní filtrování tickerů] - 2026-01-17

### Přidáno
- **Smart ticker filtering**: Automatická detekce transformačních vztahů mezi tickery (např. SSL→RGLD, TRXC→ASXC)
- **TRANS operation analysis**: Detekce transformačních operací typu TRANS_SUB/TRANS_ADD ve stejném časovém okamžiku
- **Transformation cache**: Optimalizovaný cache pro rychlé vyhledávání transformačních vztahů (40k+ záznamů <50ms)
- **Bidirectional relationships**: Filtrování funguje obousměrně (SSL zobrazí i RGLD transakce, RGLD zobrazí i SSL)
- **Enhanced debug logging**: Podrobné logování transformační analýzy s FINER úrovní

### Implementace
- `TransformationCache.java`: Nová třída pro správu transformačních vztahů s lazy loading
- `TransactionSet.analyzeTransTransformations()`: Analýza TRANS operací pro detekci ticker změn
- Aktualizace `applyFilter()` pro využití transformačních vztahů při filtrování
- Oprava cache rebuild procesu pro zachování TRANS vztahů přes invalidace
- Aktualizace stavového řádku pro zobrazení souvisejících tickerů

## [Detekce duplicitních transakcí] - 2026-01-17

### Přidáno
- **Duplicate detection**: Automatická detekce duplicitních transakcí při importu
- **Preview filtering**: Duplikáty jsou filtrovány již v okně náhledu importu
- **Business key comparison**: Detekce na základě klíčových obchodních polí (datum, směr, ticker, množství, cena, měna, trh)
- **Tolerance for amounts**: Tolerance ±0.01 pro množství a ceny kvůli floating-point přesnosti
- **UI feedback**: Zobrazení počtu vyfiltrovaných duplicit v náhledu importu
- **Cache consistency**: Cache ukládá filtrované transakce pro konzistenci mezi náhledem a sloučením

### Implementace
- `TransactionSet.filterDuplicates()`: Filtrování duplicitních transakcí
- `TransactionSet.isDuplicateTransaction()`: Porovnání transakcí podle business klíče
- Aktualizace `ImportWindow` pro filtrování během načítání náhledu pro API i souborové importy
- Oprava duplicitní detekce pro všechny formáty importu (IB TradeLog, FIO, Revolut, atd.)

### Nové funkce z TODO seznamu
#### Status bar v hlavním okně
- Přidán status bar dole zobrazující počet záznamů
- Automatická aktualizace při změnách v databázi
- Změna layoutu z GridBagLayout na BorderLayout
- Oprava: Bezpečná inicializace bez NullPointerException
- Oprava: Aktualizace po souborových importech (FIO, IB, atd.)
- Vylepšení: TableModelListener pro 100% spolehlivé automatické aktualizace
- Vylepšení: Zobrazení filtrovaných záznamů ("Záznamů: 150 | Filtr: 25")
- Optimalizace: Žádné manuální volání, aktualizace jen při skutečných změnách dat
- Oprava: Správné počítání záznamů (vyloučení prázdného řádku pro přidávání)
- Oprava: Status bar aktualizace při "Soubor/Nový" (opětovné připojení TableModelListener + explicitní aktualizace)
- Nová funkce: Smart filtering pro ticker s transformacemi (automatické zahrnutí příbuzných tickerů při filtrování)
  - TransformationCache třída pro cachování transformačních vztahů
  - Automatická detekce tickerů přejmenovaných během obchodování
  - Filtrování podle jednoho tickeru zobrazí transakce pro všechny příbuzné tickery
  - Optimalizace výkonu pro velké datové sady (40k+ záznamů)
  - Konfigurovatelné úrovně ladění přes systémové vlastnosti
- Vylepšení: Status bar zobrazuje seznam zahrnutých tickerů při smart filtering ("Zahrnuje: AAPL, AAPL.NEW")

#### Perzistentní výběr formátu importu
- Uložení posledního vybraného import formátu do nastavení
- Obnovení výběru při dalším spuštění aplikace i v menu "Import od brokera"
- Použití Java Preferences API pro perzistenci
- Pre-selekce uloženého formátu v dialogu výběru formátu

#### Maximalizační tlačítko v okně importu
- Převedeno ImportWindow z JDialog na JFrame pro spolehlivou podporu maximalizace
- Odstraněno modální chování (okno nezablokuje hlavní okno)
- Přidáno maximalizační tlačítko do title baru okna

## [API Dokumentace] - 2026-01-17

### Přidáno
- **API.md**: Kompletní dokumentace všech externích API používaných aplikací
- **Trading212 API dokumentace**: Detailní popis endpointů, autentifikace, rate limitů a integrace
- **ČNB API dokumentace**: Dokumentace kurzů měn včetně výpočtu jednotného kurzu
- **README.md odkaz**: Přidán odkaz na API dokumentaci v sekci funkcí aplikace

## [Oprava headless Trading 212 API import] - 2026-01-16

### Opraveno
- **Headless CSV import**: Přidána podpora pro API import bez GUI dialogu pro CLI/headless prostředí
- **Polling-based monitoring**: Implementováno pravidelné sledování stavu CSV reportů bez nutnosti GUI
- **Fallback mechanism**: Automatické přepínání mezi GUI a headless módem podle dostupnosti okna
- **Button text logic**: Opraveno zobrazování textu tlačítka "Importovat" vs "Sloučit do databáze"
- **UI state updates**: Přidáno volání updateImportButtonText() po načtení preview dat
- **Import workflow logic**: Opraveno dvojité volání API - nyní správně rozlišuje fetch vs merge režimy
- **Transaction count accuracy**: Opraveno zobrazování počtu importovaných transakcí - nyní odpovídá skutečně přidaným řádkům v databázi
- **Progress dialog restoration**: Obnoveno GUI okno s odpočtem pro sledování generování CSV reportů
- **Parent frame passing**: Implementováno předávání parent okna pro správné zobrazování progress dialogu
- **Button text localization**: Přejmenováno tlačítko z "Importovat" na "API stahnuti" pro Trading 212 API formát
- **Czech language translation**: Přeloženy všechny anglické texty do češtiny (tlačítka, zprávy, chybové hlášky, status indikátory)
- **Import preview isolation**: Opraveno zobrazování dat z předchozího importu při přepínání formátů - náhled se nyní čistí při přechodu na API formát
- **Progress dialog cancel functionality**: Opraveno kliknutí na "Zrušit" v progress dialogu - nyní skutečně zastaví API import místo pouhého zavření okna
- **API permissions documentation**: Přidána informace o požadovaných API oprávněních v nastavení Trading 212 (Account data, History, Dividends, Orders, Transactions)
- **Complete Czech localization**: Dokončena česká lokalizace nastavení Trading 212 API s ponecháním technických termínů v angličtině
- **Enhanced connection test**: Test připojení nyní zobrazuje načtené údaje o účtu (Account ID, Type, Balance, Currency, Status)
- **Structured account data display**: Test připojení zobrazuje kompletní strukturované údaje o účtu včetně rozepsané hotovosti a investičních detailů

## [Rozšířené debugování API import workflow] - 2026-01-16

### Přidáno
- **Strukturované debug logování**: Kompletní trasování execution flow s emoji markery a hierarchickou strukturou
- **Fázové markery**: Jasné oddělení validačních, API, UI a dokončovacích fází
- **Status indikátory**: ✅ ❌ ⏳ indikátory pro okamžité rozpoznání úspěchu/neúspěchu
- **Časové značky**: Console timestamps umožňují sledování timing každého kroku
- **Hierarchické formátování**: Indentace ukazuje call depth a vztahy mezi komponentami

### Technické detaily
- **Debug formát**: Standardizovaný výstup s [PHASE] markery a vizuálními indikátory
- **Kompletní pokryti**: Logování od button click přes validace, API volání, až po UI updates
- **Error tracing**: Podrobné logování všech exception scénářů s kontextem
- **Performance**: Minimální overhead - jen console výstup bez vlivu na funkcionalitu
- **Analytická hodnota**: Umožňuje okamžité diagnostikování kde execution zastaví

## [Odstranění neimplementované background funkcionality] - 2026-01-16

### Odebráno
- **Background button**: Odstraněno tlačítko "Continue in Background" které nebylo implementováno
- **Background option v timeout dialogu**: Zjednodušena timeout dialog z 3 na 2 možnosti (Continue/Cancel)
- **switchToBackgroundMode() metoda**: Kompletní odstranění neimplementované metody

### Změněno
- **UI simplification**: Zjednodušeno rozhraní CsvReportProgressDialog - pouze Cancel tlačítko
- **Timeout dialog**: Aktualizován text dialogu bez reference na background režim
- **User experience**: Odstraněna zavádějící funkcionalita která slibovala background processing bez implementace

### Technické detaily
- **CsvReportProgressDialog**: Odstraněn backgroundButton field a související kód
- **Timeout handling**: Změna z YES_NO_CANCEL_OPTION na YES_NO_OPTION
- **Code cleanup**: Odstraněny všechny reference na neimplementovanou background funkcionalitu

## [Kritická oprava timer leak v Trading 212 API importech] - 2026-01-16

### Opraveno
- **Timer leak po úspěšném importu**: Kritická chyba kde CsvReportProgressDialog nezastavoval timery po dokončení importu
- **Background notifications**: Odstraněno nekonečné zobrazování timeout dialogů každých 30 minut po úspěšném importu
- **GUI freeze**: Opraveno zamrzání aplikace vyžadující force-kill po úspěšných API importe
- **Resource cleanup**: Zajištěno zastavení všech background timerů po dokončení importu

### Technické detaily
- **CsvReportProgressDialog.completeImport()**: Přidáno zastavení statusTimer a countdownTimer před logováním úspěchu
- **Timer management**: Sjednoceno zastavování timerů ve všech completion path (success, error, cancel)
- **Background thread cleanup**: Zabráněno zombie threads které způsobovaly GUI freeze na Linux platformě

## [Kompletní oprava Trading 212 import workflow] - 2026-01-16

### Opraveno
- **Modal dialog closing**: Kritická chyba v zavírání ImportWindow po úspěšném API importu kvůli modal dialog konfliktům
- **Resource cleanup**: Nahrazení setVisible(false) metodou dispose() pro správné uvolnění zdrojů modal dialogu
- **UI responsiveness**: ImportWindow se nyní správně zavře po dokončení Trading 212 API importu bez zaseknutí
- **Import state persistence**: Přidána persistentní správa stavu importu Trading 212 s ukládáním do Settings
- **Year status display**: Roky nyní zobrazují správný stav importu místo placeholdru "(Not Imported)"
- **Error handling**: Zajištěno zavírání dialogu i v případě chyb během importu

### Přidáno
- **Trading212ImportState persistence**: Import stavy se nyní ukládají do Preferences API a přežívají restart aplikace
- **JSON serialization**: Import stavy jsou serializovány jako JSON pro persistentní ukládání
- **Real-time status updates**: Rok selector se aktualizuje po úspěšném importu
- **Settings integration**: Nové metody getTrading212ImportState/setTrading212ImportState v Settings

### Technické detaily
- **performTrading212Import()**: Změna JOptionPane parent z modal ImportWindow na MainWindow pro vyřešení modal konfliktů
- **Trading212ImportState**: Přidány loadFromSettings()/saveToSettings() metody s JSON persistencí
- **ImportWindow**: Přidána importState instance a refreshTrading212YearStatuses() metoda
- **Settings.java**: Přidány trading212ImportState field a load/save logika
- **dispose() usage**: Standardizace zavírání všech modal dialogů pomocí dispose() místo setVisible(false)

## [Podmíněný tok importu pro API vs souborové importy] - 2026-01-16

### Přidáno
- **Format-first workflow**: Nový tok importu začínající výběrem formátu před výběrem souboru
- **Podmíněné zobrazování UI**: Skrytí souborových prvků (kalendáře, náhled, tabulka) pro API importy
- **Inteligentní zpracování importu**: Automatické přepínání mezi souborovým a API workflow na základě vybraného formátu
- **Vylepšené UX pro API importy**: Přímý import bez nutnosti výběru souboru pro Trading 212 API

### Změněno
- **Import menu flow**: Změna z "soubor → formát" na "formát → soubor (pouze pro souborové formáty)"
- **ImportWindow adaptivita**: Dynamické skrývání/zobrazování UI komponent na základě typu importu
- **API import workflow**: Přímé provedení importu bez preview pro API metody (Trading 212)

### Technické detaily
- **MainWindow.java**: Přepsána miImportActionPerformed() pro format-first přístup s podmíněným file dialogem
- **ImportWindow.java**: Přidána updateUiForFormat() metoda pro správu viditelnosti komponent
- **Rozšířené startImport()**: Nová přetížená metoda s parametrem preselectedFormat
- **API import handling**: Modifikováno performTrading212Import() pro přímé sloučení výsledků do hlavní databáze
- **Backwards compatibility**: Zachování plné funkcionality pro všechny existující souborové importy

## [Oprava cache expirace a kompilace Trading 212 integrace] - 2026-01-16

### Opraveno
- **Cache expirace**: Oprava kritické chyby v Trading212ReportCache, kde PROCESSING status byl cachován na 24 hodin, což bránilo aktualizaci stavu dlouhotrvajících reportů
- **Chytrá expirace cache**: Implementace různých dob expirace - finální statusy (FINISHED/FAILED) cachovány na 24 hodin, nefinální statusy (QUEUED/PROCESSING) na 1 minutu
- **Kompilační chyba**: Oprava typu parametru v Trading212Importer (apiClient → csvClient) pro CsvReportProgressDialog konstruktor
- **Rate limiting**: Ověřeno správné fungování 65-sekundového intervalu pro status kontroly API

### Technické detaily
- **Trading212ReportCache**: Přidána metoda isFinalStatus() pro detekci finálních stavů reportů
- **Cache strategie**: Zabránění zaseknutí na "PROCESSING" stavu během dlouhých reportů (81+ minut)
- **Kompatibilita**: Oprava předávání správného typu klienta do progress dialogu
- **Testování**: Úspěšná kompilace projektu s Java 17 kompatibilitou

## [Kompletní Trading 212 CSV integrace s hybridním importem] - 2026-01-16

### Přidáno
- **CSV Report integrace**: Nový způsob importu pomocí Trading 212 CSV reportů pro přesné filtrování dat podle data
- **Hybridní import strategie**: Automatický výběr mezi CSV (pro velké rozsahy >90 dní) a API (pro nedávné údaje)
- **Asynchronní CSV generování**: Pozadí generování reportů s polling stavem a timeout ochranou (10 minut max)
- **Kompletní Trading 212 API integrace**: Základní API klient pro objednávky s klient-side filtrováním
- **Inteligentní správu importů**: Sledování stavu importu po jednotlivých rocích s podporou přírůstkových importů
- **API klient s rate limiting**: Bezpečné volání Trading 212 API s dodržováním limitů (6 požadavků/minutu, 1 CSV/30s)
- **Debug režim**: Ukládání surových API a CSV odpovědí do `/tmp/trading212_debug/` pro ladění
- **Nastavení API přihlašovacích údajů**: Bezpečné uložení API klíče a tajemství s testováním připojení
- **Rozšířené UI**: Test tlačítko, výběr roku s indikátory stavu, detailní chybové dialogy
- **Automatické opakování**: Retry logika s exponenciálním backoff pro přechodné chyby
- **Validace rozsahu dat**: Kontrola API omezení a uživatelsky přívětivé chybové hlášky

### Opraveno
- **Formátování datumů**: Oprava kritické chyby "Unsupported field: OffsetSeconds" v CSV requestech
- **UTC timezone handling**: Vždy používá UTC s 'Z' suffixem pro kompatibilitu s Trading 212 API
- **Validace rozsahu**: Klient-side kontrola rozsahu datumů (max 2 roky) před odesláním na API
- **JSON parsing**: Přidání org.json knihovny pro správné parsování API odpovědí
- **Rate limiting**: Implementace 65-sekundového intervalu pro status kontroly (1 req/minuta)
- **Status caching**: Lokální cache pro dokončené reporty, aby se předešlo opakovaným API voláním
- **Progress dialog**: Dialog s countdown timer a real-time status aktualizacemi
- **30-min timeout**: Dialog po 30 minutách s možnostmi pokračování, zrušení nebo přepnutí do pozadí
- **Pokrok zpětné vazby**: Detailní status aktualizace během generování CSV reportů
- **Chybové hlášky**: Specifické rady pro různé typy chyb (timeout, credentials, range, network, rate limits)
- **CSV parsing**: Kompletní implementace parseru pro Trading 212 CSV formát s podporou všech typů transakcí
- **Debug logging**: Rozšířené ukládání API odpovědí pro troubleshooting

### Změněno
- **Rozšířené možnosti importu**: Přidána možnost "Trading 212 API" do seznamu formátů importu
- **Architektura importu**: Rozšířena `TransactionSet` třída pro podporu API-based importů bez souborů

### Technické detaily
- **Komponenty**: `Trading212ApiClient`, `Trading212CsvClient`, `Trading212CsvParser`, `Trading212DataTransformer`, `Trading212Importer`, `Trading212ImportState`, `Trading212DebugStorage`
- **CSV Workflow**: Asynchronní generování → polling stavu → stahování → parsování CSV
- **Hybridní strategie**: CSV pro rozsahy >90 dní, API pro nedávné údaje s klient-side filtrováním
- **Transformace dat**: Ruční parsování JSON a CSV odpovědí (kompatibilní s Java 8)
- **Správa stavu**: Import stavu per rok s podporou pro plné a přírůstkové importy
- **Bezpečnost**: API klíče a tajemství uložena šifrovaně v systémových preferencích
- **Rate Limiting**: 6 API požadavků/minutu, 1 CSV požadavek/30 sekund
- **Testování připojení**: Validace přes account summary endpoint s detailními chybami
- **Rozšířené UI**: Test tlačítko, výběr roku s indikátory, asynchronní progress feedback
- **Automatické opakování**: Exponenciální backoff pro síťové chyby a rate limiting
- **Validace**: Kontrola datumů, délky rozsahu, API omezení s uživatelsky přívětivými hláškami
- **Debug podpora**: Ukládání všech API a CSV odpovědí do `/tmp/trading212_debug/`
- **Chybová odolnost**: Komplexní error handling s retry logikou a recovery options

### Omezení
- **Časové okno**: API umožňuje importovat maximálně 1 rok dat najednou
- **Účetní typy**: Podporováno pouze pro "Invest and Stocks ISA" typy účtů
- **Živé obchodování**: Pouze market objednávky jsou podporovány pro živé obchodování přes API

## [Filtrování podle typu transakce s přednastavenými hodnotami] - 2026-01-16

### Přidáno
- **Filtrování podle typu**: Nový combobox "Typ" v hlavním okně umožňující filtrování transakcí podle typu (CP, Derivát, Transformace, Dividenda, Cash)
- **Přednastavené hodnoty**: Combobox obsahuje všechny platné typy transakcí plus prázdnou možnost pro zrušení filtru
- **Integrace s existujícím systémem**: Filtrování typu je plně integrováno s ostatními filtry (datum, ticker, trh, poznámka)

### Technické detaily
- **applyFilter()**: Rozšířena metoda pro přijetí parametru typu a filtrování pomocí `tx.getStringType().equals(type)`
- **UI komponenty**: Přidán `JComboBox cbTypeFilter` s přednastavenými hodnotami mezi filtry "Trh" a "Note"
- **Reset filtru**: Combobox se automaticky resetuje při zrušení všech filtrů

## [Rozšiřitelná velikost sloupců tabulky a širší sloupec Ticker] - 2026-01-16

### Změněno
- **Sloupec Ticker**: Zvětšena preferovaná šířka z 50 na 150 pixelů a maximální šířka z 100 na 300 pixelů pro zobrazení dlouhých názvů opcí (např. "SOXS  220218C00003000")
- **Změna velikosti sloupců**: Povolena manuální změna velikosti všech sloupců tabulky pomocí myši (AUTO_RESIZE_OFF)

### Technické detaily
- **initTableColumns()**: Přidáno `table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)` pro plnou kontrolu nad šířkou sloupců
- **Kompatibilita**: Žádný vliv na existující funkcionalitu, pouze vylepšení UI

## [Oprava okna O aplikaci a CHANGES.md] - 2026-01-16

### Změněno
- **Okno O aplikaci**: Odstraněna reference na původní verzi 1.2.7 a autora Michala Káru
- **Odkaz na historii změn**: Opraven text a odkaz na plnou historii změn v CHANGES.md na GitHubu
- **Gramatika CHANGES.md**: Opraveny chyby v české gramatice, diakritika a konzistence v názvech sekcí

## [Rozšířená správa denních kurzů s exportem/importem] - 2026-01-15

### Změněno
- **Persistence denních kurzů**: Změněno z file-based úložiště na Preferences API s individuálními klíči
- **Formát klíčů**: `"dailyRate.CURRENCY|DATE"` → `"RATE"` (vyhýbání se limitu 8KB na klíč)
- **Správa kurzů**: Nahrazeno jednoduché mazání rozšířeným dialogem s hierarchickým výběrem
- **Spolehlivost**: Denní kurzy nyní přežívají restart aplikace stejně jako sjednocené kurzy

### Přidáno
- **Hierarchická správa**: Stromová struktura Měna → Roky s počtem kurzů
- **Selektivní mazání**: Mazání podle konkrétních měn a roků s přesným počtem
- **Export kurzů**: CSV export vybraných měn/roků s popisnými názvy souborů
- **Import kurzů**: CSV import s validací a řešením konfliktů
- **Auto-backup**: Automatické zálohování před mazáním s časovým razítkem
- **Undo funkce**: Jednorázové vrácení posledního smazání
- **Selektivní načítání kurzů**: `getUsedCurrencies()` a `getCurrenciesToFetch()` pro inteligentní načítání pouze používaných měn
- **Optimalizace API**: 90% snížení počtu volání ČNB API díky detekci měn z transakcí

### Vyčištěno
- **Redundantní položka menu**: Odstraněna "Správa denních kurzů" z menu Nástroje
- **Duplicitní kód**: Vyčištěny nepoužívané proměnné a metody
- **Zjednodušení UI**: Jediný přístupový bod k správě kurzů přes Nastavení

### Technické detaily
- **Migrace**: Automatická konverze ze starého file-based formátu na individuální klíče
- **Škálovatelnost**: Žádné limity velikosti (oproti 8KB u jednotného klíče)
- **Výkon**: <2 sekundy načítání pro realistické objemy dat (43,800+ záznamů)
- **CSV formát**: `CURRENCY,DATE,RATE` s UTF-8 kódováním
- **Validace**: Automatická kontrola formátu měn, dat a kurzů při importu
- **UI komponenty**: `RateManagementDialog` s vlastním stromovým rendererem

## [Dynamické zobrazení verze a informace o běhovém prostředí] - 2026-01-15

### Změněno
- **Požadavek na verzi Java**: Snížen z Java 21 na Java 17 pro širší kompatibilitu
- **Sestavovací systém**: Aktualizován pro použití kompilátoru Java 17 s příznakem `--release 17`
- **GitHub Workflow**: Upraven pro použití JDK 17 místo JDK 21

### Přidáno
- **Detekce verze**: Vylepšené skripty `run.sh` a `run.bat` s automatickou kontrolou verze Java
- **Uživatelsky přívětivé chyby**: Jasné instrukce pro instalaci při nedostatečné verzi Java
- **Multiplatformní podpora**: Spouštěcí skripty pro Linux i Windows nyní poskytují užitečné pokyny
- **Dokumentace instalace**: Přidány systémové požadavky a instrukce pro instalaci Java do README.md

### Technické detaily
- **Formát verze**: Úplný výstup git describe (značka-počet commitů-hash)
- **Náhradní chování**: Git describe → JAR-vložený version.properties → "dev-build"
- **Výkon**: Příkaz git spuštěn jednou při spuštění aplikace, uložen v cache pro relaci
- **Verze souboru třídy**: Nyní generuje bytecode kompatibilní s Java 17 (verze 61.0)
- **Proces sestavení**: Explicitně používá kompilátor Java 17 pro zajištění konzistentní kompilace
- **Kontrola za běhu**: Aplikace ověřuje Java 17+ při spuštění s užitečnými chybovými hláškami

## [Konfigurace vzdáleného repozitáře] - 2026-01-15

### Změněno
- **Vzdálený repozitář**: Nakonfigurováno duální nastavení s Gitea jako primárním a GitHub jako sekundárním
- **Primární vzdálený (gitea)**: `ssh://git@192.168.88.97:222/kadu/stock_accounting`
- **Sekundární vzdálený (origin)**: `https://github.com/kaaduu/StockAccounting.git`
- **Dokumentace**: Aktualizováno README.md a create-release-tag.sh pro duální workflow vzdálených repozitářů
- **Workflow**: Značky se nyní odesílají do Gitea jako výchozí, s volitelným odesláním do GitHub

## [Modernizace a migrace na Java 21] - 2026-01-12

### Přidáno
- **Sestavovací systém pro příkazový řádek**: Přidány `build.sh` a `run.sh` pro umožnění sestavení a spuštění projektu bez NetBeans.
- **Nativní výběry souborů**: Integrováno `java.awt.FileDialog` v celé aplikaci pro nativní a responzivní zkušenost s výběrem souborů (Otevřít, Uložit, Import, Export).
- **UX zkratka**: Přidána funkce "Enter pro filtrování" v polích data v `MainWindow`.
- **Moderní závislosti**: Přidáno `jcalendar-1.4.jar`.

### Změněno
- **Kompatibilita s Java 21**:
    - Nahrazeny zastaralé konstruktory `Integer` a `Double` metodami `valueOf()`.
    - Přidány generiky do `SortedSetComboBoxModel`, `JComboBox` a `DefaultComboBoxModel` pro snížení varování o raw typech.
    - Aktualizováno `TransactionSet.java` a `ComputeWindow.java` pro opravu varování o raw typech `Class`.
- **Nahrazení DatePicker**: Nahrazen nejasný `com.n1logic.date.DatePicker` s `com.toedter.calendar.JDateChooser`.
- **Aktualizace knihoven**: Aktualizováno `jcalendar` z verze 1.3.2 na 1.4.
- **Vylepšené UI MainWindow**: Migrace `MainWindow.java` a `MainWindow.form` pro použití `JDateChooser`.

### Odebráno
- **Zastaralé knihovny**:
    - `DatePicker.jar` (nahrazeno JDateChooser).
    - `looks-2.0.1.jar` (nepoužívaná knihovna JGoodies).
    - `swing-layout-1.0.3.jar` (redundantní, verze 1.0.4 je zachována).
## [Funkce načítání kurzů měn] - 2026-01-13

### Přidáno
- **Integrace ČNB**: Nový nástroj `CurrencyRateFetcher` pro automatické načítání oficiálního "jednotného kurzu" z České národní banky (ČNB).
- **Automatické výpočty**: Počítá "jednotný kurz" jako aritmetický průměr 12 měsíčních koncových kurzů podle pokynů Ministerstva financí (GFŘ).
- **Vylepšené UI nastavení**:
    - Přidáno tlačítko "Načíst kurzy" do tabulky nastavení měn.
    - Přidán modální ukazatel průběhu se zpětnou vazbou v reálném čase během načítání dat.
    - Implementováno **načítání pro jeden rok**: Uživatelé mohou nyní načíst kurzy pro konkrétní vybraný rok nebo všechny najednou.
- **Náhled a návrat**:
    - Načtené kurzy jsou zobrazeny v tabulce se žlutým zvýrazněním pro upravené hodnoty.
    - Potvrzovací dialog umožňuje buď aplikovat změny nebo se vrátit k původním hodnotám.
- **Přesné zaokrouhlení**: Směnné kurzy jsou automaticky zaokrouhleny na 2 desetinná místa podle oficiálních daňových požadavků.

### Změněno
- **Vylepšení SettingsWindow**: Integrováno `HighlightedDoubleRenderer` pro vizuální odlišení načtených dat od ručně zadaných.
- **Validace**: Přidána logika porovnání (tolerance 0.001) pro identifikaci a zvýraznění upravených směnných kurzů.
## [Podpora denních kurzů měn] - 2026-01-13

### Přidáno
- **Správa denních kurzů**: Nová záložka "Denní kurzy" v Nastavení pro správu přesných denních kurzů měn.
- **Hromadné načítání**: Efektivní roční hromadné stahování denních kurzů z ČNB.
- **Inteligentní nástroj načítání**: Automaticky identifikuje roky se stávajícími obchody a stahuje chybějící denní kurzy pro ně.
- **Obálka výpočtů**: Implementována centralizovaná poskytovatel kurzů, která přepíná mezi Denními a Sjednocenými kurzy na základě globálního nastavení.
- **Persistence**: Denní kurzy jsou uloženy v dedikovaném souboru `daily_rates.dat` v adresáři dat.
- **Globální přepínač**: Přidáno nastavení "Používat denní kurzy" pro kontrolu úrovně přesnosti ve všech výpočtech (obchody, dividendy, daně).

### Změněno
- **Integrace základních výpočtů**: Migrace `Stocks.java` a `ComputeWindow.java` pro použití nové obálky kurzů měn.
- **Persistence dat**: Aktualizováno `Settings.java` pro zpracování nových nastavení a uložení denních kurzů.
- **Vylepšené UI ComputeWindow**:
    - Přidán indikátor "Metoda přepočtu" zobrazující, zda se používají Denní nebo Sjednocené kurzy.
    - Přidány sloupce "Kurz" do tabulek obchodů pro obě strany (otevření i uzavření).
    - Aktualizovány exporty CSV/HTML pro zahrnutí těchto nových sloupců a zachování správného zarovnání.
- **Vylepšená spolehlivost kurzů měn**:
    - Implementována logika zpětného pohledu 7 dnů v poskytovateli kurzů.
    - Automaticky zpracovává svátky ČNB a víkendy načtením kurzu z předchozího pracovního dne.
    - Odstraňuje falešně pozitivní varování "chybějící kurz" během výpočtu.
- **Rychlý výběr v ComputeWindow a oprava UI**:
    - Přidán přepínač "Používat denní kurzy" přímo do `ComputeWindow` pro rychlé přepínání mezi metodami výpočtu.
    - Refaktorována výpočetní smyčka pro vyřešení kritické chyby, kde se výsledky nezobrazovaly kvůli strukturálním nekonzistencím.
    - Opraveno zarovnání sloupců pro souhrnné řádky (Příjem, Výdej, Zisk) tak, aby odráželo novou strukturu tabulky.
## 2026-01-29

- Oprava importu IBKR Flex (CTRN): sloupec `TransactionID` se spravne mapuje do `TxnID` i pri opakovanych sekcich s ruznym poctem sloupcu.
- Import IBKR Flex: pri reimportu se uz neposouvaji (TimeShift) zaznamy, ktere uz existuji v databazi, aby nevznikaly duplicitni kopie.
