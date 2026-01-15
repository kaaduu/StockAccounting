# Changes

# Změny

Všechny významné změny projektu StockAccounting budou zdokumentovány v tomto souboru.

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

### Technické detaily
- **Migrace**: Automatická konverze ze starého file-based formátu na individuální klíče
- **Škálovatelnost**: Žádné limity velikosti (oproti 8KB u jednotného klíče)
- **Výkon**: <2 sekundy načítání pro realistické objemy dat (43,800+ záznamů)
- **CSV formát**: `CURRENCY,DATE,RATE` s UTF-8 kódováním
- **Validace**: Automatická kontrola formátu měn, dat a kurzů při importu
- **UI komponenty**: `RateManagementDialog` s vlastním stromovým rendererem

## [Dynamické zobrazení verze a informace o běhovém prostředí] - 2026-01-15

### Změněno
- **Požadavek na verzi Java**: Snížena z Java 21 na Java 17 pro širší kompatibilitu
- **Sestavovací systém**: Aktualizováno pro použití kompilátoru Java 17 s příznakem `--release 17`
- **GitHub Workflow**: Upraveno pro použití JDK 17 místo JDK 21

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
