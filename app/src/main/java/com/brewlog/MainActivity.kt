package com.brewlog

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast

private val Ink = Color(0xFF30251E)
private val Muted = Color(0xFF85766B)
private val Cream = Color(0xFFF7F3ED)
private val Coffee = Color(0xFF754A2D)
private val Green = Color(0xFF56664A)

data class Shot(val id: Long, val date: String, val bean: String, val roaster: String, val roastDate: String, val origin: String, val process: String, val dose: String, val yield: String, val time: String, val ratio: String, val grinder: String, val grind: String, val brewer: String, val basket: String, val temp: String, val pressure: String, val preinfusion: String, val water: String, val prep: String, val taste: String, val rating: String, val notes: String, val showerScreenUsed: Boolean = false, val roastLevel: String = "", val acidity: String = "", val sourness: String = "", val sweetness: String = "", val body: String = "", val crema: String = "", val photoUris:List<String> = emptyList())
data class CoffeeBean(val id: Long, val brand: String, val name: String, val origin: String, val process: String)
data class CoffeeBag(val id: Long, val beanId: Long, val roastDate: String, val done: Boolean = false)
data class BrewMachine(val id: Long, val name: String, val type: String)
data class Grinder(val id: Long, val name: String)
data class PortafilterBasket(val id: Long, val brand: String, val model: String, val size: String, val doseRange: String)
data class CurrentSetup(val beanId: Long = 0, val machineId: Long = 0, val grinderId: Long = 0, val basketId: Long = 0, val bagId: Long = 0)
private data class CoffeeHistoryEntry(val brand:String,val name:String,val origin:String,val process:String,val bags:List<CoffeeBag>,val shots:List<Shot>) {
    val averageScore:Double? get() = shots.mapNotNull { it.rating.toDoubleOrNull() }.takeIf { it.isNotEmpty() }?.average()
}

private val fields = listOf("Coffee / bean name", "Roaster", "Roast date (YYYY-MM-DD)", "Origin", "Process / variety", "Dose (g)", "Yield (g)", "Brew time (sec)", "Grinder", "Grind setting", "Coffee tool / machine", "Basket / filter", "Brew temperature (°C)", "Pressure (bar)", "Pre-infusion", "Water / profile", "Puck prep", "Taste / aroma", "Rating (1–5)", "Notes")
private data class LogEntryOption(val key:String,val title:String,val description:String)
private val logEntryOptions = listOf(
    LogEntryOption("bean", "Bean details", "Choose a saved bean or enter coffee details."),
    LogEntryOption("bag", "Coffee bag", "Select a saved bag and its roast date."),
    LogEntryOption("machine", "Brewing machine", "Choose an espresso machine, moka pot, French press, or other tool."),
    LogEntryOption("grinder", "Grinder and grind setting", "Record the grinder and its setting."),
    LogEntryOption("basket", "Portafilter basket", "Record the basket used for this shot."),
    LogEntryOption("dose", "Dose", "Ground coffee weight in grams."),
    LogEntryOption("yield", "Yield", "Brewed coffee weight in grams."),
    LogEntryOption("brewTime", "Brew time", "Extraction time in seconds."),
    LogEntryOption("temperature", "Brew temperature", "Water temperature in °C."),
    LogEntryOption("pressure", "Pressure", "Brew pressure in bar."),
    LogEntryOption("preinfusion", "Pre-infusion", "Pre-infusion time or setting."),
    LogEntryOption("water", "Water profile", "Water source or mineral profile."),
    LogEntryOption("puckPrep", "Puck preparation", "WDT, distribution, tamping, and other prep."),
    LogEntryOption("showerScreen", "Shower screen", "Whether a puck shower screen was used."),
    LogEntryOption("taste", "Taste and aroma notes", "Freeform tasting notes."),
    LogEntryOption("roastLevel", "Roast level", "Light, medium, or dark."),
    LogEntryOption("overallScore", "Overall score", "Rate the shot from 1 to 5."),
    LogEntryOption("acidity", "Acidity score", "Rate acidity from 1 to 5."),
    LogEntryOption("sourness", "Sourness score", "Rate sourness from 1 to 5."),
    LogEntryOption("sweetness", "Sweetness score", "Rate sweetness from 1 to 5."),
    LogEntryOption("body", "Body score", "Rate body from 1 to 5."),
    LogEntryOption("crema", "Crema score", "Rate crema from 1 to 5."),
    LogEntryOption("notes", "Additional notes", "Anything else about this brew."),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { BrewlogApp() } }
}

