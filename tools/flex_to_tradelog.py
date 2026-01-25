#!/usr/bin/env python3
"""Generate TradeLog-style exports and mapping from IBKR Flex CSV.

This script:
- Reads IBKR Flex CSV with multiple header sections.
- Extracts TRADES section rows (the big header with TransactionType/IBOrderID/etc).
- Keeps only trade-like rows: ExchTrade, BookTrade, FracShare.
- Groups by IBOrderID and consolidates fills.
- Writes:
  - TradeLog-style .tlg with STOCK_TRANSACTIONS and OPTION_TRANSACTIONS
  - Mapping CSV (one row per IBOrderID group), optionally compared to a reference .tlg

It is intended as a diagnostic / validation tool.
"""

from __future__ import annotations

import argparse
import csv
import os
import re
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple


TRADELIKE_TX_TYPES = {"ExchTrade", "BookTrade", "FracShare"}


def dec(s: str) -> Decimal:
    if s is None:
        return Decimal("0")
    st = str(s).strip()
    if st == "" or st.lower() == "null":
        return Decimal("0")
    try:
        return Decimal(st)
    except InvalidOperation:
        return Decimal("0")


def fmt_decimal(x: Decimal, max_decimals: int = 6) -> str:
    # normalize without scientific notation
    q = Decimal(10) ** (-max_decimals)
    y = x.quantize(q) if max_decimals >= 0 else x
    s = format(y, "f")
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s if s != "-0" else "0"


def quant6(x: Decimal) -> Decimal:
    return x.quantize(Decimal("0.000001"))


def quant5(x: Decimal) -> Decimal:
    return x.quantize(Decimal("0.00001"))


def quant4(x: Decimal) -> Decimal:
    return x.quantize(Decimal("0.0001"))


def fmt_decimal_fixed(x: Decimal, decimals: int = 2) -> str:
    q = Decimal(10) ** (-decimals)
    y = x.quantize(q)
    s = format(y, "f")
    return s if s != "-0.00" else "0.00"


def fmt_qty_tradelog(x: Decimal) -> str:
    """Mimic IB TradeLog quantity formatting.

    Most trades use 2 decimals, but fractional shares keep more precision.
    """
    xq = quant6(x)
    # If fractional component exists, keep up to 4 decimals (TradeLog commonly uses 4 for fracshares)
    if xq != xq.to_integral_value():
        q = Decimal("0.0001")
        return fmt_decimal_fixed(xq.quantize(q), 4)
    return fmt_decimal_fixed(xq, 2)


def parse_flex_datetime(s: str) -> Optional[datetime]:
    if s is None:
        return None
    st = str(s).strip()
    if not st:
        return None

    # Common compact format: YYYYMMDD;HHMMSS
    for fmt in (
        "%Y%m%d;%H%M%S",
        "%Y%m%d;%H%M",
        "%Y%m%d %H%M%S",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d;%H:%M:%S",
    ):
        try:
            return datetime.strptime(st, fmt)
        except ValueError:
            pass
    return None


def is_trade_header(header: Sequence[str]) -> bool:
    # TRADES section has these canonical columns.
    required = {
        "AssetClass",
        "Symbol",
        "TransactionType",
        "DateTime",
        "Quantity",
        "TradePrice",
        "Buy/Sell",
        "IBOrderID",
    }
    return required.issubset(set(header))


def split_notes_codes(value: str) -> List[str]:
    if value is None:
        return []
    st = str(value).strip()
    if not st:
        return []
    # Notes/Codes can contain ';' or ','
    parts = re.split(r"[;,]", st)
    return [p.strip() for p in parts if p.strip()]


def record_type_for_asset_class(asset_class: str) -> str:
    ac = (asset_class or "").strip().upper()
    if ac == "STK":
        return "STK_TRD"
    if ac in {"OPT", "FOP"}:
        return "OPT_TRD"
    if ac == "FUT":
        return "FUT_TRD"
    if ac == "WAR":
        return "WAR_TRD"
    if ac in {"CASH", "FX"}:
        return "CASH_TRD"
    return "STK_TRD"


@dataclass
class FlexFill:
    asset_class: str
    symbol: str
    description: str
    currency: str
    fx_rate_to_base: str
    txn_type: str
    dt_raw: str
    dt: Optional[datetime]
    exchange: str
    qty: Decimal
    multiplier: Decimal
    price: Decimal
    trade_money: Decimal
    proceeds: Decimal
    commission: Decimal
    buy_sell: str
    open_close: str
    notes_codes: str
    transaction_id: str
    ib_order_id: str


