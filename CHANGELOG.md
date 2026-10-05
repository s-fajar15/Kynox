# Changelog

## 2.3.0 (versionCode 5) — BELUM DIBUILD/DIUJI di perangkat
Application ID dan format data lama tidak berubah. Semua kunci penyimpanan baru bersifat tambahan; data 2.2.0 tetap terbaca.

**Profil per game dan pemulihan otomatis**
- Automation Rules kini mengembalikan keadaan setelah aplikasinya ditinggalkan (3 polling berturut-turut): profil sebelumnya diterapkan lagi (atau profil asli bila tadinya tidak ada) dan refresh rate kembali ke bawaan perangkat. Sebelumnya profil dari aturan tidak pernah dikembalikan.
- Pengaman: profil hanya dikembalikan bila profil aktif masih yang diterapkan aturan; jika kamu mengganti profil sendiri, pilihanmu tidak ditimpa. Bila Mode Game sedang aktif untuk aplikasi yang sama, profil game dari Pustaka Game yang dipakai, aturan hanya mengatur refresh rate.
- Status aturan disimpan (`automation_session`), jadi pemulihan tetap jalan walau proses dimatikan sistem.
- Satu service (GameDetectionService) membaca aplikasi di depan sekali per polling untuk Mode Game otomatis dan Automation (sebelumnya dua kali). Service berhenti sendiri bila keduanya mati; mematikan Mode Game otomatis tidak lagi mematikan Automation.
- Perbaikan: Mode Game otomatis sebelumnya tetap bereaksi pada game terkelola walau saklar deteksi dimatikan selama service hidup karena Automation; kini saklar dihormati.
- Perbaikan: menyalakan Automation menyimpan status dulu baru menjalankan service (menghindari service membaca status lama).

**Notifikasi**
- Pengaturan > Notifikasi: peringatan suhu (ambang Otomatis / 42 / 45 / 48 / 50 / 55 °C), notifikasi profil diterapkan/dikembalikan, dan jeda antar notifikasi sejenis (1 / 5 / 15 / 30 menit).
- Satu pembatas waktu dipakai bersama semua sumber peringatan suhu; zona yang sama tidak memperingatkan lagi sebelum suhunya turun 5 °C di bawah ambang. Peringatan yang tertahan jeda tidak hilang, dikirim saat jeda selesai bila masih panas.
- Ambang Otomatis (5 °C di bawah titik trip tiap sensor) berjalan di service Mode Game/Automation. Ambang yang dipilih sendiri berlaku untuk suhu CPU dan baterai oleh service Monitor.
- Notifikasi "pengaturan dikembalikan" baru saat game/aturan selesai.

**Riwayat monitor persisten**
- Kartu "Riwayat Tersimpan" di tab Performa: rentang 1/6/24/72 jam, grafik, min/rata-rata/maks dihitung dari semua sampel, jumlah sampel dan ukuran berkas, tombol hapus dengan konfirmasi.
- Penyimpanan di `monitor_history.csv` (filesDir). Selang simpan 10 dtk / 30 dtk / 1 mnt / 5 mnt dan lama simpan 1/6/24/72 jam diatur di Pengaturan > Riwayat monitor; selang lebih jarang berarti lebih hemat baterai dan penyimpanan. Berkas dipangkas menurut umur dan dibatasi 3 MB (sampel tertua dibuang dulu); baris rusak dilewati.
- Buffer 30 menit untuk grafik langsung tidak berubah.

**Cadangan & Pulihkan** (Pengaturan > Cadangan)
- Ekspor/impor satu berkas JSON lewat pemilih berkas sistem (tanpa izin tambahan): pengaturan portabel, aturan Automation, daftar game dan profilnya, profil kustom, refresh rate per aplikasi.
- Impor memeriksa format dan versi (ditolak bila dibuat versi Kynox lebih baru), menampilkan ringkasan dan meminta konfirmasi. Hanya kunci yang diizinkan yang dibaca; status perangkat (profil asli, Game Mode, sesi Automation) tidak ikut. Terapkan saat boot, mode aman, batas pengisian, notifikasi pengisian, dan monitor selalu aktif sengaja tidak diimpor. Mesin Automation selalu mati setelah impor. Tidak ada perintah sistem yang dijalankan.

