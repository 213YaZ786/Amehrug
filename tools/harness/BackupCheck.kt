import com.amehrug.app.crypto.BackupCrypto
import com.amehrug.app.model.Attachment
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.model.BackupContent
import com.amehrug.app.model.BackupFormat
import com.amehrug.app.model.Folder
import com.amehrug.app.model.Json
import com.amehrug.app.model.JsonValue
import com.amehrug.app.model.ListItem
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteColor
import com.amehrug.app.model.NoteType
import com.amehrug.app.model.Reminder
import com.amehrug.app.model.TextSpan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom

fun good(condition: Boolean, what: String) {
    if (!condition) throw AssertionError(what)
    println("ok  $what")
}

fun refused(what: String, block: () -> Unit) {
    try {
        block()
        throw AssertionError("should have failed: $what")
    } catch (e: IllegalArgumentException) {
        println("ok  refused: $what")
    } catch (e: GeneralSecurityException) {
        println("ok  refused: $what")
    }
}

fun main() {
    // JSON
    val tricky = "quote \" backslash \\ newline \n tab \t accent é emoji \uD83D\uDD10 </script>"
    val written = Json.write(JsonValue.Str(tricky))
    good(Json.parse(written) == JsonValue.Str(tricky), "json string round trip with quotes, accents and emoji")
    good(Json.write(JsonValue.Num(42.0)) == "42", "whole numbers stay whole")
    good(Json.parse("""{"a":[1,{"b":null},true]}""") is JsonValue.Obj, "nested json")
    good(Json.parse("""{ "a" : 1 , "b" : 2 }""").let { (it as JsonValue.Obj).fields.size == 2 }, "spaces allowed")
    refused("unclosed object") { Json.parse("""{"a":1""") }
    refused("trailing comma") { Json.parse("""[1,2,]""") }
    refused("trailing text") { Json.parse("""{"a":1} oops""") }
    refused("single quotes") { Json.parse("""{'a':1}""") }
    refused("control character in string") { Json.parse("\"line\nbreak\"") }
    refused("too deep") { Json.parse("[".repeat(40) + "]".repeat(40)) }
    good(Json.parse("\"\\u00e9\"") == JsonValue.Str("é"), "unicode escape")

    // Backup format
    val note = Note(
        id = 7,
        type = NoteType.LIST,
        folder = Folder.ARCHIVED,
        color = NoteColor.SAGE,
        title = "Courses",
        body = "corps é \" \n",
        spans = listOf(TextSpan(0, 5, bold = true)),
        items = listOf(ListItem("lait", checked = true), ListItem("pain", indent = 1)),
        labels = listOf("Maison"),
        attachments = listOf(Attachment(id = 3, kind = AttachmentKind.IMAGE, fileName = "abc123", mimeType = "image/png", sizeBytes = 99, createdAt = 5)),
        reminders = listOf(Reminder(atMillis = 1234, repeat = com.amehrug.app.model.Repeat.WEEKLY)),
        pinned = true,
        createdAt = 10,
        modifiedAt = 20,
    )
    val content = BackupContent(createdAt = 999, notes = listOf(note), labels = listOf("Maison", "Travail"))
    val back = BackupFormat.decode(BackupFormat.encode(content))
    good(back.notes.size == 1 && back.labels == listOf("Maison", "Travail"), "backup round trip")
    val restored = back.notes[0]
    good(restored.title == note.title && restored.body == note.body, "text kept")
    good(restored.type == NoteType.LIST && restored.folder == Folder.ARCHIVED && restored.color == NoteColor.SAGE, "enums kept")
    good(restored.items == note.items && restored.spans == note.spans, "items and spans kept")
    good(restored.attachments.map { it.fileName } == listOf("abc123"), "attachment kept")
    good(restored.reminders == note.reminders && restored.pinned, "reminder and pin kept")
    good(restored.createdAt == 10L && restored.modifiedAt == 20L, "dates kept")
    good(restored.id == 0L, "identifiers are not restored")

    val unknown = BackupFormat.encode(content).replace("\"SAGE\"", "\"FUCHSIA\"").replace("\"LIST\"", "\"SONG\"")
    val lenient = BackupFormat.decode(unknown).notes[0]
    good(lenient.color == NoteColor.DEFAULT && lenient.type == NoteType.NOTE, "unknown values fall back")
    refused("not our backup") { BackupFormat.decode("""{"format":"other","version":1}""") }
    refused("newer version") { BackupFormat.decode(BackupFormat.encode(content.copy(version = 99))) }
    val escaping = BackupFormat.encode(content.copy(notes = listOf(note.copy(attachments = listOf(fileNameAttack())))))
    good(BackupFormat.decode(escaping).notes[0].attachments.isEmpty(), "attachment path is refused")

    // Password envelope
    val plain = ByteArray(200_000).also { SecureRandom().nextBytes(it) }
    val sealed = ByteArrayOutputStream()
    val password = "correct horse battery".toCharArray()
    BackupCrypto.encrypt(password, ByteArrayInputStream(plain), sealed, SecureRandom(), iterations = 1000)
    val opened = ByteArrayOutputStream()
    BackupCrypto.decrypt(password, ByteArrayInputStream(sealed.toByteArray()), opened)
    good(opened.toByteArray().contentEquals(plain), "backup round trip with a password")
    refused("wrong password") {
        BackupCrypto.decrypt("wrong password here".toCharArray(), ByteArrayInputStream(sealed.toByteArray()), ByteArrayOutputStream())
    }
    refused("cut backup") {
        BackupCrypto.decrypt(password, ByteArrayInputStream(sealed.toByteArray().copyOf(sealed.size() - 50)), ByteArrayOutputStream())
    }
    refused("edited backup") {
        val bad = sealed.toByteArray()
        bad[bad.size - 20] = (bad[bad.size - 20].toInt() xor 1).toByte()
        BackupCrypto.decrypt(password, ByteArrayInputStream(bad), ByteArrayOutputStream())
    }
    refused("someone else's file") {
        BackupCrypto.decrypt(password, ByteArrayInputStream(ByteArray(64)), ByteArrayOutputStream())
    }
    val other = ByteArrayOutputStream()
    BackupCrypto.encrypt(password, ByteArrayInputStream(plain), other, SecureRandom(), iterations = 1000)
    good(!other.toByteArray().contentEquals(sealed.toByteArray()), "two backups of the same notes differ")
    println("ALL PASSED")
}

fun fileNameAttack(): Attachment = Attachment(kind = AttachmentKind.IMAGE, fileName = "../../databases/amehrug.db", mimeType = "image/png")
