# API Dokumentace

## Přehled
Tato aplikace využívá externí API pro získávání dat o obchodech a kurzů měn. Níže je detailní dokumentace všech používaných API rozčleněná podle platformy.

## Trading212 API

### Účel
Načítání obchodních dat z Trading212 účtu včetně historie obchodů, shrnutí účtu a generování CSV reportů pro import do aplikace.

### Autentifikace
- **Metoda**: Základní HTTP autentifikace (Basic Auth)
- **Pověření**: API klíč + API secret (zakódované v Base64)
- **Prostředí**: Podpora demo (`demo.trading212.com`) a live (`live.trading212.com`) prostředí

### Základní URL
```
https://live.trading212.com/api/v0
https://demo.trading212.com/api/v0
```

### Endpointy

#### GET /equity/account/summary
**Účel**: Test připojení k API a získání základních informací o účtu

**Parametry**: Žádné

**Příklad odpovědi**:
```json
{
  "id": 12345678,
  "currency": "EUR",
  "totalValue": 12543.67,
  "cash": {
    "availableToTrade": 5432.10,
    "reservedForOrders": 123.45,
    "inPies": 678.90
  },
  "investments": {
    "currentValue": 7109.32,
    "totalCost": 6543.21,
    "realizedProfitLoss": 432.10,
    "unrealizedProfitLoss": 134.01
  }
}
```

**Chybové kódy**:
- `401`: Neplatné API pověření
- `429`: Překročen limit požadavků

#### GET /equity/history/orders
**Účel**: Načtení historie obchodů s podporou stránkování

**Parametry**:
- `timeFrom` (volitelné): Počáteční datum ve formátu ISO 8601 (např. `2023-01-01T00:00:00Z`)
- `timeTo` (volitelné): Koncové datum ve formátu ISO 8601

**Příklad odpovědi**:
```json
{
  "items": [
    {
      "id": "order123",
      "ticker": "AAPL",
      "quantity": 10,
      "price": 150.25,
      "time": "2023-01-15T10:30:00Z",
      "type": "BUY"
    }
  ],
  "nextPagePath": "/api/v0/equity/history/orders?page=2"
}
```

#### POST /equity/history/exports
**Účel**: Požadavek na generování CSV reportu s obchodní historií

**Parametry v těle požadavku**:
```json
{
  "timeFrom": "2023-01-01T00:00:00Z",
  "timeTo": "2023-12-31T23:59:59Z",
  "dataIncluded": {
    "includeOrders": true,
    "includeTransactions": true,
    "includeDividends": true,
    "includeInterest": true
  }
}
```

**Příklad odpovědi**:
```json
{"reportId": 987654}
```

#### GET /equity/history/exports
**Účel**: Kontrola stavu generování CSV reportů

**Příklad odpovědi**:
```json
[
  {
    "reportId": 987654,
    "status": "Finished",
    "downloadLink": "https://trading212.com/download/report123.csv"
  }
]
```

**Stavy reportu**:
- `Queued`: Požadavek přijat, čeká se na zpracování
- `Processing`: Generování reportu probíhá
- `Running`: Generování reportu běží
- `Finished`: Report připraven ke stažení
- `Failed`: Generování selhalo
- `Canceled`: Generování zrušeno

### Omezení rychlosti
- **Obecné volání** (/equity/account/summary, /equity/history/orders): 6 požadavků za minutu
- **CSV exporty** (POST /equity/history/exports): 1 požadavek za 30 sekund
- **Kontrola stavu** (GET /equity/history/exports): 1 požadavek za minutu

### Integrace v kódu
- `Trading212ApiClient.java`: Klient pro základní API volání s rate limiting
- `Trading212CsvClient.java`: Specializovaný klient pro CSV reporty
- `Trading212Importer.java`: Import obchodů z API nebo CSV
- `Trading212DebugStorage.java`: Ukládání odpovědí pro debugging

### Konfigurace
API klíče se nastavují v `SettingsWindow.java`:
- Trading212 API Key
- Trading212 API Secret
- Volba demo/live prostředí