**Kualitas**
- Polling berhenti saat aplikasi di latar belakang (Proses, CPU, GPU, Termal, Baterai, Dashboard, Mode Game, Jaringan, status overlay Sesi) lewat `AppVisibility`. Service latar belakang yang memang dimaksudkan (Monitor, Deteksi Game, Sesi) tidak terpengaruh.
- Aksesibilitas: target sentuh minimal 48dp untuk pemilih rentang/tab di Performa dan baris pilihan di Pengaturan, role Tab/Button/RadioButton pada kontrol klik kustom.
- Terjemahan data-layer: status dan kesehatan baterai (Mengisi daya, Penuh, Baik, ...) dan nama aksi di Log (APPLY_PROFILE -> "Terapkan profil", dll.; aksi tak dikenal tampil apa adanya), hasil log "Berhasil/Gagal".
- Perbaikan: "Not charging" sebelumnya dilabeli "Mengisi" di Dashboard karena mengandung kata "charging".
- Unit test (JVM, `app/src/test`): parser FPS SurfaceFlinger, klasifikasi perintah Console (blokir/berisiko/aman), filter dan label log, kebijakan notifikasi (jeda, ambang, hysteresis), codec dan pemangkasan riwayat, format dan validasi cadangan, penyaringan pengaturan portabel. Logika Console dan filter log dipindah ke berkas sendiri agar bisa diuji.

**Belum dikerjakan / diketahui**
- Build dan unit test belum pernah dijalankan (lingkungan penyusun tidak punya Android SDK/Kotlin compiler); semua perubahan hanya diperiksa statis. Jalankan `./gradlew testDebugUnitTest` dan `./gradlew assembleDebug` lalu kirim error bila ada.
- Dependensi tes baru (`junit`, `org.json`) hanya diunduh saat menjalankan tes, bukan saat assemble.
- Tema terang dan layar Pengaturan belum diperiksa di perangkat.
- Polling per layar masih berjalan untuk tab yang tersimpan di back stack selama aplikasi terlihat; hanya latar belakang aplikasi yang dijeda.
- Pembacaan riwayat memuat seluruh berkas ke memori (maks. 3 MB); cukup untuk batas ini, perlu diubah jika batas dinaikkan.

## 2.2.0 (versionCode 4) — BELUM DIBUILD/DIUJI di perangkat
Application ID dan format data tidak berubah.