@Composable fun BrewlogApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("brewlog", 0) }
    var shots by remember { mutableStateOf(loadShots(prefs.getString("shots", "[]") ?: "[]")) }
    var beans by remember { mutableStateOf(loadBeans(prefs.getString("beans", "[]") ?: "[]")) }
    var bags by remember { mutableStateOf(loadBags(prefs.getString("bags", "[]") ?: "[]")) }
    var machines by remember { mutableStateOf(loadMachines(prefs.getString("machines", "[]") ?: "[]")) }
    var grinders by remember { mutableStateOf(loadGrinders(prefs.getString("grinders", "[]") ?: "[]")) }
    var baskets by remember { mutableStateOf(loadBaskets(prefs.getString("baskets", "[]") ?: "[]")) }
    var setup by remember { mutableStateOf(loadSetup(prefs.getString("setup", "") ?: "")) }
    var enabledLogEntries by remember { mutableStateOf(loadLogSettings(prefs.getString("log_settings", "{}") ?: "{}")) }
    var editing by remember { mutableStateOf<Shot?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Shots") }
    var importPayload by remember { mutableStateOf<String?>(null) }
    fun save(items: List<Shot>) { shots = items; prefs.edit().putString("shots", encode(items)).apply() }
    fun saveBeans(items: List<CoffeeBean>) { beans = items; prefs.edit().putString("beans", encodeBeans(items)).apply() }
    fun saveBags(items: List<CoffeeBag>) { bags = items; prefs.edit().putString("bags", encodeBags(items)).apply() }
    fun saveMachines(items: List<BrewMachine>) { machines = items; prefs.edit().putString("machines", encodeMachines(items)).apply() }
    fun saveGrinders(items: List<Grinder>) { grinders = items; prefs.edit().putString("grinders", encodeGrinders(items)).apply() }
    fun saveBaskets(items: List<PortafilterBasket>) { baskets = items; prefs.edit().putString("baskets", encodeBaskets(items)).apply() }
    fun saveSetup(item: CurrentSetup) { setup = item; prefs.edit().putString("setup", encodeSetup(item)).apply() }
    fun saveLogSettings(item: Map<String,Boolean>) { enabledLogEntries = item; prefs.edit().putString("log_settings", encodeLogSettings(item)).apply() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(encodeBackup(shots,beans,bags,machines,grinders,baskets,setup,enabledLogEntries).toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context,"Brewlog backup exported",Toast.LENGTH_SHORT).show()
        } catch(_:Exception) { Toast.makeText(context,"Could not export backup",Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try {
            importPayload=context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if(importPayload==null) Toast.makeText(context,"Could not read backup",Toast.LENGTH_LONG).show()
        } catch(_:Exception) { importPayload=null; Toast.makeText(context,"Could not read backup",Toast.LENGTH_LONG).show() }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Coffee, onPrimary = Color.White, background = Cream, surface = Color.White, onSurface = Ink)) {
        Box(Modifier.fillMaxSize().background(Cream)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().background(Ink).padding(start=22.dp,end=22.dp,top=26.dp,bottom=22.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("BREWLOG  ·  COFFEE JOURNAL",Modifier.weight(1f),color=Color(0xFFD7B68E),fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp)
                        IconButton(onClick={page="Settings"},modifier=Modifier.size(56.dp).offset(x=10.dp,y=8.dp)) { Text("⚙",color=Color(0xFFD7B68E),fontSize=30.sp) }
                    }
                    Spacer(Modifier.height(14.dp)); Text(when(page) { "Library" -> "Your coffee\nand equipment."; "Coffee" -> "Your coffee\njourney."; "Photos" -> "Every shot,\nin pictures."; "Setup" -> "Ready for\nyour next brew."; else -> "Every shot\ntells a story." }, color=Color.White, fontSize=30.sp, lineHeight=34.sp, fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        StatCard("${shots.size}", "SHOTS LOGGED", Modifier.weight(1f))
                        StatCard(if (shots.isEmpty()) "—" else String.format(Locale.US,"%.1f",shots.mapNotNull{it.rating.toFloatOrNull()}.average().takeIf{it.isFinite()} ?: 0.0), "AVG RATING", Modifier.weight(1f))
                        StatCard(shots.firstOrNull()?.ratio?.takeIf{it.isNotBlank()} ?: "—", "LAST RATIO", Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp, vertical=10.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Shots", "Coffee", "Photos", "Library", "Setup", "Settings").forEach { section ->
                        FilterChip(selected=page==section,onClick={page=section},label={Text(section)},shape=RoundedCornerShape(12.dp))
                    }
                }
                when(page) {
                    "Coffee" -> CoffeeHistoryPage(beans,bags,shots)
                    "Photos" -> PhotoHistoryPage(shots)
                    "Library" -> LibraryPage(beans, bags, machines, grinders, baskets, ::saveBeans, ::saveBags, ::saveMachines, ::saveGrinders, ::saveBaskets)
                    "Setup" -> SetupPage(beans, bags, machines, grinders, baskets, setup, ::saveSetup)
                    "Settings" -> LogSettingsPage(enabledLogEntries, ::saveLogSettings,
                        onExport={exportLauncher.launch("brewlog-backup.json")}, onImport={importLauncher.launch(arrayOf("application/json","text/json","*/*"))})
                    else -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Your shots", fontSize=23.sp, fontWeight=FontWeight.Bold); Text("A little better with every brew.", color=Muted, fontSize=13.sp) }
                            Button(onClick={ editing=null; showForm=true }, shape=RoundedCornerShape(14.dp), contentPadding=PaddingValues(horizontal=16.dp, vertical=12.dp)) { Text("＋  Log shot", fontWeight=FontWeight.SemiBold) }
                        }
                        OutlinedTextField(query,{query=it}, Modifier.fillMaxWidth().padding(horizontal=20.dp), placeholder={Text("Search coffee, grinder, notes…", color=Muted)}, singleLine=true, shape=RoundedCornerShape(14.dp), colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White))
                        Spacer(Modifier.height(12.dp))
                        val filtered = shots.filter { listOf(it.bean,it.roaster,it.grinder,it.brewer,it.notes).any { s -> s.contains(query,true) } }
                        if (filtered.isEmpty()) {
                            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                                Text("☕", fontSize=42.sp); Spacer(Modifier.height(12.dp)); Text(if(shots.isEmpty()) "Your first brew starts here" else "No shots found", fontSize=18.sp, fontWeight=FontWeight.SemiBold)
                                Text(if(shots.isEmpty()) "Log the details, taste the difference, and find your perfect cup." else "Try a different search.", color=Muted, fontSize=14.sp)
                                if(shots.isEmpty()) { Spacer(Modifier.height(16.dp)); Text("START YOUR JOURNAL  →", color=Coffee, fontSize=12.sp, fontWeight=FontWeight.Bold, modifier=Modifier.clickable { showForm=true }) }
                            }
                        } else LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=28.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            items(filtered, key={it.id}) { shot -> ShotCard(shot, onClick={editing=shot;showForm=true}, onDelete={save(shots.filterNot{it.id==shot.id})}) }
                        }
                    }
                }
            }
        }
        if(showForm) ShotForm(editing, beans, bags, machines, grinders, baskets, setup, enabledLogEntries, onDismiss={showForm=false}, onSave={shot -> save(if(editing==null) listOf(shot)+shots else shots.map{if(it.id==shot.id)shot else it});showForm=false})
        importPayload?.let { payload ->
            val valid=isValidBackup(payload)
            AlertDialog(onDismissRequest={importPayload=null},title={Text("Replace app data?")},text={Text(if(valid) "Importing this backup replaces your shots, beans, bags, equipment, setup, and field settings on this device." else "This file is not a valid Brewlog backup.")},
                confirmButton={ TextButton(enabled=valid,onClick={
                    try {
                        val backup=JSONObject(payload)
                        val newShots=loadShots(backup.getJSONArray("shots").toString())
                        val newBeans=loadBeans(backup.getJSONArray("beans").toString())
                        val newBags=loadBags(backup.getJSONArray("bags").toString())
                        val newMachines=loadMachines(backup.getJSONArray("machines").toString())
                        val newGrinders=loadGrinders(backup.getJSONArray("grinders").toString())
                        val newBaskets=loadBaskets(backup.getJSONArray("baskets").toString())
                        val newSetup=loadSetup(backup.getJSONObject("setup").toString())
                        val newSettings=loadLogSettings(backup.getJSONObject("logSettings").toString())
                        save(newShots); saveBeans(newBeans); saveBags(newBags); saveMachines(newMachines); saveGrinders(newGrinders); saveBaskets(newBaskets); saveSetup(newSetup); saveLogSettings(newSettings)
                        Toast.makeText(context,"Brewlog backup imported",Toast.LENGTH_SHORT).show()
                    } catch(_:Exception) { Toast.makeText(context,"Backup file is incomplete or invalid",Toast.LENGTH_LONG).show() }
                    importPayload=null
                }) { Text("Import") } },dismissButton={TextButton(onClick={importPayload=null}){Text("Cancel")}})
        }
    }
}

@Composable private fun LogSettingsPage(enabledEntries:Map<String,Boolean>,onSave:(Map<String,Boolean>)->Unit,onExport:()->Unit,onImport:()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Backup & restore",fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text("Export all shots and saved coffee data to a JSON file, or restore from a Brewlog backup.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp,bottom=8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick=onExport,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)) { Text("Export data") }
                        OutlinedButton(onClick=onImport,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)) { Text("Import data") }
                    }
                }
            }
        }
        item {
            Text("Choose which fields appear in the shot form. Turning a field off hides it from shot entry; existing shot data is kept.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(bottom=8.dp))
        }
        items(logEntryOptions,key={it.key}) { option ->
            val isEnabled=enabledEntries[option.key] ?: true
            Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Row(Modifier.fillMaxWidth().padding(start=14.dp,end=10.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical=4.dp)) {
                        Text(option.title,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        Text(option.description,color=Muted,fontSize=11.sp)
                    }
                    Switch(checked=isEnabled,onCheckedChange={value -> onSave(enabledEntries + (option.key to value))})
                }
            }
        }
    }
}