@dataclass
class Consolidated:
    ib_order_id: str
    asset_class: str
    symbol: str
    description: str
    currency: str
    fx_rate_to_base: str
    buy_sell: str
    open_close: str
    status: str
    date: str  # YYYYMMDD
    time: str  # HH:MM:SS
    exchange: str
    qty_sum: Decimal
    multiplier: Decimal
    price_wavg: Decimal
    trade_money_sum: Decimal
    proceeds_sum: Decimal
    commission_sum: Decimal
    transaction_ids: List[str]
    fill_count: int
    notes_codes_set: List[str]

    def record_type(self) -> str:
        return record_type_for_asset_class(self.asset_class)

    def to_tradelog_fields(self) -> List[str]:
        # Format matches the classic .tlg layout used by ImportIBTradeLog:
        # TYPE|TxnID|Ticker|Description|Exchange|Action|Status|YYYYMMDD|HH:MM:SS|CCY|Qty|Multiplier|Price|TradeMoney|Commission|FXRate
        rtype = self.record_type()
        action = derive_tradelog_action(rtype, self.buy_sell, self.open_close)
        status = self.status

        qty_s = fmt_qty_tradelog(self.qty_sum)
        mult_s = fmt_decimal_fixed(self.multiplier, 2)
        price_s = fmt_decimal(self.price_wavg, 6)
        money_s = fmt_decimal(self.trade_money_sum, 6)
        comm_s = fmt_decimal(self.commission_sum, 6)
        fx_s = fmt_decimal(dec(self.fx_rate_to_base), 6) if self.fx_rate_to_base else ""

        return [
            self.record_type(),
            self.ib_order_id,
            self.symbol,
            self.description,
            self.exchange,
            action,
            status,
            self.date,
            self.time,
            self.currency,
            qty_s,
            mult_s,
            price_s,
            money_s,
            comm_s,
            fx_s,
        ]


def derive_tradelog_action(record_type: str, buy_sell: str, open_close: str) -> str:
    bs = (buy_sell or "").strip().upper()
    oc = (open_close or "").strip().upper()

    # Cash trades in TradeLog use TOOPEN actions.
    if record_type == "CASH_TRD":
        if bs == "BUY":
            return "BUYTOOPEN"
        if bs == "SELL":
            return "SELLTOOPEN"
        return ""

    if oc == "O":
        if bs == "BUY":
            return "BUYTOOPEN"
        if bs == "SELL":
            return "SELLTOOPEN"
    if oc == "C":
        if bs == "BUY":
            return "BUYTOCLOSE"
        if bs == "SELL":
            return "SELLTOCLOSE"

    # Fallback: mimic what you typically see in your exported TradeLog.
    if bs == "BUY":
        return "BUYTOOPEN"
    if bs == "SELL":
        return "SELLTOCLOSE"
    return ""


def derive_tradelog_status(
    record_type: str, buy_sell: str, open_close: str, notes_codes: Iterable[str]
) -> str:
    if record_type == "CASH_TRD":
        return ""

    oc = (open_close or "").strip().upper()
    if oc in ("O", "C"):
        base = oc
    else:
        # Infer from action fallback
        bs = (buy_sell or "").strip().upper()
        base = "O" if bs == "BUY" else ("C" if bs == "SELL" else "")

    extras: List[str] = []
    for c in notes_codes:
        cc = (c or "").strip()
        if not cc:
            continue
        # Common junk marker in Flex: "P" (present on many fills); TradeLog usually doesn't include it.
        if cc == "P":
            continue
        if cc not in extras:
            extras.append(cc)

    if extras and base:
        return base + ";" + ";".join(extras)
    if base:
        return base
    return ";".join(extras)


