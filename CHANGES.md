# Změny

# Změny v projektu StockAccounting

Všechny významné změny projektu StockAccounting budou zdokumentovány v tomto souboru.

## [Kontrola chybějících kurzů po importu] - 2026-02-12

### Nové
- Automatická kontrola chybějících měnových kurzů po importu transakcí
- Kontrola denních kurzů i jednotných kurzů (jednotný kurz)
- Zobrazení dialogu s možností stažení chybějících kurzů z ČNB
- Nastavení v předvolbách pro povolení/zákaz kontroly (výchozí: zapnuto)
- Zvýraznění chybějících kurzů v tabulce nastavení (červená barva)
- Komplexní logování událostí do konzole i do Nápověda → Logy

### Funkce
- Po úspěšném importu se automaticky kontroluje, zda jsou k dispozici všechny potřebné kurzy
- Pokud chybějí denní kurzy: uživatel může stáhnout pomocí "Chytré stažení"
- Pokud chybějí jednotné kurzy: uživatel může otevřít nastavení a vidět zvýrazněné chybějící kurzy
- Dialog "Chybějící kurzy měn" nabízí možnosti:
  - "Načíst chybějící kurzy" - automaticky stáhne z ČNB
  - "Otevřít nastavení" - otevře nastavení kurzů se zvýrazněnými chybějícími položkami
  - "Ignorovat" - zavře dialog (uživatel může stáhnout kurzy později)

### Nové soubory
- `FXRateChecker.java` - třída pro kontrolu chybějících kurzů
- `MissingRatesDialog.java` - dialog pro zobrazení chybějících kurzů a akcí

### Změny v existujících souborech
- `Settings.java`:
  - Přidána metoda `addOrUpdateRatio()` pro přidání/aktualizaci jednotného kurzu
  - Přidáno nastavení `checkMissingRatesAfterImport` (výchozí: true)
  - Přidány metody getter/setter pro toto nastavení
- `SettingsWindow.java`:
  - Přidán renderer `MissingRatesRenderer` pro zvýraznění chybějících kurzů (červeně)
  - Přidána metoda `highlightMissingRates()` pro zvýraznění chybějících kurzů v tabulce
  - Přidáno zaškrtávací políčko "Kontrolovat chybějící kurzy po importu" v nastaveních
- `ImportWindow.java`:
  - Po importu volá `FXRateChecker.checkForMissingRates()` pokud je kontrola povolena
  - Zobrazí `MissingRatesDialog` pokud byly nalezeny chybějící kurzy

### Detaily implementace
- Kontrola zahrnuje denní kurzy (pokud jsou povoleny v nastaveních) a jednotné kurzy
- Denní kurzy se kontrolují s 7denním lookback pro víkendy a svátky (stejně jako při výpočtu)
- Načítání kurzů probíhá na pozadí s indikátorem průběhu
- Při načítání se načítají oba typy kurzů (denní i jednotné)
- Při chybějících kurzech v nastaveních jsou buňky zvýrazněny červeně

### Oprava - Kontrola kurzů u všech importů
- **FIXED**: Kontrola chybějících kurzů nyní probíhá u všech typů importu:
  - Souborové importy (CSV, TradeLog)
  - IBKR Flex API importy
  - Trading 212 API importy
- Před opravou fungovala pouze u běžných souborových importů
- Vytvořena společná pomocná metoda `performPostImportFXRateCheck()` pro opětovné použití

### Oprava - Kontrola kurzů po načtení .dat souborů
- **FIXED**: Kontrola chybějících kurzů nyní probíhá i při načtení .dat souborů (otevírání uložených databází)
- Přejmenováno nastavení z `checkMissingRatesAfterImport` na `checkMissingRatesAfterLoad`
- Přidáno volání `performPostLoadFXRateCheck()` v `MainWindow.openFile()`
- Přidáno zpoždění 100ms pro stabilitu UI před kontrolou kurzů
- Přidáno volání `SwingUtilities.invokeLater()` v `openFile()` pro bezpečné spuštění kontroly
- Dialog zobrazuje i po načtení existujícího souboru s upraveným textem "načteném souboru"
- Předvolba "Kontrolovat chybějící kurzy po načtení" nyní ovládá oba scénáře (import i load)
- Při chybě kontrole zobrazí se chybová zpráva uživateli (stejně jako u importu)

### Logování
Všechny důležité události jsou logovány do konzole (prefix `[FXRATES:*]`) a do Nápověda → Logy:

**V ImportWindow.java:**
- `[FXRATES:IMPORT:001]` - Kontrola je spuštěna (pokud je povolena)
- `[FXRATES:IMPORT:002]` - Nalezeny chybějící kurzy, dialog zobrazen
- `[FXRATES:IMPORT:003]` - Všechny kurzy jsou k dispozici
- `[FXRATES:IMPORT:004]` - Kontrola je vypnutá v nastaveních

**Ve FXRateChecker.java:**
- `[FXRATES:CHECK:001]` - Spuštěna kontrola
- `[FXRATES:CHECK:002-003]` - Nalezené měny a roky
- `[FXRATES:CHECK:004]` - ✅ Žádné chybějící kurzy
- `[FXRATES:CHECK:005-009]` - ⚠ Nalezeny chybějící kurzy (s detaily)

