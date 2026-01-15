# Přispívání do projektu (Contributing)

## CI/CD (Gitea Actions)

### Nastavení vzdálených repozitářů
Tento projekt používá duální vzdálené repozitáře pro správu verzí a automatické sestavení:
- **Primární (Gitea)**: `ssh://git@192.168.88.97:222/kadu/stock_accounting`
- **Sekundární (GitHub)**: `https://github.com/kaaduu/StockAccounting.git`

### Workflow vývoje
1. **Lokální vývoj**: Práce na větvi `modernization-java21`
2. **Commit změn**: `git add . && git commit -m "popis změn"`
3. **Push do Gitea**: `git push gitea modernization-java21` (spustí automatické sestavení)
4. **Volitelný push do GitHub**: `git push origin modernization-java21`

### Vytváření vydání
```bash
# 1. Vytvoření tagu s automatickým datem
./create-release-tag.sh              # Vytvoří vYYYY.MM.DD
./create-release-tag.sh feature-x    # Vytvoří vYYYY.MM.DD-feature-x

# 2. Push tagů (spustí release workflow)
git push gitea --tags               # Primární repozitář
git push origin --tags              # Sekundární repozitář (volitelné)
```

### Gitea Actions Workflow
- **Automatické sestavení**: Spouští se při každém push do Gitea
- **Vytváření vydání**: Tag spustí automatické vytvoření release s ZIP archivem
- **Java 17 kompilace**: Používá JDK 17 pro konzistentní bytecode (`--release 17`)
- **Testování**: Automatické spuštění build.sh pro ověření funkčnosti

## Sestavení projektu

### Z příkazové řádky
Projekt lze sestavit bez IDE pomocí přiložených skriptů:

```bash
# Sestavení aplikace
./build.sh

# Spuštění sestavené aplikace
./run.sh

# Test běhu (automatická kontrola Java verze)
./run.sh --version
```

### S NetBeans IDE
1. **Stáhněte NetBeans** s podporou Java (doporučeno s JDK 17+)
2. **Nastavte JDK platformu** (doporučeno Zulu JDK na Linuxu)
3. **Konfigurace classpath**:
   - Otevřete Project Properties → Libraries → Compile tab
   - Přidejte následující JAR soubory:
     - `libjar/DatePicker.jar`
     - `libjar/swing-layout-1.0.4.jar`
     - `libjar/jcalendar-1.4.jar`

### Systémové požadavky pro vývoj
- **Java 17+ JDK** (nejlépe Adoptium Temurin nebo Zulu)
- **Git** pro správu verzí
- **NetBeans IDE** (volitelné, ale doporučené)

## Architektura kódu

### Klíčové komponenty

#### Core Classes
- **`MainWindow.java`**: Hlavní UI a obchodní logika aplikace
- **`Settings.java`**: Konfigurace aplikace a persistence dat
- **`TransactionSet.java`**: Zpracování a správa obchodních dat
- **`ComputeWindow.java`**: Výpočty zisků, ztrát a daňových optimalizací

#### Currency Management
- **`CurrencyRateFetcher.java`**: Načítání kurzů z ČNB API
- **`SettingsWindow.java`**: UI pro správu kurzů a nastavení
- **`RateManagementDialog.java`**: Hierarchická správa denních kurzů

#### Import/Export System
- **`ImportWindow.java`**: UI pro výběr formátu importu
- Implementace v `imp/` složce:
  - `ImportFio.java` - FIO banka CSV
  - `ImportT212.java` - Trading 212 Invest
  - `ImportRevolutCSV.java` - Revolut CSV
  - `ImportIB.java` - Interactive Brokers

### Persistence vrstva

#### Denní kurzy
- **Úložiště**: Java Preferences API s individuálními klíči
- **Klíče**: `"dailyRate.CURRENCY|YYYY-MM-DD"` → `"RATE"`
- **Výhody**: Žádné limity velikosti, přežívá restart aplikace
- **Migrace**: Automatická konverze ze starého file-based formátu

#### Nastavení aplikace
- **Úložiště**: Java Preferences API
- **Klíče**: Standardní preference názvy
- **Export**: Automatické zálohování při kritických operacích

### Perspektiva kurzů měn

#### Denní kurzy
- **Zdroj**: Česká národní banka (ČNB) API
- **Rozlišení**: Jedno datum = jeden kurz
- **Přesnost**: Maximální přesnost pro historické výpočty
- **Načítání**: Smart detekce měn z existujících obchodů

#### Sjednocené kurzy
- **Zdroj**: Výpočet z denních kurzů podle MFČR pokynů
- **Vzorec**: Aritmetický průměr 12 měsíčních koncových kurzů
- **Zaokrouhlení**: Na 2 desetinná místa podle daňových požadavků
- **Účel**: Oficiální kurz pro daňové účely

### Import formáty

#### Podporované brokery
- **Interactive Brokers**: TradeLog (TLG), FlexQuery (CSV)
- **Trading 212 Invest**: CSV export s podporou GBX→GBP konverze
- **Revolut**: CSV export s rozpoznáním typů transakcí
- **FIO banka**: CSV export obchodů
- **BrokerJet**: HTML export (legacy podpora)

#### Validace a zpracování
- **Formátová kontrola**: Automatické ověření struktury souborů
- **Kódování**: UTF-8 s fallback na Windows-1250 pro starší exporty
- **Chybové hlášky**: Uživatelsky přívětivé chybové zprávy
- **Částečné akcie**: Plná podpora frakčních množství

### Export možnosti

#### CSV export
- **Oddělení měny**: Možnost oddělit měnu do samostatného sloupce
- **Kódování**: UTF-8 pro kompatibilitu s Excel/OpenOffice
- **Oddělovače**: Konfigurovatelné (čárka, středník)

#### FIO formát
- **Účel**: Import na kacka.baldsoft.com pro daňové výpočty
- **Filtrace**: Pouze obchody typu "Cenný papír"
- **Validace**: Kontrola formátu před exportem

#### HTML export
- **Barevné zvýraznění**: Zisky/ztráty různými barvami
- **Tabulková struktura**: Snadno čitelná tabulka
- **CSS styly**: Moderní vzhled s responzivním designem

## Verzování a vydávání

### Formát tagů
Projekt používá **date-based versioning** podle `create-release-tag.sh`:
- **Standard**: `vYYYY.MM.DD` (např. `v2026.01.15`)
- **S příponou**: `vYYYY.MM.DD-feature` (např. `v2026.01.15-daily-rates`)

### Informace o verzi
Aplikace zobrazuje verzi automaticky pomocí git describe:
- **Tagged releases**: Zobrazuje název tagu (např. `v2026.01.15`)
- **Development builds**: Zobrazuje `dev-build` pokud git není dostupný
- **Runtime info**: Verze Java, OS, dodavatel JVM

### Poznámky k verzování
- Vyhněte se semantic versioning (v1.0.0) - používejte date-based formát
- Tag vytvoří automatické release s ZIP archivem
- Gitea Actions zajišťují konzistentní build prostředí

## Historie změn
Detailní historie všech změn viz [CHANGES.md](CHANGES.md).

## Kontakt a podpora
- **GitHub Issues**: https://github.com/kaaduu/StockAccounting/issues
- **Gitea Repository**: Primární repozitář pro vývoj
- **Původní dokumentace**: http://lemming.ucw.cz/ucetnictvi/

---

**Tento projekt vítá příspěvky od komunity!** Před odesláním pull requestu se prosím seznamte s workflow vývoje a testujte změny důkladně.