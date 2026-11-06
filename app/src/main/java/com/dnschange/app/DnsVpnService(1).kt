package com.dnschange.app

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal IPv4/UDP DNS forwarder.
 * It intercepts DNS packets routed to the selected resolvers and forwards them unchanged.
 * This first build intentionally does not tunnel arbitrary application traffic.
 */
class DnsVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var first = "8.8.8.8"
    private var second = "8.8.4.4"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        first = intent?.getStringExtra("dns1") ?: first
        second = intent?.getStringExtra("dns2") ?: second
        startForeground(31, notification())
        stopTunnel()
        try {
            val builder = Builder()
                .setSession("DNS Change")
                .setMtu(1500)
                .addAddress("10.111.0.1", 32)
                .addDnsServer(first)
                .addDnsServer(second)
                .addRoute(first, 32)
                .addRoute(second, 32)
            tun = builder.establish()
            if (tun == null) throw IllegalStateException("VPN interface could not be established")
            running.set(true)
            worker = Thread { loop() }.also { it.name="DNSChange-UDP"; it.start() }
        } catch (e: Exception) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun loop() {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val packet = ByteArray(32767)
        while (running.get()) {
            try {
                val n = input.read(packet)
                if (n <= 0) continue
                val response = handle(packet.copyOf(n))
                if (response != null) synchronized(output) { output.write(response) }
            } catch (_: Exception) {
                if (running.get()) { /* transient packet or socket error */ }
            }
        }
    }

    private fun handle(p: ByteArray): ByteArray? {
        if (p.size < 28 || (p[0].toInt() and 0xF0) != 0x40) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p.size < ihl + 8 || p[9].toInt() != 17) return null // UDP only
        val src = p.copyOfRange(12,16)
        val dst = p.copyOfRange(16,20)
        val destIp = InetAddress.getByAddress(dst).hostAddress
        if (destIp != first && destIp != second) return null
        val udpLen = u16(p,ihl+4)
        if (udpLen < 8 || ihl + udpLen > p.size) return null
        val srcPort = u16(p,ihl)
        val dstPort = u16(p,ihl+2)
        if (dstPort != 53) return null
        val query = p.copyOfRange(ihl+8, ihl+udpLen)
        val answer = DatagramSocket().use { sock ->
            protect(sock)
            sock.soTimeout=5000
            sock.send(DatagramPacket(query,query.size,InetAddress.getByName(destIp),53))
            val buf=ByteArray(4096)
            val dp=DatagramPacket(buf,buf.size)
            sock.receive(dp)
            dp.data.copyOf(dp.length)
        }
        return buildIpv4Udp(dst,src,dstPort,srcPort,answer)
    }

    private fun buildIpv4Udp(src:ByteArray,dst:ByteArray,srcPort:Int,dstPort:Int,data:ByteArray):ByteArray {
        val total=20+8+data.size
        val out=ByteArray(total)
        out[0]=0x45; out[1]=0
        put16(out,2,total); put16(out,4,0); put16(out,6,0x4000)
        out[8]=64; out[9]=17
        System.arraycopy(src,0,out,12,4); System.arraycopy(dst,0,out,16,4)
        put16(out,10,checksum(out,0,20))
        put16(out,20,srcPort); put16(out,22,dstPort); put16(out,24,8+data.size)
        System.arraycopy(data,0,out,28,data.size)
        return out
    }
    private fun checksum(b:ByteArray,off:Int,len:Int):Int {
        var sum=0L; var i=off
        while(i<off+len) { val v=((b[i].toInt() and 255) shl 8) or (if(i+1<off+len) b[i+1].toInt() and 255 else 0); sum+=v; i+=2 }
        while((sum shr 16)>0) sum=(sum and 0xffff)+(sum shr 16)
        return sum.inv().toInt() and 0xffff
    }
    private fun u16(b:ByteArray,i:Int)=((b[i].toInt() and 255) shl 8) or (b[i+1].toInt() and 255)
    private fun put16(b:ByteArray,i:Int,v:Int) { b[i]=(v shr 8).toByte(); b[i+1]=v.toByte() }

    private fun notification():Notification {
        val channel="dnschange_vpn"
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel,"DNS Change VPN",NotificationManager.IMPORTANCE_LOW))
        val builder=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,channel) else Notification.Builder(this)
        return builder.setContentTitle("DNS Change").setContentText("Local DNS forwarding is active")
            .setSmallIcon(android.R.drawable.stat_sys_warning).build()
    }
    private fun stopTunnel() {
        running.set(false)
        try { tun?.close() } catch (_:Exception) {}
        tun=null
        worker?.interrupt()
    }
    override fun onDestroy() { stopTunnel(); super.onDestroy() }
    override fun onRevoke() { stopTunnel(); stopSelf(); super.onRevoke() }
}
