package com.diegoca.gpsmocker

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

object GpxCodec {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun read(input: InputStream): List<Coordinates> {
        val bytes = input.readBytesBounded()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(bytes.inputStream(), null)
        val track = mutableListOf<Coordinates>()
        val route = mutableListOf<Coordinates>()
        val waypoints = mutableListOf<Coordinates>()
        var event = parser.eventType
        var count = 0
        var rootSeen = false
        while (event != XmlPullParser.END_DOCUMENT) {
            require(event != XmlPullParser.DOCDECL) { "El GPX no puede contener declaraciones externas." }
            if (event == XmlPullParser.START_TAG && !rootSeen) {
                require(parser.name == "gpx") { "El archivo no es un documento GPX." }
                rootSeen = true
            }
            if (event == XmlPullParser.START_TAG && parser.name in setOf("trkpt", "rtept", "wpt")) {
                require(++count <= RouteNavigator.MAX_POINTS) { "El GPX admite hasta ${RouteNavigator.MAX_POINTS} puntos." }
                val point = Coordinates.parse(parser.getAttributeValue(null, "lat").orEmpty(),
                    parser.getAttributeValue(null, "lon").orEmpty())
                    ?: throw IllegalArgumentException("El GPX contiene coordenadas inválidas.")
                val list = when (parser.name) { "trkpt" -> track; "rtept" -> route; else -> waypoints }
                if (list.lastOrNull() != point) list += point
            }
            event = parser.nextToken()
        }
        val result = track.ifEmpty { route.ifEmpty { waypoints } }
        require(result.size >= 2) { "El GPX debe contener al menos dos puntos distintos." }
        return result
    }

    fun write(output: OutputStream, points: List<Coordinates>) {
        require(points.size in 2..RouteNavigator.MAX_POINTS)
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "gpx").attribute(null, "version", "1.1")
            .attribute(null, "creator", "GPS Mocker")
            .attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")
        serializer.startTag(null, "trk").startTag(null, "name").text("Ruta GPS Mocker").endTag(null, "name")
        serializer.startTag(null, "trkseg")
        for (point in points) {
            serializer.startTag(null, "trkpt")
                .attribute(null, "lat", String.format(Locale.US, "%.8f", point.latitude))
                .attribute(null, "lon", String.format(Locale.US, "%.8f", point.longitude))
                .endTag(null, "trkpt")
        }
        serializer.endTag(null, "trkseg").endTag(null, "trk").endTag(null, "gpx")
        serializer.endDocument()
        serializer.flush()
    }

    private fun InputStream.readBytesBounded(): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = read(buffer)
            if (size < 0) break
            require(result.size() + size <= MAX_BYTES) { "El GPX debe ocupar menos de 2 MB." }
            result.write(buffer, 0, size)
        }
        return result.toByteArray()
    }
}
