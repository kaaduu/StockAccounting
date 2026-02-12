# Změny

# Změny v projektu StockAccounting

Všechny významné změny projektu StockAccounting budou zdokumentovány v tomto souboru.

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

