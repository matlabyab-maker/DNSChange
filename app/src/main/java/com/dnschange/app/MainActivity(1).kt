package com.dnschange.app

import android.app.Activity
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
    private lateinit var tabContent: FrameLayout
    private var targetField = 1
    private val prefs by lazy { getSharedPreferences("dnschange", MODE_PRIVATE) }

    data class Resolver(val name: String, val first: String, val second: String, val note: String = "")

    private val countries = linkedMapOf(
        "🇨🇳 چین" to listOf(
            Resolver("Alibaba AliDNS", "223.5.5.5", "223.6.6.6", "ارائه‌دهنده چینی؛ آدرس‌های رسمی عمومی"),
            Resolver("Tencent DNSPod", "119.29.29.29", "182.254.116.116", "ارائه‌دهنده چینی")
        ),
        "🇺🇸 آمریکا" to listOf(
            Resolver("Google Public DNS", "8.8.8.8", "8.8.4.4", "شبکه Anycast جهانی"),
            Resolver("Cloudflare", "1.1.1.1", "1.0.0.1", "شبکه Anycast جهانی"),
            Resolver("OpenDNS", "208.67.222.222", "208.67.220.220", "Cisco OpenDNS"),
            Resolver("Quad9", "9.9.9.9", "149.112.112.112", "شبکه جهانی")
        ),
        "🇨🇦 کانادا" to listOf(
            Resolver("CIRA Canadian Shield Private", "149.112.121.10", "149.112.122.10", "DNS بدون فیلتر محتوایی"),
            Resolver("CIRA Canadian Shield Protected", "149.112.121.20", "149.112.122.20", "مسدودسازی بدافزار و فیشینگ"),
            Resolver("CIRA Canadian Shield Family", "149.112.121.30", "149.112.122.30", "فیلتر خانوادگی")
        ),
        "🇫🇷 فرانسه" to listOf(
            Resolver("FDN ns0", "80.67.169.12", "80.67.169.40", "ارائه‌دهنده فرانسوی"),
            Resolver("Aquilenet", "185.233.100.100", "185.233.100.101", "ارائه‌دهنده فرانسوی")
        ),
        "🇬🇧 انگلیس" to listOf(
            Resolver("OpenDNS", "208.67.222.222", "208.67.220.220", "شبکه جهانی"),
            Resolver("Google Public DNS", "8.8.8.8", "8.8.4.4", "شبکه Anycast جهانی")
        )
    )

    private val publicDns = listOf(
        Resolver("Google", "8.8.8.8", "8.8.4.4"),
        Resolver("Cloudflare", "1.1.1.1", "1.0.0.1"),
        Resolver("Quad9", "9.9.9.9", "149.112.112.112"),
        Resolver("OpenDNS", "208.67.222.222", "208.67.220.220"),
        Resolver("Comodo Secure DNS", "8.26.56.26", "8.20.247.20"),
        Resolver("AdGuard DNS", "94.140.14.14", "94.140.15.15"),
        Resolver("AliDNS", "223.5.5.5", "223.6.6.6"),
        Resolver("DNSPod", "119.29.29.29", "182.254.116.116"),
        Resolver("CIRA Canadian Shield", "149.112.121.10", "149.112.122.10"),
        Resolver("FDN France", "80.67.169.12", "80.67.169.40")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        restore()
    }

    /** Fixed header + tab host: the activity itself never scrolls. Only the selected tab scrolls. */
    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(8))
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        setContentView(page)

        page.addView(TextView(this).apply {
            text = "DNS Change"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFF152238.toInt())
            setPadding(0, dp(3), 0, dp(5))
        }, LinearLayout.LayoutParams(-1, -2))

        val dnsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(8))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val dnsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dns1 = edit("DNS 1").apply { setText("8.8.8.8") }
        dns2 = edit("DNS 2").apply { setText("8.8.4.4") }
        dnsRow.addView(dns1, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        dnsRow.addView(dns2, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        dnsCard.addView(dnsRow)

        val activate = Button(this).apply {
            text = "فعال‌سازی DNS"
            textSize = 18f
            isAllCaps = false
            minHeight = dp(54)
            setOnClickListener { save(); activateVpn() }
        }
        dnsCard.addView(activate, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(3) })

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        status = TextView(this).apply {
            text = "وضعیت: خاموش"
            textSize = 13f
            setTextColor(0xFF42536A.toInt())
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        statusRow.addView(Button(this).apply {
            text = "قطع اتصال"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                stopService(Intent(this@MainActivity, DnsVpnService::class.java))
                status.text = "وضعیت: خاموش"
            }
        }, LinearLayout.LayoutParams(-2, dp(42)))
        dnsCard.addView(statusRow)
        page.addView(dnsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFE3EAF3.toInt())
        }
        page.addView(tabs, LinearLayout.LayoutParams(-1, dp(46)))
        tabContent = FrameLayout(this)
        page.addView(tabContent, LinearLayout.LayoutParams(-1, 0, 1f))

        val labels = listOf("کشورها", "DNS عمومی", "ذخیره‌شده", "راهنما")
        labels.forEachIndexed { index, label ->
            val button = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF1B3554.toInt())
                setPadding(dp(3), 0, dp(3), 0)
                setOnClickListener { showTab(index, tabs, labels) }
            }
            tabs.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
        }
        showTab(0, tabs, labels)
    }

    private fun showTab(index: Int, tabs: LinearLayout, labels: List<String>) {
        for (i in 0 until tabs.childCount) {
            val selected = i == index
            tabs.getChildAt(i).setBackgroundColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFE3EAF3.toInt())
            (tabs.getChildAt(i) as TextView).setTypeface(null, if (selected) 1 else 0)
        }
        tabContent.removeAllViews()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(12))
        }
        scroll.addView(content)
        tabContent.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        when (index) {
            0 -> buildCountriesTab(content)
            1 -> buildPublicTab(content)
            2 -> buildSavedTab(content)
            else -> buildInfoTab(content)
        }
    }

    private fun buildCountriesTab(content: LinearLayout) {
        panel(content, "انتخاب مقصد DNS") {
            addView(TextView(this@MainActivity).apply {
                text = "ابتدا مشخص کنید کدام آدرس تغییر کند؛ سپس از فهرست کشورها یک سرویس را انتخاب کنید."
                textSize = 13f
                setTextColor(0xFF526176.toInt())
            })
            val group = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.HORIZONTAL }
            group.addView(RadioButton(this@MainActivity).apply { text = "DNS 1"; id = 1; isChecked = true })
            group.addView(RadioButton(this@MainActivity).apply { text = "DNS 2"; id = 2 })
            group.setOnCheckedChangeListener { _, checked -> targetField = if (checked == 2) 2 else 1 }
            addView(group)
        }
        countries.forEach { (country, resolvers) ->
            panel(content, country) {
                resolvers.forEach { addView(resolverRow(it)) }
            }
        }
    }

    private fun buildPublicTab(content: LinearLayout) {
        panel(content, "DNSهای عمومی و شناخته‌شده") {
            addView(TextView(this@MainActivity).apply {
                text = "با انتخاب هر سرویس، DNS 1 یا DNS 2 انتخاب‌شده در بالای صفحه به‌روزرسانی می‌شود."
                textSize = 13f
                setTextColor(0xFF526176.toInt())
                setPadding(0, 0, 0, dp(7))
            })
            publicDns.forEach { addView(resolverRow(it)) }
        }
    }

    private fun buildSavedTab(content: LinearLayout) {
        panel(content, "مدیریت تنظیمات ذخیره‌شده") {
            addView(Button(this@MainActivity).apply {
                text = "ذخیره DNSهای فعلی"
                isAllCaps = false
                setOnClickListener { save(); toast("DNSهای فعلی ذخیره شدند.") }
            })
            addView(Button(this@MainActivity).apply {
                text = "بارگذاری DNSهای ذخیره‌شده"
                isAllCaps = false
                setOnClickListener { restore(); toast("DNSهای ذخیره‌شده بارگذاری شدند.") }
            })
            addView(Button(this@MainActivity).apply {
                text = "ذخیره فهرست پیش‌فرض کشورها"
                isAllCaps = false
                setOnClickListener { saveCatalog(); toast("فهرست کشورها ذخیره شد.") }
            })
            addView(Button(this@MainActivity).apply {
                text = "بررسی فهرست‌های ذخیره‌شده"
                isAllCaps = false
                setOnClickListener {
                    val exists = prefs.contains("catalog")
                    if (!exists) saveCatalog()
                    toast(if (exists) "فهرست ذخیره‌شده موجود است." else "فهرست پیش‌فرض ذخیره شد.")
                }
            })
        }
    }

    private fun buildInfoTab(content: LinearLayout) {
        panel(content, "راهنمای استفاده") {
            addView(TextView(this@MainActivity).apply {
                text = "۱. آدرس‌های DNS 1 و DNS 2 را در بالای صفحه وارد کنید یا از زبانه‌های کشورها و DNS عمومی انتخاب کنید.\n\n۲. دکمه فعال‌سازی DNS را بزنید و درخواست اتصال VPN اندروید را تأیید کنید. این برنامه برای تغییر DNS به دسترسی روت نیاز ندارد و از مجوز VPN محلی استفاده می‌کند.\n\n۳. برای قطع سرویس از دکمه «قطع اتصال» استفاده کنید.\n\nتوجه: این نسخه یک مسیر‌دهنده محلی DNS برای بسته‌های IPv4/UDP است؛ ترافیک عمومی برنامه‌ها را از تونل عبور نمی‌دهد. فهرست کشورها بر اساس ارائه‌دهنده‌هاست و بعضی سرویس‌ها Anycast هستند؛ بنابراین محل فیزیکی سرور الزاماً محدود به کشور درج‌شده نیست."
                textSize = 14f
                setTextColor(0xFF34445A.toInt())
                setLineSpacing(dp(3).toFloat(), 1f)
            })
        }
    }

    private fun panel(parent: LinearLayout, title: String, body: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(0xFF172A46.toInt())
            setPadding(0, 0, 0, dp(7))
        })
        card.body()
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun edit(hintText: String) = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        inputType = android.text.InputType.TYPE_CLASS_TEXT
    }

    private fun resolverRow(resolver: Resolver): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFFF0F4F9.toInt())
        }
        row.addView(TextView(this).apply {
            text = resolver.name
            textSize = 14f
            setTypeface(null, 1)
            setTextColor(0xFF1C2B40.toInt())
        })
        row.addView(TextView(this).apply {
            text = "${resolver.first}     |     ${resolver.second}"
            textSize = 13f
            setTextColor(0xFF34445A.toInt())
        })
        if (resolver.note.isNotBlank()) row.addView(TextView(this).apply {
            text = resolver.note
            textSize = 11f
            setTextColor(0xFF64748B.toInt())
        })
        row.addView(Button(this).apply {
            text = "انتخاب برای DNS $targetField"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                if (targetField == 1) dns1.setText(resolver.first) else dns2.setText(resolver.second)
                save()
                toast("آدرس DNS $targetField انتخاب شد.")
            }
        })
        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        return row
    }

    private fun save() {
        prefs.edit().putString("dns1", dns1.text.toString().trim())
            .putString("dns2", dns2.text.toString().trim()).apply()
    }

    private fun restore() {
        if (::dns1.isInitialized) {
            dns1.setText(prefs.getString("dns1", "8.8.8.8"))
            dns2.setText(prefs.getString("dns2", "8.8.4.4"))
        }
    }

    private fun saveCatalog() {
        val obj = JSONObject()
        countries.forEach { (key, values) ->
            val array = JSONArray()
            values.forEach { array.put(JSONObject().put("name", it.name).put("dns1", it.first).put("dns2", it.second)) }
            obj.put(key, array)
        }
        prefs.edit().putString("catalog", obj.toString()).apply()
    }

    private fun activateVpn() {
        val first = dns1.text.toString().trim()
        val second = dns2.text.toString().trim()
        if (!validIp(first) || !validIp(second)) {
            toast("هر دو DNS باید IP معتبر IPv4 باشند.")
            return
        }
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) startActivityForResult(prepareIntent, 77) else startDns()
    }

    @Deprecated("Deprecated by Android API; retained for compatibility with minSdk 23")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) startDns()
    }

    private fun startDns() {
        val intent = Intent(this, DnsVpnService::class.java)
            .putExtra("dns1", dns1.text.toString().trim())
            .putExtra("dns2", dns2.text.toString().trim())
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "وضعیت: درخواست فعال‌سازی ارسال شد"
    }

    private fun validIp(value: String): Boolean = try {
        val parts = value.split(".")
        parts.size == 4 && parts.all { it.toInt() in 0..255 }
    } catch (_: Exception) { false }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