## ČNB (Česká národní banka) API

### Účel
Načítání oficiálních kurzů měn z České národní banky pro daňové výpočty a výpočet "jednotného kurzu" podle pokynů Ministerstva financí ČR.

### Autentifikace
Žádná autentifikace nevyžadována - API je veřejně dostupné.

### Základní URL
```
https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/
```

### Endpointy

#### denni_kurz.txt
**Účel**: Denní kurzy pro konkrétní datum

**Parametry**:
- `date`: Datum ve formátu DD.MM.YYYY

**Příklad URL**: `denni_kurz.txt?date=15.01.2024`

**Formát odpovědi**:
```
15.01.2024
země|měna|množství|kód|kurz
EMU|euro|1|EUR|25,445
USA|dolar|1|USD|22,850
```

#### rok.txt
**Účel**: Roční kurzy pro všechny pracovní dny v roce

**Parametry**:
- `rok`: Rok ve formátu YYYY

**Příklad URL**: `rok.txt?rok=2024`

**Formát odpovědi**:
```
Datum|1 AUD|1 BGN|1 BRL|1 CAD|...|100 HUF|...
01.01.2024|14,845|13,622|4,846|17,050|...|6,144|...
02.01.2024|14,812|13,595|4,832|17,020|...|6,124|...
```

### Výpočet jednotného kurzu
**Algoritmus**: Aritmetický průměr z kurzů posledních pracovních dnů každého měsíce

**Požadavky na platnost**:
- Minimálně 10 měsíců s dostupnými daty
- Automatické vyhledávání nejbližšího pracovního dne při víkendech/svátcích
- Výsledek zaokrouhlen na 2 desetinná místa

### Cache mechanismus
- Denní kurzy se cachují v paměti pro minimalizaci API volání
- Klíč cache: `CURRENCY|YYYY-MM-DD`
- Cache se čistí při restartu aplikace nebo explicitně

### Integrace v kódu
- `CurrencyRateFetcher.java`: Hlavní třída pro načítání kurzů
  - `fetchJednotnyKurz()`: Výpočet jednotného kurzu
  - `fetchDailyRate()`: Načtení denního kurzu
  - `fetchAnnualDailyRates()`: Hromadné načtení ročních dat
- `RateManagementDialog.java`: UI pro správu kurzů
- Automatické načítání při výpočtu daní v `ComputeWindow.java`

### Chybové situace
- **Chybějící data**: Vrácení `null` pro víkendy/sváteční dny
- **Neplatná odpověď**: Automatické opakování s předchozími dny
- **Timeout**: 10 sekund timeout na požadavek
- **Neplatný formát**: Přeskočení neplatných řádků

## Bezpečnostní poznámky
- API klíče Trading212 se ukládají v nastavení aplikace
- Žádné API klíče se neukládají do verzovacího systému
- CNB API nevyžaduje autentifikaci
- Všechny HTTPS komunikace používají výchozí Java truststore

## Troubleshooting
- **Trading212 API chyby**: Kontrola API klíčů a rate limitů
- **CNB API nedostupnost**: Automatické opakování s předchozími dny
- **Debug informace**: Trading212 odpovědi se ukládají do `Trading212DebugStorage`

## Interactive Brokers (IBKR) Flex Web Service

### Účel
Načítání kompletních historických obchodních dat z Interactive Brokers účtu přes Flex Web Service API pro daňové účely. Umožňuje import dat za libovolný počet let zpětně.

### Autentifikace
- **Metoda**: Token-based autentifikace
- **Token**: Vygenerováno v IBKR Client Portal → Settings → Flex Web Service Configuration
- **Prostředí**: Cloud (není vyžadován lokální Gateway ani TWS)
- **Verze API**: Flex Web Service v3

### Základní URL
```
https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService
```

### Endpointy

#### SendRequest - Generování reportu
**Účel**: Požadavek o vygenerování Flex Query reportu pro specifické datumové rozsahy.

