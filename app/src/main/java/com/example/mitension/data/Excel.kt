package com.example.mitension.data

import com.example.mitension.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.*
import java.time.*
import java.util.zip.*
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs
import kotlin.math.roundToLong

/** Bounded XLSX codec compatible with the iOS export. No ZIP extraction and no spreadsheet formulas. */
object Excel {
    private const val MAX_FILE = 32 * 1024 * 1024
    private const val MAX_EXPANDED = 64 * 1024 * 1024
    private const val EPOCH = -2209161600L
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // iOS's ISO8601DateFormatter expects fractional seconds, including whole-second timestamps.
    private val instantFormatter = java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter()
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun text(value: String, ref: String): String {
        require(value.length <= 32767)
        return "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
    }

    /** Visible columns A–H are human readable; hidden I–K preserve exact timestamps and medications. */
    fun write(rows: List<Reading>, titles: List<String>, zone: ZoneId = ZoneId.systemDefault(), periods: Pair<String, String> = "Mañana" to "Noche"): ByteArray {
        require(rows.all { it.valid() } && rows.size < 200000 && titles.size == 8)
        val headers = titles + listOf("MiTension.DateISO8601", "MiTension.Medications.v1", "MiTension.OriginalSerial")
        val sheet = buildString {
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/><col min=\"2\" max=\"8\" width=\"24\" customWidth=\"1\"/><col min=\"9\" max=\"11\" hidden=\"1\"/></cols><sheetData><row r=\"1\">")
            headers.forEachIndexed { i, name -> append(text(name, "${'A' + i}1")) }; append("</row>")
            rows.sortedBy { it.measuredAt }.forEachIndexed { index, r ->
                val row = index + 2
                val instant = Instant.ofEpochMilli(r.measuredAt)
                val serial = (r.measuredAt / 1000.0 + zone.rules.getOffset(instant).totalSeconds - EPOCH) / 86400
                append("<row r=\"$row\"><c r=\"A$row\" s=\"1\"><v>$serial</v></c>")
                append(text(if (r.period(zone) == "morning") periods.first else periods.second, "B$row"))
                append("<c r=\"C$row\"><v>${r.systolic}</v></c><c r=\"D$row\"><v>${r.diastolic}</v></c>")
                r.pulse?.let { append("<c r=\"E$row\"><v>$it</v></c>") }
                append(text(r.note, "F$row")); append(text(r.id, "G$row"))
                append(text(r.medications.joinToString("\n") { it.description }, "H$row"))
                append(text(instantFormatter.format(instant), "I$row")); append(text(json.encodeToString(r.medications), "J$row"))
                append("<c r=\"K$row\"><v>$serial</v></c></row>")
            }; append("</sheetData></worksheet>")
        }
        val files = linkedMapOf(
            "[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>",
            "_rels/.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml" to "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Mi Tension\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>",
            "xl/styles.xml" to "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"1\"><numFmt numFmtId=\"165\" formatCode=\"yyyy-mm-dd hh:mm\"/></numFmts><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs></styleSheet>",
            "xl/worksheets/sheet1.xml" to sheet)
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip -> files.forEach { (path, xml) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(xml.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            } }; output.toByteArray().also { require(it.size <= MAX_FILE) }
        }
    }

    /** Validate every row before returning; an invalid file can never be partially imported. */
    fun read(input: InputStream, zone: ZoneId = ZoneId.systemDefault()): List<Reading> {
        val bytes = input.readNBytesBounded(MAX_FILE)
        require(bytes.size >= 22 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte())
        fun u16(i: Int) = (bytes[i].toInt() and 255) or ((bytes[i+1].toInt() and 255) shl 8)
        fun u32(i: Int): Long = u16(i).toLong() or (u16(i+2).toLong() shl 16)
        val end = (bytes.size-22 downTo maxOf(0, bytes.size-65557)).firstOrNull {
            u32(it) == 0x06054b50L && it + 22 + u16(it+20) == bytes.size
        } ?: error("Incomplete ZIP directory")
        require(u16(end+4) == 0 && u16(end+6) == 0 && u16(end+8) == u16(end+10))
        require(u16(end+10) in 1..2000 && u32(end+12) + u32(end+16) == end.toLong())
        val files = mutableMapOf<String, ByteArray>(); var expanded = 0; var count = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 2000 && entry.name !in files && !entry.name.contains(".."))
                val content = zip.readNBytesBounded(MAX_EXPANDED - expanded)
                expanded += content.size
                files[entry.name] = content
                zip.closeEntry() // ZipInputStream also verifies the entry CRC.
            }
        }
        require(count == u16(end+10))
        val sheet = requireNotNull(files["xl/worksheets/sheet1.xml"])
        val shared = files["xl/sharedStrings.xml"]?.let { parse(it).strings }.orEmpty()
        val parsed = parse(sheet).rows
        fun value(row: Map<String, Cell>, column: String): String {
            val cell = row[column] ?: return ""
            require(!cell.formula)
            return if (cell.type == "s") shared[cell.value.toInt()] else cell.value
        }
        require(parsed.isNotEmpty() && value(parsed.first(), "G") == "ID")
        val seen = linkedMapOf<String, Reading>()
        parsed.drop(1).forEach { row ->
            val sys = value(row, "C"); val dia = value(row, "D"); val id = value(row, "G")
            if (sys.isEmpty() && dia.isEmpty() && id.isEmpty()) return@forEach
            fun number(text: String): Int {
                val n = text.toDouble(); require(n.isFinite() && n % 1 == 0.0 && n in 0.0..1000.0); return n.toInt()
            }
            val serial = value(row, "A").toDouble(); require(serial.isFinite() && serial in 1.0..100000.0)
            val original = value(row, "K").toDoubleOrNull()
            val exact = value(row, "I")
            val instant = if (exact.isNotBlank() && original != null && abs(original - serial) < 0.00000001)
                Instant.parse(exact) else LocalDateTime.ofInstant(Instant.ofEpochSecond(EPOCH + (serial * 86400).roundToLong()), ZoneOffset.UTC).atZone(zone).toInstant()
            val summary = value(row, "H"); val metadata = value(row, "J")
            var medications = if (metadata.isBlank()) emptyList() else json.decodeFromString<List<Medication>>(metadata)
            if (medications.joinToString("\n") { it.description } != summary)
                medications = if (summary.isBlank()) emptyList() else listOf(Medication(name = summary))
            val pulse = value(row, "E")
            val reading = Reading(id, number(sys), number(dia), if (pulse.isBlank()) null else number(pulse), instant.toEpochMilli(), value(row, "F"), medications).canonical()
            require(reading.valid() && (seen[reading.id] == null || seen[reading.id] == reading))
            seen[reading.id] = reading
        }
        return seen.values.toList()
    }

    private fun InputStream.readNBytesBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break
            require(output.size() + n <= limit); output.write(buffer, 0, n)
        }; return output.toByteArray()
    }
    private data class Cell(var value: String = "", var type: String = "", var formula: Boolean = false)
    private class XML : DefaultHandler() {
        val rows = mutableListOf<Map<String, Cell>>(); val strings = mutableListOf<String>()
        private var row = mutableMapOf<String, Cell>(); private var column = ""
        private var cell = Cell(); private var collecting = false; private var shared = false
        private val text = StringBuilder()
        override fun startElement(uri: String?, local: String?, name: String, attributes: Attributes) {
            when (name.substringAfter(':')) {
                "row" -> row = mutableMapOf()
                "c" -> { column = (attributes.getValue("r") ?: "").takeWhile { it.isLetter() }; cell = Cell(type = attributes.getValue("t") ?: ""); text.clear() }
                "si" -> { shared = true; text.clear() }
                "t", "v" -> collecting = true
                "f" -> cell.formula = true
            }
        }
        override fun characters(chars: CharArray, start: Int, length: Int) {
            if (collecting) { text.append(chars, start, length); require(text.length <= 32767) }
        }
        override fun endElement(uri: String?, local: String?, name: String) {
            when (name.substringAfter(':')) {
                "t", "v" -> collecting = false
                "c" -> { require(column.isNotEmpty() && column !in row); cell.value = text.toString(); row[column] = cell }
                "row" -> { require(rows.size < 200000); rows += row }
                "si" -> { strings += text.toString(); shared = false; require(strings.size < 200000) }
            }
        }
    }
    private fun parse(bytes: ByteArray): XML {
        // Our interchange format is UTF-8; reject UTF-16/NUL tricks before checking declarations.
        require(bytes.none { it == 0.toByte() })
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true))
        val handler = XML()
        SAXParserFactory.newInstance().newSAXParser().parse(ByteArrayInputStream(bytes), handler)
        return handler
    }
}
