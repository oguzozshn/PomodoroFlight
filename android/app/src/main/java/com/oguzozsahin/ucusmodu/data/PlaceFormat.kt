package com.oguzozsahin.ucusmodu.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** tools/build_places.py ile uretilen yer listesini okur. */
object PlaceFormat {

    private const val MAGIC = "UMP2"
    private const val LON_SCALE = 180.0 / 32767.0
    private const val LAT_SCALE = 90.0 / 32767.0

    fun parse(bytes: ByteArray): List<Airport> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Beklenmeyen yer listesi bicimi: $magic" }

        val count = buffer.int
        val places = ArrayList<Airport>(count)
        val code = ByteArray(3)
        val country = ByteArray(2)
        repeat(count) {
            val lon = buffer.short * LON_SCALE
            val lat = buffer.short * LAT_SCALE
            val rank = buffer.get().toInt() and 0xFF
            buffer.get(code)
            buffer.get(country)
            val cityLength = buffer.get().toInt() and 0xFF
            val cityName = ByteArray(cityLength).also { buffer.get(it) }
            val airportLength = buffer.get().toInt() and 0xFF
            val airportName = ByteArray(airportLength).also { buffer.get(it) }
            places += Airport(
                code = code.toString(Charsets.US_ASCII),
                city = cityName.toString(Charsets.UTF_8),
                lat = lat,
                lon = lon,
                country = country.toString(Charsets.US_ASCII),
                rank = rank,
                airportName = airportName.toString(Charsets.UTF_8),
            )
        }
        return places
    }
}