**Parametry**:
- `t` (vyžadováno): Flex Token vytvořený v Client Portal
- `q` (vyžadováno): Query ID - identifikátor předkonfigurovaného Flex Query
- `v` (vyžadováno): Verze Flex Web Service - vždy hodnota `3`
- `startDate` (volitelné): Počáteční datum ve formátu `YYYYMMDD`
- `endDate` (volitelné): Koncové datum ve formátu `YYYYMMDD`

**Příklad URL**:
```
https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/SendRequest?t={Token}&q={QueryID}&v=3&startDate=20210101&endDate=20211231
```

**Příklad odpovědi**:
```xml
<FlexStatementResponse timestamp='19 January, 2026 09:41 AM EST'>
<Status>Success</Status>
<ReferenceCode>123456789012345</ReferenceCode>
<Url>https://gdcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement</Url>
</FlexStatementResponse>
```

**Poznámka**: IBKR může vracet odpověď v XML formátu (novější) nebo plain text formátu (starší). Aplikace podporuje oba formáty.

#### GetStatement - Stažení reportu
**Účel**: Stažení vygenerovaného reportu ve formátu CSV nebo XML.

**Parametry**:
- `ref` (vyžadováno): Reference Code získaný z endpointu SendRequest
- `t` (vyžadováno): Flex Token (shodný jako u SendRequest)

**Příklad URL**:
```
https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement?ref={ReferenceCode}&t={Token}
```

**Příklad odpovědi**: CSV soubor obsahující sekce:
- **Trades**: Historie obchodů (nákupy, prodeje, deriváty)
- **Transaction Fees**: Poplatky za transakce
- **Cash Transactions**: Vklady, výběry, převody
- **Dividends**: Dividendové výplaty (pokud zahrnuto v query)

**Formát CSV**:
```
DateOfTrade,SettlementDate,TransactionType,Symbol,Name,Quantity,Price,Currency,Proceeds,Commission,NetProceeds,Code,Exchange
20210115,20210118,Buy,AAPL,Apple Inc.,100,150.25,USD,-15025.00,1.00,-15026.00,,NASDAQ
20210322,20210323,sell,AAPL,Apple Inc.,-100,175.50,USD,17550.00,1.00,17549.00,,NASDAQ
```

### Nastavení Flex Query (jednorázový setup v IBKR Client Portal)

**Povinné kroky před prvním importem**:

1. **Přihlášení do IBKR Client Portal**:
   - Otevřete: https://www.interactivebrokers.com/sso/Login
   - Přihlaste se svým účtem

2. **Nastavení Flex Web Service**:
   - Navigate: Menu → Settings → Flex Web Service Configuration
   - Klikněte na "Create a new Flex Web Service Token"
   - Zadejte popis tokenu (např. "StockAccounting Import")
   - Uložte a zkopírujte vygenerovaný token
   - ⚠️ Tento token je vyžadován v aplikaci pro každé API volání

3. **Vytvoření Flex Query**:
   - Navigate: Performance & Reports → Flex Queries
   - Klikněte na "Custom Flex Queries"
   - Klikněte na "+" v sekci "Trade Confirmation Flex Query Templates"
   - Zadejte název query (např. "StockAccounting Import")
   - V sekci "Sections" vyberte:
     - ✅ **Trades** (povinné)
     - ✅ **Transaction Fees** (doporučeno)
     - ✅ **Cash Transactions** (doporučeno)
     - ℹ️ **Dividends** (volitelné pro daňové účely)
     - ℹ️ **Corporate Actions** (volitelné)

4. **Konfigurace polí**:
   - U každě sekce klikněte na úroveň detailu:
     - **Basic**: Základní pole
     - **Detail**: Další pole (pro kompletní data)
   - Vyberte konkrétní pole:
     - Date Of Trade (datum obchodu)
     - Settlement Date (datum vyřízení)
     - Transaction Type (typ transakce)
     - Symbol (ticker)
     - Name (název instrumentu)
     - Quantity (množství)
     - Price (cena)
     - Currency (měna)
     - Proceeds (hrubá částka pro prodeje)
     - Commission (poplatek)
     - Net Proceeds (čistá částka)
     - Code (kód pro deriváty - Assignment, Exercise, Expired)
     - Exchange (burza)
   - Drag & Drop pro přesun polí v požadovaném pořadí

