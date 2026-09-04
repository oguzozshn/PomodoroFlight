#!/usr/bin/env python3
"""
Natural Earth kiyi seridi GeoJSON'unu uygulamanin okudugu kompakt ikili bicime cevirir.

Kullanim:
    python3 tools/build_coastline.py ne_110m_coastline.geojson android/app/src/main/assets/coastline_110m.bin

Bicim (little-endian):
    "UMC1"            4 bayt sihirli sayi
    int32             cizgi sayisi
    her cizgi icin:
        int32         nokta sayisi
        int16 x N     boylam, 32767/180 olcekli
        int16 x N     enlem,  32767/90  olcekli

Int16 kuantalama ~600 m hassasiyet verir; kure olceginde fark edilmez,
buna karsilik dosya GeoJSON'un onda biri kadar yer kaplar.
"""
import json
import struct
import sys

LON_SCALE = 32767.0 / 180.0
LAT_SCALE = 32767.0 / 90.0


def quantize(value: float, scale: float) -> int:
    return max(-32768, min(32767, int(round(value * scale))))


def lines_of(geojson: dict):
    for feature in geojson["features"]:
        geometry = feature["geometry"]
        if geometry["type"] == "LineString":
            yield geometry["coordinates"]
        elif geometry["type"] == "MultiLineString":
            for part in geometry["coordinates"]:
                yield part


def main(src: str, dst: str) -> None:
    with open(src, encoding="utf-8") as handle:
        data = json.load(handle)

    out = bytearray(b"UMC1")
    lines = [line for line in lines_of(data) if len(line) >= 2]
    out += struct.pack("<i", len(lines))

    total = 0
    for line in lines:
        out += struct.pack("<i", len(line))
        for lon, lat in line:
            out += struct.pack("<hh", quantize(lon, LON_SCALE), quantize(lat, LAT_SCALE))
        total += len(line)

    with open(dst, "wb") as handle:
        handle.write(out)

    print(f"{dst}: {len(lines)} cizgi, {total} nokta, {len(out) / 1024:.0f} KB")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