def read_flex_fills(flex_csv_path: Path) -> List[FlexFill]:
    fills: List[FlexFill] = []

    with flex_csv_path.open(newline="") as f:
        r = csv.reader(f)
        header: Optional[List[str]] = None
        idx: Dict[str, int] = {}
        in_trade_section = False

        for row in r:
            if not row:
                continue
            # Section header can repeat.
            if row and row[0].strip('"') == "ClientAccountID":
                header = row
                idx = {h: i for i, h in enumerate(header)}
                in_trade_section = is_trade_header(header)
                continue
            if header is None:
                # Skip any preamble.
                continue

            if not in_trade_section:
                continue

            txn_type = row[idx["TransactionType"]].strip()
            if txn_type not in TRADELIKE_TX_TYPES:
                continue

            ib_order_id = row[idx["IBOrderID"]].strip()
            if not ib_order_id:
                continue

            dt_raw = row[idx["DateTime"]].strip()
            dt = parse_flex_datetime(dt_raw)

            fills.append(
                FlexFill(
                    asset_class=row[idx["AssetClass"]].strip(),
                    symbol=row[idx["Symbol"]].strip(),
                    description=row[idx["Description"]].strip(),
                    currency=row[idx["CurrencyPrimary"]].strip(),
                    fx_rate_to_base=row[idx["FXRateToBase"]].strip(),
                    txn_type=txn_type,
                    dt_raw=dt_raw,
                    dt=dt,
                    exchange=row[idx["Exchange"]].strip(),
                    qty=dec(row[idx["Quantity"]]),
                    multiplier=dec(row[idx.get("Multiplier", -1)])
                    if idx.get("Multiplier", -1) >= 0
                    else Decimal("1"),
                    price=dec(row[idx["TradePrice"]]),
                    trade_money=dec(row[idx["TradeMoney"]]),
                    proceeds=dec(row[idx["Proceeds"]]),
                    commission=dec(row[idx["IBCommission"]]),
                    buy_sell=row[idx["Buy/Sell"]].strip(),
                    open_close=row[idx.get("Open/CloseIndicator", -1)].strip()
                    if idx.get("Open/CloseIndicator", -1) >= 0
                    else "",
                    notes_codes=row[idx.get("Notes/Codes", -1)].strip()
                    if idx.get("Notes/Codes", -1) >= 0
                    else "",
                    transaction_id=row[idx.get("TransactionID", -1)].strip()
                    if idx.get("TransactionID", -1) >= 0
                    else "",
                    ib_order_id=ib_order_id,
                )
            )

    return fills


