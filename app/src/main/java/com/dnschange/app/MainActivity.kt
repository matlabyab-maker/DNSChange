package com.dnschange.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var dns1: EditText
    private lateinit var dns2: EditText
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private var targetField = 1
    private val prefs by lazy { getSharedPreferences("dnschange", MODE_PRIVATE) }

    data class Resolver(val name: String, val first: String, val second: String, val note: String = "")

    private val countries = linkedMapOf(
        "🇨🇳 چین" to listOf(
            Resolver("Alibaba AliDNS","223.5.5.5","223.6.6.6","ارائه‌دهنده چینی؛ آدرس‌های رسمی عمومی"),
            Resolver("Tencent DNSPod","119.29.29.29","182.254.116.116","ارائه‌دهنده چینی")
        ),
        "🇺🇸 آمریکا" to listOf(
            Resolver("Google Public DNS","8.8.8.8","8.8.4.4","شبکه Anycast جهانی؛ نه الزاماً فقط آمریکا"),
            Resolver("Cloudflare","1.1.1.1","1.0.0.1","شبکه Anycast جهانی"),
            Resolver("OpenDNS","208.67.222.222","208.67.220.220","Cisco OpenDNS"),
            Resolver("Quad9","9.9.9.9","149.112.112.112","شبکه جهانی")
        ),
        "🇨🇦 کانادا" to listOf(
            Resolver("CIRA Canadian Shield Private","149.112.121.10","149.112.122.10","DNS بدون فیلتر محتوایی"),
            Resolver("CIRA Canadian Shield Protected","149.112.121.20","149.112.122.20","مسدودسازی بدافزار و فیشینگ"),
            Resolver("CIRA Canadian Shield Family","149.112.121.30","149.112.122.30","فیلتر خانوادگی")
        ),
        "🇫🇷 فرانسه" to listOf(
            Resolver("FDN ns0","80.67.169.12","80.67.169.40","ارائه‌دهنده فرانسوی؛ دو resolver رسمی"),
            Resolver("Aquilenet","185.233.100.100","185.233.100.101","ارائه‌دهنده فرانسوی")
        ),
        "🇬🇧 انگلیس" to listOf(
            Resolver("OpenDNS","208.67.222.222","208.67.220.220","شبکه جهانی؛ مکان جغرافیایی اختصاصی تضمین‌شده نیست"),
            Resolver("Google Public DNS","8.8.8.8","8.8.4.4","شبکه Anycast جهانی؛ نه الزاماً فقط انگلیس")
        )
    )
    private val publicDns = listOf(
        Resolver("Google","8.8.8.8","8.8.4.4"),
        Resolver("Cloudflare","1.1.1.1","1.0.0.1"),
        Resolver("Quad9","9.9.9.9","149.112.112.112"),
        Resolver("OpenDNS","208.67.222.222","208.67.220.220"),
        Resolver("Comodo Secure DNS","8.26.56.26","8.20.247.20"),
        Resolver("AdGuard DNS","94.140.14.14","94.140.15.15"),
        Resolver("AliDNS","223.5.5.5","223.6.6.6"),
        Resolver("DNSPod","119.29.29.29","182.254.116.116"),
        Resolver("CIRA Canadian Shield","149.112.121.10","149.112.122.10"),
        Resolver("FDN France","80.67.169.12","80.67.169.40")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        restore()
    }

    private fun buildUi() {
        val scroller = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,28); setBackgroundColor(0xFFF4F6FA.toInt()) }
        scroller.addView(root)
        setContentView(scroller)

        root.addView(TextView(this).apply { text="DNS Change"; textSize=27f; setTextColor(0xFF152238.toInt()); gravity=Gravity.CENTER; setPadding(0,8,0,16) })
        panel("تنظیم و فعال‌سازی DNS") {
            dns1 = edit("DNS 1 — آدرس اول")
            dns2 = edit("DNS 2 — آدرس دوم")
            val activate = Button(this).apply {
                text="فعال‌سازی DNS"
                textSize=20f
                minHeight=64
                isAllCaps=false
                setOnClickListener { save(); activateVpn() }
            }
            addView(activate, LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin=dp(8) })
            val stop = Button(this).apply { text="قطع اتصال DNS"; isAllCaps=false; setOnClickListener { stopService(Intent(this@MainActivity,DnsVpnService::class.java)); status.text="وضعیت: خاموش" } }
            addView(stop)
            status = TextView(this@MainActivity).apply { text="وضعیت: خاموش"; textSize=15f; setPadding(4,12,4,4) }
            addView(status)
        }

        panel("انتخاب کشور — ابتدا DNS 1 یا DNS 2 را انتخاب کن") {
            val choose = RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
            val r1 = RadioButton(this).apply { text="انتخاب DNS 1"; id=1; isChecked=true }
            val r2 = RadioButton(this).apply { text="انتخاب DNS 2"; id=2 }
            choose.addView(r1); choose.addView(r2)
            choose.setOnCheckedChangeListener { _, id -> targetField = if(id==2) 2 else 1 }
            addView(choose)
            countries.forEach { (country, list) ->
                addText(country, 18f, true)
                val listBox = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                list.forEach { resolver -> listBox.addView(resolverRow(resolver)) }
                val inner = ScrollView(this).apply { addView(listBox); layoutParams=LinearLayout.LayoutParams(-1,dp(160)) }
                addView(inner)
                addView(Button(this).apply {
                    text="به‌روزرسانی آنلاین فهرست $country"
                    isAllCaps=false
                    setOnClickListener { Toast.makeText(this@MainActivity,"فهرست اولیه در برنامه ذخیره است؛ دریافت کاتالوگ آنلاین در نسخه بعدی اضافه می‌شود.",Toast.LENGTH_LONG).show() }
                })
            }
        }
        panel("DNSهای عمومی") {
            val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            publicDns.forEach { box.addView(resolverRow(it)) }
            addView(ScrollView(this).apply { addView(box); layoutParams=LinearLayout.LayoutParams(-1,dp(230)) })
        }
        panel("مدیریت فهرست‌ها") {
            addView(Button(this).apply { text="ذخیره DNSهای فعلی"; isAllCaps=false; setOnClickListener { save(); Toast.makeText(this@MainActivity,"DNSهای فعلی ذخیره شدند.",Toast.LENGTH_SHORT).show() } })
            addView(Button(this).apply { text="ورود DNSهای ذخیره‌شده"; isAllCaps=false; setOnClickListener { restore(); Toast.makeText(this@MainActivity,"DNSهای ذخیره‌شده بارگذاری شدند.",Toast.LENGTH_SHORT).show() } })
            addView(Button(this).apply { text="ذخیره فهرست پیش‌فرض کشورها"; isAllCaps=false; setOnClickListener { saveCatalog(); Toast.makeText(this@MainActivity,"فهرست‌های کشورها ذخیره شدند.",Toast.LENGTH_SHORT).show() } })
            addView(Button(this).apply { text="ورود فهرست‌های ذخیره‌شده"; isAllCaps=false; setOnClickListener { loadCatalog(); Toast.makeText(this@MainActivity,"فهرست ذخیره‌شده بررسی شد.",Toast.LENGTH_SHORT).show() } })
        }
        root.addView(TextView(this).apply { text="نکته: فهرست کشورها بر اساس ارائه‌دهنده‌های شناخته‌شده تنظیم شده است. برخی DNSها Anycast هستند و الزاماً فقط در کشور نام‌برده میزبانی نمی‌شوند."; textSize=12f; setTextColor(0xFF555F70.toInt()); setPadding(6,12,6,0) })
    }

    private fun panel(title: String, body: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(14)); setBackgroundColor(0xFFFFFFFF.toInt()) }
        card.addView(TextView(this).apply { text=title; textSize=18f; setTextColor(0xFF172A46.toInt()); setPadding(0,0,0,dp(8)) })
        card.body()
        root.addView(card, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
    }
    private fun edit(hintText:String) = EditText(this).apply { hint=hintText; singleLine=true; textSize=16f; setPadding(dp(10),dp(8),dp(10),dp(8)); inputType=android.text.InputType.TYPE_CLASS_TEXT }
    private fun addText(t:String, size:Float, bold:Boolean) { addView(TextView(this).apply { text=t; textSize=size; setTextColor(0xFF24364D.toInt()); if(bold) setTypeface(null,1); setPadding(0,dp(10),0,dp(5)) }) }
    private fun resolverRow(r:Resolver): View {
        val row = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(6),dp(8),dp(6)); setBackgroundColor(0xFFF0F4F9.toInt()) }
        row.addView(TextView(this).apply { text=r.name; textSize=15f; setTypeface(null,1); setTextColor(0xFF1C2B40.toInt()) })
        row.addView(TextView(this).apply { text="${r.first}    |    ${r.second}"; textSize=14f; setTextColor(0xFF34445A.toInt()) })
        if(r.note.isNotBlank()) row.addView(TextView(this).apply { text=r.note; textSize=11f; setTextColor(0xFF64748B.toInt()) })
        row.addView(Button(this).apply {
            text="انتخاب برای DNS $targetField"; isAllCaps=false
            setOnClickListener {
                if(targetField==1) dns1.setText(r.first) else dns2.setText(r.second)
                Toast.makeText(this@MainActivity,"آدرس ${if(targetField==1) "اول" else "دوم"} انتخاب شد.",Toast.LENGTH_SHORT).show()
            }
        })
        row.layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) }
        return row
    }
    private fun save() { prefs.edit().putString("dns1",dns1.text.toString().trim()).putString("dns2",dns2.text.toString().trim()).apply() }
    private fun restore() { if(::dns1.isInitialized) { dns1.setText(prefs.getString("dns1","8.8.8.8")); dns2.setText(prefs.getString("dns2","8.8.4.4")) } }
    private fun saveCatalog() {
        val obj=JSONObject()
        countries.forEach { (k,v) -> val a=JSONArray(); v.forEach { a.put(JSONObject().put("name",it.name).put("dns1",it.first).put("dns2",it.second)) }; obj.put(k,a) }
        prefs.edit().putString("catalog",obj.toString()).apply()
    }
    private fun loadCatalog() { prefs.getString("catalog",null) ?: saveCatalog() }
    private fun activateVpn() {
        val a=dns1.text.toString().trim(); val b=dns2.text.toString().trim()
        if(!validIp(a) || !validIp(b)) { Toast.makeText(this,"هر دو DNS باید IP معتبر IPv4 باشند.",Toast.LENGTH_LONG).show(); return }
        val intent=VpnService.prepare(this)
        if(intent!=null) startActivityForResult(intent,77) else startDns()
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==77 && resultCode==RESULT_OK) startDns()
    }
    private fun startDns() {
        val i=Intent(this,DnsVpnService::class.java).putExtra("dns1",dns1.text.toString().trim()).putExtra("dns2",dns2.text.toString().trim())
        if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i)
        status.text="وضعیت: درخواست فعال‌سازی ارسال شد"
    }
    private fun validIp(s:String):Boolean = try { val p=s.split("."); p.size==4 && p.all { it.toInt() in 0..255 } } catch (_:Exception) { false }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