@Composable private fun CoffeeHistoryPage(beans:List<CoffeeBean>,bags:List<CoffeeBag>,shots:List<Shot>) {
    var highestFirst by remember { mutableStateOf(true) }
    val history=remember(beans,bags,shots) { buildCoffeeHistory(beans,bags,shots) }
        .sortedWith(compareBy<CoffeeHistoryEntry> { entry -> entry.averageScore?.let { if(highestFirst) -it else it } ?: Double.MAX_VALUE }
            .thenBy { it.brand.lowercase(Locale.ROOT) }.thenBy { it.name.lowercase(Locale.ROOT) })
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Coffee history",fontWeight=FontWeight.Bold,fontSize=23.sp)
                Text("Bags and shot scores by coffee.",color=Muted,fontSize=13.sp)
            }
            FilterChip(selected=highestFirst,onClick={highestFirst=!highestFirst},label={Text(if(highestFirst) "Score ↓" else "Score ↑")},shape=RoundedCornerShape(12.dp))
        }
        if(history.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text("No coffee history yet",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                Text("Add beans and bags in Library or log a shot to start tracking coffees.",color=Muted,fontSize=13.sp)
            }
        } else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(history,key={coffeeHistoryKey(it.brand,it.name)}) { entry ->
                Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name.ifBlank { "Unnamed coffee" },fontWeight=FontWeight.Bold,fontSize=17.sp)
                                Text(entry.brand.ifBlank { "Unknown roaster" },color=Muted,fontSize=12.sp)
                                val details=listOf(entry.origin,entry.process).filter{it.isNotBlank()}.joinToString(" · ")
                                if(details.isNotBlank()) Text(details,color=Muted,fontSize=12.sp)
                            }
                            Column(horizontalAlignment=Alignment.End) {
                                Text(entry.averageScore?.let { String.format(Locale.US,"%.1f",it) } ?: "—",fontWeight=FontWeight.Bold,fontSize=22.sp,color=Coffee)
                                Text("AVG · ${entry.shots.size} shots",fontSize=10.sp,color=Muted,fontWeight=FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical=10.dp),color=Cream)
                        Text("BAGS BOUGHT · ${entry.bags.size}",fontSize=10.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=0.7.sp)
                        if(entry.bags.isEmpty()) Text("No bag records",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                        else entry.bags.sortedByDescending { it.id }.forEach { bag ->
                            Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("Roasted ${bag.roastDate}",Modifier.weight(1f),fontSize=12.sp,color=Ink)
                                Text(if(bag.done) "DONE" else "OPEN",fontSize=10.sp,color=if(bag.done) Muted else Green,fontWeight=FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("SHOT SCORES · ${entry.shots.size}",fontSize=10.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=0.7.sp)
                        if(entry.shots.isEmpty()) Text("No shots logged",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                        else entry.shots.sortedByDescending { it.date }.take(5).forEach { shot ->
                            Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(shot.date,Modifier.weight(1f),fontSize=12.sp,color=Ink)
                                Text(shot.rating.takeIf{it.isNotBlank()}?.let { "★ $it" } ?: "Not rated",fontSize=12.sp,color=Coffee,fontWeight=FontWeight.SemiBold)
                            }
                        }
                        if(entry.shots.size>5) Text("+ ${entry.shots.size-5} earlier shots",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                    }
                }
            }
        }
    }
}

private data class ShotPhoto(val shot:Shot,val uri:String,val index:Int)
@Composable private fun PhotoHistoryPage(shots:List<Shot>) {
    var preview by remember { mutableStateOf<String?>(null) }
    val photos=remember(shots) { shots.flatMap { shot -> shot.photoUris.mapIndexed { index,uri -> ShotPhoto(shot,uri,index) } }.sortedWith(compareByDescending<ShotPhoto> { shotTimestamp(it.shot) }.thenByDescending { it.index }) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column {
                Text("Shot photos",fontWeight=FontWeight.Bold,fontSize=23.sp)
                Text("Newest first · ${photos.size} photos",color=Muted,fontSize=13.sp)
            }
        }
        if(photos.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text("No shot photos yet",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                Text("Edit or log a shot and attach photos from your gallery.",color=Muted,fontSize=13.sp)
            }
        } else LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            gridItems(photos,key={"${it.shot.id}:${it.index}"}) { photo ->
                Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.clickable { preview=photo.uri }) {
                    Column {
                        UriImage(photo.uri,Modifier.fillMaxWidth().aspectRatio(1f))
                        Column(Modifier.padding(horizontal=10.dp,vertical=8.dp)) {
                            Text(photo.shot.bean.ifBlank { "Untitled coffee" },fontWeight=FontWeight.SemiBold,fontSize=13.sp,maxLines=1)
                            Text(photo.shot.date,color=Muted,fontSize=10.sp,maxLines=1)
                            if(photo.shot.rating.isNotBlank()) Text("★ ${photo.shot.rating}",color=Coffee,fontWeight=FontWeight.Bold,fontSize=11.sp)
                        }
                    }
                }
            }
        }
    }
    preview?.let { uri ->
        Dialog(onDismissRequest={preview=null}) {
            Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth().clickable { preview=null }) {
                UriImage(uri,Modifier.fillMaxWidth().height(480.dp),contentScale=ContentScale.Fit)
            }
        }
    }
}

@Composable private fun UriImage(uri:String,modifier:Modifier=Modifier,contentScale:ContentScale=ContentScale.Crop) {
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue=null,key1=uri) {
        value=withContext(Dispatchers.IO) { try { loadPhotoBitmap(context,uri,1000) } catch(_:Exception) { null } }
    }
    if(bitmap==null) Box(modifier.background(Color(0xFFE9E3DC)),contentAlignment=Alignment.Center) { Text("Photo unavailable",color=Muted,fontSize=11.sp) }
    else Image(bitmap!!.asImageBitmap(),contentDescription="Photo attached to a coffee shot",modifier=modifier,contentScale=contentScale)
}

private fun loadPhotoBitmap(context:android.content.Context,uriString:String,maxDimension:Int):Bitmap? {
    val uri=Uri.parse(uriString)
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    val boundsStream=context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it,null,bounds) }
    var sample=1
    while(bounds.outWidth/sample>maxDimension || bounds.outHeight/sample>maxDimension) sample*=2
    val options=BitmapFactory.Options().apply { inSampleSize=sample.coerceAtLeast(1) }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
}

private fun shotTimestamp(shot:Shot):Long = try {
    SimpleDateFormat("MMM d, yyyy · HH:mm",Locale.getDefault()).parse(shot.date)?.time ?: shot.id
} catch(_:Exception) { try { SimpleDateFormat("MMM d, yyyy · HH:mm",Locale.US).parse(shot.date)?.time ?: shot.id } catch(_:Exception) { shot.id } }

private data class MutableCoffeeHistory(var brand:String,var name:String,var origin:String,var process:String,val bags:MutableList<CoffeeBag> = mutableListOf(),val shots:MutableList<Shot> = mutableListOf())
private fun coffeeHistoryKey(brand:String,name:String)="${brand.trim().lowercase(Locale.ROOT)}\u0000${name.trim().lowercase(Locale.ROOT)}"
private fun buildCoffeeHistory(beans:List<CoffeeBean>,bags:List<CoffeeBag>,shots:List<Shot>):List<CoffeeHistoryEntry> {
    val grouped=linkedMapOf<String,MutableCoffeeHistory>()
    beans.forEach { bean -> grouped.getOrPut(coffeeHistoryKey(bean.brand,bean.name)) { MutableCoffeeHistory(bean.brand,bean.name,bean.origin,bean.process) } }
    bags.forEach { bag -> beans.firstOrNull{it.id==bag.beanId}?.let { bean -> grouped.getOrPut(coffeeHistoryKey(bean.brand,bean.name)) { MutableCoffeeHistory(bean.brand,bean.name,bean.origin,bean.process) }.bags.add(bag) } }
    shots.forEach { shot -> grouped.getOrPut(coffeeHistoryKey(shot.roaster,shot.bean)) { MutableCoffeeHistory(shot.roaster,shot.bean,shot.origin,shot.process) }.shots.add(shot) }
    return grouped.values.map { CoffeeHistoryEntry(it.brand,it.name,it.origin,it.process,it.bags.toList(),it.shots.toList()) }
}