def consolidate(fills: List[FlexFill]) -> List[Consolidated]:
    by_order: Dict[str, List[FlexFill]] = {}
    for f in fills:
        by_order.setdefault(f.ib_order_id, []).append(f)

    result: List[Consolidated] = []
    for oid, group in by_order.items():
        # Sort by timestamp (fallback to dt_raw)
        group_sorted = sorted(group, key=lambda x: (x.dt or datetime.max, x.dt_raw))
        first = group_sorted[0]

        # Earliest datetime for output
        dt0 = first.dt
        if dt0 is None:
            # Fallback: take TradeDate from dt_raw if present as YYYYMMDD
            # If it's empty, keep placeholders.
            date = first.dt_raw.split(";")[0] if first.dt_raw else ""
            time = "00:00:00"
        else:
            date = dt0.strftime("%Y%m%d")
            time = dt0.strftime("%H:%M:%S")

        exchanges = sorted({x.exchange for x in group_sorted if x.exchange})
        exchange = ",".join(exchanges) if exchanges else "--"

        qty_sum = sum((x.qty for x in group_sorted), Decimal("0"))
        abs_qty_sum = sum((abs(x.qty) for x in group_sorted), Decimal("0"))

        # Weighted average price by abs(quantity)
        if abs_qty_sum != 0:
            wsum = sum((abs(x.qty) * x.price for x in group_sorted), Decimal("0"))
            price_wavg = wsum / abs_qty_sum
        else:
            price_wavg = first.price

        trade_money_sum = sum((x.trade_money for x in group_sorted), Decimal("0"))
        proceeds_sum = sum((x.proceeds for x in group_sorted), Decimal("0"))
        commission_sum = sum((x.commission for x in group_sorted), Decimal("0"))

        # Multiplier should be stable across fills; take the first non-zero.
        mult = Decimal("0")
        for x in group_sorted:
            if x.multiplier != 0:
                mult = x.multiplier
                break
        if mult == 0:
            mult = Decimal("1")

        bs_set = {x.buy_sell.strip().upper() for x in group_sorted if x.buy_sell}
        buy_sell = (sorted(bs_set)[0] if bs_set else "").upper()

        oc_set = {x.open_close.strip().upper() for x in group_sorted if x.open_close}
        # Open/CloseIndicator isn't consistently populated; if mixed, prefer first fill.
        open_close = (
            first.open_close.strip().upper()
            if first.open_close
            else (sorted(oc_set)[0] if oc_set else "")
        )

        codes: List[str] = []
        for x in group_sorted:
            for c in split_notes_codes(x.notes_codes):
                if c not in codes:
                    codes.append(c)

        # Record type depends on AssetClass.
        rtype = Consolidated(
            ib_order_id=oid,
            asset_class=first.asset_class,
            symbol=first.symbol,
            description=first.description,
            currency=first.currency,
            fx_rate_to_base=first.fx_rate_to_base,
            buy_sell=buy_sell,
            open_close=open_close,
            status="",
            date=date,
            time=time,
            exchange=exchange,
            qty_sum=qty_sum,
            multiplier=mult,
            price_wavg=price_wavg,
            trade_money_sum=trade_money_sum,
            proceeds_sum=proceeds_sum,
            commission_sum=commission_sum,
            transaction_ids=[],
            fill_count=0,
            notes_codes_set=[],
        ).record_type()

        # FXRate in TradeLog appears to be a month-level constant (e.g. 24.408 for 2025-01 USD).
        # For comparison against IB TradeLog exports, we later override fx_rate at write-time
        # based on month/currency. Here we keep the first fill's FXRateToBase for diagnostics.
        status = derive_tradelog_status(rtype, buy_sell, open_close, codes)

        tx_ids = [x.transaction_id for x in group_sorted if x.transaction_id]
        notes_codes_set = codes

        result.append(
            Consolidated(
                ib_order_id=oid,
                asset_class=first.asset_class,
                symbol=first.symbol,
                description=first.description,
                currency=first.currency,
                fx_rate_to_base=first.fx_rate_to_base,
                buy_sell=buy_sell,
                open_close=open_close,
                status=status,
                date=date,
                time=time,
                exchange=exchange,
                qty_sum=qty_sum,
                multiplier=mult,
                price_wavg=price_wavg,
                trade_money_sum=trade_money_sum,
                proceeds_sum=proceeds_sum,
                commission_sum=commission_sum,
                transaction_ids=tx_ids,
                fill_count=len(group_sorted),
                notes_codes_set=notes_codes_set,
            )
        )

    # Stable sort: date/time, then symbol, then order id.
    def sort_key(x: Consolidated) -> Tuple[str, str, str, str]:
        return (x.date or "", x.time or "", x.symbol or "", x.ib_order_id)

    result.sort(key=sort_key)
    return result


def parse_tradelog(tlg_path: Path) -> Dict[str, Dict[str, str]]:
    # key: TradeLog "transactionId" (field #2)
    res: Dict[str, Dict[str, str]] = {}
    section = ""
    with tlg_path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line in (
                "ACCOUNT_INFORMATION",
                "STOCK_TRANSACTIONS",
                "OPTION_TRANSACTIONS",
                "WARRANT_TRANSACTIONS",
                "STOCK_POSITIONS",
                "OPTION_POSITIONS",
                "EOF",
            ):
                section = line
                continue
            if section not in (
                "STOCK_TRANSACTIONS",
                "OPTION_TRANSACTIONS",
                "WARRANT_TRANSACTIONS",
            ):
                continue
            parts = line.split("|")
            if len(parts) < 16:
                continue
            typ = parts[0]
            tid = parts[1]
            res[tid] = {
                "tlg_type": typ,
                "tlg_id": tid,
                "tlg_ticker": parts[2],
                "tlg_desc": parts[3],
                "tlg_exchange": parts[4],
                "tlg_action": parts[5],
                "tlg_status": parts[6],
                "tlg_date": parts[7],
                "tlg_time": parts[8],
                "tlg_ccy": parts[9],
                "tlg_qty": parts[10],
                "tlg_multiplier": parts[11],
                "tlg_price": parts[12],
                "tlg_trade_money": parts[13],
                "tlg_commission": parts[14],
                "tlg_fx_rate": parts[15] if len(parts) > 15 else "",
            }
    return res