- Tema gelap: palet navy lebih dalam, permukaan bertingkat, kontras teks lebih jelas, aksen sekunder ungu lembut untuk seri GPU.
- Dashboard: status suhu CPU tiga tingkat (Normal / Hangat / Panas) memakai ambang yang sama dengan layar lain; bila sensor tidak tersedia tampil "Tidak tersedia", bukan "Normal".
- Proses Berjalan: baris dipadatkan (nama, PID, user, CPU, RAM), tombol hentikan kini lewat dialog konfirmasi, hasil diverifikasi dengan membaca ulang daftar proses (tidak mengklaim berhasil jika proses masih ada), ada keadaan kosong.
- Log: pencarian (aksi, target, nilai, error), filter Semua/Berhasil/Gagal, penghitung entri, konfirmasi sebelum hapus semua log, dan kunci daftar yang unik (mencegah crash bila dua entri identik).
- Dropdown: latar menu lebih jelas dan opsi terpilih diberi warna aksen.
- Laporan Sesi: "Sorotan sesi" dihapus. Layout baru: header (ikon, nama, tanggal, durasi/resolusi/sampel tanpa teks terpotong) -> verdict FPS -> kartu ringkasan (suhu puncak beserta waktunya, daya, baterai terpakai + laju pengurasan) -> grafik FPS, peta penurunan, histogram -> tabel suhu Min/Rata-rata/Maks + frekuensi & daya -> grafik suhu/daya/frekuensi. Data laju pengurasan dan waktu puncak suhu dipindah ke kartu ringkasan, tidak hilang.
- Automation Rules: validasi package, refresh rate dipilih dari mode yang didukung layar, profil dari daftar (bukan teks bebas), tombol aktif/nonaktif per aturan, konfirmasi hapus, cegah aturan ganda.
- SELinux Monitor: status berwarna + penjelasan Permissive, state loading/gagal baca; tetap hanya membaca.
- Snapshot: menampilkan snapshot terakhir yang tersimpan, penanganan error, tombol tidak ganda.
- Command Console: output/perintah/error dibedakan warna, kode keluar & durasi, perintah merusak (mis. rm -rf /, mkfs, dd ke blok) diblokir, perintah berisiko (rm, setprop, pm uninstall, reboot, dst.) minta konfirmasi, output dibatasi 20 rb karakter.
- Diagnostics: tombol "Selesai" yang tidak berfungsi diganti "Periksa ulang" sungguhan, ringkasan lolos/perlu perhatian, penanganan error.
- Bersihkan RAM: mode Agresif selalu minta konfirmasi walau "Wajib konfirmasi" dimatikan; kegagalan ditampilkan.
- Profil Performa: kini menghormati pengaturan "Wajib konfirmasi" (sebelumnya hanya Cleaner yang menghormatinya).
- Kartu di layar alat memakai gaya yang sama dengan seluruh aplikasi.
- Bahasa visual baru ("Aurora"): kartu bertingkat dengan tepi bergradien dan sudut 26 dp (bukan kotak datar berbingkai), judul bagian dengan penanda gradien, tombol utama berbentuk pil dengan gradien biru-ungu, ikon tonal (bukan kotak biru solid), gauge berbusur "komet" (ekor transparan ke warna status).
- Navigasi bawah baru: kapsul melayang, hanya tab aktif yang menampilkan label (melebar dengan animasi), tab lain ikon saja.
- Dashboard baru: cincin suhu CPU besar sebagai pusat halaman, tiga bar beban CPU/GPU/RAM, kartu grafik beban CPU, ubin Baterai dan RAM dengan bar progres, banner status berbentuk pil. Logo K di top bar.
- Overlay (FPS saat sesi dan overlay metrik) didesain ulang: panel gradien navy bertepi tipis, angka FPS besar dengan garis status berwarna di kiri (cyan/kuning/merah), metrik CPU/GPU/PWR sebagai baris label-nilai, tombol stop bulat; overlay metrik berupa daftar baris (bukan kotak-kotak) dengan label "Kynox".
- Halaman Performa: tab berbentuk pil bergradien, kartu hero dengan gauge berwarna sesuai beban. Dihapus data palsu: teks "Status: Aktif" yang ditulis mati, dan suhu CPU yang salah diberi label suhu GPU. Gauge tab Baterai kini menampilkan Watt asli (skala 0-8 W), bukan persen palsu.
- Manajer CPU dan tab Kontrol CPU: mematikan core kini minta konfirmasi.
- Bentuk kartu diseragamkan di semua layar (hero 32 dp, kartu 26 dp) lewat KynoxShapes.
- Versi aplikasi 2.1.0 -> 2.2.0 (versionCode 3 -> 4).

## Belum dikerjakan / diketahui
- Build, unit test, dan uji di perangkat belum dijalankan (tidak ada Android SDK di lingkungan pengembangan ini).
- Profil yang diterapkan Automation tidak dikembalikan otomatis saat aplikasi ditutup (perilaku lama, tidak diubah).
- Tema terang belum diperiksa setelah redesain.
- Teks hasil data layer (status baterai, nama aksi log) masih Inggris (batasan lama).
