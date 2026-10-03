import { useState } from "react";
import { Modal, ScrollView, StyleSheet, Text, TextInput, View, Pressable } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Header, Empty, gold, card, muted } from "../../src/ui";
import { useStore } from "../../src/store";

export default function Playlists() {
  const { state, createPlaylist } = useStore();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const names = Object.keys(state.playlists);
  const submit = () => {
    const clean = name.trim();
    if (!clean) return;
    createPlaylist(clean);
    setName("");
    setOpen(false);
  };
  return <SafeAreaView style={styles.safe}><ScrollView contentContainerStyle={styles.content}>
    <Header eyebrow="Playlists" title="Made for you." />
    <Pressable style={styles.create} onPress={() => setOpen(true)}><Text style={styles.createText}>＋ Create playlist</Text></Pressable>
    <View style={styles.list}>{names.map(n => <View key={n} style={styles.item}>
      <View><Text style={styles.name}>{n}</Text><Text style={styles.sub}>{state.playlists[n].length} songs</Text></View>
      <Text style={styles.arrow}>›</Text>
    </View>)}</View>
    {!names.length && <Empty>Create a playlist to organize your music.</Empty>}
    <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
      <View style={styles.overlay}><View style={styles.sheet}><Text style={styles.sheetTitle}>New playlist</Text>
        <TextInput value={name} onChangeText={setName} autoFocus placeholder="Playlist name" placeholderTextColor={muted} style={styles.input} />
        <Pressable style={styles.create} onPress={submit}><Text style={styles.createText}>Create</Text></Pressable>
        <Pressable onPress={() => setOpen(false)}><Text style={styles.cancel}>Cancel</Text></Pressable>
      </View></View>
    </Modal>
  </ScrollView></SafeAreaView>;
}
const styles=StyleSheet.create({
safe:{flex:1,backgroundColor:"#090909"},content:{paddingHorizontal:18,paddingBottom:100},
create:{backgroundColor:gold,padding:15,borderRadius:14,alignItems:"center"},createText:{color:"#090909",fontWeight:"800"},
list:{backgroundColor:card,borderRadius:18,marginTop:18},item:{padding:18,borderBottomWidth:1,borderBottomColor:"#282218",flexDirection:"row",justifyContent:"space-between"},
name:{color:"#fff",fontSize:16,fontWeight:"700"},sub:{color:muted,marginTop:5},arrow:{color:gold,fontSize:28},
overlay:{flex:1,justifyContent:"flex-end",backgroundColor:"#0009"},sheet:{backgroundColor:"#17150f",padding:20,borderTopLeftRadius:24,borderTopRightRadius:24},
sheetTitle:{color:"#fff",fontSize:22,fontWeight:"800",marginBottom:14},input:{backgroundColor:"#0d0d0d",color:"#fff",padding:14,borderRadius:12,marginBottom:12},cancel:{color:muted,textAlign:"center",padding:16,fontWeight:"700"}
});
