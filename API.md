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