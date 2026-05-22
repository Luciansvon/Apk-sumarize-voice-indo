package com.sumarize.voiceindo.viewmodel

enum class RecordingTemplate(val displayName: String) {
    UMUM("Umum"),
    MEETING("Meeting"),
    KULIAH("Kuliah"),
    WAWANCARA("Wawancara"),
    PODCAST("Podcast")
}

fun RecordingTemplate.buildSummaryPrompt(transcript: String): String {
    val trimmed = transcript.take(3000)
    return when (this) {
        RecordingTemplate.UMUM -> """Kamu adalah asisten ringkasan dalam bahasa Indonesia. Buat ringkasan singkat dan padat.

Format:
**Ringkasan:** (1-2 kalimat inti)
**Poin Penting:**
- (poin 1)
- (poin 2)
- (poin 3, jika ada)

Transkripsi:
$trimmed"""

        RecordingTemplate.MEETING -> """Kamu adalah asisten notulensi profesional. Analisis transkripsi rapat dan buat ringkasan terstruktur dalam bahasa Indonesia.

Format:
**Ringkasan Rapat:** (1-2 kalimat tentang tujuan dan hasil)
**Keputusan Utama:**
- (keputusan yang diambil)
**Action Items:**
- [ ] (tugas – penanggung jawab jika disebutkan)
**Poin Diskusi Penting:**
- (topik diskusi signifikan)

Transkripsi:
$trimmed"""

        RecordingTemplate.KULIAH -> """Kamu adalah asisten catatan kuliah. Buat catatan terstruktur dalam bahasa Indonesia.

Format:
**Topik Utama:** (judul atau tema kuliah)
**Konsep & Materi Penting:**
- (konsep + penjelasan singkat)
**Definisi & Istilah:**
- (istilah): (definisi)
**Poin yang Perlu Diingat:**
- (hal penting untuk diingat)

Transkripsi:
$trimmed"""

        RecordingTemplate.WAWANCARA -> """Kamu adalah asisten analisis wawancara. Buat ringkasan terstruktur dalam bahasa Indonesia.

Format:
**Konteks Wawancara:** (topik dan tujuan)
**Poin Jawaban Utama:**
- (topik): (jawaban atau insight)
**Kutipan Penting:**
- "(kutipan yang mewakili isi wawancara)"
**Kesimpulan:** (insight dan takeaway utama)
**Follow-up:**
- [ ] (tindak lanjut yang perlu dilakukan, jika ada)

Transkripsi:
$trimmed"""

        RecordingTemplate.PODCAST -> """Kamu adalah asisten ringkasan podcast. Buat ringkasan menarik dalam bahasa Indonesia.

Format:
**Topik Episode:** (tema utama yang dibahas)
**Highlight Diskusi:**
- (topik): (insight atau pembahasan utama)
**Perspektif Menarik:**
- (opini atau sudut pandang unik narasumber)
**Takeaway Utama:** (1-2 hal paling berharga dari episode ini)

Transkripsi:
$trimmed"""
    }
}