def parse_tradelog_entries(tlg_path: Path) -> Dict[str, Dict[str, Any]]:
    """Parse TradeLog rows (including warrants) keyed by TxnID.

    Returns numeric fields as Decimal and includes full line.
    """

    section = ""
    res: Dict[str, Dict[str, Any]] = {}

    with tlg_path.open("r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\n")
            s = line.strip()
            if not s:
                continue

            if s in (
                "ACCOUNT_INFORMATION",
                "STOCK_TRANSACTIONS",
                "OPTION_TRANSACTIONS",
                "WARRANT_TRANSACTIONS",
                "STOCK_POSITIONS",
                "OPTION_POSITIONS",
                "EOF",
            ):
                section = s
                continue

            if section not in (
                "STOCK_TRANSACTIONS",
                "OPTION_TRANSACTIONS",
                "WARRANT_TRANSACTIONS",
            ):
                continue

            if "_TRD|" not in s:
                continue

            parts = s.split("|")
            if len(parts) < 16:
                continue

            typ = parts[0]
            tid = parts[1]

            res[tid] = {
                "type": typ,
                "id": tid,
                "ticker": parts[2],
                "desc": parts[3],
                "exchange": parts[4],
                "action": parts[5],
                "status": parts[6],
                "date": parts[7],
                "time": parts[8],
                "ccy": parts[9],
                "qty": dec(parts[10]),
                "multiplier": dec(parts[11]),
                "price": dec(parts[12]),
                "trade_money": dec(parts[13]),
                "commission": dec(parts[14]),
                "fx_rate": dec(parts[15]) if len(parts) > 15 else Decimal("0"),
                "line": s,
            }

    return res


def compare_entries(
    orig: Dict[str, Any], gen: Dict[str, Any]
) -> Tuple[List[str], List[str]]:
    """Return (hard_mismatches, soft_mismatches)."""

    hard: List[str] = []
    soft: List[str] = []

    # Identity/meta checks
    for key in ("type", "ticker", "ccy", "date", "time"):
        if str(orig.get(key, "")) != str(gen.get(key, "")):
            soft.append(key)

    # Exchange is sometimes multi-valued; treat mismatch as soft.
    if str(orig.get("exchange", "")) != str(gen.get("exchange", "")):
        soft.append("exchange")

    # Numeric checks rounded to 6 decimals.
    # FX rate differs between IB TradeLog and Flex exports; report it as soft.
    num_fields_6 = ["qty", "multiplier", "trade_money"]
    for key in num_fields_6:
        if quant6(dec(orig.get(key, "0"))) != quant6(dec(gen.get(key, "0"))):
            hard.append(key)

    # Price sometimes differs by 1e-6 due to fill-weighting/rounding.
    if quant5(dec(orig.get("price", "0"))) != quant5(dec(gen.get("price", "0"))):
        hard.append("price")

    # Commission differs due to rounding/truncation between formats.
    # Use 4 decimals which matches typical TradeLog precision.
    if quant4(dec(orig.get("commission", "0"))) != quant4(
        dec(gen.get("commission", "0"))
    ):
        hard.append("commission")

    if quant6(dec(orig.get("fx_rate", "0"))) != quant6(dec(gen.get("fx_rate", "0"))):
        soft.append("fx_rate")

    # Action/status differences are informative only.
    for key in ("action", "status"):
        if str(orig.get(key, "")) != str(gen.get(key, "")):
            soft.append(key)

    # De-dup
    hard = sorted(set(hard))
    soft = sorted(set(soft))
    return hard, soft