5. **Formát a výstup**:
   - Format: **CSV** (vyžadováno aplikací)
   - Period: **Custom** (umožňuje specifikovat přesné datumové rozsahy)
   - Uložte query → Získat **Query ID** (např. `6789012345`)
   - ⚠️ Toto Query ID je vyžadováno v aplikaci

### Omezení
- **Rate limiting**: Asynchronní generování reportů
  - Max. doba čekání: 10 minut
  - Intervál kontroly stavu: 5 sekund
- **Rozsah dat**: Neomezený historie (libovolná minulost)
- **Formát**: Podpora pouze CSV a XML
- **Požadované sekce**: Minimálně "Trades" pro import obchodů

### Použití v aplikaci
- `IBKRFlexClient.java`: HTTP klient pro komunikaci s Flex Web Service
  - requestReport(): Požadavek o vygenerování reportu
  - checkReportStatus(): Kontrola stavu generování
  - downloadCsvReport(): Stažení vygenerovaného reportu
  - requestAndDownloadReport(): Plný workflow s pollingem
- `IBKRFlexParser.java`: Parser IBKR Flex CSV do Transaction objektů
  - Podpora všech 5 sekcí (Trades, Transaction Fees, Cash Transactions, Dividends, Corporate Actions)
  - Flexibilní detekce sloupců v CSV hlavičce
- `IBKRFlexImporter.java`: Hlavní orchestrátor importu
  - Podpora víceletního importu s checkboxy pro výběr let
  - Caching importovaných let (uložení do `~/.ibkr_flex`)
  - Automatické načítání z cache při opětovném importu
- `IBKRFlexCache.java`: Správa cache importovaných CSV souborů
  - Per-year CSV soubory: `ibkr_flex_{year}.csv`
  - Automatické vytvoření cache adresáře
- `IBKRFlexImportWindow.java`: Dialog pro import IBKR Flex dat
  - Checkboxy pro jednotlivé roky (2021-2025, automaticky přidávány nové)
  - Pro současný rok: import od začátku roku do dnešního data
  - Pole pro Query ID a Flex Token
  - Integrace s Settings pro uložení pověření
- `IBKRFlexProgressDialog.java`: Progress monitoring (similar to Trading212)
  - Countdown timer: "Čekání: X s (Y s zbývá)"
  - Real-time status aktualizace
  - Zrušitelné tlačítko
  - Automatické ukládání do cache po stažení
- `IBKRFlexPreviewTable.java`: Preview transakcí před importem
  - Read-only tabulka souborými transakcemi
  - Checkboxy pro řádkové vybření
  - Tlačítka "Importovat vybrané" a "Importovat vše"
  - Filtr dle typu transakce (Nákup/Prodej, Dividenda, Poplatek, Převod)

### UI integrace
- **SettingsWindow.java**: Samostatná karta "IBKR Flex" pro IBKR pověření
  - Pole pro Query ID a Flex Token
  - Uložení přes Preferences API (`IBKR_FLEX_QUERY_ID`, `IBKR_FLEX_TOKEN`)
  - Password field pro Flex Token (z bezpečnostních důvodů)
  - Tlačítko "Otestovat připojení" pro validaci pověření
  - Validace při ukládání
- **MainWindow.java**: Položka menu "Import z IBKR Flex..."
  - Otevření IBKRFlexImportWindow
  - Předání importovaných transakcí do hlavní tabulky
  - Aktualizace UI po importu

### Chybové situace
- **Chybějící Token**: Aplikace vyzve k zadání tokenu v nastavení
  - **Neplatný Query ID**: Ověřte správnost Query ID v Client Portal
  - **Timeout požadavku**: Report se negeneroval do 10 minut (zkuste znovu)
  - **Prázdný CSV**: Report neobsahuje žádná data pro zadaný rozsah
  - **Nesprávné datumové formáty**: IBKR používá formát `YYYYMMDD`

