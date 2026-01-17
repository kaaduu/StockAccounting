# Changes

# Změny

Všechny významné změny projektu StockAccounting budou zdokumentovány v tomto souboru.

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