@Composable private fun LibraryPage(
    beans: List<CoffeeBean>, bags:List<CoffeeBag>, machines: List<BrewMachine>, grinders: List<Grinder>, baskets: List<PortafilterBasket>,
    saveBeans: (List<CoffeeBean>) -> Unit, saveBags:(List<CoffeeBag>)->Unit, saveMachines: (List<BrewMachine>) -> Unit, saveGrinders: (List<Grinder>) -> Unit, saveBaskets: (List<PortafilterBasket>) -> Unit,
) {
    var dialog by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Beans") }
    var showDoneBags by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Beans","Bags","Gear").forEach { FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)},shape=RoundedCornerShape(12.dp)) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(horizontal=20.dp, vertical=8.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                "Beans" -> item { LibrarySection("Beans", beans.size, "＋ Add bean", { dialog="bean" }) {
                    beans.forEach { bean -> LibraryRow(beanLabel(bean), listOf(bean.origin, bean.process).filter{it.isNotBlank()}.joinToString("  ·  "), { saveBeans(beans.filterNot{it.id==bean.id}); saveBags(bags.filterNot{it.beanId==bean.id}) }) }
                } }
                "Bags" -> item { LibrarySection("Bags", bags.count{!it.done}, "＋ Add bag", { dialog="bag" }) {
                    val doneCount=bags.count{it.done}
                    if(doneCount>0) TextButton(onClick={showDoneBags=!showDoneBags}) { Text(if(showDoneBags) "Hide done bags" else "Show done bags ($doneCount)") }
                    bags.filter{showDoneBags || !it.done}.forEach { bag ->
                        LibraryRow(bagLabel(bag,beans), "Roasted ${bag.roastDate}", { saveBags(bags.filterNot{it.id==bag.id}) }, onAction={saveBags(bags.map{if(it.id==bag.id)it.copy(done=!it.done) else it})}, actionLabel=if(bag.done) "RESTORE" else "DONE")
                    }
                } }
                else -> {
                    item { LibrarySection("Brewing machines", machines.size, "＋ Add machine", { dialog="machine" }) {
                        machines.forEach { machine -> LibraryRow(machine.name, machine.type, { saveMachines(machines.filterNot{it.id==machine.id}) }) }
                    } }
                    item { LibrarySection("Grinders", grinders.size, "＋ Add grinder", { dialog="grinder" }) {
                        grinders.forEach { grinder -> LibraryRow(grinder.name, "", { saveGrinders(grinders.filterNot{it.id==grinder.id}) }) }
                    } }
                    item { LibrarySection("Portafilter baskets", baskets.size, "＋ Add basket", { dialog="basket" }) {
                        baskets.forEach { basket -> LibraryRow(basketLabel(basket), listOf(basket.size,basket.doseRange).filter{it.isNotBlank()}.joinToString("  ·  "), { saveBaskets(baskets.filterNot{it.id==basket.id}) }) }
                    } }
                }
            }
        }
    }
    when(dialog) {
        "bean" -> BeanEditorDialog(onDismiss={dialog=""}, onSave={bean -> saveBeans(listOf(bean)+beans);dialog=""})
        "bag" -> BagEditorDialog(beans,onDismiss={dialog=""},onSave={bag -> saveBags(listOf(bag)+bags);dialog=""})
        "machine" -> MachineEditorDialog(onDismiss={dialog=""}, onSave={machine -> saveMachines(listOf(machine)+machines);dialog=""})
        "grinder" -> GrinderEditorDialog(onDismiss={dialog=""}, onSave={grinder -> saveGrinders(listOf(grinder)+grinders);dialog=""})
        "basket" -> BasketEditorDialog(onDismiss={dialog=""}, onSave={basket -> saveBaskets(listOf(basket)+baskets);dialog=""})
    }
}

@Composable private fun LibrarySection(title:String, count:Int, action:String, onAdd:()->Unit, content:@Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold); Text("$count saved",fontSize=12.sp,color=Muted) }
                TextButton(onClick=onAdd) { Text(action) }
            }
            Spacer(Modifier.height(6.dp)); content()
        }
    }
}

@Composable private fun LibraryRow(title:String, subtitle:String, onDelete:()->Unit, onAction:()->Unit=onDelete, actionLabel:String="DELETE") {
    Row(Modifier.fillMaxWidth().padding(vertical=7.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp); if(subtitle.isNotBlank()) Text(subtitle,color=Muted,fontSize=12.sp) }
        if(actionLabel!="DELETE") Text(actionLabel,color=Coffee,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable(onClick=onAction).padding(8.dp))
        Text("DELETE",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable(onClick=onDelete).padding(8.dp))
    }
}

@Composable private fun BeanEditorDialog(onDismiss:()->Unit,onSave:(CoffeeBean)->Unit) {
    var brand by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var origin by remember { mutableStateOf("") }; var process by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Add a bean",fontWeight=FontWeight.Bold)}, text={
        Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState())) {
            FormText("Brand / roaster",brand,{brand=it}); FormText("Coffee name",name,{name=it}); FormText("Origin",origin,{origin=it}); FormText("Variety / process (optional)",process,{process=it})
        }
    }, confirmButton={TextButton(onClick={onSave(CoffeeBean(System.currentTimeMillis(),brand.trim(),name.trim(),origin.trim(),process.trim()))}){Text("Save bean")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun BagEditorDialog(beans:List<CoffeeBean>,onDismiss:()->Unit,onSave:(CoffeeBag)->Unit) {
    var beanId by remember(beans) { mutableLongStateOf(beans.firstOrNull()?.id ?: 0L) }
    var roastDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add a coffee bag",fontWeight=FontWeight.Bold)},text={
        Column(Modifier.fillMaxWidth()) {
            ChoiceField("Coffee",beans.firstOrNull{it.id==beanId}?.let(::beanLabel) ?: "Choose existing coffee",beans.map(::beanLabel)){ selected -> beanId=beans.first{beanLabel(it)==selected}.id }
            DatePickerField("Roast date",roastDate.ifBlank{"Choose roast date"}) { showDatePicker=true }
        }
    },confirmButton={TextButton(enabled=beanId!=0L&&roastDate.isNotBlank(),onClick={onSave(CoffeeBag(System.currentTimeMillis(),beanId,roastDate))}){Text("Save bag")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(showDatePicker) CalendarDateDialog(roastDate,onDismiss={showDatePicker=false},onSelect={roastDate=it;showDatePicker=false})
}

private val machineTypes = listOf("Espresso machine", "Moka pot", "French press", "Pour-over", "AeroPress", "Lever machine", "Other")

@Composable private fun MachineEditorDialog(onDismiss:()->Unit,onSave:(BrewMachine)->Unit) {
    var name by remember { mutableStateOf("") }; var type by remember { mutableStateOf(machineTypes.first()) }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Add brewing machine",fontWeight=FontWeight.Bold)}, text={
        Column(Modifier.fillMaxWidth()) { FormText("Name / model",name,{name=it}); ChoiceField("Machine type",type,machineTypes){type=it} }
    }, confirmButton={TextButton(onClick={onSave(BrewMachine(System.currentTimeMillis(),name.trim(),type))}){Text("Save machine")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun GrinderEditorDialog(onDismiss:()->Unit,onSave:(Grinder)->Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Add grinder",fontWeight=FontWeight.Bold)}, text={FormText("Brand / model",name,{name=it})}, confirmButton={TextButton(onClick={onSave(Grinder(System.currentTimeMillis(),name.trim()))}){Text("Save grinder")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun BasketEditorDialog(onDismiss:()->Unit,onSave:(PortafilterBasket)->Unit) {
    var brand by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }; var size by remember { mutableStateOf("") }; var doseRange by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add portafilter basket",fontWeight=FontWeight.Bold)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=500.dp).verticalScroll(rememberScrollState())) {
            FormText("Brand / maker",brand,{brand=it}); FormText("Model / basket name",model,{model=it}); FormText("Size (e.g. 58 mm)",size,{size=it}); FormText("Dose range (e.g. 18–20 g)",doseRange,{doseRange=it})
        }
    },confirmButton={TextButton(onClick={onSave(PortafilterBasket(System.currentTimeMillis(),brand.trim(),model.trim(),size.trim(),doseRange.trim()))}){Text("Save basket")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun FormText(label:String,value:String,onValue:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(bottom=8.dp),singleLine=true,shape=RoundedCornerShape(12.dp))
}

@Composable private fun RatingPicker(label:String,value:String,helper:String,onSelect:(String)->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(label,fontSize=14.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            Text(helper,color=Muted,fontSize=10.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                val selected=value==score.toString()
                Surface(onClick={onSelect(score.toString())},shape=RoundedCornerShape(10.dp),color=if(selected) Coffee else Cream,modifier=Modifier.weight(1f)) {
                    Box(Modifier.height(38.dp),contentAlignment=Alignment.Center) { Text(score.toString(),color=if(selected) Color.White else Ink,fontWeight=FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable private fun ShotInput(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false,multiline:Boolean=false) {
    OutlinedTextField(
        value=value,
        onValueChange=onChange,
        label={Text(label)},
        modifier=Modifier.fillMaxWidth().padding(bottom=7.dp),
        singleLine=!multiline,
        shape=RoundedCornerShape(12.dp),
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),
    )
}

@Composable private fun CollapsibleSection(title:String,expanded:Boolean,onClick:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),fontSize=12.sp,color=Coffee,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Text(if(expanded) "⌃" else "⌄",fontSize=18.sp,color=Coffee,fontWeight=FontWeight.Bold)
            }
            if(expanded) Column(content=content)
        }
    }
}

@Composable private fun ChoiceField(label:String,value:String,choices:List<String>,onSelect:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
        Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.padding(start=4.dp,bottom=4.dp))
        Box {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=13.dp)) {
                Text(value,Modifier.weight(1f),color=Ink); Text("⌄",color=Muted)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.background(Color.White)) {
                choices.forEach { choice -> DropdownMenuItem(text={Text(choice)},onClick={onSelect(choice);expanded=false}) }
            }
        }
    }
}

@Composable private fun DatePickerField(label:String,value:String,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
        Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.padding(start=4.dp,bottom=4.dp))
        OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=13.dp)) {
            Text(value,Modifier.weight(1f),color=if(value.startsWith("Choose")) Muted else Ink); Text("▦",color=Coffee)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CalendarDateDialog(value:String,onDismiss:()->Unit,onSelect:(String)->Unit) {
    val initialMillis = try { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() } catch(_:Exception) { null }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis=initialMillis)
    DatePickerDialog(
        onDismissRequest=onDismiss,
        confirmButton={ TextButton(enabled=datePickerState.selectedDateMillis!=null,onClick={
            datePickerState.selectedDateMillis?.let { millis -> onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
        }) { Text("Select date") } },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}},
    ) { DatePicker(state=datePickerState,showModeToggle=false) }
}