def write_tradelog(
    out_path: Path, consolidated: List[Consolidated], account_id: str = ""
) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)

    # TradeLog monthly exports use a month-level FXRate value per currency.
    # Derive it from the data we are writing: for each currency, take the most common
    # FXRateToBase among rows and use it for all rows in that currency.
    fx_by_ccy_counts: Dict[str, Dict[str, int]] = {}
    for c in consolidated:
        ccy = (c.currency or "").strip().upper()
        if not ccy:
            continue
        fx = (c.fx_rate_to_base or "").strip()
        if not fx:
            continue
        fx_by_ccy_counts.setdefault(ccy, {})[fx] = (
            fx_by_ccy_counts.setdefault(ccy, {}).get(fx, 0) + 1
        )

    fx_by_ccy: Dict[str, str] = {}
    for ccy, m in fx_by_ccy_counts.items():
        fx_by_ccy[ccy] = sorted(m.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]

    with out_path.open("w", encoding="utf-8", newline="\n") as f:
        f.write("ACCOUNT_INFORMATION\n")
        if account_id:
            f.write(f"ACT_INF|{account_id}|UNKNOWN|Individual|\n")
        f.write("\n\n")

        # Some months contain a dedicated warrant section in IB TradeLog.
        warrant = [c for c in consolidated if c.record_type() == "WAR_TRD"]
        if warrant:
            f.write("WARRANT_TRANSACTIONS\n")
            for c in warrant:
                # Override FX rate to month-level constant when available.
                if (c.currency or "").strip().upper() in fx_by_ccy:
                    c.fx_rate_to_base = fx_by_ccy[(c.currency or "").strip().upper()]
                f.write("|".join(c.to_tradelog_fields()) + "\n")
            f.write("\n")

        f.write("STOCK_TRANSACTIONS\n")
        for c in consolidated:
            # IB TradeLog places non-option trades here as well:
            # STK_TRD, CASH_TRD, FUT_TRD, WAR_TRD.
            if c.record_type() in ("OPT_TRD", "WAR_TRD"):
                continue
            if (c.currency or "").strip().upper() in fx_by_ccy:
                c.fx_rate_to_base = fx_by_ccy[(c.currency or "").strip().upper()]
            f.write("|".join(c.to_tradelog_fields()) + "\n")

        f.write("\n")
        f.write("OPTION_TRANSACTIONS\n")
        for c in consolidated:
            if c.record_type() != "OPT_TRD":
                continue
            if (c.currency or "").strip().upper() in fx_by_ccy:
                c.fx_rate_to_base = fx_by_ccy[(c.currency or "").strip().upper()]
            f.write("|".join(c.to_tradelog_fields()) + "\n")

        f.write("\n\nEOF\n")