**V MissingRatesDialog.java:**
- `[FXRATES:DIALOG:001-003]` - Dialog zobrazen (s počty chybějících kurzů)
- `[FXRATES:DIALOG:004]` - Uživatel ignoroval (zavřel dialog)
- `[FXRATES:DIALOG:005]` - Uživatel otevřel nastavení
- `[FXRATES:FETCH:001-004]` - Načítání kurzů spuštěno (s podrobnostmi)
- `[FXRATES:FETCH:005]` - Načítání dokončeno (s výsledky)
- `[FXRATES:ERROR:001-002]` - Chyby při načítání kurzů

**V SettingsWindow.java:**
- `[FXRATES:SETTINGS:001]` - Změna nastavení kontroly (zapnuto/vypnuto)
- `[FXRATES:SETTINGS:002-007]` - Zvýrazňování chybějících kurzů v tabulce

**AppLog (Nápověda → Logy):**
- INFO: Spuštění kontroly, výsledky, úspěšné načítání
- WARN: Nalezeny chybějící kurzy, chyby při načítání
- ERROR: Výjimky při kontrole nebo načítání

**Logování rozšířeno:**
- **Import scénář**: `[FXRATES:IMPORT:*]` - logy při importu
- **Load scénář**: `[FXRATES:LOAD:*]` - logy při načtení .dat souboru
- Rozlišení logování umožňuje identifikaci scénáře v konzoli i v Nápověda → Logy

## [TxnID-based seskupování transformací] - 2026-02-12

### Změněno
- Nahrazeno minute-level seskupování transformací na TxnID-based seskupování
- Transformace nyní používají ID transakce (TxnID) jako primární klíč pro párování
- Podporováno více transformací ve stejné minutě, pokud mají různé TxnID
- Zpětná kompatibilita zachována: stará data bez TxnID stále fungují

### Nové pravidla
1. **Transformace = PŘESNĚ 2 transakce** (SUB + ADD)
2. **Seskupení**: minute-level (yyyy-MM-dd HH:mm) NEBO TxnID
3. **Multiple transformace ve stejné minutě**:
   - Pokud mají TxnID: povoleno (každý pár má unikátní TxnID)
   - Pokud BEZ TxnID: **CHYBA** (povoleno jen 1 pár)
4. **Balance check**: Přesná rovnost (bez tolerance)
   - `am1 == am2`: ticker rename nebo identity
   - `am1 != am2`: split s poměrem `am2/am1`

### Zpětná kompatibilita
- Staré .dat soubory bez TxnID: zpracovány jako v předchozích verzích
- Mínutové seskupování pro backward compatibility
- Pokud 2 transformace bez TxnID ve stejné minutě: aplikováno jako dříve
- Pokud 3+ transformací bez TxnID: vyhodí se chyba

### Odstraněno
- Time Shifting workaround z importů (IBKR Flex, Trading 212)
- `normalizeIbkrMinuteCollisions()` metoda
- `disambiguateIbkrDuplicateCollisions()` metoda
- `appendTimeShiftMinutesMarker()` metoda
- `shiftTransactionByMinutes()` metoda
- `normalizeTrading212MinuteCollisions()` metoda
- Automatické časové posuny při importu transformací
- Korekce kolizí časů (duplicate collisions) v importech

### Chybové zprávy
- Pro >2 transformací s TxnID: "Transformace s TxnID=... má ... transakcí (očekáváno 2: SUB + ADD). Každá korporátní akce musí mít PŘESNĚ 2 transakce (SUB + ADD)."
- Pro >2 transformací bez TxnID: "Nalezeno ... transformací ve stejné minutě bez ID transakce.\n\nPro vyřešení:\n  1) Pokud importujete z IBKR/Trading 212, zkontrolujte nastavení exportu.\n  2) Přidejte ID transakce (TxnID) do obou transakcí.\n  3) Nebo rozdělte transformace do různých minut."

### Změny v kódu
- `Stocks.java`: Přechod z `Vector<Transaction> trans` na `Map<String, List<Transaction>> pendingTransformations`
- Přidány metody: `getTransformationKey()`, `processTransformationBucket()`, `flushPendingTransformation()`
- Změna v `applyTransaction()`: používá TxnID-based grouping místo časového seskupování
- Změna v `finishTransformations()`: volá `flushPendingTransformation()` místo přímého zpracování
- Přidána metoda `applyTransformationPair()` (zachována původní logika)
- `ComputeWindow.java`: Nahrazeno `JOptionPane.showMessageDialog()` pomocí `UiDialogs.error()`
- `ImportWindow.java`: Odstraněny všechny time shifting metody a jejich volání
- Přidán komentář k nahrazení Time Shifting logiky

### Technické detaily
- `getTransformationKey()`: Generuje klíč ve formátu `yyyy-MM-dd HH:mm|broker|accountId`
- `processTransformationBucket()`: Rozděluje transakce podle TxnID vs bez TxnID, validuje počet 2 transakcí, vyvolává chyby pro >2
- `flushPendingTransformation()`: Prochází všechny pending transformations, zpracovává každou skupinu
- Používá `Calendar` pro minute-level truncation
- Balance check provádí přesnou rovnost (bez tolerance)

### Důsledky
- Importy nyní importují přesné časy včetně sekund
- TxnID slouží k jednoznačné identifikaci transformací
- Eliminováno riziko automatických časových posunů
- Zlepšená spolehlivost při importech z IBKR/Trading 212
- Automatické párování transformací

### Backwards Compatibility
- Stará data bez TxnID: fungují stejně jako dříve
- Pokud 2 transformace bez TxnID ve stejné minutě: aplikováno jako minutové seskupení
- Pokud 3+ transformací bez TxnID: vyhodí se chyba (user upravit data)
- Nové importy s TxnID: podporují více transformací ve stejné minutě

---

*Další změny budou přidány sem.*