@Composable private fun SetupPage(beans:List<CoffeeBean>,bags:List<CoffeeBag>,machines:List<BrewMachine>,grinders:List<Grinder>,baskets:List<PortafilterBasket>,setup:CurrentSetup,onSave:(CurrentSetup)->Unit) {
    var beanId by remember(setup,beans) { mutableLongStateOf(setup.beanId) }
    var machineId by remember(setup,machines) { mutableLongStateOf(setup.machineId) }
    var grinderId by remember(setup,grinders) { mutableLongStateOf(setup.grinderId) }
    var basketId by remember(setup,baskets) { mutableLongStateOf(setup.basketId) }
    var bagId by remember(setup,bags) { mutableLongStateOf(setup.bagId) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp)) {
        Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Current setup",fontWeight=FontWeight.Bold,fontSize=20.sp)
                Spacer(Modifier.height(4.dp)); Text("These choices will prefill your next shot log.",color=Muted,fontSize=13.sp)
                Spacer(Modifier.height(18.dp))
                ChoiceField("Bean",beans.firstOrNull{it.id==beanId}?.let{beanLabel(it)} ?: "Choose a bean",beans.map(::beanLabel)){ selected -> beanId=beans.first{beanLabel(it)==selected}.id; bagId=0L }
                val activeBags=bags.filterNot{it.done}
                ChoiceField("Coffee bag",activeBags.firstOrNull{it.id==bagId}?.let{bagLabel(it,beans)} ?: "Choose a bag",activeBags.map{bagLabel(it,beans)}){ selected ->
                    val bag=activeBags.first{bagLabel(it,beans)==selected}; bagId=bag.id; beanId=bag.beanId
                }
                ChoiceField("Brewing machine",machines.firstOrNull{it.id==machineId}?.let{machineLabel(it)} ?: "Choose a machine",machines.map(::machineLabel)){ selected -> machineId=machines.first{machineLabel(it)==selected}.id }
                ChoiceField("Grinder",grinders.firstOrNull{it.id==grinderId}?.name ?: "Choose a grinder",grinders.map{it.name}){ selected -> grinderId=grinders.first{it.name==selected}.id }
                ChoiceField("Portafilter basket",baskets.firstOrNull{it.id==basketId}?.let(::basketLabel) ?: "Choose a basket",baskets.map(::basketLabel)){ selected -> basketId=baskets.first{basketLabel(it)==selected}.id }
                Spacer(Modifier.height(8.dp)); Button(onClick={onSave(CurrentSetup(beanId,machineId,grinderId,basketId,bagId))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)) { Text("Save current setup") }
            }
        }
        if(beans.isEmpty()||machines.isEmpty()||grinders.isEmpty()||baskets.isEmpty()) {
            Spacer(Modifier.height(12.dp)); Text("Add beans, bags, machines, grinders, and baskets in Library to include them in your setup.",color=Muted,fontSize=13.sp)
        }
    }
}

private fun beanLabel(bean:CoffeeBean) = listOf(bean.brand,bean.name).filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"Unnamed bean"}
private fun bagLabel(bag:CoffeeBag,beans:List<CoffeeBean>) = "${beans.firstOrNull{it.id==bag.beanId}?.let(::beanLabel) ?: "Unknown coffee"} · roasted ${bag.roastDate}"
private fun machineLabel(machine:BrewMachine) = "${machine.name} · ${machine.type}"
private fun basketLabel(basket:PortafilterBasket) = listOf(basket.brand,basket.model).filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"Unnamed basket"}

@Composable private fun StatCard(value:String,label:String, modifier:Modifier=Modifier) { Column(modifier.background(Color(0xFF45382F),RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=10.dp)) { Text(value,color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold); Text(label,color=Color(0xFFC9B9AA),fontSize=8.sp,letterSpacing=0.8.sp,fontWeight=FontWeight.Bold) } }

