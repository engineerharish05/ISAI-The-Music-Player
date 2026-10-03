package com.rishinnovations.isai
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
private val Gold=Color(0xFFC6A15B);private val Black=Color(0xFF0A0A09);private val Surface=Color(0xFF171714)
@Composable fun IsaiApp(vm:IsaiViewModel=viewModel()){val songs by vm.songs.collectAsState();var tab by remember{mutableIntStateOf(0)};LaunchedEffect(Unit){vm.connect();vm.scan()};MaterialTheme(colorScheme=darkColorScheme(primary=Gold,background=Black,surface=Surface)){Scaffold(containerColor=Black,bottomBar={NavigationBar(containerColor=Surface){listOf("Home","Explore","Library","Playlists","Settings").forEachIndexed{i,x->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","⌕","♫","▤","⚙")[i])},label={Text(x)})}}}){p->when(tab){0->Home(songs,vm,p);1,2->Library(if(tab==1)"Explore" else "Library",songs,vm,p);else->Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){Text(if(tab==3)"Playlists" else "Settings",style=MaterialTheme.typography.headlineMedium)}}}}}
@Composable private fun Home(s:List<Song>,vm:IsaiViewModel,p:PaddingValues){Column(Modifier.fillMaxSize().padding(p).padding(20.dp)){Text("ISAI",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Gold);Text("THE MUSIC PLAYER",fontWeight=FontWeight.Medium);Spacer(Modifier.height(24.dp));Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Surface)){Column(Modifier.padding(24.dp)){Text("Your music.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Native playback. Background ready.",color=Color.LightGray);Spacer(Modifier.height(16.dp));Button(onClick={s.firstOrNull()?.let(vm::play)}){Text("Listen now")}}};Spacer(Modifier.height(24.dp));Text("Your library",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));SongList(s.take(8),vm)}}
@Composable private fun Library(title:String,s:List<Song>,vm:IsaiViewModel,p:PaddingValues){Column(Modifier.fillMaxSize().padding(p).padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(s.size.toString()+" songs",color=Gold);Spacer(Modifier.height(12.dp));SongList(s,vm)}}
@Composable private fun SongList(s:List<Song>,vm:IsaiViewModel){LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(s,key={it.id}){x->Row(Modifier.fillMaxWidth().background(Surface,RoundedCornerShape(18.dp)).clickable{vm.play(x)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(52.dp).background(Gold.copy(alpha=.18f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Text("♫",color=Gold)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(x.title,fontWeight=FontWeight.SemiBold,maxLines=1);Text(x.artist+" · "+x.album,color=Color.LightGray,maxLines=1)};Text("›",color=Gold)}}}}