### Troubleshooting
- **Token nefunguje**: Zkontrolujte, že byl vytvořen jako "Flex Web Service Token" (ne jako API token)
- **Query ID nenalezen**: Ověřte, že query bylo uloženo v sekci "Trade Confirmation Flex Query Templates"
- **Report se negeneruje**: Zkontrolujte IBKR status stránku pro případné výpadky
- **CSV se nenačítá**: Zkontrolujte správnost Reference Code a Token

## Integrace v nastaveních (manuální úprava)

Aplikace má samostatnou kartu "IBKR Flex" v nastaveních (Tools → Settings) obsahující:

- **IBKR Query ID**: Textové pole pro zadání Query ID z Client Portal
- **IBKR Flex Token**: Password pole pro zadání Flex Token (z bezpečnostních důvodů skryté)
- **Otestovat připojení**: Tlačítko pro validaci pověření před importem

Nastavení se ukládají automaticky při použití aplikace a jsou dostupná ve všech importních dialozích.

## Interactive Brokers (IBKR) TWS API (lokální TWS)

### Účel
Napojení na lokálně spuštěný Trader Workstation (TWS) přes TWS API (socket) pro načtení aktuálních pozic (portfolio holdings) a porovnání s vypočteným stavem v okně „Stav účtu“.

### Požadavky
- Spuštěný TWS na lokálním počítači
- Povolený API přístup v TWS:
  - `Configure → Settings → API → Settings`
  - povolit „Enable ActiveX and Socket Clients“
  - nastavit port (typicky 7496 live / 7497 paper)
  - povolit lokální IP (127.0.0.1)

### Konfigurace v aplikaci
Nastavení se ukládá do Preferences a nastavuje se v aplikaci v `Tools → Settings → IBKR TWS API`:

- `twsHost` (default `127.0.0.1`)
- `twsPort` (default `7496`)
- `twsClientId` (default `101`)
- `twsTimeoutSeconds` (default `15`)
- `twsDefaultAccount` (default prázdné = „Součet všech“)

Součástí karty je i tlačítko **„Otestovat připojení“**, které provede krátký dotaz `reqPositions()` a ověří, že aplikace umí z TWS načíst pozice.

### Použití v aplikaci
- `AccountStateWindow.java`: tlačítko „Načíst z TWS“ a sloupec „TWS“ s porovnáním
- `IbkrTwsPositionsClient.java`: klient pro `reqPositions()` (načítá pouze `STK` pozice)

Workflow:
1. Otevřete `Stav účtu`.
2. Klikněte na **„Načíst z TWS“**.
3. Aplikace načte pozice z TWS a doplní sloupec **„TWS“** (množství).
4. Řádky se barevně zvýrazní:
   - zeleně = množství sedí
   - červeně = množství nesedí
5. Pokud TWS vrátí více účtů, lze vybrat konkrétní účet nebo „Součet všech“.
6. Ve spodním řádku se zobrazí krátké shrnutí (počet tickerů, počet nesouladů, tickery pouze v TWS).

### Poznámky
- `clientId` musí být unikátní (pokud je již používán jiným klientem, TWS připojení odmítne)
- Pokud TWS vrátí více účtů, aplikace nabídne výběr účtu nebo „Součet všech“

Omezení a kompatibilita:
- Porovnání je implementované pouze pro akcie (`STK`) a porovnává pouze množství (žádné ceny/avgCost/PNL).
- Pro robustní spárování tickerů se zkouší běžné varianty (např. `BRK.B` vs `BRK B`, tečka/mezera/pomlčka).
- IB TWS API od verze 10.42 používá Protobuf; aplikace vyžaduje runtime knihovnu `protobuf-java`.
  - Použitá verze v projektu: `libjar/protobuf-java-4.33.4.jar`.