@Composable private fun ShotCard(s:Shot,onClick:()->Unit,onDelete:()->Unit) {
    Card(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick), shape=RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=Color.White), elevation=CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.bean.ifBlank{"Untitled coffee"},fontWeight=FontWeight.Bold,fontSize=17.sp); Text(listOf(s.roaster,s.date).filter{it.isNotBlank()}.joinToString(" · "),color=Muted,fontSize=12.sp); roastAge(s.roastDate)?.let { Text("$it days post-roast",color=Green,fontSize=11.sp,fontWeight=FontWeight.SemiBold) } }; if(s.photoUris.isNotEmpty()) Text("▧ ${s.photoUris.size}",color=Muted,fontSize=12.sp,modifier=Modifier.padding(end=10.dp)); if(s.rating.isNotBlank()) Text("★ ${s.rating}",color=Coffee,fontWeight=FontWeight.Bold) }
            Spacer(Modifier.height(14.dp)); Row(horizontalArrangement=Arrangement.spacedBy(0.dp)) { Metric("IN", "${s.dose.ifBlank{"—"}} g",Modifier.weight(1f)); Metric("OUT", "${s.yield.ifBlank{"—"}} g",Modifier.weight(1f)); Metric("TIME", "${s.time.ifBlank{"—"}} s",Modifier.weight(1f)); Metric("RATIO", s.ratio.ifBlank{"—"},Modifier.weight(1f)) }
            Spacer(Modifier.height(12.dp)); HorizontalDivider(color=Color(0xFFF0ECE7)); Spacer(Modifier.height(10.dp))
            Text(listOf(s.grinder.takeIf{it.isNotBlank()}?.let{"⚙ $it ${s.grind}"},s.brewer.takeIf{it.isNotBlank()}?.let{"☕ $it"}).filterNotNull().joinToString("     "),color=Muted,fontSize=12.sp)
            Spacer(Modifier.height(6.dp)); Text(listOf(s.basket.takeIf{it.isNotBlank()}?.let{"▤ $it"},"Shower screen: ${if(s.showerScreenUsed) "used" else "not used"}").filterNotNull().joinToString("     "),color=Muted,fontSize=11.sp)
            if(s.roastLevel.isNotBlank() || listOf(s.acidity,s.sourness,s.sweetness,s.body,s.crema).any{it.isNotBlank()}) {
                Spacer(Modifier.height(6.dp)); Text(listOf(s.roastLevel.takeIf{it.isNotBlank()}?.let{"${it.uppercase()} ROAST"},s.acidity.takeIf{it.isNotBlank()}?.let{"Acid $it"},s.sourness.takeIf{it.isNotBlank()}?.let{"Sour $it"},s.sweetness.takeIf{it.isNotBlank()}?.let{"Sweet $it"},s.body.takeIf{it.isNotBlank()}?.let{"Body $it"},s.crema.takeIf{it.isNotBlank()}?.let{"Crema $it"}).filterNotNull().joinToString("  ·  "),color=Coffee,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            }
            if(s.taste.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("TASTES LIKE  ·  ${s.taste}",color=Green,fontSize=11.sp,fontWeight=FontWeight.SemiBold) }
            if(s.notes.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(s.notes,color=Ink,fontSize=13.sp,maxLines=2) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Text("DELETE",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable(onClick=onDelete).padding(6.dp)) }
        }
    }
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier) { Column(modifier) { Text(label,color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text(value,color=Ink,fontWeight=FontWeight.SemiBold,fontSize=14.sp) } }

@Composable private fun ShotForm(existing:Shot?,beans:List<CoffeeBean>,bags:List<CoffeeBag>,machines:List<BrewMachine>,grinders:List<Grinder>,baskets:List<PortafilterBasket>,setup:CurrentSetup,enabledLogEntries:Map<String,Boolean>,onDismiss:()->Unit,onSave:(Shot)->Unit) {
    val context=LocalContext.current
    var photoUris by remember(existing) { mutableStateOf(existing?.photoUris.orEmpty()) }
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems=20)) { selected ->
        selected.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) { } }
        photoUris=(photoUris+selected.map(Uri::toString)).distinct()
    }
    val entryEnabled:(String)->Boolean = { enabledLogEntries[it] ?: true }
    val activeBags=bags.filterNot{it.done}
    val initialBag = if(existing==null) activeBags.firstOrNull { it.id==setup.bagId } else activeBags.firstOrNull { bag ->
        bag.roastDate==existing.roastDate && beans.firstOrNull{it.id==bag.beanId}?.let{it.name==existing.bean && it.brand==existing.roaster}==true
    }
    val initialBean = if(existing!=null) beans.firstOrNull{it.name==existing.bean && it.brand==existing.roaster} else beans.firstOrNull { it.id==(initialBag?.beanId ?: setup.beanId) }
    val initialMachine = machines.firstOrNull { it.id==setup.machineId }
    val initialGrinder = grinders.firstOrNull { it.id==setup.grinderId }
    val initialBasket = baskets.firstOrNull { it.id==setup.basketId }
    val values = remember(existing,beans,bags,machines,grinders,baskets,setup) {
        mutableStateMapOf<String,String>().apply {
            if(existing!=null) {
                fields.zip(listOf(existing.bean,existing.roaster,existing.roastDate,existing.origin,existing.process,existing.dose,existing.yield,existing.time,existing.grinder,existing.grind,existing.brewer,existing.basket,existing.temp,existing.pressure,existing.preinfusion,existing.water,existing.prep,existing.taste,existing.rating,existing.notes)).forEach{(k,v)->put(k,v)}
            } else {
                put(fields[0],initialBean?.name.orEmpty()); put(fields[1],initialBean?.brand.orEmpty()); put(fields[2],initialBag?.roastDate.orEmpty()); put(fields[3],initialBean?.origin.orEmpty()); put(fields[4],initialBean?.process.orEmpty())
                put(fields[8],initialGrinder?.name.orEmpty()); put(fields[10],initialMachine?.let(::machineLabel).orEmpty()); put(fields[11],initialBasket?.let(::basketLabel).orEmpty())
            }
        }
    }
    var customBean by remember(existing,initialBean,beans) { mutableStateOf(if(existing==null) initialBean==null else beans.none{beanLabel(it)==listOf(existing.roaster,existing.bean).filter{value->value.isNotBlank()}.joinToString(" · ")}) }
    var selectedBagId by remember(existing,initialBag,bags) { mutableLongStateOf(initialBag?.id ?: bags.firstOrNull{bag -> existing!=null && bag.roastDate==existing.roastDate && beans.firstOrNull{it.id==bag.beanId}?.let{it.name==existing.bean && it.brand==existing.roaster}==true}?.id ?: 0L) }
    var customMachine by remember(existing,initialMachine,machines) { mutableStateOf(if(existing==null) initialMachine==null else machines.none{machineLabel(it)==existing.brewer}) }
    var customGrinder by remember(existing,initialGrinder,grinders) { mutableStateOf(if(existing==null) initialGrinder==null else grinders.none{it.name==existing.grinder}) }
    var customBasket by remember(existing,initialBasket,baskets) { mutableStateOf(if(existing==null) initialBasket==null else baskets.none{basketLabel(it)==existing.basket}) }
    var showerScreenUsed by remember(existing) { mutableStateOf(existing?.showerScreenUsed ?: false) }
    var overallScore by remember(existing) { mutableStateOf(existing?.rating.orEmpty()) }
    var roastLevel by remember(existing) { mutableStateOf(existing?.roastLevel.orEmpty()) }
    var acidity by remember(existing) { mutableStateOf(existing?.acidity.orEmpty()) }
    var sourness by remember(existing) { mutableStateOf(existing?.sourness.orEmpty()) }
    var sweetness by remember(existing) { mutableStateOf(existing?.sweetness.orEmpty()) }
    var bodyScore by remember(existing) { mutableStateOf(existing?.body.orEmpty()) }
    var crema by remember(existing) { mutableStateOf(existing?.crema.orEmpty()) }
    var expandedSections by remember(existing) { mutableStateOf(setOf("coffee", "extraction", "prep", "scores", "photos")) }
    AlertDialog(onDismissRequest=onDismiss, confirmButton={ TextButton(onClick={
        fun v(k:String)=values[k].orEmpty()
        val dose=v("Dose (g)").toFloatOrNull(); val yield=v("Yield (g)").toFloatOrNull()
        val ratio=if(dose!=null&&yield!=null&&dose>0) String.format(Locale.US,"1:%.1f",yield/dose) else ""
        val shot=Shot(existing?.id?:System.currentTimeMillis(),existing?.date?:SimpleDateFormat("MMM d, yyyy · HH:mm",Locale.getDefault()).format(Date()),v(fields[0]),v(fields[1]),v(fields[2]),v(fields[3]),v(fields[4]),v(fields[5]),v(fields[6]),v(fields[7]),ratio,v(fields[8]),v(fields[9]),v(fields[10]),v(fields[11]),v(fields[12]),v(fields[13]),v(fields[14]),v(fields[15]),v(fields[16]),v(fields[17]),overallScore,v(fields[19]),showerScreenUsed,roastLevel,acidity,sourness,sweetness,bodyScore,crema,photoUris)
        onSave(shot)
    }) { Text("Save shot") } }, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}}, title={Text(if(existing==null)"Log a shot" else "Edit shot",fontWeight=FontWeight.Bold)}, text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            CollapsibleSection("COFFEE & BEANS", "coffee" in expandedSections, { expandedSections=if("coffee" in expandedSections) expandedSections-"coffee" else expandedSections+"coffee" }) {
                if(entryEnabled("bean")) ChoiceField("Bean",if(customBean) "Custom bean" else beans.firstOrNull{beanLabel(it)==listOf(values[fields[1]],values[fields[0]]).filterNotNull().joinToString(" · ")}?.let(::beanLabel) ?: "Choose a bean",beans.map(::beanLabel)+"Custom bean") { choice ->
                    val selected=beans.firstOrNull{beanLabel(it)==choice}
                    customBean=selected==null; selectedBagId=0L
                    if(selected!=null) { values[fields[0]]=selected.name; values[fields[1]]=selected.brand; values[fields[2]]=""; values[fields[3]]=selected.origin; values[fields[4]]=selected.process }
                    else { values[fields[0]]=""; values[fields[1]]=""; values[fields[2]]=""; values[fields[3]]=""; values[fields[4]]="" }
                }
                if(entryEnabled("bag")) ChoiceField("Coffee bag",activeBags.firstOrNull{it.id==selectedBagId}?.let{bagLabel(it,beans)} ?: "No saved bag",activeBags.map{bagLabel(it,beans)}+"No saved bag") { choice ->
                    val bag=activeBags.firstOrNull{bagLabel(it,beans)==choice}; selectedBagId=bag?.id ?: 0L
                    if(bag!=null) {
                        val bean=beans.firstOrNull{it.id==bag.beanId}
                        if(bean!=null) { customBean=false; values[fields[0]]=bean.name; values[fields[1]]=bean.brand; values[fields[3]]=bean.origin; values[fields[4]]=bean.process }
                        else { customBean=true; values[fields[0]]=""; values[fields[1]]=""; values[fields[3]]=""; values[fields[4]]="" }
                        values[fields[2]]=bag.roastDate
                    } else values[fields[2]]=""
                }
                if(entryEnabled("bean") && customBean) {
                    ShotInput(fields[0],values[fields[0]].orEmpty(),{values[fields[0]]=it})
                    ShotInput(fields[1],values[fields[1]].orEmpty(),{values[fields[1]]=it})
                    ShotInput(fields[3],values[fields[3]].orEmpty(),{values[fields[3]]=it})
                    ShotInput(fields[4],values[fields[4]].orEmpty(),{values[fields[4]]=it})
                }
            }
            val extractionKeys=listOf("machine","grinder","basket","showerScreen","dose","yield","brewTime","temperature","pressure","preinfusion","water")
            if(extractionKeys.any(entryEnabled)) CollapsibleSection("EXTRACTION & GEAR", "extraction" in expandedSections, { expandedSections=if("extraction" in expandedSections) expandedSections-"extraction" else expandedSections+"extraction" }) {
                if(entryEnabled("machine")) ChoiceField("Coffee tool / machine",if(customMachine) "Custom machine" else values[fields[10]].orEmpty().ifBlank{"Choose a machine"},machines.map(::machineLabel)+"Custom machine") { choice ->
                    val selected=machines.firstOrNull{machineLabel(it)==choice}; customMachine=selected==null; values[fields[10]]=selected?.let(::machineLabel).orEmpty()
                }
                if(entryEnabled("machine") && customMachine) ShotInput(fields[10],values[fields[10]].orEmpty(),{values[fields[10]]=it})
                if(entryEnabled("grinder")) ChoiceField("Saved grinder",if(customGrinder) "Custom grinder" else values[fields[8]].orEmpty().ifBlank{"Choose a grinder"},grinders.map{it.name}+"Custom grinder") { choice ->
                    val selected=grinders.firstOrNull{it.name==choice}; customGrinder=selected==null; values[fields[8]]=selected?.name.orEmpty()
                }
                if(entryEnabled("grinder") && customGrinder) ShotInput(fields[8],values[fields[8]].orEmpty(),{values[fields[8]]=it})
                if(entryEnabled("basket")) ChoiceField("Portafilter basket",if(customBasket) "Custom basket" else values[fields[11]].orEmpty().ifBlank{"Choose a basket"},baskets.map(::basketLabel)+"Custom basket") { choice ->
                    val selected=baskets.firstOrNull{basketLabel(it)==choice}; customBasket=selected==null; values[fields[11]]=selected?.let(::basketLabel).orEmpty()
                }
                if(entryEnabled("basket") && customBasket) ShotInput(fields[11],values[fields[11]].orEmpty(),{values[fields[11]]=it})
                if(entryEnabled("showerScreen")) Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=showerScreenUsed,onCheckedChange={showerScreenUsed=it})
                    Column { Text("Shower screen used",fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text("Tick if you placed a screen over the coffee puck.",color=Muted,fontSize=11.sp) }
                }
                if(entryEnabled("dose")) ShotInput(fields[5],values[fields[5]].orEmpty(),{values[fields[5]]=it},numeric=true)
                if(entryEnabled("yield")) ShotInput(fields[6],values[fields[6]].orEmpty(),{values[fields[6]]=it},numeric=true)
                if(entryEnabled("brewTime")) ShotInput(fields[7],values[fields[7]].orEmpty(),{values[fields[7]]=it},numeric=true)
                if(entryEnabled("grinder")) ShotInput(fields[9],values[fields[9]].orEmpty(),{values[fields[9]]=it})
                if(entryEnabled("temperature")) ShotInput(fields[12],values[fields[12]].orEmpty(),{values[fields[12]]=it},numeric=true)
                if(entryEnabled("pressure")) ShotInput(fields[13],values[fields[13]].orEmpty(),{values[fields[13]]=it},numeric=true)
                if(entryEnabled("preinfusion")) ShotInput(fields[14],values[fields[14]].orEmpty(),{values[fields[14]]=it})
                if(entryEnabled("water")) ShotInput(fields[15],values[fields[15]].orEmpty(),{values[fields[15]]=it})
            }
            val prepKeys=listOf("puckPrep","taste","notes")
            if(prepKeys.any(entryEnabled)) CollapsibleSection("PREP & TASTING", "prep" in expandedSections, { expandedSections=if("prep" in expandedSections) expandedSections-"prep" else expandedSections+"prep" }) {
                if(entryEnabled("puckPrep")) ShotInput(fields[16],values[fields[16]].orEmpty(),{values[fields[16]]=it},multiline=true)
                if(entryEnabled("taste")) ShotInput(fields[17],values[fields[17]].orEmpty(),{values[fields[17]]=it},multiline=true)
                if(entryEnabled("notes")) ShotInput(fields[19],values[fields[19]].orEmpty(),{values[fields[19]]=it},multiline=true)
            }
            val scoreKeys=listOf("roastLevel","overallScore","acidity","sourness","sweetness","body","crema")
            if(scoreKeys.any(entryEnabled)) CollapsibleSection("ROAST & CUP SCORES", "scores" in expandedSections, { expandedSections=if("scores" in expandedSections) expandedSections-"scores" else expandedSections+"scores" }) {
                Text("Flavor traits: 1 low · 5 high. Overall: 1 poor · 5 excellent.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=2.dp,bottom=8.dp))
                if(entryEnabled("roastLevel")) ChoiceField("Roast level",roastLevel.ifBlank{"Choose roast level"},listOf("Light","Medium","Dark")){roastLevel=it}
                if(entryEnabled("overallScore")) RatingPicker("Overall score",overallScore,"1 low · 5 excellent"){overallScore=it}
                if(entryEnabled("acidity")) RatingPicker("Acidity",acidity,"Intensity"){acidity=it}
                if(entryEnabled("sourness")) RatingPicker("Sourness",sourness,"Intensity"){sourness=it}
                if(entryEnabled("sweetness")) RatingPicker("Sweetness",sweetness,"Intensity"){sweetness=it}
                if(entryEnabled("body")) RatingPicker("Body",bodyScore,"Intensity"){bodyScore=it}
                if(entryEnabled("crema")) RatingPicker("Crema",crema,"Intensity"){crema=it}
            }
            CollapsibleSection("SHOT PHOTOS", "photos" in expandedSections, { expandedSections=if("photos" in expandedSections) expandedSections-"photos" else expandedSections+"photos" }) {
                Text("Attach photos from your gallery to keep them with this shot.",fontSize=11.sp,color=Muted)
                OutlinedButton(onClick={photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { Text("＋ Add photos from gallery") }
                photoUris.forEach { uri ->
                    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        UriImage(uri,Modifier.size(58.dp).aspectRatio(1f))
                        Text(uri.substringAfterLast('/').take(24),Modifier.weight(1f).padding(horizontal=10.dp),fontSize=11.sp,color=Muted,maxLines=1)
                        Text("REMOVE",color=Coffee,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable { photoUris=photoUris.filterNot{it==uri} }.padding(8.dp))
                    }
                }
            }
            Text("Dose-to-yield ratio is calculated automatically.",fontSize=11.sp,color=Muted)
        }
    }, shape=RoundedCornerShape(22.dp), containerColor=Color(0xFFFFFCF8))
}

