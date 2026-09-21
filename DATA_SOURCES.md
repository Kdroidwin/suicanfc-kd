# Data sources

The following normalized lookup data is included as UTF-8 CSV assets and is used entirely offline:

- `StationCodeExtra.csv`: 2,560 station-code mappings.
- `BusCodeExtra.csv`: 44,267 active bus-stop mappings.
- `MerchantCode.csv`: 7,129 unambiguous merchant-name mappings for Japanese transit IC cards.
- `MerchantCodeV4006.csv`: 23,632 five-digit merchant-code mappings from version 4.006.
- `GateCode.csv`: 46,331 active station ticket-gate mappings.

The data was extracted from the static databases in `jp.co.mirai_ii.nfc.allinone` versions 0.378 and 4.006, and `net.kino2718.cardadmin` version 16.19, with permission reported for this open-source project.  Only fields required for lookup are retained: codes, operator/line/stop names, merchant display names, and ticket-gate labels.  Addresses, coordinates, telephone numbers, device metadata, and the source application's code are not included.

Suica SF-history records expose a merchant terminal code only for certain shopping transactions.  This app resolves those known Suica C7/C8 codes.  It does not reuse a code across PASMO, ICOCA, or another brand, because the same numeric code can identify a different merchant.

Version 4.006 supplies a newer five-digit merchant-code format.  It is available through the explicit `Merchant.getSuicaMerchant` string-code overload; the existing four-digit SF-history parser remains unchanged until a compatible record source is added.

The current 16-byte SF-history parser does not receive ticket-gate codes.  `GateCode.csv` is therefore available through `GateDatabase` for future card records that do provide a gate code; it does not alter existing history display or read behavior.
