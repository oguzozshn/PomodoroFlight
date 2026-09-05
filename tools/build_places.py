#!/usr/bin/env python3
"""
OurAirports verisini (kamu mali) uygulamanin okudugu kompakt yer listesine cevirir.

Yalnizca tarifeli seferi olan, IATA kodu ve sehir adi bulunan havalimanlari alinir;
boylece listedeki her kayit gercekten ucus yapilabilen bir sehre karsilik gelir.

Kullanim:
    python3 tools/build_places.py airports.csv android/app/src/main/assets/places.bin

Bicim (little-endian):
    "UMP2"           4 bayt
    int32            yer sayisi (onem sirasina gore: once buyuk havalimanlari)
    her yer icin:
        int16        boylam, 32767/180 olcekli
        int16        enlem,  32767/90  olcekli
        uint8        onem sirasi (0 buyuk, 1 orta, 2 kucuk)
        3 bayt       IATA kodu
        2 bayt       ulke kodu (ISO 3166-1 alfa-2)
        uint8        sehir adi uzunlugu
        N bayt       sehir adi (UTF-8)
        uint8        havalimani adi uzunlugu
        N bayt       havalimani adi (UTF-8)

Havalimani adi hem aramada (OurAirports'un "municipality" alani bazen ilceyi
verir: ADB icin Gaziemir) hem de listede ikinci satir olarak kullanilir.
"""
import csv
import struct
import sys

RANKS = {"large_airport": 0, "medium_airport": 1, "small_airport": 2}

# Havalimani adlarindan atilan genel ekler
SUFFIXES = (
    " International Airport",
    " Intl Airport",
    " International",
    " Airport",
    " Airfield",
    " Aerodrome",
)


def clean_name(name: str) -> str:
    text = name.strip()
    for suffix in SUFFIXES:
        if text.endswith(suffix):
            text = text[: -len(suffix)].strip()
            break
    return text or name.strip()

LON_SCALE = 32767.0 / 180.0
LAT_SCALE = 32767.0 / 90.0


def quantize(value: float, scale: float) -> int:
    return max(-32768, min(32767, int(round(value * scale))))


def main(src: str, dst: str) -> None:
    best = {}
    with open(src, encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            rank = RANKS.get(row["type"])
            iata = row["iata_code"].strip().upper()
            city = row["municipality"].strip()
            country = row["iso_country"].strip().upper()
            if rank is None or len(iata) != 3 or not city or len(country) != 2:
                continue
            if row["scheduled_service"] != "yes":
                continue
            try:
                lat = float(row["latitude_deg"])
                lon = float(row["longitude_deg"])
            except ValueError:
                continue
            if not (-90 <= lat <= 90 and -180 <= lon <= 180):
                continue
            # Ayni IATA kodu birden fazla satirda gecerse buyuk olani kalir.
            current = best.get(iata)
            if current is None or rank < current[0]:
                best[iata] = (rank, city, country, lat, lon, clean_name(row["name"]))

    places = sorted(best.items(), key=lambda kv: (kv[1][0], kv[1][1]))

    out = bytearray(b"UMP2")
    out += struct.pack("<i", len(places))
    for iata, (rank, city, country, lat, lon, airport) in places:
        city_bytes = city.encode("utf-8")[:255]
        airport_bytes = airport.encode("utf-8")[:255]
        out += struct.pack("<hhB", quantize(lon, LON_SCALE), quantize(lat, LAT_SCALE), rank)
        out += iata.encode("ascii")
        out += country.encode("ascii")
        out += struct.pack("<B", len(city_bytes))
        out += city_bytes
        out += struct.pack("<B", len(airport_bytes))
        out += airport_bytes

    with open(dst, "wb") as handle:
        handle.write(out)

    ranks = [0, 0, 0]
    for _, (rank, *_rest) in places:
        ranks[rank] += 1
    print(
        f"{dst}: {len(places)} yer "
        f"(buyuk {ranks[0]}, orta {ranks[1]}, kucuk {ranks[2]}), {len(out) / 1024:.0f} KB"
    )


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