private fun encode(shots:List<Shot>):String { val a=JSONArray(); shots.forEach{s-> val o=JSONObject(); listOf("id" to s.id,"date" to s.date,"bean" to s.bean,"roaster" to s.roaster,"roastDate" to s.roastDate,"origin" to s.origin,"process" to s.process,"dose" to s.dose,"yield" to s.yield,"time" to s.time,"ratio" to s.ratio,"grinder" to s.grinder,"grind" to s.grind,"brewer" to s.brewer,"basket" to s.basket,"temp" to s.temp,"pressure" to s.pressure,"preinfusion" to s.preinfusion,"water" to s.water,"prep" to s.prep,"taste" to s.taste,"rating" to s.rating,"notes" to s.notes,"showerScreenUsed" to s.showerScreenUsed,"roastLevel" to s.roastLevel,"acidity" to s.acidity,"sourness" to s.sourness,"sweetness" to s.sweetness,"body" to s.body,"crema" to s.crema).forEach{(k,v)->o.put(k,v)};o.put("photoUris",JSONArray(s.photoUris));a.put(o) };return a.toString() }
private fun roastAge(date:String):Long? { return try { val roast=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { isLenient=false }.parse(date) ?: return null; ((System.currentTimeMillis()-roast.time)/86_400_000L).takeIf{it>=0} } catch(_:Exception) { null } }
private fun encodeBeans(items:List<CoffeeBean>):String = JSONArray().apply { items.forEach { bean -> put(JSONObject().put("id",bean.id).put("brand",bean.brand).put("name",bean.name).put("origin",bean.origin).put("process",bean.process)) } }.toString()
private fun loadBeans(json:String):List<CoffeeBean> = try { val array=JSONArray(json); (0 until array.length()).map { index -> val item=array.getJSONObject(index); CoffeeBean(item.optLong("id"),item.optString("brand"),item.optString("name"),item.optString("origin"),item.optString("process")) } } catch(_:Exception) { emptyList() }
private fun encodeBags(items:List<CoffeeBag>):String = JSONArray().apply { items.forEach { bag -> put(JSONObject().put("id",bag.id).put("beanId",bag.beanId).put("roastDate",bag.roastDate).put("done",bag.done)) } }.toString()
private fun loadBags(json:String):List<CoffeeBag> = try { val array=JSONArray(json); (0 until array.length()).map { index -> val item=array.getJSONObject(index); CoffeeBag(item.optLong("id"),item.optLong("beanId"),item.optString("roastDate"),item.optBoolean("done",false)) } } catch(_:Exception) { emptyList() }
private fun encodeMachines(items:List<BrewMachine>):String = JSONArray().apply { items.forEach { machine -> put(JSONObject().put("id",machine.id).put("name",machine.name).put("type",machine.type)) } }.toString()
private fun loadMachines(json:String):List<BrewMachine> = try { val array=JSONArray(json); (0 until array.length()).map { index -> val item=array.getJSONObject(index); BrewMachine(item.optLong("id"),item.optString("name"),item.optString("type")) } } catch(_:Exception) { emptyList() }
private fun encodeGrinders(items:List<Grinder>):String = JSONArray().apply { items.forEach { grinder -> put(JSONObject().put("id",grinder.id).put("name",grinder.name)) } }.toString()
private fun loadGrinders(json:String):List<Grinder> = try { val array=JSONArray(json); (0 until array.length()).map { index -> val item=array.getJSONObject(index); Grinder(item.optLong("id"),item.optString("name")) } } catch(_:Exception) { emptyList() }
private fun encodeBaskets(items:List<PortafilterBasket>):String = JSONArray().apply { items.forEach { basket -> put(JSONObject().put("id",basket.id).put("brand",basket.brand).put("model",basket.model).put("size",basket.size).put("doseRange",basket.doseRange)) } }.toString()
private fun loadBaskets(json:String):List<PortafilterBasket> = try { val array=JSONArray(json); (0 until array.length()).map { index -> val item=array.getJSONObject(index); PortafilterBasket(item.optLong("id"),item.optString("brand"),item.optString("model"),item.optString("size"),item.optString("doseRange")) } } catch(_:Exception) { emptyList() }
private fun encodeSetup(setup:CurrentSetup):String = JSONObject().put("beanId",setup.beanId).put("machineId",setup.machineId).put("grinderId",setup.grinderId).put("basketId",setup.basketId).put("bagId",setup.bagId).toString()
private fun loadSetup(json:String):CurrentSetup = try { val item=JSONObject(json); CurrentSetup(item.optLong("beanId"),item.optLong("machineId"),item.optLong("grinderId"),item.optLong("basketId"),item.optLong("bagId")) } catch(_:Exception) { CurrentSetup() }
private fun encodeBackup(shots:List<Shot>,beans:List<CoffeeBean>,bags:List<CoffeeBag>,machines:List<BrewMachine>,grinders:List<Grinder>,baskets:List<PortafilterBasket>,setup:CurrentSetup,logSettings:Map<String,Boolean>):String = JSONObject()
    .put("format","brewlog-backup").put("schemaVersion",1)
    .put("shots",JSONArray(encode(shots))).put("beans",JSONArray(encodeBeans(beans))).put("bags",JSONArray(encodeBags(bags)))
    .put("machines",JSONArray(encodeMachines(machines))).put("grinders",JSONArray(encodeGrinders(grinders))).put("baskets",JSONArray(encodeBaskets(baskets)))
    .put("setup",JSONObject(encodeSetup(setup))).put("logSettings",JSONObject(encodeLogSettings(logSettings))).toString(2)
