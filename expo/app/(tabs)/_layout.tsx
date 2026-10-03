import { Tabs } from "expo-router";
import { Text } from "react-native";

const Icon = ({ children }: { children: string }) => (
  <Text style={{ fontSize: 19 }}>{children}</Text>
);

export default function TabLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: "#0d0d0d",
          borderTopColor: "#242018",
          height: 68,
          paddingBottom: 10,
          paddingTop: 8
        },
        tabBarActiveTintColor: "#d7ad58",
        tabBarInactiveTintColor: "#77736c"
      }}
    >
      <Tabs.Screen name="index" options={{ title: "Home", tabBarIcon: () => <Icon>⌂</Icon> }} />
      <Tabs.Screen name="library" options={{ title: "Library", tabBarIcon: () => <Icon>♫</Icon> }} />
      <Tabs.Screen name="playlists" options={{ title: "Playlists", tabBarIcon: () => <Icon>☷</Icon> }} />
      <Tabs.Screen name="settings" options={{ title: "Settings", tabBarIcon: () => <Icon>⚙</Icon> }} />
    </Tabs>
  );
}
