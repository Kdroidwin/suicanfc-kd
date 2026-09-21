# Data sources

The following normalized lookup data is included as UTF-8 CSV assets and is used entirely offline:

- `StationCodeExtra.csv`: 367 station-code mappings.
- `BusCodeExtra.csv`: 33,353 active bus-stop mappings.
- `MerchantCode.csv`: 7,129 unambiguous merchant-name mappings for Japanese transit IC cards.

The data was extracted from the static databases in `jp.co.mirai_ii.nfc.allinone` version 0.378 with permission reported for this open-source project.  Only fields required for lookup are retained: codes, operator/line/stop names, and merchant display names.  Addresses, coordinates, telephone numbers, device metadata, and the source application's code are not included.

Suica SF-history records expose a merchant terminal code only for certain shopping transactions.  This app resolves those known Suica C7/C8 codes.  It does not reuse a code across PASMO, ICOCA, or another brand, because the same numeric code can identify a different merchant.