private fun isValidBackup(json:String):Boolean = try {
    val backup=JSONObject(json)
    backup.optString("format")=="brewlog-backup" && backup.optInt("schemaVersion")==1 &&
        listOf("shots","beans","bags","machines","grinders","baskets").all { backup.opt(it) is JSONArray } &&
        backup.opt("setup") is JSONObject && backup.opt("logSettings") is JSONObject
} catch(_:Exception) { false }
private fun encodeLogSettings(settings:Map<String,Boolean>):String = JSONObject().apply { settings.forEach { (key,value) -> put(key,value) } }.toString()
private fun loadLogSettings(json:String):Map<String,Boolean> = try {
    val item=JSONObject(json)
    buildMap { val keys=item.keys(); while(keys.hasNext()) { val key=keys.next(); put(key,item.optBoolean(key,true)) } }
} catch(_:Exception) { emptyMap() }
private fun loadShots(json: String): List<Shot> {
    return try {
        val array = JSONArray(json)
        buildList<Shot> {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Shot(
                        id = item.optLong("id"),
                        date = item.optString("date"),
                        bean = item.optString("bean"),
                        roaster = item.optString("roaster"),
                        roastDate = item.optString("roastDate"),
                        origin = item.optString("origin"),
                        process = item.optString("process"),
                        dose = item.optString("dose"),
                        yield = item.optString("yield"),
                        time = item.optString("time"),
                        ratio = item.optString("ratio"),
                        grinder = item.optString("grinder"),
                        grind = item.optString("grind"),
                        brewer = item.optString("brewer"),
                        basket = item.optString("basket"),
                        temp = item.optString("temp"),
                        pressure = item.optString("pressure"),
                        preinfusion = item.optString("preinfusion"),
                        water = item.optString("water"),
                        prep = item.optString("prep"),
                        taste = item.optString("taste"),
                        rating = item.optString("rating"),
                        notes = item.optString("notes"),
                        showerScreenUsed = item.optBoolean("showerScreenUsed", false),
                        roastLevel = item.optString("roastLevel"),
                        acidity = item.optString("acidity"),
                        sourness = item.optString("sourness"),
                        sweetness = item.optString("sweetness"),
                        body = item.optString("body"),
                        crema = item.optString("crema"),
                        photoUris = item.optJSONArray("photoUris")?.let { photos -> (0 until photos.length()).map { photos.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