def write_mapping_csv(
    out_path: Path,
    consolidated: List[Consolidated],
    tlg_index: Optional[Dict[str, Dict[str, str]]],
) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)

    base_cols = [
        "ibOrderId",
        "recordType",
        "assetClass",
        "symbol",
        "description",
        "buySell",
        "openClose",
        "status",
        "date",
        "time",
        "exchange",
        "currency",
        "fxRateToBase",
        "qtySum",
        "multiplier",
        "priceWavg",
        "tradeMoneySum",
        "proceedsSum",
        "commissionSum",
        "fillCount",
        "transactionIds",
        "notesCodesSet",
    ]

    tlg_cols: List[str] = []
    if tlg_index is not None:
        tlg_cols = [
            "tlg_type",
            "tlg_id",
            "tlg_ticker",
            "tlg_desc",
            "tlg_exchange",
            "tlg_action",
            "tlg_status",
            "tlg_date",
            "tlg_time",
            "tlg_ccy",
            "tlg_qty",
            "tlg_multiplier",
            "tlg_price",
            "tlg_trade_money",
            "tlg_commission",
            "tlg_fx_rate",
        ]

    with out_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=base_cols + tlg_cols)
        w.writeheader()
        for c in consolidated:
            row: Dict[str, Any] = {
                "ibOrderId": c.ib_order_id,
                "recordType": c.record_type(),
                "assetClass": c.asset_class,
                "symbol": c.symbol,
                "description": c.description,
                "buySell": c.buy_sell,
                "openClose": c.open_close,
                "status": c.status,
                "date": c.date,
                "time": c.time,
                "exchange": c.exchange,
                "currency": c.currency,
                "fxRateToBase": c.fx_rate_to_base,
                "qtySum": fmt_decimal(c.qty_sum, 6),
                "multiplier": fmt_decimal(c.multiplier, 6),
                "priceWavg": fmt_decimal(c.price_wavg, 10),
                "tradeMoneySum": fmt_decimal(c.trade_money_sum, 10),
                "proceedsSum": fmt_decimal(c.proceeds_sum, 10),
                "commissionSum": fmt_decimal(c.commission_sum, 10),
                "fillCount": c.fill_count,
                "transactionIds": ",".join(c.transaction_ids),
                "notesCodesSet": ",".join(c.notes_codes_set),
            }
            if tlg_index is not None:
                row.update(tlg_index.get(c.ib_order_id, {}))
            w.writerow(row)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--flex", required=True, help="Path to IBKR Flex CSV")
    ap.add_argument("--out-dir", default="out", help="Output directory")
    ap.add_argument(
        "--ref-tlg",
        default=None,
        help="Optional reference TradeLog .tlg for comparison",
    )
    ap.add_argument("--account-id", default="", help="AccountId for header (optional)")
    ap.add_argument(
        "--split-monthly",
        action="store_true",
        help="Write one TradeLog file per YYYYMM based on DateTime",
    )
    ap.add_argument(
        "--compare-dir",
        default=None,
        help="Directory with original monthly TradeLogs to compare against",
    )
    ap.add_argument(
        "--compare-values",
        action="store_true",
        help="When comparing monthly files, also compare per-ID fields (6 decimals)",
    )
    args = ap.parse_args()

    flex_path = Path(args.flex)
    out_dir = Path(args.out_dir)
    ref_tlg = Path(args.ref_tlg) if args.ref_tlg else None

    fills = read_flex_fills(flex_path)
    cons = consolidate(fills)

    tlg_index = parse_tradelog(ref_tlg) if ref_tlg else None

    if args.split_monthly:
        monthly_dir = out_dir / "tradelog_from_flex"
        monthly_dir.mkdir(parents=True, exist_ok=True)

        by_month: Dict[str, List[Consolidated]] = defaultdict(list)
        for c in cons:
            if c.date and len(c.date) >= 6:
                by_month[c.date[:6]].append(c)

        for yyyymm, items in sorted(by_month.items()):
            dst = monthly_dir / f"U15493818_{yyyymm}_{yyyymm}.tlg"
            write_tradelog(dst, items, account_id=args.account_id)

        if args.compare_dir:
            compare_dir = Path(args.compare_dir)
            report_path = monthly_dir / "monthly_compare.csv"
            mismatch_path = monthly_dir / "monthly_value_mismatches.csv"
            with report_path.open("w", encoding="utf-8", newline="") as f:
                w = csv.writer(f)
                w.writerow(
                    [
                        "month",
                        "orig_total",
                        "gen_total",
                        "missing_count",
                        "extra_count",
                        "missing_ids",
                        "extra_ids",
                    ]
                )

                for yyyymm, items in sorted(by_month.items()):
                    orig = compare_dir / f"U15493818_{yyyymm}_{yyyymm}.tlg"
                    gen = monthly_dir / f"U15493818_{yyyymm}_{yyyymm}.tlg"
                    if not orig.exists() or not gen.exists():
                        continue

                    orig_ids = set(parse_tradelog(orig).keys())
                    gen_ids = set(parse_tradelog(gen).keys())
                    missing = sorted(list(orig_ids - gen_ids))
                    extra = sorted(list(gen_ids - orig_ids))
                    w.writerow(
                        [
                            yyyymm,
                            len(orig_ids),
                            len(gen_ids),
                            len(missing),
                            len(extra),
                            ",".join(missing[:50]),
                            ",".join(extra[:50]),
                        ]
                    )

            if args.compare_values:
                with mismatch_path.open("w", encoding="utf-8", newline="") as f:
                    w = csv.writer(f)
                    w.writerow(
                        [
                            "month",
                            "txnId",
                            "mismatch_hard",
                            "mismatch_soft",
                            "original_line",
                            "generated_line",
                        ]
                    )

                    for yyyymm, items in sorted(by_month.items()):
                        orig = compare_dir / f"U15493818_{yyyymm}_{yyyymm}.tlg"
                        gen = monthly_dir / f"U15493818_{yyyymm}_{yyyymm}.tlg"
                        if not orig.exists() or not gen.exists():
                            continue

                        orig_entries = parse_tradelog_entries(orig)
                        gen_entries = parse_tradelog_entries(gen)

                        all_ids = sorted(
                            set(orig_entries.keys()) & set(gen_entries.keys())
                        )
                        for tid in all_ids:
                            hard, soft = compare_entries(
                                orig_entries[tid], gen_entries[tid]
                            )
                            if hard or soft:
                                w.writerow(
                                    [
                                        yyyymm,
                                        tid,
                                        ",".join(hard),
                                        ",".join(soft),
                                        orig_entries[tid].get("line", ""),
                                        gen_entries[tid].get("line", ""),
                                    ]
                                )

                print(f"Wrote: {mismatch_path}")

            print(f"Wrote: {report_path}")
    else:
        write_tradelog(
            out_dir / "flex_to_tradelog_2025.tlg", cons, account_id=args.account_id
        )
        write_mapping_csv(
            out_dir / "flex_tradelog_mapping.csv", cons, tlg_index=tlg_index
        )

    counts = defaultdict(int)
    for c in cons:
        counts[c.record_type()] += 1
    print(f"Flex fills: {len(fills)}")
    print(f"Consolidated orders: {len(cons)} ({dict(counts)})")
    if ref_tlg:
        print(f"Compared against: {ref_tlg}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
