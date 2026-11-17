package com.example.restaurantbillqr

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF249B32)
private val Pale = Color(0xFFF4FAF3)
private val Ink = Color(0xFF17231A)

data class MenuItem(val name: String, val price: Double, val category: String)
data class LineItem(val name: String, val price: Double, val qty: Int)
data class Bill(val id: Long, val table: String, val lines: List<LineItem>, val subtotal: Double,
                val tax: Double, val tip: Double, val total: Double, val time: String)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Pale, surface = Color.White)) {
                RestaurantApp(this) {
                    val permissions = if (Build.VERSION.SDK_INT >= 31)
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissionLauncher.launch(permissions)
                }
            }
        }
    }
}

@Composable
fun RestaurantApp(context: Context, requestBluetooth: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("restaurant_app", Context.MODE_PRIVATE) }
    var restaurant by remember { mutableStateOf(prefs.getString("restaurant", "The Food Corner") ?: "The Food Corner") }
    var upi by remember { mutableStateOf(prefs.getString("upi", "jubairhussain41527@ybl") ?: "jubairhussain41527@ybl") }
    var gstPercent by remember { mutableStateOf(prefs.getString("gst", "5") ?: "5") }
    var screen by remember { mutableStateOf("Home") }
    var table by remember { mutableStateOf("T-01") }
    var tipPercent by remember { mutableIntStateOf(10) }
    var customTip by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(listOf(
        MenuItem("Veg Momos",120.0,"Starters"), MenuItem("Paneer Tikka",220.0,"Starters"),
        MenuItem("Masala Dosa",180.0,"Main Course"), MenuItem("Cold Coffee",140.0,"Drinks")
    )) }
    var cart by remember { mutableStateOf(mapOf<String, Int>()) }
    var bills by remember { mutableStateOf(loadBills(prefs)) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var selectedPrinter by remember { mutableStateOf<BluetoothDevice?>(null) }
    var paperWidth by remember { mutableStateOf(prefs.getString("paperWidth", "58mm") ?: "58mm") }

    val lines = menu.filter { (cart[it.name] ?: 0) > 0 }.map { LineItem(it.name,it.price,cart[it.name] ?: 0) }
    val subtotal = lines.sumOf { it.price * it.qty }
    val tax = subtotal * ((gstPercent.toDoubleOrNull() ?: 0.0) / 100.0)
    val tip = if (customTip.isNotBlank()) customTip.toDoubleOrNull() ?: 0.0 else subtotal * tipPercent / 100.0
    val total = subtotal + tax + tip

    Scaffold(
        containerColor = Pale,
        topBar = {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restaurant, null, tint = Green, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(restaurant, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Restaurant Bill & QR Tips", fontSize = 11.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { screen = "Settings" }) { Icon(Icons.Default.Settings, null) }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Home","New Bill","History","Reports","Settings").forEach { item ->
                    NavigationBarItem(selected = screen == item, onClick = { screen = item },
                        icon = { Icon(when(item) {
                            "Home" -> Icons.Default.Home
                            "New Bill" -> Icons.Default.ReceiptLong
                            "History" -> Icons.Default.History
                            "Reports" -> Icons.Default.BarChart
                            else -> Icons.Default.Settings
                        }, null) }, label = { Text(item, fontSize = 10.sp) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            when(screen) {
                "Home" -> HomeScreen(restaurant, bills, onNew = { screen="New Bill" }, onMenu={screen="Menu"}, onPrinter={screen="Printer"}, onHistory={screen="History"}, onReports={screen="Reports"})
                "New Bill" -> NewBillScreen(menu, cart, table, {table=it}, {name,delta -> cart = cart.toMutableMap().apply { this[name] = ((this[name] ?: 0)+delta).coerceAtLeast(0) } }, subtotal, tax, total, gstPercent) { screen="Bill Summary" }
                "Bill Summary" -> BillSummaryScreen(lines, table, subtotal, tax, tip, total, tipPercent, {tipPercent=it;customTip=""}, customTip, {customTip=it}, onGenerate = {
                    val uri = "upi://pay?pa=${upi}&pn=${restaurant.replace(" ","%20")}&am=${"%.2f".format(Locale.US, total)}&cu=INR&tn=Restaurant%20Tip"
                    qrBitmap = makeQr(uri)
                    screen="QR Tip"
                })
                "QR Tip" -> QrScreen(qrBitmap, upi, total, onPrint={ screen="Print Preview" })
                "Print Preview" -> PrintPreviewScreen(restaurant, table, lines, subtotal, tax, tip, total, onPrint={
                    val id = System.currentTimeMillis()
                    val bill = Bill(id, table, lines, subtotal, tax, tip, total, SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()))
                    bills = listOf(bill) + bills
                    saveBills(prefs,bills)
                    status = if (selectedPrinter != null) {
                        try { printReceipt(selectedPrinter!!, restaurant, table, lines, subtotal, tax, tip, total, paperWidth); "Bill #${id.toString().takeLast(6)} saved and sent to printer." }
                        catch(e: Exception) { "Bill saved, but printing failed: ${e.message}" }
                    } else "Bill #${id.toString().takeLast(6)} saved. Pair/select a printer to print."
                    cart = emptyMap()
                    screen="Home"
                })
                "Menu" -> MenuScreen(menu, newName, {newName=it}, newPrice, {newPrice=it}, onAdd={
                    val p=newPrice.toDoubleOrNull()
                    if(newName.isNotBlank() && p!=null && p>=0) { menu=menu+MenuItem(newName,p,"Other");newName="";newPrice="" }
                }, onDelete={name -> menu=menu.filterNot{it.name==name}})
                "History" -> HistoryScreen(bills)
                "Reports" -> ReportsScreen(bills)
                "Settings" -> SettingsScreen(restaurant,{restaurant=it;prefs.edit().putString("restaurant",it).apply()},upi,{upi=it;prefs.edit().putString("upi",it).apply()},gstPercent,{gstPercent=it;prefs.edit().putString("gst",it).apply()},onPrinter={screen="Printer"})
                "Printer" -> PrinterScreen(context, requestBluetooth, selectedPrinter, {selectedPrinter=it}, paperWidth, { paperWidth=it; prefs.edit().putString("paperWidth", it).apply() }, onTest={ device, width ->
                    status = try { printTest(device, width); "Test print sent to ${width} printer." } catch(e:Exception) { "Print failed: ${e.message}" }
                })
                else -> HomeScreen(restaurant,bills,onNew={screen="New Bill"},onMenu={screen="Menu"},onPrinter={screen="Printer"},onHistory={screen="History"},onReports={screen="Reports"})
            }
            if(status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, color=Green, fontSize=12.sp, modifier=Modifier.clickable { status="" }.padding(4.dp))
            }
        }
    }
}

@Composable fun HomeScreen(restaurant:String,bills:List<Bill>,onNew:()->Unit,onMenu:()->Unit,onPrinter:()->Unit,onHistory:()->Unit,onReports:()->Unit) {
    val sales=bills.sumOf{it.total}; val tips=bills.sumOf{it.tip}
    Text("Hello, Manager 👋",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp))
    Text("Today's overview",color=Color.Gray)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=14.dp)) {
        StatCard("Sales","₹${money(sales)}",Modifier.weight(1f)); StatCard("Bills",bills.size.toString(),Modifier.weight(1f)); StatCard("Tips","₹${money(tips)}",Modifier.weight(1f))
    }
    val actions=listOf("New Bill" to Icons.Default.AddShoppingCart,"Menu" to Icons.Default.RestaurantMenu,"Past Bills" to Icons.Default.Receipt,"Reports" to Icons.Default.BarChart,"Printer" to Icons.Default.Print,"Settings" to Icons.Default.Settings)
    actions.chunked(2).forEach { pair ->
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
            pair.forEach { (name,icon) ->
                Card(Modifier.weight(1f).padding(vertical=5.dp).height(92.dp).clickable {
                    when(name){"New Bill"->onNew();"Menu"->onMenu();"Past Bills"->onHistory();"Reports"->onReports();"Printer"->onPrinter();else->onPrinter()}
                }, colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Icon(icon,null,tint=Green,modifier=Modifier.size(28.dp));Text(name,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    Text("Recent bills",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(top=16.dp,bottom=6.dp))
    bills.take(3).forEach { BillRow(it) }
}
@Composable fun StatCard(label:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) { Text(value,fontWeight=FontWeight.Bold,fontSize=18.sp,color=Green);Text(label,fontSize=11.sp,color=Color.Gray) }
    }
}
@Composable fun NewBillScreen(menu:List<MenuItem>,cart:Map<String,Int>,table:String,onTable:(String)->Unit,onQty:(String,Int)->Unit,subtotal:Double,tax:Double,total:Double,gst:String,onReview:()->Unit) {
    Text("New Bill",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    OutlinedTextField(table,onTable,label={Text("Table No.")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    LazyColumn(Modifier.weight(1f)) {
        items(menu) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(item.name,fontWeight=FontWeight.SemiBold);Text("₹${money(item.price)} · ${item.category}",fontSize=12.sp,color=Color.Gray) }
                IconButton(onClick={onQty(item.name,-1)}){Icon(Icons.Default.Remove,null)}
                Text((cart[item.name]?:0).toString(),fontWeight=FontWeight.Bold)
                IconButton(onClick={onQty(item.name,1)}){Icon(Icons.Default.Add,null,tint=Green)}
            }
            HorizontalDivider()
        }
    }
    Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text("Subtotal ₹${money(subtotal)}  ·  GST ${gst}% ₹${money(tax)}",fontSize=13.sp)
            Button(onClick=onReview,modifier=Modifier.fillMaxWidth(),enabled=cart.values.sum()>0,colors=ButtonDefaults.buttonColors(containerColor=Green)) { Text("Review Bill · ₹${money(total)}") }
        }
    }
}
@Composable fun BillSummaryScreen(lines:List<LineItem>,table:String,subtotal:Double,tax:Double,tip:Double,total:Double,tipPercent:Int,onTip:(Int)->Unit,custom:String,onCustom:(String)->Unit,onGenerate:()->Unit) {
    Text("Bill Summary",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text("Table: $table",fontWeight=FontWeight.Bold)
            lines.forEach { Text("${it.name} × ${it.qty}     ₹${money(it.price*it.qty)}",modifier=Modifier.padding(vertical=5.dp)) }
            HorizontalDivider()
            SummaryLine("Subtotal",subtotal); SummaryLine("GST",tax); SummaryLine("Total before tip",subtotal+tax)
            Text("Add Tip (Optional)",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(0,5,10,15,20).forEach { OutlinedButton(onClick={onTip(it)},colors=ButtonDefaults.outlinedButtonColors(contentColor=if(tipPercent==it) Green else Ink)){Text(if(it==0)"No tip" else "$it%")} } }
            OutlinedTextField(custom,onCustom,label={Text("Custom tip amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
            SummaryLine("Tip",tip);SummaryLine("Grand Total",total,true)
            Button(onClick=onGenerate,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Generate UPI QR")}
        }
    }
}
@Composable fun SummaryLine(label:String,value:Double,bold:Boolean=false) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal);Text("₹${money(value)}",fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,color=if(bold)Green else Ink) }
}
@Composable fun QrScreen(bitmap:Bitmap?,upi:String,total:Double,onPrint:()->Unit) {
    Text("Scan & Pay / Tip",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Scan with any UPI app",fontWeight=FontWeight.Bold)
            bitmap?.let { Image(it.asImageBitmap(),null,Modifier.size(240.dp).padding(10.dp)) }
            Text("UPI ID: $upi");Text("Amount: ₹${money(total)}",fontWeight=FontWeight.Bold,color=Green)
            Text("Payment status is not verified by this QR.",fontSize=11.sp,color=Color.Gray)
            Button(onClick=onPrint,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Continue to Print Preview")}
        }
    }
}
@Composable fun PrintPreviewScreen(restaurant:String,table:String,lines:List<LineItem>,subtotal:Double,tax:Double,tip:Double,total:Double,onPrint:()->Unit) {
    Text("Print Preview",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    Card(Modifier.fillMaxWidth().weight(1f),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(restaurant,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Restaurant Receipt")
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            Text("Table: $table")
            lines.forEach { Text("${it.name} × ${it.qty}     ₹${money(it.price*it.qty)}",modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            SummaryLine("Subtotal",subtotal);SummaryLine("GST",tax);SummaryLine("Tip",tip);SummaryLine("Grand Total",total,true)
            Spacer(Modifier.height(20.dp));Text("Thank you! Visit again.",color=Green,fontWeight=FontWeight.Bold)
        }
    }
    Button(onClick=onPrint,modifier=Modifier.fillMaxWidth().padding(vertical=10.dp),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Save Bill & Print")}
}
@Composable fun MenuScreen(menu:List<MenuItem>,name:String,onName:(String)->Unit,price:String,onPrice:(String)->Unit,onAdd:()->Unit,onDelete:(String)->Unit) {
    Text("Menu Management",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    OutlinedTextField(name,onName,label={Text("Item name")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(price,onPrice,label={Text("Price (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
    Button(onClick=onAdd,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Add Menu Item")}
    LazyColumn { items(menu) { item -> Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(item.name,fontWeight=FontWeight.SemiBold);Text("₹${money(item.price)}",color=Green)};IconButton(onClick={onDelete(item.name)}){Icon(Icons.Default.Delete,null,tint=Color.Red)} } ;HorizontalDivider() } }
}
@Composable fun HistoryScreen(bills:List<Bill>) {
    Text("Past Bills",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    LazyColumn { items(bills) { BillRow(it) } }
}
@Composable fun BillRow(b:Bill) {
    Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text("Bill #${b.id.toString().takeLast(6)} · ${b.table}",fontWeight=FontWeight.Bold);Text(b.time,fontSize=11.sp,color=Color.Gray)};Text("₹${money(b.total)}",fontWeight=FontWeight.Bold,color=Green) }
    }
}
@Composable fun ReportsScreen(bills:List<Bill>) {
    Text("Reports & Analytics",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    StatCard("Total Sales","₹${money(bills.sumOf{it.total})}",Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp));StatCard("Total Bills",bills.size.toString(),Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp));StatCard("Tips Collected","₹${money(bills.sumOf{it.tip})}",Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp));StatCard("Average Bill","₹${money(if(bills.isEmpty())0.0 else bills.map{it.total}.average())}",Modifier.fillMaxWidth())
}
@Composable fun SettingsScreen(restaurant:String,onRestaurant:(String)->Unit,upi:String,onUpi:(String)->Unit,gst:String,onGst:(String)->Unit,onPrinter:()->Unit) {
    Text("Settings",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    OutlinedTextField(restaurant,onRestaurant,label={Text("Restaurant name")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(upi,onUpi,label={Text("UPI ID (VPA)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(gst,onGst,label={Text("GST percentage")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
    Button(onClick=onPrinter,modifier=Modifier.fillMaxWidth().padding(top=10.dp),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Printer Settings")}
    Text("UPI QR is generated locally. This app does not confirm whether a payment was received.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=12.dp))
}

@Composable fun PrinterScreen(
    context:Context,
    requestPermission:()->Unit,
    selected:BluetoothDevice?,
    onSelect:(BluetoothDevice)->Unit,
    paperWidth:String,
    onWidth:(String)->Unit,
    onTest:(BluetoothDevice,String)->Unit
) {
    Text("Bluetooth Printer",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
    Text("Receipt paper width",fontWeight=FontWeight.SemiBold)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=8.dp)) {
        listOf("58mm","80mm").forEach { width ->
            FilterChip(selected=paperWidth==width,onClick={onWidth(width)},label={Text(width)})
        }
    }
    Text("Select the same width as the roll loaded in your printer.",fontSize=12.sp,color=Color.Gray)
    Button(onClick=requestPermission,modifier=Modifier.fillMaxWidth().padding(top=8.dp),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Grant Bluetooth Permission")}
    val adapter=remember { BluetoothAdapter.getDefaultAdapter() }
    val devices=remember { try { adapter?.bondedDevices?.toList() ?: emptyList() } catch(_:SecurityException){emptyList()} }
    Text("Paired devices",fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=10.dp))
    if(devices.isEmpty()) Text("No paired Bluetooth devices found. Pair your thermal printer in Android Bluetooth settings first.")
    devices.forEach { d ->
        Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{onSelect(d)},colors=CardDefaults.cardColors(containerColor=Color.White)) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text(d.name ?: "Bluetooth printer",fontWeight=FontWeight.Bold);Text(d.address,fontSize=11.sp)}
                if(selected?.address==d.address) Text("Selected",color=Green,fontWeight=FontWeight.Bold)
            }
        }
    }
    selected?.let { Button(onClick={onTest(it,paperWidth)},modifier=Modifier.fillMaxWidth().padding(top=10.dp),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Print Test Receipt (${paperWidth})")} }
}
fun makeQr(data:String):Bitmap {
    val matrix=MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,640,640)
    val bmp=Bitmap.createBitmap(640,640,Bitmap.Config.RGB_565)
    for(x in 0 until 640) for(y in 0 until 640) bmp.setPixel(x,y,if(matrix[x,y]) AndroidColor.BLACK else AndroidColor.WHITE)
    return bmp
}
fun printTest(device:BluetoothDevice, width:String) {
    val chars = if (width == "80mm") 48 else 32
    val socket=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
    socket.connect()
    try {
        val out=socket.outputStream
        out.write(byteArrayOf(0x1B,0x40))
        out.write(byteArrayOf(0x1B,0x61,0x01))
        out.write("RESTAURANT TEST PRINT\n".toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf(0x1B,0x61,0x00))
        out.write(("Paper width: $width\n"+"-".repeat(chars)+"\nBluetooth printer connected\n\n\n").toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf(0x1D,0x56,0x00))
        out.flush()
    } finally { socket.close() }
}
fun printReceipt(device:BluetoothDevice, restaurant:String, table:String, lines:List<LineItem>,
                 subtotal:Double, tax:Double, tip:Double, total:Double, width:String) {
    val chars = if (width == "80mm") 48 else 32
    fun row(label:String, value:String):String {
        val left = label.take(chars - value.length - 1)
        return left + " ".repeat((chars - left.length - value.length).coerceAtLeast(1)) + value + "\n"
    }
    val receipt = buildString {
        append(restaurant.uppercase().centerText(chars)).append("\n")
        append("RESTAURANT RECEIPT".centerText(chars)).append("\n")
        append("-".repeat(chars)).append("\n")
        append("Table: $table\n")
        append("Date: ${SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())}\n")
        append("-".repeat(chars)).append("\n")
        lines.forEach { line ->
            append("${line.name} x${line.qty}\n")
            append(row("","₹${money(line.price * line.qty)}"))
        }
        append("-".repeat(chars)).append("\n")
        append(row("Subtotal","₹${money(subtotal)}"))
        append(row("GST","₹${money(tax)}"))
        append(row("Tip","₹${money(tip)}"))
        append(row("GRAND TOTAL","₹${money(total)}"))
        append("-".repeat(chars)).append("\n")
        append("Thank you! Visit again.".centerText(chars)).append("\n\n\n")
    }
    val socket=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
    socket.connect()
    try {
        val out=socket.outputStream
        out.write(byteArrayOf(0x1B,0x40))
        out.write(byteArrayOf(0x1B,0x61,0x00))
        out.write(receipt.toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf(0x1D,0x56,0x00))
        out.flush()
    } finally { socket.close() }
}
fun String.centerText(width:Int):String {
    val s=take(width)
    val left=((width-s.length)/2).coerceAtLeast(0)
    return " ".repeat(left)+s
}
fun money(v:Double)=String.format(Locale.US,"%.2f",v)
fun saveBills(prefs:android.content.SharedPreferences,bills:List<Bill>) {
    val arr=JSONArray()
    bills.forEach { b ->
        val obj=JSONObject().put("id",b.id).put("table",b.table).put("subtotal",b.subtotal).put("tax",b.tax).put("tip",b.tip).put("total",b.total).put("time",b.time)
        val ls=JSONArray();b.lines.forEach{ls.put(JSONObject().put("name",it.name).put("price",it.price).put("qty",it.qty))}
        obj.put("lines",ls);arr.put(obj)
    }
    prefs.edit().putString("bills",arr.toString()).apply()
}
fun loadBills(prefs:android.content.SharedPreferences):List<Bill> = try {
    val arr=JSONArray(prefs.getString("bills","[]"))
    (0 until arr.length()).map { i ->
        val o=arr.getJSONObject(i);val ls=o.getJSONArray("lines")
        val lines=(0 until ls.length()).map{j->val l=ls.getJSONObject(j);LineItem(l.getString("name"),l.getDouble("price"),l.getInt("qty"))}
        Bill(o.getLong("id"),o.getString("table"),lines,o.getDouble("subtotal"),o.getDouble("tax"),o.getDouble("tip"),o.getDouble("total"),o.getString("time"))
    }
} catch(_:Exception){emptyList()}
